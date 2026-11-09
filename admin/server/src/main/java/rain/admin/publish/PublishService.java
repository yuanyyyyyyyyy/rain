package rain.admin.publish;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import rain.admin.content.ContentService;
import rain.admin.content.ProjectDetail;
import rain.admin.content.SiteDataGenerator;
import rain.admin.photo.Photo;
import rain.admin.photo.PhotoRepo;

/**
 * 发布编排：生成数据文件（tmp → 原子替换）→ ProcessBuilder 触发 npm run build → publish_log 落库。
 * 单管理员场景仍做并发防护（AtomicBoolean + synchronized），进行中重复触发返回 409。
 */
@Service
public class PublishService {

    private static final long BUILD_TIMEOUT_SECONDS = 300;

    private final PhotoRepo photoRepo;
    private final PhotoDataGenerator photoDataGenerator;
    private final ContentService contentService;
    private final SiteDataGenerator siteDataGenerator;
    private final PublishLogRepo logRepo;
    private final Path projectRoot;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public PublishService(PhotoRepo photoRepo, PhotoDataGenerator photoDataGenerator,
                          ContentService contentService, SiteDataGenerator siteDataGenerator,
                          PublishLogRepo logRepo,
                          @Value("${app.storage.frontend-public-dir}") String frontendPublicDir) {
        this.photoRepo = photoRepo;
        this.photoDataGenerator = photoDataGenerator;
        this.contentService = contentService;
        this.siteDataGenerator = siteDataGenerator;
        this.logRepo = logRepo;
        this.projectRoot = Path.of(frontendPublicDir).getParent();
    }

    public record PublishResult(String status, Long durationMs, List<String> writtenFiles, String logTail) {}

    public boolean isRunning() {
        return running.get();
    }

    public synchronized PublishResult publish(String triggeredBy) {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("发布正在进行中，请稍候");
        }
        long start = System.currentTimeMillis();
        try {
            String commitHash = currentCommit();
            // 1. 生成全部数据文件内容（任一生成失败即中止，不碰现有文件）
            List<Photo> photos = photoRepo.findAllByOrderByGroupNameAscSortNoAscIdAsc();
            Photo hero = photos.stream().filter(p -> Boolean.TRUE.equals(p.getIsHero())).findFirst().orElse(null);
            String photosJs = photoDataGenerator.generate(photos, hero);
            List<ProjectDetail> details = contentService.list();
            Path siteJsPath = projectRoot.resolve("src/data/site.js");
            String siteJs = siteDataGenerator.generate(details, Files.readString(siteJsPath));
            List<String[]> files = new ArrayList<>(); // [相对路径, 内容]
            files.add(new String[] {"src/data/photos.js", photosJs});
            files.add(new String[] {"src/data/site.js", siteJs});
            List<String> slugs = details.stream().map(ProjectDetail::getSlug).toList();
            for (ProjectDetail d : details) {
                files.add(new String[] {"src/data/projects/" + d.getSlug() + ".js", contentService.export(d)});
            }
            // 清理已删项目的数据文件（DB 之外的 .js）
            try (var stream = Files.list(projectRoot.resolve("src/data/projects"))) {
                stream.filter(p -> p.toString().endsWith(".js"))
                        .filter(p -> slugs.stream().noneMatch(s -> p.getFileName().toString().equals(s + ".js")))
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException ignored) {
                                // 清理失败不阻断发布
                            }
                        });
            }

            // 2. tmp 全部写成功后再逐个原子替换（写 tmp 失败 = 现有文件分毫未动）
            List<String> written = new ArrayList<>();
            List<Path> tmps = new ArrayList<>();
            try {
                for (String[] f : files) {
                    Path target = projectRoot.resolve(f[0]);
                    Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
                    Files.writeString(tmp, f[1]);
                    tmps.add(tmp);
                }
                for (int i = 0; i < files.size(); i++) {
                    Path target = projectRoot.resolve(files.get(i)[0]);
                    try {
                        Files.move(tmps.get(i), target, StandardCopyOption.REPLACE_EXISTING,
                                StandardCopyOption.ATOMIC_MOVE);
                    } catch (IOException atomicFailed) {
                        Files.move(tmps.get(i), target, StandardCopyOption.REPLACE_EXISTING);
                    }
                    written.add(files.get(i)[0]);
                }
            } finally {
                for (Path tmp : tmps) {
                    try {
                        Files.deleteIfExists(tmp);
                    } catch (IOException ignored) {
                        // 残留 tmp 由下次发布覆盖
                    }
                }
            }

            // 3. 触发前台构建
            String buildOutput = runBuild();

            // 4. 数据文件 commit + 推送公网（闭环：管理台发布 → GitHub → Pages 自动部署）
            String pushNote = commitAndSync();

            long duration = System.currentTimeMillis() - start;
            saveLog(triggeredBy, "SUCCESS", "构建成功\n" + pushNote + "\n" + buildOutput, duration, written, commitHash);
            return new PublishResult("SUCCESS", duration, written, tail(buildOutput, 40));
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            String msg = e.getMessage() == null ? e.toString() : e.getMessage();
            saveLog(triggeredBy, "FAILED", msg, duration, List.of(), currentCommit());
            throw new IllegalStateException("发布失败：" + msg, e);
        } finally {
            running.set(false);
        }
    }

    /** 发布时的数据版本（git 不可用或非仓库时返回 null，不阻断发布） */
    private String currentCommit() {
        try {
            Process p = new ProcessBuilder("git", "rev-parse", "--short", "HEAD")
                    .directory(projectRoot.toFile()).start();
            if (!p.waitFor(10, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return null;
            }
            return new String(p.getInputStream().readAllBytes()).trim();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 4. 数据文件 commit + 推送公网。推送策略：git push（代理开启时最快）失败则回退
     * tools/sync-via-api.py（API 通道，github.com 被墙时可用）。失败不阻断发布，日志留痕可手动补。
     */
    private String commitAndSync() {
        StringBuilder out = new StringBuilder();
        try {
            run(20, "git", "add", "src/data");
            Process diff = new ProcessBuilder("git", "diff", "--cached", "--quiet")
                    .directory(projectRoot.toFile()).start();
            boolean hasChanges = diff.waitFor(10, TimeUnit.SECONDS) && diff.exitValue() != 0;
            if (hasChanges) {
                run(20, "git", "commit", "-m", "publish: content update from admin console");
                out.append("数据文件已 commit；");
            } else {
                out.append("数据无变化，跳过 commit；");
            }
            try {
                run(20, "git", "push");
                out.append("git push 成功（公网约 1 分钟后更新）");
            } catch (Exception proxyDown) {
                run(240, "python", "tools/sync-via-api.py");
                out.append("经 API 通道推送成功（公网约 1 分钟后更新）");
            }
        } catch (Exception e) {
            out.append("推送失败，可稍后手动执行 python tools/sync-via-api.py：")
                    .append(e.getMessage() == null ? e.toString() : e.getMessage());
        }
        return out.toString();
    }

    private void run(int timeoutSec, String... cmd) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(cmd).directory(projectRoot.toFile())
                .redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes());
        if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new IllegalStateException("命令超时: " + String.join(" ", cmd));
        }
        if (p.exitValue() != 0) {
            throw new IllegalStateException("命令失败(" + p.exitValue() + "): "
                    + String.join(" ", cmd) + "\n" + tail(output, 10));
        }
    }

    /** npm run build；输出重定向到临时文件读取（超时安全），命令固定数组无注入面 */
    private String runBuild() throws IOException, InterruptedException {
        Path outFile = Files.createTempFile("rain-build-", ".log");
        try {
            ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "npm", "run", "build");
            pb.directory(projectRoot.toFile());
            pb.redirectErrorStream(true);
            pb.redirectOutput(outFile.toFile());
            Process p = pb.start();
            if (!p.waitFor(BUILD_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IllegalStateException("构建超时（" + BUILD_TIMEOUT_SECONDS + "s）");
            }
            String output = Files.readString(outFile);
            if (p.exitValue() != 0) {
                throw new IllegalStateException("构建失败（exit=" + p.exitValue() + "）\n" + tail(output, 40));
            }
            return output;
        } finally {
            try {
                Files.deleteIfExists(outFile);
            } catch (IOException ignored) {
                // 临时日志文件删除失败可接受
            }
        }
    }

    /** 日志落库（repo.save 自带事务；失败不掩盖主流程结果） */
    private void saveLog(String triggeredBy, String status, String output, long durationMs,
                         List<String> files, String commitHash) {
        try {
            PublishLog log = new PublishLog();
            log.setTriggeredBy(triggeredBy);
            log.setStatus(status);
            log.setOutput(tail(output, 200));
            log.setDurationMs(durationMs);
            log.setWrittenFiles(String.join(", ", files));
            log.setCommitHash(commitHash);
            logRepo.save(log);
        } catch (RuntimeException ignored) {
            // 日志落库失败不掩盖主流程结果
        }
    }

    private static String tail(String text, int maxLines) {
        if (text == null) return "";
        String[] lines = text.split("\n");
        if (lines.length <= maxLines) return text;
        return String.join("\n", java.util.Arrays.copyOfRange(lines, lines.length - maxLines, lines.length));
    }
}
