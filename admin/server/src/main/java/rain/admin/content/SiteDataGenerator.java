package rain.admin.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * project_detail 表 → 前台 src/data/site.js 的 projects 数组（锚点替换）。
 * 只替换 PROJECTS:AUTO-START/END 标记之间的内容；profile/capabilities/apiBase 等手写区原样保留。
 * 卡片字段映射：title←headline、period←meta["周期"]、tags←meta["技术栈"]按"·"拆分、
 * metrics←metrics 节前 3 条（label/value）、flagship←首个项目。
 */
@Component
public class SiteDataGenerator {

    private static final String START = "// === PROJECTS:AUTO-START ===";
    private static final String END = "// === PROJECTS:AUTO-END ===";

    private final ObjectMapper mapper;

    public SiteDataGenerator(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** 生成替换后的 site.js 全文；siteContent 为当前文件内容（提供锚点）。锚点行本身必须保留，否则下次写回失去定位。 */
    public String generate(List<ProjectDetail> details, String siteContent) throws IOException {
        int start = siteContent.indexOf(START);
        int end = siteContent.indexOf(END);
        if (start < 0 || end < 0 || end < start) {
            throw new IllegalStateException("site.js 缺少 PROJECTS:AUTO 锚点标记，无法写回");
        }
        StringBuilder cards = new StringBuilder();
        cards.append("export const projects = [\n");
        for (int i = 0; i < details.size(); i++) {
            cards.append(card(details.get(i), i)).append(i < details.size() - 1 ? ",\n" : "\n");
        }
        cards.append("]");
        return siteContent.substring(0, start) + START + "\n" + cards
                + "\n" + siteContent.substring(end);
    }

    private String card(ProjectDetail d, int index) throws IOException {
        JsonNode meta = mapper.readTree(d.getMetaJson());
        // 卡片标题/周期用专用 meta 键（与详情页 headline/长周期解耦），缺省回退
        String cardTitle = firstNonBlank(metaValue(meta, "项目名"), d.getHeadline());
        String period = firstNonBlank(metaValue(meta, "卡片周期"), metaValue(meta, "周期"));
        String tagsRaw = metaValue(meta, "技术栈");
        JsonNode metricsItems = findMetricsItems(d);

        StringBuilder c = new StringBuilder("  {\n");
        c.append("    slug: '").append(d.getSlug()).append("',\n");
        c.append("    no: '").append(String.format("%03d", index + 1)).append("',\n");
        if (index == 0) c.append("    flagship: true,\n");
        if (!period.isBlank()) c.append("    period: '").append(escape(period)).append("',\n");
        c.append("    title: '").append(escape(cardTitle)).append("',\n");
        c.append("    summary:\n      '").append(escape(d.getSummary())).append("',\n");
        if (!tagsRaw.isBlank()) {
            StringBuilder tags = new StringBuilder();
            for (String t : tagsRaw.split("·")) {
                tags.append("'").append(escape(t.trim().toUpperCase())).append("', ");
            }
            c.append("    tags: [").append(tags.toString().replaceAll(", $", "")).append("],\n");
        }
        if (metricsItems != null && metricsItems.isArray() && metricsItems.size() > 0) {
            c.append("    metrics: [\n");
            int n = Math.min(3, metricsItems.size());
            for (int j = 0; j < n; j++) {
                JsonNode it = metricsItems.get(j);
                c.append("      { label: '").append(escape(it.path("label").asText()))
                        .append("', value: '").append(escape(it.path("value").asText())).append("' },\n");
            }
            c.append("    ],\n");
        }
        c.append("  }");
        return c.toString();
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

    private JsonNode findMetricsItems(ProjectDetail d) throws IOException {
        JsonNode sections = mapper.readTree(d.getSectionsJson());
        for (JsonNode s : sections) {
            if ("metrics".equals(s.path("type").asText())) {
                return s.path("items");
            }
        }
        return null;
    }

    /** 单引号转义（生成 JS 单引号字符串） */
    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("'", "\\'");
    }
}
