package com.ohgiraffers.dalryeo.onboarding.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class OnboardingRequest {
    @NotBlank(message = "닉네임은 필수입니다.")
    private String nickname;

    @NotBlank(message = "성별은 필수입니다.")
    @Pattern(regexp = "^[FMO]$", message = "성별은 F, M 또는 O이어야 합니다.")
    private String gender;

    @NotNull(message = "생년월일은 필수입니다.")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate birth;

    @NotNull(message = "키는 필수입니다.")
    private Integer height;

    @NotNull(message = "몸무게는 필수입니다.")
    private Integer weight;

    private String profileImage;
}

