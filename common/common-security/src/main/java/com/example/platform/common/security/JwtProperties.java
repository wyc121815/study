package com.example.platform.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 配置，前缀 {@code app.jwt}。
 */
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /** HMAC 密钥，base64 编码，解码后至少 32 字节。 */
    private String secret;

    /** 令牌有效期（分钟）。 */
    private long ttlMinutes = 30;

    /** 签发者标识。 */
    private String issuer = "conn-platform";

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getTtlMinutes() {
        return ttlMinutes;
    }

    public void setTtlMinutes(long ttlMinutes) {
        this.ttlMinutes = ttlMinutes;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }
}
