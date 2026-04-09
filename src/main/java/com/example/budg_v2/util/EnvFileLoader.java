package com.example.budg_v2.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Utility class to load environment variables from .env file
 * Supports standard .env format:
 * - KEY=value
 * - # comments
 * - Empty lines are ignored
 */
public class EnvFileLoader {
    
    private static final Logger logger = LoggerFactory.getLogger(EnvFileLoader.class);
    private static volatile boolean loaded = false;
    
    /**
     * Load environment variables from .env file in project root
     * Only loads once, even if called multiple times
     */
    public static void loadEnvFile() {
        if (loaded) {
            return;
        }
        
        synchronized (EnvFileLoader.class) {
            if (loaded) {
                return;
            }
            
            Path envPath = findEnvFile();
            
            // If not found in file system, try classpath as last resort
            // Note: File system .env files take priority over classpath to allow deployment-specific configs
            if (envPath == null || !Files.exists(envPath)) {
                logger.debug("No .env file found in file system, trying classpath as fallback...");
                try {
                    loadEnvFileFromClasspath();
                    logger.info("Loaded environment variables from .env file in classpath (fallback)");
                    logger.warn("NOTE: Using .env from classpath. For production, use file system .env file (e.g., Tomcat bin/.env) for deployment-specific configuration.");
                    loaded = true;
                    return;
                } catch (Exception e) {
                    logger.debug("No .env file found in classpath either: {}", e.getMessage());
                }
                
                logger.debug("No .env file found. Using system environment variables only.");
                logger.debug("Searched in: current directory, user.dir, classpath, and common project locations");
                loaded = true;
                return;
            }
            
            try {
                loadEnvFile(envPath);
                logger.info("Loaded environment variables from .env file: {}", envPath.toAbsolutePath());
            } catch (IOException e) {
                logger.error("Failed to load .env file: {}", envPath.toAbsolutePath(), e);
            } finally {
                loaded = true;
            }
        }
    }
    
    /**
     * Find .env file in various possible locations
     */
    private static Path findEnvFile() {
        // Build list of possible paths to search
        java.util.List<String> searchPaths = new java.util.ArrayList<>();
        
        // 1. Current working directory
        searchPaths.add(".env");
        
        // 2. User directory (where application was started from)
        String userDir = System.getProperty("user.dir");
        if (userDir != null) {
            searchPaths.add(Paths.get(userDir, ".env").toString());
        }
        
        // 3. Parent directories (common when running from target/ or build/)
        searchPaths.add("../.env");
        searchPaths.add("../../.env");
        searchPaths.add("../../../.env");
        
        // 4. Resources directory (if .env is copied there)
        searchPaths.add("src/main/resources/.env");
        searchPaths.add("resources/.env");
        
        // 5. Try to find project root by looking for common markers
        if (userDir != null) {
            Path userDirPath = Paths.get(userDir);
            // Look for pom.xml or build.gradle in parent directories
            Path current = userDirPath;
            for (int i = 0; i < 5 && current != null; i++) {
                Path pomFile = current.resolve("pom.xml");
                Path gradleFile = current.resolve("build.gradle");
                if (Files.exists(pomFile) || Files.exists(gradleFile)) {
                    Path envInRoot = current.resolve(".env");
                    if (Files.exists(envInRoot)) {
                        searchPaths.add(envInRoot.toString());
                    }
                }
                current = current.getParent();
            }
        }
        
        // Search all paths
        for (String pathStr : searchPaths) {
            try {
                Path testPath = Paths.get(pathStr);
                if (Files.exists(testPath) && Files.isRegularFile(testPath)) {
                    logger.debug("Found .env file at: {}", testPath.toAbsolutePath());
                    return testPath;
                }
            } catch (Exception e) {
                // Skip invalid paths
            }
        }
        
        return null;
    }
    
    /**
     * Load environment variables from a specific .env file
     */
    private static void loadEnvFile(Path envPath) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(envPath)) {
            String line;
            int lineNumber = 0;
            
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                
                // Skip empty lines and comments
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                
                // Parse KEY=value format
                int equalsIndex = line.indexOf('=');
                if (equalsIndex <= 0) {
                    logger.warn("Invalid line in .env file (line {}): {}", lineNumber, line);
                    // Special logging for JWT_SECRET_KEY to help diagnose
                    if (line.contains("JWT_SECRET_KEY")) {
                        logger.error("JWT_SECRET_KEY line found but could not be parsed! Line: [{}], equalsIndex: {}", line, equalsIndex);
                    }
                    continue;
                }
                
                String key = line.substring(0, equalsIndex).trim();
                String value = line.substring(equalsIndex + 1).trim();
                
                // Remove quotes if present
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                } else if (value.startsWith("'") && value.endsWith("'")) {
                    value = value.substring(1, value.length() - 1);
                }
                
                // Only set if not already set in system environment
                if (System.getenv(key) == null && System.getProperty(key) == null) {
                    System.setProperty(key, value);
                    logger.debug("Loaded from .env: {} = {} (hidden)", key, value.length() > 0 ? "***" : "empty");
                    // Special logging for JWT_SECRET_KEY to help diagnose issues
                    if ("JWT_SECRET_KEY".equals(key)) {
                        logger.info("JWT_SECRET_KEY loaded from .env file (length: {} bytes)", value.getBytes().length);
                    }
                } else {
                    logger.debug("Skipping .env value for {} (already set in environment)", key);
                    // Special logging for JWT_SECRET_KEY
                    if ("JWT_SECRET_KEY".equals(key)) {
                        logger.warn("JWT_SECRET_KEY was skipped because it's already set in environment (env var: {}, system property: {})", 
                            System.getenv(key) != null, System.getProperty(key) != null);
                    }
                }
            }
        }
    }
    
    /**
     * Try to load .env file from classpath (src/main/resources/.env)
     */
    private static void loadEnvFileFromClasspath() throws IOException {
        InputStream inputStream = EnvFileLoader.class.getClassLoader().getResourceAsStream(".env");
        if (inputStream == null) {
            throw new IOException(".env not found in classpath");
        }
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                
                // Skip empty lines and comments
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                
                // Parse KEY=value format
                int equalsIndex = line.indexOf('=');
                if (equalsIndex <= 0) {
                    logger.warn("Invalid line in .env file (line {}): {}", lineNumber, line);
                    continue;
                }
                
                String key = line.substring(0, equalsIndex).trim();
                String value = line.substring(equalsIndex + 1).trim();
                
                // Remove quotes if present
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                } else if (value.startsWith("'") && value.endsWith("'")) {
                    value = value.substring(1, value.length() - 1);
                }
                
                // Only set if not already set in system environment
                if (System.getenv(key) == null && System.getProperty(key) == null) {
                    System.setProperty(key, value);
                    logger.debug("Loaded from .env (classpath): {} = {} (hidden)", key, value.length() > 0 ? "***" : "empty");
                } else {
                    logger.debug("Skipping .env value for {} (already set in environment)", key);
                }
            }
        }
    }
}

