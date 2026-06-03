package com.healthassist.exception;

/**
 * Thrown when an appointment status change or scheduling validation
 * is rejected (e.g. PENDING -> COMPLETED, past datetime, outside working hours).
 */
public class InvalidTransitionException extends RuntimeException {

    public InvalidTransitionException(String message) {
        super(message);
    }
}
