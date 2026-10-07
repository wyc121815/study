package com.example.platform.common.security;

/**
 * 登录用户在服务间传递时的最小信息集。
 */
public record LoginUser(Long userId, String username, String role) {
}
