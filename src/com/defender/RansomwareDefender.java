package com.defender;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.defender.gui.DefenderGUI;
import javafx.application.Application;

/**
 * Main class for Ransomware Defender Application
 * Provides real-time folder monitoring and emergency response
 */
public class RansomwareDefender {
    private static final Logger LOGGER = Logger.getLogger(RansomwareDefender.class.getName());

    public static void main(String[] args) {
        try {
            LOGGER.info("Launching Ransomware Defender (JavaFX)...");
            Application.launch(DefenderGUI.class, args);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to start application", e);
            System.exit(1);
        }
    }
}
