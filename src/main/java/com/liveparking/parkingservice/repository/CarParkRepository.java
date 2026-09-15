package com.liveparking.parkingservice.repository;

import com.liveparking.parkingservice.dto.StaticCarParkDto;

public interface CarParkRepository {

    void save(StaticCarParkDto carPark);

    long count();
}