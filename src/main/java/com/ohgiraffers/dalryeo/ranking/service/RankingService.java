package com.ohgiraffers.dalryeo.ranking.service;

import com.ohgiraffers.dalryeo.auth.entity.User;
import com.ohgiraffers.dalryeo.common.time.ServiceDateProvider;
import com.ohgiraffers.dalryeo.ranking.dto.DistanceRankingResponse;
import com.ohgiraffers.dalryeo.ranking.dto.RankingMeResponse;
import com.ohgiraffers.dalryeo.ranking.dto.ScoreRankingResponse;
import com.ohgiraffers.dalryeo.record.entity.WeeklyUserStats;
import com.ohgiraffers.dalryeo.record.repository.WeeklyUserStatsRepository;
import com.ohgiraffers.dalryeo.record.repository.WeeklyUserStatsRepository.WeeklyRankingRow;
import com.ohgiraffers.dalryeo.tier.service.CurrentWeeklyTierResolver;
import com.ohgiraffers.dalryeo.tier.service.TierService;
import com.ohgiraffers.dalryeo.user.service.UserLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RankingService {

    private static final int DEFAULT_RANKING_LIMIT = 100;
    private static final String DEFAULT_TIER_CODE = "TURTLE";
    private static final String DEFAULT_TIER_GRADE = "B";

    private final WeeklyUserStatsRepository weeklyUserStatsRepository;
    private final UserLookupService userLookupService;
    private final TierService tierService;
    private final CurrentWeeklyTierResolver currentWeeklyTierResolver;
    private final ServiceDateProvider serviceDateProvider;

    // 점수 기준 주간 랭킹 목록을 조회한다.
    public List<ScoreRankingResponse> getWeeklyScoreRanking() {
        LocalDate weekStart = currentWeekStart();
        List<WeeklyRankingRow> rankingRows = weeklyUserStatsRepository.findScoreRankingRows(
                weekStart,
                DEFAULT_RANKING_LIMIT
        );
        Map<Long, CurrentWeeklyTierResolver.CurrentTier> currentTiers = resolveCurrentTiers(rankingRows, weekStart);

        return IntStream.range(0, rankingRows.size())
                .mapToObj(index -> toScoreRankingResponse(index, rankingRows.get(index), currentTiers))
                .collect(Collectors.toList());
    }

    // 거리 기준 주간 랭킹 목록을 조회한다.
    public List<DistanceRankingResponse> getWeeklyDistanceRanking() {
        LocalDate weekStart = currentWeekStart();
        List<WeeklyRankingRow> rankingRows = weeklyUserStatsRepository.findDistanceRankingRows(
                weekStart,
                DEFAULT_RANKING_LIMIT
        );
        Map<Long, CurrentWeeklyTierResolver.CurrentTier> currentTiers = resolveCurrentTiers(rankingRows, weekStart);

        return IntStream.range(0, rankingRows.size())
                .mapToObj(index -> toDistanceRankingResponse(index, rankingRows.get(index), currentTiers))
                .collect(Collectors.toList());
    }

    // 로그인 사용자의 이번 주 랭킹 정보를 조회한다.
    public RankingMeResponse getMyRanking(Long userId) {
        User user = userLookupService.getActiveById(userId);

        LocalDate weekStart = currentWeekStart();
        return weeklyUserStatsRepository.findByUserIdAndWeekStartDate(userId, weekStart)
                .filter(WeeklyUserStats::hasRecords)
                .map(stats -> buildMyRankingFromStats(user, weekStart, stats))
                .orElseGet(() -> buildEmptyMyRanking(user, weekStart));
    }

    // 점수 랭킹 조회 row를 응답 DTO로 변환한다.
    private ScoreRankingResponse toScoreRankingResponse(
            int index,
            WeeklyRankingRow row,
            Map<Long, CurrentWeeklyTierResolver.CurrentTier> currentTiers
    ) {
        double tierScore = row.getTierScoreAsDouble();
        TierDisplay tierDisplay = resolveTierDisplay(row.getUserId(), currentTiers);
        return ScoreRankingResponse.builder()
                .rank(index + 1)
                .nickname(row.getNickname())
                .tierCode(tierDisplay.tierCode())
                .tierGrade(tierDisplay.tierGrade())
                .defaultProfileImage(tierDisplay.defaultProfileImage())
                .tierScore(tierScore)
                .weeklyAvgPace(row.getAvgPaceSecPerKm())
                .weeklyDistance(row.getTotalDistanceKmAsDouble())
                .build();
    }

    // 거리 랭킹 조회 row를 응답 DTO로 변환한다.
    private DistanceRankingResponse toDistanceRankingResponse(
            int index,
            WeeklyRankingRow row,
            Map<Long, CurrentWeeklyTierResolver.CurrentTier> currentTiers
    ) {
        TierDisplay tierDisplay = resolveTierDisplay(row.getUserId(), currentTiers);
        return DistanceRankingResponse.builder()
                .rank(index + 1)
                .nickname(row.getNickname())
                .weeklyDistance(row.getTotalDistanceKmAsDouble())
                .weeklyAvgPace(row.getAvgPaceSecPerKm())
                .tierCode(tierDisplay.tierCode())
                .tierGrade(tierDisplay.tierGrade())
                .defaultProfileImage(tierDisplay.defaultProfileImage())
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
        TierDisplay tierDisplay = resolveTierDisplay(user.getId(), weekStart);

        return RankingMeResponse.builder()
                .nickname(user.getNickname())
                .scoreRank(scoreRank)
                .distanceRank(distanceRank)
                .tierCode(tierDisplay.tierCode())
                .tierGrade(tierDisplay.tierGrade())
                .defaultProfileImage(tierDisplay.defaultProfileImage())
                .tierScore(stats.tierScoreAsDouble())
                .weeklyAvgPace(stats.getAvgPaceSecPerKm())
                .weeklyDistance(stats.totalDistanceAsDouble())
                .build();
    }

    // 이번 주 기록이 없는 사용자의 기본 내 랭킹 응답을 만든다.
    private RankingMeResponse buildEmptyMyRanking(User user, LocalDate weekStart) {
        TierDisplay tierDisplay = resolveTierDisplay(user.getId(), weekStart);
        return RankingMeResponse.builder()
                .nickname(user.getNickname())
                .scoreRank(null)
                .distanceRank(null)
                .tierCode(tierDisplay.tierCode())
                .tierGrade(tierDisplay.tierGrade())
                .defaultProfileImage(tierDisplay.defaultProfileImage())
                .tierScore(0.0)
                .weeklyAvgPace(0)
                .weeklyDistance(0.0)
                .build();
    }

    private Map<Long, CurrentWeeklyTierResolver.CurrentTier> resolveCurrentTiers(
            List<WeeklyRankingRow> rows,
            LocalDate weekStart
    ) {
        Set<Long> userIds = rows.stream()
                .map(WeeklyRankingRow::getUserId)
                .collect(Collectors.toSet());
        return currentWeeklyTierResolver.resolveAll(userIds, weekStart);
    }

    private TierDisplay resolveTierDisplay(
            Long userId,
            Map<Long, CurrentWeeklyTierResolver.CurrentTier> currentTiers
    ) {
        CurrentWeeklyTierResolver.CurrentTier currentTier = currentTiers.get(userId);
        if (currentTier != null) {
            return TierDisplay.from(currentTier);
        }
        return defaultTierDisplay();
    }

    private TierDisplay resolveTierDisplay(Long userId, LocalDate weekStart) {
        return currentWeeklyTierResolver.resolve(userId, weekStart)
                .map(TierDisplay::from)
                .orElseGet(this::defaultTierDisplay);
    }

    private TierDisplay defaultTierDisplay() {
        String defaultProfileImage = tierService.findDefaultProfileImageByTierCode(DEFAULT_TIER_CODE)
                .orElse(null);
        return new TierDisplay(DEFAULT_TIER_CODE, DEFAULT_TIER_GRADE, defaultProfileImage);
    }

    // 나보다 앞선 사용자 수를 1부터 시작하는 순위로 변환한다.
    private int toRank(long aheadCount) {
        return Math.toIntExact(aheadCount + 1);
    }

    // 오늘 날짜를 기준으로 이번 주 월요일을 구한다.
    private LocalDate currentWeekStart() {
        return serviceDateProvider.currentWeekStart();
    }

    private record TierDisplay(
            String tierCode,
            String tierGrade,
            String defaultProfileImage
    ) {
        private static TierDisplay from(CurrentWeeklyTierResolver.CurrentTier currentTier) {
            return new TierDisplay(
                    currentTier.tierCode(),
                    currentTier.tierGrade(),
                    currentTier.defaultProfileImage()
            );
        }

    }
}
