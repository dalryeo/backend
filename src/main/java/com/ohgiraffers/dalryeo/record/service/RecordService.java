package com.ohgiraffers.dalryeo.record.service;

import com.ohgiraffers.dalryeo.auth.entity.User;
import com.ohgiraffers.dalryeo.auth.repository.UserRepository;
import com.ohgiraffers.dalryeo.record.dto.RecordIdResponse;
import com.ohgiraffers.dalryeo.record.dto.RecordSummaryResponse;
import com.ohgiraffers.dalryeo.record.dto.RunningRecordRequest;
import com.ohgiraffers.dalryeo.record.dto.WeeklyRecordListResponse;
import com.ohgiraffers.dalryeo.record.dto.WeeklyRecordResponse;
import com.ohgiraffers.dalryeo.record.dto.WeeklySummaryByWeekResponse;
import com.ohgiraffers.dalryeo.record.dto.WeeklySummaryItemResponse;
import com.ohgiraffers.dalryeo.record.dto.WeeklySummaryResponse;
import com.ohgiraffers.dalryeo.record.entity.RunningRecord;
import com.ohgiraffers.dalryeo.record.repository.RunningRecordRepository;
import com.ohgiraffers.dalryeo.weeklytier.entity.WeeklyTier;
import com.ohgiraffers.dalryeo.weeklytier.repository.WeeklyTierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class RecordService {

    private final RunningRecordRepository runningRecordRepository;
    private final UserRepository userRepository;
    private final WeeklyTierRepository weeklyTierRepository;

    /**
     * 러닝 기록 저장
     */
    public RecordIdResponse saveRecord(Long userId, RunningRecordRequest request) {
        RunningRecord record = RunningRecord.builder()
                .userId(userId)
                .platform(request.getPlatform())
                .distanceKm(request.getDistanceKm())
                .durationSec(request.getDurationSec())
                .avgPaceSecPerKm(request.getAvgPaceSecPerKm())
                .avgHeartRate(request.getAvgHeartRate())
                .caloriesKcal(request.getCaloriesKcal())
                .startAt(request.getStartAt())
                .endAt(request.getEndAt())
                .build();

        RunningRecord savedRecord = runningRecordRepository.save(record);

        return RecordIdResponse.builder()
                .recordId(savedRecord.getId())
                .build();
    }

    /**
     * 기록 탭 메인 정보 (주간 요약)
     */
    @Transactional(readOnly = true)
    public RecordSummaryResponse getSummary(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

        // 이번 주 시작일과 종료일 계산 (월요일 ~ 일요일)
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(7);

        LocalDateTime startDateTime = weekStart.atStartOfDay();
        LocalDateTime endDateTime = weekEnd.atStartOfDay();

        // 이번 주 기록 조회
        List<RunningRecord> weeklyRecords = runningRecordRepository.findByUserIdAndWeekRange(
                userId, startDateTime, endDateTime);

        // 주간 통계 계산
        int weeklyCount = weeklyRecords.size();
        double weeklyDistance = weeklyRecords.stream()
                .mapToDouble(RunningRecord::getDistanceKm)
                .sum();

        int weeklyAvgPace = 0;
        if (weeklyCount > 0) {
            double totalPace = weeklyRecords.stream()
                    .mapToInt(RunningRecord::getAvgPaceSecPerKm)
                    .sum();
            weeklyAvgPace = (int) Math.round(totalPace / weeklyCount);
        }
        ResolvedTier currentTier = resolveCurrentTier(userId, weekStart, weeklyRecords);

        return RecordSummaryResponse.builder()
                .currentTier(currentTier.tierCode())
                .currentTierGrade(currentTier.tierGrade())
                .weeklyCount(weeklyCount)
                .weeklyAvgPace(weeklyAvgPace)
                .weeklyDistance(weeklyDistance)
                .build();
    }

    /**
     * 주간 요약 (이번 주, 가입일 이후만 집계)
     */
    @Transactional(readOnly = true)
    public WeeklySummaryResponse getCurrentWeeklySummary(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

        // 이번 주 시작일과 종료일 계산 (월요일 ~ 일요일)
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(7);

        LocalDateTime weekStartDateTime = weekStart.atStartOfDay();
        LocalDateTime endDateTime = weekEnd.atStartOfDay();

        // 가입일 이후만 집계되도록 시작일 보정
        LocalDateTime createdAt = user.getCreatedAt();
        LocalDateTime createdAtDateStart = createdAt != null
                ? createdAt.toLocalDate().atStartOfDay()
                : null;
        LocalDateTime effectiveStart = createdAtDateStart != null && createdAtDateStart.isAfter(weekStartDateTime)
                ? createdAtDateStart
                : weekStartDateTime;

        // 이번 주 기록 조회 (가입일 이후)
        List<RunningRecord> summaryRecords = runningRecordRepository.findByUserIdAndWeekRange(
                userId, effectiveStart, endDateTime);

        int runCount = summaryRecords.size();
        double weeklyDistance = summaryRecords.stream()
                .mapToDouble(RunningRecord::getDistanceKm)
                .sum();

        int averagePace = 0;
        if (weeklyDistance > 0) {
            double totalWeightedPace = summaryRecords.stream()
                    .mapToDouble(record -> record.getAvgPaceSecPerKm() * record.getDistanceKm())
                    .sum();
            averagePace = (int) Math.round(totalWeightedPace / weeklyDistance);
        }
        ResolvedTier currentTier = resolveCurrentTier(userId, weekStart, summaryRecords);

        return WeeklySummaryResponse.builder()
                .currentTier(currentTier.tierCode())
                .currentTierGrade(currentTier.tierGrade())
                .weeklyCount(runCount)
                .weeklyAvgPace(averagePace)
                .weeklyDistance(weeklyDistance)
                .build();
    }

    /**
     * 주간 요약 리스트 (가입일부터 현재까지)
     */
    @Transactional(readOnly = true)
    public List<WeeklySummaryItemResponse> getWeeklySummaryList(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

        LocalDate createdAt = user.getCreatedAt() != null
                ? user.getCreatedAt().toLocalDate()
                : LocalDate.now();
        LocalDate startWeek = createdAt.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate endWeek = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        List<WeeklySummaryItemResponse> summaries = new ArrayList<>();
        LocalDate weekStart = startWeek;
        while (!weekStart.isAfter(endWeek)) {
            WeeklySummaryByWeekResponse summary = buildWeeklySummaryByWeekStart(user, weekStart);
            summaries.add(WeeklySummaryItemResponse.builder()
                    .weekStart(weekStart)
                    .tierCode(summary.getTierCode())
                    .tierGrade(summary.getTierGrade())
                    .runCount(summary.getRunCount())
                    .averagePace(summary.getAveragePace())
                    .weeklyDistance(summary.getWeeklyDistance())
                    .build());
            weekStart = weekStart.plusWeeks(1);
        }

        return summaries;
    }

    /**
     * 주간 기록 목록
     */
    @Transactional(readOnly = true)
    public WeeklyRecordListResponse getWeeklyRecords(Long userId) {
        // 이번 주 시작일과 종료일 계산 (월요일 ~ 일요일)
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(7);

        LocalDateTime startDateTime = weekStart.atStartOfDay();
        LocalDateTime endDateTime = weekEnd.atStartOfDay();

        // 이번 주 기록 조회
        List<RunningRecord> weeklyRecords = runningRecordRepository.findByUserIdAndWeekRange(
                userId, startDateTime, endDateTime);

        List<WeeklyRecordResponse> records = weeklyRecords.stream()
                .map(record -> {
                    String tierCode = resolveTierCode(
                            calculateTierScore(record.getDistanceKm(), record.getAvgPaceSecPerKm()));
                    return WeeklyRecordResponse.builder()
                            .recordId(record.getId())
                            .platform(record.getPlatform())
                            .distanceKm(record.getDistanceKm())
                            .durationSec(record.getDurationSec())
                            .avgPaceSecPerKm(record.getAvgPaceSecPerKm())
                            .avgHeartRate(record.getAvgHeartRate())
                            .caloriesKcal(record.getCaloriesKcal())
                            .tierCode(tierCode)
                            .startAt(record.getStartAt())
                            .endAt(record.getEndAt())
                            .build();
                })
                .collect(Collectors.toList());

        return WeeklyRecordListResponse.builder()
                .weeklyCount(weeklyRecords.size())
                .records(records)
                .build();
    }

    private double calculateTierScore(double distanceKm, int paceSecPerKm) {
        double paceMinutes = round2(paceSecPerKm / 60.0);
        double baseScore = round2(6.00 / paceMinutes);
        double distanceWeight = getDistanceWeight(distanceKm);
        return round2(baseScore * distanceWeight);
    }

    private double getDistanceWeight(double distanceKm) {
        if (distanceKm < 1.00) {
            return 0.50;
        } else if (distanceKm < 2.00) {
            return 0.60;
        } else if (distanceKm < 3.00) {
            return 0.70;
        } else if (distanceKm < 5.00) {
            return 1.00;
        } else if (distanceKm < 7.00) {
            return 1.03;
        } else if (distanceKm < 9.00) {
            return 1.05;
        } else if (distanceKm < 11.00) {
            return 1.06;
        } else if (distanceKm < 15.00) {
            return 1.07;
        } else if (distanceKm < 25.00) {
            return 1.08;
        } else if (distanceKm < 40.00) {
            return 1.09;
        }
        return 1.10;
    }

    private double round2(double value) {
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private WeeklySummaryByWeekResponse buildWeeklySummaryByWeekStart(User user, LocalDate weekStartDate) {
        LocalDateTime weekStartDateTime = weekStartDate.atStartOfDay();
        LocalDateTime endDateTime = weekStartDate.plusDays(7).atStartOfDay();

        // 가입일 이후만 집계되도록 시작일 보정
        LocalDateTime createdAt = user.getCreatedAt();
        LocalDateTime createdAtDateStart = createdAt != null
                ? createdAt.toLocalDate().atStartOfDay()
                : null;
        LocalDateTime effectiveStart = createdAtDateStart != null && createdAtDateStart.isAfter(weekStartDateTime)
                ? createdAtDateStart
                : weekStartDateTime;

        List<RunningRecord> summaryRecords = runningRecordRepository.findByUserIdAndWeekRange(
                user.getId(), effectiveStart, endDateTime);

        int runCount = summaryRecords.size();
        double weeklyDistance = summaryRecords.stream()
                .mapToDouble(RunningRecord::getDistanceKm)
                .sum();

        int averagePace = 0;
        if (weeklyDistance > 0) {
            double totalWeightedPace = summaryRecords.stream()
                    .mapToDouble(record -> record.getAvgPaceSecPerKm() * record.getDistanceKm())
                    .sum();
            averagePace = (int) Math.round(totalWeightedPace / weeklyDistance);
        }
        ResolvedTier currentTier = resolveCurrentTier(user.getId(), weekStartDate, summaryRecords);

        return WeeklySummaryByWeekResponse.builder()
                .tierCode(currentTier.tierCode())
                .tierGrade(currentTier.tierGrade())
                .runCount(runCount)
                .averagePace(averagePace)
                .weeklyDistance(weeklyDistance)
                .build();
    }

    private ResolvedTier resolveCurrentTier(Long userId, LocalDate weekStartDate, List<RunningRecord> weeklyRecords) {
        if (!weeklyRecords.isEmpty()) {
            double weeklyTierScore = calculateWeeklyTierScore(weeklyRecords);
            return new ResolvedTier(resolveTierCode(weeklyTierScore), resolveTierGrade(weeklyTierScore));
        }

        return weeklyTierRepository.findByUserIdAndWeekStartDate(userId, weekStartDate)
                .map(this::toResolvedTier)
                .orElseGet(() -> new ResolvedTier("TURTLE", "B"));
    }

    private ResolvedTier toResolvedTier(WeeklyTier weeklyTier) {
        double score = scoreFromInt(weeklyTier.getTierScore());
        String tierGrade = resolveTierGrade(score);
        return new ResolvedTier(weeklyTier.getTierCode(), tierGrade == null ? "B" : tierGrade);
    }

    private double calculateWeeklyTierScore(List<RunningRecord> records) {
        if (records.isEmpty()) {
            return 0.0;
        }

        double totalScore = records.stream()
                .mapToDouble(record -> calculateTierScore(record.getDistanceKm(), record.getAvgPaceSecPerKm()))
                .sum();
        return round2(totalScore / records.size());
    }

    private String resolveTierCode(double score) {
        if (score >= 1.50) {
            return "CHEETAH";
        } else if (score >= 1.20) {
            return "DEER";
        } else if (score >= 1.00) {
            return "HUSKY";
        } else if (score >= 0.86) {
            return "FOX";
        } else if (score >= 0.75) {
            return "ROE_DEER";
        } else if (score >= 0.67) {
            return "SHEEP";
        } else if (score >= 0.60) {
            return "RABBIT";
        } else if (score >= 0.55) {
            return "PANDA";
        } else if (score >= 0.46) {
            return "DUCK";
        }
        return "TURTLE";
    }

    private String resolveTierGrade(double score) {
        if (score >= 1.50) {
            return gradeForRange(score, 1.64, 1.57, 1.50);
        } else if (score >= 1.20) {
            return gradeForRange(score, 1.39, 1.29, 1.20);
        } else if (score >= 1.00) {
            return gradeForRange(score, 1.13, 1.06, 1.00);
        } else if (score >= 0.86) {
            return gradeForRange(score, 0.95, 0.90, 0.86);
        } else if (score >= 0.75) {
            return gradeForRange(score, 0.82, 0.78, 0.75);
        } else if (score >= 0.67) {
            return gradeForRange(score, 0.72, 0.69, 0.67);
        } else if (score >= 0.60) {
            return gradeForRange(score, 0.64, 0.62, 0.60);
        } else if (score >= 0.55) {
            return gradeForRange(score, 0.58, 0.56, 0.55);
        } else if (score >= 0.46) {
            return gradeForRange(score, 0.52, 0.49, 0.46);
        }
        return null;
    }

    private String gradeForRange(double score, double goldMin, double silverMin, double bronzeMin) {
        if (score >= goldMin) {
            return "G";
        } else if (score >= silverMin) {
            return "S";
        } else if (score >= bronzeMin) {
            return "B";
        }
        return null;
    }

    private double scoreFromInt(Integer score) {
        if (score == null) {
            return 0.0;
        }
        return BigDecimal.valueOf(score)
                .movePointLeft(2)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private record ResolvedTier(String tierCode, String tierGrade) {
    }
}
