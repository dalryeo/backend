package com.ohgiraffers.dalryeo.tier.service;

import com.ohgiraffers.dalryeo.record.entity.RunningRecord;
import com.ohgiraffers.dalryeo.record.repository.WeeklyUserStatsRepository;
import com.ohgiraffers.dalryeo.weeklytier.entity.WeeklyTier;
import com.ohgiraffers.dalryeo.weeklytier.repository.WeeklyTierRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentTierResolverTest {

    @Mock
    private WeeklyUserStatsRepository weeklyUserStatsRepository;

    @Mock
    private WeeklyTierRepository weeklyTierRepository;

    @Mock
    private TierService tierService;

    @Spy
    private TierScoreCalculator tierScoreCalculator = new TierScoreCalculator();

    @InjectMocks
    private CurrentTierResolver currentTierResolver;

    @Test
    void resolve_usesCurrentWeekRecordsBeforeWeeklyTierSnapshot() {
        Long userId = 1L;
        LocalDate weekStart = LocalDate.of(2026, 3, 30);
        RunningRecord runningRecord = RunningRecord.builder()
                .userId(userId)
                .platform("IOS")
                .distanceKm(5.0)
                .durationSec(1500)
                .avgPaceSecPerKm(300)
                .avgHeartRate(150)
                .caloriesKcal(300)
                .startAt(LocalDateTime.of(2026, 3, 31, 7, 0))
                .endAt(LocalDateTime.of(2026, 3, 31, 7, 25))
                .build();

        when(tierService.resolveByScore(1.24))
                .thenReturn(new TierService.TierInfo("DEER", "사슴", "B", "/profiles/tiers/deer.png"));

        Optional<CurrentTierResolver.CurrentTier> result = currentTierResolver.resolve(userId, weekStart, List.of(runningRecord));

        assertThat(result).isPresent();
        assertThat(result.get().tierCode()).isEqualTo("DEER");
        assertThat(result.get().tierGrade()).isEqualTo("B");
        assertThat(result.get().score()).isEqualTo(1.24);
        assertThat(result.get().defaultProfileImage()).isEqualTo("/profiles/tiers/deer.png");
    }

    @Test
    void resolve_fallsBackToWeeklyTierSnapshotWhenCurrentWeekRecordsDoNotExist() {
        Long userId = 2L;
        LocalDate weekStart = LocalDate.of(2026, 3, 30);
        WeeklyTier weeklyTier = WeeklyTier.builder()
                .userId(userId)
                .weekStartDate(weekStart)
                .tierCode("FOX")
                .tierScore(90)
                .build();

        when(weeklyTierRepository.findByUserIdAndWeekStartDate(userId, weekStart))
                .thenReturn(Optional.of(weeklyTier));
        when(tierService.resolveByTierCodeAndScore("FOX", 0.90))
                .thenReturn(new TierService.TierInfo("FOX", "여우", "S", "/profiles/tiers/fox.png"));

        Optional<CurrentTierResolver.CurrentTier> result = currentTierResolver.resolve(userId, weekStart, List.of());

        assertThat(result).isPresent();
        assertThat(result.get().tierCode()).isEqualTo("FOX");
        assertThat(result.get().tierGrade()).isEqualTo("S");
        assertThat(result.get().score()).isEqualTo(0.90);
        assertThat(result.get().defaultProfileImage()).isEqualTo("/profiles/tiers/fox.png");
    }

    @Test
    void resolve_returnsEmptyWhenNoCurrentWeekRecordsAndNoWeeklyTierSnapshotExist() {
        Long userId = 3L;
        LocalDate weekStart = LocalDate.of(2026, 3, 30);

        when(weeklyTierRepository.findByUserIdAndWeekStartDate(userId, weekStart))
                .thenReturn(Optional.empty());

        Optional<CurrentTierResolver.CurrentTier> result = currentTierResolver.resolve(userId, weekStart, List.of());

        assertThat(result).isEmpty();
    }
}
