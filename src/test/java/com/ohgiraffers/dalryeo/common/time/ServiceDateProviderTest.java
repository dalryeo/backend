package com.ohgiraffers.dalryeo.common.time;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceDateProviderTest {

    private final ServiceDateProvider serviceDateProvider = new ServiceDateProvider("Asia/Seoul");

    @Test
    void today_usesConfiguredSeoulZoneWhenUtcDateIsStillPreviousDay() {
        Clock utcSundayNight = Clock.fixed(
                Instant.parse("2026-05-31T15:30:00Z"),
                ZoneOffset.UTC
        );
        ServiceDateProvider seoulProvider = new ServiceDateProvider("Asia/Seoul", utcSundayNight);
        ServiceDateProvider utcProvider = new ServiceDateProvider("UTC", utcSundayNight);

        assertThat(utcProvider.today()).isEqualTo(LocalDate.of(2026, 5, 31));
        assertThat(seoulProvider.today()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(seoulProvider.currentWeekStart()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(seoulProvider.currentMonthStart()).isEqualTo(LocalDate.of(2026, 6, 1));
    }

    @Test
    void currentWeekStart_returnsSameDayWhenDateIsMonday() {
        LocalDate weekStart = serviceDateProvider.currentWeekStart(LocalDate.of(2026, 6, 1));

        assertThat(weekStart).isEqualTo(LocalDate.of(2026, 6, 1));
    }

    @Test
    void currentWeekStart_returnsMostRecentMondayWhenDateIsNotMonday() {
        LocalDate weekStart = serviceDateProvider.currentWeekStart(LocalDate.of(2026, 6, 4));

        assertThat(weekStart).isEqualTo(LocalDate.of(2026, 6, 1));
    }

    @Test
    void currentMonthStart_returnsFirstDayOfCurrentServiceMonth() {
        LocalDate monthStart = serviceDateProvider.monthStart(LocalDate.of(2026, 6, 4));

        assertThat(monthStart).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(serviceDateProvider.zoneId()).isEqualTo(ZoneId.of("Asia/Seoul"));
    }
}
