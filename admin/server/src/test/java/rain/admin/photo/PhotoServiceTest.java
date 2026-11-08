package rain.admin.photo;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;

/** 覆盖上传管线的关键纪律：pid 冲突防御、孤儿文件补偿、只缩不放 */
class PhotoServiceTest {

    @TempDir
    Path tmp;

    private PhotoService service;
    private PhotoRepo repo;
    private Path publicDir;
    private Path origDir;

    @BeforeEach
    void setUp() {
        repo = Mockito.mock(PhotoRepo.class);
        publicDir = tmp.resolve("public");
        origDir = tmp.resolve("orig");
        service = new PhotoService(repo, publicDir.toString(), origDir.toString());
    }

    private static byte[] jpegBytes(int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", out);
        return out.toByteArray();
    }

    @Test
    void rejectsNonImageContentType() {
        MockMultipartFile file = new MockMultipartFile("file", "x.txt", "text/plain", "hi".getBytes());
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.upload(file, null, "STREET"));
        assertTrue(e.getMessage().contains("仅支持图片"));
    }

    @Test
    void rejectsInvalidGroup() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "x.jpg", "image/jpeg", jpegBytes(100, 80));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.upload(file, null, "FOO"));
        assertTrue(e.getMessage().contains("STREET / LIGHT / DAILY"));
    }

    @Test
    void pidConflictRefusesInsteadOfOverwriting() throws Exception {
        // 表为空 → 生成 p01；但磁盘上已有 p01.jpg（种子未导入场景），必须拒绝且原文件分毫不动
        when(repo.maxPidNumber()).thenReturn(0);
        Files.createDirectories(publicDir.resolve("photos"));
        byte[] original = "KEEP ME".getBytes();
        Files.write(publicDir.resolve("photos").resolve("p01.jpg"), original);
        MockMultipartFile file = new MockMultipartFile("file", "x.jpg", "image/jpeg", jpegBytes(100, 80));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.upload(file, null, "STREET"));
        assertTrue(e.getMessage().contains("已存在"));
        assertArrayEquals(original, Files.readAllBytes(publicDir.resolve("photos").resolve("p01.jpg")));
    }

    @Test
    void uploadScalesDownButNeverUp() throws Exception {
        when(repo.maxPidNumber()).thenReturn(0);
        when(repo.maxSortNoInGroup(anyString())).thenReturn(0);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // 长边 2000 → 缩到 1600
        MockMultipartFile big = new MockMultipartFile("file", "big.jpg", "image/jpeg", jpegBytes(2000, 1000));
        PhotoService.UploadResult r1 = service.upload(big, null, "STREET");
        assertEquals(1600, r1.photo().getWidth());
        assertEquals(800, r1.photo().getHeight());
        assertTrue(Files.exists(publicDir.resolve("photos").resolve("p01.jpg")));
        assertTrue(Files.exists(origDir)); // 原始副本已留档

        // 长边 600 → 不放大，保持原尺寸
        when(repo.maxPidNumber()).thenReturn(1);
        MockMultipartFile small = new MockMultipartFile("file", "small.jpg", "image/jpeg", jpegBytes(600, 400));
        PhotoService.UploadResult r2 = service.upload(small, null, "STREET");
        assertEquals(600, r2.photo().getWidth());
        assertEquals(400, r2.photo().getHeight());
    }

    @Test
    void dbFailureDeletesOrphanFiles() throws Exception {
        when(repo.maxPidNumber()).thenReturn(0);
        when(repo.maxSortNoInGroup(anyString())).thenReturn(0);
        when(repo.save(any())).thenThrow(new RuntimeException("db down"));
        MockMultipartFile file = new MockMultipartFile("file", "x.jpg", "image/jpeg", jpegBytes(100, 80));

        assertThrows(RuntimeException.class, () -> service.upload(file, null, "STREET"));
        // 孤儿补偿：压缩产物与原始副本都被清掉
        assertTrue(Files.notExists(publicDir.resolve("photos").resolve("p01.jpg")));
        try (var s = Files.list(origDir)) {
            assertEquals(0, s.count());
        }
    }

    @Test
    void snippetFollowsPhotosJsConventions() throws Exception {
        when(repo.maxPidNumber()).thenReturn(0);
        when(repo.maxSortNoInGroup(anyString())).thenReturn(0);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        MockMultipartFile file = new MockMultipartFile("file", "x.jpg", "image/jpeg", jpegBytes(100, 80));
        PhotoService.UploadResult r = service.upload(file, java.time.LocalDate.of(2026, 1, 7), "LIGHT");
        assertEquals("P('p01', '2026-01-07'),        // LIGHT\np01: 100 / 80,", r.snippet());
    }
}
