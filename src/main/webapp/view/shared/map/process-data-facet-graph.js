/**
 * process-data-facet-graph.js - data load and graph builders for process data map.
 * Load after map-graph-utils.js.
 */
(function () {
    'use strict';

    window.createProcessDataFacetGraphPipeline = function (ctx) {
        var state = ctx.state;
        var adapter = ctx.adapter;
        var renderNetwork = ctx.renderNetwork;
        var showLoading = ctx.showLoading;
        var hideLoading = ctx.hideLoading;
        var showPlaceholder = ctx.showPlaceholder;
        var logPrefix = ctx.logPrefix || '[ProcessDataMap]';
        var _pal = window.MapRenderUtils.MAP_PALETTE;
        var ORANGE = _pal.orange || '#f97316';
        var ORANGE_BORDER = _pal.orangeBorder || '#ea580c';
        var BLACK = _pal.black || '#1f2937';
        var BLACK_BORDER = _pal.blackBorder || '#374151';
        var EDGE_LINE = _pal.edgeLine || '#64748b';
        var EDGE_DASHED = _pal.edgeDashed || '#94a3b8';
        var _cachedGraph = null;

        function invalidateGraphCache() {
            _cachedGraph = null;
        }

        /** Dataset icon: fa-database style (cylinder). */
        function getDatasetIcon() {
                const iconColor = '#ffffff';
                const svg = `<svg width="40" height="40" viewBox="0 0 40 40" xmlns="http://www.w3.org/2000/svg">
                    <ellipse cx="20" cy="10" rx="12" ry="4" fill="${iconColor}" opacity="0.95"/>
                    <path d="M8 10 v20 q0 4 12 4 q12 0 12 -4 v-20 q0 -4 -12 -4 q-12 0 -12 4 z" fill="${iconColor}" opacity="0.9"/>
                    <ellipse cx="20" cy="30" rx="12" ry="4" fill="${iconColor}" opacity="0.85"/>
                </svg>`;
                return 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg);
            }
            async function loadProcessDatasetsAndAttributes(processId) {
                const [datasetsRes, attributesRes] = await Promise.all([
                    fetch(`/api/process-data/${processId}/datasets`, { credentials: 'include' }).then(r => r.ok ? r.json() : []).catch(() => []),
                    fetch(`/api/process-data/${processId}/attributes`, { credentials: 'include' }).then(r => r.ok ? r.json() : []).catch(() => [])
                ]);
                const datasets = Array.isArray(datasetsRes) ? datasetsRes : (datasetsRes?.data || []);
                const attributes = Array.isArray(attributesRes) ? attributesRes : (attributesRes?.data || []);
                return { datasets, attributes };
            }

            async function loadSystemLineageData(processId) {
                state.impactSystemIdsFromDatasets.clear();
                state.impactSystemIdsFromAttributes.clear();
                state.impactSystemIds.clear();
                state.linkedSystemIds.clear();
                state.systemsData.clear();
                state.inaccessibleSystems.clear();
                state.interfacesData = [];
                state.dataFlowData = [];

                const { datasets, attributes } = await loadProcessDatasetsAndAttributes(processId);

                datasets.forEach(d => {
                    const sid = d.systemId != null ? String(d.systemId) : (d.system_id != null ? String(d.system_id) : null);
                    if (sid) state.impactSystemIdsFromDatasets.add(sid);
                });
                attributes.forEach(a => {
                    const sid = a.systemId != null ? String(a.systemId) : (a.system_id != null ? String(a.system_id) : null);
                    if (sid) state.impactSystemIdsFromAttributes.add(sid);
                });

                state.impactSystemIds = new Set([
                    ...state.impactSystemIdsFromDatasets,
                    ...state.impactSystemIdsFromAttributes
                ]);

                state.impactSystemIdToDatasetIds.clear();
                state.impactSystemIdToDatasets.clear();
                state.impactSystemIdToAttributeIds.clear();
                datasets.forEach(d => {
                    const sid = d.systemId != null ? String(d.systemId) : (d.system_id != null ? String(d.system_id) : null);
                    const did = d.datasetId != null ? String(d.datasetId) : (d.dataset_id != null ? String(d.dataset_id) : null);
                    if (sid && did) {
                        if (!state.impactSystemIdToDatasetIds.has(sid)) state.impactSystemIdToDatasetIds.set(sid, []);
                        state.impactSystemIdToDatasetIds.get(sid).push(did);
                        if (!state.impactSystemIdToDatasets.has(sid)) state.impactSystemIdToDatasets.set(sid, []);
                        state.impactSystemIdToDatasets.get(sid).push({
                            id: did,
                            name: d.datasetName || d.dataset_name || d.name || ('Dataset ' + did),
                            ref: d.datasetRefNumber || d.dataset_ref_number || d.refNumber || '',
                            glossaryId: d.glossaryId ?? d.glossary_id,
                            glossaryName: d.glossaryName || d.glossary_name || ''
                        });
                    }
                });
                attributes.forEach(a => {
                    const sid = a.systemId != null ? String(a.systemId) : (a.system_id != null ? String(a.system_id) : null);
                    const aid = a.attributeId != null ? String(a.attributeId) : (a.attribute_id != null ? String(a.attribute_id) : null);
                    const did = a.datasetId != null ? String(a.datasetId) : (a.dataset_id != null ? String(a.dataset_id) : null);
                    const name = a.attributeName || a.attribute_name || a.name || ('Attribute ' + aid);
                    const glossaryId = a.glossaryId ?? a.glossary_id;
                    const glossaryName = a.glossaryName || a.glossary_name || '';
                    if (sid && aid) {
                        if (!state.impactSystemIdToAttributeIds.has(sid)) state.impactSystemIdToAttributeIds.set(sid, []);
                        state.impactSystemIdToAttributeIds.get(sid).push({ id: aid, name: name, glossaryId: glossaryId, glossaryName: glossaryName });
                    }
                    // Dataset overlay: also include dataset of each attribute (Impact > Attributes subtab) for this system.
                    if (sid && did) {
                        if (!state.impactSystemIdToDatasetIds.has(sid)) state.impactSystemIdToDatasetIds.set(sid, []);
                        const existingIds = state.impactSystemIdToDatasetIds.get(sid);
                        if (existingIds.indexOf(did) === -1) {
                            existingIds.push(did);
                            if (!state.impactSystemIdToDatasets.has(sid)) state.impactSystemIdToDatasets.set(sid, []);
                            state.impactSystemIdToDatasets.get(sid).push({
                                id: did,
                                name: a.datasetName || a.dataset_name || ('Dataset ' + did),
                                ref: a.datasetRefNumber || a.dataset_ref_number || a.refNumber || '',
                                glossaryId: a.datasetGlossaryId ?? a.dataset_glossary_id,
                                glossaryName: a.datasetGlossaryName || a.dataset_glossary_name || ''
                            });
                        }
                    }
                });

                if (state.impactSystemIds.size === 0) {
                    return { nodes: [], edges: [] };
                }

                state.attributeIdsWithLinks.clear();
                state.impactLinkedDatasetIds.clear();
                const impactDatasetIdsForRels = new Set();
                state.impactSystemIdToDatasetIds.forEach((ids) => ids.forEach(did => impactDatasetIdsForRels.add(did)));
                for (const did of impactDatasetIdsForRels) {
                    try {
                        const relResp = await fetch(`/api/dataset-relationships/${did}`, { credentials: 'include', headers: { 'Content-Type': 'application/json' } });
                        if (!relResp.ok) continue;
                        const relData = await relResp.json();
                        (relData.inbound || []).concat(relData.outbound || []).forEach(rel => {
                            const sid = rel.Source_AttributeID ?? rel.sourceAttributeId;
                            const tid = rel.Target_AttributeID ?? rel.targetAttributeId;
                            const srcDid = rel.sourceDatasetId ?? rel.targetDatasetId;
                            const tgtDid = rel.targetDatasetId ?? rel.sourceDatasetId;
                            if (sid != null) state.attributeIdsWithLinks.add(String(sid));
                            if (tid != null) state.attributeIdsWithLinks.add(String(tid));
                            if (srcDid != null && String(srcDid) !== String(did)) state.impactLinkedDatasetIds.add(String(srcDid));
                            if (tgtDid != null && String(tgtDid) !== String(did)) state.impactLinkedDatasetIds.add(String(tgtDid));
                        });
                    } catch (e) { }
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
                            const key = window.MapGraphUtils.buildDataFlowKey(flow, { includeInterfaceId: true });
                            if (key && !dataFlowKeys.has(key)) {
                                dataFlowKeys.add(key);
                                state.dataFlowData.push(flow);
                            }
                        });
                        if (!state.systemsData.has(systemId)) {
                            try {
                                const sys = await api.getSystemById(systemId);
                                const data = sys?.data || sys;
                                if (data) state.systemsData.set(systemId, data);
                            } catch (e) {
                                const status = e?.status ?? e?.statusCode ?? (e?.response?.status);
                                if (status === 403 || (e?.message && (e.message.includes('403') || e.message.toLowerCase().includes('forbidden')))) {
                                    state.inaccessibleSystems.add(String(systemId));
                                    state.systemsData.set(systemId, { name: 'System ' + systemId });
                                }
                            }
                        }
                    } catch (e) { /* ignore */ }
                }

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

                for (const systemId of state.linkedSystemIds) {
                    if (state.systemsData.has(systemId)) continue;
                    try {
                        const sys = await api.getSystemById(systemId);
                        const data = sys?.data || sys;
                        if (data) state.systemsData.set(systemId, data);
                        else state.systemsData.set(systemId, { name: 'System ' + systemId });
                    } catch (e) {
                        const status = e?.status ?? e?.statusCode ?? (e?.response?.status);
                        if (status === 403 || (e?.message && (e.message.includes('403') || e.message.toLowerCase().includes('forbidden')))) {
                            state.inaccessibleSystems.add(String(systemId));
                        }
                        state.systemsData.set(systemId, { name: 'System ' + systemId });
                    }
                }

                return buildSystemLineageGraph();
            }

            function buildSystemLineageGraphFromImpactOnly() {
                const nodes = [];
                const edges = [];
                state.impactSystemIds.forEach(systemId => {
                    const sys = state.systemsData.get(systemId);
                    const name = sys?.name || sys?.primaryName || sys?.primaryname || ('System ' + systemId);
                    const isLocked = state.inaccessibleSystems.has(String(systemId));
                    let meta = { classification: '', type: '', lifecycle: '' };
                    if (window.MapGraphUtils && typeof window.MapGraphUtils.extractSystemMeta === 'function') {
                        const ex = window.MapGraphUtils.extractSystemMeta(sys || {}, String(systemId));
                        meta = { classification: ex.classification || '', type: ex.type || '', lifecycle: ex.lifecycle || '' };
                    }
                    nodes.push({
                        id: systemId,
                        label: name,
                        isImpact: true,
                        isLocked: isLocked,
                        nodeColor: isLocked ? BLACK : ORANGE,
                        borderColor: isLocked ? BLACK_BORDER : ORANGE_BORDER,
                        backgroundImage: adapter.getSystemIcon(true),
                        meta: meta
                    });
                });
                return { nodes, edges };
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
                    const sys = state.systemsData.get(id) || {};
                    let meta = { classification: '', type: '', lifecycle: '' };
                    if (window.MapGraphUtils && typeof window.MapGraphUtils.extractSystemMeta === 'function') {
                        const ex = window.MapGraphUtils.extractSystemMeta(sys, id);
                        meta = {
                            classification: ex.classification || '',
                            type: ex.type || '',
                            lifecycle: ex.lifecycle || ''
                        };
                    }
                    nodesMap.set(id, {
                        id: id,
                        label: label || ('System ' + id),
                        isImpact: isOrange,
                        isLocked: isLocked,
                        nodeColor: isLocked ? BLACK : (isOrange ? ORANGE : BLACK),
                        borderColor: isLocked ? BLACK_BORDER : (isOrange ? ORANGE_BORDER : BLACK_BORDER),
                        backgroundImage: adapter.getSystemIcon(isOrange),
                        meta: meta
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

                const linkFilters = state.filters || { systemInterfaces: true, dataAttributeLinks: true };
                state.interfacesData.forEach(iface => {
                    const fromId = String(iface.fromId || iface.sourceSystemId || '');
                    const toId = String(iface.toId || iface.targetSystemId || '');
                    if (!fromId || !toId || !nodesMap.has(fromId) || !nodesMap.has(toId)) return;
                    const dataAttributes = iface.dataAttributes ?? iface.data_attributes ?? 0;
                    const isInterfaceLineage = dataAttributes === 0;
                    const isAttributeLineage = dataAttributes > 0;
                    if (isInterfaceLineage && !linkFilters.systemInterfaces) return;
                    if (isAttributeLineage && !linkFilters.dataAttributeLinks) return;
                    const key = fromId + '->' + toId + '-i-' + (iface.id || '');
                    if (edgeMap.has(key)) return;
                    edgeMap.add(key);
                    edges.push({
                        id: key,
                        from: fromId,
                        to: toId,
                        dashes: dataAttributes === 0
                    });
                });
                state.dataFlowData.forEach(flow => {
                    if (!linkFilters.dataAttributeLinks) return;
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
            /** Only include linked dataset Z if the relationship involves an impact attribute (i not j). */
            function relationshipInvolvesImpactAttribute(rel) {
                const sourceAttrId = String(rel.Source_AttributeID ?? rel.sourceAttributeId ?? '');
                const targetAttrId = String(rel.Target_AttributeID ?? rel.targetAttributeId ?? '');
                return (sourceAttrId && state.impactAttributeIds.has(sourceAttrId)) ||
                       (targetAttrId && state.impactAttributeIds.has(targetAttrId));
            }

            async function loadDatasetLineageData(processId) {
                const { datasets, attributes } = await loadProcessDatasetsAndAttributes(processId);
                state.impactDatasetIds.clear();
                state.impactDatasetIdsFromAttributes.clear();
                state.impactAttributeIds.clear();
                state.impactDatasetIdToImpactAttributeIds.clear();
                state.linkedDatasets.clear();
                state.datasetRelationships = [];

                datasets.forEach(d => {
                    const did = d.datasetId != null ? String(d.datasetId) : (d.dataset_id != null ? String(d.dataset_id) : null);
                    if (did) state.impactDatasetIds.add(did);
                });
                attributes.forEach(a => {
                    const aid = a.attributeId != null ? String(a.attributeId) : (a.attribute_id != null ? String(a.attribute_id) : null);
                    const datasetId = a.datasetId != null ? String(a.datasetId) : (a.dataset_id != null ? String(a.dataset_id) : null);
                    const name = a.attributeName || a.attribute_name || a.name || ('Attribute ' + aid);
                    if (aid) state.impactAttributeIds.add(aid);
                    if (datasetId) {
                        state.impactDatasetIdsFromAttributes.add(datasetId);
                        const glossaryId = a.glossaryId ?? a.glossary_id;
                        const glossaryName = a.glossaryName || a.glossary_name || '';
                        if (!state.impactDatasetIdToImpactAttributeIds.has(datasetId)) state.impactDatasetIdToImpactAttributeIds.set(datasetId, []);
                        state.impactDatasetIdToImpactAttributeIds.get(datasetId).push({ id: aid, name: name, glossaryId: glossaryId, glossaryName: glossaryName });
                    }
                });

                const allImpactDatasetIds = new Set([
                    ...state.impactDatasetIds,
                    ...state.impactDatasetIdsFromAttributes
                ]);
                if (allImpactDatasetIds.size === 0) {
                    return { nodes: [], edges: [] };
                }

                const linkedDatasetIds = new Set(allImpactDatasetIds);
                const allRelationships = [];
                const datasetToSystemMap = new Map();
                const API = window.BUDG_API_SERVICE;

                for (const datasetId of allImpactDatasetIds) {
                    try {
                        const relResp = await fetch(`/api/dataset-relationships/${datasetId}`, {
                            method: 'GET',
                            credentials: 'include',
                            headers: { 'Content-Type': 'application/json' }
                        });
                        if (!relResp.ok) continue;
                        const relData = await relResp.json();
                        const inbound = relData.inbound || [];
                        const outbound = relData.outbound || [];

                        inbound.forEach(rel => {
                            const otherId = rel.sourceDatasetId || rel.targetDatasetId;
                            if (!otherId || otherId === datasetId) return;
                            if (!relationshipInvolvesImpactAttribute(rel)) return;
                            linkedDatasetIds.add(String(otherId));
                            allRelationships.push({
                                sourceDatasetId: String(otherId),
                                targetDatasetId: String(datasetId),
                                sourceAttributeId: rel.Source_AttributeID ?? rel.sourceAttributeId,
                                targetAttributeId: rel.Target_AttributeID ?? rel.targetAttributeId,
                                systemId: rel.systemId
                            });
                            if (rel.systemId) datasetToSystemMap.set(String(otherId), String(rel.systemId));
                        });
                        outbound.forEach(rel => {
                            const otherId = rel.targetDatasetId || rel.sourceDatasetId;
                            if (!otherId || otherId === datasetId) return;
                            if (!relationshipInvolvesImpactAttribute(rel)) return;
                            linkedDatasetIds.add(String(otherId));
                            allRelationships.push({
                                sourceDatasetId: String(datasetId),
                                targetDatasetId: String(otherId),
                                sourceAttributeId: rel.Source_AttributeID ?? rel.sourceAttributeId,
                                targetAttributeId: rel.Target_AttributeID ?? rel.targetAttributeId,
                                systemId: rel.systemId
                            });
                            if (rel.systemId) datasetToSystemMap.set(String(otherId), String(rel.systemId));
                        });
                    } catch (e) {
                        console.warn('[ProcessDataMap] dataset-relationships for', datasetId, e);
                    }
                }

                for (const datasetId of linkedDatasetIds) {
                    const systemId = datasetToSystemMap.get(String(datasetId)) || null;
                    try {
                        const res = await (API && API.getDatasetById ? API.getDatasetById(datasetId, null, { silent404: true }) : fetch(`/api/datasets/${datasetId}`, { credentials: 'include' }).then(r => r.ok ? r.json() : {}).catch(() => ({})));
                        const data = res?.data || res;
                        const sysId = data?.systemId ?? data?.system_id ?? systemId;
                        if (sysId) datasetToSystemMap.set(String(datasetId), String(sysId));
                        state.linkedDatasets.set(String(datasetId), {
                            dataset: data || { id: datasetId, name: 'Dataset ' + datasetId },
                            systemId: String(sysId || systemId || ''),
                            isImpact: allImpactDatasetIds.has(String(datasetId))
                        });
                    } catch (e) {
                        state.linkedDatasets.set(String(datasetId), {
                            dataset: { id: datasetId, name: 'Dataset ' + datasetId },
                            systemId: String(systemId || ''),
                            isImpact: allImpactDatasetIds.has(String(datasetId))
                        });
                    }
                }

                state.datasetLineageSystemNames.clear();
                const uniqueSystemIds = new Set([...datasetToSystemMap.values()].filter(Boolean));
                if (API && typeof API.getSystemById === 'function') {
                    await Promise.all([...uniqueSystemIds].map(async (systemId) => {
                        try {
                            const res = await API.getSystemById(systemId);
                            const s = res?.data || res;
                            if (s) state.datasetLineageSystemNames.set(String(systemId), s.name || s.primaryName || s.primaryname || ('System ' + systemId));
                        } catch (e) { /* ignore */ }
                    }));
                }

                state.datasetRelationships = allRelationships;
                state.attributeIdsWithLinks.clear();
                allRelationships.forEach(rel => {
                    const sid = rel.sourceAttributeId != null ? String(rel.sourceAttributeId) : '';
                    const tid = rel.targetAttributeId != null ? String(rel.targetAttributeId) : '';
                    if (sid) state.attributeIdsWithLinks.add(sid);
                    if (tid) state.attributeIdsWithLinks.add(tid);
                });
                return buildDatasetLineageGraph();
            }

            function buildDatasetLineageGraph() {
                const nodes = [];
                const edges = [];
                const edgeKeys = new Set();
                const systemNames = state.datasetLineageSystemNames;

                state.linkedDatasets.forEach((info, datasetId) => {
                    const dataset = info.dataset || {};
                    const systemId = info.systemId;
                    const isImpact = info.isImpact;
                    const name = dataset.name || dataset.primaryName || dataset.PrimaryName || ('Dataset ' + datasetId);
                    const refNumber = dataset.refNumber || dataset.RefNumber || dataset.ref || '';
                    const systemName = (systemId && systemNames.get(String(systemId))) || (systemId ? ('System ' + systemId) : '');
                    const label = refNumber ? (name + ' (' + refNumber + ')') : name;
                    const typeName = dataset.typeName || dataset.datasetTypeName || dataset.type || '';
                    const lifecycleName = dataset.lifecycleName || dataset.lifecycle || '';
                    nodes.push({
                        id: 'dataset-' + datasetId,
                        label: label,
                        systemName: systemName,
                        isImpact: isImpact,
                        nodeColor: isImpact ? ORANGE : BLACK,
                        borderColor: isImpact ? ORANGE_BORDER : BLACK_BORDER,
                        backgroundImage: getDatasetIcon(),
                        meta: {
                            datasetId: String(datasetId),
                            typeName: String(typeName || ''),
                            lifecycleName: String(lifecycleName || '')
                        }
                    });
                });

                state.datasetRelationships.forEach(rel => {
                    const fromId = 'dataset-' + rel.sourceDatasetId;
                    const toId = 'dataset-' + rel.targetDatasetId;
                    const key = fromId + '->' + toId;
                    if (edgeKeys.has(key)) return;
                    if (!nodes.some(n => n.id === fromId) || !nodes.some(n => n.id === toId)) return;
                    edgeKeys.add(key);
                    edges.push({ id: key, from: fromId, to: toId, dashes: false });
                });

                let resultNodes = nodes;
                let resultEdges = edges;
                if (state.hopsCount > 0) {
                    const filtered = adapter.applyHopsFilter({ nodes: resultNodes, edges: resultEdges }, 'isImpact');
                    resultNodes = filtered.nodes;
                    resultEdges = filtered.edges;
                }
                const { nodes: nodesFiltered, edges: edgesFiltered } = adapter.filterHiddenNodes(resultNodes, resultEdges);
                return { nodes: nodesFiltered, edges: edgesFiltered };
            }
        async function loadMapData() {
            var processId = state.processId;
            if (!processId) return;
            showLoading();
            try {
                var graph;
                if (state.mapType === 'dataset-lineage') {
                    graph = await loadDatasetLineageData(processId);
                } else {
                    graph = await loadSystemLineageData(processId);
                }
                renderNetwork(graph);
            } catch (e) {
                console.error(logPrefix + ' Error:', e);
                showPlaceholder('Error loading data map.');
            } finally {
                hideLoading();
            }
        }

        async function _mapEngineApiLoader(entityId, mapType, filters) {
            state.processId = entityId;
            if (mapType) { state.mapType = mapType; }
            adapter.mergeEngineFilters(filters);
            _cachedGraph = null;
            if (state.mapType === 'dataset-lineage') {
                _cachedGraph = await loadDatasetLineageData(entityId);
            } else {
                _cachedGraph = await loadSystemLineageData(entityId);
            }
            return { entity: null, _state: state };
        }

        function _mapEngineNodeBuilder(data) { return _cachedGraph ? _cachedGraph.nodes : []; }
        function _mapEngineEdgeBuilder(data) { return _cachedGraph ? _cachedGraph.edges : []; }

        return {
            loadMapData: loadMapData,
            loadSystemLineageData: loadSystemLineageData,
            loadDatasetLineageData: loadDatasetLineageData,
            loadProcessDatasetsAndAttributes: loadProcessDatasetsAndAttributes,
            buildSystemLineageGraph: buildSystemLineageGraph,
            buildSystemLineageGraphFromImpactOnly: buildSystemLineageGraphFromImpactOnly,
            buildDatasetLineageGraph: buildDatasetLineageGraph,
            invalidateGraphCache: invalidateGraphCache,
            _mapEngineApiLoader: _mapEngineApiLoader,
            _mapEngineNodeBuilder: _mapEngineNodeBuilder,
            _mapEngineEdgeBuilder: _mapEngineEdgeBuilder
        };
    };
})();