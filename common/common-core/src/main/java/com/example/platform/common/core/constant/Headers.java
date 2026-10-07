package com.example.platform.common.core.constant;

/**
 * 服务间约定的 HTTP 头。
 *
 * <p>网关校验 JWT 后把用户信息写进请求头，下游服务只信任这些头；
 * 因此业务服务不对外暴露端口，只允许网关访问。</p>
 */
public final class Headers {

    /** 链路追踪 ID，网关生成后一路透传。 */
    public static final String TRACE_ID = "X-Trace-Id";

    public static final String USER_ID = "X-User-Id";
    public static final String USERNAME = "X-Username";
    public static final String USER_ROLE = "X-User-Role";

    /** 由网关注入，标记该请求已经过认证，防止外部伪造。 */
    public static final String FROM_GATEWAY = "X-From-Gateway";

    /**
     * 内部调用共享密钥。只有网关（或服务间 Feign）知道，用于证明
     * {@code X-User-Id} 等身份头确实来自可信入口，而不是外部伪造。
     */
    public static final String INTERNAL_TOKEN = "X-Internal-Token";

    private Headers() {
    }
}
