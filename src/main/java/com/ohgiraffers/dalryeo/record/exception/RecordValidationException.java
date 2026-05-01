package com.ohgiraffers.dalryeo.record.exception;

import com.ohgiraffers.dalryeo.common.exception.BusinessException;
import lombok.Getter;

@Getter
public class RecordValidationException extends BusinessException {

    public RecordValidationException(RecordValidationErrorCode errorCode) {
        super(errorCode);
    }
}
