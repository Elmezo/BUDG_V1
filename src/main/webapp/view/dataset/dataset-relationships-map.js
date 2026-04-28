/**
 * Dataset Relationships Map Component
 * Implements Dataset Lineage and System Lineage maps for the Dataset facet Relationships tab
 * 
 * Map Types:
 * - Dataset Lineage (default): Shows datasets with direct relationships + datasets linked through attributes
 * - System Lineage: Shows systems with attribute relationships (solid) + systems with interfaces but no data attributes (dotted)
 * 
 * Dependencies:
 * - shared/map/map-styles.js, map-icons.js (InterfaceMapStyles / InterfaceMapIcons)
 * - shared/map/dataset-facet-lineage-graph.js (createDatasetFacetLineageGraphPipeline)
 * - shared/map/system-lineage-network-interactions.js (createDatasetFacetLineageNetworkInteractions)
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

    var DATASET_MAP_COLOR_FALLBACK = {
        currentSystem: '#f97316',
        currentSystemBorder: '#ea580c',
        otherSystem: '#1f2937',
        otherSystemBorder: '#374151',
        interfaceLine: '#94a3b8',
        attributeLine: '#64748b',
        upstreamHighlight: '#ef4444',
        downstreamHighlight: '#22c55e',
        lockedSystem: '#6b7280',
        currentDataset: '#f97316',
        currentDatasetBorder: '#ea580c',
        otherDataset: '#1f2937',
        otherDatasetBorder: '#374151'
    };
    function getMapColors() {
        return window.MapRenderUtils.getMapColors(DATASET_MAP_COLOR_FALLBACK);
    }

    // Map State Management
    const DatasetMapState = {
        initialized: false,
        dagreRegistered: false,
        network: null,
        canvas: null,
        loadingEl: null,
        datasetId: null,
        datasetData: null,
        systemId: null,
        systemData: null,
        interfacesData: [],
        dataFlowData: [],
        connectedSystems: new Map(),
        inaccessibleSystems: new Set(),
        interfaceIds: new Set(),
        dataFlowKeys: new Set(),
        hiddenNodes: new Set(),
        hiddenUpstreamNodes: new Set(),
        hiddenDownstreamNodes: new Set(),
        focusedNode: null,
        highlightedNodeId: null,
        layout: 'top-to-bottom',
        mapType: 'dataset-lineage', // Default to dataset-lineage
        overlay: 'none',
        overlayColumnsByType: {},
        hopsCount: 15, // 1-99, Axon recommends 15 for lineage
        filters: {
            systemInterfaces: true,
            dataAttributeLinks: true
        },
        filtersInitialized: false,
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
        },
        // System lineage state
        systemsWithAttributes: new Set(), // Systems that have datasets with attributes having relationships
        systemsWithInterfacesOnly: new Set() // Systems with interfaces but no data attributes
    };

    const adapter = window.createMapAdapter(DatasetMapState, 'datasetRelationshipsMap', { zoomRecenter: true });

    var graphPipeline = null;

    async function loadMapData(datasetId) {
        return graphPipeline.loadMapData(datasetId);
    }
    function buildDatasetLineageGraph() {
        return graphPipeline.buildDatasetLineageGraph();
    }
    function buildSystemLineageGraph() {
        return graphPipeline.buildSystemLineageGraph();
    }
    async function loadDatasetLineageData() {
        return graphPipeline.loadDatasetLineageData();
    }
    async function loadSystemLineageData() {
        return graphPipeline.loadSystemLineageData();
    }

    var networkIx = null;

    // Initialize the map
    function init(datasetId, containerSelector) {
        embLog('[DATASET-MAP] Initializing map for dataset:', datasetId, 'container:', containerSelector);
        DatasetMapState.datasetId = datasetId;
        DatasetMapState.canvas = document.querySelector(containerSelector || '#datasetRelationshipsMapCanvas');
        DatasetMapState.loadingEl = document.querySelector('[data-map-loading]') || document.querySelector('[data-dataset-map-loading]');

        if (!DatasetMapState.canvas) {
            console.error('[DATASET-MAP] Canvas element not found');
            return;
        }

        DatasetMapState.canvas.addEventListener('contextmenu', function(e) { e.preventDefault(); });

        // Check if Cytoscape is available
        if (typeof cytoscape === 'undefined') {
            console.error('[DATASET-MAP] Cytoscape library not available');
            DatasetMapState.canvas.innerHTML = window.MapRenderUtils.htmlCytoscapeUnavailable();
            return;
        }

        // Register dagre layout if available
        if (!DatasetMapState.dagreRegistered) {
            try {
                // Try different global variable names for cytoscape-dagre
                const dagre = window.cytoscapeDagre || window['cytoscape-dagre'];
                if (dagre) {
                    cytoscape.use(dagre);
                    DatasetMapState.dagreRegistered = true;
                    embLog('[DATASET-MAP] Dagre layout registered');
                } else {
                    embWarn('[DATASET-MAP] Dagre layout extension not found, will use fallback layout');
                }
            } catch (e) {
                embWarn('[DATASET-MAP] Error registering dagre layout:', e);
            }
        }

        DatasetMapState.initialized = true;
        DatasetMapState.mapType = 'dataset-lineage'; // Default to dataset-lineage
        DatasetMapState.overlay = 'none'; // Reset overlay when re-entering the tab
        
        // Load map data
        loadMapData(datasetId);
    }

    // Get node icon - use dataset icon for datasets, system icon for systems
    function getNodeIcon(type, isCurrent) {
        if (!window.InterfaceMapIcons) {
            return null;
        }
        
        if (type === 'dataset') {
            return window.InterfaceMapIcons.createDatasetIcon('#ffffff');
        } else if (type === 'locked') {
            return window.InterfaceMapIcons.createLockIcon('#ffffff');
        } else {
            return window.InterfaceMapIcons.getIconByType('system', isCurrent);
        }
    }

    // Render the network using Cytoscape
    function renderNetwork(graph) {
        if (!DatasetMapState.canvas) return;
        if (graphPipeline && graphPipeline.invalidateGraphCache) {
            graphPipeline.invalidateGraphCache();
        }

        // Destroy existing network
        if (DatasetMapState.network) {
            DatasetMapState.network.destroy();
            DatasetMapState.network = null;
        }

        if (graph.nodes.length === 0) {
            showPlaceholder('No relationships found.');
            return;
        }

        const colors = getMapColors();
        const elements = [];

        // Add nodes
        graph.nodes.forEach(node => {
            const isCurrent = node.isCurrent;
            const isLocked = node.isLocked;
            const isDataset = node.group === 'dataset';
            
            let nodeColor, borderColor, icon;
            
            if (isDataset) {
                nodeColor = isLocked ? colors.lockedSystem : (isCurrent ? colors.currentDataset : colors.otherDataset);
                borderColor = isLocked ? colors.lockedSystem : (isCurrent ? colors.currentDatasetBorder : colors.otherDatasetBorder);
                icon = isLocked ? getNodeIcon('locked', false) : getNodeIcon('dataset', isCurrent);
            } else {
                nodeColor = isLocked ? colors.lockedSystem : (isCurrent ? colors.currentSystem : colors.otherSystem);
                borderColor = isLocked ? colors.lockedSystem : (isCurrent ? colors.currentSystemBorder : colors.otherSystemBorder);
                icon = isLocked ? getNodeIcon('locked', false) : getNodeIcon('system', isCurrent);
            }

            elements.push({
                data: {
                    id: node.id,
                    label: (isLocked ? '\u{1F512} ' : '') + (node.label || ''),
                    group: node.group,
                    isCurrent: isCurrent,
                    isLocked: !!isLocked,
                    isDataset: isDataset,
                    meta: node.meta,
                    nodeColor: nodeColor,
                    borderColor: borderColor,
                    backgroundImage: icon
                }
            });
        });

        // Add edges
        const sortedEdges = [...graph.edges].sort((a, b) => {
            if (a.dashes && !b.dashes) return -1;
            if (!a.dashes && b.dashes) return 1;
            return 0;
        });

        // Collect current node IDs so dagre places them at rank 0
        const currentNodeIds = new Set(graph.nodes.filter(n => n.isCurrent).map(n => String(n.id)));

        sortedEdges.forEach(edge => {
            const lineColor = edge.dashes ? colors.interfaceLine : colors.attributeLine;
            const isReversed = currentNodeIds.has(String(edge.to)) && !currentNodeIds.has(String(edge.from));
            elements.push({
                data: {
                    id: edge.id,
                    source: isReversed ? edge.to : edge.from,
                    target: isReversed ? edge.from : edge.to,
                    label: edge.label || '',
                    lineStyle: edge.dashes ? 'dashed' : 'solid',
                    lineColor: lineColor,
                    lineType: edge.lineType || (edge.dashes ? 'interface' : 'lineage'),
                    reversed: isReversed
                },
                classes: isReversed ? 'reversed-edge' : ''
            });
        });

        // Initialize Cytoscape
        DatasetMapState.network = cytoscape({
            container: DatasetMapState.canvas,
            elements: elements,
            style: getCytoscapeStyle(),
            layout: buildCytoscapeLayout(),
            minZoom: 0.3,
            maxZoom: 3
        });

        // Add event listeners
        setupEventListeners();
        
        // Update legend after rendering
        updateLegend();
        
        // Apply filters if needed
        if (DatasetMapState.mapType === 'dataset-lineage') {
            adapter.applyDatasetNodeFilters({
                linkedDatasets: DatasetMapState.linkedDatasets,
                updateOverlayPositions: updateOverlayPositions
            });
        } else {
            applyNodeFiltersToNetwork();
        }

        // Re-apply overlay when one was selected (e.g. after map type change or redraw)
        if (DatasetMapState.overlay && DatasetMapState.overlay !== 'none') {
            loadOverlayData(DatasetMapState.overlay);
        }
    }

    // Get Cytoscape styling - uses external styles module if available
    function getCytoscapeStyle() {
        // Try to use external styles module
        if (window.InterfaceMapStyles && typeof window.InterfaceMapStyles.getCytoscapeStyles === 'function') {
            return window.InterfaceMapStyles.getCytoscapeStyles('datasetRelationshipsMap');
        }

        // Fallback inline styles if external module not loaded
        const colors = getMapColors();
        const nodeSizes = adapter.getNodeSizes();

        return [
            {
                selector: 'node',
                style: {
                    'label': 'data(label)',
                    'text-valign': 'bottom',
                    'text-halign': 'center',
                    'background-opacity': 1,
                    'background-color': 'data(nodeColor)',
                    'border-color': 'data(borderColor)',
                    'border-width': 2,
                    'text-margin-y': 12,
                    'text-wrap': 'wrap',
                    'text-max-width': 150,
                    'font-size': 12,
                    'font-weight': '600',
                    'font-family': 'Inter, system-ui, sans-serif',
                    'color': colors.nodeLabel || '#1f2937',
                    'shape': 'round-rectangle',
                    'width': nodeSizes.system.width,
                    'height': nodeSizes.system.height,
                    'background-image': 'data(backgroundImage)',
                    'background-fit': 'contain',
                    'background-width': '50%',
                    'background-height': '50%',
                    'background-position-y': '30%'
                }
            },
            {
                selector: 'node[?isCurrent]',
                style: {
                    'background-color': colors.currentSystem,
                    'border-color': colors.currentSystemBorder || '#ea580c',
                    'border-width': 3
                }
            },
            {
                selector: 'node[?isLocked]',
                style: {
                    'background-color': colors.lockedSystem || '#6b7280',
                    'border-color': colors.lockedSystem || '#6b7280',
                    'background-image': getNodeIcon('locked', false),
                    'background-opacity': 1
                }
            },
            {
                selector: 'node[?isDataset]',
                style: {
                    'shape': 'round-rectangle',
                    'width': nodeSizes.system.width * 1.2,
                    'height': nodeSizes.system.height * 1.3,
                    'background-image': 'data(backgroundImage)',
                    'background-fit': 'contain',
                    'background-width': '50%',
                    'background-height': '50%',
                    'background-position-y': '25%',
                    'text-wrap': 'wrap',
                    'text-max-width': nodeSizes.system.width * 1.1,
                    'font-size': 11,
                    'line-height': 1.3
                }
            },
            {
                selector: 'edge',
                style: {
                    'width': 2,
                    'line-color': 'data(lineColor)',
                    'target-arrow-color': 'data(lineColor)',
                    'target-arrow-shape': 'triangle',
                    'target-arrow-width': 8,
                    'curve-style': (window._sharedDropdownApis && window._sharedDropdownApis['datasetRelationshipsMap'] && typeof window._sharedDropdownApis['datasetRelationshipsMap'].getCurveStyle === 'function') ? window._sharedDropdownApis['datasetRelationshipsMap'].getCurveStyle() : 'bezier',
                    'label': 'data(label)',
                    'font-size': 10,
                    'font-family': 'Inter, system-ui, sans-serif',
                    'text-rotation': 'autorotate',
                    'text-margin-y': -10,
                    'color': colors.edgeLabelColor || '#6b7280',
                    'text-background-color': '#ffffff',
                    'text-background-opacity': 0.8,
                    'text-background-padding': '2px'
                }
            },
            {
                selector: 'edge.reversed-edge',
                style: {
                    'target-arrow-shape': 'none',
                    'source-arrow-shape': 'triangle',
                    'source-arrow-color': colors.attributeLine || colors.interfaceLine || '#64748b'
                }
            },
            {
                selector: 'edge.reversed-edge[lineColor]',
                style: {
                    'source-arrow-color': 'data(lineColor)'
                }
            },
            {
                selector: 'edge[lineStyle = "dashed"]',
                style: {
                    'line-style': 'dashed',
                    'line-dash-pattern': [6, 3],
                    'line-color': '#94a3b8',
                    'target-arrow-color': '#94a3b8',
                    'z-index': 1
                }
            },
            {
                selector: 'edge[lineStyle = "solid"]',
                style: {
                    'line-style': 'solid',
                    'line-color': '#64748b',
                    'target-arrow-color': '#64748b',
                    'z-index': 2
                }
            },
            {
                selector: 'node:selected',
                style: {
                    'border-width': 4,
                    'border-color': colors.selectedBorder || '#248567'
                }
            },
            {
                selector: '.highlighted-upstream',
                style: {
                    'line-color': colors.upstreamHighlight,
                    'target-arrow-color': colors.upstreamHighlight,
                    'width': 3,
                    'z-index': 999
                }
            },
            {
                selector: '.highlighted-downstream',
                style: {
                    'line-color': colors.downstreamHighlight,
                    'target-arrow-color': colors.downstreamHighlight,
                    'width': 3,
                    'z-index': 999
                }
            },
            {
                selector: '.dimmed',
                style: {
                    'opacity': 0.25
                }
            },
            {
                selector: '.focused',
                style: {
                    'border-color': colors.downstreamHighlight,
                    'border-width': 4
                }
            }
        ];
    }

    // Build Cytoscape layout configuration - uses external styles module if available
    function buildCytoscapeLayout() {
        const mapId = 'datasetRelationshipsMap';
        const ddApi = window._sharedDropdownApis && window._sharedDropdownApis[mapId];
        const DEFAULT_SF = 2.2;
        const DEFAULT_PAD = 90;
        const sf = ddApi && typeof ddApi.getSpacingFactor === 'function' ? ddApi.getSpacingFactor() : DEFAULT_SF;
        const sp = ddApi && typeof ddApi.getSpacingPadding === 'function' ? ddApi.getSpacingPadding() : DEFAULT_PAD;
        const layoutOption = DatasetMapState.layout;

        // Try to use external layout configuration
        if (window.InterfaceMapStyles && typeof window.InterfaceMapStyles.getLayoutConfig === 'function') {
            const layoutConfig = window.InterfaceMapStyles.getLayoutConfig(layoutOption);
            // Add roots for breadthfirst layouts
            if (layoutConfig.name === 'breadthfirst') {
                return { ...layoutConfig, roots: adapter.findRootNodes(), padding: sp, spacingFactor: sf };
            }
            return { ...layoutConfig, padding: sp, spacingFactor: sf };
        }

        // Fallback inline layout configuration
        const baseLayout = {
            name: 'breadthfirst',
            directed: true,
            padding: sp,
            spacingFactor: sf,
            animate: true,
            animationDuration: 500,
            nodeDimensionsIncludeLabels: true
        };

        switch (layoutOption) {
            case 'right-to-left':
                return {
                    ...baseLayout,
                    roots: adapter.findRootNodes(),
                    transform: (node, pos) => ({ x: -pos.x, y: pos.y })
                };
            case 'top-to-bottom':
                if (DatasetMapState.dagreRegistered) {
                    return {
                        name: 'dagre',
                        rankDir: 'TB',
                        padding: sp,
                        spacingFactor: sf,
                        animate: true,
                        animationDuration: 500,
                        nodeDimensionsIncludeLabels: true
                    };
                }
                return { ...baseLayout, roots: adapter.findRootNodes() };
            case 'organic':
            case 'force':
                return {
                    name: 'cose',
                    animate: true,
                    animationDuration: 500,
                    nodeRepulsion: Math.round(4500 * sf),
                    idealEdgeLength: Math.round(100 * sf),
                    edgeElasticity: 0.45,
                    nestingFactor: 0.1,
                    gravity: 0.25,
                    numIter: 1500,
                    initialEnergyOnIncremental: 0.3
                };
            default: // left-to-right
                if (DatasetMapState.dagreRegistered) {
                    return {
                        name: 'dagre',
                        rankDir: 'LR',
                        padding: sp,
                        spacingFactor: sf,
                        animate: true,
                        animationDuration: 500,
                        nodeDimensionsIncludeLabels: true
                    };
                }
                return { ...baseLayout, roots: adapter.findRootNodes() };
        }
    }

    function setupEventListeners() {
        networkIx.setupEventListeners();
    }

    // Show edge info
    function showEdgeInfo(edgeData) {
        // Could show a tooltip or info panel with edge details
    }

    networkIx = window.createDatasetFacetLineageNetworkInteractions({
        state: DatasetMapState,
        getMapColors: getMapColors,
        buildDatasetLineageGraph: buildDatasetLineageGraph,
        buildSystemLineageGraph: buildSystemLineageGraph,
        renderNetwork: renderNetwork,
        showEdgeInfo: showEdgeInfo
    });

    // Show loading indicator
    function showLoading() {
        if (DatasetMapState.loadingEl) {
            DatasetMapState.loadingEl.style.display = 'flex';
        }
    }

    // Hide loading indicator
    function hideLoading() {
        if (DatasetMapState.loadingEl) {
            DatasetMapState.loadingEl.style.display = 'none';
        }
    }

    // Show placeholder message
    function showPlaceholder(message) {
        if (DatasetMapState.canvas) {
            DatasetMapState.canvas.innerHTML = window.MapRenderUtils.htmlMapCanvasMessage(message);
        }
    }

    // Build legend HTML (used for both side panel and dropdown)
    function getLegendHtml() {
        const colors = getMapColors();
        const isDatasetLineage = DatasetMapState.mapType === 'dataset-lineage';
        const lineageHtml = (window.SharedMapStyles && typeof window.SharedMapStyles.getLineageLegendHtml === 'function')
            ? window.SharedMapStyles.getLineageLegendHtml(colors, DatasetMapState.mapType === 'system-lineage' ? { includeEucTypes: true } : undefined)
            : (window.InterfaceMapStyles && typeof window.InterfaceMapStyles.getLineageLegendHtml === 'function')
                ? window.InterfaceMapStyles.getLineageLegendHtml(colors, DatasetMapState.mapType === 'system-lineage' ? { includeEucTypes: true } : undefined)
                : '';
        if (isDatasetLineage) {
            return `
                <div class="legend-item">
                    <div class="legend-color" style="background: ${colors.currentDataset || colors.currentSystem};"></div>
                    <span>Current Dataset</span>
                </div>
                <div class="legend-item">
                    <div class="legend-color" style="background: ${colors.otherDataset || colors.otherSystem};"></div>
                    <span>Linked Dataset</span>
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
                    <span>Locked / Inaccessible Dataset</span>
                </div>
            `;
        }
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
        `;
    }

    // Update legend (side panel and any dropdown)
    function updateLegend() {
        const mapSection = document.getElementById('datasetRelationshipsMapSection');
        const legendContainer = mapSection
            ? (mapSection.querySelector('[data-map-legend]') || mapSection.querySelector('[data-dataset-map-legend]') || mapSection.querySelector('#datasetRelationshipsMapSidePanel [data-map-legend]'))
            : (document.querySelector('[data-map-legend]') || document.querySelector('[data-dataset-map-legend]') || document.querySelector('#datasetRelationshipsMapSidePanel [data-map-legend]'));
        if (legendContainer) legendContainer.innerHTML = getLegendHtml();
        // Update dropdown if open
        const dropdown = document.getElementById('datasetRelationshipsMapLegendDropdown');
        if (dropdown) dropdown.innerHTML = getLegendHtml();
    }

    // Control Functions (exposed for toolbar)
    function setMapType(type) {
        if (type !== 'dataset-lineage' && type !== 'system-lineage') {
            embWarn('[DATASET-MAP] Invalid map type:', type);
            return;
        }
        
        embLog('[DATASET-MAP] setMapType called:', type, '(was:', DatasetMapState.mapType, ')');
        DatasetMapState.mapType = type;
        
        // Clear overlay state
        DatasetMapState.overlay = 'none';
        clearOverlayPanels();
        
        // Clear existing network
        if (DatasetMapState.network) {
            DatasetMapState.network.destroy();
            DatasetMapState.network = null;
        }
        
        // Clear hidden nodes and focus state
        if (!DatasetMapState.hiddenNodes) DatasetMapState.hiddenNodes = new Set();
        if (!DatasetMapState.hiddenUpstreamNodes) DatasetMapState.hiddenUpstreamNodes = new Set();
        if (!DatasetMapState.hiddenDownstreamNodes) DatasetMapState.hiddenDownstreamNodes = new Set();
        DatasetMapState.hiddenNodes.clear();
        DatasetMapState.focusedNode = null;
        DatasetMapState.hiddenUpstreamNodes.clear();
        DatasetMapState.hiddenDownstreamNodes.clear();
        
        // Reload data and rebuild graph
        loadMapData(DatasetMapState.datasetId);
    }

    function setLayout(layout) {
        DatasetMapState.layout = layout;
        if (DatasetMapState.network) {
            DatasetMapState.network.layout(buildCytoscapeLayout()).run();
        }
    }

    // Set hops count (1-99, Axon recommends 15)
    function setHopsCount(count) {
        const val = Math.min(99, Math.max(1, parseInt(count, 10) || 15));
        DatasetMapState.hopsCount = val;
        // Re-load so multi-hop expansion fetches indirect relationships at the new depth.
        if (DatasetMapState.datasetId) {
            loadMapData(DatasetMapState.datasetId);
        }
    }

    function setOverlay(overlay) {
        embLog('[DATASET-MAP] setOverlay called with:', overlay, 'mapType:', DatasetMapState.mapType);
        DatasetMapState.overlay = overlay;
        
        // Clear existing overlays
        clearOverlayPanels();
        
        if (overlay === 'none' || !overlay) {
            embLog('[DATASET-MAP] Overlay cleared');
            return;
        }
        
        // Load and display overlay data
        embLog('[DATASET-MAP] Loading overlay data for:', overlay);
        loadOverlayData(overlay);
    }

    /** Set overlay columns (field ids) for an overlay type. Re-renders overlay if that type is active. */
    function setOverlayColumns(overlayType, columnIds) {
        if (!overlayType) return;
        DatasetMapState.overlayColumnsByType[overlayType] = Array.isArray(columnIds) ? columnIds.slice() : [];
        if (DatasetMapState.overlay === overlayType) {
            loadOverlayData(overlayType);
        }
    }

    /** Get selected overlay column ids for an overlay type. */
    function getOverlayColumns(overlayType) {
        var raw = DatasetMapState.overlayColumnsByType[overlayType];
        if (window.OverlayColumns && typeof window.OverlayColumns.resolveSelectedColumnIds === 'function') {
            return window.OverlayColumns.resolveSelectedColumnIds(raw, overlayType);
        }
        return (Array.isArray(raw) && raw.length > 0) ? raw.slice() : (window.OverlayColumns ? window.OverlayColumns.getDefaultOverlayColumnIds(overlayType) : ['name']);
    }
    
    // Clear overlay panels
    function clearOverlayPanels() {
        DatasetMapState._highlightedOverlayKey = null;
        if (!DatasetMapState.network) return;
        
        DatasetMapState.network.elements().removeClass('overlay-highlight-source-node overlay-highlight-related-node overlay-highlight-edge');
        
        var overlayContainer = DatasetMapState.canvas?.querySelector('.map-overlay-container');
        if (overlayContainer) {
            overlayContainer.innerHTML = '';
        }
        
        // Also remove any orphaned overlay panels
        const overlayElements = DatasetMapState.canvas?.querySelectorAll('.map-node-overlay-panel');
        overlayElements?.forEach(el => el.remove());
        
        // Reset node styles
        DatasetMapState.network.nodes().forEach(node => {
            node.removeClass('has-overlay');
        });
        
        // Remove zoom/pan/drag listeners
        DatasetMapState.network.off('zoom pan', updateOverlayPositions);
        DatasetMapState.network.off('drag', 'node', updateOverlayPositions);
    }

    function relDatasetId(rel, keys) {
        if (!rel || !Array.isArray(keys)) return '';
        for (var i = 0; i < keys.length; i++) {
            var value = rel[keys[i]];
            if (value !== undefined && value !== null && String(value).trim() !== '') {
                return String(value);
            }
        }
        return '';
    }

    function getDatasetMapGlossaryScopeKey() {
        return [
            String(DatasetMapState.systemId || ''),
            String(DatasetMapState.mapType || ''),
            String((DatasetMapState.linkedDatasets && DatasetMapState.linkedDatasets.size) || 0),
            String((DatasetMapState.datasetRelationships || []).length)
        ].join('|');
    }

    function ensureDatasetMapGlossaryScope() {
        var key = getDatasetMapGlossaryScopeKey();
        if (DatasetMapState._glossaryDatasetScope && DatasetMapState._glossaryDatasetScope.key === key) {
            return DatasetMapState._glossaryDatasetScope.scope;
        }

        var currentSystemId = String(DatasetMapState.systemId || '');
        var currentDatasetIds = new Set();
        var datasetsBySystem = new Map();
        if (DatasetMapState.linkedDatasets) {
            DatasetMapState.linkedDatasets.forEach(function (info, datasetId) {
                var sid = String((info && info.systemId) || '');
                var did = String(datasetId || '');
                if (!sid || !did) return;
                if (!datasetsBySystem.has(sid)) datasetsBySystem.set(sid, []);
                datasetsBySystem.get(sid).push(did);
                if (sid === currentSystemId) currentDatasetIds.add(did);
            });
        }

        var relatedDatasetIds = new Set();
        // For non-current datasets, build the set of attribute IDs that are linked
        // (via dataset-attribute relationships) to one of the current system's datasets.
        // Key: datasetId (string), Value: Set<attributeId (string)>
        var linkedAttributeIdsByDataset = new Map();
        function addLinkedAttr(did, aid) {
            var didStr = String(did || '');
            var aidStr = aid != null ? String(aid) : '';
            if (!didStr || !aidStr) return;
            if (currentDatasetIds.has(didStr)) return; // current's attrs are unrestricted
            if (!linkedAttributeIdsByDataset.has(didStr)) {
                linkedAttributeIdsByDataset.set(didStr, new Set());
            }
            linkedAttributeIdsByDataset.get(didStr).add(aidStr);
        }
        (DatasetMapState.datasetRelationships || []).forEach(function (rel) {
            var srcDid = relDatasetId(rel, ['sourceDatasetId', 'sourceDataSetId', 'Source_DatasetID']);
            var tgtDid = relDatasetId(rel, ['targetDatasetId', 'targetDataSetId', 'Target_DatasetID']);
            if (!srcDid || !tgtDid) return;
            if (currentDatasetIds.has(srcDid) && !currentDatasetIds.has(tgtDid)) relatedDatasetIds.add(tgtDid);
            if (currentDatasetIds.has(tgtDid) && !currentDatasetIds.has(srcDid)) relatedDatasetIds.add(srcDid);

            var srcAid = rel && (rel.sourceAttributeId != null ? rel.sourceAttributeId : rel.Source_AttributeID);
            var tgtAid = rel && (rel.targetAttributeId != null ? rel.targetAttributeId : rel.Target_AttributeID);
            if (currentDatasetIds.has(srcDid) && !currentDatasetIds.has(tgtDid)) {
                addLinkedAttr(tgtDid, tgtAid);
            }
            if (currentDatasetIds.has(tgtDid) && !currentDatasetIds.has(srcDid)) {
                addLinkedAttr(srcDid, srcAid);
            }
        });

        var scope = {
            currentSystemId: currentSystemId,
            currentDatasetIds: currentDatasetIds,
            relatedDatasetIds: relatedDatasetIds,
            datasetsBySystem: datasetsBySystem,
            linkedAttributeIdsByDataset: linkedAttributeIdsByDataset
        };
        DatasetMapState._glossaryDatasetScope = { key: key, scope: scope };
        return scope;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cross-panel transitive highlight cache. Built from DatasetMapState.datasetRelationships
    // (which already mixes dataset and attribute level info). Populated lazily
    // alongside the existing glossary scope on every overlay load.
    // ─────────────────────────────────────────────────────────────────────────
    function getOverlayHighlightCache() {
        if (!DatasetMapState._overlayHighlightCache) {
            DatasetMapState._overlayHighlightCache = {
                datasetGraph: new Map(),
                attributeRels: [],
                attributeOwnerMap: new Map(),
                glossaryToAnchors: new Map(),
                anchorToGlossaries: new Map(),
                anchorGraph: null
            };
        }
        return DatasetMapState._overlayHighlightCache;
    }

    function resetOverlayHighlightCache() {
        DatasetMapState._overlayHighlightCache = {
            datasetGraph: new Map(),
            attributeRels: [],
            attributeOwnerMap: new Map(),
            glossaryToAnchors: new Map(),
            anchorToGlossaries: new Map(),
            anchorGraph: null
        };
    }

    function rebuildHighlightCacheFromRelationships() {
        const cache = getOverlayHighlightCache();
        const helper = window.MapOverlayHighlight;
        const rels = DatasetMapState.datasetRelationships || [];
        const datasetGraph = new Map();
        const attributeRels = [];
        const attributeOwnerMap = new Map();

        function addEdge(graph, a, b) {
            if (!a || !b || a === b) return;
            if (!graph.has(a)) graph.set(a, new Set());
            if (!graph.has(b)) graph.set(b, new Set());
            graph.get(a).add(b);
            graph.get(b).add(a);
        }
        function getStr(rel, keys) {
            for (let i = 0; i < keys.length; i++) {
                const v = rel && rel[keys[i]];
                if (v !== null && v !== undefined && String(v).trim() !== '') return String(v);
            }
            return '';
        }

        rels.forEach(rel => {
            const srcDs = getStr(rel, ['sourceDatasetId', 'sourceDataSetId', 'Source_DatasetID', 'source_dataset_id']);
            const tgtDs = getStr(rel, ['targetDatasetId', 'targetDataSetId', 'Target_DatasetID', 'target_dataset_id']);
            const srcAttr = getStr(rel, ['sourceAttributeId', 'Source_AttributeID', 'source_attribute_id']);
            const tgtAttr = getStr(rel, ['targetAttributeId', 'Target_AttributeID', 'target_attribute_id']);

            if (srcDs && tgtDs) addEdge(datasetGraph, srcDs, tgtDs);
            if (srcAttr && tgtAttr) attributeRels.push({ sourceAttributeId: srcAttr, targetAttributeId: tgtAttr });
            if (srcAttr && srcDs) attributeOwnerMap.set(srcAttr, srcDs);
            if (tgtAttr && tgtDs) attributeOwnerMap.set(tgtAttr, tgtDs);
        });

        cache.datasetGraph = datasetGraph;
        cache.attributeRels = attributeRels;
        cache.attributeOwnerMap = attributeOwnerMap;

        if (helper && typeof helper.buildAnchorGraph === 'function') {
            cache.anchorGraph = helper.buildAnchorGraph(datasetGraph, attributeRels, attributeOwnerMap);
        }
    }

    function recordHighlightGlossaryAnchor(anchorKey, glossaryId) {
        if (!anchorKey || glossaryId == null) return;
        const cache = getOverlayHighlightCache();
        const gid = String(glossaryId).trim();
        if (!gid) return;
        if (!cache.glossaryToAnchors.has(gid)) cache.glossaryToAnchors.set(gid, new Set());
        cache.glossaryToAnchors.get(gid).add(anchorKey);
        if (!cache.anchorToGlossaries.has(anchorKey)) cache.anchorToGlossaries.set(anchorKey, new Set());
        cache.anchorToGlossaries.get(anchorKey).add(gid);
    }

    function isDatasetAllowedInDatasetMapGlossary(scope, datasetId, systemId) {
        if (!scope) return false;
        var did = String(datasetId || '');
        var sid = String(systemId || '');
        if (!did || !sid) return false;
        if (sid === scope.currentSystemId) return scope.currentDatasetIds.has(did);
        return scope.relatedDatasetIds.has(did);
    }

    // For a non-current dataset, return true only if the attribute is in the
    // linked-to-current set. Current-system datasets pass through (all attrs).
    function isAttributeAllowedInDatasetMapGlossary(scope, datasetId, attributeId) {
        if (!scope) return false;
        var did = String(datasetId || '');
        var aid = attributeId != null ? String(attributeId) : '';
        if (!did) return false;
        if (scope.currentDatasetIds && scope.currentDatasetIds.has(did)) return true;
        if (!aid) return false;
        var linked = scope.linkedAttributeIdsByDataset && scope.linkedAttributeIdsByDataset.get(did);
        return !!(linked && linked.has(aid));
    }
    
    // Fetch overlay data for a dataset
    async function fetchOverlayDataForDataset(datasetId, overlayType) {
        try {
            switch (overlayType) {
                case 'description':
                    // Check cache first
                    const cachedInfo = DatasetMapState.linkedDatasets?.get(String(datasetId));
                    if (cachedInfo?.dataset) {
                        const ds = cachedInfo.dataset;
                        const desc = ds.definition || ds.Definition || ds.description || ds.Description;
                        if (desc) {
                            return [{ name: 'Definition', value: desc }];
                        }
                    }
                    // Fetch from API
                    try {
                        const resp = await fetch(`/api/dataset/${datasetId}`, { credentials: 'include' });
                        if (resp.ok) {
                            const data = await resp.json();
                            const dataset = data?.data || data;
                            const desc = dataset?.definition || dataset?.Definition || dataset?.description || dataset?.Description;
                            if (desc) {
                                return [{ name: 'Definition', value: desc }];
                            }
                        }
                    } catch (e) { /* ignore */ }
                    return [];

                case 'stakeholders':
                    try {
                        const resp = await fetch(`/api/dataset-stakeholder/${datasetId}/stakeholders`, { credentials: 'include' });
                        if (resp.ok) {
                            const data = await resp.json();
                            return Array.isArray(data) ? data : (data?.data || []);
                        }
                    } catch (e) { /* ignore */ }
                    return [];

                case 'attributes':
                    try {
                        const resp = await fetch(`/api/attribute/${datasetId}`, { credentials: 'include' });
                        if (resp.ok) {
                            const data = await resp.json();
                            return Array.isArray(data?.data) ? data.data : (Array.isArray(data) ? data : []);
                        }
                    } catch (e) { /* ignore */ }
                    return [];

                case 'linking-attributes': {
                    const currentDid = String(datasetId);
                    const rels = (DatasetMapState.datasetRelationships || []).filter(rel => {
                        const srcDid = rel.sourceDatasetId != null ? String(rel.sourceDatasetId) : (rel.Source_DatasetID != null ? String(rel.Source_DatasetID) : null);
                        const tgtDid = rel.targetDatasetId != null ? String(rel.targetDatasetId) : (rel.Target_DatasetID != null ? String(rel.Target_DatasetID) : null);
                        return srcDid === currentDid || tgtDid === currentDid;
                    });
                    if (!rels.length) return [];

                    const datasetIds = new Set([currentDid]);
                    rels.forEach(rel => {
                        const srcDid = rel.sourceDatasetId != null ? String(rel.sourceDatasetId) : (rel.Source_DatasetID != null ? String(rel.Source_DatasetID) : null);
                        const tgtDid = rel.targetDatasetId != null ? String(rel.targetDatasetId) : (rel.Target_DatasetID != null ? String(rel.Target_DatasetID) : null);
                        if (srcDid) datasetIds.add(srcDid);
                        if (tgtDid) datasetIds.add(tgtDid);
                    });

                    const attrsByDataset = new Map();
                    await Promise.all(Array.from(datasetIds).map(async did => {
                        try {
                            const resp = await fetch(`/api/attribute/${did}`, { credentials: 'include' });
                            if (!resp.ok) { attrsByDataset.set(did, []); return; }
                            const data = await resp.json();
                            const attrs = Array.isArray(data?.data) ? data.data : (Array.isArray(data) ? data : []);
                            attrsByDataset.set(did, attrs);
                        } catch (e) {
                            attrsByDataset.set(did, []);
                        }
                    }));

                    const getDatasetName = (did) => {
                        const info = DatasetMapState.linkedDatasets?.get(String(did));
                        const ds = info?.dataset || {};
                        return ds.primaryName || ds.name || ds.shortName || String(did);
                    };
                    const getAttrId = (a) => (
                        a?.id ?? a?.ID ?? a?.attributeId ?? a?.Attribute_ID ?? a?.attribute_id ?? a?.['Attribute ID'] ?? null
                    );
                    const attrName = (a) => (
                        a?.name || a?.['Name attribute'] || a?.attributeName || a?.attribute_name || a?.primaryName || a?.PrimaryName || a?.Name || ''
                    );
                    const getAttrById = (did, attrId) => {
                        const list = attrsByDataset.get(String(did)) || [];
                        const targetId = String(attrId);
                        return list.find(a => String(getAttrId(a)) === targetId) || null;
                    };

                    const out = [];
                    const seen = new Set();
                    rels.forEach(rel => {
                        const srcDid = rel.sourceDatasetId != null ? String(rel.sourceDatasetId) : (rel.Source_DatasetID != null ? String(rel.Source_DatasetID) : null);
                        const tgtDid = rel.targetDatasetId != null ? String(rel.targetDatasetId) : (rel.Target_DatasetID != null ? String(rel.Target_DatasetID) : null);
                        const srcAid = rel.sourceAttributeId ?? rel.Source_AttributeID;
                        const tgtAid = rel.targetAttributeId ?? rel.Target_AttributeID;
                        if (!srcAid || !tgtAid) return;

                        let direction, localDid, localAid, relatedDid, relatedAid;
                        if (srcDid === currentDid) {
                            direction = 'outbound';
                            localDid = srcDid; localAid = srcAid;
                            relatedDid = tgtDid; relatedAid = tgtAid;
                        } else if (tgtDid === currentDid) {
                            direction = 'inbound';
                            localDid = tgtDid; localAid = tgtAid;
                            relatedDid = srcDid; relatedAid = srcAid;
                        } else {
                            return;
                        }

                        const localAttr = getAttrById(localDid, localAid);
                        const relatedAttr = getAttrById(relatedDid, relatedAid);
                        const key = [direction, localAid, relatedDid || '', relatedAid].join('|');
                        if (seen.has(key)) return;
                        seen.add(key);

                        const relSourceName = rel.sourceAttributeName || rel.Source_AttributeName || rel.sourceAttribute || rel.Source_Attribute;
                        const relTargetName = rel.targetAttributeName || rel.Target_AttributeName || rel.targetAttribute || rel.Target_Attribute;
                        const localNameFallback = (srcDid === currentDid ? relSourceName : relTargetName);
                        const relatedNameFallback = (srcDid === currentDid ? relTargetName : relSourceName);

                        out.push({
                            id: getAttrId(localAttr) ?? localAid,
                            name: attrName(localAttr) || localNameFallback || String(localAid),
                            type: localAttr?.typeName || localAttr?.type || localAttr?.Type || '',
                            glossary: localAttr?.glossaryName || localAttr?.glossary || localAttr?.['Glossary Name attribute'] || localAttr?.GlossaryName || '',
                            direction: direction,
                            relatedDataset: relatedDid ? getDatasetName(relatedDid) : '',
                            relatedAttribute: attrName(relatedAttr) || relatedNameFallback || String(relatedAid),
                            sourceAttributeId: srcAid,
                            targetAttributeId: tgtAid
                        });
                    });

                    return out;
                }

                case 'glossary': {
                    var glossaryDsScope = ensureDatasetMapGlossaryScope();
                    var dsInfoForScope = DatasetMapState.linkedDatasets?.get(String(datasetId));
                    var dsSystemIdForScope = dsInfoForScope && dsInfoForScope.systemId != null ? String(dsInfoForScope.systemId) : '';
                    var datasetIsCurrent = !!(glossaryDsScope && glossaryDsScope.currentDatasetIds && glossaryDsScope.currentDatasetIds.has(String(datasetId)));
                    // Apply the glossary scope in both system-lineage and dataset-lineage maps so non-chain
                    // datasets do not bleed unrelated glossaries into the overlay.
                    if (!isDatasetAllowedInDatasetMapGlossary(glossaryDsScope, datasetId, dsSystemIdForScope)) {
                        return [];
                    }
                    const glossaryTerms = [];
                    const seen = new Set();
                    // 1) Dataset's own glossary term
                    try {
                        const cachedDs = DatasetMapState.linkedDatasets?.get(String(datasetId));
                        let dsGlossaryName = cachedDs?.dataset?.glossaryName;
                        let dsGlossaryId = cachedDs?.dataset?.glossaryId;
                        if (!dsGlossaryName) {
                            const dsResp = await fetch(`/api/dataset/${datasetId}`, { credentials: 'include' });
                            if (dsResp.ok) {
                                const dsData = await dsResp.json();
                                const ds = dsData?.data || dsData;
                                dsGlossaryName = ds?.glossaryName;
                                dsGlossaryId = ds?.glossaryId;
                            }
                        }
                        if (dsGlossaryName && !seen.has(dsGlossaryName)) {
                            seen.add(dsGlossaryName);
                            glossaryTerms.push({ name: dsGlossaryName, glossary: dsGlossaryName, id: dsGlossaryId, glossaryId: dsGlossaryId, source: 'dataset' });
                        }
                        if (window.MapOverlayHighlight && dsGlossaryId != null) {
                            recordHighlightGlossaryAnchor(window.MapOverlayHighlight.dsKey(datasetId), dsGlossaryId);
                        }
                    } catch (e) { /* ignore */ }
                    // 2) Glossary terms from attributes — for non-current datasets,
                    // restrict to attributes linked to current via dataset relationships in both map modes.
                    try {
                        const resp = await fetch(`/api/attribute/${datasetId}`, { credentials: 'include' });
                        if (resp.ok) {
                            const data = await resp.json();
                            let attrs = Array.isArray(data?.data) ? data.data : (Array.isArray(data) ? data : []);
                            if (!datasetIsCurrent) {
                                attrs = attrs.filter(a => isAttributeAllowedInDatasetMapGlossary(glossaryDsScope, datasetId, a && (a.id != null ? a.id : (a.ID != null ? a.ID : a.attribute_id))));
                            }
                            attrs.forEach(attr => {
                                const aid = attr && (attr.id != null ? attr.id : (attr.ID != null ? attr.ID : attr.attribute_id));
                                if (aid != null) {
                                    getOverlayHighlightCache().attributeOwnerMap.set(String(aid), String(datasetId));
                                }
                                const gName = adapter.extractGlossaryNameFromAttr(attr);
                                const gId = attr.glossaryId || attr.glossary_id || attr.Glossary_ID;
                                if (window.MapOverlayHighlight && aid != null && gId != null) {
                                    recordHighlightGlossaryAnchor(window.MapOverlayHighlight.attrKey(aid), gId);
                                }
                                if (gName && !seen.has(gName)) {
                                    seen.add(gName);
                                    glossaryTerms.push({ name: gName, glossary: gName, id: gId, glossaryId: gId, source: 'attribute' });
                                }
                            });
                        }
                    } catch (e) { /* ignore */ }
                    embLog('[DATASET-MAP] Glossary overlay for dataset', datasetId, ':', glossaryTerms.length, 'terms', glossaryTerms.map(t => t.name));
                    if (window.OverlayColumns && typeof window.OverlayColumns.enrichGlossaryOverlayTerms === 'function') {
                        return await window.OverlayColumns.enrichGlossaryOverlayTerms(glossaryTerms);
                    }
                    return glossaryTerms;
                }

                case 'processes':
                    try {
                        const resp = await fetch(`/api/process-impact/datasets/${datasetId}/processes`, { credentials: 'include' });
                        if (resp.ok) {
                            const data = await resp.json();
                            return Array.isArray(data?.data) ? data.data : (Array.isArray(data) ? data : []);
                        }
                    } catch (e) { /* ignore */ }
                    return [];

                case 'projects':
                    try {
                        const resp = await fetch(`/api/project-impact/datasets/${datasetId}/projects`, { credentials: 'include' });
                        if (resp.ok) {
                            const data = await resp.json();
                            return Array.isArray(data?.data) ? data.data : (Array.isArray(data) ? data : []);
                        }
                    } catch (e) { /* ignore */ }
                    return [];

                case 'policies':
                    try {
                        const resp = await fetch(`/api/policy-impact/datasets/${datasetId}/policies`, { credentials: 'include' });
                        if (resp.ok) {
                            const data = await resp.json();
                            return Array.isArray(data?.data) ? data.data : (Array.isArray(data) ? data : []);
                        }
                    } catch (e) { /* ignore */ }
                    return [];

                case 'business-area':
                    try {
                        const resp = await fetch(`/api/businessarea-impact/datasets/${datasetId}/businessareas`, { credentials: 'include' });
                        if (resp.ok) {
                            const data = await resp.json();
                            return Array.isArray(data?.data) ? data.data : (Array.isArray(data) ? data : []);
                        }
                    } catch (e) { /* ignore */ }
                    return [];

                case 'products':
                    try {
                        const resp = await fetch(`/api/dataset-impact/${datasetId}/products`, { credentials: 'include' });
                        if (resp.ok) {
                            const data = await resp.json();
                            return Array.isArray(data?.data) ? data.data : (Array.isArray(data) ? data : []);
                        }
                    } catch (e) { /* ignore */ }
                    return [];

                case 'legal-entities':
                    try {
                        const resp = await fetch(`/api/dataset-impact/${datasetId}/legals`, { credentials: 'include' });
                        if (resp.ok) {
                            const data = await resp.json();
                            return Array.isArray(data?.data) ? data.data : (Array.isArray(data) ? data : []);
                        }
                    } catch (e) { /* ignore */ }
                    return [];

                case 'clients':
                    try {
                        const resp = await fetch(`/api/dataset-impact/${datasetId}/clients`, { credentials: 'include' });
                        if (resp.ok) {
                            const data = await resp.json();
                            return Array.isArray(data?.data) ? data.data : (Array.isArray(data) ? data : []);
                        }
                    } catch (e) { /* ignore */ }
                    return [];

                case 'capabilities':
                    try {
                        const resp = await fetch(`/api/dataset-impact/${datasetId}/capabilities`, { credentials: 'include' });
                        if (resp.ok) {
                            const data = await resp.json();
                            return Array.isArray(data?.data) ? data.data : (Array.isArray(data) ? data : []);
                        }
                    } catch (e) { /* ignore */ }
                    return [];

                case 'data-quality':
                    try {
                        const cachedDQInfo = DatasetMapState.linkedDatasets?.get(String(datasetId));
                        if (cachedDQInfo?.dataset?.dataQuality) {
                            const dq = cachedDQInfo.dataset.dataQuality;
                            if (Array.isArray(dq)) return dq;
                            if (typeof dq === 'object') return [dq];
                        }
                        const dqResp = await fetch(`/api/data-quality/dataset/${datasetId}`, { credentials: 'include' });
                        if (dqResp.ok) {
                            const dqData = await dqResp.json();
                            return Array.isArray(dqData?.data) ? dqData.data : (Array.isArray(dqData) ? dqData : []);
                        }
                    } catch (e) { /* ignore */ }
                    return [];

                case 'data-privacy':
                    try {
                        const cachedPrivInfo = DatasetMapState.linkedDatasets?.get(String(datasetId));
                        if (cachedPrivInfo?.dataset) {
                            const ds = cachedPrivInfo.dataset;
                            if (ds.privacyClassification || ds.dataPrivacy || ds.sensitivity) {
                                return [{ name: ds.privacyClassification || ds.dataPrivacy || ds.sensitivity, type: 'privacy' }];
                            }
                        }
                        const dpResp = await fetch(`/api/data-privacy/dataset/${datasetId}`, { credentials: 'include' });
                        if (dpResp.ok) {
                            const dpData = await dpResp.json();
                            return Array.isArray(dpData?.data) ? dpData.data : (Array.isArray(dpData) ? dpData : []);
                        }
                    } catch (e) { /* ignore */ }
                    return [];

                case 'geography':
                    try {
                        const resp = await fetch(`/api/dataset-impact/${datasetId}/geographies`, { credentials: 'include' });
                        if (resp.ok) {
                            const data = await resp.json();
                            return Array.isArray(data?.data) ? data.data : (Array.isArray(data) ? data : []);
                        }
                    } catch (e) { /* ignore */ }
                    return [];

                default:
                    return [];
            }
        } catch (error) {
            embWarn(`[DATASET-MAP] Failed to fetch ${overlayType} for dataset ${datasetId}:`, error);
            return [];
        }
    }
    
    // Fetch overlay data for a system
    async function fetchOverlayDataForSystem(systemId, overlayType) {
        try {
            switch (overlayType) {
                case 'description':
                    const cachedInfo = DatasetMapState.connectedSystems?.get(String(systemId));
                    if (cachedInfo?.systemData) {
                        const desc = cachedInfo.systemData.description;
                        if (desc) {
                            return [{ name: 'Description', value: desc }];
                        }
                    }
                    try {
                        const resp = await fetch(`/api/system/${systemId}`, { credentials: 'include' });
                        if (resp.ok) {
                            const data = await resp.json();
                            const sys = data?.data || data;
                            const desc = sys?.description;
                            if (desc) {
                                return [{ name: 'Description', value: desc }];
                            }
                        }
                    } catch (e) { /* ignore */ }
                    return [];

                case 'stakeholders':
                    try {
                        const resp = await fetch(`/api/system-stakeholders/${systemId}`, { credentials: 'include' });
                        if (resp.ok) {
                            const data = await resp.json();
                            return Array.isArray(data?.data) ? data.data : (Array.isArray(data) ? data : []);
                        }
                    } catch (e) { /* ignore */ }
                    return [];

                case 'datasets':
                    try {
                        const API = window.BUDG_API_SERVICE;
                        if (API && typeof API.getSystemDatasets === 'function') {
                            const res = await API.getSystemDatasets(systemId).catch(() => null);
                            const data = res?.data ?? res;
                            return Array.isArray(data) ? data : (Array.isArray(data?.data) ? data.data : []);
                        }
                        const resp = await fetch(`/api/system-data/${systemId}/datasets`, { credentials: 'include' });
                        if (resp.ok) {
                            const data = await resp.json();
                            return Array.isArray(data?.data) ? data.data : (Array.isArray(data) ? data : []);
                        }
                    } catch (e) { /* ignore */ }
                    return [];

                case 'attributes':
                    try {
                        const sysDatasets = DatasetMapState.linkedDatasets
                            ? Array.from(DatasetMapState.linkedDatasets.entries()).filter(([, info]) => String(info.systemId) === String(systemId)).map(([did]) => did)
                            : [];
                        const allAttrs = [];
                        for (const did of sysDatasets) {
                            const attrResp = await fetch(`/api/attribute/${did}`, { credentials: 'include' });
                            if (attrResp.ok) {
                                const attrData = await attrResp.json();
                                const attrs = Array.isArray(attrData?.data) ? attrData.data : (Array.isArray(attrData) ? attrData : []);
                                attrs.forEach(a => allAttrs.push(a));
                            }
                        }
                        return allAttrs;
                    } catch (e) { /* ignore */ }
                    return [];

                case 'glossary': {
                    const glossaryTerms = [];
                    const seenGlossary = new Set();
                    try {
                        const glossaryScope = ensureDatasetMapGlossaryScope();
                        const systemIdStr = String(systemId);
                        const isCurrentSystem = !!(glossaryScope && systemIdStr === glossaryScope.currentSystemId);
                        let sysGlossaryDatasets = DatasetMapState.linkedDatasets
                            ? Array.from(DatasetMapState.linkedDatasets.entries()).filter(([, info]) => String(info.systemId) === systemIdStr).map(([did]) => did)
                            : [];
                        sysGlossaryDatasets = sysGlossaryDatasets.filter(did => isDatasetAllowedInDatasetMapGlossary(glossaryScope, did, systemIdStr));
                        for (const did of sysGlossaryDatasets) {
                            // 1) Dataset's own glossary
                            try {
                                const cachedDs = DatasetMapState.linkedDatasets?.get(String(did));
                                let dsGName = cachedDs?.dataset?.glossaryName;
                                let dsGId = cachedDs?.dataset?.glossaryId;
                                if (!dsGName) {
                                    const dsResp = await fetch(`/api/dataset/${did}`, { credentials: 'include' });
                                    if (dsResp.ok) {
                                        const dsJson = await dsResp.json();
                                        const ds = dsJson?.data || dsJson;
                                        dsGName = ds?.glossaryName;
                                        dsGId = ds?.glossaryId;
                                    }
                                }
                                if (dsGName && !seenGlossary.has(dsGName)) {
                                    seenGlossary.add(dsGName);
                                    glossaryTerms.push({ name: dsGName, glossary: dsGName, id: dsGId, glossaryId: dsGId, source: 'dataset' });
                                }
                                if (window.MapOverlayHighlight && dsGId != null) {
                                    recordHighlightGlossaryAnchor(window.MapOverlayHighlight.dsKey(did), dsGId);
                                }
                            } catch (e) { /* skip */ }
                            // 2) Glossary terms from attributes — for non-current systems,
                            // only include attributes linked to current via dataset relationships.
                            try {
                                const attrResp = await fetch(`/api/attribute/${did}`, { credentials: 'include' });
                                if (attrResp.ok) {
                                    const attrData = await attrResp.json();
                                    let attrs = Array.isArray(attrData?.data) ? attrData.data : (Array.isArray(attrData) ? attrData : []);
                                    if (!isCurrentSystem) {
                                        attrs = attrs.filter(a => isAttributeAllowedInDatasetMapGlossary(glossaryScope, did, a && (a.id != null ? a.id : (a.ID != null ? a.ID : a.attribute_id))));
                                    }
                                    attrs.forEach(attr => {
                                        const aid = attr && (attr.id != null ? attr.id : (attr.ID != null ? attr.ID : attr.attribute_id));
                                        if (aid != null) {
                                            getOverlayHighlightCache().attributeOwnerMap.set(String(aid), String(did));
                                        }
                                        const gName = adapter.extractGlossaryNameFromAttr(attr);
                                        const gId = attr.glossaryId || attr.glossary_id || attr.Glossary_ID;
                                        if (window.MapOverlayHighlight && aid != null && gId != null) {
                                            recordHighlightGlossaryAnchor(window.MapOverlayHighlight.attrKey(aid), gId);
                                        }
                                        if (gName && !seenGlossary.has(gName)) {
                                            seenGlossary.add(gName);
                                            glossaryTerms.push({ name: gName, glossary: gName, id: gId, glossaryId: gId, source: 'attribute' });
                                        }
                                    });
                                }
                            } catch (e) { /* skip */ }
                        }
                    } catch (e) { /* ignore */ }
                    if (window.OverlayColumns && typeof window.OverlayColumns.enrichGlossaryOverlayTerms === 'function') {
                        return await window.OverlayColumns.enrichGlossaryOverlayTerms(glossaryTerms);
                    }
                    return glossaryTerms;
                }

                case 'linking-attributes': {
                    const linkingByDataset = new Map();
                    const addLink = (did, aid) => {
                        if (!did || aid == null) return;
                        const key = String(did);
                        if (!linkingByDataset.has(key)) linkingByDataset.set(key, new Set());
                        linkingByDataset.get(key).add(String(aid));
                    };
                    // Existing in-memory relationships
                    (DatasetMapState.datasetRelationships || []).forEach(rel => {
                        const srcDid = rel.sourceDatasetId != null ? String(rel.sourceDatasetId) : (rel.Source_DatasetID != null ? String(rel.Source_DatasetID) : null);
                        const tgtDid = rel.targetDatasetId != null ? String(rel.targetDatasetId) : (rel.Target_DatasetID != null ? String(rel.Target_DatasetID) : null);
                        addLink(srcDid, rel.sourceAttributeId ?? rel.Source_AttributeID);
                        addLink(tgtDid, rel.targetAttributeId ?? rel.Target_AttributeID);
                    });
                    let systemDatasets = DatasetMapState.linkedDatasets ? Array.from(DatasetMapState.linkedDatasets.entries()).filter(([, info]) => String(info.systemId) === String(systemId)).map(([did]) => did) : [];
                    if (systemDatasets.length === 0) {
                        try {
                            const API = window.BUDG_API_SERVICE;
                            if (API && typeof API.getSystemDatasets === 'function') {
                                const res = await API.getSystemDatasets(systemId).catch(() => null);
                                const data = res?.data ?? res;
                                const list = Array.isArray(data) ? data : (Array.isArray(data?.data) ? data.data : []);
                                systemDatasets = list.map(d => d.id ?? d.ID ?? d.datasetId ?? d.dataset_id).filter(Boolean);
                            } else {
                                const resp = await fetch(`/api/system-data/${systemId}/datasets`, { credentials: 'include' });
                                if (resp.ok) {
                                    const data = await resp.json();
                                    const list = Array.isArray(data?.data) ? data.data : (Array.isArray(data) ? data : []);
                                    systemDatasets = list.map(d => d.id ?? d.ID ?? d.datasetId ?? d.dataset_id).filter(Boolean);
                                }
                            }
                        } catch (e) { /* ignore */ }
                    }
                    // Enrich links by reading direct dataflow relationships for each dataset in this system.
                    // This ensures System Lineage linking-attributes is not limited to the opened dataset only.
                    for (const did of systemDatasets) {
                        try {
                            const relResp = await fetch(`/api/dataflow/${did}/relationships`, { credentials: 'include' });
                            if (!relResp.ok) continue;
                            const relData = await relResp.json();
                            const inbound = relData?.inbound || [];
                            const outbound = relData?.outbound || [];
                            inbound.forEach(rel => {
                                addLink(rel.Source_DatasetID || rel.sourceDatasetId, rel.Source_AttributeID || rel.sourceAttributeId);
                                addLink(rel.Target_DatasetID || rel.targetDatasetId, rel.Target_AttributeID || rel.targetAttributeId);
                            });
                            outbound.forEach(rel => {
                                addLink(rel.Source_DatasetID || rel.sourceDatasetId, rel.Source_AttributeID || rel.sourceAttributeId);
                                addLink(rel.Target_DatasetID || rel.targetDatasetId, rel.Target_AttributeID || rel.targetAttributeId);
                            });
                        } catch (e) { /* ignore */ }
                    }
                    const out = [];
                    for (const datasetId of systemDatasets) {
                        const linkingIds = linkingByDataset.get(String(datasetId));
                        if (!linkingIds || linkingIds.size === 0) continue;
                        try {
                            const resp = await fetch(`/api/attribute/${datasetId}`, { credentials: 'include' });
                            if (resp.ok) {
                                const data = await resp.json();
                                const attrs = Array.isArray(data?.data) ? data.data : (Array.isArray(data) ? data : []);
                                attrs.filter(a => linkingIds.has(String(a.id ?? a.ID))).forEach(a => out.push(a));
                            }
                        } catch (e) { /* ignore */ }
                    }
                    return out;
                }

                case 'processes':
                    try {
                        const resp = await fetch(`/api/process-impact/systems/${systemId}/processes`, { credentials: 'include' });
                        if (resp.ok) {
                            const data = await resp.json();
                            return Array.isArray(data?.data) ? data.data : (Array.isArray(data) ? data : []);
                        }
                    } catch (e) { /* ignore */ }
                    return [];

                case 'projects':
                    try {
                        const resp = await fetch(`/api/project-impact/systems/${systemId}/projects`, { credentials: 'include' });
                        if (resp.ok) {
                            const data = await resp.json();
                            return Array.isArray(data?.data) ? data.data : (Array.isArray(data) ? data : []);
                        }
                    } catch (e) { /* ignore */ }
                    return [];

                case 'policies':
                    try {
                        const resp = await fetch(`/api/policy-impact/systems/${systemId}/policies`, { credentials: 'include' });
                        if (resp.ok) {
                            const data = await resp.json();
                            return Array.isArray(data?.data) ? data.data : (Array.isArray(data) ? data : []);
                        }
                    } catch (e) { /* ignore */ }
                    return [];

                case 'business-area':
                    try {
                        const resp = await fetch(`/api/businessarea-impact/systems/${systemId}/businessareas`, { credentials: 'include' });
                        if (resp.ok) {
                            const data = await resp.json();
                            return Array.isArray(data?.data) ? data.data : (Array.isArray(data) ? data : []);
                        }
                    } catch (e) { /* ignore */ }
                    return [];

                case 'products':
                    try {
                        const resp = await fetch(`/api/system-impact/${systemId}/products`, { credentials: 'include' });
                        if (resp.ok) {
                            const data = await resp.json();
                            return Array.isArray(data?.data) ? data.data : (Array.isArray(data) ? data : []);
                        }
                    } catch (e) { /* ignore */ }
                    return [];

                case 'legal-entities':
                    try {
                        const resp = await fetch(`/api/system-impact/${systemId}/legals`, { credentials: 'include' });
                        if (resp.ok) {
                            const data = await resp.json();
                            return Array.isArray(data?.data) ? data.data : (Array.isArray(data) ? data : []);
                        }
                    } catch (e) { /* ignore */ }
                    return [];

                case 'data-quality':
                    try {
                        const dqResp = await fetch(`/api/data-quality/system/${systemId}`, { credentials: 'include' });
                        if (dqResp.ok) {
                            const dqData = await dqResp.json();
                            return Array.isArray(dqData?.data) ? dqData.data : (Array.isArray(dqData) ? dqData : []);
                        }
                    } catch (e) { /* ignore */ }
                    return [];

                case 'data-privacy':
                    try {
                        const dpResp = await fetch(`/api/data-privacy/system/${systemId}`, { credentials: 'include' });
                        if (dpResp.ok) {
                            const dpData = await dpResp.json();
                            return Array.isArray(dpData?.data) ? dpData.data : (Array.isArray(dpData) ? dpData : []);
                        }
                    } catch (e) { /* ignore */ }
                    return [];

                case 'geography':
                    try {
                        const resp = await fetch(`/api/system-impact/${systemId}/geographies`, { credentials: 'include' });
                        if (resp.ok) {
                            const data = await resp.json();
                            return Array.isArray(data?.data) ? data.data : (Array.isArray(data) ? data : []);
                        }
                    } catch (e) { /* ignore */ }
                    return [];

                default:
                    return [];
            }
        } catch (error) {
            embWarn(`[DATASET-MAP] Failed to fetch ${overlayType} for system ${systemId}:`, error);
            return [];
        }
    }
    
    // Load overlay data for all visible nodes (parallel loading like system map)
    async function loadOverlayData(overlayType) {
        if (!DatasetMapState.network) return;
        
        const nodes = DatasetMapState.network.nodes();
        const isDatasetLineage = DatasetMapState.mapType === 'dataset-lineage';
        
        embLog('[DATASET-MAP] Loading overlay data:', overlayType, 'for', nodes.length, 'nodes, mapType:', DatasetMapState.mapType);

        // Reset and rebuild the cross-panel highlight cache from the latest
        // dataset relationships. Glossary anchors will be recorded as the
        // glossary fetch loops below run.
        resetOverlayHighlightCache();
        rebuildHighlightCacheFromRelationships();

        try {
            const overlayData = new Map();
            
            if (isDatasetLineage) {
                // Dataset lineage - get dataset IDs
                const datasetIds = nodes.map(n => {
                    const meta = n.data('meta') || {};
                    return meta.datasetId;
                }).filter(id => id && id !== 'undefined');
                
                embLog('[DATASET-MAP] Dataset IDs for overlay:', datasetIds);
                
                // Fetch overlay data in parallel for all datasets (include empty so panel shows for every node)
                await Promise.all(datasetIds.map(async (datasetId) => {
                    try {
                        const data = await fetchOverlayDataForDataset(datasetId, overlayType);
                        overlayData.set(String(datasetId), Array.isArray(data) ? data : []);
                        if (data && data.length > 0) {
                            embLog('[DATASET-MAP] Dataset', datasetId, overlayType, ':', data.length, 'items');
                        }
                    } catch (error) {
                        embWarn(`[DATASET-MAP] Failed to load ${overlayType} for dataset ${datasetId}:`, error);
                        overlayData.set(String(datasetId), []);
                    }
                }));
            } else {
                // System lineage - get system IDs
                const systemIds = nodes.map(n => {
                    const meta = n.data('meta') || {};
                    return meta.systemId;
                }).filter(id => id && id !== 'undefined');
                
                embLog('[DATASET-MAP] System IDs for overlay:', systemIds);
                
                // Fetch overlay data in parallel for all systems (include empty so panel shows for every node)
                await Promise.all(systemIds.map(async (systemId) => {
                    try {
                        const data = await fetchOverlayDataForSystem(systemId, overlayType);
                        overlayData.set(String(systemId), Array.isArray(data) ? data : []);
                        if (data && data.length > 0) {
                            embLog('[DATASET-MAP] System', systemId, overlayType, ':', data.length, 'items');
                        }
                    } catch (error) {
                        embWarn(`[DATASET-MAP] Failed to load ${overlayType} for system ${systemId}:`, error);
                        overlayData.set(String(systemId), []);
                    }
                }));
            }
            
            // Store overlay data in state
            DatasetMapState.overlayData = overlayData;

            // Finalise the unified anchor graph now that glossary anchors and
            // attribute owners have been populated by the per-item glossary fetch.
            try {
                const _hcache = getOverlayHighlightCache();
                if (window.MapOverlayHighlight && typeof window.MapOverlayHighlight.buildAnchorGraph === 'function') {
                    _hcache.anchorGraph = window.MapOverlayHighlight.buildAnchorGraph(
                        _hcache.datasetGraph || new Map(),
                        _hcache.attributeRels || [],
                        _hcache.attributeOwnerMap || new Map()
                    );
                }
            } catch (cacheErr) {
                embWarn('[DATASET-MAP] Failed to finalise highlight cache:', cacheErr);
            }

            try {
                const focusNodeIds = new Set();
                if (isDatasetLineage) {
                    if (DatasetMapState.datasetId != null) focusNodeIds.add(String(DatasetMapState.datasetId));
                } else if (DatasetMapState.network) {
                    DatasetMapState.network.nodes().forEach(function (node) {
                        const m = node.data('meta') || {};
                        if (node.data('isCurrent') && m.systemId) focusNodeIds.add(String(m.systemId));
                    });
                }
                const _hcacheDr = getOverlayHighlightCache();
                if (window.MapOverlayHighlight && typeof window.MapOverlayHighlight.sortOverlayDataByRelevance === 'function') {
                    window.MapOverlayHighlight.sortOverlayDataByRelevance({
                        overlayType: overlayType,
                        overlayData: overlayData,
                        cache: _hcacheDr,
                        getItemId: getOverlayItemId,
                        getItemText: getOverlayItemText,
                        focusNodeIds: focusNodeIds
                    });
                }
            } catch (sortErr) { /* non-fatal */ }

            // Render overlay panels
            renderOverlayPanels(overlayType, overlayData);
            
        } catch (error) {
            console.error('[DATASET-MAP] Error loading overlay data:', error);
        }
    }
    
    // Render overlay panels for each node with data
    function renderOverlayPanels(overlayType, overlayData) {
        if (!DatasetMapState.network || !DatasetMapState.canvas) return;
        
        const container = DatasetMapState.canvas;
        
        // Create overlay container if not exists (position above graph like other maps)
        let overlayContainer = container.querySelector('.map-overlay-container');
        if (!overlayContainer) {
            overlayContainer = document.createElement('div');
            overlayContainer.classList.add('map-overlay-container');
            overlayContainer.style.cssText = 'position:absolute;top:0;left:0;width:100%;height:100%;pointer-events:none;z-index:50;';
            container.style.position = container.style.position || 'relative';
            container.appendChild(overlayContainer);
        }
        
        // Clear existing panels
        overlayContainer.innerHTML = '';
        
        const isDatasetLineage = DatasetMapState.mapType === 'dataset-lineage';
        
        DatasetMapState.network.nodes().forEach(node => {
            const nodeId = node.data('id');
            const meta = node.data('meta') || {};
            let data;
            
            // Lookup overlay data based on map type — show panel only when there is data
            if (isDatasetLineage) {
                const datasetId = meta.datasetId;
                if (datasetId != null) {
                    data = overlayData.get(String(datasetId));
                }
            } else {
                const systemId = meta.systemId;
                if (systemId != null) {
                    data = overlayData.get(String(systemId));
                }
            }
            if (data === undefined) data = [];
            if (!Array.isArray(data)) data = [];
            if (!data.length) return;
            
            const panel = createOverlayPanel(node.data('label'), overlayType, data, nodeId);
            if (!panel) return;
            overlayContainer.appendChild(panel);
            panel.style.pointerEvents = 'auto';
            positionOverlayPanelElement(panel, node);
            node.addClass('has-overlay');
        });
        
        // Update panel positions on zoom/pan/drag
        DatasetMapState.network.on('zoom pan', updateOverlayPositions);
        DatasetMapState.network.on('drag', 'node', updateOverlayPositions);
        requestAnimationFrame(() => requestAnimationFrame(updateOverlayPositions));
    }
    
    function positionOverlayPanelElement(panel, node) {
        const overlayContainer = DatasetMapState.canvas?.querySelector('.map-overlay-container');
        const network = DatasetMapState.network;
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

    function setFilter(filterType, enabled) {
        DatasetMapState.filters[filterType] = enabled;
        const graph = DatasetMapState.mapType === 'dataset-lineage' ? buildDatasetLineageGraph() : buildSystemLineageGraph();
        renderNetwork(graph);
    }

    function setNodeFilters(nodeFilters) {
        DatasetMapState.nodeFilters = {
            classifications: nodeFilters.classifications || [],
            types: nodeFilters.types || [],
            lifecycles: nodeFilters.lifecycles || []
        };
        DatasetMapState.filtersInitialized = true;
        applyNodeFiltersToNetwork();
    }

    function setDatasetNodeFilters(datasetFilters) {
        DatasetMapState.datasetNodeFilters = {
            types: datasetFilters.types || [],
            lifecycles: datasetFilters.lifecycles || []
        };
        adapter.applyDatasetNodeFilters({
            linkedDatasets: DatasetMapState.linkedDatasets,
            updateOverlayPositions: updateOverlayPositions,
            filtersInitialized: true
        });
    }

    function applyNodeFiltersToNetwork() {
        adapter.applySystemNodeFilters({
            updateOverlayPositions: updateOverlayPositions,
            filtersInitialized: DatasetMapState.filtersInitialized === true
        });
    }

    function zoomIn()  { adapter.zoomIn(); }
    function zoomOut() { adapter.zoomOut(); }

    function resetMap() {
        if (!DatasetMapState.hiddenNodes) DatasetMapState.hiddenNodes = new Set();
        if (!DatasetMapState.hiddenUpstreamNodes) DatasetMapState.hiddenUpstreamNodes = new Set();
        if (!DatasetMapState.hiddenDownstreamNodes) DatasetMapState.hiddenDownstreamNodes = new Set();
        DatasetMapState.hiddenNodes.clear();
        DatasetMapState.focusedNode = null;
        DatasetMapState.hiddenUpstreamNodes.clear();
        DatasetMapState.hiddenDownstreamNodes.clear();
        networkIx.resetHighlights();
        const graph = DatasetMapState.mapType === 'dataset-lineage' ? buildDatasetLineageGraph() : buildSystemLineageGraph();
        renderNetwork(graph);
    }

    function redrawMap() {
        if (DatasetMapState.network) {
            DatasetMapState.network.layout(buildCytoscapeLayout()).run();
        }
    }

    function exportAsPng() {
        if (!DatasetMapState.network) {
            alert('No map to export');
            return;
        }
        const filename = `dataset-relationships-map-${DatasetMapState.datasetId}.png`;
        if (typeof window.exportMapWithOverlays === 'function') {
            window.exportMapWithOverlays(DatasetMapState.network, DatasetMapState.canvas, filename);
        } else {
            const png64 = DatasetMapState.network.png({ bg: '#ffffff', full: true, scale: 2 });
            const link = document.createElement('a');
            link.download = filename;
            link.href = png64;
            link.click();
        }
    }

    // Fullscreen: open map in new tab with full toolbar controls
    function openFullscreen() {
        if (!DatasetMapState.network) {
            if (typeof window.toastMessage === 'function') {
                window.toastMessage(window.I18n?.t('dataset.map.notReady') || 'Map is still loading or has no data.');
            }
            return;
        }
        try {
            const nodes = DatasetMapState.network.nodes().map(function (n) { return n.json(); });
            const edges = DatasetMapState.network.edges().map(function (e) { return e.json(); });
            const datasetName = (DatasetMapState.datasetData && DatasetMapState.datasetData.name) ? String(DatasetMapState.datasetData.name).replace(/</g, '&lt;') : 'Dataset';

            if (typeof window.openMapFullscreen === 'function') {
                window.openMapFullscreen({
                    title: 'Dataset Relationships Map - ' + datasetName,
                    elements: { nodes: nodes, edges: edges },
                    style: getCytoscapeStyle(),
                    layoutName: DatasetMapState.layout || 'top-to-bottom',
                    legendHtml: getLegendHtml(),
                    exportFilename: 'dataset-relationships-map-' + DatasetMapState.datasetId + '.png',
                    toolbarAnchor: DatasetMapState.canvas,
                    mapType: DatasetMapState.mapType || 'dataset-lineage',
                    mapTabKind: 'dataset-relationships',
                    getState: function () { return DatasetMapState; }
                });
            } else {
                var newWindow = window.open('', '_blank');
                if (!newWindow) { alert('Popup blocked.'); return; }
                newWindow.document.write('<!DOCTYPE html><html><head><title>Dataset Relationships Map</title><style>body{margin:0;padding:0;}#fullscreenMap{width:100vw;height:100vh;}</style><script src="/assets/js/cytoscape.min.js"><\/script></head><body><div id="fullscreenMap"></div><script>cytoscape({container:document.getElementById("fullscreenMap"),elements:' + JSON.stringify({ nodes: nodes, edges: edges }) + ',style:' + JSON.stringify(getCytoscapeStyle()) + ',layout:{name:"preset"}});<\/script></body></html>');
                newWindow.document.close();
            }
        } catch (e) {
            console.error('[DATASET-MAP] openFullscreen failed:', e);
            if (typeof window.toastMessage === 'function') {
                window.toastMessage(window.I18n?.t('dataset.map.openFailed') || 'Could not open map in new tab.');
            }
        }
    }

    /** Returns layout config with roots as node IDs only (for JSON serialization / minimap etc.). */
    function getSerializableLayoutConfig() {
        const raw = buildCytoscapeLayout();
        const config = Object.assign({}, raw);
        if (Array.isArray(config.roots) && config.roots.length > 0) {
            config.roots = config.roots.map(function (r) {
                return typeof r === 'string' ? r : (r && r.id ? r.id() : r);
            });
        }
        if (typeof config.transform === 'function') delete config.transform;
        return config;
    }

    function toggleNavigator() {
        const container = DatasetMapState.canvas && DatasetMapState.canvas.parentElement;
        if (!container || !DatasetMapState.network || !window.MapRenderUtils) return;
        window.MapRenderUtils.attachOrToggleLineageMinimap({
            container: container,
            network: DatasetMapState.network,
            minimapCanvasId: 'datasetRelationshipsMapMinimap',
            syncViewport: true,
            cytoscapeOptions: { minZoom: 0.1, maxZoom: 0.5 }
        });
    }

    function toggleInterfaceLabels(show) {
        DatasetMapState.showInterfaceLabels = show !== undefined ? show : !DatasetMapState.showInterfaceLabels;
        const graph = DatasetMapState.mapType === 'dataset-lineage' ? buildDatasetLineageGraph() : buildSystemLineageGraph();
        renderNetwork(graph);
    }

    function escapeHtml(str) {
        if (!str) return '';
        const div = document.createElement('div');
        div.textContent = str;
        return div.innerHTML;
    }

    // ========== OVERLAY PANEL FUNCTIONS ==========
    
    // Get display title for overlay type
    function getOverlayTitle(overlayType, nodeId) {
        if (overlayType === 'description' && nodeId != null && String(nodeId).indexOf('dataset-') === 0) {
            return 'Definition';
        }
        const titles = {
            'description': 'Description',
            'stakeholders': 'Stakeholders',
            'datasets': 'Data Sets',
            'attributes': 'Attributes',
            'linking-attributes': 'Linking Attributes',
            'glossary': 'Glossaries',
            'processes': 'Processes',
            'projects': 'Projects',
            'policies': 'Policies',
            'business-area': 'Business Area',
            'products': 'Products',
            'legal-entities': 'Legal Entities',
            'clients': 'Clients',
            'capabilities': 'Capabilities',
            'custom-fields': 'Custom Fields',
            'data-quality': 'Data Quality',
            'data-privacy': 'Data Privacy',
            'geography': 'Geography'
        };
        return titles[overlayType] || overlayType;
    }

    // Get display text for an overlay item
    function getOverlayItemText(overlayType, item) {
        if (!item) return '';
        
        switch (overlayType) {
            case 'description':
                return item.value || item.description || '';
            case 'stakeholders':
                const role = item.roleName || item.role || '';
                const name = item.personName || item.name || '';
                return role ? `${name} (${role})` : name;
            case 'datasets':
                return item.primaryName || item.name || item.shortName || '';
            case 'attributes':
                return item.name || item['Name attribute'] || item.attributeName || item.primaryName || item.PrimaryName || item.Name || '';
            case 'linking-attributes': {
                const base = item.name || item['Name attribute'] || item.attributeName || item.primaryName || item.PrimaryName || item.Name || '';
                const dir = item.direction ? String(item.direction).toUpperCase() : '';
                const related = item.relatedDataset || '';
                return dir && related ? `${base} (${dir} -> ${related})` : base;
            }
            case 'glossary': {
                const gName = item.glossary || item.name || '';
                const srcLabel = item.source === 'dataset' ? 'Dataset Glossary'
                               : item.source === 'attribute' ? 'Attribute Glossary' : '';
                return srcLabel ? `${gName} (${srcLabel})` : gName;
            }
            case 'processes':
                return item.processName || item.PrimaryName || item.primaryName || item.name || item.Name || '';
            case 'projects':
                return item.projectName || item.PrimaryName || item.primaryName || item.name || item.Name || '';
            case 'policies':
                return item.policyName || item.PrimaryName || item.primaryName || item.name || item.Name || '';
            case 'business-area':
                return item.businessAreaName || item.PrimaryName || item.primaryName || item.name || item.Name || '';
            case 'products':
                return item.productName || item.PrimaryName || item.primaryName || item.name || item.Name || '';
            case 'legal-entities':
                return item.legalEntityName || item.legalShortName || item.LegalShortName ||
                    item.legalLongName || item.LegalLongName || item.longName || item.longname ||
                    item.name || item.Name || '';
            case 'clients':
                return item.clientName || item.PrimaryName || item.primaryName || item.name || item.Name || '';
            case 'capabilities':
                return item.capabilityName || item.PrimaryName || item.primaryName || item.name || item.Name || '';
            case 'custom-fields':
                return item.fieldName ? `${item.fieldName}: ${item.value || ''}` : (item.name || '');
            case 'data-quality':
                return item.ruleName || item.name || '';
            case 'data-privacy':
                return item.privacyClassification || item.classification || item.name || item.value || '';
            case 'geography':
                return item.name || item.region || item.country || '';
            default:
                return item.name || item.primaryName || item.PrimaryName || item.Name || JSON.stringify(item);
        }
    }

    // Get ID for an overlay item
    function getOverlayItemId(overlayType, item) {
        if (!item) return null;
        return item.id || item.ID || item.Id || null;
    }

    // Get value for a specific overlay column/field (for table view)
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
                    case 'name': return v(item.glossary || item.name || item.primaryName);
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
            case 'datasets':
                switch (fieldId) {
                    case 'name': return v(item.primaryName || item.PrimaryName || item.name || item.shortName);
                    case 'refNumber': return v(item.refNumber || item.refnumber || item.RefNumber || item.ref || item.Ref || item.processRefNumber || item.ProcessRefNumber || item.processRef || item.ProcessRef || item.projectRefNumber || item.ProjectRefNumber || item.projectRef || item.ProjectRef || item.productRefNumber || item.ProductRefNumber || item.policyRefNumber || item.PolicyRefNumber || item.capabilityRefNumber || item.CapabilityRefNumber || item.datasetRefNumber || item.DatasetRefNumber || item.attributeRefNumber || item.AttributeRefNumber || item.glossaryRefNumber || item.GlossaryRefNumber || item.interfaceRefNumber || item.InterfaceRefNumber || item.systemRef || item.SystemRef || item.regulationRefNumber || item.RegulationRefNumber || item.regulatoryThemeRefNumber || item.RegulatoryThemeRefNumber || item.businessAreaReference || item.BusinessAreaReference || item.clientReference || item.ClientReference || item.legalReference || item.LegalReference || item.sourceProcessRef || item.targetProcessRef);
                    case 'type': return v(item.typeName || item.type || item.TypeName || item.Type);
                    case 'lifecycle': return v(item.lifecycleName || item.LifecycleName || item.lifecycle || item.Lifecycle || item.lifecycleStatusName || item.LifecycleStatusName || item.lifecycleStatus || item.LifecycleStatus || item.processLifecycleName || item.ProcessLifecycleName || item.sourceProcessLifecycleName || item.targetProcessLifecycleName || item.datasetLifecycleName || item.DatasetLifecycleName || fieldDefault(item, fieldId));
                    default: return fieldDefault(item, fieldId);
                }
            case 'attributes':
                switch (fieldId) {
                    case 'name': return v(item.name || item['Name attribute'] || item.attributeName || item.primaryName || item.PrimaryName || item.Name);
                    case 'type': return v(item.typeName || item.type || item.TypeName || item.Type);
                    case 'glossary': return v(item.glossaryName || item.glossary);
                    case 'refNumber': return v(item.refNumber || item.refnumber || item.RefNumber || item.ref || item.Ref || item.processRefNumber || item.ProcessRefNumber || item.processRef || item.ProcessRef || item.projectRefNumber || item.ProjectRefNumber || item.projectRef || item.ProjectRef || item.productRefNumber || item.ProductRefNumber || item.policyRefNumber || item.PolicyRefNumber || item.capabilityRefNumber || item.CapabilityRefNumber || item.datasetRefNumber || item.DatasetRefNumber || item.attributeRefNumber || item.AttributeRefNumber || item.glossaryRefNumber || item.GlossaryRefNumber || item.interfaceRefNumber || item.InterfaceRefNumber || item.systemRef || item.SystemRef || item.regulationRefNumber || item.RegulationRefNumber || item.regulatoryThemeRefNumber || item.RegulatoryThemeRefNumber || item.businessAreaReference || item.BusinessAreaReference || item.clientReference || item.ClientReference || item.legalReference || item.LegalReference || item.sourceProcessRef || item.targetProcessRef);
                    default: return fieldDefault(item, fieldId);
                }
            case 'linking-attributes':
                switch (fieldId) {
                    case 'name': return v(item.name || item['Name attribute'] || item.attributeName || item.primaryName || item.PrimaryName || item.Name);
                    case 'type': return v(item.typeName || item.type || item.TypeName || item.Type);
                    case 'glossary': return v(item.glossaryName || item.glossary || item['Glossary Name attribute']);
                    case 'direction': return v(item.direction || item.Direction);
                    case 'relatedDataset': return v(item.relatedDataset || item.related_dataset);
                    case 'relatedAttribute': return v(item.relatedAttribute || item.related_attribute);
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
                return v(item.name || item.primaryName);
            }
        }
    }

    // Create an overlay panel element (header + filter bar + body; filter by name, Show max; table when columns selected)
    function createOverlayPanel(nodeName, overlayType, data, nodeId) {
        const arr = Array.isArray(data) ? data : [];
        if (arr.length === 0) return null;
        if (!window.MapOverlayPanel) {
            return createOverlayPanelFallback(nodeName, overlayType, arr, nodeId);
        }
        var overlayColumnDefs = [];
        if (window.OverlayColumns) {
            var allCols = window.OverlayColumns.getOverlayColumns(overlayType);
            var selectedIds = window.OverlayColumns.resolveSelectedColumnIds(DatasetMapState.overlayColumnsByType[overlayType], overlayType);
            overlayColumnDefs = allCols.filter(function(c) { return selectedIds && selectedIds.indexOf(c.id) !== -1; });
        }
        const panel = window.MapOverlayPanel.create(overlayType, arr, nodeId, {
            getTitle: getOverlayTitle,
            getItemText: getOverlayItemText,
            getItemId: getOverlayItemId,
            escapeHtml: escapeHtml,
            getItemField: getOverlayItemField,
            overlayColumnDefs: overlayColumnDefs,
            onItemClick: function(panel, el) {
                const idx = parseInt(el.getAttribute('data-item-index'), 10);
                const list = panel._overlayData;
                const item = (list && list[idx] != null) ? list[idx] : null;
                if (item != null) highlightOverlayItem(panel._overlayType, item, panel.getAttribute('data-node-id'));
            }
        });
        return panel;
    }

    function createOverlayPanelFallback(nodeName, overlayType, data, nodeId) {
        const panel = document.createElement('div');
        panel.className = 'map-node-overlay-panel';
        panel.setAttribute('data-node-id', nodeId);
        panel._overlayData = data;
        panel._overlayType = overlayType;
        const body = document.createElement('div');
        body.className = 'map-node-overlay-body';
        body.textContent = 'Overlay not available.';
        panel.appendChild(body);
        return panel;
    }

    function clearOverlayHighlights() {
        var overlayContainer = DatasetMapState.canvas?.querySelector('.map-overlay-container');
        if (overlayContainer) {
            overlayContainer.querySelectorAll('.map-node-overlay-item').forEach(function(row) {
                row.classList.remove('highlighted-source', 'highlighted-related');
            });
        }
        if (DatasetMapState.network) {
            DatasetMapState.network.elements().removeClass('overlay-highlight-source-node overlay-highlight-related-node overlay-highlight-edge');
        }
        DatasetMapState._highlightedOverlayKey = null;
    }

    function highlightOverlayItem(overlayType, item, sourceNodeId) {
        var overlayContainer = DatasetMapState.canvas?.querySelector('.map-overlay-container');
        if (!overlayContainer) return;

        // Build a key to detect re-click on the same item
        var itemId = getOverlayItemId(overlayType, item);
        var itemText = getOverlayItemText(overlayType, item);
        var key = overlayType + '|' + sourceNodeId + '|' + (itemId != null ? itemId : itemText);

        // Toggle off if same item clicked again
        if (DatasetMapState._highlightedOverlayKey === key) {
            clearOverlayHighlights();
            return;
        }

        // For attributes/linking-attributes use relationship-based highlighting
        if ((overlayType === 'attributes' || overlayType === 'linking-attributes') && item && (item.id != null || item.ID != null)) {
            var attrId = item.id != null ? item.id : item.ID;
            highlightAttributeRelationships(overlayContainer, attrId, sourceNodeId);
            DatasetMapState._highlightedOverlayKey = key;
            return;
        }

        // Clear previous highlights
        overlayContainer.querySelectorAll('.map-node-overlay-item').forEach(function(row) {
            row.classList.remove('highlighted-source', 'highlighted-related');
        });

        // Cross-panel transitive BFS for datasets and glossary overlays.
        var helper = window.MapOverlayHighlight;
        var hcache = getOverlayHighlightCache();
        var datasetRelatedIds = null;
        var glossaryRelatedIds = null;
        if (overlayType === 'datasets' && itemId && helper && hcache && hcache.datasetGraph) {
            datasetRelatedIds = helper.findAllRelatedDatasets(String(itemId), hcache.datasetGraph);
        }
        if (overlayType === 'glossary' && itemId && helper && hcache && hcache.anchorGraph) {
            glossaryRelatedIds = helper.findAllRelatedGlossaries(
                String(itemId),
                hcache.glossaryToAnchors,
                hcache.anchorGraph,
                hcache.anchorToGlossaries
            );
        }

        overlayContainer.querySelectorAll('.map-node-overlay-item').forEach(function(row) {
            var panel = row.closest('.map-node-overlay-panel');
            var isSource = panel?.getAttribute('data-node-id') === sourceNodeId;
            var rowItemId = row.dataset.itemId ? String(row.dataset.itemId) : null;
            var shouldHighlight = false;
            if (itemId && rowItemId && rowItemId === String(itemId)) shouldHighlight = true;
            if (!shouldHighlight && row.dataset.overlayValue === itemText) shouldHighlight = true;
            if (shouldHighlight) {
                if (isSource) row.classList.add('highlighted-source');
                else row.classList.add('highlighted-related');
                return;
            }
            if (datasetRelatedIds && rowItemId && datasetRelatedIds.has(rowItemId)) {
                row.classList.add('highlighted-related');
                return;
            }
            if (glossaryRelatedIds && rowItemId && glossaryRelatedIds.has(rowItemId)) {
                row.classList.add('highlighted-related');
            }
        });
        applyOverlayRelatedNodeAndEdgeHighlights(overlayContainer, sourceNodeId);
        DatasetMapState._highlightedOverlayKey = key;
    }

    function highlightAttributeRelationships(overlayContainer, clickedAttributeId, sourceNodeId) {
        var relationships = DatasetMapState.datasetRelationships || [];
        var clickedAttrIdStr = String(clickedAttributeId);
        var relatedAttributeIds = (window.MapOverlayHighlight && typeof window.MapOverlayHighlight.findAllRelatedAttributes === 'function')
            ? window.MapOverlayHighlight.findAllRelatedAttributes(clickedAttrIdStr, relationships)
            : new Set();

        overlayContainer.querySelectorAll('.map-node-overlay-item').forEach(function(el) {
            el.classList.remove('highlighted-source', 'highlighted-related');
            var attrIdStr = (el.dataset.attributeId || el.dataset.itemId) ? String(el.dataset.attributeId || el.dataset.itemId) : null;
            if (!attrIdStr) return;
            var panel = el.closest('.map-node-overlay-panel');
            var isSource = panel?.getAttribute('data-node-id') === sourceNodeId;
            if (attrIdStr === clickedAttrIdStr) {
                el.classList.add('highlighted-source');
            } else if (relatedAttributeIds.has(attrIdStr)) {
                el.classList.add('highlighted-related');
            }
        });
        applyOverlayRelatedNodeAndEdgeHighlights(overlayContainer, sourceNodeId);
    }

    function applyOverlayRelatedNodeAndEdgeHighlights(overlayContainer, sourceNodeId) {
        var network = DatasetMapState.network;
        if (!network) return;
        network.elements().removeClass('overlay-highlight-source-node overlay-highlight-related-node overlay-highlight-edge');
        var relatedNodeIds = new Set();
        overlayContainer.querySelectorAll('.map-node-overlay-item.highlighted-source, .map-node-overlay-item.highlighted-related').forEach(function(el) {
            var panel = el.closest('.map-node-overlay-panel');
            var nodeId = panel?.getAttribute('data-node-id');
            if (nodeId) relatedNodeIds.add(nodeId);
        });
        relatedNodeIds.forEach(function(nodeId) {
            var node = network.getElementById(nodeId);
            if (node.length) node.addClass(nodeId === sourceNodeId ? 'overlay-highlight-source-node' : 'overlay-highlight-related-node');
        });
        network.edges().forEach(function(edge) {
            var src = edge.source().id();
            var tgt = edge.target().id();
            if (relatedNodeIds.has(src) && relatedNodeIds.has(tgt)) edge.addClass('overlay-highlight-edge');
        });
    }

    // findAllRelatedAttributes lives in window.MapOverlayHighlight (shared helper).

    function updateOverlayPositions() {
        const overlayContainer = DatasetMapState.canvas?.querySelector('.map-overlay-container');
        if (!overlayContainer || !DatasetMapState.network) return;
        const cw = overlayContainer.clientWidth || 800;
        const ch = overlayContainer.clientHeight || 600;
        const buf = 150;

        overlayContainer.querySelectorAll('.map-node-overlay-panel').forEach(panel => {
            const nodeId = panel.getAttribute('data-node-id');
            const node = DatasetMapState.network.getElementById(nodeId);
            
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

    // ─────────────────────────────────────────────────────────────────────────
    // Graph pipeline + MapEngine (shared/map/dataset-facet-lineage-graph.js)
    // ─────────────────────────────────────────────────────────────────────────

    graphPipeline = window.createDatasetFacetLineageGraphPipeline({
        state: DatasetMapState,
        adapter: adapter,
        renderNetwork: renderNetwork,
        showPlaceholder: showPlaceholder,
        showLoading: showLoading,
        hideLoading: hideLoading,
        logPrefix: '[DATASET-MAP]'
    });

    var _mapEngineApiLoader = graphPipeline._mapEngineApiLoader;
    var _mapEngineNodeBuilder = graphPipeline._mapEngineNodeBuilder;
    var _mapEngineEdgeBuilder = graphPipeline._mapEngineEdgeBuilder;

    if (window.MapConfigs && window.MapConfigs.register) {
        window.MapConfigs.register('dataset-lineage', {
            apiLoader:     _mapEngineApiLoader,
            nodeBuilder:   _mapEngineNodeBuilder,
            edgeBuilder:   _mapEngineEdgeBuilder,
            legendBuilder: getLegendHtml
        });
    }

    function getDatasetRelationshipsMapHtml() {
        if (window.SharedMapHTML) {
            return window.SharedMapHTML({
                mapId: 'datasetRelationshipsMap',
                defaultMapType: 'dataset-lineage',
                mapTypeOptions: [
                    { value: 'dataset-lineage', label: 'Dataset Lineage' },
                    { value: 'system-lineage', label: 'System Lineage' }
                ],
                overlayConfig: {
                    systemLineage: {
                        columns: [
                            {
                                header: 'Data',
                                items: [
                                    { overlay: 'description', icon: 'fa-info-circle', label: 'Description' },
                                    { overlay: 'glossary', icon: 'fa-book', label: 'Glossary' },
                                    { overlay: 'datasets', icon: 'fa-layer-group', label: 'Data Sets' },
                                    { overlay: 'attributes', icon: 'fa-th', label: 'Attributes' },
                                    { overlay: 'linking-attributes', icon: 'fa-th', label: 'Linking Attributes' },
                                    { overlay: 'data-quality', icon: 'fa-bullseye', label: 'Data Quality' },
                                    { overlay: 'data-privacy', icon: 'fa-lock', label: 'Data Privacy' }
                                ]
                            },
                            {
                                header: 'Business',
                                items: [
                                    { overlay: 'stakeholders', icon: 'fa-users', label: 'Stakeholders' },
                                    { overlay: 'processes', icon: 'fa-play', label: 'Processes' },
                                    { overlay: 'projects', icon: 'fa-project-diagram', label: 'Projects' },
                                    { overlay: 'policies', icon: 'fa-file-alt', label: 'Policies' }
                                ]
                            },
                            {
                                header: 'Organizational',
                                items: [
                                    { overlay: 'business-area', icon: 'fa-briefcase', label: 'Business Area' },
                                    { overlay: 'products', icon: 'fa-tag', label: 'Products' },
                                    { overlay: 'legal-entities', icon: 'fa-landmark', label: 'Legal Entities' }
                                ]
                            },
                            {
                                header: 'Regulatory',
                                items: [
                                    { overlay: 'geography', icon: 'fa-globe', label: 'Geography' }
                                ]
                            }
                        ]
                    },
                    datasetLineage: {
                        columns: [
                            {
                                header: 'Data',
                                items: [
                                    { overlay: 'description', icon: 'fa-info-circle', label: 'Definition' },
                                    { overlay: 'glossary', icon: 'fa-book', label: 'Glossary' },
                                    { overlay: 'attributes', icon: 'fa-th', label: 'Attributes' },
                                    { overlay: 'linking-attributes', icon: 'fa-th', label: 'Linking Attributes' },
                                    { overlay: 'data-quality', icon: 'fa-bullseye', label: 'Data Quality' }
                                ]
                            },
                            {
                                header: 'Business',
                                items: [
                                    { overlay: 'stakeholders', icon: 'fa-users', label: 'Stakeholders' },
                                    { overlay: 'processes', icon: 'fa-play', label: 'Processes' },
                                    { overlay: 'projects', icon: 'fa-project-diagram', label: 'Projects' },
                                    { overlay: 'policies', icon: 'fa-file-alt', label: 'Policies' }
                                ]
                            }
                        ]
                    }
                },
                filterConfig: {
                    systemLineage: {
                        categories: [
                            {
                                header: 'LINKS',
                                options: [
                                    { id: 'systemInterfaces', label: 'System Interfaces', checked: true },
                                    { id: 'dataAttributeLinks', label: 'Data Attribute Links', checked: true }
                                ]
                            },
                            {
                                header: 'CLASSIFICATION',
                                dynamic: true,
                                containerId: 'datasetRelationshipsSystemFilterClassificationOptions'
                            },
                            {
                                header: 'TYPE',
                                dynamic: true,
                                containerId: 'datasetRelationshipsSystemFilterTypeOptions'
                            },
                            {
                                header: 'LIFECYCLE',
                                dynamic: true,
                                containerId: 'datasetRelationshipsSystemFilterLifecycleOptions'
                            }
                        ]
                    },
                    datasetLineage: {
                        categories: [
                            {
                                header: 'TYPE',
                                dynamic: true,
                                containerId: 'datasetRelationshipsFilterTypeOptions'
                            },
                            {
                                header: 'LIFECYCLE',
                                dynamic: true,
                                containerId: 'datasetRelationshipsFilterLifecycleOptions'
                            }
                        ]
                    }
                }
            });
        }
        
        // Fallback to basic HTML if shared generator not available
        return `
            <div class="map-section" id="datasetRelationshipsMapSection" style="grid-column:1/-1; margin-bottom: 1.5rem;">
                <div class="map-section-header">
                    <div class="map-section-title">MAP</div>
                    <button type="button" class="map-collapse-btn" id="datasetRelationshipsMapCollapseBtn" title="Collapse/Expand">
                        <i class="fas fa-minus"></i>
                    </button>
                </div>
                <div class="interface-map-toolbar">
                    <div class="map-control-group">
                        <label>Map type:</label>
                        <select id="datasetRelationshipsMapTypeSelect" class="map-select">
                            <option value="dataset-lineage" selected>Dataset Lineage</option>
                            <option value="system-lineage">System Lineage</option>
                        </select>
                    </div>
                    <div class="map-control-group">
                        <label>Layout:</label>
                        <div class="map-layout-controls">
                            ${typeof window.SharedMapLayoutRichControlsHtml === 'function' ? window.SharedMapLayoutRichControlsHtml('datasetRelationshipsMap') : ''}
                        </div>
                    </div>
                    <div class="map-control-group map-hops-group" id="datasetRelationshipsMapHopsGroup">
                        <label>Hops:</label>
                        <input type="number" id="datasetRelationshipsMapHopsCount" class="map-hops-input" min="1" max="99" value="15" title="Upstream/downstream lineage depth (1-99, recommend 15)">
                    </div>
                </div>
                <div class="interface-map-body">
                    <div class="interface-map-canvas" id="datasetRelationshipsMapCanvas">
                        <div class="interface-map-loading" data-map-loading style="display:none;">
                            <i class="fas fa-spinner fa-spin"></i>
                            <span>Building lineage map...</span>
                        </div>
                    </div>
                </div>
            </div>
        `;
    }
    function getDatasetRelationshipsMapRoot() {
        const roots = document.querySelectorAll('#datasetRelationshipsMapSection');
        return roots.length ? roots[roots.length - 1] : document;
    }

    // Update dataset map filters
    function updateDatasetMapFilters() {
        const root = getDatasetRelationshipsMapRoot();
        const filterSystemInterfaces = root.querySelector('#filterdatasetRelationshipsMapsystemInterfaces');
        const filterDataAttributeLinks = root.querySelector('#filterdatasetRelationshipsMapdataAttributeLinks');
        
        if (window.DatasetRelationshipsMap) {
            if (filterSystemInterfaces) {
                window.DatasetRelationshipsMap.setFilter('systemInterfaces', filterSystemInterfaces.checked);
            }
            if (filterDataAttributeLinks) {
                window.DatasetRelationshipsMap.setFilter('dataAttributeLinks', filterDataAttributeLinks.checked);
            }
        }
    }

    // Update filter options in UI (for dataset lineage)
    function updateFilterOptions(options) {
        const root = getDatasetRelationshipsMapRoot();
        const typeOptions = root.querySelector('#datasetRelationshipsFilterTypeOptions');
        const lifecycleOptions = root.querySelector('#datasetRelationshipsFilterLifecycleOptions');

        if (typeOptions && options.types) {
            typeOptions.innerHTML = options.types.map(type => `
                <div class="map-filter-option">
                    <input type="checkbox" id="filterType-${type}" value="${type}" checked>
                    <label for="filterType-${type}">${type}</label>
                </div>
            `).join('');
            
            // Add event listeners for type checkboxes
            typeOptions.querySelectorAll('input[type="checkbox"]').forEach(cb => {
                cb.addEventListener('change', applyDynamicFilters);
            });
        }

        if (lifecycleOptions && options.lifecycles) {
            lifecycleOptions.innerHTML = options.lifecycles.map(lifecycle => `
                <div class="map-filter-option">
                    <input type="checkbox" id="filterLifecycle-${lifecycle}" value="${lifecycle}" checked>
                    <label for="filterLifecycle-${lifecycle}">${lifecycle}</label>
                </div>
            `).join('');
            
            // Add event listeners for lifecycle checkboxes
            lifecycleOptions.querySelectorAll('input[type="checkbox"]').forEach(cb => {
                cb.addEventListener('change', applyDynamicFilters);
            });
        }
    }
    
    // Update filter options in UI (for system lineage)
    function updateSystemFilterOptions(options) {
        const root = getDatasetRelationshipsMapRoot();
        const classificationOptions = root.querySelector('#datasetRelationshipsSystemFilterClassificationOptions');
        const typeOptions = root.querySelector('#datasetRelationshipsSystemFilterTypeOptions');
        const lifecycleOptions = root.querySelector('#datasetRelationshipsSystemFilterLifecycleOptions');

        if (classificationOptions && options.classifications) {
            classificationOptions.innerHTML = options.classifications.map(cls => `
                <div class="map-filter-option">
                    <input type="checkbox" id="filterClassification-${cls}" value="${cls}" checked>
                    <label for="filterClassification-${cls}">${cls}</label>
                </div>
            `).join('');
            
            // Add event listeners for classification checkboxes
            classificationOptions.querySelectorAll('input[type="checkbox"]').forEach(cb => {
                cb.addEventListener('change', applySystemDynamicFilters);
            });
        }

        if (typeOptions && options.types) {
            typeOptions.innerHTML = options.types.map(type => `
                <div class="map-filter-option">
                    <input type="checkbox" id="filterSystemType-${type}" value="${type}" checked>
                    <label for="filterSystemType-${type}">${type}</label>
                </div>
            `).join('');
            
            // Add event listeners for type checkboxes
            typeOptions.querySelectorAll('input[type="checkbox"]').forEach(cb => {
                cb.addEventListener('change', applySystemDynamicFilters);
            });
        }

        if (lifecycleOptions && options.lifecycles) {
            lifecycleOptions.innerHTML = options.lifecycles.map(lifecycle => `
                <div class="map-filter-option">
                    <input type="checkbox" id="filterSystemLifecycle-${lifecycle}" value="${lifecycle}" checked>
                    <label for="filterSystemLifecycle-${lifecycle}">${lifecycle}</label>
                </div>
            `).join('');
            
            // Add event listeners for lifecycle checkboxes
            lifecycleOptions.querySelectorAll('input[type="checkbox"]').forEach(cb => {
                cb.addEventListener('change', applySystemDynamicFilters);
            });
        }
    }
    
    // Apply system lineage dynamic filters
    function applySystemDynamicFilters() {
        const root = getDatasetRelationshipsMapRoot();
        const classificationCheckboxes = root.querySelectorAll('#datasetRelationshipsSystemFilterClassificationOptions input[type="checkbox"]');
        const typeCheckboxes = root.querySelectorAll('#datasetRelationshipsSystemFilterTypeOptions input[type="checkbox"]');
        const lifecycleCheckboxes = root.querySelectorAll('#datasetRelationshipsSystemFilterLifecycleOptions input[type="checkbox"]');
        
        const selectedClassifications = Array.from(classificationCheckboxes).filter(cb => cb.checked).map(cb => cb.value);
        const selectedTypes = Array.from(typeCheckboxes).filter(cb => cb.checked).map(cb => cb.value);
        const selectedLifecycles = Array.from(lifecycleCheckboxes).filter(cb => cb.checked).map(cb => cb.value);
        
        if (window.DatasetRelationshipsMap) {
            window.DatasetRelationshipsMap.setNodeFilters({
                classifications: selectedClassifications,
                types: selectedTypes,
                lifecycles: selectedLifecycles
            });
        }
    }
    
    // Apply dynamic filters (type, lifecycle)
    function applyDynamicFilters() {
        const root = getDatasetRelationshipsMapRoot();
        const typeOptions = root.querySelector('#datasetRelationshipsFilterTypeOptions');
        const lifecycleOptions = root.querySelector('#datasetRelationshipsFilterLifecycleOptions');
        
        const selectedTypes = [];
        const selectedLifecycles = [];
        
        if (typeOptions) {
            typeOptions.querySelectorAll('input[type="checkbox"]:checked').forEach(cb => {
                selectedTypes.push(cb.value);
            });
        }
        
        if (lifecycleOptions) {
            lifecycleOptions.querySelectorAll('input[type="checkbox"]:checked').forEach(cb => {
                selectedLifecycles.push(cb.value);
            });
        }
        
        if (window.DatasetRelationshipsMap) {
            window.DatasetRelationshipsMap.setDatasetNodeFilters({
                types: selectedTypes,
                lifecycles: selectedLifecycles
            });
        }
    }
    function initDatasetRelationshipsMapToolbar() {
        var mapId = 'datasetRelationshipsMap';
        var mapRoot = document.getElementById(mapId + 'Section') || document;
        if (mapRoot.dataset && mapRoot.dataset.datasetRelationshipsMapToolbarWired === '1') {
            return;
        }
        if (mapRoot.dataset) {
            mapRoot.dataset.datasetRelationshipsMapToolbarWired = '1';
        }
        var byId = function (id) {
            return mapRoot.querySelector('#' + id) || document.getElementById(id);
        };

        // Map type selector
        const mapTypeSelect = byId('datasetRelationshipsMapTypeSelect');
        const directionBtn = byId('datasetRelationshipsMapDirection');
        const collapseBtn = byId('datasetRelationshipsMapCollapseBtn');
        const mapSection = byId('datasetRelationshipsMapSection');
        const overlayBtn = byId('datasetRelationshipsMapOverlayBtn');
        const overlayMenuSystem = byId('datasetRelationshipsMapOverlayMenuSystem');
        const overlayMenuDataset = byId('datasetRelationshipsMapOverlayMenuDataset');
        const filterBtn = byId('datasetRelationshipsMapFilterBtn');
        const systemFilterMenu = byId('datasetRelationshipsMapFilterMenuSystem');
        const datasetFilterMenu = byId('datasetRelationshipsMapFilterMenuDataset');
        const clearOverlaysBtnSystem = byId('datasetRelationshipsMapClearOverlaysBtnSystem');
        const clearOverlaysBtnDataset = byId('datasetRelationshipsMapClearOverlaysBtnDataset');
        
        console.log('[DATASET-MAP] Elements found:', {
            overlayBtn: !!overlayBtn,
            overlayMenuSystem: !!overlayMenuSystem,
            overlayMenuDataset: !!overlayMenuDataset,
            filterBtn: !!filterBtn,
            systemFilterMenu: !!systemFilterMenu,
            datasetFilterMenu: !!datasetFilterMenu
        });
        
        // Track current map type for menu switching
        let currentMapType = 'dataset-lineage';
        
        // Function to get active overlay menu based on map type
        const getActiveOverlayMenu = () => {
            return currentMapType === 'dataset-lineage' ? overlayMenuDataset : overlayMenuSystem;
        };
        
        // Function to get active filter menu based on map type
        const getActiveFilterMenu = () => {
            return currentMapType === 'dataset-lineage' ? datasetFilterMenu : systemFilterMenu;
        };
        
        // Function to switch menus based on map type
        const switchMenus = (mapType) => {
            currentMapType = mapType;
            
            // Reset overlay button text
            if (overlayBtn) {
                overlayBtn.querySelector('span').textContent = 'None';
            }
            // Clear active overlays
            mapRoot.querySelectorAll('.overlay-menu-item').forEach(i => i.classList.remove('active'));
            // Hide any open menus
            if (overlayMenuSystem) overlayMenuSystem.classList.remove('open');
            if (overlayMenuDataset) overlayMenuDataset.classList.remove('open');
            if (systemFilterMenu) systemFilterMenu.classList.remove('open');
            if (datasetFilterMenu) datasetFilterMenu.classList.remove('open');
            
            // Clear overlay on map
            if (window.DatasetRelationshipsMap) {
                window.DatasetRelationshipsMap.setOverlay('none');
            }
            
            // Update filter label based on map type
            updateDatasetFilterLabel(mapType);
        };
        
        // Update filter label for specific map type
        const updateDatasetFilterLabel = (mapType) => {
            const filterBtnText = byId('datasetRelationshipsMapFilterBtnText');
            if (!filterBtnText) return;
            
            const activeFilterMenu = mapType === 'dataset-lineage' ? datasetFilterMenu : systemFilterMenu;
            if (!activeFilterMenu) return;
            
            const checkboxes = activeFilterMenu.querySelectorAll('input[type="checkbox"]');
            const checkedCount = Array.from(checkboxes).filter(c => c.checked && c.closest('.map-filter-option')?.style.display !== 'none').length;
            const totalCount = Array.from(checkboxes).filter(c => c.closest('.map-filter-option')?.style.display !== 'none').length;
            
            if (totalCount === 0) {
                filterBtnText.textContent = 'No filters';
            } else if (checkedCount === totalCount) {
                filterBtnText.textContent = `All selected (${totalCount})`;
            } else {
                filterBtnText.textContent = `${checkedCount} of ${totalCount}`;
            }
        };
        
        if (mapTypeSelect) {
            mapTypeSelect.addEventListener('change', (e) => {
                const mapType = e.target.value;
                switchMenus(mapType);
                if (window.DatasetRelationshipsMap) {
                    window.DatasetRelationshipsMap.setMapType(mapType);
                }
            });
        }
        
        
        // Hops Count (1-99, default 15)
        const hopsInput = byId('datasetRelationshipsMapHopsCount');
        if (hopsInput) {
            hopsInput.addEventListener('change', (e) => {
                let val = parseInt(e.target.value, 10);
                if (isNaN(val) || val < 1) val = 1;
                if (val > 99) val = 99;
                e.target.value = val;
                if (window.DatasetRelationshipsMap && typeof window.DatasetRelationshipsMap.setHopsCount === 'function') {
                    window.DatasetRelationshipsMap.setHopsCount(val);
                }
            });
        }
        
        // Initialize shared dropdown menus for expand/collapse & direction buttons
        if (typeof window.SharedMapDropdowns === 'function') {
            window.SharedMapDropdowns({
                mapId: 'datasetRelationshipsMap',
                getNetwork: () => window.DatasetRelationshipsMap ? window.DatasetRelationshipsMap.cy : null,
                setLayout: (dir) => {
                    const ls = byId('datasetRelationshipsMapLayoutSelect');
                    if (ls) ls.value = dir;
                    if (window.DatasetRelationshipsMap) window.DatasetRelationshipsMap.setLayout(dir);
                },
                getCanvas: () => byId('datasetRelationshipsMapCanvas')
            });
        }
        
        // Collapse/Expand button
        if (collapseBtn && mapSection) {
            collapseBtn.addEventListener('click', () => {
                const toolbar = mapSection.querySelector('.interface-map-toolbar');
                const body = mapSection.querySelector('.interface-map-body');
                const icon = collapseBtn.querySelector('i');
                
                if (toolbar && body) {
                    const isCollapsed = toolbar.style.display === 'none';
                    toolbar.style.display = isCollapsed ? '' : 'none';
                    body.style.display = isCollapsed ? '' : 'none';
                    icon.className = isCollapsed ? 'fas fa-minus' : 'fas fa-plus';
                }
            });
        }
        
        // Overlay dropdown
        if (overlayBtn) {
            overlayBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                const activeOverlayMenu = getActiveOverlayMenu();
                if (activeOverlayMenu) {
                    activeOverlayMenu.classList.toggle('open');
                }
                // Close filter menus if open
                if (systemFilterMenu) systemFilterMenu.classList.remove('open');
                if (datasetFilterMenu) datasetFilterMenu.classList.remove('open');
            });
        }
        
        // Setup overlay menu item handlers for both menus
        const setupOverlayMenuHandlers = (menu) => {
            if (!menu) return;
            menu.querySelectorAll('.overlay-menu-item').forEach(item => {
                item.addEventListener('click', (e) => {
                    e.preventDefault();
                    e.stopPropagation();
                    const overlayType = e.currentTarget.getAttribute('data-overlay');
                    const wasActive = e.currentTarget.classList.contains('active');
                    menu.querySelectorAll('.overlay-menu-item').forEach(i => i.classList.remove('active'));
                    
                    if (!wasActive) {
                        e.currentTarget.classList.add('active');
                        if (overlayBtn) {
                            overlayBtn.querySelector('span').textContent = e.currentTarget.textContent.trim();
                        }
                        if (window.DatasetRelationshipsMap) {
                            window.DatasetRelationshipsMap.setOverlay(overlayType);
                        }
                    } else {
                        if (overlayBtn) {
                            overlayBtn.querySelector('span').textContent = 'None';
                        }
                        if (window.DatasetRelationshipsMap) {
                            window.DatasetRelationshipsMap.setOverlay('none');
                        }
                    }
                    menu.classList.remove('open');
                });
            });
        };
        
        setupOverlayMenuHandlers(overlayMenuSystem);
        setupOverlayMenuHandlers(overlayMenuDataset);
        
        // Clear overlays button handler
        const clearOverlaysHandler = () => {
            mapRoot.querySelectorAll('.overlay-menu-item').forEach(i => i.classList.remove('active'));
            if (overlayBtn) overlayBtn.querySelector('span').textContent = 'None';
            if (window.DatasetRelationshipsMap) {
                window.DatasetRelationshipsMap.setOverlay('none');
            }
            const activeOverlayMenu = getActiveOverlayMenu();
            if (activeOverlayMenu) activeOverlayMenu.classList.remove('open');
        };
        
        if (clearOverlaysBtnSystem) {
            clearOverlaysBtnSystem.addEventListener('click', clearOverlaysHandler);
        }
        if (clearOverlaysBtnDataset) {
            clearOverlaysBtnDataset.addEventListener('click', clearOverlaysHandler);
        }
        
        // Overlay grid button: overlay fields as columns dropdown
        const overlayGridBtn = byId('datasetRelationshipsMapOverlayGrid');
        const overlayColumnsMenu = byId('datasetRelationshipsMapOverlayColumnsMenu');
        const overlayColumnsOptions = byId('datasetRelationshipsMapOverlayColumnsOptions');
        if (overlayGridBtn && overlayColumnsMenu && overlayColumnsOptions) {
            function populateOverlayColumnsMenu() {
                overlayColumnsOptions.innerHTML = '';
                const overlayType = window.DatasetRelationshipsMap && window.DatasetRelationshipsMap.getState ? (window.DatasetRelationshipsMap.getState().overlay || '') : '';
                if (!overlayType || overlayType === 'none') {
                    overlayColumnsOptions.innerHTML = '<div class="map-overlay-columns-empty">Select an overlay first.</div>';
                    return;
                }
                const columns = window.OverlayColumns && window.OverlayColumns.getOverlayColumns ? window.OverlayColumns.getOverlayColumns(overlayType) : [];
                const selectedIds = window.DatasetRelationshipsMap && typeof window.DatasetRelationshipsMap.getOverlayColumns === 'function' ? window.DatasetRelationshipsMap.getOverlayColumns(overlayType) : [];
                if (!columns || columns.length === 0) {
                    overlayColumnsOptions.innerHTML = '<div class="map-overlay-columns-empty">No columns for this overlay.</div>';
                    return;
                }
                columns.forEach(col => {
                    const div = document.createElement('div');
                    div.className = 'map-filter-option';
                    const input = document.createElement('input');
                    input.type = 'checkbox';
                    input.id = 'overlayCol_dataset_' + overlayType + '_' + col.id;
                    input.checked = selectedIds.indexOf(col.id) !== -1;
                    input.dataset.columnId = col.id;
                    const label = document.createElement('label');
                    label.htmlFor = input.id;
                    label.textContent = col.label;
                    div.appendChild(input);
                    div.appendChild(label);
                    input.addEventListener('change', () => {
                        const checked = Array.from(overlayColumnsOptions.querySelectorAll('input:checked')).map(i => i.dataset.columnId);
                        if (window.DatasetRelationshipsMap && typeof window.DatasetRelationshipsMap.setOverlayColumns === 'function') {
                            window.DatasetRelationshipsMap.setOverlayColumns(overlayType, checked);
                        }
                    });
                    overlayColumnsOptions.appendChild(div);
                });
            }
            overlayGridBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                const filterMenu = byId('datasetRelationshipsMapFilterMenuSystem');
                const systemOverlayMenu = byId('datasetRelationshipsMapOverlayMenuSystem');
                const datasetOverlayMenu = byId('datasetRelationshipsMapOverlayMenuDataset');
                if (filterMenu) filterMenu.classList.remove('open');
                if (systemOverlayMenu) systemOverlayMenu.classList.remove('open');
                if (datasetOverlayMenu) datasetOverlayMenu.classList.remove('open');
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
        }
        
        // Filter dropdown toggle
        if (filterBtn) {
            filterBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                const activeFilterMenu = getActiveFilterMenu();
                if (activeFilterMenu) {
                    activeFilterMenu.classList.toggle('open');
                }
                // Close overlay menus if open
                if (overlayMenuSystem) overlayMenuSystem.classList.remove('open');
                if (overlayMenuDataset) overlayMenuDataset.classList.remove('open');
            });
        }
        
        // Filter checkboxes - using correct IDs from SharedMapHTML
        const filterSystemInterfaces = byId('filterdatasetRelationshipsMapsystemInterfaces');
        const filterDataAttributeLinks = byId('filterdatasetRelationshipsMapdataAttributeLinks');
        
        if (filterSystemInterfaces) {
            filterSystemInterfaces.addEventListener('change', () => {
                if (window.DatasetRelationshipsMap) {
                    window.DatasetRelationshipsMap.setFilter('systemInterfaces', filterSystemInterfaces.checked);
                }
                updateDatasetFilterLabel(currentMapType);
            });
        }
        if (filterDataAttributeLinks) {
            filterDataAttributeLinks.addEventListener('change', () => {
                if (window.DatasetRelationshipsMap) {
                    window.DatasetRelationshipsMap.setFilter('dataAttributeLinks', filterDataAttributeLinks.checked);
                }
                updateDatasetFilterLabel(currentMapType);
            });
        }
        
        // Close dropdowns when clicking outside
        document.addEventListener('click', (e) => {
            const activeFilterMenu = getActiveFilterMenu();
            const activeOverlayMenu = getActiveOverlayMenu();
            
            if (filterBtn && activeFilterMenu && !filterBtn.contains(e.target) && !activeFilterMenu.contains(e.target)) {
                activeFilterMenu.classList.remove('open');
            }
            if (overlayBtn && activeOverlayMenu && !overlayBtn.contains(e.target) && !activeOverlayMenu.contains(e.target)) {
                activeOverlayMenu.classList.remove('open');
            }
            if (overlayColumnsMenu && overlayGridBtn && !overlayColumnsMenu.contains(e.target) && !overlayGridBtn.contains(e.target)) {
                overlayColumnsMenu.classList.remove('open');
                overlayColumnsMenu.style.display = 'none';
                overlayGridBtn.classList.remove('active');
            }
        });
        
        // Toolbar buttons
        const zoomInBtn = byId('datasetRelationshipsMapZoomIn');
        const zoomOutBtn = byId('datasetRelationshipsMapZoomOut');
        const redrawBtn = byId('datasetRelationshipsMapRedraw');
        const resetBtn = byId('datasetRelationshipsMapReset');
        const exportBtn = byId('datasetRelationshipsMapExport');
        const fullscreenBtn = byId('datasetRelationshipsMapFullscreen');
        const legendBtn = byId('datasetRelationshipsMapLegend');
        const toggleLabelsBtn = byId('datasetRelationshipsMapToggleLabels');
        const navigatorBtn = byId('datasetRelationshipsMapNavigator');

        if (zoomInBtn) {
            zoomInBtn.addEventListener('click', () => {
                if (window.DatasetRelationshipsMap) window.DatasetRelationshipsMap.zoomIn();
            });
        }

        if (zoomOutBtn) {
            zoomOutBtn.addEventListener('click', () => {
                if (window.DatasetRelationshipsMap) window.DatasetRelationshipsMap.zoomOut();
            });
        }

        if (redrawBtn) {
            redrawBtn.addEventListener('click', () => {
                if (window.DatasetRelationshipsMap) window.DatasetRelationshipsMap.redrawMap();
            });
        }

        if (resetBtn) {
            resetBtn.addEventListener('click', () => {
                if (window.DatasetRelationshipsMap) window.DatasetRelationshipsMap.resetMap();
            });
        }

        if (exportBtn) {
            exportBtn.addEventListener('click', () => {
                if (window.DatasetRelationshipsMap) window.DatasetRelationshipsMap.exportAsPng();
            });
        }
        
        if (fullscreenBtn) {
            fullscreenBtn.addEventListener('click', () => {
                if (window.DatasetRelationshipsMap && window.DatasetRelationshipsMap.openFullscreen) {
                    window.DatasetRelationshipsMap.openFullscreen();
                }
            });
        }
        
        if (legendBtn) {
            if (typeof window.setupMapLegendDropdown === 'function') {
                window.setupMapLegendDropdown('datasetRelationshipsMapLegend', 'datasetRelationshipsMapLegendDropdown', function() {
                    return (window.DatasetRelationshipsMap && window.DatasetRelationshipsMap.getLegendHtml) ? window.DatasetRelationshipsMap.getLegendHtml() : '';
                });
            } else {
                legendBtn.addEventListener('click', (e) => {
                    e.stopPropagation();
                    const html = (window.DatasetRelationshipsMap && window.DatasetRelationshipsMap.getLegendHtml) ? window.DatasetRelationshipsMap.getLegendHtml() : '';
                    alert(html ? 'Legend loaded' : 'No legend available');
                });
            }
        }
        
        if (navigatorBtn) {
            navigatorBtn.addEventListener('click', () => {
                navigatorBtn.classList.toggle('active');
                if (window.DatasetRelationshipsMap && window.DatasetRelationshipsMap.toggleNavigator) {
                    window.DatasetRelationshipsMap.toggleNavigator();
                }
            });
        }
        
        if (toggleLabelsBtn) {
            let labelsVisible = true;
            toggleLabelsBtn.classList.add('active');
            toggleLabelsBtn.addEventListener('click', () => {
                labelsVisible = !labelsVisible;
                if (window.DatasetRelationshipsMap && window.DatasetRelationshipsMap.toggleInterfaceLabels) {
                    window.DatasetRelationshipsMap.toggleInterfaceLabels(labelsVisible);
                }
                toggleLabelsBtn.classList.toggle('active', labelsVisible);
            });
        }

        // Listen for filter options updates (dataset lineage)
        window.addEventListener('datasetMapFilterOptionsUpdated', (event) => {
            updateFilterOptions(event.detail);
            updateDatasetFilterLabel(currentMapType);
        });
        
        // Listen for filter options updates (system lineage)
        window.addEventListener('systemMapFilterOptionsUpdated', (event) => {
            updateSystemFilterOptions(event.detail);
            updateDatasetFilterLabel(currentMapType);
        });
    }

    var _datasetRelationshipsEngine = new window.MapEngine('dataset-lineage');

    Object.assign(_datasetRelationshipsEngine, {
        init: init,
        loadMapData: loadMapData,
        setMapType: setMapType,
        setLayout: setLayout,
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
        toggleNavigator: toggleNavigator,
        getLegendHtml: getLegendHtml,
        getState: function () { return DatasetMapState; },
        setOverlay: setOverlay,
        setOverlayColumns: setOverlayColumns,
        getOverlayColumns: getOverlayColumns,
        initToolbar: function () { initDatasetRelationshipsMapToolbar(); }
    });

    Object.defineProperty(_datasetRelationshipsEngine, 'cy', {
        get: function () { return DatasetMapState.network; },
        configurable: true
    });

    window.DatasetRelationshipsMap = _datasetRelationshipsEngine;

    window.getDatasetRelationshipsMapHtml = getDatasetRelationshipsMapHtml;

})();

