package com.example.budg_v2.service;

import com.example.budg_v2.bulk.objects.BulkUploadBroadcaster;
import com.example.budg_v2.dao.*;
import com.example.budg_v2.database.DatabaseConnection;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Persists migrated role / stakeholder rows produced by the Python role processor.
 */
public class RoleAssignmentBulkService {

    private static final Logger logger = LoggerFactory.getLogger(RoleAssignmentBulkService.class);

    private final PolicyDAO policyDAO = new PolicyDAO();
    private final ProcessDAO processDAO = new ProcessDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final ProjectDAO projectDAO = new ProjectDAO();
    private final SystemDAO systemDAO = new SystemDAO();
    private final ClientDAO clientDAO = new ClientDAO();
    private final DatasetDAO datasetDAO = new DatasetDAO();
    private final GlossaryDAO glossaryDAO = new GlossaryDAO();
    private final AttributeDAO attributeDAO = new AttributeDAO();
    private final InterfaceDAO interfaceDAO = new InterfaceDAO();
    private final LegalDAO legalDAO = new LegalDAO();
    private final CapabilityDAO capabilityDAO = new CapabilityDAO();
    private final BusinessAreaDAO businessAreaDAO = new BusinessAreaDAO();
    private final CommitteeDAO committeeDAO = new CommitteeDAO();
    private final RegulationDAO regulationDAO = new RegulationDAO();
    private final JobDAO jobDAO = new JobDAO();

    public void processRoleAssignments(String roleLabel, int jobId, JsonArray validatedData, int userId,
            String errorHandling) throws SQLException {
        if (validatedData == null || validatedData.size() == 0) {
            jobDAO.updateJobStatus(jobId, "Completed", true);
            jobDAO.updateJobProgress(jobId, "Completed", "No role rows to process");
            return;
        }

        String kind = roleLabel == null ? "" : roleLabel.trim().toLowerCase(Locale.ROOT);
        jobDAO.updateJobStatus(jobId, "Processing", false);
        jobDAO.createJobProgress(jobId, 20, "Processing", "Applying role assignments...");

        int ok = 0;
        int failed = 0;

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            for (int i = 0; i < validatedData.size(); i++) {
                JsonObject row = validatedData.get(i).getAsJsonObject();
                try {
                    applyOne(conn, kind, row, userId);
                    ok++;
                } catch (Exception e) {
                    logger.error("Role row failed: {}", e.getMessage(), e);
                    failed++;
                    if ("STOP_ON_ERROR".equals(errorHandling)) {
                        throw e;
                    }
                }
            }
            conn.commit();
        }

        jobDAO.updateJobStatus(jobId, "Completed", true);
        jobDAO.createJobProgress(jobId, 100, "Completed",
                String.format("Roles: %d applied, %d failed", ok, failed));
        BulkUploadBroadcaster.getInstance().broadcast(jobId, "Completed", 100,
                "Role import finished", ok, 0, 0, failed);
    }

    private void applyOne(Connection conn, String roleKind, JsonObject row, int userId) throws SQLException {
        Integer personId = getInt(row, "Person_ID");
        Integer roleId = getInt(row, "Role_ID");
        if (personId == null || roleId == null) {
            throw new SQLException("Person_ID and Role_ID are required");
        }

        Integer objectId = null;
        String objectKeyUsed = null;
        for (String k : row.keySet()) {
            if ("Person_ID".equalsIgnoreCase(k) || "Role_ID".equalsIgnoreCase(k)
                    || "operation".equalsIgnoreCase(k) || "row_number".equalsIgnoreCase(k)
                    || "rowNumber".equalsIgnoreCase(k)) {
                continue;
            }
            if (k.endsWith("_ID") || k.endsWith("Id")) {
                Integer v = getInt(row, k);
                if (v != null) {
                    objectId = v;
                    objectKeyUsed = k;
                    break;
                }
            }
        }
        if (objectId == null) {
            throw new SQLException("No object id column (e.g. Policy_ID) in validated row");
        }

        Map<String, Object> stakeholder = new HashMap<>();
        stakeholder.put("userId", personId);
        stakeholder.put("roleId", roleId);

        if (roleKind.contains("policy")) {
            int oxp = policyDAO.createObjectXPeople(conn, stakeholder, userId);
            policyDAO.linkStakeholderToPolicy(conn, objectId, oxp, userId);
        } else if (roleKind.contains("process")) {
            int oxp = processDAO.createObjectXPeople(conn, stakeholder, userId);
            processDAO.linkStakeholderToProcess(conn, objectId, oxp, userId);
        } else if (roleKind.contains("product")) {
            int oxp = productDAO.createObjectXPeople(conn, stakeholder, userId);
            productDAO.linkStakeholderToProduct(conn, objectId, oxp, userId);
        } else if (roleKind.contains("project")) {
            int oxp = projectDAO.createObjectXPeople(conn, stakeholder, userId);
            projectDAO.linkStakeholderToProject(conn, objectId, oxp);
        } else if (roleKind.contains("system") && !roleKind.contains("interface")) {
            int oxp = systemDAO.createObjectXPeople(conn, stakeholder, userId);
            systemDAO.linkStakeholderToSystem(conn, objectId, oxp);
        } else if (roleKind.contains("client")) {
            int oxp = clientDAO.createObjectXPeople(conn, stakeholder, userId);
            clientDAO.linkStakeholderToClient(conn, objectId, oxp, userId);
        } else if (roleKind.contains("data set")) {
            int oxp = datasetDAO.createObjectXPeople(conn, stakeholder, userId);
            datasetDAO.linkStakeholderToDataset(conn, objectId, oxp, userId);
        } else if (roleKind.contains("glossary")) {
            int oxp = glossaryDAO.createObjectXPeople(conn, stakeholder, userId);
            glossaryDAO.linkStakeholderToGlossary(conn, objectId, oxp, userId);
        } else if (roleKind.contains("attribute")) {
            int oxp = attributeDAO.createObjectXPeople(conn, stakeholder, userId);
            attributeDAO.linkStakeholderToAttribute(conn, objectId, oxp, userId);
        } else if (roleKind.contains("interface")) {
            int oxp = interfaceDAO.createObjectXPeople(conn, stakeholder, userId);
            interfaceDAO.linkStakeholderToInterface(conn, objectId, oxp);
        } else if (roleKind.contains("legal")) {
            int oxp = legalDAO.createObjectXPeople(conn, stakeholder, userId);
            legalDAO.linkStakeholderToLegal(conn, objectId, oxp, userId);
        } else if (roleKind.contains("attribute")) {
            int oxp = attributeDAO.createObjectXPeople(conn, stakeholder, userId);
            attributeDAO.linkStakeholderToAttribute(conn, objectId, oxp, userId);
        } else if (roleKind.contains("capability")) {
            int oxp = capabilityDAO.createObjectXPeople(conn, stakeholder, userId);
            capabilityDAO.linkStakeholderToCapability(conn, objectId, oxp, userId);
        } else if (roleKind.contains("business area")) {
            int oxp = businessAreaDAO.createObjectXPeople(conn, stakeholder, userId);
            businessAreaDAO.linkStakeholderToBusinessArea(conn, objectId, oxp, userId);
        } else if (roleKind.contains("committee")) {
            int oxp = committeeDAO.createObjectXPeople(conn, stakeholder, userId);
            committeeDAO.linkStakeholderToCommittee(conn, objectId, oxp, userId);
        } else if (roleKind.contains("regulation")) {
            int oxp = regulationDAO.createObjectXPeople(conn, stakeholder, userId);
            regulationDAO.linkStakeholderToRegulation(conn, objectId, oxp);
        } else {
            throw new SQLException("Unsupported role type: " + roleKind + " (object key " + objectKeyUsed + ")");
        }
    }

    private static Integer getInt(JsonObject o, String k) {
        if (!o.has(k) || o.get(k).isJsonNull()) {
            return null;
        }
        try {
            return o.get(k).getAsInt();
        } catch (Exception e) {
            return null;
        }
    }
}
