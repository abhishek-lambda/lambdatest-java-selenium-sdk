package com.lambdatest.selenium.agent;

import java.net.URL;
import java.util.Map;

import org.openqa.selenium.Capabilities;
import org.openqa.selenium.MutableCapabilities;

import com.lambdatest.selenium.lambdatest.LambdaTestConfig;

import net.bytebuddy.asm.Advice;

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

    @Advice.OnMethodEnter
    static void onEnter(@Advice.Argument(0) URL hubUrl, @Advice.Argument(1) Capabilities capabilities) {
        
        
        // Check if capabilities can be modified
        if (!(capabilities instanceof MutableCapabilities)) {
            return;
        }
        
        MutableCapabilities mutableCapabilities = (MutableCapabilities) capabilities;
        
        // Only intercept if it's a LambdaTest URL or if lt:options is present
        boolean isLambdaTestUrl = hubUrl.toString().contains("lambdatest.com");
        boolean hasLtOptions = capabilities.asMap().containsKey("lt:options");
        
        if (isLambdaTestUrl || hasLtOptions) {
            
            try {
                // Check if SDK capabilities are stored by TestNG listener
                String storedCapabilities = System.getProperty("lambdatest.sdk.capabilities");
                if (storedCapabilities != null) {
                    
                    // Load configuration from lambdatest.yml
                    LambdaTestConfig config = LambdaTestConfig.getInstance();
                    Map<String, Object> sdkCapMap = config.getCapabilitiesFromYaml().asMap();
                    Map<String, Object> userCapMap = mutableCapabilities.asMap();
                    
                    // Add missing capabilities from SDK config
                    for (Map.Entry<String, Object> entry : sdkCapMap.entrySet()) {
                        String key = entry.getKey();
                        Object value = entry.getValue();
                        
                        if (!userCapMap.containsKey(key)) {
                            mutableCapabilities.setCapability(key, value);
                        }
                    }
                    
                    // Ensure lt:options contains credentials
                    if (userCapMap.containsKey("lt:options")) {
                        Map<String, Object> ltOptions = (Map<String, Object>) userCapMap.get("lt:options");
                        if (sdkCapMap.containsKey("lt:options")) {
                            Map<String, Object> sdkLtOptions = (Map<String, Object>) sdkCapMap.get("lt:options");
                            ltOptions.putAll(sdkLtOptions);
                        }
                    }
                    
                    
                } else {
                }
                
            } catch (Exception e) {
            }
        } else {
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
     * Static method to enhance capabilities that can be called from ASM bytecode.
     * This is the ACTUAL method that gets called by the agent's bytecode transformation.
     */
    public static void enhanceCapabilities(MutableCapabilities capabilities) {
        // Skip if this is an internal SDK driver creation (e.g., from ChromeDriverAdvice)
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
        
        // Set re-entrance guard
        isEnhancing.set(true);
        
        try {
        
            // Load configuration from lambdatest.yml
            LambdaTestConfig config = LambdaTestConfig.getInstance();
            Map<String, Object> sdkCapMap = config.getCapabilitiesFromYaml().asMap();
            Map<String, Object> userCapMap = capabilities.asMap();
                
                // Add missing capabilities from SDK config
                for (Map.Entry<String, Object> entry : sdkCapMap.entrySet()) {
                    String key = entry.getKey();
                    Object value = entry.getValue();
                    
                    if (!userCapMap.containsKey(key)) {
                        capabilities.setCapability(key, value);
                    }
                }
                
                // Ensure lt:options contains credentials
                if (userCapMap.containsKey("lt:options")) {
                    Map<String, Object> ltOptions = (Map<String, Object>) userCapMap.get("lt:options");
                    if (sdkCapMap.containsKey("lt:options")) {
                        Map<String, Object> sdkLtOptions = (Map<String, Object>) sdkCapMap.get("lt:options");
                        ltOptions.putAll(sdkLtOptions);
                    }
                    
                // Check if tunnel is enabled in YAML/capabilities only (no environment variable check)
                if (ltOptions.containsKey("tunnel")) {
                    Object tunnelValue = ltOptions.get("tunnel");
                    boolean tunnelInCapabilities = false;
                    
                    if (tunnelValue instanceof Boolean) {
                        tunnelInCapabilities = (Boolean) tunnelValue;
                    } else if (tunnelValue instanceof String) {
                        tunnelInCapabilities = Boolean.parseBoolean((String) tunnelValue);
                    }
                        
                    if (tunnelInCapabilities) {
                        // Tunnel is enabled in YAML - check if it's running and add tunnel name
                        try {
                            com.lambdatest.selenium.tunnel.TunnelManager tunnelManager = 
                                com.lambdatest.selenium.tunnel.TunnelManager.getInstance();
                            
                            if (tunnelManager.isTunnelRunning()) {
                                String tunnelName = tunnelManager.getTunnelName();
                                if (tunnelName != null && !tunnelName.trim().isEmpty()) {
                                    ltOptions.put("tunnelName", tunnelName);
                                }
                            } else {
                                // Tunnel configured in YAML but not running - warn but allow test to continue
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
                }
            
        } catch (Exception e) {
            System.err.println("[LambdaTest SDK RemoteWebDriverAdvice] Error enhancing capabilities: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Always clear the re-entrance guard and clean up ThreadLocal
            isEnhancing.set(false);
            // Clean up ThreadLocal to prevent memory leaks in long-running applications
            // Note: We don't remove internalDriverCreation here as it might still be needed
        }
    }
    
    /**
     * Clean up all ThreadLocal variables to prevent memory leaks.
     * Should be called after all tests complete.
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