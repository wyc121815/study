package com.example.platform.common.web.filter;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.platform.common.core.api.ErrorCode;
import com.example.platform.common.core.constant.Headers;
import com.example.platform.common.security.InternalAuthProperties;
import com.example.platform.common.security.LoginUser;
import com.example.platform.common.web.context.UserContext;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 从网关注入的请求头还原登录用户，写进 {@link UserContext}。
 *
 * <p>身份头只有在同时带上正确的 {@code X-Internal-Token}（网关或服务间 Feign
 * 才会持有）时才被采信；否则一律按未登录处理，防止外部直接访问业务服务端口，
 * 靠伪造 {@code X-User-Id} 冒充任意用户。</p>
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class UserContextFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(UserContextFilter.class);

    private final InternalAuthProperties properties;
    private final ObjectMapper objectMapper;

    public UserContextFilter(InternalAuthProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String userId = request.getHeader(Headers.USER_ID);
        String username = request.getHeader(Headers.USERNAME);
        String role = request.getHeader(Headers.USER_ROLE);
        try {
            if (StringUtils.hasText(userId)) {
                if (!isTrustedInternalCall(request)) {
                    log.warn("拒绝不可信的身份头: path={}, userId={}", request.getRequestURI(), userId);
                    writeUnauthorized(response);
                    return;
                }
                try {
                    UserContext.set(new LoginUser(Long.valueOf(userId), username, role));
                } catch (NumberFormatException ignored) {
                    // 头被伪造或损坏，按未登录处理
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }

    private boolean isTrustedInternalCall(HttpServletRequest request) {
        String expected = properties.getSecret();
        if (!StringUtils.hasText(expected)) {
            log.warn("未配置 app.internal.secret，所有身份头都不会被采信");
            return false;
        }
        String actual = request.getHeader(Headers.INTERNAL_TOKEN);
        return expected.equals(actual);
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        ResponseWriters.writeError(response, objectMapper, ErrorCode.UNAUTHORIZED,
                ErrorCode.UNAUTHORIZED.message());
    }
}
