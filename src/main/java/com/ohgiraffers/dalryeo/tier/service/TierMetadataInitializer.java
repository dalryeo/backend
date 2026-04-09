package com.ohgiraffers.dalryeo.tier.service;

import com.ohgiraffers.dalryeo.tier.entity.Tier;
import com.ohgiraffers.dalryeo.tier.entity.TierGrade;
import com.ohgiraffers.dalryeo.tier.repository.TierGradeRepository;
import com.ohgiraffers.dalryeo.tier.repository.TierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class TierMetadataInitializer implements ApplicationRunner {

    private static final String TIER_PROFILE_IMAGE_BASE_PATH = "/profiles/tiers/";
    private static final String TIER_PROFILE_IMAGE_EXTENSION = ".png";

    private final TierRepository tierRepository;
    private final TierGradeRepository tierGradeRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedTiers();
        seedTierGrades();
    }

    private void seedTiers() {
        predefinedTiers().forEach(metadata -> {
            Tier tier = tierRepository.findById(metadata.tierCode())
                    .orElseGet(() -> Tier.builder()
                            .tierCode(metadata.tierCode())
                            .displayName(metadata.displayName())
                            .minScore(metadata.minScore())
                            .maxScore(metadata.maxScore())
                            .defaultProfileImage(metadata.defaultProfileImage())
                            .build());

            tier.updateMetadata(
                    metadata.displayName(),
                    metadata.minScore(),
                    metadata.maxScore(),
                    metadata.defaultProfileImage()
            );
            tierRepository.save(tier);
        });
    }

    private void seedTierGrades() {
        predefinedTierGrades().forEach(metadata -> {
            TierGrade tierGrade = tierGradeRepository.findByTierCodeAndGrade(metadata.tierCode(), metadata.grade())
                    .orElseGet(() -> TierGrade.builder()
                            .tierCode(metadata.tierCode())
                            .grade(metadata.grade())
                            .minScore(metadata.minScore())
                            .maxScore(metadata.maxScore())
                            .build());

            tierGrade.updateRange(metadata.minScore(), metadata.maxScore());
            tierGradeRepository.save(tierGrade);
        });
    }

    private List<TierMetadata> predefinedTiers() {
        return List.of(
                new TierMetadata("CHEETAH", "치타", 1.50, 999.99, defaultProfileImagePath("CHEETAH")),
                new TierMetadata("DEER", "사슴", 1.20, 1.49, defaultProfileImagePath("DEER")),
                new TierMetadata("HUSKY", "허스키", 1.00, 1.19, defaultProfileImagePath("HUSKY")),
                new TierMetadata("FOX", "여우", 0.86, 0.99, defaultProfileImagePath("FOX")),
                new TierMetadata("WATERDEER", "고라니", 0.75, 0.85, defaultProfileImagePath("WATERDEER")),
                new TierMetadata("SHEEP", "양", 0.67, 0.74, defaultProfileImagePath("SHEEP")),
                new TierMetadata("RABBIT", "토끼", 0.60, 0.66, defaultProfileImagePath("RABBIT")),
                new TierMetadata("PANDA", "판다", 0.55, 0.59, defaultProfileImagePath("PANDA")),
                new TierMetadata("DUCK", "오리", 0.46, 0.54, defaultProfileImagePath("DUCK")),
                new TierMetadata("TURTLE", "거북이", 0.00, 0.45, defaultProfileImagePath("TURTLE"))
        );
    }

    private String defaultProfileImagePath(String tierCode) {
        return TIER_PROFILE_IMAGE_BASE_PATH + tierCode.toLowerCase() + TIER_PROFILE_IMAGE_EXTENSION;
    }

    private List<TierGradeMetadata> predefinedTierGrades() {
        return List.of(
                new TierGradeMetadata("CHEETAH", "G", 1.64, 999.99),
                new TierGradeMetadata("CHEETAH", "S", 1.57, 1.63),
                new TierGradeMetadata("CHEETAH", "B", 1.50, 1.56),
                new TierGradeMetadata("DEER", "G", 1.39, 1.49),
                new TierGradeMetadata("DEER", "S", 1.29, 1.38),
                new TierGradeMetadata("DEER", "B", 1.20, 1.28),
                new TierGradeMetadata("HUSKY", "G", 1.13, 1.19),
                new TierGradeMetadata("HUSKY", "S", 1.06, 1.12),
                new TierGradeMetadata("HUSKY", "B", 1.00, 1.05),
                new TierGradeMetadata("FOX", "G", 0.95, 0.99),
                new TierGradeMetadata("FOX", "S", 0.90, 0.94),
                new TierGradeMetadata("FOX", "B", 0.86, 0.89),
                new TierGradeMetadata("WATERDEER", "G", 0.82, 0.85),
                new TierGradeMetadata("WATERDEER", "S", 0.78, 0.81),
                new TierGradeMetadata("WATERDEER", "B", 0.75, 0.77),
                new TierGradeMetadata("SHEEP", "G", 0.72, 0.74),
                new TierGradeMetadata("SHEEP", "S", 0.69, 0.71),
                new TierGradeMetadata("SHEEP", "B", 0.67, 0.68),
                new TierGradeMetadata("RABBIT", "G", 0.64, 0.66),
                new TierGradeMetadata("RABBIT", "S", 0.62, 0.63),
                new TierGradeMetadata("RABBIT", "B", 0.60, 0.61),
                new TierGradeMetadata("PANDA", "G", 0.58, 0.59),
                new TierGradeMetadata("PANDA", "S", 0.56, 0.57),
                new TierGradeMetadata("PANDA", "B", 0.55, 0.55),
                new TierGradeMetadata("DUCK", "G", 0.52, 0.54),
                new TierGradeMetadata("DUCK", "S", 0.49, 0.51),
                new TierGradeMetadata("DUCK", "B", 0.46, 0.48)
        );
    }

    private record TierMetadata(
            String tierCode,
            String displayName,
            Double minScore,
            Double maxScore,
            String defaultProfileImage
    ) {
    }

    private record TierGradeMetadata(String tierCode, String grade, Double minScore, Double maxScore) {
    }
}
