package com.liveparking.parkingservice.client;

import com.liveparking.parkingservice.config.ParkingApiProperties;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DataGovStaticCarParkClientTest {

    private MockWebServer mockWebServer;
    private StaticCarParkClient client;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        RestClient restClient = RestClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .build();

        ParkingApiProperties properties =
                new ParkingApiProperties(
                        mockWebServer.url("/").toString(),
                        "test-resource"
                );

        client = new DataGovStaticCarParkClient(
                restClient,
                properties
        );
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void shouldFetchAllCarParksInSingleRequest() throws InterruptedException {

        mockWebServer.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody("""
                            {
                              "success": true,
                              "result": {
                                "resource_id": "test-resource",
                                "fields": [],
                                "records": [
                                  {
                                    "car_park_no": "CP001",
                                    "address": "Test Address 1",
                                    "x_coord": "30314.7936",
                                    "y_coord": "31490.4942",
                                    "car_park_type": "MULTI-STOREY CAR PARK",
                                    "type_of_parking_system": "ELECTRONIC PARKING",
                                    "short_term_parking": "WHOLE DAY",
                                    "free_parking": "NO",
                                    "night_parking": "YES",
                                    "car_park_decks": 5,
                                    "gantry_height": 1.8,
                                    "car_park_basement": "N"
                                  },
                                  {
                                    "car_park_no": "CP002",
                                    "address": "Test Address 2",
                                    "x_coord": "30315.7936",
                                    "y_coord": "31491.4942",
                                    "car_park_type": "MULTI-STOREY CAR PARK",
                                    "type_of_parking_system": "ELECTRONIC PARKING",
                                    "short_term_parking": "WHOLE DAY",
                                    "free_parking": "NO",
                                    "night_parking": "YES",
                                    "car_park_decks": 4,
                                    "gantry_height": 1.8,
                                    "car_park_basement": "N"
                                  }
                                ],
                                "_links": {},
                                "total": 2
                              }
                            }
                            """)
        );

        var carParks = client.fetchCarParks();

        assertNotNull(carParks);
        assertEquals(2, carParks.size());

        assertEquals("CP001", carParks.get(0).carParkNo());
        assertEquals("CP002", carParks.get(1).carParkNo());

        // Verify that only one API request was made.
        assertEquals(1, mockWebServer.getRequestCount());

        RecordedRequest request = mockWebServer.takeRequest();

        assertEquals("GET", request.getMethod());
        assertNotNull(request.getRequestUrl());

        assertEquals(
                "test-resource",
                request.getRequestUrl().queryParameter("resource_id")
        );

        assertEquals(
                "5000",
                request.getRequestUrl().queryParameter("limit")
        );
    }

}