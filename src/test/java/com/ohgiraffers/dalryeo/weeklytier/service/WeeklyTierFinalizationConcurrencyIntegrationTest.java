package com.ohgiraffers.dalryeo.weeklytier.service;

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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 분산 락 없이 여러 인스턴스가 같은 주차를 동시에 확정해도
 * weekly_tiers upsert가 동일한 최종 스냅샷으로 수렴하는지 검증한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class WeeklyTierFinalizationConcurrencyIntegrationTest {

    private static final LocalDate SOURCE_WEEK_START = LocalDate.of(2026, 6, 15);
    private static final LocalDate SNAPSHOT_WEEK_START = LocalDate.of(2026, 6, 22);
    private static final int USER_COUNT = 50;
    private static final int INSTANCE_COUNT = 3;

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
    private WeeklyTierFinalizationTransactionService transactionService;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("DELETE FROM weekly_tiers");
        jdbcTemplate.update("DELETE FROM weekly_user_stats");
        jdbcTemplate.update("DELETE FROM users");

        int insertedUsers = jdbcTemplate.update("""
                INSERT INTO users (status)
                SELECT 'NORMAL'
                FROM generate_series(1, ?)
                """, USER_COUNT);
        assertThat(insertedUsers).isEqualTo(USER_COUNT);

        int insertedStats = jdbcTemplate.update("""
                INSERT INTO weekly_user_stats (
                    user_id,
                    week_start_date,
                    run_count,
                    total_distance_km,
                    total_duration_sec,
                    weighted_pace_sum,
                    avg_pace_sec_per_km,
                    tier_score_sum,
                    tier_score,
                    created_at,
                    updated_at
                )
                SELECT
                    id,
                    ?,
                    5,
                    25.000,
                    7500,
                    7500.000,
                    300,
                    5.00,
                    1.00,
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP
                FROM users
                """, SOURCE_WEEK_START);
        assertThat(insertedStats).isEqualTo(USER_COUNT);
    }

    @RepeatedTest(20)
    void concurrentFinalizationYieldsOneSnapshotPerUser() throws Exception {
        ExecutorService instances = Executors.newFixedThreadPool(INSTANCE_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger totalChanged = new AtomicInteger();
        List<Future<Throwable>> results = new ArrayList<>();

        for (int instance = 0; instance < INSTANCE_COUNT; instance++) {
            results.add(instances.submit(() -> {
                if (!start.await(10, TimeUnit.SECONDS)) {
                    return new IllegalStateException("Instances did not start within the timeout");
                }
                try {
                    WeeklyTierFinalizationTransactionService.WeekResult result = transactionService.finalizeWeek(
                            SOURCE_WEEK_START,
                            SNAPSHOT_WEEK_START
                    );
                    totalChanged.addAndGet(result.changed());
                    return null;
                } catch (Throwable throwable) {
                    return throwable;
                }
            }));
        }

        try {
            start.countDown();
            List<Throwable> errors = new ArrayList<>();
            for (Future<Throwable> result : results) {
                Throwable error = result.get(30, TimeUnit.SECONDS);
                if (error != null) {
                    errors.add(error);
                }
            }

            assertThat(errors)
                    .as("동시 확정 중 예외가 외부로 전파되면 안 됨")
                    .isEmpty();
            assertThat(totalChanged.get())
                    .as("동일 스냅샷은 전체 실행을 합쳐 사용자당 한 번만 변경되어야 함")
                    .isEqualTo(USER_COUNT);
            assertThat(countSnapshots())
                    .as("사용자마다 스냅샷이 정확히 한 행 있어야 함")
                    .isEqualTo(USER_COUNT);
            assertThat(countUsersWithDuplicateSnapshots())
                    .as("중복 스냅샷을 가진 사용자가 없어야 함")
                    .isZero();
        } finally {
            start.countDown();
            instances.shutdownNow();
            instances.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    private Integer countSnapshots() {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM weekly_tiers
                WHERE week_start_date = ?
                """, Integer.class, SNAPSHOT_WEEK_START);
    }

    private Integer countUsersWithDuplicateSnapshots() {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM (
                    SELECT user_id
                    FROM weekly_tiers
                    WHERE week_start_date = ?
                    GROUP BY user_id
                    HAVING COUNT(*) > 1
                ) duplicated
                """, Integer.class, SNAPSHOT_WEEK_START);
    }
}
