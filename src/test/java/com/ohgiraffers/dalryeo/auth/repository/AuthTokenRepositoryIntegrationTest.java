package com.ohgiraffers.dalryeo.auth.repository;

import com.ohgiraffers.dalryeo.auth.entity.AuthToken;
import com.ohgiraffers.dalryeo.auth.entity.User;
import com.ohgiraffers.dalryeo.auth.entity.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class AuthTokenRepositoryIntegrationTest {

    @Autowired
    private AuthTokenRepository authTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanDatabase() {
        authTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void rotateRefreshTokenIfCurrent_updatesOnlyWhenOldHashStillMatches() {
        User user = userRepository.save(User.builder()
                .status(UserStatus.NORMAL)
                .build());
        LocalDateTime firstExpiry = LocalDateTime.of(2026, 4, 1, 0, 0);
        LocalDateTime secondExpiry = LocalDateTime.of(2026, 4, 2, 0, 0);
        authTokenRepository.save(AuthToken.builder()
                .userId(user.getId())
                .refreshTokenHash("old-hash")
                .expiresAt(firstExpiry)
                .build());

        int updated = authTokenRepository.rotateRefreshTokenIfCurrent(
                user.getId(),
                "old-hash",
                "new-hash",
                secondExpiry
        );
        int staleUpdated = authTokenRepository.rotateRefreshTokenIfCurrent(
                user.getId(),
                "old-hash",
                "stale-hash",
                LocalDateTime.of(2026, 4, 3, 0, 0)
        );

        AuthToken authToken = authTokenRepository.findByUserId(user.getId()).orElseThrow();
        assertThat(updated).isEqualTo(1);
        assertThat(staleUpdated).isZero();
        assertThat(authToken.getRefreshTokenHash()).isEqualTo("new-hash");
        assertThat(authToken.getExpiresAt()).isEqualTo(secondExpiry);
    }
}
