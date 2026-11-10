package rain.admin.photo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 摄影页分组。groupKey 与 photo.group_name 对应；展示名/描述在发布时写进前台 photos.js。
 * 管理台可新建、改名、删除、排序；删除前必须把组内照片移走（服务层校验）。
 */
@Entity
@Table(name = "photo_group")
public class PhotoGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 分组 key（大写字母/数字/下划线），photo.group_name 引用它 */
    @Column(name = "group_key", nullable = false, unique = true, length = 20)
    private String groupKey;

    /** 前台展示名，如 "STREET / 街道与城市" */
    @Column(nullable = false, length = 80)
    private String name;

    /** 前台分组描述 */
    @Column(length = 255)
    private String description;

    /** 分组顺序（发布时按此顺序输出） */
    @Column(name = "sort_no", nullable = false)
    private Integer sortNo;

    public Long getId() { return id; }
    public String getGroupKey() { return groupKey; }
    public void setGroupKey(String groupKey) { this.groupKey = groupKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
}
