package com.ohgiraffers.dalryeo.record.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Objects;

@Repository
@RequiredArgsConstructor
public class WeeklyUserStatsRebuildLockRepository {

    private static final String LOCK_PREFIX = "weekly_user_stats_rebuild:";
    private static final long FNV_64_OFFSET_BASIS = -3750763034362895579L;
    private static final long FNV_64_PRIME = 1099511628211L;

    private final JdbcTemplate jdbcTemplate;

    public void lockUserWeek(Long userId, LocalDate weekStartDate) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Weekly user stats rebuild advisory lock requires an active transaction.");
        }

        long lockKey = lockKey(userId, weekStartDate);
        jdbcTemplate.execute(
                "SELECT pg_advisory_xact_lock(?)",
                new PreparedStatementCallback<Void>() {
                    @Override
                    public Void doInPreparedStatement(PreparedStatement ps) throws SQLException, DataAccessException {
                        ps.setLong(1, lockKey);
                        ps.execute();
                        return null;
                    }
                }
        );
    }

    static long lockKey(Long userId, LocalDate weekStartDate) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(weekStartDate, "weekStartDate must not be null");

        return fnv1a64(LOCK_PREFIX + userId + ":" + weekStartDate.toEpochDay());
    }

    private static long fnv1a64(String value) {
        long hash = FNV_64_OFFSET_BASIS;
        for (byte current : value.getBytes(StandardCharsets.UTF_8)) {
            hash ^= Byte.toUnsignedInt(current);
            hash *= FNV_64_PRIME;
        }
        return hash;
    }
}
