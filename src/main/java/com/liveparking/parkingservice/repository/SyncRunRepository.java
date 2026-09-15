package com.liveparking.parkingservice.repository;

import com.liveparking.parkingservice.dto.SyncResult;
import com.liveparking.parkingservice.enums.SyncStatus;

import java.time.OffsetDateTime;

public interface SyncRunRepository {

    long start(
            String syncType,
            OffsetDateTime startedAt
    );

    void complete(
            long syncRunId,
            SyncStatus status,
            OffsetDateTime completedAt,
            SyncResult result
    );

    void fail(
            long syncRunId,
            OffsetDateTime completedAt,
            String errorMessage
    );
}