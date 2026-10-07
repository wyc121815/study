package com.example.platform.common.core.constant;

import java.util.Locale;

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

    /**
     * 角色比较一律走这里：忽略大小写与首尾空白。
     *
     * <p>角色会经过 JWT、HTTP 头、用户输入等好几道手，任何一处出现 {@code admin}
     * 或 {@code " ADMIN "} 都不该让权限判断失效，所以不要在业务代码里直接对
     * 角色字符串用 {@code equals}/{@code ==}。</p>
     */
    public static boolean isAdmin(String role) {
        return ADMIN.equals(normalize(role));
    }

    public static boolean isUser(String role) {
        return USER.equals(normalize(role));
    }

    /** 是否是平台认识的角色（用于管理接口的入参校验）。 */
    public static boolean isValid(String role) {
        return normalize(role) != null;
    }

    /**
     * 归一化成"大写 + 去首尾空白"，非法值返回 {@code null}。
     * 入参校验用 {@link #isValid}，需要报错文案的地方用 {@link #normalize} 判空。
     */
    public static String normalize(String role) {
        if (role == null) {
            return null;
        }
        String normalized = role.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case ADMIN, USER -> normalized;
            default -> null;
        };
    }

    /**
     * 对外传递/展示用的规范角色：认识的归一化成大写，认不出的原样返回，
     * 避免把未知角色悄悄抹成 USER 而丢掉排查线索。
     */
    public static String canonical(String role) {
        String normalized = normalize(role);
        return normalized != null ? normalized : role;
    }
}
