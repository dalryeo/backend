package com.ohgiraffers.dalryeo.record.repository;

import com.ohgiraffers.dalryeo.record.entity.WeeklyUserStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WeeklyUserStatsRepository extends JpaRepository<WeeklyUserStats, Long> {

    Optional<WeeklyUserStats> findByUserIdAndWeekStartDate(Long userId, LocalDate weekStartDate);

    List<WeeklyUserStats> findByUserIdAndWeekStartDateBetweenOrderByWeekStartDateAsc(
            Long userId,
            LocalDate startWeek,
            LocalDate endWeek
    );

    @Modifying
    @Query(value = """
            INSERT INTO weekly_user_stats (
                user_id,
                week_start_date,
                run_count,
                total_distance_km,
                total_duration_sec,
                weighted_pace_sum,
                avg_pace_sec_per_km,
                tier_score_sum,
                tier_score,
                created_at,
                updated_at
            )
            VALUES (
                :userId,
                :weekStartDate,
                1,
                :distanceKm,
                :durationSec,
                :weightedPaceSum,
                :avgPaceSecPerKm,
                :tierScore,
                :tierScore,
                CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP
            )
            ON CONFLICT (user_id, week_start_date)
            DO UPDATE SET
                run_count = weekly_user_stats.run_count + EXCLUDED.run_count,
                total_distance_km = weekly_user_stats.total_distance_km + EXCLUDED.total_distance_km,
                total_duration_sec = weekly_user_stats.total_duration_sec + EXCLUDED.total_duration_sec,
                weighted_pace_sum = weekly_user_stats.weighted_pace_sum + EXCLUDED.weighted_pace_sum,
                tier_score_sum = weekly_user_stats.tier_score_sum + EXCLUDED.tier_score_sum,
                avg_pace_sec_per_km = CAST(ROUND(
                    (weekly_user_stats.weighted_pace_sum + EXCLUDED.weighted_pace_sum)
                    / NULLIF(weekly_user_stats.total_distance_km + EXCLUDED.total_distance_km, 0)
                ) AS INTEGER),
                tier_score = ROUND(
                    (weekly_user_stats.tier_score_sum + EXCLUDED.tier_score_sum)
                    / NULLIF(weekly_user_stats.run_count + EXCLUDED.run_count, 0),
                    2
                ),
                updated_at = CURRENT_TIMESTAMP
            """, nativeQuery = true)
    void upsertRecordDelta(
            @Param("userId") Long userId,
            @Param("weekStartDate") LocalDate weekStartDate,
            @Param("distanceKm") BigDecimal distanceKm,
            @Param("durationSec") Integer durationSec,
            @Param("weightedPaceSum") BigDecimal weightedPaceSum,
            @Param("avgPaceSecPerKm") Integer avgPaceSecPerKm,
            @Param("tierScore") BigDecimal tierScore
    );

    @Query(value = """
            SELECT s.*
            FROM weekly_user_stats s
            JOIN users u ON u.id = s.user_id
            WHERE s.week_start_date = :weekStartDate
              AND s.run_count > 0
              AND u.status <> 'WITHDRAWN'
              AND u.nickname IS NOT NULL
            ORDER BY s.tier_score DESC, s.total_distance_km DESC, s.user_id ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<WeeklyUserStats> findScoreRankingRows(
            @Param("weekStartDate") LocalDate weekStartDate,
            @Param("limit") int limit
    );

    @Query(value = """
            SELECT s.*
            FROM weekly_user_stats s
            JOIN users u ON u.id = s.user_id
            WHERE s.week_start_date = :weekStartDate
              AND s.run_count > 0
              AND u.status <> 'WITHDRAWN'
              AND u.nickname IS NOT NULL
            ORDER BY s.total_distance_km DESC, s.tier_score DESC, s.user_id ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<WeeklyUserStats> findDistanceRankingRows(
            @Param("weekStartDate") LocalDate weekStartDate,
            @Param("limit") int limit
    );

    @Query(value = """
            SELECT COUNT(*)
            FROM weekly_user_stats s
            JOIN users u ON u.id = s.user_id
            WHERE s.week_start_date = :weekStartDate
              AND s.run_count > 0
              AND u.status <> 'WITHDRAWN'
              AND u.nickname IS NOT NULL
              AND (
                  s.tier_score > :tierScore
                  OR (s.tier_score = :tierScore AND s.total_distance_km > :totalDistanceKm)
                  OR (
                      s.tier_score = :tierScore
                      AND s.total_distance_km = :totalDistanceKm
                      AND s.user_id < :userId
                  )
              )
            """, nativeQuery = true)
    long countAheadForScoreRank(
            @Param("weekStartDate") LocalDate weekStartDate,
            @Param("tierScore") BigDecimal tierScore,
            @Param("totalDistanceKm") BigDecimal totalDistanceKm,
            @Param("userId") Long userId
    );

    @Query(value = """
            SELECT COUNT(*)
            FROM weekly_user_stats s
            JOIN users u ON u.id = s.user_id
            WHERE s.week_start_date = :weekStartDate
              AND s.run_count > 0
              AND u.status <> 'WITHDRAWN'
              AND u.nickname IS NOT NULL
              AND (
                  s.total_distance_km > :totalDistanceKm
                  OR (s.total_distance_km = :totalDistanceKm AND s.tier_score > :tierScore)
                  OR (
                      s.total_distance_km = :totalDistanceKm
                      AND s.tier_score = :tierScore
                      AND s.user_id < :userId
                  )
              )
            """, nativeQuery = true)
    long countAheadForDistanceRank(
            @Param("weekStartDate") LocalDate weekStartDate,
            @Param("totalDistanceKm") BigDecimal totalDistanceKm,
            @Param("tierScore") BigDecimal tierScore,
            @Param("userId") Long userId
    );

    void deleteByUserId(Long userId);
}
