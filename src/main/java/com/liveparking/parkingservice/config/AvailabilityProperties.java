package com.liveparking.parkingservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "parking.availability")
public record AvailabilityProperties(
        long syncIntervalMs,
        long staleAfterSeconds,
        double maxSearchRadiusMeters
) {
}