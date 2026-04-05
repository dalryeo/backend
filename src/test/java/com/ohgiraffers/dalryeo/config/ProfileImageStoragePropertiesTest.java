package com.ohgiraffers.dalryeo.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileImageStoragePropertiesTest {

    @Test
    void normalizedUrlPrefix_addsLeadingSlashAndRemovesTrailingSlash() {
        ProfileImageStorageProperties properties = new ProfileImageStorageProperties();
        properties.setUrlPrefix("profiles/custom/");

        assertThat(properties.getNormalizedUrlPrefix()).isEqualTo("/profiles/custom");
    }

    @Test
    void storageLocationUri_appendsTrailingSlash() {
        ProfileImageStorageProperties properties = new ProfileImageStorageProperties();
        properties.setUploadDir("build/test-uploads/non-existent-profile-images");

        String location = properties.getStorageLocationUri();

        assertThat(location).isEqualTo(expectedLocation(properties.getUploadDir()));
        assertThat(location).endsWith("/");
    }

    @Test
    void normalizedUrlPrefix_usesDefaultWhenBlank() {
        ProfileImageStorageProperties properties = new ProfileImageStorageProperties();
        properties.setUrlPrefix("  ");

        assertThat(properties.getNormalizedUrlPrefix()).isEqualTo("/profiles/custom");
    }

    private String expectedLocation(String uploadDir) {
        String location = Path.of(uploadDir)
                .toAbsolutePath()
                .normalize()
                .toUri()
                .toString();
        return location.endsWith("/") ? location : location + "/";
    }
}
