/**
 * glossary-data-facet-graph.js - data load and buildGraph for glossary data map.
 * Load after map-graph-utils.js.
 */
(function () {
    'use strict';

    window.createGlossaryDataFacetGraphPipeline = function (ctx) {
        var state = ctx.state;
        var adapter = ctx.adapter;
        var renderNetwork = ctx.renderNetwork;
        var showPlaceholder = ctx.showPlaceholder;
        var showLoading = ctx.showLoading;
        var hideLoading = ctx.hideLoading;
        var updateLegend = ctx.updateLegend;
        var logPrefix = ctx.logPrefix || '[GLOSSARY-DATA-MAP]';
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
        async function loadMapData(glossaryId) {
            showLoading();

            try {
                // Load glossary data
                const glossaryResponse = await window.BUDG_API_SERVICE.getGlossaryById(glossaryId);
                state.glossaryData = glossaryResponse?.data || glossaryResponse;
                facetLog('[GLOSSARY-DATA-MAP] Glossary data:', state.glossaryData);
                
                // Determine case type and load associated data
                await determineCaseType(glossaryId);
                
                // Load data based on map type
                if (state.mapType === 'system-lineage') {
                    await loadSystemLineageData(glossaryId);
                } else if (state.mapType === 'dataset-lineage') {
                    await loadDatasetLineageData(glossaryId);
                } else if (state.mapType === 'multi-node-lineage') {
                    await loadMultiNodeLineageData(glossaryId);
                }

                // Build and render graph
                const graph = buildGraph();
                renderNetwork(graph);
                if (typeof updateLegend === 'function') {
                    updateLegend();
                }

            } catch (error) {
                console.error('[GLOSSARY-DATA-MAP] Failed to load map data:', error);
                showPlaceholder('Failed to load map data.');
            } finally {
                hideLoading();
            }
        }

        // Determine case type (Case 1: dataset, Case 2: attribute)
        async function determineCaseType(glossaryId) {
            try {
                // Check if glossary is directly associated with any dataset
                const datasets = await window.BUDG_API_SERVICE.getGlossaryDatasets(glossaryId);
                const directDatasets = Array.isArray(datasets) ? datasets : (datasets?.data || []);
                
                if (directDatasets && directDatasets.length > 0) {
                    // Case 1: Glossary directly associated with dataset
                    state.caseType = 'case1';
                    
                    // Collect directly linked systems and datasets
                    directDatasets.forEach(ds => {
                        const systemId = ds.systemId || ds.system_id || ds.System_ID;
                        if (systemId) {
                            state.directlyLinkedSystems.add(String(systemId));
                        }
                        const datasetId = ds.id || ds.datasetId || ds.dataset_id || ds.ID;
                        if (datasetId) {
                            state.directlyLinkedDatasets.add(String(datasetId));
                        }
                    });
                } else {
                    // Check if glossary is associated with any attribute
                    const attributes = await window.BUDG_API_SERVICE.getGlossaryAttributes(glossaryId);
                    const directAttributes = Array.isArray(attributes) ? attributes : (attributes?.data || []);
                    
                    if (directAttributes && directAttributes.length > 0) {
                        // Case 2: Glossary associated with attribute
                        state.caseType = 'case2';
                        
                        // Collect directly linked attributes and their systems/datasets
                        directAttributes.forEach(attr => {
                            const attrId = attr.attributeId || attr.id || attr.ID || attr.attribute_id;
                            if (attrId) {
                                state.directlyLinkedAttributes.add(String(attrId));
                            }
                            const systemId = attr.systemId || attr.system_id || attr.System_ID;
                            if (systemId) {
                                state.directlyLinkedSystems.add(String(systemId));
                            }
                            const datasetId = attr.datasetId || attr.dataset_id || attr.Dataset_ID;
                            if (datasetId) {
                                state.directlyLinkedDatasets.add(String(datasetId));
                            }
                        });
                    } else {
                        // No direct associations found
                        state.caseType = null;
                    }
                }
                
                facetLog('[GLOSSARY-DATA-MAP] Case type determined:', state.caseType);
                facetLog('[GLOSSARY-DATA-MAP] Directly linked systems:', Array.from(state.directlyLinkedSystems));
                facetLog('[GLOSSARY-DATA-MAP] Directly linked datasets:', Array.from(state.directlyLinkedDatasets));
                facetLog('[GLOSSARY-DATA-MAP] Directly linked attributes:', Array.from(state.directlyLinkedAttributes));
            } catch (error) {
                console.error('[GLOSSARY-DATA-MAP] Error determining case type:', error);
                state.caseType = null;
            }
        }

        // Load system lineage data (hop-bounded BFS from each directly linked seed system).
        async function loadSystemLineageData(glossaryId) {
            try {
                state.missingSystems.clear();
                const depthLimit = Math.min(99, Math.max(1, parseInt(state.hopsCount, 10) || 15));
                const interfaceIds = new Set();
                const dataFlowKeys = new Set();
                state.interfacesData.forEach(iface => {
                    if (iface?.id != null) interfaceIds.add(String(iface.id));
                });
                state.dataFlowData.forEach(flow => {
                    const key = window.MapGraphUtils.buildDataFlowKey(flow, { useArrowSeparator: true });
                    if (key) dataFlowKeys.add(key);
                });

                const systemsFullyLoaded = new Set();
                const systemDepth = new Map();
                const systemQueue = [];
                state.directlyLinkedSystems.forEach(id => {
                    const idStr = String(id);
                    systemDepth.set(idStr, 0);
                    systemQueue.push(idStr);
                });

                while (systemQueue.length > 0) {
                    const systemId = systemQueue.shift();
                    if (systemsFullyLoaded.has(systemId)) continue;
                    const currentDepth = systemDepth.get(systemId) || 0;
                    try {
                        if (!state.systemsData.has(systemId)) {
                            const systemData = await window.BUDG_API_SERVICE.getSystemById(systemId);
                            if (systemData && !systemData.error) {
                                state.systemsData.set(systemId, systemData);
                                state.connectedSystems.set(systemId, { systemData });
                            } else if (systemData && systemData.error) {
                                const err = (systemData.error || '').toLowerCase();
                                if (err.includes('403') || err.includes('forbidden')) {
                                    state.inaccessibleSystems.add(systemId);
                                    systemsFullyLoaded.add(systemId);
                                    continue;
                                } else if (err.includes('not found')) {
                                    state.missingSystems.add(systemId);
                                    systemsFullyLoaded.add(systemId);
                                    continue;
                                }
                            }
                        }

                        const interfaces = await window.BUDG_API_SERVICE.getSystemInterfaces(systemId).catch(() => null);
                        const interfacesList = Array.isArray(interfaces?.data) ? interfaces.data : (Array.isArray(interfaces) ? interfaces : []);
                        interfacesList.forEach(iface => {
                            const ifaceId = iface?.id;
                            if (ifaceId != null && !interfaceIds.has(String(ifaceId))) {
                                state.interfacesData.push(iface);
                                interfaceIds.add(String(ifaceId));
                            }
                        });

                        const dataFlow = await window.BUDG_API_SERVICE.getDataFlowOutsideInterfaces(systemId).catch(() => null);
                        const dataFlowList = Array.isArray(dataFlow?.data) ? dataFlow.data : (Array.isArray(dataFlow) ? dataFlow : []);
                        dataFlowList.forEach(flow => {
                            const key = window.MapGraphUtils.buildDataFlowKey(flow, { useArrowSeparator: true });
                            if (key && !dataFlowKeys.has(key)) {
                                state.dataFlowData.push(flow);
                                dataFlowKeys.add(key);
                            }
                        });
                        systemsFullyLoaded.add(systemId);

                        if (currentDepth >= depthLimit) continue;
                        const neighbors = new Set();
                        interfacesList.forEach(iface => {
                            const fromId = String(iface.fromId || iface.sourceSystemId || '');
                            const toId = String(iface.toId || iface.targetSystemId || '');
                            if (fromId && fromId !== systemId) neighbors.add(fromId);
                            if (toId && toId !== systemId) neighbors.add(toId);
                        });
                        dataFlowList.forEach(flow => {
                            const fromId = String(flow.fromId || flow.sourceSystemId || '');
                            const toId = String(flow.toId || flow.targetSystemId || '');
                            if (fromId && fromId !== systemId) neighbors.add(fromId);
                            if (toId && toId !== systemId) neighbors.add(toId);
                        });
                        neighbors.forEach(nbId => {
                            const nextDepth = currentDepth + 1;
                            if (!systemDepth.has(nbId) || systemDepth.get(nbId) > nextDepth) {
                                systemDepth.set(nbId, nextDepth);
                            }
                            if (!systemsFullyLoaded.has(nbId)) {
                                systemQueue.push(nbId);
                            }
                        });
                    } catch (e) {
                        const statusCode = e.status || e.statusCode || (e.response && e.response.status);
                        const msg = (e.message || e.toString() || '').toLowerCase();
                        const isNotFound = statusCode === 404 || msg.includes('not found');
                        const isForbidden = statusCode === 403 || msg.includes('403') || msg.includes('forbidden');
                        if (isNotFound) state.missingSystems.add(systemId);
                        else if (isForbidden) state.inaccessibleSystems.add(systemId);
                        systemsFullyLoaded.add(systemId);
                    }
                }

                if (state.missingSystems.size > 0) {
                    facetWarn('[GLOSSARY-DATA-MAP] Some systems no longer exist (e.g. deleted but still referenced):', Array.from(state.missingSystems));
                }

                await buildOverlayData();

            } catch (error) {
                console.error('[GLOSSARY-DATA-MAP] Error loading system lineage data:', error);
            }
        }

        // Load dataset lineage data
        async function loadDatasetLineageData(glossaryId) {
            try {
                // Get all datasets linked with the glossary (directly or via attributes)
                const datasets = await window.BUDG_API_SERVICE.getGlossaryDatasets(glossaryId);
                const allDatasets = Array.isArray(datasets) ? datasets : (datasets?.data || []);
                
                const attributes = await window.BUDG_API_SERVICE.getGlossaryAttributes(glossaryId);
                const allAttributes = Array.isArray(attributes) ? attributes : (attributes?.data || []);
                
                // Collect all linked dataset IDs
                const linkedDatasetIds = new Set();
                
                // Add datasets directly associated with glossary
                allDatasets.forEach(ds => {
                    const datasetId = ds.id || ds.datasetId || ds.dataset_id || ds.ID;
                    if (datasetId) {
                        linkedDatasetIds.add(String(datasetId));
                    }
                });
                
                // Add datasets from attributes
                allAttributes.forEach(attr => {
                    const datasetId = attr.datasetId || attr.dataset_id || attr.Dataset_ID;
                    if (datasetId) {
                        linkedDatasetIds.add(String(datasetId));
                    }
                });
                
                // Hop-bounded BFS from every seed dataset over
                // /api/dataset-relationships/{id} to discover indirect datasets.
                const depthLimit = Math.min(99, Math.max(1, parseInt(state.hopsCount, 10) || 15));
                const datasetDepth = new Map();
                const datasetQueue = [];
                linkedDatasetIds.forEach(id => {
                    const idStr = String(id);
                    datasetDepth.set(idStr, 0);
                    datasetQueue.push(idStr);
                });
                const processedDatasets = new Set();
                while (datasetQueue.length > 0) {
                    const datasetId = datasetQueue.shift();
                    if (processedDatasets.has(datasetId)) continue;
                    processedDatasets.add(datasetId);
                    const currentDepth = datasetDepth.get(datasetId) || 0;
                    try {
                        if (!state.datasetsData.has(datasetId)) {
                            const datasetData = await window.BUDG_API_SERVICE.getDatasetById(datasetId, null, { silent404: true });
                            const dataset = datasetData?.data || datasetData;
                            if (dataset) state.datasetsData.set(datasetId, dataset);
                        }
                        if (currentDepth >= depthLimit) continue;
                        const relResp = await fetch(`/api/dataset-relationships/${datasetId}`, {
                            method: 'GET',
                            credentials: 'include',
                            headers: { 'Content-Type': 'application/json' }
                        });
                        if (relResp.ok) {
                            const relData = await relResp.json();
                            const allRels = [...(relData.inbound || []), ...(relData.outbound || [])];
                            allRels.forEach(rel => {
                                state.datasetRelationships.push(rel);
                                const srcId = String(rel.sourceDatasetId || '');
                                const tgtId = String(rel.targetDatasetId || '');
                                const nextDepth = currentDepth + 1;
                                [srcId, tgtId].forEach(nbId => {
                                    if (!nbId || nbId === datasetId) return;
                                    if (!datasetDepth.has(nbId) || datasetDepth.get(nbId) > nextDepth) {
                                        datasetDepth.set(nbId, nextDepth);
                                    }
                                    if (!processedDatasets.has(nbId)) {
                                        datasetQueue.push(nbId);
                                    }
                                });
                            });
                        }
                    } catch (e) {
                        facetWarn('[GLOSSARY-DATA-MAP] Error loading dataset', datasetId, e);
                    }
                }

                await buildOverlayData();
                
            } catch (error) {
                console.error('[GLOSSARY-DATA-MAP] Error loading dataset lineage data:', error);
            }
        }

        // Load multi-node lineage data
        async function loadMultiNodeLineageData(glossaryId) {
            // Multi-node lineage shows entities connected across business
            // Combine dataset and system lineage
            await loadDatasetLineageData(glossaryId);
            await loadSystemLineageData(glossaryId);
        }

        // Build overlay data (datasets and attributes per system)
        async function buildOverlayData() {
            state.overlayDatasets.clear();
            state.overlayAttributes.clear();
            state.linkingAttributes.clear();
            
            // For each directly linked system (orange), collect associated datasets and attributes
            state.directlyLinkedSystems.forEach(systemId => {
                const systemDatasets = new Set();
                const systemAttributes = new Set();
                const systemLinkingAttributes = new Set();
                
                // Collect datasets associated with this glossary in this system
                // Only show datasets that are directly associated with the glossary
                state.directlyLinkedDatasets.forEach(datasetId => {
                    const dataset = state.datasetsData.get(String(datasetId));
                    if (dataset) {
                        const datasetSystemId = String(dataset.systemId || dataset.masterSource || dataset.MasterSource || dataset.system_id);
                        if (datasetSystemId === String(systemId)) {
                            systemDatasets.add(String(datasetId));
                        }
                    }
                });
                
                // Collect attributes based on case type (for "Attributes" overlay – all directly associated)
                if (state.caseType === 'case2') {
                    state.directlyLinkedAttributes.forEach(attrId => {
                        const attr = state.attributesData.get(String(attrId));
                        if (attr) {
                            const attrSystemId = String(attr.systemId || attr.system_id || attr.System_ID);
                            if (attrSystemId === String(systemId)) {
                                systemAttributes.add(String(attrId));
                            }
                        }
                    });
                }
                // linkingAttributes is populated below from datasetRelationships only (attributes that appear in a relationship)
                
                if (systemDatasets.size > 0) {
                    state.overlayDatasets.set(String(systemId), systemDatasets);
                }
                if (systemAttributes.size > 0) {
                    state.overlayAttributes.set(String(systemId), systemAttributes);
                }
            });
            
            // Build linkingAttributes only from attributes that appear in dataset relationships (source/target attribute IDs)
            state.datasetRelationships.forEach(rel => {
                const srcDid = rel.sourceDatasetId ?? rel.sourceDataSetId ?? rel.Source_DatasetID;
                const tgtDid = rel.targetDatasetId ?? rel.targetDataSetId ?? rel.Target_DatasetID;
                const srcAid = rel.sourceAttributeId ?? rel.source_attribute_id ?? rel.Source_AttributeID;
                const tgtAid = rel.targetAttributeId ?? rel.target_attribute_id ?? rel.Target_AttributeID;
                if (srcDid && srcAid != null) {
                    const ds = state.datasetsData.get(String(srcDid));
                    const sysId = ds ? String(ds.systemId || ds.masterSource || ds.MasterSource || ds.system_id || '') : null;
                    if (sysId) {
                        if (!state.linkingAttributes.has(sysId)) state.linkingAttributes.set(sysId, new Set());
                        state.linkingAttributes.get(sysId).add(String(srcAid));
                    }
                }
                if (tgtDid && tgtAid != null) {
                    const ds = state.datasetsData.get(String(tgtDid));
                    const sysId = ds ? String(ds.systemId || ds.masterSource || ds.MasterSource || ds.system_id || '') : null;
                    if (sysId) {
                        if (!state.linkingAttributes.has(sysId)) state.linkingAttributes.set(sysId, new Set());
                        state.linkingAttributes.get(sysId).add(String(tgtAid));
                    }
                }
            });
            
            // For black systems (not directly linked), show attributes directly associated with glossary
            state.systemsData.forEach((system, systemId) => {
                if (!state.directlyLinkedSystems.has(String(systemId))) {
                    const blackSystemAttributes = new Set();
                    
                    // Find attributes in this system that are directly associated with the glossary
                    state.attributesData.forEach((attr, attrId) => {
                        const attrSystemId = String(attr.systemId || attr.system_id || attr.System_ID);
                        if (attrSystemId === String(systemId) && 
                            state.directlyLinkedAttributes.has(String(attrId))) {
                            blackSystemAttributes.add(String(attrId));
                        }
                    });
                    
                    if (blackSystemAttributes.size > 0) {
                        state.overlayAttributes.set(String(systemId), blackSystemAttributes);
                    }
                }
            });
            
            // Load attribute data for overlay
            await loadAttributeDataForOverlay();
        }

        // Load attribute data for overlay
        async function loadAttributeDataForOverlay() {
            // Load attributes for all directly linked datasets
            for (const datasetId of state.directlyLinkedDatasets) {
                try {
                    const datasetData = await window.BUDG_API_SERVICE.getDatasetById(datasetId, null, { silent404: true });
                    const dataset = datasetData?.data || datasetData;
                    if (dataset && dataset.attributes) {
                        dataset.attributes.forEach(attr => {
                            const attrId = String(attr.id || attr.ID || attr.attributeId);
                            if (attrId) {
                                state.attributesData.set(attrId, {
                                    ...attr,
                                    datasetId: String(datasetId),
                                    systemId: String(dataset.systemId || dataset.masterSource || dataset.MasterSource)
                                });
                            }
                        });
                    }
                } catch (e) {
                    facetWarn('[GLOSSARY-DATA-MAP] Error loading attributes for dataset', datasetId, e);
                }
            }
            
            // Also load attributes from directly linked attributes list
            for (const attrId of state.directlyLinkedAttributes) {
                if (!state.attributesData.has(String(attrId))) {
                    try {
                        // Try to get attribute from API if available
                        // For now, we'll rely on the attributes loaded from datasets
                    } catch (e) {
                        // Ignore
                    }
                }
            }
        }

        // Build graph from loaded data
        function buildGraph() {
            const nodes = [];
            const edges = [];
            const edgeMap = new Map();
            
            if (state.mapType === 'system-lineage') {
                // System lineage: show systems (orange = directly linked, black = other)
                state.systemsData.forEach((system, systemId) => {
                    const isDirectlyLinked = state.directlyLinkedSystems.has(String(systemId));
                    const systemName = system.name || system.Name || `System ${systemId}`;
                    const systemRef = system.refNumber || system.RefNumber || system.ref || `SYS-${systemId}`;
                    
                    nodes.push({
                        id: `system_${systemId}`,
                        label: `${systemName}\n${systemRef}`,
                        group: 'system',
                        isCurrent: isDirectlyLinked,
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
                
            } else if (state.mapType === 'dataset-lineage') {
                // Dataset lineage: show datasets (orange = directly linked, black = other)
                state.datasetsData.forEach((dataset, datasetId) => {
                    const isDirectlyLinked = state.directlyLinkedDatasets.has(String(datasetId));
                    const datasetName = dataset.name || dataset.primaryName || dataset.PrimaryName || `Dataset ${datasetId}`;
                    const datasetRef = dataset.refNumber || dataset.RefNumber || dataset.ref || `DS-${datasetId}`;
                    
                    nodes.push({
                        id: `dataset_${datasetId}`,
                        label: `${datasetName}\n${datasetRef}`,
                        group: 'dataset',
                        isCurrent: isDirectlyLinked,
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
                    
                    if (sourceId && targetId && state.datasetsData.has(sourceId) && state.datasetsData.has(targetId)) {
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
                
            } else if (state.mapType === 'multi-node-lineage') {
                // Multi-node: show all entities (systems and datasets)
                state.systemsData.forEach((system, systemId) => {
                    const isDirectlyLinked = state.directlyLinkedSystems.has(String(systemId));
                    const systemName = system.name || system.Name || `System ${systemId}`;
                    const systemRef = system.refNumber || system.RefNumber || system.ref || `SYS-${systemId}`;
                    
                    nodes.push({
                        id: `system_${systemId}`,
                        label: `${systemName}\n${systemRef}`,
                        group: 'system',
                        isCurrent: isDirectlyLinked,
                        isLocked: state.inaccessibleSystems.has(String(systemId)),
                        meta: {
                            systemId: String(systemId),
                            systemName: systemName,
                            refNumber: systemRef,
                            type: 'system'
                        }
                    });
                });
                
                state.datasetsData.forEach((dataset, datasetId) => {
                    const isDirectlyLinked = state.directlyLinkedDatasets.has(String(datasetId));
                    const datasetName = dataset.name || dataset.primaryName || dataset.PrimaryName || `Dataset ${datasetId}`;
                    const datasetRef = dataset.refNumber || dataset.RefNumber || dataset.ref || `DS-${datasetId}`;
                    
                    nodes.push({
                        id: `dataset_${datasetId}`,
                        label: `${datasetName}\n${datasetRef}`,
                        group: 'dataset',
                        isCurrent: isDirectlyLinked,
                        meta: {
                            datasetId: String(datasetId),
                            datasetName: datasetName,
                            refNumber: datasetRef,
                            systemId: String(dataset.systemId || dataset.masterSource || dataset.MasterSource),
                            type: 'dataset'
                        }
                    });
                });
                
                // Add edges from interfaces, data flow, and dataset relationships
                state.interfacesData.forEach(iface => {
                    const fromId = String(iface.fromId || iface.sourceSystemId);
                    const toId = String(iface.toId || iface.targetSystemId);
                    
                    if (fromId && toId && state.systemsData.has(fromId) && state.systemsData.has(toId)) {
                        const edgeKey = `sys_${fromId}_${toId}`;
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
                        const edgeKey = `flow_${fromId}_${toId}`;
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
                
                state.datasetRelationships.forEach(rel => {
                    const sourceId = String(rel.sourceDatasetId);
                    const targetId = String(rel.targetDatasetId);
                    
                    if (sourceId && targetId && state.datasetsData.has(sourceId) && state.datasetsData.has(targetId)) {
                        const edgeKey = `ds_${sourceId}_${targetId}`;
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
            }

            let resultNodes = nodes;
            let resultEdges = edges;
            if (state.hopsCount > 0) {
                const filtered = adapter.applyHopsFilter({ nodes: resultNodes, edges: resultEdges });
                resultNodes = filtered.nodes;
                resultEdges = filtered.edges;
            }
            const { nodes: nodesFiltered, edges: edgesFiltered } = adapter.filterHiddenNodes(resultNodes, resultEdges);
            return { nodes: nodesFiltered, edges: edgesFiltered };
        }

        async function _mapEngineApiLoader(entityId, mapType, filters) {
            state.glossaryId = entityId;
            if (mapType) { state.mapType = mapType; }
            adapter.mergeEngineFilters(filters);

            state.systemsData.clear();
            state.datasetsData.clear();
            state.attributesData.clear();
            state.interfacesData = [];
            state.dataFlowData = [];
            state.datasetRelationships = [];
            state.connectedSystems.clear();
            state.directlyLinkedSystems.clear();
            state.directlyLinkedDatasets.clear();
            state.directlyLinkedAttributes.clear();
            state.inaccessibleSystems.clear();
            state.missingSystems.clear();
            state.overlayDatasets.clear();
            state.overlayAttributes.clear();
            state.linkingAttributes.clear();

            _cachedGraph = null;

            const glossaryResponse = await window.BUDG_API_SERVICE.getGlossaryById(entityId);
            state.glossaryData = glossaryResponse?.data || glossaryResponse;

            await determineCaseType(entityId);

            if (state.mapType === 'system-lineage') {
                await loadSystemLineageData(entityId);
            } else if (state.mapType === 'dataset-lineage') {
                await loadDatasetLineageData(entityId);
            } else if (state.mapType === 'multi-node-lineage') {
                await loadMultiNodeLineageData(entityId);
            }

            return {
                entity: state.glossaryData,
                _state: state
            };
        }

        function _getOrBuildGraph() {
            if (!_cachedGraph) { _cachedGraph = buildGraph(); }
            return _cachedGraph;
        }

        function _mapEngineNodeBuilder(data) { return _getOrBuildGraph().nodes; }
        function _mapEngineEdgeBuilder(data) { return _getOrBuildGraph().edges; }

        return {
            loadMapData: loadMapData,
            buildGraph: buildGraph,
            invalidateGraphCache: invalidateGraphCache,
            _mapEngineApiLoader: _mapEngineApiLoader,
            _mapEngineNodeBuilder: _mapEngineNodeBuilder,
            _mapEngineEdgeBuilder: _mapEngineEdgeBuilder
        };
    };
})();