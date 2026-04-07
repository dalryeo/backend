package com.ohgiraffers.dalryeo.onboarding.controller;

import com.ohgiraffers.dalryeo.auth.exception.AuthErrorCode;
import com.ohgiraffers.dalryeo.auth.exception.AuthException;
import com.ohgiraffers.dalryeo.auth.jwt.JwtTokenExtractor;
import com.ohgiraffers.dalryeo.auth.jwt.JwtTokenProvider;
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
import jakarta.servlet.http.HttpServletRequest;
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
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtTokenExtractor jwtTokenExtractor;

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
            HttpServletRequest httpRequest) {
        Long userId = extractUserIdFromRequest(httpRequest);
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
            HttpServletRequest httpRequest) {
        Long userId = extractUserIdFromRequest(httpRequest);
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
            HttpServletRequest httpRequest) {
        Long userId = extractUserIdFromRequest(httpRequest);
        ProfileImageUploadResponse response = onboardingService.uploadProfileImage(userId, profileImage);
        return CommonResponse.success(response);
    }

    /**
     * 온보딩 정보 조회
     * GET /onboarding
     */
    @GetMapping
    public CommonResponse<OnboardingResponse> getOnboarding(HttpServletRequest httpRequest) {
        Long userId = extractUserIdFromRequest(httpRequest);
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
            HttpServletRequest httpRequest) {
        Long userId = extractUserIdFromRequest(httpRequest);
        EstimateTierResponse response = onboardingService.estimateTier(userId, request);
        return CommonResponse.success(response);
    }

    /**
     * 요청에서 AccessToken을 추출하여 사용자 ID를 반환
     */
    private Long extractUserIdFromRequest(HttpServletRequest request) {
        String token = jwtTokenExtractor.extractToken(request);
        if (token == null || !jwtTokenProvider.validateToken(token)) {
            throw new AuthException(AuthErrorCode.REFRESH_TOKEN_EXPIRED);
        }
        return jwtTokenProvider.getUserIdFromToken(token);
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
