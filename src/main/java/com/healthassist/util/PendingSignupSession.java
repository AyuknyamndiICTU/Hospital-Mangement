package com.healthassist.util;

/**
 * Holds the "currently pending" user during OTP verification.
 * This is separate from SessionManager (which represents an authenticated user).
 */
public class PendingSignupSession {

    private static PendingSignupSession instance;

    private int pendingUserId = -1;
    private String pendingDestination = null;

    private PendingSignupSession() {}

    public static synchronized PendingSignupSession getInstance() {
        if (instance == null) {
            instance = new PendingSignupSession();
        }
        return instance;
    }

    public int getPendingUserId() {
        return pendingUserId;
    }

    public void setPendingUserId(int pendingUserId) {
        this.pendingUserId = pendingUserId;
    }

    public String getPendingDestination() {
        return pendingDestination;
    }

    public void setPendingDestination(String pendingDestination) {
        this.pendingDestination = pendingDestination;
    }

    public boolean hasPendingUser() {
        return pendingUserId > 0;
    }

    public void clear() {
        pendingUserId = -1;
        pendingDestination = null;
    }
}
