package com.liveparking.parkingservice.controller;

import com.liveparking.parkingservice.dto.NearbyCarParkDto;
import com.liveparking.parkingservice.dto.NearbyCarParkRequest;
import com.liveparking.parkingservice.service.InvalidNearbySearchException;
import com.liveparking.parkingservice.service.NearbyCarParkService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NearbyCarParkController.class)
class NearbyCarParkControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NearbyCarParkService service;

    @Test
    void shouldReturnNearbyCarParks() throws Exception {

        NearbyCarParkDto carPark =
                new NearbyCarParkDto(
                        "CP001",
                        "Test Address",
                        1.3525,
                        103.8200,
                        50,
                        100.0
                );

        when(service.findNearby(any(NearbyCarParkRequest.class)))
                .thenReturn(List.of(carPark));

        mockMvc.perform(
                        get("/api/v1/carparks/nearby")
                                .param("latitude", "1.3521")
                                .param("longitude", "103.8198")
                                .param("radiusMeters", "1000")
                                .param("page", "0")
                                .param("pageSize", "20")
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(content().json("""
                    [
                        {
                            "carParkNumber": "CP001",
                            "address": "Test Address",
                            "latitude": 1.3525,
                            "longitude": 103.8200,
                            "availableLots": 50,
                            "distanceMeters": 100.0
                        }
                    ]
                    """));

        verify(service)
                .findNearby(
                        new NearbyCarParkRequest(
                                1.3521,
                                103.8198,
                                1000.0,
                                0,
                                20
                        )
                );
    }

    @Test
    void shouldSupportOptionalParameters() throws Exception {

        when(service.findNearby(any(NearbyCarParkRequest.class)))
                .thenReturn(List.of());

        mockMvc.perform(
                        get("/api/v1/carparks/nearby")
                                .param("latitude", "1.3521")
                                .param("longitude", "103.8198")
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(service)
                .findNearby(
                        new NearbyCarParkRequest(
                                1.3521,
                                103.8198,
                                null,
                                null,
                                null
                        )
                );
    }

    @Test
    void shouldReturnBadRequestWhenLatitudeIsMissing()
            throws Exception {

        mockMvc.perform(
                        get("/api/v1/carparks/nearby")
                                .param("longitude", "103.8198")
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnBadRequestWhenLongitudeIsMissing()
            throws Exception {

        mockMvc.perform(
                        get("/api/v1/carparks/nearby")
                                .param("latitude", "1.3521")
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnBadRequestWhenLatitudeIsNotNumeric()
            throws Exception {

        mockMvc.perform(
                        get("/api/v1/carparks/nearby")
                                .param("latitude", "invalid")
                                .param("longitude", "103.8198")
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnBadRequestWhenLongitudeIsNotNumeric()
            throws Exception {

        mockMvc.perform(
                        get("/api/v1/carparks/nearby")
                                .param("latitude", "1.3521")
                                .param("longitude", "invalid")
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnStructuredBadRequestForInvalidSearchException()
            throws Exception {

        when(service.findNearby(any(NearbyCarParkRequest.class)))
                .thenThrow(
                        new InvalidNearbySearchException(
                                "latitude must be between -90 and 90"
                        )
                );

        mockMvc.perform(
                        get("/api/v1/carparks/nearby")
                                .param("latitude", "91")
                                .param("longitude", "103.8198")
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        content().json("""
                        {
                            "code": "INVALID_REQUEST",
                            "message": "latitude must be between -90 and 90"
                        }
                        """)
                );

    }

    @Test
    void shouldReturnStructuredInternalServerError()
            throws Exception {

        when(service.findNearby(any(NearbyCarParkRequest.class)))
                .thenThrow(
                        new IllegalStateException(
                                "Database connection failed"
                        )
                );

        mockMvc.perform(
                        get("/api/v1/carparks/nearby")
                                .param("latitude", "1.3521")
                                .param("longitude", "103.8198")
                )
                .andExpect(status().isInternalServerError())
                .andExpect(
                        content().json("""
                        {
                            "code": "INTERNAL_ERROR",
                            "message": "An unexpected error occurred"
                        }
                        """)
                );

    }

}