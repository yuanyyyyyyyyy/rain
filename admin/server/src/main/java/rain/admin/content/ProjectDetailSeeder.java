package rain.admin.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * project_detail 表从 classpath content-seed/*.json 导入正文（幂等）。
 * 种子由 node 直接 import 前台数据文件生成（零手工转写）；headerNote 为数据文件头注释原文。
 * 幂等语义（按 slug）：缺失 → 导入；已存在 → 仅回填缺失的 sortNo（老库升级路径，正文绝不覆盖）。
 */
@Configuration
public class ProjectDetailSeeder {

    @Bean
    CommandLineRunner seedProjectDetails(ProjectDetailRepo repo, ObjectMapper mapper) {
        return args -> {
            seed(repo, mapper, "yunshu", """
                    云枢智慧医院管理平台 — 详情页正文数据
                    证据基线：zero-one-his2607/his-java j8 分支（76b48c6ed）+ Setroster-docs/压测报告.md
                    所有数字均有出处；表述遵守诚实口径（MQ 链路止步于通知任务生成）""");
            seed(repo, mapper, "intelnexus", """
                    IntelNexus — 详情页正文数据
                    证据基线：D:/Improve/Project/Python/IntelNexus/（素材回答-IntelNexus-20261108.md 逐条核对）
                    诚实口径：功能代码以 AI 生成为主，本人负责需求定义/方案决策/集成/验证/迭代；无 QPS 类实测数据故不写""");
            seed(repo, mapper, "forge", """
                    Forge（Agent Harness）— 详情页正文数据
                    来源：简历 forge 项目内容（2026-11 用户提供截图转写）；数字出自项目验收记录与 ForgeBench 对照实验
                    诚实口径：P95 为端到端含模型推理时间；调优收益由 72 Trials 冻结变量对照测得""");
            seed(repo, mapper, "rain", """
                    Rain（本站）— 详情页正文数据
                    来源：README 与仓库自身事实，全部为可公开自查信息""");
            seed(repo, mapper, "shimmer", "");
        };
    }

    private static void seed(ProjectDetailRepo repo, ObjectMapper mapper, String slug, String headerNote) {
        JsonNode detail;
        try (InputStream in = ProjectDetailSeeder.class
                .getResourceAsStream("/content-seed/" + slug + ".json")) {
            if (in == null) throw new IllegalStateException("种子缺失: content-seed/" + slug + ".json");
            detail = mapper.readTree(in);
        } catch (Exception e) {
            throw new IllegalStateException("种子读取失败: " + slug, e);
        }
        Integer sortNo = detail.path("sortNo").asInt(0) > 0 ? detail.path("sortNo").asInt(0) : null;

        ProjectDetail existing = repo.findBySlug(slug).orElse(null);
        if (existing != null) {
            // 老库升级：仅当 sortNo 尚未设置时回填（不覆盖既有正文）
            if (existing.getSortNo() == null && sortNo != null) {
                existing.setSortNo(sortNo);
                repo.save(existing);
            }
            return;
        }

        try {
            ProjectDetail d = new ProjectDetail();
            d.setSlug(detail.path("slug").asText(slug));
            d.setHeadline(detail.path("headline").asText());
            d.setSummary(detail.path("summary").asText());
            d.setHeaderNote(headerNote);
            d.setMetaJson(detail.path("meta").toString());
            d.setSectionsJson(detail.path("sections").toString());
            d.setSortNo(sortNo);
            repo.save(d);
        } catch (Exception e) {
            throw new IllegalStateException("种子导入失败: " + slug, e);
        }
    }
}
