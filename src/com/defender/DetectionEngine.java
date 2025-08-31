package com.defender;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Detection engine that analyzes file modification & deletion patterns
 * to identify potential ransomware behavior
 */
public class DetectionEngine {
    private static final Logger LOGGER = Logger.getLogger(DetectionEngine.class.getName());
    
    // Detection thresholds
    private static final int MODIFICATION_THRESHOLD = 10; // files
    private static final int DELETION_THRESHOLD = 5; // files
    private static final long TIME_WINDOW_SECONDS = 2; // seconds
    
    // Track file modifications
    private final ConcurrentLinkedQueue<FileEvent> recentModifications;
    private final AtomicInteger modificationCount;
    
    // Track file deletions
    private final ConcurrentLinkedQueue<FileEvent> recentDeletions;
    private final AtomicInteger deletionCount;
    
    private final DetectionCallback callback;
    
    // Statistics
    private long totalModifications = 0;
    private long totalDeletions = 0;
    private long alertsTriggered = 0;
    
    public interface DetectionCallback {
        void onRansomwareDetected(String type, int fileCount, long timeWindowSeconds);
        void onStatisticsUpdate(long totalMods, long totalDeletes, long alerts);
    }
    
    private static class FileEvent {
        final Path path;
        final Instant timestamp;
        
        FileEvent(Path path, Instant timestamp) {
            this.path = path;
            this.timestamp = timestamp;
        }
    }
    
    public DetectionEngine(DetectionCallback callback) {
        this.callback = callback;
        this.recentModifications = new ConcurrentLinkedQueue<>();
        this.recentDeletions = new ConcurrentLinkedQueue<>();
        this.modificationCount = new AtomicInteger(0);
        this.deletionCount = new AtomicInteger(0);
    }
    
    /**
     * Record a file modification and check for ransomware pattern
     */
    public synchronized void recordFileModification(Path filePath) {
        Instant now = Instant.now();
        recentModifications.offer(new FileEvent(filePath, now));
        totalModifications++;
        
        cleanupOldEvents(recentModifications, now);
        int currentCount = recentModifications.size();
        modificationCount.set(currentCount);
        
        callback.onStatisticsUpdate(totalModifications, totalDeletions, alertsTriggered);
        
        if (currentCount >= MODIFICATION_THRESHOLD) {
            alertsTriggered++;
            LOGGER.warning(String.format(
                "🚨 RANSOMWARE DETECTED (Modification): %d files modified in last %d seconds.\nExample: %s",
                currentCount, TIME_WINDOW_SECONDS, filePath.toString()
            ));
            callback.onRansomwareDetected("Modification", currentCount, TIME_WINDOW_SECONDS);
        } else {
            LOGGER.fine(String.format("File modifications in window: %d/%d",
                currentCount, MODIFICATION_THRESHOLD));
        }
    }
    
    /**
     * Record a file deletion and check for ransomware pattern
     */
    public synchronized void recordFileDeletion(Path filePath) {
        Instant now = Instant.now();
        recentDeletions.offer(new FileEvent(filePath, now));
        totalDeletions++;
        
        cleanupOldEvents(recentDeletions, now);
        int currentCount = recentDeletions.size();
        deletionCount.set(currentCount);
        
        callback.onStatisticsUpdate(totalModifications, totalDeletions, alertsTriggered);
        
        // Detect single suspicious deletion
        if (currentCount == 1) {
            LOGGER.info(String.format("⚠️ Single file deleted: %s", filePath.toString()));
        }
        
        // Detect mass deletions within short window
        if (currentCount >= DELETION_THRESHOLD) {
            alertsTriggered++;
            LOGGER.warning(String.format(
                "🚨 RANSOMWARE DETECTED (Deletion): %d files deleted in last %d seconds.\nExample: %s",
                currentCount, TIME_WINDOW_SECONDS, filePath.toString()
            ));
            callback.onRansomwareDetected("Deletion", currentCount, TIME_WINDOW_SECONDS);
        }
    }
    
    /**
     * Increment modification count manually (for testing)
     */
    public synchronized void incrementModifications() {
        totalModifications++;
        callback.onStatisticsUpdate(totalModifications, totalDeletions, alertsTriggered);
    }
    
    /**
     * Increment alert count manually (for testing)
     */
    public synchronized void incrementAlerts() {
        alertsTriggered++;
        callback.onStatisticsUpdate(totalModifications, totalDeletions, alertsTriggered);
    }
    
    /**
     * Remove old events outside the time window
     */
    private void cleanupOldEvents(ConcurrentLinkedQueue<FileEvent> queue, Instant now) {
        Instant cutoff = now.minus(TIME_WINDOW_SECONDS, ChronoUnit.SECONDS);
        queue.removeIf(event -> event.timestamp.isBefore(cutoff));
    }
    
    // Getter methods
    public int getCurrentModificationCount() { return modificationCount.get(); }
    public int getCurrentDeletionCount() { return deletionCount.get(); }
    public long getTotalModifications() { return totalModifications; }
    public long getTotalDeletions() { return totalDeletions; }
    public long getAlertsTriggered() { return alertsTriggered; }
    
    // Reset stats
    public synchronized void resetStatistics() {
        recentModifications.clear();
        recentDeletions.clear();
        modificationCount.set(0);
        deletionCount.set(0);
        totalModifications = 0;
        totalDeletions = 0;
        alertsTriggered = 0;
        LOGGER.info("Detection statistics reset");
        callback.onStatisticsUpdate(totalModifications, totalDeletions, alertsTriggered);
    }
}
