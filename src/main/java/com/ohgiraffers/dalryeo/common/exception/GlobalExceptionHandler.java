package com.ohgiraffers.dalryeo.common.exception;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.ohgiraffers.dalryeo.common.CommonResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
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

    // 도메인 예외를 공통 오류 응답으로 바꾸고 운영 로그를 남긴다.
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<CommonResponse<Map<String, Object>>> handleBusinessException(
            BusinessException e,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = e.getErrorCode();
        logApiError(errorCode.getCode(), errorCode.getStatus(), request, e);

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(CommonResponse.failure(errorBody(
                        errorCode.getCode(),
                        e.getMessage()
                )));
    }

    // Bean Validation 실패를 필드 오류 응답으로 바꾸고 운영 로그를 남긴다.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CommonResponse<Map<String, Object>>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException e,
            HttpServletRequest request
    ) {
        logApiError(BAD_REQUEST, HttpStatus.BAD_REQUEST, request, e);

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(CommonResponse.failure(validationErrorBody(fieldErrors)));
    }

    // 요청 파라미터 검증 실패를 공통 BAD_REQUEST 응답으로 바꾸고 운영 로그를 남긴다.
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<CommonResponse<Map<String, Object>>> handleHandlerMethodValidationException(
            HandlerMethodValidationException e,
            HttpServletRequest request
    ) {
        logApiError(BAD_REQUEST, HttpStatus.BAD_REQUEST, request, e);

        Map<String, String> parameterErrors = new LinkedHashMap<>();
        e.getAllValidationResults().forEach(result -> {
            String parameterName = result.getMethodParameter().getParameterName();
            if (parameterName == null || parameterName.isBlank()) {
                parameterName = "request";
            }

            String finalParameterName = parameterName;
            result.getResolvableErrors().stream()
                    .map(error -> error.getDefaultMessage())
                    .filter(errorMessage -> errorMessage != null && !errorMessage.isBlank())
                    .findFirst()
                    .ifPresent(errorMessage -> parameterErrors.putIfAbsent(finalParameterName, errorMessage));
        });

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(CommonResponse.failure(validationErrorBody(parameterErrors)));
    }

    // JSON 파싱 실패를 안전한 요청 본문 오류 응답으로 바꾸고 운영 로그를 남긴다.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<CommonResponse<Map<String, Object>>> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException e,
            HttpServletRequest request
    ) {
        logApiError(BAD_REQUEST, HttpStatus.BAD_REQUEST, request, e);

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

    // 잘못된 인자 예외를 BAD_REQUEST 응답으로 바꾸고 운영 로그를 남긴다.
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<CommonResponse<Map<String, Object>>> handleIllegalArgumentException(
            IllegalArgumentException e,
            HttpServletRequest request
    ) {
        logApiError(BAD_REQUEST, HttpStatus.BAD_REQUEST, request, e);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(CommonResponse.failure(errorBody(BAD_REQUEST, e.getMessage())));
    }

    // 공통 오류 응답 본문을 만든다.
    private Map<String, Object> errorBody(String code, String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("code", code);
        error.put("message", message);
        return error;
    }

    // 검증 오류를 message와 필드별 errors가 포함된 공통 본문으로 만든다.
    private Map<String, Object> validationErrorBody(Map<String, String> errors) {
        String message = errors.isEmpty()
                ? "요청 값이 올바르지 않습니다."
                : errors.values().iterator().next();

        Map<String, Object> error = errorBody(BAD_REQUEST, message);
        error.put("errors", errors);
        return error;
    }

    // JSON 파싱 예외에서 클라이언트에 보여줄 필드 오류를 추출한다.
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

    // Jackson 예외 경로를 점 표기 필드 경로로 변환한다.
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

    // JSON 파싱 실패 대상 타입을 확인한다.
    private Class<?> resolveTargetType(JsonMappingException e) {
        if (e instanceof InvalidFormatException invalidFormatException) {
            return invalidFormatException.getTargetType();
        }
        if (e instanceof MismatchedInputException mismatchedInputException) {
            return mismatchedInputException.getTargetType();
        }
        return null;
    }

    // JSON 파싱 실패 타입별 안전한 안내 메시지를 고른다.
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

    // 숫자 타입인지 확인한다.
    private boolean isNumberType(Class<?> targetType) {
        return Number.class.isAssignableFrom(targetType)
                || int.class.equals(targetType)
                || long.class.equals(targetType)
                || double.class.equals(targetType)
                || float.class.equals(targetType)
                || short.class.equals(targetType)
                || byte.class.equals(targetType);
    }

    // 민감한 요청 값 없이 운영자가 볼 수 있는 API 오류 맥락만 로그로 남긴다.
    private void logApiError(String code, HttpStatus status, HttpServletRequest request, Exception exception) {
        String path = request == null ? "unknown" : request.getRequestURI();
        String exceptionName = exception.getClass().getSimpleName();
        String message = "api.error code={} status={} path={} exception={}";

        if (status.is5xxServerError()) {
            log.error(message, code, status.value(), path, exceptionName, exception);
            return;
        }

        log.warn(message, code, status.value(), path, exceptionName);
    }

    private record JsonFieldError(String fieldPath, String message) {
    }
}
