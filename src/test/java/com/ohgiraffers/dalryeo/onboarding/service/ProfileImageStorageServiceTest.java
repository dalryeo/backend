package com.ohgiraffers.dalryeo.onboarding.service;

import com.ohgiraffers.dalryeo.config.ProfileImageStorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfileImageStorageServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void storeProfileImage_savesFileAndReturnsManagedUrl() throws Exception {
        ProfileImageStorageService storageService = new ProfileImageStorageService(storageProperties());
        byte[] imageBytes = "fake-image".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile profileImage = new MockMultipartFile(
                "profileImage",
                "avatar.png",
                "image/png",
                imageBytes
        );

        String imageUrl = storageService.storeProfileImage(1L, profileImage);

        assertThat(imageUrl).startsWith("/profiles/custom/user-1-");
        Path storedFile = tempDirectory.resolve(imageUrl.substring("/profiles/custom/".length()));
        assertThat(Files.exists(storedFile)).isTrue();
        assertThat(Files.readAllBytes(storedFile)).isEqualTo(imageBytes);
    }

    @Test
    void deleteStoredProfileImage_removesManagedFile() {
        ProfileImageStorageService storageService = new ProfileImageStorageService(storageProperties());
        MockMultipartFile profileImage = new MockMultipartFile(
                "profileImage",
                "avatar.png",
                "image/png",
                "fake-image".getBytes(StandardCharsets.UTF_8)
        );

        String imageUrl = storageService.storeProfileImage(2L, profileImage);
        Path storedFile = tempDirectory.resolve(imageUrl.substring("/profiles/custom/".length()));

        storageService.deleteStoredProfileImage(imageUrl);

        assertThat(Files.exists(storedFile)).isFalse();
    }

    @Test
    void storeProfileImage_rejectsUnsupportedExtension() {
        ProfileImageStorageService storageService = new ProfileImageStorageService(storageProperties());
        MockMultipartFile profileImage = new MockMultipartFile(
                "profileImage",
                "avatar.txt",
                "text/plain",
                "not-image".getBytes(StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> storageService.storeProfileImage(3L, profileImage))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미지 파일만 업로드할 수 있습니다.");
    }

    private ProfileImageStorageProperties storageProperties() {
        ProfileImageStorageProperties properties = new ProfileImageStorageProperties();
        properties.setUploadDir(tempDirectory.toString());
        properties.setUrlPrefix("/profiles/custom");
        return properties;
    }
}
