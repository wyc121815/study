package com.example.platform.auth.dto;

/**
 * 登录/刷新成功的返回。
 *
 * @param token            访问令牌（JWT，短期，用于调接口）
 * @param tokenType        固定 Bearer
 * @param expiresIn        访问令牌有效期（秒）
 * @param refreshToken     刷新令牌（不透明串，长期，仅用于换新令牌）
 * @param refreshExpiresIn 刷新令牌有效期（秒）
 * @param user             当前用户信息
 */
public record LoginResponse(String token,
                            String tokenType,
                            long expiresIn,
                            String refreshToken,
                            long refreshExpiresIn,
                            UserInfo user) {
}
