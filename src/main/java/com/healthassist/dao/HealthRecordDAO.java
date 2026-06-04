package com.healthassist.dao;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.healthassist.config.DatabaseConfig;
import com.healthassist.exception.UnauthorizedActionException;
import com.healthassist.model.HealthRecord;
import com.healthassist.model.User;
import com.healthassist.util.AuditLogger;

public class HealthRecordDAO {

    private final AppointmentDAO appointmentDAO = new AppointmentDAO();

    /**
     * Internal unscoped fetch — never expose publicly. Used by the actor-aware
     * overload for ADMIN/PATIENT branches after RBAC has been enforced.
     */
    private List<HealthRecord> findByPatientUnscoped(int patientId) {
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
            System.err.println("HealthRecordDAO.findByPatientUnscoped error: " + e.getMessage());
        } finally { DatabaseConfig.getInstance().releaseConnection(conn); }
        return list;
    }

    /**
     * RBAC-aware find:
     * - PATIENT: only their own (enforced by controller via selected patient id)
     * - DOCTOR: only records created by this doctor AND only for patients they handled
     * - ADMIN: all records for patient
     */
    public List<HealthRecord> findByPatient(int patientId, User actor) {
        if (actor == null) return List.of();
        if (actor.getRole() == User.Role.ADMIN) return findByPatientUnscoped(patientId);

        if (actor.getRole() == User.Role.DOCTOR) {
            boolean hasAppt = appointmentDAO.doctorHasAppointmentWithPatient(actor.getId(), patientId);
            System.out.println(
                    "[HealthRecordDAO] doctorHasAppointmentWithPatient check -> doctorId="
                            + actor.getId()
                            + ", patientId=" + patientId
                            + ", hasAppt=" + hasAppt
            );
            if (!hasAppt) return List.of();

            // Only records authored by this doctor
            List<HealthRecord> list = new ArrayList<>();
            String sql = "SELECT hr.*, up.full_name AS patient_name, ud.full_name AS doctor_name " +
                         "FROM health_records hr " +
                         "LEFT JOIN users up ON hr.patient_id = up.id " +
                         "LEFT JOIN users ud ON hr.doctor_id = ud.id " +
                         "WHERE hr.patient_id = ? AND hr.doctor_id = ? " +
                         "ORDER BY hr.visit_date DESC";
            Connection conn = null;
            try {
                conn = DatabaseConfig.getInstance().getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ps.setInt(1, patientId);
                ps.setInt(2, actor.getId());
                ResultSet rs = ps.executeQuery();
                while (rs.next()) list.add(mapRow(rs));
                rs.close(); ps.close();
            } catch (SQLException e) {
                System.err.println("HealthRecordDAO.findByPatient (doctor) error: " + e.getMessage());
            } finally { DatabaseConfig.getInstance().releaseConnection(conn); }
            return list;
        }

        // PATIENT: only their own records
        if (actor.getRole() == User.Role.PATIENT && actor.getId() == patientId) {
            return findByPatientUnscoped(patientId);
        }

        return List.of();
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

    private int save(HealthRecord record) {
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

    /**
     * RBAC-aware save:
     * - PATIENT: denied
     * - DOCTOR: allowed only when record.doctorId == actor.id
     * - ADMIN: allowed
     */
    public int save(HealthRecord record, User actor) {
        if (actor == null || record == null) return -1;
        if (actor.getRole() == User.Role.PATIENT)
            throw new UnauthorizedActionException("Role " + actor.getRole() + " cannot perform this action.");

        if (actor.getRole() == User.Role.DOCTOR) {
            if (record.getDoctorId() != actor.getId())
                throw new UnauthorizedActionException("Role " + actor.getRole() + " cannot perform this action.");
            if (!appointmentDAO.doctorHasAppointmentWithPatient(actor.getId(), record.getPatientId()))
                throw new UnauthorizedActionException("Role " + actor.getRole() + " cannot perform this action.");
        }

        int id = save(record);

        if (id > 0) {
            Connection conn = null;
            try {
                conn = DatabaseConfig.getInstance().getConnection();
                AuditLogger.log(conn, "HEALTH_RECORD_CREATED", actor.getId(), "health_record", id, null);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            } finally {
                DatabaseConfig.getInstance().releaseConnection(conn);
            }
        }

        return id;
    }

    private boolean update(HealthRecord record) {
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

    /**
     * RBAC-aware update:
     * - PATIENT: denied
     * - DOCTOR: allowed only when record.doctorId == actor.id
     * - ADMIN: allowed
     */
    public boolean update(HealthRecord record, User actor) {
        if (actor == null || record == null) return false;
        if (actor.getRole() == User.Role.PATIENT)
            throw new UnauthorizedActionException("Role " + actor.getRole() + " cannot perform this action.");

        if (actor.getRole() == User.Role.DOCTOR) {
            if (record.getDoctorId() != actor.getId())
                throw new UnauthorizedActionException("Role " + actor.getRole() + " cannot perform this action.");
            if (!appointmentDAO.doctorHasAppointmentWithPatient(actor.getId(), record.getPatientId()))
                throw new UnauthorizedActionException("Role " + actor.getRole() + " cannot perform this action.");
        }

        boolean ok = update(record);

        if (ok) {
            Connection conn = null;
            try {
                conn = DatabaseConfig.getInstance().getConnection();
                AuditLogger.log(conn, "HEALTH_RECORD_UPDATED", actor.getId(), "health_record", record.getId(), null);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            } finally {
                DatabaseConfig.getInstance().releaseConnection(conn);
            }
        }

        return ok;
    }

    private boolean delete(int id) {
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

    /**
     * RBAC-aware delete:
     * - PATIENT: denied
     * - DOCTOR: allowed only when the record's doctor_id == actor.id
     * - ADMIN: allowed
     */
    public boolean delete(int id, User actor) {
        if (actor == null) return false;
        if (actor.getRole() == User.Role.PATIENT)
            throw new UnauthorizedActionException("Role " + actor.getRole() + " cannot perform this action.");

        if (actor.getRole() == User.Role.DOCTOR) {
            // Enforce both doctor ownership and appointment linkage
            HealthRecord existing = findDoctorIdByRecordId(id);
            if (existing == null)
                throw new UnauthorizedActionException("Role " + actor.getRole() + " cannot perform this action.");
            if (existing.getDoctorId() != actor.getId())
                throw new UnauthorizedActionException("Role " + actor.getRole() + " cannot perform this action.");

            // We also need patientId to check appointment linkage
            int patientId = findPatientIdByRecordId(id);
            if (patientId < 0)
                throw new UnauthorizedActionException("Role " + actor.getRole() + " cannot perform this action.");
            if (!appointmentDAO.doctorHasAppointmentWithPatient(actor.getId(), patientId))
                throw new UnauthorizedActionException("Role " + actor.getRole() + " cannot perform this action.");
        }

        boolean ok = delete(id);

        if (ok) {
            Connection conn = null;
            try {
                conn = DatabaseConfig.getInstance().getConnection();
                AuditLogger.log(conn, "HEALTH_RECORD_DELETED", actor.getId(), "health_record", id, null);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            } finally {
                DatabaseConfig.getInstance().releaseConnection(conn);
            }
        }

        return ok;
    }

    /**
     * Convenience DAO method required by Phase 9 plan.
     * Returns all records for a specific patient + doctor pair.
     * (RBAC should be enforced by the caller/controller/service.)
     */
    public List<HealthRecord> findByPatientAndDoctor(int patientId, int doctorId) {
        List<HealthRecord> list = new ArrayList<>();
        String sql = "SELECT * FROM health_records WHERE patient_id = ? AND doctor_id = ? ORDER BY visit_date DESC";

        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, patientId);
            ps.setInt(2, doctorId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                HealthRecord hr = new HealthRecord();
                hr.setId(rs.getInt("id"));
                hr.setPatientId(rs.getInt("patient_id"));
                hr.setDoctorId(rs.getInt("doctor_id"));
                hr.setDiagnosis(rs.getString("diagnosis"));
                hr.setPrescription(rs.getString("prescription"));
                Date vd = rs.getDate("visit_date");
                if (vd != null) hr.setVisitDate(vd.toLocalDate());
                list.add(hr);
            }
            rs.close(); ps.close();
        } catch (SQLException e) {
            System.err.println("HealthRecordDAO.findByPatientAndDoctor error: " + e.getMessage());
        } finally { DatabaseConfig.getInstance().releaseConnection(conn); }

        return list;
    }

    /**
     * Convenience DAO method required by Phase 9 plan.
     * Saves a health record "for" an appointment:
     * - derives patient_id and doctor_id from the appointment
     * - enforces RBAC via save(record, actor)
     */
    public int saveForAppointment(int appointmentId, HealthRecord record, User actor) {
        if (record == null) return -1;
        com.healthassist.model.Appointment appt = appointmentDAO.findById(appointmentId);
        if (appt == null) return -1;

        record.setPatientId(appt.getPatientId());
        record.setDoctorId(appt.getDoctorId());

        return save(record, actor);
    }

    private int findPatientIdByRecordId(int id) {
        String sql = "SELECT id, patient_id FROM health_records WHERE id = ?";
        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                int patientId = rs.getInt("patient_id");
                rs.close();
                ps.close();
                return patientId;
            }
            rs.close();
            ps.close();
        } catch (SQLException e) {
            System.err.println("HealthRecordDAO.findPatientIdByRecordId error: " + e.getMessage());
        } finally { DatabaseConfig.getInstance().releaseConnection(conn); }
        return -1;
    }

    private HealthRecord findDoctorIdByRecordId(int id) {
        String sql = "SELECT id, doctor_id FROM health_records WHERE id = ?";
        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                HealthRecord hr = new HealthRecord();
                hr.setId(rs.getInt("id"));
                hr.setDoctorId(rs.getInt("doctor_id"));
                rs.close();
                ps.close();
                return hr;
            }
            rs.close(); ps.close();
        } catch (SQLException e) {
            System.err.println("HealthRecordDAO.findDoctorIdByRecordId error: " + e.getMessage());
        } finally { DatabaseConfig.getInstance().releaseConnection(conn); }
        return null;
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
