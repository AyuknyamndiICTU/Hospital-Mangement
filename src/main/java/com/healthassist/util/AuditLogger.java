package com.healthassist.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import com.healthassist.config.DatabaseConfig;

/**
 * Minimal audit logger used by RBAC-protected mutation paths.
 * Returns a boolean so callers can detect (and surface) audit-write failures
 * instead of having them swallowed silently.
 */
public final class AuditLogger {

    private static final String INSERT_SQL =
            "INSERT INTO audit_log (actor_user_id, event_type, target_type, target_id, details, created_at) "
          + "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";

    private AuditLogger() {}

    /**
     * Write an audit entry on its own connection.
     * @return true on successful insert, false on validation or SQL failure.
     */
    public static boolean log(Integer actorUserId, String eventType, String targetType, Integer targetId, String details) {
        if (eventType == null || targetType == null) return false;

        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();
            return writeWithConnection(conn, actorUserId, eventType, targetType, targetId, details);
        } catch (SQLException e) {
            System.err.println("AuditLogger.log error: " + e.getMessage());
            return false;
        } finally {
            DatabaseConfig.getInstance().releaseConnection(conn);
        }
    }

    /**
     * Write an audit entry on a caller-supplied connection so the audit
     * insert participates in the same transaction as the mutation.
     * The caller is responsible for commit/rollback.
     */
    public static boolean logOnConnection(Connection conn, Integer actorUserId, String eventType,
                                          String targetType, Integer targetId, String details) throws SQLException {
        if (conn == null) return false;
        if (eventType == null || targetType == null) return false;
        return writeWithConnection(conn, actorUserId, eventType, targetType, targetId, details);
    }

    private static boolean writeWithConnection(Connection conn, Integer actorUserId, String eventType,
                                               String targetType, Integer targetId, String details) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
            if (actorUserId == null) ps.setNull(1, java.sql.Types.INTEGER);
            else ps.setInt(1, actorUserId);

            ps.setString(2, eventType);
            ps.setString(3, targetType);

            if (targetId == null) ps.setNull(4, java.sql.Types.INTEGER);
            else ps.setInt(4, targetId);

            ps.setString(5, details);
            return ps.executeUpdate() > 0;
        }
    }
}
