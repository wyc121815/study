package com.example.platform.conn.dto;

import jakarta.validation.constraints.Positive;

/**
 * 提交一个异步导出任务：要么给「连接 + SQL」（查询台），要么给「指标 ID」（指标页）。
 */
public record ExportRequest(
        Long connectionId,
        Long metricId,
        String sql,
        @Positive(message = "最大行数必须为正整数") Integer maxRows) {
}
