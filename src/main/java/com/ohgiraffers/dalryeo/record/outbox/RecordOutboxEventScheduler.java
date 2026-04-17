package com.ohgiraffers.dalryeo.record.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "record.outbox.scheduler",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class RecordOutboxEventScheduler {

    private final RecordOutboxEventProcessor recordOutboxEventProcessor;

    @Value("${record.outbox.scheduler.batch-size:20}")
    private int batchSize;

    @Value("${record.outbox.scheduler.stale-timeout-seconds:300}")
    private long staleTimeoutSeconds;

    @Scheduled(fixedDelayString = "${record.outbox.scheduler.fixed-delay-ms:5000}")
    public void processDueEvents() {
        recordOutboxEventProcessor.processDueEvents(batchSize, staleTimeoutSeconds);
    }
}
