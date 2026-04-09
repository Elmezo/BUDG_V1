/**
 * project-data-facet-graph.js - data load and graph builders for project data map.
 * Load after map-graph-utils.js.
 */
(function () {
    'use strict';

    window.createProjectDataFacetGraphPipeline = function (ctx) {
        var state = ctx.state;
        var adapter = ctx.adapter;
        var renderNetwork = ctx.renderNetwork;
        var showLoading = ctx.showLoading;
        var hideLoading = ctx.hideLoading;
        var showPlaceholder = ctx.showPlaceholder;
        var logPrefix = ctx.logPrefix || '[ProjectDataMap]';
        var _pal = window.MapRenderUtils.MAP_PALETTE;
        var ORANGE = _pal.orange || '#f97316';
        var ORANGE_BORDER = _pal.orangeBorder || '#ea580c';
        var GREY = _pal.grey || '#6b7280';
        var GREY_BORDER = _pal.greyBorder || '#4b5563';
        var EDGE_LINE = _pal.edgeLine || '#64748b';
        var EDGE_DASHED = _pal.edgeDashed || '#94a3b8';
        var _cachedGraph = null;

        function invalidateGraphCache() {
            _cachedGraph = null;
        }

        async function fetchSystemByIdSafe(systemId) {
                try {
                    const r = await fetch('/api/system/' + encodeURIComponent(systemId), { credentials: 'include', headers: { 'Content-Type': 'application/json' } });
                    if (r.status === 403) {
                        state.inaccessibleSystems.add(String(systemId));
                        return { name: 'System ' + systemId };
                    }
                    if (!r.ok) return null;
                    const data = await r.json();
                    return data?.data || data || null;
                } catch (e) {
                    return null;
                }
            }

            /** Load impact system IDs from project Impact > Systems. */
            async function loadProjectImpactSystems(projectId) {
                const response = await fetch(`/api/project-impact/${projectId}/systems`, {
                    credentials: 'include',
                    headers: { 'Content-Type': 'application/json' }
                });
                if (!response.ok) return [];
                const data = await response.json();
                const list = Array.isArray(data) ? data : (data?.data || []);
                return list;
            }

            /** Add to impactSystemIds any system IDs that appear in project Impact > Data Sets or Data Attributes (so the map shows systems even if they were only added there, not in Impact > System). */
            async function addImpactSystemIdsFromDatasetsAndAttributes(projectId) {
                try {
                    const [dsRes, attrRes] = await Promise.all([
                        fetch(`/api/project-impact/${projectId}/datasets`, { credentials: 'include', headers: { 'Content-Type': 'application/json' } }),
                        fetch(`/api/project-impact/${projectId}/attributes`, { credentials: 'include', headers: { 'Content-Type': 'application/json' } })
                    ]);
                    const dsData = dsRes.ok ? await dsRes.json() : [];
                    const attrData = attrRes.ok ? await attrRes.json() : [];
                    const dsList = Array.isArray(dsData) ? dsData : (dsData?.data || []);
                    const attrList = Array.isArray(attrData) ? attrData : (attrData?.data || []);
                    dsList.forEach(r => {
                        const sid = r.systemId != null ? String(r.systemId) : (r.system_id != null ? String(r.system_id) : null);
                        if (sid) state.impactSystemIds.add(sid);
                    });
                    attrList.forEach(r => {
                        const sid = r.systemId != null ? String(r.systemId) : (r.system_id != null ? String(r.system_id) : null);
                        if (sid) state.impactSystemIds.add(sid);
                    });
                } catch (e) { console.warn('[ProjectDataMap] addImpactSystemIdsFromDatasetsAndAttributes', e); }
            }

            /** Load project Impact > Datasets and Attributes for overlay (impact systems only). */
            async function loadProjectImpactDatasetsAndAttributes(projectId) {
                state.impactSystemIdToDatasets.clear();
                state.impactSystemIdToAttributes.clear();
                try {
                    const [dsRes, attrRes] = await Promise.all([
                        fetch(`/api/project-impact/${projectId}/datasets`, { credentials: 'include', headers: { 'Content-Type': 'application/json' } }),
                        fetch(`/api/project-impact/${projectId}/attributes`, { credentials: 'include', headers: { 'Content-Type': 'application/json' } })
                    ]);
                    const dsData = dsRes.ok ? await dsRes.json() : [];
                    const attrData = attrRes.ok ? await attrRes.json() : [];
                    const dsList = Array.isArray(dsData) ? dsData : (dsData?.data || []);
                    const attrList = Array.isArray(attrData) ? attrData : (attrData?.data || []);
                    dsList.forEach(r => {
                        const sid = r.systemId != null ? String(r.systemId) : (r.system_id != null ? String(r.system_id) : null);
                        if (!sid || !state.impactSystemIds.has(sid)) return;
                        const id = r.datasetId ?? r.dataset_id ?? r.id;
                        const name = r.datasetName ?? r.dataset_name ?? r.name ?? ('Dataset ' + id);
                        const ref = r.datasetRefNumber ?? r.dataset_ref_number ?? r.refNumber ?? r.ref ?? '';
                        if (!state.impactSystemIdToDatasets.has(sid)) state.impactSystemIdToDatasets.set(sid, []);
                        const arr = state.impactSystemIdToDatasets.get(sid);
                        if (!arr.some(x => String(x.id) === String(id))) arr.push({ id, name, ref });
                    });
                    attrList.forEach(r => {
                        const sid = r.systemId != null ? String(r.systemId) : (r.system_id != null ? String(r.system_id) : null);
                        if (!sid || !state.impactSystemIds.has(sid)) return;
                        const id = r.attributeId ?? r.attribute_id ?? r.id;
                        const name = r.attributeName ?? r.attribute_name ?? r.name ?? ('Attribute ' + id);
                        const ref = r.attributeRefNumber ?? r.attribute_ref_number ?? r.refNumber ?? r.ref ?? '';
                        if (!state.impactSystemIdToAttributes.has(sid)) state.impactSystemIdToAttributes.set(sid, []);
                        const arr = state.impactSystemIdToAttributes.get(sid);
                        if (!arr.some(x => String(x.id) === String(id))) arr.push({ id, name, ref });
                    });
                } catch (e) { console.warn('[ProjectDataMap] loadProjectImpactDatasetsAndAttributes', e); }
            }

            /** For solid-line interfaces only, fetch interface data-within and build interfaceDatasetsBySystem, interfaceAttributesBySystem; also populate attributeIdsWithLinks. */
            async function loadInterfaceDataForSolidEdges() {
                state.interfaceDatasetsBySystem.clear();
                state.interfaceAttributesBySystem.clear();
                state.attributeIdsWithLinks.clear();
                const api = window.BUDG_API_SERVICE;
                if (!api || typeof api.getInterfaceDataWithin !== 'function') return;
                const solidInterfaces = state.interfacesData.filter(iface => {
                    const n = iface.dataAttributes ?? iface.data_attributes ?? 0;
                    return n > 0;
                });
                for (const iface of solidInterfaces) {
                    const fromId = String(iface.fromId ?? iface.sourceSystemId ?? '');
                    const toId = String(iface.toId ?? iface.targetSystemId ?? '');
                    if (!fromId || !toId) continue;
                    try {
                        const res = await api.getInterfaceDataWithin(iface.id);
                        const rows = Array.isArray(res) ? res : (res?.data || []);
                        rows.forEach(rel => {
                            if (fromId) {
                                const did = rel.sourceDatasetId ?? rel.source_dataset_id;
                                const dname = rel.sourceDataSet ?? rel.source_data_set;
                                const dref = rel.sourceRef ?? rel.source_ref ?? '';
                                if (did != null) {
                                    if (!state.interfaceDatasetsBySystem.has(fromId)) state.interfaceDatasetsBySystem.set(fromId, []);
                                    const arr = state.interfaceDatasetsBySystem.get(fromId);
                                    if (!arr.some(x => String(x.id) === String(did))) arr.push({ id: did, name: dname || ('Dataset ' + did), ref: dref });
                                }
                                const aid = rel.sourceAttributeId ?? rel.source_attribute_id;
                                const aname = rel.sourceAttribute ?? rel.source_attribute;
                                const aref = rel.sourceRef ?? rel.source_ref ?? '';
                                if (aid != null) {
                                    state.attributeIdsWithLinks.add(String(aid));
                                    if (!state.interfaceAttributesBySystem.has(fromId)) state.interfaceAttributesBySystem.set(fromId, []);
                                    const arr = state.interfaceAttributesBySystem.get(fromId);
                                    if (!arr.some(x => String(x.id) === String(aid))) arr.push({ id: aid, name: aname || ('Attribute ' + aid), ref: aref });
                                }
                            }
                            if (toId) {
                                const did = rel.targetDatasetId ?? rel.target_dataset_id;
                                const dname = rel.targetDataSet ?? rel.target_data_set;
                                const dref = rel.targetRef ?? rel.target_ref ?? '';
                                if (did != null) {
                                    if (!state.interfaceDatasetsBySystem.has(toId)) state.interfaceDatasetsBySystem.set(toId, []);
                                    const arr = state.interfaceDatasetsBySystem.get(toId);
                                    if (!arr.some(x => String(x.id) === String(did))) arr.push({ id: did, name: dname || ('Dataset ' + did), ref: dref });
                                }
                                const aid = rel.targetAttributeId ?? rel.target_attribute_id;
                                const aname = rel.targetAttribute ?? rel.target_attribute;
                                const aref = rel.targetRef ?? rel.target_ref ?? '';
                                if (aid != null) {
                                    state.attributeIdsWithLinks.add(String(aid));
                                    if (!state.interfaceAttributesBySystem.has(toId)) state.interfaceAttributesBySystem.set(toId, []);
                                    const arr = state.interfaceAttributesBySystem.get(toId);
                                    if (!arr.some(x => String(x.id) === String(aid))) arr.push({ id: aid, name: aname || ('Attribute ' + aid), ref: aref });
                                }
                            }
                        });
                    } catch (e) { console.warn('[ProjectDataMap] getInterfaceDataWithin', iface.id, e); }
                }
            }

            /** Populate attributeIdsWithLinks from dataset-relationships for impact datasets (Impact > Datasets). */
            async function loadAttributeIdsWithLinksFromDatasetRelationships() {
                const impactDatasetIds = new Set();
                state.impactSystemIdToDatasets.forEach(arr => {
                    (arr || []).forEach(d => { if (d && d.id != null) impactDatasetIds.add(String(d.id)); });
                });
                for (const datasetId of impactDatasetIds) {
                    try {
                        const r = await fetch(`/api/dataset-relationships/${datasetId}`, { credentials: 'include', headers: { 'Content-Type': 'application/json' } });
                        if (!r.ok) continue;
                        const data = await r.json();
                        const inbound = data.inbound || [];
                        const outbound = data.outbound || [];
                        [...inbound, ...outbound].forEach(rel => {
                            const sid = rel.Source_AttributeID ?? rel.sourceAttributeId;
                            const tid = rel.Target_AttributeID ?? rel.targetAttributeId;
                            if (sid != null) state.attributeIdsWithLinks.add(String(sid));
                            if (tid != null) state.attributeIdsWithLinks.add(String(tid));
                        });
                    } catch (e) { /* ignore */ }
                }
            }

            async function loadSystemLineageData(projectId) {
                state.impactSystemIds.clear();
                state.linkedSystemIds.clear();
                state.systemsData.clear();
                state.inaccessibleSystems.clear();
                state.interfacesData = [];
                state.dataFlowData = [];

                const impactRelations = await loadProjectImpactSystems(projectId);
                impactRelations.forEach(r => {
                    const sid = r.systemId != null ? String(r.systemId) : (r.system_id != null ? String(r.system_id) : null);
                    if (sid) state.impactSystemIds.add(sid);
                });
                // Also include systems that appear in Impact > Data Sets or Data Attributes (so e.g. HR System1 from Data Attributes shows on the map even if not in Impact > System)
                await addImpactSystemIdsFromDatasetsAndAttributes(projectId);

                if (state.impactSystemIds.size === 0) {
                    return { nodes: [], edges: [] };
                }

                const api = window.BUDG_API_SERVICE;
                if (!api || typeof api.getSystemInterfaces !== 'function' || typeof api.getDataFlowOutsideInterfaces !== 'function') {
                    return buildSystemLineageGraphFromImpactOnly();
                }

                const interfaceIds = new Set();
                const dataFlowKeys = new Set();

                for (const systemId of state.impactSystemIds) {
                    try {
                        const [ifaceRes, flowRes] = await Promise.all([
                            api.getSystemInterfaces(systemId).catch(() => null),
                            api.getDataFlowOutsideInterfaces(systemId).catch(() => null)
                        ]);
                        const ifaceList = Array.isArray(ifaceRes?.data) ? ifaceRes.data : (Array.isArray(ifaceRes) ? ifaceRes : []);
                        const flowList = Array.isArray(flowRes?.data) ? flowRes.data : (Array.isArray(flowRes) ? flowRes : []);
                        ifaceList.forEach(iface => {
                            if (iface?.id != null && !interfaceIds.has(String(iface.id))) {
                                interfaceIds.add(String(iface.id));
                                state.interfacesData.push(iface);
                            }
                        });
                        flowList.forEach(flow => {
                            const key = (flow.fromId || flow.sourceSystemId) + '|' + (flow.toId || flow.targetSystemId) + '|' + (flow.interfaceId || flow.interface_id || '');
                            if (!dataFlowKeys.has(key)) {
                                dataFlowKeys.add(key);
                                state.dataFlowData.push(flow);
                            }
                        });
                        if (!state.systemsData.has(systemId)) {
                            const data = await fetchSystemByIdSafe(systemId);
                            if (data) state.systemsData.set(systemId, data);
                            else state.impactSystemIds.delete(systemId);  // deleted system: don't show on map
                        }
                    } catch (e) { /* ignore */ }
                }

                // Project Facet: only 1-hop. Linked = directly connected to an impact system; do NOT add 2-hop.
                state.interfacesData.forEach(iface => {
                    const fromId = String(iface.fromId || iface.sourceSystemId || '');
                    const toId = String(iface.toId || iface.targetSystemId || '');
                    if (fromId && state.impactSystemIds.has(fromId) && !state.impactSystemIds.has(toId)) state.linkedSystemIds.add(toId);
                    if (toId && state.impactSystemIds.has(toId) && !state.impactSystemIds.has(fromId)) state.linkedSystemIds.add(fromId);
                });
                state.dataFlowData.forEach(flow => {
                    const fromId = String(flow.fromId || flow.sourceSystemId || '');
                    const toId = String(flow.toId || flow.targetSystemId || '');
                    if (fromId && state.impactSystemIds.has(fromId) && !state.impactSystemIds.has(toId)) state.linkedSystemIds.add(toId);
                    if (toId && state.impactSystemIds.has(toId) && !state.impactSystemIds.has(fromId)) state.linkedSystemIds.add(fromId);
                });

                // Overlay: Impact datasets/attributes and interface data (solid edges only)
                await loadProjectImpactDatasetsAndAttributes(projectId);
                await loadInterfaceDataForSolidEdges();
                await loadAttributeIdsWithLinksFromDatasetRelationships();

                // Systems with at least one solid edge: only these show datasets/attributes in overlay (systems with only dashed edges show nothing)
                state.systemIdsWithSolidEdge.clear();
                state.interfacesData.forEach(iface => {
                    const n = iface.dataAttributes ?? iface.data_attributes ?? 0;
                    if (n > 0) {
                        const fromId = String(iface.fromId ?? iface.sourceSystemId ?? '');
                        const toId = String(iface.toId ?? iface.targetSystemId ?? '');
                        if (fromId) state.systemIdsWithSolidEdge.add(fromId);
                        if (toId) state.systemIdsWithSolidEdge.add(toId);
                    }
                });

                // Fetch names for linked systems we don't have yet (use safe fetch so 404 does not log or throw). Deleted systems are not shown.
                const linkedToRemove = [];
                for (const systemId of state.linkedSystemIds) {
                    if (state.systemsData.has(systemId)) continue;
                    const data = await fetchSystemByIdSafe(systemId);
                    if (data) state.systemsData.set(systemId, data);
                    else linkedToRemove.push(systemId);
                }
                linkedToRemove.forEach(id => state.linkedSystemIds.delete(id));

                return buildSystemLineageGraph();
            }

            function buildSystemLineageGraphFromImpactOnly() {
                const nodes = [];
                const hidden = state.hiddenNodes;
                state.impactSystemIds.forEach(systemId => {
                    if (hidden.has(String(systemId))) return;
                    const sys = state.systemsData.get(systemId);
                    const name = sys?.name || sys?.primaryName || sys?.primaryname || ('System ' + systemId);
                    const isLocked = state.inaccessibleSystems.has(String(systemId));
                    nodes.push({
                        id: systemId,
                        label: name,
                        isImpact: true,
                        isLocked: isLocked,
                        nodeColor: isLocked ? GREY : ORANGE,
                        borderColor: isLocked ? GREY_BORDER : ORANGE_BORDER,
                        backgroundImage: adapter.getSystemIcon(true)
                    });
                });
                return { nodes, edges: [] };
            }

            function buildSystemLineageGraph() {
                const nodesMap = new Map();
                const edges = [];
                const edgeMap = new Set();

                const addNode = (systemId, label, isImpact) => {
                    const id = String(systemId);
                    if (nodesMap.has(id)) return;
                    const isOrange = state.impactSystemIds.has(id);
                    const isLocked = state.inaccessibleSystems.has(id);
                    nodesMap.set(id, {
                        id: id,
                        label: label || ('System ' + id),
                        isImpact: isOrange,
                        isLocked: isLocked,
                        nodeColor: isLocked ? GREY : (isOrange ? ORANGE : GREY),
                        borderColor: isLocked ? GREY_BORDER : (isOrange ? ORANGE_BORDER : GREY_BORDER),
                        backgroundImage: adapter.getSystemIcon(isOrange)
                    });
                };

                state.impactSystemIds.forEach(systemId => {
                    const sys = state.systemsData.get(systemId);
                    const name = sys?.name || sys?.primaryName || sys?.primaryname || ('System ' + systemId);
                    addNode(systemId, name, true);
                });
                state.linkedSystemIds.forEach(systemId => {
                    if (!state.systemsData.has(systemId)) {
                        state.systemsData.set(systemId, { name: 'System ' + systemId });
                    }
                    const sys = state.systemsData.get(systemId);
                    const name = sys?.name || sys?.primaryName || sys?.primaryname || ('System ' + systemId);
                    addNode(systemId, name, false);
                });

                // Only draw edges between nodes on the map (impact + 1-hop). Solid = interface has attributes; dashed = no attributes.
                state.interfacesData.forEach(iface => {
                    const fromId = String(iface.fromId || iface.sourceSystemId || '');
                    const toId = String(iface.toId || iface.targetSystemId || '');
                    if (!fromId || !toId || !nodesMap.has(fromId) || !nodesMap.has(toId)) return;
                    const key = fromId + '->' + toId + '-i-' + (iface.id || '');
                    if (edgeMap.has(key)) return;
                    edgeMap.add(key);
                    const dataAttributes = iface.dataAttributes ?? iface.data_attributes ?? 0;
                    edges.push({
                        id: key,
                        from: fromId,
                        to: toId,
                        dashes: dataAttributes === 0  // solid if attributes specified for interface, dashed otherwise
                    });
                });
                state.dataFlowData.forEach(flow => {
                    const fromId = String(flow.fromId || flow.sourceSystemId || '');
                    const toId = String(flow.toId || flow.targetSystemId || '');
                    if (!fromId || !toId || !nodesMap.has(fromId) || !nodesMap.has(toId)) return;
                    const key = fromId + '->' + toId + '-f-' + (flow.interfaceId || flow.interface_id || '');
                    if (edgeMap.has(key)) return;
                    edgeMap.add(key);
                    edges.push({ id: key, from: fromId, to: toId, dashes: false });
                });

                let resultNodes = Array.from(nodesMap.values());
                let resultEdges = edges;
                if (state.hopsCount > 0) {
                    const filtered = adapter.applyHopsFilter({ nodes: resultNodes, edges: resultEdges }, 'isImpact');
                    resultNodes = filtered.nodes;
                    resultEdges = filtered.edges;
                }
                const { nodes, edges: edgesFiltered } = adapter.filterHiddenNodes(resultNodes, resultEdges);
                return { nodes, edges: edgesFiltered };
            }
        async function loadMapData() {
            var projectId = state.projectId;
            if (!projectId) return;
            showLoading();
            try {
                var graph = await loadSystemLineageData(projectId);
                renderNetwork(graph);
            } catch (e) {
                console.error(logPrefix + ' Error:', e);
                showPlaceholder('Error loading data map.');
            } finally {
                hideLoading();
            }
        }

        async function _mapEngineApiLoader(entityId, mapType, filters) {
            state.projectId = entityId;
            adapter.mergeEngineFilters(filters);
            _cachedGraph = null;
            _cachedGraph = await loadSystemLineageData(entityId);
            return { entity: null, _state: state };
        }

        function _mapEngineNodeBuilder(data) { return _cachedGraph ? _cachedGraph.nodes : []; }
        function _mapEngineEdgeBuilder(data) { return _cachedGraph ? _cachedGraph.edges : []; }

        return {
            loadMapData: loadMapData,
            loadSystemLineageData: loadSystemLineageData,
            fetchSystemByIdSafe: fetchSystemByIdSafe,
            loadProjectImpactSystems: loadProjectImpactSystems,
            addImpactSystemIdsFromDatasetsAndAttributes: addImpactSystemIdsFromDatasetsAndAttributes,
            loadProjectImpactDatasetsAndAttributes: loadProjectImpactDatasetsAndAttributes,
            loadInterfaceDataForSolidEdges: loadInterfaceDataForSolidEdges,
            loadAttributeIdsWithLinksFromDatasetRelationships: loadAttributeIdsWithLinksFromDatasetRelationships,
            buildSystemLineageGraph: buildSystemLineageGraph,
            buildSystemLineageGraphFromImpactOnly: buildSystemLineageGraphFromImpactOnly,
            invalidateGraphCache: invalidateGraphCache,
            _mapEngineApiLoader: _mapEngineApiLoader,
            _mapEngineNodeBuilder: _mapEngineNodeBuilder,
            _mapEngineEdgeBuilder: _mapEngineEdgeBuilder
        };
    };
})();