package com.ohgiraffers.dalryeo.tier.service;

import com.ohgiraffers.dalryeo.record.entity.RunningRecord;
import com.ohgiraffers.dalryeo.record.repository.RunningRecordRepository;
import com.ohgiraffers.dalryeo.weeklytier.entity.WeeklyTier;
import com.ohgiraffers.dalryeo.weeklytier.repository.WeeklyTierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CurrentTierResolver {

    private final RunningRecordRepository runningRecordRepository;
    private final WeeklyTierRepository weeklyTierRepository;
    private final TierService tierService;

    public Optional<CurrentTier> resolve(Long userId) {
        LocalDate weekStart = currentWeekStart();
        return resolve(userId, weekStart);
    }

    public Optional<CurrentTier> resolve(Long userId, LocalDate weekStart) {
        LocalDateTime startDateTime = weekStart.atStartOfDay();
        LocalDateTime endDateTime = weekStart.plusDays(7).atStartOfDay();
        List<RunningRecord> weeklyRecords = runningRecordRepository.findByUserIdAndWeekRange(
                userId,
                startDateTime,
                endDateTime
        );
        return resolve(userId, weekStart, weeklyRecords);
    }

    public Optional<CurrentTier> resolve(Long userId, LocalDate weekStart, List<RunningRecord> weeklyRecords) {
        if (!weeklyRecords.isEmpty()) {
            double weeklyTierScore = calculateWeeklyTierScore(weeklyRecords);
            TierService.TierInfo tierInfo = tierService.resolveByScore(weeklyTierScore);
            return Optional.of(CurrentTier.from(tierInfo, weeklyTierScore));
        }

        return weeklyTierRepository.findByUserIdAndWeekStartDate(userId, weekStart)
                .map(this::fromWeeklyTier);
    }

    private CurrentTier fromWeeklyTier(WeeklyTier weeklyTier) {
        double score = scoreFromInt(weeklyTier.getTierScore());
        TierService.TierInfo tierInfo = tierService.resolveByTierCodeAndScore(weeklyTier.getTierCode(), score);
        return CurrentTier.from(tierInfo, score);
    }

    private LocalDate currentWeekStart() {
        return LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private double calculateWeeklyTierScore(List<RunningRecord> records) {
        double totalScore = records.stream()
                .mapToDouble(record -> calculateTierScore(record.getDistanceKm(), record.getAvgPaceSecPerKm()))
                .sum();
        return round2(totalScore / records.size());
    }

    private double calculateTierScore(double distanceKm, int paceSecPerKm) {
        double paceMinutes = round2(paceSecPerKm / 60.0);
        double baseScore = round2(6.00 / paceMinutes);
        double distanceWeight = getDistanceWeight(distanceKm);
        return round2(baseScore * distanceWeight);
    }

    private double getDistanceWeight(double distanceKm) {
        if (distanceKm < 1.00) {
            return 0.50;
        } else if (distanceKm < 2.00) {
            return 0.60;
        } else if (distanceKm < 3.00) {
            return 0.70;
        } else if (distanceKm < 5.00) {
            return 1.00;
        } else if (distanceKm < 7.00) {
            return 1.03;
        } else if (distanceKm < 9.00) {
            return 1.05;
        } else if (distanceKm < 11.00) {
            return 1.06;
        } else if (distanceKm < 15.00) {
            return 1.07;
        } else if (distanceKm < 25.00) {
            return 1.08;
        } else if (distanceKm < 40.00) {
            return 1.09;
        }
        return 1.10;
    }

    private double round2(double value) {
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
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
