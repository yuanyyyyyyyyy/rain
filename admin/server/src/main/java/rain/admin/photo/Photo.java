package rain.admin.photo;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 照片元数据。文件实体存前台 public/photos/<pid>.jpg（压缩产物），原始副本留 var/photos-orig/。 */
@Entity
@Table(name = "photo")
public class Photo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 文件名 stem（p01…），全局唯一，与前台 photos.js 的 key 一致 */
    @Column(nullable = false, unique = true, length = 20)
    private String pid;

    /** 上传时的原始文件名（仅留档，不参与前台渲染） */
    @Column(name = "orig_name", length = 255)
    private String origName;

    /** 拍摄日期（可空 = 未知/未整理） */
    private LocalDate shotDate;

    /** 压缩产物实际宽（前台 dims 表用 w/h 表达式） */
    @Column(nullable = false)
    private Integer width;

    @Column(nullable = false)
    private Integer height;

    /** 分组 key：STREET / LIGHT / DAILY（显示名在管理台映射，全称在 M4 写回时映射） */
    @Column(name = "group_name", nullable = false, length = 20)
    private String groupName;

    /** 组内排序号 */
    @Column(name = "sort_no", nullable = false)
    private Integer sortNo;

    /** 头图，全局唯一（服务层事务保证：先全表清零再置一） */
    @Column(name = "is_hero", nullable = false)
    private Boolean isHero = false;

    /** 图注（可空）：前台 Lightbox / 版面 caption 展示用 */
    @Column(length = 255)
    private String caption;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public String getPid() { return pid; }
    public void setPid(String pid) { this.pid = pid; }
    public String getOrigName() { return origName; }
    public void setOrigName(String origName) { this.origName = origName; }
    public LocalDate getShotDate() { return shotDate; }
    public void setShotDate(LocalDate shotDate) { this.shotDate = shotDate; }
    public Integer getWidth() { return width; }
    public void setWidth(Integer width) { this.width = width; }
    public Integer getHeight() { return height; }
    public void setHeight(Integer height) { this.height = height; }
    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }
    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
    public Boolean getIsHero() { return isHero; }
    public void setIsHero(Boolean isHero) { this.isHero = isHero; }
    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
