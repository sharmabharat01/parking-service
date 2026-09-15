package com.liveparking.parkingservice.ingestion;

import com.liveparking.parkingservice.client.StaticCarParkClient;
import com.liveparking.parkingservice.dto.StaticCarParkDto;
import com.liveparking.parkingservice.repository.CarParkRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class StaticCarParkIngestionServiceTest {

    @Test
    void shouldIngestValidCarParks() {

        StaticCarParkClient client = mock(StaticCarParkClient.class);
        CarParkRepository repository = mock(CarParkRepository.class);

        StaticCarParkIngestionService service =
                new StaticCarParkIngestionService(client, repository);

        StaticCarParkDto carPark = new StaticCarParkDto(
                "CP001",
                "Test Address",
                "30314.7936",
                "31490.4942",
                "MULTI-STOREY CAR PARK",
                "ELECTRONIC PARKING",
                "WHOLE DAY",
                "NO",
                "YES",
                5,
                1.8,
                "N"
        );

        when(client.fetchCarParks())
                .thenReturn(List.of(carPark));

        int result = service.ingest();

        assertEquals(1, result);

        verify(repository).save(carPark);
    }

    @Test
    void shouldSkipCarParkWithInvalidCoordinates() {

        StaticCarParkClient client = mock(StaticCarParkClient.class);
        CarParkRepository repository = mock(CarParkRepository.class);

        StaticCarParkIngestionService service =
                new StaticCarParkIngestionService(client, repository);

        StaticCarParkDto invalidCarPark = new StaticCarParkDto(
                "CP001",
                "Test Address",
                "INVALID",
                "31490.4942",
                "MULTI-STOREY CAR PARK",
                "ELECTRONIC PARKING",
                "WHOLE DAY",
                "NO",
                "YES",
                5,
                1.8,
                "N"
        );

        when(client.fetchCarParks())
                .thenReturn(List.of(invalidCarPark));

        int result = service.ingest();

        assertEquals(0, result);

        verify(repository, never()).save(any());
    }
}