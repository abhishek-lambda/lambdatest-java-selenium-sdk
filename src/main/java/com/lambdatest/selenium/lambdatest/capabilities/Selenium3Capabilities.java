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
            new CapabilityDefinition(CapabilityKeys.VERSION, CapabilityDefinition.CapabilityTarget.DESIRED_CAPABILITIES),
            
            // Organization capabilities
            new CapabilityDefinition(CapabilityKeys.BUILD, 
                Arrays.asList(CapabilityKeys.BUILD_NAME, CapabilityKeys.JOB, CapabilityKeys.JOB_NAME), 
                CapabilityDefinition.CapabilityTarget.BOTH),
            // Note: project is handled separately in special cases due to projectName mapping
            new CapabilityDefinition(CapabilityKeys.NAME, 
                Arrays.asList(CapabilityKeys.TESTNAME, CapabilityKeys.SESSIONNAME, CapabilityKeys.TEST), 
                CapabilityDefinition.CapabilityTarget.BOTH),
            new CapabilityDefinition(CapabilityKeys.TAGS, CapabilityDefinition.CapabilityTarget.BOTH),
            new CapabilityDefinition(CapabilityKeys.BUILD_TAGS, CapabilityDefinition.CapabilityTarget.BOTH),
            
            // Driver and version capabilities
            new CapabilityDefinition(CapabilityKeys.DRIVER_VERSION, 
                Arrays.asList(CapabilityKeys.DRIVER_VERSION_ALIAS, CapabilityKeys.DRIVER), 
                CapabilityDefinition.CapabilityTarget.BOTH),
            
            // Resolution
            new CapabilityDefinition(CapabilityKeys.RESOLUTION, 
                Arrays.asList(CapabilityKeys.VIEWPORT), 
                CapabilityDefinition.CapabilityTarget.BOTH),
            
            // Extension loading
            new CapabilityDefinition(CapabilityKeys.LAMBDA_LOAD_EXTENSION, 
                Arrays.asList(CapabilityKeys.LOAD_EXTENSION), 
                CapabilityDefinition.CapabilityTarget.DESIRED_CAPABILITIES),
            
            // Logging capabilities
            new CapabilityDefinition(CapabilityKeys.COMMAND_LOG, 
                Arrays.asList(CapabilityKeys.COMMAND_LOGS), 
                CapabilityDefinition.CapabilityTarget.DESIRED_CAPABILITIES),
            new CapabilityDefinition(CapabilityKeys.SYSTEM_LOG, 
                Arrays.asList(CapabilityKeys.SELENIUM_LOGS), 
                CapabilityDefinition.CapabilityTarget.DESIRED_CAPABILITIES),
            
            // Network capabilities
            new CapabilityDefinition(CapabilityKeys.NETWORK_HTTP2, CapabilityDefinition.CapabilityTarget.DESIRED_CAPABILITIES),
            new CapabilityDefinition(CapabilityKeys.DISABLE_XF_HEADERS, CapabilityDefinition.CapabilityTarget.DESIRED_CAPABILITIES),
            new CapabilityDefinition(CapabilityKeys.NETWORK_DEBUG, CapabilityDefinition.CapabilityTarget.DESIRED_CAPABILITIES),
            new CapabilityDefinition(CapabilityKeys.IGNORE_FF_OPTIONS_ARGS, CapabilityDefinition.CapabilityTarget.DESIRED_CAPABILITIES),
            new CapabilityDefinition(CapabilityKeys.UPDATE_BUILD_STATUS_ON_SUCCESS, CapabilityDefinition.CapabilityTarget.DESIRED_CAPABILITIES)
        );
    }
}

