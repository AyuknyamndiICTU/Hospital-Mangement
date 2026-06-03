package com.healthassist.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import com.healthassist.config.DatabaseConfig;

/**
 * Minimal audit logger used by RBAC-protected mutation paths.
 */
public final class AuditLogger {

    private AuditLogger() {}

    public static void log(Integer actorUserId, String eventType, String targetType, Integer targetId, String details) {
        if (eventType == null || targetType == null) return;

        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();
            String sql = """
                INSERT INTO audit_log (actor_user_id, event_type, target_type, target_id, details, created_at)
                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """;

            PreparedStatement ps = conn.prepareStatement(sql);
            if (actorUserId == null) ps.setNull(1, java.sql.Types.INTEGER);
            else ps.setInt(1, actorUserId);

            ps.setString(2, eventType);
            ps.setString(3, targetType);

            if (targetId == null) ps.setNull(4, java.sql.Types.INTEGER);
            else ps.setInt(4, targetId);

            ps.setString(5, details);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            System.err.println("AuditLogger.log error: " + e.getMessage());
        } finally {
            DatabaseConfig.getInstance().releaseConnection(conn);
        }
    }
}
