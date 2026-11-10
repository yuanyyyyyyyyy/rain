package rain.admin.photo;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PhotoGroupRepo extends JpaRepository<PhotoGroup, Long> {

    List<PhotoGroup> findAllByOrderBySortNoAscIdAsc();

    Optional<PhotoGroup> findByGroupKey(String groupKey);

    boolean existsByGroupKey(String groupKey);
}
