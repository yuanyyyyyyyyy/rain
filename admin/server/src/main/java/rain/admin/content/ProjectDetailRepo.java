package rain.admin.content;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectDetailRepo extends JpaRepository<ProjectDetail, Long> {

    Optional<ProjectDetail> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
