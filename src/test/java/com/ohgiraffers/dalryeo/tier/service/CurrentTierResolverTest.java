package com.ohgiraffers.dalryeo.tier.service;

import com.ohgiraffers.dalryeo.common.time.ServiceDateProvider;
import com.ohgiraffers.dalryeo.record.entity.RunningRecord;
import com.ohgiraffers.dalryeo.weeklytier.entity.WeeklyTier;
import com.ohgiraffers.dalryeo.weeklytier.repository.WeeklyTierRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentTierResolverTest {

    @Mock
    private WeeklyTierRepository weeklyTierRepository;

    @Mock
    private TierService tierService;

    @Mock
    private ServiceDateProvider serviceDateProvider;

    @InjectMocks
    private CurrentTierResolver currentTierResolver;

    @Test
    void resolve_ignoresCurrentWeekRecordsAndUsesLatestFinalizedSnapshot() {
        Long userId = 1L;
        LocalDate currentWeekStart = LocalDate.of(2026, 6, 8);
        LocalDate previousSnapshotWeekStart = LocalDate.of(2026, 6, 1);
        RunningRecord runningRecord = RunningRecord.builder()
                .userId(userId)
                .platform("IOS")
                .distanceKm(5.0)
                .durationSec(1500)
                .avgPaceSecPerKm(300)
                .avgHeartRate(150)
                .caloriesKcal(300)
                .startAt(LocalDateTime.of(2026, 6, 2, 7, 0))
                .endAt(LocalDateTime.of(2026, 6, 2, 7, 25))
                .build();
        WeeklyTier weeklyTier = WeeklyTier.builder()
                .userId(userId)
                .weekStartDate(previousSnapshotWeekStart)
                .tierCode("TURTLE")
                .tierScore(45)
                .build();

        when(weeklyTierRepository.findTopByUserIdAndWeekStartDateLessThanEqualOrderByWeekStartDateDesc(
                userId,
                currentWeekStart
        )).thenReturn(Optional.of(weeklyTier));
        when(tierService.resolveByTierCodeAndScore("TURTLE", 0.45))
                .thenReturn(new TierService.TierInfo("TURTLE", "거북이", "B", "/profiles/tiers/turtle.png"));

        Optional<CurrentTierResolver.CurrentTier> result =
                currentTierResolver.resolve(userId, currentWeekStart, List.of(runningRecord));

        assertThat(result).isPresent();
        assertThat(result.get().tierCode()).isEqualTo("TURTLE");
        assertThat(result.get().tierGrade()).isEqualTo("B");
        assertThat(result.get().score()).isEqualTo(0.45);
        assertThat(result.get().defaultProfileImage()).isEqualTo("/profiles/tiers/turtle.png");
        verify(tierService, never()).resolveByScore(anyDouble());
    }

    @Test
    void resolve_returnsLatestFinalizedSnapshotAtOrBeforeRequestedWeekStart() {
        Long userId = 2L;
        LocalDate currentWeekStart = LocalDate.of(2026, 6, 8);
        WeeklyTier weeklyTier = WeeklyTier.builder()
                .userId(userId)
                .weekStartDate(LocalDate.of(2026, 6, 1))
                .tierCode("FOX")
                .tierScore(90)
                .build();

        when(weeklyTierRepository.findTopByUserIdAndWeekStartDateLessThanEqualOrderByWeekStartDateDesc(
                userId,
                currentWeekStart
        )).thenReturn(Optional.of(weeklyTier));
        when(tierService.resolveByTierCodeAndScore("FOX", 0.90))
                .thenReturn(new TierService.TierInfo("FOX", "여우", "S", "/profiles/tiers/fox.png"));

        Optional<CurrentTierResolver.CurrentTier> result = currentTierResolver.resolve(userId, currentWeekStart);

        assertThat(result).isPresent();
        assertThat(result.get().tierCode()).isEqualTo("FOX");
        assertThat(result.get().tierGrade()).isEqualTo("S");
        assertThat(result.get().score()).isEqualTo(0.90);
        assertThat(result.get().defaultProfileImage()).isEqualTo("/profiles/tiers/fox.png");
    }

    @Test
    void resolve_returnsEmptyWhenNoFinalizedSnapshotExists() {
        Long userId = 3L;
        LocalDate currentWeekStart = LocalDate.of(2026, 6, 8);

        when(weeklyTierRepository.findTopByUserIdAndWeekStartDateLessThanEqualOrderByWeekStartDateDesc(
                userId,
                currentWeekStart
        )).thenReturn(Optional.empty());

        Optional<CurrentTierResolver.CurrentTier> result = currentTierResolver.resolve(userId, currentWeekStart);

        assertThat(result).isEmpty();
    }

    @Test
    void resolve_usesServiceDateProviderForCurrentWeekStart() {
        Long userId = 4L;
        LocalDate currentWeekStart = LocalDate.of(2026, 6, 8);
        WeeklyTier weeklyTier = WeeklyTier.builder()
                .userId(userId)
                .weekStartDate(currentWeekStart)
                .tierCode("CHEETAH")
                .tierScore(157)
                .build();

        when(serviceDateProvider.currentWeekStart()).thenReturn(currentWeekStart);
        when(weeklyTierRepository.findTopByUserIdAndWeekStartDateLessThanEqualOrderByWeekStartDateDesc(
                userId,
                currentWeekStart
        )).thenReturn(Optional.of(weeklyTier));
        when(tierService.resolveByTierCodeAndScore("CHEETAH", 1.57))
                .thenReturn(new TierService.TierInfo("CHEETAH", "치타", "S", "/profiles/tiers/cheetah.png"));

        Optional<CurrentTierResolver.CurrentTier> result = currentTierResolver.resolve(userId);

        assertThat(result).isPresent();
        assertThat(result.get().tierCode()).isEqualTo("CHEETAH");
        assertThat(result.get().score()).isEqualTo(1.57);
    }
}
