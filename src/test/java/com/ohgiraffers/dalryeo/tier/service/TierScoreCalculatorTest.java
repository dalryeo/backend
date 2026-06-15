package com.ohgiraffers.dalryeo.tier.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TierScoreCalculatorTest {

    private final TierScoreCalculator tierScoreCalculator = new TierScoreCalculator();

    @Test
    void displayScoreFromStoredScore_convertsIntegerScoreToTwoDecimalScore() {
        assertThat(tierScoreCalculator.displayScoreFromStoredScore(157)).isEqualTo(1.57);
        assertThat(tierScoreCalculator.displayScoreFromStoredScore(90)).isEqualTo(0.90);
    }

    @Test
    void displayScoreFromStoredScore_returnsZeroWhenStoredScoreIsNull() {
        assertThat(tierScoreCalculator.displayScoreFromStoredScore(null)).isEqualTo(0.0);
    }
}
