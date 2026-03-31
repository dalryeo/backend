package com.ohgiraffers.dalryeo.ranking.service;

import com.ohgiraffers.dalryeo.auth.entity.User;
import com.ohgiraffers.dalryeo.auth.repository.UserRepository;
import com.ohgiraffers.dalryeo.ranking.dto.DistanceRankingResponse;
import com.ohgiraffers.dalryeo.ranking.dto.RankingMeResponse;
import com.ohgiraffers.dalryeo.ranking.dto.ScoreRankingResponse;
import com.ohgiraffers.dalryeo.record.entity.RunningRecord;
import com.ohgiraffers.dalryeo.record.repository.RunningRecordRepository;
import com.ohgiraffers.dalryeo.tier.service.TierService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final TierService tierService;

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

            if (userRecords.isEmpty()) {
                continue; // 이번 주 기록이 없는 사용자는 제외
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

            double weeklyTierScore = calculateWeeklyTierScore(userRecords);
            TierService.TierInfo tierInfo = tierService.resolveByScore(weeklyTierScore);

            ScoreRankingResponse ranking = ScoreRankingResponse.builder()
                    .nickname(user.getNickname())
                    .tierCode(tierInfo.tierCode())
                    .tierGrade(tierInfo.tierGrade())
                    .tierScore(weeklyTierScore)
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

            int weeklyAvgPace = 0;
            if (!userRecords.isEmpty()) {
                double totalWeightedPace = userRecords.stream()
                        .mapToDouble(r -> r.getAvgPaceSecPerKm() * r.getDistanceKm())
                        .sum();
                weeklyAvgPace = (int) Math.round(totalWeightedPace / weeklyDistance);
            }

            double weeklyTierScore = calculateWeeklyTierScore(userRecords);
            TierService.TierInfo tierInfo = tierService.resolveByScore(weeklyTierScore);

            DistanceRankingResponse ranking = DistanceRankingResponse.builder()
                    .nickname(user.getNickname())
                    .weeklyDistance(weeklyDistance)
                    .weeklyAvgPace(weeklyAvgPace)
                    .tierCode(tierInfo.tierCode())
                    .tierGrade(tierInfo.tierGrade())
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

    /**
     * 내 랭킹 조회
     */
    @Transactional(readOnly = true)
    public RankingMeResponse getMyRanking(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

        if (user.isWithdrawn()) {
            throw new RuntimeException("탈퇴한 사용자입니다.");
        }

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
                .filter(activeUser -> !activeUser.isWithdrawn() && activeUser.getNickname() != null)
                .collect(Collectors.toList());

        Integer scoreRank = calculateScoreRank(activeUsers, recordsByUser, userId);
        Integer distanceRank = calculateDistanceRank(activeUsers, recordsByUser, userId);

        List<RunningRecord> myRecords = recordsByUser.getOrDefault(userId, Collections.emptyList());
        double weeklyDistance = myRecords.stream()
                .mapToDouble(RunningRecord::getDistanceKm)
                .sum();

        int weeklyAvgPace = 0;
        if (!myRecords.isEmpty()) {
            double totalWeightedPace = myRecords.stream()
                    .mapToDouble(r -> r.getAvgPaceSecPerKm() * r.getDistanceKm())
                    .sum();
            weeklyAvgPace = (int) Math.round(totalWeightedPace / weeklyDistance);
        }

        double weeklyTierScore = calculateWeeklyTierScore(myRecords);
        TierService.TierInfo tierInfo = tierService.resolveByScore(weeklyTierScore);

        return RankingMeResponse.builder()
                .nickname(user.getNickname())
                .scoreRank(scoreRank)
                .distanceRank(distanceRank)
                .tierCode(tierInfo.tierCode())
                .tierGrade(tierInfo.tierGrade())
                .tierScore(weeklyTierScore)
                .weeklyAvgPace(weeklyAvgPace)
                .weeklyDistance(weeklyDistance)
                .build();
    }

    private Integer calculateScoreRank(List<User> activeUsers, Map<Long, List<RunningRecord>> recordsByUser, Long userId) {
        List<UserScoreEntry> entries = new ArrayList<>();

        for (User user : activeUsers) {
            List<RunningRecord> userRecords = recordsByUser.getOrDefault(user.getId(), Collections.emptyList());
            if (userRecords.isEmpty()) {
                continue;
            }

            double weeklyTierScore = calculateWeeklyTierScore(userRecords);
            entries.add(new UserScoreEntry(user.getId(), weeklyTierScore));
        }

        entries.sort((a, b) -> Double.compare(b.tierScore(), a.tierScore()));
        for (int i = 0; i < entries.size(); i++) {
            if (Objects.equals(entries.get(i).userId(), userId)) {
                return i + 1;
            }
        }
        return null;
    }

    private Integer calculateDistanceRank(List<User> activeUsers, Map<Long, List<RunningRecord>> recordsByUser, Long userId) {
        List<UserDistanceEntry> entries = new ArrayList<>();

        for (User user : activeUsers) {
            List<RunningRecord> userRecords = recordsByUser.getOrDefault(user.getId(), Collections.emptyList());
            if (userRecords.isEmpty()) {
                continue;
            }

            double weeklyDistance = userRecords.stream()
                    .mapToDouble(RunningRecord::getDistanceKm)
                    .sum();

            entries.add(new UserDistanceEntry(user.getId(), weeklyDistance));
        }

        entries.sort((a, b) -> Double.compare(b.weeklyDistance(), a.weeklyDistance()));
        for (int i = 0; i < entries.size(); i++) {
            if (Objects.equals(entries.get(i).userId(), userId)) {
                return i + 1;
            }
        }
        return null;
    }

    private record UserScoreEntry(Long userId, Double tierScore) {
    }

    private record UserDistanceEntry(Long userId, Double weeklyDistance) {
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
}
