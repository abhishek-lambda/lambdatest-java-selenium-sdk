package com.lambdatest.selenium.agent;

/**
 * Configuration for LambdaTest Agent.
 * Controls various aspects of runtime instrumentation.
 */
public class AgentConfig {
    
    private boolean debug = false;
    private boolean autoManageLifecycle = true;
    
    public AgentConfig() {
        // Default configuration
    }
    
    /**
     * Check if debug mode is enabled.
     * 
     * @return true if debug logging is enabled
     */
    public boolean isDebug() {
        return debug;
    }
    
    /**
     * Enable or disable debug mode.
     * 
     * @param debug true to enable debug logging
     */
    public void setDebug(boolean debug) {
        this.debug = debug;
    }
    
    /**
     * Check if automatic lifecycle management is enabled.
     * 
     * @return true if test lifecycle should be managed automatically
     */
    public boolean isAutoManageLifecycle() {
        return autoManageLifecycle;
    }
    
    /**
     * Enable or disable automatic lifecycle management.
     * 
     * @param autoManageLifecycle true to automatically manage test lifecycle
     */
    public void setAutoManageLifecycle(boolean autoManageLifecycle) {
        this.autoManageLifecycle = autoManageLifecycle;
    }
    
    @Override
    public String toString() {
        return "AgentConfig{" +
                "debug=" + debug +
                ", autoManageLifecycle=" + autoManageLifecycle +
                '}';
    }
}

