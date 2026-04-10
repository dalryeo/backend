package com.ohgiraffers.dalryeo.record.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RecordValidationErrorCode {
    INVALID_TIME_RANGE("RC-001", "종료 시간은 시작 시간보다 뒤여야 합니다."),
    INVALID_DURATION("RC-002", "기록 시간과 시작/종료 시각이 일치하지 않습니다."),
    INVALID_AVERAGE_PACE("RC-003", "거리, 시간, 평균 페이스가 일치하지 않습니다."),
    FUTURE_RECORD_NOT_ALLOWED("RC-004", "미래 시각의 기록은 저장할 수 없습니다.");

    private final String code;
    private final String message;
}
