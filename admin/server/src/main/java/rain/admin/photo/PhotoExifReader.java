package rain.admin.photo;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * EXIF 读取：上传时自动提取拍摄日期与方向，省去手填（任务 B）。
 * 语义决定：DateTimeOriginal 是相机本地时间，直接取其日期部分作为拍摄日期——
 * 相册语义「这张照片是哪天拍的」以拍摄地为准，不换算时区（跨时区拍摄也符合直觉）。
 * 任何解析失败（非 JPEG / 无 EXIF / 字段缺失）都静默回退 null，由调用方走 mtime 兜底。
 */
public final class PhotoExifReader {

    private PhotoExifReader() {}

    /** @param orientation EXIF Orientation 原始值（1-8，null = 未知） */
    public record ExifData(LocalDate shotDate, Integer orientation) {}

    private static final DateTimeFormatter EXIF_DATETIME = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss");

    public static ExifData read(Path file) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(file.toFile());

            LocalDate date = null;
            ExifSubIFDDirectory sub = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            if (sub != null) {
                String dt = sub.getString(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
                if (dt != null && !dt.isBlank()) {
                    try {
                        date = LocalDate.parse(dt.trim(), EXIF_DATETIME);
                    } catch (DateTimeParseException ignored) {
                        // 日期格式异常按缺失处理
                    }
                }
            }

            Integer orientation = null;
            ExifIFD0Directory ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (ifd0 != null && ifd0.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
                orientation = ifd0.getInteger(ExifIFD0Directory.TAG_ORIENTATION);
            }
            return new ExifData(date, orientation);
        } catch (Exception e) {
            return new ExifData(null, null);
        }
    }

    /** Orientation 5-8 含 90° 旋转，宽高需要互换（2/3/4 是镜像或 180°，不影响宽高） */
    public static boolean swapsDimensions(Integer orientation) {
        return orientation != null && orientation >= 5 && orientation <= 8;
    }
}
