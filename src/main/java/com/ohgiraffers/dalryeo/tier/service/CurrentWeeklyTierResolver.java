package com.ohgiraffers.dalryeo.tier.service;

import com.ohgiraffers.dalryeo.common.time.ServiceDateProvider;
import com.ohgiraffers.dalryeo.weeklytier.entity.WeeklyTier;
import com.ohgiraffers.dalryeo.weeklytier.repository.WeeklyTierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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

    public Map<Long, CurrentTier> resolveAll(Collection<Long> userIds, LocalDate weekStart) {
        Set<Long> distinctUserIds = userIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (distinctUserIds.isEmpty()) {
            return Map.of();
        }

        return weeklyTierRepository.findLatestSnapshotsByUserIdsAndWeekStartDateLessThanEqual(
                        distinctUserIds,
                        weekStart
                )
                .stream()
                .collect(Collectors.toMap(
                        WeeklyTierRepository.CurrentTierSnapshot::getUserId,
                        this::fromSnapshot,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private CurrentTier fromWeeklyTier(WeeklyTier weeklyTier) {
        double score = tierScoreCalculator.displayScoreFromStoredScore(weeklyTier.getTierScore());
        return fromTierCodeAndScore(weeklyTier.getTierCode(), score);
    }

    private CurrentTier fromSnapshot(WeeklyTierRepository.CurrentTierSnapshot snapshot) {
        double score = tierScoreCalculator.displayScoreFromStoredScore(snapshot.getTierScore());
        return fromTierCodeAndScore(snapshot.getTierCode(), score);
    }

    private CurrentTier fromTierCodeAndScore(String tierCode, double score) {
        TierService.TierInfo tierInfo = tierService.resolveByTierCodeAndScore(tierCode, score);
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
