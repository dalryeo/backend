package com.ohgiraffers.dalryeo.tier.service;

import com.ohgiraffers.dalryeo.tier.entity.TierGrade;
import com.ohgiraffers.dalryeo.tier.repository.TierGradeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TierMetadataCacheTest {

    @Mock
    private TierGradeRepository tierGradeRepository;

    @Test
    void reload_loadsTierGradeMetadataAndResolvesByScore() {
        TierMetadataCache cache = new TierMetadataCache(tierGradeRepository);
        when(tierGradeRepository.findAllByOrderByMinScoreDesc())
                .thenReturn(List.of(
                        tierGrade("DEER", "사슴", "B", 1.20, 1.28, "/profiles/tiers/deer.png"),
                        tierGrade("TURTLE", "거북이", "B", 0.00, 0.45, "/profiles/tiers/turtle.png")
                ));

        cache.reload();

        TierService.TierInfo result = cache.resolveByScore(1.24);
        assertThat(result.tierCode()).isEqualTo("DEER");
        assertThat(result.displayName()).isEqualTo("사슴");
        assertThat(result.tierGrade()).isEqualTo("B");
        assertThat(result.defaultProfileImage()).isEqualTo("/profiles/tiers/deer.png");
    }

    @Test
    void resolveByTierCodeAndScore_matchesTierCodeAndScoreRange() {
        TierMetadataCache cache = new TierMetadataCache(tierGradeRepository);
        when(tierGradeRepository.findAllByOrderByMinScoreDesc())
                .thenReturn(List.of(
                        tierGrade("DUCK", "오리", "B", 0.46, 0.48, "/profiles/tiers/duck.png"),
                        tierGrade("TURTLE", "거북이", "B", 0.00, 0.45, "/profiles/tiers/turtle.png")
                ));

        cache.reload();

        TierService.TierInfo result = cache.resolveByTierCodeAndScore("TURTLE", 0.30);
        assertThat(result.tierCode()).isEqualTo("TURTLE");
        assertThat(result.displayName()).isEqualTo("거북이");
        assertThat(result.tierGrade()).isEqualTo("B");
        assertThat(result.defaultProfileImage()).isEqualTo("/profiles/tiers/turtle.png");
    }

    @Test
    void resolveByTierCodeAndScore_fallsBackToLowestGradeWhenTierCodeExistsButScoreRangeDoesNotMatch() {
        TierMetadataCache cache = new TierMetadataCache(tierGradeRepository);
        when(tierGradeRepository.findAllByOrderByMinScoreDesc())
                .thenReturn(List.of(
                        tierGrade("FOX", "여우", "G", 0.95, 0.99, "/profiles/tiers/fox-g.png"),
                        tierGrade("FOX", "여우", "S", 0.90, 0.94, "/profiles/tiers/fox-s.png"),
                        tierGrade("FOX", "여우", "B", 0.86, 0.89, "/profiles/tiers/fox-b.png")
                ));

        cache.reload();

        TierService.TierInfo result = cache.resolveByTierCodeAndScore("FOX", 0.85);

        assertThat(result.tierCode()).isEqualTo("FOX");
        assertThat(result.displayName()).isEqualTo("여우");
        assertThat(result.tierGrade()).isEqualTo("B");
        assertThat(result.defaultProfileImage()).isEqualTo("/profiles/tiers/fox-b.png");
    }

    @Test
    void findDefaultProfileImageByTierCode_returnsLowestMinScoreImage() {
        TierMetadataCache cache = new TierMetadataCache(tierGradeRepository);
        when(tierGradeRepository.findAllByOrderByMinScoreDesc())
                .thenReturn(List.of(
                        tierGrade("DEER", "사슴", "G", 1.39, 1.49, "/profiles/tiers/deer-g.png"),
                        tierGrade("DEER", "사슴", "B", 1.20, 1.28, "/profiles/tiers/deer-b.png")
                ));

        cache.reload();

        Optional<String> result = cache.findDefaultProfileImageByTierCode("DEER");
        assertThat(result).contains("/profiles/tiers/deer-b.png");
    }

    @Test
    void reload_replacesExistingCacheAtomically() {
        TierMetadataCache cache = new TierMetadataCache(tierGradeRepository);
        when(tierGradeRepository.findAllByOrderByMinScoreDesc())
                .thenReturn(List.of(tierGrade("TURTLE", "거북이", "B", 0.00, 0.45, "/profiles/tiers/old.png")))
                .thenReturn(List.of(tierGrade("TURTLE", "거북이", "B", 0.00, 0.45, "/profiles/tiers/new.png")));

        cache.reload();
        assertThat(cache.resolveByScore(0.30).defaultProfileImage()).isEqualTo("/profiles/tiers/old.png");

        cache.reload();
        assertThat(cache.resolveByScore(0.30).defaultProfileImage()).isEqualTo("/profiles/tiers/new.png");
    }

    @Test
    void resolveByScore_throwsWhenScoreRangeDoesNotExist() {
        TierMetadataCache cache = new TierMetadataCache(tierGradeRepository);
        when(tierGradeRepository.findAllByOrderByMinScoreDesc())
                .thenReturn(List.of(tierGrade("DEER", "사슴", "B", 1.20, 1.28, "/profiles/tiers/deer.png")));
        cache.reload();

        assertThatThrownBy(() -> cache.resolveByScore(0.30))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("score=0.3");
    }

    @Test
    void reload_throwsWhenRepositoryReturnsNoMetadata() {
        TierMetadataCache cache = new TierMetadataCache(tierGradeRepository);
        when(tierGradeRepository.findAllByOrderByMinScoreDesc()).thenReturn(List.of());

        assertThatThrownBy(cache::reload)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("티어 메타데이터가 비어 있습니다");
    }

    private TierGrade tierGrade(
            String tierCode,
            String displayName,
            String grade,
            double minScore,
            double maxScore,
            String defaultProfileImage
    ) {
        return TierGrade.builder()
                .tierCode(tierCode)
                .displayName(displayName)
                .grade(grade)
                .minScore(minScore)
                .maxScore(maxScore)
                .defaultProfileImage(defaultProfileImage)
                .build();
    }
}
