package com.ohgiraffers.dalryeo.onboarding.service;

import com.ohgiraffers.dalryeo.auth.entity.User;
import com.ohgiraffers.dalryeo.auth.repository.UserRepository;
import com.ohgiraffers.dalryeo.onboarding.dto.EstimateTierRequest;
import com.ohgiraffers.dalryeo.onboarding.dto.EstimateTierResponse;
import com.ohgiraffers.dalryeo.onboarding.dto.NicknameCheckResponse;
import com.ohgiraffers.dalryeo.onboarding.dto.OnboardingRequest;
import com.ohgiraffers.dalryeo.onboarding.dto.OnboardingResponse;
import com.ohgiraffers.dalryeo.onboarding.dto.ProfileImageUploadResponse;
import com.ohgiraffers.dalryeo.tier.service.CurrentWeeklyTierResolver;
import com.ohgiraffers.dalryeo.tier.service.TierScoreCalculator;
import com.ohgiraffers.dalryeo.tier.service.TierService;
import com.ohgiraffers.dalryeo.user.service.UserLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Objects;

import static com.ohgiraffers.dalryeo.user.exception.UserConstraintViolationTranslator.translate;

@Service
@RequiredArgsConstructor
@Transactional
public class OnboardingService {

    private final UserRepository userRepository;
    private final UserLookupService userLookupService;
    private final TierService tierService;
    private final CurrentWeeklyTierResolver currentWeeklyTierResolver;
    private final ProfileImageStorageService profileImageStorageService;
    private final TierScoreCalculator tierScoreCalculator;
    private static final String DEFAULT_ONBOARDING_TIER_CODE = "TURTLE";

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
        User user = userLookupService.getActiveById(userId);

        userLookupService.validateNicknameAvailable(request.getNickname(), user.getNickname());

        String previousProfileImage = user.getProfileImage();
        String newProfileImage = normalizeProfileImage(request.getProfileImage());

        user.updateOnboarding(
                request.getNickname(),
                request.getGender(),
                request.getBirth(),
                request.getHeight(),
                request.getWeight(),
                newProfileImage
        );

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw translate(e);
        }
        deletePreviousManagedProfileImage(previousProfileImage, newProfileImage);
    }

    public ProfileImageUploadResponse uploadProfileImage(Long userId, MultipartFile profileImage) {
        User user = userLookupService.getActiveById(userId);

        String previousProfileImage = user.getProfileImage();
        String storedProfileImage = profileImageStorageService.storeProfileImage(userId, profileImage);

        try {
            user.updateProfileImage(storedProfileImage);
            userRepository.save(user);
        } catch (RuntimeException e) {
            profileImageStorageService.deleteStoredProfileImage(storedProfileImage);
            throw e;
        }

        deletePreviousManagedProfileImage(previousProfileImage, storedProfileImage);

        return ProfileImageUploadResponse.builder()
                .imageUrl(storedProfileImage)
                .build();
    }

    /**
     * 온보딩 정보 조회
     */
    @Transactional(readOnly = true)
    public OnboardingResponse getOnboarding(Long userId) {
        User user = userLookupService.getActiveById(userId);
        ResolvedOnboardingTier currentTier = resolveOnboardingTier(user, userId);

        return OnboardingResponse.builder()
                .nickname(user.getNickname())
                .gender(user.getGender())
                .birth(user.getBirth())
                .height(user.getHeight())
                .weight(user.getWeight())
                .displayProfileImage(resolveDisplayProfileImage(user, currentTier))
                .customProfileImage(user.getProfileImage())
                .tierCode(currentTier.tierCode())
                .tierGrade(currentTier.tierGrade())
                .defaultProfileImage(currentTier.defaultProfileImage())
                .build();
    }

    /**
     * 예상 티어 계산
     * 거리와 페이스를 기반으로 티어를 계산합니다.
     */
    @Transactional
    public EstimateTierResponse estimateTier(Long userId, EstimateTierRequest request) {
        userLookupService.getActiveById(userId);

        double distanceKm = request.getDistanceKm();
        int paceSecPerKm = request.getPaceSecPerKm();

        double score = tierScoreCalculator.calculateRecordScore(distanceKm, paceSecPerKm);
        TierService.TierInfo tierInfo = tierService.resolveByScore(score);

        return EstimateTierResponse.builder()
                .tierCode(tierInfo.tierCode())
                .displayName(tierInfo.displayName())
                .tierGrade(tierInfo.tierGrade())
                .score(score)
                .build();
    }

    private String resolveDisplayProfileImage(User user, ResolvedOnboardingTier currentTier) {
        if (hasText(user.getProfileImage())) {
            return user.getProfileImage();
        }

        return currentTier.defaultProfileImage();
    }

    private ResolvedOnboardingTier resolveOnboardingTier(User user, Long userId) {
        return currentWeeklyTierResolver.resolve(userId)
                .map(ResolvedOnboardingTier::from)
                .orElseGet(() -> resolveDefaultOnboardingTier(user));
    }

    private ResolvedOnboardingTier resolveDefaultOnboardingTier(User user) {
        if (!isOnboardingCompleted(user)) {
            return ResolvedOnboardingTier.empty();
        }
        String defaultProfileImage = tierService.findDefaultProfileImageByTierCode(DEFAULT_ONBOARDING_TIER_CODE)
                .orElse(null);
        return new ResolvedOnboardingTier(DEFAULT_ONBOARDING_TIER_CODE, "B", defaultProfileImage);
    }

    private boolean isOnboardingCompleted(User user) {
        return hasText(user.getNickname())
                && hasText(user.getGender())
                && user.getBirth() != null
                && user.getHeight() != null
                && user.getWeight() != null;
    }

    private String normalizeProfileImage(String profileImage) {
        return hasText(profileImage) ? profileImage : null;
    }

    private void deletePreviousManagedProfileImage(String previousProfileImage, String newProfileImage) {
        if (!Objects.equals(previousProfileImage, newProfileImage)) {
            profileImageStorageService.deleteStoredProfileImage(previousProfileImage);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record ResolvedOnboardingTier(
            String tierCode,
            String tierGrade,
            String defaultProfileImage
    ) {
        private static ResolvedOnboardingTier from(CurrentWeeklyTierResolver.CurrentTier currentTier) {
            return new ResolvedOnboardingTier(
                    currentTier.tierCode(),
                    currentTier.tierGrade(),
                    currentTier.defaultProfileImage()
            );
        }

        private static ResolvedOnboardingTier empty() {
            return new ResolvedOnboardingTier(null, null, null);
        }
    }

}
