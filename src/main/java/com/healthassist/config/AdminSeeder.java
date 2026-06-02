package com.healthassist.config;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Seeds a default administrator account if none exists.
 * Credentials are read from config.properties.
 */
public class AdminSeeder {

    public static void seed() {
        Properties props = loadProperties();
        String email = props.getProperty("admin.default.email", "admin@health.com");
        String password = props.getProperty("admin.default.password", "Admin@123");
        String name = props.getProperty("admin.default.name", "System Administrator");

        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();

            // Check if any admin exists
            PreparedStatement checkStmt = conn.prepareStatement(
                "SELECT COUNT(*) FROM users WHERE role = 'ADMIN'"
            );
            ResultSet rs = checkStmt.executeQuery();
            rs.next();
            int adminCount = rs.getInt(1);
            rs.close();
            checkStmt.close();

            if (adminCount == 0) {
                // Hash password
                String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt(12));

                // Insert admin user (auto-verified)
                PreparedStatement insertStmt = conn.prepareStatement(
                    "INSERT INTO users (full_name, email, password_hash, role, is_verified, verified_at) VALUES (?, ?, ?, 'ADMIN', 1, NOW())"
                );
                insertStmt.setString(1, name);
                insertStmt.setString(2, email);
                insertStmt.setString(3, passwordHash);
                insertStmt.executeUpdate();
                insertStmt.close();

                System.out.println("Default admin seeded: " + email);
            } else {
                // If admin already exists, ensure OTP verification fields are set
                try (PreparedStatement upd = conn.prepareStatement(
                    "UPDATE users SET is_verified = 1, verified_at = NOW() WHERE role = 'ADMIN' AND email = ?"
                )) {
                    upd.setString(1, email);
                    upd.executeUpdate();
                }
                System.out.println("Admin user already exists — ensured verified state.");
            }
        } catch (SQLException e) {
            System.err.println("Failed to seed admin: " + e.getMessage());
        } finally {
            if (conn != null) {
                DatabaseConfig.getInstance().releaseConnection(conn);
            }
        }
    }

    private static Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream in = AdminSeeder.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException e) {
            System.err.println("Could not load config.properties: " + e.getMessage());
        }
        return props;
    }
}
