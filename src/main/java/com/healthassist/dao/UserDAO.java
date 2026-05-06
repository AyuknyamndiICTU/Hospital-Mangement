package com.healthassist.dao;

import com.healthassist.config.DatabaseConfig;
import com.healthassist.model.User;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for the users table.
 * All queries use PreparedStatements — never string concatenation.
 */
public class UserDAO {

    /**
     * Find a user by email address.
     */
    public User findByEmail(String email) {
        String sql = "SELECT * FROM users WHERE email = ?";
        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, email);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return mapRow(rs);
            }
            rs.close();
            ps.close();
        } catch (SQLException e) {
            System.err.println("UserDAO.findByEmail error: " + e.getMessage());
        } finally {
            DatabaseConfig.getInstance().releaseConnection(conn);
        }
        return null;
    }

    /**
     * Find a user by ID.
     */
    public User findById(int id) {
        String sql = "SELECT * FROM users WHERE id = ?";
        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return mapRow(rs);
            }
            rs.close();
            ps.close();
        } catch (SQLException e) {
            System.err.println("UserDAO.findById error: " + e.getMessage());
        } finally {
            DatabaseConfig.getInstance().releaseConnection(conn);
        }
        return null;
    }

    /**
     * Find all users, optionally filtered by role.
     */
    public List<User> findAll() {
        return findAllByRole(null);
    }

    public List<User> findAllByRole(User.Role role) {
        List<User> users = new ArrayList<>();
        String sql = role != null ? "SELECT * FROM users WHERE role = ? ORDER BY full_name"
                                  : "SELECT * FROM users ORDER BY full_name";
        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            if (role != null) {
                ps.setString(1, role.name());
            }
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                users.add(mapRow(rs));
            }
            rs.close();
            ps.close();
        } catch (SQLException e) {
            System.err.println("UserDAO.findAll error: " + e.getMessage());
        } finally {
            DatabaseConfig.getInstance().releaseConnection(conn);
        }
        return users;
    }

    /**
     * Insert a new user. Returns the generated ID.
     */
    public int save(User user) {
        String sql = "INSERT INTO users (full_name, email, password_hash, role) VALUES (?, ?, ?, ?)";
        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();
            PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, user.getFullName());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getPasswordHash());
            ps.setString(4, user.getRole().name());
            ps.executeUpdate();

            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                int id = keys.getInt(1);
                user.setId(id);
                keys.close();
                ps.close();
                return id;
            }
            keys.close();
            ps.close();
        } catch (SQLException e) {
            System.err.println("UserDAO.save error: " + e.getMessage());
        } finally {
            DatabaseConfig.getInstance().releaseConnection(conn);
        }
        return -1;
    }

    /**
     * Update an existing user (name, email, role). Does NOT update password.
     */
    public boolean update(User user) {
        String sql = "UPDATE users SET full_name = ?, email = ?, role = ? WHERE id = ?";
        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, user.getFullName());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getRole().name());
            ps.setInt(4, user.getId());
            int rows = ps.executeUpdate();
            ps.close();
            return rows > 0;
        } catch (SQLException e) {
            System.err.println("UserDAO.update error: " + e.getMessage());
        } finally {
            DatabaseConfig.getInstance().releaseConnection(conn);
        }
        return false;
    }

    /**
     * Update a user's password hash.
     */
    public boolean updatePassword(int userId, String newPasswordHash) {
        String sql = "UPDATE users SET password_hash = ? WHERE id = ?";
        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, newPasswordHash);
            ps.setInt(2, userId);
            int rows = ps.executeUpdate();
            ps.close();
            return rows > 0;
        } catch (SQLException e) {
            System.err.println("UserDAO.updatePassword error: " + e.getMessage());
        } finally {
            DatabaseConfig.getInstance().releaseConnection(conn);
        }
        return false;
    }

    /**
     * Delete a user by ID (cascades to patients/doctors).
     */
    public boolean delete(int id) {
        String sql = "DELETE FROM users WHERE id = ?";
        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, id);
            int rows = ps.executeUpdate();
            ps.close();
            return rows > 0;
        } catch (SQLException e) {
            System.err.println("UserDAO.delete error: " + e.getMessage());
        } finally {
            DatabaseConfig.getInstance().releaseConnection(conn);
        }
        return false;
    }

    /**
     * Count users by role.
     */
    public int countByRole(User.Role role) {
        String sql = "SELECT COUNT(*) FROM users WHERE role = ?";
        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, role.name());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                int count = rs.getInt(1);
                rs.close();
                ps.close();
                return count;
            }
            rs.close();
            ps.close();
        } catch (SQLException e) {
            System.err.println("UserDAO.countByRole error: " + e.getMessage());
        } finally {
            DatabaseConfig.getInstance().releaseConnection(conn);
        }
        return 0;
    }

    /**
     * Map a ResultSet row to a User object.
     */
    private User mapRow(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(rs.getInt("id"));
        user.setFullName(rs.getString("full_name"));
        user.setEmail(rs.getString("email"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setRole(User.Role.valueOf(rs.getString("role")));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) {
            user.setCreatedAt(ts.toLocalDateTime());
        }
        return user;
    }
}
