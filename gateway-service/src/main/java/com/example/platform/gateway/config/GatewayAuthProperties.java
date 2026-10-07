package com.example.platform.gateway.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 网关鉴权配置，前缀 {@code app.gateway}。
 */
@ConfigurationProperties(prefix = "app.gateway")
public class GatewayAuthProperties {

    /** 无需登录即可访问的路径（Ant 风格）。 */
    private List<String> permitPaths = new ArrayList<>(
            List.of("/api/auth/login", "/api/auth/refresh", "/api/auth/logout", "/actuator/**"));

    /** 内部共享密钥，随请求下发给业务服务，证明身份头可信。 */
    private String internalSecret = "";

    /**
     * Redis 不可用时是否放行请求。
     *
     * <p>{@code true}（默认）优先保证可用性：吊销名单查不了就按未吊销处理，
     * 同时打错误日志；{@code false} 则一律拒绝，安全性更高但 Redis 抖动会影响全站。</p>
     */
    private boolean failOpenOnRedisError = true;

    /** 登录限流：同一 IP 在窗口内允许的最大尝试次数，小于等于 0 表示关闭。 */
    private int loginMaxAttemptsPerWindow = 20;

    /** 登录限流窗口（秒）。 */
    private long loginWindowSeconds = 60;

    public List<String> getPermitPaths() {
        return permitPaths;
    }

    public void setPermitPaths(List<String> permitPaths) {
        this.permitPaths = permitPaths;
    }

    public String getInternalSecret() {
        return internalSecret;
    }

    public void setInternalSecret(String internalSecret) {
        this.internalSecret = internalSecret;
    }

    public boolean isFailOpenOnRedisError() {
        return failOpenOnRedisError;
    }

    public void setFailOpenOnRedisError(boolean failOpenOnRedisError) {
        this.failOpenOnRedisError = failOpenOnRedisError;
    }

    public int getLoginMaxAttemptsPerWindow() {
        return loginMaxAttemptsPerWindow;
    }

    public void setLoginMaxAttemptsPerWindow(int loginMaxAttemptsPerWindow) {
        this.loginMaxAttemptsPerWindow = loginMaxAttemptsPerWindow;
    }

    public long getLoginWindowSeconds() {
        return loginWindowSeconds;
    }

    public void setLoginWindowSeconds(long loginWindowSeconds) {
        this.loginWindowSeconds = loginWindowSeconds;
    }
}
