package com.healthassist.dao;

import com.healthassist.config.DatabaseConfig;
import com.healthassist.model.HealthRecord;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class HealthRecordDAO {

    public List<HealthRecord> findByPatient(int patientId) {
        List<HealthRecord> list = new ArrayList<>();
        String sql = "SELECT hr.*, up.full_name AS patient_name, ud.full_name AS doctor_name FROM health_records hr LEFT JOIN users up ON hr.patient_id = up.id LEFT JOIN users ud ON hr.doctor_id = ud.id WHERE hr.patient_id = ? ORDER BY hr.visit_date DESC";
        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, patientId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
            rs.close(); ps.close();
        } catch (SQLException e) {
            System.err.println("HealthRecordDAO.findByPatient error: " + e.getMessage());
        } finally { DatabaseConfig.getInstance().releaseConnection(conn); }
        return list;
    }

    public List<HealthRecord> findAll() {
        List<HealthRecord> list = new ArrayList<>();
        String sql = "SELECT hr.*, up.full_name AS patient_name, ud.full_name AS doctor_name FROM health_records hr LEFT JOIN users up ON hr.patient_id = up.id LEFT JOIN users ud ON hr.doctor_id = ud.id ORDER BY hr.visit_date DESC";
        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
            rs.close(); ps.close();
        } catch (SQLException e) {
            System.err.println("HealthRecordDAO.findAll error: " + e.getMessage());
        } finally { DatabaseConfig.getInstance().releaseConnection(conn); }
        return list;
    }

    public int save(HealthRecord record) {
        String sql = "INSERT INTO health_records (patient_id, doctor_id, diagnosis, prescription, visit_date) VALUES (?, ?, ?, ?, ?)";
        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();
            PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, record.getPatientId());
            ps.setInt(2, record.getDoctorId());
            ps.setString(3, record.getDiagnosis());
            ps.setString(4, record.getPrescription());
            ps.setDate(5, record.getVisitDate() != null ? Date.valueOf(record.getVisitDate()) : null);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) { int id = keys.getInt(1); record.setId(id); keys.close(); ps.close(); return id; }
            keys.close(); ps.close();
        } catch (SQLException e) {
            System.err.println("HealthRecordDAO.save error: " + e.getMessage());
        } finally { DatabaseConfig.getInstance().releaseConnection(conn); }
        return -1;
    }

    public boolean update(HealthRecord record) {
        String sql = "UPDATE health_records SET diagnosis = ?, prescription = ?, visit_date = ? WHERE id = ?";
        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, record.getDiagnosis());
            ps.setString(2, record.getPrescription());
            ps.setDate(3, record.getVisitDate() != null ? Date.valueOf(record.getVisitDate()) : null);
            ps.setInt(4, record.getId());
            int rows = ps.executeUpdate(); ps.close();
            return rows > 0;
        } catch (SQLException e) {
            System.err.println("HealthRecordDAO.update error: " + e.getMessage());
        } finally { DatabaseConfig.getInstance().releaseConnection(conn); }
        return false;
    }

    public boolean delete(int id) {
        String sql = "DELETE FROM health_records WHERE id = ?";
        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, id);
            int rows = ps.executeUpdate(); ps.close();
            return rows > 0;
        } catch (SQLException e) {
            System.err.println("HealthRecordDAO.delete error: " + e.getMessage());
        } finally { DatabaseConfig.getInstance().releaseConnection(conn); }
        return false;
    }

    private HealthRecord mapRow(ResultSet rs) throws SQLException {
        HealthRecord hr = new HealthRecord();
        hr.setId(rs.getInt("id"));
        hr.setPatientId(rs.getInt("patient_id"));
        hr.setDoctorId(rs.getInt("doctor_id"));
        hr.setDiagnosis(rs.getString("diagnosis"));
        hr.setPrescription(rs.getString("prescription"));
        Date vd = rs.getDate("visit_date");
        if (vd != null) hr.setVisitDate(vd.toLocalDate());
        try { hr.setPatientName(rs.getString("patient_name")); } catch (SQLException ignored) {}
        try { hr.setDoctorName(rs.getString("doctor_name")); } catch (SQLException ignored) {}
        return hr;
    }
}
