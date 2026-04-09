/**
 * System Interfaces Map Component
 * Implements Axon-style System Lineage Map for the Interfaces tab
 * Reference: https://knowledge.informatica.com/s/article/626346
 * 
 * Dependencies:
 * - map-render-utils.js (MapRenderUtils.escapeHtml, …) — required, load before this file
 * - map-graph-utils.js (createMapAdapter, MapGraphUtils.buildDataFlowKey) — required, load before this file
 * - shared/map/map-styles.js + map-icons.js (InterfaceMapStyles / InterfaceMapIcons)
 * - shared/map/system-lineage-network-interactions.js (createSystemLineageNetworkInteractions)
 */

(function() {
    'use strict';

    function embLog() {
        if (typeof window !== 'undefined' && window.BUDG_DEBUG_EMBEDDED_MAPS === true) {
            console.log.apply(console, arguments);
        }
    }
    function embWarn() {
        if (typeof window !== 'undefined' && window.BUDG_DEBUG_EMBEDDED_MAPS === true) {
            console.warn.apply(console, arguments);
        }
    }

    function getMapColors() {
        return window.MapRenderUtils.getMapColors(window.MapRenderUtils.SYSTEM_LINEAGE_MAP_COLOR_FALLBACK);
    }

    function isValidSystemIdForMap(id) {
        if (id === null || id === undefined) return false;
        if (typeof id === 'string') {
            const t = id.trim().toLowerCase();
            if (t === '' || t === 'null' || t === 'undefined' || t === 'nan') return false;
        }
        return true;
    }

    // Map State Management
    const InterfaceMapState = {
        initialized: false,
        dagreRegistered: false,
        network: null,
        canvas: null,
        loadingEl: null,
        systemId: null,
        systemData: null,
        interfacesData: [],
        dataFlowData: [],
        connectedSystems: new Map(),
        inaccessibleSystems: new Set(),
        interfaceIds: new Set(),
        dataFlowKeys: new Set(),
        hiddenNodes: new Set(),
        focusedNode: null,
        highlightedNodeId: null,
        hiddenUpstreamNodes: new Set(), // Track nodes with hidden upstream
        hiddenDownstreamNodes: new Set(), // Track nodes with hidden downstream
        layout: 'top-to-bottom',
        mapType: 'system-lineage', // Default to system-lineage
        overlay: 'none',
        /** overlayType -> selected column ids for overlay panels (e.g. { glossary: ['name', 'aliasNames'] }) */
        overlayColumnsByType: {},
        hopsCount: 15, // 1-99, Axon recommends 15 for lineage
        showInterfaceLabels: true,
        filters: {
            systemInterfaces: true,
            dataAttributeLinks: true
        },
        nodeFilters: {
            classifications: [],
            types: [],
            lifecycles: []
        },
        // Dataset lineage state
        datasetsData: [],
        linkedDatasets: new Map(), // datasetId -> { dataset, systemId, isLocked }
        datasetRelationships: [], // relationships between datasets via attributes
        datasetNodeFilters: {
            types: [],
            lifecycles: []
        }
    };

    const adapter = window.createMapAdapter(InterfaceMapState, 'interfaceMap', { zoomRecenter: true });

    function ensureDagreRegisteredForInterfaceMap() {
        if (InterfaceMapState.dagreRegistered || typeof cytoscape === 'undefined') return;
        try {
            var dagreExt = window.cytoscapeDagre || window['cytoscape-dagre'];
            if (dagreExt) {
                cytoscape.use(dagreExt);
                InterfaceMapState.dagreRegistered = true;
                embLog('[INTERFACE-MAP] Dagre layout registered');
            } else {
                embWarn('[INTERFACE-MAP] Dagre layout extension not found, will use fallback layout');
            }
        } catch (eReg) {
            embWarn('[INTERFACE-MAP] Error registering dagre layout:', eReg);
        }
    }

    // Initialize the map
    function init(systemId, containerSelector) {
        embLog('[INTERFACE-MAP] Initializing map for system:', systemId, 'container:', containerSelector);
        // Only replace systemId when valid. MapEngine.prototype.setMapType (before override) called
        // init(this._state.entityId) with entityId never synced — that would clear a good id to null.
        if (isValidSystemIdForMap(systemId)) {
            InterfaceMapState.systemId = systemId;
        }
        var selector = (typeof containerSelector === 'string' && containerSelector) ? containerSelector : '#interfaceMapCanvas';
        var canvasEl = (containerSelector && typeof containerSelector === 'object' && containerSelector.nodeType === 1)
            ? containerSelector
            : document.querySelector(selector);
        InterfaceMapState.canvas = canvasEl;
        InterfaceMapState.loadingEl = canvasEl ? canvasEl.querySelector('[data-interface-map-loading]') : document.querySelector('[data-interface-map-loading]');

        if (!InterfaceMapState.canvas) {
            console.error('[INTERFACE-MAP] Canvas element not found');
            return;
        }

        InterfaceMapState.canvas.addEventListener('contextmenu', function(e) { e.preventDefault(); });

        // Check if Cytoscape is available
        if (typeof cytoscape === 'undefined') {
            console.error('[INTERFACE-MAP] Cytoscape library not available');
            InterfaceMapState.canvas.innerHTML = window.MapRenderUtils.htmlCytoscapeUnavailable();
            return;
        }

        ensureDagreRegisteredForInterfaceMap();

        // Determine map type based on which canvas is being initialized
        const isDataMapCanvas = selector === '#dataMapCanvas' || InterfaceMapState.canvas.id === 'dataMapCanvas';
        const isInterfaceMapCanvas = selector === '#interfaceMapCanvas' || InterfaceMapState.canvas.id === 'interfaceMapCanvas';
        
        // Set map type based on canvas
        if (isDataMapCanvas) {
            // Data Map tab - always use dataset-lineage
            InterfaceMapState.mapType = 'dataset-lineage';
            embLog('[INTERFACE-MAP] Initializing for Data Map - setting map type to dataset-lineage');
        } else if (isInterfaceMapCanvas) {
            // INTERFACES tab - always use system-lineage
            InterfaceMapState.mapType = 'system-lineage';
            embLog('[INTERFACE-MAP] Initializing for INTERFACES tab - setting map type to system-lineage');
        }

        InterfaceMapState.initialized = true;
        
        // Prefer explicit id from this call; otherwise keep existing state (survives bad init(null) from MapEngine)
        var effectiveSystemId = isValidSystemIdForMap(systemId) ? systemId : InterfaceMapState.systemId;

        // Load map data based on the determined map type
        if (!isValidSystemIdForMap(effectiveSystemId)) {
            embWarn('[INTERFACE-MAP] Skipping map load: invalid or missing system id');
            showPlaceholder('System id is missing. Reload the page or open this map from a system record.');
            return;
        }
        if (InterfaceMapState.mapType === 'dataset-lineage') {
            // For dataset lineage, check if data is already loaded
            if (InterfaceMapState.linkedDatasets.size > 0 || InterfaceMapState.datasetRelationships.length > 0) {
                const graph = buildDatasetLineageGraph();
                renderNetwork(graph);
                updateLegend();
            } else {
                // Data not loaded yet, load it
                loadMapData(effectiveSystemId);
            }
        } else {
            // System lineage - always load map data
            loadMapData(effectiveSystemId);
        }
    }

    var graphPipeline = null;
    var styleLayoutCtl = null;
    var networkIx = null;

    async function loadMapData(systemId) { return graphPipeline.loadMapData(systemId); }
    async function loadDatasetLineageData() { return graphPipeline.loadDatasetLineageData(); }
    function buildSystemLineageGraph() { return graphPipeline.buildSystemLineageGraph(); }
    function buildDatasetLineageGraph() { return graphPipeline.buildDatasetLineageGraph(); }
    function getNeighborsForSystem(systemId) { return graphPipeline.getNeighborsForSystem(systemId); }
    async function expandConnectedSystemsLineage(maxDepth) { return graphPipeline.expandConnectedSystemsLineage(maxDepth); }
    async function fetchAndMergeSystem(systemId) { return graphPipeline.fetchAndMergeSystem(systemId); }


    // Render the network using Cytoscape
    function renderNetwork(graph) {
        if (!InterfaceMapState.canvas) return;

        // Destroy existing network
        if (InterfaceMapState.network) {
            InterfaceMapState.network.destroy();
            InterfaceMapState.network = null;
        }

        if (graph.nodes.length === 0) {
            if (overlayCtl && typeof overlayCtl.clearOverlayPanels === 'function') {
                overlayCtl.clearOverlayPanels();
            }
            InterfaceMapState.overlay = 'none';
            showPlaceholder('No interface connections found for this system.');
            return;
        }

        const elements = window.MapRenderUtils.buildSystemLineageCytoscapeElements(graph, {
            getMapColors: getMapColors,
            getNodeIcon: getNodeIcon
        });

        // Initialize Cytoscape
        InterfaceMapState.network = cytoscape({
            container: InterfaceMapState.canvas,
            elements: elements,
            style: getCytoscapeStyle(),
            layout: buildCytoscapeLayout(),
            // Removed wheelSensitivity to use default (avoids warning)
            minZoom: 0.3,
            maxZoom: 3
        });

        // Add event listeners
        setupEventListeners();

        var activeOv = InterfaceMapState.overlay;
        if (activeOv && activeOv !== 'none' && overlayCtl && typeof overlayCtl.loadOverlayData === 'function') {
            overlayCtl.loadOverlayData(activeOv, { quiet: true });
        }

        if (typeof InterfaceMapState._mapTabAfterRender === 'function') {
            try { InterfaceMapState._mapTabAfterRender(InterfaceMapState.network); } catch (eMt) { embWarn('[INTERFACE-MAP] map-tab after render', eMt); }
        }
    }

    function getCytoscapeStyle() {
        return styleLayoutCtl.getCytoscapeStyle();
    }

    function buildCytoscapeLayout() {
        return styleLayoutCtl.buildCytoscapeLayout();
    }

    function setupEventListeners() {
        networkIx.setupEventListeners();
    }

    // Show edge info
    function showEdgeInfo(edgeData) {
        embLog('[INTERFACE-MAP] Edge clicked:', edgeData);
        // Could show a tooltip or info panel with interface details
    }

    // Create database icon for system nodes - uses external icons module if available
    function createDatabaseIcon(isCurrent) {
        // Try to use external icons module
        if (window.InterfaceMapIcons && typeof window.InterfaceMapIcons.createDatabaseIcon === 'function') {
            return window.InterfaceMapIcons.createDatabaseIcon(isCurrent);
        }

        // Fallback inline icon
        const iconColor = '#ffffff';
        const svg = `<svg width="40" height="40" viewBox="0 0 40 40" xmlns="http://www.w3.org/2000/svg">
            <rect x="6" y="4" width="28" height="6" rx="2" fill="${iconColor}" opacity="0.95"/>
            <rect x="6" y="14" width="28" height="6" rx="2" fill="${iconColor}" opacity="0.95"/>
            <rect x="6" y="24" width="28" height="6" rx="2" fill="${iconColor}" opacity="0.95"/>
        </svg>`;
        return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
    }

    // Get icon by node type - uses external icons module if available
    function getNodeIcon(nodeType, isCurrent) {
        if (window.InterfaceMapIcons && typeof window.InterfaceMapIcons.getIconByType === 'function') {
            // For dataset nodes, use dataset icon
            if (nodeType === 'dataset') {
                return window.InterfaceMapIcons.createDatasetIcon('#ffffff');
            }
            return window.InterfaceMapIcons.getIconByType(nodeType, isCurrent);
        }
        return createDatabaseIcon(isCurrent);
    }

    function getLegendHtml() {
        const colors = getMapColors();
        const lineageHtml = (window.SharedMapStyles && typeof window.SharedMapStyles.getLineageLegendHtml === 'function')
            ? window.SharedMapStyles.getLineageLegendHtml(colors, { includeEucTypes: true })
            : (window.InterfaceMapStyles && typeof window.InterfaceMapStyles.getLineageLegendHtml === 'function')
                ? window.InterfaceMapStyles.getLineageLegendHtml(colors, { includeEucTypes: true })
                : '';
        return `
            <div class="legend-item">
                <div class="legend-color" style="background: ${colors.currentSystem};"></div>
                <span>Current System</span>
            </div>
            <div class="legend-item">
                <div class="legend-color" style="background: ${colors.otherSystem};"></div>
                <span>Connected System</span>
            </div>
            ${lineageHtml}
            <div class="legend-item">
                <div class="legend-line" style="border-bottom: 2px solid ${colors.upstreamHighlight};"></div>
                <span>Upstream</span>
            </div>
            <div class="legend-item">
                <div class="legend-line" style="border-bottom: 2px solid ${colors.downstreamHighlight};"></div>
                <span>Downstream</span>
            </div>
            <div class="legend-item">
                <div class="legend-color" style="background: ${colors.lockedSystem};"></div>
                <span>Locked / Inaccessible System</span>
            </div>
            <div class="legend-note" style="margin-top: 12px; padding-top: 12px; border-top: 1px solid var(--border-color, #e5e7eb); font-size: 11px; color: var(--text-secondary, #6b7280);">
                <strong>Note:</strong> Solid lines may appear on top of dotted lines when both interface and lineage exist between systems.
            </div>
        `;
    }

    function updateLegend() {
        const legendContainer = document.querySelector('[data-interface-map-legend]');
        if (legendContainer) legendContainer.innerHTML = getLegendHtml();
        const dropdown = document.getElementById('interfaceMapLegendDropdown');
        if (dropdown) dropdown.innerHTML = getLegendHtml();
    }

    // UI Helper Functions
    function showLoading() {
        if (InterfaceMapState.loadingEl) {
            InterfaceMapState.loadingEl.style.display = 'flex';
        }
    }

    function hideLoading() {
        if (InterfaceMapState.loadingEl) {
            InterfaceMapState.loadingEl.style.display = 'none';
        }
    }

    function showPlaceholder(message) {
        if (InterfaceMapState.canvas) {
            InterfaceMapState.canvas.innerHTML = window.MapRenderUtils.htmlMapCanvasCenteredDiagram(message);
        }
        if (typeof InterfaceMapState._mapTabAfterRender === 'function') {
            try { InterfaceMapState._mapTabAfterRender(null); } catch (ePh) { /* no-op */ }
        }
    }

    // Control Functions (exposed for toolbar)
    function setMapType(type) {
        // Tear down overlays before destroying cytoscape (panels/listeners reference the network)
        if (overlayCtl && typeof overlayCtl.clearOverlayPanels === 'function') {
            overlayCtl.clearOverlayPanels();
        }
        InterfaceMapState.overlay = 'none';
        var simInst = window.SystemInterfacesMap;
        if (simInst && simInst._state) {
            simInst._state.overlay = 'none';
        }

        InterfaceMapState.mapType = type;
        embLog('[INTERFACE-MAP] Setting map type to:', type);
        
        // Clear existing network to ensure clean state
        if (InterfaceMapState.network) {
            InterfaceMapState.network.destroy();
            InterfaceMapState.network = null;
        }
        
        // Clear hidden nodes and focus state when switching map types
        InterfaceMapState.hiddenNodes.clear();
        InterfaceMapState.focusedNode = null;
        InterfaceMapState.hiddenUpstreamNodes.clear();
        InterfaceMapState.hiddenDownstreamNodes.clear();
        
        if (type === 'dataset-lineage') {
            if (!isValidSystemIdForMap(InterfaceMapState.systemId)) {
                embWarn('[INTERFACE-MAP] Cannot load dataset lineage: system id not set');
                showPlaceholder('System id is missing. Open the Data Map from a system record.');
                return;
            }
            // Load dataset lineage data and build graph
            embLog('[INTERFACE-MAP] Loading dataset lineage data...');
            loadDatasetLineageData().then(() => {
                embLog('[INTERFACE-MAP] Dataset lineage data loaded, building graph...');
                const graph = buildDatasetLineageGraph();
                embLog('[INTERFACE-MAP] Graph built:', graph.nodes.length, 'nodes,', graph.edges.length, 'edges');
                renderNetwork(graph);
                updateLegend();
            }).catch(error => {
                console.error('[INTERFACE-MAP] Failed to load dataset lineage:', error);
                showPlaceholder('Failed to load dataset lineage. Please try refreshing.');
            });
        } else {
            // System lineage - reload map data to get system data
            // Only load if systemId is set
            if (InterfaceMapState.systemId) {
                loadMapData(InterfaceMapState.systemId);
            } else {
                embWarn('[INTERFACE-MAP] Cannot load system lineage: systemId not set yet');
            }
        }
    }

    function setLayout(layout) {
        InterfaceMapState.layout = layout;
        if (InterfaceMapState.network) {
            InterfaceMapState.network.layout(buildCytoscapeLayout()).run();
        }
    }

    // Set hops count (1-99, Axon recommends 15)
    function setHopsCount(count) {
        const val = Math.min(99, Math.max(1, parseInt(count, 10) || 15));
        InterfaceMapState.hopsCount = val;
        if (InterfaceMapState.network) {
            const graph = InterfaceMapState.mapType === 'dataset-lineage' ? buildDatasetLineageGraph() : buildSystemLineageGraph();
            renderNetwork(graph);
        }
    }

    var overlayCtl = null;

    function updateOverlayPositions() {
        if (overlayCtl) overlayCtl.updateOverlayPositions();
    }

    function setFilter(filterType, enabled) {
        if (filterType !== 'systemInterfaces' && filterType !== 'dataAttributeLinks') {
            return;
        }
        InterfaceMapState.filters[filterType] = enabled;
        if (_systemEngineInstance && _systemEngineInstance._state && _systemEngineInstance._state.filters) {
            _systemEngineInstance._state.filters.systemInterfaces = InterfaceMapState.filters.systemInterfaces;
            _systemEngineInstance._state.filters.dataAttributeLinks = InterfaceMapState.filters.dataAttributeLinks;
        }
        if (InterfaceMapState.mapType !== 'system-lineage' && InterfaceMapState.mapType !== 'dataset-lineage') {
            return;
        }
        const graph = InterfaceMapState.mapType === 'dataset-lineage'
            ? buildDatasetLineageGraph()
            : buildSystemLineageGraph();
        renderNetwork(graph);
    }

    function setNodeFilters(nodeFilters) {
        InterfaceMapState.nodeFilters = {
            classifications: nodeFilters.classifications || [],
            types: nodeFilters.types || [],
            lifecycles: nodeFilters.lifecycles || []
        };
        // Mark that filters have been initialized (so empty arrays mean "hide all" not "show all")
        InterfaceMapState.filtersInitialized = true;
        // Re-filter and re-render the network
        applyNodeFiltersToNetwork();
    }

    // Set dataset node filters (for Dataset Lineage)
    function setDatasetNodeFilters(datasetFilters) {
        InterfaceMapState.datasetNodeFilters = {
            types: datasetFilters.types || [],
            lifecycles: datasetFilters.lifecycles || []
        };
        // Re-filter and re-render the network
        adapter.applyDatasetNodeFilters({
            linkedDatasets: InterfaceMapState.linkedDatasets,
            updateOverlayPositions: updateOverlayPositions,
            filtersInitialized: true
        });
    }

    function applyNodeFiltersToNetwork() {
        if (!InterfaceMapState.network) return;
        window.MapGraphUtils.applySystemNodeFilters(
            InterfaceMapState.network,
            InterfaceMapState.nodeFilters,
            {
                filtersInitialized: InterfaceMapState.filtersInitialized === true,
                updateOverlayPositions: updateOverlayPositions,
                logPrefix: '[INTERFACE-MAP]'
            }
        );
    }

    function toggleInterfaceLabels(show) {
        InterfaceMapState.showInterfaceLabels = show;
        const graph = InterfaceMapState.mapType === 'dataset-lineage'
            ? buildDatasetLineageGraph()
            : buildSystemLineageGraph();
        renderNetwork(graph);
    }

    function zoomIn()  { adapter.zoomIn(); }
    function zoomOut() { adapter.zoomOut(); }

    function resetMap() {
        InterfaceMapState.hiddenNodes.clear();
        InterfaceMapState.focusedNode = null;
        InterfaceMapState.hiddenUpstreamNodes.clear();
        InterfaceMapState.hiddenDownstreamNodes.clear();
        networkIx.resetHighlights();
        const isDatasetLineage = InterfaceMapState.mapType === 'dataset-lineage';
        const graph = isDatasetLineage ? buildDatasetLineageGraph() : buildSystemLineageGraph();
        renderNetwork(graph);
    }

    function redrawMap() {
        if (InterfaceMapState.network) {
            InterfaceMapState.network.layout(buildCytoscapeLayout()).run();
        }
    }

    function exportAsPng() {
        if (!InterfaceMapState.network) {
            const m = (window.I18n && window.I18n.t('system.interfacesMap.messages.noMapToExport')) || 'No map to export';
            if (typeof window.showNotification === 'function') { window.showNotification(m, 'warning'); } else { alert(m); }
            return;
        }
        const filename = `system-lineage-map-${InterfaceMapState.systemId}-${Date.now()}.png`;
        if (typeof window.exportMapWithOverlays === 'function') {
            window.exportMapWithOverlays(InterfaceMapState.network, InterfaceMapState.canvas, filename);
        } else {
            const png64 = InterfaceMapState.network.png({ bg: '#ffffff', full: true, scale: 2 });
            const link = document.createElement('a');
            link.href = png64;
            link.download = filename;
            link.click();
        }
    }

    function openFullscreen() {
        if (!InterfaceMapState.network) {
            const m = (window.I18n && window.I18n.t('system.interfacesMap.messages.noMapToOpen')) || 'No map to open';
            if (typeof window.showNotification === 'function') { window.showNotification(m, 'warning'); } else { alert(m); }
            return;
        }
        if (typeof window.openMapFullscreen === 'function') {
            window.openMapFullscreen({
                title: 'System Lineage Map - ' + (InterfaceMapState.systemData?.name || 'System'),
                network: InterfaceMapState.network,
                getCytoscapeStyle: getCytoscapeStyle,
                layoutName: InterfaceMapState.layout || 'top-to-bottom',
                toolbarAnchor: InterfaceMapState.canvas,
                mapType: InterfaceMapState.mapType || 'system-lineage',
                mapTabKind: 'system-interfaces',
                entityId: InterfaceMapState.systemId,
                exportFilename: 'system-lineage-map-' + InterfaceMapState.systemId + '.png',
                getState: function () { return InterfaceMapState; }
            });
        } else {
            const newWindow = window.open('', '_blank');
            if (newWindow) {
                const elements = InterfaceMapState.network.json().elements;
                newWindow.document.write('<!DOCTYPE html><html><head><title>System Lineage Map</title><style>body{margin:0;padding:0;}#fullscreenMap{width:100vw;height:100vh;}</style><script src="/assets/js/cytoscape.min.js"><\/script></head><body><div id="fullscreenMap"></div><script>cytoscape({container:document.getElementById("fullscreenMap"),elements:' + JSON.stringify(elements) + ',style:' + JSON.stringify(getCytoscapeStyle()) + ',layout:{name:"preset"}});<\/script></body></html>');
                newWindow.document.close();
            }
        }
    }

    function toggleNavigator() {
        const container = InterfaceMapState.canvas && InterfaceMapState.canvas.parentElement;
        if (!container || !InterfaceMapState.network || !window.MapRenderUtils) return;
        window.MapRenderUtils.attachOrToggleLineageMinimap({
            container: container,
            network: InterfaceMapState.network,
            minimapCanvasId: 'interfaceMapMinimap'
        });
    }

    // Function to get all interfaces from map (for table display)
    // This includes interfaces from the current system AND all connected systems
    function getAllInterfaces() {
        const allInterfaces = [...(InterfaceMapState.interfacesData || [])];
        
        // Also collect interfaces from connected systems
        InterfaceMapState.connectedSystems.forEach((systemInfo, systemId) => {
            if (systemInfo.interfacesData) {
                const ifaceList = Array.isArray(systemInfo.interfacesData?.data) 
                    ? systemInfo.interfacesData.data 
                    : (Array.isArray(systemInfo.interfacesData) ? systemInfo.interfacesData : []);
                
                // Add interfaces that aren't already in the list
                ifaceList.forEach(iface => {
                    const existing = allInterfaces.find(existing => 
                        existing.id === iface.id || 
                        (existing.fromId === iface.fromId && existing.toId === iface.toId && existing.name === iface.name)
                    );
                    if (!existing) {
                        allInterfaces.push(iface);
                    }
                });
            }
        });
        
        return allInterfaces;
    }

    // Check if a system has data attributes (dataAttributes > 0)
    function checkSystemHasDataAttributes(systemId) {
        // Check interfaces
        const hasInterfaceAttributes = InterfaceMapState.interfacesData.some(iface => {
            const fromId = iface.fromId || iface.sourceSystemId;
            const toId = iface.toId || iface.targetSystemId;
            const isRelated = String(fromId) === String(systemId) || String(toId) === String(systemId);
            return isRelated && (iface.dataAttributes || 0) > 0;
        });
        
        // Check data flow
        const hasDataFlowAttributes = InterfaceMapState.dataFlowData.some(flow => {
            const fromId = flow.fromId;
            const toId = flow.toId;
            const isRelated = String(fromId) === String(systemId) || String(toId) === String(systemId);
            return isRelated && (flow.dataAttributes || 0) > 0;
        });
        
        return hasInterfaceAttributes || hasDataFlowAttributes;
    }

    // Function to notify that map data has been updated (so table can refresh)
    function notifyMapDataUpdated() {
        // Dispatch custom event so table can update
        if (typeof window !== 'undefined' && window.dispatchEvent) {
            window.dispatchEvent(new CustomEvent('systemMapInterfacesUpdated', {
                detail: {
                    interfaces: InterfaceMapState.interfacesData,
                    systemId: InterfaceMapState.systemId
                }
            }));
        }
    }

    // === MapEngine Unified Architecture Integration ===
    // ─────────────────────────────────────────────────────────────────────────
    // MapEngine integration: apiLoader / nodeBuilder / edgeBuilder
    //
    // The system-lineage map has two distinct graph builders chosen by mapType:
    //   'system-lineage'  → buildSystemLineageGraph()
    //   'dataset-lineage' → buildDatasetLineageGraph()
    //
    // loadMapData() has an internal cache-skip for dataset-lineage when data is
    // already loaded (linkedDatasets / datasetRelationships).  The apiLoader
    // always resets these collections before loading so every init() call
    // produces fresh data.
    // ─────────────────────────────────────────────────────────────────────────

    styleLayoutCtl = window.createSystemLineageCytoscapeStyleLayout({
        state: InterfaceMapState,
        adapter: adapter,
        getMapColors: getMapColors,
        getNodeIcon: getNodeIcon,
        mapId: 'interfaceMap',
        logPrefix: '[INTERFACE-MAP]'
    });

    networkIx = window.createSystemLineageNetworkInteractions({
        state: InterfaceMapState,
        getMapColors: getMapColors,
        buildSystemLineageGraph: buildSystemLineageGraph,
        buildDatasetLineageGraph: buildDatasetLineageGraph,
        renderNetwork: renderNetwork,
        showEdgeInfo: showEdgeInfo
    });

    graphPipeline = window.createSystemLineageGraphPipeline({
        state: InterfaceMapState,
        adapter: adapter,
        renderNetwork: renderNetwork,
        showPlaceholder: showPlaceholder,
        updateLegend: updateLegend,
        showLoading: showLoading,
        hideLoading: hideLoading,
        logPrefix: '[INTERFACE-MAP]',
        onMapDataUpdated: notifyMapDataUpdated
    });

    var _mapEngineApiLoader = graphPipeline._mapEngineApiLoader;
    var _mapEngineNodeBuilder = graphPipeline._mapEngineNodeBuilder;
    var _mapEngineEdgeBuilder = graphPipeline._mapEngineEdgeBuilder;

    // === MapEngine Unified Architecture Integration ===
    if (window.MapConfigs && window.MapConfigs.register) {
        window.MapConfigs.register('system-lineage', {
            apiLoader:     _mapEngineApiLoader,
            nodeBuilder:   _mapEngineNodeBuilder,
            edgeBuilder:   _mapEngineEdgeBuilder,
            legendBuilder: getLegendHtml
        });
    }

    overlayCtl = window.createSystemLineageOverlayController({
        state: InterfaceMapState,
        showLoading: showLoading,
        hideLoading: hideLoading,
        checkSystemHasDataAttributes: checkSystemHasDataAttributes,
        extractGlossaryNameFromAttr: function (attr) {
            return adapter.extractGlossaryNameFromAttr(attr);
        },
        logPrefix: '[INTERFACE-MAP]'
    });

    var _systemEngineInstance = new window.MapEngine('system-lineage');

    _systemEngineInstance.setOverlay = function (overlay) {
        InterfaceMapState.overlay = overlay;
        this._state.overlay = overlay;
        if (!overlay || overlay === 'none') {
            overlayCtl.clearOverlayPanels();
            return;
        }
        overlayCtl.loadOverlayData(overlay);
    };
    _systemEngineInstance.setOverlayColumns = function (overlayType, columnIds) {
        overlayCtl.setOverlayColumns(overlayType, columnIds);
        if (!this._state.overlayColumnsByType) this._state.overlayColumnsByType = {};
        this._state.overlayColumnsByType[overlayType] = Array.isArray(columnIds) ? columnIds.slice() : [];
    };
    _systemEngineInstance.getOverlayColumns = function (overlayType) {
        return overlayCtl.getOverlayColumns(overlayType);
    };

    Object.assign(_systemEngineInstance, {
        init: function (systemId, containerSelector) {
            if (isValidSystemIdForMap(systemId)) {
                this._state.entityId = systemId;
            }
            return init(systemId, containerSelector);
        },
        loadMapData: loadMapData,
        setLayout: setLayout,
        setMapType: setMapType,
        setHopsCount: setHopsCount,
        setFilter: setFilter,
        setNodeFilters: setNodeFilters,
        setDatasetNodeFilters: setDatasetNodeFilters,
        toggleInterfaceLabels: toggleInterfaceLabels,
        zoomIn: zoomIn,
        zoomOut: zoomOut,
        resetMap: resetMap,
        redrawMap: redrawMap,
        exportAsPng: exportAsPng,
        openFullscreen: openFullscreen,
        /** Point lineage engine at map-tab canvas (new tab); then call setMapType / setHopsCount to load live data. */
        adoptMapTabCanvas: function (systemId, canvasEl) {
            if (!isValidSystemIdForMap(systemId) || !canvasEl) return;
            ensureDagreRegisteredForInterfaceMap();
            InterfaceMapState.systemId = systemId;
            InterfaceMapState.canvas = canvasEl;
            InterfaceMapState.loadingEl = canvasEl.querySelector('[data-interface-map-loading]');
            /* full_map never runs init(); reset filter semantics so empty DOM is not treated as “initialized hide-all”. */
            InterfaceMapState.filtersInitialized = false;
            InterfaceMapState.nodeFilters = { classifications: [], types: [], lifecycles: [] };
        },
        /** Apply toolbar prefs before setMapType in map-tab (layout / hops / labels affect next graph build). */
        prepareMapTabEngineState: function (opts) {
            if (!opts || typeof opts !== 'object') return;
            if (opts.layout != null) InterfaceMapState.layout = opts.layout;
            if (opts.hopsCount != null) {
                var v = Math.min(99, Math.max(1, parseInt(opts.hopsCount, 10) || 15));
                InterfaceMapState.hopsCount = v;
            }
            if (typeof opts.showInterfaceLabels === 'boolean') {
                InterfaceMapState.showInterfaceLabels = opts.showInterfaceLabels;
            }
            if (opts.mapType != null && (opts.mapType === 'system-lineage' || opts.mapType === 'dataset-lineage')) {
                InterfaceMapState.mapType = opts.mapType;
            }
        },
        setMapTabAfterRender: function (fn) {
            InterfaceMapState._mapTabAfterRender = typeof fn === 'function' ? fn : null;
        },
        toggleNavigator: toggleNavigator,
        getAllInterfaces: getAllInterfaces,
        notifyMapDataUpdated: notifyMapDataUpdated,
        getLegendHtml: getLegendHtml
    });

    // Preserve the live cy getter that SharedMapDropdowns depends on
    Object.defineProperty(_systemEngineInstance, 'cy', {
        get: function () { return InterfaceMapState.network; },
        configurable: true
    });

    // Fullscreen tab reads parent via getState() for overlays, filters, and graph sync — must be InterfaceMapState, not MapEngine's empty _state.
    _systemEngineInstance.getState = function () {
        return InterfaceMapState;
    };

    window.SystemInterfacesMap = _systemEngineInstance;

})();


