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
        List<RunningRecord> records = runningRecordRepository.findByUserIdAndWeekRange(
                userId,
                weekStartDate.atStartOfDay(),
                weekStartDate.plusWeeks(1).atStartOfDay()
        );

        Integer runCount = records.size();
        BigDecimal totalDistanceKm = BigDecimal.ZERO;
        Integer totalDurationSec = 0;
        BigDecimal weightedPaceSum = BigDecimal.ZERO;
        BigDecimal tierScoreSum = BigDecimal.ZERO;

        for (RunningRecord record : records) {
            BigDecimal distanceKm = decimal(record.getDistanceKm(), 3);

            totalDistanceKm = totalDistanceKm.add(distanceKm);
            totalDurationSec += record.getDurationSec();
            weightedPaceSum = weightedPaceSum.add(
                    distanceKm.multiply(BigDecimal.valueOf(record.getAvgPaceSecPerKm()))
            );
            tierScoreSum = tierScoreSum.add(decimal(
                    tierScoreCalculator.calculateRecordScore(
                            record.getDistanceKm(),
                            record.getAvgPaceSecPerKm()
                    ),
                    2
            ));
        }

        totalDistanceKm = decimal(totalDistanceKm, 3);
        weightedPaceSum = decimal(weightedPaceSum, 3);
        tierScoreSum = decimal(tierScoreSum, 2);

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

    private Integer calculateAveragePaceSecPerKm(BigDecimal weightedPaceSum, BigDecimal totalDistanceKm) {
        if (totalDistanceKm.compareTo(BigDecimal.ZERO) == 0) {
            return 0;
        }
        return weightedPaceSum.divide(totalDistanceKm, 0, RoundingMode.HALF_UP)
                .intValue();
    }
}
