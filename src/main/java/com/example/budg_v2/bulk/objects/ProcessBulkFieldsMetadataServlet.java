package com.example.budg_v2.bulk.objects;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.ModuleResolver;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@WebServlet("/api/bulk/process/fields")
public class ProcessBulkFieldsMetadataServlet extends HttpServlet {

	private static final Logger logger = LoggerFactory.getLogger(ProcessBulkFieldsMetadataServlet.class);
	private static final Gson gson = new Gson();

	@Override
	protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
		CorsUtil.handlePreflight(resp);
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		response.setContentType("application/json");
		response.setCharacterEncoding("UTF-8");
		CorsUtil.setCorsHeaders(response);

		try {
			logger.info("Fetching field metadata for Process entity");
			String operation = request.getParameter("operation");

			JsonArray fields = new JsonArray();

			if (operation == null || "INSERT".equalsIgnoreCase(operation)) {
				// Required
				fields.add(field("Name", "Name", "Process name", true, "STRING"));
				fields.add(field("Description", "Description", "Process description", true, "STRING"));
				fields.add(field("Step Type", "Step Type", "Process step type (lookup)", true, "STRING"));
				fields.add(field("Type", "Type", "Process type (lookup)", true, "STRING"));
				fields.add(field("Lifecycle", "Lifecycle", "Process lifecycle (lookup)", true, "STRING"));
				// Optional (as supported by template/processor)
				fields.add(field("Ref.", "Ref.", "Reference code (auto if empty)", false, "STRING"));
				fields.add(field("Input Description", "Input Description", "Input description", false, "STRING"));
				fields.add(field("Output Description", "Output Description", "Output description", false, "STRING"));
				fields.add(field("Create Permission", "Create Permission", "Create permission (boolean)", false, "BOOLEAN"));
				fields.add(field("Read Permission", "Read Permission", "Read permission (boolean)", false, "BOOLEAN"));
				fields.add(field("Update Permission", "Update Permission", "Update permission (boolean)", false, "BOOLEAN"));
				fields.add(field("Delete Permission", "Delete Permission", "Delete permission (boolean)", false, "BOOLEAN"));
				fields.add(field("Archive Permission", "Archive Permission", "Archive permission (boolean)", false, "BOOLEAN"));
				fields.add(field("Duration", "Duration", "Estimated duration (number)", false, "INTEGER"));
				fields.add(field("Parent Name", "Parent Name", "Parent process name", false, "STRING"));
				fields.add(field("Parent Ref.", "Parent Ref.", "Parent process reference", false, "STRING"));
				fields.add(field("Parent_ID", "Parent_ID", "Parent process ID", false, "INTEGER"));
				fields.add(field("BUDG Viewing", "BUDG Viewing", "Viewing (lookup)", false, "STRING"));
				fields.add(field("BUDG Status", "BUDG Status", "Status (lookup)", false, "STRING"));
				fields.add(field("Duration Type", "Duration Type", "Duration type (lookup)", false, "STRING"));
				fields.add(field("Classification", "Classification", "Process classification (lookup)", false, "STRING"));
				fields.add(field("Automation", "Automation", "Automation (lookup)", false, "STRING"));
				fields.add(field("User Email", "User Email", "Stakeholder user email", false, "STRING"));
				fields.add(field("User First Name", "User First Name", "Stakeholder first name", false, "STRING"));
				fields.add(field("User Last Name", "User Last Name", "Stakeholder last name", false, "STRING"));
				fields.add(field("User Lan ID", "User Lan ID", "Stakeholder LAN ID", false, "STRING"));
				fields.add(field("Governance Role", "Governance Role", "Stakeholder governance role (lookup)", false, "STRING"));
			} else if ("UPDATE".equalsIgnoreCase(operation)) {
				fields.add(field("ID", "ID", "Process unique identifier", false, "INTEGER"));
				// All other fields optional
				fields.add(field("Name", "Name", "Process name", false, "STRING"));
				fields.add(field("Ref.", "Ref.", "Reference code", false, "STRING"));
				fields.add(field("Description", "Description", "Process description", false, "STRING"));
				fields.add(field("Input Description", "Input Description", "Input description", false, "STRING"));
				fields.add(field("Output Description", "Output Description", "Output description", false, "STRING"));
				fields.add(field("Step Type", "Step Type", "Process step type (lookup)", false, "STRING"));
				fields.add(field("Create Permission", "Create Permission", "Create permission (boolean)", false, "BOOLEAN"));
				fields.add(field("Read Permission", "Read Permission", "Read permission (boolean)", false, "BOOLEAN"));
				fields.add(field("Update Permission", "Update Permission", "Update permission (boolean)", false, "BOOLEAN"));
				fields.add(field("Delete Permission", "Delete Permission", "Delete permission (boolean)", false, "BOOLEAN"));
				fields.add(field("Archive Permission", "Archive Permission", "Archive permission (boolean)", false, "BOOLEAN"));
				fields.add(field("Duration", "Duration", "Estimated duration (number)", false, "INTEGER"));
				fields.add(field("Parent Name", "Parent Name", "Parent process name", false, "STRING"));
				fields.add(field("Parent Ref.", "Parent Ref.", "Parent process reference", false, "STRING"));
				fields.add(field("Parent_ID", "Parent_ID", "Parent process ID", false, "INTEGER"));
				fields.add(field("BUDG Viewing", "BUDG Viewing", "Viewing (lookup)", false, "STRING"));
				fields.add(field("BUDG Status", "BUDG Status", "Status (lookup)", false, "STRING"));
				fields.add(field("Type", "Type", "Process type (lookup)", false, "STRING"));
				fields.add(field("Duration Type", "Duration Type", "Duration type (lookup)", false, "STRING"));
				fields.add(field("Lifecycle", "Lifecycle", "Process lifecycle (lookup)", false, "STRING"));
				fields.add(field("Classification", "Classification", "Process classification (lookup)", false, "STRING"));
				fields.add(field("Automation", "Automation", "Automation (lookup)", false, "STRING"));
			} else if ("DELETE".equalsIgnoreCase(operation)) {
				fields.add(field("ID", "ID", "Process unique identifier to delete", true, "INTEGER"));
			} else {
				// Unknown operation: return a union that's helpful
				fields.add(field("ID", "ID", "Process identifier", false, "INTEGER"));
				fields.add(field("Name", "Name", "Process name", true, "STRING"));
				fields.add(field("Description", "Description", "Process description", true, "STRING"));
				fields.add(field("Step Type", "Step Type", "Process step type (lookup)", true, "STRING"));
				fields.add(field("Type", "Type", "Process type (lookup)", true, "STRING"));
				fields.add(field("Lifecycle", "Lifecycle", "Process lifecycle (lookup)", true, "STRING"));
			}

			// Custom fields from DB (same as Universal/Regulator metadata servlets) — INSERT/UPDATE only
			if (operation == null || "INSERT".equalsIgnoreCase(operation) || "UPDATE".equalsIgnoreCase(operation)) {
				try {
					for (JsonObject customField : getCustomFieldsForEntity("Process")) {
						fields.add(customField);
					}
				} catch (Exception e) {
					logger.warn("Failed to fetch custom fields for Process: {}", e.getMessage());
				}
			}

			response.setStatus(HttpServletResponse.SC_OK);
			response.getWriter().write(gson.toJson(fields));
		} catch (Exception e) {
			logger.error("Error fetching process field metadata", e);
			sendErrorResponse(response, "Failed to fetch field metadata: " + e.getMessage(), 500);
		}
	}

	private JsonObject field(String fieldName, String displayName, String description, boolean required, String dataType) {
		JsonObject f = new JsonObject();
		f.addProperty("fieldName", fieldName);
		f.addProperty("name", fieldName);
		f.addProperty("displayName", displayName);
		f.addProperty("description", description);
		f.addProperty("required", required);
		f.addProperty("dataType", dataType);
		return f;
	}

	/**
	 * Load custom field definitions for Process from Custom_Field_Metadata.
	 */
	private List<JsonObject> getCustomFieldsForEntity(String entityName) throws SQLException {
		List<JsonObject> customFields = new ArrayList<>();

		int moduleId;
		try {
			moduleId = ModuleResolver.getModuleId(entityName);
		} catch (SQLException | IllegalArgumentException e) {
			logger.warn("Module not found for entity: {}", entityName);
			return customFields;
		}

		String sql = """
			SELECT DisplayName, CustomFieldName, DataType, is_Mandatory,
			       Default_Value, Description, Placeholder_Text
			FROM Custom_Field_Metadata
			WHERE Module_ID = ?
			ORDER BY DisplayName
			""";

		try (Connection conn = DatabaseConnection.getConnection();
				PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setInt(1, moduleId);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					String displayName = rs.getString("DisplayName");
					String customFieldName = rs.getString("CustomFieldName");
					String dataType = rs.getString("DataType");
					boolean isMandatory = rs.getBoolean("is_Mandatory");
					String description = rs.getString("Description");
					if (description == null) {
						description = "";
					}
					String mappedDataType = mapCustomFieldDataType(dataType);
					String mappingKey = displayName;
					if (customFieldName != null && !customFieldName.trim().isEmpty()) {
						mappingKey = customFieldName.trim();
					}
					JsonObject cf = field(
							mappingKey,
							displayName,
							description.isEmpty() ? "Custom field" : description,
							isMandatory,
							mappedDataType);
					cf.addProperty("isCustomField", true);
					cf.addProperty("customFieldDataType", dataType);
					appendCustomFieldAliases(cf, displayName, customFieldName);
					customFields.add(cf);
				}
			}
		}

		return customFields;
	}

	private void appendCustomFieldAliases(JsonObject field, String displayName, String customFieldName) {
		JsonArray aliases = new JsonArray();
		if (displayName != null && !displayName.trim().isEmpty()) {
			String dn = displayName.trim();
			aliases.add(dn);
			aliases.add(dn + " *");
		}
		if (customFieldName != null && !customFieldName.trim().isEmpty()) {
			String cf = customFieldName.trim();
			aliases.add(cf);
			aliases.add(cf + " *");
		}
		field.add("aliases", aliases);
	}

	private String mapCustomFieldDataType(String customFieldDataType) {
		if (customFieldDataType == null) {
			return "STRING";
		}
		String lower = customFieldDataType.toLowerCase();
		switch (lower) {
			case "number":
			case "integer":
				return "INTEGER";
			case "decimal":
			case "float":
			case "double":
				return "DECIMAL";
			case "date":
				return "DATE";
			case "time":
				return "TIME";
			case "checkbox":
			case "boolean":
				return "BOOLEAN";
			case "percentage":
				return "PERCENTAGE";
			case "dropdown":
			case "multiselect":
				return "DROPDOWN";
			case "text":
			default:
				return "STRING";
		}
	}

	private void sendErrorResponse(HttpServletResponse response, String message, int statusCode)
			throws IOException {
		JsonObject error = new JsonObject();
		error.addProperty("status", "error");
		error.addProperty("message", message);
		response.setStatus(statusCode);
		response.getWriter().write(gson.toJson(error));
	}
}


