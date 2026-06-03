package com.healthassist.service.notifier;

import com.healthassist.model.Appointment;

import javafx.application.Platform;
import javafx.scene.control.Alert;

/**
 * Sends appointment reminders by showing a JavaFX popup (on the UI thread).
 * Always returns true if the popup could be scheduled.
 */
public class JavaFxAppointmentReminderNotifier implements AppointmentReminderNotifier {

    @Override
    public boolean sendReminder(Appointment appointment) {
        if (appointment == null) return false;

        Platform.runLater(() -> {
            try {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Appointment Reminder");
                alert.setHeaderText("Upcoming Appointment");
                alert.setContentText(String.format(
                        "You have an appointment at %s\nPatient: %s\nDoctor: %s",
                        appointment.getAppointmentDatetime().toLocalTime().toString(),
                        appointment.getPatientName() != null ? appointment.getPatientName() : "N/A",
                        appointment.getDoctorName() != null ? appointment.getDoctorName() : "N/A"
                ));
                alert.show();
            } catch (Exception e) {
                System.err.println("[Notifier] FX popup failed: " + e.getMessage());
            }
        });

        return true;
    }
}
