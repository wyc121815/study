package com.example.platform.common.web.filter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import com.example.platform.common.core.api.ErrorCode;
import com.example.platform.common.core.api.Result;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 过滤器阶段直接回写统一响应体（此时还进不到 @RestControllerAdvice）。
 */
final class ResponseWriters {

    private ResponseWriters() {
    }

    static void writeError(HttpServletResponse response, ObjectMapper objectMapper,
                           ErrorCode errorCode, String message) throws IOException {
        Result<Void> body = Result.fail(errorCode, message);
        response.setStatus(HttpStatus.valueOf(errorCode.httpStatus()).value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
