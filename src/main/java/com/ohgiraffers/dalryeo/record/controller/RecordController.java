package com.ohgiraffers.dalryeo.record.controller;

import com.ohgiraffers.dalryeo.auth.exception.AuthErrorCode;
import com.ohgiraffers.dalryeo.auth.exception.AuthException;
import com.ohgiraffers.dalryeo.auth.jwt.JwtTokenExtractor;
import com.ohgiraffers.dalryeo.auth.jwt.JwtTokenProvider;
import com.ohgiraffers.dalryeo.common.CommonResponse;
import com.ohgiraffers.dalryeo.record.dto.RecordIdResponse;
import com.ohgiraffers.dalryeo.record.dto.RecordSummaryResponse;
import com.ohgiraffers.dalryeo.record.dto.RunningRecordRequest;
import com.ohgiraffers.dalryeo.record.dto.WeeklyRecordListResponse;
import com.ohgiraffers.dalryeo.record.service.RecordService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/records")
@RequiredArgsConstructor
public class RecordController {

    private final RecordService recordService;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtTokenExtractor jwtTokenExtractor;

    /**
     * 러닝 기록 저장
     * POST /records
     */
    @PostMapping
    public CommonResponse<RecordIdResponse> saveRecord(
            @Valid @RequestBody RunningRecordRequest request,
            HttpServletRequest httpRequest) {
        Long userId = extractUserIdFromRequest(httpRequest);
        RecordIdResponse response = recordService.saveRecord(userId, request);
        return CommonResponse.success(response);
    }

    /**
     * 기록 탭 메인 정보 (주간 요약)
     * GET /records/summary
     */
    @GetMapping("/summary")
    public CommonResponse<RecordSummaryResponse> getSummary(HttpServletRequest httpRequest) {
        Long userId = extractUserIdFromRequest(httpRequest);
        RecordSummaryResponse response = recordService.getSummary(userId);
        return CommonResponse.success(response);
    }

    /**
     * 주간 기록 목록
     * GET /records/weekly
     */
    @GetMapping("/weekly")
    public CommonResponse<WeeklyRecordListResponse> getWeeklyRecords(HttpServletRequest httpRequest) {
        Long userId = extractUserIdFromRequest(httpRequest);
        WeeklyRecordListResponse response = recordService.getWeeklyRecords(userId);
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
