package rain.admin.photo;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /** 设头图第一步：全表清零（与置一在同一事务内，保证全局唯一） */
    @Modifying
    @Query("update Photo p set p.isHero = false")
    void clearHero();
}
