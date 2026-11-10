package rain.admin.photo;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/photos")
public class PhotoController {

    private final PhotoService service;
    private final PhotoRepo repo;

    public PhotoController(PhotoService service, PhotoRepo repo) {
        this.service = service;
        this.repo = repo;
    }

    /** 全量列表（按分组、组内排序号） */
    @GetMapping
    public List<Photo> list() {
        return repo.findAllByOrderByGroupNameAscSortNoAscIdAsc();
    }

    /** 上传：file + groupName（必填）+ shotDate / dateSource（可空，缺省走自动识别链） */
    @PostMapping("/upload")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file,
                                    @RequestParam("groupName") String groupName,
                                    @RequestParam(value = "shotDate", required = false) LocalDate shotDate,
                                    @RequestParam(value = "dateSource", required = false) String dateSource) {
        try {
            PhotoService.UploadResult r = service.upload(file, shotDate, groupName, dateSource);
            Photo p = r.photo();
            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("id", p.getId());
            body.put("pid", p.getPid());
            body.put("width", p.getWidth());
            body.put("height", p.getHeight());
            body.put("shotDate", p.getShotDate() == null ? "" : p.getShotDate().toString());
            body.put("dateSource", r.dateSource());
            return ResponseEntity.ok(body);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            // 2026-11-10 审计：500 分支不外发原始异常（可能带内部路径），细节进服务端日志
            org.slf4j.LoggerFactory.getLogger(PhotoController.class)
                    .error("photo upload failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error",
                    "上传失败：图片解析或落盘异常，请确认文件为有效图片后重试"));
        }
    }

    /** 批量重排（拖拽排序一次请求写回；items 支持跨组：groupName 可选携带） */
    @PutMapping("/reorder")
    public ResponseEntity<?> reorder(@RequestBody List<PhotoService.ReorderItem> items) {
        try {
            return ResponseEntity.ok(Map.of("updated", service.reorder(items)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 更新分组/日期/排序号/图注（管理台表单全字段提交） */
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody PhotoService.UpdateParams params) {
        try {
            return ResponseEntity.ok(service.update(id, params));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 设为头图（全局唯一：旧头图清零 + 本张置一） */
    @PutMapping("/{id}/hero")
    public ResponseEntity<?> setHero(@PathVariable Long id) {
        try {
            service.setHero(id);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 隐藏 / 显示：true = 前台不展示（留库留文件）；头图禁止隐藏 */
    @PutMapping("/{id}/hidden")
    public ResponseEntity<?> setHidden(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        try {
            boolean hidden = body == null || !Boolean.FALSE.equals(body.get("hidden"));
            Photo p = service.setHidden(id, hidden);
            return ResponseEntity.ok(Map.of("id", p.getId(), "hidden", p.getHidden()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 删除：先删记录（事务），后删文件（失败以 warning 提示人工清理，不回滚记录删除） */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            Photo p = service.deleteRecord(id);
            List<String> failedFiles = service.deleteFiles(p);
            if (failedFiles.isEmpty()) return ResponseEntity.ok(Map.of("deleted", p.getPid()));
            return ResponseEntity.ok(Map.of("deleted", p.getPid(),
                    "warning", "记录已删除，但以下文件删除失败，请人工清理：" + String.join(", ", failedFiles)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
