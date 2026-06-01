package com.ohgiraffers.dalryeo.weeklytier.service;

import com.ohgiraffers.dalryeo.record.entity.WeeklyUserStats;
import com.ohgiraffers.dalryeo.record.repository.WeeklyUserStatsRepository;
import com.ohgiraffers.dalryeo.tier.service.TierService;
import com.ohgiraffers.dalryeo.weeklytier.repository.WeeklyTierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WeeklyTierFinalizationTransactionService {

    private final WeeklyUserStatsRepository weeklyUserStatsRepository;
    private final WeeklyTierRepository weeklyTierRepository;
    private final TierService tierService;

    @Transactional
    public WeekResult finalizeWeek(LocalDate sourceWeekStartDate, LocalDate snapshotWeekStartDate) {
        List<WeeklyUserStats> targets = weeklyUserStatsRepository.findFinalizationTargets(sourceWeekStartDate);
        int changed = 0;

        for (WeeklyUserStats stats : targets) {
            double score = stats.tierScoreAsDouble();
            TierService.TierInfo tierInfo = tierService.resolveByScore(score);
            changed += weeklyTierRepository.upsertFinalizedTier(
                    stats.getUserId(),
                    snapshotWeekStartDate,
                    tierInfo.tierCode(),
                    storedScore(stats.getTierScore())
            );
        }

        return new WeekResult(sourceWeekStartDate, snapshotWeekStartDate, targets.size(), changed);
    }

    private int storedScore(BigDecimal score) {
        if (score == null) {
            return 0;
        }
        return score.movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
    }

    public record WeekResult(
            LocalDate sourceWeekStartDate,
            LocalDate snapshotWeekStartDate,
            int candidates,
            int changed
    ) {
    }
}
