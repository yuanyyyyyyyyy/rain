package rain.admin.publish;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import rain.admin.content.ContentService;
import rain.admin.photo.PhotoRepo;

@RestController
@RequestMapping("/api/publish")
public class PublishController {

    private final PublishService service;
    private final PublishLogRepo logRepo;
    private final PhotoRepo photoRepo;
    private final ContentService contentService;

    public PublishController(PublishService service, PublishLogRepo logRepo,
                             PhotoRepo photoRepo, ContentService contentService) {
        this.service = service;
        this.logRepo = logRepo;
        this.photoRepo = photoRepo;
        this.contentService = contentService;
    }

    /** 执行发布（同步等待构建完成） */
    @PostMapping
    public ResponseEntity<?> publish() {
        String user = SecurityContextHolder.getContext().getAuthentication().getName();
        try {
            PublishService.PublishResult r = service.publish(user);
            return ResponseEntity.ok(r);
        } catch (IllegalStateException e) {
            if (service.isRunning()) return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 发布历史（最近 20 条） */
    @GetMapping("/logs")
    public List<PublishLog> logs() {
        return logRepo.findTop20ByOrderByCreatedAtDescIdDesc();
    }

    /** 状态摘要：照片数 / 项目数 / 最近一次发布 / 是否进行中 */
    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("photos", photoRepo.count());
        m.put("projects", contentService.list().size());
        m.put("running", service.isRunning());
        PublishLog last = logRepo.findTop20ByOrderByCreatedAtDescIdDesc().stream().findFirst().orElse(null);
        if (last != null) {
            m.put("lastPublish", Map.of(
                    "status", last.getStatus(),
                    "triggeredBy", last.getTriggeredBy(),
                    "durationMs", last.getDurationMs(),
                    "createdAt", last.getCreatedAt().toString()));
        }
        return m;
    }

    /** 未发布变更检测（任务 C）：侧栏/照片页/内容页角标数据源 */
    @GetMapping("/dirty")
    public Map<String, Object> dirty() {
        return service.dirty();
    }

    /** 公网部署进度（GitHub Actions 最新 run，后端代理查询） */
    @GetMapping("/deploy-status")
    public Map<String, Object> deployStatus() {
        return service.deployStatus();
    }
}
