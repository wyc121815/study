package com.example.platform.common.web;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import com.example.platform.common.security.InternalAuthProperties;
import com.example.platform.common.web.feign.FeignUserInterceptor;

import feign.RequestInterceptor;

/**
 * 仅在应用引入了 OpenFeign 时生效，把登录用户透传给下游服务。
 */
@AutoConfiguration
@ConditionalOnClass(RequestInterceptor.class)
public class FeignAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "feignUserInterceptor")
    public RequestInterceptor feignUserInterceptor(InternalAuthProperties properties) {
        return new FeignUserInterceptor(properties);
    }
}
