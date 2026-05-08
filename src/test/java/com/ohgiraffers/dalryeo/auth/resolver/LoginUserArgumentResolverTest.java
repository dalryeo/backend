package com.ohgiraffers.dalryeo.auth.resolver;

import com.ohgiraffers.dalryeo.auth.annotation.LoginUser;
import com.ohgiraffers.dalryeo.auth.jwt.AuthenticatedUserResolver;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoginUserArgumentResolverTest {

    private final AuthenticatedUserResolver authenticatedUserResolver = mock(AuthenticatedUserResolver.class);
    private final LoginUserArgumentResolver loginUserArgumentResolver =
            new LoginUserArgumentResolver(authenticatedUserResolver);

    @Test
    void supportsParameter_returnsTrueForLoginUserLongParameter() throws Exception {
        MethodParameter parameter = methodParameter("loginUserLong", 0);

        assertThat(loginUserArgumentResolver.supportsParameter(parameter)).isTrue();
    }

    @Test
    void supportsParameter_returnsFalseWhenAnnotationIsMissing() throws Exception {
        MethodParameter parameter = methodParameter("plainLong", 0);

        assertThat(loginUserArgumentResolver.supportsParameter(parameter)).isFalse();
    }

    @Test
    void supportsParameter_returnsFalseWhenParameterTypeIsNotLong() throws Exception {
        MethodParameter parameter = methodParameter("loginUserString", 0);

        assertThat(loginUserArgumentResolver.supportsParameter(parameter)).isFalse();
    }

    @Test
    void resolveArgument_returnsAuthenticatedUserId() throws Exception {
        MethodParameter parameter = methodParameter("loginUserLong", 0);
        MockHttpServletRequest request = new MockHttpServletRequest();
        NativeWebRequest webRequest = new ServletWebRequest(request);

        when(authenticatedUserResolver.resolveUserId(request)).thenReturn(1L);

        Object resolved = loginUserArgumentResolver.resolveArgument(parameter, null, webRequest, null);

        assertThat(resolved).isEqualTo(1L);
    }

    private MethodParameter methodParameter(String methodName, int parameterIndex) throws NoSuchMethodException {
        Method method = TestController.class.getDeclaredMethod(methodName, parameterType(methodName));
        return new MethodParameter(method, parameterIndex);
    }

    private Class<?> parameterType(String methodName) {
        if ("loginUserString".equals(methodName)) {
            return String.class;
        }
        return Long.class;
    }

    private static class TestController {

        void loginUserLong(@LoginUser Long userId) {
        }

        void plainLong(Long userId) {
        }

        void loginUserString(@LoginUser String userId) {
        }
    }
}
