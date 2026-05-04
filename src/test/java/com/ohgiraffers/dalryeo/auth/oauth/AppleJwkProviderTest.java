package com.ohgiraffers.dalryeo.auth.oauth;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.ohgiraffers.dalryeo.config.AppleOAuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.client.MockServerRestTemplateCustomizer;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.MediaType;

import javax.crypto.spec.SecretKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AppleJwkProviderTest {

    @Test
    void getByKeyId_returnsCachedKeyWithoutRefetchWithinTtl() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-05-04T00:00:00Z"));
        AppleOAuthProperties properties = properties(Duration.ofHours(24));
        RSAKey key = rsaKey("kid-1");
        AtomicInteger fetchCount = new AtomicInteger();
        AppleJwkProvider provider = new AppleJwkProvider(
                properties,
                clock,
                () -> {
                    fetchCount.incrementAndGet();
                    return jwkSet(key);
                }
        );

        JWK first = provider.getByKeyId("kid-1");
        clock.advance(Duration.ofHours(23));
        JWK second = provider.getByKeyId("kid-1");

        assertThat(first).isSameAs(key);
        assertThat(second).isSameAs(key);
        assertThat(fetchCount).hasValue(1);
    }

    @Test
    void getByKeyId_refetchesWhenKidIsMissingFromValidCache() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-05-04T00:00:00Z"));
        AppleOAuthProperties properties = properties(Duration.ofHours(24));
        RSAKey oldKey = rsaKey("old-kid");
        RSAKey newKey = rsaKey("new-kid");
        AtomicInteger fetchCount = new AtomicInteger();
        AppleJwkProvider provider = new AppleJwkProvider(
                properties,
                clock,
                () -> fetchCount.incrementAndGet() == 1 ? jwkSet(oldKey) : jwkSet(oldKey, newKey)
        );

        assertThat(provider.getByKeyId("old-kid")).isSameAs(oldKey);
        JWK found = provider.getByKeyId("new-kid");

        assertThat(found).isSameAs(newKey);
        assertThat(fetchCount).hasValue(2);
    }

    @Test
    void getByKeyId_doesNotRepeatedlyRefetchUnknownKidDuringCooldown() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-05-04T00:00:00Z"));
        AppleOAuthProperties properties = properties(Duration.ofHours(24));
        RSAKey key = rsaKey("kid-1");
        AtomicInteger fetchCount = new AtomicInteger();
        AppleJwkProvider provider = new AppleJwkProvider(
                properties,
                clock,
                () -> {
                    fetchCount.incrementAndGet();
                    return jwkSet(key);
                }
        );

        assertThat(provider.getByKeyId("kid-1")).isSameAs(key);

        assertThatThrownBy(() -> provider.getByKeyId("unknown-kid-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Apple JWK kid not found");
        assertThatThrownBy(() -> provider.getByKeyId("unknown-kid-2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Apple JWK kid not found");

        assertThat(fetchCount).hasValue(2);
    }

    @Test
    void getByKeyId_doesNotRepeatedlyRefetchColdStartUnknownKidDuringCooldown() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-05-04T00:00:00Z"));
        AppleOAuthProperties properties = properties(Duration.ofHours(24));
        RSAKey key = rsaKey("kid-1");
        AtomicInteger fetchCount = new AtomicInteger();
        AppleJwkProvider provider = new AppleJwkProvider(
                properties,
                clock,
                () -> {
                    fetchCount.incrementAndGet();
                    return jwkSet(key);
                }
        );

        assertThatThrownBy(() -> provider.getByKeyId("unknown-kid-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Apple JWK kid not found");
        assertThatThrownBy(() -> provider.getByKeyId("unknown-kid-2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Apple JWK kid not found");

        assertThat(fetchCount).hasValue(1);
    }

    @Test
    void getByKeyId_refetchesUnknownKidAfterCooldownAndFindsRotatedKey() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-05-04T00:00:00Z"));
        AppleOAuthProperties properties = properties(Duration.ofHours(24));
        RSAKey oldKey = rsaKey("old-kid");
        RSAKey newKey = rsaKey("new-kid");
        AtomicInteger fetchCount = new AtomicInteger();
        AppleJwkProvider provider = new AppleJwkProvider(
                properties,
                clock,
                () -> fetchCount.incrementAndGet() < 3 ? jwkSet(oldKey) : jwkSet(oldKey, newKey)
        );

        assertThat(provider.getByKeyId("old-kid")).isSameAs(oldKey);
        assertThatThrownBy(() -> provider.getByKeyId("new-kid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Apple JWK kid not found");

        clock.advance(Duration.ofMinutes(5).plusSeconds(1));
        JWK found = provider.getByKeyId("new-kid");

        assertThat(found).isSameAs(newKey);
        assertThat(fetchCount).hasValue(3);
    }

    @Test
    void getByKeyId_throwsWhenMatchingKeyIsNotRsa() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-05-04T00:00:00Z"));
        AppleOAuthProperties properties = properties(Duration.ofHours(24));
        OctetSequenceKey nonRsaKey = octetSequenceKey("kid-1");
        AppleJwkProvider provider = new AppleJwkProvider(
                properties,
                clock,
                () -> jwkSet(nonRsaKey)
        );

        assertThatThrownBy(() -> provider.getByKeyId("kid-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Apple JWK kid must be RSA");
    }

    @Test
    void getByKeyId_throwsWhenKidIsBlankWithoutFetching() {
        MutableClock clock = new MutableClock(Instant.parse("2026-05-04T00:00:00Z"));
        AppleOAuthProperties properties = properties(Duration.ofHours(24));
        AtomicInteger fetchCount = new AtomicInteger();
        AppleJwkProvider provider = new AppleJwkProvider(
                properties,
                clock,
                () -> {
                    fetchCount.incrementAndGet();
                    return jwkSet();
                }
        );

        assertThatThrownBy(() -> provider.getByKeyId("   \t\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Apple JWK kid is required");
        assertThat(fetchCount).hasValue(0);
    }

    @Test
    void getByKeyId_throwsWhenFetchFailsWithoutValidCache() {
        MutableClock clock = new MutableClock(Instant.parse("2026-05-04T00:00:00Z"));
        AppleOAuthProperties properties = properties(Duration.ofHours(24));
        AtomicInteger fetchCount = new AtomicInteger();
        AppleJwkProvider provider = new AppleJwkProvider(
                properties,
                clock,
                () -> {
                    fetchCount.incrementAndGet();
                    throw new IllegalStateException("fetch failed");
                }
        );

        assertThatThrownBy(() -> provider.getByKeyId("kid-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("fetch failed");
        assertThat(fetchCount).hasValue(1);
    }

    @Test
    void getByKeyId_throwsWhenCacheIsStaleAndRefreshFails() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-05-04T00:00:00Z"));
        AppleOAuthProperties properties = properties(Duration.ofHours(24));
        RSAKey key = rsaKey("kid-1");
        AtomicInteger fetchCount = new AtomicInteger();
        AppleJwkProvider provider = new AppleJwkProvider(
                properties,
                clock,
                () -> {
                    if (fetchCount.incrementAndGet() == 1) {
                        return jwkSet(key);
                    }
                    throw new IllegalStateException("refresh failed");
                }
        );

        assertThat(provider.getByKeyId("kid-1")).isSameAs(key);
        clock.advance(Duration.ofHours(25));

        assertThatThrownBy(() -> provider.getByKeyId("kid-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("refresh failed");
        assertThat(fetchCount).hasValue(2);
    }

    @Test
    void getByKeyId_fetchesJwkSetWithRestTemplateBuilderConstructor() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-05-04T00:00:00Z"));
        AppleOAuthProperties properties = properties(Duration.ofHours(24));
        RSAKey key = rsaKey("kid-1");
        AppleJwkProvider provider = restTemplateProvider(properties, clock);

        mockServer()
                .expect(requestTo(properties.getJwkSetUri()))
                .andRespond(withSuccess(jwkSet(key).toPublicJWKSet().toString(), MediaType.APPLICATION_JSON));

        JWK found = provider.getByKeyId("kid-1");

        assertThat(found.getKeyID()).isEqualTo("kid-1");
        mockServer().verify();
    }

    @Test
    void getByKeyId_throwsWhenRestTemplateFetcherReceivesEmptyBody() {
        MutableClock clock = new MutableClock(Instant.parse("2026-05-04T00:00:00Z"));
        AppleOAuthProperties properties = properties(Duration.ofHours(24));
        AppleJwkProvider provider = restTemplateProvider(properties, clock);

        mockServer()
                .expect(requestTo(properties.getJwkSetUri()))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.getByKeyId("kid-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Apple JWK fetch failed: empty response body");
        mockServer().verify();
    }

    @Test
    void getByKeyId_throwsWhenRestTemplateFetcherReceivesMalformedJson() {
        MutableClock clock = new MutableClock(Instant.parse("2026-05-04T00:00:00Z"));
        AppleOAuthProperties properties = properties(Duration.ofHours(24));
        AppleJwkProvider provider = restTemplateProvider(properties, clock);

        mockServer()
                .expect(requestTo(properties.getJwkSetUri()))
                .andRespond(withSuccess("{not-json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.getByKeyId("kid-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Apple JWK fetch failed")
                .hasCauseInstanceOf(java.text.ParseException.class);
        mockServer().verify();
    }

    @Test
    void getByKeyId_throwsWhenRestTemplateFetcherReceivesServerError() {
        MutableClock clock = new MutableClock(Instant.parse("2026-05-04T00:00:00Z"));
        AppleOAuthProperties properties = properties(Duration.ofHours(24));
        AppleJwkProvider provider = restTemplateProvider(properties, clock);

        mockServer()
                .expect(requestTo(properties.getJwkSetUri()))
                .andRespond(withServerError());

        assertThatThrownBy(() -> provider.getByKeyId("kid-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Apple JWK fetch failed")
                .hasCauseInstanceOf(org.springframework.web.client.RestClientException.class);
        mockServer().verify();
    }

    @Test
    void sanitizeKidForLog_replacesControlSeparatorsAndTruncatesLongKid() {
        String longKid = "safe\rkid\nwith\tcontrols-" + "a".repeat(100);

        String sanitized = AppleJwkProvider.sanitizeKidForLog(longKid);

        assertThat(sanitized)
                .doesNotContain("\r", "\n", "\t")
                .startsWith("safe_kid_with_controls-")
                .hasSize(64);
    }

    private final MockServerRestTemplateCustomizer mockServerCustomizer = new MockServerRestTemplateCustomizer();

    private AppleJwkProvider restTemplateProvider(AppleOAuthProperties properties, Clock clock) {
        return new AppleJwkProvider(properties, new RestTemplateBuilder(mockServerCustomizer), clock);
    }

    private org.springframework.test.web.client.MockRestServiceServer mockServer() {
        return mockServerCustomizer.getServer();
    }

    private static AppleOAuthProperties properties(Duration jwkCacheTtl) {
        AppleOAuthProperties properties = new AppleOAuthProperties();
        properties.setClientId("test-client-id");
        properties.setJwkSetUri("https://apple.example.test/auth/keys");
        properties.setJwkCacheTtl(jwkCacheTtl);
        properties.setJwkReadTimeout(Duration.ofSeconds(2));
        return properties;
    }

    private static RSAKey rsaKey(String keyId) throws Exception {
        return new RSAKeyGenerator(2048)
                .keyID(keyId)
                .generate();
    }

    private static OctetSequenceKey octetSequenceKey(String keyId) {
        return new OctetSequenceKey.Builder(new SecretKeySpec("0123456789abcdef".getBytes(), "AES"))
                .keyID(keyId)
                .build();
    }

    private static JWKSet jwkSet(JWK... keys) {
        return new JWKSet(List.of(keys));
    }

    private static class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
