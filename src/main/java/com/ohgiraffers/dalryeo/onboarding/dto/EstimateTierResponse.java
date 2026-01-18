package com.ohgiraffers.dalryeo.onboarding.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EstimateTierResponse {
    private String tierCode;
    private String displayName;
    private String tierGrade;
    private Double score;
}

