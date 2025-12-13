package com.ohgiraffers.dalryeo.analysis.controller;

import com.ohgiraffers.dalryeo.analysis.dto.RecordDetailResponse;
import com.ohgiraffers.dalryeo.analysis.dto.RecordListResponse;
import com.ohgiraffers.dalryeo.analysis.service.AnalysisService;
import com.ohgiraffers.dalryeo.auth.exception.AuthErrorCode;
import com.ohgiraffers.dalryeo.auth.exception.AuthException;
import com.ohgiraffers.dalryeo.auth.jwt.JwtTokenExtractor;
import com.ohgiraffers.dalryeo.auth.jwt.JwtTokenProvider;
import com.ohgiraffers.dalryeo.common.CommonResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService analysisService;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtTokenExtractor jwtTokenExtractor;

    /**
     * 전체 기록 조회
     * GET /analysis/records?page=1&sort=latest&period=monthly
     */
    @GetMapping("/records")
    public CommonResponse<RecordListResponse> getRecords(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String period,
            HttpServletRequest httpRequest) {
        Long userId = extractUserIdFromRequest(httpRequest);
        RecordListResponse response = analysisService.getRecords(userId, page, sort, period);
        return CommonResponse.success(response);
    }

    /**
     * 기록 상세
     * GET /analysis/records/{recordId}
     */
    @GetMapping("/records/{recordId}")
    public CommonResponse<RecordDetailResponse> getRecordDetail(
            @PathVariable Long recordId,
            HttpServletRequest httpRequest) {
        Long userId = extractUserIdFromRequest(httpRequest);
        RecordDetailResponse response = analysisService.getRecordDetail(userId, recordId);
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

