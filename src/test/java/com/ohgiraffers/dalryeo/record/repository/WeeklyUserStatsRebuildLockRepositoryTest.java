package com.ohgiraffers.dalryeo.record.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class WeeklyUserStatsRebuildLockRepositoryTest {

    @Test
    @DisplayName("같은 사용자와 같은 주차의 advisory lock key는 항상 같은 값이다")
    void lockKeyIsStableForSameUserWeek() {
        long lockKey = WeeklyUserStatsRebuildLockRepository.lockKey(1L, LocalDate.of(2026, 6, 22));

        assertThat(lockKey).isEqualTo(-5682160895883215893L);
    }

    @Test
    @DisplayName("사용자나 주차가 다르면 advisory lock key도 달라진다")
    void lockKeyDiffersByUserAndWeek() {
        long baseKey = WeeklyUserStatsRebuildLockRepository.lockKey(1L, LocalDate.of(2026, 6, 22));
        long otherUserKey = WeeklyUserStatsRebuildLockRepository.lockKey(2L, LocalDate.of(2026, 6, 22));
        long otherWeekKey = WeeklyUserStatsRebuildLockRepository.lockKey(1L, LocalDate.of(2026, 6, 29));

        assertThat(otherUserKey).isNotEqualTo(baseKey);
        assertThat(otherWeekKey).isNotEqualTo(baseKey);
    }

    @Test
    @DisplayName("advisory lock key 계산에는 사용자와 주차가 모두 필요하다")
    void lockKeyRejectsNullInputs() {
        assertThatNullPointerException()
                .isThrownBy(() -> WeeklyUserStatsRebuildLockRepository.lockKey(null, LocalDate.of(2026, 6, 22)));
        assertThatNullPointerException()
                .isThrownBy(() -> WeeklyUserStatsRebuildLockRepository.lockKey(1L, null));
    }

    @Test
    @DisplayName("transaction advisory lock은 활성 트랜잭션 안에서만 획득할 수 있다")
    void lockUserWeekRequiresActiveTransaction() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        WeeklyUserStatsRebuildLockRepository repository = new WeeklyUserStatsRebuildLockRepository(jdbcTemplate);

        assertThatIllegalStateException()
                .isThrownBy(() -> repository.lockUserWeek(1L, LocalDate.of(2026, 6, 22)))
                .withMessageContaining("transaction");
        verifyNoInteractions(jdbcTemplate);
    }
}
