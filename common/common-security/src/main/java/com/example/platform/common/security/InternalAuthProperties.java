package com.example.platform.common.security;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 服务间信任边界配置，前缀 {@code app.internal}。
 *
 * <p>网关通过 {@code X-Internal-Token} 下发这份共享密钥，业务服务据此判断
 * {@code X-User-Id} 等身份头是否可信；服务间 Feign 调用同样携带它。
 * 这样即使业务服务端口被误暴露，外部也无法靠伪造请求头冒充用户。</p>
 */
@ConfigurationProperties(prefix = "app.internal")
public class InternalAuthProperties {

    /** 内部共享密钥，生产环境必须用 APP_INTERNAL_SECRET 覆盖。 */
    private String secret;

    /**
     * 无需内部密钥即可访问的路径（Ant 风格），仅对 {@code /api/**} 生效。
     *
     * <p>默认只放行三个免登录的认证接口——它们本来就是给浏览器直接调的，
     * 网关不会给这类放行请求注入内部密钥。其余业务接口一律要求带上内部密钥，
     * 即必须经过网关（或服务间 Feign）转发。</p>
     */
    private List<String> permitPaths = new ArrayList<>(
            List.of("/api/auth/login", "/api/auth/refresh", "/api/auth/logout"));

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public List<String> getPermitPaths() {
        return permitPaths;
    }

    public void setPermitPaths(List<String> permitPaths) {
        this.permitPaths = permitPaths;
    }
}
