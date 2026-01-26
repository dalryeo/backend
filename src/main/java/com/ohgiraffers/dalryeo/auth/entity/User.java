package com.ohgiraffers.dalryeo.auth.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String appleId;

    @Column(length = 500)
    private String refreshToken;

    @Column(nullable = false)
    private boolean isWithdrawn = false;

    @Column(unique = true)
    private String nickname;

    @Column(length = 1)
    private String gender;

    private LocalDate birth;

    private Integer height;

    private Integer weight;

    @Column(length = 500)
    private String profileImage;

    @Column(length = 20)
    private String currentTier;

    @Column(length = 1)
    private String currentTierGrade;

    private Double tierScore;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public User(String appleId) {
        this.appleId = appleId;
        this.isWithdrawn = false;
    }

    public void updateRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public void withdraw() {
        this.isWithdrawn = true;
        this.refreshToken = null;
    }

    public void reactivate() {
        this.isWithdrawn = false;
        this.refreshToken = null;
        this.nickname = null;
        this.gender = null;
        this.birth = null;
        this.height = null;
        this.weight = null;
        this.profileImage = null;
        this.currentTier = null;
        this.currentTierGrade = null;
        this.tierScore = null;
    }

    public void clearRefreshToken() {
        this.refreshToken = null;
    }

    public void updateOnboarding(String nickname, String gender, LocalDate birth, Integer height, Integer weight, String profileImage) {
        this.nickname = nickname;
        this.gender = gender;
        this.birth = birth;
        this.height = height;
        this.weight = weight;
        this.profileImage = profileImage;
    }

    public void updateProfile(String nickname, String gender, LocalDate birth, Integer height, Integer weight) {
        this.nickname = nickname;
        this.gender = gender;
        this.birth = birth;
        this.height = height;
        this.weight = weight;
    }
}

