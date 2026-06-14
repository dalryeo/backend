package com.ohgiraffers.dalryeo.tier.service;

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
    private TierMetadataCache tierMetadataCache;

    @InjectMocks
    private TierService tierService;

    @Test
    void resolveByScore_returnsTierCodeDisplayNameGradeAndDefaultProfileImage() {
        when(tierMetadataCache.resolveByScore(1.24))
                .thenReturn(new TierService.TierInfo("DEER", "사슴", "B", "/profiles/tiers/deer.png"));

        TierService.TierInfo result = tierService.resolveByScore(1.24);

        assertThat(result.tierCode()).isEqualTo("DEER");
        assertThat(result.displayName()).isEqualTo("사슴");
        assertThat(result.tierGrade()).isEqualTo("B");
        assertThat(result.defaultProfileImage()).isEqualTo("/profiles/tiers/deer.png");
    }

    @Test
    void resolveByTierCodeAndScore_returnsTurtleBronzeMetadata() {
        when(tierMetadataCache.resolveByTierCodeAndScore("TURTLE", 0.30))
                .thenReturn(new TierService.TierInfo("TURTLE", "거북이", "B", "/profiles/tiers/turtle.png"));

        TierService.TierInfo result = tierService.resolveByTierCodeAndScore("TURTLE", 0.30);

        assertThat(result.tierCode()).isEqualTo("TURTLE");
        assertThat(result.displayName()).isEqualTo("거북이");
        assertThat(result.tierGrade()).isEqualTo("B");
        assertThat(result.defaultProfileImage()).isEqualTo("/profiles/tiers/turtle.png");
    }

    @Test
    void findDefaultProfileImageByTierCode_returnsLowestGradeImage() {
        when(tierMetadataCache.findDefaultProfileImageByTierCode("TURTLE"))
                .thenReturn(Optional.of("/profiles/tiers/turtle.png"));

        Optional<String> result = tierService.findDefaultProfileImageByTierCode("TURTLE");

        assertThat(result).contains("/profiles/tiers/turtle.png");
    }
}
