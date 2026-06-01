package com.ohgiraffers.dalryeo.weeklytier.repository;

import com.ohgiraffers.dalryeo.weeklytier.entity.WeeklyTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface WeeklyTierRepository extends JpaRepository<WeeklyTier, Long> {
    Optional<WeeklyTier> findByUserIdAndWeekStartDate(Long userId, LocalDate weekStartDate);

    @Modifying
    @Query(value = """
            INSERT INTO weekly_tiers (
                user_id,
                week_start_date,
                tier_code,
                tier_score,
                created_at
            )
            VALUES (
                :userId,
                :weekStartDate,
                :tierCode,
                :tierScore,
                CURRENT_TIMESTAMP
            )
            ON CONFLICT (user_id, week_start_date)
            DO UPDATE SET
                tier_code = EXCLUDED.tier_code,
                tier_score = EXCLUDED.tier_score
            WHERE weekly_tiers.tier_code IS DISTINCT FROM EXCLUDED.tier_code
               OR weekly_tiers.tier_score IS DISTINCT FROM EXCLUDED.tier_score
            """, nativeQuery = true)
    int upsertFinalizedTier(
            @Param("userId") Long userId,
            @Param("weekStartDate") LocalDate weekStartDate,
            @Param("tierCode") String tierCode,
            @Param("tierScore") Integer tierScore
    );

    void deleteByUserId(Long userId);
}
