package com.ohgiraffers.dalryeo.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class SentryProfilePropertiesTest {

    private final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

    @Test
    void devConfigurationDefaultsSentryEnvironmentToDev() throws IOException {
        PropertySource<?> properties = load("application-dev.yml");

        assertProperty(properties, "sentry.environment", "${SENTRY_ENVIRONMENT:dev}");
    }

    @Test
    void prodConfigurationDefaultsSentryEnvironmentToProd() throws IOException {
        PropertySource<?> properties = load("application-prod.yml");

        assertProperty(properties, "sentry.environment", "${SENTRY_ENVIRONMENT:prod}");
    }

    private PropertySource<?> load(String resourceName) throws IOException {
        return loader.load(resourceName, new ClassPathResource(resourceName)).get(0);
    }

    private void assertProperty(PropertySource<?> properties, String key, String expectedValue) {
        assertThat(String.valueOf(properties.getProperty(key))).isEqualTo(expectedValue);
    }
}
