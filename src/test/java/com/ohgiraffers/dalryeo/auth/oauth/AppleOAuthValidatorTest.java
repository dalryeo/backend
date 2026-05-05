package com.ohgiraffers.dalryeo.auth.oauth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.ohgiraffers.dalryeo.auth.exception.AuthErrorCode;
import com.ohgiraffers.dalryeo.auth.exception.AuthException;
import com.ohgiraffers.dalryeo.config.AppleOAuthProperties;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppleOAuthValidatorTest {

    private static final Instant NOW = Instant.parse("2026-05-04T00:00:00Z");
    private static final String CLIENT_ID = "com.dalryeo.app";
    private static final String ISSUER = "https://appleid.apple.com";
    private static final String SUBJECT = "apple-subject-123";

    private RSAKey rsaKey;
    private AppleOAuthValidator validator;

    @BeforeEach
    void setUp() throws Exception {
        rsaKey = new RSAKeyGenerator(2048)
                .keyID("kid-1")
                .generate();
        validator = validatorWith(rsaKey);
    }

    @Test
    void validateAndExtractAppleId_returnsSubjectForValidSignedToken() throws Exception {
        String identityToken = signedToken(claimsBuilder(), rsaKey, "kid-1");

        String appleId = validator.validateAndExtractAppleId(identityToken);

        assertThat(appleId).isEqualTo(SUBJECT);
    }

    @Test
    void validateAndExtractAppleId_failsForInvalidJwtFormat() {
        assertAppleTokenVerificationFails(() -> validator.validateAndExtractAppleId("not-a-jwt"));
    }

    @Test
    void validateAndExtractAppleId_failsWhenKidIsMissing() throws Exception {
        String identityToken = signedToken(claimsBuilder(), rsaKey, null);

        assertAppleTokenVerificationFails(() -> validator.validateAndExtractAppleId(identityToken));
    }

    @Test
    void validateAndExtractAppleId_failsForSignatureMismatch() throws Exception {
        RSAKey otherKey = new RSAKeyGenerator(2048)
                .keyID("kid-1")
                .generate();
        String identityToken = signedToken(claimsBuilder(), otherKey, "kid-1");

        assertAppleTokenVerificationFails(() -> validator.validateAndExtractAppleId(identityToken));
    }

    @Test
    void validateAndExtractAppleId_failsForNonRs256Algorithm() throws Exception {
        String identityToken = signedToken(claimsBuilder(), rsaKey, "kid-1", JWSAlgorithm.RS512);

        assertAppleTokenVerificationFails(() -> validator.validateAndExtractAppleId(identityToken));
    }

    @Test
    void validateAndExtractAppleId_failsForInvalidIssuer() throws Exception {
        String identityToken = signedToken(claimsBuilder().issuer("https://attacker.example"), rsaKey, "kid-1");

        assertAppleTokenVerificationFails(() -> validator.validateAndExtractAppleId(identityToken));
    }

    @Test
    void validateAndExtractAppleId_failsForInvalidAudience() throws Exception {
        String identityToken = signedToken(claimsBuilder().audience("other-client-id"), rsaKey, "kid-1");

        assertAppleTokenVerificationFails(() -> validator.validateAndExtractAppleId(identityToken));
    }

    @Test
    void validateAndExtractAppleId_failsForExpiredTokenBeyondClockSkew() throws Exception {
        String identityToken = signedToken(
                claimsBuilder().expirationTime(Date.from(NOW.minusSeconds(61))),
                rsaKey,
                "kid-1"
        );

        assertAppleTokenVerificationFails(() -> validator.validateAndExtractAppleId(identityToken));
    }

    @Test
    void validateAndExtractAppleId_failsForIssuedAtInFutureBeyondClockSkew() throws Exception {
        String identityToken = signedToken(
                claimsBuilder().issueTime(Date.from(NOW.plusSeconds(61))),
                rsaKey,
                "kid-1"
        );

        assertAppleTokenVerificationFails(() -> validator.validateAndExtractAppleId(identityToken));
    }

    @Test
    void validateAndExtractAppleId_returnsSubjectForIssuedAtInFutureWithinClockSkew() throws Exception {
        String identityToken = signedToken(
                claimsBuilder().issueTime(Date.from(NOW.plusSeconds(60))),
                rsaKey,
                "kid-1"
        );

        String appleId = validator.validateAndExtractAppleId(identityToken);

        assertThat(appleId).isEqualTo(SUBJECT);
    }

    @Test
    void validateAndExtractAppleId_failsWhenIssuedAtIsMissing() throws Exception {
        String identityToken = signedToken(claimsBuilderWithoutIssueTime(), rsaKey, "kid-1");

        assertAppleTokenVerificationFails(() -> validator.validateAndExtractAppleId(identityToken));
    }

    @Test
    void validateAndExtractAppleId_returnsSubjectForExpiredTokenWithinClockSkew() throws Exception {
        String identityToken = signedToken(
                claimsBuilder().expirationTime(Date.from(NOW.minusSeconds(60))),
                rsaKey,
                "kid-1"
        );

        String appleId = validator.validateAndExtractAppleId(identityToken);

        assertThat(appleId).isEqualTo(SUBJECT);
    }

    @Test
    void validateAndExtractAppleId_failsWhenSubjectIsMissing() throws Exception {
        String identityToken = signedToken(claimsBuilder().subject(" "), rsaKey, "kid-1");

        assertAppleTokenVerificationFails(() -> validator.validateAndExtractAppleId(identityToken));
    }

    private void assertAppleTokenVerificationFails(ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOf(AuthException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.OAUTH_TOKEN_VERIFICATION_FAILED);
    }

    private AppleOAuthValidator validatorWith(RSAKey key) {
        AppleOAuthProperties properties = new AppleOAuthProperties();
        properties.setClientId(CLIENT_ID);
        properties.setClockSkew(Duration.ofSeconds(60));

        AppleJwkProvider jwkProvider = new AppleJwkProvider(
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC),
                () -> new JWKSet(List.of(key.toPublicJWK()))
        );
        return new AppleOAuthValidator(jwkProvider, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private JWTClaimsSet.Builder claimsBuilder() {
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(CLIENT_ID)
                .issueTime(Date.from(NOW.minusSeconds(30)))
                .expirationTime(Date.from(NOW.plusSeconds(300)))
                .subject(SUBJECT);
    }

    private JWTClaimsSet.Builder claimsBuilderWithoutIssueTime() {
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(CLIENT_ID)
                .expirationTime(Date.from(NOW.plusSeconds(300)))
                .subject(SUBJECT);
    }

    private String signedToken(JWTClaimsSet.Builder claimsBuilder, RSAKey signingKey, String keyId) throws Exception {
        return signedToken(claimsBuilder, signingKey, keyId, JWSAlgorithm.RS256);
    }

    private String signedToken(
            JWTClaimsSet.Builder claimsBuilder,
            RSAKey signingKey,
            String keyId,
            JWSAlgorithm algorithm
    ) throws Exception {
        JWSHeader.Builder headerBuilder = new JWSHeader.Builder(algorithm);
        if (keyId != null) {
            headerBuilder.keyID(keyId);
        }

        SignedJWT signedJWT = new SignedJWT(headerBuilder.build(), claimsBuilder.build());
        signedJWT.sign(new RSASSASigner(signingKey));
        return signedJWT.serialize();
    }
}
