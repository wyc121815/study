package com.example.platform.conn.dto;

import com.example.platform.conn.entity.DbConnection;
import com.example.platform.conn.enums.DbType;

/**
 * 面向查询场景的数据源视图：只给"选哪条连接"需要的信息，
 * 不含密码脱敏值等管理字段。
 */
public record DataSourceResponse(
        Long id,
        String name,
        String dbType,
        String dbTypeLabel,
        String host,
        Integer port,
        String databaseName,
        String username,
        String remark) {

    public static DataSourceResponse from(DbConnection entity) {
        String label;
        try {
            label = DbType.of(entity.getDbType()).label();
        } catch (RuntimeException e) {
            label = entity.getDbType();
        }
        return new DataSourceResponse(entity.getId(), entity.getName(), entity.getDbType(), label,
                entity.getHost(), entity.getPort(), entity.getDatabaseName(), entity.getUsername(),
                entity.getRemark());
    }
}
