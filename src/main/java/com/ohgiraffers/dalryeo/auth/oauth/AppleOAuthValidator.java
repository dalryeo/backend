package com.ohgiraffers.dalryeo.auth.oauth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.ohgiraffers.dalryeo.auth.exception.AuthErrorCode;
import com.ohgiraffers.dalryeo.auth.exception.AuthException;
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
import java.util.Set;

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
            throw verificationFailed();
        }

        validateAlgorithm(signedJWT);

        RSAKey rsaKey = getRsaKey(keyId);
        verifySignature(signedJWT, rsaKey, keyId);

        JWTClaimsSet claimsSet = claims(signedJWT);
        validateIssuer(claimsSet);
        validateAudience(claimsSet);
        validateExpiration(claimsSet);
        validateIssuedAt(claimsSet);
        return requireSubject(claimsSet);
    }

    private SignedJWT parse(String identityToken) {
        try {
            return SignedJWT.parse(identityToken);
        } catch (ParseException e) {
            log.warn("Apple identityToken validation failed. reason=invalid_format");
            throw verificationFailed();
        }
    }

    private void validateAlgorithm(SignedJWT signedJWT) {
        JWSAlgorithm algorithm = signedJWT.getHeader().getAlgorithm();
        if (!JWSAlgorithm.RS256.equals(algorithm)) {
            log.warn(
                    "Apple identityToken validation failed. reason=invalid_algorithm alg={}",
                    sanitizeClaimForLog(algorithm == null ? null : algorithm.getName())
            );
            throw verificationFailed();
        }
    }

    private RSAKey getRsaKey(String keyId) {
        try {
            return (RSAKey) appleJwkProvider.getByKeyId(keyId);
        } catch (AppleJwkFetchException e) {
            log.error(
                    "Apple identityToken validation failed. reason=jwk_fetch_failed kid={}",
                    AppleJwkProvider.sanitizeKidForLog(keyId),
                    e
            );
            throw verificationFailed();
        } catch (RuntimeException e) {
            log.warn(
                    "Apple identityToken validation failed. reason=jwk_lookup_failed kid={}",
                    AppleJwkProvider.sanitizeKidForLog(keyId)
            );
            throw verificationFailed();
        }
    }

    private void verifySignature(SignedJWT signedJWT, RSAKey rsaKey, String keyId) {
        try {
            boolean verified = signedJWT.verify(new RSASSAVerifier(rsaKey.toRSAPublicKey()));
            if (!verified) {
                log.warn(
                        "Apple identityToken validation failed. reason=invalid_signature kid={}",
                        AppleJwkProvider.sanitizeKidForLog(keyId)
                );
                throw verificationFailed();
            }
        } catch (JOSEException e) {
            log.warn(
                    "Apple identityToken validation failed. reason=signature_verification_error kid={}",
                    AppleJwkProvider.sanitizeKidForLog(keyId)
            );
            throw verificationFailed();
        }
    }

    private JWTClaimsSet claims(SignedJWT signedJWT) {
        try {
            return signedJWT.getJWTClaimsSet();
        } catch (ParseException e) {
            log.warn("Apple identityToken validation failed. reason=invalid_claims");
            throw verificationFailed();
        }
    }

    private void validateIssuer(JWTClaimsSet claimsSet) {
        String issuer = claimsSet.getIssuer();
        if (!APPLE_ISSUER.equals(issuer)) {
            log.warn(
                    "Apple identityToken validation failed. reason=invalid_issuer iss={}",
                    sanitizeClaimForLog(issuer)
            );
            throw verificationFailed();
        }
    }

    private void validateAudience(JWTClaimsSet claimsSet) {
        List<String> audience = claimsSet.getAudience();
        Set<String> allowedClientIds = properties.getEffectiveClientIds();
        if (audience == null || audience.stream().noneMatch(allowedClientIds::contains)) {
            log.warn(
                    "Apple identityToken validation failed. reason=invalid_audience aud={}",
                    sanitizeClaimForLog(audience == null ? null : String.join(",", audience))
            );
            throw verificationFailed();
        }
    }

    private void validateExpiration(JWTClaimsSet claimsSet) {
        Date expirationTime = claimsSet.getExpirationTime();
        if (expirationTime == null) {
            log.warn("Apple identityToken validation failed. reason=missing_exp");
            throw verificationFailed();
        }

        Instant expiresAtWithSkew = expirationTime.toInstant().plus(properties.getClockSkew());
        if (expiresAtWithSkew.isBefore(clock.instant())) {
            log.warn("Apple identityToken validation failed. reason=expired");
            throw verificationFailed();
        }
    }

    private void validateIssuedAt(JWTClaimsSet claimsSet) {
        Date issueTime = claimsSet.getIssueTime();
        if (issueTime == null) {
            log.warn("Apple identityToken validation failed. reason=missing_iat");
            throw verificationFailed();
        }

        Instant latestAllowedIssueTime = clock.instant().plus(properties.getClockSkew());
        if (issueTime.toInstant().isAfter(latestAllowedIssueTime)) {
            log.warn("Apple identityToken validation failed. reason=issued_at_in_future");
            throw verificationFailed();
        }
    }

    private String requireSubject(JWTClaimsSet claimsSet) {
        String subject = claimsSet.getSubject();
        if (!StringUtils.hasText(subject)) {
            log.warn("Apple identityToken validation failed. reason=missing_subject");
            throw verificationFailed();
        }
        return subject;
    }

    private AuthException verificationFailed() {
        return new AuthException(AuthErrorCode.OAUTH_TOKEN_VERIFICATION_FAILED);
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
