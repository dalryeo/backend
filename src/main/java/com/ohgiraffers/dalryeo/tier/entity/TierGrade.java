package com.ohgiraffers.dalryeo.tier.entity;

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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "tier_grade",
        uniqueConstraints = @UniqueConstraint(name = "uq_tier_grade_tier_grade", columnNames = {"tier_code", "grade"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TierGrade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tier_code", length = 50, nullable = false)
    private String tierCode;

    @Column(length = 1, nullable = false)
    private String grade;

    @Column(name = "min_score", nullable = false)
    private Double minScore;

    @Column(name = "max_score", nullable = false)
    private Double maxScore;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public TierGrade(String tierCode, String grade, Double minScore, Double maxScore) {
        this.tierCode = tierCode;
        this.grade = grade;
        this.minScore = minScore;
        this.maxScore = maxScore;
    }

    public void updateRange(Double minScore, Double maxScore) {
        this.minScore = minScore;
        this.maxScore = maxScore;
    }
}
