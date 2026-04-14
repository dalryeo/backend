package com.ohgiraffers.dalryeo.record.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "weekly_user_stats",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_weekly_user_stats_user_week",
                columnNames = {"user_id", "week_start_date"}
        ),
        indexes = {
                @Index(
                        name = "idx_weekly_user_stats_score_ranking",
                        columnList = "week_start_date, tier_score DESC, total_distance_km DESC, user_id"
                ),
                @Index(
                        name = "idx_weekly_user_stats_distance_ranking",
                        columnList = "week_start_date, total_distance_km DESC, tier_score DESC, user_id"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WeeklyUserStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "week_start_date", nullable = false)
    private LocalDate weekStartDate;

    @Column(name = "run_count", nullable = false)
    private Integer runCount;

    @Column(name = "total_distance_km", precision = 10, scale = 3, nullable = false)
    private BigDecimal totalDistanceKm;

    @Column(name = "total_duration_sec", nullable = false)
    private Integer totalDurationSec;

    @Column(name = "weighted_pace_sum", precision = 14, scale = 3, nullable = false)
    private BigDecimal weightedPaceSum;

    @Column(name = "avg_pace_sec_per_km", nullable = false)
    private Integer avgPaceSecPerKm;

    @Column(name = "tier_score_sum", precision = 10, scale = 2, nullable = false)
    private BigDecimal tierScoreSum;

    @Column(name = "tier_score", precision = 5, scale = 2, nullable = false)
    private BigDecimal tierScore;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public WeeklyUserStats(
            Long userId,
            LocalDate weekStartDate,
            Integer runCount,
            BigDecimal totalDistanceKm,
            Integer totalDurationSec,
            BigDecimal weightedPaceSum,
            Integer avgPaceSecPerKm,
            BigDecimal tierScoreSum,
            BigDecimal tierScore
    ) {
        this.userId = userId;
        this.weekStartDate = weekStartDate;
        this.runCount = runCount == null ? 0 : runCount;
        this.totalDistanceKm = totalDistanceKm == null ? BigDecimal.ZERO : totalDistanceKm;
        this.totalDurationSec = totalDurationSec == null ? 0 : totalDurationSec;
        this.weightedPaceSum = weightedPaceSum == null ? BigDecimal.ZERO : weightedPaceSum;
        this.avgPaceSecPerKm = avgPaceSecPerKm == null ? 0 : avgPaceSecPerKm;
        this.tierScoreSum = tierScoreSum == null ? BigDecimal.ZERO : tierScoreSum;
        this.tierScore = tierScore == null ? BigDecimal.ZERO : tierScore;
    }

    public boolean hasRecords() {
        return runCount != null && runCount > 0;
    }

    public double totalDistanceAsDouble() {
        return totalDistanceKm == null ? 0.0 : totalDistanceKm.doubleValue();
    }

    public double tierScoreAsDouble() {
        return tierScore == null ? 0.0 : tierScore.doubleValue();
    }
}
