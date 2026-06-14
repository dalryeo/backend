package com.ohgiraffers.dalryeo.tier.service;

import com.ohgiraffers.dalryeo.tier.entity.TierGrade;
import com.ohgiraffers.dalryeo.tier.repository.TierGradeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TierServiceTest {

    @Mock
    private TierGradeRepository tierGradeRepository;

    @InjectMocks
    private TierService tierService;

    @Test
    void resolveByScore_returnsTierCodeDisplayNameGradeAndDefaultProfileImage() {
        TierGrade deerBronze = tierGrade(
                "DEER",
                "사슴",
                "B",
                1.20,
                1.28,
                "/profiles/tiers/deer.png"
        );

        when(tierGradeRepository.findFirstByMinScoreLessThanEqualAndMaxScoreGreaterThanEqualOrderByMinScoreDesc(
                1.24,
                1.24
        )).thenReturn(Optional.of(deerBronze));

        TierService.TierInfo result = tierService.resolveByScore(1.24);

        assertThat(result.tierCode()).isEqualTo("DEER");
        assertThat(result.displayName()).isEqualTo("사슴");
        assertThat(result.tierGrade()).isEqualTo("B");
        assertThat(result.defaultProfileImage()).isEqualTo("/profiles/tiers/deer.png");
    }

    @Test
    void resolveByTierCodeAndScore_returnsTurtleBronzeMetadata() {
        TierGrade turtleBronze = tierGrade(
                "TURTLE",
                "거북이",
                "B",
                0.00,
                0.45,
                "/profiles/tiers/turtle.png"
        );

        when(tierGradeRepository.findFirstByTierCodeAndMinScoreLessThanEqualAndMaxScoreGreaterThanEqualOrderByMinScoreDesc(
                "TURTLE",
                0.30,
                0.30
        )).thenReturn(Optional.of(turtleBronze));

        TierService.TierInfo result = tierService.resolveByTierCodeAndScore("TURTLE", 0.30);

        assertThat(result.tierCode()).isEqualTo("TURTLE");
        assertThat(result.displayName()).isEqualTo("거북이");
        assertThat(result.tierGrade()).isEqualTo("B");
        assertThat(result.defaultProfileImage()).isEqualTo("/profiles/tiers/turtle.png");
    }

    @Test
    void findDefaultProfileImageByTierCode_returnsLowestGradeImage() {
        TierGrade turtleBronze = tierGrade(
                "TURTLE",
                "거북이",
                "B",
                0.00,
                0.45,
                "/profiles/tiers/turtle.png"
        );

        when(tierGradeRepository.findFirstByTierCodeOrderByMinScoreAsc("TURTLE"))
                .thenReturn(Optional.of(turtleBronze));

        Optional<String> result = tierService.findDefaultProfileImageByTierCode("TURTLE");

        assertThat(result).contains("/profiles/tiers/turtle.png");
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
