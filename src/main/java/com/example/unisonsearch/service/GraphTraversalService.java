package com.example.unisonsearch.service;

import com.example.budg_v2.service.SegmentAccessService;
import com.example.unisonsearch.model.FacetResult;
import com.example.unisonsearch.model.SegmentAccessContext;
import com.example.unisonsearch.model.TraversalScope;
import com.example.unisonsearch.model.TraversalStats;
import com.example.unisonsearch.repository.DatabaseHelper;

import java.sql.SQLException;
import java.util.*;

/**
 * Service for traversing relationships between facets using graph traversal (BFS).
 * NEW VERSION: Uses RelationshipService and ALLOWED_TARGETS instead of RELATIONSHIP_MAP.
 * Finds all connected objects across facets starting from seed objects.
 */
public class GraphTraversalService {

    @SuppressWarnings("unused")
    private final DatabaseHelper databaseHelper;
    @SuppressWarnings("unused")
    private final RelationshipManager relationshipManager;
    private final RelationshipService relationshipService;

    public GraphTraversalService(RelationshipManager relationshipManager, DatabaseHelper databaseHelper) {
        this.relationshipManager = relationshipManager;
        this.databaseHelper = databaseHelper;
        this.relationshipService = new RelationshipServiceImpl(databaseHelper);
    }

    /**
     * Find all connected objects across facets using BFS traversal.
     * Performance optimization: limits seed set to 1000 to prevent performance issues.
     */
    public Map<String, FacetResult> findConnectedObjects(String seedFacet, Set<Integer> seedIds, int maxDepth) throws SQLException {
        return findConnectedObjects(seedFacet, seedIds, maxDepth, null);
    }

    /**
     * Find all connected objects with optional facet-level post-filtering.
     * FacetFilter can prune IDs per facet (e.g., applying search filters) after traversal.
     */
    public Map<String, FacetResult> findConnectedObjects(String seedFacet,
                                                         Set<Integer> seedIds,
                                                         int maxDepth,
                                                         FacetFilter facetFilter) throws SQLException {
        return findConnectedObjects(seedFacet, seedIds, maxDepth, facetFilter, null, null, null, null);
    }

    /**
     * Find all connected objects with rooted scope constraint and segment access enforcement.
     * This is the main traversal method used by UnisonSearchService.
     * 
     * NEW VERSION: Uses RelationshipService and ALLOWED_TARGETS instead of RELATIONSHIP_MAP.
     * 
     * @param seedFacet Starting facet
     * @param seedIds Starting object IDs
     * @param maxDepth Maximum traversal depth
     * @param facetFilter Optional post-traversal filter (deprecated, use rootScope instead)
     * @param rootScope Optional rooted universe constraint (applied PRE-enqueue)
     * @param accessCtx Optional segment access context (enforced in SQL)
     * @param statsOut Optional stats object to record truncation
     * @param correlationId Optional correlation ID for logging
     * @return Map of facet results
     * @throws SQLException if database error occurs
     */
    public Map<String, FacetResult> findConnectedObjects(String seedFacet,
                                                         Set<Integer> seedIds,
                                                         int maxDepth,
                                                         FacetFilter facetFilter,
                                                         TraversalScope rootScope,
                                                         SegmentAccessContext accessCtx,
                                                         TraversalStats statsOut,
                                                         String correlationId) throws SQLException {
        String logPrefix = correlationId != null ? "[" + correlationId + "] " : "";
        
        if (seedIds == null || seedIds.isEmpty()) {
            return new HashMap<>();
        }
        
        // Limit seed set to 1000 for performance
        if (seedIds.size() > 1000) {
            seedIds = seedIds.stream().limit(1000).collect(java.util.stream.Collectors.toSet());
        }

        String normalizedSeedFacet = normalizeFacetName(seedFacet);
        
        // Apply rootScope constraint to initial seeds if scope is present
        if (rootScope != null && rootScope.hasConstraints()) {
            Set<Integer> scopedSeeds = new HashSet<>(seedIds);
            scopedSeeds.retainAll(rootScope.getAllowedIds(normalizedSeedFacet));
            seedIds = scopedSeeds;
            
            if (seedIds.isEmpty()) {
                return new HashMap<>();
            }
        }
        
        Map<String, Set<Integer>> visited = new HashMap<>();
        visited.put(normalizedSeedFacet, new HashSet<>(seedIds));
        Map<String, Map<Integer, Integer>> depthByFacet = new HashMap<>();
        Map<Integer, Integer> seedDepths = new HashMap<>();
        for (Integer id : seedIds) {
            seedDepths.put(id, 0);
        }
        depthByFacet.put(normalizedSeedFacet, seedDepths);

        Queue<TraversalNode> queue = new LinkedList<>();
        queue.offer(new TraversalNode(normalizedSeedFacet, seedIds, 0));
        
        // Early stopping: limit total results to prevent performance issues
        int maxTotalResults = 10000;
        int totalResults = seedIds.size();
        int maxDepthReached = 0;
        boolean wasTruncated = false;

        while (!queue.isEmpty()) {
            TraversalNode node = queue.poll();
            
            if (node.depth >= maxDepth) {
                continue;
            }
            
            maxDepthReached = Math.max(maxDepthReached, node.depth);
            
            // Early stopping if we have too many results
            if (totalResults >= maxTotalResults) {
                wasTruncated = true;
                break;
            }

            String currentFacet = node.facet;
            Set<Integer> currentIds = node.ids;

            // NEW: Use ALLOWED_TARGETS instead of RELATIONSHIP_MAP
            Set<String> allowedTargets = RelationshipManager.ALLOWED_TARGETS.get(currentFacet);
            if (allowedTargets == null || allowedTargets.isEmpty()) {
                continue;
            }
            
            // Check depth limit for this facet
            Integer depthLimit = RelationshipManager.DEPTH_LIMITS.get(currentFacet.toUpperCase());
            if (depthLimit == null) {
                depthLimit = RelationshipManager.DEPTH_LIMITS.get("DEFAULT");
            }
            if (depthLimit != null && node.depth >= depthLimit) {
                continue;
            }

            for (String targetFacet : allowedTargets) {
                String normalizedTargetFacet = normalizeFacetName(targetFacet);
                Set<Integer> targetVisited = visited.computeIfAbsent(normalizedTargetFacet, k -> new HashSet<>());
                Map<Integer, Integer> targetDepths = depthByFacet.computeIfAbsent(normalizedTargetFacet, k -> new HashMap<>());

                Set<Integer> connectedIds = new HashSet<>();
                
                try {
                    // NEW: Use RelationshipService instead of SQL conditions
                    // Query relationships for all source IDs
                    for (Integer sourceId : currentIds) {
                        List<Integer> relatedIds = getRelatedIds(relationshipService, currentFacet, sourceId, targetFacet);
                        connectedIds.addAll(relatedIds);
                    }
                    
                    } catch (Exception e) {
                    // Skip this relationship on error
                    System.err.println(logPrefix + "GraphTraversalService: Error getting relationships from " + 
                                     currentFacet + " to " + targetFacet + ": " + e.getMessage());
                    continue;
                }

                // Apply rootScope constraint PRE-ENQUEUE (prevents path leakage)
                if (rootScope != null && rootScope.hasConstraints()) {
                    Set<Integer> scopedIds = new HashSet<>(connectedIds);
                    scopedIds.retainAll(rootScope.getAllowedIds(normalizedTargetFacet));
                    connectedIds = scopedIds;
                }

                connectedIds = applySegmentAccessContext(normalizedTargetFacet, connectedIds, accessCtx);

                Set<Integer> newIds = new HashSet<>();
                for (Integer id : connectedIds) {
                    if (!targetVisited.contains(id)) {
                        newIds.add(id);
                        targetVisited.add(id);
                        // Record minimal depth for this ID
                        int newDepth = node.depth + 1;
                        int existingDepth = targetDepths.getOrDefault(id, Integer.MAX_VALUE);
                        targetDepths.put(id, Math.min(existingDepth, newDepth));

                        totalResults++;
                        
                        // Early stopping check
                        if (totalResults >= maxTotalResults) {
                            wasTruncated = true;
                            break;
                        }
                    }
                }
                
                // Early stopping check
                if (totalResults >= maxTotalResults) {
                    wasTruncated = true;
                    break;
                }

                if (!newIds.isEmpty()) {
                    queue.offer(new TraversalNode(normalizedTargetFacet, newIds, node.depth + 1));
                }
            }
        }

        // Record stats for caller
        if (statsOut != null) {
            statsOut.setTotalResults(totalResults);
            statsOut.setTruncated(wasTruncated);
            statsOut.setMaxDepthReached(maxDepthReached);
        }

        // Apply optional facet-level filtering after traversal
        if (facetFilter != null) {
            for (Map.Entry<String, Set<Integer>> entry : new HashMap<>(visited).entrySet()) {
                String normalizedFacet = entry.getKey();
                Set<Integer> ids = entry.getValue();
                if (ids == null || ids.isEmpty()) {
                    continue;
                }
                try {
                Set<Integer> filtered = facetFilter.filter(normalizedFacet, ids);
                visited.put(normalizedFacet, filtered);
                // Align depth map to filtered IDs only
                Map<Integer, Integer> depths = depthByFacet.getOrDefault(normalizedFacet, new HashMap<>());
                depths.keySet().retainAll(filtered);
                depthByFacet.put(normalizedFacet, depths);
                } catch (SQLException e) {
                    System.err.println(logPrefix + "GraphTraversalService: Error applying facet filter: " + e.getMessage());
                }
            }
        }

        Map<String, FacetResult> results = new HashMap<>();
        for (Map.Entry<String, Set<Integer>> entry : visited.entrySet()) {
            String normalizedFacet = entry.getKey();
            Set<Integer> ids = entry.getValue();
            
            String facetId = normalizedFacetToFacetId(normalizedFacet);
            boolean hasActiveFilter = normalizedFacet.equals(normalizedSeedFacet);
            Map<Integer, Integer> depthMap = depthByFacet.getOrDefault(normalizedFacet, Collections.emptyMap());
            results.put(facetId, new FacetResult(ids, hasActiveFilter, depthMap));
        }

        return results;
    }

    private Set<Integer> applySegmentAccessContext(String normalizedFacet, Set<Integer> ids, SegmentAccessContext accessCtx)
            throws SQLException {
        if (ids == null || ids.isEmpty() || accessCtx == null) {
            return ids;
        }

        String objectType = mapFacetToObjectType(normalizedFacet);
        if (objectType == null) {
            return ids;
        }

        if (accessCtx.isAnonymous()) {
            Set<Integer> visibleIds = new HashSet<>();
            for (Integer id : ids) {
                int segmentId = SegmentAccessService.getObjectSegmentId(id, objectType);
                if (segmentId == -1 || segmentId == 1) {
                    visibleIds.add(id);
                }
            }
            return visibleIds;
        }

        Integer userId = accessCtx.getUserId();
        if (userId == null || userId <= 0) {
            return ids;
        }

        return SegmentAccessService.filterBySelectedSegments(userId, objectType, new ArrayList<>(ids));
    }

    private String mapFacetToObjectType(String normalizedFacet) {
        if (normalizedFacet == null) {
            return null;
        }

        return switch (normalizedFacet.toLowerCase()) {
            case "dataset" -> "Dataset";
            case "attribute" -> "Dataset";
            case "system" -> "System";
            case "glossary" -> "Glossary";
            case "process" -> "Process";
            case "project" -> "Project";
            case "product" -> "Product";
            case "policy" -> "Policy";
            case "business_area", "businessarea" -> "BusinessArea";
            case "capability" -> "Capability";
            case "client" -> "Client";
            case "committee" -> "Committee";
            case "org_unit", "orgunit" -> "OrgUnit";
            case "geography" -> "Geography";
            case "regulation" -> "Regulation";
            case "regulator" -> "Regulator";
            case "regulatory_theme", "regulatorytheme" -> "RegulatoryTheme";
            case "interface", "systeminterface", "system_interface" -> "SystemInterface";
            case "legal_entity", "legalentity", "legal" -> "LegalEntity";
            default -> null;
        };
    }

    /**
     * Get related IDs using RelationshipService based on source and target facets.
     */
    private List<Integer> getRelatedIds(RelationshipService relationshipService, String sourceFacet, int sourceId, String targetFacet) throws SQLException {
        String normalizedSource = normalizeFacetName(sourceFacet);
        String normalizedTarget = normalizeFacetName(targetFacet);
        
        // Check if this is a special relationship
        boolean isSpecial = RelationshipManager.isSpecialRelationship(normalizedSource, normalizedTarget);
        
        // Use appropriate RelationshipService method based on facet combination
        if ("dataset".equals(normalizedSource)) {
            if ("system".equals(normalizedTarget)) {
                Integer systemId = relationshipService.getSystemOfDataset(sourceId);
                return systemId != null ? List.of(systemId) : Collections.emptyList();
            } else if ("attribute".equals(normalizedTarget)) {
                return relationshipService.getAttributesByDataset(sourceId);
            } else if ("glossary".equals(normalizedTarget)) {
                return relationshipService.getGlossariesByDatasetIncludingAttributes(sourceId);
            } else if ("interface".equals(normalizedTarget)) {
                return relationshipService.getInterfacesByDataset(sourceId);
            } else if ("project".equals(normalizedTarget)) {
                LinkedHashSet<Integer> projectIds = new LinkedHashSet<>(relationshipService.getProjectsByDataset(sourceId));
                Integer sysForGov = relationshipService.getSystemOfDataset(sourceId);
                if (sysForGov != null) {
                    projectIds.addAll(relationshipService.getProjectsBySystem(sysForGov));
                }
                return new ArrayList<>(projectIds);
            } else if ("process".equals(normalizedTarget)) {
                LinkedHashSet<Integer> processIds = new LinkedHashSet<>(relationshipService.getProcessesByDataset(sourceId));
                Integer sysForGov = relationshipService.getSystemOfDataset(sourceId);
                if (sysForGov != null) {
                    processIds.addAll(relationshipService.getProcessesBySystem(sysForGov));
                }
                return new ArrayList<>(processIds);
            } else if ("policy".equals(normalizedTarget)) {
                LinkedHashSet<Integer> policyIds = new LinkedHashSet<>(relationshipService.getPoliciesByDataset(sourceId));
                Integer sysForGov = relationshipService.getSystemOfDataset(sourceId);
                if (sysForGov != null) {
                    policyIds.addAll(relationshipService.getPoliciesBySystem(sysForGov));
                }
                return new ArrayList<>(policyIds);
            } else if ("legal_entity".equals(normalizedTarget) || "legalentity".equals(normalizedTarget)
                    || "legal".equals(normalizedTarget)) {
                return relationshipService.getLegalEntitiesByDataset(sourceId);
            } else if ("client".equals(normalizedTarget)) {
                return relationshipService.getClientsByDataset(sourceId);
            }
        } else if ("system".equals(normalizedSource)) {
            if ("dataset".equals(normalizedTarget)) {
                return relationshipService.getDatasetsBySystem(sourceId);
            } else if ("attribute".equals(normalizedTarget)) {
                return relationshipService.getAllAttributesBySystem(sourceId);
            } else if ("glossary".equals(normalizedTarget)) {
                return relationshipService.getGlossariesBySystem(sourceId);
            } else if ("interface".equals(normalizedTarget)) {
                return relationshipService.getInterfacesBySystem(sourceId);
            } else if ("project".equals(normalizedTarget)) {
                return relationshipService.getProjectsBySystem(sourceId);
            } else if ("process".equals(normalizedTarget)) {
                return relationshipService.getProcessesBySystem(sourceId);
            } else if ("policy".equals(normalizedTarget)) {
                return relationshipService.getPoliciesBySystem(sourceId);
            }
        } else if ("glossary".equals(normalizedSource)) {
            if ("dataset".equals(normalizedTarget)) {
                return relationshipService.getDatasetsByGlossary(sourceId);
            } else if ("attribute".equals(normalizedTarget)) {
                return relationshipService.getAttributesByGlossary(sourceId);
            } else if ("system".equals(normalizedTarget) && isSpecial) {
                return relationshipService.getSystemByGlossary(sourceId);
            } else if ("project".equals(normalizedTarget)) {
                return relationshipService.getProjectsByGlossary(sourceId);
            } else if ("process".equals(normalizedTarget)) {
                return relationshipService.getProcessesByGlossary(sourceId);
            } else if ("policy".equals(normalizedTarget)) {
                return relationshipService.getPoliciesByGlossary(sourceId);
            }
        } else if ("attribute".equals(normalizedSource)) {
            if ("dataset".equals(normalizedTarget)) {
                Integer datasetId = relationshipService.getDatasetOfAttribute(sourceId);
                return datasetId != null ? List.of(datasetId) : Collections.emptyList();
            } else if ("glossary".equals(normalizedTarget)) {
                Integer glossaryId = relationshipService.getGlossaryOfAttribute(sourceId);
                return glossaryId != null ? List.of(glossaryId) : Collections.emptyList();
            } else if ("system".equals(normalizedTarget) && isSpecial) {
                Integer systemId = relationshipService.getSystemOfAttribute(sourceId);
                return systemId != null ? List.of(systemId) : Collections.emptyList();
            } else if ("project".equals(normalizedTarget)) {
                return relationshipService.getProjectsByAttribute(sourceId);
            } else if ("process".equals(normalizedTarget)) {
                return relationshipService.getProcessesByAttribute(sourceId);
            } else if ("policy".equals(normalizedTarget)) {
                return relationshipService.getPoliciesByAttribute(sourceId);
            }
        } else if ("interface".equals(normalizedSource)) {
            if ("system".equals(normalizedTarget)) {
                return relationshipService.getSystemsByInterface(sourceId);
            } else if ("dataset".equals(normalizedTarget) && isSpecial) {
                return relationshipService.getDatasetsByInterface(sourceId);
            }
        } else if ("project".equals(normalizedSource)) {
            if ("glossary".equals(normalizedTarget)) {
                return relationshipService.getGlossariesByProject(sourceId);
            } else if ("dataset".equals(normalizedTarget) && isSpecial) {
                return relationshipService.getDatasetsByProject(sourceId);
            } else if ("attribute".equals(normalizedTarget) && isSpecial) {
                return relationshipService.getAttributesByProject(sourceId);
            } else if ("system".equals(normalizedTarget) && isSpecial) {
                return relationshipService.getSystemsByProject(sourceId);
            }
        } else if ("process".equals(normalizedSource)) {
            if ("glossary".equals(normalizedTarget)) {
                return relationshipService.getGlossariesByProcess(sourceId);
            } else if ("dataset".equals(normalizedTarget) && isSpecial) {
                return relationshipService.getDatasetsByProcess(sourceId);
            } else if ("attribute".equals(normalizedTarget) && isSpecial) {
                return relationshipService.getAttributesByProcess(sourceId);
            } else if ("system".equals(normalizedTarget) && isSpecial) {
                return relationshipService.getSystemsByProcess(sourceId);
            }
        } else if ("policy".equals(normalizedSource)) {
            if ("glossary".equals(normalizedTarget)) {
                return relationshipService.getGlossariesByPolicy(sourceId);
            } else if ("dataset".equals(normalizedTarget) && isSpecial) {
                return relationshipService.getDatasetsByPolicy(sourceId);
            } else if ("attribute".equals(normalizedTarget) && isSpecial) {
                return relationshipService.getAttributesByPolicy(sourceId);
            } else if ("system".equals(normalizedTarget) && isSpecial) {
                return relationshipService.getSystemsByPolicy(sourceId);
            }
        } else if ("capability".equals(normalizedSource)) {
            if ("glossary".equals(normalizedTarget)) {
                return relationshipService.getGlossariesByCapability(sourceId);
            } else if ("dataset".equals(normalizedTarget)) {
                return relationshipService.getDatasetsByCapability(sourceId);
            } else if ("attribute".equals(normalizedTarget)) {
                return relationshipService.getAttributesByCapability(sourceId);
            } else if ("system".equals(normalizedTarget)) {
                return relationshipService.getSystemsByCapability(sourceId);
            }
        } else if ("product".equals(normalizedSource)) {
            if ("glossary".equals(normalizedTarget)) {
                return relationshipService.getGlossariesByProduct(sourceId);
            } else if ("dataset".equals(normalizedTarget)) {
                return relationshipService.getDatasetsByProduct(sourceId);
            } else if ("system".equals(normalizedTarget)) {
                return relationshipService.getSystemsByProduct(sourceId);
            } else if ("attribute".equals(normalizedTarget) && isSpecial) {
                return relationshipService.getAttributesByProduct(sourceId);
            }
        } else if ("business_area".equals(normalizedSource) || "businessarea".equals(normalizedSource)) {
            if ("glossary".equals(normalizedTarget)) {
                return relationshipService.getGlossariesByBusinessArea(sourceId);
            } else if ("process".equals(normalizedTarget)) {
                return relationshipService.getProcessesByBusinessArea(sourceId);
            } else if ("system".equals(normalizedTarget)) {
                return relationshipService.getSystemsByBusinessArea(sourceId);
            } else if ("dataset".equals(normalizedTarget) && isSpecial) {
                return relationshipService.getDatasetsByBusinessArea(sourceId);
            } else if ("attribute".equals(normalizedTarget) && isSpecial) {
                return relationshipService.getAttributesByBusinessArea(sourceId);
            }
        } else if ("client".equals(normalizedSource)) {
            if ("glossary".equals(normalizedTarget)) {
                return relationshipService.getGlossariesByClient(sourceId);
            } else if ("dataset".equals(normalizedTarget)) {
                return relationshipService.getDatasetsByClient(sourceId);
            } else if ("system".equals(normalizedTarget)) {
                return relationshipService.getSystemsByClient(sourceId);
            } else if ("policy".equals(normalizedTarget)) {
                return relationshipService.getPoliciesByClient(sourceId);
            } else if ("process".equals(normalizedTarget)) {
                return relationshipService.getProcessesByClient(sourceId);
            } else if ("project".equals(normalizedTarget)) {
                return relationshipService.getProjectsByClient(sourceId);
            } else if ("attribute".equals(normalizedTarget) && isSpecial) {
                return relationshipService.getAttributesByClient(sourceId);
            }
        } else if ("committee".equals(normalizedSource)) {
            if ("capability".equals(normalizedTarget)) {
                return relationshipService.getCapabilitiesByCommittee(sourceId);
            } else if ("committee".equals(normalizedTarget)) {
                return relationshipService.getCommitteesByCommittee(sourceId);
            } else if ("glossary".equals(normalizedTarget) && isSpecial) {
                return relationshipService.getGlossariesByCommittee(sourceId);
            } else if ("dataset".equals(normalizedTarget) && isSpecial) {
                return relationshipService.getDatasetsByCommittee(sourceId);
            } else if ("attribute".equals(normalizedTarget) && isSpecial) {
                return relationshipService.getAttributesByCommittee(sourceId);
            } else if ("system".equals(normalizedTarget) && isSpecial) {
                return relationshipService.getSystemsByCommittee(sourceId);
            }
        } else if ("legal_entity".equals(normalizedSource) || "legalentity".equals(normalizedSource) || "legal".equals(normalizedSource)) {
            if ("geography".equals(normalizedTarget)) {
                return relationshipService.getGeographiesByLegalEntity(sourceId);
            } else if ("dataset".equals(normalizedTarget)) {
                return relationshipService.getDatasetsByLegalEntity(sourceId);
            } else if ("system".equals(normalizedTarget) && isSpecial) {
                return relationshipService.getSystemsByLegalEntity(sourceId);
            } else if ("attribute".equals(normalizedTarget) && isSpecial) {
                return relationshipService.getAttributesByLegalEntity(sourceId);
            }
        } else if ("regulation".equals(normalizedSource)) {
            if ("regulator".equals(normalizedTarget)) {
                return relationshipService.getRegulatorsByRegulation(sourceId);
            } else if ("regulatory_theme".equals(normalizedTarget) || "regulatorytheme".equals(normalizedTarget)) {
                return relationshipService.getRegulatoryThemesByRegulation(sourceId);
            } else if ("geography".equals(normalizedTarget) && isSpecial) {
                return relationshipService.getGeographiesByRegulation(sourceId);
            }
        } else if ("regulator".equals(normalizedSource)) {
            if ("geography".equals(normalizedTarget)) {
                return relationshipService.getGeographiesByRegulator(sourceId);
            } else if ("regulation".equals(normalizedTarget)) {
                return relationshipService.getRegulationsByRegulator(sourceId);
            } else if ("regulatory_theme".equals(normalizedTarget) && isSpecial) {
                return relationshipService.getRegulatoryThemesByRegulator(sourceId);
            } else if ("regulatorytheme".equals(normalizedTarget) && isSpecial) {
                return relationshipService.getRegulatoryThemesByRegulator(sourceId);
            }
        } else if ("regulatory_theme".equals(normalizedSource) || "regulatorytheme".equals(normalizedSource)) {
            if ("regulation".equals(normalizedTarget)) {
                return relationshipService.getRegulationsByRegulatoryTheme(sourceId);
            } else if ("regulator".equals(normalizedTarget) && isSpecial) {
                return relationshipService.getRegulatorsByRegulatoryTheme(sourceId);
            } else if ("geography".equals(normalizedTarget) && isSpecial) {
                return relationshipService.getGeographiesByRegulatoryTheme(sourceId);
            }
        } else if ("geography".equals(normalizedSource)) {
            if ("regulator".equals(normalizedTarget)) {
                return relationshipService.getRegulatorsByGeography(sourceId);
            } else if ("regulation".equals(normalizedTarget) && isSpecial) {
                return relationshipService.getRegulationsByGeography(sourceId);
            } else if ("regulatory_theme".equals(normalizedTarget) && isSpecial) {
                return relationshipService.getRegulatoryThemesByGeography(sourceId);
            } else if ("regulatorytheme".equals(normalizedTarget) && isSpecial) {
                return relationshipService.getRegulatoryThemesByGeography(sourceId);
            }
        } else if ("person".equals(normalizedSource) || "people".equals(normalizedSource)) {
            if ("orgunit".equals(normalizedTarget) || "org_unit".equals(normalizedTarget)) {
                return relationshipService.getOrgUnitsByPerson(sourceId);
            } else if ("role".equals(normalizedTarget)) {
                return relationshipService.getRolesByPerson(sourceId);
            } else if ("changerequest".equals(normalizedTarget) || "change_request".equals(normalizedTarget)) {
                return relationshipService.getChangeRequestsByPerson(sourceId);
            } else if ("task".equals(normalizedTarget) || "active_task".equals(normalizedTarget) || "activetask".equals(normalizedTarget)) {
                return relationshipService.getActiveTasksByPerson(sourceId);
            }
        } else if ("orgunit".equals(normalizedSource) || "org_unit".equals(normalizedSource)) {
            if ("person".equals(normalizedTarget) || "people".equals(normalizedTarget)) {
                return relationshipService.getPeopleByOrgUnit(sourceId);
            } else if ("object".equals(normalizedTarget)) {
                return relationshipService.getObjectsByOrgUnit(sourceId);
            }
        }
        
        // Default: return empty list if relationship not found
        return Collections.emptyList();
    }

    /**
     * Normalize facet name to canonical form.
     * Uses FacetNormalizationUtil for consistency across the codebase.
     */
    private String normalizeFacetName(String facet) {
        if (facet == null) return null;
        // Use FacetNormalizationUtil to normalize to lowercase canonical form
        return com.example.unisonsearch.util.FacetNormalizationUtil.normalizeToLowercase(facet);
    }

    /**
     * Convert normalized facet name to facet ID (uppercase).
     */
    private String normalizedFacetToFacetId(String normalizedFacet) {
        if (normalizedFacet == null) return null;
        return normalizedFacet.toUpperCase();
    }

    /**
     * Internal class for BFS queue nodes.
     */
    private static class TraversalNode {
        final String facet;
        final Set<Integer> ids;
        final int depth;

        TraversalNode(String facet, Set<Integer> ids, int depth) {
            this.facet = facet;
            this.ids = ids;
            this.depth = depth;
        }
    }

    /**
     * Functional interface for post-traversal facet filtering.
     */
    @FunctionalInterface
    public interface FacetFilter {
        Set<Integer> filter(String normalizedFacetId, Set<Integer> ids) throws SQLException;
    }
}
