package com.ohgiraffers.dalryeo.auth.oauth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
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
    private static final int MAX_LOGGED_CLAIM_LENGTH = 128;

    private final AppleJwkProvider appleJwkProvider;
    private final AppleOAuthProperties properties;
    private final Clock clock;

    public String validateAndExtractAppleId(String identityToken) {
        SignedJWT signedJWT = parse(identityToken);
        String keyId = signedJWT.getHeader().getKeyID();
        if (!StringUtils.hasText(keyId)) {
            log.warn("Apple identityToken validation failed. reason=missing_kid");
            throw new RuntimeException("Apple identityToken kid is required");
        }

        validateAlgorithm(signedJWT);

        RSAKey rsaKey = getRsaKey(keyId);
        verifySignature(signedJWT, rsaKey, keyId);

        JWTClaimsSet claimsSet = claims(signedJWT);
        validateIssuer(claimsSet);
        validateAudience(claimsSet);
        validateExpiration(claimsSet);
        return requireSubject(claimsSet);
    }

    private SignedJWT parse(String identityToken) {
        try {
            return SignedJWT.parse(identityToken);
        } catch (ParseException e) {
            log.warn("Apple identityToken validation failed. reason=invalid_format");
            throw new RuntimeException("Invalid Apple identityToken format", e);
        }
    }

    private void validateAlgorithm(SignedJWT signedJWT) {
        JWSAlgorithm algorithm = signedJWT.getHeader().getAlgorithm();
        if (!JWSAlgorithm.RS256.equals(algorithm)) {
            log.warn(
                    "Apple identityToken validation failed. reason=invalid_algorithm alg={}",
                    sanitizeClaimForLog(algorithm == null ? null : algorithm.getName())
            );
            throw new RuntimeException("Invalid Apple identityToken algorithm");
        }
    }

    private RSAKey getRsaKey(String keyId) {
        try {
            JWK jwk = appleJwkProvider.getByKeyId(keyId);
            if (jwk instanceof RSAKey rsaKey) {
                return rsaKey;
            }
        } catch (RuntimeException e) {
            log.warn(
                    "Apple identityToken validation failed. reason=jwk_lookup_failed kid={}",
                    AppleJwkProvider.sanitizeKidForLog(keyId)
            );
            throw e;
        }

        log.warn(
                "Apple identityToken validation failed. reason=non_rsa_jwk kid={}",
                AppleJwkProvider.sanitizeKidForLog(keyId)
        );
        throw new RuntimeException("Apple identityToken JWK must be RSA");
    }

    private void verifySignature(SignedJWT signedJWT, RSAKey rsaKey, String keyId) {
        try {
            boolean verified = signedJWT.verify(new RSASSAVerifier(rsaKey.toRSAPublicKey()));
            if (!verified) {
                log.warn(
                        "Apple identityToken validation failed. reason=invalid_signature kid={}",
                        AppleJwkProvider.sanitizeKidForLog(keyId)
                );
                throw new RuntimeException("Invalid Apple identityToken signature");
            }
        } catch (JOSEException e) {
            log.warn(
                    "Apple identityToken validation failed. reason=signature_verification_error kid={}",
                    AppleJwkProvider.sanitizeKidForLog(keyId)
            );
            throw new RuntimeException("Apple identityToken signature verification failed", e);
        }
    }

    private JWTClaimsSet claims(SignedJWT signedJWT) {
        try {
            return signedJWT.getJWTClaimsSet();
        } catch (ParseException e) {
            log.warn("Apple identityToken validation failed. reason=invalid_claims");
            throw new RuntimeException("Invalid Apple identityToken claims", e);
        }
    }

    private void validateIssuer(JWTClaimsSet claimsSet) {
        String issuer = claimsSet.getIssuer();
        if (!APPLE_ISSUER.equals(issuer)) {
            log.warn(
                    "Apple identityToken validation failed. reason=invalid_issuer iss={}",
                    sanitizeClaimForLog(issuer)
            );
            throw new RuntimeException("Invalid Apple identityToken issuer");
        }
    }

    private void validateAudience(JWTClaimsSet claimsSet) {
        List<String> audience = claimsSet.getAudience();
        if (audience == null || !audience.contains(properties.getClientId())) {
            log.warn(
                    "Apple identityToken validation failed. reason=invalid_audience aud={}",
                    sanitizeClaimForLog(audience == null ? null : String.join(",", audience))
            );
            throw new RuntimeException("Invalid Apple identityToken audience");
        }
    }

    private void validateExpiration(JWTClaimsSet claimsSet) {
        Date expirationTime = claimsSet.getExpirationTime();
        if (expirationTime == null) {
            log.warn("Apple identityToken validation failed. reason=missing_exp");
            throw new RuntimeException("Expired Apple identityToken");
        }

        Instant expiresAtWithSkew = expirationTime.toInstant().plus(properties.getClockSkew());
        if (expiresAtWithSkew.isBefore(clock.instant())) {
            log.warn("Apple identityToken validation failed. reason=expired");
            throw new RuntimeException("Expired Apple identityToken");
        }
    }

    private String requireSubject(JWTClaimsSet claimsSet) {
        String subject = claimsSet.getSubject();
        if (!StringUtils.hasText(subject)) {
            log.warn("Apple identityToken validation failed. reason=missing_subject");
            throw new RuntimeException("Apple identityToken subject is required");
        }
        return subject;
    }

    private static String sanitizeClaimForLog(String value) {
        if (value == null) {
            return null;
        }

        String sanitized = value
                .replace('\r', '_')
                .replace('\n', '_')
                .replace('\t', '_');
        if (sanitized.length() <= MAX_LOGGED_CLAIM_LENGTH) {
            return sanitized;
        }
        return sanitized.substring(0, MAX_LOGGED_CLAIM_LENGTH);
    }
}
