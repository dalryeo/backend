package com.ohgiraffers.dalryeo.record.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklySummaryResponse {
    private String currentTier;
    private String currentTierGrade;
    private Integer weeklyCount;
    private Integer weeklyAvgPace;
    private Double weeklyDistance;
}
