package com.healthassist.controller;

import java.time.format.DateTimeFormatter;
import java.util.List;

import com.healthassist.model.Appointment;
import com.healthassist.model.User;
import com.healthassist.service.AppointmentService;
import com.healthassist.util.AlertUtil;
import com.healthassist.util.SessionManager;

import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;

public class AppointmentManagementController {

    @FXML private ListView<Appointment> appointmentListView;

    @FXML private Label patientNameLabel;
    @FXML private Label datetimeLabel;
    @FXML private Label statusLabel;
    @FXML private Label doctorNameLabel;
    @FXML private TextArea notesArea;
    @FXML private Label errorLabel;

    @FXML private Button confirmBtn;
    @FXML private Button cancelBtn;
    @FXML private Button completeBtn;

    private final AppointmentService appointmentService = new AppointmentService();

    private Appointment selected;

    private final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @FXML
    public void initialize() {
        errorLabel.setText("");

        appointmentListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Appointment item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                String patient = item.getPatientName() != null ? item.getPatientName() : ("#"+item.getPatientId());
                String doctor = item.getDoctorName() != null ? item.getDoctorName() : ("#"+item.getDoctorId());
                setText(patient + " • " + (item.getAppointmentDatetime() != null ? dtf.format(item.getAppointmentDatetime()) : "-") + " • " + item.getStatus());
            }
        });

        appointmentListView.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            selected = newV;
            renderSelected();
        });

        confirmBtn.setDisable(true);
        cancelBtn.setDisable(true);
        completeBtn.setDisable(true);

        loadPendingAppointments();
    }

    private void loadPendingAppointments() {
        User user = SessionManager.getInstance().getCurrentUser();
        if (user == null) {
            AlertUtil.showError("Error", "Please login again.");
            return;
        }
        if (user.getRole() != User.Role.DOCTOR && user.getRole() != User.Role.ADMIN) {
            errorLabel.setText("Not authorized.");
            appointmentListView.getItems().clear();
            confirmBtn.setDisable(true);
            cancelBtn.setDisable(true);
            completeBtn.setDisable(true);
            return;
        }

        Task<List<Appointment>> task = new Task<>() {
            @Override
            protected List<Appointment> call() {
                if (user.getRole() == User.Role.DOCTOR) {
                    return appointmentService.getDoctorAppointments(user.getId()).stream()
                            .filter(a -> a.getStatus() == Appointment.Status.PENDING || a.getStatus() == Appointment.Status.CONFIRMED)
                            .toList();
                }

                // Admin: show all pending appointments
                return appointmentService.getAllAppointments().stream()
                        .filter(a -> a.getStatus() == Appointment.Status.PENDING || a.getStatus() == Appointment.Status.CONFIRMED)
                        .toList();
            }
        };

        task.setOnSucceeded(e -> {
            appointmentListView.setItems(FXCollections.observableArrayList(task.getValue()));
            selected = null;
            renderSelected();
        });
        task.setOnFailed(e -> errorLabel.setText("Could not load appointments."));
        new Thread(task).start();
    }

    private String getReasonText() {
        if (notesArea == null || notesArea.getText() == null) return null;
        String t = notesArea.getText().trim();
        return t.isEmpty() ? null : t;
    }

    private void renderSelected() {
        if (selected == null) {
            patientNameLabel.setText("-");
            doctorNameLabel.setText("-");
            datetimeLabel.setText("-");
            statusLabel.setText("-");
            notesArea.setText("");
            confirmBtn.setDisable(true);
            cancelBtn.setDisable(true);
            completeBtn.setDisable(true);
            return;
        }

        patientNameLabel.setText(selected.getPatientName() != null ? selected.getPatientName() : ("#"+selected.getPatientId()));
        doctorNameLabel.setText(selected.getDoctorName() != null ? selected.getDoctorName() : ("#"+selected.getDoctorId()));
        datetimeLabel.setText(selected.getAppointmentDatetime() != null ? dtf.format(selected.getAppointmentDatetime()) : "-");
        statusLabel.setText(selected.getStatus() != null ? selected.getStatus().toString() : "-");
        notesArea.setText(selected.getNotes() != null ? selected.getNotes() : "");

        // Buttons enabled only for valid transitions from the current status.
        // Selected is always PENDING in list, but keep logic robust.
        boolean canConfirm = selected.getStatus() == Appointment.Status.PENDING;
        boolean canCancel = selected.getStatus() == Appointment.Status.PENDING;
        boolean canComplete = selected.getStatus() == Appointment.Status.CONFIRMED;

        confirmBtn.setDisable(!canConfirm);
        cancelBtn.setDisable(!canCancel);
        completeBtn.setDisable(!canComplete);
    }

    @FXML
    private void onConfirm(ActionEvent e) {
        if (selected == null) return;
        User actor = SessionManager.getInstance().getCurrentUser();
        boolean ok = appointmentService.confirmAppointment(actor, selected.getId(), getReasonText());
        if (!ok) {
            AlertUtil.showError("Error", "Could not confirm appointment (invalid transition).");
            return;
        }
        AlertUtil.showSuccess("Confirmed!");
        loadPendingAppointments();
    }

    @FXML
    private void onCancel(ActionEvent e) {
        if (selected == null) return;
        User actor = SessionManager.getInstance().getCurrentUser();
        boolean ok = appointmentService.cancelAppointment(actor, selected.getId(), getReasonText());
        if (!ok) {
            AlertUtil.showError("Error", "Could not cancel appointment (invalid transition).");
            return;
        }
        AlertUtil.showSuccess("Cancelled!");
        loadPendingAppointments();
    }

    @FXML
    private void onComplete(ActionEvent e) {
        if (selected == null) return;
        User actor = SessionManager.getInstance().getCurrentUser();
        boolean ok = appointmentService.completeAppointment(actor, selected.getId(), getReasonText());
        if (!ok) {
            AlertUtil.showError("Error", "Could not complete appointment (invalid transition).");
            return;
        }
        AlertUtil.showSuccess("Completed!");
        loadPendingAppointments();
    }
}
