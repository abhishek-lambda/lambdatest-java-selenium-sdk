package com.lambdatest.selenium.tunnel;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.json.JSONObject;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;

/**
 * Automatic tunnel binary manager for LambdaTest.
 * Downloads and manages the LambdaTest tunnel binary similar to BrowserStack SDK.
 * 
 * This class handles:
 * - Automatic binary download
 * - Starting tunnel process
 * - Stopping tunnel process
 * - Platform detection (Windows/Mac/Linux)
 */
public class TunnelManager {
    
    private static final Logger LOGGER = Logger.getLogger(TunnelManager.class.getName());
    private static final String TUNNEL_VERSION = "v3";
    private static final String TUNNEL_DIR = System.getProperty("user.home") + "/.lambdatest-tunnel";
    private static final String TUNNEL_INFO_API = "/api/v1.0/info";
    private static final int MAX_TUNNEL_STARTUP_RETRY_COUNT = 20;
    private static final int TUNNEL_INITIAL_WAIT_AND_RETRY_BACKOFF = 3000; // 3 seconds
    private static final int HTTP_TIMEOUT = 5000; // 5 seconds
    
    private static TunnelManager instance;
    
    private Process tunnelProcess;
    private String tunnelBinaryPath;
    private boolean isRunning = false;
    private boolean shutdownHookRegistered = false;
    private String username;
    private String accessKey;
    private String tunnelName;
    private int infoAPIPort;
    private Integer tunnelID;
    
    /**
     * Get singleton instance of TunnelManager.
     */
    public static synchronized TunnelManager getInstance() {
        if (instance == null) {
            instance = new TunnelManager();
        }
        return instance;
    }
    
    private TunnelManager() {
        // Create tunnel directory if it doesn't exist
        try {
            Files.createDirectories(Paths.get(TUNNEL_DIR));
        } catch (IOException e) {
            LOGGER.warning("Failed to create tunnel directory: " + e.getMessage());
        }
    }
    
    /**
     * Start the LambdaTest tunnel.
     * Downloads binary if needed and starts the tunnel process.
     * 
     * @param username LambdaTest username
     * @param accessKey LambdaTest access key
     * @param tunnelName Optional tunnel name
     * @throws TunnelException if tunnel fails to start
     */
    public void startTunnel(String username, String accessKey, String tunnelName) throws TunnelException {
        // Only synchronize for the initial check to avoid holding lock during wait
        synchronized(this) {
            if (isRunning) {
                LOGGER.info("Tunnel is already running");
                return;
            }
        }
        
        if (username == null || username.trim().isEmpty() || accessKey == null || accessKey.trim().isEmpty()) {
            throw new TunnelException("Username or access key cannot be null or empty");
        }
        
        this.username = username;
        this.accessKey = accessKey;
        
        // Auto-generate tunnel name if not provided
        if (tunnelName == null || tunnelName.trim().isEmpty()) {
            this.tunnelName = "lt-java-sdk-tunnel-" + System.currentTimeMillis();
        } else {
            this.tunnelName = tunnelName;
        }
        
        // Find an available port for the Info API
        try {
            this.infoAPIPort = findAvailablePort();
        } catch (IOException e) {
            throw new TunnelException("Failed to find available port for tunnel Info API: " + e.getMessage(), e);
        }
        
        try {
            // Ensure tunnel binary exists first (synchronously to catch early errors)
            ensureTunnelBinary();
            
            // Start tunnel in a background thread to avoid blocking agent initialization
            final Object tunnelReadyLock = new Object();
            final boolean[] tunnelReady = {false};
            final Exception[] tunnelError = {null};
            
            Thread tunnelThread = new Thread(() -> {
                try {
                    LOGGER.fine("Background thread: Starting tunnel process...");
                    
                    // Start the tunnel process
                    startTunnelProcess();
                    
                    LOGGER.fine("Background thread: Tunnel process started successfully");
                    
                    // Register shutdown hook to stop tunnel
                    registerShutdownHook();
                    
                    LOGGER.fine("Background thread: Shutdown hook registered");
                    
                    synchronized(TunnelManager.this) {
                        isRunning = true;
                    }
                    
                    LOGGER.info("Background thread: Notifying main thread that tunnel is ready");
                    
                    // Notify waiting threads that tunnel is ready
                    synchronized(tunnelReadyLock) {
                        tunnelReady[0] = true;
                        tunnelReadyLock.notifyAll();
                    }
                    
                    LOGGER.fine("Background thread: Notification sent successfully");
                } catch (Exception e) {
                    LOGGER.severe("Background thread: Exception occurred: " + e.getMessage());
                    synchronized(TunnelManager.this) {
                        isRunning = false;
                    }
                    synchronized(tunnelReadyLock) {
                        tunnelError[0] = e;
                        tunnelReadyLock.notifyAll();
                    }
                    LOGGER.severe("Failed to start tunnel: " + e.getMessage());
                    e.printStackTrace();
                }
            });
            
            tunnelThread.setDaemon(true);
            tunnelThread.setName("LambdaTest-Tunnel-Thread");
            tunnelThread.start();
            
            LOGGER.info("Main thread: Waiting for tunnel to be ready...");
            
            // Wait for tunnel to start
            // Note: Background thread has its own timeout logic (max 60 seconds)
            // We add a buffer to allow it to complete or report errors
            synchronized(tunnelReadyLock) {
                long timeout = 90000; // 90 seconds (allows 60s for connection + 30s buffer)
                long startWait = System.currentTimeMillis();
                
                while (!tunnelReady[0] && tunnelError[0] == null) {
                    try {
                        LOGGER.fine("Main thread: Entering wait state...");
                        tunnelReadyLock.wait(timeout);
                        long elapsed = System.currentTimeMillis() - startWait;
                        LOGGER.fine("Main thread: Woken up after " + elapsed + "ms");
                        break; // Exit loop after notification or timeout
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new TunnelException("Interrupted while waiting for tunnel to start");
                    }
                }
                
                if (tunnelError[0] != null) {
                    LOGGER.severe("Main thread: Received error notification from background thread");
                    throw new TunnelException("Tunnel failed to start", tunnelError[0]);
                }
                
                if (!tunnelReady[0]) {
                    LOGGER.severe("Main thread: Timeout - tunnel not ready after " + (timeout / 1000) + " seconds");
                    throw new TunnelException("Tunnel start timed out after " + (timeout / 1000) + " seconds");
                }
                
                LOGGER.info("Main thread: Tunnel is ready!");
            }
            
        } catch (Exception e) {
            synchronized(this) {
                isRunning = false;
            }
            throw new TunnelException("Failed to start tunnel: " + e.getMessage(), e);
        }
    }
    
    /**
     * Stop the LambdaTest tunnel.
     */
    public synchronized void stopTunnel() {
        if (!isRunning) {
            return;
        }
        
        try {
            if (tunnelProcess != null && tunnelProcess.isAlive()) {
                tunnelProcess.destroy();
                
                // Wait a bit for graceful shutdown
                boolean terminated = tunnelProcess.waitFor(5, TimeUnit.SECONDS);
                
                if (!terminated) {
                    tunnelProcess.destroyForcibly();
                }
                
                tunnelProcess = null;
            }
            
            isRunning = false;
            
        } catch (Exception e) {
            LOGGER.warning("Error stopping tunnel: " + e.getMessage());
        }
    }
    
    /**
     * Check if tunnel is running.
     */
    public synchronized boolean isTunnelRunning() {
        return isRunning && tunnelProcess != null && tunnelProcess.isAlive();
    }
    
    /**
     * Get the tunnel name being used.
     * 
     * @return tunnel name or null if not set
     */
    public String getTunnelName() {
        return tunnelName;
    }
    
    /**
     * Get the tunnel ID assigned by LambdaTest.
     * 
     * @return tunnel ID or null if tunnel is not running or ID not available
     */
    public Integer getTunnelID() {
        return tunnelID;
    }
    
    /**
     * Ensure the tunnel binary exists, download if necessary.
     */
    private void ensureTunnelBinary() throws TunnelException {
        String binaryName = getBinaryNameForPlatform();
        tunnelBinaryPath = TUNNEL_DIR + "/" + binaryName;
        
        File binary = new File(tunnelBinaryPath);
        
        if (binary.exists() && binary.canExecute()) {
            return;
        }
        
        // Download the binary - only log if actually downloading
        System.out.println("Downloading LambdaTest tunnel binary...");
        downloadTunnelBinary(binaryName);
        
        // Make executable on Unix systems
        if (isUnixPlatform()) {
            try {
                Files.setPosixFilePermissions(Paths.get(tunnelBinaryPath),
                    java.util.Set.of(PosixFilePermission.OWNER_READ, 
                                    PosixFilePermission.OWNER_WRITE, 
                                    PosixFilePermission.OWNER_EXECUTE));
            } catch (IOException e) {
                LOGGER.warning("Failed to set executable permissions: " + e.getMessage());
            }
        }
    }
    
    /**
     * Download the tunnel binary ZIP for the current platform.
     */
    private void downloadTunnelBinary(String binaryName) throws TunnelException {
        String downloadUrl = getDownloadUrl(binaryName);
        String zipPath = TUNNEL_DIR + "/tunnel.zip";
        
        try {
            // Downloading from URL (suppressed to reduce logs)
            
            URL url = new URL(downloadUrl);
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
            
            // Set user agent to avoid 403 errors
            connection.setRequestProperty("User-Agent", "LambdaTestSeleniumSDK/1.0.0");
            connection.setConnectTimeout(30000); // 30 seconds
            connection.setReadTimeout(30000); // 30 seconds
            
            int responseCode = connection.getResponseCode();
            // HTTP Response Code (suppressed to reduce logs)
            
            if (responseCode == 403) {
                throw new TunnelException("403 Forbidden: Access denied. This might be due to network restrictions or firewall settings. " +
                    "Please whitelist downloads.lambdatest.com in your network/firewall. " +
                    "You may also download the tunnel binary manually from: " + downloadUrl);
            }
            
            if (responseCode != 200) {
                throw new TunnelException("Failed to download tunnel binary. HTTP Response Code: " + responseCode);
            }
            
            // Download ZIP file
            try (InputStream in = connection.getInputStream();
                 OutputStream out = new FileOutputStream(zipPath)) {
                
                byte[] buffer = new byte[4096];
                int bytesRead;
                long totalBytes = 0;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    totalBytes += bytesRead;
                }
                // Downloaded bytes (suppressed to reduce logs)
            }
            
            // Extract ZIP file
            // Extracting (suppressed to reduce logs)
            try {
                extractZipFile(zipPath, TUNNEL_DIR);
            } catch (ZipException e) {
                throw new TunnelException("Failed to extract tunnel binary from ZIP. " + 
                    "Error: " + e.getMessage(), e);
            }
            
            // Clean up ZIP file
            new File(zipPath).delete();
            
            System.out.println("Tunnel binary downloaded successfully");
            
        } catch (java.net.UnknownHostException e) {
            throw new TunnelException("Network error: Cannot reach LambdaTest servers. " +
                "Please check your internet connection and ensure downloads.lambdatest.com is accessible.", e);
        } catch (java.net.SocketTimeoutException e) {
            throw new TunnelException("Download timeout: The connection to LambdaTest servers timed out. " +
                "Please check your network connection and try again.", e);
        } catch (IOException e) {
            throw new TunnelException("Failed to download tunnel binary from " + downloadUrl + 
                ". Error: " + e.getMessage() + 
                ". You can download it manually from: " + downloadUrl, e);
        }
    }
    
    /**
     * Extract ZIP file to destination directory.
     */
    private void extractZipFile(String zipPath, String destDir) throws ZipException {
        ZipFile zipFile = new ZipFile(zipPath);
        zipFile.extractAll(destDir);
    }
    
    /**
     * Get download URL for the current platform.
     */
    private String getDownloadUrl(String binaryName) {
        // LambdaTest tunnel binary download URLs (ZIP format)
        // Reference: Based on lambdatest-maven-tunnel implementation
        
        String arch = System.getProperty("os.arch").toLowerCase();
        
        if (isWindowsPlatform()) {
            // For Windows: 64-bit or 32-bit ZIP
            if (arch.contains("64")) {
                return "https://downloads.lambdatest.com/tunnel/v3/windows/64bit/LT_Windows.zip";
            } else {
                return "https://downloads.lambdatest.com/tunnel/v3/windows/32bit/LT_Windows.zip";
            }
        } else if (isMacPlatform()) {
            // For Mac: ARM64 (Apple Silicon) or x64 (Intel) ZIP
            if (arch.contains("arm") || arch.contains("aarch")) {
                return "https://downloads.lambdatest.com/tunnel/v3/mac/arm64/LT_Mac.zip";
            } else {
                return "https://downloads.lambdatest.com/tunnel/v3/mac/64bit/LT_Mac.zip";
            }
        } else {
            // For Linux: ARM64 or x64 ZIP
            if (arch.contains("arm") || arch.contains("aarch")) {
                return "https://downloads.lambdatest.com/tunnel/v3/linux/arm64/LT_Linux.zip";
            } else {
                return "https://downloads.lambdatest.com/tunnel/v3/linux/64bit/LT_Linux.zip";
            }
        }
    }
    
    /**
     * Get binary name for the current platform.
     */
    private String getBinaryNameForPlatform() {
        if (isWindowsPlatform()) {
            return "LT.exe";
        } else {
            return "LT";
        }
    }
    
    /**
     * Start the tunnel process and wait for it to be fully connected.
     */
    private void startTunnelProcess() throws TunnelException {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder();
            
            // Build command
            if (isWindowsPlatform()) {
                processBuilder.command("cmd", "/c", tunnelBinaryPath,
                    "--user", username,
                    "--key", accessKey,
                    "--infoAPIPort", String.valueOf(infoAPIPort));
            } else {
                processBuilder.command(tunnelBinaryPath,
                    "--user", username,
                    "--key", accessKey,
                    "--infoAPIPort", String.valueOf(infoAPIPort));
            }
            
            // Add tunnel name (always set now - either user-provided or auto-generated)
            if (this.tunnelName != null && !this.tunnelName.trim().isEmpty()) {
                processBuilder.command().add("--tunnelName");
                processBuilder.command().add(this.tunnelName);
            }
            
            // Redirect output
            File logFile = new File(TUNNEL_DIR + "/tunnel.log");
            processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile));
            processBuilder.redirectError(ProcessBuilder.Redirect.appendTo(logFile));
            
            // Clear or create the log file to start fresh
            if (logFile.exists()) {
                // Truncate log file to avoid reading old connection messages
                try (FileOutputStream fos = new FileOutputStream(logFile, false)) {
                    // Just opening in overwrite mode clears it
                }
            }
            
            // Start the process
            tunnelProcess = processBuilder.start();
            
            LOGGER.info("Tunnel process started, waiting for connection to establish...");
            
            // Wait for tunnel to be ready by polling the Info API
            waitForTunnelToStart();
            
            LOGGER.info("Tunnel successfully connected and ready" + 
                (tunnelID != null ? " (Tunnel ID: " + tunnelID + ")" : ""));
            
        } catch (Exception e) {
            throw new TunnelException("Failed to start tunnel process: " + e.getMessage(), e);
        }
    }
    
    /**
     * Wait for tunnel to establish connection by polling the Info API.
     * This is more reliable than parsing log files.
     * 
     * @throws TunnelException if connection is not established within timeout
     */
    private void waitForTunnelToStart() throws TunnelException {
        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(HTTP_TIMEOUT)
                .setSocketTimeout(HTTP_TIMEOUT)
                .setConnectionRequestTimeout(HTTP_TIMEOUT)
                .build();
        
        try (CloseableHttpClient httpClient = HttpClientBuilder.create()
                .setDefaultRequestConfig(config).build()) {
            
            for (int retryCount = 0; retryCount < MAX_TUNNEL_STARTUP_RETRY_COUNT; retryCount++) {
                try {
                    // Wait before checking (3 seconds)
                    Thread.sleep(TUNNEL_INITIAL_WAIT_AND_RETRY_BACKOFF);
                    
                    // Check if process is still alive
                    if (tunnelProcess == null || !tunnelProcess.isAlive()) {
                        String recentLog = getRecentTunnelLog();
                        throw new TunnelException("Tunnel process died unexpectedly. Recent log:\n" + 
                            (recentLog != null ? recentLog : "No log available"));
                    }
                    
                    LOGGER.fine("Checking tunnel status (attempt " + (retryCount + 1) + "/" + 
                        MAX_TUNNEL_STARTUP_RETRY_COUNT + ")...");
                    
                    String infoAPIGetEndpoint = "http://127.0.0.1:" + infoAPIPort + TUNNEL_INFO_API;
                    HttpGet httpGet = new HttpGet(infoAPIGetEndpoint);
                    
                    try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                        if (response.getStatusLine().getStatusCode() == 200) {
                            LOGGER.fine("Received tunnel status response");
                            
                            BufferedReader reader = new BufferedReader(
                                new InputStreamReader(response.getEntity().getContent()));
                            StringBuilder responseBuilder = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) {
                                responseBuilder.append(line);
                            }
                            
                            JSONObject tunnelInfo = new JSONObject(responseBuilder.toString());
                            if (tunnelInfo.getString("status").equals("SUCCESS")) {
                                LOGGER.info("Tunnel started successfully");
                                
                                // Extract tunnel ID if available
                                if (tunnelInfo.has("data") && tunnelInfo.getJSONObject("data") != null 
                                    && tunnelInfo.getJSONObject("data").has("id")) {
                                    tunnelID = tunnelInfo.getJSONObject("data").getInt("id");
                                }
                                
                                return; // Tunnel is ready!
                            } else {
                                LOGGER.fine("Tunnel status: " + tunnelInfo.getString("status"));
                            }
                        } else {
                            LOGGER.fine("Tunnel status response code: " + 
                                response.getStatusLine().getStatusCode());
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new TunnelException("Interrupted while waiting for tunnel to start");
                } catch (Exception e) {
                    LOGGER.fine("Tunnel not yet started. Retrying... (" + e.getMessage() + ")");
                }
            }
            
            // If we got here, tunnel didn't start within timeout
            String recentLog = getRecentTunnelLog();
            throw new TunnelException("Tunnel connection timeout after " + 
                (MAX_TUNNEL_STARTUP_RETRY_COUNT * TUNNEL_INITIAL_WAIT_AND_RETRY_BACKOFF / 1000) + 
                " seconds. Check log: " + TUNNEL_DIR + "/tunnel.log\n" +
                "Recent log:\n" + (recentLog != null ? recentLog : "No log available"));
            
        } catch (IOException e) {
            throw new TunnelException("Failed to check tunnel status via Info API: " + e.getMessage(), e);
        }
    }
    
    /**
     * Register shutdown hook to stop tunnel on JVM exit.
     * Only registers once to avoid duplicate hooks.
     */
    private synchronized void registerShutdownHook() {
        if (shutdownHookRegistered) {
            // Shutdown hook already registered
            return;
        }
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            // Shutting down tunnel (suppressed to reduce logs)
            stopTunnel();
        }));
        
        shutdownHookRegistered = true;
        // Shutdown hook registered
    }
    
    /**
     * Find an available port for the tunnel Info API.
     * 
     * @return An available port number
     * @throws IOException if unable to find an available port
     */
    private int findAvailablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }
    
    /**
     * Get recent tunnel log for error debugging.
     */
    private String getRecentTunnelLog() {
        try {
            File logFile = new File(TUNNEL_DIR + "/tunnel.log");
            if (logFile.exists() && logFile.length() > 0) {
                // Read last 500 characters
                RandomAccessFile file = new RandomAccessFile(logFile, "r");
                long length = file.length();
                long start = Math.max(0, length - 500);
                file.seek(start);
                
                byte[] bytes = new byte[(int)(length - start)];
                file.read(bytes);
                file.close();
                
                return new String(bytes);
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to read tunnel log: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Check if current platform is Windows.
     */
    private boolean isWindowsPlatform() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
    
    /**
     * Check if current platform is Mac.
     */
    private boolean isMacPlatform() {
        String osName = System.getProperty("os.name").toLowerCase();
        return osName.contains("mac") || osName.contains("darwin");
    }
    
    /**
     * Check if current platform is Unix.
     */
    private boolean isUnixPlatform() {
        return !isWindowsPlatform();
    }
    
    /**
     * Custom exception for tunnel operations.
     */
    public static class TunnelException extends Exception {
        public TunnelException(String message) {
            super(message);
        }
        
        public TunnelException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

