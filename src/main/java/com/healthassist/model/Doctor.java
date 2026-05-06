package com.healthassist.model;

import java.math.BigDecimal;

/**
 * Doctor entity extending User with specialization and scheduling info.
 */
public class Doctor extends User {

    private String specialization;
    private BigDecimal ratePerHour;
    private String hospital;
    private String workingHours;

    public Doctor() {
        setRole(Role.DOCTOR);
    }

    public Doctor(int id, String fullName, String email, String passwordHash) {
        super(id, fullName, email, passwordHash, Role.DOCTOR);
    }

    // ── Getters & Setters ──────────────────────────

    public String getSpecialization() { return specialization; }
    public void setSpecialization(String specialization) { this.specialization = specialization; }

    public BigDecimal getRatePerHour() { return ratePerHour; }
    public void setRatePerHour(BigDecimal ratePerHour) { this.ratePerHour = ratePerHour; }

    public String getHospital() { return hospital; }
    public void setHospital(String hospital) { this.hospital = hospital; }

    public String getWorkingHours() { return workingHours; }
    public void setWorkingHours(String workingHours) { this.workingHours = workingHours; }

    @Override
    public String toString() {
        return "Doctor{id=" + getId() + ", name='" + getFullName() + "', spec='" + specialization + "'}";
    }
}
