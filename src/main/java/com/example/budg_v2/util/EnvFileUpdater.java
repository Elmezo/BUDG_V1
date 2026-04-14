package com.example.budg_v2.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class to update environment variables in .env file
 */
public class EnvFileUpdater {
    
    private static final Logger logger = LoggerFactory.getLogger(EnvFileUpdater.class);
    
    /**
     * Update or add an environment variable in .env file
     * @param key The environment variable key
     * @param value The environment variable value
     * @return true if successful, false otherwise
     */
    public static boolean updateEnvVariable(String key, String value) {
        Path envPath = findEnvFile();
        
        // If not found, return false - don't create new file
        if (envPath == null || !Files.exists(envPath)) {
            logger.warn("No .env file found to update. Please ensure .env file exists in project root or one of the standard locations.");
            return false;
        }
        
        try {
            List<String> lines;
            // Handle empty file case
            if (Files.size(envPath) == 0) {
                lines = new ArrayList<>();
            } else {
                lines = Files.readAllLines(envPath, StandardCharsets.UTF_8);
            }
            
            List<String> updatedLines = new ArrayList<>();
            boolean found = false;
            
            for (String line : lines) {
                String trimmed = line.trim();
                
                // Skip empty lines and comments
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    updatedLines.add(line);
                    continue;
                }
                
                // Check if this line contains the key we're looking for
                int equalsIndex = trimmed.indexOf('=');
                if (equalsIndex > 0) {
                    String lineKey = trimmed.substring(0, equalsIndex).trim();
                    if (lineKey.equals(key)) {
                        // Update the existing line
                        updatedLines.add(key + "=" + value);
                        found = true;
                        continue;
                    }
                }
                
                // Keep the original line
                updatedLines.add(line);
            }
            
            // If key not found, add it at the end
            if (!found) {
                updatedLines.add(key + "=" + value);
            }
            
            // Write back to file
            Files.write(envPath, updatedLines, StandardCharsets.UTF_8);
            logger.info("Updated .env file: {} = {}", key, value);
            
            return true;
        } catch (IOException e) {
            logger.error("Failed to update .env file", e);
            return false;
        }
    }
    
    /**
     * Find .env file in various possible locations (same logic as EnvFileLoader)
     * Uses the same search order as EnvFileLoader to ensure consistency
     */
    private static Path findEnvFile() {
        // Build list of possible paths to search
        // Use same order as EnvFileLoader for consistency
        List<Path> searchPaths = new ArrayList<>();
        
        // Priority 1: Try to find project root from class location first (most reliable for src/main/resources)
        Path projectRootFromClass = findProjectRootFromClassLocation();
        if (projectRootFromClass != null) {
            searchPaths.add(projectRootFromClass.resolve(".env"));
        }
        
        // Priority 2: Current working directory (same as EnvFileLoader)
        searchPaths.add(Paths.get(".env"));
        
        // Priority 3: User directory (where application was started from) - SAME AS EnvFileLoader
        // This is where EnvFileLoader finds the file in Tomcat
        String userDir = System.getProperty("user.dir");
        if (userDir != null) {
            searchPaths.add(Paths.get(userDir, ".env"));
        }
        
        // Priority 4: Parent directories (common when running from target/ or build/)
        searchPaths.add(Paths.get("../.env"));
        searchPaths.add(Paths.get("../../.env"));
        searchPaths.add(Paths.get("../../../.env"));
        
        // Priority 5: Try to find project root by looking for common markers (pom.xml, build.gradle)
        if (userDir != null) {
            Path userDirPath = Paths.get(userDir);
            Path current = userDirPath;
            for (int i = 0; i < 10 && current != null; i++) {
                Path pomFile = current.resolve("pom.xml");
                Path gradleFile = current.resolve("build.gradle");
                if (Files.exists(pomFile) || Files.exists(gradleFile)) {
                    Path envInRoot = current.resolve(".env");
                    // Only add if file exists (same as EnvFileLoader)
                    if (Files.exists(envInRoot)) {
                        searchPaths.add(envInRoot);
                    }
                }
                current = current.getParent();
            }
        }
        
        // Priority 7: Check CATALINA_BASE and CATALINA_HOME (Tomcat deployment)
        String catalinaBase = System.getProperty("catalina.base");
        if (catalinaBase != null) {
            searchPaths.add(Paths.get(catalinaBase, ".env"));
        }
        String catalinaHome = System.getProperty("catalina.home");
        if (catalinaHome != null) {
            searchPaths.add(Paths.get(catalinaHome, ".env"));
        }
        
        // Search all paths
        for (Path testPath : searchPaths) {
            try {
                if (Files.exists(testPath) && Files.isRegularFile(testPath)) {
                    logger.info("Found .env file at: {}", testPath.toAbsolutePath());
                    return testPath;
                }
            } catch (Exception e) {
                // Skip invalid paths
                logger.debug("Error checking path {}: {}", testPath, e.getMessage());
            }
        }
        
        // Log all searched paths for debugging
        logger.warn("Could not find .env file. Searched in {} locations:", searchPaths.size());
        for (Path path : searchPaths) {
            logger.debug("  - {}", path.toAbsolutePath());
        }
        
        return null;
    }
    
    /**
     * Find project root by examining class location
     * This works even when deployed in Tomcat
     * @return Path to project root, or null if not found
     */
    private static Path findProjectRootFromClassLocation() {
        try {
            java.net.URL classUrl = EnvFileUpdater.class.getProtectionDomain()
                    .getCodeSource().getLocation();
            if (classUrl != null) {
                Path classDir;
                try {
                    classDir = Paths.get(classUrl.toURI());
                } catch (java.net.URISyntaxException e) {
                    // Fallback: try to convert URL path manually
                    String classPath = classUrl.getPath();
                    if (classPath != null && !classPath.isEmpty()) {
                        // Handle Windows URL paths like /D:/... -> D:\...
                        if (classPath.startsWith("/") && classPath.length() > 3 && 
                            classPath.charAt(2) == ':' && Character.isLetter(classPath.charAt(1))) {
                            // Windows absolute path in URL format: /D:/path -> D:\path
                            classPath = classPath.substring(1).replace('/', '\\');
                        }
                        // Decode URL encoding (e.g., %20 -> space)
                        try {
                            classPath = java.net.URLDecoder.decode(classPath, "UTF-8");
                        } catch (java.io.UnsupportedEncodingException ex) {
                            // UTF-8 should always be supported
                        }
                        classDir = Paths.get(classPath);
                    } else {
                        return null;
                    }
                }
                
                String classPathStr = classDir.toString();
                // Check if we're in a build directory (target/classes, build/classes, etc.)
                if (classPathStr.contains("target" + java.io.File.separator + "classes") ||
                    classPathStr.contains("build" + java.io.File.separator + "classes") ||
                    classPathStr.contains("target/classes") || 
                    classPathStr.contains("build/classes")) {
                    // We're in a build directory, go up to project root
                    Path projectRoot = classDir.getParent().getParent();
                    if (Files.exists(projectRoot.resolve("pom.xml")) || 
                        Files.exists(projectRoot.resolve("build.gradle"))) {
                        logger.debug("Found project root from class location: {}", projectRoot);
                        return projectRoot;
                    }
                }
                
                // If not in build directory, search up from class location
                Path current = classDir;
                for (int i = 0; i < 10 && current != null; i++) {
                    Path pomFile = current.resolve("pom.xml");
                    Path gradleFile = current.resolve("build.gradle");
                    if (Files.exists(pomFile) || Files.exists(gradleFile)) {
                        logger.debug("Found project root from class location (searched up): {}", current);
                        return current;
                    }
                    current = current.getParent();
                }
            }
        } catch (Exception e) {
            logger.debug("Could not determine project root from class location: {}", e.getMessage());
        }
        
        return null;
    }
}


