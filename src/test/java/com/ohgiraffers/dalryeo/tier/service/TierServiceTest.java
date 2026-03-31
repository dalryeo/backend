package com.ohgiraffers.dalryeo.tier.service;

import com.ohgiraffers.dalryeo.tier.entity.Tier;
import com.ohgiraffers.dalryeo.tier.entity.TierGrade;
import com.ohgiraffers.dalryeo.tier.repository.TierGradeRepository;
import com.ohgiraffers.dalryeo.tier.repository.TierRepository;
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
    private TierRepository tierRepository;

    @Mock
    private TierGradeRepository tierGradeRepository;

    @InjectMocks
    private TierService tierService;

    @Test
    void resolveByScore_returnsTierCodeDisplayNameAndGrade() {
        Tier deer = Tier.builder()
                .tierCode("DEER")
                .displayName("사슴")
                .minScore(1.20)
                .maxScore(1.49)
                .build();
        TierGrade deerBronze = TierGrade.builder()
                .tierCode("DEER")
                .grade("B")
                .minScore(1.20)
                .maxScore(1.28)
                .build();

        when(tierRepository.findFirstByMinScoreLessThanEqualAndMaxScoreGreaterThanEqualOrderByMinScoreDesc(1.24, 1.24))
                .thenReturn(Optional.of(deer));
        when(tierGradeRepository.findFirstByTierCodeAndMinScoreLessThanEqualAndMaxScoreGreaterThanEqualOrderByMinScoreDesc(
                "DEER", 1.24, 1.24))
                .thenReturn(Optional.of(deerBronze));

        TierService.TierInfo result = tierService.resolveByScore(1.24);

        assertThat(result.tierCode()).isEqualTo("DEER");
        assertThat(result.displayName()).isEqualTo("사슴");
        assertThat(result.tierGrade()).isEqualTo("B");
    }

    @Test
    void resolveByTierCodeAndScore_returnsNullWhenGradeMetadataDoesNotExist() {
        Tier turtle = Tier.builder()
                .tierCode("TURTLE")
                .displayName("거북이")
                .minScore(0.0)
                .maxScore(0.45)
                .build();

        when(tierRepository.findById("TURTLE")).thenReturn(Optional.of(turtle));
        when(tierGradeRepository.findFirstByTierCodeAndMinScoreLessThanEqualAndMaxScoreGreaterThanEqualOrderByMinScoreDesc(
                "TURTLE", 0.30, 0.30))
                .thenReturn(Optional.empty());

        TierService.TierInfo result = tierService.resolveByTierCodeAndScore("TURTLE", 0.30);

        assertThat(result.tierCode()).isEqualTo("TURTLE");
        assertThat(result.displayName()).isEqualTo("거북이");
        assertThat(result.tierGrade()).isNull();
    }
}
