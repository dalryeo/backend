package com.ohgiraffers.dalryeo.weeklytier.service;

import com.ohgiraffers.dalryeo.common.time.ServiceDateProvider;
import com.ohgiraffers.dalryeo.tier.service.TierService;
import com.ohgiraffers.dalryeo.user.service.UserLookupService;
import com.ohgiraffers.dalryeo.weeklytier.dto.WeeklyTierResponse;
import com.ohgiraffers.dalryeo.weeklytier.entity.WeeklyTier;
import com.ohgiraffers.dalryeo.weeklytier.repository.WeeklyTierRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeeklyTierServiceTest {

    @Mock
    private WeeklyTierRepository weeklyTierRepository;

    @Mock
    private TierService tierService;

    @Mock
    private UserLookupService userLookupService;

    @Mock
    private ServiceDateProvider serviceDateProvider;

    @InjectMocks
    private WeeklyTierService weeklyTierService;

    @Test
    void getCurrentWeeklyTier_returnsLatestFinalizedSnapshotAtOrBeforeCurrentWeek() {
        Long userId = 1L;
        LocalDate currentWeekStart = LocalDate.of(2026, 6, 8);
        LocalDate snapshotWeekStart = LocalDate.of(2026, 6, 1);
        WeeklyTier weeklyTier = WeeklyTier.builder()
                .userId(userId)
                .weekStartDate(snapshotWeekStart)
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

        WeeklyTierResponse response = weeklyTierService.getCurrentWeeklyTier(userId);

        assertThat(response).isNotNull();
        assertThat(response.getWeekStartDate()).isEqualTo(snapshotWeekStart);
        assertThat(response.getTierCode()).isEqualTo("CHEETAH");
        assertThat(response.getTierGrade()).isEqualTo("S");
        assertThat(response.getTierScore()).isEqualTo(1.57);
        assertThat(response.getDefaultProfileImage()).isEqualTo("/profiles/tiers/cheetah.png");
        verify(weeklyTierRepository).findTopByUserIdAndWeekStartDateLessThanEqualOrderByWeekStartDateDesc(
                userId,
                currentWeekStart
        );
    }

    @Test
    void getCurrentWeeklyTier_returnsNullWhenNoFinalizedSnapshotExists() {
        Long userId = 2L;
        LocalDate currentWeekStart = LocalDate.of(2026, 6, 8);

        when(serviceDateProvider.currentWeekStart()).thenReturn(currentWeekStart);
        when(weeklyTierRepository.findTopByUserIdAndWeekStartDateLessThanEqualOrderByWeekStartDateDesc(
                userId,
                currentWeekStart
        )).thenReturn(Optional.empty());

        WeeklyTierResponse response = weeklyTierService.getCurrentWeeklyTier(userId);

        assertThat(response).isNull();
    }
}
