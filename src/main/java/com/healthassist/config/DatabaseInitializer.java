package com.healthassist.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Automatically creates the health_assist database and all required tables
 * on first launch.
 *
 * Also ensures schema evolution for OTP/verification columns without requiring
 * manual DB re-creation (best-effort: adds missing columns if they don't exist).
 */
public class DatabaseInitializer {

    public static void initialize() {
        createDatabase();
        createTables();
        ensureOtpAndVerificationColumns();
    }

    /**
     * Create the database if it doesn't exist.
     */
    private static void createDatabase() {
        try (Connection conn = DatabaseConfig.getServerConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS health_assist");
            System.out.println("Database 'health_assist' ensured.");
        } catch (SQLException e) {
            System.err.println("Failed to create database: " + e.getMessage());
        }
    }

    /**
     * Create all tables using the application connection pool.
     */
    private static void createTables() {
        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();

            // Users table (verification fields added for OTP signup flow)
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS users (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        full_name VARCHAR(100) NOT NULL,
                        email VARCHAR(100) UNIQUE NOT NULL,
                        password_hash VARCHAR(255) NOT NULL,
                        role ENUM('PATIENT','DOCTOR','ADMIN') NOT NULL,
                        is_verified TINYINT DEFAULT 0,
                        verified_at DATETIME NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);

                // Patients table
                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS patients (
                        id INT PRIMARY KEY,
                        date_of_birth DATE,
                        blood_type VARCHAR(5),
                        address TEXT,
                        phone VARCHAR(20),
                        emergency_contact VARCHAR(100),
                        FOREIGN KEY (id) REFERENCES users(id) ON DELETE CASCADE
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);

                // Doctors table
                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS doctors (
                        id INT PRIMARY KEY,
                        specialization VARCHAR(100),
                        rate_per_hour DECIMAL(10,2),
                        hospital VARCHAR(150),
                        working_hours VARCHAR(50),
                        FOREIGN KEY (id) REFERENCES users(id) ON DELETE CASCADE
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);

                // Doctor schedule table
                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS doctor_schedule (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        doctor_id INT,
                        day_of_week ENUM('MON','TUE','WED','THU','FRI','SAT','SUN'),
                        start_time TIME,
                        end_time TIME,
                        FOREIGN KEY (doctor_id) REFERENCES doctors(id) ON DELETE CASCADE
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);

                // Appointments table (includes reminder_sent for ReminderService)
                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS appointments (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        patient_id INT,
                        doctor_id INT,
                        appointment_datetime DATETIME NOT NULL,
                        status ENUM('PENDING','CONFIRMED','CANCELLED','COMPLETED') DEFAULT 'PENDING',
                        notes TEXT,
                        reminder_sent TINYINT DEFAULT 0,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (patient_id) REFERENCES patients(id) ON DELETE CASCADE,
                        FOREIGN KEY (doctor_id) REFERENCES doctors(id) ON DELETE CASCADE
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);

                // Health records table
                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS health_records (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        patient_id INT,
                        doctor_id INT,
                        diagnosis TEXT,
                        prescription TEXT,
                        visit_date DATE,
                        FOREIGN KEY (patient_id) REFERENCES patients(id) ON DELETE CASCADE,
                        FOREIGN KEY (doctor_id) REFERENCES doctors(id) ON DELETE CASCADE
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);

                // OTP verification lifecycle table
                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS otp_verifications (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        user_id INT NOT NULL,
                        destination VARCHAR(100) NOT NULL,
                        otp_hash VARCHAR(255) NOT NULL,
                        expires_at DATETIME NOT NULL,
                        attempts_used INT DEFAULT 0,
                        max_attempts INT DEFAULT 5,
                        consumed TINYINT DEFAULT 0,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                        INDEX idx_otp_user_created (user_id, created_at)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
            }

            System.out.println("All tables created/verified successfully.");
        } catch (SQLException e) {
            System.err.println("Failed to create tables: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (conn != null) {
                DatabaseConfig.getInstance().releaseConnection(conn);
            }
        }
    }

    /**
     * Best-effort schema evolution:
     * if users table already exists without verification columns, add them.
     */
    private static void ensureOtpAndVerificationColumns() {
        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();

            ensureUsersColumn(conn, "is_verified", "TINYINT DEFAULT 0");
            ensureUsersColumn(conn, "verified_at", "DATETIME NULL");

        } catch (SQLException e) {
            System.err.println("ensureOtpAndVerificationColumns error: " + e.getMessage());
        } finally {
            if (conn != null) DatabaseConfig.getInstance().releaseConnection(conn);
        }
    }

    private static void ensureUsersColumn(Connection conn, String columnName, String columnDefinition) throws SQLException {
        String sql = """
            SELECT COUNT(*)
            FROM information_schema.columns
            WHERE table_schema = 'health_assist'
              AND table_name = 'users'
              AND column_name = ?
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, columnName);
            var rs = ps.executeQuery();
            int count = 0;
            if (rs.next()) count = rs.getInt(1);
            rs.close();

            if (count == 0) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate("ALTER TABLE users ADD COLUMN " + columnName + " " + columnDefinition);
                    System.out.println("Added missing column users." + columnName);
                }
            }
        }
    }
}
