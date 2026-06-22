# Apple Identity Token 검증 Implementation Plan

- Status: Archived
- Audience: Engineers
- Source of Truth: No
- Last Reviewed: 2026-06-16

> 이 문서는 완료된 Apple identity token 검증 구현 계획이다. 현재 결정 기준은 `docs/decisions/apple-identity-token-validation.md`와 `docs/domains/auth.md`를 우선 확인한다.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Apple identity token을 Apple JWK 기반으로 검증해 실제 Apple이 우리 앱 대상으로 발급한 만료되지 않은 토큰만 로그인에 사용하게 한다.

**Architecture:** `AppleOAuthValidator`는 identity token 검증 흐름을 담당하고, `AppleJwkProvider`는 Apple JWK 조회와 인메모리 캐시를 담당한다. 설정값은 `AppleOAuthProperties`로 묶고, 시간 테스트는 `Clock` 주입으로 처리한다.

**Tech Stack:** Spring Boot 3.2.3, Java 17, Nimbus JOSE JWT 9.37.3, Spring `RestTemplateBuilder`, JUnit 5, Mockito, AssertJ.

---

## File Structure

- Create: `src/main/java/com/ohgiraffers/dalryeo/config/AppleOAuthProperties.java`
  - `app.oauth.apple` 설정 바인딩 담당
  - `clientId`, `jwkSetUri`, `jwkCacheTtl`, `jwkReadTimeout`, `clockSkew` 보관

- Create: `src/main/java/com/ohgiraffers/dalryeo/config/ClockConfig.java`
  - 운영 코드에서 사용할 `Clock.systemUTC()` Bean 제공

- Create: `src/main/java/com/ohgiraffers/dalryeo/auth/oauth/AppleJwkProvider.java`
  - Apple JWK 조회, TTL 캐시, `kid` 기반 JWK 선택 담당
  - 테스트용 package-private 생성자로 fake fetcher 주입 가능하게 구성

- Modify: `src/main/java/com/ohgiraffers/dalryeo/auth/oauth/AppleOAuthValidator.java`
  - 기존 `JWTParser.parse()` 후 `sub` 추출만 하던 구현을 서명/claim 검증으로 교체

- Modify: `src/main/resources/application.yml`
  - `app.oauth.apple.client-id`, `jwk-set-uri`, `jwk-cache-ttl`, `jwk-read-timeout`, `clock-skew` 추가
  - 미사용 `dev-mode`, `dev-apple-id-prefix` 제거

- Modify: `src/test/resources/application-test.yml`
  - 테스트용 `app.oauth.apple.client-id: test.apple.client` 추가
  - 주의: 로컬 전용 DB URL 변경은 커밋하지 않는다.

- Create: `src/test/java/com/ohgiraffers/dalryeo/auth/oauth/AppleJwkProviderTest.java`
  - JWK 캐시, `kid` miss 재조회, 조회 실패, stale 캐시 거부 검증

- Create: `src/test/java/com/ohgiraffers/dalryeo/auth/oauth/AppleOAuthValidatorTest.java`
  - 서명, `iss`, `aud`, `exp`, `sub`, `kid` 검증

- Modify: `src/test/java/com/ohgiraffers/dalryeo/auth/service/AuthServiceTest.java`
  - validator 실패가 `AuthException(AuthErrorCode.OAUTH_TOKEN_VERIFICATION_FAILED)`로 변환되는지 검증

- Modify: `src/test/java/com/ohgiraffers/dalryeo/integration/ApiContractIntegrationTest.java`
  - Apple 로그인 검증 실패 API 응답이 `AC-003`인지 검증

---

### Task 1: Apple OAuth 설정 바인딩 추가

**Files:**
- Create: `src/main/java/com/ohgiraffers/dalryeo/config/AppleOAuthProperties.java`
- Create: `src/main/java/com/ohgiraffers/dalryeo/config/ClockConfig.java`
- Modify: `src/main/resources/application.yml`
- Modify: `src/test/resources/application-test.yml`

- [ ] **Step 1: 설정 테스트 없이 컴파일 가능한 설정 클래스부터 추가**

Create `src/main/java/com/ohgiraffers/dalryeo/config/AppleOAuthProperties.java`:

```java
package com.ohgiraffers.dalryeo.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.oauth.apple")
public class AppleOAuthProperties {

    private String clientId;
    private String jwkSetUri = "https://appleid.apple.com/auth/keys";
    private Duration jwkCacheTtl = Duration.ofHours(24);
    private Duration jwkReadTimeout = Duration.ofSeconds(2);
    private Duration clockSkew = Duration.ofSeconds(60);
}
```

Create `src/main/java/com/ohgiraffers/dalryeo/config/ClockConfig.java`:

```java
package com.ohgiraffers.dalryeo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
```

- [ ] **Step 2: 운영 설정 추가와 dev-mode 제거**

Modify `src/main/resources/application.yml`:

```yaml
app:
  public-base-url: ${APP_PUBLIC_BASE_URL:https://api.dalryeo.store}
  oauth:
    apple:
      client-id: ${APP_OAUTH_APPLE_CLIENT_ID}
      jwk-set-uri: https://appleid.apple.com/auth/keys
      jwk-cache-ttl: 24h
      jwk-read-timeout: 2s
      clock-skew: 60s
  profile-image:
    upload-dir: ${PROFILE_IMAGE_UPLOAD_DIR:uploads/profile-images}
    url-prefix: /profiles/custom
```

- [ ] **Step 3: 테스트 설정 추가**

Modify `src/test/resources/application-test.yml`.

If the local file currently contains a local DB URL, preserve the working copy for local execution but do not commit that hunk. The committed datasource URL must remain:

```yaml
spring:
  datasource:
    url: ${TEST_DB_URL:${DB_URL:}}
```

Add Apple test config under existing `app:`:

```yaml
app:
  public-base-url: https://api.dalryeo.store
  oauth:
    apple:
      client-id: test.apple.client
      jwk-set-uri: https://appleid.apple.com/auth/keys
      jwk-cache-ttl: 24h
      jwk-read-timeout: 2s
      clock-skew: 60s
  profile-image:
    upload-dir: build/test-uploads/profile-images
    url-prefix: /profiles/custom
```

- [ ] **Step 4: Compile check**

Run:

```bash
./gradlew compileJava compileTestJava
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 5: Commit**

Stage only intended config and Java files. Do not stage local DB URL changes.

Run:

```bash
git add src/main/java/com/ohgiraffers/dalryeo/config/AppleOAuthProperties.java \
  src/main/java/com/ohgiraffers/dalryeo/config/ClockConfig.java \
  src/main/resources/application.yml
git add -p src/test/resources/application-test.yml
git commit -m "config: Apple OAuth 검증 설정 추가" -m "Apple identity token 검증에 필요한 client id와 JWK 설정을 추가한다"
```

Expected:

```text
[security/apple-identity-token-validation <sha>] config: Apple OAuth 검증 설정 추가
```

---

### Task 2: AppleJwkProvider 캐시와 조회 정책 구현

**Files:**
- Create: `src/main/java/com/ohgiraffers/dalryeo/auth/oauth/AppleJwkProvider.java`
- Create: `src/test/java/com/ohgiraffers/dalryeo/auth/oauth/AppleJwkProviderTest.java`

- [ ] **Step 1: Write failing provider tests**

Create `src/test/java/com/ohgiraffers/dalryeo/auth/oauth/AppleJwkProviderTest.java`:

```java
package com.ohgiraffers.dalryeo.auth.oauth;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.ohgiraffers.dalryeo.config.AppleOAuthProperties;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppleJwkProviderTest {

    @Test
    void getByKeyId_returnsCachedKeyWithoutRefetchWithinTtl() throws Exception {
        RSAKey key = rsaKey("kid-1");
        AtomicInteger fetchCount = new AtomicInteger();
        AppleJwkProvider provider = provider(
                fixedClock("2026-05-04T00:00:00Z"),
                () -> {
                    fetchCount.incrementAndGet();
                    return new JWKSet(key);
                }
        );

        JWK first = provider.getByKeyId("kid-1");
        JWK second = provider.getByKeyId("kid-1");

        assertThat(first.getKeyID()).isEqualTo("kid-1");
        assertThat(second.getKeyID()).isEqualTo("kid-1");
        assertThat(fetchCount.get()).isEqualTo(1);
    }

    @Test
    void getByKeyId_refetchesWhenKidIsMissingFromValidCache() throws Exception {
        RSAKey oldKey = rsaKey("old-kid");
        RSAKey rotatedKey = rsaKey("rotated-kid");
        AtomicInteger fetchCount = new AtomicInteger();
        AppleJwkProvider provider = provider(
                fixedClock("2026-05-04T00:00:00Z"),
                () -> {
                    int count = fetchCount.incrementAndGet();
                    return count == 1 ? new JWKSet(oldKey) : new JWKSet(List.of(oldKey, rotatedKey));
                }
        );

        provider.getByKeyId("old-kid");
        JWK result = provider.getByKeyId("rotated-kid");

        assertThat(result.getKeyID()).isEqualTo("rotated-kid");
        assertThat(fetchCount.get()).isEqualTo(2);
    }

    @Test
    void getByKeyId_throwsWhenFetchFailsWithoutValidCache() {
        AppleJwkProvider provider = provider(
                fixedClock("2026-05-04T00:00:00Z"),
                () -> {
                    throw new IllegalStateException("Apple JWK fetch failed");
                }
        );

        assertThatThrownBy(() -> provider.getByKeyId("kid-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Apple JWK fetch failed");
    }

    @Test
    void getByKeyId_throwsWhenCacheIsStaleAndRefreshFails() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-05-04T00:00:00Z"));
        RSAKey key = rsaKey("kid-1");
        AtomicInteger fetchCount = new AtomicInteger();
        AppleJwkProvider provider = provider(
                clock,
                () -> {
                    int count = fetchCount.incrementAndGet();
                    if (count == 1) {
                        return new JWKSet(key);
                    }
                    throw new IllegalStateException("Apple JWK fetch failed");
                }
        );

        provider.getByKeyId("kid-1");
        clock.moveTo(Instant.parse("2026-05-05T00:00:01Z"));

        assertThatThrownBy(() -> provider.getByKeyId("kid-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Apple JWK fetch failed");
    }

    private AppleJwkProvider provider(Clock clock, AppleJwkProvider.JwkSetFetcher fetcher) {
        AppleOAuthProperties properties = new AppleOAuthProperties();
        properties.setJwkCacheTtl(Duration.ofHours(24));
        properties.setJwkReadTimeout(Duration.ofSeconds(2));
        properties.setJwkSetUri("https://appleid.apple.com/auth/keys");
        return new AppleJwkProvider(properties, clock, fetcher);
    }

    private Clock fixedClock(String instant) {
        return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
    }

    private RSAKey rsaKey(String keyId) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        return new RSAKey.Builder((java.security.interfaces.RSAPublicKey) keyPair.getPublic())
                .privateKey((java.security.interfaces.RSAPrivateKey) keyPair.getPrivate())
                .keyID(keyId)
                .build();
    }

    private static class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void moveTo(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
```

- [ ] **Step 2: Run provider tests and verify RED**

Run:

```bash
./gradlew test --tests com.ohgiraffers.dalryeo.auth.oauth.AppleJwkProviderTest
```

Expected:

```text
Compilation failed because AppleJwkProvider does not exist
```

- [ ] **Step 3: Implement AppleJwkProvider**

Create `src/main/java/com/ohgiraffers/dalryeo/auth/oauth/AppleJwkProvider.java`:

```java
package com.ohgiraffers.dalryeo.auth.oauth;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.ohgiraffers.dalryeo.config.AppleOAuthProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;

@Slf4j
@Component
public class AppleJwkProvider {

    private final AppleOAuthProperties properties;
    private final Clock clock;
    private final JwkSetFetcher jwkSetFetcher;
    private JWKSet cachedJwkSet;
    private Instant cachedAt;

    public AppleJwkProvider(
            AppleOAuthProperties properties,
            RestTemplateBuilder restTemplateBuilder,
            Clock clock
    ) {
        this(
                properties,
                clock,
                new RestTemplateJwkSetFetcher(properties, restTemplateBuilder)
        );
    }

    AppleJwkProvider(
            AppleOAuthProperties properties,
            Clock clock,
            JwkSetFetcher jwkSetFetcher
    ) {
        this.properties = properties;
        this.clock = clock;
        this.jwkSetFetcher = jwkSetFetcher;
    }

    public synchronized JWK getByKeyId(String keyId) {
        if (!StringUtils.hasText(keyId)) {
            log.warn("Apple identity token validation failed. reason=missing_kid");
            throw new IllegalArgumentException("Apple identity token kid is missing.");
        }

        JWK cachedKey = findValidCachedKey(keyId);
        if (cachedKey != null) {
            return cachedKey;
        }

        JWKSet refreshedJwkSet = fetchAndCache();
        JWK refreshedKey = refreshedJwkSet.getKeyByKeyId(keyId);
        if (refreshedKey == null) {
            log.warn("Apple identity token validation failed. reason=kid_not_found kid={}", keyId);
            throw new IllegalArgumentException("Apple JWK kid not found.");
        }
        return refreshedKey;
    }

    private JWK findValidCachedKey(String keyId) {
        if (cachedJwkSet == null || cachedAt == null) {
            return null;
        }
        Instant expiresAt = cachedAt.plus(properties.getJwkCacheTtl());
        if (!clock.instant().isBefore(expiresAt)) {
            return null;
        }
        return cachedJwkSet.getKeyByKeyId(keyId);
    }

    private JWKSet fetchAndCache() {
        try {
            JWKSet jwkSet = jwkSetFetcher.fetch();
            cachedJwkSet = jwkSet;
            cachedAt = clock.instant();
            return jwkSet;
        } catch (RuntimeException e) {
            log.error("Apple JWK fetch failed.", e);
            throw e;
        }
    }

    @FunctionalInterface
    interface JwkSetFetcher {
        JWKSet fetch();
    }

    private static class RestTemplateJwkSetFetcher implements JwkSetFetcher {

        private final AppleOAuthProperties properties;
        private final RestTemplate restTemplate;

        private RestTemplateJwkSetFetcher(
                AppleOAuthProperties properties,
                RestTemplateBuilder restTemplateBuilder
        ) {
            this.properties = properties;
            this.restTemplate = restTemplateBuilder
                    .setConnectTimeout(properties.getJwkReadTimeout())
                    .setReadTimeout(properties.getJwkReadTimeout())
                    .build();
        }

        @Override
        public JWKSet fetch() {
            try {
                String body = restTemplate.getForObject(properties.getJwkSetUri(), String.class);
                if (!StringUtils.hasText(body)) {
                    throw new IllegalStateException("Apple JWK response body is empty.");
                }
                return JWKSet.parse(body);
            } catch (RestClientException | ParseException e) {
                throw new IllegalStateException("Apple JWK fetch failed", e);
            }
        }
    }
}
```

- [ ] **Step 4: Run provider tests and verify GREEN**

Run:

```bash
./gradlew test --tests com.ohgiraffers.dalryeo.auth.oauth.AppleJwkProviderTest
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 5: Commit**

Run:

```bash
git add src/main/java/com/ohgiraffers/dalryeo/auth/oauth/AppleJwkProvider.java \
  src/test/java/com/ohgiraffers/dalryeo/auth/oauth/AppleJwkProviderTest.java
git commit -m "feat: Apple JWK 조회 캐시 추가" -m "Apple JWK를 인메모리 TTL 캐시로 관리하고 kid miss 시 재조회한다"
```

Expected:

```text
[security/apple-identity-token-validation <sha>] feat: Apple JWK 조회 캐시 추가
```

---

### Task 3: AppleOAuthValidator 서명과 claim 검증 구현

**Files:**
- Modify: `src/main/java/com/ohgiraffers/dalryeo/auth/oauth/AppleOAuthValidator.java`
- Create: `src/test/java/com/ohgiraffers/dalryeo/auth/oauth/AppleOAuthValidatorTest.java`

- [ ] **Step 1: Write failing validator tests**

Create `src/test/java/com/ohgiraffers/dalryeo/auth/oauth/AppleOAuthValidatorTest.java`:

```java
package com.ohgiraffers.dalryeo.auth.oauth;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.ohgiraffers.dalryeo.config.AppleOAuthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AppleOAuthValidatorTest {

    private static final String CLIENT_ID = "test.apple.client";
    private static final String ISSUER = "https://appleid.apple.com";
    private static final String SUBJECT = "apple-sub-123";

    private AppleOAuthProperties properties;
    private AppleJwkProvider appleJwkProvider;
    private Clock clock;
    private RSAKey validKey;

    @BeforeEach
    void setUp() throws Exception {
        properties = new AppleOAuthProperties();
        properties.setClientId(CLIENT_ID);
        properties.setClockSkew(Duration.ofSeconds(60));
        clock = Clock.fixed(Instant.parse("2026-05-04T00:00:00Z"), ZoneOffset.UTC);
        validKey = rsaKey("kid-1");
        appleJwkProvider = mock(AppleJwkProvider.class);
    }

    @Test
    void validateAndExtractAppleId_returnsSubjectWhenTokenIsValid() throws Exception {
        when(appleJwkProvider.getByKeyId("kid-1")).thenReturn(validKey.toPublicJWK());
        AppleOAuthValidator validator = new AppleOAuthValidator(appleJwkProvider, properties, clock);
        String identityToken = token(validKey, "kid-1", ISSUER, CLIENT_ID, SUBJECT, "2026-05-04T00:10:00Z");

        String appleId = validator.validateAndExtractAppleId(identityToken);

        assertThat(appleId).isEqualTo(SUBJECT);
    }

    @Test
    void validateAndExtractAppleId_throwsWhenTokenFormatIsInvalid() {
        AppleOAuthValidator validator = new AppleOAuthValidator(appleJwkProvider, properties, clock);

        assertThatThrownBy(() -> validator.validateAndExtractAppleId("not-a-jwt"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void validateAndExtractAppleId_throwsWhenKidIsMissing() throws Exception {
        AppleOAuthValidator validator = new AppleOAuthValidator(appleJwkProvider, properties, clock);
        String identityToken = token(validKey, null, ISSUER, CLIENT_ID, SUBJECT, "2026-05-04T00:10:00Z");

        assertThatThrownBy(() -> validator.validateAndExtractAppleId(identityToken))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void validateAndExtractAppleId_throwsWhenSignatureDoesNotMatch() throws Exception {
        RSAKey otherKey = rsaKey("kid-1");
        when(appleJwkProvider.getByKeyId("kid-1")).thenReturn(otherKey.toPublicJWK());
        AppleOAuthValidator validator = new AppleOAuthValidator(appleJwkProvider, properties, clock);
        String identityToken = token(validKey, "kid-1", ISSUER, CLIENT_ID, SUBJECT, "2026-05-04T00:10:00Z");

        assertThatThrownBy(() -> validator.validateAndExtractAppleId(identityToken))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void validateAndExtractAppleId_throwsWhenIssuerIsInvalid() throws Exception {
        when(appleJwkProvider.getByKeyId("kid-1")).thenReturn(validKey.toPublicJWK());
        AppleOAuthValidator validator = new AppleOAuthValidator(appleJwkProvider, properties, clock);
        String identityToken = token(validKey, "kid-1", "https://attacker.example", CLIENT_ID, SUBJECT, "2026-05-04T00:10:00Z");

        assertThatThrownBy(() -> validator.validateAndExtractAppleId(identityToken))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void validateAndExtractAppleId_throwsWhenAudienceIsInvalid() throws Exception {
        when(appleJwkProvider.getByKeyId("kid-1")).thenReturn(validKey.toPublicJWK());
        AppleOAuthValidator validator = new AppleOAuthValidator(appleJwkProvider, properties, clock);
        String identityToken = token(validKey, "kid-1", ISSUER, "wrong.client", SUBJECT, "2026-05-04T00:10:00Z");

        assertThatThrownBy(() -> validator.validateAndExtractAppleId(identityToken))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void validateAndExtractAppleId_throwsWhenTokenIsExpiredBeyondClockSkew() throws Exception {
        when(appleJwkProvider.getByKeyId("kid-1")).thenReturn(validKey.toPublicJWK());
        AppleOAuthValidator validator = new AppleOAuthValidator(appleJwkProvider, properties, clock);
        String identityToken = token(validKey, "kid-1", ISSUER, CLIENT_ID, SUBJECT, "2026-05-03T23:58:59Z");

        assertThatThrownBy(() -> validator.validateAndExtractAppleId(identityToken))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void validateAndExtractAppleId_allowsTokenExpiredWithinClockSkew() throws Exception {
        when(appleJwkProvider.getByKeyId("kid-1")).thenReturn(validKey.toPublicJWK());
        AppleOAuthValidator validator = new AppleOAuthValidator(appleJwkProvider, properties, clock);
        String identityToken = token(validKey, "kid-1", ISSUER, CLIENT_ID, SUBJECT, "2026-05-03T23:59:30Z");

        String appleId = validator.validateAndExtractAppleId(identityToken);

        assertThat(appleId).isEqualTo(SUBJECT);
    }

    @Test
    void validateAndExtractAppleId_throwsWhenSubjectIsMissing() throws Exception {
        when(appleJwkProvider.getByKeyId("kid-1")).thenReturn(validKey.toPublicJWK());
        AppleOAuthValidator validator = new AppleOAuthValidator(appleJwkProvider, properties, clock);
        String identityToken = token(validKey, "kid-1", ISSUER, CLIENT_ID, null, "2026-05-04T00:10:00Z");

        assertThatThrownBy(() -> validator.validateAndExtractAppleId(identityToken))
                .isInstanceOf(RuntimeException.class);
    }

    private String token(
            RSAKey key,
            String kid,
            String issuer,
            String audience,
            String subject,
            String expiresAt
    ) throws Exception {
        JWSHeader.Builder headerBuilder = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(JOSEObjectType.JWT);
        if (kid != null) {
            headerBuilder.keyID(kid);
        }

        JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(List.of(audience))
                .expirationTime(Date.from(Instant.parse(expiresAt)));
        if (subject != null) {
            claimsBuilder.subject(subject);
        }

        SignedJWT signedJWT = new SignedJWT(headerBuilder.build(), claimsBuilder.build());
        signedJWT.sign(new RSASSASigner(key.toPrivateKey()));
        return signedJWT.serialize();
    }

    private RSAKey rsaKey(String keyId) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        return new RSAKey.Builder((java.security.interfaces.RSAPublicKey) keyPair.getPublic())
                .privateKey((java.security.interfaces.RSAPrivateKey) keyPair.getPrivate())
                .keyID(keyId)
                .build();
    }
}
```

- [ ] **Step 2: Run validator tests and verify RED**

Run:

```bash
./gradlew test --tests com.ohgiraffers.dalryeo.auth.oauth.AppleOAuthValidatorTest
```

Expected:

```text
Tests fail because AppleOAuthValidator still parses sub without signature and claim validation
```

- [ ] **Step 3: Implement AppleOAuthValidator**

Replace `src/main/java/com/ohgiraffers/dalryeo/auth/oauth/AppleOAuthValidator.java` with:

```java
package com.ohgiraffers.dalryeo.auth.oauth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.ohgiraffers.dalryeo.config.AppleOAuthProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AppleOAuthValidator {

    private static final String APPLE_ISSUER = "https://appleid.apple.com";

    private final AppleJwkProvider appleJwkProvider;
    private final AppleOAuthProperties properties;
    private final Clock clock;

    public String validateAndExtractAppleId(String identityToken) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(identityToken);
            String keyId = signedJWT.getHeader().getKeyID();
            if (!StringUtils.hasText(keyId)) {
                log.warn("Apple identity token validation failed. reason=missing_kid");
                throw new IllegalArgumentException("Apple identity token kid is missing.");
            }

            JWK jwk = appleJwkProvider.getByKeyId(keyId);
            if (!(jwk instanceof RSAKey rsaKey)) {
                log.warn("Apple identity token validation failed. reason=unsupported_jwk kid={}", keyId);
                throw new IllegalArgumentException("Apple JWK must be RSA.");
            }

            verifySignature(signedJWT, rsaKey, keyId);
            JWTClaimsSet claimsSet = signedJWT.getJWTClaimsSet();
            validateIssuer(claimsSet);
            validateAudience(claimsSet);
            validateExpiration(claimsSet);
            return validateSubject(claimsSet);
        } catch (ParseException e) {
            log.warn("Apple identity token validation failed. reason=invalid_jwt_format", e);
            throw new RuntimeException("Invalid Apple identityToken format", e);
        } catch (JOSEException e) {
            log.warn("Apple identity token validation failed. reason=signature_verification_error", e);
            throw new RuntimeException("Apple identityToken signature verification failed", e);
        }
    }

    private void verifySignature(SignedJWT signedJWT, RSAKey rsaKey, String keyId)
            throws JOSEException {
        boolean verified = signedJWT.verify(new RSASSAVerifier(rsaKey.toRSAPublicKey()));
        if (!verified) {
            log.warn("Apple identity token validation failed. reason=signature_verification_failed kid={}", keyId);
            throw new RuntimeException("Apple identityToken signature verification failed");
        }
    }

    private void validateIssuer(JWTClaimsSet claimsSet) {
        if (!APPLE_ISSUER.equals(claimsSet.getIssuer())) {
            log.warn("Apple identity token validation failed. reason=invalid_issuer iss={}", claimsSet.getIssuer());
            throw new RuntimeException("Apple identityToken issuer is invalid");
        }
    }

    private void validateAudience(JWTClaimsSet claimsSet) {
        List<String> audience = claimsSet.getAudience();
        if (audience == null || !audience.contains(properties.getClientId())) {
            log.warn("Apple identity token validation failed. reason=invalid_audience aud={}", audience);
            throw new RuntimeException("Apple identityToken audience is invalid");
        }
    }

    private void validateExpiration(JWTClaimsSet claimsSet) {
        Date expirationTime = claimsSet.getExpirationTime();
        if (expirationTime == null) {
            log.warn("Apple identity token validation failed. reason=missing_expiration");
            throw new RuntimeException("Apple identityToken expiration is missing");
        }

        Instant allowedUntil = expirationTime.toInstant().plus(properties.getClockSkew());
        if (allowedUntil.isBefore(clock.instant())) {
            log.warn("Apple identity token validation failed. reason=expired_token");
            throw new RuntimeException("Apple identityToken is expired");
        }
    }

    private String validateSubject(JWTClaimsSet claimsSet) {
        String appleId = claimsSet.getSubject();
        if (!StringUtils.hasText(appleId)) {
            log.warn("Apple identity token validation failed. reason=missing_subject");
            throw new RuntimeException("Apple ID not found in token");
        }
        return appleId;
    }
}
```

- [ ] **Step 4: Run validator tests and provider tests**

Run:

```bash
./gradlew test --tests com.ohgiraffers.dalryeo.auth.oauth.AppleOAuthValidatorTest --tests com.ohgiraffers.dalryeo.auth.oauth.AppleJwkProviderTest
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 5: Commit**

Run:

```bash
git add src/main/java/com/ohgiraffers/dalryeo/auth/oauth/AppleOAuthValidator.java \
  src/test/java/com/ohgiraffers/dalryeo/auth/oauth/AppleOAuthValidatorTest.java
git commit -m "feat: Apple identity token 검증 강화" -m "Apple JWK 서명 검증과 issuer, audience, expiration, subject 검증을 추가한다"
```

Expected:

```text
[security/apple-identity-token-validation <sha>] feat: Apple identity token 검증 강화
```

---

### Task 4: 로그인 실패 응답 계약 보강

**Files:**
- Modify: `src/test/java/com/ohgiraffers/dalryeo/auth/service/AuthServiceTest.java`
- Modify: `src/test/java/com/ohgiraffers/dalryeo/integration/ApiContractIntegrationTest.java`

- [ ] **Step 1: Write AuthService failure test**

Add to `src/test/java/com/ohgiraffers/dalryeo/auth/service/AuthServiceTest.java`:

```java
@Test
void loginWithApple_throwsAuthExceptionWhenAppleTokenVerificationFails() {
    String identityToken = "invalid-identity-token";

    when(appleOAuthValidator.validateAndExtractAppleId(identityToken))
            .thenThrow(new RuntimeException("Apple identityToken validation failed"));

    assertThatThrownBy(() -> authService.loginWithApple(identityToken))
            .isInstanceOf(AuthException.class)
            .extracting("errorCode")
            .isEqualTo(AuthErrorCode.OAUTH_TOKEN_VERIFICATION_FAILED);

    verify(oAuthClientRepository, never()).findByProviderAndProviderId(any(), any());
    verify(userRepository, never()).save(any(User.class));
    verify(authTokenRepository, never()).save(any(AuthToken.class));
}
```

- [ ] **Step 2: Write API failure contract test**

Add to `src/test/java/com/ohgiraffers/dalryeo/integration/ApiContractIntegrationTest.java` near the Apple login test:

```java
@Test
void loginWithApple_returnsUnauthorizedWhenIdentityTokenVerificationFails() throws Exception {
    when(appleOAuthValidator.validateAndExtractAppleId("invalid-identity-token"))
            .thenThrow(new RuntimeException("Apple identityToken validation failed"));

    mockMvc.perform(post("/auth/oauth/apple")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "identityToken": "invalid-identity-token"
                            }
                            """))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.data.code").value("AC-003"))
            .andExpect(jsonPath("$.data.message").value("OAuth 토큰 검증 실패"));
}
```

- [ ] **Step 3: Run new failure tests and verify expected result**

Run:

```bash
./gradlew test --tests com.ohgiraffers.dalryeo.auth.service.AuthServiceTest.loginWithApple_throwsAuthExceptionWhenAppleTokenVerificationFails --tests com.ohgiraffers.dalryeo.integration.ApiContractIntegrationTest.loginWithApple_returnsUnauthorizedWhenIdentityTokenVerificationFails
```

Expected:

```text
BUILD SUCCESSFUL
```

The tests should pass because `AuthService` already wraps validator exceptions as `AC-003`.

- [ ] **Step 4: Run related auth tests**

Run:

```bash
./gradlew test --tests com.ohgiraffers.dalryeo.auth.service.AuthServiceTest --tests com.ohgiraffers.dalryeo.integration.ApiContractIntegrationTest.loginWithApple_keepsTokenResponseContract --tests com.ohgiraffers.dalryeo.integration.ApiContractIntegrationTest.loginWithApple_returnsUnauthorizedWhenIdentityTokenVerificationFails
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 5: Commit**

Run:

```bash
git add src/test/java/com/ohgiraffers/dalryeo/auth/service/AuthServiceTest.java \
  src/test/java/com/ohgiraffers/dalryeo/integration/ApiContractIntegrationTest.java
git commit -m "test: Apple 로그인 검증 실패 응답 보강" -m "Apple identity token 검증 실패가 AC-003 응답으로 변환되는지 확인한다"
```

Expected:

```text
[security/apple-identity-token-validation <sha>] test: Apple 로그인 검증 실패 응답 보강
```

---

### Task 5: 최종 검증과 PR 준비

**Files:**
- Verify all changed files

- [ ] **Step 1: Check changed files**

Run:

```bash
git status --short
git diff --stat main..HEAD
```

Expected committed-code files:

```text
src/main/java/com/ohgiraffers/dalryeo/config/AppleOAuthProperties.java
src/main/java/com/ohgiraffers/dalryeo/config/ClockConfig.java
src/main/java/com/ohgiraffers/dalryeo/auth/oauth/AppleJwkProvider.java
src/main/java/com/ohgiraffers/dalryeo/auth/oauth/AppleOAuthValidator.java
src/main/resources/application.yml
src/test/resources/application-test.yml
src/test/java/com/ohgiraffers/dalryeo/auth/oauth/AppleJwkProviderTest.java
src/test/java/com/ohgiraffers/dalryeo/auth/oauth/AppleOAuthValidatorTest.java
src/test/java/com/ohgiraffers/dalryeo/auth/service/AuthServiceTest.java
src/test/java/com/ohgiraffers/dalryeo/integration/ApiContractIntegrationTest.java
```

Expected uncommitted local-only files may remain:

```text
AGENTS.md
docs/
.githooks/
.gitmessage.txt
scripts/
src/test/resources/application-test.yml local DB URL hunk
```

If `application-test.yml` is staged, inspect it before committing:

```bash
git diff --cached -- src/test/resources/application-test.yml
```

The staged diff must not include:

```yaml
url: jdbc:postgresql://localhost:5432/postgres
```

- [ ] **Step 2: Run whitespace check**

Run:

```bash
git diff --check
```

Expected:

```text
No output and exit code 0
```

- [ ] **Step 3: Run focused tests**

Run:

```bash
./gradlew test --tests com.ohgiraffers.dalryeo.auth.oauth.AppleJwkProviderTest --tests com.ohgiraffers.dalryeo.auth.oauth.AppleOAuthValidatorTest --tests com.ohgiraffers.dalryeo.auth.service.AuthServiceTest --tests com.ohgiraffers.dalryeo.integration.ApiContractIntegrationTest
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 4: Run full tests fresh**

Run:

```bash
./gradlew test --rerun-tasks
```

Expected:

```text
BUILD SUCCESSFUL
5 actionable tasks: 5 executed
```

- [ ] **Step 5: Push branch**

Run:

```bash
git push -u origin security/apple-identity-token-validation
```

Expected:

```text
security/apple-identity-token-validation -> security/apple-identity-token-validation
```

- [ ] **Step 6: PR body draft**

Use this PR body:

```markdown
## 작업 내용

- Apple identity token을 Apple JWK 기반으로 서명 검증
- iss, aud, exp, sub claim 검증 추가
- Apple JWK 인메모리 TTL 캐시와 kid miss 재조회 정책 추가
- 미사용 dev-mode 설정 제거
- 검증 실패 시 AC-003 응답 계약 유지

## 운영 설정

- APP_OAUTH_APPLE_CLIENT_ID 환경변수 필요

## 검증

- ./gradlew test --tests com.ohgiraffers.dalryeo.auth.oauth.AppleJwkProviderTest --tests com.ohgiraffers.dalryeo.auth.oauth.AppleOAuthValidatorTest --tests com.ohgiraffers.dalryeo.auth.service.AuthServiceTest --tests com.ohgiraffers.dalryeo.integration.ApiContractIntegrationTest
- ./gradlew test --rerun-tasks
```

---

## Self-Review Checklist

- Spec coverage:
  - JWK 서명 검증: Task 3
  - issuer/audience/expiration/subject 검증: Task 3
  - 60초 clock skew: Task 3
  - JWK 24시간 인메모리 캐시: Task 2
  - kid miss 재조회: Task 2
  - stale 캐시 미사용: Task 2
  - 2초 timeout, 재시도 없음: Task 2
  - dev-mode 제거: Task 1
  - AC-003 응답 유지: Task 4
  - 실제 Apple 네트워크 없는 테스트: Task 2, Task 3

- Placeholder scan:
  - This plan contains no unresolved placeholders or unspecified implementation steps.

- Type consistency:
  - `AppleOAuthProperties`, `AppleJwkProvider`, `AppleOAuthValidator` names are consistent across tasks.
  - `jwkSetUri`, `jwkCacheTtl`, `jwkReadTimeout`, `clockSkew`, `clientId` property names are consistent across config and Java snippets.
