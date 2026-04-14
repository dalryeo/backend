package com.ohgiraffers.dalryeo.auth.service;

import com.ohgiraffers.dalryeo.auth.dto.RefreshTokenRequest;
import com.ohgiraffers.dalryeo.auth.dto.TokenResponse;
import com.ohgiraffers.dalryeo.auth.entity.AuthToken;
import com.ohgiraffers.dalryeo.auth.entity.OAuthClient;
import com.ohgiraffers.dalryeo.auth.entity.User;
import com.ohgiraffers.dalryeo.auth.entity.UserStatus;
import com.ohgiraffers.dalryeo.auth.exception.AuthErrorCode;
import com.ohgiraffers.dalryeo.auth.exception.AuthException;
import com.ohgiraffers.dalryeo.auth.jwt.JwtTokenProvider;
import com.ohgiraffers.dalryeo.auth.oauth.AppleOAuthValidator;
import com.ohgiraffers.dalryeo.auth.repository.AuthTokenRepository;
import com.ohgiraffers.dalryeo.auth.repository.OAuthClientRepository;
import com.ohgiraffers.dalryeo.auth.repository.UserRepository;
import com.ohgiraffers.dalryeo.onboarding.service.ProfileImageStorageService;
import com.ohgiraffers.dalryeo.record.repository.RunningRecordRepository;
import com.ohgiraffers.dalryeo.record.repository.WeeklyUserStatsRepository;
import com.ohgiraffers.dalryeo.user.exception.UserErrorCode;
import com.ohgiraffers.dalryeo.user.exception.UserException;
import com.ohgiraffers.dalryeo.weeklytier.repository.WeeklyTierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private static final String APPLE_PROVIDER = "APPLE";

    private final AppleOAuthValidator appleOAuthValidator;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final OAuthClientRepository oAuthClientRepository;
    private final AuthTokenRepository authTokenRepository;
    private final RunningRecordRepository runningRecordRepository;
    private final WeeklyUserStatsRepository weeklyUserStatsRepository;
    private final WeeklyTierRepository weeklyTierRepository;
    private final ProfileImageStorageService profileImageStorageService;

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

        boolean isNewUser = false;
        OAuthClient oAuthClient = oAuthClientRepository.findByProviderAndProviderId(APPLE_PROVIDER, appleId)
                .orElse(null);
        User user;

        if (oAuthClient == null) {
            user = User.builder()
                    .status(UserStatus.NORMAL)
                    .build();
            user = userRepository.save(user);
            oAuthClientRepository.save(OAuthClient.builder()
                    .userId(user.getId())
                    .provider(APPLE_PROVIDER)
                    .providerId(appleId)
                    .build());
            isNewUser = true;
        } else {
            user = userRepository.findById(oAuthClient.getUserId())
                    .orElseThrow(() -> new UserException(UserErrorCode.OAUTH_USER_MAPPING_INVALID));
            if (user.isWithdrawn()) {
                user.reactivate();
                user = userRepository.save(user);
                isNewUser = true;
            }
        }

        // JWT 토큰 생성
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        saveRefreshToken(user.getId(), refreshToken);

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

        String refreshTokenHash = hashRefreshToken(refreshToken);
        AuthToken authToken = authTokenRepository.findByRefreshTokenHash(refreshTokenHash)
                .orElseThrow(() -> new AuthException(AuthErrorCode.REFRESH_TOKEN_MISMATCH));
        if (authToken.isExpired(LocalDateTime.now())) {
            throw new AuthException(AuthErrorCode.REFRESH_TOKEN_EXPIRED);
        }

        User user = userRepository.findById(authToken.getUserId())
                .orElseThrow(() -> new AuthException(AuthErrorCode.REFRESH_TOKEN_MISMATCH));

        if (user.isWithdrawn()) {
            throw new AuthException(AuthErrorCode.WITHDRAWN_USER);
        }

        String newAccessToken = jwtTokenProvider.generateAccessToken(user.getId());
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        saveRefreshToken(user.getId(), newRefreshToken);

        return TokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .build();
    }

    /**
     * 로그아웃 처리
     */
    public void logout(Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.REFRESH_TOKEN_MISMATCH));
        authTokenRepository.deleteByUserId(userId);
    }

    /**
     * 회원 탈퇴 처리
     */
    public void withdraw(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.REFRESH_TOKEN_MISMATCH));

        String previousProfileImage = user.getProfileImage();
        authTokenRepository.deleteByUserId(userId);
        weeklyTierRepository.deleteByUserId(userId);
        weeklyUserStatsRepository.deleteByUserId(userId);
        runningRecordRepository.deleteByUserId(userId);
        user.withdraw();
        userRepository.save(user);
        profileImageStorageService.deleteStoredProfileImage(previousProfileImage);
    }

    private void saveRefreshToken(Long userId, String refreshToken) {
        String refreshTokenHash = hashRefreshToken(refreshToken);
        LocalDateTime expiresAt = LocalDateTime.ofInstant(
                jwtTokenProvider.getExpiration(refreshToken).toInstant(),
                ZoneId.systemDefault()
        );

        AuthToken authToken = authTokenRepository.findByUserId(userId)
                .orElseGet(() -> AuthToken.builder()
                        .userId(userId)
                        .refreshTokenHash(refreshTokenHash)
                        .expiresAt(expiresAt)
                        .build());

        authToken.rotate(refreshTokenHash, expiresAt);
        authTokenRepository.save(authToken);
    }

    private String hashRefreshToken(String refreshToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashedBytes = digest.digest(refreshToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashedBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available.", e);
        }
    }
}
