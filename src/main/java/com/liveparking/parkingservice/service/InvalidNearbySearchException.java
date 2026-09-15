package com.liveparking.parkingservice.service;

public class InvalidNearbySearchException extends RuntimeException {

    public InvalidNearbySearchException(String message) {
        super(message);
    }
}