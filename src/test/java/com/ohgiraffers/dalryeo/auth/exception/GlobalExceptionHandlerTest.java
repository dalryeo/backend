package com.ohgiraffers.dalryeo.auth.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.ohgiraffers.dalryeo.common.CommonResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private static final String INVALID_REQUEST_BODY_MESSAGE = "요청 본문 형식이 올바르지 않습니다.";
    private static final String INVALID_OFFSET_DATE_TIME_MESSAGE =
            "시간 값은 timezone offset을 포함해야 합니다. 예: 2026-04-14T12:13:09+09:00";
    private static final String INVALID_NUMBER_MESSAGE = "숫자 형식으로 입력해야 합니다.";

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleHttpMessageNotReadableException_returnsFieldErrorWhenOffsetDateTimeFormatIsInvalid() {
        InvalidFormatException cause = invalidFormatException("startAt", "2026-03-31T07:00:00", OffsetDateTime.class);

        ResponseEntity<CommonResponse<Map<String, Object>>> response =
                handler.handleHttpMessageNotReadableException(messageNotReadableException(cause));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, Object> data = response.getBody().getData();
        assertThat(data)
                .containsEntry("code", "BAD_REQUEST")
                .containsEntry("message", INVALID_REQUEST_BODY_MESSAGE);
        assertThat(errors(data)).containsEntry("startAt", INVALID_OFFSET_DATE_TIME_MESSAGE);
    }

    @Test
    void handleHttpMessageNotReadableException_returnsFieldErrorWhenNumberFormatIsInvalid() {
        InvalidFormatException cause = invalidFormatException("distanceKm", "fast", Double.class);

        ResponseEntity<CommonResponse<Map<String, Object>>> response =
                handler.handleHttpMessageNotReadableException(messageNotReadableException(cause));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, Object> data = response.getBody().getData();
        assertThat(data)
                .containsEntry("code", "BAD_REQUEST")
                .containsEntry("message", INVALID_REQUEST_BODY_MESSAGE);
        assertThat(errors(data)).containsEntry("distanceKm", INVALID_NUMBER_MESSAGE);
    }

    @Test
    void handleHttpMessageNotReadableException_doesNotReturnFieldErrorWhenFieldCannotBeResolved() {
        ResponseEntity<CommonResponse<Map<String, Object>>> response =
                handler.handleHttpMessageNotReadableException(
                        messageNotReadableException(new RuntimeException("malformed json"))
                );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, Object> data = response.getBody().getData();
        assertThat(data)
                .containsEntry("code", "BAD_REQUEST")
                .containsEntry("message", INVALID_REQUEST_BODY_MESSAGE)
                .doesNotContainKey("errors");
    }

    private InvalidFormatException invalidFormatException(String fieldName, String value, Class<?> targetType) {
        InvalidFormatException exception = InvalidFormatException.from(null, "invalid value", value, targetType);
        exception.prependPath(RequestFixture.class, fieldName);
        return exception;
    }

    private HttpMessageNotReadableException messageNotReadableException(Throwable cause) {
        return new HttpMessageNotReadableException(
                "parse error",
                cause,
                new MockHttpInputMessage(new byte[0])
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> errors(Map<String, Object> data) {
        return (Map<String, String>) data.get("errors");
    }

    private static class RequestFixture {
    }
}
