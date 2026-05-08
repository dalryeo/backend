package com.ohgiraffers.dalryeo.auth.controller;

import com.ohgiraffers.dalryeo.auth.dto.AppleOAuthRequest;
import com.ohgiraffers.dalryeo.auth.dto.RefreshTokenRequest;
import com.ohgiraffers.dalryeo.auth.dto.TokenResponse;
import com.ohgiraffers.dalryeo.auth.exception.AuthErrorCode;
import com.ohgiraffers.dalryeo.auth.exception.AuthException;
import com.ohgiraffers.dalryeo.auth.jwt.JwtTokenExtractor;
import com.ohgiraffers.dalryeo.auth.jwt.JwtTokenProvider;
import com.ohgiraffers.dalryeo.auth.service.AuthService;
import com.ohgiraffers.dalryeo.common.CommonResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtTokenExtractor jwtTokenExtractor;

    /**
     * Apple OAuth 로그인
     * POST /auth/oauth/apple
     */
    @PostMapping("/oauth/apple")
    public CommonResponse<TokenResponse> loginWithApple(@Valid @RequestBody AppleOAuthRequest request) {
        TokenResponse response = authService.loginWithApple(request.getIdentityToken());
        return CommonResponse.success(response);
    }

    /**
     * Refresh Token 재발급
     * POST /auth/token/refresh
     */
    @PostMapping("/token/refresh")
    public CommonResponse<TokenResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        TokenResponse response = authService.refreshToken(request);
        return CommonResponse.success(response);
    }

    /**
     * 로그아웃
     * POST /auth/logout
     */
    @PostMapping("/logout")
    public CommonResponse<Void> logout(HttpServletRequest httpRequest) {
        Long userId = extractUserIdFromRequest(httpRequest);
        authService.logout(userId);
        return CommonResponse.success();
    }

    /**
     * 회원 탈퇴
     * DELETE /auth/withdraw
     */
    @DeleteMapping("/withdraw")
    public CommonResponse<Void> withdraw(HttpServletRequest httpRequest) {
        Long userId = extractUserIdFromRequest(httpRequest);
        authService.withdraw(userId);
        return CommonResponse.success();
    }

    /**
     * 요청에서 AccessToken을 추출하여 사용자 ID를 반환
     */
    private Long extractUserIdFromRequest(HttpServletRequest request) {
        String token = jwtTokenExtractor.extractToken(request);
        if (token == null || !jwtTokenProvider.validateAccessToken(token)) {
            throw new AuthException(AuthErrorCode.REFRESH_TOKEN_EXPIRED);
        }
        return jwtTokenProvider.getUserIdFromToken(token);
    }
}
