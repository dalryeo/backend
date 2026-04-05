package com.ohgiraffers.dalryeo.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class StaticResourceConfigTest {

    @Test
    void profileImageStorageLocation_appendsTrailingSlashForDirectoryLocation() {
        ProfileImageStorageProperties properties = new ProfileImageStorageProperties();
        properties.setUploadDir("build/test-uploads/non-existent-profile-images");
        properties.setUrlPrefix("/profiles/custom");
        StaticResourceConfig config = new StaticResourceConfig(properties);

        String location = ReflectionTestUtils.invokeMethod(config, "profileImageStorageLocation");

        assertThat(location).isEqualTo(expectedLocation(properties.getUploadDir()));
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
