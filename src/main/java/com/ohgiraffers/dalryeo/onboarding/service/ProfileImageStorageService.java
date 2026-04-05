package com.ohgiraffers.dalryeo.onboarding.service;

import com.ohgiraffers.dalryeo.config.ProfileImageStorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileImageStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");

    private final ProfileImageStorageProperties profileImageStorageProperties;

    public String storeProfileImage(Long userId, MultipartFile profileImage) {
        validateProfileImage(profileImage);

        Path storageDirectory = getStorageDirectory();
        String extension = extractExtension(profileImage);
        String storedFilename = "user-%d-%s.%s".formatted(userId, UUID.randomUUID(), extension);
        Path targetPath = storageDirectory.resolve(storedFilename).normalize();

        if (!targetPath.startsWith(storageDirectory)) {
            throw new IllegalArgumentException("잘못된 프로필 이미지 경로입니다.");
        }

        try {
            Files.createDirectories(storageDirectory);
            try (InputStream inputStream = profileImage.getInputStream()) {
                Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("프로필 이미지를 저장할 수 없습니다.", e);
        }

        return normalizedUrlPrefix() + "/" + storedFilename;
    }

    public void deleteStoredProfileImage(String profileImageUrl) {
        try {
            resolveManagedImagePath(profileImageUrl).ifPresent(this::deleteFileBestEffort);
        } catch (RuntimeException e) {
            log.warn("프로필 이미지 정리 중 예외가 발생했습니다. profileImageUrl={}", profileImageUrl, e);
        }
    }

    private void validateProfileImage(MultipartFile profileImage) {
        if (profileImage == null || profileImage.isEmpty()) {
            throw new IllegalArgumentException("프로필 이미지 파일은 비어 있을 수 없습니다.");
        }

        String contentType = profileImage.getContentType();
        if (StringUtils.hasText(contentType) && !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("이미지 파일만 업로드할 수 있습니다.");
        }

        extractExtension(profileImage);
    }

    private String extractExtension(MultipartFile profileImage) {
        String originalFilename = StringUtils.cleanPath(
                Optional.ofNullable(profileImage.getOriginalFilename()).orElse("")
        );
        String extension = StringUtils.getFilenameExtension(originalFilename);
        if (!StringUtils.hasText(extension)) {
            throw new IllegalArgumentException("프로필 이미지 파일 확장자가 필요합니다.");
        }

        String normalizedExtension = extension.toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(normalizedExtension)) {
            throw new IllegalArgumentException("지원하지 않는 프로필 이미지 형식입니다.");
        }

        return normalizedExtension;
    }

    private Optional<Path> resolveManagedImagePath(String profileImageUrl) {
        if (!StringUtils.hasText(profileImageUrl)) {
            return Optional.empty();
        }

        String normalizedUrlPrefix = normalizedUrlPrefix();
        String prefixWithSlash = normalizedUrlPrefix + "/";
        if (!profileImageUrl.startsWith(prefixWithSlash)) {
            return Optional.empty();
        }

        String relativePath = profileImageUrl.substring(prefixWithSlash.length());
        if (!StringUtils.hasText(relativePath)) {
            return Optional.empty();
        }

        Path storageDirectory = getStorageDirectory();
        Path resolvedPath = storageDirectory.resolve(relativePath).normalize();
        if (!resolvedPath.startsWith(storageDirectory)) {
            throw new IllegalArgumentException("잘못된 프로필 이미지 경로입니다.");
        }

        return Optional.of(resolvedPath);
    }

    private void deleteFileBestEffort(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("프로필 이미지 파일 삭제에 실패했습니다. path={}", path, e);
        }
    }

    private Path getStorageDirectory() {
        return Path.of(profileImageStorageProperties.getUploadDir())
                .toAbsolutePath()
                .normalize();
    }

    private String normalizedUrlPrefix() {
        String urlPrefix = profileImageStorageProperties.getUrlPrefix();
        if (!StringUtils.hasText(urlPrefix)) {
            return "/profiles/custom";
        }

        String normalized = urlPrefix.startsWith("/") ? urlPrefix : "/" + urlPrefix;
        if (normalized.endsWith("/")) {
            return normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
