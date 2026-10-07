package com.example.platform.conn.dto;

import java.time.LocalDateTime;

import com.example.platform.common.core.util.AesCipher;
import com.example.platform.conn.entity.DbConnection;

/**
 * 对外返回的连接配置，密码只给脱敏后的展示值。
 */
public record ConnectionResponse(
        Long id,
        String name,
        String dbType,
        String dbTypeLabel,
        String host,
        Integer port,
        String databaseName,
        String username,
        String passwordMasked,
        String params,
        String remark,
        String status,
        LocalDateTime lastTestAt,
        String lastTestMessage,
        Long createdBy,
        String createdByName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean hasPassword) {

    public static ConnectionResponse from(DbConnection entity, String plainPassword, String createdByName) {
        return new ConnectionResponse(
                entity.getId(),
                entity.getName(),
                entity.getDbType(),
                labelOf(entity.getDbType()),
                entity.getHost(),
                entity.getPort(),
                entity.getDatabaseName(),
                entity.getUsername(),
                AesCipher.mask(plainPassword),
                entity.getParams(),
                entity.getRemark(),
                entity.getStatus(),
                entity.getLastTestAt(),
                entity.getLastTestMessage(),
                entity.getCreatedBy(),
                createdByName,
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                plainPassword != null && !plainPassword.isEmpty());
    }

    private static String labelOf(String dbType) {
        try {
            return com.example.platform.conn.enums.DbType.of(dbType).label();
        } catch (RuntimeException e) {
            return dbType;
        }
    }
}
