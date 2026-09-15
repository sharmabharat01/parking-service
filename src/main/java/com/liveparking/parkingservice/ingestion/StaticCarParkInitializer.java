package com.liveparking.parkingservice.ingestion;

import com.liveparking.parkingservice.repository.CarParkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class StaticCarParkInitializer {

    private static final Logger log =
            LoggerFactory.getLogger(StaticCarParkInitializer.class);

    private final CarParkRepository carParkRepository;
    private final StaticCarParkIngestionService ingestionService;

    public StaticCarParkInitializer(
            CarParkRepository carParkRepository,
            StaticCarParkIngestionService ingestionService) {

        this.carParkRepository = carParkRepository;
        this.ingestionService = ingestionService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {

        long existingCount = carParkRepository.count();

        if (existingCount > 0) {
            log.info(
                    "Static car park data already exists. " +
                            "Skipping initialization. count={}",
                    existingCount
            );
            return;
        }

        log.info("No static car park data found. Starting initialization.");

        try {
            int saved = ingestionService.ingest();

            log.info(
                    "Static car park initialization completed. saved={}",
                    saved
            );

        } catch (Exception e) {

            log.error(
                    "Static car park initialization failed. " +
                            "Application will continue running.",
                    e
            );
        }
    }
}