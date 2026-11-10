package rain.admin.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import rain.admin.common.RateLimiter;
import rain.admin.user.AdminUser;
import rain.admin.user.AdminUserRepo;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AdminUserRepo repo;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;
    /** 仅当部署在可信反向代理之后才置 true（application.yml），届时限流键取 XFF 首段 */
    private final boolean trustProxy;
    /** 登录防爆破：同 IP+用户名 15 分钟内最多 5 次失败 */
    private final RateLimiter loginLimiter = new RateLimiter(5, Duration.ofMinutes(15));

    public AuthController(AdminUserRepo repo, PasswordEncoder encoder, JwtService jwtService,
                          @Value("${app.security.trust-proxy:false}") boolean trustProxy) {
        this.repo = repo;
        this.encoder = encoder;
        this.jwtService = jwtService;
        this.trustProxy = trustProxy;
    }

    /** 登录限流表回收：XFF 可伪造轮换导致 key 增长，5 分钟清一次过期项 */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 300_000)
    public void evictStaleLoginAttempts() {
        loginLimiter.evictStale();
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    /** 用户不存在时也执行一次 matches，抹平「跳过 BCrypt」的可测时序差（防用户名枚举，2026-11-10 审计） */
    private static final String DUMMY_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMye.IjPeGqBOKosEzLkXvBfrGw5v0QmHbO";

    public record PasswordChangeRequest(@NotBlank String oldPassword,
                                        @NotBlank @Size(min = 8, max = 72) String newPassword) {}

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        String key = clientIp(http) + "|" + req.username();
        if (!loginLimiter.allow(key)) {
            return ResponseEntity.status(429).body(Map.of("error", "尝试过于频繁，请 15 分钟后再试"));
        }
        AdminUser user = repo.findByUsername(req.username()).orElse(null);
        boolean matched = user != null && encoder.matches(req.password(), user.getPasswordHash());
        if (!matched) {
            if (user == null) encoder.matches(req.password(), DUMMY_HASH); // 时序拉平
            loginLimiter.record(key);
            return ResponseEntity.status(401).body(Map.of("error", "用户名或密码错误"));
        }
        loginLimiter.reset(key);
        return ResponseEntity.ok(Map.of(
                "token", jwtService.issue(user.getUsername()),
                "username", user.getUsername()));
    }

    /** 修改密码（鉴权）：验证原密码后更新；新密码 8-72 位（BCrypt 上限 72 字节） */
    @PutMapping("/password")
    public ResponseEntity<?> changePassword(@Valid @RequestBody PasswordChangeRequest req,
                                            Authentication auth) {
        AdminUser user = repo.findByUsername(auth.getName()).orElse(null);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "用户不存在"));
        if (!encoder.matches(req.oldPassword(), user.getPasswordHash())) {
            return ResponseEntity.badRequest().body(Map.of("error", "原密码不正确"));
        }
        if (encoder.matches(req.newPassword(), user.getPasswordHash())) {
            return ResponseEntity.badRequest().body(Map.of("error", "新密码不能与原密码相同"));
        }
        user.setPasswordHash(encoder.encode(req.newPassword()));
        repo.save(user);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /**
     * 限流键 IP（2026-11-10 审计修复）：默认取直连地址 remoteAddr。
     * 原实现无条件信任 X-Forwarded-For，攻击者每请求换一个伪造 XFF 即可无限轮换爆破键，登录限流形同虚设。
     * 仅当部署在可信反代之后（app.security.trust-proxy=true）才读 XFF。
     */
    private String clientIp(HttpServletRequest http) {
        if (trustProxy) {
            String xff = http.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        }
        return http.getRemoteAddr();
    }
}
