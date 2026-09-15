package com.liveparking.parkingservice.repository;

import com.liveparking.parkingservice.dto.LiveAvailabilityDto;
import com.liveparking.parkingservice.dto.SyncResult;
import com.liveparking.parkingservice.enums.LotType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Repository
public class JdbcAvailabilityRepository implements AvailabilityRepository {

    private static final String UPSERT_SQL = """
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
            ?,
            ?,
            ?,
            ?,
            CURRENT_TIMESTAMP
        )
        ON CONFLICT (carpark_number, lot_type)
        DO UPDATE SET
            total_lots = EXCLUDED.total_lots,
            available_lots = EXCLUDED.available_lots,
            source_updated_at = EXCLUDED.source_updated_at,
            ingested_at = CURRENT_TIMESTAMP
        WHERE carpark_availability.source_updated_at
              <= EXCLUDED.source_updated_at
        """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcAvailabilityRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public SyncResult save(LiveAvailabilityDto availability) {

        int recordsReceived = 0;
        int recordsRejected = 0;

        List<AvailabilityRecord> validRecords =
                new ArrayList<>();

        Set<String> carParkNumbers =
                new HashSet<>();

        for (LiveAvailabilityDto.CarParkAvailability carPark
                : availability.carParks()) {

            if (carPark == null
                    || carPark.carParkNumber() == null
                    || carPark.carParkNumber().isBlank()
                    || carPark.lots() == null) {

                continue;
            }

            for (LiveAvailabilityDto.LotAvailability lot
                    : carPark.lots()) {

                recordsReceived++;

                if (!isValidLot(lot)) {
                    recordsRejected++;
                    continue;
                }

                validRecords.add(
                        new AvailabilityRecord(
                                carPark.carParkNumber(),
                                lot,
                                availability.sourceUpdatedAt()
                        )
                );

                carParkNumbers.add(
                        carPark.carParkNumber()
                );
            }
        }

        if (validRecords.isEmpty()) {
            return new SyncResult(
                    recordsReceived,
                    0,
                    recordsRejected
            );
        }

        Set<String> existingCarParks =
                findExistingCarParks(carParkNumbers);

        List<AvailabilityRecord> recordsToSave =
                new ArrayList<>();

        for (AvailabilityRecord record : validRecords) {

            if (!existingCarParks.contains(
                    record.carParkNumber())) {

                recordsRejected++;
                continue;
            }

            recordsToSave.add(record);
        }

        int recordsUpdated =
                batchUpsert(recordsToSave);

        return new SyncResult(
                recordsReceived,
                recordsUpdated,
                recordsRejected
        );
    }

    private Set<String> findExistingCarParks(
            Set<String> carParkNumbers) {

        if (carParkNumbers.isEmpty()) {
            return Set.of();
        }

        String placeholders =
                String.join(
                        ", ",
                        carParkNumbers.stream()
                                .map(number -> "?")
                                .toList()
                );

        String sql =
                """
                SELECT carpark_number
                FROM car_parks
                WHERE carpark_number IN (%s)
                """.formatted(placeholders);

        List<String> existing =
                jdbcTemplate.query(
                        sql,
                        (rs, rowNum) ->
                                rs.getString("carpark_number"),
                        carParkNumbers.toArray()
                );

        return new HashSet<>(existing);
    }

    private int batchUpsert(
            List<AvailabilityRecord> records) {

        if (records.isEmpty()) {
            return 0;
        }

        int[] results =
                jdbcTemplate.batchUpdate(
                        UPSERT_SQL,
                        new BatchPreparedStatementSetter() {

                            @Override
                            public void setValues(
                                    PreparedStatement statement,
                                    int index)
                                    throws SQLException {

                                AvailabilityRecord record =
                                        records.get(index);

                                LiveAvailabilityDto.LotAvailability lot =
                                        record.lot();

                                statement.setString(
                                        1,
                                        record.carParkNumber()
                                );

                                statement.setString(
                                        2,
                                        lot.lotType()
                                );

                                statement.setInt(
                                        3,
                                        lot.totalLots()
                                );

                                statement.setInt(
                                        4,
                                        lot.availableLots()
                                );

                                statement.setTimestamp(
                                        5,
                                        Timestamp.from(
                                                record.sourceUpdatedAt()
                                                        .toInstant()
                                        )
                                );
                            }

                            @Override
                            public int getBatchSize() {
                                return records.size();
                            }
                        }
                );

        int updated = 0;

        for (int result : results) {
            if (result > 0) {
                updated++;
            }
        }

        return updated;
    }

    private boolean isValidLot(
            LiveAvailabilityDto.LotAvailability lot) {

        if (lot == null
                || lot.lotType() == null
                || lot.totalLots() == null
                || lot.availableLots() == null) {
            return false;
        }

        try {
            LotType.valueOf(lot.lotType());
        } catch (IllegalArgumentException exception) {
            return false;
        }

        return lot.totalLots() >= 0
                && lot.availableLots() >= 0
                && lot.availableLots() <= lot.totalLots();
    }

    private record AvailabilityRecord(
            String carParkNumber,
            LiveAvailabilityDto.LotAvailability lot,
            OffsetDateTime sourceUpdatedAt) {
    }

}