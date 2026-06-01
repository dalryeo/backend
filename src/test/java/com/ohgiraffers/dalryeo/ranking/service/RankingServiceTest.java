package com.ohgiraffers.dalryeo.ranking.service;

import com.ohgiraffers.dalryeo.auth.entity.User;
import com.ohgiraffers.dalryeo.auth.entity.UserStatus;
import com.ohgiraffers.dalryeo.auth.repository.UserRepository;
import com.ohgiraffers.dalryeo.common.time.ServiceDateProvider;
import com.ohgiraffers.dalryeo.ranking.dto.DistanceRankingResponse;
import com.ohgiraffers.dalryeo.ranking.dto.RankingMeResponse;
import com.ohgiraffers.dalryeo.ranking.dto.ScoreRankingResponse;
import com.ohgiraffers.dalryeo.record.entity.WeeklyUserStats;
import com.ohgiraffers.dalryeo.record.repository.WeeklyUserStatsRepository;
import com.ohgiraffers.dalryeo.record.repository.WeeklyUserStatsRepository.WeeklyRankingRow;
import com.ohgiraffers.dalryeo.tier.service.TierService;
import com.ohgiraffers.dalryeo.user.exception.UserErrorCode;
import com.ohgiraffers.dalryeo.user.exception.UserException;
import com.ohgiraffers.dalryeo.user.service.UserLookupService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RankingServiceTest {

    @Mock
    private WeeklyUserStatsRepository weeklyUserStatsRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserLookupService userLookupService;

    @Mock
    private TierService tierService;

    @Mock
    private ServiceDateProvider serviceDateProvider;

    @InjectMocks
    private RankingService rankingService;

    @Test
    void getWeeklyScoreRanking_returnsRankedUsersFromWeeklyStatsInDescendingScoreOrder() {
        WeeklyRankingRow betaStats = weeklyRankingRow(2L, "beta", 10.0, 300, 1.27);
        WeeklyRankingRow alphaStats = weeklyRankingRow(1L, "alpha", 5.0, 300, 1.24);

        when(serviceDateProvider.currentWeekStart()).thenReturn(LocalDate.of(2026, 3, 30));
        when(weeklyUserStatsRepository.findScoreRankingRows(any(LocalDate.class), eq(100)))
                .thenReturn(List.of(betaStats, alphaStats));
        when(tierService.resolveByScore(1.27))
                .thenReturn(new TierService.TierInfo("DEER", "사슴", "B", "/profiles/tiers/deer.png"));
        when(tierService.resolveByScore(1.24))
                .thenReturn(new TierService.TierInfo("DEER", "사슴", "B", "/profiles/tiers/deer.png"));

        List<ScoreRankingResponse> response = rankingService.getWeeklyScoreRanking();

        assertThat(response).hasSize(2);
        assertThat(response.get(0).getRank()).isEqualTo(1);
        assertThat(response.get(0).getNickname()).isEqualTo("beta");
        assertThat(response.get(0).getTierCode()).isEqualTo("DEER");
        assertThat(response.get(0).getTierGrade()).isEqualTo("B");
        assertThat(response.get(0).getTierScore()).isEqualTo(1.27);
        assertThat(response.get(1).getRank()).isEqualTo(2);
        assertThat(response.get(1).getNickname()).isEqualTo("alpha");
        assertThat(response.get(1).getTierScore()).isEqualTo(1.24);
        verifyNoInteractions(userRepository);
    }

    @Test
    void getWeeklyDistanceRanking_returnsRankedUsersFromWeeklyStatsInDescendingDistanceOrder() {
        WeeklyRankingRow betaStats = weeklyRankingRow(2L, "beta", 10.0, 300, 1.27);
        WeeklyRankingRow alphaStats = weeklyRankingRow(1L, "alpha", 5.0, 300, 1.24);

        when(serviceDateProvider.currentWeekStart()).thenReturn(LocalDate.of(2026, 3, 30));
        when(weeklyUserStatsRepository.findDistanceRankingRows(any(LocalDate.class), eq(100)))
                .thenReturn(List.of(betaStats, alphaStats));
        when(tierService.resolveByScore(1.27))
                .thenReturn(new TierService.TierInfo("DEER", "사슴", "B", "/profiles/tiers/deer.png"));
        when(tierService.resolveByScore(1.24))
                .thenReturn(new TierService.TierInfo("DEER", "사슴", "B", "/profiles/tiers/deer.png"));

        List<DistanceRankingResponse> response = rankingService.getWeeklyDistanceRanking();

        assertThat(response).hasSize(2);
        assertThat(response.get(0).getRank()).isEqualTo(1);
        assertThat(response.get(0).getNickname()).isEqualTo("beta");
        assertThat(response.get(0).getWeeklyDistance()).isEqualTo(10.0);
        assertThat(response.get(1).getRank()).isEqualTo(2);
        assertThat(response.get(1).getNickname()).isEqualTo("alpha");
        assertThat(response.get(1).getWeeklyDistance()).isEqualTo(5.0);
        verifyNoInteractions(userRepository);
    }

    @Test
    void getMyRanking_returnsMyRanksAndWeeklyStatsFromCountQueries() {
        Long userId = 1L;
        User alpha = user(1L, "alpha", UserStatus.NORMAL);
        WeeklyUserStats alphaStats = weeklyStats(1L, 5.0, 300, 1.24);

        when(userLookupService.getActiveById(userId)).thenReturn(alpha);
        when(serviceDateProvider.currentWeekStart()).thenReturn(LocalDate.of(2026, 3, 30));
        when(weeklyUserStatsRepository.findByUserIdAndWeekStartDate(eq(userId), any(LocalDate.class)))
                .thenReturn(Optional.of(alphaStats));
        when(weeklyUserStatsRepository.countAheadForScoreRank(
                any(LocalDate.class),
                eq(BigDecimal.valueOf(1.24)),
                eq(BigDecimal.valueOf(5.000).setScale(3)),
                eq(userId)
        )).thenReturn(1L);
        when(weeklyUserStatsRepository.countAheadForDistanceRank(
                any(LocalDate.class),
                eq(BigDecimal.valueOf(5.000).setScale(3)),
                eq(BigDecimal.valueOf(1.24)),
                eq(userId)
        )).thenReturn(1L);
        when(tierService.resolveByScore(1.24))
                .thenReturn(new TierService.TierInfo("DEER", "사슴", "B", "/profiles/tiers/deer.png"));

        RankingMeResponse response = rankingService.getMyRanking(userId);

        assertThat(response.getNickname()).isEqualTo("alpha");
        assertThat(response.getScoreRank()).isEqualTo(2);
        assertThat(response.getDistanceRank()).isEqualTo(2);
        assertThat(response.getTierCode()).isEqualTo("DEER");
        assertThat(response.getTierGrade()).isEqualTo("B");
        assertThat(response.getTierScore()).isEqualTo(1.24);
        assertThat(response.getWeeklyAvgPace()).isEqualTo(300);
        assertThat(response.getWeeklyDistance()).isEqualTo(5.0);
    }

    @Test
    void getMyRanking_throwsWhenUserIsWithdrawn() {
        Long userId = 10L;

        when(userLookupService.getActiveById(userId))
                .thenThrow(new UserException(UserErrorCode.WITHDRAWN_USER));

        assertThatThrownBy(() -> rankingService.getMyRanking(userId))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.WITHDRAWN_USER);
    }

    private User user(Long id, String nickname, UserStatus status) {
        User user = User.builder()
                .status(status)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "nickname", nickname);
        return user;
    }

    private WeeklyUserStats weeklyStats(Long userId, double distanceKm, int avgPaceSecPerKm, double tierScore) {
        BigDecimal distance = BigDecimal.valueOf(distanceKm).setScale(3);
        return WeeklyUserStats.builder()
                .userId(userId)
                .weekStartDate(LocalDate.of(2026, 3, 30))
                .runCount(1)
                .totalDistanceKm(distance)
                .totalDurationSec((int) Math.round(distanceKm * avgPaceSecPerKm))
                .weightedPaceSum(distance.multiply(BigDecimal.valueOf(avgPaceSecPerKm)).setScale(3))
                .avgPaceSecPerKm(avgPaceSecPerKm)
                .tierScoreSum(BigDecimal.valueOf(tierScore))
                .tierScore(BigDecimal.valueOf(tierScore))
                .build();
    }

    private WeeklyRankingRow weeklyRankingRow(
            Long userId,
            String nickname,
            double distanceKm,
            int avgPaceSecPerKm,
            double tierScore
    ) {
        BigDecimal distance = BigDecimal.valueOf(distanceKm).setScale(3);
        return new WeeklyRankingRow() {
            @Override
            public Long getUserId() {
                return userId;
            }

            @Override
            public String getNickname() {
                return nickname;
            }

            @Override
            public BigDecimal getTotalDistanceKm() {
                return distance;
            }

            @Override
            public Integer getAvgPaceSecPerKm() {
                return avgPaceSecPerKm;
            }

            @Override
            public BigDecimal getTierScore() {
                return BigDecimal.valueOf(tierScore);
            }
        };
    }
}
