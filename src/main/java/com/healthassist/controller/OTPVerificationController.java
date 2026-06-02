package com.healthassist.controller;

import com.healthassist.service.OTPService;
import com.healthassist.util.AlertUtil;
import com.healthassist.util.PendingSignupSession;
import com.healthassist.util.SceneNavigator;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

/**
 * OTP verification screen. Verifies the OTP, activates the account,
 * then navigates back to Login (optionally auto-logs in).
 */
public class OTPVerificationController {

    @FXML private Label destinationLabel;
    @FXML private Label errorLabel;
    @FXML private TextField otpField;
    @FXML private Button verifyBtn;

    private final OTPService otpService = new OTPService();

    @FXML
    public void initialize() {
        errorLabel.setText("");
        var pending = PendingSignupSession.getInstance();
        if (pending.hasPendingUser()) {
            String destination = pending.getPendingDestination();
            if (destinationLabel != null) {
                destinationLabel.setText("We sent a code to: " + (destination == null ? "your email/phone" : destination));
            }
        }
    }

    @FXML
    private void onVerify(javafx.event.ActionEvent event) {
        errorLabel.setText("");

        var pending = PendingSignupSession.getInstance();
        if (!pending.hasPendingUser()) {
            AlertUtil.showError("OTP Error", "No signup session found. Please sign up again.");
            SceneNavigator.navigateTo("SignUp.fxml", event);
            return;
        }

        String otp = otpField.getText() != null ? otpField.getText().trim() : "";
        if (otp.isEmpty()) {
            errorLabel.setText("Enter the 6-digit OTP.");
            return;
        }

        verifyBtn.setDisable(true);
        verifyBtn.setText("Verifying...");

        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() {
                return otpService.verifyOtpAndActivate(pending.getPendingUserId(), otp);
            }
        };

        task.setOnSucceeded(e -> {
            boolean ok = task.getValue() != null && task.getValue();
            if (!ok) {
                errorLabel.setText("Invalid or expired OTP. Please try again.");
                verifyBtn.setDisable(false);
                verifyBtn.setText("Verify");
                return;
            }

            pending.clear();

            // Navigate to login. User will log in normally.
            SceneNavigator.navigateTo("Login.fxml", event);
            verifyBtn.setDisable(false);
            verifyBtn.setText("Verify");
        });

        task.setOnFailed(e -> {
            errorLabel.setText("Verification failed. Please check your connection.");
            verifyBtn.setDisable(false);
            verifyBtn.setText("Verify");
        });

        new Thread(task).start();
    }

    @FXML
    private void onBackToSignUp(javafx.event.ActionEvent event) {
        PendingSignupSession.getInstance().clear();
        SceneNavigator.navigateTo("SignUp.fxml", event);
    }
}
