package com.ohgiraffers.dalryeo.tier.service;

import com.ohgiraffers.dalryeo.tier.entity.TierGrade;
import com.ohgiraffers.dalryeo.tier.repository.TierGradeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TierService {

    private final TierGradeRepository tierGradeRepository;

    public TierInfo resolveByScore(double score) {
        TierGrade tierGrade = tierGradeRepository
                .findFirstByMinScoreLessThanEqualAndMaxScoreGreaterThanEqualOrderByMinScoreDesc(score, score)
                .orElseThrow(() -> new IllegalStateException("점수에 해당하는 티어 메타데이터를 찾을 수 없습니다. score=" + score));

        return toTierInfo(tierGrade);
    }

    public TierInfo resolveByTierCodeAndScore(String tierCode, double score) {
        TierGrade tierGrade = tierGradeRepository
                .findFirstByTierCodeAndMinScoreLessThanEqualAndMaxScoreGreaterThanEqualOrderByMinScoreDesc(
                        tierCode,
                        score,
                        score
                )
                .orElseThrow(() -> new IllegalStateException(
                        "티어 코드와 점수에 해당하는 티어 메타데이터를 찾을 수 없습니다. tierCode=" + tierCode + ", score=" + score
                ));

        return toTierInfo(tierGrade);
    }

    public Optional<String> findDefaultProfileImageByTierCode(String tierCode) {
        return tierGradeRepository.findFirstByTierCodeOrderByMinScoreAsc(tierCode)
                .map(TierGrade::getDefaultProfileImage);
    }

    private TierInfo toTierInfo(TierGrade tierGrade) {
        return new TierInfo(
                tierGrade.getTierCode(),
                tierGrade.getDisplayName(),
                tierGrade.getGrade(),
                tierGrade.getDefaultProfileImage()
        );
    }

    public record TierInfo(String tierCode, String displayName, String tierGrade, String defaultProfileImage) {
    }
}
