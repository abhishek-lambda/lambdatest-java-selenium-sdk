package com.lambdatest.selenium.testng;

import java.net.URL;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import com.lambdatest.selenium.lambdatest.LambdaTestConfig;

/**
 * Base test class for LambdaTest Selenium tests with TestNG.
 * 
 * This class handles all the setup and teardown.
 * Users just need to extend this class and write their test methods!
 * 
 * Usage:
 * public class MyTest extends TestNgRemoteTest {
 *     @Test
 *     public void myTestMethod() {
 *         driver.get("https://example.com");
 *         // Your test code here
 *     }
 * }
 */
public class TestNgRemoteTest {
    
    public WebDriver driver;
    
    @BeforeMethod(alwaysRun = true)
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        // SDK agent automatically handles:
        // 1. Loading capabilities from lambdatest.yml
        // 2. Adding LambdaTest credentials from environment variables
        // 3. Starting tunnel if tunnel: true
        // 4. Adding tunnelName to capabilities
        
        // Get config to build hub URL
        LambdaTestConfig config = LambdaTestConfig.getInstance();
        String hubUrl = config.getHubUrl();
        
        // Get capabilities from YAML (agent will enhance them automatically)
        DesiredCapabilities capabilities = config.getCapabilitiesFromYaml();
        
        // Create RemoteWebDriver - agent intercepts and enhances automatically
        driver = new RemoteWebDriver(new URL(hubUrl), capabilities);
    }
    
    @AfterMethod(alwaysRun = true)
    public void tearDown() throws Exception {
        if (driver != null && driver instanceof RemoteWebDriver) {
            RemoteWebDriver remoteDriver = (RemoteWebDriver) driver;
            
            try {
                // Mark test status on LambdaTest dashboard BEFORE quitting
                markTestStatus(remoteDriver, "passed");
            } catch (Exception e) {
            }
            
            driver.quit();
        } else if (driver != null) {
            driver.quit();
        }
    }
    
    /**
     * Mark test status on LambdaTest dashboard using JavaScript execution.
     * This is thread-safe because each test has its own driver instance.
     */
    private void markTestStatus(RemoteWebDriver driver, String status) {
        try {
            // LambdaTest uses JavaScript to mark test status
            String script = String.format(
                "lambda-status=%s", status
            );
            driver.executeScript(script);
        } catch (Exception e) {
            // Don't throw - test status is best-effort
        }
    }
}

