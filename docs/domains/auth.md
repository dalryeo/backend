# Auth Domain

- Status: Active
- Audience: Engineers, Codex
- Source of Truth: Yes
- Last Reviewed: 2026-06-22

## 도메인 개요

Auth 도메인은 외부 OAuth identity를 달려 사용자와 연결하고, 보호 API 호출에 필요한 토큰을 발급/검증하는 도메인이다. 현재 로그인 수단은 Apple OAuth이며, 서비스 내부 인증은 JWT access token과 refresh token으로 처리한다.

Auth는 사용자 생명주기의 일부도 소유한다. Apple OAuth 로그인 시 사용자를 생성하거나 탈퇴 사용자를 재활성화하고, 로그아웃과 탈퇴 시 refresh token을 정리한다.

## 핵심 책임

- Apple identity token을 검증하고 Apple subject를 추출한다.
- Apple subject와 달려 사용자를 연결한다.
- 신규 사용자를 생성한다.
- 탈퇴 사용자의 Apple 재로그인을 재활성화로 처리한다.
- Access Token과 Refresh Token을 발급한다.
- Refresh Token을 검증하고 회전한다.
- 보호 API에서 `@LoginUser` 사용자 ID를 해석한다.
- 로그아웃과 회원 탈퇴를 처리한다.

## 도메인 경계

Auth 도메인이 책임지는 것:

- OAuth provider와 provider user id의 매핑을 관리한다.
- JWT의 서명, 만료, token use claim을 검증한다.
- Refresh Token 원문을 저장하지 않고 hash로 저장한다.
- Refresh Token 재발급 시 저장된 token hash를 회전한다.
- 탈퇴 시 인증 토큰과 사용자 소유 데이터를 현재 구현 기준대로 정리한다.

Auth 도메인이 책임지지 않는 것:

- 온보딩 프로필을 완성하지 않는다.
- 러닝 기록의 도메인 검증을 수행하지 않는다.
- 랭킹 순위를 계산하지 않는다.
- 티어 점수나 현재 표시 티어를 계산하지 않는다.
- Apple 계정 자체의 생명주기나 Apple 서버 정책을 소유하지 않는다.
- `UserStatus.BANNED`에 대한 별도 제재 정책은 현재 Auth 문서의 기준으로 정의하지 않는다.

## 주요 모델

### 사용자

_Entity_

`User`는 달려 내부의 사용자 식별자와 프로필 상태를 가진다.

#### 속성

- `id`: 내부 사용자 식별자
- `status`: `NORMAL`, `BANNED`, `WITHDRAWN`
- `nickname`
- `gender`
- `birth`
- `height`
- `weight`
- `profileImage`
- `deletedAt`

#### 행위

- `withdraw()`: 프로필을 비우고 상태를 `WITHDRAWN`으로 바꾸며 탈퇴 시각을 기록한다.
- `reactivate()`: 상태를 `NORMAL`으로 바꾸고 탈퇴 시각을 비운다.
- `updateOnboarding(...)`: 온보딩 프로필을 갱신한다.
- `updateProfile(...)`: 프로필을 갱신한다.
- `updateProfileImage(...)`: 프로필 이미지를 갱신한다.

#### 규칙

- Apple OAuth 최초 로그인 시 `NORMAL` 사용자를 생성한다.
- 탈퇴 사용자가 같은 Apple 계정으로 다시 로그인하면 `NORMAL`로 재활성화하고 신규 사용자 흐름으로 응답한다.
- 탈퇴 시 닉네임, 성별, 생년월일, 키, 몸무게, 프로필 이미지는 비워진다.
- 현재 공통 활성 사용자 조회는 `WITHDRAWN` 사용자를 차단한다.

### OAuth Client

_Entity_

`OAuthClient`는 외부 OAuth 사용자와 내부 사용자를 연결하는 매핑이다.

#### 속성

- `userId`: 내부 사용자 ID
- `provider`: OAuth provider. 현재 기준은 `APPLE`
- `providerId`: provider가 발급한 사용자 식별자

#### 규칙

- 같은 provider와 provider id 조합은 하나의 내부 사용자에만 연결된다.
- 같은 사용자와 같은 provider 조합은 하나만 존재한다.
- Apple 로그인은 identity token 검증 후 subject를 `providerId`로 사용한다.

### Apple Identity Token

_External Credential_

Apple identity token은 Apple이 발급한 JWT이며, 달려 로그인 시작점이다.

#### 검증 기준

- JWT 형식이어야 한다.
- `kid`가 있어야 한다.
- 서명 알고리즘은 `RS256`이어야 한다.
- Apple JWK로 서명을 검증해야 한다.
- issuer는 `https://appleid.apple.com`이어야 한다.
- audience는 설정된 Apple client id 중 하나여야 한다.
- 만료 시각은 clock skew를 고려해 유효해야 한다.
- issued-at이 허용 범위를 넘는 미래이면 안 된다.
- subject가 비어 있으면 안 된다.

#### 규칙

- 검증 실패는 OAuth 토큰 검증 실패로 처리한다.
- 로그에는 claim 값을 그대로 길게 남기지 않고 제한 길이로 정리한다.
- Apple JWK 조회 실패와 kid lookup 실패는 인증 실패로 수렴한다.

### Apple JWK Cache

_External Key Cache_

Apple JWK Cache는 Apple identity token 서명 검증에 필요한 공개키 집합을 보관한다.

#### 속성

- `jwkSetUri`: Apple JWK endpoint
- `jwkCacheTtl`: JWK cache TTL
- `jwkReadTimeout`: JWK 조회 timeout
- `clockSkew`: Apple token 시간 검증 허용 오차

#### 규칙

- cache가 유효하면 먼저 cache에서 kid를 찾는다.
- cache miss 후 kid를 찾지 못하면 일정 시간 동안 반복 refetch를 제한한다.
- JWK 조회 응답이 비어 있거나 파싱할 수 없으면 fetch 실패로 처리한다.
- RSA key가 아닌 JWK는 사용할 수 없다.

### Access Token

_Credential_

Access Token은 보호 API 호출에 사용하는 JWT다.

#### 속성

- subject: 내부 `userId`
- `token_use`: `access`
- issued-at
- expiration

#### 규칙

- 보호 API는 `Authorization: Bearer <accessToken>` 형식으로 token을 받는다.
- `token_use`가 `access`가 아니면 access token으로 사용할 수 없다.
- 서명, 만료, subject 변환이 모두 유효해야 `@LoginUser`에 사용자 ID가 주입된다.
- Refresh Token을 Bearer token으로 제출하면 보호 API 인증에 실패해야 한다.

### Refresh Token

_Credential_

Refresh Token은 새 token pair를 발급받기 위한 JWT다.

#### 속성

- subject: 내부 `userId`
- `token_use`: `refresh`
- issued-at
- expiration

#### 규칙

- refresh endpoint는 `token_use=refresh`인 token만 받아야 한다.
- Refresh Token은 원문을 DB에 저장하지 않는다.
- 저장값은 SHA-256 hash다.
- 저장된 hash가 요청 token hash와 일치해야 한다.
- 저장된 token의 user id와 JWT subject user id가 같아야 한다.
- 만료되었거나 revoke된 token은 사용할 수 없다.
- 재발급에 성공하면 새 Access Token과 새 Refresh Token을 발급하고 저장 hash를 회전한다.
- 저장 hash 회전은 요청 token hash가 아직 현재 저장 hash와 일치할 때만 성공해야 한다.
- 같은 Refresh Token으로 동시에 재발급이 들어오면 하나의 요청만 회전에 성공해야 한다.

### Auth Token

_Entity_

`AuthToken`은 사용자별 현재 Refresh Token hash를 저장한다.

#### 속성

- `userId`
- `refreshTokenHash`
- `expiresAt`
- `revokedAt`

#### 행위

- `rotate(...)`: 새 refresh token hash와 만료 시각으로 교체하고 revoke 상태를 해제한다.
- `isExpired(now)`: 만료되었거나 revoke된 token인지 확인한다.

#### 규칙

- 사용자별 refresh token row는 하나다.
- refresh token hash는 전체에서 고유해야 한다.
- 로그아웃 시 사용자의 auth token row를 삭제한다.
- 탈퇴 시 사용자의 auth token row를 삭제한다.

### 회원 탈퇴

_Lifecycle Policy_

회원 탈퇴는 사용자 상태와 사용자 소유 데이터를 정리하는 흐름이다.

#### 규칙

- 탈퇴 요청은 인증된 사용자 기준으로 수행한다.
- auth token을 삭제한다.
- weekly tier snapshot을 삭제한다.
- weekly user stats를 삭제한다.
- running records를 삭제한다.
- 사용자 상태를 `WITHDRAWN`으로 바꾸고 프로필 값을 비운다.
- 이전 커스텀 프로필 이미지는 best-effort로 삭제한다.
- OAuth client 매핑은 현재 구현 기준으로 유지된다. 같은 Apple 계정으로 다시 로그인하면 기존 사용자를 재활성화한다.

## 도메인 규칙

- Apple identity token 검증을 통과하지 못하면 내부 사용자를 생성하거나 token을 발급하면 안 된다.
- Access Token과 Refresh Token은 `token_use`로 용도를 분리해야 한다.
- Refresh Token 원문은 저장하면 안 된다.
- Refresh Token 재발급은 저장된 token hash와 JWT subject가 모두 일치해야 한다.
- 로그아웃은 refresh token 저장 상태를 제거하는 동작이다.
- 탈퇴는 단순 token 폐기가 아니라 사용자 상태 전이와 소유 데이터 정리를 포함한다.
- 탈퇴 사용자의 재로그인은 기존 OAuth 매핑을 통해 재활성화될 수 있다.
- 보호 API에서 사용자 ID가 필요하면 직접 JWT를 파싱하지 말고 `@LoginUser` 흐름을 사용한다.

## 타 도메인과의 관계

| 도메인 | 관계 |
| --- | --- |
| Onboarding | Auth가 생성한 사용자의 프로필을 온보딩에서 완성한다. Auth는 온보딩 필드를 직접 완성하지 않는다. |
| User | 사용자 조회와 닉네임 중복 검증을 제공한다. User entity는 현재 auth 패키지에 위치한다. |
| Record | 탈퇴 시 사용자의 원본 기록을 삭제한다. Record 저장 검증은 Auth 책임이 아니다. |
| Ranking | 탈퇴 시 ranking 원천 집계인 `weekly_user_stats`를 삭제한다. |
| WeeklyTier | 탈퇴 시 사용자의 주간 티어 스냅샷을 삭제한다. |
| Profile Image | 탈퇴 시 이전 커스텀 프로필 이미지를 best-effort로 삭제한다. |

## 구현 앵커

### API

- `POST /auth/oauth/apple`
- `POST /auth/token/refresh`
- `POST /auth/refresh`
- `POST /auth/logout`
- `DELETE /auth/withdraw`

### 코드

- [AuthController](../../src/main/java/com/ohgiraffers/dalryeo/auth/controller/AuthController.java)
- [AuthService](../../src/main/java/com/ohgiraffers/dalryeo/auth/service/AuthService.java)
- [User](../../src/main/java/com/ohgiraffers/dalryeo/auth/entity/User.java)
- [UserStatus](../../src/main/java/com/ohgiraffers/dalryeo/auth/entity/UserStatus.java)
- [OAuthClient](../../src/main/java/com/ohgiraffers/dalryeo/auth/entity/OAuthClient.java)
- [AuthToken](../../src/main/java/com/ohgiraffers/dalryeo/auth/entity/AuthToken.java)
- [JwtTokenProvider](../../src/main/java/com/ohgiraffers/dalryeo/auth/jwt/JwtTokenProvider.java)
- [AuthenticatedUserResolver](../../src/main/java/com/ohgiraffers/dalryeo/auth/jwt/AuthenticatedUserResolver.java)
- [JwtTokenExtractor](../../src/main/java/com/ohgiraffers/dalryeo/auth/jwt/JwtTokenExtractor.java)
- [LoginUserArgumentResolver](../../src/main/java/com/ohgiraffers/dalryeo/auth/resolver/LoginUserArgumentResolver.java)
- [AppleOAuthValidator](../../src/main/java/com/ohgiraffers/dalryeo/auth/oauth/AppleOAuthValidator.java)
- [AppleJwkProvider](../../src/main/java/com/ohgiraffers/dalryeo/auth/oauth/AppleJwkProvider.java)
- [AppleOAuthProperties](../../src/main/java/com/ohgiraffers/dalryeo/config/AppleOAuthProperties.java)

### 대표 테스트

- [AuthControllerTest](../../src/test/java/com/ohgiraffers/dalryeo/auth/controller/AuthControllerTest.java)
- [AuthServiceTest](../../src/test/java/com/ohgiraffers/dalryeo/auth/service/AuthServiceTest.java)
- [JwtTokenProviderTest](../../src/test/java/com/ohgiraffers/dalryeo/auth/jwt/JwtTokenProviderTest.java)
- [AuthenticatedUserResolverTest](../../src/test/java/com/ohgiraffers/dalryeo/auth/jwt/AuthenticatedUserResolverTest.java)
- [LoginUserArgumentResolverTest](../../src/test/java/com/ohgiraffers/dalryeo/auth/resolver/LoginUserArgumentResolverTest.java)
- [AppleOAuthValidatorTest](../../src/test/java/com/ohgiraffers/dalryeo/auth/oauth/AppleOAuthValidatorTest.java)
- [AppleJwkProviderTest](../../src/test/java/com/ohgiraffers/dalryeo/auth/oauth/AppleJwkProviderTest.java)
- [AppleOAuthPropertiesTest](../../src/test/java/com/ohgiraffers/dalryeo/config/AppleOAuthPropertiesTest.java)
- [ApiContractIntegrationTest](../../src/test/java/com/ohgiraffers/dalryeo/integration/ApiContractIntegrationTest.java)
