package com.ohgiraffers.dalryeo.auth.exception;

import com.ohgiraffers.dalryeo.common.CommonResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<CommonResponse<Map<String, String>>> handleAuthException(AuthException e) {
        Map<String, String> error = new HashMap<>();
        error.put("code", e.getErrorCode().getCode());
        error.put("message", e.getErrorCode().getMessage());

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(CommonResponse.success(error));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<CommonResponse<Map<String, String>>> handleIllegalArgumentException(IllegalArgumentException e) {
        Map<String, String> error = new HashMap<>();
        error.put("code", "BAD_REQUEST");
        error.put("message", e.getMessage());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(CommonResponse.success(error));
    }
}
