package com.example.platform.common.security;

import java.time.Instant;

/**
 * 解析后的访问令牌：除用户信息外，还带上网关做吊销判断所需的元数据。
 *
 * @param user      登录用户
 * @param jti       令牌唯一 ID，用于按"单次会话"吊销
 * @param issuedAt  签发时间，用于和"该用户在此时间之前签发的令牌全部作废"比较
 * @param expiresAt 过期时间，也是黑名单 key 的存活时长
 */
public record ParsedAccessToken(LoginUser user, String jti, Instant issuedAt, Instant expiresAt) {
}
