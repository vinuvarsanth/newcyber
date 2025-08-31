package com.defender.gui;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

import com.defender.DetectionEngine;
import com.defender.EmergencyResponse;
import com.defender.FileMonitor;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.*;

public class DefenderGUI extends Application implements
        FileMonitor.FileMonitorCallback,
        DetectionEngine.DetectionCallback,
        EmergencyResponse.EmergencyCallback {

    private FileMonitor fileMonitor;
    private DetectionEngine detectionEngine;
    private EmergencyResponse emergencyResponse;

    // GUI elements
    private TextField pathField;
    private Label statusLabel, pathLabel, statsLabel;
    private TextArea logArea;
    private ProgressBar detectionProgress;
    private boolean isMonitoring = false;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");

    @Override
    public void start(Stage primaryStage) {
        detectionEngine = new DetectionEngine(this);
        fileMonitor = new FileMonitor(detectionEngine, this);
        emergencyResponse = new EmergencyResponse(this);

        // Root layout with background canvas
        StackPane root = new StackPane();

        // Matrix rain canvas
        Canvas canvas = new Canvas(800, 600);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        startMatrixRain(gc, 800, 600);

        VBox mainUI = new VBox(10);
        mainUI.setPadding(new Insets(10));

        // Top controls
        HBox topControls = new HBox(10);
        pathField = new TextField();
        pathField.setPromptText("Folder to monitor...");
        Button browseBtn = new Button("Browse");
        Button startBtn = new Button("Start");
        Button stopBtn = new Button("Stop");
        Button testBtn = new Button("Test Emergency");

        browseBtn.setOnAction(e -> selectFolder(primaryStage));
        startBtn.setOnAction(e -> startMonitoring());
        stopBtn.setOnAction(e -> stopMonitoring());
        testBtn.setOnAction(e -> testEmergency());

        topControls.getChildren().addAll(pathField, browseBtn, startBtn, stopBtn, testBtn);

        // Status labels
        statusLabel = new Label("Ready");
        pathLabel = new Label("No folder selected");
        statsLabel = new Label("Files: 0 | Deletes: 0 | Alerts: 0 | Current: 0/10");

        VBox statusBox = new VBox(5, statusLabel, pathLabel, statsLabel);

        // Progress bar
        detectionProgress = new ProgressBar(0);

        // Log area
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefHeight(400);

        mainUI.getChildren().addAll(topControls, statusBox, detectionProgress, logArea);
        root.getChildren().addAll(canvas, mainUI);

        Scene scene = new Scene(root, 800, 600);
        scene.getStylesheets().add(getClass().getResource("hacker-style.css").toExternalForm());

        primaryStage.setScene(scene);
        primaryStage.setTitle("💻 Ransomware Defender v1.0 [Hacker Mode]");
        primaryStage.initStyle(StageStyle.DECORATED);
        primaryStage.show();

        logMessage("JavaFX Hacker UI initialized");
    }

    private void selectFolder(Stage stage) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Folder to Monitor");
        java.io.File file = chooser.showDialog(stage);
        if (file != null) {
            Path selected = file.toPath();
            pathField.setText(selected.toString());
            pathLabel.setText(selected.toString());
        }
    }

    private void startMonitoring() {
        String path = pathField.getText().trim();
        if (path.isEmpty()) {
            alert("Please select a folder first.");
            return;
        }

        try {
            Path monitorPath = Paths.get(path);
            fileMonitor.startMonitoring(monitorPath);
        } catch (Exception e) {
            alert("Failed to start monitoring: " + e.getMessage());
            logMessage("ERROR: " + e.getMessage());
        }
    }

    private void stopMonitoring() {
        fileMonitor.stopMonitoring();
    }

    private void testEmergency() {
        alert("Simulating emergency response (network disconnect + shutdown).");
        emergencyResponse.executeEmergencyResponse();
    }

    private void alert(String msg) {
        Platform.runLater(() -> new Alert(Alert.AlertType.INFORMATION, msg).showAndWait());
    }

    private void logMessage(String msg) {
        Platform.runLater(() -> {
            String timestamp = dateFormat.format(new Date());
            logArea.appendText("[" + timestamp + "] " + msg + "\n");
        });
    }

    // ====== FILE MONITOR CALLBACKS ======

    @Override
    public void onMonitoringStarted(Path path) {
        Platform.runLater(() -> {
            isMonitoring = true;
            statusLabel.setText("Monitoring: " + path);
            logMessage("Started monitoring " + path);
        });
    }

    @Override
    public void onMonitoringStopped() {
        Platform.runLater(() -> {
            isMonitoring = false;
            statusLabel.setText("Monitoring stopped");
            logMessage("Stopped monitoring");
        });
    }

    @Override
    public void onFileEvent(WatchEvent.Kind<?> kind, Path path) {
        Platform.runLater(() -> {
            logMessage("File " + kind.name().toLowerCase() + ": " + path);
        });
    }

    @Override
    public void onError(Exception e) {
        logMessage("ERROR: " + e.getMessage());
    }

    // ====== DETECTION ENGINE CALLBACKS ======

    @Override
    public void onRansomwareDetected(String type, int fileCount, long timeWindowSeconds) {
        logMessage("🚨 Ransomware detected (" + type + "): " + fileCount + " files in " + timeWindowSeconds + "s");
        alert("Ransomware detected! Executing emergency response...");
        emergencyResponse.executeEmergencyResponse();
    }

    @Override
    public void onStatisticsUpdate(long totalMods, long totalDeletes, long alerts) {
        Platform.runLater(() -> {
            statsLabel.setText("Files: " + totalMods + " | Deletes: " + totalDeletes + " | Alerts: " + alerts +
                " | Current: " + detectionEngine.getCurrentModificationCount() + "/10");
            detectionProgress.setProgress(
                Math.min(1.0, detectionEngine.getCurrentModificationCount() / 10.0));
        });
    }

    // ===== EMERGENCY RESPONSE CALLBACKS =====

    @Override
    public void onEmergencyStarted() {
        logMessage("🚨 Emergency started!");
    }

    @Override
    public void onNetworkDisconnected(boolean success) {
        logMessage(success ? "✅ Network disconnected" : "❌ Failed to disconnect network");
    }

    @Override
    public void onSystemShutdownInitiated() {
        logMessage("💀 System shutdown initiated");
    }

    @Override
    public void onEmergencyComplete() {
        logMessage("Emergency complete");
    }

    @Override
    public void onEmergencyError(Exception e) {
        logMessage("Emergency error: " + e.getMessage());
    }

    // ===== MATRIX RAIN EFFECT =====

    private void startMatrixRain(GraphicsContext gc, int width, int height) {
        final String chars = "01ZX$#@%&";
        final int fontSize = 18;
        final int columns = width / fontSize;
        int[] drops = new int[columns];
        Random random = new Random();

        new AnimationTimer() {
            @Override
            public void handle(long now) {
                gc.setFill(Color.rgb(0, 0, 0, 0.05));
                gc.fillRect(0, 0, width, height);

                gc.setFill(Color.LIME);
                gc.setFont(javafx.scene.text.Font.font("Consolas", fontSize));

                for (int i = 0; i < columns; i++) {
                    String text = String.valueOf(chars.charAt(random.nextInt(chars.length())));
                    gc.fillText(text, i * fontSize, drops[i] * fontSize);

                    if (drops[i] * fontSize > height && Math.random() > 0.975) {
                        drops[i] = 0;
                    }

                    drops[i]++;
                }
            }
        }.start();
    }
}
