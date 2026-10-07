package com.example.platform.common.core.constant;

/**
 * 平台角色。当前只有两级：
 *
 * <ul>
 *   <li>{@code ADMIN} —— 可增删改数据库连接、查看用户列表</li>
 *   <li>{@code USER} —— 只读：查看/搜索连接与做连通性测试</li>
 * </ul>
 *
 * <p>角色字符串会写进 JWT 与 {@code X-User-Role} 头，鉴权在业务服务侧
 * 通过 {@code @RequireRole} 完成。</p>
 */
public final class Roles {

    public static final String ADMIN = "ADMIN";
    public static final String USER = "USER";

    private Roles() {
    }

    public static boolean isAdmin(String role) {
        return ADMIN.equalsIgnoreCase(role);
    }
}
