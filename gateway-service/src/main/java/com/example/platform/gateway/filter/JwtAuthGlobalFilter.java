package com.example.platform.gateway.filter;

import java.nio.charset.StandardCharsets;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;

import com.example.platform.common.core.api.ErrorCode;
import com.example.platform.common.core.api.Result;
import com.example.platform.common.core.constant.Headers;
import com.example.platform.common.core.exception.BusinessException;
import com.example.platform.common.security.JwtService;
import com.example.platform.common.security.LoginUser;
import com.example.platform.common.security.ParsedAccessToken;
import com.example.platform.gateway.config.GatewayAuthProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;

/**
 * 全局 JWT 校验。
 *
 * <p>校验通过后把用户信息写进请求头，下游服务直接读头即可，不必每个服务都解析一次 token。
 * 同时打上 {@code X-From-Gateway} 标记，服务端可以用它拒绝绕过网关的直连请求。</p>
 */
@Component
@EnableConfigurationProperties(GatewayAuthProperties.class)
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String LOGIN_PATH = "/api/auth/login";

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;
    private final GatewayAuthProperties properties;
    private final TokenRevocationChecker revocationChecker;
    private final LoginRateLimiter loginRateLimiter;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public JwtAuthGlobalFilter(JwtService jwtService,
                               ObjectMapper objectMapper,
                               GatewayAuthProperties properties,
                               TokenRevocationChecker revocationChecker,
                               LoginRateLimiter loginRateLimiter) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.revocationChecker = revocationChecker;
        this.loginRateLimiter = loginRateLimiter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 预检请求直接放行
        if (HttpMethod.OPTIONS.equals(request.getMethod())) {
            return chain.filter(exchange);
        }

        // 登录接口免鉴权，但要过一层按来源 IP 的限流
        if (pathMatcher.match(LOGIN_PATH, path)) {
            return loginRateLimiter.isAllowed(clientIp(request))
                    .flatMap(allowed -> allowed
                            ? chain.filter(exchange)
                            : writeError(exchange, ErrorCode.TOO_MANY_REQUESTS, "登录尝试过于频繁，请稍后再试"));
        }

        if (isPermitted(path)) {
            return chain.filter(exchange);
        }

        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return writeError(exchange, ErrorCode.UNAUTHORIZED, "缺少访问令牌");
        }

        ParsedAccessToken parsed;
        try {
            parsed = jwtService.parseToken(authorization.substring(BEARER_PREFIX.length()));
        } catch (BusinessException e) {
            return writeError(exchange, e.getErrorCode(), e.getMessage());
        }

        // 令牌本身有效，还要确认没被登出/改密提前作废（查 Redis 吊销名单）
        return revocationChecker.isRevoked(parsed)
                .flatMap(revoked -> {
                    if (revoked) {
                        return writeError(exchange, ErrorCode.UNAUTHORIZED, "登录已失效，请重新登录");
                    }
                    LoginUser user = parsed.user();
                    ServerHttpRequest mutated = request.mutate()
                            // 先清掉客户端可能自己带的身份头，再写入网关照信后的值
                            .headers(headers -> {
                                headers.remove(Headers.USER_ID);
                                headers.remove(Headers.USERNAME);
                                headers.remove(Headers.USER_ROLE);
                                headers.remove(Headers.FROM_GATEWAY);
                                headers.remove(Headers.INTERNAL_TOKEN);
                            })
                            .header(Headers.USER_ID, String.valueOf(user.userId()))
                            .header(Headers.USERNAME, user.username() == null ? "" : user.username())
                            .header(Headers.USER_ROLE, user.role() == null ? "" : user.role())
                            .header(Headers.FROM_GATEWAY, "true")
                            .header(Headers.INTERNAL_TOKEN, properties.getInternalSecret())
                            .build();
                    return chain.filter(exchange.mutate().request(mutated).build());
                });
    }

    /** 取客户端 IP；经 Nginx 反代时优先用 X-Forwarded-For 的第一段。 */
    private static String clientIp(ServerHttpRequest request) {
        String forwarded = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return normalizeIp((comma > 0 ? forwarded.substring(0, comma) : forwarded).trim());
        }
        if (request.getRemoteAddress() == null) {
            return null;
        }
        return normalizeIp(request.getRemoteAddress().getAddress().getHostAddress());
    }

    /**
     * 归一化 IP 写法，避免同一个来源被拆成多个限流桶：
     * 把 IPv4-mapped IPv6（{@code ::ffff:1.2.3.4}）还原成 IPv4，
     * 并把 IPv6 回环统一成 {@code 127.0.0.1}（本机用 localhost 访问时两者都可能出现）。
     */
    static String normalizeIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return ip;
        }
        String value = ip.trim();
        if (value.startsWith("::ffff:")) {
            return value.substring("::ffff:".length());
        }
        if ("0:0:0:0:0:0:0:1".equals(value) || "::1".equals(value)) {
            return "127.0.0.1";
        }
        return value;
    }

    private boolean isPermitted(String path) {
        if (properties.getPermitPaths() == null) {
            return false;
        }
        return properties.getPermitPaths().stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private Mono<Void> writeError(ServerWebExchange exchange, ErrorCode errorCode, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.valueOf(errorCode.httpStatus()));
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Result<Void> body = Result.fail(errorCode, message);
        Object traceId = exchange.getAttributes().get(TraceIdGlobalFilter.TRACE_ID_ATTR);
        if (traceId != null) {
            body.setTraceId(String.valueOf(traceId));
        }

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            bytes = ("{\"code\":" + errorCode.code() + ",\"message\":\"认证失败\"}").getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        // 比 TraceIdGlobalFilter 晚，比转发早
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }
}
