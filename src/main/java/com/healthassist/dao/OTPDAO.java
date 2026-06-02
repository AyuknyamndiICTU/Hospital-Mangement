package com.healthassist.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import com.healthassist.config.DatabaseConfig;

/**
 * DAO for otp_verifications lifecycle.
 */
public class OTPDAO {

    public boolean insertOtp(int userId, String destination, String otpHash, LocalDateTime expiresAt, int maxAttempts) {
        String sql = """
            INSERT INTO otp_verifications (user_id, destination, otp_hash, expires_at, attempts_used, max_attempts, consumed)
            VALUES (?, ?, ?, ?, 0, ?, 0)
        """;
        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, userId);
            ps.setString(2, destination);
            ps.setString(3, otpHash);
            ps.setTimestamp(4, Timestamp.valueOf(expiresAt));
            ps.setInt(5, maxAttempts);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("OTPDAO.insertOtp error: " + e.getMessage());
            return false;
        } finally {
            DatabaseConfig.getInstance().releaseConnection(conn);
        }
    }

    public boolean verifyOtp(int userId, String otpHash, LocalDateTime now) {
        String sql = """
            SELECT id, attempts_used, max_attempts, consumed
            FROM otp_verifications
            WHERE user_id = ?
              AND otp_hash = ?
              AND consumed = 0
              AND expires_at > ?
            ORDER BY created_at DESC
            LIMIT 1
        """;

        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, userId);
            ps.setString(2, otpHash);
            ps.setTimestamp(3, Timestamp.valueOf(now));

            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                rs.close();
                ps.close();
                // For mismatch/expired: no update here.
                return false;
            }

            int otpVerificationId = rs.getInt("id");
            int attemptsUsed = rs.getInt("attempts_used");
            int maxAttempts = rs.getInt("max_attempts");
            boolean consumed = rs.getInt("consumed") == 1;
            rs.close();
            ps.close();

            if (consumed) return false;
            if (attemptsUsed >= maxAttempts) return false;

            String consumeSql = "UPDATE otp_verifications SET consumed = 1 WHERE id = ?";
            try (PreparedStatement consumePs = conn.prepareStatement(consumeSql)) {
                consumePs.setInt(1, otpVerificationId);
                return consumePs.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            System.err.println("OTPDAO.verifyOtp error: " + e.getMessage());
            return false;
        } finally {
            DatabaseConfig.getInstance().releaseConnection(conn);
        }
    }

    public void incrementAttempt(int userId, LocalDateTime now) {
        String sql = """
            UPDATE otp_verifications
            SET attempts_used = attempts_used + 1
            WHERE user_id = ?
              AND consumed = 0
              AND expires_at > ?
            ORDER BY created_at DESC
            LIMIT 1
        """;

        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, userId);
            ps.setTimestamp(2, Timestamp.valueOf(now));
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            System.err.println("OTPDAO.incrementAttempt error: " + e.getMessage());
        } finally {
            DatabaseConfig.getInstance().releaseConnection(conn);
        }
    }
}
