package com.ohgiraffers.dalryeo.auth.jwt;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtSecretConfigurationTest {

    private static final String JWT_SECRET_PROPERTY = "jwt.secret";
    private static final String TEST_SECRET = "test-only-jwt-secret-not-for-production-2026";

    @Test
    void mainConfigurationFailsToResolveWithoutJwtSecret() throws IOException {
        PropertySourcesPropertyResolver resolver = resolverFor("application.yml");

        assertThatThrownBy(() -> resolver.getRequiredProperty(JWT_SECRET_PROPERTY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void testProfileProvidesDedicatedJwtSecret() throws IOException {
        PropertySourcesPropertyResolver resolver = resolverFor("application-test.yml");

        assertThat(resolver.getRequiredProperty(JWT_SECRET_PROPERTY))
                .isEqualTo(TEST_SECRET)
                .doesNotContain("your-secret-key-change-this-in-production");
    }

    private PropertySourcesPropertyResolver resolverFor(String resourcePath) throws IOException {
        List<PropertySource<?>> loadedSources = new YamlPropertySourceLoader()
                .load(resourcePath, new ClassPathResource(resourcePath));
        MutablePropertySources propertySources = new MutablePropertySources();
        loadedSources.forEach(propertySources::addLast);
        return new PropertySourcesPropertyResolver(propertySources);
    }
}
