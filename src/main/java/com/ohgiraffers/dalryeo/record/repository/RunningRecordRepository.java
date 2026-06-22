package com.ohgiraffers.dalryeo.record.repository;

import com.ohgiraffers.dalryeo.record.entity.RunningRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RunningRecordRepository extends JpaRepository<RunningRecord, Long> {

    interface UserWeekAggregate {
        Integer getRunCount();
        BigDecimal getTotalDistanceKm();
        Integer getTotalDurationSec();
        BigDecimal getWeightedPaceSum();
        BigDecimal getTierScoreSum();
    }
    
    Page<RunningRecord> findByUserIdOrderByStartAtDesc(Long userId, Pageable pageable);
    
    @Query("SELECT r FROM RunningRecord r WHERE r.userId = :userId " +
           "AND r.startAt >= :startDate AND r.startAt < :endDate " +
           "ORDER BY r.startAt DESC")
    Page<RunningRecord> findByUserIdAndDateRange(
            @Param("userId") Long userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );
    
    Optional<RunningRecord> findByIdAndUserId(Long id, Long userId);
    
    @Query("SELECT r FROM RunningRecord r WHERE r.userId = :userId " +
           "AND r.startAt >= :startDate AND r.startAt < :endDate " +
           "ORDER BY r.startAt DESC")
    List<RunningRecord> findByUserIdAndWeekRange(
            @Param("userId") Long userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    @Query(value = """
            SELECT
                CAST(COUNT(*) AS INTEGER) AS "runCount",
                CAST(COALESCE(ROUND(SUM(ROUND(CAST(distance_km AS numeric), 3)), 3), 0) AS numeric(10, 3))
                    AS "totalDistanceKm",
                CAST(COALESCE(SUM(duration_sec), 0) AS INTEGER) AS "totalDurationSec",
                CAST(COALESCE(ROUND(SUM(ROUND(CAST(distance_km AS numeric), 3) * avg_pace_sec_per_km), 3), 0)
                    AS numeric(14, 3)) AS "weightedPaceSum",
                CAST(COALESCE(ROUND(SUM(
                    ROUND(
                        ROUND(
                            6.00 / NULLIF(ROUND(CAST(avg_pace_sec_per_km AS numeric) / 60.0, 2), 0),
                            2
                        )
                        * CASE
                            WHEN distance_km < 1.00 THEN 0.50
                            WHEN distance_km < 2.00 THEN 0.60
                            WHEN distance_km < 3.00 THEN 0.70
                            WHEN distance_km < 5.00 THEN 1.00
                            WHEN distance_km < 7.00 THEN 1.03
                            WHEN distance_km < 9.00 THEN 1.05
                            WHEN distance_km < 11.00 THEN 1.06
                            WHEN distance_km < 15.00 THEN 1.07
                            WHEN distance_km < 25.00 THEN 1.08
                            WHEN distance_km < 40.00 THEN 1.09
                            ELSE 1.10
                        END,
                        2
                    )
                ), 2), 0) AS numeric(10, 2)) AS "tierScoreSum"
            FROM running_records
            WHERE user_id = :userId
              AND start_at >= :startDate
              AND start_at < :endDate
            """, nativeQuery = true)
    UserWeekAggregate aggregateUserWeek(
            @Param("userId") Long userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    @Query("SELECT COUNT(r) > 0 FROM RunningRecord r WHERE r.userId = :userId " +
           "AND r.startAt >= :startDate AND r.startAt < :endDate")
    boolean existsByUserIdAndWeekRange(
            @Param("userId") Long userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    @Query("SELECT r FROM RunningRecord r WHERE r.userId = :userId " +
           "AND DATE(r.startAt) = :date " +
           "ORDER BY r.startAt DESC")
    List<RunningRecord> findByUserIdAndDate(
            @Param("userId") Long userId,
            @Param("date") LocalDate date
    );

    @Query("SELECT r FROM RunningRecord r WHERE r.startAt >= :startDate AND r.startAt < :endDate " +
           "ORDER BY r.startAt DESC")
    List<RunningRecord> findByWeekRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    void deleteByUserId(Long userId);
}
