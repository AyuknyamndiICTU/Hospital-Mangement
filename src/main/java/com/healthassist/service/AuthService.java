package com.healthassist.service;

import com.healthassist.dao.UserDAO;
import com.healthassist.model.User;
import org.mindrot.jbcrypt.BCrypt;

/**
 * Authentication service handling login and password hashing.
 */
public class AuthService {

    private final UserDAO userDAO = new UserDAO();

    /**
     * Authenticate a user by email and plain-text password.
     * Returns the User if credentials match, null otherwise.
     */
    public User login(String email, String password) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            return null;
        }

        User user = userDAO.findByEmail(email.trim().toLowerCase());
        if (user == null) {
            return null;
        }

        // Verify BCrypt hash
        if (BCrypt.checkpw(password, user.getPasswordHash())) {
            return user;
        }

        return null;
    }

    /**
     * Register a new user with a plain-text password (will be hashed).
     * Returns the generated user ID, or -1 on failure.
     */
    public int register(User user, String rawPassword) {
        if (rawPassword == null || rawPassword.length() < 6) {
            System.err.println("Password must be at least 6 characters.");
            return -1;
        }

        // Check if email already exists
        if (userDAO.findByEmail(user.getEmail()) != null) {
            System.err.println("Email already registered: " + user.getEmail());
            return -1;
        }

        // Hash the password
        String hash = BCrypt.hashpw(rawPassword, BCrypt.gensalt(12));
        user.setPasswordHash(hash);

        return userDAO.save(user);
    }

    /**
     * Change a user's password.
     */
    public boolean changePassword(int userId, String oldPassword, String newPassword) {
        User user = userDAO.findById(userId);
        if (user == null) return false;

        if (!BCrypt.checkpw(oldPassword, user.getPasswordHash())) {
            return false; // Old password doesn't match
        }

        String newHash = BCrypt.hashpw(newPassword, BCrypt.gensalt(12));
        return userDAO.updatePassword(userId, newHash);
    }
}
