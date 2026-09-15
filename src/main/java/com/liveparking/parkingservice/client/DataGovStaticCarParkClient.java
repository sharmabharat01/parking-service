package com.liveparking.parkingservice.client;

import com.liveparking.parkingservice.config.ParkingApiProperties;
import com.liveparking.parkingservice.dto.StaticCarParkDto;
import com.liveparking.parkingservice.dto.StaticCarParkResponseDto;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

@Component
public class DataGovStaticCarParkClient
        implements StaticCarParkClient {

    private static final int PAGE_SIZE = 5000;

    private final RestClient restClient;
    private final ParkingApiProperties properties;

    public DataGovStaticCarParkClient(
            RestClient restClient,
            ParkingApiProperties properties) {

        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public List<StaticCarParkDto> fetchCarParks() {

        StaticCarParkResponseDto response =
                restClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/api/action/datastore_search")
                                .queryParam(
                                        "resource_id",
                                        properties.resourceId()
                                )
                                .queryParam(
                                        "limit",
                                        PAGE_SIZE
                                )
                                .build())
                        .retrieve()
                        .body(StaticCarParkResponseDto.class);

        if (response == null
                || response.result() == null
                || response.result().records() == null) {

            throw new IllegalStateException(
                    "Invalid static car park API response"
            );
        }

        return new ArrayList<>(
                response.result().records()
        );
    }
}