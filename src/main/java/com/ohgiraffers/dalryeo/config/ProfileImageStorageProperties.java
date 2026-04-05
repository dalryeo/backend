package com.ohgiraffers.dalryeo.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.profile-image")
public class ProfileImageStorageProperties {

    private String uploadDir = "uploads/profile-images";
    private String urlPrefix = "/profiles/custom";
}
