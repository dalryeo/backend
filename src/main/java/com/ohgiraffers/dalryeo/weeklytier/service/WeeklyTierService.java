package com.ohgiraffers.dalryeo.weeklytier.service;

import com.ohgiraffers.dalryeo.record.repository.RunningRecordRepository;
import com.ohgiraffers.dalryeo.weeklytier.dto.WeeklyTierResponse;
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
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class WeeklyTierService {

    private final WeeklyTierRepository weeklyTierRepository;
    private final RunningRecordRepository runningRecordRepository;

    @Transactional(readOnly = true)
    public WeeklyTierResponse getCurrentWeeklyTier(Long userId) {
        LocalDate weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDateTime startDateTime = weekStart.atStartOfDay();
        LocalDateTime endDateTime = weekStart.plusDays(7).atStartOfDay();

        boolean hasWeeklyRecords = runningRecordRepository.existsByUserIdAndWeekRange(
                userId, startDateTime, endDateTime);
        if (hasWeeklyRecords) {
            return null;
        }

        Optional<WeeklyTier> weeklyTier = weeklyTierRepository.findByUserIdAndWeekStartDate(userId, weekStart);
        if (weeklyTier.isEmpty()) {
            return null;
        }

        WeeklyTier tier = weeklyTier.get();
        double score = scoreFromInt(tier.getTierScore());
        String tierGrade = resolveTierGrade(score);

        return WeeklyTierResponse.builder()
                .weekStartDate(tier.getWeekStartDate())
                .tierCode(tier.getTierCode())
                .tierGrade(tierGrade)
                .tierScore(score)
                .build();
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

    private String resolveTierGrade(double score) {
        if (score >= 1.50) {
            return gradeForRange(score, 1.64, 1.57, 1.50);
        } else if (score >= 1.20) {
            return gradeForRange(score, 1.39, 1.29, 1.20);
        } else if (score >= 1.00) {
            return gradeForRange(score, 1.13, 1.06, 1.00);
        } else if (score >= 0.86) {
            return gradeForRange(score, 0.95, 0.90, 0.86);
        } else if (score >= 0.75) {
            return gradeForRange(score, 0.82, 0.78, 0.75);
        } else if (score >= 0.67) {
            return gradeForRange(score, 0.72, 0.69, 0.67);
        } else if (score >= 0.60) {
            return gradeForRange(score, 0.64, 0.62, 0.60);
        } else if (score >= 0.55) {
            return gradeForRange(score, 0.58, 0.56, 0.55);
        } else if (score >= 0.46) {
            return gradeForRange(score, 0.52, 0.49, 0.46);
        }
        return null;
    }

    private String gradeForRange(double score, double goldMin, double silverMin, double bronzeMin) {
        if (score >= goldMin) {
            return "G";
        } else if (score >= silverMin) {
            return "S";
        } else if (score >= bronzeMin) {
            return "B";
        }
        return null;
    }
}
