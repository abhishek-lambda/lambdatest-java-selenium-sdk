package com.lambdatest.selenium.lambdatest;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages thread-to-session ID mapping to ensure thread affinity.
 * 
 * This class ensures that:
 * 1. Each session ID is always handled by the same thread
 * 2. All commands for a session go to that dedicated thread
 * 3. Thread violations are detected and logged
 * 
 * Thread-safe for high parallelism.
 */
public class SessionThreadManager {
    
    private static final Logger LOGGER = Logger.getLogger(SessionThreadManager.class.getName());
    
    // Singleton instance
    private static final SessionThreadManager INSTANCE = new SessionThreadManager();
    
    // Session ID -> Thread ID mapping
    private final Map<String, Long> sessionToThread = new ConcurrentHashMap<>();
    
    // Thread ID -> Session ID mapping (for reverse lookup)
    private final Map<Long, String> threadToSession = new ConcurrentHashMap<>();
    
    // Session ID -> Thread name (for debugging)
    private final Map<String, String> sessionToThreadName = new ConcurrentHashMap<>();
    
    private SessionThreadManager() {
        // Private constructor for singleton
    }
    
    /**
     * Get singleton instance.
     */
    public static SessionThreadManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * Register a session ID to the current thread.
     * This creates the binding between the session and thread.
     * 
     * @param sessionId The session ID to bind
     * @return true if registration successful, false if session already bound to different thread
     */
    public boolean registerSession(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            LOGGER.warning("Cannot register null or empty session ID");
            return false;
        }
        
        long currentThreadId = Thread.currentThread().getId();
        String currentThreadName = Thread.currentThread().getName();
        
        // Check if session is already registered
        Long existingThreadId = sessionToThread.get(sessionId);
        
        if (existingThreadId != null) {
            if (existingThreadId == currentThreadId) {
                // Already registered to this thread - OK
                LOGGER.fine(String.format("Session %s already registered to thread %d/%s", 
                    sessionId, currentThreadId, currentThreadName));
                return true;
            } else {
                // Registered to different thread - WARNING
                String existingThreadName = sessionToThreadName.get(sessionId);
                LOGGER.warning(String.format(
                    "THREAD VIOLATION: Session %s already bound to thread %d/%s, " +
                    "attempted access from thread %d/%s",
                    sessionId, existingThreadId, existingThreadName, 
                    currentThreadId, currentThreadName));
                return false;
            }
        }
        
        // Register the mapping
        sessionToThread.put(sessionId, currentThreadId);
        threadToSession.put(currentThreadId, sessionId);
        sessionToThreadName.put(sessionId, currentThreadName);
        
        LOGGER.info(String.format("Registered session %s to thread %d/%s", 
            sessionId, currentThreadId, currentThreadName));
        
        return true;
    }
    
    /**
     * Check if the current thread is the owner of the given session.
     * 
     * @param sessionId The session ID to check
     * @return true if current thread owns the session, false otherwise
     */
    public boolean isCurrentThreadOwner(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return false;
        }
        
        Long ownerThreadId = sessionToThread.get(sessionId);
        if (ownerThreadId == null) {
            // Session not registered yet
            return true; // Allow unregistered sessions
        }
        
        return ownerThreadId == Thread.currentThread().getId();
    }
    
    /**
     * Validate that the current thread owns the given session.
     * Logs a warning if validation fails.
     * 
     * @param sessionId The session ID to validate
     * @return true if valid, false if thread violation detected
     */
    public boolean validateThreadOwnership(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return true; // Can't validate null session
        }
        
        Long ownerThreadId = sessionToThread.get(sessionId);
        if (ownerThreadId == null) {
            // Session not registered - this is OK for first access
            return true;
        }
        
        long currentThreadId = Thread.currentThread().getId();
        if (ownerThreadId != currentThreadId) {
            String ownerThreadName = sessionToThreadName.get(sessionId);
            String currentThreadName = Thread.currentThread().getName();
            
            LOGGER.warning(String.format(
                "⚠️ THREAD VIOLATION DETECTED:\n" +
                "  Session ID: %s\n" +
                "  Owner Thread: %d/%s\n" +
                "  Current Thread: %d/%s\n" +
                "  This may cause race conditions and test failures!",
                sessionId, ownerThreadId, ownerThreadName, 
                currentThreadId, currentThreadName));
            
            return false;
        }
        
        return true;
    }
    
    /**
     * Get the thread ID that owns the given session.
     * 
     * @param sessionId The session ID
     * @return The owner thread ID, or null if not registered
     */
    public Long getOwnerThreadId(String sessionId) {
        return sessionToThread.get(sessionId);
    }
    
    /**
     * Get the thread name that owns the given session.
     * 
     * @param sessionId The session ID
     * @return The owner thread name, or null if not registered
     */
    public String getOwnerThreadName(String sessionId) {
        return sessionToThreadName.get(sessionId);
    }
    
    /**
     * Get the session ID for the current thread.
     * 
     * @return The session ID, or null if no session bound to current thread
     */
    public String getCurrentThreadSession() {
        return threadToSession.get(Thread.currentThread().getId());
    }
    
    /**
     * Unregister a session (called when driver is quit).
     * 
     * @param sessionId The session ID to unregister
     */
    public void unregisterSession(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return;
        }
        
        Long threadId = sessionToThread.remove(sessionId);
        if (threadId != null) {
            threadToSession.remove(threadId);
            sessionToThreadName.remove(sessionId);
            
            LOGGER.fine(String.format("Unregistered session %s from thread %d", 
                sessionId, threadId));
        }
    }
    
    /**
     * Clear all mappings (for testing or emergency cleanup).
     */
    public void clearAll() {
        sessionToThread.clear();
        threadToSession.clear();
        sessionToThreadName.clear();
        LOGGER.info("Cleared all session-thread mappings");
    }
    
    /**
     * Get the current number of active session-thread bindings.
     * 
     * @return Number of active bindings
     */
    public int getActiveBindingsCount() {
        return sessionToThread.size();
    }
    
    /**
     * Get a formatted string showing all current session-thread bindings.
     * Useful for debugging.
     * 
     * @return Formatted string with all bindings
     */
    public String getAllBindings() {
        StringBuilder sb = new StringBuilder();
        sb.append("Session-Thread Bindings:\n");
        
        for (Map.Entry<String, Long> entry : sessionToThread.entrySet()) {
            String sessionId = entry.getKey();
            Long threadId = entry.getValue();
            String threadName = sessionToThreadName.get(sessionId);
            
            sb.append(String.format("  Session %s -> Thread %d/%s\n", 
                sessionId, threadId, threadName));
        }
        
        if (sessionToThread.isEmpty()) {
            sb.append("  (No active bindings)\n");
        }
        
        return sb.toString();
    }
}

