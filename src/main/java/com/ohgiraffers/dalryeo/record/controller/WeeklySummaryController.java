package com.ohgiraffers.dalryeo.record.controller;

import com.ohgiraffers.dalryeo.auth.exception.AuthErrorCode;
import com.ohgiraffers.dalryeo.auth.exception.AuthException;
import com.ohgiraffers.dalryeo.auth.jwt.JwtTokenExtractor;
import com.ohgiraffers.dalryeo.auth.jwt.JwtTokenProvider;
import com.ohgiraffers.dalryeo.common.CommonResponse;
import com.ohgiraffers.dalryeo.record.dto.WeeklySummaryItemResponse;
import com.ohgiraffers.dalryeo.record.dto.WeeklySummaryResponse;
import com.ohgiraffers.dalryeo.record.service.RecordService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/weekly/summary")
@RequiredArgsConstructor
public class WeeklySummaryController {

    private final RecordService recordService;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtTokenExtractor jwtTokenExtractor;

    /**
     * 주간 요약 (이번 주, 수~일 기준)
     * GET /weekly/summary/current
     */
    @GetMapping("/current")
    public CommonResponse<WeeklySummaryResponse> getCurrentWeeklySummary(HttpServletRequest httpRequest) {
        Long userId = extractUserIdFromRequest(httpRequest);
        WeeklySummaryResponse response = recordService.getCurrentWeeklySummary(userId);
        return CommonResponse.success(response);
    }

    /**
     * 주간 요약 리스트 (가입일부터 현재까지)
     * GET /weekly/summary/list
     */
    @GetMapping("/list")
    public CommonResponse<List<WeeklySummaryItemResponse>> getWeeklySummaryList(HttpServletRequest httpRequest) {
        Long userId = extractUserIdFromRequest(httpRequest);
        List<WeeklySummaryItemResponse> response = recordService.getWeeklySummaryList(userId);
        return CommonResponse.success(response);
    }

    /**
     * 요청에서 AccessToken을 추출하여 사용자 ID를 반환
     */
    private Long extractUserIdFromRequest(HttpServletRequest request) {
        String token = jwtTokenExtractor.extractToken(request);
        if (token == null || !jwtTokenProvider.validateAccessToken(token)) {
            throw new AuthException(AuthErrorCode.REFRESH_TOKEN_EXPIRED);
        }
        return jwtTokenProvider.getUserIdFromToken(token);
    }
}
