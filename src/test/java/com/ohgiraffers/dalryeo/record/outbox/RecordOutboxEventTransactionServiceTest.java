package com.ohgiraffers.dalryeo.record.outbox;

import com.ohgiraffers.dalryeo.record.entity.RunningRecord;
import com.ohgiraffers.dalryeo.record.repository.RunningRecordRepository;
import com.ohgiraffers.dalryeo.record.service.WeeklyUserStatsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecordOutboxEventTransactionServiceTest {

    @Mock
    private RecordOutboxEventRepository recordOutboxEventRepository;

    @Mock
    private RunningRecordRepository runningRecordRepository;

    @Mock
    private WeeklyUserStatsService weeklyUserStatsService;

    @Spy
    private RecordOutboxRetryPolicy retryPolicy = new RecordOutboxRetryPolicy(10, 30, 1800);

    @InjectMocks
    private RecordOutboxEventTransactionService transactionService;

    @Test
    void claimNextPendingEvent_marksEventAsProcessing() {
        LocalDateTime now = LocalDateTime.of(2026, 4, 17, 10, 0);
        RecordOutboxEvent event = RecordOutboxEvent.weeklyStatsUpdateRequested(10L, now);
        ReflectionTestUtils.setField(event, "id", 1L);
        when(recordOutboxEventRepository.findNextPendingForUpdate(now)).thenReturn(Optional.of(event));

        Optional<Long> eventId = transactionService.claimNextPendingEvent(now);

        assertThat(eventId).contains(1L);
        assertThat(event.getStatus()).isEqualTo(RecordOutboxEventStatus.PROCESSING);
    }

    @Test
    void processClaimedEvent_updatesWeeklyStatsAndMarksEventDone() {
        LocalDateTime now = LocalDateTime.of(2026, 4, 17, 10, 0);
        RecordOutboxEvent event = RecordOutboxEvent.weeklyStatsUpdateRequested(10L, now);
        event.markProcessing();
        RunningRecord record = runningRecord();
        when(recordOutboxEventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(runningRecordRepository.findById(10L)).thenReturn(Optional.of(record));

        transactionService.processClaimedEvent(1L);

        verify(weeklyUserStatsService).applyRecord(record);
        assertThat(event.getStatus()).isEqualTo(RecordOutboxEventStatus.DONE);
    }

    @Test
    void markRetryOrFailed_schedulesRetryWhenRetryCountIsBelowLimit() {
        LocalDateTime now = LocalDateTime.of(2026, 4, 17, 10, 0);
        RecordOutboxEvent event = RecordOutboxEvent.weeklyStatsUpdateRequested(10L, now);
        event.markProcessing();
        when(recordOutboxEventRepository.findById(1L)).thenReturn(Optional.of(event));

        transactionService.markRetryOrFailed(1L, new IllegalStateException("weekly failed"), now);

        assertThat(event.getStatus()).isEqualTo(RecordOutboxEventStatus.PENDING);
        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getNextRetryAt()).isEqualTo(now.plusSeconds(30));
        assertThat(event.getLastError()).contains("weekly failed");
    }

    @Test
    void markRetryOrFailed_marksFailedWhenRetryCountReachesLimit() {
        LocalDateTime now = LocalDateTime.of(2026, 4, 17, 10, 0);
        RecordOutboxEvent event = RecordOutboxEvent.weeklyStatsUpdateRequested(10L, now);
        event.scheduleRetry(9, now, "previous failure");
        event.markProcessing();
        when(recordOutboxEventRepository.findById(1L)).thenReturn(Optional.of(event));

        transactionService.markRetryOrFailed(1L, new IllegalStateException("weekly failed"), now);

        assertThat(event.getStatus()).isEqualTo(RecordOutboxEventStatus.FAILED);
        assertThat(event.getRetryCount()).isEqualTo(10);
        assertThat(event.getLastError()).contains("weekly failed");
    }

    private RunningRecord runningRecord() {
        return RunningRecord.builder()
                .userId(1L)
                .platform("IOS")
                .distanceKm(5.0)
                .durationSec(1500)
                .avgPaceSecPerKm(300)
                .avgHeartRate(150)
                .caloriesKcal(300)
                .startAt(LocalDateTime.of(2026, 4, 17, 7, 0))
                .endAt(LocalDateTime.of(2026, 4, 17, 7, 25))
                .build();
    }
}
