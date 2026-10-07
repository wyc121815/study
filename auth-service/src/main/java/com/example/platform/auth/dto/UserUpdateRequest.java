package com.example.platform.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 管理员编辑用户。登录名不可改，密码走单独的重置接口。
 */
public record UserUpdateRequest(
        @NotBlank(message = "昵称不能为空")
        @Size(max = 64, message = "昵称不能超过 64 个字符")
        String nickname,

        /** ADMIN / USER */
        String role,

        /** 1=启用，0=禁用 */
        Integer status) {
}
