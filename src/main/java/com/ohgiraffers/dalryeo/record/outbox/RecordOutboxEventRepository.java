package com.ohgiraffers.dalryeo.record.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RecordOutboxEventRepository extends JpaRepository<RecordOutboxEvent, Long> {

    @Query(value = """
            SELECT *
            FROM record_outbox_events
            WHERE status = 'PENDING'
              AND next_retry_at <= :now
            ORDER BY id ASC
            FOR UPDATE SKIP LOCKED
            LIMIT 1
            """, nativeQuery = true)
    Optional<RecordOutboxEvent> findNextPendingForUpdate(@Param("now") LocalDateTime now);

    @Modifying
    @Query(value = """
            UPDATE record_outbox_events
            SET status = 'PENDING',
                next_retry_at = :now,
                updated_at = CURRENT_TIMESTAMP
            WHERE status = 'PROCESSING'
              AND updated_at <= :staleBefore
            """, nativeQuery = true)
    int resetStaleProcessingEvents(
            @Param("staleBefore") LocalDateTime staleBefore,
            @Param("now") LocalDateTime now
    );
}
