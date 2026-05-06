package com.healthassist.dao;

import com.healthassist.config.DatabaseConfig;
import com.healthassist.model.Doctor;
import com.healthassist.model.User;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

public class DoctorDAO {
    private final UserDAO userDAO = new UserDAO();

    public Doctor findById(int id) {
        String sql = "SELECT u.*, d.specialization, d.rate_per_hour, d.hospital, d.working_hours FROM users u JOIN doctors d ON u.id = d.id WHERE u.id = ?";
        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRow(rs);
            rs.close(); ps.close();
        } catch (SQLException e) {
            System.err.println("DoctorDAO.findById error: " + e.getMessage());
        } finally { DatabaseConfig.getInstance().releaseConnection(conn); }
        return null;
    }

    public List<Doctor> findAll() {
        List<Doctor> doctors = new ArrayList<>();
        String sql = "SELECT u.*, d.specialization, d.rate_per_hour, d.hospital, d.working_hours FROM users u JOIN doctors d ON u.id = d.id ORDER BY u.full_name";
        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) doctors.add(mapRow(rs));
            rs.close(); ps.close();
        } catch (SQLException e) {
            System.err.println("DoctorDAO.findAll error: " + e.getMessage());
        } finally { DatabaseConfig.getInstance().releaseConnection(conn); }
        return doctors;
    }

    public boolean save(Doctor doctor) {
        int userId = userDAO.save(doctor);
        if (userId < 0) return false;
        String sql = "INSERT INTO doctors (id, specialization, rate_per_hour, hospital, working_hours) VALUES (?, ?, ?, ?, ?)";
        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, userId);
            ps.setString(2, doctor.getSpecialization());
            ps.setBigDecimal(3, doctor.getRatePerHour() != null ? doctor.getRatePerHour() : BigDecimal.ZERO);
            ps.setString(4, doctor.getHospital());
            ps.setString(5, doctor.getWorkingHours());
            ps.executeUpdate(); ps.close();
            return true;
        } catch (SQLException e) {
            System.err.println("DoctorDAO.save error: " + e.getMessage());
            userDAO.delete(userId);
        } finally { DatabaseConfig.getInstance().releaseConnection(conn); }
        return false;
    }

    public boolean update(Doctor doctor) {
        userDAO.update(doctor);
        String sql = "UPDATE doctors SET specialization = ?, rate_per_hour = ?, hospital = ?, working_hours = ? WHERE id = ?";
        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, doctor.getSpecialization());
            ps.setBigDecimal(2, doctor.getRatePerHour() != null ? doctor.getRatePerHour() : BigDecimal.ZERO);
            ps.setString(3, doctor.getHospital());
            ps.setString(4, doctor.getWorkingHours());
            ps.setInt(5, doctor.getId());
            int rows = ps.executeUpdate(); ps.close();
            return rows > 0;
        } catch (SQLException e) {
            System.err.println("DoctorDAO.update error: " + e.getMessage());
        } finally { DatabaseConfig.getInstance().releaseConnection(conn); }
        return false;
    }

    public boolean delete(int id) { return userDAO.delete(id); }

    public List<Map<String, String>> getSchedule(int doctorId) {
        List<Map<String, String>> schedule = new ArrayList<>();
        String sql = "SELECT day_of_week, start_time, end_time FROM doctor_schedule WHERE doctor_id = ? ORDER BY FIELD(day_of_week,'MON','TUE','WED','THU','FRI','SAT','SUN')";
        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, doctorId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, String> entry = new HashMap<>();
                entry.put("day", rs.getString("day_of_week"));
                entry.put("start", rs.getTime("start_time").toString());
                entry.put("end", rs.getTime("end_time").toString());
                schedule.add(entry);
            }
            rs.close(); ps.close();
        } catch (SQLException e) {
            System.err.println("DoctorDAO.getSchedule error: " + e.getMessage());
        } finally { DatabaseConfig.getInstance().releaseConnection(conn); }
        return schedule;
    }

    public boolean saveSchedule(int doctorId, List<Map<String, String>> schedule) {
        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();
            PreparedStatement delPs = conn.prepareStatement("DELETE FROM doctor_schedule WHERE doctor_id = ?");
            delPs.setInt(1, doctorId); delPs.executeUpdate(); delPs.close();
            String sql = "INSERT INTO doctor_schedule (doctor_id, day_of_week, start_time, end_time) VALUES (?, ?, ?, ?)";
            PreparedStatement ps = conn.prepareStatement(sql);
            for (Map<String, String> entry : schedule) {
                ps.setInt(1, doctorId);
                ps.setString(2, entry.get("day"));
                ps.setString(3, entry.get("start"));
                ps.setString(4, entry.get("end"));
                ps.addBatch();
            }
            ps.executeBatch(); ps.close();
            return true;
        } catch (SQLException e) {
            System.err.println("DoctorDAO.saveSchedule error: " + e.getMessage());
        } finally { DatabaseConfig.getInstance().releaseConnection(conn); }
        return false;
    }

    public List<Doctor> search(String query) {
        List<Doctor> doctors = new ArrayList<>();
        String sql = "SELECT u.*, d.specialization, d.rate_per_hour, d.hospital, d.working_hours FROM users u JOIN doctors d ON u.id = d.id WHERE u.full_name LIKE ? OR d.specialization LIKE ? ORDER BY u.full_name";
        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            String pattern = "%" + query + "%";
            ps.setString(1, pattern); ps.setString(2, pattern);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) doctors.add(mapRow(rs));
            rs.close(); ps.close();
        } catch (SQLException e) {
            System.err.println("DoctorDAO.search error: " + e.getMessage());
        } finally { DatabaseConfig.getInstance().releaseConnection(conn); }
        return doctors;
    }

    private Doctor mapRow(ResultSet rs) throws SQLException {
        Doctor d = new Doctor();
        d.setId(rs.getInt("id"));
        d.setFullName(rs.getString("full_name"));
        d.setEmail(rs.getString("email"));
        d.setPasswordHash(rs.getString("password_hash"));
        d.setRole(User.Role.valueOf(rs.getString("role")));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) d.setCreatedAt(ts.toLocalDateTime());
        d.setSpecialization(rs.getString("specialization"));
        d.setRatePerHour(rs.getBigDecimal("rate_per_hour"));
        d.setHospital(rs.getString("hospital"));
        d.setWorkingHours(rs.getString("working_hours"));
        return d;
    }
}
