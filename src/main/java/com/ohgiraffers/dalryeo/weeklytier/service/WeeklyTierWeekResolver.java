package com.ohgiraffers.dalryeo.weeklytier.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

@Component
@RequiredArgsConstructor
public class WeeklyTierWeekResolver {

    private final WeeklyTierProperties properties;

    public LocalDate currentWeekStart() {
        return currentWeekStart(LocalDate.now(properties.zoneId()));
    }

    LocalDate currentWeekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
