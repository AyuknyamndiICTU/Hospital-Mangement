package com.healthassist.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.healthassist.dao.AppointmentDAO;
import com.healthassist.dao.UserDAO;
import com.healthassist.model.Appointment;
import com.healthassist.model.User;
import com.healthassist.service.AppointmentService;
import com.healthassist.service.ReminderService;
import com.healthassist.util.AlertUtil;
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
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

public class DashboardController {

    @FXML private Label greetingLabel, dateLabel, clockLabel;
    @FXML private Label statPatients, statAppointments, statDoctors;
    @FXML private PieChart statusChart;
    @FXML private VBox recentAppointmentsBox;
    @FXML private VBox healthMonitoringBox;
    @FXML private VBox statusLegendBox;
    @FXML private Label avatarInitials, profileName, profileRole, profileEmail, profileId;
    @FXML private Label calMonthLabel;
    @FXML private GridPane calendarGrid;
    @FXML private Button navHome, navAppointments, navPatients, navDoctors, navRecords, navAuditLog;

    private final AppointmentDAO appointmentDAO = new AppointmentDAO();
    private final AppointmentService appointmentService = new AppointmentService();
    private final UserDAO userDAO = new UserDAO();
    private final com.healthassist.dao.HealthRecordDAO healthRecordDAO = new com.healthassist.dao.HealthRecordDAO();
    private ReminderService reminderService;
    private Timeline realtimeDashboardTimeline;
    private volatile boolean refreshInProgress = false;
    private static final int DASHBOARD_REFRESH_SECONDS = 30;
    private static final int HEALTH_MONITORING_ITEMS = 5;

    // Debug: helps verify the pie chart is using the expected live counts
    private volatile boolean debugPrintedOnce = false;

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

        // Load stats once (initial)
        loadStats();

        // Start real-time dashboard refresh (multithreaded)
        startRealtimeDashboardRefresh();

        // Apply role-based visibility
        applyRoleAccess(user);

        // Start reminder service daemon
        startReminderService();

        // Doctor login popup for first pending appointment
        if (user != null && user.getRole() == User.Role.DOCTOR) {
            List<Appointment> doctorAppointments = appointmentDAO.findByDoctor(user.getId());
            Appointment pending = doctorAppointments.stream()
                    .filter(a -> a.getStatus() == Appointment.Status.PENDING)
                    .sorted(Comparator.comparing(Appointment::getAppointmentDatetime))
                    .findFirst()
                    .orElse(null);

            if (pending != null) {
                Platform.runLater(() -> showDoctorAppointmentDecisionDialog(pending));
            }
        }
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
                // Appointment Status Overview should reflect appointment statuses across the system (not only today).
                pending = appointmentDAO.countByStatus(Appointment.Status.PENDING);
                confirmed = appointmentDAO.countByStatus(Appointment.Status.CONFIRMED);
                cancelled = appointmentDAO.countByStatus(Appointment.Status.CANCELLED);
                completed = appointmentDAO.countByStatus(Appointment.Status.COMPLETED);

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
                        PieChart.Data pData = new PieChart.Data("Pending (" + pending + ")", pending);
                        if (pending > 0) {
                            statusChart.getData().add(pData);
                            applyPieColor(pData, "#F59E0B");
                        }
                        
                        PieChart.Data cfData = new PieChart.Data("Confirmed (" + confirmed + ")", confirmed);
                        if (confirmed > 0) {
                            statusChart.getData().add(cfData);
                            applyPieColor(cfData, "#2D5BE3");
                        }
                        
                        PieChart.Data cpData = new PieChart.Data("Completed (" + completed + ")", completed);
                        if (completed > 0) {
                            statusChart.getData().add(cpData);
                            applyPieColor(cpData, "#22C55E");
                        }
                        
                        PieChart.Data cxData = new PieChart.Data("Cancelled (" + cancelled + ")", cancelled);
                        if (cancelled > 0) {
                            statusChart.getData().add(cxData);
                            applyPieColor(cxData, "#EF4444");
                        }

                        // Ensure legend text is visible/consistent after chart data update
                        stylePieLegendText();
                    } else {
                        PieChart.Data noData = new PieChart.Data("No Data", 1);
                        statusChart.getData().add(noData);
                        applyPieColor(noData, "#E2E8F0");

                        // Ensure legend text is visible/consistent after chart data update
                        stylePieLegendText();
                    }

                    // Custom legend (readable status names)
                    renderStatusLegend(pending, confirmed, completed, cancelled);

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

    private void startRealtimeDashboardRefresh() {
        realtimeDashboardTimeline = new Timeline(new KeyFrame(Duration.seconds(DASHBOARD_REFRESH_SECONDS), e -> {
            if (refreshInProgress) return;
            refreshInProgress = true;

            Task<Void> task = new Task<>() {
                int patients, appointments, doctors;
                int pending, confirmed, cancelled, completed;
                List<Appointment> recent;
                List<com.healthassist.model.HealthRecord> healthMonitoring;

                @Override
                protected Void call() {
                    patients = userDAO.countByRole(User.Role.PATIENT);
                    doctors = userDAO.countByRole(User.Role.DOCTOR);
                    appointments = appointmentDAO.countToday();
                    // Real-time pie chart uses live counts across all appointment dates.
                    pending = appointmentDAO.countByStatus(Appointment.Status.PENDING);
                    confirmed = appointmentDAO.countByStatus(Appointment.Status.CONFIRMED);
                    cancelled = appointmentDAO.countByStatus(Appointment.Status.CANCELLED);
                    completed = appointmentDAO.countByStatus(Appointment.Status.COMPLETED);

                    com.healthassist.model.User user = SessionManager.getInstance().getCurrentUser();
                    if (user != null && user.getRole() == com.healthassist.model.User.Role.DOCTOR) {
                        recent = appointmentDAO.findByDoctor(user.getId());
                    } else if (user != null && user.getRole() == com.healthassist.model.User.Role.PATIENT) {
                        recent = appointmentDAO.findByPatient(user.getId());
                    } else {
                        recent = appointmentDAO.findAll();
                    }
                    if (recent != null && recent.size() > 5) recent = recent.subList(0, 5);

                    // Health monitoring (Option A): latest health activity
                    healthMonitoring = healthRecordDAO.findAll();
                    if (healthMonitoring != null && healthMonitoring.size() > HEALTH_MONITORING_ITEMS) {
                        healthMonitoring = healthMonitoring.subList(0, HEALTH_MONITORING_ITEMS);
                    }

                    return null;
                }

                @Override
                protected void succeeded() {
                    Platform.runLater(() -> {
                        try {
                            statPatients.setText(String.valueOf(patients));
                            statAppointments.setText(String.valueOf(appointments));
                            statDoctors.setText(String.valueOf(doctors));

                            // Debug once: verify counts used by the pie chart
                            if (!debugPrintedOnce) {
                                System.out.println(
                                        "[DashboardController] PieChart counts -> " +
                                                "pending=" + pending +
                                                ", confirmed=" + confirmed +
                                                ", completed=" + completed +
                                                ", cancelled=" + cancelled
                                );
                                debugPrintedOnce = true;
                            }

                            // Pie chart (live appointment status)
                            statusChart.getData().clear();
                            int total = pending + confirmed + cancelled + completed;
                            if (total > 0) {
                                PieChart.Data pData = new PieChart.Data("Pending (" + pending + ")", pending);
                                if (pending > 0) {
                                    statusChart.getData().add(pData);
                                    applyPieColor(pData, "#F59E0B");
                                }

                                PieChart.Data cfData = new PieChart.Data("Confirmed (" + confirmed + ")", confirmed);
                                if (confirmed > 0) {
                                    statusChart.getData().add(cfData);
                                    applyPieColor(cfData, "#2D5BE3");
                                }

                                PieChart.Data cpData = new PieChart.Data("Completed (" + completed + ")", completed);
                                if (completed > 0) {
                                    statusChart.getData().add(cpData);
                                    applyPieColor(cpData, "#22C55E");
                                }

                                PieChart.Data cxData = new PieChart.Data("Cancelled (" + cancelled + ")", cancelled);
                                if (cancelled > 0) {
                                    statusChart.getData().add(cxData);
                                    applyPieColor(cxData, "#EF4444");
                                }

                                // Ensure legend text is visible/consistent after chart data update (kept as fallback)
                                stylePieLegendText();
                            } else {
                                PieChart.Data noData = new PieChart.Data("No Data", 1);
                                statusChart.getData().add(noData);
                                applyPieColor(noData, "#E2E8F0");

                                // Ensure legend text is visible/consistent after chart data update (kept as fallback)
                                stylePieLegendText();
                            }

                            // Custom legend (readable status names)
                            renderStatusLegend(pending, confirmed, completed, cancelled);

                            // Recent appointments list
                            recentAppointmentsBox.getChildren().clear();
                            if (recent != null) {
                                for (Appointment a : recent) {
                                    recentAppointmentsBox.getChildren().add(createAppointmentItem(a));
                                }
                            }
                            if (recentAppointmentsBox.getChildren().isEmpty()) {
                                recentAppointmentsBox.getChildren().add(new Label("No recent appointments"));
                            }

                            // Health monitoring list (latest health activity)
                            healthMonitoringBox.getChildren().clear();
                            if (healthMonitoring != null && !healthMonitoring.isEmpty()) {
                                for (com.healthassist.model.HealthRecord hr : healthMonitoring) {
                                    Label line = new Label("🩺 " +
                                            DateUtil.formatDate(hr.getVisitDate()) +
                                            " — " +
                                            (hr.getPatientName() != null ? hr.getPatientName() : ("Patient #" + hr.getPatientId())) +
                                            " • " +
                                            (hr.getDiagnosis() != null ? hr.getDiagnosis() : "N/A"));
                                    line.setWrapText(true);
                                    line.setStyle("-fx-text-fill: #1E293B; -fx-font-size: 12;");
                                    healthMonitoringBox.getChildren().add(line);
                                }
                            } else {
                                healthMonitoringBox.getChildren().add(new Label("No health monitoring updates"));
                            }
                        } finally {
                            refreshInProgress = false;
                        }
                    });
                }

                @Override
                protected void failed() {
                    Platform.runLater(() -> {
                        refreshInProgress = false;
                        // Keep scheduler alive; log for debugging
                        System.err.println("Realtime dashboard refresh failed.");
                    });
                }
            };

            new Thread(task).start();
        }));
        realtimeDashboardTimeline.setCycleCount(Timeline.INDEFINITE);
        realtimeDashboardTimeline.play();
    }

    private HBox createAppointmentItem(Appointment appt) {
        HBox box = new HBox(10);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("appointment-item");

        box.setOnMouseClicked(e -> {
            User cur = SessionManager.getInstance().getCurrentUser();
            if (cur != null && cur.getRole() == User.Role.DOCTOR) {
                Platform.runLater(() -> showDoctorAppointmentDecisionDialog(appt));
            } else {
                Platform.runLater(() -> SceneNavigator.navigateTo("AppointmentPage.fxml", box));
            }
        });

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

    private void showDoctorAppointmentDecisionDialog(Appointment appt) {
        if (appt == null) return;

        User actor = SessionManager.getInstance().getCurrentUser();
        if (actor == null || actor.getRole() != User.Role.DOCTOR) return;

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Appointment Action");

        ButtonType confirmType = new ButtonType("Confirm", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Cancel", ButtonBar.ButtonData.NO);
        ButtonType reschedType = new ButtonType("Reschedule", ButtonBar.ButtonData.APPLY);
        ButtonType closeType = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);

        dialog.getDialogPane().getButtonTypes().addAll(confirmType, cancelType, reschedType, closeType);

        Label summary = new Label(
                "Patient: " + (appt.getPatientName() != null ? appt.getPatientName() : ("#" + appt.getPatientId()))
                        + "\nWhen: " + (appt.getAppointmentDatetime() != null ? fmt.format(appt.getAppointmentDatetime()) : "-")
                        + "\nStatus: " + appt.getStatus()
        );
        summary.setWrapText(true);

        TextArea reasonArea = new TextArea();
        reasonArea.setPromptText("Reason (optional)");
        reasonArea.setPrefRowCount(2);

        TextField datetimeField = new TextField();
        datetimeField.setPromptText("New datetime (yyyy-MM-dd HH:mm)");
        if (appt.getAppointmentDatetime() != null) {
            datetimeField.setText(fmt.format(appt.getAppointmentDatetime()));
        }

        VBox content = new VBox(10,
                summary,
                new Label("Reason:"),
                reasonArea,
                new Label("Reschedule datetime (only used if clicking Reschedule):"),
                datetimeField
        );
        dialog.getDialogPane().setContent(content);

        if (appt.getStatus() != Appointment.Status.PENDING) {
            dialog.getDialogPane().lookupButton(confirmType).setDisable(true);
        }

        dialog.setResultConverter(bt -> bt);
        Optional<ButtonType> result = dialog.showAndWait();

        String reason = reasonArea.getText() != null ? reasonArea.getText().trim() : null;

        if (result.isEmpty()) return;
        ButtonType pressed = result.get();

        try {
            if (pressed == confirmType) {
                boolean ok = appointmentService.confirmAppointment(actor, appt.getId(), reason);
                if (!ok) AlertUtil.showError("Error", "Could not confirm appointment.");
            } else if (pressed == cancelType) {
                boolean ok = appointmentService.cancelAppointment(actor, appt.getId(), reason);
                if (!ok) AlertUtil.showError("Error", "Could not cancel appointment.");
            } else if (pressed == reschedType) {
                String raw = datetimeField.getText() != null ? datetimeField.getText().trim() : "";
                if (raw.isEmpty()) {
                    AlertUtil.showError("Validation", "Enter a new datetime.");
                    return;
                }
                LocalDateTime newDt = LocalDateTime.parse(raw, fmt);
                boolean ok = appointmentService.rescheduleAppointment(actor, appt.getId(), newDt, reason);
                if (!ok) AlertUtil.showError("Error", "Could not reschedule appointment.");
            } else {
                // closeType or unknown -> do nothing
            }
        } catch (Exception ex) {
            AlertUtil.showError("Error", ex.getMessage() != null ? ex.getMessage() : "Operation failed.");
        }

        // Refresh dialog-triggered item display by reloading stats
        loadStats();
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

        boolean isAdmin = user.getRole() == User.Role.ADMIN;
        navAuditLog.setVisible(isAdmin);
        navAuditLog.setManaged(isAdmin);

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
        reminderService = new ReminderService(new com.healthassist.service.notifier.JavaFxAppointmentReminderNotifier());
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
    private void onNavAuditLog(javafx.event.ActionEvent e) {
        SceneNavigator.navigateTo("AuditLog.fxml", e);
    }

    @FXML
    private void onLogout(javafx.event.ActionEvent e) {
        // Stop background reminder polling
        if (reminderService != null) reminderService.stop();

        // Stop real-time dashboard refresh
        if (realtimeDashboardTimeline != null) {
            realtimeDashboardTimeline.stop();
        }
        refreshInProgress = false;

        SessionManager.getInstance().logout();
        SceneNavigator.navigateTo("Login.fxml", e);
    }

    private void stylePieLegendText() {
        // PieChart legend nodes are created by the JavaFX skin; CSS selectors sometimes miss
        // the internal Label/Text nodes. This makes the legend names/counts always visible.
        for (javafx.scene.Node legendItem : statusChart.lookupAll(".chart-legend-item")) {
            // Legend label is usually a Label inside the legend item
            javafx.scene.Node labelNode = legendItem.lookup(".label");
            if (labelNode != null) {
                labelNode.setStyle("-fx-text-fill: #1E293B; -fx-font-size: 12px; -fx-font-weight: bold;");
            }

            javafx.scene.Node textNode = legendItem.lookup(".text");
            if (textNode != null) {
                textNode.setStyle("-fx-fill: #1E293B; -fx-font-size: 12px; -fx-font-weight: bold;");
            }
        }
    }

    private void renderStatusLegend(int pending, int confirmed, int completed, int cancelled) {
        if (statusLegendBox == null) return;

        statusLegendBox.getChildren().clear();

        // Only show rows for statuses that actually exist (count > 0), to keep it clean.
        // But if all counts are 0, show a single "No Data".
        int total = pending + confirmed + cancelled + completed;
        if (total <= 0) {
            Label row = new Label("No Appointment Status Data");
            row.setStyle("-fx-text-fill: #64748B; -fx-font-size: 12px; -fx-font-weight: bold;");
            statusLegendBox.getChildren().add(row);
            return;
        }

        addLegendRow("Pending", pending, "#F59E0B");
        addLegendRow("Confirmed", confirmed, "#2D5BE3");
        addLegendRow("Completed", completed, "#22C55E");
        addLegendRow("Cancelled", cancelled, "#EF4444");
    }

    private void addLegendRow(String label, int count, String color) {
        if (count <= 0) return;
        if (statusLegendBox == null) return;

        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);

        Label dot = new Label("●");
        dot.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 14;");

        Label text = new Label(label + " (" + count + ")");
        text.setStyle("-fx-text-fill: #1E293B; -fx-font-size: 12px; -fx-font-weight: bold;");

        row.getChildren().addAll(dot, text);
        statusLegendBox.getChildren().add(row);
    }

    private void applyPieColor(PieChart.Data data, String color) {
        javafx.scene.Node node = data.getNode();
        if (node != null) {
            node.setStyle("-fx-pie-color: " + color + ";");
        } else {
            data.nodeProperty().addListener((obs, oldNode, newNode) -> {
                if (newNode != null) {
                    newNode.setStyle("-fx-pie-color: " + color + ";");
                }
            });
        }
    }
}
