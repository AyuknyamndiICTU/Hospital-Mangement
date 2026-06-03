package com.healthassist.service;

import java.util.List;

import com.healthassist.dao.AppointmentDAO;
import com.healthassist.model.Appointment;
import com.healthassist.service.notifier.AppointmentReminderNotifier;
import com.healthassist.service.notifier.JavaFxAppointmentReminderNotifier;
import com.healthassist.util.AuditLogger;
import com.healthassist.util.SessionManager;

/**
 * Daemon background thread that polls for upcoming appointments
 * and shows JavaFX notification popups 30 minutes before.
 */
public class ReminderService implements Runnable {

    private final AppointmentDAO appointmentDAO = new AppointmentDAO();
    private final AppointmentReminderNotifier reminderNotifier;

    private volatile boolean running = true;

    public ReminderService() {
        this(new JavaFxAppointmentReminderNotifier());
    }

    public ReminderService(AppointmentReminderNotifier reminderNotifier) {
        this.reminderNotifier = reminderNotifier != null ? reminderNotifier : new JavaFxAppointmentReminderNotifier();
    }

    @Override
    public void run() {
        System.out.println("ReminderService started (polling every 60s).");
        while (running) {
            try {
                checkReminders();
                Thread.sleep(60_000); // Poll every 60 seconds
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("ReminderService error: " + e.getMessage());
            }
        }
        System.out.println("ReminderService stopped.");
    }

    /**
     * Query for upcoming confirmed appointments within 30 min that haven't been reminded.
     */
    private void checkReminders() {
        List<Appointment> upcoming = appointmentDAO.getUpcomingReminders();
        for (Appointment appt : upcoming) {
            boolean delivered = reminderNotifier.sendReminder(appt);

            // Persist reminder_sent only when delivery succeeds (or can be scheduled).
            if (delivered) {
                appointmentDAO.markReminderSent(appt.getId(), SessionManager.getInstance().getCurrentUser());

                java.sql.Connection conn = null;
                try {
                    conn = com.healthassist.config.DatabaseConfig.getInstance().getConnection();
                    AuditLogger.log(conn, "APPOINTMENT_REMINDER_SENT", SessionManager.getInstance().getCurrentUser().getId(),
                            "appointment", appt.getId(), "Reminder delivered/scheduled");
                } catch (java.sql.SQLException e) {
                    throw new RuntimeException(e);
                } finally {
                    com.healthassist.config.DatabaseConfig.getInstance().releaseConnection(conn);
                }
            } else {
                java.sql.Connection conn = null;
                try {
                    conn = com.healthassist.config.DatabaseConfig.getInstance().getConnection();
                    AuditLogger.log(conn, "APPOINTMENT_REMINDER_FAILED", SessionManager.getInstance().getCurrentUser().getId(),
                            "appointment", appt.getId(), "Reminder delivery failed");
                } catch (java.sql.SQLException e) {
                    throw new RuntimeException(e);
                } finally {
                    com.healthassist.config.DatabaseConfig.getInstance().releaseConnection(conn);
                }
            }
        }
    }

    /**
     * Stop the reminder service.
     */
    public void stop() {
        running = false;
    }
}
