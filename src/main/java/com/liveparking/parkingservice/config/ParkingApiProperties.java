package com.liveparking.parkingservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "parking.static-api")
public record ParkingApiProperties(
        String baseUrl,
        String resourceId
) {
}