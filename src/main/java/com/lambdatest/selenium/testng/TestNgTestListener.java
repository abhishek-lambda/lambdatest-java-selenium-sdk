package com.lambdatest.selenium.testng;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.logging.Logger;

import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.ITestListener;
import org.testng.ITestResult;

import com.lambdatest.selenium.agent.LambdaTestAgent;
import com.lambdatest.selenium.lambdatest.LambdaTestConfig;
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
public class TestNgTestListener implements ITestListener, IInvokedMethodListener, ISuiteListener {
    
    private static final Logger LOGGER = Logger.getLogger(TestNgTestListener.class.getName());
    
    // Test-level: Thread-safe storage for each test's driver
    // Using ThreadLocal for primary storage (fast access per thread)
    private static final ThreadLocal<WebDriver> CURRENT_DRIVER = new ThreadLocal<>();
    
    // Test-instance-level: Map to track drivers per test instance
    // This is critical for parallel="classes" where multiple test instances run in parallel
    private static final Map<Object, WebDriver> INSTANCE_DRIVERS = 
        new java.util.concurrent.ConcurrentHashMap<>();
    
    // Suite-level: Tunnel management
    private static TunnelManager tunnelManager;
    private static boolean tunnelStarted = false;
    private static final Object tunnelLock = new Object();
    
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
            
            // Clear instance driver map to prevent memory leaks
            INSTANCE_DRIVERS.clear();
            LOGGER.fine("Cleared instance driver map");
            
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
            
            // Start tunnel
            tunnelManager.startTunnel(username, accessKey, tunnelName);
            
            // Wait for tunnel to be ready
            int maxWaitSeconds = 30;
            boolean ready = false;
            
            for (int i = 0; i < maxWaitSeconds; i++) {
                if (tunnelManager.isTunnelRunning()) {
                    ready = true;
                    break;
                }
                Thread.sleep(1000);
            }
            
            if (!ready) {
                LOGGER.warning("Warning: Tunnel did not start within " + maxWaitSeconds + " seconds. Check logs: ~/.lambdatest-tunnel/tunnel.log");
                return;
            }
            
            // Give extra time for tunnel to register with LambdaTest grid
            Thread.sleep(5000);
            
            tunnelStarted = true;
            LOGGER.info("✓ LambdaTest Tunnel started: " + tunnelManager.getTunnelName());
            
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
            LOGGER.info("✓ LambdaTest Tunnel stopped");
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
        // Store driver when @BeforeMethod runs (not just methods with "setUp" in name)
        // This is critical for parallel="classes" support
        if (method.isConfigurationMethod()) {
            try {
                // Check if this is a @BeforeMethod
                boolean isBeforeMethod = method.getTestMethod().getConstructorOrMethod()
                    .getMethod().isAnnotationPresent(org.testng.annotations.BeforeMethod.class);
                
                if (isBeforeMethod) {
                    // Don't store yet - driver might not be created
                    // We'll capture it in onTestStart instead
                    LOGGER.fine("@BeforeMethod detected: " + method.getTestMethod().getMethodName());
                }
            } catch (Exception e) {
                // Ignore - annotation might not be accessible
            }
        }
    }
    
    @Override
    public void afterInvocation(IInvokedMethod method, ITestResult testResult) {
        // Store driver after @BeforeMethod completes
        if (method.isConfigurationMethod()) {
            try {
                boolean isBeforeMethod = method.getTestMethod().getConstructorOrMethod()
                    .getMethod().isAnnotationPresent(org.testng.annotations.BeforeMethod.class);
                
                if (isBeforeMethod) {
                    Object testInstance = testResult.getInstance();
                    if (testInstance != null) {
                        WebDriver driver = findDriverInInstance(testInstance);
                        if (driver != null) {
                            // Store in both ThreadLocal and instance map
                            CURRENT_DRIVER.set(driver);
                            INSTANCE_DRIVERS.put(testInstance, driver);
                            LOGGER.fine("Driver captured for test instance: " + testInstance.getClass().getSimpleName());
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
        // Store the driver for this test (if it exists)
        // Try multiple approaches to find the driver
        try {
            Object testInstance = result.getInstance();
            if (testInstance != null) {
                // First, check if we already have a driver for this instance
                WebDriver driver = INSTANCE_DRIVERS.get(testInstance);
                
                // If not, try to find it via reflection
                if (driver == null) {
                    driver = findDriverInInstance(testInstance);
                    if (driver != null) {
                        INSTANCE_DRIVERS.put(testInstance, driver);
                    }
                }
                
                // Store in ThreadLocal for fast access
                if (driver != null) {
                    CURRENT_DRIVER.set(driver);
                    LOGGER.fine("Driver found for test: " + result.getMethod().getMethodName() + 
                               " (Class: " + testInstance.getClass().getSimpleName() + ")");
                }
            }
        } catch (Exception e) {
            LOGGER.fine("Could not find driver in onTestStart: " + e.getMessage());
        }
    }
    
    @Override
    public void onTestSuccess(ITestResult result) {
        // Status already marked in afterInvocation()
        // Just cleanup here
        cleanupAfterTest();
    }
    
    @Override
    public void onTestFailure(ITestResult result) {
        // Status already marked in afterInvocation()
        // Just cleanup here
        cleanupAfterTest();
    }
    
    @Override
    public void onTestSkipped(ITestResult result) {
        // Status already marked in afterInvocation()
        // Just cleanup here
        cleanupAfterTest();
    }
    
    /**
     * Clean up after test completes.
     * CRITICAL: Prevents memory leaks in thread pools.
     */
    private void cleanupAfterTest() {
        try {
            // Clean up ThreadLocal to prevent memory leaks
            CURRENT_DRIVER.remove();
            
            // Quit all drivers for this thread (automatically registered by agent)
            LambdaTestAgent.quitAllDrivers();
            
            // Note: We don't remove from INSTANCE_DRIVERS here because
            // the instance might be reused for other tests in the same class
            // The driver is already quit by LambdaTestAgent.quitAllDrivers()
        } catch (Exception e) {
            LOGGER.warning("Error during cleanup: " + e.getMessage());
        }
    }
    
    /**
     * Mark test status on LambdaTest dashboard.
     * This is thread-safe and works for both parallel methods and parallel classes.
     */
    private void markTestStatus(String status, ITestResult result) {
        try {
            // Try ThreadLocal first (fastest)
            WebDriver driver = CURRENT_DRIVER.get();
            
            // If not in ThreadLocal, try instance map (important for parallel="classes")
            if (driver == null) {
                Object testInstance = result.getInstance();
                if (testInstance != null) {
                    driver = INSTANCE_DRIVERS.get(testInstance);
                }
            }
            
            // If still no driver, try reflection as last resort
            if (driver == null) {
                Object testInstance = result.getInstance();
                if (testInstance != null) {
                    driver = findDriverInInstance(testInstance);
                }
            }
            
            if (driver instanceof RemoteWebDriver) {
                RemoteWebDriver remoteDriver = (RemoteWebDriver) driver;
                
                // LambdaTest uses JavaScript to mark test status
                String script = String.format("lambda-status=%s", status);
                remoteDriver.executeScript(script);
                
                LOGGER.fine("Marked test as " + status + " on LambdaTest dashboard " +
                           "(Test: " + result.getMethod().getMethodName() + ")");
            } else if (driver != null) {
                LOGGER.fine("Driver found but not a RemoteWebDriver, cannot mark status");
            } else {
                LOGGER.fine("No driver found to mark test status");
            }
        } catch (Exception e) {
            // Don't throw - test status is best-effort
            LOGGER.fine("Could not mark test status: " + e.getMessage());
        }
    }
    
    /**
     * Find ANY WebDriver field in the test instance.
     * This is more robust than getDriverField - works with any field name.
     * Critical for parallel="classes" support.
     */
    private WebDriver findDriverInInstance(Object testInstance) {
        if (testInstance == null) {
            return null;
        }
        
        Class<?> clazz = testInstance.getClass();
        
        // Search through class hierarchy (includes parent classes)
        while (clazz != null && !clazz.equals(Object.class)) {
            // Try all declared fields
            for (Field field : clazz.getDeclaredFields()) {
                try {
                    // Check if field is a WebDriver or subclass
                    if (WebDriver.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        Object value = field.get(testInstance);
                        
                        if (value instanceof WebDriver) {
                            LOGGER.fine("Found WebDriver field: " + field.getName() + 
                                       " in class: " + clazz.getSimpleName());
                            return (WebDriver) value;
                        }
                    }
                } catch (Exception e) {
                    // Skip inaccessible fields
                    continue;
                }
            }
            
            // Move to parent class
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

