package com.ohgiraffers.dalryeo.onboarding.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class EstimateTierRequest {
    @NotNull(message = "거리는 필수입니다.")
    @Positive(message = "거리는 양수여야 합니다.")
    private Double distanceKm;

    @NotNull(message = "페이스는 필수입니다.")
    @Positive(message = "페이스는 양수여야 합니다.")
    private Integer paceSecPerKm;
}

