package rain.admin.publish;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import rain.admin.content.ProjectDetail;

/** sitemap 生成契约：固定页 + projects 按卡片排序语义、无 lastmod（防跨天误报脏） */
class SitemapGeneratorTest {

    private final SitemapGenerator gen = new SitemapGenerator();

    private ProjectDetail detail(String slug, Integer sortNo) {
        ProjectDetail d = new ProjectDetail();
        d.setSlug(slug);
        d.setSortNo(sortNo);
        return d;
    }

    @Test
    void generatesFixedPagesAndOrderedProjects() {
        List<ProjectDetail> details = new ArrayList<>();
        details.add(detail("forge", 3));
        details.add(detail("yunshu", 1));
        ProjectDetail noSort = detail("newproj", null);
        details.add(noSort);
        details.add(detail("intelnexus", 2));

        String xml = gen.generate(details);
        assertTrue(xml.startsWith("<?xml"));
        assertTrue(xml.contains("<loc>https://yuanyyyyyyyyy.github.io/rain/</loc><priority>1.0</priority>"));
        assertTrue(xml.contains("<loc>https://yuanyyyyyyyyy.github.io/rain/photography</loc>"));
        // projects 按 sortNo 排序，null 靠后
        int yunshu = xml.indexOf("/projects/yunshu");
        int intel = xml.indexOf("/projects/intelnexus");
        int forge = xml.indexOf("/projects/forge");
        int news = xml.indexOf("/projects/newproj");
        assertTrue(yunshu < intel && intel < forge && forge < news);
        assertFalse(xml.contains("lastmod")); // 刻意不带：随日期变化会造成误报脏
    }
}
