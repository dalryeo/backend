package com.ohgiraffers.dalryeo.record.controller;

import com.ohgiraffers.dalryeo.auth.annotation.LoginUser;
import com.ohgiraffers.dalryeo.common.CommonResponse;
import com.ohgiraffers.dalryeo.record.dto.RecordIdResponse;
import com.ohgiraffers.dalryeo.record.dto.RecordSummaryResponse;
import com.ohgiraffers.dalryeo.record.dto.RunningRecordRequest;
import com.ohgiraffers.dalryeo.record.dto.WeeklyRecordListResponse;
import com.ohgiraffers.dalryeo.record.service.RecordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/records")
@RequiredArgsConstructor
public class RecordController {

    private final RecordService recordService;

    /**
     * 러닝 기록 저장
     * POST /records
     */
    @PostMapping
    public CommonResponse<RecordIdResponse> saveRecord(
            @Valid @RequestBody RunningRecordRequest request,
            @LoginUser Long userId) {
        RecordIdResponse response = recordService.saveRecord(userId, request);
        return CommonResponse.success(response);
    }

    /**
     * 기록 탭 메인 정보 (주간 요약)
     * GET /records/summary
     */
    @GetMapping("/summary")
    public CommonResponse<RecordSummaryResponse> getSummary(@LoginUser Long userId) {
        RecordSummaryResponse response = recordService.getSummary(userId);
        return CommonResponse.success(response);
    }

    /**
     * 주간 기록 목록
     * GET /records/weekly
     */
    @GetMapping("/weekly")
    public CommonResponse<WeeklyRecordListResponse> getWeeklyRecords(@LoginUser Long userId) {
        WeeklyRecordListResponse response = recordService.getWeeklyRecords(userId);
        return CommonResponse.success(response);
    }

}
