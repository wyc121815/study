package com.example.platform.conn.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.example.platform.common.core.exception.BusinessException;

class DbTypeTest {

    @Test
    void buildsMysqlUrlWithTimeoutParams() {
        String url = DbType.MYSQL.buildUrl("127.0.0.1", 3306, "demo", null);

        assertThat(url).startsWith("jdbc:mysql://127.0.0.1:3306/demo?");
        assertThat(url).contains("connectTimeout=5000");
    }

    @Test
    void appendsUserSuppliedParams() {
        String url = DbType.MYSQL.buildUrl("db", 3307, "demo", "?useSSL=false&serverTimezone=UTC");

        assertThat(url).contains("useSSL=false");
        assertThat(url).contains("serverTimezone=UTC");
        // 不能出现两个问号
        assertThat(url.indexOf('?')).isEqualTo(url.lastIndexOf('?'));
    }

    @Test
    void buildUrlWithoutDatabaseKeepsTrailingSlash() {
        String url = DbType.POSTGRESQL.buildUrl("pg", 5432, null, " ");

        assertThat(url).startsWith("jdbc:postgresql://pg:5432/?");
    }

    @Test
    void ofIsCaseInsensitive() {
        assertThat(DbType.of("mysql")).isEqualTo(DbType.MYSQL);
        assertThat(DbType.of(" PostgreSQL ")).isEqualTo(DbType.POSTGRESQL);
    }

    @Test
    void unknownTypeIsRejected() {
        assertThatThrownBy(() -> DbType.of("ORACLE"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的数据库类型");
    }
}
