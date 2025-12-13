package com.ohgiraffers.dalryeo.auth.service;

import com.ohgiraffers.dalryeo.auth.dto.RefreshTokenRequest;
import com.ohgiraffers.dalryeo.auth.dto.TokenResponse;
import com.ohgiraffers.dalryeo.auth.entity.User;
import com.ohgiraffers.dalryeo.auth.exception.AuthErrorCode;
import com.ohgiraffers.dalryeo.auth.exception.AuthException;
import com.ohgiraffers.dalryeo.auth.jwt.JwtTokenProvider;
import com.ohgiraffers.dalryeo.auth.oauth.AppleOAuthValidator;
import com.ohgiraffers.dalryeo.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final AppleOAuthValidator appleOAuthValidator;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    /**
     * Apple OAuth 로그인 처리
     */
    public TokenResponse loginWithApple(String identityToken) {
        // Apple identityToken 검증 및 Apple ID 추출
        String appleId;
        try {
            appleId = appleOAuthValidator.validateAndExtractAppleId(identityToken);
        } catch (Exception e) {
            throw new AuthException(AuthErrorCode.OAUTH_TOKEN_VERIFICATION_FAILED);
        }

        // 기존 사용자 조회
        User user = userRepository.findByAppleId(appleId)
                .orElse(null);

        boolean isNewUser = false;

        if (user == null) {
            // 신규 사용자 생성
            user = User.builder()
                    .appleId(appleId)
                    .build();
            user = userRepository.save(user);
            isNewUser = true;
        } else {
            // 탈퇴한 사용자 체크
            if (user.isWithdrawn()) {
                throw new AuthException(AuthErrorCode.WITHDRAWN_USER);
            }
        }

        // JWT 토큰 생성
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        // Refresh Token 저장
        user.updateRefreshToken(refreshToken);
        userRepository.save(user);

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .isNewUser(isNewUser)
                .build();
    }

    /**
     * Refresh Token으로 새 토큰 발급
     */
    public TokenResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();

        // Refresh Token 유효성 검증
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new AuthException(AuthErrorCode.REFRESH_TOKEN_EXPIRED);
        }

        // DB에서 사용자 조회
        User user = userRepository.findByRefreshToken(refreshToken)
                .orElseThrow(() -> new AuthException(AuthErrorCode.REFRESH_TOKEN_MISMATCH));

        // 탈퇴한 사용자 체크
        if (user.isWithdrawn()) {
            throw new AuthException(AuthErrorCode.WITHDRAWN_USER);
        }

        // 새 토큰 생성
        String newAccessToken = jwtTokenProvider.generateAccessToken(user.getId());
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        // 새 Refresh Token 저장
        user.updateRefreshToken(newRefreshToken);
        userRepository.save(user);

        return TokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .build();
    }

    /**
     * 로그아웃 처리
     */
    public void logout(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.REFRESH_TOKEN_MISMATCH));

        // Refresh Token 제거
        user.clearRefreshToken();
        userRepository.save(user);
    }

    /**
     * 회원 탈퇴 처리
     */
    public void withdraw(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.REFRESH_TOKEN_MISMATCH));

        // 탈퇴 처리
        user.withdraw();
        userRepository.save(user);
    }
}

