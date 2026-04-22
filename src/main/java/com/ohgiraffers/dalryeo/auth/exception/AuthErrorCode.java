package com.ohgiraffers.dalryeo.auth.exception;

import com.ohgiraffers.dalryeo.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {
    OAUTH_TOKEN_VERIFICATION_FAILED(HttpStatus.UNAUTHORIZED, "AC-003", "OAuth 토큰 검증 실패"),
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "AC-006", "refreshToken 만료"),
    REFRESH_TOKEN_MISMATCH(HttpStatus.UNAUTHORIZED, "AC-004", "refreshToken 불일치"),
    WITHDRAWN_USER(HttpStatus.UNAUTHORIZED, "AC-006", "탈퇴된 사용자");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
