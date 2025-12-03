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
    private static final Set<String> LT_SPECIFIC_KEYS = Set.of("build", "name", "projectName", "resolution");

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
                    // Remove from top level
                    capabilities.asMap().remove(ltKey);
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

