package com.example.platform.conn.dto;

/**
 * 指标热度排行条目：被查询次数来自 Redis 的 ZSET。
 */
public record MetricRankItem(
        Long metricId,
        String name,
        String description,
        String datasourceName,
        long runs) {
}
