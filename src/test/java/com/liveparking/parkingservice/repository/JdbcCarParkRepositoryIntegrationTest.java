package com.liveparking.parkingservice.repository;

import com.liveparking.parkingservice.dto.StaticCarParkDto;
import com.liveparking.parkingservice.integration.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "parking.availability.scheduler-enabled=false",
        "spring.task.scheduling.enabled=false"
})
class JdbcCarParkRepositoryIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private JdbcCarParkRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldSaveCarParkAndTransformCoordinatesToWgs84() {
        StaticCarParkDto carPark = new StaticCarParkDto(
                "TEST01",
                "TEST ADDRESS",
                "28018.123",
                "38789.456",
                "MULTI-STOREY CAR PARK",
                "ELECTRONIC",
                "WHOLE DAY",
                "NO",
                "NO",
                5,
                2.1,
                "N"
        );

        repository.save(carPark);

        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM car_parks
                WHERE carpark_number = 'TEST01'
                """,
                Integer.class
        );

        assertThat(count).isEqualTo(1);

        Double latitude = jdbcTemplate.queryForObject(
                """
                SELECT ST_Y(location::geometry)
                FROM car_parks
                WHERE carpark_number = 'TEST01'
                """,
                Double.class
        );

        Double longitude = jdbcTemplate.queryForObject(
                """
                SELECT ST_X(location::geometry)
                FROM car_parks
                WHERE carpark_number = 'TEST01'
                """,
                Double.class
        );

        assertThat(latitude).isBetween(-90.0, 90.0);
        assertThat(longitude).isBetween(-180.0, 180.0);
    }
}