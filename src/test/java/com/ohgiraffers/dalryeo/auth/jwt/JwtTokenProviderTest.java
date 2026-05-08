package com.ohgiraffers.dalryeo.auth.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

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
    void generatedAccessAndRefreshTokensAreValidatedOnlyForTheirOwnPurpose() {
        Long userId = 10L;

        String accessToken = jwtTokenProvider.generateAccessToken(userId);
        String refreshToken = jwtTokenProvider.generateRefreshToken(userId);

        assertThat(jwtTokenProvider.validateToken(accessToken)).isTrue();
        assertThat(jwtTokenProvider.validateToken(refreshToken)).isTrue();
        assertThat(jwtTokenProvider.validateAccessToken(accessToken)).isTrue();
        assertThat(jwtTokenProvider.validateAccessToken(refreshToken)).isFalse();
        assertThat(jwtTokenProvider.validateRefreshToken(refreshToken)).isTrue();
        assertThat(jwtTokenProvider.validateRefreshToken(accessToken)).isFalse();
        assertThat(jwtTokenProvider.getUserIdFromToken(accessToken)).isEqualTo(userId);
        assertThat(jwtTokenProvider.getUserIdFromToken(refreshToken)).isEqualTo(userId);
    }

    @Test
    void legacyTokenWithoutTokenUseIsRejectedByPurposeValidation() {
        String legacyToken = Jwts.builder()
                .subject("20")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(secretKey())
                .compact();

        assertThat(jwtTokenProvider.validateToken(legacyToken)).isTrue();
        assertThat(jwtTokenProvider.validateAccessToken(legacyToken)).isFalse();
        assertThat(jwtTokenProvider.validateRefreshToken(legacyToken)).isFalse();
        assertThat(jwtTokenProvider.getUserIdFromToken(legacyToken)).isEqualTo(20L);
    }

    @Test
    void malformedTokenIsRejectedByEveryValidationMethod() {
        String malformedToken = "not-a-jwt";

        assertThat(jwtTokenProvider.validateToken(malformedToken)).isFalse();
        assertThat(jwtTokenProvider.validateAccessToken(malformedToken)).isFalse();
        assertThat(jwtTokenProvider.validateRefreshToken(malformedToken)).isFalse();
    }

    private SecretKey secretKey() {
        return Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }
}
