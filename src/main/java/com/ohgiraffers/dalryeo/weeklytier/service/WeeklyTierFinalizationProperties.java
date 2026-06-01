package com.ohgiraffers.dalryeo.weeklytier.service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "weekly-tier.finalization")
public class WeeklyTierFinalizationProperties {

    private boolean enabled = true;
    private String cron = "0 10 0 * * MON";
    private int lookbackWeeks = 4;

    public int safeLookbackWeeks() {
        return Math.max(1, lookbackWeeks);
    }
}
