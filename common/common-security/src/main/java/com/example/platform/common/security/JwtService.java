package com.example.platform.common.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import com.example.platform.common.core.api.ErrorCode;
import com.example.platform.common.core.exception.BusinessException;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * JWT 签发与校验。auth-service 用它签发，网关用它校验。
 */
public class JwtService {

    public static final String CLAIM_USERNAME = "username";
    public static final String CLAIM_ROLE = "role";
    /** 令牌用途声明，避免其它用途的 JWT 被当成访问令牌使用。 */
    public static final String CLAIM_TOKEN_TYPE = "typ";
    public static final String TOKEN_TYPE_ACCESS = "access";

    private final SecretKey key;
    private final Duration ttl;
    private final String issuer;

    public JwtService(JwtProperties properties) {
        String secret = properties.getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("缺少配置 app.jwt.secret，请设置环境变量 APP_JWT_SECRET");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(secret.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("app.jwt.secret 必须是 base64 编码", e);
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret 解码后至少 32 字节（HS256 要求），当前 " + keyBytes.length + " 字节");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.ttl = Duration.ofMinutes(properties.getTtlMinutes());
        this.issuer = properties.getIssuer();
    }

    /** 生成一份随机密钥，供本地初始化配置使用。 */
    public static String generateSecret() {
        byte[] bytes = new byte[32];
        new java.security.SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    public String issue(LoginUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString().replace("-", ""))
                .subject(String.valueOf(user.userId()))
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS)
                .claim(CLAIM_USERNAME, user.username())
                .claim(CLAIM_ROLE, user.role())
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 校验并解析令牌。任何异常都统一转成 {@link ErrorCode#TOKEN_INVALID}，
     * 避免把 jjwt 的内部异常暴露给调用方。
     */
    public LoginUser parse(String token) {
        return parseToken(token).user();
    }

    /** 解析令牌并保留 jti / iat / exp，供网关做吊销与限流判断。 */
    public ParsedAccessToken parseToken(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            // 老令牌不带 typ，兼容放行；带了就必须是 access
            String tokenType = claims.get(CLAIM_TOKEN_TYPE, String.class);
            if (tokenType != null && !TOKEN_TYPE_ACCESS.equals(tokenType)) {
                throw new BusinessException(ErrorCode.TOKEN_INVALID);
            }
            LoginUser user = new LoginUser(
                    Long.valueOf(claims.getSubject()),
                    claims.get(CLAIM_USERNAME, String.class),
                    claims.get(CLAIM_ROLE, String.class));
            return new ParsedAccessToken(user,
                    claims.getId(),
                    claims.getIssuedAt() == null ? null : claims.getIssuedAt().toInstant(),
                    claims.getExpiration() == null ? null : claims.getExpiration().toInstant());
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录已过期，请重新登录");
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
    }

    public long getTtlSeconds() {
        return ttl.toSeconds();
    }
}
