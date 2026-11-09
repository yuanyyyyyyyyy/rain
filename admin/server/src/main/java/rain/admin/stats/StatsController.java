package rain.admin.stats;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import rain.admin.common.RateLimiter;

/**
 * 前台埋点接收（公开，路径 /api/t）+ 管理台统计查询（鉴权，路径 /api/stats/**）。
 * 前台部署在静态托管（无后端）时，上报请求静默失败——后端公网化后自动生效。
 */
@RestController
public class StatsController {

    private final VisitLogRepo repo;
    /** 埋点限流：同 ipHash 每分钟最多 30 次上报（唯一约束只挡重复 path，防伪造 header 轮换 path 刷库） */
    private final RateLimiter trackLimiter = new RateLimiter(30, Duration.ofMinutes(1));
    private final AtomicInteger sinceEvict = new AtomicInteger();

    public StatsController(VisitLogRepo repo) {
        this.repo = repo;
    }

    public record TrackBody(String path, String referrer) {}

    /** 1px 埋点：POST {path, referrer}；异常静默 204（埋点永不影响前台） */
    @PostMapping("/api/t")
    public ResponseEntity<Void> track(@RequestBody TrackBody body,
                                      @RequestHeader(value = "User-Agent", defaultValue = "") String ua,
                                      @RequestHeader(value = "X-Forwarded-For", required = false) String xff) {
        try {
            String ip = xff != null && !xff.isBlank() ? xff.split(",")[0].trim() : "local";
            String ipHash = sha256(ip + "|rain-salt");
            if (!trackLimiter.allow(ipHash)) return ResponseEntity.status(429).build();
            String path = normalize(body.path());
            VisitLog v = new VisitLog();
            v.setIpHash(ipHash);
            v.setPath(path);
            v.setReferrer(trim(body.referrer(), 200));
            v.setUa(trim(ua, 200));
            v.setMinuteBucket(DateTimeFormatter.ofPattern("yyyyMMddHHmm").format(LocalDateTime.now()));
            repo.save(v); // 同 ip+path+分钟重复上报被唯一约束吞掉
            trackLimiter.record(ipHash);
            // 低频回收限流器里的过期 key，防 XFF 轮换导致的内存增长
            if (sinceEvict.incrementAndGet() >= 1000) {
                sinceEvict.set(0);
                trackLimiter.evictStale();
            }
        } catch (RuntimeException ignored) {
            // 去重冲突等一律静默
        }
        return ResponseEntity.noContent().build();
    }

    /** 管理台概览：今日/累计 PV、UV、按天趋势（近 14 天）、热门页面 Top 5 */
    @GetMapping("/api/stats/summary")
    public Map<String, Object> summary() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime since14 = LocalDate.now().minusDays(13).atStartOfDay();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pvToday", repo.countByCreatedAtBetween(todayStart, LocalDateTime.now()));
        m.put("pvTotal", repo.count());
        m.put("uvTotal", repo.countUvSince(LocalDateTime.of(1970, 1, 1, 0, 0)));

        Map<String, Long> daily = new LinkedHashMap<>();
        LocalDate.now().minusDays(13).datesUntil(LocalDate.now().plusDays(1))
                .forEach(d -> daily.put(d.toString(), 0L));
        DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        for (Object[] row : repo.countByDaySince(since14)) {
            String day = String.valueOf(row[0]);
            // MySQL date_format 返回字符串；防御其他驱动返回类型
            if (day.length() > 10) day = day.substring(0, 10);
            if (daily.containsKey(day)) daily.put(day, ((Number) row[1]).longValue());
        }
        m.put("daily14", daily);

        Map<String, Long> top = new LinkedHashMap<>();
        repo.topPaths().stream().limit(5)
                .forEach(r -> top.put(String.valueOf(r[0]), ((Number) r[1]).longValue()));
        m.put("topPaths", top);

        // 来源 Top 5（与 Worker /stats/summary 能力对齐）
        Map<String, Long> refs = new LinkedHashMap<>();
        repo.topReferrers().stream().limit(5)
                .forEach(r -> refs.put(String.valueOf(r[0]), ((Number) r[1]).longValue()));
        m.put("topReferrers", refs);
        return m;
    }

    private static String normalize(String path) {
        if (path == null || path.isBlank()) return "/";
        String p = path.split("\\?")[0];
        return p.length() > 200 ? p.substring(0, 200) : p;
    }

    private static String trim(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
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
