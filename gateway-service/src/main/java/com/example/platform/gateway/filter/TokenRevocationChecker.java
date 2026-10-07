package com.example.platform.gateway.filter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.example.platform.common.security.AuthRedisKeys;
import com.example.platform.common.security.ParsedAccessToken;
import com.example.platform.gateway.config.GatewayAuthProperties;

import reactor.core.publisher.Mono;

/**
 * 查 Redis 判断访问令牌是否已被吊销。
 *
 * <p>无状态 JWT 本身无法提前作废，这里靠两把 Redis key 补齐：
 * 单次会话登出写 {@code revoked:jti:<jti>}；改密/强制下线写
 * {@code revoked-before:<userId>}，凡是签发时间不晚于该水位的令牌全部失效。</p>
 */
@Component
public class TokenRevocationChecker {

    private static final Logger log = LoggerFactory.getLogger(TokenRevocationChecker.class);

    private final ReactiveStringRedisTemplate redis;
    private final GatewayAuthProperties properties;

    public TokenRevocationChecker(ReactiveStringRedisTemplate redis, GatewayAuthProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public Mono<Boolean> isRevoked(ParsedAccessToken token) {
        boolean checkJti = StringUtils.hasText(token.jti());
        List<String> keys = new ArrayList<>(2);
        if (checkJti) {
            keys.add(AuthRedisKeys.revokedJti(token.jti()));
        }
        keys.add(AuthRedisKeys.userRevokedBefore(token.user().userId()));

        return redis.opsForValue()
                .multiGet(keys)
                .map(values -> revoked(keys, values, token, checkJti))
                .defaultIfEmpty(false)
                .onErrorResume(e -> {
                    log.error("查询令牌吊销状态失败，按 fail-open={} 处理: {}",
                            properties.isFailOpenOnRedisError(), e.getMessage());
                    return Mono.just(!properties.isFailOpenOnRedisError());
                });
    }

    private boolean revoked(List<String> keys, List<String> values, ParsedAccessToken token, boolean checkJti) {
        if (values == null || values.isEmpty()) {
            return false;
        }
        int index = 0;
        if (checkJti) {
            if (valueAt(values, index++) != null) {
                return true;
            }
        }
        String before = valueAt(values, index);
        if (before == null) {
            return false;
        }
        try {
            Instant issuedAt = token.issuedAt();
            return issuedAt != null && issuedAt.getEpochSecond() <= Long.parseLong(before.trim());
        } catch (NumberFormatException e) {
            log.warn("吊销水位值不合法，忽略: key={}, value={}", keys.get(index), before);
            return false;
        }
    }

    private static String valueAt(List<String> values, int index) {
        return index >= 0 && index < values.size() ? values.get(index) : null;
    }
}
