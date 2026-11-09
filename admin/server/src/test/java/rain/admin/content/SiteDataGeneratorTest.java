package rain.admin.content;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** site.js 写回的契约：锚点行必须保留（否则下次写回失去定位）、卡片字段映射、单引号转义 */
class SiteDataGeneratorTest {

    private final SiteDataGenerator gen = new SiteDataGenerator(new ObjectMapper());

    private static final String SITE = """
            // 手写区
            export const apiBase = ''
            // === PROJECTS:AUTO-START ===（自动生成区）
            export const projects = [
              { slug: 'old', no: '001' },
            ]
            // === PROJECTS:AUTO-END ===
            export const capabilities = []""";

    private ProjectDetail detail(String slug, String headline, String metaJson, String sectionsJson) throws Exception {
        ProjectDetail d = new ProjectDetail();
        d.setSlug(slug);
        d.setHeadline(headline);
        d.setSummary("summary of " + slug);
        d.setMetaJson(metaJson);
        d.setSectionsJson(sectionsJson);
        return d;
    }

    @Test
    void replacesBetweenAnchorsAndKeepsBothMarkerLines() throws Exception {
        String out = gen.generate(java.util.List.of(
                detail("yunshu", "云枢", "[[\"周期\",\"2026.06 — 至今\"],[\"技术栈\",\"Java · Redis\"]]", "[]")), SITE);
        assertTrue(out.contains("PROJECTS:AUTO-START"));
        assertTrue(out.contains("PROJECTS:AUTO-END"));
        assertFalse(out.contains("'old'"));
        assertTrue(out.indexOf("PROJECTS:AUTO-START") < out.indexOf("export const projects"));
        // 锚点外的手写区原样保留
        assertTrue(out.contains("export const apiBase = ''"));
        assertTrue(out.contains("export const capabilities = []"));
        // 连续两次生成结果一致（锚点不丢失 → 可重复写回）
        String again = gen.generate(java.util.List.of(
                detail("yunshu", "云枢", "[[\"周期\",\"2026.06 — 至今\"],[\"技术栈\",\"Java · Redis\"]]", "[]")), out);
        assertEquals(out, again);
    }

    @Test
    void cardMappingUsesDedicatedMetaKeysWithFallback() throws Exception {
        // 项目名/卡片周期缺省时回退 headline/周期
        String out = gen.generate(java.util.List.of(
                detail("a", "详情大标题A", "[[\"周期\",\"2026.06 — 至今\"],[\"技术栈\",\"Java\"]]", "[]")), SITE);
        assertTrue(out.contains("title: '详情大标题A'"));
        assertTrue(out.contains("period: '2026.06 — 至今'"));

        String out2 = gen.generate(java.util.List.of(
                detail("b", "详情大标题B", "[[\"项目名\",\"卡片名B\"],[\"卡片周期\",\"2025\"],[\"周期\",\"长周期B\"],[\"技术栈\",\"Go\"]]", "[]")), SITE);
        assertTrue(out2.contains("title: '卡片名B'"));
        assertTrue(out2.contains("period: '2025'"));
        assertFalse(out2.contains("长周期B"));
    }

    @Test
    void flagshipOnFirstAndMetricsExtracted() throws Exception {
        String metrics = """
                [{"type":"metrics","kicker":"05 / 性能","items":[
                   {"label":"P99","value":"0.148s"},{"label":"吞吐","value":"2480"},{"label":"均响","value":"19.7ms"},{"label":"多余","value":"x"}]}]""";
        String out = gen.generate(java.util.List.of(
                detail("a", "A", "[[\"技术栈\",\"JAVA · REDIS\"]]", metrics),
                detail("b", "B", "[]", "[]")), SITE);
        assertTrue(out.contains("flagship: true"));
        assertTrue(out.contains("{ label: 'P99', value: '0.148s' }"));
        assertFalse(out.contains("多余")); // 只取前 3 条
        assertTrue(out.contains("tags: ['JAVA', 'REDIS']"));
    }

    @Test
    void escapesSingleQuotesAndBackslashes() throws Exception {
        String out = gen.generate(java.util.List.of(
                detail("a", "Little's Law 与 \\ 反斜杠", "[]", "[]")), SITE);
        assertTrue(out.contains("Little\\'s Law 与 \\\\ 反斜杠"));
    }

    @Test
    void refusesWithoutAnchors() {
        assertThrows(IllegalStateException.class,
                () -> gen.generate(java.util.List.of(), "export const projects = []"));
    }
}
