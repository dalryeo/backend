package com.ohgiraffers.dalryeo.record.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyRecordResponse {
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;
    private Double distanceKm;
    private Integer paceSecPerKm;
}

