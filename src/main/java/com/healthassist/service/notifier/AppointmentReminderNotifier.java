package com.healthassist.service.notifier;

import com.healthassist.model.Appointment;

/**
 * Reminder delivery abstraction for upcoming appointments.
 * Return value indicates whether delivery succeeded (so reminder_sent can be persisted).
 */
public interface AppointmentReminderNotifier {
    boolean sendReminder(Appointment appointment);
}
