package com.ohgiraffers.dalryeo.auth.jwt;

import com.ohgiraffers.dalryeo.auth.exception.AuthErrorCode;
import com.ohgiraffers.dalryeo.auth.exception.AuthException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticatedUserResolverTest {

    @Mock
    private JwtTokenExtractor jwtTokenExtractor;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthenticatedUserResolver authenticatedUserResolver;

    @Test
    void resolveUserId_returnsUserIdWhenAccessTokenIsValid() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        String accessToken = "access-token";

        when(jwtTokenExtractor.extractToken(request)).thenReturn(accessToken);
        when(jwtTokenProvider.getUserIdFromAccessToken(accessToken)).thenReturn(1L);

        Long userId = authenticatedUserResolver.resolveUserId(request);

        assertThat(userId).isEqualTo(1L);
    }

    @Test
    void resolveUserId_throwsAccessTokenErrorWhenTokenIsMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        when(jwtTokenExtractor.extractToken(request)).thenReturn(null);

        assertThatThrownBy(() -> authenticatedUserResolver.resolveUserId(request))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.ACCESS_TOKEN_INVALID);

        verify(jwtTokenProvider, never()).getUserIdFromAccessToken(null);
    }

    @Test
    void resolveUserId_throwsAccessTokenErrorWhenTokenIsInvalid() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        String refreshToken = "refresh-token";

        when(jwtTokenExtractor.extractToken(request)).thenReturn(refreshToken);
        when(jwtTokenProvider.getUserIdFromAccessToken(refreshToken))
                .thenThrow(new AuthException(AuthErrorCode.ACCESS_TOKEN_INVALID));

        assertThatThrownBy(() -> authenticatedUserResolver.resolveUserId(request))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.ACCESS_TOKEN_INVALID);
    }
}
