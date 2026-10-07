package com.example.platform.conn.dto;

import java.time.LocalDateTime;

/**
 * 指标详情。
 */
public record MetricResponse(
        Long id,
        String name,
        String description,
        Long datasourceId,
        String datasourceName,
        String datasourceLabel,
        String sql,
        String status,
        Long createdBy,
        String createdByName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
