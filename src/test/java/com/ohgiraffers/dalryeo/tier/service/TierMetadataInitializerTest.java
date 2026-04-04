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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TierMetadataInitializerTest {

    @Mock
    private TierRepository tierRepository;

    @Mock
    private TierGradeRepository tierGradeRepository;

    @InjectMocks
    private TierMetadataInitializer tierMetadataInitializer;

    @Test
    void run_insertsAllTierMetadataWhenDatabaseIsEmpty() throws Exception {
        when(tierRepository.findById(anyString())).thenReturn(Optional.empty());
        when(tierRepository.save(any(Tier.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tierGradeRepository.findByTierCodeAndGrade(anyString(), anyString())).thenReturn(Optional.empty());
        when(tierGradeRepository.save(any(TierGrade.class))).thenAnswer(invocation -> invocation.getArgument(0));

        tierMetadataInitializer.run(null);

        verify(tierRepository, atLeastOnce()).save(any(Tier.class));
        verify(tierGradeRepository, atLeastOnce()).save(any(TierGrade.class));
    }

    @Test
    void run_updatesExistingTierAndTierGradeMetadata() throws Exception {
        Tier existingTier = Tier.builder()
                .tierCode("DEER")
                .displayName("old")
                .minScore(0.0)
                .maxScore(0.0)
                .defaultProfileImage("/profiles/tiers/old.jpg")
                .build();
        TierGrade existingTierGrade = TierGrade.builder()
                .tierCode("DEER")
                .grade("B")
                .minScore(0.0)
                .maxScore(0.0)
                .build();

        when(tierRepository.findById(anyString())).thenAnswer(invocation -> {
            String tierCode = invocation.getArgument(0);
            if ("DEER".equals(tierCode)) {
                return Optional.of(existingTier);
            }
            return Optional.empty();
        });
        when(tierRepository.save(any(Tier.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tierGradeRepository.findByTierCodeAndGrade(anyString(), anyString())).thenAnswer(invocation -> {
            String tierCode = invocation.getArgument(0);
            String grade = invocation.getArgument(1);
            if ("DEER".equals(tierCode) && "B".equals(grade)) {
                return Optional.of(existingTierGrade);
            }
            return Optional.empty();
        });
        when(tierGradeRepository.save(any(TierGrade.class))).thenAnswer(invocation -> invocation.getArgument(0));

        tierMetadataInitializer.run(null);

        assertThat(existingTier.getDisplayName()).isEqualTo("사슴");
        assertThat(existingTier.getMinScore()).isEqualTo(1.20);
        assertThat(existingTier.getMaxScore()).isEqualTo(1.49);
        assertThat(existingTier.getDefaultProfileImage()).isEqualTo("/profiles/tiers/deer.jpg");
        assertThat(existingTierGrade.getMinScore()).isEqualTo(1.20);
        assertThat(existingTierGrade.getMaxScore()).isEqualTo(1.28);
    }
}
