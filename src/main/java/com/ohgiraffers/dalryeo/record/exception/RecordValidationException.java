package com.ohgiraffers.dalryeo.record.exception;

import lombok.Getter;

@Getter
public class RecordValidationException extends RuntimeException {

    private final RecordValidationErrorCode errorCode;

    public RecordValidationException(RecordValidationErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
