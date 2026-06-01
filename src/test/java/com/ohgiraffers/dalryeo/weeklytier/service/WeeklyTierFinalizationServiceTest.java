package com.ohgiraffers.dalryeo.weeklytier.service;

import com.ohgiraffers.dalryeo.common.time.ServiceDateProvider;
import com.ohgiraffers.dalryeo.weeklytier.config.WeeklyTierFinalizationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeeklyTierFinalizationServiceTest {

    @Mock
    private WeeklyTierFinalizationTransactionService transactionService;

    private WeeklyTierFinalizationProperties properties;
    private WeeklyTierFinalizationService service;

    @BeforeEach
    void setUp() {
        properties = new WeeklyTierFinalizationProperties();
        properties.setLookbackWeeks(4);
        ServiceDateProvider serviceDateProvider = new ServiceDateProvider("Asia/Seoul");
        service = new WeeklyTierFinalizationService(transactionService, properties, serviceDateProvider);
    }

    @Test
    void finalizeRecentCompletedWeeks_usesMostRecentMondayAndSkipsCurrentWeek() {
        when(transactionService.finalizeWeek(any(), any()))
                .thenAnswer(invocation -> {
                    LocalDate sourceWeekStart = invocation.getArgument(0);
                    LocalDate snapshotWeekStart = invocation.getArgument(1);
                    return new WeeklyTierFinalizationTransactionService.WeekResult(sourceWeekStart, snapshotWeekStart, 2, 1);
                });

        WeeklyTierFinalizationService.Summary summary = service.finalizeRecentCompletedWeeks(
                LocalDate.of(2026, 6, 4)
        );

        InOrder inOrder = inOrder(transactionService);
        inOrder.verify(transactionService).finalizeWeek(LocalDate.of(2026, 5, 25), LocalDate.of(2026, 6, 1));
        inOrder.verify(transactionService).finalizeWeek(LocalDate.of(2026, 5, 18), LocalDate.of(2026, 5, 25));
        inOrder.verify(transactionService).finalizeWeek(LocalDate.of(2026, 5, 11), LocalDate.of(2026, 5, 18));
        inOrder.verify(transactionService).finalizeWeek(LocalDate.of(2026, 5, 4), LocalDate.of(2026, 5, 11));

        assertThat(summary.attemptedWeeks()).isEqualTo(4);
        assertThat(summary.succeededWeeks()).isEqualTo(4);
        assertThat(summary.failedWeeks()).isZero();
        assertThat(summary.totalCandidates()).isEqualTo(8);
        assertThat(summary.totalChanged()).isEqualTo(4);
    }

    @Test
    void finalizeRecentCompletedWeeks_continuesWhenOneWeekFails() {
        properties.setLookbackWeeks(3);
        when(transactionService.finalizeWeek(LocalDate.of(2026, 5, 25), LocalDate.of(2026, 6, 1)))
                .thenReturn(new WeeklyTierFinalizationTransactionService.WeekResult(
                        LocalDate.of(2026, 5, 25),
                        LocalDate.of(2026, 6, 1),
                        3,
                        3
                ));
        when(transactionService.finalizeWeek(LocalDate.of(2026, 5, 18), LocalDate.of(2026, 5, 25)))
                .thenThrow(new IllegalStateException("tier metadata missing"));
        when(transactionService.finalizeWeek(LocalDate.of(2026, 5, 11), LocalDate.of(2026, 5, 18)))
                .thenReturn(new WeeklyTierFinalizationTransactionService.WeekResult(
                        LocalDate.of(2026, 5, 11),
                        LocalDate.of(2026, 5, 18),
                        2,
                        1
                ));

        WeeklyTierFinalizationService.Summary summary = service.finalizeRecentCompletedWeeks(
                LocalDate.of(2026, 6, 1)
        );

        InOrder inOrder = inOrder(transactionService);
        inOrder.verify(transactionService).finalizeWeek(LocalDate.of(2026, 5, 25), LocalDate.of(2026, 6, 1));
        inOrder.verify(transactionService).finalizeWeek(LocalDate.of(2026, 5, 18), LocalDate.of(2026, 5, 25));
        inOrder.verify(transactionService).finalizeWeek(LocalDate.of(2026, 5, 11), LocalDate.of(2026, 5, 18));

        assertThat(summary.attemptedWeeks()).isEqualTo(3);
        assertThat(summary.succeededWeeks()).isEqualTo(2);
        assertThat(summary.failedWeeks()).isEqualTo(1);
        assertThat(summary.totalCandidates()).isEqualTo(5);
        assertThat(summary.totalChanged()).isEqualTo(4);
    }
}
