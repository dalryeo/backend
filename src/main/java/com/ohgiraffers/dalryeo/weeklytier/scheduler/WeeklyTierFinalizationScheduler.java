package com.ohgiraffers.dalryeo.weeklytier.scheduler;

import com.ohgiraffers.dalryeo.weeklytier.service.WeeklyTierFinalizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "weekly-tier.finalization",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class WeeklyTierFinalizationScheduler {

    private final WeeklyTierFinalizationService weeklyTierFinalizationService;

    @Scheduled(
            cron = "${weekly-tier.finalization.cron:0 10 0 * * MON}",
            zone = "${app.time-zone:Asia/Seoul}"
    )
    public void finalizeWeeklyTiers() {
        try {
            weeklyTierFinalizationService.finalizeRecentCompletedWeeks();
        } catch (Exception exception) {
            log.error(
                    "weekly_tier.finalization.failed exception={}",
                    exception.getClass().getSimpleName(),
                    exception
            );
        }
    }
}
