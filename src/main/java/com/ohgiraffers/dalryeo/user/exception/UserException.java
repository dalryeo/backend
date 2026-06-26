package com.ohgiraffers.dalryeo.user.exception;

import com.ohgiraffers.dalryeo.common.exception.BusinessException;
import lombok.Getter;

@Getter
public class UserException extends BusinessException {

    public UserException(UserErrorCode errorCode) {
        super(errorCode);
    }

    public UserException(UserErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
