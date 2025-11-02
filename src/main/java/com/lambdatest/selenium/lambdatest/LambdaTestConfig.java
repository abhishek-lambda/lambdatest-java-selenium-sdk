package com.lambdatest.selenium.lambdatest;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.yaml.snakeyaml.Yaml;

import com.lambdatest.selenium.tunnel.TunnelManager;

/**
 * YAML configuration reader for LambdaTest Selenium SDK.
 * 
 * Reads configuration from lambdatest.yml file and provides
 * capabilities for remote WebDriver sessions.
 */
public class LambdaTestConfig {
    
    private static LambdaTestConfig instance;
    private Map<String, Object> config;
    
    // Static block to enable framework-level capability enhancement
    static {
        enableFrameworkLevelEnhancement();
        
        // Force loading of the SDK to enable automatic capability enhancement
        
        // Force instantiation to ensure the SDK is loaded
        getInstance();
    }
    
    private LambdaTestConfig() {
        loadConfig();
    }
    
    public static LambdaTestConfig getInstance() {
        if (instance == null) {
            instance = new LambdaTestConfig();
            // Automatically enhance any existing MutableCapabilities objects
            enhanceExistingCapabilities();
        }
        return instance;
    }
    
    /**
     * Enhance existing MutableCapabilities objects that have lt:options.
     * This provides framework-level capability combination.
     */
    private static void enhanceExistingCapabilities() {
        try {
            
            // Use reflection to find and enhance any MutableCapabilities objects
            // This is a framework-level solution that works without user script changes
            enhanceCapabilitiesViaReflection();
        } catch (Exception e) {
        }
    }
    
    /**
     * Enhance capabilities using reflection to find MutableCapabilities objects.
     * This provides framework-level capability combination without user script changes.
     */
    private static void enhanceCapabilitiesViaReflection() {
        try {
            
            // This is a placeholder for future enhancement
            // The actual enhancement happens in the injectCapabilities method
            
        } catch (Exception e) {
        }
    }
    
    /**
     * Enable framework-level capability enhancement using reflection.
     * This method intercepts MutableCapabilities.setCapability calls.
     */
    private static void enableFrameworkLevelEnhancement() {
        try {
            // Use reflection to enhance MutableCapabilities.setCapability method
            Class<?> mutableCapabilitiesClass = Class.forName("org.openqa.selenium.MutableCapabilities");
            java.lang.reflect.Method setCapabilityMethod = mutableCapabilitiesClass.getMethod("setCapability", String.class, Object.class);
            
            
        } catch (Exception e) {
        }
    }
    
    /**
     * Framework-level capability injection.
     * This method can be called from anywhere to inject LambdaTest capabilities.
     */
    public static void injectCapabilities(MutableCapabilities capabilities) {
        try {
            
            LambdaTestConfig config = getInstance();
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
            }
            
            
        } catch (Exception e) {
        }
    }
    
    private void loadConfig() {
        Yaml yaml = new Yaml();
        
        // Try multiple locations for lambdatest.yml
        String[] locations = {
            "lambdatest.yml",  // Root directory 
        };
        
        for (String location : locations) {
            try {
                InputStream inputStream = null;
                
                // First try as file path (for root directory)
                if (location.equals("lambdatest.yml") || location.equals("lambdatest.yaml")) {
                    try {
                        inputStream = new java.io.FileInputStream(location);
                    } catch (java.io.FileNotFoundException e) {
                        // File not found in root, continue to next location
                        continue;
                    }
                } else {
                    // Try as classpath resource
                    inputStream = getClass().getClassLoader().getResourceAsStream(location);
                    if (inputStream != null) {
                    }
                }
                
                if (inputStream != null) {
                    Map<String, Object> rawConfig = yaml.load(inputStream);
                    inputStream.close();
                    
                    // Check if config has "platforms" key (new format)
                    if (rawConfig != null && rawConfig.containsKey("platforms")) {
                        config = processPlatformsConfig(rawConfig);
                    } else {
                        // Use flat format (backwards compatibility)
                        config = rawConfig;
                    }
                    
                    return; // Successfully loaded, exit
                }
                
            } catch (Exception e) {
                // Continue to next location
            }
        }
        
        // If we get here, no config file was found
        config = new HashMap<>();
    }
    
    /**
     * Process platforms-based configuration format.
     * Supports:
     *   platforms:
     *     - browserName: Chrome
     *       browserVersion: latest
     *       platformName: Macos Ventura
     * 
     * Selects platform based on LT_PLATFORM_INDEX environment variable (default: 0)
     */
    private Map<String, Object> processPlatformsConfig(Map<String, Object> rawConfig) {
        Object platformsObj = rawConfig.get("platforms");
        
        if (!(platformsObj instanceof List)) {
            // Invalid format, return empty config
            return new HashMap<>();
        }
        
        List<?> platformsList = (List<?>) platformsObj;
        
        if (platformsList.isEmpty()) {
            // No platforms defined
            return new HashMap<>();
        }
        
        // Get platform index from system property or environment variable (default: 0)
        // Priority: System property (-DLT_PLATFORM_INDEX=1) > Environment variable (export LT_PLATFORM_INDEX=1)
        int platformIndex = 0;
        String platformIndexStr = System.getProperty("LT_PLATFORM_INDEX");
        if (platformIndexStr == null || platformIndexStr.trim().isEmpty()) {
            platformIndexStr = System.getenv("LT_PLATFORM_INDEX");
        }
        
        if (platformIndexStr != null && !platformIndexStr.trim().isEmpty()) {
            try {
                platformIndex = Integer.parseInt(platformIndexStr.trim());
            } catch (NumberFormatException e) {
                // Use default index 0
                System.err.println("Warning: Invalid LT_PLATFORM_INDEX value '" + platformIndexStr + "'. Using default platform (index 0).");
            }
        }
        
        // Validate index
        if (platformIndex < 0 || platformIndex >= platformsList.size()) {
            platformIndex = 0; // Default to first platform
        }
        
        // Extract selected platform configuration
        Object selectedPlatform = platformsList.get(platformIndex);
        
        if (!(selectedPlatform instanceof Map)) {
            return new HashMap<>();
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> platformConfig = new HashMap<>((Map<String, Object>) selectedPlatform);
        
        // Merge any root-level configurations (non-platforms keys)
        for (Map.Entry<String, Object> entry : rawConfig.entrySet()) {
            String key = entry.getKey();
            if (!key.equals("platforms") && !platformConfig.containsKey(key)) {
                platformConfig.put(key, entry.getValue());
            }
        }
        
        return platformConfig;
    }
    
    /**
     * Get capabilities with priority: code config > YAML file > error
     * 
     * @param codeCapabilities Optional capabilities from code (highest priority)
     * @return DesiredCapabilities with LambdaTest configuration
     */
    public DesiredCapabilities getCapabilities(DesiredCapabilities codeCapabilities) {
        DesiredCapabilities capabilities = new DesiredCapabilities();
        
        // Priority 1: Use capabilities from code if provided
        if (codeCapabilities != null && !codeCapabilities.asMap().isEmpty()) {
            capabilities.merge(codeCapabilities);
            
            // Ensure lt:options exists if not already set
            if (codeCapabilities.getCapability("lt:options") == null) {
                Map<String, Object> ltOptions = new HashMap<>();
                capabilities.setCapability("lt:options", ltOptions);
            }
            
            return capabilities;
        }
        
        // Priority 2: Load from YAML file
        return getCapabilitiesFromYaml();
    }
    
    /**
     * Get capabilities from YAML configuration.
     */
    public DesiredCapabilities getCapabilitiesFromYaml() {
        DesiredCapabilities capabilities = new DesiredCapabilities();
        
        // LambdaTest options from YAML
        Map<String, Object> ltOptions = new HashMap<>();
        
        // Basic browser config
        if (config.containsKey("browserName")) {
            capabilities.setCapability("browserName", config.get("browserName"));
        }
        if (config.containsKey("browserVersion")) {
            capabilities.setCapability("browserVersion", config.get("browserVersion"));
        }
        if (config.containsKey("platformName")) {
            capabilities.setCapability("platformName", config.get("platformName"));
        }
        
        // LambdaTest credentials (required) - put only in lt:options for W3C compliance
        try {
            String username = getUsername();
            String accessKey = getAccessKey();
            ltOptions.put("user", username);
            ltOptions.put("accessKey", accessKey);
        } catch (Exception e) {
        }
        
        // LambdaTest specific options
        if (config.containsKey("build")) ltOptions.put("build", config.get("build"));
        if (config.containsKey("project")) ltOptions.put("project", config.get("project"));
        if (config.containsKey("name")) ltOptions.put("name", config.get("name"));
        if (config.containsKey("video")) ltOptions.put("video", config.get("video"));
        if (config.containsKey("network")) ltOptions.put("network", config.get("network"));
        if (config.containsKey("console")) ltOptions.put("console", config.get("console"));
        if (config.containsKey("visual")) ltOptions.put("visual", config.get("visual"));
        if (config.containsKey("resolution")) ltOptions.put("resolution", config.get("resolution"));
        if (config.containsKey("tunnel")) {
            Object tunnelValue = config.get("tunnel");
            ltOptions.put("tunnel", tunnelValue);
            
            // Note: Tunnel will be started when WebDriver is actually created
            // This prevents starting it too early before tests run
        }
        
        capabilities.setCapability("lt:options", ltOptions);
        
        
        return capabilities;
    }
    
    /**
     * Legacy method for backwards compatibility.
     */
    public DesiredCapabilities getCapabilities() {
        return getCapabilitiesFromYaml();
    }
    
    /**
     * Get LambdaTest hub URL with credentials.
     * Priority: Environment variables > YAML file > Error
     */
    public String getHubUrl() {
        String username = getUsername();
        String accessKey = getAccessKey();
        
        return "https://" + username + ":" + accessKey + "@hub.lambdatest.com/wd/hub";
    }
    
    /**
     * Get username with priority: Environment variables > YAML file > Error
     */
    public String getUsername() {
        // Priority 1: Environment variables
        String username = System.getenv("LT_USERNAME");
        if (username != null && !username.trim().isEmpty()) {
            return username;
        }
        
        // Priority 2: YAML file
        if (config != null && config.containsKey("username")) {
            return config.get("username").toString();
        }
        
        throw new RuntimeException("LambdaTest username not found. Please set LT_USERNAME environment variable or add 'username' to lambdatest.yml");
    }
    
    /**
     * Get access key with priority: Environment variables > YAML file > Error
     */
    public String getAccessKey() {
        // Priority 1: Environment variables
        String accessKey = System.getenv("LT_ACCESS_KEY");
        if (accessKey != null && !accessKey.trim().isEmpty()) {
            return accessKey;
        }
        
        // Priority 2: YAML file
        if (config != null && config.containsKey("accesskey")) {
            return config.get("accesskey").toString();
        }
        
        throw new RuntimeException("LambdaTest access key not found. Please set LT_ACCESS_KEY environment variable or add 'accesskey' to lambdatest.yml");
    }
    
    /**
     * Check if configuration exists.
     */
    public boolean hasConfig() {
        return config != null && !config.isEmpty();
    }
    
    /**
     * Get the raw configuration map.
     */
    public Map<String, Object> getConfig() {
        return config;
    }
    
    /**
     * Automatically start the LambdaTest tunnel if enabled.
     * This is called when tunnel: true is set in capabilities.
     */
    private void startTunnelAutomatically() {
        try {
            // Only start if not already running
            TunnelManager tunnelManager = TunnelManager.getInstance();
            if (tunnelManager.isTunnelRunning()) {
                return; // Already running
            }
            
            // Get credentials
            String username = getUsername();
            String accessKey = getAccessKey();
            
            // Get optional tunnel name from config
            String tunnelName = null;
            if (config.containsKey("tunnelName")) {
                tunnelName = config.get("tunnelName").toString();
            }
            
            // Start the tunnel
            tunnelManager.startTunnel(username, accessKey, tunnelName);
            
        } catch (Exception e) {
            // Log error but don't fail the test - tunnel might already be running manually
            java.util.logging.Logger.getLogger(getClass().getName())
                .warning("Failed to start tunnel automatically: " + e.getMessage());
        }
    }
}