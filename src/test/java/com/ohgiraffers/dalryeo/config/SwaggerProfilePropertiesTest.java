package com.ohgiraffers.dalryeo.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class SwaggerProfilePropertiesTest {

    private final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

    @Test
    void defaultConfigurationDisablesSwagger() throws IOException {
        PropertySource<?> properties = load("application.yml");

        assertSwaggerEnabled(properties, "false");
    }

    @Test
    void localConfigurationEnablesSwagger() throws IOException {
        PropertySource<?> properties = load("application-local.yml");

        assertSwaggerEnabled(properties, "true");
    }

    @Test
    void prodConfigurationDisablesSwagger() throws IOException {
        PropertySource<?> properties = load("application-prod.yml");

        assertSwaggerEnabled(properties, "false");
    }

    private PropertySource<?> load(String resourceName) throws IOException {
        return loader.load(resourceName, new ClassPathResource(resourceName)).get(0);
    }

    private void assertSwaggerEnabled(PropertySource<?> properties, String expectedValue) {
        assertProperty(properties, "springdoc.api-docs.enabled", expectedValue);
        assertProperty(properties, "springdoc.swagger-ui.enabled", expectedValue);
    }

    private void assertProperty(PropertySource<?> properties, String key, String expectedValue) {
        assertThat(String.valueOf(properties.getProperty(key))).isEqualTo(expectedValue);
    }
}
