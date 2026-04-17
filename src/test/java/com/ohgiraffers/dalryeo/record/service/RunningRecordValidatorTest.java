package com.ohgiraffers.dalryeo.record.service;

import com.ohgiraffers.dalryeo.record.dto.RunningRecordRequest;
import com.ohgiraffers.dalryeo.record.exception.RecordValidationErrorCode;
import com.ohgiraffers.dalryeo.record.exception.RecordValidationException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RunningRecordValidatorTest {

    private final RunningRecordValidator validator = new RunningRecordValidator();

    @ParameterizedTest
    @ValueSource(strings = {"IOS", "ANDROID", "APPLE_WATCH", "GALAXY_WATCH"})
    void validate_allowsSupportedPlatforms(String platform) {
        RunningRecordRequest request = validRequest();
        ReflectionTestUtils.setField(request, "platform", platform);

        validator.validate(request, Instant.parse("2026-04-17T00:00:00Z"));
    }

    @Test
    void validate_throwsWhenPlatformIsUnsupported() {
        RunningRecordRequest request = validRequest();
        ReflectionTestUtils.setField(request, "platform", "IPHONE");

        assertThatThrownBy(() -> validator.validate(request, Instant.parse("2026-04-17T00:00:00Z")))
                .isInstanceOf(RecordValidationException.class)
                .extracting("errorCode")
                .isEqualTo(RecordValidationErrorCode.INVALID_PLATFORM);
    }

    @Test
    void validate_throwsWhenPlatformIsBlank() {
        RunningRecordRequest request = validRequest();
        ReflectionTestUtils.setField(request, "platform", " ");

        assertThatThrownBy(() -> validator.validate(request, Instant.parse("2026-04-17T00:00:00Z")))
                .isInstanceOf(RecordValidationException.class)
                .extracting("errorCode")
                .isEqualTo(RecordValidationErrorCode.INVALID_PLATFORM);
    }

    @Test
    void validate_allowsZeroWhenOptionalSensorValuesAreMissing() {
        RunningRecordRequest request = validRequest();
        ReflectionTestUtils.setField(request, "avgHeartRate", 0);
        ReflectionTestUtils.setField(request, "caloriesKcal", 0);

        validator.validate(request, Instant.parse("2026-04-17T00:00:00Z"));
    }

    @Test
    void validate_throwsWhenAverageHeartRateIsPositiveButOutsideRange() {
        RunningRecordRequest request = validRequest();
        ReflectionTestUtils.setField(request, "avgHeartRate", 10);

        assertThatThrownBy(() -> validator.validate(request, Instant.parse("2026-04-17T00:00:00Z")))
                .isInstanceOf(RecordValidationException.class)
                .extracting("errorCode")
                .isEqualTo(RecordValidationErrorCode.INVALID_AVERAGE_HEART_RATE);
    }

    @Test
    void validate_throwsWhenCaloriesIsPositiveButOutsideRange() {
        RunningRecordRequest request = validRequest();
        ReflectionTestUtils.setField(request, "caloriesKcal", 10001);

        assertThatThrownBy(() -> validator.validate(request, Instant.parse("2026-04-17T00:00:00Z")))
                .isInstanceOf(RecordValidationException.class)
                .extracting("errorCode")
                .isEqualTo(RecordValidationErrorCode.INVALID_CALORIES);
    }

    private RunningRecordRequest validRequest() {
        OffsetDateTime startAt = OffsetDateTime.of(2026, 3, 31, 7, 0, 0, 0, ZoneOffset.ofHours(9));
        RunningRecordRequest request = new RunningRecordRequest();
        ReflectionTestUtils.setField(request, "platform", "IOS");
        ReflectionTestUtils.setField(request, "distanceKm", 5.0);
        ReflectionTestUtils.setField(request, "durationSec", 1500);
        ReflectionTestUtils.setField(request, "avgPaceSecPerKm", 300);
        ReflectionTestUtils.setField(request, "avgHeartRate", 150);
        ReflectionTestUtils.setField(request, "caloriesKcal", 300);
        ReflectionTestUtils.setField(request, "startAt", startAt);
        ReflectionTestUtils.setField(request, "endAt", startAt.plusSeconds(1500));
        return request;
    }
}
