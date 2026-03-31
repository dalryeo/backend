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

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private UserStatus status;

    @Column(length = 30, unique = true)
    private String nickname;

    @Column(length = 1)
    private String gender;

    private LocalDate birth;

    private Integer height;

    private Integer weight;

    @Column(length = 500)
    private String profileImage;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;

    @Builder
    public User(UserStatus status) {
        this.status = status == null ? UserStatus.NORMAL : status;
    }

    public boolean isWithdrawn() {
        return status == UserStatus.WITHDRAWN;
    }

    public void withdraw() {
        clearProfile();
        this.status = UserStatus.WITHDRAWN;
        this.deletedAt = LocalDateTime.now();
    }

    public void reactivate() {
        this.status = UserStatus.NORMAL;
        this.deletedAt = null;
    }

    private void clearProfile() {
        this.nickname = null;
        this.gender = null;
        this.birth = null;
        this.height = null;
        this.weight = null;
        this.profileImage = null;
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
