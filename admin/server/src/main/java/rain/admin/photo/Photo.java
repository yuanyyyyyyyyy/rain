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

    // ---- 日期来源（2026-11-10）：四种候选都留库，切换来源时重算 shotDate ----
    /** EXIF DateTimeOriginal 拍摄日期 */
    @Column(name = "exif_date")
    private LocalDate exifDate;

    /** 原始文件修改时间（无 EXIF 时的旧兜底） */
    @Column(name = "file_date")
    private LocalDate fileDate;

    /** 上传当天 */
    @Column(name = "upload_date")
    private LocalDate uploadDate;

    /** 手填日期 */
    @Column(name = "manual_date")
    private LocalDate manualDate;

    /** 当前采用的来源：exif / mtime / upload / manual；legacy = 存量照片未标注来源 */
    @Column(name = "date_source", length = 16)
    private String dateSource;

    /**
     * 隐藏：true = 留库留文件但前台不展示（含直接 URL），由发布生成层过滤。
     * 头图禁止隐藏（管理台 UI 禁用 + 服务端校验），否则生成器会因"没有头图"报错。
     */
    @Column(name = "hidden", nullable = false, columnDefinition = "bit not null default 0")
    private Boolean hidden = false;

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
    public LocalDate getExifDate() { return exifDate; }
    public void setExifDate(LocalDate exifDate) { this.exifDate = exifDate; }
    public LocalDate getFileDate() { return fileDate; }
    public void setFileDate(LocalDate fileDate) { this.fileDate = fileDate; }
    public LocalDate getUploadDate() { return uploadDate; }
    public void setUploadDate(LocalDate uploadDate) { this.uploadDate = uploadDate; }
    public LocalDate getManualDate() { return manualDate; }
    public void setManualDate(LocalDate manualDate) { this.manualDate = manualDate; }
    public String getDateSource() { return dateSource; }
    public void setDateSource(String dateSource) { this.dateSource = dateSource; }
    public Boolean getHidden() { return hidden; }
    public void setHidden(Boolean hidden) { this.hidden = hidden; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
