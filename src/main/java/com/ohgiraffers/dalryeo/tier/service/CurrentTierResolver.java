package com.ohgiraffers.dalryeo.tier.service;

import com.ohgiraffers.dalryeo.record.entity.RunningRecord;
import com.ohgiraffers.dalryeo.record.entity.WeeklyUserStats;
import com.ohgiraffers.dalryeo.record.repository.WeeklyUserStatsRepository;
import com.ohgiraffers.dalryeo.weeklytier.entity.WeeklyTier;
import com.ohgiraffers.dalryeo.weeklytier.repository.WeeklyTierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CurrentTierResolver {

    private final WeeklyUserStatsRepository weeklyUserStatsRepository;
    private final WeeklyTierRepository weeklyTierRepository;
    private final TierService tierService;
    private final TierScoreCalculator tierScoreCalculator;

    public Optional<CurrentTier> resolve(Long userId) {
        LocalDate weekStart = currentWeekStart();
        return resolve(userId, weekStart);
    }

    public Optional<CurrentTier> resolve(Long userId, LocalDate weekStart) {
        return weeklyUserStatsRepository.findByUserIdAndWeekStartDate(userId, weekStart)
                .filter(WeeklyUserStats::hasRecords)
                .map(this::fromWeeklyUserStats)
                .or(() -> weeklyTierRepository.findByUserIdAndWeekStartDate(userId, weekStart)
                        .map(this::fromWeeklyTier));
    }

    public Optional<CurrentTier> resolve(Long userId, LocalDate weekStart, List<RunningRecord> weeklyRecords) {
        if (!weeklyRecords.isEmpty()) {
            double weeklyTierScore = calculateWeeklyTierScoreFromRecords(weeklyRecords);
            TierService.TierInfo tierInfo = tierService.resolveByScore(weeklyTierScore);
            return Optional.of(CurrentTier.from(tierInfo, weeklyTierScore));
        }

        return resolve(userId, weekStart);
    }

    private CurrentTier fromWeeklyUserStats(WeeklyUserStats weeklyUserStats) {
        double score = weeklyUserStats.tierScoreAsDouble();
        TierService.TierInfo tierInfo = tierService.resolveByScore(score);
        return CurrentTier.from(tierInfo, score);
    }

    private CurrentTier fromWeeklyTier(WeeklyTier weeklyTier) {
        double score = scoreFromInt(weeklyTier.getTierScore());
        TierService.TierInfo tierInfo = tierService.resolveByTierCodeAndScore(weeklyTier.getTierCode(), score);
        return CurrentTier.from(tierInfo, score);
    }

    private LocalDate currentWeekStart() {
        return LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private double calculateWeeklyTierScoreFromRecords(List<RunningRecord> records) {
        double totalScore = records.stream()
                .mapToDouble(record -> tierScoreCalculator.calculateRecordScore(
                        record.getDistanceKm(),
                        record.getAvgPaceSecPerKm()
                ))
                .sum();
        return tierScoreCalculator.calculateWeeklyScore(totalScore, records.size());
    }

    private double scoreFromInt(Integer score) {
        if (score == null) {
            return 0.0;
        }
        return BigDecimal.valueOf(score)
                .movePointLeft(2)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    public record CurrentTier(
            String tierCode,
            String displayName,
            String tierGrade,
            Double score,
            String defaultProfileImage
    ) {
        private static CurrentTier from(TierService.TierInfo tierInfo, double score) {
            return new CurrentTier(
                    tierInfo.tierCode(),
                    tierInfo.displayName(),
                    tierInfo.tierGrade(),
                    score,
                    tierInfo.defaultProfileImage()
            );
        }
    }
}
