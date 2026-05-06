module com.healthassist {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires jbcrypt;

    opens com.healthassist to javafx.fxml;
    opens com.healthassist.controller to javafx.fxml;
    opens com.healthassist.model to javafx.base;

    exports com.healthassist;
    exports com.healthassist.controller;
    exports com.healthassist.model;
    exports com.healthassist.config;
    exports com.healthassist.dao;
    exports com.healthassist.service;
    exports com.healthassist.util;
}
