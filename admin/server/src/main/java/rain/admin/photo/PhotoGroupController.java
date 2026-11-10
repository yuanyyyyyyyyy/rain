package rain.admin.photo;

import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理台「02 照片」→ 分组管理端点；/api/** 由 SecurityConfig 统一要求登录。 */
@RestController
@RequestMapping("/api/photo-groups")
public class PhotoGroupController {

    private final PhotoGroupService service;

    public PhotoGroupController(PhotoGroupService service) {
        this.service = service;
    }

    @GetMapping
    public List<PhotoGroup> list() {
        return service.list();
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody PhotoGroupService.GroupInput body) {
        try {
            return ResponseEntity.ok(service.create(body));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody PhotoGroupService.GroupUpdate body) {
        try {
            return ResponseEntity.ok(service.update(id, body));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 整体重排：按提交顺序写 sortNo */
    @PutMapping("/reorder")
    public ResponseEntity<?> reorder(@RequestBody List<Long> ids) {
        try {
            return ResponseEntity.ok(service.reorder(ids));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            service.delete(id);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
