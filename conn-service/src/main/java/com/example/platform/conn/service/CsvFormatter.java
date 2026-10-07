package com.example.platform.conn.service;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.example.platform.conn.dto.SqlQueryResponse;
import com.example.platform.conn.dto.SqlResultColumn;

/**
 * 把结果集导出成 CSV。
 *
 * <p>带 UTF-8 BOM，Excel 直接双击打开不会乱码；字段按 RFC 4180 用双引号包裹并转义。</p>
 */
public final class CsvFormatter {

    /** Excel 需要 BOM 才能正确识别 UTF-8，这里用码点构造，避免源码里出现不可见字符。 */
    private static final String BOM = String.valueOf((char) 0xFEFF);

    private CsvFormatter() {
    }

    public static byte[] toCsv(SqlQueryResponse result) {
        return toCsvText(result).getBytes(StandardCharsets.UTF_8);
    }

    /** 文本形式，便于存库（异步导出任务把结果落库后再下载）。 */
    public static String toCsvText(SqlQueryResponse result) {
        StringBuilder out = new StringBuilder(BOM);

        List<SqlResultColumn> columns = result.columns();
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            SqlResultColumn column = columns.get(i);
            String header = column.label() == null || column.label().isBlank() ? column.name() : column.label();
            out.append(escape(header));
        }
        out.append('\n');

        for (List<Object> row : result.rows()) {
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                Object value = i < row.size() ? row.get(i) : null;
                out.append(escape(value == null ? "" : String.valueOf(value)));
            }
            out.append('\n');
        }
        return out.toString();
    }

    private static String escape(String value) {
        boolean needsQuote = value.indexOf(',') >= 0 || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
        if (!needsQuote) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
