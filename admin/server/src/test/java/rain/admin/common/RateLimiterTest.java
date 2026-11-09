package rain.admin.common;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    @Test
    void allowOnlyBeforeMaxEvents() {
        RateLimiter limiter = new RateLimiter(2, Duration.ofSeconds(1));
        assertTrue(limiter.allow("k"));
        limiter.record("k");
        assertTrue(limiter.allow("k"));
        limiter.record("k");
        assertFalse(limiter.allow("k")); // 已达上限
    }

    @Test
    void windowSlideRestoresAllowance() throws InterruptedException {
        RateLimiter limiter = new RateLimiter(1, Duration.ofMillis(60));
        limiter.record("k");
        assertFalse(limiter.allow("k"));
        Thread.sleep(120); // 窗口滑过
        assertTrue(limiter.allow("k"));
    }

    @Test
    void resetClearsKey() {
        RateLimiter limiter = new RateLimiter(1, Duration.ofSeconds(1));
        limiter.record("k");
        assertFalse(limiter.allow("k"));
        limiter.reset("k");
        assertTrue(limiter.allow("k"));
    }

    @Test
    void keysAreIndependent() {
        RateLimiter limiter = new RateLimiter(1, Duration.ofSeconds(1));
        limiter.record("a");
        assertFalse(limiter.allow("a"));
        assertTrue(limiter.allow("b"));
    }

    @Test
    void evictStaleDropsExpiredBuckets() throws InterruptedException {
        RateLimiter limiter = new RateLimiter(1, Duration.ofMillis(60));
        limiter.record("gone");
        Thread.sleep(120);
        limiter.evictStale();
        assertTrue(limiter.allow("gone")); // 桶已回收，计数从零开始
    }
}
