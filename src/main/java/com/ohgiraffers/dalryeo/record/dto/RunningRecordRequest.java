package com.ohgiraffers.dalryeo.record.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
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
    private Double distanceKm;

    @NotNull(message = "시간은 필수입니다.")
    @Positive(message = "시간은 양수여야 합니다.")
    private Integer durationSec;

    @NotNull(message = "평균 페이스는 필수입니다.")
    @Positive(message = "평균 페이스는 양수여야 합니다.")
    private Integer avgPaceSecPerKm;

    private Integer avgHeartRate;

    private Integer caloriesKcal;

    @NotNull(message = "시작 시간은 필수입니다.")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime startAt;

    @NotNull(message = "종료 시간은 필수입니다.")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime endAt;
}

