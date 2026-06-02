package com.healthassist.service.notifier;

/**
 * Local-dev notifier that prints OTPs to the console.
 * Replace later with SMTP/SMS provider integrations.
 */
public class ConsoleNotifier implements Notifier {

    @Override
    public void sendOtp(String destination, String otp) {
        System.out.println("[OTP] Sending OTP to " + destination + " => " + otp);
    }
}
