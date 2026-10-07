package com.example.platform.auth.dto;

/**
 * 登出请求。
 *
 * <p>带上访问令牌是为了让服务端把它的 jti 拉黑，做到"登出即刻失效"，
 * 而不是等访问令牌自己过期。访问令牌已过期时可以只传 refreshToken。</p>
 */
public record LogoutRequest(String refreshToken, String accessToken) {
}
