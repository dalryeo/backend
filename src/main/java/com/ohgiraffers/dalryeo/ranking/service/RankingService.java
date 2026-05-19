package com.ohgiraffers.dalryeo.ranking.service;

import com.ohgiraffers.dalryeo.auth.entity.User;
import com.ohgiraffers.dalryeo.ranking.dto.DistanceRankingResponse;
import com.ohgiraffers.dalryeo.ranking.dto.RankingMeResponse;
import com.ohgiraffers.dalryeo.ranking.dto.ScoreRankingResponse;
import com.ohgiraffers.dalryeo.record.entity.WeeklyUserStats;
import com.ohgiraffers.dalryeo.record.repository.WeeklyUserStatsRepository;
import com.ohgiraffers.dalryeo.record.repository.WeeklyUserStatsRepository.WeeklyRankingRow;
import com.ohgiraffers.dalryeo.tier.service.TierService;
import com.ohgiraffers.dalryeo.user.service.UserLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RankingService {

    private static final int DEFAULT_RANKING_LIMIT = 100;

    private final WeeklyUserStatsRepository weeklyUserStatsRepository;
    private final UserLookupService userLookupService;
    private final TierService tierService;

    /**
     * 점수 기반 주간 랭킹 조회
     */
    public List<ScoreRankingResponse> getWeeklyScoreRanking() {
        LocalDate weekStart = currentWeekStart();
        List<WeeklyRankingRow> rankingRows = weeklyUserStatsRepository.findScoreRankingRows(
                weekStart,
                DEFAULT_RANKING_LIMIT
        );

        return java.util.stream.IntStream.range(0, rankingRows.size())
                .mapToObj(index -> toScoreRankingResponse(index, rankingRows.get(index)))
                .collect(Collectors.toList());
    }

    /**
     * 거리 기반 주간 랭킹 조회
     */
    public List<DistanceRankingResponse> getWeeklyDistanceRanking() {
        LocalDate weekStart = currentWeekStart();
        List<WeeklyRankingRow> rankingRows = weeklyUserStatsRepository.findDistanceRankingRows(
                weekStart,
                DEFAULT_RANKING_LIMIT
        );

        return java.util.stream.IntStream.range(0, rankingRows.size())
                .mapToObj(index -> toDistanceRankingResponse(index, rankingRows.get(index)))
                .collect(Collectors.toList());
    }

    /**
     * 내 랭킹 조회
     */
    public RankingMeResponse getMyRanking(Long userId) {
        User user = userLookupService.getActiveById(userId);

        LocalDate weekStart = currentWeekStart();
        return weeklyUserStatsRepository.findByUserIdAndWeekStartDate(userId, weekStart)
                .filter(WeeklyUserStats::hasRecords)
                .map(stats -> buildMyRankingFromStats(user, weekStart, stats))
                .orElseGet(() -> buildEmptyMyRanking(user));
    }

    private ScoreRankingResponse toScoreRankingResponse(
            int index,
            WeeklyRankingRow row
    ) {
        double tierScore = toDouble(row.getTierScore());
        TierService.TierInfo tierInfo = tierService.resolveByScore(tierScore);
        return ScoreRankingResponse.builder()
                .rank(index + 1)
                .nickname(row.getNickname())
                .tierCode(tierInfo.tierCode())
                .tierGrade(tierInfo.tierGrade())
                .tierScore(tierScore)
                .weeklyAvgPace(row.getAvgPaceSecPerKm())
                .weeklyDistance(toDouble(row.getTotalDistanceKm()))
                .build();
    }

    private DistanceRankingResponse toDistanceRankingResponse(
            int index,
            WeeklyRankingRow row
    ) {
        double tierScore = toDouble(row.getTierScore());
        TierService.TierInfo tierInfo = tierService.resolveByScore(tierScore);
        return DistanceRankingResponse.builder()
                .rank(index + 1)
                .nickname(row.getNickname())
                .weeklyDistance(toDouble(row.getTotalDistanceKm()))
                .weeklyAvgPace(row.getAvgPaceSecPerKm())
                .tierCode(tierInfo.tierCode())
                .tierGrade(tierInfo.tierGrade())
                .build();
    }

    private RankingMeResponse buildMyRankingFromStats(User user, LocalDate weekStart, WeeklyUserStats stats) {
        int scoreRank = toRank(weeklyUserStatsRepository.countAheadForScoreRank(
                weekStart,
                stats.getTierScore(),
                stats.getTotalDistanceKm(),
                user.getId()
        ));
        int distanceRank = toRank(weeklyUserStatsRepository.countAheadForDistanceRank(
                weekStart,
                stats.getTotalDistanceKm(),
                stats.getTierScore(),
                user.getId()
        ));
        TierService.TierInfo tierInfo = tierService.resolveByScore(stats.tierScoreAsDouble());

        return RankingMeResponse.builder()
                .nickname(user.getNickname())
                .scoreRank(scoreRank)
                .distanceRank(distanceRank)
                .tierCode(tierInfo.tierCode())
                .tierGrade(tierInfo.tierGrade())
                .tierScore(stats.tierScoreAsDouble())
                .weeklyAvgPace(stats.getAvgPaceSecPerKm())
                .weeklyDistance(stats.totalDistanceAsDouble())
                .build();
    }

    private RankingMeResponse buildEmptyMyRanking(User user) {
        TierService.TierInfo tierInfo = tierService.resolveByScore(0.0);
        return RankingMeResponse.builder()
                .nickname(user.getNickname())
                .scoreRank(null)
                .distanceRank(null)
                .tierCode(tierInfo.tierCode())
                .tierGrade(tierInfo.tierGrade())
                .tierScore(0.0)
                .weeklyAvgPace(0)
                .weeklyDistance(0.0)
                .build();
    }

    private int toRank(long aheadCount) {
        return Math.toIntExact(aheadCount + 1);
    }

    private double toDouble(BigDecimal value) {
        return value == null ? 0.0 : value.doubleValue();
    }

    private LocalDate currentWeekStart() {
        return LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
