package com.example.budg_v2.admin;

import com.example.budg_v2.constants.ActivityLogConstants;
import com.example.budg_v2.util.ActivityLogHelper;
import com.example.budg_v2.util.UserContextUtil;
import com.google.gson.Gson;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Servlet to download log files as a ZIP archive.
 * Only accessible by ADMIN users.
 */
@WebServlet("/admin/logs/download")
public class LogsDownloadServlet extends HttpServlet {
    
    private static final long serialVersionUID = 1L;
    private static final String LOGS_DIRECTORY = "logs";
    private static final String[] LOG_FILE_PATTERNS = {
        "prod_errors-*.log",
        "prod_app-*.log",
        "prod_audit-*.log"
    };
    
    // Also include current day files without date in filename
    private static final String[] LOG_FILE_NAMES = {
        "prod_errors.log",
        "prod_app.log",
        "prod_audit.log"
    };
    
    private final Gson gson = new Gson();
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        // Check if user is admin
        if (!UserContextUtil.isCurrentUserAdmin(request)) {
            sendErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, 
                "Forbidden: Admin access required");
            return;
        }
        
        // Log the download action
        ActivityLogHelper.logSimpleActivity(request, 
            ActivityLogConstants.SETTING_DOWNLOAD_LOGS,
            ActivityLogConstants.COMPONENT_LOGS,
            ActivityLogConstants.CHANGE_TYPE_OTHER_ACTIONS);
        
        try {
            // Get logs directory
            Path logsDir = getLogsDirectory();
            if (logsDir == null || !Files.exists(logsDir)) {
                // Provide helpful error message with searched locations
                StringBuilder errorMsg = new StringBuilder("Logs directory not found. Searched locations: ");
                errorMsg.append("catalina.base/logs, catalina.home/logs, ./logs, ");
                errorMsg.append("servlet context, user.dir/logs");
                sendErrorResponse(response, HttpServletResponse.SC_NOT_FOUND, errorMsg.toString());
                return;
            }
            
            // Collect all log files
            List<Path> logFiles = collectLogFiles(logsDir);
            
            if (logFiles.isEmpty()) {
                // Provide helpful error message
                String errorMsg = String.format(
                    "No log files found in directory: %s. " +
                    "Patterns searched: %s. " +
                    "Make sure the application has generated log files.",
                    logsDir.toAbsolutePath(),
                    String.join(", ", LOG_FILE_PATTERNS)
                );
                sendErrorResponse(response, HttpServletResponse.SC_NOT_FOUND, errorMsg);
                return;
            }
            
            // Generate ZIP file
            byte[] zipData = createZipFile(logFiles, logsDir);
            
            // Set response headers
            String zipFileName = generateZipFileName();
            response.setContentType("application/zip");
            response.setHeader("Content-Disposition", 
                "attachment; filename=\"" + zipFileName + "\"");
            response.setContentLength(zipData.length);
            
            // Write ZIP to response
            try (OutputStream out = response.getOutputStream()) {
                out.write(zipData);
                out.flush();
            }
            
        } catch (Exception e) {
            sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                "Error generating log archive: " + e.getMessage());
        }
    }
    
    /**
     * Get the logs directory path.
     * Logback typically creates logs in the current working directory or CATALINA_BASE.
     * 
     * @return Path to logs directory or null if not found
     */
    private Path getLogsDirectory() {
        // Priority 1: Try CATALINA_BASE/logs (Tomcat standard location)
        String catalinaBase = System.getProperty("catalina.base");
        if (catalinaBase != null) {
            Path catalinaLogs = Paths.get(catalinaBase, LOGS_DIRECTORY);
            if (Files.exists(catalinaLogs) && Files.isDirectory(catalinaLogs)) {
                return catalinaLogs.toAbsolutePath();
            }
        }
        
        // Priority 2: Try CATALINA_HOME/logs
        String catalinaHome = System.getProperty("catalina.home");
        if (catalinaHome != null) {
            Path catalinaHomeLogs = Paths.get(catalinaHome, LOGS_DIRECTORY);
            if (Files.exists(catalinaHomeLogs) && Files.isDirectory(catalinaHomeLogs)) {
                return catalinaHomeLogs.toAbsolutePath();
            }
        }
        
        // Priority 3: Try relative path (from application root/working directory)
        Path logsDir = Paths.get(LOGS_DIRECTORY);
        if (Files.exists(logsDir) && Files.isDirectory(logsDir)) {
            return logsDir.toAbsolutePath();
        }
        
        // Priority 4: Try absolute path from servlet context
        String realPath = getServletContext().getRealPath("/" + LOGS_DIRECTORY);
        if (realPath != null) {
            Path path = Paths.get(realPath);
            if (Files.exists(path) && Files.isDirectory(path)) {
                return path;
            }
        }
        
        // Priority 5: Try current working directory
        Path cwdLogs = Paths.get(System.getProperty("user.dir"), LOGS_DIRECTORY);
        if (Files.exists(cwdLogs) && Files.isDirectory(cwdLogs)) {
            return cwdLogs.toAbsolutePath();
        }
        
        // Priority 6: Try parent of working directory (common in IDE setups)
        String userDir = System.getProperty("user.dir");
        if (userDir != null) {
            Path parentLogs = Paths.get(userDir).getParent();
            if (parentLogs != null) {
                Path parentLogsDir = parentLogs.resolve(LOGS_DIRECTORY);
                if (Files.exists(parentLogsDir) && Files.isDirectory(parentLogsDir)) {
                    return parentLogsDir.toAbsolutePath();
                }
            }
        }
        
        return null;
    }
    
    /**
     * Collect all log files matching the patterns.
     * 
     * @param logsDir The logs directory
     * @return List of log file paths
     */
    private List<Path> collectLogFiles(Path logsDir) throws IOException {
        List<Path> logFiles = new ArrayList<>();
        
        if (!Files.exists(logsDir) || !Files.isDirectory(logsDir)) {
            return logFiles;
        }
        
        // Collect files matching each pattern
        for (String pattern : LOG_FILE_PATTERNS) {
            // Convert glob pattern to regex
            // prod_errors-*.log -> prod_errors-.*\.log
            String regex = pattern
                .replace(".", "\\.")
                .replace("*", ".*");
            
            try {
                Files.list(logsDir)
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String fileName = path.getFileName().toString();
                        // Check if file matches pattern (e.g., prod_errors-2025-01-15.log)
                        return fileName.matches(regex);
                    })
                    .forEach(logFiles::add);
            } catch (IOException e) {
                // Log error but continue
                System.err.println("Error listing log files for pattern " + pattern + ": " + e.getMessage());
            }
        }
        
        // Also collect current day files (without date in filename)
        for (String fileName : LOG_FILE_NAMES) {
            Path logFile = logsDir.resolve(fileName);
            if (Files.exists(logFile) && Files.isRegularFile(logFile)) {
                logFiles.add(logFile);
            }
        }
        
        // Remove duplicates (in case a file matches multiple patterns)
        Set<Path> uniqueFiles = new LinkedHashSet<>(logFiles);
        logFiles = new ArrayList<>(uniqueFiles);
        
        // Sort by filename (which includes date)
        logFiles.sort(Comparator.comparing(path -> path.getFileName().toString()));
        
        return logFiles;
    }
    
    /**
     * Create ZIP file from log files.
     * 
     * @param logFiles List of log file paths
     * @param baseDir Base directory for relative paths in ZIP
     * @return ZIP file as byte array
     */
    private byte[] createZipFile(List<Path> logFiles, Path baseDir) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (Path logFile : logFiles) {
                if (!Files.exists(logFile) || !Files.isRegularFile(logFile)) {
                    continue;
                }
                
                // Get relative path for ZIP entry
                String entryName = logFile.getFileName().toString();
                
                // Create ZIP entry
                ZipEntry entry = new ZipEntry(entryName);
                entry.setTime(Files.getLastModifiedTime(logFile).toMillis());
                zos.putNextEntry(entry);
                
                // Copy file content to ZIP
                try (InputStream is = Files.newInputStream(logFile)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        zos.write(buffer, 0, bytesRead);
                    }
                }
                
                zos.closeEntry();
            }
        }
        
        return baos.toByteArray();
    }
    
    /**
     * Generate ZIP filename with timestamp.
     * 
     * @return ZIP filename
     */
    private String generateZipFileName() {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm");
        return "axon-logs-" + now.format(formatter) + ".zip";
    }
    
    /**
     * Send error response as JSON.
     * 
     * @param response HTTP response
     * @param statusCode HTTP status code
     * @param message Error message
     */
    private void sendErrorResponse(HttpServletResponse response, int statusCode, String message)
            throws IOException {
        response.setStatus(statusCode);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        Map<String, String> error = new HashMap<>();
        error.put("error", message);
        error.put("code", String.valueOf(statusCode));
        
        response.getWriter().write(gson.toJson(error));
    }
}

