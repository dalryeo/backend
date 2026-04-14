package com.ohgiraffers.dalryeo.user.service;

import com.ohgiraffers.dalryeo.auth.entity.User;
import com.ohgiraffers.dalryeo.auth.entity.UserStatus;
import com.ohgiraffers.dalryeo.auth.repository.UserRepository;
import com.ohgiraffers.dalryeo.user.exception.UserErrorCode;
import com.ohgiraffers.dalryeo.user.exception.UserException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserLookupServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserLookupService userLookupService;

    @Test
    void getById_returnsUserWhenUserExists() {
        Long userId = 1L;
        User user = User.builder()
                .status(UserStatus.NORMAL)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        User result = userLookupService.getById(userId);

        assertThat(result).isSameAs(user);
    }

    @Test
    void getById_throwsWhenUserDoesNotExist() {
        Long userId = 2L;

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userLookupService.getById(userId))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.USER_NOT_FOUND);
    }

    @Test
    void getActiveById_throwsWhenUserIsWithdrawn() {
        Long userId = 3L;
        User user = User.builder()
                .status(UserStatus.WITHDRAWN)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userLookupService.getActiveById(userId))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.WITHDRAWN_USER);
    }

    @Test
    void validateNicknameAvailable_doesNothingWhenNicknameIsSameAsCurrentNickname() {
        userLookupService.validateNicknameAvailable("runner", "runner");
    }

    @Test
    void validateNicknameAvailable_throwsWhenNicknameAlreadyExists() {
        when(userRepository.existsByNickname("taken")).thenReturn(true);

        assertThatThrownBy(() -> userLookupService.validateNicknameAvailable("taken", "current"))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.DUPLICATED_NICKNAME);
    }
}
