package rain.admin.stats;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 页面访问流水（1px 埋点上报）。IP 只存 SHA-256 哈希（脱敏）；同 ip+path+分钟唯一防刷重。 */
@Entity
@Table(name = "visit_log", uniqueConstraints = @UniqueConstraint(
        name = "uk_visit_dedup", columnNames = {"ip_hash", "path", "minute_bucket"}))
public class VisitLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ip_hash", nullable = false, length = 64)
    private String ipHash;

    /** 页面路径（如 /photography），不带 query */
    @Column(nullable = false, length = 200)
    private String path;

    @Column(nullable = false, length = 200)
    private String referrer;

    /** UA 截断存储（只做粗粒度设备归类用） */
    @Column(nullable = false, length = 200)
    private String ua;

    /** yyyyMMddHHmm（分钟桶，配合唯一约束做同分钟去重） */
    @Column(name = "minute_bucket", nullable = false, length = 12)
    private String minuteBucket;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public String getIpHash() { return ipHash; }
    public void setIpHash(String ipHash) { this.ipHash = ipHash; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getReferrer() { return referrer; }
    public void setReferrer(String referrer) { this.referrer = referrer; }
    public String getUa() { return ua; }
    public void setUa(String ua) { this.ua = ua; }
    public String getMinuteBucket() { return minuteBucket; }
    public void setMinuteBucket(String minuteBucket) { this.minuteBucket = minuteBucket; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
