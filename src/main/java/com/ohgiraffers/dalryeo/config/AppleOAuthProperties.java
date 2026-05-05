package com.ohgiraffers.dalryeo.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "app.oauth.apple")
public class AppleOAuthProperties {

    @NotBlank
    private String clientId;
    private String jwkSetUri = "https://appleid.apple.com/auth/keys";
    private Duration jwkCacheTtl = Duration.ofHours(24);
    private Duration jwkReadTimeout = Duration.ofSeconds(2);
    private Duration clockSkew = Duration.ofSeconds(60);
}
