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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RankingService {

    private static final int DEFAULT_RANKING_LIMIT = 100;

    private final WeeklyUserStatsRepository weeklyUserStatsRepository;
    private final UserLookupService userLookupService;
    private final TierService tierService;

    // 점수 기준 주간 랭킹 목록을 조회한다.
    public List<ScoreRankingResponse> getWeeklyScoreRanking() {
        LocalDate weekStart = currentWeekStart();
        List<WeeklyRankingRow> rankingRows = weeklyUserStatsRepository.findScoreRankingRows(
                weekStart,
                DEFAULT_RANKING_LIMIT
        );

        return IntStream.range(0, rankingRows.size())
                .mapToObj(index -> toScoreRankingResponse(index, rankingRows.get(index)))
                .collect(Collectors.toList());
    }

    // 거리 기준 주간 랭킹 목록을 조회한다.
    public List<DistanceRankingResponse> getWeeklyDistanceRanking() {
        LocalDate weekStart = currentWeekStart();
        List<WeeklyRankingRow> rankingRows = weeklyUserStatsRepository.findDistanceRankingRows(
                weekStart,
                DEFAULT_RANKING_LIMIT
        );

        return IntStream.range(0, rankingRows.size())
                .mapToObj(index -> toDistanceRankingResponse(index, rankingRows.get(index)))
                .collect(Collectors.toList());
    }

    // 로그인 사용자의 이번 주 랭킹 정보를 조회한다.
    public RankingMeResponse getMyRanking(Long userId) {
        User user = userLookupService.getActiveById(userId);

        LocalDate weekStart = currentWeekStart();
        return weeklyUserStatsRepository.findByUserIdAndWeekStartDate(userId, weekStart)
                .filter(WeeklyUserStats::hasRecords)
                .map(stats -> buildMyRankingFromStats(user, weekStart, stats))
                .orElseGet(() -> buildEmptyMyRanking(user));
    }

    // 점수 랭킹 조회 row를 응답 DTO로 변환한다.
    private ScoreRankingResponse toScoreRankingResponse(
            int index,
            WeeklyRankingRow row
    ) {
        double tierScore = row.getTierScoreAsDouble();
        TierService.TierInfo tierInfo = tierService.resolveByScore(tierScore);
        return ScoreRankingResponse.builder()
                .rank(index + 1)
                .nickname(row.getNickname())
                .tierCode(tierInfo.tierCode())
                .tierGrade(tierInfo.tierGrade())
                .tierScore(tierScore)
                .weeklyAvgPace(row.getAvgPaceSecPerKm())
                .weeklyDistance(row.getTotalDistanceKmAsDouble())
                .build();
    }

    // 거리 랭킹 조회 row를 응답 DTO로 변환한다.
    private DistanceRankingResponse toDistanceRankingResponse(
            int index,
            WeeklyRankingRow row
    ) {
        double tierScore = row.getTierScoreAsDouble();
        TierService.TierInfo tierInfo = tierService.resolveByScore(tierScore);
        return DistanceRankingResponse.builder()
                .rank(index + 1)
                .nickname(row.getNickname())
                .weeklyDistance(row.getTotalDistanceKmAsDouble())
                .weeklyAvgPace(row.getAvgPaceSecPerKm())
                .tierCode(tierInfo.tierCode())
                .tierGrade(tierInfo.tierGrade())
                .build();
    }

    // 주간 기록이 있는 사용자의 내 랭킹 응답을 만든다.
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

    // 이번 주 기록이 없는 사용자의 기본 내 랭킹 응답을 만든다.
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

    // 나보다 앞선 사용자 수를 1부터 시작하는 순위로 변환한다.
    private int toRank(long aheadCount) {
        return Math.toIntExact(aheadCount + 1);
    }

    // 오늘 날짜를 기준으로 이번 주 월요일을 구한다.
    private LocalDate currentWeekStart() {
        return LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
