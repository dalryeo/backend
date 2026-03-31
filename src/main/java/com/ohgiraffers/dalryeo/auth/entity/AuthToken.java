package com.ohgiraffers.dalryeo.auth.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "auth_tokens",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_auth_tokens_user", columnNames = "user_id"),
                @UniqueConstraint(name = "uq_auth_tokens_hash", columnNames = "refresh_token_hash")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "refresh_token_hash", length = 255, nullable = false)
    private String refreshTokenHash;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime revokedAt;

    @Builder
    public AuthToken(Long userId, String refreshTokenHash, LocalDateTime expiresAt) {
        this.userId = userId;
        this.refreshTokenHash = refreshTokenHash;
        this.expiresAt = expiresAt;
    }

    public void rotate(String refreshTokenHash, LocalDateTime expiresAt) {
        this.refreshTokenHash = refreshTokenHash;
        this.expiresAt = expiresAt;
        this.revokedAt = null;
    }

    public boolean isExpired(LocalDateTime now) {
        return expiresAt.isBefore(now) || revokedAt != null;
    }
}
