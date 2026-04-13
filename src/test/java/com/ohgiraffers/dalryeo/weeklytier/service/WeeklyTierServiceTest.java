package com.ohgiraffers.dalryeo.weeklytier.service;

import com.ohgiraffers.dalryeo.record.entity.WeeklyUserStats;
import com.ohgiraffers.dalryeo.record.repository.WeeklyUserStatsRepository;
import com.ohgiraffers.dalryeo.tier.service.TierService;
import com.ohgiraffers.dalryeo.weeklytier.dto.WeeklyTierResponse;
import com.ohgiraffers.dalryeo.weeklytier.entity.WeeklyTier;
import com.ohgiraffers.dalryeo.weeklytier.repository.WeeklyTierRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeeklyTierServiceTest {

    @Mock
    private WeeklyTierRepository weeklyTierRepository;

    @Mock
    private WeeklyUserStatsRepository weeklyUserStatsRepository;

    @Mock
    private TierService tierService;

    @InjectMocks
    private WeeklyTierService weeklyTierService;

    @Test
    void getCurrentWeeklyTier_returnsNullWhenCurrentWeekRecordExists() {
        Long userId = 1L;
        LocalDate weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        when(weeklyUserStatsRepository.findByUserIdAndWeekStartDate(userId, weekStart))
                .thenReturn(Optional.of(weeklyStats(userId, weekStart)));

        WeeklyTierResponse response = weeklyTierService.getCurrentWeeklyTier(userId);

        assertThat(response).isNull();
    }

    @Test
    void getCurrentWeeklyTier_returnsNullWhenNoRecordAndNoWeeklyTierSnapshot() {
        Long userId = 2L;
        LocalDate weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        when(weeklyUserStatsRepository.findByUserIdAndWeekStartDate(userId, weekStart))
                .thenReturn(Optional.empty());
        when(weeklyTierRepository.findByUserIdAndWeekStartDate(userId, weekStart))
                .thenReturn(Optional.empty());

        WeeklyTierResponse response = weeklyTierService.getCurrentWeeklyTier(userId);

        assertThat(response).isNull();
    }

    @Test
    void getCurrentWeeklyTier_returnsSnapshotWhenNoCurrentWeekRecordExists() {
        Long userId = 3L;
        LocalDate weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        WeeklyTier weeklyTier = WeeklyTier.builder()
                .userId(userId)
                .weekStartDate(weekStart)
                .tierCode("CHEETAH")
                .tierScore(157)
                .build();

        when(weeklyUserStatsRepository.findByUserIdAndWeekStartDate(userId, weekStart))
                .thenReturn(Optional.empty());
        when(weeklyTierRepository.findByUserIdAndWeekStartDate(userId, weekStart))
                .thenReturn(Optional.of(weeklyTier));
        when(tierService.resolveByTierCodeAndScore("CHEETAH", 1.57))
                .thenReturn(new TierService.TierInfo("CHEETAH", "치타", "S", "/profiles/tiers/cheetah.png"));

        WeeklyTierResponse response = weeklyTierService.getCurrentWeeklyTier(userId);

        assertThat(response).isNotNull();
        assertThat(response.getWeekStartDate()).isEqualTo(weekStart);
        assertThat(response.getTierCode()).isEqualTo("CHEETAH");
        assertThat(response.getTierGrade()).isEqualTo("S");
        assertThat(response.getTierScore()).isEqualTo(1.57);
    }

    private WeeklyUserStats weeklyStats(Long userId, LocalDate weekStart) {
        return WeeklyUserStats.builder()
                .userId(userId)
                .weekStartDate(weekStart)
                .runCount(1)
                .totalDistanceKm(BigDecimal.valueOf(5.000))
                .totalDurationSec(1500)
                .weightedPaceSum(BigDecimal.valueOf(1500.000))
                .avgPaceSecPerKm(300)
                .tierScoreSum(BigDecimal.valueOf(1.24))
                .tierScore(BigDecimal.valueOf(1.24))
                .build();
    }
}
