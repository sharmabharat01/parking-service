package com.liveparking.parkingservice.controller;

import com.liveparking.parkingservice.dto.NearbyCarParkDto;
import com.liveparking.parkingservice.dto.NearbyCarParkRequest;
import com.liveparking.parkingservice.service.NearbyCarParkService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/carparks")
public class NearbyCarParkController {

    private final NearbyCarParkService service;

    public NearbyCarParkController(
            NearbyCarParkService service) {
        this.service = service;
    }

    @GetMapping("/nearby")
    public List<NearbyCarParkDto> findNearby(
            @RequestParam Double latitude,
            @RequestParam Double longitude,
            @RequestParam(required = false) Double radiusMeters,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {

        NearbyCarParkRequest request =
                new NearbyCarParkRequest(
                        latitude,
                        longitude,
                        radiusMeters,
                        page,
                        pageSize
                );

        return service.findNearby(request);
    }
}