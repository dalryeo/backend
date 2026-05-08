package com.ohgiraffers.dalryeo.record.controller;

import com.ohgiraffers.dalryeo.auth.jwt.AuthenticatedUserResolver;
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
    private final AuthenticatedUserResolver authenticatedUserResolver;

    /**
     * 주간 요약 (이번 주, 수~일 기준)
     * GET /weekly/summary/current
     */
    @GetMapping("/current")
    public CommonResponse<WeeklySummaryResponse> getCurrentWeeklySummary(HttpServletRequest httpRequest) {
        Long userId = authenticatedUserResolver.resolveUserId(httpRequest);
        WeeklySummaryResponse response = recordService.getCurrentWeeklySummary(userId);
        return CommonResponse.success(response);
    }

    /**
     * 주간 요약 리스트 (가입일부터 현재까지)
     * GET /weekly/summary/list
     */
    @GetMapping("/list")
    public CommonResponse<List<WeeklySummaryItemResponse>> getWeeklySummaryList(HttpServletRequest httpRequest) {
        Long userId = authenticatedUserResolver.resolveUserId(httpRequest);
        List<WeeklySummaryItemResponse> response = recordService.getWeeklySummaryList(userId);
        return CommonResponse.success(response);
    }

}
