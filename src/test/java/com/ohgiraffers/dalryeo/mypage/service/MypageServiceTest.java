package com.ohgiraffers.dalryeo.mypage.service;

import com.ohgiraffers.dalryeo.auth.entity.User;
import com.ohgiraffers.dalryeo.auth.entity.UserStatus;
import com.ohgiraffers.dalryeo.auth.repository.UserRepository;
import com.ohgiraffers.dalryeo.mypage.dto.ProfileUpdateRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MypageServiceTest {

    @Mock
    private UserRepository userRepository;

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

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
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
        ReflectionTestUtils.setField(user, "profileImage", "https://cdn.example.com/original.jpg");
        ProfileUpdateRequest request = updateRequest(
                "runner2",
                "M",
                LocalDate.of(1997, 1, 3),
                178,
                70,
                null
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        mypageService.updateProfile(userId, request);

        assertThat(user.getProfileImage()).isNull();
        verify(userRepository).save(user);
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
