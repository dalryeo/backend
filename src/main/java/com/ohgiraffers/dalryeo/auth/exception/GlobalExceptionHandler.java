package com.ohgiraffers.dalryeo.auth.exception;

import com.ohgiraffers.dalryeo.common.CommonResponse;
import com.ohgiraffers.dalryeo.record.exception.RecordValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<CommonResponse<Map<String, Object>>> handleAuthException(AuthException e) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(CommonResponse.failure(errorBody(e.getErrorCode().getCode(), e.getErrorCode().getMessage())));
    }

    @ExceptionHandler(RecordValidationException.class)
    public ResponseEntity<CommonResponse<Map<String, Object>>> handleRecordValidationException(RecordValidationException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(CommonResponse.failure(errorBody(
                        e.getErrorCode().getCode(),
                        e.getErrorCode().getMessage()
                )));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CommonResponse<Map<String, Object>>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException e
    ) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }

        String message = fieldErrors.isEmpty()
                ? "요청 값이 올바르지 않습니다."
                : fieldErrors.values().iterator().next();

        Map<String, Object> error = errorBody("BAD_REQUEST", message);
        error.put("errors", fieldErrors);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(CommonResponse.failure(error));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<CommonResponse<Map<String, Object>>> handleIllegalArgumentException(IllegalArgumentException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(CommonResponse.failure(errorBody("BAD_REQUEST", e.getMessage())));
    }

    private Map<String, Object> errorBody(String code, String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("code", code);
        error.put("message", message);
        return error;
    }
}
