package rain.admin.publish;

import java.util.List;
import org.springframework.stereotype.Component;
import rain.admin.content.ProjectDetail;

/**
 * project_detail 表 → 前台 public/sitemap.xml（发布时自动重写）。
 * 手工维护曾脱节（forge/rain 上线后未收录），改为发布管线的一部分。
 * 刻意不带 lastmod：它随发布日期变化会造成"无改动也脏"的误报，URL 集合才是 sitemap 的有效信号。
 */
@Component
public class SitemapGenerator {

    private static final String BASE = "https://yuanyyyyyyyyy.github.io/rain";

    /** 固定页面与优先级；projects 段由 DB 生成（全部 0.9——骨架页也有 URL 就该收录） */
    private static final List<String[]> FIXED = List.of(
            new String[] {"/", "1.0"},
            new String[] {"/photography", "0.8"},
            new String[] {"/about", "0.6"});

    public String generate(List<ProjectDetail> details) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        for (String[] f : FIXED) {
            url(sb, f[0], f[1]);
        }
        // 与 SiteDataGenerator 相同的排序语义：sortNo 升序（null 靠后）→ id
        details.stream()
                .sorted(java.util.Comparator
                        .comparing(ProjectDetail::getSortNo, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                        .thenComparing(ProjectDetail::getId, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .forEach(d -> url(sb, "/projects/" + d.getSlug(), "0.9"));
        sb.append("</urlset>\n");
        return sb.toString();
    }

    private static void url(StringBuilder sb, String path, String priority) {
        sb.append("  <url><loc>").append(BASE).append(path)
                .append("</loc><priority>").append(priority).append("</priority></url>\n");
    }
}
