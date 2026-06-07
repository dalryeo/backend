package com.ohgiraffers.dalryeo.record.service;

import com.ohgiraffers.dalryeo.auth.entity.User;
import com.ohgiraffers.dalryeo.auth.entity.UserStatus;
import com.ohgiraffers.dalryeo.common.time.ServiceDateProvider;
import com.ohgiraffers.dalryeo.record.dto.RecordIdResponse;
import com.ohgiraffers.dalryeo.record.dto.RecordSummaryResponse;
import com.ohgiraffers.dalryeo.record.dto.RunningRecordRequest;
import com.ohgiraffers.dalryeo.record.dto.WeeklySummaryItemResponse;
import com.ohgiraffers.dalryeo.record.dto.WeeklySummaryResponse;
import com.ohgiraffers.dalryeo.record.entity.RunningRecord;
import com.ohgiraffers.dalryeo.record.entity.WeeklyUserStats;
import com.ohgiraffers.dalryeo.record.exception.RecordValidationErrorCode;
import com.ohgiraffers.dalryeo.record.exception.RecordValidationException;
import com.ohgiraffers.dalryeo.record.outbox.RecordOutboxEvent;
import com.ohgiraffers.dalryeo.record.outbox.RecordOutboxEventRepository;
import com.ohgiraffers.dalryeo.record.outbox.RecordOutboxEventStatus;
import com.ohgiraffers.dalryeo.record.outbox.RecordOutboxEventType;
import com.ohgiraffers.dalryeo.record.repository.RunningRecordRepository;
import com.ohgiraffers.dalryeo.tier.service.CurrentTierResolver;
import com.ohgiraffers.dalryeo.tier.service.TierScoreCalculator;
import com.ohgiraffers.dalryeo.tier.service.TierService;
import com.ohgiraffers.dalryeo.user.exception.UserErrorCode;
import com.ohgiraffers.dalryeo.user.exception.UserException;
import com.ohgiraffers.dalryeo.user.service.UserLookupService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecordServiceTest {

    private static final ZoneId SERVICE_ZONE_ID = ZoneId.of("Asia/Seoul");

    @Mock
    private RunningRecordRepository runningRecordRepository;

    @Mock
    private RecordOutboxEventRepository recordOutboxEventRepository;

    @Mock
    private UserLookupService userLookupService;

    @Mock
    private CurrentTierResolver currentTierResolver;

    @Mock
    private TierService tierService;

    @Mock
    private WeeklyUserStatsService weeklyUserStatsService;

    @Spy
    private RunningRecordValidator runningRecordValidator = new RunningRecordValidator();

    @Spy
    private TierScoreCalculator tierScoreCalculator = new TierScoreCalculator();

    @Mock
    private ServiceDateProvider serviceDateProvider;

    @InjectMocks
    private RecordService recordService;

    @Test
    void saveRecord_savesRecordWhenRequestIsValid() {
        Long userId = 1L;
        ZoneId configuredZoneId = ZoneId.of("UTC");
        OffsetDateTime startAt = validPastStartAt();
        RunningRecordRequest request = request(
                5.0,
                1500,
                300,
                startAt,
                startAt.plusMinutes(25)
        );

        when(userLookupService.getActiveById(userId)).thenReturn(user(userId));
        when(serviceDateProvider.zoneId()).thenReturn(configuredZoneId);
        when(runningRecordRepository.save(any(RunningRecord.class))).thenAnswer(invocation -> {
            RunningRecord record = invocation.getArgument(0);
            ReflectionTestUtils.setField(record, "id", 100L);
            return record;
        });

        RecordIdResponse response = recordService.saveRecord(userId, request);

        ArgumentCaptor<RunningRecord> recordCaptor = ArgumentCaptor.forClass(RunningRecord.class);
        ArgumentCaptor<RecordOutboxEvent> outboxCaptor = ArgumentCaptor.forClass(RecordOutboxEvent.class);
        assertThat(response.getRecordId()).isEqualTo(100L);
        verify(runningRecordRepository).save(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getStartAt())
                .isEqualTo(startAt.atZoneSameInstant(configuredZoneId).toLocalDateTime());
        assertThat(recordCaptor.getValue().getEndAt())
                .isEqualTo(startAt.plusMinutes(25).atZoneSameInstant(configuredZoneId).toLocalDateTime());
        verify(recordOutboxEventRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType())
                .isEqualTo(RecordOutboxEventType.WEEKLY_STATS_UPDATE_REQUESTED);
        assertThat(outboxCaptor.getValue().getAggregateId()).isEqualTo(100L);
        assertThat(outboxCaptor.getValue().getStatus()).isEqualTo(RecordOutboxEventStatus.PENDING);
    }

    @Test
    void saveRecord_checksActiveUserBeforeRecordValidation() {
        Long userId = 99L;
        OffsetDateTime startAt = validPastStartAt();
        RunningRecordRequest request = request(
                5.0,
                1500,
                300,
                startAt,
                startAt
        );

        when(userLookupService.getActiveById(userId))
                .thenThrow(new UserException(UserErrorCode.USER_NOT_FOUND));

        assertThatThrownBy(() -> recordService.saveRecord(userId, request))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.USER_NOT_FOUND);

        verify(runningRecordRepository, never()).save(any(RunningRecord.class));
    }

    @Test
    void saveRecord_throwsWhenEndAtIsNotAfterStartAt() {
        Long userId = 1L;
        OffsetDateTime startAt = validPastStartAt();
        RunningRecordRequest request = request(
                5.0,
                1500,
                300,
                startAt,
                startAt
        );

        assertThatThrownBy(() -> recordService.saveRecord(userId, request))
                .isInstanceOf(RecordValidationException.class)
                .extracting("errorCode")
                .isEqualTo(RecordValidationErrorCode.INVALID_TIME_RANGE);

        verify(runningRecordRepository, never()).save(any(RunningRecord.class));
    }

    @Test
    void saveRecord_throwsWhenDurationDoesNotMatchTimeRange() {
        Long userId = 1L;
        OffsetDateTime startAt = validPastStartAt();
        RunningRecordRequest request = request(
                5.0,
                1500,
                300,
                startAt,
                startAt.plusMinutes(25).plusSeconds(10)
        );

        assertThatThrownBy(() -> recordService.saveRecord(userId, request))
                .isInstanceOf(RecordValidationException.class)
                .extracting("errorCode")
                .isEqualTo(RecordValidationErrorCode.INVALID_DURATION);

        verify(runningRecordRepository, never()).save(any(RunningRecord.class));
    }

    @Test
    void saveRecord_throwsWhenAveragePaceDoesNotMatchDistanceAndDuration() {
        Long userId = 1L;
        OffsetDateTime startAt = validPastStartAt();
        RunningRecordRequest request = request(
                5.0,
                1500,
                330,
                startAt,
                startAt.plusMinutes(25)
        );

        assertThatThrownBy(() -> recordService.saveRecord(userId, request))
                .isInstanceOf(RecordValidationException.class)
                .extracting("errorCode")
                .isEqualTo(RecordValidationErrorCode.INVALID_AVERAGE_PACE);

        verify(runningRecordRepository, never()).save(any(RunningRecord.class));
    }

    @Test
    void saveRecord_throwsWhenRecordIsTooFarInFuture() {
        Long userId = 1L;
        OffsetDateTime startAt = OffsetDateTime.now().plusMinutes(6);
        RunningRecordRequest request = request(
                5.0,
                1500,
                300,
                startAt,
                startAt.plusMinutes(25)
        );

        assertThatThrownBy(() -> recordService.saveRecord(userId, request))
                .isInstanceOf(RecordValidationException.class)
                .extracting("errorCode")
                .isEqualTo(RecordValidationErrorCode.FUTURE_RECORD_NOT_ALLOWED);

        verify(runningRecordRepository, never()).save(any(RunningRecord.class));
    }

    @Test
    void saveRecord_allowsBoundaryToleranceForDurationAndAveragePace() {
        Long userId = 1L;
        OffsetDateTime startAt = validPastStartAt();
        RunningRecordRequest request = request(
                5.0,
                1500,
                315,
                startAt,
                startAt.plusMinutes(25).plusSeconds(5)
        );

        when(userLookupService.getActiveById(userId)).thenReturn(user(userId));
        when(serviceDateProvider.zoneId()).thenReturn(SERVICE_ZONE_ID);
        when(runningRecordRepository.save(any(RunningRecord.class))).thenAnswer(invocation -> {
            RunningRecord record = invocation.getArgument(0);
            ReflectionTestUtils.setField(record, "id", 101L);
            return record;
        });

        RecordIdResponse response = recordService.saveRecord(userId, request);

        assertThat(response.getRecordId()).isEqualTo(101L);
        verify(runningRecordRepository).save(any(RunningRecord.class));
        verify(recordOutboxEventRepository).save(any(RecordOutboxEvent.class));
    }

    @Test
    void saveRecord_normalizesZeroOptionalSensorValuesToNull() {
        Long userId = 1L;
        OffsetDateTime startAt = validPastStartAt();
        RunningRecordRequest request = request(
                5.0,
                1500,
                300,
                startAt,
                startAt.plusMinutes(25)
        );
        ReflectionTestUtils.setField(request, "avgHeartRate", 0);
        ReflectionTestUtils.setField(request, "caloriesKcal", 0);

        when(userLookupService.getActiveById(userId)).thenReturn(user(userId));
        when(serviceDateProvider.zoneId()).thenReturn(SERVICE_ZONE_ID);
        when(runningRecordRepository.save(any(RunningRecord.class))).thenAnswer(invocation -> {
            RunningRecord record = invocation.getArgument(0);
            ReflectionTestUtils.setField(record, "id", 102L);
            return record;
        });

        recordService.saveRecord(userId, request);

        ArgumentCaptor<RunningRecord> recordCaptor = ArgumentCaptor.forClass(RunningRecord.class);
        verify(runningRecordRepository).save(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getAvgHeartRate()).isNull();
        assertThat(recordCaptor.getValue().getCaloriesKcal()).isNull();
    }

    @Test
    void getSummary_keepsCurrentWeekMetricsButUsesFinalizedTier() {
        Long userId = 1L;
        LocalDate weekStart = LocalDate.of(2026, 3, 30);
        User user = user(userId);
        RunningRecord runningRecord = RunningRecord.builder()
                .userId(userId)
                .platform("IOS")
                .distanceKm(5.0)
                .durationSec(1500)
                .avgPaceSecPerKm(300)
                .avgHeartRate(150)
                .caloriesKcal(300)
                .startAt(LocalDateTime.of(2026, 3, 31, 7, 0))
                .endAt(LocalDateTime.of(2026, 3, 31, 7, 25))
                .build();

        when(userLookupService.getActiveById(userId)).thenReturn(user);
        when(serviceDateProvider.currentWeekStart()).thenReturn(weekStart);
        when(weeklyUserStatsService.findByUserIdAndWeekStartDate(userId, weekStart))
                .thenReturn(Optional.empty());
        when(runningRecordRepository.findByUserIdAndWeekRange(eq(userId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(runningRecord));
        when(currentTierResolver.resolve(userId, weekStart))
                .thenReturn(Optional.of(new CurrentTierResolver.CurrentTier(
                        "FOX",
                        "여우",
                        "S",
                        0.90,
                        "/profiles/tiers/fox.png"
                )));

        RecordSummaryResponse response = recordService.getSummary(userId);

        assertThat(response.getCurrentTier()).isEqualTo("FOX");
        assertThat(response.getCurrentTierGrade()).isEqualTo("S");
        assertThat(response.getWeeklyCount()).isEqualTo(1);
        assertThat(response.getWeeklyAvgPace()).isEqualTo(300);
        assertThat(response.getWeeklyDistance()).isEqualTo(5.0);
        verify(currentTierResolver).resolve(userId, weekStart);
    }

    @Test
    void getSummary_fallsBackToDefaultTierWhenNoFinalizedTierExists() {
        Long userId = 2L;
        LocalDate weekStart = LocalDate.of(2026, 3, 30);
        User user = user(userId);

        when(userLookupService.getActiveById(userId)).thenReturn(user);
        when(serviceDateProvider.currentWeekStart()).thenReturn(weekStart);
        when(weeklyUserStatsService.findByUserIdAndWeekStartDate(userId, weekStart))
                .thenReturn(Optional.empty());
        when(runningRecordRepository.findByUserIdAndWeekRange(eq(userId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(currentTierResolver.resolve(userId, weekStart))
                .thenReturn(Optional.empty());

        RecordSummaryResponse response = recordService.getSummary(userId);

        assertThat(response.getCurrentTier()).isEqualTo("TURTLE");
        assertThat(response.getCurrentTierGrade()).isEqualTo("B");
        assertThat(response.getWeeklyCount()).isEqualTo(0);
        assertThat(response.getWeeklyAvgPace()).isEqualTo(0);
        assertThat(response.getWeeklyDistance()).isEqualTo(0.0);
    }

    @Test
    void getSummary_usesFinalizedTierWhenCurrentWeekStatsExist() {
        Long userId = 1L;
        LocalDate weekStart = LocalDate.of(2026, 3, 30);

        when(userLookupService.getActiveById(userId)).thenReturn(user(userId));
        when(serviceDateProvider.currentWeekStart()).thenReturn(weekStart);
        when(weeklyUserStatsService.findByUserIdAndWeekStartDate(userId, weekStart))
                .thenReturn(Optional.of(weeklyStats(userId, weekStart)));
        when(currentTierResolver.resolve(userId, weekStart))
                .thenReturn(Optional.of(new CurrentTierResolver.CurrentTier(
                        "FOX",
                        "여우",
                        "S",
                        0.90,
                        "/profiles/tiers/fox.png"
                )));

        RecordSummaryResponse response = recordService.getSummary(userId);

        assertThat(response.getCurrentTier()).isEqualTo("FOX");
        assertThat(response.getCurrentTierGrade()).isEqualTo("S");
        assertThat(response.getWeeklyCount()).isEqualTo(1);
        assertThat(response.getWeeklyAvgPace()).isEqualTo(300);
        assertThat(response.getWeeklyDistance()).isEqualTo(5.0);
        verify(tierService, never()).resolveByScore(anyDouble());
    }

    @Test
    void getCurrentWeeklySummary_usesFinalizedTierWhenCurrentWeekStatsExist() {
        Long userId = 1L;
        LocalDate weekStart = LocalDate.of(2026, 3, 30);

        when(userLookupService.getActiveById(userId)).thenReturn(user(userId));
        when(serviceDateProvider.currentWeekStart()).thenReturn(weekStart);
        when(weeklyUserStatsService.findByUserIdAndWeekStartDate(userId, weekStart))
                .thenReturn(Optional.of(weeklyStats(userId, weekStart)));
        when(currentTierResolver.resolve(userId, weekStart))
                .thenReturn(Optional.of(new CurrentTierResolver.CurrentTier(
                        "HUSKY",
                        "허스키",
                        "B",
                        1.00,
                        "/profiles/tiers/husky.png"
                )));

        WeeklySummaryResponse response = recordService.getCurrentWeeklySummary(userId);

        assertThat(response.getCurrentTier()).isEqualTo("HUSKY");
        assertThat(response.getCurrentTierGrade()).isEqualTo("B");
        assertThat(response.getWeeklyCount()).isEqualTo(1);
        assertThat(response.getWeeklyAvgPace()).isEqualTo(300);
        assertThat(response.getWeeklyDistance()).isEqualTo(5.0);
        verify(tierService, never()).resolveByScore(anyDouble());
    }

    @Test
    void getWeeklySummaryList_usesRawWeeklyPerformanceTierWhenStatsDoNotExist() {
        Long userId = 5L;
        LocalDate today = LocalDate.of(2026, 4, 2);
        LocalDate weekStart = LocalDate.of(2026, 3, 30);
        User user = user(userId);
        RunningRecord runningRecord = RunningRecord.builder()
                .userId(userId)
                .platform("IOS")
                .distanceKm(5.0)
                .durationSec(1500)
                .avgPaceSecPerKm(300)
                .avgHeartRate(150)
                .caloriesKcal(300)
                .startAt(LocalDateTime.of(2026, 3, 31, 7, 0))
                .endAt(LocalDateTime.of(2026, 3, 31, 7, 25))
                .build();

        when(userLookupService.getActiveById(userId)).thenReturn(user);
        when(serviceDateProvider.today()).thenReturn(today);
        when(serviceDateProvider.currentWeekStart(today)).thenReturn(weekStart);
        when(serviceDateProvider.currentWeekStart()).thenReturn(weekStart);
        when(weeklyUserStatsService.findByUserIdAndWeekStartDate(userId, weekStart))
                .thenReturn(Optional.empty());
        when(runningRecordRepository.findByUserIdAndWeekRange(eq(userId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(runningRecord));
        when(tierService.resolveByScore(1.24))
                .thenReturn(new TierService.TierInfo("DEER", "사슴", "B", "/profiles/tiers/deer.png"));

        List<WeeklySummaryItemResponse> response = recordService.getWeeklySummaryList(userId);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getWeekStart()).isEqualTo(weekStart);
        assertThat(response.get(0).getTierCode()).isEqualTo("DEER");
        assertThat(response.get(0).getTierGrade()).isEqualTo("B");
        assertThat(response.get(0).getRunCount()).isEqualTo(1);
        assertThat(response.get(0).getAveragePace()).isEqualTo(300);
        assertThat(response.get(0).getWeeklyDistance()).isEqualTo(5.0);
        verify(currentTierResolver, never()).resolve(eq(userId), any(LocalDate.class));
    }

    private RunningRecordRequest request(
            double distanceKm,
            int durationSec,
            int avgPaceSecPerKm,
            OffsetDateTime startAt,
            OffsetDateTime endAt
    ) {
        RunningRecordRequest request = new RunningRecordRequest();
        ReflectionTestUtils.setField(request, "platform", "IOS");
        ReflectionTestUtils.setField(request, "distanceKm", distanceKm);
        ReflectionTestUtils.setField(request, "durationSec", durationSec);
        ReflectionTestUtils.setField(request, "avgPaceSecPerKm", avgPaceSecPerKm);
        ReflectionTestUtils.setField(request, "avgHeartRate", 150);
        ReflectionTestUtils.setField(request, "caloriesKcal", 300);
        ReflectionTestUtils.setField(request, "startAt", startAt);
        ReflectionTestUtils.setField(request, "endAt", endAt);
        return request;
    }

    private OffsetDateTime validPastStartAt() {
        return OffsetDateTime.now(SERVICE_ZONE_ID).minusMinutes(30);
    }

    private User user(Long userId) {
        User user = User.builder()
                .status(UserStatus.NORMAL)
                .build();
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }

    private WeeklyUserStats weeklyStats(Long userId, LocalDate weekStart) {
        return WeeklyUserStats.builder()
                .userId(userId)
                .weekStartDate(weekStart)
                .runCount(1)
                .totalDistanceKm(BigDecimal.valueOf(5.000))
                .totalDurationSec(1500)
                .weightedPaceSum(BigDecimal.valueOf(1500.000))
                .avgPaceSecPerKm(300)
                .tierScoreSum(BigDecimal.valueOf(1.24))
                .tierScore(BigDecimal.valueOf(1.24))
                .build();
    }
}
