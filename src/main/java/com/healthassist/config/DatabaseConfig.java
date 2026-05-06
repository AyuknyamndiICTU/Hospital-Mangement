package com.healthassist.config;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.Properties;

/**
 * Simple JDBC connection pool for MySQL.
 * Maintains a synchronized pool of Connection objects (min 3, max 10).
 */
public class DatabaseConfig {

    private static DatabaseConfig instance;

    private final String url;
    private final String user;
    private final String password;
    private final int minPool;
    private final int maxPool;

    private final LinkedList<Connection> availableConnections = new LinkedList<>();
    private int totalConnections = 0;

    private DatabaseConfig() {
        Properties props = loadProperties();
        this.url = props.getProperty("db.url",
                "jdbc:mysql://localhost:3307/health_assist?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
        this.user = props.getProperty("db.user", "root");
        this.password = props.getProperty("db.password", "admin237");
        this.minPool = Integer.parseInt(props.getProperty("db.pool.min", "3"));
        this.maxPool = Integer.parseInt(props.getProperty("db.pool.max", "10"));

        initializePool();
    }

    /**
     * Get the singleton instance.
     */
    public static synchronized DatabaseConfig getInstance() {
        if (instance == null) {
            instance = new DatabaseConfig();
        }
        return instance;
    }

    /**
     * Initialize minimum number of connections.
     */
    private void initializePool() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            for (int i = 0; i < minPool; i++) {
                availableConnections.add(createConnection());
                totalConnections++;
            }
            System.out.println("Connection pool initialized with " + minPool + " connections.");
        } catch (ClassNotFoundException e) {
            System.err.println("MySQL JDBC Driver not found: " + e.getMessage());
        } catch (SQLException e) {
            System.err.println("Failed to initialize connection pool: " + e.getMessage());
        }
    }

    /**
     * Get a connection from the pool. Creates a new one if pool is empty and under
     * max.
     */
    public synchronized Connection getConnection() throws SQLException {
        if (!availableConnections.isEmpty()) {
            Connection conn = availableConnections.removeFirst();
            // Validate connection before returning
            if (conn == null || conn.isClosed()) {
                totalConnections--;
                return getConnection(); // Recursively get another
            }
            return conn;
        }

        if (totalConnections < maxPool) {
            Connection conn = createConnection();
            totalConnections++;
            return conn;
        }

        // Wait for a connection to become available
        try {
            wait(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (!availableConnections.isEmpty()) {
            return availableConnections.removeFirst();
        }

        throw new SQLException("Connection pool exhausted — max " + maxPool + " connections reached.");
    }

    /**
     * Return a connection to the pool.
     */
    public synchronized void releaseConnection(Connection conn) {
        if (conn != null) {
            try {
                if (!conn.isClosed()) {
                    availableConnections.addLast(conn);
                    notifyAll();
                } else {
                    totalConnections--;
                }
            } catch (SQLException e) {
                totalConnections--;
            }
        }
    }

    /**
     * Shutdown the pool — close all connections.
     */
    public synchronized void shutdown() {
        for (Connection conn : availableConnections) {
            try {
                if (conn != null && !conn.isClosed()) {
                    conn.close();
                }
            } catch (SQLException e) {
                // Ignore on shutdown
            }
        }
        availableConnections.clear();
        totalConnections = 0;
        System.out.println("Connection pool shut down.");
    }

    /**
     * Create a raw JDBC connection.
     */
    private Connection createConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    /**
     * Get a connection to the MySQL server WITHOUT selecting a database.
     * Used for CREATE DATABASE IF NOT EXISTS.
     */
    public static Connection getServerConnection() throws SQLException {
        Properties props = loadPropertiesStatic();
        String serverUrl = props.getProperty("db.url", "jdbc:mysql://localhost:3307/health_assist")
                .replaceAll("/health_assist.*", "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
        String u = props.getProperty("db.user", "root");
        String p = props.getProperty("db.password", "root");
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("MySQL driver not found", e);
        }
        return DriverManager.getConnection(serverUrl, u, p);
    }

    /**
     * Static helper to load properties without triggering the singleton
     * constructor.
     */
    private static Properties loadPropertiesStatic() {
        Properties props = new Properties();
        try (InputStream in = DatabaseConfig.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException e) {
            System.err.println("Could not load config.properties: " + e.getMessage());
        }
        return props;
    }

    private Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException e) {
            System.err.println("Could not load config.properties: " + e.getMessage());
        }
        return props;
    }
}
