package com.example.platform.auth.dto;

import com.example.platform.auth.entity.SysUser;
import com.example.platform.common.core.constant.Roles;

/**
 * 对外暴露的用户信息，绝不包含密码。
 */
public record UserInfo(Long id, String username, String nickname, String role) {

    public static UserInfo from(SysUser user) {
        return new UserInfo(user.getId(), user.getUsername(), user.getNickname(),
                Roles.canonical(user.getRole()));
    }
}
