package com.ohgiraffers.dalryeo.auth.oauth;

import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.ParseException;

@Slf4j
@Component
public class AppleOAuthValidator {

    /**
     * Apple identityToken을 검증하고 Apple ID를 추출합니다.
     * 실제 프로덕션에서는 Apple의 공개키를 사용하여 서명을 검증해야 합니다.
     *
     * @param identityToken Apple에서 받은 identityToken
     * @return Apple ID (sub claim)
     * @throws RuntimeException 토큰 검증 실패 시
     */
    public String validateAndExtractAppleId(String identityToken) {
        try {
            JWT jwt = JWTParser.parse(identityToken);
            JWTClaimsSet claimsSet = jwt.getJWTClaimsSet();

            // Apple ID 추출 (sub claim)
            String appleId = claimsSet.getSubject();
            if (appleId == null || appleId.isEmpty()) {
                throw new RuntimeException("Apple ID not found in token");
            }

            // TODO: 실제 프로덕션에서는 Apple의 공개키를 사용하여 서명 검증 필요
            // 현재는 기본적인 파싱만 수행
            // Apple의 JWK 엔드포인트: https://appleid.apple.com/auth/keys

            return appleId;
        } catch (ParseException e) {
            log.error("Failed to parse Apple identityToken", e);
            throw new RuntimeException("Invalid Apple identityToken format", e);
        } catch (Exception e) {
            log.error("Failed to validate Apple identityToken", e);
            throw new RuntimeException("Apple identityToken validation failed", e);
        }
    }
}

