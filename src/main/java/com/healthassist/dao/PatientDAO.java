package com.healthassist.dao;

import com.healthassist.config.DatabaseConfig;
import com.healthassist.model.Patient;
import com.healthassist.model.User;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for the patients table.
 * Joins with users table to provide full patient data.
 */
public class PatientDAO {

    private final UserDAO userDAO = new UserDAO();

    /**
     * Find a patient by ID (joins users + patients).
     */
    public Patient findById(int id) {
        String sql = """
            SELECT u.*, p.date_of_birth, p.blood_type, p.address, p.phone, p.emergency_contact
            FROM users u JOIN patients p ON u.id = p.id
            WHERE u.id = ?
        """;
        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return mapRow(rs);
            }
            rs.close();
            ps.close();
        } catch (SQLException e) {
            System.err.println("PatientDAO.findById error: " + e.getMessage());
        } finally {
            DatabaseConfig.getInstance().releaseConnection(conn);
        }
        return null;
    }

    /**
     * Find all patients.
     */
    public List<Patient> findAll() {
        List<Patient> patients = new ArrayList<>();
        String sql = """
            SELECT u.*, p.date_of_birth, p.blood_type, p.address, p.phone, p.emergency_contact
            FROM users u JOIN patients p ON u.id = p.id
            ORDER BY u.full_name
        """;
        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                patients.add(mapRow(rs));
            }
            rs.close();
            ps.close();
        } catch (SQLException e) {
            System.err.println("PatientDAO.findAll error: " + e.getMessage());
        } finally {
            DatabaseConfig.getInstance().releaseConnection(conn);
        }
        return patients;
    }

    /**
     * Find patients assigned to a specific doctor (via appointments).
     */
    public List<Patient> findByDoctorId(int doctorId) {
        List<Patient> patients = new ArrayList<>();
        String sql = """
            SELECT DISTINCT u.*, p.date_of_birth, p.blood_type, p.address, p.phone, p.emergency_contact
            FROM users u
            JOIN patients p ON u.id = p.id
            JOIN appointments a ON a.patient_id = p.id
            WHERE a.doctor_id = ?
            ORDER BY u.full_name
        """;
        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, doctorId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                patients.add(mapRow(rs));
            }
            rs.close();
            ps.close();
        } catch (SQLException e) {
            System.err.println("PatientDAO.findByDoctorId error: " + e.getMessage());
        } finally {
            DatabaseConfig.getInstance().releaseConnection(conn);
        }
        return patients;
    }

    /**
     * Save a new patient (inserts into both users and patients tables).
     */
    public boolean save(Patient patient) {
        // First insert into users table
        int userId = userDAO.save(patient);
        if (userId < 0) return false;

        // Then insert into patients table
        String sql = "INSERT INTO patients (id, date_of_birth, blood_type, address, phone, emergency_contact) VALUES (?, ?, ?, ?, ?, ?)";
        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, userId);
            ps.setDate(2, patient.getDateOfBirth() != null ? Date.valueOf(patient.getDateOfBirth()) : null);
            ps.setString(3, patient.getBloodType());
            ps.setString(4, patient.getAddress());
            ps.setString(5, patient.getPhone());
            ps.setString(6, patient.getEmergencyContact());
            ps.executeUpdate();
            ps.close();
            return true;
        } catch (SQLException e) {
            System.err.println("PatientDAO.save error: " + e.getMessage());
            // Rollback: delete the user we just created
            userDAO.delete(userId);
        } finally {
            DatabaseConfig.getInstance().releaseConnection(conn);
        }
        return false;
    }

    /**
     * Update patient details (both users and patients tables).
     */
    public boolean update(Patient patient) {
        // Update users table
        userDAO.update(patient);

        // Update patients table
        String sql = "UPDATE patients SET date_of_birth = ?, blood_type = ?, address = ?, phone = ?, emergency_contact = ? WHERE id = ?";
        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setDate(1, patient.getDateOfBirth() != null ? Date.valueOf(patient.getDateOfBirth()) : null);
            ps.setString(2, patient.getBloodType());
            ps.setString(3, patient.getAddress());
            ps.setString(4, patient.getPhone());
            ps.setString(5, patient.getEmergencyContact());
            ps.setInt(6, patient.getId());
            int rows = ps.executeUpdate();
            ps.close();
            return rows > 0;
        } catch (SQLException e) {
            System.err.println("PatientDAO.update error: " + e.getMessage());
        } finally {
            DatabaseConfig.getInstance().releaseConnection(conn);
        }
        return false;
    }

    /**
     * Delete a patient (cascades via users table FK).
     */
    public boolean delete(int id) {
        return userDAO.delete(id);
    }

    /**
     * Search patients by name.
     */
    public List<Patient> searchByName(String name) {
        List<Patient> patients = new ArrayList<>();
        String sql = """
            SELECT u.*, p.date_of_birth, p.blood_type, p.address, p.phone, p.emergency_contact
            FROM users u JOIN patients p ON u.id = p.id
            WHERE u.full_name LIKE ?
            ORDER BY u.full_name
        """;
        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, "%" + name + "%");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                patients.add(mapRow(rs));
            }
            rs.close();
            ps.close();
        } catch (SQLException e) {
            System.err.println("PatientDAO.searchByName error: " + e.getMessage());
        } finally {
            DatabaseConfig.getInstance().releaseConnection(conn);
        }
        return patients;
    }

    /**
     * Map a ResultSet row to a Patient object.
     */
    private Patient mapRow(ResultSet rs) throws SQLException {
        Patient p = new Patient();
        p.setId(rs.getInt("id"));
        p.setFullName(rs.getString("full_name"));
        p.setEmail(rs.getString("email"));
        p.setPasswordHash(rs.getString("password_hash"));
        p.setRole(User.Role.valueOf(rs.getString("role")));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) p.setCreatedAt(ts.toLocalDateTime());

        Date dob = rs.getDate("date_of_birth");
        if (dob != null) p.setDateOfBirth(dob.toLocalDate());
        p.setBloodType(rs.getString("blood_type"));
        p.setAddress(rs.getString("address"));
        p.setPhone(rs.getString("phone"));
        p.setEmergencyContact(rs.getString("emergency_contact"));
        return p;
    }
}
