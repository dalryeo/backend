package com.ohgiraffers.dalryeo.record.service;

import com.ohgiraffers.dalryeo.auth.entity.User;
import com.ohgiraffers.dalryeo.auth.entity.UserStatus;
import com.ohgiraffers.dalryeo.record.dto.RecordIdResponse;
import com.ohgiraffers.dalryeo.record.dto.RecordSummaryResponse;
import com.ohgiraffers.dalryeo.record.dto.RunningRecordRequest;
import com.ohgiraffers.dalryeo.record.entity.RunningRecord;
import com.ohgiraffers.dalryeo.record.exception.RecordValidationErrorCode;
import com.ohgiraffers.dalryeo.record.exception.RecordValidationException;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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

    @InjectMocks
    private RecordService recordService;

    @Test
    void saveRecord_savesRecordWhenRequestIsValid() {
        Long userId = 1L;
        OffsetDateTime startAt = validPastStartAt();
        RunningRecordRequest request = request(
                5.0,
                1500,
                300,
                startAt,
                startAt.plusMinutes(25)
        );

        when(userLookupService.getActiveById(userId)).thenReturn(user(userId));
        when(runningRecordRepository.save(any(RunningRecord.class))).thenAnswer(invocation -> {
            RunningRecord record = invocation.getArgument(0);
            ReflectionTestUtils.setField(record, "id", 100L);
            return record;
        });

        RecordIdResponse response = recordService.saveRecord(userId, request);

        ArgumentCaptor<RunningRecord> recordCaptor = ArgumentCaptor.forClass(RunningRecord.class);
        assertThat(response.getRecordId()).isEqualTo(100L);
        verify(runningRecordRepository).save(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getStartAt())
                .isEqualTo(startAt.atZoneSameInstant(SERVICE_ZONE_ID).toLocalDateTime());
        assertThat(recordCaptor.getValue().getEndAt())
                .isEqualTo(startAt.plusMinutes(25).atZoneSameInstant(SERVICE_ZONE_ID).toLocalDateTime());
        verify(weeklyUserStatsService).applyRecord(any(RunningRecord.class));
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
        when(runningRecordRepository.save(any(RunningRecord.class))).thenAnswer(invocation -> {
            RunningRecord record = invocation.getArgument(0);
            ReflectionTestUtils.setField(record, "id", 101L);
            return record;
        });

        RecordIdResponse response = recordService.saveRecord(userId, request);

        assertThat(response.getRecordId()).isEqualTo(101L);
        verify(runningRecordRepository).save(any(RunningRecord.class));
        verify(weeklyUserStatsService).applyRecord(any(RunningRecord.class));
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
    void getSummary_usesCurrentWeekRecordsToResolveTier() {
        Long userId = 1L;
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
        when(runningRecordRepository.findByUserIdAndWeekRange(eq(userId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(runningRecord));
        when(currentTierResolver.resolve(eq(userId), any(LocalDate.class), anyList()))
                .thenReturn(Optional.of(new CurrentTierResolver.CurrentTier(
                        "DEER",
                        "사슴",
                        "B",
                        1.24,
                        "/profiles/tiers/deer.png"
                )));

        RecordSummaryResponse response = recordService.getSummary(userId);

        assertThat(response.getCurrentTier()).isEqualTo("DEER");
        assertThat(response.getCurrentTierGrade()).isEqualTo("B");
        assertThat(response.getWeeklyCount()).isEqualTo(1);
        assertThat(response.getWeeklyAvgPace()).isEqualTo(300);
        assertThat(response.getWeeklyDistance()).isEqualTo(5.0);
    }

    @Test
    void getSummary_fallsBackToWeeklyTierWhenNoCurrentWeekRecordsExist() {
        Long userId = 2L;
        User user = user(userId);

        when(userLookupService.getActiveById(userId)).thenReturn(user);
        when(runningRecordRepository.findByUserIdAndWeekRange(eq(userId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(currentTierResolver.resolve(eq(userId), any(LocalDate.class), anyList()))
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
        assertThat(response.getWeeklyCount()).isEqualTo(0);
        assertThat(response.getWeeklyAvgPace()).isEqualTo(0);
        assertThat(response.getWeeklyDistance()).isEqualTo(0.0);
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
}
