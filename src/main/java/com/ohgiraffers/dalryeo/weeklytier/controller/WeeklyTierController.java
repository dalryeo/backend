package com.ohgiraffers.dalryeo.weeklytier.controller;

import com.ohgiraffers.dalryeo.auth.exception.AuthErrorCode;
import com.ohgiraffers.dalryeo.auth.exception.AuthException;
import com.ohgiraffers.dalryeo.auth.jwt.JwtTokenExtractor;
import com.ohgiraffers.dalryeo.auth.jwt.JwtTokenProvider;
import com.ohgiraffers.dalryeo.common.CommonResponse;
import com.ohgiraffers.dalryeo.weeklytier.dto.WeeklyTierResponse;
import com.ohgiraffers.dalryeo.weeklytier.service.WeeklyTierService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/weekly/tiers")
@RequiredArgsConstructor
public class WeeklyTierController {

    private final WeeklyTierService weeklyTierService;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtTokenExtractor jwtTokenExtractor;

    /**
     * 주간 기록 전 조회 가능한 티어
     * GET /weekly/tiers/current
     */
    @GetMapping("/current")
    public CommonResponse<WeeklyTierResponse> getCurrentWeeklyTier(HttpServletRequest httpRequest) {
        Long userId = extractUserIdFromRequest(httpRequest);
        WeeklyTierResponse response = weeklyTierService.getCurrentWeeklyTier(userId);
        return CommonResponse.success(response);
    }

    /**
     * 요청에서 AccessToken을 추출하여 사용자 ID를 반환
     */
    private Long extractUserIdFromRequest(HttpServletRequest request) {
        String token = jwtTokenExtractor.extractToken(request);
        if (token == null || !jwtTokenProvider.validateToken(token)) {
            throw new AuthException(AuthErrorCode.REFRESH_TOKEN_EXPIRED);
        }
        return jwtTokenProvider.getUserIdFromToken(token);
    }
}
