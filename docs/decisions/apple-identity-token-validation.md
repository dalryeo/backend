# Apple Identity Token Validation

- Status: Active
- Audience: Engineers, Codex
- Source of Truth: Yes
- Last Reviewed: 2026-06-16

## 결정

Apple OAuth 로그인에서 클라이언트가 보낸 `identityToken`은 서버에서 Apple JWK 기반으로 검증한다. 단순히 JWT를 파싱해서 `sub`만 읽는 방식은 사용하지 않는다.

검증에 성공한 경우에만 Apple `sub`를 내부 OAuth provider id로 사용한다.

## 배경

Apple identity token은 외부 사용자를 식별하는 인증 재료다. 서명, issuer, audience, 만료, subject를 확인하지 않으면 아래 문제가 생긴다.

- Apple이 발급하지 않은 토큰을 신뢰할 수 있다.
- 다른 앱을 대상으로 발급된 토큰을 받아들일 수 있다.
- 만료된 토큰이나 형식이 깨진 토큰으로 로그인이 가능할 수 있다.
- 운영 장애 상황에서 Apple JWK 조회 실패와 잘못된 토큰을 구분하기 어렵다.

## 선택한 방식

`AppleOAuthValidator`가 identity token 검증 흐름을 담당하고, `AppleJwkProvider`가 Apple JWK 조회와 cache를 담당한다.

검증 기준은 아래와 같다.

- JWT 형식이어야 한다.
- header에 `kid`가 있어야 한다.
- JWS algorithm은 `RS256`이어야 한다.
- `kid`에 맞는 Apple JWK를 찾고 RSA 공개키로 서명을 검증해야 한다.
- issuer는 `https://appleid.apple.com`이어야 한다.
- audience는 설정된 Apple client id 중 하나여야 한다.
- expiration은 clock skew를 고려해 유효해야 한다.
- issued-at은 있어야 하며 허용 범위를 넘는 미래이면 안 된다.
- subject는 비어 있으면 안 된다.

허용 audience는 `app.oauth.apple.client-id`와 `app.oauth.apple.allowed-client-ids`를 합친 effective client ids를 사용한다.

## JWK 조회와 cache

Apple JWK는 Apple JWK endpoint에서 가져온다.

```text
https://appleid.apple.com/auth/keys
```

JWK cache는 서버 메모리에 둔다. 유효한 cache가 있으면 먼저 cache에서 `kid`를 찾고, cache가 없거나 필요한 `kid`를 찾지 못하면 endpoint를 다시 조회한다.

현재 정책은 아래와 같다.

- cache TTL 안에서는 cache를 우선 사용한다.
- stale cache는 신뢰하지 않는다.
- `kid` miss 후 반복 refetch는 cooldown으로 제한한다.
- JWK 조회 응답이 비어 있거나 파싱할 수 없으면 fetch 실패로 처리한다.
- RSA key가 아닌 JWK는 서명 검증에 사용하지 않는다.

## 실패 처리

Apple identity token 검증 실패는 외부 응답에서 `AuthErrorCode.OAUTH_TOKEN_VERIFICATION_FAILED`로 수렴한다.

```text
AC-003 OAuth 토큰 검증 실패
HTTP 401 Unauthorized
```

클라이언트에는 세부 실패 이유를 노출하지 않는다. 세부 이유는 운영 로그로 확인한다.

- 잘못된 토큰, claim 불일치, signature 실패는 warning 로그로 본다.
- Apple JWK fetch 실패는 외부 의존성 문제일 수 있으므로 error 로그로 본다.
- identity token 원문, refresh token 원문, Apple `sub` 원문은 로그에 남기지 않는다.
- 로그에 남기는 `kid`, `iss`, `aud` 같은 값은 줄바꿈 제거와 길이 제한을 적용한다.

## 결과

프론트엔드는 실제 Apple 로그인으로 받은 identity token을 서버에 보내야 한다. 임시 문자열, 서명되지 않은 JWT, 다른 앱 audience를 가진 token은 실패한다.

운영 환경에는 Apple client id 설정이 필요하다.

```text
APP_OAUTH_APPLE_CLIENT_ID
APP_OAUTH_APPLE_ALLOWED_CLIENT_IDS
```

## 구현 앵커

- `src/main/java/com/ohgiraffers/dalryeo/auth/oauth/AppleOAuthValidator.java`
- `src/main/java/com/ohgiraffers/dalryeo/auth/oauth/AppleJwkProvider.java`
- `src/main/java/com/ohgiraffers/dalryeo/config/AppleOAuthProperties.java`
- `src/test/java/com/ohgiraffers/dalryeo/auth/oauth/AppleOAuthValidatorTest.java`
- `src/test/java/com/ohgiraffers/dalryeo/auth/oauth/AppleJwkProviderTest.java`
- `src/test/java/com/ohgiraffers/dalryeo/config/AppleOAuthPropertiesTest.java`

## 보관된 원문

- `docs/archive/auth/apple-identity-token-validation-design.md`
- `docs/archive/auth/apple-identity-token-validation-implementation-plan.md`
- `docs/archive/auth/apple-identity-token-pr-review-followups.md`
