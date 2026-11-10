package rain.admin.content;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * project_detail 表 → 前台 content/projects.json（首页项目卡片数组）。
 * 内容与代码分层：不再往 site.js 的锚点区间拼 JS 字符串，手写配置独立放 content/site.json，
 * 本生成器只写纯数据 JSON，单引号/反斜杠/换行由 Jackson 负责，不再需要 escape()。
 * 卡片字段映射：title←headline、period←meta["周期"]、tags←meta["技术栈"]按"·"拆分、
 * metrics←metrics 节前 3 条（label/value）、flagship←首个项目。
 */
@Component
public class SiteDataGenerator {

    private final ObjectMapper mapper;

    public SiteDataGenerator(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String generate(List<ProjectDetail> details) {
        // 卡片顺序：sortNo 升序（null 靠后）→ id 升序稳定收尾；不依赖 DB findAll 的未定义顺序
        List<ProjectDetail> ordered = details.stream()
                .sorted(Comparator
                        .comparing(ProjectDetail::getSortNo, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ProjectDetail::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        ArrayNode cards = mapper.createArrayNode();
        for (int i = 0; i < ordered.size(); i++) {
            ProjectDetail d = ordered.get(i);
            try {
                ObjectNode c = cards.addObject();
                JsonNode meta = mapper.readTree(d.getMetaJson());
                String cardTitle = firstNonBlank(metaValue(meta, "项目名"), d.getHeadline());
                String period = firstNonBlank(metaValue(meta, "卡片周期"), metaValue(meta, "周期"));
                String tagsRaw = metaValue(meta, "技术栈");

                c.put("slug", d.getSlug());
                c.put("no", String.format("%03d", i + 1));
                if (i == 0) c.put("flagship", true);
                if (!period.isBlank()) c.put("period", period);
                c.put("title", cardTitle);
                c.put("summary", d.getSummary());
                if (!tagsRaw.isBlank()) {
                    ArrayNode tags = c.putArray("tags");
                    for (String t : tagsRaw.split("·")) tags.add(t.trim().toUpperCase());
                }
                JsonNode metricsItems = findMetricsItems(d);
                if (metricsItems != null && metricsItems.isArray() && metricsItems.size() > 0) {
                    ArrayNode ms = c.putArray("metrics");
                    int n = Math.min(3, metricsItems.size());
                    for (int j = 0; j < n; j++) {
                        JsonNode it = metricsItems.get(j);
                        ObjectNode m = ms.addObject();
                        m.put("label", it.path("label").asText());
                        m.put("value", it.path("value").asText());
                    }
                }
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("项目卡片 JSON 序列化失败：" + d.getSlug(), e);
            }
        }
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(cards) + "\n";
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("项目卡片 JSON 序列化失败", e);
        }
    }

    private String metaValue(JsonNode meta, String label) {
        if (meta == null || !meta.isArray()) return "";
        for (JsonNode row : meta) {
            if (row.isArray() && row.size() == 2 && label.equals(row.get(0).asText())) {
                return row.get(1).asText();
            }
        }
        return "";
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : (b == null ? "" : b);
    }

    private JsonNode findMetricsItems(ProjectDetail d) throws JsonProcessingException {
        JsonNode sections = mapper.readTree(d.getSectionsJson());
        for (JsonNode s : sections) {
            if ("metrics".equals(s.path("type").asText())) {
                return s.path("items");
            }
        }
        return null;
    }
}
