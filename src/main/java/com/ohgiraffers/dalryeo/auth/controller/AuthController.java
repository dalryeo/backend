package com.ohgiraffers.dalryeo.auth.controller;

import com.ohgiraffers.dalryeo.auth.annotation.LoginUser;
import com.ohgiraffers.dalryeo.auth.dto.AppleOAuthRequest;
import com.ohgiraffers.dalryeo.auth.dto.RefreshTokenRequest;
import com.ohgiraffers.dalryeo.auth.dto.TokenResponse;
import com.ohgiraffers.dalryeo.auth.service.AuthService;
import com.ohgiraffers.dalryeo.common.CommonResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

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
     * POST /auth/refresh
     */
    @PostMapping({"/token/refresh", "/refresh"})
    public CommonResponse<TokenResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        TokenResponse response = authService.refreshToken(request);
        return CommonResponse.success(response);
    }

    /**
     * 로그아웃
     * POST /auth/logout
     */
    @PostMapping("/logout")
    public CommonResponse<Void> logout(@LoginUser Long userId) {
        authService.logout(userId);
        return CommonResponse.success();
    }

    /**
     * 회원 탈퇴
     * DELETE /auth/withdraw
     */
    @DeleteMapping("/withdraw")
    public CommonResponse<Void> withdraw(@LoginUser Long userId) {
        authService.withdraw(userId);
        return CommonResponse.success();
    }
}
