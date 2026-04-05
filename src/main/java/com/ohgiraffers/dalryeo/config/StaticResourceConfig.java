package com.ohgiraffers.dalryeo.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class StaticResourceConfig implements WebMvcConfigurer {

    private final ProfileImageStorageProperties profileImageStorageProperties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/profiles/tiers/**")
                .addResourceLocations("classpath:/static/profiles/tiers/");

        registry.addResourceHandler(profileImageStorageProperties.getNormalizedUrlPrefix() + "/**")
                .addResourceLocations(profileImageStorageProperties.getStorageLocationUri());
    }
}
