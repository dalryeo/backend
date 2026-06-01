package com.ohgiraffers.dalryeo.weeklytier.service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.ZoneId;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "weekly-tier")
public class WeeklyTierProperties {

    private String zone = "Asia/Seoul";

    public ZoneId zoneId() {
        return ZoneId.of(zone);
    }
}
