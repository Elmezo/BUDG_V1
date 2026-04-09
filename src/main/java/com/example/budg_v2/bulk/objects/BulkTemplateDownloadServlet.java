package com.example.budg_v2.bulk.objects;

import com.example.budg_v2.util.CorsUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Servlet for downloading bulk upload template files
 * Endpoint: /api/bulk/template/{entity}/{templateFile}
 */
@WebServlet("/api/bulk/template/*")
public class BulkTemplateDownloadServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(BulkTemplateDownloadServlet.class);
    private static final Gson gson = new Gson();
    private static final String TEMPLATE_BASE_PATH = "src/main/bulk/";
    
    /**
     * Find the template file using multiple strategies.
     * Tries: system property, environment variable, project root search, and servlet context.
     */
    private Path findTemplateFile(String entity, String templateFile, HttpServletRequest request) {
        // Strategy 1: Check system property for template base path
        String templateBaseProp = System.getProperty("bulk.template.path");
        if (templateBaseProp != null && !templateBaseProp.isEmpty()) {
            Path templatePath = Paths.get(templateBaseProp, entity, templateFile);
            File file = templatePath.toFile();
            if (file.exists() && file.isFile()) {
                logger.debug("Found template via system property: {}", templatePath);
                return templatePath;
            }
        }
        
        // Strategy 2: Check environment variable
        String templateBaseEnv = System.getenv("BULK_TEMPLATE_PATH");
        if (templateBaseEnv != null && !templateBaseEnv.isEmpty()) {
            Path templatePath = Paths.get(templateBaseEnv, entity, templateFile);
            File file = templatePath.toFile();
            if (file.exists() && file.isFile()) {
                logger.debug("Found template via environment variable: {}", templatePath);
                return templatePath;
            }
        }
        
        // Strategy 3: Find project root by searching for pom.xml or build.gradle
        // Search from multiple starting points
        String[] searchStarts = {
            System.getProperty("user.dir"),
            System.getProperty("catalina.base"),
            System.getProperty("catalina.home"),
            request != null ? getServletContext().getRealPath("/") : null
        };
        
        // Also try to find project root from class location (for development)
        try {
            java.net.URL classUrl = BulkTemplateDownloadServlet.class.getProtectionDomain()
                    .getCodeSource().getLocation();
            if (classUrl != null) {
                // Use toURI() to properly convert URL to file path (handles Windows paths correctly)
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
                        throw new Exception("Could not determine class path from URL");
                    }
                }
                
                String classPathStr = classDir.toString();
                if (classPathStr.contains("target" + File.separator + "classes") || 
                    classPathStr.contains("build" + File.separator + "classes") ||
                    classPathStr.contains("target/classes") || 
                    classPathStr.contains("build/classes")) {
                    // We're in a build directory, go up to project root
                    if (classPathStr.contains("target")) {
                        // Go from target/classes to project root (up 2 levels)
                        Path projectRoot = classDir.getParent().getParent();
                        if (Files.exists(projectRoot.resolve("pom.xml")) || 
                            Files.exists(projectRoot.resolve("build.gradle"))) {
                            // Immediately check if template exists at this project root
                            Path templatePath = projectRoot.resolve(TEMPLATE_BASE_PATH).resolve(entity).resolve(templateFile);
                            File templateFileObj = templatePath.toFile();
                            if (templateFileObj.exists() && templateFileObj.isFile()) {
                                logger.info("Found template via class location at project root: {}", templatePath);
                                return templatePath;
                            }
                            
                            // Set as first search start
                            searchStarts = new String[]{
                                projectRoot.toString(), // project root
                                System.getProperty("user.dir"),
                                System.getProperty("catalina.base"),
                                System.getProperty("catalina.home"),
                                request != null ? getServletContext().getRealPath("/") : null
                            };
                            logger.info("Using project root from class location: {}", projectRoot);
                            logger.debug("Template path checked: {}", templatePath);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Could not determine class location: {}", e.getMessage());
        }
        
        for (String startDir : searchStarts) {
            if (startDir == null) continue;
            
            Path current = Paths.get(startDir);
            // Search up to 15 levels to find project root
            for (int i = 0; i < 15 && current != null; i++) {
                Path pomFile = current.resolve("pom.xml");
                Path gradleFile = current.resolve("build.gradle");
                if (Files.exists(pomFile) || Files.exists(gradleFile)) {
                    Path templatePath = current.resolve(TEMPLATE_BASE_PATH).resolve(entity).resolve(templateFile);
                    File file = templatePath.toFile();
                    if (file.exists() && file.isFile()) {
                        logger.info("Found template via project root search from {}: {}", startDir, templatePath);
                        return templatePath;
                    } else {
                        logger.debug("Found project root at {} but template not found at {}", current, templatePath);
                    }
                }
                current = current.getParent();
            }
        }
        
        // Strategy 4: Try relative to current working directory
        Path relativePath = Paths.get(TEMPLATE_BASE_PATH, entity, templateFile);
        File file = relativePath.toFile();
        if (file.exists() && file.isFile()) {
            logger.debug("Found template via relative path: {}", relativePath);
            return relativePath;
        }
        
        // Strategy 5: Try servlet context real path (if templates are in webapp)
        if (request != null) {
            String realPath = getServletContext().getRealPath("/bulk/" + entity + "/" + templateFile);
            if (realPath != null) {
                File contextFile = new File(realPath);
                if (contextFile.exists() && contextFile.isFile()) {
                    logger.debug("Found template via servlet context: {}", realPath);
                    return Paths.get(realPath);
                }
            }
        }
        
        // Not found - return null (caller will handle error)
        return null;
    }
    
    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        CorsUtil.handlePreflight(resp);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        CorsUtil.setCorsHeaders(response);
        
        try {
            // Parse path: /api/bulk/template/{entity}/{templateFile}
            String pathInfo = request.getPathInfo(); // e.g., "/regulator/TEMPLATE_INSERT.xlsx"
            
            if (pathInfo == null || pathInfo.equals("/")) {
                sendErrorResponse(response, "Missing entity and template file in path", 400);
                return;
            }
            
            // Remove leading slash and split
            String[] pathParts = pathInfo.substring(1).split("/");
            
            if (pathParts.length != 2) {
                sendErrorResponse(response, "Invalid path format. Expected: /api/bulk/template/{entity}/{templateFile}", 400);
                return;
            }
            
            String entity = pathParts[0]; // e.g., "regulator"
            String templateFile = pathParts[1]; // e.g., "TEMPLATE_INSERT.xlsx"
            
            logger.info("Template download request - Entity: {}, Template: {}", entity, templateFile);
            
            // Validate template file name (security check)
            if (!isValidTemplateFileName(templateFile)) {
                sendErrorResponse(response, "Invalid template file name", 400);
                return;
            }
            
            // Find template file using multiple strategies
            Path templatePath = findTemplateFile(entity, templateFile, request);
            if (templatePath == null) {
                logger.warn("Template file not found: Entity: {}, Template: {}", entity, templateFile);
                logger.warn("Searched from: user.dir={}, catalina.base={}, catalina.home={}", 
                    System.getProperty("user.dir"), 
                    System.getProperty("catalina.base"),
                    System.getProperty("catalina.home"));
                sendErrorResponse(response, "Template file not found", 404);
                return;
            }
            
            File templateFileObj = templatePath.toFile();
            
            // Security check: ensure file is within allowed template directory
            // Check if path contains bulk templates and is a valid Excel file
            String canonicalFilePath = templateFileObj.getCanonicalPath();
            String fileName = templateFileObj.getName();
            
            // Verify it's actually a template file (security)
            if (!fileName.startsWith("TEMPLATE_") || 
                (!fileName.endsWith(".xlsx") && !fileName.endsWith(".xls"))) {
                logger.warn("Security violation: invalid template file: {}", canonicalFilePath);
                sendErrorResponse(response, "Access denied", 403);
                return;
            }
            
            // Additional security: ensure path contains expected structure
            if (!canonicalFilePath.replace("\\", "/").toLowerCase().contains("/bulk/") ||
                !canonicalFilePath.replace("\\", "/").toLowerCase().contains("/" + entity.toLowerCase() + "/")) {
                logger.warn("Security violation: template file path doesn't match expected structure: {}", canonicalFilePath);
                sendErrorResponse(response, "Access denied", 403);
                return;
            }
            
            // Set response headers for file download
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + templateFile + "\"");
            response.setContentLengthLong(templateFileObj.length());
            
            // Stream file to response
            try (FileInputStream fileInputStream = new FileInputStream(templateFileObj);
                 OutputStream outputStream = response.getOutputStream()) {
                
                byte[] buffer = new byte[4096];
                int bytesRead;
                
                while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                
                outputStream.flush();
                logger.info("Template file downloaded successfully: {}", templateFile);
            }
            
        } catch (Exception e) {
            logger.error("Error downloading template file", e);
            sendErrorResponse(response, "Internal server error: " + e.getMessage(), 500);
        }
    }
    
    /**
     * Validate template file name to prevent directory traversal attacks
     */
    private boolean isValidTemplateFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return false;
        }
        
        // Must be an Excel file
        if (!fileName.endsWith(".xlsx") && !fileName.endsWith(".xls")) {
            return false;
        }
        
        // Must start with TEMPLATE_
        if (!fileName.startsWith("TEMPLATE_")) {
            return false;
        }
        
        // Must not contain path separators or special characters
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            return false;
        }
        
        // Valid template names: TEMPLATE_INSERT.xlsx, TEMPLATE_UPDATE.xlsx, TEMPLATE_DELETE.xlsx
        return fileName.matches("TEMPLATE_(INSERT|UPDATE|DELETE)\\.(xlsx|xls)");
    }
    
    /**
     * Send JSON error response
     */
    private void sendErrorResponse(HttpServletResponse response, String message, int statusCode) 
            throws IOException {
        response.setStatus(statusCode);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        JsonObject errorResponse = new JsonObject();
        errorResponse.addProperty("status", "error");
        errorResponse.addProperty("message", message);
        
        response.getWriter().write(gson.toJson(errorResponse));
    }
}

