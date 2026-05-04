package com.ohgiraffers.dalryeo.auth.oauth;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.ohgiraffers.dalryeo.config.AppleOAuthProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
public class AppleJwkProvider {

    private static final Duration KID_MISS_REFETCH_COOLDOWN = Duration.ofMinutes(5);
    private static final int MAX_LOGGED_KID_LENGTH = 64;

    private final AppleOAuthProperties properties;
    private final Clock clock;
    private final JwkSetFetcher jwkSetFetcher;

    private JWKSet cachedJwkSet;
    private Instant cacheExpiresAt;
    private Instant nextKidMissRefetchAllowedAt;

    @Autowired
    public AppleJwkProvider(
            AppleOAuthProperties properties,
            RestTemplateBuilder restTemplateBuilder,
            Clock clock
    ) {
        this(properties, clock, new RestTemplateJwkSetFetcher(properties, restTemplateBuilder));
    }

    AppleJwkProvider(AppleOAuthProperties properties, Clock clock, JwkSetFetcher jwkSetFetcher) {
        this.properties = properties;
        this.clock = clock;
        this.jwkSetFetcher = jwkSetFetcher;
    }

    public synchronized JWK getByKeyId(String keyId) {
        if (!StringUtils.hasText(keyId)) {
            log.warn("Apple JWK lookup failed. reason=missing_kid");
            throw new IllegalArgumentException("Apple JWK kid is required");
        }

        if (hasValidCache()) {
            JWK cachedKey = cachedJwkSet.getKeyByKeyId(keyId);
            if (cachedKey != null) {
                return requireRsa(cachedKey, keyId);
            }

            if (!canRefetchAfterKidMiss()) {
                log.warn("Apple JWK lookup failed. reason=kid_not_found kid={}", sanitizeKidForLog(keyId));
                throw new IllegalArgumentException("Apple JWK kid not found");
            }
        }

        JWKSet refreshedJwkSet = fetchAndCache();
        JWK refreshedKey = refreshedJwkSet.getKeyByKeyId(keyId);
        if (refreshedKey == null) {
            nextKidMissRefetchAllowedAt = clock.instant().plus(KID_MISS_REFETCH_COOLDOWN);
            log.warn("Apple JWK lookup failed. reason=kid_not_found kid={}", sanitizeKidForLog(keyId));
            throw new IllegalArgumentException("Apple JWK kid not found");
        }
        return requireRsa(refreshedKey, keyId);
    }

    private boolean hasValidCache() {
        return cachedJwkSet != null
                && cacheExpiresAt != null
                && clock.instant().isBefore(cacheExpiresAt);
    }

    private boolean canRefetchAfterKidMiss() {
        return nextKidMissRefetchAllowedAt == null
                || !clock.instant().isBefore(nextKidMissRefetchAllowedAt);
    }

    private JWKSet fetchAndCache() {
        try {
            JWKSet jwkSet = jwkSetFetcher.fetch();
            cachedJwkSet = jwkSet;
            cacheExpiresAt = clock.instant().plus(properties.getJwkCacheTtl());
            return jwkSet;
        } catch (RuntimeException e) {
            log.error("Apple JWK fetch failed.", e);
            throw e;
        }
    }

    private JWK requireRsa(JWK jwk, String keyId) {
        if (jwk instanceof com.nimbusds.jose.jwk.RSAKey) {
            return jwk;
        }

        log.warn("Apple JWK lookup failed. reason=non_rsa_jwk kid={}", sanitizeKidForLog(keyId));
        throw new IllegalArgumentException("Apple JWK kid must be RSA");
    }

    static String sanitizeKidForLog(String keyId) {
        String sanitized = keyId
                .replace('\r', '_')
                .replace('\n', '_')
                .replace('\t', '_');
        if (sanitized.length() <= MAX_LOGGED_KID_LENGTH) {
            return sanitized;
        }
        return sanitized.substring(0, MAX_LOGGED_KID_LENGTH);
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
                    throw new IllegalStateException("Apple JWK fetch failed: empty response body");
                }
                return JWKSet.parse(body);
            } catch (RestClientException | ParseException e) {
                throw new IllegalStateException("Apple JWK fetch failed", e);
            }
        }
    }
}

@FunctionalInterface
interface JwkSetFetcher {

    JWKSet fetch();
}
