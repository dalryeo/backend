package com.ohgiraffers.dalryeo.mypage.service;

import com.ohgiraffers.dalryeo.auth.entity.User;
import com.ohgiraffers.dalryeo.auth.entity.UserStatus;
import com.ohgiraffers.dalryeo.auth.repository.UserRepository;
import com.ohgiraffers.dalryeo.mypage.dto.ProfileUpdateRequest;
import com.ohgiraffers.dalryeo.onboarding.service.ProfileImageStorageService;
import com.ohgiraffers.dalryeo.user.exception.UserErrorCode;
import com.ohgiraffers.dalryeo.user.exception.UserException;
import com.ohgiraffers.dalryeo.user.service.UserLookupService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MypageServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserLookupService userLookupService;

    @Mock
    private ProfileImageStorageService profileImageStorageService;

    @InjectMocks
    private MypageService mypageService;

    @Test
    void updateProfile_updatesCustomProfileImageWithOtherFields() {
        Long userId = 1L;
        User user = userWithId(userId);
        ProfileUpdateRequest request = updateRequest(
                "runner1",
                "F",
                LocalDate.of(1998, 5, 12),
                165,
                52,
                "https://cdn.example.com/custom.jpg"
        );

        when(userLookupService.getActiveById(userId)).thenReturn(user);
        when(userRepository.save(user)).thenReturn(user);

        mypageService.updateProfile(userId, request);

        assertThat(user.getNickname()).isEqualTo("runner1");
        assertThat(user.getGender()).isEqualTo("F");
        assertThat(user.getBirth()).isEqualTo(LocalDate.of(1998, 5, 12));
        assertThat(user.getHeight()).isEqualTo(165);
        assertThat(user.getWeight()).isEqualTo(52);
        assertThat(user.getProfileImage()).isEqualTo("https://cdn.example.com/custom.jpg");
        verify(userRepository).save(user);
    }

    @Test
    void updateProfile_clearsCustomProfileImageWhenNullIsProvided() {
        Long userId = 2L;
        User user = userWithId(userId);
        ReflectionTestUtils.setField(user, "profileImage", "/profiles/custom/original.jpg");
        ProfileUpdateRequest request = updateRequest(
                "runner2",
                "M",
                LocalDate.of(1997, 1, 3),
                178,
                70,
                null
        );

        when(userLookupService.getActiveById(userId)).thenReturn(user);
        when(userRepository.save(user)).thenReturn(user);

        mypageService.updateProfile(userId, request);

        assertThat(user.getProfileImage()).isNull();
        verify(userRepository).save(user);
        verify(profileImageStorageService).deleteStoredProfileImage("/profiles/custom/original.jpg");
    }

    @Test
    void updateProfile_throwsWhenNicknameAlreadyExists() {
        Long userId = 3L;
        User user = userWithId(userId);
        ReflectionTestUtils.setField(user, "nickname", "current");
        ProfileUpdateRequest request = updateRequest(
                "taken",
                "F",
                LocalDate.of(1998, 5, 12),
                165,
                52,
                null
        );

        when(userLookupService.getActiveById(userId)).thenReturn(user);
        when(userRepository.existsByNickname("taken")).thenReturn(true);

        assertThatThrownBy(() -> mypageService.updateProfile(userId, request))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.DUPLICATED_NICKNAME);
    }

    private User userWithId(Long id) {
        User user = User.builder()
                .status(UserStatus.NORMAL)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private ProfileUpdateRequest updateRequest(
            String nickname,
            String gender,
            LocalDate birth,
            Integer height,
            Integer weight,
            String profileImage
    ) {
        ProfileUpdateRequest request = new ProfileUpdateRequest();
        ReflectionTestUtils.setField(request, "nickname", nickname);
        ReflectionTestUtils.setField(request, "gender", gender);
        ReflectionTestUtils.setField(request, "birth", birth);
        ReflectionTestUtils.setField(request, "height", height);
        ReflectionTestUtils.setField(request, "weight", weight);
        ReflectionTestUtils.setField(request, "profileImage", profileImage);
        return request;
    }
}
