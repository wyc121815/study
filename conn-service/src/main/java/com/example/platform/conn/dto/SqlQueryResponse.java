package com.example.platform.conn.dto;

import java.util.List;

/**
 * SQL 执行结果。
 *
 * <p>行数据按列顺序排列（{@code rows.get(i).get(j)} 对应 {@code columns.get(j)}），
 * 这样即使两条 SQL 出现重名列也不会丢数据。</p>
 *
 * @param rowCount     实际返回行数
 * @param truncated    是否因为达到行数上限而被截断
 * @param elapsedMillis 服务端耗时（毫秒）
 * @param statementType 语句类型，如 SELECT / SHOW
 */
public record SqlQueryResponse(
        List<SqlResultColumn> columns,
        List<List<Object>> rows,
        int rowCount,
        boolean truncated,
        long elapsedMillis,
        String statementType,
        boolean cached) {

    /** 指标命中缓存时用同一份结果打标记，避免改得到处都是。 */
    public SqlQueryResponse asCached() {
        return new SqlQueryResponse(columns, rows, rowCount, truncated, elapsedMillis, statementType, true);
    }
}
