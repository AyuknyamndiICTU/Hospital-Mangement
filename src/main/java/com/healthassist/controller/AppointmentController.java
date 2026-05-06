package com.healthassist.controller;

import com.healthassist.dao.DoctorDAO;
import com.healthassist.dao.PatientDAO;
import com.healthassist.model.Appointment;
import com.healthassist.model.Doctor;
import com.healthassist.model.Patient;
import com.healthassist.model.User;
import com.healthassist.service.AppointmentService;
import com.healthassist.util.*;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import java.time.*;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

public class AppointmentController {
    @FXML private Label bookMonthLabel;
    @FXML private GridPane bookCalendarGrid;
    @FXML private FlowPane timeSlotsPane, doctorCardsPane;
    @FXML private TextArea concernsArea;
    @FXML private ComboBox<String> patientCombo;
    @FXML private Button bookBtn;
    @FXML private TextField doctorSearchField;
    @FXML private VBox detailPanel;
    @FXML private Label detailAvatar, detailName, detailSpec, detailHospital, detailRate, detailHours, detailEmail;

    private final DoctorDAO doctorDAO = new DoctorDAO();
    private final PatientDAO patientDAO = new PatientDAO();
    private final AppointmentService appointmentService = new AppointmentService();
    private YearMonth bookMonth = YearMonth.now();
    private LocalDate selectedDate;
    private LocalTime selectedTime;
    private Doctor selectedDoctor;
    private List<Patient> patientList;

    @FXML
    public void initialize() {
        renderBookCalendar();
        loadDoctors("");
        loadPatients();
    }

    private void loadDoctors(String query) {
        Task<List<Doctor>> task = new Task<>() {
            @Override protected List<Doctor> call() {
                return query.isEmpty() ? doctorDAO.findAll() : doctorDAO.search(query);
            }
        };
        task.setOnSucceeded(e -> {
            doctorCardsPane.getChildren().clear();
            for (Doctor d : task.getValue()) {
                doctorCardsPane.getChildren().add(createDoctorCard(d));
            }
            if (doctorCardsPane.getChildren().isEmpty()) {
                doctorCardsPane.getChildren().add(new Label("No doctors found."));
            }
        });
        new Thread(task).start();
    }

    private void loadPatients() {
        Task<List<Patient>> task = new Task<>() {
            @Override protected List<Patient> call() { return patientDAO.findAll(); }
        };
        task.setOnSucceeded(e -> {
            patientList = task.getValue();
            patientCombo.getItems().clear();
            for (Patient p : patientList) {
                patientCombo.getItems().add(p.getId() + " - " + p.getFullName());
            }
            // Auto-select if current user is patient
            User cur = SessionManager.getInstance().getCurrentUser();
            if (cur != null && cur.getRole() == User.Role.PATIENT) {
                for (int i = 0; i < patientList.size(); i++) {
                    if (patientList.get(i).getId() == cur.getId()) {
                        patientCombo.getSelectionModel().select(i);
                        patientCombo.setDisable(true);
                        break;
                    }
                }
            }
        });
        new Thread(task).start();
    }

    private VBox createDoctorCard(Doctor doc) {
        VBox card = new VBox(8);
        card.getStyleClass().add("doctor-card");
        card.setStyle("-fx-background-color: white; -fx-background-radius: 14; -fx-padding: 16; -fx-effect: dropshadow(gaussian, rgba(45,91,227,0.08), 12, 0, 0, 3); -fx-pref-width: 180; -fx-cursor: hand;");
        card.setAlignment(Pos.CENTER);

        StackPane avatarPane = new StackPane();
        Circle circle = new Circle(28);
        circle.setStyle("-fx-fill: #EEF2FF;");
        String initials = "";
        for (String p : doc.getFullName().split(" ")) { if (!p.isEmpty()) initials += p.charAt(0); }
        Label initLabel = new Label(initials.toUpperCase());
        initLabel.setStyle("-fx-text-fill: #2D5BE3; -fx-font-weight: bold; -fx-font-size: 16;");
        avatarPane.getChildren().addAll(circle, initLabel);

        Label name = new Label(doc.getFullName());
        name.setStyle("-fx-font-weight: bold; -fx-text-fill: #1E293B; -fx-font-size: 13;");
        Label spec = new Label(doc.getSpecialization() != null ? doc.getSpecialization() : "General");
        spec.setStyle("-fx-text-fill: #64748B; -fx-font-size: 11;");
        Label rate = new Label(doc.getRatePerHour() != null ? doc.getRatePerHour() + " FCFA/hr" : "");
        rate.setStyle("-fx-text-fill: #2D5BE3; -fx-font-size: 11; -fx-font-weight: bold;");

        Button selectBtn = new Button("Select");
        selectBtn.getStyleClass().add("btn-primary");
        selectBtn.setStyle("-fx-font-size: 11; -fx-padding: 6 16;");
        selectBtn.setOnAction(e -> onDoctorSelected(doc));

        card.getChildren().addAll(avatarPane, name, spec, rate, selectBtn);
        card.setOnMouseClicked(e -> showDoctorDetail(doc));
        return card;
    }

    private void onDoctorSelected(Doctor doc) {
        selectedDoctor = doc;
        showDoctorDetail(doc);
        if (selectedDate != null) loadTimeSlots();
    }

    private void showDoctorDetail(Doctor doc) {
        selectedDoctor = doc;
        detailPanel.setVisible(true);
        detailPanel.setManaged(true);
        String initials = "";
        for (String p : doc.getFullName().split(" ")) { if (!p.isEmpty()) initials += p.charAt(0); }
        detailAvatar.setText(initials.toUpperCase());
        detailName.setText(doc.getFullName());
        detailSpec.setText(doc.getSpecialization() != null ? doc.getSpecialization() : "General");
        detailHospital.setText("🏥  " + (doc.getHospital() != null ? doc.getHospital() : "N/A"));
        detailRate.setText("💰  " + (doc.getRatePerHour() != null ? doc.getRatePerHour() + " FCFA/hr" : "N/A"));
        detailHours.setText("🕐  " + (doc.getWorkingHours() != null ? doc.getWorkingHours() : "N/A"));
        detailEmail.setText("✉  " + doc.getEmail());
    }

    @FXML private void onSelectDetailDoctor() {
        if (selectedDoctor != null && selectedDate != null) loadTimeSlots();
    }

    // ── Calendar ──
    private void renderBookCalendar() {
        bookCalendarGrid.getChildren().clear();
        bookCalendarGrid.getColumnConstraints().clear();
        bookMonthLabel.setText(bookMonth.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + bookMonth.getYear());

        String[] days = {"Mo","Tu","We","Th","Fr","Sa","Su"};
        for (int i = 0; i < 7; i++) {
            ColumnConstraints cc = new ColumnConstraints(); cc.setHalignment(HPos.CENTER); cc.setPrefWidth(32);
            bookCalendarGrid.getColumnConstraints().add(cc);
            Label dl = new Label(days[i]);
            dl.setStyle("-fx-text-fill: #64748B; -fx-font-size: 10; -fx-font-weight: bold;");
            bookCalendarGrid.add(dl, i, 0);
        }

        LocalDate first = bookMonth.atDay(1);
        int startCol = first.getDayOfWeek().getValue() - 1;
        int row = 1, col = startCol;

        for (int day = 1; day <= bookMonth.lengthOfMonth(); day++) {
            final int d = day;
            Label dayLbl = new Label(String.valueOf(day));
            dayLbl.setPrefSize(30, 30);
            dayLbl.setAlignment(Pos.CENTER);
            dayLbl.setStyle("-fx-font-size: 11; -fx-text-fill: #1E293B; -fx-cursor: hand;");

            LocalDate thisDate = bookMonth.atDay(d);
            if (selectedDate != null && selectedDate.equals(thisDate)) {
                dayLbl.setStyle("-fx-background-color: #2D5BE3; -fx-text-fill: white; -fx-background-radius: 15; -fx-font-size: 11; -fx-font-weight: bold; -fx-cursor: hand;");
            } else if (thisDate.equals(LocalDate.now())) {
                dayLbl.setStyle("-fx-border-color: #2D5BE3; -fx-border-radius: 15; -fx-font-size: 11; -fx-cursor: hand;");
            }

            dayLbl.setOnMouseClicked(e -> {
                selectedDate = bookMonth.atDay(d);
                renderBookCalendar();
                loadTimeSlots();
            });
            bookCalendarGrid.add(dayLbl, col, row);
            col++; if (col > 6) { col = 0; row++; }
        }
    }

    @FXML private void onBookPrevMonth() { bookMonth = bookMonth.minusMonths(1); renderBookCalendar(); }
    @FXML private void onBookNextMonth() { bookMonth = bookMonth.plusMonths(1); renderBookCalendar(); }

    // ── Time Slots ──
    private void loadTimeSlots() {
        timeSlotsPane.getChildren().clear();
        if (selectedDoctor == null || selectedDate == null) return;

        Task<List<LocalTime>> task = new Task<>() {
            @Override protected List<LocalTime> call() {
                return appointmentService.getAvailableSlots(selectedDoctor.getId(), selectedDate);
            }
        };
        task.setOnSucceeded(e -> {
            List<LocalTime> slots = task.getValue();
            for (LocalTime t : slots) {
                Button slotBtn = new Button(t.toString());
                slotBtn.setStyle("-fx-background-color: #F0F4FF; -fx-text-fill: #2D5BE3; -fx-background-radius: 8; -fx-padding: 6 12; -fx-cursor: hand; -fx-font-size: 11;");
                slotBtn.setOnAction(ev -> {
                    selectedTime = t;
                    // Highlight selected
                    timeSlotsPane.getChildren().forEach(n -> {
                        if (n instanceof Button b) b.setStyle("-fx-background-color: #F0F4FF; -fx-text-fill: #2D5BE3; -fx-background-radius: 8; -fx-padding: 6 12; -fx-cursor: hand; -fx-font-size: 11;");
                    });
                    slotBtn.setStyle("-fx-background-color: #2D5BE3; -fx-text-fill: white; -fx-background-radius: 8; -fx-padding: 6 12; -fx-cursor: hand; -fx-font-size: 11;");
                });
                timeSlotsPane.getChildren().add(slotBtn);
            }
            if (slots.isEmpty()) {
                timeSlotsPane.getChildren().add(new Label("No available slots"));
            }
        });
        new Thread(task).start();
    }

    // ── Book ──
    @FXML
    private void onBookAppointment() {
        if (selectedDoctor == null) { AlertUtil.showError("Error", "Please select a doctor."); return; }
        if (selectedDate == null) { AlertUtil.showError("Error", "Please select a date."); return; }
        if (selectedTime == null) { AlertUtil.showError("Error", "Please select a time slot."); return; }
        if (patientCombo.getSelectionModel().isEmpty()) { AlertUtil.showError("Error", "Please select a patient."); return; }

        int patientId = patientList.get(patientCombo.getSelectionModel().getSelectedIndex()).getId();

        Appointment appt = new Appointment();
        appt.setPatientId(patientId);
        appt.setDoctorId(selectedDoctor.getId());
        appt.setAppointmentDatetime(LocalDateTime.of(selectedDate, selectedTime));
        appt.setStatus(Appointment.Status.PENDING);
        appt.setNotes(concernsArea.getText());

        bookBtn.setDisable(true);
        Task<Integer> task = new Task<>() {
            @Override protected Integer call() { return appointmentService.bookAppointment(appt); }
        };
        task.setOnSucceeded(e -> {
            int id = task.getValue();
            if (id > 0) {
                AlertUtil.showSuccess("Appointment booked successfully! ID: " + id);
                concernsArea.clear();
                selectedTime = null;
                loadTimeSlots();
            } else {
                AlertUtil.showError("Booking Failed", "Time slot conflict or error. Please choose another slot.");
            }
            bookBtn.setDisable(false);
        });
        task.setOnFailed(e -> { AlertUtil.showError("Error", "Booking failed."); bookBtn.setDisable(false); });
        new Thread(task).start();
    }

    @FXML private void onSearchDoctors() { loadDoctors(doctorSearchField.getText().trim()); }

    // ── Navigation ──
    @FXML private void onNavHome(javafx.event.ActionEvent e) { SceneNavigator.navigateTo("Dashboard.fxml", e); }
    @FXML private void onNavAppointments(javafx.event.ActionEvent e) { SceneNavigator.navigateTo("AppointmentPage.fxml", e); }
    @FXML private void onNavPatients(javafx.event.ActionEvent e) { SceneNavigator.navigateTo("PatientManagement.fxml", e); }
    @FXML private void onNavDoctors(javafx.event.ActionEvent e) { SceneNavigator.navigateTo("DoctorManagement.fxml", e); }
    @FXML private void onNavRecords(javafx.event.ActionEvent e) { SceneNavigator.navigateTo("HealthRecords.fxml", e); }
    @FXML private void onLogout(javafx.event.ActionEvent e) { SessionManager.getInstance().logout(); SceneNavigator.navigateTo("Login.fxml", e); }
}
