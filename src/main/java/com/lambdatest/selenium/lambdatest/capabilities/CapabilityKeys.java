package com.lambdatest.selenium.lambdatest.capabilities;

/**
 * Common capability key constants used across Selenium 3, Selenium 4, and LambdaTest configuration.
 * This centralizes capability string definitions to avoid duplication and ensure consistency.
 */
public final class CapabilityKeys {
    
    // Private constructor to prevent instantiation
    private CapabilityKeys() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
    
    // W3C Browser Capabilities
    public static final String BROWSER_NAME = "browserName";
    public static final String BROWSER = "browser";
    public static final String BROWSER_VERSION = "browserVersion";
    public static final String VERSION = "version";
    public static final String PLATFORM_NAME = "platformName";
    public static final String PLATFORM = "platform";
    public static final String OS = "OS";
    
    // LambdaTest Options
    public static final String LT_OPTIONS = "lt:options";
    
    // Organization capabilities
    public static final String BUILD = "build";
    public static final String BUILD_NAME = "buildName";
    public static final String JOB = "job";
    public static final String JOB_NAME = "jobName";
    public static final String NAME = "name";
    public static final String TESTNAME = "testname";
    public static final String SESSIONNAME = "sessionname";
    public static final String TEST = "test";
    public static final String TAGS = "tags";
    public static final String BUILD_TAGS = "buildTags";
    public static final String PROJECT = "project";
    public static final String PROJECT_NAME = "projectName";
    
    // Driver and version capabilities
    public static final String DRIVER_VERSION = "driver_version";
    public static final String DRIVER_VERSION_ALIAS = "driverVersion";
    public static final String DRIVER = "driver";
    
    // Resolution
    public static final String RESOLUTION = "resolution";
    public static final String VIEWPORT = "viewport";
    
    // Extension loading
    public static final String LAMBDA_LOAD_EXTENSION = "lambda:loadExtension";
    public static final String LOAD_EXTENSION = "loadExtension";
    
    // Logging capabilities
    public static final String COMMAND_LOG = "commandLog";
    public static final String COMMAND_LOGS = "commandLogs";
    public static final String SYSTEM_LOG = "systemLog";
    public static final String SELENIUM_LOGS = "seleniumLogs";
    
    // Network capabilities
    public static final String NETWORK_HTTP2 = "network.http2";
    public static final String DISABLE_XF_HEADERS = "DisableXFHeaders";
    public static final String NETWORK_DEBUG = "network.debug";
    public static final String IGNORE_FF_OPTIONS_ARGS = "ignoreFfOptionsArgs";
    public static final String UPDATE_BUILD_STATUS_ON_SUCCESS = "updateBuildStatusOnSuccess";
    
    // User files
    public static final String LAMBDA_USER_FILES = "lambda:userFiles";
    public static final String USER_FILES = "userFiles";
    
    // Tunnel
    public static final String TUNNEL_NAME = "tunnelName";
    
    // Credentials
    public static final String USER = "user";
    public static final String ACCESS_KEY = "accessKey";
    public static final String USERNAME = "username";
    public static final String ACCESSKEY = "accesskey";
    
    // Environment Variables
    public static final String ENV_LT_USERNAME = "LT_USERNAME";
    public static final String ENV_LT_ACCESS_KEY = "LT_ACCESS_KEY";
    public static final String ENV_LT_PLATFORM_INDEX = "LT_PLATFORM_INDEX";
    
    // Config Keys
    public static final String PLATFORMS = "platforms";
    
    // Config File Names
    public static final String CONFIG_FILE_YML = "lambdatest.yml";
    public static final String CONFIG_FILE_YAML = "lambdatest.yaml";
    
    // Hub URL
    public static final String HUB_URL_PREFIX = "https://";
    public static final String HUB_URL_SUFFIX = "@hub.lambdatest.com/wd/hub";
    public static final String HUB_URL_SEPARATOR = ":";
}

