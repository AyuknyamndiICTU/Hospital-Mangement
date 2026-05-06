package com.healthassist.model;

/**
 * Admin entity extending User.
 * Marker subclass for administrators with full system access.
 */
public class Admin extends User {

    public Admin() {
        setRole(Role.ADMIN);
    }

    public Admin(int id, String fullName, String email, String passwordHash) {
        super(id, fullName, email, passwordHash, Role.ADMIN);
    }

    @Override
    public String toString() {
        return "Admin{id=" + getId() + ", name='" + getFullName() + "'}";
    }
}
