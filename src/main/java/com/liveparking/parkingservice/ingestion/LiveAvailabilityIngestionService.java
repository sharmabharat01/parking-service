package com.liveparking.parkingservice.ingestion;

import com.liveparking.parkingservice.client.LiveAvailabilityClient;
import com.liveparking.parkingservice.dto.LiveAvailabilityDto;
import com.liveparking.parkingservice.dto.SyncResult;
import com.liveparking.parkingservice.enums.SyncStatus;
import com.liveparking.parkingservice.repository.AvailabilityRepository;
import com.liveparking.parkingservice.repository.SyncRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class LiveAvailabilityIngestionService {

    private static final Logger log =
            LoggerFactory.getLogger(
                    LiveAvailabilityIngestionService.class
            );

    private static final String SYNC_TYPE =
            "LIVE_AVAILABILITY";

    private final LiveAvailabilityClient availabilityClient;
    private final AvailabilityRepository availabilityRepository;
    private final SyncRunRepository syncRunRepository;

    public LiveAvailabilityIngestionService(
            LiveAvailabilityClient availabilityClient,
            AvailabilityRepository availabilityRepository,
            SyncRunRepository syncRunRepository) {

        this.availabilityClient = availabilityClient;
        this.availabilityRepository = availabilityRepository;
        this.syncRunRepository = syncRunRepository;
    }

    public int ingest() {

        OffsetDateTime startedAt =
                OffsetDateTime.now();

        long syncRunId =
                syncRunRepository.start(
                        SYNC_TYPE,
                        startedAt
                );

        try {
            LiveAvailabilityDto availability =
                    availabilityClient.fetchAvailability();

            if (availability == null
                    || availability.sourceUpdatedAt() == null) {

                throw new IllegalStateException(
                        "Live availability response was empty"
                );
            }

            SyncResult result =
                    availabilityRepository.save(
                            availability
                    );

            OffsetDateTime completedAt =
                    OffsetDateTime.now();

            syncRunRepository.complete(
                    syncRunId,
                    SyncStatus.SUCCESS,
                    completedAt,
                    result
            );

            log.info(
                    "Live availability sync completed. " +
                            "syncRunId={}, sourceUpdatedAt={}, " +
                            "recordsReceived={}, recordsUpdated={}, " +
                            "recordsRejected={}",
                    syncRunId,
                    availability.sourceUpdatedAt(),
                    result.recordsReceived(),
                    result.recordsUpdated(),
                    result.recordsRejected()
            );

            return result.recordsUpdated();

        } catch (Exception e) {

            OffsetDateTime completedAt =
                    OffsetDateTime.now();

            syncRunRepository.fail(
                    syncRunId,
                    completedAt,
                    e.getMessage()
            );

            log.error(
                    "Live availability sync failed. syncRunId={}",
                    syncRunId,
                    e
            );

            return 0;
        }
    }
}