package com.healthassist.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.healthassist.config.DatabaseConfig;
import com.healthassist.model.AuditLogEntry;
import com.healthassist.model.User;

/**
 * Read-only DAO for the audit_log table. Writes are performed by AuditLogger.
 * Access restricted to ADMIN by the controller layer.
 */
public class AuditLogDAO {

    public void insert(String eventType,
                       int actorId,
                       String targetType,
                       int targetId,
                       String details,
                       Connection conn) throws SQLException {
        String sql = "INSERT INTO audit_log " +
                "(event_type, actor_user_id, target_type, " +
                "target_id, details, created_at) " +
                "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, eventType);
            ps.setInt(2, actorId);
            ps.setString(3, targetType);
            ps.setInt(4, targetId);
            ps.setString(5, details);
            ps.executeUpdate();
        }
    }

    /**
     * Find the most recent audit entries (newest first), capped at {@code limit}.
     * Joins users to expose actor name to the admin UI.
     */
    public List<AuditLogEntry> findRecent(int limit, User actor) {
        if (actor == null || actor.getRole() != User.Role.ADMIN) return List.of();
        if (limit <= 0) limit = 200;

        List<AuditLogEntry> list = new ArrayList<>();
        String sql = "SELECT al.id, al.actor_user_id, al.event_type, al.target_type, al.target_id, al.details, al.created_at, u.full_name AS actor_name "
                   + "FROM audit_log al LEFT JOIN users u ON al.actor_user_id = u.id "
                   + "ORDER BY al.created_at DESC, al.id DESC LIMIT ?";

        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                AuditLogEntry e = new AuditLogEntry();
                e.setId(rs.getInt("id"));
                int actorId = rs.getInt("actor_user_id");
                e.setActorUserId(rs.wasNull() ? null : actorId);
                e.setEventType(rs.getString("event_type"));
                e.setTargetType(rs.getString("target_type"));
                int targetId = rs.getInt("target_id");
                e.setTargetId(rs.wasNull() ? null : targetId);
                e.setDetails(rs.getString("details"));
                e.setCreatedAt(rs.getTimestamp("created_at"));
                e.setActorName(rs.getString("actor_name"));
                list.add(e);
            }
            rs.close();
            ps.close();
        } catch (SQLException e) {
            System.err.println("AuditLogDAO.findRecent error: " + e.getMessage());
        } finally {
            DatabaseConfig.getInstance().releaseConnection(conn);
        }
        return list;
    }
}
