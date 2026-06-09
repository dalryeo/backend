package com.ohgiraffers.dalryeo.weeklytier.service;

import com.ohgiraffers.dalryeo.common.time.ServiceDateProvider;
import com.ohgiraffers.dalryeo.tier.service.TierService;
import com.ohgiraffers.dalryeo.user.service.UserLookupService;
import com.ohgiraffers.dalryeo.weeklytier.dto.WeeklyTierResponse;
import com.ohgiraffers.dalryeo.weeklytier.entity.WeeklyTier;
import com.ohgiraffers.dalryeo.weeklytier.repository.WeeklyTierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional
public class WeeklyTierService {

    private final WeeklyTierRepository weeklyTierRepository;
    private final TierService tierService;
    private final UserLookupService userLookupService;
    private final ServiceDateProvider serviceDateProvider;

    @Transactional(readOnly = true)
    public WeeklyTierResponse getCurrentWeeklyTier(Long userId) {
        userLookupService.getActiveById(userId);

        LocalDate weekStart = serviceDateProvider.currentWeekStart();
        return weeklyTierRepository.findTopByUserIdAndWeekStartDateLessThanEqualOrderByWeekStartDateDesc(
                        userId,
                        weekStart
                )
                .map(this::toResponse)
                .orElse(null);
    }

    private WeeklyTierResponse toResponse(WeeklyTier tier) {
        double score = scoreFromInt(tier.getTierScore());
        TierService.TierInfo tierInfo = tierService.resolveByTierCodeAndScore(tier.getTierCode(), score);

        return WeeklyTierResponse.builder()
                .weekStartDate(tier.getWeekStartDate())
                .tierCode(tierInfo.tierCode())
                .tierGrade(tierInfo.tierGrade())
                .defaultProfileImage(tierInfo.defaultProfileImage())
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
