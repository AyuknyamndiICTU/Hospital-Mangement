package com.healthassist.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.healthassist.config.DatabaseConfig;
import com.healthassist.exception.UnauthorizedActionException;
import com.healthassist.model.Doctor;
import com.healthassist.model.User;
import com.healthassist.util.AuditLogger;

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

    private boolean save(Doctor doctor) {
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

    private boolean update(Doctor doctor) {
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

    private boolean delete(int id) { return userDAO.delete(id); }

    /**
     * RBAC-aware delete for admin-only doctor management.
     * Throws {@link UnauthorizedActionException} on RBAC denial.
     */
    public boolean delete(int id, User actor) {
        if (actor == null || actor.getRole() != User.Role.ADMIN) {
            throw new UnauthorizedActionException("delete doctor", "admin only");
        }
        boolean ok = userDAO.delete(id);
        if (ok) {
            Connection conn = null;
            try {
                conn = DatabaseConfig.getInstance().getConnection();
                AuditLogger.log(conn, "DOCTOR_DELETED", actor.getId(), "user", id, null);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            } finally {
                DatabaseConfig.getInstance().releaseConnection(conn);
            }
        }
        return ok;
    }

    /**
     * RBAC-aware update: ADMIN, or DOCTOR updating their own record.
     * Throws {@link UnauthorizedActionException} on RBAC denial.
     */
    public boolean update(Doctor doctor, User actor) {
        if (actor == null || doctor == null) {
            throw new UnauthorizedActionException("update doctor", "missing actor or target");
        }
        boolean adminOk = actor.getRole() == User.Role.ADMIN;
        boolean selfOk  = actor.getRole() == User.Role.DOCTOR && actor.getId() == doctor.getId();
        if (!adminOk && !selfOk) {
            throw new UnauthorizedActionException("update doctor", "must be admin or self");
        }
        boolean ok = update(doctor);
        if (ok) {
            Connection conn = null;
            try {
                conn = DatabaseConfig.getInstance().getConnection();
                AuditLogger.log(conn, "DOCTOR_UPDATED", actor.getId(), "user", doctor.getId(), null);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            } finally {
                DatabaseConfig.getInstance().releaseConnection(conn);
            }
        }
        return ok;
    }

    /**
     * RBAC-aware schedule update: ADMIN, or DOCTOR updating their own schedule.
     * Throws {@link UnauthorizedActionException} on RBAC denial.
     */
    public boolean saveSchedule(int doctorId, List<Map<String, String>> schedule, User actor) {
        if (actor == null) {
            throw new UnauthorizedActionException("save schedule", "missing actor");
        }
        boolean adminOk = actor.getRole() == User.Role.ADMIN;
        boolean selfOk  = actor.getRole() == User.Role.DOCTOR && actor.getId() == doctorId;
        if (!adminOk && !selfOk) {
            throw new UnauthorizedActionException("save schedule", "must be admin or self");
        }
        boolean ok = saveSchedule(doctorId, schedule);
        if (ok) {
            Connection conn = null;
            try {
                conn = DatabaseConfig.getInstance().getConnection();
                AuditLogger.log(conn, "DOCTOR_SCHEDULE_UPDATED", actor.getId(), "doctor_schedule", doctorId, null);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            } finally {
                DatabaseConfig.getInstance().releaseConnection(conn);
            }
        }
        return ok;
    }

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

    private boolean saveSchedule(int doctorId, List<Map<String, String>> schedule) {
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
