package com.liveparking.parkingservice.client;

import com.liveparking.parkingservice.config.LiveApiProperties;
import com.liveparking.parkingservice.dto.LiveAvailabilityDto;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class DataGovLiveAvailabilityClientTest {

    private MockWebServer mockWebServer;
    private DataGovLiveAvailabilityClient client;

    @BeforeEach
    void setUp() throws IOException {

        mockWebServer = new MockWebServer();
        mockWebServer.start();

        RestClient restClient =
                RestClient.builder()
                        .baseUrl(
                                mockWebServer.url("/").toString()
                        )
                        .build();

        LiveApiProperties properties =
                new LiveApiProperties(
                        mockWebServer.url("/").toString(),
                        1000,
                        1000,
                        3,
                        1
                );

        client =
                new DataGovLiveAvailabilityClient(
                        restClient,
                        properties
                );
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void shouldFetchAndMapAvailability() throws Exception {

        mockWebServer.enqueue(
                jsonResponse(
                        200,
                        """
                        {
                          "items": [
                            {
                              "timestamp": "2026-09-15T06:00:00+00:00",
                              "carpark_data": [
                                {
                                  "carpark_number": "CP001",
                                  "carpark_info": [
                                    {
                                      "total_lots": "100",
                                      "lot_type": "C",
                                      "lots_available": "50"
                                    },
                                    {
                                      "total_lots": "20",
                                      "lot_type": "Y",
                                      "lots_available": "10"
                                    }
                                  ]
                                }
                              ]
                            }
                          ]
                        }
                        """
                )
        );

        LiveAvailabilityDto result =
                client.fetchAvailability();

        assertNotNull(result);
        assertEquals(
                "2026-09-15T06:00Z",
                result.sourceUpdatedAt().toString()
        );

        assertEquals(1, result.carParks().size());

        LiveAvailabilityDto.CarParkAvailability carPark =
                result.carParks().getFirst();

        assertEquals("CP001", carPark.carParkNumber());
        assertEquals(2, carPark.lots().size());

        assertEquals(
                "C",
                carPark.lots().get(0).lotType()
        );

        assertEquals(
                100,
                carPark.lots().get(0).totalLots()
        );

        assertEquals(
                50,
                carPark.lots().get(0).availableLots()
        );

        assertEquals(1, mockWebServer.getRequestCount());

        RecordedRequest request =
                mockWebServer.takeRequest();

        assertEquals("GET", request.getMethod());

        assertEquals(
                "/v1/transport/carpark-availability",
                request.getPath()
        );
    }

    @Test
    void shouldUseLatestSnapshotWhenMultipleItemsExist() {

        mockWebServer.enqueue(
                jsonResponse(
                        200,
                        """
                        {
                          "items": [
                            {
                              "timestamp": "2026-09-15T05:00:00+00:00",
                              "carpark_data": [
                                {
                                  "carpark_number": "OLD",
                                  "carpark_info": [
                                    {
                                      "total_lots": "100",
                                      "lot_type": "C",
                                      "lots_available": "10"
                                    }
                                  ]
                                }
                              ]
                            },
                            {
                              "timestamp": "2026-09-15T06:00:00+00:00",
                              "carpark_data": [
                                {
                                  "carpark_number": "NEW",
                                  "carpark_info": [
                                    {
                                      "total_lots": "200",
                                      "lot_type": "C",
                                      "lots_available": "80"
                                    }
                                  ]
                                }
                              ]
                            }
                          ]
                        }
                        """
                )
        );

        LiveAvailabilityDto result =
                client.fetchAvailability();

        assertEquals(
                "2026-09-15T06:00Z",
                result.sourceUpdatedAt().toString()
        );

        assertEquals(1, result.carParks().size());

        assertEquals(
                "NEW",
                result.carParks().getFirst().carParkNumber()
        );
    }

    @Test
    void shouldRetryOnServerErrorAndEventuallySucceed()
            throws Exception {

        mockWebServer.enqueue(
                new MockResponse()
                        .setResponseCode(503)
        );

        mockWebServer.enqueue(
                jsonResponse(
                        200,
                        """
                        {
                          "items": [
                            {
                              "timestamp": "2026-09-15T06:00:00+00:00",
                              "carpark_data": []
                            }
                          ]
                        }
                        """
                )
        );

        LiveAvailabilityDto result =
                client.fetchAvailability();

        assertNotNull(result);
        assertEquals(
                "2026-09-15T06:00Z",
                result.sourceUpdatedAt().toString()
        );

        assertEquals(2, mockWebServer.getRequestCount());
    }

    @Test
    void shouldRetryOnTooManyRequestsAndEventuallySucceed()
            throws Exception {

        mockWebServer.enqueue(
                new MockResponse()
                        .setResponseCode(429)
        );

        mockWebServer.enqueue(
                jsonResponse(
                        200,
                        """
                        {
                          "items": [
                            {
                              "timestamp": "2026-09-15T06:00:00+00:00",
                              "carpark_data": []
                            }
                          ]
                        }
                        """
                )
        );

        LiveAvailabilityDto result =
                client.fetchAvailability();

        assertNotNull(result);

        assertEquals(
                2,
                mockWebServer.getRequestCount()
        );
    }

    @Test
    void shouldRetryUntilMaximumAttemptsAreExhausted()
            throws Exception {

        mockWebServer.enqueue(
                new MockResponse()
                        .setResponseCode(503)
        );

        mockWebServer.enqueue(
                new MockResponse()
                        .setResponseCode(503)
        );

        mockWebServer.enqueue(
                new MockResponse()
                        .setResponseCode(503)
        );

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> client.fetchAvailability()
                );

        assertTrue(
                exception.getMessage()
                        .contains("failed after 3 attempts")
        );

        assertEquals(
                3,
                mockWebServer.getRequestCount()
        );
    }

    @Test
    void shouldNotRetryOnNonRetryableClientError()
            throws Exception {

        mockWebServer.enqueue(
                new MockResponse()
                        .setResponseCode(400)
        );

        assertThrows(
                RuntimeException.class,
                () -> client.fetchAvailability()
        );

        assertEquals(
                1,
                mockWebServer.getRequestCount()
        );
    }

    @Test
    void shouldRetryOnTimeout() throws Exception {

        mockWebServer.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setBodyDelay(
                                500,
                                TimeUnit.MILLISECONDS
                        )
                        .setBody(
                                """
                                {
                                  "items": []
                                }
                                """
                        )
        );

        mockWebServer.enqueue(
                jsonResponse(
                        200,
                        """
                        {
                          "items": [
                            {
                              "timestamp": "2026-09-15T06:00:00+00:00",
                              "carpark_data": []
                            }
                          ]
                        }
                        """
                )
        );

        LiveAvailabilityDto result =
                client.fetchAvailability();

        assertNotNull(result);

        assertEquals(
                2,
                mockWebServer.getRequestCount()
        );
    }

    @Test
    void shouldHandleInvalidNumericValues() {

        mockWebServer.enqueue(
                jsonResponse(
                        200,
                        """
                        {
                          "items": [
                            {
                              "timestamp": "2026-09-15T06:00:00+00:00",
                              "carpark_data": [
                                {
                                  "carpark_number": "CP001",
                                  "carpark_info": [
                                    {
                                      "total_lots": "invalid",
                                      "lot_type": "C",
                                      "lots_available": ""
                                    }
                                  ]
                                }
                              ]
                            }
                          ]
                        }
                        """
                )
        );

        LiveAvailabilityDto result =
                client.fetchAvailability();

        assertEquals(1, result.carParks().size());

        LiveAvailabilityDto.LotAvailability lot =
                result.carParks()
                        .getFirst()
                        .lots()
                        .getFirst();

        assertEquals("C", lot.lotType());
        assertNull(lot.totalLots());
        assertNull(lot.availableLots());
    }

    @Test
    void shouldHandleEmptyItems() {

        mockWebServer.enqueue(
                jsonResponse(
                        200,
                        """
                        {
                          "items": []
                        }
                        """
                )
        );

        LiveAvailabilityDto result =
                client.fetchAvailability();

        assertNull(result.sourceUpdatedAt());
        assertTrue(result.carParks().isEmpty());
    }

    private MockResponse jsonResponse(
            int status,
            String body) {

        return new MockResponse()
                .setResponseCode(status)
                .setHeader(
                        "Content-Type",
                        "application/json"
                )
                .setBody(body);
    }

}