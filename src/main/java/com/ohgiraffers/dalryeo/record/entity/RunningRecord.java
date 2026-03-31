package com.ohgiraffers.dalryeo.record.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "running_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RunningRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(name = "source", length = 20, nullable = false)
    private String platform;

    @Column(nullable = false)
    private Double distanceKm;

    @Column(nullable = false)
    private Integer durationSec;

    @Column(nullable = false)
    private Integer avgPaceSecPerKm;

    private Integer avgHeartRate;

    private Integer caloriesKcal;

    @Column(nullable = false)
    private LocalDateTime startAt;

    @Column(nullable = false)
    private LocalDateTime endAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public RunningRecord(Long userId, String platform, Double distanceKm, Integer durationSec,
                        Integer avgPaceSecPerKm, Integer avgHeartRate, Integer caloriesKcal,
                        LocalDateTime startAt, LocalDateTime endAt) {
        this.userId = userId;
        this.platform = platform;
        this.distanceKm = distanceKm;
        this.durationSec = durationSec;
        this.avgPaceSecPerKm = avgPaceSecPerKm;
        this.avgHeartRate = avgHeartRate;
        this.caloriesKcal = caloriesKcal;
        this.startAt = startAt;
        this.endAt = endAt;
    }
}
