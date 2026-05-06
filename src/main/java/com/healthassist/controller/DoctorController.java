package com.healthassist.controller;

import com.healthassist.dao.DoctorDAO;
import com.healthassist.model.Doctor;
import com.healthassist.model.User;
import com.healthassist.service.AuthService;
import com.healthassist.util.*;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import java.math.BigDecimal;
import java.util.*;

public class DoctorController {
    @FXML private TextField searchField;
    @FXML private Button addDoctorBtn;
    @FXML private FlowPane doctorCardsPane;

    private final DoctorDAO doctorDAO = new DoctorDAO();
    private final AuthService authService = new AuthService();

    @FXML
    public void initialize() {
        loadDoctors("");
        User user = SessionManager.getInstance().getCurrentUser();
        if (user != null && user.getRole() != User.Role.ADMIN) {
            addDoctorBtn.setVisible(false);
            addDoctorBtn.setManaged(false);
        }
    }

    private void loadDoctors(String query) {
        Task<List<Doctor>> task = new Task<>() {
            @Override protected List<Doctor> call() {
                return query.isEmpty() ? doctorDAO.findAll() : doctorDAO.search(query);
            }
        };
        task.setOnSucceeded(e -> {
            doctorCardsPane.getChildren().clear();
            for (Doctor d : task.getValue()) doctorCardsPane.getChildren().add(createDoctorCard(d));
            if (doctorCardsPane.getChildren().isEmpty()) doctorCardsPane.getChildren().add(new Label("No doctors found."));
        });
        new Thread(task).start();
    }

    private VBox createDoctorCard(Doctor doc) {
        VBox card = new VBox(10);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 16; -fx-padding: 20; -fx-effect: dropshadow(gaussian, rgba(45,91,227,0.10), 18, 0, 0, 4); -fx-pref-width: 220; -fx-cursor: hand;");
        card.setAlignment(Pos.CENTER);

        StackPane avatar = new StackPane();
        Circle c = new Circle(32); c.setStyle("-fx-fill: linear-gradient(to bottom, #2D5BE3, #6C8EFF);");
        String init = "";
        for (String p : doc.getFullName().split(" ")) { if (!p.isEmpty()) init += p.charAt(0); }
        Label il = new Label(init.toUpperCase());
        il.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 18;");
        avatar.getChildren().addAll(c, il);

        Label name = new Label(doc.getFullName());
        name.setStyle("-fx-font-weight: bold; -fx-text-fill: #1E293B; -fx-font-size: 15;");
        Label spec = new Label(doc.getSpecialization() != null ? doc.getSpecialization() : "General");
        spec.setStyle("-fx-text-fill: #64748B; -fx-font-size: 12;");
        Label hosp = new Label("🏥 " + (doc.getHospital() != null ? doc.getHospital() : "N/A"));
        hosp.setStyle("-fx-text-fill: #64748B; -fx-font-size: 11;");
        Label rate = new Label(doc.getRatePerHour() != null ? "$" + doc.getRatePerHour() + "/hr" : "");
        rate.setStyle("-fx-text-fill: #2D5BE3; -fx-font-size: 13; -fx-font-weight: bold;");

        HBox btns = new HBox(8);
        btns.setAlignment(Pos.CENTER);
        Button editBtn = new Button("Edit");
        editBtn.setStyle("-fx-background-color: #2D5BE3; -fx-text-fill: white; -fx-background-radius: 8; -fx-font-size: 11; -fx-padding: 5 14; -fx-cursor: hand;");
        editBtn.setOnAction(e -> showDoctorDialog(doc));
        Button delBtn = new Button("Delete");
        delBtn.setStyle("-fx-background-color: #EF4444; -fx-text-fill: white; -fx-background-radius: 8; -fx-font-size: 11; -fx-padding: 5 14; -fx-cursor: hand;");
        delBtn.setOnAction(e -> onDelete(doc));
        btns.getChildren().addAll(editBtn, delBtn);

        User user = SessionManager.getInstance().getCurrentUser();
        if (user != null && user.getRole() != User.Role.ADMIN) {
            editBtn.setVisible(false); delBtn.setVisible(false);
        }

        card.getChildren().addAll(avatar, name, spec, hosp, rate, btns);
        return card;
    }

    private void onDelete(Doctor doc) {
        if (AlertUtil.showConfirmation("Delete Doctor", "Delete " + doc.getFullName() + "?")) {
            Task<Boolean> task = new Task<>() {
                @Override protected Boolean call() { return doctorDAO.delete(doc.getId()); }
            };
            task.setOnSucceeded(e -> {
                if (task.getValue()) { AlertUtil.showSuccess("Doctor deleted."); loadDoctors(""); }
                else AlertUtil.showError("Error", "Could not delete doctor.");
            });
            new Thread(task).start();
        }
    }

    @FXML private void onAddDoctor() { showDoctorDialog(null); }
    @FXML private void onSearch() { loadDoctors(searchField.getText().trim()); }

    private void showDoctorDialog(Doctor existing) {
        Dialog<Doctor> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Add Doctor" : "Edit Doctor");
        dialog.setHeaderText(existing == null ? "Register a new doctor" : "Edit doctor details");
        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(12); grid.setVgap(10); grid.setPadding(new Insets(20));

        TextField nameF = new TextField(existing != null ? existing.getFullName() : "");
        TextField emailF = new TextField(existing != null ? existing.getEmail() : "");
        PasswordField passF = new PasswordField(); passF.setPromptText("Password");
        TextField specF = new TextField(existing != null && existing.getSpecialization() != null ? existing.getSpecialization() : "");
        TextField hospF = new TextField(existing != null && existing.getHospital() != null ? existing.getHospital() : "");
        TextField rateF = new TextField(existing != null && existing.getRatePerHour() != null ? existing.getRatePerHour().toString() : "");
        TextField hoursF = new TextField(existing != null && existing.getWorkingHours() != null ? existing.getWorkingHours() : "");
        hoursF.setPromptText("e.g. 08:00-17:00");

        // Schedule day checkboxes
        String[] dayNames = {"MON","TUE","WED","THU","FRI","SAT","SUN"};
        CheckBox[] dayCbs = new CheckBox[7];
        HBox daysBox = new HBox(6);
        List<Map<String, String>> existingSchedule = existing != null ? doctorDAO.getSchedule(existing.getId()) : new ArrayList<>();
        Set<String> scheduledDays = new HashSet<>();
        for (var entry : existingSchedule) scheduledDays.add(entry.get("day"));
        for (int i = 0; i < 7; i++) {
            dayCbs[i] = new CheckBox(dayNames[i]);
            dayCbs[i].setSelected(scheduledDays.contains(dayNames[i]));
            daysBox.getChildren().add(dayCbs[i]);
        }
        TextField schedStartF = new TextField("08:00"); schedStartF.setPrefWidth(70);
        TextField schedEndF = new TextField("17:00"); schedEndF.setPrefWidth(70);
        if (!existingSchedule.isEmpty()) {
            schedStartF.setText(existingSchedule.get(0).get("start"));
            schedEndF.setText(existingSchedule.get(0).get("end"));
        }
        HBox timeBox = new HBox(8, new Label("Start:"), schedStartF, new Label("End:"), schedEndF);
        timeBox.setAlignment(Pos.CENTER_LEFT);

        int row = 0;
        grid.add(new Label("Full Name:"), 0, row); grid.add(nameF, 1, row++);
        grid.add(new Label("Email:"), 0, row); grid.add(emailF, 1, row++);
        if (existing == null) { grid.add(new Label("Password:"), 0, row); grid.add(passF, 1, row++); }
        grid.add(new Label("Specialization:"), 0, row); grid.add(specF, 1, row++);
        grid.add(new Label("Hospital:"), 0, row); grid.add(hospF, 1, row++);
        grid.add(new Label("Rate/Hour ($):"), 0, row); grid.add(rateF, 1, row++);
        grid.add(new Label("Working Hours:"), 0, row); grid.add(hoursF, 1, row++);
        grid.add(new Label("Schedule Days:"), 0, row); grid.add(daysBox, 1, row++);
        grid.add(new Label("Schedule Time:"), 0, row); grid.add(timeBox, 1, row++);

        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(btn -> {
            if (btn == saveType) {
                if (nameF.getText().isBlank()) { AlertUtil.showError("Validation", "Name required."); return null; }
                if (!DateUtil.isValidEmail(emailF.getText())) { AlertUtil.showError("Validation", "Valid email required."); return null; }
                if (existing == null && passF.getText().length() < 6) { AlertUtil.showError("Validation", "Password 6+ chars."); return null; }

                Doctor d = existing != null ? existing : new Doctor();
                d.setFullName(nameF.getText().trim());
                d.setEmail(emailF.getText().trim().toLowerCase());
                d.setRole(User.Role.DOCTOR);
                d.setSpecialization(specF.getText().trim());
                d.setHospital(hospF.getText().trim());
                d.setWorkingHours(hoursF.getText().trim());
                try { d.setRatePerHour(new BigDecimal(rateF.getText().trim())); } catch (Exception ex) { d.setRatePerHour(BigDecimal.ZERO); }

                if (existing == null) {
                    int id = authService.register(d, passF.getText());
                    if (id > 0) { d.setId(id); insertDoctorRow(d); }
                } else {
                    doctorDAO.update(d);
                }

                // Save schedule
                if (d.getId() > 0) {
                    List<Map<String, String>> schedule = new ArrayList<>();
                    for (int i = 0; i < 7; i++) {
                        if (dayCbs[i].isSelected()) {
                            Map<String, String> entry = new HashMap<>();
                            entry.put("day", dayNames[i]);
                            entry.put("start", schedStartF.getText().trim());
                            entry.put("end", schedEndF.getText().trim());
                            schedule.add(entry);
                        }
                    }
                    doctorDAO.saveSchedule(d.getId(), schedule);
                }
                return d;
            }
            return null;
        });

        Optional<Doctor> result = dialog.showAndWait();
        if (result.isPresent()) { AlertUtil.showSuccess("Doctor saved."); loadDoctors(""); }
    }

    private void insertDoctorRow(Doctor d) {
        java.sql.Connection conn = null;
        try {
            conn = com.healthassist.config.DatabaseConfig.getInstance().getConnection();
            var ps = conn.prepareStatement("INSERT INTO doctors (id, specialization, rate_per_hour, hospital, working_hours) VALUES (?, ?, ?, ?, ?)");
            ps.setInt(1, d.getId());
            ps.setString(2, d.getSpecialization());
            ps.setBigDecimal(3, d.getRatePerHour() != null ? d.getRatePerHour() : BigDecimal.ZERO);
            ps.setString(4, d.getHospital());
            ps.setString(5, d.getWorkingHours());
            ps.executeUpdate(); ps.close();
        } catch (Exception e) {
            System.err.println("insertDoctorRow error: " + e.getMessage());
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
