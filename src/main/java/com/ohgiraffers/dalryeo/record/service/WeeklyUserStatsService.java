package com.ohgiraffers.dalryeo.record.service;

import com.ohgiraffers.dalryeo.record.entity.RunningRecord;
import com.ohgiraffers.dalryeo.record.entity.WeeklyUserStats;
import com.ohgiraffers.dalryeo.record.repository.RunningRecordRepository;
import com.ohgiraffers.dalryeo.record.repository.WeeklyUserStatsRepository;
import com.ohgiraffers.dalryeo.tier.service.TierScoreCalculator;
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
public class WeeklyUserStatsService {

    private final WeeklyUserStatsRepository weeklyUserStatsRepository;
    private final RunningRecordRepository runningRecordRepository;
    private final TierScoreCalculator tierScoreCalculator;

    @Transactional
    public void rebuildForRecord(RunningRecord record) {
        LocalDate weekStartDate = resolveWeekStart(record);
        rebuildUserWeek(record.getUserId(), weekStartDate);
    }

    @Transactional
    public void rebuildUserWeek(Long userId, LocalDate weekStartDate) {
        RunningRecordRepository.UserWeekAggregate aggregate = runningRecordRepository.aggregateUserWeek(
                userId,
                weekStartDate.atStartOfDay(),
                weekStartDate.plusWeeks(1).atStartOfDay()
        );

        Integer runCount = valueOrZero(aggregate.getRunCount());
        BigDecimal totalDistanceKm = decimal(aggregate.getTotalDistanceKm(), 3);
        Integer totalDurationSec = valueOrZero(aggregate.getTotalDurationSec());
        BigDecimal weightedPaceSum = decimal(aggregate.getWeightedPaceSum(), 3);
        BigDecimal tierScoreSum = decimal(aggregate.getTierScoreSum(), 2);
        Integer avgPaceSecPerKm = calculateAveragePaceSecPerKm(weightedPaceSum, totalDistanceKm);
        BigDecimal tierScore = decimal(tierScoreCalculator.calculateWeeklyScore(tierScoreSum, runCount), 2);

        weeklyUserStatsRepository.replaceAggregate(
                userId,
                weekStartDate,
                runCount,
                totalDistanceKm,
                totalDurationSec,
                weightedPaceSum,
                tierScoreSum,
                avgPaceSecPerKm,
                tierScore
        );
    }

    public Optional<WeeklyUserStats> findByUserIdAndWeekStartDate(Long userId, LocalDate weekStartDate) {
        return weeklyUserStatsRepository.findByUserIdAndWeekStartDate(userId, weekStartDate)
                .filter(WeeklyUserStats::hasRecords);
    }

    public List<WeeklyUserStats> findByUserIdAndWeekStartDateBetween(
            Long userId,
            LocalDate startWeek,
            LocalDate endWeek
    ) {
        return weeklyUserStatsRepository.findByUserIdAndWeekStartDateBetweenOrderByWeekStartDateAsc(
                userId,
                startWeek,
                endWeek
        );
    }

    private LocalDate resolveWeekStart(RunningRecord record) {
        return record.getStartAt()
                .toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private BigDecimal decimal(double value, int scale) {
        return BigDecimal.valueOf(value)
                .setScale(scale, RoundingMode.HALF_UP);
    }

    private BigDecimal decimal(BigDecimal value, int scale) {
        return (value == null ? BigDecimal.ZERO : value)
                .setScale(scale, RoundingMode.HALF_UP);
    }

    private Integer valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private Integer calculateAveragePaceSecPerKm(BigDecimal weightedPaceSum, BigDecimal totalDistanceKm) {
        if (totalDistanceKm.compareTo(BigDecimal.ZERO) == 0) {
            return 0;
        }
        return weightedPaceSum.divide(totalDistanceKm, 0, RoundingMode.HALF_UP)
                .intValue();
    }
}
