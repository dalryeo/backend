package com.ohgiraffers.dalryeo.auth.repository;

import com.ohgiraffers.dalryeo.auth.entity.AuthToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {
    Optional<AuthToken> findByRefreshTokenHash(String refreshTokenHash);
    Optional<AuthToken> findByUserId(Long userId);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE AuthToken token
            SET token.refreshTokenHash = :newRefreshTokenHash,
                token.expiresAt = :expiresAt,
                token.revokedAt = null
            WHERE token.userId = :userId
              AND token.refreshTokenHash = :currentRefreshTokenHash
            """)
    int rotateRefreshTokenIfCurrent(
            @Param("userId") Long userId,
            @Param("currentRefreshTokenHash") String currentRefreshTokenHash,
            @Param("newRefreshTokenHash") String newRefreshTokenHash,
            @Param("expiresAt") LocalDateTime expiresAt
    );

    void deleteByUserId(Long userId);
}
