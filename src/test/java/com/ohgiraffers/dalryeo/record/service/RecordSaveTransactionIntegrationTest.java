package com.ohgiraffers.dalryeo.record.service;

import com.ohgiraffers.dalryeo.auth.entity.User;
import com.ohgiraffers.dalryeo.auth.entity.UserStatus;
import com.ohgiraffers.dalryeo.auth.repository.UserRepository;
import com.ohgiraffers.dalryeo.record.dto.RunningRecordRequest;
import com.ohgiraffers.dalryeo.record.repository.RunningRecordRepository;
import com.ohgiraffers.dalryeo.record.repository.WeeklyUserStatsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("test")
class RecordSaveTransactionIntegrationTest {

    @Autowired
    private RecordService recordService;

    @Autowired
    private RunningRecordRepository runningRecordRepository;

    @Autowired
    private WeeklyUserStatsRepository weeklyUserStatsRepository;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private WeeklyUserStatsService weeklyUserStatsService;

    @BeforeEach
    void cleanDatabase() {
        weeklyUserStatsRepository.deleteAll();
        runningRecordRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void saveRecord_rollsBackRunningRecordWhenWeeklyStatsUpdateFails() {
        User user = userRepository.save(User.builder()
                .status(UserStatus.NORMAL)
                .build());
        doThrow(new IllegalStateException("weekly stats update failed"))
                .when(weeklyUserStatsService)
                .applyRecord(any());

        assertThatThrownBy(() -> recordService.saveRecord(user.getId(), request()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("weekly stats update failed");

        assertThat(runningRecordRepository.count()).isZero();
        assertThat(weeklyUserStatsRepository.count()).isZero();
    }

    private RunningRecordRequest request() {
        LocalDateTime startAt = LocalDateTime.of(2026, 3, 31, 7, 0);
        RunningRecordRequest request = new RunningRecordRequest();
        ReflectionTestUtils.setField(request, "platform", "IOS");
        ReflectionTestUtils.setField(request, "distanceKm", 5.0);
        ReflectionTestUtils.setField(request, "durationSec", 1500);
        ReflectionTestUtils.setField(request, "avgPaceSecPerKm", 300);
        ReflectionTestUtils.setField(request, "avgHeartRate", 150);
        ReflectionTestUtils.setField(request, "caloriesKcal", 300);
        ReflectionTestUtils.setField(request, "startAt", startAt);
        ReflectionTestUtils.setField(request, "endAt", startAt.plusSeconds(1500));
        return request;
    }
}
