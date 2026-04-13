package com.ohgiraffers.dalryeo.record.service;

import com.ohgiraffers.dalryeo.auth.entity.User;
import com.ohgiraffers.dalryeo.auth.entity.UserStatus;
import com.ohgiraffers.dalryeo.auth.repository.UserRepository;
import com.ohgiraffers.dalryeo.record.dto.RunningRecordRequest;
import com.ohgiraffers.dalryeo.record.entity.WeeklyUserStats;
import com.ohgiraffers.dalryeo.record.repository.RunningRecordRepository;
import com.ohgiraffers.dalryeo.record.repository.WeeklyUserStatsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class RecordAggregationIntegrationTest {

    @Autowired
    private RecordService recordService;

    @Autowired
    private RunningRecordRepository runningRecordRepository;

    @Autowired
    private WeeklyUserStatsRepository weeklyUserStatsRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanDatabase() {
        weeklyUserStatsRepository.deleteAll();
        runningRecordRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void saveRecord_accumulatesWeeklyUserStatsInSameWeek() {
        User user = userRepository.save(User.builder()
                .status(UserStatus.NORMAL)
                .build());

        recordService.saveRecord(user.getId(), request(
                5.0,
                300,
                LocalDateTime.of(2026, 3, 31, 7, 0)
        ));
        recordService.saveRecord(user.getId(), request(
                10.0,
                300,
                LocalDateTime.of(2026, 4, 1, 7, 0)
        ));

        WeeklyUserStats stats = weeklyUserStatsRepository.findByUserIdAndWeekStartDate(
                user.getId(),
                LocalDate.of(2026, 3, 30)
        ).orElseThrow();

        assertThat(runningRecordRepository.count()).isEqualTo(2);
        assertThat(stats.getRunCount()).isEqualTo(2);
        assertThat(stats.getTotalDistanceKm()).isEqualByComparingTo(BigDecimal.valueOf(15.000).setScale(3));
        assertThat(stats.getTotalDurationSec()).isEqualTo(4500);
        assertThat(stats.getWeightedPaceSum()).isEqualByComparingTo(BigDecimal.valueOf(4500.000).setScale(3));
        assertThat(stats.getAvgPaceSecPerKm()).isEqualTo(300);
        assertThat(stats.getTierScoreSum()).isEqualByComparingTo(BigDecimal.valueOf(2.51));
        assertThat(stats.getTierScore()).isEqualByComparingTo(BigDecimal.valueOf(1.26));
    }

    private RunningRecordRequest request(double distanceKm, int avgPaceSecPerKm, LocalDateTime startAt) {
        int durationSec = (int) Math.round(distanceKm * avgPaceSecPerKm);
        RunningRecordRequest request = new RunningRecordRequest();
        ReflectionTestUtils.setField(request, "platform", "IOS");
        ReflectionTestUtils.setField(request, "distanceKm", distanceKm);
        ReflectionTestUtils.setField(request, "durationSec", durationSec);
        ReflectionTestUtils.setField(request, "avgPaceSecPerKm", avgPaceSecPerKm);
        ReflectionTestUtils.setField(request, "avgHeartRate", 150);
        ReflectionTestUtils.setField(request, "caloriesKcal", 300);
        ReflectionTestUtils.setField(request, "startAt", startAt);
        ReflectionTestUtils.setField(request, "endAt", startAt.plusSeconds(durationSec));
        return request;
    }
}
