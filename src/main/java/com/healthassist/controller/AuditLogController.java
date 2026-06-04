package com.healthassist.controller;

import java.time.format.DateTimeFormatter;
import java.util.List;

import com.healthassist.dao.AuditLogDAO;
import com.healthassist.model.AuditLogEntry;
import com.healthassist.model.User;
import com.healthassist.util.SceneNavigator;
import com.healthassist.util.SessionManager;

import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;

public class AuditLogController {

    @FXML private Button refreshBtn;
    @FXML private ListView<AuditLogEntry> auditListView;
    @FXML private Label emptyLabel;

    private final AuditLogDAO auditLogDAO = new AuditLogDAO();
    private final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int DEFAULT_LIMIT = 200;

    @FXML
    public void initialize() {
        auditListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(AuditLogEntry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                String when = item.getCreatedAt() != null ? dtf.format(item.getCreatedAt().toLocalDateTime()) : "-";
                String actor = item.getActorName() != null
                        ? item.getActorName()
                        : (item.getActorUserId() != null ? "User#" + item.getActorUserId() : "system");
                String target = item.getTargetType() + (item.getTargetId() != null ? "#" + item.getTargetId() : "");
                String details = item.getDetails() != null && !item.getDetails().isBlank()
                        ? " — " + item.getDetails()
                        : "";
                setText(when + " • " + actor + " • " + item.getEventType() + " • " + target + details);
            }
        });

        loadEntries();
    }

    @FXML
    private void onRefresh() {
        loadEntries();
    }

    @FXML
    private void onBack(javafx.event.ActionEvent e) {
        // Navigate back to dashboard
        SceneNavigator.navigateTo("Dashboard.fxml", e);
    }

    private void loadEntries() {
        User actor = SessionManager.getInstance().getCurrentUser();
        if (actor == null || actor.getRole() != User.Role.ADMIN) {
            emptyLabel.setText("Not authorized.");
            auditListView.setItems(FXCollections.observableArrayList());
            return;
        }

        emptyLabel.setText("Loading...");
        Task<List<AuditLogEntry>> task = new Task<>() {
            @Override
            protected List<AuditLogEntry> call() {
                return auditLogDAO.findRecent(DEFAULT_LIMIT, actor);
            }
        };
        task.setOnSucceeded(e -> {
            List<AuditLogEntry> rows = task.getValue();
            auditListView.setItems(FXCollections.observableArrayList(rows));
            emptyLabel.setText(rows.isEmpty() ? "No audit entries." : "");
        });
        task.setOnFailed(e -> emptyLabel.setText("Could not load audit log."));
        new Thread(task).start();
    }
}
