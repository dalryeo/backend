package com.ohgiraffers.dalryeo.mypage.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class ProfileUpdateRequest {
    @NotBlank(message = "닉네임은 필수입니다.")
    private String nickname;

    @NotBlank(message = "성별은 필수입니다.")
    @Pattern(regexp = "^[FM]$", message = "성별은 F 또는 M이어야 합니다.")
    private String gender;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @NotNull(message = "생년월일은 필수입니다.")
    private LocalDate birth;

    @NotNull(message = "키는 필수입니다.")
    private Integer height;

    @NotNull(message = "몸무게는 필수입니다.")
    private Integer weight;
}
