package com.example.platform.common.web.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import com.example.platform.common.core.api.ErrorCode;
import com.example.platform.common.core.api.Result;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void methodNotSupportedMapsTo405InsteadOf500() {
        ResponseEntity<Result<Void>> response =
                handler.handleMethodNotSupported(new HttpRequestMethodNotSupportedException("PUT"));

        assertThat(response.getStatusCode().value()).isEqualTo(405);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.METHOD_NOT_ALLOWED.code());
        assertThat(response.getBody().getMessage()).contains("PUT");
    }

    @Test
    void unsupportedMediaTypeMapsToBadRequest() {
        ResponseEntity<Result<Void>> response = handler.handleUnsupportedRequest(
                new HttpMediaTypeNotSupportedException("text/plain"));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.BAD_REQUEST.code());
    }
}
