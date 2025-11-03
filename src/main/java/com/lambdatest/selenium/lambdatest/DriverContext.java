package com.lambdatest.selenium.lambdatest;

import java.util.logging.Logger;

import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Lightweight wrapper for RemoteWebDriver that delegates thread affinity to SessionThreadManager.
 * 
 * This class:
 * - Holds the RemoteWebDriver instance
 * - Tracks driver-specific state (alive/dead, creation time)
 * - Delegates all thread-affinity operations to SessionThreadManager
 * 
 * This provides a clean separation:
 * - SessionThreadManager = Global session-thread mapping service
 * - DriverContext = Per-driver instance wrapper with state
 */
public class DriverContext {
    
    private static final Logger LOGGER = Logger.getLogger(DriverContext.class.getName());
    
    private final RemoteWebDriver driver;
    private final String sessionId;
    private final long createdAt;
    private boolean driverAlive;
    
    public DriverContext(RemoteWebDriver driver) {
        this.driver = driver;
        this.sessionId = driver.getSessionId() != null ? driver.getSessionId().toString() : null;
        this.createdAt = System.currentTimeMillis();
        this.driverAlive = true;
        
        // Register this session with SessionThreadManager
        if (sessionId != null) {
            SessionThreadManager manager = SessionThreadManager.getInstance();
            boolean registered = manager.registerSession(sessionId);
            if (!registered) {
                LOGGER.warning(String.format(
                    "Failed to register session %s - may already be bound to another thread",
                    sessionId));
            }
        }
    }
    
    /**
     * Get the driver instance.
     * Validates thread ownership via SessionThreadManager before returning.
     * 
     * @return The RemoteWebDriver instance
     * @throws IllegalStateException if called from wrong thread
     */
    public RemoteWebDriver getDriver() {
        // Delegate thread validation to SessionThreadManager
        if (sessionId != null && !SessionThreadManager.getInstance().validateThreadOwnership(sessionId)) {
            throw new IllegalStateException(getThreadViolationMessage());
        }
        return driver;
    }
    
    /**
     * Get the driver without thread validation (for internal use only).
     * Use with caution - bypasses thread affinity checks.
     * 
     * @return The RemoteWebDriver instance
     */
    RemoteWebDriver getDriverUnsafe() {
        return driver;
    }
    
    /**
     * Get the session ID for this driver.
     * 
     * @return The session ID
     */
    public String getSessionId() {
        return sessionId;
    }
    
    /**
     * Get the owner thread ID from SessionThreadManager.
     * 
     * @return The owner thread ID, or null if not registered
     */
    public Long getOwnerThreadId() {
        return SessionThreadManager.getInstance().getOwnerThreadId(sessionId);
    }
    
    /**
     * Get the owner thread name from SessionThreadManager.
     * 
     * @return The owner thread name, or null if not registered
     */
    public String getOwnerThreadName() {
        return SessionThreadManager.getInstance().getOwnerThreadName(sessionId);
    }
    
    /**
     * Check if current thread owns this driver context.
     * Delegates to SessionThreadManager.
     * 
     * @return true if current thread owns this context
     */
    public boolean isCurrentThreadOwner() {
        if (sessionId == null) {
            return true;
        }
        return SessionThreadManager.getInstance().isCurrentThreadOwner(sessionId);
    }
    
    /**
     * Validate thread access. Delegates to SessionThreadManager.
     * 
     * @return true if valid, false if thread violation
     */
    public boolean validateThreadAccess() {
        if (sessionId == null) {
            return true;
        }
        return SessionThreadManager.getInstance().validateThreadOwnership(sessionId);
    }
    
    /**
     * Check if driver is alive.
     * 
     * @return true if driver is alive and session is active
     */
    public boolean isDriverAlive() {
        return driverAlive && isSessionActive();
    }
    
    /**
     * Set driver alive status.
     * Unregisters from SessionThreadManager when set to false.
     * 
     * @param alive The new alive status
     */
    public void setDriverAlive(boolean alive) {
        this.driverAlive = alive;
        
        // Unregister from SessionThreadManager when driver is quit
        if (!alive && sessionId != null) {
            SessionThreadManager.getInstance().unregisterSession(sessionId);
            LOGGER.fine(String.format("Unregistered session %s (driver no longer alive)", sessionId));
        }
    }
    
    /**
     * Check if the WebDriver session is still active.
     * 
     * @return true if session is active
     */
    private boolean isSessionActive() {
        try {
            return driver != null && driver.getSessionId() != null;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Get the age of this context in milliseconds.
     * 
     * @return Age in milliseconds since creation
     */
    public long getAgeMillis() {
        return System.currentTimeMillis() - createdAt;
    }
    
    /**
     * Get detailed string with thread info from SessionThreadManager.
     * 
     * @return Detailed context information
     */
    public String toDetailedString() {
        Long ownerThreadId = getOwnerThreadId();
        String ownerThreadName = getOwnerThreadName();
        
        return String.format(
            "DriverContext[\n" +
            "  sessionId=%s\n" +
            "  ownerThread=%s/%s\n" +
            "  currentThread=%d/%s\n" +
            "  isOwner=%s\n" +
            "  alive=%s\n" +
            "  age=%dms\n" +
            "]",
            sessionId,
            ownerThreadId != null ? ownerThreadId : "unregistered",
            ownerThreadName != null ? ownerThreadName : "N/A",
            Thread.currentThread().getId(), Thread.currentThread().getName(),
            isCurrentThreadOwner(),
            isDriverAlive(),
            getAgeMillis());
    }
    
    @Override
    public String toString() {
        return String.format("DriverContext[session=%s, alive=%s, isOwner=%s]", 
            sessionId, isDriverAlive(), isCurrentThreadOwner());
    }
    
    /**
     * Generate thread violation error message.
     * 
     * @return Formatted error message with thread details
     */
    private String getThreadViolationMessage() {
        Long ownerThreadId = getOwnerThreadId();
        String ownerThreadName = getOwnerThreadName();
        
        return String.format(
            "Thread violation: Session %s is owned by thread %s/%s but accessed from thread %d/%s",
            sessionId, 
            ownerThreadId != null ? ownerThreadId : "unknown",
            ownerThreadName != null ? ownerThreadName : "unknown",
            Thread.currentThread().getId(), 
            Thread.currentThread().getName());
    }
    
    // ========================================================================
    // STATIC HELPER METHODS - Convenience wrappers for SessionThreadManager
    // ========================================================================
    
    /**
     * Get owner thread ID for a session ID (static helper).
     * 
     * @param sessionId The session ID
     * @return The owner thread ID, or null if not registered
     */
    public static Long getOwnerThreadIdForSession(String sessionId) {
        if (sessionId == null) return null;
        return SessionThreadManager.getInstance().getOwnerThreadId(sessionId);
    }
    
    /**
     * Get owner thread name for a session ID (static helper).
     * 
     * @param sessionId The session ID
     * @return The owner thread name, or null if not registered
     */
    public static String getOwnerThreadNameForSession(String sessionId) {
        if (sessionId == null) return null;
        return SessionThreadManager.getInstance().getOwnerThreadName(sessionId);
    }
    
    /**
     * Validate current thread owns session (static helper).
     * 
     * @param sessionId The session ID
     * @return true if current thread owns the session
     */
    public static boolean validateCurrentThreadOwnsSession(String sessionId) {
        if (sessionId == null) return true;
        return SessionThreadManager.getInstance().isCurrentThreadOwner(sessionId);
    }
    
    /**
     * Get detailed session-thread mapping info (static helper).
     * 
     * @param sessionId The session ID
     * @return Formatted string with session-thread details
     */
    public static String getSessionThreadInfo(String sessionId) {
        if (sessionId == null) {
            return "No session ID provided";
        }
        
        SessionThreadManager manager = SessionThreadManager.getInstance();
        Long ownerThreadId = manager.getOwnerThreadId(sessionId);
        String ownerThreadName = manager.getOwnerThreadName(sessionId);
        long currentThreadId = Thread.currentThread().getId();
        String currentThreadName = Thread.currentThread().getName();
        
        if (ownerThreadId == null) {
            return String.format(
                "Session %s is not registered\nCurrent thread: %d/%s",
                sessionId, currentThreadId, currentThreadName);
        }
        
        return String.format(
            "Session: %s\nOwner: %d/%s\nCurrent: %d/%s\nIs Owner: %s",
            sessionId, ownerThreadId, ownerThreadName,
            currentThreadId, currentThreadName,
            manager.isCurrentThreadOwner(sessionId));
    }
}
