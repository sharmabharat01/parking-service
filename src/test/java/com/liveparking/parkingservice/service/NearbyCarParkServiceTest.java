package com.liveparking.parkingservice.service;

import com.liveparking.parkingservice.config.AvailabilityProperties;
import com.liveparking.parkingservice.dto.NearbyCarParkDto;
import com.liveparking.parkingservice.dto.NearbyCarParkRequest;
import com.liveparking.parkingservice.repository.NearbyCarParkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NearbyCarParkServiceTest {

    @Mock
    private NearbyCarParkRepository repository;

    @Mock
    private AvailabilityProperties availabilityProperties;

    private NearbyCarParkService service;

    @BeforeEach
    void setUp() {

        service =
                new NearbyCarParkService(
                        repository,
                        availabilityProperties
                );
    }

    @Test
    void shouldFindNearbyCarParksWithDefaults() {

        when(availabilityProperties.staleAfterSeconds())
                .thenReturn(300L);

        when(availabilityProperties.maxSearchRadiusMeters())
                .thenReturn(5000.0);

        NearbyCarParkRequest request =
                new NearbyCarParkRequest(
                        1.3521,
                        103.8198,
                        null,
                        null,
                        null
                );

        NearbyCarParkDto carPark =
                new NearbyCarParkDto(
                        "CP001",
                        "Test Address",
                        1.3525,
                        103.8200,
                        50,
                        100.0
                );

        when(repository.findNearby(
                1.3521,
                103.8198,
                1000.0,
                0,
                20,
                300L
        )).thenReturn(List.of(carPark));

        List<NearbyCarParkDto> result =
                service.findNearby(request);

        assertEquals(List.of(carPark), result);

        verify(repository)
                .findNearby(
                        1.3521,
                        103.8198,
                        1000.0,
                        0,
                        20,
                        300L
                );
    }

    @Test
    void shouldCalculateCorrectPaginationOffset() {

        when(availabilityProperties.staleAfterSeconds())
                .thenReturn(300L);

        when(availabilityProperties.maxSearchRadiusMeters())
                .thenReturn(5000.0);

        NearbyCarParkRequest request =
                new NearbyCarParkRequest(
                        1.3521,
                        103.8198,
                        2000.0,
                        2,
                        10
                );

        when(repository.findNearby(
                1.3521,
                103.8198,
                2000.0,
                20,
                10,
                300L
        )).thenReturn(List.of());

        List<NearbyCarParkDto> result =
                service.findNearby(request);

        assertEquals(List.of(), result);

        verify(repository)
                .findNearby(
                        1.3521,
                        103.8198,
                        2000.0,
                        20,
                        10,
                        300L
                );
    }

    @Test
    void shouldRejectNullRequest() {

        InvalidNearbySearchException exception =
                assertThrows(
                        InvalidNearbySearchException.class,
                        () -> service.findNearby(null)
                );

        assertEquals(
                "Search parameters are required",
                exception.getMessage()
        );

        verifyNoInteractions(repository);
        verifyNoInteractions(availabilityProperties);
    }

    @Test
    void shouldRejectInvalidLatitude() {

        NearbyCarParkRequest request =
                new NearbyCarParkRequest(
                        91.0,
                        103.8198,
                        1000.0,
                        0,
                        20
                );

        InvalidNearbySearchException exception =
                assertThrows(
                        InvalidNearbySearchException.class,
                        () -> service.findNearby(request)
                );

        assertEquals(
                "latitude must be between -90 and 90",
                exception.getMessage()
        );

        verifyNoInteractions(repository);
        verifyNoInteractions(availabilityProperties);
    }

    @Test
    void shouldRejectInvalidLongitude() {

        NearbyCarParkRequest request =
                new NearbyCarParkRequest(
                        1.3521,
                        181.0,
                        1000.0,
                        0,
                        20
                );

        InvalidNearbySearchException exception =
                assertThrows(
                        InvalidNearbySearchException.class,
                        () -> service.findNearby(request)
                );

        assertEquals(
                "longitude must be between -180 and 180",
                exception.getMessage()
        );

        verifyNoInteractions(repository);
        verifyNoInteractions(availabilityProperties);
    }

    @Test
    void shouldRejectNonPositiveRadius() {

        NearbyCarParkRequest request =
                new NearbyCarParkRequest(
                        1.3521,
                        103.8198,
                        0.0,
                        0,
                        20
                );

        InvalidNearbySearchException exception =
                assertThrows(
                        InvalidNearbySearchException.class,
                        () -> service.findNearby(request)
                );

        assertEquals(
                "radiusMeters must be greater than 0",
                exception.getMessage()
        );

        verifyNoInteractions(repository);
        verifyNoInteractions(availabilityProperties);

    }

    @Test
    void shouldRejectRadiusAboveMaximum() {

        when(availabilityProperties.maxSearchRadiusMeters())
                .thenReturn(5000.0);

        NearbyCarParkRequest request =
                new NearbyCarParkRequest(
                        1.3521,
                        103.8198,
                        5001.0,
                        0,
                        20
                );

        InvalidNearbySearchException exception =
                assertThrows(
                        InvalidNearbySearchException.class,
                        () -> service.findNearby(request)
                );

        assertEquals(
                "radiusMeters exceeds maximum allowed radius of 5000.0 meters",
                exception.getMessage()
        );

        verifyNoInteractions(repository);
    }

    @Test
    void shouldAcceptBoundaryCoordinates() {

        when(availabilityProperties.staleAfterSeconds())
                .thenReturn(300L);

        when(availabilityProperties.maxSearchRadiusMeters())
                .thenReturn(5000.0);

        NearbyCarParkRequest request =
                new NearbyCarParkRequest(
                        -90.0,
                        180.0,
                        1000.0,
                        0,
                        20
                );

        when(repository.findNearby(
                -90.0,
                180.0,
                1000.0,
                0,
                20,
                300L
        )).thenReturn(List.of());

        List<NearbyCarParkDto> result =
                service.findNearby(request);

        assertEquals(List.of(), result);

        verify(repository)
                .findNearby(
                        -90.0,
                        180.0,
                        1000.0,
                        0,
                        20,
                        300L
                );
    }

    @Test
    void shouldRejectNegativePage() {

        when(availabilityProperties.maxSearchRadiusMeters())
                .thenReturn(5000.0);

        NearbyCarParkRequest request =
                new NearbyCarParkRequest(
                        1.3521,
                        103.8198,
                        1000.0,
                        -1,
                        20
                );

        InvalidNearbySearchException exception =
                assertThrows(
                        InvalidNearbySearchException.class,
                        () -> service.findNearby(request)
                );

        assertEquals(
                "page must be greater than or equal to 0",
                exception.getMessage()
        );

        verifyNoInteractions(repository);

    }

    @Test
    void shouldRejectPageSizeBelowOne() {

        when(availabilityProperties.maxSearchRadiusMeters())
                .thenReturn(5000.0);

        NearbyCarParkRequest request =
                new NearbyCarParkRequest(
                        1.3521,
                        103.8198,
                        1000.0,
                        0,
                        0
                );

        InvalidNearbySearchException exception =
                assertThrows(
                        InvalidNearbySearchException.class,
                        () -> service.findNearby(request)
                );

        assertEquals(
                "pageSize must be between 1 and 100",
                exception.getMessage()
        );

        verifyNoInteractions(repository);

    }

    @Test
    void shouldRejectPageSizeAbove100() {

        when(availabilityProperties.maxSearchRadiusMeters())
                .thenReturn(5000.0);

        NearbyCarParkRequest request =
                new NearbyCarParkRequest(
                        1.3521,
                        103.8198,
                        1000.0,
                        0,
                        101
                );

        InvalidNearbySearchException exception =
                assertThrows(
                        InvalidNearbySearchException.class,
                        () -> service.findNearby(request)
                );

        assertEquals(
                "pageSize must be between 1 and 100",
                exception.getMessage()
        );

        verifyNoInteractions(repository);

    }

}