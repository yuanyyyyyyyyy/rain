package rain.admin.publish;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private final SitemapGenerator sitemapGenerator;
    private final PublishLogRepo logRepo;
    private final Path projectRoot;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public PublishService(PhotoRepo photoRepo, PhotoDataGenerator photoDataGenerator,
                          ContentService contentService, SiteDataGenerator siteDataGenerator,
                          SitemapGenerator sitemapGenerator, PublishLogRepo logRepo,
                          @Value("${app.storage.frontend-public-dir}") String frontendPublicDir) {
        this.photoRepo = photoRepo;
        this.photoDataGenerator = photoDataGenerator;
        this.contentService = contentService;
        this.siteDataGenerator = siteDataGenerator;
        this.sitemapGenerator = sitemapGenerator;
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
            List<String[]> files = buildFiles();
            List<String> slugs = contentService.list().stream().map(ProjectDetail::getSlug).toList();
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

    /** 从 DB 生成全部数据文件内容（[相对路径, 内容]）；publish 与 dirty 共用同一生成源 */
    private List<String[]> buildFiles() throws IOException {
        List<Photo> photos = photoRepo.findAllByOrderByGroupNameAscSortNoAscIdAsc();
        Photo hero = photos.stream().filter(p -> Boolean.TRUE.equals(p.getIsHero())).findFirst().orElse(null);
        String photosJs = photoDataGenerator.generate(photos, hero);
        List<ProjectDetail> details = contentService.list();
        Path siteJsPath = projectRoot.resolve("src/data/site.js");
        String siteJs = siteDataGenerator.generate(details, Files.readString(siteJsPath));
        List<String[]> files = new ArrayList<>(); // [相对路径, 内容]
        files.add(new String[] {"src/data/photos.js", photosJs});
        files.add(new String[] {"src/data/site.js", siteJs});
        // sitemap 随发布自动重写（手工维护曾与项目集脱节）；不带 lastmod 防止跨天误报脏
        files.add(new String[] {"public/sitemap.xml", sitemapGenerator.generate(details)});
        for (ProjectDetail d : details) {
            files.add(new String[] {"src/data/projects/" + d.getSlug() + ".js", contentService.export(d)});
        }
        return files;
    }

    /**
     * 脏检测：DB 当前内容若立即发布，会有哪些变化。
     * dirty_files = 生成内容与磁盘不一致的数据文件；dirty_photos = public/photos 有未提交的新增/删除二进制
     * （此前发布 commit 只带 src/data，新照片公网 404 的根因，任务 C P0 修复后由本接口暴露给 UI 角标）。
     */
    public Map<String, Object> dirty() {
        List<String> changed = new ArrayList<>();
        try {
            for (String[] f : buildFiles()) {
                Path target = projectRoot.resolve(f[0]);
                if (!Files.exists(target) || !Files.readString(target).equals(f[1])) {
                    changed.add(f[0]);
                }
            }
        } catch (Exception e) {
            // 生成失败（如缺头图）也视为有未发布状态，提示文案交给前端
            return Map.of("dirty", true, "files", changed, "photosUncommitted", false, "error",
                    e.getMessage() == null ? e.toString() : e.getMessage());
        }
        boolean photosUncommitted = false;
        try {
            // 照片与截图两个二进制目录都在发布 commit 范围内（任务 C/G），任一有未提交改动都算脏
            Process p = new ProcessBuilder("git", "status", "--porcelain", "public/photos", "public/screenshots")
                    .directory(projectRoot.toFile()).start();
            String out = new String(p.getInputStream().readAllBytes());
            if (p.waitFor(10, TimeUnit.SECONDS) && !out.isBlank()) photosUncommitted = true;
        } catch (Exception ignored) {
            // git 不可用时按无照片改动处理
        }
        boolean dirty = !changed.isEmpty() || photosUncommitted;
        return Map.of("dirty", dirty, "files", changed, "photosUncommitted", photosUncommitted);
    }

    /**
     * 公网部署状态：代理 GitHub Actions 最新一次 runs（公开仓库匿名可读）。
     * 由后端代理而非前端直连——api.github.com 国内直连同样受限，后端所在网络已验证可达。
     */
    public Map<String, Object> deployStatus() {
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(8)).build();
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("https://api.github.com/repos/yuanyyyyyyyyy/rain/actions/runs?per_page=1"))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "rain-admin")
                    .timeout(Duration.ofSeconds(8))
                    .GET().build();
            java.net.http.HttpResponse<String> res = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                return Map.of("error", "GitHub API " + res.statusCode());
            }
            com.fasterxml.jackson.databind.JsonNode run = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(res.body()).path("workflow_runs").path(0);
            if (run.isMissingNode()) return Map.of("error", "无部署记录");
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("status", run.path("status").asText());
            String conclusion = run.path("conclusion").asText(null);
            if (conclusion != null) m.put("conclusion", conclusion);
            m.put("createdAt", run.path("created_at").asText());
            m.put("htmlUrl", run.path("html_url").asText());
            return m;
        } catch (Exception e) {
            return Map.of("error", "查询失败：" + (e.getMessage() == null ? e.toString() : e.getMessage()));
        }
    }

    /**
     * 4. 数据文件 commit + 推送公网。
     *
     * 并发护栏（2026-11-10 事故）：多会话/多工作区同时发布时，push 会因非快进被拒，
     * 旧逻辑随即回退 tools/sync-via-api.py 全量重放本地历史，把线上整棵树回退成旧版；
     * 且异常被本方法吞掉，publish() 仍把 publish_log 记成 SUCCESS，界面显示「发布成功」。
     *
     * 现在：推送前先 fetch + 确认 origin/main 是 HEAD 的祖先（即本次发布基于同一基线），
     * 不满足就直接抛出，由 publish() 记为 FAILED 并把原因返回给管理台。
     * 只有基线一致、push 因网络失败时才允许 API 续传兜底（脚本 v3 会再做 message+tree 双匹配校验）。
     */
    private String commitAndSync() throws IOException, InterruptedException {
        StringBuilder out = new StringBuilder();
        // 任务 C P0：照片/截图二进制必须与数据文件一起进发布 commit，否则新上传照片公网 404；
        // git add <pathspec> 自带删除语义，删除同样入库
        run(20, "git", "add", "src/data", "public/photos", "public/screenshots");
        Process diff = new ProcessBuilder("git", "diff", "--cached", "--quiet")
                .directory(projectRoot.toFile()).start();
        boolean hasChanges = diff.waitFor(10, TimeUnit.SECONDS) && diff.exitValue() != 0;
        if (hasChanges) {
            run(20, "git", "commit", "-m", "publish: content update from admin console");
            out.append("数据文件已 commit；");
        } else {
            out.append("数据无变化，跳过 commit；");
        }

        run(30, "git", "fetch", "origin");
        Process ancestor = new ProcessBuilder("git", "merge-base", "--is-ancestor", "origin/main", "HEAD")
                .directory(projectRoot.toFile()).start();
        boolean basedOnRemote = ancestor.waitFor(20, TimeUnit.SECONDS) && ancestor.exitValue() == 0;
        if (!basedOnRemote) {
            throw new IllegalStateException(
                    "检测到本地 main 与 origin/main 分叉（另一个会话已发布过内容）。为保护线上，已中止推送。"
                            + "请先执行 git fetch origin && git pull --rebase origin main，"
                            + "确认 git merge-base --is-ancestor origin/main HEAD 通过后再发布；"
                            + "禁止 force push，也不要运行 tools/sync-via-api.py 做全量重放。");
        }

        try {
            // 显式写 origin HEAD:main，不依赖本地 upstream 配置（本机曾未配置，push 无条件失败）
            run(120, "git", "push", "origin", "HEAD:main");
            out.append("git push 成功（公网约 1 分钟后更新）");
        } catch (Exception networkDown) {
            run(240, "python", "tools/sync-via-api.py");
            out.append("经 API 通道推送成功（公网约 1 分钟后更新）");
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
