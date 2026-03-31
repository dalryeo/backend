package com.ohgiraffers.dalryeo.tier.service;

import com.ohgiraffers.dalryeo.tier.entity.Tier;
import com.ohgiraffers.dalryeo.tier.entity.TierGrade;
import com.ohgiraffers.dalryeo.tier.repository.TierGradeRepository;
import com.ohgiraffers.dalryeo.tier.repository.TierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TierService {

    private final TierRepository tierRepository;
    private final TierGradeRepository tierGradeRepository;

    public TierInfo resolveByScore(double score) {
        Tier tier = tierRepository.findFirstByMinScoreLessThanEqualAndMaxScoreGreaterThanEqualOrderByMinScoreDesc(score, score)
                .orElseThrow(() -> new IllegalStateException("점수에 해당하는 티어 메타데이터를 찾을 수 없습니다. score=" + score));

        String grade = tierGradeRepository
                .findFirstByTierCodeAndMinScoreLessThanEqualAndMaxScoreGreaterThanEqualOrderByMinScoreDesc(
                        tier.getTierCode(),
                        score,
                        score
                )
                .map(TierGrade::getGrade)
                .orElse(null);

        return new TierInfo(tier.getTierCode(), tier.getDisplayName(), grade);
    }

    public TierInfo resolveByTierCodeAndScore(String tierCode, double score) {
        Tier tier = tierRepository.findById(tierCode)
                .orElseThrow(() -> new IllegalStateException("티어 코드를 찾을 수 없습니다. tierCode=" + tierCode));

        String grade = tierGradeRepository
                .findFirstByTierCodeAndMinScoreLessThanEqualAndMaxScoreGreaterThanEqualOrderByMinScoreDesc(
                        tierCode,
                        score,
                        score
                )
                .map(TierGrade::getGrade)
                .orElse(null);

        return new TierInfo(tier.getTierCode(), tier.getDisplayName(), grade);
    }

    public record TierInfo(String tierCode, String displayName, String tierGrade) {
    }
}
