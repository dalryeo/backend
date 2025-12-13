package com.ohgiraffers.dalryeo.analysis.dto;

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
public class RecordListItemResponse {
    private Long recordId;
    private Double distanceKm;
    private Integer durationSec;
    private Integer avgPaceSecPerKm;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;
}

