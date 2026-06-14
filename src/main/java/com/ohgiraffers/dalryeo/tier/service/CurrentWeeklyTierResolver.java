package com.ohgiraffers.dalryeo.tier.service;

import com.ohgiraffers.dalryeo.common.time.ServiceDateProvider;
import com.ohgiraffers.dalryeo.weeklytier.entity.WeeklyTier;
import com.ohgiraffers.dalryeo.weeklytier.repository.WeeklyTierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CurrentWeeklyTierResolver {

    private final WeeklyTierRepository weeklyTierRepository;
    private final TierService tierService;
    private final TierScoreCalculator tierScoreCalculator;
    private final ServiceDateProvider serviceDateProvider;

    public Optional<CurrentTier> resolve(Long userId) {
        return resolve(userId, serviceDateProvider.currentWeekStart());
    }

    public Optional<CurrentTier> resolve(Long userId, LocalDate weekStart) {
        return weeklyTierRepository.findTopByUserIdAndWeekStartDateLessThanEqualOrderByWeekStartDateDesc(
                        userId,
                        weekStart
                )
                .map(this::fromWeeklyTier);
    }

    private CurrentTier fromWeeklyTier(WeeklyTier weeklyTier) {
        double score = tierScoreCalculator.displayScoreFromStoredScore(weeklyTier.getTierScore());
        TierService.TierInfo tierInfo = tierService.resolveByTierCodeAndScore(weeklyTier.getTierCode(), score);
        return CurrentTier.from(tierInfo, score);
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
