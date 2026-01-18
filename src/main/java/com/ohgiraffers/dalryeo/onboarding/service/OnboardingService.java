package com.ohgiraffers.dalryeo.onboarding.service;

import com.ohgiraffers.dalryeo.auth.entity.User;
import com.ohgiraffers.dalryeo.auth.repository.UserRepository;
import com.ohgiraffers.dalryeo.onboarding.dto.EstimateTierRequest;
import com.ohgiraffers.dalryeo.onboarding.dto.EstimateTierResponse;
import com.ohgiraffers.dalryeo.onboarding.dto.NicknameCheckResponse;
import com.ohgiraffers.dalryeo.onboarding.dto.OnboardingRequest;
import com.ohgiraffers.dalryeo.onboarding.dto.OnboardingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

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
                .profileImage(user.getProfileImage())
                .build();
    }

    /**
     * 예상 티어 계산
     * 거리와 페이스를 기반으로 티어를 계산합니다.
     */
    @Transactional(readOnly = true)
    public EstimateTierResponse estimateTier(EstimateTierRequest request) {
        double distanceKm = request.getDistanceKm();
        int paceSecPerKm = request.getPaceSecPerKm();

        double score = calculateTierScore(distanceKm, paceSecPerKm);
        TierInfo tierInfo = resolveTierInfo(score);

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

    private TierInfo resolveTierInfo(double score) {
        if (score >= 1.50) {
            return new TierInfo("CHEETAH", "치타", gradeForRange(score, 1.64, 1.57, 1.50));
        } else if (score >= 1.20) {
            return new TierInfo("DEER", "사슴", gradeForRange(score, 1.39, 1.29, 1.20));
        } else if (score >= 1.00) {
            return new TierInfo("HUSKY", "허스키", gradeForRange(score, 1.13, 1.06, 1.00));
        } else if (score >= 0.86) {
            return new TierInfo("FOX", "여우", gradeForRange(score, 0.95, 0.90, 0.86));
        } else if (score >= 0.75) {
            return new TierInfo("ROE_DEER", "고라니", gradeForRange(score, 0.82, 0.78, 0.75));
        } else if (score >= 0.67) {
            return new TierInfo("SHEEP", "양", gradeForRange(score, 0.72, 0.69, 0.67));
        } else if (score >= 0.60) {
            return new TierInfo("RABBIT", "토끼", gradeForRange(score, 0.64, 0.62, 0.60));
        } else if (score >= 0.55) {
            return new TierInfo("PANDA", "판다", gradeForRange(score, 0.58, 0.56, 0.55));
        } else if (score >= 0.46) {
            return new TierInfo("DUCK", "오리", gradeForRange(score, 0.52, 0.49, 0.46));
        }
        return new TierInfo("TURTLE", "거북이", null);
    }

    private String gradeForRange(double score, double goldMin, double silverMin, double bronzeMin) {
        if (score >= goldMin) {
            return "Gold";
        } else if (score >= silverMin) {
            return "Silver";
        } else if (score >= bronzeMin) {
            return "Bronze";
        }
        return null;
    }

    private double round2(double value) {
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private record TierInfo(String tierCode, String displayName, String tierGrade) {
    }
}

