package com.example.platform.conn.service;

import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * 单个数据源的并发查询配额，存在 Redis 里，**多实例共享同一份额度**。
 *
 * <p>只在 JVM 内做信号量的话，配 4 个并发、起 3 个实例，实际就是 12 个并发打到同一个库，
 * 配额形同虚设。这里用 INCR/DECR + 过期时间做成一个带租约的分布式计数器：
 * 拿不到配额就短暂重试，超时后由调用方回 429，而不是无限排队。</p>
 *
 * <p>Redis 不可用时放行并记日志——并发保护是尽力而为，不能因为它挂掉让查询全废；
 * 真正兜底的还有本地有界线程池。</p>
 */
@Component
public class DatasourceConcurrencyLimiter {

    private static final Logger log = LoggerFactory.getLogger(DatasourceConcurrencyLimiter.class);

    private static final String KEY_PREFIX = "datasource:concurrency:";

    private static final RedisScript<Long> ACQUIRE = new DefaultRedisScript<>("""
            local current = redis.call('INCR', KEYS[1])
            redis.call('PEXPIRE', KEYS[1], ARGV[1])
            if current > tonumber(ARGV[2]) then
              redis.call('DECR', KEYS[1])
              return 0
            end
            return 1
            """, Long.class);

    private static final RedisScript<Long> RELEASE = new DefaultRedisScript<>("""
            local current = redis.call('DECR', KEYS[1])
            if current <= 0 then
              redis.call('DEL', KEYS[1])
              return 0
            end
            return current
            """, Long.class);

    private final StringRedisTemplate redis;
    private final int limit;
    private final long waitMillis;
    private final long leaseMillis;

    public DatasourceConcurrencyLimiter(StringRedisTemplate redis,
                                        @Value("${app.query.per-datasource-concurrency:4}") int limit,
                                        @Value("${app.query.datasource-acquire-wait-ms:2000}") long waitMillis,
                                        @Value("${app.query.timeout-seconds:30}") int timeoutSeconds) {
        this.redis = redis;
        this.limit = Math.max(limit, 1);
        this.waitMillis = Math.max(waitMillis, 0);
        // 租约比单条查询的超时更长，避免查询还没跑完额度就被过期回收
        this.leaseMillis = Math.max(timeoutSeconds * 2_000L, 60_000L);
        log.info("数据源并发限流已生效: limit={}, 等待上限={}ms, 租约={}ms（额度存 Redis，多实例共享）",
                this.limit, this.waitMillis, this.leaseMillis);
    }

    /**
     * 取一个该数据源的并发额度，最多等 {@code waitMillis}；超时返回 false。
     */
    public boolean acquire(Long datasourceId) {
        long deadline = System.currentTimeMillis() + waitMillis;
        do {
            if (tryAcquireOnce(datasourceId)) {
                return true;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        } while (System.currentTimeMillis() < deadline);
        return false;
    }

    public void release(Long datasourceId) {
        try {
            redis.execute(RELEASE, List.of(key(datasourceId)));
        } catch (Exception e) {
            log.warn("释放数据源并发额度失败: datasourceId={}, error={}", datasourceId, e.getMessage());
        }
    }

    public int limit() {
        return limit;
    }

    private boolean tryAcquireOnce(Long datasourceId) {
        try {
            Long acquired = redis.execute(ACQUIRE, List.of(key(datasourceId)),
                    String.valueOf(leaseMillis), String.valueOf(limit));
            return acquired != null && acquired == 1L;
        } catch (Exception e) {
            log.warn("获取数据源并发额度失败，本次放行: datasourceId={}, error={}", datasourceId, e.getMessage());
            return true;
        }
    }

    private String key(Long datasourceId) {
        return KEY_PREFIX + datasourceId;
    }

    /** 仅用于日志展示。 */
    public Duration lease() {
        return Duration.ofMillis(leaseMillis);
    }
}
