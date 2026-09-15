package com.liveparking.parkingservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StaticCarParkDto(

        @JsonProperty("car_park_no")
        String carParkNo,

        String address,

        @JsonProperty("x_coord")
        String xCoord,

        @JsonProperty("y_coord")
        String yCoord,

        @JsonProperty("car_park_type")
        String carParkType,

        @JsonProperty("type_of_parking_system")
        String typeOfParkingSystem,

        @JsonProperty("short_term_parking")
        String shortTermParking,

        @JsonProperty("free_parking")
        String freeParking,

        @JsonProperty("night_parking")
        String nightParking,

        @JsonProperty("car_park_decks")
        Integer carParkDecks,

        @JsonProperty("gantry_height")
        Double gantryHeight,

        @JsonProperty("car_park_basement")
        String carParkBasement
) {
}