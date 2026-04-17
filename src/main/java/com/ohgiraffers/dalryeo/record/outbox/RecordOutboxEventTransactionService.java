package com.ohgiraffers.dalryeo.record.outbox;

import com.ohgiraffers.dalryeo.record.entity.RunningRecord;
import com.ohgiraffers.dalryeo.record.repository.RunningRecordRepository;
import com.ohgiraffers.dalryeo.record.service.WeeklyUserStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RecordOutboxEventTransactionService {

    private final RecordOutboxEventRepository recordOutboxEventRepository;
    private final RunningRecordRepository runningRecordRepository;
    private final WeeklyUserStatsService weeklyUserStatsService;
    private final RecordOutboxRetryPolicy retryPolicy;

    @Transactional
    public Optional<Long> claimNextPendingEvent(LocalDateTime now) {
        return recordOutboxEventRepository.findNextPendingForUpdate(now)
                .map(event -> {
                    event.markProcessing();
                    return event.getId();
                });
    }

    @Transactional
    public void processClaimedEvent(Long eventId) {
        RecordOutboxEvent event = recordOutboxEventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalStateException("Record outbox event not found. eventId=" + eventId));

        if (!event.isProcessing()) {
            return;
        }

        handle(event);
        event.markDone();
    }

    @Transactional
    public void markRetryOrFailed(Long eventId, Exception exception, LocalDateTime now) {
        RecordOutboxEvent event = recordOutboxEventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalStateException("Record outbox event not found. eventId=" + eventId));

        if (event.isDone()) {
            return;
        }

        int nextRetryCount = event.getRetryCount() + 1;
        String errorMessage = exception.getClass().getSimpleName() + ": " + exception.getMessage();
        if (retryPolicy.shouldStopRetrying(nextRetryCount)) {
            event.markFailed(nextRetryCount, errorMessage);
            return;
        }

        event.scheduleRetry(nextRetryCount, retryPolicy.nextRetryAt(now, nextRetryCount), errorMessage);
    }

    @Transactional
    public int resetStaleProcessingEvents(LocalDateTime staleBefore, LocalDateTime now) {
        return recordOutboxEventRepository.resetStaleProcessingEvents(staleBefore, now);
    }

    private void handle(RecordOutboxEvent event) {
        if (event.getEventType() != RecordOutboxEventType.WEEKLY_STATS_UPDATE_REQUESTED) {
            throw new IllegalStateException("Unsupported record outbox event type. eventType=" + event.getEventType());
        }

        RunningRecord record = runningRecordRepository.findById(event.getAggregateId())
                .orElseThrow(() -> new IllegalStateException(
                        "Running record not found for outbox event. recordId=" + event.getAggregateId()
                ));
        weeklyUserStatsService.applyRecord(record);
    }
}
