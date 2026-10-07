package com.example.platform.auth.service;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.example.platform.auth.config.AuthProperties;
import com.example.platform.common.security.AuthRedisKeys;
import com.example.platform.common.security.JwtProperties;
import com.example.platform.common.security.JwtService;
import com.example.platform.common.security.ParsedAccessToken;

/**
 * 往 Redis 写访问令牌的吊销标记，供网关侧校验。
 *
 * <p>两种粒度：</p>
 * <ul>
 *   <li>jti 黑名单 —— 单次会话登出，只作废当前这一枚访问令牌</li>
 *   <li>用户级水位 —— 改密/强制下线，作废该用户此前签发的所有访问令牌</li>
 * </ul>
 *
 * <p>Redis 写入失败只记日志：最坏情况退化成"访问令牌到期前仍可用"，
 * 不影响登出本身（刷新令牌已经在库里被吊销）。</p>
 */
@Service
public class TokenRevocationService {

    private static final Logger log = LoggerFactory.getLogger(TokenRevocationService.class);

    private static final String REVOKED = "1";

    private final StringRedisTemplate redis;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final AuthProperties authProperties;

    public TokenRevocationService(StringRedisTemplate redis,
                                  JwtService jwtService,
                                  JwtProperties jwtProperties,
                                  AuthProperties authProperties) {
        this.redis = redis;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.authProperties = authProperties;
    }

    /** 拉黑一枚访问令牌，TTL 取它的剩余有效期（过期令牌无需拉黑）。 */
    public void revokeAccessToken(String accessToken) {
        if (!StringUtils.hasText(accessToken)) {
            return;
        }
        ParsedAccessToken parsed;
        try {
            parsed = jwtService.parseToken(accessToken);
        } catch (RuntimeException e) {
            // 已过期或本来就无效，无需拉黑
            return;
        }
        if (!StringUtils.hasText(parsed.jti()) || parsed.expiresAt() == null) {
            return;
        }
        long ttlSeconds = Duration.between(Instant.now(), parsed.expiresAt()).getSeconds();
        if (ttlSeconds <= 0) {
            return;
        }
        try {
            redis.opsForValue().set(AuthRedisKeys.revokedJti(parsed.jti()), REVOKED,
                    Duration.ofSeconds(ttlSeconds));
            log.info("已拉黑访问令牌: userId={}, jti={}", parsed.user().userId(), parsed.jti());
        } catch (RuntimeException e) {
            log.error("写入令牌黑名单失败，该访问令牌将在到期前继续有效: jti={}", parsed.jti(), e);
        }
    }

    /** 用户级吊销：此时间点之前签发的访问令牌全部失效。 */
    public void revokeAllAccessTokens(Long userId) {
        if (userId == null) {
            return;
        }
        long now = Instant.now().getEpochSecond();
        // 水位只需保留到"现存最老的访问令牌也过期"为止
        long ttlSeconds = jwtProperties.getTtlMinutes() * 60 + authProperties.getRevocationGraceSeconds();
        try {
            redis.opsForValue().set(AuthRedisKeys.userRevokedBefore(userId), String.valueOf(now),
                    Duration.ofSeconds(ttlSeconds));
            log.info("已吊销该用户此前的全部访问令牌: userId={}, revokedBefore={}", userId, now);
        } catch (RuntimeException e) {
            log.error("写入用户级吊销水位失败，旧访问令牌将在到期前继续有效: userId={}", userId, e);
        }
    }
}
