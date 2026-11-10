package rain.admin.photo;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PhotoRepo extends JpaRepository<Photo, Long> {

    /** 管理台列表：按分组、组内排序号 */
    List<Photo> findAllByOrderByGroupNameAscSortNoAscIdAsc();

    Optional<Photo> findByPid(String pid);

    /** 当前 pid 编号最大值（如 "p31" → 31，空表 → 0），上传时 +1 生成新 pid */
    @Query("select coalesce(max(cast(substring(p.pid, 2) as integer)), 0) from Photo p")
    int maxPidNumber();

    /** 组内排序号最大值，新上传照片排组内最后 */
    @Query("select coalesce(max(p.sortNo), 0) from Photo p where p.groupName = :group")
    int maxSortNoInGroup(String group);

    /** 当前头图（0 或 1 条） */
    List<Photo> findByIsHeroTrue();

    /** 分组内照片数（删除分组前校验，非空分组禁止删除） */
    long countByGroupName(String groupName);
}
