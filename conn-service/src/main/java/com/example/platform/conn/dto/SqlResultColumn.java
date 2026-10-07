package com.example.platform.conn.dto;

/**
 * 结果集列信息。
 *
 * @param name     真实列名，用于程序化处理
 * @param label    展示名（别名为空时等于列名）
 * @param typeName 数据库方言里的类型名，如 VARCHAR / BIGINT
 */
public record SqlResultColumn(String name, String label, String typeName) {
}
