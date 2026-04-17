package com.ohgiraffers.dalryeo.record.outbox;

public enum RecordOutboxEventStatus {
    PENDING,
    PROCESSING,
    DONE,
    FAILED
}
