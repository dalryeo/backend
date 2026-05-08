package com.ohgiraffers.dalryeo.analysis.controller;

import com.ohgiraffers.dalryeo.analysis.dto.RecordDetailResponse;
import com.ohgiraffers.dalryeo.analysis.dto.RecordListResponse;
import com.ohgiraffers.dalryeo.analysis.service.AnalysisService;
import com.ohgiraffers.dalryeo.auth.annotation.LoginUser;
import com.ohgiraffers.dalryeo.common.CommonResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService analysisService;

    /**
     * 전체 기록 조회
     * GET /analysis/records?page=1&sort=latest&period=monthly
     */
    @GetMapping("/records")
    public CommonResponse<RecordListResponse> getRecords(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String period,
            @LoginUser Long userId) {
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
            @LoginUser Long userId) {
        RecordDetailResponse response = analysisService.getRecordDetail(userId, recordId);
        return CommonResponse.success(response);
    }

}
