package com.healthassist.dao;

import com.healthassist.config.DatabaseConfig;
import com.healthassist.model.Appointment;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AppointmentDAO {

    public Appointment findById(int id) {
        String sql = "SELECT a.*, up.full_name AS patient_name, ud.full_name AS doctor_name FROM appointments a LEFT JOIN users up ON a.patient_id = up.id LEFT JOIN users ud ON a.doctor_id = ud.id WHERE a.id = ?";
        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRow(rs);
            rs.close(); ps.close();
        } catch (SQLException e) {
            System.err.println("AppointmentDAO.findById error: " + e.getMessage());
        } finally { DatabaseConfig.getInstance().releaseConnection(conn); }
        return null;
    }

    public List<Appointment> findByPatient(int patientId) {
        return findByField("a.patient_id", patientId);
    }

    public List<Appointment> findByDoctor(int doctorId) {
        return findByField("a.doctor_id", doctorId);
    }

    private List<Appointment> findByField(String field, int value) {
        List<Appointment> list = new ArrayList<>();
        String sql = "SELECT a.*, up.full_name AS patient_name, ud.full_name AS doctor_name FROM appointments a LEFT JOIN users up ON a.patient_id = up.id LEFT JOIN users ud ON a.doctor_id = ud.id WHERE " + field + " = ? ORDER BY a.appointment_datetime DESC";
        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, value);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
            rs.close(); ps.close();
        } catch (SQLException e) {
            System.err.println("AppointmentDAO.findByField error: " + e.getMessage());
        } finally { DatabaseConfig.getInstance().releaseConnection(conn); }
        return list;
    }

    public List<Appointment> findByDate(LocalDate date) {
        List<Appointment> list = new ArrayList<>();
        String sql = "SELECT a.*, up.full_name AS patient_name, ud.full_name AS doctor_name FROM appointments a LEFT JOIN users up ON a.patient_id = up.id LEFT JOIN users ud ON a.doctor_id = ud.id WHERE DATE(a.appointment_datetime) = ? ORDER BY a.appointment_datetime";
        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setDate(1, Date.valueOf(date));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
            rs.close(); ps.close();
        } catch (SQLException e) {
            System.err.println("AppointmentDAO.findByDate error: " + e.getMessage());
        } finally { DatabaseConfig.getInstance().releaseConnection(conn); }
        return list;
    }

    public List<Appointment> findAll() {
        List<Appointment> list = new ArrayList<>();
        String sql = "SELECT a.*, up.full_name AS patient_name, ud.full_name AS doctor_name FROM appointments a LEFT JOIN users up ON a.patient_id = up.id LEFT JOIN users ud ON a.doctor_id = ud.id ORDER BY a.appointment_datetime DESC";
        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
            rs.close(); ps.close();
        } catch (SQLException e) {
            System.err.println("AppointmentDAO.findAll error: " + e.getMessage());
        } finally { DatabaseConfig.getInstance().releaseConnection(conn); }
        return list;
    }

    public int save(Appointment appt) {
        String sql = "INSERT INTO appointments (patient_id, doctor_id, appointment_datetime, status, notes) VALUES (?, ?, ?, ?, ?)";
        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();
            PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, appt.getPatientId());
            ps.setInt(2, appt.getDoctorId());
            ps.setTimestamp(3, Timestamp.valueOf(appt.getAppointmentDatetime()));
            ps.setString(4, appt.getStatus().name());
            ps.setString(5, appt.getNotes());
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) { int id = keys.getInt(1); appt.setId(id); keys.close(); ps.close(); return id; }
            keys.close(); ps.close();
        } catch (SQLException e) {
            System.err.println("AppointmentDAO.save error: " + e.getMessage());
        } finally { DatabaseConfig.getInstance().releaseConnection(conn); }
        return -1;
    }

    public boolean updateStatus(int id, Appointment.Status status) {
        String sql = "UPDATE appointments SET status = ? WHERE id = ?";
        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, status.name());
            ps.setInt(2, id);
            int rows = ps.executeUpdate(); ps.close();
            return rows > 0;
        } catch (SQLException e) {
            System.err.println("AppointmentDAO.updateStatus error: " + e.getMessage());
        } finally { DatabaseConfig.getInstance().releaseConnection(conn); }
        return false;
    }

    public boolean delete(int id) {
        String sql = "DELETE FROM appointments WHERE id = ?";
        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, id);
            int rows = ps.executeUpdate(); ps.close();
            return rows > 0;
        } catch (SQLException e) {
            System.err.println("AppointmentDAO.delete error: " + e.getMessage());
        } finally { DatabaseConfig.getInstance().releaseConnection(conn); }
        return false;
    }

    /** Check if a doctor already has an appointment in the same 1-hour slot */
    public boolean hasConflict(int doctorId, LocalDateTime dateTime) {
        String sql = "SELECT COUNT(*) FROM appointments WHERE doctor_id = ? AND status != 'CANCELLED' AND appointment_datetime >= ? AND appointment_datetime < ?";
        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, doctorId);
            LocalDateTime slotStart = dateTime.withMinute(0).withSecond(0);
            LocalDateTime slotEnd = slotStart.plusHours(1);
            ps.setTimestamp(2, Timestamp.valueOf(slotStart));
            ps.setTimestamp(3, Timestamp.valueOf(slotEnd));
            ResultSet rs = ps.executeQuery();
            if (rs.next()) { boolean conflict = rs.getInt(1) > 0; rs.close(); ps.close(); return conflict; }
            rs.close(); ps.close();
        } catch (SQLException e) {
            System.err.println("AppointmentDAO.hasConflict error: " + e.getMessage());
        } finally { DatabaseConfig.getInstance().releaseConnection(conn); }
        return false;
    }

    /** Get upcoming appointments needing reminders (within 30 min, confirmed, not yet reminded) */
    public List<Appointment> getUpcomingReminders() {
        List<Appointment> list = new ArrayList<>();
        String sql = "SELECT a.*, up.full_name AS patient_name, ud.full_name AS doctor_name FROM appointments a LEFT JOIN users up ON a.patient_id = up.id LEFT JOIN users ud ON a.doctor_id = ud.id WHERE a.appointment_datetime BETWEEN NOW() AND DATE_ADD(NOW(), INTERVAL 30 MINUTE) AND a.status = 'CONFIRMED' AND a.reminder_sent = 0";
        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
            rs.close(); ps.close();
        } catch (SQLException e) {
            System.err.println("AppointmentDAO.getUpcomingReminders error: " + e.getMessage());
        } finally { DatabaseConfig.getInstance().releaseConnection(conn); }
        return list;
    }

    /** Mark reminder as sent */
    public boolean markReminderSent(int id) {
        String sql = "UPDATE appointments SET reminder_sent = 1 WHERE id = ?";
        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, id);
            int rows = ps.executeUpdate(); ps.close();
            return rows > 0;
        } catch (SQLException e) {
            System.err.println("AppointmentDAO.markReminderSent error: " + e.getMessage());
        } finally { DatabaseConfig.getInstance().releaseConnection(conn); }
        return false;
    }

    /** Count appointments by status for today */
    public int countTodayByStatus(Appointment.Status status) {
        String sql = "SELECT COUNT(*) FROM appointments WHERE DATE(appointment_datetime) = CURDATE() AND status = ?";
        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, status.name());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) { int count = rs.getInt(1); rs.close(); ps.close(); return count; }
            rs.close(); ps.close();
        } catch (SQLException e) {
            System.err.println("AppointmentDAO.countTodayByStatus error: " + e.getMessage());
        } finally { DatabaseConfig.getInstance().releaseConnection(conn); }
        return 0;
    }

    public int countToday() {
        String sql = "SELECT COUNT(*) FROM appointments WHERE DATE(appointment_datetime) = CURDATE()";
        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) { int c = rs.getInt(1); rs.close(); ps.close(); return c; }
            rs.close(); ps.close();
        } catch (SQLException e) {
            System.err.println("AppointmentDAO.countToday error: " + e.getMessage());
        } finally { DatabaseConfig.getInstance().releaseConnection(conn); }
        return 0;
    }

    private Appointment mapRow(ResultSet rs) throws SQLException {
        Appointment a = new Appointment();
        a.setId(rs.getInt("id"));
        a.setPatientId(rs.getInt("patient_id"));
        a.setDoctorId(rs.getInt("doctor_id"));
        Timestamp dt = rs.getTimestamp("appointment_datetime");
        if (dt != null) a.setAppointmentDatetime(dt.toLocalDateTime());
        a.setStatus(Appointment.Status.valueOf(rs.getString("status")));
        a.setNotes(rs.getString("notes"));
        a.setReminderSent(rs.getInt("reminder_sent") == 1);
        Timestamp ca = rs.getTimestamp("created_at");
        if (ca != null) a.setCreatedAt(ca.toLocalDateTime());
        try { a.setPatientName(rs.getString("patient_name")); } catch (SQLException ignored) {}
        try { a.setDoctorName(rs.getString("doctor_name")); } catch (SQLException ignored) {}
        return a;
    }
}
