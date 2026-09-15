package com.liveparking.parkingservice.client;

import com.liveparking.parkingservice.dto.StaticCarParkDto;

import java.util.List;

public interface StaticCarParkClient {

    List<StaticCarParkDto> fetchCarParks();
}