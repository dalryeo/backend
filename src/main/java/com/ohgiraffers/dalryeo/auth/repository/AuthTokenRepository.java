package com.ohgiraffers.dalryeo.auth.repository;

import com.ohgiraffers.dalryeo.auth.entity.AuthToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {
    Optional<AuthToken> findByRefreshTokenHash(String refreshTokenHash);
    Optional<AuthToken> findByUserId(Long userId);
    void deleteByUserId(Long userId);
}
