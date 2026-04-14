package com.ohgiraffers.dalryeo.weeklytier.service;

import com.ohgiraffers.dalryeo.record.entity.WeeklyUserStats;
import com.ohgiraffers.dalryeo.record.repository.WeeklyUserStatsRepository;
import com.ohgiraffers.dalryeo.tier.service.TierService;
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
import java.time.temporal.TemporalAdjusters;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class WeeklyTierService {

    private final WeeklyTierRepository weeklyTierRepository;
    private final WeeklyUserStatsRepository weeklyUserStatsRepository;
    private final TierService tierService;

    @Transactional(readOnly = true)
    public WeeklyTierResponse getCurrentWeeklyTier(Long userId) {
        LocalDate weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        boolean hasCurrentWeeklyStats = weeklyUserStatsRepository.findByUserIdAndWeekStartDate(userId, weekStart)
                .filter(WeeklyUserStats::hasRecords)
                .isPresent();
        if (hasCurrentWeeklyStats) {
            return null;
        }

        Optional<WeeklyTier> weeklyTier = weeklyTierRepository.findByUserIdAndWeekStartDate(userId, weekStart);
        if (weeklyTier.isEmpty()) {
            return null;
        }

        WeeklyTier tier = weeklyTier.get();
        double score = scoreFromInt(tier.getTierScore());
        TierService.TierInfo tierInfo = tierService.resolveByTierCodeAndScore(tier.getTierCode(), score);

        return WeeklyTierResponse.builder()
                .weekStartDate(tier.getWeekStartDate())
                .tierCode(tierInfo.tierCode())
                .tierGrade(tierInfo.tierGrade())
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
}
