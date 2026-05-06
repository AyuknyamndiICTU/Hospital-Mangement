package com.healthassist.controller;

import com.healthassist.dao.HealthRecordDAO;
import com.healthassist.dao.PatientDAO;
import com.healthassist.dao.DoctorDAO;
import com.healthassist.model.*;
import com.healthassist.util.*;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class HealthRecordController {
    @FXML private ComboBox<String> patientCombo;
    @FXML private Button addRecordBtn;
    @FXML private VBox timelineLine, recordsBox;

    private final PatientDAO patientDAO = new PatientDAO();
    private final DoctorDAO doctorDAO = new DoctorDAO();
    private final HealthRecordDAO healthRecordDAO = new HealthRecordDAO();
    private List<Patient> patientList;
    private int selectedPatientId = -1;

    @FXML
    public void initialize() {
        loadPatients();
        User user = SessionManager.getInstance().getCurrentUser();
        // Patients can only view their own records
        if (user != null && user.getRole() == User.Role.PATIENT) {
            addRecordBtn.setVisible(false);
            addRecordBtn.setManaged(false);
        }
    }

    private void loadPatients() {
        Task<List<Patient>> task = new Task<>() {
            @Override protected List<Patient> call() { return patientDAO.findAll(); }
        };
        task.setOnSucceeded(e -> {
            patientList = task.getValue();
            patientCombo.getItems().clear();
            for (Patient p : patientList) patientCombo.getItems().add(p.getId() + " - " + p.getFullName());

            User user = SessionManager.getInstance().getCurrentUser();
            if (user != null && user.getRole() == User.Role.PATIENT) {
                for (int i = 0; i < patientList.size(); i++) {
                    if (patientList.get(i).getId() == user.getId()) {
                        patientCombo.getSelectionModel().select(i);
                        patientCombo.setDisable(true);
                        selectedPatientId = user.getId();
                        loadRecords(selectedPatientId);
                        break;
                    }
                }
            }
        });
        new Thread(task).start();
    }

    @FXML
    private void onLoadRecords() {
        int idx = patientCombo.getSelectionModel().getSelectedIndex();
        if (idx < 0) { AlertUtil.showError("Error", "Please select a patient."); return; }
        selectedPatientId = patientList.get(idx).getId();
        loadRecords(selectedPatientId);
    }

    private void loadRecords(int patientId) {
        Task<List<HealthRecord>> task = new Task<>() {
            @Override protected List<HealthRecord> call() { return healthRecordDAO.findByPatient(patientId); }
        };
        task.setOnSucceeded(e -> {
            timelineLine.getChildren().clear();
            recordsBox.getChildren().clear();
            List<HealthRecord> records = task.getValue();

            if (records.isEmpty()) {
                recordsBox.getChildren().add(new Label("No health records found for this patient."));
                return;
            }

            for (int i = 0; i < records.size(); i++) {
                HealthRecord hr = records.get(i);

                // Timeline dot + connector line
                VBox dotContainer = new VBox();
                dotContainer.setAlignment(Pos.TOP_CENTER);
                Circle dot = new Circle(8);
                dot.setStyle("-fx-fill: #2D5BE3;");
                dotContainer.getChildren().add(dot);
                if (i < records.size() - 1) {
                    Region line = new Region();
                    line.setStyle("-fx-background-color: #CBD5E1; -fx-pref-width: 2; -fx-min-height: 60;");
                    line.setPrefHeight(80);
                    dotContainer.getChildren().add(line);
                }
                timelineLine.getChildren().add(dotContainer);

                // Record card
                VBox card = new VBox(6);
                card.setStyle("-fx-background-color: white; -fx-background-radius: 14; -fx-padding: 18; -fx-effect: dropshadow(gaussian, rgba(45,91,227,0.08), 14, 0, 0, 3);");

                Label dateL = new Label("📅  " + DateUtil.formatDate(hr.getVisitDate()));
                dateL.setStyle("-fx-font-weight: bold; -fx-text-fill: #2D5BE3; -fx-font-size: 14;");
                Label docL = new Label("👨‍⚕️  Dr. " + (hr.getDoctorName() != null ? hr.getDoctorName() : "ID " + hr.getDoctorId()));
                docL.setStyle("-fx-text-fill: #64748B; -fx-font-size: 12;");
                Label diagL = new Label("Diagnosis: " + (hr.getDiagnosis() != null ? hr.getDiagnosis() : "N/A"));
                diagL.setStyle("-fx-text-fill: #1E293B; -fx-font-size: 13;");
                diagL.setWrapText(true);
                Label presL = new Label("Prescription: " + (hr.getPrescription() != null ? hr.getPrescription() : "N/A"));
                presL.setStyle("-fx-text-fill: #1E293B; -fx-font-size: 13;");
                presL.setWrapText(true);

                HBox actions = new HBox(8);
                actions.setAlignment(Pos.CENTER_RIGHT);
                User user = SessionManager.getInstance().getCurrentUser();
                if (user != null && user.getRole() != User.Role.PATIENT) {
                    Button editBtn = new Button("Edit");
                    editBtn.setStyle("-fx-background-color: #2D5BE3; -fx-text-fill: white; -fx-background-radius: 8; -fx-font-size: 11; -fx-padding: 5 14; -fx-cursor: hand;");
                    editBtn.setOnAction(ev -> showRecordDialog(hr));
                    Button delBtn = new Button("Delete");
                    delBtn.setStyle("-fx-background-color: #EF4444; -fx-text-fill: white; -fx-background-radius: 8; -fx-font-size: 11; -fx-padding: 5 14; -fx-cursor: hand;");
                    delBtn.setOnAction(ev -> onDeleteRecord(hr));
                    actions.getChildren().addAll(editBtn, delBtn);
                }

                card.getChildren().addAll(dateL, docL, diagL, presL, actions);
                recordsBox.getChildren().add(card);
            }
        });
        new Thread(task).start();
    }

    @FXML
    private void onAddRecord() {
        if (selectedPatientId < 0) { AlertUtil.showError("Error", "Select a patient first."); return; }
        showRecordDialog(null);
    }

    private void onDeleteRecord(HealthRecord hr) {
        if (AlertUtil.showConfirmation("Delete Record", "Delete this health record?")) {
            Task<Boolean> task = new Task<>() {
                @Override protected Boolean call() { return healthRecordDAO.delete(hr.getId()); }
            };
            task.setOnSucceeded(e -> {
                if (task.getValue()) { AlertUtil.showSuccess("Record deleted."); loadRecords(selectedPatientId); }
                else AlertUtil.showError("Error", "Could not delete record.");
            });
            new Thread(task).start();
        }
    }

    private void showRecordDialog(HealthRecord existing) {
        Dialog<HealthRecord> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Add Health Record" : "Edit Health Record");
        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(12); grid.setVgap(10); grid.setPadding(new Insets(20));

        // Doctor selector
        ComboBox<String> doctorCombo = new ComboBox<>();
        List<Doctor> doctors = doctorDAO.findAll();
        for (Doctor d : doctors) doctorCombo.getItems().add(d.getId() + " - " + d.getFullName());
        if (existing != null) {
            for (int i = 0; i < doctors.size(); i++) {
                if (doctors.get(i).getId() == existing.getDoctorId()) { doctorCombo.getSelectionModel().select(i); break; }
            }
        }
        // Auto-select if current user is doctor
        User user = SessionManager.getInstance().getCurrentUser();
        if (user != null && user.getRole() == User.Role.DOCTOR) {
            for (int i = 0; i < doctors.size(); i++) {
                if (doctors.get(i).getId() == user.getId()) { doctorCombo.getSelectionModel().select(i); doctorCombo.setDisable(true); break; }
            }
        }

        DatePicker dateF = new DatePicker(existing != null ? existing.getVisitDate() : LocalDate.now());
        TextArea diagF = new TextArea(existing != null && existing.getDiagnosis() != null ? existing.getDiagnosis() : "");
        diagF.setPrefRowCount(3); diagF.setPromptText("Enter diagnosis...");
        TextArea presF = new TextArea(existing != null && existing.getPrescription() != null ? existing.getPrescription() : "");
        presF.setPrefRowCount(3); presF.setPromptText("Enter prescription...");

        grid.add(new Label("Doctor:"), 0, 0); grid.add(doctorCombo, 1, 0);
        grid.add(new Label("Visit Date:"), 0, 1); grid.add(dateF, 1, 1);
        grid.add(new Label("Diagnosis:"), 0, 2); grid.add(diagF, 1, 2);
        grid.add(new Label("Prescription:"), 0, 3); grid.add(presF, 1, 3);

        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(btn -> {
            if (btn == saveType) {
                if (doctorCombo.getSelectionModel().isEmpty()) { AlertUtil.showError("Validation", "Select a doctor."); return null; }
                if (diagF.getText().isBlank()) { AlertUtil.showError("Validation", "Diagnosis required."); return null; }

                HealthRecord hr = existing != null ? existing : new HealthRecord();
                hr.setPatientId(selectedPatientId);
                hr.setDoctorId(doctors.get(doctorCombo.getSelectionModel().getSelectedIndex()).getId());
                hr.setVisitDate(dateF.getValue());
                hr.setDiagnosis(diagF.getText().trim());
                hr.setPrescription(presF.getText().trim());

                if (existing == null) healthRecordDAO.save(hr);
                else healthRecordDAO.update(hr);
                return hr;
            }
            return null;
        });

        Optional<HealthRecord> result = dialog.showAndWait();
        if (result.isPresent()) { AlertUtil.showSuccess("Record saved."); loadRecords(selectedPatientId); }
    }

    // ── Navigation ──
    @FXML private void onNavHome(javafx.event.ActionEvent e) { SceneNavigator.navigateTo("Dashboard.fxml", e); }
    @FXML private void onNavAppointments(javafx.event.ActionEvent e) { SceneNavigator.navigateTo("AppointmentPage.fxml", e); }
    @FXML private void onNavPatients(javafx.event.ActionEvent e) { SceneNavigator.navigateTo("PatientManagement.fxml", e); }
    @FXML private void onNavDoctors(javafx.event.ActionEvent e) { SceneNavigator.navigateTo("DoctorManagement.fxml", e); }
    @FXML private void onNavRecords(javafx.event.ActionEvent e) { SceneNavigator.navigateTo("HealthRecords.fxml", e); }
    @FXML private void onLogout(javafx.event.ActionEvent e) { SessionManager.getInstance().logout(); SceneNavigator.navigateTo("Login.fxml", e); }
}
