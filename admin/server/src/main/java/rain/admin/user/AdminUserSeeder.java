package rain.admin.user;

import java.security.SecureRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 首次启动种子：admin_user 表为空时创建管理员 admin。
 * 初始口令取 app.admin.initial-password；未配置则随机生成并打印到控制台（仅此一次）。
 * 不再使用固定弱口令。已有账号请用管理台「修改密码」（PUT /api/auth/password）。
 */
@Configuration
public class AdminUserSeeder {

    private static final Logger log = LoggerFactory.getLogger(AdminUserSeeder.class);

    /** 去掉易混淆字符（I l O 0 1）的口令字符集 */
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";

    @Bean
    CommandLineRunner seedAdminUser(AdminUserRepo repo, PasswordEncoder encoder,
                                    @Value("${app.admin.initial-password:}") String initialPassword) {
        return args -> {
            if (repo.count() != 0) return;
            AdminUser u = new AdminUser();
            u.setUsername("admin");
            String pwd = initialPassword;
            if (pwd == null || pwd.isBlank()) {
                pwd = randomPassword();
                log.warn("初始管理员密码（仅打印一次，请立即保存；登录后请在管理台修改）: {}", pwd);
            }
            u.setPasswordHash(encoder.encode(pwd));
            repo.save(u);
        };
    }

    /** SecureRandom 生成 16 位随机口令 */
    static String randomPassword() {
        SecureRandom r = new SecureRandom();
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 16; i++) sb.append(CHARS.charAt(r.nextInt(CHARS.length())));
        return sb.toString();
    }
}
