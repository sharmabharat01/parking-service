package com.liveparking.parkingservice.repository;

import com.liveparking.parkingservice.dto.NearbyCarParkDto;
import com.liveparking.parkingservice.integration.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "parking.availability.scheduler-enabled=false",
        "spring.task.scheduling.enabled=false"
})
class JdbcNearbyCarParkRepositoryIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private JdbcNearbyCarParkRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM carpark_availability");
        jdbcTemplate.update("DELETE FROM car_parks");

        insertCarPark("CP1", 1.35210, 103.81980);
        insertCarPark("CP2", 1.35500, 103.81980);
        insertCarPark("CP3", 1.35700, 103.81980);
        insertCarPark("CP4", 1.37000, 103.81980);

        insertAvailability("CP1", 100, 50, OffsetDateTime.now());
        insertAvailability("CP2", 100, 30, OffsetDateTime.now());
        insertAvailability("CP3", 100, 0, OffsetDateTime.now());
        insertAvailability("CP4", 100, 80, OffsetDateTime.now());
    }

    @Test
    void shouldReturnAvailableCarParksWithinRadiusOrderedByDistance() {
        List<NearbyCarParkDto> results = repository.findNearby(
                1.35210,
                103.81980,
                1000,
                0,
                20,
                300
        );

        assertThat(results)
                .extracting(NearbyCarParkDto::carParkNumber)
                .containsExactly("CP1", "CP2");

        assertThat(results)
                .extracting(NearbyCarParkDto::availableLots)
                .containsExactly(50, 30);

        assertThat(results.get(0).distanceMeters())
                .isLessThan(results.get(1).distanceMeters());
    }

    @Test
    void shouldExcludeCarParksWithNoAvailableLots() {
        List<NearbyCarParkDto> results = repository.findNearby(
                1.35210,
                103.81980,
                1000,
                0,
                20,
                300
        );

        assertThat(results)
                .extracting(NearbyCarParkDto::carParkNumber)
                .doesNotContain("CP3");
    }

    @Test
    void shouldExcludeCarParksOutsideRadius() {
        List<NearbyCarParkDto> results = repository.findNearby(
                1.35210,
                103.81980,
                1000,
                0,
                20,
                300
        );

        assertThat(results)
                .extracting(NearbyCarParkDto::carParkNumber)
                .doesNotContain("CP4");
    }

    @Test
    void shouldExcludeStaleAvailability() {
        jdbcTemplate.update(
                """
                UPDATE carpark_availability
                SET source_updated_at =
                    CURRENT_TIMESTAMP - INTERVAL '10 minutes'
                WHERE carpark_number = 'CP2'
                """
        );

        List<NearbyCarParkDto> results = repository.findNearby(
                1.35210,
                103.81980,
                1000,
                0,
                20,
                300
        );

        assertThat(results)
                .extracting(NearbyCarParkDto::carParkNumber)
                .containsExactly("CP1");
    }

    @Test
    void shouldSupportPagination() {
        List<NearbyCarParkDto> firstPage = repository.findNearby(
                1.35210,
                103.81980,
                1000,
                0,
                1,
                300
        );

        List<NearbyCarParkDto> secondPage = repository.findNearby(
                1.35210,
                103.81980,
                1000,
                1,
                1,
                300
        );

        assertThat(firstPage)
                .extracting(NearbyCarParkDto::carParkNumber)
                .containsExactly("CP1");

        assertThat(secondPage)
                .extracting(NearbyCarParkDto::carParkNumber)
                .containsExactly("CP2");
    }

    private void insertCarPark(
            String carParkNumber,
            double latitude,
            double longitude) {

        jdbcTemplate.update(
                """
                INSERT INTO car_parks (
                    carpark_number,
                    address,
                    location
                )
                VALUES (
                    ?,
                    ?,
                    ST_SetSRID(
                        ST_MakePoint(?, ?),
                        4326
                    )::geography
                )
                """,
                carParkNumber,
                "Test address " + carParkNumber,
                longitude,
                latitude
        );
    }

    private void insertAvailability(
            String carParkNumber,
            int totalLots,
            int availableLots,
            OffsetDateTime sourceUpdatedAt) {

        jdbcTemplate.update(
                """
                INSERT INTO carpark_availability (
                    carpark_number,
                    lot_type,
                    total_lots,
                    available_lots,
                    source_updated_at,
                    ingested_at
                )
                VALUES (
                    ?,
                    'C',
                    ?,
                    ?,
                    ?,
                    CURRENT_TIMESTAMP
                )
                """,
                carParkNumber,
                totalLots,
                availableLots,
                sourceUpdatedAt
        );
    }
}