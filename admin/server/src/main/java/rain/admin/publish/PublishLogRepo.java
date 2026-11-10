package rain.admin.publish;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface PublishLogRepo extends JpaRepository<PublishLog, Long> {

    List<PublishLog> findTop20ByOrderByCreatedAtDescIdDesc();

    PublishLog findFirstByStatusOrderByCreatedAtDesc(String status);

    /** 修剪一年前的发布日志（随发布顺手执行，防表无界增长）；UI 只展示最近 20 条 */
    @Modifying
    @Transactional
    @Query("delete from PublishLog p where p.createdAt < :cutoff")
    void deleteByCreatedAtBefore(LocalDateTime cutoff);
}
