package com.liveparking.parkingservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record StaticCarParkResponseDto(
        boolean success,
        Result result
) {
    public record Result(

            @JsonProperty("resource_id")
            String resourceId,

            List<Field> fields,

            List<StaticCarParkDto> records,

            @JsonProperty("_links")
            Links links,

            Integer total
    ) {
    }

    public record Field(
            String type,
            String id
    ) {
    }

    public record Links(
            String start,
            String next
    ) {
    }
}