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
        if (DatasetMapState.network) {
            const graph = DatasetMapState.mapType === 'dataset-lineage' ? buildDatasetLineageGraph() : buildSystemLineageGraph();
            renderNetwork(graph);
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
        return DatasetMapState.overlayColumnsByType[overlayType] || (window.OverlayColumns ? window.OverlayColumns.getDefaultOverlayColumnIds(overlayType) : ['name']);
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
    
    // Fetch overlay data for a dataset
    async function fetchOverlayDataForDataset(datasetId, overlayType) {
        try {
            switch (overlayType) {
                case 'description':
                    // Check cache first
                    const cachedInfo = DatasetMapState.linkedDatasets?.get(String(datasetId));
                    if (cachedInfo?.dataset) {
                        const ds = cachedInfo.dataset;
                        const desc = ds.description || ds.Definition || ds.definition;
                        if (desc) {
                            return [{ name: 'Description', value: desc }];
                        }
                    }
                    // Fetch from API
                    try {
                        const resp = await fetch(`/api/dataset/${datasetId}`, { credentials: 'include' });
                        if (resp.ok) {
                            const data = await resp.json();
                            const dataset = data?.data || data;
                            const desc = dataset?.description || dataset?.Definition || dataset?.definition;
                            if (desc) {
                                return [{ name: 'Description', value: desc }];
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
                    } catch (e) { /* ignore */ }
                    // 2) Glossary terms from attributes
                    try {
                        const resp = await fetch(`/api/attribute/${datasetId}`, { credentials: 'include' });
                        if (resp.ok) {
                            const data = await resp.json();
                            const attrs = Array.isArray(data?.data) ? data.data : (Array.isArray(data) ? data : []);
                            attrs.forEach(attr => {
                                const gName = adapter.extractGlossaryNameFromAttr(attr);
                                if (gName && !seen.has(gName)) {
                                    seen.add(gName);
                                    const gId = attr.glossaryId || attr.glossary_id || attr.Glossary_ID;
                                    glossaryTerms.push({ name: gName, glossary: gName, id: gId, glossaryId: gId, source: 'attribute' });
                                }
                            });
                        }
                    } catch (e) { /* ignore */ }
                    embLog('[DATASET-MAP] Glossary overlay for dataset', datasetId, ':', glossaryTerms.length, 'terms', glossaryTerms.map(t => t.name));
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
                        const sysGlossaryDatasets = DatasetMapState.linkedDatasets
                            ? Array.from(DatasetMapState.linkedDatasets.entries()).filter(([, info]) => String(info.systemId) === String(systemId)).map(([did]) => did)
                            : [];
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
                            } catch (e) { /* skip */ }
                            // 2) Glossary terms from attributes
                            try {
                                const attrResp = await fetch(`/api/attribute/${did}`, { credentials: 'include' });
                                if (attrResp.ok) {
                                    const attrData = await attrResp.json();
                                    const attrs = Array.isArray(attrData?.data) ? attrData.data : (Array.isArray(attrData) ? attrData : []);
                                    attrs.forEach(attr => {
                                        const gName = adapter.extractGlossaryNameFromAttr(attr);
                                        if (gName && !seenGlossary.has(gName)) {
                                            seenGlossary.add(gName);
                                            const gId = attr.glossaryId || attr.glossary_id || attr.Glossary_ID;
                                            glossaryTerms.push({ name: gName, glossary: gName, id: gId, glossaryId: gId, source: 'attribute' });
                                        }
                                    });
                                }
                            } catch (e) { /* skip */ }
                        }
                    } catch (e) { /* ignore */ }
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
        applyNodeFiltersToNetwork();
    }

    function setDatasetNodeFilters(datasetFilters) {
        DatasetMapState.datasetNodeFilters = {
            types: datasetFilters.types || [],
            lifecycles: datasetFilters.lifecycles || []
        };
        adapter.applyDatasetNodeFilters({
            linkedDatasets: DatasetMapState.linkedDatasets,
            updateOverlayPositions: updateOverlayPositions
        });
    }

    function applyNodeFiltersToNetwork() {
        adapter.applySystemNodeFilters({
            updateOverlayPositions: updateOverlayPositions
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
    function getOverlayTitle(overlayType) {
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
                return item.legalEntityName || item.longName || item.longname || item.name || item.Name || '';
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
        switch (overlayType) {
            case 'glossary':
                switch (fieldId) {
                    case 'name': return v(item.glossary || item.name || item.primaryName);
                    case 'source': return item.source === 'dataset' ? 'Dataset Glossary'
                                        : item.source === 'attribute' ? 'Attribute Glossary' : '';
                    case 'aliasNames': return v(item.aliasNames || item.aliases || item.alias);
                    case 'parentName': return v(item.parentName || item.parent?.name);
                    case 'lifecycle': return v(item.lifecycleName || item.lifecycle);
                    case 'securityClassification': return v(item.securityClassification || item.classification);
                    default: return v(item[fieldId]);
                }
            case 'description':
                return fieldId === 'value' ? v(item.value || item.description) : v(item[fieldId]);
            case 'datasets':
                switch (fieldId) {
                    case 'name': return v(item.primaryName || item.name || item.shortName);
                    case 'refNumber': return v(item.refNumber || item.ref);
                    case 'type': return v(item.typeName || item.type);
                    case 'lifecycle': return v(item.lifecycleName || item.lifecycle);
                    default: return v(item[fieldId]);
                }
            case 'attributes':
                switch (fieldId) {
                    case 'name': return v(item.name || item['Name attribute'] || item.attributeName || item.primaryName);
                    case 'type': return v(item.typeName || item.type);
                    case 'glossary': return v(item.glossaryName || item.glossary);
                    case 'refNumber': return v(item.refNumber || item.ref);
                    default: return v(item[fieldId]);
                }
            case 'linking-attributes':
                switch (fieldId) {
                    case 'name': return v(item.name || item['Name attribute'] || item.attributeName || item.primaryName);
                    case 'type': return v(item.typeName || item.type);
                    case 'glossary': return v(item.glossaryName || item.glossary || item['Glossary Name attribute']);
                    case 'direction': return v(item.direction);
                    case 'relatedDataset': return v(item.relatedDataset);
                    case 'relatedAttribute': return v(item.relatedAttribute);
                    default: return v(item[fieldId]);
                }
            case 'stakeholders':
                switch (fieldId) {
                    case 'name': return v(item.personName || item.name);
                    case 'role': return v(item.roleName || item.role);
                    default: return v(item[fieldId]);
                }
            case 'processes':
            case 'projects':
            case 'policies':
            case 'business-area':
            case 'products':
            case 'legal-entities':
            case 'data-quality':
            case 'data-privacy':
            case 'geography':
            case 'systems':
                switch (fieldId) {
                    case 'name': return v(item.name || item.primaryName || item.PrimaryName || item.glossaryName || item.ruleName);
                    case 'refNumber': return v(item.refNumber || item.ref);
                    case 'type': return v(item.typeName || item.type);
                    case 'lifecycle': return v(item.lifecycleName || item.lifecycle);
                    case 'status': return v(item.statusName || item.status);
                    case 'ruleName': return v(item.ruleName);
                    case 'rating': return v(item.rating || item.qualityRating);
                    case 'classification': return v(item.classification || item.privacyClassification);
                    case 'region': return v(item.region);
                    case 'country': return v(item.country);
                    default: return v(item[fieldId]);
                }
            default:
                return v(item[fieldId] || item.name || item.primaryName);
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
        if (overlayType !== 'stakeholders' && window.OverlayColumns) {
            var allCols = window.OverlayColumns.getOverlayColumns(overlayType);
            var selectedIds = DatasetMapState.overlayColumnsByType[overlayType] || window.OverlayColumns.getDefaultOverlayColumnIds(overlayType);
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

        overlayContainer.querySelectorAll('.map-node-overlay-item').forEach(function(row) {
            var panel = row.closest('.map-node-overlay-panel');
            var isSource = panel?.getAttribute('data-node-id') === sourceNodeId;
            var shouldHighlight = false;
            if (itemId) {
                var rowItemId = row.dataset.itemId ? String(row.dataset.itemId) : null;
                if (rowItemId && rowItemId === String(itemId)) shouldHighlight = true;
            }
            if (!shouldHighlight && row.dataset.overlayValue === itemText) shouldHighlight = true;
            if (shouldHighlight) {
                if (isSource) row.classList.add('highlighted-source');
                else row.classList.add('highlighted-related');
            }
        });
        applyOverlayRelatedNodeAndEdgeHighlights(overlayContainer, sourceNodeId);
        DatasetMapState._highlightedOverlayKey = key;
    }

    function highlightAttributeRelationships(overlayContainer, clickedAttributeId, sourceNodeId) {
        var relationships = DatasetMapState.datasetRelationships || [];
        var clickedAttrIdStr = String(clickedAttributeId);
        var relatedAttributeIds = findAllRelatedAttributes(clickedAttrIdStr, relationships);

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

    function findAllRelatedAttributes(clickedAttributeId, relationships) {
        const related = new Set();
        const visited = new Set();
        const queue = [String(clickedAttributeId)];
        const normalizedRels = (relationships || [])
            .map(rel => ({
                source: String(rel.sourceAttributeId || ''),
                target: String(rel.targetAttributeId || '')
            }))
            .filter(rel => rel.source && rel.target && rel.source !== 'undefined' && rel.target !== 'undefined');
        while (queue.length > 0) {
            const currentId = String(queue.shift());
            if (visited.has(currentId)) continue;
            visited.add(currentId);
            normalizedRels.forEach(rel => {
                if (rel.source === currentId && rel.target && !visited.has(rel.target)) {
                    related.add(rel.target);
                    queue.push(rel.target);
                }
            });
            normalizedRels.forEach(rel => {
                if (rel.target === currentId && rel.source && !visited.has(rel.source)) {
                    related.add(rel.source);
                    queue.push(rel.source);
                    normalizedRels.forEach(rel2 => {
                        if (rel2.source === rel.source && rel2.target && rel2.target !== currentId && !visited.has(rel2.target)) {
                            related.add(rel2.target);
                            queue.push(rel2.target);
                        }
                    });
                }
            });
        }
        return related;
    }

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
        getOverlayColumns: getOverlayColumns
    });

    Object.defineProperty(_datasetRelationshipsEngine, 'cy', {
        get: function () { return DatasetMapState.network; },
        configurable: true
    });

    window.DatasetRelationshipsMap = _datasetRelationshipsEngine;

})();

