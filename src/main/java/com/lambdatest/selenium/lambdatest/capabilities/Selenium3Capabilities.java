package com.lambdatest.selenium.lambdatest.capabilities;

import java.util.Arrays;
import java.util.List;

/**
 * Selenium 3 specific capabilities.
 * These are set directly on DesiredCapabilities for backwards compatibility.
 */
public class Selenium3Capabilities {
    
    /**
     * Get all Selenium 3 capability definitions.
     */
    public static List<CapabilityDefinition> getDefinitions() {
        return Arrays.asList(
            // Basic browser capabilities (also in Selenium 4, but needed for Selenium 3 compatibility)
            new CapabilityDefinition("version", CapabilityDefinition.CapabilityTarget.DESIRED_CAPABILITIES),
            
            // Organization capabilities
            new CapabilityDefinition("build", 
                Arrays.asList("buildName", "job", "jobName"), 
                CapabilityDefinition.CapabilityTarget.BOTH),
            // Note: project is handled separately in special cases due to projectName mapping
            new CapabilityDefinition("name", 
                Arrays.asList("testname", "sessionname", "test"), 
                CapabilityDefinition.CapabilityTarget.BOTH),
            new CapabilityDefinition("tags", CapabilityDefinition.CapabilityTarget.BOTH),
            new CapabilityDefinition("buildTags", CapabilityDefinition.CapabilityTarget.BOTH),
            
            // Driver and version capabilities
            new CapabilityDefinition("driver_version", 
                Arrays.asList("driverVersion", "driver"), 
                CapabilityDefinition.CapabilityTarget.BOTH),
            
            // Resolution
            new CapabilityDefinition("resolution", 
                Arrays.asList("viewport"), 
                CapabilityDefinition.CapabilityTarget.BOTH),
            
            // Extension loading
            new CapabilityDefinition("lambda:loadExtension", 
                Arrays.asList("loadExtension"), 
                CapabilityDefinition.CapabilityTarget.DESIRED_CAPABILITIES),
            
            // Logging capabilities
            new CapabilityDefinition("commandLog", 
                Arrays.asList("commandLogs"), 
                CapabilityDefinition.CapabilityTarget.DESIRED_CAPABILITIES),
            new CapabilityDefinition("systemLog", 
                Arrays.asList("seleniumLogs"), 
                CapabilityDefinition.CapabilityTarget.DESIRED_CAPABILITIES),
            
            // Network capabilities
            new CapabilityDefinition("network.http2", CapabilityDefinition.CapabilityTarget.DESIRED_CAPABILITIES),
            new CapabilityDefinition("DisableXFHeaders", CapabilityDefinition.CapabilityTarget.DESIRED_CAPABILITIES),
            new CapabilityDefinition("network.debug", CapabilityDefinition.CapabilityTarget.DESIRED_CAPABILITIES),
            new CapabilityDefinition("ignoreFfOptionsArgs", CapabilityDefinition.CapabilityTarget.DESIRED_CAPABILITIES),
            new CapabilityDefinition("updateBuildStatusOnSuccess", CapabilityDefinition.CapabilityTarget.DESIRED_CAPABILITIES)
        );
    }
}

