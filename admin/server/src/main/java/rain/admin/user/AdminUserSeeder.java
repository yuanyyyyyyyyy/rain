package rain.admin.user;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 首次启动种子：admin_user 表为空时创建默认管理员 admin/admin123。
 * 上线前必须修改默认密码（二期提供改密接口）。
 */
@Configuration
public class AdminUserSeeder {

    @Bean
    CommandLineRunner seedAdminUser(AdminUserRepo repo, PasswordEncoder encoder) {
        return args -> {
            if (repo.count() == 0) {
                AdminUser u = new AdminUser();
                u.setUsername("admin");
                u.setPasswordHash(encoder.encode("admin123"));
                repo.save(u);
            }
        };
    }
}
