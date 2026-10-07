package com.example.platform.common.security;

/**
 * 鉴权相关 Redis key 的约定。auth-service 负责写，网关负责读，两边必须一致。
 */
public final class AuthRedisKeys {

    private static final String PREFIX = "conn-platform:auth:";

    private AuthRedisKeys() {
    }

    /** 单次会话吊销标记：登出时写入，值为 "1"，TTL 取令牌剩余有效期。 */
    public static String revokedJti(String jti) {
        return PREFIX + "revoked:jti:" + jti;
    }

    /**
     * 用户级吊销水位线（epoch 秒）。改密、强制下线时写入当前时间，
     * 签发时间早于该值的访问令牌一律失效。
     */
    public static String userRevokedBefore(Long userId) {
        return PREFIX + "revoked-before:" + userId;
    }

    /** 登录限流计数 key：按客户端 IP 统计固定窗口内的尝试次数。 */
    public static String loginAttempts(String clientIp) {
        return PREFIX + "login-attempts:" + clientIp;
    }
}
