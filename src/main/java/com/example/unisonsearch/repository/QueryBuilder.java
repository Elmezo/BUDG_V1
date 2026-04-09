package com.example.unisonsearch.repository;

import com.example.budg_v2.service.SegmentAccessService;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.unisonsearch.model.QueryResult;
import com.example.unisonsearch.model.SearchParams;
import com.example.unisonsearch.model.SegmentAccessContext;
import com.example.unisonsearch.service.ConfigurationService;
import com.example.unisonsearch.service.RelationshipManager;
import com.example.unisonsearch.service.RelationshipService;
import com.example.unisonsearch.parser.*;
import com.example.unisonsearch.util.FuzzySearchUtil;
import com.example.unisonsearch.util.UnisonTrace;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.Collections;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Builds SQL and parameters identical to original servlet's logic.
 */
public class QueryBuilder {

	private final ConfigurationService configurationService;
	@SuppressWarnings("unused")
	private final RelationshipManager relationshipManager;
	private final RelationshipService relationshipService; // Optional - for new relationship logic

	public QueryBuilder(ConfigurationService configurationService, RelationshipManager relationshipManager) {
		this.configurationService = configurationService;
		this.relationshipManager = relationshipManager;
		this.relationshipService = null; // Backward compatibility
	}

	public QueryBuilder(ConfigurationService configurationService, RelationshipManager relationshipManager, RelationshipService relationshipService) {
		this.configurationService = configurationService;
		this.relationshipManager = relationshipManager;
		this.relationshipService = relationshipService;
	}

	public QueryResult build(SearchParams params) {
		String module = params.module;
		String q = params.q;
		Integer idFilter = params.idFilter;
		String relatedModule = params.relatedModule;
		Integer relatedId = params.relatedId;
		List<com.example.unisonsearch.model.SearchCondition> conditions = params.conditions;

		// Special handling for Active Tasks - return null to indicate it should be
		// handled by service layer
		if ("activeTasks".equals(module) || "active-tasks".equals(module) || "activetasks".equals(module)) {
			// Active Tasks are handled by WorkflowTaskDAO, not SQL queries
			// Return null to signal that SearchService should handle it differently
			return new QueryResult(null, List.of());
		}

		String sql = getSqlForModule(module, params.userId);
		if (sql == null) {
			return new QueryResult(null, List.of());
		}
		// Always include segment columns in Unison results where applicable
		sql = appendSegmentSelectColumnsIfNeeded(module, sql);

		List<Object> parameters = new ArrayList<>();
		boolean useFuzzy = configurationService.getFuzzySearchConfig();

		// Check if we should use AST-based parser
		boolean searchApplied = false;
		if (params.useNewParser && params.rawQuery != null && !params.rawQuery.isBlank()) {
			try {
				// Parse query into AST
				QueryParser parser = new QueryParser();
				QueryNode ast = parser.parse(params.rawQuery);

				// Generate SQL from AST
				String searchCondition = buildFromAST(ast, module, useFuzzy, parameters);

				if (searchCondition != null && !searchCondition.isEmpty()) {
					sql = insertWhereClause(sql, searchCondition);
					searchApplied = true;
				}
			} catch (QueryParseException e) {
				System.err.println("QueryBuilder: Failed to parse query '" + params.rawQuery + "': " + e.getMessage());
				// searchApplied remains false, so legacy search will run below
			}
		}
		// Fall back to legacy search if AST parser was not used or failed
		if (!searchApplied && conditions != null && !conditions.isEmpty()) {
			StringBuilder allConditions = new StringBuilder();
			boolean firstCondition = true;

			for (com.example.unisonsearch.model.SearchCondition condition : conditions) {
				if (condition.query == null || condition.query.isBlank()) {
					continue;
				}

				// Build condition for this search
				String conditionSql = buildConditionForModule(module, condition.module, condition.query, useFuzzy,
						parameters);
				if (conditionSql == null || conditionSql.isEmpty()) {
					continue;
				}

				// Add operator
				String operator = condition.operator != null ? condition.operator.toUpperCase() : "FIND";
				if (firstCondition) {
					// First condition uses WHERE
					allConditions.append(conditionSql);
					firstCondition = false;
				} else {
					// Subsequent conditions use operators
					switch (operator) {
						case "AND":
							allConditions.append(" AND ").append(conditionSql);
							break;
						case "OR":
							allConditions.append(" OR ").append(conditionSql);
							break;
						case "NOT":
							allConditions.append(" AND NOT ").append(conditionSql);
							break;
						default:
							allConditions.append(" AND ").append(conditionSql);
							break;
					}
				}
			}

			if (allConditions.length() > 0) {
				sql = insertWhereClause(sql, allConditions.toString());
			}
		} else if (!searchApplied && q != null && !q.isBlank()) {
			if (useFuzzy) {
				List<String> searchableColumns = getSearchableColumnsForModule(module);
				if (!searchableColumns.isEmpty()) {
					// Use FuzzySearchUtil for advanced fuzzy matching
					List<String> fuzzyPatterns = FuzzySearchUtil.generateFuzzyPatterns(q);
					StringBuilder searchConditions = new StringBuilder();

					for (int i = 0; i < searchableColumns.size(); i++) {
						if (i > 0) {
							searchConditions.append(" OR ");
						}

						// For each column, create OR conditions for all fuzzy patterns
						if (fuzzyPatterns.size() > 1) {
							searchConditions.append("(");
						}

					for (int j = 0; j < fuzzyPatterns.size(); j++) {
						if (j > 0) {
							searchConditions.append(" OR ");
						}
						searchConditions.append(searchableColumns.get(i)).append(" LIKE ?");
						parameters.add(fuzzyPatterns.get(j));
					}

					if (fuzzyPatterns.size() > 1) {
						searchConditions.append(")");
					}
				}

				// Also search in custom field values
				String customFieldCondition = buildCustomFieldSearchCondition(module, q, true, parameters);
				if (customFieldCondition != null) {
					searchConditions.append(" OR ").append(customFieldCondition);
				}

				sql = insertWhereClause(sql, "(" + searchConditions + ")");
			}
		} else {
			// Non-fuzzy mode: use exact match (=) for precise results
			// This ensures "age" matches exactly "age" and not "gender" or "page"
			List<String> searchableColumns = getSearchableColumnsForModule(module);
			if (!searchableColumns.isEmpty()) {
				String searchParameter = prepareSearchParameter(q, useFuzzy);
				StringBuilder searchConditions = new StringBuilder();
				for (int i = 0; i < searchableColumns.size(); i++) {
					if (i > 0) {
						searchConditions.append(" OR ");
					}
					String condition = createSearchCondition(searchableColumns.get(i), q, useFuzzy);
					searchConditions.append(condition);
					// Add parameter for each column (each ? in SQL needs its own parameter)
					parameters.add(searchParameter);
				}

				// Also search in custom field values
				String customFieldCondition = buildCustomFieldSearchCondition(module, q, false, parameters);
				if (customFieldCondition != null) {
					searchConditions.append(" OR ").append(customFieldCondition);
				}

				sql = insertWhereClause(sql, "(" + searchConditions + ")");
			}
		}
		}

		if (idFilter != null) {
			// Special handling for attribute module: use a.ID for ID filtering
			// (getIdColumnForModule returns a.Dataset_ID for segment filtering, but we need a.ID for ID filtering)
			String idColumn;
			if (module != null && module.trim().equalsIgnoreCase("attribute")) {
				idColumn = "a.ID";
			} else {
				idColumn = getIdColumnForModule(module);
			}
			if (idColumn != null) {
				sql = insertWhereClause(sql, idColumn + " = ?");
				parameters.add(idFilter);
			}
		}

		if (relatedModule != null && relatedId != null) {
			String relatedCondition = buildRelatedModuleCondition(module, relatedModule, relatedId, parameters);
			if (relatedCondition != null) {
				sql = insertWhereClause(sql, relatedCondition);
			}
		}

		// Apply segment filtering:
		// - Authenticated (userId > 0): selected segments (cube filter)
		// - Guest/Anonymous (userId <= 0 or null): Enterprise-only + Is_Public = 1
		if (params.userId != null && params.userId > 0) {
			// Authenticated users (including Super Admin) must respect cube selection.
			String segmentCondition = buildSegmentFilterCondition(module, params.userId);
			if (segmentCondition != null && !segmentCondition.isEmpty()) {
				sql = insertWhereClause(sql, segmentCondition);
			}
		} else {
			// Guest user (userId <= 0) OR Anonymous (userId == null)
			String enterpriseOnly = buildEnterpriseOnlySegmentCondition(module);
			if (enterpriseOnly != null && !enterpriseOnly.isEmpty()) {
				sql = insertWhereClause(sql, enterpriseOnly);
			}
			// V-06: unconditionally enforce Is_Public = 1 for guest users (independent of config)
			String guestAlias = getTableAliasForModule(module);
			if (guestAlias != null) {
				String guestPublicColumn = getPublicColumnForAlias(guestAlias);
				if (guestPublicColumn != null) {
					sql = insertWhereClause(sql, guestPublicColumn + " = 1");
				}
			}
		}

		sql = finalizeSqlWithWebUserDeletedStatusRule(sql, module, params.userId, null);
		return new QueryResult(sql, parameters);
	}

	/**
	 * Build segment filter condition to only show objects in user's selected
	 * segments.
	 * Uses SegmentAccessService to get user's effective segment IDs.
	 * 
	 * @param module The module being queried
	 * @param userId The user ID
	 * @return SQL condition string for segment filtering, or null if no filtering
	 *         needed
	 */
	private String buildSegmentFilterCondition(String module, int userId) {
		try {
			// Get user's effective segment IDs (intersection of accessible AND selected)
			// For Super Admin, this returns their selected segments (they have access to everything)
			// For regular users, this returns intersection of accessible AND selected segments
			Set<Integer> effectiveSegments = SegmentAccessService.getEffectiveFilterSegmentIds(userId);
			// system.out.println("[QueryBuilder] 🔍 Effective segments for user " + userId
			// + ": " + effectiveSegments);

			if (effectiveSegments == null || effectiveSegments.isEmpty()) {
				// system.out.println("[QueryBuilder] ⚠️ No effective segments found");
				return null;
			}

			// Get the object type for segment filtering
			String objectType = getObjectTypeForModule(module);
			if (objectType == null) {
				// system.out.println("[QueryBuilder] ⚠️ No object type for module: " + module);
				return null;
			}

			// Get ID column for this module
			String idColumn = getIdColumnForModule(module);
			if (idColumn == null) {
				// system.out.println("[QueryBuilder] ⚠️ No ID column for module: " + module);
				return null;
			}
			// system.out.println("[QueryBuilder] 📋 Building filter: module=" + module + ",
			// objectType=" + objectType + ", idColumn=" + idColumn);

			// Build segment IDs list
			String segmentIdList = effectiveSegments.stream()
					.map(String::valueOf)
					.reduce((a, b) -> a + "," + b)
					.orElse("1");
			String segmentTypeInClause = buildSegmentTypeInClauseForModule(module);

			boolean enterpriseSelected = effectiveSegments.contains(1);

			if (enterpriseSelected) {
				// Enterprise is selected - include objects in selected segments OR objects
				// without segment assignment
				return String.format("""
							(
								EXISTS (
									SELECT 1 FROM segment_x_resource sxr
									JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID
									JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID
									WHERE orr.Object_ID = %s
									AND LOWER(REPLACE(REPLACE(REPLACE(TRIM(sot.Type), ' ', ''), '_', ''), '-', '')) IN (%s)
									AND sxr.Segment_ID IN (%s)
									AND sxr.Deleted_At IS NULL
								)
								OR
								NOT EXISTS (
									SELECT 1 FROM segment_x_resource sxr
									JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID
									JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID
									WHERE orr.Object_ID = %s
									AND LOWER(REPLACE(REPLACE(REPLACE(TRIM(sot.Type), ' ', ''), '_', ''), '-', '')) IN (%s)
									AND sxr.Deleted_At IS NULL
								)
							)
						""", idColumn, segmentTypeInClause, segmentIdList, idColumn, segmentTypeInClause);
			} else {
				// Enterprise not selected - only show objects explicitly in selected segments
				return String.format("""
							EXISTS (
								SELECT 1 FROM segment_x_resource sxr
								JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID
								JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID
								WHERE orr.Object_ID = %s
								AND LOWER(REPLACE(REPLACE(REPLACE(TRIM(sot.Type), ' ', ''), '_', ''), '-', '')) IN (%s)
								AND sxr.Segment_ID IN (%s)
								AND sxr.Deleted_At IS NULL
							)
						""", idColumn, segmentTypeInClause, segmentIdList);
			}
		} catch (SQLException e) {
			System.err.println("Error building segment filter condition: " + e.getMessage());
			return null;
		}
	}

	/**
	 * Anonymous users must only see Enterprise objects (Segment_ID = 1) plus
	 * objects with no segment assignment.
	 * This hides all non-public/private segments from unauthenticated users.
	 */
	private String buildEnterpriseOnlySegmentCondition(String module) {
		String objectType = getObjectTypeForModule(module);
		String idColumn = getIdColumnForModule(module);
		if (objectType == null || idColumn == null) {
			return null;
		}
		String segmentTypeInClause = buildSegmentTypeInClauseForModule(module);
		return String.format("""
					(
						EXISTS (
							SELECT 1 FROM segment_x_resource sxr
							JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID
							JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID
							WHERE orr.Object_ID = %s
							AND LOWER(REPLACE(REPLACE(REPLACE(TRIM(sot.Type), ' ', ''), '_', ''), '-', '')) IN (%s)
							AND sxr.Segment_ID = 1
							AND sxr.Deleted_At IS NULL
						)
						OR
						NOT EXISTS (
							SELECT 1 FROM segment_x_resource sxr
							JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID
							JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID
							WHERE orr.Object_ID = %s
							AND LOWER(REPLACE(REPLACE(REPLACE(TRIM(sot.Type), ' ', ''), '_', ''), '-', '')) IN (%s)
							AND sxr.Deleted_At IS NULL
						)
					)
				""", idColumn, segmentTypeInClause, idColumn, segmentTypeInClause);
	}

	/**
	 * Add segment_id + segment_name columns to SELECT list for this module when it
	 * maps
	 * cleanly to a single segment-able object type.
	 */
	private String appendSegmentSelectColumnsIfNeeded(String module, String sql) {
		if (sql == null || sql.isBlank() || module == null)
			return sql;

		// People/Role are managed via segment_x_identity and/or span multiple object
		// types; skip.
		// Org units don't have segments; skip.
		String normalized = normalizeModuleName(module);
		if ("people".equals(normalized) || "role".equals(normalized) || "org_unit".equals(normalized))
			return sql;

		String objectType = getObjectTypeForModule(module);
		String idColumn = getIdColumnForModule(module);
		String segmentTypeInClause = buildSegmentTypeInClauseForModule(module);

		// Interfaces inherit segment from their target system; derive segment columns
		// from Target_systemID so list pages do not show "Not Assigned".
		if ("interface".equals(normalized)) {
			objectType = "System";
			idColumn = "i.Target_systemID";
			segmentTypeInClause = buildSegmentTypeInClauseForModule("system");
		}

		if (objectType == null || idColumn == null)
			return sql;

		// Segment ID (default Enterprise = 1 when no assignment)
		String segmentIdExpr = String.format("""
					COALESCE((
						SELECT sxr.Segment_ID
						FROM segment_x_resource sxr
						JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID
						JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID
						WHERE orr.Object_ID = %s
						  AND LOWER(REPLACE(REPLACE(REPLACE(TRIM(sot.Type), ' ', ''), '_', ''), '-', '')) IN (%s)
						  AND sxr.Deleted_At IS NULL
						LIMIT 1
					), 1) AS Segment_ID
				""", idColumn, segmentTypeInClause).trim();

		// Segment name (default Enterprise)
		String segmentNameExpr = String.format("""
					COALESCE((
						SELECT s.Name
						FROM segment s
						WHERE s.ID = COALESCE((
							SELECT sxr.Segment_ID
							FROM segment_x_resource sxr
							JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID
							JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID
							WHERE orr.Object_ID = %s
							  AND LOWER(REPLACE(REPLACE(REPLACE(TRIM(sot.Type), ' ', ''), '_', ''), '-', '')) IN (%s)
							  AND sxr.Deleted_At IS NULL
							LIMIT 1
						), 1)
						  AND s.Deleted_At IS NULL
						LIMIT 1
					), 'Enterprise') AS Segment
				""", idColumn, segmentTypeInClause).trim();

		return insertSelectColumnsBeforeFrom(sql, segmentIdExpr + ",\n" + segmentNameExpr);
	}

	private String buildSegmentTypeInClauseForModule(String module) {
		String canonicalType = getObjectTypeForModule(module);
		if (canonicalType == null || canonicalType.isBlank()) {
			return "'__NO_SEGMENT_TYPE__'";
		}
		List<String> candidates = new ArrayList<>();
		candidates.add(canonicalType);
		String compact = canonicalType.replace(" ", "").replace("_", "").replace("-", "").toLowerCase();
		switch (compact) {
			case "system" -> {
				candidates.add("Systems");
				candidates.add("system");
				candidates.add("systems");
			}
			case "dataset" -> {
				candidates.add("Datasets");
				candidates.add("Data Set");
				candidates.add("Data Sets");
				candidates.add("dataset");
				candidates.add("datasets");
				candidates.add("data_set");
				candidates.add("data_sets");
			}
			case "policy" -> {
				candidates.add("Policies");
				candidates.add("policy");
				candidates.add("policies");
			}
			case "process" -> {
				candidates.add("Processes");
				candidates.add("process");
				candidates.add("processes");
			}
			case "project" -> {
				candidates.add("Projects");
				candidates.add("project");
				candidates.add("projects");
			}
			case "product" -> {
				candidates.add("Products");
				candidates.add("product");
				candidates.add("products");
			}
			case "glossary" -> {
				candidates.add("Glossaries");
				candidates.add("glossary");
				candidates.add("glossaries");
			}
			case "capability" -> {
				candidates.add("Capabilities");
				candidates.add("capability");
				candidates.add("capabilities");
			}
			case "client" -> {
				candidates.add("Clients");
				candidates.add("client");
				candidates.add("clients");
			}
			case "committee" -> {
				candidates.add("Committees");
				candidates.add("committee");
				candidates.add("committees");
			}
			case "geography" -> {
				candidates.add("Geographies");
				candidates.add("geography");
				candidates.add("geographies");
			}
			case "regulation" -> {
				candidates.add("Regulations");
				candidates.add("regulation");
				candidates.add("regulations");
			}
			case "regulator" -> {
				candidates.add("Regulators");
				candidates.add("regulator");
				candidates.add("regulators");
			}
			case "businessarea" -> {
				candidates.add("Business Area");
				candidates.add("Business Areas");
				candidates.add("BusinessArea");
				candidates.add("business area");
				candidates.add("business areas");
				candidates.add("businessarea");
				candidates.add("business_area");
				candidates.add("businessareas");
			}
			case "legalentity" -> {
				candidates.add("Legal Entity");
				candidates.add("Legal Entities");
				candidates.add("LegalEntity");
				candidates.add("legal entity");
				candidates.add("legal entities");
				candidates.add("legalentity");
				candidates.add("legal_entity");
				candidates.add("legalentities");
			}
			case "regulatorytheme" -> {
				candidates.add("Regulatory Theme");
				candidates.add("Regulatory Themes");
				candidates.add("RegulatoryTheme");
				candidates.add("regulatory theme");
				candidates.add("regulatory themes");
				candidates.add("regulatorytheme");
				candidates.add("regulatory_theme");
				candidates.add("regulatorythemes");
			}
			case "systeminterface" -> {
				candidates.add("Interface");
				candidates.add("System Interface");
				candidates.add("interface");
				candidates.add("Interfaces");
				candidates.add("SystemInterface");
				candidates.add("system interface");
				candidates.add("systeminterface");
			}
			default -> {
				// Use canonical type only.
			}
		}
		return candidates.stream()
				.map(this::normalizeSegmentTypeToken)
				.distinct()
				.map(this::quoteSqlLiteral)
				.reduce((a, b) -> a + "," + b)
				.orElse("'__NO_SEGMENT_TYPE__'");
	}

	private String normalizeSegmentTypeToken(String value) {
		if (value == null) {
			return "";
		}
		return value.trim()
				.toLowerCase(Locale.ROOT)
				.replace(" ", "")
				.replace("_", "")
				.replace("-", "");
	}

	private String quoteSqlLiteral(String value) {
		if (value == null) {
			return "''";
		}
		return "'" + value.replace("'", "''") + "'";
	}

	/**
	 * Insert additional columns into the SELECT list right before the first main
	 * FROM.
	 * Tries to match "\nFROM" to avoid false positives (e.g., "fromName").
	 */
	private String insertSelectColumnsBeforeFrom(String sql, String additionalColumns) {
		if (sql == null || additionalColumns == null || additionalColumns.isBlank())
			return sql;
		Pattern p = Pattern.compile("(?i)\\n\\s*from\\s");
		Matcher m = p.matcher(sql);
		if (m.find()) {
			int idx = m.start(); // at newline before FROM
			String beforeFrom = sql.substring(0, idx).trim();
			String fromAndAfter = sql.substring(idx); // Keep original formatting including newline
			// Ensure there's a comma before adding new columns if the last non-whitespace
			// character isn't already a comma
			String trimmedBefore = beforeFrom.trim();
			if (!trimmedBefore.endsWith(",")) {
				beforeFrom = beforeFrom + ",";
			}
			return beforeFrom + "\n    " + additionalColumns.replace("\n", "\n    ") + fromAndAfter;
		}
		// Fallback: try " FROM " (case-insensitive) if formatting is not multiline
		String lower = sql.toLowerCase(Locale.ROOT);
		int fromIdx = lower.indexOf(" from ");
		if (fromIdx > 0) {
			String beforeFrom = sql.substring(0, fromIdx).trim();
			String fromAndAfter = sql.substring(fromIdx);
			// Ensure there's a comma before adding new columns if the last non-whitespace
			// character isn't already a comma
			if (!beforeFrom.endsWith(",")) {
				beforeFrom = beforeFrom + ",";
			}
			return beforeFrom + ",\n    " + additionalColumns.replace("\n", "\n    ") + fromAndAfter;
		}
		return sql;
	}

	/**
	 * Map module name to segment object type.
	 */
	private String getObjectTypeForModule(String module) {
		if (module == null)
			return null;
		String normalized = normalizeModuleName(module);
		return switch (normalized) {
			case "dataset" -> "Dataset";
			case "attribute" -> "Dataset"; // Attributes inherit from dataset
			case "system" -> "System";
			case "glossary" -> "Glossary";
			case "process" -> "Process";
			case "project" -> "Project";
			case "product" -> "Product";
			case "policy" -> "Policy";
			case "legal_entity" -> "LegalEntity";
			case "business_area" -> "BusinessArea";
			case "capability" -> "Capability";
			case "client" -> "Client";
			case "committee" -> "Committee";
			case "org_unit" -> null; // Org units don't have segments - removed from segment filtering
			case "geography" -> "Geography";
			case "regulation" -> "Regulation";
			case "regulator" -> "Regulator";
			case "regulatory_theme" -> "RegulatoryTheme";
			case "interface" -> "SystemInterface";
			case "people" -> null; // People segment membership is handled via segment_x_identity (not object
									// segments)
			case "role" -> null; // Role results span multiple object types; no single segment column
			case "data_quality" -> "Dataset";
			default -> null;
		};
	}

	/**
	 * Build SQL condition for filtering by related module and ID.
	 * Uses RelationshipService to get related IDs and builds SQL IN clause.
	 * 
	 * @param module The module being queried
	 * @param relatedModule The related module (e.g., "system")
	 * @param relatedId The ID in the related module
	 * @param parameters List to add parameters to
	 * @return SQL condition string, or null if relationship not supported
	 */
	private String buildRelatedModuleCondition(String module, String relatedModule, Integer relatedId, List<Object> parameters) {
		if (relationshipService == null) {
			// RelationshipService not available - backward compatibility
			return null;
		}

		String normalizedModule = normalizeModuleName(module);
		String normalizedRelated = normalizeModuleName(relatedModule);
		
		if (normalizedModule == null || normalizedRelated == null) {
			return null;
		}

		// Check if relationship is allowed
		Set<String> allowedTargets = RelationshipManager.getAllowedTargets(normalizedRelated);
		if (allowedTargets == null || !allowedTargets.contains(normalizedModule)) {
			// Check if it's a special relationship (reverse direction)
			Set<String> specialTargets = RelationshipManager.getSpecialRelationships(normalizedRelated);
			if (specialTargets == null || !specialTargets.contains(normalizedModule)) {
				return null;
			}
		}

		try {
			// Get related IDs using RelationshipService
			List<Integer> relatedIds = getRelatedIdsFromService(normalizedRelated, relatedId, normalizedModule);
			
			if (relatedIds == null || relatedIds.isEmpty()) {
				return null;
			}

			// Build SQL IN clause
			String idColumn = getIdColumnForModule(normalizedModule);
			if (idColumn == null) {
				return null;
			}

			// Build IN clause with placeholders and add parameters
			StringBuilder inClause = new StringBuilder();
			for (int i = 0; i < relatedIds.size(); i++) {
				if (i > 0) inClause.append(",");
				inClause.append("?");
				parameters.add(relatedIds.get(i));
			}

			return idColumn + " IN (" + inClause + ")";
		} catch (Exception e) {
			System.err.println("Error building related module condition: " + e.getMessage());
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Get related IDs from RelationshipService based on source and target facets.
	 */
	private List<Integer> getRelatedIdsFromService(String sourceFacet, int sourceId, String targetFacet) throws Exception {
		// Map sourceFacet -> targetFacet to RelationshipService methods
		switch (sourceFacet) {
			case "dataset":
				switch (targetFacet) {
					case "system":
						Integer systemId = relationshipService.getSystemOfDataset(sourceId);
						return systemId != null ? List.of(systemId) : Collections.emptyList();
					case "attribute":
						return relationshipService.getAttributesByDataset(sourceId);
					case "glossary":
						return relationshipService.getGlossariesByDatasetIncludingAttributes(sourceId);
					case "interface":
						return relationshipService.getInterfacesByDataset(sourceId);
				}
				break;
			case "system":
				switch (targetFacet) {
					case "dataset":
						return relationshipService.getDatasetsBySystem(sourceId);
					case "attribute":
						return relationshipService.getAllAttributesBySystem(sourceId);
					case "glossary":
						return relationshipService.getGlossariesBySystem(sourceId);
					case "interface":
						return relationshipService.getInterfacesBySystem(sourceId);
				}
				break;
			case "glossary":
				switch (targetFacet) {
					case "dataset":
						return relationshipService.getDatasetsByGlossary(sourceId);
					case "attribute":
						return relationshipService.getAttributesByGlossary(sourceId);
					case "system":
						return relationshipService.getSystemByGlossary(sourceId);
				}
				break;
			case "attribute":
				switch (targetFacet) {
					case "dataset":
						Integer datasetId = relationshipService.getDatasetOfAttribute(sourceId);
						return datasetId != null ? List.of(datasetId) : Collections.emptyList();
					case "glossary":
						Integer glossaryId = relationshipService.getGlossaryOfAttribute(sourceId);
						return glossaryId != null ? List.of(glossaryId) : Collections.emptyList();
					case "system":
						Integer sysId = relationshipService.getSystemOfAttribute(sourceId);
						return sysId != null ? List.of(sysId) : Collections.emptyList();
				}
				break;
			case "interface":
				switch (targetFacet) {
					case "system":
						return relationshipService.getSystemsByInterface(sourceId);
				}
				break;
			case "project":
				switch (targetFacet) {
					case "glossary":
						return relationshipService.getGlossariesByProject(sourceId);
					case "dataset":
						return relationshipService.getDatasetsByProject(sourceId);
					case "attribute":
						return relationshipService.getAttributesByProject(sourceId);
					case "system":
						return relationshipService.getSystemsByProject(sourceId);
				}
				break;
			case "process":
				switch (targetFacet) {
					case "glossary":
						return relationshipService.getGlossariesByProcess(sourceId);
					case "dataset":
						return relationshipService.getDatasetsByProcess(sourceId);
					case "attribute":
						return relationshipService.getAttributesByProcess(sourceId);
					case "system":
						return relationshipService.getSystemsByProcess(sourceId);
				}
				break;
			case "policy":
				switch (targetFacet) {
					case "glossary":
						return relationshipService.getGlossariesByPolicy(sourceId);
					case "dataset":
						return relationshipService.getDatasetsByPolicy(sourceId);
					case "attribute":
						return relationshipService.getAttributesByPolicy(sourceId);
					case "system":
						return relationshipService.getSystemsByPolicy(sourceId);
				}
				break;
			case "capability":
				switch (targetFacet) {
					case "glossary":
						return relationshipService.getGlossariesByCapability(sourceId);
					case "dataset":
						return relationshipService.getDatasetsByCapability(sourceId);
					case "attribute":
						return relationshipService.getAttributesByCapability(sourceId);
					case "system":
						return relationshipService.getSystemsByCapability(sourceId);
				}
				break;
		}
		return Collections.emptyList();
	}

	/**
	 * Build a search condition for a specific module.
	 * 
	 * @param targetModule    The module being queried (from URL)
	 * @param conditionModule The module specified in the condition (may be
	 *                        different)
	 * @param query           The search query
	 * @param useFuzzy        Whether to use fuzzy search
	 * @param parameters      List to add parameters to
	 * @return SQL condition string, or null if invalid
	 */
	private String buildConditionForModule(String targetModule, String conditionModule, String query, boolean useFuzzy,
			List<Object> parameters) {
		// Normalize module names
		String normalizedTarget = normalizeModuleName(targetModule);
		String normalizedCondition = normalizeModuleName(conditionModule);

		// If conditionModule is different from targetModule, build a relationship-based
		// condition
		if (normalizedCondition != null && !normalizedCondition.equals(normalizedTarget)) {
			return buildCrossModuleCondition(normalizedTarget, normalizedCondition, query, useFuzzy, parameters);
		}

		// Same module - use direct search
		List<String> searchableColumns = getSearchableColumnsForModule(targetModule);
		if (searchableColumns.isEmpty()) {
			return null;
		}

		if (useFuzzy) {
			// Use FuzzySearchUtil for advanced fuzzy matching
			List<String> fuzzyPatterns = FuzzySearchUtil.generateFuzzyPatterns(query);
			StringBuilder searchConditions = new StringBuilder();

			for (int i = 0; i < searchableColumns.size(); i++) {
				if (i > 0) {
					searchConditions.append(" OR ");
				}

				// For each column, create OR conditions for all fuzzy patterns
				if (fuzzyPatterns.size() > 1) {
					searchConditions.append("(");
				}

				for (int j = 0; j < fuzzyPatterns.size(); j++) {
					if (j > 0) {
						searchConditions.append(" OR ");
					}
					searchConditions.append(searchableColumns.get(i)).append(" LIKE ?");
					parameters.add(fuzzyPatterns.get(j));
				}

				if (fuzzyPatterns.size() > 1) {
					searchConditions.append(")");
				}
			}

			// Also search in custom field values
			String customFieldCondition = buildCustomFieldSearchCondition(targetModule, query, true, parameters);
			if (customFieldCondition != null) {
				searchConditions.append(" OR ").append(customFieldCondition);
			}

			return "(" + searchConditions + ")";
		} else {
			// Non-fuzzy mode: use simple matching
			String searchParameter = prepareSearchParameter(query, useFuzzy);
			StringBuilder searchConditions = new StringBuilder();

			for (int i = 0; i < searchableColumns.size(); i++) {
				if (i > 0) {
					searchConditions.append(" OR ");
				}
				searchConditions.append(createSearchCondition(searchableColumns.get(i), query, useFuzzy));
				parameters.add(searchParameter);
			}

			// Also search in custom field values
			String customFieldCondition = buildCustomFieldSearchCondition(targetModule, query, false, parameters);
			if (customFieldCondition != null) {
				searchConditions.append(" OR ").append(customFieldCondition);
			}

			return "(" + searchConditions + ")";
		}
	}

	/**
	 * Variant of buildConditionForModule that respects a "Search in" field selection.
	 * If searchFields is null or empty, behaves identically to buildConditionForModule.
	 */
	private String buildConditionForModuleWithSearchFields(String targetModule, String conditionModule, String query,
			boolean useFuzzy, List<Object> parameters, Map<String, Boolean> searchFields) {
		String normalizedTarget = normalizeModuleName(targetModule);
		String normalizedCondition = normalizeModuleName(conditionModule);
		// Cross-module path doesn't need filtering here; delegate to original method
		if (normalizedCondition != null && !normalizedCondition.equals(normalizedTarget)) {
			return buildCrossModuleCondition(normalizedTarget, normalizedCondition, query, useFuzzy, parameters);
		}
		// Use filtered column list if searchFields provided
		List<String> searchableColumns = getSearchableColumnsForModuleFiltered(targetModule, searchFields);

		// Extract custom-field IDs from the searchFields map.
		// Keys that match "cf_<digits>" are CF references; true = enabled, false = disabled.
		// null searchFields → pass null (means "all CFs"); non-null → pass selected subset.
		List<Integer> enabledCfIds = null;
		if (searchFields != null) {
			enabledCfIds = new ArrayList<>();
			boolean anyCfKeyPresent = false;
			for (Map.Entry<String, Boolean> entry : searchFields.entrySet()) {
				String k = entry.getKey();
				if (k != null && k.startsWith("cf_")) {
					anyCfKeyPresent = true;
					if (Boolean.TRUE.equals(entry.getValue())) {
						try {
							enabledCfIds.add(Integer.parseInt(k.substring(3)));
						} catch (NumberFormatException ignored) { /* skip malformed keys */ }
					}
				}
			}
			// If searchFields was provided but contained NO cf_ keys, the user restricted
			// the search to static columns only — skip all CF search (keep enabledCfIds empty).
			// Only treat as "no restriction" (null) when searchFields itself is null (i.e. user
			// opened the search without touching the "Search in" checkboxes at all).
		}

		if (searchableColumns.isEmpty() && (enabledCfIds == null || enabledCfIds.isEmpty())) {
			return null;
		}

		if (useFuzzy) {
			List<String> fuzzyPatterns = FuzzySearchUtil.generateFuzzyPatterns(query);
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < searchableColumns.size(); i++) {
				if (i > 0) sb.append(" OR ");
				if (fuzzyPatterns.size() > 1) sb.append("(");
				for (int j = 0; j < fuzzyPatterns.size(); j++) {
					if (j > 0) sb.append(" OR ");
					sb.append(searchableColumns.get(i)).append(" LIKE ?");
					parameters.add(fuzzyPatterns.get(j));
				}
				if (fuzzyPatterns.size() > 1) sb.append(")");
			}
			String customFieldCondition = buildCustomFieldSearchConditionFiltered(targetModule, query, true, parameters, enabledCfIds);
			if (customFieldCondition != null) {
				if (sb.length() > 0) sb.append(" OR ");
				sb.append(customFieldCondition);
			}
			if (sb.length() == 0) return null;
			return "(" + sb + ")";
		} else {
			String searchParameter = prepareSearchParameter(query, useFuzzy);
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < searchableColumns.size(); i++) {
				if (i > 0) sb.append(" OR ");
				sb.append(createSearchCondition(searchableColumns.get(i), query, useFuzzy));
				parameters.add(searchParameter);
			}
			String customFieldCondition = buildCustomFieldSearchConditionFiltered(targetModule, query, false, parameters, enabledCfIds);
			if (customFieldCondition != null) {
				if (sb.length() > 0) sb.append(" OR ");
				sb.append(customFieldCondition);
			}
			if (sb.length() == 0) return null;
			return "(" + sb + ")";
		}
	}

	/**
	 * Build a condition that filters targetModule based on search in
	 * conditionModule.
	 * Example: Filter datasets by searching in their attributes.
	 * 
	 * @param targetModule    The module being queried
	 * @param conditionModule The module to search in
	 * @param query           The search query
	 * @param useFuzzy        Whether to use fuzzy search
	 * @param parameters      List to add parameters to
	 * @return SQL condition string, or null if no relationship exists
	 */
	private String buildCrossModuleCondition(String targetModule, String conditionModule, String query,
			boolean useFuzzy, List<Object> parameters) {
		// ensureInitialized() method removed - RelationshipManager no longer needs initialization

		// Get the relationship condition from targetModule to conditionModule
		// We need to find targetModule records that are related to conditionModule
		// records matching the query

		// First, find IDs in conditionModule that match the query
		List<String> searchableColumns = getSearchableColumnsForModule(conditionModule);
		if (searchableColumns.isEmpty()) {
			return null;
		}

		StringBuilder conditionSearchConditions = new StringBuilder();

		if (useFuzzy) {
			// Use FuzzySearchUtil for advanced fuzzy matching
			List<String> fuzzyPatterns = FuzzySearchUtil.generateFuzzyPatterns(query);

			for (int i = 0; i < searchableColumns.size(); i++) {
				if (i > 0) {
					conditionSearchConditions.append(" OR ");
				}

				// For each column, create OR conditions for all fuzzy patterns
				if (fuzzyPatterns.size() > 1) {
					conditionSearchConditions.append("(");
				}

				for (int j = 0; j < fuzzyPatterns.size(); j++) {
					if (j > 0) {
						conditionSearchConditions.append(" OR ");
					}
					conditionSearchConditions.append(searchableColumns.get(i)).append(" LIKE ?");
					parameters.add(fuzzyPatterns.get(j));
				}

				if (fuzzyPatterns.size() > 1) {
					conditionSearchConditions.append(")");
				}
			}
		} else {
			// Non-fuzzy mode: use simple matching
			String searchParameter = prepareSearchParameter(query, useFuzzy);

			for (int i = 0; i < searchableColumns.size(); i++) {
				if (i > 0) {
					conditionSearchConditions.append(" OR ");
				}
				conditionSearchConditions.append(createSearchCondition(searchableColumns.get(i), query, useFuzzy));
				parameters.add(searchParameter);
			}
		}

		// Get the table name and ID column for conditionModule
		String conditionTable = getTableNameForModule(conditionModule);
		// Special handling for attribute module: use a.ID for ID filtering in subquery
		// (getIdColumnForModule returns a.Dataset_ID for segment filtering, but we need a.ID for ID filtering)
		String conditionIdColumnRaw;
		if (conditionModule != null && conditionModule.trim().equalsIgnoreCase("attribute")) {
			conditionIdColumnRaw = "a.ID";
		} else {
			conditionIdColumnRaw = getIdColumnForModule(conditionModule);
		}
		if (conditionTable == null || conditionIdColumnRaw == null) {
			return null;
		}

		// Get the relationship condition from targetModule to conditionModule
		// We need the reverse: find targetModule IDs where conditionModule matches
		// Use RelationshipService to get related IDs
		if (relationshipService == null) {
			// RelationshipService not available - backward compatibility
			return null;
		}

		String normalizedTarget = normalizeModuleName(targetModule);
		String normalizedCondition = normalizeModuleName(conditionModule);
		
		if (normalizedTarget == null || normalizedCondition == null) {
			return null;
		}

		// Check if relationship is allowed
		Set<String> allowedTargets = RelationshipManager.getAllowedTargets(normalizedCondition);
		Set<String> specialTargets = RelationshipManager.getSpecialRelationships(normalizedCondition);
		
		boolean isAllowed = (allowedTargets != null && allowedTargets.contains(normalizedTarget));
		boolean isSpecial = (specialTargets != null && specialTargets.contains(normalizedTarget));
		
		if (!isAllowed && !isSpecial) {
			return null;
		}

		try {
			// Get IDs in conditionModule that match the search query
			// We need to find conditionModule records matching the query first
			// Then get targetModule IDs related to those conditionModule records
			
			// This is complex - we need to:
			// 1. Find conditionModule IDs matching the query (already done in conditionSubquery)
			// 2. For each matching conditionModule ID, get related targetModule IDs
			// 3. Build WHERE targetModule.ID IN (related IDs)
			
			// For now, use a simpler approach: get all related IDs and filter in memory
			// This is less efficient but works correctly
			
			// Get targetModule ID column
			String targetIdColumn = getIdColumnForModule(normalizedTarget);
			if (targetIdColumn == null) {
				return null;
			}
			
			// For cross-module conditions, we need to:
			// 1. Find conditionModule IDs matching the search (already in conditionSearchConditions)
			// 2. For each matching conditionModule ID, get related targetModule IDs using RelationshipService
			// 3. Build WHERE targetModule.ID IN (related IDs)
			
			// This is complex because we need to execute the subquery first to get matching IDs,
			// then use RelationshipService to get related targetModule IDs.
			// For now, this functionality is not fully implemented as it requires database access
			// which QueryBuilder should avoid (separation of concerns).
			
			// The proper solution would be to handle this at a higher level (e.g., in SearchService)
			// where we can execute the subquery, get IDs, call RelationshipService, and build the final query.
			
			return null; // Cross-module conditions need to be handled at service layer
		} catch (Exception e) {
			System.err.println("Error building cross-module condition: " + e.getMessage());
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Normalize module name to match RelationshipManager keys.
	 * This should match the normalizeModuleName method in RelationshipManager.
	 */
	private String normalizeModuleName(String module) {
		if (module == null)
			return null;
		String c = module.trim().toLowerCase().replace("-", "_").replace(" ", "_");

		// Map common variations to standard module names (matching RelationshipManager)
		return switch (c) {
			case "dataset", "datasets", "data_set", "data_sets", "datasetentity", "data" -> "dataset";
			case "attribute", "attributes", "attr", "field", "fields", "column", "columns" -> "attribute";
			case "system", "systems", "sys", "application", "applications", "app", "apps" -> "system";
			case "glossary", "glossaries", "dictionary", "dictionaries", "term", "terms" -> "glossary";
			case "people", "person", "user", "users", "employee", "employees", "staff" -> "people";
			case "interface", "interfaces", "integration", "integrations", "connection", "connections" -> "interface";
			case "orgunit", "org_unit", "org_units", "organization", "organizations", "department", "departments" ->
				"org_unit";
			case "legal_entity", "legalentity", "legal_entities", "legal", "company", "companies" -> "legal_entity";
			case "business_area", "businessarea", "business_areas", "businessareas", "business" -> "business_area";
			case "regulation", "regulations" -> "regulation";
			case "geography", "geographies", "geo" -> "geography";
			case "regulator", "regulators" -> "regulator";
			case "regulatory_theme", "regulatorytheme", "regulatory_themes", "regulatorythemes", "theme", "themes" ->
				"regulatory_theme";
			case "process", "processes" -> "process";
			case "project", "projects" -> "project";
			case "product", "products" -> "product";
			case "policy", "policies" -> "policy";
			case "capability", "capabilities" -> "capability";
			case "client", "clients" -> "client";
			case "committee", "committees" -> "committee";
			case "requirement", "requirements" -> "requirement";
			case "data_store", "datastore", "data_stores" -> "data_store";
			case "data_quality", "dataquality" -> "data_quality";
			case "association_origin", "associationorigin" -> "association_origin";
			case "role", "roles" -> "role";
			default -> c;
		};
	}

	/**
	 * Get table name for a module.
	 */
	private String getTableNameForModule(String module) {
		if (module == null)
			return null;
		String normalized = normalizeModuleName(module);
		return switch (normalized) {
			case "dataset" -> "dataset";
			case "attribute" -> "attribute";
			case "system" -> "system";
			case "glossary" -> "glossary";
			case "people" -> "people";
			case "interface" -> "interface";
			case "process" -> "process";
			case "project" -> "project";
			case "product" -> "product";
			case "policy" -> "policy";
			case "legal_entity" -> "legal_entity";
			case "business_area" -> "business_area";
			case "capability" -> "capability";
			case "client" -> "client";
			case "committee" -> "committee";
			case "org_unit" -> "org_unit";
			case "geography" -> "geography";
			case "regulation" -> "regulation";
			case "regulator" -> "regulator";
			case "regulatory_theme" -> "regulatorytheme";
			case "requirement" -> "requirement";
			case "data_store" -> "data_store";
			case "data_quality" -> "data_quality";
			case "association_origin" -> "association_origin";
			case "role" -> "object_x_people";
			default -> normalized;
		};
	}

	/**
	 * Get table alias for a module (as used in SQL queries).
	 */
	private String getTableAliasForModule(String module) {
		if (module == null)
			return null;
		String normalized = normalizeModuleName(module);
		return switch (normalized) {
			case "dataset" -> "d";
			case "attribute" -> "a";
			case "system" -> "s";
			case "glossary" -> "g";
			case "people" -> "p";
			case "interface" -> "i";
			case "process" -> "pr";
			case "project" -> "prj";
			case "product" -> "prd";
			case "policy" -> "po";
			case "legal_entity" -> "l";
			case "business_area" -> "ba";
			case "capability" -> "cap";
			case "client" -> "cl";
			case "committee" -> "com";
			case "org_unit" -> "ou";
			case "geography" -> "geo";
			case "regulation" -> "reg";
			case "regulator" -> "regu";
			case "regulatory_theme" -> "rt";
			case "requirement" -> "r";
			case "data_store" -> "ds";
			case "data_quality" -> "dq";
			case "association_origin" -> "ao";
			case "role" -> "oxp";
			default -> null;
		};
	}

	/**
	 * Primary entity table alias in module search SQL (matches the alias passed to {@code applyCommonFilters}).
	 * Used to qualify filter columns (e.g. {@code Created_Datetime}) that would otherwise be
	 * ambiguous when the same column name exists on joined tables (e.g. glossary + people).
	 */
	private String getPrimaryEntityAliasForFilterQualification(String module) {
		if (module == null) {
			return null;
		}
		String n = normalizeModuleName(module);
		if (n == null) {
			return null;
		}
		return switch (n) {
			case "dataset" -> "d";
			case "attribute" -> "a";
			case "system" -> "s";
			case "glossary" -> "g";
			case "people" -> "p";
			case "interface" -> "i";
			case "org_unit" -> "ou";
			case "process" -> "pr";
			case "project" -> "prj";
			case "product" -> "prd";
			case "policy" -> "po";
			case "legal_entity" -> "l";
			case "business_area" -> "ba";
			case "capability" -> "c";
			case "client" -> "c";
			case "committee" -> "c";
			case "geography" -> "g";
			case "regulation" -> "r";
			case "regulator" -> "reg";
			case "regulatory_theme" -> "rt";
			case "change_request" -> "cr";
			case "role" -> "oxp";
			case "data_quality" -> "dq";
			default -> null;
		};
	}

	/** Prefix bare column names with the module's primary table alias when needed for WHERE clauses. */
	private String qualifyFilterColumnExpression(String module, String columnExpr) {
		if (columnExpr == null || columnExpr.isBlank()) {
			return columnExpr;
		}
		String trimmed = columnExpr.trim();
		if (trimmed.contains(".")) {
			return columnExpr;
		}
		if (trimmed.contains("(") || trimmed.contains(" ") || trimmed.contains("`")) {
			return columnExpr;
		}
		String alias = getPrimaryEntityAliasForFilterQualification(module);
		if (alias == null) {
			return columnExpr;
		}
		return alias + "." + trimmed;
	}

	@SuppressWarnings("unused")
	private int countOccurrences(String str, String subStr) {
		if (str == null || subStr == null || subStr.isEmpty()) {
			return 0;
		}
		int count = 0;
		int idx = 0;
		while ((idx = str.indexOf(subStr, idx)) != -1) {
			count++;
			idx += subStr.length();
		}
		return count;
	}

	/**
	 * Create a LIKE condition with proper collation for Arabic text search.
	 * Uses utf8mb4_unicode_ci collation which supports Arabic character ordering.
	 * 
	 * @param columnName The column name to search in
	 * @param query The search query
	 * @param useFuzzy Whether to use fuzzy search
	 * @return SQL LIKE condition with collation
	 */
	private String createSearchCondition(String columnName, String query, boolean useFuzzy) {
		if (useFuzzy) {
			// Fuzzy mode always uses LIKE
			return columnName + " LIKE ?";
		} else {
			boolean hasStartWildcard = query.startsWith("*");
			boolean hasEndWildcard = query.endsWith("*");

			if (hasStartWildcard || hasEndWildcard) {
				// User explicitly specified wildcards — honour them
				return columnName + " LIKE ?";
			} else {
				// Default: contains search (LIKE '%value%') so partial text matches work
				return columnName + " LIKE ?";
			}
		}
	}

	private String prepareSearchParameter(String query, boolean useFuzzy) {
		if (query == null || query.isEmpty()) {
			return useFuzzy ? "%" : "";
		}
		// Remove explicit wildcards from query (we'll add them in the pattern)
		String cleanQuery = query;
		boolean hasStartWildcard = cleanQuery.startsWith("*");
		boolean hasEndWildcard = cleanQuery.endsWith("*");

		if (hasStartWildcard && cleanQuery.length() > 1) {
			cleanQuery = cleanQuery.substring(1);
		} else if (hasStartWildcard) {
			cleanQuery = "";
		}
		if (hasEndWildcard && cleanQuery.length() > 1) {
			cleanQuery = cleanQuery.substring(0, cleanQuery.length() - 1);
		} else if (hasEndWildcard) {
			cleanQuery = "";
		}

		cleanQuery = cleanQuery.trim();
		if (cleanQuery.isEmpty()) {
			return useFuzzy ? "%" : "";
		}

		if (useFuzzy) {
			// For fuzzy search, add wildcards for pattern matching
			String pattern;

			if (hasStartWildcard && hasEndWildcard) {
				pattern = "%" + cleanQuery + "%";
			} else if (hasStartWildcard) {
				pattern = "%" + cleanQuery;
			} else if (hasEndWildcard) {
				pattern = cleanQuery + "%";
			} else {
				// No explicit wildcards - add % on both sides for fuzzy matching
				pattern = "%" + cleanQuery + "%";
			}

			return pattern;
		} else {
			// Non-fuzzy mode: still use LIKE for contains behaviour
			if (hasStartWildcard && hasEndWildcard) {
				return "%" + cleanQuery + "%";
			} else if (hasStartWildcard) {
				return "%" + cleanQuery;
			} else if (hasEndWildcard) {
				return cleanQuery + "%";
			} else {
				// Default: contains match (%value%) so "hhh" finds "hhh.." etc.
				return "%" + cleanQuery + "%";
			}
		}
	}

	private List<String> getSearchableColumnsForModule(String module) {
		if (module == null)
			return new ArrayList<>();
		String moduleLower = module.trim().toLowerCase();
		return switch (moduleLower) {
			case "dataset" -> List.of("d.PrimaryName", "d.RefNumber", "d.definition", "d.Usage");
			case "attribute" -> List.of("a.PrimaryName", "a.RefNumber", "a.Definition", "a.Business_Logic");
			case "system" -> List.of("s.Name", "s.Long_Name", "s.AssetID", "s.Description");
			case "glossary" -> List.of("g.Name", "g.Description", "g.Business_Logic", "ga.name");
			case "people" -> List.of("CONCAT(p.First_Name, ' ', p.Last_Name)", "p.First_Name", "p.Last_Name", "p.Email");
			case "interface" -> List.of("i.Name", "i.Ref_number", "i.Description");
			case "role" -> List.of("orl.PrimaryName", "orl.description", "CONCAT(p.First_Name, ' ', p.Last_Name)");
			case "orgunit", "org-unit" -> List.of("ou.Name", "ou.Reference", "ou.Description");
			case "process" -> List.of("pr.primaryname", "pr.refnumber", "pr.description");
			case "project" -> List.of("prj.primaryname", "prj.refnumber", "prj.description");
			case "product" -> List.of("prd.primaryname", "prd.longname", "prd.description");
			case "policy" -> List.of("po.PrimaryName", "po.refNumber", "po.Description");
			case "legal-entity", "legalentity", "legal" -> List.of("l.ShortName", "l.LongName", "l.Description");
			case "business-area" -> List.of("ba.PrimaryName", "ba.Description");
			case "capability" -> List.of("c.PrimaryName", "c.RefNumber", "c.Description");
			case "client" -> List.of("c.PrimaryName", "c.LongName", "c.Description");
			case "committee" -> List.of("c.PrimaryName", "c.RefNumber", "c.Description");
			case "geography" -> List.of("g.PrimaryName", "g.Description");
			case "regulation" -> List.of("r.primaryName", "r.RefNumber", "r.ShortName", "r.Description");
			case "regulator" -> List.of("reg.PrimaryName", "reg.ShortName", "reg.Description");
			case "regulatory-theme", "regulatorytheme" -> List.of("rt.PrimaryName", "rt.RefNumber", "rt.ShortName", "rt.Description");
			case "change-request", "changerequest" -> List.of("cr.PrimaryName", "cr.Reference", "cr.Summary");
			case "activetasks", "active-tasks", "activetask" ->
				List.of("name", "title", "objectType", "object", "owner");
			default -> List.of(getNameColumnForModule(module));
		};
	}

	/**
	 * Map from frontend "Search in" field keys to the DB column expressions used
	 * by getSearchableColumnsForModule for each module.
	 * Each entry: module → (fieldKey → DB column expression).
	 */
	private static final Map<String, Map<String, String>> FIELD_KEY_TO_COLUMN;
	static {
		Map<String, Map<String, String>> m = new HashMap<>();
		m.put("dataset",          Map.of("name","d.PrimaryName","ref","d.RefNumber","definition","d.definition","usage","d.Usage"));
		m.put("attribute",        Map.of("name","a.PrimaryName","ref","a.RefNumber","definition","a.Definition","businessLogic","a.Business_Logic"));
		m.put("system",           Map.of("name","s.Name","longName","s.Long_Name","assetId","s.AssetID","description","s.Description"));
		m.put("glossary",         Map.of("name","g.Name","ref","g.Ref_Number","definition","g.Description","businessLogic","g.Business_Logic","aliasName","ga.name"));
		m.put("people",           Map.of("name","CONCAT(p.First_Name, ' ', p.Last_Name)","firstName","p.First_Name","lastName","p.Last_Name","email","p.Email"));
		m.put("interface",        Map.of("name","i.Name","ref","i.Ref_number","description","i.Description"));
		m.put("role",             Map.of("name","orl.PrimaryName","description","orl.description"));
		m.put("orgunit",          Map.of("ref","ou.Reference","name","ou.Name","description","ou.Description"));
		m.put("org-unit",         Map.of("ref","ou.Reference","name","ou.Name","description","ou.Description"));
		m.put("process",          Map.of("name","pr.primaryname","ref","pr.refnumber","description","pr.description"));
		m.put("project",          Map.of("ref","prj.refnumber","name","prj.primaryname","description","prj.description"));
		m.put("product",          Map.of("name","prd.primaryname","longName","prd.longname","description","prd.description"));
		m.put("policy",           Map.of("ref","po.refNumber","name","po.PrimaryName","description","po.Description"));
		m.put("legal-entity",     Map.of("name","l.ShortName","longName","l.LongName","description","l.Description"));
		m.put("legalentity",      Map.of("name","l.ShortName","longName","l.LongName","description","l.Description"));
		m.put("business-area",    Map.of("name","ba.PrimaryName","description","ba.Description"));
		m.put("capability",       Map.of("ref","c.RefNumber","name","c.PrimaryName","description","c.Description"));
		m.put("client",           Map.of("name","c.PrimaryName","description","c.Description"));
		m.put("committee",        Map.of("ref","c.RefNumber","name","c.PrimaryName","description","c.Description"));
		m.put("geography",        Map.of("name","g.PrimaryName","description","g.Description"));
		m.put("regulation",       Map.of("name","r.primaryName","ref","r.RefNumber","shortName","r.ShortName","description","r.Description"));
		m.put("regulator",        Map.of("name","reg.PrimaryName","shortName","reg.ShortName","description","reg.Description"));
		m.put("regulatory-theme", Map.of("name","rt.PrimaryName","ref","rt.RefNumber","shortName","rt.ShortName","description","rt.Description"));
		m.put("regulatorytheme",  Map.of("name","rt.PrimaryName","ref","rt.RefNumber","shortName","rt.ShortName","description","rt.Description"));
		m.put("change-request",   Map.of("name","cr.PrimaryName","summary","cr.Summary"));
		m.put("changerequest",    Map.of("name","cr.PrimaryName","summary","cr.Summary"));
		m.put("activetasks",      Map.of("name","name","title","title","objectType","objectType","object","object","owner","owner"));
		m.put("active-tasks",     Map.of("name","name","title","title","objectType","objectType","object","object","owner","owner"));
		FIELD_KEY_TO_COLUMN = Collections.unmodifiableMap(m);
	}

	/**
	 * Filter the searchable columns for a module based on the user's "Search in" selection.
	 * If searchFields is null or empty, returns all searchable columns (default behaviour).
	 * Otherwise returns only the columns whose field key is mapped to true.
	 *
	 * @param module       Module name (e.g. "dataset", "glossary")
	 * @param searchFields Map from field key to enabled flag, or null
	 * @return filtered list of DB column expressions
	 */
	public List<String> getSearchableColumnsForModuleFiltered(String module, Map<String, Boolean> searchFields) {
		List<String> allColumns = getSearchableColumnsForModule(module);
		if (searchFields == null || searchFields.isEmpty()) {
			return allColumns;
		}
		String moduleLower = module == null ? "" : module.trim().toLowerCase();
		Map<String, String> keyToCol = FIELD_KEY_TO_COLUMN.get(moduleLower);
		if (keyToCol == null) {
			return allColumns; // No mapping known — return all
		}
		List<String> filtered = new ArrayList<>();
		for (Map.Entry<String, Boolean> entry : searchFields.entrySet()) {
			if (Boolean.TRUE.equals(entry.getValue())) {
				String col = keyToCol.get(entry.getKey());
				if (col != null && !filtered.contains(col)) {
					filtered.add(col);
				}
			}
		}
		// If nothing was selected (all false), fall back to all columns so we don't return zero results
		return filtered.isEmpty() ? allColumns : filtered;
	}

	/**
	 * Get possible module primarynames (facet names) as stored in the module table.
	 * Returns multiple possible names to handle variations in how module names are stored
	 * (e.g., "Dataset" vs "Data Sets", "Business Area" vs "Business Areas").
	 */
	private List<String> getModuleFacetNames(String module) {
		if (module == null) return null;
		String normalized = normalizeModuleName(module);
		return switch (normalized) {
			case "dataset" -> List.of("Dataset", "Data Sets", "Data Set", "Datasets");
			case "attribute" -> List.of("Attribute", "Attributes");
			case "system" -> List.of("System", "Systems");
			case "glossary" -> List.of("Glossary", "Glossaries");
			case "people" -> List.of("People", "Person");
			case "interface" -> List.of("System Interface", "Interface", "Interfaces");
			case "org_unit" -> List.of("Org Unit", "Org Units", "Organization Unit");
			case "process" -> List.of("Process", "Processes");
			case "project" -> List.of("Project", "Projects");
			case "product" -> List.of("Product", "Products");
			case "policy" -> List.of("Policy", "Policies");
			case "legal_entity" -> List.of("Legal Entity", "Legal Entities");
			case "business_area" -> List.of("Business Areas", "Business Area");
			case "capability" -> List.of("Capability", "Capabilities");
			case "client" -> List.of("Client", "Clients");
			case "committee" -> List.of("Committee", "Committees");
			case "geography" -> List.of("Geography", "Geographies");
			case "regulation" -> List.of("Regulation", "Regulations");
			case "regulator" -> List.of("Regulator", "Regulators");
			case "regulatory_theme" -> List.of("Regulatory Theme", "Regulatory Themes");
			case "role" -> List.of("Role", "Roles");
			default -> null;
		};
	}

	/**
	 * Get the actual object ID column for a module (primary key of the facet record).
	 * Unlike getIdColumnForModule which may return special columns for segment filtering,
	 * this returns the actual primary key used as Facet_Object_ID in Custom_Field_Data.
	 */
	private String getFacetObjectIdColumn(String module) {
		if (module == null) return null;
		String normalized = normalizeModuleName(module);
		return switch (normalized) {
			case "dataset" -> "d.ID";
			case "attribute" -> "a.ID";
			case "system" -> "s.id";
			case "glossary" -> "g.ID";
			case "people" -> "p.ID";
			case "interface" -> "i.id";
			case "role" -> "oxp.ID";
			case "org_unit" -> "ou.ID";
			case "process" -> "pr.id";
			case "project" -> "prj.id";
			case "product" -> "prd.id";
			case "policy" -> "po.ID";
			case "legal_entity" -> "l.ID";
			case "business_area" -> "ba.ID";
			case "capability" -> "c.ID";
			case "client" -> "c.ID";
			case "committee" -> "c.ID";
			case "geography" -> "g.ID";
			case "regulation" -> "r.ID";
			case "regulator" -> "reg.ID";
			case "regulatory_theme" -> "rt.ID";
			default -> null;
		};
	}

	/**
	 * Build an EXISTS subquery condition that searches through searchable custom field values.
	 * Handles text/numeric values (Custom_Field_Value) and enum values (dropdown/multiselect).
	 *
	 * @param module     The module being searched
	 * @param query      The search query
	 * @param useFuzzy   Whether to use fuzzy search
	 * @param parameters List to add prepared statement parameters to
	 * @return SQL EXISTS condition, or null if not applicable
	 */
	private String buildCustomFieldSearchCondition(String module, String query, boolean useFuzzy, List<Object> parameters) {
		List<String> facetNames = getModuleFacetNames(module);
		String objectIdColumn = getFacetObjectIdColumn(module);

		if (facetNames == null || facetNames.isEmpty() || objectIdColumn == null) {
			System.out.println("[QueryBuilder] CF search skipped for module '" + module
				+ "': facetNames=" + facetNames + ", objectIdColumn=" + objectIdColumn);
			return null;
		}
		System.out.println("[QueryBuilder] CF search enabled for module '" + module
			+ "': facetNames=" + facetNames + ", objectIdColumn=" + objectIdColumn + ", query='" + query + "'");

		StringBuilder sb = new StringBuilder();
		sb.append("EXISTS (SELECT 1 FROM Custom_Field_Data cfd_s ");
		sb.append("INNER JOIN Custom_Field_Metadata cfm_s ON cfd_s.Custom_Field_Metadata_ID = cfm_s.ID ");
		sb.append("INNER JOIN module m_s ON cfm_s.Module_ID = m_s.ID ");
		sb.append("LEFT JOIN Custom_Field_Enum cfe_s ON cfe_s.ID = cfd_s.Custom_Field_Enum_ID ");
		sb.append("WHERE m_s.primaryname IN (");
		for (int i = 0; i < facetNames.size(); i++) {
			if (i > 0) sb.append(", ");
			sb.append("?");
			parameters.add(facetNames.get(i));
		}
		sb.append(") ");
		sb.append("AND cfm_s.is_Searchable = 1 ");
		sb.append("AND cfd_s.Facet_Object_ID = ").append(objectIdColumn).append(" ");
		sb.append("AND (");

		if (useFuzzy) {
			List<String> fuzzyPatterns = FuzzySearchUtil.generateFuzzyPatterns(query);
			boolean first = true;
			for (String pattern : fuzzyPatterns) {
				if (!first) sb.append(" OR ");
				sb.append("LOWER(cfd_s.Custom_Field_Value) LIKE LOWER(?)");
				parameters.add(pattern);
				first = false;
			}
			for (String pattern : fuzzyPatterns) {
				sb.append(" OR LOWER(cfe_s.EnumValue) LIKE LOWER(?)");
				parameters.add(pattern);
			}
		} else {
			String searchParam = "%" + query.replace("*", "") + "%";
			sb.append("LOWER(cfd_s.Custom_Field_Value) LIKE LOWER(?)");
			parameters.add(searchParam);
			sb.append(" OR LOWER(cfe_s.EnumValue) LIKE LOWER(?)");
			parameters.add(searchParam);
		}

		sb.append("))");
		return sb.toString();
	}

	/**
	 * Variant of buildCustomFieldSearchCondition that restricts the search to a
	 * specific set of custom field IDs (from the user's "Search in" selection).
	 *
	 * @param module        The module name (e.g. "dataset")
	 * @param query         The search keyword
	 * @param useFuzzy      Whether to use fuzzy matching
	 * @param parameters    Prepared statement parameter list
	 * @param enabledCfIds  List of Custom_Field_Metadata IDs the user has enabled.
	 *                      null  → search all is_Searchable=1 fields (same as original method).
	 *                      empty → user turned off all CFs; return null (skip CF search).
	 * @return SQL EXISTS condition, or null if not applicable
	 */
	private String buildCustomFieldSearchConditionFiltered(String module, String query, boolean useFuzzy,
			List<Object> parameters, List<Integer> enabledCfIds) {
		// If the caller explicitly provided an empty list, the user has deselected all CFs
		if (enabledCfIds != null && enabledCfIds.isEmpty()) {
			return null;
		}
		// null means "no restriction" — delegate to the original method
		if (enabledCfIds == null) {
			return buildCustomFieldSearchCondition(module, query, useFuzzy, parameters);
		}
		// Build like the original but with an extra AND cfm_s.ID IN (...)
		List<String> facetNames = getModuleFacetNames(module);
		String objectIdColumn = getFacetObjectIdColumn(module);

		if (facetNames == null || facetNames.isEmpty() || objectIdColumn == null) {
			return null;
		}

		StringBuilder sb = new StringBuilder();
		sb.append("EXISTS (SELECT 1 FROM Custom_Field_Data cfd_s ");
		sb.append("INNER JOIN Custom_Field_Metadata cfm_s ON cfd_s.Custom_Field_Metadata_ID = cfm_s.ID ");
		sb.append("INNER JOIN module m_s ON cfm_s.Module_ID = m_s.ID ");
		sb.append("LEFT JOIN Custom_Field_Enum cfe_s ON cfe_s.ID = cfd_s.Custom_Field_Enum_ID ");
		sb.append("WHERE m_s.primaryname IN (");
		for (int i = 0; i < facetNames.size(); i++) {
			if (i > 0) sb.append(", ");
			sb.append("?");
			parameters.add(facetNames.get(i));
		}
		sb.append(") ");
		sb.append("AND cfm_s.is_Searchable = 1 ");
		// Restrict to the user-selected CF IDs
		sb.append("AND cfm_s.ID IN (");
		for (int i = 0; i < enabledCfIds.size(); i++) {
			if (i > 0) sb.append(", ");
			sb.append("?");
			parameters.add(enabledCfIds.get(i));
		}
		sb.append(") ");
		sb.append("AND cfd_s.Facet_Object_ID = ").append(objectIdColumn).append(" ");
		sb.append("AND (");

		if (useFuzzy) {
			List<String> fuzzyPatterns = FuzzySearchUtil.generateFuzzyPatterns(query);
			boolean first = true;
			for (String pattern : fuzzyPatterns) {
				if (!first) sb.append(" OR ");
				sb.append("LOWER(cfd_s.Custom_Field_Value) LIKE LOWER(?)");
				parameters.add(pattern);
				first = false;
			}
			for (String pattern : fuzzyPatterns) {
				sb.append(" OR LOWER(cfe_s.EnumValue) LIKE LOWER(?)");
				parameters.add(pattern);
			}
		} else {
			String searchParam = "%" + query.replace("*", "") + "%";
			sb.append("LOWER(cfd_s.Custom_Field_Value) LIKE LOWER(?)");
			parameters.add(searchParam);
			sb.append(" OR LOWER(cfe_s.EnumValue) LIKE LOWER(?)");
			parameters.add(searchParam);
		}

		sb.append("))");
		return sb.toString();
	}

	private String getNameColumnForModule(String moduleRaw) {
		if (moduleRaw == null)
			return null;
		String module = moduleRaw.trim().toLowerCase();
		return switch (module) {
			case "dataset" -> "d.PrimaryName";
			case "attribute" -> "a.PrimaryName";
			case "system" -> "s.Name";
			case "glossary" -> "g.Name";
			case "people" -> "CONCAT(p.First_Name, ' ', p.Last_Name)";
			case "interface" -> "i.Name";
			case "role" -> "orl.PrimaryName";
			case "orgunit", "org-unit" -> "ou.Name";
			case "process" -> "pr.primaryname";
			case "project" -> "prj.primaryname";
			case "product" -> "prd.primaryname";
			case "policy" -> "po.PrimaryName";
			case "legal-entity", "legalentity", "legal" -> "l.ShortName";
			case "business-area" -> "ba.PrimaryName";
			case "capability" -> "c.PrimaryName";
			case "client" -> "c.PrimaryName";
			case "committee" -> "c.PrimaryName";
			case "geography" -> "g.PrimaryName";
			case "regulation" -> "r.primaryName";
			case "regulator" -> "reg.PrimaryName";
			case "regulatory-theme", "regulatorytheme" -> "rt.PrimaryName";
			default -> null;
		};
	}

	private String getIdColumnForModule(String moduleRaw) {
		if (moduleRaw == null)
			return null;
		String module = normalizeModuleName(moduleRaw);
		return switch (module) {
			case "dataset" -> "d.ID";
			// Attributes inherit segment from their dataset, so segment filtering must use
			// dataset_id
			case "attribute" -> "a.Dataset_ID";
			case "system" -> "s.id";
			case "glossary" -> "g.ID";
			case "people" -> "p.ID";
			case "interface" -> "i.id";
			case "role" -> "oxp.ID";
			case "org_unit", "orgunit" -> "ou.ID";
			case "process" -> "pr.id";
			case "project" -> "prj.id";
			case "product" -> "prd.id";
			case "policy" -> "po.ID";
			case "legal_entity", "legalentity", "legal" -> "l.ID";
			case "business_area" -> "ba.ID";
			case "capability" -> "c.ID";
			case "client" -> "c.ID";
			case "committee" -> "c.ID";
			case "geography" -> "g.ID";
			case "regulation" -> "r.ID";
			case "regulator" -> "reg.ID";
			case "regulatory_theme", "regulatorytheme" -> "rt.ID";
			case "active_tasks", "activetasks", "activetask" -> "taskId";
			default -> null;
		};
	}

	private String getSqlForModule(String moduleRaw, Integer userId) {
		String module = moduleRaw == null ? "" : moduleRaw.trim().toLowerCase();
		return switch (module) {
			case "role" -> buildOptimizedRoleQuery();
			case "dataset" -> {
				String baseSql = "SELECT \n" +
						"    d.ID AS ID,\n" +
						"    d.RefNumber AS 'Ref.',\n" +
						"    d.PrimaryName AS Name,\n" +
						"    d.ID AS 'Name_ID',\n" +
						"    d.definition AS Definition,\n" +
						"    dt.PrimaryName AS Type,\n" +
						"    s.PrimaryName AS 'BUDG Status',\n" +
						"    l.PrimaryName AS Lifecycle,\n" +
						"    sys.Name AS 'System Short Name',\n" +
						"    sys.ID AS 'System Short Name_ID',\n" +
						"    g.Name AS 'Glossary Name',\n" +
						"    g.ID AS 'Glossary Name_ID',\n" +
						"    CONCAT(p.First_Name, ' ', p.Last_Name) AS 'Created By',\n" +
						"    p.ID AS 'Created By_ID',\n" +
						"    d.CreateDatetime AS 'Created Date',\n" +
						"    d.LastUpdateDatetime AS 'Last Updated',\n" +
						"    v.Name AS 'BUDG Viewing',\n" +
						"    d.`Usage` AS `Usage`,\n" +
						"    NULL AS 'Last Approved Date'\n" +
						"FROM dataset d\n" +
						"LEFT JOIN status s ON d.status = s.ID\n" +
						"LEFT JOIN viewing v ON d.AccessControlType = v.ID\n" +
						"LEFT JOIN people p ON d.Createdby_ID = p.ID\n" +
						"LEFT JOIN glossary g ON d.glossary = g.ID\n" +
						"LEFT JOIN dataset_lifecycle l ON d.lifecycle = l.ID\n" +
				"LEFT JOIN dataset_type dt ON d.DatasetType = dt.ID\n" +
				"LEFT JOIN system sys ON d.MasterSource = sys.ID\n";
				String finalSql = applyCommonFilters(baseSql, "d", "DeletedDatetime", "dataset", userId, true);
				yield finalSql + "\nORDER BY d.ID DESC";
			}
			case "attribute" -> {
				String baseSql = "SELECT \n" +
						"    a.ID AS ID,\n" +
						"    a.RefNumber AS 'Ref.',\n" +
						"    a.PrimaryName AS Name,\n" +
						"    a.ID AS 'Name_ID',\n" +
						"    a.Definition AS Definition,\n" +
						"    NULL AS 'Review Status',\n" +
						"    a.Confidence_score AS 'Confidence Score (%)',\n" +
						"    d.PrimaryName AS 'Data Set Name',\n" +
						"    d.ID AS 'Data Set Name_ID',\n" +
						"    sys.Name AS 'System Short Name',\n" +
						"    sys.ID AS 'System Short Name_ID',\n" +
						"    g.Name AS 'Glossary Name',\n" +
						"    g.ID AS 'Glossary Name_ID',\n" +
						"    GROUP_CONCAT(aan.Name SEPARATOR ', ') AS 'DB Field Name',\n" +
						"    CONCAT(dt.PrimaryName, ' (', a.DataLength, ')') AS 'Data Type ( Data Length )',\n" +
						"    oe.PrimaryName AS Origin,\n" +
						"    er.PrimaryName AS 'Editability Role',\n" +
						"    e.PrimaryName AS Editability,\n" +
						"    r.PrimaryName AS Requirement,\n" +
						"    CONCAT(p.First_Name, ' ', p.Last_Name) AS 'Created By',\n" +
						"    p.ID AS 'Created By_ID',\n" +
						"    a.CreatedDatetime AS 'Created Date',\n" +
						"    a.Last_UpdateDatetime AS 'Last Updated',\n" +
						"    a.Business_Logic AS 'Business Logic'\n" +
						"FROM attribute a\n" +
						"LEFT JOIN dataset d ON a.Dataset_ID = d.ID\n" +
						"LEFT JOIN system sys ON d.MasterSource = sys.ID\n" +
						"LEFT JOIN glossary g ON a.Glossary_ID = g.ID\n" +
						"LEFT JOIN requirement r ON a.Requirement_ID = r.ID\n" +
						"LEFT JOIN people p ON a.CreatedBy = p.ID\n" +
						"LEFT JOIN attribute_origination oe ON a.Origination = oe.ID\n" +
						"LEFT JOIN attribute_editability e ON a.Editability = e.ID\n" +
						"LEFT JOIN attribute_edit_role er ON a.Editability_role = er.ID\n" +
						"LEFT JOIN attribute_alias_name aan ON aan.AttributeID = a.ID\n" +
						"LEFT JOIN attribute_datatype dt ON a.Data_type_ID = dt.ID  \n" +
						"GROUP BY \n" +
						"    a.ID, a.PrimaryName, a.RefNumber, a.Definition, \n" +
						"    a.Business_Logic, a.Confidence_score, a.DataLength,\n" +
						"    a.CreatedDatetime, a.Last_UpdateDatetime, a.Dataset_ID,\n" +
						"    p.First_Name, p.Last_Name, p.ID,\n" +
						"    d.PrimaryName, d.ID,\n" +
						"    sys.Name, sys.ID,\n" +
						"    g.Name, g.ID,\n" +
						"    r.PrimaryName,\n" +
						"    oe.PrimaryName,\n" +
						"    e.PrimaryName,\n" +
						"    er.PrimaryName,\n" +
						"    dt.PrimaryName\n";
				String finalSql = applyCommonFilters(baseSql, "a", "DeletedDatetime", "attribute", userId, true);
				yield finalSql + "\nORDER BY a.ID DESC";
			}
			case "system" -> {
				String baseSql = "SELECT \n" +
						"    s.id AS ID,\n" +
						"    s.Name AS 'Short Name',\n" +
						"    s.id AS 'Short Name_ID',\n" +
						"    s.Description AS Description,\n" +
						"    t.Name AS Type,\n" +
						"    s.URL AS URL,\n" +
						"    s.External AS External,\n" +
						"    st.PrimaryName AS 'BUDG Status',\n" +
						"    s.Long_Name AS 'Long Name',\n" +
						"    pr.Name AS 'Parent Short Name',\n" +
						"    pr.id AS 'Parent Short Name_ID',\n" +
						"    l.Name AS Lifecycle,\n" +
						"    c.Name AS Classification,\n" +
						"    CONCAT(p.First_Name, ' ', p.Last_Name) AS 'Created By',\n" +
						"    p.ID AS 'Created By_ID',\n" +
						"    s.Created_Datetime AS 'Created Date',\n" +
						"    s.Last_Updated_Datetime AS 'Last Updated',\n" +
						"    v.Name AS 'BUDG Viewing',\n" +
						"    CONCAT(\n" +
						"        CASE WHEN COALESCE(s.Confidentiality_Rating, 0) = 0 THEN '-' ELSE CAST(s.Confidentiality_Rating AS CHAR) END,\n"
						+
						"        '-',\n" +
						"        CASE WHEN COALESCE(s.Integrity_Rating, 0) = 0 THEN '-' ELSE CAST(s.Integrity_Rating AS CHAR) END,\n"
						+
						"        '-',\n" +
						"        CASE WHEN COALESCE(s.Availability_Rating, 0) = 0 THEN '-' ELSE CAST(s.Availability_Rating AS CHAR) END\n"
						+
						"    ) AS 'CIA Rating',\n" +
						"    s.AssetID AS 'Asset ID',\n" +
						"    NULL AS 'Last Approved Date'\n" +
						"FROM system s\n" +
						"LEFT JOIN status st ON s.status = st.ID\n" +
						"LEFT JOIN viewing v ON s.is_Public = v.ID\n" +
						"LEFT JOIN people p ON s.CreatedBy_ID = p.ID\n" +
						"LEFT JOIN system_classification c ON s.Classification = c.ID\n" +
						"LEFT JOIN system_lifecycle l ON s.Lifecycle = l.ID\n" +
						"LEFT JOIN system_type t ON s.Type = t.ID\n" +
						"LEFT JOIN system pr ON s.parent_id = pr.id\n";
				String finalSql = applyCommonFilters(baseSql, "s", "Deleted_datetime", "system", userId, true);
				yield finalSql + "\nORDER BY s.id DESC";
			}
			case "glossary" -> {
				String baseSql = "SELECT \n" +
						"  g.ID AS ID,\n" +
						"  g.Ref_Number AS 'Ref.',\n" +
						"  g.Name AS Name,\n" +
						"  g.ID AS 'Name_ID',\n" +
						"  gt.Name AS Type,\n" +
						"  g.Description AS Definition,\n" +
						"  COALESCE(GROUP_CONCAT(ga.name ORDER BY ga.name SEPARATOR ', '), '') AS 'Alias Names',\n" +
						"  COALESCE(pr.Name, 'Top Level') AS 'Parent Name',\n" +
						"  pr.ID AS 'Parent Name_ID',\n" +
						"  pt.Name AS 'Parent Type',\n" +
						"  k.Name AS KDE,\n" +
						"  l.Name AS Lifecycle,\n" +
						"  s.primaryname AS 'BUDG Status',\n" +
						"  NULL AS 'Security Classification',\n" +
						"  CONCAT(\n" +
						"    CASE WHEN COALESCE(g.Confidentiality_Rating, 0) = 0 THEN '-' ELSE CAST(g.Confidentiality_Rating AS CHAR) END,\n"
						+
						"    '-',\n" +
						"    CASE WHEN COALESCE(g.Integrity_Rating, 0) = 0 THEN '-' ELSE CAST(g.Integrity_Rating AS CHAR) END,\n"
						+
						"    '-',\n" +
						"    CASE WHEN COALESCE(g.Availability_Rating, 0) = 0 THEN '-' ELSE CAST(g.Availability_Rating AS CHAR) END\n"
						+
						"  ) AS 'CIA Rating',\n" +
						"  CONCAT(p.first_name, ' ', p.last_name) AS 'Created By',\n" +
						"  p.ID AS 'Created By_ID',\n" +
						"  CONCAT(p2.first_name, ' ', p2.last_name) AS 'Last Updated By',\n" +
						"  p2.ID AS 'Last Updated By_ID',\n" +
						"  g.Created_Datetime AS 'Created Date',\n" +
						"  g.Last_Updated_Datetime AS 'Last Updated',\n" +
						"  v.name AS 'BUDG Viewing',\n" +
						"  g.LDM AS 'LDM Reference',\n" +
						"  g.Business_Logic AS 'Business Logic',\n" +
						"  g.Examples AS Examples,\n" +
						"  g.Format AS 'Format Description',\n" +
						"  f.Name AS 'Format Type',\n" +
						"  NULL AS 'Last Approved Date'\n" +
						"FROM glossary g\n" +
						"LEFT JOIN status              s  ON g.Status            = s.id\n" +
						"LEFT JOIN viewing             v  ON g.Is_Public         = v.id\n" +
						"LEFT JOIN people              p  ON g.CreatedBy_ID      = p.id\n" +
						"LEFT JOIN people              p2 ON g.Last_updated_userID = p2.id\n" +
						"LEFT JOIN glossary_lifecycle  l  ON g.Lifecycle         = l.id\n" +
						"LEFT JOIN glossary_format_type f ON g.Format_type       = f.id\n" +
						"LEFT JOIN glossary_kde_type   k  ON g.KDE               = k.id\n" +
						"LEFT JOIN glossary            pr ON g.Parent_ID         = pr.id\n" +
						"LEFT JOIN glossary_type       pt ON pr.Type             = pt.id\n" +
						"LEFT JOIN glossary_type       gt ON g.Type             = gt.id\n" +
						"LEFT JOIN glossary_alias_names ga ON g.ID               = ga.Glossary_id\n" +
						"GROUP BY \n" +
						"  g.ID, g.Name, g.Ref_Number, g.Description, g.Examples, g.Business_Logic,\n" +
						"  g.Format, g.LDM, g.Created_Datetime, g.Last_Updated_Datetime,\n" +
						"  p.first_name, p.last_name, p.ID, p2.first_name, p2.last_name, p2.ID,\n" +
						"  s.primaryname, v.name, l.Name, f.Name,\n" +
						"  k.Name, pr.Name, pr.ID, pt.Name,\n" +
						"  gt.Name, g.Availability_Rating, g.Integrity_Rating, g.Confidentiality_Rating\n";
				String finalSql = applyCommonFilters(baseSql, "g", "Deleted_datetime", "glossary", userId, true);
				yield finalSql + "\nORDER BY g.ID DESC";
			}
			case "people" -> {
				String baseSql = "SELECT \n" +
						"    p.ID AS ID,\n" +
						"    p.First_Name AS 'First Name',\n" +
						"    p.ID AS 'First Name_ID',\n" +
						"    p.Last_Name AS 'Last Name',\n" +
						"    p.ID AS 'Last Name_ID',\n" +
						"    p.Email AS Email,\n" +
						"    p.Function_Name AS Function,\n" +
						"    ou.Reference AS 'Org Unit Ref',\n" +
						"    ou.Name AS 'Org Unit',\n" +
						"    ou.ID AS 'Org Unit_ID',\n" +
						"    s.PrimaryName AS 'BUDG Status',\n" +
						"    r.primaryname AS 'Profile Name',\n" +
						"    p.last_User_LogIn AS 'Last Login',\n" +
						"    ls.Primary_Name AS Lifecycle,\n" +
						"    et.primary_Name AS 'Employee Type',\n" +
						"    pd.lan_id AS 'LAN ID',\n" +
						"    p.Created_Date AS 'Created Date',\n" +
						"    p.Last_Updated AS 'Last Updated'\n" +
						"FROM people p\n" +
						"LEFT JOIN status s ON p.status_id = s.ID\n" +
						"LEFT JOIN people_details pd ON p.ip_details = pd.id\n" +
						"LEFT JOIN employment_type et ON pd.employment_type = et.id\n" +
						"LEFT JOIN role r ON p.System_Role = r.id\n" +
						"LEFT JOIN org_unit ou ON p.Org_Unit_ID = ou.ID\n" +
						"LEFT JOIN people_lifecycle_status ls ON pd.lifecycle = ls.ID";
				String finalSql = applyCommonFilters(baseSql, "p", "Deleted_date", "people", userId, true);
				yield finalSql + "\nORDER BY p.ID DESC";
			}
			case "interface" -> {
				String baseSql = "SELECT \n" +
						"    i.id AS ID,\n" +
						"    i.Ref_number AS 'Ref.',\n" +
						"    i.Name AS Name,\n" +
						"    i.id AS 'Name_ID',\n" +
						"    i.Description AS Description,\n" +
						"    sys1.Name AS 'Source System Short Name',\n" +
						"    sys1.id AS 'Source System Short Name_ID',\n" +
						"    sys2.Name AS 'Target System Short Name',\n" +
						"    sys2.id AS 'Target System Short Name_ID',\n" +
						"    s.PrimaryName AS 'BUDG Status',\n" +
						"    a.Name AS Automation,\n" +
						"    f.Name AS Frequency,\n" +
						"    l.Name AS Lifecycle,\n" +
						"    i.Synchronisation_Control AS Synchronisation,\n" +
						"    i.Asset_ID AS 'Asset ID',\n" +
						"    c.Name AS Classification,\n" +
						"    tf.Name AS 'Transfer Format',\n" +
						"    tm.Name AS 'Transfer Method',\n" +
						"    CONCAT(p.First_Name, ' ', p.Last_Name) AS 'Created By',\n" +
						"    p.ID AS 'Created By_ID',\n" +
						"    i.Created_datetime AS 'Created Date',\n" +
						"    i.last_updatedtime AS 'Last Updated',\n" +
						"    v.Name AS 'BUDG Viewing'\n" +
						"FROM interface i\n" +
						"LEFT JOIN status s ON i.status_id = s.ID\n" +
						"LEFT JOIN viewing v ON i.is_Public = v.ID\n" +
						"LEFT JOIN people p ON i.createdBy_ID = p.ID\n" +
						"LEFT JOIN interface_classification c ON i.Classification_id = c.id\n" +
						"LEFT JOIN interface_lifecycle l ON i.Lifecycle_id = l.id\n" +
						"LEFT JOIN interface_frequency f ON i.Frequency_ID = f.id\n" +
						"LEFT JOIN interface_automation a ON i.Automation_ID = a.id\n" +
						"LEFT JOIN interface_transfer_format tf ON i.Transfer_Format_ID = tf.id\n" +
						"LEFT JOIN interface_transfer tm ON i.Transfer_Method_ID = tm.id\n" +
						"LEFT JOIN system sys1 ON i.Source_systemID = sys1.id\n" +
						"LEFT JOIN system sys2 ON i.Target_systemID = sys2.id\n";
				String finalSql = applyCommonFilters(baseSql, "i", "deleted_datetime", "interface", userId, true);
				yield finalSql + "\nORDER BY i.id DESC";
			}
			case "orgunit", "org-unit" -> {
				String baseSql = "SELECT \n" +
						"    ou.ID AS ID,\n" +
						"    ou.Reference AS 'Ref.',\n" +
						"    ou.Name AS Name,\n" +
						"    ou.ID AS 'Name_ID',\n" +
						"    COALESCE(pr.Name, 'Top Level') AS Parent,\n" +
						"    pr.ID AS 'Parent_ID',\n" +
						"    ou.Description AS Description,\n" +
						"    s.PrimaryName AS 'BUDG Status',\n" +
						"    ou.Created_Date AS 'Created Date',\n" +
						"    ou.last_updated_date AS 'Last Updated'\n" +
						"FROM org_unit ou\n" +
						"LEFT JOIN status s ON ou.status_id = s.ID\n" +
						"LEFT JOIN org_unit pr ON ou.Parent_ID = pr.ID "
						+ "AND (pr.deleted_Date IS NULL OR pr.deleted_Date = '')\n";
				String finalSql = applyCommonFilters(baseSql, "ou", "deleted_Date", "org_unit", userId, true);
				yield finalSql + "\nORDER BY ou.ID DESC";
			}
			case "process" -> {
				String baseSql = "SELECT \n" +
						"    pr.id AS ID,\n" +
						"    pr.refnumber AS 'Ref.',\n" +
						"    pr.primaryname AS Name,\n" +
						"    pr.id AS 'Name_ID',\n" +
						"    COALESCE(pr2.primaryname, 'Top Level') AS 'Parent Name',\n" +
						"    pr2.id AS 'Parent Name_ID',\n" +
						"    pr.description AS Description,\n" +
						"    l.primaryname AS Lifecycle,\n" +
						"    pa.primaryname AS Automation,\n" +
						"    c.primaryname AS Classification,\n" +
						"    s.PrimaryName AS 'BUDG Status',\n" +
						"    t.PrimaryName AS `Type`,\n" +
						"    v.Name AS 'BUDG Viewing',\n" +
						"    pr.step_type AS 'Step Type',\n" +
						"    CONCAT(p.First_Name, ' ', p.Last_Name) AS 'Created By',\n" +
						"    p.ID AS 'Created By_ID',\n" +
						"    pr.Createdatetime AS 'Created Date',\n" +
						"    pr.Lastupdatedatetime AS 'Last Updated',\n" +
						"    NULL AS 'Last Approved Date'\n" +
						"FROM process pr\n" +
						"LEFT JOIN status s ON pr.status = s.ID\n" +
						"LEFT JOIN viewing v ON pr.isPublic = v.ID\n" +
						"LEFT JOIN people p ON pr.createdby_id = p.ID\n" +
						"LEFT JOIN process_class c ON pr.processclass_id = c.id\n" +
						"LEFT JOIN process_lifecycle_status l ON pr.lifecycle_status = l.id\n" +
						"LEFT JOIN process_type t ON pr.type = t.id\n" +
						"LEFT JOIN process_automation pa ON pr.processautomation_id = pa.id\n" +
						"LEFT JOIN process pr2 ON pr.parentid = pr2.id";
				String finalSql = applyCommonFilters(baseSql, "pr", "DeletedDatetime", "process", userId, true);
				yield finalSql + "\nORDER BY pr.ID DESC";
			}
			case "project" -> {
				String baseSql = "SELECT \n" +
						"    prj.id AS ID,\n" +
						"    prj.primaryname AS Name,\n" +
						"    prj.refnumber AS Ref,\n" +
						"    prj.description AS Description,\n" +
						"    prj.startdate AS Start_Date,\n" +
						"    prj.enddate AS End_Date,\n" +
						"    prj.createdatetime AS Created_Date,\n" +
						"    prj.lastupdatedatetime AS Last_Updated,\n" +
						"    CONCAT(p.First_Name, ' ', p.Last_Name) AS Created_By,\n" +
						"    s.PrimaryName AS Status,\n" +
						"    v.Name AS Viewing,\n" +
						"    l.PrimaryName AS Lifecycle,\n" +
						"    r.PrimaryName AS RAG,\n" +
						"    t.PrimaryName AS Type,\n" +
						"    c.PrimaryName AS Classification,\n" +
						"    pr2.primaryname AS Parent,\n" +
						"    pr2.id AS Parent_ID\n" +
						"FROM project prj\n" +
						"LEFT JOIN status s ON prj.status = s.ID\n" +
						"LEFT JOIN viewing v ON prj.is_public = v.id\n" +
						"LEFT JOIN people p ON prj.createdby_id = p.ID\n" +
						"LEFT JOIN project_rag r ON prj.rag = r.id\n" +
						"LEFT JOIN project_comment_type t ON prj.project_type = t.id\n" +
						"LEFT JOIN project_lifecycle l ON prj.lifecycle_status = l.id\n" +
						"LEFT JOIN project_classification c ON prj.classification = c.id\n" +
						"LEFT JOIN project pr2 ON prj.parentid = pr2.id";
				String finalSql = applyCommonFilters(baseSql, "prj", "deletedatetime", "project", userId, true);
				yield finalSql + "\nORDER BY prj.ID DESC";
			}
			case "product" -> {
				String baseSql = "SELECT \n" +
						"    prd.id AS ID,\n" +
						"    prd.refnumber AS 'Ref.',\n" +
						"    prd.primaryname AS Name,\n" +
						"    prd.id AS 'Name_ID',\n" +
						"    prd.longname AS 'Long Name',\n" +
						"    prd.id AS 'Long Name_ID',\n" +
						"    COALESCE(pr2.primaryname, 'Top Level') AS Parent,\n" +
						"    pr2.id AS 'Parent_ID',\n" +
						"    prd.description AS Description,\n" +
						"    s.PrimaryName AS 'BUDG Status',\n" +
						"    l.primaryname AS Lifecycle,\n" +
						"    prd.Createdatetime AS 'Created Date',\n" +
						"    prd.Lastupdatedatetime AS 'Last Updated',\n" +
						"    v.Name AS 'BUDG Viewing'\n" +
						"FROM product prd\n" +
						"LEFT JOIN status s ON prd.status = s.ID\n" +
						"LEFT JOIN viewing v ON prd.is_Public = v.ID\n" +
						"LEFT JOIN product_lifecycle l ON prd.lifecycle_status = l.id\n" +
						"LEFT JOIN product pr2 ON prd.parent_id = pr2.id";
				String finalSql = applyCommonFilters(baseSql, "prd", "DeletedDatetime", "product", userId, true);
				yield finalSql + "\nORDER BY prd.ID DESC";
			}
			case "policy" -> {
				String baseSql = "SELECT \n" +
						"    po.ID AS ID,\n" +
						"    po.refNumber AS 'Ref.',\n" +
						"    po.PrimaryName AS Name,\n" +
						"    po.ID AS 'Name_ID',\n" +
						"    COALESCE(pr.PrimaryName, 'Top Level') AS 'Parent Name',\n" +
						"    pr.ID AS 'Parent Name_ID',\n" +
						"    po.Description AS Description,\n" +
						"    t.PrimaryName AS Type,\n" +
						"    s.PrimaryName AS 'BUDG Status',\n" +
						"    po.Internal AS Internal,\n" +
						"    l.PrimaryName AS Lifecycle,\n" +
						"    po.EffectiveDate AS 'Effective Date',\n" +
						"    po.EndDate AS 'End Date',\n" +
						"    v.Name AS 'BUDG Viewing',\n" +
						"    CONCAT(p.First_Name, ' ', p.Last_Name) AS 'Created By',\n" +
						"    p.ID AS 'Created By_ID',\n" +
						"    po.CreateDatetime AS 'Created Date',\n" +
						"    po.LastUpdateDatetime AS 'Last Updated'\n" +
						"FROM policy po\n" +
						"LEFT JOIN status s ON po.status = s.ID\n" +
						"LEFT JOIN viewing v ON po.isPublic = v.ID\n" +
						"LEFT JOIN people p ON po.CreatedBy_ID = p.ID\n" +
						"LEFT JOIN policy_lifecycle_status l ON po.Lifecycle_Status = l.id\n" +
						"LEFT JOIN policy_type t ON po.Policy_Type = t.id\n" +
						"LEFT JOIN policy pr ON po.ParentID = pr.ID";
				String finalSql = applyCommonFilters(baseSql, "po", "DeletedDatetime", "policy", userId, true);
				yield finalSql + "\nORDER BY po.ID DESC";
			}
			case "legal-entity", "legalentity", "legal" -> {
				String baseSql = "SELECT \n" +
						"    l.ID AS ID,\n" +
						"    l.ShortName AS 'Short Name',\n" +
						"    l.ID AS 'Short Name_ID',\n" +
						"    l.LongName AS 'Long Name',\n" +
						"    l.ID AS 'Long Name_ID',\n" +
						"    COALESCE(p.ShortName, 'Top Level') AS 'Parent Short Name',\n" +
						"    p.ID AS 'Parent Short Name_ID',\n" +
						"    COALESCE(p.LongName, 'Top Level') AS 'Parent Long Name',\n" +
						"    p.ID AS 'Parent Long Name_ID',\n" +
						"    l.Description AS Description,\n" +
						"    s.PrimaryName AS 'BUDG Status',\n" +
						"    l.CreateDatetime AS 'Created Date',\n" +
						"    l.LastUpdateDatetime AS 'Last Updated',\n" +
						"    v.Name AS 'BUDG Viewing'\n" +
						"FROM legal l\n" +
						"LEFT JOIN legal p ON l.Parent_ID = p.ID\n" +
						"LEFT JOIN status s ON l.Status = s.ID\n" +
						"LEFT JOIN viewing v ON l.Is_Public = v.ID";
				String finalSql = applyCommonFilters(baseSql, "l", "DeleteDatetime", "legal_entity", userId, true);
				yield finalSql + "\nORDER BY l.ID DESC";
			}
			case "business-area" -> {
				String baseSql = "SELECT \n" +
						"    ba.ID AS ID,\n" +
						"    ba.PrimaryName AS Name,\n" +
						"    ba.ID AS 'Name_ID',\n" +
						"    ba2.PrimaryName AS Parent,\n" +
						"    ba2.ID AS 'Parent_ID',\n" +
						"    ba.Description AS Description,\n" +
						"    l.PrimaryName AS Lifecycle,\n" +
						"    s.PrimaryName AS 'BUDG Status',\n" +
						"    ba.CreateDatetime AS 'Created Date',\n" +
						"    ba.LastUpdateDatetime AS 'Last Updated',\n" +
						"    v.Name AS 'BUDG Viewing'\n" +
						"FROM business_area ba\n" +
						"LEFT JOIN status s ON ba.Status = s.ID\n" +
						"LEFT JOIN viewing v ON ba.Is_Public = v.ID\n" +
						"LEFT JOIN business_area_lifecycle l ON ba.Lifecycle = l.ID\n" +
						"LEFT JOIN business_area ba2 ON ba.Parent_ID = ba2.ID";
				String finalSql = applyCommonFilters(baseSql, "ba", "deletedatetime", "business_area", userId, true);
				yield finalSql + "\nORDER BY ba.ID DESC";
			}
			case "capability" -> {
				String baseSql = "SELECT \n" +
						"    c.ID AS ID,\n" +
						"    c.RefNumber AS 'Ref.',\n" +
						"    c.PrimaryName AS Name,\n" +
						"    c.ID AS 'Name_ID',\n" +
						"    COALESCE(c2.PrimaryName, 'Top Level') AS Parent,\n" +
						"    c2.ID AS 'Parent_ID',\n" +
						"    c.Description AS Description,\n" +
						"    l.PrimaryName AS Lifecycle,\n" +
						"    s.PrimaryName AS 'BUDG Status',\n" +
						"    cc.PrimaryName AS Classification,\n" +
						"    ct.PrimaryName AS `Type`,\n" +
						"    c.CreateDatetime AS 'Created Date',\n" +
						"    c.LastUpdateDatetime AS 'Last Updated',\n" +
						"    v.Name AS 'BUDG Viewing'\n" +
						"FROM capability c\n" +
						"LEFT JOIN status s ON c.Status = s.ID\n" +
						"LEFT JOIN viewing v ON c.Is_Public = v.ID\n" +
						"LEFT JOIN capability_lifecyle l ON c.Lifecycle = l.ID\n" +
						"LEFT JOIN capability c2 ON c.Parent_ID = c2.ID\n" +
						"LEFT JOIN capability_classification cc ON c.Classification = cc.ID\n" +
						"LEFT JOIN capability_type ct ON c.Capability_Type = ct.ID";
				String finalSql = applyCommonFilters(baseSql, "c", "DeletedDatetime", "capability", userId, true);
				yield finalSql + "\nORDER BY c.ID DESC";
			}
			case "client" -> {
				String baseSql = "SELECT \n" +
						"    c.ID AS ID,\n" +
						"    c.PrimaryName AS Name,\n" +
						"    c.ID AS 'Name_ID',\n" +
						"    c.LongName AS 'Long Name',\n" +
						"    COALESCE(pc.PrimaryName, 'Top Level') AS Parent,\n" +
						"    pc.ID AS 'Parent_ID',\n" +
						"    c.Description AS Description,\n" +
						"    s.PrimaryName AS 'BUDG Status',\n" +
						"    cl.PrimaryName AS Lifecycle,\n" +
						"    c.CreateDatetime AS 'Created Date',\n" +
						"    c.LastUpdateDatetime AS 'Last Updated',\n" +
						"    v.Name AS 'BUDG Viewing'\n" +
						"FROM client c\n" +
						"LEFT JOIN status s ON c.Status = s.ID\n" +
						"LEFT JOIN viewing v ON c.IsPublic = v.ID\n" +
						"LEFT JOIN client_lifecycle cl ON c.Lifecycle = cl.ID\n" +
						"LEFT JOIN client pc ON c.Parent_ID = pc.ID";
				String finalSql = baseSql;
				yield finalSql + "\nWHERE c.ID IS NOT NULL\nORDER BY c.ID DESC";
			}
			case "committee" -> {
				String baseSql = "SELECT \n" +
						"    c.ID AS ID,\n" +
						"    c.RefNumber AS 'Ref.',\n" +
						"    c.PrimaryName AS Name,\n" +
						"    c.ID AS 'Name_ID',\n" +
						"    COALESCE(pc.PrimaryName, 'Top Level') AS Parent,\n" +
						"    pc.ID AS 'Parent_ID',\n" +
						"    c.Description AS Description,\n" +
						"    cl.PrimaryName AS Lifecycle,\n" +
						"    s.PrimaryName AS 'BUDG Status',\n" +
						"    c.CreateDatetime AS 'Created Date',\n" +
						"    c.LastUpdateDatetime AS 'Last Updated',\n" +
						"    cc.PrimaryName AS Classification,\n" +
						"    ct.PrimaryName AS `Type`,\n" +
						"    v.Name AS 'BUDG Viewing',\n" +
						"    CONCAT(p_created.First_Name, ' ', p_created.Last_Name) AS 'Created By',\n" +
						"    p_created.ID AS 'Created By_ID'\n" +
						"FROM committee c\n" +
						"LEFT JOIN status s ON c.Status = s.ID\n" +
						"LEFT JOIN viewing v ON c.Is_Public = v.ID\n" +
						"LEFT JOIN committee_lifecycle cl ON c.Lifecycle = cl.ID\n" +
						"LEFT JOIN committee_classification cc ON c.Classification = cc.ID\n" +
						"LEFT JOIN committee_type ct ON c.Committee_Type = ct.ID\n" +
						"LEFT JOIN committee pc ON c.Parent_ID = pc.ID\n" +
						"LEFT JOIN people p_created ON c.Created_By = p_created.ID";
				String finalSql = applyCommonFilters(baseSql, "c", "DeleteDatetime", "client", userId, true);
				yield finalSql + "\nORDER BY c.ID DESC";
			}
			case "geography" -> {
				String baseSql = "SELECT \n" +
						"    g.ID AS ID,\n" +
						"    g.PrimaryName AS Name,\n" +
						"    g.ID AS 'Name_ID',\n" +
						"    COALESCE(p.PrimaryName, 'Top Level') AS Parent,\n" +
						"    p.ID AS 'Parent_ID',\n" +
						"    g.Description AS Description,\n" +
						"    g.CreateDatetime AS 'Created Date',\n" +
						"    g.LastUpdateDatetime AS 'Last Updated'\n" +
						"FROM geography g\n" +
						"LEFT JOIN geography p ON g.ParentID = p.ID";
				String finalSql = applyCommonFilters(baseSql, "g", "DeletedDatetime", "geography", userId, true);
				yield finalSql + "\nORDER BY g.ID DESC";
			}
			case "regulation" -> {
				String baseSql = "SELECT \n" +
						"    r.ID AS ID,\n" +
						"    r.RefNumber AS 'Ref.',\n" +
						"    r.primaryName AS Name,\n" +
						"    r.ID AS 'Name_ID',\n" +
						"    COALESCE(pr.primaryName, 'Top Level') AS Parent,\n" +
						"    pr.ID AS 'Parent_ID',\n" +
						"    r.Description AS Description,\n" +
						"    rs.PrimaryName AS 'BUDG Status',\n" +
						"    rst.PrimaryName AS Stage,\n" +
						"    rm.PrimaryName AS Maturity,\n" +
						"    rp.PrimaryName AS Probability,\n" +
						"    rcl.PrimaryName AS 'Compliance Level',\n" +
						"    r.ComplianceDate AS 'Compliance Date',\n" +
						"    r.PublicationDate AS 'Publication Date',\n" +
						"    r.ShortName AS 'Short Name',\n" +
						"    r.CreateDatetime AS 'Created Date',\n" +
						"    r.LastUpdateDatetime AS 'Last Updated'\n" +
						"FROM regulation r\n" +
						"LEFT JOIN regulation pr ON r.Parent_ID = pr.ID\n" +
						"LEFT JOIN regulation_status rs ON r.RegulationStatus_ID = rs.ID\n" +
						"LEFT JOIN regulation_stage rst ON r.RegulationStage_ID = rst.ID\n" +
						"LEFT JOIN regulation_maturity rm ON r.RegulationMaturity_ID = rm.ID\n" +
						"LEFT JOIN regulation_probability rp ON r.RegulationProbability_ID = rp.ID\n" +
						"LEFT JOIN regulation_compliance_level rcl ON r.ComplianceLevel_ID = rcl.ID";
				String finalSql = applyCommonFilters(baseSql, "r", "DeletedDatetime", "regulation", userId, true);
				yield finalSql + "\nORDER BY r.ID DESC";
			}
			case "regulator" -> {
				String baseSql = "SELECT \n" +
						"    reg.ID AS ID,\n" +
						"    reg.PrimaryName AS Name,\n" +
						"    reg.ID AS 'Name_ID',\n" +
						"    reg.Description AS Description,\n" +
						"    reg.ShortName AS 'Short Name',\n" +
						"    reg.CreateDatetime AS 'Created Date',\n" +
						"    reg.LastUpdateDatetime AS 'Last Updated'\n" +
						"FROM regulator reg";
				String finalSql = applyCommonFilters(baseSql, "reg", "DeletedDatetime", "regulator", userId, true);
				yield finalSql + "\nORDER BY reg.ID DESC";
			}
			case "regulatory-theme", "regulatorytheme" -> {
				String baseSql = "SELECT \n" +
						"    rt.ID AS ID,\n" +
						"    rt.RefNumber AS 'Ref.',\n" +
						"    rt.PrimaryName AS Name,\n" +
						"    rt.ID AS 'Name_ID',\n" +
						"    COALESCE(pr.PrimaryName, 'Top Level') AS Parent,\n" +
						"    pr.ID AS 'Parent_ID',\n" +
						"    rt.Description AS Description,\n" +
						"    s.PrimaryName AS 'BUDG Status',\n" +
						"    rt.CreateDatetime AS 'Created Date',\n" +
						"    rt.LastUpdateDatetime AS 'Last Updated'\n" +
						"FROM regulatorytheme rt\n" +
						"LEFT JOIN status s ON rt.Status_ID = s.ID\n" +
						"LEFT JOIN regulatorytheme pr ON rt.Parent_ID = pr.ID";
				String finalSql = applyCommonFilters(baseSql, "rt", "DeletedDatetime", "regulatory_theme", userId, true);
				yield finalSql + "\nORDER BY rt.ID DESC";
			}
			case "change-request", "changerequest" -> {
				String baseSql = "SELECT \n" +
						"    cr.ID AS ID,\n" +
						"    cr.Reference AS 'Ref.',\n" +
						"    cr.PrimaryName AS Name,\n" +
						"    cr.ID AS 'Name_ID',\n" +
						"    cr.Summary AS Description,\n" +
						"    cr.CreateDatetime AS 'Created Date',\n" +
						"    cr.LastUpdateDatetime AS 'Last Updated'\n" +
						"FROM changerequest cr";
				String finalSql = applyCommonFilters(baseSql, "cr", "Deleted_At", "change_request", userId, true);
				yield finalSql + "\nORDER BY cr.ID DESC";
			}
			default -> null;
		};
	}

	/**
	 * Check if user is admin or superadmin.
	 * WebUsers should not see deleted status objects, but admin/superadmin can.
	 * 
	 * @param userId The user ID, or null for anonymous users
	 * @return true if user is admin or superadmin, false otherwise (including WebUsers and anonymous)
	 */
	private boolean isAdminOrSuperAdmin(Integer userId) {
		if (userId == null || userId <= 0) {
			return false; // Anonymous users are treated as WebUsers
		}
		
		// Check if user is superadmin
		try {
			if (SegmentAccessService.isSuperAdmin(userId)) {
				return true;
			}
		} catch (SQLException e) {
			// If check fails, continue to check admin status
		}
		
		// Check if user is admin (not superadmin)
		// Admin role is typically stored in people.System_Role pointing to role table
		// where role.primaryname = 'Admin' (case-insensitive)
		try {
			String sql = """
				SELECT r.primaryname AS role_name
				FROM people p
				LEFT JOIN role r ON p.System_Role = r.id
				WHERE p.ID = ?
			""";
			
			try (Connection conn = DatabaseConnection.getConnection();
				 PreparedStatement pstmt = conn.prepareStatement(sql)) {
				pstmt.setInt(1, userId);
				try (ResultSet rs = pstmt.executeQuery()) {
					if (rs.next()) {
						String roleName = rs.getString("role_name");
						if (roleName != null) {
							String normalizedRole = roleName.toLowerCase().trim();
							// Admin (not super admin) can also see deleted objects
							return normalizedRole.equals("admin");
						}
					}
				}
			}
		} catch (Exception e) {
			// If check fails, assume user is not admin
		}
		
		return false;
	}
	
	/**
	 * Get the status table alias used in SQL queries for a given module.
	 * Most modules use alias 's' for status table, but system uses 'st'.
	 * 
	 * @param module The module name
	 * @return The status table alias, or null if module doesn't have status
	 */
	private String getStatusAliasForModule(String module) {
		if (module == null) {
			return null;
		}
		
		String normalized = module.trim().toLowerCase();
		
		// Modules that don't have status (based on BulkDeleteDAO comments)
		if ("geography".equals(normalized) || "regulator".equals(normalized)) {
			return null;
		}
		
		// System module uses 'st' as status alias (see getSqlForModule)
		if ("system".equals(normalized)) {
			return "st";
		}
		
		// Most modules use 's' as status alias
		return "s";
	}
	
	/**
	 * Check if the SQL query has a status join with the given alias.
	 * 
	 * @param sql The SQL query
	 * @param statusAlias The status table alias to check for
	 * @return true if status join exists, false otherwise
	 */
	private boolean hasStatusJoin(String sql, String statusAlias) {
		if (sql == null || statusAlias == null) {
			return false;
		}
		
		// Check for LEFT JOIN status or JOIN status with the alias
		String pattern = "JOIN\\s+STATUS\\s+" + statusAlias.toUpperCase();
		return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(sql).find();
	}

	/**
	 * True if any filterGroup in searchGroups targets this module's main BUDG status FK column
	 * (same mapping as {@code mapFieldToColumn(module, "status")}).
	 */
	private boolean searchGroupsDefineEntityStatusFilter(String resultModule, JsonObject searchGroupsJson) {
		if (searchGroupsJson == null || !searchGroupsJson.has("searchGroups")
				|| !searchGroupsJson.get("searchGroups").isJsonArray()) {
			return false;
		}
		String resultNorm = normalizeModuleName(resultModule);
		JsonArray groups = searchGroupsJson.getAsJsonArray("searchGroups");
		for (JsonElement gEl : groups) {
			if (!gEl.isJsonObject()) {
				continue;
			}
			JsonObject group = gEl.getAsJsonObject();
			if (!group.has("searches") || !group.get("searches").isJsonArray()) {
				continue;
			}
			for (JsonElement sEl : group.getAsJsonArray("searches")) {
				if (!sEl.isJsonObject()) {
					continue;
				}
				JsonObject search = sEl.getAsJsonObject();
				String facetId = search.has("facetId") ? search.get("facetId").getAsString() : null;
				String searchModule = facetIdToModuleName(facetId);
				if (searchModule == null) {
					searchModule = resultModule;
				}
				if (!normalizeModuleName(searchModule).equals(resultNorm)) {
					continue;
				}
				if (!search.has("filterGroups") || !search.get("filterGroups").isJsonArray()) {
					continue;
				}
				for (JsonElement fgEl : search.getAsJsonArray("filterGroups")) {
					if (!fgEl.isJsonObject()) {
						continue;
					}
					JsonObject fg = fgEl.getAsJsonObject();
					if (!fg.has("field")) {
						continue;
					}
					String field = fg.get("field").getAsString();
					if (filterFieldTargetsModuleStatusColumn(searchModule, field)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private boolean filterFieldTargetsModuleStatusColumn(String module, String fieldFromJson) {
		if (module == null || fieldFromJson == null) {
			return false;
		}
		String statusCol = mapFieldToColumn(module, "status");
		if (statusCol == null) {
			return false;
		}
		String mapped = mapFieldToColumn(module, fieldFromJson);
		return mapped != null && statusCol.equalsIgnoreCase(mapped);
	}

	/**
	 * WebUsers normally cannot see rows whose joined {@code status.PrimaryName} is {@code Deleted}.
	 * When the user applies an explicit Status filter, skip this rule so IN (Deleted, …) works.
	 * Legacy {@link #build} passes {@code searchGroupsJson == null} and always applies the rule.
	 */
	private String finalizeSqlWithWebUserDeletedStatusRule(String sql, String module, Integer userId,
			JsonObject searchGroupsJson) {
		if (sql == null || sql.isBlank() || module == null) {
			return sql;
		}
		if (isAdminOrSuperAdmin(userId)) {
			return sql;
		}
		if (searchGroupsJson != null && searchGroupsDefineEntityStatusFilter(module, searchGroupsJson)) {
			return sql;
		}
		String statusAlias = getStatusAliasForModule(module);
		if (statusAlias == null || !hasStatusJoin(sql, statusAlias)) {
			return sql;
		}
		String frag = "(" + statusAlias + ".PrimaryName IS NULL OR " + statusAlias + ".PrimaryName != 'Deleted')";
		return insertWhereClause(sql, frag);
	}

	/**
	 * @param deferWebUserDeletedStatusNameFilter when true, do not append
	 *                                            {@code status.PrimaryName != 'Deleted'} here — caller must append
	 *                                            via {@link #finalizeSqlWithWebUserDeletedStatusRule} so explicit
	 *                                            Status filters (e.g. include Deleted) still work.
	 */
	private String applyCommonFilters(String sql, String tableAlias, String deletedColumn, String module, Integer userId,
			boolean deferWebUserDeletedStatusNameFilter) {
		// V-06: guest/anonymous users must see only Public (Is_Public = 1) objects, regardless of config
		// Guest includes: userId == null, userId == 0, OR userId < 0 (e.g., -1 for guest token)
		// Admin/SuperAdmin are exempt from "Hide Non-Public" setting and can always see Non-Public objects.
		boolean isGuest = (userId == null || userId <= 0);
		boolean hideNonPublicConfig = configurationService.shouldHideNonPublicObjects();
		boolean exemptAdmin = isAdminOrSuperAdmin(userId);
		boolean enforcePublicOnly = isGuest || (hideNonPublicConfig && !exemptAdmin);

		if (deletedColumn == null || deletedColumn.isBlank()) {
			// We may still need to hide non-public rows
			if (enforcePublicOnly) {
				String publicColumn = getPublicColumnForAlias(tableAlias);
				if (publicColumn != null) {
					if (!isGuest && hideNonPublicConfig) {
						// C-31/E-06: authenticated stakeholders are exempt from Hide Non-Public config
						String stakeholderExempt = buildStakeholderExemptionCondition(tableAlias, module, userId);
						if (stakeholderExempt != null) {
							return appendFilterCondition(sql, "(" + publicColumn + " = 1 OR " + stakeholderExempt + ")");
						}
					}
					return appendFilterCondition(sql, publicColumn + " = 1");
				}
			}
			return sql;
		}
		String condition = tableAlias + "." + deletedColumn + " IS NULL";
		if (enforcePublicOnly) {
			String publicColumn = getPublicColumnForAlias(tableAlias);
			if (publicColumn != null) {
				if (!isGuest && hideNonPublicConfig) {
					// C-31/E-06: authenticated stakeholders are exempt from Hide Non-Public config
					String stakeholderExempt = buildStakeholderExemptionCondition(tableAlias, module, userId);
					if (stakeholderExempt != null) {
						condition = "(" + condition + " AND (" + publicColumn + " = 1 OR " + stakeholderExempt + "))";
					} else {
						condition = "(" + condition + " AND " + publicColumn + " = 1)";
					}
				} else {
					condition = "(" + condition + " AND " + publicColumn + " = 1)";
				}
			}
		}
		
		// Add status filter for WebUsers: hide objects with status "Deleted"
		// Admin and SuperAdmin can see deleted status objects.
		// For "system" module in Unison Search we do not filter by Deleted so deleted systems appear in the list.
		// When deferWebUserDeletedStatusNameFilter is true (Unison searchGroups path), this is applied later so
		// explicit Status dropdown filters can include Deleted / Pending Review without being negated here.
		if (!deferWebUserDeletedStatusNameFilter && !isAdminOrSuperAdmin(userId)) {
			String statusAlias = getStatusAliasForModule(module);
			if (statusAlias != null && hasStatusJoin(sql, statusAlias)) {
				// Filter out objects where status.PrimaryName = 'Deleted'
				condition = "(" + condition + " AND (" + statusAlias + ".PrimaryName IS NULL OR " + statusAlias + ".PrimaryName != 'Deleted'))";
			}
		}
		
		// Exclude nobject_id (temporary cloned rows from active CRs) for the 4 facets
		String nobjectIdExclusion = getNObjectIdExclusion(module, tableAlias);
		if (nobjectIdExclusion != null && !nobjectIdExclusion.isEmpty()) {
			condition = "(" + condition + " AND " + nobjectIdExclusion + ")";
		}

		// C-20: exclude permanently-locked objects from search results for non-admin/non-stakeholder users
		if (!isAdminOrSuperAdmin(userId)) {
			String lockExclusion = getLockExclusionCondition(module, tableAlias);
			if (lockExclusion != null && !lockExclusion.isEmpty()) {
				condition = "(" + condition + " AND " + lockExclusion + ")";
			}
		}

		return appendFilterCondition(sql, condition);
	}

	/**
	 * C-20: Build a NOT EXISTS condition that excludes permanently-locked objects from
	 * search results when the requesting user is not an admin or stakeholder.
	 *
	 * @param module     the QueryBuilder module name (e.g. "dataset", "system")
	 * @param tableAlias the SQL table alias used in the query (e.g. "d", "s")
	 * @return SQL NOT EXISTS fragment, or null if this module is not lockable
	 */
	private String getLockExclusionCondition(String module, String tableAlias) {
		String dbModuleName = queryBuilderModuleToDbModuleName(module);
		if (dbModuleName == null) return null;
		// Use single-quoted literal; dbModuleName must never come from user input
		return String.format(
				"NOT EXISTS (SELECT 1 FROM object_lock ol " +
				"JOIN module m ON ol.Module_ID = m.id " +
				"WHERE ol.Object_ID = %s.ID " +
				"AND m.primaryname = '%s' " +
				"AND ol.Is_Permanent = 1)",
				tableAlias, dbModuleName.replace("'", "''"));
	}

	/**
	 * Maps a QueryBuilder module key to the exact {@code module.primaryname} value
	 * stored in the database (as returned by {@code ModuleResolver}).
	 */
	private static String queryBuilderModuleToDbModuleName(String module) {
		if (module == null) return null;
		return switch (module.toLowerCase()) {
			case "dataset" -> "Data Sets";
			case "system" -> "System";
			case "glossary" -> "Glossary";
			case "process" -> "Process";
			case "project" -> "Project";
			case "product" -> "Product";
			case "policy" -> "Policy";
			case "legal_entity", "legal-entity", "legalentity", "legal" -> "Legal Entity";
			case "business_area", "business-area" -> "Business Area";
			case "capability" -> "Capability";
			case "client" -> "Client";
			case "committee" -> "Committee";
			case "geography" -> "Geography";
			case "regulation" -> "Regulation";
			case "regulator" -> "Regulator";
			case "regulatory_theme", "regulatory-theme", "regulatorytheme" -> "Regulatory Theme";
			case "interface" -> "Interface";
			default -> null;
		};
	}
	
	/**
	 * C-31 / E-06: Build an EXISTS subquery that is true when the current user
	 * is a direct stakeholder of the row being evaluated.  Used to exempt
	 * authenticated stakeholders from the "Hide Non-Public Objects" configuration.
	 *
	 * @param tableAlias the SQL table alias of the main object (e.g. "d")
	 * @param module     the QueryBuilder module key (e.g. "dataset")
	 * @param userId     the current authenticated user ID (must be > 0)
	 * @return SQL EXISTS fragment, or {@code null} if this module has no stakeholder table
	 */
	private String buildStakeholderExemptionCondition(String tableAlias, String module, Integer userId) {
		if (userId == null || userId <= 0) return null;
		String[] info = stakeholderTableForModule(module);
		if (info == null) return null;
		return "EXISTS (SELECT 1 FROM " + info[0] + " _sth "
				+ "JOIN object_x_people _oxp ON _sth." + info[2] + " = _oxp.id "
				+ "WHERE _sth." + info[1] + " = " + tableAlias + ".ID AND _oxp.ipid = " + userId + ")";
	}

	/** Maps a QueryBuilder module key to [stakeholderTable, objectIdColumn, objectXIpidColumn]. */
	private static String[] stakeholderTableForModule(String module) {
		if (module == null) return null;
		return switch (module.toLowerCase()) {
			case "dataset"           -> new String[]{"dataset_x_objectxpeople",       "Dataset_ID",         "Object_x_ipid"};
			case "system"            -> new String[]{"system_x_objectxpeople",        "SystemID",           "Object_x_ipid"};
			case "glossary"          -> new String[]{"glossary_x_objectxpeople",      "GlossaryID",         "Object_x_ipid"};
			case "process"           -> new String[]{"process_x_objectxpeople",       "process_id",         "object_x_ip"};
			case "project"           -> new String[]{"project_x_objectxpeople",       "ProjectID",          "Object_x_ipid"};
			case "product"           -> new String[]{"product_x_objectxpeople",       "product_id",         "object_x_ip"};
			case "policy"            -> new String[]{"policy_x_objectxpeople",        "Policy_ID",          "Object_X_IP"};
			case "legal_entity"      -> new String[]{"legalentity_x_objectxpeople",   "LegalEntityID",      "Object_x_ipid"};
			case "business_area"     -> new String[]{"businessarea_x_objectxpeople",  "BusinessAreaID",     "Object_x_ipid"};
			case "capability"        -> new String[]{"capability_x_objectxpeople",    "CapabilityID",       "Object_x_ipid"};
			case "client"            -> new String[]{"client_x_objectxpeople",        "ClientID",           "Object_x_ipid"};
			case "committee"         -> new String[]{"committee_x_objectxpeople",     "Committee_ID",       "Object_X_ipid"};
			case "geography"         -> new String[]{"geography_x_objectxpeople",     "GeographyID",        "Object_x_ipid"};
			case "regulation"        -> new String[]{"regulation_x_objectxpeople",    "RegulationID",       "Object_x_ipid"};
			case "regulator"         -> new String[]{"regulator_x_objectxpeople",     "RegulatorID",        "Object_x_ipid"};
			case "regulatory_theme"  -> new String[]{"regulatorytheme_x_objectxpeople","RegulatoryThemeID", "Object_x_ipid"};
			case "interface"         -> new String[]{"interface_x_objectxpeople",     "InterfaceID",        "Object_x_ipid"};
			default                  -> null;
		};
	}

	/**
	 * Get SQL condition to exclude nobject_id (temporary cloned rows from active CRs).
	 * Only applies to the 4 facets that support Auto CR: glossary, dataset, system, process.
	 * 
	 * @param module The module name (e.g., "glossary", "dataset", "system", "process")
	 * @param tableAlias The table alias used in the SQL query (e.g., "g", "d", "s", "p")
	 * @return SQL condition string to exclude nobject_id, or null if not applicable
	 */
	private String getNObjectIdExclusion(String module, String tableAlias) {
		if (module == null || tableAlias == null) return null;
		
		// Optional: disable nobject_id exclusion for system (e.g. if system 128 is missing from Unison Search)
		if ("system".equalsIgnoreCase(module) && !configurationService.getExcludeSystemNObjectIdInSearch()) {
			return null;
		}
		
		// Map module to facet name for FacetChangesDAO
		String facetName = null;
		String idColumn = null;
		
		switch (module.toLowerCase()) {
			case "glossary":
				facetName = "glossary";
				idColumn = tableAlias + ".ID";
				break;
			case "dataset":
				facetName = "dataset";
				idColumn = tableAlias + ".ID";
				break;
			case "system":
				facetName = "system";
				idColumn = tableAlias + ".id";
				break;
			case "process":
				facetName = "process";
				idColumn = tableAlias + ".id";
				break;
			default:
				return null; // Not applicable for other modules
		}
		
		try {
			com.example.budg_v2.dao.FacetChangesDAO facetChangesDAO = new com.example.budg_v2.dao.FacetChangesDAO();
			java.util.Set<Integer> excludedIds = facetChangesDAO.getActiveNObjectIdsForFacet(facetName);
			
			if (excludedIds.isEmpty()) {
				return null; // No nobject_id to exclude
			}
			
			String idsList = excludedIds.stream()
				.map(String::valueOf)
				.collect(java.util.stream.Collectors.joining(","));
			
			System.out.println("[QueryBuilder] Excluding " + excludedIds.size() + " nobject_id values for " + module + ": " + idsList);
			
			return idColumn + " NOT IN (" + idsList + ")";
		} catch (Exception e) {
			// Log but don't fail - if we can't get excluded IDs, just return null
			System.err.println("[QueryBuilder] Error getting active nobject_id for " + module + ": " + e.getMessage());
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Build SQL WHERE clause from AST.
	 * 
	 * @param ast        The query AST root node
	 * @param module     The module being queried
	 * @param useFuzzy   Whether to use fuzzy search
	 * @param parameters List to add SQL parameters to
	 * @return SQL WHERE condition string
	 */
	private String buildFromAST(QueryNode ast, String module, boolean useFuzzy, List<Object> parameters) {
		List<String> searchableColumns = getSearchableColumnsForModule(module);
		if (searchableColumns.isEmpty()) {
			return null;
		}

		// Use visitor pattern to generate SQL from AST
		// Include custom field search info so the visitor can also search custom fields
		List<String> facetNames = getModuleFacetNames(module);
		String objectIdCol = getFacetObjectIdColumn(module);
		SQLGeneratorVisitor visitor = new SQLGeneratorVisitor(searchableColumns, parameters, useFuzzy, facetNames, objectIdCol);
		return ast.accept(visitor);
	}

	/**
	 * Insert a WHERE clause into SQL query at the appropriate position.
	 * Handles existing WHERE clauses, ORDER BY, and GROUP BY.
	 * 
	 * @param sql       The base SQL query
	 * @param condition The WHERE condition to insert
	 * @return Modified SQL with WHERE clause inserted
	 */
	private String insertWhereClause(String sql, String condition) {
		if (condition == null || condition.isEmpty()) {
			return sql;
		}

		int insertPosition = findMainWhereInsertPosition(sql);

		if (insertPosition >= 0) {
			String beforeClause = sql.substring(0, insertPosition).trim();
			String clauseAndAfter = sql.substring(insertPosition);
			if (hasWhere(beforeClause)) {
				return beforeClause + " AND " + condition + "\n" + clauseAndAfter;
			} else {
				return beforeClause + " WHERE " + condition + "\n" + clauseAndAfter;
			}
		} else {
			if (hasWhere(sql)) {
				return sql + " AND " + condition;
			} else {
				return sql + " WHERE " + condition;
			}
		}
	}

	/**
	 * Find the appropriate position to insert a WHERE clause in the main query.
	 * Searches for top-level GROUP BY or ORDER BY.
	 */
	private int findMainWhereInsertPosition(String sql) {
		int orderByPos = findMainClause(sql, "ORDER BY");
		int groupByPos = findMainClause(sql, "GROUP BY");

		if (orderByPos >= 0 && groupByPos >= 0) {
			return Math.min(orderByPos, groupByPos);
		} else if (orderByPos >= 0) {
			return orderByPos;
		} else if (groupByPos >= 0) {
			return groupByPos;
		}
		return -1;
	}

	/**
	 * Find a SQL clause (ORDER BY, GROUP BY) only at the top level of the query,
	 * ignoring anything inside parentheses (subqueries).
	 */
	private int findMainClause(String sql, String clause) {
		if (sql == null || clause == null)
			return -1;

		String upperSql = sql.toUpperCase(Locale.ROOT);
		String upperClause = clause.toUpperCase(Locale.ROOT);

		int bracketLevel = 0;
		for (int i = 0; i < upperSql.length(); i++) {
			char c = upperSql.charAt(i);
			if (c == '(')
				bracketLevel++;
			else if (c == ')')
				bracketLevel--;
			else if (bracketLevel == 0) {
				// Check if clause starts at this position
				if (upperSql.startsWith(upperClause, i)) {
					// Ensure it's a word match (preceded and followed by space/newline or
					// start/end)
					boolean prevOk = (i == 0 || Character.isWhitespace(upperSql.charAt(i - 1)));
					boolean nextOk = (i + upperClause.length() == upperSql.length()
							|| Character.isWhitespace(upperSql.charAt(i + upperClause.length())));

					if (prevOk && nextOk) {
						return i;
					}
				}
			}
		}
		return -1;
	}

	/**
	 * Build SQL query from saved search searchGroups format.
	 * This method parses the BUDG-style searchGroups JSON structure and converts it
	 * to SQL.
	 * 
	 * @param module           The target module to search in
	 * @param searchGroupsJson The searchGroups JSON structure
	 * @return QueryResult with SQL and parameters
	 */
	public QueryResult buildFromSearchGroups(String module, JsonObject searchGroupsJson) {
		return buildFromSearchGroups(module, searchGroupsJson, null);
	}

	/**
	 * Build SQL query from saved search searchGroups format with segment filtering.
	 * This method parses the BUDG-style searchGroups JSON structure and converts it
	 * to SQL.
	 * 
	 * NOTE: The generated SQL queries can be quite complex (2500-3900 characters)
	 * with multiple LEFT JOINs (up to 15 joins), nested subqueries, and GROUP_CONCAT
	 * with complex GROUP BY clauses. For performance optimization in the future,
	 * consider:
	 * - Creating database views for frequently used query patterns
	 * - Using stored procedures for complex queries
	 * - Splitting large queries into smaller, more maintainable parts
	 * - Adding appropriate indexes on frequently joined columns
	 * 
	 * @param module           The target module to search in
	 * @param searchGroupsJson The searchGroups JSON structure
	 * @param userId           The user ID for segment filtering (null for no
	 *                         filtering)
	 * @return QueryResult with SQL and parameters
	 */
	public QueryResult buildFromSearchGroups(String module, JsonObject searchGroupsJson, Integer userId) {
		// Special handling for Active Tasks - return null to indicate it should be
		// handled by service layer
		if ("activeTasks".equals(module) || "active-tasks".equals(module) || "activetasks".equals(module)) {
			// Active Tasks are handled by WorkflowTaskDAO, not SQL queries
			// Return null to signal that SearchService should handle it differently
			return new QueryResult(null, List.of());
		}

		String sql = getSqlForModule(module, userId);
		if (sql == null) {
			return new QueryResult(null, List.of());
		}
		// Always include segment columns in Unison results where applicable
		sql = appendSegmentSelectColumnsIfNeeded(module, sql);

		List<Object> parameters = new ArrayList<>();
		boolean useFuzzy = configurationService.getFuzzySearchConfig();

		if (!searchGroupsJson.has("searchGroups")) {
			// Apply segment filtering even if no search groups
			if (userId != null && userId > 0) {
				String segmentCondition = buildSegmentFilterCondition(module, userId);
				if (segmentCondition != null && !segmentCondition.isEmpty()) {
					sql = insertWhereClause(sql, segmentCondition);
				}
			} else {
				String enterpriseOnly = buildEnterpriseOnlySegmentCondition(module);
				if (enterpriseOnly != null && !enterpriseOnly.isEmpty()) {
					sql = insertWhereClause(sql, enterpriseOnly);
				}
			}
			sql = finalizeSqlWithWebUserDeletedStatusRule(sql, module, userId, searchGroupsJson);
			return new QueryResult(sql, parameters);
		}

		JsonArray searchGroups = searchGroupsJson.getAsJsonArray("searchGroups");
		if (searchGroups.size() == 0) {
			sql = finalizeSqlWithWebUserDeletedStatusRule(sql, module, userId, searchGroupsJson);
			return new QueryResult(sql, parameters);
		}

		// Build SQL conditions from searchGroups
		StringBuilder allConditions = new StringBuilder();
		boolean firstGroup = true;

		for (int i = 0; i < searchGroups.size(); i++) {
			JsonObject group = searchGroups.get(i).getAsJsonObject();

			// Skip inactive groups
			if (group.has("active") && !group.get("active").getAsBoolean()) {
				continue;
			}

			String groupOperator = group.has("operator") ? group.get("operator").getAsString().toUpperCase() : "START";

			if (!group.has("searches") || !group.get("searches").isJsonArray()) {
				continue;
			}

			JsonArray searches = group.getAsJsonArray("searches");
			if (searches.size() == 0) {
				continue;
			}

			// Build conditions for searches in this group
			StringBuilder groupConditions = new StringBuilder();
			boolean firstSearch = true;

			for (int j = 0; j < searches.size(); j++) {
				JsonObject search = searches.get(j).getAsJsonObject();

				// Skip inactive searches
				if (search.has("active") && !search.get("active").getAsBoolean()) {
					continue;
				}

				String searchOperator = search.has("operator") ? search.get("operator").getAsString().toUpperCase()
						: "START";
				String searchFacetId = search.has("facetId") ? search.get("facetId").getAsString() : null;

				// Convert facetId to module name
				String searchModule = facetIdToModuleName(searchFacetId);
				if (searchModule == null) {
					searchModule = module; // Fallback to target module
				}

				// Process filterGroups
				String searchCondition = buildFromFilterGroups(searchModule, search, useFuzzy, parameters);

				if (searchCondition != null && !searchCondition.isEmpty()) {
					if (firstSearch) {
						groupConditions.append(searchCondition);
						firstSearch = false;
					} else {
						// Apply search operator within group
						switch (searchOperator) {
							case "AND":
								groupConditions.append(" AND ").append(searchCondition);
								break;
							case "OR":
								groupConditions.append(" OR ").append(searchCondition);
								break;
							case "NOT":
								groupConditions.append(" AND NOT ").append(searchCondition);
								break;
							default:
								groupConditions.append(" AND ").append(searchCondition);
								break;
						}
					}
				}
			}

			if (groupConditions.length() > 0) {
				if (firstGroup) {
					allConditions.append("(").append(groupConditions).append(")");
					firstGroup = false;
				} else {
					// Apply group operator
					switch (groupOperator) {
						case "AND":
							allConditions.append(" AND (").append(groupConditions).append(")");
							break;
						case "OR":
							allConditions.append(" OR (").append(groupConditions).append(")");
							break;
						case "NOT":
							allConditions.append(" AND NOT (").append(groupConditions).append(")");
							break;
						default:
							allConditions.append(" AND (").append(groupConditions).append(")");
							break;
					}
				}
			}
		}

		if (allConditions.length() > 0) {
			sql = insertWhereClause(sql, allConditions.toString());
			if (UnisonTrace.enabled()) {
				UnisonTrace.log(null, "QueryBuilder.where",
						"module=" + module + " searchWhereChars=" + allConditions.length() + " params=" + parameters.size());
			}
		}

		// Apply segment filtering if user is authenticated
		if (userId != null && userId > 0) {
			// Authenticated users (including Super Admin) must respect cube selection.
			String segmentCondition = buildSegmentFilterCondition(module, userId);
			if (segmentCondition != null && !segmentCondition.isEmpty()) {
				sql = insertWhereClause(sql, segmentCondition);
			}
		} else {
			// Guest user (userId <= 0) OR Anonymous (userId == null)
			String enterpriseOnly = buildEnterpriseOnlySegmentCondition(module);
			if (enterpriseOnly != null && !enterpriseOnly.isEmpty()) {
				sql = insertWhereClause(sql, enterpriseOnly);
			}
			// V-06: unconditionally enforce Is_Public = 1 for guest users
			String tableAlias = getTableAliasForModule(module);
			if (tableAlias != null) {
				String publicColumn = getPublicColumnForAlias(tableAlias);
				if (publicColumn != null) {
					sql = insertWhereClause(sql, publicColumn + " = 1");
				}
			}
		}

		sql = finalizeSqlWithWebUserDeletedStatusRule(sql, module, userId, searchGroupsJson);
		return new QueryResult(sql, parameters);
	}

	/**
	 * Build SQL query from saved search searchGroups format using SegmentAccessContext.
	 * This method uses the pre-computed effective segments from accessCtx instead of
	 * recalculating them, ensuring consistency across the application.
	 * 
	 * @param module           The target module to search in
	 * @param searchGroupsJson The searchGroups JSON structure
	 * @param accessCtx        The SegmentAccessContext with pre-computed effective segments
	 * @return QueryResult with SQL and parameters
	 */
	public QueryResult buildFromSearchGroupsWithContext(String module, JsonObject searchGroupsJson, SegmentAccessContext accessCtx) {
		if (accessCtx == null) {
			return buildFromSearchGroups(module, searchGroupsJson, null);
		}
		
		// Always use actual userId; segment filtering is still required for Super Admin,
		// based on cube-selected segments.
		Integer userId = accessCtx.getUserId();
		
		// Build query with userId first
		QueryResult qr = buildFromSearchGroups(module, searchGroupsJson, userId);

		// If no effective segments from context, keep base query result.
		if (accessCtx.getEffectiveSegments() == null || accessCtx.getEffectiveSegments().isEmpty()) {
			return qr;
		}
		
		// Replace segment condition with pre-computed effective segments from accessCtx
		String segmentConditionFromContext = buildSegmentFilterConditionFromSegmentIds(
			module, 
			new ArrayList<>(accessCtx.getEffectiveSegments())
		);
		
		if (segmentConditionFromContext != null && !segmentConditionFromContext.isEmpty()) {
			// Remove old segment condition if it exists and add context-based one
			String modifiedSql = replaceSegmentCondition(qr.sql, segmentConditionFromContext);
			qr = new QueryResult(modifiedSql, qr.parameters);
		}
		
		return qr;
	}
	
	/**
	 * Build segment filter condition directly from a list of segment IDs.
	 * This bypasses the database queries and uses pre-computed segment IDs.
	 * 
	 * @param module      The module to filter
	 * @param segmentIds  List of segment IDs user has access to
	 * @return SQL condition string for segment filtering
	 */
	private String buildSegmentFilterConditionFromSegmentIds(String module, List<Integer> segmentIds) {
		// Skip segment filtering for modules that are not tracked in segment_x_resource
		// (people, role, org_unit, geography, etc.).  getObjectTypeForModule() returning null
		// is the canonical indicator that the module has no object-segment assignments.
		if (getObjectTypeForModule(module) == null) {
			return null;
		}

		if (segmentIds == null || segmentIds.isEmpty()) {
			// Default to Enterprise segment (ID=1)
			segmentIds = Collections.singletonList(1);
		}
		
		String segmentIdList = segmentIds.stream()
			.map(String::valueOf)
			.collect(java.util.stream.Collectors.joining(","));
		
		StringBuilder condition = new StringBuilder();
		
		switch (module) {
			case "system":
				// s.ID is the system.id. segment_x_resource links to object_reference.ID which
				// contains Object_ID (the actual system.id) and Object_Type_ID for the type.
				// Use an EXISTS subquery joining object_reference -> segment_x_resource to
				// check if the system is assigned to any of the given segment IDs.
				condition.append("EXISTS (SELECT 1 FROM object_reference orr JOIN segment_x_resource sxr ON sxr.Object_Reference_ID = orr.ID WHERE orr.Object_Type_ID = (SELECT ID FROM segment_object_type WHERE Type = 'System') AND orr.Object_ID = s.ID AND sxr.Segment_ID IN (").append(segmentIdList).append(") )");
				break;
			case "dataset":
				condition.append("d.ID IN (SELECT sxr.Object_Reference_ID FROM segment_x_resource sxr WHERE sxr.Segment_ID IN (").append(segmentIdList).append("))");
				break;
			case "attribute":
				condition.append("a.ID IN (SELECT sxr.Object_Reference_ID FROM segment_x_resource sxr WHERE sxr.Segment_ID IN (").append(segmentIdList).append("))");
				break;
			// Add more modules as needed - use table alias (e.g. prj for project) not table name,
			// so the condition matches the generated SQL which uses aliases.
			default:
				String idColumn = getIdColumnForModule(module);
				if (idColumn != null) {
					condition.append(idColumn).append(" IN (SELECT sxr.Object_Reference_ID FROM segment_x_resource sxr WHERE sxr.Segment_ID IN (").append(segmentIdList).append("))");
				}
		}
		
		return condition.length() > 0 ? condition.toString() : null;
	}
	
	/**
	 * Replace the existing segment condition in SQL with a new one from SegmentAccessContext.
	 * This ensures that the pre-computed effective segments are used consistently.
	 * 
	 * @param sql                    The SQL query
	 * @param newSegmentCondition    The new segment condition to use
	 * @return Modified SQL with the new segment condition
	 */
	private String replaceSegmentCondition(String sql, String newSegmentCondition) {
		// Look for existing segment_x_resource joins and replace them
		String result = sql;
		
		// Pattern to find segment conditions
		int whereIndex = result.toUpperCase().indexOf("WHERE");
		if (whereIndex > -1) {
			// Insert the new segment condition into the WHERE clause
			String whereClause = result.substring(whereIndex);
			String modifiedWhereClause = newSegmentCondition + " AND " + whereClause.substring(5); // Skip "WHERE"
			result = result.substring(0, whereIndex) + "WHERE " + modifiedWhereClause;
		} else {
			result = insertWhereClause(result, newSegmentCondition);
		}
		
		return result;
	}

	/**
	 * Build SQL condition from filterGroups in a search object.
	 * 
	 * @param module     The module to search in
	 * @param search     The search JSON object containing filterGroups
	 * @param useFuzzy   Whether to use fuzzy search
	 * @param parameters List to add SQL parameters to
	 * @return SQL condition string
	 */
	private String buildFromFilterGroups(String module, JsonObject search, boolean useFuzzy, List<Object> parameters) {
		if (!search.has("filterGroups") || !search.get("filterGroups").isJsonArray()) {
			return null;
		}

		JsonArray filterGroups = search.getAsJsonArray("filterGroups");
		if (filterGroups.size() == 0) {
			return null;
		}

		StringBuilder conditions = new StringBuilder();
		boolean first = true;

		for (int i = 0; i < filterGroups.size(); i++) {
			JsonObject filterGroup = filterGroups.get(i).getAsJsonObject();

			// Support different filterGroup formats
			// Format 1: { "field": "name", "condition": "contains", "value": "test" }
			// Format 2: { "query": "test" } - simple query

			String condition = null;

			if (filterGroup.has("field")) {
				// Format 1: field-based filter
				String field = filterGroup.get("field").getAsString();
				String filterCondition = filterGroup.has("condition") ? filterGroup.get("condition").getAsString()
						: "contains";
				
				// Handle BETWEEN condition for date ranges
				if ("between".equalsIgnoreCase(filterCondition)) {
					String from = filterGroup.has("from") ? filterGroup.get("from").getAsString() : null;
					String to = filterGroup.has("to") ? filterGroup.get("to").getAsString() : null;
					condition = buildBetweenCondition(module, field, from, to, parameters);
				} else if (filterGroup.has("value")) {
					// Other conditions (in, equals, contains)
					JsonElement valueElement = filterGroup.get("value");
					
					// For IN condition with JsonArray, extract values directly for better parsing
					String value;
					if (valueElement != null && valueElement.isJsonArray() && "in".equalsIgnoreCase(filterCondition)) {
						JsonArray jsonArray = valueElement.getAsJsonArray();
						List<String> valueList = new ArrayList<>();
						for (JsonElement elem : jsonArray) {
							if (elem.isJsonPrimitive()) {
								// Handle numbers properly to avoid "1.0" -> "1.0" string conversion
								if (elem.getAsJsonPrimitive().isNumber()) {
									// Convert number to integer string to avoid float representation
									int intValue = elem.getAsInt();
									valueList.add(String.valueOf(intValue));
								} else {
									valueList.add(elem.getAsString());
								}
							}
						}
						value = String.join(",", valueList);
					} else {
						value = valueElement == null || valueElement.isJsonNull()
								? ""
								: (valueElement.isJsonArray() ? valueElement.toString() : valueElement.getAsString());
					}

					condition = buildFieldCondition(module, field, filterCondition, value, useFuzzy, parameters);
				}
		} else if (filterGroup.has("query")) {
			// Format 2: simple query; optionally restricted to specific search columns
			String query = filterGroup.get("query").getAsString();
			Map<String, Boolean> sfMap = null;
			if (filterGroup.has("searchFields") && filterGroup.get("searchFields").isJsonObject()) {
				sfMap = new HashMap<>();
				JsonObject sfJson = filterGroup.getAsJsonObject("searchFields");
				for (Map.Entry<String, JsonElement> e : sfJson.entrySet()) {
					if (e.getValue().isJsonPrimitive()) {
						sfMap.put(e.getKey(), e.getValue().getAsBoolean());
					}
				}
			}
			condition = buildConditionForModuleWithSearchFields(module, module, query, useFuzzy, parameters, sfMap);
			}

			if (condition != null && !condition.isEmpty()) {
				if (first) {
					conditions.append(condition);
					first = false;
				} else {
					conditions.append(" AND ").append(condition);
				}
			}
		}

		return conditions.length() > 0 ? conditions.toString() : null;
	}

	/**
	 * Build SQL BETWEEN condition for date ranges.
	 * 
	 * @param module     The module
	 * @param field      The field name
	 * @param from       The start date (can be null)
	 * @param to         The end date (can be null)
	 * @param parameters List to add SQL parameters to
	 * @return SQL condition string
	 */
	private String buildBetweenCondition(String module, String field, String from, String to,
			List<Object> parameters) {
		// Map field names to column names
		String columnName = mapFieldToColumn(module, field);
		if (columnName == null) {
			// Fallback to field name as-is
			columnName = field;
		}
		columnName = qualifyFilterColumnExpression(module, columnName);
		
		StringBuilder condition = new StringBuilder();
		
		if (from != null && !from.trim().isEmpty() && to != null && !to.trim().isEmpty()) {
			// Both from and to provided
			condition.append(columnName).append(" BETWEEN ? AND ?");
			parameters.add(from.trim());
			parameters.add(to.trim());
		} else if (from != null && !from.trim().isEmpty()) {
			// Only from provided
			condition.append(columnName).append(" >= ?");
			parameters.add(from.trim());
		} else if (to != null && !to.trim().isEmpty()) {
			// Only to provided
			condition.append(columnName).append(" <= ?");
			parameters.add(to.trim());
		} else {
			// Neither provided - return null (no condition)
			return null;
		}
		
		return condition.toString();
	}
	
	/**
	 * Build SQL condition for a specific field.
	 * 
	 * @param module     The module
	 * @param field      The field name
	 * @param condition  The condition type (contains, equals, etc.)
	 * @param value      The value to search for
	 * @param useFuzzy   Whether to use fuzzy search
	 * @param parameters List to add SQL parameters to
	 * @return SQL condition string
	 */
	private static boolean isSystemExternalFilterColumn(String module, String qualifiedColumn) {
		if (module == null || qualifiedColumn == null) {
			return false;
		}
		if (!module.trim().equalsIgnoreCase("system")) {
			return false;
		}
		String c = qualifiedColumn.trim();
		return c.equalsIgnoreCase("s.External") || c.toLowerCase(Locale.ROOT).endsWith(".external");
	}

	private String buildFieldCondition(String module, String field, String condition, String value,
			boolean useFuzzy, List<Object> parameters) {
		// Map field names to column names
		String columnName = mapFieldToColumn(module, field);
		if (columnName == null) {
			// Fallback to searching all searchable columns
			return buildConditionForModule(module, module, value, useFuzzy, parameters);
		}
		columnName = qualifyFilterColumnExpression(module, columnName);

		// BOOLEAN equals on System.External (0/1) — avoid LIKE; treat NULL as false for 0
		if (condition != null && "equals".equalsIgnoreCase(condition) && value != null) {
			String v = value.trim();
			if (("0".equals(v) || "1".equals(v)) && isSystemExternalFilterColumn(module, columnName)) {
				int b = Integer.parseInt(v);
				if (b == 0) {
					parameters.add(0);
					return "(" + columnName + " = ? OR " + columnName + " IS NULL)";
				}
				parameters.add(1);
				return columnName + " = ?";
			}
		}

		// Support IN condition for id lists
		if (condition != null && "in".equalsIgnoreCase(condition)) {
			// Expect comma-separated string or JSON array string
			List<String> tokens = new ArrayList<>();
			if (value != null && value.trim().startsWith("[")) {
				// Try to split JSON-like array without full parsing
				String trimmed = value.trim().replaceAll("[\\[\\]\"]", "");
				if (!trimmed.isEmpty()) {
					tokens.addAll(Arrays.asList(trimmed.split(",")));
				}
			} else if (value != null && !value.trim().isEmpty()) {
				tokens.addAll(Arrays.asList(value.split(",")));
			}
			List<Integer> ints = new ArrayList<>();
			int parseErrors = 0;
			for (String token : tokens) {
				String trimmedToken = token.trim();
				if (trimmedToken.isEmpty()) {
					continue;
				}
				try {
					// Try parsing as integer first
					ints.add(Integer.parseInt(trimmedToken));
				} catch (NumberFormatException e) {
					// If that fails, try parsing as double then converting to int (handles "1.0" case)
					try {
						double doubleValue = Double.parseDouble(trimmedToken);
						int intValue = (int) doubleValue;
						ints.add(intValue);
						System.out.println("[QueryBuilder] buildFieldCondition: Converted float '" + trimmedToken + "' to int " + intValue);
					} catch (NumberFormatException e2) {
						parseErrors++;
						System.err.println("[QueryBuilder] buildFieldCondition: Failed to parse ID token: '" + trimmedToken + "' in value: " + value);
					}
				}
			}
			if (ints.isEmpty()) {
				System.err.println("[QueryBuilder] buildFieldCondition: IN condition with empty ID list. Module: " + module + ", Field: " + field + ", Value: " + value + ", Parse errors: " + parseErrors);
				return null;
			}
			if (parseErrors > 0) {
				System.err.println("[QueryBuilder] buildFieldCondition: IN condition parsed " + ints.size() + " IDs with " + parseErrors + " parse errors. Module: " + module + ", Field: " + field);
			}
			// System.External: DB often stores NULL for "not external"; UI "No" sends IN (0). Match NULL as false.
			if (ints.size() == 1 && ints.get(0) == 0 && isSystemExternalFilterColumn(module, columnName)) {
				parameters.add(0);
				return "(" + columnName + " = ? OR " + columnName + " IS NULL)";
			}
			if (ints.size() == 1 && ints.get(0) == 1 && isSystemExternalFilterColumn(module, columnName)) {
				parameters.add(1);
				return columnName + " = ?";
			}
			String placeholders = getPlaceholders(ints.size());
			parameters.addAll(ints);
			String sqlCondition = columnName + " IN (" + placeholders + ")";
			return sqlCondition;
		}

		String searchParameter = prepareSearchParameter(value, useFuzzy);
		String sqlCondition = createSearchCondition(columnName, value, useFuzzy);
		parameters.add(searchParameter);

		return sqlCondition;
	}

	/**
	 * Map field name to database column name.
	 * 
	 * @param module The module
	 * @param field  The field name (camelCase or display name)
	 * @return The database column name, or null if not found
	 */
	private String mapFieldToColumn(String module, String field) {
		// This is a simplified mapping - can be extended
		// Field names from frontend may be in camelCase or display names
		String fieldLower = field != null ? field.toLowerCase() : "";

		// Common field mappings
		switch (fieldLower) {
			case "name":
			case "primaryname":
				return getNameColumnForModule(module);
			case "refnumber":
			case "ref":
				return getRefNumberColumnForModule(module);
		case "description":
		case "definition":
			return getDescriptionColumnForModule(module);
		case "lifecycle":
		case "lifecycle_status":
			// Map lifecycle field to appropriate column based on module
			if (module != null) {
				String moduleLower = module.trim().toLowerCase();
				return switch (moduleLower) {
					case "dataset" -> "d.lifecycle";
					case "glossary" -> "g.Lifecycle";
					case "system" -> "s.Lifecycle";
					case "process" -> "pr.lifecycle_status";
					case "policy" -> "po.Lifecycle_Status";
					case "capability" -> "c.Lifecycle";
					case "product" -> "prd.lifecycle_status";
					case "project" -> "prj.lifecycle_status";
					case "interface" -> "i.Lifecycle_id";
					case "business-area", "businessarea" -> "ba.Lifecycle";
					case "client" -> "c.Lifecycle";
					case "committee" -> "c.Lifecycle";
					default -> field; // Fallback to field name as-is
				};
			}
			return field;
		case "external":
			// System: External flag (0/1) — matches SELECT s.External AS External
			if (module != null && module.trim().equalsIgnoreCase("system")) {
				return "s.External";
			}
			return null;
		case "status":
			// Map status field to appropriate column based on module
			if (module != null) {
				String moduleLower = module.trim().toLowerCase();
				return switch (moduleLower) {
					case "system" -> "s.status"; // System table uses s.status column
					case "dataset" -> "d.status";
					case "glossary" -> "g.Status";
					case "process" -> "pr.status";
					case "policy" -> "po.status"; // Policy uses lowercase status column
					case "capability" -> "c.Status";
					case "client" -> "c.Status"; // Client uses c alias
					case "committee" -> "c.Status"; // Committee uses c alias
					case "business-area", "businessarea" -> "ba.Status";
					case "legal-entity", "legalentity", "legal" -> "l.Status";
					case "interface" -> "i.status_id";
					case "product" -> "prd.status";
					case "project" -> "prj.status";
					case "people" -> "p.status_id";
					case "org-unit", "orgunit" -> "ou.status_id";
					case "regulatory-theme", "regulatorytheme" -> "rt.Status_ID";
					default -> {
						// Try to get table alias and use status column
						String tableAlias = getTableAliasForModule(module);
						if (tableAlias != null) {
							// Check if status column name needs special handling
							if (moduleLower.contains("interface") || moduleLower.contains("people") || 
							    moduleLower.contains("org") || moduleLower.contains("regulatory-theme")) {
								yield tableAlias + ".status_id";
							} else if (moduleLower.contains("regulatory-theme")) {
								yield tableAlias + ".Status_ID";
							}
							yield tableAlias + ".status";
						}
						yield field; // Fallback to field name as-is
					}
				};
			}
			return field;
		case "id":
			// Special handling for attribute module: use a.ID for ID filtering
			// (getIdColumnForModule returns a.Dataset_ID for segment filtering, but we need a.ID for ID filtering)
			if (module != null && module.trim().equalsIgnoreCase("attribute")) {
				return "a.ID";
			}
			// Special handling for role module: relationship queries return RoleID (object_role.ID),
			// but role search uses oxp.ID (object_x_people.ID). When filtering by ID for roles,
			// we need to check if we should use oxp.RoleID instead. However, since role records
			// are identified by oxp.ID in the search results, we keep using oxp.ID here.
			// The issue is that relationship queries return RoleID values, which need to be
			// converted to oxp.ID values. This conversion should happen in GraphTraversalService.
			return getIdColumnForModule(module) != null ? getIdColumnForModule(module) : "id";
			default:
				// Try to find in searchable columns
				List<String> searchableColumns = getSearchableColumnsForModule(module);
				for (String col : searchableColumns) {
					if (col.toLowerCase().contains(fieldLower)) {
						return col;
					}
				}
				return null;
		}
	}

	/**
	 * Get reference number column for a module.
	 */
	private String getRefNumberColumnForModule(String moduleRaw) {
		if (moduleRaw == null)
			return null;
		String module = moduleRaw.trim().toLowerCase();
		return switch (module) {
			case "dataset" -> "d.RefNumber";
			case "attribute" -> "a.RefNumber";
			case "glossary" -> "g.RefNumber";
			case "process" -> "pr.refnumber";
			case "policy" -> "po.refNumber";
			case "capability" -> "c.RefNumber";
			case "committee" -> "c.RefNumber";
			case "regulation" -> "r.RefNumber";
			case "regulatory-theme", "regulatorytheme" -> "rt.RefNumber";
			case "interface" -> "i.Ref_number";
			default -> null;
		};
	}

	/**
	 * Get description column for a module.
	 */
	private String getDescriptionColumnForModule(String moduleRaw) {
		if (moduleRaw == null)
			return null;
		String module = moduleRaw.trim().toLowerCase();
		return switch (module) {
			case "dataset" -> "d.definition";
			case "attribute" -> "a.Definition";
			case "glossary" -> "g.Description";
			case "system" -> "s.Description";
			case "process" -> "pr.description";
			case "policy" -> "po.Description";
			case "capability" -> "c.Description";
			case "client" -> "c.Description";
			case "committee" -> "c.Description";
			case "business-area" -> "ba.Description";
			case "legal-entity", "legalentity", "legal" -> "l.Description";
			case "geography" -> "g.Description";
			case "interface" -> "i.Description";
			default -> null;
		};
	}

	private String getPlaceholders(int count) {
		return String.join(",", java.util.Collections.nCopies(count, "?"));
	}

	private String appendFilterCondition(String sql, String condition) {
		return insertWhereClause(sql, condition);
	}

	private boolean hasWhere(String sql) {
		if (sql == null)
			return false;

		String upperSql = sql.toUpperCase(Locale.ROOT);
		int bracketLevel = 0;
		for (int i = 0; i < upperSql.length(); i++) {
			char c = upperSql.charAt(i);
			if (c == '(')
				bracketLevel++;
			else if (c == ')')
				bracketLevel--;
			else if (bracketLevel == 0) {
				// Check for WHERE at top level
				if (upperSql.startsWith("WHERE", i)) {
					// Ensure it's a word match
					boolean prevOk = (i == 0 || Character.isWhitespace(upperSql.charAt(i - 1)));
					boolean nextOk = (i + 5 == upperSql.length() || Character.isWhitespace(upperSql.charAt(i + 5)));
					if (prevOk && nextOk)
						return true;
				}
			}
		}
		return false;
	}

	private String getPublicColumnForAlias(String tableAlias) {
		if (tableAlias == null)
			return null;
		return switch (tableAlias) {
			case "d" -> "d.AccessControlType"; // dataset uses AccessControlType (1 = Public)
			case "a" -> null; // attribute table has no AccessControlType/Is_Public column in schema
			case "g" -> "g.Is_Public"; // glossary
			case "s" -> "s.is_Public"; // system
			case "i" -> "i.is_Public"; // interface
			case "pr" -> "pr.isPublic"; // process
			case "prj" -> "prj.is_public"; // project
			case "prd" -> "prd.is_Public"; // product
			case "po" -> "po.isPublic"; // policy
			case "ba" -> "ba.Is_Public"; // business area
			case "cap" -> "cap.Is_Public"; // capability
			case "com" -> "com.Is_Public"; // committee
			case "l" -> "l.Is_Public"; // legal entity
			case "reg" -> "reg.Is_Public"; // regulation
			case "rt" -> null; // regulatory theme has no Is_Public column in schema
			case "regu" -> null; // regulator has no Is_Public column
			case "geo" -> null; // geography has no Is_Public column
			case "cl" -> null; // client has no Is_Public column
			case "ou" -> null; // org unit has no Is_Public column
			case "p" -> null; // people has no Is_Public column
			default -> null;
		};
	}

	/**
	 * Convert facet ID to module name.
	 * 
	 * @param facetId The facet ID (e.g., "DATASET", "PROCESS")
	 * @return The module name (e.g., "dataset", "process")
	 */
	private String facetIdToModuleName(String facetId) {
		if (facetId == null)
			return null;

		// Map facet IDs to module names
		return switch (facetId.toUpperCase()) {
			case "DATASET" -> "dataset";
			case "ATTRIBUTE" -> "attribute";
			case "SYSTEM" -> "system";
			case "GLOSSARY" -> "glossary";
			case "DATAQUALITY" -> "dataquality";
			case "PEOPLE" -> "people";
			case "ROLE" -> "role";
			case "BUSINESS_AREA" -> "business-area";
			case "LEGAL_ENTITY" -> "legal-entity";
			case "CLIENT" -> "client";
			case "COMMITTEE" -> "committee";
			case "POLICY" -> "policy";
			case "PROCESS" -> "process";
			case "INTERFACE" -> "interface";
			case "CAPABILITY" -> "capability";
			case "PRODUCT" -> "product";
			case "ORG_UNIT" -> "orgunit";
			case "GEOGRAPHY" -> "geography";
			case "REGULATION" -> "regulation";
			case "REGULATOR" -> "regulator";
			case "REGULATORY_THEME" -> "regulatory-theme";
			default -> null;
		};
	}
	
	/**
	 * Build optimized Role query using subqueries instead of multiple LEFT JOINs
	 * This improves performance by allowing the database to optimize each subquery independently
	 */
	private String buildOptimizedRoleQuery() {
		return "SELECT \n" +
				"    oxp.ID AS ID,\n" +
				"    orl.PrimaryName AS 'Role',\n" +
				"    ort.PrimaryName AS 'Role type',\n" +
				"    orl.Description AS Description,\n" +
				"    CONCAT(p.First_Name, ' ', p.Last_Name) AS 'Full Name',\n" +
				"    p.ID AS 'Full Name_ID',\n" +
				"    m.PrimaryName AS 'Object Type',\n" +
				"    COALESCE(\n" +
				"        (SELECT g.Name FROM glossary_x_objectxpeople gxo JOIN glossary g ON gxo.GlossaryID = g.ID WHERE gxo.Object_x_ipid = oxp.ID LIMIT 1),\n" +
				"        (SELECT d.PrimaryName FROM dataset_x_objectxpeople dxo JOIN dataset d ON dxo.Dataset_ID = d.ID WHERE dxo.Object_x_ipid = oxp.ID LIMIT 1),\n" +
				"        (SELECT pr.PrimaryName FROM process_x_objectxpeople pxo JOIN process pr ON pxo.Process_ID = pr.ID WHERE pxo.Object_x_ip = oxp.ID LIMIT 1),\n" +
				"        (SELECT s.Name FROM system_x_objectxpeople sxo JOIN system s ON sxo.SystemID = s.ID WHERE sxo.Object_x_ipid = oxp.ID LIMIT 1),\n" +
				"        (SELECT prod.PrimaryName FROM product_x_objectxpeople proxo JOIN product prod ON proxo.Product_ID = prod.ID WHERE proxo.Object_x_ip = oxp.ID LIMIT 1),\n" +
				"        (SELECT pj.PrimaryName FROM project_x_objectxpeople pjxo JOIN project pj ON pjxo.Project_ID = pj.ID WHERE pjxo.Object_x_ip = oxp.ID LIMIT 1),\n" +
				"        (SELECT a.PrimaryName FROM attribute_x_objectxpeople axo JOIN attribute a ON axo.AttributeID = a.ID WHERE axo.Object_x_ipid = oxp.ID LIMIT 1),\n" +
				"        (SELECT po.PrimaryName FROM policy_x_objectxpeople pox JOIN policy po ON pox.Policy_ID = po.ID WHERE pox.Object_X_IP = oxp.ID LIMIT 1),\n" +
				"        (SELECT si.Name FROM interface_x_objectxpeople sixo JOIN interface si ON sixo.InterfaceID = si.ID WHERE sixo.Object_x_ipid = oxp.ID LIMIT 1),\n" +
				"        (SELECT reg.primaryName FROM regulation_x_objectxpeople regxo JOIN regulation reg ON regxo.RegulationID = reg.ID WHERE regxo.Object_x_ipid = oxp.ID LIMIT 1),\n" +
				"        (SELECT com.PrimaryName FROM committee_x_objectxpeople comxo JOIN committee com ON comxo.Committee_ID = com.ID WHERE comxo.Object_X_ipid = oxp.ID LIMIT 1),\n" +
				"        (SELECT cl.PrimaryName FROM client_x_objectxpeople clxo JOIN client cl ON clxo.ClientID = cl.ID WHERE clxo.Object_x_ipid = oxp.ID LIMIT 1),\n" +
				"        (SELECT le.ShortName FROM legal_x_objectxpeople lexo JOIN Legal le ON lexo.Legal_ID = le.ID WHERE lexo.Object_X_IP = oxp.ID LIMIT 1),\n" +
				"        (SELECT ba.PrimaryName FROM businessarea_x_objectxpeople baxo JOIN business_area ba ON baxo.BusinessAreaID = ba.ID WHERE baxo.Object_x_ipid = oxp.ID LIMIT 1),\n" +
				"        (SELECT cap.PrimaryName FROM capability_x_objectxpeople capxo JOIN capability cap ON capxo.CapabilityID = cap.ID WHERE capxo.Object_x_ipid = oxp.ID LIMIT 1)\n" +
				"    ) AS Object,\n" +
				"    COALESCE(\n" +
				"        (SELECT g.ID FROM glossary_x_objectxpeople gxo JOIN glossary g ON gxo.GlossaryID = g.ID WHERE gxo.Object_x_ipid = oxp.ID LIMIT 1),\n" +
				"        (SELECT d.ID FROM dataset_x_objectxpeople dxo JOIN dataset d ON dxo.Dataset_ID = d.ID WHERE dxo.Object_x_ipid = oxp.ID LIMIT 1),\n" +
				"        (SELECT pr.ID FROM process_x_objectxpeople pxo JOIN process pr ON pxo.Process_ID = pr.ID WHERE pxo.Object_x_ip = oxp.ID LIMIT 1),\n" +
				"        (SELECT s.id FROM system_x_objectxpeople sxo JOIN system s ON sxo.SystemID = s.ID WHERE sxo.Object_x_ipid = oxp.ID LIMIT 1),\n" +
				"        (SELECT prod.id FROM product_x_objectxpeople proxo JOIN product prod ON proxo.Product_ID = prod.ID WHERE proxo.Object_x_ip = oxp.ID LIMIT 1),\n" +
				"        (SELECT pj.id FROM project_x_objectxpeople pjxo JOIN project pj ON pjxo.Project_ID = pj.ID WHERE pjxo.Object_x_ip = oxp.ID LIMIT 1),\n" +
				"        (SELECT a.ID FROM attribute_x_objectxpeople axo JOIN attribute a ON axo.AttributeID = a.ID WHERE axo.Object_x_ipid = oxp.ID LIMIT 1),\n" +
				"        (SELECT po.ID FROM policy_x_objectxpeople pox JOIN policy po ON pox.Policy_ID = po.ID WHERE pox.Object_X_IP = oxp.ID LIMIT 1),\n" +
				"        (SELECT si.id FROM interface_x_objectxpeople sixo JOIN interface si ON sixo.InterfaceID = si.ID WHERE sixo.Object_x_ipid = oxp.ID LIMIT 1),\n" +
				"        (SELECT reg.ID FROM regulation_x_objectxpeople regxo JOIN regulation reg ON regxo.RegulationID = reg.ID WHERE regxo.Object_x_ipid = oxp.ID LIMIT 1),\n" +
				"        (SELECT com.ID FROM committee_x_objectxpeople comxo JOIN committee com ON comxo.Committee_ID = com.ID WHERE comxo.Object_X_ipid = oxp.ID LIMIT 1),\n" +
				"        (SELECT cl.ID FROM client_x_objectxpeople clxo JOIN client cl ON clxo.ClientID = cl.ID WHERE clxo.Object_x_ipid = oxp.ID LIMIT 1),\n" +
				"        (SELECT le.ID FROM legal_x_objectxpeople lexo JOIN Legal le ON lexo.Legal_ID = le.ID WHERE lexo.Object_X_IP = oxp.ID LIMIT 1),\n" +
				"        (SELECT ba.ID FROM businessarea_x_objectxpeople baxo JOIN business_area ba ON baxo.BusinessAreaID = ba.ID WHERE baxo.Object_x_ipid = oxp.ID LIMIT 1),\n" +
				"        (SELECT cap.ID FROM capability_x_objectxpeople capxo JOIN capability cap ON capxo.CapabilityID = cap.ID WHERE capxo.Object_x_ipid = oxp.ID LIMIT 1)\n" +
				"    ) AS 'Object_ID',\n" +
				"    CASE \n" +
				"        WHEN oxp.AcceptedID IS NOT NULL THEN 'yes'\n" +
				"        ELSE 'no'\n" +
				"    END AS 'Role Accepted',\n" +
				"    ra.CreateDatetime AS 'Date Accepted'\n" +
				"FROM object_x_people oxp\n" +
				"JOIN object_role orl ON oxp.RoleID = orl.ID\n" +
				"LEFT JOIN object_role_type ort ON orl.ObjectRoleType_ID = ort.ID\n" +
				"LEFT JOIN people p ON oxp.ipid = p.ID\n" +
				"LEFT JOIN module m ON orl.Module = m.ID\n" +
				"LEFT JOIN roleaccepted ra ON oxp.AcceptedID = ra.ID\n" +
				"WHERE (p.Deleted_date IS NULL OR p.ID IS NULL)\n" +
				"ORDER BY oxp.ID DESC";
	}
}
