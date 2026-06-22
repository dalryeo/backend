# Apple Identity Token 검증 PR 리뷰 반영 기록

- Status: Archived
- Audience: Engineers
- Source of Truth: No
- Last Reviewed: 2026-06-16

> 이 문서는 Apple identity token 검증 PR 리뷰 반영 기록이다. 현재 결정 기준은 `docs/decisions/apple-identity-token-validation.md`와 `docs/domains/auth.md`를 우선 확인한다.

## 배경

Apple identity token 검증 강화 PR에서 리뷰를 받으면서 아래 내용을 추가로 정리했다.

- `RuntimeException` 대신 인증 도메인 예외로 실패를 통일한다.
- `exp`뿐 아니라 `iat`도 검증한다.
- 중복된 RSA 타입 검사를 제거한다.
- `AppleJwkProvider`의 `synchronized` 병목 가능성은 별도 작업으로 분리한다.

이번 문서는 구현 자체보다 리뷰를 반영하면서 결정한 예외 처리, 시간 검증, 배포 설정 기준을 남기기 위한 기록이다.

## RuntimeException 대신 AuthException을 사용한 이유

기존 구현은 Apple identity token 검증 실패를 `RuntimeException`으로 표현할 수 있었다.

`RuntimeException`은 너무 넓은 예외다. 프로그램 입장에서는 아래 상황을 타입만 보고 구분하기 어렵다.

```text
토큰 형식이 잘못됨
서명이 맞지 않음
audience가 다름
Apple JWK 조회 실패
예상하지 못한 내부 오류
```

로그에 실패 이유를 남기면 사람은 원인을 볼 수 있다. 하지만 로그는 프로그램이 분기하기 위한 계약이 아니다.

따라서 외부 API 응답으로 이어지는 검증 실패는 `AuthException(AuthErrorCode.OAUTH_TOKEN_VERIFICATION_FAILED)`로 통일했다.

```text
Apple identity token 검증 실패
-> AuthException
-> AC-003 OAuth 토큰 검증 실패
-> 401 Unauthorized
```

이렇게 하면 클라이언트는 실패 이유 세부사항을 알 필요 없이 안정적인 에러 코드 `AC-003`을 받는다. 서버는 로그로 세부 원인을 확인한다.

## 로그와 예외의 역할 차이

로그는 사람을 위한 기록이다.

```text
운영자가 어떤 이유로 실패했는지 확인한다.
장애 상황에서 원인을 추적한다.
```

예외는 프로그램을 위한 흐름 제어다.

```text
어떤 계층에서 잡아야 하는지 결정한다.
API 응답을 어떤 에러 코드로 바꿀지 결정한다.
외부 의존성 실패와 사용자 입력 실패를 구분한다.
```

따라서 `log.warn` 또는 `log.error`만 남기고 `RuntimeException`을 그대로 던지는 방식은 부족하다.

사람에게는 로그가 보이지만, 프로그램에게는 여전히 너무 넓은 예외만 전달되기 때문이다.

## AppleJwkFetchException을 추가한 이유

Apple JWK 조회 실패는 인증 실패로 응답해야 하지만, 내부적으로는 일반 토큰 검증 실패와 성격이 다르다.

```text
invalid_audience: 클라이언트가 우리 앱 대상이 아닌 토큰을 보냄
jwk_fetch_failed: 서버가 Apple 공개키를 가져오지 못함
```

둘 다 API 응답은 `AC-003`으로 내려가지만 운영 관점에서는 다르게 봐야 한다.

- `invalid_audience`는 잘못된 토큰 또는 설정 불일치 가능성이 크다.
- `jwk_fetch_failed`는 Apple JWK endpoint, 네트워크, 서버 outbound 문제일 수 있다.

그래서 `AppleJwkProvider`에서는 JWK 조회 실패를 `AppleJwkFetchException`으로 감싼다.

```java
catch (RuntimeException e) {
    throw new AppleJwkFetchException("Apple JWK fetch failed", e);
}
```

그리고 `AppleOAuthValidator`는 이 예외를 잡아 `ERROR` 로그를 남긴 뒤, 외부로는 `AuthException(AC-003)`으로 변환한다.

```text
AppleJwkFetchException
-> log.error(reason=jwk_fetch_failed)
-> AuthException(OAUTH_TOKEN_VERIFICATION_FAILED)
```

## 이미 AppleJwkFetchException이면 다시 감싸지 않는 이유

`fetchAndCache()`에서는 catch를 두 번 나눈다.

```java
catch (AppleJwkFetchException e) {
    throw e;
} catch (RuntimeException e) {
    throw new AppleJwkFetchException("Apple JWK fetch failed", e);
}
```

이미 `AppleJwkFetchException`이면 의미가 정리된 예외다. 다시 감싸면 예외가 불필요하게 한 겹 더 생긴다.

예를 들어 빈 응답 본문은 이미 아래처럼 구체적인 메시지를 가진다.

```text
Apple JWK fetch failed: empty response body
```

이걸 다시 감싸면 바깥 메시지는 더 일반적인 값이 된다.

```text
Apple JWK fetch failed
caused by Apple JWK fetch failed: empty response body
```

따라서 이미 의미가 명확한 예외는 그대로 전달하고, 아직 의미가 애매한 `RuntimeException`만 `AppleJwkFetchException`으로 바꾼다.

## iat 검증을 추가한 이유

기존 검증은 만료 시간 `exp`를 확인했다. 리뷰에서는 발급 시간 `iat`도 확인하자는 의견이 있었다.

`iat`는 토큰이 언제 발급되었는지를 나타낸다. 이 값이 서버 현재 시간보다 너무 미래라면 이상한 토큰이다.

예를 들어 현재 시간이 10시인데 토큰의 `iat`가 10시 30분이면 아래 가능성을 의심할 수 있다.

```text
클라이언트 또는 서버 시간 불일치
비정상적으로 생성된 토큰
검증 기준을 우회하려는 토큰
```

`iat` 검증은 `exp` 검증만큼 핵심적인 만료 방어는 아니지만, 토큰의 시간 일관성을 보완한다.

이번 구현에서는 `iat`가 없거나, 현재 시간보다 60초를 초과해 미래이면 실패시킨다.

```text
iat 없음 -> AC-003
iat > now + 60초 -> AC-003
iat <= now + 60초 -> 허용
```

60초를 허용한 이유는 서버와 Apple 또는 클라이언트 사이에 작은 시간 차이가 있을 수 있기 때문이다.

시간대는 별도 보정 대상이 아니다. JWT의 `iat`, `exp`는 epoch 기반 시간이고 코드에서는 `Instant`로 비교한다. 한국시간으로 변환해서 비교하는 구조가 아니다.

## 중복 RSA 검사 제거

리뷰에서 `AppleOAuthValidator`의 RSA 타입 검사가 중복이라고 지적했다.

현재 책임은 아래처럼 나뉜다.

```text
AppleJwkProvider
-> kid에 맞는 JWK 조회
-> RSAKey인지 확인

AppleOAuthValidator
-> provider가 준 RSAKey로 서명 검증
```

`AppleJwkProvider`가 이미 `requireRsa()`로 RSA 키만 통과시키기 때문에, validator에서 다시 `instanceof RSAKey`를 확인할 필요가 없다.

중복 검사를 제거해 책임을 한 곳으로 모았다.

## synchronized 개선은 보류한 이유

`AppleJwkProvider.getByKeyId()`는 현재 `synchronized`다.

장점은 캐시 갱신과 `kid` miss cooldown 상태를 안전하게 다룰 수 있다는 것이다. 단점은 캐시 hit 상황에서도 모든 요청이 같은 lock을 지나가므로 로그인 요청이 많아지면 병목이 될 수 있다는 점이다.

이 문제는 맞는 지적이지만 이번 PR에서는 보류했다.

이유는 아래와 같다.

- 현재 PR의 핵심은 Apple identity token 검증 정확성이다.
- 동시성 최적화는 `volatile`, 별도 lock, cache snapshot 등 설계 선택지가 있다.
- 잘못 건드리면 키 rotation, kid miss, cache refresh 흐름에 회귀가 생길 수 있다.

따라서 이번 PR에서는 안전한 동기화 구조를 유지하고, 성능 개선은 별도 작업으로 넘긴다.

## 배포 설정

이번 기능은 배포 환경에 아래 값이 필요하다.

```text
APP_OAUTH_APPLE_CLIENT_ID
```

이 값은 Apple identity token의 `aud`와 비교된다.

iOS 앱에서만 Apple 로그인을 수행한다면 값은 iOS 앱의 Bundle ID다.

```text
예: com.dalryeo.app
```

이 값은 Apple Team ID, Key ID, client secret이 아니다. 비밀값이 아니라 앱 식별자다.

현재 `deploy-dev.yml`은 GitHub Actions variable을 읽어 Azure Container App 환경변수로 주입한다.

```yaml
APP_OAUTH_APPLE_CLIENT_ID=${{ vars.APP_OAUTH_APPLE_CLIENT_ID }}
```

따라서 GitHub 저장소에서 아래 위치에 등록한다.

```text
Settings
-> Secrets and variables
-> Actions
-> Variables
-> New repository variable
```

```text
Name: APP_OAUTH_APPLE_CLIENT_ID
Value: iOS 앱 Bundle ID
```

## merge 후 확인할 것

merge 후 dev 배포가 성공하면 실제 iOS 앱에서 Apple 로그인을 한 번 수행해 `/auth/oauth/apple` 호출이 성공하는지 확인한다.

실패하면 백엔드 로그의 `reason`을 먼저 확인한다.

```text
invalid_audience
-> APP_OAUTH_APPLE_CLIENT_ID 값이 실제 identityToken aud와 다를 가능성이 크다.

jwk_fetch_failed
-> 서버가 Apple JWK endpoint를 조회하지 못한 것이다.

invalid_signature
-> 토큰 서명이 Apple 공개키로 검증되지 않은 것이다.

expired
-> 오래된 identityToken을 보낸 것이다.
```

## 다음 작업으로 넘긴 것

### AppleJwkProvider 동시성 최적화

현재 `getByKeyId()` 전체가 `synchronized`라서 캐시 hit도 직렬화된다.

별도 작업에서는 아래 방향을 검토한다.

```text
cache snapshot을 volatile로 관리
cache hit은 lock 없이 처리
cache miss 또는 refresh 때만 lock 사용
kid miss cooldown 상태와 cache refresh 경쟁 조건 테스트 추가
```

### 여러 Apple client id 허용 여부

현재 백엔드는 `APP_OAUTH_APPLE_CLIENT_ID` 하나만 허용한다.

iOS 앱 로그인만 있으면 충분하다. 하지만 나중에 아래 흐름이 추가되면 여러 client id를 허용해야 할 수 있다.

```text
React 웹에서 Apple 로그인
watchOS 앱이 독립적으로 Apple 로그인
다른 Apple Services ID 기반 로그인
```

이 경우 설정을 단일 값이 아니라 목록으로 바꾸는 작업을 별도 PR로 진행한다.

```yaml
app:
  oauth:
    apple:
      client-ids:
        - com.dalryeo.app
        - com.dalryeo.web
```

## 검증한 테스트

PR 작업 중 아래 테스트를 실행했다.

```bash
./gradlew test --tests com.ohgiraffers.dalryeo.auth.oauth.AppleJwkProviderTest \
  --tests com.ohgiraffers.dalryeo.auth.oauth.AppleOAuthValidatorTest
```

```bash
./gradlew test --tests com.ohgiraffers.dalryeo.auth.oauth.AppleJwkProviderTest \
  --tests com.ohgiraffers.dalryeo.auth.oauth.AppleOAuthValidatorTest \
  --tests com.ohgiraffers.dalryeo.auth.service.AuthServiceTest \
  --tests com.ohgiraffers.dalryeo.integration.ApiContractIntegrationTest
```

```bash
./gradlew test --rerun-tasks
```

최종 push 전 pre-push hook에서도 `./gradlew test`가 실행되었다.
