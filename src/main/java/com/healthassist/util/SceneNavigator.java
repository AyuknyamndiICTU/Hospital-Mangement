package com.healthassist.util;

import com.healthassist.MainApp;
import javafx.animation.FadeTransition;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;

/**
 * Utility for navigating between FXML screens with fade transitions.
 */
public class SceneNavigator {

    /**
     * Navigate to a new FXML screen with a 300ms fade transition.
     */
    public static void navigateTo(String fxmlPath, ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(
                SceneNavigator.class.getResource("/com/healthassist/fxml/" + fxmlPath)
            );
            Parent root = loader.load();

            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            Scene currentScene = stage.getScene();

            // Fade out current content
            FadeTransition fadeOut = new FadeTransition(Duration.millis(150), currentScene.getRoot());
            fadeOut.setFromValue(1.0);
            fadeOut.setToValue(0.0);
            fadeOut.setOnFinished(e -> {
                Scene newScene = new Scene(root, currentScene.getWidth(), currentScene.getHeight());
                newScene.getStylesheets().addAll(
                    SceneNavigator.class.getResource("/com/healthassist/styles/global.css").toExternalForm()
                );
                // Add screen-specific CSS
                addScreenCss(fxmlPath, newScene);
                stage.setScene(newScene);
                stage.setMinWidth(1024);
                stage.setMinHeight(700);

                // Fade in new content
                FadeTransition fadeIn = new FadeTransition(Duration.millis(150), root);
                fadeIn.setFromValue(0.0);
                fadeIn.setToValue(1.0);
                fadeIn.play();
            });
            fadeOut.play();

        } catch (IOException e) {
            System.err.println("Navigation error: " + e.getMessage());
            e.printStackTrace();
            AlertUtil.showError("Navigation Error", "Could not load page: " + fxmlPath);
        }
    }

    /**
     * Navigate from any node (not just ActionEvent source).
     */
    public static void navigateTo(String fxmlPath, Node sourceNode) {
        try {
            FXMLLoader loader = new FXMLLoader(
                SceneNavigator.class.getResource("/com/healthassist/fxml/" + fxmlPath)
            );
            Parent root = loader.load();

            Stage stage = (Stage) sourceNode.getScene().getWindow();
            Scene currentScene = stage.getScene();

            FadeTransition fadeOut = new FadeTransition(Duration.millis(150), currentScene.getRoot());
            fadeOut.setFromValue(1.0);
            fadeOut.setToValue(0.0);
            fadeOut.setOnFinished(e -> {
                Scene newScene = new Scene(root, currentScene.getWidth(), currentScene.getHeight());
                newScene.getStylesheets().add(
                    SceneNavigator.class.getResource("/com/healthassist/styles/global.css").toExternalForm()
                );
                addScreenCss(fxmlPath, newScene);
                stage.setScene(newScene);

                FadeTransition fadeIn = new FadeTransition(Duration.millis(150), root);
                fadeIn.setFromValue(0.0);
                fadeIn.setToValue(1.0);
                fadeIn.play();
            });
            fadeOut.play();

        } catch (IOException e) {
            System.err.println("Navigation error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Add screen-specific CSS based on FXML filename.
     */
    private static void addScreenCss(String fxmlPath, Scene scene) {
        if (fxmlPath.contains("Login")) {
            var loginCss = SceneNavigator.class.getResource("/com/healthassist/styles/login.css");
            if (loginCss != null) scene.getStylesheets().add(loginCss.toExternalForm());
        } else {
            // All non-Login screens use dashboard.css styles
            var dashCss = SceneNavigator.class.getResource("/com/healthassist/styles/dashboard.css");
            if (dashCss != null) scene.getStylesheets().add(dashCss.toExternalForm());
        }
    }
}
