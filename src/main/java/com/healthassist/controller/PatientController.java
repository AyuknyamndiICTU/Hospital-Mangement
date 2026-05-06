package com.healthassist.controller;

import com.healthassist.dao.HealthRecordDAO;
import com.healthassist.dao.PatientDAO;
import com.healthassist.model.HealthRecord;
import com.healthassist.model.Patient;
import com.healthassist.model.User;
import com.healthassist.service.AuthService;
import com.healthassist.util.*;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class PatientController {
    @FXML private TextField searchField;
    @FXML private Button addPatientBtn;
    @FXML private TableView<Patient> patientTable;
    @FXML private TableColumn<Patient, Integer> colId;
    @FXML private TableColumn<Patient, String> colName, colEmail, colBlood, colPhone;
    @FXML private TableColumn<Patient, LocalDate> colDob;
    @FXML private TableColumn<Patient, String> colActions;
    @FXML private VBox recordsPanel, recordsListBox;
    @FXML private Label recordsPanelTitle;

    private final PatientDAO patientDAO = new PatientDAO();
    private final HealthRecordDAO healthRecordDAO = new HealthRecordDAO();
    private final AuthService authService = new AuthService();

    @FXML
    public void initialize() {
        setupActionsColumn();
        loadPatients("");

        // Role-based access
        User user = SessionManager.getInstance().getCurrentUser();
        if (user != null && user.getRole() == User.Role.PATIENT) {
            addPatientBtn.setVisible(false);
            addPatientBtn.setManaged(false);
        }
    }

    private void setupActionsColumn() {
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn = new Button("Edit");
            private final Button viewBtn = new Button("Records");
            private final Button deleteBtn = new Button("Delete");
            private final HBox box = new HBox(6, editBtn, viewBtn, deleteBtn);
            {
                editBtn.setStyle("-fx-background-color: #2D5BE3; -fx-text-fill: white; -fx-background-radius: 6; -fx-font-size: 11; -fx-padding: 4 10; -fx-cursor: hand;");
                viewBtn.setStyle("-fx-background-color: #22C55E; -fx-text-fill: white; -fx-background-radius: 6; -fx-font-size: 11; -fx-padding: 4 10; -fx-cursor: hand;");
                deleteBtn.setStyle("-fx-background-color: #EF4444; -fx-text-fill: white; -fx-background-radius: 6; -fx-font-size: 11; -fx-padding: 4 10; -fx-cursor: hand;");
                box.setAlignment(Pos.CENTER);
                editBtn.setOnAction(e -> onEdit(getTableView().getItems().get(getIndex())));
                viewBtn.setOnAction(e -> onViewRecords(getTableView().getItems().get(getIndex())));
                deleteBtn.setOnAction(e -> onDelete(getTableView().getItems().get(getIndex())));

                User user = SessionManager.getInstance().getCurrentUser();
                if (user != null && user.getRole() == User.Role.PATIENT) {
                    deleteBtn.setVisible(false);
                    editBtn.setVisible(false);
                }
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });
    }

    private void loadPatients(String query) {
        Task<List<Patient>> task = new Task<>() {
            @Override protected List<Patient> call() {
                return query.isEmpty() ? patientDAO.findAll() : patientDAO.searchByName(query);
            }
        };
        task.setOnSucceeded(e -> {
            patientTable.getItems().clear();
            patientTable.getItems().addAll(task.getValue());
        });
        new Thread(task).start();
    }

    @FXML private void onSearch() { loadPatients(searchField.getText().trim()); }

    @FXML
    private void onAddPatient() {
        showPatientDialog(null);
    }

    private void onEdit(Patient patient) {
        showPatientDialog(patient);
    }

    private void onDelete(Patient patient) {
        if (AlertUtil.showConfirmation("Delete Patient", "Are you sure you want to delete " + patient.getFullName() + "?")) {
            Task<Boolean> task = new Task<>() {
                @Override protected Boolean call() { return patientDAO.delete(patient.getId()); }
            };
            task.setOnSucceeded(e -> {
                if (task.getValue()) {
                    AlertUtil.showSuccess("Patient deleted successfully.");
                    loadPatients("");
                } else {
                    AlertUtil.showError("Error", "Could not delete patient.");
                }
            });
            new Thread(task).start();
        }
    }

    private void onViewRecords(Patient patient) {
        recordsPanel.setVisible(true);
        recordsPanel.setManaged(true);
        recordsPanelTitle.setText("Health Records — " + patient.getFullName());

        Task<List<HealthRecord>> task = new Task<>() {
            @Override protected List<HealthRecord> call() { return healthRecordDAO.findByPatient(patient.getId()); }
        };
        task.setOnSucceeded(e -> {
            recordsListBox.getChildren().clear();
            List<HealthRecord> records = task.getValue();
            if (records.isEmpty()) {
                recordsListBox.getChildren().add(new Label("No health records found."));
            }
            for (HealthRecord hr : records) {
                VBox card = new VBox(4);
                card.setStyle("-fx-background-color: #F8FAFC; -fx-background-radius: 10; -fx-padding: 12;");
                Label date = new Label("📅  " + DateUtil.formatDate(hr.getVisitDate()));
                date.setStyle("-fx-font-weight: bold; -fx-text-fill: #2D5BE3; -fx-font-size: 12;");
                Label doctor = new Label("Doctor: " + (hr.getDoctorName() != null ? hr.getDoctorName() : "ID " + hr.getDoctorId()));
                doctor.setStyle("-fx-text-fill: #64748B; -fx-font-size: 11;");
                Label diag = new Label("Diagnosis: " + (hr.getDiagnosis() != null ? hr.getDiagnosis() : "N/A"));
                diag.setStyle("-fx-text-fill: #1E293B; -fx-font-size: 12;");
                diag.setWrapText(true);
                Label pres = new Label("Prescription: " + (hr.getPrescription() != null ? hr.getPrescription() : "N/A"));
                pres.setStyle("-fx-text-fill: #1E293B; -fx-font-size: 12;");
                pres.setWrapText(true);
                card.getChildren().addAll(date, doctor, diag, pres);
                recordsListBox.getChildren().add(card);
            }
        });
        new Thread(task).start();
    }

    @FXML private void onCloseRecords() {
        recordsPanel.setVisible(false);
        recordsPanel.setManaged(false);
    }

    private void showPatientDialog(Patient existing) {
        Dialog<Patient> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Add Patient" : "Edit Patient");
        dialog.setHeaderText(existing == null ? "Register a new patient" : "Edit patient details");

        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(12); grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField nameF = new TextField(existing != null ? existing.getFullName() : "");
        nameF.setPromptText("Full Name");
        TextField emailF = new TextField(existing != null ? existing.getEmail() : "");
        emailF.setPromptText("Email");
        PasswordField passF = new PasswordField();
        passF.setPromptText("Password (min 6 chars)");
        DatePicker dobF = new DatePicker(existing != null ? existing.getDateOfBirth() : null);
        TextField bloodF = new TextField(existing != null && existing.getBloodType() != null ? existing.getBloodType() : "");
        bloodF.setPromptText("Blood Type");
        TextField phoneF = new TextField(existing != null && existing.getPhone() != null ? existing.getPhone() : "");
        phoneF.setPromptText("Phone");
        TextField addressF = new TextField(existing != null && existing.getAddress() != null ? existing.getAddress() : "");
        addressF.setPromptText("Address");
        TextField emergencyF = new TextField(existing != null && existing.getEmergencyContact() != null ? existing.getEmergencyContact() : "");
        emergencyF.setPromptText("Emergency Contact");

        int row = 0;
        grid.add(new Label("Full Name:"), 0, row); grid.add(nameF, 1, row++);
        grid.add(new Label("Email:"), 0, row); grid.add(emailF, 1, row++);
        if (existing == null) { grid.add(new Label("Password:"), 0, row); grid.add(passF, 1, row++); }
        grid.add(new Label("Date of Birth:"), 0, row); grid.add(dobF, 1, row++);
        grid.add(new Label("Blood Type:"), 0, row); grid.add(bloodF, 1, row++);
        grid.add(new Label("Phone:"), 0, row); grid.add(phoneF, 1, row++);
        grid.add(new Label("Address:"), 0, row); grid.add(addressF, 1, row++);
        grid.add(new Label("Emergency Contact:"), 0, row); grid.add(emergencyF, 1, row++);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> {
            if (btn == saveType) {
                // Validation
                if (nameF.getText().isBlank()) { AlertUtil.showError("Validation", "Name is required."); return null; }
                if (!DateUtil.isValidEmail(emailF.getText())) { AlertUtil.showError("Validation", "Valid email is required."); return null; }
                if (existing == null && passF.getText().length() < 6) { AlertUtil.showError("Validation", "Password must be 6+ characters."); return null; }

                Patient p = existing != null ? existing : new Patient();
                p.setFullName(nameF.getText().trim());
                p.setEmail(emailF.getText().trim().toLowerCase());
                p.setRole(User.Role.PATIENT);
                p.setDateOfBirth(dobF.getValue());
                p.setBloodType(bloodF.getText().trim());
                p.setPhone(phoneF.getText().trim());
                p.setAddress(addressF.getText().trim());
                p.setEmergencyContact(emergencyF.getText().trim());

                if (existing == null) {
                    // New patient — register via AuthService (creates users row)
                    int id = authService.register(p, passF.getText());
                    if (id > 0) {
                        // Insert patient-specific row into patients table
                        p.setId(id);
                        insertPatientRow(p);
                    }
                } else {
                    patientDAO.update(p);
                }
                return p;
            }
            return null;
        });

        Optional<Patient> result = dialog.showAndWait();
        if (result.isPresent()) {
            AlertUtil.showSuccess("Patient saved successfully.");
            loadPatients("");
        }
    }

    private void insertPatientRow(Patient p) {
        // Direct insert into patients table since user was already created by AuthService
        java.sql.Connection conn = null;
        try {
            conn = com.healthassist.config.DatabaseConfig.getInstance().getConnection();
            var ps = conn.prepareStatement("INSERT INTO patients (id, date_of_birth, blood_type, address, phone, emergency_contact) VALUES (?, ?, ?, ?, ?, ?)");
            ps.setInt(1, p.getId());
            ps.setDate(2, p.getDateOfBirth() != null ? java.sql.Date.valueOf(p.getDateOfBirth()) : null);
            ps.setString(3, p.getBloodType());
            ps.setString(4, p.getAddress());
            ps.setString(5, p.getPhone());
            ps.setString(6, p.getEmergencyContact());
            ps.executeUpdate();
            ps.close();
        } catch (Exception e) {
            System.err.println("insertPatientRow error: " + e.getMessage());
        } finally {
            if (conn != null) com.healthassist.config.DatabaseConfig.getInstance().releaseConnection(conn);
        }
    }

    // ── Navigation ──
    @FXML private void onNavHome(javafx.event.ActionEvent e) { SceneNavigator.navigateTo("Dashboard.fxml", e); }
    @FXML private void onNavAppointments(javafx.event.ActionEvent e) { SceneNavigator.navigateTo("AppointmentPage.fxml", e); }
    @FXML private void onNavPatients(javafx.event.ActionEvent e) { SceneNavigator.navigateTo("PatientManagement.fxml", e); }
    @FXML private void onNavDoctors(javafx.event.ActionEvent e) { SceneNavigator.navigateTo("DoctorManagement.fxml", e); }
    @FXML private void onNavRecords(javafx.event.ActionEvent e) { SceneNavigator.navigateTo("HealthRecords.fxml", e); }
    @FXML private void onLogout(javafx.event.ActionEvent e) { SessionManager.getInstance().logout(); SceneNavigator.navigateTo("Login.fxml", e); }
}
