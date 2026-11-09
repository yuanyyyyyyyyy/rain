package rain.admin.common;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内滑动窗口限流器（单实例部署足够）：key -> 窗口内事件时间戳队列。
 * 两种用法：
 *  - 先 allow(key) 判断、通过后 record(key)（登录失败计数、埋点限流）
 *  - reset(key) 在成功后清零；evictStale() 由调用方低频触发，回收过期 key 防内存增长
 */
public class RateLimiter {

    private final int maxEvents;
    private final Duration window;
    private final ConcurrentHashMap<String, Deque<Instant>> buckets = new ConcurrentHashMap<>();

    public RateLimiter(int maxEvents, Duration window) {
        this.maxEvents = maxEvents;
        this.window = window;
    }

    /** 该 key 窗口内事件数是否未达上限（只读，不记入） */
    public boolean allow(String key) {
        Deque<Instant> q = bucket(key);
        synchronized (q) {
            evictExpired(q);
            return q.size() < maxEvents;
        }
    }

    /** 记入一次事件（失败/请求等，语义由调用方定义） */
    public void record(String key) {
        Deque<Instant> q = bucket(key);
        synchronized (q) {
            q.addLast(Instant.now());
        }
    }

    /** 成功后清零该 key */
    public void reset(String key) {
        buckets.remove(key);
    }

    /** 回收窗口外事件已清空的 key（全表扫描，调用方低频触发） */
    public void evictStale() {
        buckets.entrySet().removeIf(e -> {
            Deque<Instant> q = e.getValue();
            synchronized (q) {
                evictExpired(q);
                return q.isEmpty();
            }
        });
    }

    private Deque<Instant> bucket(String key) {
        return buckets.computeIfAbsent(key, k -> new ArrayDeque<>());
    }

    private void evictExpired(Deque<Instant> q) {
        Instant cutoff = Instant.now().minus(window);
        while (!q.isEmpty() && q.peekFirst().isBefore(cutoff)) q.pollFirst();
    }
}
