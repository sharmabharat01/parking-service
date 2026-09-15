package com.liveparking.parkingservice.repository;

import com.liveparking.parkingservice.dto.LiveAvailabilityDto;
import com.liveparking.parkingservice.dto.SyncResult;

public interface AvailabilityRepository {

    SyncResult save(LiveAvailabilityDto availability);
}