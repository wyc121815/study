package com.example.platform.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 网关：整个平台对外的唯一入口。
 *
 * <p>职责：路由转发、JWT 校验、跨域、traceId 生成、把登录用户信息下发给后端服务。</p>
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
