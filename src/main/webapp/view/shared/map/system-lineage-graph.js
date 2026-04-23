/**
 * system-lineage-graph.js - data load, graph builders, expansion for system/dataset lineage maps.
 */
(function () {
    'use strict';

    window.createSystemLineageGraphPipeline = function (ctx) {
        var state = ctx.state;
        var renderNetwork = ctx.renderNetwork;
        var showPlaceholder = ctx.showPlaceholder;
        var updateLegend = ctx.updateLegend;
        var showLoading = ctx.showLoading;
        var hideLoading = ctx.hideLoading;
        var logPrefix = ctx.logPrefix || '[SYSTEM-LINEAGE-GRAPH]';
        var onMapDataUpdated = typeof ctx.onMapDataUpdated === 'function' ? ctx.onMapDataUpdated : null;
        var adapter = ctx.adapter;

        /** Verbose dataset-lineage tracing: set window.BUDG_DEBUG_DATASET_LINEAGE = true */
        function dLogDL() {
            if (typeof window !== 'undefined' && window.BUDG_DEBUG_DATASET_LINEAGE === true) {
                console.log.apply(console, arguments);
            }
        }
        function dWarnDL() {
            if (typeof window !== 'undefined' && window.BUDG_DEBUG_DATASET_LINEAGE === true) {
                console.warn.apply(console, arguments);
            }
        }

        /** System/interface lineage load + expansion: set window.BUDG_DEBUG_SYSTEM_LINEAGE_GRAPH = true */
        function dLogMap() {
            if (typeof window !== 'undefined' && window.BUDG_DEBUG_SYSTEM_LINEAGE_GRAPH === true) {
                console.log.apply(console, arguments);
            }
        }
        function dWarnMap() {
            if (typeof window !== 'undefined' && window.BUDG_DEBUG_SYSTEM_LINEAGE_GRAPH === true) {
                console.warn.apply(console, arguments);
            }
        }

        var _cachedGraph = null;

        function isValidGraphSystemId(id) {
            if (id === null || id === undefined) return false;
            if (typeof id === 'string') {
                const t = id.trim().toLowerCase();
                if (t === '' || t === 'null' || t === 'undefined' || t === 'nan') return false;
            }
            return true;
        }

        // Load all necessary data for the map
    async function loadMapData(systemId) {
        const isDatasetLineage = state.mapType === 'dataset-lineage';
        
        // For dataset lineage, use the dedicated function (only if data not already loaded)
        if (isDatasetLineage) {
            if (!isValidGraphSystemId(systemId)) {
                showPlaceholder('System id is missing or invalid.');
                return;
            }
            // Check if dataset lineage data is already loaded
            if (state.linkedDatasets.size === 0 && state.datasetRelationships.length === 0) {
                await loadDatasetLineageData();
            }
            // Build and render dataset lineage graph
            const graph = buildDatasetLineageGraph();
            renderNetwork(graph);
            updateLegend();
            return;
        }
        
        // For system lineage, load system data
        showLoading();

        try {
            if (!isValidGraphSystemId(systemId)) {
                showPlaceholder('System id is missing or invalid.');
                return;
            }
            // Reset collections so reloads/switches don't accumulate stale data.
            state.systemData = null;
            state.interfacesData = [];
            state.dataFlowData = [];
            state.connectedSystems = new Map();
            state.inaccessibleSystems = new Set();
            state.deletedSystems = new Set();
            state.systemsFullyLoaded = new Set();
            state.interfaceIds = new Set();
            state.dataFlowKeys = new Set();

            // Load system data, interfaces, and data flow outside interfaces in parallel
            const [systemData, interfacesData, dataFlowData] = await Promise.all([
                window.BUDG_API_SERVICE.getSystemById(systemId),
                window.BUDG_API_SERVICE.getSystemInterfaces(systemId),
                window.BUDG_API_SERVICE.getDataFlowOutsideInterfaces(systemId)
            ]);

            state.systemData = systemData;
            state.interfacesData = Array.isArray(interfacesData?.data) ? interfacesData.data : (Array.isArray(interfacesData) ? interfacesData : []);
            state.interfacesData.forEach(iface => {
                if (iface?.id != null) state.interfaceIds.add(String(iface.id));
            });
            state.dataFlowData = Array.isArray(dataFlowData?.data) ? dataFlowData.data : (Array.isArray(dataFlowData) ? dataFlowData : []);
            state.dataFlowData.forEach(flow => {
                const key = window.MapGraphUtils.buildDataFlowKey(flow, { useArrowSeparator: true });
                if (key) state.dataFlowKeys.add(key);
            });
            // Root system's interfaces + data flow now loaded — mark as fully loaded.
            state.systemsFullyLoaded.add(String(systemId));

            dLogMap(logPrefix + ' System data:', systemData);
            dLogMap(logPrefix + ' Interfaces:', state.interfacesData);
            dLogMap(logPrefix + ' Data flow:', state.dataFlowData);

            // Collect all unique connected system IDs (from interfaces and data flow)
            const connectedSystemIds = new Set();
            
            state.interfacesData.forEach(iface => {
                const fromId = iface.fromId || iface.sourceSystemId;
                const toId = iface.toId || iface.targetSystemId;
                if (fromId && String(fromId) !== String(systemId)) connectedSystemIds.add(String(fromId));
                if (toId && String(toId) !== String(systemId)) connectedSystemIds.add(String(toId));
            });
            
            state.dataFlowData.forEach(flow => {
                const fromId = flow.fromId;
                const toId = flow.toId;
                if (fromId && String(fromId) !== String(systemId)) connectedSystemIds.add(String(fromId));
                if (toId && String(toId) !== String(systemId)) connectedSystemIds.add(String(toId));
            });
            
            dLogMap(logPrefix + ' Connected system IDs:', Array.from(connectedSystemIds));
            
            // Fetch full system details for all connected systems to get classification, type, lifecycle
            const connectedSystemPromises = Array.from(connectedSystemIds).map(async (sysId) => {
                try {
                    const sysData = await window.BUDG_API_SERVICE.getSystemById(sysId);
                    
                    // Check if system is deleted (404) vs inaccessible (403)
                    if (!sysData || (sysData.error && sysData.error.includes('404'))) {
                        // Deleted system - skip it (don't add to map)
                        if (state.deletedSystems) state.deletedSystems.add(String(sysId));
                        dLogMap(logPrefix + ' System', sysId, 'not found (likely deleted), skipping');
                        return;
                    }
                    
                    if (sysData && !sysData.error) {
                        state.connectedSystems.set(String(sysId), {
                            systemData: sysData
                        });
                    } else if (sysData && sysData.error && sysData.error.includes('403')) {
                        // Segment restriction - show as locked
                        dLogMap(logPrefix + ' System', sysId, 'is inaccessible (segment restriction), showing as locked');
                        state.inaccessibleSystems.add(String(sysId));
                    } else {
                        // System might be inaccessible (different segment)
                        state.inaccessibleSystems.add(String(sysId));
                    }
                } catch (err) {
                    // Check error type
                    const statusCode = err.status || err.statusCode || (err.response && err.response.status);
                    const errorMessage = err.message || err.toString() || '';
                    
                    const isNotFound = statusCode === 404 || errorMessage.includes('404') || errorMessage.toLowerCase().includes('not found');
                    const isForbidden = statusCode === 403 || errorMessage.includes('403') || errorMessage.toLowerCase().includes('forbidden');
                    
                    if (isNotFound) {
                        // Deleted system - skip it
                        if (state.deletedSystems) state.deletedSystems.add(String(sysId));
                        dLogMap(logPrefix + ' System', sysId, 'not found (likely deleted), skipping');
                    } else if (isForbidden) {
                        // Segment restriction - show as locked
                        dLogMap(logPrefix + ' System', sysId, 'is inaccessible (segment restriction), showing as locked');
                        state.inaccessibleSystems.add(String(sysId));
                    } else {
                        // Other error - check message for access clues
                        if (errorMessage.toLowerCase().includes('access') || errorMessage.toLowerCase().includes('permission')) {
                            state.inaccessibleSystems.add(String(sysId));
                        } else {
                            // Unknown error - skip it
                            dWarnMap(`[INTERFACE-MAP] Could not fetch system ${sysId}:`, errorMessage);
                        }
                    }
                }
            });
            
            await Promise.all(connectedSystemPromises);
            dLogMap(logPrefix + ' Connected systems data:', state.connectedSystems);

            // Expand fetched lineage to requested hop depth before graph build.
            const requestedDepth = Math.min(99, Math.max(1, parseInt(state.hopsCount, 10) || 15));
            await expandConnectedSystemsLineage(requestedDepth);

            // Build and render graph with all system data
            const graph = buildSystemLineageGraph();
            renderNetwork(graph);
            updateLegend();

            // Notify that map data has been updated (so table can refresh)
            if (onMapDataUpdated) onMapDataUpdated();

        } catch (error) {
            console.error(logPrefix + ' Failed to load map data:', error);
            showPlaceholder('Failed to load map data. Please try refreshing.');
        } finally {
            hideLoading();
        }
    }

    // Build the system lineage graph from interfaces and data flow
    function buildSystemLineageGraph() {
        const nodesMap = new Map();
        const edges = [];
        const edgeMap = new Map();
        const systemData = state.systemData;
        const currentSystemName = systemData?.name || '';
        const currentSystemId = state.systemId;

        // Add current system as the central node
        nodesMap.set(String(currentSystemId), {
            id: String(currentSystemId),
            label: currentSystemName,
            group: 'system',
            isCurrent: true,
            isLocked: state.inaccessibleSystems.has(String(currentSystemId)),
            meta: {
                id: currentSystemId,
                name: currentSystemName,
                description: systemData?.description || '',
                classification: systemData?.classificationName || systemData?.classification || '',
                type: systemData?.typeName || systemData?.type || '',
                lifecycle: systemData?.lifecycleName || systemData?.lifecycle || '',
                status: systemData?.statusName || ''
            }
        });

        // Process interfaces if filter is enabled
        // According to Axon:
        // - System Interfaces filter: shows dashed interface lines (dataAttributes = 0, pure interface connections)
        // - Data Attribute Links filter: shows solid attribute lineage lines (dataAttributes > 0, attribute-level flows)
        // Only show interfaces where current system is FROM or TO
        if (state.filters.systemInterfaces || state.filters.dataAttributeLinks) {
            state.interfacesData.forEach(iface => {
                const fromSystem = iface.from || iface.fromSystem || iface.sourceSystem;
                const toSystem = iface.to || iface.toSystem || iface.targetSystem;
                const fromId = iface.fromId || iface.sourceSystemId;
                const toId = iface.toId || iface.targetSystemId;
                const interfaceName = iface.name || '';
                const dataAttributes = iface.dataAttributes || 0;
                
                // Filter by dataAttributes:
                // - systemInterfaces: show dashed lines (dataAttributes = 0, interface connections)
                // - dataAttributeLinks: show solid lines (dataAttributes > 0, attribute lineage)
                const isInterfaceLineage = dataAttributes === 0; // dashed line
                const isAttributeLineage = dataAttributes > 0;   // solid line
                
                if (isInterfaceLineage && !state.filters.systemInterfaces) {
                    return; // System Interfaces filter is OFF, skip dashed interface lines
                }
                if (isAttributeLineage && !state.filters.dataAttributeLinks) {
                    return; // Data Attribute Links filter is OFF, skip solid attribute lines
                }
                
                // Add source system node (only if it exists - skip deleted systems)
                if (fromId && !nodesMap.has(String(fromId))) {
                    // Skip if system is deleted (not in connectedSystems and not in inaccessibleSystems)
                    const systemInfo = state.connectedSystems.get(String(fromId));
                    const isInaccessible = state.inaccessibleSystems.has(String(fromId));
                    
                    // Skip if explicitly known deleted
                    if (state.deletedSystems && state.deletedSystems.has(String(fromId))) {
                        dLogMap(logPrefix + ' Skipping deleted system', fromId, 'from interface', interfaceName);
                        return; // Skip this interface entirely
                    }
                    
                    const locked = isInaccessible;
                    // Use system name from connectedSystems if available, otherwise use from interface
                    const systemName = systemInfo?.systemData?.name || fromSystem || `System ${fromId}`;
                    
                    const sysData = systemInfo?.systemData || {};
                    nodesMap.set(String(fromId), {
                        id: String(fromId),
                        label: systemName,
                        group: 'system',
                        isCurrent: String(fromId) === String(currentSystemId),
                        isLocked: locked,
                        meta: {
                            id: fromId,
                            name: systemName,
                            interfaceCount: 1,
                            locked,
                            classification: sysData.classificationName || sysData.classification || '',
                            type: sysData.typeName || sysData.type || '',
                            lifecycle: sysData.lifecycleName || sysData.lifecycle || ''
                        }
                    });
                } else if (fromId && nodesMap.has(String(fromId))) {
                    const node = nodesMap.get(String(fromId));
                    if (state.inaccessibleSystems.has(String(fromId))) {
                        node.isLocked = true;
                        if (node.meta) node.meta.locked = true;
                    }
                    if (node.meta) node.meta.interfaceCount = (node.meta.interfaceCount || 0) + 1;
                }

                // Add target system node (only if it exists - skip deleted systems)
                if (toId && !nodesMap.has(String(toId))) {
                    // Skip if system is deleted (not in connectedSystems and not in inaccessibleSystems)
                    const systemInfo = state.connectedSystems.get(String(toId));
                    const isInaccessible = state.inaccessibleSystems.has(String(toId));
                    
                    // Skip if explicitly known deleted
                    if (state.deletedSystems && state.deletedSystems.has(String(toId))) {
                        dLogMap(logPrefix + ' Skipping deleted system', toId, 'from interface', interfaceName);
                        return; // Skip this interface entirely
                    }
                    
                    const locked = isInaccessible;
                    // Use system name from connectedSystems if available, otherwise use from interface
                    const systemName = systemInfo?.systemData?.name || toSystem || `System ${toId}`;
                    const sysDataTo = systemInfo?.systemData || {};
                    
                    nodesMap.set(String(toId), {
                        id: String(toId),
                        label: systemName,
                        group: 'system',
                        isCurrent: String(toId) === String(currentSystemId),
                        isLocked: locked,
                        meta: {
                            id: toId,
                            name: systemName,
                            interfaceCount: 1,
                            locked,
                            classification: sysDataTo.classificationName || sysDataTo.classification || '',
                            type: sysDataTo.typeName || sysDataTo.type || '',
                            lifecycle: sysDataTo.lifecycleName || sysDataTo.lifecycle || ''
                        }
                    });
                } else if (toId && nodesMap.has(String(toId))) {
                    const node = nodesMap.get(String(toId));
                    if (state.inaccessibleSystems.has(String(toId))) {
                        node.isLocked = true;
                        if (node.meta) node.meta.locked = true;
                    }
                    if (node.meta) node.meta.interfaceCount = (node.meta.interfaceCount || 0) + 1;
                }

                // Add edge between systems
                // Interface lineage (dashed) if dataAttributes = 0, Attribute lineage (solid) if dataAttributes > 0
                // Only create edge if both nodes exist (not deleted)
                if (fromId && toId && nodesMap.has(String(fromId)) && nodesMap.has(String(toId))) {
                    const edgeId = `${fromId}->${toId}-interface`;
                    // Use a unique ID per interface to allow multiple interfaces between same systems
                    const uniqueEdgeId = `${edgeId}-${iface.id || Date.now()}`;
                    
                    if (!edgeMap.has(uniqueEdgeId)) {
                        edgeMap.set(uniqueEdgeId, true);
                        
                        // If dataAttributes = 0, it's interface lineage (dashed)
                        // If dataAttributes > 0, it's attribute lineage (solid)
                        const isInterfaceLineage = dataAttributes === 0;
                        
                        edges.push({
                            id: uniqueEdgeId,
                            from: String(fromId),
                            to: String(toId),
                            label: state.showInterfaceLabels ? interfaceName : '',
                            dataAttributes: dataAttributes,
                            dashes: isInterfaceLineage, // Dashed for interface lineage (dataAttributes = 0)
                            isInterface: true,
                            interfaceId: iface.id,
                            lineType: isInterfaceLineage ? 'interface' : 'attribute-lineage' // For styling/identification
                        });
                    }
                }
            });
        }

        // Process data flow outside interfaces if filter is enabled
        // According to Axon: Solid lines = Attribute Lineage (show WHAT data moves)
        // This includes both data flow outside interfaces AND attribute-level flows within interfaces
        // Only show data flow where current system is FROM or TO
        if (state.filters.dataAttributeLinks) {
            state.dataFlowData.forEach(flow => {
                const fromSystem = flow.from;
                const toSystem = flow.to;
                const fromId = flow.fromId;
                const toId = flow.toId;
                const dataAttributes = flow.dataAttributes || 0;
                
                // Add source system node (only if it exists - skip deleted systems)
                if (fromId && !nodesMap.has(String(fromId))) {
                    // Skip if system is deleted (not in connectedSystems and not in inaccessibleSystems)
                    const systemInfo = state.connectedSystems.get(String(fromId));
                    const isInaccessible = state.inaccessibleSystems.has(String(fromId));
                    
                    // Skip if explicitly known deleted
                    if (state.deletedSystems && state.deletedSystems.has(String(fromId))) {
                        dLogMap(logPrefix + ' Skipping deleted system', fromId, 'from data flow');
                        return; // Skip this data flow entirely
                    }
                    
                    const locked = isInaccessible;
                    // Use system name from connectedSystems if available, otherwise use from flow
                    const systemName = systemInfo?.systemData?.name || fromSystem || `System ${fromId}`;
                    const sysDataFlow = systemInfo?.systemData || {};
                    
                    nodesMap.set(String(fromId), {
                        id: String(fromId),
                        label: systemName,
                        group: 'system',
                        isCurrent: String(fromId) === String(currentSystemId),
                        isLocked: locked,
                        isDataFlow: true,
                        meta: {
                            id: fromId,
                            name: systemName,
                            locked,
                            classification: sysDataFlow.classificationName || sysDataFlow.classification || '',
                            type: sysDataFlow.typeName || sysDataFlow.type || '',
                            lifecycle: sysDataFlow.lifecycleName || sysDataFlow.lifecycle || ''
                        }
                    });
                } else if (fromId && nodesMap.has(String(fromId))) {
                    const node = nodesMap.get(String(fromId));
                    if (state.inaccessibleSystems.has(String(fromId))) {
                        node.isLocked = true;
                        if (node.meta) node.meta.locked = true;
                    }
                }

                // Add target system node (only if it exists - skip deleted systems)
                if (toId && !nodesMap.has(String(toId))) {
                    // Skip if system is deleted (not in connectedSystems and not in inaccessibleSystems)
                    const systemInfo = state.connectedSystems.get(String(toId));
                    const isInaccessible = state.inaccessibleSystems.has(String(toId));
                    
                    // Skip if explicitly known deleted
                    if (state.deletedSystems && state.deletedSystems.has(String(toId))) {
                        dLogMap(logPrefix + ' Skipping deleted system', toId, 'from data flow');
                        return; // Skip this data flow entirely
                    }
                    
                    const locked = isInaccessible;
                    // Use system name from connectedSystems if available, otherwise use from flow
                    const systemName = systemInfo?.systemData?.name || toSystem || `System ${toId}`;
                    const sysDataFlowTo = systemInfo?.systemData || {};
                    
                    nodesMap.set(String(toId), {
                        id: String(toId),
                        label: systemName,
                        group: 'system',
                        isCurrent: String(toId) === String(currentSystemId),
                        isLocked: locked,
                        isDataFlow: true,
                        meta: {
                            id: toId,
                            name: systemName,
                            locked,
                            classification: sysDataFlowTo.classificationName || sysDataFlowTo.classification || '',
                            type: sysDataFlowTo.typeName || sysDataFlowTo.type || '',
                            lifecycle: sysDataFlowTo.lifecycleName || sysDataFlowTo.lifecycle || ''
                        }
                    });
                } else if (toId && nodesMap.has(String(toId))) {
                    const node = nodesMap.get(String(toId));
                    if (state.inaccessibleSystems.has(String(toId))) {
                        node.isLocked = true;
                        if (node.meta) node.meta.locked = true;
                    }
                }

                // Add edge for data flow (attribute lineage)
                // Solid lines = Attribute Lineage (show WHAT data moves, attribute-by-attribute)
                // Only create edge if both nodes exist (not deleted)
                if (fromId && toId && nodesMap.has(String(fromId)) && nodesMap.has(String(toId))) {
                    const edgeId = `${fromId}->${toId}-lineage`;
                    // Use unique ID to allow multiple flows between same systems
                    const uniqueEdgeId = `${edgeId}-${flow.id || Date.now()}`;
                    
                    if (!edgeMap.has(uniqueEdgeId)) {
                        edgeMap.set(uniqueEdgeId, true);
                        edges.push({
                            id: uniqueEdgeId,
                            from: String(fromId),
                            to: String(toId),
                            label: dataAttributes > 0 ? `${dataAttributes} attrs` : '',
                            dataAttributes: dataAttributes,
                            dashes: false, // Lineage is ALWAYS solid
                            isDataFlow: true,
                            lineType: 'lineage' // For styling/identification
                        });
                    }
                }
            });

            // Also check interfaces for attribute-level lineage
            // If an interface has data attributes, we should show a solid line for the lineage
            // (in addition to the dotted interface line, if interface filter is also on)
            state.interfacesData.forEach(iface => {
                const fromId = iface.fromId || iface.sourceSystemId;
                const toId = iface.toId || iface.targetSystemId;
                const dataAttributes = iface.dataAttributes || 0;

                // Only add lineage edge if interface has attributes AND both systems exist
                if (fromId && toId && dataAttributes > 0) {
                    const edgeId = `${fromId}->${toId}-lineage-interface-${iface.id}`;
                    
                    // Check if we already have a lineage edge for this connection
                    const existingLineage = edges.find(e => 
                        e.from === String(fromId) && 
                        e.to === String(toId) && 
                        e.lineType === 'lineage' &&
                        !e.isInterface
                    );

                    // Only add if no existing lineage edge (to avoid duplicates)
                    // The solid lineage line can sit "on top of" the dotted interface line
                    if (!existingLineage && !edgeMap.has(edgeId)) {
                        edgeMap.set(edgeId, true);
                        edges.push({
                            id: edgeId,
                            from: String(fromId),
                            to: String(toId),
                            label: `${dataAttributes} attrs`,
                            dataAttributes: dataAttributes,
                            dashes: false, // Lineage is ALWAYS solid
                            isDataFlow: true,
                            isInterfaceLineage: true, // Lineage within an interface
                            interfaceId: iface.id,
                            lineType: 'lineage'
                        });
                    }
                }
            });
        }

        let resultNodes = Array.from(nodesMap.values());
        let resultEdges = edges;
        if (state.hopsCount > 0) {
            const filtered = adapter.applyHopsFilter({ nodes: resultNodes, edges: resultEdges });
            resultNodes = filtered.nodes;
            resultEdges = filtered.edges;
        }

        // Collect unique filter options from all nodes for dynamic filter menu
        const classifications = new Set();
        const types = new Set();
        const lifecycles = new Set();
        resultNodes.forEach(node => {
            if (node.meta) {
                if (node.meta.classification) classifications.add(node.meta.classification);
                if (node.meta.type) types.add(node.meta.type);
                if (node.meta.lifecycle) lifecycles.add(node.meta.lifecycle);
            }
        });

        // Emit filter options to update the UI dynamically
        if (typeof window !== 'undefined' && window.dispatchEvent) {
            window.dispatchEvent(new CustomEvent('systemMapFilterOptionsUpdated', {
                detail: {
                    classifications: Array.from(classifications).filter(Boolean).sort(),
                    types: Array.from(types).filter(Boolean).sort(),
                    lifecycles: Array.from(lifecycles).filter(Boolean).sort()
                }
            }));
        }

        // Filter out hidden nodes
        const { nodes: filteredNodes, edges: filteredEdges } = adapter.filterHiddenNodes(resultNodes, resultEdges);

        return {
            nodes: filteredNodes,
            edges: filteredEdges
        };
    }

    /** Attribute relationships between two systems (same API as system-lineage-overlays). */
    async function fetchAttributeRelationships(sourceSystemId, targetSystemId) {
        try {
            const url = '/api/attribute-relationships?sourceSystem=' + encodeURIComponent(sourceSystemId) +
                '&targetSystem=' + encodeURIComponent(targetSystemId);
            const response = await fetch(url, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });
            if (!response.ok) return [];
            const data = await response.json();
            const relationships = Array.isArray(data) ? data : [];
            return relationships.map(function (rel) {
                return Object.assign({}, rel, {
                    sourceSystemId: sourceSystemId,
                    targetSystemId: targetSystemId
                });
            });
        } catch (e) {
            dWarnMap(logPrefix + ' fetchAttributeRelationships failed', sourceSystemId, '->', targetSystemId, e);
            return [];
        }
    }

    /** Normalize getSystemDatasets (and similar) payloads — same shapes as system-data-tab.js */
    function normalizeSystemDatasetsList(raw) {
        if (raw == null) return [];
        if (Array.isArray(raw)) return raw;
        if (Array.isArray(raw.data)) return raw.data;
        if (raw.data && Array.isArray(raw.data.data)) return raw.data.data;
        if (Array.isArray(raw.datasets)) return raw.datasets;
        if (raw.data && Array.isArray(raw.data.datasets)) return raw.data.datasets;
        return [];
    }

    // Load dataset lineage data - datasets that have attribute links
    async function loadDatasetLineageData() {
        if (!isValidGraphSystemId(state.systemId)) {
            dWarnMap(logPrefix + ' Dataset lineage: missing system id, aborting load');
            return;
        }
        showLoading();
        
        // First, load system data and interfaces to get connected systems info (needed for dataset relationships)
        try {
            const systemId = state.systemId;
            const [systemData, interfacesData, dataFlowData] = await Promise.all([
                window.BUDG_API_SERVICE.getSystemById(systemId),
                window.BUDG_API_SERVICE.getSystemInterfaces(systemId),
                window.BUDG_API_SERVICE.getDataFlowOutsideInterfaces(systemId)
            ]);

            state.systemData = systemData;
            state.interfacesData = Array.isArray(interfacesData?.data) ? interfacesData.data : (Array.isArray(interfacesData) ? interfacesData : []);
            state.dataFlowData = Array.isArray(dataFlowData?.data) ? dataFlowData.data : (Array.isArray(dataFlowData) ? dataFlowData : []);
        } catch (error) {
            dWarnDL('[DATASET-LINEAGE] Failed to load system/interfaces data (continuing anyway):', error);
        }
        
        try {
            const currentSystemId = String(state.systemId);
            const API = window.BUDG_API_SERVICE;
            
            // Get all datasets for the current system
            const currentSystemDatasets = await API.getSystemDatasets?.(currentSystemId) || [];
            const datasets = normalizeSystemDatasetsList(currentSystemDatasets);
            
            dLogDL('[DATASET-LINEAGE] Current system datasets:', datasets.length, datasets);
            
            // Get all attribute relationships to find linked datasets
            const allRelationships = [];
            const linkedDatasetIds = new Set();
            const datasetToSystemMap = new Map(); // datasetId -> systemId
            
            // First, add all current system dataset IDs
            const currentSystemDatasetIds = new Set();
            datasets.forEach(dataset => {
                const id = dataset.id || dataset.ID;
                if (id) {
                    currentSystemDatasetIds.add(String(id));
                    datasetToSystemMap.set(String(id), currentSystemId);
                }
            });
            
            dLogDL('[DATASET-LINEAGE] Current system dataset IDs:', Array.from(currentSystemDatasetIds));
            
            // For each dataset in the current system, fetch its attribute relationships
            // using the dataset-relationships endpoint
            for (const dataset of datasets) {
                const datasetId = dataset.id || dataset.ID;
                if (!datasetId) continue;
                
                try {
                    // Fetch inbound and outbound relationships for this dataset
                    const relResp = await fetch(`/api/dataset-relationships/${datasetId}`, {
                        method: 'GET',
                        credentials: 'include',
                        headers: { 'Content-Type': 'application/json' }
                    });
                    
                    if (relResp.ok) {
                        const relData = await relResp.json();
                        const inbound = relData.inbound || [];
                        const outbound = relData.outbound || [];
                        
                        dLogDL(`[DATASET-LINEAGE] Dataset ${datasetId} relationships:`, { inbound: inbound.length, outbound: outbound.length, inboundData: inbound, outboundData: outbound });
                        
                        // Process inbound relationships (other datasets -> this dataset)
                        inbound.forEach(rel => {
                            // Try multiple property names for source dataset ID
                            const sourceId = rel.sourceDatasetId || rel.sourceDataSetId || rel.datasetId || rel.DatasetId || rel.id || rel.ID;
                            const sourceDatasetId = sourceId ? String(sourceId) : null;
                            const targetDatasetId = String(datasetId);
                            
                            dLogDL(`[DATASET-LINEAGE] Inbound rel:`, rel, `-> sourceDatasetId:`, sourceDatasetId);
                            
                            if (sourceDatasetId && sourceDatasetId !== 'undefined' && sourceDatasetId !== 'null' && sourceDatasetId !== targetDatasetId) {
                                allRelationships.push({
                                    ...rel,
                                    sourceDatasetId: sourceDatasetId,
                                    targetDatasetId: targetDatasetId,
                                    sourceSystemId: rel.systemId || rel.sourceSystemId || rel.masterSource,
                                    targetSystemId: currentSystemId
                                });
                                
                                linkedDatasetIds.add(sourceDatasetId);
                                linkedDatasetIds.add(targetDatasetId);
                                
                                const srcSysId = rel.systemId || rel.sourceSystemId || rel.masterSource;
                                if (srcSysId) {
                                    datasetToSystemMap.set(sourceDatasetId, String(srcSysId));
                                }
                            }
                        });
                        
                        // Process outbound relationships (this dataset -> other datasets)
                        outbound.forEach(rel => {
                            const sourceDatasetId = String(datasetId);
                            // Try multiple property names for target dataset ID
                            const targetId = rel.targetDatasetId || rel.targetDataSetId || rel.datasetId || rel.DatasetId || rel.id || rel.ID;
                            const targetDatasetId = targetId ? String(targetId) : null;
                            
                            dLogDL(`[DATASET-LINEAGE] Outbound rel:`, rel, `-> targetDatasetId:`, targetDatasetId);
                            
                            if (targetDatasetId && targetDatasetId !== 'undefined' && targetDatasetId !== 'null' && sourceDatasetId !== targetDatasetId) {
                                allRelationships.push({
                                    ...rel,
                                    sourceDatasetId: sourceDatasetId,
                                    targetDatasetId: targetDatasetId,
                                    sourceSystemId: currentSystemId,
                                    targetSystemId: rel.systemId || rel.targetSystemId || rel.masterSource
                                });
                                
                                linkedDatasetIds.add(sourceDatasetId);
                                linkedDatasetIds.add(targetDatasetId);
                                
                                const tgtSysId = rel.systemId || rel.targetSystemId || rel.masterSource;
                                if (tgtSysId) {
                                    datasetToSystemMap.set(targetDatasetId, String(tgtSysId));
                                }
                            }
                        });
                    }
                } catch (e) {
                    dWarnDL(`[DATASET-LINEAGE] Error fetching relationships for dataset ${datasetId}:`, e);
                }
            }
            
            // Always fetch attribute relationships to get attribute IDs, even if dataset relationships exist
            // This ensures we have sourceAttributeId and targetAttributeId for linking-attributes overlay
            dLogDL('[DATASET-LINEAGE] Fetching attribute relationships to get attribute IDs...');
            
            // Get all systems to check (current + connected from interfaces/data flow)
            const systemsToCheck = new Set([currentSystemId]);
            state.interfacesData.forEach(iface => {
                const fromId = iface.fromId || iface.sourceSystemId;
                const toId = iface.toId || iface.targetSystemId;
                if (fromId) systemsToCheck.add(String(fromId));
                if (toId) systemsToCheck.add(String(toId));
            });
            state.dataFlowData.forEach(flow => {
                if (flow.fromId) systemsToCheck.add(String(flow.fromId));
                if (flow.toId) systemsToCheck.add(String(flow.toId));
            });
            
            const systemsArray = Array.from(systemsToCheck);
            dLogDL('[DATASET-LINEAGE] Systems to check for attribute relationships:', systemsArray);
            
            // Fetch attribute relationships between all pairs of systems
            for (const sourceSystemId of systemsArray) {
                for (const targetSystemId of systemsArray) {
                    if (sourceSystemId === targetSystemId) continue;
                    
                    const rels = await fetchAttributeRelationships(sourceSystemId, targetSystemId);
                    
                    rels.forEach(rel => {
                        const sourceDatasetId = String(rel.sourceDatasetId || rel.sourceDataSetId || '');
                        const targetDatasetId = String(rel.targetDatasetId || rel.targetDataSetId || '');
                        
                        if (!sourceDatasetId || !targetDatasetId || sourceDatasetId === 'undefined' || targetDatasetId === 'undefined') return;
                        
                        // Check if this relationship involves a dataset from the current system
                        const sourceIsCurrentSystem = currentSystemDatasetIds.has(sourceDatasetId);
                        const targetIsCurrentSystem = currentSystemDatasetIds.has(targetDatasetId);
                        
                        if (sourceIsCurrentSystem || targetIsCurrentSystem) {
                            // Merge with existing relationships or add new one
                            const existingRel = allRelationships.find(r => 
                                String(r.sourceDatasetId) === sourceDatasetId && 
                                String(r.targetDatasetId) === targetDatasetId
                            );
                            
                            if (existingRel) {
                                // Merge attribute IDs into existing relationship
                                if (rel.sourceAttributeId) {
                                    existingRel.sourceAttributeId = rel.sourceAttributeId;
                                    dLogDL('[DATASET-LINEAGE] Merged sourceAttributeId:', rel.sourceAttributeId, 'into relationship:', sourceDatasetId, '->', targetDatasetId);
                                }
                                if (rel.targetAttributeId) {
                                    existingRel.targetAttributeId = rel.targetAttributeId;
                                    dLogDL('[DATASET-LINEAGE] Merged targetAttributeId:', rel.targetAttributeId, 'into relationship:', sourceDatasetId, '->', targetDatasetId);
                                }
                                if (rel.sourceAttributeName) existingRel.sourceAttributeName = rel.sourceAttributeName;
                                if (rel.targetAttributeName) existingRel.targetAttributeName = rel.targetAttributeName;
                            } else {
                                // Add new relationship with attribute IDs
                                const newRel = {
                                    ...rel,
                                    sourceDatasetId: sourceDatasetId,
                                    targetDatasetId: targetDatasetId
                                };
                                allRelationships.push(newRel);
                                if (rel.sourceAttributeId || rel.targetAttributeId) {
                                    dLogDL('[DATASET-LINEAGE] Added relationship with attribute IDs:', {
                                        sourceDatasetId,
                                        targetDatasetId,
                                        sourceAttributeId: rel.sourceAttributeId,
                                        targetAttributeId: rel.targetAttributeId
                                    });
                                }
                            }
                            
                            linkedDatasetIds.add(sourceDatasetId);
                            linkedDatasetIds.add(targetDatasetId);
                            
                            if (rel.sourceSystemId) {
                                datasetToSystemMap.set(sourceDatasetId, String(rel.sourceSystemId));
                            }
                            if (rel.targetSystemId) {
                                datasetToSystemMap.set(targetDatasetId, String(rel.targetSystemId));
                            }
                        }
                    });
                }
            }
            
            dLogDL('[DATASET-LINEAGE] Final relationships with attribute IDs:', allRelationships.filter(r => r.sourceAttributeId || r.targetAttributeId).length);
            
            // Add all current system datasets to linkedDatasetIds (they should always appear)
            currentSystemDatasetIds.forEach(id => {
                linkedDatasetIds.add(id);
            });
            
            state.datasetRelationships = allRelationships;
            dLogDL('[DATASET-LINEAGE] Found', allRelationships.length, 'attribute relationships');
            dLogDL('[DATASET-LINEAGE] Relationships with dataset IDs:', allRelationships.filter(r => r.sourceDatasetId && r.targetDatasetId).length);
            dLogDL('[DATASET-LINEAGE] Linked dataset IDs:', Array.from(linkedDatasetIds));
            dLogDL('[DATASET-LINEAGE] Dataset to System Map:', Array.from(datasetToSystemMap.entries()));
            
            // Fetch dataset details for all linked datasets
            const linkedDatasetsMap = new Map();
            
            // Add current system datasets
            for (const dataset of datasets) {
                const dsId = dataset.id || dataset.ID;
                if (dsId) {
                    linkedDatasetsMap.set(String(dsId), {
                        dataset: dataset,
                        systemId: currentSystemId,
                        isLocked: false
                    });
                }
            }
            
            // Fetch linked datasets from other systems using API service
            for (const datasetId of linkedDatasetIds) {
                // Skip if already added (current system datasets)
                if (linkedDatasetsMap.has(String(datasetId))) continue;
                
                const systemId = datasetToSystemMap.get(String(datasetId));
                if (!systemId) {
                    dWarnDL('[DATASET-LINEAGE] No system ID for dataset:', datasetId);
                    continue;
                }
                
                try {
                    // Try to fetch dataset using the API service
                    if (API && API.getDatasetById) {
                        const datasetData = await API.getDatasetById(datasetId, null, { silent404: true });
                        const dataset = datasetData?.data || datasetData;
                        
                        if (dataset && (dataset.id || dataset.ID)) {
                            linkedDatasetsMap.set(String(datasetId), {
                                dataset: dataset,
                                systemId: systemId,
                                isLocked: false
                            });
                            dLogDL('[DATASET-LINEAGE] Loaded dataset', datasetId, ':', dataset.name || dataset.Name || dataset.datasetName);
                        } else {
                            // Dataset might not exist or be inaccessible - skip it (don't add to map)
                            dLogDL('[DATASET-LINEAGE] Dataset', datasetId, 'not found or deleted, skipping');
                        }
                    } else {
                        // No API service available - skip it (don't add to map)
                        dLogDL('[DATASET-LINEAGE] No API service available for dataset', datasetId, ', skipping');
                    }
                } catch (e) {
                    // Check HTTP status code if available
                    const statusCode = e.status || e.statusCode || (e.response && e.response.status);
                    const errorMessage = e.message || e.toString() || '';
                    
                    // Check if it's a 403 Forbidden (segment access restriction) vs 404 Not Found (deleted/not found)
                    const isForbidden = statusCode === 403 || (
                        errorMessage.includes('403') || 
                        errorMessage.includes('Forbidden') || 
                        errorMessage.includes('Access denied') ||
                        errorMessage.toLowerCase().includes('permission') ||
                        errorMessage.toLowerCase().includes('access denied')
                    );
                    const isNotFound = statusCode === 404 || (
                        errorMessage.includes('404') || 
                        errorMessage.toLowerCase().includes('not found') ||
                        errorMessage.toLowerCase().includes('dataset not found')
                    );
                    
                    if (isForbidden) {
                        // 403 Forbidden = segment access restriction - show as locked
                        dLogDL('[DATASET-LINEAGE] Dataset', datasetId, 'is inaccessible (segment restriction), showing as locked');
                        linkedDatasetsMap.set(String(datasetId), {
                            dataset: { id: datasetId, name: `Dataset ${datasetId}`, refNumber: `DS-${datasetId}` },
                            systemId: systemId,
                            isLocked: true
                        });
                    } else if (isNotFound) {
                        // 404 Not Found = deleted or doesn't exist - skip it (don't add to map)
                        dLogDL('[DATASET-LINEAGE] Dataset', datasetId, 'not found (likely deleted), skipping');
                    } else {
                        // Other error - check error message for clues
                        if (errorMessage.toLowerCase().includes('access') || errorMessage.toLowerCase().includes('permission')) {
                            // Likely an access issue - show as locked
                            dLogDL('[DATASET-LINEAGE] Dataset', datasetId, 'appears to be inaccessible, showing as locked');
                            linkedDatasetsMap.set(String(datasetId), {
                                dataset: { id: datasetId, name: `Dataset ${datasetId}`, refNumber: `DS-${datasetId}` },
                                systemId: systemId,
                                isLocked: true
                            });
                        } else {
                            // Unknown error - skip it (don't add to map)
                            console.warn('[DATASET-LINEAGE] Failed to fetch dataset', datasetId, ':', errorMessage);
                        }
                    }
                }
            }
            
            state.linkedDatasets = linkedDatasetsMap;
            state.datasetsData = datasets;
            
            dLogDL('[DATASET-LINEAGE] Loaded', linkedDatasetsMap.size, 'datasets');
            
        } catch (error) {
            console.error('[DATASET-LINEAGE] Failed to load dataset lineage data:', error);
        } finally {
            hideLoading();
        }
    }

    // Build dataset lineage graph
    function buildDatasetLineageGraph() {
        const nodesMap = new Map();
        const edges = [];
        const edgeMap = new Map();
        const currentSystemId = String(state.systemId);
        
        // Group datasets by system
        const datasetsBySystem = new Map();
        state.linkedDatasets.forEach((info, datasetId) => {
            const systemId = String(info.systemId);
            if (!datasetsBySystem.has(systemId)) {
                datasetsBySystem.set(systemId, []);
            }
            datasetsBySystem.get(systemId).push({ ...info, datasetId });
        });
        
        // Create nodes for each dataset
        datasetsBySystem.forEach((datasets, systemId) => {
            // Get system name
            let systemName = 'Unknown System';
            if (systemId === currentSystemId) {
                systemName = state.systemData?.name || 'Current System';
            } else {
                const sysInfo = state.connectedSystems.get(systemId);
                systemName = sysInfo?.systemData?.name || `System ${systemId}`;
            }
            
            datasets.forEach(({ dataset, datasetId, isLocked }) => {
                const nodeId = `dataset-${datasetId}`;
                const datasetName = dataset.name || dataset.primaryName || dataset.PrimaryName || `Dataset ${datasetId}`;
                const refNumber = dataset.refNumber || dataset.RefNumber || dataset.ref || `DS-${datasetId}`;
                
                // Label: System Name + Dataset Name + DS Reference
                const label = `${systemName}\n${datasetName}\n${refNumber}`;
                
                // Datasets belonging to the opened system should be colored in orange
                const isCurrentSystemDataset = systemId === currentSystemId;
                
                // Get type and lifecycle from dataset for filtering
                const typeName = dataset.typeName || dataset.datasetTypeName || dataset.type || dataset.Type || '';
                const lifecycleName = dataset.lifecycleName || dataset.lifecycle || dataset.Lifecycle || '';
                
                nodesMap.set(nodeId, {
                    id: nodeId,
                    label: label,
                    group: 'dataset',
                    isCurrent: isCurrentSystemDataset, // Mark as current if it belongs to opened system
                    isLocked: isLocked,
                    meta: {
                        datasetId: datasetId,
                        datasetName: datasetName,
                        refNumber: refNumber,
                        systemId: systemId,
                        systemName: systemName,
                        locked: isLocked,
                        isCurrentSystemDataset: isCurrentSystemDataset,
                        typeName: typeName,
                        lifecycleName: lifecycleName
                    }
                });
            });
        });
        
        // Create edges based on attribute relationships
        dLogDL('[DATASET-LINEAGE] Building edges from', state.datasetRelationships.length, 'relationships');
        dLogDL('[DATASET-LINEAGE] Available nodes:', Array.from(nodesMap.keys()));
        
        state.datasetRelationships.forEach((rel, index) => {
            const sourceDatasetId = rel.sourceDatasetId || rel.sourceDataSetId;
            const targetDatasetId = rel.targetDatasetId || rel.targetDataSetId;
            
            dLogDL(`[DATASET-LINEAGE] Relationship ${index}:`, {
                sourceDatasetId,
                targetDatasetId,
                sourceAttributeId: rel.sourceAttributeId,
                targetAttributeId: rel.targetAttributeId
            });
            
            // If we don't have valid dataset IDs, skip
            if (!sourceDatasetId || !targetDatasetId || 
                sourceDatasetId === 'undefined' || targetDatasetId === 'undefined' ||
                sourceDatasetId === 'null' || targetDatasetId === 'null') {
                dWarnDL('[DATASET-LINEAGE] Skipping relationship without valid dataset IDs:', rel);
                return;
            }
            
            const sourceNodeId = `dataset-${sourceDatasetId}`;
            const targetNodeId = `dataset-${targetDatasetId}`;
            
            const hasSource = nodesMap.has(sourceNodeId);
            const hasTarget = nodesMap.has(targetNodeId);
            
            dLogDL(`[DATASET-LINEAGE] Edge check: ${sourceNodeId} -> ${targetNodeId}`, {
                hasSource,
                hasTarget,
                sourceExists: hasSource,
                targetExists: hasTarget
            });
            
            if (hasSource && hasTarget) {
                const edgeId = `${sourceNodeId}->${targetNodeId}`;
                if (!edgeMap.has(edgeId)) {
                    edgeMap.set(edgeId, true);
                    edges.push({
                        id: edgeId,
                        from: sourceNodeId,
                        to: targetNodeId,
                        label: '',
                        dashes: false, // Solid lines for dataset lineage (attribute lineage)
                        lineStyle: 'solid', // Explicitly set lineStyle for proper styling
                        isDatasetLineage: true,
                        lineType: 'attribute-lineage' // Dataset lineage is always attribute lineage
                    });
                    dLogDL('[DATASET-LINEAGE] Added edge:', edgeId);
                } else {
                    dLogDL('[DATASET-LINEAGE] Edge already exists:', edgeId);
                }
            } else {
                dWarnDL('[DATASET-LINEAGE] Missing nodes for edge:', {
                    sourceNodeId,
                    targetNodeId,
                    hasSource,
                    hasTarget
                });
            }
        });
        
        dLogDL('[DATASET-LINEAGE] Created', edges.length, 'edges');
        
        // Collect unique types and lifecycles from all datasets for filter options
        const types = new Set();
        const lifecycles = new Set();
        
        nodesMap.forEach(node => {
            const meta = node.meta || {};
            
            // Get type and lifecycle from node meta (which is set when creating nodes)
            const datasetType = meta.typeName || '';
            const datasetLifecycle = meta.lifecycleName || '';
            
            if (datasetType) types.add(datasetType);
            if (datasetLifecycle) lifecycles.add(datasetLifecycle);
        });
        
        dLogDL('[DATASET-LINEAGE] Filter options collected - types:', Array.from(types), 'lifecycles:', Array.from(lifecycles));
        
        // Emit dataset filter options to update the UI dynamically
        if (typeof window !== 'undefined' && window.dispatchEvent) {
            window.dispatchEvent(new CustomEvent('datasetMapFilterOptionsUpdated', {
                detail: {
                    types: Array.from(types).filter(Boolean).sort(),
                    lifecycles: Array.from(lifecycles).filter(Boolean).sort()
                }
            }));
        }
        
        let resultNodes = Array.from(nodesMap.values());
        let resultEdges = edges;
        if (state.hopsCount > 0) {
            const filtered = adapter.applyHopsFilter({ nodes: resultNodes, edges: resultEdges });
            resultNodes = filtered.nodes;
            resultEdges = filtered.edges;
        }
        
        // Filter out hidden nodes
        const { nodes: filteredNodes, edges: filteredEdges } = adapter.filterHiddenNodes(resultNodes, resultEdges);

        return {
            nodes: filteredNodes,
            edges: filteredEdges
        };
    }

    // Get neighbor system IDs for a given system from current data
    // This includes both interfaces and data flow outside interfaces
    function getNeighborsForSystem(systemId) {
        const idStr = String(systemId);
        const neighbors = new Set();

        // Get neighbors from interfaces
        state.interfacesData.forEach(iface => {
            const fromId = String(iface.fromId || iface.sourceSystemId || '');
            const toId = String(iface.toId || iface.targetSystemId || '');
            if (fromId === idStr && toId) neighbors.add(toId);
            if (toId === idStr && fromId) neighbors.add(fromId);
        });

        // Get neighbors from data flow outside interfaces
        state.dataFlowData.forEach(flow => {
            const fromId = String(flow.fromId || flow.sourceSystemId || '');
            const toId = String(flow.toId || flow.targetSystemId || '');
            if (fromId === idStr && toId) neighbors.add(toId);
            if (toId === idStr && fromId) neighbors.add(fromId);
        });

        // Also check connected systems that were already fetched
        // This helps discover connections from systems like QWR to QWR2
        state.connectedSystems.forEach((systemInfo, systemIdStr) => {
            if (systemIdStr === idStr) {
                // This is the system we're looking for neighbors of
                // Check its interfaces and data flow
                const ifaceList = Array.isArray(systemInfo.interfacesData?.data) 
                    ? systemInfo.interfacesData.data 
                    : (Array.isArray(systemInfo.interfacesData) ? systemInfo.interfacesData : []);
                
                ifaceList.forEach(iface => {
                    const fromId = String(iface.fromId || iface.sourceSystemId || '');
                    const toId = String(iface.toId || iface.targetSystemId || '');
                    if (fromId === idStr && toId) neighbors.add(toId);
                    if (toId === idStr && fromId) neighbors.add(fromId);
                });

                const flowList = Array.isArray(systemInfo.dataFlowData?.data) 
                    ? systemInfo.dataFlowData.data 
                    : (Array.isArray(systemInfo.dataFlowData) ? systemInfo.dataFlowData : []);
                
                flowList.forEach(flow => {
                    const fromId = String(flow.fromId || flow.sourceSystemId || '');
                    const toId = String(flow.toId || flow.targetSystemId || '');
                    if (fromId === idStr && toId) neighbors.add(toId);
                    if (toId === idStr && fromId) neighbors.add(fromId);
                });
            }
        });

        return Array.from(neighbors);
    }

    // Expand lineage by pulling connections of neighboring systems (multi-hop)
    // This shows all systems connected to the current system, and systems connected to those systems
    // Example: CRM -> QWR -> QWR2 (all will be shown) up to state.hopsCount depth.
    async function expandConnectedSystemsLineage(maxDepth = 2) {
        const depthLimit = Math.min(99, Math.max(1, parseInt(maxDepth, 10) || 2));
        const rootId = String(state.systemId || '');
        if (!rootId) return;

        if (!state.systemsFullyLoaded) state.systemsFullyLoaded = new Set();
        // Root's interfaces/data-flow are loaded by the caller (loadMapData / _mapEngineApiLoader)
        state.systemsFullyLoaded.add(rootId);

        const U = window.MapGraphUtils;
        if (U && typeof U.expandByHops === 'function') {
            await U.expandByHops({
                rootId: rootId,
                maxDepth: depthLimit,
                getNeighbors: function (id) { return getNeighborsForSystem(id); },
                fetchAndMerge: function (id) { return fetchAndMergeSystem(id); },
                isLoaded: function (id) {
                    return state.systemsFullyLoaded.has(String(id)) ||
                           (state.deletedSystems && state.deletedSystems.has(String(id)));
                },
                onError: function (id, err) {
                    dWarnMap(`${logPrefix} Failed to fetch system ${id}:`, err);
                    state.inaccessibleSystems.add(String(id));
                }
            });
        }
    }

    // Fetch a system's interfaces/dataFlow and merge into current state
    // This handles both accessible and inaccessible systems (locked by segment permissions)
    async function fetchAndMergeSystem(systemId) {
        if (!systemId) return;
        const idStr = String(systemId);

        if (!state.systemsFullyLoaded) state.systemsFullyLoaded = new Set();
        // Skip if we've already fully loaded this system's interfaces/data-flow.
        if (state.systemsFullyLoaded.has(idStr)) return;
        // Skip known-deleted systems.
        if (state.deletedSystems && state.deletedSystems.has(idStr)) return;

        try {
            let systemData = null;
            let systemError = null;
            
            try {
                systemData = await window.BUDG_API_SERVICE.getSystemById(idStr);
            } catch (e) {
                systemError = e;
                // Check if it's a 404 (deleted) vs 403 (segment restriction)
                const statusCode = e.status || e.statusCode || (e.response && e.response.status);
                const errorMessage = e.message || e.toString() || '';
                
                const isNotFound = statusCode === 404 || errorMessage.includes('404') || errorMessage.toLowerCase().includes('not found');
                const isForbidden = statusCode === 403 || errorMessage.includes('403') || errorMessage.toLowerCase().includes('forbidden') || errorMessage.toLowerCase().includes('access denied');
                
                if (isNotFound) {
                    // 404 Not Found = deleted system - skip it (don't add to map)
                    dLogMap(logPrefix + ' System', idStr, 'not found (likely deleted), skipping');
                    if (state.deletedSystems) state.deletedSystems.add(idStr);
                    return;
                } else if (isForbidden) {
                    // 403 Forbidden = segment access restriction - show as locked
                    dLogMap(logPrefix + ' System', idStr, 'is inaccessible (segment restriction), showing as locked');
                    state.inaccessibleSystems.add(idStr);
                    // Still add it to the map as a locked node
                    return;
                } else {
                    // Other error - check error message for clues
                    if (errorMessage.toLowerCase().includes('access') || errorMessage.toLowerCase().includes('permission')) {
                        // Likely an access issue - show as locked
                        dLogMap(logPrefix + ' System', idStr, 'appears to be inaccessible, showing as locked');
                        state.inaccessibleSystems.add(idStr);
                        return;
                    } else {
                        // Unknown error - skip it (don't add to map)
                        dWarnMap(logPrefix + ' Failed to fetch system', idStr, ':', errorMessage);
                        return;
                    }
                }
            }

            // Check if systemData indicates an error
            if (!systemData || (systemData.error && systemData.error.includes('404'))) {
                // Deleted system - skip it
                dLogMap(logPrefix + ' System', idStr, 'not found (likely deleted), skipping');
                if (state.deletedSystems) state.deletedSystems.add(idStr);
                return;
            }
            
            if (systemData.error && systemData.error.includes('403')) {
                // Segment restriction - show as locked
                dLogMap(logPrefix + ' System', idStr, 'is inaccessible (segment restriction), showing as locked');
                state.inaccessibleSystems.add(idStr);
                return;
            }

            // Fetch interfaces and data flow (these may fail for locked systems, that's OK)
            const [interfacesData, dataFlowData] = await Promise.all([
                window.BUDG_API_SERVICE.getSystemInterfaces(idStr).catch(() => null),
                window.BUDG_API_SERVICE.getDataFlowOutsideInterfaces(idStr).catch(() => null)
            ]);

            state.connectedSystems.set(idStr, {
                systemData,
                interfacesData,
                dataFlowData
            });

            // Merge interfaces
            const ifaceList = Array.isArray(interfacesData?.data) ? interfacesData.data : (Array.isArray(interfacesData) ? interfacesData : []);
            ifaceList.forEach(iface => {
                const ifaceId = iface?.id;
                if (ifaceId != null && !state.interfaceIds.has(String(ifaceId))) {
                    state.interfacesData.push(iface);
                    state.interfaceIds.add(String(ifaceId));
                }
            });

            // Merge data flow
            const flowList = Array.isArray(dataFlowData?.data) ? dataFlowData.data : (Array.isArray(dataFlowData) ? dataFlowData : []);
            flowList.forEach(flow => {
                const key = window.MapGraphUtils.buildDataFlowKey(flow, { useArrowSeparator: true });
                if (key && !state.dataFlowKeys.has(key)) {
                    state.dataFlowData.push(flow);
                    state.dataFlowKeys.add(key);
                }
            });

            // Mark as fully loaded so subsequent hop expansions don't refetch.
            if (!state.systemsFullyLoaded) state.systemsFullyLoaded = new Set();
            state.systemsFullyLoaded.add(idStr);
        } catch (error) {
            // System is likely inaccessible due to segment permissions
            dWarnMap(logPrefix + ' Unable to expand system (likely inaccessible/locked):', idStr, error);
            state.inaccessibleSystems.add(idStr);
            // Note: The system will still appear in the map as a locked node
        }
    }
        function _getOrBuildGraph() {
            if (!_cachedGraph) {
                _cachedGraph = state.mapType === 'dataset-lineage'
                    ? buildDatasetLineageGraph()
                    : buildSystemLineageGraph();
            }
            return _cachedGraph;
        }

        async function _mapEngineApiLoader(entityId, mapType, filters) {
            state.systemId = entityId;
            if (mapType) { state.mapType = mapType; }
            adapter.mergeEngineFilters(filters);

            state.systemData         = null;
            state.interfacesData     = [];
            state.dataFlowData       = [];
            state.connectedSystems   = new Map();
            state.inaccessibleSystems = new Set();
            state.deletedSystems    = new Set();
            state.systemsFullyLoaded = new Set();
            state.interfaceIds       = new Set();
            state.dataFlowKeys       = new Set();
            state.datasetsData       = [];
            state.linkedDatasets     = new Map();
            state.datasetRelationships = [];
            _cachedGraph = null;

            if (state.mapType === 'dataset-lineage') {
                await loadDatasetLineageData();
            } else if (!isValidGraphSystemId(entityId)) {
                dWarnMap(logPrefix + ' MapEngine load skipped: invalid system id');
            } else {
                const [systemData, interfacesData, dataFlowData] = await Promise.all([
                    window.BUDG_API_SERVICE.getSystemById(entityId),
                    window.BUDG_API_SERVICE.getSystemInterfaces(entityId),
                    window.BUDG_API_SERVICE.getDataFlowOutsideInterfaces(entityId)
                ]);

                state.systemData     = systemData;
                state.interfacesData = Array.isArray(interfacesData?.data)  ? interfacesData.data  : (Array.isArray(interfacesData)  ? interfacesData  : []);
                state.dataFlowData   = Array.isArray(dataFlowData?.data)    ? dataFlowData.data    : (Array.isArray(dataFlowData)    ? dataFlowData    : []);

                state.interfacesData.forEach(iface => {
                    if (iface?.id != null) state.interfaceIds.add(String(iface.id));
                });
                state.dataFlowData.forEach(flow => {
                    const key = window.MapGraphUtils.buildDataFlowKey(flow, { useArrowSeparator: true });
                    if (key) state.dataFlowKeys.add(key);
                });

                const connectedSystemIds = new Set();
                state.interfacesData.forEach(iface => {
                    const fromId = iface.fromId || iface.sourceSystemId;
                    const toId   = iface.toId   || iface.targetSystemId;
                    if (fromId && String(fromId) !== String(entityId)) connectedSystemIds.add(String(fromId));
                    if (toId   && String(toId)   !== String(entityId)) connectedSystemIds.add(String(toId));
                });
                state.dataFlowData.forEach(flow => {
                    const fromId = flow.fromId;
                    const toId   = flow.toId;
                    if (fromId && String(fromId) !== String(entityId)) connectedSystemIds.add(String(fromId));
                    if (toId   && String(toId)   !== String(entityId)) connectedSystemIds.add(String(toId));
                });

                await Promise.all(Array.from(connectedSystemIds).map(async sysId => {
                    try {
                        const sysData = await window.BUDG_API_SERVICE.getSystemById(sysId);
                        if (!sysData || (sysData.error && sysData.error.includes('404'))) {
                            state.deletedSystems.add(String(sysId));
                            return;
                        }
                        if (sysData && !sysData.error) {
                            state.connectedSystems.set(String(sysId), { systemData: sysData });
                        } else if (sysData && sysData.error && sysData.error.includes('403')) {
                            state.inaccessibleSystems.add(String(sysId));
                        }
                    } catch (e) {
                        const statusCode = e.status || e.statusCode || (e.response && e.response.status);
                        const errorMessage = e.message || e.toString() || '';
                        const isNotFound = statusCode === 404 || errorMessage.includes('404') || errorMessage.toLowerCase().includes('not found');
                        if (isNotFound) state.deletedSystems.add(String(sysId));
                        /* skip inaccessible */
                    }
                }));

                const requestedDepth = Math.min(99, Math.max(1, parseInt(state.hopsCount, 10) || 15));
                await expandConnectedSystemsLineage(requestedDepth);
            }

            return { entity: state.systemData, _state: state };
        }

        function _mapEngineNodeBuilder(data) { return _getOrBuildGraph().nodes; }
        function _mapEngineEdgeBuilder(data) { return _getOrBuildGraph().edges; }

        return {
            loadMapData: loadMapData,
            loadDatasetLineageData: loadDatasetLineageData,
            buildSystemLineageGraph: buildSystemLineageGraph,
            buildDatasetLineageGraph: buildDatasetLineageGraph,
            getNeighborsForSystem: getNeighborsForSystem,
            expandConnectedSystemsLineage: expandConnectedSystemsLineage,
            fetchAndMergeSystem: fetchAndMergeSystem,
            _mapEngineApiLoader: _mapEngineApiLoader,
            _mapEngineNodeBuilder: _mapEngineNodeBuilder,
            _mapEngineEdgeBuilder: _mapEngineEdgeBuilder,
            invalidateGraphCache: function () { _cachedGraph = null; }
        };
    };
})();