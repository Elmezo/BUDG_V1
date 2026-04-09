package com.example.budg_v2.service;

import com.example.budg_v2.dao.FacetChangesDAO;
import com.example.budg_v2.database.DatabaseConnection;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Service for applying or discarding pending changes from facet_changes tables.
 * Used when CR is completed (apply) or deleted (discard).
 */
public class FacetChangesService {
    private static final Logger logger = LoggerFactory.getLogger(FacetChangesService.class);
    private final FacetChangesDAO facetChangesDAO = new FacetChangesDAO();
    
    // Thread-local Set to track which objectId/table combinations have already had old rows deleted
    // This prevents deleting old rows multiple times when processing multiple relationships
    private static final ThreadLocal<Set<String>> deletedOldRowsTracker = ThreadLocal.withInitial(HashSet::new);

    /**
     * Apply all pending changes for a CR: delete object_id (old data) and replace with nobject_id (new data)
     * This is called when CR status becomes "Completed"
     * Logic: All changes are accepted, so old data (object_id) is replaced with new data (nobject_id)
     */
    public void applyChangesForCR(int changeRequestId) throws SQLException {
        logger.info("🚀 [AUTO CR] Applying changes for CR: {}", changeRequestId);
        
        // Get all mappings for this CR
        Map<String, Map<Integer, Map<String, List<Integer>>>> allMappings = 
            facetChangesDAO.getAllMappingsByChangeRequest(changeRequestId);
        
        if (allMappings.isEmpty()) {
            logger.warn("⚠️  [AUTO CR] No pending changes found for CR {}", changeRequestId);
            return;
        }
        
        logger.info("✅ [AUTO CR] Found pending changes in {} facet(s) for CR {}", allMappings.size(), changeRequestId);
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            
            try {
                // Process each facet
                for (Map.Entry<String, Map<Integer, Map<String, List<Integer>>>> facetEntry : allMappings.entrySet()) {
                    String facetName = facetEntry.getKey();
                    // Normalize facet name to lowercase for consistency
                    String normalizedFacetName = facetName != null ? facetName.toLowerCase().trim() : null;
                    Map<Integer, Map<String, List<Integer>>> objectMappings = facetEntry.getValue();
                    
                    // Find the main object_id from the summary area (needed for impact/stakeholder/relationship areas)
                    Integer mainObjectId = null;
                    Integer mainNobjectId = null;
                    for (Map.Entry<Integer, Map<String, List<Integer>>> objEntry : objectMappings.entrySet()) {
                        Map<String, List<Integer>> areas = objEntry.getValue();
                        if (areas.containsKey("summary")) {
                            mainObjectId = objEntry.getKey();
                            mainNobjectId = areas.get("summary").get(0); // summary always has one entry
                            break;
                        }
                    }
                    
                    // If no summary found, try to find it from other areas
                    if (mainObjectId == null) {
                        for (Map.Entry<Integer, Map<String, List<Integer>>> objEntry : objectMappings.entrySet()) {
                            Map<String, List<Integer>> areas = objEntry.getValue();
                            for (Map.Entry<String, List<Integer>> areaEntry : areas.entrySet()) {
                                String areaKeyCheck = areaEntry.getKey();
                                if (areaKeyCheck.startsWith("impact#")) {
                                    mainObjectId = objEntry.getKey();
                                    mainNobjectId = areaEntry.getValue().get(0);
                                    break;
                                }
                            }
                            if (mainObjectId != null) break;
                        }
                    }
                    
                    if (mainObjectId == null) {
                        continue;
                    }
                    
                    if (mainNobjectId == null) {
                        mainNobjectId = mainObjectId;
                    }
                    
                    // IMPORTANT: Process areas in the correct order to avoid foreign key issues:
                    // 1. First: summary#attribute (update attribute Dataset_IDs)
                    // 2. Second: summary#dataset_value_info (update value info dataset_ids)
                    // 3. Third: impact, stakeholders, relationships, data-content, documents (copy to original, delete from cloned)
                    // 4. Last: summary (copy main table data, delete cloned row)
                    // If summary is processed first, deleting the cloned dataset will cascade-delete attributes!
                    
                    // Collect all areas to process in order - flatten List<Integer> into individual entries
                    List<Map.Entry<Integer, Map.Entry<String, Integer>>> orderedAreas = new ArrayList<>();
                    for (Map.Entry<Integer, Map<String, List<Integer>>> objectEntry : objectMappings.entrySet()) {
                        int objectId = objectEntry.getKey();
                        for (Map.Entry<String, List<Integer>> areaEntry : objectEntry.getValue().entrySet()) {
                            String areaKey = areaEntry.getKey();
                            for (int nobjectId : areaEntry.getValue()) {
                                orderedAreas.add(new java.util.AbstractMap.SimpleEntry<>(objectId, 
                                    new java.util.AbstractMap.SimpleEntry<>(areaKey, nobjectId)));
                            }
                        }
                    }
                    
                    // Sort areas by processing priority
                    orderedAreas.sort((a, b) -> {
                        String areaA = a.getValue().getKey();
                        String areaB = b.getValue().getKey();
                        int priorityA = getAreaProcessingPriority(areaA);
                        int priorityB = getAreaProcessingPriority(areaB);
                        return Integer.compare(priorityA, priorityB);
                    });
                    
                    // Process each area in order
                    for (Map.Entry<Integer, Map.Entry<String, Integer>> entry : orderedAreas) {
                        int objectId = entry.getKey();
                        String areaKey = entry.getValue().getKey();
                        int nobjectId = entry.getValue().getValue();
                        
                        if ("summary".equals(areaKey)) {
                            applySummaryChanges(conn, normalizedFacetName, objectId, nobjectId);
                        } else if (areaKey.startsWith("impact#")) {
                            if (areaKey.toLowerCase().contains("process_x_process")) {
                                continue;
                            }
                            logger.info("🔗 [RELATIONSHIPS] Applying impact changes: {} for facet: {}", areaKey, normalizedFacetName);
                            logger.info("🔗 [RELATIONSHIPS] Parameters: mainObjectId={}, mainNobjectId={}, entryObjectId={}, entryNobjectId={}", 
                                mainObjectId, mainNobjectId, objectId, nobjectId);
                            applyImpactChanges(conn, normalizedFacetName, mainObjectId, mainNobjectId, areaKey, objectId, nobjectId);
                        } else if (areaKey.startsWith("stakeholders#")) {
                            // Skip stakeholders
                        } else if (areaKey.startsWith("relationships#")) {
                            logger.info("🔗 [RELATIONSHIPS] Applying relationship changes: {} for facet: {}", areaKey, normalizedFacetName);
                            applyRelationshipChanges(conn, normalizedFacetName, mainObjectId, mainNobjectId, areaKey, objectId, nobjectId);
                        } else if ("data-content".equals(areaKey) && "system".equals(normalizedFacetName)) {
                            logger.info("🔗 [DATA-CONTENT] Applying data-content changes for system: {}", mainObjectId);
                            applyDataContentChanges(conn, mainObjectId, mainNobjectId, nobjectId);
                        } else if (areaKey.startsWith("documents#")) {
                            logger.info("🔗 [DOCUMENTS] Applying document changes: {} for facet: {}", areaKey, normalizedFacetName);
                            applyDocumentChanges(conn, normalizedFacetName, mainObjectId, mainNobjectId, areaKey, nobjectId);
                        } else if (areaKey.equals("summary#attribute")) {
                            applyAttributeChanges(conn, mainObjectId, mainNobjectId);
                        } else if (areaKey.equals("summary#dataset_value_info")) {
                            applyValueInfoChanges(conn, mainObjectId, mainNobjectId);
                        }
                    }
                }
                
                conn.commit();
                logger.info("✅ [AUTO CR] Successfully completed CR {}", changeRequestId);
                
                // Clean up ThreadLocal Set to avoid memory leaks
                deletedOldRowsTracker.remove();
                
            } catch (SQLException e) {
                logger.error("═══════════════════════════════════════════════════════════════");
                logger.error("❌ [ERROR] Error applying changes for CR {}: {}", changeRequestId, e.getMessage(), e);
                logger.error("🔄 [ROLLBACK] Rolling back transaction");
                logger.error("═══════════════════════════════════════════════════════════════");
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
                logger.info("🔓 [TRANSACTION] Transaction ended (auto-commit re-enabled)");
                
                // Clean up ThreadLocal Set to avoid memory leaks (always clear, even on error)
                deletedOldRowsTracker.remove();
            }
        }
    }

    /**
     * Discard all pending changes for a CR: delete nobject_id (new data), keep object_id (original data)
     * This is called when CR status becomes "Deleted"
     * Logic: All changes are rejected, so new data (nobject_id) is discarded and original data (object_id) remains
     */
    public void discardChangesForCR(int changeRequestId) throws SQLException {
        logger.info("Discarding changes for CR {}", changeRequestId);
        
        // Get all mappings for this CR
        Map<String, Map<Integer, Map<String, List<Integer>>>> allMappings = 
            facetChangesDAO.getAllMappingsByChangeRequest(changeRequestId);
        
        if (allMappings.isEmpty()) {
            logger.info("No pending changes found for CR {}", changeRequestId);
            return;
        }
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            
            try {
                // Process each facet
                for (Map.Entry<String, Map<Integer, Map<String, List<Integer>>>> facetEntry : allMappings.entrySet()) {
                    String facetName = facetEntry.getKey();
                    Map<Integer, Map<String, List<Integer>>> objectMappings = facetEntry.getValue();
                    
                    // IMPORTANT: Process areas in correct order to avoid FK constraint issues
                    // Delete child records (attributes, value info, relationships) BEFORE deleting parent (summary/cloned object)
                    
                    // Collect all areas to process in order - flatten List<Integer> into individual entries
                    List<Map.Entry<Integer, Map.Entry<String, Integer>>> orderedAreas = new ArrayList<>();
                    for (Map.Entry<Integer, Map<String, List<Integer>>> objectEntry : objectMappings.entrySet()) {
                        int objectId = objectEntry.getKey();
                        for (Map.Entry<String, List<Integer>> areaEntry : objectEntry.getValue().entrySet()) {
                            String areaKey = areaEntry.getKey();
                            for (int nobjectId : areaEntry.getValue()) {
                                orderedAreas.add(new java.util.AbstractMap.SimpleEntry<>(objectId, 
                                    new java.util.AbstractMap.SimpleEntry<>(areaKey, nobjectId)));
                            }
                        }
                    }
                    
                    // Sort areas - process child records first, summary last
                    orderedAreas.sort((a, b) -> {
                        String areaA = a.getValue().getKey();
                        String areaB = b.getValue().getKey();
                        int priorityA = getAreaProcessingPriority(areaA);
                        int priorityB = getAreaProcessingPriority(areaB);
                        return Integer.compare(priorityA, priorityB);
                    });
                    
                    // IMPORTANT: Save snapshot BEFORE deleting cloned rows!
                    // This ensures we capture the actual data before it's deleted
                    logger.info("   📸 Saving changes snapshot BEFORE deleting cloned rows for facet: {}", facetName);
                    for (Map.Entry<Integer, Map.Entry<String, Integer>> snapshotEntry : orderedAreas) {
                        int objectId = snapshotEntry.getKey();
                        String areaKey = snapshotEntry.getValue().getKey();
                        int nobjectId = snapshotEntry.getValue().getValue();
                            
                            // Save snapshot of changes for ALL area types BEFORE deleting cloned rows
                            saveChangesSnapshot(conn, facetName, changeRequestId, objectId, nobjectId, areaKey);
                    }
                    
                    // Now process each area - delete cloned rows AFTER saving snapshot
                    for (Map.Entry<Integer, Map.Entry<String, Integer>> entry : orderedAreas) {
                        int objectId = entry.getKey();
                        String areaKey = entry.getValue().getKey();
                        int nobjectId = entry.getValue().getValue();
                        
                        logger.info("Discarding changes: facet={}, objectId={}, area={}, nobjectId={}", 
                            facetName, objectId, areaKey, nobjectId);
                        
                        if ("summary".equals(areaKey)) {
                            // Delete cloned summary row - LAST to avoid FK issues
                            deleteClonedRow(conn, facetName, nobjectId);
                        } else if (areaKey.startsWith("impact#")) {
                            // Delete cloned impact rows
                            deleteImpactRows(conn, facetName, nobjectId, areaKey);
                        } else if (areaKey.startsWith("stakeholders#")) {
                            // Stakeholders are excluded from pending changes - skip deleting
                            // Stakeholders changes are applied immediately, so there's nothing to discard
                            logger.info("   └─ SKIPPING stakeholder deletion (stakeholders excluded from pending changes)");
                        } else if (areaKey.startsWith("relationships#")) {
                            // Delete the relationship record that was added during CR
                            // nobjectId is the ID of the relationship record itself
                            deleteRelationshipRecordById(conn, nobjectId, areaKey);
                        } else if ("data-content".equals(areaKey) && "system".equals(facetName)) {
                            // Delete the data-content record (glossary_x_system) that was added during CR
                            // nobjectId is the ID of the glossary_x_system record itself
                            logger.info("Discarding data-content changes (glossary_x_system ID: {})", nobjectId);
                            String sql = "DELETE FROM glossary_x_system WHERE ID = ?";
                            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                                stmt.setInt(1, nobjectId);
                                int rowsDeleted = stmt.executeUpdate();
                                logger.info("   └─ Deleted data-content record {} from glossary_x_system, rows: {}", nobjectId, rowsDeleted);
                            }
                        } else if (areaKey.startsWith("documents#")) {
                            // Delete the document record that was added during CR
                            // nobjectId is the ID of the document relationship record itself
                            // areaKey format: "documents#system_x_document" or "documents#glossary_x_document", etc.
                            String[] parts = areaKey.split("#");
                            if (parts.length >= 2) {
                                String tableName = parts[1].toLowerCase();
                                logger.info("Discarding document changes ({} ID: {})", tableName, nobjectId);
                                String sql = "DELETE FROM " + tableName + " WHERE ID = ?";
                                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                                    stmt.setInt(1, nobjectId);
                                    int rowsDeleted = stmt.executeUpdate();
                                    logger.info("   └─ Deleted document record {} from {}, rows: {}", nobjectId, tableName, rowsDeleted);
                                }
                            } else {
                                logger.warn("   └─ Invalid documents area key format: {}", areaKey);
                            }
                        } else if (areaKey.equals("summary#attribute")) {
                            // For attributes, nobjectId is the attribute ID
                            // Check if this is a new attribute (object_id = 0 or object_id = nobject_id) or an update
                            // If object_id = 0 or object_id = nobject_id, it's a new attribute - delete it
                            // If object_id != nobject_id, it's an update - we can't revert in-place changes easily
                            // For now, we'll check if the attribute exists and was created during this CR
                            logger.info("Discarding attribute changes (attribute ID: {})", nobjectId);
                            // New logic: check if attribute belongs to cloned dataset
                            // If so, it was created during CR and should be deleted
                            // The attribute will be cascade-deleted when cloned dataset is deleted anyway
                            logger.info("   └─ Attribute {} will be cascade-deleted with cloned dataset", nobjectId);
                        } else if (areaKey.equals("summary#dataset_value_info")) {
                            // For value info, nobjectId is the values_datastore ID
                            // Check if this is a new value info (object_id = 0 or object_id = nobject_id) or an update
                            logger.info("Discarding value info changes (datastore ID: {})", nobjectId);
                            // New logic: value info records will be cascade-deleted when cloned dataset is deleted
                            logger.info("   └─ Value info {} will be cascade-deleted with cloned dataset", nobjectId);
                        }
                    }
                    
                    // OLD CODE (commented out - do not delete mappings):
                    // Delete all mappings for this CR
                    // facetChangesDAO.deleteByChangeRequest(facetName, changeRequestId);
                    // logger.info("Deleted mappings for CR {} in facet {}", changeRequestId, facetName);
                    logger.info("   ℹ️  Preserving mappings in facet_changes tables for history (not deleting)");
                    logger.info("   ℹ️  Mappings and snapshots will remain available for 'CHANGES TO REVIEW' section");
                }
                
                conn.commit();
                logger.info("Successfully discarded all changes for CR {}", changeRequestId);
                
            } catch (SQLException e) {
                conn.rollback();
                logger.error("Error discarding changes for CR {}: {}", changeRequestId, e.getMessage(), e);
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /**
     * Apply summary changes: copy data from nobject_id to object_id and keep object_id
     * When completed, all changes are accepted, so:
     * 1. Copy all data from nobject_id row to object_id row
     * 2. Delete nobject_id row completely
     * 3. object_id remains as the final ID (unchanged)
     */
    private void applySummaryChanges(Connection conn, String facetName, int objectId, int nobjectId) throws SQLException {
        String tableName = getMainTableName(facetName);
        if (tableName == null) {
            logger.warn("⚠️  Unknown facet for applying summary changes: {}", facetName);
            return;
        }
        
        logger.info("   🔄 [SUMMARY] Facet: {}, Table: {}", facetName, tableName);
        logger.info("      └─ Object ID (to keep): {}", objectId);
        logger.info("      └─ NObject ID (source data, to delete): {}", nobjectId);
        
        // Diagnostic: Check if both objects exist before attempting update
        String idColumnName = getIdColumnName(facetName);
        String checkObjectSql = "SELECT COUNT(*) as cnt FROM " + tableName + " WHERE " + idColumnName + " = ?";
        try (PreparedStatement checkStmt = conn.prepareStatement(checkObjectSql)) {
            checkStmt.setInt(1, objectId);
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next()) {
                    int count = rs.getInt("cnt");
                    if (count == 0) {
                        logger.warn("      ⚠️  [DIAGNOSTIC] No row found with {}={} in {} table to update", idColumnName, objectId, tableName);
                    } else {
                        logger.info("      ✅ [DIAGNOSTIC] Found row with {}={} in {} table", idColumnName, objectId, tableName);
                    }
                }
            }
        }
        
        try (PreparedStatement checkStmt = conn.prepareStatement(checkObjectSql)) {
            checkStmt.setInt(1, nobjectId);
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next()) {
                    int count = rs.getInt("cnt");
                    if (count == 0) {
                        logger.warn("      ⚠️  [DIAGNOSTIC] No row found with {}={} in {} table", idColumnName, nobjectId, tableName);
                    } else {
                        logger.info("      ✅ [DIAGNOSTIC] Found row with {}={} in {} table", idColumnName, nobjectId, tableName);
                    }
                }
            }
        }
        
        // Step 1: Copy all data from nobject_id row to object_id row
        logger.info("      └─ [STEP 1] Copying data from nobject_id {} to object_id {}...", nobjectId, objectId);
        String updateSql = buildUpdateSql(facetName, tableName, objectId, nobjectId);
        if (updateSql == null) {
            logger.error("      ❌ Could not build update SQL for facet: {}", facetName);
            return;
        }
        
        try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
            // First parameter: source ID (nobject_id) for JOIN
            stmt.setInt(1, nobjectId);
            // Second parameter: destination ID (object_id) for WHERE
            stmt.setInt(2, objectId);
            
            // Log the SQL for debugging (replace placeholders for logging)
            String logSql = updateSql.replaceFirst("\\?", String.valueOf(nobjectId)).replaceFirst("\\?", String.valueOf(objectId));
            logger.info("      └─ Executing UPDATE SQL: {}", logSql);
            
            int rowsUpdated = stmt.executeUpdate();
            if (rowsUpdated > 0) {
                logger.info("      ✅ Copied all data from nobject_id {} to object_id {} in {} table ({} row(s) updated)", 
                    nobjectId, objectId, tableName, rowsUpdated);
            } else {
                logger.warn("      ⚠️  No row found with {}={} in {} table to update", getIdColumnName(facetName), objectId, tableName);
            }
        } catch (SQLException e) {
            logger.error("      ❌ SQL Error updating {} table: {}", tableName, e.getMessage());
            logger.error("      ❌ SQL: {}", updateSql.replaceFirst("\\?", String.valueOf(nobjectId)).replaceFirst("\\?", String.valueOf(objectId)));
            throw e;
        }
        
        // Step 2: Update all foreign key references from nobject_id to object_id
        // This must be done BEFORE deleting nobject_id to avoid foreign key constraint violations
        // (e.g., dataset_audit_history table references dataset.ID)
        logger.info("      └─ [STEP 2] Updating foreign key references from nobject_id {} to object_id {}...", nobjectId, objectId);
        updateForeignKeysFromNobjectToObject(conn, facetName, objectId, nobjectId);
        
        // Step 3: Delete remaining child records that reference nobject_id
        // Some child records (e.g., audit_history) cannot be moved and must be deleted
        logger.info("      └─ [STEP 3] Deleting remaining child records for nobject_id {}...", nobjectId);
        deleteChildRecordsForClonedRow(conn, facetName, nobjectId);
        
        // Step 4: Delete the nobject_id row completely (source data is now in object_id, FKs updated)
        logger.info("      └─ [STEP 4] Deleting nobject_id row from {} table...", tableName);
        String deleteSql = "DELETE FROM " + tableName + " WHERE " + idColumnName + " = ?";
        logger.info("      └─ DELETE SQL: {}", deleteSql.replace("?", String.valueOf(nobjectId)));
        try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
            stmt.setInt(1, nobjectId);
            int rowsDeleted = stmt.executeUpdate();
            if (rowsDeleted > 0) {
                logger.info("      ✅ Deleted {} row(s) with {}={} from {} (data copied to {}={})", 
                    rowsDeleted, idColumnName, nobjectId, tableName, idColumnName, objectId);
            } else {
                logger.warn("      ⚠️  No row found with {}={} in {} table", idColumnName, nobjectId, tableName);
            }
        } catch (SQLException e) {
            logger.error("      ❌ SQL Error deleting from {} table: {}", tableName, e.getMessage());
            logger.error("      ❌ SQL: {}", deleteSql.replace("?", String.valueOf(nobjectId)));
            throw e;
        }
    }

    /**
     * Update Change Request Reference field to point to nobject_id instead of object_id
     * After replacing object_id with nobject_id, the CR's Reference must be updated
     * to maintain the connection to the new object ID.
     * 
     * @param conn Database connection
     * @param changeRequestId The change request ID
     * @param allMappings All facet mappings containing object_id -> nobject_id relationships
     */
    private void updateChangeRequestReference(Connection conn, int changeRequestId, 
                                               Map<String, Map<Integer, Map<String, Integer>>> allMappings) throws SQLException {
        logger.info("   🔗 Processing Change Request Reference update...");
        
        try {
            // Process each facet that has pending changes
            for (Map.Entry<String, Map<Integer, Map<String, Integer>>> facetEntry : allMappings.entrySet()) {
                String facetName = facetEntry.getKey();
                Map<Integer, Map<String, Integer>> objectMappings = facetEntry.getValue();
                
                logger.info("      └─ Facet: {}", facetName);
                
                // Process each object mapping
                for (Map.Entry<Integer, Map<String, Integer>> objectEntry : objectMappings.entrySet()) {
                    int objectId = objectEntry.getKey();
                    Map<String, Integer> areaMappings = objectEntry.getValue();
                    
                    logger.info("         └─ Old Object ID: {}", objectId);
                    
                    // Get nobject_id from summary area (main object replacement)
                    Integer nobjectId = areaMappings.get("summary");
                    if (nobjectId == null || nobjectId.equals(objectId)) {
                        // Skip if no summary mapping or IDs are the same
                        logger.info("         └─ ⚠️  Skipping: no summary mapping or IDs are the same");
                        continue;
                    }
                    
                    logger.info("         └─ New Object ID: {}", nobjectId);
                    
                    // Build new reference string
                    // Format: "FacetName ObjectId" (e.g., "Glossary 13" -> "Glossary 20")
                    String newReference = buildReference(facetName, nobjectId);
                    
                    logger.info("         └─ New Reference: \"{}\"", newReference);
                    
                    // Get current reference for logging
                    String currentRefSql = "SELECT Reference FROM changerequest WHERE ID = ?";
                    String currentReference = null;
                    try (PreparedStatement checkStmt = conn.prepareStatement(currentRefSql)) {
                        checkStmt.setInt(1, changeRequestId);
                        try (ResultSet rs = checkStmt.executeQuery()) {
                            if (rs.next()) {
                                currentReference = rs.getString("Reference");
                                logger.info("         └─ Current CR Reference: \"{}\"", currentReference);
                            }
                        }
                    }
                    
                    // Direct update: Update reference to new object ID
                    // We know the CR ID, so we can update directly without matching the old reference
                    // This ensures the Affected Item hyperlink will point to nobject_id after completion
                    String updateSql = "UPDATE changerequest SET Reference = ? WHERE ID = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
                        stmt.setString(1, newReference);
                        stmt.setInt(2, changeRequestId);
                        
                        int rowsUpdated = stmt.executeUpdate();
                        if (rowsUpdated > 0) {
                            logger.info("         ✅ Successfully updated CR {} reference: \"{}\" -> \"{}\"", 
                                changeRequestId, currentReference, newReference);
                            logger.info("         ✅ Affected Item hyperlink will now point to {} (nobject_id)", nobjectId);
                        } else {
                            logger.warn("         ⚠️  No rows updated for CR {} reference. " +
                                      "Current: \"{}\", New: \"{}\". CR may not exist.", 
                                changeRequestId, currentReference, newReference);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("         ❌ Error updating Change Request reference for CR {}: {}", 
                changeRequestId, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Build reference string in the format "FacetName ObjectId"
     * Handles facet name variations (e.g., "Data Set" vs "Dataset")
     * 
     * @param facetName The facet name
     * @param objectId The object ID
     * @return Formatted reference string
     */
    private String buildReference(String facetName, int objectId) {
        // Normalize facet name for reference
        String normalizedFacetName = normalizeFacetNameForReference(facetName);
        return normalizedFacetName + " " + objectId;
    }
    
    /**
     * Normalize facet name for use in Change Request Reference field
     * Converts internal names to display names used in references
     * 
     * @param facetName The facet name (may be "dataset", "data set", etc.)
     * @return Normalized facet name for reference
     */
    private String normalizeFacetNameForReference(String facetName) {
        if (facetName == null) {
            return "Unknown";
        }
        
        String lowerName = facetName.toLowerCase().trim();
        switch (lowerName) {
            case "dataset":
            case "data set":
                return "Data Set";  // Display name used in references
            case "glossary":
                return "Glossary";
            case "system":
                return "System";
            case "process":
                return "Process";
            default:
                // Capitalize first letter of each word
                String[] words = facetName.split("\\s+");
                StringBuilder result = new StringBuilder();
                for (int i = 0; i < words.length; i++) {
                    if (i > 0) result.append(" ");
                    if (words[i].length() > 0) {
                        result.append(words[i].substring(0, 1).toUpperCase());
                        if (words[i].length() > 1) {
                            result.append(words[i].substring(1).toLowerCase());
                        }
                    }
                }
                return result.toString();
        }
    }

    /**
     * Build UPDATE SQL to copy data from nobject_id to object_id
     */
    private String buildUpdateSql(String facetName, String tableName, int objectId, int nobjectId) {
        // Build UPDATE statement based on facet type
        switch (facetName.toLowerCase()) {
            case "glossary":
                return "UPDATE " + tableName + " g1 " +
                       "INNER JOIN " + tableName + " g2 ON g2.ID = ? " +
                       "SET g1.Name = g2.Name, " +
                       "    g1.Description = g2.Description, " +
                       "    g1.Format = g2.Format, " +
                       "    g1.LDM = g2.LDM, " +
                       "    g1.Business_Logic = g2.Business_Logic, " +
                       "    g1.Examples = g2.Examples, " +
                       "    g1.Ref_Number = g2.Ref_Number, " +
                       "    g1.Format_type = g2.Format_type, " +
                       "    g1.Parent_ID = g2.Parent_ID, " +
                       "    g1.Status = g2.Status, " +
                       "    g1.Lifecycle = g2.Lifecycle, " +
                       "    g1.Is_Public = g2.Is_Public, " +
                       "    g1.Type = g2.Type, " +
                       "    g1.Security_Classification = g2.Security_Classification, " +
                       "    g1.KDE = g2.KDE, " +
                       "    g1.Confidentiality_Rating = g2.Confidentiality_Rating, " +
                       "    g1.Integrity_Rating = g2.Integrity_Rating, " +
                       "    g1.Availability_Rating = g2.Availability_Rating, " +
                       "    g1.Last_updated_userID = g2.Last_updated_userID, " +
                       "    g1.Last_Updated_Datetime = CURRENT_TIMESTAMP " +
                       "WHERE g1.ID = ?";
                       
            case "dataset":
            case "data set":
                return "UPDATE " + tableName + " d1 " +
                       "INNER JOIN " + tableName + " d2 ON d2.ID = ? " +
                       "SET d1.RefNumber = d2.RefNumber, " +
                       "    d1.PrimaryName = d2.PrimaryName, " +
                       "    d1.definition = d2.definition, " +
                       "    d1.MasterSource = d2.MasterSource, " +
                       "    d1.glossary = d2.glossary, " +
                       "    d1.`Usage` = d2.`Usage`, " +
                       "    d1.status = d2.status, " +
                       "    d1.DatasetType = d2.DatasetType, " +
                       "    d1.AccessControlType = d2.AccessControlType, " +
                       "    d1.lifecycle = d2.lifecycle, " +
                       "    d1.LastUpdateUser_ID = d2.LastUpdateUser_ID, " +
                       "    d1.LastUpdateDatetime = CURRENT_TIMESTAMP " +
                       "WHERE d1.ID = ?";
                       
            case "system":
                return "UPDATE " + tableName + " s1 " +
                       "INNER JOIN " + tableName + " s2 ON s2.id = ? " +
                       "SET s1.Name = s2.Name, " +
                       "    s1.Long_Name = s2.Long_Name, " +
                       "    s1.Description = s2.Description, " +
                       "    s1.Type = s2.Type, " +
                       "    s1.External = s2.External, " +
                       "    s1.URL = s2.URL, " +
                       "    s1.Status = s2.Status, " +
                       "    s1.Lifecycle = s2.Lifecycle, " +
                       "    s1.Is_Public = s2.Is_Public, " +
                       "    s1.Confidentiality_Rating = s2.Confidentiality_Rating, " +
                       "    s1.Integrity_Rating = s2.Integrity_Rating, " +
                       "    s1.Availability_Rating = s2.Availability_Rating, " +
                       "    s1.AssetID = s2.AssetID, " +
                       "    s1.Classification = s2.Classification, " +
                       "    s1.DQ_Automation = s2.DQ_Automation, " +
                       "    s1.Parent_ID = s2.Parent_ID, " +
                       "    s1.Last_updated_userID = s2.Last_updated_userID, " +
                       "    s1.Last_Updated_Datetime = CURRENT_TIMESTAMP " +
                       "WHERE s1.id = ?";
                       
            case "process":
                return "UPDATE " + tableName + " p1 " +
                       "INNER JOIN " + tableName + " p2 ON p2.id = ? " +
                       "SET p1.primaryname = p2.primaryname, " +
                       "    p1.description = p2.description, " +
                       "    p1.parentid = p2.parentid, " +
                       "    p1.status = p2.status, " +
                       "    p1.type = p2.type, " +
                       "    p1.duration_type = p2.duration_type, " +
                       "    p1.duration = p2.duration, " +
                       "    p1.lifecycle_status = p2.lifecycle_status, " +
                       "    p1.processclass_id = p2.processclass_id, " +
                       "    p1.processautomation_id = p2.processautomation_id, " +
                       "    p1.refnumber = p2.refnumber, " +
                       "    p1.input_description = p2.input_description, " +
                       "    p1.output_description = p2.output_description, " +
                       "    p1.step_type = p2.step_type, " +
                       "    p1.ispublic = p2.ispublic, " +
                       "    p1.cancreate = p2.cancreate, " +
                       "    p1.canread = p2.canread, " +
                       "    p1.canupdate = p2.canupdate, " +
                       "    p1.candelete = p2.candelete, " +
                       "    p1.canarchive = p2.canarchive, " +
                       "    p1.lastupdateuser_id = p2.lastupdateuser_id, " +
                       "    p1.lastupdatedatetime = CURRENT_TIMESTAMP " +
                       "WHERE p1.id = ?";
                       
            default:
                logger.warn("Unknown facet for building update SQL: {}", facetName);
                return null;
        }
    }

    /**
     * Apply impact changes (relationship tables)
     * Format: impact#glossary_X_product, impact#dataset_X_system, etc.
     * When completed: replace old rows in original with new rows from cloned
     * Logic: 
     * 1. DELETE old rows in original (object_id)
     * 2. INSERT new rows from cloned (nobject_id) to original (object_id)
     * 3. DELETE rows in cloned (nobject_id)
     */
    private void applyImpactChanges(Connection conn, String facetName, int objectId, int nobjectId, 
                                   String areaKey, int entryObjectId, int entryNobjectId) throws SQLException {
        // Parse areaKey: impact#glossary_X_product -> actual table name and sourceColumn
        String[] parts = areaKey.split("#");
        if (parts.length < 2) {
            logger.warn("Invalid impact area key format: {}", areaKey);
            return;
        }
        
        String relationshipInfo = parts[1]; // e.g., "glossary_X_product", "system_X_product", "process_X_process"
        String[] relParts = relationshipInfo.split("_X_");
        if (relParts.length < 2) {
            logger.warn("Invalid relationship format in area key: {}", areaKey);
            return;
        }
        
        // Get actual table name (handles reversed tables like product_x_system)
        String tableName = getImpactTableName(facetName, relationshipInfo);
        if (tableName == null) {
            logger.warn("Could not determine table name for impact area: {}", areaKey);
            return;
        }
        
        String expectedSourceColumn = getImpactSourceColumn(facetName, areaKey, tableName);
        
        if (expectedSourceColumn == null) {
            logger.warn("Could not determine source column for impact area: {}", areaKey);
            return;
        }
        
        // Find the actual column name in the table (handles variations like process_id vs processid)
        String sourceColumn = findActualSourceColumn(conn, tableName, expectedSourceColumn);
        if (sourceColumn == null) {
            logger.error("            ❌ Source column '{}' not found in table {} (and no variations match)", expectedSourceColumn, tableName);
            throw new SQLException("Source column '" + expectedSourceColumn + "' not found in table " + tableName);
        }
        
        // For all impact areas:
        // Use entryNobjectId (from impact area mapping) to find relationships
        // This is the ID that was used when saving the relationships in the servlet
        // For glossary impact, this should be the same as main nobjectId (cloned glossary ID)
        // But we use entryNobjectId to be safe in case they differ
        // Fallback to mainNobjectId if entryNobjectId is 0 (CREATE CR) or invalid
        int impactNobjectId = (entryNobjectId > 0) ? entryNobjectId : nobjectId;
        boolean useRelationshipRecordId = false; // Always use sourceColumn, not ID
        
        logger.info("         └─ [IMPACT] Using impactNobjectId {} with {} = {}", 
            impactNobjectId, sourceColumn, impactNobjectId);
        logger.info("         └─ [IMPACT] mainNobjectId={}, entryNobjectId={}, final impactNobjectId={}", 
            nobjectId, entryNobjectId, impactNobjectId);
        
        logger.info("         └─ [IMPACT] Table: {}, Column: {} (expected: {})", tableName, sourceColumn, expectedSourceColumn);
        logger.info("            └─ Copying rows from nobject_id {} to object_id {}", impactNobjectId, objectId);
        
        try {
            // Step 1: Get table metadata to build INSERT...SELECT statement
            // We need to copy all columns except auto-increment ID, replacing nobject_id with object_id
            DatabaseMetaData metaData = conn.getMetaData();
            try (ResultSet columns = metaData.getColumns(null, null, tableName, null)) {
                List<String> columnNames = new ArrayList<>();
                List<String> selectExpressions = new ArrayList<>();
                
                boolean sourceColumnFound = false;
                while (columns.next()) {
                    String columnName = columns.getString("COLUMN_NAME");
                    String isAutoIncrement = columns.getString("IS_AUTOINCREMENT");
                    
                    // Skip ID column - we'll let the database generate a new ID
                    // This is critical for tables like process_x_process where ID is the primary key
                    // but not auto-increment (it's manually managed via getNextId())
                    if (columnName.equalsIgnoreCase("ID")) {
                        logger.info("            └─ ⏭️  Skipping ID column '{}' (will generate new ID for copied record)", columnName);
                        continue;
                    }
                    
                    // Skip auto-increment columns (for other tables that might have auto-increment non-ID columns)
                    if ("YES".equals(isAutoIncrement)) {
                        logger.info("            └─ ⏭️  Skipping auto-increment column '{}'", columnName);
                        continue;
                    }
                    
                    columnNames.add(columnName);
                    
                    // Replace nobject_id with object_id in the source column
                    if (columnName.equalsIgnoreCase(sourceColumn)) {
                        selectExpressions.add(objectId + " AS " + columnName); // Use object_id directly
                        sourceColumnFound = true;
                        logger.info("            └─ ✅ Found source column '{}' in table {} - will replace with object_id {}", 
                            columnName, tableName, objectId);
                    } else {
                        selectExpressions.add(columnName);
                    }
                }
                
                if (columnNames.isEmpty()) {
                    logger.error("            ❌ [CRITICAL] No columns found for table {}!", tableName);
                    throw new SQLException("No columns found for table " + tableName);
                }
                
                if (!sourceColumnFound) {
                    logger.error("            ❌ [CRITICAL] Source column '{}' NOT FOUND in table {} columns!", sourceColumn, tableName);
                    logger.error("            ❌ Available columns: {}", String.join(", ", columnNames));
                    throw new SQLException("Source column '" + sourceColumn + "' not found in table " + tableName + ". Available columns: " + String.join(", ", columnNames));
                }
                
                // Pre-check: Check if rows exist with nobject_id before attempting copy
                // This is needed to decide whether to delete old relationships
                String whereClause;
                if (useRelationshipRecordId) {
                    whereClause = "ID = ?";
                } else {
                    whereClause = sourceColumn + " = ?";
                }
                
                String checkSql = "SELECT COUNT(*) as cnt FROM " + tableName + " WHERE " + whereClause;
                int relationshipCount = 0;
                try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                    checkStmt.setInt(1, impactNobjectId);
                    try (ResultSet rs = checkStmt.executeQuery()) {
                        if (rs.next()) {
                            relationshipCount = rs.getInt("cnt");
                            logger.info("            └─ [PRE-CHECK] Found {} row(s) in {} with {} = {}", relationshipCount, tableName, sourceColumn, impactNobjectId);
                            if (relationshipCount == 0) {
                                // Additional diagnostic: show sample IDs in the table
                                String sampleSql = "SELECT " + sourceColumn + " FROM " + tableName + " LIMIT 10";
                                try (PreparedStatement sampleStmt = conn.prepareStatement(sampleSql);
                                     ResultSet sampleRs = sampleStmt.executeQuery()) {
                                    List<Integer> sampleIds = new ArrayList<>();
                                    while (sampleRs.next()) {
                                        sampleIds.add(sampleRs.getInt(1));
                                    }
                                    logger.warn("            ⚠️  [PRE-CHECK] No rows found with {} = {} in {}. Sample IDs in table: {}", 
                                        sourceColumn, impactNobjectId, tableName, sampleIds);
                                    
                                    // Fallback: Check if relationships exist with original objectId
                                    // This handles the case where relationships were saved to original ID instead of cloned ID
                                    if (objectId > 0 && objectId != impactNobjectId) {
                                        String fallbackCheckSql = "SELECT COUNT(*) as cnt FROM " + tableName + " WHERE " + sourceColumn + " = ?";
                                        try (PreparedStatement fallbackStmt = conn.prepareStatement(fallbackCheckSql)) {
                                            fallbackStmt.setInt(1, objectId);
                                            try (ResultSet fallbackRs = fallbackStmt.executeQuery()) {
                                                if (fallbackRs.next()) {
                                                    int fallbackCount = fallbackRs.getInt("cnt");
                                                    if (fallbackCount > 0) {
                                                        logger.info("            └─ [FALLBACK] Found {} relationship(s) with original objectId {} - these are already in final location", 
                                                            fallbackCount, objectId);
                                                        logger.info("            ✅ [EARLY RETURN] Skipping all steps - relationships already correct, nothing to copy or delete");
                                                        // CRITICAL FIX: Return early - relationships are already where they should be
                                                        // Do NOT delete, do NOT copy, do NOT touch them
                                                        return;
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Step 1: DELETE old rows in original (object_id) first
                // This ensures we replace old data with new data, not merge them
                // This applies to all impact tables including process_x_process (predecessor)
                // IMPORTANT: Only delete if we have relationships to copy from cloned ID
                // This prevents data loss if relationships were never saved to cloned ID
                logger.info("            └─ [STEP 1] Checking if we should delete old rows in original (object_id {})...", objectId);
                
                // Only delete old relationships if we have new ones to copy
                // If relationshipCount is 0, we already checked fallback and returned early if found
                boolean shouldDeleteOld = (relationshipCount > 0);
                
                if (shouldDeleteOld) {
                    String deleteOldSql = "DELETE FROM " + tableName + " WHERE " + sourceColumn + " = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(deleteOldSql)) {
                        stmt.setInt(1, objectId);
                        int rowsDeletedOld = stmt.executeUpdate();
                        if (rowsDeletedOld > 0) {
                            logger.info("            ✅ Deleted {} old impact row(s) in original (object_id {}) from {}", 
                                rowsDeletedOld, objectId, tableName);
                        } else {
                            logger.info("            ℹ️  No old impact rows to delete in {} (no references to object_id {})", tableName, objectId);
                        }
                    } catch (SQLException sqlEx) {
                        logger.error("            ❌ SQL Error in DELETE (old rows): {}", sqlEx.getMessage());
                        logger.error("            ❌ SQL: {}", deleteOldSql);
                        throw sqlEx;
                    }
                } else {
                    logger.warn("            ⚠️  [SAFETY] Skipping deletion of old relationships - no relationships found with cloned ID {} to replace them", impactNobjectId);
                    logger.warn("            ⚠️  [SAFETY] This prevents data loss if relationships were never saved to cloned ID");
                }
                
                // Step 2: INSERT new rows from cloned (impactNobjectId) to original (object_id)
                // For process_x_process, use WHERE ID = entryNobjectId (relationship record ID)
                // For other tables, use WHERE sourceColumn = impactNobjectId
                // whereClause already defined in pre-check above
                if (useRelationshipRecordId) {
                    logger.info("            └─ [IMPACT] Using WHERE ID = {} (relationship record ID for process_x_process)", impactNobjectId);
                } else {
                    logger.info("            └─ [IMPACT] Using WHERE {} = {} (cloned object ID)", sourceColumn, impactNobjectId);
                }
                
                // Check if ID column exists and needs to be generated (not auto-increment)
                boolean needsIdColumn = false;
                int baseId = 0;
                try (ResultSet pkColumns = metaData.getPrimaryKeys(null, null, tableName)) {
                    if (pkColumns.next()) {
                        String pkColumnName = pkColumns.getString("COLUMN_NAME");
                        if (pkColumnName.equalsIgnoreCase("ID")) {
                            // Check if ID is auto-increment
                            try (ResultSet idColumn = metaData.getColumns(null, null, tableName, "ID")) {
                                if (idColumn.next()) {
                                    String isAutoIncrement = idColumn.getString("IS_AUTOINCREMENT");
                                    if (!"YES".equals(isAutoIncrement)) {
                                        needsIdColumn = true;
                                        // Get the next available ID
                                        try (PreparedStatement stmt = conn.prepareStatement("SELECT COALESCE(MAX(ID), 0) FROM " + tableName)) {
                                            try (ResultSet rs = stmt.executeQuery()) {
                                                if (rs.next()) {
                                                    baseId = rs.getInt(1);
                                                }
                                            }
                                        }
                                        logger.info("            └─ ID column found but not auto-increment - will generate IDs starting from {}", baseId + 1);
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Build INSERT statement with ID column if needed
                List<String> insertColumnNames = new ArrayList<>(columnNames);
                List<String> insertSelectExpressions = new ArrayList<>(selectExpressions);
                
                if (needsIdColumn) {
                    // Add ID column at the beginning
                    insertColumnNames.add(0, "ID");
                    // Use MySQL user variable to generate sequential IDs for multiple rows
                    insertSelectExpressions.add(0, "(@row_number := @row_number + 1) + " + baseId + " AS ID");
                    logger.info("            └─ Adding ID column with sequential generation (base: {})", baseId);
                }
                
                // Initialize MySQL user variable if needed
                if (needsIdColumn) {
                    try (PreparedStatement stmt = conn.prepareStatement("SET @row_number = 0")) {
                        stmt.execute();
                        logger.info("            └─ Initialized MySQL user variable @row_number = 0");
                    }
                }
                
                String insertSql = "INSERT INTO " + tableName + " (" + 
                    String.join(", ", insertColumnNames) + ") " +
                    "SELECT " + String.join(", ", insertSelectExpressions) + 
                    " FROM " + tableName + " WHERE " + whereClause;
                
                logger.info("            └─ [STEP 2] Copying rows from nobject_id {} to object_id {}...", impactNobjectId, objectId);
                logger.info("            └─ SQL: {}", insertSql.replace("?", "{" + impactNobjectId + "}"));
                logger.info("            └─ Columns count: {}", insertColumnNames.size());
                
                try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                    // Set impactNobjectId for WHERE clause
                    stmt.setInt(1, impactNobjectId);
                    logger.info("            └─ Executing INSERT with parameter nobject_id = {}", impactNobjectId);
                    
                    int rowsInserted = stmt.executeUpdate();
                    if (rowsInserted > 0) {
                        logger.info("            ✅ Copied {} impact row(s) from nobject_id {} to object_id {} in {}", 
                            rowsInserted, impactNobjectId, objectId, tableName);
                    } else {
                        logger.warn("            ⚠️  No impact rows to copy in {} (no references to nobject_id {})", tableName, impactNobjectId);
                    }
                } catch (SQLException sqlEx) {
                    logger.error("            ❌ SQL Error in INSERT: {}", sqlEx.getMessage());
                    logger.error("            ❌ SQL: {}", insertSql);
                    throw sqlEx;
                }
            }
            
            // Step 3: Delete rows that reference impactNobjectId (cloned)
            // For process_x_process, delete by ID (relationship record ID)
            // For other tables, delete by sourceColumn
            if (useRelationshipRecordId) {
                logger.info("            └─ [STEP 3] Deleting cloned relationship record with ID {}...", impactNobjectId);
                String deleteSql = "DELETE FROM " + tableName + " WHERE ID = ?";
                try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
                    stmt.setInt(1, impactNobjectId);
                    int rowsDeleted = stmt.executeUpdate();
                    if (rowsDeleted > 0) {
                        logger.info("            ✅ Deleted {} impact row(s) with ID {} from {}", 
                            rowsDeleted, impactNobjectId, tableName);
                    } else {
                        logger.info("            ℹ️  No impact rows to delete in {} (no record with ID {})", tableName, impactNobjectId);
                    }
                }
            } else {
                logger.info("            └─ [STEP 3] Deleting rows that reference nobject_id {} (cloned)...", impactNobjectId);
                String deleteSql = "DELETE FROM " + tableName + " WHERE " + sourceColumn + " = ?";
                try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
                    stmt.setInt(1, impactNobjectId);
                    int rowsDeleted = stmt.executeUpdate();
                    if (rowsDeleted > 0) {
                        logger.info("            ✅ Deleted {} impact row(s) that referenced nobject_id {} from {}", 
                            rowsDeleted, impactNobjectId, tableName);
                    } else {
                        logger.info("            ℹ️  No impact rows to delete in {} (no references to nobject_id {})", tableName, impactNobjectId);
                    }
                }
            }
            
        } catch (SQLException e) {
            logger.error("            ❌ Error applying impact changes for {}: {}", areaKey, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Apply stakeholder changes
     * Format: stakeholders#glossary_stakeholder, stakeholders#dataset_stakeholder, etc.
     * When completed: merge rows from cloned into original (do NOT delete old rows in original)
     * Logic:
     * 1. INSERT IGNORE new rows from cloned (nobject_id) to original (object_id) - merges without deleting old
     * 2. DELETE rows in cloned (nobject_id)
     * 
     * Note: Unlike impact/relationships, stakeholders use INSERT IGNORE to merge data.
     * Old stakeholder rows in original are kept, new ones from cloned are added.
     */
    private void applyStakeholderChanges(Connection conn, String facetName, int objectId, int nobjectId, String areaKey) throws SQLException {
        String[] parts = areaKey.split("#");
        if (parts.length < 2) {
            logger.warn("Invalid stakeholder area key format: {}", areaKey);
            return;
        }
        
        // Map area key to actual table name (e.g., "glossary_stakeholder" -> "glossary_x_objectxpeople")
        String tableName = getStakeholderTableName(facetName, parts[1]);
        if (tableName == null) {
            logger.warn("Could not determine stakeholder table name for area key: {}", areaKey);
            return;
        }
        
        String sourceColumn = getStakeholderSourceColumn(facetName);
        
        if (sourceColumn == null) {
            logger.warn("Unknown facet for stakeholder changes: {}", facetName);
            return;
        }
        
        logger.info("         └─ [STAKEHOLDER] Table: {}, Column: {}", tableName, sourceColumn);
        logger.info("            └─ Copying rows from nobject_id {} to object_id {}", nobjectId, objectId);
        
        try {
            // Step 1: Get table metadata to build INSERT...SELECT statement
            DatabaseMetaData metaData = conn.getMetaData();
            try (ResultSet columns = metaData.getColumns(null, null, tableName, null)) {
                List<String> columnNames = new ArrayList<>();
                List<String> selectExpressions = new ArrayList<>();
                
                while (columns.next()) {
                    String columnName = columns.getString("COLUMN_NAME");
                    String isAutoIncrement = columns.getString("IS_AUTOINCREMENT");
                    
                    // Skip auto-increment ID column
                    if ("YES".equals(isAutoIncrement)) {
                        continue;
                    }
                    
                    columnNames.add(columnName);
                    
                    // Replace nobject_id with object_id in the source column
                    if (columnName.equalsIgnoreCase(sourceColumn)) {
                        selectExpressions.add(objectId + " AS " + columnName); // Use object_id directly
                    } else {
                        selectExpressions.add(columnName);
                    }
                }
                
                if (columnNames.isEmpty()) {
                    logger.warn("            ⚠️  No columns found for table {}", tableName);
                    return;
                }
                
                // Step 1: Copy rows from nobject_id to object_id
                // Use INSERT IGNORE to handle potential duplicates gracefully
                String insertSql = "INSERT IGNORE INTO " + tableName + " (" + 
                    String.join(", ", columnNames) + ") " +
                    "SELECT " + String.join(", ", selectExpressions) + 
                    " FROM " + tableName + " WHERE " + sourceColumn + " = ?";
                
                logger.info("            └─ [STEP 1] Copying rows from nobject_id {} to object_id {}...", nobjectId, objectId);
                try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                    // Set nobject_id for WHERE clause
                    stmt.setInt(1, nobjectId);
                    
                    int rowsInserted = stmt.executeUpdate();
                    if (rowsInserted > 0) {
                        logger.info("            ✅ Copied {} stakeholder row(s) from nobject_id {} to object_id {} in {}", 
                            rowsInserted, nobjectId, objectId, tableName);
                    } else {
                        logger.info("            ℹ️  No stakeholder rows to copy in {} (no references to nobject_id {})", tableName, nobjectId);
                    }
                }
            }
            
            // Step 2: Delete rows that reference nobject_id
            logger.info("            └─ [STEP 2] Deleting rows that reference nobject_id {}...", nobjectId);
            String deleteSql = "DELETE FROM " + tableName + " WHERE " + sourceColumn + " = ?";
            try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
                stmt.setInt(1, nobjectId);
                int rowsDeleted = stmt.executeUpdate();
                if (rowsDeleted > 0) {
                    logger.info("            ✅ Deleted {} stakeholder row(s) that referenced nobject_id {} from {}", 
                        rowsDeleted, nobjectId, tableName);
                } else {
                    logger.info("            ℹ️  No stakeholder rows to delete in {} (no references to nobject_id {})", tableName, nobjectId);
                }
            }
            
        } catch (SQLException e) {
            logger.error("            ❌ Error applying stakeholder changes for {}: {}", areaKey, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Apply relationship changes
     * Format: relationships#glossary_x_glossary, relationships#dataset_x_dataset, relationships#glossary_x_system, relationships#process_x_process, etc.
     * When completed: replace old rows in original with new rows from cloned
     * Logic:
     * 1. DELETE old rows in original (object_id)
     * 2. INSERT new rows from cloned (nobject_id) to original (object_id) - ALL relationships at once
     * 3. DELETE rows in cloned (nobject_id) - ALL relationships at once
     * 
     * This handles:
     * - Strategic Source (glossary_x_system)
     * - Process Predecessor (process_x_process)
     * - Glossary relationships (glossary_x_glossary)
     * - Dataset relationships (dataset_x_dataset)
     * - All other relationship tables
     * 
     * SPECIAL CASE: attribute_x_attribute
     * - Relationships are created between attributes in the cloned dataset
     * - After applyAttributeChanges updates Dataset_ID, the attributes now belong to original dataset
     * - The relationship records are already correct (pointing to the right attribute IDs)
     * - No copying needed - just log and continue
     * 
     * SPECIAL CASE: dataset_mastersource
     * - entryNobjectId is the dataset ID that was linked to the system
     * - No copying needed - the dataset's MasterSource field already points to the system
     * 
     * @param conn Database connection
     * @param facetName Facet name
     * @param objectId Main object ID (from summary - original)
     * @param nobjectId Main nobject ID (from summary - cloned)
     * @param areaKey The area key
     * @param entryObjectId Object ID from the current mapping entry
     * @param entryNobjectId NObject ID from the current mapping entry (used for tracking only)
     */
    private void applyRelationshipChanges(Connection conn, String facetName, int objectId, int nobjectId, 
                                          String areaKey, int entryObjectId, int entryNobjectId) throws SQLException {
        String[] parts = areaKey.split("#");
        if (parts.length < 2) {
            logger.warn("Invalid relationship area key format: {}", areaKey);
            return;
        }
        
        // Keep original table name for SQL queries (case-sensitive in some databases)
        String tableNameOriginal = parts[1]; // e.g., "glossary_x_glossary"
        String tableName = tableNameOriginal.toLowerCase(); // For switch statements
        
        // Special case: attribute_x_attribute
        // These relationships are between attributes, not datasets
        // The attributes were updated by applyAttributeChanges to belong to original dataset
        // The relationship records don't need modification - they point to attribute IDs which don't change
        if ("attribute_x_attribute".equals(tableName)) {
            logger.info("         └─ [RELATIONSHIP] Special case: attribute_x_attribute");
            logger.info("            └─ Relationships between attributes don't need copying");
            logger.info("            └─ Attribute Dataset_IDs were already updated by applyAttributeChanges");
            logger.info("            ✅ No action needed for attribute_x_attribute relationships");
            return;
        }
        
        // Special case: dataset_mastersource
        // This tracks datasets linked to a system via MasterSource field
        // entryNobjectId = dataset ID that was linked during CR
        // The dataset's MasterSource field already points to the correct system
        // No update needed - just confirm the relationship exists
        if ("dataset_mastersource".equals(tableName)) {
            logger.info("         └─ [RELATIONSHIP] Special case: dataset_mastersource");
            logger.info("            └─ System object_id: {}, Linked Dataset ID: {}", entryObjectId, entryNobjectId);
            logger.info("            └─ Dataset's MasterSource field already points to the system");
            logger.info("            ✅ No action needed - relationship is already established");
            return;
        }
        
        // Try to get table-specific column name first, then fall back to facet-based
        String sourceColumn = getRelationshipSourceColumnForTable(tableName);
        logger.info("         └─ [RELATIONSHIP] Table: {}, Looking up source column...", tableName);
        if (sourceColumn != null) {
            logger.info("            ✅ Found table-specific source column: {}", sourceColumn);
        } else {
            logger.info("            ⚠️  No table-specific column found, trying facet-based lookup for facet: {}", facetName);
            sourceColumn = getRelationshipSourceColumn(facetName);
            if (sourceColumn != null) {
                logger.info("            ✅ Found facet-based source column: {}", sourceColumn);
            } else {
                logger.warn("            ❌ Unknown facet for relationship changes: {}", facetName);
                logger.warn("            ❌ Cannot determine source column for table: {}", tableName);
                return;
            }
        }
        
        // Find the actual column name in the table (handles case sensitivity)
        String actualSourceColumn = findActualSourceColumn(conn, tableNameOriginal, sourceColumn);
        if (actualSourceColumn == null) {
            logger.error("            ❌ Source column '{}' not found in table {} (and no variations match)", sourceColumn, tableNameOriginal);
            throw new SQLException("Source column '" + sourceColumn + "' not found in table " + tableNameOriginal);
        }
        sourceColumn = actualSourceColumn; // Use the actual column name found in the table
        
        logger.info("         └─ [RELATIONSHIP] Table: {}, Source Column: {}", tableNameOriginal, sourceColumn);
        logger.info("            └─ Main object_id: {} (original), Main nobject_id: {} (cloned)", objectId, nobjectId);
        logger.info("            └─ Entry object_id: {}, Entry nobject_id: {} (relationship record ID - for tracking only)", entryObjectId, entryNobjectId);
        logger.info("            └─ Copying ALL relationships where {} = {} to object_id {} (batch operation)", 
            sourceColumn, nobjectId, objectId);
        
        try {
            // Step 1: Get table metadata to build INSERT...SELECT statement
            // We need to copy all columns except auto-increment ID, replacing nobject_id with object_id
            // Use original table name (case-sensitive) for metadata queries
            DatabaseMetaData metaData = conn.getMetaData();
            logger.info("            └─ Getting column metadata for table: {}", tableNameOriginal);
            try (ResultSet columns = metaData.getColumns(null, null, tableNameOriginal, null)) {
                List<String> columnNames = new ArrayList<>();
                List<String> selectExpressions = new ArrayList<>();
                
                boolean sourceColumnFound = false;
                while (columns.next()) {
                    String columnName = columns.getString("COLUMN_NAME");
                    String isAutoIncrement = columns.getString("IS_AUTOINCREMENT");
                    
                    // Skip ID column - we'll let the database generate a new ID
                    // This is critical for tables like glossary_x_glossary where ID is the primary key
                    // but not auto-increment (it's manually managed via getNextId())
                    if (columnName.equalsIgnoreCase("ID")) {
                        logger.info("            └─ ⏭️  Skipping ID column '{}' (will generate new ID for copied records)", columnName);
                        continue;
                    }
                    
                    // Skip auto-increment columns (for other tables that might have auto-increment non-ID columns)
                    if ("YES".equals(isAutoIncrement)) {
                        logger.info("            └─ ⏭️  Skipping auto-increment column '{}'", columnName);
                        continue;
                    }
                    
                    columnNames.add(columnName);
                    
                    // Replace nobject_id with object_id in the source column
                    if (columnName.equalsIgnoreCase(sourceColumn)) {
                        selectExpressions.add(objectId + " AS " + columnName); // Use object_id directly
                        sourceColumnFound = true;
                        logger.info("            └─ ✅ Found source column '{}' in table {} - will replace with object_id {}", 
                            columnName, tableNameOriginal, objectId);
                    } else {
                        selectExpressions.add(columnName);
                    }
                }
                
                if (columnNames.isEmpty()) {
                    logger.error("            ❌ [CRITICAL] No columns found for table {}!", tableNameOriginal);
                    throw new SQLException("No columns found for table " + tableNameOriginal);
                }
                
                if (!sourceColumnFound) {
                    logger.error("            ❌ [CRITICAL] Source column '{}' NOT FOUND in table {} columns!", sourceColumn, tableNameOriginal);
                    logger.error("            ❌ Available columns: {}", String.join(", ", columnNames));
                    throw new SQLException("Source column '" + sourceColumn + "' not found in table " + tableNameOriginal + ". Available columns: " + String.join(", ", columnNames));
                }
                
                logger.info("            └─ Total columns to copy: {}, Source column '{}' will be replaced with object_id {}", 
                    columnNames.size(), sourceColumn, objectId);
                
                // Pre-check: Check if rows exist with nobject_id before attempting copy
                // This is needed to decide whether to delete old relationships
                String whereClause = sourceColumn + " = ?";
                String checkSql = "SELECT COUNT(*) as cnt FROM " + tableNameOriginal + " WHERE " + whereClause;
                int relationshipCount = 0;
                try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                    checkStmt.setInt(1, nobjectId);
                    try (ResultSet rs = checkStmt.executeQuery()) {
                        if (rs.next()) {
                            relationshipCount = rs.getInt("cnt");
                            logger.info("            └─ [PRE-CHECK] Found {} row(s) in {} with {} = {}", relationshipCount, tableNameOriginal, sourceColumn, nobjectId);
                            if (relationshipCount == 0) {
                                // Additional diagnostic: show sample IDs in the table
                                String sampleSql = "SELECT " + sourceColumn + " FROM " + tableNameOriginal + " LIMIT 10";
                                try (PreparedStatement sampleStmt = conn.prepareStatement(sampleSql);
                                     ResultSet sampleRs = sampleStmt.executeQuery()) {
                                    List<Integer> sampleIds = new ArrayList<>();
                                    while (sampleRs.next()) {
                                        sampleIds.add(sampleRs.getInt(1));
                                    }
                                    logger.warn("            ⚠️  [PRE-CHECK] No rows found with {} = {} in {}. Sample IDs in table: {}", 
                                        sourceColumn, nobjectId, tableNameOriginal, sampleIds);
                                    
                                    // Fallback: Check if relationships exist with original objectId
                                    // This handles the case where relationships were saved to original ID instead of cloned ID
                                    if (objectId > 0 && objectId != nobjectId) {
                                        String fallbackCheckSql = "SELECT COUNT(*) as cnt FROM " + tableNameOriginal + " WHERE " + sourceColumn + " = ?";
                                        try (PreparedStatement fallbackStmt = conn.prepareStatement(fallbackCheckSql)) {
                                            fallbackStmt.setInt(1, objectId);
                                            try (ResultSet fallbackRs = fallbackStmt.executeQuery()) {
                                                if (fallbackRs.next()) {
                                                    int fallbackCount = fallbackRs.getInt("cnt");
                                                    if (fallbackCount > 0) {
                                                        logger.info("            └─ [FALLBACK] Found {} relationship(s) with original objectId {} - these are already in final location", 
                                                            fallbackCount, objectId);
                                                        logger.info("            ✅ [EARLY RETURN] Skipping all steps - relationships already correct, nothing to copy or delete");
                                                        // CRITICAL FIX: Return early - relationships are already where they should be
                                                        // Do NOT delete, do NOT copy, do NOT touch them
                                                        return;
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Step 1: DELETE old rows in original (object_id) first
                // CRITICAL FIX: Only delete old rows ONCE per objectId/table combination
                // Problem: If we have multiple relationship mappings, we process them one by one.
                // If we delete old rows each time, we'll delete relationships we just copied in previous iterations.
                // Solution: Use a ThreadLocal Set to track which objectId/table combinations have already had
                // old rows deleted. This ensures we only delete old rows once, even if there are multiple mappings.
                // IMPORTANT: Only delete if we have relationships to copy from cloned ID
                // This prevents data loss if relationships were never saved to cloned ID
                String deletionKey = objectId + ":" + tableNameOriginal;
                Set<String> deletedKeys = deletedOldRowsTracker.get();
                
                // Only delete old relationships if we have new ones to copy
                // If relationshipCount is 0, we already checked fallback and returned early if found
                boolean shouldDeleteOld = (relationshipCount > 0);
                
                if (!deletedKeys.contains(deletionKey) && shouldDeleteOld) {
                    // First time processing this objectId/table combination - delete old rows
                    logger.info("            └─ [STEP 1] First relationship processing for object_id {} in {}. Deleting old rows...", objectId, tableNameOriginal);
                    String deleteOldSql = "DELETE FROM " + tableNameOriginal + " WHERE " + sourceColumn + " = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(deleteOldSql)) {
                        stmt.setInt(1, objectId);
                        int rowsDeletedOld = stmt.executeUpdate();
                        if (rowsDeletedOld > 0) {
                            logger.info("            ✅ Deleted {} old relationship row(s) in original (object_id {}) from {}", 
                                rowsDeletedOld, objectId, tableNameOriginal);
                        } else {
                            logger.info("            ℹ️  No old relationship rows to delete in {} (no references to object_id {})", tableNameOriginal, objectId);
                        }
                    } catch (SQLException sqlEx) {
                        logger.error("            ❌ SQL Error in DELETE (old rows): {}", sqlEx.getMessage());
                        logger.error("            ❌ SQL: {}", deleteOldSql);
                        throw sqlEx;
                    }
                    
                    // Mark this combination as processed
                    deletedKeys.add(deletionKey);
                } else if (deletedKeys.contains(deletionKey)) {
                    // Already deleted old rows for this objectId/table combination - skip deletion
                    logger.info("            └─ [STEP 1] Old rows already deleted for object_id {} in {} (skipping deletion to avoid deleting newly copied relationships).", 
                        objectId, tableNameOriginal);
                } else {
                    // No relationships to copy, skip deletion to prevent data loss
                    logger.warn("            ⚠️  [SAFETY] Skipping deletion of old relationships - no relationships found with cloned ID {} to replace them", nobjectId);
                    logger.warn("            ⚠️  [SAFETY] This prevents data loss if relationships were never saved to cloned ID");
                }
                
                // Step 2: INSERT ALL new rows from cloned (nobjectId) to original (objectId)
                // Copy ALL relationships where sourceColumn = nobjectId at once (batch operation)
                // This is the key change: we copy all relationships, not just one by ID
                
                // Check if ID column exists and needs to be generated (not auto-increment)
                boolean needsIdColumn = false;
                int baseId = 0;
                try (ResultSet pkColumns = metaData.getPrimaryKeys(null, null, tableNameOriginal)) {
                    if (pkColumns.next()) {
                        String pkColumnName = pkColumns.getString("COLUMN_NAME");
                        if (pkColumnName.equalsIgnoreCase("ID")) {
                            // Check if ID is auto-increment
                            try (ResultSet idColumn = metaData.getColumns(null, null, tableNameOriginal, "ID")) {
                                if (idColumn.next()) {
                                    String isAutoIncrement = idColumn.getString("IS_AUTOINCREMENT");
                                    if (!"YES".equals(isAutoIncrement)) {
                                        needsIdColumn = true;
                                        // Get the next available ID
                                        try (PreparedStatement stmt = conn.prepareStatement("SELECT COALESCE(MAX(ID), 0) FROM " + tableNameOriginal)) {
                                            try (ResultSet rs = stmt.executeQuery()) {
                                                if (rs.next()) {
                                                    baseId = rs.getInt(1);
                                                }
                                            }
                                        }
                                        logger.info("            └─ ID column found but not auto-increment - will generate sequential IDs starting from {}", baseId + 1);
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Build INSERT statement with ID column if needed
                List<String> insertColumnNames = new ArrayList<>(columnNames);
                List<String> insertSelectExpressions = new ArrayList<>(selectExpressions);
                
                if (needsIdColumn) {
                    // Add ID column at the beginning
                    insertColumnNames.add(0, "ID");
                    // Use MySQL user variable to generate sequential IDs for multiple rows
                    insertSelectExpressions.add(0, "(@row_number := @row_number + 1) + " + baseId + " AS ID");
                    logger.info("            └─ Adding ID column with sequential generation (base: {})", baseId);
                }
                
                // Initialize MySQL user variable if needed
                if (needsIdColumn) {
                    try (PreparedStatement stmt = conn.prepareStatement("SET @row_number = 0")) {
                        stmt.execute();
                        logger.info("            └─ Initialized MySQL user variable @row_number = 0");
                    }
                }
                
                // Copy ALL relationships where sourceColumn = nobjectId (batch operation)
                String insertSql = "INSERT INTO " + tableNameOriginal + " (" + 
                    String.join(", ", insertColumnNames) + ") " +
                    "SELECT " + String.join(", ", insertSelectExpressions) + 
                    " FROM " + tableNameOriginal + " WHERE " + sourceColumn + " = ?";
                
                logger.info("            └─ [STEP 2] Copying ALL relationships from nobject_id {} to object_id {} (batch operation)...", nobjectId, objectId);
                logger.info("            └─ SQL: {}", insertSql.replace("?", "{" + nobjectId + "}"));
                logger.info("            └─ Columns count: {}", insertColumnNames.size());
                logger.info("            └─ Source column '{}' will be updated from {} to {} for all matching rows", sourceColumn, nobjectId, objectId);
                
                // Only copy if we have relationships to copy
                if (relationshipCount > 0) {
                    try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                        // Set nobjectId for WHERE clause (copy all relationships where sourceColumn = nobjectId)
                        stmt.setInt(1, nobjectId);
                        logger.info("            └─ Executing INSERT with parameter nobject_id = {} (will copy all relationships)", nobjectId);
                        
                        int rowsInserted = stmt.executeUpdate();
                        if (rowsInserted > 0) {
                            logger.info("            ✅ Copied {} relationship row(s) from nobject_id {} to object_id {} in {} (batch operation)", 
                                rowsInserted, nobjectId, objectId, tableNameOriginal);
                        } else {
                            logger.info("            ℹ️  No relationship rows to copy in {} (no references to nobject_id {})", tableNameOriginal, nobjectId);
                        }
                    } catch (SQLException sqlEx) {
                        logger.error("            ❌ SQL Error in INSERT: {}", sqlEx.getMessage());
                        logger.error("            ❌ SQL: {}", insertSql);
                        throw sqlEx;
                    }
                } else {
                    logger.warn("            ⚠️  [SKIP] No relationships found with cloned ID {} to copy", nobjectId);
                }
            }
            
            // Step 3: Delete ALL rows that reference nobjectId (cloned) - batch operation
            // Delete all relationships where sourceColumn = nobjectId
            logger.info("            └─ [STEP 3] Deleting ALL cloned relationship rows where {} = {} (batch operation)...", sourceColumn, nobjectId);
            String deleteSql = "DELETE FROM " + tableNameOriginal + " WHERE " + sourceColumn + " = ?";
            int rowsDeleted = 0;
            
            try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
                stmt.setInt(1, nobjectId);
                rowsDeleted = stmt.executeUpdate();
                
                if (rowsDeleted > 0) {
                    logger.info("            ✅ Deleted {} relationship row(s) that referenced nobject_id {} from {}", 
                        rowsDeleted, nobjectId, tableNameOriginal);
                } else {
                    logger.info("            ℹ️  No relationship rows to delete in {} (no references to nobject_id {})", tableNameOriginal, nobjectId);
                }
            } catch (SQLException sqlEx) {
                logger.error("            ❌ SQL Error in DELETE: {}", sqlEx.getMessage());
                logger.error("            ❌ SQL: {}", deleteSql);
                // Don't throw - deletion is cleanup, not critical
                logger.warn("            ⚠️  Continuing despite delete error...");
            }
            
        } catch (SQLException e) {
            logger.error("            ❌ Error applying relationship changes for {}: {}", areaKey, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Apply data-content changes (glossary_x_system)
     * Copies the relationship from cloned system to original system
     */
    private void applyDataContentChanges(Connection conn, int objectId, int nobjectId, int relationshipRecordId) throws SQLException {
        logger.info("         └─ [DATA-CONTENT] Applying glossary_x_system changes");
        logger.info("            └─ Original system ID: {}, Cloned system ID: {}, Relationship record ID: {}", 
            objectId, nobjectId, relationshipRecordId);
        
        try {
            // Get the relationship record from the cloned system
            String selectSql = "SELECT GlossaryID, Relation_TypeID FROM glossary_x_system WHERE ID = ? AND SystemID = ?";
            Integer glossaryId = null;
            Integer relationTypeId = null;
            
            try (PreparedStatement selectStmt = conn.prepareStatement(selectSql)) {
                selectStmt.setInt(1, relationshipRecordId);
                selectStmt.setInt(2, nobjectId);
                try (ResultSet rs = selectStmt.executeQuery()) {
                    if (rs.next()) {
                        glossaryId = rs.getInt("GlossaryID");
                        relationTypeId = rs.getInt("Relation_TypeID");
                        logger.info("            └─ Found relationship: GlossaryID={}, Relation_TypeID={}", glossaryId, relationTypeId);
                    } else {
                        logger.warn("            └─ ⚠️  Relationship record {} not found in cloned system {}", relationshipRecordId, nobjectId);
                        return;
                    }
                }
            }
            
            // Check if the relationship already exists in the original system
            String checkSql = "SELECT COUNT(*) as cnt FROM glossary_x_system WHERE SystemID = ? AND GlossaryID = ? AND Relation_TypeID = ?";
            boolean exists = false;
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setInt(1, objectId);
                checkStmt.setInt(2, glossaryId);
                checkStmt.setInt(3, relationTypeId);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next() && rs.getInt("cnt") > 0) {
                        exists = true;
                    }
                }
            }
            
            if (!exists) {
                // Insert the relationship into the original system (Link_Source = 'system' for Data Content Summary)
                String insertSql = "INSERT INTO glossary_x_system (GlossaryID, SystemID, Relation_TypeID, Link_Source) VALUES (?, ?, ?, 'system')";
                try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                    insertStmt.setInt(1, glossaryId);
                    insertStmt.setInt(2, objectId);
                    insertStmt.setInt(3, relationTypeId);
                    int rowsInserted = insertStmt.executeUpdate();
                    if (rowsInserted > 0) {
                        logger.info("            ✅ Copied data-content relationship to original system {}", objectId);
                    }
                }
            } else {
                logger.info("            ℹ️  Relationship already exists in original system - skipping");
            }
            
            // Delete the relationship from the cloned system
            String deleteSql = "DELETE FROM glossary_x_system WHERE ID = ? AND SystemID = ?";
            try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                deleteStmt.setInt(1, relationshipRecordId);
                deleteStmt.setInt(2, nobjectId);
                int rowsDeleted = deleteStmt.executeUpdate();
                if (rowsDeleted > 0) {
                    logger.info("            ✅ Deleted data-content relationship from cloned system {}", nobjectId);
                }
            }
        } catch (SQLException e) {
            logger.error("            ❌ Error applying data-content changes: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Apply document changes (system_x_document, glossary_x_document, etc.)
     * 
     * IMPORTANT: Documents are uploaded directly to the ORIGINAL object (not the cloned one).
     * The DocumentUploadServlet inserts into system_x_document with the original System_ID.
     * So when completing a CR, the document is already in the right place.
     * We only need to ensure the document record stays (no-op for apply).
     * The nobjectId in the mapping is the ID of the document relationship record itself.
     */
    private void applyDocumentChanges(Connection conn, String facetName, int objectId, int nobjectId, 
                                      String areaKey, int relationshipRecordId) throws SQLException {
        String[] parts = areaKey.split("#");
        if (parts.length < 2) {
            logger.warn("            └─ ⚠️  Invalid document area key format: {}", areaKey);
            return;
        }
        
        String tableName = parts[1]; // e.g., "system_x_document"
        logger.info("         └─ [DOCUMENTS] Applying document changes for table: {}", tableName);
        logger.info("            └─ Original object ID: {}, Cloned object ID: {}, Relationship record ID: {}", 
            objectId, nobjectId, relationshipRecordId);
        
        // Determine the source column based on the facet (correct column names matching DB schema)
        String sourceColumn = getDocumentSourceColumn(facetName);
        if (sourceColumn == null) {
            logger.warn("            └─ ⚠️  Unknown facet for document changes: {}", facetName);
            return;
        }
        
        // Documents are uploaded directly to the original object (not the cloned one).
        // The nobjectId in the mapping = the ID of the document relationship record.
        // Check if the document record exists and is already linked to the original object.
        String checkSql = "SELECT ID, " + sourceColumn + " FROM " + tableName + " WHERE ID = ?";
        try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
            checkStmt.setInt(1, relationshipRecordId);
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next()) {
                    int linkedObjectId = rs.getInt(sourceColumn);
                    if (linkedObjectId == objectId) {
                        // Document is already linked to the original object - nothing to do
                        logger.info("            ✅ Document record {} is already linked to original object {} - no action needed", 
                            relationshipRecordId, objectId);
                    } else if (linkedObjectId == nobjectId) {
                        // Document is linked to the cloned object - update to point to original
                        logger.info("            └─ Document record {} is linked to cloned object {}, updating to original {}", 
                            relationshipRecordId, nobjectId, objectId);
                        String updateSql = "UPDATE " + tableName + " SET " + sourceColumn + " = ? WHERE ID = ?";
                        try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                            updateStmt.setInt(1, objectId);
                            updateStmt.setInt(2, relationshipRecordId);
                            int updated = updateStmt.executeUpdate();
                            if (updated > 0) {
                                logger.info("            ✅ Updated document record {} to point to original object {}", 
                                    relationshipRecordId, objectId);
                            }
                        }
                    } else {
                        logger.info("            ℹ️  Document record {} is linked to object {} (neither original {} nor cloned {}) - leaving as-is", 
                            relationshipRecordId, linkedObjectId, objectId, nobjectId);
                    }
                } else {
                    logger.warn("            └─ ⚠️  Document record {} not found in {} - may have been deleted", 
                        relationshipRecordId, tableName);
                }
            }
        } catch (SQLException e) {
            // Don't throw - document apply is best-effort since docs are usually already on original
            logger.warn("            ⚠️  Error checking document record {}: {} - continuing", relationshipRecordId, e.getMessage());
        }
    }
    
    /**
     * Get the correct source column name for document tables based on facet.
     * Column names must match the actual database schema exactly.
     */
    private String getDocumentSourceColumn(String facetName) {
        if (facetName == null) return null;
        switch (facetName.toLowerCase()) {
            case "system": return "System_ID";
            case "glossary": return "Glossary_ID";
            case "dataset": return "Dataset_ID";
            case "process": return "Process_ID";
            default: return null;
        }
    }

    /**
     * Delete cloned row from main table
     */
    private void deleteClonedRow(Connection conn, String facetName, int nobjectId) throws SQLException {
        String tableName = getMainTableName(facetName);
        if (tableName == null) {
            logger.warn("Unknown facet for deleting cloned row: {}", facetName);
            return;
        }
        
        // Delete dependent records from child tables BEFORE deleting the cloned row
        // This avoids FK constraint violations (e.g., system_audit_history, system_audit, etc.)
        deleteChildRecordsForClonedRow(conn, facetName, nobjectId);
        
        String sql = "DELETE FROM " + tableName + " WHERE ID = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, nobjectId);
            int rowsAffected = stmt.executeUpdate();
            logger.info("Deleted cloned row: {} rows deleted from {}", rowsAffected, tableName);
        }
    }
    
    /**
     * Delete child records that reference the cloned row to avoid FK constraint violations.
     * Must be called BEFORE deleting the cloned row from the main table.
     */
    private void deleteChildRecordsForClonedRow(Connection conn, String facetName, int nobjectId) {
        // Define child tables and their FK columns for each facet
        String[][] childTables;
        
        switch (facetName.toLowerCase()) {
            case "system":
                childTables = new String[][] {
                    {"system_audit_history", "id"},
                    {"system_audit", "id"},
                    {"system_follow", "System_id"},
                    {"system_x_objectxpeople", "SystemID"},
                    {"system_x_document", "System_ID"},
                    {"system_x_legal", "System_ID"},
                    {"glossary_x_system", "SystemID"},
                    {"businessarea_x_system", "System_ID"},
                    {"capability_x_system", "System_ID"},
                    {"client_x_system", "System_ID"},
                    {"policy_x_system", "System_ID"},
                    {"process_x_system", "system_id"},
                    {"product_x_system", "System_ID"},
                    {"project_x_system", "systemid"},
                    {"interface", "Source_systemID"},
                    {"interface", "Target_systemID"},
                };
                break;
            case "glossary":
                childTables = new String[][] {
                    {"glossary_audit_history", "id"},
                    {"glossary_audit", "id"},
                    {"glossary_x_system", "GlossaryID"},
                    {"glossary_x_dataset", "GlossaryID"},
                    {"glossary_x_attribute", "GlossaryID"},
                    {"glossary_x_glossary", "GlossaryID"},
                    {"glossary_x_glossary", "Related_GlossaryID"},
                    {"glossary_x_process", "GlossaryID"},
                    {"glossary_x_project", "GlossaryID"},
                };
                break;
            case "dataset":
                childTables = new String[][] {
                    {"dataset_audit_history", "id"},
                    {"dataset_audit", "id"},
                    {"glossary_x_dataset", "DatasetID"},
                    {"attribute", "Dataset_ID"},
                };
                break;
            case "process":
                childTables = new String[][] {
                    {"process_audit_history", "id"},
                    {"process_audit", "id"},
                    {"process_x_system", "system_id"},
                    {"process_x_process", "ProcessID"},
                    {"process_x_process", "Related_ProcessID"},
                    {"glossary_x_process", "ProcessID"},
                };
                break;
            default:
                logger.warn("   └─ No child table cleanup defined for facet: {}", facetName);
                return;
        }
        
        for (String[] childTable : childTables) {
            String table = childTable[0];
            String column = childTable[1];
            try {
                String sql = "DELETE FROM `" + table + "` WHERE `" + column + "` = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, nobjectId);
                    int deleted = stmt.executeUpdate();
                    if (deleted > 0) {
                        logger.info("   └─ Cleaned up {} row(s) from {}.{} for cloned ID {}", deleted, table, column, nobjectId);
                    }
                }
            } catch (SQLException e) {
                // Table might not exist or column might not exist - that's OK
                logger.debug("   └─ Could not clean up {}.{} for cloned ID {}: {}", table, column, nobjectId, e.getMessage());
            }
        }
    }

    /**
     * Delete impact rows for cloned object
     */
    private void deleteImpactRows(Connection conn, String facetName, int nobjectId, String areaKey) throws SQLException {
        String[] parts = areaKey.split("#");
        if (parts.length < 2) return;
        
        // Skip impact#process_X_process - predecessors are handled by relationships#process_x_process
        if (areaKey.toLowerCase().contains("process_x_process")) {
            logger.info("         → SKIPPING delete for impact#process_X_process - handled by relationships");
            return;
        }
        
        String relationshipInfo = parts[1]; // e.g., "system_X_product"
        String tableName = getImpactTableName(facetName, relationshipInfo);
        if (tableName == null) {
            logger.warn("Could not determine table name for impact area: {}", areaKey);
            return;
        }
        
        String expectedColumn = getImpactSourceColumn(facetName, areaKey, tableName);
        if (expectedColumn == null) return;
        
        // Find the actual column name in the table (handles variations)
        String sourceColumn = findActualSourceColumn(conn, tableName, expectedColumn);
        if (sourceColumn == null) {
            logger.warn("Could not find source column '{}' (or variants) in table {}", expectedColumn, tableName);
            return;
        }
        
        String sql = "DELETE FROM " + tableName + " WHERE " + sourceColumn + " = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, nobjectId);
            stmt.executeUpdate();
        }
    }

    /**
     * Delete stakeholder rows for cloned object
     */
    private void deleteStakeholderRows(Connection conn, String facetName, int nobjectId, String areaKey) throws SQLException {
        String[] parts = areaKey.split("#");
        if (parts.length < 2) return;
        
        String tableName = parts[1].toLowerCase();
        String sourceColumn = getStakeholderSourceColumn(facetName);
        
        if (sourceColumn == null) return;
        
        String sql = "DELETE FROM " + tableName + " WHERE " + sourceColumn + " = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, nobjectId);
            stmt.executeUpdate();
        }
    }

    /**
     * Delete relationship rows for cloned object (legacy method - uses source column)
     */
    private void deleteRelationshipRows(Connection conn, String facetName, int nobjectId, String areaKey) throws SQLException {
        String[] parts = areaKey.split("#");
        if (parts.length < 2) return;
        
        String tableName = parts[1].toLowerCase();
        
        // Try to get table-specific column name first, then fall back to facet-based
        String sourceColumn = getRelationshipSourceColumnForTable(tableName);
        if (sourceColumn == null) {
            sourceColumn = getRelationshipSourceColumn(facetName);
        }
        
        if (sourceColumn == null) return;
        
        String sql = "DELETE FROM " + tableName + " WHERE " + sourceColumn + " = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, nobjectId);
            stmt.executeUpdate();
        }
    }
    
    /**
     * Delete a specific relationship record by its ID
     * Used when discarding pending relationship changes
     * @param recordId The ID (primary key) of the relationship record to delete
     */
    private void deleteRelationshipRecordById(Connection conn, int recordId, String areaKey) throws SQLException {
        String[] parts = areaKey.split("#");
        if (parts.length < 2) return;
        
        String tableName = parts[1].toLowerCase();
        
        // Handle special case for dataset_mastersource - this is the dataset table itself, not a junction table
        // In this case, we shouldn't delete the dataset, just clear the MasterSource field
        if ("dataset_mastersource".equals(tableName)) {
            logger.info("   └─ Reverting dataset MasterSource (dataset ID: {})", recordId);
            String sql = "UPDATE dataset SET MasterSource = NULL WHERE ID = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, recordId);
                int rowsUpdated = stmt.executeUpdate();
                logger.info("      ✅ Cleared MasterSource for dataset {}, rows: {}", recordId, rowsUpdated);
            }
            return;
        }
        
        // For actual junction tables, delete the relationship record by its ID
        String sql = "DELETE FROM " + tableName + " WHERE ID = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, recordId);
            int rowsDeleted = stmt.executeUpdate();
            logger.info("   └─ Deleted relationship record {} from {}, rows: {}", recordId, tableName, rowsDeleted);
        }
    }

    /**
     * Update all foreign key references from object_id to nobject_id
     * This is needed when we delete object_id and replace it with nobject_id
     */
    private void updateForeignKeys(Connection conn, String facetName, int objectId, int nobjectId) throws SQLException {
        String mainTableName = getMainTableName(facetName);
        if (mainTableName == null) {
            logger.warn("      ⚠️  Unknown facet for updating foreign keys: {}", facetName);
            return;
        }
        
        logger.info("         └─ Main table: {}", mainTableName);
        logger.info("         └─ Finding all foreign keys pointing to {}...", mainTableName);
        
        int totalFkUpdated = 0;
        
        try {
            // Get all foreign key constraints that reference this table
            DatabaseMetaData metaData = conn.getMetaData();
            String catalog = conn.getCatalog();
            
            // Find all tables that have foreign keys pointing to our main table
            // getExportedKeys returns foreign keys in OTHER tables that reference this table's primary key
            try (ResultSet fkRs = metaData.getExportedKeys(catalog, null, mainTableName)) {
                int fkCount = 0;
                while (fkRs.next()) {
                    fkCount++;
                    String fkTableName = fkRs.getString("FKTABLE_NAME");
                    String fkColumnName = fkRs.getString("FKCOLUMN_NAME");
                    
                    // Skip the changes tables themselves
                    if (fkTableName.endsWith("_changes")) {
                        logger.info("         └─ Skipping changes table: {}", fkTableName);
                        continue;
                    }
                    
                    logger.info("         └─ Found FK: {} -> {}.{}", fkTableName, mainTableName, fkColumnName);
                    
                    // Update foreign key references from object_id to nobject_id
                    String updateSql = "UPDATE " + fkTableName + " SET " + fkColumnName + " = ? WHERE " + fkColumnName + " = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
                        stmt.setInt(1, nobjectId);
                        stmt.setInt(2, objectId);
                        int rowsUpdated = stmt.executeUpdate();
                        if (rowsUpdated > 0) {
                            totalFkUpdated += rowsUpdated;
                            logger.info("            ✅ Updated {} row(s) in {}: {} -> {}", 
                                rowsUpdated, fkTableName, objectId, nobjectId);
                        } else {
                            logger.info("            ℹ️  No rows to update in {} (no references to {})", fkTableName, objectId);
                        }
                    } catch (SQLException e) {
                        // Log but continue - some tables might not have the column or might have constraints
                        logger.warn("            ⚠️  Could not update foreign keys in {}: {}", fkTableName, e.getMessage());
                    }
                }
                if (fkCount == 0) {
                    logger.info("         └─ No foreign keys found pointing to {}", mainTableName);
                }
            }
            
            // Also handle self-referencing foreign keys (e.g., Parent_ID in glossary table)
            String parentColumnName = getParentColumnName(facetName);
            if (parentColumnName != null) {
                logger.info("         └─ Updating self-referencing FK: {} in {}", parentColumnName, mainTableName);
                String updateParentSql = "UPDATE " + mainTableName + " SET " + parentColumnName + " = ? WHERE " + parentColumnName + " = ?";
                try (PreparedStatement stmt = conn.prepareStatement(updateParentSql)) {
                    stmt.setInt(1, nobjectId);
                    stmt.setInt(2, objectId);
                    int rowsUpdated = stmt.executeUpdate();
                    if (rowsUpdated > 0) {
                        totalFkUpdated += rowsUpdated;
                        logger.info("            ✅ Updated {} parent reference(s) in {}: {} -> {}", 
                            rowsUpdated, mainTableName, objectId, nobjectId);
                    } else {
                        logger.info("            ℹ️  No parent references to update in {}", mainTableName);
                    }
                }
            } else {
                logger.info("         └─ No self-referencing FK column for facet: {}", facetName);
            }
            
            logger.info("         ✅ Total foreign key references updated: {}", totalFkUpdated);
            
        } catch (SQLException e) {
            logger.error("         ❌ Error updating foreign keys for {}: {}", facetName, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Update all foreign key references from nobject_id to object_id
     * This is needed when we delete nobject_id after copying its data to object_id
     * We must update all FKs pointing to nobject_id to point to object_id instead
     */
    private void updateForeignKeysFromNobjectToObject(Connection conn, String facetName, int objectId, int nobjectId) throws SQLException {
        String mainTableName = getMainTableName(facetName);
        if (mainTableName == null) {
            logger.warn("      ⚠️  Unknown facet for updating foreign keys: {}", facetName);
            return;
        }
        
        logger.info("         └─ Main table: {}", mainTableName);
        logger.info("         └─ Finding all foreign keys pointing to {} (to update from {} to {})...", mainTableName, nobjectId, objectId);
        
        int totalFkUpdated = 0;
        
        try {
            // Get all foreign key constraints that reference this table
            DatabaseMetaData metaData = conn.getMetaData();
            String catalog = conn.getCatalog();
            
            // Find all tables that have foreign keys pointing to our main table
            // getExportedKeys returns foreign keys in OTHER tables that reference this table's primary key
            try (ResultSet fkRs = metaData.getExportedKeys(catalog, null, mainTableName)) {
                int fkCount = 0;
                while (fkRs.next()) {
                    fkCount++;
                    String fkTableName = fkRs.getString("FKTABLE_NAME");
                    String fkColumnName = fkRs.getString("FKCOLUMN_NAME");
                    
                    // Skip the changes tables themselves
                    if (fkTableName.endsWith("_changes")) {
                        logger.info("         └─ Skipping changes table: {}", fkTableName);
                        continue;
                    }
                    
                    logger.info("         └─ Found FK: {} -> {}.{}", fkTableName, mainTableName, fkColumnName);
                    
                    // Update foreign key references from nobject_id to object_id
                    String updateSql = "UPDATE " + fkTableName + " SET " + fkColumnName + " = ? WHERE " + fkColumnName + " = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
                        stmt.setInt(1, objectId);  // Update TO object_id
                        stmt.setInt(2, nobjectId); // Update FROM nobject_id
                        int rowsUpdated = stmt.executeUpdate();
                        if (rowsUpdated > 0) {
                            totalFkUpdated += rowsUpdated;
                            logger.info("            ✅ Updated {} row(s) in {}: {} -> {}", 
                                rowsUpdated, fkTableName, nobjectId, objectId);
                        } else {
                            logger.info("            ℹ️  No rows to update in {} (no references to {})", fkTableName, nobjectId);
                        }
                    } catch (SQLException e) {
                        // Log but continue - some tables might not have the column or might have constraints
                        logger.warn("            ⚠️  Could not update foreign keys in {}: {}", fkTableName, e.getMessage());
                    }
                }
                if (fkCount == 0) {
                    logger.info("         └─ No foreign keys found pointing to {}", mainTableName);
                }
            }
            
            // Also handle self-referencing foreign keys (e.g., Parent_ID in glossary table)
            String parentColumnName = getParentColumnName(facetName);
            if (parentColumnName != null) {
                logger.info("         └─ Updating self-referencing FK: {} in {}", parentColumnName, mainTableName);
                String updateParentSql = "UPDATE " + mainTableName + " SET " + parentColumnName + " = ? WHERE " + parentColumnName + " = ?";
                try (PreparedStatement stmt = conn.prepareStatement(updateParentSql)) {
                    stmt.setInt(1, objectId);  // Update TO object_id
                    stmt.setInt(2, nobjectId); // Update FROM nobject_id
                    int rowsUpdated = stmt.executeUpdate();
                    if (rowsUpdated > 0) {
                        totalFkUpdated += rowsUpdated;
                        logger.info("            ✅ Updated {} parent reference(s) in {}: {} -> {}", 
                            rowsUpdated, mainTableName, nobjectId, objectId);
                    } else {
                        logger.info("            ℹ️  No parent references to update in {}", mainTableName);
                    }
                }
            } else {
                logger.info("         └─ No self-referencing FK column for facet: {}", facetName);
            }
            
            logger.info("         ✅ Total foreign key references updated: {}", totalFkUpdated);
            
        } catch (SQLException e) {
            logger.error("         ❌ Error updating foreign keys for {}: {}", facetName, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Get parent column name for self-referencing foreign keys
     */
    private String getParentColumnName(String facetName) {
        switch (facetName.toLowerCase()) {
            case "glossary": return "Parent_ID";
            case "dataset":
            case "data set": return null; // Dataset might not have Parent_ID
            case "system": return "Parent_ID";
            case "process": return "parentid"; // Process table uses lowercase "parentid", not "Parent_ID"
            default: return null;
        }
    }

    /**
     * Get main table name for facet
     */
    private String getMainTableName(String facetName) {
        switch (facetName.toLowerCase()) {
            case "glossary": return "glossary";
            case "dataset":
            case "data set": return "dataset";
            case "system": return "system";
            case "process": return "process";
            default: return null;
        }
    }

    /**
     * Get ID column name for facet table
     * Tables using "ID" (uppercase): glossary, dataset
     * Tables using "id" (lowercase): system, process
     */
    private String getIdColumnName(String facetName) {
        switch (facetName.toLowerCase()) {
            case "glossary":
            case "dataset":
            case "data set":
                return "ID";
            case "system":
            case "process":
                return "id";
            default:
                return "ID"; // Default to uppercase
        }
    }

    /**
     * Get stakeholder table name from area key
     * Maps area keys like "glossary_stakeholder" to actual table names like "glossary_x_objectxpeople"
     */
    private String getStakeholderTableName(String facetName, String areaKeyPart) {
        String normalizedFacet = facetName.toLowerCase();
        
        // Map facet name to actual table name
        switch (normalizedFacet) {
            case "glossary": return "glossary_x_objectxpeople";
            case "dataset":
            case "data set": return "dataset_x_objectxpeople";
            case "system": return "system_x_objectxpeople";
            case "process": return "process_x_objectxpeople";
            default: return null;
        }
    }
    
    /**
     * Get stakeholder source column name for facet
     * Note: Column names match actual database column names (case-sensitive)
     */
    private String getStakeholderSourceColumn(String facetName) {
        switch (facetName.toLowerCase()) {
            case "glossary": return "GlossaryID";  // Actual column name in glossary_x_objectxpeople
            case "dataset":
            case "data set": return "Dataset_ID";  // Actual column name in dataset_x_objectxpeople
            case "system": return "SystemID";      // Actual column name in system_x_objectxpeople
            case "process": return "process_id";   // Actual column name in process_x_objectxpeople
            default: return null;
        }
    }

    /**
     * Get relationship source column name for facet
     */
    private String getRelationshipSourceColumn(String facetName) {
        switch (facetName.toLowerCase()) {
            case "glossary": return "glossary_id";
            case "dataset":
            case "data set": return "dataset_id";
            case "system": return "system_id";
            case "process": return "process_id";
            default: return null;
        }
    }
    
    /**
     * Get relationship source column name for specific relationship table
     * This handles tables that have specific column naming conventions
     */
    private String getRelationshipSourceColumnForTable(String tableName) {
        switch (tableName.toLowerCase()) {
            case "process_x_process": return "sourceprocess_id";
            case "glossary_x_glossary": return "SourceGlossaryID";
            case "glossary_x_system": return "GlossaryID";
            case "dataset_x_dataset": return "Source_ID";
            case "attribute_x_attribute": return "Source_AttributeID";
            case "dataset_mastersource": return "MasterSource"; // This is in dataset table
            default: return null;
        }
    }
    
    /**
     * Apply attribute changes: update Dataset_ID from nobjectId (cloned dataset) to objectId (original dataset)
     * Attributes were saved with cloned dataset ID, need to update to original dataset ID
     */
    private void applyAttributeChanges(Connection conn, int objectId, int nobjectId) throws SQLException {
        logger.info("            └─ [ATTRIBUTE] Updating Dataset_ID from {} to {} in attribute table", nobjectId, objectId);
        String sql = "UPDATE attribute SET Dataset_ID = ? WHERE Dataset_ID = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, objectId);
            stmt.setInt(2, nobjectId);
            int rowsUpdated = stmt.executeUpdate();
            if (rowsUpdated > 0) {
                logger.info("            ✅ Updated {} attribute(s) from dataset {} to dataset {}", rowsUpdated, nobjectId, objectId);
            } else {
                logger.info("            ℹ️  No attributes to update (no attributes with Dataset_ID = {})", nobjectId);
            }
        } catch (SQLException e) {
            logger.error("            ❌ Error updating attributes: {}", e.getMessage());
            throw e;
        }
    }
    
    /**
     * Apply value info changes: update dataset_id from nobjectId (cloned dataset) to objectId (original dataset)
     * Values_datastore was saved with cloned dataset ID, need to update to original dataset ID
     */
    private void applyValueInfoChanges(Connection conn, int objectId, int nobjectId) throws SQLException {
        logger.info("            └─ [VALUE INFO] Updating dataset_id from {} to {} in values_datastore table", nobjectId, objectId);
        String sql = "UPDATE values_datastore SET dataset_id = ? WHERE dataset_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, objectId);
            stmt.setInt(2, nobjectId);
            int rowsUpdated = stmt.executeUpdate();
            if (rowsUpdated > 0) {
                logger.info("            ✅ Updated {} value info record(s) from dataset {} to dataset {}", rowsUpdated, nobjectId, objectId);
            } else {
                logger.info("            ℹ️  No value info records to update (no records with dataset_id = {})", nobjectId);
            }
        } catch (SQLException e) {
            logger.error("            ❌ Error updating value info: {}", e.getMessage());
            throw e;
        }
    }
    
    /**
     * Delete an attribute by ID
     * Used when discarding pending changes for attributes created during CR
     */
    private void deleteAttributeById(Connection conn, int attributeId) throws SQLException {
        String sql = "DELETE FROM attribute WHERE ID = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, attributeId);
            int rowsDeleted = stmt.executeUpdate();
            logger.info("      ✅ Deleted attribute {} from attribute table, rows: {}", attributeId, rowsDeleted);
        }
    }
    
    /**
     * Delete value info (values_datastore) by ID
     * Used when discarding pending changes for value info created during CR
     */
    private void deleteValueInfoById(Connection conn, int datastoreId) throws SQLException {
        String sql = "DELETE FROM values_datastore WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, datastoreId);
            int rowsDeleted = stmt.executeUpdate();
            logger.info("      ✅ Deleted value info {} from values_datastore table, rows: {}", datastoreId, rowsDeleted);
        }
    }

    /**
     * Get actual table name for impact relationship based on facet and relationship info
     * Handles reversed table names (e.g., system_X_product -> product_x_system)
     */
    private String getImpactTableName(String facetName, String relationshipInfo) {
        String prefix = facetName.toLowerCase();
        String lower = relationshipInfo.toLowerCase();
        
        // Dataset has special table names
        if ("dataset".equals(prefix)) {
            if (lower.contains("product")) return "product_x_dataset"; // Product comes first
            if (lower.contains("client")) return "client_x_dataset";   // Client comes first
            if (lower.contains("legal")) return "dataset_x_legal";     // Dataset comes first for legal
        }
        
        // Glossary has special table names
        if ("glossary".equals(prefix)) {
            if (lower.contains("product")) return "product_x_glossary"; // Product comes first
            if (lower.contains("client")) return "client_x_glossary";    // Client comes first
            if (lower.contains("system")) return "glossary_x_system";   // Glossary comes first
            if (lower.contains("process")) return "glossary_x_process"; // Glossary comes first
            if (lower.contains("project")) return "glossary_x_project"; // Glossary comes first
            if (lower.contains("dataset")) return "glossary_x_dataset"; // Glossary comes first
            if (lower.contains("attribute")) return "glossary_x_attribute"; // Glossary comes first
        }
        
        // System has special table names
        if ("system".equals(prefix)) {
            if (lower.contains("product")) return "product_x_system";   // Product comes first
            if (lower.contains("client")) return "client_x_system";    // Client comes first
            if (lower.contains("legal")) return "system_x_legal";       // System comes first
            if (lower.contains("process")) return "process_x_system";  // Process comes first (reversed)
        }
        
        // Process has special table names
        if ("process".equals(prefix)) {
            // Special case: process_x_process (predecessor relationships)
            // Must check that BOTH sides are "process" (not just the first part)
            if (lower.equals("process_x_process") || 
                (lower.split("_x_").length >= 2 && 
                 lower.split("_x_")[0].equals("process") && 
                 lower.split("_x_")[1].equals("process"))) {
                return "process_x_process";
            }
            if (lower.contains("system")) return "process_x_system";
            if (lower.contains("product")) return "product_x_process";   // Product comes first
            if (lower.contains("client")) return "client_x_process";     // Client comes first
            if (lower.contains("glossary")) return "glossary_x_process";  // Glossary comes first
            if (lower.contains("project")) return "project_x_process";    // Project comes first
            if (lower.contains("policy")) return "policy_x_process";      // Policy comes first
            if (lower.contains("interface")) return "process_x_interface";
            if (lower.contains("legal")) return "process_x_legal";
            if (lower.contains("dataset")) return "process_x_dataset";
            if (lower.contains("attribute")) return "process_x_attribute";
        }
        
        // For other facets, try the standard pattern
        if (lower.contains("product")) return prefix + "_x_product";
        if (lower.contains("client")) return prefix + "_x_client";
        if (lower.contains("legal") || lower.contains("regulation")) return prefix + "_x_legal";
        if (lower.contains("system")) return prefix + "_x_system";
        if (lower.contains("process")) return prefix + "_x_process";
        if (lower.contains("glossary")) return prefix + "_x_glossary";
        if (lower.contains("dataset")) return prefix + "_x_dataset";
        if (lower.contains("project")) return prefix + "_x_project";
        if (lower.contains("policy")) return prefix + "_x_policy";
        if (lower.contains("interface")) return prefix + "_x_interface";
        if (lower.contains("attribute")) return prefix + "_x_attribute";
        
        return null;
    }

    /**
     * Get impact source column name for facet and area key
     * Returns the column name that references the facet's ID in the actual table
     * Handles special cases where tables use different casing conventions
     * 
     * Returns a list of possible column names to try (in order of preference)
     * This handles variations like: process_id, processid, Process_ID, ProcessID
     */
    private String getImpactSourceColumn(String facetName, String areaKey, String tableName) {
        String normalizedFacet = facetName.toLowerCase();
        String normalizedTable = tableName.toLowerCase();
        
        // Special case: process_x_process uses "sourceprocess_id" as source column
        if (normalizedTable.equals("process_x_process")) {
            if (normalizedFacet.equals("process")) return "sourceprocess_id";
        }
        
        // Special cases for tables with lowercase column names
        if (normalizedTable.equals("process_x_system")) {
            if (normalizedFacet.equals("system")) return "system_id";
            if (normalizedFacet.equals("process")) return "process_id";
        }
        
        // process_x_attribute uses "processid" (no underscore)
        if (normalizedTable.equals("process_x_attribute")) {
            if (normalizedFacet.equals("process")) return "processid";
        }
        
        if (normalizedTable.equals("process_x_product") || normalizedTable.equals("process_x_client") ||
            normalizedTable.equals("process_x_legal") || normalizedTable.equals("process_x_dataset") ||
            normalizedTable.equals("process_x_interface")) {
            if (normalizedFacet.equals("process")) return "process_id";
        }
        
        // Special case: product_x_glossary uses lowercase "glossaryid" (not "Glossary_ID")
        if (normalizedTable.equals("product_x_glossary")) {
            if (normalizedFacet.equals("glossary")) return "glossaryid";
        }
        
        // Special case: client_x_glossary uses PascalCase "Glossary_ID"
        if (normalizedTable.equals("client_x_glossary")) {
            if (normalizedFacet.equals("glossary")) return "Glossary_ID";
        }
        
        // project_x_process uses lowercase column name "process_id"
        if (normalizedTable.equals("project_x_process")) {
            if (normalizedFacet.equals("process")) return "process_id";
        }
        
        // client_x_process uses PascalCase "Process_ID"
        if (normalizedTable.equals("client_x_process")) {
            if (normalizedFacet.equals("process")) return "Process_ID";
        }
        
        // product_x_process uses PascalCase "Process_ID"
        if (normalizedTable.equals("product_x_process")) {
            if (normalizedFacet.equals("process")) return "Process_ID";
        }
        
        // policy_x_process
        if (normalizedTable.equals("policy_x_process")) {
            if (normalizedFacet.equals("process")) return "Process_ID";
        }
        
        // glossary_x_process
        if (normalizedTable.equals("glossary_x_process")) {
            if (normalizedFacet.equals("process")) return "Process_ID";
        }
        
        // Check if table name starts with facet name (e.g., glossary_x_product -> Glossary_ID)
        // or if it's reversed (e.g., product_x_system -> System_ID)
        String[] tableParts = tableName.split("_x_");
        if (tableParts.length >= 2) {
            String firstPart = tableParts[0].toLowerCase();
            String secondPart = tableParts[1].toLowerCase();
            
            // If facet matches first part, use first part column
            if (firstPart.equals(normalizedFacet) || firstPart.equals("data set")) {
                return capitalizeFirst(firstPart) + "_ID";
            }
            // If facet matches second part, use second part column
            if (secondPart.equals(normalizedFacet) || secondPart.equals("data set")) {
                return capitalizeFirst(secondPart) + "_ID";
            }
        }
        
        // Fallback: use facet name
        switch (normalizedFacet) {
            case "glossary": return "Glossary_ID";
            case "dataset":
            case "data set": return "Dataset_ID";
            case "system": return "System_ID";
            case "process": return "process_id";  // Process uses lowercase
            default:
                return capitalizeFirst(normalizedFacet) + "_ID";
        }
    }
    
    /**
     * Find the actual source column in a table by trying multiple naming conventions.
     * Returns the actual column name that exists in the table, or null if not found.
     */
    private String findActualSourceColumn(Connection conn, String tableName, String expectedColumn) throws SQLException {
        // Build a list of possible column name variations to try
        List<String> possibleNames = new ArrayList<>();
        possibleNames.add(expectedColumn);
        
        // Try without underscore (e.g., process_id -> processid)
        possibleNames.add(expectedColumn.replace("_", ""));
        
        // Try lowercase version
        possibleNames.add(expectedColumn.toLowerCase());
        possibleNames.add(expectedColumn.toLowerCase().replace("_", ""));
        
        // Try PascalCase version (e.g., process_id -> Process_ID)
        if (expectedColumn.contains("_")) {
            String[] parts = expectedColumn.split("_");
            StringBuilder pascalCase = new StringBuilder();
            for (String part : parts) {
                if (!part.isEmpty()) {
                    pascalCase.append(part.substring(0, 1).toUpperCase());
                    pascalCase.append(part.substring(1).toLowerCase());
                    pascalCase.append("_");
                }
            }
            if (pascalCase.length() > 0) {
                possibleNames.add(pascalCase.substring(0, pascalCase.length() - 1)); // Remove trailing underscore
            }
        }
        
        DatabaseMetaData metaData = conn.getMetaData();
        try (ResultSet columns = metaData.getColumns(null, null, tableName, null)) {
            List<String> actualColumns = new ArrayList<>();
            while (columns.next()) {
                actualColumns.add(columns.getString("COLUMN_NAME"));
            }
            
            // Try to find a matching column (case-insensitive)
            for (String possible : possibleNames) {
                for (String actual : actualColumns) {
                    if (actual.equalsIgnoreCase(possible)) {
                        logger.debug("            └─ Found column '{}' matching expected '{}'", actual, expectedColumn);
                        return actual;
                    }
                }
            }
            
            logger.warn("            └─ Could not find column matching '{}' in table {}. Tried: {}. Available: {}", 
                expectedColumn, tableName, possibleNames, actualColumns);
        }
        return null;
    }
    
    private String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) return str;
        if (str.equals("data set")) return "Dataset";
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
    
    /**
     * Get processing priority for area keys.
     * Lower numbers are processed first.
     * 
     * Order:
     * 1. summary#attribute (priority 1) - Must update Dataset_ID before deleting cloned dataset
     * 2. summary#dataset_value_info (priority 2) - Must update dataset_id before deleting cloned dataset  
     * 3. relationships# (priority 3) - Copy relationships before deleting cloned object
     * 4. impact# (priority 4) - Copy impact rows before deleting cloned object
     * 5. stakeholders# (priority 5) - Copy stakeholder rows before deleting cloned object
     * 6. summary (priority 100) - Delete cloned object LAST to avoid cascade deletes
     */
    private int getAreaProcessingPriority(String areaKey) {
        if (areaKey == null) return 99;
        
        if (areaKey.equals("summary#attribute")) return 1;
        if (areaKey.equals("summary#dataset_value_info")) return 2;
        if (areaKey.startsWith("relationships#")) return 3;
        if (areaKey.equals("data-content")) return 3; // Same priority as relationships
        if (areaKey.startsWith("documents#")) return 3; // Same priority as relationships
        if (areaKey.startsWith("impact#")) return 4;
        if (areaKey.startsWith("stakeholders#")) return 5;
        if (areaKey.equals("summary")) return 100; // Last - delete cloned object
        
        return 50; // Unknown areas processed in middle
    }
    
    /**
     * Save snapshot of changes before deleting cloned rows.
     * This allows displaying changes in "CHANGES TO REVIEW" even after CR is completed/cancelled.
     * Handles ALL area types across all 4 facets:
     * - Summary (all facets)
     * - Attributes (Dataset)
     * - Value Info (Dataset - Values tab)
     * - Impact relationships (all facets)
     * - Stakeholders (all facets)
     * - Relationships (all facets)
     * - Data Content (System)
     */
    private void saveChangesSnapshot(Connection conn, String facetName, int changeRequestId, 
                                   int objectId, int nobjectId, String areaKey) throws SQLException {
        String changesTableName = getChangesTableName(facetName);
        if (changesTableName == null) {
            logger.warn("⚠️  Unknown facet for saving snapshot: {}", facetName);
            return;
        }
        
        try {
            // Get snapshot data based on area type
            String snapshotJson = null;
            
            logger.debug("   📸 Generating snapshot for {} {} {} (objectId={}, nobjectId={})", 
                facetName, objectId, areaKey, objectId, nobjectId);
            
            if ("summary".equals(areaKey)) {
                // For summary, compare original vs cloned and save field changes
                snapshotJson = getSummaryChangesSnapshot(conn, facetName, objectId, nobjectId);
            } else if (areaKey.equals("summary#attribute")) {
                // For Dataset attributes
                snapshotJson = getAttributeChangesSnapshot(conn, objectId, nobjectId);
            } else if (areaKey.equals("summary#dataset_value_info")) {
                // For Dataset Values tab (value info metadata)
                snapshotJson = getValueInfoChangesSnapshot(conn, objectId, nobjectId);
            } else if (areaKey.startsWith("impact#")) {
                // For impact relationships (all facets)
                String relationType = areaKey.substring("impact#".length());
                snapshotJson = getImpactChangesSnapshot(conn, facetName, objectId, nobjectId, relationType);
            } else if (areaKey.startsWith("stakeholders#")) {
                // For stakeholders (all facets)
                snapshotJson = getStakeholderChangesSnapshot(conn, facetName, objectId, nobjectId);
            } else if (areaKey.startsWith("relationships#")) {
                // For relationships (all facets)
                String relTable = areaKey.substring("relationships#".length());
                snapshotJson = getRelationshipChangesSnapshot(conn, facetName, objectId, nobjectId, relTable);
            } else if ("data-content".equals(areaKey) && "system".equals(facetName)) {
                // For System data content (glossary_x_system from System perspective)
                snapshotJson = getDataContentChangesSnapshot(conn, facetName, objectId, nobjectId);
            }
            
            // IMPORTANT: Always save snapshot, even if empty (to record that we tried)
            if (snapshotJson == null || snapshotJson.trim().isEmpty()) {
                snapshotJson = "[]";
                logger.warn("⚠️  Empty snapshot for {} {} {} - saving empty array", facetName, objectId, areaKey);
            } else {
                // Count changes for logging
                int changeCount = countChangesInSnapshot(snapshotJson);
                logger.debug("   📸 Generated snapshot: {} changes (length: {} chars)", changeCount, snapshotJson.length());
            }
            
            // Update the mapping record with snapshot
            String updateSql = "UPDATE `" + changesTableName + "` " +
                               "SET `changes_snapshot` = ? " +
                               "WHERE `change_request_id` = ? " +
                               "AND `object_id` = ? " +
                               "AND `area_key` = ?";
            
            try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
                stmt.setString(1, snapshotJson);
                stmt.setInt(2, changeRequestId);
                stmt.setInt(3, objectId);
                stmt.setString(4, areaKey);
                int updated = stmt.executeUpdate();
                if (updated > 0) {
                    int changeCount = countChangesInSnapshot(snapshotJson);
                    logger.info("   ✅ Saved snapshot for {} {} area {} ({} changes)", 
                        facetName, objectId, areaKey, changeCount);
                } else {
                    logger.error("❌ FAILED to save snapshot - no rows updated for {} {} {} (mapping may not exist!)", 
                        facetName, objectId, areaKey);
                }
            }
        } catch (SQLException e) {
            logger.error("❌ Error saving snapshot for {} {} {}: {}", facetName, objectId, areaKey, e.getMessage(), e);
            // Don't fail the entire operation, but log as error
        }
    }
    
    /**
     * Count changes in snapshot JSON (for logging)
     */
    private int countChangesInSnapshot(String snapshotJson) {
        try {
            com.google.gson.JsonArray array = com.google.gson.JsonParser.parseString(snapshotJson).getAsJsonArray();
            return array.size();
        } catch (Exception e) {
            return -1; // Error parsing
        }
    }
    
    /**
     * Get summary changes snapshot as JSON.
     * Saves ALL fields (original and cloned) with resolved FK values, same format as compareObjects()
     * This ensures the display matches exactly what was shown before CR completion
     */
    private String getSummaryChangesSnapshot(Connection conn, String facetName, int objectId, int nobjectId) throws SQLException {
        JsonArray changesArray = new JsonArray();
        
        String[] columnsToCompare = getColumnsToCompare(facetName);
        String tableName = getMainTableName(facetName);
        
        if (tableName == null || columnsToCompare.length == 0) {
            return "[]";
        }
        
        // Check if this is a CREATE CR (objectId == nobjectId or objectId == 0)
        // For CREATE CRs, all values are "new" - show them in newValue column
        boolean isCreateCR = (objectId == nobjectId) || (objectId == 0);
        
        // Get the correct ID column name for this facet (ID for glossary/dataset, id for system/process)
        String idColumnName = getIdColumnName(facetName);
        
        // CRITICAL: Read ALL original values FIRST before any modifications
        // This ensures we capture the true "old" values (X) even if objectId was modified
        // in a previous iteration or by another process within the same transaction
        Map<String, String> originalValues = new HashMap<>();
        if (!isCreateCR) {
            logger.info("   📸 [CRITICAL] Reading ALL original values from objectId={} BEFORE any modifications (using column: {})", objectId, idColumnName);
            String selectAllSql = "SELECT * FROM " + tableName + " WHERE `" + idColumnName + "` = ?";
            try (PreparedStatement stmt = conn.prepareStatement(selectAllSql)) {
                stmt.setInt(1, objectId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        ResultSetMetaData meta = rs.getMetaData();
                        int colCount = meta.getColumnCount();
                        for (int i = 1; i <= colCount; i++) {
                            String colName = meta.getColumnName(i);
                            Object val = rs.getObject(i);
                            if (val != null) {
                                // Handle boolean fields specially
                                if (colName.equalsIgnoreCase("DQ_Automation")) {
                                    int intVal = rs.getInt(i);
                                    originalValues.put(colName.toLowerCase(), (intVal == 1) ? "true" : "false");
                                } else {
                                    originalValues.put(colName.toLowerCase(), val.toString());
                                }
                            } else {
                                originalValues.put(colName.toLowerCase(), null);
                            }
                        }
                        logger.info("   📸 [CRITICAL] Captured {} original values from objectId={} (OLD VALUES = X)", originalValues.size(), objectId);
                    } else {
                        logger.warn("   📸 [CRITICAL] No row found for objectId={} in table {} (using column: {}) - cannot capture original values", objectId, tableName, idColumnName);
                    }
                }
            } catch (SQLException e) {
                logger.error("   📸 [CRITICAL] Error reading original values from objectId={} in table {} (using column: {}): {}", objectId, tableName, idColumnName, e.getMessage());
                // Continue - will try to read individually as fallback
            }
        }
        
        // CRITICAL: Also read ALL cloned values FIRST before any modifications
        // This ensures we capture the true "new" values (Y) from nobjectId
        Map<String, String> clonedValues = new HashMap<>();
        logger.info("   📸 [CRITICAL] Reading ALL cloned values from nobjectId={} BEFORE any modifications (using column: {})", nobjectId, idColumnName);
        String selectAllClonedSql = "SELECT * FROM " + tableName + " WHERE `" + idColumnName + "` = ?";
        try (PreparedStatement stmt = conn.prepareStatement(selectAllClonedSql)) {
            stmt.setInt(1, nobjectId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int colCount = meta.getColumnCount();
                    for (int i = 1; i <= colCount; i++) {
                        String colName = meta.getColumnName(i);
                        Object val = rs.getObject(i);
                        if (val != null) {
                            // Handle boolean fields specially
                            if (colName.equalsIgnoreCase("DQ_Automation")) {
                                int intVal = rs.getInt(i);
                                clonedValues.put(colName.toLowerCase(), (intVal == 1) ? "true" : "false");
                            } else {
                                clonedValues.put(colName.toLowerCase(), val.toString());
                            }
                        } else {
                            clonedValues.put(colName.toLowerCase(), null);
                        }
                    }
                    logger.info("   📸 [CRITICAL] Captured {} cloned values from nobjectId={} (NEW VALUES = Y)", clonedValues.size(), nobjectId);
                } else {
                    logger.error("   📸 [CRITICAL] No row found for nobjectId={} in table {} (using column: {}) - cannot capture cloned values! This will cause NEW VALUE to be empty!", nobjectId, tableName, idColumnName);
                    // For CREATE CRs, if nobjectId doesn't exist, this is a critical error
                    if (isCreateCR) {
                        logger.error("   📸 [CRITICAL] CREATE CR: nobjectId={} should exist but doesn't! Returning empty snapshot.", nobjectId);
                        return "[]";
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("   📸 [CRITICAL] Error reading cloned values from nobjectId={} in table {} (using column: {}): {}", nobjectId, tableName, idColumnName, e.getMessage());
            // For CREATE CRs, if we can't read nobjectId, return empty snapshot
            if (isCreateCR) {
                logger.error("   📸 [CRITICAL] CREATE CR: Cannot read nobjectId={}! Returning empty snapshot.", nobjectId);
                return "[]";
            }
            // Continue - will try to read individually as fallback
        }
        
        for (String col : columnsToCompare) {
            try {
                String sql = "SELECT " + col + " FROM " + tableName + " WHERE `" + idColumnName + "` = ?";
                String originalVal = null;
                String clonedVal = null;
                
                // Get column name in lowercase for Map lookups
                String colLower = col.toLowerCase();
                
                // For CREATE CR, only get the created object's values (nobjectId)
                // For EDIT CR, get both original and cloned values
                if (!isCreateCR) {
                    // CRITICAL: Use pre-captured original value if available
                    // This ensures we get the value BEFORE any modifications (OLD VALUE = X)
                    if (originalValues.containsKey(colLower)) {
                        originalVal = originalValues.get(colLower);
                        logger.debug("   📸 Using pre-captured original value for {}: '{}' (OLD VALUE = X)", col, originalVal);
                    } else {
                        // Fallback: read from database (may have been modified, but better than nothing)
                        logger.warn("   📸 Column {} not found in pre-captured values, reading from database (may be modified)", col);
                        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                            stmt.setInt(1, objectId);
                            try (ResultSet rs = stmt.executeQuery()) {
                                if (rs.next()) {
                                    // Handle boolean fields specially (DQ_Automation)
                                    if (col.equalsIgnoreCase("DQ_Automation")) {
                                        Object obj = rs.getObject(1);
                                        if (obj == null) {
                                            originalVal = "false";
                                        } else {
                                            int intVal = rs.getInt(1);
                                            originalVal = (intVal == 1) ? "true" : "false";
                                        }
                                    } else {
                                        originalVal = rs.getString(1);
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Get cloned/created value - also get raw Object for CREATE CRs to match getObjectFields exactly
                Object clonedValObj = null; // Raw object value for CREATE CRs
                int clonedIntVal = 0; // For boolean fields
                
                // CRITICAL: Use pre-captured cloned value if available
                // This ensures we get the value BEFORE any modifications (NEW VALUE = Y)
                if (clonedValues.containsKey(colLower)) {
                    String preCapturedVal = clonedValues.get(colLower);
                    if (preCapturedVal != null) {
                        clonedVal = preCapturedVal;
                        // For boolean fields, we need to parse it
                        if (col.equalsIgnoreCase("DQ_Automation")) {
                            clonedValObj = "true".equals(preCapturedVal) ? 1 : 0;
                            clonedIntVal = "true".equals(preCapturedVal) ? 1 : 0;
                        } else {
                            clonedValObj = preCapturedVal;
                        }
                        logger.debug("   📸 Using pre-captured cloned value for {}: '{}' (NEW VALUE = Y)", col, clonedVal);
                    }
                } else {
                    // Fallback: read from database (may have been modified, but better than nothing)
                    logger.warn("   📸 Column {} not found in pre-captured cloned values, reading from database (may be modified)", col);
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setInt(1, nobjectId);
                        try (ResultSet rs = stmt.executeQuery()) {
                            if (rs.next()) {
                                // Get raw Object first (same as getObjectFields line 1098)
                                clonedValObj = rs.getObject(1);
                                
                                // Handle boolean fields specially (DQ_Automation)
                                if (col.equalsIgnoreCase("DQ_Automation")) {
                                    if (clonedValObj == null) {
                                        clonedVal = "false";
                                    } else {
                                        clonedIntVal = rs.getInt(1);
                                        clonedVal = (clonedIntVal == 1) ? "true" : "false";
                                    }
                                } else {
                                    clonedVal = rs.getString(1);
                                }
                            } else {
                                logger.error("   📸 [ERROR] No row found for nobjectId={} when reading column {} (using column: {}) - NEW VALUE will be empty!", nobjectId, col, idColumnName);
                            }
                        }
                    }
                }
                
                // Normalize null and empty strings
                String normalizedOriginal = (originalVal == null || originalVal.trim().isEmpty()) ? null : originalVal.trim();
                String normalizedCloned = (clonedVal == null || clonedVal.trim().isEmpty()) ? null : clonedVal.trim();
                
                // For boolean fields, always show the value even if both are false
                boolean isBooleanField = col.equalsIgnoreCase("DQ_Automation");
                
                // For CREATE CR: show all non-empty values as new values (EXACT same logic as getObjectFields)
                if (isCreateCR) {
                    String displayVal = null;
                    
                    // Handle boolean fields specially (EXACT same as getObjectFields lines 1102-1108)
                    if (isBooleanField) {
                        if (clonedValObj == null) {
                            displayVal = "false";
                        } else {
                            // Use int value we already fetched (same as getObjectFields line 1106)
                            displayVal = (clonedIntVal == 1) ? "true" : "false";
                        }
                    } else {
                        // For other fields, only include if not null/empty (EXACT same as getObjectFields line 1111)
                        if (clonedValObj != null && !clonedValObj.toString().isEmpty()) {
                            // Resolve FK values to display names (EXACT same as getObjectFields line 1113)
                            displayVal = resolveForeignKeyValue(conn, facetName, col, clonedValObj.toString());
                        }
                    }
                    
                    // Add field if we have a display value (always for boolean fields, EXACT same as getObjectFields line 1118)
                    if (displayVal != null) {
                        JsonObject change = new JsonObject();
                        change.addProperty("fieldName", formatFieldName(col));
                        change.addProperty("oldValue", ""); // Empty for CREATE
                        change.addProperty("newValue", displayVal);
                        change.addProperty("operation", "Created");
                        changesArray.add(change);
                    }
                } else {
                    // For EDIT CR: show only changed fields
                    // Skip if both are null/empty (no change), except for boolean fields
                    if (!isBooleanField && normalizedOriginal == null && normalizedCloned == null) {
                        continue;
                    }
                    
                    // For boolean fields, ensure we have values (default to "false" if null)
                    if (isBooleanField) {
                        if (normalizedOriginal == null) normalizedOriginal = "false";
                        if (normalizedCloned == null) normalizedCloned = "false";
                    }
                    
                    // ONLY save fields that have DIFFERENT values (changed fields)
                    // This matches the behavior shown in screenshots 3-4 (correct display)
                    // Skip if values are the same (no change to show)
                    if (!Objects.equals(normalizedOriginal, normalizedCloned)) {
                        // Resolve FK values to display names (same as compareObjects)
                        String displayOldVal = isBooleanField ? normalizedOriginal : resolveForeignKeyValue(conn, facetName, col, normalizedOriginal);
                        String displayNewVal = isBooleanField ? normalizedCloned : resolveForeignKeyValue(conn, facetName, col, normalizedCloned);
                        
                        // Normalize display values
                        String normalizedDisplayOld = (displayOldVal == null || displayOldVal.trim().isEmpty()) ? null : displayOldVal.trim();
                        String normalizedDisplayNew = (displayNewVal == null || displayNewVal.trim().isEmpty()) ? null : displayNewVal.trim();
                        
                        // Only add if there's actually a visible difference (not both null/empty after resolution)
                        if (normalizedDisplayOld != null || normalizedDisplayNew != null) {
                            JsonObject change = new JsonObject();
                            change.addProperty("fieldName", formatFieldName(col));
                            change.addProperty("oldValue", normalizedDisplayOld != null ? normalizedDisplayOld : "");
                            change.addProperty("newValue", normalizedDisplayNew != null ? normalizedDisplayNew : "");
                            change.addProperty("operation", "Updated");
                            changesArray.add(change);
                        }
                    }
                }
            } catch (SQLException e) {
                // Column may not exist, skip it
                continue;
            }
        }
        
        return changesArray.toString();
    }
    
    /**
     * Resolve foreign key values to their display names (same as PendingChangesServlet)
     */
    private String resolveForeignKeyValue(Connection conn, String facetName, String columnName, String value) {
        if (value == null || value.isEmpty()) return value;
        
        String col = columnName.toLowerCase();
        
        // Handle boolean fields (DQ_Automation)
        if (col.equals("dq_automation") || col.equals("dqautomation")) {
            try {
                int intVal = Integer.parseInt(value);
                return (intVal == 1) ? "true" : "false";
            } catch (NumberFormatException e) {
                if ("true".equalsIgnoreCase(value) || "1".equals(value)) {
                    return "true";
                }
                return "false";
            }
        }
        
        try {
            int id = Integer.parseInt(value);
            if (id <= 0) return value;
            
            // Status field
            if (col.equals("status")) {
                return lookupValue(conn, "status", id, "primaryname", value);
            }
            
            // Lifecycle field
            if (col.equals("lifecycle") || col.equals("lifecycle_status")) {
                if ("dataset".equals(facetName)) {
                    return lookupValue(conn, "dataset_lifecycle", id, "PrimaryName", value);
                } else if ("glossary".equals(facetName)) {
                    return lookupValue(conn, "glossary_lifecycle", id, "PrimaryName", value);
                } else if ("system".equals(facetName)) {
                    return lookupValue(conn, "system_lifecycle", id, "Name", value);
                } else if ("process".equals(facetName)) {
                    return lookupValue(conn, "process_lifecycle_status", id, "PrimaryName", value);
                }
                return value;
            }
            
            // Dataset Type
            if (col.equals("datasettype")) {
                return lookupValue(conn, "dataset_type", id, "PrimaryName", value);
            }
            
            // Access Control Type
            if (col.equals("accesscontroltype") && "dataset".equals(facetName)) {
                return lookupValue(conn, "viewing", id, "Name", value);
            }
            
            // Master Source
            if (col.equals("mastersource")) {
                return lookupValue(conn, "system", id, "Name", value);
            }
            
            // Glossary reference
            if (col.equals("glossary")) {
                return lookupValue(conn, "glossary", id, "Name", value);
            }
            
            // Parent ID
            if (col.equals("parent_id") || col.equals("parentid")) {
                String parentTable = facetName.toLowerCase();
                String nameCol;
                if ("dataset".equals(parentTable)) {
                    nameCol = "PrimaryName";
                } else if ("process".equals(parentTable)) {
                    nameCol = "primaryname";
                } else if ("system".equals(parentTable) || "glossary".equals(parentTable)) {
                    nameCol = "Name";
                } else {
                    nameCol = "Name";
                }
                return lookupValue(conn, parentTable, id, nameCol, value);
            }
            
            // Type field
            if (col.equals("type") && "system".equals(facetName)) {
                return lookupValue(conn, "system_type", id, "Name", value);
            }
            if (col.equals("type") && "glossary".equals(facetName)) {
                return lookupValue(conn, "glossary_type", id, "Name", value);
            }
            if (col.equals("type") && "process".equals(facetName)) {
                return lookupValue(conn, "process_type", id, "PrimaryName", value);
            }
            
            // Format Type
            if (col.equals("format_type") && "glossary".equals(facetName)) {
                return lookupValue(conn, "glossary_format_type", id, "Name", value);
            }
            
            // Classification
            if (col.equals("classification") && "system".equals(facetName)) {
                return lookupValue(conn, "system_classification", id, "Name", value);
            }
            
            // Duration Type
            if (col.equals("duration_type") && "process".equals(facetName)) {
                return lookupValue(conn, "process_duration_type", id, "PrimaryName", value);
            }
            
            // Is_Public
            if (col.equals("is_public") || col.equals("ispublic")) {
                return lookupValue(conn, "viewing", id, "Name", value);
            }
            
            // KDE
            if (col.equals("kde") && "glossary".equals(facetName)) {
                return lookupValue(conn, "glossary_kde_type", id, "PrimaryName", value);
            }
            
            // Security Classification
            if ((col.equals("security_classification") || col.equals("securityclassification")) && "glossary".equals(facetName)) {
                return lookupValue(conn, "security_classification", id, "PrimaryName", value);
            }
            
            // CIA Ratings
            if (col.equals("confidentiality_rating") || col.equals("integrity_rating") || col.equals("availability_rating")) {
                if ("glossary".equals(facetName) || "system".equals(facetName)) {
                    return lookupValue(conn, "cia_rating", id, "Values", value);
                }
            }
            
            // External
            if (col.equals("external") && "system".equals(facetName)) {
                if ("1".equals(value) || "true".equalsIgnoreCase(value)) {
                    return "Yes";
                } else if ("0".equals(value) || "false".equalsIgnoreCase(value)) {
                    return "No";
                }
                return value;
            }
            
        } catch (NumberFormatException e) {
            // Not a number, return as-is
        }
        
        return value;
    }
    
    /**
     * Lookup a value from a reference table
     */
    private String lookupValue(Connection conn, String tableName, int id, String nameColumn, String defaultValue) {
        try {
            String sql = "SELECT " + nameColumn + " FROM " + tableName + " WHERE ID = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String name = rs.getString(1);
                        if (name != null && !name.isEmpty()) {
                            return name;
                        }
                    }
                }
            }
        } catch (SQLException e) {
            // Try lowercase id
            try {
                String sql = "SELECT " + nameColumn + " FROM " + tableName + " WHERE id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, id);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            String name = rs.getString(1);
                            if (name != null && !name.isEmpty()) {
                                return name;
                            }
                        }
                    }
                }
            } catch (SQLException e2) {
                // Return default
            }
        }
        return defaultValue;
    }
    
    /**
     * Format field name for display (same as PendingChangesServlet.formatFieldName)
     */
    private String formatFieldName(String columnName) {
        if (columnName == null) return "";
        
        // Special mappings for common field names
        Map<String, String> specialMappings = new HashMap<>();
        specialMappings.put("primaryname", "Primary Name");
        specialMappings.put("ref_number", "Ref Number");
        specialMappings.put("refnumber", "Refnumber");
        specialMappings.put("is_public", "Is Public");
        specialMappings.put("ispublic", "Is Public");
        specialMappings.put("format_type", "Format Type");
        specialMappings.put("format", "Format Description");
        specialMappings.put("ldm_reference", "LDM Reference");
        specialMappings.put("business_logic", "Business Logic");
        specialMappings.put("security_classification", "Security Classification");
        specialMappings.put("confidentiality_rating", "Confidentiality Rating");
        specialMappings.put("integrity_rating", "Integrity Rating");
        specialMappings.put("availability_rating", "Availability Rating");
        specialMappings.put("lifecycle_status", "Lifecycle Status");
        specialMappings.put("datasettype", "Dataset Type");
        specialMappings.put("accesscontroltype", "Access Control Type");
        specialMappings.put("mastersource", "Master Source");
        specialMappings.put("duration_type", "Duration Type");
        specialMappings.put("dq_automation", "DQ Automation");
        
        String lower = columnName.toLowerCase();
        if (specialMappings.containsKey(lower)) {
            return specialMappings.get(lower);
        }
        
        // Default: convert snake_case to Title Case
        String result = columnName.replace("_", " ");
        // Add space before capital letters (camelCase)
        result = result.replaceAll("([a-z])([A-Z])", "$1 $2");
        // Capitalize first letter of each word
        String[] words = result.split("\\s+");
        StringBuilder formatted = new StringBuilder();
        for (String word : words) {
            if (formatted.length() > 0) formatted.append(" ");
            if (!word.isEmpty()) {
                formatted.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    formatted.append(word.substring(1));
                }
            }
        }
        return formatted.toString();
    }
    
    /**
     * Get Value Info changes snapshot as JSON (Dataset Values tab)
     * Reuses logic from PendingChangesServlet.compareValueInfoChanges()
     */
    private String getValueInfoChangesSnapshot(Connection conn, int datasetId, int datastoreId) throws SQLException {
        JsonArray changesArray = new JsonArray();
        
        // Check if values_datastore exists in original dataset
        String checkOriginalSql = "SELECT COUNT(*) as count FROM values_datastore WHERE id = ? AND dataset_id = ?";
        boolean isNewValueInfo = true;
        try (PreparedStatement checkStmt = conn.prepareStatement(checkOriginalSql)) {
            checkStmt.setInt(1, datastoreId);
            checkStmt.setInt(2, datasetId);
            try (ResultSet checkRs = checkStmt.executeQuery()) {
                if (checkRs.next()) {
                    isNewValueInfo = (checkRs.getInt("count") == 0);
                }
            }
        }
        
        String sql = "SELECT frequency, frequency_comments, availability, availability_comments, values_in_axon " +
                     "FROM values_datastore WHERE id = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, datastoreId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String operation = isNewValueInfo ? "Inserted" : "Updated";
                    
                    // Add changes for each field
                    String frequency = rs.getString("frequency");
                    if (frequency != null && !frequency.isEmpty()) {
                        JsonObject change = new JsonObject();
                        change.addProperty("operation", operation);
                        change.addProperty("fieldName", "Value Update Frequency");
                        change.addProperty("oldValue", "");
                        change.addProperty("newValue", frequency);
                        changesArray.add(change);
                    }
                    
                    String availability = rs.getString("availability");
                    if (availability != null && !availability.isEmpty()) {
                        JsonObject change = new JsonObject();
                        change.addProperty("operation", operation);
                        change.addProperty("fieldName", "Availability");
                        change.addProperty("oldValue", "");
                        change.addProperty("newValue", availability);
                        changesArray.add(change);
                    }
                    
                    Boolean valuesInAxon = rs.getObject("values_in_axon", Boolean.class);
                    if (valuesInAxon != null) {
                        JsonObject change = new JsonObject();
                        change.addProperty("operation", operation);
                        change.addProperty("fieldName", "Values in BUDG");
                        change.addProperty("oldValue", "");
                        change.addProperty("newValue", valuesInAxon ? "Yes" : "No");
                        changesArray.add(change);
                    }
                    
                    String frequencyComments = rs.getString("frequency_comments");
                    if (frequencyComments != null && !frequencyComments.trim().isEmpty()) {
                        JsonObject change = new JsonObject();
                        change.addProperty("operation", operation);
                        change.addProperty("fieldName", "Frequency Comments");
                        change.addProperty("oldValue", "");
                        change.addProperty("newValue", frequencyComments);
                        changesArray.add(change);
                    }
                    
                    String availabilityComments = rs.getString("availability_comments");
                    if (availabilityComments != null && !availabilityComments.trim().isEmpty()) {
                        JsonObject change = new JsonObject();
                        change.addProperty("operation", operation);
                        change.addProperty("fieldName", "Availability Comments");
                        change.addProperty("oldValue", "");
                        change.addProperty("newValue", availabilityComments);
                        changesArray.add(change);
                    }
                }
            }
        }
        
        return changesArray.toString();
    }
    
    /**
     * Get Attribute changes snapshot as JSON (Dataset Attributes tab)
     * Reuses logic from PendingChangesServlet.compareAttributeChanges()
     */
    private String getAttributeChangesSnapshot(Connection conn, int datasetId, int attrId) throws SQLException {
        JsonArray changesArray = new JsonArray();
        
        // Check if attribute exists in original dataset
        String checkOriginalSql = "SELECT COUNT(*) as count FROM attribute WHERE ID = ? AND Dataset_ID = ?";
        boolean isNewAttribute = true;
        try (PreparedStatement checkStmt = conn.prepareStatement(checkOriginalSql)) {
            checkStmt.setInt(1, attrId);
            checkStmt.setInt(2, datasetId);
            try (ResultSet checkRs = checkStmt.executeQuery()) {
                if (checkRs.next()) {
                    isNewAttribute = (checkRs.getInt("count") == 0);
                }
            }
        }
        
        // Get attribute details
        String sql = "SELECT a.ID, a.PrimaryName, a.Definition, " +
                     "dt.PrimaryName as data_type, a.DataLength, a.Is_Mandatory, a.Is_PrimaryKey " +
                     "FROM attribute a " +
                     "LEFT JOIN attribute_datatype dt ON a.Data_type_ID = dt.ID " +
                     "WHERE a.ID = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, attrId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    JsonObject change = new JsonObject();
                    change.addProperty("attributeName", rs.getString("PrimaryName"));
                    change.addProperty("operation", isNewAttribute ? "Inserted" : "Updated");
                    change.addProperty("fieldName", "Attribute Metadata");
                    change.addProperty("oldValue", "");
                    change.addProperty("newValue", rs.getString("PrimaryName") + " (Data Type: " + 
                                       rs.getString("data_type") + ")");
                    changesArray.add(change);
                }
            }
        }
        
        return changesArray.toString();
    }
    
    /**
     * Get relationship changes snapshot as JSON
     * Reuses EXACT logic from PendingChangesServlet.compareRelationshipChanges()
     * This ensures the snapshot matches exactly what was displayed before CR completion
     */
    private String getRelationshipChangesSnapshot(Connection conn, String facetName, int objectId, int nobjectId, String relTable) throws SQLException {
        JsonArray changesArray = new JsonArray();
        
        // Use EXACT same SQL queries as PendingChangesServlet.compareRelationshipChanges()
        String sqlById = null;
        String displayFormat = null;
        
        switch (relTable) {
            case "process_x_process":
                sqlById = "SELECT pxp.ID, pxp.sourceprocess_id, pxp.targetprocess_id, pxp.relationtype, pxp.annotations, " +
                          "sp.primaryname as source_name, tp.primaryname as target_name, " +
                          "rt.PrimaryName as relation_type_name " +
                          "FROM process_x_process pxp " +
                          "LEFT JOIN process sp ON pxp.sourceprocess_id = sp.id " +
                          "LEFT JOIN process tp ON pxp.targetprocess_id = tp.id " +
                          "LEFT JOIN process_x_process_relationtype rt ON pxp.relationtype = rt.id " +
                          "WHERE pxp.ID = ?";
                displayFormat = "{source_name} X {target_name}";
                break;
                
            case "glossary_x_glossary":
                sqlById = "SELECT gxg.ID, gxg.SourceGlossaryID, gxg.TargetGlossaryID, gxg.RelationType, " +
                          "sg.Name as source_name, tg.Name as target_name, " +
                          "rt.PrimaryName as relation_type_name " +
                          "FROM glossary_x_glossary gxg " +
                          "LEFT JOIN glossary sg ON gxg.SourceGlossaryID = sg.ID " +
                          "LEFT JOIN glossary tg ON gxg.TargetGlossaryID = tg.ID " +
                          "LEFT JOIN glossary_x_glossary_reltype rt ON gxg.RelationType = rt.ID " +
                          "WHERE gxg.ID = ?";
                displayFormat = "{source_name} X {target_name}";
                break;
                
            case "glossary_x_system":
                sqlById = "SELECT gxs.ID, gxs.GlossaryID, gxs.SystemID, gxs.Strategic_DatasetID, " +
                          "g.Name as glossary_name, s.Name as system_name, d.PrimaryName as dataset_name " +
                          "FROM glossary_x_system gxs " +
                          "LEFT JOIN glossary g ON gxs.GlossaryID = g.ID " +
                          "LEFT JOIN system s ON gxs.SystemID = s.id " +
                          "LEFT JOIN dataset d ON gxs.Strategic_DatasetID = d.ID " +
                          "WHERE gxs.ID = ?";
                displayFormat = "{glossary_name} X {system_name}";
                break;
                
            case "attribute_x_attribute":
                sqlById = "SELECT axa.ID, axa.Source_AttributeID, axa.Target_AttributeID, axa.Relation_Type, axa.Relation_Scope, " +
                          "axa.Relation_Method, axa.Sourcing_Logic, axa.Review_Status, " +
                          "sa.PrimaryName as source_name, sa.RefNumber as source_ref, " +
                          "ta.PrimaryName as target_name, ta.RefNumber as target_ref, " +
                          "rt.PrimaryName as relation_type_name, " +
                          "rs.PrimaryName as relation_scope_name, " +
                          "td.ID as target_dataset_id, td.PrimaryName as target_dataset_name, td.RefNumber as target_dataset_ref, " +
                          "s.id as system_id, s.Name as system_name, " +
                          "i.id as interface_id, i.Name as interface_name, i.Ref_number as interface_ref " +
                          "FROM attribute_x_attribute axa " +
                          "LEFT JOIN attribute sa ON axa.Source_AttributeID = sa.ID " +
                          "LEFT JOIN attribute ta ON axa.Target_AttributeID = ta.ID " +
                          "LEFT JOIN dataset td ON td.ID = ta.Dataset_ID " +
                          "LEFT JOIN system s ON s.id = td.MasterSource " +
                          "LEFT JOIN interface i ON i.id = axa.Relation_Method " +
                          "LEFT JOIN attribute_x_attribute_relationtype rt ON axa.Relation_Type = rt.ID " +
                          "LEFT JOIN attribute_x_attribute_relationscope rs ON axa.Relation_Scope = rs.ID " +
                          "WHERE axa.ID = ?";
                displayFormat = "{source_name} X {target_name}";
                break;
                
            case "dataset_mastersource":
                sqlById = "SELECT d.ID as dataset_id, d.PrimaryName as dataset_name, " +
                          "s.Name as system_name, d.MasterSource " +
                          "FROM dataset d " +
                          "LEFT JOIN system s ON d.MasterSource = s.id " +
                          "WHERE d.ID = ?";
                displayFormat = "{system_name} X {dataset_name}";
                break;
                
            default:
                return "[]";
        }
        
        // Check if relationship exists (if not, it was deleted)
        boolean relationshipExists = false;
        Map<String, Object> relationshipRow = new HashMap<>();
        
        logger.info("   📸 getRelationshipChangesSnapshot: checking if relationship {} exists in {} (objectId={}, nobjectId={})", 
            nobjectId, relTable, objectId, nobjectId);
        logger.info("   📸 SQL: {}", sqlById.replace("?", String.valueOf(nobjectId)));
        
        try (PreparedStatement checkStmt = conn.prepareStatement(sqlById)) {
            checkStmt.setInt(1, nobjectId);
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next()) {
                    relationshipExists = true;
                    ResultSetMetaData meta = rs.getMetaData();
                    int colCount = meta.getColumnCount();
                    for (int i = 1; i <= colCount; i++) {
                        relationshipRow.put(meta.getColumnLabel(i).toLowerCase(), rs.getObject(i));
                    }
                    logger.info("   📸 ✅ Found relationship {}: source={}, target={}", 
                        nobjectId, 
                        relationshipRow.get("source_name"),
                        relationshipRow.get("target_name"));
                } else {
                    logger.warn("   📸 ❌ Relationship {} NOT FOUND in {} - will show as deleted", nobjectId, relTable);
                    
                    // Debug: Check if there are ANY relationships in the table
                    String countSql = "SELECT COUNT(*) as cnt FROM " + relTable;
                    try (PreparedStatement countStmt = conn.prepareStatement(countSql);
                         ResultSet countRs = countStmt.executeQuery()) {
                        if (countRs.next()) {
                            logger.warn("   📸 ❌ Total relationships in {}: {}", relTable, countRs.getInt("cnt"));
                        }
                    }
                }
            }
        }
        
        if (!relationshipExists) {
            // Relationship was deleted - try to get display name from original relationships
            String deletedDisplayName = null;
            
            // For process_x_process, try to find the relationship using sourceprocess_id/targetprocess_id
            if ("process_x_process".equals(relTable)) {
                // The relationship might have sourceprocess_id = mainNobjectId (cloned process ID)
                // Try to find it that way
                String findSql = "SELECT pxp.ID, sp.primaryname as source_name, tp.primaryname as target_name " +
                                 "FROM process_x_process pxp " +
                                 "LEFT JOIN process sp ON pxp.sourceprocess_id = sp.id " +
                                 "LEFT JOIN process tp ON pxp.targetprocess_id = tp.id " +
                                 "WHERE pxp.ID = ?";
                try (PreparedStatement findStmt = conn.prepareStatement(findSql)) {
                    findStmt.setInt(1, nobjectId);
                    try (ResultSet findRs = findStmt.executeQuery()) {
                        if (findRs.next()) {
                            String sourceName = findRs.getString("source_name");
                            String targetName = findRs.getString("target_name");
                            if (sourceName != null && targetName != null) {
                                deletedDisplayName = sourceName + " X " + targetName;
                            }
                        }
                    }
                } catch (SQLException e) {
                    logger.debug("   📸 Could not find relationship {} in process_x_process: {}", nobjectId, e.getMessage());
                }
            }
            
            // Create deleted entry
            JsonObject change = new JsonObject();
            change.addProperty("operation", "Deleted");
            if (deletedDisplayName != null && !deletedDisplayName.isEmpty()) {
                change.addProperty("displayName", deletedDisplayName + " (deleted)");
                change.addProperty("oldValue", deletedDisplayName);
            } else {
                change.addProperty("displayName", "Relationship ID " + nobjectId + " (deleted)");
                change.addProperty("oldValue", "Relationship ID " + nobjectId);
            }
            change.addProperty("fieldName", "Relationship");
            change.addProperty("newValue", "");
            changesArray.add(change);
        } else {
            // Relationship exists - check if it's new or updated
            boolean isNewRelationship = true;
            if ("attribute_x_attribute".equals(relTable)) {
                String checkSql = "SELECT COUNT(*) as count FROM attribute_x_attribute axa " +
                                "INNER JOIN attribute sa ON axa.Source_AttributeID = sa.ID " +
                                "WHERE axa.ID = ? AND sa.Dataset_ID = ?";
                try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                    checkStmt.setInt(1, nobjectId);
                    checkStmt.setInt(2, objectId);
                    try (ResultSet checkRs = checkStmt.executeQuery()) {
                        if (checkRs.next() && checkRs.getInt("count") > 0) {
                            isNewRelationship = false;
                        }
                    }
                } catch (SQLException e) {
                    // Assume new if check fails
                }
            }
            
            String operation = isNewRelationship ? "Inserted" : "Updated";
            
            // Format relationship display name
            String relationshipDisplayName = formatRelationshipDisplayFromMap(displayFormat, relationshipRow);
            
            if ("attribute_x_attribute".equals(relTable)) {
                // For attribute_x_attribute, create separate field changes (same as PendingChangesServlet)
                String sourceAttr = getAttributeDisplayNameFromMap(relationshipRow, "source_name", "source_ref");
                String targetAttr = getAttributeDisplayNameFromMap(relationshipRow, "target_name", "target_ref");
                String relationshipDisplay = (sourceAttr != null ? sourceAttr : "") + " X " + (targetAttr != null ? targetAttr : "");
                
                // Add individual field changes
                JsonObject attrChange = new JsonObject();
                attrChange.addProperty("displayName", relationshipDisplay);
                attrChange.addProperty("operation", operation);
                attrChange.addProperty("fieldName", "Attribute");
                attrChange.addProperty("oldValue", "");
                attrChange.addProperty("newValue", sourceAttr != null ? sourceAttr : "");
                changesArray.add(attrChange);
                
                JsonObject relatedAttrChange = new JsonObject();
                relatedAttrChange.addProperty("displayName", relationshipDisplay);
                relatedAttrChange.addProperty("operation", operation);
                relatedAttrChange.addProperty("fieldName", "Related Attributes");
                relatedAttrChange.addProperty("oldValue", "");
                relatedAttrChange.addProperty("newValue", targetAttr != null ? targetAttr : "");
                changesArray.add(relatedAttrChange);
                
                // Add other fields if available
                String relationTypeName = getStringValueFromMap(relationshipRow, "relation_type_name");
                if (relationTypeName != null && !relationTypeName.isEmpty()) {
                    JsonObject typeChange = new JsonObject();
                    typeChange.addProperty("displayName", relationshipDisplay);
                    typeChange.addProperty("operation", operation);
                    typeChange.addProperty("fieldName", "Type");
                    typeChange.addProperty("oldValue", "");
                    typeChange.addProperty("newValue", relationTypeName);
                    changesArray.add(typeChange);
                }
                
                String relationScopeName = getStringValueFromMap(relationshipRow, "relation_scope_name");
                if (relationScopeName != null && !relationScopeName.isEmpty()) {
                    JsonObject scopeChange = new JsonObject();
                    scopeChange.addProperty("displayName", relationshipDisplay);
                    scopeChange.addProperty("operation", operation);
                    scopeChange.addProperty("fieldName", "Scope of Data");
                    scopeChange.addProperty("oldValue", "");
                    scopeChange.addProperty("newValue", relationScopeName);
                    changesArray.add(scopeChange);
                }
                
                String systemName = getStringValueFromMap(relationshipRow, "system_name");
                if (systemName != null && !systemName.isEmpty()) {
                    JsonObject systemChange = new JsonObject();
                    systemChange.addProperty("displayName", relationshipDisplay);
                    systemChange.addProperty("operation", operation);
                    systemChange.addProperty("fieldName", "Source System");
                    systemChange.addProperty("oldValue", "");
                    systemChange.addProperty("newValue", systemName);
                    changesArray.add(systemChange);
                }
                
                String datasetDisplay = getDatasetDisplayNameFromMap(relationshipRow, "target_dataset_ref", "target_dataset_name");
                if (datasetDisplay != null && !datasetDisplay.isEmpty()) {
                    JsonObject datasetChange = new JsonObject();
                    datasetChange.addProperty("displayName", relationshipDisplay);
                    datasetChange.addProperty("operation", operation);
                    datasetChange.addProperty("fieldName", "Related Data set");
                    datasetChange.addProperty("oldValue", "");
                    datasetChange.addProperty("newValue", datasetDisplay);
                    changesArray.add(datasetChange);
                }
            } else {
                // For other relationship types, use simple format
                JsonObject change = new JsonObject();
                change.addProperty("operation", operation);
                change.addProperty("displayName", relationshipDisplayName);
                change.addProperty("fieldName", "Relationship");
                change.addProperty("oldValue", "");
                change.addProperty("newValue", relationshipDisplayName);
                changesArray.add(change);
            }
        }
        
        return changesArray.toString();
    }
    
    /**
     * Get impact relationships changes snapshot as JSON
     * Reuses EXACT logic from PendingChangesServlet.compareImpactRelationships()
     */
    private String getImpactChangesSnapshot(Connection conn, String facetName, int objectId, int nobjectId, String relationType) throws SQLException {
        JsonArray changesArray = new JsonArray();
        
        // Get relationship table
        String relationTable = getRelationshipTableForImpact(facetName, relationType);
        if (relationTable == null) {
            return "[]";
        }
        
        String relatedTypeName = extractRelatedTypeName(relationType);
        
        // Get relationships from original and cloned objects (same as compareImpactRelationships)
        List<Map<String, String>> originalRels = getImpactRelationshipsForSnapshot(conn, facetName, relationType, objectId);
        List<Map<String, String>> clonedRels = getImpactRelationshipsForSnapshot(conn, facetName, relationType, nobjectId);
        
        // Build maps for comparison (same logic as compareImpactRelationships)
        Map<String, Map<String, String>> originalMap = new HashMap<>();
        for (Map<String, String> rel : originalRels) {
            String key = rel.get("relatedId");
            if (key != null) originalMap.put(key, rel);
        }
        
        Map<String, Map<String, String>> clonedMap = new HashMap<>();
        for (Map<String, String> rel : clonedRels) {
            String key = rel.get("relatedId");
            if (key != null) clonedMap.put(key, rel);
        }
        
        // Pair removed and added as "Updated" entries
        List<Map<String, String>> removedRels = new ArrayList<>();
        List<Map<String, String>> addedRels = new ArrayList<>();
        
        // Find added relationships
        for (String key : clonedMap.keySet()) {
            if (!originalMap.containsKey(key)) {
                addedRels.add(clonedMap.get(key));
            }
        }
        
        // Find removed relationships
        for (String key : originalMap.keySet()) {
            if (!clonedMap.containsKey(key)) {
                removedRels.add(originalMap.get(key));
            }
        }
        
        // Pair removed and added as "Updated"
        int pairCount = Math.min(removedRels.size(), addedRels.size());
        for (int i = 0; i < pairCount; i++) {
            Map<String, String> oldRel = removedRels.get(i);
            Map<String, String> newRel = addedRels.get(i);
            
            JsonObject change = new JsonObject();
            change.addProperty("operation", "Updated");
            change.addProperty("relatedType", relatedTypeName);
            change.addProperty("relatedName", newRel.get("relatedName"));
            change.addProperty("fieldName", relatedTypeName);
            change.addProperty("oldValue", oldRel.get("relatedName"));
            change.addProperty("newValue", newRel.get("relatedName"));
            changesArray.add(change);
            
            // Also add relationship type change if both have types
            String oldRelType = oldRel.get("relationTypeName");
            String newRelType = newRel.get("relationTypeName");
            if (oldRelType != null || newRelType != null) {
                JsonObject rtChange = new JsonObject();
                rtChange.addProperty("operation", "Updated");
                rtChange.addProperty("relatedType", relatedTypeName);
                rtChange.addProperty("relatedName", newRel.get("relatedName"));
                rtChange.addProperty("fieldName", "Relationship Type");
                rtChange.addProperty("oldValue", oldRelType != null ? oldRelType : "");
                rtChange.addProperty("newValue", newRelType != null ? newRelType : "");
                changesArray.add(rtChange);
            }
        }
        
        // Handle remaining removals (pure deletions)
        for (int i = pairCount; i < removedRels.size(); i++) {
            Map<String, String> oldRel = removedRels.get(i);
            JsonObject change = new JsonObject();
            change.addProperty("operation", "Deleted");
            change.addProperty("relatedType", relatedTypeName);
            change.addProperty("relatedName", oldRel.get("relatedName"));
            change.addProperty("fieldName", relatedTypeName);
            change.addProperty("oldValue", oldRel.get("relatedName"));
            change.addProperty("newValue", "");
            changesArray.add(change);
        }
        
        // Handle remaining additions (pure insertions)
        for (int i = pairCount; i < addedRels.size(); i++) {
            Map<String, String> newRel = addedRels.get(i);
            JsonObject change = new JsonObject();
            change.addProperty("operation", "Inserted");
            change.addProperty("relatedType", relatedTypeName);
            change.addProperty("relatedName", newRel.get("relatedName"));
            change.addProperty("fieldName", relatedTypeName);
            change.addProperty("oldValue", "");
            change.addProperty("newValue", newRel.get("relatedName"));
            changesArray.add(change);
            
            // Also add relationship type if available
            if (newRel.get("relationTypeName") != null) {
                JsonObject rtChange = new JsonObject();
                rtChange.addProperty("operation", "Inserted");
                rtChange.addProperty("relatedType", relatedTypeName);
                rtChange.addProperty("relatedName", newRel.get("relatedName"));
                rtChange.addProperty("fieldName", "Relationship Type");
                rtChange.addProperty("oldValue", "");
                rtChange.addProperty("newValue", newRel.get("relationTypeName"));
                changesArray.add(rtChange);
            }
        }
        
        // Find modified relationships (same related object but different relationship type)
        for (String key : clonedMap.keySet()) {
            if (originalMap.containsKey(key)) {
                Map<String, String> oldRel = originalMap.get(key);
                Map<String, String> newRel = clonedMap.get(key);
                
                String oldRelType = oldRel.get("relationTypeName");
                String newRelType = newRel.get("relationTypeName");
                
                if (oldRelType != null && newRelType != null && !oldRelType.equals(newRelType)) {
                    JsonObject change = new JsonObject();
                    change.addProperty("operation", "Updated");
                    change.addProperty("relatedType", relatedTypeName);
                    change.addProperty("relatedName", newRel.get("relatedName"));
                    change.addProperty("fieldName", "Relationship Type");
                    change.addProperty("oldValue", oldRelType);
                    change.addProperty("newValue", newRelType);
                    changesArray.add(change);
                }
            }
        }
        
        return changesArray.toString();
    }
    
    /**
     * Get stakeholder changes snapshot as JSON
     * Reuses EXACT logic from PendingChangesServlet.compareStakeholders()
     */
    private String getStakeholderChangesSnapshot(Connection conn, String facetName, int objectId, int nobjectId) throws SQLException {
        JsonArray changesArray = new JsonArray();
        
        // Get stakeholders from original and cloned objects (same as compareStakeholders)
        List<Map<String, String>> originalStakeholders = getStakeholdersForSnapshot(conn, facetName, objectId);
        List<Map<String, String>> clonedStakeholders = getStakeholdersForSnapshot(conn, facetName, nobjectId);
        
        // Build lists of stakeholder display strings (same format as compareStakeholders)
        List<String> originalList = new ArrayList<>();
        for (Map<String, String> sh : originalStakeholders) {
            originalList.add(sh.get("personName") + " (" + sh.get("roleName") + ")");
        }
        
        List<String> clonedList = new ArrayList<>();
        for (Map<String, String> sh : clonedStakeholders) {
            clonedList.add(sh.get("personName") + " (" + sh.get("roleName") + ")");
        }
        
        // Find removed stakeholders
        for (String s : originalList) {
            if (!clonedList.contains(s)) {
                JsonObject change = new JsonObject();
                change.addProperty("operation", "Deleted");
                change.addProperty("fieldName", "Stakeholder");
                change.addProperty("oldValue", s);
                change.addProperty("newValue", "");
                changesArray.add(change);
            }
        }
        
        // Find added stakeholders
        for (String s : clonedList) {
            if (!originalList.contains(s)) {
                JsonObject change = new JsonObject();
                change.addProperty("operation", "Inserted");
                change.addProperty("fieldName", "Stakeholder");
                change.addProperty("oldValue", "");
                change.addProperty("newValue", s);
                changesArray.add(change);
            }
        }
        
        return changesArray.toString();
    }
    
    /**
     * Get data content changes snapshot as JSON (System)
     * Uses same logic as relationship changes for glossary_x_system
     */
    private String getDataContentChangesSnapshot(Connection conn, String facetName, int objectId, int nobjectId) throws SQLException {
        // Data content is essentially a glossary_x_system relationship from System perspective
        // Use the same logic as getRelationshipChangesSnapshot with glossary_x_system
        return getRelationshipChangesSnapshot(conn, facetName, objectId, nobjectId, "glossary_x_system");
    }
    
    /**
     * Get columns to compare based on facet
     */
    private String[] getColumnsToCompare(String facetName) {
        switch (facetName.toLowerCase()) {
            case "glossary":
                return new String[]{"Name", "Definition", "Ref_Number", "Format_Type", "LDM_Reference", 
                                   "Business_Logic", "Examples", "Status", "Lifecycle", "Is_Public", 
                                   "Type", "Security_Classification", "KDE", "Confidentiality_Rating", 
                                   "Integrity_Rating", "Availability_Rating"};
            case "dataset":
            case "data set":
                return new String[]{"PrimaryName", "Description", "RefNumber", "DatasetType", "Status", 
                                   "Lifecycle", "IsPublic", "AccessControlType"};
            case "system":
                return new String[]{"Name", "Description", "AssetID", "Status", "Lifecycle", "IsPublic"};
            case "process":
                return new String[]{"PrimaryName", "Description", "refnumber", "Status", "LifecycleStatus", 
                                   "IsPublic", "Type", "DurationType", "Duration"};
            default:
                return new String[]{};
        }
    }
    
    /**
     * Get changes table name for facet
     */
    private String getChangesTableName(String facetName) {
        if (facetName == null) return null;
        switch (facetName.toLowerCase()) {
            case "glossary": return "glossary_changes";
            case "dataset":
            case "data set": return "dataset_changes";
            case "system": return "system_changes";
            case "process": return "process_changes";
            default: return null;
        }
    }
    
    /**
     * Format relationship display from map (same as PendingChangesServlet.formatRelationshipDisplay)
     */
    private String formatRelationshipDisplayFromMap(String format, Map<String, Object> rel) {
        String result = format;
        for (Map.Entry<String, Object> entry : rel.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            result = result.replace(placeholder, value);
        }
        return result;
    }
    
    /**
     * Get attribute display name from map (same as PendingChangesServlet.getAttributeDisplayName)
     */
    private String getAttributeDisplayNameFromMap(Map<String, Object> row, String nameKey, String refKey) {
        String name = getStringValueFromMap(row, nameKey);
        String ref = getStringValueFromMap(row, refKey);
        if (ref != null && !ref.isEmpty()) {
            return ref + ": " + name;
        }
        return name != null ? name : "";
    }
    
    /**
     * Get dataset display name from map (same as PendingChangesServlet.getDatasetDisplayName)
     */
    private String getDatasetDisplayNameFromMap(Map<String, Object> row, String refKey, String nameKey) {
        String ref = getStringValueFromMap(row, refKey);
        String name = getStringValueFromMap(row, nameKey);
        if (ref != null && !ref.isEmpty() && name != null && !name.isEmpty()) {
            return ref + ": " + name;
        }
        return name != null ? name : "";
    }
    
    /**
     * Get string value from map (case-insensitive, same as PendingChangesServlet.getStringValue)
     */
    private String getStringValueFromMap(Map<String, Object> row, String key) {
        Object value = row.get(key.toLowerCase());
        if (value == null) {
            value = row.get(key);
        }
        return value != null ? value.toString() : "";
    }
    
    /**
     * Get relationship table for impact relationships (same as PendingChangesServlet.getRelationshipTable)
     */
    private String getRelationshipTableForImpact(String facetName, String relationType) {
        String prefix = facetName.toLowerCase();
        String lower = relationType.toLowerCase();
        
        // Dataset impact relationships
        if ("dataset".equals(prefix)) {
            if (lower.contains("product")) return "product_x_dataset";
            if (lower.contains("client")) return "client_x_dataset";
            if (lower.contains("legal")) return "dataset_x_legal";
        }
        
        // Glossary impact relationships
        if ("glossary".equals(prefix)) {
            if (lower.contains("product")) return "product_x_glossary";
            if (lower.contains("client")) return "client_x_glossary";
            if (lower.contains("system")) return "glossary_x_system";
        }
        
        // System impact relationships
        if ("system".equals(prefix)) {
            if (lower.contains("product")) return "product_x_system";
            if (lower.contains("client")) return "client_x_system";
            if (lower.contains("legal")) return "system_x_legal";
            if (lower.contains("process")) return "system_x_process";
        }
        
        // Process impact relationships
        if ("process".equals(prefix)) {
            if (lower.contains("system")) return "process_x_system";
            if (lower.contains("product")) return "product_x_process";
            if (lower.contains("client")) return "client_x_process";
        }
        
        return null;
    }
    
    /**
     * Extract related type name from relationType (same as PendingChangesServlet.extractRelatedTypeName)
     */
    private String extractRelatedTypeName(String relationType) {
        if (relationType == null) return "Unknown";
        String lower = relationType.toLowerCase();
        
        // Check for more specific types first
        if (lower.contains("product")) return "Product";
        if (lower.contains("client")) return "Client";
        if (lower.contains("legal") || lower.contains("regulation")) return "Legal";
        if (lower.contains("system")) return "System";
        if (lower.contains("glossary")) return "Glossary";
        if (lower.contains("dataset")) return "Data Set";
        if (lower.contains("project")) return "Project";
        if (lower.contains("policy")) return "Policy";
        if (lower.contains("interface")) return "Interface";
        if (lower.contains("attribute")) return "Attribute";
        if (lower.contains("process")) return "Process";
        
        return "Unknown";
    }
    
    /**
     * Get impact relationships for snapshot (same as PendingChangesServlet.getImpactRelationships)
     */
    private List<Map<String, String>> getImpactRelationshipsForSnapshot(Connection conn, String facetName, String relationType, int objectId) throws SQLException {
        List<Map<String, String>> relationships = new ArrayList<>();
        
        String sql = buildImpactRelationshipQuery(facetName, relationType);
        if (sql == null) {
            return relationships;
        }
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, objectId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> rel = new HashMap<>();
                    
                    // Get related object ID
                    String relatedId = null;
                    try { 
                        relatedId = String.valueOf(rs.getInt("relatedId")); 
                    } catch (SQLException e) { 
                        // Try alternative column names
                        try { relatedId = String.valueOf(rs.getInt("Product_ID")); } catch (SQLException e2) {}
                        if (relatedId == null) try { relatedId = String.valueOf(rs.getInt("productid")); } catch (SQLException e2) {}
                        if (relatedId == null) try { relatedId = String.valueOf(rs.getInt("Client_ID")); } catch (SQLException e2) {}
                        if (relatedId == null) try { relatedId = String.valueOf(rs.getInt("Legal_ID")); } catch (SQLException e2) {}
                        if (relatedId == null) try { relatedId = String.valueOf(rs.getInt("System_ID")); } catch (SQLException e2) {}
                        if (relatedId == null) try { relatedId = String.valueOf(rs.getInt("system_id")); } catch (SQLException e2) {}
                        if (relatedId == null) try { relatedId = String.valueOf(rs.getInt("Process_ID")); } catch (SQLException e2) {}
                        if (relatedId == null) try { relatedId = String.valueOf(rs.getInt("process_id")); } catch (SQLException e2) {}
                        if (relatedId == null) try { relatedId = String.valueOf(rs.getInt("Glossary_ID")); } catch (SQLException e2) {}
                    }
                    rel.put("relatedId", relatedId);
                    
                    // Get related object name
                    String relatedName = null;
                    try { relatedName = rs.getString("relatedName"); } catch (SQLException e) {}
                    rel.put("relatedName", relatedName != null ? relatedName : "Unknown");
                    
                    // Get relationship type name
                    String relationTypeName = null;
                    try { relationTypeName = rs.getString("relationTypeName"); } catch (SQLException e) {}
                    rel.put("relationTypeName", relationTypeName);
                    
                    relationships.add(rel);
                }
            }
        }
        
        return relationships;
    }
    
    /**
     * Build SQL query for impact relationships (same as PendingChangesServlet.buildImpactRelationshipQuery)
     */
    private String buildImpactRelationshipQuery(String facetName, String relationType) {
        String lower = relationType.toLowerCase();
        String prefix = facetName.toLowerCase();
        
        // Dataset impact relationships
        if ("dataset".equals(prefix)) {
            if (lower.contains("product")) {
                return "SELECT pxd.Product_ID as relatedId, p.primaryname as relatedName, rt.PrimaryName as relationTypeName " +
                       "FROM product_x_dataset pxd " +
                       "LEFT JOIN product p ON pxd.Product_ID = p.id " +
                       "LEFT JOIN product_x_dataset_relationtype rt ON pxd.Product_Dataset_Relation_Type = rt.ID " +
                       "WHERE pxd.Dataset_ID = ?";
            } else if (lower.contains("client")) {
                return "SELECT cxd.Client_ID as relatedId, c.PrimaryName as relatedName, rt.PrimaryName as relationTypeName " +
                       "FROM client_x_dataset cxd " +
                       "LEFT JOIN client c ON cxd.Client_ID = c.ID " +
                       "LEFT JOIN client_x_dataset_relationtype rt ON cxd.RelationType = rt.ID " +
                       "WHERE cxd.Dataset_ID = ?";
            } else if (lower.contains("legal")) {
                return "SELECT dxl.Legal_ID as relatedId, l.ShortName as relatedName, rt.PrimaryName as relationTypeName " +
                       "FROM dataset_x_legal dxl " +
                       "LEFT JOIN legal l ON dxl.Legal_ID = l.ID " +
                       "LEFT JOIN dataset_x_legal_relationtype rt ON dxl.RelationType = rt.ID " +
                       "WHERE dxl.Dataset_ID = ?";
            }
        }
        
        // Glossary impact relationships
        if ("glossary".equals(prefix)) {
            if (lower.contains("product")) {
                return "SELECT pxg.productid as relatedId, p.PrimaryName as relatedName, rt.primaryname as relationTypeName " +
                       "FROM product_x_glossary pxg " +
                       "LEFT JOIN product p ON pxg.productid = p.id " +
                       "LEFT JOIN product_x_glossary_relationtype rt ON pxg.relationtype = rt.id " +
                       "WHERE pxg.glossaryid = ?";
            } else if (lower.contains("client")) {
                return "SELECT cxg.Client_ID as relatedId, c.PrimaryName as relatedName, rt.PrimaryName as relationTypeName " +
                       "FROM client_x_glossary cxg " +
                       "LEFT JOIN client c ON cxg.Client_ID = c.ID " +
                       "LEFT JOIN client_x_glossary_relationtype rt ON cxg.RelationType = rt.ID " +
                       "WHERE cxg.Glossary_ID = ?";
            } else if (lower.contains("system")) {
                return "SELECT gxs.System_ID as relatedId, s.Name as relatedName, rt.PrimaryName as relationTypeName " +
                       "FROM glossary_x_system gxs " +
                       "LEFT JOIN system s ON gxs.System_ID = s.ID " +
                       "LEFT JOIN glossary_x_system_relationtype rt ON gxs.Relation_TypeID = rt.ID " +
                       "WHERE gxs.Glossary_ID = ?";
            }
        }
        
        // System impact relationships
        if ("system".equals(prefix)) {
            if (lower.contains("product")) {
                return "SELECT pxs.Product_ID as relatedId, p.primaryname as relatedName, prt.PrimaryName as relationTypeName " +
                       "FROM product_x_system pxs " +
                       "LEFT JOIN product p ON pxs.Product_ID = p.id " +
                       "LEFT JOIN product_x_system_relationtype prt ON pxs.Product_System_Relation_Type = prt.ID " +
                       "WHERE pxs.System_ID = ?";
            } else if (lower.contains("client")) {
                return "SELECT cxs.Client_ID as relatedId, c.PrimaryName as relatedName, rt.PrimaryName as relationTypeName " +
                       "FROM client_x_system cxs " +
                       "LEFT JOIN client c ON cxs.Client_ID = c.ID " +
                       "LEFT JOIN client_x_system_relationtype rt ON cxs.RelationType = rt.ID " +
                       "WHERE cxs.System_ID = ?";
            } else if (lower.contains("legal")) {
                return "SELECT sxl.Legal_ID as relatedId, l.ShortName as relatedName, rt.PrimaryName as relationTypeName " +
                       "FROM system_x_legal sxl " +
                       "LEFT JOIN legal l ON sxl.Legal_ID = l.ID " +
                       "LEFT JOIN system_x_legal_relationtype rt ON sxl.RelationType = rt.ID " +
                       "WHERE sxl.System_ID = ?";
            } else if (lower.contains("process")) {
                return "SELECT sxp.Process_ID as relatedId, p.PrimaryName as relatedName, rt.PrimaryName as relationTypeName " +
                       "FROM system_x_process sxp " +
                       "LEFT JOIN process p ON sxp.Process_ID = p.ID " +
                       "LEFT JOIN system_x_process_relationtype rt ON sxp.RelationType = rt.ID " +
                       "WHERE sxp.System_ID = ?";
            }
        }
        
        // Process impact relationships
        if ("process".equals(prefix)) {
            if (lower.contains("system")) {
                return "SELECT pxs.System_ID as relatedId, s.Name as relatedName, rt.PrimaryName as relationTypeName " +
                       "FROM process_x_system pxs " +
                       "LEFT JOIN system s ON pxs.System_ID = s.ID " +
                       "LEFT JOIN process_x_system_relationtype rt ON pxs.RelationType = rt.ID " +
                       "WHERE pxs.Process_ID = ?";
            } else if (lower.contains("product")) {
                return "SELECT pxp.Product_ID as relatedId, p.primaryname as relatedName, rt.PrimaryName as relationTypeName " +
                       "FROM product_x_process pxp " +
                       "LEFT JOIN product p ON pxp.Product_ID = p.id " +
                       "LEFT JOIN product_x_process_relationtype rt ON pxp.RelationType = rt.ID " +
                       "WHERE pxp.Process_ID = ?";
            } else if (lower.contains("client")) {
                return "SELECT cxp.Client_ID as relatedId, c.PrimaryName as relatedName, rt.PrimaryName as relationTypeName " +
                       "FROM client_x_process cxp " +
                       "LEFT JOIN client c ON cxp.Client_ID = c.ID " +
                       "LEFT JOIN client_x_process_relationtype rt ON cxp.RelationType = rt.ID " +
                       "WHERE cxp.Process_ID = ?";
            }
        }
        
        return null;
    }
    
    /**
     * Get stakeholders for snapshot (same as PendingChangesServlet.getStakeholders)
     */
    private List<Map<String, String>> getStakeholdersForSnapshot(Connection conn, String facetName, int objectId) throws SQLException {
        List<Map<String, String>> stakeholders = new ArrayList<>();
        
        String sql;
        
        if ("dataset".equalsIgnoreCase(facetName)) {
            sql = "SELECT oxp.ipid as personId, CONCAT(p.First_Name, ' ', p.Last_Name) as personName, r.PrimaryName as roleName " +
                  "FROM dataset_x_objectxpeople dx " +
                  "JOIN object_x_people oxp ON dx.Object_x_ipid = oxp.ID " +
                  "JOIN object_role r ON oxp.roleID = r.ID " +
                  "JOIN people p ON oxp.ipid = p.ID " +
                  "WHERE dx.Dataset_ID = ?";
        } else if ("glossary".equalsIgnoreCase(facetName)) {
            sql = "SELECT oxp.ipid as personId, CONCAT(p.First_Name, ' ', p.Last_Name) as personName, r.PrimaryName as roleName " +
                  "FROM glossary_x_objectxpeople gx " +
                  "JOIN object_x_people oxp ON gx.Object_x_ipid = oxp.ID " +
                  "JOIN object_role r ON oxp.roleID = r.ID " +
                  "JOIN people p ON oxp.ipid = p.ID " +
                  "WHERE gx.GlossaryID = ?";
        } else if ("system".equalsIgnoreCase(facetName)) {
            sql = "SELECT oxp.ipid as personId, CONCAT(p.First_Name, ' ', p.Last_Name) as personName, r.PrimaryName as roleName " +
                  "FROM system_x_objectxpeople sx " +
                  "JOIN object_x_people oxp ON sx.Object_x_ipid = oxp.ID " +
                  "JOIN object_role r ON oxp.roleID = r.ID " +
                  "JOIN people p ON oxp.ipid = p.ID " +
                  "WHERE sx.SystemID = ?";
        } else if ("process".equalsIgnoreCase(facetName)) {
            sql = "SELECT oxp.ipid as personId, CONCAT(p.First_Name, ' ', p.Last_Name) as personName, r.PrimaryName as roleName " +
                  "FROM process_x_objectxpeople px " +
                  "JOIN object_x_people oxp ON px.object_x_ip = oxp.id " +
                  "JOIN object_role r ON oxp.roleID = r.ID " +
                  "JOIN people p ON oxp.ipid = p.ID " +
                  "WHERE px.process_id = ?";
        } else {
            return stakeholders;
        }
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, objectId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> sh = new HashMap<>();
                    sh.put("personId", String.valueOf(rs.getInt("personId")));
                    sh.put("personName", rs.getString("personName"));
                    sh.put("roleName", rs.getString("roleName"));
                    stakeholders.add(sh);
                }
            }
        }
        
        return stakeholders;
    }
    
    /**
     * Get foreign key column for facet
     */
    private String getForeignKeyColumn(String facetName) {
        switch (facetName.toLowerCase()) {
            case "glossary": return "GlossaryID";
            case "dataset": return "Dataset_ID";
            case "system": return "SystemID";
            case "process": return "process_id";
            default: return "ID";
        }
    }
}

