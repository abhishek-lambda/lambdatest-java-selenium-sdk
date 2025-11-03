package com.lambdatest.selenium.testng;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.logging.Logger;

import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.testng.IConfigurationListener;
import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.ITestListener;
import org.testng.ITestResult;

import com.lambdatest.selenium.agent.LambdaTestAgent;
import com.lambdatest.selenium.lambdatest.DriverContext;
import com.lambdatest.selenium.lambdatest.LambdaTestConfig;
import com.lambdatest.selenium.lambdatest.SessionThreadManager;
import com.lambdatest.selenium.tunnel.TunnelManager;

/**
 * Unified TestNG listener for LambdaTest SDK.
 * 
 * Handles both suite-level and test-level operations:
 * 
 * Suite-level (ISuiteListener):
 * - Starts ONE tunnel before suite execution (shared by all parallel threads)
 * - Stops tunnel after suite completes
 * - Force-quits any orphaned drivers (safety net for high parallelism)
 * 
 * Method-level (IInvokedMethodListener):
 * - Marks test status IMMEDIATELY after test method (BEFORE @AfterMethod)
 * - This ensures status is marked while driver is still active
 * 
 * Test-level (ITestListener):
 * - Tracks WebDriver instances per thread
 * - Automatically cleans up drivers after each test
 * 
 * TestNG Execution Order:
 * 1. @BeforeMethod
 * 2. @Test method
 * 3. IInvokedMethodListener.afterInvocation() ← Mark status HERE (driver still active)
 * 4. @AfterMethod ← User may quit driver here
 * 5. ITestListener.onTestSuccess() ← Cleanup HERE
 * 
 * Thread-safe for high parallelism (tested with 50+ parallel threads).
 * This listener is automatically registered by the Java agent.
 */
public class TestNgTestListener implements ITestListener, IInvokedMethodListener, ISuiteListener, IConfigurationListener {
    
    private static final Logger LOGGER = Logger.getLogger(TestNgTestListener.class.getName());
    
    // Store driver context with metadata
    // Primary: ThreadLocal for O(1) current-thread access
    private static final ThreadLocal<DriverContext> CURRENT_CONTEXT = new ThreadLocal<>();
    
    // Secondary: Global map of all driver contexts keyed by RemoteWebDriver instance
    // This allows lookups when we have the driver but not the context
    private static final Map<RemoteWebDriver, DriverContext> DRIVER_CONTEXTS = 
        new java.util.concurrent.ConcurrentHashMap<>();
    
    // Thread-to-driver mapping for parallel="methods" support
    // When TestNG uses parallel="methods", all test methods share the same instance
    // but run on different threads. This map tracks drivers for logging and monitoring.
    // Note: Actual thread-safe storage is handled by WebDriverFieldTransformer + ThreadLocalDriverStorage
    private static final Map<Long, WebDriver> THREAD_DRIVERS = new java.util.concurrent.ConcurrentHashMap<>();
    
    // Suite-level: Tunnel management
    private static TunnelManager tunnelManager;
    private static boolean tunnelStarted = false;
    private static final Object tunnelLock = new Object();
    
    // Session-thread management
    private static final SessionThreadManager sessionThreadManager = SessionThreadManager.getInstance();
    
    public TestNgTestListener() {
    }
    
    // ========================================================================
    // SUITE-LEVEL METHODS (ISuiteListener)
    // ========================================================================
    
    /**
     * Called before the suite starts.
     * Starts tunnel if enabled in configuration.
     */
    @Override
    public void onStart(ISuite suite) {
        synchronized (tunnelLock) {
            // Check if tunnel is enabled from multiple sources (priority order):
            // 1. System property: -DLT_TUNNEL=true
            // 2. Environment variable: export LT_TUNNEL=true
            // 3. YAML configuration: tunnel: true
            
            String tunnelEnabled = System.getProperty("LT_TUNNEL", System.getenv("LT_TUNNEL"));
            
            // If not set via system property or env var, check YAML
            if (tunnelEnabled == null) {
                try {
                    LambdaTestConfig config = LambdaTestConfig.getInstance();
                    MutableCapabilities caps = config.getCapabilitiesFromYaml();
                    Object ltOptions = caps.getCapability("lt:options");
                    if (ltOptions instanceof Map) {
                        Object tunnelValue = ((Map<?, ?>) ltOptions).get("tunnel");
                        if (tunnelValue != null) {
                            tunnelEnabled = tunnelValue.toString();
                        }
                    }
                } catch (Exception e) {
                    // Ignore - YAML might not exist or be configured
                }
            }
            
            if ("true".equalsIgnoreCase(tunnelEnabled)) {
                LOGGER.info("Suite listener triggered - starting tunnel...");
                startSharedTunnel();
            }
        }
    }
    
    /**
     * Called after the suite finishes.
     * Stops tunnel if it was started and ensures ALL drivers are quit.
     */
    @Override
    public void onFinish(ISuite suite) {
        synchronized (tunnelLock) {
            // Force cleanup of any orphaned drivers (important for thread pool scenarios)
            LOGGER.info("Suite finishing - performing final driver cleanup...");
            LambdaTestAgent.quitAllDriversGlobally();
            
            // Log session-thread bindings for debugging
            if (sessionThreadManager.getActiveBindingsCount() > 0) {
                LOGGER.info("Active session-thread bindings before cleanup:\n" + 
                    sessionThreadManager.getAllBindings());
            }
            
            // Clear driver context map to prevent memory leaks
            DRIVER_CONTEXTS.clear();
            LOGGER.fine("Cleared driver context map");
            
            // Clear thread-specific driver mappings (for parallel="methods" support)
            THREAD_DRIVERS.clear();
            LOGGER.fine("Cleared thread driver mappings");
            
            // Clear session-thread mappings
            sessionThreadManager.clearAll();
            LOGGER.fine("Cleared session-thread mappings");
            
            // Stop tunnel
            if (tunnelStarted && tunnelManager != null) {
                stopSharedTunnel();
            }
        }
    }
    
    /**
     * Start shared tunnel for all parallel tests.
     * This method is synchronized to prevent multiple tunnel instances.
     */
    private void startSharedTunnel() {
        try {
            // Get tunnel manager singleton (thread-safe)
            tunnelManager = TunnelManager.getInstance();
            
            // Check if tunnel is already running
            if (tunnelManager.isTunnelRunning()) {
                tunnelStarted = true;
                LOGGER.info("LambdaTest Tunnel already running: " + tunnelManager.getTunnelName());
                return;
            }
            
            // Get LambdaTest configuration
            LambdaTestConfig config = LambdaTestConfig.getInstance();
            String username = config.getUsername();
            String accessKey = config.getAccessKey();
            
            // Get tunnel name (user-provided or auto-generated)
            String tunnelName = System.getProperty("LT_TUNNEL_NAME", System.getenv("LT_TUNNEL_NAME"));
            if (tunnelName == null || tunnelName.trim().isEmpty()) {
                tunnelName = "lt-sdk-parallel-" + System.currentTimeMillis();
            }
            
            // Start tunnel (this will block until tunnel is fully connected)
            tunnelManager.startTunnel(username, accessKey, tunnelName);
            
            // Verify tunnel is running
            if (!tunnelManager.isTunnelRunning()) {
                LOGGER.warning("Warning: Tunnel did not start properly. Check logs: ~/.lambdatest-tunnel/tunnel.log");
                return;
            }
            
            tunnelStarted = true;
            LOGGER.info("LambdaTest Tunnel started and ready: " + tunnelManager.getTunnelName());
            
        } catch (Exception e) {
            LOGGER.severe("Failed to start tunnel: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Stop shared tunnel after all tests complete.
     */
    private void stopSharedTunnel() {
        try {
            tunnelManager.stopTunnel();
            tunnelStarted = false;
            LOGGER.info("LambdaTest Tunnel stopped");
        } catch (Exception e) {
            LOGGER.warning("Error stopping tunnel: " + e.getMessage());
        }
    }
    
    /**
     * Check if tunnel is available for tests.
     * Tests can call this to verify tunnel is ready.
     */
    public static boolean isTunnelAvailable() {
        return tunnelStarted && tunnelManager != null && tunnelManager.isTunnelRunning();
    }
    
    /**
     * Get the tunnel name being used.
     * Tests can use this to verify which tunnel they're connected to.
     */
    public static String getTunnelName() {
        if (tunnelManager != null) {
            return tunnelManager.getTunnelName();
        }
        return null;
    }
    
    // ========================================================================
    // METHOD-LEVEL METHODS (IInvokedMethodListener)
    // Runs BEFORE @AfterMethod - perfect for marking status while driver is active
    // ========================================================================
    
    @Override
    public void beforeInvocation(IInvokedMethod method, ITestResult testResult) {
        // Handle @BeforeMethod detection (for parallel="classes" support)
        if (method.isConfigurationMethod()) {
            try {
                // Check if this is a @BeforeMethod
                boolean isBeforeMethod = method.getTestMethod().getConstructorOrMethod()
                    .getMethod().isAnnotationPresent(org.testng.annotations.BeforeMethod.class);
                
                if (isBeforeMethod) {
                    // Don't store yet - driver might not be created
                    // We'll capture it in afterInvocation instead
                    LOGGER.fine("@BeforeMethod detected: " + method.getTestMethod().getMethodName());
                }
            } catch (Exception e) {
                // Ignore - annotation might not be accessible
            }
        }
        
        // NOTE: Manual driver injection is NO LONGER NEEDED!
        // Field access is now intercepted automatically by WebDriverFieldTransformer
        // which redirects all field reads/writes to ThreadLocal storage.
        // This makes parallel="methods" work transparently without any manual injection.
        
        // The code below is kept for backward compatibility with older SDK versions
        // but is effectively a no-op when field interception is active.
    }
    
    @Override
    public void afterInvocation(IInvokedMethod method, ITestResult testResult) {
        // Store driver context after @BeforeMethod completes
        if (method.isConfigurationMethod()) {
            try {
                boolean isBeforeMethod = method.getTestMethod().getConstructorOrMethod()
                    .getMethod().isAnnotationPresent(org.testng.annotations.BeforeMethod.class);
                
                if (isBeforeMethod) {
                    Object testInstance = testResult.getInstance();
                    if (testInstance != null) {
                        WebDriver driver = findDriverInInstance(testInstance);
                        if (driver instanceof RemoteWebDriver) {
                            RemoteWebDriver remoteDriver = (RemoteWebDriver) driver;
                            
                            // Store driver in thread map for parallel="methods" support
                            long currentThreadId = Thread.currentThread().getId();
                            THREAD_DRIVERS.put(currentThreadId, remoteDriver);
                            
                            // LOGGER.info(String.format(
                            //     "[Thread-%d/%s] Captured driver (Session: %s) - Total drivers in map: %d",
                            //     currentThreadId, Thread.currentThread().getName(),
                            //     remoteDriver.getSessionId().toString().substring(0, 8) + "...",
                            //     THREAD_DRIVERS.size()));
                            
                            // Create driver context with thread and session metadata
                            // The DriverContext constructor automatically registers the session with SessionThreadManager
                            DriverContext context = new DriverContext(remoteDriver);
                            
                            // Store in both ThreadLocal (fast) and global map (lookup)
                            CURRENT_CONTEXT.set(context);
                            DRIVER_CONTEXTS.put(remoteDriver, context);
                            
                            LOGGER.info(String.format(
                                "[Thread-%d/%s] Stored driver context for session %s in CURRENT_CONTEXT ThreadLocal",
                                Thread.currentThread().getId(),
                                Thread.currentThread().getName(),
                                remoteDriver.getSessionId().toString().substring(0, 8) + "..."));
                            
                            // Log session-thread binding
                            LOGGER.fine(String.format("Session %s bound to thread %d/%s",
                                context.getSessionId(), 
                                context.getOwnerThreadId(),
                                context.getOwnerThreadName()));
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.fine("Could not capture driver in afterInvocation: " + e.getMessage());
            }
        }
        
        // Only process @Test methods (not @BeforeMethod or @AfterMethod)
        if (method.isTestMethod()) {
            // Mark test status BEFORE @AfterMethod runs
            // This ensures driver is still active when we mark the status
            
            // Debug: Check if context is available
            DriverContext debugContext = CURRENT_CONTEXT.get();
            LOGGER.info(String.format(
                "[Thread-%d/%s] About to mark status for test %s - Context available: %s, Session: %s",
                Thread.currentThread().getId(),
                Thread.currentThread().getName(),
                testResult.getMethod().getMethodName(),
                debugContext != null ? "YES" : "NO",
                debugContext != null ? debugContext.getSessionId().substring(0, 8) + "..." : "N/A"));
            
            if (testResult.isSuccess()) {
                markTestStatus("passed", testResult);
            } else if (testResult.getStatus() == ITestResult.FAILURE) {
                markTestStatus("failed", testResult);
            } else if (testResult.getStatus() == ITestResult.SKIP) {
                markTestStatus("skipped", testResult);
            }
        }
    }
    
    // ========================================================================
    // TEST-LEVEL METHODS (ITestListener)
    // Runs AFTER @AfterMethod - used for cleanup only
    // ========================================================================
    
    @Override
    public void onTestStart(ITestResult result) {
        // Ensure driver context is set for this test
        try {
            // Check if context already exists in ThreadLocal (set by afterInvocation)
            DriverContext context = CURRENT_CONTEXT.get();
            
            // If not in ThreadLocal, try to recover it from global map
            if (context == null) {
                Object testInstance = result.getInstance();
                if (testInstance != null) {
                    WebDriver driver = findDriverInInstance(testInstance);
                    if (driver instanceof RemoteWebDriver) {
                        RemoteWebDriver remoteDriver = (RemoteWebDriver) driver;
                        
                        // Check if context already exists in global map
                        context = DRIVER_CONTEXTS.get(remoteDriver);
                        
                        if (context == null) {
                            // Context doesn't exist - this means driver was created outside @BeforeMethod
                            // or afterInvocation didn't capture it. Create new context.
                            // Comment out - this is expected if driver created outside @BeforeMethod
                            // LOGGER.warning(String.format(
                            //     "Driver context not found for test %s - creating new context. " +
                            //     "This may indicate driver was not created in @BeforeMethod.",
                            //     result.getMethod().getMethodName()));
                            
                            context = new DriverContext(remoteDriver);
                            DRIVER_CONTEXTS.put(remoteDriver, context);
                        }
                        
                            // CRITICAL: Validate thread ownership BEFORE setting in ThreadLocal
                        if (!context.isCurrentThreadOwner()) {
                            LOGGER.severe(String.format(
                                "THREAD VIOLATION in onTestStart - REFUSING to use driver!\n" +
                                "  Test: %s\n" +
                                "  Expected Thread: %d/%s\n" +
                                "  Current Thread: %d/%s\n" +
                                "  Session: %s\n" +
                                "  This driver belongs to a different thread and CANNOT be used here!",
                                result.getMethod().getMethodName(),
                                context.getOwnerThreadId(), context.getOwnerThreadName(),
                                Thread.currentThread().getId(), Thread.currentThread().getName(),
                                context.getSessionId()));
                            
                            // DO NOT set context in ThreadLocal - prevent wrong thread from using it
                            context = null;
                        } else {
                            // Thread ownership validated - safe to set in ThreadLocal
                            CURRENT_CONTEXT.set(context);
                            
                            // LOGGER.fine(String.format(
                            //     "Driver context restored to ThreadLocal for test: %s (Thread: %d/%s, Session: %s)",
                            //     result.getMethod().getMethodName(),
                            //     Thread.currentThread().getId(),
                            //     Thread.currentThread().getName(),
                            //     context.getSessionId()));
                        }
                    }
                }
            }
            
            // Comment out - this is called BEFORE @BeforeMethod, so no context is expected yet
            // if (context != null) {
            //     LOGGER.fine("Driver context for test: " + result.getMethod().getMethodName() + 
            //                " - " + context.toString());
            // } else {
            //     LOGGER.warning(String.format(
            //         "No driver context available for test: %s (Thread: %d/%s)",
            //         result.getMethod().getMethodName(),
            //         Thread.currentThread().getId(),
            //         Thread.currentThread().getName()));
            // }
        } catch (Exception e) {
            LOGGER.fine("Could not find driver in onTestStart: " + e.getMessage());
        }
    }
    
    @Override
    public void onTestSuccess(ITestResult result) {
        // Status already marked in afterInvocation() (BEFORE @AfterMethod)
        // @AfterMethod has already run at this point, so driver.quit() was already called by user
        // Just cleanup our internal tracking structures
        cleanupAfterTest();
    }
    
    @Override
    public void onTestFailure(ITestResult result) {
        // Status already marked in afterInvocation() (BEFORE @AfterMethod)
        // @AfterMethod has already run at this point, so driver.quit() was already called by user
        // Just cleanup our internal tracking structures
        cleanupAfterTest();
    }
    
    @Override
    public void onTestSkipped(ITestResult result) {
        // Status already marked in afterInvocation() (BEFORE @AfterMethod)
        // @AfterMethod has already run at this point (if defined)
        // Just cleanup our internal tracking structures
        cleanupAfterTest();
    }

    // Remove invalid @Override (there is no onTestComplete in ITestListener)
    /**
     * Runs after @AfterMethod (if defined). Triggers SDK cleanup after a test if needed.
     * Not called by TestNG framework directly—SDK-internal usage only.
     */
    public void onFinish(ITestResult result) {
        cleanupAfterTest();
    }       
        
    /**
     * Clean up after test completes.
     * CRITICAL: Prevents memory leaks in thread pools.
     * 
     * This is called from onTestSuccess/Failure/Skipped, which run AFTER @AfterMethod.
     * User's @AfterMethod has already called driver.quit() by this point.
     * We just clean up our internal tracking structures.
     */
    private void cleanupAfterTest() {
        try {
            // Get current context before clearing
            DriverContext context = CURRENT_CONTEXT.get();
            
            // Mark context as driver no longer alive (user already quit it in @AfterMethod)
            if (context != null) {
                context.setDriverAlive(false);
                // Remove from global map (driver is quit, no need to track anymore)
                DRIVER_CONTEXTS.remove(context.getDriver());
            }
            
            // Clean up thread-specific driver mapping (for parallel="methods")
            long currentThreadId = Thread.currentThread().getId();
            THREAD_DRIVERS.remove(currentThreadId);
            
            // Clean up ThreadLocal to prevent memory leaks in thread pools
            CURRENT_CONTEXT.remove();
            
            // NOTE: We do NOT call quit() here because:
            // 1. User's @AfterMethod already called driver.quit()
            // 2. Calling quit() here would clear ThreadLocal BEFORE @AfterMethod runs
            // 3. That would cause NullPointerException in user's @AfterMethod
            // 4. Any orphaned drivers are cleaned up in onFinish(ISuite)
            
        } catch (Exception e) {
            LOGGER.fine("Error during test cleanup: " + e.getMessage());
        }
    }
    
    /**
     * Mark test status on LambdaTest dashboard.
     * Thread-safe for parallel execution using DriverContext.
     */
    private void markTestStatus(String status, ITestResult result) {
        try {
            // Get driver context from ThreadLocal (O(1) lookup)
            DriverContext context = CURRENT_CONTEXT.get();
            RemoteWebDriver driver = null;
            
            if (context != null && context.isDriverAlive()) {
                // Validate thread ownership before using driver
                if (!context.validateThreadAccess()) {
                    LOGGER.warning(String.format(
                        "Skipping status mark due to thread violation for test: %s",
                        result.getMethod().getMethodName()));
                    return;
                }
                driver = context.getDriver();
            } else {
                // Fallback: Try to find driver via reflection and get its context
                Object testInstance = result.getInstance();
                if (testInstance != null) {
                    WebDriver foundDriver = findDriverInInstance(testInstance);
                    if (foundDriver instanceof RemoteWebDriver) {
                        RemoteWebDriver remoteDriver = (RemoteWebDriver) foundDriver;
                        context = DRIVER_CONTEXTS.get(remoteDriver);
                        if (context != null && context.isDriverAlive()) {
                            // Validate thread ownership
                            if (context.validateThreadAccess()) {
                                driver = remoteDriver;
                            } else {
                                LOGGER.warning("Skipping status mark - thread violation in fallback lookup");
                                return;
                            }
                        }
                    }
                }
            }
            
            if (driver != null) {
                // Verify session-thread binding before executing script
                String sessionId = context != null ? context.getSessionId() : null;
                if (sessionId != null && !sessionThreadManager.isCurrentThreadOwner(sessionId)) {
                    LOGGER.warning(String.format(
                        "Session %s not owned by current thread %d/%s, skipping status mark",
                        sessionId, Thread.currentThread().getId(), Thread.currentThread().getName()));
                    return;
                }
                
                // LambdaTest uses JavaScript to mark test status
                String script = String.format("lambda-status=%s", status);
                driver.executeScript(script);
                
                LOGGER.info("Marked test as " + status + " on LambdaTest dashboard " +
                           "(Test: " + result.getMethod().getMethodName() + 
                           ", Thread: " + Thread.currentThread().getId() + "/" + 
                           Thread.currentThread().getName() +
                           ", Session: " + (sessionId != null ? sessionId.substring(0, 8) + "..." : "unknown") + ")");
            } else {
                LOGGER.warning("No active driver found to mark test status for test: " + 
                    result.getMethod().getMethodName() + " (status: " + status + ")");
            }
        } catch (Exception e) {
            // Don't throw - test status is best-effort
            LOGGER.warning("Could not mark test status for test " + 
                result.getMethod().getMethodName() + ": " + e.getMessage());
        }
    }
    
    /**
     * Find ANY WebDriver field in the test instance.
     * This is more robust than getDriverField - works with any field name.
     * Critical for parallel="classes" support.
     * 
     * Note: With field interception active, we need to use ThreadLocalDriverStorage
     * instead of direct field access.
     */
    private WebDriver findDriverInInstance(Object testInstance) {
        if (testInstance == null) {
            return null;
        }
        
        // First try ThreadLocalDriverStorage (for field-intercepted drivers)
        try {
            Class<?> storageClass = Class.forName("com.lambdatest.selenium.agent.ThreadLocalDriverStorage");
            
            // Try to get driver for each WebDriver field in the instance
            Class<?> clazz = testInstance.getClass();
            while (clazz != null && !clazz.equals(Object.class)) {
                for (Field field : clazz.getDeclaredFields()) {
                    if (WebDriver.class.isAssignableFrom(field.getType())) {
                        // Build the field key: className.fieldName
                        String fieldKey = clazz.getName() + "." + field.getName();
                        
                        try {
                            java.lang.reflect.Method getDriverMethod = storageClass.getMethod("getDriver", String.class);
                            Object driver = getDriverMethod.invoke(null, fieldKey);
                            
                            if (driver instanceof WebDriver) {
                                LOGGER.fine("Found WebDriver via ThreadLocalDriverStorage: " + field.getName() + 
                                           " in class: " + clazz.getSimpleName());
                                return (WebDriver) driver;
                            }
                        } catch (Exception e) {
                            // Continue to next field
                        }
                    }
                }
                clazz = clazz.getSuperclass();
            }
        } catch (ClassNotFoundException e) {
            // ThreadLocalDriverStorage not available, fall back to direct field access
        }
        
        // Fallback: Try direct field access (for non-intercepted fields)
        Class<?> clazz = testInstance.getClass();
        while (clazz != null && !clazz.equals(Object.class)) {
            for (Field field : clazz.getDeclaredFields()) {
                try {
                    if (WebDriver.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        Object value = field.get(testInstance);
                        
                        if (value instanceof WebDriver) {
                            LOGGER.fine("Found WebDriver field (direct access): " + field.getName() + 
                                       " in class: " + clazz.getSimpleName());
                            return (WebDriver) value;
                        }
                    }
                } catch (Exception e) {
                    // Skip inaccessible fields
                    continue;
                }
            }
            clazz = clazz.getSuperclass();
        }
        
        LOGGER.fine("No WebDriver field found in test instance: " + 
                   testInstance.getClass().getSimpleName());
        return null;
    }
    
    // REMOVED: All capability enhancement methods
    // Capability enhancement now happens ONLY in RemoteWebDriverAdvice
    // This prevents duplicate enhancement and works reliably with high parallelism
}

