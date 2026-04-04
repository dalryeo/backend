package com.ohgiraffers.dalryeo.onboarding.dto;

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
public class OnboardingResponse {
    private String nickname;
    private String gender;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate birth;
    private Integer height;
    private Integer weight;
    private String displayProfileImage;
    private String customProfileImage;
}
