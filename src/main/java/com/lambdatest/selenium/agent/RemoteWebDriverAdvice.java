package com.lambdatest.selenium.agent;

import java.util.Map;
import java.util.Set;

import org.openqa.selenium.MutableCapabilities;

import com.lambdatest.selenium.lambdatest.LambdaTestConfig;
import com.lambdatest.selenium.lambdatest.SessionThreadManager;

/**
 * ByteBuddy Advice for RemoteWebDriver constructor interception.
 * 
 * This class enhances capabilities before RemoteWebDriver is created,
 * adding LambdaTest-specific configuration from lambdatest.yml.
 */
public class RemoteWebDriverAdvice {

    // Track which capabilities objects have been enhanced to prevent double-processing
    private static final java.util.Set<Integer> processedCapabilities = 
        new java.util.concurrent.ConcurrentHashMap<Integer, Boolean>().keySet(true);
    
    // ThreadLocal to prevent recursive enhancement when SDK creates RemoteWebDriver internally
    private static final ThreadLocal<Boolean> internalDriverCreation = ThreadLocal.withInitial(() -> false);
    
    // ThreadLocal re-entrance guard to prevent multiple enhancement calls in the same thread
    private static final ThreadLocal<Boolean> isEnhancing = ThreadLocal.withInitial(() -> false);
    
    // Flag to warn only once about missing tunnel (avoid spam in parallel execution)
    private static volatile boolean warnedAboutMissingTunnel = false;
    
    // Session-thread manager for validating thread affinity
    private static final SessionThreadManager sessionThreadManager = SessionThreadManager.getInstance();
    
    // LambdaTest-specific keys that should ONLY be in lt:options, not at W3C top level
    // These keys cause W3C validation errors in Selenium 4 if set at top level
    private static final Set<String> LT_SPECIFIC_KEYS = Set.of(
        "build", "name", "projectName", "resolution", 
        "buildTags", "driver_version", "tags"
    );
    
    // Selenium 3 capabilities that should be moved to lt:options when using Selenium 4
    // These are legacy capabilities that Selenium 4 rejects at top level
    private static final Set<String> SELENIUM3_LEGACY_KEYS = Set.of(
        "version", "commandLog", "systemLog", "network.http2", 
        "DisableXFHeaders", "network.debug", "ignoreFfOptionsArgs", 
        "updateBuildStatusOnSuccess", "lambda:loadExtension"
    );

    /**
     * Static method to enhance capabilities that can be called from ASM bytecode.
     * This is called by RemoteWebDriverBytecodeTransformer.
     */
    public static void enhanceCapabilities(MutableCapabilities capabilities) {
        // Skip if this is an internal SDK driver creation
        if (internalDriverCreation.get()) {
            System.out.println("[LambdaTest SDK RemoteWebDriverAdvice] Skipping enhancement for internal driver creation");
            return;
        }
        
        // Re-entrance guard: prevent nested calls from the same thread
        if (isEnhancing.get()) {
            System.out.println("[LambdaTest SDK RemoteWebDriverAdvice] Already enhancing in this thread, skipping to prevent recursion");
            return;
        }
        
        // Prevent double-processing of the same capabilities object
        int capabilitiesHash = System.identityHashCode(capabilities);
        if (processedCapabilities.contains(capabilitiesHash)) {
            System.out.println("[LambdaTest SDK RemoteWebDriverAdvice] Capabilities already processed, skipping");
            return;
        }
        processedCapabilities.add(capabilitiesHash);
        
        // Log thread information for debugging
        long threadId = Thread.currentThread().getId();
        String threadName = Thread.currentThread().getName();
        System.out.println(String.format(
            "[LambdaTest SDK RemoteWebDriverAdvice] Enhancing capabilities on thread %d/%s",
            threadId, threadName));
        
        // Set re-entrance guard
        isEnhancing.set(true);
        
        try {
            // Load configuration from lambdatest.yml
            LambdaTestConfig config = LambdaTestConfig.getInstance();
            Map<String, Object> sdkCapMap = config.getCapabilitiesFromYaml().asMap();
            Map<String, Object> userCapMap = capabilities.asMap();
                
            // Ensure lt:options exists
            Map<String, Object> ltOptions;
            if (userCapMap.containsKey("lt:options")) {
                ltOptions = (Map<String, Object>) userCapMap.get("lt:options");
            } else {
                ltOptions = new java.util.HashMap<>();
                capabilities.setCapability("lt:options", ltOptions);
            }
            
            // Merge SDK lt:options into user lt:options first
            if (sdkCapMap.containsKey("lt:options")) {
                Map<String, Object> sdkLtOptions = (Map<String, Object>) sdkCapMap.get("lt:options");
                ltOptions.putAll(sdkLtOptions);
            }
            
            // Add missing capabilities from SDK config, but filter out LT-specific keys
            // LT-specific keys (build, name, projectName, resolution) should ONLY be in lt:options
            for (Map.Entry<String, Object> entry : sdkCapMap.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                
                // Skip lt:options (already handled above)
                if ("lt:options".equals(key)) {
                    continue;
                }
                
                // Skip LT-specific keys - they should only be in lt:options, not at top level
                if (LT_SPECIFIC_KEYS.contains(key)) {
                    // Ensure they're in lt:options instead
                    if (!ltOptions.containsKey(key)) {
                        ltOptions.put(key, value);
                    }
                    continue;
                }
                
                // Selenium 3 legacy capabilities should be moved to lt:options when using Selenium 4
                // (Selenium 4 rejects these at top level)
                if (SELENIUM3_LEGACY_KEYS.contains(key)) {
                    // Skip empty string values (e.g., lambda:loadExtension: "")
                    if (value instanceof String && ((String) value).trim().isEmpty()) {
                        continue;
                    }
                    // Move to lt:options instead of top level
                    if (!ltOptions.containsKey(key)) {
                        ltOptions.put(key, value);
                    }
                    continue;
                }
                
                // Add valid W3C capabilities to top level
                if (!userCapMap.containsKey(key)) {
                    capabilities.setCapability(key, value);
                }
            }
            
            // Clean up: Remove any LT-specific keys that might have been set at top level
            // These should only be in lt:options for W3C compliance
            for (String ltKey : LT_SPECIFIC_KEYS) {
                if (capabilities.asMap().containsKey(ltKey)) {
                    Object ltValue = capabilities.getCapability(ltKey);
                    // Ensure it's in lt:options
                    if (!ltOptions.containsKey(ltKey)) {
                        ltOptions.put(ltKey, ltValue);
                    }
                    // Remove from top level by setting to null (safer than remove on potentially unmodifiable map)
                    try {
                        capabilities.setCapability(ltKey, (Object) null);
                    } catch (Exception e) {
                        // If setCapability with null doesn't work, try to remove from map
                        try {
                            if (capabilities.asMap() instanceof java.util.Map) {
                                ((java.util.Map<String, Object>) capabilities.asMap()).remove(ltKey);
                            }
                        } catch (Exception e2) {
                            // Ignore - capability might already be removed or map is unmodifiable
                        }
                    }
                }
            }
            
            // Clean up: Remove Selenium 3 legacy capabilities from top level (Selenium 4 rejects them)
            // Move them to lt:options instead
            for (String legacyKey : SELENIUM3_LEGACY_KEYS) {
                if (capabilities.asMap().containsKey(legacyKey)) {
                    Object legacyValue = capabilities.getCapability(legacyKey);
                    // Skip empty string values
                    if (legacyValue instanceof String && ((String) legacyValue).trim().isEmpty()) {
                        // Just remove from top level, don't add to lt:options
                        try {
                            capabilities.setCapability(legacyKey, (Object) null);
                        } catch (Exception e) {
                            try {
                                if (capabilities.asMap() instanceof java.util.Map) {
                                    ((java.util.Map<String, Object>) capabilities.asMap()).remove(legacyKey);
                                }
                            } catch (Exception e2) {
                                // Ignore
                            }
                        }
                        continue;
                    }
                    // Ensure it's in lt:options
                    if (!ltOptions.containsKey(legacyKey)) {
                        ltOptions.put(legacyKey, legacyValue);
                    }
                    // Remove from top level
                    try {
                        capabilities.setCapability(legacyKey, (Object) null);
                    } catch (Exception e) {
                        // If setCapability with null doesn't work, try to remove from map
                        try {
                            if (capabilities.asMap() instanceof java.util.Map) {
                                ((java.util.Map<String, Object>) capabilities.asMap()).remove(legacyKey);
                            }
                        } catch (Exception e2) {
                            // Ignore - capability might already be removed or map is unmodifiable
                        }
                    }
                }
            }
            
            // Ensure lt:options is updated in capabilities
            capabilities.setCapability("lt:options", ltOptions);
            
            // Check if tunnel is enabled in capabilities
            if (ltOptions.containsKey("tunnel")) {
                Object tunnelValue = ltOptions.get("tunnel");
                boolean tunnelInCapabilities = false;
                
                if (tunnelValue instanceof Boolean) {
                    tunnelInCapabilities = (Boolean) tunnelValue;
                } else if (tunnelValue instanceof String) {
                    tunnelInCapabilities = Boolean.parseBoolean((String) tunnelValue);
                }
                    
                if (tunnelInCapabilities) {
                    // Tunnel is enabled - check if it's running and add tunnel name
                    try {
                        com.lambdatest.selenium.tunnel.TunnelManager tunnelManager = 
                            com.lambdatest.selenium.tunnel.TunnelManager.getInstance();
                        
                        if (tunnelManager.isTunnelRunning()) {
                            String tunnelName = tunnelManager.getTunnelName();
                            if (tunnelName != null && !tunnelName.trim().isEmpty()) {
                                ltOptions.put("tunnelName", tunnelName);
                            }
                        } else {
                            // Tunnel configured but not running - warn once
                            if (!warnedAboutMissingTunnel) {
                                warnedAboutMissingTunnel = true;
                                System.err.println("[LambdaTest SDK] WARNING: tunnel=true in YAML but no tunnel is running.");
                                System.err.println("[LambdaTest SDK] Tests will continue without tunnel. This may cause connection issues for local resources.");
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("[LambdaTest SDK] Warning: Error checking tunnel status: " + e.getMessage());
                    }
                }
            }
            
        } catch (Exception e) {
            System.err.println("[LambdaTest SDK RemoteWebDriverAdvice] Error enhancing capabilities: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Always clear the re-entrance guard
            isEnhancing.set(false);
            
            // Log completion with thread info
            System.out.println(String.format(
                "[LambdaTest SDK RemoteWebDriverAdvice] Capability enhancement completed on thread %d/%s",
                Thread.currentThread().getId(), Thread.currentThread().getName()));
        }
    }
    
    /**
     * Set flag to indicate SDK is creating RemoteWebDriver internally.
     * This prevents recursive enhancement.
     */
    public static void setInternalDriverCreation(boolean value) {
        internalDriverCreation.set(value);
    }
    
    /**
     * Clean up all ThreadLocal variables to prevent memory leaks.
     */
    public static void cleanupThreadLocals() {
        try {
            internalDriverCreation.remove();
            isEnhancing.remove();
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }
}

