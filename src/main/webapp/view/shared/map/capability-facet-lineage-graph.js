/**
 * capability-facet-lineage-graph.js - data load and graph builders for capability relationships map.
 * Load after map-graph-utils.js.
 */
(function () {
    'use strict';

    window.createCapabilityFacetLineageGraphPipeline = function (ctx) {
        var state = ctx.state;
        var adapter = ctx.adapter;
        var renderNetwork = ctx.renderNetwork;
        var showPlaceholder = ctx.showPlaceholder;
        var showLoading = ctx.showLoading;
        var hideLoading = ctx.hideLoading;
        var updateLegend = ctx.updateLegend;
        var populateFilterOptions = ctx.populateFilterOptions;
        var logPrefix = ctx.logPrefix || '[CAPABILITY-MAP]';
        var _cachedGraph = null;

        function invalidateGraphCache() {
            _cachedGraph = null;
        }

        async function loadMapData(capabilityId) {
            showLoading();
            try {
                const capabilityData = await window.BUDG_API_SERVICE.getCapabilityById(capabilityId);
                state.capabilityData = capabilityData?.data || capabilityData;
                console.log(logPrefix + ' Capability data:', state.capabilityData);
                await loadCapabilityLineageData(capabilityId);
                const graph = buildCapabilityLineageGraph();
                renderNetwork(graph);
                if (typeof updateLegend === 'function') {
                    updateLegend();
                }
                if (typeof populateFilterOptions === 'function') {
                    populateFilterOptions().catch(function (err) {
                        console.warn('[CAPABILITY-MAP] Failed to populate filter options:', err);
                    });
                }
            } catch (error) {
                console.error('[CAPABILITY-MAP] Failed to load map data:', error);
                showPlaceholder('Failed to load map data.');
            } finally {
                hideLoading();
            }
        }

        // Load capability lineage data
    // Builds hierarchy showing only direct path from root to current, then current's children (no siblings)
    async function loadCapabilityLineageData(capabilityId) {
        try {
            const API = window.BUDG_API_SERVICE;
            
            // Get all capabilities to build hierarchy
            const allCapabilitiesResp = await API.getCapabilities();
            const allCapabilities = Array.isArray(allCapabilitiesResp?.data) 
                ? allCapabilitiesResp.data 
                : (Array.isArray(allCapabilitiesResp) ? allCapabilitiesResp : []);
            
            state.allCapabilities = allCapabilities;
            console.log('[CAPABILITY-LINEAGE] Loaded', allCapabilities.length, 'capabilities');
            
            // Build capability map for quick lookup
            const capabilityMap = new Map();
            allCapabilities.forEach(cap => {
                const id = String(cap.id || cap.ID);
                capabilityMap.set(id, cap);
            });
            
            const currentId = String(capabilityId);
            const currentCapability = capabilityMap.get(currentId);
            
            if (!currentCapability) {
                console.error('[CAPABILITY-LINEAGE] Current capability not found:', capabilityId);
                state.lineageCapabilities = [];
                return;
            }
            
            // Build lineage: ancestors (path to root) + all descendants (recursive children).
            // The final hop trim is applied in buildCapabilityLineageGraph via
            // adapter.applyHopsFilter, so loading the full ancestor path and the full
            // descendant subtree lets the user expand/contract freely via hopsCount.
            const lineageCapabilities = new Set();

            // 1) Ancestors: follow parentId up to root.
            const pathToCurrent = [];
            let current = currentCapability;
            while (current) {
                pathToCurrent.unshift(current);
                const parentId = String(current.parentId || current.Parent_ID || current.parent_id || '');
                if (parentId && parentId !== '0' && capabilityMap.has(parentId)) {
                    current = capabilityMap.get(parentId);
                } else {
                    break;
                }
            }
            pathToCurrent.forEach(cap => {
                lineageCapabilities.add(String(cap.id || cap.ID));
            });

            // 2) Descendants: BFS over children (not just direct) so N hops expand N levels down.
            const childrenByParent = new Map();
            allCapabilities.forEach(cap => {
                const parentId = String(cap.parentId || cap.Parent_ID || cap.parent_id || '');
                if (!parentId) return;
                if (!childrenByParent.has(parentId)) childrenByParent.set(parentId, []);
                childrenByParent.get(parentId).push(cap);
            });

            const descendantQueue = [currentId];
            const descendantVisited = new Set([currentId]);
            while (descendantQueue.length > 0) {
                const parentKey = descendantQueue.shift();
                const children = childrenByParent.get(parentKey) || [];
                children.forEach(child => {
                    const childId = String(child.id || child.ID);
                    if (descendantVisited.has(childId)) return;
                    descendantVisited.add(childId);
                    lineageCapabilities.add(childId);
                    descendantQueue.push(childId);
                });
            }
            
            // Convert to array of capability objects
            state.lineageCapabilities = Array.from(lineageCapabilities)
                .map(id => capabilityMap.get(id))
                .filter(Boolean);
            
            console.log('[CAPABILITY-LINEAGE] Lineage capabilities:', state.lineageCapabilities.length);
            console.log('[CAPABILITY-LINEAGE] Path to current:', pathToCurrent.map(c => c.primaryName || c.PrimaryName || c.name || c.Name));
            
        } catch (error) {
            console.error('[CAPABILITY-LINEAGE] Failed to load capability lineage data:', error);
            state.lineageCapabilities = [];
        }
    }

    // Helper function to get node ID from capability
    function getNodeIdFromCapability(cap) {
        const capId = String(cap.id || cap.ID);
        const capName = cap.primaryName || cap.PrimaryName || cap.name || cap.Name || `Capability ${capId}`;
        const sanitizedCapName = capName.replace(/[^a-zA-Z0-9]/g, '_').toLowerCase();
        return sanitizedCapName || `capability_${capId}`;
    }

    // Build capability lineage graph
    function buildCapabilityLineageGraph() {
        const nodesMap = new Map();
        const edges = [];
        const edgeMap = new Map();
        const currentCapabilityId = String(state.capabilityId);
        
        // Create capability map for quick lookup
        const capabilityMap = new Map();
        state.lineageCapabilities.forEach(cap => {
            const id = String(cap.id || cap.ID);
            capabilityMap.set(id, cap);
        });
        
        // Create nodes for each capability in lineage
        state.lineageCapabilities.forEach(cap => {
            const capId = String(cap.id || cap.ID);
            const isCurrent = capId === currentCapabilityId;
            const capabilityName = cap.primaryName || cap.PrimaryName || cap.name || cap.Name || `Capability ${capId}`;
            const refNumber = cap.refNumber || cap.RefNumber || cap.ref || `CAP-${capId}`;
            
            // Use capability name for node ID (sanitized)
            const nodeId = getNodeIdFromCapability(cap);
            const label = `${capabilityName}\n${refNumber}`;
            
            // Get lifecycle from capability
            const lifecycle = cap.lifecycleName || cap.LifecycleName || cap.lifecycle || cap.Lifecycle || '';
            
            nodesMap.set(nodeId, {
                id: nodeId,
                label: label,
                group: 'capability',
                isCurrent: isCurrent,
                isLocked: false,
                lifecycle: lifecycle,
                lifecycleName: lifecycle,
                meta: {
                    capabilityId: capId,
                    capabilityName: capabilityName,
                    refNumber: refNumber,
                    isCurrentCapability: isCurrent,
                    lifecycle: lifecycle,
                    lifecycleName: lifecycle
                }
            });
        });
        
        // Create edges based on parent-child relationships
        state.lineageCapabilities.forEach(cap => {
            const capId = String(cap.id || cap.ID);
            const parentId = String(cap.parentId || cap.Parent_ID || cap.parent_id || '');
            
            // Only create edge if parent is also in lineage (to maintain path-only structure)
            if (parentId && parentId !== '0' && capabilityMap.has(parentId)) {
                // Find parent and child nodes by their IDs
                const parentCap = capabilityMap.get(parentId);
                const childCap = capabilityMap.get(capId);
                
                if (!parentCap || !childCap) return;
                
                const parentName = parentCap.primaryName || parentCap.PrimaryName || parentCap.name || parentCap.Name || `Capability ${parentId}`;
                const childName = childCap.primaryName || childCap.PrimaryName || childCap.name || childCap.Name || `Capability ${capId}`;
                
                const sanitizedParentName = parentName.replace(/[^a-zA-Z0-9]/g, '_').toLowerCase();
                const sanitizedChildName = childName.replace(/[^a-zA-Z0-9]/g, '_').toLowerCase();
                
                const sourceNodeId = sanitizedParentName || `capability_${parentId}`;
                const targetNodeId = sanitizedChildName || `capability_${capId}`;
                
                if (nodesMap.has(sourceNodeId) && nodesMap.has(targetNodeId)) {
                    const edgeId = `${sourceNodeId}->${targetNodeId}`;
                    if (!edgeMap.has(edgeId)) {
                        edgeMap.set(edgeId, true);
                        edges.push({
                            id: edgeId,
                            from: sourceNodeId,
                            to: targetNodeId,
                            label: '',
                            dashes: false,
                            lineStyle: 'solid',
                            lineType: 'capability-lineage'
                        });
                    }
                }
            }
        });
        
        // Apply overlay if enabled
        if (state.overlay === 'system' && state.systemOverlayData.length > 0) {
            // Add system nodes from overlay
            state.systemOverlayData.forEach(system => {
                const systemId = String(system.system_id || system.systemId || system.id || system.ID);
                const systemName = system.systemName || system.name || system.Name || `System ${systemId}`;
                
                const nodeId = `system-${systemId}`;
                if (!nodesMap.has(nodeId)) {
                    nodesMap.set(nodeId, {
                        id: nodeId,
                        label: systemName,
                        group: 'system',
                        isCurrent: false,
                        isLocked: false,
                        meta: {
                            systemId: systemId,
                            systemName: systemName,
                            isOverlay: true
                        }
                    });
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
        // Filter out hidden nodes
        if (!state.hiddenNodes) state.hiddenNodes = new Set();
        const { nodes: filteredNodes, edges: filteredEdges } = adapter.filterHiddenNodes(resultNodes, resultEdges);

        return {
            nodes: filteredNodes,
            edges: filteredEdges
        };
    }
        async function _mapEngineApiLoader(entityId, mapType, filters) {
            state.capabilityId = entityId;
            if (mapType) { state.mapType = mapType; }
            adapter.mergeEngineFilters(filters);

            state.allCapabilities = [];
            state.lineageCapabilities = [];
            state.systemOverlayData = [];
            _cachedGraph = null;

            const capabilityData = await window.BUDG_API_SERVICE.getCapabilityById(entityId);
            state.capabilityData = capabilityData?.data || capabilityData;

            await loadCapabilityLineageData(entityId);

            return { entity: state.capabilityData, _state: state };
        }

        function _getOrBuildGraph() {
            if (!_cachedGraph) {
                _cachedGraph = buildCapabilityLineageGraph();
            }
            return _cachedGraph;
        }

        function _mapEngineNodeBuilder(data) { return _getOrBuildGraph().nodes; }
        function _mapEngineEdgeBuilder(data) { return _getOrBuildGraph().edges; }

        return {
            loadMapData: loadMapData,
            loadCapabilityLineageData: loadCapabilityLineageData,
            buildCapabilityLineageGraph: buildCapabilityLineageGraph,
            getNodeIdFromCapability: getNodeIdFromCapability,
            invalidateGraphCache: invalidateGraphCache,
            _mapEngineApiLoader: _mapEngineApiLoader,
            _mapEngineNodeBuilder: _mapEngineNodeBuilder,
            _mapEngineEdgeBuilder: _mapEngineEdgeBuilder
        };
    };
})();