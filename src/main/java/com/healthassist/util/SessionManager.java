package com.healthassist.util;

import com.healthassist.model.User;

/**
 * Singleton session manager holding the currently logged-in user.
 */
public class SessionManager {

    private static SessionManager instance;
    private User currentUser;

    private SessionManager() {}

    public static synchronized SessionManager getInstance() {
        if (instance == null) {
            instance = new SessionManager();
        }
        return instance;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
    }

    public void logout() {
        this.currentUser = null;
    }

    public boolean isLoggedIn() {
        return currentUser != null;
    }

    public boolean isAdmin() {
        return currentUser != null && currentUser.getRole() == User.Role.ADMIN;
    }

    public boolean isDoctor() {
        return currentUser != null && currentUser.getRole() == User.Role.DOCTOR;
    }

    public boolean isPatient() {
        return currentUser != null && currentUser.getRole() == User.Role.PATIENT;
    }
}
