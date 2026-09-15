package com.liveparking.parkingservice.repository;

import com.liveparking.parkingservice.dto.SyncResult;
import com.liveparking.parkingservice.enums.SyncStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.time.OffsetDateTime;

@Repository
public class JdbcSyncRunRepository implements SyncRunRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcSyncRunRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public long start(
            String syncType,
            OffsetDateTime startedAt) {

        String sql = """
                INSERT INTO sync_runs (
                    sync_type,
                    status,
                    started_at
                )
                VALUES (?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(
                connection -> {
                    PreparedStatement statement =
                            connection.prepareStatement(
                                    sql,
                                    new String[]{"id"}
                            );

                    statement.setString(
                            1,
                            syncType
                    );

                    statement.setString(
                            2,
                            SyncStatus.RUNNING.name()
                    );

                    statement.setObject(
                            3,
                            startedAt
                    );

                    return statement;
                },
                keyHolder
        );

        Number key = keyHolder.getKey();

        if (key == null) {
            throw new IllegalStateException(
                    "Failed to create sync run"
            );
        }

        return key.longValue();
    }

    @Override
    public void complete(
            long syncRunId,
            SyncStatus status,
            OffsetDateTime completedAt,
            SyncResult result) {

        String sql = """
                UPDATE sync_runs
                SET
                    status = ?,
                    completed_at = ?,
                    records_received = ?,
                    records_updated = ?,
                    records_rejected = ?
                WHERE id = ?
                """;

        jdbcTemplate.update(
                sql,
                status.name(),
                completedAt,
                result.recordsReceived(),
                result.recordsUpdated(),
                result.recordsRejected(),
                syncRunId
        );
    }

    @Override
    public void fail(
            long syncRunId,
            OffsetDateTime completedAt,
            String errorMessage) {

        String sql = """
                UPDATE sync_runs
                SET
                    status = ?,
                    completed_at = ?,
                    error_message = ?
                WHERE id = ?
                """;

        jdbcTemplate.update(
                sql,
                SyncStatus.FAILED.name(),
                completedAt,
                errorMessage,
                syncRunId
        );
    }
}