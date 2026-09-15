package com.liveparking.parkingservice.dto;

public record SyncResult(
        int recordsReceived,
        int recordsUpdated,
        int recordsRejected
) {
}