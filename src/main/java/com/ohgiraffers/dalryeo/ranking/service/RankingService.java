package com.ohgiraffers.dalryeo.ranking.service;

import com.ohgiraffers.dalryeo.auth.entity.User;
import com.ohgiraffers.dalryeo.auth.repository.UserRepository;
import com.ohgiraffers.dalryeo.ranking.dto.DistanceRankingResponse;
import com.ohgiraffers.dalryeo.ranking.dto.ScoreRankingResponse;
import com.ohgiraffers.dalryeo.record.entity.RunningRecord;
import com.ohgiraffers.dalryeo.record.repository.RunningRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class RankingService {

    private final RunningRecordRepository runningRecordRepository;
    private final UserRepository userRepository;

    /**
     * 점수 기반 주간 랭킹 조회
     */
    @Transactional(readOnly = true)
    public List<ScoreRankingResponse> getWeeklyScoreRanking() {
        // 이번 주 시작일과 종료일 계산 (월요일 ~ 일요일)
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(7);

        LocalDateTime startDateTime = weekStart.atStartOfDay();
        LocalDateTime endDateTime = weekEnd.atStartOfDay();

        // 이번 주 모든 기록 조회
        List<RunningRecord> weeklyRecords = runningRecordRepository.findByWeekRange(startDateTime, endDateTime);

        // 사용자별로 그룹화
        Map<Long, List<RunningRecord>> recordsByUser = weeklyRecords.stream()
                .collect(Collectors.groupingBy(RunningRecord::getUserId));

        // 모든 활성 사용자 조회 (탈퇴하지 않은 사용자)
        List<User> activeUsers = userRepository.findAll().stream()
                .filter(user -> !user.isWithdrawn() && user.getNickname() != null)
                .collect(Collectors.toList());

        // 사용자별 주간 통계 계산 및 랭킹 데이터 생성
        List<ScoreRankingResponse> rankings = new ArrayList<>();

        for (User user : activeUsers) {
            List<RunningRecord> userRecords = recordsByUser.getOrDefault(user.getId(), Collections.emptyList());

            if (userRecords.isEmpty() || user.getTierScore() == null) {
                continue; // 이번 주 기록이 없거나 tierScore가 없는 사용자는 제외
            }

            // 주간 통계 계산
            double weeklyDistance = userRecords.stream()
                    .mapToDouble(RunningRecord::getDistanceKm)
                    .sum();

            int weeklyAvgPace = 0;
            if (!userRecords.isEmpty()) {
                double totalWeightedPace = userRecords.stream()
                        .mapToDouble(r -> r.getAvgPaceSecPerKm() * r.getDistanceKm())
                        .sum();
                weeklyAvgPace = (int) Math.round(totalWeightedPace / weeklyDistance);
            }

            ScoreRankingResponse ranking = ScoreRankingResponse.builder()
                    .nickname(user.getNickname())
                    .tierCode(user.getCurrentTier() != null ? user.getCurrentTier() : "BRONZE")
                    .tierGrade(user.getCurrentTierGrade() != null ? user.getCurrentTierGrade() : "C")
                    .tierScore(user.getTierScore())
                    .weeklyAvgPace(weeklyAvgPace)
                    .weeklyDistance(weeklyDistance)
                    .build();

            rankings.add(ranking);
        }

        // tierScore 기준 내림차순 정렬
        rankings.sort((a, b) -> Double.compare(b.getTierScore(), a.getTierScore()));

        // 랭크 부여
        for (int i = 0; i < rankings.size(); i++) {
            rankings.get(i).setRank(i + 1);
        }

        return rankings;
    }

    /**
     * 거리 기반 주간 랭킹 조회
     */
    @Transactional(readOnly = true)
    public List<DistanceRankingResponse> getWeeklyDistanceRanking() {
        // 이번 주 시작일과 종료일 계산 (월요일 ~ 일요일)
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(7);

        LocalDateTime startDateTime = weekStart.atStartOfDay();
        LocalDateTime endDateTime = weekEnd.atStartOfDay();

        // 이번 주 모든 기록 조회
        List<RunningRecord> weeklyRecords = runningRecordRepository.findByWeekRange(startDateTime, endDateTime);

        // 사용자별로 그룹화
        Map<Long, List<RunningRecord>> recordsByUser = weeklyRecords.stream()
                .collect(Collectors.groupingBy(RunningRecord::getUserId));

        // 모든 활성 사용자 조회 (탈퇴하지 않은 사용자)
        List<User> activeUsers = userRepository.findAll().stream()
                .filter(user -> !user.isWithdrawn() && user.getNickname() != null)
                .collect(Collectors.toList());

        // 사용자별 주간 거리 계산 및 랭킹 데이터 생성
        List<DistanceRankingResponse> rankings = new ArrayList<>();

        for (User user : activeUsers) {
            List<RunningRecord> userRecords = recordsByUser.getOrDefault(user.getId(), Collections.emptyList());

            if (userRecords.isEmpty()) {
                continue; // 이번 주 기록이 없는 사용자는 제외
            }

            // 주간 거리 계산
            double weeklyDistance = userRecords.stream()
                    .mapToDouble(RunningRecord::getDistanceKm)
                    .sum();

            DistanceRankingResponse ranking = DistanceRankingResponse.builder()
                    .nickname(user.getNickname())
                    .weeklyDistance(weeklyDistance)
                    .tierCode(user.getCurrentTier() != null ? user.getCurrentTier() : "BRONZE")
                    .tierGrade(user.getCurrentTierGrade() != null ? user.getCurrentTierGrade() : "C")
                    .build();

            rankings.add(ranking);
        }

        // weeklyDistance 기준 내림차순 정렬
        rankings.sort((a, b) -> Double.compare(b.getWeeklyDistance(), a.getWeeklyDistance()));

        // 랭크 부여
        for (int i = 0; i < rankings.size(); i++) {
            rankings.get(i).setRank(i + 1);
        }

        return rankings;
    }
}

