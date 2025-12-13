package com.ohgiraffers.dalryeo.auth.repository;

import com.ohgiraffers.dalryeo.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByAppleId(String appleId);
    Optional<User> findByRefreshToken(String refreshToken);
    boolean existsByNickname(String nickname);
}

