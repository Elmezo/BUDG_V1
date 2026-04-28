/**
 * Glossary Data Map Component
 * Implements Data Map for Glossary facet in Data Tab > Data Map
 * 
 * Map Types:
 * - System Lineage: Shows systems linked with the opened glossary (orange) + directly linked systems
 * - Dataset Lineage: Shows datasets linked with the opened glossary (orange) + directly linked datasets
 * - Multi Node Lineage: Shows entities connected across business
 * 
 * Cases:
 * - Case 1: Glossary directly associated with dataset - show system in orange + directly linked systems
 * - Case 2: Glossary associated with attribute - show attribute's dataset's system in orange + directly linked systems
 * 
 * Overlays:
 * - Datasets: Only show associated datasets in systems (not all datasets in the system)
 * - Attributes: Case 1 - don't show, Case 2 - only show directly associated attributes
 * - Linking Attributes: Case 1 - don't show, Case 2 - only show directly associated attributes
 * - Black systems: Show attributes directly associated with the same glossary G
 * 
 * Dependencies:
 * - shared/map/map-styles.js, map-icons.js (InterfaceMapStyles / InterfaceMapIcons)
 * - shared/map/glossary-data-facet-graph.js (createGlossaryDataFacetGraphPipeline)
 * - shared/map/system-lineage-network-interactions.js (createGlossaryDataMapNetworkInteractions)
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

    var GLOSSARY_DATA_MAP_COLOR_FALLBACK = {
        currentSystem: '#f97316',
        currentSystemBorder: '#ea580c',
        otherSystem: '#1f2937',
        otherSystemBorder: '#374151',
        currentDataset: '#f97316',
        currentDatasetBorder: '#ea580c',
        otherDataset: '#1f2937',
        otherDatasetBorder: '#374151',
        relationshipLine: '#64748b',
        interfaceLine: '#94a3b8',
        attributeLine: '#64748b',
        upstreamHighlight: '#ef4444',
        downstreamHighlight: '#22c55e',
        selectedBorder: '#248567',
        nodeLabel: '#1f2937',
        edgeLabelColor: '#6b7280'
    };
    function getMapColors() {
        return window.MapRenderUtils.getMapColors(GLOSSARY_DATA_MAP_COLOR_FALLBACK);
    }

    // Map State Management
    const GlossaryDataMapState = {
        initialized: false,
        dagreRegistered: false,
        network: null,
        canvas: null,
        loadingEl: null,
        glossaryId: null,
        glossaryData: null,
        layout: 'top-to-bottom',
        mapType: 'system-lineage', // Default to system-lineage (matches HTML default)
        overlay: 'none',
        overlayColumnsByType: {},
        hopsCount: 15,
        filters: {},
        nodeFilters: {
            classifications: [],
            types: [],
            lifecycles: []
        },
        datasetNodeFilters: {
            types: [],
            lifecycles: []
        },
        hiddenNodes: new Set(),
        hiddenUpstreamNodes: new Set(),
        hiddenDownstreamNodes: new Set(),
        focusedNode: null,
        // Case tracking
        caseType: null, // 'case1' (dataset) or 'case2' (attribute)
        directlyLinkedSystems: new Set(), // Systems directly linked to glossary (orange)
        directlyLinkedDatasets: new Set(), // Datasets directly linked to glossary
        directlyLinkedAttributes: new Set(), // Attributes directly linked to glossary
        // Data storage
        systemsData: new Map(), // systemId -> system data
        datasetsData: new Map(), // datasetId -> dataset data
        attributesData: new Map(), // attributeId -> attribute data
        interfacesData: [], // Interfaces for systems
        dataFlowData: [], // Data flow outside interfaces
        datasetRelationships: [], // Relationships between datasets via attributes
        connectedSystems: new Map(), // systemId -> { systemData }
        inaccessibleSystems: new Set(), // Systems that are locked/inaccessible
        missingSystems: new Set(), // Systems that no longer exist (404) - e.g. deleted but still referenced
        // Overlay data
        overlayDatasets: new Map(), // systemId -> Set of datasetIds (only associated ones)
        overlayAttributes: new Map(), // systemId -> Set of attributeIds
        linkingAttributes: new Map() // systemId -> Set of attributeIds (for linking overlay)
    };

    const adapter = window.createMapAdapter(GlossaryDataMapState, 'glossaryDataMap', { zoomRecenter: false });

    var graphPipeline = null;

    async function loadMapData(glossaryId) {
        return graphPipeline.loadMapData(glossaryId);
    }

    function buildGraph() {
        return graphPipeline.buildGraph();
    }

    var networkIx = null;

    // Initialize the map
    function init(glossaryId, containerSelector) {
        embLog('[GLOSSARY-DATA-MAP] Initializing map for glossary:', glossaryId, 'container:', containerSelector);
        GlossaryDataMapState.glossaryId = glossaryId;
        GlossaryDataMapState.canvas = document.querySelector(containerSelector || '#glossaryDataMapCanvas');
        GlossaryDataMapState.loadingEl = document.querySelector('[data-glossary-data-map-loading]');

        if (!GlossaryDataMapState.canvas) {
            console.error('[GLOSSARY-DATA-MAP] Canvas element not found');
            return;
        }

        // Check if Cytoscape is available
        if (typeof cytoscape === 'undefined') {
            console.error('[GLOSSARY-DATA-MAP] Cytoscape library not available');
            GlossaryDataMapState.canvas.innerHTML = window.MapRenderUtils.htmlCytoscapeUnavailable();
            return;
        }

        // Register dagre layout if available - same as system map
        if (!GlossaryDataMapState.dagreRegistered) {
            try {
                const dagre = window.cytoscapeDagre || window['cytoscape-dagre'];
                if (dagre) {
                    cytoscape.use(dagre);
                    GlossaryDataMapState.dagreRegistered = true;
                }
                // Silently fall back to breadthfirst if dagre not available
            } catch (e) {
                // Silently handle error - will use breadthfirst fallback
            }
        }

        GlossaryDataMapState.initialized = true;
        
        // Load map data
        loadMapData(glossaryId);
    }


    // Render network using Cytoscape
    function renderNetwork(graph) {
        if (!GlossaryDataMapState.canvas) {
            console.error('[GLOSSARY-DATA-MAP] Canvas not available');
            return;
        }

        if (graphPipeline && graphPipeline.invalidateGraphCache) {
            graphPipeline.invalidateGraphCache();
        }

        // Clear canvas
        GlossaryDataMapState.canvas.innerHTML = '';

        if (!graph || !graph.nodes || graph.nodes.length === 0) {
            showPlaceholder('No data to display.');
            return;
        }

        // Convert graph format to Cytoscape format
        const elements = [];
        const colors = getMapColors();
        
        // Add nodes
        graph.nodes.forEach(node => {
            const isCurrent = node.isCurrent || false;
            const isSystem = node.group === 'system';
            const nodeColor = isCurrent 
                ? (isSystem ? colors.currentSystem : colors.currentDataset)
                : (isSystem ? colors.otherSystem : colors.otherDataset);
            const borderColor = isCurrent
                ? (isSystem ? colors.currentSystemBorder : colors.currentDatasetBorder)
                : (isSystem ? colors.otherSystemBorder : colors.otherDatasetBorder);
            
            elements.push({
                data: {
                    id: node.id,
                    label: (node.isLocked ? '\u{1F512} ' : '') + (node.label || ''),
                    nodeColor: nodeColor,
                    borderColor: borderColor,
                    isCurrent: isCurrent,
                    isSystem: isSystem,
                    isDataset: node.group === 'dataset',
                    isLocked: node.isLocked || false,
                    // Spread meta properties directly into data for easier access
                    ...(node.meta || {}),
                    // Also keep meta as nested object for compatibility
                    meta: node.meta || {}
                }
            });
        });
        
        // Collect current node IDs for edge reversal (so dagre places current node leftmost/topmost)
        const currentNodeIds = new Set(graph.nodes.filter(n => n.isCurrent).map(n => n.id));
        
        // Add edges
        graph.edges.forEach(edge => {
            const targetIsCurrent = currentNodeIds.has(edge.to);
            const sourceIsCurrent = currentNodeIds.has(edge.from);
            const reverseEdge = targetIsCurrent && !sourceIsCurrent;
            const source = reverseEdge ? edge.to : edge.from;
            const target = reverseEdge ? edge.from : edge.to;
            elements.push({
                data: {
                    id: edge.id,
                    source: source,
                    target: target,
                    label: edge.label || '',
                    lineColor: colors.relationshipLine,
                    lineStyle: edge.lineStyle || 'solid',
                    lineType: edge.lineType || 'relationship',
                    reversed: !!reverseEdge
                },
                classes: reverseEdge ? 'reversed-edge' : ''
            });
        });

        // Find root nodes from graph data before creating network
        const rootNodeIds = findRootNodesFromGraph(graph);
        
        // Build layout configuration with root nodes
        const layoutConfig = buildCytoscapeLayout(rootNodeIds);
        
        // Create Cytoscape instance
        GlossaryDataMapState.network = cytoscape({
            container: GlossaryDataMapState.canvas,
            elements: elements,
            style: getCytoscapeStyle(),
            layout: layoutConfig,
            minZoom: 0.3,
            maxZoom: 3
        });

        // Setup event listeners
        if (networkIx) networkIx.setupEventListeners();
        
        // Setup overlay if enabled
        if (GlossaryDataMapState.overlay !== 'none') {
            loadOverlayData(GlossaryDataMapState.overlay);
        }
    }

    // Get Cytoscape styling - uses external styles module if available
    function getCytoscapeStyle() {
        // Try to use external styles module
        if (window.InterfaceMapStyles && typeof window.InterfaceMapStyles.getCytoscapeStyles === 'function') {
            const styles = window.InterfaceMapStyles.getCytoscapeStyles('glossaryDataMap');
            // Override background-image to only apply when the data field exists
            return styles.map(style => {
                if (style.selector === 'node' && style.style && style.style['background-image']) {
                    return {
                        ...style,
                        style: {
                            ...style.style,
                            'background-image': undefined
                        }
                    };
                }
                return style;
            }).concat([
                {
                    selector: 'node[backgroundImage]',
                    style: {
                        'background-image': 'data(backgroundImage)',
                        'background-fit': 'contain',
                        'background-width': '50%',
                        'background-height': '50%',
                        'background-position-y': '30%'
                    }
                }
            ]);
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
                    'height': nodeSizes.system.height
                }
            },
            {
                selector: 'node[isDataset = true]',
                style: {
                    'width': nodeSizes.dataset.width,
                    'height': nodeSizes.dataset.height
                }
            },
            {
                selector: 'edge',
                style: {
                    'width': 2,
                    'line-color': 'data(lineColor)',
                    'target-arrow-color': 'data(lineColor)',
                    'target-arrow-shape': 'triangle',
                    'curve-style': (window._sharedDropdownApis && window._sharedDropdownApis['glossaryDataMap'] && typeof window._sharedDropdownApis['glossaryDataMap'].getCurveStyle === 'function') ? window._sharedDropdownApis['glossaryDataMap'].getCurveStyle() : 'bezier',
                    'label': 'data(label)',
                    'text-rotation': 'autorotate',
                    'text-margin-y': -10,
                    'font-size': 10,
                    'color': colors.edgeLabelColor || '#6b7280'
                }
            },
            {
                selector: '.reversed-edge',
                style: {
                    'target-arrow-shape': 'none',
                    'source-arrow-shape': 'triangle',
                    'source-arrow-color': colors.attributeLine || colors.interfaceLine || '#64748b'
                }
            },
            {
                selector: '.reversed-edge[lineColor]',
                style: {
                    'source-arrow-color': 'data(lineColor)'
                }
            },
            {
                selector: 'edge[lineStyle = "dashed"]',
                style: {
                    'line-style': 'dashed',
                    'line-dash-pattern': [5, 5]
                }
            },
            {
                selector: 'node[isLocked = true]',
                style: {
                    'background-color': '#6b7280',
                    'border-color': '#4b5563',
                    'opacity': 0.6
                }
            },
            {
                selector: '.highlighted-upstream',
                style: {
                    'line-color': colors.upstreamHighlight || '#ef4444',
                    'target-arrow-color': colors.upstreamHighlight || '#ef4444',
                    'width': 3,
                    'z-index': 999
                }
            },
            {
                selector: '.highlighted-downstream',
                style: {
                    'line-color': colors.downstreamHighlight || '#22c55e',
                    'target-arrow-color': colors.downstreamHighlight || '#22c55e',
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
                    'border-color': colors.downstreamHighlight || '#22c55e',
                    'border-width': 4
                }
            }
        ];
    }

    // Build Cytoscape layout configuration - same as system map
    function buildCytoscapeLayout(rootNodeIds = null) {
        const mapId = 'glossaryDataMap';
        const ddApi = window._sharedDropdownApis && window._sharedDropdownApis[mapId];
        const DEFAULT_SF = 2.2;
        const DEFAULT_PAD = 90;
        const sf = ddApi && typeof ddApi.getSpacingFactor === 'function' ? ddApi.getSpacingFactor() : DEFAULT_SF;
        const sp = ddApi && typeof ddApi.getSpacingPadding === 'function' ? ddApi.getSpacingPadding() : DEFAULT_PAD;
        const layoutOption = GlossaryDataMapState.layout;
        
        // Get root nodes - use provided IDs or find from network
        const roots = rootNodeIds || adapter.findRootNodes();

        // Try to use external layout configuration
        if (window.InterfaceMapStyles && typeof window.InterfaceMapStyles.getLayoutConfig === 'function') {
            const layoutConfig = window.InterfaceMapStyles.getLayoutConfig(layoutOption);
            // Add roots for breadthfirst layouts
            if (layoutConfig.name === 'breadthfirst') {
                return { ...layoutConfig, roots: roots, padding: sp, spacingFactor: sf };
            }
            // If dagre is requested but not registered, fall back to breadthfirst silently
            if (layoutConfig.name === 'dagre' && !GlossaryDataMapState.dagreRegistered) {
                return {
                    name: 'breadthfirst',
                    directed: true,
                    padding: sp,
                    spacingFactor: sf,
                    animate: true,
                    animationDuration: 500,
                    nodeDimensionsIncludeLabels: true,
                    roots: roots
                };
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
                    roots: roots,
                    transform: (node, pos) => ({ x: -pos.x, y: pos.y })
                };
            case 'top-to-bottom':
                // Use dagre if available, otherwise fall back to breadthfirst
                if (GlossaryDataMapState.dagreRegistered) {
                    return {
                        name: 'dagre',
                        rankDir: 'TB',
                        padding: sp,
                        spacingFactor: sf,
                        animate: true,
                        animationDuration: 500,
                        nodeDimensionsIncludeLabels: true
                    };
                } else {
                    // Fallback to breadthfirst if dagre not available
                    return {
                        ...baseLayout,
                        roots: roots
                    };
                }
            case 'organic':
            case 'force':
                return {
                    name: 'cose',
                    animate: true,
                    animationDuration: 500,
                    nodeRepulsion: Math.round(5000 * sf),
                    idealEdgeLength: Math.round(150 * sf),
                    edgeElasticity: 0.45,
                    nestingFactor: 0.1,
                    gravity: 0.25,
                    numIter: 1500,
                    initialEnergyOnIncremental: 0.3
                };
            default: // left-to-right
                if (GlossaryDataMapState.dagreRegistered) {
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
                return {
                    ...baseLayout,
                    roots: roots
                };
        }
    }

    // Find root nodes from graph data (before network is created)
    function findRootNodesFromGraph(graph) {
        if (!graph || !graph.nodes || graph.nodes.length === 0) return [];
        
        const nodeIds = new Set(graph.nodes.map(n => n.id));
        const targets = new Set();
        
        if (graph.edges) {
            graph.edges.forEach(edge => {
                if (edge.to) targets.add(edge.to);
            });
        }
        
        const roots = graph.nodes.filter(node => !targets.has(node.id));
        return roots.length > 0 ? roots.map(n => n.id) : (graph.nodes.length > 0 ? [graph.nodes[0].id] : []);
    }

    function setupEventListeners() {
        if (networkIx) networkIx.setupEventListeners();
    }

    // Show node details in side panel
    function showNodeDetails(nodeData) {
        const detailsEl = document.querySelector('[data-glossary-data-map-details]');
        const placeholderEl = document.querySelector('[data-glossary-data-map-placeholder]');
        
        if (!detailsEl || !placeholderEl) return;
        
        placeholderEl.style.display = 'none';
        detailsEl.style.display = 'block';
        
        const name = nodeData.systemName || nodeData.datasetName || nodeData.label || 'Unknown';
        const type = nodeData.isSystem ? 'System' : 'Dataset';
        const ref = nodeData.refNumber || '';
        
        detailsEl.innerHTML = `
            <div class="selection-details">
                <h5>${escapeHtml(name)}</h5>
                <div class="selection-meta">
                    <div><strong>Type:</strong> ${escapeHtml(type)}</div>
                    ${ref ? `<div><strong>Ref:</strong> ${escapeHtml(ref)}</div>` : ''}
                    ${nodeData.classification ? `<div><strong>Classification:</strong> ${escapeHtml(nodeData.classification)}</div>` : ''}
                    ${nodeData.type ? `<div><strong>Type:</strong> ${escapeHtml(nodeData.type)}</div>` : ''}
                    ${nodeData.lifecycle ? `<div><strong>Lifecycle:</strong> ${escapeHtml(nodeData.lifecycle)}</div>` : ''}
                </div>
            </div>
        `;
    }

    // Hide node details
    function hideNodeDetails() {
        const detailsEl = document.querySelector('[data-glossary-data-map-details]');
        const placeholderEl = document.querySelector('[data-glossary-data-map-placeholder]');
        
        if (detailsEl) detailsEl.style.display = 'none';
        if (placeholderEl) placeholderEl.style.display = 'block';
    }

    // Show edge info
    function showEdgeInfo(edgeData) {
        embLog('[GLOSSARY-DATA-MAP] Edge clicked:', edgeData);
    }

    // Set map type
    function setMapType(mapType) {
        GlossaryDataMapState.mapType = mapType;
        if (GlossaryDataMapState.glossaryId) {
            loadMapData(GlossaryDataMapState.glossaryId);
        }
    }

    // Set layout
    function setLayout(layout) {
        GlossaryDataMapState.layout = layout;
        if (GlossaryDataMapState.network) {
            const rootNodeIds = adapter.findRootNodes();
            GlossaryDataMapState.network.layout(buildCytoscapeLayout(rootNodeIds)).run();
        }
    }

    function setHopsCount(count) {
        const val = Math.min(99, Math.max(1, parseInt(count, 10) || 15));
        GlossaryDataMapState.hopsCount = val;
        if (GlossaryDataMapState.glossaryId) {
            loadMapData(GlossaryDataMapState.glossaryId);
        }
    }

    // Set overlay
    function setOverlay(overlayType) {
        embLog('[GLOSSARY-DATA-MAP] setOverlay called with:', overlayType);
        GlossaryDataMapState.overlay = overlayType;
        
        if (overlayType === 'none') {
            clearOverlayPanels();
        } else {
            loadOverlayData(overlayType);
        }
    }

    /** Set overlay columns (field ids) for an overlay type. Re-renders overlay if that type is active. */
    function setOverlayColumns(overlayType, columnIds) {
        if (!overlayType) return;
        GlossaryDataMapState.overlayColumnsByType[overlayType] = Array.isArray(columnIds) ? columnIds.slice() : [];
        if (GlossaryDataMapState.overlay === overlayType) {
            loadOverlayData(overlayType);
        }
    }

    /** Get selected overlay column ids for an overlay type. */
    function getOverlayColumns(overlayType) {
        var raw = GlossaryDataMapState.overlayColumnsByType[overlayType];
        if (window.OverlayColumns && typeof window.OverlayColumns.resolveSelectedColumnIds === 'function') {
            return window.OverlayColumns.resolveSelectedColumnIds(raw, overlayType);
        }
        return (Array.isArray(raw) && raw.length > 0) ? raw.slice() : (window.OverlayColumns ? window.OverlayColumns.getDefaultOverlayColumnIds(overlayType) : ['name']);
    }

    // Load overlay data for all visible nodes
    async function loadOverlayData(overlayType) {
        if (!GlossaryDataMapState.network) return;
        
        embLog('[GLOSSARY-DATA-MAP] Loading overlay data:', overlayType);

        // Reset and rebuild the cross-panel highlight cache from the latest
        // dataset relationships. Glossary anchors are recorded as the per-system /
        // per-dataset glossary fetch loops below run.
        resetOverlayHighlightCache();
        rebuildHighlightCacheFromRelationships();

        try {
            const overlayData = new Map();
            const isDatasetLineage = GlossaryDataMapState.mapType === 'dataset-lineage';
            const isMultiNode = GlossaryDataMapState.mapType === 'multi-node-lineage';
            
            if (GlossaryDataMapState.mapType === 'system-lineage' || isMultiNode) {
                const systemIds = new Set();
                GlossaryDataMapState.network.nodes().forEach(node => {
                    const nodeData = node.data();
                    if (nodeData.isSystem && nodeData.systemId) systemIds.add(String(nodeData.systemId));
                });
                await Promise.all(Array.from(systemIds).map(async (systemId) => {
                    try {
                        const data = await fetchOverlayDataForSystem(systemId, overlayType);
                        if (data && data.length > 0) overlayData.set(systemId, data);
                    } catch (error) {
                        embWarn(`[GLOSSARY-DATA-MAP] Failed to load ${overlayType} for system ${systemId}:`, error);
                    }
                }));
            }
            
            if (isDatasetLineage || isMultiNode) {
                const datasetNodes = [];
                GlossaryDataMapState.network.nodes().forEach(node => {
                    const nodeData = node.data();
                    if (nodeData.isDataset && nodeData.datasetId) datasetNodes.push({ nodeId: node.id(), datasetId: String(nodeData.datasetId) });
                });
                await Promise.all(datasetNodes.map(async ({ nodeId, datasetId }) => {
                    try {
                        const data = await fetchOverlayDataForDataset(datasetId, overlayType);
                        if (data && data.length > 0) overlayData.set(nodeId, data);
                    } catch (error) {
                        embWarn(`[GLOSSARY-DATA-MAP] Failed to load ${overlayType} for dataset ${datasetId}:`, error);
                    }
                }));
            }
            
            // Finalise the unified anchor graph after glossary anchors and
            // attribute owners have been populated by the per-item glossary fetch.
            try {
                var _hcache = getOverlayHighlightCache();
                if (window.MapOverlayHighlight && typeof window.MapOverlayHighlight.buildAnchorGraph === 'function') {
                    _hcache.anchorGraph = window.MapOverlayHighlight.buildAnchorGraph(
                        _hcache.datasetGraph || new Map(),
                        _hcache.attributeRels || [],
                        _hcache.attributeOwnerMap || new Map()
                    );
                }
            } catch (cacheErr) {
                embWarn('[GLOSSARY-DATA-MAP] Failed to finalise highlight cache:', cacheErr);
            }

            try {
                const focusNodeIds = new Set();
                if (GlossaryDataMapState.network) {
                    GlossaryDataMapState.network.nodes().forEach(function (node) {
                        const d = node.data();
                        if (!d || !d.isCurrent) return;
                        if (d.isSystem && d.systemId) focusNodeIds.add(String(d.systemId));
                        if (d.isDataset) focusNodeIds.add(String(node.id()));
                    });
                }
                const _hcacheGd = getOverlayHighlightCache();
                if (window.MapOverlayHighlight && typeof window.MapOverlayHighlight.sortOverlayDataByRelevance === 'function') {
                    window.MapOverlayHighlight.sortOverlayDataByRelevance({
                        overlayType: overlayType,
                        overlayData: overlayData,
                        cache: _hcacheGd,
                        getItemId: getOverlayItemId,
                        getItemText: getOverlayItemText,
                        focusNodeIds: focusNodeIds
                    });
                }
            } catch (sortErr) { /* non-fatal */ }

            // Render overlay panels (key by node id: systemId for system nodes, dataset_${id} for dataset nodes)
            renderOverlayPanels(overlayType, overlayData);
            
        } catch (error) {
            console.error('[GLOSSARY-DATA-MAP] Error loading overlay data:', error);
        }
    }

    function getDatasetRelationshipField(rel, keys) {
        if (!rel || !Array.isArray(keys)) return '';
        for (var i = 0; i < keys.length; i++) {
            var value = rel[keys[i]];
            if (value !== undefined && value !== null && String(value).trim() !== '') {
                return String(value);
            }
        }
        return '';
    }

    function getGlossaryDataMapScopeKey() {
        var currentSystemIds = getCurrentSystemIdsForGlossaryMap();
        return [
            GlossaryDataMapState.mapType || '',
            Array.from(currentSystemIds).sort().join(','),
            Array.from(GlossaryDataMapState.overlayDatasets.keys()).sort().join(',')
        ].join('|');
    }

    function getCurrentSystemIdsForGlossaryMap() {
        var out = new Set();
        if (GlossaryDataMapState.network) {
            GlossaryDataMapState.network.nodes().forEach(function (node) {
                var nd = node.data();
                if (nd && nd.isSystem && nd.isCurrent && nd.systemId != null) {
                    out.add(String(nd.systemId));
                }
            });
        }
        if (out.size === 0 && GlossaryDataMapState.directlyLinkedSystems && GlossaryDataMapState.directlyLinkedSystems.size > 0) {
            GlossaryDataMapState.directlyLinkedSystems.forEach(function (sid) { out.add(String(sid)); });
        }
        return out;
    }

    function getRelatedDatasetsForGlossaryDataMap(seedDatasetIds) {
        var related = new Set();
        var seed = new Set(Array.from(seedDatasetIds || []).map(function (id) { return String(id); }));
        (GlossaryDataMapState.datasetRelationships || []).forEach(function (rel) {
            var srcDid = getDatasetRelationshipField(rel, ['sourceDatasetId', 'sourceDataSetId', 'Source_DatasetID']);
            var tgtDid = getDatasetRelationshipField(rel, ['targetDatasetId', 'targetDataSetId', 'Target_DatasetID']);
            if (!srcDid || !tgtDid) return;
            if (seed.has(srcDid) && !seed.has(tgtDid)) related.add(tgtDid);
            if (seed.has(tgtDid) && !seed.has(srcDid)) related.add(srcDid);
        });
        return related;
    }

    function ensureGlossaryDatasetScope() {
        var key = getGlossaryDataMapScopeKey();
        if (GlossaryDataMapState._glossaryDatasetScope && GlossaryDataMapState._glossaryDatasetScope.key === key) {
            return GlossaryDataMapState._glossaryDatasetScope.scope;
        }

        var currentSystemIds = getCurrentSystemIdsForGlossaryMap();
        var currentDatasetIds = new Set();
        currentSystemIds.forEach(function (sid) {
            var ids = GlossaryDataMapState.overlayDatasets.get(String(sid));
            if (ids) {
                ids.forEach(function (did) { currentDatasetIds.add(String(did)); });
            }
        });

        // Build per-non-current-dataset set of attribute IDs that are linked
        // (via dataset relationships) to attributes in current's datasets.
        var linkedAttributeIdsByDataset = new Map();
        function addLinkedAttr(did, aid) {
            var didStr = String(did || '');
            var aidStr = aid != null ? String(aid) : '';
            if (!didStr || !aidStr) return;
            if (currentDatasetIds.has(didStr)) return;
            if (!linkedAttributeIdsByDataset.has(didStr)) {
                linkedAttributeIdsByDataset.set(didStr, new Set());
            }
            linkedAttributeIdsByDataset.get(didStr).add(aidStr);
        }
        (GlossaryDataMapState.datasetRelationships || []).forEach(function (rel) {
            var srcDid = getDatasetRelationshipField(rel, ['sourceDatasetId', 'sourceDataSetId', 'Source_DatasetID']);
            var tgtDid = getDatasetRelationshipField(rel, ['targetDatasetId', 'targetDataSetId', 'Target_DatasetID']);
            if (!srcDid || !tgtDid) return;
            var srcAid = rel && (rel.sourceAttributeId != null ? rel.sourceAttributeId : rel.Source_AttributeID);
            var tgtAid = rel && (rel.targetAttributeId != null ? rel.targetAttributeId : rel.Target_AttributeID);
            if (currentDatasetIds.has(srcDid) && !currentDatasetIds.has(tgtDid)) addLinkedAttr(tgtDid, tgtAid);
            if (currentDatasetIds.has(tgtDid) && !currentDatasetIds.has(srcDid)) addLinkedAttr(srcDid, srcAid);
        });

        var scope = {
            currentSystemIds: currentSystemIds,
            currentDatasetIds: currentDatasetIds,
            relatedDatasetIds: getRelatedDatasetsForGlossaryDataMap(currentDatasetIds),
            linkedAttributeIdsByDataset: linkedAttributeIdsByDataset
        };
        GlossaryDataMapState._glossaryDatasetScope = { key: key, scope: scope };
        return scope;
    }

    function isDatasetAllowedForGlossary(scope, datasetId, systemId) {
        if (!scope) return false;
        var did = String(datasetId || '');
        var sid = String(systemId || '');
        if (!did || !sid) return false;
        if (scope.currentSystemIds.has(sid)) return scope.currentDatasetIds.has(did);
        return scope.relatedDatasetIds.has(did);
    }

    function isAttributeAllowedForGlossary(scope, datasetId, attributeId) {
        if (!scope) return false;
        var did = String(datasetId || '');
        var aid = attributeId != null ? String(attributeId) : '';
        if (!did) return false;
        if (scope.currentDatasetIds && scope.currentDatasetIds.has(did)) return true;
        if (!aid) return false;
        var linked = scope.linkedAttributeIdsByDataset && scope.linkedAttributeIdsByDataset.get(did);
        return !!(linked && linked.has(aid));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cross-panel transitive highlight cache. Built from the existing dataset
    // relationships array. Glossary anchors and attribute-owner mappings are
    // recorded as the per-system / per-dataset glossary fetch loops run.
    // ─────────────────────────────────────────────────────────────────────────
    function getOverlayHighlightCache() {
        if (!GlossaryDataMapState._overlayHighlightCache) {
            GlossaryDataMapState._overlayHighlightCache = {
                datasetGraph: new Map(),
                attributeRels: [],
                attributeOwnerMap: new Map(),
                glossaryToAnchors: new Map(),
                anchorToGlossaries: new Map(),
                anchorGraph: null
            };
        }
        return GlossaryDataMapState._overlayHighlightCache;
    }

    function resetOverlayHighlightCache() {
        GlossaryDataMapState._overlayHighlightCache = {
            datasetGraph: new Map(),
            attributeRels: [],
            attributeOwnerMap: new Map(),
            glossaryToAnchors: new Map(),
            anchorToGlossaries: new Map(),
            anchorGraph: null
        };
    }

    function rebuildHighlightCacheFromRelationships() {
        var cache = getOverlayHighlightCache();
        var datasetGraph = new Map();
        var attributeRels = [];
        var attributeOwnerMap = new Map();

        function addEdge(graph, a, b) {
            if (!a || !b || a === b) return;
            if (!graph.has(a)) graph.set(a, new Set());
            if (!graph.has(b)) graph.set(b, new Set());
            graph.get(a).add(b);
            graph.get(b).add(a);
        }

        (GlossaryDataMapState.datasetRelationships || []).forEach(function (rel) {
            var srcDs = getDatasetRelationshipField(rel, ['sourceDatasetId', 'sourceDataSetId', 'Source_DatasetID']);
            var tgtDs = getDatasetRelationshipField(rel, ['targetDatasetId', 'targetDataSetId', 'Target_DatasetID']);
            var srcAttr = rel && (rel.sourceAttributeId != null ? String(rel.sourceAttributeId) : (rel.Source_AttributeID != null ? String(rel.Source_AttributeID) : ''));
            var tgtAttr = rel && (rel.targetAttributeId != null ? String(rel.targetAttributeId) : (rel.Target_AttributeID != null ? String(rel.Target_AttributeID) : ''));
            if (srcDs && tgtDs) addEdge(datasetGraph, srcDs, tgtDs);
            if (srcAttr && tgtAttr) attributeRels.push({ sourceAttributeId: srcAttr, targetAttributeId: tgtAttr });
            if (srcAttr && srcDs) attributeOwnerMap.set(srcAttr, srcDs);
            if (tgtAttr && tgtDs) attributeOwnerMap.set(tgtAttr, tgtDs);
        });

        cache.datasetGraph = datasetGraph;
        cache.attributeRels = attributeRels;
        cache.attributeOwnerMap = attributeOwnerMap;

        if (window.MapOverlayHighlight && typeof window.MapOverlayHighlight.buildAnchorGraph === 'function') {
            cache.anchorGraph = window.MapOverlayHighlight.buildAnchorGraph(datasetGraph, attributeRels, attributeOwnerMap);
        }
    }

    function recordHighlightGlossaryAnchor(anchorKey, glossaryId) {
        if (!anchorKey || glossaryId == null) return;
        var cache = getOverlayHighlightCache();
        var gid = String(glossaryId).trim();
        if (!gid) return;
        if (!cache.glossaryToAnchors.has(gid)) cache.glossaryToAnchors.set(gid, new Set());
        cache.glossaryToAnchors.get(gid).add(anchorKey);
        if (!cache.anchorToGlossaries.has(anchorKey)) cache.anchorToGlossaries.set(anchorKey, new Set());
        cache.anchorToGlossaries.get(anchorKey).add(gid);
    }

    // Fetch overlay data for a system
    async function fetchOverlayDataForSystem(systemId, overlayType) {
        try {
            switch (overlayType) {
                case 'datasets':
                    // Only show datasets associated with the glossary in this system
                    const systemDatasets = GlossaryDataMapState.overlayDatasets.get(String(systemId));
                    if (systemDatasets) {
                        const normDs = window.OverlayRowNormalize && typeof window.OverlayRowNormalize.datasetFromApi === 'function'
                            ? window.OverlayRowNormalize.datasetFromApi : null;
                        const datasets = [];
                        for (const datasetId of systemDatasets) {
                            const dataset = GlossaryDataMapState.datasetsData.get(String(datasetId));
                            if (dataset) {
                                datasets.push(normDs ? normDs(dataset, { id: datasetId }) : {
                                    id: datasetId,
                                    name: dataset.name || dataset.primaryName || dataset.PrimaryName || `Dataset ${datasetId}`,
                                    refNumber: dataset.refNumber || dataset.RefNumber || ''
                                });
                            }
                        }
                        return datasets;
                    }
                    return [];
                    
                case 'attributes':
                    // Case 1: Don't show attributes
                    if (GlossaryDataMapState.caseType === 'case1') {
                        return [];
                    }
                    // Case 2: Only show directly associated attributes
                    const systemAttributes = GlossaryDataMapState.overlayAttributes.get(String(systemId));
                    if (systemAttributes) {
                        const normAttr = window.OverlayRowNormalize && typeof window.OverlayRowNormalize.attributeFromApi === 'function'
                            ? window.OverlayRowNormalize.attributeFromApi : null;
                        const attributes = [];
                        for (const attrId of systemAttributes) {
                            const attr = GlossaryDataMapState.attributesData.get(String(attrId));
                            if (attr) {
                                const base = Object.assign({}, attr, { id: attr.id || attr.ID || attrId });
                                attributes.push(normAttr ? normAttr(base, { systemId: systemId, datasetId: attr.datasetId }) : {
                                    id: attrId,
                                    name: attr.name || attr.attributeName || attr.Name || `Attribute ${attrId}`,
                                    datasetId: attr.datasetId
                                });
                            }
                        }
                        return attributes;
                    }
                    return [];
                    
                case 'linking-attributes':
                    // Case 1: Don't show attributes
                    if (GlossaryDataMapState.caseType === 'case1') {
                        return [];
                    }
                    // Case 2: Only show directly associated attributes
                    const linkingAttributes = GlossaryDataMapState.linkingAttributes.get(String(systemId));
                    if (linkingAttributes) {
                        const normAttrL = window.OverlayRowNormalize && typeof window.OverlayRowNormalize.attributeFromApi === 'function'
                            ? window.OverlayRowNormalize.attributeFromApi : null;
                        const attributes = [];
                        for (const attrId of linkingAttributes) {
                            const attr = GlossaryDataMapState.attributesData.get(String(attrId));
                            if (attr) {
                                const base = Object.assign({}, attr, { id: attr.id || attr.ID || attrId });
                                attributes.push(normAttrL ? normAttrL(base, { systemId: systemId, datasetId: attr.datasetId }) : {
                                    id: attrId,
                                    name: attr.name || attr.attributeName || attr.Name || `Attribute ${attrId}`,
                                    datasetId: attr.datasetId
                                });
                            }
                        }
                        return attributes;
                    }
                    return [];
                    
                case 'description':
                    const system = GlossaryDataMapState.systemsData.get(String(systemId));
                    if (system && system.description) {
                        return [{ name: 'Description', value: system.description }];
                    }
                    return [];
                    
                case 'glossary': {
                    const glossaries = [];
                    const seenSysG = new Set();
                    const sysDatasets = GlossaryDataMapState.overlayDatasets.get(String(systemId));
                    const glossaryScope = ensureGlossaryDatasetScope();
                    const isCurrentSystem = !!(glossaryScope && glossaryScope.currentSystemIds && glossaryScope.currentSystemIds.has(String(systemId)));
                    if (sysDatasets) {
                        for (const datasetId of sysDatasets) {
                            if (!isDatasetAllowedForGlossary(glossaryScope, datasetId, systemId)) continue;
                            // 1) Dataset's own glossary
                            const dataset = GlossaryDataMapState.datasetsData.get(String(datasetId));
                            if (dataset && dataset.glossaryId) {
                                const gIdStr = String(dataset.glossaryId);
                                if (!seenSysG.has(gIdStr)) {
                                    seenSysG.add(gIdStr);
                                    try {
                                        const glossary = await window.BUDG_API_SERVICE.getGlossaryById(dataset.glossaryId);
                                        const gd = glossary?.data || glossary;
                                        if (gd) {
                                            const gName = gd.name || gd.Name || gd.primaryName || '';
                                            glossaries.push({ id: dataset.glossaryId, name: gName, glossary: gName, glossaryId: dataset.glossaryId, source: 'dataset' });
                                        }
                                    } catch (e) { /* ignore */ }
                                }
                                if (window.MapOverlayHighlight) {
                                    recordHighlightGlossaryAnchor(window.MapOverlayHighlight.dsKey(datasetId), dataset.glossaryId);
                                }
                            }
                            // 2) Glossary terms from attributes — for non-current systems,
                            // restrict to attributes linked via dataset relationships to current.
                            try {
                                const attrResp = await fetch(`/api/attribute/${datasetId}`, { credentials: 'include' });
                                if (attrResp.ok) {
                                    const attrData = await attrResp.json();
                                    let attrs = Array.isArray(attrData?.data) ? attrData.data : (Array.isArray(attrData) ? attrData : []);
                                    if (!isCurrentSystem) {
                                        attrs = attrs.filter(a => isAttributeAllowedForGlossary(glossaryScope, datasetId, a && (a.id != null ? a.id : (a.ID != null ? a.ID : a.attribute_id))));
                                    }
                                    attrs.forEach(attr => {
                                        const aid = attr && (attr.id != null ? attr.id : (attr.ID != null ? attr.ID : attr.attribute_id));
                                        if (aid != null) {
                                            getOverlayHighlightCache().attributeOwnerMap.set(String(aid), String(datasetId));
                                        }
                                        const gName = attr['Glossary Name attribute'] || attr.glossary || attr.glossaryName || attr.GlossaryName;
                                        const gId = attr.glossaryId || attr.glossary_id || attr.Glossary_ID;
                                        if (window.MapOverlayHighlight && aid != null && gId != null) {
                                            recordHighlightGlossaryAnchor(window.MapOverlayHighlight.attrKey(aid), gId);
                                        }
                                        const gKey = gName || (gId ? String(gId) : null);
                                        if (gKey && !seenSysG.has(gKey)) {
                                            seenSysG.add(gKey);
                                            glossaries.push({ id: gId, name: gName, glossary: gName, glossaryId: gId, source: 'attribute' });
                                        }
                                    });
                                }
                            } catch (e) { /* ignore */ }
                        }
                    }
                    if (window.OverlayColumns && typeof window.OverlayColumns.enrichGlossaryOverlayTerms === 'function') {
                        return await window.OverlayColumns.enrichGlossaryOverlayTerms(glossaries);
                    }
                    return glossaries;
                }
                    
                case 'stakeholders':
                    try {
                        const resp = await fetch(`/api/system-stakeholder/${systemId}/stakeholders`, { credentials: 'include' });
                        if (resp.ok) {
                            const data = await resp.json();
                            if (Array.isArray(data)) {
                                return data;
                            } else if (Array.isArray(data?.data)) {
                                return data.data;
                            } else if (Array.isArray(data?.stakeholders)) {
                                return data.stakeholders;
                            }
                        }
                    } catch (e) {
                        embWarn('[GLOSSARY-DATA-MAP] Error fetching stakeholders:', e);
                    }
                    return [];
                    
                case 'projects':
                    try {
                        const resp = await fetch(`/api/system-impact/${systemId}/projects`, { credentials: 'include' });
                        if (resp.ok) {
                            const data = await resp.json();
                            return Array.isArray(data?.data) ? data.data : (Array.isArray(data) ? data : []);
                        }
                    } catch (e) {
                        // Ignore
                    }
                    return [];

                case 'processes':
                    try {
                        const resp = await fetch(`/api/process-impact/systems/${systemId}/processes`, { credentials: 'include' });
                        if (resp.ok) {
                            const data = await resp.json();
                            return Array.isArray(data?.data) ? data.data : (Array.isArray(data) ? data : []);
                        }
                    } catch (e) {
                        // Ignore
                    }
                    return [];

                case 'policies':
                    try {
                        const resp = await fetch(`/api/policy-impact/systems/${systemId}/policies`, { credentials: 'include' });
                        if (resp.ok) {
                            const data = await resp.json();
                            return Array.isArray(data?.data) ? data.data : (Array.isArray(data) ? data : []);
                        }
                    } catch (e) {
                        // Ignore
                    }
                    return [];

                case 'data-quality':
                    try {
                        const resp = await fetch(`/api/data-quality/system/${systemId}`, { credentials: 'include' });
                        if (resp.ok) {
                            const data = await resp.json();
                            return Array.isArray(data?.data) ? data.data : (Array.isArray(data) ? data : []);
                        }
                    } catch (e) {
                        // Ignore
                    }
                    return [];

                case 'data-privacy':
                    try {
                        const resp = await fetch(`/api/data-privacy/system/${systemId}`, { credentials: 'include' });
                        if (resp.ok) {
                            const data = await resp.json();
                            return Array.isArray(data?.data) ? data.data : (Array.isArray(data) ? data : []);
                        }
                    } catch (e) {
                        // Ignore
                    }
                    return [];

                case 'business-area':
                    try {
                        const resp = await fetch(`/api/businessarea-impact/systems/${systemId}/businessareas`, { credentials: 'include' });
                        if (resp.ok) {
                            const data = await resp.json();
                            return Array.isArray(data?.data) ? data.data : (Array.isArray(data) ? data : []);
                        }
                    } catch (e) {
                        // Ignore
                    }
                    return [];

                case 'products':
                    try {
                        const resp = await fetch(`/api/system-impact/${systemId}/products`, { credentials: 'include' });
                        if (resp.ok) {
                            const data = await resp.json();
                            return Array.isArray(data?.data) ? data.data : (Array.isArray(data) ? data : []);
                        }
                    } catch (e) {
                        // Ignore
                    }
                    return [];

                case 'legal-entities':
                    try {
                        const resp = await fetch(`/api/system-impact/${systemId}/legals`, { credentials: 'include' });
                        if (resp.ok) {
                            const data = await resp.json();
                            return Array.isArray(data?.data) ? data.data : (Array.isArray(data) ? data : []);
                        }
                    } catch (e) {
                        // Ignore
                    }
                    return [];

                case 'geography':
                    try {
                        const system = GlossaryDataMapState.systemsData.get(String(systemId));
                        if (system && (system.geography || system.region || system.country)) {
                            return [{ name: system.geography || system.region || system.country, type: 'geography' }];
                        }
                        const API = window.BUDG_API_SERVICE;
                        if (API && typeof API.getSystemById === 'function') {
                            const res = await API.getSystemById(systemId);
                            const d = res?.data || res;
                            if (d && (d.geography || d.region || d.country)) {
                                return [{ name: d.geography || d.region || d.country, type: 'geography' }];
                            }
                        }
                    } catch (e) {
                        // Ignore
                    }
                    return [];
                    
                default:
                    return [];
            }
        } catch (error) {
            embWarn(`[GLOSSARY-DATA-MAP] Failed to fetch ${overlayType} for system ${systemId}:`, error);
            return [];
        }
    }

    // Fetch overlay data for a dataset (dataset lineage / multi-node)
    async function fetchOverlayDataForDataset(datasetId, overlayType) {
        try {
            switch (overlayType) {
                case 'description':
                    const dataset = GlossaryDataMapState.datasetsData.get(String(datasetId));
                    if (dataset && (dataset.definition || dataset.description)) {
                        return [{ name: 'Definition', value: dataset.definition || dataset.description }];
                    }
                    return [];
                case 'glossary': {
                    const datasetData = GlossaryDataMapState.datasetsData.get(String(datasetId));
                    const datasetSystemId = datasetData ? String(datasetData.systemId || datasetData.masterSource || datasetData.MasterSource || '') : '';
                    const glossaryScope = ensureGlossaryDatasetScope();
                    if (!isDatasetAllowedForGlossary(glossaryScope, datasetId, datasetSystemId)) {
                        return [];
                    }
                    const datasetIsCurrent = !!(glossaryScope && glossaryScope.currentDatasetIds && glossaryScope.currentDatasetIds.has(String(datasetId)));
                    const glossaryTerms = [];
                    const seenG = new Set();
                    // 1) Dataset's own glossary term
                    const ds = datasetData;
                    if (ds && ds.glossaryId) {
                        try {
                            const glossary = await window.BUDG_API_SERVICE.getGlossaryById(ds.glossaryId);
                            const g = glossary?.data || glossary;
                            if (g) {
                                const gName = g.name || g.Name || g.primaryName || '';
                                if (gName && !seenG.has(gName)) {
                                    seenG.add(gName);
                                    glossaryTerms.push({ id: ds.glossaryId, name: gName, glossary: gName, glossaryId: ds.glossaryId, source: 'dataset' });
                                }
                            }
                        } catch (e) { /* ignore */ }
                        if (window.MapOverlayHighlight) {
                            recordHighlightGlossaryAnchor(window.MapOverlayHighlight.dsKey(datasetId), ds.glossaryId);
                        }
                    }
                    // 2) Glossary terms from attributes — for non-current datasets,
                    // only include attributes linked via dataset relationships to current.
                    try {
                        const attrResp = await fetch(`/api/attribute/${datasetId}`, { credentials: 'include' });
                        if (attrResp.ok) {
                            const attrData = await attrResp.json();
                            let attrs = Array.isArray(attrData?.data) ? attrData.data : (Array.isArray(attrData) ? attrData : []);
                            if (!datasetIsCurrent) {
                                attrs = attrs.filter(a => isAttributeAllowedForGlossary(glossaryScope, datasetId, a && (a.id != null ? a.id : (a.ID != null ? a.ID : a.attribute_id))));
                            }
                            attrs.forEach(attr => {
                                const aid = attr && (attr.id != null ? attr.id : (attr.ID != null ? attr.ID : attr.attribute_id));
                                if (aid != null) {
                                    getOverlayHighlightCache().attributeOwnerMap.set(String(aid), String(datasetId));
                                }
                                const gName = attr['Glossary Name attribute'] || attr.glossary || attr.glossaryName || attr.GlossaryName;
                                const gId = attr.glossaryId || attr.glossary_id || attr.Glossary_ID;
                                if (window.MapOverlayHighlight && aid != null && gId != null) {
                                    recordHighlightGlossaryAnchor(window.MapOverlayHighlight.attrKey(aid), gId);
                                }
                                if (gName && !seenG.has(gName)) {
                                    seenG.add(gName);
                                    glossaryTerms.push({ id: gId, name: gName, glossary: gName, glossaryId: gId, source: 'attribute' });
                                }
                            });
                        }
                    } catch (e) { /* ignore */ }
                    if (window.OverlayColumns && typeof window.OverlayColumns.enrichGlossaryOverlayTerms === 'function') {
                        return await window.OverlayColumns.enrichGlossaryOverlayTerms(glossaryTerms);
                    }
                    return glossaryTerms;
                }
                case 'attributes': {
                    // Case 1 (glossary on dataset): no attributes shown.
                    if (GlossaryDataMapState.caseType === 'case1') return [];
                    try {
                        const normA = window.OverlayRowNormalize && typeof window.OverlayRowNormalize.attributeFromApi === 'function'
                            ? window.OverlayRowNormalize.attributeFromApi : null;
                        const resp = await fetch(`/api/attribute/${datasetId}`, { credentials: 'include' });
                        if (!resp.ok) return [];
                        const data = await resp.json();
                        const attrs = Array.isArray(data?.data) ? data.data : (Array.isArray(data) ? data : []);
                        // Case 2 (glossary on attribute): only attributes directly associated with the opened glossary.
                        const dsRow = GlossaryDataMapState.datasetsData.get(String(datasetId));
                        const sysId = dsRow ? String(dsRow.systemId || dsRow.masterSource || dsRow.MasterSource || '') : '';
                        const allowedAttrIds = sysId ? GlossaryDataMapState.overlayAttributes.get(sysId) : null;
                        return attrs
                            .filter(a => {
                                if (!allowedAttrIds) return false;
                                const aid = a && (a.id != null ? a.id : (a.ID != null ? a.ID : a.attribute_id));
                                return aid != null && allowedAttrIds.has(String(aid));
                            })
                            .map(a => (
                                normA ? normA(a, { datasetId: String(datasetId) }) : { id: a.id ?? a.ID, name: a['Name attribute'] || a.name || a.primaryName || '', datasetId: datasetId }
                            ));
                    } catch (e) { /* ignore */ }
                    return [];
                }
                case 'linking-attributes': {
                    // Case 1 (glossary on dataset): no attributes shown.
                    if (GlossaryDataMapState.caseType === 'case1') return [];
                    try {
                        const normL = window.OverlayRowNormalize && typeof window.OverlayRowNormalize.attributeFromApi === 'function'
                            ? window.OverlayRowNormalize.attributeFromApi : null;
                        const resp = await fetch(`/api/attribute/${datasetId}?linking=true`, { credentials: 'include' });
                        if (!resp.ok) return [];
                        const data = await resp.json();
                        const attrs = Array.isArray(data?.data) ? data.data : (Array.isArray(data) ? data : []);
                        // Case 2 (glossary on attribute): only linking attributes directly associated with the opened glossary.
                        const dsRow = GlossaryDataMapState.datasetsData.get(String(datasetId));
                        const sysId = dsRow ? String(dsRow.systemId || dsRow.masterSource || dsRow.MasterSource || '') : '';
                        const allowedAttrIds = sysId ? GlossaryDataMapState.linkingAttributes.get(sysId) : null;
                        return attrs
                            .filter(a => {
                                if (!allowedAttrIds) return false;
                                const aid = a && (a.id != null ? a.id : (a.ID != null ? a.ID : a.attribute_id));
                                return aid != null && allowedAttrIds.has(String(aid));
                            })
                            .map(a => (
                                normL ? normL(a, { datasetId: String(datasetId) }) : { id: a.id ?? a.ID, name: a['Name attribute'] || a.name || a.primaryName || '', datasetId: datasetId }
                            ));
                    } catch (e) { /* ignore */ }
                    return [];
                }
                case 'data-quality':
                    try {
                        const resp = await fetch(`/api/data-quality/dataset/${datasetId}`, { credentials: 'include' });
                        if (resp.ok) {
                            const data = await resp.json();
                            return Array.isArray(data?.data) ? data.data : (Array.isArray(data) ? data : []);
                        }
                    } catch (e) { /* ignore */ }
                    return [];
                case 'stakeholders':
                    try {
                        const resp = await fetch(`/api/dataset-stakeholder/${datasetId}/stakeholders`, { credentials: 'include' });
                        if (resp.ok) {
                            const data = await resp.json();
                            return Array.isArray(data?.data) ? data.data : (Array.isArray(data) ? data : []);
                        }
                    } catch (e) { /* ignore */ }
                    return [];
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
                default:
                    return [];
            }
        } catch (error) {
            embWarn(`[GLOSSARY-DATA-MAP] Failed to fetch ${overlayType} for dataset ${datasetId}:`, error);
            return [];
        }
    }

    // Render overlay panels on nodes
    function renderOverlayPanels(overlayType, overlayData) {
        if (!GlossaryDataMapState.network || !GlossaryDataMapState.canvas) return;
        
        // Clear existing overlay panels
        clearOverlayPanels();
        
        // Create overlay container if it doesn't exist
        let overlayContainer = GlossaryDataMapState.canvas.querySelector('.map-overlay-container');
        if (!overlayContainer) {
            overlayContainer = document.createElement('div');
            overlayContainer.className = 'map-overlay-container';
            overlayContainer.style.cssText = 'position: absolute; top: 0; left: 0; width: 100%; height: 100%; pointer-events: none; z-index: 1000;';
            GlossaryDataMapState.canvas.appendChild(overlayContainer);
        }
        
        // Add panels for each node with overlay data
        GlossaryDataMapState.network.nodes().forEach(node => {
            const nodeId = node.id();
            const nodeData = node.data();
            let data;
            let nodeName;
            if (nodeData.isSystem && nodeData.systemId) {
                data = overlayData.get(String(nodeData.systemId));
                nodeName = nodeData.systemName || nodeData.label || 'System';
            } else if (nodeData.isDataset && nodeData.datasetId) {
                data = overlayData.get(nodeId);
                nodeName = nodeData.datasetName || nodeData.label || 'Dataset';
            } else {
                return;
            }
            if (!data || data.length === 0) return;
            
            const panel = createOverlayPanel(nodeName, overlayType, data, nodeId);
            if (!panel) return;
            overlayContainer.appendChild(panel);
            positionOverlayPanelElement(panel, node);
            node.addClass('has-overlay');
        });
        
        // Add zoom/pan/drag listeners to update positions
        GlossaryDataMapState.network.on('zoom pan', updateOverlayPositions);
        GlossaryDataMapState.network.on('drag', 'node', updateOverlayPositions);
    }

    // Use global glossary-style overlay panel (header, body, footer Page X of Y; stakeholders = Name/Role, Accepted, Org Unit table)
    function createOverlayPanel(nodeName, overlayType, data, nodeId) {
        const arr = Array.isArray(data) ? data : [];
        if (arr.length === 0) return null;
        if (!window.MapOverlayPanel) {
            return createOverlayPanelFallback(nodeName, overlayType, arr, nodeId);
        }
        var overlayColumnDefs = [];
        if (window.OverlayColumns) {
            var allCols = window.OverlayColumns.getOverlayColumns(overlayType);
            var selectedIds = window.OverlayColumns.resolveSelectedColumnIds(GlossaryDataMapState.overlayColumnsByType[overlayType], overlayType);
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
        panel._overlayData = arr;
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

    // Get overlay title
    function getOverlayTitle(overlayType, nodeId) {
        if (overlayType === 'description' && nodeId != null && String(nodeId).indexOf('dataset-') === 0) {
            return 'Definition';
        }
        const titles = {
            'description': 'Description',
            'stakeholders': 'Stakeholders',
            'glossary': 'Glossaries',
            'datasets': 'Data Sets',
            'attributes': 'Attributes',
            'linking-attributes': 'Linking Attributes',
            'projects': 'Projects',
            'processes': 'Processes',
            'policies': 'Policies',
            'data-quality': 'Data Quality',
            'data-privacy': 'Data Privacy',
            'business-area': 'Business Area',
            'products': 'Products',
            'legal-entities': 'Legal Entities',
            'geography': 'Geography'
        };
        return titles[overlayType] || overlayType;
    }

    // Get overlay item text
    function getOverlayItemText(overlayType, item) {
        if (!item) return '';
        
        switch (overlayType) {
            case 'description':
                return item.value || item.description || '';
            case 'stakeholders':
                const role = item.roleName || item.RoleName || item.role || item.Role || '';
                const name = item.personName || item.PersonName || item.name || item.Name || '';
                return name ? (role ? `${name} (${role})` : name) : (role || '');
            case 'glossary': {
                const gName = item.glossary || item.name || '';
                const srcLabel = item.source === 'dataset' ? 'Dataset Glossary'
                               : item.source === 'attribute' ? 'Attribute Glossary' : '';
                return srcLabel ? `${gName} (${srcLabel})` : gName;
            }
            case 'datasets':
                return item.name || item.datasetName || item.primaryName || item.PrimaryName || '';
            case 'attributes':
            case 'linking-attributes':
                return item.name || item.attributeName || item.primaryName || item.PrimaryName || '';
            case 'projects':
                return item.projectName || item.PrimaryName || item.primaryName || item.name || item.Name || '';
            case 'processes':
                return item.processName || item.name || item.Name || item.primaryName || '';
            case 'policies':
                return item.policyName || item.name || item.Name || item.primaryName || '';
            case 'data-quality':
            case 'data-privacy':
                return item.name || item.primaryName || item.description || '';
            case 'business-area':
                return item.businessAreaName || item.name || item.Name || '';
            case 'products':
                return item.productName || item.name || item.Name || '';
            case 'legal-entities':
                return item.legalEntityName || item.name || item.Name || '';
            case 'geography':
                return item.name || item.geography || item.region || item.country || '';
            default:
                return item.name || item.primaryName || item.PrimaryName || item.Name || JSON.stringify(item);
        }
    }

    // Get overlay item ID
    function getOverlayItemId(overlayType, item) {
        if (!item) return null;
        return item.id || item.ID || item.Id || null;
    }

    // Overlay item highlighting: clicked = dark green, same item in other panels = light green
    function highlightOverlayItem(overlayType, item, sourceNodeId) {
        const overlayContainer = GlossaryDataMapState.canvas?.querySelector('.map-overlay-container');
        if (!overlayContainer) return;
        const itemId = getOverlayItemId(overlayType, item);
        const itemText = getOverlayItemText(overlayType, item);

        // Cross-panel transitive BFS via shared helpers.
        const helper = window.MapOverlayHighlight;
        const hcache = getOverlayHighlightCache();
        let datasetRelatedIds = null;
        let attributeRelatedIds = null;
        let glossaryRelatedIds = null;
        if (overlayType === 'datasets' && itemId && helper && hcache && hcache.datasetGraph) {
            datasetRelatedIds = helper.findAllRelatedDatasets(String(itemId), hcache.datasetGraph);
        }
        if ((overlayType === 'attributes' || overlayType === 'linking-attributes') && itemId && helper && hcache && Array.isArray(hcache.attributeRels)) {
            attributeRelatedIds = helper.findAllRelatedAttributes(String(itemId), hcache.attributeRels);
        }
        if (overlayType === 'glossary' && itemId && helper && hcache && hcache.anchorGraph) {
            glossaryRelatedIds = helper.findAllRelatedGlossaries(
                String(itemId),
                hcache.glossaryToAnchors,
                hcache.anchorGraph,
                hcache.anchorToGlossaries
            );
        }

        overlayContainer.querySelectorAll('.map-node-overlay-item').forEach(el => {
            el.classList.remove('highlighted-source', 'highlighted-related');
        });
        overlayContainer.querySelectorAll('.map-node-overlay-item').forEach(el => {
            const panel = el.closest('.map-node-overlay-panel');
            const isSource = panel?.getAttribute('data-node-id') === sourceNodeId;
            const elItemId = el.dataset.itemId ? String(el.dataset.itemId) : null;
            let shouldHighlight = false;
            if (itemId && elItemId && elItemId === String(itemId)) shouldHighlight = true;
            if (!shouldHighlight && (el.dataset.overlayValue || '').trim() === (itemText || '').trim()) shouldHighlight = true;
            if (shouldHighlight) {
                if (isSource) el.classList.add('highlighted-source');
                else el.classList.add('highlighted-related');
                return;
            }
            if (datasetRelatedIds && elItemId && datasetRelatedIds.has(elItemId)) {
                el.classList.add('highlighted-related');
                return;
            }
            if (attributeRelatedIds && elItemId && attributeRelatedIds.has(elItemId)) {
                el.classList.add('highlighted-related');
                return;
            }
            if (glossaryRelatedIds && elItemId && glossaryRelatedIds.has(elItemId)) {
                el.classList.add('highlighted-related');
            }
        });
        applyOverlayRelatedNodeAndEdgeHighlights(overlayContainer, sourceNodeId);
    }

    function applyOverlayRelatedNodeAndEdgeHighlights(overlayContainer, sourceNodeId) {
        const network = GlossaryDataMapState.network;
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
                    case 'name': return v(item.name || item.glossaryName || item.primaryName);
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
                    case 'name': return v(item.name || item.datasetName || item.primaryName || item.PrimaryName);
                    case 'refNumber': return v(item.refNumber || item.refnumber || item.RefNumber || item.ref || item.Ref || item.processRefNumber || item.ProcessRefNumber || item.processRef || item.ProcessRef || item.projectRefNumber || item.ProjectRefNumber || item.projectRef || item.ProjectRef || item.productRefNumber || item.ProductRefNumber || item.policyRefNumber || item.PolicyRefNumber || item.capabilityRefNumber || item.CapabilityRefNumber || item.datasetRefNumber || item.DatasetRefNumber || item.attributeRefNumber || item.AttributeRefNumber || item.glossaryRefNumber || item.GlossaryRefNumber || item.interfaceRefNumber || item.InterfaceRefNumber || item.systemRef || item.SystemRef || item.regulationRefNumber || item.RegulationRefNumber || item.regulatoryThemeRefNumber || item.RegulatoryThemeRefNumber || item.businessAreaReference || item.BusinessAreaReference || item.clientReference || item.ClientReference || item.legalReference || item.LegalReference || item.sourceProcessRef || item.targetProcessRef);
                    case 'type': return v(item.typeName || item.type || item.TypeName || item.Type);
                    case 'lifecycle': return v(item.lifecycleName || item.LifecycleName || item.lifecycle || item.Lifecycle || item.lifecycleStatusName || item.LifecycleStatusName || item.lifecycleStatus || item.LifecycleStatus || item.processLifecycleName || item.ProcessLifecycleName || item.sourceProcessLifecycleName || item.targetProcessLifecycleName || item.datasetLifecycleName || item.DatasetLifecycleName || fieldDefault(item, fieldId));
                    default: return fieldDefault(item, fieldId);
                }
            case 'attributes':
            case 'linking-attributes':
                switch (fieldId) {
                    case 'name': return v(item.name || item['Name attribute'] || item.attributeName || item.primaryName || item.PrimaryName || item.Name);
                    case 'type': return v(item.typeName || item.type || item.TypeName || item.Type);
                    case 'glossary': return v(item.glossaryName || item.glossary || item['Glossary Name attribute']);
                    case 'refNumber': return v(item.refNumber || item.refnumber || item.RefNumber || item.ref || item.Ref || item.processRefNumber || item.ProcessRefNumber || item.processRef || item.ProcessRef || item.projectRefNumber || item.ProjectRefNumber || item.projectRef || item.ProjectRef || item.productRefNumber || item.ProductRefNumber || item.policyRefNumber || item.PolicyRefNumber || item.capabilityRefNumber || item.CapabilityRefNumber || item.datasetRefNumber || item.DatasetRefNumber || item.attributeRefNumber || item.AttributeRefNumber || item.glossaryRefNumber || item.GlossaryRefNumber || item.interfaceRefNumber || item.InterfaceRefNumber || item.systemRef || item.SystemRef || item.regulationRefNumber || item.RegulationRefNumber || item.regulatoryThemeRefNumber || item.RegulatoryThemeRefNumber || item.businessAreaReference || item.BusinessAreaReference || item.clientReference || item.ClientReference || item.legalReference || item.LegalReference || item.sourceProcessRef || item.targetProcessRef);
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
            case 'projects':
                switch (fieldId) {
                    case 'name': return v(item.projectName || item.primaryName || item.PrimaryName || item.name || item.Name);
                    case 'refNumber': return v(item.refNumber || item.refnumber || item.RefNumber || item.ref || item.Ref || item.processRefNumber || item.ProcessRefNumber || item.processRef || item.ProcessRef || item.projectRefNumber || item.ProjectRefNumber || item.projectRef || item.ProjectRef || item.productRefNumber || item.ProductRefNumber || item.policyRefNumber || item.PolicyRefNumber || item.capabilityRefNumber || item.CapabilityRefNumber || item.datasetRefNumber || item.DatasetRefNumber || item.attributeRefNumber || item.AttributeRefNumber || item.glossaryRefNumber || item.GlossaryRefNumber || item.interfaceRefNumber || item.InterfaceRefNumber || item.systemRef || item.SystemRef || item.regulationRefNumber || item.RegulationRefNumber || item.regulatoryThemeRefNumber || item.RegulatoryThemeRefNumber || item.businessAreaReference || item.BusinessAreaReference || item.clientReference || item.ClientReference || item.legalReference || item.LegalReference || item.sourceProcessRef || item.targetProcessRef);
                    case 'status': return v(item.statusName || item.StatusName || item.status || item.Status || item.projectStatusName || item.ProjectStatusName || item.policyStatusName || item.PolicyStatusName);
                    default: return fieldDefault(item, fieldId);
                }
            case 'processes':
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

    function positionOverlayPanelElement(panel, node) {
        const overlayContainer = GlossaryDataMapState.canvas?.querySelector('.map-overlay-container');
        const network = GlossaryDataMapState.network;
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
        const overlayContainer = GlossaryDataMapState.canvas?.querySelector('.map-overlay-container');
        if (!overlayContainer || !GlossaryDataMapState.network) return;
        const cw = overlayContainer.clientWidth || 800;
        const ch = overlayContainer.clientHeight || 600;
        const buf = 150;

        overlayContainer.querySelectorAll('.map-node-overlay-panel').forEach(panel => {
            const nodeId = panel.getAttribute('data-node-id');
            const node = GlossaryDataMapState.network.getElementById(nodeId);
            
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

    // Clear overlay panels
    function clearOverlayPanels() {
        if (!GlossaryDataMapState.canvas) return;
        
        const overlayContainer = GlossaryDataMapState.canvas.querySelector('.map-overlay-container');
        if (overlayContainer) {
            overlayContainer.innerHTML = '';
        }
        
        if (GlossaryDataMapState.network) {
            GlossaryDataMapState.network.nodes().removeClass('has-overlay');
            try { GlossaryDataMapState.network.off('zoom pan', updateOverlayPositions); } catch (e) { /* no-op */ }
            try { GlossaryDataMapState.network.off('drag', 'node', updateOverlayPositions); } catch (e) { /* no-op */ }
        }
    }

    function getLegendHtml() {
        const colors = getMapColors();
        const lineageHtml = (window.SharedMapStyles && typeof window.SharedMapStyles.getLineageLegendHtml === 'function')
            ? window.SharedMapStyles.getLineageLegendHtml(colors)
            : (window.InterfaceMapStyles && typeof window.InterfaceMapStyles.getLineageLegendHtml === 'function')
                ? window.InterfaceMapStyles.getLineageLegendHtml(colors)
                : '';
        return `
            <div class="legend-item">
                <div class="legend-color" style="background: ${colors.currentSystem || colors.currentDataset};"></div>
                <span>Directly Linked</span>
            </div>
            <div class="legend-item">
                <div class="legend-color" style="background: ${colors.otherSystem || colors.otherDataset};"></div>
                <span>Related</span>
            </div>
            <div class="legend-item">
                <span class="legend-lock" aria-hidden="true">&#x1F512;</span>
                <span>Segment not accessible</span>
            </div>
            ${lineageHtml}
            <div class="legend-item">
                <div class="legend-line" style="border-bottom: 2px solid ${colors.relationshipLine || colors.attributeLine || '#64748b'};"></div>
                <span>Relationship</span>
            </div>
        `;
    }

    function updateLegend() {
        const legendEl = document.querySelector('[data-glossary-data-map-legend]');
        if (legendEl) legendEl.innerHTML = getLegendHtml();
        const dropdown = document.getElementById('glossaryDataMapLegendDropdown');
        if (dropdown) dropdown.innerHTML = getLegendHtml();
    }

    // Zoom in
    function zoomIn()  { adapter.zoomIn(); }
    function zoomOut() { adapter.zoomOut(); }

    // Reset map
    function resetMap() {
        if (GlossaryDataMapState.network) {
            GlossaryDataMapState.network.reset();
            const rootNodeIds = adapter.findRootNodes();
            GlossaryDataMapState.network.layout(buildCytoscapeLayout(rootNodeIds)).run();
        }
    }

    // Redraw map
    function redrawMap() {
        if (GlossaryDataMapState.glossaryId) {
            loadMapData(GlossaryDataMapState.glossaryId);
        }
    }

    // Export as PNG (includes overlay panels when active)
    function exportAsPng() {
        if (!GlossaryDataMapState.network) return;
        const filename = `glossary-data-map-${GlossaryDataMapState.glossaryId}-${Date.now()}.png`;
        try {
            if (typeof window.exportMapWithOverlays === 'function') {
                window.exportMapWithOverlays(GlossaryDataMapState.network, GlossaryDataMapState.canvas, filename);
            } else {
                const png = GlossaryDataMapState.network.png({ output: 'blob', bg: 'white', full: true });
                const url = URL.createObjectURL(png);
                const link = document.createElement('a');
                link.href = url;
                link.download = filename;
                link.click();
                URL.revokeObjectURL(url);
            }
        } catch (error) {
            console.error('[GLOSSARY-DATA-MAP] Error exporting map:', error);
        }
    }

    // Open fullscreen in new tab with toolbar controls
    function openFullscreen() {
        if (!GlossaryDataMapState.network) return;
        if (typeof window.openMapFullscreen === 'function') {
            const nodes = GlossaryDataMapState.network.nodes().map(n => n.json());
            const edges = GlossaryDataMapState.network.edges().map(e => e.json());
            window.openMapFullscreen({
                title: 'Glossary Data Map - ' + (GlossaryDataMapState.glossaryData?.name || GlossaryDataMapState.glossaryId),
                elements: { nodes: nodes, edges: edges },
                style: getCytoscapeStyle(),
                layoutName: GlossaryDataMapState.layout || 'top-to-bottom',
                legendHtml: getLegendHtml(),
                exportFilename: 'glossary-data-map-' + GlossaryDataMapState.glossaryId + '.png',
                toolbarAnchor: GlossaryDataMapState.canvas,
                mapType: GlossaryDataMapState.mapType || 'system-lineage',
                mapTabKind: 'glossary-data',
                getState: function () { return GlossaryDataMapState; }
            });
        } else {
            const canvas = GlossaryDataMapState.canvas;
            if (canvas && canvas.requestFullscreen) {
                canvas.requestFullscreen();
            } else if (canvas && canvas.webkitRequestFullscreen) {
                canvas.webkitRequestFullscreen();
            }
        }
    }

    // Toggle navigator
    function toggleNavigator() {
        if (!GlossaryDataMapState.network) return;
        var container = GlossaryDataMapState.canvas && GlossaryDataMapState.canvas.parentElement;
        var MRU = window.MapRenderUtils;
        if (MRU && typeof MRU.attachOrToggleLineageMinimap === 'function' && container) {
            MRU.attachOrToggleLineageMinimap({
                container: container,
                network: GlossaryDataMapState.network,
                minimapCanvasId: 'glossaryDataMapMinimapCy',
                syncViewport: true,
                cytoscapeOptions: { minZoom: 0.1, maxZoom: 0.5 }
            });
            return;
        }
        var nav = GlossaryDataMapState.network.navigator();
        if (nav) nav.toggle();
    }

    // Set node filters
    function setNodeFilters(nodeFilters) {
        GlossaryDataMapState.nodeFilters = {
            classifications: nodeFilters.classifications || [],
            types: nodeFilters.types || [],
            lifecycles: nodeFilters.lifecycles || []
        };
        applyNodeFiltersToNetwork();
    }

    // Set dataset node filters
    function setDatasetNodeFilters(datasetFilters) {
        GlossaryDataMapState.datasetNodeFilters = {
            types: datasetFilters.types || [],
            lifecycles: datasetFilters.lifecycles || []
        };
        applyDatasetNodeFiltersToNetwork();
    }

    function applyNodeFiltersToNetwork() {
        if (!GlossaryDataMapState.network || !window.MapGraphUtils) return;
        window.MapGraphUtils.applySystemNodeFilters(GlossaryDataMapState.network, GlossaryDataMapState.nodeFilters, {
            filtersInitialized: true,
            updateOverlayPositions: updateOverlayPositions
        });
        const rootNodeIds = adapter.findRootNodes();
        GlossaryDataMapState.network.layout(buildCytoscapeLayout(rootNodeIds)).run();
        updateOverlayPositions();
    }

    function applyDatasetNodeFiltersToNetwork() {
        if (!GlossaryDataMapState.network || !window.MapGraphUtils) return;
        window.MapGraphUtils.applyDatasetNodeFilters(GlossaryDataMapState.network, GlossaryDataMapState.datasetNodeFilters, {
            linkedDatasets: new Map(),
            filtersInitialized: true,
            updateOverlayPositions: updateOverlayPositions
        });
        const rootNodeIds = adapter.findRootNodes();
        GlossaryDataMapState.network.layout(buildCytoscapeLayout(rootNodeIds)).run();
        updateOverlayPositions();
    }

    // Show loading indicator
    function showLoading() {
        if (GlossaryDataMapState.loadingEl) {
            GlossaryDataMapState.loadingEl.style.display = 'block';
        } else if (GlossaryDataMapState.canvas) {
            GlossaryDataMapState.canvas.innerHTML = window.MapRenderUtils.htmlMapLoading();
        }
    }

    // Hide loading indicator
    function hideLoading() {
        if (GlossaryDataMapState.loadingEl) {
            GlossaryDataMapState.loadingEl.style.display = 'none';
        }
    }

    // Show placeholder
    function showPlaceholder(message) {
        if (GlossaryDataMapState.canvas) {
            GlossaryDataMapState.canvas.innerHTML = window.MapRenderUtils.htmlMapCanvasMessage(message || 'No data available');
        }
    }

    // Escape HTML
    function escapeHtml(str) {
        if (!str) return '';
        const div = document.createElement('div');
        div.textContent = str;
        return div.innerHTML;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Graph pipeline + MapEngine (shared/map/glossary-data-facet-graph.js)
    // ─────────────────────────────────────────────────────────────────────────

    graphPipeline = window.createGlossaryDataFacetGraphPipeline({
        state: GlossaryDataMapState,
        adapter: adapter,
        renderNetwork: renderNetwork,
        showPlaceholder: showPlaceholder,
        showLoading: showLoading,
        hideLoading: hideLoading,
        updateLegend: updateLegend,
        logPrefix: '[GLOSSARY-DATA-MAP]'
    });

    var _mapEngineApiLoader = graphPipeline._mapEngineApiLoader;
    var _mapEngineNodeBuilder = graphPipeline._mapEngineNodeBuilder;
    var _mapEngineEdgeBuilder = graphPipeline._mapEngineEdgeBuilder;

    networkIx = window.createGlossaryDataMapNetworkInteractions({
        state: GlossaryDataMapState,
        buildGraph: buildGraph,
        renderNetwork: renderNetwork,
        showEdgeInfo: showEdgeInfo,
        showNodeDetails: showNodeDetails,
        hideNodeDetails: hideNodeDetails
    });

    if (window.MapConfigs && window.MapConfigs.register) {
        window.MapConfigs.register('glossary-data', {
            apiLoader:     _mapEngineApiLoader,
            nodeBuilder:   _mapEngineNodeBuilder,
            edgeBuilder:   _mapEngineEdgeBuilder,
            legendBuilder: getLegendHtml
        });
    }

    var _glossaryDataEngine = new window.MapEngine('glossary-data');

    Object.assign(_glossaryDataEngine, {
        init: init,
        loadMapData: loadMapData,
        setMapType: setMapType,
        setLayout: setLayout,
        setOverlay: setOverlay,
        setOverlayColumns: setOverlayColumns,
        getOverlayColumns: getOverlayColumns,
        setHopsCount: setHopsCount,
        setNodeFilters: setNodeFilters,
        setDatasetNodeFilters: setDatasetNodeFilters,
        zoomIn: zoomIn,
        zoomOut: zoomOut,
        resetMap: resetMap,
        redrawMap: redrawMap,
        exportAsPng: exportAsPng,
        openFullscreen: openFullscreen,
        toggleNavigator: toggleNavigator,
        getLegendHtml: getLegendHtml,
        updateLegend: updateLegend,
        getState: function () { return GlossaryDataMapState; }
    });

    Object.defineProperty(_glossaryDataEngine, 'cy', {
        get: function () { return GlossaryDataMapState.network; },
        configurable: true
    });

    window.GlossaryDataMap = _glossaryDataEngine;

})();
