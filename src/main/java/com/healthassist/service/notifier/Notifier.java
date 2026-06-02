package com.healthassist.service.notifier;

/**
 * Sends OTP codes to the given destination (email or phone).
 * In this project, we start with a console notifier.
 */
public interface Notifier {
    void sendOtp(String destination, String otp);
}
