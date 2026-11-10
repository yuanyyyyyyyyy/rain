package rain.admin.content;

import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/content")
public class ContentController {

    private final ContentService service;

    public ContentController(ContentService service) {
        this.service = service;
    }

    /** 项目列表（管理台切换用） */
    @GetMapping("/projects")
    public List<Map<String, Object>> list() {
        return service.list().stream()
                .map(d -> Map.<String, Object>of(
                        "slug", d.getSlug(),
                        "headline", d.getHeadline(),
                        "hidden", d.getHidden(),
                        "updatedAt", d.getUpdatedAt().toString()))
                .toList();
    }

    @GetMapping("/projects/{slug}")
    public ResponseEntity<?> detail(@PathVariable String slug) {
        try {
            return ResponseEntity.ok(service.toResponse(service.bySlug(slug)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 全量更新（先校验后落库） */
    @PutMapping("/projects/{slug}")
    public ResponseEntity<?> update(@PathVariable String slug, @RequestBody ContentService.UpdateRequest req) {
        try {
            return ResponseEntity.ok(service.toResponse(service.update(slug, req)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 新建项目（最小骨架，空 sections；发布时自动写入 site.js 卡片与前台数据文件） */
    @PostMapping("/projects")
    public ResponseEntity<?> create(@RequestBody ContentService.CreateRequest req) {
        try {
            return ResponseEntity.ok(service.toResponse(service.create(req)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 删除项目（详情数据删除；下次发布后前台卡片同步移除） */
    @DeleteMapping("/projects/{slug}")
    public ResponseEntity<?> delete(@PathVariable String slug) {
        try {
            service.delete(slug);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 隐藏 / 显示：true = 前台不展示（留库）；发布后卡片与详情页同步移除 */
    @PutMapping("/projects/{slug}/hidden")
    public ResponseEntity<?> setHidden(@PathVariable String slug,
                                      @RequestBody(required = false) Map<String, Object> body) {
        try {
            boolean hidden = body == null || !Boolean.FALSE.equals(body.get("hidden"));
            ProjectDetail d = service.setHidden(slug, hidden);
            return ResponseEntity.ok(Map.of("slug", d.getSlug(), "hidden", d.getHidden()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 导出 <slug>.js 全文（text/plain，管理台显示 + 复制，人工替换数据文件后 build） */
    @GetMapping(value = "/projects/{slug}/export", produces = "text/plain;charset=UTF-8")
    public ResponseEntity<?> export(@PathVariable String slug) {
        try {
            return ResponseEntity.ok(service.export(service.bySlug(slug)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** shots 表单截图选择器：public/screenshots/<slug>/ 文件名列表 */
    @GetMapping("/screenshots/{slug}")
    public List<String> screenshots(@PathVariable String slug) {
        return service.listScreenshots(slug);
    }
}
