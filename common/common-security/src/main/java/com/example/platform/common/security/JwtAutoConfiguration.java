package com.example.platform.common.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 引入 common-security 后自动装配 {@link JwtService}，网关和业务服务共用同一份实现。
 */
@AutoConfiguration
@EnableConfigurationProperties({JwtProperties.class, InternalAuthProperties.class})
public class JwtAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JwtService jwtService(JwtProperties properties) {
        return new JwtService(properties);
    }
}
