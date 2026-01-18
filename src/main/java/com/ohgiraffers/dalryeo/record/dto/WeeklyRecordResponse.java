package com.ohgiraffers.dalryeo.record.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyRecordResponse {
    private Long recordId;
    private String platform;
    private Double distanceKm;
    private Integer durationSec;
    private Integer avgPaceSecPerKm;
    private Integer avgHeartRate;
    private Integer caloriesKcal;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime startAt;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime endAt;
}

