package com.ohgiraffers.dalryeo.tier.repository;

import com.ohgiraffers.dalryeo.tier.entity.TierGrade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TierGradeRepository extends JpaRepository<TierGrade, Long> {
    Optional<TierGrade> findByTierCodeAndGrade(String tierCode, String grade);

    Optional<TierGrade> findFirstByTierCodeAndMinScoreLessThanEqualAndMaxScoreGreaterThanEqualOrderByMinScoreDesc(
            String tierCode,
            Double minScore,
            Double maxScore
    );
}
