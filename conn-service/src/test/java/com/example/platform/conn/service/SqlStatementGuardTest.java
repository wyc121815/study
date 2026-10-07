package com.example.platform.conn.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.example.platform.common.core.api.ErrorCode;
import com.example.platform.common.core.exception.BusinessException;

class SqlStatementGuardTest {

    private final SqlStatementGuard guard = new SqlStatementGuard();

    @Test
    void acceptsSimpleSelectAndNormalizesKeyword() {
        assertThat(guard.validate("select id, name from t", false)).isEqualTo("SELECT");
    }

    @Test
    void ignoresTrailingSemicolonAndComments() {
        assertThat(guard.validate("/* 报表查询 */ SELECT 1;  ", false)).isEqualTo("SELECT");
        assertThat(guard.validate("SELECT 1 -- 只看一行\n", false)).isEqualTo("SELECT");
    }

    @Test
    void acceptsReadOnlyStatements() {
        assertThat(guard.validate("SHOW TABLES", false)).isEqualTo("SHOW");
        assertThat(guard.validate("DESCRIBE db_connection", false)).isEqualTo("DESCRIBE");
        assertThat(guard.validate("EXPLAIN SELECT 1", false)).isEqualTo("EXPLAIN");
        assertThat(guard.validate("WITH t AS (SELECT 1 AS a) SELECT * FROM t", false)).isEqualTo("WITH");
    }

    @Test
    void rejectsWriteStatementsInReadOnlyMode() {
        assertThatThrownBy(() -> guard.validate("DELETE FROM t", false))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
        assertThatThrownBy(() -> guard.validate("UPDATE t SET a = 1", false))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsWriteHiddenInCte() {
        assertThatThrownBy(() -> guard.validate(
                "WITH t AS (SELECT 1 AS a) DELETE FROM target WHERE id IN (SELECT a FROM t)", false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("CTE");
    }

    @Test
    void rejectsMultipleStatements() {
        assertThatThrownBy(() -> guard.validate("SELECT 1; SELECT 2", false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("一条");
    }

    @Test
    void rejectsFileOperations() {
        assertThatThrownBy(() -> guard.validate("SELECT * FROM t INTO OUTFILE '/tmp/x'", false))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void keywordInsideLiteralIsNotTreatedAsWrite() {
        assertThat(guard.validate("SELECT * FROM t WHERE status = 'delete'", false)).isEqualTo("SELECT");
    }

    @Test
    void allowsWriteWhenExplicitlyEnabled() {
        assertThat(guard.validate("UPDATE t SET a = 1", true)).isEqualTo("UPDATE");
    }
}
