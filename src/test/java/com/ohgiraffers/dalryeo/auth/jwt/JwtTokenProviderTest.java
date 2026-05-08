package com.ohgiraffers.dalryeo.auth.jwt;

import com.ohgiraffers.dalryeo.auth.exception.AuthErrorCode;
import com.ohgiraffers.dalryeo.auth.exception.AuthException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private static final String SECRET = "12345678901234567890123456789012";
    private static final long ACCESS_TOKEN_EXPIRATION = 3_600_000L;
    private static final long REFRESH_TOKEN_EXPIRATION = 604_800_000L;

    private final JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(
            SECRET,
            ACCESS_TOKEN_EXPIRATION,
            REFRESH_TOKEN_EXPIRATION
    );

    @Test
    void generatedAccessAndRefreshTokensExtractUserIdOnlyForTheirOwnPurpose() {
        Long userId = 10L;

        String accessToken = jwtTokenProvider.generateAccessToken(userId);
        String refreshToken = jwtTokenProvider.generateRefreshToken(userId);

        assertThat(jwtTokenProvider.getUserIdFromAccessToken(accessToken)).isEqualTo(userId);
        assertThat(jwtTokenProvider.getUserIdFromRefreshToken(refreshToken)).isEqualTo(userId);
        assertTokenError(
                () -> jwtTokenProvider.getUserIdFromAccessToken(refreshToken),
                AuthErrorCode.ACCESS_TOKEN_INVALID
        );
        assertTokenError(
                () -> jwtTokenProvider.getUserIdFromRefreshToken(accessToken),
                AuthErrorCode.REFRESH_TOKEN_EXPIRED
        );
    }

    @Test
    void legacyTokenWithoutTokenUseIsRejectedByPurposeExtraction() {
        String legacyToken = Jwts.builder()
                .subject("20")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(secretKey())
                .compact();

        assertTokenError(
                () -> jwtTokenProvider.getUserIdFromAccessToken(legacyToken),
                AuthErrorCode.ACCESS_TOKEN_INVALID
        );
        assertTokenError(
                () -> jwtTokenProvider.getUserIdFromRefreshToken(legacyToken),
                AuthErrorCode.REFRESH_TOKEN_EXPIRED
        );
    }

    @Test
    void malformedTokenIsRejectedByPurposeExtraction() {
        String malformedToken = "not-a-jwt";

        assertTokenError(
                () -> jwtTokenProvider.getUserIdFromAccessToken(malformedToken),
                AuthErrorCode.ACCESS_TOKEN_INVALID
        );
        assertTokenError(
                () -> jwtTokenProvider.getUserIdFromRefreshToken(malformedToken),
                AuthErrorCode.REFRESH_TOKEN_EXPIRED
        );
    }

    @Test
    void tokenWithNonNumericSubjectIsRejectedByPurposeExtraction() {
        String invalidSubjectAccessToken = Jwts.builder()
                .subject("not-a-number")
                .claim("token_use", "access")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(secretKey())
                .compact();

        assertTokenError(
                () -> jwtTokenProvider.getUserIdFromAccessToken(invalidSubjectAccessToken),
                AuthErrorCode.ACCESS_TOKEN_INVALID
        );
    }

    private SecretKey secretKey() {
        return Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    private void assertTokenError(Runnable tokenAction, AuthErrorCode expectedErrorCode) {
        assertThatThrownBy(tokenAction::run)
                .isInstanceOf(AuthException.class)
                .extracting("errorCode")
                .isEqualTo(expectedErrorCode);
    }
}
