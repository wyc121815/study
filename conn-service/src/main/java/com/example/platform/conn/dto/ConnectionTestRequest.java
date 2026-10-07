package com.example.platform.conn.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 「测试连接」入参：既支持保存前试连（带密码），也支持对已保存连接试连（密码留空则用库里的）。
 */
public record ConnectionTestRequest(
        Long id,

        @NotBlank(message = "数据库类型不能为空")
        String dbType,

        @NotBlank(message = "主机地址不能为空")
        String host,

        @NotNull(message = "端口不能为空")
        @Min(value = 1, message = "端口必须在 1-65535 之间")
        @Max(value = 65535, message = "端口必须在 1-65535 之间")
        Integer port,

        String databaseName,

        @NotBlank(message = "用户名不能为空")
        String username,

        @Size(max = 128, message = "密码不能超过 128 个字符")
        String password,

        String params) {
}
