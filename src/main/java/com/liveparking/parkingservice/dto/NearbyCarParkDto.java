package com.liveparking.parkingservice.dto;

public record NearbyCarParkDto(
        String carParkNumber,
        String address,
        Double latitude,
        Double longitude,
        Integer availableLots,
        Double distanceMeters
) {
}