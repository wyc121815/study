package com.example.platform.conn.dto;

/**
 * 连通性测试结果。测试失败属于正常业务结果，HTTP 仍然返回 200。
 */
public record ConnectionTestResult(boolean success, String message, long costMillis, String serverInfo) {

    public static ConnectionTestResult ok(long costMillis, String serverInfo) {
        return new ConnectionTestResult(true, "连接成功", costMillis, serverInfo);
    }

    public static ConnectionTestResult fail(long costMillis, String message) {
        return new ConnectionTestResult(false, message, costMillis, null);
    }
}
