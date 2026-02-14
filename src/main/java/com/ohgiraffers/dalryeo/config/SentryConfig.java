package com.ohgiraffers.dalryeo.config;

import io.sentry.SentryOptions;
import io.sentry.protocol.Request;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class SentryConfig {

    @Bean
    public SentryOptions.BeforeSendCallback sentryBeforeSendCallback() {
        return (event, hint) -> {
            Request req = event.getRequest();
            if (req != null) {
                Map<String, String> headers = req.getHeaders();
                if (headers != null) {
                    headers.remove("Authorization");
                    headers.remove("Cookie");
                    headers.remove("Set-Cookie");
                }
                // refresh token이 body로 오가므로 필수
                req.setData(null);
            }
            return event;
        };
    }
}