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
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.chart.*;
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
    
    // Charts
    private LineChart<Number, Number> activityChart;
    private PieChart statisticsChart;
    private XYChart.Series<Number, Number> modificationSeries;
    private XYChart.Series<Number, Number> deletionSeries;
    
    private boolean isMonitoring = false;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
    private int timeCounter = 0;

    @Override
    public void start(Stage primaryStage) {
        detectionEngine = new DetectionEngine(this);
        fileMonitor = new FileMonitor(detectionEngine, this);
        emergencyResponse = new EmergencyResponse(this);

        // Root layout with background canvas
        StackPane root = new StackPane();

        // Matrix rain canvas
        Canvas canvas = new Canvas(1200, 800);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        startMatrixRain(gc, 1200, 800);

        // Main UI with charts
        HBox mainLayout = new HBox(10);
        mainLayout.setPadding(new Insets(10));

        // Left panel - Controls and logs
        VBox leftPanel = createLeftPanel(primaryStage);
        leftPanel.setPrefWidth(500);

        // Right panel - Charts
        VBox rightPanel = createChartsPanel();
        rightPanel.setPrefWidth(650);

        mainLayout.getChildren().addAll(leftPanel, rightPanel);
        root.getChildren().addAll(canvas, mainLayout);

        Scene scene = new Scene(root, 1200, 800);
        scene.getStylesheets().add(getClass().getResource("hacker-style.css").toExternalForm());

        primaryStage.setScene(scene);
        primaryStage.setTitle("💻 Ransomware Defender v2.0 [Enhanced with Charts]");
        primaryStage.initStyle(StageStyle.DECORATED);
        primaryStage.show();

        logMessage("JavaFX Enhanced UI with Charts initialized");
    }

    private VBox createLeftPanel(Stage primaryStage) {
        VBox leftPanel = new VBox(10);

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

        topControls.getChildren().addAll(pathField, browseBtn);
        
        HBox buttonControls = new HBox(10);
        buttonControls.getChildren().addAll(startBtn, stopBtn, testBtn);

        // Status labels
        statusLabel = new Label("Ready");
        pathLabel = new Label("No folder selected");
        statsLabel = new Label("Files: 0 | Deletes: 0 | Alerts: 0 | Current: 0/10");

        VBox statusBox = new VBox(5, statusLabel, pathLabel, statsLabel);

        // Progress bar
        detectionProgress = new ProgressBar(0);
        detectionProgress.setPrefWidth(450);

        // Log area
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefHeight(400);

        leftPanel.getChildren().addAll(topControls, buttonControls, statusBox, detectionProgress, 
                                     new Label("Activity Log:"), logArea);
        
        return leftPanel;
    }

    private VBox createChartsPanel() {
        VBox chartsPanel = new VBox(15);

        // Real-time Activity Chart
        NumberAxis xAxis = new NumberAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel("Time (seconds)");
        yAxis.setLabel("File Count");
        xAxis.setAnimated(true);
        yAxis.setAnimated(true);

        activityChart = new LineChart<>(xAxis, yAxis);
        activityChart.setTitle("📊 Real-Time File Activity");
        activityChart.setPrefHeight(300);
        activityChart.setAnimated(true);
        activityChart.setCreateSymbols(false);

        // Create data series
        modificationSeries = new XYChart.Series<>();
        modificationSeries.setName("Modifications");
        deletionSeries = new XYChart.Series<>();
        deletionSeries.setName("Deletions");

        activityChart.getData().addAll(modificationSeries, deletionSeries);

        // Statistics Pie Chart
        statisticsChart = new PieChart();
        statisticsChart.setTitle("📈 Detection Statistics");
        statisticsChart.setPrefHeight(300);
        updateStatisticsPieChart(0, 0, 0);

        // Threat Level Gauge (using ProgressIndicator as gauge)
        VBox gaugeBox = new VBox(5);
        Label gaugeLabel = new Label("🚨 Threat Level");
        ProgressIndicator threatGauge = new ProgressIndicator(0);
        threatGauge.setPrefSize(100, 100);
        gaugeBox.getChildren().addAll(gaugeLabel, threatGauge);

        chartsPanel.getChildren().addAll(activityChart, statisticsChart, gaugeBox);
        
        return chartsPanel;
    }

    private void updateStatisticsPieChart(long totalMods, long totalDeletes, long alerts) {
        Platform.runLater(() -> {
            ObservableList<PieChart.Data> pieChartData = FXCollections.observableArrayList(
                new PieChart.Data("Modifications", totalMods),
                new PieChart.Data("Deletions", totalDeletes),
                new PieChart.Data("Alerts Triggered", alerts)
            );
            statisticsChart.setData(pieChartData);
        });
    }

    private void updateActivityChart() {
        Platform.runLater(() -> {
            timeCounter++;
            
            // Add new data points
            modificationSeries.getData().add(new XYChart.Data<>(timeCounter, 
                detectionEngine.getCurrentModificationCount()));
            deletionSeries.getData().add(new XYChart.Data<>(timeCounter, 
                detectionEngine.getCurrentDeletionCount()));

            // Keep only last 20 points for performance
            if (modificationSeries.getData().size() > 20) {
                modificationSeries.getData().remove(0);
            }
            if (deletionSeries.getData().size() > 20) {
                deletionSeries.getData().remove(0);
            }
        });
    }

    // Rest of your existing methods remain the same...
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

    // ====== CALLBACKS ======

    @Override
    public void onMonitoringStarted(Path path) {
        Platform.runLater(() -> {
            isMonitoring = true;
            statusLabel.setText("🔍 Monitoring: " + path.getFileName());
            logMessage("Started monitoring " + path);
        });
    }

    @Override
    public void onMonitoringStopped() {
        Platform.runLater(() -> {
            isMonitoring = false;
            statusLabel.setText("⏹ Monitoring stopped");
            logMessage("Stopped monitoring");
        });
    }

    @Override
    public void onFileEvent(WatchEvent.Kind<?> kind, Path path) {
        Platform.runLater(() -> {
            logMessage("📁 File " + kind.name().toLowerCase() + ": " + path.getFileName());
            // Update charts with new activity
            updateActivityChart();
        });
    }

    @Override
    public void onError(Exception e) {
        logMessage("❌ ERROR: " + e.getMessage());
    }

    @Override
    public void onRansomwareDetected(String type, int fileCount, long timeWindowSeconds) {
        logMessage("🚨 RANSOMWARE DETECTED (" + type + "): " + fileCount + " files in " + timeWindowSeconds + "s");
        alert("🚨 CRITICAL ALERT: Ransomware detected! Executing emergency response...");
        emergencyResponse.executeEmergencyResponse();
    }

    @Override
    public void onStatisticsUpdate(long totalMods, long totalDeletes, long alerts) {
        Platform.runLater(() -> {
            statsLabel.setText("📊 Files: " + totalMods + " | Deletes: " + totalDeletes + " | Alerts: " + alerts +
                " | Current: " + detectionEngine.getCurrentModificationCount() + "/10");
            detectionProgress.setProgress(
                Math.min(1.0, detectionEngine.getCurrentModificationCount() / 10.0));
            
            // Update charts
            updateStatisticsPieChart(totalMods, totalDeletes, alerts);
            updateActivityChart();
        });
    }

    // Emergency Response Callbacks
    @Override
    public void onEmergencyStarted() {
        logMessage("🚨 Emergency protocol activated!");
    }

    @Override
    public void onNetworkDisconnected(boolean success) {
        logMessage(success ? "✅ Network isolated successfully" : "❌ Network isolation failed");
    }

    @Override
    public void onSystemShutdownInitiated() {
        logMessage("💀 Emergency shutdown sequence initiated");
    }

    @Override
    public void onEmergencyComplete() {
        logMessage("✅ Emergency response complete");
    }

    @Override
    public void onEmergencyError(Exception e) {
        logMessage("⚠️ Emergency response error: " + e.getMessage());
    }

    // Matrix Rain Effect (same as before)
    private void startMatrixRain(GraphicsContext gc, int width, int height) {
        final String chars = "01ZX$#@%&🔒🛡️⚡";
        final int fontSize = 16;
        final int columns = width / fontSize;
        int[] drops = new int[columns];
        Random random = new Random();

        new AnimationTimer() {
            @Override
            public void handle(long now) {
                gc.setFill(Color.rgb(0, 0, 0, 0.03));
                gc.fillRect(0, 0, width, height);

                gc.setFill(Color.LIME);
                gc.setFont(javafx.scene.text.Font.font("Consolas", fontSize));

                for (int i = 0; i < columns; i++) {
                    String text = String.valueOf(chars.charAt(random.nextInt(chars.length())));
                    gc.fillText(text, i * fontSize, drops[i] * fontSize);

                    if (drops[i] * fontSize > height && Math.random() > 0.98) {
                        drops[i] = 0;
                    }
                    drops[i]++;
                }
            }
        }.start();
    }
}
