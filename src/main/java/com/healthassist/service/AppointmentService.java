package com.healthassist.service;

import com.healthassist.dao.AppointmentDAO;
import com.healthassist.dao.DoctorDAO;
import com.healthassist.model.Appointment;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
     * Cancel an appointment by ID.
     */
    public boolean cancelAppointment(int appointmentId) {
        return appointmentDAO.updateStatus(appointmentId, Appointment.Status.CANCELLED);
    }

    /**
     * Confirm an appointment by ID.
     */
    public boolean confirmAppointment(int appointmentId) {
        return appointmentDAO.updateStatus(appointmentId, Appointment.Status.CONFIRMED);
    }

    /**
     * Mark an appointment as completed.
     */
    public boolean completeAppointment(int appointmentId) {
        return appointmentDAO.updateStatus(appointmentId, Appointment.Status.COMPLETED);
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
     * Get today's appointments.
     */
    public List<Appointment> getTodayAppointments() {
        return appointmentDAO.findByDate(LocalDate.now());
    }
}
