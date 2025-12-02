package com.lambdatest.selenium.lambdatest.capabilities;

import java.util.Arrays;
import java.util.List;

/**
 * Definition of a capability with its key, aliases, and target locations.
 * This makes it easy to add new capabilities without modifying the main processing logic.
 */
public class CapabilityDefinition {
    
    private final String primaryKey;
    private final List<String> aliases;
    private final CapabilityTarget target;
    private final String targetKey; // Optional: different key in target (e.g., "project" -> "projectName" for Selenium 3)
    
    /**
     * Where the capability should be set.
     */
    public enum CapabilityTarget {
        /** Set directly on DesiredCapabilities (Selenium 3 style) */
        DESIRED_CAPABILITIES,
        /** Set in lt:options (Selenium 4/W3C style) */
        LT_OPTIONS,
        /** Set in both places (for backwards compatibility) */
        BOTH
    }
    
    public CapabilityDefinition(String primaryKey, CapabilityTarget target) {
        this(primaryKey, target, null);
    }
    
    public CapabilityDefinition(String primaryKey, CapabilityTarget target, String targetKey) {
        this(primaryKey, Arrays.asList(), target, targetKey);
    }
    
    public CapabilityDefinition(String primaryKey, List<String> aliases, CapabilityTarget target) {
        this(primaryKey, aliases, target, null);
    }
    
    public CapabilityDefinition(String primaryKey, List<String> aliases, CapabilityTarget target, String targetKey) {
        this.primaryKey = primaryKey;
        this.aliases = aliases;
        this.target = target;
        this.targetKey = targetKey;
    }
    
    public String getPrimaryKey() {
        return primaryKey;
    }
    
    public List<String> getAliases() {
        return aliases;
    }
    
    public CapabilityTarget getTarget() {
        return target;
    }
    
    public String getTargetKey() {
        return targetKey != null ? targetKey : primaryKey;
    }
    
    /**
     * Check if this definition matches the given key (primary key or any alias).
     */
    public boolean matches(String key) {
        return primaryKey.equals(key) || aliases.contains(key);
    }
    
    /**
     * Get the value from config if it exists (checking primary key and aliases).
     */
    public Object getValue(java.util.Map<String, Object> config) {
        if (config.containsKey(primaryKey)) {
            return config.get(primaryKey);
        }
        for (String alias : aliases) {
            if (config.containsKey(alias)) {
                return config.get(alias);
            }
        }
        return null;
    }
}

