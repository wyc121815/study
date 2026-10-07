package com.example.platform.gateway.filter;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import com.example.platform.common.core.api.ErrorCode;
import com.example.platform.common.core.api.Result;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;

/**
 * 兜住网关层异常，保证任何情况下返回的都是统一的 JSON 结构。
 */
@Component
@Order(-1)
public class GatewayExceptionHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayExceptionHandler.class);

    private final ObjectMapper objectMapper;

    public GatewayExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        ErrorCode errorCode;
        String message;
        if (ex instanceof ResponseStatusException statusException) {
            int status = statusException.getStatusCode().value();
            errorCode = status == 404 ? ErrorCode.NOT_FOUND : ErrorCode.INTERNAL_ERROR;
            message = statusException.getReason() == null ? errorCode.message() : statusException.getReason();
        } else if (ex instanceof ConnectException) {
            errorCode = ErrorCode.SERVICE_UNAVAILABLE;
            message = "下游服务不可用，请稍后重试";
        } else if (ex instanceof TimeoutException) {
            errorCode = ErrorCode.SERVICE_UNAVAILABLE;
            message = "请求下游服务超时";
        } else {
            errorCode = ErrorCode.INTERNAL_ERROR;
            message = ErrorCode.INTERNAL_ERROR.message();
        }

        if (errorCode == ErrorCode.INTERNAL_ERROR) {
            log.error("网关处理请求失败: {}", exchange.getRequest().getURI(), ex);
        } else {
            log.warn("网关处理请求失败: {} - {}", exchange.getRequest().getURI(), ex.getMessage());
        }

        response.setStatusCode(HttpStatus.valueOf(errorCode.httpStatus()));
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        Result<Void> body = Result.fail(errorCode, message);
        Object traceId = exchange.getAttributes().get(TraceIdGlobalFilter.TRACE_ID_ATTR);
        if (traceId != null) {
            body.setTraceId(String.valueOf(traceId));
        }
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
        } catch (Exception e) {
            return response.writeWith(Mono.just(response.bufferFactory()
                    .wrap(message.getBytes(StandardCharsets.UTF_8))));
        }
    }
}
