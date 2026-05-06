package com.ohgiraffers.dalryeo.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class AppleOAuthPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withBean(AppleOAuthProperties.class);

    @Test
    void bindsCommaSeparatedAllowedClientIds() {
        contextRunner
                .withPropertyValues("app.oauth.apple.allowed-client-ids=com.dalryeo.dev1,com.dalryeo.dev2")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(AppleOAuthProperties.class).getEffectiveClientIds())
                            .containsExactlyInAnyOrder("com.dalryeo.dev1", "com.dalryeo.dev2");
                });
    }

    @Test
    void bindsLegacyClientIdWhenAllowedClientIdsIsBlank() {
        contextRunner
                .withPropertyValues(
                        "app.oauth.apple.client-id=com.dalryeo.app",
                        "app.oauth.apple.allowed-client-ids="
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(AppleOAuthProperties.class).getEffectiveClientIds())
                            .containsExactly("com.dalryeo.app");
                });
    }

    @Test
    void failsWhenNoClientIdIsConfigured() {
        contextRunner
                .withPropertyValues(
                        "app.oauth.apple.client-id=",
                        "app.oauth.apple.allowed-client-ids="
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCauseOf(context.getStartupFailure()))
                            .hasMessageContaining("At least one Apple OAuth client id must be configured");
                });
    }

    private Throwable rootCauseOf(Throwable throwable) {
        Throwable rootCause = throwable;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        return rootCause;
    }
}
