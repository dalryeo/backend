package com.ohgiraffers.dalryeo.weeklytier.service;

import com.ohgiraffers.dalryeo.record.entity.WeeklyUserStats;
import com.ohgiraffers.dalryeo.record.repository.WeeklyUserStatsRepository;
import com.ohgiraffers.dalryeo.tier.service.TierService;
import com.ohgiraffers.dalryeo.weeklytier.repository.WeeklyTierRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeeklyTierFinalizationTransactionServiceTest {

    @Mock
    private WeeklyUserStatsRepository weeklyUserStatsRepository;

    @Mock
    private WeeklyTierRepository weeklyTierRepository;

    @Mock
    private TierService tierService;

    @InjectMocks
    private WeeklyTierFinalizationTransactionService transactionService;

    @Test
    void finalizeWeek_resolvesTierAndUpsertsSnapshot() {
        LocalDate sourceWeekStart = LocalDate.of(2026, 5, 25);
        LocalDate snapshotWeekStart = LocalDate.of(2026, 6, 1);
        WeeklyUserStats stats = weeklyStats(1L, sourceWeekStart, BigDecimal.valueOf(1.57));
        when(weeklyUserStatsRepository.findFinalizationTargets(sourceWeekStart)).thenReturn(List.of(stats));
        when(tierService.resolveByScore(1.57))
                .thenReturn(new TierService.TierInfo("CHEETAH", "치타", "S", "/profiles/tiers/cheetah.png"));
        when(weeklyTierRepository.upsertFinalizedTier(1L, snapshotWeekStart, "CHEETAH", 157))
                .thenReturn(1);

        WeeklyTierFinalizationTransactionService.WeekResult result = transactionService.finalizeWeek(
                sourceWeekStart,
                snapshotWeekStart
        );

        assertThat(result.sourceWeekStartDate()).isEqualTo(sourceWeekStart);
        assertThat(result.snapshotWeekStartDate()).isEqualTo(snapshotWeekStart);
        assertThat(result.candidates()).isEqualTo(1);
        assertThat(result.changed()).isEqualTo(1);
        verify(weeklyTierRepository).upsertFinalizedTier(1L, snapshotWeekStart, "CHEETAH", 157);
    }

    @Test
    void finalizeWeek_doesNothingWhenNoFinalizationTargetsExist() {
        LocalDate sourceWeekStart = LocalDate.of(2026, 5, 25);
        LocalDate snapshotWeekStart = LocalDate.of(2026, 6, 1);
        when(weeklyUserStatsRepository.findFinalizationTargets(sourceWeekStart)).thenReturn(List.of());

        WeeklyTierFinalizationTransactionService.WeekResult result = transactionService.finalizeWeek(
                sourceWeekStart,
                snapshotWeekStart
        );

        assertThat(result.sourceWeekStartDate()).isEqualTo(sourceWeekStart);
        assertThat(result.snapshotWeekStartDate()).isEqualTo(snapshotWeekStart);
        assertThat(result.candidates()).isZero();
        assertThat(result.changed()).isZero();
        verifyNoInteractions(tierService, weeklyTierRepository);
    }

    private WeeklyUserStats weeklyStats(Long userId, LocalDate weekStart, BigDecimal tierScore) {
        return WeeklyUserStats.builder()
                .userId(userId)
                .weekStartDate(weekStart)
                .runCount(1)
                .totalDistanceKm(BigDecimal.valueOf(5.000))
                .totalDurationSec(1500)
                .weightedPaceSum(BigDecimal.valueOf(1500.000))
                .avgPaceSecPerKm(300)
                .tierScoreSum(tierScore)
                .tierScore(tierScore)
                .build();
    }
}
