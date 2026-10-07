package com.example.platform.common.web.exception;

import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.example.platform.common.core.api.ErrorCode;
import com.example.platform.common.core.api.Result;
import com.example.platform.common.core.exception.BusinessException;
import com.example.platform.common.web.filter.TraceIdFilter;

import org.slf4j.MDC;

import jakarta.validation.ConstraintViolationException;

/**
 * 统一异常出口：所有异常都翻译成 {@link Result}，HTTP 状态码取 {@link ErrorCode#httpStatus()}。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("业务异常: code={}, message={}", errorCode.code(), e.getMessage());
        return build(errorCode, e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<Result<Void>> handleValidation(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));
        return build(ErrorCode.BAD_REQUEST, message.isBlank() ? ErrorCode.BAD_REQUEST.message() : message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraint(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(v -> v.getMessage())
                .collect(Collectors.joining("；"));
        return build(ErrorCode.BAD_REQUEST, message.isBlank() ? ErrorCode.BAD_REQUEST.message() : message);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<Result<Void>> handleBadRequest(Exception e) {
        log.warn("请求不合法: {}", e.getMessage());
        return build(ErrorCode.BAD_REQUEST, "请求参数不合法");
    }

    /** 路径存在但 HTTP 方法不对（例如把 PUT 打到集合接口上），要回 405 而不是 500。 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方法不支持: method={}, message={}", e.getMethod(), e.getMessage());
        return build(ErrorCode.METHOD_NOT_ALLOWED,
                "该接口不支持 " + e.getMethod() + " 方法");
    }

    /** 请求体/参数类型不对，属于客户端错误。 */
    @ExceptionHandler({HttpMessageConversionException.class, MethodArgumentTypeMismatchException.class,
            HttpMediaTypeNotSupportedException.class})
    public ResponseEntity<Result<Void>> handleUnsupportedRequest(Exception e) {
        log.warn("请求格式不支持: {}", e.getMessage());
        return build(ErrorCode.BAD_REQUEST, "请求格式不支持");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNotFound(NoResourceFoundException e) {
        return build(ErrorCode.NOT_FOUND, "接口不存在");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnexpected(Exception e) {
        log.error("未预期的异常", e);
        return build(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.message());
    }

    private ResponseEntity<Result<Void>> build(ErrorCode errorCode, String message) {
        Result<Void> body = Result.fail(errorCode, message);
        body.setTraceId(MDC.get(TraceIdFilter.MDC_KEY));
        return ResponseEntity.status(HttpStatus.valueOf(errorCode.httpStatus())).body(body);
    }
}
