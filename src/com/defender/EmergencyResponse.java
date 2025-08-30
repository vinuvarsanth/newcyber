
package com.defender;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Emergency response system that executes defensive actions
 * when ransomware is detected
 */
public class EmergencyResponse {
    private static final Logger LOGGER = Logger.getLogger(EmergencyResponse.class.getName());

    private final EmergencyCallback callback;
    private boolean emergencyMode = false;

    public interface EmergencyCallback {
        void onEmergencyStarted();
        void onNetworkDisconnected(boolean success);
        void onSystemShutdownInitiated();
        void onEmergencyComplete();
        void onEmergencyError(Exception e);
    }

    public EmergencyResponse(EmergencyCallback callback) {
        this.callback = callback;
    }

    /**
     * Execute emergency response sequence
     */
    public void executeEmergencyResponse() {
        if (emergencyMode) {
            LOGGER.warning("Emergency response already in progress");
            return;
        }

        emergencyMode = true;
        callback.onEmergencyStarted();

        LOGGER.severe("EXECUTING EMERGENCY RESPONSE - RANSOMWARE DETECTED!");

        CompletableFuture.runAsync(() -> {
            try {
                // Step 1: Disconnect from network
                boolean networkDisconnected = disconnectNetwork();
                callback.onNetworkDisconnected(networkDisconnected);

                if (networkDisconnected) {
                    LOGGER.info("Network disconnected successfully");
                } else {
                    LOGGER.warning("Failed to disconnect network");
                }

                // Step 2: Wait a moment for network disconnection to take effect
                Thread.sleep(1000);

                // Step 3: Initiate system shutdown
                callback.onSystemShutdownInitiated();
                initiateSystemShutdown();

                callback.onEmergencyComplete();

            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error during emergency response", e);
                callback.onEmergencyError(e);
            } finally {
                emergencyMode = false;
            }
        });
    }

    /**
     * Disconnect from network based on operating system
     */
    private boolean disconnectNetwork() {
        String os = System.getProperty("os.name").toLowerCase();

        try {
            if (os.contains("windows")) {
                return disconnectWindowsNetwork();
            } else if (os.contains("linux") || os.contains("unix")) {
                return disconnectLinuxNetwork();
            } else {
                LOGGER.warning("Unsupported operating system for network disconnection: " + os);
                return false;
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to disconnect network", e);
            return false;
        }
    }

    /**
     * Disconnect network on Windows using ipconfig
     */
    private boolean disconnectWindowsNetwork() throws IOException, InterruptedException {
        LOGGER.info("Disconnecting Windows network...");

        // Release all IP addresses
        ProcessBuilder pb = new ProcessBuilder("ipconfig", "/release");
        Process process = pb.start();

        boolean finished = process.waitFor(10, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return false;
        }

        int exitCode = process.exitValue();
        if (exitCode == 0) {
            LOGGER.info("Windows network disconnected successfully");
            return true;
        } else {
            LOGGER.warning("ipconfig /release failed with exit code: " + exitCode);
            return false;
        }
    }

    /**
     * Disconnect network on Linux using nmcli
     */
    private boolean disconnectLinuxNetwork() throws IOException, InterruptedException {
        LOGGER.info("Disconnecting Linux network...");

        // Disconnect all network connections
        ProcessBuilder pb = new ProcessBuilder("nmcli", "networking", "off");
        Process process = pb.start();

        boolean finished = process.waitFor(10, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return false;
        }

        int exitCode = process.exitValue();
        if (exitCode == 0) {
            LOGGER.info("Linux network disconnected successfully");
            return true;
        } else {
            LOGGER.warning("nmcli networking off failed with exit code: " + exitCode);
            return false;
        }
    }

    /**
     * Initiate system shutdown
     */
    private void initiateSystemShutdown() throws IOException, InterruptedException {
        String os = System.getProperty("os.name").toLowerCase();

        LOGGER.severe("INITIATING EMERGENCY SYSTEM SHUTDOWN");

        ProcessBuilder pb;
        if (os.contains("windows")) {
            // Windows shutdown command - immediate shutdown
            pb = new ProcessBuilder("shutdown", "/s", "/t", "0", "/f",  "/c", "Emergency shutdown - Ransomware detected");
        } else if (os.contains("linux") || os.contains("unix")) {
            // Linux shutdown command - immediate shutdown
            pb = new ProcessBuilder("sudo", "shutdown", "now", "Emergency shutdown - Ransomware detected");
        } else {
            LOGGER.severe("Unsupported operating system for shutdown: " + os);
            return;
        }

        try {
            Process process = pb.start();
            LOGGER.info("System shutdown command executed");
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to execute shutdown command", e);
            throw e;
        }
    }

    /**
     * Test network disconnection without actually disconnecting
     */
    public boolean testNetworkDisconnection() {
        String os = System.getProperty("os.name").toLowerCase();

        try {
            if (os.contains("windows")) {
                // Test if ipconfig command is available
                ProcessBuilder pb = new ProcessBuilder("ipconfig", "/?");
                Process process = pb.start();
                return process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0;
            } else if (os.contains("linux") || os.contains("unix")) {
                // Test if nmcli command is available
                ProcessBuilder pb = new ProcessBuilder("which", "nmcli");
                Process process = pb.start();
                return process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0;
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to test network disconnection capabilities", e);
        }

        return false;
    }

    public boolean isEmergencyMode() {
        return emergencyMode;
    }
}
