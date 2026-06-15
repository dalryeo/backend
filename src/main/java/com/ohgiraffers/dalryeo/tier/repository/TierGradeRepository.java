package com.ohgiraffers.dalryeo.tier.repository;

import com.ohgiraffers.dalryeo.tier.entity.TierGrade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TierGradeRepository extends JpaRepository<TierGrade, Long> {
    List<TierGrade> findAllByOrderByMinScoreDesc();
}
