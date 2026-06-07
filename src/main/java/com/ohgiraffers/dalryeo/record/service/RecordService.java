package com.ohgiraffers.dalryeo.record.service;

import com.ohgiraffers.dalryeo.auth.entity.User;
import com.ohgiraffers.dalryeo.common.time.ServiceDateProvider;
import com.ohgiraffers.dalryeo.record.dto.RecordIdResponse;
import com.ohgiraffers.dalryeo.record.dto.RecordSummaryResponse;
import com.ohgiraffers.dalryeo.record.dto.RunningRecordRequest;
import com.ohgiraffers.dalryeo.record.dto.WeeklyRecordListResponse;
import com.ohgiraffers.dalryeo.record.dto.WeeklyRecordResponse;
import com.ohgiraffers.dalryeo.record.dto.WeeklySummaryByWeekResponse;
import com.ohgiraffers.dalryeo.record.dto.WeeklySummaryItemResponse;
import com.ohgiraffers.dalryeo.record.dto.WeeklySummaryResponse;
import com.ohgiraffers.dalryeo.record.entity.RunningRecord;
import com.ohgiraffers.dalryeo.record.entity.WeeklyUserStats;
import com.ohgiraffers.dalryeo.record.outbox.RecordOutboxEvent;
import com.ohgiraffers.dalryeo.record.outbox.RecordOutboxEventRepository;
import com.ohgiraffers.dalryeo.record.repository.RunningRecordRepository;
import com.ohgiraffers.dalryeo.tier.service.CurrentTierResolver;
import com.ohgiraffers.dalryeo.tier.service.TierScoreCalculator;
import com.ohgiraffers.dalryeo.tier.service.TierService;
import com.ohgiraffers.dalryeo.user.service.UserLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class RecordService {

    private final RunningRecordRepository runningRecordRepository;
    private final UserLookupService userLookupService;
    private final RecordOutboxEventRepository recordOutboxEventRepository;
    private final TierService tierService;
    private final CurrentTierResolver currentTierResolver;
    private final RunningRecordValidator runningRecordValidator;
    private final WeeklyUserStatsService weeklyUserStatsService;
    private final TierScoreCalculator tierScoreCalculator;
    private final ServiceDateProvider serviceDateProvider;

    /**
     * 러닝 기록 저장
     */
    public RecordIdResponse saveRecord(Long userId, RunningRecordRequest request) {
        userLookupService.getActiveById(userId);
        runningRecordValidator.validate(request, Instant.now());

        RunningRecord record = RunningRecord.builder()
                .userId(userId)
                .platform(request.getPlatform())
                .distanceKm(request.getDistanceKm())
                .durationSec(request.getDurationSec())
                .avgPaceSecPerKm(request.getAvgPaceSecPerKm())
                .avgHeartRate(normalizeOptionalMeasuredValue(request.getAvgHeartRate()))
                .caloriesKcal(normalizeOptionalMeasuredValue(request.getCaloriesKcal()))
                .startAt(toServiceLocalDateTime(request.getStartAt()))
                .endAt(toServiceLocalDateTime(request.getEndAt()))
                .build();

        RunningRecord savedRecord = runningRecordRepository.save(record);
        recordOutboxEventRepository.save(RecordOutboxEvent.weeklyStatsUpdateRequested(
                savedRecord.getId(),
                LocalDateTime.now()
        ));

        return RecordIdResponse.builder()
                .recordId(savedRecord.getId())
                .build();
    }

    private Integer normalizeOptionalMeasuredValue(Integer value) {
        return value != null && value == 0 ? null : value;
    }

    private LocalDateTime toServiceLocalDateTime(OffsetDateTime dateTime) {
        return dateTime.atZoneSameInstant(serviceDateProvider.zoneId())
                .toLocalDateTime();
    }

    /**
     * 기록 탭 메인 정보 (주간 요약)
     */
    @Transactional(readOnly = true)
    public RecordSummaryResponse getSummary(Long userId) {
        User user = userLookupService.getActiveById(userId);

        LocalDate weekStart = currentWeekStart();
        return weeklyUserStatsService.findByUserIdAndWeekStartDate(userId, weekStart)
                .map(this::buildRecordSummaryFromStats)
                .orElseGet(() -> buildRecordSummaryFromRawRecords(user, weekStart));
    }

    private RecordSummaryResponse buildRecordSummaryFromStats(WeeklyUserStats stats) {
        ResolvedTier currentTier = resolveCurrentTier(stats.getUserId(), stats.getWeekStartDate());

        return RecordSummaryResponse.builder()
                .currentTier(currentTier.tierCode())
                .currentTierGrade(currentTier.tierGrade())
                .weeklyCount(stats.getRunCount())
                .weeklyAvgPace(stats.getAvgPaceSecPerKm())
                .weeklyDistance(stats.totalDistanceAsDouble())
                .build();
    }

    private RecordSummaryResponse buildRecordSummaryFromRawRecords(User user, LocalDate weekStart) {
        LocalDateTime startDateTime = weekStart.atStartOfDay();
        LocalDateTime endDateTime = weekStart.plusDays(7).atStartOfDay();
        List<RunningRecord> weeklyRecords = runningRecordRepository.findByUserIdAndWeekRange(
                user.getId(), startDateTime, endDateTime);
        WeeklyStatsSnapshot snapshot = summarizeRawRecords(weeklyRecords);
        ResolvedTier currentTier = resolveCurrentTier(user.getId(), weekStart);

        return RecordSummaryResponse.builder()
                .currentTier(currentTier.tierCode())
                .currentTierGrade(currentTier.tierGrade())
                .weeklyCount(snapshot.runCount())
                .weeklyAvgPace(snapshot.averagePace())
                .weeklyDistance(snapshot.weeklyDistance())
                .build();
    }

    private WeeklySummaryResponse buildWeeklySummaryFromStats(WeeklyUserStats stats) {
        ResolvedTier currentTier = resolveCurrentTier(stats.getUserId(), stats.getWeekStartDate());

        return WeeklySummaryResponse.builder()
                .currentTier(currentTier.tierCode())
                .currentTierGrade(currentTier.tierGrade())
                .weeklyCount(stats.getRunCount())
                .weeklyAvgPace(stats.getAvgPaceSecPerKm())
                .weeklyDistance(stats.totalDistanceAsDouble())
                .build();
    }

    private WeeklySummaryResponse buildWeeklySummaryFromRawRecords(User user, LocalDate weekStart) {
        LocalDateTime weekStartDateTime = weekStart.atStartOfDay();
        LocalDateTime endDateTime = weekStart.plusDays(7).atStartOfDay();

        LocalDateTime effectiveStart = effectiveWeekStart(user, weekStartDateTime);
        List<RunningRecord> summaryRecords = runningRecordRepository.findByUserIdAndWeekRange(
                user.getId(), effectiveStart, endDateTime);
        WeeklyStatsSnapshot snapshot = summarizeRawRecords(summaryRecords);
        ResolvedTier currentTier = resolveCurrentTier(user.getId(), weekStart);

        return WeeklySummaryResponse.builder()
                .currentTier(currentTier.tierCode())
                .currentTierGrade(currentTier.tierGrade())
                .weeklyCount(snapshot.runCount())
                .weeklyAvgPace(snapshot.averagePace())
                .weeklyDistance(snapshot.weeklyDistance())
                .build();
    }

    private WeeklyStatsSnapshot summarizeRawRecords(List<RunningRecord> records) {
        int runCount = records.size();
        double weeklyDistance = records.stream()
                .mapToDouble(RunningRecord::getDistanceKm)
                .sum();

        int averagePace = 0;
        if (weeklyDistance > 0) {
            double totalWeightedPace = records.stream()
                    .mapToDouble(record -> record.getAvgPaceSecPerKm() * record.getDistanceKm())
                    .sum();
            averagePace = (int) Math.round(totalWeightedPace / weeklyDistance);
        }

        return new WeeklyStatsSnapshot(runCount, weeklyDistance, averagePace);
    }

    /**
     * 주간 요약 (이번 주, 가입일 이후만 집계)
     */
    @Transactional(readOnly = true)
    public WeeklySummaryResponse getCurrentWeeklySummary(Long userId) {
        User user = userLookupService.getActiveById(userId);

        LocalDate weekStart = currentWeekStart();
        return weeklyUserStatsService.findByUserIdAndWeekStartDate(userId, weekStart)
                .map(this::buildWeeklySummaryFromStats)
                .orElseGet(() -> buildWeeklySummaryFromRawRecords(user, weekStart));
    }

    /**
     * 주간 요약 리스트 (가입일부터 현재까지)
     */
    @Transactional(readOnly = true)
    public List<WeeklySummaryItemResponse> getWeeklySummaryList(Long userId) {
        User user = userLookupService.getActiveById(userId);

        LocalDate createdAt = user.getCreatedAt() != null
                ? user.getCreatedAt().toLocalDate()
                : serviceDateProvider.today();
        LocalDate startWeek = serviceDateProvider.currentWeekStart(createdAt);
        LocalDate endWeek = currentWeekStart();

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
        userLookupService.getActiveById(userId);

        // 이번 주 시작일과 종료일 계산 (월요일 ~ 일요일)
        LocalDate weekStart = currentWeekStart();
        LocalDate weekEnd = weekStart.plusDays(7);

        LocalDateTime startDateTime = weekStart.atStartOfDay();
        LocalDateTime endDateTime = weekEnd.atStartOfDay();

        // 이번 주 기록 조회
        List<RunningRecord> weeklyRecords = runningRecordRepository.findByUserIdAndWeekRange(
                userId, startDateTime, endDateTime);

        List<WeeklyRecordResponse> records = weeklyRecords.stream()
                .map(record -> {
                    String tierCode = tierService.resolveByScore(
                            tierScoreCalculator.calculateRecordScore(record.getDistanceKm(), record.getAvgPaceSecPerKm()))
                            .tierCode();
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

    private WeeklySummaryByWeekResponse buildWeeklySummaryByWeekStart(User user, LocalDate weekStartDate) {
        return weeklyUserStatsService.findByUserIdAndWeekStartDate(user.getId(), weekStartDate)
                .map(this::buildWeeklySummaryByWeekFromStats)
                .orElseGet(() -> buildWeeklySummaryByWeekFromRawRecords(user, weekStartDate));
    }

    private WeeklySummaryByWeekResponse buildWeeklySummaryByWeekFromStats(WeeklyUserStats stats) {
        TierService.TierInfo tierInfo = tierService.resolveByScore(stats.tierScoreAsDouble());

        return WeeklySummaryByWeekResponse.builder()
                .tierCode(tierInfo.tierCode())
                .tierGrade(tierInfo.tierGrade())
                .runCount(stats.getRunCount())
                .averagePace(stats.getAvgPaceSecPerKm())
                .weeklyDistance(stats.totalDistanceAsDouble())
                .build();
    }

    private WeeklySummaryByWeekResponse buildWeeklySummaryByWeekFromRawRecords(User user, LocalDate weekStartDate) {
        LocalDateTime weekStartDateTime = weekStartDate.atStartOfDay();
        LocalDateTime endDateTime = weekStartDate.plusDays(7).atStartOfDay();

        LocalDateTime effectiveStart = effectiveWeekStart(user, weekStartDateTime);

        List<RunningRecord> summaryRecords = runningRecordRepository.findByUserIdAndWeekRange(
                user.getId(), effectiveStart, endDateTime);

        WeeklyStatsSnapshot snapshot = summarizeRawRecords(summaryRecords);
        ResolvedTier performanceTier = resolveWeeklyPerformanceTier(summaryRecords);

        return WeeklySummaryByWeekResponse.builder()
                .tierCode(performanceTier.tierCode())
                .tierGrade(performanceTier.tierGrade())
                .runCount(snapshot.runCount())
                .averagePace(snapshot.averagePace())
                .weeklyDistance(snapshot.weeklyDistance())
                .build();
    }

    private LocalDate currentWeekStart() {
        return serviceDateProvider.currentWeekStart();
    }

    private LocalDateTime effectiveWeekStart(User user, LocalDateTime weekStartDateTime) {
        LocalDateTime createdAt = user.getCreatedAt();
        LocalDateTime createdAtDateStart = createdAt != null
                ? createdAt.toLocalDate().atStartOfDay()
                : null;
        return createdAtDateStart != null && createdAtDateStart.isAfter(weekStartDateTime)
                ? createdAtDateStart
                : weekStartDateTime;
    }

    private ResolvedTier resolveCurrentTier(Long userId, LocalDate weekStartDate) {
        return currentTierResolver.resolve(userId, weekStartDate)
                .map(ResolvedTier::from)
                .orElseGet(() -> new ResolvedTier("TURTLE", "B"));
    }

    private ResolvedTier resolveWeeklyPerformanceTier(List<RunningRecord> weeklyRecords) {
        if (weeklyRecords.isEmpty()) {
            return new ResolvedTier("TURTLE", "B");
        }

        double totalScore = weeklyRecords.stream()
                .mapToDouble(record -> tierScoreCalculator.calculateRecordScore(
                        record.getDistanceKm(),
                        record.getAvgPaceSecPerKm()
                ))
                .sum();
        double weeklyTierScore = tierScoreCalculator.calculateWeeklyScore(totalScore, weeklyRecords.size());
        TierService.TierInfo tierInfo = tierService.resolveByScore(weeklyTierScore);
        return new ResolvedTier(tierInfo.tierCode(), tierInfo.tierGrade());
    }

    private record ResolvedTier(String tierCode, String tierGrade) {
        private static ResolvedTier from(CurrentTierResolver.CurrentTier currentTier) {
            String tierGrade = currentTier.tierGrade();
            return new ResolvedTier(currentTier.tierCode(), tierGrade == null ? "B" : tierGrade);
        }
    }

    private record WeeklyStatsSnapshot(int runCount, double weeklyDistance, int averagePace) {
    }
}
