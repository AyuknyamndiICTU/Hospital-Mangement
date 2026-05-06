package com.healthassist.controller;

import com.healthassist.model.User;
import com.healthassist.service.AuthService;
import com.healthassist.util.AlertUtil;
import com.healthassist.util.DateUtil;
import com.healthassist.util.SceneNavigator;
import com.healthassist.util.SessionManager;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;

/**
 * Controller for the Login screen.
 * Validates credentials via AuthService and navigates to Dashboard.
 */
public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField passwordVisible;
    @FXML private Button showPasswordBtn;
    @FXML private Button loginBtn;
    @FXML private CheckBox rememberMeCheck;
    @FXML private Label errorLabel;

    private final AuthService authService = new AuthService();
    private boolean passwordShown = false;

    @FXML
    public void initialize() {
        errorLabel.setText("");
        // Bind visible text field to password field
        passwordVisible.textProperty().bindBidirectional(passwordField.textProperty());
    }

    /**
     * Handle Sign In button click.
     */
    @FXML
    private void onLogin(javafx.event.ActionEvent event) {
        String email = usernameField.getText().trim();
        String password = passwordShown ? passwordVisible.getText() : passwordField.getText();

        // ── Input Validation ──
        if (email.isEmpty()) {
            showError("Please enter your email address.");
            return;
        }
        if (!DateUtil.isValidEmail(email)) {
            showError("Please enter a valid email address.");
            return;
        }
        if (password == null || password.isEmpty()) {
            showError("Please enter your password.");
            return;
        }

        // ── Authenticate on background thread ──
        loginBtn.setDisable(true);
        loginBtn.setText("Signing in...");
        errorLabel.setText("");

        Task<User> loginTask = new Task<>() {
            @Override
            protected User call() {
                return authService.login(email, password);
            }
        };

        loginTask.setOnSucceeded(e -> {
            User user = loginTask.getValue();
            if (user != null) {
                // Store user in session
                SessionManager.getInstance().setCurrentUser(user);
                System.out.println("Login successful: " + user);

                // Navigate to Dashboard
                SceneNavigator.navigateTo("Dashboard.fxml", event);
            } else {
                showError("Invalid email or password. Please try again.");
                loginBtn.setDisable(false);
                loginBtn.setText("Sign In");
            }
        });

        loginTask.setOnFailed(e -> {
            showError("Login failed. Please check your connection.");
            loginBtn.setDisable(false);
            loginBtn.setText("Sign In");
        });

        new Thread(loginTask).start();
    }

    /**
     * Toggle password visibility.
     */
    @FXML
    private void onTogglePassword() {
        passwordShown = !passwordShown;
        if (passwordShown) {
            passwordVisible.setVisible(true);
            passwordVisible.setManaged(true);
            passwordField.setVisible(false);
            passwordField.setManaged(false);
            showPasswordBtn.setText("HIDE");
        } else {
            passwordField.setVisible(true);
            passwordField.setManaged(true);
            passwordVisible.setVisible(false);
            passwordVisible.setManaged(false);
            showPasswordBtn.setText("SHOW");
        }
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setStyle("-fx-text-fill: #EF4444;");
    }
}
