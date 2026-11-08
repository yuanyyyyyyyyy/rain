package rain.admin.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminUserRepo extends JpaRepository<AdminUser, Long> {

    Optional<AdminUser> findByUsername(String username);
}
