package com.defender;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * File system monitor using Java WatchService API
 * Monitors specified directory for file changes
 */
public class FileMonitor {
    private static final Logger LOGGER = Logger.getLogger(FileMonitor.class.getName());
    
    private WatchService watchService;
    private ExecutorService executorService;
    private boolean monitoring = false;
    private Path monitoredPath;
    private DetectionEngine detectionEngine;
    private FileMonitorCallback callback;
    
    public interface FileMonitorCallback {
        void onFileEvent(WatchEvent.Kind<?> kind, Path path);
        void onMonitoringStarted(Path path);
        void onMonitoringStopped();
        void onError(Exception e);
    }
    
    public FileMonitor(DetectionEngine detectionEngine, FileMonitorCallback callback) {
        this.detectionEngine = detectionEngine;
        this.callback = callback;
        this.executorService = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "FileMonitor-Thread");
            t.setDaemon(true);
            return t;
        });
    }
    
    /**
     * Start monitoring the specified directory
     */
    public synchronized void startMonitoring(Path directoryPath) throws IOException {
        if (monitoring) {
            stopMonitoring();
        }
        
        if (!Files.exists(directoryPath) || !Files.isDirectory(directoryPath)) {
            throw new IllegalArgumentException("Path must be an existing directory: " + directoryPath);
        }
        
        this.monitoredPath = directoryPath;
        this.watchService = FileSystems.getDefault().newWatchService();
        
        // Register directory and all subdirectories
        registerDirectoryTree(directoryPath);
        
        monitoring = true;
        
        // Start monitoring in background thread
        executorService.submit(this::monitorLoop);
        
        callback.onMonitoringStarted(directoryPath);
        LOGGER.info("Started monitoring: " + directoryPath);
    }
    
    /**
     * Register directory and all subdirectories for monitoring
     */
    private void registerDirectoryTree(Path start) throws IOException {
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                dir.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);
                return FileVisitResult.CONTINUE;
            }
        });
    }
    
    /**
     * Main monitoring loop
     */
    private void monitorLoop() {
        try {
            while (monitoring) {
                WatchKey key = watchService.take();
                
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    
                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }
                    
                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path filename = ev.context();
                    Path fullPath = ((Path) key.watchable()).resolve(filename);
                    
                    // Notify callback
                    callback.onFileEvent(kind, fullPath);
                    
                    // Check with detection engine
                    if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                        detectionEngine.recordFileModification(fullPath);
                    } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                        detectionEngine.recordFileDeletion(fullPath);
                    }
                    
                    LOGGER.fine(String.format("File event: %s - %s", kind.name(), fullPath));
                }
                
                boolean valid = key.reset();
                if (!valid) {
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.info("File monitoring interrupted");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error in file monitoring", e);
            callback.onError(e);
        }
    }
    
    /**
     * Stop monitoring
     */
    public synchronized void stopMonitoring() {
        monitoring = false;
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Error closing watch service", e);
            }
        }
        callback.onMonitoringStopped();
        LOGGER.info("Stopped monitoring");
    }
    
    public boolean isMonitoring() {
        return monitoring;
    }
    
    public Path getMonitoredPath() {
        return monitoredPath;
    }
    
    /**
     * Shutdown the file monitor
     */
    public void shutdown() {
        stopMonitoring();
        executorService.shutdown();
    }
}
