package com.ohgiraffers.dalryeo.ranking.service;

import com.ohgiraffers.dalryeo.auth.entity.User;
import com.ohgiraffers.dalryeo.auth.repository.UserRepository;
import com.ohgiraffers.dalryeo.ranking.dto.DistanceRankingResponse;
import com.ohgiraffers.dalryeo.ranking.dto.RankingMeResponse;
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
                    .tierCode(mapTierName(user.getCurrentTier()))
                    .tierGrade(mapTierGrade(user.getCurrentTierGrade()))
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

            int weeklyAvgPace = 0;
            if (!userRecords.isEmpty()) {
                double totalWeightedPace = userRecords.stream()
                        .mapToDouble(r -> r.getAvgPaceSecPerKm() * r.getDistanceKm())
                        .sum();
                weeklyAvgPace = (int) Math.round(totalWeightedPace / weeklyDistance);
            }

            DistanceRankingResponse ranking = DistanceRankingResponse.builder()
                    .nickname(user.getNickname())
                    .weeklyDistance(weeklyDistance)
                    .weeklyAvgPace(weeklyAvgPace)
                    .tierCode(mapTierName(user.getCurrentTier()))
                    .tierGrade(mapTierGrade(user.getCurrentTierGrade()))
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

        return RankingMeResponse.builder()
                .nickname(user.getNickname())
                .scoreRank(scoreRank)
                .distanceRank(distanceRank)
                .tierCode(mapTierName(user.getCurrentTier()))
                .tierGrade(mapTierGrade(user.getCurrentTierGrade()))
                .tierScore(user.getTierScore())
                .weeklyAvgPace(weeklyAvgPace)
                .weeklyDistance(weeklyDistance)
                .build();
    }

    private Integer calculateScoreRank(List<User> activeUsers, Map<Long, List<RunningRecord>> recordsByUser, Long userId) {
        List<UserScoreEntry> entries = new ArrayList<>();

        for (User user : activeUsers) {
            List<RunningRecord> userRecords = recordsByUser.getOrDefault(user.getId(), Collections.emptyList());
            if (userRecords.isEmpty() || user.getTierScore() == null) {
                continue;
            }

            entries.add(new UserScoreEntry(user.getId(), user.getTierScore()));
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

    private String mapTierName(String tierCode) {
        if (tierCode == null) {
            return "TURTLE";
        }
        return switch (tierCode) {
            case "CHEETAH", "DEER", "HUSKY", "FOX", "ROE_DEER", "SHEEP",
                 "RABBIT", "PANDA", "DUCK", "TURTLE" -> tierCode;
            case "BRONZE", "SILVER", "GOLD", "PLATINUM", "DIAMOND" -> "TURTLE";
            default -> "TURTLE";
        };
    }

    private String mapTierGrade(String tierGrade) {
        if (tierGrade == null) {
            return "B";
        }
        return switch (tierGrade) {
            case "G", "S", "B" -> tierGrade;
            case "Gold" -> "G";
            case "Silver" -> "S";
            case "Bronze" -> "B";
            default -> "B";
        };
    }
}

