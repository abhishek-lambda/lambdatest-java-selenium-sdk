package com.lambdatest.selenium.agent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * ThreadLocal storage for WebDriver instances.
 * 
 * This class provides thread-safe storage for WebDriver fields, enabling
 * parallel="methods" execution without code changes.
 * 
 * Each thread maintains its own map of field -> driver mappings.
 */
public class ThreadLocalDriverStorage {
    
    private static final Logger LOGGER = Logger.getLogger(ThreadLocalDriverStorage.class.getName());
    
    // ThreadLocal storage: each thread has its own map of (fieldKey -> driver)
    private static final ThreadLocal<Map<String, WebDriver>> THREAD_STORAGE = 
        ThreadLocal.withInitial(ConcurrentHashMap::new);
    
    // Track which fields have been logged (to avoid spam)
    private static final Map<String, Boolean> LOGGED_FIELDS = new ConcurrentHashMap<>();
    
    /**
     * Store a WebDriver for the current thread.
     * 
     * @param fieldKey Unique identifier for the field (className.fieldName)
     * @param driver The WebDriver instance to store
     */
    public static void setDriver(String fieldKey, WebDriver driver) {
        if (driver == null) {
            THREAD_STORAGE.get().remove(fieldKey);
            return;
        }
        
        THREAD_STORAGE.get().put(fieldKey, driver);
        
        // Log once per field per thread (first write only)
        String logKey = Thread.currentThread().getId() + ":" + fieldKey;
        if (!LOGGED_FIELDS.containsKey(logKey)) {
            LOGGED_FIELDS.put(logKey, true);
            
            String sessionId = "unknown";
            if (driver instanceof RemoteWebDriver) {
                RemoteWebDriver rwd = (RemoteWebDriver) driver;
                try {
                    if (rwd.getSessionId() != null) {
                        sessionId = rwd.getSessionId().toString().substring(0, 8) + "...";
                    }
                } catch (Exception e) {
                    // Ignore - session might not be available yet
                }
            }
            
            LOGGER.info(String.format(
                "[Thread-%d/%s] ThreadLocal field write: %s (key=%s) -> Session %s",
                Thread.currentThread().getId(),
                Thread.currentThread().getName(),
                fieldKey.substring(fieldKey.lastIndexOf('.') + 1),
                fieldKey,
                sessionId));
        }
    }
    
    /**
     * Retrieve a WebDriver for the current thread.
     * 
     * @param fieldKey Unique identifier for the field (className.fieldName)
     * @return The WebDriver instance, or null if not found
     */
    public static WebDriver getDriver(String fieldKey) {
        WebDriver driver = THREAD_STORAGE.get().get(fieldKey);
        if (driver == null) {
            LOGGER.warning(String.format("[Thread-%d/%s] ThreadLocal driver lookup failed for key: %s (mapSize=%d)",
                Thread.currentThread().getId(),
                Thread.currentThread().getName(),
                fieldKey,
                THREAD_STORAGE.get().size()));
        } else {
            LOGGER.fine(String.format("[Thread-%d/%s] ThreadLocal driver lookup success for key: %s",
                Thread.currentThread().getId(),
                Thread.currentThread().getName(),
                fieldKey));
        }
        return driver;
    }
    
    /**
     * Clean up ThreadLocal storage for the current thread.
     * Should be called after test completes to prevent memory leaks.
     */
    public static void cleanupThread() {
        Map<String, WebDriver> storage = THREAD_STORAGE.get();
        if (storage != null) {
            storage.clear();
        }
        THREAD_STORAGE.remove();
        
        LOGGER.fine(String.format(
            "[Thread-%d/%s] Cleaned up ThreadLocal driver storage",
            Thread.currentThread().getId(),
            Thread.currentThread().getName()));
    }
    
    /**
     * Get all drivers for the current thread (for debugging).
     */
    public static Map<String, WebDriver> getAllDrivers() {
        return new ConcurrentHashMap<>(THREAD_STORAGE.get());
    }
    
    /**
     * Get the number of drivers stored for the current thread.
     */
    public static int getDriverCount() {
        return THREAD_STORAGE.get().size();
    }
}

