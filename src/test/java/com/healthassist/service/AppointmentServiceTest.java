package com.healthassist.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.healthassist.model.Appointment;

/**
 * Pure unit tests for AppointmentService that do not require a database.
 * Exercise the state machine, future-date check, and audit-detail formatting.
 */
class AppointmentServiceTest {

    private final AppointmentService service = new AppointmentService();

    // ── canTransition ─────────────────────────────────────────────────────────

    @Test
    void canTransition_pendingToConfirmed_allowed() {
        assertTrue(service.canTransition(Appointment.Status.PENDING, Appointment.Status.CONFIRMED));
    }

    @Test
    void canTransition_pendingToCancelled_allowed() {
        assertTrue(service.canTransition(Appointment.Status.PENDING, Appointment.Status.CANCELLED));
    }

    @Test
    void canTransition_pendingToCompleted_rejected() {
        assertFalse(service.canTransition(Appointment.Status.PENDING, Appointment.Status.COMPLETED));
    }

    @Test
    void canTransition_confirmedToCompleted_allowed() {
        assertTrue(service.canTransition(Appointment.Status.CONFIRMED, Appointment.Status.COMPLETED));
    }

    @Test
    void canTransition_confirmedToCancelled_allowed() {
        assertTrue(service.canTransition(Appointment.Status.CONFIRMED, Appointment.Status.CANCELLED));
    }

    @Test
    void canTransition_fromTerminalStates_rejected() {
        assertFalse(service.canTransition(Appointment.Status.CANCELLED, Appointment.Status.CONFIRMED));
        assertFalse(service.canTransition(Appointment.Status.CANCELLED, Appointment.Status.COMPLETED));
        assertFalse(service.canTransition(Appointment.Status.COMPLETED, Appointment.Status.CONFIRMED));
        assertFalse(service.canTransition(Appointment.Status.COMPLETED, Appointment.Status.CANCELLED));
    }

    @Test
    void canTransition_nullInputs_rejected() {
        assertFalse(service.canTransition(null, Appointment.Status.CONFIRMED));
        assertFalse(service.canTransition(Appointment.Status.PENDING, null));
        assertFalse(service.canTransition(null, null));
    }

    // ── isAppointmentInFuture ─────────────────────────────────────────────────

    @Test
    void isAppointmentInFuture_pastDatetime_rejected() {
        assertFalse(service.isAppointmentInFuture(LocalDateTime.now().minusMinutes(1)));
    }

    @Test
    void isAppointmentInFuture_futureDatetime_accepted() {
        assertTrue(service.isAppointmentInFuture(LocalDateTime.now().plusHours(1)));
    }

    @Test
    void isAppointmentInFuture_oneSecondFuture_accepted() {
        // Edge case from Phase 12 audit: booking 1 second into the future is allowed.
        assertTrue(service.isAppointmentInFuture(LocalDateTime.now().plusSeconds(2)));
    }

    // ── buildAuditDetails ─────────────────────────────────────────────────────

    @Test
    void buildAuditDetails_capturesStatusDiff() {
        String details = service.buildAuditDetails(
                Appointment.Status.PENDING, Appointment.Status.CONFIRMED, null);
        assertEquals("PENDING -> CONFIRMED", details);
    }

    @Test
    void buildAuditDetails_includesReasonWhenProvided() {
        String details = service.buildAuditDetails(
                Appointment.Status.CONFIRMED, Appointment.Status.CANCELLED, "  no-show  ");
        assertEquals("CONFIRMED -> CANCELLED; reason: no-show", details);
    }

    @Test
    void buildAuditDetails_blankReasonOmitted() {
        String details = service.buildAuditDetails(
                Appointment.Status.PENDING, Appointment.Status.CANCELLED, "   ");
        assertEquals("PENDING -> CANCELLED", details);
    }

    @Test
    void buildAuditDetails_nullStatusesRenderQuestionMark() {
        String details = service.buildAuditDetails(null, null, null);
        assertEquals("? -> ?", details);
    }
}
