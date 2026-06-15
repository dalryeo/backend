package com.ohgiraffers.dalryeo.tier.service;

import com.ohgiraffers.dalryeo.common.time.ServiceDateProvider;
import com.ohgiraffers.dalryeo.weeklytier.entity.WeeklyTier;
import com.ohgiraffers.dalryeo.weeklytier.repository.WeeklyTierRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentWeeklyTierResolverTest {

    @Mock
    private WeeklyTierRepository weeklyTierRepository;

    @Mock
    private TierService tierService;

    @Spy
    private TierScoreCalculator tierScoreCalculator = new TierScoreCalculator();

    @Mock
    private ServiceDateProvider serviceDateProvider;

    @InjectMocks
    private CurrentWeeklyTierResolver currentWeeklyTierResolver;

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

        Optional<CurrentWeeklyTierResolver.CurrentTier> result = currentWeeklyTierResolver.resolve(userId, currentWeekStart);

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

        Optional<CurrentWeeklyTierResolver.CurrentTier> result = currentWeeklyTierResolver.resolve(userId, currentWeekStart);

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

        Optional<CurrentWeeklyTierResolver.CurrentTier> result = currentWeeklyTierResolver.resolve(userId);

        assertThat(result).isPresent();
        assertThat(result.get().tierCode()).isEqualTo("CHEETAH");
        assertThat(result.get().score()).isEqualTo(1.57);
    }

    @Test
    void resolveAll_returnsLatestFinalizedSnapshotsForRequestedUsers() {
        LocalDate currentWeekStart = LocalDate.of(2026, 6, 8);
        WeeklyTierRepository.CurrentTierSnapshot betaSnapshot = currentTierSnapshot(2L, "CHEETAH", 157);
        WeeklyTierRepository.CurrentTierSnapshot alphaSnapshot = currentTierSnapshot(1L, "FOX", 90);

        when(weeklyTierRepository.findLatestSnapshotsByUserIdsAndWeekStartDateLessThanEqual(
                Set.of(1L, 2L),
                currentWeekStart
        )).thenReturn(List.of(betaSnapshot, alphaSnapshot));
        when(tierService.resolveByTierCodeAndScore("CHEETAH", 1.57))
                .thenReturn(new TierService.TierInfo("CHEETAH", "치타", "S", "/profiles/tiers/cheetah.png"));
        when(tierService.resolveByTierCodeAndScore("FOX", 0.90))
                .thenReturn(new TierService.TierInfo("FOX", "여우", "S", "/profiles/tiers/fox.png"));

        Map<Long, CurrentWeeklyTierResolver.CurrentTier> result = currentWeeklyTierResolver.resolveAll(
                Set.of(1L, 2L),
                currentWeekStart
        );

        assertThat(result).containsOnlyKeys(1L, 2L);
        assertThat(result.get(1L).tierCode()).isEqualTo("FOX");
        assertThat(result.get(1L).defaultProfileImage()).isEqualTo("/profiles/tiers/fox.png");
        assertThat(result.get(2L).tierCode()).isEqualTo("CHEETAH");
        assertThat(result.get(2L).defaultProfileImage()).isEqualTo("/profiles/tiers/cheetah.png");
    }

    private WeeklyTierRepository.CurrentTierSnapshot currentTierSnapshot(Long userId, String tierCode, Integer tierScore) {
        return new WeeklyTierRepository.CurrentTierSnapshot() {
            @Override
            public Long getUserId() {
                return userId;
            }

            @Override
            public String getTierCode() {
                return tierCode;
            }

            @Override
            public Integer getTierScore() {
                return tierScore;
            }
        };
    }
}
