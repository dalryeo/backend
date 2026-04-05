package com.ohgiraffers.dalryeo.onboarding.service;

import com.ohgiraffers.dalryeo.auth.entity.User;
import com.ohgiraffers.dalryeo.auth.repository.UserRepository;
import com.ohgiraffers.dalryeo.onboarding.dto.EstimateTierRequest;
import com.ohgiraffers.dalryeo.onboarding.dto.EstimateTierResponse;
import com.ohgiraffers.dalryeo.onboarding.dto.NicknameCheckResponse;
import com.ohgiraffers.dalryeo.onboarding.dto.OnboardingRequest;
import com.ohgiraffers.dalryeo.onboarding.dto.OnboardingResponse;
import com.ohgiraffers.dalryeo.tier.service.CurrentTierResolver;
import com.ohgiraffers.dalryeo.tier.service.TierService;
import com.ohgiraffers.dalryeo.weeklytier.entity.WeeklyTier;
import com.ohgiraffers.dalryeo.weeklytier.repository.WeeklyTierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

@Service
@RequiredArgsConstructor
@Transactional
public class OnboardingService {

    private final UserRepository userRepository;
    private final WeeklyTierRepository weeklyTierRepository;
    private final TierService tierService;
    private final CurrentTierResolver currentTierResolver;

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
                normalizeProfileImage(request.getProfileImage())
        );

        userRepository.save(user);
    }

    /**
     * 온보딩 정보 조회
     */
    @Transactional(readOnly = true)
    public OnboardingResponse getOnboarding(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

        return OnboardingResponse.builder()
                .nickname(user.getNickname())
                .gender(user.getGender())
                .birth(user.getBirth())
                .height(user.getHeight())
                .weight(user.getWeight())
                .displayProfileImage(resolveDisplayProfileImage(user, userId))
                .customProfileImage(user.getProfileImage())
                .build();
    }

    /**
     * 예상 티어 계산
     * 거리와 페이스를 기반으로 티어를 계산합니다.
     */
    @Transactional
    public EstimateTierResponse estimateTier(Long userId, EstimateTierRequest request) {
        double distanceKm = request.getDistanceKm();
        int paceSecPerKm = request.getPaceSecPerKm();

        double score = calculateTierScore(distanceKm, paceSecPerKm);
        TierService.TierInfo tierInfo = tierService.resolveByScore(score);

        saveWeeklyTier(userId, tierInfo, score);

        return EstimateTierResponse.builder()
                .tierCode(tierInfo.tierCode())
                .displayName(tierInfo.displayName())
                .tierGrade(tierInfo.tierGrade())
                .score(score)
                .build();
    }

    /**
     * 거리와 페이스를 기반으로 티어 점수를 계산합니다.
     * 실제 비즈니스 로직에 맞게 수정이 필요할 수 있습니다.
     */
    private double calculateTierScore(double distanceKm, int paceSecPerKm) {
        double paceMinutes = round2(paceSecPerKm / 60.0);
        double baseScore = round2(6.00 / paceMinutes);
        double distanceWeight = getDistanceWeight(distanceKm);
        return round2(baseScore * distanceWeight);
    }

    private double getDistanceWeight(double distanceKm) {
        if (distanceKm < 1.00) {
            return 0.50;
        } else if (distanceKm < 2.00) {
            return 0.60;
        } else if (distanceKm < 3.00) {
            return 0.70;
        } else if (distanceKm < 5.00) {
            return 1.00;
        } else if (distanceKm < 7.00) {
            return 1.03;
        } else if (distanceKm < 9.00) {
            return 1.05;
        } else if (distanceKm < 11.00) {
            return 1.06;
        } else if (distanceKm < 15.00) {
            return 1.07;
        } else if (distanceKm < 25.00) {
            return 1.08;
        } else if (distanceKm < 40.00) {
            return 1.09;
        }
        return 1.10;
    }

    private double round2(double value) {
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private void saveWeeklyTier(Long userId, TierService.TierInfo tierInfo, double score) {
        LocalDate weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Integer tierScore = toTierScoreInt(score);

        WeeklyTier weeklyTier = weeklyTierRepository.findByUserIdAndWeekStartDate(userId, weekStart)
                .orElseGet(() -> WeeklyTier.builder()
                        .userId(userId)
                        .weekStartDate(weekStart)
                        .tierCode(tierInfo.tierCode())
                        .tierScore(tierScore)
                        .build());

        weeklyTier.updateTier(tierInfo.tierCode(), tierScore);
        weeklyTierRepository.save(weeklyTier);
    }

    private Integer toTierScoreInt(double score) {
        return BigDecimal.valueOf(score)
                .setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2)
                .intValue();
    }

    private String resolveDisplayProfileImage(User user, Long userId) {
        if (hasText(user.getProfileImage())) {
            return user.getProfileImage();
        }

        return currentTierResolver.resolve(userId)
                .map(CurrentTierResolver.CurrentTier::defaultProfileImage)
                .orElse(null);
    }

    private String normalizeProfileImage(String profileImage) {
        return hasText(profileImage) ? profileImage : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

}
