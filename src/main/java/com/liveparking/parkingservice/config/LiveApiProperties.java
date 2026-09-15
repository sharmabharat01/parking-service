package com.liveparking.parkingservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "parking.live-api")
public record LiveApiProperties(
        String baseUrl,
        int connectTimeoutMs,
        int readTimeoutMs,
        int maxAttempts,
        long initialBackoffMs
) {
}