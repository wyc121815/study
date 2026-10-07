package com.example.platform.conn.dto;

/**
 * auth-service 返回的用户信息（与对方的 UserInfo 结构保持一致）。
 */
public record UserInfoDto(Long id, String username, String nickname, String role) {
}
