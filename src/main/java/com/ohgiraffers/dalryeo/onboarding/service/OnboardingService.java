package com.ohgiraffers.dalryeo.onboarding.service;

import com.ohgiraffers.dalryeo.auth.entity.User;
import com.ohgiraffers.dalryeo.auth.repository.UserRepository;
import com.ohgiraffers.dalryeo.onboarding.dto.EstimateTierRequest;
import com.ohgiraffers.dalryeo.onboarding.dto.EstimateTierResponse;
import com.ohgiraffers.dalryeo.onboarding.dto.NicknameCheckResponse;
import com.ohgiraffers.dalryeo.onboarding.dto.OnboardingRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class OnboardingService {

    private final UserRepository userRepository;

    /**
     * 닉네임 중복 체크
     */
    @Transactional(readOnly = true)
    public NicknameCheckResponse checkNickname(String nickname) {
        boolean exists = userRepository.existsByNickname(nickname);
        return NicknameCheckResponse.builder()
                .available(!exists)
                .build();
    }

    /**
     * 온보딩 정보 저장
     */
    public void saveOnboarding(Long userId, OnboardingRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

        user.updateOnboarding(
                request.getNickname(),
                request.getGender(),
                request.getBirth(),
                request.getHeight(),
                request.getWeight(),
                request.getProfileImage()
        );

        userRepository.save(user);
    }

    /**
     * 예상 티어 계산
     * 거리와 페이스를 기반으로 티어를 계산합니다.
     */
    @Transactional(readOnly = true)
    public EstimateTierResponse estimateTier(EstimateTierRequest request) {
        double distanceKm = request.getDistanceKm();
        int paceSecPerKm = request.getPaceSecPerKm();

        // 티어 계산 로직
        // 거리가 길수록, 페이스가 빠를수록(초가 적을수록) 높은 티어
        // 예시: BRONZE, SILVER, GOLD, PLATINUM, DIAMOND
        String tier = calculateTier(distanceKm, paceSecPerKm);

        return EstimateTierResponse.builder()
                .tier(tier)
                .build();
    }

    /**
     * 거리와 페이스를 기반으로 티어를 계산합니다.
     * 실제 비즈니스 로직에 맞게 수정이 필요할 수 있습니다.
     */
    private String calculateTier(double distanceKm, int paceSecPerKm) {
        // 예시 티어 계산 로직
        // 거리 점수: 5km 이상이면 높은 점수
        // 페이스 점수: 300초(5분/km) 이하면 높은 점수
        double distanceScore = Math.min(distanceKm / 5.0, 1.0) * 50;
        double paceScore = Math.max(0, (600 - paceSecPerKm) / 600.0) * 50;
        double totalScore = distanceScore + paceScore;

        if (totalScore >= 80) {
            return "DIAMOND";
        } else if (totalScore >= 60) {
            return "PLATINUM";
        } else if (totalScore >= 40) {
            return "GOLD";
        } else if (totalScore >= 20) {
            return "SILVER";
        } else {
            return "BRONZE";
        }
    }
}

