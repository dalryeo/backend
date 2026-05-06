package com.ohgiraffers.dalryeo.config;

import jakarta.validation.constraints.AssertTrue;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "app.oauth.apple")
public class AppleOAuthProperties {

    private String clientId;
    private List<String> allowedClientIds = List.of();
    private String jwkSetUri = "https://appleid.apple.com/auth/keys";
    private Duration jwkCacheTtl = Duration.ofHours(24);
    private Duration jwkReadTimeout = Duration.ofSeconds(2);
    private Duration clockSkew = Duration.ofSeconds(60);

    public void setAllowedClientIds(List<String> allowedClientIds) {
        this.allowedClientIds = normalizeClientIds(allowedClientIds);
    }

    public Set<String> getEffectiveClientIds() {
        Set<String> clientIds = new LinkedHashSet<>();
        addClientIds(clientIds, clientId);
        clientIds.addAll(allowedClientIds);
        return Set.copyOf(clientIds);
    }

    @AssertTrue(message = "At least one Apple OAuth client id must be configured")
    public boolean isClientIdConfigured() {
        return !getEffectiveClientIds().isEmpty();
    }

    private static List<String> normalizeClientIds(List<String> clientIds) {
        if (clientIds == null) {
            return List.of();
        }

        Set<String> normalized = new LinkedHashSet<>();
        for (String clientId : clientIds) {
            addClientIds(normalized, clientId);
        }
        return List.copyOf(normalized);
    }

    private static void addClientIds(Set<String> target, String rawClientIds) {
        if (!StringUtils.hasText(rawClientIds)) {
            return;
        }

        Arrays.stream(rawClientIds.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .forEach(target::add);
    }
}
