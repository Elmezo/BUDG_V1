package com.example.budg_v2.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Utility class for managing BPMN files on disk
 */
public class BpmnFileManager {

    private static final Logger logger = LoggerFactory.getLogger(BpmnFileManager.class);
    private static final String BPMN_DIR = "data/bpmn/";

    /**
     * Save BPMN XML to file
     */
    public static void saveBpmnFile(int processDefinitionId, String xmlContent) throws IOException {
        // Ensure directory exists
        Path dirPath = Paths.get(BPMN_DIR);
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath);
            logger.info("Created BPMN directory: {}", dirPath.toAbsolutePath());
        }

        // Save file
        String filename = getFilename(processDefinitionId);
        Path filePath = Paths.get(BPMN_DIR, filename);

        try (BufferedWriter writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8)) {
            writer.write(xmlContent);
        }

        logger.info("Saved BPMN file: {}", filePath.toAbsolutePath());
    }

    /**
     * Load BPMN XML from file
     */
    public static String loadBpmnFile(int processDefinitionId) throws IOException {
        String filename = getFilename(processDefinitionId);
        Path filePath = Paths.get(BPMN_DIR, filename);

        if (!Files.exists(filePath)) {
            logger.warn("BPMN file not found: {}", filePath.toAbsolutePath());
            return null;
        }

        return Files.readString(filePath, StandardCharsets.UTF_8);
    }

    /**
     * Delete BPMN file
     */
    public static void deleteBpmnFile(int processDefinitionId) throws IOException {
        String filename = getFilename(processDefinitionId);
        Path filePath = Paths.get(BPMN_DIR, filename);

        if (Files.exists(filePath)) {
            Files.delete(filePath);
            logger.info("Deleted BPMN file: {}", filePath.toAbsolutePath());
        }
    }

    /**
     * Check if BPMN file exists
     */
    public static boolean fileExists(int processDefinitionId) {
        String filename = getFilename(processDefinitionId);
        Path filePath = Paths.get(BPMN_DIR, filename);
        return Files.exists(filePath);
    }

    /**
     * Get filename for process definition
     */
    private static String getFilename(int processDefinitionId) {
        return "workflow_" + processDefinitionId + ".bpmn";
    }

    /**
     * Get absolute path for BPMN file
     */
    public static String getAbsolutePath(int processDefinitionId) {
        String filename = getFilename(processDefinitionId);
        Path filePath = Paths.get(BPMN_DIR, filename);
        return filePath.toAbsolutePath().toString();
    }
}
