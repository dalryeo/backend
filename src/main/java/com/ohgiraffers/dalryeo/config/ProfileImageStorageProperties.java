package com.ohgiraffers.dalryeo.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Path;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.profile-image")
public class ProfileImageStorageProperties {

    private String uploadDir = "uploads/profile-images";
    private String urlPrefix = "/profiles/custom";

    public Path getUploadDirPath() {
        return Path.of(uploadDir)
                .toAbsolutePath()
                .normalize();
    }

    public String getNormalizedUrlPrefix() {
        if (!StringUtils.hasText(urlPrefix)) {
            return "/profiles/custom";
        }

        String normalized = urlPrefix.startsWith("/") ? urlPrefix : "/" + urlPrefix;
        if (normalized.endsWith("/")) {
            return normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    public String getStorageLocationUri() {
        String location = getUploadDirPath()
                .toUri()
                .toString();
        return location.endsWith("/") ? location : location + "/";
    }
}
