package com.lambdatest.selenium.agent;

import java.net.URL;
import java.util.Map;
import java.util.logging.Logger;

import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;

import com.lambdatest.selenium.lambdatest.LambdaTestConfig;

/**
 * Helper class to redirect local driver creation to LambdaTest RemoteWebDriver.
 * 
 * This is called from transformed bytecode when users create local drivers
 * like new ChromeDriver(), new FirefoxDriver(), etc.
 */
public class LocalDriverRedirector {
    
    private static final Logger LOGGER = Logger.getLogger(LocalDriverRedirector.class.getName());
    
    // ThreadLocal to store the created RemoteWebDriver for the current thread
    private static final ThreadLocal<RemoteWebDriver> currentRemoteDriver = new ThreadLocal<>();
    
    /**
     * Create a RemoteWebDriver for LambdaTest when a local driver is instantiated.
     * 
     * @param browserName The browser name (Chrome, Firefox, Edge, Safari, Opera, IE)
     * @param localDriverInstance The local driver instance that was created
     * @return RemoteWebDriver instance connected to LambdaTest
     */
    public static RemoteWebDriver createRemoteWebDriver(String browserName, Object localDriverInstance) {
        try {
            LOGGER.info("Redirecting local " + browserName + " driver to LambdaTest cloud...");
            
            // Get LambdaTest configuration
            LambdaTestConfig config = LambdaTestConfig.getInstance();
            String hubUrl = config.getHubUrl();
            
            // Create capabilities with browser name
            DesiredCapabilities capabilities = config.getCapabilitiesFromYaml();
            
            // Ensure browserName is set
            if (!capabilities.asMap().containsKey("browserName")) {
                capabilities.setCapability("browserName", browserName);
            }
            
            // Create RemoteWebDriver
            @SuppressWarnings("deprecation")
            RemoteWebDriver remoteDriver = new RemoteWebDriver(new URL(hubUrl), capabilities);
            
            // Register for automatic cleanup
            LambdaTestAgent.registerDriver(remoteDriver);
            
            // Store in ThreadLocal for access
            currentRemoteDriver.set(remoteDriver);
            
            LOGGER.info("Successfully created RemoteWebDriver for " + browserName + " on LambdaTest");
            
            return remoteDriver;
            
        } catch (Exception e) {
            String errorMsg = "Failed to redirect to LambdaTest: " + e.getMessage();
            LOGGER.severe(errorMsg);
            throw new RuntimeException(errorMsg, e);
        }
    }
    
    /**
     * Redirect local driver constructor to LambdaTest.
     * This method is called from transformed bytecode.
     * 
     * @param browserName The browser name
     * @param localDriverInstance The local driver instance
     */
    public static void redirectToLambdaTest(String browserName, Object localDriverInstance) {
        try {
            RemoteWebDriver remoteDriver = createRemoteWebDriver(browserName, localDriverInstance);
            
            // Store the RemoteWebDriver reference in the local driver instance using reflection
            // This allows the local driver to delegate to RemoteWebDriver
            try {
                java.lang.reflect.Field field = localDriverInstance.getClass().getDeclaredField("remoteDriver");
                field.setAccessible(true);
                field.set(localDriverInstance, remoteDriver);
            } catch (NoSuchFieldException e) {
                // Field doesn't exist - that's okay, we'll use a different approach
                // Store in a map keyed by instance
                storeDriverMapping(localDriverInstance, remoteDriver);
            }
            
        } catch (Exception e) {
            String errorMsg = "Failed to redirect local driver: " + e.getMessage();
            LOGGER.severe(errorMsg);
        }
    }
    
    // Map to store local driver -> RemoteWebDriver mappings
    private static final Map<Object, RemoteWebDriver> driverMappings = 
        new java.util.concurrent.ConcurrentHashMap<>();
    
    /**
     * Store mapping between local driver instance and RemoteWebDriver.
     */
    private static void storeDriverMapping(Object localDriver, RemoteWebDriver remoteDriver) {
        driverMappings.put(localDriver, remoteDriver);
    }
    
    /**
     * Get RemoteWebDriver for a local driver instance.
     */
    public static RemoteWebDriver getRemoteDriver(Object localDriver) {
        return driverMappings.get(localDriver);
    }
    
    /**
     * Get the current RemoteWebDriver for this thread.
     */
    public static RemoteWebDriver getCurrentRemoteDriver() {
        return currentRemoteDriver.get();
    }
    
    /**
     * Clear ThreadLocal to prevent memory leaks.
     */
    public static void clearCurrentDriver() {
        currentRemoteDriver.remove();
    }
}

