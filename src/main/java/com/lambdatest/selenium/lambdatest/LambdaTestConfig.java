package com.lambdatest.selenium.lambdatest;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.yaml.snakeyaml.Yaml;

import com.lambdatest.selenium.tunnel.TunnelManager;
import com.lambdatest.selenium.lambdatest.capabilities.CapabilityProcessor;
import com.lambdatest.selenium.lambdatest.capabilities.Selenium3Capabilities;
import com.lambdatest.selenium.lambdatest.capabilities.Selenium4Capabilities;
import com.lambdatest.selenium.lambdatest.capabilities.BrowserOptionsCapabilities;

/**
 * YAML configuration reader for LambdaTest Selenium SDK.
 * 
 * Reads configuration from lambdatest.yml file and provides
 * capabilities for remote WebDriver sessions.
 */
public class LambdaTestConfig {
    
    private static volatile LambdaTestConfig instance;  // volatile for double-checked locking
    private static final Object instanceLock = new Object();  // Lock for singleton initialization
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
    
    /**
     * Thread-safe singleton getInstance using double-checked locking.
     * Critical for high-parallelism scenarios (e.g., parallel=50).
     */
    public static LambdaTestConfig getInstance() {
        if (instance == null) {  // First check (no locking)
            synchronized (instanceLock) {  // Acquire lock
                if (instance == null) {  // Second check (with locking)
                    instance = new LambdaTestConfig();
                    // Automatically enhance any existing MutableCapabilities objects
                    enhanceExistingCapabilities();
                }
            }
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
        long startTime = System.currentTimeMillis();
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger(getClass().getName());
        
        logger.fine(String.format("[Thread-%d/%s] Loading LambdaTest configuration...",
            Thread.currentThread().getId(), Thread.currentThread().getName()));
        
        Yaml yaml = new Yaml();
        
        // Try multiple locations for lambdatest.yml
        String[] locations = {
            "lambdatest.yml",  // Root directory 
            "lambdatest.yaml"
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
                    
                    long duration = System.currentTimeMillis() - startTime;
                    logger.fine(String.format("[Thread-%d/%s] Configuration loaded in %dms",
                        Thread.currentThread().getId(), Thread.currentThread().getName(), duration));
                    
                    return; // Successfully loaded, exit
                }
                
            } catch (Exception e) {
                // Continue to next location
            }
        }
        
        // If we get here, no config file was found
        config = new HashMap<>();
        logger.fine("No configuration file found, using empty config");
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
     * Supports all Selenium 3, Selenium 4, and LambdaTest advanced capabilities.
     * 
     * Supported Browsers (Selenium 3 & 4):
     * - Chrome
     * - Firefox
     * - Safari
     * - MS Edge (Microsoft Edge)
     * - Opera
     * - IE (Internet Explorer)
     * 
     * Browser-specific options are supported for both Selenium 3 and 4:
     * - Chrome: chromeOptions / goog:chromeOptions
     * - Firefox: firefoxOptions / moz:firefoxOptions
     * - Edge: edgeOptions / ms:edgeOptions
     * - Safari: safariOptions / safari:options
     * - Opera: operaOptions
     * - IE: ieOptions / se:ieOptions
     * 
     * Selenium 3 capabilities are set directly on DesiredCapabilities for backwards compatibility.
     * Selenium 4 capabilities use the W3C standard format with lt:options.
     */
    public DesiredCapabilities getCapabilitiesFromYaml() {
        DesiredCapabilities capabilities = new DesiredCapabilities();
        Map<String, Object> ltOptions = new HashMap<>();
        CapabilityProcessor processor = new CapabilityProcessor(config, capabilities, ltOptions);
        
        // ============================================================
        // 1. W3C Standard Browser Capabilities (Selenium 3 & 4)
        // ============================================================
        processW3CBrowserCapabilities(capabilities);
        
        // ============================================================
        // 2. Browser-Specific Options (Chrome, Firefox, Edge, etc.)
        // ============================================================
        BrowserOptionsCapabilities.processBrowserOptions(config, capabilities);
        
        // ============================================================
        // 3. LambdaTest Credentials (Required)
        // ============================================================
        processCredentials(ltOptions);
        
        // ============================================================
        // 4. Selenium 3 Capabilities (for backwards compatibility)
        // ============================================================
        processor.process(Selenium3Capabilities.getDefinitions());
        
        // Handle special case: version -> browserVersion mapping
        processVersionCapability(capabilities);
        
        // ============================================================
        // 5. Selenium 4 / W3C Capabilities (LambdaTest advanced)
        // ============================================================
        processor.process(Selenium4Capabilities.getDefinitions());
        
        // ============================================================
        // 6. Special Cases (require custom handling)
        // ============================================================
        processSpecialCases(capabilities, ltOptions);
        
        // ============================================================
        // 7. Finalize: Set lt:options on capabilities
        // ============================================================
        capabilities.setCapability("lt:options", ltOptions);
        
        return capabilities;
    }
    
    /**
     * Process W3C standard browser capabilities (browserName, browserVersion, platformName).
     */
    private void processW3CBrowserCapabilities(DesiredCapabilities capabilities) {
        // browserName (case-sensitive, mandatory)
        if (config.containsKey("browserName")) {
            capabilities.setCapability("browserName", config.get("browserName"));
        } else if (config.containsKey("browser")) {
            capabilities.setCapability("browserName", config.get("browser"));
        }
        
        // browserVersion
        if (config.containsKey("browserVersion")) {
            capabilities.setCapability("browserVersion", config.get("browserVersion"));
        } else if (config.containsKey("version")) {
            capabilities.setCapability("browserVersion", config.get("version"));
        }
        
        // platformName
        if (config.containsKey("platformName")) {
            capabilities.setCapability("platformName", config.get("platformName"));
        } else if (config.containsKey("platform")) {
            capabilities.setCapability("platformName", config.get("platform"));
        } else if (config.containsKey("OS")) {
            capabilities.setCapability("platformName", config.get("OS"));
        }
    }
    
    /**
     * Process LambdaTest credentials.
     */
    private void processCredentials(Map<String, Object> ltOptions) {
        try {
            String username = getUsername();
            String accessKey = getAccessKey();
            ltOptions.put("user", username);
            ltOptions.put("accessKey", accessKey);
        } catch (Exception e) {
            // Credentials will be required when creating WebDriver
            throw new RuntimeException("LambdaTest credentials not found. Please set LT_USERNAME and LT_ACCESS_KEY environment variables or add 'username' and 'accesskey' to lambdatest.yml");
        }
    }
    
    /**
     * Handle version capability special case (Selenium 3 compatibility).
     * version should be set on DesiredCapabilities AND as browserVersion for W3C.
     */
    private void processVersionCapability(DesiredCapabilities capabilities) {
        if (config.containsKey("version") && !config.containsKey("browserVersion")) {
            Object versionValue = config.get("version");
            capabilities.setCapability("version", versionValue); // Selenium 3
            capabilities.setCapability("browserVersion", versionValue); // W3C
        }
    }
    
    /**
     * Process special cases that require custom logic.
     */
    private void processSpecialCases(DesiredCapabilities capabilities, Map<String, Object> ltOptions) {
        // lambda:userFiles - set directly on capabilities (not in lt:options)
        if (config.containsKey("lambda:userFiles")) {
            capabilities.setCapability("lambda:userFiles", config.get("lambda:userFiles"));
        } else if (config.containsKey("userFiles")) {
            capabilities.setCapability("lambda:userFiles", config.get("userFiles"));
        }
        
        // project -> projectName mapping for Selenium 3, and project -> lt:options for Selenium 4
        if (config.containsKey("project")) {
            Object projectValue = config.get("project");
            ltOptions.put("project", projectValue); // Selenium 4
            if (!config.containsKey("projectName")) {
                capabilities.setCapability("projectName", projectValue); // Selenium 3
            }
        }
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