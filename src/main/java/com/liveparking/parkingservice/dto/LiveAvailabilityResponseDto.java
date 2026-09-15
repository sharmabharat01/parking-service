package com.liveparking.parkingservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record LiveAvailabilityResponseDto(
        List<Item> items
) {

    public record Item(
            String timestamp,

            @JsonProperty("carpark_data")
            List<CarParkData> carParkData
    ) {
    }

    public record CarParkData(
            @JsonProperty("carpark_number")
            String carParkNumber,

            @JsonProperty("carpark_info")
            List<CarParkInfo> carParkInfo
    ) {
    }

    public record CarParkInfo(
            @JsonProperty("total_lots")
            String totalLots,

            @JsonProperty("lot_type")
            String lotType,

            @JsonProperty("lots_available")
            String lotsAvailable
    ) {
    }
}