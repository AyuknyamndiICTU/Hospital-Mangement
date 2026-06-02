package com.healthassist.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;

import com.healthassist.dao.OTPDAO;
import com.healthassist.dao.UserDAO;
import com.healthassist.service.notifier.Notifier;

/**
 * OTP lifecycle:
 * - generate OTP
 * - hash OTP deterministically
 * - store otp_hash + expiry
 * - send OTP through notifier
 * - verify OTP and mark account verified
 */
public class OTPService {

    private static final int OTP_LENGTH_DIGITS = 6;
    private static final long OTP_VALIDITY_MINUTES = 5;

    private final OTPDAO otpDAO;
    private final UserDAO userDAO;
    private final Notifier notifier;
    private final SecureRandom secureRandom = new SecureRandom();

    public OTPService(OTPDAO otpDAO, UserDAO userDAO, Notifier notifier) {
        this.otpDAO = otpDAO;
        this.userDAO = userDAO;
        this.notifier = notifier;
    }

    public OTPService() {
        this(new OTPDAO(), new UserDAO(), new com.healthassist.service.notifier.ConsoleNotifier());
    }

    /**
     * Creates and sends a new OTP for the user.
     */
    public boolean requestOtp(int userId, String destination) {
        String otp = generateOtp();
        String otpHash = sha256Hex(otp);

        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(OTP_VALIDITY_MINUTES);

        boolean stored = otpDAO.insertOtp(
            userId,
            destination,
            otpHash,
            expiresAt,
            5
        );

        if (!stored) return false;

        notifier.sendOtp(destination, otp);
        return true;
    }

    /**
     * Verifies OTP and marks user verified.
     */
    public boolean verifyOtpAndActivate(int userId, String otp) {
        String otpHash = sha256Hex(otp);

        LocalDateTime now = LocalDateTime.now();
        boolean ok = otpDAO.verifyOtp(userId, otpHash, now);
        if (!ok) {
            otpDAO.incrementAttempt(userId, now);
            return false;
        }

        return userDAO.markVerified(userId, now);
    }

    private String generateOtp() {
        int max = (int) Math.pow(10, OTP_LENGTH_DIGITS) - 1;
        int min = (int) Math.pow(10, OTP_LENGTH_DIGITS - 1);
        int value = secureRandom.nextInt(max - min + 1) + min;
        return String.valueOf(value);
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash OTP", e);
        }
    }
}
