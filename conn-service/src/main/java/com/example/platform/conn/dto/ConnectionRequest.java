package com.example.platform.conn.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 新建 / 编辑连接配置的入参。
 *
 * <p>编辑时 {@code password} 留空表示"沿用原密码"。</p>
 */
public record ConnectionRequest(
        @NotBlank(message = "连接名称不能为空")
        @Size(max = 128, message = "连接名称不能超过 128 个字符")
        String name,

        @NotBlank(message = "数据库类型不能为空")
        String dbType,

        @NotBlank(message = "主机地址不能为空")
        @Size(max = 255, message = "主机地址不能超过 255 个字符")
        String host,

        @NotNull(message = "端口不能为空")
        @Min(value = 1, message = "端口必须在 1-65535 之间")
        @Max(value = 65535, message = "端口必须在 1-65535 之间")
        Integer port,

        @Size(max = 128, message = "库名不能超过 128 个字符")
        String databaseName,

        @NotBlank(message = "用户名不能为空")
        @Size(max = 128, message = "用户名不能超过 128 个字符")
        String username,

        @Size(max = 128, message = "密码不能超过 128 个字符")
        String password,

        @Size(max = 512, message = "扩展参数不能超过 512 个字符")
        String params,

        @Size(max = 512, message = "备注不能超过 512 个字符")
        String remark) {
}
