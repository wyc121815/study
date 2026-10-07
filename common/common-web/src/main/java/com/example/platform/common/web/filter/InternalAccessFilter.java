package com.example.platform.common.web.filter;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.platform.common.core.api.ErrorCode;
import com.example.platform.common.core.constant.Headers;
import com.example.platform.common.security.InternalAuthProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 业务服务的入口闸门：{@code /api/**} 必须携带有效的 {@code X-Internal-Token}。
 *
 * <p>这个头只有网关和服务间 Feign 调用才会带上，因此它能保证业务接口只能被
 * "可信入口"访问。即使服务端口被误暴露、被内网横向访问，直接打 {@code :8081}
 * 或 {@code :8082} 也只会拿到 401，而不是像以前那样只读接口直接返回数据。</p>
 *
 * <p>放行名单默认是三个免登录的认证接口，它们由浏览器直连网关调用，网关不会
 * 为此类请求注入内部密钥；名单可通过 {@code app.internal.permit-paths} 调整。</p>
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class InternalAccessFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(InternalAccessFilter.class);

    private static final String API_PREFIX = "/api/";

    private final InternalAuthProperties properties;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public InternalAccessFilter(InternalAuthProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        // 非业务接口（actuator、静态资源等）与预检请求不拦
        if (!path.startsWith(API_PREFIX)
                || HttpMethod.OPTIONS.matches(request.getMethod())
                || isPermitted(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String expected = properties.getSecret();
        String actual = request.getHeader(Headers.INTERNAL_TOKEN);
        if (StringUtils.hasText(expected) && expected.equals(actual)) {
            filterChain.doFilter(request, response);
            return;
        }

        log.warn("拒绝未经网关转发的业务请求: {} {}", request.getMethod(), path);
        ResponseWriters.writeError(response, objectMapper, ErrorCode.UNAUTHORIZED,
                "请求必须经过网关");
    }

    private boolean isPermitted(String path) {
        return properties.getPermitPaths() != null
                && properties.getPermitPaths().stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }
}
