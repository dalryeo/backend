package com.ohgiraffers.dalryeo.ranking.controller;

import com.ohgiraffers.dalryeo.auth.exception.AuthErrorCode;
import com.ohgiraffers.dalryeo.auth.exception.AuthException;
import com.ohgiraffers.dalryeo.auth.jwt.JwtTokenExtractor;
import com.ohgiraffers.dalryeo.auth.jwt.JwtTokenProvider;
import com.ohgiraffers.dalryeo.common.CommonResponse;
import com.ohgiraffers.dalryeo.ranking.dto.DistanceRankingResponse;
import com.ohgiraffers.dalryeo.ranking.dto.RankingMeResponse;
import com.ohgiraffers.dalryeo.ranking.dto.ScoreRankingResponse;
import com.ohgiraffers.dalryeo.ranking.service.RankingService;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/ranking")
@RequiredArgsConstructor
public class RankingController {

    private final RankingService rankingService;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtTokenExtractor jwtTokenExtractor;

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
    public CommonResponse<RankingMeResponse> getMyRanking(HttpServletRequest httpRequest) {
        Long userId = extractUserIdFromRequest(httpRequest);
        RankingMeResponse response = rankingService.getMyRanking(userId);
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

