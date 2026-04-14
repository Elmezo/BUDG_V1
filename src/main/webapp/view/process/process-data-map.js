/**
 * Process Data Map – Data tab > Data Map sub-tab.
 * Same layout as other maps (system interfaces, process context, process components).
 * Two lineage types:
 * - System Lineage: System A (orange) = systems of datasets in Impact > Datasets;
 *   System B (orange) = systems of datasets that contain attributes in Impact > Attributes;
 *   X (black) = systems directly linked to A or B only.
 * - Dataset Lineage: Datasets from impact; only show system Z if Z's relationship
 *   is via an attribute that is in Impact > Attributes.
 *
 * Dependencies: map-graph-utils.js, map-render-utils.js, shared/map/process-data-facet-graph.js (createProcessDataFacetGraphPipeline), system-lineage-network-interactions.js (createProcessDataMapNetworkInteractions).
 */
(function() {
    'use strict';

    const MAP_ID = 'processDataMap';
    const ProcessDataMapState = {
        initialized: false,
        network: null,
        canvas: null,
        processId: null,
        mapType: 'system-lineage',
        layout: 'top-to-bottom',
        hopsCount: 15,
        /** System A: system IDs from Impact > Datasets */
        impactSystemIdsFromDatasets: new Set(),
        /** System B: system IDs from Impact > Attributes (system of each attribute) */
        impactSystemIdsFromAttributes: new Set(),
        /** All impact system IDs (A ∪ B) – shown in orange */
        impactSystemIds: new Set(),
        /** Systems directly linked to A or B – shown in black */
        linkedSystemIds: new Set(),
        systemsData: new Map(),
        interfacesData: [],
        dataFlowData: [],
        /** For dataset lineage: impact dataset IDs (System A) and attribute IDs */
        impactDatasetIds: new Set(),
        /** Datasets that contain impact attributes (System B) */
        impactDatasetIdsFromAttributes: new Set(),
        impactAttributeIds: new Set(),
        /** datasetId -> { dataset, systemId, isImpact } */
        linkedDatasets: new Map(),
        datasetRelationships: [],
        /** systemId -> systemName (for dataset lineage labels) */
        datasetLineageSystemNames: new Map(),
        overlay: 'none',
        overlayColumnsByType: {},
        /** systemId -> [datasetIds] from Impact > Datasets (for overlay) */
        impactSystemIdToDatasetIds: new Map(),
        /** systemId -> [{ id, name, ref, glossaryId, glossaryName }] from Impact > Datasets (for Dataset/Glossary overlay) */
        impactSystemIdToDatasets: new Map(),
        /** systemId -> [{ id, name, glossaryId, glossaryName }] from Impact > Attributes (for overlay) */
        impactSystemIdToAttributeIds: new Map(),
        /** dataset IDs that appear in relationships with impact datasets (for system lineage black Dataset overlay) */
        impactLinkedDatasetIds: new Set(),
        /** datasetId -> [{ id, name, ... }] all attributes (for System A overlay); populated on demand */
        impactDatasetIdToAttributes: new Map(),
        /** datasetId -> [{ id, name }] impact attributes in that dataset (Impact > Attributes subtab) */
        impactDatasetIdToImpactAttributeIds: new Map(),
        /** attribute IDs that appear in any relationship (for linking / black overlay) */
        attributeIdsWithLinks: new Set(),
        /** nodeId -> { systemId?, datasetId?, isImpact, isFromDatasets? } for overlay */
        nodeMeta: new Map(),
        /** nodeId set for "Hide object" */
        hiddenNodes: new Set(),
        /** System IDs in segments user cannot access – show lock */
        inaccessibleSystems: new Set(),
        /** System lineage: dashed vs solid link toggles (same semantics as system interfaces map) */
        filters: { systemInterfaces: true, dataAttributeLinks: true },
        nodeFilters: { classifications: [], types: [], lifecycles: [] },
        datasetNodeFilters: { types: [], lifecycles: [] },
        showEdgeLabels: true
    };

    const adapter = window.createMapAdapter(ProcessDataMapState, MAP_ID, { zoomRecenter: false });

    var graphPipeline = null;

    async function loadMapData() {
        return graphPipeline.loadMapData();
    }

    function buildSystemLineageGraph() {
        return graphPipeline.buildSystemLineageGraph();
    }

    function buildDatasetLineageGraph() {
        return graphPipeline.buildDatasetLineageGraph();
    }

    var networkIx = null;

    // Colors from shared palette (map-render-utils.js)
    const _pal         = window.MapRenderUtils.MAP_PALETTE;
    const ORANGE        = _pal.orange        || '#f97316';
    const ORANGE_BORDER = _pal.orangeBorder  || '#ea580c';
    const BLACK         = _pal.black         || '#1f2937';
    const BLACK_BORDER  = _pal.blackBorder   || '#374151';
    const EDGE_LINE     = _pal.edgeLine      || '#64748b';
    const EDGE_DASHED   = _pal.edgeDashed    || '#94a3b8';

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

    function getToolbarOnlyHtml() {
        return `
                <div class="interface-map-toolbar">
                    <div class="map-control-group">
                        <label>Map type:</label>
                        <select id="${MAP_ID}TypeSelect" class="map-select">
                            <option value="system-lineage" selected>System Lineage</option>
                            <option value="dataset-lineage">Dataset Lineage</option>
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
                                            <div class="overlay-menu-item" data-overlay="description" data-map-type="system-lineage"><i class="fas fa-info-circle"></i> Description</div>
                                            <div class="overlay-menu-item" data-overlay="description" data-map-type="dataset-lineage"><i class="fas fa-info-circle"></i> Definition</div>
                                            <div class="overlay-menu-item" data-overlay="glossary"><i class="fas fa-book"></i> Glossary</div>
                                            <div class="overlay-menu-item" data-overlay="datasets" data-map-type="system-lineage"><i class="fas fa-layer-group"></i> Data Sets</div>
                                            <div class="overlay-menu-item" data-overlay="attributes"><i class="fas fa-th"></i> Attributes</div>
                                            <div class="overlay-menu-item" data-overlay="linking-attributes"><i class="fas fa-link"></i> Linking Attributes</div>
                                            <div class="overlay-menu-item" data-overlay="data-quality"><i class="fas fa-bullseye"></i> Data Quality</div>
                                            <div class="overlay-menu-item" data-overlay="data-privacy" data-map-type="system-lineage"><i class="fas fa-lock"></i> Data Privacy</div>
                                        </div>
                                        <div class="overlay-menu-column" data-overlay-column="business">
                                            <div class="overlay-menu-header">Business</div>
                                            <div class="overlay-menu-item" data-overlay="stakeholders"><i class="fas fa-users"></i> Stakeholders</div>
                                            <div class="overlay-menu-item" data-overlay="processes"><i class="fas fa-play"></i> Processes</div>
                                            <div class="overlay-menu-item" data-overlay="projects"><i class="fas fa-project-diagram"></i> Projects</div>
                                            <div class="overlay-menu-item" data-overlay="policies"><i class="fas fa-file-alt"></i> Policies</div>
                                        </div>
                                        <div class="overlay-menu-column" data-overlay-column="organizational" data-map-type="system-lineage">
                                            <div class="overlay-menu-header">Organizational</div>
                                            <div class="overlay-menu-item" data-overlay="business-area"><i class="fas fa-briefcase"></i> Business Area</div>
                                            <div class="overlay-menu-item" data-overlay="products"><i class="fas fa-tag"></i> Products</div>
                                            <div class="overlay-menu-item" data-overlay="legal-entities"><i class="fas fa-landmark"></i> Legal Entities</div>
                                        </div>
                                        <div class="overlay-menu-column" data-overlay-column="regulatory" data-map-type="system-lineage">
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
                        <div class="map-layout-controls">
                            <div class="map-filter-dropdown">
                                <button type="button" class="map-select-btn" id="${MAP_ID}FilterBtn">
                                    <span id="${MAP_ID}FilterBtnText">All link types</span>
                                    <i class="fas fa-chevron-down"></i>
                                </button>
                                <div class="map-filter-menu" id="${MAP_ID}FilterMenuSystem" data-map-type="system-lineage">
                                    <div class="map-filter-category">
                                        <div class="map-filter-category-header">LINKS</div>
                                        <div class="map-filter-option">
                                            <input type="checkbox" id="filter${MAP_ID}systemInterfaces" checked>
                                            <label for="filter${MAP_ID}systemInterfaces">System Interfaces</label>
                                        </div>
                                        <div class="map-filter-option">
                                            <input type="checkbox" id="filter${MAP_ID}dataAttributeLinks" checked>
                                            <label for="filter${MAP_ID}dataAttributeLinks">Data Attribute Links</label>
                                        </div>
                                    </div>
                                    <div class="map-filter-separator"></div>
                                    <div class="map-filter-category">
                                        <div class="map-filter-category-header">CLASSIFICATION</div>
                                        <div id="${MAP_ID}filterClassificationOptions" class="map-filter-options-container"></div>
                                    </div>
                                    <div class="map-filter-separator"></div>
                                    <div class="map-filter-category">
                                        <div class="map-filter-category-header">TYPE</div>
                                        <div id="${MAP_ID}filterTypeOptions" class="map-filter-options-container"></div>
                                    </div>
                                    <div class="map-filter-separator"></div>
                                    <div class="map-filter-category">
                                        <div class="map-filter-category-header">LIFECYCLE</div>
                                        <div id="${MAP_ID}filterLifecycleOptions" class="map-filter-options-container"></div>
                                    </div>
                                </div>
                                <div class="map-filter-menu" id="${MAP_ID}FilterMenuDataset" data-map-type="dataset-lineage">
                                    <div class="map-filter-category">
                                        <div class="map-filter-category-header">TYPE</div>
                                        <div id="${MAP_ID}filterDatasetTypeOptions" class="map-filter-options-container"></div>
                                    </div>
                                    <div class="map-filter-separator"></div>
                                    <div class="map-filter-category">
                                        <div class="map-filter-category-header">LIFECYCLE</div>
                                        <div id="${MAP_ID}filterDatasetLifecycleOptions" class="map-filter-options-container"></div>
                                    </div>
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
                </div>`;
    }

    function getMapHtml() {
        return `
            <div class="interface-map-container process-data-map-wrapper" id="${MAP_ID}Wrapper">
                ${getToolbarOnlyHtml()}
                <div class="interface-map-body">
                    <div class="interface-map-canvas" id="${MAP_ID}Canvas">
                        <div class="interface-map-loading" data-process-data-map-loading style="display:none;">
                            <i class="fas fa-spinner fa-spin"></i>
                            <span>Building data map...</span>
                        </div>
                    </div>
                    <div class="interface-map-side-panel" id="${MAP_ID}SidePanel" style="display:none;">
                        <div class="map-side-panel-section">
                            <h4 class="map-legend-palette-title"><i class="fas fa-layer-group"></i> Insight Maps Palette</h4>
                            <div data-process-data-map-legend></div>
                        </div>
                    </div>
                </div>
            </div>
        `;
    }

    function showLoading() {
        const el = document.querySelector('[data-process-data-map-loading]');
        if (el) el.style.display = 'block';
    }

    function hideLoading() {
        const el = document.querySelector('[data-process-data-map-loading]');
        if (el) el.style.display = 'none';
    }

    function getDataMapRoot() {
        const w = document.getElementById(MAP_ID + 'Wrapper') || document.getElementById('processDataMapContainer');
        if (w) return w;
        const host = document.getElementById('mapTabToolbarHost');
        if (host && host.querySelector('#' + MAP_ID + 'TypeSelect')) return host;
        return null;
    }

    /**
     * /view/full_map/process/… injects toolbar only (no Wrapper). Wire filter menus + link toggles once.
     */
    function ensureMapTabProcessFilterWiring() {
        const host = document.getElementById('mapTabToolbarHost');
        if (!host || !host.querySelector('#' + MAP_ID + 'FilterBtn')) return;
        if (host.dataset.processMapTabFiltersWired === '1') return;
        host.dataset.processMapTabFiltersWired = '1';

        const filterBtn = document.getElementById(MAP_ID + 'FilterBtn');
        const filterSi = document.getElementById('filter' + MAP_ID + 'systemInterfaces');
        const filterDa = document.getElementById('filter' + MAP_ID + 'dataAttributeLinks');
        if (filterBtn) {
            filterBtn.addEventListener('click', function (e) {
                e.stopPropagation();
                const menu = getActiveDataFilterMenu();
                if (menu) {
                    const wasOpen = menu.classList.contains('open');
                    getDataMapRoot()?.querySelectorAll('.map-filter-menu').forEach(function (m) { m.classList.remove('open'); });
                    if (!wasOpen) menu.classList.add('open');
                }
            });
        }
        if (filterSi) {
            filterSi.addEventListener('change', function () {
                setLinkFilter('systemInterfaces', filterSi.checked);
            });
        }
        if (filterDa) {
            filterDa.addEventListener('change', function () {
                setLinkFilter('dataAttributeLinks', filterDa.checked);
            });
        }
        const onDynamicFilterChange = function (e) {
            const t = e.target;
            if (!t || t.tagName !== 'INPUT' || t.type !== 'checkbox') return;
            if (t.id === 'filter' + MAP_ID + 'systemInterfaces' || t.id === 'filter' + MAP_ID + 'dataAttributeLinks') return;
            const menu = t.closest('.map-filter-menu');
            if (!menu || !getDataMapRoot()?.contains(menu)) return;
            if (ProcessDataMapState.mapType === 'dataset-lineage') {
                syncDatasetNodeFiltersFromDomAndApply();
            } else {
                syncSystemNodeFiltersFromDomAndApply();
            }
        };
        getDataMapRoot()?.addEventListener('change', onDynamicFilterChange);
        document.addEventListener('click', function (e) {
            const root = getDataMapRoot();
            if (!root) return;
            const active = getActiveDataFilterMenu();
            if (active && filterBtn && !active.contains(e.target) && !filterBtn.contains(e.target)) {
                active.classList.remove('open');
            }
        });
        updateProcessDataFilterButtonText();
    }

    function getActiveDataFilterMenu() {
        const root = getDataMapRoot();
        if (!root) return null;
        const mt = ProcessDataMapState.mapType || 'system-lineage';
        return root.querySelector('.map-filter-menu[data-map-type="' + mt + '"]');
    }

    function updateProcessDataFilterButtonText() {
        const el = document.getElementById(MAP_ID + 'FilterBtnText');
        if (!el) return;
        if (ProcessDataMapState.mapType === 'dataset-lineage') {
            el.textContent = 'Type & lifecycle';
            return;
        }
        const si = document.getElementById('filter' + MAP_ID + 'systemInterfaces');
        const da = document.getElementById('filter' + MAP_ID + 'dataAttributeLinks');
        const n = (si && si.checked ? 1 : 0) + (da && da.checked ? 1 : 0);
        if (n === 0) el.textContent = 'No link types';
        else if (n === 2) el.textContent = 'All link types';
        else el.textContent = '1 link type';
    }

    function fillFilterCheckboxGroup(containerId, values, idPrefix) {
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

    function populateSystemLineageFilterOptions() {
        const extract = window.MapGraphUtils && typeof window.MapGraphUtils.extractSystemMeta === 'function'
            ? window.MapGraphUtils.extractSystemMeta : null;
        if (!extract) return;
        const cls = new Set();
        const types = new Set();
        const lifes = new Set();
        ProcessDataMapState.systemsData.forEach(function (sys, sid) {
            const m = extract(sys, sid);
            if (m.classification) cls.add(m.classification);
            if (m.type) types.add(m.type);
            if (m.lifecycle) lifes.add(m.lifecycle);
        });
        fillFilterCheckboxGroup(MAP_ID + 'filterClassificationOptions', cls, 'cls');
        fillFilterCheckboxGroup(MAP_ID + 'filterTypeOptions', types, 'typ');
        fillFilterCheckboxGroup(MAP_ID + 'filterLifecycleOptions', lifes, 'life');
    }

    function populateDatasetLineageFilterOptions() {
        const types = new Set();
        const lifes = new Set();
        ProcessDataMapState.linkedDatasets.forEach(function (info) {
            const d = info && info.dataset ? info.dataset : {};
            const tn = d.typeName || d.datasetTypeName || d.type || '';
            const ln = d.lifecycleName || d.lifecycle || '';
            if (tn) types.add(String(tn));
            if (ln) lifes.add(String(ln));
        });
        fillFilterCheckboxGroup(MAP_ID + 'filterDatasetTypeOptions', types, 'dtyp');
        fillFilterCheckboxGroup(MAP_ID + 'filterDatasetLifecycleOptions', lifes, 'dlife');
    }

    function readCheckedValues(containerId) {
        const c = document.getElementById(containerId);
        if (!c) return [];
        return Array.prototype.map.call(c.querySelectorAll('input[type="checkbox"]:checked'), function (i) { return i.value; });
    }

    function syncSystemNodeFiltersFromDomAndApply() {
        ProcessDataMapState.nodeFilters = {
            classifications: readCheckedValues(MAP_ID + 'filterClassificationOptions'),
            types: readCheckedValues(MAP_ID + 'filterTypeOptions'),
            lifecycles: readCheckedValues(MAP_ID + 'filterLifecycleOptions')
        };
        if (ProcessDataMapState.network && ProcessDataMapState.mapType === 'system-lineage') {
            adapter.applySystemNodeFilters({
                filtersInitialized: true,
                updateOverlayPositions: updateOverlayPositions
            });
        }
    }

    function syncDatasetNodeFiltersFromDomAndApply() {
        ProcessDataMapState.datasetNodeFilters = {
            types: readCheckedValues(MAP_ID + 'filterDatasetTypeOptions'),
            lifecycles: readCheckedValues(MAP_ID + 'filterDatasetLifecycleOptions')
        };
        if (ProcessDataMapState.network && ProcessDataMapState.mapType === 'dataset-lineage') {
            adapter.applyDatasetNodeFilters({
                linkedDatasets: ProcessDataMapState.linkedDatasets,
                filtersInitialized: true,
                updateOverlayPositions: updateOverlayPositions
            });
        }
    }

    function refreshProcessDataMapFiltersAfterRender() {
        if (ProcessDataMapState.mapType === 'dataset-lineage') {
            populateDatasetLineageFilterOptions();
            syncDatasetNodeFiltersFromDomAndApply();
        } else {
            populateSystemLineageFilterOptions();
            syncSystemNodeFiltersFromDomAndApply();
        }
        updateProcessDataFilterButtonText();
    }

    function setLinkFilter(filterKey, checked) {
        if (!ProcessDataMapState.filters) return;
        if (filterKey === 'systemInterfaces') ProcessDataMapState.filters.systemInterfaces = !!checked;
        if (filterKey === 'dataAttributeLinks') ProcessDataMapState.filters.dataAttributeLinks = !!checked;
        updateProcessDataFilterButtonText();
        loadMapData();
    }

    function showPlaceholder(message) {
        if (ProcessDataMapState.network) {
            try {
                ProcessDataMapState.network.destroy();
            } catch (e) { /* no-op */ }
            ProcessDataMapState.network = null;
        }
        clearOverlayPanels();
        if (ProcessDataMapState.canvas) {
            ProcessDataMapState.canvas.innerHTML = window.MapRenderUtils.htmlMapCanvasMessage(message || 'No data to display.');
        }
        if (typeof ProcessDataMapState._mapTabAfterRender === 'function') {
            try { ProcessDataMapState._mapTabAfterRender(ProcessDataMapState.network); } catch (eMt) { /* no-op */ }
        }
    }




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
                style: { 'background-color': BLACK, 'border-color': BLACK_BORDER, 'opacity': 0.8 }
            }
        ];
    }



    function renderNetwork(graph) {
        if (!ProcessDataMapState.canvas) return;

        if (graphPipeline && graphPipeline.invalidateGraphCache) {
            graphPipeline.invalidateGraphCache();
        }

        if (!graph.nodes || graph.nodes.length === 0) {
            const msg = ProcessDataMapState.mapType === 'dataset-lineage'
                ? 'No datasets linked from Impact > Datasets or Impact > Attributes. Add datasets or attributes in the Impact tab.'
                : 'No systems linked from Impact > Datasets or Impact > Attributes. Add datasets or attributes in the Impact tab.';
            showPlaceholder(msg);
            updateProcessDataFilterButtonText();
            hideLoading();
            return;
        }

        if (ProcessDataMapState.network) {
            ProcessDataMapState.network.destroy();
            ProcessDataMapState.network = null;
        }

        const elements = [];
        const currentNodeIds = new Set(graph.nodes.filter(n => n.isImpact).map(n => n.id));
        graph.nodes.forEach(n => {
            const isLocked = n.isLocked || (typeof n.id === 'string' && !n.id.startsWith('dataset-') && ProcessDataMapState.inaccessibleSystems.has(String(n.id)));
            const nodeMeta = n.meta || {};
            elements.push({
                data: {
                    id: n.id,
                    label: (isLocked ? '\u{1F512} ' : '') + (n.label || ''),
                    nodeColor: n.nodeColor,
                    borderColor: n.borderColor,
                    backgroundImage: n.backgroundImage || (String(n.id).startsWith('dataset-') ? getDatasetIcon() : adapter.getSystemIcon(n.isImpact)),
                    isLocked: !!isLocked,
                    isCurrent: !!n.isImpact,
                    meta: nodeMeta
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

        ProcessDataMapState.network = cytoscape({
            container: ProcessDataMapState.canvas,
            elements: elements,
            style: getCytoscapeStyle(),
            layout: adapter.buildLayoutOptions(),
            minZoom: 0.3,
            maxZoom: 3
        });
        ProcessDataMapState.network.on('layoutstop', function() {
            ProcessDataMapState.network.fit(50);
        });
        networkIx.attachToCurrentNetwork();
        ProcessDataMapState.nodeMeta.clear();
        graph.nodes.forEach(n => {
            const id = n.id;
            const isDataset = String(id).startsWith('dataset-');
            ProcessDataMapState.nodeMeta.set(id, {
                systemId: isDataset ? null : id,
                datasetId: isDataset ? id.replace(/^dataset-/, '') : null,
                isImpact: n.isImpact,
                isFromDatasets: isDataset ? ProcessDataMapState.impactDatasetIds.has(id.replace(/^dataset-/, '')) : ProcessDataMapState.impactSystemIdsFromDatasets.has(id),
                isFromAttributes: isDataset ? ProcessDataMapState.impactDatasetIdsFromAttributes.has(id.replace(/^dataset-/, '')) : ProcessDataMapState.impactSystemIdsFromAttributes.has(id)
            });
        });
        clearOverlayPanels();
        if (ProcessDataMapState.overlay && ProcessDataMapState.overlay !== 'none') loadOverlayData(ProcessDataMapState.overlay);
        updateLegend();
        refreshProcessDataMapFiltersAfterRender();
        hideLoading();
        if (typeof ProcessDataMapState._mapTabAfterRender === 'function') {
            try { ProcessDataMapState._mapTabAfterRender(ProcessDataMapState.network); } catch (eMt) { /* no-op */ }
        }
    }

    function getLegendHtml() {
        const lineageColors = {
            interfaceLine: EDGE_DASHED ? undefined : '#94a3b8',
            attributeLine: EDGE_LINE ? undefined : '#64748b',
            upstreamHighlight: '#ef4444',
            downstreamHighlight: '#22c55e'
        };
        if (EDGE_DASHED) lineageColors.interfaceLine = EDGE_DASHED;
        if (EDGE_LINE) lineageColors.attributeLine = EDGE_LINE;
        const lineageHtml = (window.SharedMapStyles && typeof window.SharedMapStyles.getLineageLegendHtml === 'function')
            ? window.SharedMapStyles.getLineageLegendHtml(lineageColors)
            : (window.InterfaceMapStyles && typeof window.InterfaceMapStyles.getLineageLegendHtml === 'function')
                ? window.InterfaceMapStyles.getLineageLegendHtml(lineageColors)
                : `
            <div class="legend-item"><div class="legend-line" style="border-bottom:2px dashed ${EDGE_DASHED};"></div><span>Interface Lineage</span></div>
            <div class="legend-item"><div class="legend-line" style="border-bottom:2px solid ${EDGE_LINE};"></div><span>Attribute Lineage</span></div>
        `;
        return `
            <div class="legend-item"><div class="legend-color" style="background:${ORANGE};border:1px solid ${ORANGE_BORDER};"></div><span>Impact System</span><span class="legend-desc"> – Systems from Impact &gt; Datasets or Impact &gt; Attributes (orange).</span></div>
            <div class="legend-item"><div class="legend-color" style="background:${BLACK};border:1px solid ${BLACK_BORDER};"></div><span>Linked System</span><span class="legend-desc"> – Systems directly linked to impact systems (black).</span></div>
            <div class="legend-item"><span class="legend-lock" aria-hidden="true">&#x1F512;</span><span>Segment not accessible</span></div>
            <div class="legend-item"><div class="legend-color" style="background:${ORANGE};border:1px solid ${ORANGE_BORDER};"></div><span>Impact Dataset</span><span class="legend-desc"> – Datasets from Impact (orange).</span></div>
            ${lineageHtml}
        `;
    }

    function updateLegend() {
        const legendEl = document.querySelector('[data-process-data-map-legend]');
        if (legendEl) legendEl.innerHTML = getLegendHtml();
        const dropdown = document.getElementById(MAP_ID + 'LegendDropdown');
        if (dropdown) dropdown.innerHTML = getLegendHtml();
    }

    function toggleSidePanel() {
        const panel = document.getElementById(MAP_ID + 'SidePanel');
        const legendBtn = document.getElementById(MAP_ID + 'Legend');
        if (!panel || !legendBtn) return;
        const isVisible = panel.style.display !== 'none';
        panel.style.display = isVisible ? 'none' : 'flex';
        legendBtn.classList.toggle('active', !isVisible);
    }

    function getOverlayTitle(overlayType, nodeId) {
        if (overlayType === 'description' && nodeId != null && String(nodeId).indexOf('dataset-') === 0) {
            return 'Definition';
        }
        const titles = {
            description: 'Description', glossary: 'Glossaries', datasets: 'Data Sets', dataset: 'Data Sets',
            attributes: 'Attributes', 'linking-attributes': 'Linking Attributes',
            'data-quality': 'Data Quality', 'data-privacy': 'Data Privacy',
            stakeholders: 'Stakeholders', processes: 'Processes', projects: 'Projects', policies: 'Policies',
            'business-area': 'Business Area', products: 'Products', 'legal-entities': 'Legal Entities', geography: 'Geography'
        };
        return titles[overlayType] || overlayType || 'Overlay';
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
            case 'glossary':
                return item.glossaryName || item.glossary_name || item.name || '';
            case 'datasets': case 'dataset':
                return item.name || item.datasetName || item.primaryName || '';
            default: return item.name || item.Name || item.primaryName || item.PrimaryName || '';
        }
    }

    function getOverlayItemId(overlayType, item) {
        if (!item) return null;
        return item.id != null ? item.id : (item.ID != null ? item.ID : (item.Id != null ? item.Id : null));
    }

    function highlightOverlayItem(overlayType, item, sourceNodeId) {
        const canvas = ProcessDataMapState.canvas;
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
        const network = ProcessDataMapState.network;
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
                    const sys = await API.getSystemById(systemId).catch(() => null);
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
                    const sys = await API.getSystemById(systemId).catch(() => null);
                    const s = sys?.data || sys;
                    if (s?.privacyClassification || s?.dataPrivacy || s?.sensitivity)
                        return [{ name: s.privacyClassification || s.dataPrivacy || s.sensitivity, type: 'privacy' }];
                    return [];
                }
                case 'geography': {
                    const sys = await API.getSystemById(systemId).catch(() => null);
                    const s = sys?.data || sys;
                    if (s?.geography || s?.region || s?.country)
                        return [{ name: s.geography || s.region || s.country, type: 'geography' }];
                    return [];
                }
                default: return [];
            }
        } catch (e) { return []; }
    }

    async function fetchOverlayForDatasetNode(datasetId, overlayType) {
        const API = window.BUDG_API_SERVICE;
        const info = ProcessDataMapState.linkedDatasets.get(String(datasetId));
        const dataset = info?.dataset;
        try {
            switch (overlayType) {
                case 'description': {
                    if (dataset) {
                        const desc = dataset.definition || dataset.Definition || dataset.description || dataset.Description;
                        if (desc) return [{ name: 'Definition', value: desc }];
                    }
                    const res = API?.getDatasetById ? await API.getDatasetById(datasetId, null, { silent404: true }).catch(() => null) : null;
                    const d = res?.data || res;
                    const desc = d?.definition || d?.Definition || d?.description || d?.Description;
                    if (desc) return [{ name: 'Definition', value: desc }];
                    return [];
                }
                case 'stakeholders': {
                    const res = await API?.getDatasetStakeholders?.(datasetId).catch(() => null);
                    return Array.isArray(res?.data) ? res.data : (Array.isArray(res) ? res : []);
                }
                case 'processes': {
                    const r = await fetch(`/api/process-impact/datasets/${datasetId}/processes`, { credentials: 'include' });
                    const json = r.ok ? await r.json() : null;
                    return Array.isArray(json?.data) ? json.data : (Array.isArray(json) ? json : []);
                }
                case 'projects': {
                    const r = await fetch(`/api/project-impact/datasets/${datasetId}/projects`, { credentials: 'include' });
                    const json = r.ok ? await r.json() : null;
                    return Array.isArray(json?.data) ? json.data : (Array.isArray(json) ? json : []);
                }
                case 'policies': {
                    const r = await fetch(`/api/policy-impact/datasets/${datasetId}/policies`, { credentials: 'include' });
                    const json = r.ok ? await r.json() : null;
                    return Array.isArray(json?.data) ? json.data : (Array.isArray(json) ? json : []);
                }
                case 'business-area': {
                    const r = await fetch(`/api/businessarea-impact/datasets/${datasetId}/businessareas`, { credentials: 'include' }).catch(() => ({ ok: false }));
                    if (!r.ok && dataset?.businessAreas) return Array.isArray(dataset.businessAreas) ? dataset.businessAreas : [];
                    const json = r.ok ? await r.json() : null;
                    return Array.isArray(json?.data) ? json.data : (Array.isArray(json) ? json : []);
                }
                case 'products': {
                    const r = await fetch(`/api/dataset-impact/${datasetId}/products`, { credentials: 'include' }).catch(() => ({ ok: false }));
                    if (!r.ok && dataset?.products) return Array.isArray(dataset.products) ? dataset.products : [];
                    const json = r.ok ? await r.json() : null;
                    return Array.isArray(json?.data) ? json.data : (Array.isArray(json) ? json : []);
                }
                case 'legal-entities': {
                    const r = await fetch(`/api/dataset-impact/${datasetId}/legals`, { credentials: 'include' }).catch(() => ({ ok: false }));
                    if (!r.ok && dataset?.legalEntities) return Array.isArray(dataset.legalEntities) ? dataset.legalEntities : [];
                    const json = r.ok ? await r.json() : null;
                    return Array.isArray(json?.data) ? json.data : (Array.isArray(json) ? json : []);
                }
                case 'data-quality': {
                    const r = await fetch(`/api/data-quality/dataset/${datasetId}`, { credentials: 'include' }).catch(() => ({ ok: false }));
                    if (r.ok) {
                        const json = await r.json();
                        return Array.isArray(json?.data) ? json.data : (Array.isArray(json) ? json : []);
                    }
                    if (dataset?.qualityRating || dataset?.qualityScore)
                        return [{ name: 'Quality: ' + (dataset.qualityRating || dataset.qualityScore), rating: dataset.qualityRating, score: dataset.qualityScore }];
                    return [];
                }
                case 'data-privacy': {
                    if (dataset?.privacyClassification || dataset?.dataPrivacy || dataset?.sensitivity)
                        return [{ name: dataset.privacyClassification || dataset.dataPrivacy || dataset.sensitivity, type: 'privacy' }];
                    const res = API?.getDatasetById ? await API.getDatasetById(datasetId, null, { silent404: true }).catch(() => null) : null;
                    const d = res?.data || res;
                    if (d?.privacyClassification || d?.dataPrivacy || d?.sensitivity)
                        return [{ name: d.privacyClassification || d.dataPrivacy || d.sensitivity, type: 'privacy' }];
                    return [];
                }
                case 'geography': {
                    if (dataset?.geography || dataset?.region || dataset?.location)
                        return [{ name: dataset.geography || dataset.region || dataset.location, type: 'geography' }];
                    const res = API?.getDatasetById ? await API.getDatasetById(datasetId, null, { silent404: true }).catch(() => null) : null;
                    const d = res?.data || res;
                    if (d?.geography || d?.region || d?.location)
                        return [{ name: d.geography || d.region || d.location, type: 'geography' }];
                    return [];
                }
                default: return [];
            }
        } catch (e) { return []; }
    }

    function clearOverlayPanels() {
        const canvas = ProcessDataMapState.canvas;
        const network = ProcessDataMapState.network;
        if (canvas) {
            const overlayContainer = canvas.querySelector('.map-overlay-container');
            if (overlayContainer) overlayContainer.innerHTML = '';
        }
        if (network) {
            try { network.off('zoom pan', updateOverlayPositions); } catch (e) { /* no-op */ }
            try { network.off('drag', 'node', updateOverlayPositions); } catch (e) { /* no-op */ }
        }
    }

    function positionOverlayPanelElement(panel, node) {
        const overlayContainer = ProcessDataMapState.canvas?.querySelector('.map-overlay-container');
        const network = ProcessDataMapState.network;
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
        const overlayContainer = ProcessDataMapState.canvas?.querySelector('.map-overlay-container');
        if (!overlayContainer || !ProcessDataMapState.network) return;
        const cw = overlayContainer.clientWidth || 800;
        const ch = overlayContainer.clientHeight || 600;
        const buf = 150;

        overlayContainer.querySelectorAll('.map-node-overlay-panel').forEach(panel => {
            const nodeId = panel.getAttribute('data-node-id');
            const node = ProcessDataMapState.network.getElementById(nodeId);

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

    function escapeOverlayHtml(str) {
        if (str == null) return '';
        const div = document.createElement('div');
        div.textContent = String(str);
        return div.innerHTML;
    }

    function formatDataMapOverlayHtml(overlayType, data) {
        if (data == null) return '<p class="map-overlay-empty">No data.</p>';
        if (overlayType === 'description') {
            const list = Array.isArray(data) ? data : (data.items || []);
            const first = list[0];
            const text = first ? getDataMapOverlayItemText('description', first) : '';
            if (!text) return '<p class="map-overlay-empty">No description.</p>';
            return '<div class="map-node-overlay-item map-overlay-description">' + escapeOverlayHtml(text) + '</div>';
        }
        if (overlayType === 'attributes' || overlayType === 'linking-attributes') {
            const list = Array.isArray(data) ? data : (data.items || []);
            if (!list.length) return '<p class="map-overlay-empty">No attributes.</p>';
            const rows = list.slice(0, 30).map(r => {
                const name = (r && (r.name || r.attributeName || r.attribute_name)) || '-';
                const ref = r && (r.refNumber || r.ref || r.attributeRefNumber || '');
                return '<div class="map-node-overlay-item">' + escapeOverlayHtml(name) + (ref ? ' <span class="map-overlay-ref">' + escapeOverlayHtml(ref) + '</span>' : '') + '</div>';
            });
            const more = list.length > 30 ? '<div class="map-overlay-more">+' + (list.length - 30) + ' more</div>' : '';
            return rows.join('') + more;
        }
        if (overlayType === 'glossary') {
            const list = Array.isArray(data) ? data : (data.items || []);
            if (!list.length) return '<p class="map-overlay-empty">No glossary terms.</p>';
            const rows = list.slice(0, 20).map(r => {
                const name = (r && (r.glossaryName || r.name || r.Name)) || '-';
                const ref = r && (r.glossaryRefNumber || r.refNumber || '');
                const srcLabel = getGlossarySourceLabel(r);
                const srcHtml = srcLabel ? ' <span class="map-overlay-ref">(' + escapeOverlayHtml(srcLabel) + ')</span>' : '';
                return '<div class="map-node-overlay-item">' + escapeOverlayHtml(name) + (ref ? ' <span class="map-overlay-ref">' + escapeOverlayHtml(ref) + '</span>' : '') + srcHtml + '</div>';
            });
            const more = list.length > 20 ? '<div class="map-overlay-more">+' + (list.length - 20) + ' more</div>' : '';
            return rows.join('') + more;
        }
        if (overlayType === 'dataset' || overlayType === 'datasets') {
            const list = Array.isArray(data) ? data : (data.items || []);
            if (!list.length) return '<p class="map-overlay-empty">No datasets.</p>';
            const rows = list.slice(0, 20).map(r => {
                const name = (r && (r.name || r.datasetName || r.primaryName)) || '-';
                const ref = r && (r.refNumber || r.ref || '');
                return '<div class="map-node-overlay-item">' + escapeOverlayHtml(name) + (ref ? ' <span class="map-overlay-ref">' + escapeOverlayHtml(ref) + '</span>' : '') + '</div>';
            });
            const more = list.length > 20 ? '<div class="map-overlay-more">+' + (list.length - 20) + ' more</div>' : '';
            return rows.join('') + more;
        }
        var genericListTypes = ['stakeholders', 'processes', 'projects', 'policies', 'business-area', 'products', 'legal-entities', 'data-quality', 'data-privacy', 'geography'];
        if (genericListTypes.indexOf(overlayType) !== -1) {
            const list = Array.isArray(data) ? data : (data.items || []);
            if (!list.length) return '<p class="map-overlay-empty">No data.</p>';
            const rows = list.slice(0, 20).map(r => {
                const text = getDataMapOverlayItemText(overlayType, r);
                return '<div class="map-node-overlay-item">' + escapeOverlayHtml(text || '-') + '</div>';
            });
            const more = list.length > 20 ? '<div class="map-overlay-more">+' + (list.length - 20) + ' more</div>' : '';
            return rows.join('') + more;
        }
        return '<p class="map-overlay-empty">No data.</p>';
    }

    function renderOverlayPanels(overlayType, overlayData) {
        const network = ProcessDataMapState.network;
        const canvas = ProcessDataMapState.canvas;
        if (!network || !canvas) return;
        let overlayContainer = canvas.querySelector('.map-overlay-container');
        if (!overlayContainer) {
            overlayContainer = document.createElement('div');
            overlayContainer.classList.add('map-overlay-container');
            overlayContainer.style.zIndex = '50';
            overlayContainer.style.position = 'absolute';
            overlayContainer.style.top = '0';
            overlayContainer.style.left = '0';
            overlayContainer.style.pointerEvents = 'none';
            canvas.appendChild(overlayContainer);
        }
        overlayContainer.innerHTML = '';
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
            titleSpan.textContent = getOverlayTitle(overlayType, nodeId);
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
            var colDefs = [];
            if (window.OverlayColumns) {
                var allCols = window.OverlayColumns.getOverlayColumns(overlayType);
                var selectedIds = window.OverlayColumns.resolveSelectedColumnIds(ProcessDataMapState.overlayColumnsByType[overlayType], overlayType);
                colDefs = allCols.filter(function(c) { return selectedIds && selectedIds.indexOf(c.id) !== -1; });
            }
            let currentFilter = '';
            let currentMax = 10;
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
                    const trHead = document.createElement('tr');
                    colDefs.forEach(c => { const th = document.createElement('th'); th.style.cssText = 'text-align:left;padding:4px 6px;border-bottom:1px solid #e5e7eb;'; th.textContent = c.label; trHead.appendChild(th); });
                    thead.appendChild(trHead);
                    table.appendChild(thead);
                    const tbody = document.createElement('tbody');
                    displayItems.forEach((item) => {
                        const tr = document.createElement('tr');
                        tr.className = 'map-node-overlay-item';
                        tr.style.cursor = 'pointer';
                        tr.style.borderBottom = '1px solid #f3f4f6';
                        const itemText = getDataMapOverlayItemText(overlayType, item) || '-';
                        tr.dataset.overlayValue = itemText;
                        const itemId = item.id != null ? item.id : (item.ID != null ? item.ID : null);
                        if (itemId) { tr.dataset.itemId = String(itemId); if (overlayType === 'attributes' || overlayType === 'linking-attributes') tr.dataset.attributeId = String(itemId); }
                        colDefs.forEach(c => { const td = document.createElement('td'); td.style.padding = '4px 6px'; td.textContent = getOverlayItemField(overlayType, item, c.id); tr.appendChild(td); });
                        tr.addEventListener('click', function(e) { e.stopPropagation(); highlightOverlayItem(overlayType, item, panel.getAttribute('data-node-id')); });
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
                        if (itemId) { row.dataset.itemId = String(itemId); if (overlayType === 'attributes' || overlayType === 'linking-attributes') row.dataset.attributeId = String(itemId); }
                        row.addEventListener('click', function(e) { e.stopPropagation(); highlightOverlayItem(overlayType, item, panel.getAttribute('data-node-id')); });
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
        const network = ProcessDataMapState.network;
        if (!network) return;
        const overlayData = new Map();
        const isSystemLineage = ProcessDataMapState.mapType === 'system-lineage';
        const nodeIds = network.nodes().map(n => n.data('id'));
        const normAttr = window.OverlayRowNormalize && typeof window.OverlayRowNormalize.attributeFromApi === 'function'
            ? window.OverlayRowNormalize.attributeFromApi : null;
        const normDs = window.OverlayRowNormalize && typeof window.OverlayRowNormalize.datasetFromApi === 'function'
            ? window.OverlayRowNormalize.datasetFromApi : null;

        if (overlayType === 'attributes') {
            for (const nodeId of nodeIds) {
                const meta = ProcessDataMapState.nodeMeta.get(String(nodeId));
                if (!meta) continue;
                let items = [];
                if (isSystemLineage && meta.systemId) {
                    if (meta.isFromDatasets) {
                        const datasetIds = ProcessDataMapState.impactSystemIdToDatasetIds.get(meta.systemId) || [];
                        for (const did of datasetIds) {
                            if (!ProcessDataMapState.impactDatasetIdToAttributes.has(did)) {
                                try {
                                    const r = await fetch(`/api/attribute/${did}`, { credentials: 'include' });
                                    const json = r.ok ? await r.json() : null;
                                    const arr = Array.isArray(json?.data) ? json.data : (Array.isArray(json) ? json : []);
                                    ProcessDataMapState.impactDatasetIdToAttributes.set(did, arr.map(a => (
                                        normAttr ? normAttr(a, { datasetId: String(did) }) : {
                                            id: a.id || a.ID,
                                            name: a['Name attribute'] || a.name || a.primaryName || '',
                                            glossaryId: a.glossaryId ?? a.glossary_id ?? a.Glossary_ID,
                                            glossaryName: a['Glossary Name attribute'] || a.glossaryName || a.glossary_name || a.GlossaryName || ''
                                        }
                                    )));
                                } catch (e) { ProcessDataMapState.impactDatasetIdToAttributes.set(did, []); }
                            }
                            items = items.concat(ProcessDataMapState.impactDatasetIdToAttributes.get(did) || []);
                        }
                    }
                    if (meta.isFromAttributes) items = items.concat(ProcessDataMapState.impactSystemIdToAttributeIds.get(meta.systemId) || []);
                    // Dedupe by id when system is both A and B
                    if (items.length > 1) {
                        const seen = new Set();
                        items = items.filter(a => {
                            const id = String(a.id ?? '');
                            if (seen.has(id)) return false;
                            seen.add(id);
                            return true;
                        });
                    }
                    if (!meta.isImpact) {
                        try {
                            const API = window.BUDG_API_SERVICE;
                            let sysDatasets = [];
                            if (API && typeof API.getSystemDatasets === 'function') {
                                const res = await API.getSystemDatasets(meta.systemId).catch(() => null);
                                const data = res?.data ?? res;
                                sysDatasets = Array.isArray(data) ? data : (Array.isArray(data?.data) ? data.data : []);
                            } else {
                                const sysDatasetsRes = await fetch(`/api/system-data/${meta.systemId}/datasets`, { credentials: 'include' });
                                const sysDatasetsJson = sysDatasetsRes.ok ? await sysDatasetsRes.json() : null;
                                sysDatasets = Array.isArray(sysDatasetsJson?.data) ? sysDatasetsJson.data : (Array.isArray(sysDatasetsJson) ? sysDatasetsJson : []);
                            }
                            for (const sd of sysDatasets) {
                                const did = sd.id ?? sd.datasetId ?? sd.dataset_id;
                                if (!did) continue;
                                const attrRes = await fetch(`/api/attribute/${did}`, { credentials: 'include' });
                                const attrJson = attrRes.ok ? await attrRes.json() : null;
                                const arr = Array.isArray(attrJson?.data) ? attrJson.data : (Array.isArray(attrJson) ? attrJson : []);
                                items = items.concat(arr.map(a => (
                                    normAttr ? normAttr(a, { datasetId: String(did) }) : { id: a.id || a.ID, name: a['Name attribute'] || a.name || a.primaryName || '' }
                                )));
                            }
                        } catch (e) { }
                        // Attributes overlay: show all attributes for this system (no filter)
                    }
                } else if (!isSystemLineage && meta.datasetId) {
                    const info = ProcessDataMapState.linkedDatasets.get(meta.datasetId);
                    if (meta.isImpact) {
                        if (meta.isFromDatasets) {
                            if (!ProcessDataMapState.impactDatasetIdToAttributes.has(meta.datasetId)) {
                                try {
                                    const r = await fetch(`/api/attribute/${meta.datasetId}`, { credentials: 'include' });
                                    const json = r.ok ? await r.json() : null;
                                    const arr = Array.isArray(json?.data) ? json.data : (Array.isArray(json) ? json : []);
                                    ProcessDataMapState.impactDatasetIdToAttributes.set(meta.datasetId, arr.map(a => (
                                        normAttr ? normAttr(a, { datasetId: String(meta.datasetId) }) : {
                                            id: a.id || a.ID,
                                            name: a['Name attribute'] || a.name || a.primaryName || '',
                                            glossaryId: a.glossaryId ?? a.glossary_id ?? a.Glossary_ID,
                                            glossaryName: a['Glossary Name attribute'] || a.glossaryName || a.glossary_name || a.GlossaryName || ''
                                        }
                                    )));
                                } catch (e) { ProcessDataMapState.impactDatasetIdToAttributes.set(meta.datasetId, []); }
                            }
                            items = ProcessDataMapState.impactDatasetIdToAttributes.get(meta.datasetId) || [];
                        } else {
                            items = ProcessDataMapState.impactDatasetIdToImpactAttributeIds.get(meta.datasetId) || [];
                        }
                    } else {
                        if (!ProcessDataMapState.impactDatasetIdToAttributes.has(meta.datasetId)) {
                            try {
                                const r = await fetch(`/api/attribute/${meta.datasetId}`, { credentials: 'include' });
                                const json = r.ok ? await r.json() : null;
                                const arr = Array.isArray(json?.data) ? json.data : (Array.isArray(json) ? json : []);
                                ProcessDataMapState.impactDatasetIdToAttributes.set(meta.datasetId, arr.map(a => (
                                    normAttr ? normAttr(a, { datasetId: String(meta.datasetId) }) : {
                                        id: a.id || a.ID,
                                        name: a['Name attribute'] || a.name || a.primaryName || '',
                                        glossaryId: a.glossaryId ?? a.glossary_id ?? a.Glossary_ID,
                                        glossaryName: a['Glossary Name attribute'] || a.glossaryName || a.glossary_name || a.GlossaryName || ''
                                    }
                                )));
                            } catch (e) { ProcessDataMapState.impactDatasetIdToAttributes.set(meta.datasetId, []); }
                        }
                        // Attributes overlay: show all attributes for this dataset (no filter)
                        items = ProcessDataMapState.impactDatasetIdToAttributes.get(meta.datasetId) || [];
                    }
                }
                overlayData.set(String(nodeId), Array.isArray(items) ? items : { items: items });
            }
        } else if (overlayType === 'linking-attributes') {
            for (const nodeId of nodeIds) {
                const meta = ProcessDataMapState.nodeMeta.get(String(nodeId));
                if (!meta) continue;
                let items = [];
                if (isSystemLineage && meta.systemId) {
                    const impactAttrs = ProcessDataMapState.impactSystemIdToAttributeIds.get(meta.systemId) || [];
                    const datasetIds = ProcessDataMapState.impactSystemIdToDatasetIds.get(meta.systemId) || [];
                    let allAttrs = impactAttrs.slice();
                    for (const did of datasetIds) {
                        if (ProcessDataMapState.impactDatasetIdToAttributes.has(did)) allAttrs = allAttrs.concat(ProcessDataMapState.impactDatasetIdToAttributes.get(did));
                        else {
                            try {
                                const r = await fetch(`/api/attribute/${did}`, { credentials: 'include' });
                                const json = r.ok ? await r.json() : null;
                                const arr = Array.isArray(json?.data) ? json.data : (Array.isArray(json) ? json : []);
                                const mapped = arr.map(a => (
                                    normAttr ? normAttr(a, { datasetId: String(did) }) : {
                                        id: a.id || a.ID,
                                        name: a['Name attribute'] || a.name || a.primaryName || '',
                                        glossaryId: a.glossaryId ?? a.glossary_id ?? a.Glossary_ID,
                                        glossaryName: a['Glossary Name attribute'] || a.glossaryName || a.glossary_name || a.GlossaryName || ''
                                    }
                                ));
                                ProcessDataMapState.impactDatasetIdToAttributes.set(did, mapped);
                                allAttrs = allAttrs.concat(mapped);
                            } catch (e) { }
                        }
                    }
                    items = allAttrs.filter(a => ProcessDataMapState.attributeIdsWithLinks.has(String(a.id)));
                } else if (!isSystemLineage && meta.datasetId) {
                    const info = ProcessDataMapState.linkedDatasets.get(meta.datasetId);
                    let allAttrs = [];
                    if (ProcessDataMapState.impactDatasetIdToAttributes.has(meta.datasetId)) allAttrs = ProcessDataMapState.impactDatasetIdToAttributes.get(meta.datasetId);
                    else if (meta.isImpact) {
                        try {
                            const r = await fetch(`/api/attribute/${meta.datasetId}`, { credentials: 'include' });
                            const json = r.ok ? await r.json() : null;
                            const arr = Array.isArray(json?.data) ? json.data : (Array.isArray(json) ? json : []);
                            allAttrs = arr.map(a => (
                                normAttr ? normAttr(a, { datasetId: String(meta.datasetId) }) : {
                                    id: a.id || a.ID,
                                    name: a['Name attribute'] || a.name || a.primaryName || '',
                                    glossaryId: a.glossaryId ?? a.glossary_id ?? a.Glossary_ID,
                                    glossaryName: a['Glossary Name attribute'] || a.glossaryName || a.glossary_name || a.GlossaryName || ''
                                }
                            ));
                            ProcessDataMapState.impactDatasetIdToAttributes.set(meta.datasetId, allAttrs);
                        } catch (e) { }
                    }
                    items = allAttrs.filter(a => ProcessDataMapState.attributeIdsWithLinks.has(String(a.id)));
                }
                overlayData.set(String(nodeId), Array.isArray(items) ? items : { items: items });
            }
        } else if (overlayType === 'glossary') {
            const API = window.BUDG_API_SERVICE;
            for (const nodeId of nodeIds) {
                const meta = ProcessDataMapState.nodeMeta.get(String(nodeId));
                if (!meta) { overlayData.set(String(nodeId), { items: [] }); continue; }
                const glossaryItems = [];
                if (isSystemLineage && meta.systemId) {
                    if (meta.isImpact) {
                        // System A: system glossary + all glossaries of attributes in this system's impact datasets.
                        // System B: system glossary + only glossaries of attributes added in Impact > Data Attributes.
                        if (meta.isFromDatasets) {
                            const datasetIds = ProcessDataMapState.impactSystemIdToDatasetIds.get(meta.systemId) || [];
                            for (const did of datasetIds) {
                                if (!ProcessDataMapState.impactDatasetIdToAttributes.has(did)) {
                                    try {
                                        const r = await fetch(`/api/attribute/${did}`, { credentials: 'include' });
                                        const json = r.ok ? await r.json() : null;
                                        const arr = Array.isArray(json?.data) ? json.data : (Array.isArray(json) ? json : []);
                                        ProcessDataMapState.impactDatasetIdToAttributes.set(did, arr.map(a => (
                                            normAttr ? normAttr(a, { datasetId: String(did) }) : {
                                                id: a.id || a.ID, name: a['Name attribute'] || a.name || '', glossaryId: a.glossaryId ?? a.glossary_id ?? a.Glossary_ID, glossaryName: a['Glossary Name attribute'] || a.glossaryName || a.glossary_name || a.GlossaryName || ''
                                            }
                                        )));
                                    } catch (e) { ProcessDataMapState.impactDatasetIdToAttributes.set(did, []); }
                                }
                                (ProcessDataMapState.impactDatasetIdToAttributes.get(did) || []).forEach(a => {
                                    if (a.glossaryId != null && a.glossaryName) glossaryItems.push({ glossaryName: a.glossaryName, glossaryRefNumber: '', glossaryId: a.glossaryId, source: 'attribute' });
                                });
                            }
                        }
                        if (meta.isFromAttributes) {
                            (ProcessDataMapState.impactSystemIdToAttributeIds.get(meta.systemId) || []).forEach(a => {
                                if (a.glossaryId != null && a.glossaryName) glossaryItems.push({ glossaryName: a.glossaryName, glossaryRefNumber: '', glossaryId: a.glossaryId, source: 'attribute' });
                            });
                        }
                        // Dedupe by glossary id/name so same glossary is not shown twice (e.g. system is both A and B, or multiple attributes share same glossary).
                        const seen = new Set();
                        const deduped = glossaryItems.filter(x => {
                            const key = (x.glossaryId != null ? String(x.glossaryId) : '') || (x.glossaryName || '');
                            if (seen.has(key)) return false;
                            seen.add(key);
                            return true;
                        });
                        glossaryItems.length = 0;
                        deduped.forEach(x => glossaryItems.push({
                            glossaryName: x.glossaryName,
                            glossaryRefNumber: x.glossaryRefNumber || '',
                            glossaryId: x.glossaryId,
                            id: x.glossaryId,
                            name: x.glossaryName,
                            glossary: x.glossaryName,
                            source: x.source
                        }));
                        // System glossary first (associated with this system).
                        try {
                            const sys = API && typeof API.getSystemById === 'function' ? await API.getSystemById(meta.systemId) : null;
                            const s = sys?.data || sys;
                            const gid = s?.primaryGlossaryId ?? s?.glossaryId ?? s?.glossary_id;
                            const gname = s?.primaryGlossaryName ?? s?.glossaryName ?? s?.glossary_name;
                            if (gid != null && gname) glossaryItems.unshift({
                                glossaryName: gname,
                                glossaryRefNumber: '',
                                glossaryId: gid,
                                id: gid,
                                name: gname,
                                glossary: gname,
                                source: 'system'
                            });
                        } catch (e) { }
                    } else {
                        // Black systems: unknown (system vs attribute glossaries may differ).
                        glossaryItems.push({ glossaryName: 'Unknown (system vs attribute glossaries may differ)', glossaryRefNumber: '' });
                    }
                } else if (!isSystemLineage && meta.datasetId) {
                    const info = ProcessDataMapState.linkedDatasets.get(meta.datasetId);
                    const dataset = info?.dataset || {};
                    const gid = dataset.glossaryId ?? dataset.glossary_id;
                    const gname = dataset.glossaryName || dataset.glossary_name;
                    if (gid != null && gname) glossaryItems.push({ glossaryName: gname, glossaryRefNumber: '', source: 'dataset' });
                    if (meta.isImpact) {
                        (ProcessDataMapState.impactDatasetIdToImpactAttributeIds.get(meta.datasetId) || ProcessDataMapState.impactDatasetIdToAttributes.get(meta.datasetId) || []).forEach(a => {
                            if (a.glossaryId != null && a.glossaryName) glossaryItems.push({ glossaryName: a.glossaryName, glossaryRefNumber: '', source: 'attribute' });
                        });
                    } else {
                        if (!ProcessDataMapState.impactDatasetIdToAttributes.has(meta.datasetId)) {
                            try {
                                const r = await fetch(`/api/attribute/${meta.datasetId}`, { credentials: 'include' });
                                const json = r.ok ? await r.json() : null;
                                const arr = Array.isArray(json?.data) ? json.data : (Array.isArray(json) ? json : []);
                                ProcessDataMapState.impactDatasetIdToAttributes.set(meta.datasetId, arr.map(a => ({
                                    id: a.id || a.ID,
                                    name: a['Name attribute'] || a.name || a.primaryName || '',
                                    glossaryId: a.glossaryId ?? a.glossary_id ?? a.Glossary_ID,
                                    glossaryName: a['Glossary Name attribute'] || a.glossaryName || a.glossary_name || a.GlossaryName || ''
                                })));
                            } catch (e) { ProcessDataMapState.impactDatasetIdToAttributes.set(meta.datasetId, []); }
                        }
                        const allAttrs = ProcessDataMapState.impactDatasetIdToAttributes.get(meta.datasetId) || [];
                        allAttrs.filter(a => ProcessDataMapState.attributeIdsWithLinks.has(String(a.id))).forEach(a => {
                            if (a.glossaryId != null && a.glossaryName) glossaryItems.push({ glossaryName: a.glossaryName, glossaryRefNumber: '', source: 'attribute' });
                        });
                    }
                }
                let glossaryOut = glossaryItems;
                if (window.OverlayColumns && typeof window.OverlayColumns.enrichGlossaryOverlayTerms === 'function') {
                    glossaryOut = await window.OverlayColumns.enrichGlossaryOverlayTerms(glossaryItems);
                }
                overlayData.set(String(nodeId), { items: glossaryOut });
            }
        } else if (overlayType === 'dataset') {
            for (const nodeId of nodeIds) {
                const meta = ProcessDataMapState.nodeMeta.get(String(nodeId));
                if (!meta) { overlayData.set(String(nodeId), { items: [] }); continue; }
                let datasetItems = [];
                if (isSystemLineage && meta.systemId) {
                    if (meta.isImpact) {
                        datasetItems = ProcessDataMapState.impactSystemIdToDatasets.get(meta.systemId) || [];
                        datasetItems = datasetItems.map(d => (normDs ? normDs(d) : { id: d.id, name: d.name, refNumber: d.ref }));
                    } else {
                        try {
                            const API = window.BUDG_API_SERVICE;
                            let arr = [];
                            if (API && typeof API.getSystemDatasets === 'function') {
                                const res = await API.getSystemDatasets(meta.systemId).catch(() => null);
                                const data = res?.data ?? res;
                                arr = Array.isArray(data) ? data : (Array.isArray(data?.data) ? data.data : []);
                            } else {
                                const r = await fetch(`/api/system-data/${meta.systemId}/datasets`, { credentials: 'include' });
                                const json = r.ok ? await r.json() : null;
                                arr = Array.isArray(json?.data) ? json.data : (Array.isArray(json) ? json : []);
                            }
                            datasetItems = arr.filter(d => ProcessDataMapState.impactLinkedDatasetIds.has(String(d.id ?? d.datasetId ?? d.dataset_id))).map(d => (
                                normDs ? normDs(d) : {
                                    id: d.id ?? d.datasetId ?? d.dataset_id,
                                    name: d.name || d.datasetName || d.primaryName || '',
                                    refNumber: d.refNumber || d.ref || ''
                                }
                            ));
                        } catch (e) { }
                    }
                } else if (!isSystemLineage && meta.datasetId) {
                    const info = ProcessDataMapState.linkedDatasets.get(meta.datasetId);
                    const d = info?.dataset;
                    if (d) datasetItems = [normDs ? normDs(d, { id: d.id ?? meta.datasetId }) : { id: d.id ?? meta.datasetId, name: d.name || d.primaryName || '', refNumber: d.refNumber || d.ref || '' }];
                }
                overlayData.set(String(nodeId), { items: datasetItems });
            }
        } else if (overlayType === 'datasets') {
            for (const nodeId of nodeIds) {
                const meta = ProcessDataMapState.nodeMeta.get(String(nodeId));
                if (!meta) { overlayData.set(String(nodeId), { items: [] }); continue; }
                let datasetItems = [];
                if (isSystemLineage && meta.systemId) {
                    if (meta.isImpact) {
                        datasetItems = ProcessDataMapState.impactSystemIdToDatasets.get(meta.systemId) || [];
                        datasetItems = datasetItems.map(d => (normDs ? normDs(d) : { id: d.id, name: d.name, refNumber: d.ref }));
                    } else {
                        try {
                            const API = window.BUDG_API_SERVICE;
                            let arr = [];
                            if (API && typeof API.getSystemDatasets === 'function') {
                                const res = await API.getSystemDatasets(meta.systemId).catch(() => null);
                                const data = res?.data ?? res;
                                arr = Array.isArray(data) ? data : (Array.isArray(data?.data) ? data.data : []);
                            } else {
                                const r = await fetch(`/api/system-data/${meta.systemId}/datasets`, { credentials: 'include' });
                                const json = r.ok ? await r.json() : null;
                                arr = Array.isArray(json?.data) ? json.data : (Array.isArray(json) ? json : []);
                            }
                            datasetItems = arr.filter(d => ProcessDataMapState.impactLinkedDatasetIds.has(String(d.id ?? d.datasetId ?? d.dataset_id))).map(d => (
                                normDs ? normDs(d) : {
                                    id: d.id ?? d.datasetId ?? d.dataset_id,
                                    name: d.name || d.datasetName || d.primaryName || '',
                                    refNumber: d.refNumber || d.ref || ''
                                }
                            ));
                        } catch (e) { }
                    }
                } else if (!isSystemLineage && meta.datasetId) {
                    const info = ProcessDataMapState.linkedDatasets.get(meta.datasetId);
                    const d = info?.dataset;
                    if (d) datasetItems = [normDs ? normDs(d, { id: d.id ?? meta.datasetId }) : { id: d.id ?? meta.datasetId, name: d.name || d.primaryName || '', refNumber: d.refNumber || d.ref || '' }];
                }
                overlayData.set(String(nodeId), { items: datasetItems });
            }
        } else if (['description', 'stakeholders', 'processes', 'projects', 'policies', 'business-area', 'products', 'legal-entities', 'data-quality', 'data-privacy', 'geography'].indexOf(overlayType) !== -1) {
            for (const nodeId of nodeIds) {
                const meta = ProcessDataMapState.nodeMeta.get(String(nodeId));
                if (!meta) { overlayData.set(String(nodeId), { items: [] }); continue; }
                let items = [];
                if (isSystemLineage && meta.systemId) {
                    items = await fetchOverlayForSystemNode(meta.systemId, overlayType);
                } else if (!isSystemLineage && meta.datasetId) {
                    items = await fetchOverlayForDatasetNode(meta.datasetId, overlayType);
                }
                overlayData.set(String(nodeId), Array.isArray(items) ? { items: items } : { items: [] });
            }
        } else {
            network.nodes().forEach(node => overlayData.set(String(node.data('id')), null));
        }
        renderOverlayPanels(overlayType, overlayData);
    }

    function setOverlay(overlay) {
        ProcessDataMapState.overlay = overlay;
        clearOverlayPanels();
        if (overlay === 'none' || !overlay) return;
        loadOverlayData(overlay);
    }

    function setOverlayColumns(overlayType, columnIds) {
        if (!overlayType) return;
        ProcessDataMapState.overlayColumnsByType[overlayType] = Array.isArray(columnIds) ? columnIds.slice() : [];
        if (ProcessDataMapState.overlay === overlayType) {
            loadOverlayData(overlayType);
        }
    }

    function getOverlayColumns(overlayType) {
        var raw = ProcessDataMapState.overlayColumnsByType[overlayType];
        if (window.OverlayColumns && typeof window.OverlayColumns.resolveSelectedColumnIds === 'function') {
            return window.OverlayColumns.resolveSelectedColumnIds(raw, overlayType);
        }
        return (Array.isArray(raw) && raw.length > 0) ? raw.slice() : (window.OverlayColumns ? window.OverlayColumns.getDefaultOverlayColumnIds(overlayType) : ['name']);
    }

    function getState() {
        return ProcessDataMapState;
    }

    function getOverlayItemField(overlayType, item, fieldId) {
        if (!item) return '';
        const v = (x) => (x != null && x !== '') ? String(x) : '';
        function fieldDefault(it, fid) {
            const d = it[fid];
            if (d !== undefined && d !== null && d !== '') return v(d);
            if (window.MapOverlayFieldPick && typeof window.MapOverlayFieldPick.pick === 'function') {
                return window.MapOverlayFieldPick.pick(it, fid);
            }
            return '';
        }
        function entityNameCell() {
            return v(
                item.processName || item.ProcessName ||
                item.projectName || item.ProjectName ||
                item.policyName || item.PolicyName ||
                item.businessAreaName || item.BusinessAreaName ||
                item.productName || item.ProductName ||
                item.legalEntityName || item.LegalEntityName ||
                item.legalShortName || item.LegalShortName ||
                item.legalLongName || item.LegalLongName ||
                item.clientName || item.ClientName ||
                item.capabilityName || item.CapabilityName ||
                item.systemName || item.SystemName ||
                item.name || item.Name || item.primaryName || item.PrimaryName ||
                item.glossaryName || item.GlossaryName || item.ruleName || item.RuleName ||
                item.longName || item.longname ||
                item.region || item.country
            );
        }
        switch (overlayType) {
            case 'glossary':
                switch (fieldId) {
                    case 'name': return v(item.glossaryName || item.glossary_name || item.glossary || item.name || item.primaryName);
                    case 'source': return item.source === 'dataset' ? 'Dataset Glossary' : item.source === 'attribute' ? 'Attribute Glossary' : item.source === 'system' ? 'System Glossary' : '';
                    case 'aliasNames': return v(item.aliasNames || item.aliases || item.alias);
                    case 'parentName': return v(item.parentName || item.parent?.name);
                    case 'lifecycle': return v(item.lifecycleName || item.LifecycleName || item.lifecycle || item.Lifecycle || item.lifecycleStatusName || item.LifecycleStatusName || item.lifecycleStatus || item.LifecycleStatus || item.processLifecycleName || item.ProcessLifecycleName || item.sourceProcessLifecycleName || item.targetProcessLifecycleName || item.datasetLifecycleName || item.DatasetLifecycleName || fieldDefault(item, fieldId));
                    case 'securityClassification': return v(item.securityClassification || item.classification);
                    default: return fieldDefault(item, fieldId);
                }
            case 'description':
                return fieldId === 'value'
                    ? v(item.value || item.definition || item.Definition || item.description || item.Description)
                    : fieldDefault(item, fieldId);
            case 'datasets':
                switch (fieldId) {
                    case 'name': return v(item.primaryName || item.PrimaryName || item.name || item.shortName || item.datasetName);
                    case 'refNumber': return v(item.refNumber || item.refnumber || item.RefNumber || item.ref || item.Ref || item.processRefNumber || item.ProcessRefNumber || item.processRef || item.ProcessRef || item.projectRefNumber || item.ProjectRefNumber || item.projectRef || item.ProjectRef || item.productRefNumber || item.ProductRefNumber || item.policyRefNumber || item.PolicyRefNumber || item.capabilityRefNumber || item.CapabilityRefNumber || item.datasetRefNumber || item.DatasetRefNumber || item.attributeRefNumber || item.AttributeRefNumber || item.glossaryRefNumber || item.GlossaryRefNumber || item.interfaceRefNumber || item.InterfaceRefNumber || item.systemRef || item.SystemRef || item.regulationRefNumber || item.RegulationRefNumber || item.regulatoryThemeRefNumber || item.RegulatoryThemeRefNumber || item.businessAreaReference || item.BusinessAreaReference || item.clientReference || item.ClientReference || item.legalReference || item.LegalReference || item.sourceProcessRef || item.targetProcessRef);
                    case 'type': return v(item.typeName || item.type || item.TypeName || item.Type);
                    case 'lifecycle': return v(item.lifecycleName || item.LifecycleName || item.lifecycle || item.Lifecycle || item.lifecycleStatusName || item.LifecycleStatusName || item.lifecycleStatus || item.LifecycleStatus || item.processLifecycleName || item.ProcessLifecycleName || item.sourceProcessLifecycleName || item.targetProcessLifecycleName || item.datasetLifecycleName || item.DatasetLifecycleName || fieldDefault(item, fieldId));
                    default: return fieldDefault(item, fieldId);
                }
            case 'attributes':
            case 'linking-attributes':
                switch (fieldId) {
                    case 'name': return v(item.name || item['Name attribute'] || item.attributeName || item.primaryName || item.PrimaryName || item.Name);
                    case 'type': return v(item.typeName || item.type || item.TypeName || item.Type || item.dataType || item.DataType);
                    case 'glossary': return v(item.glossaryName || item.glossary || item['Glossary Name attribute']);
                    case 'refNumber': return v(item.refNumber || item.refnumber || item.RefNumber || item.ref || item.Ref || item.processRefNumber || item.ProcessRefNumber || item.processRef || item.ProcessRef || item.projectRefNumber || item.ProjectRefNumber || item.projectRef || item.ProjectRef || item.productRefNumber || item.ProductRefNumber || item.policyRefNumber || item.PolicyRefNumber || item.capabilityRefNumber || item.CapabilityRefNumber || item.datasetRefNumber || item.DatasetRefNumber || item.attributeRefNumber || item.AttributeRefNumber || item.glossaryRefNumber || item.GlossaryRefNumber || item.interfaceRefNumber || item.InterfaceRefNumber || item.systemRef || item.SystemRef || item.regulationRefNumber || item.RegulationRefNumber || item.regulatoryThemeRefNumber || item.RegulatoryThemeRefNumber || item.businessAreaReference || item.BusinessAreaReference || item.clientReference || item.ClientReference || item.legalReference || item.LegalReference || item.sourceProcessRef || item.targetProcessRef);
                    case 'direction': return v(item.direction || item.Direction);
                    case 'relatedDataset': return v(item.relatedDataset || item.related_dataset);
                    case 'relatedAttribute': return v(item.relatedAttribute || item.related_attribute);
                    case 'dataType': return v(item.dataType || item.typeName || item.type);
                    case 'lifecycle': return v(item.lifecycleName || item.LifecycleName || item.lifecycle || item.Lifecycle || item.lifecycleStatusName || item.LifecycleStatusName || item.lifecycleStatus || item.LifecycleStatus || item.processLifecycleName || item.ProcessLifecycleName || item.sourceProcessLifecycleName || item.targetProcessLifecycleName || item.datasetLifecycleName || item.DatasetLifecycleName || fieldDefault(item, fieldId));
                    case 'securityClassification': return v(item.securityClassification || item.classification);
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
                    case 'name': return v(item.personName || item.PersonName || item.name || item.Name);
                    case 'role': return v(item.roleName || item.RoleName || item.role || item.Role);
                    case 'accepted': return v(item.accepted || item.Accepted || item.acceptedStatus);
                    case 'orgUnit': return v(item.orgUnit || item.OrgUnit || item.orgUnitName);
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
            case 'systems':
            case 'data-quality':
            case 'data-privacy':
            case 'geography':
                switch (fieldId) {
                    case 'name': return entityNameCell();
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
                return v(item.name || item.Name || item.primaryName || item.PrimaryName);
            }
        }
    }

    /** Overlays available only in System Lineage (hidden in Data Set Lineage). */
    const SYSTEM_LINEAGE_ONLY_OVERLAYS = ['datasets', 'data-privacy', 'business-area', 'products', 'legal-entities', 'geography'];

    function updateOverlayMenuForMapType() {
        const overlayMenu = document.getElementById(MAP_ID + 'OverlayMenu');
        if (!overlayMenu) return;
        const isSystemLineage = ProcessDataMapState.mapType === 'system-lineage';
        overlayMenu.querySelectorAll('.overlay-menu-item[data-map-type="system-lineage"]').forEach(el => {
            el.style.display = isSystemLineage ? '' : 'none';
        });
        overlayMenu.querySelectorAll('.overlay-menu-column[data-map-type="system-lineage"]').forEach(el => {
            el.style.display = isSystemLineage ? '' : 'none';
        });
        if (!isSystemLineage && ProcessDataMapState.overlay && SYSTEM_LINEAGE_ONLY_OVERLAYS.indexOf(ProcessDataMapState.overlay) !== -1) {
            ProcessDataMapState.overlay = 'none';
            clearOverlayPanels();
            const textEl = document.getElementById(MAP_ID + 'OverlayBtnText');
            if (textEl) textEl.textContent = 'None';
            overlayMenu.querySelectorAll('.overlay-menu-item').forEach(i => i.classList.remove('active'));
        }
    }


    function setMapType(mapType) {
        ProcessDataMapState.mapType = mapType;
        updateOverlayMenuForMapType();
        updateProcessDataFilterButtonText();
        if (ProcessDataMapState.processId) loadMapData();
    }

    function setLayout(layout) {
        ProcessDataMapState.layout = layout;
        if (ProcessDataMapState.network) {
            ProcessDataMapState.network.layout(adapter.buildLayoutOptions()).run();
        }
    }

    function setHopsCount(count) {
        const val = Math.min(99, Math.max(1, parseInt(count, 10) || 15));
        ProcessDataMapState.hopsCount = val;
        if (ProcessDataMapState.processId) loadMapData();
    }

    // ── Shared toolbar actions (called by SharedMapControls adapter) ──────────
    function exportAsPng() {
        window.MapRenderUtils.exportLineageMapToPng({
            network: ProcessDataMapState.network,
            canvas: ProcessDataMapState.canvas,
            filename: 'process-data-map-' + ProcessDataMapState.processId + '-' + Date.now() + '.png',
            emptyMessage: 'No map to export. Wait for the map to finish loading or add Impact data.',
            failMessage: 'Could not export the map. Try again after the map finishes loading.',
            logPrefix: '[ProcessDataMap]'
        });
    }

    function openFullscreen() {
        if (!ProcessDataMapState.network) return;
        if (typeof window.openMapFullscreen === 'function') {
            window.openMapFullscreen({
                title:          'Process Data Map - ' + (ProcessDataMapState.processId || ''),
                network:        ProcessDataMapState.network,
                getCytoscapeStyle: getCytoscapeStyle,
                layoutName:     ProcessDataMapState.layout || 'top-to-bottom',
                legendHtml:     getLegendHtml(),
                exportFilename: 'process-data-map-' + ProcessDataMapState.processId + '.png',
                toolbarAnchor:  ProcessDataMapState.canvas,
                mapType:        ProcessDataMapState.mapType || 'system-lineage',
                mapTabKind:     'process-data',
                entityId:       ProcessDataMapState.processId,
                getState:       function () { return ProcessDataMapState; }
            });
        } else {
            const wrapper = document.getElementById(MAP_ID + 'Wrapper');
            if (wrapper) try { if (!document.fullscreenElement) wrapper.requestFullscreen?.(); else document.exitFullscreen?.(); } catch (e) { /* fullscreen API may reject */ }
        }
    }

    function setupControls() {
        const container = document.getElementById(MAP_ID + 'Wrapper') || document.getElementById(MAP_ID + 'Container');
        if (!container) return;
        const bindOnce = !container.dataset.processDataMapControlsBound;
        if (bindOnce) {
            container.dataset.processDataMapControlsBound = '1';
        }

        const typeSelect = document.getElementById(MAP_ID + 'TypeSelect');
        if (bindOnce) {
            if (typeSelect) typeSelect.addEventListener('change', () => setMapType(typeSelect.value));
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
        if (bindOnce) {
            window.MapRenderUtils.wireLineageToolbarSharedControls({
                mapId: MAP_ID,
                adapter: adapter,
                state: ProcessDataMapState,
                loadMapData: loadMapData,
                setLayout: setLayout,
                exportAsPng: exportAsPng,
                openFullscreen: openFullscreen,
                getLegendHtml: getLegendHtml
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
                const ot = ProcessDataMapState.overlay || '';
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
                    input.id = 'overlayCol_processDataMap_' + ot + '_' + col.id;
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
        if (bindOnce) {
            const filterBtn = document.getElementById(MAP_ID + 'FilterBtn');
            const filterSi = document.getElementById('filter' + MAP_ID + 'systemInterfaces');
            const filterDa = document.getElementById('filter' + MAP_ID + 'dataAttributeLinks');
            if (filterBtn) {
                filterBtn.addEventListener('click', function (e) {
                    e.stopPropagation();
                    const menu = getActiveDataFilterMenu();
                    if (menu) {
                        const wasOpen = menu.classList.contains('open');
                        getDataMapRoot()?.querySelectorAll('.map-filter-menu').forEach(function (m) { m.classList.remove('open'); });
                        if (!wasOpen) menu.classList.add('open');
                    }
                });
            }
            if (filterSi) {
                filterSi.addEventListener('change', function () {
                    setLinkFilter('systemInterfaces', filterSi.checked);
                });
            }
            if (filterDa) {
                filterDa.addEventListener('change', function () {
                    setLinkFilter('dataAttributeLinks', filterDa.checked);
                });
            }
            const onDynamicFilterChange = function (e) {
                const t = e.target;
                if (!t || t.tagName !== 'INPUT' || t.type !== 'checkbox') return;
                if (t.id === 'filter' + MAP_ID + 'systemInterfaces' || t.id === 'filter' + MAP_ID + 'dataAttributeLinks') return;
                const menu = t.closest('.map-filter-menu');
                if (!menu || !getDataMapRoot()?.contains(menu)) return;
                if (ProcessDataMapState.mapType === 'dataset-lineage') {
                    syncDatasetNodeFiltersFromDomAndApply();
                } else {
                    syncSystemNodeFiltersFromDomAndApply();
                }
            };
            getDataMapRoot()?.addEventListener('change', onDynamicFilterChange);
            document.addEventListener('click', function (e) {
                const root = getDataMapRoot();
                if (!root) return;
                const active = getActiveDataFilterMenu();
                if (active && filterBtn && !active.contains(e.target) && !filterBtn.contains(e.target)) {
                    active.classList.remove('open');
                }
            });
            updateProcessDataFilterButtonText();
        }

        if (bindOnce && overlayBtn && overlayMenu) {
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
        updateOverlayMenuForMapType();
    }

    function init(processId) {
        ProcessDataMapState.processId = processId;
        const container = document.getElementById('processDataMapContainer');
        if (!container) return;
        ProcessDataMapState.canvas = null;
        if (typeof cytoscape === 'undefined') {
            container.innerHTML = window.MapRenderUtils.htmlMapLibraryUnavailable();
            return;
        }
        if (!document.getElementById(MAP_ID + 'Canvas')) {
            container.innerHTML = getMapHtml();
        }
        ProcessDataMapState.canvas = document.getElementById(MAP_ID + 'Canvas');
        if (!ProcessDataMapState.canvas) return;
        ProcessDataMapState.initialized = true;
        setupControls();
        loadMapData();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Graph pipeline + MapEngine (shared/map/process-data-facet-graph.js)
    // ─────────────────────────────────────────────────────────────────────────

    graphPipeline = window.createProcessDataFacetGraphPipeline({
        state: ProcessDataMapState,
        adapter: adapter,
        renderNetwork: renderNetwork,
        showLoading: showLoading,
        hideLoading: hideLoading,
        showPlaceholder: showPlaceholder,
        logPrefix: '[ProcessDataMap]'
    });

    var _mapEngineApiLoader = graphPipeline._mapEngineApiLoader;
    var _mapEngineNodeBuilder = graphPipeline._mapEngineNodeBuilder;
    var _mapEngineEdgeBuilder = graphPipeline._mapEngineEdgeBuilder;

    networkIx = window.createProcessDataMapNetworkInteractions({
        state: ProcessDataMapState,
        buildSystemLineageGraph: buildSystemLineageGraph,
        buildDatasetLineageGraph: buildDatasetLineageGraph,
        renderNetwork: renderNetwork
    });

    if (window.MapConfigs && window.MapConfigs.register) {
        window.MapConfigs.register('process-data', {
            apiLoader:     _mapEngineApiLoader,
            nodeBuilder:   _mapEngineNodeBuilder,
            edgeBuilder:   _mapEngineEdgeBuilder,
            legendBuilder: getLegendHtml
        });
    }

    var _processDataEngine = new window.MapEngine('process-data');

    Object.assign(_processDataEngine, {
        init: init,
        loadMapData: loadMapData,
        /** /view/full_map/…: point engine at fullscreen canvas and reload from API */
        adoptForMapTab: function (processId, canvasEl) {
            if (!processId || !canvasEl) return;
            ProcessDataMapState.processId = processId;
            ProcessDataMapState.canvas = canvasEl;
            ensureMapTabProcessFilterWiring();
        },
        applyMapTabToolbarPrefs: function (prefs) {
            if (!prefs || typeof prefs !== 'object') return;
            if (prefs.mapType != null) {
                ProcessDataMapState.mapType = prefs.mapType;
                updateOverlayMenuForMapType();
                updateProcessDataFilterButtonText();
            }
            if (prefs.layout != null) ProcessDataMapState.layout = prefs.layout;
            if (prefs.hopsCount != null) {
                ProcessDataMapState.hopsCount = Math.min(99, Math.max(1, parseInt(prefs.hopsCount, 10) || 15));
            }
            if (typeof prefs.showEdgeLabels === 'boolean') {
                ProcessDataMapState.showEdgeLabels = prefs.showEdgeLabels;
            }
        },
        setMapTabAfterRender: function (fn) {
            ProcessDataMapState._mapTabAfterRender = typeof fn === 'function' ? fn : null;
        },
        setMapType: setMapType,
        setLayout: setLayout,
        setHopsCount: setHopsCount,
        setOverlay: setOverlay,
        setOverlayColumns: setOverlayColumns,
        getOverlayColumns: getOverlayColumns,
        resetMap: function () {
            ProcessDataMapState.hiddenNodes.clear();
            if (ProcessDataMapState.network) ProcessDataMapState.network.fit(undefined, 50);
        },
        redrawMap: function () {
            loadMapData();
        },
        toggleInterfaceLabels: function (show) {
            if (!ProcessDataMapState.network) return;
            var cur = ProcessDataMapState.showEdgeLabels !== false;
            var on = show !== undefined ? !!show : !cur;
            ProcessDataMapState.showEdgeLabels = on;
            ProcessDataMapState.network.edges().style('label', on ? 'data(label)' : '');
        },
        getState: function () { return ProcessDataMapState; },
        /** HTML for /view/full_map/process/{id} toolbar host (no canvas wrapper). */
        getToolbarHtmlForFullscreen: function () { return getToolbarOnlyHtml(); }
    });

    Object.defineProperty(_processDataEngine, 'cy', {
        get: function () { return ProcessDataMapState.network; },
        configurable: true
    });

    window.ProcessDataMap = _processDataEngine;
})();
