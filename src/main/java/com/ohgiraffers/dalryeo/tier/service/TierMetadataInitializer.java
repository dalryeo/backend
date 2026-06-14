package com.ohgiraffers.dalryeo.tier.service;

import com.ohgiraffers.dalryeo.tier.entity.TierGrade;
import com.ohgiraffers.dalryeo.tier.repository.TierGradeRepository;
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

    private final TierGradeRepository tierGradeRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedTierGrades();
    }

    private void seedTierGrades() {
        predefinedTierGrades().forEach(metadata -> {
            TierGrade tierGrade = tierGradeRepository.findByTierCodeAndGrade(metadata.tierCode(), metadata.grade())
                    .orElseGet(() -> TierGrade.builder()
                            .tierCode(metadata.tierCode())
                            .displayName(metadata.displayName())
                            .grade(metadata.grade())
                            .minScore(metadata.minScore())
                            .maxScore(metadata.maxScore())
                            .defaultProfileImage(metadata.defaultProfileImage())
                            .build());

            tierGrade.updateMetadata(
                    metadata.displayName(),
                    metadata.minScore(),
                    metadata.maxScore(),
                    metadata.defaultProfileImage()
            );
            tierGradeRepository.save(tierGrade);
        });
    }

    private String defaultProfileImagePath(String tierCode) {
        return TIER_PROFILE_IMAGE_BASE_PATH + tierCode.toLowerCase() + TIER_PROFILE_IMAGE_EXTENSION;
    }

    private List<TierGradeMetadata> predefinedTierGrades() {
        return List.of(
                tierGrade("CHEETAH", "치타", "G", 1.64, 999.99),
                tierGrade("CHEETAH", "치타", "S", 1.57, 1.63),
                tierGrade("CHEETAH", "치타", "B", 1.50, 1.56),
                tierGrade("DEER", "사슴", "G", 1.39, 1.49),
                tierGrade("DEER", "사슴", "S", 1.29, 1.38),
                tierGrade("DEER", "사슴", "B", 1.20, 1.28),
                tierGrade("HUSKY", "허스키", "G", 1.13, 1.19),
                tierGrade("HUSKY", "허스키", "S", 1.06, 1.12),
                tierGrade("HUSKY", "허스키", "B", 1.00, 1.05),
                tierGrade("FOX", "여우", "G", 0.95, 0.99),
                tierGrade("FOX", "여우", "S", 0.90, 0.94),
                tierGrade("FOX", "여우", "B", 0.86, 0.89),
                tierGrade("WATERDEER", "고라니", "G", 0.82, 0.85),
                tierGrade("WATERDEER", "고라니", "S", 0.78, 0.81),
                tierGrade("WATERDEER", "고라니", "B", 0.75, 0.77),
                tierGrade("SHEEP", "양", "G", 0.72, 0.74),
                tierGrade("SHEEP", "양", "S", 0.69, 0.71),
                tierGrade("SHEEP", "양", "B", 0.67, 0.68),
                tierGrade("RABBIT", "토끼", "G", 0.64, 0.66),
                tierGrade("RABBIT", "토끼", "S", 0.62, 0.63),
                tierGrade("RABBIT", "토끼", "B", 0.60, 0.61),
                tierGrade("PANDA", "판다", "G", 0.58, 0.59),
                tierGrade("PANDA", "판다", "S", 0.56, 0.57),
                tierGrade("PANDA", "판다", "B", 0.55, 0.55),
                tierGrade("DUCK", "오리", "G", 0.52, 0.54),
                tierGrade("DUCK", "오리", "S", 0.49, 0.51),
                tierGrade("DUCK", "오리", "B", 0.46, 0.48),
                tierGrade("TURTLE", "거북이", "B", 0.00, 0.45)
        );
    }

    private TierGradeMetadata tierGrade(
            String tierCode,
            String displayName,
            String grade,
            Double minScore,
            Double maxScore
    ) {
        return new TierGradeMetadata(
                tierCode,
                displayName,
                grade,
                minScore,
                maxScore,
                defaultProfileImagePath(tierCode)
        );
    }

    private record TierGradeMetadata(
            String tierCode,
            String displayName,
            String grade,
            Double minScore,
            Double maxScore,
            String defaultProfileImage
    ) {
    }
}
