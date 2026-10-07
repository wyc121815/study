package com.example.platform.conn.dto;

import java.time.LocalDateTime;

import com.example.platform.conn.entity.QueryHistory;

/**
 * 查询历史条目。
 */
public record QueryHistoryItem(
        Long id,
        Long userId,
        String username,
        Long connectionId,
        String connectionName,
        String sql,
        String source,
        String statementType,
        Integer rowCount,
        Long elapsedMillis,
        boolean success,
        String message,
        LocalDateTime createdAt) {

    public static QueryHistoryItem from(QueryHistory entity) {
        return new QueryHistoryItem(
                entity.getId(),
                entity.getUserId(),
                entity.getUsername(),
                entity.getConnectionId(),
                entity.getConnectionName(),
                entity.getSqlText(),
                entity.getSource(),
                entity.getStatementType(),
                entity.getRowCount(),
                entity.getElapsedMs(),
                entity.isSuccess(),
                entity.getMessage(),
                entity.getCreatedAt());
    }
}
