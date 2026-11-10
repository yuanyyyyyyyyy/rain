package rain.admin.content;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 项目详情正文（详情页 headline/summary/meta/sections）。前台数据文件由导出功能人工同步，M4 自动写回接管。 */
@Entity
@Table(name = "project_detail")
public class ProjectDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 前台路由 slug（yunshu / intelnexus / 未来 shimmer） */
    @Column(nullable = false, unique = true, length = 50)
    private String slug;

    @Column(nullable = false, length = 200)
    private String headline;

    @Column(nullable = false, columnDefinition = "text")
    private String summary;

    /** 数据文件头注释原文（证据基线/诚实口径说明），导出时原样带回 */
    @Column(name = "header_note", columnDefinition = "text")
    private String headerNote;

    /** [[label, value], …] 二元数组，JSON 列存取 */
    @Column(name = "meta_json", columnDefinition = "json")
    private String metaJson;

    /** sections 数组（异构类型），JSON 列存取 */
    @Column(name = "sections_json", columnDefinition = "json")
    private String sectionsJson;

    /**
     * 卡片排序号（可空）：Works 列表按 sortNo 升序、null 靠后、再按 id 稳定排序。
     * 种子携带（yunshu=1 … shimmer=5），管理台新建的项目为 null 排最后。
     */
    @Column(name = "sort_no")
    private Integer sortNo;

    /**
     * 隐藏：true = 留库但前台不展示（卡片与详情页均不出现，含直接 URL），由发布生成层过滤。
     * 新建项目默认为 false（展示）。
     */
    @Column(name = "hidden", nullable = false, columnDefinition = "bit not null default 0")
    private Boolean hidden = false;

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getHeadline() { return headline; }
    public void setHeadline(String headline) { this.headline = headline; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getHeaderNote() { return headerNote; }
    public void setHeaderNote(String headerNote) { this.headerNote = headerNote; }
    public String getMetaJson() { return metaJson; }
    public void setMetaJson(String metaJson) { this.metaJson = metaJson; }
    public String getSectionsJson() { return sectionsJson; }
    public void setSectionsJson(String sectionsJson) { this.sectionsJson = sectionsJson; }
    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
    public Boolean getHidden() { return hidden; }
    public void setHidden(Boolean hidden) { this.hidden = hidden; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
