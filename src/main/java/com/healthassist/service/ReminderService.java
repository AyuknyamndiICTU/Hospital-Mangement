package com.healthassist.service;

import com.healthassist.dao.AppointmentDAO;
import com.healthassist.model.Appointment;
import javafx.application.Platform;
import javafx.scene.control.Alert;

import java.util.List;

/**
 * Daemon background thread that polls for upcoming appointments
 * and shows JavaFX notification popups 30 minutes before.
 */
public class ReminderService implements Runnable {

    private final AppointmentDAO appointmentDAO = new AppointmentDAO();
    private volatile boolean running = true;

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
            // Show notification on JavaFX thread
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Appointment Reminder");
                alert.setHeaderText("Upcoming Appointment");
                alert.setContentText(String.format(
                    "You have an appointment at %s\nPatient: %s\nDoctor: %s",
                    appt.getAppointmentDatetime().toLocalTime().toString(),
                    appt.getPatientName() != null ? appt.getPatientName() : "N/A",
                    appt.getDoctorName() != null ? appt.getDoctorName() : "N/A"
                ));
                alert.show();
            });

            // Mark as reminded
            appointmentDAO.markReminderSent(appt.getId());
        }
    }

    /**
     * Stop the reminder service.
     */
    public void stop() {
        running = false;
    }
}
