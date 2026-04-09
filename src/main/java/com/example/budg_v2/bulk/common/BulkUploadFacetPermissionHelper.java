package com.example.budg_v2.bulk.common;

import com.example.budg_v2.service.PermissionService;
import com.example.budg_v2.service.SegmentAccessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;

/**
 * Helper for bulk upload facet/module permission checks.
 * Web Users may only bulk upload for Segments + Facets they have permission on;
 * Super Admin and Admin bypass facet checks (segment is validated elsewhere).
 */
public final class BulkUploadFacetPermissionHelper {

    private static final Logger logger = LoggerFactory.getLogger(BulkUploadFacetPermissionHelper.class);
    private static final PermissionService permissionService = new PermissionService();

    private BulkUploadFacetPermissionHelper() {}

    /**
     * Returns true if the user is allowed to perform bulk upload (create/update) for the given module/facet.
     * Super Admin and Admin: always true (facet not restricted for them).
     * Web User: true only if they have at least Create or Edit permission for the module.
     *
     * @param userId    authenticated user id
     * @param moduleName module/facet name (e.g. "Process", "Policy", "Dataset")
     * @return true if bulk upload for this facet is allowed
     */
    public static boolean canBulkUploadForModule(int userId, String moduleName) {
        if (userId <= 0 || moduleName == null || moduleName.isBlank()) {
            return false;
        }
        try {
            if (SegmentAccessService.isSuperAdmin(userId)) {
                return true;
            }
            if (permissionService.isAdminOrSuperAdmin(userId)) {
                return true;
            }
            // Web User: must have Create or Edit permission for this module
            return permissionService.canCreate(userId, moduleName) || permissionService.canEdit(userId, moduleName);
        } catch (SQLException e) {
            logger.error("Error checking bulk upload permission for user {} module {}: {}", userId, moduleName, e.getMessage());
            return false;
        }
    }
}
