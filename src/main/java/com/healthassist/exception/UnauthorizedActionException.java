package com.healthassist.exception;

/**
 * Thrown when an actor attempts a mutation or read they are not authorized for.
 * Used by DAO/Service layer to replace silent -1/false returns on RBAC denial.
 */
public class UnauthorizedActionException extends RuntimeException {

    public UnauthorizedActionException(String message) {
        super(message);
    }

    public UnauthorizedActionException(String action, String reason) {
        super("Unauthorized: " + action + " — " + reason);
    }
}
