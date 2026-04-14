package com.ohgiraffers.dalryeo.ranking.service;

import com.ohgiraffers.dalryeo.auth.entity.User;
import com.ohgiraffers.dalryeo.auth.repository.UserRepository;
import com.ohgiraffers.dalryeo.ranking.dto.DistanceRankingResponse;
import com.ohgiraffers.dalryeo.ranking.dto.RankingMeResponse;
import com.ohgiraffers.dalryeo.ranking.dto.ScoreRankingResponse;
import com.ohgiraffers.dalryeo.record.entity.WeeklyUserStats;
import com.ohgiraffers.dalryeo.record.repository.WeeklyUserStatsRepository;
import com.ohgiraffers.dalryeo.tier.service.TierService;
import com.ohgiraffers.dalryeo.user.service.UserLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RankingService {

    private static final int DEFAULT_RANKING_LIMIT = 100;

    private final WeeklyUserStatsRepository weeklyUserStatsRepository;
    private final UserRepository userRepository;
    private final UserLookupService userLookupService;
    private final TierService tierService;

    /**
     * 점수 기반 주간 랭킹 조회
     */
    public List<ScoreRankingResponse> getWeeklyScoreRanking() {
        LocalDate weekStart = currentWeekStart();
        List<WeeklyUserStats> statsRows = weeklyUserStatsRepository.findScoreRankingRows(
                weekStart,
                DEFAULT_RANKING_LIMIT
        );
        Map<Long, User> usersById = findUsersById(statsRows);

        return java.util.stream.IntStream.range(0, statsRows.size())
                .mapToObj(index -> toScoreRankingResponse(index, statsRows.get(index), usersById))
                .filter(response -> response != null)
                .collect(Collectors.toList());
    }

    /**
     * 거리 기반 주간 랭킹 조회
     */
    public List<DistanceRankingResponse> getWeeklyDistanceRanking() {
        LocalDate weekStart = currentWeekStart();
        List<WeeklyUserStats> statsRows = weeklyUserStatsRepository.findDistanceRankingRows(
                weekStart,
                DEFAULT_RANKING_LIMIT
        );
        Map<Long, User> usersById = findUsersById(statsRows);

        return java.util.stream.IntStream.range(0, statsRows.size())
                .mapToObj(index -> toDistanceRankingResponse(index, statsRows.get(index), usersById))
                .filter(response -> response != null)
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
            WeeklyUserStats stats,
            Map<Long, User> usersById
    ) {
        User user = usersById.get(stats.getUserId());
        if (user == null) {
            return null;
        }

        TierService.TierInfo tierInfo = tierService.resolveByScore(stats.tierScoreAsDouble());
        return ScoreRankingResponse.builder()
                .rank(index + 1)
                .nickname(user.getNickname())
                .tierCode(tierInfo.tierCode())
                .tierGrade(tierInfo.tierGrade())
                .tierScore(stats.tierScoreAsDouble())
                .weeklyAvgPace(stats.getAvgPaceSecPerKm())
                .weeklyDistance(stats.totalDistanceAsDouble())
                .build();
    }

    private DistanceRankingResponse toDistanceRankingResponse(
            int index,
            WeeklyUserStats stats,
            Map<Long, User> usersById
    ) {
        User user = usersById.get(stats.getUserId());
        if (user == null) {
            return null;
        }

        TierService.TierInfo tierInfo = tierService.resolveByScore(stats.tierScoreAsDouble());
        return DistanceRankingResponse.builder()
                .rank(index + 1)
                .nickname(user.getNickname())
                .weeklyDistance(stats.totalDistanceAsDouble())
                .weeklyAvgPace(stats.getAvgPaceSecPerKm())
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

    private Map<Long, User> findUsersById(Collection<WeeklyUserStats> statsRows) {
        List<Long> userIds = statsRows.stream()
                .map(WeeklyUserStats::getUserId)
                .toList();
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private int toRank(long aheadCount) {
        return Math.toIntExact(aheadCount + 1);
    }

    private LocalDate currentWeekStart() {
        return LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
