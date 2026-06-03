package com.healthassist.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.healthassist.dao.AppointmentDAO;
import com.healthassist.dao.DoctorDAO;
import com.healthassist.model.Appointment;
import com.healthassist.model.User;
import com.healthassist.util.AuditLogger;

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
        // Conflict check: no two appointments for same doctor in same 1-hour slot
        if (appointmentDAO.hasConflict(appointment.getDoctorId(), appointment.getAppointmentDatetime())) {
            System.err.println("Appointment conflict detected for doctor " + appointment.getDoctorId());
            return -1;
        }

        return appointmentDAO.save(appointment);
    }

    /**
     * Book an appointment with RBAC enforcement.
     * Only PATIENT can create appointments and only for their own patient_id.
     */
    public int bookAppointment(User actor, Appointment appointment) {
        if (actor == null || actor.getRole() != User.Role.PATIENT) return -1;
        if (appointment == null) return -1;
        if (appointment.getPatientId() != actor.getId()) return -1;

        int id = bookAppointment(appointment);
        if (id > 0) {
            AuditLogger.log(actor.getId(), "APPOINTMENT_BOOKED", "appointment", id, "Booked by patient");
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
     */
    public boolean cancelAppointment(User actor, int appointmentId, String reason) {
        if (actor == null) return false;
        if (actor.getRole() == User.Role.PATIENT) return false;

        Appointment appt = appointmentDAO.findById(appointmentId);
        if (appt == null) return false;
        if (actor.getRole() == User.Role.DOCTOR && appt.getDoctorId() != actor.getId()) return false;

        boolean ok = cancelAppointment(appointmentId, reason);
        if (ok) {
            AuditLogger.log(actor.getId(), "APPOINTMENT_CANCELLED", "appointment", appointmentId, reason != null ? reason.trim() : null);
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
        return appointmentDAO.updateStatusAndNotes(appointmentId, Appointment.Status.CANCELLED, updatedNotes);
    }

    /**
     * Confirm an appointment by ID (without reason).
     */
    public boolean confirmAppointment(int appointmentId) {
        return confirmAppointment(appointmentId, null);
    }

    /**
     * Confirm an appointment with RBAC enforcement + optional audit reason.
     */
    public boolean confirmAppointment(User actor, int appointmentId, String reason) {
        if (actor == null) return false;
        if (actor.getRole() == User.Role.PATIENT) return false;

        Appointment appt = appointmentDAO.findById(appointmentId);
        if (appt == null) return false;
        if (actor.getRole() == User.Role.DOCTOR && appt.getDoctorId() != actor.getId()) return false;

        boolean ok = confirmAppointment(appointmentId, reason);
        if (ok) {
            AuditLogger.log(actor.getId(), "APPOINTMENT_CONFIRMED", "appointment", appointmentId, reason != null ? reason.trim() : null);
        }
        return ok;
    }

    /**
     * Confirm an appointment by ID with audit reason captured in notes.
     * Enforces valid transitions:
     * - PENDING -> CONFIRMED
     */
    public boolean confirmAppointment(int appointmentId, String reason) {
        Appointment appt = appointmentDAO.findById(appointmentId);
        if (appt == null) return false;

        if (!canTransition(appt.getStatus(), Appointment.Status.CONFIRMED)) return false;

        String updatedNotes = buildUpdatedNotes(appt.getNotes(), reason, "CONFIRMED");
        return appointmentDAO.updateStatusAndNotes(appointmentId, Appointment.Status.CONFIRMED, updatedNotes);
    }

    /**
     * Mark an appointment as completed (without reason).
     */
    public boolean completeAppointment(int appointmentId) {
        return completeAppointment(appointmentId, null);
    }

    /**
     * Complete an appointment with RBAC enforcement + optional audit reason.
     */
    public boolean completeAppointment(User actor, int appointmentId, String reason) {
        if (actor == null) return false;
        if (actor.getRole() == User.Role.PATIENT) return false;

        Appointment appt = appointmentDAO.findById(appointmentId);
        if (appt == null) return false;
        if (actor.getRole() == User.Role.DOCTOR && appt.getDoctorId() != actor.getId()) return false;

        boolean ok = completeAppointment(appointmentId, reason);
        if (ok) {
            AuditLogger.log(actor.getId(), "APPOINTMENT_COMPLETED", "appointment", appointmentId, reason != null ? reason.trim() : null);
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
        return appointmentDAO.updateStatusAndNotes(appointmentId, Appointment.Status.COMPLETED, updatedNotes);
    }

    private boolean canTransition(Appointment.Status from, Appointment.Status to) {
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
