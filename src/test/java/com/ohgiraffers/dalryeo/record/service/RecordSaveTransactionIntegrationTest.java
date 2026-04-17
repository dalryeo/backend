package com.ohgiraffers.dalryeo.record.service;

import com.ohgiraffers.dalryeo.auth.entity.User;
import com.ohgiraffers.dalryeo.auth.entity.UserStatus;
import com.ohgiraffers.dalryeo.auth.repository.UserRepository;
import com.ohgiraffers.dalryeo.record.dto.RunningRecordRequest;
import com.ohgiraffers.dalryeo.record.outbox.RecordOutboxEvent;
import com.ohgiraffers.dalryeo.record.outbox.RecordOutboxEventProcessor;
import com.ohgiraffers.dalryeo.record.outbox.RecordOutboxEventRepository;
import com.ohgiraffers.dalryeo.record.outbox.RecordOutboxEventStatus;
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
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("test")
class RecordSaveTransactionIntegrationTest {

    private static final ZoneOffset TEST_ZONE_OFFSET = ZoneOffset.ofHours(9);

    @Autowired
    private RecordService recordService;

    @Autowired
    private RunningRecordRepository runningRecordRepository;

    @Autowired
    private WeeklyUserStatsRepository weeklyUserStatsRepository;

    @Autowired
    private RecordOutboxEventRepository recordOutboxEventRepository;

    @Autowired
    private RecordOutboxEventProcessor recordOutboxEventProcessor;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private WeeklyUserStatsService weeklyUserStatsService;

    @BeforeEach
    void cleanDatabase() {
        recordOutboxEventRepository.deleteAll();
        weeklyUserStatsRepository.deleteAll();
        runningRecordRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void saveRecord_keepsRunningRecordAndRetriesOutboxWhenWeeklyStatsUpdateFails() {
        User user = userRepository.save(User.builder()
                .status(UserStatus.NORMAL)
                .build());
        doThrow(new IllegalStateException("weekly stats update failed"))
                .when(weeklyUserStatsService)
                .applyRecord(any());

        recordService.saveRecord(user.getId(), request());
        recordOutboxEventProcessor.processNextDueEvent();

        RecordOutboxEvent outboxEvent = recordOutboxEventRepository.findAll().get(0);

        assertThat(runningRecordRepository.count()).isEqualTo(1);
        assertThat(weeklyUserStatsRepository.count()).isZero();
        assertThat(outboxEvent.getStatus()).isEqualTo(RecordOutboxEventStatus.PENDING);
        assertThat(outboxEvent.getRetryCount()).isEqualTo(1);
        assertThat(outboxEvent.getLastError()).contains("weekly stats update failed");
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
        ReflectionTestUtils.setField(request, "startAt", startAt.atOffset(TEST_ZONE_OFFSET));
        ReflectionTestUtils.setField(request, "endAt", startAt.plusSeconds(1500).atOffset(TEST_ZONE_OFFSET));
        return request;
    }
}
