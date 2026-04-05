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
import com.ohgiraffers.dalryeo.tier.service.TierService;
import com.ohgiraffers.dalryeo.weeklytier.entity.WeeklyTier;
import com.ohgiraffers.dalryeo.weeklytier.repository.WeeklyTierRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OnboardingServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private WeeklyTierRepository weeklyTierRepository;

    @Mock
    private TierService tierService;

    @Mock
    private CurrentTierResolver currentTierResolver;

    @Mock
    private ProfileImageStorageService profileImageStorageService;

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

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
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

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
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

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

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

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(currentTierResolver.resolve(userId))
                .thenReturn(Optional.of(new CurrentTierResolver.CurrentTier(
                        "DEER",
                        "사슴",
                        "B",
                        1.24,
                        "/profiles/tiers/deer.jpg"
                )));

        OnboardingResponse response = onboardingService.getOnboarding(userId);

        assertThat(response.getDisplayProfileImage()).isEqualTo("/profiles/tiers/deer.jpg");
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

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(profileImageStorageService.storeProfileImage(userId, profileImage))
                .thenReturn("/profiles/custom/new.png");

        ProfileImageUploadResponse response = onboardingService.uploadProfileImage(userId, profileImage);

        assertThat(user.getProfileImage()).isEqualTo("/profiles/custom/new.png");
        assertThat(response.getImageUrl()).isEqualTo("/profiles/custom/new.png");
        verify(profileImageStorageService).deleteStoredProfileImage("/profiles/custom/old.png");
    }

    @Test
    void estimateTier_createsWeeklyTierForCurrentWeek() {
        Long userId = 3L;
        EstimateTierRequest request = estimateTierRequest(5.0, 300);
        LocalDate weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        when(weeklyTierRepository.findByUserIdAndWeekStartDate(eq(userId), eq(weekStart)))
                .thenReturn(Optional.empty());
        when(tierService.resolveByScore(1.24))
                .thenReturn(new TierService.TierInfo("DEER", "사슴", "B", "/profiles/tiers/deer.jpg"));

        EstimateTierResponse response = onboardingService.estimateTier(userId, request);

        assertThat(response.getTierCode()).isEqualTo("DEER");
        assertThat(response.getDisplayName()).isEqualTo("사슴");
        assertThat(response.getTierGrade()).isEqualTo("B");
        assertThat(response.getScore()).isEqualTo(1.24);

        ArgumentCaptor<WeeklyTier> weeklyTierCaptor = ArgumentCaptor.forClass(WeeklyTier.class);
        verify(weeklyTierRepository).save(weeklyTierCaptor.capture());
        WeeklyTier savedWeeklyTier = weeklyTierCaptor.getValue();
        assertThat(savedWeeklyTier.getUserId()).isEqualTo(userId);
        assertThat(savedWeeklyTier.getWeekStartDate()).isEqualTo(weekStart);
        assertThat(savedWeeklyTier.getTierCode()).isEqualTo("DEER");
        assertThat(savedWeeklyTier.getTierScore()).isEqualTo(124);
    }

    @Test
    void estimateTier_updatesExistingWeeklyTierForCurrentWeek() {
        Long userId = 4L;
        EstimateTierRequest request = estimateTierRequest(3.0, 360);
        LocalDate weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        WeeklyTier existingWeeklyTier = WeeklyTier.builder()
                .userId(userId)
                .weekStartDate(weekStart)
                .tierCode("PANDA")
                .tierScore(55)
                .build();

        when(weeklyTierRepository.findByUserIdAndWeekStartDate(eq(userId), eq(weekStart)))
                .thenReturn(Optional.of(existingWeeklyTier));
        when(weeklyTierRepository.save(existingWeeklyTier)).thenReturn(existingWeeklyTier);
        when(tierService.resolveByScore(1.00))
                .thenReturn(new TierService.TierInfo("HUSKY", "허스키", "B", "/profiles/tiers/husky.jpg"));

        EstimateTierResponse response = onboardingService.estimateTier(userId, request);

        assertThat(response.getTierCode()).isEqualTo("HUSKY");
        assertThat(response.getDisplayName()).isEqualTo("허스키");
        assertThat(response.getTierGrade()).isEqualTo("B");
        assertThat(response.getScore()).isEqualTo(1.00);
        assertThat(existingWeeklyTier.getTierCode()).isEqualTo("HUSKY");
        assertThat(existingWeeklyTier.getTierScore()).isEqualTo(100);
        verify(weeklyTierRepository).save(existingWeeklyTier);
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
