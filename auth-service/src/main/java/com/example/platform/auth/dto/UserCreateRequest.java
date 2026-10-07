package com.example.platform.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 管理员新建用户。
 */
public record UserCreateRequest(
        @NotBlank(message = "登录名不能为空")
        @Size(max = 64, message = "登录名不能超过 64 个字符")
        String username,

        @NotBlank(message = "密码不能为空")
        String password,

        @NotBlank(message = "昵称不能为空")
        @Size(max = 64, message = "昵称不能超过 64 个字符")
        String nickname,

        /** ADMIN / USER，为空按 USER 处理。 */
        String role) {
}
