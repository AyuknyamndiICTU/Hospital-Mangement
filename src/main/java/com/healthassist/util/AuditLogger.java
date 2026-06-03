package com.healthassist.util;

import java.sql.Connection;
import java.sql.SQLException;

import com.healthassist.dao.AuditLogDAO;

/**
 * Minimal audit logger used by RBAC-protected mutation paths.
 * Delegates the actual insert to AuditLogDAO and propagates SQL failures.
 */
public final class AuditLogger {

    private AuditLogger() {}

    public static void log(Connection conn,
                            String eventType,
                            int actorId,
                            String targetType,
                            int targetId,
                            String details) {
        try {
            new AuditLogDAO().insert(eventType, actorId, targetType, targetId, details, conn);
        } catch (SQLException e) {
            throw new RuntimeException(e);
    }

    private static String toJson(String... pairs) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < pairs.length - 1; i += 2) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(pairs[i]).append("\":\"")
                    .append(pairs[i + 1]).append("\"");
        }
        return sb.append("}").toString();
    }
}
}
