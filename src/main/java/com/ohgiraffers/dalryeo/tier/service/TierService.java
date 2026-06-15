package com.ohgiraffers.dalryeo.tier.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TierService {

    private final TierMetadataCache tierMetadataCache;

    public TierInfo resolveByScore(double score) {
        return tierMetadataCache.resolveByScore(score);
    }

    public TierInfo resolveByTierCodeAndScore(String tierCode, double score) {
        return tierMetadataCache.resolveByTierCodeAndScore(tierCode, score);
    }

    public Optional<String> findDefaultProfileImageByTierCode(String tierCode) {
        return tierMetadataCache.findDefaultProfileImageByTierCode(tierCode);
    }

    public record TierInfo(String tierCode, String displayName, String tierGrade, String defaultProfileImage) {
    }
}
