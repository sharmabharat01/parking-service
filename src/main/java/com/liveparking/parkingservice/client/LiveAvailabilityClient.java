package com.liveparking.parkingservice.client;

import com.liveparking.parkingservice.dto.LiveAvailabilityDto;

public interface LiveAvailabilityClient {

    LiveAvailabilityDto fetchAvailability();
}