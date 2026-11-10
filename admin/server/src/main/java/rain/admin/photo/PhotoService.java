package rain.admin.photo;

import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
 * 拍摄日期自动提取链（任务 B）：表单手填 > EXIF DateTimeOriginal > 文件修改时间；
 * EXIF Orientation 5-8 上传时旋转像素（压缩产物不带 EXIF，浏览器无法自行摆正）。
 */
@Service
public class PhotoService {

    private static final int LONG_EDGE = 1600;
    private static final double QUALITY = 0.8;
    private static final int THUMB_EDGE = 640;
    // q85（2026-11-10 由 q70 提升）：q70 单张仅 0.5~1.7 bit/px，夜景渐变出现明显涂抹与色带。
    // 该档只服务窄视口 / 小屏，桌面由前台 srcset 直接取 1600px 原图，画质不打折。
    private static final double THUMB_QUALITY = 0.85;
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

    /** dateSource：manual（表单手填）/ exif（EXIF 拍摄时间）/ mtime（文件修改时间兜底） */
    public record UploadResult(Photo photo, String dateSource) {}

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

        // 原始副本先落盘（留档，EXIF 读取 / 压缩与宽高读取都从它走，避免 MultipartFile 流只能读一次的问题）
        Files.createDirectories(origDir);
        Path origTarget = origDir.resolve(pid + "-" + sanitize(file.getOriginalFilename()));
        Files.copy(file.getInputStream(), origTarget, StandardCopyOption.REPLACE_EXISTING);

        // 拍摄日期与方向
        PhotoExifReader.ExifData exif = PhotoExifReader.read(origTarget);

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
        // 竖拍（Orientation 5-8）：旋转像素摆正，压缩产物不带 EXIF，必须自行处理
        if (PhotoExifReader.swapsDimensions(exif.orientation())) {
            scaled = rotateForOrientation(scaled, exif.orientation());
        }
        Thumbnails.of(scaled).scale(1.0).outputQuality(QUALITY).outputFormat("jpg").toFile(target.toFile());
        writeThumb(pid, target); // 网格轻量档（640px q85，供窄视口）；桌面网格与 Lightbox 均取 1600px 原图

        // 入库；失败则删除两个文件补偿
        try {
            LocalDate date;
            String source;
            if (shotDate != null) {
                date = shotDate;
                source = "manual";
            } else if (exif.shotDate() != null) {
                date = exif.shotDate();
                source = "exif";
            } else {
                date = lastModifiedLocalDate(origTarget);
                source = "mtime";
            }
            Photo p = new Photo();
            p.setPid(pid);
            p.setOrigName(file.getOriginalFilename());
            p.setShotDate(date);
            p.setWidth(scaled.getWidth());
            p.setHeight(scaled.getHeight());
            p.setGroupName(groupName);
            p.setSortNo(repo.maxSortNoInGroup(groupName) + 1);
            p.setIsHero(false);
            repo.save(p);
            return new UploadResult(p, source);
        } catch (RuntimeException e) {
            deleteQuietly(target);
            deleteQuietly(thumbsDir().resolve(pid + ".jpg"));
            deleteQuietly(origTarget);
            throw e;
        }
    }

    private static LocalDate lastModifiedLocalDate(Path file) {
        try {
            return LocalDateTime.ofInstant(Files.getLastModifiedTime(file).toInstant(), ZoneId.systemDefault())
                    .toLocalDate();
        } catch (IOException e) {
            return LocalDate.now();
        }
    }

    /** 缩略图输出目录：public/photos/thumbs/（在发布 commit 范围 public/photos 内） */
    public Path thumbsDir() {
        return frontendPublicDir.resolve("photos").resolve("thumbs");
    }

    /** 生成 640px q85 轻量档（从 1600px 压缩产物缩，等比且已按 EXIF 摆正）；失败不阻断主流程 */
    public boolean writeThumb(String pid, Path compressedSource) {
        try {
            Path thumb = thumbsDir().resolve(pid + ".jpg");
            if (Files.exists(thumb)) return true;
            Files.createDirectories(thumbsDir());
            BufferedImage src = ImageIO.read(compressedSource.toFile());
            if (src == null) return false;
            BufferedImage scaled = src;
            if (Math.max(src.getWidth(), src.getHeight()) > THUMB_EDGE) {
                scaled = Thumbnails.of(src).size(THUMB_EDGE, THUMB_EDGE).asBufferedImage();
            }
            Thumbnails.of(scaled).scale(1.0).outputQuality(THUMB_QUALITY).outputFormat("jpg").toFile(thumb.toFile());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 启动时补齐存量照片的缩略图（一次性回填，幂等）；返回生成数量 */
    public int backfillThumbs() {
        Path photosDir = frontendPublicDir.resolve("photos");
        if (!Files.isDirectory(photosDir)) return 0;
        int n = 0;
        try (var stream = Files.list(photosDir)) {
            for (Path p : stream.filter(f -> f.getFileName().toString().matches("p\\d+\\.jpg")).toList()) {
                String pid = p.getFileName().toString().replace(".jpg", "");
                if (writeThumb(pid, p)) n++;
            }
        } catch (IOException ignored) {
            // 回填失败不阻断启动；下次启动重试
        }
        return n;
    }

    /**
     * EXIF Orientation → 像素旋转（AffineTransform，y 轴向下坐标系）：
     * 6=顺时针 90°，8=逆时针 90°，5=主对角线转置，7=副对角线转置。
     */
    private static BufferedImage rotateForOrientation(BufferedImage src, int orientation) {
        int w = src.getWidth();
        int h = src.getHeight();
        // AffineTransform(m00, m10, m01, m11, m02, m12)：x' = m00·x + m01·y + m02；y' = m10·x + m11·y + m12
        AffineTransform at = switch (orientation) {
            case 6 -> new AffineTransform(0, 1, -1, 0, h, 0);
            case 8 -> new AffineTransform(0, -1, 1, 0, 0, w);
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
            case 7 -> new AffineTransform(0, -1, -1, 0, h, w);
            default -> null;
        };
        if (at == null) return src;
        BufferedImage dst = new BufferedImage(h, w, src.getType() == 0 ? BufferedImage.TYPE_INT_RGB : src.getType());
        new AffineTransformOp(at, AffineTransformOp.TYPE_BICUBIC).filter(src, dst);
        return dst;
    }

    public record UpdateParams(String groupName, LocalDate shotDate, Integer sortNo, String caption) {}

    /** 更新分组/日期/排序号/图注（管理台表单总是提交全字段；shotDate 可空 = 清空日期） */
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
        p.setCaption(params.caption() == null || params.caption().isBlank() ? null : params.caption().trim());
        return repo.save(p);
    }

    public record ReorderItem(Long id, String groupName, Integer sortNo) {}

    /** 批量重排（拖拽排序后一次请求写回，替代逐张 PUT；groupName 允许跨组移动） */
    @Transactional
    public int reorder(List<ReorderItem> items) {
        if (items == null) throw new IllegalArgumentException("参数无效");
        for (ReorderItem it : items) {
            if (it.id() == null || it.sortNo() == null || it.sortNo() < 0) {
                throw new IllegalArgumentException("排序参数无效：id/sortNo 必填且 sortNo ≥ 0");
            }
        }
        int n = 0;
        for (ReorderItem it : items) {
            Photo p = repo.findById(it.id()).orElseThrow(() -> new IllegalArgumentException("照片不存在：" + it.id()));
            if (it.groupName() != null) {
                if (!GROUPS.contains(it.groupName())) {
                    throw new IllegalArgumentException("分组必须是 STREET / LIGHT / DAILY 之一");
                }
                p.setGroupName(it.groupName());
            }
            p.setSortNo(it.sortNo());
            repo.save(p);
            n++;
        }
        return n;
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

    /** 删除压缩产物 + 缩略图 + 原始副本（容忍缺失）；返回失败清单，空 = 全部成功 */
    public List<String> deleteFiles(Photo p) {
        List<String> failed = new ArrayList<>();
        try {
            Files.deleteIfExists(frontendPublicDir.resolve("photos").resolve(p.getPid() + ".jpg"));
        } catch (IOException e) {
            failed.add("photos/" + p.getPid() + ".jpg");
        }
        try {
            Files.deleteIfExists(thumbsDir().resolve(p.getPid() + ".jpg"));
        } catch (IOException e) {
            failed.add("photos/thumbs/" + p.getPid() + ".jpg");
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
