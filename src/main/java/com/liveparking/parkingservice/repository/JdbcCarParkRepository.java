package com.liveparking.parkingservice.repository;

import com.liveparking.parkingservice.dto.StaticCarParkDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCarParkRepository implements CarParkRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcCarParkRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(StaticCarParkDto carPark) {

        String sql = """
                INSERT INTO car_parks (
                    carpark_number,
                    address,
                    location,
                    car_park_type,
                    parking_system,
                    short_term_parking,
                    free_parking,
                    night_parking,
                    carpark_decks,
                    gantry_height,
                    carpark_basement
                )
                VALUES (
                    ?,
                    ?,
                    ST_Transform(
                        ST_SetSRID(
                            ST_MakePoint(?, ?),
                            3414
                        ),
                        4326
                    )::geography,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?
                )
                ON CONFLICT (carpark_number)
                DO UPDATE SET
                    address = EXCLUDED.address,
                    location = EXCLUDED.location,
                    car_park_type = EXCLUDED.car_park_type,
                    parking_system = EXCLUDED.parking_system,
                    short_term_parking = EXCLUDED.short_term_parking,
                    free_parking = EXCLUDED.free_parking,
                    night_parking = EXCLUDED.night_parking,
                    carpark_decks = EXCLUDED.carpark_decks,
                    gantry_height = EXCLUDED.gantry_height,
                    carpark_basement = EXCLUDED.carpark_basement,
                    updated_at = CURRENT_TIMESTAMP
                """;

        jdbcTemplate.update(
                sql,
                carPark.carParkNo(),
                carPark.address(),
                Double.parseDouble(carPark.xCoord()),
                Double.parseDouble(carPark.yCoord()),
                carPark.carParkType(),
                carPark.typeOfParkingSystem(),
                carPark.shortTermParking(),
                carPark.freeParking(),
                carPark.nightParking(),
                carPark.carParkDecks(),
                carPark.gantryHeight(),
                carPark.carParkBasement()
        );
    }

    @Override
    public long count() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM car_parks",
                Long.class
        );
    }
}