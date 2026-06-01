package com.ohgiraffers.dalryeo.weeklytier.scheduler;

import com.ohgiraffers.dalryeo.weeklytier.service.WeeklyTierFinalizationService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WeeklyTierFinalizationSchedulerTest {

    @Test
    void finalizeWeeklyTiers_delegatesToService() {
        WeeklyTierFinalizationService service = mock(WeeklyTierFinalizationService.class);
        WeeklyTierFinalizationScheduler scheduler = new WeeklyTierFinalizationScheduler(service);

        scheduler.finalizeWeeklyTiers();

        verify(service).finalizeRecentCompletedWeeks();
    }

    @Test
    void finalizeWeeklyTiers_doesNotLeakUnexpectedException() {
        WeeklyTierFinalizationService service = mock(WeeklyTierFinalizationService.class);
        when(service.finalizeRecentCompletedWeeks()).thenThrow(new IllegalStateException("unexpected"));
        WeeklyTierFinalizationScheduler scheduler = new WeeklyTierFinalizationScheduler(service);

        assertThatCode(scheduler::finalizeWeeklyTiers).doesNotThrowAnyException();
    }
}
