package rain.admin.photo;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 照片上传与管理。压缩参数对齐前台现有管线：长边 1600px / q80（只缩不放）。
 * 一致性纪律（设计方案 4.2）：文件写成功但入库失败 → 删除孤儿文件（try-catch 补偿）。
 */
@Service
public class PhotoService {

    private static final int LONG_EDGE = 1600;
    private static final double QUALITY = 0.8;
    private static final Set<String> GROUPS = Set.of("STREET", "LIGHT", "DAILY");

    private final PhotoRepo repo;
    private final Path frontendPublicDir;
    private final Path origDir;

    public PhotoService(PhotoRepo repo,
                        @Value("${app.storage.frontend-public-dir}") String frontendPublicDir,
                        @Value("${app.storage.orig-dir}") String origDir) {
        this.repo = repo;
        this.frontendPublicDir = Path.of(frontendPublicDir);
        this.origDir = Path.of(origDir);
    }

    public record UploadResult(Photo photo, String snippet) {}

    @Transactional
    public UploadResult upload(MultipartFile file, LocalDate shotDate, String groupName) throws IOException {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("文件为空");
        String ct = file.getContentType();
        if (ct == null || !ct.startsWith("image/")) throw new IllegalArgumentException("仅支持图片文件");
        if (groupName == null || !GROUPS.contains(groupName)) {
            throw new IllegalArgumentException("分组必须是 STREET / LIGHT / DAILY 之一");
        }

        // pid：现有最大编号 +1（单管理员场景无并发竞争）
        String pid = String.format("p%02d", repo.maxPidNumber() + 1);
        // 目标文件已存在 = 库表与文件系统脱节（如种子未导入），拒绝而不是覆盖现有照片
        Path photosDir = frontendPublicDir.resolve("photos");
        Files.createDirectories(photosDir);
        Path target = photosDir.resolve(pid + ".jpg");
        if (Files.exists(target)) {
            throw new IllegalArgumentException("pid " + pid + " 的目标文件已存在，请先完成种子导入或人工核对");
        }

        // 原始副本先落盘（留档，压缩与宽高读取都从它走，避免 MultipartFile 流只能读一次的问题）
        Files.createDirectories(origDir);
        Path origTarget = origDir.resolve(pid + "-" + sanitize(file.getOriginalFilename()));
        Files.copy(file.getInputStream(), origTarget, StandardCopyOption.REPLACE_EXISTING);

        // 压缩：长边超过 1600 才缩（只缩不放），jpg q80
        BufferedImage src = ImageIO.read(origTarget.toFile());
        if (src == null) {
            deleteQuietly(origTarget);
            throw new IllegalArgumentException("无法解析的图片文件");
        }
        BufferedImage scaled = src;
        if (Math.max(src.getWidth(), src.getHeight()) > LONG_EDGE) {
            scaled = Thumbnails.of(src).size(LONG_EDGE, LONG_EDGE).asBufferedImage();
        }
        Thumbnails.of(scaled).scale(1.0).outputQuality(QUALITY).outputFormat("jpg").toFile(target.toFile());

        // 入库；失败则删除两个文件补偿
        try {
            Photo p = new Photo();
            p.setPid(pid);
            p.setOrigName(file.getOriginalFilename());
            p.setShotDate(shotDate);
            p.setWidth(scaled.getWidth());
            p.setHeight(scaled.getHeight());
            p.setGroupName(groupName);
            p.setSortNo(repo.maxSortNoInGroup(groupName) + 1);
            p.setIsHero(false);
            repo.save(p);
            return new UploadResult(p, snippet(p));
        } catch (RuntimeException e) {
            deleteQuietly(target);
            deleteQuietly(origTarget);
            throw e;
        }
    }

    /** photos.js 两行片段（M2 半自动过渡用；M4 写回链路接管后废弃） */
    static String snippet(Photo p) {
        String date = p.getShotDate() == null ? "" : p.getShotDate().toString();
        return "P('" + p.getPid() + "', '" + date + "'),        // " + p.getGroupName()
                + "\n" + p.getPid() + ": " + p.getWidth() + " / " + p.getHeight() + ",";
    }

    public record UpdateParams(String groupName, LocalDate shotDate, Integer sortNo) {}

    /** 更新分组/日期/排序号（管理台表单总是提交全字段；shotDate 可空 = 清空日期） */
    @Transactional
    public Photo update(Long id, UpdateParams params) {
        Photo p = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("照片不存在"));
        if (params.groupName() == null || !GROUPS.contains(params.groupName())) {
            throw new IllegalArgumentException("分组必须是 STREET / LIGHT / DAILY 之一");
        }
        if (params.sortNo() == null || params.sortNo() < 0) throw new IllegalArgumentException("排序号无效");
        p.setGroupName(params.groupName());
        p.setShotDate(params.shotDate());
        p.setSortNo(params.sortNo());
        return repo.save(p);
    }

    /** 设头图：旧头图清零 + 新头图置一（实体操作而非 bulk update，避免一级缓存不同步把旧 hero 写回） */
    @Transactional
    public void setHero(Long id) {
        Photo target = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("照片不存在"));
        for (Photo h : repo.findByIsHeroTrue()) {
            h.setIsHero(false);
            repo.save(h);
        }
        target.setIsHero(true);
        repo.save(target);
    }

    /** 删除记录（事务内）；文件删除由调用方在事务提交后执行，失败不影响记录删除 */
    @Transactional
    public Photo deleteRecord(Long id) {
        Photo p = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("照片不存在"));
        repo.delete(p);
        return p;
    }

    /** 删除压缩产物 + 原始副本（容忍缺失）；返回失败清单，空 = 全部成功 */
    public List<String> deleteFiles(Photo p) {
        List<String> failed = new ArrayList<>();
        try {
            Files.deleteIfExists(frontendPublicDir.resolve("photos").resolve(p.getPid() + ".jpg"));
        } catch (IOException e) {
            failed.add("photos/" + p.getPid() + ".jpg");
        }
        if (Files.isDirectory(origDir)) {
            try (var stream = Files.list(origDir)) {
                stream.filter(f -> f.getFileName().toString().startsWith(p.getPid() + "-")).forEach(f -> {
                    try {
                        Files.deleteIfExists(f);
                    } catch (IOException e) {
                        failed.add(f.getFileName().toString());
                    }
                });
            } catch (IOException ignored) {
                // 遍历失败按残留处理，由调用方提示人工清理
            }
        }
        return failed;
    }

    private static String sanitize(String name) {
        if (name == null || name.isBlank()) return "upload";
        String cleaned = name.replaceAll("[\\\\/:*?\"<>|]", "_");
        return cleaned.length() > 80 ? cleaned.substring(cleaned.length() - 80) : cleaned;
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 补偿失败不掩盖主异常；孤儿文件可人工清理
        }
    }
}
