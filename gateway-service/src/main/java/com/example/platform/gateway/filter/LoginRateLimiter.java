package com.example.platform.gateway.filter;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;

import com.example.platform.common.security.AuthRedisKeys;
import com.example.platform.gateway.config.GatewayAuthProperties;

import reactor.core.publisher.Mono;

/**
 * 登录接口的固定窗口限流：同一 IP 在窗口内超过阈值就拒绝，防止撞库和暴力破解。
 *
 * <p>账号级锁定在 auth-service（落库、按账号），这里补一层按来源 IP 的网关照，
 * 两者互补。Redis 不可用时放行，避免限流组件故障导致全站登不上。</p>
 */
@Component
public class LoginRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimiter.class);

    private final ReactiveStringRedisTemplate redis;
    private final GatewayAuthProperties properties;

    public LoginRateLimiter(ReactiveStringRedisTemplate redis, GatewayAuthProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public Mono<Boolean> isAllowed(String clientIp) {
        int limit = properties.getLoginMaxAttemptsPerWindow();
        if (limit <= 0 || clientIp == null || clientIp.isBlank()) {
            return Mono.just(true);
        }
        String key = AuthRedisKeys.loginAttempts(clientIp);
        return redis.opsForValue()
                .increment(key)
                .flatMap(count -> {
                    if (count == 1L) {
                        return redis.expire(key, Duration.ofSeconds(properties.getLoginWindowSeconds()))
                                .thenReturn(count <= limit);
                    }
                    return Mono.just(count <= limit);
                })
                .onErrorResume(e -> {
                    log.error("登录限流查询失败，放行本次请求: {}", e.getMessage());
                    return Mono.just(true);
                });
    }
}
