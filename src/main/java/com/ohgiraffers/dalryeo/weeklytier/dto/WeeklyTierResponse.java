package com.ohgiraffers.dalryeo.weeklytier.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyTierResponse {
    private LocalDate weekStartDate;
    private String tierCode;
    private String tierGrade;
    private Double tierScore;
}
