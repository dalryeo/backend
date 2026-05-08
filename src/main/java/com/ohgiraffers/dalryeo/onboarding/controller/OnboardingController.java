package com.ohgiraffers.dalryeo.onboarding.controller;

import com.ohgiraffers.dalryeo.auth.annotation.LoginUser;
import com.ohgiraffers.dalryeo.common.CommonResponse;
import com.ohgiraffers.dalryeo.onboarding.dto.EstimateTierRequest;
import com.ohgiraffers.dalryeo.onboarding.dto.EstimateTierResponse;
import com.ohgiraffers.dalryeo.onboarding.dto.NicknameCheckResponse;
import com.ohgiraffers.dalryeo.onboarding.dto.OnboardingRequest;
import com.ohgiraffers.dalryeo.onboarding.dto.OnboardingResponse;
import com.ohgiraffers.dalryeo.onboarding.dto.ProfileImageUploadResponse;
import com.ohgiraffers.dalryeo.onboarding.service.OnboardingService;
import com.ohgiraffers.dalryeo.mypage.dto.ProfileUpdateRequest;
import com.ohgiraffers.dalryeo.mypage.service.MypageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingService onboardingService;
    private final MypageService mypageService;

    @Value("${app.public-base-url:https://api.dalryeo.store}")
    private String publicBaseUrl;

    /**
     * 닉네임 중복 체크
     * GET /onboarding/nickname/check?nickname=abc
     */
    @GetMapping("/nickname/check")
    public CommonResponse<NicknameCheckResponse> checkNickname(@RequestParam String nickname) {
        NicknameCheckResponse response = onboardingService.checkNickname(nickname);
        return CommonResponse.success(response);
    }

    /**
     * 온보딩 정보 저장
     * POST /onboarding
     */
    @PostMapping
    public CommonResponse<Void> saveOnboarding(
            @Valid @RequestBody OnboardingRequest request,
            @LoginUser Long userId) {
        onboardingService.saveOnboarding(userId, request);
        return CommonResponse.success();
    }

    /**
     * 온보딩 정보 수정
     * PUT /onboarding
     */
    @PutMapping
    public CommonResponse<Void> updateOnboarding(
            @Valid @RequestBody ProfileUpdateRequest request,
            @LoginUser Long userId) {
        mypageService.updateProfile(userId, request);
        return CommonResponse.success();
    }

    /**
     * 프로필 이미지 업로드
     * POST /onboarding/profile-image
     */
    @PostMapping(value = "/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResponse<ProfileImageUploadResponse> uploadProfileImage(
            @RequestPart("profileImage") MultipartFile profileImage,
            @LoginUser Long userId) {
        ProfileImageUploadResponse response = onboardingService.uploadProfileImage(userId, profileImage);
        return CommonResponse.success(response);
    }

    /**
     * 온보딩 정보 조회
     * GET /onboarding
     */
    @GetMapping
    public CommonResponse<OnboardingResponse> getOnboarding(@LoginUser Long userId) {
        OnboardingResponse response = enrichTierDefaultImageUrl(onboardingService.getOnboarding(userId));
        return CommonResponse.success(response);
    }

    /**
     * 예상 티어 계산
     * POST /onboarding/estimate-tier
     */
    @PostMapping("/estimate-tier")
    public CommonResponse<EstimateTierResponse> estimateTier(
            @Valid @RequestBody EstimateTierRequest request,
            @LoginUser Long userId) {
        EstimateTierResponse response = onboardingService.estimateTier(userId, request);
        return CommonResponse.success(response);
    }

    private OnboardingResponse enrichTierDefaultImageUrl(OnboardingResponse response) {
        String displayProfileImage = response.getDisplayProfileImage();
        if (!isTierDefaultImagePath(displayProfileImage)) {
            return response;
        }

        return OnboardingResponse.builder()
                .nickname(response.getNickname())
                .gender(response.getGender())
                .birth(response.getBirth())
                .height(response.getHeight())
                .weight(response.getWeight())
                .displayProfileImage(toAbsoluteUrl(displayProfileImage))
                .customProfileImage(response.getCustomProfileImage())
                .build();
    }

    private boolean isTierDefaultImagePath(String path) {
        return path != null && path.startsWith("/profiles/tiers/");
    }

    private String toAbsoluteUrl(String path) {
        return normalizePublicBaseUrl() + path;
    }

    private String normalizePublicBaseUrl() {
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            return "https://api.dalryeo.store";
        }
        return publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
    }
}
