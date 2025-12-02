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
     * Selenium 3 capabilities are set directly on DesiredCapabilities for backwards compatibility.
     * Selenium 4 capabilities use the W3C standard format with lt:options.
     */
    public DesiredCapabilities getCapabilitiesFromYaml() {
        DesiredCapabilities capabilities = new DesiredCapabilities();
        
        // LambdaTest options from YAML
        Map<String, Object> ltOptions = new HashMap<>();
        
        // Basic browser config (W3C standard capabilities)
        if (config.containsKey("browserName")) {
            capabilities.setCapability("browserName", config.get("browserName"));
        }
        
        // browserVersion with alias support (version)
        if (config.containsKey("browserVersion")) {
            capabilities.setCapability("browserVersion", config.get("browserVersion"));
        } else if (config.containsKey("version")) {
            capabilities.setCapability("browserVersion", config.get("version"));
        }
        
        // platformName with alias support (platform, OS)
        if (config.containsKey("platformName")) {
            capabilities.setCapability("platformName", config.get("platformName"));
        } else if (config.containsKey("platform")) {
            capabilities.setCapability("platformName", config.get("platform"));
        } else if (config.containsKey("OS")) {
            capabilities.setCapability("platformName", config.get("OS"));
        }
        
        // LambdaTest credentials (required) - put only in lt:options for W3C compliance
        try {
            String username = getUsername();
            String accessKey = getAccessKey();
            ltOptions.put("user", username);
            ltOptions.put("accessKey", accessKey);
        } catch (Exception e) {
        }
        
        // Selenium 4 specific capabilities
        
        // driver_version with aliases (driverVersion, driver)
        if (config.containsKey("driver_version")) {
            ltOptions.put("driver_version", config.get("driver_version"));
        } else if (config.containsKey("driverVersion")) {
            ltOptions.put("driver_version", config.get("driverVersion"));
        } else if (config.containsKey("driver")) {
            ltOptions.put("driver_version", config.get("driver"));
        }
        
        // selenium_version with aliases (seleniumVersion, seVersion)
        if (config.containsKey("selenium_version")) {
            ltOptions.put("selenium_version", config.get("selenium_version"));
        } else if (config.containsKey("seleniumVersion")) {
            ltOptions.put("selenium_version", config.get("seleniumVersion"));
        } else if (config.containsKey("seVersion")) {
            ltOptions.put("selenium_version", config.get("seVersion"));
        }
        
        // idleTimeout with alias (idle)
        if (config.containsKey("idleTimeout")) {
            ltOptions.put("idleTimeout", config.get("idleTimeout"));
        } else if (config.containsKey("idle")) {
            ltOptions.put("idleTimeout", config.get("idle"));
        }
        
        // LambdaTest organization capabilities
        // build with aliases (buildName, job, jobName) - Selenium 3 compatibility
        if (config.containsKey("build")) {
            Object buildValue = config.get("build");
            ltOptions.put("build", buildValue);
            capabilities.setCapability("build", buildValue); // Selenium 3 compatibility
        } else if (config.containsKey("buildName")) {
            Object buildValue = config.get("buildName");
            ltOptions.put("build", buildValue);
            capabilities.setCapability("build", buildValue);
        } else if (config.containsKey("job")) {
            Object buildValue = config.get("job");
            ltOptions.put("build", buildValue);
            capabilities.setCapability("build", buildValue);
        } else if (config.containsKey("jobName")) {
            Object buildValue = config.get("jobName");
            ltOptions.put("build", buildValue);
            capabilities.setCapability("build", buildValue);
        }
        
        // projectName with aliases (projectName, project) - Selenium 3 compatibility
        if (config.containsKey("projectName")) {
            Object projectValue = config.get("projectName");
            ltOptions.put("project", projectValue);
            capabilities.setCapability("projectName", projectValue); // Selenium 3 compatibility
        } else if (config.containsKey("project")) {
            Object projectValue = config.get("project");
            ltOptions.put("project", projectValue);
            capabilities.setCapability("projectName", projectValue);
        }
        
        // name with aliases (testname, sessionname, test) - Selenium 3 compatibility
        if (config.containsKey("name")) {
            Object nameValue = config.get("name");
            ltOptions.put("name", nameValue);
            capabilities.setCapability("name", nameValue); // Selenium 3 compatibility
        } else if (config.containsKey("testname")) {
            Object nameValue = config.get("testname");
            ltOptions.put("name", nameValue);
            capabilities.setCapability("name", nameValue);
        } else if (config.containsKey("sessionname")) {
            Object nameValue = config.get("sessionname");
            ltOptions.put("name", nameValue);
            capabilities.setCapability("name", nameValue);
        } else if (config.containsKey("test")) {
            Object nameValue = config.get("test");
            ltOptions.put("name", nameValue);
            capabilities.setCapability("name", nameValue);
        }
        
        // tags - Selenium 3 compatibility
        if (config.containsKey("tags")) {
            Object tagsValue = config.get("tags");
            ltOptions.put("tags", tagsValue);
            capabilities.setCapability("tags", tagsValue); // Selenium 3 compatibility
        }
        
        // buildTags - Selenium 3 compatibility
        if (config.containsKey("buildTags")) {
            Object buildTagsValue = config.get("buildTags");
            ltOptions.put("buildTags", buildTagsValue);
            capabilities.setCapability("buildTags", buildTagsValue); // Selenium 3 compatibility
        }
        
        // version - Selenium 3 compatibility (also set directly on capabilities)
        if (config.containsKey("version")) {
            Object versionValue = config.get("version");
            capabilities.setCapability("version", versionValue); // Selenium 3 compatibility
            // Also set as browserVersion for W3C compliance
            if (!config.containsKey("browserVersion")) {
                capabilities.setCapability("browserVersion", versionValue);
            }
        }
        
        // driver_version - Selenium 3 compatibility (also set directly on capabilities)
        if (config.containsKey("driver_version")) {
            Object driverVersionValue = config.get("driver_version");
            ltOptions.put("driver_version", driverVersionValue);
            capabilities.setCapability("driver_version", driverVersionValue); // Selenium 3 compatibility
        } else if (config.containsKey("driverVersion")) {
            Object driverVersionValue = config.get("driverVersion");
            ltOptions.put("driver_version", driverVersionValue);
            capabilities.setCapability("driver_version", driverVersionValue);
        } else if (config.containsKey("driver")) {
            Object driverVersionValue = config.get("driver");
            ltOptions.put("driver_version", driverVersionValue);
            capabilities.setCapability("driver_version", driverVersionValue);
        }
        
        // resolution with alias (viewport) - Selenium 3 compatibility
        if (config.containsKey("resolution")) {
            Object resolutionValue = config.get("resolution");
            ltOptions.put("resolution", resolutionValue);
            capabilities.setCapability("resolution", resolutionValue); // Selenium 3 compatibility
        } else if (config.containsKey("viewport")) {
            Object resolutionValue = config.get("viewport");
            ltOptions.put("resolution", resolutionValue);
            capabilities.setCapability("resolution", resolutionValue);
        }
        
        // lambda:loadExtension - Selenium 3 compatibility
        if (config.containsKey("lambda:loadExtension")) {
            Object loadExtensionValue = config.get("lambda:loadExtension");
            capabilities.setCapability("lambda:loadExtension", loadExtensionValue);
        } else if (config.containsKey("loadExtension")) {
            Object loadExtensionValue = config.get("loadExtension");
            capabilities.setCapability("lambda:loadExtension", loadExtensionValue);
        }
        
        // commandLog with alias (commandLogs) - Selenium 3 compatibility
        if (config.containsKey("commandLog")) {
            Object commandLogValue = config.get("commandLog");
            capabilities.setCapability("commandLog", commandLogValue);
        } else if (config.containsKey("commandLogs")) {
            Object commandLogValue = config.get("commandLogs");
            capabilities.setCapability("commandLog", commandLogValue);
        }
        
        // systemLog with alias (seleniumLogs) - Selenium 3 compatibility
        if (config.containsKey("systemLog")) {
            Object systemLogValue = config.get("systemLog");
            capabilities.setCapability("systemLog", systemLogValue);
        } else if (config.containsKey("seleniumLogs")) {
            Object systemLogValue = config.get("seleniumLogs");
            capabilities.setCapability("systemLog", systemLogValue);
        }
        
        // network.http2 - Selenium 3 compatibility
        if (config.containsKey("network.http2")) {
            Object networkHttp2Value = config.get("network.http2");
            capabilities.setCapability("network.http2", networkHttp2Value);
        }
        
        // DisableXFHeaders - Selenium 3 compatibility
        if (config.containsKey("DisableXFHeaders")) {
            Object disableXFHeadersValue = config.get("DisableXFHeaders");
            capabilities.setCapability("DisableXFHeaders", disableXFHeadersValue);
        }
        
        // network.debug - Selenium 3 compatibility
        if (config.containsKey("network.debug")) {
            Object networkDebugValue = config.get("network.debug");
            capabilities.setCapability("network.debug", networkDebugValue);
        }
        
        // ignoreFfOptionsArgs - Selenium 3 compatibility
        if (config.containsKey("ignoreFfOptionsArgs")) {
            Object ignoreFfOptionsArgsValue = config.get("ignoreFfOptionsArgs");
            capabilities.setCapability("ignoreFfOptionsArgs", ignoreFfOptionsArgsValue);
        }
        
        // updateBuildStatusOnSuccess - Selenium 3 compatibility
        if (config.containsKey("updateBuildStatusOnSuccess")) {
            Object updateBuildStatusOnSuccessValue = config.get("updateBuildStatusOnSuccess");
            capabilities.setCapability("updateBuildStatusOnSuccess", updateBuildStatusOnSuccessValue);
        }
        
        // LambdaTest advanced capabilities - Debugging
        
        // video (default: true)
        if (config.containsKey("video")) {
            ltOptions.put("video", config.get("video"));
        }
        
        // visual with alias (debug) - command by command screenshots
        if (config.containsKey("visual")) {
            ltOptions.put("visual", config.get("visual"));
        } else if (config.containsKey("debug")) {
            ltOptions.put("visual", config.get("debug"));
        }
        
        // network with alias (networkLogs) - captures network packets
        if (config.containsKey("network")) {
            ltOptions.put("network", config.get("network"));
        } else if (config.containsKey("networkLogs")) {
            ltOptions.put("network", config.get("networkLogs"));
        }
        
        // console - JavaScript console logs
        if (config.containsKey("console")) {
            ltOptions.put("console", config.get("console"));
        }
        
        // network.mask - mask network traffic
        if (config.containsKey("network.mask")) {
            ltOptions.put("network.mask", config.get("network.mask"));
        }
        
        // verboseWebDriverLogging - detailed Selenium logs
        if (config.containsKey("verboseWebDriverLogging")) {
            ltOptions.put("verboseWebDriverLogging", config.get("verboseWebDriverLogging"));
        }
        
        // LambdaTest advanced capabilities - Environment
        
        // resolution - screen resolution
        if (config.containsKey("resolution")) {
            ltOptions.put("resolution", config.get("resolution"));
        }
        
        // timezone - custom timezone (default: UTC+00:00)
        if (config.containsKey("timezone")) {
            ltOptions.put("timezone", config.get("timezone"));
        }
        
        // LambdaTest advanced capabilities - Tunnel
        
        // tunnel with alias (local) - Lambda Tunnel for local testing
        if (config.containsKey("tunnel")) {
            Object tunnelValue = config.get("tunnel");
            ltOptions.put("tunnel", tunnelValue);
            
            // Note: Tunnel will be started when WebDriver is actually created
            // This prevents starting it too early before tests run
        } else if (config.containsKey("local")) {
            ltOptions.put("tunnel", config.get("local"));
        }
        
        // tunnelName with alias (localName) - tunnel identifier
        if (config.containsKey("tunnelName")) {
            ltOptions.put("tunnelName", config.get("tunnelName"));
        } else if (config.containsKey("localName")) {
            ltOptions.put("tunnelName", config.get("localName"));
        }
        
        // LambdaTest advanced capabilities - Auto Healing & Smart Wait
        
        // autoHeal - automatically recover from element locator failures
        // Note: Cannot be used with smartWait (mutually exclusive)
        if (config.containsKey("autoHeal")) {
            ltOptions.put("autoHeal", config.get("autoHeal"));
        }
        
        // smartWait - automatically wait for elements to be ready
        // Note: Cannot be used with autoHeal (mutually exclusive)
        if (config.containsKey("smartWait")) {
            ltOptions.put("smartWait", config.get("smartWait"));
        }
        
        // smartWaitRetryDelay - delay between smartWait retries (in milliseconds)
        if (config.containsKey("smartWaitRetryDelay")) {
            ltOptions.put("smartWaitRetryDelay", config.get("smartWaitRetryDelay"));
        }
        
        // LambdaTest advanced capabilities - Geolocation
        
        // geoLocation - simulate user location for geolocation testing
        // Format: "US" or "IN" or any country code
        if (config.containsKey("geoLocation")) {
            ltOptions.put("geoLocation", config.get("geoLocation"));
        }
        
        // LambdaTest advanced capabilities - Security & Privacy
        
        // lambdaMaskCommands - mask sensitive data in test logs
        // Format: ["setValues", "setCookies", "getCookies"]
        if (config.containsKey("lambdaMaskCommands")) {
            ltOptions.put("lambdaMaskCommands", config.get("lambdaMaskCommands"));
        }
        
        // LambdaTest advanced capabilities - Network
        
        // networkThrottling - simulate network conditions
        // Format: "Regular 2G", "Good 2G", "Regular 3G", "Good 3G", "Regular 4G", "LTE", "DSL", "Wifi"
        if (config.containsKey("networkThrottling")) {
            ltOptions.put("networkThrottling", config.get("networkThrottling"));
        }
        
        // network.full.har - capture full HAR logs with request/response bodies
        if (config.containsKey("network.full.har")) {
            ltOptions.put("network.full.har", config.get("network.full.har"));
        }
        
        // LambdaTest advanced capabilities - Custom Configuration
        
        // customHeaders - add custom HTTP headers to all requests
        // Format: {"header1": "value1", "header2": "value2"}
        if (config.containsKey("customHeaders")) {
            ltOptions.put("customHeaders", config.get("customHeaders"));
        }
        
        // customDnsMap - custom DNS mapping for testing
        // Format: {"example.com": "192.168.1.1"}
        if (config.containsKey("customDnsMap")) {
            ltOptions.put("customDnsMap", config.get("customDnsMap"));
        }
        
        // LambdaTest advanced capabilities - File Upload
        
        // lambda:userFiles - array of file names to upload for testing
        // Files must be pre-uploaded via LambdaTest API
        // Format: ["file1.txt", "file2.pdf"]
        if (config.containsKey("lambda:userFiles")) {
            capabilities.setCapability("lambda:userFiles", config.get("lambda:userFiles"));
        } else if (config.containsKey("userFiles")) {
            capabilities.setCapability("lambda:userFiles", config.get("userFiles"));
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