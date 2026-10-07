package com.example.platform.common.web.context;

import com.example.platform.common.core.api.ErrorCode;
import com.example.platform.common.core.exception.BusinessException;
import com.example.platform.common.security.LoginUser;

/**
 * 当前请求的登录用户，由 {@code UserContextFilter} 从网关下发的请求头写入。
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    /** 需要登录态的地方调用；缺失说明请求绕过了网关。 */
    public static LoginUser require() {
        LoginUser user = HOLDER.get();
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
