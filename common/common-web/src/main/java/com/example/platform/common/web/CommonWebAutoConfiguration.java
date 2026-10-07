package com.example.platform.common.web;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.example.platform.common.web.exception.GlobalExceptionHandler;
import com.example.platform.common.web.filter.InternalAccessFilter;
import com.example.platform.common.web.filter.TraceIdFilter;
import com.example.platform.common.web.filter.UserContextFilter;
import com.example.platform.common.web.interceptor.RoleInterceptor;

/**
 * 业务服务引入 common-web 后自动获得：内部调用闸门、全局异常处理、traceId、
 * 登录用户上下文、角色鉴权。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import({GlobalExceptionHandler.class, TraceIdFilter.class,
        InternalAccessFilter.class, UserContextFilter.class})
public class CommonWebAutoConfiguration {

    /** 注册 {@code @RequireRole} 的拦截器。 */
    @Bean
    public WebMvcConfigurer roleWebMvcConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new RoleInterceptor());
            }
        };
    }
}
