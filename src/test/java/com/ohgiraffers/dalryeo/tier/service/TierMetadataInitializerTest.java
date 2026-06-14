package com.ohgiraffers.dalryeo.tier.service;

import com.ohgiraffers.dalryeo.tier.entity.TierGrade;
import com.ohgiraffers.dalryeo.tier.repository.TierGradeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
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
    private TierGradeRepository tierGradeRepository;

    @InjectMocks
    private TierMetadataInitializer tierMetadataInitializer;

    @Test
    void run_insertsAllTierGradeMetadataWhenDatabaseIsEmpty() throws Exception {
        when(tierGradeRepository.findByTierCodeAndGrade(anyString(), anyString())).thenReturn(Optional.empty());
        when(tierGradeRepository.save(any(TierGrade.class))).thenAnswer(invocation -> invocation.getArgument(0));

        tierMetadataInitializer.run(null);

        ArgumentCaptor<TierGrade> captor = ArgumentCaptor.forClass(TierGrade.class);
        verify(tierGradeRepository, atLeastOnce()).save(captor.capture());

        List<TierGrade> saved = captor.getAllValues();
        assertThat(saved).hasSize(28);
        assertThat(saved).anySatisfy(tierGrade -> {
            assertThat(tierGrade.getTierCode()).isEqualTo("TURTLE");
            assertThat(tierGrade.getDisplayName()).isEqualTo("거북이");
            assertThat(tierGrade.getGrade()).isEqualTo("B");
            assertThat(tierGrade.getMinScore()).isEqualTo(0.00);
            assertThat(tierGrade.getMaxScore()).isEqualTo(0.45);
            assertThat(tierGrade.getDefaultProfileImage()).isEqualTo("/profiles/tiers/turtle.png");
        });
    }

    @Test
    void run_updatesExistingTierGradeMetadata() throws Exception {
        TierGrade existingTierGrade = TierGrade.builder()
                .tierCode("DEER")
                .displayName("old")
                .grade("B")
                .minScore(0.0)
                .maxScore(0.0)
                .defaultProfileImage("/profiles/tiers/old.png")
                .build();

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

        assertThat(existingTierGrade.getDisplayName()).isEqualTo("사슴");
        assertThat(existingTierGrade.getMinScore()).isEqualTo(1.20);
        assertThat(existingTierGrade.getMaxScore()).isEqualTo(1.28);
        assertThat(existingTierGrade.getDefaultProfileImage()).isEqualTo("/profiles/tiers/deer.png");
    }
}
