package com.ohgiraffers.dalryeo.ranking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DistanceRankingResponse {
    @Setter
    private Integer rank;
    private String nickname;
    private Double weeklyDistance;
    private String tierCode;
    private String tierGrade;
}

