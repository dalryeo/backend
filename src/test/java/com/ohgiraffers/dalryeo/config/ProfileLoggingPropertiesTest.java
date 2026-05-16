package com.ohgiraffers.dalryeo.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileLoggingPropertiesTest {

    private final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

    @Test
    void defaultConfigurationUsesProductionSafeLoggingAndErrorExposure() throws IOException {
        PropertySource<?> properties = load("application.yml");

        assertProperty(properties, "spring.jpa.show-sql", "false");
        assertProperty(properties, "spring.jpa.properties.hibernate.format_sql", "false");
        assertProperty(properties, "spring.jpa.properties.hibernate.use_sql_comments", "false");
        assertProperty(properties, "server.error.include-message", "never");
        assertProperty(properties, "server.error.include-binding-errors", "never");
        assertProperty(properties, "server.error.include-stacktrace", "never");
        assertProperty(properties, "logging.level.com.ohgiraffers.dalryeo", "INFO");
        assertProperty(properties, "logging.level.org.hibernate.SQL", "OFF");
        assertProperty(properties, "logging.level.org.hibernate.orm.jdbc.bind", "OFF");
        assertProperty(properties, "logging.level.org.hibernate.type.descriptor.sql.BasicBinder", "OFF");
    }

    @Test
    void localConfigurationKeepsDeveloperFriendlyLoggingWithoutBindParameters() throws IOException {
        PropertySource<?> properties = load("application-local.yml");

        assertProperty(properties, "spring.jpa.show-sql", "true");
        assertProperty(properties, "spring.jpa.properties.hibernate.format_sql", "true");
        assertProperty(properties, "spring.jpa.properties.hibernate.use_sql_comments", "true");
        assertProperty(properties, "server.error.include-message", "always");
        assertProperty(properties, "server.error.include-binding-errors", "always");
        assertProperty(properties, "server.error.include-stacktrace", "on_param");
        assertProperty(properties, "logging.level.com.ohgiraffers.dalryeo", "DEBUG");
        assertProperty(properties, "logging.level.org.hibernate.SQL", "DEBUG");
        assertProperty(properties, "logging.level.org.hibernate.orm.jdbc.bind", "OFF");
        assertProperty(properties, "logging.level.org.hibernate.type.descriptor.sql.BasicBinder", "OFF");
    }

    @Test
    void prodConfigurationKeepsSensitiveLoggingDisabled() throws IOException {
        PropertySource<?> properties = load("application-prod.yml");

        assertProperty(properties, "spring.jpa.show-sql", "false");
        assertProperty(properties, "server.error.include-message", "never");
        assertProperty(properties, "server.error.include-binding-errors", "never");
        assertProperty(properties, "server.error.include-stacktrace", "never");
        assertProperty(properties, "logging.level.com.ohgiraffers.dalryeo", "INFO");
        assertProperty(properties, "logging.level.org.hibernate.SQL", "OFF");
        assertProperty(properties, "logging.level.org.hibernate.orm.jdbc.bind", "OFF");
        assertProperty(properties, "logging.level.org.hibernate.type.descriptor.sql.BasicBinder", "OFF");
    }

    private PropertySource<?> load(String resourceName) throws IOException {
        return loader.load(resourceName, new ClassPathResource(resourceName)).get(0);
    }

    private void assertProperty(PropertySource<?> properties, String key, String expectedValue) {
        assertThat(String.valueOf(properties.getProperty(key))).isEqualTo(expectedValue);
    }
}
