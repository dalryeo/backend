package com.ohgiraffers.dalryeo.tier.repository;

import com.ohgiraffers.dalryeo.tier.entity.Tier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TierRepository extends JpaRepository<Tier, String> {
    Optional<Tier> findFirstByMinScoreLessThanEqualAndMaxScoreGreaterThanEqualOrderByMinScoreDesc(Double minScore, Double maxScore);
}
