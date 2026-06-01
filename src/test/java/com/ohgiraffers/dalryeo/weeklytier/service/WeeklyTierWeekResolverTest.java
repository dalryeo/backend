package com.ohgiraffers.dalryeo.weeklytier.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class WeeklyTierWeekResolverTest {

    private final WeeklyTierWeekResolver weekResolver = new WeeklyTierWeekResolver(new WeeklyTierProperties());

    @Test
    void currentWeekStart_returnsSameDayWhenDateIsMonday() {
        LocalDate weekStart = weekResolver.currentWeekStart(LocalDate.of(2026, 6, 1));

        assertThat(weekStart).isEqualTo(LocalDate.of(2026, 6, 1));
    }

    @Test
    void currentWeekStart_returnsMostRecentMondayWhenDateIsNotMonday() {
        LocalDate weekStart = weekResolver.currentWeekStart(LocalDate.of(2026, 6, 4));

        assertThat(weekStart).isEqualTo(LocalDate.of(2026, 6, 1));
    }
}
