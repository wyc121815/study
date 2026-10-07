package com.example.platform.conn.service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.example.platform.conn.dto.ConnectionTestResult;
import com.example.platform.conn.enums.DbType;

/**
 * 真正的数据库连通性测试。
 *
 * <p>JDBC 驱动的超时参数并不总是可靠，所以在外面再套一层线程池超时兜底，
 * 保证接口不会被一个卡死的数据库连接拖住。</p>
 */
@Component
public class ConnectionTester {

    private static final Logger log = LoggerFactory.getLogger(ConnectionTester.class);

    private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "conn-test");
        thread.setDaemon(true);
        return thread;
    });

    private final int timeoutSeconds;

    public ConnectionTester(@Value("${app.connection.test-timeout-seconds:10}") int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public ConnectionTestResult test(DbType dbType, String host, int port, String databaseName,
                                     String username, String password, String params) {
        String url = dbType.buildUrl(host, port, databaseName, params);
        long start = System.currentTimeMillis();

        Future<String> future = executor.submit(() -> {
            loadDriver(dbType);
            Properties properties = new Properties();
            properties.setProperty("user", username);
            properties.setProperty("password", password == null ? "" : password);

            try (Connection connection = DriverManager.getConnection(url, properties)) {
                if (!connection.isValid(timeoutSeconds)) {
                    throw new IllegalStateException("连接不可用");
                }
                DatabaseMetaData metaData = connection.getMetaData();
                return metaData.getDatabaseProductName() + " " + metaData.getDatabaseProductVersion();
            }
        });

        try {
            String serverInfo = future.get(timeoutSeconds, TimeUnit.SECONDS);
            long cost = System.currentTimeMillis() - start;
            log.info("数据库连通性测试通过: url={}, cost={}ms", url, cost);
            return ConnectionTestResult.ok(cost, serverInfo);
        } catch (TimeoutException e) {
            future.cancel(true);
            return ConnectionTestResult.fail(System.currentTimeMillis() - start,
                    "连接超时（超过 " + timeoutSeconds + " 秒）");
        } catch (Exception e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            log.warn("数据库连通性测试失败: url={}, error={}", url, cause.getMessage());
            return ConnectionTestResult.fail(System.currentTimeMillis() - start,
                    cause.getClass().getSimpleName() + ": " + cause.getMessage());
        }
    }

    private void loadDriver(DbType dbType) throws ClassNotFoundException {
        Class.forName(dbType.driverClass());
    }
}
