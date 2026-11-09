package rain.admin.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * project_detail 表空时从 classpath content-seed/*.json 导入现有两项目正文（幂等）。
 * 种子由 node 直接 import 前台数据文件生成（零手工转写）；headerNote 为数据文件头注释原文。
 */
@Configuration
public class ProjectDetailSeeder {

    @Bean
    CommandLineRunner seedProjectDetails(ProjectDetailRepo repo, ObjectMapper mapper) {
        return args -> {
            // 逐 slug 幂等：已存在的跳过（新增种子可自动补种到已有库）
            if (!repo.existsBySlug("yunshu"))
                seed(repo, mapper, "yunshu", """
                        云枢智慧医院管理平台 — 详情页正文数据
                        证据基线：zero-one-his2607/his-java j8 分支（76b48c6ed）+ Setroster-docs/压测报告.md
                        所有数字均有出处；表述遵守诚实口径（MQ 链路止步于通知任务生成）""");
            if (!repo.existsBySlug("intelnexus"))
                seed(repo, mapper, "intelnexus", """
                        IntelNexus — 详情页正文数据
                        证据基线：D:/Improve/Project/Python/IntelNexus/（素材回答-IntelNexus-20261108.md 逐条核对）
                        诚实口径：功能代码以 AI 生成为主，本人负责需求定义/方案决策/集成/验证/迭代；无 QPS 类实测数据故不写""");
            if (!repo.existsBySlug("shimmer"))
                seed(repo, mapper, "shimmer", "");
        };
    }

    private static void seed(ProjectDetailRepo repo, ObjectMapper mapper, String slug, String headerNote) {
        try (InputStream in = ProjectDetailSeeder.class
                .getResourceAsStream("/content-seed/" + slug + ".json")) {
            if (in == null) throw new IllegalStateException("种子缺失: content-seed/" + slug + ".json");
            JsonNode detail = mapper.readTree(in);
            ProjectDetail d = new ProjectDetail();
            d.setSlug(detail.path("slug").asText(slug));
            d.setHeadline(detail.path("headline").asText());
            d.setSummary(detail.path("summary").asText());
            d.setHeaderNote(headerNote);
            d.setMetaJson(detail.path("meta").toString());
            d.setSectionsJson(detail.path("sections").toString());
            repo.save(d);
        } catch (Exception e) {
            throw new IllegalStateException("种子导入失败: " + slug, e);
        }
    }
}
