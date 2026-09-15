package com.liveparking.parkingservice.client;

import com.liveparking.parkingservice.config.LiveApiProperties;
import com.liveparking.parkingservice.dto.LiveAvailabilityDto;
import com.liveparking.parkingservice.dto.LiveAvailabilityResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class DataGovLiveAvailabilityClient
        implements LiveAvailabilityClient {

    private static final Logger log =
            LoggerFactory.getLogger(
                    DataGovLiveAvailabilityClient.class
            );

    private final RestClient restClient;
    private final LiveApiProperties properties;

    public DataGovLiveAvailabilityClient(
            RestClient liveApiRestClient,
            LiveApiProperties properties) {

        this.restClient = liveApiRestClient;
        this.properties = properties;
    }

    @Override
    public LiveAvailabilityDto fetchAvailability() {

        Exception lastException = null;

        for (int attempt = 1;
             attempt <= properties.maxAttempts();
             attempt++) {

            try {

                return fetch(attempt);

            } catch (RetryableLiveApiException e) {

                lastException = e;

                if (attempt == properties.maxAttempts()) {
                    break;
                }

                long backoff =
                        calculateBackoff(attempt);

                log.warn(
                        "Live API request failed with retryable " +
                                "error. attempt={}, maxAttempts={}, " +
                                "backoffMs={}",
                        attempt,
                        properties.maxAttempts(),
                        backoff
                );

                sleep(backoff);

            } catch (NonRetryableLiveApiException e) {

                throw e;
            }
        }

        throw new IllegalStateException(
                "Live availability API failed after "
                        + properties.maxAttempts()
                        + " attempts",
                lastException
        );
    }

    private LiveAvailabilityDto fetch(int attempt) {

        try {

            LiveAvailabilityResponseDto response =
                    restClient.get()
                            .uri("/v1/transport/carpark-availability")
                            .retrieve()
                            .onStatus(
                                    status -> status.value() == 429,
                                    (request, response1) -> {
                                        throw new RetryableLiveApiException(
                                                "Live API returned HTTP 429"
                                        );
                                    }
                            )
                            .onStatus(
                                    HttpStatusCode::is5xxServerError,
                                    (request, response1) -> {
                                        throw new RetryableLiveApiException(
                                                "Live API returned HTTP "
                                                        + response1.getStatusCode()
                                        );
                                    }
                            )
                            .onStatus(
                                    HttpStatusCode::is4xxClientError,
                                    (request, response1) -> {
                                        throw new NonRetryableLiveApiException(
                                                "Live API returned HTTP "
                                                        + response1.getStatusCode()
                                        );
                                    }
                            )
                            .body(LiveAvailabilityResponseDto.class);

            return map(response);

        } catch (RetryableLiveApiException
                 | NonRetryableLiveApiException e) {

            throw e;

        } catch (Exception e) {

            log.warn(
                    "Live API request failed. attempt={}",
                    attempt,
                    e
            );

            throw new RetryableLiveApiException(
                    "Live API request failed",
                    e
            );
        }
    }

    private LiveAvailabilityDto map(
            LiveAvailabilityResponseDto response) {

        if (response == null
                || response.items() == null
                || response.items().isEmpty()) {

            return new LiveAvailabilityDto(
                    null,
                    List.of()
            );
        }

        LiveAvailabilityResponseDto.Item latest =
                response.items().getLast();

        OffsetDateTime sourceUpdatedAt =
                OffsetDateTime.parse(
                        latest.timestamp()
                );

        List<LiveAvailabilityDto.CarParkAvailability>
                carParks = new ArrayList<>();

        if (latest.carParkData() == null) {

            return new LiveAvailabilityDto(
                    sourceUpdatedAt,
                    List.of()
            );
        }

        for (LiveAvailabilityResponseDto.CarParkData carPark
                : latest.carParkData()) {

            List<LiveAvailabilityDto.LotAvailability> lots =
                    new ArrayList<>();

            if (carPark.carParkInfo() != null) {

                for (LiveAvailabilityResponseDto.CarParkInfo info
                        : carPark.carParkInfo()) {

                    Integer totalLots =
                            parseInteger(info.totalLots());

                    Integer availableLots =
                            parseInteger(
                                    info.lotsAvailable()
                            );

                    lots.add(
                            new LiveAvailabilityDto.LotAvailability(
                                    info.lotType(),
                                    totalLots,
                                    availableLots
                            )
                    );
                }
            }

            carParks.add(
                    new LiveAvailabilityDto.CarParkAvailability(
                            carPark.carParkNumber(),
                            lots
                    )
            );
        }

        return new LiveAvailabilityDto(
                sourceUpdatedAt,
                carParks
        );
    }

    private Integer parseInteger(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private long calculateBackoff(int attempt) {

        return properties.initialBackoffMs()
                * (1L << (attempt - 1));
    }

    private void sleep(long milliseconds) {

        try {

            Thread.sleep(milliseconds);

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "Live API retry interrupted",
                    e
            );
        }
    }

    private static class RetryableLiveApiException
            extends RuntimeException {

        private RetryableLiveApiException(
                String message) {
            super(message);
        }

        private RetryableLiveApiException(
                String message,
                Throwable cause) {
            super(message, cause);
        }
    }

    private static class NonRetryableLiveApiException
            extends RuntimeException {

        private NonRetryableLiveApiException(
                String message) {
            super(message);
        }
    }
}