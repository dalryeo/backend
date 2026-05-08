package com.ohgiraffers.dalryeo.weeklytier.controller;

import com.ohgiraffers.dalryeo.auth.jwt.AuthenticatedUserResolver;
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
    private final AuthenticatedUserResolver authenticatedUserResolver;

    /**
     * 주간 기록 전 조회 가능한 티어
     * GET /weekly/tiers/current
     */
    @GetMapping("/current")
    public CommonResponse<WeeklyTierResponse> getCurrentWeeklyTier(HttpServletRequest httpRequest) {
        Long userId = authenticatedUserResolver.resolveUserId(httpRequest);
        WeeklyTierResponse response = weeklyTierService.getCurrentWeeklyTier(userId);
        return CommonResponse.success(response);
    }

}
