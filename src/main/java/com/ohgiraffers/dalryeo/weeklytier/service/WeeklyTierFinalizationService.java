package com.ohgiraffers.dalryeo.weeklytier.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeeklyTierFinalizationService {

    private final WeeklyTierFinalizationTransactionService transactionService;
    private final WeeklyTierFinalizationProperties properties;

    public Summary finalizeRecentCompletedWeeks() {
        return finalizeRecentCompletedWeeks(LocalDate.now(properties.zoneId()));
    }

    Summary finalizeRecentCompletedWeeks(LocalDate today) {
        LocalDate currentWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        int lookbackWeeks = properties.safeLookbackWeeks();
        Summary summary = Summary.empty(lookbackWeeks);

        log.info(
                "weekly_tier.finalization.started lookbackWeeks={} currentWeekStart={}",
                lookbackWeeks,
                currentWeekStart
        );

        for (int weekOffset = 1; weekOffset <= lookbackWeeks; weekOffset++) {
            LocalDate sourceWeekStart = currentWeekStart.minusWeeks(weekOffset);
            LocalDate snapshotWeekStart = sourceWeekStart.plusWeeks(1);
            try {
                WeeklyTierFinalizationTransactionService.WeekResult result = transactionService.finalizeWeek(
                        sourceWeekStart,
                        snapshotWeekStart
                );
                summary = summary.withSuccess(result);
                log.info(
                        "weekly_tier.finalization.week sourceWeekStart={} snapshotWeekStart={} candidates={} changed={}",
                        result.sourceWeekStartDate(),
                        result.snapshotWeekStartDate(),
                        result.candidates(),
                        result.changed()
                );
            } catch (Exception exception) {
                summary = summary.withFailure();
                log.error(
                        "weekly_tier.finalization.week_failed sourceWeekStart={} snapshotWeekStart={} exception={}",
                        sourceWeekStart,
                        snapshotWeekStart,
                        exception.getClass().getSimpleName(),
                        exception
                );
            }
        }

        log.info(
                "weekly_tier.finalization.finished attemptedWeeks={} succeededWeeks={} failedWeeks={} candidates={} changed={}",
                summary.attemptedWeeks(),
                summary.succeededWeeks(),
                summary.failedWeeks(),
                summary.totalCandidates(),
                summary.totalChanged()
        );
        return summary;
    }

    public record Summary(
            int attemptedWeeks,
            int succeededWeeks,
            int failedWeeks,
            int totalCandidates,
            int totalChanged
    ) {

        static Summary empty(int attemptedWeeks) {
            return new Summary(attemptedWeeks, 0, 0, 0, 0);
        }

        Summary withSuccess(WeeklyTierFinalizationTransactionService.WeekResult result) {
            return new Summary(
                    attemptedWeeks,
                    succeededWeeks + 1,
                    failedWeeks,
                    totalCandidates + result.candidates(),
                    totalChanged + result.changed()
            );
        }

        Summary withFailure() {
            return new Summary(
                    attemptedWeeks,
                    succeededWeeks,
                    failedWeeks + 1,
                    totalCandidates,
                    totalChanged
            );
        }
    }
}
