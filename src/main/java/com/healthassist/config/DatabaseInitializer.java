package com.healthassist.config;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Automatically creates the health_assist database and all required tables
 * on first launch using CREATE TABLE IF NOT EXISTS.
 */
public class DatabaseInitializer {

    public static void initialize() {
        createDatabase();
        createTables();
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
            Statement stmt = conn.createStatement();

            // Users table
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS users (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    full_name VARCHAR(100) NOT NULL,
                    email VARCHAR(100) UNIQUE NOT NULL,
                    password_hash VARCHAR(255) NOT NULL,
                    role ENUM('PATIENT','DOCTOR','ADMIN') NOT NULL,
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

            stmt.close();
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
}
