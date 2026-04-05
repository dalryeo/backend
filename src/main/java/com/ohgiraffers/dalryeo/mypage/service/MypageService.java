package com.ohgiraffers.dalryeo.mypage.service;

import com.ohgiraffers.dalryeo.auth.entity.User;
import com.ohgiraffers.dalryeo.auth.repository.UserRepository;
import com.ohgiraffers.dalryeo.mypage.dto.ProfileUpdateRequest;
import com.ohgiraffers.dalryeo.onboarding.service.ProfileImageStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional
public class MypageService {

    private final UserRepository userRepository;
    private final ProfileImageStorageService profileImageStorageService;

    public void updateProfile(Long userId, ProfileUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

        String nickname = request.getNickname();
        if (nickname != null && !nickname.equals(user.getNickname()) && userRepository.existsByNickname(nickname)) {
            throw new RuntimeException("이미 사용 중인 닉네임입니다.");
        }

        String previousProfileImage = user.getProfileImage();
        String newProfileImage = normalizeProfileImage(request.getProfileImage());

        user.updateProfile(
                request.getNickname(),
                request.getGender(),
                request.getBirth(),
                request.getHeight(),
                request.getWeight(),
                newProfileImage
        );
        userRepository.save(user);

        if (!Objects.equals(previousProfileImage, newProfileImage)) {
            profileImageStorageService.deleteStoredProfileImage(previousProfileImage);
        }
    }

    private String normalizeProfileImage(String profileImage) {
        if (profileImage == null || profileImage.isBlank()) {
            return null;
        }
        return profileImage;
    }
}
