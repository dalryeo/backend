package com.ohgiraffers.dalryeo.common.exception;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.ohgiraffers.dalryeo.common.CommonResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String BAD_REQUEST = "BAD_REQUEST";
    private static final String INVALID_REQUEST_BODY_MESSAGE = "요청 본문 형식이 올바르지 않습니다.";
    private static final String INVALID_REQUEST_VALUE_MESSAGE = "요청 값의 형식이 올바르지 않습니다.";
    private static final String INVALID_OFFSET_DATE_TIME_MESSAGE =
            "시간 값은 timezone offset을 포함해야 합니다. 예: 2026-04-14T12:13:09+09:00";
    private static final String INVALID_LOCAL_DATE_MESSAGE =
            "날짜 값은 yyyy-MM-dd 형식이어야 합니다. 예: 2001-01-01";
    private static final String INVALID_NUMBER_MESSAGE = "숫자 형식으로 입력해야 합니다.";
    private static final String INVALID_BOOLEAN_MESSAGE = "true 또는 false로 입력해야 합니다.";
    private static final String INVALID_ENUM_MESSAGE = "허용된 값 중 하나로 입력해야 합니다.";

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<CommonResponse<Map<String, Object>>> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(CommonResponse.failure(errorBody(
                        errorCode.getCode(),
                        e.getMessage()
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

        Map<String, Object> error = errorBody(BAD_REQUEST, message);
        error.put("errors", fieldErrors);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(CommonResponse.failure(error));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<CommonResponse<Map<String, Object>>> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException e
    ) {
        Map<String, Object> error = errorBody(BAD_REQUEST, INVALID_REQUEST_BODY_MESSAGE);
        resolveJsonFieldError(e).ifPresent(fieldError -> {
            Map<String, String> fieldErrors = new LinkedHashMap<>();
            fieldErrors.put(fieldError.fieldPath(), fieldError.message());
            error.put("errors", fieldErrors);
        });

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(CommonResponse.failure(error));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<CommonResponse<Map<String, Object>>> handleIllegalArgumentException(IllegalArgumentException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(CommonResponse.failure(errorBody(BAD_REQUEST, e.getMessage())));
    }

    private Map<String, Object> errorBody(String code, String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("code", code);
        error.put("message", message);
        return error;
    }

    private Optional<JsonFieldError> resolveJsonFieldError(HttpMessageNotReadableException e) {
        if (!(e.getCause() instanceof JsonMappingException mappingException)) {
            return Optional.empty();
        }

        String fieldPath = resolveFieldPath(mappingException.getPath());
        if (fieldPath == null || fieldPath.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(new JsonFieldError(
                fieldPath,
                safeJsonParseErrorMessage(resolveTargetType(mappingException))
        ));
    }

    private String resolveFieldPath(List<JsonMappingException.Reference> path) {
        if (path == null || path.isEmpty()) {
            return null;
        }

        StringBuilder fieldPath = new StringBuilder();
        for (JsonMappingException.Reference reference : path) {
            if (reference.getFieldName() != null) {
                if (fieldPath.length() > 0) {
                    fieldPath.append(".");
                }
                fieldPath.append(reference.getFieldName());
                continue;
            }

            if (reference.getIndex() >= 0) {
                fieldPath.append("[")
                        .append(reference.getIndex())
                        .append("]");
            }
        }

        return fieldPath.isEmpty() ? null : fieldPath.toString();
    }

    private Class<?> resolveTargetType(JsonMappingException e) {
        if (e instanceof InvalidFormatException invalidFormatException) {
            return invalidFormatException.getTargetType();
        }
        if (e instanceof MismatchedInputException mismatchedInputException) {
            return mismatchedInputException.getTargetType();
        }
        return null;
    }

    private String safeJsonParseErrorMessage(Class<?> targetType) {
        if (targetType == null) {
            return INVALID_REQUEST_VALUE_MESSAGE;
        }
        if (OffsetDateTime.class.equals(targetType)) {
            return INVALID_OFFSET_DATE_TIME_MESSAGE;
        }
        if (LocalDate.class.equals(targetType)) {
            return INVALID_LOCAL_DATE_MESSAGE;
        }
        if (isNumberType(targetType)) {
            return INVALID_NUMBER_MESSAGE;
        }
        if (Boolean.class.equals(targetType) || boolean.class.equals(targetType)) {
            return INVALID_BOOLEAN_MESSAGE;
        }
        if (targetType.isEnum()) {
            return INVALID_ENUM_MESSAGE;
        }
        return INVALID_REQUEST_VALUE_MESSAGE;
    }

    private boolean isNumberType(Class<?> targetType) {
        return Number.class.isAssignableFrom(targetType)
                || int.class.equals(targetType)
                || long.class.equals(targetType)
                || double.class.equals(targetType)
                || float.class.equals(targetType)
                || short.class.equals(targetType)
                || byte.class.equals(targetType);
    }

    private record JsonFieldError(String fieldPath, String message) {
    }
}
