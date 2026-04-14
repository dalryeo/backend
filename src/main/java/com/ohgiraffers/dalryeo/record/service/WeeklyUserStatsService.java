package com.ohgiraffers.dalryeo.record.service;

import com.ohgiraffers.dalryeo.record.entity.RunningRecord;
import com.ohgiraffers.dalryeo.record.entity.WeeklyUserStats;
import com.ohgiraffers.dalryeo.record.repository.WeeklyUserStatsRepository;
import com.ohgiraffers.dalryeo.tier.service.TierScoreCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
    private final TierScoreCalculator tierScoreCalculator;

    @Transactional(propagation = Propagation.MANDATORY)
    public void applyRecord(RunningRecord record) {
        LocalDate weekStartDate = resolveWeekStart(record);
        BigDecimal distanceKm = decimal(record.getDistanceKm(), 3);
        BigDecimal weightedPaceSum = distanceKm
                .multiply(BigDecimal.valueOf(record.getAvgPaceSecPerKm()))
                .setScale(3, RoundingMode.HALF_UP);
        BigDecimal tierScore = decimal(
                tierScoreCalculator.calculateRecordScore(record.getDistanceKm(), record.getAvgPaceSecPerKm()),
                2
        );

        weeklyUserStatsRepository.upsertRecordDelta(
                record.getUserId(),
                weekStartDate,
                distanceKm,
                record.getDurationSec(),
                weightedPaceSum,
                record.getAvgPaceSecPerKm(),
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
}
