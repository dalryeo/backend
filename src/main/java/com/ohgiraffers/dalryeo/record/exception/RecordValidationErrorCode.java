package com.ohgiraffers.dalryeo.record.exception;

import com.ohgiraffers.dalryeo.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum RecordValidationErrorCode implements ErrorCode {
    INVALID_TIME_RANGE(HttpStatus.BAD_REQUEST, "RC-001", "종료 시간은 시작 시간보다 뒤여야 합니다."),
    INVALID_DURATION(HttpStatus.BAD_REQUEST, "RC-002", "기록 시간과 시작/종료 시각이 일치하지 않습니다."),
    INVALID_AVERAGE_PACE(HttpStatus.BAD_REQUEST, "RC-003", "거리, 시간, 평균 페이스가 일치하지 않습니다."),
    FUTURE_RECORD_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "RC-004", "미래 시각의 기록은 저장할 수 없습니다."),
    INVALID_AVERAGE_HEART_RATE(HttpStatus.BAD_REQUEST, "RC-005", "평균 심박수는 0 또는 30 이상 240 이하여야 합니다."),
    INVALID_CALORIES(HttpStatus.BAD_REQUEST, "RC-006", "칼로리는 0 또는 1 이상 10000 이하여야 합니다."),
    INVALID_PLATFORM(HttpStatus.BAD_REQUEST, "RC-007", "지원하지 않는 플랫폼입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
