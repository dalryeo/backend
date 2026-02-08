package com.ohgiraffers.dalryeo.weeklytier.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "weekly_tiers",
        uniqueConstraints = @UniqueConstraint(name = "uq_user_week", columnNames = {"user_id", "week_start_date"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WeeklyTier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "week_start_date", nullable = false)
    private LocalDate weekStartDate;

    @Column(name = "tier_code", length = 50, nullable = false)
    private String tierCode;

    @Column(name = "tier_score", nullable = false)
    private Integer tierScore;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public WeeklyTier(Long userId, LocalDate weekStartDate, String tierCode, Integer tierScore) {
        this.userId = userId;
        this.weekStartDate = weekStartDate;
        this.tierCode = tierCode;
        this.tierScore = tierScore;
    }

    public void updateTier(String tierCode, Integer tierScore) {
        this.tierCode = tierCode;
        this.tierScore = tierScore;
    }
}
