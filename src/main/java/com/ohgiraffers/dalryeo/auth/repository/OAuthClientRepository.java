package com.ohgiraffers.dalryeo.auth.repository;

import com.ohgiraffers.dalryeo.auth.entity.OAuthClient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OAuthClientRepository extends JpaRepository<OAuthClient, Long> {
    Optional<OAuthClient> findByProviderAndProviderId(String provider, String providerId);
}
