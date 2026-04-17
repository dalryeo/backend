package com.ohgiraffers.dalryeo.record.outbox;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class RecordOutboxRetryPolicy {

    private final int maxAttempts;
    private final long baseDelaySeconds;
    private final long maxDelaySeconds;

    public RecordOutboxRetryPolicy(
            @Value("${record.outbox.retry.max-attempts:10}") int maxAttempts,
            @Value("${record.outbox.retry.base-delay-seconds:30}") long baseDelaySeconds,
            @Value("${record.outbox.retry.max-delay-seconds:1800}") long maxDelaySeconds
    ) {
        this.maxAttempts = maxAttempts;
        this.baseDelaySeconds = baseDelaySeconds;
        this.maxDelaySeconds = maxDelaySeconds;
    }

    public boolean shouldStopRetrying(int nextRetryCount) {
        return nextRetryCount >= maxAttempts;
    }

    public LocalDateTime nextRetryAt(LocalDateTime now, int nextRetryCount) {
        int exponent = Math.max(0, Math.min(nextRetryCount - 1, 10));
        long delaySeconds = Math.min(maxDelaySeconds, baseDelaySeconds * (1L << exponent));
        return now.plusSeconds(delaySeconds);
    }
}
