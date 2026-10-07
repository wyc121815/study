package com.example.platform.conn.service;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.example.platform.common.core.api.ErrorCode;
import com.example.platform.common.core.exception.BusinessException;

/**
 * SQL 执行前的安全闸门：只放行单条只读语句。
 *
 * <p>这里做的是"防误伤"而不是完整的 SQL 解析——目标是把常见的写操作、多语句
 * 注入和落盘导出挡在门外，真正的权威限制仍是数据库账号自身的权限。默认只读，
 * 管理员可通过 {@code app.query.allow-write=true} 放开写操作。</p>
 *
 * <p>校验流程：先剥掉注释与字符串/反引号字面量（避免把字面量里的关键字当成
 * 语法），再去掉结尾分号，最后按首关键字和危险关键字判断。</p>
 */
@Component
public class SqlStatementGuard {

    /** 只读模式下允许作为首关键字的语句。 */
    private static final Set<String> READ_ONLY_KEYWORDS =
            Set.of("SELECT", "WITH", "SHOW", "DESC", "DESCRIBE", "EXPLAIN", "TABLE", "VALUES");

    /** CTE（WITH ...）后面可能跟的写操作，必须显式拦掉。 */
    private static final Pattern WRITE_KEYWORDS = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|DROP|ALTER|CREATE|TRUNCATE|REPLACE|MERGE|"
                    + "GRANT|REVOKE|CALL|EXEC|EXECUTE|RENAME|LOAD|HANDLER|"
                    + "COMMIT|ROLLBACK|SAVEPOINT|LOCK|UNLOCK|PREPARE|DEALLOCATE)\\b");

    /** 任何模式下都要拦的落盘 / 读文件操作。 */
    private static final Pattern FILE_OPERATIONS = Pattern.compile(
            "\\b(OUTFILE|DUMPFILE|LOAD_FILE)\\b");

    private static final Pattern FIRST_KEYWORD = Pattern.compile("^[A-Za-z_]+");

    /**
     * 校验并归一化 SQL，返回首关键字（大写）。
     *
     * @param rawSql     用户输入的原始 SQL
     * @param allowWrite 是否允许写操作
     */
    public String validate(String rawSql, boolean allowWrite) {
        if (rawSql == null || rawSql.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "SQL 不能为空");
        }

        String code = stripCommentsAndLiterals(rawSql).trim();
        // 允许结尾的空白与分号，方便用户直接从客户端复制带分号的语句
        code = code.replaceAll("[\\s;]+$", "");
        if (code.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "SQL 不能为空");
        }
        if (code.indexOf(';') >= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "一次只能执行一条 SQL 语句");
        }

        String first = firstKeyword(code);
        if (first == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无法识别的 SQL 语句");
        }

        if (!allowWrite && !READ_ONLY_KEYWORDS.contains(first)) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "当前为只读模式，只允许 SELECT / SHOW / DESCRIBE / EXPLAIN 等只读语句");
        }

        if (FILE_OPERATIONS.matcher(code).find()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不允许执行文件读写相关的 SQL");
        }
        // WITH 开头的 CTE 可以直接接 DELETE/UPDATE，必须再查一遍写关键字
        if (!allowWrite && "WITH".equals(first) && WRITE_KEYWORDS.matcher(code).find()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只读模式下不允许在 CTE 中执行写操作");
        }

        return first;
    }

    /** 取出语句的首关键字，忽略前导括号，如 {@code (SELECT ...) UNION ...}。 */
    private String firstKeyword(String code) {
        int start = 0;
        while (start < code.length() && (code.charAt(start) == '(' || Character.isWhitespace(code.charAt(start)))) {
            start++;
        }
        Matcher matcher = FIRST_KEYWORD.matcher(code.substring(start));
        return matcher.find() ? matcher.group().toUpperCase() : null;
    }

    /**
     * 把注释与字符串/反引号字面量替换为空格，只保留可判断的语法骨架。
     * 不追求完全符合各数据库的方言细节，够挡住误操作即可。
     */
    private String stripCommentsAndLiterals(String sql) {
        StringBuilder out = new StringBuilder(sql.length());
        int i = 0;
        int n = sql.length();
        while (i < n) {
            char current = sql.charAt(i);
            char next = i + 1 < n ? sql.charAt(i + 1) : '\0';

            if (current == '-' && next == '-') {
                i += 2;
                while (i < n && sql.charAt(i) != '\n') {
                    i++;
                }
            } else if (current == '#') {
                i++;
                while (i < n && sql.charAt(i) != '\n') {
                    i++;
                }
            } else if (current == '/' && next == '*') {
                i += 2;
                while (i + 1 < n && !(sql.charAt(i) == '*' && sql.charAt(i + 1) == '/')) {
                    i++;
                }
                i = Math.min(i + 2, n);
            } else if (current == '\'' || current == '"' || current == '`') {
                char quote = current;
                i++;
                while (i < n) {
                    char inner = sql.charAt(i);
                    if (inner == '\\' && quote != '`') {
                        i += 2;
                        continue;
                    }
                    if (inner == quote) {
                        // 连续两个引号是转义后的引号字符
                        if (quote != '`' && i + 1 < n && sql.charAt(i + 1) == quote) {
                            i += 2;
                            continue;
                        }
                        i++;
                        break;
                    }
                    i++;
                }
                out.append(' ');
            } else {
                out.append(current);
                i++;
            }
        }
        return out.toString();
    }
}
