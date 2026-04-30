package com.ohgiraffers.dalryeo.auth.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode {
    OAUTH_TOKEN_VERIFICATION_FAILED("AC-003", "OAuth 토큰 검증 실패"),
    REFRESH_TOKEN_EXPIRED("AC-006", "refreshToken 만료"),
    REFRESH_TOKEN_MISMATCH("AC-004", "refreshToken 불일치");

    private final String code;
    private final String message;
}
