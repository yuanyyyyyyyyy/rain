package rain.admin.message;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface MessageRepo extends JpaRepository<Message, Long> {

    List<Message> findAllByOrderByCreatedAtDesc();

    long countByReadFalse();

    @Modifying
    @Transactional
    @Query("update Message m set m.read = true where m.read = false")
    void markAllRead();
}
