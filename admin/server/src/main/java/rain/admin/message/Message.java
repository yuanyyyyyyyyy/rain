package rain.admin.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 联系表单留言（前台提交，管理台收件箱管理） */
@Entity
@Table(name = "message")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 200)
    private String email;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    /** 提交来源（前台页面路径，便于判断来自公网还是本地） */
    @Column(length = 200)
    private String sourcePath;

    /** ip 哈希（脱敏，限频用）；仅服务端内部使用，不随管理台列表接口出网（2026-11-10 审计） */
    @Column(name = "ip_hash", nullable = false, length = 64)
    @JsonIgnore
    private String ipHash;

    /** read 是 MySQL 保留字，列名用 is_read */
    @Column(name = "is_read", nullable = false)
    private Boolean read = false;

    /** 归档：从待处理列表移除但仍可检索 */
    @Column(nullable = false)
    private Boolean archived = false;

    /**
     * 回收站（软删）：true = 已删除进入回收站，可恢复或彻底删除；false = 正常。
     * 与 archived 正交：归档是"搁置但可见"，回收站是"已删可恢复"。
     */
    @Column(name = "deleted", nullable = false, columnDefinition = "bit not null default 0")
    private Boolean deleted = false;

    /** 软删时间（回收站排序/展示用） */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getSourcePath() { return sourcePath; }
    public void setSourcePath(String sourcePath) { this.sourcePath = sourcePath; }
    public String getIpHash() { return ipHash; }
    public void setIpHash(String ipHash) { this.ipHash = ipHash; }
    public Boolean getRead() { return read; }
    public void setRead(Boolean read) { this.read = read; }
    public Boolean getArchived() { return archived; }
    public void setArchived(Boolean archived) { this.archived = archived; }
    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
