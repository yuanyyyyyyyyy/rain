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
    private PhotoGroupRepo groupRepo;
    private Path publicDir;
    private Path origDir;

    @BeforeEach
    void setUp() {
        repo = Mockito.mock(PhotoRepo.class);
        groupRepo = Mockito.mock(PhotoGroupRepo.class);
        when(groupRepo.existsByGroupKey(anyString())).thenReturn(true);
        publicDir = tmp.resolve("public");
        origDir = tmp.resolve("orig");
        service = new PhotoService(repo, groupRepo, publicDir.toString(), origDir.toString());
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
        when(groupRepo.existsByGroupKey("FOO")).thenReturn(false);
        MockMultipartFile file = new MockMultipartFile("file", "x.jpg", "image/jpeg", jpegBytes(100, 80));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.upload(file, null, "FOO"));
        assertTrue(e.getMessage().contains("分组不存在"));
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

    /** 读取 test/resources/exif/ 下的夹具 */
    private static MockMultipartFile fixture(String name) throws Exception {
        byte[] bytes = Files.readAllBytes(Path.of(PhotoServiceTest.class.getResource("/exif/" + name).toURI()));
        return new MockMultipartFile("file", name, "image/jpeg", bytes);
    }

    @Test
    void uploadReadsExifDate() throws Exception {
        when(repo.maxPidNumber()).thenReturn(0);
        when(repo.maxSortNoInGroup(anyString())).thenReturn(0);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PhotoService.UploadResult r = service.upload(fixture("exif-datetime.jpg"), null, "STREET");
        assertEquals(java.time.LocalDate.of(2024, 5, 1), r.photo().getShotDate());
        assertEquals("exif", r.dateSource());
    }

    @Test
    void paramDateWinsOverExif() throws Exception {
        when(repo.maxPidNumber()).thenReturn(0);
        when(repo.maxSortNoInGroup(anyString())).thenReturn(0);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PhotoService.UploadResult r = service.upload(fixture("exif-datetime.jpg"),
                java.time.LocalDate.of(2020, 1, 1), "STREET");
        assertEquals(java.time.LocalDate.of(2020, 1, 1), r.photo().getShotDate());
        assertEquals("manual", r.dateSource());
    }

    @Test
    void noExifFallsBackToMtime() throws Exception {
        when(repo.maxPidNumber()).thenReturn(0);
        when(repo.maxSortNoInGroup(anyString())).thenReturn(0);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PhotoService.UploadResult r = service.upload(
                new MockMultipartFile("file", "plain.jpg", "image/jpeg", jpegBytes(100, 80)), null, "STREET");
        // 夹具刚写入 = 今天；mtime 兜底来源标注
        assertEquals(java.time.LocalDate.now(), r.photo().getShotDate());
        assertEquals("mtime", r.dateSource());
    }

    @Test
    void orientation6RotatesPixelsAndSwapsDims() throws Exception {
        when(repo.maxPidNumber()).thenReturn(0);
        when(repo.maxSortNoInGroup(anyString())).thenReturn(0);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // 原始像素 600x400 + Orientation 6 → 顺时针 90° 摆正为 400x600
        PhotoService.UploadResult r = service.upload(fixture("exif-oriented6.jpg"), null, "STREET");
        assertEquals(400, r.photo().getWidth());
        assertEquals(600, r.photo().getHeight());

        // 产物实际尺寸与库一致（读回压缩产物验证）
        BufferedImage out = ImageIO.read(publicDir.resolve("photos").resolve("p01.jpg").toFile());
        assertEquals(400, out.getWidth());
        assertEquals(600, out.getHeight());
    }

    @Test
    void updateSetsCaption() throws Exception {
        Photo p = new Photo();
        p.setPid("p01");
        p.setGroupName("STREET");
        p.setSortNo(1);
        p.setWidth(100);
        p.setHeight(80);
        when(repo.findById(1L)).thenReturn(java.util.Optional.of(p));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Photo updated = service.update(1L, new PhotoService.UpdateParams("LIGHT", null, 2, "  雨后的涩谷  "));
        assertEquals("LIGHT", updated.getGroupName());
        assertEquals("雨后的涩谷", updated.getCaption());
        // 空白图注 = 清空
        Photo cleared = service.update(1L, new PhotoService.UpdateParams("LIGHT", null, 2, "   "));
        assertNull(cleared.getCaption());
    }

    @Test
    void uploadStoresAllDateCandidatesAndHonorsRequestedSource() throws Exception {
        when(repo.maxPidNumber()).thenReturn(0);
        when(repo.maxSortNoInGroup(anyString())).thenReturn(0);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PhotoService.UploadResult r = service.upload(
                new MockMultipartFile("file", "plain.jpg", "image/jpeg", jpegBytes(100, 80)),
                null, "STREET", "upload");
        assertEquals(java.time.LocalDate.now(), r.photo().getShotDate());
        assertEquals("upload", r.dateSource());
        assertEquals(java.time.LocalDate.now(), r.photo().getUploadDate());
        assertNotNull(r.photo().getFileDate()); // 文件时间候选也留库
        assertNull(r.photo().getExifDate());    // 该夹具无 EXIF
    }

    @Test
    void uploadRejectsUnavailableSourceAndCleansOrphans() throws Exception {
        when(repo.maxPidNumber()).thenReturn(0);
        when(repo.maxSortNoInGroup(anyString())).thenReturn(0);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        MockMultipartFile file = new MockMultipartFile("file", "plain.jpg", "image/jpeg", jpegBytes(100, 80));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.upload(file, null, "STREET", "exif"));
        assertTrue(e.getMessage().contains("没有 EXIF"));
        assertTrue(Files.notExists(publicDir.resolve("photos").resolve("p01.jpg")));
    }

    @Test
    void updateSwitchesSourceWithoutLosingCandidates() {
        Photo p = new Photo();
        p.setPid("p01");
        p.setGroupName("STREET");
        p.setSortNo(1);
        p.setWidth(100);
        p.setHeight(80);
        p.setExifDate(java.time.LocalDate.of(2024, 5, 1));
        p.setFileDate(java.time.LocalDate.of(2026, 1, 1));
        p.setUploadDate(java.time.LocalDate.of(2026, 1, 2));
        p.setManualDate(java.time.LocalDate.of(2020, 1, 1));
        p.setShotDate(p.getFileDate());
        p.setDateSource("mtime");
        when(repo.findById(1L)).thenReturn(java.util.Optional.of(p));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Photo exif = service.update(1L,
                new PhotoService.UpdateParams("STREET", null, 1, null, "exif"));
        assertEquals(java.time.LocalDate.of(2024, 5, 1), exif.getShotDate());
        assertEquals("exif", exif.getDateSource());
        assertEquals(java.time.LocalDate.of(2026, 1, 1), exif.getFileDate()); // 候选不丢

        Photo manual = service.update(1L,
                new PhotoService.UpdateParams("STREET", java.time.LocalDate.of(2019, 3, 3), 1, null, "manual"));
        assertEquals(java.time.LocalDate.of(2019, 3, 3), manual.getShotDate());
        assertEquals("manual", manual.getDateSource());
    }
}
