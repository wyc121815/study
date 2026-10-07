package com.example.platform.conn.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.example.platform.common.core.api.Result;
import com.example.platform.conn.dto.UserInfoDto;

/**
 * 调用公共服务 auth-service。
 *
 * <p>{@code url} 留空走注册中心 + 负载均衡；本地模式通过
 * {@code app.clients.auth-service-url} 直接指定地址。</p>
 */
@FeignClient(name = "auth-service", url = "${app.clients.auth-service-url:}")
public interface UserClient {

    @GetMapping("/api/users/{id}")
    Result<UserInfoDto> getById(@PathVariable("id") Long id);
}
