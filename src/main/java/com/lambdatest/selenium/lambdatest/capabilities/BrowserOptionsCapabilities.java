package com.lambdatest.selenium.lambdatest.capabilities;

import java.util.Map;

import org.openqa.selenium.remote.DesiredCapabilities;

/**
 * Handles browser-specific options (Chrome, Firefox, Edge, Safari, Opera, IE).
 * These need special handling because they have different keys for Selenium 3 and 4.
 */
public class BrowserOptionsCapabilities {

    /**
     * Process browser-specific options from config.
     * Sets both Selenium 3 and Selenium 4 keys for compatibility.
     */
    public static void processBrowserOptions(Map<String, Object> config, DesiredCapabilities capabilities) {
        // Chrome
        processBrowserOption(config, capabilities, "chromeOptions", "goog:chromeOptions");

        // Firefox
        processBrowserOption(config, capabilities, "firefoxOptions", "moz:firefoxOptions");

        // Edge
        processBrowserOption(config, capabilities, "edgeOptions", "ms:edgeOptions");

        // Safari
        processBrowserOption(config, capabilities, "safariOptions", "safari:options");

        // Opera (no W3C namespace, same for both)
        processBrowserOption(config, capabilities, "operaOptions", "operaOptions");

        // Internet Explorer
        processBrowserOption(config, capabilities, "ieOptions", "se:ieOptions", "IEOptions");
    }

    /**
     * Process a browser option with Selenium 3 and 4 compatibility.
     */
    private static void processBrowserOption(Map<String, Object> config, DesiredCapabilities capabilities,
                                           String selenium3Key, String selenium4Key) {
        processBrowserOption(config, capabilities, selenium3Key, selenium4Key, selenium3Key);
    }

    /**
     * Process a browser option with custom Selenium 3 key.
     */
    private static void processBrowserOption(Map<String, Object> config, DesiredCapabilities capabilities,
                                           String selenium3Key, String selenium4Key, String selenium3TargetKey) {
        Object options = null;

        // Check Selenium 3 key first
        if (config.containsKey(selenium3Key)) {
            options = config.get(selenium3Key);
        } 
        // Then check Selenium 4 key
        else if (config.containsKey(selenium4Key)) {
            options = config.get(selenium4Key);
        }

        if (options != null) {
            // Set both keys for compatibility
            capabilities.setCapability(selenium3TargetKey, options); // Selenium 3
            capabilities.setCapability(selenium4Key, options);      // Selenium 4 W3C
        }
    }
}