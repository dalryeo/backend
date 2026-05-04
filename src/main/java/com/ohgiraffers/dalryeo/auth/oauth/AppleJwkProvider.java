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
    private Instant cacheExpiresAt;

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
                return cachedKey;
            }
        }

        JWKSet refreshedJwkSet = fetchAndCache();
        JWK refreshedKey = refreshedJwkSet.getKeyByKeyId(keyId);
        if (refreshedKey == null) {
            log.warn("Apple JWK lookup failed. reason=kid_not_found kid={}", keyId);
            throw new IllegalArgumentException("Apple JWK kid not found");
        }
        return refreshedKey;
    }

    private boolean hasValidCache() {
        return cachedJwkSet != null
                && cacheExpiresAt != null
                && clock.instant().isBefore(cacheExpiresAt);
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
