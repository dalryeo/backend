package com.ohgiraffers.dalryeo.record.controller;

import com.ohgiraffers.dalryeo.auth.jwt.AuthenticatedUserResolver;
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
    private final AuthenticatedUserResolver authenticatedUserResolver;

    /**
     * 러닝 기록 저장
     * POST /records
     */
    @PostMapping
    public CommonResponse<RecordIdResponse> saveRecord(
            @Valid @RequestBody RunningRecordRequest request,
            HttpServletRequest httpRequest) {
        Long userId = authenticatedUserResolver.resolveUserId(httpRequest);
        RecordIdResponse response = recordService.saveRecord(userId, request);
        return CommonResponse.success(response);
    }

    /**
     * 기록 탭 메인 정보 (주간 요약)
     * GET /records/summary
     */
    @GetMapping("/summary")
    public CommonResponse<RecordSummaryResponse> getSummary(HttpServletRequest httpRequest) {
        Long userId = authenticatedUserResolver.resolveUserId(httpRequest);
        RecordSummaryResponse response = recordService.getSummary(userId);
        return CommonResponse.success(response);
    }

    /**
     * 주간 기록 목록
     * GET /records/weekly
     */
    @GetMapping("/weekly")
    public CommonResponse<WeeklyRecordListResponse> getWeeklyRecords(HttpServletRequest httpRequest) {
        Long userId = authenticatedUserResolver.resolveUserId(httpRequest);
        WeeklyRecordListResponse response = recordService.getWeeklyRecords(userId);
        return CommonResponse.success(response);
    }

}
