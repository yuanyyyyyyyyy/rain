package rain.admin.message;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepo extends JpaRepository<Message, Long> {

    List<Message> findAllByOrderByCreatedAtDesc();

    long countByReadFalse();
}
