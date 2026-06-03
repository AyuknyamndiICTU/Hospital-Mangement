package com.healthassist.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.healthassist.dao.AppointmentDAO;
import com.healthassist.dao.DoctorDAO;
import com.healthassist.exception.InvalidTransitionException;
import com.healthassist.exception.UnauthorizedActionException;
import com.healthassist.model.Appointment;
import com.healthassist.model.User;
import com.healthassist.util.AuditLogger;
import com.healthassist.util.SessionManager;

/**
 * Business logic for appointment booking, conflict detection, and slot availability.
 */
public class AppointmentService {

    private final AppointmentDAO appointmentDAO = new AppointmentDAO();
    private final DoctorDAO doctorDAO = new DoctorDAO();

    /**
     * Get available time slots for a doctor on a given date.
     * Cross-references doctor_schedule with existing appointments.
     */
    public List<LocalTime> getAvailableSlots(int doctorId, LocalDate date) {
        List<LocalTime> slots = new ArrayList<>();

        // Get doctor's schedule for this day of week
        String dayCode = date.getDayOfWeek().name().substring(0, 3); // MON, TUE, etc.
        List<Map<String, String>> schedule = doctorDAO.getSchedule(doctorId);

        LocalTime startTime = null;
        LocalTime endTime = null;

        for (Map<String, String> entry : schedule) {
            if (entry.get("day").equals(dayCode)) {
                startTime = LocalTime.parse(entry.get("start"));
                endTime = LocalTime.parse(entry.get("end"));
                break;
            }
        }

        // If no schedule found for this day, use default 08:00 - 17:00
        if (startTime == null) {
            startTime = LocalTime.of(8, 0);
            endTime = LocalTime.of(17, 0);
        }

        // Generate 1-hour slots
        LocalTime current = startTime;
        while (current.isBefore(endTime)) {
            LocalDateTime slotDateTime = LocalDateTime.of(date, current);
            // Check if slot is available (no conflict)
            if (!appointmentDAO.hasConflict(doctorId, slotDateTime)) {
                slots.add(current);
            }
            current = current.plusHours(1);
        }

        return slots;
    }

    /**
     * Book an appointment with conflict checking.
     * Returns the appointment ID on success, -1 on conflict or failure.
     */
    public int bookAppointment(Appointment appointment) {
        if (appointment == null || appointment.getAppointmentDatetime() == null) return -1;

        // Data integrity hard rules (Phase 12)
        if (!isAppointmentInFuture(appointment.getAppointmentDatetime())) return -1;
        if (!isWithinWorkingHours(appointment.getDoctorId(), appointment.getAppointmentDatetime())) return -1;

        // Conflict check: no two appointments for same doctor in same 1-hour slot
        if (appointmentDAO.hasConflict(appointment.getDoctorId(), appointment.getAppointmentDatetime())) {
            System.err.println("Appointment conflict detected for doctor " + appointment.getDoctorId());
            return -1;
        }

        return appointmentDAO.save(appointment, SessionManager.getInstance().getCurrentUser());
    }

    /**
     * Book an appointment with RBAC enforcement.
     * Only PATIENT can create appointments and only for their own patient_id.
     * Throws {@link UnauthorizedActionException} on RBAC denial.
     */
    public int bookAppointment(User actor, Appointment appointment) {
        if (actor == null || actor.getRole() != User.Role.PATIENT) {
            throw new UnauthorizedActionException("book appointment", "only patients can book");
        }
        if (appointment == null) return -1;
        if (appointment.getPatientId() != actor.getId()) {
            throw new UnauthorizedActionException("book appointment", "patient may only book for self");
        }

        int id = bookAppointment(appointment);
        if (id > 0) {
            java.sql.Connection conn = null;
            try {
                conn = com.healthassist.config.DatabaseConfig.getInstance().getConnection();
                AuditLogger.log(conn, "APPOINTMENT_BOOKED", actor.getId(), "appointment", id, "Booked by patient");
            } catch (java.sql.SQLException e) {
                throw new RuntimeException(e);
            } finally {
                com.healthassist.config.DatabaseConfig.getInstance().releaseConnection(conn);
            }
        }
        return id;
    }

    /**
     * Cancel an appointment by ID (without reason).
     */
    public boolean cancelAppointment(int appointmentId) {
        return cancelAppointment(appointmentId, null);
    }

    /**
     * Cancel an appointment with RBAC enforcement + optional audit reason.
     * Throws {@link UnauthorizedActionException} on RBAC denial,
     * {@link InvalidTransitionException} on invalid status transition.
     */
    public boolean cancelAppointment(User actor, int appointmentId, String reason) {
        if (actor == null || actor.getRole() == User.Role.PATIENT) {
            throw new UnauthorizedActionException("cancel appointment", "patients cannot cancel via this path");
        }

        Appointment appt = appointmentDAO.findById(appointmentId);
        if (appt == null) return false;
        if (actor.getRole() == User.Role.DOCTOR && appt.getDoctorId() != actor.getId()) {
            throw new UnauthorizedActionException("cancel appointment", "doctor may only cancel own appointments");
        }

        Appointment.Status oldStatus = appt.getStatus();
        boolean ok = cancelAppointment(appointmentId, reason);
        if (ok) {
            java.sql.Connection conn = null;
            try {
                conn = com.healthassist.config.DatabaseConfig.getInstance().getConnection();
                AuditLogger.log(conn, "APPOINTMENT_CANCELLED", actor.getId(), "appointment", appointmentId,
                        buildAuditDetails(oldStatus, Appointment.Status.CANCELLED, reason));
            } catch (java.sql.SQLException e) {
                throw new RuntimeException(e);
            } finally {
                com.healthassist.config.DatabaseConfig.getInstance().releaseConnection(conn);
            }
        }
        return ok;
    }

    /**
     * Cancel an appointment by ID with audit reason captured in notes.
     * Enforces valid transitions:
     * - PENDING -> CANCELLED
     * - CONFIRMED -> CANCELLED
     */
    public boolean cancelAppointment(int appointmentId, String reason) {
        Appointment appt = appointmentDAO.findById(appointmentId);
        if (appt == null) return false;

        if (!canTransition(appt.getStatus(), Appointment.Status.CANCELLED)) return false;

        String updatedNotes = buildUpdatedNotes(appt.getNotes(), reason, "CANCELLED");
        return appointmentDAO.updateStatusAndNotes(appointmentId, Appointment.Status.CANCELLED, updatedNotes,
                SessionManager.getInstance().getCurrentUser());
    }

    /**
     * Confirm an appointment by ID (without reason).
     */
    public boolean confirmAppointment(int appointmentId) {
        return confirmAppointment(appointmentId, null);
    }

    /**
     * Confirm an appointment with RBAC enforcement + optional audit reason.
     * Throws {@link UnauthorizedActionException} on RBAC denial,
     * {@link InvalidTransitionException} when the appointment time has drifted
     * into the past or no longer fits the doctor's working hours.
     */
    public boolean confirmAppointment(User actor, int appointmentId, String reason) {
        if (actor == null || actor.getRole() == User.Role.PATIENT) {
            throw new UnauthorizedActionException("confirm appointment", "patients cannot confirm");
        }

        Appointment appt = appointmentDAO.findById(appointmentId);
        if (appt == null) return false;
        if (actor.getRole() == User.Role.DOCTOR && appt.getDoctorId() != actor.getId()) {
            throw new UnauthorizedActionException("confirm appointment", "doctor may only confirm own appointments");
        }

        Appointment.Status oldStatus = appt.getStatus();
        boolean ok = confirmAppointment(appointmentId, reason);
        if (ok) {
            java.sql.Connection conn = null;
            try {
                conn = com.healthassist.config.DatabaseConfig.getInstance().getConnection();
                AuditLogger.log(conn, "APPOINTMENT_CONFIRMED", actor.getId(), "appointment", appointmentId,
                        buildAuditDetails(oldStatus, Appointment.Status.CONFIRMED, reason));
            } catch (java.sql.SQLException e) {
                throw new RuntimeException(e);
            } finally {
                com.healthassist.config.DatabaseConfig.getInstance().releaseConnection(conn);
            }
        }
        return ok;
    }

    /**
     * Confirm an appointment by ID with audit reason captured in notes.
     * Re-validates Phase 12 scheduling rules so a stale PENDING row cannot
     * be promoted to CONFIRMED once its datetime has moved into the past
     * or fallen outside the doctor's working hours.
     * Enforces valid transitions: PENDING -> CONFIRMED.
     */
    public boolean confirmAppointment(int appointmentId, String reason) {
        Appointment appt = appointmentDAO.findById(appointmentId);
        if (appt == null) return false;

        if (!canTransition(appt.getStatus(), Appointment.Status.CONFIRMED)) {
            throw new InvalidTransitionException("Cannot confirm appointment in state " + appt.getStatus());
        }
        if (!isAppointmentInFuture(appt.getAppointmentDatetime())) {
            throw new InvalidTransitionException("Cannot confirm an appointment whose datetime is in the past");
        }
        if (!isWithinWorkingHours(appt.getDoctorId(), appt.getAppointmentDatetime())) {
            throw new InvalidTransitionException("Cannot confirm an appointment outside the doctor's working hours");
        }

        String updatedNotes = buildUpdatedNotes(appt.getNotes(), reason, "CONFIRMED");
        return appointmentDAO.updateStatusAndNotes(appointmentId, Appointment.Status.CONFIRMED, updatedNotes,
                SessionManager.getInstance().getCurrentUser());
    }

    /**
     * Mark an appointment as completed (without reason).
     */
    public boolean completeAppointment(int appointmentId) {
        return completeAppointment(appointmentId, null);
    }

    /**
     * Complete an appointment with RBAC enforcement + optional audit reason.
     * Throws {@link UnauthorizedActionException} on RBAC denial.
     */
    public boolean completeAppointment(User actor, int appointmentId, String reason) {
        if (actor == null || actor.getRole() == User.Role.PATIENT) {
            throw new UnauthorizedActionException("complete appointment", "patients cannot complete");
        }

        Appointment appt = appointmentDAO.findById(appointmentId);
        if (appt == null) return false;
        if (actor.getRole() == User.Role.DOCTOR && appt.getDoctorId() != actor.getId()) {
            throw new UnauthorizedActionException("complete appointment", "doctor may only complete own appointments");
        }

        Appointment.Status oldStatus = appt.getStatus();
        boolean ok = completeAppointment(appointmentId, reason);
        if (ok) {
            java.sql.Connection conn = null;
            try {
                conn = com.healthassist.config.DatabaseConfig.getInstance().getConnection();
                AuditLogger.log(conn, "APPOINTMENT_COMPLETED", actor.getId(), "appointment", appointmentId,
                        buildAuditDetails(oldStatus, Appointment.Status.COMPLETED, reason));
            } catch (java.sql.SQLException e) {
                throw new RuntimeException(e);
            } finally {
                com.healthassist.config.DatabaseConfig.getInstance().releaseConnection(conn);
            }
        }
        return ok;
    }

    /**
     * Mark an appointment as completed with audit reason captured in notes.
     * Enforces valid transitions:
     * - CONFIRMED -> COMPLETED
     */
    public boolean completeAppointment(int appointmentId, String reason) {
        Appointment appt = appointmentDAO.findById(appointmentId);
        if (appt == null) return false;

        if (!canTransition(appt.getStatus(), Appointment.Status.COMPLETED)) return false;

        String updatedNotes = buildUpdatedNotes(appt.getNotes(), reason, "COMPLETED");
        return appointmentDAO.updateStatusAndNotes(appointmentId, Appointment.Status.COMPLETED, updatedNotes,
                SessionManager.getInstance().getCurrentUser());
    }

    // package-private for unit tests
    boolean canTransition(Appointment.Status from, Appointment.Status to) {
        if (from == null || to == null) return false;

        // Terminal states: once cancelled/completed, no further transitions.
        if (from == Appointment.Status.CANCELLED || from == Appointment.Status.COMPLETED) {
            return false;
        }

        switch (from) {
            case PENDING:
                return to == Appointment.Status.CONFIRMED || to == Appointment.Status.CANCELLED;
            case CONFIRMED:
                return to == Appointment.Status.COMPLETED || to == Appointment.Status.CANCELLED;
            default:
                return false;
        }
    }

    /**
     * Format an audit-log details string capturing the status diff and optional reason.
     * Example: "PENDING -> CONFIRMED" or "PENDING -> CANCELLED; reason: no-show".
     */
    // package-private for unit tests
    String buildAuditDetails(Appointment.Status oldStatus, Appointment.Status newStatus, String reason) {
        String from = oldStatus != null ? oldStatus.name() : "?";
        String to = newStatus != null ? newStatus.name() : "?";
        String base = from + " -> " + to;
        if (reason != null && !reason.trim().isEmpty()) {
            base = base + "; reason: " + reason.trim();
        }
        return base;
    }

    private String buildUpdatedNotes(String existingNotes, String reason, String actionStatus) {
        String base = existingNotes != null ? existingNotes.trim() : "";
        String r = reason != null ? reason.trim() : "";

        if (r.isEmpty()) {
            return base; // no reason captured
        }

        String reasonLine = "Reason (" + actionStatus + "): " + r;
        if (base.isEmpty()) return reasonLine;

        // Append without clobbering original booking notes
        return base + "\n" + reasonLine;
    }

    // package-private for unit tests
    boolean isAppointmentInFuture(LocalDateTime appointmentDatetime) {
        return appointmentDatetime.isAfter(LocalDateTime.now());
    }

    /**
     * Enforces doctor working hours hard (Phase 12).
     * Appointment must be aligned to a 1-hour slot boundary (minute=0, second=0)
     * and must fit within the configured start/end time for that day.
     */
    private boolean isWithinWorkingHours(int doctorId, LocalDateTime appointmentDatetime) {
        if (appointmentDatetime.getMinute() != 0 || appointmentDatetime.getSecond() != 0) return false;

        String dayCode = appointmentDatetime.getDayOfWeek().name().substring(0, 3); // MON, TUE, etc.
        List<Map<String, String>> schedule = doctorDAO.getSchedule(doctorId);

        LocalTime startTime = null;
        LocalTime endTime = null;

        for (Map<String, String> entry : schedule) {
            if (entry.get("day").equals(dayCode)) {
                startTime = LocalTime.parse(entry.get("start"));
                endTime = LocalTime.parse(entry.get("end"));
                break;
            }
        }

        // If no schedule found for this day, use default 08:00 - 17:00 (matches slot generation)
        if (startTime == null) {
            startTime = LocalTime.of(8, 0);
            endTime = LocalTime.of(17, 0);
        }

        LocalTime slotStart = appointmentDatetime.toLocalTime();
        LocalTime slotEnd = slotStart.plusHours(1);

        // slot must fit within [startTime, endTime)
        return !slotStart.isBefore(startTime) && slotEnd.compareTo(endTime) <= 0;
    }

    /**
     * Get all appointments for a patient.
     */
    public List<Appointment> getPatientAppointments(int patientId) {
        return appointmentDAO.findByPatient(patientId);
    }

    /**
     * Get all appointments for a doctor.
     */
    public List<Appointment> getDoctorAppointments(int doctorId) {
        return appointmentDAO.findByDoctor(doctorId);
    }

    /**
     * Get all appointments.
     */
    public List<Appointment> getAllAppointments() {
        return appointmentDAO.findAll();
    }

    /**
     * Get today's appointments.
     */
    public List<Appointment> getTodayAppointments() {
        return appointmentDAO.findByDate(LocalDate.now());
    }
}
