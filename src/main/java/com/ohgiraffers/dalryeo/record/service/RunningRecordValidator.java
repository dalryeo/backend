package com.ohgiraffers.dalryeo.record.service;

import com.ohgiraffers.dalryeo.record.dto.RunningRecordRequest;
import com.ohgiraffers.dalryeo.record.exception.RecordValidationErrorCode;
import com.ohgiraffers.dalryeo.record.exception.RecordValidationException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

@Component
public class RunningRecordValidator {

    static final int DURATION_TOLERANCE_SECONDS = 5;
    static final int PACE_TOLERANCE_SECONDS = 15;
    static final int FUTURE_TIME_ALLOWANCE_SECONDS = 300;
    static final int MIN_AVERAGE_HEART_RATE = 30;
    static final int MAX_AVERAGE_HEART_RATE = 240;
    static final int MIN_CALORIES_KCAL = 1;
    static final int MAX_CALORIES_KCAL = 10000;
    private static final Set<String> SUPPORTED_PLATFORMS = Set.of(
            "IOS",
            "ANDROID",
            "APPLE_WATCH",
            "GALAXY_WATCH"
    );

    public void validate(RunningRecordRequest request, Instant currentTime) {
        if (request == null || hasMissingRequiredValues(request) || currentTime == null) {
            return;
        }

        validatePlatform(request);
        validateTimeRange(request);
        validateFutureTime(request, currentTime);
        validateDuration(request);
        validateAveragePace(request);
        validateOptionalSensorValues(request);
    }

    private boolean hasMissingRequiredValues(RunningRecordRequest request) {
        return request.getDistanceKm() == null
                || request.getDurationSec() == null
                || request.getAvgPaceSecPerKm() == null
                || request.getStartAt() == null
                || request.getEndAt() == null;
    }

    private void validatePlatform(RunningRecordRequest request) {
        String platform = request.getPlatform();
        if (!StringUtils.hasText(platform) || !SUPPORTED_PLATFORMS.contains(platform)) {
            throw new RecordValidationException(RecordValidationErrorCode.INVALID_PLATFORM);
        }
    }

    private void validateTimeRange(RunningRecordRequest request) {
        if (!request.getEndAt().isAfter(request.getStartAt())) {
            throw new RecordValidationException(RecordValidationErrorCode.INVALID_TIME_RANGE);
        }
    }

    private void validateFutureTime(RunningRecordRequest request, Instant currentTime) {
        Instant maxAllowedTime = currentTime.plusSeconds(FUTURE_TIME_ALLOWANCE_SECONDS);
        if (request.getStartAt().toInstant().isAfter(maxAllowedTime)
                || request.getEndAt().toInstant().isAfter(maxAllowedTime)) {
            throw new RecordValidationException(RecordValidationErrorCode.FUTURE_RECORD_NOT_ALLOWED);
        }
    }

    private void validateDuration(RunningRecordRequest request) {
        long actualDurationSeconds = Duration.between(request.getStartAt(), request.getEndAt()).getSeconds();
        long durationDifference = Math.abs(actualDurationSeconds - request.getDurationSec());
        if (durationDifference > DURATION_TOLERANCE_SECONDS) {
            throw new RecordValidationException(RecordValidationErrorCode.INVALID_DURATION);
        }
    }

    private void validateAveragePace(RunningRecordRequest request) {
        long calculatedPaceSeconds = Math.round(request.getDurationSec() / request.getDistanceKm());
        long paceDifference = Math.abs(calculatedPaceSeconds - request.getAvgPaceSecPerKm());
        if (paceDifference > PACE_TOLERANCE_SECONDS) {
            throw new RecordValidationException(RecordValidationErrorCode.INVALID_AVERAGE_PACE);
        }
    }

    private void validateOptionalSensorValues(RunningRecordRequest request) {
        validateOptionalRange(
                request.getAvgHeartRate(),
                MIN_AVERAGE_HEART_RATE,
                MAX_AVERAGE_HEART_RATE,
                RecordValidationErrorCode.INVALID_AVERAGE_HEART_RATE
        );
        validateOptionalRange(
                request.getCaloriesKcal(),
                MIN_CALORIES_KCAL,
                MAX_CALORIES_KCAL,
                RecordValidationErrorCode.INVALID_CALORIES
        );
    }

    private void validateOptionalRange(
            Integer value,
            int min,
            int max,
            RecordValidationErrorCode errorCode
    ) {
        if (value == null || value == 0) {
            return;
        }
        if (value < min || value > max) {
            throw new RecordValidationException(errorCode);
        }
    }
}
