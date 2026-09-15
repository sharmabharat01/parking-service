package com.liveparking.parkingservice.repository;

import com.liveparking.parkingservice.dto.NearbyCarParkDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class JdbcNearbyCarParkRepository
        implements NearbyCarParkRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcNearbyCarParkRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<NearbyCarParkDto> findNearby(
            double latitude,
            double longitude,
            double radiusMeters,
            int offset,
            int limit,
            long staleAfterSeconds) {

        String sql = """
                SELECT
                    cp.carpark_number,
                    cp.address,
                    ST_Y(cp.location::geometry) AS latitude,
                    ST_X(cp.location::geometry) AS longitude,
                    ca.available_lots,
                    ST_Distance(
                        cp.location,
                        ST_SetSRID(
                            ST_MakePoint(?, ?),
                            4326
                        )::geography
                    ) AS distance_meters
                FROM car_parks cp
                JOIN carpark_availability ca
                    ON ca.carpark_number = cp.carpark_number
                WHERE ca.lot_type = 'C'
                  AND ca.available_lots > 0
                  AND ca.source_updated_at >=
                      CURRENT_TIMESTAMP
                      - (? * INTERVAL '1 second')
                  AND ST_DWithin(
                      cp.location,
                      ST_SetSRID(
                          ST_MakePoint(?, ?),
                          4326
                      )::geography,
                      ?
                  )
                ORDER BY distance_meters ASC,
                         cp.carpark_number ASC
                LIMIT ?
                OFFSET ?
                """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new NearbyCarParkDto(
                        rs.getString("carpark_number"),
                        rs.getString("address"),
                        rs.getDouble("latitude"),
                        rs.getDouble("longitude"),
                        rs.getInt("available_lots"),
                        rs.getDouble("distance_meters")
                ),
                longitude,
                latitude,
                staleAfterSeconds,
                longitude,
                latitude,
                radiusMeters,
                limit,
                offset
        );
    }
}