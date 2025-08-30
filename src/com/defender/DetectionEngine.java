
package com.defender;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Detection engine that analyzes file modification patterns
 * to identify potential ransomware behavior
 */
public class DetectionEngine {
    private static final Logger LOGGER = Logger.getLogger(DetectionEngine.class.getName());

    // Detection thresholds
    private static final int MODIFICATION_THRESHOLD = 10;
    private static final long TIME_WINDOW_SECONDS = 2;

    // Track file modifications
    private final ConcurrentLinkedQueue<FileModification> recentModifications;
    private final AtomicInteger modificationCount;
    private final DetectionCallback callback;

    // Statistics
    private long totalModifications = 0;
    private long alertsTriggered = 0;

    public interface DetectionCallback {
        void onRansomwareDetected(int fileCount, long timeWindowSeconds);
        void onStatisticsUpdate(long totalMods, long alerts);
    }

    private static class FileModification {
        final Path path;
        final Instant timestamp;

        FileModification(Path path, Instant timestamp) {
            this.path = path;
            this.timestamp = timestamp;
        }
    }

    public DetectionEngine(DetectionCallback callback) {
        this.callback = callback;
        this.recentModifications = new ConcurrentLinkedQueue<>();
        this.modificationCount = new AtomicInteger(0);
    }

    /**
     * Record a file modification and check for ransomware pattern
     */
    public synchronized void recordFileModification(Path filePath) {
        Instant now = Instant.now();

        // Add new modification
        recentModifications.offer(new FileModification(filePath, now));
        totalModifications++;

        // Remove old modifications outside time window
        cleanupOldModifications(now);

        int currentCount = recentModifications.size();
        modificationCount.set(currentCount);

        // Update statistics
        callback.onStatisticsUpdate(totalModifications, alertsTriggered);

        // Check for ransomware pattern
        if (currentCount >= MODIFICATION_THRESHOLD) {
            // Log the paths of modified files for additional context
            StringBuilder modifiedFiles = new StringBuilder();
            for (FileModification mod : recentModifications) {
                modifiedFiles.append(mod.path.toString()).append("; ");
            }
            LOGGER.warning(String.format("RANSOMWARE DETECTED: %d files modified in %d seconds. Modified files: %s",  
                currentCount, TIME_WINDOW_SECONDS, modifiedFiles.toString()));

            alertsTriggered++;
            callback.onRansomwareDetected(currentCount, TIME_WINDOW_SECONDS);

            // Clear modifications after alert to prevent repeated triggers
            recentModifications.clear();
            modificationCount.set(0);
        } else {
            LOGGER.fine(String.format("File modifications in window: %d/%d",  
                currentCount, MODIFICATION_THRESHOLD));
        }
    }

    /**
     * Remove modifications older than the time window
     */
    private void cleanupOldModifications(Instant now) {
        Instant cutoff = now.minus(TIME_WINDOW_SECONDS, ChronoUnit.SECONDS);

        recentModifications.removeIf(mod -> mod.timestamp.isBefore(cutoff));
    }

    /**
     * Get current modification count in time window
     */
    public int getCurrentModificationCount() {
        return modificationCount.get();
    }

    /**
     * Get total modifications recorded
     */
    public long getTotalModifications() {
        return totalModifications;
    }

    /**
     * Get number of alerts triggered
     */
    public long getAlertsTriggered() {
        return alertsTriggered;
    }

    /**
     * Reset statistics
     */
    public synchronized void resetStatistics() {
        recentModifications.clear();
        modificationCount.set(0);
        totalModifications = 0;
        alertsTriggered = 0;
        LOGGER.info("Detection statistics reset");
    }
}
