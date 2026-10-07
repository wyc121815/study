package com.example.platform.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.platform.auth.config.AuthProperties;
import com.example.platform.auth.entity.RefreshToken;
import com.example.platform.auth.repository.RefreshTokenRepository;

/**
 * 刷新令牌的签发、校验与吊销。明文令牌只返回给客户端一次，库里只存哈希。
 */
@Service
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final AuthProperties properties;

    public RefreshTokenService(RefreshTokenRepository repository, AuthProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    /** 签发一枚新的刷新令牌，返回明文（仅此一次）。 */
    @Transactional
    public String issue(Long userId, String clientIp, String userAgent) {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String plain = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        RefreshToken entity = new RefreshToken();
        entity.setUserId(userId);
        entity.setTokenHash(hash(plain));
        entity.setExpiresAt(LocalDateTime.now().plusDays(properties.getRefreshTtlDays()));
        entity.setClientIp(truncate(clientIp, 64));
        entity.setUserAgent(truncate(userAgent, 255));
        repository.save(entity);
        return plain;
    }

    @Transactional(readOnly = true)
    public Optional<RefreshToken> find(String plainToken) {
        if (plainToken == null || plainToken.isBlank()) {
            return Optional.empty();
        }
        return repository.findByTokenHash(hash(plainToken.trim()));
    }

    @Transactional
    public void revoke(RefreshToken token) {
        if (!token.isRevoked()) {
            token.revoke(LocalDateTime.now());
            repository.save(token);
        }
    }

    /** 轮换：作废旧令牌并签发新的，记录替换关系。 */
    @Transactional
    public String rotate(RefreshToken old, String clientIp, String userAgent) {
        old.revoke(LocalDateTime.now());
        String next = issue(old.getUserId(), clientIp, userAgent);
        old.setReplacedByHash(hash(next));
        repository.save(old);
        return next;
    }

    /** 吊销某用户全部有效令牌。 */
    @Transactional
    public int revokeAllForUser(Long userId) {
        return repository.revokeAllForUser(userId, LocalDateTime.now());
    }

    public long getTtlSeconds() {
        return properties.getRefreshTtlDays() * 24 * 60 * 60;
    }

    static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("运行环境不支持 SHA-256", e);
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
