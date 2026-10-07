package com.example.platform.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 登录相关策略，前缀 {@code app.auth}。
 */
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    /** 刷新令牌有效期（天）。 */
    private long refreshTtlDays = 7;

    /** 连续失败多少次后临时锁定。 */
    private int maxFailedAttempts = 5;

    /** 锁定时长（分钟）。 */
    private long lockMinutes = 15;

    /** 新密码最小长度。 */
    private int minPasswordLength = 8;

    /** 用户级吊销水位的额外保留时间（秒），避免时钟漂移导致边界令牌漏网。 */
    private long revocationGraceSeconds = 120;

    public long getRefreshTtlDays() {
        return refreshTtlDays;
    }

    public void setRefreshTtlDays(long refreshTtlDays) {
        this.refreshTtlDays = refreshTtlDays;
    }

    public int getMaxFailedAttempts() {
        return maxFailedAttempts;
    }

    public void setMaxFailedAttempts(int maxFailedAttempts) {
        this.maxFailedAttempts = maxFailedAttempts;
    }

    public long getLockMinutes() {
        return lockMinutes;
    }

    public void setLockMinutes(long lockMinutes) {
        this.lockMinutes = lockMinutes;
    }

    public int getMinPasswordLength() {
        return minPasswordLength;
    }

    public void setMinPasswordLength(int minPasswordLength) {
        this.minPasswordLength = minPasswordLength;
    }

    public long getRevocationGraceSeconds() {
        return revocationGraceSeconds;
    }

    public void setRevocationGraceSeconds(long revocationGraceSeconds) {
        this.revocationGraceSeconds = revocationGraceSeconds;
    }
}
