package rain.admin.message;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface MessageRepo extends JpaRepository<Message, Long> {

    List<Message> findAllByOrderByCreatedAtDesc();

    /** 正常列表（未进回收站） */
    List<Message> findAllByDeletedFalseOrderByCreatedAtDesc();

    /** 回收站列表（已软删，按删除时间倒序） */
    List<Message> findAllByDeletedTrueOrderByDeletedAtDesc();

    /** 回收站计数（侧栏徽标） */
    long countByDeletedTrue();

    /** 未读计数只考虑未进回收站的留言 */
    long countByReadFalseAndDeletedFalse();

    @Modifying
    @Transactional
    @Query("update Message m set m.deleted = true, m.deletedAt = CURRENT_TIMESTAMP where m.deleted = false")
    void moveAllToTrash();

    @Modifying
    @Transactional
    @Query("delete from Message m where m.deleted = true")
    void purgeTrash();

    @Modifying
    @Transactional
    @Query("update Message m set m.read = true where m.read = false")
    void markAllRead();
}
