package com.example.platform.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.example.platform.auth.config.AuthProperties;

/**
 * 公共服务：统一登录认证与用户信息，被网关和业务服务复用。
 */
@SpringBootApplication
@EnableConfigurationProperties(AuthProperties.class)
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
