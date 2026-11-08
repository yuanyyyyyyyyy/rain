package rain.admin.publish;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 发布历史：写回数据文件 + 触发前台构建的一次执行记录 */
@Entity
@Table(name = "publish_log")
public class PublishLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 触发人（JWT subject） */
    @Column(name = "triggered_by", nullable = false, length = 50)
    private String triggeredBy;

    /** SUCCESS / FAILED */
    @Column(nullable = false, length = 20)
    private String status;

    /** 失败原因或构建日志尾部 */
    @Column(columnDefinition = "text")
    private String output;

    @Column(name = "duration_ms", nullable = false)
    private Long durationMs;

    /** 本次写回的文件（相对路径，逗号分隔） */
    @Column(name = "written_files", columnDefinition = "text")
    private String writtenFiles;

    /** 发布时仓库 HEAD 短哈希（数据版本追溯） */
    @Column(name = "commit_hash", length = 12)
    private String commitHash;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public String getTriggeredBy() { return triggeredBy; }
    public void setTriggeredBy(String triggeredBy) { this.triggeredBy = triggeredBy; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getOutput() { return output; }
    public void setOutput(String output) { this.output = output; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    public String getWrittenFiles() { return writtenFiles; }
    public void setWrittenFiles(String writtenFiles) { this.writtenFiles = writtenFiles; }
    public String getCommitHash() { return commitHash; }
    public void setCommitHash(String commitHash) { this.commitHash = commitHash; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
