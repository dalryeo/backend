# 인증 토큰 의존성 보안 개선 구현 계획 및 기록

- Status: Archived
- Audience: Engineers
- Source of Truth: No
- Last Reviewed: 2026-06-16

> 이 문서는 JWT token 용도 분리와 인증 의존성 보안 개선 작업 기록이다. 현재 결정 기준은 `docs/decisions/jwt-token-purpose-separation.md`와 `docs/domains/auth.md`를 우선 확인한다.

> **에이전트 작업자 필수 안내:** 이 계획을 작업 단위로 구현할 때는 `superpowers:subagent-driven-development` 사용을 권장하며, 대안으로 `superpowers:executing-plans`를 사용할 수 있다. 각 단계는 진행 추적을 위해 체크박스(`- [ ]`) 형식을 사용한다.

**목표:** refresh token이 API access token처럼 사용되지 못하게 하고, access token이 refresh endpoint에서 사용되지 못하게 하며, 취약한 Nimbus JOSE JWT 의존성 버전을 제거한다.

**아키텍처:** 현재 Controller/Service 구조를 유지하고, Spring Security 전면 도입, Redis 전환, 세션 정책 재설계, DB 스키마 변경은 하지 않는다. JWT에 `token_use` claim을 추가하고, access token 검증과 refresh token 검증을 분리한다. 보호 API 또는 refresh API 진입 여부를 판단하는 인증 호출 지점만 수정한다. Apple OAuth 호환성 위험을 줄이기 위해 Nimbus JOSE JWT는 기존 `9.37.x` 라인에서 업그레이드한다.

**기술 스택:** Java 17, Spring Boot 3.2.3, Gradle, JJWT 0.12.3, Nimbus JOSE JWT, JUnit 5, Mockito, MockMvc.

---

## 현재 상태

- 브랜치: `fix/auth-token-dependency-security`
- 관련 이슈: #30, #31
- PR: #46
- 구현 상태: 검증 중
- 문서 갱신일: 2026-05-09

아래 작업 1~8은 최초 실행 계획이다. 최종 구현 기준은 이 섹션의 구현 결과와 검증 기록을 우선한다.

## 구현 결과

- `build.gradle`에서 `com.nimbusds:nimbus-jose-jwt`를 `9.37.4`로 업그레이드했다.
- `JwtTokenProvider`가 JWT 생성 시 `token_use` claim에 `access` 또는 `refresh`를 담도록 변경했다.
- 용도 검증 없는 public JWT 검증 API를 제거하고, `getUserIdFromAccessToken`, `getUserIdFromRefreshToken`으로 토큰 용도와 userId 추출을 함께 처리하도록 정리했다.
- refresh token 만료 조회 메서드는 실제 사용 범위에 맞춰 `getRefreshTokenExpiration`으로 이름만 명확히 했다. 이 메서드는 만료 시각 조회 용도이며, `token_use` 추가 검증은 하지 않는다.
- 보호 API는 `AuthenticatedUserResolver`와 `@LoginUser` 기반 `HandlerMethodArgumentResolver`를 통해 access token에서 사용자 ID를 주입받도록 바꿨다.
- 7개 Controller에 중복돼 있던 `extractUserIdFromRequest` 흐름을 제거하고, 보호 API 인증 실패 시 `ACCESS_TOKEN_INVALID`(`AC-007`)를 반환하도록 정리했다.
- refresh API는 `getUserIdFromRefreshToken`을 사용해 access token 오용을 막고, refresh token 검증 실패 시 `REFRESH_TOKEN_INVALID`(`AC-006`)를 반환한다.
- `REFRESH_TOKEN_EXPIRED`는 `REFRESH_TOKEN_INVALID`로 정리했고, README의 인증 에러 코드 설명도 `AC-006`, `AC-007` 기준으로 갱신했다.
- JWT 파싱 실패, 잘못된 JWT 인자, 숫자가 아닌 subject 같은 원인을 `AuthException`의 `cause`로 보존하도록 `BusinessException`과 `AuthException` 생성자를 보강했다.
- 탈퇴한 사용자의 refresh token 재발급 요청은 기존 API 계약을 유지하기 위해 사용자 조회를 먼저 수행하며, 클라이언트에는 `탈퇴한 사용자입니다.` 응답이 유지된다.
- 협업 규칙 관리를 위해 `AGENTS.md`를 추가하고, 같은 세션에서 이미 읽은 스킬 md 파일은 반복 조회하지 않는 기준을 기록했다.

## 검증 기록

| 날짜 | 명령 | 결과 |
| --- | --- | --- |
| 2026-05-09 | `./gradlew test --tests '*JwtTokenProviderTest' --tests '*AuthServiceTest'` | 성공 |
| 2026-05-09 | `./gradlew test --tests '*JwtTokenProviderTest' --tests '*AuthServiceTest' --tests '*GlobalExceptionHandlerTest' --tests '*ApiContractIntegrationTest'` | 성공 |
| 2026-05-09 | `./gradlew test --tests '*LoginUserArgumentResolverTest' --tests '*AuthenticatedUserResolverTest' --tests '*ApiContractIntegrationTest'` | 성공 |
| 2026-05-09 | `./gradlew test --rerun-tasks` | 성공 |
| 2026-05-09 | `git push` pre-push `./gradlew test` | 성공 |

## 진행 기록

| 날짜 | 상태 | 기록 |
| --- | --- | --- |
| 2026-05-09 | 검증 중 | #30 JWT access/refresh token 용도 분리, 보호 API access-only 적용, refresh API refresh-only 적용, 중복 사용자 추출 제거, `@LoginUser` 인자 주입, AC-006/AC-007 에러 계약 정리를 구현했다. |
| 2026-05-09 | 검증 중 | #30 JWT 예외 처리에서 광범위한 `Exception` catch를 제거하고, 예상 가능한 JWT 파싱 오류와 subject 변환 오류의 cause를 보존하도록 정리했다. |
| 2026-05-09 | 검증 중 | #31 Nimbus JOSE JWT를 `9.37.4`로 업그레이드했다. |

## 실행 메모

- 사용자의 명시적인 동의 없이 `main` 브랜치에서 구현을 시작하지 않는다.
- 구현 전에는 격리된 worktree 또는 짧은 생명주기의 작업 브랜치를 사용한다.
- 기존의 관련 없는 로컬 변경을 되돌리지 않는다. 계획 작성 시점에는 `src/test/resources/application-test.yml`, `.githooks/`, `.gitmessage.txt`, `AGENTS.md`, `scripts/`에 관련 없는 변경이 있었다.
- 사용자가 브랜치 또는 커밋을 명시적으로 요청하지 않는 한 브랜치/커밋 컨벤션 문서는 읽지 않는다.
- 이 계획은 계획 문서이며, 현재 요청 범위가 커밋이 아니므로 커밋 단계는 포함하지 않는다.

## 파일별 책임

- `build.gradle` 수정: `com.nimbusds:nimbus-jose-jwt`를 `9.37.3`에서 `9.37.4`로 업그레이드한다.
- `src/main/java/com/ohgiraffers/dalryeo/auth/jwt/JwtTokenProvider.java` 수정: `token_use` claim과 용도별 검증 메서드를 추가한다.
- `src/test/java/com/ohgiraffers/dalryeo/auth/jwt/JwtTokenProviderTest.java` 생성: access/refresh token 용도 분리와 legacy token의 용도 검증 거부를 검증한다.
- `src/main/java/com/ohgiraffers/dalryeo/auth/service/AuthService.java` 수정: `refreshToken()`에서 refresh 용도 JWT만 허용한다.
- `src/test/java/com/ohgiraffers/dalryeo/auth/service/AuthServiceTest.java` 수정: `AuthService`가 refresh 용도 검증을 사용하고, 잘못된 용도의 token은 DB 조회 전에 중단하는지 검증한다.
- 보호 API Controller 수정: access 용도 JWT만 허용한다.
  - `src/main/java/com/ohgiraffers/dalryeo/auth/controller/AuthController.java`
  - `src/main/java/com/ohgiraffers/dalryeo/onboarding/controller/OnboardingController.java`
  - `src/main/java/com/ohgiraffers/dalryeo/record/controller/RecordController.java`
  - `src/main/java/com/ohgiraffers/dalryeo/record/controller/WeeklySummaryController.java`
  - `src/main/java/com/ohgiraffers/dalryeo/analysis/controller/AnalysisController.java`
  - `src/main/java/com/ohgiraffers/dalryeo/ranking/controller/RankingController.java`
  - `src/main/java/com/ohgiraffers/dalryeo/weeklytier/controller/WeeklyTierController.java`
- `src/test/java/com/ohgiraffers/dalryeo/integration/ApiContractIntegrationTest.java` 수정: API 수준의 token 오용 테스트를 추가한다.
- `docs/archive/auth/jwt-token-dependency-security-improvement.md` 수정: 구현과 검증이 끝난 뒤 체크리스트와 진행 기록을 갱신한다.

---

### 작업 1: Nimbus JOSE JWT 의존성 업그레이드

**파일:**
- 수정: `build.gradle`

- [ ] **1단계: Nimbus 의존성 버전 변경**

`build.gradle`에서 다음 내용을 찾는다.

```gradle
implementation 'com.nimbusds:nimbus-jose-jwt:9.37.3'
```

아래처럼 변경한다.

```gradle
implementation 'com.nimbusds:nimbus-jose-jwt:9.37.4'
```

- [ ] **2단계: 의존성 해석 결과 확인**

실행:

```bash
./gradlew dependencyInsight --dependency nimbus-jose-jwt --configuration runtimeClasspath
```

예상 결과: 출력에 `com.nimbusds:nimbus-jose-jwt:9.37.4`가 표시된다.

- [ ] **3단계: Apple OAuth 테스트 실행**

실행:

```bash
./gradlew test --tests '*AppleOAuthValidatorTest' --tests '*AppleJwkProviderTest'
```

예상 결과: Apple OAuth 관련 테스트 클래스가 모두 통과한다.

Gradle이 Maven Central에 접근하지 못해서만 실패한다면, 네트워크 권한 승인을 요청한 뒤 같은 명령을 다시 실행한다.

---

### 작업 2: JwtTokenProvider 용도 검증 테스트 추가

**파일:**
- 생성: `src/test/java/com/ohgiraffers/dalryeo/auth/jwt/JwtTokenProviderTest.java`
- 이후 작업 3에서 수정: `src/main/java/com/ohgiraffers/dalryeo/auth/jwt/JwtTokenProvider.java`

- [ ] **1단계: token 용도 분리를 검증하는 실패 테스트 추가**

`src/test/java/com/ohgiraffers/dalryeo/auth/jwt/JwtTokenProviderTest.java` 파일을 생성하고 아래 내용을 작성한다.

```java
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
```

- [ ] **2단계: 테스트가 실패하는지 확인**

실행:

```bash
./gradlew test --tests '*JwtTokenProviderTest'
```

예상 결과: `validateAccessToken(String)`과 `validateRefreshToken(String)` 메서드가 아직 없기 때문에 컴파일이 실패한다.

---

### 작업 3: JwtTokenProvider에 용도 claim 구현

**파일:**
- 수정: `src/main/java/com/ohgiraffers/dalryeo/auth/jwt/JwtTokenProvider.java`
- 테스트: `src/test/java/com/ohgiraffers/dalryeo/auth/jwt/JwtTokenProviderTest.java`

- [ ] **1단계: 용도 상수 추가 및 token 생성 로직 수정**

`JwtTokenProvider`의 필드 근처에 아래 상수를 추가한다.

```java
private static final String TOKEN_USE_CLAIM = "token_use";
private static final String ACCESS_TOKEN_USE = "access";
private static final String REFRESH_TOKEN_USE = "refresh";
```

token 생성 메서드를 아래처럼 교체한다.

```java
public String generateAccessToken(Long userId) {
    return generateToken(userId, accessTokenExpiration, ACCESS_TOKEN_USE);
}

public String generateRefreshToken(Long userId) {
    return generateToken(userId, refreshTokenExpiration, REFRESH_TOKEN_USE);
}

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
```

- [ ] **2단계: 용도별 검증 메서드와 공통 parser 추가**

반복되는 parser 코드를 아래 메서드들로 교체한다.

```java
public Long getUserIdFromToken(String token) {
    Claims claims = parseClaims(token);
    return Long.parseLong(claims.getSubject());
}

public boolean validateToken(String token) {
    try {
        parseClaims(token);
        return true;
    } catch (Exception e) {
        return false;
    }
}

public boolean validateAccessToken(String token) {
    return validateTokenUse(token, ACCESS_TOKEN_USE);
}

public boolean validateRefreshToken(String token) {
    return validateTokenUse(token, REFRESH_TOKEN_USE);
}

public Date getExpiration(String token) {
    Claims claims = parseClaims(token);
    return claims.getExpiration();
}

private boolean validateTokenUse(String token, String expectedTokenUse) {
    try {
        Claims claims = parseClaims(token);
        return expectedTokenUse.equals(claims.get(TOKEN_USE_CLAIM, String.class));
    } catch (Exception e) {
        return false;
    }
}

private Claims parseClaims(String token) {
    return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
}
```

- [ ] **3단계: JwtTokenProvider 테스트 실행**

실행:

```bash
./gradlew test --tests '*JwtTokenProviderTest'
```

예상 결과: `JwtTokenProviderTest`가 통과한다.

---

### 작업 4: AuthService에서 refresh token 용도 검증 적용

**파일:**
- 수정: `src/test/java/com/ohgiraffers/dalryeo/auth/service/AuthServiceTest.java`
- 수정: `src/main/java/com/ohgiraffers/dalryeo/auth/service/AuthService.java`

- [ ] **1단계: 기존 refresh token 서비스 테스트 수정**

`AuthServiceTest`에서 refresh token 검증 mock을 아래처럼 교체한다.

```java
when(jwtTokenProvider.validateToken(currentRefreshToken)).thenReturn(true);
```

아래처럼 변경한다.

```java
when(jwtTokenProvider.validateRefreshToken(currentRefreshToken)).thenReturn(true);
```

`refreshToken`, `deleted-refresh-token`, `missing-user-refresh-token`에 대한 같은 패턴도 모두 교체한다.

`refreshToken_throwsWhenJwtValidationFails`에서는 아래 코드를:

```java
when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(false);
```

아래처럼 변경한다.

```java
when(jwtTokenProvider.validateRefreshToken(refreshToken)).thenReturn(false);
```

- [ ] **2단계: access token 오용 서비스 테스트 추가**

`AuthServiceTest`의 기존 refresh token 테스트 근처에 아래 테스트를 추가한다.

```java
@Test
void refreshToken_throwsWhenAccessTokenIsUsedForRefresh() {
    String accessToken = "access-token";
    RefreshTokenRequest request = refreshTokenRequest(accessToken);

    when(jwtTokenProvider.validateRefreshToken(accessToken)).thenReturn(false);

    assertThatThrownBy(() -> authService.refreshToken(request))
            .isInstanceOf(AuthException.class)
            .extracting("errorCode")
            .isEqualTo(AuthErrorCode.REFRESH_TOKEN_EXPIRED);

    verify(jwtTokenProvider, never()).getUserIdFromToken(any());
    verify(authTokenRepository, never()).findByRefreshTokenHash(any());
}
```

- [ ] **3단계: 서비스 테스트가 실패하는지 확인**

실행:

```bash
./gradlew test --tests '*AuthServiceTest'
```

예상 결과: `AuthService.refreshToken()`이 아직 `validateToken()`을 호출하므로 refresh token 관련 테스트 중 하나 이상이 실패한다.

- [ ] **4단계: AuthService refresh 검증 수정**

`AuthService.refreshToken()`에서 아래 코드를:

```java
if (!jwtTokenProvider.validateToken(refreshToken)) {
    throw new AuthException(AuthErrorCode.REFRESH_TOKEN_EXPIRED);
}
```

아래처럼 변경한다.

```java
if (!jwtTokenProvider.validateRefreshToken(refreshToken)) {
    throw new AuthException(AuthErrorCode.REFRESH_TOKEN_EXPIRED);
}
```

- [ ] **5단계: 서비스 테스트 재실행**

실행:

```bash
./gradlew test --tests '*AuthServiceTest'
```

예상 결과: `AuthServiceTest`가 통과한다.

---

### 작업 5: token 오용 API 계약 테스트 추가

**파일:**
- 수정: `src/test/java/com/ohgiraffers/dalryeo/integration/ApiContractIntegrationTest.java`
- 이후 작업 6에서 수정: 보호 API Controller

- [ ] **1단계: refresh endpoint 오용 테스트 추가**

`refreshToken_keepsTokenResponseContract()` 근처에 아래 테스트를 추가한다.

```java
@Test
void refreshToken_returnsUnauthorizedWhenAccessTokenIsSubmitted() throws Exception {
    JsonNode loginResponse = login("apple-sub-refresh-access-token", "identity-refresh-access-token");
    String accessToken = loginResponse.path("data").path("accessToken").asText();

    mockMvc.perform(post("/auth/token/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "refreshToken": "%s"
                            }
                            """.formatted(accessToken)))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.data.code").value("AC-006"))
            .andExpect(jsonPath("$.data.message").value("refreshToken 만료"));
}
```

- [ ] **2단계: 보호 API 오용 테스트 추가**

로그아웃/탈퇴 계약 테스트 근처에 아래 테스트를 추가한다.

```java
@Test
void protectedApi_returnsUnauthorizedWhenRefreshTokenIsUsedAsBearerToken() throws Exception {
    JsonNode loginResponse = login("apple-sub-refresh-as-bearer", "identity-refresh-as-bearer");
    String refreshToken = loginResponse.path("data").path("refreshToken").asText();

    mockMvc.perform(post("/auth/logout")
                    .header("Authorization", bearer(refreshToken)))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.data.code").value("AC-006"))
            .andExpect(jsonPath("$.data.message").value("refreshToken 만료"));
}
```

- [ ] **3단계: 현재 보호 API 결함을 테스트로 확인**

실행:

```bash
./gradlew test --tests '*ApiContractIntegrationTest'
```

작업 6 이전의 예상 결과: `protectedApi_returnsUnauthorizedWhenRefreshTokenIsUsedAsBearerToken`가 실패한다. 보호 Controller가 아직 `validateToken()`을 호출하기 때문에 유효한 refresh JWT를 bearer token으로 받아들이기 때문이다.

---

### 작업 6: 보호 Controller에서 access token 용도 검증 적용

**파일:**
- 수정: `src/main/java/com/ohgiraffers/dalryeo/auth/controller/AuthController.java`
- 수정: `src/main/java/com/ohgiraffers/dalryeo/onboarding/controller/OnboardingController.java`
- 수정: `src/main/java/com/ohgiraffers/dalryeo/record/controller/RecordController.java`
- 수정: `src/main/java/com/ohgiraffers/dalryeo/record/controller/WeeklySummaryController.java`
- 수정: `src/main/java/com/ohgiraffers/dalryeo/analysis/controller/AnalysisController.java`
- 수정: `src/main/java/com/ohgiraffers/dalryeo/ranking/controller/RankingController.java`
- 수정: `src/main/java/com/ohgiraffers/dalryeo/weeklytier/controller/WeeklyTierController.java`
- 테스트: `src/test/java/com/ohgiraffers/dalryeo/integration/ApiContractIntegrationTest.java`

- [ ] **1단계: 보호 Controller의 일반 JWT 검증을 교체**

위 Controller들의 private helper 조건을 아래에서:

```java
if (token == null || !jwtTokenProvider.validateToken(token)) {
    throw new AuthException(AuthErrorCode.REFRESH_TOKEN_EXPIRED);
}
```

아래처럼 변경한다.

```java
if (token == null || !jwtTokenProvider.validateAccessToken(token)) {
    throw new AuthException(AuthErrorCode.REFRESH_TOKEN_EXPIRED);
}
```

랭킹 목록 조회 endpoint나 닉네임 중복 체크처럼 `extractUserIdFromRequest()`를 호출하지 않는 public endpoint는 수정하지 않는다.

- [ ] **2단계: 보호 Controller에 일반 검증이 남아 있지 않은지 확인**

실행:

```bash
rg -n "validateToken\\(token\\)" src/main/java/com/ohgiraffers/dalryeo
```

예상 결과: Controller의 `extractUserIdFromRequest()` 메서드 안에서는 매칭 결과가 없어야 한다. `validateToken()` 자체는 일반 검증과 내부 호환 용도로 `JwtTokenProvider`에 남아 있을 수 있다.

- [ ] **3단계: API 계약 통합 테스트 실행**

실행:

```bash
./gradlew test --tests '*ApiContractIntegrationTest'
```

예상 결과: `ApiContractIntegrationTest`가 통과한다. 아래 테스트도 포함해 통과해야 한다.

- `refreshToken_returnsUnauthorizedWhenAccessTokenIsSubmitted`
- `protectedApi_returnsUnauthorizedWhenRefreshTokenIsUsedAsBearerToken`

---

### 작업 7: 추적 문서 갱신

**파일:**
- 수정: `docs/archive/auth/jwt-token-dependency-security-improvement.md`

- [ ] **1단계: 완료된 구현 항목 체크**

각 항목에 해당하는 테스트가 통과한 뒤, 체크리스트를 아래처럼 변경한다.

```markdown
- [x] `nimbus-jose-jwt`를 patched version으로 올린다.
- [x] 의존성 변경만으로 Apple OAuth 테스트를 실행한다.
- [x] JWT에 `token_use` claim을 추가한다.
- [x] access/refresh token 용도 검증 메서드를 분리한다.
- [x] 보호 API가 access token만 허용하도록 바꾼다.
- [x] refresh API가 refresh token만 허용하도록 바꾼다.
- [x] refresh token으로 보호 API 호출 시 401 테스트를 추가한다.
- [x] access token으로 refresh API 호출 시 401 테스트를 추가한다.
- [x] 전체 인증/계약 테스트를 실행한다.
```

- [ ] **2단계: 진행 기록 추가**

진행 기록 표에 아래 행을 추가한다.

```markdown
| 2026-05-08 | 구현 완료 | Nimbus JOSE JWT를 patched version으로 올리고, JWT `token_use` 기반 access/refresh 용도 검증과 API 계약 테스트를 추가했다. |
```

이 행은 작업 8의 대상 검증이 통과한 뒤에만 추가한다. 작업 8의 전체 회귀 테스트를 실행할 수 없다면 상태를 `구현 완료`가 아니라 `검증 중`으로 쓰고, 진행 기록에 전체 회귀 테스트 미실행 사유를 남긴다.

---

### 작업 8: 최종 검증

**파일:**
- 수정된 모든 구현 파일과 테스트 파일을 검증한다.

- [ ] **1단계: 인증/계약 대상 테스트 실행**

실행:

```bash
./gradlew test --tests '*JwtTokenProviderTest' --tests '*AuthServiceTest' --tests '*ApiContractIntegrationTest' --tests '*AppleOAuthValidatorTest' --tests '*AppleJwkProviderTest'
```

예상 결과: 선택된 모든 테스트가 통과한다.

- [ ] **2단계: 환경이 가능하면 전체 회귀 테스트 실행**

실행:

```bash
./gradlew test --rerun-tasks
```

예상 결과: 전체 테스트 스위트가 통과한다.

로컬 PostgreSQL 테스트 DB를 사용할 수 없거나 인증 정보가 없어 실패한다면 즉시 멈추고 정확한 실패 원인을 보고한다. 전체 회귀 테스트가 통과했다고 말하지 않는다.

- [ ] **3단계: 변경 파일 검토**

실행:

```bash
git diff -- build.gradle src/main/java/com/ohgiraffers/dalryeo/auth/jwt/JwtTokenProvider.java src/main/java/com/ohgiraffers/dalryeo/auth/service/AuthService.java src/main/java/com/ohgiraffers/dalryeo/auth/controller/AuthController.java src/main/java/com/ohgiraffers/dalryeo/onboarding/controller/OnboardingController.java src/main/java/com/ohgiraffers/dalryeo/record/controller/RecordController.java src/main/java/com/ohgiraffers/dalryeo/record/controller/WeeklySummaryController.java src/main/java/com/ohgiraffers/dalryeo/analysis/controller/AnalysisController.java src/main/java/com/ohgiraffers/dalryeo/ranking/controller/RankingController.java src/main/java/com/ohgiraffers/dalryeo/weeklytier/controller/WeeklyTierController.java src/test/java/com/ohgiraffers/dalryeo/auth/jwt/JwtTokenProviderTest.java src/test/java/com/ohgiraffers/dalryeo/auth/service/AuthServiceTest.java src/test/java/com/ohgiraffers/dalryeo/integration/ApiContractIntegrationTest.java docs/archive/auth/jwt-token-dependency-security-improvement.md
```

예상 결과: diff에는 범위가 제한된 의존성 업그레이드, JWT 용도 claim/검증, 검증 호출 지점 변경, 테스트, 추적 문서 갱신만 포함되어야 한다.

---

## 자체 검토

- 명세 반영: Nimbus 업그레이드, `token_use` claim 추가, access/refresh 검증 분리, 보호 API의 access-only 적용, refresh endpoint의 refresh-only 적용, 오용 테스트, 검증 계획을 포함한다.
- 빈 표현 점검: 금지된 placeholder 표현이나 모호한 구현 단계가 남아 있지 않다.
- 타입 일관성: `validateAccessToken(String)`, `validateRefreshToken(String)`, `token_use`, `access`, `refresh`를 일관되게 사용한다.
- 범위 점검: Spring Security, Redis, DB 스키마 변경, Apple OAuth 재설계, 멀티 디바이스 세션 정책 변경은 제외한다.
