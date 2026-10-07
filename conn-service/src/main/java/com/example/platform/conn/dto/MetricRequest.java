package com.example.platform.conn.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 新建 / 编辑指标的入参。
 */
public record MetricRequest(
        @NotBlank(message = "指标名称不能为空")
        @Size(max = 128, message = "指标名称不能超过 128 个字符")
        String name,

        @Size(max = 512, message = "口径说明不能超过 512 个字符")
        String description,

        @NotNull(message = "请选择数据源")
        Long datasourceId,

        @NotBlank(message = "SQL 不能为空")
        String sql) {
}
