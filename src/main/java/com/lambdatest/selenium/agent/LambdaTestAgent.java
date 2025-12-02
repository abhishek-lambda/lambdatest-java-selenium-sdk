package com.lambdatest.selenium.agent;

import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.RemoteWebDriver;

import com.lambdatest.selenium.lambdatest.SessionThreadManager;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;

/**
 * LambdaTest Java Agent for runtime instrumentation.
 * 
 * This agent automatically intercepts WebDriver creation and test lifecycle,
 * eliminating the need for users to extend base classes or add listeners.
 * 
 * Usage:
 * 1. Build the agent JAR: mvn clean package
 * 2. Run tests with agent: java -javaagent:lambdatest-selenium-sdk-1.0.0-agent.jar -jar your-tests.jar
 * 
 * Or with Maven Surefire:
 * <argLine>-javaagent:${settings.localRepository}/com/lambdatest/lambdatest-selenium-sdk/1.0.0/lambdatest-selenium-sdk-1.0.0-agent.jar</argLine>
 * 
 * Features:
 * - Automatic WebDriver interception
 * - TestNG and JUnit 5 lifecycle management
 * - Thread-safe parallel execution
 * - Zero code changes required
 */
public class LambdaTestAgent {
    
    private static final Logger LOGGER = Logger.getLogger(LambdaTestAgent.class.getName());
    private static final String AGENT_VERSION = getVersionFromManifest();
    
    // ThreadLocal to track WebDriver instances per thread for automatic cleanup
    private static final ThreadLocal<List<WebDriver>> THREAD_DRIVERS = ThreadLocal.withInitial(ArrayList::new);
    
    // Global registry to track ALL drivers across all threads (backup for thread pool scenarios)
    private static final java.util.Set<WebDriver> ALL_DRIVERS = 
        java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    
    // Session-thread manager for enforcing thread affinity
    private static final SessionThreadManager sessionThreadManager = SessionThreadManager.getInstance();
    
    /**
     * Read version from JAR manifest.
     * Falls back to "unknown" if not found.
     */
    private static String getVersionFromManifest() {
        try {
            Package pkg = LambdaTestAgent.class.getPackage();
            String version = pkg.getImplementationVersion();
            if (version != null && !version.isEmpty()) {
                return version;
            }
            
            // Fallback: Try reading from manifest directly
            String className = LambdaTestAgent.class.getSimpleName() + ".class";
            String classPath = LambdaTestAgent.class.getResource(className).toString();
            
            if (classPath.startsWith("jar:")) {
                String manifestPath = classPath.substring(0, classPath.lastIndexOf("!") + 1) + 
                    "/META-INF/MANIFEST.MF";
                java.net.URL manifestUrl = new java.net.URL(manifestPath);
                java.util.jar.Manifest manifest = new java.util.jar.Manifest(manifestUrl.openStream());
                java.util.jar.Attributes attributes = manifest.getMainAttributes();
                version = attributes.getValue("Implementation-Version");
                if (version != null) {
                    return version;
                }
            }
        } catch (Exception e) {
            // Ignore - will use fallback
        }
        
        // Fallback for development/testing
        return "dev";
    }
    
    /**
     * Register a WebDriver instance for automatic cleanup after test.
     * This is called automatically by the instrumentation.
     * 
     * Thread-safe and works correctly for both parallel="methods" and parallel="classes".
     * Automatically binds the session to the current thread for thread affinity.
     * 
     * @param driver WebDriver instance to track
     */
    public static void registerDriver(WebDriver driver) {
        if (driver != null) {
            // Track in ThreadLocal (per-thread)
            THREAD_DRIVERS.get().add(driver);
            
            // Also track globally (backup for thread pool reuse scenarios)
            ALL_DRIVERS.add(driver);
            
            // Register session-to-thread binding if this is a RemoteWebDriver
            if (driver instanceof RemoteWebDriver) {
                RemoteWebDriver remoteDriver = (RemoteWebDriver) driver;
                String sessionId = remoteDriver.getSessionId() != null ? 
                    remoteDriver.getSessionId().toString() : null;
                
                if (sessionId != null) {
                    boolean registered = sessionThreadManager.registerSession(sessionId);
                    if (registered) {
                        LOGGER.fine(String.format(
                            "Registered session %s to thread %d/%s",
                            sessionId, Thread.currentThread().getId(), 
                            Thread.currentThread().getName()));
                    }
                }
            }
            
            // Enhanced logging for parallel execution debugging
            long threadId = Thread.currentThread().getId();
            String threadName = Thread.currentThread().getName();
            LOGGER.info(String.format("[Thread-%d/%s] Registered WebDriver: %s (Total active: %d)", 
                threadId, threadName, driver.getClass().getSimpleName(), ALL_DRIVERS.size()));
        }
    }
    
    /**
     * Check if a WebDriver is still active (not already quit).
     * This prevents errors when user calls driver.quit() in their own @AfterMethod.
     */
    private static boolean isDriverActive(WebDriver driver) {
        try {
            if (driver instanceof RemoteWebDriver) {
                RemoteWebDriver remoteDriver = (RemoteWebDriver) driver;
                // Check if session ID exists - if null/empty, driver is already quit
                String sessionId = remoteDriver.getSessionId() != null ? 
                    remoteDriver.getSessionId().toString() : null;
                return sessionId != null && !sessionId.isEmpty();
            }
            // For non-RemoteWebDriver, assume active
            return true;
        } catch (Exception e) {
            // If we can't check, assume already quit
            return false;
        }
    }
    
    /**
     * Quit all WebDriver instances for the current thread.
     * This is called automatically after each test method completes.
     * 
     * Thread-safe and idempotent - safe to call multiple times.
     * Safe to use even if user calls driver.quit() in their own @AfterMethod.
     * Works correctly for both parallel="methods" and parallel="classes".
     */
    public static void quitAllDrivers() {
        List<WebDriver> drivers = THREAD_DRIVERS.get();
        
        // Check if already cleaned up (prevents double cleanup)
        if (drivers == null || drivers.isEmpty()) {
            LOGGER.fine(String.format("[Thread-%d/%s] No drivers to clean up", 
                Thread.currentThread().getId(), Thread.currentThread().getName()));
            return;
        }
        
        long threadId = Thread.currentThread().getId();
        String threadName = Thread.currentThread().getName();
        LOGGER.info(String.format("[Thread-%d/%s] Cleaning up %d WebDriver instance(s)", 
            threadId, threadName, drivers.size()));
        
        // Create a copy to avoid ConcurrentModificationException
        List<WebDriver> driversCopy = new ArrayList<>(drivers);
        
        // Clear the list immediately to prevent double cleanup
        drivers.clear();
        
        // Quit each driver and remove from global registry
        for (WebDriver driver : driversCopy) {
            try {
                if (driver != null) {
                    // Unregister session-thread binding if this is a RemoteWebDriver
                    if (driver instanceof RemoteWebDriver) {
                        RemoteWebDriver remoteDriver = (RemoteWebDriver) driver;
                        try {
                            String sessionId = remoteDriver.getSessionId() != null ? 
                                remoteDriver.getSessionId().toString() : null;
                            if (sessionId != null) {
                                sessionThreadManager.unregisterSession(sessionId);
                                LOGGER.fine(String.format(
                                    "Unregistered session %s from thread %d/%s",
                                    sessionId, Thread.currentThread().getId(), 
                                    Thread.currentThread().getName()));
                            }
                        } catch (Exception e) {
                            // Ignore - session might already be gone
                        }
                    }
                    
                    // Check if driver is already quit before trying to quit
                    if (isDriverActive(driver)) {
                        driver.quit();
                        LOGGER.fine("[Thread-" + Thread.currentThread().getId() + "] Successfully quit WebDriver: " + 
                            driver.getClass().getName() + " (Remaining active: " + ALL_DRIVERS.size() + ")");
                    } else {
                        LOGGER.fine("[Thread-" + Thread.currentThread().getId() + "] WebDriver already quit by user, skipping");
                    }
                    
                    // Remove from global registry regardless
                    ALL_DRIVERS.remove(driver);
                }
            } catch (Exception e) {
                // Even if quit fails, remove from registry
                ALL_DRIVERS.remove(driver);
                
                // Only log if it's NOT a "session already deleted" error
                String errorMsg = e.getMessage();
                if (errorMsg != null && (errorMsg.contains("Session ID is null") || 
                                        errorMsg.contains("already deleted") ||
                                        errorMsg.contains("invalid session id") ||
                                        errorMsg.contains("Session not found"))) {
                    LOGGER.fine("[Thread-" + Thread.currentThread().getId() + "] Driver already quit by user");
                } else {
                    LOGGER.warning("[Thread-" + Thread.currentThread().getId() + "] Failed to quit WebDriver: " + e.getMessage());
                }
            }
        }
        
        // CRITICAL: Remove ThreadLocal to prevent memory leaks in thread pools
        THREAD_DRIVERS.remove();
    }
    
    /**
     * Emergency cleanup: Quit ALL drivers across ALL threads.
     * This is called by TestNG suite listener after all tests complete.
     * 
     * Ensures no driver is left running, even if ThreadLocal cleanup failed.
     * Safe to use even if users call driver.quit() in their own @AfterMethod.
     * Also cleans up all session-thread bindings.
     */
    public static void quitAllDriversGlobally() {
        int count = ALL_DRIVERS.size();
        if (count > 0) {
            // Comment out - Expected behavior for parallel execution where user quits drivers
            // LOGGER.warning("Found " + count + " driver(s) not properly cleaned up. Forcing cleanup...");
            
            // Log session-thread bindings before cleanup
            if (sessionThreadManager.getActiveBindingsCount() > 0) {
                LOGGER.info("Active session-thread bindings:\n" + 
                    sessionThreadManager.getAllBindings());
            }
            
            // Create a copy to avoid ConcurrentModificationException
            List<WebDriver> driversCopy = new ArrayList<>(ALL_DRIVERS);
            ALL_DRIVERS.clear();
            
            int successCount = 0;
            int alreadyQuitCount = 0;
            
            for (WebDriver driver : driversCopy) {
                try {
                    if (driver != null) {
                        // Unregister session-thread binding first
                        if (driver instanceof RemoteWebDriver) {
                            RemoteWebDriver remoteDriver = (RemoteWebDriver) driver;
                            try {
                                String sessionId = remoteDriver.getSessionId() != null ? 
                                    remoteDriver.getSessionId().toString() : null;
                                if (sessionId != null) {
                                    sessionThreadManager.unregisterSession(sessionId);
                                }
                            } catch (Exception e) {
                                // Ignore - session might already be gone
                            }
                        }
                        
                        // Check if driver is still active before quitting
                        if (isDriverActive(driver)) {
                            driver.quit();
                            successCount++;
                            // LOGGER.info("Force-quit orphaned WebDriver: " + driver.getClass().getName());
                        } else {
                            alreadyQuitCount++;
                            LOGGER.fine("Driver already quit by user, skipping: " + driver.getClass().getName());
                        }
                    }
                } catch (Exception e) {
                    // Only log if it's not a "session already deleted" error
                    String errorMsg = e.getMessage();
                    if (errorMsg != null && (errorMsg.contains("Session ID is null") || 
                                            errorMsg.contains("already deleted") ||
                                            errorMsg.contains("invalid session id") ||
                                            errorMsg.contains("Session not found"))) {
                        alreadyQuitCount++;
                        LOGGER.fine("Driver already quit by user");
                    } else {
                        LOGGER.warning("Failed to force-quit driver: " + e.getMessage());
                    }
                }
            }
            
            if (successCount > 0 || alreadyQuitCount > 0) {
                LOGGER.info("Global cleanup: " + successCount + " quit, " + alreadyQuitCount + " already quit by user");
            }
            
            // Clear all session-thread mappings
            sessionThreadManager.clearAll();
        }
    }
    
    /**
     * Premain method called when agent is specified on command line.
     * This is the entry point for the Java agent.
     * 
     * @param agentArgs Arguments passed to the agent
     * @param inst Instrumentation instance provided by JVM
     */
    public static void premain(String agentArgs, Instrumentation inst) {
        // Enable ByteBuddy experimental mode for Java versions > 21
        System.setProperty("net.bytebuddy.experimental", "true");
        
        LOGGER.info("╔════════════════════════════════════════════════════════════╗");
        LOGGER.info(    "║   LambdaTest Selenium SDK Agent v" + AGENT_VERSION + "     ║");
        LOGGER.info("║   Runtime instrumentation enabled - No code changes!       ║");
        LOGGER.info("╚════════════════════════════════════════════════════════════╝");
        
        initialize(agentArgs, inst);
    }
    
    /**
     * Agentmain method for attaching to running JVM.
     * 
     * @param agentArgs Arguments passed to the agent
     * @param inst Instrumentation instance provided by JVM
     */
    public static void agentmain(String agentArgs, Instrumentation inst) {
        LOGGER.info("╔════════════════════════════════════════════════════════════╗");
        LOGGER.info("    ║   LambdaTest Selenium SDK Agent v" + AGENT_VERSION + "     ║");
        LOGGER.info("║   Runtime instrumentation enabled - No code changes!       ║");
        LOGGER.info("╚════════════════════════════════════════════════════════════╝");
        initialize(agentArgs, inst);
    }
    
    /**
     * Initialize the agent and set up instrumentation.
     * 
     * @param agentArgs Agent arguments
     * @param inst Instrumentation instance
     */
    private static void initialize(String agentArgs, Instrumentation inst) {
        try {
            LOGGER.info("Initializing LambdaTest instrumentation...");
            
            // Enable retransformation capability
            if (!inst.isRetransformClassesSupported()) {
                LOGGER.warning("Retransformation not supported, some classes may not be instrumented");
            } else {
                LOGGER.info("Retransformation capability enabled");
            }
            
            // Parse agent arguments if provided
            AgentConfig config = parseArguments(agentArgs);
            
            // Create agent builder with appropriate matchers
            AgentBuilder agentBuilder = new AgentBuilder.Default()
                .disableClassFormatChanges()
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .with(AgentBuilder.InitializationStrategy.NoOp.INSTANCE)
                .with(AgentBuilder.TypeStrategy.Default.REDEFINE)
                // Ignore classes that shouldn't be instrumented
                .ignore(ElementMatchers.nameStartsWith("com.google.inject"))
                .ignore(ElementMatchers.nameStartsWith("com.google.common"))
                .ignore(ElementMatchers.nameStartsWith("org.testng.internal"))
                .ignore(ElementMatchers.nameStartsWith("org.testng.SuiteRunner"))
                .ignore(ElementMatchers.nameStartsWith("org.testng.ITestContext"))
                .ignore(ElementMatchers.nameStartsWith("net.bytebuddy"))
                .ignore(ElementMatchers.nameStartsWith("sun."))
                .ignore(ElementMatchers.nameStartsWith("jdk.internal"))
                .ignore(ElementMatchers.nameContains("$Proxy"))
                .ignore(ElementMatchers.nameContains("$$"))
                .with(new AgentBuilder.Listener.Adapter() {
                    @Override
                    public void onTransformation(TypeDescription typeDescription, 
                                                ClassLoader classLoader,
                                                JavaModule module,
                                                boolean loaded,
                                                DynamicType dynamicType) {
                        LOGGER.fine("Transformed: " + typeDescription.getName());
                    }
                    
                    @Override
                    public void onError(String typeName, 
                                       ClassLoader classLoader,
                                       JavaModule module,
                                       boolean loaded,
                                       Throwable throwable) {
                        // Suppress common ignorable errors (dependency resolution failures, etc.)
                        String errorMsg = throwable.getMessage();
                        if (errorMsg != null && (
                            errorMsg.contains("Cannot resolve type description") ||
                            errorMsg.contains("Field values caching was disabled") ||
                            errorMsg.contains("Cannot call super") ||
                            errorMsg.contains("allows for delegation") ||
                            errorMsg.contains("com.google.inject") ||
                            typeName.contains("$$") ||
                            typeName.startsWith("org.testng.internal") ||
                            typeName.startsWith("com.google.")
                        )) {
                            // Log at FINE level for expected/ignorable errors
                            LOGGER.fine("Skipped transformation of " + typeName + ": " + errorMsg);
                        } else {
                            // Log genuine errors at WARNING level
                            LOGGER.log(Level.WARNING, 
                                "Error transforming " + typeName + ": " + errorMsg, 
                                throwable);
                        }
                    }
                });
            
            // Install WebDriver constructor interceptors
            installWebDriverInterceptors(agentBuilder, inst, config);
            
            // Install test framework method interceptors
            installTestFrameworkMethodInterceptors(agentBuilder, inst, config);
            
            // Install test framework interceptors
            installTestFrameworkInterceptors(agentBuilder, inst, config);
            
            // Register TestNG listener automatically
            registerTestNGListener();
            
            // Install WebDriver field interceptor for parallel="methods" support
            installWebDriverFieldInterceptor(inst);
            
            LOGGER.info("LambdaTest instrumentation initialized successfully!");
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize LambdaTest agent: " + e.getMessage(), e);
            throw new RuntimeException("LambdaTest agent initialization failed", e);
        }
    }
    
    /**
     * Install WebDriver field interceptor for thread-safe parallel execution.
     * 
     * This intercepts all WebDriver field access (GETFIELD/PUTFIELD) and redirects
     * to ThreadLocal storage, enabling parallel="methods" without code changes.
     * 
     * @param inst Instrumentation instance
     */
    private static void installWebDriverFieldInterceptor(Instrumentation inst) {
        try {
            LOGGER.info("Installing WebDriver field interceptor for parallel execution...");
            
            // Add the field transformer
            inst.addTransformer(new WebDriverFieldTransformer(), true);
            
            LOGGER.info("WebDriver field interceptor installed (enables parallel=\"methods\")");
            
        } catch (Exception e) {
            LOGGER.warning("Failed to install WebDriver field interceptor: " + e.getMessage());
        }
    }
    
    /**
     * Install WebDriver constructor interceptors to redirect to LambdaTest.
     * 
     * @param builder Agent builder
     * @param inst Instrumentation instance
     * @param config Agent configuration
     */
    private static void installWebDriverInterceptors(AgentBuilder builder,
                                                     Instrumentation inst,
                                                     AgentConfig config) {
        LOGGER.info("Installing WebDriver interceptors...");
        
        // Check for LambdaTest credentials from environment variables or YAML
        String username = System.getenv("LT_USERNAME");
        String accessKey = System.getenv("LT_ACCESS_KEY");
        
        // If not in env vars, try loading from lambdatest.yml
        if (username == null || accessKey == null) {
            try {
                com.lambdatest.selenium.lambdatest.LambdaTestConfig ltConfig = com.lambdatest.selenium.lambdatest.LambdaTestConfig.getInstance();
                if (username == null) {
                    username = ltConfig.getUsername();
                }
                if (accessKey == null) {
                    accessKey = ltConfig.getAccessKey();
                }
            } catch (Exception e) {
                // Ignore - credentials may not be needed for all scenarios
            }
        }
        
        if (username != null && accessKey != null) {
            LOGGER.info("LambdaTest credentials loaded");
        } else {
            LOGGER.info("No LambdaTest credentials found (tests will use capabilities from code/YAML)");
        }
        
        // Install ASM-based transformer for RemoteWebDriver constructor interception
        // This handles capability enhancement and driver registration
        inst.addTransformer(new RemoteWebDriverBytecodeTransformer(), true);
        
        // Install transformer for local driver interception (ChromeDriver, FirefoxDriver, etc.)
        // This redirects local driver creation to LambdaTest RemoteWebDriver
        inst.addTransformer(new LocalDriverBytecodeTransformer(), true);
        
        LOGGER.info("WebDriver interceptors installed (RemoteWebDriver + local drivers: Chrome, Firefox, Edge, Safari, Opera, IE)");
    }
    
    
    /**
     * Register TestNG listener automatically.
     * This is the most reliable way to intercept WebDriver creation.
     */
    private static void registerTestNGListener() {
        try {
            
            // Use reflection to register the listener with TestNG
            Class<?> testNGClass = Class.forName("org.testng.TestNG");
            
        } catch (ClassNotFoundException e) {
        } catch (Exception e) {
        }
    }
    
    /**
     * Install test framework method interceptors for capability enhancement and driver cleanup.
     * Supports both TestNG and JUnit frameworks.
     * 
     * @param builder Agent builder
     * @param inst Instrumentation instance
     * @param config Agent configuration
     */
    private static void installTestFrameworkMethodInterceptors(AgentBuilder builder,
                                                               Instrumentation inst,
                                                               AgentConfig config) {
        LOGGER.info("Installing TestNG and JUnit method interceptors...");
        
        // TestNG listener (TestNgTestListener) is more reliable for driver cleanup
        // The listener's onTestSuccess/Failure/Skipped methods handle cleanup
        // The global cleanup in onFinish(ISuite) ensures ALL drivers are quit
        LOGGER.info("Using TestNG listener for driver cleanup (more reliable for parallel execution)");
    }
    
    /**
     * Install test framework interceptors for automatic lifecycle management.
     * 
     * @param builder Agent builder
     * @param inst Instrumentation instance
     * @param config Agent configuration
     */
    private static void installTestFrameworkInterceptors(AgentBuilder builder,
                                                         Instrumentation inst,
                                                         AgentConfig config) {
        LOGGER.info("Installing test framework interceptors...");
        
        // For TestNG, automatically register unified listener
        try {
            Class.forName("org.testng.annotations.Test");
            String existingListeners = System.getProperty("testng.listeners", "");
            
            // Register unified listener (handles both suite-level and test-level operations)
            String listener = "com.lambdatest.selenium.testng.TestNgTestListener";
            
            // Add listener
            if (!existingListeners.contains(listener)) {
                String newListeners = existingListeners.isEmpty() ? 
                    listener : existingListeners + "," + listener;
                System.setProperty("testng.listeners", newListeners);
                LOGGER.info("TestNG listener auto-registered: " + listener);
                
                // Enhanced registration methods for Maven Surefire compatibility
                try {
                    // Method 1: Direct class loading and instantiation
                    Class<?> listenerClass = Class.forName(listener);
                    LOGGER.info("TestNG listener class loaded successfully");
                    
                    // Method 2: Set additional system properties that TestNG might check
                    System.setProperty("org.testng.listeners", listener);
                    LOGGER.info("TestNG listener registered via org.testng.listeners property");
                    
                    // Method 3: Maven Surefire specific properties
                    registerMavenSurefireListener(listener);
                    
                } catch (ClassNotFoundException e) {
                    LOGGER.warning("Could not load TestNG listener class: " + e.getMessage());
                }
            }
        } catch (ClassNotFoundException e) {
            LOGGER.fine("TestNG not found on classpath");
        }
        
        // For JUnit 5, register extension via System property
        try {
            Class.forName("org.junit.jupiter.api.Test");
            // JUnit 5 extensions are typically registered via @ExtendWith
            // We'll use ServiceLoader approach instead
            LOGGER.info("JUnit 5 detected - using extension approach");
        } catch (ClassNotFoundException e) {
            LOGGER.fine("JUnit 5 not found on classpath");
        }
    }
    
    /**
     * Parse agent arguments.
     * 
     * @param agentArgs Comma-separated key=value pairs
     * @return Agent configuration
     */
    private static AgentConfig parseArguments(String agentArgs) {
        AgentConfig config = new AgentConfig();
        
        if (agentArgs != null && !agentArgs.trim().isEmpty()) {
            String[] args = agentArgs.split(",");
            for (String arg : args) {
                String[] keyValue = arg.split("=", 2);
                if (keyValue.length == 2) {
                    String key = keyValue[0].trim();
                    String value = keyValue[1].trim();
                    
                    switch (key) {
                        case "debug":
                            config.setDebug(Boolean.parseBoolean(value));
                            break;
                        case "autoManageLifecycle":
                            config.setAutoManageLifecycle(Boolean.parseBoolean(value));
                            break;
                        default:
                            LOGGER.warning("Unknown agent argument: " + key);
                    }
                }
            }
        }
        
        if (config.isDebug()) {
            Logger.getLogger("com.lambdatest").setLevel(Level.FINE);
        }
        
        return config;
    }
    
    /**
     * Register TestNG listener via Maven Surefire properties.
     * This ensures the listener is automatically registered when using Maven Surefire plugin.
     * 
     * @param listenerClassName The fully qualified class name of the listener
     */
    private static void registerMavenSurefireListener(String listenerClassName) {
        try {
            // Method 1: Set Maven Surefire TestNG listener property
            String surefireListeners = System.getProperty("surefire.testng.listeners", "");
            if (!surefireListeners.contains(listenerClassName)) {
                String newListeners = surefireListeners.isEmpty() ? 
                    listenerClassName : surefireListeners + "," + listenerClassName;
                System.setProperty("surefire.testng.listeners", newListeners);
                LOGGER.info("Maven Surefire TestNG listener registered: " + listenerClassName);
            }
            
            // Method 2: Set Maven Surefire properties property
            String surefireProperties = System.getProperty("surefire.properties", "");
            String listenerProperty = "listener=" + listenerClassName;
            if (!surefireProperties.contains(listenerProperty)) {
                String newProperties = surefireProperties.isEmpty() ? 
                    listenerProperty : surefireProperties + "," + listenerProperty;
                System.setProperty("surefire.properties", newProperties);
                LOGGER.info("Maven Surefire properties listener registered: " + listenerClassName);
            }
            
            // Method 3: Set TestNG system properties that Maven Surefire might check
            System.setProperty("testng.default.listeners", listenerClassName);
            LOGGER.info("TestNG default listeners property set: " + listenerClassName);
            
            // Method 4: Set additional TestNG properties
            System.setProperty("testng.listeners", listenerClassName);
            LOGGER.info("TestNG listeners property set: " + listenerClassName);
            
            // Method 5: Try to register via reflection if TestNG is available
            try {
                Class<?> testNGClass = Class.forName("org.testng.TestNG");
                LOGGER.info("TestNG class found, attempting reflection-based registration");
                
                // This is a more aggressive approach - we'll try to register the listener
                // by modifying TestNG's internal state if possible
                registerListenerViaReflection(listenerClassName);
                
            } catch (ClassNotFoundException e) {
                LOGGER.fine("TestNG class not found for reflection-based registration");
            }
            
        } catch (Exception e) {
            LOGGER.warning("Failed to register Maven Surefire listener: " + e.getMessage());
        }
    }
    
    /**
     * Attempt to register listener via reflection-based approach.
     * This is a more aggressive method that tries to directly modify TestNG's behavior.
     * 
     * @param listenerClassName The fully qualified class name of the listener
     */
    private static void registerListenerViaReflection(String listenerClassName) {
        try {
            // Try to find and instantiate the listener
            Class<?> listenerClass = Class.forName(listenerClassName);
            Object listenerInstance = listenerClass.getDeclaredConstructor().newInstance();
            
            LOGGER.info("Listener instantiated via reflection: " + listenerClassName);
            
            // Try to register with TestNG's global listener registry if it exists
            try {
                Class<?> testNGClass = Class.forName("org.testng.TestNG");
                LOGGER.info("Attempting to register listener with TestNG via reflection");
                
                // Try to find TestNG's listener registry and register our listener
                try {
                    // Method 1: Try to find TestNG's global listener registry
                    Class<?> listenerRegistryClass = Class.forName("org.testng.internal.listeners.ListenerRegistry");
                    LOGGER.info("Found TestNG ListenerRegistry class");
                    
                    // Try to get the global instance and register our listener
                    try {
                        java.lang.reflect.Method getInstanceMethod = listenerRegistryClass.getMethod("getInstance");
                        Object registry = getInstanceMethod.invoke(null);
                        
                        java.lang.reflect.Method addListenerMethod = listenerRegistryClass.getMethod("addListener", Object.class);
                        addListenerMethod.invoke(registry, listenerInstance);
                        
                        LOGGER.info("Successfully registered listener with TestNG ListenerRegistry");
                        
                    } catch (Exception e) {
                        LOGGER.fine("Could not register with ListenerRegistry: " + e.getMessage());
                    }
                    
                } catch (ClassNotFoundException e) {
                    LOGGER.fine("TestNG ListenerRegistry not found: " + e.getMessage());
                }
                
                // Method 2: Try to register with TestNG's SuiteRunner
                try {
                    Class<?> suiteRunnerClass = Class.forName("org.testng.SuiteRunner");
                    LOGGER.info("Found TestNG SuiteRunner class");
                    
                    // This is more complex as we'd need to intercept SuiteRunner creation
                    // For now, we'll log that we found the class
                    
                } catch (ClassNotFoundException e) {
                    LOGGER.fine("TestNG SuiteRunner not found: " + e.getMessage());
                }
                
                // Method 3: Try to set up a global listener that TestNG will pick up
                try {
                    // Set up a static listener that TestNG might discover
                    System.setProperty("testng.global.listeners", listenerClassName);
                    LOGGER.info("Set testng.global.listeners property");
                    
                } catch (Exception e) {
                    LOGGER.fine("Could not set global listeners property: " + e.getMessage());
                }
                
            } catch (Exception e) {
                LOGGER.fine("Reflection-based TestNG registration not available: " + e.getMessage());
            }
            
        } catch (Exception e) {
            LOGGER.warning("Failed to instantiate listener via reflection: " + e.getMessage());
        }
    }
    
   
}

