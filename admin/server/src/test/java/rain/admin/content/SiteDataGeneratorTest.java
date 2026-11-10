package rain.admin.content;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** content/projects.json 的契约：卡片字段映射、flagship、metrics 截断、排序、文案原样保留 */
class SiteDataGeneratorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final SiteDataGenerator gen = new SiteDataGenerator(mapper);

    private ProjectDetail detail(String slug, String headline, String metaJson, String sectionsJson) {
        ProjectDetail d = new ProjectDetail();
        d.setSlug(slug);
        d.setHeadline(headline);
        d.setSummary("summary of " + slug);
        d.setMetaJson(metaJson);
        d.setSectionsJson(sectionsJson);
        return d;
    }

    private JsonNode parse(String json) throws Exception {
        return mapper.readTree(json);
    }

    @Test
    void generatesJsonArrayWithCardMapping() throws Exception {
        JsonNode arr = parse(gen.generate(java.util.List.of(
                detail("yunshu", "云枢", "[[\"周期\",\"2026.06 — 至今\"],[\"技术栈\",\"Java · Redis\"]]", "[]"))));
        assertTrue(arr.isArray());
        assertEquals(1, arr.size());
        JsonNode c = arr.get(0);
        assertEquals("yunshu", c.path("slug").asText());
        assertEquals("001", c.path("no").asText());
        assertTrue(c.path("flagship").asBoolean());
        assertEquals("云枢", c.path("title").asText()); // 无「项目名」时回退 headline
        assertEquals("2026.06 — 至今", c.path("period").asText());
        assertEquals("summary of yunshu", c.path("summary").asText());
        assertEquals("JAVA", c.path("tags").get(0).asText());
        assertEquals("REDIS", c.path("tags").get(1).asText());
    }

    @Test
    void cardMappingUsesDedicatedMetaKeysWithFallback() throws Exception {
        JsonNode a = parse(gen.generate(java.util.List.of(
                detail("a", "详情大标题A", "[[\"周期\",\"2026.06 — 至今\"],[\"技术栈\",\"Java\"]]", "[]")))).get(0);
        assertEquals("详情大标题A", a.path("title").asText());
        assertEquals("2026.06 — 至今", a.path("period").asText());

        JsonNode b = parse(gen.generate(java.util.List.of(
                detail("b", "详情大标题B",
                        "[[\"项目名\",\"卡片名B\"],[\"卡片周期\",\"2025\"],[\"周期\",\"长周期B\"],[\"技术栈\",\"Go\"]]", "[]")))).get(0);
        assertEquals("卡片名B", b.path("title").asText());
        assertEquals("2025", b.path("period").asText());
        assertFalse(b.toString().contains("长周期B"));
    }

    @Test
    void flagshipOnFirstAndMetricsExtracted() throws Exception {
        String metrics = """
                [{"type":"metrics","kicker":"05 / 性能","items":[
                   {"label":"P99","value":"0.148s"},{"label":"吞吐","value":"2480"},{"label":"均响","value":"19.7ms"},{"label":"多余","value":"x"}]}]""";
        JsonNode arr = parse(gen.generate(java.util.List.of(
                detail("a", "A", "[[\"技术栈\",\"JAVA · REDIS\"]]", metrics),
                detail("b", "B", "[]", "[]"))));
        assertTrue(arr.get(0).path("flagship").asBoolean());
        assertFalse(arr.get(1).has("flagship"));
        JsonNode ms = arr.get(0).path("metrics");
        assertEquals(3, ms.size()); // 只取前 3 条
        assertEquals("P99", ms.get(0).path("label").asText());
        assertEquals("0.148s", ms.get(0).path("value").asText());
        assertFalse(ms.toString().contains("多余"));
        assertEquals("JAVA", arr.get(0).path("tags").get(0).asText());
    }

    @Test
    void keepsQuotesAndBackslashesVerbatim() throws Exception {
        JsonNode arr = parse(gen.generate(java.util.List.of(
                detail("a", "Little's Law 与 \\ 反斜杠", "[]", "[]"))));
        // JSON 不需要手写单引号转义，解析后文案原样回来
        assertEquals("Little's Law 与 \\ 反斜杠", arr.get(0).path("title").asText());
    }

    @Test
    void ordersBySortNoWithNullsLast() throws Exception {
        ProjectDetail a = detail("a", "A-null", "[]", "[]");
        ProjectDetail b = detail("b", "B-2", "[]", "[]"); b.setSortNo(2);
        ProjectDetail c = detail("c", "C-1", "[]", "[]"); c.setSortNo(1);
        JsonNode arr = parse(gen.generate(java.util.List.of(b, a, c)));
        assertEquals("c", arr.get(0).path("slug").asText());
        assertEquals("b", arr.get(1).path("slug").asText());
        assertEquals("a", arr.get(2).path("slug").asText());
        assertTrue(arr.get(0).path("flagship").asBoolean());
    }

    @Test
    void emptyListProducesEmptyArray() throws Exception {
        JsonNode arr = parse(gen.generate(java.util.List.of()));
        assertTrue(arr.isArray());
        assertEquals(0, arr.size());
    }
}
