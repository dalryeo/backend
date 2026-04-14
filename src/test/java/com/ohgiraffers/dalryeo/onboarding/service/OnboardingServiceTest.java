package com.ohgiraffers.dalryeo.onboarding.service;

import com.ohgiraffers.dalryeo.auth.entity.User;
import com.ohgiraffers.dalryeo.auth.entity.UserStatus;
import com.ohgiraffers.dalryeo.auth.repository.UserRepository;
import com.ohgiraffers.dalryeo.onboarding.dto.EstimateTierRequest;
import com.ohgiraffers.dalryeo.onboarding.dto.EstimateTierResponse;
import com.ohgiraffers.dalryeo.onboarding.dto.NicknameCheckResponse;
import com.ohgiraffers.dalryeo.onboarding.dto.OnboardingRequest;
import com.ohgiraffers.dalryeo.onboarding.dto.OnboardingResponse;
import com.ohgiraffers.dalryeo.onboarding.dto.ProfileImageUploadResponse;
import com.ohgiraffers.dalryeo.tier.service.CurrentTierResolver;
import com.ohgiraffers.dalryeo.tier.service.TierScoreCalculator;
import com.ohgiraffers.dalryeo.tier.service.TierService;
import com.ohgiraffers.dalryeo.user.exception.UserErrorCode;
import com.ohgiraffers.dalryeo.user.exception.UserException;
import com.ohgiraffers.dalryeo.user.service.UserLookupService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OnboardingServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserLookupService userLookupService;

    @Mock
    private TierService tierService;

    @Mock
    private CurrentTierResolver currentTierResolver;

    @Mock
    private ProfileImageStorageService profileImageStorageService;

    @Spy
    private TierScoreCalculator tierScoreCalculator = new TierScoreCalculator();

    @InjectMocks
    private OnboardingService onboardingService;

    @Test
    void checkNickname_returnsUnavailableWhenNicknameAlreadyExists() {
        when(userRepository.existsByNickname("runner1")).thenReturn(true);

        NicknameCheckResponse response = onboardingService.checkNickname("runner1");

        assertThat(response.getAvailable()).isFalse();
    }

    @Test
    void saveOnboarding_updatesUserProfileFields() {
        Long userId = 1L;
        User user = userWithId(userId);
        OnboardingRequest request = onboardingRequest(
                "runner1",
                "F",
                LocalDate.of(1998, 5, 12),
                165,
                52,
                "profile.png"
        );

        when(userLookupService.getActiveById(userId)).thenReturn(user);
        when(userRepository.save(user)).thenReturn(user);

        onboardingService.saveOnboarding(userId, request);

        assertThat(user.getNickname()).isEqualTo("runner1");
        assertThat(user.getGender()).isEqualTo("F");
        assertThat(user.getBirth()).isEqualTo(LocalDate.of(1998, 5, 12));
        assertThat(user.getHeight()).isEqualTo(165);
        assertThat(user.getWeight()).isEqualTo(52);
        assertThat(user.getProfileImage()).isEqualTo("profile.png");
        verify(userRepository).save(user);
    }

    @Test
    void saveOnboarding_throwsWhenNicknameAlreadyExists() {
        Long userId = 10L;
        User user = userWithId(userId);
        OnboardingRequest request = onboardingRequest(
                "taken",
                "F",
                LocalDate.of(1998, 5, 12),
                165,
                52,
                null
        );

        when(userLookupService.getActiveById(userId)).thenReturn(user);
        doThrow(new UserException(UserErrorCode.DUPLICATED_NICKNAME))
                .when(userLookupService)
                .validateNicknameAvailable("taken", null);

        assertThatThrownBy(() -> onboardingService.saveOnboarding(userId, request))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.DUPLICATED_NICKNAME);
    }

    @Test
    void saveOnboarding_deletesManagedProfileImageWhenClearingCustomImage() {
        Long userId = 6L;
        User user = userWithId(userId);
        ReflectionTestUtils.setField(user, "profileImage", "/profiles/custom/original.png");
        OnboardingRequest request = onboardingRequest(
                "runner6",
                "F",
                LocalDate.of(1998, 5, 12),
                165,
                52,
                null
        );

        when(userLookupService.getActiveById(userId)).thenReturn(user);
        when(userRepository.save(user)).thenReturn(user);

        onboardingService.saveOnboarding(userId, request);

        assertThat(user.getProfileImage()).isNull();
        verify(profileImageStorageService).deleteStoredProfileImage("/profiles/custom/original.png");
    }

    @Test
    void getOnboarding_returnsStoredProfileFields() {
        Long userId = 2L;
        User user = userWithId(userId);
        ReflectionTestUtils.setField(user, "nickname", "runner2");
        ReflectionTestUtils.setField(user, "gender", "M");
        ReflectionTestUtils.setField(user, "birth", LocalDate.of(1995, 1, 3));
        ReflectionTestUtils.setField(user, "height", 178);
        ReflectionTestUtils.setField(user, "weight", 70);
        ReflectionTestUtils.setField(user, "profileImage", "image2.png");

        when(userLookupService.getActiveById(userId)).thenReturn(user);

        OnboardingResponse response = onboardingService.getOnboarding(userId);

        assertThat(response.getNickname()).isEqualTo("runner2");
        assertThat(response.getGender()).isEqualTo("M");
        assertThat(response.getBirth()).isEqualTo(LocalDate.of(1995, 1, 3));
        assertThat(response.getHeight()).isEqualTo(178);
        assertThat(response.getWeight()).isEqualTo(70);
        assertThat(response.getDisplayProfileImage()).isEqualTo("image2.png");
        assertThat(response.getCustomProfileImage()).isEqualTo("image2.png");
    }

    @Test
    void getOnboarding_returnsTierDefaultProfileImageWhenCustomProfileImageDoesNotExist() {
        Long userId = 5L;
        User user = userWithId(userId);
        ReflectionTestUtils.setField(user, "nickname", "runner5");

        when(userLookupService.getActiveById(userId)).thenReturn(user);
        when(currentTierResolver.resolve(userId))
                .thenReturn(Optional.of(new CurrentTierResolver.CurrentTier(
                        "DEER",
                        "사슴",
                        "B",
                        1.24,
                        "/profiles/tiers/deer.png"
                )));

        OnboardingResponse response = onboardingService.getOnboarding(userId);

        assertThat(response.getDisplayProfileImage()).isEqualTo("/profiles/tiers/deer.png");
        assertThat(response.getCustomProfileImage()).isNull();
    }

    @Test
    void getOnboarding_returnsTurtleProfileImageWhenOnboardingCompletedWithoutCurrentTier() {
        Long userId = 6L;
        User user = userWithId(userId);
        ReflectionTestUtils.setField(user, "nickname", "runner6");
        ReflectionTestUtils.setField(user, "gender", "F");
        ReflectionTestUtils.setField(user, "birth", LocalDate.of(1998, 5, 12));
        ReflectionTestUtils.setField(user, "height", 165);
        ReflectionTestUtils.setField(user, "weight", 52);

        when(userLookupService.getActiveById(userId)).thenReturn(user);
        when(currentTierResolver.resolve(userId)).thenReturn(Optional.empty());
        when(tierService.findDefaultProfileImageByTierCode("TURTLE"))
                .thenReturn(Optional.of("/profiles/tiers/turtle.png"));

        OnboardingResponse response = onboardingService.getOnboarding(userId);

        assertThat(response.getDisplayProfileImage()).isEqualTo("/profiles/tiers/turtle.png");
        assertThat(response.getCustomProfileImage()).isNull();
    }

    @Test
    void getOnboarding_returnsNullWhenOnboardingIsIncompleteAndCurrentTierDoesNotExist() {
        Long userId = 9L;
        User user = userWithId(userId);

        when(userLookupService.getActiveById(userId)).thenReturn(user);
        when(currentTierResolver.resolve(userId)).thenReturn(Optional.empty());

        OnboardingResponse response = onboardingService.getOnboarding(userId);

        assertThat(response.getDisplayProfileImage()).isNull();
        assertThat(response.getCustomProfileImage()).isNull();
    }

    @Test
    void uploadProfileImage_updatesUserWithManagedImageUrl() {
        Long userId = 7L;
        User user = userWithId(userId);
        ReflectionTestUtils.setField(user, "profileImage", "/profiles/custom/old.png");
        MockMultipartFile profileImage = new MockMultipartFile(
                "profileImage",
                "profile.png",
                "image/png",
                "image-content".getBytes(StandardCharsets.UTF_8)
        );

        when(userLookupService.getActiveById(userId)).thenReturn(user);
        when(userRepository.save(user)).thenReturn(user);
        when(profileImageStorageService.storeProfileImage(userId, profileImage))
                .thenReturn("/profiles/custom/new.png");

        ProfileImageUploadResponse response = onboardingService.uploadProfileImage(userId, profileImage);

        assertThat(user.getProfileImage()).isEqualTo("/profiles/custom/new.png");
        assertThat(response.getImageUrl()).isEqualTo("/profiles/custom/new.png");
        verify(profileImageStorageService).deleteStoredProfileImage("/profiles/custom/old.png");
    }

    @Test
    void estimateTier_returnsResponseWithoutPersistingWeeklyTier() {
        Long userId = 3L;
        EstimateTierRequest request = estimateTierRequest(5.0, 300);
        when(tierService.resolveByScore(1.24))
                .thenReturn(new TierService.TierInfo("DEER", "사슴", "B", "/profiles/tiers/deer.png"));

        EstimateTierResponse response = onboardingService.estimateTier(userId, request);

        assertThat(response.getTierCode()).isEqualTo("DEER");
        assertThat(response.getDisplayName()).isEqualTo("사슴");
        assertThat(response.getTierGrade()).isEqualTo("B");
        assertThat(response.getScore()).isEqualTo(1.24);
        verifyNoInteractions(userRepository, currentTierResolver, profileImageStorageService);
    }

    private User userWithId(Long id) {
        User user = User.builder()
                .status(UserStatus.NORMAL)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private OnboardingRequest onboardingRequest(
            String nickname,
            String gender,
            LocalDate birth,
            Integer height,
            Integer weight,
            String profileImage
    ) {
        OnboardingRequest request = new OnboardingRequest();
        ReflectionTestUtils.setField(request, "nickname", nickname);
        ReflectionTestUtils.setField(request, "gender", gender);
        ReflectionTestUtils.setField(request, "birth", birth);
        ReflectionTestUtils.setField(request, "height", height);
        ReflectionTestUtils.setField(request, "weight", weight);
        ReflectionTestUtils.setField(request, "profileImage", profileImage);
        return request;
    }

    private EstimateTierRequest estimateTierRequest(Double distanceKm, Integer paceSecPerKm) {
        EstimateTierRequest request = new EstimateTierRequest();
        ReflectionTestUtils.setField(request, "distanceKm", distanceKm);
        ReflectionTestUtils.setField(request, "paceSecPerKm", paceSecPerKm);
        return request;
    }
}
