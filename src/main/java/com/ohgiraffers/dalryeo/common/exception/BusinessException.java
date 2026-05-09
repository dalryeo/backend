package com.ohgiraffers.dalryeo.common.exception;

public abstract class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    protected BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.getMessage());
    }

    protected BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    protected BusinessException(ErrorCode errorCode, Throwable cause) {
        this(errorCode, errorCode.getMessage(), cause);
    }

    protected BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
