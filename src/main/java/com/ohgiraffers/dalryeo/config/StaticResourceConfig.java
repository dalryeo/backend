package com.ohgiraffers.dalryeo.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
@RequiredArgsConstructor
public class StaticResourceConfig implements WebMvcConfigurer {

    private final ProfileImageStorageProperties profileImageStorageProperties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/profiles/tiers/**")
                .addResourceLocations("classpath:/static/profiles/tiers/");

        registry.addResourceHandler(normalizedUrlPrefix() + "/**")
                .addResourceLocations(profileImageStorageLocation());
    }

    private String profileImageStorageLocation() {
        String location = Path.of(profileImageStorageProperties.getUploadDir())
                .toAbsolutePath()
                .normalize()
                .toUri()
                .toString();
        return location.endsWith("/") ? location : location + "/";
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
