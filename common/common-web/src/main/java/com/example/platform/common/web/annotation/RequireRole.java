package com.example.platform.common.web.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注需要特定角色才能访问的接口，可加在方法或类上（方法优先）。
 *
 * <p>由 {@code RoleInterceptor} 读取：未登录返回 401，角色不匹配返回 403。
 * 例：{@code @RequireRole(Roles.ADMIN)}。</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequireRole {

    /** 允许访问的角色，命中任意一个即可。 */
    String[] value();
}
