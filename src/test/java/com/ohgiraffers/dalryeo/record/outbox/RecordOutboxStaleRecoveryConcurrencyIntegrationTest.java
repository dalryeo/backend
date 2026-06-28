package com.ohgiraffers.dalryeo.record.outbox;

import com.ohgiraffers.dalryeo.auth.entity.User;
import com.ohgiraffers.dalryeo.auth.entity.UserStatus;
import com.ohgiraffers.dalryeo.auth.repository.UserRepository;
import com.ohgiraffers.dalryeo.record.entity.RunningRecord;
import com.ohgiraffers.dalryeo.record.repository.RunningRecordRepository;
import com.ohgiraffers.dalryeo.record.repository.WeeklyUserStatsRepository;
import com.ohgiraffers.dalryeo.record.service.WeeklyUserStatsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * Outbox stale recovery race로 동일 러닝 기록이 중복 집계되는 문제의 회귀 테스트.
 *
 * <p>워커 A를 실제 집계 직전에 멈춘 뒤 이벤트를 stale 상태로 만들고, 워커 B가
 * reset, reclaim, aggregate, DONE 처리를 끝내게 한다. 이후 A를 재개해도 동일 이벤트는
 * 정확히 한 번만 집계되어야 한다.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class RecordOutboxStaleRecoveryConcurrencyIntegrationTest {

    private static final int BATCH_SIZE = 1;
    private static final long STALE_TIMEOUT_SECONDS = 1L;
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
    private RecordOutboxEventProcessor processor;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RunningRecordRepository runningRecordRepository;

    @Autowired
    private RecordOutboxEventRepository recordOutboxEventRepository;

    @Autowired
    private WeeklyUserStatsRepository weeklyUserStatsRepository;

    @SpyBean
    private WeeklyUserStatsService weeklyUserStatsService;

    private Long userId;
    private Long eventId;

    @BeforeEach
    void seed() {
        recordOutboxEventRepository.deleteAll();
        weeklyUserStatsRepository.deleteAll();
        runningRecordRepository.deleteAll();
        userRepository.deleteAll();

        User user = userRepository.save(User.builder()
                .status(UserStatus.NORMAL)
                .build());
        RunningRecord record = runningRecordRepository.save(RunningRecord.builder()
                .userId(user.getId())
                .platform("IOS")
                .distanceKm(1.0)
                .durationSec(600)
                .avgPaceSecPerKm(600)
                .avgHeartRate(150)
                .caloriesKcal(100)
                .startAt(LocalDateTime.of(2026, 6, 22, 7, 0))
                .endAt(LocalDateTime.of(2026, 6, 22, 7, 10))
                .build());
        RecordOutboxEvent event = recordOutboxEventRepository.save(
                RecordOutboxEvent.weeklyStatsUpdateRequested(record.getId(), LocalDateTime.now())
        );

        userId = user.getId();
        eventId = event.getId();
    }

    @Test
    @DisplayName("stale recovery race에서도 동일 이벤트의 집계는 정확히 1회만 반영된다")
    void aggregationCommitsExactlyOnceUnderStaleRecoveryRace() throws Exception {
        CountDownLatch workerAEnteredApply = new CountDownLatch(1);
        CountDownLatch workerAMayProceed = new CountDownLatch(1);
        AtomicInteger applyCalls = new AtomicInteger();

        doAnswer(invocation -> {
            if (applyCalls.incrementAndGet() == 1) {
                workerAEnteredApply.countDown();
                boolean released = workerAMayProceed.await(10, TimeUnit.SECONDS);
                assertThat(released)
                        .as("워커 A가 제한 시간 안에 재개되어야 함")
                        .isTrue();
            }
            return invocation.callRealMethod();
        }).when(weeklyUserStatsService).rebuildForRecord(any(RunningRecord.class));

        ExecutorService workers = Executors.newFixedThreadPool(2);
        Future<Integer> workerA = workers.submit(
                () -> processor.processDueEvents(BATCH_SIZE, STALE_TIMEOUT_SECONDS)
        );

        try {
            assertThat(workerAEnteredApply.await(10, TimeUnit.SECONDS))
                    .as("워커 A가 실제 집계 직전에 도달해야 함")
                    .isTrue();

            int staleEvents = jdbcTemplate.update("""
                    UPDATE record_outbox_events
                       SET updated_at = CURRENT_TIMESTAMP - INTERVAL '10 minutes'
                     WHERE id = ?
                       AND status = 'PROCESSING'
                    """, eventId);
            assertThat(staleEvents)
                    .as("워커 A가 claim한 PROCESSING 이벤트가 stale 상태로 변경되어야 함")
                    .isEqualTo(1);

            Future<Integer> workerB = workers.submit(
                    () -> processor.processDueEvents(BATCH_SIZE, STALE_TIMEOUT_SECONDS)
            );
            assertThat(workerB.get(10, TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(currentRunCount())
                    .as("워커 B 처리 후 집계는 1회 반영되어 있어야 함")
                    .isEqualTo(1);

            workerAMayProceed.countDown();
            assertThat(workerA.get(10, TimeUnit.SECONDS)).isEqualTo(1);

            assertThat(currentRunCount())
                    .as("동일 이벤트의 집계는 정확히 1회만 반영되어야 함")
                    .isEqualTo(1);
        } finally {
            workerAMayProceed.countDown();
            workers.shutdownNow();
            workers.awaitTermination(10, TimeUnit.SECONDS);
        }
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
