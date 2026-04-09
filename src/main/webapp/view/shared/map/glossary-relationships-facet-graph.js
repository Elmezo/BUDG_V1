/**
 * glossary-relationships-facet-graph.js - data load and buildGraph for glossary relationships map.
 * Load after map-graph-utils.js.
 */
(function () {
    'use strict';

    window.createGlossaryRelationshipsFacetGraphPipeline = function (ctx) {
        var state = ctx.state;
        var adapter = ctx.adapter;
        var renderNetwork = ctx.renderNetwork;
        var showPlaceholder = ctx.showPlaceholder;
        var showLoading = ctx.showLoading;
        var hideLoading = ctx.hideLoading;
        var updateLegend = ctx.updateLegend;
        var populateFilterOptions = ctx.populateFilterOptions;
        var logPrefix = ctx.logPrefix || '[GLOSSARY-RELATIONSHIPS-MAP]';
        var _cachedGraph = null;

        function facetLog() {
            if (typeof window !== 'undefined' && window.BUDG_DEBUG_FACET_MAPS === true) {
                console.log.apply(console, arguments);
            }
        }
        function facetWarn() {
            if (typeof window !== 'undefined' && window.BUDG_DEBUG_FACET_MAPS === true) {
                console.warn.apply(console, arguments);
            }
        }

        function invalidateGraphCache() {
            _cachedGraph = null;
        }

        async function loadMapData(glossaryId) {
            showLoading();
            try {
                const glossaryResponse = await window.BUDG_API_SERVICE.getGlossaryById(glossaryId);
                state.glossaryData = glossaryResponse?.data || glossaryResponse;
                facetLog(logPrefix + ' Glossary data:', state.glossaryData);
                if (state.mapType === 'glossary-lineage') {
                    await loadGlossaryLineageData(glossaryId);
                } else if (state.mapType === 'dataset-lineage') {
                    await loadDatasetLineageData(glossaryId);
                } else if (state.mapType === 'system-lineage') {
                    await loadSystemLineageData(glossaryId);
                }
                const graph = buildGraph();
                facetLog(logPrefix + ' Graph built:', graph.nodes.length, 'nodes,', graph.edges.length, 'edges');
                if (graph.nodes.length === 0) {
                    facetWarn(logPrefix + ' No nodes to display - check data loading');
                }
                renderNetwork(graph);
                if (typeof updateLegend === 'function') {
                    updateLegend();
                }
                if (typeof populateFilterOptions === 'function') {
                    populateFilterOptions().catch(function (err) {
                        facetWarn(logPrefix + ' Failed to populate filter options:', err);
                    });
                }
            } catch (error) {
                console.error(logPrefix + ' Failed to load map data:', error);
                showPlaceholder('Failed to load map data.');
            } finally {
                hideLoading();
            }
        }

        // Load glossary lineage data (linear relationships only, no siblings)
        async function loadGlossaryLineageData(glossaryId) {
        try {
            // Get all glossaries (API: getGlossaryList)
            const allGlossariesResp = await window.BUDG_API_SERVICE.getGlossaryList();
            const allGlossaries = Array.isArray(allGlossariesResp?.data)
                ? allGlossariesResp.data
                : (Array.isArray(allGlossariesResp) ? allGlossariesResp : []);
            
            state.allGlossaries = allGlossaries;
            facetLog('[GLOSSARY-RELATIONSHIPS-MAP] Loaded', allGlossaries.length, 'glossaries');
            
            // Get relationships where current glossary is the SOURCE (outgoing) - these are what show in the table
            const sourceRelationships = await window.BUDG_API_SERVICE.getGlossaryRelationshipsBySourceId(glossaryId);
            const sourceRels = Array.isArray(sourceRelationships) ? sourceRelationships : (Array.isArray(sourceRelationships?.data) ? sourceRelationships.data : []);
            
            // Get relationships where current glossary is the TARGET (incoming)
            const targetRelationships = await window.BUDG_API_SERVICE.getGlossaryRelationshipsByTargetId(glossaryId);
            const targetRels = Array.isArray(targetRelationships) ? targetRelationships : (Array.isArray(targetRelationships?.data) ? targetRelationships.data : []);
            
            // Combine both directions (remove duplicates)
            const allRelationships = [];
            const seenRelationships = new Set();
            
            [...sourceRels, ...targetRels].forEach(rel => {
                const relId = String(rel.id || rel.ID || `${rel.sourceGlossaryId || rel.SourceGlossaryID}_${rel.targetGlossaryId || rel.TargetGlossaryID}`);
                if (!seenRelationships.has(relId)) {
                    seenRelationships.add(relId);
                    allRelationships.push(rel);
                }
            });
            
            state.glossaryRelationships = allRelationships;
            facetLog('[GLOSSARY-RELATIONSHIPS-MAP] Loaded', allRelationships.length, 'relationships for glossary', glossaryId);
            facetLog('[GLOSSARY-RELATIONSHIPS-MAP] Source relationships:', sourceRels.length, 'Target relationships:', targetRels.length);
            facetLog('[GLOSSARY-RELATIONSHIPS-MAP] All relationships:', allRelationships);
            
            // Collect all glossary IDs from relationships to ensure we have them in the map
            const relatedGlossaryIds = new Set([String(glossaryId)]); // Always include current
            allRelationships.forEach(rel => {
                const sourceId = String(rel.sourceGlossaryId || rel.SourceGlossaryID);
                const targetId = String(rel.targetGlossaryId || rel.TargetGlossaryID);
                if (sourceId) relatedGlossaryIds.add(sourceId);
                if (targetId) relatedGlossaryIds.add(targetId);
            });
            
            facetLog('[GLOSSARY-RELATIONSHIPS-MAP] Related glossary IDs:', Array.from(relatedGlossaryIds));
            
            // Ensure all related glossaries are in the allGlossaries list
            // If not, we need to fetch them - THIS IS CRITICAL for the map to work
            const missingGlossaryIds = Array.from(relatedGlossaryIds).filter(id => {
                return !state.allGlossaries.some(g => String(g.id || g.ID) === id);
            });
            
            if (missingGlossaryIds.length > 0) {
                facetLog('[GLOSSARY-RELATIONSHIPS-MAP] Fetching', missingGlossaryIds.length, 'missing glossaries:', missingGlossaryIds);
                for (const missingId of missingGlossaryIds) {
                    try {
                        const glossary = await window.BUDG_API_SERVICE.getGlossaryById(missingId);
                        const glossaryData = glossary?.data || glossary;
                        if (glossaryData) {
                            state.allGlossaries.push(glossaryData);
                            facetLog('[GLOSSARY-RELATIONSHIPS-MAP] Fetched missing glossary:', missingId, glossaryData.name || glossaryData.Name);
                        } else {
                            facetWarn('[GLOSSARY-RELATIONSHIPS-MAP] Glossary', missingId, 'returned no data');
                        }
                    } catch (e) {
                        facetWarn('[GLOSSARY-RELATIONSHIPS-MAP] Failed to fetch glossary', missingId, e);
                    }
                }
            } else {
                facetLog('[GLOSSARY-RELATIONSHIPS-MAP] All related glossaries are already in allGlossaries list');
            }
            
            // Get relationship types for filters (from glossary_x_glossary_relationtype table)
            try {
                const relTypesResp = await window.BUDG_API_SERVICE.getGlossaryRelationTypes();
                state.relationshipTypes = Array.isArray(relTypesResp?.data)
                    ? relTypesResp.data
                    : (Array.isArray(relTypesResp) ? relTypesResp : []);
            } catch (e) {
                facetWarn('[GLOSSARY-RELATIONSHIPS-MAP] Failed to load relationship types:', e);
            }
            
            // Build full hierarchy: show entire connected component (all related glossaries)
            buildFullHierarchyGlossaries(glossaryId);
            
        } catch (error) {
            console.error('[GLOSSARY-RELATIONSHIPS-MAP] Error loading glossary lineage data:', error);
        }
    }

    // Build full hierarchy: show entire connected component of the relationship graph.
    // When you open any glossary (e.g. X or Customer Data_Proj), the map shows ALL glossaries
    // that are connected by any relationship path (Customer Data, x, Total Revenue, Customer Data_Proj, gloo, Products).
    function buildFullHierarchyGlossaries(glossaryId) {
        const glossaryMap = new Map();
        state.allGlossaries.forEach(glossary => {
            const id = String(glossary.id || glossary.ID);
            glossaryMap.set(id, glossary);
        });
        
        const currentId = String(glossaryId);
        const hierarchyGlossaries = new Set([currentId]);
        
        const relationships = state.glossaryRelationships;
        
        // Build adjacency: for each glossary id, set of neighbor ids (both directions)
        const neighbors = new Map();
        relationships.forEach(rel => {
            const sourceId = String(rel.sourceGlossaryId || rel.SourceGlossaryID);
            const targetId = String(rel.targetGlossaryId || rel.TargetGlossaryID);
            if (sourceId && targetId) {
                if (!neighbors.has(sourceId)) neighbors.set(sourceId, new Set());
                if (!neighbors.has(targetId)) neighbors.set(targetId, new Set());
                neighbors.get(sourceId).add(targetId);
                neighbors.get(targetId).add(sourceId);
            }
        });
        
        // BFS: expand from current node to entire connected component
        const queue = [currentId];
        const visited = new Set([currentId]);
        while (queue.length > 0) {
            const nodeId = queue.shift();
            const nodeNeighbors = neighbors.get(nodeId) || new Set();
            nodeNeighbors.forEach(neighborId => {
                if (!visited.has(neighborId)) {
                    visited.add(neighborId);
                    hierarchyGlossaries.add(neighborId);
                    queue.push(neighborId);
                }
            });
        }
        
        // Fallback: if we have relationships but only one node, add all related ids from relationships
        if (relationships.length > 0 && hierarchyGlossaries.size === 1) {
            relationships.forEach(rel => {
                const sourceId = String(rel.sourceGlossaryId || rel.SourceGlossaryID);
                const targetId = String(rel.targetGlossaryId || rel.TargetGlossaryID);
                if (sourceId === currentId && targetId) hierarchyGlossaries.add(targetId);
                if (targetId === currentId && sourceId) hierarchyGlossaries.add(sourceId);
            });
        }
        
        // Convert to array of glossary objects (reuse linearGlossaries state key)
        state.linearGlossaries = Array.from(hierarchyGlossaries).map(id => {
            const glossary = glossaryMap.get(id);
            if (!glossary) {
                if (id === currentId && state.glossaryData) {
                    const g = state.glossaryData;
                    return {
                        id: g.id || g.ID,
                        ID: g.id || g.ID,
                        name: g.name || g.Name,
                        Name: g.name || g.Name,
                        refNumber: g.refNumber || g.RefNumber,
                        RefNumber: g.refNumber || g.RefNumber
                    };
                }
            }
            return glossary;
        }).filter(Boolean);
    }

    // Load dataset lineage data
    // Linked datasets (orange): (1) dataset directly associated with opened glossary,
    // (2) one of the dataset's attributes is associated with opened glossary,
    // (3) dataset's glossary is in the hierarchy of the opened glossary.
    // Other datasets: connected to linked datasets via dataset relationships (shown in grey).
    async function loadDatasetLineageData(glossaryId) {
        try {
            state.linkedDatasets.clear();
            state.datasetsData.clear();
            state.datasetRelationships = [];
            
            const linkedDatasetIds = new Set();
            const currentId = String(glossaryId);
            
            // 1) Build glossary hierarchy (full connected component) for "dataset's glossary is in hierarchy"
            const hierarchyGlossaryIds = new Set([currentId]);
            let allRels = [];
            try {
                const relsResp = await window.BUDG_API_SERVICE.getGlossaryRelationships();
                allRels = Array.isArray(relsResp) ? relsResp : (Array.isArray(relsResp?.data) ? relsResp.data : []);
            } catch (e) {
                const srcRels = await window.BUDG_API_SERVICE.getGlossaryRelationshipsBySourceId(glossaryId);
                const tgtRels = await window.BUDG_API_SERVICE.getGlossaryRelationshipsByTargetId(glossaryId);
                const src = Array.isArray(srcRels) ? srcRels : (srcRels?.data || []);
                const tgt = Array.isArray(tgtRels) ? tgtRels : (tgtRels?.data || []);
                const seen = new Set();
                [...src, ...tgt].forEach(rel => {
                    const key = `${rel.sourceGlossaryId || rel.SourceGlossaryID}_${rel.targetGlossaryId || rel.TargetGlossaryID}`;
                    if (!seen.has(key)) { seen.add(key); allRels.push(rel); }
                });
            }
            const neighbors = new Map();
            allRels.forEach(rel => {
                const srcId = String(rel.sourceGlossaryId || rel.SourceGlossaryID);
                const tgtId = String(rel.targetGlossaryId || rel.TargetGlossaryID);
                if (srcId && tgtId) {
                    if (!neighbors.has(srcId)) neighbors.set(srcId, new Set());
                    if (!neighbors.has(tgtId)) neighbors.set(tgtId, new Set());
                    neighbors.get(srcId).add(tgtId);
                    neighbors.get(tgtId).add(srcId);
                }
            });
            const queue = [currentId];
            const visited = new Set([currentId]);
            while (queue.length > 0) {
                const nid = queue.shift();
                (neighbors.get(nid) || new Set()).forEach(nb => {
                    if (!visited.has(nb)) { visited.add(nb); hierarchyGlossaryIds.add(nb); queue.push(nb); }
                });
            }
            
            // 2) Linked: datasets directly associated with opened glossary
            const directDatasetsResp = await window.BUDG_API_SERVICE.getGlossaryDatasets(glossaryId);
            const directDatasets = Array.isArray(directDatasetsResp) ? directDatasetsResp : (directDatasetsResp?.data || []);
            directDatasets.forEach(ds => {
                const id = String(ds.id || ds.datasetId || ds.dataset_id || ds.ID);
                if (id) linkedDatasetIds.add(id);
            });
            
            // 3) Linked: datasets that have at least one attribute associated with opened glossary
            const attributesResp = await window.BUDG_API_SERVICE.getGlossaryAttributes(glossaryId);
            const directAttributes = Array.isArray(attributesResp) ? attributesResp : (attributesResp?.data || []);
            directAttributes.forEach(attr => {
                const id = String(attr.datasetId || attr.dataset_id || attr.Dataset_ID);
                if (id) linkedDatasetIds.add(id);
            });
            
            // 4) Linked: datasets whose glossary is in the hierarchy of the opened glossary
            for (const gid of hierarchyGlossaryIds) {
                if (gid === currentId) continue; // already added via direct
                try {
                    const dsResp = await window.BUDG_API_SERVICE.getGlossaryDatasets(gid);
                    const list = Array.isArray(dsResp) ? dsResp : (dsResp?.data || []);
                    list.forEach(ds => {
                        const id = String(ds.id || ds.datasetId || ds.dataset_id || ds.ID);
                        if (id) linkedDatasetIds.add(id);
                    });
                } catch (e) {
                    // ignore per-glossary errors
                }
            }
            
            // Mark linked in state
            linkedDatasetIds.forEach(id => state.linkedDatasets.add(id));
            
            // 5) Load linked dataset details and collect relationship edges (to find "other" datasets)
            const otherDatasetIds = new Set();
            for (const datasetId of linkedDatasetIds) {
                try {
                    const datasetData = await window.BUDG_API_SERVICE.getDatasetById(datasetId, null, { silent404: true });
                    const dataset = datasetData?.data || datasetData;
                    if (dataset) {
                        state.datasetsData.set(String(datasetId), dataset);
                    }
                    const relResp = await fetch(`/api/dataset-relationships/${datasetId}`, { method: 'GET', credentials: 'include', headers: { 'Content-Type': 'application/json' } });
                    if (relResp.ok) {
                        const relData = await relResp.json();
                        const allRelsList = [...(relData.inbound || []), ...(relData.outbound || [])];
                        allRelsList.forEach(rel => {
                            const srcId = String(rel.sourceDatasetId ?? rel.sourceId ?? '');
                            const tgtId = String(rel.targetDatasetId ?? rel.targetId ?? '');
                            if (srcId && tgtId) {
                                state.datasetRelationships.push({
                                    ...rel,
                                    sourceDatasetId: srcId,
                                    targetDatasetId: tgtId
                                });
                                if (!linkedDatasetIds.has(srcId)) otherDatasetIds.add(srcId);
                                if (!linkedDatasetIds.has(tgtId)) otherDatasetIds.add(tgtId);
                            }
                        });
                    }
                } catch (e) {
                    facetWarn('[GLOSSARY-RELATIONSHIPS-MAP] Error loading dataset', datasetId, e);
                }
            }
            
            // 6) Load "other" dataset details (connected but not linked)
            for (const datasetId of otherDatasetIds) {
                if (state.datasetsData.has(String(datasetId))) continue;
                try {
                    const datasetData = await window.BUDG_API_SERVICE.getDatasetById(datasetId, null, { silent404: true });
                    const dataset = datasetData?.data || datasetData;
                    if (dataset) {
                        state.datasetsData.set(String(datasetId), dataset);
                    }
                } catch (e) {
                    facetWarn('[GLOSSARY-RELATIONSHIPS-MAP] Error loading other dataset', datasetId, e);
                }
            }
            
        } catch (error) {
            console.error('[GLOSSARY-RELATIONSHIPS-MAP] Error loading dataset lineage data:', error);
        }
    }

    // Load system lineage data. Linked (orange): strategic source, or system owns linked dataset/attribute or dataset whose glossary in hierarchy. Other (grey): connected via interfaces/data flow.
    async function loadSystemLineageData(glossaryId) {
        try {
            state.linkedSystems.clear();
            state.systemsData.clear();
            state.interfacesData.length = 0;
            state.dataFlowData.length = 0;
            state.connectedSystems.clear();
            state.inaccessibleSystems.clear();
            
            const linkedSystemIds = new Set();
            const currentId = String(glossaryId);
            
            // Build glossary hierarchy (full connected component)
            const hierarchyGlossaryIds = new Set([currentId]);
            let allRels = [];
            try {
                const relsResp = await window.BUDG_API_SERVICE.getGlossaryRelationships();
                allRels = Array.isArray(relsResp) ? relsResp : (Array.isArray(relsResp?.data) ? relsResp.data : []);
            } catch (e) {
                const srcRels = await window.BUDG_API_SERVICE.getGlossaryRelationshipsBySourceId(glossaryId);
                const tgtRels = await window.BUDG_API_SERVICE.getGlossaryRelationshipsByTargetId(glossaryId);
                const src = Array.isArray(srcRels) ? srcRels : (srcRels?.data || []);
                const tgt = Array.isArray(tgtRels) ? tgtRels : (tgtRels?.data || []);
                const seen = new Set();
                [...src, ...tgt].forEach(rel => {
                    const key = `${rel.sourceGlossaryId || rel.SourceGlossaryID}_${rel.targetGlossaryId || rel.TargetGlossaryID}`;
                    if (!seen.has(key)) { seen.add(key); allRels.push(rel); }
                });
            }
            const neighbors = new Map();
            allRels.forEach(rel => {
                const srcId = String(rel.sourceGlossaryId || rel.SourceGlossaryID);
                const tgtId = String(rel.targetGlossaryId || rel.TargetGlossaryID);
                if (srcId && tgtId) {
                    if (!neighbors.has(srcId)) neighbors.set(srcId, new Set());
                    if (!neighbors.has(tgtId)) neighbors.set(tgtId, new Set());
                    neighbors.get(srcId).add(tgtId);
                    neighbors.get(tgtId).add(srcId);
                }
            });
            const queue = [currentId];
            const visited = new Set([currentId]);
            while (queue.length > 0) {
                const nid = queue.shift();
                (neighbors.get(nid) || new Set()).forEach(nb => {
                    if (!visited.has(nb)) { visited.add(nb); hierarchyGlossaryIds.add(nb); queue.push(nb); }
                });
            }
            
            try {
                const strategicResp = await window.BUDG_API_SERVICE.getGlossaryStrategicSource(glossaryId);
                const strategicList = Array.isArray(strategicResp) ? strategicResp : (strategicResp?.data || []);
                strategicList.forEach(item => {
                    const id = String(item.systemId || item.system_id || item.System_ID || item.id || '');
                    if (id) linkedSystemIds.add(id);
                });
            } catch (e) { /* ignore */ }
            
            const directDsResp = await window.BUDG_API_SERVICE.getGlossaryDatasets(glossaryId);
            const directDs = Array.isArray(directDsResp) ? directDsResp : (directDsResp?.data || []);
            directDs.forEach(ds => {
                const id = String(ds.systemId || ds.system_id || ds.System_ID || ds.masterSource || ds.MasterSource || '');
                if (id) linkedSystemIds.add(id);
            });
            
            const attrsResp = await window.BUDG_API_SERVICE.getGlossaryAttributes(glossaryId);
            const attrs = Array.isArray(attrsResp) ? attrsResp : (attrsResp?.data || []);
            attrs.forEach(attr => {
                const id = String(attr.systemId || attr.system_id || attr.System_ID || '');
                if (id) linkedSystemIds.add(id);
            });
            
            for (const gid of hierarchyGlossaryIds) {
                if (gid === currentId) continue;
                try {
                    const dsResp = await window.BUDG_API_SERVICE.getGlossaryDatasets(gid);
                    const list = Array.isArray(dsResp) ? dsResp : (dsResp?.data || []);
                    list.forEach(ds => {
                        const id = String(ds.systemId || ds.system_id || ds.System_ID || ds.masterSource || ds.MasterSource || '');
                        if (id) linkedSystemIds.add(id);
                    });
                } catch (e) { /* ignore */ }
            }
            
            linkedSystemIds.forEach(id => state.linkedSystems.add(id));
            
            const otherSystemIds = new Set();
            for (const systemId of linkedSystemIds) {
                try {
                    const systemData = await window.BUDG_API_SERVICE.getSystemById(systemId);
                    const data = systemData?.data || systemData;
                    if (data && !data.error) {
                        state.systemsData.set(String(systemId), data);
                    }
                    const interfaces = await window.BUDG_API_SERVICE.getSystemInterfaces(systemId);
                    const interfacesList = Array.isArray(interfaces?.data) ? interfaces.data : (Array.isArray(interfaces) ? interfaces : []);
                    state.interfacesData.push(...interfacesList);
                    const dataFlow = await window.BUDG_API_SERVICE.getDataFlowOutsideInterfaces(systemId);
                    const dataFlowList = Array.isArray(dataFlow?.data) ? dataFlow.data : (Array.isArray(dataFlow) ? dataFlow : []);
                    state.dataFlowData.push(...dataFlowList);
                    interfacesList.forEach(iface => {
                        const fromId = iface.fromId || iface.sourceSystemId;
                        const toId = iface.toId || iface.targetSystemId;
                        if (fromId && String(fromId) !== String(systemId)) otherSystemIds.add(String(fromId));
                        if (toId && String(toId) !== String(systemId)) otherSystemIds.add(String(toId));
                    });
                    dataFlowList.forEach(flow => {
                        const fromId = flow.fromId;
                        const toId = flow.toId;
                        if (fromId && String(fromId) !== String(systemId)) otherSystemIds.add(String(fromId));
                        if (toId && String(toId) !== String(systemId)) otherSystemIds.add(String(toId));
                    });
                } catch (e) {
                    facetWarn('[GLOSSARY-RELATIONSHIPS-MAP] Error loading system', systemId, e);
                }
            }
            
            for (const systemId of otherSystemIds) {
                if (state.systemsData.has(String(systemId))) continue;
                try {
                    const systemData = await window.BUDG_API_SERVICE.getSystemById(systemId);
                    const data = systemData?.data || systemData;
                    if (data && !data.error) {
                        state.systemsData.set(String(systemId), data);
                    } else if (data && data.error && (String(data.error).includes('403') || String(data.error).toLowerCase().includes('forbidden'))) {
                        state.inaccessibleSystems.add(String(systemId));
                    }
                } catch (e) {
                    const code = e.status || e.statusCode || (e.response && e.response.status);
                    const msg = (e.message || e.toString() || '').toLowerCase();
                    if (code === 403 || msg.includes('403') || msg.includes('forbidden')) {
                        state.inaccessibleSystems.add(String(systemId));
                    }
                }
            }
            
        } catch (error) {
            console.error('[GLOSSARY-RELATIONSHIPS-MAP] Error loading system lineage data:', error);
        }
    }

    // Build graph from loaded data
    function buildGraph() {
        const nodes = [];
        const edges = [];
        const edgeMap = new Map();
        
        if (state.mapType === 'glossary-lineage') {
            // Glossary lineage: show glossaries (orange = opened, black = other)
            const linearGlossaryIds = new Set();
            
            state.linearGlossaries.forEach(glossary => {
                const glossaryId = String(glossary.id || glossary.ID);
                linearGlossaryIds.add(glossaryId);
                const isCurrent = glossaryId === String(state.glossaryId);
                
                // Get glossary name - prioritize 'name' (from list API), then 'Name' (from getById)
                // Note: Glossaries use 'name' field (not primaryName like other entities)
                let glossaryName = glossary.name || glossary.Name;
                if (!glossaryName || glossaryName.trim() === '') {
                    facetWarn('[GLOSSARY-RELATIONSHIPS-MAP] Glossary', glossaryId, 'has no name field. Available fields:', Object.keys(glossary));
                    glossaryName = `Glossary ${glossaryId}`;
                }
                
                const glossaryRef = glossary.refNumber || glossary.RefNumber || glossary.ref || glossary.Ref_Number || `GL-${glossaryId}`;
                
                nodes.push({
                    id: `glossary_${glossaryId}`,
                    label: glossaryName, // Show only glossary name (no ref number) for Glossary Lineage
                    group: 'glossary',
                    isCurrent: isCurrent,
                    meta: {
                        glossaryId: glossaryId,
                        glossaryName: glossaryName,
                        refNumber: glossaryRef,
                        type: glossary.typeName || glossary.type || ''
                    }
                });
            });
            
            facetLog('[GLOSSARY-RELATIONSHIPS-MAP] Building graph with', nodes.length, 'nodes and', state.glossaryRelationships.length, 'relationships');
            
            // Add edges from relationships (only for linear glossaries)
            state.glossaryRelationships.forEach(rel => {
                const sourceId = String(rel.sourceGlossaryId || rel.SourceGlossaryID);
                const targetId = String(rel.targetGlossaryId || rel.TargetGlossaryID);
                
                // Only add edge if both source and target are in the linear glossaries set
                if (linearGlossaryIds.has(sourceId) && linearGlossaryIds.has(targetId)) {
                    const edgeKey = `${sourceId}_${targetId}`;
                    if (!edgeMap.has(edgeKey)) {
                        // Get relationship type name - check multiple possible field names
                        const relationTypeName = rel.relationTypeName || rel.RelationTypeName || rel.relationTypeName || rel.primaryName || rel.PrimaryName || '';
                        edges.push({
                            id: `glossary_rel_${edgeKey}`,
                            from: `glossary_${sourceId}`,
                            to: `glossary_${targetId}`,
                            label: relationTypeName,
                            lineStyle: 'dashed',
                            lineType: 'glossary-relationship',
                            relationType: String(rel.relationType || rel.RelationType || '')
                        });
                        edgeMap.set(edgeKey, true);
                        facetLog('[GLOSSARY-RELATIONSHIPS-MAP] Added edge:', sourceId, '→', targetId, '(', relationTypeName, ')');
                    }
                } else {
                    facetLog('[GLOSSARY-RELATIONSHIPS-MAP] Skipped edge - source in set:', linearGlossaryIds.has(sourceId), 'target in set:', linearGlossaryIds.has(targetId), 'source:', sourceId, 'target:', targetId);
                }
            });
            
            facetLog('[GLOSSARY-RELATIONSHIPS-MAP] Created', edges.length, 'edges from', state.glossaryRelationships.length, 'relationships');
            
        } else if (state.mapType === 'dataset-lineage') {
            // Dataset lineage: show datasets (orange = linked, black = other)
            state.datasetsData.forEach((dataset, datasetId) => {
                const isLinked = state.linkedDatasets.has(String(datasetId));
                const datasetName = dataset.name || dataset.primaryName || dataset.PrimaryName || `Dataset ${datasetId}`;
                const datasetRef = dataset.refNumber || dataset.RefNumber || dataset.ref || `DS-${datasetId}`;
                
                nodes.push({
                    id: `dataset_${datasetId}`,
                    label: `${datasetName}\n${datasetRef}`,
                    group: 'dataset',
                    isCurrent: isLinked,
                    meta: {
                        datasetId: String(datasetId),
                        datasetName: datasetName,
                        refNumber: datasetRef,
                        systemId: String(dataset.systemId || dataset.masterSource || dataset.MasterSource),
                        type: dataset.typeName || dataset.type || '',
                        lifecycle: dataset.lifecycleName || dataset.lifecycle || ''
                    }
                });
            });
            
            // Add edges from dataset relationships
            state.datasetRelationships.forEach(rel => {
                const sourceId = String(rel.sourceDatasetId);
                const targetId = String(rel.targetDatasetId);
                
                if (state.datasetsData.has(sourceId) && state.datasetsData.has(targetId)) {
                    const edgeKey = `${sourceId}_${targetId}`;
                    if (!edgeMap.has(edgeKey)) {
                        edges.push({
                            id: `dataset_rel_${edgeKey}`,
                            from: `dataset_${sourceId}`,
                            to: `dataset_${targetId}`,
                            label: rel.relationType || '',
                            lineStyle: 'solid',
                            lineType: 'dataset-relationship'
                        });
                        edgeMap.set(edgeKey, true);
                    }
                }
            });
            
        } else if (state.mapType === 'system-lineage') {
            // System lineage: show systems (orange = linked, black = other)
            state.systemsData.forEach((system, systemId) => {
                const isLinked = state.linkedSystems.has(String(systemId));
                const systemName = system.name || system.Name || `System ${systemId}`;
                const systemRef = system.refNumber || system.RefNumber || system.ref || `SYS-${systemId}`;
                
                nodes.push({
                    id: `system_${systemId}`,
                    label: `${systemName}\n${systemRef}`,
                    group: 'system',
                    isCurrent: isLinked,
                    isLocked: state.inaccessibleSystems.has(String(systemId)),
                    meta: {
                        systemId: String(systemId),
                        systemName: systemName,
                        refNumber: systemRef,
                        classification: system.classificationName || system.classification || '',
                        type: system.typeName || system.type || '',
                        lifecycle: system.lifecycleName || system.lifecycle || ''
                    }
                });
            });
            
            // Add edges from interfaces and data flow
            state.interfacesData.forEach(iface => {
                const fromId = String(iface.fromId || iface.sourceSystemId);
                const toId = String(iface.toId || iface.targetSystemId);
                
                if (fromId && toId && state.systemsData.has(fromId) && state.systemsData.has(toId)) {
                    const edgeKey = `${fromId}_${toId}`;
                    if (!edgeMap.has(edgeKey)) {
                        edges.push({
                            id: `interface_${edgeKey}`,
                            from: `system_${fromId}`,
                            to: `system_${toId}`,
                            label: iface.name || '',
                            lineStyle: 'solid',
                            lineType: 'interface'
                        });
                        edgeMap.set(edgeKey, true);
                    }
                }
            });
            
            state.dataFlowData.forEach(flow => {
                const fromId = String(flow.fromId);
                const toId = String(flow.toId);
                
                if (fromId && toId && state.systemsData.has(fromId) && state.systemsData.has(toId)) {
                    const edgeKey = `${fromId}_${toId}`;
                    if (!edgeMap.has(edgeKey)) {
                        edges.push({
                            id: `dataflow_${edgeKey}`,
                            from: `system_${fromId}`,
                            to: `system_${toId}`,
                            label: flow.name || '',
                            lineStyle: 'dashed',
                            lineType: 'dataflow'
                        });
                        edgeMap.set(edgeKey, true);
                    }
                }
            });
        }
        
        // Hops filter: restrict to nodes within hopsCount from root (System/Data lineage only)
        let result = { nodes, edges };
        if ((state.mapType === 'system-lineage' || state.mapType === 'dataset-lineage') && state.hopsCount > 0) {
            result = adapter.applyHopsFilter(result);
        }
        const { nodes: nodesFiltered, edges: edgesFiltered } = adapter.filterHiddenNodes(result.nodes, result.edges);
        return { nodes: nodesFiltered, edges: edgesFiltered };
    }
        async function _mapEngineApiLoader(entityId, mapType, filters) {
            state.glossaryId = entityId;
            if (mapType) { state.mapType = mapType; }
            adapter.mergeEngineFilters(filters);

            state.allGlossaries = [];
            state.glossaryRelationships = [];
            state.relationshipTypes = [];
            state.linearGlossaries = [];
            state.linkedDatasets = new Set();
            state.datasetsData = new Map();
            state.datasetRelationships = [];
            state.linkedSystems = new Set();
            state.systemsData = new Map();
            state.interfacesData = [];
            state.dataFlowData = [];
            state.connectedSystems = new Map();
            state.inaccessibleSystems = new Set();
            state.attributeRelationships = [];
            _cachedGraph = null;

            const glossaryResponse = await window.BUDG_API_SERVICE.getGlossaryById(entityId);
            state.glossaryData = glossaryResponse?.data || glossaryResponse;

            if (state.mapType === 'glossary-lineage') {
                await loadGlossaryLineageData(entityId);
            } else if (state.mapType === 'dataset-lineage') {
                await loadDatasetLineageData(entityId);
            } else if (state.mapType === 'system-lineage') {
                await loadSystemLineageData(entityId);
            }

            return { entity: state.glossaryData, _state: state };
        }

        function _getOrBuildGraph() {
            if (!_cachedGraph) {
                _cachedGraph = buildGraph();
            }
            return _cachedGraph;
        }

        function _mapEngineNodeBuilder(data) { return _getOrBuildGraph().nodes; }
        function _mapEngineEdgeBuilder(data) { return _getOrBuildGraph().edges; }

        return {
            loadMapData: loadMapData,
            loadGlossaryLineageData: loadGlossaryLineageData,
            loadDatasetLineageData: loadDatasetLineageData,
            loadSystemLineageData: loadSystemLineageData,
            buildGraph: buildGraph,
            invalidateGraphCache: invalidateGraphCache,
            _mapEngineApiLoader: _mapEngineApiLoader,
            _mapEngineNodeBuilder: _mapEngineNodeBuilder,
            _mapEngineEdgeBuilder: _mapEngineEdgeBuilder
        };
    };
})();