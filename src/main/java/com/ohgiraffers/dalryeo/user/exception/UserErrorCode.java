package com.ohgiraffers.dalryeo.user.exception;

import com.ohgiraffers.dalryeo.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {
    USER_NOT_FOUND("USER-001", "사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    WITHDRAWN_USER("USER-002", "탈퇴한 사용자입니다.", HttpStatus.FORBIDDEN),
    DUPLICATED_NICKNAME("USER-003", "이미 사용 중인 닉네임입니다.", HttpStatus.CONFLICT),
    OAUTH_USER_MAPPING_INVALID("USER-004", "OAuth 사용자 매핑이 올바르지 않습니다.", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
