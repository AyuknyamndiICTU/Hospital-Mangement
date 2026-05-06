package com.healthassist.controller;

import com.healthassist.dao.AppointmentDAO;
import com.healthassist.dao.UserDAO;
import com.healthassist.model.Appointment;
import com.healthassist.model.User;
import com.healthassist.service.ReminderService;
import com.healthassist.util.DateUtil;
import com.healthassist.util.SceneNavigator;
import com.healthassist.util.SessionManager;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

public class DashboardController {

    @FXML private Label greetingLabel, dateLabel, clockLabel;
    @FXML private Label statPatients, statAppointments, statDoctors;
    @FXML private PieChart statusChart;
    @FXML private VBox recentAppointmentsBox;
    @FXML private Label avatarInitials, profileName, profileRole, profileEmail, profileId;
    @FXML private Label calMonthLabel;
    @FXML private GridPane calendarGrid;
    @FXML private Button navHome, navAppointments, navPatients, navDoctors, navRecords;

    private final AppointmentDAO appointmentDAO = new AppointmentDAO();
    private final UserDAO userDAO = new UserDAO();
    private ReminderService reminderService;
    private YearMonth currentCalMonth;

    @FXML
    public void initialize() {
        User user = SessionManager.getInstance().getCurrentUser();

        // Setup greeting
        String dayOfWeek = LocalDate.now().getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        String name = user != null ? user.getFullName() : "User";
        greetingLabel.setText("Good Day, " + name + "!");
        dateLabel.setText("Have a Nice " + dayOfWeek + "!  •  " + DateUtil.formatFullDate(LocalDate.now()));

        // Setup profile
        setupProfile(user);

        // Start live clock
        startLiveClock();

        // Setup calendar
        currentCalMonth = YearMonth.now();
        renderCalendar();

        // Load stats on background thread
        loadStats();

        // Apply role-based visibility
        applyRoleAccess(user);

        // Start reminder service daemon
        startReminderService();
    }

    private void setupProfile(User user) {
        if (user != null) {
            profileName.setText(user.getFullName());
            profileRole.setText(user.getRole().name());
            profileEmail.setText("✉  " + user.getEmail());
            profileId.setText("ID: " + user.getId());
            String initials = "";
            String[] parts = user.getFullName().split(" ");
            for (String p : parts) {
                if (!p.isEmpty()) initials += p.charAt(0);
            }
            avatarInitials.setText(initials.toUpperCase());
        }
    }

    private void startLiveClock() {
        Timeline clock = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            clockLabel.setText(DateUtil.formatClockTime(LocalDateTime.now()));
        }));
        clock.setCycleCount(Timeline.INDEFINITE);
        clock.play();
    }

    private void loadStats() {
        Task<Void> task = new Task<>() {
            int patients, appointments, doctors;
            int pending, confirmed, cancelled, completed;
            List<Appointment> recent;

            @Override
            protected Void call() {
                patients = userDAO.countByRole(User.Role.PATIENT);
                doctors = userDAO.countByRole(User.Role.DOCTOR);
                appointments = appointmentDAO.countToday();
                pending = appointmentDAO.countTodayByStatus(Appointment.Status.PENDING);
                confirmed = appointmentDAO.countTodayByStatus(Appointment.Status.CONFIRMED);
                cancelled = appointmentDAO.countTodayByStatus(Appointment.Status.CANCELLED);
                completed = appointmentDAO.countTodayByStatus(Appointment.Status.COMPLETED);

                User user = SessionManager.getInstance().getCurrentUser();
                if (user != null && user.getRole() == User.Role.DOCTOR) {
                    recent = appointmentDAO.findByDoctor(user.getId());
                } else if (user != null && user.getRole() == User.Role.PATIENT) {
                    recent = appointmentDAO.findByPatient(user.getId());
                } else {
                    recent = appointmentDAO.findAll();
                }
                if (recent.size() > 5) recent = recent.subList(0, 5);
                return null;
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    statPatients.setText(String.valueOf(patients));
                    statAppointments.setText(String.valueOf(appointments));
                    statDoctors.setText(String.valueOf(doctors));
                    
                    // Pie chart
                    statusChart.getData().clear();
                    if (pending + confirmed + cancelled + completed > 0) {
                        if (pending > 0) statusChart.getData().add(new PieChart.Data("Pending (" + pending + ")", pending));
                        if (confirmed > 0) statusChart.getData().add(new PieChart.Data("Confirmed (" + confirmed + ")", confirmed));
                        if (completed > 0) statusChart.getData().add(new PieChart.Data("Completed (" + completed + ")", completed));
                        if (cancelled > 0) statusChart.getData().add(new PieChart.Data("Cancelled (" + cancelled + ")", cancelled));
                    } else {
                        statusChart.getData().add(new PieChart.Data("No Data", 1));
                    }

                    // Recent appointments
                    recentAppointmentsBox.getChildren().clear();
                    if (recent != null) {
                        for (Appointment a : recent) {
                            recentAppointmentsBox.getChildren().add(createAppointmentItem(a));
                        }
                    }
                    if (recentAppointmentsBox.getChildren().isEmpty()) {
                        recentAppointmentsBox.getChildren().add(new Label("No recent appointments"));
                    }
                });
            }
        };
        new Thread(task).start();
    }

    private HBox createAppointmentItem(Appointment appt) {
        HBox box = new HBox(10);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("appointment-item");
        box.setStyle("-fx-background-color: #F8FAFC; -fx-background-radius: 10; -fx-padding: 12;");

        String statusColor = switch (appt.getStatus()) {
            case PENDING -> "#F59E0B";
            case CONFIRMED -> "#2D5BE3";
            case COMPLETED -> "#22C55E";
            case CANCELLED -> "#EF4444";
        };

        Label dot = new Label("●");
        dot.setStyle("-fx-text-fill: " + statusColor + "; -fx-font-size: 14;");

        VBox info = new VBox(2);
        HBox.setHgrow(info, Priority.ALWAYS);
        Label nameL = new Label(appt.getPatientName() != null ? appt.getPatientName() : "Patient #" + appt.getPatientId());
        nameL.setStyle("-fx-font-weight: bold; -fx-text-fill: #1E293B; -fx-font-size: 13;");
        Label timeL = new Label(DateUtil.formatDateTime(appt.getAppointmentDatetime()));
        timeL.setStyle("-fx-text-fill: #64748B; -fx-font-size: 11;");
        info.getChildren().addAll(nameL, timeL);

        Label status = new Label(appt.getStatus().name());
        status.setStyle("-fx-text-fill: " + statusColor + "; -fx-font-size: 11; -fx-font-weight: bold;");

        box.getChildren().addAll(dot, info, status);
        return box;
    }

    private void renderCalendar() {
        calendarGrid.getChildren().clear();
        calendarGrid.getColumnConstraints().clear();
        calMonthLabel.setText(currentCalMonth.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + currentCalMonth.getYear());

        String[] days = {"Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"};
        for (int i = 0; i < 7; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setHalignment(HPos.CENTER);
            cc.setPrefWidth(36);
            calendarGrid.getColumnConstraints().add(cc);
            Label dayLabel = new Label(days[i]);
            dayLabel.setStyle("-fx-text-fill: #64748B; -fx-font-size: 11; -fx-font-weight: bold;");
            calendarGrid.add(dayLabel, i, 0);
        }

        LocalDate first = currentCalMonth.atDay(1);
        int startCol = first.getDayOfWeek().getValue() - 1;
        int daysInMonth = currentCalMonth.lengthOfMonth();
        int row = 1;
        int col = startCol;

        for (int day = 1; day <= daysInMonth; day++) {
            Label dayNum = new Label(String.valueOf(day));
            dayNum.setPrefSize(32, 32);
            dayNum.setAlignment(Pos.CENTER);
            dayNum.setStyle("-fx-font-size: 12; -fx-text-fill: #1E293B;");

            if (currentCalMonth.equals(YearMonth.now()) && day == LocalDate.now().getDayOfMonth()) {
                dayNum.setStyle("-fx-background-color: #2D5BE3; -fx-text-fill: white; -fx-background-radius: 16; -fx-font-size: 12; -fx-font-weight: bold;");
            }

            calendarGrid.add(dayNum, col, row);
            col++;
            if (col > 6) { col = 0; row++; }
        }
    }

    @FXML private void onPrevMonth() { currentCalMonth = currentCalMonth.minusMonths(1); renderCalendar(); }
    @FXML private void onNextMonth() { currentCalMonth = currentCalMonth.plusMonths(1); renderCalendar(); }

    private void applyRoleAccess(User user) {
        if (user == null) return;
        switch (user.getRole()) {
            case PATIENT:
                navDoctors.setVisible(false); navDoctors.setManaged(false);
                break;
            case DOCTOR:
                break;
            case ADMIN:
                break;
        }
    }

    private void startReminderService() {
        reminderService = new ReminderService();
        Thread thread = new Thread(reminderService);
        thread.setDaemon(true);
        thread.setName("ReminderService");
        thread.start();
    }

    // ── Navigation ──
    @FXML private void onNavHome(javafx.event.ActionEvent e) { SceneNavigator.navigateTo("Dashboard.fxml", e); }
    @FXML private void onNavAppointments(javafx.event.ActionEvent e) { SceneNavigator.navigateTo("AppointmentPage.fxml", e); }
    @FXML private void onNavPatients(javafx.event.ActionEvent e) { SceneNavigator.navigateTo("PatientManagement.fxml", e); }
    @FXML private void onNavDoctors(javafx.event.ActionEvent e) { SceneNavigator.navigateTo("DoctorManagement.fxml", e); }
    @FXML private void onNavRecords(javafx.event.ActionEvent e) { SceneNavigator.navigateTo("HealthRecords.fxml", e); }

    @FXML
    private void onLogout(javafx.event.ActionEvent e) {
        if (reminderService != null) reminderService.stop();
        SessionManager.getInstance().logout();
        SceneNavigator.navigateTo("Login.fxml", e);
    }
}
