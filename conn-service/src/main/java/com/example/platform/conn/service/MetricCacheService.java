package com.example.platform.conn.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.example.platform.conn.dto.SqlQueryResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 指标相关的 Redis 用法，两类：
 *
 * <ul>
 *   <li><b>结果缓存</b>：{@code metric:result:{id}:{variant}} → 结果 JSON，TTL 可配；</li>
 *   <li><b>热度排行</b>：{@code metric:usage} 这个 ZSET，每次执行 ZINCRBY 累加。</li>
 * </ul>
 *
 * <p>Redis 在这里只是加速层，不是数据源：任何一步失败都降级成"没有缓存 / 没有排行"，
 * 绝不能因为 Redis 抖动让查询本身失败。</p>
 */
@Service
public class MetricCacheService {

    private static final Logger log = LoggerFactory.getLogger(MetricCacheService.class);

    private static final String RESULT_PREFIX = "metric:result:";
    private static final String USAGE_KEY = "metric:usage";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final long ttlSeconds;

    public MetricCacheService(StringRedisTemplate redis,
                              ObjectMapper objectMapper,
                              @Value("${app.query.cache-seconds:300}") long ttlSeconds) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttlSeconds = ttlSeconds;
    }

    public boolean isEnabled() {
        return ttlSeconds > 0;
    }

    /** 读缓存；未命中、未开启或 Redis 异常都返回空。 */
    public Optional<SqlQueryResponse> find(Long metricId, String variant) {
        if (!isEnabled()) {
            return Optional.empty();
        }
        try {
            String json = redis.opsForValue().get(resultKey(metricId, variant));
            if (!StringUtils.hasText(json)) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, SqlQueryResponse.class).asCached());
        } catch (Exception e) {
            log.warn("读取指标缓存失败，降级为直接查库: metricId={}, error={}", metricId, e.getMessage());
            return Optional.empty();
        }
    }

    public void save(Long metricId, String variant, SqlQueryResponse result) {
        if (!isEnabled()) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(result.asCached());
            redis.opsForValue().set(resultKey(metricId, variant), json, Duration.ofSeconds(ttlSeconds));
        } catch (Exception e) {
            log.warn("写入指标缓存失败: metricId={}, error={}", metricId, e.getMessage());
        }
    }

    /** 记录一次指标执行，用于热度排行。 */
    public void recordRun(Long metricId) {
        try {
            redis.opsForZSet().incrementScore(USAGE_KEY, String.valueOf(metricId), 1);
        } catch (Exception e) {
            log.warn("记录指标热度失败: metricId={}, error={}", metricId, e.getMessage());
        }
    }

    /**
     * 热度排行（从高到低）。返回顺序与分数，指标名由调用方补齐，
     * 这样即使某个指标已被删除，也不会因为取不到名字而报错。
     */
    public List<Map.Entry<Long, Long>> topUsage(int limit) {
        try {
            Set<ZSetOperations.TypedTuple<String>> tuples =
                    redis.opsForZSet().reverseRangeWithScores(USAGE_KEY, 0, Math.max(limit, 1) - 1L);
            if (tuples == null || tuples.isEmpty()) {
                return List.of();
            }
            List<Map.Entry<Long, Long>> result = new ArrayList<>();
            for (ZSetOperations.TypedTuple<String> tuple : tuples) {
                if (tuple.getValue() == null || tuple.getScore() == null) {
                    continue;
                }
                try {
                    result.add(Map.entry(Long.valueOf(tuple.getValue()), tuple.getScore().longValue()));
                } catch (NumberFormatException ignored) {
                    // 脏数据跳过
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("读取指标热度排行失败: error={}", e.getMessage());
            return List.of();
        }
    }

    /** 让调用方按 metricId 顺序取名字。 */
    public Map<Long, Long> usageMap(int limit) {
        Map<Long, Long> map = new LinkedHashMap<>();
        topUsage(limit).forEach(entry -> map.put(entry.getKey(), entry.getValue()));
        return map;
    }

    private String resultKey(Long metricId, String variant) {
        return RESULT_PREFIX + metricId + ':' + (variant == null ? "default" : variant);
    }
}
