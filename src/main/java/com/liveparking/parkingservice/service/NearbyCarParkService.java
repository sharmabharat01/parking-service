package com.liveparking.parkingservice.service;

import com.liveparking.parkingservice.config.AvailabilityProperties;
import com.liveparking.parkingservice.dto.NearbyCarParkDto;
import com.liveparking.parkingservice.dto.NearbyCarParkRequest;
import com.liveparking.parkingservice.repository.NearbyCarParkRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NearbyCarParkService {

    private final NearbyCarParkRepository repository;
    private final AvailabilityProperties availabilityProperties;

    public NearbyCarParkService(
            NearbyCarParkRepository repository,
            AvailabilityProperties availabilityProperties) {
        this.repository = repository;
        this.availabilityProperties = availabilityProperties;
    }

    public List<NearbyCarParkDto> findNearby(
            NearbyCarParkRequest request) {

        validate(request);

        double radius = request.radiusMeters() == null
                ? 1000
                : request.radiusMeters();

        int page = request.page() == null
                ? 0
                : request.page();

        int pageSize = request.pageSize() == null
                ? 20
                : request.pageSize();

        int offset = page * pageSize;

        return repository.findNearby(
                request.latitude(),
                request.longitude(),
                radius,
                offset,
                pageSize,
                availabilityProperties.staleAfterSeconds()
        );
    }

    private void validate(NearbyCarParkRequest request) {

        if (request == null) {
            throw new InvalidNearbySearchException(
                    "Search parameters are required");
        }

        if (request.latitude() == null
                || request.latitude() < -90
                || request.latitude() > 90) {
            throw new InvalidNearbySearchException(
                    "latitude must be between -90 and 90");
        }

        if (request.longitude() == null
                || request.longitude() < -180
                || request.longitude() > 180) {
            throw new InvalidNearbySearchException(
                    "longitude must be between -180 and 180");
        }

        double radius = request.radiusMeters() == null
                ? 1000
                : request.radiusMeters();

        if (radius <= 0) {
            throw new InvalidNearbySearchException(
                    "radiusMeters must be greater than 0");
        }

        if (radius > availabilityProperties.maxSearchRadiusMeters()) {
            throw new InvalidNearbySearchException(
                    "radiusMeters exceeds maximum allowed radius of "
                            + availabilityProperties.maxSearchRadiusMeters()
                            + " meters");
        }

        int page = request.page() == null
                ? 0
                : request.page();

        if (page < 0) {
            throw new InvalidNearbySearchException(
                    "page must be greater than or equal to 0");
        }

        int pageSize = request.pageSize() == null
                ? 20
                : request.pageSize();

        if (pageSize < 1 || pageSize > 100) {
            throw new InvalidNearbySearchException(
                    "pageSize must be between 1 and 100");
        }
    }
}