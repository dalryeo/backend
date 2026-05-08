package com.ohgiraffers.dalryeo.ranking.controller;

import com.ohgiraffers.dalryeo.auth.annotation.LoginUser;
import com.ohgiraffers.dalryeo.common.CommonResponse;
import com.ohgiraffers.dalryeo.ranking.dto.DistanceRankingResponse;
import com.ohgiraffers.dalryeo.ranking.dto.RankingMeResponse;
import com.ohgiraffers.dalryeo.ranking.dto.ScoreRankingResponse;
import com.ohgiraffers.dalryeo.ranking.service.RankingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/ranking")
@RequiredArgsConstructor
public class RankingController {

    private final RankingService rankingService;

    /**
     * 점수 기반 주간 랭킹 조회
     * GET /ranking/weekly/score
     */
    @GetMapping("/weekly/score")
    public CommonResponse<List<ScoreRankingResponse>> getWeeklyScoreRanking() {
        List<ScoreRankingResponse> response = rankingService.getWeeklyScoreRanking();
        return CommonResponse.success(response);
    }

    /**
     * 거리 기반 주간 랭킹 조회
     * GET /ranking/weekly/distance
     */
    @GetMapping("/weekly/distance")
    public CommonResponse<List<DistanceRankingResponse>> getWeeklyDistanceRanking() {
        List<DistanceRankingResponse> response = rankingService.getWeeklyDistanceRanking();
        return CommonResponse.success(response);
    }

    /**
     * 내 랭킹 조회
     * GET /ranking/me
     */
    @GetMapping("/me")
    public CommonResponse<RankingMeResponse> getMyRanking(@LoginUser Long userId) {
        RankingMeResponse response = rankingService.getMyRanking(userId);
        return CommonResponse.success(response);
    }

}
