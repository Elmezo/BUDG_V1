/**
 * dataset-facet-lineage-graph.js - data load and graph builders for dataset relationships map (facet).
 * Load after map-graph-utils.js.
 */
(function () {
    'use strict';

    window.createDatasetFacetLineageGraphPipeline = function (ctx) {
        var state = ctx.state;
        var adapter = ctx.adapter;
        var renderNetwork = ctx.renderNetwork;
        var showPlaceholder = ctx.showPlaceholder;
        var showLoading = ctx.showLoading;
        var hideLoading = ctx.hideLoading;
        var logPrefix = ctx.logPrefix || '[DATASET-MAP]';
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

        // Load all necessary data for the map
        async function loadMapData(datasetId) {
        showLoading();

        try {
            // Load dataset data to get system ID
            const datasetData = await window.BUDG_API_SERVICE.getDatasetById(datasetId);
            state.datasetData = datasetData?.data || datasetData;
            
            // Try multiple field names for system ID
            state.systemId = state.datasetData?.systemId || 
                                       state.datasetData?.masterSource || 
                                       state.datasetData?.MasterSource ||
                                       state.datasetData?.system_id;
            
            facetLog('[DATASET-MAP] Dataset data:', state.datasetData);
            facetLog('[DATASET-MAP] System ID:', state.systemId);
            
            if (!state.systemId) {
                console.error('[DATASET-MAP] Dataset has no system ID. Dataset data:', state.datasetData);
                hideLoading();
                showPlaceholder('Dataset has no associated system.');
                return;
            }

            // Load system data
            state.systemData = await window.BUDG_API_SERVICE.getSystemById(state.systemId);

            // Load based on map type
            if (state.mapType === 'dataset-lineage') {
                await loadDatasetLineageData();
            } else {
                await loadSystemLineageData();
            }

            // Build and render graph
            const graph = state.mapType === 'dataset-lineage' 
                ? buildDatasetLineageGraph() 
                : buildSystemLineageGraph();
            renderNetwork(graph);

        } catch (error) {
            console.error('[DATASET-MAP] Failed to load map data:', error);
            showPlaceholder('Failed to load map data.');
        } finally {
            hideLoading();
        }
    }

    // Load dataset lineage data
    async function loadDatasetLineageData() {
        try {
            const currentDatasetId = String(state.datasetId);
            const API = window.BUDG_API_SERVICE;
            
            // First, get all attributes of the opened dataset
            const openedDatasetAttributes = await fetchAllAttributesForDataset(currentDatasetId);
            const openedDatasetAttributeIds = new Set(openedDatasetAttributes.map(attr => String(attr.id)));
            facetLog('[DATASET-LINEAGE] Opened dataset has', openedDatasetAttributeIds.size, 'attributes');
            
            // Get relationships for the opened dataset (direct relationships)
            const relResp = await fetch(`/api/dataset-relationships/${currentDatasetId}`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });
            
            if (!relResp.ok) {
                throw new Error(`Failed to fetch relationships: ${relResp.status}`);
            }
            
            const relData = await relResp.json();
            const inbound = relData.inbound || [];
            const outbound = relData.outbound || [];
            
            facetLog('[DATASET-LINEAGE] Direct relationships:', { inbound: inbound.length, outbound: outbound.length });
            
            // Collect all linked dataset IDs (starting with direct relationships)
            const linkedDatasetIds = new Set([currentDatasetId]);
            const allRelationships = [];
            const datasetToSystemMap = new Map();
            const processedDatasets = new Set();
            
            // Process direct inbound relationships
            inbound.forEach(rel => {
                const sourceDatasetId = rel.sourceDatasetId || rel.targetDatasetId;
                const sourceAttrId = rel.Source_AttributeID || rel.sourceAttributeId;
                const targetAttrId = rel.Target_AttributeID || rel.targetAttributeId;
                
                // Check if target attribute belongs to opened dataset (inbound: target is opened dataset)
                if (sourceDatasetId && (openedDatasetAttributeIds.has(String(targetAttrId)) || openedDatasetAttributeIds.has(String(sourceAttrId)))) {
                    linkedDatasetIds.add(String(sourceDatasetId));
                    allRelationships.push({
                        ...rel,
                        sourceDatasetId: String(sourceDatasetId),
                        targetDatasetId: currentDatasetId,
                        sourceSystemId: rel.systemId,
                        targetSystemId: String(state.systemId),
                        sourceAttributeId: sourceAttrId,
                        targetAttributeId: targetAttrId
                    });
                    if (rel.systemId) {
                        datasetToSystemMap.set(String(sourceDatasetId), String(rel.systemId));
                    }
                }
            });
            
            // Process direct outbound relationships
            outbound.forEach(rel => {
                const targetDatasetId = rel.targetDatasetId || rel.sourceDatasetId;
                const sourceAttrId = rel.Source_AttributeID || rel.sourceAttributeId;
                const targetAttrId = rel.Target_AttributeID || rel.targetAttributeId;
                
                // Check if source attribute belongs to opened dataset (outbound: source is opened dataset)
                if (targetDatasetId && (openedDatasetAttributeIds.has(String(sourceAttrId)) || openedDatasetAttributeIds.has(String(targetAttrId)))) {
                    linkedDatasetIds.add(String(targetDatasetId));
                    allRelationships.push({
                        ...rel,
                        sourceDatasetId: currentDatasetId,
                        targetDatasetId: String(targetDatasetId),
                        sourceSystemId: String(state.systemId),
                        targetSystemId: rel.systemId,
                        sourceAttributeId: sourceAttrId,
                        targetAttributeId: targetAttrId
                    });
                    if (rel.systemId) {
                        datasetToSystemMap.set(String(targetDatasetId), String(rel.systemId));
                    }
                }
            });
            
            // Now recursively find datasets that have relationships with attributes from already-linked datasets
            // This implements: "datasets that have relationships with an attribute from one of the present datasets"
            const datasetsToProcess = Array.from(linkedDatasetIds).filter(id => id !== currentDatasetId);
            const allLinkedDatasetAttributeIds = new Map(); // datasetId -> Set of attribute IDs
            
            // Initialize with opened dataset attributes
            allLinkedDatasetAttributeIds.set(currentDatasetId, openedDatasetAttributeIds);
            
            // Process each linked dataset
            for (const datasetId of datasetsToProcess) {
                if (processedDatasets.has(datasetId)) continue;
                processedDatasets.add(datasetId);
                
                try {
                    // Get attributes for this linked dataset
                    const datasetAttributes = await fetchAllAttributesForDataset(datasetId);
                    const datasetAttributeIds = new Set(datasetAttributes.map(attr => String(attr.id)));
                    allLinkedDatasetAttributeIds.set(datasetId, datasetAttributeIds);
                    
                    // Get relationships for this linked dataset
                    const datasetRelResp = await fetch(`/api/dataset-relationships/${datasetId}`, {
                        method: 'GET',
                        credentials: 'include',
                        headers: { 'Content-Type': 'application/json' }
                    });
                    
                    if (datasetRelResp.ok) {
                        const datasetRelData = await datasetRelResp.json();
                        const datasetInbound = datasetRelData.inbound || [];
                        const datasetOutbound = datasetRelData.outbound || [];
                        
                        // Check each relationship to see if it involves attributes from already-linked datasets
                        for (const rel of [...datasetInbound, ...datasetOutbound]) {
                            const sourceAttrId = String(rel.Source_AttributeID || rel.sourceAttributeId || '');
                            const targetAttrId = String(rel.Target_AttributeID || rel.targetAttributeId || '');
                            
                            // Determine which dataset is the other one in this relationship
                            let otherDatasetId = null;
                            let thisDatasetAttrId = null; // Attribute ID from the current linked dataset
                            let otherDatasetAttrId = null; // Attribute ID from the other dataset
                            
                            if (datasetInbound.includes(rel)) {
                                // Inbound: this dataset is target, other is source
                                otherDatasetId = rel.sourceDatasetId || rel.targetDatasetId;
                                thisDatasetAttrId = targetAttrId; // Target attribute is in this dataset
                                otherDatasetAttrId = sourceAttrId; // Source attribute is in other dataset
                            } else {
                                // Outbound: this dataset is source, other is target
                                otherDatasetId = rel.targetDatasetId || rel.sourceDatasetId;
                                thisDatasetAttrId = sourceAttrId; // Source attribute is in this dataset
                                otherDatasetAttrId = targetAttrId; // Target attribute is in other dataset
                            }
                            
                            if (!otherDatasetId || otherDatasetId === datasetId || otherDatasetId === currentDatasetId) continue;
                            
                            // Check if this relationship involves an attribute from this linked dataset
                            // The requirement: "datasets that have relationships with an attribute from one of the present datasets"
                            // Since this dataset (datasetId) is already linked, any relationship it has should be included
                            // because it involves an attribute from a linked dataset (this dataset)
                            
                            // Simply check if the relationship involves an attribute from this linked dataset
                            // If yes, include the other dataset
                            let linksToLinkedDataset = false;
                            
                            // Check if thisDatasetAttrId (attribute from this linked dataset) is valid
                            // If it is, then this relationship involves an attribute from a linked dataset, so include it
                            if (thisDatasetAttrId && thisDatasetAttrId !== 'undefined' && thisDatasetAttrId !== 'null' && thisDatasetAttrId !== '') {
                                // This relationship involves an attribute from this linked dataset
                                // So the other dataset should be included
                                linksToLinkedDataset = true;
                            }
                            
                            // Only include if it links back to a linked dataset
                            if (linksToLinkedDataset && !linkedDatasetIds.has(String(otherDatasetId))) {
                                linkedDatasetIds.add(String(otherDatasetId));
                                
                                // Add relationship
                                const relSourceDatasetId = datasetInbound.includes(rel) ? String(otherDatasetId) : String(datasetId);
                                const relTargetDatasetId = datasetInbound.includes(rel) ? String(datasetId) : String(otherDatasetId);
                                
                                allRelationships.push({
                                    ...rel,
                                    sourceDatasetId: relSourceDatasetId,
                                    targetDatasetId: relTargetDatasetId,
                                    sourceSystemId: rel.systemId || datasetToSystemMap.get(relSourceDatasetId),
                                    targetSystemId: datasetToSystemMap.get(relTargetDatasetId) || rel.systemId,
                                    sourceAttributeId: sourceAttrId,
                                    targetAttributeId: targetAttrId
                                });
                                
                                if (rel.systemId) {
                                    datasetToSystemMap.set(String(otherDatasetId), String(rel.systemId));
                                }
                                
                                // Add to processing queue
                                if (!processedDatasets.has(String(otherDatasetId))) {
                                    datasetsToProcess.push(String(otherDatasetId));
                                }
                            }
                        }
                    }
                } catch (e) {
                    facetWarn(`[DATASET-LINEAGE] Error processing dataset ${datasetId}:`, e);
                }
            }
            
            state.datasetRelationships = allRelationships;
            
            // Fetch dataset details for all linked datasets
            const linkedDatasetsMap = new Map();
            
            // Add opened dataset
            linkedDatasetsMap.set(currentDatasetId, {
                dataset: state.datasetData,
                systemId: String(state.systemId),
                isLocked: false
            });
            
            // Fetch other linked datasets
            for (const datasetId of linkedDatasetIds) {
                if (datasetId === currentDatasetId) continue;
                
                const systemId = datasetToSystemMap.get(String(datasetId)) || String(state.systemId);
                
                try {
                    const datasetData = await API.getDatasetById(datasetId, null, { silent404: true });
                    const dataset = datasetData?.data || datasetData;
                    
                    if (dataset && (dataset.id || dataset.ID)) {
                        linkedDatasetsMap.set(String(datasetId), {
                            dataset: dataset,
                            systemId: systemId,
                            isLocked: false
                        });
                    }
                } catch (e) {
                    const statusCode = e.status || e.statusCode || (e.response && e.response.status);
                    const isForbidden = statusCode === 403;
                    
                    if (isForbidden) {
                        linkedDatasetsMap.set(String(datasetId), {
                            dataset: { id: datasetId, name: `Dataset ${datasetId}` },
                            systemId: systemId,
                            isLocked: true
                        });
                    }
                }
            }
            
            state.linkedDatasets = linkedDatasetsMap;
            
            facetLog('[DATASET-LINEAGE] Final linked datasets:', linkedDatasetsMap.size);
            
        } catch (error) {
            console.error('[DATASET-LINEAGE] Failed to load dataset lineage data:', error);
        }
    }

    // Fetch all attributes for a dataset
    async function fetchAllAttributesForDataset(datasetId) {
        try {
            const response = await fetch(`/api/attribute/${datasetId}`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });
            
            if (!response.ok) {
                facetWarn('[DATASET-MAP] Failed to fetch attributes for dataset', datasetId);
                return [];
            }
            
            const attrData = await response.json();
            const attrs = Array.isArray(attrData?.data) ? attrData.data : (Array.isArray(attrData) ? attrData : []);
            
            return attrs.map(attr => ({
                id: attr.id || attr.ID || attr.attribute_id,
                name: attr['Name attribute'] || attr.name || attr.primaryName || attr.PrimaryName || '',
                datasetId: String(datasetId)
            })).filter(attr => attr.id);
        } catch (e) {
            facetWarn('[DATASET-MAP] Failed to fetch attributes for dataset', datasetId, e);
            return [];
        }
    }

    // Load system lineage data
    async function loadSystemLineageData() {
        try {
            const currentSystemId = String(state.systemId);
            
            // Load interfaces
            const interfacesData = await window.BUDG_API_SERVICE.getSystemInterfaces(currentSystemId);
            state.interfacesData = Array.isArray(interfacesData?.data) ? interfacesData.data : (Array.isArray(interfacesData) ? interfacesData : []);
            
            // Load data flow
            const dataFlowData = await window.BUDG_API_SERVICE.getDataFlowOutsideInterfaces(currentSystemId);
            state.dataFlowData = Array.isArray(dataFlowData?.data) ? dataFlowData.data : (Array.isArray(dataFlowData) ? dataFlowData : []);
            
            // Get all systems that have datasets with attributes having relationships with the opened dataset
            // Case 1: Systems with attribute relationships (solid lines)
            const systemsWithAttributes = new Set();
            const currentDatasetId = String(state.datasetId);
            
            // Get relationships for the opened dataset
            const relResp = await fetch(`/api/dataset-relationships/${currentDatasetId}`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });
            
            if (relResp.ok) {
                const relData = await relResp.json();
                const allRels = [...(relData.inbound || []), ...(relData.outbound || [])];
                
                allRels.forEach(rel => {
                    if (rel.systemId) {
                        systemsWithAttributes.add(String(rel.systemId));
                    }
                });
            }
            
            state.systemsWithAttributes = systemsWithAttributes;
            
            // Case 2: Systems with interfaces but no data attributes (dotted lines)
            // These are systems that have interfaces with the current system but do NOT have
            // datasets with attributes having relationships with the opened dataset
            const systemsWithInterfacesOnly = new Set();
            const allConnectedSystemIds = new Set();
            
            // Collect all systems connected via interfaces
            state.interfacesData.forEach(iface => {
                const fromId = iface.fromId || iface.sourceSystemId;
                const toId = iface.toId || iface.targetSystemId;
                
                if (fromId && String(fromId) !== currentSystemId) {
                    allConnectedSystemIds.add(String(fromId));
                }
                if (toId && String(toId) !== currentSystemId) {
                    allConnectedSystemIds.add(String(toId));
                }
            });
            
            // Systems with interfaces only are those that have interfaces but are NOT in systemsWithAttributes
            allConnectedSystemIds.forEach(systemId => {
                if (!systemsWithAttributes.has(systemId)) {
                    systemsWithInterfacesOnly.add(systemId);
                }
            });
            
            state.systemsWithInterfacesOnly = systemsWithInterfacesOnly;
            
            // Fetch system details
            const allSystemIds = new Set([currentSystemId]);
            systemsWithAttributes.forEach(id => allSystemIds.add(id));
            systemsWithInterfacesOnly.forEach(id => allSystemIds.add(id));
            
            for (const systemId of allSystemIds) {
                if (systemId === currentSystemId) continue;
                
                try {
                    const systemData = await window.BUDG_API_SERVICE.getSystemById(systemId);
                    state.connectedSystems.set(String(systemId), {
                        systemData: systemData,
                        hasAttributes: systemsWithAttributes.has(String(systemId)),
                        interfacesOnly: systemsWithInterfacesOnly.has(String(systemId))
                    });
                } catch (e) {
                    const statusCode = e.status || e.statusCode;
                    if (statusCode === 403) {
                        state.inaccessibleSystems.add(String(systemId));
                    }
                }
            }
            
        } catch (error) {
            console.error('[SYSTEM-LINEAGE] Failed to load system lineage data:', error);
        }
    }

    // Build dataset lineage graph
    function buildDatasetLineageGraph() {
        const nodesMap = new Map();
        const edges = [];
        const edgeMap = new Map();
        const currentDatasetId = String(state.datasetId);
        const currentSystemId = String(state.systemId);
        
        // Create nodes for each dataset
        state.linkedDatasets.forEach((info, datasetId) => {
            const systemId = String(info.systemId);
            const dataset = info.dataset;
            const isLocked = info.isLocked;
            
            const nodeId = `dataset-${datasetId}`;
            const datasetName = dataset.name || dataset.primaryName || dataset.PrimaryName || `Dataset ${datasetId}`;
            const refNumber = dataset.refNumber || dataset.RefNumber || dataset.ref || `DS-${datasetId}`;
            
            // Get system name
            let systemName = 'Unknown System';
            if (systemId === currentSystemId) {
                systemName = state.systemData?.name || 'Current System';
            } else {
                const sysInfo = state.connectedSystems.get(systemId);
                systemName = sysInfo?.systemData?.name || `System ${systemId}`;
            }
            
            const label = `${systemName}\n${datasetName}\n${refNumber}`;
            const isCurrentDataset = datasetId === currentDatasetId;
            
            const typeName = dataset.typeName || dataset.datasetTypeName || dataset.type || '';
            const lifecycleName = dataset.lifecycleName || dataset.lifecycle || '';
            
            nodesMap.set(nodeId, {
                id: nodeId,
                label: label,
                group: 'dataset',
                isCurrent: isCurrentDataset,
                isLocked: isLocked,
                meta: {
                    datasetId: datasetId,
                    datasetName: datasetName,
                    refNumber: refNumber,
                    systemId: systemId,
                    systemName: systemName,
                    locked: isLocked,
                    isCurrentDataset: isCurrentDataset,
                    typeName: typeName,
                    lifecycleName: lifecycleName
                }
            });
        });
        
        // Create edges based on relationships
        state.datasetRelationships.forEach(rel => {
            const sourceDatasetId = rel.sourceDatasetId;
            const targetDatasetId = rel.targetDatasetId;
            
            if (!sourceDatasetId || !targetDatasetId) return;
            
            const sourceNodeId = `dataset-${sourceDatasetId}`;
            const targetNodeId = `dataset-${targetDatasetId}`;
            
            if (nodesMap.has(sourceNodeId) && nodesMap.has(targetNodeId)) {
                const edgeId = `${sourceNodeId}->${targetNodeId}`;
                if (!edgeMap.has(edgeId)) {
                    edgeMap.set(edgeId, true);
                    edges.push({
                        id: edgeId,
                        from: sourceNodeId,
                        to: targetNodeId,
                        label: '',
                        dashes: false, // Solid lines for dataset lineage
                        lineStyle: 'solid',
                        lineType: 'attribute-lineage'
                    });
                }
            }
        });
        
        // Collect filter options
        const types = new Set();
        const lifecycles = new Set();
        
        nodesMap.forEach(node => {
            const meta = node.meta || {};
            if (meta.typeName) types.add(meta.typeName);
            if (meta.lifecycleName) lifecycles.add(meta.lifecycleName);
        });
        
        // Emit filter options
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
        if (!state.hiddenNodes) state.hiddenNodes = new Set();
        const { nodes: filteredNodes, edges: filteredEdges } = adapter.filterHiddenNodes(resultNodes, resultEdges);

        return {
            nodes: filteredNodes,
            edges: filteredEdges
        };
    }


    // Build system lineage graph
    function buildSystemLineageGraph() {
        const nodesMap = new Map();
        const edges = [];
        const edgeMap = new Map();
        const currentSystemId = String(state.systemId);
        
        // Apply filters
        const showSystemInterfaces = state.filters.systemInterfaces !== false;
        const showDataAttributeLinks = state.filters.dataAttributeLinks !== false;
        
        // Add current system
        const currentSystemName = state.systemData?.name || 'Current System';
        nodesMap.set(currentSystemId, {
            id: currentSystemId,
            label: currentSystemName,
            group: 'system',
            isCurrent: true,
            isLocked: false,
            meta: {
                systemId: currentSystemId,
                systemName: currentSystemName,
                classification: state.systemData?.classificationName || '',
                type: state.systemData?.typeName || '',
                lifecycle: state.systemData?.lifecycleName || ''
            }
        });
        
        // Add systems with attributes (Case 1 - solid lines) - only if dataAttributeLinks filter is enabled
        if (showDataAttributeLinks) {
            state.systemsWithAttributes.forEach(systemId => {
                const sysInfo = state.connectedSystems.get(String(systemId));
                if (sysInfo && sysInfo.systemData) {
                    const systemName = sysInfo.systemData.name || `System ${systemId}`;
                    nodesMap.set(String(systemId), {
                        id: String(systemId),
                        label: systemName,
                        group: 'system',
                        isCurrent: false,
                        isLocked: state.inaccessibleSystems && state.inaccessibleSystems.has(String(systemId)),
                        hasAttributes: true,
                        meta: {
                            systemId: String(systemId),
                            systemName: systemName,
                            classification: sysInfo.systemData.classificationName || '',
                            type: sysInfo.systemData.typeName || '',
                            lifecycle: sysInfo.systemData.lifecycleName || ''
                        }
                    });
                }
            });
        }
        
        // Add systems with interfaces only (Case 2 - dotted lines) - only if systemInterfaces filter is enabled
        if (showSystemInterfaces) {
            state.systemsWithInterfacesOnly.forEach(systemId => {
                const sysInfo = state.connectedSystems.get(String(systemId));
                if (sysInfo && sysInfo.systemData) {
                    const systemName = sysInfo.systemData.name || `System ${systemId}`;
                    nodesMap.set(String(systemId), {
                        id: String(systemId),
                        label: systemName,
                        group: 'system',
                        isCurrent: false,
                        isLocked: state.inaccessibleSystems && state.inaccessibleSystems.has(String(systemId)),
                        interfacesOnly: true,
                        meta: {
                            systemId: String(systemId),
                            systemName: systemName,
                            classification: sysInfo.systemData.classificationName || '',
                            type: sysInfo.systemData.typeName || '',
                            lifecycle: sysInfo.systemData.lifecycleName || ''
                        }
                    });
                }
            });
        }
        
        // Create edges
        // Case 1: Solid lines for systems with attributes
        if (showDataAttributeLinks) {
            state.systemsWithAttributes.forEach(systemId => {
                const systemIdStr = String(systemId);
                if (!nodesMap.has(systemIdStr)) return;
                
                const edgeId = `${systemIdStr}->${currentSystemId}`;
                const reverseEdgeId = `${currentSystemId}->${systemIdStr}`;
                
                if (!edgeMap.has(edgeId) && !edgeMap.has(reverseEdgeId)) {
                    edgeMap.set(edgeId, true);
                    edges.push({
                        id: edgeId,
                        from: systemIdStr,
                        to: currentSystemId,
                        label: '',
                        dashes: false, // Solid line
                        lineStyle: 'solid',
                        lineType: 'attribute-lineage'
                    });
                }
            });
        }
        
        // Case 2: Dotted lines for systems with interfaces only
        if (showSystemInterfaces) {
            state.systemsWithInterfacesOnly.forEach(systemId => {
                const systemIdStr = String(systemId);
                if (!nodesMap.has(systemIdStr)) return;
                
                const edgeId = `${systemIdStr}->${currentSystemId}`;
                const reverseEdgeId = `${currentSystemId}->${systemIdStr}`;
                
                if (!edgeMap.has(edgeId) && !edgeMap.has(reverseEdgeId)) {
                    edgeMap.set(edgeId, true);
                    edges.push({
                        id: edgeId,
                        from: systemIdStr,
                        to: currentSystemId,
                        label: '',
                        dashes: true, // Dotted line
                        lineStyle: 'dashed',
                        lineType: 'interface'
                    });
                }
            });
        }
        
        // Collect filter options (classifications, types, lifecycles)
        const classifications = new Set();
        const types = new Set();
        const lifecycles = new Set();
        
        nodesMap.forEach(node => {
            if (node.meta) {
                if (node.meta.classification) classifications.add(node.meta.classification);
                if (node.meta.type) types.add(node.meta.type);
                if (node.meta.lifecycle) lifecycles.add(node.meta.lifecycle);
            }
        });
        
        // Emit filter options for system lineage
        if (typeof window !== 'undefined' && window.dispatchEvent) {
            window.dispatchEvent(new CustomEvent('systemMapFilterOptionsUpdated', {
                detail: {
                    classifications: Array.from(classifications).filter(Boolean).sort(),
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
        if (!state.hiddenNodes) state.hiddenNodes = new Set();
        const { nodes: filteredNodes, edges: filteredEdges } = adapter.filterHiddenNodes(resultNodes, resultEdges);

        return {
            nodes: filteredNodes,
            edges: filteredEdges
        };
    }
        async function _mapEngineApiLoader(entityId, mapType, filters) {
            state.datasetId = entityId;
            if (mapType) { state.mapType = mapType; }
            adapter.mergeEngineFilters(filters);

            state.datasetData         = null;
            state.systemId            = null;
            state.systemData          = null;
            state.interfacesData      = [];
            state.dataFlowData        = [];
            state.connectedSystems    = new Map();
            state.inaccessibleSystems = new Set();
            state.interfaceIds        = new Set();
            state.dataFlowKeys        = new Set();
            state.datasetsData        = [];
            state.linkedDatasets      = new Map();
            state.datasetRelationships = [];
            _cachedGraph = null;

            const datasetData = await window.BUDG_API_SERVICE.getDatasetById(entityId);
            state.datasetData = datasetData?.data || datasetData;
            state.systemId = state.datasetData?.systemId
                || state.datasetData?.masterSource
                || state.datasetData?.MasterSource
                || state.datasetData?.system_id;

            if (!state.systemId) {
                return { entity: state.datasetData, _state: state };
            }

            state.systemData = await window.BUDG_API_SERVICE.getSystemById(state.systemId);

            if (state.mapType === 'dataset-lineage') {
                await loadDatasetLineageData();
            } else {
                await loadSystemLineageData();
            }

            return { entity: state.datasetData, _state: state };
        }

        function _getOrBuildGraph() {
            if (!_cachedGraph) {
                _cachedGraph = state.mapType === 'dataset-lineage'
                    ? buildDatasetLineageGraph()
                    : buildSystemLineageGraph();
            }
            return _cachedGraph;
        }

        function _mapEngineNodeBuilder(data) { return _getOrBuildGraph().nodes; }
        function _mapEngineEdgeBuilder(data) { return _getOrBuildGraph().edges; }

        return {
            loadMapData: loadMapData,
            loadDatasetLineageData: loadDatasetLineageData,
            loadSystemLineageData: loadSystemLineageData,
            buildDatasetLineageGraph: buildDatasetLineageGraph,
            buildSystemLineageGraph: buildSystemLineageGraph,
            invalidateGraphCache: invalidateGraphCache,
            _mapEngineApiLoader: _mapEngineApiLoader,
            _mapEngineNodeBuilder: _mapEngineNodeBuilder,
            _mapEngineEdgeBuilder: _mapEngineEdgeBuilder
        };
    };
})();