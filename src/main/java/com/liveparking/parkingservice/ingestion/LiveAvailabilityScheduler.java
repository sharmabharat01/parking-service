package com.liveparking.parkingservice.ingestion;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "parking.availability.scheduler-enabled",
        havingValue = "true"
)
public class LiveAvailabilityScheduler {

    private final LiveAvailabilityIngestionService ingestionService;

    public LiveAvailabilityScheduler(
            LiveAvailabilityIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @Scheduled(
            fixedDelayString =
                    "${parking.availability.sync-interval-ms}"
    )
    public void syncAvailability() {
        ingestionService.ingest();
    }

}