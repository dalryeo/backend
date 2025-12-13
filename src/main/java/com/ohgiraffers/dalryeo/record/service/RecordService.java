package com.ohgiraffers.dalryeo.record.service;

import com.ohgiraffers.dalryeo.auth.entity.User;
import com.ohgiraffers.dalryeo.auth.repository.UserRepository;
import com.ohgiraffers.dalryeo.record.dto.RecordIdResponse;
import com.ohgiraffers.dalryeo.record.dto.RecordSummaryResponse;
import com.ohgiraffers.dalryeo.record.dto.RunningRecordRequest;
import com.ohgiraffers.dalryeo.record.dto.WeeklyRecordResponse;
import com.ohgiraffers.dalryeo.record.entity.RunningRecord;
import com.ohgiraffers.dalryeo.record.repository.RunningRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class RecordService {

    private final RunningRecordRepository runningRecordRepository;
    private final UserRepository userRepository;

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

        return RecordSummaryResponse.builder()
                .currentTier(user.getCurrentTier() != null ? user.getCurrentTier() : "BRONZE")
                .currentTierGrade(user.getCurrentTierGrade() != null ? user.getCurrentTierGrade() : "C")
                .weeklyCount(weeklyCount)
                .weeklyAvgPace(weeklyAvgPace)
                .weeklyDistance(weeklyDistance)
                .build();
    }

    /**
     * 주간 기록 목록
     */
    @Transactional(readOnly = true)
    public List<WeeklyRecordResponse> getWeeklyRecords(Long userId) {
        // 이번 주 시작일과 종료일 계산 (월요일 ~ 일요일)
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(7);

        LocalDateTime startDateTime = weekStart.atStartOfDay();
        LocalDateTime endDateTime = weekEnd.atStartOfDay();

        // 이번 주 기록 조회
        List<RunningRecord> weeklyRecords = runningRecordRepository.findByUserIdAndWeekRange(
                userId, startDateTime, endDateTime);

        // 날짜별로 그룹화하여 일별 합계 계산
        Map<LocalDate, List<RunningRecord>> recordsByDate = weeklyRecords.stream()
                .collect(Collectors.groupingBy(record -> record.getStartAt().toLocalDate()));

        return recordsByDate.entrySet().stream()
                .map(entry -> {
                    LocalDate date = entry.getKey();
                    List<RunningRecord> dayRecords = entry.getValue();

                    // 하루 총 거리
                    double totalDistance = dayRecords.stream()
                            .mapToDouble(RunningRecord::getDistanceKm)
                            .sum();

                    // 하루 평균 페이스 (거리 가중 평균)
                    int avgPace = 0;
                    if (dayRecords.size() > 0) {
                        double totalWeightedPace = dayRecords.stream()
                                .mapToDouble(r -> r.getAvgPaceSecPerKm() * r.getDistanceKm())
                                .sum();
                        avgPace = (int) Math.round(totalWeightedPace / totalDistance);
                    }

                    return WeeklyRecordResponse.builder()
                            .date(date)
                            .distanceKm(totalDistance)
                            .paceSecPerKm(avgPace)
                            .build();
                })
                .sorted((a, b) -> b.getDate().compareTo(a.getDate())) // 최신순 정렬
                .collect(Collectors.toList());
    }
}

