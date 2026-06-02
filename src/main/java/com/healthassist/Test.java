package com.healthassist;

import com.healthassist.util.SessionManager;

public class Test {
    public static void main(String[] args) {
        SessionManager.getInstance();
        System.out.println("WORKS!");
    }
}
