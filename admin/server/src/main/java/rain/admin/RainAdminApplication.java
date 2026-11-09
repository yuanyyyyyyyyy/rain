package rain.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // 限流表/临时态的低频回收（RateLimiter.evictStale、lastSubmit 清理）
public class RainAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(RainAdminApplication.class, args);
    }
}
