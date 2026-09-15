package com.liveparking.parkingservice.ingestion;

import com.liveparking.parkingservice.client.LiveAvailabilityClient;
import com.liveparking.parkingservice.dto.LiveAvailabilityDto;
import com.liveparking.parkingservice.dto.SyncResult;
import com.liveparking.parkingservice.enums.SyncStatus;
import com.liveparking.parkingservice.repository.AvailabilityRepository;
import com.liveparking.parkingservice.repository.SyncRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LiveAvailabilityIngestionServiceTest {

    @Mock
    private LiveAvailabilityClient availabilityClient;

    @Mock
    private AvailabilityRepository availabilityRepository;

    @Mock
    private SyncRunRepository syncRunRepository;

    private LiveAvailabilityIngestionService service;

    @BeforeEach
    void setUp() {

        service =
                new LiveAvailabilityIngestionService(
                        availabilityClient,
                        availabilityRepository,
                        syncRunRepository
                );
    }

    @Test
    void shouldSuccessfullyIngestAvailability() {

        long syncRunId = 101L;

        OffsetDateTime sourceUpdatedAt =
                OffsetDateTime.parse(
                        "2026-09-15T06:00:00Z"
                );

        LiveAvailabilityDto availability =
                new LiveAvailabilityDto(
                        sourceUpdatedAt,
                        List.of(
                                new LiveAvailabilityDto.CarParkAvailability(
                                        "CP001",
                                        List.of(
                                                new LiveAvailabilityDto.LotAvailability(
                                                        "C",
                                                        100,
                                                        50
                                                )
                                        )
                                )
                        )
                );

        SyncResult syncResult =
                new SyncResult(
                        1,
                        1,
                        0
                );

        when(syncRunRepository.start(
                eq("LIVE_AVAILABILITY"),
                any(OffsetDateTime.class)
        )).thenReturn(syncRunId);

        when(availabilityClient.fetchAvailability())
                .thenReturn(availability);

        when(availabilityRepository.save(availability))
                .thenReturn(syncResult);

        int updated =
                service.ingest();

        assertEquals(1, updated);

        verify(syncRunRepository)
                .start(
                        eq("LIVE_AVAILABILITY"),
                        any(OffsetDateTime.class)
                );

        verify(availabilityClient)
                .fetchAvailability();

        verify(availabilityRepository)
                .save(availability);

        verify(syncRunRepository)
                .complete(
                        eq(syncRunId),
                        eq(SyncStatus.SUCCESS),
                        any(OffsetDateTime.class),
                        eq(syncResult)
                );

        verify(syncRunRepository, never())
                .fail(
                        anyLong(),
                        any(OffsetDateTime.class),
                        anyString()
                );
    }

    @Test
    void shouldMarkSyncAsFailedWhenLiveApiFails() {

        long syncRunId = 102L;

        when(syncRunRepository.start(
                eq("LIVE_AVAILABILITY"),
                any(OffsetDateTime.class)
        )).thenReturn(syncRunId);

        when(availabilityClient.fetchAvailability())
                .thenThrow(
                        new RuntimeException(
                                "Live API unavailable"
                        )
                );

        int updated =
                service.ingest();

        assertEquals(0, updated);

        verify(availabilityClient)
                .fetchAvailability();

        verify(availabilityRepository, never())
                .save(any());

        verify(syncRunRepository)
                .fail(
                        eq(syncRunId),
                        any(OffsetDateTime.class),
                        eq("Live API unavailable")
                );

        verify(syncRunRepository, never())
                .complete(
                        anyLong(),
                        any(SyncStatus.class),
                        any(OffsetDateTime.class),
                        any(SyncResult.class)
                );
    }

    @Test
    void shouldMarkSyncAsFailedWhenResponseHasNoSourceTimestamp() {

        long syncRunId = 103L;

        LiveAvailabilityDto availability =
                new LiveAvailabilityDto(
                        null,
                        List.of()
                );

        when(syncRunRepository.start(
                eq("LIVE_AVAILABILITY"),
                any(OffsetDateTime.class)
        )).thenReturn(syncRunId);

        when(availabilityClient.fetchAvailability())
                .thenReturn(availability);

        int updated =
                service.ingest();

        assertEquals(0, updated);

        verify(availabilityRepository, never())
                .save(any());

        verify(syncRunRepository)
                .fail(
                        eq(syncRunId),
                        any(OffsetDateTime.class),
                        eq("Live availability response was empty")
                );
    }

    @Test
    void shouldMarkSyncAsFailedWhenClientReturnsNull() {

        long syncRunId = 104L;

        when(syncRunRepository.start(
                eq("LIVE_AVAILABILITY"),
                any(OffsetDateTime.class)
        )).thenReturn(syncRunId);

        when(availabilityClient.fetchAvailability())
                .thenReturn(null);

        int updated =
                service.ingest();

        assertEquals(0, updated);

        verify(availabilityRepository, never())
                .save(any());

        verify(syncRunRepository)
                .fail(
                        eq(syncRunId),
                        any(OffsetDateTime.class),
                        eq("Live availability response was empty")
                );
    }

    @Test
    void shouldReturnNumberOfUpdatedRecords() {

        long syncRunId = 105L;

        OffsetDateTime sourceUpdatedAt =
                OffsetDateTime.parse(
                        "2026-09-15T06:00:00Z"
                );

        LiveAvailabilityDto availability =
                new LiveAvailabilityDto(
                        sourceUpdatedAt,
                        List.of()
                );

        SyncResult syncResult =
                new SyncResult(
                        100,
                        87,
                        13
                );

        when(syncRunRepository.start(
                eq("LIVE_AVAILABILITY"),
                any(OffsetDateTime.class)
        )).thenReturn(syncRunId);

        when(availabilityClient.fetchAvailability())
                .thenReturn(availability);

        when(availabilityRepository.save(availability))
                .thenReturn(syncResult);

        int updated =
                service.ingest();

        assertEquals(87, updated);

        verify(syncRunRepository)
                .complete(
                        eq(syncRunId),
                        eq(SyncStatus.SUCCESS),
                        any(OffsetDateTime.class),
                        eq(syncResult)
                );
    }

}