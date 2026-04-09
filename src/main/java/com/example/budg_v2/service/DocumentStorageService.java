package com.example.budg_v2.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Service for handling document file storage operations
 */
public class DocumentStorageService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentStorageService.class);

    // Base upload directory - dynamic based on project location or system property
    private static final String BASE_UPLOAD_DIR = getBaseUploadDirectory();

    // Allowed file extensions
    private static final Set<String> ALLOWED_EXTENSIONS = new HashSet<>(Arrays.asList(
            ".jpg", ".jpeg", ".gif", ".png", ".txt", ".doc", ".docx",
            ".rtf", ".pdf", ".xls", ".xlsx", ".ppt", ".pptx"));

    // Maximum file size (10MB)
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    // Special characters not allowed in filename
    private static final String FORBIDDEN_CHARS_REGEX = "[<>,()]";

    /**
     * Get base upload directory dynamically
     * Priority:
     * 1. System property: document.upload.dir
     * 2. Relative to project root: ./uploads/documents
     * 3. User home directory: ~/BUDG_V2/uploads/documents
     */
    private static String getBaseUploadDirectory() {
        // Try system property first
        String systemProperty = System.getProperty("document.upload.dir");
        if (systemProperty != null && !systemProperty.trim().isEmpty()) {
            logger.info("Using upload directory from system property: {}", systemProperty);
            return systemProperty;
        }

        // Try relative to current working directory (project root)
        String relativePath = "uploads" + File.separator + "documents";
        Path relativeDir = Paths.get(relativePath);
        try {
            // Check if we can create/write to this directory
            if (!Files.exists(relativeDir)) {
                Files.createDirectories(relativeDir);
            }
            if (Files.isWritable(relativeDir)) {
                String absolutePath = relativeDir.toAbsolutePath().toString();
                logger.info("Using relative upload directory: {}", absolutePath);
                return absolutePath;
            }
        } catch (Exception e) {
            logger.debug("Cannot use relative path, trying user home: {}", e.getMessage());
        }

        // Fallback to user home directory
        String userHome = System.getProperty("user.home");
        String homePath = userHome + File.separator + "BUDG_V2" + File.separator + "uploads" + File.separator + "documents";
        logger.info("Using user home upload directory: {}", homePath);
        return homePath;
    }

    /**
     * Validate file extension
     */
    public boolean isValidFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return false;
        }

        String extension = getFileExtension(filename).toLowerCase();
        return ALLOWED_EXTENSIONS.contains(extension);
    }

    /**
     * Validate filename doesn't contain forbidden characters
     */
    public boolean isValidFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            return false;
        }

        return !filename.matches(".*" + FORBIDDEN_CHARS_REGEX + ".*");
    }

    /**
     * Validate file size
     */
    public boolean isValidFileSize(long fileSize) {
        return fileSize > 0 && fileSize <= MAX_FILE_SIZE;
    }

    /**
     * Get file extension including the dot
     */
    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1) {
            return "";
        }
        return filename.substring(lastDot);
    }

    /**
     * Save uploaded file to disk
     * 
     * @param inputStream      File input stream
     * @param facetType        Facet type (e.g., "system", "dataset")
     * @param originalFilename Original filename
     * @return Relative file path
     */
    public String saveFile(InputStream inputStream, String facetType, String originalFilename) throws IOException {
        // Create facet-specific directory
        Path facetDir = Paths.get(BASE_UPLOAD_DIR, facetType.toLowerCase());
        try {
            if (!Files.exists(facetDir)) {
                Files.createDirectories(facetDir);
                logger.info("Created directory: {}", facetDir);
            }
            
            // Verify directory is writable
            if (!Files.isWritable(facetDir)) {
                throw new IOException("Upload directory is not writable: " + facetDir);
            }
        } catch (IOException e) {
            logger.error("Failed to create or access upload directory: {}", facetDir, e);
            throw new IOException("Failed to create upload directory. Please check permissions and disk space: " + e.getMessage(), e);
        }

        // Generate unique filename to avoid collisions
        String uniqueFilename = generateUniqueFilename(originalFilename);
        Path filePath = facetDir.resolve(uniqueFilename);

        // Save file
        try (OutputStream outputStream = Files.newOutputStream(filePath)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        } catch (IOException e) {
            logger.error("Failed to save file: {}", filePath, e);
            throw new IOException("Failed to save file. Please check disk space and permissions: " + e.getMessage(), e);
        }

        logger.info("Saved file: {}", filePath);

        // Return relative path from base upload dir
        return Paths.get(facetType.toLowerCase(), uniqueFilename).toString();
    }

    /**
     * Generate unique filename using timestamp and UUID
     */
    private String generateUniqueFilename(String originalFilename) {
        String extension = getFileExtension(originalFilename);
        String baseName = originalFilename.substring(0, originalFilename.length() - extension.length());

        // Sanitize base name (remove special chars, keep only alphanumeric, dash,
        // underscore)
        baseName = baseName.replaceAll("[^a-zA-Z0-9_-]", "_");

        // Add timestamp and short UUID
        String timestamp = String.valueOf(System.currentTimeMillis());
        String shortUuid = UUID.randomUUID().toString().substring(0, 8);

        return baseName + "_" + timestamp + "_" + shortUuid + extension;
    }

    /**
     * Delete file from disk
     */
    public boolean deleteFile(String relativePath) {
        try {
            Path filePath = Paths.get(BASE_UPLOAD_DIR, relativePath);
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                logger.info("Deleted file: {}", filePath);
                return true;
            } else {
                logger.warn("File not found for deletion: {}", filePath);
                return false;
            }
        } catch (IOException e) {
            logger.error("Error deleting file: {}", relativePath, e);
            return false;
        }
    }

    /**
     * Get absolute file path
     */
    public Path getAbsolutePath(String relativePath) {
        return Paths.get(BASE_UPLOAD_DIR, relativePath);
    }

    /**
     * Get file size
     */
    public long getFileSize(String relativePath) throws IOException {
        Path filePath = getAbsolutePath(relativePath);
        if (Files.exists(filePath)) {
            return Files.size(filePath);
        }
        return 0;
    }

    /**
     * Check if file exists
     */
    public boolean fileExists(String relativePath) {
        Path filePath = getAbsolutePath(relativePath);
        return Files.exists(filePath);
    }

    /**
     * Get validation error message for filename
     */
    public String getFilenameValidationError(String filename) {
        if (!isValidFilename(filename)) {
            return "The file name cannot contain the following special characters: < > , ()";
        }
        if (!isValidFileExtension(filename)) {
            return "File type not supported. Allowed types: " + String.join(", ", ALLOWED_EXTENSIONS);
        }
        return null;
    }

    /**
     * Get max file size in MB
     */
    public static long getMaxFileSizeMB() {
        return MAX_FILE_SIZE / (1024 * 1024);
    }
}
