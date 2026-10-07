package com.example.platform.conn.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * SQL 执行请求。
 *
 * @param connectionId 目标连接（已保存的 db_connection.id）
 * @param sql          待执行的 SQL，默认只允许单条只读语句
 * @param maxRows      期望的最大返回行数，为空时用服务端默认值，超出上限会被压到上限
 */
public record SqlQueryRequest(
        @NotNull(message = "请选择数据库连接") Long connectionId,
        @NotBlank(message = "SQL 不能为空") String sql,
        @Positive(message = "最大行数必须为正整数") Integer maxRows) {
}
