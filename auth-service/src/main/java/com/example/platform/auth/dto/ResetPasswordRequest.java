package com.example.platform.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 管理员重置某个用户的密码。
 */
public record ResetPasswordRequest(
        @NotBlank(message = "新密码不能为空")
        String newPassword) {
}
