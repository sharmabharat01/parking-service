package com.liveparking.parkingservice.dto;

public record ApiErrorResponse(
        String code,
        String message
) {
}