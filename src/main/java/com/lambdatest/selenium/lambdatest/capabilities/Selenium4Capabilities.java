package com.lambdatest.selenium.lambdatest.capabilities;

import java.util.Arrays;
import java.util.List;

/**
 * Selenium 4 / W3C standard capabilities.
 * These are set in lt:options for W3C compliance.
 */
public class Selenium4Capabilities {
    
    /**
     * Get all Selenium 4 capability definitions.
     */
    public static List<CapabilityDefinition> getDefinitions() {
        return Arrays.asList(
            // Driver and Selenium version
            new CapabilityDefinition("driver_version", 
                Arrays.asList("driverVersion", "driver"), 
                CapabilityDefinition.CapabilityTarget.LT_OPTIONS),
            new CapabilityDefinition("selenium_version", 
                Arrays.asList("seleniumVersion", "seVersion"), 
                CapabilityDefinition.CapabilityTarget.LT_OPTIONS),
            
            // Timeout
            new CapabilityDefinition("idleTimeout", 
                Arrays.asList("idle"), 
                CapabilityDefinition.CapabilityTarget.LT_OPTIONS),
            
            // Organization (also in Selenium 3, but processed separately for clarity)
            new CapabilityDefinition("build", CapabilityDefinition.CapabilityTarget.LT_OPTIONS),
            new CapabilityDefinition("project", CapabilityDefinition.CapabilityTarget.LT_OPTIONS),
            new CapabilityDefinition("name", CapabilityDefinition.CapabilityTarget.LT_OPTIONS),
            new CapabilityDefinition("tags", CapabilityDefinition.CapabilityTarget.LT_OPTIONS),
            new CapabilityDefinition("buildTags", CapabilityDefinition.CapabilityTarget.LT_OPTIONS),
            
            // Debugging capabilities
            new CapabilityDefinition("video", CapabilityDefinition.CapabilityTarget.LT_OPTIONS),
            new CapabilityDefinition("visual", 
                Arrays.asList("debug"), 
                CapabilityDefinition.CapabilityTarget.LT_OPTIONS),
            new CapabilityDefinition("network", 
                Arrays.asList("networkLogs"), 
                CapabilityDefinition.CapabilityTarget.LT_OPTIONS),
            new CapabilityDefinition("console", CapabilityDefinition.CapabilityTarget.LT_OPTIONS),
            new CapabilityDefinition("network.mask", CapabilityDefinition.CapabilityTarget.LT_OPTIONS),
            new CapabilityDefinition("verboseWebDriverLogging", CapabilityDefinition.CapabilityTarget.LT_OPTIONS),
            new CapabilityDefinition("network.full.har", CapabilityDefinition.CapabilityTarget.LT_OPTIONS),
            
            // Environment
            new CapabilityDefinition("resolution", CapabilityDefinition.CapabilityTarget.LT_OPTIONS),
            new CapabilityDefinition("timezone", CapabilityDefinition.CapabilityTarget.LT_OPTIONS),
            
            // Tunnel
            new CapabilityDefinition("tunnel", 
                Arrays.asList("local"), 
                CapabilityDefinition.CapabilityTarget.LT_OPTIONS),
            new CapabilityDefinition("tunnelName", 
                Arrays.asList("localName"), 
                CapabilityDefinition.CapabilityTarget.LT_OPTIONS),
            
            // Auto Healing & Smart Wait
            new CapabilityDefinition("autoHeal", CapabilityDefinition.CapabilityTarget.LT_OPTIONS),
            new CapabilityDefinition("smartWait", CapabilityDefinition.CapabilityTarget.LT_OPTIONS),
            new CapabilityDefinition("smartWaitRetryDelay", CapabilityDefinition.CapabilityTarget.LT_OPTIONS),
            
            // Geolocation
            new CapabilityDefinition("geoLocation", CapabilityDefinition.CapabilityTarget.LT_OPTIONS),
            
            // Security & Privacy
            new CapabilityDefinition("lambdaMaskCommands", CapabilityDefinition.CapabilityTarget.LT_OPTIONS),
            
            // Network
            new CapabilityDefinition("networkThrottling", CapabilityDefinition.CapabilityTarget.LT_OPTIONS),
            
            // Custom Configuration
            new CapabilityDefinition("customHeaders", CapabilityDefinition.CapabilityTarget.LT_OPTIONS),
            new CapabilityDefinition("customDnsMap", CapabilityDefinition.CapabilityTarget.LT_OPTIONS)
        );
    }
}

