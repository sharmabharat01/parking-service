package com.liveparking.parkingservice.repository;

import com.liveparking.parkingservice.dto.NearbyCarParkDto;

import java.util.List;

public interface NearbyCarParkRepository {

    List<NearbyCarParkDto> findNearby(
            double latitude,
            double longitude,
            double radiusMeters,
            int offset,
            int limit,
            long staleAfterSeconds
    );
}