package com.ohgiraffers.dalryeo.tier.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "tier")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Tier {

    @Id
    @Column(name = "tier_code", length = 50, nullable = false)
    private String tierCode;

    @Column(name = "display_name", length = 50, nullable = false)
    private String displayName;

    @Column(name = "min_score", nullable = false)
    private Double minScore;

    @Column(name = "max_score", nullable = false)
    private Double maxScore;

    @Column(name = "default_profile_image", length = 500)
    private String defaultProfileImage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Tier(String tierCode, String displayName, Double minScore, Double maxScore, String defaultProfileImage) {
        this.tierCode = tierCode;
        this.displayName = displayName;
        this.minScore = minScore;
        this.maxScore = maxScore;
        this.defaultProfileImage = defaultProfileImage;
    }

    public void updateMetadata(String displayName, Double minScore, Double maxScore, String defaultProfileImage) {
        this.displayName = displayName;
        this.minScore = minScore;
        this.maxScore = maxScore;
        this.defaultProfileImage = defaultProfileImage;
    }
}
