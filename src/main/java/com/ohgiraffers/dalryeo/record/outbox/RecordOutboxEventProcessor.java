package com.ohgiraffers.dalryeo.record.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecordOutboxEventProcessor {

    private final RecordOutboxEventTransactionService transactionService;

    public int processDueEvents(int batchSize, long staleTimeoutSeconds) {
        LocalDateTime now = LocalDateTime.now();
        int resetCount = transactionService.resetStaleProcessingEvents(
                now.minusSeconds(staleTimeoutSeconds),
                now
        );
        if (resetCount > 0) {
            log.warn("Reset stale record outbox events. count={}", resetCount);
        }

        int processedCount = 0;
        while (processedCount < batchSize && processNextDueEvent()) {
            processedCount++;
        }
        return processedCount;
    }

    public boolean processNextDueEvent() {
        LocalDateTime now = LocalDateTime.now();
        return transactionService.claimNextPendingEvent(now)
                .map(eventId -> {
                    processClaimedEvent(eventId);
                    return true;
                })
                .orElse(false);
    }

    private void processClaimedEvent(Long eventId) {
        try {
            transactionService.processClaimedEvent(eventId);
        } catch (Exception exception) {
            log.warn("Record outbox event processing failed. eventId={}", eventId, exception);
            transactionService.markRetryOrFailed(eventId, exception, LocalDateTime.now());
        }
    }
}
