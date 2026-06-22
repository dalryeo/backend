# Apple Identity Token 검증 설계

- Status: Archived
- Audience: Engineers
- Source of Truth: No
- Last Reviewed: 2026-06-16

> 이 문서는 Apple identity token 검증 강화 설계 기록이다. 현재 결정 기준은 `docs/decisions/apple-identity-token-validation.md`와 `docs/domains/auth.md`를 우선 확인한다.

## 배경

현재 Apple 로그인 흐름은 클라이언트가 보낸 `identityToken`을 JWT로 파싱한 뒤 `sub` claim을 Apple 사용자 ID로 사용한다.

이 방식은 Apple 사용자를 식별하는 최소 흐름은 갖추고 있지만, 운영 인증 검증으로는 부족하다. 현재 구현은 토큰이 실제 Apple이 발급한 것인지, 우리 앱을 대상으로 발급된 것인지, 아직 만료되지 않았는지 확인하지 않는다.

이번 작업의 목적은 Apple 로그인을 새로 만드는 것이 아니라, 이미 있는 Apple 로그인 흐름에서 identity token 검증을 운영 가능한 수준으로 강화하는 것이다.

## 목표

Apple identity token은 아래 조건을 모두 만족해야 한다.

- Apple 공개키로 서명이 유효해야 한다.
- `iss`는 `https://appleid.apple.com`이어야 한다.
- `aud`는 서버 설정의 Apple client id와 정확히 일치해야 한다.
- `exp`가 만료되지 않아야 한다.
- 서버와 Apple 사이의 작은 시간 차이를 고려해 60초 clock skew를 허용한다.
- `sub`는 비어 있으면 안 된다.

검증에 성공하면 기존처럼 `sub`를 Apple 사용자 ID로 반환한다.

## 제외 범위

이번 브랜치에서는 아래 작업을 하지 않는다.

- Spring Security OAuth Client 도입
- Redis 도입
- refresh token 저장 구조 변경
- Apple Watch 또는 멀티 디바이스 토큰 정책 변경
- 프론트 개발용 우회 로그인 추가
- 미사용 `dev-mode` 유지

범위를 제한하는 이유는 이번 작업의 핵심이 Apple identity token 검증이기 때문이다. Redis, 멀티 디바이스 토큰, Spring Security 전환은 인증 구조 전체에 영향을 주므로 별도 브랜치에서 다루는 것이 적절하다.

## 결정한 정책

### Audience 검증

Apple identity token의 `aud`는 `APP_OAUTH_APPLE_CLIENT_ID` 환경변수로 주입한 client id와 일치해야 한다.

```yaml
app:
  oauth:
    apple:
      client-id: ${APP_OAUTH_APPLE_CLIENT_ID}
```

테스트 환경에서는 실제 Apple client id를 쓰지 않고 테스트용 값을 사용한다.

```yaml
app:
  oauth:
    apple:
      client-id: test.apple.client
```

### Dev Mode 제거

현재 `dev-mode`, `dev-apple-id-prefix` 설정은 코드에서 사용되지 않는다. 보안 검증 우회가 가능한 것처럼 오해를 만들 수 있으므로 제거한다.

프론트엔드는 앞으로 실제 Apple 로그인으로 받은 identity token을 서버에 보내는 것을 전제로 한다. 임시 문자열이나 서명되지 않은 가짜 토큰은 실패한다.

### Apple JWK 캐시

Apple 공개키는 Apple JWK endpoint에서 가져온다.

```text
https://appleid.apple.com/auth/keys
```

캐시 정책은 아래와 같다.

- Apple JWK는 서버 메모리에 캐시한다.
- 캐시 TTL은 24시간이다.
- 토큰 header의 `kid`가 캐시에 있고 TTL 안이면 캐시를 사용한다.
- 캐시가 없거나 `kid`가 없으면 Apple JWK endpoint를 다시 조회한다.
- TTL이 지난 stale 캐시는 사용하지 않는다.
- 재조회 실패 또는 재조회 후에도 `kid`가 없으면 검증 실패로 처리한다.

stale 캐시를 사용하지 않는 이유는 보안 검증 작업의 목적과 맞추기 위해서다. Apple이 키를 폐기했을 가능성이 있는 상황에서 만료된 캐시를 계속 신뢰하면 검증 기준이 약해진다.

### Apple JWK 조회 실패 정책

- JWK 조회 timeout은 2초로 둔다.
- 로그인 요청 중 JWK 조회 재시도는 하지 않는다.
- 유효한 캐시가 없고 Apple JWK 조회도 실패하면 `AC-003`으로 처리한다.

외부 API 재시도를 로그인 요청 안에서 길게 수행하면 사용자 응답이 느려지고 서버 자원도 묶인다. 이번 브랜치에서는 짧게 실패시키고, 운영 로그로 원인을 확인할 수 있게 한다.

### 시간 검증

`exp` 검증에는 60초 clock skew를 허용한다. 서버 시간과 Apple 토큰 발급 시간 사이에 작은 차이가 있을 수 있기 때문이다.

`AppleJwkProvider`의 TTL 테스트는 `Thread.sleep`을 사용하지 않고 `Clock`을 주입해 테스트한다. 운영에서는 실제 시계를 사용하고, 테스트에서는 가짜 시계로 시간이 지난 상황을 만든다.

## 구성 요소

### AppleOAuthValidator

Apple identity token 검증 흐름을 담당한다.

처리 순서는 아래와 같다.

1. identity token을 JWT로 파싱한다.
2. token header에서 `kid`를 확인한다.
3. `AppleJwkProvider`에 `kid`에 맞는 JWK를 요청한다.
4. JWK로 서명을 검증한다.
5. `iss`, `aud`, `exp`, `sub`를 검증한다.
6. 검증에 성공하면 `sub`를 반환한다.

### AppleJwkProvider

Apple JWK 조회, 캐시, `kid` 선택을 담당한다.

이름을 `AppleJwkProvider`로 두는 이유는 이 클래스가 단순한 공개키 제공자가 아니라 Apple JWK endpoint와 `kid` 기반 키 선택을 다루기 때문이다.

처리 정책은 아래와 같다.

1. 유효한 캐시에서 `kid`를 먼저 찾는다.
2. 캐시가 없거나 `kid`가 없으면 Apple JWK endpoint를 조회한다.
3. 조회에 성공하면 캐시를 갱신한다.
4. 갱신된 JWK set에서 `kid`를 찾는다.
5. 찾지 못하면 검증 실패로 이어지게 한다.
6. TTL이 지난 stale 캐시는 사용하지 않는다.

### AppleOAuthProperties

`app.oauth.apple` 설정값을 Java 코드에서 읽기 위한 설정 바인딩 클래스다.

이번 설정은 단일 값이 아니라 아래 값들을 한 묶음으로 다룬다.

- `client-id`
- `jwk-set-uri`
- `jwk-cache-ttl`
- `jwk-read-timeout`
- `clock-skew`

설정값이 여러 개이고 시간 정책이 포함되어 있으므로 `@Value`를 여러 클래스에 흩어두기보다 properties 클래스로 묶는 것이 적절하다.

## 검증 흐름

성공 흐름은 아래와 같다.

```text
identity token 수신
-> JWT parse
-> header kid 확인
-> Apple JWK 조회 또는 캐시 조회
-> 서명 검증
-> iss 검증
-> aud 검증
-> exp 검증
-> sub 검증
-> sub 반환
-> 기존 OAuthClient 조회 또는 User 생성
-> access token / refresh token 발급
```

실패 흐름은 모두 `AuthService`에서 기존 `AuthErrorCode.OAUTH_TOKEN_VERIFICATION_FAILED`로 변환한다.

## 에러 처리와 로그 정책

API 응답은 기존 인증 에러 코드로 통일한다.

```text
AC-003 OAuth 토큰 검증 실패
HTTP 401 Unauthorized
```

클라이언트 응답에는 세부 실패 이유를 노출하지 않는다. 실패 원인을 자세히 알려줘도 클라이언트가 다르게 처리할 일이 거의 없고, 보안상 불필요한 정보를 줄이는 편이 낫기 때문이다.

로그 레벨은 실패 성격에 따라 나눈다.

| 실패 상황 | 로그 레벨 | 이유 |
| --- | --- | --- |
| invalid jwt format | WARN | 잘못된 클라이언트 입력 |
| missing kid | WARN | 비정상 토큰 |
| kid not found | WARN | 키 불일치 또는 잘못된 토큰 |
| signature verification failed | WARN | 변조 가능성은 있지만 서버 장애는 아님 |
| invalid issuer | WARN | 신뢰하지 않는 발급자 |
| invalid audience | WARN | 우리 앱 대상 토큰이 아님 |
| expired token | WARN | 만료 토큰 사용 |
| missing subject | WARN | 사용자 식별자 없음 |
| jwk fetch failed | ERROR | 외부 의존성 실패, 로그인 장애 가능 |

로그에 남기지 않을 값은 아래와 같다.

- identity token 원문
- refresh token 원문
- `sub` 원문

로그에 제한적으로 남길 수 있는 값은 아래와 같다.

- reason
- kid
- iss
- aud

## 테스트 전략

테스트는 실제 Apple 서버에 의존하지 않는다. 테스트용 RSA 키와 테스트용 JWK를 만들어 Apple identity token 검증을 재현한다.

### AppleOAuthValidatorTest

검증할 케이스는 아래와 같다.

| 케이스 | 기대 결과 |
| --- | --- |
| 유효한 Apple identity token | `sub` 반환 |
| JWT 형식이 아님 | 검증 실패 |
| `kid` 없음 | 검증 실패 |
| `kid`에 맞는 JWK 없음 | 검증 실패 |
| 서명 불일치 | 검증 실패 |
| `iss` 불일치 | 검증 실패 |
| `aud` 불일치 | 검증 실패 |
| `exp` 만료 | 검증 실패 |
| `sub` 없음 | 검증 실패 |

### AppleJwkProviderTest

검증할 케이스는 아래와 같다.

| 케이스 | 기대 결과 |
| --- | --- |
| 캐시 TTL 안에서 같은 `kid` 요청 | Apple JWK 재조회 없음 |
| 캐시 없음 + JWK 조회 실패 | 검증 실패 |
| `kid` miss 후 JWK 재조회 성공 | 검증 성공 |
| stale 캐시 + JWK 조회 실패 | 검증 실패 |

시간 테스트는 `Clock` 주입으로 처리한다. `Thread.sleep`은 사용하지 않는다.

기존 `AuthServiceTest`와 `ApiContractIntegrationTest`는 Apple validator를 mock으로 쓰는 구조를 유지한다. 로그인 API 계약 테스트가 Apple 서명 검증 세부 구현에 묶이지 않게 하기 위해서다.

## 프론트엔드 영향

프론트엔드는 실제 Apple 로그인으로 받은 identity token을 서버에 보내야 한다.

영향은 아래와 같다.

| 기존 프론트 테스트 방식 | 영향 |
| --- | --- |
| 실제 Apple identity token 사용 | 정상 동작 |
| 임시 문자열 사용 | 로그인 실패 |
| 서명되지 않은 가짜 JWT 사용 | 로그인 실패 |
| `sub`만 들어 있는 토큰 사용 | 로그인 실패 |

실패 응답은 `AC-003`으로 내려간다.

## 필요한 환경변수

운영 환경에는 아래 환경변수가 필요하다.

```bash
APP_OAUTH_APPLE_CLIENT_ID=실제 Apple client_id
```

이 값은 코드에 커밋하지 않는다. 운영 배포 환경에서 주입한다.

## 참고 문서

- Apple Developer Documentation: Verifying a user
- Apple Developer Documentation: Generate and validate tokens
- Apple JWK endpoint: `https://appleid.apple.com/auth/keys`

## 포트폴리오 정리 관점

이 작업은 "Apple 로그인 구현" 자체보다 "Apple identity token 검증 누락을 운영 수준으로 보완한 작업"으로 정리하는 것이 적절하다.

포트폴리오에 사용할 때는 아래 구조로 정리한다.

```text
문제:
Apple identity token을 파싱만 하고 있어 실제 Apple 발급 토큰인지 검증하지 못했다.

원인:
서명, issuer, audience, expiration 검증이 없었다.

해결:
Apple JWK 기반 서명 검증과 iss/aud/exp/sub 검증을 추가하고,
JWK 캐시 TTL, timeout, stale cache 미사용 정책을 정했다.

결과:
실제 Apple이 우리 앱 대상으로 발급한 만료되지 않은 토큰만 로그인에 사용할 수 있게 한다.
```
