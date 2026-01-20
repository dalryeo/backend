package com.ohgiraffers.dalryeo.ranking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RankingMeResponse {
    private String nickname;
    private Integer scoreRank;
    private Integer distanceRank;
    private String tierCode;
    private String tierGrade;
    private Double tierScore;
    private Integer weeklyAvgPace;
    private Double weeklyDistance;
}
