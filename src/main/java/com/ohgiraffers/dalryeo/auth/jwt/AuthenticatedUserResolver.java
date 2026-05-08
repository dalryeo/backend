package com.ohgiraffers.dalryeo.auth.jwt;

import com.ohgiraffers.dalryeo.auth.exception.AuthErrorCode;
import com.ohgiraffers.dalryeo.auth.exception.AuthException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthenticatedUserResolver {

    private final JwtTokenExtractor jwtTokenExtractor;
    private final JwtTokenProvider jwtTokenProvider;

    public Long resolveUserId(HttpServletRequest request) {
        String token = jwtTokenExtractor.extractToken(request);
        if (token == null || !jwtTokenProvider.validateAccessToken(token)) {
            throw new AuthException(AuthErrorCode.ACCESS_TOKEN_INVALID);
        }
        return jwtTokenProvider.getUserIdFromToken(token);
    }
}
