# LambdaTest Selenium Java SDK

[![Maven Central](https://img.shields.io/maven-central/v/io.github.lambdatest/lambdatest-selenium-java-sdk.svg)](https://central.sonatype.com/artifact/io.github.lambdatest/lambdatest-selenium-java-sdk)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

A powerful Java SDK for seamlessly integrating Selenium tests with the LambdaTest cloud platform. This SDK provides automatic capability injection, test status management, and simplified configuration for running Selenium tests on LambdaTest's cloud infrastructure.

## Features

✨ **Automatic Capability Injection** - No need to manually configure LambdaTest capabilities  
🔧 **Java Agent Support** - Bytecode instrumentation for seamless integration  
📊 **Test Status Management** - Automatically mark tests as passed/failed on LambdaTest  
🎯 **Framework Support** - Works with TestNG, JUnit 5, and plain Selenium tests  
🌐 **Tunnel Management** - Built-in support for LambdaTest Tunnel  
⚙️ **YAML Configuration** - Simple YAML-based configuration  
🚀 **Zero Code Changes** - Just add the agent, no changes to existing tests  

## Installation

### Maven

Add the following dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>io.github.lambdatest</groupId>
    <artifactId>lambdatest-selenium-java-sdk</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle

Add the following to your `build.gradle`:

```gradle
dependencies {
    implementation 'io.github.lambdatest:lambdatest-selenium-java-sdk:1.0.0'
}
```

### Gradle Kotlin DSL

Add the following to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.github.lambdatest:lambdatest-selenium-java-sdk:1.0.0")
}
```

## Quick Start

### 1. Configuration

Create a `lambdatest.yaml` file in your project root:

```yaml
# LambdaTest credentials
username: YOUR_LAMBDATEST_USERNAME
accessKey: YOUR_LAMBDATEST_ACCESS_KEY

# Browser capabilities
capabilities:
  browserName: chrome
  browserVersion: latest
  platformName: Windows 10
  
# Test configuration
testName: My Selenium Test
build: Build #1
project: My Project
```

### 2. Using the Java Agent (Recommended)

The easiest way to use this SDK is with the Java agent, which automatically instruments your Selenium tests:

**Maven:**

```bash
mvn test -DargLine="-javaagent:/path/to/lambdatest-selenium-java-sdk-1.0.0-agent.jar"
```

**Gradle:**

```bash
./gradlew test -Djvmargs="-javaagent:/path/to/lambdatest-selenium-java-sdk-1.0.0-agent.jar"
```

**IDE (IntelliJ IDEA / Eclipse):**

Add VM option: `-javaagent:/path/to/lambdatest-selenium-java-sdk-1.0.0-agent.jar`

### 3. Using Programmatically (Alternative)

If you prefer not to use the agent, you can use the SDK programmatically:

```java
import com.lambdatest.selenium.LambdaTestRemoteTest;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.testng.annotations.Test;

public class MySeleniumTest extends LambdaTestRemoteTest {
    
    @Test
    public void testGoogle() {
        WebDriver driver = getDriver(); // Automatically configured for LambdaTest
        
        driver.get("https://www.google.com");
        System.out.println("Title: " + driver.getTitle());
        
        // Test will be automatically marked as passed/failed
    }
}
```

## Configuration Options

### YAML Configuration File

Create `lambdatest.yaml` in your project root:

```yaml
# Authentication (Required)
username: YOUR_USERNAME
accessKey: YOUR_ACCESS_KEY

# Or use environment variables:
# username: ${LT_USERNAME}
# accessKey: ${LT_ACCESS_KEY}

# Capabilities (Optional - will be merged with test capabilities)
capabilities:
  browserName: chrome
  browserVersion: latest
  platformName: Windows 10
  resolution: 1920x1080
  
# LambdaTest Options
ltOptions:
  build: Build #1
  project: My Project
  network: true
  video: true
  console: true
  visual: true
  
# Tunnel Configuration (Optional)
tunnel:
  enabled: true
  name: my-tunnel
  
# Grid Configuration
gridUrl: https://hub.lambdatest.com/wd/hub
```

### Environment Variables

You can also configure using environment variables:

- `LT_USERNAME` - LambdaTest username
- `LT_ACCESS_KEY` - LambdaTest access key
- `LT_GRID_URL` - Grid URL (default: https://hub.lambdatest.com/wd/hub)

## Supported Capabilities

This SDK supports **all Selenium 4 and LambdaTest advanced capabilities**. See [CAPABILITIES.md](CAPABILITIES.md) for comprehensive documentation.

### Quick Reference

**Selenium 4 Capabilities:**
- `browserName`, `browserVersion` (alias: `version`), `platformName` (alias: `platform`, `OS`)
- `driver_version` (aliases: `driverVersion`, `driver`)
- `selenium_version` (aliases: `seleniumVersion`, `seVersion`)
- `idleTimeout` (alias: `idle`)

**Test Organization:**
- `build`, `project`, `name`

**Debugging Capabilities:**
- `video` (default: true) - Video recording
- `visual` (alias: `debug`) - Command-by-command screenshots
- `network` (alias: `networkLogs`) - Network packet capture
- `console` - JavaScript console logs
- `verboseWebDriverLogging` - Detailed Selenium logs
- `network.mask` - Mask network traffic for security

**Environment:**
- `resolution` - Screen resolution (e.g., "1920x1080")
- `timezone` - Custom timezone (e.g., "UTC+05:30")

**Local Testing:**
- `tunnel` (alias: `local`) - Enable Lambda Tunnel
- `tunnelName` (alias: `localName`) - Tunnel identifier

**Advanced Testing Capabilities:**
- `autoHeal` - Automatically recover from element locator failures ([Learn more](https://www.lambdatest.com/support/docs/auto-healing/))
- `smartWait` - Automatically wait for elements to be ready (mutually exclusive with autoHeal) ([Learn more](https://www.lambdatest.com/support/docs/smart-wait/))
- `smartWaitRetryDelay` - Delay between smartWait retries in milliseconds
- `geoLocation` - Simulate user location (e.g., "US", "IN") ([Learn more](https://www.lambdatest.com/support/docs/selenium-geolocation-capabilities/))
- `lambdaMaskCommands` - Mask sensitive data in logs (e.g., ["setValues", "setCookies", "getCookies"]) ([Learn more](https://www.lambdatest.com/support/docs/selenium-mask-your-data/))
- `networkThrottling` - Simulate network conditions (e.g., "Regular 3G", "Good 4G") ([Learn more](https://www.lambdatest.com/support/docs/network-throttling/))
- `customHeaders` - Add custom HTTP headers to all requests ([Learn more](https://www.lambdatest.com/support/docs/custom-headers/))
- `customDnsMap` - Custom DNS mapping for testing ([Learn more](https://www.lambdatest.com/support/docs/custom-dns-map/))
- `network.full.har` - Capture full HAR logs with request/response bodies ([Learn more](https://www.lambdatest.com/support/docs/har-log-viewer-with-waterfall/))
- `lambda:userFiles` (alias: `userFiles`) - Pre-uploaded files for testing ([Learn more](https://www.lambdatest.com/support/docs/upload-files-using-lambdatest/))

### Example Configuration

```yaml
# Authentication
username: "your_username"
accesskey: "your_accesskey"

# Browser Configuration
browserName: "Chrome"
browserVersion: "latest"  # or "latest-1", "101.0", etc.
platformName: "Windows 10"

# Test Organization
build: "Selenium 4 Build"
project: "My Project"
name: "Sample Test"

# Debugging (increases test time)
video: true
visual: false
network: false
console: false
verboseWebDriverLogging: false

# Environment
resolution: "1920x1080"
timezone: "UTC+05:30"

# Performance
idleTimeout: 600
selenium_version: "4.15.0"

# Local Testing
tunnel: false
tunnelName: "my-tunnel"

# Advanced Testing Capabilities
autoHeal: false  # Note: Cannot be used with smartWait
smartWait: false  # Note: Cannot be used with autoHeal
smartWaitRetryDelay: 1000  # milliseconds
geoLocation: "US"  # Country code for geolocation testing
networkThrottling: "Regular 3G"  # Simulate network conditions
network.full.har: true  # Capture full HAR logs

# Security & Privacy
lambdaMaskCommands:  # Mask sensitive data in logs
  - setValues
  - setCookies
  - getCookies

# Custom Configuration
customHeaders:  # Add custom HTTP headers
  X-Custom-Header: "value"
  Authorization: "Bearer token"

customDnsMap:  # Custom DNS mapping
  example.com: "192.168.1.1"
  test.local: "127.0.0.1"

# File Upload (files must be pre-uploaded via LambdaTest API)
lambda:userFiles:
  - "sample.pdf"
  - "test-image.jpg"
```

### Advanced Capabilities Examples

**Auto Healing - Automatically recover from element locator failures:**

```yaml
autoHeal: true
```

This feature helps tests automatically adapt when element locators change. Note: Cannot be used together with `smartWait`.

**Smart Wait - Automatically wait for elements:**

```yaml
smartWait: true
smartWaitRetryDelay: 500  # Retry every 500ms
```

Automatically waits for elements to be ready. Note: Cannot be used together with `autoHeal`.

**Geolocation Testing:**

```yaml
geoLocation: "IN"  # Simulate location from India
timezone: "Asia/Kolkata"
```

**Network Throttling - Simulate different network conditions:**

```yaml
networkThrottling: "Regular 4G"
# Options: "Regular 2G", "Good 2G", "Regular 3G", "Good 3G", "Regular 4G", "LTE", "DSL", "Wifi"
```

**Masking Sensitive Data in Logs:**

```yaml
lambdaMaskCommands:
  - setValues      # Hide sendKeys input
  - setCookies     # Hide cookies being set
  - getCookies     # Hide cookies being retrieved
```

**Custom Headers for API Testing:**

```yaml
customHeaders:
  X-API-Key: "your-api-key"
  Authorization: "Bearer token"
  Custom-Header: "custom-value"
```

**Custom DNS Mapping:**

```yaml
customDnsMap:
  internal.example.com: "192.168.1.100"
  staging.myapp.com: "10.0.0.50"
```

**Full HAR Logs with Request/Response Bodies:**

```yaml
network: true
network.full.har: true
```

For detailed documentation with examples, see [CAPABILITIES.md](CAPABILITIES.md).

**Reference Documentation:**
- [Selenium 4 Capabilities](https://www.lambdatest.com/support/docs/selenium-four-capabilities/)
- [LambdaTest Advanced Capabilities](https://www.lambdatest.com/support/docs/lambdatest-selenium-advance-capabilities/)

## Framework Integration

### TestNG

Add TestNG listener for automatic test status updates:

```xml
<!-- testng.xml -->
<suite name="LambdaTest Suite">
    <listeners>
        <listener class-name="com.lambdatest.selenium.LambdaTestStatusListener"/>
    </listeners>
    
    <test name="My Tests">
        <classes>
            <class name="com.example.MyTest"/>
        </classes>
    </test>
</suite>
```

Or programmatically:

```java
@Listeners(LambdaTestStatusListener.class)
public class MyTest {
    // Your tests
}
```

### JUnit 5

Use the JUnit transformer with the Java agent (automatically detected).

## Advanced Usage

### Tunnel Management

Enable LambdaTest Tunnel for testing local/private applications:

```yaml
tunnel:
  enabled: true
  name: my-tunnel
  # Additional tunnel options
  tunnelName: custom-tunnel
  verbose: true
```

### Custom Capabilities

Merge custom capabilities with configured ones:

```java
import com.lambdatest.selenium.LambdaTestCapabilities;
import org.openqa.selenium.chrome.ChromeOptions;

ChromeOptions options = new ChromeOptions();
options.addArguments("--start-maximized");

// SDK will merge these with lambdatest.yaml capabilities
LambdaTestCapabilities.enhance(options);
```

### Parallel Execution

The SDK fully supports parallel test execution:

**TestNG:**

```xml
<suite name="Parallel Suite" parallel="tests" thread-count="5">
    <test name="Chrome Test">
        <parameter name="browser" value="chrome"/>
        <classes><class name="com.example.Test1"/></classes>
    </test>
    <test name="Firefox Test">
        <parameter name="browser" value="firefox"/>
        <classes><class name="com.example.Test1"/></classes>
    </test>
</suite>
```

## Building from Source

### Prerequisites

- Java 8 or higher
- Gradle 7.0+

### Build

```bash
# Clone the repository
git clone https://github.com/LambdatestIncPrivate/lambdatest-selenium-java-sdk.git
cd lambdatest-selenium-java-sdk

# Build the project
./gradlew clean build

# Generated artifacts will be in build/libs/
# - lambdatest-selenium-java-sdk-1.0.0.jar (main JAR)
# - lambdatest-selenium-java-sdk-1.0.0-agent.jar (agent JAR with dependencies)
```

### Publishing

For maintainers publishing to Maven Central:

```bash
# See MAVEN_CENTRAL_PUBLISHING.md for detailed instructions
./verify-setup.sh          # Verify publishing prerequisites
./gradlew publishToMavenLocal  # Test local publishing
./gradlew publishMavenJavaPublicationToOSSRHRepository  # Publish to Maven Central
```

## Examples

### Basic Test

```java
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import java.net.URL;

public class BasicTest {
    WebDriver driver;
    
    @BeforeMethod
    public void setup() throws Exception {
        ChromeOptions options = new ChromeOptions();
        options.setCapability("platformName", "Windows 10");
        options.setCapability("browserVersion", "latest");
        
        // SDK will automatically inject LambdaTest capabilities
        driver = new RemoteWebDriver(
            new URL("https://hub.lambdatest.com/wd/hub"), 
            options
        );
    }
    
    @Test
    public void testExample() {
        driver.get("https://www.example.com");
        String title = driver.getTitle();
        System.out.println("Page title: " + title);
        assert title.contains("Example");
    }
    
    @AfterMethod
    public void teardown() {
        if (driver != null) {
            driver.quit();
        }
    }
}
```

### Cross-Browser Testing

```java
import org.testng.annotations.*;

public class CrossBrowserTest {
    
    @Parameters({"browser", "version", "platform"})
    @BeforeMethod
    public void setup(String browser, String version, String platform) {
        // SDK automatically configures based on parameters
    }
    
    @Test
    public void testAcrossBrowsers() {
        // Your test code
    }
}
```

## Troubleshooting

### Common Issues

**Issue: Driver not connecting to LambdaTest**
- Verify credentials in `lambdatest.yaml` or environment variables
- Check your LambdaTest account has active minutes
- Ensure grid URL is correct

**Issue: Java agent not working**
- Verify agent JAR path is correct
- Use the `-agent` classifier JAR (with all dependencies)
- Check Java version compatibility (Java 8+)

**Issue: Tests not marked as passed/failed**
- Ensure TestNG listener is configured
- Verify driver session ID is available
- Check network connectivity to LambdaTest

### Enable Debug Logging

Add to your test:

```java
System.setProperty("lambdatest.debug", "true");
```

## Requirements

- **Java**: 8 or higher
- **Selenium**: 4.x (tested with 4.15.0)
- **TestNG**: 7.4.0+ (optional, for TestNG integration)
- **JUnit**: 5.10.0+ (optional, for JUnit integration)

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Support

- 📧 Email: support@lambdatest.com
- 📚 Documentation: https://www.lambdatest.com/support/docs/
- 💬 Community: https://community.lambdatest.com/
- 🐛 Issues: [GitHub Issues](https://github.com/LambdatestIncPrivate/lambdatest-selenium-java-sdk/issues)

## Links

- [LambdaTest Platform](https://www.lambdatest.com/)
- [LambdaTest Documentation](https://www.lambdatest.com/support/docs/)
- [Maven Central Repository](https://central.sonatype.com/artifact/io.github.lambdatest/lambdatest-selenium-java-sdk)

---

Made with ❤️ by [LambdaTest](https://www.lambdatest.com/)
