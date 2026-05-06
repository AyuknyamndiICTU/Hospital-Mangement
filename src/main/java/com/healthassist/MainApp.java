package com.healthassist;

import com.healthassist.config.AdminSeeder;
import com.healthassist.config.DatabaseConfig;
import com.healthassist.config.DatabaseInitializer;
import com.healthassist.config.MockDataSeeder;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Main entry point for the Health Assistance System application.
 * Initializes database, seeds admin user, and loads the Login screen.
 */
public class MainApp extends Application {

    private static Stage primaryStage;
    private static final Properties appProperties = new Properties();

    @Override
    public void start(Stage stage) {
        primaryStage = stage;

        try {
            // Load application properties
            loadProperties();

            // Initialize database schema
            System.out.println("Initializing database...");
            DatabaseInitializer.initialize();
            System.out.println("Database initialized successfully.");

            // Seed default admin if not exists
            AdminSeeder.seed();
            System.out.println("Admin seeder complete.");

            // Seed realistic mock data
            MockDataSeeder.seed();

            // Load Login screen
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/healthassist/fxml/Login.fxml")
            );
            Parent root = loader.load();

            Scene scene = new Scene(root, 1100, 750);
            scene.getStylesheets().addAll(
                getClass().getResource("/com/healthassist/styles/global.css").toExternalForm(),
                getClass().getResource("/com/healthassist/styles/login.css").toExternalForm()
            );

            stage.setTitle(appProperties.getProperty("app.name", "Health Assistance System"));
            stage.setScene(scene);
            stage.setMinWidth(1024);
            stage.setMinHeight(700);
            stage.setResizable(true);
            stage.show();

        } catch (Exception e) {
            System.err.println("Failed to start application: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void stop() {
        // Shutdown connection pool on exit
        DatabaseConfig.getInstance().shutdown();
        System.out.println("Application shut down gracefully.");
    }

    /**
     * Load config.properties from classpath.
     */
    private void loadProperties() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("config.properties")) {
            if (input != null) {
                appProperties.load(input);
            } else {
                System.err.println("config.properties not found, using defaults.");
            }
        } catch (IOException e) {
            System.err.println("Error loading config.properties: " + e.getMessage());
        }
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    public static Properties getAppProperties() {
        return appProperties;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
