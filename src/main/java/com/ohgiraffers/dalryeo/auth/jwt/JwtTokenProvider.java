package com.ohgiraffers.dalryeo.auth.jwt;

import com.ohgiraffers.dalryeo.auth.exception.AuthErrorCode;
import com.ohgiraffers.dalryeo.auth.exception.AuthException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private static final String TOKEN_USE_CLAIM = "token_use";
    private static final String ACCESS_TOKEN_USE = "access";
    private static final String REFRESH_TOKEN_USE = "refresh";

    private final SecretKey secretKey;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;

    /**
     * JWT 서명 키와 토큰 만료 시간을 준비한다.
     */
    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpiration,
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    /**
     * 보호 API 호출에 사용할 Access Token을 생성한다.
     */
    public String generateAccessToken(Long userId) {
        return generateToken(userId, accessTokenExpiration, ACCESS_TOKEN_USE);
    }

    /**
     * 토큰 재발급에 사용할 Refresh Token을 생성한다.
     */
    public String generateRefreshToken(Long userId) {
        return generateToken(userId, refreshTokenExpiration, REFRESH_TOKEN_USE);
    }

    /**
     * userId, 만료 시간, 토큰 용도를 담아 JWT 문자열을 만든다.
     */
    private String generateToken(Long userId, long expiration, String tokenUse) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(TOKEN_USE_CLAIM, tokenUse)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    /**
     * Access Token을 검증하고 subject에서 userId를 꺼낸다.
     */
    public Long getUserIdFromAccessToken(String token) {
        return extractUserIdFromToken(token, ACCESS_TOKEN_USE, AuthErrorCode.ACCESS_TOKEN_INVALID);
    }

    /**
     * Refresh Token을 검증하고 subject에서 userId를 꺼낸다.
     */
    public Long getUserIdFromRefreshToken(String token) {
        return extractUserIdFromToken(token, REFRESH_TOKEN_USE, AuthErrorCode.REFRESH_TOKEN_INVALID);
    }

    /**
     * 토큰의 만료 시각을 조회한다.
     */
    public Date getExpiration(String token) {
        Claims claims = parseClaims(token);
        return claims.getExpiration();
    }

    /**
     * 토큰 서명, 만료 시간, 용도를 한 번에 확인하고 userId를 반환한다.
     */
    private Long extractUserIdFromToken(String token, String expectedTokenUse, AuthErrorCode errorCode) {
        try {
            Claims claims = parseClaims(token);
            if (!expectedTokenUse.equals(claims.get(TOKEN_USE_CLAIM, String.class))) {
                throw new AuthException(errorCode);
            }
            return extractUserId(claims);
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthException(errorCode);
        }
    }

    /**
     * Claims의 subject 값을 Long userId로 변환한다.
     */
    private Long extractUserId(Claims claims) {
        return Long.parseLong(claims.getSubject());
    }

    /**
     * JWT를 파싱하면서 서명과 만료 시간을 검증한다.
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
