package com.example.platform.common.web.interceptor;

import java.util.Arrays;

import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.example.platform.common.core.api.ErrorCode;
import com.example.platform.common.core.exception.BusinessException;
import com.example.platform.common.security.LoginUser;
import com.example.platform.common.web.annotation.RequireRole;
import com.example.platform.common.web.context.UserContext;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 执行 {@link RequireRole} 声明的角色校验。方法上的注解优先于类上的。
 */
public class RoleInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RequireRole required = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (required == null) {
            required = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
        }
        if (required == null) {
            return true;
        }

        LoginUser user = UserContext.get();
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        String role = user.role();
        boolean allowed = role != null
                && Arrays.stream(required.value()).anyMatch(role::equalsIgnoreCase);
        if (!allowed) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return true;
    }
}
