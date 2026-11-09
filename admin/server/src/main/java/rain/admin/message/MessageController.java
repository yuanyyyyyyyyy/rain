package rain.admin.message;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 收件箱：前台联系表单提交（公开，同 IP 60s 限频）+ 管理台管理（鉴权）。
 * 公网部署（静态托管无后端）时前台提交失败会自动降级为邮件联系方式，见 ContactSection。
 */
@RestController
public class MessageController {

    private final MessageRepo repo;
    /** 简易限频：ipHash -> 上次提交时间（单实例足够） */
    private final ConcurrentHashMap<String, LocalDateTime> lastSubmit = new ConcurrentHashMap<>();

    public MessageController(MessageRepo repo) {
        this.repo = repo;
    }

    public record SubmitRequest(@NotBlank @Size(max = 80) String name,
                                @NotBlank @Email @Size(max = 200) String email,
                                @NotBlank @Size(max = 4000) String body) {}

    /** 前台提交（公开）：同 IP 60 秒 1 条 */
    @PostMapping("/api/messages")
    public ResponseEntity<?> submit(@Valid @RequestBody SubmitRequest req,
                                    @RequestHeader(value = "X-Forwarded-For", required = false) String xff) {
        String ipHash = sha256(xff != null && !xff.isBlank() ? xff.split(",")[0].trim() : "local");
        LocalDateTime last = lastSubmit.get(ipHash);
        if (last != null && last.isAfter(LocalDateTime.now().minusSeconds(60))) {
            return ResponseEntity.status(429).body(Map.of("error", "发送过于频繁，请稍后再试"));
        }
        Message m = new Message();
        m.setName(req.name().trim());
        m.setEmail(req.email().trim());
        m.setBody(req.body().trim());
        m.setIpHash(ipHash);
        repo.save(m);
        lastSubmit.put(ipHash, LocalDateTime.now());
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /** 收件箱列表（管理台） */
    @GetMapping("/api/messages")
    public List<Message> list() {
        return repo.findAllByOrderByCreatedAtDesc();
    }

    /** 未读数（侧栏徽标） */
    @GetMapping("/api/messages/unread")
    public Map<String, Long> unread() {
        return Map.of("unread", repo.countByReadFalse());
    }

    /** 标记已读 */
    @PutMapping("/api/messages/{id}/read")
    public ResponseEntity<?> markRead(@PathVariable Long id) {
        var opt = repo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "留言不存在"));
        Message m = opt.get();
        m.setRead(true);
        repo.save(m);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /** 全部标记已读 */
    @PostMapping("/api/messages/read-all")
    public Map<String, Boolean> readAll() {
        repo.markAllRead();
        return Map.of("ok", true);
    }

    /** 归档 / 取消归档（body {"archived": false} 取消；缺省归档） */
    @PutMapping("/api/messages/{id}/archive")
    public ResponseEntity<?> setArchived(@PathVariable Long id,
                                         @RequestBody(required = false) Map<String, Object> body) {
        var opt = repo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "留言不存在"));
        Message m = opt.get();
        m.setArchived(body == null || !Boolean.FALSE.equals(body.get("archived")));
        repo.save(m);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
