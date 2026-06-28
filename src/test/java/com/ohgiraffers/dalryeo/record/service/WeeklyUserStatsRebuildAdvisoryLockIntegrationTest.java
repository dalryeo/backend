package com.ohgiraffers.dalryeo.record.service;

import com.ohgiraffers.dalryeo.auth.entity.User;
import com.ohgiraffers.dalryeo.auth.entity.UserStatus;
import com.ohgiraffers.dalryeo.auth.repository.UserRepository;
import com.ohgiraffers.dalryeo.record.entity.RunningRecord;
import com.ohgiraffers.dalryeo.record.repository.RunningRecordRepository;
import com.ohgiraffers.dalryeo.record.repository.WeeklyUserStatsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class WeeklyUserStatsRebuildAdvisoryLockIntegrationTest {

    private static final LocalDate WEEK_START = LocalDate.of(2026, 6, 22);

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
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WeeklyUserStatsService weeklyUserStatsService;

    @Autowired
    private UserRepository userRepository;

    @SpyBean
    private RunningRecordRepository runningRecordRepository;

    @SpyBean
    private WeeklyUserStatsRepository weeklyUserStatsRepository;

    private Long userId;

    @BeforeEach
    void seed() {
        weeklyUserStatsRepository.deleteAll();
        runningRecordRepository.deleteAll();
        userRepository.deleteAll();

        User user = userRepository.save(User.builder()
                .status(UserStatus.NORMAL)
                .build());
        userId = user.getId();

        for (int i = 0; i < 5; i++) {
            saveRecord(i);
        }
    }

    @Test
    @DisplayName("같은 사용자와 같은 주차의 재집계는 원본 조회부터 덮어쓰기까지 직렬화된다")
    void sameUserWeekRebuildIsSerializedFromSourceReadToReplace() throws Exception {
        CountDownLatch workerAEnteredReplace = new CountDownLatch(1);
        CountDownLatch workerAMayReplace = new CountDownLatch(1);
        CountDownLatch workerBEnteredSourceRead = new CountDownLatch(1);
        CountDownLatch workerBEnteredReplace = new CountDownLatch(1);
        AtomicInteger sourceReadCalls = new AtomicInteger();
        AtomicInteger replaceCalls = new AtomicInteger();

        doAnswer(invocation -> {
            int call = sourceReadCalls.incrementAndGet();
            if (call == 2) {
                workerBEnteredSourceRead.countDown();
            }
            return findRecords(invocation);
        }).when(runningRecordRepository).findByUserIdAndWeekRange(
                any(Long.class),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        );

        doAnswer(invocation -> {
            int call = replaceCalls.incrementAndGet();
            if (call == 1) {
                workerAEnteredReplace.countDown();
                boolean released = workerAMayReplace.await(10, TimeUnit.SECONDS);
                assertThat(released)
                        .as("워커 A가 제한 시간 안에 재개되어야 함")
                        .isTrue();
            }
            if (call == 2) {
                workerBEnteredReplace.countDown();
            }
            replaceAggregate(invocation);
            return null;
        }).when(weeklyUserStatsRepository).replaceAggregate(
                any(Long.class),
                any(LocalDate.class),
                any(Integer.class),
                any(BigDecimal.class),
                any(Integer.class),
                any(BigDecimal.class),
                any(BigDecimal.class),
                any(Integer.class),
                any(BigDecimal.class)
        );

        ExecutorService workers = Executors.newFixedThreadPool(2);
        Future<?> workerA = workers.submit(() -> weeklyUserStatsService.rebuildUserWeek(userId, WEEK_START));

        try {
            assertThat(workerAEnteredReplace.await(10, TimeUnit.SECONDS))
                    .as("워커 A가 기존 5건을 읽고 weekly_user_stats 덮어쓰기 직전에 도달해야 함")
                    .isTrue();

            saveRecord(5);

            Future<?> workerB = workers.submit(() -> weeklyUserStatsService.rebuildUserWeek(userId, WEEK_START));

            assertThat(workerBEnteredSourceRead.await(700, TimeUnit.MILLISECONDS))
                    .as("워커 B는 워커 A가 같은 user/week lock을 쥐고 있는 동안 원본 running_records 조회에도 도달하면 안 됨")
                    .isFalse();
            assertThat(workerBEnteredReplace.await(700, TimeUnit.MILLISECONDS))
                    .as("워커 B는 워커 A가 같은 user/week 재집계를 끝내기 전까지 덮어쓰기 지점에 도달하면 안 됨")
                    .isFalse();
            assertThat(workerB.isDone())
                    .as("워커 B는 예외로 실패하는 것이 아니라 advisory lock에서 대기해야 함")
                    .isFalse();

            workerAMayReplace.countDown();

            workerA.get(10, TimeUnit.SECONDS);
            workerB.get(10, TimeUnit.SECONDS);

            assertThat(currentRunCount())
                    .as("워커 B는 A 완료 후 최신 원본 6건을 다시 읽고 덮어써야 함")
                    .isEqualTo(6);
        } finally {
            workerAMayReplace.countDown();
            workers.shutdownNow();
            workers.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    private void saveRecord(int sequence) {
        LocalDateTime startAt = WEEK_START.atTime(7, 0).plusHours(sequence);
        runningRecordRepository.save(RunningRecord.builder()
                .userId(userId)
                .platform("IOS")
                .distanceKm(1.0)
                .durationSec(600)
                .avgPaceSecPerKm(600)
                .avgHeartRate(150)
                .caloriesKcal(100)
                .startAt(startAt)
                .endAt(startAt.plusMinutes(10))
                .build());
    }

    private List<RunningRecord> findRecords(InvocationOnMock invocation) {
        Long requestedUserId = invocation.getArgument(0);
        LocalDateTime startDate = invocation.getArgument(1);
        LocalDateTime endDate = invocation.getArgument(2);

        return jdbcTemplate.query("""
                        SELECT
                            user_id,
                            source,
                            distance_km,
                            duration_sec,
                            avg_pace_sec_per_km,
                            avg_heart_rate,
                            calories_kcal,
                            start_at,
                            end_at
                        FROM running_records
                        WHERE user_id = ?
                          AND start_at >= ?
                          AND start_at < ?
                        ORDER BY start_at DESC
                        """,
                (rs, rowNum) -> RunningRecord.builder()
                        .userId(rs.getLong("user_id"))
                        .platform(rs.getString("source"))
                        .distanceKm(rs.getDouble("distance_km"))
                        .durationSec(rs.getInt("duration_sec"))
                        .avgPaceSecPerKm(rs.getInt("avg_pace_sec_per_km"))
                        .avgHeartRate((Integer) rs.getObject("avg_heart_rate"))
                        .caloriesKcal((Integer) rs.getObject("calories_kcal"))
                        .startAt(rs.getTimestamp("start_at").toLocalDateTime())
                        .endAt(rs.getTimestamp("end_at").toLocalDateTime())
                        .build(),
                requestedUserId,
                startDate,
                endDate
        );
    }

    private void replaceAggregate(InvocationOnMock invocation) {
        jdbcTemplate.update("""
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
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        ON CONFLICT (user_id, week_start_date)
                        DO UPDATE SET
                            run_count = EXCLUDED.run_count,
                            total_distance_km = EXCLUDED.total_distance_km,
                            total_duration_sec = EXCLUDED.total_duration_sec,
                            weighted_pace_sum = EXCLUDED.weighted_pace_sum,
                            avg_pace_sec_per_km = EXCLUDED.avg_pace_sec_per_km,
                            tier_score_sum = EXCLUDED.tier_score_sum,
                            tier_score = EXCLUDED.tier_score,
                            updated_at = CURRENT_TIMESTAMP
                        """,
                invocation.getArgument(0),
                invocation.getArgument(1),
                invocation.getArgument(2),
                invocation.getArgument(3),
                invocation.getArgument(4),
                invocation.getArgument(5),
                invocation.getArgument(7),
                invocation.getArgument(6),
                invocation.getArgument(8)
        );
    }

    private Integer currentRunCount() {
        return jdbcTemplate.queryForObject("""
                        SELECT run_count
                          FROM weekly_user_stats
                         WHERE user_id = ?
                           AND week_start_date = ?
                        """,
                Integer.class,
                userId,
                WEEK_START
        );
    }
}
