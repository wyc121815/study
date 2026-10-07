package com.example.platform.conn.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.platform.conn.dto.SqlQueryResponse;
import com.example.platform.conn.dto.SqlResultColumn;

class CsvFormatterTest {

    @Test
    void writesHeaderRowsAndBom() {
        SqlQueryResponse result = new SqlQueryResponse(
                List.of(new SqlResultColumn("id", "id", "BIGINT"),
                        new SqlResultColumn("name", "名称", "VARCHAR")),
                List.of(List.of(1, "张三"), List.of(2, "李四")),
                2, false, 12, "SELECT", false);

        String csv = new String(CsvFormatter.toCsv(result), StandardCharsets.UTF_8);

        assertThat(csv).startsWith("\uFEFF");
        assertThat(csv).isEqualTo("\uFEFFid,名称\n1,张三\n2,李四\n");
    }

    @Test
    void escapesCommasQuotesAndNulls() {
        SqlQueryResponse result = new SqlQueryResponse(
                List.of(new SqlResultColumn("a", "a", "VARCHAR"), new SqlResultColumn("b", "b", "VARCHAR")),
                List.of(java.util.Arrays.asList("x,\"y\"", null)),
                1, false, 3, "SELECT", false);

        String csv = new String(CsvFormatter.toCsv(result), StandardCharsets.UTF_8);

        assertThat(csv).contains("\"x,\"\"y\"\"\",\n");
    }
}
