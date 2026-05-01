package com.ohgiraffers.dalryeo.auth.exception;

import com.ohgiraffers.dalryeo.common.exception.BusinessException;
import lombok.Getter;

@Getter
public class AuthException extends BusinessException {

    public AuthException(AuthErrorCode errorCode) {
        super(errorCode);
    }

    public AuthException(AuthErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
