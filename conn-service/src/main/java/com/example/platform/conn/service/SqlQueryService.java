package com.example.platform.conn.service;

import java.sql.Clob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.platform.common.core.api.ErrorCode;
import com.example.platform.common.core.exception.BusinessException;
import com.example.platform.common.core.util.AesCipher;
import com.example.platform.common.security.LoginUser;
import com.example.platform.common.web.context.UserContext;
import com.example.platform.conn.dto.SqlQueryRequest;
import com.example.platform.conn.dto.SqlQueryResponse;
import com.example.platform.conn.dto.SqlResultColumn;
import com.example.platform.conn.entity.DbConnection;
import com.example.platform.conn.entity.QueryHistory;
import com.example.platform.conn.enums.DbType;
import com.example.platform.conn.repository.DbConnectionRepository;

/**
 * 在已保存的连接上执行只读 SQL。
 *
 * <p>与连通性测试一样，JDBC 驱动侧的超时并不总是可靠，因此执行放在独立线程池里，
 * 外层再加一层 {@link Future#get} 超时兜底，避免一条慢 SQL 把 Tomcat 线程占死。</p>
 */
@Service
public class SqlQueryService {

    private static final Logger log = LoggerFactory.getLogger(SqlQueryService.class);

    private final DbConnectionRepository repository;
    private final AesCipher cipher;
    private final SqlStatementGuard guard;
    private final QueryHistoryService historyService;
    private final DatasourceConcurrencyLimiter limiter;
    private final int defaultMaxRows;
    private final int maxRowsLimit;
    private final int timeoutSeconds;
    private final boolean allowWrite;

    /**
     * 查询执行池必须是有界的：{@code newCachedThreadPool} 来多少请求开多少线程，
     * 每条线程再开一个到目标库的连接，数据库连接数瞬间被打满——"并发有限制"说的就是这件事。
     *
     * <p>线程数固定、队列有界、排满即拒绝（回 429）。队列只是缓冲，不产生容量；
     * 无限排队只会把"快速失败"拖成"慢慢超时"。</p>
     */
    private final ThreadPoolExecutor executor;

    public SqlQueryService(DbConnectionRepository repository,
                           AesCipher cipher,
                           SqlStatementGuard guard,
                           QueryHistoryService historyService,
                           DatasourceConcurrencyLimiter limiter,
                           @Value("${app.query.max-rows:1000}") int defaultMaxRows,
                           @Value("${app.query.max-rows-limit:5000}") int maxRowsLimit,
                           @Value("${app.query.timeout-seconds:30}") int timeoutSeconds,
                           @Value("${app.query.allow-write:false}") boolean allowWrite,
                           @Value("${app.query.worker-threads:8}") int workerThreads,
                           @Value("${app.query.queue-capacity:100}") int queueCapacity) {
        this.repository = repository;
        this.cipher = cipher;
        this.guard = guard;
        this.historyService = historyService;
        this.limiter = limiter;
        this.defaultMaxRows = defaultMaxRows;
        this.maxRowsLimit = maxRowsLimit;
        this.timeoutSeconds = timeoutSeconds;
        this.allowWrite = allowWrite;
        int threads = Math.max(workerThreads, 1);
        this.executor = new ThreadPoolExecutor(
                threads, threads,
                60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(Math.max(queueCapacity, 1)),
                new SqlQueryThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy());
        this.executor.allowCoreThreadTimeOut(true);
        log.info("查询执行池已就绪: 线程数={}, 队列容量={}, 单条超时={}s, 只读模式={}",
                threads, Math.max(queueCapacity, 1), timeoutSeconds, !allowWrite);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    public SqlQueryResponse execute(SqlQueryRequest request) {
        return execute(request.connectionId(), request.sql(), request.maxRows(), QueryHistory.SOURCE_ADHOC);
    }

    public SqlQueryResponse execute(Long connectionId, String sql, Integer maxRows, String source) {
        LoginUser user = UserContext.get();
        return execute(connectionId, sql, maxRows, source,
                user == null ? null : user.userId(),
                user == null ? null : user.username());
    }

    /**
     * 执行一条 SQL。
     *
     * @param source 记录到查询历史的来源（查询台 / 指标）
     * @param userId 执行人；消息队列消费时没有请求上下文，需要显式传入
     */
    public SqlQueryResponse execute(Long connectionId, String sql, Integer maxRows, String source,
                                    Long userId, String username) {
        DbConnection entity = repository.findById(connectionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "连接不存在: " + connectionId));

        String statementType = null;
        try {
            if (!entity.isQueryEnabled()) {
                throw new BusinessException(ErrorCode.FORBIDDEN,
                        "连接「" + entity.getName() + "」未开放查询，请在连接管理里打开「允许查询」");
            }

            statementType = guard.validate(sql, allowWrite);
            int limit = resolveMaxRows(maxRows);

            DbType dbType = DbType.of(entity.getDbType());
            String url = dbType.buildUrl(entity.getHost(), entity.getPort(),
                    entity.getDatabaseName(), entity.getParams());
            String password = decryptPassword(entity);

            long start = System.currentTimeMillis();
            SqlQueryResponse result = runWithTimeout(dbType, url, entity.getUsername(), password,
                    sql, limit, statementType, entity, userId, start);

            historyService.record(userId, username, entity.getId(), entity.getName(), sql, source,
                    statementType, result.rowCount(), result.elapsedMillis(), true,
                    result.truncated() ? "结果超过 " + limit + " 行，已截断" : null);
            return result;
        } catch (BusinessException e) {
            historyService.record(userId, username, entity.getId(), entity.getName(), sql, source,
                    statementType, null, null, false, e.getMessage());
            throw e;
        }
    }

    /** 导出 CSV：复用同一条执行链路，因此只读、行数与超时护栏完全一致。 */
    public byte[] exportCsv(Long connectionId, String sql, Integer maxRows, String source) {
        return CsvFormatter.toCsv(execute(connectionId, sql, maxRows, source));
    }

    private SqlQueryResponse runWithTimeout(DbType dbType, String url, String username, String password,
                                            String sql, int maxRows, String statementType,
                                            DbConnection entity, Long userId, long start) {
        Future<SqlQueryResponse> future;
        try {
            future = executor.submit(() -> {
                Long datasourceId = entity.getId();
                if (!limiter.acquire(datasourceId)) {
                    throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,
                            "数据源「" + entity.getName() + "」并发查询已达上限（" + limiter.limit() + "），请稍后重试");
                }
                try {
                    return runQuery(dbType, url, username, password, sql, maxRows, statementType);
                } finally {
                    limiter.release(datasourceId);
                }
            });
        } catch (RejectedExecutionException e) {
            log.warn("查询排队已满，拒绝执行: userId={}, connectionId={}", userId, entity.getId());
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "查询排队已满，请稍后重试");
        }

        try {
            SqlQueryResponse result = future.get(timeoutSeconds + 1L, TimeUnit.SECONDS);
            log.info("SQL 执行成功: userId={}, connectionId={}, type={}, rows={}, elapsed={}ms, sql={}",
                    userId, entity.getId(), statementType, result.rowCount(),
                    System.currentTimeMillis() - start, oneLine(sql));
            return result;
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("SQL 执行超时: userId={}, connectionId={}, timeout={}s, sql={}",
                    userId, entity.getId(), timeoutSeconds, oneLine(sql));
            throw new BusinessException(ErrorCode.SQL_EXECUTION_FAILED,
                    "查询超时（超过 " + timeoutSeconds + " 秒）");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            // 任务内部抛的业务异常（比如限流 429）要保留自己的错误码，
            // 不能被统一压成"SQL 执行失败"的 200。
            if (cause instanceof BusinessException business) {
                log.warn("SQL 执行被业务规则拒绝: userId={}, connectionId={}, message={}",
                        userId, entity.getId(), business.getMessage());
                throw business;
            }
            log.warn("SQL 执行失败: userId={}, connectionId={}, error={}", userId, entity.getId(), cause.getMessage());
            throw new BusinessException(ErrorCode.SQL_EXECUTION_FAILED, describe(cause));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.SQL_EXECUTION_FAILED, "查询被中断");
        }
    }

    private SqlQueryResponse runQuery(DbType dbType, String url, String username, String password,
                                      String sql, int maxRows, String statementType) throws Exception {
        Class.forName(dbType.driverClass());
        Properties properties = new Properties();
        properties.setProperty("user", username);
        properties.setProperty("password", password == null ? "" : password);

        long start = System.currentTimeMillis();
        try (Connection connection = DriverManager.getConnection(url, properties)) {
            // 尽力而为地把连接标记为只读，部分驱动（如 MySQL）只是提示而非强约束
            try {
                connection.setReadOnly(true);
            } catch (SQLException ignored) {
                // 驱动不支持就算了，真正的权限限制在数据库账号上
            }

            try (Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(timeoutSeconds);
                // 多取一行用于判断是否被截断
                statement.setMaxRows(maxRows + 1);

                try (ResultSet rs = statement.executeQuery(sql)) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int columnCount = meta.getColumnCount();

                    List<SqlResultColumn> columns = new ArrayList<>(columnCount);
                    for (int i = 1; i <= columnCount; i++) {
                        columns.add(new SqlResultColumn(
                                meta.getColumnName(i), meta.getColumnLabel(i), meta.getColumnTypeName(i)));
                    }

                    List<List<Object>> rows = new ArrayList<>();
                    boolean truncated = false;
                    while (rs.next()) {
                        if (rows.size() >= maxRows) {
                            truncated = true;
                            break;
                        }
                        List<Object> row = new ArrayList<>(columnCount);
                        for (int i = 1; i <= columnCount; i++) {
                            row.add(normalize(rs.getObject(i)));
                        }
                        rows.add(row);
                    }

                    return new SqlQueryResponse(columns, rows, rows.size(), truncated,
                            System.currentTimeMillis() - start, statementType, false);
                }
            }
        }
    }

    /** 把 JDBC 值转成前端可直接渲染的形式：二进制转十六进制，时间与其它的走 toString。 */
    private Object normalize(Object value) throws SQLException {
        if (value == null) {
            return null;
        }
        if (value instanceof byte[] bytes) {
            return HexFormat.of().withUpperCase().formatHex(bytes);
        }
        if (value instanceof TemporalAccessor || value instanceof java.util.Date) {
            return value.toString();
        }
        if (value instanceof Clob clob) {
            long length = Math.min(clob.length(), 10_000L);
            return clob.getSubString(1, (int) length);
        }
        return value;
    }

    private int resolveMaxRows(Integer requested) {
        int rows = requested == null ? defaultMaxRows : requested;
        return Math.clamp(rows, 1, maxRowsLimit);
    }

    private String decryptPassword(DbConnection entity) {
        try {
            return cipher.decrypt(entity.getPasswordCipher());
        } catch (RuntimeException e) {
            log.error("解密连接密码失败: id={}", entity.getId(), e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "密码解密失败，请重新保存该连接");
        }
    }

    private static String describe(Throwable cause) {
        String message = cause.getMessage();
        String type = cause.getClass().getSimpleName();
        return message == null || message.isBlank() ? type : type + ": " + message;
    }

    private static String oneLine(String sql) {
        String trimmed = sql.trim();
        return trimmed.length() <= 200 ? trimmed : trimmed.substring(0, 200) + "…";
    }

    /** 线程命名带上序号，出问题看 jstack / 线程转储时一眼能认出是谁。 */
    private static final class SqlQueryThreadFactory implements ThreadFactory {

        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "sql-query-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
