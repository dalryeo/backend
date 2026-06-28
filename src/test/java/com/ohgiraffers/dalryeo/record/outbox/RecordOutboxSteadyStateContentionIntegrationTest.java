package com.ohgiraffers.dalryeo.record.outbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 정상 outbox 경합에서 여러 워커가 SKIP LOCKED로 이벤트를 나눠 처리할 때
 * 집계 중복이나 유실이 없는지 검증한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class RecordOutboxSteadyStateContentionIntegrationTest {

    private static final int EVENT_COUNT = 100;
    private static final int WORKER_COUNT = 3;
    private static final int BATCH_SIZE = 10;
    private static final long STALE_TIMEOUT_SECONDS = 300L;

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("record.outbox.scheduler.enabled", () -> false);
        registry.add("weekly-tier.finalization.enabled", () -> false);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RecordOutboxEventProcessor processor;

    private Long userId;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("DELETE FROM record_outbox_events");
        jdbcTemplate.update("DELETE FROM weekly_user_stats");
        jdbcTemplate.update("DELETE FROM running_records");
        jdbcTemplate.update("DELETE FROM users");

        userId = jdbcTemplate.queryForObject("""
                INSERT INTO users (status)
                VALUES ('NORMAL')
                RETURNING id
                """, Long.class);

        jdbcTemplate.update("""
                INSERT INTO running_records (
                    user_id,
                    source,
                    distance_km,
                    duration_sec,
                    avg_pace_sec_per_km,
                    avg_heart_rate,
                    calories_kcal,
                    start_at,
                    end_at,
                    created_at,
                    updated_at
                )
                SELECT
                    ?,
                    'IOS',
                    1.0,
                    600,
                    600,
                    150,
                    100,
                    TIMESTAMP '2026-06-22 07:00:00',
                    TIMESTAMP '2026-06-22 07:10:00',
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP
                FROM generate_series(1, ?)
                """, userId, EVENT_COUNT);

        int insertedEvents = jdbcTemplate.update("""
                INSERT INTO record_outbox_events (
                    event_type,
                    aggregate_id,
                    status,
                    retry_count,
                    next_retry_at,
                    created_at,
                    updated_at
                )
                SELECT
                    'WEEKLY_STATS_UPDATE_REQUESTED',
                    id,
                    'PENDING',
                    0,
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP
                FROM running_records
                WHERE user_id = ?
                """, userId);
        assertThat(insertedEvents).isEqualTo(EVENT_COUNT);
    }

    @RepeatedTest(30)
    void allEventsAreAggregatedExactlyOnceUnderThreeWorkers() throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(WORKER_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();

        for (int worker = 0; worker < WORKER_COUNT; worker++) {
            results.add(workers.submit(() -> {
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Workers did not start within the timeout");
                }

                int totalProcessed = 0;
                int processed;
                do {
                    processed = processor.processDueEvents(BATCH_SIZE, STALE_TIMEOUT_SECONDS);
                    totalProcessed += processed;
                } while (processed > 0);
                return totalProcessed;
            }));
        }

        try {
            start.countDown();
            int totalProcessed = 0;
            for (Future<Integer> result : results) {
                totalProcessed += result.get(60, TimeUnit.SECONDS);
            }

            assertThat(totalProcessed)
                    .as("각 이벤트는 정확히 한 워커만 claim해야 함")
                    .isEqualTo(EVENT_COUNT);
            assertThat(countEventsNotDone())
                    .as("모든 이벤트가 DONE이어야 함")
                    .isZero();
            assertThat(currentRunCount())
                    .as("집계 횟수는 이벤트 수와 같아야 함")
                    .isEqualTo(EVENT_COUNT);
            assertThat(currentDistance())
                    .as("거리 합계는 이벤트 거리 합과 같아야 함")
                    .isEqualByComparingTo(BigDecimal.valueOf(EVENT_COUNT));
        } finally {
            start.countDown();
            workers.shutdownNow();
            workers.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    private Integer countEventsNotDone() {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM record_outbox_events
                WHERE status <> 'DONE'
                """, Integer.class);
    }

    private Integer currentRunCount() {
        return jdbcTemplate.queryForObject("""
                SELECT run_count
                FROM weekly_user_stats
                WHERE user_id = ?
                  AND week_start_date = DATE '2026-06-22'
                """, Integer.class, userId);
    }

    private BigDecimal currentDistance() {
        return jdbcTemplate.queryForObject("""
                SELECT total_distance_km
                FROM weekly_user_stats
                WHERE user_id = ?
                  AND week_start_date = DATE '2026-06-22'
                """, BigDecimal.class, userId);
    }
}
