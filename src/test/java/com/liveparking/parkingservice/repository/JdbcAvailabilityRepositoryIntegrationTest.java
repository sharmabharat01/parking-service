package com.liveparking.parkingservice.repository;

import com.liveparking.parkingservice.dto.LiveAvailabilityDto;
import com.liveparking.parkingservice.dto.SyncResult;
import com.liveparking.parkingservice.integration.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "parking.availability.scheduler-enabled=false",
        "spring.task.scheduling.enabled=false"
})
class JdbcAvailabilityRepositoryIntegrationTest
        extends PostgresIntegrationTest {

    @Autowired
    private JdbcAvailabilityRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM carpark_availability");
        jdbcTemplate.update("DELETE FROM car_parks");

        insertCarPark("CP1");
    }

    @Test
    void shouldInsertNewAvailability() {
        OffsetDateTime sourceUpdatedAt =
                OffsetDateTime.now().minusMinutes(1);

        LiveAvailabilityDto availability =
                availability(
                        "CP1",
                        100,
                        50,
                        sourceUpdatedAt
                );

        repository.save(availability);

        Integer availableLots = jdbcTemplate.queryForObject(
                """
                SELECT available_lots
                FROM carpark_availability
                WHERE carpark_number = 'CP1'
                  AND lot_type = 'C'
                """,
                Integer.class
        );

        assertThat(availableLots).isEqualTo(50);
    }

    @Test
    void shouldUpdateWhenIncomingDataIsNewer() {
        OffsetDateTime oldTimestamp =
                OffsetDateTime.now().minusMinutes(5);

        OffsetDateTime newTimestamp =
                OffsetDateTime.now().minusMinutes(1);

        repository.save(
                availability(
                        "CP1",
                        100,
                        50,
                        oldTimestamp
                )
        );

        repository.save(
                availability(
                        "CP1",
                        100,
                        40,
                        newTimestamp
                )
        );

        Integer availableLots = jdbcTemplate.queryForObject(
                """
                SELECT available_lots
                FROM carpark_availability
                WHERE carpark_number = 'CP1'
                  AND lot_type = 'C'
                """,
                Integer.class
        );

        assertThat(availableLots).isEqualTo(40);
    }

    @Test
    void shouldIgnoreOlderIncomingData() {
        OffsetDateTime newerTimestamp =
                OffsetDateTime.now().minusMinutes(1);

        OffsetDateTime olderTimestamp =
                OffsetDateTime.now().minusMinutes(5);

        repository.save(
                availability(
                        "CP1",
                        100,
                        40,
                        newerTimestamp
                )
        );

        repository.save(
                availability(
                        "CP1",
                        100,
                        80,
                        olderTimestamp
                )
        );

        Integer availableLots = jdbcTemplate.queryForObject(
                """
                SELECT available_lots
                FROM carpark_availability
                WHERE carpark_number = 'CP1'
                  AND lot_type = 'C'
                """,
                Integer.class
        );

        OffsetDateTime storedTimestamp = jdbcTemplate.queryForObject(
                """
                SELECT source_updated_at
                FROM carpark_availability
                WHERE carpark_number = 'CP1'
                  AND lot_type = 'C'
                """,
                OffsetDateTime.class
        );

        assertThat(availableLots).isEqualTo(40);
        assertThat(storedTimestamp).isEqualTo(newerTimestamp);
    }

    @Test
    void shouldRejectInvalidAvailability() {
        LiveAvailabilityDto availability =
                availability(
                        "CP1",
                        100,
                        -1,
                        OffsetDateTime.now()
                );

        var result = repository.save(availability);

        assertThat(result.recordsRejected()).isEqualTo(1);

        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM carpark_availability
                WHERE carpark_number = 'CP1'
                """,
                Integer.class
        );

        assertThat(count).isZero();
    }

    @Test
    void shouldIgnoreAvailabilityForUnknownCarPark() {
        LiveAvailabilityDto availability =
                availability(
                        "UNKNOWN",
                        100,
                        50,
                        OffsetDateTime.now()
                );

        var result = repository.save(availability);

        assertThat(result.recordsRejected()).isEqualTo(1);

        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM carpark_availability
                WHERE carpark_number = 'UNKNOWN'
                """,
                Integer.class
        );

        assertThat(count).isZero();
    }

    private LiveAvailabilityDto availability(
            String carParkNumber,
            int totalLots,
            int availableLots,
            OffsetDateTime sourceUpdatedAt) {

        return new LiveAvailabilityDto(
                sourceUpdatedAt,
                List.of(
                        new LiveAvailabilityDto.CarParkAvailability(
                                carParkNumber,
                                List.of(
                                        new LiveAvailabilityDto.LotAvailability(
                                                "C",
                                                totalLots,
                                                availableLots
                                        )
                                )
                        )
                )
        );
    }

    private void insertCarPark(String carParkNumber) {
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
                        ST_MakePoint(103.81980, 1.35210),
                        4326
                    )::geography
                )
                """,
                carParkNumber,
                "Test address " + carParkNumber
        );
    }

    //concurrency test
    @Test
    void shouldIgnoreOlderUpdateWhenItArrivesAfterNewerUpdate() {

        insertCarPark("CP001");

        OffsetDateTime newerTimestamp =
                OffsetDateTime.now().minusMinutes(1);

        OffsetDateTime olderTimestamp =
                OffsetDateTime.now().minusMinutes(2);

        LiveAvailabilityDto newerAvailability =
                new LiveAvailabilityDto(
                        newerTimestamp,
                        List.of(
                                new LiveAvailabilityDto.CarParkAvailability(
                                        "CP001",
                                        List.of(
                                                new LiveAvailabilityDto.LotAvailability(
                                                        "C",
                                                        100,
                                                        80
                                                )
                                        )
                                )
                        )
                );

        LiveAvailabilityDto olderAvailability =
                new LiveAvailabilityDto(
                        olderTimestamp,
                        List.of(
                                new LiveAvailabilityDto.CarParkAvailability(
                                        "CP001",
                                        List.of(
                                                new LiveAvailabilityDto.LotAvailability(
                                                        "C",
                                                        100,
                                                        20
                                                )
                                        )
                                )
                        )
                );

        SyncResult newerResult =
                repository.save(newerAvailability);

        SyncResult olderResult =
                repository.save(olderAvailability);

        assertEquals(1, newerResult.recordsUpdated());
        assertEquals(0, olderResult.recordsUpdated());

        Integer availableLots =
                jdbcTemplate.queryForObject(
                        """
                        SELECT available_lots
                        FROM carpark_availability
                        WHERE carpark_number = ?
                          AND lot_type = 'C'
                        """,
                        Integer.class,
                        "CP001"
                );

        assertEquals(80, availableLots);

        OffsetDateTime storedTimestamp =
                jdbcTemplate.queryForObject(
                        """
                        SELECT source_updated_at
                        FROM carpark_availability
                        WHERE carpark_number = ?
                          AND lot_type = 'C'
                        """,
                        OffsetDateTime.class,
                        "CP001"
                );

        assertEquals(
                newerTimestamp.toInstant(),
                storedTimestamp.toInstant()
        );

    }

    private LiveAvailabilityDto createAvailability(
            String carParkNumber,
            OffsetDateTime sourceUpdatedAt,
            int availableLots) {

        return new LiveAvailabilityDto(
                sourceUpdatedAt,
                List.of(
                        new LiveAvailabilityDto.CarParkAvailability(
                                carParkNumber,
                                List.of(
                                        new LiveAvailabilityDto.LotAvailability(
                                                "C",
                                                100,
                                                availableLots
                                        )
                                )
                        )
                )
        );

    }

    //multithread test
    @Test
    void shouldKeepNewestUpdateWhenUpdatesArriveConcurrently()
            throws Exception {

        insertCarPark("CP001");

        OffsetDateTime olderTimestamp =
                OffsetDateTime.now().minusMinutes(2);

        OffsetDateTime newerTimestamp =
                OffsetDateTime.now().minusMinutes(1);

        LiveAvailabilityDto olderAvailability =
                createAvailability(
                        "CP001",
                        olderTimestamp,
                        20
                );

        LiveAvailabilityDto newerAvailability =
                createAvailability(
                        "CP001",
                        newerTimestamp,
                        80
                );

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        try {

            CountDownLatch startLatch =
                    new CountDownLatch(1);

            Future<SyncResult> olderResult =
                    executor.submit(() -> {
                        startLatch.await();
                        return repository.save(olderAvailability);
                    });

            Future<SyncResult> newerResult =
                    executor.submit(() -> {
                        startLatch.await();
                        return repository.save(newerAvailability);
                    });

            startLatch.countDown();

            olderResult.get();
            newerResult.get();

        } finally {
            executor.shutdown();
        }

        Integer availableLots =
                jdbcTemplate.queryForObject(
                        """
                        SELECT available_lots
                        FROM carpark_availability
                        WHERE carpark_number = ?
                          AND lot_type = 'C'
                        """,
                        Integer.class,
                        "CP001"
                );

        OffsetDateTime storedTimestamp =
                jdbcTemplate.queryForObject(
                        """
                        SELECT source_updated_at
                        FROM carpark_availability
                        WHERE carpark_number = ?
                          AND lot_type = 'C'
                        """,
                        OffsetDateTime.class,
                        "CP001"
                );

        assertEquals(80, availableLots);

        assertEquals(
                newerTimestamp.toInstant(),
                storedTimestamp.toInstant()
        );

    }
    @Test
    void shouldRejectInvalidLotWithoutAffectingValidLots() {

        insertCarPark("CP001");

        OffsetDateTime timestamp =
                OffsetDateTime.now();

        LiveAvailabilityDto availability =
                new LiveAvailabilityDto(
                        timestamp,
                        List.of(
                                new LiveAvailabilityDto.CarParkAvailability(
                                        "CP001",
                                        List.of(
                                                new LiveAvailabilityDto.LotAvailability(
                                                        "C",
                                                        100,
                                                        80
                                                ),
                                                new LiveAvailabilityDto.LotAvailability(
                                                        "C",
                                                        100,
                                                        150
                                                )
                                        )
                                )
                        )
                );

        SyncResult result =
                repository.save(availability);

        assertEquals(2, result.recordsReceived());
        assertEquals(1, result.recordsUpdated());
        assertEquals(1, result.recordsRejected());

        Integer availableLots =
                jdbcTemplate.queryForObject(
                        """
                        SELECT available_lots
                        FROM carpark_availability
                        WHERE carpark_number = ?
                          AND lot_type = 'C'
                        """,
                        Integer.class,
                        "CP001"
                );

        assertEquals(80, availableLots);

    }

    @Test
    void shouldRejectUnknownCarParkWithoutFailingValidRecords() {

        insertCarPark("CP001");

        OffsetDateTime timestamp =
                OffsetDateTime.now();

        LiveAvailabilityDto availability =
                new LiveAvailabilityDto(
                        timestamp,
                        List.of(
                                new LiveAvailabilityDto.CarParkAvailability(
                                        "CP001",
                                        List.of(
                                                new LiveAvailabilityDto.LotAvailability(
                                                        "C",
                                                        100,
                                                        70
                                                )
                                        )
                                ),
                                new LiveAvailabilityDto.CarParkAvailability(
                                        "UNKNOWN",
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

        SyncResult result =
                repository.save(availability);

        assertEquals(2, result.recordsReceived());
        assertEquals(1, result.recordsUpdated());
        assertEquals(1, result.recordsRejected());

        Integer availableLots =
                jdbcTemplate.queryForObject(
                        """
                        SELECT available_lots
                        FROM carpark_availability
                        WHERE carpark_number = ?
                          AND lot_type = 'C'
                        """,
                        Integer.class,
                        "CP001"
                );

        assertEquals(70, availableLots);

    }

    @Test
    void shouldRejectUnknownLotType() {

        insertCarPark("CP001");

        OffsetDateTime timestamp =
                OffsetDateTime.now();

        LiveAvailabilityDto availability =
                new LiveAvailabilityDto(
                        timestamp,
                        List.of(
                                new LiveAvailabilityDto.CarParkAvailability(
                                        "CP001",
                                        List.of(
                                                new LiveAvailabilityDto.LotAvailability(
                                                        "INVALID",
                                                        100,
                                                        50
                                                )
                                        )
                                )
                        )
                );

        SyncResult result =
                repository.save(availability);

        assertEquals(1, result.recordsReceived());
        assertEquals(0, result.recordsUpdated());
        assertEquals(1, result.recordsRejected());

        Integer availabilityCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM carpark_availability
                        WHERE carpark_number = ?
                        """,
                        Integer.class,
                        "CP001"
                );

        assertEquals(0, availabilityCount);

    }
}