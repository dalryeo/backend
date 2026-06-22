# JWT Token Purpose Separation

- Status: Active
- Audience: Engineers, Codex
- Source of Truth: Yes
- Last Reviewed: 2026-06-16

## 결정

Access Token과 Refresh Token은 JWT `token_use` claim으로 용도를 분리한다.

```text
Access Token  -> token_use=access
Refresh Token -> token_use=refresh
```

보호 API는 access token만 허용하고, token 재발급 API는 refresh token만 허용한다.

## 배경

Access Token과 Refresh Token은 모두 JWT 형식이지만 사용 목적이 다르다. 서명과 만료만 검증하면 refresh token이 보호 API Bearer token처럼 사용되거나, access token이 refresh endpoint에 제출될 수 있다.

토큰 형식이 같다는 이유로 같은 검증 메서드를 공유하면 API 경계가 흐려지고, 인증 실패 응답도 일관되게 관리하기 어렵다.

## 선택한 방식

`JwtTokenProvider`는 token을 생성할 때 `token_use` claim을 넣는다. 사용자 ID 추출도 용도별 메서드로 나눈다.

- `getUserIdFromAccessToken(...)`: access token만 허용한다.
- `getUserIdFromRefreshToken(...)`: refresh token만 허용한다.
- `getRefreshTokenExpiration(...)`: refresh token 만료 시각 조회에만 사용한다.

보호 API는 Controller에서 직접 JWT를 파싱하지 않는다. `@LoginUser`와 `LoginUserArgumentResolver`, `AuthenticatedUserResolver` 흐름을 사용해 access token에서 사용자 ID를 주입받는다.

Refresh Token 원문은 DB에 저장하지 않고 SHA-256 hash로 저장한다. 재발급에 성공하면 새 token pair를 발급하고 저장된 refresh token hash를 회전한다.

## 오류 계약

토큰 용도 검증 실패는 API 종류에 맞는 인증 오류로 응답한다.

| 상황 | 오류 |
| --- | --- |
| 보호 API에서 access token 검증 실패 | `AC-007 accessToken 유효하지 않음` |
| refresh API에서 refresh token 검증 실패 | `AC-006 refreshToken 유효하지 않음` |

용도 claim이 없는 legacy token은 용도 검증을 통과하지 못한다.

## 의존성 판단

Nimbus JOSE JWT는 Apple OAuth 검증에 사용한다. 당시 보안 개선 작업에서는 Apple OAuth 호환성을 유지하기 위해 기존 `9.37.x` 라인에서 패치 버전으로 올렸다.

의존성 업그레이드는 인증 흐름과 직접 연결되므로, Apple OAuth 검증 테스트와 JWT 검증 테스트를 함께 확인한다.

## 결과

토큰 용도는 API 경계에 포함된다. 새 인증 흐름을 만들 때는 범용 JWT 검증 메서드를 추가하지 않고, 호출 목적에 맞는 검증 메서드를 둔다.

Controller에서 사용자 ID가 필요하면 token을 직접 파싱하지 말고 `@LoginUser` 흐름을 사용한다.

## 구현 앵커

- `src/main/java/com/ohgiraffers/dalryeo/auth/jwt/JwtTokenProvider.java`
- `src/main/java/com/ohgiraffers/dalryeo/auth/jwt/AuthenticatedUserResolver.java`
- `src/main/java/com/ohgiraffers/dalryeo/auth/resolver/LoginUserArgumentResolver.java`
- `src/main/java/com/ohgiraffers/dalryeo/auth/service/AuthService.java`
- `src/test/java/com/ohgiraffers/dalryeo/auth/jwt/JwtTokenProviderTest.java`
- `src/test/java/com/ohgiraffers/dalryeo/auth/service/AuthServiceTest.java`
- `src/test/java/com/ohgiraffers/dalryeo/integration/ApiContractIntegrationTest.java`

## 보관된 원문

- `docs/archive/auth/jwt-token-dependency-security-improvement.md`
