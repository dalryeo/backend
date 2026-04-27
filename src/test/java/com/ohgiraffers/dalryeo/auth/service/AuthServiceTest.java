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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AppleOAuthValidator appleOAuthValidator;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OAuthClientRepository oAuthClientRepository;

    @Mock
    private AuthTokenRepository authTokenRepository;

    @Mock
    private RunningRecordRepository runningRecordRepository;

    @Mock
    private WeeklyTierRepository weeklyTierRepository;

    @Mock
    private WeeklyUserStatsRepository weeklyUserStatsRepository;

    @Mock
    private ProfileImageStorageService profileImageStorageService;

    @InjectMocks
    private AuthService authService;

    @Test
    void loginWithApple_createsNewUserAndStoresOAuthAndRefreshToken() {
        Long userId = 1L;
        String identityToken = "identity-token";
        String appleId = "apple-user-1";
        String accessToken = "access-token";
        String refreshToken = "refresh-token";
        Date refreshExpiry = Date.from(Instant.parse("2026-04-01T00:00:00Z"));
        User savedUser = userWithId(userId, UserStatus.NORMAL);

        when(appleOAuthValidator.validateAndExtractAppleId(identityToken)).thenReturn(appleId);
        when(oAuthClientRepository.findByProviderAndProviderId("APPLE", appleId)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(jwtTokenProvider.generateAccessToken(userId)).thenReturn(accessToken);
        when(jwtTokenProvider.generateRefreshToken(userId)).thenReturn(refreshToken);
        when(jwtTokenProvider.getExpiration(refreshToken)).thenReturn(refreshExpiry);
        when(authTokenRepository.findByUserId(userId)).thenReturn(Optional.empty());

        TokenResponse response = authService.loginWithApple(identityToken);

        assertThat(response.getAccessToken()).isEqualTo(accessToken);
        assertThat(response.getRefreshToken()).isEqualTo(refreshToken);
        assertThat(response.getIsNewUser()).isTrue();

        ArgumentCaptor<OAuthClient> oAuthCaptor = ArgumentCaptor.forClass(OAuthClient.class);
        verify(oAuthClientRepository).save(oAuthCaptor.capture());
        OAuthClient oAuthClient = oAuthCaptor.getValue();
        assertThat(oAuthClient.getUserId()).isEqualTo(userId);
        assertThat(oAuthClient.getProvider()).isEqualTo("APPLE");
        assertThat(oAuthClient.getProviderId()).isEqualTo(appleId);

        ArgumentCaptor<AuthToken> authTokenCaptor = ArgumentCaptor.forClass(AuthToken.class);
        verify(authTokenRepository).save(authTokenCaptor.capture());
        AuthToken authToken = authTokenCaptor.getValue();
        assertThat(authToken.getUserId()).isEqualTo(userId);
        assertThat(authToken.getRefreshTokenHash()).isEqualTo(sha256(refreshToken));
        assertThat(authToken.getExpiresAt())
                .isEqualTo(LocalDateTime.ofInstant(refreshExpiry.toInstant(), ZoneId.systemDefault()));
    }

    @Test
    void loginWithApple_reactivatesWithdrawnUser() {
        Long userId = 2L;
        String identityToken = "identity-token";
        String appleId = "apple-user-2";
        String accessToken = "access-token";
        String refreshToken = "refresh-token";
        Date refreshExpiry = Date.from(Instant.parse("2026-04-01T00:00:00Z"));
        User withdrawnUser = userWithId(userId, UserStatus.WITHDRAWN);
        OAuthClient oAuthClient = OAuthClient.builder()
                .userId(userId)
                .provider("APPLE")
                .providerId(appleId)
                .build();

        when(appleOAuthValidator.validateAndExtractAppleId(identityToken)).thenReturn(appleId);
        when(oAuthClientRepository.findByProviderAndProviderId("APPLE", appleId)).thenReturn(Optional.of(oAuthClient));
        when(userRepository.findById(userId)).thenReturn(Optional.of(withdrawnUser));
        when(userRepository.save(withdrawnUser)).thenReturn(withdrawnUser);
        when(jwtTokenProvider.generateAccessToken(userId)).thenReturn(accessToken);
        when(jwtTokenProvider.generateRefreshToken(userId)).thenReturn(refreshToken);
        when(jwtTokenProvider.getExpiration(refreshToken)).thenReturn(refreshExpiry);
        when(authTokenRepository.findByUserId(userId)).thenReturn(Optional.empty());

        TokenResponse response = authService.loginWithApple(identityToken);

        assertThat(response.getIsNewUser()).isTrue();
        assertThat(withdrawnUser.getStatus()).isEqualTo(UserStatus.NORMAL);
        verify(oAuthClientRepository, never()).save(any(OAuthClient.class));
    }

    @Test
    void refreshToken_rotatesRefreshTokenForActiveUser() {
        Long userId = 3L;
        String currentRefreshToken = "current-refresh-token";
        String newAccessToken = "new-access-token";
        String newRefreshToken = "new-refresh-token";
        Date newRefreshExpiry = Date.from(Instant.parse("2026-04-02T00:00:00Z"));
        RefreshTokenRequest request = refreshTokenRequest(currentRefreshToken);
        User user = userWithId(userId, UserStatus.NORMAL);
        AuthToken existingAuthToken = AuthToken.builder()
                .userId(userId)
                .refreshTokenHash(sha256(currentRefreshToken))
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();

        when(jwtTokenProvider.validateToken(currentRefreshToken)).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken(currentRefreshToken)).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(authTokenRepository.findByRefreshTokenHash(sha256(currentRefreshToken)))
                .thenReturn(Optional.of(existingAuthToken));
        when(jwtTokenProvider.generateAccessToken(userId)).thenReturn(newAccessToken);
        when(jwtTokenProvider.generateRefreshToken(userId)).thenReturn(newRefreshToken);
        when(jwtTokenProvider.getExpiration(newRefreshToken)).thenReturn(newRefreshExpiry);
        when(authTokenRepository.findByUserId(userId)).thenReturn(Optional.of(existingAuthToken));

        TokenResponse response = authService.refreshToken(request);

        assertThat(response.getAccessToken()).isEqualTo(newAccessToken);
        assertThat(response.getRefreshToken()).isEqualTo(newRefreshToken);
        assertThat(response.getIsNewUser()).isNull();

        ArgumentCaptor<AuthToken> authTokenCaptor = ArgumentCaptor.forClass(AuthToken.class);
        verify(authTokenRepository).save(authTokenCaptor.capture());
        AuthToken rotatedAuthToken = authTokenCaptor.getValue();
        assertThat(rotatedAuthToken.getRefreshTokenHash()).isEqualTo(sha256(newRefreshToken));
        assertThat(rotatedAuthToken.getExpiresAt())
                .isEqualTo(LocalDateTime.ofInstant(newRefreshExpiry.toInstant(), ZoneId.systemDefault()));
    }

    @Test
    void refreshToken_throwsWhenJwtValidationFails() {
        String refreshToken = "invalid-refresh-token";
        RefreshTokenRequest request = refreshTokenRequest(refreshToken);

        when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(false);

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.REFRESH_TOKEN_EXPIRED);

        verify(authTokenRepository, never()).findByRefreshTokenHash(any());
    }

    @Test
    void refreshToken_throwsUserExceptionWhenWithdrawnUserRefreshTokenWasDeleted() {
        Long userId = 5L;
        String refreshToken = "deleted-refresh-token";
        RefreshTokenRequest request = refreshTokenRequest(refreshToken);
        User withdrawnUser = userWithId(userId, UserStatus.WITHDRAWN);

        when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken(refreshToken)).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(withdrawnUser));

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.WITHDRAWN_USER);

        verify(authTokenRepository, never()).findByRefreshTokenHash(any());
    }

    @Test
    void logout_deletesRefreshTokenForExistingUser() {
        Long userId = 4L;
        User user = userWithId(userId, UserStatus.NORMAL);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        authService.logout(userId);

        verify(authTokenRepository).deleteByUserId(userId);
    }

    @Test
    void withdraw_softDeletesUserAndDeletesOwnedData() {
        Long userId = 5L;
        User user = userWithId(userId, UserStatus.NORMAL);
        ReflectionTestUtils.setField(user, "profileImage", "/profiles/custom/original.png");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        authService.withdraw(userId);

        verify(authTokenRepository).deleteByUserId(userId);
        verify(weeklyTierRepository).deleteByUserId(userId);
        verify(weeklyUserStatsRepository).deleteByUserId(userId);
        verify(runningRecordRepository).deleteByUserId(userId);
        verify(userRepository).save(user);
        verify(profileImageStorageService).deleteStoredProfileImage("/profiles/custom/original.png");
        assertThat(user.isWithdrawn()).isTrue();
        assertThat(user.getDeletedAt()).isNotNull();
    }

    private User userWithId(Long id, UserStatus status) {
        User user = User.builder()
                .status(status)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private RefreshTokenRequest refreshTokenRequest(String refreshToken) {
        RefreshTokenRequest request = new RefreshTokenRequest();
        ReflectionTestUtils.setField(request, "refreshToken", refreshToken);
        return request;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
