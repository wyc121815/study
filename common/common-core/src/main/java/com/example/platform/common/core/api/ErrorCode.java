package com.example.platform.common.core.api;

/**
 * 业务错误码。
 *
 * <p>约定：0 表示成功；4xxxx 为客户端错误；5xxxx 为服务端错误。
 * 网关与各服务返回的 {@link Result#getCode()} 都来自这里，前端只需识别这一套。</p>
 */
public enum ErrorCode {

    SUCCESS(0, "成功", 200),

    BAD_REQUEST(40000, "请求参数不合法", 400),
    WEAK_PASSWORD(40001, "密码强度不符合要求", 400),
    UNAUTHORIZED(40100, "未登录或登录已过期", 401),
    LOGIN_FAILED(40101, "用户名或密码错误", 401),
    ACCOUNT_DISABLED(40102, "账号已被禁用", 403),
    TOKEN_INVALID(40103, "登录凭证无效", 401),
    REFRESH_TOKEN_INVALID(40104, "登录状态已失效，请重新登录", 401),
    ACCOUNT_LOCKED(40105, "账号已被临时锁定，请稍后再试", 429),
    FORBIDDEN(40300, "没有操作权限", 403),
    NOT_FOUND(40400, "资源不存在", 404),
    METHOD_NOT_ALLOWED(40500, "请求方法不支持", 405),
    CONFLICT(40900, "资源已存在或状态冲突", 409),
    TOO_MANY_REQUESTS(42900, "操作过于频繁，请稍后再试", 429),

    INTERNAL_ERROR(50000, "服务器内部错误", 500),
    SERVICE_UNAVAILABLE(50300, "依赖服务不可用", 503),
    CONNECTION_TEST_FAILED(50201, "数据库连接失败", 200),
    SQL_EXECUTION_FAILED(50202, "SQL 执行失败", 200);

    private final int code;
    private final String message;
    private final int httpStatus;

    ErrorCode(int code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }

    /** 对应的 HTTP 状态码，便于服务端直接构造响应。 */
    public int httpStatus() {
        return httpStatus;
    }
}
