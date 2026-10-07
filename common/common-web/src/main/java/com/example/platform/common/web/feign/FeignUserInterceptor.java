package com.example.platform.common.web.feign;

import org.springframework.util.StringUtils;

import com.example.platform.common.core.constant.Headers;
import com.example.platform.common.security.InternalAuthProperties;
import com.example.platform.common.security.LoginUser;
import com.example.platform.common.web.context.UserContext;

import feign.RequestInterceptor;
import feign.RequestTemplate;

/**
 * 服务间调用时把当前登录用户与 traceId 继续往下传，保证下游也能拿到操作人。
 */
public class FeignUserInterceptor implements RequestInterceptor {

    private final InternalAuthProperties properties;

    public FeignUserInterceptor(InternalAuthProperties properties) {
        this.properties = properties;
    }

    @Override
    public void apply(RequestTemplate template) {
        LoginUser user = UserContext.get();
        if (user != null) {
            template.header(Headers.USER_ID, String.valueOf(user.userId()));
            if (StringUtils.hasText(user.username())) {
                template.header(Headers.USERNAME, user.username());
            }
            if (StringUtils.hasText(user.role())) {
                template.header(Headers.USER_ROLE, user.role());
            }
        }
        String traceId = org.slf4j.MDC.get(com.example.platform.common.web.filter.TraceIdFilter.MDC_KEY);
        if (StringUtils.hasText(traceId)) {
            template.header(Headers.TRACE_ID, traceId);
        }
        template.header(Headers.FROM_GATEWAY, "true");
        // 带上内部密钥，下游才会采信上面的身份头
        if (StringUtils.hasText(properties.getSecret())) {
            template.header(Headers.INTERNAL_TOKEN, properties.getSecret());
        }
    }
}
