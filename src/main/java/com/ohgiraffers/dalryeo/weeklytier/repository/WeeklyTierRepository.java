package com.ohgiraffers.dalryeo.weeklytier.repository;

import com.ohgiraffers.dalryeo.weeklytier.entity.WeeklyTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WeeklyTierRepository extends JpaRepository<WeeklyTier, Long> {
    interface CurrentTierSnapshot {
        Long getUserId();

        String getTierCode();

        Integer getTierScore();
    }

    Optional<WeeklyTier> findTopByUserIdAndWeekStartDateLessThanEqualOrderByWeekStartDateDesc(
            Long userId,
            LocalDate weekStartDate
    );

    @Query(value = """
            SELECT
                ranked.user_id AS "userId",
                ranked.tier_code AS "tierCode",
                ranked.tier_score AS "tierScore"
            FROM (
                SELECT
                    wt.user_id,
                    wt.tier_code,
                    wt.tier_score,
                    ROW_NUMBER() OVER (
                        PARTITION BY wt.user_id
                        ORDER BY wt.week_start_date DESC
                    ) AS row_number
                FROM weekly_tiers wt
                WHERE wt.user_id IN (:userIds)
                  AND wt.week_start_date <= :weekStartDate
            ) ranked
            WHERE ranked.row_number = 1
            """, nativeQuery = true)
    List<CurrentTierSnapshot> findLatestSnapshotsByUserIdsAndWeekStartDateLessThanEqual(
            @Param("userIds") Collection<Long> userIds,
            @Param("weekStartDate") LocalDate weekStartDate
    );

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
