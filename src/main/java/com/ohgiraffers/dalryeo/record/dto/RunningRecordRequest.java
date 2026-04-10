package com.ohgiraffers.dalryeo.record.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class RunningRecordRequest {
    @NotBlank(message = "플랫폼은 필수입니다.")
    private String platform;

    @NotNull(message = "거리는 필수입니다.")
    @Positive(message = "거리는 양수여야 합니다.")
    @DecimalMin(value = "0.1", message = "거리는 0.1km 이상이어야 합니다.")
    @DecimalMax(value = "100.0", message = "거리는 100km 이하여야 합니다.")
    private Double distanceKm;

    @NotNull(message = "시간은 필수입니다.")
    @Positive(message = "시간은 양수여야 합니다.")
    @Min(value = 60, message = "시간은 60초 이상이어야 합니다.")
    @Max(value = 43200, message = "시간은 12시간 이하여야 합니다.")
    private Integer durationSec;

    @NotNull(message = "평균 페이스는 필수입니다.")
    @Positive(message = "평균 페이스는 양수여야 합니다.")
    @Min(value = 120, message = "평균 페이스는 120초/km 이상이어야 합니다.")
    @Max(value = 3600, message = "평균 페이스는 3600초/km 이하여야 합니다.")
    private Integer avgPaceSecPerKm;

    @Min(value = 30, message = "평균 심박수는 30 이상이어야 합니다.")
    @Max(value = 240, message = "평균 심박수는 240 이하여야 합니다.")
    private Integer avgHeartRate;

    @Min(value = 1, message = "칼로리는 1 이상이어야 합니다.")
    @Max(value = 10000, message = "칼로리는 10000 이하여야 합니다.")
    private Integer caloriesKcal;

    @NotNull(message = "시작 시간은 필수입니다.")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime startAt;

    @NotNull(message = "종료 시간은 필수입니다.")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime endAt;
}
