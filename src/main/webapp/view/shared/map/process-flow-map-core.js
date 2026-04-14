/**
 * Shared implementation for Process Context and Process Components Cytoscape maps.
 * Registration: view/process/process-flow-maps.js
 * Requires: cytoscape, dagre, process-map-client.js, map-engine, map-ui,
 * map-render-utils.js, system-lineage-network-interactions.js (createProcessFlowMapNetworkInteractions).
 */

(function() {
    'use strict';

    /**
     * @param {object} vcfg
     * @param {'context'|'components'} vcfg.variant
     * @param {string} vcfg.mapId - e.g. processContextMap | processComponentsMap
     * @param {object} vcfg.state - mutable map state (see facades)
     * @param {string} vcfg.mapEngineKey - process-context | process-components
     * @param {object} vcfg.selectors - loading, details, placeholder, legend, overlay (bracketed selectors, e.g. [data-...])
     * @param {string} vcfg.containerId - outer container element id
     * @param {string} vcfg.canvasId - Cytoscape canvas element id
     * @param {string} [vcfg.wrapperClass] - extra class on map container
     * @param {string} vcfg.overlayColPrefix - e.g. overlayCol_processContextMap_
     * @param {string} vcfg.contextMenuDomId
     * @param {string} vcfg.containerId
     * @param {string} vcfg.canvasId
     * @param {string} vcfg.fullscreenTitle
     * @param {string} vcfg.exportPrefix
     * @param {string} vcfg.parentModule
     * @param {string} vcfg.logPrefix
     */
    function createProcessFlowMapController(vcfg) {
    const MAP_ID = vcfg.mapId;
    const state = vcfg.state;
    const variant = vcfg.variant;
    var networkIx = null;
    const sel = vcfg.selectors;

    function attrSel(s) {
        if (!s || s.length < 2) return s;
        return s[0] === '[' ? s.slice(1, -1) : s;
    }

    /** Lifecycle on the root process (Summary / Components parent) from loaded processData */
    function getRootLifecycleKey() {
        const d = state.processData || {};
        // Process JSON from Gson uses SerializedName: lifecycle_status (see model Process.java)
        const id = d.lifecycle_status != null ? d.lifecycle_status
            : d.lifecycleStatus != null ? d.lifecycleStatus
                : d.Lifecycle_Status_ID != null ? d.Lifecycle_Status_ID
                    : d.lifecycle_status_id != null ? d.lifecycle_status_id
                        : d.processLifecycleId != null ? d.processLifecycleId
                            : d.process_lifecycle_id;
        const name = (d.lifecycleName || d.lifecycle_name || d.lifecycle_status_name || d.LifecycleName || d.lifecyclePrimaryName || '').toString().trim().toLowerCase();
        return { id: id != null && id !== '' ? String(id) : null, name: name || null };
    }

    function coalesceRelLifecycleId(rel, camel, snake) {
        if (!rel) return null;
        const v = rel[camel] != null && rel[camel] !== '' ? rel[camel] : (snake != null ? rel[snake] : null);
        if (v === undefined || v === null || v === '') return null;
        return String(v);
    }

    function lifecycleFromTargetRel(rel) {
        if (!rel) return { id: null, name: null };
        const id = coalesceRelLifecycleId(rel, 'targetProcessLifecycleId', 'target_process_lifecycle_id');
        const name = (rel.targetProcessLifecycleName || rel.target_process_lifecycle_name || '').toString().trim().toLowerCase();
        return { id, name: name || null };
    }

    function lifecycleFromSourceRel(rel) {
        if (!rel) return { id: null, name: null };
        const id = coalesceRelLifecycleId(rel, 'sourceProcessLifecycleId', 'source_process_lifecycle_id');
        const name = (rel.sourceProcessLifecycleName || rel.source_process_lifecycle_name || '').toString().trim().toLowerCase();
        return { id, name: name || null };
    }

    /**
     * When filter is on: keep only processes whose lifecycle matches the root process.
     * If the root has a known lifecycle but the related process has none, exclude it (strict),
     * so the filter visibly narrows the map once API returns lifecycle fields.
     */
    function sameLifecycleAsRoot(other) {
        const root = getRootLifecycleKey();
        if (!root.id && !root.name) return true;
        if (!other || (!other.id && !other.name)) return false;
        if (root.id && other.id && root.id === other.id) return true;
        if (root.name && other.name && root.name === other.name) return true;
        return false;
    }

    function lifecycleMatchesRoot(other) {
        if (!state.filterLifecycle) return true;
        return sameLifecycleAsRoot(other);
    }

    /**
     * Normalize API type to stepType + primaryName for styling.
     * DB: process_type.PrimaryName = "Process" | "Step" | "Control"
     *     process_step_type.PrimaryName = "Common Step" | "Starting Step" | "Ending Step"
     * Returns { stepType: 'process'|'step'|'control', primaryName: 'starting'|'ending'|'common'|undefined }
     */
    function normalizeStepTypeAndPrimary(type, primaryNameFromApi) {
        const t = (type || 'Process').toString().trim();
        const tLower = t.toLowerCase();
        const p = (primaryNameFromApi || '').toString().trim().toLowerCase();
        if (tLower === 'control' || tLower === 'decision') return { stepType: 'control', primaryName: undefined };
        if (tLower === 'start' || tLower === 'starting') return { stepType: 'step', primaryName: 'starting' };
        if (tLower === 'end' || tLower === 'ending') return { stepType: 'step', primaryName: 'ending' };
        if (tLower === 'step') {
            if (p === 'starting' || p === 'starting step') return { stepType: 'step', primaryName: 'starting' };
            if (p === 'ending' || p === 'ending step') return { stepType: 'step', primaryName: 'ending' };
            if (p === 'common' || p === 'common step') return { stepType: 'step', primaryName: 'common' };
            return { stepType: 'step', primaryName: 'common' };
        }
        return { stepType: 'process', primaryName: undefined };
    }

    function getStepIcon(stepType, primaryName) {
        return window.ProcessMapIcons && typeof window.ProcessMapIcons.getProcessStepIcon === 'function'
            ? window.ProcessMapIcons.getProcessStepIcon(stepType, primaryName)
            : window.ProcessMapIcons && window.ProcessMapIcons.createProcessIcon
                ? window.ProcessMapIcons.createProcessIcon()
                : '';
    }

    /** Build node label: name, ref, and "Duration: X Unit" when duration is specified */
    function formatNodeLabel(name, ref, duration, durationTypeName) {
        let label = ref ? `${name}\n${ref}` : name;
        const dur = duration != null && duration !== '' && duration !== undefined;
        const typeName = durationTypeName || '';
        if (dur) {
            label += '\nDuration: ' + String(duration).trim() + (typeName ? ' ' + String(typeName).trim() : '');
        }
        return label;
    }

    function processToNodeData(process, isCurrent) {
        const pid = String(process.id != null ? process.id : process.ID);
        const typeRaw = process.typeName || process.type || process.processTypeName || process.process_type_name || process.stepTypeName || process.step_type_name || process.stepType || 'Process';
        const primaryRaw = process.stepTypeName || process.step_type_name || process.stepTypePrimaryName || process.step_type_primary_name || process.primaryName || '';
        const norm = normalizeStepTypeAndPrimary(typeRaw, primaryRaw);
        const name = process.primaryname || process.primaryName || process.name || process.Name || process.PrimaryName || `Process ${pid}`;
        const ref = process.refnumber || process.refNumber || process.ref || process.Ref_Number || process.RefNumber || '';
        const duration = process.duration;
        const durationTypeName = process.durationTypeName || process.duration_type_name || process.durationType || process.duration_type || '';
        return {
            id: `process_${pid}`,
            label: formatNodeLabel(name, ref, duration, durationTypeName),
            stepType: norm.stepType,
            primaryName: norm.primaryName,
            isCurrent: !!isCurrent,
            processId: pid,
            backgroundImage: getStepIcon(norm.stepType, norm.primaryName)
        };
    }

    function buildGraph() {
        if (variant === 'components') {
            const nodes = [];
            const edges = [];
            const processId = String(state.processId);
            let children = state.children || [];
            if (state.filterLifecycle) {
                const rk = getRootLifecycleKey();
                if (rk.id || rk.name) {
                    children = children.filter(c => {
                        const cid = c.lifecycle_status != null ? c.lifecycle_status
                            : c.lifecycleStatus != null ? c.lifecycleStatus
                                : c.Lifecycle_Status_ID != null ? c.Lifecycle_Status_ID
                                    : c.lifecycle_status_id;
                        const cn = (c.lifecycleName || c.lifecycle_name || '').toString().trim().toLowerCase();
                        return sameLifecycleAsRoot({
                            id: cid != null && cid !== '' ? String(cid) : null,
                            name: cn || null
                        });
                    });
                }
            }
            const childRelations = state.childRelations;
            const childIds = new Set(children.map(c => String(c.id != null ? c.id : c.ID)));

            children.forEach(c => {
                nodes.push(processToNodeData(c, false));
            });

            const edgeIds = new Set();

            childRelations.forEach((rel, childId) => {
                (rel.predecessors || []).forEach(relItem => {
                    const sourceId = String(relItem.sourceProcessId != null ? relItem.sourceProcessId : relItem.sourceprocess_id);
                    if (!sourceId || !childIds.has(sourceId)) return;
                    const targetId = String(childId);
                    const edgeId = `edge_process_${sourceId}_process_${targetId}`;
                    if (!edgeIds.has(edgeId)) {
                        edgeIds.add(edgeId);
                        const condition = relItem.annotations || relItem.condition || relItem.relationTypeName || '';
                        edges.push({
                            id: edgeId,
                            from: `process_${sourceId}`,
                            to: `process_${targetId}`,
                            label: condition ? String(condition).slice(0, 30) : ''
                        });
                    }
                });
                (rel.successors || []).forEach(relItem => {
                    const targetId = String(relItem.targetProcessId != null ? relItem.targetProcessId : relItem.targetprocess_id);
                    if (!targetId || !childIds.has(targetId)) return;
                    const sourceId = String(childId);
                    const edgeId = `edge_process_${sourceId}_process_${targetId}`;
                    if (!edgeIds.has(edgeId)) {
                        edgeIds.add(edgeId);
                        const condition = relItem.annotations || relItem.condition || relItem.relationTypeName || '';
                        edges.push({
                            id: edgeId,
                            from: `process_${sourceId}`,
                            to: `process_${targetId}`,
                            label: condition ? String(condition).slice(0, 30) : ''
                        });
                    }
                });
            });

            const hidden = state.hiddenNodes;
            const nodesFiltered = nodes.filter(n => !hidden.has(n.id));
            const visibleIds = new Set(nodesFiltered.map(n => n.id));
            const edgesFiltered = edges.filter(e => visibleIds.has(e.from) && visibleIds.has(e.to));
            return { nodes: nodesFiltered, edges: edgesFiltered };
        }

        const nodes = [];
        const edges = [];
        const processId = String(state.processId);
        const processData = state.processData || {};
        // Type = process_type.PrimaryName (Process | Step | Control); Step Type = process_step_type.PrimaryName (Common Step | Starting Step | Ending Step)
        const typeRaw = processData.typeName || processData.type || processData.processTypeName || processData.process_type_name || processData.stepTypeName || processData.step_type_name || processData.stepType || 'Process';
        const primaryRaw = processData.stepTypeName || processData.step_type_name || processData.stepTypePrimaryName || processData.step_type_primary_name || processData.primaryName || '';
        const norm = normalizeStepTypeAndPrimary(typeRaw, primaryRaw);
        const name = processData.primaryname || processData.primaryName || processData.name || processData.Name || processData.PrimaryName || `Process ${processId}`;
        const ref = processData.refnumber || processData.refNumber || processData.ref || processData.Ref_Number || processData.RefNumber || '';
        const duration = processData.duration;
        const durationTypeName = processData.durationTypeName || processData.duration_type_name || processData.durationType || processData.duration_type || '';

        nodes.push({
            id: `process_${processId}`,
            label: formatNodeLabel(name, ref, duration, durationTypeName),
            stepType: norm.stepType,
            primaryName: norm.primaryName,
            isCurrent: true,
            processId: processId,
            backgroundImage: getStepIcon(norm.stepType, norm.primaryName)
        });

        const edgeIds = new Set();
        const processIdStr = String(processId);
        let predRels = state.predecessors;
        let succRels = state.successors;
        if (state.filterLifecycle) {
            predRels = predRels.filter(rel => lifecycleMatchesRoot(lifecycleFromTargetRel(rel)));
            succRels = succRels.filter(rel => lifecycleMatchesRoot(lifecycleFromSourceRel(rel)));
        }
        // Index predecessor relations by targetProcessId so we can look up node data for a process that appears as source in another relation
        const relByTarget = new Map();
        predRels.forEach(rel => {
            const src = String(rel.sourceProcessId != null ? rel.sourceProcessId : rel.sourceprocess_id);
            const tgt = String(rel.targetProcessId != null ? rel.targetProcessId : rel.targetprocess_id);
            if (!tgt) return;
            relByTarget.set(tgt, rel);
        });
        function ensureNode(id, preferRel) {
            const nodeId = `process_${id}`;
            if (nodes.find(n => n.id === nodeId)) return nodeId;
            const rel = preferRel || relByTarget.get(id);
            const typeVal = rel ? (rel.targetProcessType || rel.targetprocesstype || 'Process') : 'Process';
            const primaryVal = rel ? (rel.targetProcessStepTypePrimaryName || rel.targetprocesssteptypeprimaryname || rel.primaryName || '') : '';
            const norm = normalizeStepTypeAndPrimary(typeVal, primaryVal);
            const name = rel ? (rel.targetProcessName || rel.targetprocessname || `Process ${id}`) : `Process ${id}`;
            const ref = rel ? (rel.targetProcessRef || rel.targetprocessref || '') : '';
            const duration = rel && (rel.targetProcessDuration != null || rel.targetprocessduration != null) ? (rel.targetProcessDuration != null ? rel.targetProcessDuration : rel.targetprocessduration) : undefined;
            const durationTypeName = rel ? (rel.targetProcessDurationTypeName || rel.targetprocessdurationtypename || rel.targetDurationTypeName || '') : '';
            nodes.push({
                id: nodeId,
                label: formatNodeLabel(name, ref, duration, durationTypeName),
                stepType: norm.stepType,
                primaryName: norm.primaryName,
                isCurrent: id === processIdStr,
                processId: id,
                backgroundImage: getStepIcon(norm.stepType, norm.primaryName)
            });
            return nodeId;
        }
        predRels.forEach(rel => {
            const sourceId = String(rel.sourceProcessId != null ? rel.sourceProcessId : rel.sourceprocess_id);
            const targetId = String(rel.targetProcessId != null ? rel.targetProcessId : rel.targetprocess_id);
            if (!sourceId || !targetId) return;
            // Edge: predecessor (target) -> process that has this predecessor (source)
            const fromNode = ensureNode(targetId, rel);
            const toNode = sourceId === processIdStr ? `process_${processId}` : ensureNode(sourceId, relByTarget.get(sourceId));
            const edgeId = `edge_${fromNode}_${toNode}`;
            if (!edgeIds.has(edgeId)) {
                edgeIds.add(edgeId);
                const condition = rel.annotations || rel.condition || rel.relationTypeName || '';
                edges.push({
                    id: edgeId,
                    from: fromNode,
                    to: toNode,
                    label: condition ? String(condition).slice(0, 30) : ''
                });
            }
        });

        succRels.forEach(rel => {
            const sourceId = String(rel.sourceProcessId != null ? rel.sourceProcessId : rel.sourceprocess_id);
            if (!sourceId) return;
            const typeSucc = rel.sourceProcessType || rel.sourceprocesstype || 'Process';
            const primarySucc = rel.sourceProcessStepTypePrimaryName || rel.sourceprocesssteptypeprimaryname || rel.primaryName || '';
            const normSucc = normalizeStepTypeAndPrimary(typeSucc, primarySucc);
            const nameSucc = rel.sourceProcessName || rel.sourceprocessname || `Process ${sourceId}`;
            const refSucc = rel.sourceProcessRef || rel.sourceprocessref || '';
            const durationSucc = rel.sourceProcessDuration != null ? rel.sourceProcessDuration : rel.sourceprocessduration;
            const durationTypeSucc = rel.sourceProcessDurationTypeName || rel.sourceprocessdurationtypename || rel.sourceDurationTypeName || '';
            const nodeId = `process_${sourceId}`;
            if (!nodes.find(n => n.id === nodeId)) {
                nodes.push({
                    id: nodeId,
                    label: formatNodeLabel(nameSucc, refSucc, durationSucc, durationTypeSucc),
                    stepType: normSucc.stepType,
                    primaryName: normSucc.primaryName,
                    isCurrent: false,
                    processId: sourceId,
                    backgroundImage: getStepIcon(normSucc.stepType, normSucc.primaryName)
                });
            }
            const edgeId = `edge_process_${processId}_${nodeId}`;
            if (!edgeIds.has(edgeId)) {
                edgeIds.add(edgeId);
                const condition = rel.annotations || rel.condition || rel.relationTypeName || '';
                edges.push({
                    id: edgeId,
                    from: `process_${processId}`,
                    to: nodeId,
                    label: condition ? String(condition).slice(0, 30) : ''
                });
            }
        });

        const hidden = state.hiddenNodes;
        const nodesFiltered = nodes.filter(n => !hidden.has(n.id));
        const visibleIds = new Set(nodesFiltered.map(n => n.id));
        const edgesFiltered = edges.filter(e => visibleIds.has(e.from) && visibleIds.has(e.to));
        return { nodes: nodesFiltered, edges: edgesFiltered };
    }

    function buildElements(graph) {
        const elements = [];
        const lineColor = (window.ProcessMapStyles && window.ProcessMapStyles.colors && window.ProcessMapStyles.colors.edgeColor) || '#64748b';
        const currentNodeIds = new Set(graph.nodes.filter(n => n.isCurrent).map(n => n.id));
        graph.nodes.forEach(n => {
            const data = {
                id: n.id,
                label: n.label,
                stepType: n.stepType,
                isCurrent: n.isCurrent,
                processId: n.processId,
                backgroundImage: n.backgroundImage
            };
            if (n.primaryName != null && n.primaryName !== '') {
                data.primaryName = n.primaryName;
            }
            elements.push({ data: data });
        });
        graph.edges.forEach(e => {
            const targetIsCurrent = currentNodeIds.has(e.to);
            const sourceIsCurrent = currentNodeIds.has(e.from);
            const reverseEdge = targetIsCurrent && !sourceIsCurrent;
            const source = reverseEdge ? e.to : e.from;
            const target = reverseEdge ? e.from : e.to;
            elements.push({
                data: {
                    id: e.id,
                    source: source,
                    target: target,
                    label: e.label || '',
                    lineColor: lineColor,
                    reversed: reverseEdge
                },
                classes: reverseEdge ? 'reversed-edge' : ''
            });
        });
        return elements;
    }

    function getLayoutConfig() {
        const children = state.children || [];
        const roots = variant === 'components'
            ? (children.length ? [`process_${children[0].id != null ? children[0].id : children[0].ID}`] : [])
            : [`process_${state.processId}`];
        const sharedStyles = window.SharedMapStyles || window.InterfaceMapStyles;
        const processStyles = window.ProcessMapStyles;
        let config;
        if (processStyles && typeof processStyles.getLayoutConfig === 'function') {
            config = Object.assign({}, processStyles.getLayoutConfig(state.layout));
        } else if (sharedStyles && typeof sharedStyles.getLayoutConfig === 'function') {
            config = Object.assign({}, sharedStyles.getLayoutConfig(state.layout));
        } else {
            config = { name: 'breadthfirst', directed: true, padding: 50, spacingFactor: 1.6, animate: true, animationDuration: 400, nodeDimensionsIncludeLabels: true };
        }
        if (config.name === 'breadthfirst') {
            config.roots = roots.length ? roots : undefined;
        }
        if (config.name === 'dagre') {
            if (!state.dagreRegistered) {
                config = { name: 'breadthfirst', directed: true, padding: 50, spacingFactor: 1.6, animate: true, animationDuration: 400, nodeDimensionsIncludeLabels: true, roots: roots.length ? roots : undefined };
            } else {
                if (state.layoutDirection === 'backward') {
                    config.rankDir = (config.rankDir === 'TB' ? 'BT' : config.rankDir === 'BT' ? 'TB' : config.rankDir === 'LR' ? 'RL' : 'LR');
                }
            }
        }
        return config;
    }

    function renderNetwork(graph) {
        if (!state.canvas || !graph || graph.nodes.length === 0) return;
        const styles = window.ProcessMapStyles && typeof window.ProcessMapStyles.getCytoscapeStyles === 'function'
            ? window.ProcessMapStyles.getCytoscapeStyles()
            : [];
        const elements = buildElements(graph);
        const layoutConfig = getLayoutConfig();

        state.network = cytoscape({
            container: state.canvas,
            elements: elements,
            style: styles,
            minZoom: 0.3,
            maxZoom: 3
        });

        try {
            const layout = state.network.layout(layoutConfig);
            layout.run();
            layout.on('layoutstop', function() {
                state.network.fit(50);
            });
        } catch (e) {
            console.warn((vcfg.logPrefix || '[ProcessFlowMap]') + ' Layout failed, fitting graph:', e);
            state.network.fit(50);
        }

        if (networkIx) networkIx.setupEventListeners();
        updateLegend();
        // Clear overlay panels when graph is re-rendered (canvas content is replaced by Cytoscape)
        clearOverlayPanels();
    }

    function getOverlayTitle(overlayType) {
        const titles = { description: 'Description', glossary: 'Glossaries', systems: 'Systems', 'data-quality': 'Data Quality', stakeholders: 'Stakeholders', policies: 'Policies', 'business-area': 'Business Area', 'legal-entities': 'Legal Entities', geography: 'Geography' };
        return titles[overlayType] || overlayType || 'Overlay';
    }

    function clearOverlayPanels() {
        const canvas = state.canvas;
        const network = state.network;
        if (canvas) {
            const overlayContainer = canvas.querySelector('.map-overlay-container');
            if (overlayContainer) overlayContainer.innerHTML = '';
        }
        if (network) {
            try { network.off('zoom pan', updateOverlayPositions); } catch (e) { /* no-op */ }
            try { network.off('drag', 'node', updateOverlayPositions); } catch (e) { /* no-op */ }
        }
    }

    // Position overlay panel above the node icon (same logic as system-interfaces-map)
    function positionOverlayPanelElement(panel, node) {
        const overlayContainer = state.canvas?.querySelector('.map-overlay-container');
        const network = state.network;
        if (!overlayContainer || !node || !network) return;

        const position = node.renderedPosition();
        const bb = node.boundingBox();
        const zoom = network.zoom();
        const panelHeight = panel.offsetHeight || 180;
        const panelWidth = panel.offsetWidth || 220;
        const margin = 12;
        const renderedNodeH = (bb.h || 60) * zoom;
        const panelScale = Math.max(0.75, Math.min(1.6, zoom));
        const scaledPanelWidth = panelWidth * panelScale;
        const scaledPanelHeight = panelHeight * panelScale;

        let left = position.x - scaledPanelWidth / 2;
        let top = position.y - renderedNodeH / 2 - scaledPanelHeight - margin;
        const belowTop = position.y + renderedNodeH / 2 + margin;
        const maxLeft = Math.max(0, (overlayContainer.clientWidth || 0) - scaledPanelWidth);
        const maxTop = Math.max(0, (overlayContainer.clientHeight || 0) - scaledPanelHeight);
        const minTop = 8;
        left = Math.max(0, Math.min(maxLeft, left));
        if (top < minTop) top = belowTop;
        top = maxTop < minTop ? Math.max(0, maxTop) : Math.max(minTop, Math.min(maxTop, top));

        panel.style.left = `${left}px`;
        panel.style.top = `${top}px`;
        panel.style.transform = `translate(0, 0) scale(${panelScale})`;
        panel.style.transformOrigin = 'top left';
    }

    function updateOverlayPositions() {
        const overlayContainer = state.canvas?.querySelector('.map-overlay-container');
        if (!overlayContainer || !state.network) return;
        const cw = overlayContainer.clientWidth || 800;
        const ch = overlayContainer.clientHeight || 600;
        const buf = 150;

        overlayContainer.querySelectorAll('.map-node-overlay-panel').forEach(panel => {
            const nodeId = panel.getAttribute('data-node-id');
            const node = state.network.getElementById(nodeId);

            if (node && node.length) {
                const isVisible = node.style('display') !== 'none';
                if (!isVisible) { panel.style.display = 'none'; return; }
                const pos = node.renderedPosition();
                const offScreen = pos.x < -buf || pos.x > cw + buf || pos.y < -buf || pos.y > ch + buf;
                panel.style.display = offScreen ? 'none' : '';
                if (!offScreen) positionOverlayPanelElement(panel, node);
            } else {
                panel.style.display = 'none';
            }
        });
    }

    function renderOverlayPanels(overlayType, overlayData) {
        const network = state.network;
        const canvas = state.canvas;
        if (!network || !canvas) return;
        let overlayContainer = canvas.querySelector('.map-overlay-container');
        if (!overlayContainer) {
            overlayContainer = document.createElement('div');
            overlayContainer.classList.add('map-overlay-container');
            overlayContainer.style.zIndex = '50';
            canvas.appendChild(overlayContainer);
        }
        overlayContainer.innerHTML = '';
        const formatHtml = window.ProcessMapOverlay && typeof window.ProcessMapOverlay.formatOverlayHtml === 'function'
            ? window.ProcessMapOverlay.formatOverlayHtml
            : () => '<p class="map-overlay-empty">No data.</p>';
        network.nodes().forEach(node => {
            const processId = node.data('processId');
            const nodeId = node.data('id');
            const data = processId != null ? overlayData.get(String(processId)) : null;
            if (data == null || (Array.isArray(data) && data.length === 0)) return;
            const panel = document.createElement('div');
            panel.className = 'map-node-overlay-panel';
            panel.setAttribute('data-node-id', nodeId);
            panel.style.pointerEvents = 'auto';
            const header = document.createElement('div');
            header.className = 'map-node-overlay-header';
            const titleSpan = document.createElement('span');
            titleSpan.textContent = getOverlayTitle(overlayType);
            header.appendChild(titleSpan);
            const gearBtn = document.createElement('button');
            gearBtn.type = 'button';
            gearBtn.innerHTML = '<i class="fas fa-cog"></i>';
            gearBtn.style.cssText = 'border:none;background:transparent;cursor:pointer;padding:2px 4px;font-size:12px;color:var(--text-secondary,#6b7280);';
            gearBtn.title = 'Filter & display settings';
            header.appendChild(gearBtn);
            panel.appendChild(header);

            const filterBar = document.createElement('div');
            filterBar.className = 'map-overlay-settings-bar';
            filterBar.style.display = 'none';
            const filterRow = document.createElement('div');
            filterRow.style.cssText = 'position:relative;margin-bottom:6px;';
            const sIcon = document.createElement('i');
            sIcon.className = 'fas fa-search';
            sIcon.style.cssText = 'position:absolute;left:8px;top:50%;transform:translateY(-50%);font-size:10px;color:var(--text-secondary,#9ca3af);pointer-events:none;';
            filterRow.appendChild(sIcon);
            const searchInput = document.createElement('input');
            searchInput.type = 'text';
            searchInput.placeholder = 'Filter entries...';
            searchInput.style.cssText = 'width:100%;box-sizing:border-box;padding:4px 8px 4px 26px;border:1px solid var(--border-color,#d1d5db);border-radius:4px;font-size:11px;outline:none;';
            filterRow.appendChild(searchInput);
            filterBar.appendChild(filterRow);
            const showRow = document.createElement('div');
            showRow.style.cssText = 'display:flex;align-items:center;justify-content:space-between;font-size:11px;color:var(--text-secondary,#4b5563);';
            const totalSpan = document.createElement('span');
            showRow.appendChild(totalSpan);
            const showWrap = document.createElement('span');
            showWrap.style.cssText = 'display:flex;align-items:center;gap:4px;';
            showWrap.innerHTML = '<span>Show</span>';
            const showSelect = document.createElement('select');
            showSelect.style.cssText = 'border:1px solid var(--border-color,#d1d5db);border-radius:4px;font-size:11px;padding:2px 4px;outline:none;cursor:pointer;';
            [{ v: '0', t: 'All' }, { v: '5', t: '5' }, { v: '10', t: '10' }, { v: '20', t: '20' }].forEach(function(o) { var opt = document.createElement('option'); opt.value = o.v; opt.textContent = o.t; showSelect.appendChild(opt); });
            showWrap.appendChild(showSelect);
            showRow.appendChild(showWrap);
            filterBar.appendChild(showRow);
            panel.appendChild(filterBar);

            gearBtn.addEventListener('click', function(e) { e.stopPropagation(); var isOpen = filterBar.style.display !== 'none'; filterBar.style.display = isOpen ? 'none' : ''; if (!isOpen) searchInput.focus(); });

            const body = document.createElement('div');
            body.className = 'map-node-overlay-body';
            const cols = getOverlayColumns(overlayType);
            const items = Array.isArray(data)
                ? data
                : (data && Array.isArray(data.data))
                    ? data.data
                    : (data && Array.isArray(data.items))
                        ? data.items
                        : (data && Array.isArray(data.result))
                            ? data.result
                            : (data && Array.isArray(data.stakeholders))
                                ? data.stakeholders
                                : [];
            const useTable = window.OverlayColumns && cols && cols.length > 0 && Array.isArray(items) && items.length > 0;
            let currentFilter = '';
            let currentMax = 0;
            const getItemText = (item) => {
                if (useTable) {
                    const allCols = window.OverlayColumns.getOverlayColumns ? window.OverlayColumns.getOverlayColumns(overlayType) : [];
                    const colDefs = cols.map(cid => allCols.find(c => c.id === cid) || { id: cid, label: cid });
                    return colDefs.map(c => getOverlayItemField(overlayType, item, c.id)).join(' ');
                }
                return getOverlayItemField(overlayType, item, 'name')
                    || item.value
                    || item.description
                    || item.primaryName
                    || item.name
                    || '';
            };
            const updateDisplay = () => {
                body.innerHTML = '';
                if (items.length === 0) { body.innerHTML = formatHtml(overlayType, data); totalSpan.textContent = 'Total entries: 0'; return; }
                let filtered = items;
                if (currentFilter) { const q = currentFilter.toLowerCase(); filtered = items.filter(item => getItemText(item).toLowerCase().indexOf(q) !== -1); }
                if (currentMax > 0) filtered = filtered.slice(0, currentMax);
                totalSpan.textContent = 'Total entries: ' + filtered.length;
                if (filtered.length === 0) { body.innerHTML = '<p class="map-overlay-empty">No matching entries.</p>'; return; }
                if (useTable) {
                    const allCols = window.OverlayColumns.getOverlayColumns ? window.OverlayColumns.getOverlayColumns(overlayType) : [];
                    const colDefs = cols.map(cid => allCols.find(c => c.id === cid) || { id: cid, label: cid });
                    let tableHtml = '<table class="map-overlay-table"><thead><tr>';
                    colDefs.forEach(c => { tableHtml += '<th>' + (c.label || c.id) + '</th>'; });
                    tableHtml += '</tr></thead><tbody>';
                    filtered.forEach(item => {
                        tableHtml += '<tr>';
                        colDefs.forEach(c => { tableHtml += '<td>' + escapeHtml(getOverlayItemField(overlayType, item, c.id)) + '</td>'; });
                        tableHtml += '</tr>';
                    });
                    tableHtml += '</tbody></table>';
                    body.innerHTML = tableHtml;
                } else {
                    filtered.forEach(item => {
                        const row = document.createElement('div');
                        row.className = 'map-node-overlay-item';
                        row.style.cssText = 'padding:6px 8px;cursor:pointer;border-radius:4px;margin:2px 0;';
                        row.textContent = getItemText(item) || '-';
                        body.appendChild(row);
                    });
                }
            };
            searchInput.addEventListener('click', function(e) { e.stopPropagation(); });
            searchInput.addEventListener('mousedown', function(e) { e.stopPropagation(); });
            searchInput.addEventListener('input', function(e) { e.stopPropagation(); currentFilter = searchInput.value; updateDisplay(); });
            showSelect.addEventListener('click', function(e) { e.stopPropagation(); });
            showSelect.addEventListener('mousedown', function(e) { e.stopPropagation(); });
            showSelect.addEventListener('change', function(e) { e.stopPropagation(); currentMax = parseInt(showSelect.value, 10) || 0; updateDisplay(); });
            updateDisplay();

            panel.appendChild(body);
            overlayContainer.appendChild(panel);
            positionOverlayPanelElement(panel, node);
        });
        network.on('zoom pan', updateOverlayPositions);
        network.on('drag', 'node', updateOverlayPositions);
        // Re-position after layout so panel dimensions are correct (same as system-interfaces-map)
        requestAnimationFrame(() => requestAnimationFrame(updateOverlayPositions));
    }

    async function loadOverlayData(overlayType) {
        const network = state.network;
        if (!network) return;
        const fetchOverlay = window.ProcessMapOverlay && typeof window.ProcessMapOverlay.fetchOverlayData === 'function'
            ? window.ProcessMapOverlay.fetchOverlayData
            : () => Promise.resolve({ overlayType, data: null });
        const overlayData = new Map();
        const nodes = network.nodes();
        const loadingEl = document.querySelector(sel.loading);
        if (loadingEl) loadingEl.style.display = 'block';
        try {
            await Promise.all(nodes.map(async (node) => {
                const processId = node.data('processId');
                if (processId == null) return;
                const { data } = await fetchOverlay(processId, overlayType);
                overlayData.set(String(processId), data);
            }));
            renderOverlayPanels(overlayType, overlayData);
        } finally {
            if (loadingEl) loadingEl.style.display = 'none';
        }
    }

    function setOverlay(overlay) {
        state.overlay = overlay;
        clearOverlayPanels();
        if (overlay === 'none' || !overlay) return;
        loadOverlayData(overlay);
    }

    function setOverlayColumns(overlayType, columnIds) {
        if (!overlayType) return;
        state.overlayColumnsByType[overlayType] = Array.isArray(columnIds) ? columnIds.slice() : [];
        if (state.overlay === overlayType) {
            setOverlay(overlayType);
        }
    }

    function getOverlayColumns(overlayType) {
        var raw = state.overlayColumnsByType[overlayType];
        if (window.OverlayColumns && typeof window.OverlayColumns.resolveSelectedColumnIds === 'function') {
            return window.OverlayColumns.resolveSelectedColumnIds(raw, overlayType);
        }
        return (Array.isArray(raw) && raw.length > 0) ? raw.slice() : (window.OverlayColumns ? window.OverlayColumns.getDefaultOverlayColumnIds(overlayType) : ['name']);
    }

    function getState() {
        return state;
    }

    function getOverlayItemField(overlayType, item, fieldId) {
        if (!item) return '';
        const v = (x) => (x != null && x !== '') ? String(x) : '';
        const first = (...vals) => {
            for (let i = 0; i < vals.length; i++) {
                const out = v(vals[i]).trim();
                if (out) return out;
            }
            return '';
        };
        function fieldDefault(it, fid) {
            const d = it[fid];
            if (d !== undefined && d !== null && d !== '') return v(d);
            if (window.MapOverlayFieldPick && typeof window.MapOverlayFieldPick.pick === 'function') {
                return window.MapOverlayFieldPick.pick(it, fid);
            }
            return '';
        }
        switch (overlayType) {
            case 'glossary':
                switch (fieldId) {
                    case 'name': return v(item.glossaryName || item.glossary_name || item.glossary || item.name || item.primaryName);
                    case 'source': return item.source === 'dataset' ? 'Dataset Glossary'
                        : item.source === 'attribute' ? 'Attribute Glossary' : '';
                    case 'aliasNames':
                        if (Array.isArray(item.aliases) && item.aliases.length) return v(item.aliases.join(', '));
                        return v(item.aliasNames || item.aliases || item.alias);
                    case 'parentName': return v(item.parentName || item.ParentName || item.parent?.name);
                    case 'lifecycle': return v(item.lifecycleName || item.LifecycleName || item.lifecycle || item.Lifecycle || item.lifecycleStatusName || item.LifecycleStatusName || item.lifecycleStatus || item.LifecycleStatus || item.processLifecycleName || item.ProcessLifecycleName || item.sourceProcessLifecycleName || item.targetProcessLifecycleName || item.datasetLifecycleName || item.DatasetLifecycleName || fieldDefault(item, fieldId));
                    case 'securityClassification': return v(item.securityClassification || item.classification || item.securityName || item.SecurityName);
                    default: return fieldDefault(item, fieldId);
                }
            case 'description':
                return fieldId === 'value'
                    ? v(item.value || item.definition || item.Definition || item.description || item.Description)
                    : fieldDefault(item, fieldId);
            case 'systems':
                switch (fieldId) {
                    case 'name': return v(item.systemName || item.system_name || item.SystemName || item.name || item.primaryName);
                    case 'refNumber': return v(item.systemRef || item.SystemRef || item.refNumber || item.refnumber || item.RefNumber || item.ref || item.Ref || item.processRefNumber || item.ProcessRefNumber || item.processRef || item.ProcessRef || item.projectRefNumber || item.ProjectRefNumber || item.projectRef || item.ProjectRef || item.productRefNumber || item.ProductRefNumber || item.policyRefNumber || item.PolicyRefNumber || item.capabilityRefNumber || item.CapabilityRefNumber || item.datasetRefNumber || item.DatasetRefNumber || item.attributeRefNumber || item.AttributeRefNumber || item.glossaryRefNumber || item.GlossaryRefNumber || item.interfaceRefNumber || item.InterfaceRefNumber || item.regulationRefNumber || item.RegulationRefNumber || item.regulatoryThemeRefNumber || item.RegulatoryThemeRefNumber || item.businessAreaReference || item.BusinessAreaReference || item.clientReference || item.ClientReference || item.legalReference || item.LegalReference || item.sourceProcessRef || item.targetProcessRef);
                    default: return fieldDefault(item, fieldId);
                }
            case 'datasets':
                switch (fieldId) {
                    case 'name': return v(item.primaryName || item.name || item.shortName);
                    case 'refNumber': return v(item.refNumber || item.refnumber || item.RefNumber || item.ref || item.Ref || item.processRefNumber || item.ProcessRefNumber || item.processRef || item.ProcessRef || item.projectRefNumber || item.ProjectRefNumber || item.projectRef || item.ProjectRef || item.productRefNumber || item.ProductRefNumber || item.policyRefNumber || item.PolicyRefNumber || item.capabilityRefNumber || item.CapabilityRefNumber || item.datasetRefNumber || item.DatasetRefNumber || item.attributeRefNumber || item.AttributeRefNumber || item.glossaryRefNumber || item.GlossaryRefNumber || item.interfaceRefNumber || item.InterfaceRefNumber || item.systemRef || item.SystemRef || item.regulationRefNumber || item.RegulationRefNumber || item.regulatoryThemeRefNumber || item.RegulatoryThemeRefNumber || item.businessAreaReference || item.BusinessAreaReference || item.clientReference || item.ClientReference || item.legalReference || item.LegalReference || item.sourceProcessRef || item.targetProcessRef);
                    case 'type': return v(item.typeName || item.type);
                    case 'lifecycle': return v(item.lifecycleName || item.LifecycleName || item.lifecycle || item.Lifecycle || item.lifecycleStatusName || item.LifecycleStatusName || item.lifecycleStatus || item.LifecycleStatus || item.processLifecycleName || item.ProcessLifecycleName || item.sourceProcessLifecycleName || item.targetProcessLifecycleName || item.datasetLifecycleName || item.DatasetLifecycleName || fieldDefault(item, fieldId));
                    default: return fieldDefault(item, fieldId);
                }
            case 'attributes':
            case 'linking-attributes':
                switch (fieldId) {
                    case 'name': return v(item.name || item['Name attribute'] || item.attributeName || item.primaryName);
                    case 'type': return v(item.typeName || item.type);
                    case 'glossary': return v(item.glossaryName || item.glossary);
                    case 'refNumber': return v(item.refNumber || item.refnumber || item.RefNumber || item.ref || item.Ref || item.processRefNumber || item.ProcessRefNumber || item.processRef || item.ProcessRef || item.projectRefNumber || item.ProjectRefNumber || item.projectRef || item.ProjectRef || item.productRefNumber || item.ProductRefNumber || item.policyRefNumber || item.PolicyRefNumber || item.capabilityRefNumber || item.CapabilityRefNumber || item.datasetRefNumber || item.DatasetRefNumber || item.attributeRefNumber || item.AttributeRefNumber || item.glossaryRefNumber || item.GlossaryRefNumber || item.interfaceRefNumber || item.InterfaceRefNumber || item.systemRef || item.SystemRef || item.regulationRefNumber || item.RegulationRefNumber || item.regulatoryThemeRefNumber || item.RegulatoryThemeRefNumber || item.businessAreaReference || item.BusinessAreaReference || item.clientReference || item.ClientReference || item.legalReference || item.LegalReference || item.sourceProcessRef || item.targetProcessRef);
                    default: return fieldDefault(item, fieldId);
                }
            case 'custom-fields':
                switch (fieldId) {
                    case 'metadataId': return v(item.metadataId != null ? item.metadataId : item.Metadata_ID);
                    case 'enumId':
                        if (item.enumId !== undefined && item.enumId !== null && item.enumId !== '') return v(item.enumId);
                        if (item.enumIds && item.enumIds.length) return v(item.enumIds.join(', '));
                        return '';
                    case 'value': return v(item.value != null && item.value !== '' ? item.value : (item.displayValue != null ? item.displayValue : ''));
                    default: return fieldDefault(item, fieldId);
                }
            case 'stakeholders':
                switch (fieldId) {
                    case 'name': return first(item.personName, item.PersonName, item.person_name, item.personname, item.name, item.Name);
                    case 'role': return first(item.roleName, item.RoleName, item.role_name, item.rolename, item.role, item.Role);
                    case 'accepted': return first(item.accepted, item.Accepted, item.acceptedStatus);
                    case 'orgUnit': return first(item.orgUnit, item.OrgUnit, item.orgUnitName);
                    default: return fieldDefault(item, fieldId);
                }
            case 'processes':
            case 'projects':
            case 'policies':
            case 'business-area':
            case 'products':
            case 'legal-entities':
            case 'clients':
            case 'capabilities':
            case 'data-quality':
            case 'data-privacy':
            case 'geography':
                switch (fieldId) {
                    case 'name':
                        return first(
                            item.name, item.Name,
                            item.primaryName, item.PrimaryName,
                            item.systemName, item.SystemName,
                            item.processName, item.ProcessName,
                            item.projectName, item.ProjectName,
                            item.policyName, item.PolicyName,
                            item.businessAreaName, item.BusinessAreaName,
                            item.productName, item.ProductName,
                            item.legalEntityName, item.LegalEntityName,
                            item.legalShortName, item.LegalShortName,
                            item.legalLongName, item.LegalLongName,
                            item.legalName, item.LegalName,
                            item.clientName, item.ClientName,
                            item.capabilityName, item.CapabilityName,
                            item.glossaryName, item.GlossaryName,
                            item.ruleName, item.RuleName
                        );
                    case 'refNumber': return v(item.refNumber || item.refnumber || item.RefNumber || item.ref || item.Ref || item.processRefNumber || item.ProcessRefNumber || item.processRef || item.ProcessRef || item.projectRefNumber || item.ProjectRefNumber || item.projectRef || item.ProjectRef || item.productRefNumber || item.ProductRefNumber || item.policyRefNumber || item.PolicyRefNumber || item.capabilityRefNumber || item.CapabilityRefNumber || item.datasetRefNumber || item.DatasetRefNumber || item.attributeRefNumber || item.AttributeRefNumber || item.glossaryRefNumber || item.GlossaryRefNumber || item.interfaceRefNumber || item.InterfaceRefNumber || item.systemRef || item.SystemRef || item.regulationRefNumber || item.RegulationRefNumber || item.regulatoryThemeRefNumber || item.RegulatoryThemeRefNumber || item.businessAreaReference || item.BusinessAreaReference || item.clientReference || item.ClientReference || item.legalReference || item.LegalReference || item.sourceProcessRef || item.targetProcessRef);
                    case 'type': return v(item.typeName || item.type || item.TypeName || item.Type || item.policyTypeName || item.PolicyTypeName || item.productTypeName || item.ProductTypeName || item.relationTypeName || item.RelationTypeName);
                    case 'lifecycle': return v(item.lifecycleName || item.LifecycleName || item.lifecycle || item.Lifecycle || item.lifecycleStatusName || item.LifecycleStatusName || item.lifecycleStatus || item.LifecycleStatus || item.processLifecycleName || item.ProcessLifecycleName || item.sourceProcessLifecycleName || item.targetProcessLifecycleName || item.datasetLifecycleName || item.DatasetLifecycleName || fieldDefault(item, fieldId));
                    case 'status': return v(item.statusName || item.StatusName || item.status || item.Status || item.projectStatusName || item.ProjectStatusName || item.policyStatusName || item.PolicyStatusName);
                    case 'ruleName': return v(item.ruleName || item.RuleName);
                    case 'rating': return v(item.rating || item.qualityRating);
                    case 'classification': return v(item.classification || item.privacyClassification);
                    case 'region': return v(item.region || item.Region);
                    case 'country': return v(item.country || item.Country);
                    default: return fieldDefault(item, fieldId);
                }
            default: {
                const fb = fieldDefault(item, fieldId);
                if (fb) return fb;
                return v(item.name || item.primaryName);
            }
        }
    }

    /**
     * Recursively load predecessors: current process's predecessors plus predecessors of those.
     * Returns merged list of relations (source→target). Each relation has sourceProcessId/sourceprocess_id and targetProcessId/targetprocess_id.
     */
    async function loadPredecessorsRecursive(processId) {
        const predResp = await fetch(`/api/process-impact/${processId}/predecessors`, { credentials: 'include' });
        let direct = predResp.ok ? (await predResp.json()) || [] : [];
        if (!Array.isArray(direct)) direct = direct.data || [];
        const allRels = [];
        const seenEdge = new Set();
        const directPredIds = new Set();
        direct.forEach(rel => {
            const src = String(rel.sourceProcessId != null ? rel.sourceProcessId : rel.sourceprocess_id);
            const tgt = String(rel.targetProcessId != null ? rel.targetProcessId : rel.targetprocess_id);
            if (!src || !tgt) return;
            directPredIds.add(src);
            const key = src + '->' + tgt;
            if (!seenEdge.has(key)) {
                seenEdge.add(key);
                allRels.push(rel);
            }
        });
        await Promise.all(Array.from(directPredIds).map(async (pid) => {
            const subResp = await fetch(`/api/process-impact/${pid}/predecessors`, { credentials: 'include' });
            let sub = subResp.ok ? (await subResp.json()) || [] : [];
            if (!Array.isArray(sub)) sub = sub.data || [];
            sub.forEach(rel => {
                const src = String(rel.sourceProcessId != null ? rel.sourceProcessId : rel.sourceprocess_id);
                const tgt = String(rel.targetProcessId != null ? rel.targetProcessId : rel.targetprocess_id);
                if (!src || !tgt) return;
                const key = src + '->' + tgt;
                if (!seenEdge.has(key)) {
                    seenEdge.add(key);
                    allRels.push(rel);
                }
            });
        }));
        return allRels;
    }

    /**
     * Optional dependency filter: if the backend provides GET /process-impact/{id}/dependencies
     * returning the set of process IDs that are "in the process's dependencies", call this to
     * filter relations so only nodes in that set are shown (e.g. Z, A, D and not C for B).
     */
    function filterPredecessorsByDependency(relations, currentProcessId, dependencySetIds) {
        if (!dependencySetIds || dependencySetIds.size === 0) return relations;
        const allowed = new Set([String(currentProcessId), ...dependencySetIds].map(String));
        return relations.filter(rel => {
            const src = String(rel.sourceProcessId != null ? rel.sourceProcessId : rel.sourceprocess_id);
            const tgt = String(rel.targetProcessId != null ? rel.targetProcessId : rel.targetprocess_id);
            return allowed.has(src) && allowed.has(tgt);
        });
    }

    async function loadMapData() {
        const logP = vcfg.logPrefix || '[ProcessFlowMap]';
        const loadingEl = document.querySelector(sel.loading);

        if (variant === 'components') {
            const children = state.children || [];
            const childIds = new Set(children.map(c => String(c.id != null ? c.id : c.ID)));
            if (loadingEl) loadingEl.style.display = 'block';
            state.childRelations.clear();

            try {
                const promises = [];
                children.forEach(c => {
                    const cid = c.id != null ? c.id : c.ID;
                    promises.push(
                        fetch(`/api/process-impact/${cid}/predecessors`, { credentials: 'include' }).then(r => r.ok ? r.json() : []).catch(() => []),
                        fetch(`/api/process-impact/${cid}/successors`, { credentials: 'include' }).then(r => r.ok ? r.json() : []).catch(() => [])
                    );
                });
                const results = await Promise.all(promises);
                for (let i = 0; i < children.length; i++) {
                    const cid = String(children[i].id != null ? children[i].id : children[i].ID);
                    let pred = results[i * 2];
                    let succ = results[i * 2 + 1];
                    if (!Array.isArray(pred)) pred = pred && pred.data ? pred.data : [];
                    if (!Array.isArray(succ)) succ = succ && succ.data ? succ.data : [];
                    pred = pred.filter(r => {
                        const sid = String(r.sourceProcessId != null ? r.sourceProcessId : r.sourceprocess_id);
                        return sid && childIds.has(sid);
                    });
                    succ = succ.filter(r => {
                        const tid = String(r.targetProcessId != null ? r.targetProcessId : r.targetprocess_id);
                        return tid && childIds.has(tid);
                    });
                    state.childRelations.set(cid, { predecessors: pred, successors: succ });
                }

                const graph = buildGraph();
                if (graph.nodes.length === 0) {
                    if (state.canvas) {
                        state.canvas.innerHTML = '<div class="map-canvas-message">No components to display.</div>';
                    }
                } else {
                    renderNetwork(graph);
                }
            } catch (e) {
                console.error(logP + ' Error loading map data:', e);
                if (state.canvas) {
                    state.canvas.innerHTML = '<div class="map-canvas-message">Error loading components map.</div>';
                }
            } finally {
                if (loadingEl) loadingEl.style.display = 'none';
            }
            return;
        }

        const processId = state.processId;
        if (loadingEl) loadingEl.style.display = 'block';

        try {
            const [allPredRels, succResp] = await Promise.all([
                loadPredecessorsRecursive(processId),
                fetch(`/api/process-impact/${processId}/successors`, { credentials: 'include' }).catch(() => ({ ok: false }))
            ]);

            state.predecessors = allPredRels;

            state.successors = succResp.ok
                ? (await succResp.json()) || []
                : [];
            if (!Array.isArray(state.successors)) {
                state.successors = state.successors.data || [];
            }

            const graph = buildGraph();
            if (graph.nodes.length === 0) {
                if (state.canvas) {
                    state.canvas.innerHTML = '<div class="map-canvas-message">No dependencies to display. Add predecessors or successors for this process.</div>';
                }
            } else {
                renderNetwork(graph);
            }
        } catch (e) {
            console.error(logP + ' Error loading map data:', e);
            if (state.canvas) {
                state.canvas.innerHTML = '<div class="map-canvas-message">Error loading context map.</div>';
            }
        } finally {
            if (loadingEl) loadingEl.style.display = 'none';
        }
    }

    function setLayout(layout) {
        state.layout = layout;
        if (state.network) {
            const config = getLayoutConfig();
            try {
                const layoutInstance = state.network.layout(config);
                layoutInstance.run();
                layoutInstance.on('layoutstop', function() {
                    state.network.fit(50);
                });
            } catch (e) {
                console.warn((vcfg.logPrefix || '[ProcessFlowMap]') + ' Layout failed:', e);
                state.network.fit(50);
            }
        }
    }

    function zoomIn() {
        if (state.network) {
            state.network.zoom(state.network.zoom() * 1.2);
        }
    }

    function zoomOut() {
        if (state.network) {
            state.network.zoom(state.network.zoom() * 0.8);
        }
    }

    function resetMap() {
        if (state.network) {
            state.network.reset();
        }
    }

    function redrawMap() {
        loadMapData();
    }

    function getShellHtml() {
        return `
            <div class="interface-map-container interface-map-container--process-tall ${vcfg.wrapperClass || ''}" id="${vcfg.containerId}">
                <div class="interface-map-toolbar">
                    <div class="map-control-group">
                        <label>Map type:</label>
                        <select id="${MAP_ID}TypeSelect" class="map-select">
                            <option value="process-lineage" selected>Process Lineage</option>
                        </select>
                    </div>
                    <div class="map-control-group">
                        <label>Layout:</label>
                        <div class="map-layout-controls">
                            ${typeof window.SharedMapLayoutRichControlsHtml === 'function' ? window.SharedMapLayoutRichControlsHtml(MAP_ID) : ''}
                        </div>
                    </div>
                    <div class="map-control-group">
                        <label>Overlay:</label>
                        <div class="map-overlay-controls">
                            <div class="map-overlay-dropdown">
                                <button type="button" class="map-select-btn" id="${MAP_ID}OverlayBtn">
                                    <span id="${MAP_ID}OverlayBtnText">None</span>
                                    <i class="fas fa-chevron-down"></i>
                                </button>
                                <div class="map-overlay-menu" id="${MAP_ID}OverlayMenu">
                                    <div class="overlay-menu-grid">
                                        <div class="overlay-menu-column">
                                            <div class="overlay-menu-header">Data</div>
                                            <div class="overlay-menu-item" data-overlay="description"><i class="fas fa-info-circle"></i> Description</div>
                                            <div class="overlay-menu-item" data-overlay="glossary"><i class="fas fa-book"></i> Glossary</div>
                                            <div class="overlay-menu-item" data-overlay="systems"><i class="fas fa-server"></i> Systems</div>
                                        </div>
                                        <div class="overlay-menu-column">
                                            <div class="overlay-menu-header">Business</div>
                                            <div class="overlay-menu-item" data-overlay="data-quality"><i class="fas fa-chart-line"></i> Data Quality</div>
                                            <div class="overlay-menu-item" data-overlay="stakeholders"><i class="fas fa-users"></i> Stakeholders</div>
                                            <div class="overlay-menu-item" data-overlay="policies"><i class="fas fa-shield-alt"></i> Policies</div>
                                        </div>
                                        <div class="overlay-menu-column">
                                            <div class="overlay-menu-header">Organizational</div>
                                            <div class="overlay-menu-item" data-overlay="business-area"><i class="fas fa-sitemap"></i> Business Area</div>
                                            <div class="overlay-menu-item" data-overlay="legal-entities"><i class="fas fa-balance-scale"></i> Legal Entities</div>
                                        </div>
                                        <div class="overlay-menu-column">
                                            <div class="overlay-menu-header">Regulatory</div>
                                            <div class="overlay-menu-item" data-overlay="geography"><i class="fas fa-globe"></i> Geography</div>
                                        </div>
                                    </div>
                                    <div class="overlay-menu-footer">
                                        <button type="button" class="overlay-clear-btn" id="${MAP_ID}ClearOverlaysBtn">Clear Overlays</button>
                                    </div>
                                </div>
                            </div>
                            <button type="button" class="map-toolbar-btn-sm" id="${MAP_ID}OverlayGrid" title="Overlay fields as columns">
                                <i class="fas fa-th"></i>
                                <i class="fas fa-chevron-down map-toolbar-chevron"></i>
                            </button>
                            <div class="map-overlay-columns-menu map-filter-menu map-menu--closed" id="${MAP_ID}OverlayColumnsMenu">
                                <div class="map-filter-category">
                                    <div class="map-filter-category-header">OVERLAY FIELDS AS COLUMNS</div>
                                    <div id="${MAP_ID}OverlayColumnsOptions" class="map-filter-options-container"></div>
                                </div>
                            </div>
                        </div>
                    </div>
                    <div class="map-control-group">
                        <label>Filters:</label>
                        <div class="map-filter-dropdown">
                            <button type="button" class="map-select-btn" id="${MAP_ID}FilterBtn">
                                <span id="${MAP_ID}FilterBtnText">All lifecycles</span>
                                <i class="fas fa-chevron-down"></i>
                            </button>
                            <div class="map-filter-menu" id="${MAP_ID}FilterMenu">
                                <div class="map-filter-category">
                                    <div class="map-filter-category-header">PROCESS</div>
                                    <div class="map-filter-option">
                                        <input type="checkbox" id="${MAP_ID}FilterLifecycle" title="When enabled, only predecessors and successors in the same lifecycle as this process are shown.">
                                        <label for="${MAP_ID}FilterLifecycle">Same lifecycle only</label>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                    <div class="map-toolbar-buttons">
                        <button type="button" class="map-toolbar-btn" id="${MAP_ID}ToggleLabels" title="Toggle labels"><i class="fas fa-exchange-alt"></i></button>
                        <div class="map-toolbar-separator"></div>
                        <button type="button" class="map-toolbar-btn" id="${MAP_ID}ZoomIn" title="Zoom in"><i class="fas fa-search-plus"></i></button>
                        <button type="button" class="map-toolbar-btn" id="${MAP_ID}ZoomOut" title="Zoom out"><i class="fas fa-search-minus"></i></button>
                        <div class="map-toolbar-separator"></div>
                        <button type="button" class="map-toolbar-btn" id="${MAP_ID}Redraw" title="Redraw"><i class="fas fa-sync-alt"></i></button>
                        <button type="button" class="map-toolbar-btn" id="${MAP_ID}Reset" title="Reset"><i class="fas fa-undo"></i></button>
                        <button type="button" class="map-toolbar-btn" id="${MAP_ID}Export" title="Export as PNG"><i class="fas fa-save"></i></button>
                        <div class="map-toolbar-separator"></div>
                        <button type="button" class="map-toolbar-btn" id="${MAP_ID}Navigator" title="Map navigator"><i class="fas fa-eye"></i></button>
                        <button type="button" class="map-toolbar-btn" id="${MAP_ID}Fullscreen" title="Fullscreen"><i class="fas fa-external-link-alt"></i></button>
                        <button type="button" class="map-toolbar-btn" id="${MAP_ID}Legend" title="Open the Legend" aria-label="Open the Legend – refer to the legend to identify the symbols used in the map (Insight Maps Palette)"><i class="fas fa-list-ul"></i></button>
                        <div class="map-toolbar-separator"></div>
                        <button type="button" class="map-toolbar-btn" id="${MAP_ID}SavePreferences" title="Save map settings"><i class="fas fa-save"></i></button>
                        <button type="button" class="map-toolbar-btn" id="${MAP_ID}ResetPreferences" title="Reset map settings to default"><i class="fas fa-undo"></i></button>
                    </div>
                </div>
                <div class="interface-map-body interface-map-body--flex-fill">
                    <div class="interface-map-canvas interface-map-canvas--fill" id="${vcfg.canvasId}">
                        <div class="interface-map-loading map-js-hidden" ${attrSel(sel.loading)}>
                            <i class="fas fa-spinner fa-spin"></i>
                            <span>Building map...</span>
                        </div>
                    </div>
                    <div class="interface-map-side-panel map-js-hidden" id="${MAP_ID}SidePanel">
                        <div class="map-side-panel-section">
                            <h4><i class="fas fa-info-circle"></i> Selection</h4>
                            <div class="selection-placeholder" ${attrSel(sel.placeholder)}>Select a node to see its details.</div>
                            <div class="selection-info map-js-hidden" ${attrSel(sel.details)}></div>
                        </div>
                        <div class="map-side-panel-section">
                            <h4 class="map-legend-palette-title"><i class="fas fa-layer-group"></i> Insight Maps Palette</h4>
                            <div ${attrSel(sel.legend)}></div>
                        </div>
                    </div>
                </div>
            </div>
        `;
    }

    function getLegendHtml() {
        return `
            <div class="legend-item"><div class="legend-color map-legend-swatch--start"><span class="map-legend-swatch__marker"></span></div><span>Starting Step</span><span class="legend-desc"> – A block with a green circle indicates the starting step for a process.</span></div>
            <div class="legend-item"><div class="legend-color map-legend-swatch--common"></div><span>Common Step</span><span class="legend-desc"> – A plain grey block indicates a common step within a process.</span></div>
            <div class="legend-item"><div class="legend-color map-legend-swatch--end"><span class="map-legend-swatch__marker"></span></div><span>Ending Step</span><span class="legend-desc"> – A block with a blue circle indicates the ending step for a process.</span></div>
            <div class="legend-item"><div class="legend-color map-legend-swatch--nested-process"></div><span>Process</span><span class="legend-desc"> – A block with a plus symbol indicates that it is a complete process in itself. When the Type value is "Process," the Step Type value is not considered while creating the block.</span></div>
            <div class="legend-item"><div class="legend-color map-legend-swatch--control"></div><span>Control</span><span class="legend-desc"> – An orange decision box indicates a decision control step for a process. When the Type value is "Control," the Step Type value is not considered while creating the block.</span></div>
        `;
    }

    function updateLegend() {
        const legendEl = document.querySelector(sel.legend);
        if (legendEl) legendEl.innerHTML = getLegendHtml();
        const dropdown = document.getElementById(MAP_ID + 'LegendDropdown');
        if (dropdown) dropdown.innerHTML = getLegendHtml();
    }

    function showNodeDetails(nodeData) {
        const detailsEl = document.querySelector(sel.details);
        const placeholderEl = document.querySelector(sel.placeholder);
        if (!detailsEl || !placeholderEl) return;
        state.lastSelectedNodeData = nodeData;
        const name = nodeData.label ? nodeData.label.replace(/\n/g, ' / ') : 'Node';
        const stepType = nodeData.stepType || 'process';
        const primaryName = (nodeData.primaryName || '').toString().toLowerCase();
        let typeLabel = 'Process';
        if (stepType === 'control') typeLabel = 'Control';
        else if (stepType === 'step') {
            if (primaryName === 'starting') typeLabel = 'Start';
            else if (primaryName === 'ending') typeLabel = 'End';
            else typeLabel = 'Common Step';
        }
        const pid = nodeData.processId;
        const isCurrent = pid === String(state.processId);
        placeholderEl.style.display = 'none';
        detailsEl.style.display = 'block';
        let linkHtml = '';
        if (pid && !isCurrent) {
            linkHtml = `<div class="map-detail-link-wrap"><a href="/view/process/${escapeHtml(String(pid))}" class="relationship-link">Go to process</a></div>`;
        }
        const baseHtml = `<div><strong>${escapeHtml(name)}</strong></div><div class="map-detail-meta">${escapeHtml(typeLabel)}</div>${linkHtml}`;
        const overlayType = state.overlay;
        if (!overlayType || overlayType === 'none') {
            detailsEl.innerHTML = baseHtml;
            return;
        }
        detailsEl.innerHTML = baseHtml + '<div class="map-overlay-loading" ' + attrSel(sel.overlay) + '><i class="fas fa-spinner fa-spin"></i> Loading overlay...</div>';
        const overlayEl = detailsEl.querySelector(sel.overlay);
        if (window.ProcessMapOverlay && typeof window.ProcessMapOverlay.fetchAndFormatOverlay === 'function') {
            window.ProcessMapOverlay.fetchAndFormatOverlay(pid, overlayType).then(html => {
                if (overlayEl && overlayEl.parentNode) overlayEl.outerHTML = html || '';
            }).catch(() => {
                if (overlayEl && overlayEl.parentNode) overlayEl.outerHTML = '<p class="map-overlay-empty">Failed to load overlay.</p>';
            });
        } else {
            if (overlayEl && overlayEl.parentNode) overlayEl.outerHTML = '<p class="map-overlay-empty">Overlay not available.</p>';
        }
    }

    function hideNodeDetails() {
        state.lastSelectedNodeData = null;
        const detailsEl = document.querySelector(sel.details);
        const placeholderEl = document.querySelector(sel.placeholder);
        if (detailsEl) detailsEl.style.display = 'none';
        if (placeholderEl) placeholderEl.style.display = 'block';
    }

    networkIx = window.createProcessFlowMapNetworkInteractions({
        state: state,
        buildGraph: buildGraph,
        renderNetwork: renderNetwork,
        showNodeDetails: showNodeDetails,
        hideNodeDetails: hideNodeDetails,
        contextMenuId: vcfg.contextMenuDomId,
        defaultNavBase: '/view/process/'
    });

    function toggleSidePanel() {
        const panel = document.getElementById(`${MAP_ID}SidePanel`);
        const legendBtn = document.getElementById(`${MAP_ID}Legend`);
        if (!panel || !legendBtn) return;
        const isVisible = panel.style.display !== 'none';
        panel.style.display = isVisible ? 'none' : 'block';
        legendBtn.classList.toggle('active', !isVisible);
    }

    function toggleNavigator() {
        if (state.network && state.network.navigator) {
            const nav = state.network.navigator();
            if (nav) nav.toggle();
        }
    }

    function openFullscreen() {
        if (state.network && typeof window.openMapFullscreen === 'function') {
            var nodes = state.network.nodes().map(function(n){ return n.json(); });
            var edges = state.network.edges().map(function(e){ return e.json(); });
            var styles = window.ProcessMapStyles && typeof window.ProcessMapStyles.getCytoscapeStyles === 'function'
                ? window.ProcessMapStyles.getCytoscapeStyles() : [];
            window.openMapFullscreen({
                title: (vcfg.fullscreenTitle || 'Process map') + ' - ' + (state.processId || ''),
                elements: { nodes: nodes, edges: edges },
                style: styles,
                layoutName: state.layout || 'top-to-bottom',
                legendHtml: getLegendHtml(),
                exportFilename: (vcfg.exportPrefix || 'process-map') + '-' + state.processId + '.png',
                toolbarAnchor: state.canvas,
                mapType: 'process-lineage',
                mapTabKind: 'process-flow-' + (vcfg.variant || 'context'),
                getState: function () { return state; }
            });
        } else {
            var canvas = state.canvas;
            if (!canvas) return;
            if (canvas.requestFullscreen) canvas.requestFullscreen();
            else if (canvas.webkitRequestFullscreen) canvas.webkitRequestFullscreen();
        }
    }

    function setupControls() {
        const shellRoot = document.getElementById(vcfg.containerId);
        if (shellRoot && shellRoot.dataset.processFlowToolbarBound === MAP_ID) {
            return;
        }
        if (shellRoot) shellRoot.dataset.processFlowToolbarBound = MAP_ID;

        document.getElementById(`${MAP_ID}ZoomIn`)?.addEventListener('click', zoomIn);
        document.getElementById(`${MAP_ID}ZoomOut`)?.addEventListener('click', zoomOut);
        document.getElementById(`${MAP_ID}Reset`)?.addEventListener('click', resetMap);
        document.getElementById(`${MAP_ID}Redraw`)?.addEventListener('click', redrawMap);

        const exportBtn = document.getElementById(`${MAP_ID}Export`);
        if (exportBtn) exportBtn.addEventListener('click', () => {
            if (!state.network) return;
            try {
                const filename = `${vcfg.exportPrefix || 'process-map'}-${state.processId}-${Date.now()}.png`;
                if (typeof window.exportMapWithOverlays === 'function') {
                    window.exportMapWithOverlays(state.network, state.canvas, filename);
                } else {
                    const png = state.network.png({ output: 'blob', bg: 'white', full: true });
                    const url = URL.createObjectURL(png);
                    const a = document.createElement('a');
                    a.href = url;
                    a.download = filename;
                    a.click();
                    URL.revokeObjectURL(url);
                }
            } catch (e) { console.error(e); }
        });

        document.getElementById(`${MAP_ID}Navigator`)?.addEventListener('click', toggleNavigator);
        document.getElementById(`${MAP_ID}Fullscreen`)?.addEventListener('click', openFullscreen);
        const legendBtn = document.getElementById(`${MAP_ID}Legend`);
        if (legendBtn && typeof window.setupMapLegendDropdown === 'function') {
            window.setupMapLegendDropdown(MAP_ID + 'Legend', MAP_ID + 'LegendDropdown', () => getLegendHtml());
        } else if (legendBtn) {
            legendBtn.addEventListener('click', toggleSidePanel);
        }
        document.getElementById(`${MAP_ID}SavePreferences`)?.addEventListener('click', () => { /* save to localStorage if needed */ });
        document.getElementById(`${MAP_ID}ResetPreferences`)?.addEventListener('click', () => {
            state.layoutDirection = 'forward';
            setLayout('left-to-right');
            resetMap();
        });

        // Initialize shared dropdown menus for expand/collapse & direction buttons
        if (typeof window.SharedMapDropdowns === 'function') {
            window.SharedMapDropdowns({
                mapId: MAP_ID,
                getNetwork: () => state.network,
                setLayout: (dir) => {
                    if (dir === 'right-to-left') {
                        state.layoutDirection = 'backward';
                    } else {
                        state.layoutDirection = 'forward';
                    }
                    const ls = document.getElementById(MAP_ID + 'LayoutSelect');
                    if (ls) ls.value = dir;
                    setLayout(dir);
                },
                getCanvas: () => document.getElementById(MAP_ID + 'Canvas')
            });
        }

        const overlayLabels = { description: 'Description', glossary: 'Glossaries', systems: 'Systems', 'data-quality': 'Data Quality', stakeholders: 'Stakeholders', policies: 'Policies', 'business-area': 'Business Area', 'legal-entities': 'Legal Entities', geography: 'Geography' };
        const overlayBtn = document.getElementById(`${MAP_ID}OverlayBtn`);
        const overlayMenu = document.getElementById(`${MAP_ID}OverlayMenu`);
        if (overlayBtn && overlayMenu) {
            overlayBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                overlayMenu.classList.toggle('open');
            });
            overlayMenu.querySelectorAll('.overlay-menu-item').forEach(item => {
                item.addEventListener('click', function(e) {
                    e.preventDefault();
                    e.stopPropagation();
                    const overlayType = this.getAttribute('data-overlay');
                    const wasActive = this.classList.contains('active');
                    overlayMenu.querySelectorAll('.overlay-menu-item').forEach(i => i.classList.remove('active'));
                    if (!wasActive) {
                        this.classList.add('active');
                        const textEl = document.getElementById(`${MAP_ID}OverlayBtnText`);
                        if (textEl) textEl.textContent = overlayLabels[overlayType] || overlayType || 'None';
                        setOverlay(overlayType);
                    } else {
                        const textEl = document.getElementById(`${MAP_ID}OverlayBtnText`);
                        if (textEl) textEl.textContent = 'None';
                        setOverlay('none');
                    }
                    overlayMenu.classList.remove('open');
                    if (state.lastSelectedNodeData) showNodeDetails(state.lastSelectedNodeData);
                });
            });
            document.getElementById(`${MAP_ID}ClearOverlaysBtn`)?.addEventListener('click', () => {
                if (document.getElementById(`${MAP_ID}OverlayBtnText`)) document.getElementById(`${MAP_ID}OverlayBtnText`).textContent = 'None';
                overlayMenu.querySelectorAll('.overlay-menu-item').forEach(i => i.classList.remove('active'));
                overlayMenu.classList.remove('open');
                setOverlay('none');
                if (state.lastSelectedNodeData) showNodeDetails(state.lastSelectedNodeData);
            });
        }
        const overlayGridBtn = document.getElementById(`${MAP_ID}OverlayGrid`);
        const overlayColumnsMenu = document.getElementById(`${MAP_ID}OverlayColumnsMenu`);
        const overlayColumnsOptions = document.getElementById(`${MAP_ID}OverlayColumnsOptions`);
        if (overlayGridBtn && overlayColumnsMenu && overlayColumnsOptions) {
            function populateOverlayColumnsMenu() {
                overlayColumnsOptions.innerHTML = '';
                const ot = state.overlay || '';
                if (!ot || ot === 'none') {
                    overlayColumnsOptions.innerHTML = '<div class="map-overlay-columns-empty">Select an overlay first.</div>';
                    return;
                }
                const columns = window.OverlayColumns ? window.OverlayColumns.getOverlayColumns(ot) : [];
                const selectedIds = getOverlayColumns(ot);
                if (!columns || columns.length === 0) {
                    overlayColumnsOptions.innerHTML = '<div class="map-overlay-columns-empty">No columns for this overlay.</div>';
                    return;
                }
                columns.forEach(col => {
                    const div = document.createElement('div');
                    div.className = 'map-filter-option';
                    const input = document.createElement('input');
                    input.type = 'checkbox';
                    input.id = vcfg.overlayColPrefix + ot + '_' + col.id;
                    input.checked = selectedIds.indexOf(col.id) !== -1;
                    input.dataset.columnId = col.id;
                    const label = document.createElement('label');
                    label.htmlFor = input.id;
                    label.textContent = col.label;
                    div.appendChild(input);
                    div.appendChild(label);
                    input.addEventListener('change', () => {
                        const checked = Array.from(overlayColumnsOptions.querySelectorAll('input:checked')).map(i => i.dataset.columnId);
                        setOverlayColumns(ot, checked);
                    });
                    overlayColumnsOptions.appendChild(div);
                });
            }
            overlayGridBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                if (overlayMenu) overlayMenu.classList.remove('open');
                const isOpen = overlayColumnsMenu.classList.toggle('open');
                if (isOpen) {
                    overlayColumnsMenu.style.display = 'block';
                    populateOverlayColumnsMenu();
                    const rect = overlayGridBtn.getBoundingClientRect();
                    overlayColumnsMenu.style.position = 'fixed';
                    overlayColumnsMenu.style.left = rect.left + 'px';
                    overlayColumnsMenu.style.top = (rect.bottom + 4) + 'px';
                    overlayColumnsMenu.style.minWidth = '200px';
                } else {
                    overlayColumnsMenu.style.display = 'none';
                }
                overlayGridBtn.classList.toggle('active', isOpen);
            });
            document.addEventListener('click', (e) => {
                if (overlayColumnsMenu && overlayGridBtn && !overlayColumnsMenu.contains(e.target) && !overlayGridBtn.contains(e.target)) {
                    overlayColumnsMenu.classList.remove('open');
                    overlayColumnsMenu.style.display = 'none';
                    overlayGridBtn.classList.remove('active');
                }
            });
        }

        const filterBtn = document.getElementById(`${MAP_ID}FilterBtn`);
        const filterMenu = document.getElementById(`${MAP_ID}FilterMenu`);
        const filterLifecycleEl = document.getElementById(`${MAP_ID}FilterLifecycle`);
        const filterBtnTextEl = document.getElementById(`${MAP_ID}FilterBtnText`);
        function updateFilterButtonText() {
            if (!filterBtnTextEl) return;
            const lifecycle = filterLifecycleEl ? filterLifecycleEl.checked : false;
            state.filterLifecycle = lifecycle;
            filterBtnTextEl.textContent = lifecycle ? 'Same lifecycle' : 'All lifecycles';
        }
        if (filterBtn && filterMenu) {
            filterBtn.addEventListener('click', (e) => { e.stopPropagation(); filterMenu.classList.toggle('open'); });
        }
        if (filterLifecycleEl) {
            filterLifecycleEl.addEventListener('change', () => {
                updateFilterButtonText();
                redrawMap();
            });
        }
        updateFilterButtonText();
        document.addEventListener('click', (e) => {
            if (overlayMenu?.contains(e.target) || overlayBtn?.contains(e.target)) return;
            if (filterMenu?.contains(e.target) || filterBtn?.contains(e.target)) return;
            overlayMenu?.classList.remove('open');
            filterMenu?.classList.remove('open');
        });
    }

    function escapeHtml(str) {
        if (!str) return '';
        const div = document.createElement('div');
        div.textContent = str;
        return div.innerHTML;
    }

    function init(processId, processData, thirdArg) {
        const logP = vcfg.logPrefix || '[ProcessFlowMap]';
        if (typeof cytoscape === 'undefined') {
            console.error(logP + ' Cytoscape not loaded');
            return;
        }
        state.processId = processId;
        state.processData = processData || {};

        if (variant === 'components') {
            state.children = Array.isArray(thirdArg) ? thirdArg : [];
            const parent = document.getElementById(vcfg.containerId);
            if (!parent) {
                console.error(logP + ' No container ' + vcfg.containerId);
                return;
            }
            if (!document.getElementById(vcfg.canvasId)) {
                parent.innerHTML = getShellHtml();
            }
            state.canvas = document.getElementById(vcfg.canvasId);
            if (!state.canvas) return;
            if (!state.dagreRegistered) {
                try {
                    const dagre = window.cytoscapeDagre || window['cytoscape-dagre'];
                    if (dagre) {
                        cytoscape.use(dagre);
                        state.dagreRegistered = true;
                    }
                } catch (e) {}
            }
            state.initialized = true;
            setupControls();
            loadMapData();
            return;
        }

        const parentSelector = thirdArg;
        let parent = parentSelector ? document.querySelector(parentSelector) : document.getElementById(vcfg.containerId);
        if (!parent) {
            const contextBody = document.querySelector('#context .collapsible-body');
            if (contextBody) {
                contextBody.innerHTML = getShellHtml();
                parent = document.getElementById(vcfg.containerId);
            }
        }
        if (!parent) {
            console.error(logP + ' No container found');
            return;
        }

        if (!document.getElementById(vcfg.canvasId)) {
            parent.innerHTML = getShellHtml();
        }

        state.canvas = document.getElementById(vcfg.canvasId);
        if (!state.canvas) return;

        if (!state.dagreRegistered) {
            try {
                const dagre = window.cytoscapeDagre || window['cytoscape-dagre'];
                if (dagre) {
                    cytoscape.use(dagre);
                    state.dagreRegistered = true;
                }
            } catch (e) {}
        }

        state.initialized = true;
        setupControls();
        loadMapData();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MapEngine integration: apiLoader / nodeBuilder / edgeBuilder
    //
    // loadMapData() reads state.processId internally.
    // The apiLoader syncs entityId into state, resets the loaded data fields,
    // then invokes the same two fetches (predecessors + successors).
    // processData is pre-loaded by init() and must NOT be cleared here.
    // ─────────────────────────────────────────────────────────────────────────

    var _cachedGraph = null;

    function _getOrBuildGraph() {
        if (!_cachedGraph) { _cachedGraph = buildGraph(); }
        return _cachedGraph;
    }

    async function _mapEngineApiLoader(entityId, mapType, filters) {
        state.processId = entityId;
        _cachedGraph = null;

        if (variant === 'components') {
            state.childRelations.clear();
            const children = state.children || [];
            const childIds = new Set(children.map(c => String(c.id != null ? c.id : c.ID)));
            const promises = [];
            children.forEach(c => {
                const cid = c.id != null ? c.id : c.ID;
                promises.push(
                    fetch(`/api/process-impact/${cid}/predecessors`, { credentials: 'include' }).then(r => r.ok ? r.json() : []).catch(() => []),
                    fetch(`/api/process-impact/${cid}/successors`, { credentials: 'include' }).then(r => r.ok ? r.json() : []).catch(() => [])
                );
            });
            const results = await Promise.all(promises);
            for (let i = 0; i < children.length; i++) {
                const cid = String(children[i].id != null ? children[i].id : children[i].ID);
                let pred = results[i * 2];
                let succ = results[i * 2 + 1];
                if (!Array.isArray(pred)) pred = pred && pred.data ? pred.data : [];
                if (!Array.isArray(succ)) succ = succ && succ.data ? succ.data : [];
                pred = pred.filter(r => {
                    const sid = String(r.sourceProcessId != null ? r.sourceProcessId : r.sourceprocess_id);
                    return sid && childIds.has(sid);
                });
                succ = succ.filter(r => {
                    const tid = String(r.targetProcessId != null ? r.targetProcessId : r.targetprocess_id);
                    return tid && childIds.has(tid);
                });
                state.childRelations.set(cid, { predecessors: pred, successors: succ });
            }
            return { entity: state.processData, _state: state };
        }

        state.predecessors = [];
        state.successors = [];
        const [allPredRels, succResp] = await Promise.all([
            loadPredecessorsRecursive(entityId),
            fetch(`/api/process-impact/${entityId}/successors`, { credentials: 'include' }).catch(() => ({ ok: false }))
        ]);

        state.predecessors = allPredRels;
        state.successors = succResp.ok ? ((await succResp.json()) || []) : [];
        if (!Array.isArray(state.successors)) {
            state.successors = state.successors.data || [];
        }

        return { entity: state.processData, _state: state };
    }

    function _mapEngineNodeBuilder(data) { return _getOrBuildGraph().nodes; }
    function _mapEngineEdgeBuilder(data) { return _getOrBuildGraph().edges; }

    function registerMapEngine() {
        if (window.MapConfigs && window.MapConfigs.register) {
            window.MapConfigs.register(vcfg.mapEngineKey, {
                apiLoader:     _mapEngineApiLoader,
                nodeBuilder:   _mapEngineNodeBuilder,
                edgeBuilder:   _mapEngineEdgeBuilder,
                legendBuilder: getLegendHtml
            });
        }
    }

    return {
        getShellHtml: getShellHtml,
        init: init,
        getLegendHtml: getLegendHtml,
        getState: getState,
        registerMapEngine: registerMapEngine
    };
    }

    window.createProcessFlowMapController = createProcessFlowMapController;
})();
