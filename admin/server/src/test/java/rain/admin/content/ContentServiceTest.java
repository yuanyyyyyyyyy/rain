package rain.admin.content;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;

/** 校验器是 M3 的核心卖点：非法数据必须在落库前被拦下，报错精确到节与字段 */
class ContentServiceTest {

    private ContentService service;
    private ProjectDetailRepo repo;
    private final ObjectMapper mapper = new ObjectMapper();
    private ProjectDetail existing;

    @BeforeEach
    void setUp() {
        repo = Mockito.mock(ProjectDetailRepo.class);
        service = new ContentService(repo, mapper, "target/unused");
        existing = new ProjectDetail();
        existing.setSlug("yunshu");
        existing.setHeadline("h");
        existing.setSummary("s");
        existing.setHeaderNote("note");
        existing.setMetaJson("[[\"角色\",\"v\"]]");
        existing.setSectionsJson("[]");
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repo.findBySlug("yunshu")).thenReturn(Optional.of(existing));
    }

    private JsonNode sections(String json) throws Exception {
        return mapper.readTree(json);
    }

    private ContentService.UpdateRequest req(String sectionsJson, String metaJson) throws Exception {
        return new ContentService.UpdateRequest("h", "s", "note",
                metaJson == null ? mapper.readTree("[]") : mapper.readTree(metaJson), sections(sectionsJson));
    }

    @Test
    void updatePassesValidSectionsAndRenumbersKicker() throws Exception {
        String secs = """
                [
                  {"type":"arch","kicker":"02 / ARCHITECTURE","component":"yunshu"},
                  {"type":"text","kicker":"05 / 收获","body":"x"}
                ]""";
        ProjectDetail saved = service.update("yunshu", req(secs, null));
        JsonNode out = mapper.readTree(saved.getSectionsJson());
        // 编号按位置重排：位置 0 → 02，位置 1 → 03（标题保留）
        assertEquals("02 / ARCHITECTURE", out.get(0).get("kicker").asText());
        assertEquals("03 / 收获", out.get(1).get("kicker").asText());
    }

    @Test
    void rejectsMissingRequiredFieldWithPreciseLocation() throws Exception {
        String secs = """
                [
                  {"type":"arch","kicker":"02 / A","component":"yunshu"},
                  {"type":"arch","kicker":"03 / B","component":"yunshu"},
                  {"type":"impl","kicker":"04 / 实现","items":[{"problem":"p","solution":"s","result":"r"}]}
                ]""";
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.update("yunshu", req(secs, null)));
        assertEquals("第 3 节[impl] 第 1 条：字段 title 不能为空", e.getMessage());
    }

    @Test
    void rejectsUnknownType() throws Exception {
        String secs = "[{\"type\":\"magic\",\"kicker\":\"02 / X\"}]";
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.update("yunshu", req(secs, null)));
        assertTrue(e.getMessage().contains("未知类型"));
    }

    @Test
    void rejectsBadKickerFormat() throws Exception {
        String secs = "[{\"type\":\"text\",\"kicker\":\"收获\",\"body\":\"x\"}]";
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.update("yunshu", req(secs, null)));
        assertTrue(e.getMessage().contains("kicker"));
    }

    @Test
    void rejectsInvalidMetaRow() throws Exception {
        String secs = "[{\"type\":\"text\",\"kicker\":\"02 / 收获\",\"body\":\"x\"}]";
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.update("yunshu", req(secs, "[[\"角色\"]]" /* 一元组 */)));
        assertTrue(e.getMessage().contains("meta 第 1 行"));
    }

    @Test
    void shotsPendingTrueSkipsItemsButChecksWantedType() throws Exception {
        String secs = """
                [
                  {"type":"shots","kicker":"03 / INTERFACE","pending":true,"wanted":["图1","图2"],"note":"打码"}
                ]""";
        assertDoesNotThrow(() -> service.update("yunshu", req(secs, null)));
    }

    @Test
    void shotsWithoutPendingRequiresItemsWithSrcAndCaption() throws Exception {
        String secs = """
                [
                  {"type":"shots","kicker":"03 / INTERFACE","pending":false,"items":[{"src":"a.png"}]}
                ]""";
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.update("yunshu", req(secs, null)));
        assertTrue(e.getMessage().contains("caption"));
    }

    @Test
    void exportIsPureJsonKeepingHeaderNote() throws Exception {
        existing.setSectionsJson("[{\"type\":\"text\",\"kicker\":\"02 / 收获\",\"body\":\"x\"}]");
        String json = service.export(existing);
        JsonNode root = mapper.readTree(json); // 纯数据 JSON，不再是 JS 模块
        assertEquals("yunshu", root.path("slug").asText());
        assertEquals("note", root.path("_headerNote").asText()); // 原头注释改存数据字段
        assertEquals("h", root.path("headline").asText());
        assertTrue(root.path("sections").isArray());
        assertEquals("02 / 收获", root.path("sections").get(0).path("kicker").asText());
        assertFalse(json.contains("export const"));
    }

    // ---- 卡片排序（2026-11-10 审计补入口：sortNo 此前无任何修改手段） ----

    private ProjectDetail detail(String slug) {
        ProjectDetail d = new ProjectDetail();
        d.setSlug(slug);
        d.setHeadline("h");
        d.setSummary("s");
        return d;
    }

    @Test
    void reorderWritesSortNoBySubmissionOrder() {
        when(repo.findAll()).thenReturn(List.of(detail("a"), detail("b")));
        when(repo.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        List<ProjectDetail> out = service.reorder(List.of("b", "a"));

        // 断言按 slug 对号：b 提交在前 → sortNo 1（首页卡片第一位）
        assertEquals(1, out.stream().filter(d -> "b".equals(d.getSlug())).findFirst().orElseThrow().getSortNo());
        assertEquals(2, out.stream().filter(d -> "a".equals(d.getSlug())).findFirst().orElseThrow().getSortNo());
    }

    @Test
    void reorderRejectsMissingSlug() {
        when(repo.findAll()).thenReturn(List.of(detail("a"), detail("b")));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.reorder(List.of("b")));
        assertTrue(e.getMessage().contains("全部项目"));
    }

    @Test
    void reorderRejectsUnknownSlug() {
        when(repo.findAll()).thenReturn(List.of(detail("a"), detail("b")));
        // 数量恰好相等但 slug 不在库（ghost 替换了 b）→ 必须报「不存在的 slug」而不是静默错排
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.reorder(List.of("a", "ghost")));
        assertTrue(e.getMessage().contains("不存在的 slug"));
    }

    @Test
    void listScreenshotsRejectsUnsafeSlug() {
        // slug 拼进文件路径，非白名单字符一律空数组（防 ../ 穿越列目录）
        assertTrue(service.listScreenshots("..").isEmpty());
        assertTrue(service.listScreenshots("..%2Fetc").isEmpty());
        assertTrue(service.listScreenshots("A_UPPER").isEmpty());
    }

    // ---- 截图直传（2026-11-11 F2）----

    @TempDir
    Path tempDir;

    private ContentService serviceWithTempDir() {
        return new ContentService(repo, mapper, tempDir.toString());
    }

    private MockMultipartFile png(String name, byte[] bytes) {
        return new MockMultipartFile("file", name, "image/png", bytes);
    }

    @Test
    void uploadScreenshotWritesFileAndLists() throws Exception {
        ContentService s = serviceWithTempDir();
        List<String> files = s.uploadScreenshot("yunshu", png("forge-05.png", new byte[]{1, 2, 3}));
        assertTrue(files.contains("forge-05.png"));
        assertTrue(java.nio.file.Files.exists(
                tempDir.resolve("screenshots").resolve("yunshu").resolve("forge-05.png")));
    }

    @Test
    void uploadScreenshotRejectsBadExtension() {
        ContentService s = serviceWithTempDir();
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> s.uploadScreenshot("yunshu", new MockMultipartFile("file", "a.gif", "image/gif", new byte[]{1})));
        assertTrue(e.getMessage().contains("仅支持"));
        assertThrows(IllegalArgumentException.class,
                () -> s.uploadScreenshot("yunshu", new MockMultipartFile("file", "a.exe", "application/octet-stream", new byte[]{1})));
    }

    @Test
    void uploadScreenshotRejectsOversize() {
        ContentService s = serviceWithTempDir();
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> s.uploadScreenshot("yunshu", png("big.png", new byte[2 * 1024 * 1024 + 1])));
        assertTrue(e.getMessage().contains("2MB"));
    }

    @Test
    void uploadScreenshotRejectsUnsafeSlug() {
        ContentService s = serviceWithTempDir();
        assertThrows(IllegalArgumentException.class, () -> s.uploadScreenshot("..%2Fetc", png("a.png", new byte[]{1})));
        assertThrows(IllegalArgumentException.class, () -> s.uploadScreenshot("A_UPPER", png("a.png", new byte[]{1})));
    }

    @Test
    void uploadScreenshotRejectsDuplicateName() throws Exception {
        ContentService s = serviceWithTempDir();
        s.uploadScreenshot("yunshu", png("dup.png", new byte[]{1}));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> s.uploadScreenshot("yunshu", png("dup.png", new byte[]{2})));
        assertTrue(e.getMessage().contains("同名文件已存在"));
    }

    @Test
    void uploadScreenshotSanitizesFilename() throws Exception {
        ContentService s = serviceWithTempDir();
        List<String> files = s.uploadScreenshot("yunshu", png("my 截图 (1).png", new byte[]{1}));
        // 非白名单字符替换为 _，不保留路径分隔符与空格
        assertEquals("my_____1_.png", files.get(0)); // space+截+图+space+( 共 5 个非白名单字符
    }
}
