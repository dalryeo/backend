package com.ohgiraffers.dalryeo.tier.service;

import com.ohgiraffers.dalryeo.tier.entity.TierGrade;
import com.ohgiraffers.dalryeo.tier.repository.TierGradeRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Component
@RequiredArgsConstructor
public class TierMetadataCache {

    private final TierGradeRepository tierGradeRepository;
    private final AtomicReference<List<TierMetadata>> cache = new AtomicReference<>(List.of());

    @PostConstruct
    public void initialize() {
        reload();
    }

    public void reload() {
        List<TierMetadata> loaded = tierGradeRepository.findAllByOrderByMinScoreDesc().stream()
                .map(TierMetadata::from)
                .toList();

        if (loaded.isEmpty()) {
            throw new IllegalStateException("티어 메타데이터가 비어 있습니다.");
        }

        cache.set(List.copyOf(loaded));
    }

    public TierService.TierInfo resolveByScore(double score) {
        return cache.get().stream()
                .filter(metadata -> metadata.containsScore(score))
                .findFirst()
                .map(TierMetadata::toTierInfo)
                .orElseThrow(() -> new IllegalStateException("점수에 해당하는 티어 메타데이터를 찾을 수 없습니다. score=" + score));
    }

    public TierService.TierInfo resolveByTierCodeAndScore(String tierCode, double score) {
        return cache.get().stream()
                .filter(metadata -> metadata.matchesTierCode(tierCode))
                .filter(metadata -> metadata.containsScore(score))
                .findFirst()
                .map(TierMetadata::toTierInfo)
                .orElseThrow(() -> new IllegalStateException(
                        "티어 코드와 점수에 해당하는 티어 메타데이터를 찾을 수 없습니다. tierCode=" + tierCode + ", score=" + score
                ));
    }

    public Optional<String> findDefaultProfileImageByTierCode(String tierCode) {
        return cache.get().stream()
                .filter(metadata -> metadata.matchesTierCode(tierCode))
                .min(Comparator.comparing(TierMetadata::minScore))
                .map(TierMetadata::defaultProfileImage);
    }

    private record TierMetadata(
            String tierCode,
            String displayName,
            String grade,
            double minScore,
            double maxScore,
            String defaultProfileImage
    ) {
        private static TierMetadata from(TierGrade tierGrade) {
            return new TierMetadata(
                    tierGrade.getTierCode(),
                    tierGrade.getDisplayName(),
                    tierGrade.getGrade(),
                    tierGrade.getMinScore(),
                    tierGrade.getMaxScore(),
                    tierGrade.getDefaultProfileImage()
            );
        }

        private boolean matchesTierCode(String requestedTierCode) {
            return tierCode.equals(requestedTierCode);
        }

        private boolean containsScore(double score) {
            return minScore <= score && score <= maxScore;
        }

        private TierService.TierInfo toTierInfo() {
            return new TierService.TierInfo(tierCode, displayName, grade, defaultProfileImage);
        }
    }
}
