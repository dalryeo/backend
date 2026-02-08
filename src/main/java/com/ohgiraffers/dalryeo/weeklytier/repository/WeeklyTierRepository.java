package com.ohgiraffers.dalryeo.weeklytier.repository;

import com.ohgiraffers.dalryeo.weeklytier.entity.WeeklyTier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface WeeklyTierRepository extends JpaRepository<WeeklyTier, Long> {
    Optional<WeeklyTier> findByUserIdAndWeekStartDate(Long userId, LocalDate weekStartDate);
}
