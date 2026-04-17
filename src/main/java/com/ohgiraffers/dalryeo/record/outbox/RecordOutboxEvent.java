package com.ohgiraffers.dalryeo.record.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "record_outbox_events",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_record_outbox_events_type_aggregate",
                columnNames = {"event_type", "aggregate_id"}
        ),
        indexes = @Index(
                name = "idx_record_outbox_events_status_retry",
                columnList = "status, next_retry_at, id"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecordOutboxEvent {

    private static final int LAST_ERROR_MAX_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 50, nullable = false)
    private RecordOutboxEventType eventType;

    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private RecordOutboxEventStatus status;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "next_retry_at", nullable = false)
    private LocalDateTime nextRetryAt;

    @Column(name = "last_error", length = LAST_ERROR_MAX_LENGTH)
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private RecordOutboxEvent(RecordOutboxEventType eventType, Long aggregateId, LocalDateTime nextRetryAt) {
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.status = RecordOutboxEventStatus.PENDING;
        this.retryCount = 0;
        this.nextRetryAt = nextRetryAt;
    }

    public static RecordOutboxEvent weeklyStatsUpdateRequested(Long runningRecordId, LocalDateTime nextRetryAt) {
        return new RecordOutboxEvent(
                RecordOutboxEventType.WEEKLY_STATS_UPDATE_REQUESTED,
                runningRecordId,
                nextRetryAt
        );
    }

    public boolean isDone() {
        return status == RecordOutboxEventStatus.DONE;
    }

    public boolean isProcessing() {
        return status == RecordOutboxEventStatus.PROCESSING;
    }

    public void markProcessing() {
        this.status = RecordOutboxEventStatus.PROCESSING;
        this.lastError = null;
    }

    public void markDone() {
        this.status = RecordOutboxEventStatus.DONE;
        this.lastError = null;
    }

    public void scheduleRetry(int retryCount, LocalDateTime nextRetryAt, String errorMessage) {
        this.status = RecordOutboxEventStatus.PENDING;
        this.retryCount = retryCount;
        this.nextRetryAt = nextRetryAt;
        this.lastError = truncate(errorMessage);
    }

    public void markFailed(int retryCount, String errorMessage) {
        this.status = RecordOutboxEventStatus.FAILED;
        this.retryCount = retryCount;
        this.lastError = truncate(errorMessage);
    }

    private String truncate(String value) {
        if (value == null || value.length() <= LAST_ERROR_MAX_LENGTH) {
            return value;
        }
        return value.substring(0, LAST_ERROR_MAX_LENGTH);
    }
}
