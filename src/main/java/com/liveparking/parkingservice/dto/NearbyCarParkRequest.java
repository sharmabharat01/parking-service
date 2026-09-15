package com.liveparking.parkingservice.dto;

public record NearbyCarParkRequest(
        Double latitude,
        Double longitude,
        Double radiusMeters,
        Integer page,
        Integer pageSize
) {
}