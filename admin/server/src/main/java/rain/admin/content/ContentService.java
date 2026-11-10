package rain.admin.content;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 项目详情内容管理。核心纪律：表单校验与前台渲染器字段一一对应（设计方案 4.3），
 * 防止"存进渲染不出的数据"；kicker 编号按数组位置自动重排（01 概述由页面渲染，数据从 02 起）。
 */
@Service
public class ContentService {

    private static final Set<String> SECTION_TYPES = Set.of("arch", "shots", "impl", "metrics", "pits", "text");
    /** 新增节时允许选择的类型（arch 是 SVG 组件，不开放编辑/新增） */
    private static final Set<String> EDITABLE_TYPES = Set.of("shots", "impl", "metrics", "pits", "text");

    private final ProjectDetailRepo repo;
    private final ObjectMapper mapper;
    private final Path frontendPublicDir;

    public ContentService(ProjectDetailRepo repo, ObjectMapper mapper,
                          @Value("${app.storage.frontend-public-dir}") String frontendPublicDir) {
        this.repo = repo;
        this.mapper = mapper;
        this.frontendPublicDir = Path.of(frontendPublicDir);
    }

    public record UpdateRequest(String headline, String summary, String headerNote, JsonNode meta, JsonNode sections) {}

    public record CreateRequest(String slug, String headline, String summary) {}

    /** 新建项目（最小骨架：空 sections，概述由页面渲染）；发布时写入 site.js 卡片与前台数据文件 */
    @Transactional
    public ProjectDetail create(CreateRequest req) {
        String slug = req.slug() == null ? "" : req.slug().trim().toLowerCase();
        if (!slug.matches("[a-z0-9][a-z0-9-]{1,40}")) {
            throw bad("slug 只能是小写字母/数字/连字符（2-40 位）");
        }
        if (repo.existsBySlug(slug)) throw bad("slug 已存在: " + slug);
        if (req.headline() == null || req.headline().isBlank()) throw bad("headline 不能为空");
        if (req.summary() == null || req.summary().isBlank()) throw bad("summary 不能为空");
        ProjectDetail d = new ProjectDetail();
        d.setSlug(slug);
        d.setHeadline(req.headline().trim());
        d.setSummary(req.summary());
        d.setHeaderNote("");
        d.setMetaJson("[]");
        d.setSectionsJson("[]");
        return repo.save(d);
    }

    /** 删除项目（详情数据一并删除；发布后前台卡片与数据文件同步移除） */
    @Transactional
    public void delete(String slug) {
        repo.delete(bySlug(slug));
    }

    public List<ProjectDetail> list() {
        return repo.findAll();
    }

    public ProjectDetail bySlug(String slug) {
        return repo.findBySlug(slug).orElseThrow(() -> new IllegalArgumentException("项目不存在: " + slug));
    }

    /** 详情转响应树：meta/sections 解析为 JSON 结构返回给管理台 */
    public ObjectNode toResponse(ProjectDetail d) {
        ObjectNode n = mapper.createObjectNode();
        n.put("slug", d.getSlug());
        n.put("headline", d.getHeadline());
        n.put("summary", d.getSummary());
        n.put("headerNote", d.getHeaderNote() == null ? "" : d.getHeaderNote());
        n.put("updatedAt", d.getUpdatedAt().toString());
        try {
            n.set("meta", mapper.readTree(d.getMetaJson()));
            n.set("sections", mapper.readTree(d.getSectionsJson()));
        } catch (IOException e) {
            throw new IllegalStateException("JSON 字段解析失败", e);
        }
        return n;
    }

    /** 全量更新：先校验并规范化（kicker 重排），通过后才落库 */
    @Transactional
    public ProjectDetail update(String slug, UpdateRequest req) {
        ProjectDetail d = bySlug(slug);
        if (req.headline() == null || req.headline().isBlank()) throw bad("headline 不能为空");
        if (req.summary() == null || req.summary().isBlank()) throw bad("summary 不能为空");
        JsonNode meta = req.meta();
        if (meta == null || !meta.isArray()) throw bad("meta 必须是数组");
        for (int i = 0; i < meta.size(); i++) {
            JsonNode row = meta.get(i);
            if (!row.isArray() || row.size() != 2 || !row.get(0).isTextual() || !row.get(1).isTextual()) {
                throw bad("meta 第 " + (i + 1) + " 行必须是 [label, value] 二元字符串数组");
            }
        }
        JsonNode sections = req.sections();
        if (sections == null || !sections.isArray()) throw bad("sections 必须是数组");
        validateAndRenumber(sections);

        d.setHeadline(req.headline().trim());
        d.setSummary(req.summary());
        d.setHeaderNote(req.headerNote() == null ? "" : req.headerNote());
        d.setMetaJson(meta.toString());
        d.setSectionsJson(sections.toString());
        d.setUpdatedAt(java.time.LocalDateTime.now());
        return repo.save(d);
    }

    /** 导出 <slug>.js 全文：头注释原样带回，数据部分 JSON 序列化（合法 JS，中文无转义问题） */
    /** 详情正文 → 纯数据 JSON（内容层 content/projects/&lt;slug&gt;.json），不再包 JS 模块外壳 */
    public String export(ProjectDetail d) {
        try {
            ObjectNode detail = mapper.createObjectNode();
            // 管理台里的备注（原生成物头注释）保留为数据字段：JSON 不支持注释，前台不读此键
            if (d.getHeaderNote() != null && !d.getHeaderNote().isBlank()) {
                detail.put("_headerNote", d.getHeaderNote());
            }
            detail.put("slug", d.getSlug());
            detail.put("headline", d.getHeadline());
            detail.put("summary", d.getSummary());
            detail.set("meta", mapper.readTree(d.getMetaJson()));
            detail.set("sections", mapper.readTree(d.getSectionsJson()));
            // 数组同样换行缩进，贴近手写数据文件的可读风格
            DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
            printer.indentArraysWith(new DefaultIndenter("  ", "\n"));
            return mapper.writer(printer).writeValueAsString(detail) + "\n";
        } catch (IOException e) {
            throw new IllegalStateException("导出序列化失败", e);
        }
    }

    /** shots 表单的截图选择器：列 public/screenshots/<slug>/ 下的文件名（目录不存在 = 空数组） */
    public List<String> listScreenshots(String slug) {
        Path dir = frontendPublicDir.resolve("screenshots").resolve(slug);
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.map(p -> p.getFileName().toString()).filter(n -> !n.startsWith("."))
                    .sorted().collect(Collectors.toList());
        } catch (IOException e) {
            return List.of();
        }
    }

    /** 逐节校验 + kicker 编号重排（就地修改数组） */
    private void validateAndRenumber(JsonNode sections) {
        for (int i = 0; i < sections.size(); i++) {
            JsonNode s = sections.get(i);
            if (!s.isObject()) throw bad("sections 第 " + (i + 1) + " 项必须是对象");
            String type = s.path("type").asText("");
            String where = "第 " + (i + 1) + " 节[" + (type.isEmpty() ? "类型缺失" : type) + "]";
            if (!SECTION_TYPES.contains(type)) throw bad(where + "：未知类型（允许 arch/shots/impl/metrics/pits/text）");

            switch (type) {
                case "arch" -> requireText(s, "component", where); // SVG 组件引用，只读保留
                case "shots" -> {
                    if (s.path("pending").asBoolean(false)) {
                        requireStringArrayIfPresent(s, "wanted", where);
                    } else {
                        JsonNode items = requireArray(s, "items", where);
                        for (int j = 0; j < items.size(); j++) {
                            JsonNode it = items.get(j);
                            String w = where + " 截图第 " + (j + 1) + " 张";
                            requireText(it, "src", w);
                            requireText(it, "caption", w);
                        }
                    }
                }
                case "impl" -> {
                    requireStringArrayIfPresent(s, "labels", where);
                    JsonNode items = requireArray(s, "items", where);
                    for (int j = 0; j < items.size(); j++) {
                        JsonNode it = items.get(j);
                        String w = where + " 第 " + (j + 1) + " 条";
                        requireText(it, "title", w);
                        requireText(it, "problem", w);
                        requireText(it, "solution", w);
                        requireText(it, "result", w);
                    }
                }
                case "metrics" -> {
                    JsonNode items = requireArray(s, "items", where);
                    for (int j = 0; j < items.size(); j++) {
                        JsonNode it = items.get(j);
                        String w = where + " 第 " + (j + 1) + " 个指标";
                        requireText(it, "label", w);
                        requireText(it, "value", w);
                    }
                }
                case "pits" -> {
                    JsonNode items = requireArray(s, "items", where);
                    for (int j = 0; j < items.size(); j++) {
                        JsonNode it = items.get(j);
                        String w = where + " 第 " + (j + 1) + " 条";
                        requireText(it, "title", w);
                        requireText(it, "body", w);
                    }
                }
                case "text" -> requireText(s, "body", where);
                default -> throw bad(where + "：类型不支持");
            }

            String kicker = s.path("kicker").asText("");
            int sep = kicker.indexOf(" / ");
            if (sep <= 0) throw bad(where + "：kicker 必须为 'NN / 标题' 格式");
            String title = kicker.substring(sep + 3).trim();
            if (title.isEmpty()) throw bad(where + "：kicker 标题不能为空");
            ((ObjectNode) s).put("kicker", String.format("%02d / %s", i + 2, title));
        }
    }

    private static IllegalArgumentException bad(String msg) {
        return new IllegalArgumentException(msg);
    }

    private static void requireText(JsonNode node, String field, String where) {
        if (!node.hasNonNull(field) || node.path(field).asText().isBlank()) {
            throw bad(where + "：字段 " + field + " 不能为空");
        }
    }

    private static JsonNode requireArray(JsonNode node, String field, String where) {
        JsonNode arr = node.path(field);
        if (!arr.isArray() || arr.isEmpty()) throw bad(where + "：字段 " + field + " 必须是非空数组");
        return arr;
    }

    private static void requireStringArrayIfPresent(JsonNode node, String field, String where) {
        JsonNode arr = node.path(field);
        if (arr.isMissingNode() || arr.isNull()) return;
        if (!arr.isArray()) throw bad(where + "：字段 " + field + " 必须是字符串数组");
        for (JsonNode e : arr) {
            if (!e.isTextual()) throw bad(where + "：字段 " + field + " 必须是字符串数组");
        }
    }

    /** 新增节允许的类型（管理台下拉用，arch 不开放） */
    public List<String> editableTypes() {
        return new ArrayList<>(EDITABLE_TYPES);
    }
}
