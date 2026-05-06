package com.healthassist.model;

import java.time.LocalDate;

/**
 * Health record entity documenting a patient visit with diagnosis and prescription.
 */
public class HealthRecord {

    private int id;
    private int patientId;
    private int doctorId;
    private String diagnosis;
    private String prescription;
    private LocalDate visitDate;

    // Transient display fields
    private String patientName;
    private String doctorName;

    public HealthRecord() {}

    // ── Getters & Setters ──────────────────────────

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getPatientId() { return patientId; }
    public void setPatientId(int patientId) { this.patientId = patientId; }

    public int getDoctorId() { return doctorId; }
    public void setDoctorId(int doctorId) { this.doctorId = doctorId; }

    public String getDiagnosis() { return diagnosis; }
    public void setDiagnosis(String diagnosis) { this.diagnosis = diagnosis; }

    public String getPrescription() { return prescription; }
    public void setPrescription(String prescription) { this.prescription = prescription; }

    public LocalDate getVisitDate() { return visitDate; }
    public void setVisitDate(LocalDate visitDate) { this.visitDate = visitDate; }

    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }

    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }

    @Override
    public String toString() {
        return "HealthRecord{id=" + id + ", patient=" + patientId + ", diagnosis='" + diagnosis + "'}";
    }
}
