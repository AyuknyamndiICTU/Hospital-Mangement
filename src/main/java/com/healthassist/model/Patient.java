package com.healthassist.model;

import java.time.LocalDate;

/**
 * Patient entity extending User with medical and contact information.
 */
public class Patient extends User {

    private LocalDate dateOfBirth;
    private String bloodType;
    private String address;
    private String phone;
    private String emergencyContact;

    public Patient() {
        setRole(Role.PATIENT);
    }

    public Patient(int id, String fullName, String email, String passwordHash) {
        super(id, fullName, email, passwordHash, Role.PATIENT);
    }

    // ── Getters & Setters ──────────────────────────

    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public String getBloodType() { return bloodType; }
    public void setBloodType(String bloodType) { this.bloodType = bloodType; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmergencyContact() { return emergencyContact; }
    public void setEmergencyContact(String emergencyContact) { this.emergencyContact = emergencyContact; }

    @Override
    public String toString() {
        return "Patient{id=" + getId() + ", name='" + getFullName() + "', blood='" + bloodType + "'}";
    }
}
