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
    private ProgressIndicator threatGauge;

    // Charts
    private LineChart activityChart;
    private PieChart statisticsChart;
    private XYChart.Series modificationSeries;
    private XYChart.Series deletionSeries;

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

        // Main UI container with center alignment
        HBox mainLayout = new HBox(20);
        mainLayout.setPadding(new Insets(20));
        mainLayout.getStyleClass().add("main-container");
        mainLayout.setAlignment(javafx.geometry.Pos.CENTER);

        // Left panel - Controls and logs (now styled as panel)
        VBox leftPanel = createLeftPanel(primaryStage);
        leftPanel.setPrefWidth(500);
        leftPanel.getStyleClass().add("control-panel");

        // Right panel - Charts
        VBox rightPanel = createChartsPanel();
        rightPanel.setPrefWidth(650);

        mainLayout.getChildren().addAll(leftPanel, rightPanel);
        
        // Center the main layout in the root
        StackPane.setAlignment(mainLayout, javafx.geometry.Pos.CENTER);
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
        VBox leftPanel = new VBox(15);
        leftPanel.setPadding(new Insets(10));

        // Top controls
        HBox topControls = new HBox(10);
        pathField = new TextField();
        pathField.setPromptText("Folder to monitor...");
        Button browseBtn = new Button("📁 Browse");
        browseBtn.getStyleClass().add("control-button");
        
        browseBtn.setOnAction(e -> selectFolder(primaryStage));
        topControls.getChildren().addAll(pathField, browseBtn);

        // Button controls with enhanced styling
        HBox buttonControls = new HBox(10);
        Button startBtn = new Button("▶️ Start");
        Button stopBtn = new Button("⏹️ Stop");
        Button testBtn = new Button("🚨 Test Emergency");
        
        // Apply custom styling to buttons
        startBtn.getStyleClass().add("control-button");
        stopBtn.getStyleClass().add("control-button");
        testBtn.getStyleClass().add("control-button");
        
        startBtn.setOnAction(e -> startMonitoring());
        stopBtn.setOnAction(e -> stopMonitoring());
        testBtn.setOnAction(e -> testEmergency());
        
        buttonControls.getChildren().addAll(startBtn, stopBtn, testBtn);

        // Status labels with title
        Label statusTitle = new Label("📊 System Status");
        statusTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        
        statusLabel = new Label("Ready");
        pathLabel = new Label("No folder selected");
        statsLabel = new Label("Files: 0 | Deletes: 0 | Alerts: 0 | Current: 0/10");
        
        VBox statusBox = new VBox(8, statusTitle, statusLabel, pathLabel, statsLabel);

        // Progress bar
        detectionProgress = new ProgressBar(0);
        detectionProgress.setPrefWidth(450);

        // Log area with title
        Label logTitle = new Label("📝 Activity Log");
        logTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefHeight(350);

        leftPanel.getChildren().addAll(
            topControls, 
            buttonControls, 
            statusBox, 
            detectionProgress,
            logTitle, 
            logArea
        );

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
        activityChart.setPrefHeight(250);
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
        statisticsChart.setPrefHeight(250);
        updateStatisticsPieChart(0, 0, 0);

        // Threat Level Panel (styled as a prominent panel)
        VBox threatPanel = new VBox(10);
        threatPanel.getStyleClass().add("threat-panel");
        threatPanel.setAlignment(javafx.geometry.Pos.CENTER);
        threatPanel.setPadding(new Insets(15));
        
        Label threatTitle = new Label("🚨 THREAT LEVEL MONITOR");
        threatTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #ff0000;");
        
        // Threat gauge with larger size
        threatGauge = new ProgressIndicator(0);
        threatGauge.setPrefSize(120, 120);
        threatGauge.getStyleClass().add("threat-gauge");
        
        // Threat level text with larger font
        Label threatLevelText = new Label("LOW");
        threatLevelText.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #00ff41;");
        threatLevelText.setId("threat-level-text");
        
        // Additional threat info
        Label threatInfo = new Label("System Status: SECURE");
        threatInfo.setStyle("-fx-font-size: 12px; -fx-text-fill: #00ff41;");
        threatInfo.setId("threat-info-text");
        
        threatPanel.getChildren().addAll(threatTitle, threatGauge, threatLevelText, threatInfo);

        chartsPanel.getChildren().addAll(activityChart, statisticsChart, threatPanel);
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

    private void updateThreatLevel(long totalMods, long totalDeletes, long alerts) {
        Platform.runLater(() -> {
            // Calculate threat level based on activity
            double threatLevel = 0.0;
            String threatText = "LOW";
            String threatColor = "#00ff41"; // Green
            String threatInfo = "System Status: SECURE";
            
            int currentMods = detectionEngine.getCurrentModificationCount();
            
            if (alerts > 0) {
                threatLevel = 1.0;
                threatText = "CRITICAL";
                threatColor = "#ff0000"; // Red
                threatInfo = "⚠️ RANSOMWARE DETECTED!";
            } else if (currentMods >= 8) {
                threatLevel = 0.8;
                threatText = "HIGH";
                threatColor = "#ff6600"; // Orange
                threatInfo = "⚠️ High Activity Detected";
            } else if (currentMods >= 5 || totalDeletes > 10) {
                threatLevel = 0.5;
                threatText = "MEDIUM";
                threatColor = "#ffff00"; // Yellow
                threatInfo = "⚠️ Moderate Activity";
            } else if (currentMods >= 3 || totalMods > 20) {
                threatLevel = 0.3;
                threatText = "LOW-MED";
                threatColor = "#ccff00"; // Yellow-green
                threatInfo = "📊 Normal Activity";
            }
            
            threatGauge.setProgress(threatLevel);
            
            // Update threat level text
            Label threatLevelText = (Label) threatGauge.getParent().lookup("#threat-level-text");
            Label threatInfoText = (Label) threatGauge.getParent().lookup("#threat-info-text");
            
            if (threatLevelText != null) {
                threatLevelText.setText(threatText);
                threatLevelText.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: " + threatColor + ";");
            }
            
            if (threatInfoText != null) {
                threatInfoText.setText(threatInfo);
                threatInfoText.setStyle("-fx-font-size: 12px; -fx-text-fill: " + threatColor + ";");
            }
        });
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
            
            // Update threat level gauge
            updateThreatLevel(totalMods, totalDeletes, alerts);
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

    // Matrix Rain Effect
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

    public static void main(String[] args) {
        launch(args);
    }
}
