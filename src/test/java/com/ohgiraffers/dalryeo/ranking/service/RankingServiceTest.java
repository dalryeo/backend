package com.ohgiraffers.dalryeo.ranking.service;

import com.ohgiraffers.dalryeo.auth.entity.User;
import com.ohgiraffers.dalryeo.auth.entity.UserStatus;
import com.ohgiraffers.dalryeo.auth.repository.UserRepository;
import com.ohgiraffers.dalryeo.ranking.dto.DistanceRankingResponse;
import com.ohgiraffers.dalryeo.ranking.dto.RankingMeResponse;
import com.ohgiraffers.dalryeo.ranking.dto.ScoreRankingResponse;
import com.ohgiraffers.dalryeo.record.entity.RunningRecord;
import com.ohgiraffers.dalryeo.record.repository.RunningRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RankingServiceTest {

    @Mock
    private RunningRecordRepository runningRecordRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private RankingService rankingService;

    @Test
    void getWeeklyScoreRanking_returnsOnlyActiveRankedUsersInDescendingScoreOrder() {
        User alpha = user(1L, "alpha", UserStatus.NORMAL);
        User beta = user(2L, "beta", UserStatus.NORMAL);
        User withdrawn = user(3L, "withdrawn", UserStatus.WITHDRAWN);
        User noNickname = user(4L, null, UserStatus.NORMAL);

        when(userRepository.findAll()).thenReturn(List.of(alpha, beta, withdrawn, noNickname));
        when(runningRecordRepository.findByWeekRange(any(), any())).thenReturn(List.of(
                record(1L, 5.0, 300),
                record(2L, 10.0, 300),
                record(3L, 20.0, 280)
        ));

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
    }

    @Test
    void getWeeklyDistanceRanking_returnsOnlyActiveRankedUsersInDescendingDistanceOrder() {
        User alpha = user(1L, "alpha", UserStatus.NORMAL);
        User beta = user(2L, "beta", UserStatus.NORMAL);

        when(userRepository.findAll()).thenReturn(List.of(alpha, beta));
        when(runningRecordRepository.findByWeekRange(any(), any())).thenReturn(List.of(
                record(1L, 5.0, 300),
                record(2L, 10.0, 300)
        ));

        List<DistanceRankingResponse> response = rankingService.getWeeklyDistanceRanking();

        assertThat(response).hasSize(2);
        assertThat(response.get(0).getRank()).isEqualTo(1);
        assertThat(response.get(0).getNickname()).isEqualTo("beta");
        assertThat(response.get(0).getWeeklyDistance()).isEqualTo(10.0);
        assertThat(response.get(1).getRank()).isEqualTo(2);
        assertThat(response.get(1).getNickname()).isEqualTo("alpha");
        assertThat(response.get(1).getWeeklyDistance()).isEqualTo(5.0);
    }

    @Test
    void getMyRanking_returnsMyRanksAndWeeklyStats() {
        Long userId = 1L;
        User alpha = user(1L, "alpha", UserStatus.NORMAL);
        User beta = user(2L, "beta", UserStatus.NORMAL);

        when(userRepository.findById(userId)).thenReturn(Optional.of(alpha));
        when(userRepository.findAll()).thenReturn(List.of(alpha, beta));
        when(runningRecordRepository.findByWeekRange(any(), any())).thenReturn(List.of(
                record(1L, 5.0, 300),
                record(2L, 10.0, 300)
        ));

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
        User withdrawn = user(userId, "withdrawn", UserStatus.WITHDRAWN);

        when(userRepository.findById(userId)).thenReturn(Optional.of(withdrawn));

        assertThatThrownBy(() -> rankingService.getMyRanking(userId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("탈퇴한 사용자입니다.");
    }

    private User user(Long id, String nickname, UserStatus status) {
        User user = User.builder()
                .status(status)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "nickname", nickname);
        return user;
    }

    private RunningRecord record(Long userId, double distanceKm, int avgPaceSecPerKm) {
        return RunningRecord.builder()
                .userId(userId)
                .platform("IOS")
                .distanceKm(distanceKm)
                .durationSec((int) Math.round(distanceKm * avgPaceSecPerKm))
                .avgPaceSecPerKm(avgPaceSecPerKm)
                .avgHeartRate(150)
                .caloriesKcal(300)
                .startAt(LocalDateTime.of(2026, 3, 31, 7, 0))
                .endAt(LocalDateTime.of(2026, 3, 31, 7, 30))
                .build();
    }
}
