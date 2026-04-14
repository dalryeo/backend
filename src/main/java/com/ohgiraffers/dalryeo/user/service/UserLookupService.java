package com.ohgiraffers.dalryeo.user.service;

import com.ohgiraffers.dalryeo.auth.entity.User;
import com.ohgiraffers.dalryeo.auth.repository.UserRepository;
import com.ohgiraffers.dalryeo.user.exception.UserErrorCode;
import com.ohgiraffers.dalryeo.user.exception.UserException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserLookupService {

    private final UserRepository userRepository;

    public User getById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
    }

    public User getActiveById(Long userId) {
        User user = getById(userId);
        if (user.isWithdrawn()) {
            throw new UserException(UserErrorCode.WITHDRAWN_USER);
        }
        return user;
    }

    public void validateNicknameAvailable(String newNickname, String currentNickname) {
        if (newNickname != null && !newNickname.equals(currentNickname) && userRepository.existsByNickname(newNickname)) {
            throw new UserException(UserErrorCode.DUPLICATED_NICKNAME);
        }
    }
}
