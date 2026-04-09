/**
 * Project Data Map – Data tab > Data Map sub-tab (Project Facet).
 *
 * Map structure:
 * - Systems added in the project Impact tab (orange).
 * - Systems directly linked to those impact systems (grey), one hop only.
 * - Does NOT show systems linked to those directly linked systems (no 2-hop).
 *   Example: A→B→C, add A in impact → map shows only A and B (not C).
 * - Edges: Solid = attributes specified for the interface; dashed = no attributes.
 *
 * Dataset / Attributes overlay:
 * - For each system: Impact tab datasets/attributes (if impact system) + datasets/attributes
 *   used in interfaces (from getInterfaceDataWithin for solid edges only).
 * - Linked systems show only datasets/attributes used in the interface (e.g. ERP shows
 *   only "Sales Transaction" if that is the dataset used from ERP in the CRM–ERP interface).
 * - Systems with ONLY dashed edges show no datasets/attributes (e.g. Compliance–CRM dashed
 *   shows nothing; Compliance–CDD solid shows attributes from the Compliance–CDD interface).
 *
 * Dependencies: map-graph-utils.js, map-render-utils.js, shared/map/project-data-facet-graph.js (createProjectDataFacetGraphPipeline), system-lineage-network-interactions.js (createProjectDataMapNetworkInteractions).
 */
(function() {
    'use strict';

    const MAP_ID = 'projectDataMap';
    const ProjectDataMapState = {
        initialized: false,
        network: null,
        canvas: null,
        projectId: null,
        layout: 'top-to-bottom',
        overlay: 'none',
        overlayColumnsByType: {},
        hopsCount: 15,
        /** System IDs from project Impact > Systems – shown in orange */
        impactSystemIds: new Set(),
        /** Systems directly linked to impact systems (1 hop only) – shown in grey */
        linkedSystemIds: new Set(),
        systemsData: new Map(),
        interfacesData: [],
        dataFlowData: [],
        /** systemId -> [{ id, name, ref }] from Impact > Datasets (for overlay) */
        impactSystemIdToDatasets: new Map(),
        /** systemId -> [{ id, name, ref }] from Impact > Attributes (for overlay) */
        impactSystemIdToAttributes: new Map(),
        /** systemId -> [{ id, name, ref }] datasets used in solid-line interfaces only */
        interfaceDatasetsBySystem: new Map(),
        /** systemId -> [{ id, name, ref }] attributes used in solid-line interfaces only */
        interfaceAttributesBySystem: new Map(),
        /** Attribute IDs that appear in any relationship (interface data-within or dataset-relationships) – for Linking Attributes overlay */
        attributeIdsWithLinks: new Set(),
        /** System IDs that have at least one solid edge (interface with attributes). Datasets/Attributes overlay shows nothing for systems with only dashed edges. */
        systemIdsWithSolidEdge: new Set(),
        /** nodeId -> { systemId, isImpact } for overlay */
        nodeMeta: new Map(),
        /** nodeId set for "Hide object" */
        hiddenNodes: new Set(),
        /** System IDs in segments user cannot access – show lock */
        inaccessibleSystems: new Set(),
        /** Edge labels visible (fullscreen / toolbar sync) */
        showEdgeLabels: true,
        nodeFilters: {
            classifications: [],
            types: [],
            lifecycles: []
        },
        filtersInitialized: false,
        projectFilterTypeUiActive: false,
        projectFilterLifecycleUiActive: false
    };

    const adapter = window.createMapAdapter(ProjectDataMapState, MAP_ID, { zoomRecenter: false });

    var graphPipeline = null;

    async function loadMapData() {
        return graphPipeline.loadMapData();
    }

    function buildSystemLineageGraph() {
        return graphPipeline.buildSystemLineageGraph();
    }

    var networkIx = null;

    // Colors from shared palette (map-render-utils.js)
    const _pal         = window.MapRenderUtils.MAP_PALETTE;
    const ORANGE        = _pal.orange        || '#f97316';
    const ORANGE_BORDER = _pal.orangeBorder  || '#ea580c';
    const GREY          = _pal.grey          || '#6b7280';
    const GREY_BORDER   = _pal.greyBorder    || '#4b5563';
    const EDGE_LINE     = _pal.edgeLine      || '#64748b';
    const EDGE_DASHED   = _pal.edgeDashed    || '#94a3b8';

    function getMapHtml() {
        return `
            <div class="interface-map-container project-data-map-wrapper" id="${MAP_ID}Wrapper">
                <div class="interface-map-toolbar">
                    <div class="map-control-group">
                        <label>Map type:</label>
                        <select id="${MAP_ID}TypeSelect" class="map-select">
                            <option value="system-lineage" selected>System Lineage</option>
                        </select>
                    </div>
                    <div class="map-control-group">
                        <label>Layout:</label>
                        <div class="map-layout-controls">
                            ${typeof window.SharedMapLayoutRichControlsHtml === 'function' ? window.SharedMapLayoutRichControlsHtml(MAP_ID) : ''}
                        </div>
                    </div>
                    <div class="map-control-group map-hops-group" id="${MAP_ID}HopsGroup">
                        <label>Hops:</label>
                        <input type="number" id="${MAP_ID}HopsCount" class="map-hops-input" min="1" max="99" value="15" title="Upstream/downstream lineage depth (1-99, recommend 15)">
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
                                        <div class="overlay-menu-column" data-overlay-column="data">
                                            <div class="overlay-menu-header">Data</div>
                                            <div class="overlay-menu-item" data-overlay="description"><i class="fas fa-info-circle"></i> Description</div>
                                            <div class="overlay-menu-item" data-overlay="glossary"><i class="fas fa-book"></i> Glossary</div>
                                            <div class="overlay-menu-item" data-overlay="datasets"><i class="fas fa-layer-group"></i> Data Sets</div>
                                            <div class="overlay-menu-item" data-overlay="attributes"><i class="fas fa-th"></i> Attributes</div>
                                            <div class="overlay-menu-item" data-overlay="linking-attributes"><i class="fas fa-link"></i> Linking Attributes</div>
                                            <div class="overlay-menu-item" data-overlay="data-quality"><i class="fas fa-bullseye"></i> Data Quality</div>
                                            <div class="overlay-menu-item" data-overlay="data-privacy"><i class="fas fa-lock"></i> Data Privacy</div>
                                        </div>
                                        <div class="overlay-menu-column" data-overlay-column="business">
                                            <div class="overlay-menu-header">Business</div>
                                            <div class="overlay-menu-item" data-overlay="stakeholders"><i class="fas fa-users"></i> Stakeholders</div>
                                            <div class="overlay-menu-item" data-overlay="processes"><i class="fas fa-play"></i> Processes</div>
                                            <div class="overlay-menu-item" data-overlay="projects"><i class="fas fa-project-diagram"></i> Projects</div>
                                            <div class="overlay-menu-item" data-overlay="policies"><i class="fas fa-file-alt"></i> Policies</div>
                                        </div>
                                        <div class="overlay-menu-column" data-overlay-column="organizational">
                                            <div class="overlay-menu-header">Organizational</div>
                                            <div class="overlay-menu-item" data-overlay="business-area"><i class="fas fa-briefcase"></i> Business Area</div>
                                            <div class="overlay-menu-item" data-overlay="products"><i class="fas fa-tag"></i> Products</div>
                                            <div class="overlay-menu-item" data-overlay="legal-entities"><i class="fas fa-landmark"></i> Legal Entities</div>
                                        </div>
                                        <div class="overlay-menu-column" data-overlay-column="regulatory">
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
                            <div class="map-overlay-columns-menu map-filter-menu" id="${MAP_ID}OverlayColumnsMenu" style="display: none;">
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
                                <span id="${MAP_ID}FilterBtnText">All selected</span>
                                <i class="fas fa-chevron-down"></i>
                            </button>
                            <div class="map-filter-menu" id="${MAP_ID}FilterMenu" data-map-type="system-lineage">
                                <div class="map-filter-category">
                                    <div class="map-filter-category-header">TYPE</div>
                                    <div id="${MAP_ID}FilterTypeOptions"></div>
                                </div>
                                <div class="map-filter-separator"></div>
                                <div class="map-filter-category">
                                    <div class="map-filter-category-header">LIFECYCLE</div>
                                    <div id="${MAP_ID}FilterLifecycleOptions"></div>
                                </div>
                            </div>
                        </div>
                    </div>
                    <div class="map-toolbar-buttons">
                        <button type="button" class="map-toolbar-btn" id="${MAP_ID}ToggleLabels" title="Toggle labels">
                            <i class="fas fa-exchange-alt"></i>
                        </button>
                        <div class="map-toolbar-separator"></div>
                        <button type="button" class="map-toolbar-btn" id="${MAP_ID}ZoomIn" title="Zoom in"><i class="fas fa-search-plus"></i></button>
                        <button type="button" class="map-toolbar-btn" id="${MAP_ID}ZoomOut" title="Zoom out"><i class="fas fa-search-minus"></i></button>
                        <div class="map-toolbar-separator"></div>
                        <button type="button" class="map-toolbar-btn" id="${MAP_ID}Redraw" title="Redraw"><i class="fas fa-sync-alt"></i></button>
                        <button type="button" class="map-toolbar-btn" id="${MAP_ID}Reset" title="Reset"><i class="fas fa-undo"></i></button>
                        <button type="button" class="map-toolbar-btn" id="${MAP_ID}Export" title="Export as PNG"><i class="fas fa-save"></i></button>
                        <button type="button" class="map-toolbar-btn" id="${MAP_ID}Navigator" title="Map navigator">
                            <i class="fas fa-eye"></i>
                        </button>
                        <div class="map-toolbar-separator"></div>
                        <button type="button" class="map-toolbar-btn" id="${MAP_ID}Fullscreen" title="Fullscreen"><i class="fas fa-external-link-alt"></i></button>
                        <button type="button" class="map-toolbar-btn" id="${MAP_ID}Legend" title="Open the Legend" aria-label="Open the Legend – refer to the legend to identify the symbols used in the map (Insight Maps Palette)"><i class="fas fa-list-ul"></i></button>
                    </div>
                </div>
                <div class="interface-map-body">
                    <div style="flex:1;display:flex;flex-direction:column;min-width:0;">
                        <div class="map-legend-bar" data-project-data-map-legend></div>
                        <div class="interface-map-canvas" id="${MAP_ID}Canvas">
                            <div class="interface-map-loading" data-project-data-map-loading style="display:none;">
                                <i class="fas fa-spinner fa-spin"></i>
                                <span>Building data map...</span>
                            </div>
                        </div>
                    </div>
                    <div class="interface-map-side-panel" id="${MAP_ID}SidePanel" style="display:none;">
                        <div class="map-side-panel-section">
                            <h4 class="map-legend-palette-title"><i class="fas fa-layer-group"></i> Insight Maps Palette</h4>
                            <div data-project-data-map-legend-panel></div>
                        </div>
                    </div>
                </div>
            </div>
        `;
    }

    function showLoading() {
        const el = document.querySelector('[data-project-data-map-loading]');
        if (el) el.style.display = 'block';
    }

    function hideLoading() {
        const el = document.querySelector('[data-project-data-map-loading]');
        if (el) el.style.display = 'none';
    }

    function showPlaceholder(message) {
        clearProjectDataMapFilterOptions();
        if (ProjectDataMapState.canvas) {
            ProjectDataMapState.canvas.innerHTML = window.MapRenderUtils.htmlMapCanvasMessage(message || 'No data to display.');
        }
        updateProjectDataMapLegend(false);
    }

    function toggleSidePanel() {
        const panel = document.getElementById(MAP_ID + 'SidePanel');
        const legendBtn = document.getElementById(MAP_ID + 'Legend');
        if (!panel || !legendBtn) return;
        const isVisible = panel.style.display !== 'none';
        panel.style.display = isVisible ? 'none' : 'flex';
        legendBtn.classList.toggle('active', !isVisible);
    }

    function getLegendHtml() {
        return `
            <div class="legend-item"><div class="legend-color" style="width: 14px; height: 14px; border-radius: 4px; background: ${ORANGE}; border: 1px solid ${ORANGE_BORDER};"></div><span>Impact system</span></div>
            <div class="legend-item"><div class="legend-color" style="width: 14px; height: 14px; border-radius: 4px; background: ${GREY}; border: 1px solid ${GREY_BORDER};"></div><span>Linked system</span></div>
            <div class="legend-item"><span class="legend-lock" aria-hidden="true">&#x1F512;</span><span>Segment not accessible</span></div>
            <div class="legend-item"><div class="legend-line" style="width: 24px; border-bottom: 2px solid ${EDGE_LINE};"></div><span>Attributes specified</span></div>
            <div class="legend-item"><div class="legend-line" style="width: 24px; border-bottom: 2px dashed ${EDGE_DASHED};"></div><span>No attributes</span></div>
        `;
    }

    /** Update the legend bar below the toolbar (same pattern as other maps). */
    function updateProjectDataMapLegend(hasData) {
        const el = document.querySelector('[data-project-data-map-legend]');
        const panelEl = document.querySelector('[data-project-data-map-legend-panel]');
        if (!el) return;
        if (!hasData) {
            el.innerHTML = '';
            if (panelEl) panelEl.innerHTML = '';
            return;
        }
        const barHtml = `
            <span class="legend-item" style="display: inline-flex; align-items: center; gap: 0.35rem;">
                <span class="legend-color" style="width: 14px; height: 14px; border-radius: 4px; background: ${ORANGE}; border: 1px solid ${ORANGE_BORDER};"></span>
                <span>Impact system</span>
            </span>
            <span class="legend-item" style="display: inline-flex; align-items: center; gap: 0.35rem;">
                <span class="legend-color" style="width: 14px; height: 14px; border-radius: 4px; background: ${GREY}; border: 1px solid ${GREY_BORDER};"></span>
                <span>Linked system</span>
            </span>
            <span class="legend-item" style="display: inline-flex; align-items: center; gap: 0.35rem;">
                <span class="legend-lock" aria-hidden="true">&#x1F512;</span>
                <span>Segment not accessible</span>
            </span>
            <span class="legend-item" style="display: inline-flex; align-items: center; gap: 0.35rem;">
                <span class="legend-line" style="width: 24px; border-bottom: 2px solid ${EDGE_LINE};"></span>
                <span>Attributes specified</span>
            </span>
            <span class="legend-item" style="display: inline-flex; align-items: center; gap: 0.35rem;">
                <span class="legend-line" style="width: 24px; border-bottom: 2px dashed ${EDGE_DASHED};"></span>
                <span>No attributes</span>
            </span>
        `;
        el.innerHTML = barHtml;
        const panelHtml = getLegendHtml();
        if (panelEl) panelEl.innerHTML = panelHtml;
        const dropdown = document.getElementById(MAP_ID + 'LegendDropdown');
        if (dropdown) dropdown.innerHTML = panelHtml;
    }

    function getProjectMapRoot() {
        return document.getElementById(MAP_ID + 'Wrapper') || document.getElementById(MAP_ID + 'Section');
    }

    function clearProjectDataMapFilterOptions() {
        const t = document.getElementById(MAP_ID + 'FilterTypeOptions');
        const l = document.getElementById(MAP_ID + 'FilterLifecycleOptions');
        if (t) t.innerHTML = '';
        if (l) l.innerHTML = '';
        ProjectDataMapState.projectFilterTypeUiActive = false;
        ProjectDataMapState.projectFilterLifecycleUiActive = false;
        ProjectDataMapState.filtersInitialized = false;
        ProjectDataMapState.nodeFilters = { classifications: [], types: [], lifecycles: [] };
        updateProjectDataMapFilterButtonText();
    }

    function fillProjectFilterCheckboxGroup(containerId, values, idPrefix) {
        const container = document.getElementById(containerId);
        if (!container) return;
        container.innerHTML = '';
        const sorted = [...values].filter(Boolean).sort(function (a, b) { return String(a).localeCompare(String(b)); });
        sorted.forEach(function (val, idx) {
            const safe = String(val).replace(/[^a-zA-Z0-9_-]/g, '_');
            const id = MAP_ID + '_' + idPrefix + '_' + idx + '_' + safe;
            const div = document.createElement('div');
            div.className = 'map-filter-option';
            const input = document.createElement('input');
            input.type = 'checkbox';
            input.id = id;
            input.value = val;
            input.checked = true;
            const label = document.createElement('label');
            label.htmlFor = id;
            label.textContent = val;
            div.appendChild(input);
            div.appendChild(label);
            container.appendChild(div);
        });
    }

    function populateProjectDataMapFilterOptions() {
        const extract = window.MapGraphUtils && typeof window.MapGraphUtils.extractSystemMeta === 'function'
            ? window.MapGraphUtils.extractSystemMeta : null;
        if (!extract) return;
        const types = new Set();
        const lifes = new Set();
        ProjectDataMapState.systemsData.forEach(function (sys, sid) {
            const m = extract(sys, sid);
            if (m.type) types.add(m.type);
            if (m.lifecycle) lifes.add(m.lifecycle);
        });
        ProjectDataMapState.projectFilterTypeUiActive = types.size > 0;
        ProjectDataMapState.projectFilterLifecycleUiActive = lifes.size > 0;
        fillProjectFilterCheckboxGroup(MAP_ID + 'FilterTypeOptions', types, 'typ');
        fillProjectFilterCheckboxGroup(MAP_ID + 'FilterLifecycleOptions', lifes, 'life');
    }

    function readProjectFilterChecked(suffix) {
        const c = document.getElementById(MAP_ID + suffix);
        if (!c) return [];
        return Array.prototype.map.call(c.querySelectorAll('input[type="checkbox"]:checked'), function (i) { return i.value; });
    }

    function updateProjectDataMapFilterButtonText() {
        const el = document.getElementById(MAP_ID + 'FilterBtnText');
        if (!el) return;
        const menu = document.getElementById(MAP_ID + 'FilterMenu');
        if (!menu) {
            el.textContent = 'All selected';
            return;
        }
        const visible = Array.from(menu.querySelectorAll('input[type="checkbox"]')).filter(function (c) {
            return (c.closest('.map-filter-option') || {}).style.display !== 'none';
        });
        const checked = visible.filter(function (c) { return c.checked; });
        if (visible.length === 0) el.textContent = 'No filters';
        else if (checked.length === visible.length) el.textContent = 'All selected (' + visible.length + ')';
        else el.textContent = checked.length + ' of ' + visible.length;
    }

    /** Fetch system by ID without logging or throwing on 404. On 403 (segment not accessible), track as inaccessible and return placeholder so node shows with lock. */



    function getCytoscapeStyle() {
        return [
            {
                selector: 'node',
                style: {
                    'label': 'data(label)',
                    'text-valign': 'bottom',
                    'text-halign': 'center',
                    'text-margin-y': 10,
                    'background-opacity': 1,
                    'background-color': 'data(nodeColor)',
                    'border-color': 'data(borderColor)',
                    'border-width': 2,
                    'background-image': 'data(backgroundImage)',
                    'background-fit': 'contain',
                    'background-width': '50%',
                    'background-height': '50%',
                    'background-position-y': '30%',
                    'text-wrap': 'wrap',
                    'text-max-width': 150,
                    'font-size': 12,
                    'shape': 'round-rectangle',
                    'width': 100,
                    'height': 60
                }
            },
            {
                selector: 'edge',
                style: {
                    'width': 2,
                    'line-color': EDGE_LINE,
                    'target-arrow-color': EDGE_LINE,
                    'curve-style': (window._sharedDropdownApis && window._sharedDropdownApis[MAP_ID] && typeof window._sharedDropdownApis[MAP_ID].getCurveStyle === 'function') ? window._sharedDropdownApis[MAP_ID].getCurveStyle() : 'bezier',
                    'target-arrow-shape': 'triangle',
                    'arrow-scale': 1
                }
            },
            {
                selector: 'edge[dashes="true"]',
                style: { 'line-style': 'dashed', 'line-color': EDGE_DASHED, 'target-arrow-color': EDGE_DASHED }
            },
            {
                selector: 'edge.reversed-edge',
                style: {
                    'target-arrow-shape': 'none',
                    'source-arrow-shape': 'triangle',
                    'source-arrow-color': EDGE_LINE
                }
            },
            {
                selector: 'edge.reversed-edge[lineColor]',
                style: {
                    'source-arrow-color': 'data(lineColor)'
                }
            },
            {
                selector: 'node[isLocked = true]',
                style: { 'background-color': GREY, 'border-color': GREY_BORDER, 'opacity': 0.8 }
            }
        ];
    }

    function renderNetwork(graph) {
        if (!ProjectDataMapState.canvas) return;

        if (graphPipeline && graphPipeline.invalidateGraphCache) {
            graphPipeline.invalidateGraphCache();
        }

        if (graph.nodes.length === 0) {
            showPlaceholder('No systems in Impact tab. Add systems in Impact > System, or add records in Impact > Data Sets or Data Attributes (systems from those sub-tabs also appear on the map). Orange = impact systems; grey = systems directly linked to them (1 hop only).');
            hideLoading();
            return;
        }

        if (ProjectDataMapState.network) {
            ProjectDataMapState.network.destroy();
            ProjectDataMapState.network = null;
        }

        const elements = [];
        const currentNodeIds = new Set(graph.nodes.filter(n => n.isImpact).map(n => n.id));
        const extract = window.MapGraphUtils && typeof window.MapGraphUtils.extractSystemMeta === 'function'
            ? window.MapGraphUtils.extractSystemMeta : null;
        graph.nodes.forEach(n => {
            const isLocked = n.isLocked || ProjectDataMapState.inaccessibleSystems.has(String(n.id));
            let meta = {};
            if (extract && ProjectDataMapState.systemsData.has(n.id)) {
                meta = extract(ProjectDataMapState.systemsData.get(n.id), n.id) || {};
            }
            elements.push({
                data: {
                    id: n.id,
                    label: (isLocked ? '\u{1F512} ' : '') + (n.label || ''),
                    nodeColor: n.nodeColor,
                    borderColor: n.borderColor,
                    backgroundImage: n.backgroundImage || adapter.getSystemIcon(n.isImpact),
                    isLocked: !!isLocked,
                    isCurrent: !!n.isImpact,
                    meta: meta
                }
            });
        });
        graph.edges.forEach(e => {
            const targetIsCurrent = currentNodeIds.has(e.to);
            const sourceIsCurrent = currentNodeIds.has(e.from);
            const reverseEdge = targetIsCurrent && !sourceIsCurrent;
            const source = reverseEdge ? e.to : e.from;
            const target = reverseEdge ? e.from : e.to;
            const lineColor = e.dashes ? EDGE_DASHED : EDGE_LINE;
            elements.push({
                data: {
                    id: e.id,
                    source: source,
                    target: target,
                    dashes: e.dashes,
                    lineColor: reverseEdge ? lineColor : undefined,
                    reversed: reverseEdge
                },
                classes: reverseEdge ? 'reversed-edge' : ''
            });
        });

        ProjectDataMapState.network = cytoscape({
            container: ProjectDataMapState.canvas,
            elements: elements,
            style: getCytoscapeStyle(),
            layout: adapter.buildLayoutOptions(),
            minZoom: 0.3,
            maxZoom: 3
        });
        ProjectDataMapState.network.on('layoutstop', function() {
            ProjectDataMapState.network.fit(50);
        });
        networkIx.attachToCurrentNetwork();
        ProjectDataMapState.nodeMeta.clear();
        graph.nodes.forEach(n => {
            const id = n.id;
            ProjectDataMapState.nodeMeta.set(id, { systemId: id, isImpact: n.isImpact });
        });
        clearOverlayPanels();
        if (ProjectDataMapState.overlay && ProjectDataMapState.overlay !== 'none') loadOverlayData(ProjectDataMapState.overlay);
        updateProjectDataMapLegend(true);
        refreshProjectDataMapFiltersAfterRender();
        hideLoading();
    }

    function clearOverlayPanels() {
        if (!ProjectDataMapState.canvas) return;
        const overlayContainer = ProjectDataMapState.canvas.querySelector('.map-overlay-container');
        if (overlayContainer) overlayContainer.innerHTML = '';
        if (ProjectDataMapState.network) {
            try { ProjectDataMapState.network.off('drag', 'node', updateOverlayPositions); } catch (e) { /* no-op */ }
        }
    }

    function positionOverlayPanelElement(panel, node) {
        const overlayContainer = ProjectDataMapState.canvas?.querySelector('.map-overlay-container');
        const network = ProjectDataMapState.network;
        if (!overlayContainer || !node || !network) return;

        const position = node.renderedPosition();
        const bb = node.boundingBox();
        const zoom = network.zoom();
        const panelHeight = panel.offsetHeight || 180;
        const panelWidth = panel.offsetWidth || 220;
        const margin = 12;
        const renderedNodeH = (bb.h || 60) * zoom;

        let left = position.x - panelWidth / 2;
        let top = position.y - renderedNodeH / 2 - panelHeight - margin;

        panel.style.left = `${left}px`;
        panel.style.top = `${top}px`;
        panel.style.transform = 'translate(0, 0)';
    }

    function updateOverlayPositions() {
        const overlayContainer = ProjectDataMapState.canvas?.querySelector('.map-overlay-container');
        if (!overlayContainer || !ProjectDataMapState.network) return;
        const cw = overlayContainer.clientWidth || 800;
        const ch = overlayContainer.clientHeight || 600;
        const buf = 150;

        overlayContainer.querySelectorAll('.map-node-overlay-panel').forEach(panel => {
            const nodeId = panel.getAttribute('data-node-id');
            const node = ProjectDataMapState.network.getElementById(nodeId);
            
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

    function applyProjectNodeFiltersFromState() {
        if (!ProjectDataMapState.network || !window.MapGraphUtils) return;
        window.MapGraphUtils.applySystemNodeFilters(ProjectDataMapState.network, ProjectDataMapState.nodeFilters, {
            filtersInitialized: ProjectDataMapState.filtersInitialized === true,
            filterDimensionActive: {
                classification: false,
                type: ProjectDataMapState.projectFilterTypeUiActive,
                lifecycle: ProjectDataMapState.projectFilterLifecycleUiActive
            },
            updateOverlayPositions: updateOverlayPositions
        });
    }

    function syncProjectNodeFiltersFromDomAndApply() {
        ProjectDataMapState.nodeFilters = {
            classifications: [],
            types: readProjectFilterChecked('FilterTypeOptions'),
            lifecycles: readProjectFilterChecked('FilterLifecycleOptions')
        };
        ProjectDataMapState.filtersInitialized = true;
        applyProjectNodeFiltersFromState();
        updateProjectDataMapFilterButtonText();
    }

    function refreshProjectDataMapFiltersAfterRender() {
        populateProjectDataMapFilterOptions();
        ProjectDataMapState.nodeFilters = {
            classifications: [],
            types: readProjectFilterChecked('FilterTypeOptions'),
            lifecycles: readProjectFilterChecked('FilterLifecycleOptions')
        };
        ProjectDataMapState.filtersInitialized = true;
        applyProjectNodeFiltersFromState();
        updateProjectDataMapFilterButtonText();
    }

    function getOverlayTitle(overlayType) {
        const titles = {
            description: 'Description', glossary: 'Glossaries', datasets: 'Data Sets', dataset: 'Data Sets',
            attributes: 'Attributes', 'linking-attributes': 'Linking Attributes',
            'data-quality': 'Data Quality', 'data-privacy': 'Data Privacy',
            stakeholders: 'Stakeholders', processes: 'Processes', projects: 'Projects', policies: 'Policies',
            'business-area': 'Business Area', products: 'Products', 'legal-entities': 'Legal Entities', geography: 'Geography'
        };
        return titles[overlayType] || overlayType || 'Overlay';
    }

    function escapeOverlayHtml(str) {
        if (str == null) return '';
        const div = document.createElement('div');
        div.textContent = String(str);
        return div.innerHTML;
    }

    function getGlossarySourceLabel(item) {
        if (!item || !item.source) return '';
        if (item.source === 'dataset') return 'Dataset Glossary';
        if (item.source === 'attribute') return 'Attribute Glossary';
        if (item.source === 'system') return 'System Glossary';
        return '';
    }

    function getDataMapOverlayItemText(overlayType, item) {
        if (!item) return '';
        switch (overlayType) {
            case 'description': return item.value || item.description || '';
            case 'stakeholders':
                const role = item.roleName || item.role || '';
                const name = item.personName || item.name || '';
                return role ? (name ? name + ' (' + role + ')' : role) : name;
            case 'processes': return item.processName || item.PrimaryName || item.primaryName || item.name || item.Name || '';
            case 'projects': return item.projectName || item.PrimaryName || item.primaryName || item.name || item.Name || '';
            case 'policies': return item.policyName || item.PrimaryName || item.primaryName || item.name || item.Name || '';
            case 'business-area': return item.businessAreaName || item.PrimaryName || item.primaryName || item.name || item.Name || '';
            case 'products': return item.productName || item.PrimaryName || item.primaryName || item.name || item.Name || '';
            case 'legal-entities': return item.legalEntityName || item.longName || item.longname || item.name || item.Name || '';
            case 'data-quality': return item.ruleName || item.name || item.qualityRating || (item.rating != null ? 'Rating: ' + item.rating : '') || (item.score != null ? 'Score: ' + item.score : '') || '';
            case 'data-privacy': return item.privacyClassification || item.classification || item.name || item.value || '';
            case 'geography': return item.name || item.region || item.country || '';
            case 'glossary': {
                const gName = item.glossaryName || item.name || '';
                const gRef = item.glossaryRefNumber || item.refNumber || '';
                const srcLabel = getGlossarySourceLabel(item);
                return gName + (gRef ? ' (' + gRef + ')' : '') + (srcLabel ? ' (' + srcLabel + ')' : '');
            }
            case 'datasets': case 'dataset': return item.name || item.datasetName || item.primaryName || '';
            case 'attributes': return item.name || item.attributeName || item.primaryName || '';
            default: return item.name || item.Name || item.primaryName || item.PrimaryName || '';
        }
    }

    function getOverlayItemId(overlayType, item) {
        if (!item) return null;
        return item.id != null ? item.id : (item.ID != null ? item.ID : (item.Id != null ? item.Id : null));
    }

    function getOverlayItemField(overlayType, item, fieldId) {
        if (!item) return '';
        const v = (x) => (x != null && x !== '') ? String(x) : '';
        switch (overlayType) {
            case 'glossary':
                switch (fieldId) {
                    case 'name': return v(item.glossary || item.name);
                    case 'source': return item.source === 'dataset' ? 'Dataset Glossary' : item.source === 'attribute' ? 'Attribute Glossary' : item.source === 'system' ? 'System Glossary' : '';
                    case 'aliasNames': return v(item.aliasNames);
                    case 'parentName': return v(item.parentName);
                    case 'lifecycle': return v(item.lifecycle);
                    case 'securityClassification': return v(item.securityClassification);
                    default: return v(item[fieldId]);
                }
            case 'attributes': case 'linking-attributes':
                switch (fieldId) {
                    case 'name': return v(item.name || item.attributeName);
                    case 'dataType': return v(item.dataType);
                    case 'lifecycle': return v(item.lifecycle);
                    case 'securityClassification': return v(item.securityClassification);
                    default: return v(item[fieldId]);
                }
            case 'stakeholders':
                switch (fieldId) {
                    case 'name': { const n = item.personName || item.name || ''; const r = item.roleName || item.role || ''; return r ? (n ? n + ' (' + r + ')' : r) : n; }
                    case 'accepted': return v(item.accepted || item.Accepted || item.acceptedStatus);
                    case 'orgUnit': return v(item.orgUnit || item.OrgUnit || item.orgUnitName);
                    default: return v(item[fieldId]);
                }
            default:
                return v(item[fieldId] || item.name || item.Name || '');
        }
    }

    function highlightOverlayItem(overlayType, item, sourceNodeId) {
        const canvas = ProjectDataMapState.canvas;
        const overlayContainer = canvas?.querySelector('.map-overlay-container');
        if (!overlayContainer) return;
        const itemId = getOverlayItemId(overlayType, item);
        const itemText = getDataMapOverlayItemText(overlayType, item);
        overlayContainer.querySelectorAll('.map-node-overlay-item').forEach(el => {
            el.classList.remove('highlighted-source', 'highlighted-related');
        });
        overlayContainer.querySelectorAll('.map-node-overlay-item').forEach(el => {
            const panel = el.closest('.map-node-overlay-panel');
            const isSource = panel?.getAttribute('data-node-id') === sourceNodeId;
            let shouldHighlight = false;
            if (itemId && el.dataset.itemId && String(el.dataset.itemId) === String(itemId)) shouldHighlight = true;
            if (!shouldHighlight && (el.dataset.overlayValue || '').trim() === (itemText || '').trim()) shouldHighlight = true;
            if (shouldHighlight) {
                if (isSource) el.classList.add('highlighted-source');
                else el.classList.add('highlighted-related');
            }
        });
        applyOverlayRelatedNodeAndEdgeHighlights(overlayContainer, sourceNodeId);
    }

    function applyOverlayRelatedNodeAndEdgeHighlights(overlayContainer, sourceNodeId) {
        const network = ProjectDataMapState.network;
        if (!network) return;
        network.elements().removeClass('overlay-highlight-source-node overlay-highlight-related-node overlay-highlight-edge');
        const relatedNodeIds = new Set();
        overlayContainer.querySelectorAll('.map-node-overlay-item.highlighted-source, .map-node-overlay-item.highlighted-related').forEach(el => {
            const panel = el.closest('.map-node-overlay-panel');
            const nodeId = panel?.getAttribute('data-node-id');
            if (nodeId) relatedNodeIds.add(nodeId);
        });
        relatedNodeIds.forEach(nodeId => {
            const node = network.getElementById(nodeId);
            if (node.length) node.addClass(nodeId === sourceNodeId ? 'overlay-highlight-source-node' : 'overlay-highlight-related-node');
        });
        network.edges().forEach(edge => {
            const src = edge.source().id();
            const tgt = edge.target().id();
            if (relatedNodeIds.has(src) && relatedNodeIds.has(tgt)) edge.addClass('overlay-highlight-edge');
        });
    }

    async function fetchOverlayForSystemNode(systemId, overlayType) {
        const API = window.BUDG_API_SERVICE;
        if (!API) return [];
        try {
            switch (overlayType) {
                case 'description': {
                    const sys = await fetchSystemByIdSafe(systemId);
                    const s = sys?.data || sys;
                    return (s?.description ? [{ name: 'Description', value: s.description }] : []);
                }
                case 'stakeholders': {
                    const res = await API.getSystemStakeholders?.(systemId).catch(() => null);
                    const arr = Array.isArray(res?.data) ? res.data : (Array.isArray(res) ? res : []);
                    return arr;
                }
                case 'processes': {
                    const r = await fetch(`/api/process-impact/systems/${systemId}/processes`, { credentials: 'include' });
                    const json = r.ok ? await r.json() : null;
                    return Array.isArray(json?.data) ? json.data : (Array.isArray(json) ? json : []);
                }
                case 'projects': {
                    const r = await fetch(`/api/project-impact/systems/${systemId}/projects`, { credentials: 'include' });
                    const json = r.ok ? await r.json() : null;
                    return Array.isArray(json?.data) ? json.data : (Array.isArray(json) ? json : []);
                }
                case 'policies': {
                    const r = await fetch(`/api/policy-impact/systems/${systemId}/policies`, { credentials: 'include' });
                    const json = r.ok ? await r.json() : null;
                    return Array.isArray(json?.data) ? json.data : (Array.isArray(json) ? json : []);
                }
                case 'business-area': {
                    const r = await fetch(`/api/businessarea-impact/systems/${systemId}/businessareas`, { credentials: 'include' });
                    const json = r.ok ? await r.json() : null;
                    return Array.isArray(json?.data) ? json.data : (Array.isArray(json) ? json : []);
                }
                case 'products': {
                    const r = await fetch(`/api/system-impact/${systemId}/products`, { credentials: 'include' });
                    const json = r.ok ? await r.json() : null;
                    return Array.isArray(json?.data) ? json.data : (Array.isArray(json) ? json : []);
                }
                case 'legal-entities': {
                    const r = await fetch(`/api/system-impact/${systemId}/legals`, { credentials: 'include' });
                    const json = r.ok ? await r.json() : null;
                    return Array.isArray(json?.data) ? json.data : (Array.isArray(json) ? json : []);
                }
                case 'data-quality': {
                    const r = await fetch(`/api/data-quality/system/${systemId}`, { credentials: 'include' });
                    const json = r.ok ? await r.json() : null;
                    return Array.isArray(json?.data) ? json.data : (Array.isArray(json) ? json : []);
                }
                case 'data-privacy': {
                    const r = await fetch(`/api/data-privacy/system/${systemId}`, { credentials: 'include' });
                    if (r.ok) {
                        const json = await r.json();
                        return Array.isArray(json?.data) ? json.data : (Array.isArray(json) ? json : []);
                    }
                    const sys = await fetchSystemByIdSafe(systemId);
                    const s = sys?.data || sys;
                    if (s?.privacyClassification || s?.dataPrivacy || s?.sensitivity)
                        return [{ name: s.privacyClassification || s.dataPrivacy || s.sensitivity, type: 'privacy' }];
                    return [];
                }
                case 'geography': {
                    const sys = await fetchSystemByIdSafe(systemId);
                    const s = sys?.data || sys;
                    if (s?.geography || s?.region || s?.country)
                        return [{ name: s.geography || s.region || s.country, type: 'geography' }];
                    return [];
                }
                default: return [];
            }
        } catch (e) { return []; }
    }

    function formatDataMapOverlayHtml(overlayType, data) {
        if (data == null) return '<p class="map-overlay-empty">No data.</p>';
        if (overlayType === 'description') {
            const list = Array.isArray(data) ? data : (data.items || []);
            const first = list[0];
            const text = first ? (first.value || first.description || '') : '';
            if (!text) return '<p class="map-overlay-empty">No description.</p>';
            return '<div class="map-node-overlay-item map-overlay-description">' + escapeOverlayHtml(text) + '</div>';
        }
        if (overlayType === 'glossary') {
            const list = Array.isArray(data) ? data : (data.items || []);
            const items = Array.isArray(list) ? list : [];
            if (!items.length) return '<p class="map-overlay-empty">No glossary terms.</p>';
            const rows = items.slice(0, 20).map(r => {
                const name = (r && (r.glossaryName || r.name || r.Name)) || '-';
                const ref = r && (r.glossaryRefNumber || r.refNumber || '');
                const srcLabel = getGlossarySourceLabel(r);
                const srcHtml = srcLabel ? ' <span class="map-overlay-ref">(' + escapeOverlayHtml(srcLabel) + ')</span>' : '';
                return '<div class="map-node-overlay-item">' + escapeOverlayHtml(name) + (ref ? ' <span class="map-overlay-ref">' + escapeOverlayHtml(ref) + '</span>' : '') + srcHtml + '</div>';
            });
            const more = items.length > 20 ? '<div class="map-overlay-more">+' + (items.length - 20) + ' more</div>' : '';
            return rows.join('') + more;
        }
        const list = Array.isArray(data) ? data : (data.items || data);
        const items = Array.isArray(list) ? list : [];
        if (!items.length) {
            if (overlayType === 'datasets') return '<p class="map-overlay-empty">No datasets. This system has no interfaces with attributes specified (all connections are dashed), so no datasets/attributes are shown.</p>';
            if (overlayType === 'attributes') return '<p class="map-overlay-empty">No attributes. This system has no interfaces with attributes specified (all connections are dashed), so no datasets/attributes are shown.</p>';
            if (overlayType === 'linking-attributes') return '<p class="map-overlay-empty">No attributes with relationships. Only attributes that participate in a relationship (interface or dataset) are shown.</p>';
            return '<p class="map-overlay-empty">No data.</p>';
        }
        const genericListTypes = ['stakeholders', 'processes', 'projects', 'policies', 'business-area', 'products', 'legal-entities', 'data-quality', 'data-privacy', 'geography'];
        if (genericListTypes.indexOf(overlayType) !== -1) {
            const rows = items.slice(0, 20).map(r => {
                const text = getDataMapOverlayItemText(overlayType, r);
                return '<div class="map-node-overlay-item">' + escapeOverlayHtml(text || '-') + '</div>';
            });
            const more = items.length > 20 ? '<div class="map-overlay-more">+' + (items.length - 20) + ' more</div>' : '';
            return rows.join('') + more;
        }
        const rows = items.slice(0, 30).map(r => {
            const name = (r && (r.name || r.datasetName || r.attributeName || r.primaryName)) || '-';
            const ref = r && (r.ref || r.refNumber || '');
            return '<div class="map-node-overlay-item">' + escapeOverlayHtml(name) + (ref ? ' <span class="map-overlay-ref">' + escapeOverlayHtml(ref) + '</span>' : '') + '</div>';
        });
        const more = items.length > 30 ? '<div class="map-overlay-more">+' + (items.length - 30) + ' more</div>' : '';
        return rows.join('') + more;
    }

    function renderOverlayPanels(overlayType, overlayData) {
        const network = ProjectDataMapState.network;
        const canvas = ProjectDataMapState.canvas;
        if (!network || !canvas) return;
        let overlayContainer = canvas.querySelector('.map-overlay-container');
        if (!overlayContainer) {
            overlayContainer = document.createElement('div');
            overlayContainer.classList.add('map-overlay-container');
            overlayContainer.style.cssText = 'z-index:50;position:absolute;top:0;left:0;pointer-events:none;';
            canvas.appendChild(overlayContainer);
        }
        overlayContainer.innerHTML = '';
        const title = getOverlayTitle(overlayType);
        network.nodes().forEach(node => {
            const nodeId = node.data('id');
            const data = nodeId != null ? overlayData.get(String(nodeId)) : null;
            const items = Array.isArray(data) ? data : (data && data.items) || [];
            if (!items || !items.length) return;
            const panel = document.createElement('div');
            panel.className = 'map-node-overlay-panel';
            panel.setAttribute('data-node-id', nodeId);
            panel.style.pointerEvents = 'auto';
            const header = document.createElement('div');
            header.className = 'map-node-overlay-header';
            const titleSpan = document.createElement('span');
            titleSpan.textContent = title;
            header.appendChild(titleSpan);
            const filterIcon = document.createElement('button');
            filterIcon.type = 'button';
            filterIcon.innerHTML = '<i class="fas fa-cog"></i>';
            filterIcon.className = 'map-overlay-filter-icon';
            filterIcon.style.cssText = 'border:none;background:transparent;cursor:pointer;padding:2px 4px;font-size:12px;color:var(--text-secondary,#6b7280);';
            filterIcon.title = 'Filter & display settings';
            header.appendChild(filterIcon);
            panel.appendChild(header);
            const filterBar = document.createElement('div');
            filterBar.className = 'map-overlay-filter-bar';
            filterBar.style.display = 'none';
            const searchInput = document.createElement('input');
            searchInput.type = 'text';
            searchInput.placeholder = 'Filter entries...';
            searchInput.className = 'map-overlay-filter-search';
            filterBar.appendChild(searchInput);
            const totalSpan = document.createElement('span');
            totalSpan.className = 'map-overlay-total-entries';
            totalSpan.textContent = 'Total entries: 0';
            filterBar.appendChild(totalSpan);
            const showWrap = document.createElement('div');
            showWrap.className = 'map-overlay-show-wrap';
            showWrap.innerHTML = '<label>Show:</label><select class="map-overlay-filter-select map-overlay-filter-select-bar"><option value="10">10</option><option value="20">20</option><option value="50">50</option><option value="100">100</option><option value="all">All</option></select>';
            filterBar.appendChild(showWrap);
            panel.appendChild(filterBar);
            const body = document.createElement('div');
            body.className = 'map-node-overlay-body';
            panel.appendChild(body);
            let currentFilter = '';
            let currentMax = 10;
            var colDefs = [];
            if (overlayType !== 'stakeholders' && window.OverlayColumns) {
                var allCols = window.OverlayColumns.getOverlayColumns(overlayType);
                var selectedIds = ProjectDataMapState.overlayColumnsByType[overlayType] || window.OverlayColumns.getDefaultOverlayColumnIds(overlayType);
                colDefs = allCols.filter(function(c) { return selectedIds && selectedIds.indexOf(c.id) !== -1; });
            }
            const updateDisplay = () => {
                const filtered = items.filter(item => {
                    const text = getDataMapOverlayItemText(overlayType, item).toLowerCase();
                    return text.includes(currentFilter.toLowerCase());
                });
                const maxN = currentMax === 'all' ? filtered.length : (parseInt(currentMax, 10) || 10);
                const displayItems = currentMax === 'all' ? filtered : filtered.slice(0, maxN);
                totalSpan.textContent = 'Total entries: ' + filtered.length;
                body.innerHTML = '';
                if (colDefs.length > 0) {
                    const table = document.createElement('table');
                    table.className = 'map-overlay-table';
                    table.style.cssText = 'width:100%;border-collapse:collapse;font-size:11px;';
                    const thead = document.createElement('thead');
                    const headerRow = document.createElement('tr');
                    colDefs.forEach(function(col) {
                        const th = document.createElement('th');
                        th.textContent = col.label || col.id;
                        th.style.cssText = 'text-align:left;padding:2px 4px;border-bottom:1px solid #ddd;font-weight:600;white-space:nowrap;';
                        headerRow.appendChild(th);
                    });
                    thead.appendChild(headerRow);
                    table.appendChild(thead);
                    const tbody = document.createElement('tbody');
                    displayItems.forEach(function(item) {
                        const tr = document.createElement('tr');
                        tr.className = 'map-node-overlay-item';
                        tr.style.cursor = 'pointer';
                        const itemText = getDataMapOverlayItemText(overlayType, item) || '-';
                        tr.dataset.overlayValue = itemText;
                        const itemId = item.id != null ? item.id : (item.ID != null ? item.ID : null);
                        if (itemId) {
                            tr.dataset.itemId = String(itemId);
                            if (overlayType === 'attributes' || overlayType === 'linking-attributes') tr.dataset.attributeId = String(itemId);
                        }
                        colDefs.forEach(function(col) {
                            const td = document.createElement('td');
                            td.textContent = getOverlayItemField(overlayType, item, col.id) || '-';
                            td.style.cssText = 'padding:2px 4px;border-bottom:1px solid #eee;';
                            tr.appendChild(td);
                        });
                        tr.addEventListener('click', function(e) {
                            e.stopPropagation();
                            highlightOverlayItem(overlayType, item, panel.getAttribute('data-node-id'));
                        });
                        tbody.appendChild(tr);
                    });
                    table.appendChild(tbody);
                    body.appendChild(table);
                } else {
                    displayItems.forEach((item) => {
                        const row = document.createElement('div');
                        row.className = 'map-node-overlay-item';
                        row.style.cursor = 'pointer';
                        const itemText = getDataMapOverlayItemText(overlayType, item) || '-';
                        row.textContent = itemText;
                        row.dataset.overlayValue = itemText;
                        const itemId = item.id != null ? item.id : (item.ID != null ? item.ID : null);
                        if (itemId) {
                            row.dataset.itemId = String(itemId);
                            if (overlayType === 'attributes' || overlayType === 'linking-attributes') row.dataset.attributeId = String(itemId);
                        }
                        row.addEventListener('click', function(e) {
                            e.stopPropagation();
                            highlightOverlayItem(overlayType, item, panel.getAttribute('data-node-id'));
                        });
                        body.appendChild(row);
                    });
                }
                if (filtered.length > displayItems.length) {
                    const more = document.createElement('div');
                    more.className = 'map-node-overlay-item-more';
                    more.textContent = `+${filtered.length - displayItems.length} more`;
                    body.appendChild(more);
                }
            };
            filterIcon.addEventListener('click', (e) => { e.stopPropagation(); var isOpen = filterBar.style.display !== 'none'; filterBar.style.display = isOpen ? 'none' : ''; if (!isOpen) searchInput.focus(); });
            searchInput.addEventListener('click', (e) => { e.stopPropagation(); });
            searchInput.addEventListener('mousedown', (e) => { e.stopPropagation(); });
            searchInput.addEventListener('input', (e) => { e.stopPropagation(); currentFilter = e.target.value; updateDisplay(); });
            const maxSelect = filterBar.querySelector('select');
            maxSelect.addEventListener('click', (e) => { e.stopPropagation(); });
            maxSelect.addEventListener('mousedown', (e) => { e.stopPropagation(); });
            maxSelect.addEventListener('change', (e) => { e.stopPropagation(); currentMax = e.target.value; updateDisplay(); });
            updateDisplay();
            overlayContainer.appendChild(panel);
            positionOverlayPanelElement(panel, node);
        });
        network.on('zoom pan', updateOverlayPositions);
        network.on('drag', 'node', updateOverlayPositions);
        requestAnimationFrame(() => requestAnimationFrame(updateOverlayPositions));
    }

    async function loadOverlayData(overlayType) {
        const network = ProjectDataMapState.network;
        if (!network) return;
        const overlayData = new Map();
        const nodeIds = network.nodes().map(n => n.data('id'));

        if (overlayType === 'description' || overlayType === 'glossary' || overlayType === 'linking-attributes' ||
            ['stakeholders', 'processes', 'projects', 'policies', 'business-area', 'products', 'legal-entities', 'data-quality', 'data-privacy', 'geography'].indexOf(overlayType) !== -1) {
            for (const nodeId of nodeIds) {
                const meta = ProjectDataMapState.nodeMeta.get(String(nodeId));
                if (!meta || !meta.systemId) { overlayData.set(String(nodeId), { items: [] }); continue; }
                let items = [];
                if (overlayType === 'glossary') {
                    const seenG = new Set();
                    // 1) System-level glossary
                    try {
                        const sys = await fetchSystemByIdSafe(meta.systemId);
                        const s = sys?.data || sys;
                        const gname = s?.primaryGlossaryName ?? s?.glossaryName ?? s?.glossary_name;
                        if (gname && !seenG.has(gname)) {
                            seenG.add(gname);
                            items.push({ glossaryName: gname, glossaryRefNumber: '', source: 'system' });
                        }
                    } catch (e) { }
                    // 2) Dataset-level and attribute-level glossary from system datasets
                    try {
                        const API = window.BUDG_API_SERVICE;
                        let sysDatasets = [];
                        if (API && typeof API.getSystemDatasets === 'function') {
                            const dsResp = await API.getSystemDatasets(meta.systemId).catch(() => null);
                            const dsList = Array.isArray(dsResp?.data) ? dsResp.data : (Array.isArray(dsResp) ? dsResp : []);
                            sysDatasets = dsList.map(d => ({ id: d.id || d.ID, glossaryName: d.glossaryName }));
                        }
                        for (const ds of sysDatasets) {
                            if (ds.glossaryName && !seenG.has(ds.glossaryName)) {
                                seenG.add(ds.glossaryName);
                                items.push({ glossaryName: ds.glossaryName, glossaryRefNumber: '', source: 'dataset' });
                            }
                            if (ds.id) {
                                try {
                                    const attrResp = await fetch(`/api/attribute/${ds.id}`, { credentials: 'include' });
                                    if (attrResp.ok) {
                                        const attrData = await attrResp.json();
                                        const attrs = Array.isArray(attrData?.data) ? attrData.data : (Array.isArray(attrData) ? attrData : []);
                                        attrs.forEach(a => {
                                            const gn = a['Glossary Name attribute'] || a.glossaryName || a.glossary_name || a.GlossaryName;
                                            if (gn && !seenG.has(gn)) {
                                                seenG.add(gn);
                                                items.push({ glossaryName: gn, glossaryRefNumber: '', source: 'attribute' });
                                            }
                                        });
                                    }
                                } catch (e) { /* skip */ }
                            }
                        }
                    } catch (e) { }
                } else if (overlayType === 'linking-attributes') {
                    const systemId = meta.systemId;
                    const isImpact = meta.isImpact;
                    const hasSolidEdge = ProjectDataMapState.systemIdsWithSolidEdge.has(systemId);
                    let allAttrs = [];
                    if (hasSolidEdge) {
                        if (isImpact) {
                            allAttrs = (ProjectDataMapState.impactSystemIdToAttributes.get(systemId) || []).slice();
                            const ifaceAttrs = ProjectDataMapState.interfaceAttributesBySystem.get(systemId) || [];
                            ifaceAttrs.forEach(a => {
                                if (!allAttrs.some(x => String(x.id) === String(a.id))) allAttrs.push(a);
                            });
                        } else {
                            allAttrs = (ProjectDataMapState.interfaceAttributesBySystem.get(systemId) || []).slice();
                        }
                    }
                    items = allAttrs.filter(a => ProjectDataMapState.attributeIdsWithLinks.has(String(a.id)));
                } else {
                    items = await fetchOverlayForSystemNode(meta.systemId, overlayType);
                }
                overlayData.set(String(nodeId), Array.isArray(items) ? { items } : { items: [] });
            }
        } else {
            for (const nodeId of nodeIds) {
                const meta = ProjectDataMapState.nodeMeta.get(String(nodeId));
                if (!meta || !meta.systemId) { overlayData.set(String(nodeId), { items: [] }); continue; }
                const systemId = meta.systemId;
                const isImpact = meta.isImpact;
                // Only show datasets/attributes if this system has at least one solid edge (interface with attributes).
                // Systems with only dashed edges show nothing (e.g. Compliance–CRM dashed; Compliance–CDD solid shows CDD-interface data only).
                const hasSolidEdge = ProjectDataMapState.systemIdsWithSolidEdge.has(systemId);

                if (overlayType === 'datasets') {
                    let items = [];
                    if (hasSolidEdge) {
                        if (isImpact) {
                            items = (ProjectDataMapState.impactSystemIdToDatasets.get(systemId) || []).slice();
                            const ifaceDatasets = ProjectDataMapState.interfaceDatasetsBySystem.get(systemId) || [];
                            ifaceDatasets.forEach(d => {
                                if (!items.some(x => String(x.id) === String(d.id))) items.push(d);
                            });
                        } else {
                            items = (ProjectDataMapState.interfaceDatasetsBySystem.get(systemId) || []).slice();
                        }
                    }
                    overlayData.set(String(nodeId), { items });
                } else if (overlayType === 'attributes') {
                    let items = [];
                    if (hasSolidEdge) {
                        if (isImpact) {
                            items = (ProjectDataMapState.impactSystemIdToAttributes.get(systemId) || []).slice();
                            const ifaceAttrs = ProjectDataMapState.interfaceAttributesBySystem.get(systemId) || [];
                            ifaceAttrs.forEach(a => {
                                if (!items.some(x => String(x.id) === String(a.id))) items.push(a);
                            });
                        } else {
                            items = (ProjectDataMapState.interfaceAttributesBySystem.get(systemId) || []).slice();
                        }
                    }
                    overlayData.set(String(nodeId), { items });
                }
            }
        }
        renderOverlayPanels(overlayType, overlayData);
    }

    function setOverlay(overlay) {
        ProjectDataMapState.overlay = overlay || 'none';
        clearOverlayPanels();
        if (ProjectDataMapState.overlay === 'none' || !ProjectDataMapState.overlay) return;
        loadOverlayData(ProjectDataMapState.overlay);
    }

    function setOverlayColumns(overlayType, columnIds) {
        if (!overlayType) return;
        ProjectDataMapState.overlayColumnsByType[overlayType] = Array.isArray(columnIds) ? columnIds.slice() : [];
        if (ProjectDataMapState.overlay === overlayType) {
            loadOverlayData(overlayType);
        }
    }

    function getOverlayColumns(overlayType) {
        return ProjectDataMapState.overlayColumnsByType[overlayType] || (window.OverlayColumns ? window.OverlayColumns.getDefaultOverlayColumnIds(overlayType) : ['name']);
    }

    function getState() {
        return ProjectDataMapState;
    }

    function setLayout(layout) {
        ProjectDataMapState.layout = layout;
        if (ProjectDataMapState.network) {
            ProjectDataMapState.network.layout(adapter.buildLayoutOptions()).run();
        }
    }

    function setHopsCount(count) {
        const val = Math.min(99, Math.max(1, parseInt(count, 10) || 15));
        ProjectDataMapState.hopsCount = val;
        if (ProjectDataMapState.projectId) loadMapData();
    }

    // ── Shared toolbar actions (called by SharedMapControls adapter) ──────────
    function exportAsPng() {
        window.MapRenderUtils.exportLineageMapToPng({
            network: ProjectDataMapState.network,
            canvas: ProjectDataMapState.canvas,
            filename: 'project-data-map-' + ProjectDataMapState.projectId + '-' + Date.now() + '.png',
            emptyMessage: 'No map to export. Wait for the map to finish loading.',
            failMessage: 'Could not export the map.',
            logPrefix: '[ProjectDataMap]'
        });
    }

    function openFullscreen() {
        if (!ProjectDataMapState.network) return;
        if (typeof window.openMapFullscreen === 'function') {
            window.openMapFullscreen({
                title:              'Project Data Map - ' + (ProjectDataMapState.projectId || ''),
                network:            ProjectDataMapState.network,
                getCytoscapeStyle:  getCytoscapeStyle,
                layoutName:         ProjectDataMapState.layout || 'top-to-bottom',
                legendHtml:         getLegendHtml(),
                exportFilename:     'project-data-map-' + ProjectDataMapState.projectId + '.png',
                toolbarAnchor:      ProjectDataMapState.canvas,
                mapType:            'system-lineage',
                mapTabKind:         'project-data',
                entityId:           ProjectDataMapState.projectId,
                getState:           function () { return ProjectDataMapState; }
            });
        } else {
            const wrapper = document.getElementById(MAP_ID + 'Wrapper');
            if (wrapper) try { if (!document.fullscreenElement) wrapper.requestFullscreen?.(); else document.exitFullscreen?.(); } catch (e) { /* fullscreen API may reject */ }
        }
    }

    function setupControls() {
        const container = document.getElementById(MAP_ID + 'Wrapper') || document.getElementById(MAP_ID + 'Container');
        if (!container) return;
        const bindOnce = !container.dataset.projectDataMapControlsBound;
        if (bindOnce) {
            container.dataset.projectDataMapControlsBound = '1';
        }
        const hopsInput = document.getElementById(MAP_ID + 'HopsCount');
        if (bindOnce && hopsInput) {
            hopsInput.addEventListener('change', (e) => {
                let val = parseInt(e.target.value, 10);
                if (isNaN(val) || val < 1) val = 1;
                if (val > 99) val = 99;
                e.target.value = val;
                setHopsCount(val);
            });
        }

        const overlayLabels = window.MapRenderUtils.STANDARD_LINEAGE_OVERLAY_LABELS;
        const overlayBtn = document.getElementById(MAP_ID + 'OverlayBtn');
        const overlayMenu = document.getElementById(MAP_ID + 'OverlayMenu');
        const overlayGridBtn = document.getElementById(MAP_ID + 'OverlayGrid');
        const overlayColumnsMenu = document.getElementById(MAP_ID + 'OverlayColumnsMenu');
        const overlayColumnsOptions = document.getElementById(MAP_ID + 'OverlayColumnsOptions');
        if (bindOnce && overlayGridBtn && overlayColumnsMenu && overlayColumnsOptions) {
            function populateOverlayColumnsMenu() {
                overlayColumnsOptions.innerHTML = '';
                const ot = ProjectDataMapState.overlay || '';
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
                    input.id = 'overlayCol_projectDataMap_' + ot + '_' + col.id;
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
        if (overlayBtn && overlayMenu) {
            overlayBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                overlayMenu.classList.toggle('open');
            });
            overlayMenu.querySelectorAll('.overlay-menu-item').forEach(item => {
                item.addEventListener('click', function() {
                    const overlayType = this.getAttribute('data-overlay');
                    overlayMenu.querySelectorAll('.overlay-menu-item').forEach(i => i.classList.remove('active'));
                    if (overlayType) {
                        this.classList.add('active');
                        const textEl = document.getElementById(MAP_ID + 'OverlayBtnText');
                        if (textEl) textEl.textContent = overlayLabels[overlayType] || overlayType;
                        setOverlay(overlayType);
                    } else {
                        const textEl = document.getElementById(MAP_ID + 'OverlayBtnText');
                        if (textEl) textEl.textContent = 'None';
                        setOverlay('none');
                    }
                    overlayMenu.classList.remove('open');
                });
            });
            document.getElementById(MAP_ID + 'ClearOverlaysBtn')?.addEventListener('click', () => {
                const textEl = document.getElementById(MAP_ID + 'OverlayBtnText');
                if (textEl) textEl.textContent = 'None';
                overlayMenu.querySelectorAll('.overlay-menu-item').forEach(i => i.classList.remove('active'));
                overlayMenu.classList.remove('open');
                setOverlay('none');
            });
            document.addEventListener('click', function(e) {
                if (overlayMenu && overlayMenu.classList.contains('open') && !overlayMenu.contains(e.target) && !overlayBtn.contains(e.target)) {
                    overlayMenu.classList.remove('open');
                }
            });
        }
        if (bindOnce) {
            const filterBtn = document.getElementById(MAP_ID + 'FilterBtn');
            const filterMenu = document.getElementById(MAP_ID + 'FilterMenu');
            const projRoot = getProjectMapRoot();
            if (filterBtn && filterMenu && projRoot) {
                filterBtn.addEventListener('click', function (e) {
                    e.stopPropagation();
                    const wasOpen = filterMenu.classList.contains('open');
                    projRoot.querySelectorAll('.map-filter-menu').forEach(function (m) { m.classList.remove('open'); });
                    if (!wasOpen) filterMenu.classList.add('open');
                });
                projRoot.addEventListener('change', function (e) {
                    const t = e.target;
                    if (!t || t.tagName !== 'INPUT' || t.type !== 'checkbox') return;
                    if (!filterMenu.contains(t)) return;
                    syncProjectNodeFiltersFromDomAndApply();
                });
                document.addEventListener('click', function (e) {
                    if (filterMenu.classList.contains('open') && !filterMenu.contains(e.target) && !filterBtn.contains(e.target)) {
                        filterMenu.classList.remove('open');
                    }
                });
            }
            window.MapRenderUtils.wireLineageToolbarSharedControls({
                mapId: MAP_ID,
                adapter: adapter,
                state: ProjectDataMapState,
                loadMapData: loadMapData,
                setLayout: setLayout,
                exportAsPng: exportAsPng,
                openFullscreen: openFullscreen,
                getLegendHtml: getLegendHtml,
                mapInstance: {
                    setNodeFilters: function (filters) {
                        ProjectDataMapState.nodeFilters = {
                            classifications: (filters && filters.classifications) || [],
                            types: (filters && filters.types) || [],
                            lifecycles: (filters && filters.lifecycles) || []
                        };
                        ProjectDataMapState.filtersInitialized = true;
                        applyProjectNodeFiltersFromState();
                    },
                    getState: getState
                }
            });
        }
    }

    function init(projectId) {
        ProjectDataMapState.projectId = projectId;
        const container = document.getElementById('projectDataMapContainer');
        if (!container) return;
        ProjectDataMapState.canvas = null;
        if (typeof cytoscape === 'undefined') {
            container.innerHTML = window.MapRenderUtils.htmlMapLibraryUnavailable();
            return;
        }
        if (!document.getElementById(MAP_ID + 'Canvas')) {
            container.innerHTML = getMapHtml();
        }
        ProjectDataMapState.canvas = document.getElementById(MAP_ID + 'Canvas');
        if (!ProjectDataMapState.canvas) return;
        ProjectDataMapState.initialized = true;
        setupControls();
        loadMapData();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Graph pipeline + MapEngine (shared/map/project-data-facet-graph.js)
    // ─────────────────────────────────────────────────────────────────────────

    graphPipeline = window.createProjectDataFacetGraphPipeline({
        state: ProjectDataMapState,
        adapter: adapter,
        renderNetwork: renderNetwork,
        showLoading: showLoading,
        hideLoading: hideLoading,
        showPlaceholder: showPlaceholder,
        logPrefix: '[ProjectDataMap]'
    });

    var _mapEngineApiLoader = graphPipeline._mapEngineApiLoader;
    var _mapEngineNodeBuilder = graphPipeline._mapEngineNodeBuilder;
    var _mapEngineEdgeBuilder = graphPipeline._mapEngineEdgeBuilder;

    networkIx = window.createProjectDataMapNetworkInteractions({
        state: ProjectDataMapState,
        buildSystemLineageGraph: buildSystemLineageGraph,
        renderNetwork: renderNetwork
    });

    if (window.MapConfigs && window.MapConfigs.register) {
        window.MapConfigs.register('project-data', {
            apiLoader:     _mapEngineApiLoader,
            nodeBuilder:   _mapEngineNodeBuilder,
            edgeBuilder:   _mapEngineEdgeBuilder,
            legendBuilder: getLegendHtml
        });
    }

    var _projectDataEngine = new window.MapEngine('project-data');

    Object.assign(_projectDataEngine, {
        init: init,
        loadMapData: loadMapData,
        setLayout: setLayout,
        setHopsCount: setHopsCount,
        setOverlay: setOverlay,
        setOverlayColumns: setOverlayColumns,
        getOverlayColumns: getOverlayColumns,
        setNodeFilters: function (filters) {
            ProjectDataMapState.nodeFilters = {
                classifications: (filters && filters.classifications) || [],
                types: (filters && filters.types) || [],
                lifecycles: (filters && filters.lifecycles) || []
            };
            ProjectDataMapState.filtersInitialized = true;
            applyProjectNodeFiltersFromState();
        },
        resetMap: function () {
            ProjectDataMapState.hiddenNodes.clear();
            if (ProjectDataMapState.network) ProjectDataMapState.network.fit(undefined, 50);
        },
        redrawMap: function () {
            loadMapData();
        },
        toggleInterfaceLabels: function (show) {
            if (!ProjectDataMapState.network) return;
            var cur = ProjectDataMapState.showEdgeLabels !== false;
            var on = show !== undefined ? !!show : !cur;
            ProjectDataMapState.showEdgeLabels = on;
            ProjectDataMapState.network.edges().style('label', on ? 'data(label)' : '');
        },
        getState: function () { return ProjectDataMapState; }
    });

    Object.defineProperty(_projectDataEngine, 'cy', {
        get: function () { return ProjectDataMapState.network; },
        configurable: true
    });

    window.ProjectDataMap = _projectDataEngine;
})();
