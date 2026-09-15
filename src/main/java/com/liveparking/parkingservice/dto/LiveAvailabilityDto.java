package com.liveparking.parkingservice.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record LiveAvailabilityDto(
        OffsetDateTime sourceUpdatedAt,
        List<CarParkAvailability> carParks
) {

    public record CarParkAvailability(
            String carParkNumber,
            List<LotAvailability> lots
    ) {
    }

    public record LotAvailability(
            String lotType,
            Integer totalLots,
            Integer availableLots
    ) {
    }
}