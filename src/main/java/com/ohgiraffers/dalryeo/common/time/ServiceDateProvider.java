package com.ohgiraffers.dalryeo.common.time;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

@Component
public class ServiceDateProvider {

    private final ZoneId zoneId;
    private final Clock clock;

    @Autowired
    public ServiceDateProvider(@Value("${app.time-zone:Asia/Seoul}") String zone) {
        this(zone, Clock.systemUTC());
    }

    public ServiceDateProvider(String zone, Clock clock) {
        this.zoneId = ZoneId.of(zone);
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(zoneId));
    }

    public LocalDate currentWeekStart() {
        return currentWeekStart(today());
    }

    public LocalDate currentWeekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    public LocalDate currentMonthStart() {
        return monthStart(today());
    }

    public LocalDate monthStart(LocalDate date) {
        return date.withDayOfMonth(1);
    }

    public ZoneId zoneId() {
        return zoneId;
    }
}
