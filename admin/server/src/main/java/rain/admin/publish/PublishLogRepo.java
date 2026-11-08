package rain.admin.publish;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublishLogRepo extends JpaRepository<PublishLog, Long> {

    List<PublishLog> findTop20ByOrderByCreatedAtDescIdDesc();

    PublishLog findFirstByStatusOrderByCreatedAtDesc(String status);
}
