package com.healthassist.controller;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.healthassist.config.DatabaseConfig.getInstance;
import com.healthassist.dao.DoctorDAO;
import com.healthassist.dao.OTPDAO;
import com.healthassist.dao.PatientDAO;
import com.healthassist.model.Doctor;
import com.healthassist.model.Patient;
import com.healthassist.service.AuthService;
import com.healthassist.service.OTPService;
import com.healthassist.service.notifier.ConsoleNotifier;
import com.healthassist.util.DateUtil;
import com.healthassist.util.PendingSignupSession;
import com.healthassist.util.SceneNavigator;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

public class SignUpController {

    @FXML private ComboBox<String> roleCombo;

    @FXML private TextField fullNameField;
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;

    // Patient fields
    @FXML private VBox patientBox;
    @FXML private DatePicker dobField;
    @FXML private TextField bloodTypeField;
    @FXML private TextField addressField;
    @FXML private TextField phoneField;
    @FXML private TextField emergencyContactField;

    // Doctor fields
    @FXML private VBox doctorBox;
    @FXML private TextField specializationField;
    @FXML private TextField hospitalField;
    @FXML private TextField rateField;
    @FXML private TextField workingHoursField;

    @FXML private Button signUpBtn;
    @FXML private Label errorLabel;

    private final AuthService authService = new AuthService();
    private final DoctorDAO doctorDAO = new DoctorDAO();
    private final PatientDAO patientDAO = new PatientDAO();
    private final OTPService otpService = new OTPService(new OTPDAO(), new com.healthassist.dao.UserDAO(), new ConsoleNotifier());

    @FXML
    public void initialize() {
        errorLabel.setText("");
        roleCombo.getItems().clear();
        roleCombo.getItems().addAll("PATIENT", "DOCTOR");
        roleCombo.setValue("PATIENT");
        updateRoleVisibility();

        roleCombo.setOnAction(e -> updateRoleVisibility());
    }

    private void updateRoleVisibility() {
        boolean isPatient = "PATIENT".equals(roleCombo.getValue());
        if (patientBox != null) patientBox.setVisible(isPatient);
        if (patientBox != null) patientBox.setManaged(isPatient);
        if (doctorBox != null) doctorBox.setVisible(!isPatient);
        if (doctorBox != null) doctorBox.setManaged(!isPatient);
    }

    @FXML
    private void onSignUp(javafx.event.ActionEvent event) {
        errorLabel.setText("");

        String role = roleCombo.getValue();
        String fullName = fullNameField.getText() != null ? fullNameField.getText().trim() : "";
        String email = emailField.getText() != null ? emailField.getText().trim().toLowerCase() : "";
        String password = passwordField.getText();

        if (fullName.isBlank()) {
            showError("Full name is required.");
            return;
        }
        if (!DateUtil.isValidEmail(email)) {
            showError("Enter a valid email address.");
            return;
        }
        if (password == null || password.length() < 6) {
            showError("Password must be at least 6 characters.");
            return;
        }

        if ("PATIENT".equals(role)) {
            if (dobField.getValue() == null) { showError("Date of birth is required."); return; }
            if (bloodTypeField.getText() == null || bloodTypeField.getText().isBlank()) { showError("Blood type is required."); return; }
            if (addressField.getText() == null || addressField.getText().isBlank()) { showError("Address is required."); return; }
            if (phoneField.getText() == null || phoneField.getText().isBlank()) { showError("Phone is required."); return; }
            if (emergencyContactField.getText() == null || emergencyContactField.getText().isBlank()) { showError("Emergency contact is required."); return; }
        } else {
            if (specializationField.getText() == null || specializationField.getText().isBlank()) { showError("Specialization is required."); return; }
            if (hospitalField.getText() == null || hospitalField.getText().isBlank()) { showError("Hospital is required."); return; }
            if (rateField.getText() == null || rateField.getText().isBlank()) { showError("Rate per hour is required."); return; }
            if (workingHoursField.getText() == null || workingHoursField.getText().isBlank()) { showError("Working hours are required."); return; }
        }

        signUpBtn.setDisable(true);
        signUpBtn.setText("Creating account...");

        Task<Integer> task = new Task<>() {
            @Override
            protected Integer call() {
                if ("PATIENT".equals(role)) {
                    Patient p = new Patient();
                    p.setFullName(fullName);
                    p.setEmail(email);
                    p.setDateOfBirth(dobField.getValue());
                    p.setBloodType(bloodTypeField.getText().trim());
                    p.setAddress(addressField.getText().trim());
                    p.setPhone(phoneField.getText().trim());
                    p.setEmergencyContact(emergencyContactField.getText().trim());

                    int userId = authService.register(p, password);
                    if (userId <= 0) return -1;

                    // Insert patient details row (if not already created by DAO)
                    insertPatientRow(userId, p.getDateOfBirth(), p.getBloodType(), p.getAddress(), p.getPhone(), p.getEmergencyContact());
                    return userId;
                } else {
                    Doctor d = new Doctor();
                    d.setFullName(fullName);
                    d.setEmail(email);
                    d.setSpecialization(specializationField.getText().trim());
                    d.setHospital(hospitalField.getText().trim());

                    try {
                        d.setRatePerHour(new BigDecimal(rateField.getText().trim()));
                    } catch (Exception ex) {
                        d.setRatePerHour(BigDecimal.ZERO);
                    }
                    d.setWorkingHours(workingHoursField.getText().trim());

                    int userId = authService.register(d, password);
                    if (userId <= 0) return -1;

                    insertDoctorRow(userId, d.getSpecialization(), d.getRatePerHour(), d.getHospital(), d.getWorkingHours());

                    // Default schedule Mon-Fri 08:00-17:00
                    List<Map<String, String>> schedule = new ArrayList<>();
                    String[] days = {"MON", "TUE", "WED", "THU", "FRI"};
                    for (String day : days) {
                        Map<String, String> entry = new HashMap<>();
                        entry.put("day", day);
                        entry.put("start", "08:00");
                        entry.put("end", "17:00");
                        schedule.add(entry);
                    }
                    doctorDAO.saveSchedule(userId, schedule);

                    return userId;
                }
            }
        };

        task.setOnSucceeded(e -> {
            int userId = task.getValue() != null ? task.getValue() : -1;
            if (userId <= 0) {
                signUpBtn.setDisable(false);
                signUpBtn.setText("Sign Up");
                showError("Could not create account. Email may already be used.");
                return;
            }

            boolean otpSent = otpService.requestOtp(userId, email);
            if (!otpSent) {
                signUpBtn.setDisable(false);
                signUpBtn.setText("Sign Up");
                showError("Could not send OTP. Please try again.");
                return;
            }

            PendingSignupSession pending = PendingSignupSession.getInstance();
            pending.setPendingUserId(userId);
            pending.setPendingDestination(email);

            SceneNavigator.navigateTo("OTPVerification.fxml", event);
        });

        task.setOnFailed(e -> {
            signUpBtn.setDisable(false);
            signUpBtn.setText("Sign Up");
            showError("Signup failed. Please check your connection.");
        });

        new Thread(task).start();
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setStyle("-fx-text-fill: #EF4444;");
    }

    private void insertPatientRow(int userId, LocalDate dob, String bloodType, String address, String phone, String emergencyContact) {
        try (Connection conn = getInstance().getConnection()) {
            PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO patients (id, date_of_birth, blood_type, address, phone, emergency_contact) VALUES (?, ?, ?, ?, ?, ?)"
            );
            ps.setInt(1, userId);
            ps.setDate(2, dob != null ? java.sql.Date.valueOf(dob) : null);
            ps.setString(3, bloodType);
            ps.setString(4, address);
            ps.setString(5, phone);
            ps.setString(6, emergencyContact);
            ps.executeUpdate();
            ps.close();
        } catch (Exception ignored) {
            // If DAO insert already happened or row exists, don't block signup.
        }
    }

    private void insertDoctorRow(int userId, String specialization, BigDecimal ratePerHour, String hospital, String workingHours) {
        try (Connection conn = getInstance().getConnection()) {
            PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO doctors (id, specialization, rate_per_hour, hospital, working_hours) VALUES (?, ?, ?, ?, ?)"
            );
            ps.setInt(1, userId);
            ps.setString(2, specialization);
            ps.setBigDecimal(3, ratePerHour != null ? ratePerHour : BigDecimal.ZERO);
            ps.setString(4, hospital);
            ps.setString(5, workingHours);
            ps.executeUpdate();
            ps.close();
        } catch (Exception ignored) {
            // If row exists already, don't block signup.
        }
    }

    @FXML
    private void onGoToLogin(javafx.event.ActionEvent event) {
        SceneNavigator.navigateTo("Login.fxml", event);
    }
}
