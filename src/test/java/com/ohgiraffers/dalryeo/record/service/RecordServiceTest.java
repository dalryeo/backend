package com.ohgiraffers.dalryeo.record.service;

import com.ohgiraffers.dalryeo.auth.entity.User;
import com.ohgiraffers.dalryeo.auth.entity.UserStatus;
import com.ohgiraffers.dalryeo.auth.repository.UserRepository;
import com.ohgiraffers.dalryeo.record.dto.RecordSummaryResponse;
import com.ohgiraffers.dalryeo.record.entity.RunningRecord;
import com.ohgiraffers.dalryeo.record.repository.RunningRecordRepository;
import com.ohgiraffers.dalryeo.tier.service.CurrentTierResolver;
import com.ohgiraffers.dalryeo.tier.service.TierService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecordServiceTest {

    @Mock
    private RunningRecordRepository runningRecordRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CurrentTierResolver currentTierResolver;

    @Mock
    private TierService tierService;

    @InjectMocks
    private RecordService recordService;

    @Test
    void getSummary_usesCurrentWeekRecordsToResolveTier() {
        Long userId = 1L;
        User user = User.builder()
                .status(UserStatus.NORMAL)
                .build();
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

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(runningRecordRepository.findByUserIdAndWeekRange(eq(userId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(runningRecord));
        when(currentTierResolver.resolve(eq(userId), any(LocalDate.class), anyList()))
                .thenReturn(Optional.of(new CurrentTierResolver.CurrentTier(
                        "DEER",
                        "사슴",
                        "B",
                        1.24,
                        "/profiles/tiers/deer.jpg"
                )));

        RecordSummaryResponse response = recordService.getSummary(userId);

        assertThat(response.getCurrentTier()).isEqualTo("DEER");
        assertThat(response.getCurrentTierGrade()).isEqualTo("B");
        assertThat(response.getWeeklyCount()).isEqualTo(1);
        assertThat(response.getWeeklyAvgPace()).isEqualTo(300);
        assertThat(response.getWeeklyDistance()).isEqualTo(5.0);
    }

    @Test
    void getSummary_fallsBackToWeeklyTierWhenNoCurrentWeekRecordsExist() {
        Long userId = 2L;
        User user = User.builder()
                .status(UserStatus.NORMAL)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(runningRecordRepository.findByUserIdAndWeekRange(eq(userId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(currentTierResolver.resolve(eq(userId), any(LocalDate.class), anyList()))
                .thenReturn(Optional.of(new CurrentTierResolver.CurrentTier(
                        "FOX",
                        "여우",
                        "S",
                        0.90,
                        "/profiles/tiers/fox.jpg"
                )));

        RecordSummaryResponse response = recordService.getSummary(userId);

        assertThat(response.getCurrentTier()).isEqualTo("FOX");
        assertThat(response.getCurrentTierGrade()).isEqualTo("S");
        assertThat(response.getWeeklyCount()).isEqualTo(0);
        assertThat(response.getWeeklyAvgPace()).isEqualTo(0);
        assertThat(response.getWeeklyDistance()).isEqualTo(0.0);
    }
}
