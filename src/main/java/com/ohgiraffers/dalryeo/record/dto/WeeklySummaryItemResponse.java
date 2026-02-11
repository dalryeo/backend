package com.ohgiraffers.dalryeo.record.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklySummaryItemResponse {
    private LocalDate weekStart;
    private String tierCode;
    private String tierGrade;
    private Integer runCount;
    private Integer averagePace;
    private Double weeklyDistance;
}
