package com.example.platform.auth.dto;

import java.time.LocalDateTime;

import com.example.platform.auth.entity.SysUser;
import com.example.platform.common.core.constant.Roles;

/**
 * 用户管理列表用的视图，比 {@link UserInfo} 多带状态与时间，仍然不含密码。
 */
public record UserSummary(
        Long id,
        String username,
        String nickname,
        String role,
        Integer status,
        LocalDateTime lockedUntil,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static UserSummary from(SysUser user) {
        return new UserSummary(user.getId(), user.getUsername(), user.getNickname(),
                Roles.canonical(user.getRole()),
                user.getStatus(), user.getLockedUntil(), user.getCreatedAt(), user.getUpdatedAt());
    }
}
