package com.healthassist.model;

import java.time.LocalDateTime;

/**
 * Appointment entity linking a patient to a doctor at a specific time.
 */
public class Appointment {

    private int id;
    private int patientId;
    private int doctorId;
    private LocalDateTime appointmentDatetime;
    private Status status;
    private String notes;
    private boolean reminderSent;
    private LocalDateTime createdAt;

    // Transient display fields (not persisted, populated by JOINs)
    private String patientName;
    private String doctorName;

    /**
     * Appointment status enumeration.
     */
    public enum Status {
        PENDING, CONFIRMED, CANCELLED, COMPLETED
    }

    public Appointment() {
        this.status = Status.PENDING;
        this.reminderSent = false;
    }

    // ── Getters & Setters ──────────────────────────

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getPatientId() { return patientId; }
    public void setPatientId(int patientId) { this.patientId = patientId; }

    public int getDoctorId() { return doctorId; }
    public void setDoctorId(int doctorId) { this.doctorId = doctorId; }

    public LocalDateTime getAppointmentDatetime() { return appointmentDatetime; }
    public void setAppointmentDatetime(LocalDateTime appointmentDatetime) { this.appointmentDatetime = appointmentDatetime; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public boolean isReminderSent() { return reminderSent; }
    public void setReminderSent(boolean reminderSent) { this.reminderSent = reminderSent; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }

    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }

    @Override
    public String toString() {
        return "Appointment{id=" + id + ", patient=" + patientId + ", doctor=" + doctorId +
               ", datetime=" + appointmentDatetime + ", status=" + status + "}";
    }
}
