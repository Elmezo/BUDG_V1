/**
 * Capability Relationships Map Component
 * Implements Capability Lineage map for the Capability facet Relationships tab
 * 
 * Map Types:
 * - Capability Lineage (default): Shows capability hierarchy without siblings
 *   - Only shows direct path from root to current capability, then current's children
 *   - Example: A > B And C where B is parent of D
 *     - In A: shows A > B and C
 *     - In B: shows A > B > D
 *     - In C: shows A > C
 *     - In D: shows A > B > D
 * 
 * Overlays:
 * - System: Shows all system objects from impact tab, system subtab
 * 
 * Dependencies:
 * - shared/map/map-styles.js, map-icons.js (InterfaceMapStyles / InterfaceMapIcons)
 * - shared/map/capability-facet-lineage-graph.js (createCapabilityFacetLineageGraphPipeline)
 * - shared/map/system-lineage-network-interactions.js (createCapabilityLineageNetworkInteractions)
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

    var CAPABILITY_MAP_COLOR_FALLBACK = {
        currentCapability: '#f97316',
        currentCapabilityBorder: '#ea580c',
        otherCapability: '#1f2937',
        otherCapabilityBorder: '#374151',
        currentSystem: '#f97316',
        currentSystemBorder: '#ea580c',
        otherSystem: '#1f2937',
        otherSystemBorder: '#374151',
        relationshipLine: '#64748b',
        upstreamHighlight: '#ef4444',
        downstreamHighlight: '#22c55e',
        selectedBorder: '#248567',
        nodeLabel: '#1f2937',
        edgeLabelColor: '#6b7280'
    };
    function getMapColors() {
        return window.MapRenderUtils.getMapColors(CAPABILITY_MAP_COLOR_FALLBACK);
    }

    // Map State Management
    const CapabilityMapState = {
        initialized: false,
        dagreRegistered: false,
        network: null,
        canvas: null,
        loadingEl: null,
        capabilityId: null,
        capabilityData: null,
        layout: 'top-to-bottom',
        mapType: 'capability-lineage', // Only capability-lineage
        overlay: 'none',
        overlayColumnsByType: {},
        hopsCount: 15,
        filters: {},
        nodeFilters: {
            relationships: [],
            lifecycles: []
        },
        /** When false, that facet does not constrain nodes (no UI options). */
        relationshipFilterUiActive: false,
        lifecycleFilterUiActive: false,
        hiddenNodes: new Set(),
        hiddenUpstreamNodes: new Set(),
        hiddenDownstreamNodes: new Set(),
        focusedNode: null,
        // Capability lineage state
        allCapabilities: [],
        lineageCapabilities: [], // Capabilities to show in lineage (path + current + children)
        // System overlay state
        systemOverlayData: [] // Systems from impact tab
    };

    const adapter = window.createMapAdapter(CapabilityMapState, 'capabilityRelationshipsMap', { zoomRecenter: false });

    var graphPipeline = null;

    async function loadMapData(capabilityId) {
        return graphPipeline.loadMapData(capabilityId);
    }
    function buildCapabilityLineageGraph() {
        return graphPipeline.buildCapabilityLineageGraph();
    }
    async function loadCapabilityLineageData(capabilityId) {
        return graphPipeline.loadCapabilityLineageData(capabilityId);
    }
    function getNodeIdFromCapability(cap) {
        return graphPipeline.getNodeIdFromCapability(cap);
    }

    var networkIx = null;

    // Initialize the map
    function init(capabilityId, containerSelector) {
        embLog('[CAPABILITY-MAP] Initializing map for capability:', capabilityId, 'container:', containerSelector);
        CapabilityMapState.capabilityId = capabilityId;
        CapabilityMapState.canvas = document.querySelector(containerSelector || '#capabilityRelationshipsMapCanvas');
        CapabilityMapState.loadingEl = document.querySelector('[data-capability-map-loading]');

        if (!CapabilityMapState.canvas) {
            console.error('[CAPABILITY-MAP] Canvas element not found');
            return;
        }

        // Check if Cytoscape is available
        if (typeof cytoscape === 'undefined') {
            console.error('[CAPABILITY-MAP] Cytoscape library not available');
            CapabilityMapState.canvas.innerHTML = window.MapRenderUtils.htmlCytoscapeUnavailable();
            return;
        }

        // Register dagre layout if available - same as system map
        if (!CapabilityMapState.dagreRegistered) {
            try {
                // Try different global variable names for cytoscape-dagre
                const dagre = window.cytoscapeDagre || window['cytoscape-dagre'];
                if (dagre) {
                    cytoscape.use(dagre);
                    CapabilityMapState.dagreRegistered = true;
                }
                // Silently fall back to breadthfirst if dagre not available
            } catch (e) {
                // Silently handle error - will use breadthfirst fallback
            }
        }

        CapabilityMapState.initialized = true;
        CapabilityMapState.mapType = 'capability-lineage';
        
        // Load map data
        loadMapData(capabilityId);
    }




    // Render network using Cytoscape
    function renderNetwork(graph) {
        if (!CapabilityMapState.canvas) {
            console.error('[CAPABILITY-MAP] Canvas not available');
            return;
        }
        if (graphPipeline && graphPipeline.invalidateGraphCache) {
            graphPipeline.invalidateGraphCache();
        }

        // Clear canvas
        CapabilityMapState.canvas.innerHTML = '';

        if (!graph || !graph.nodes || graph.nodes.length === 0) {
            showPlaceholder('No capabilities to display.');
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
                ? (isSystem ? colors.currentSystem : colors.currentCapability)
                : (isSystem ? colors.otherSystem : colors.otherCapability);
            const borderColor = isCurrent
                ? (isSystem ? colors.currentSystemBorder : colors.currentCapabilityBorder)
                : (isSystem ? colors.otherSystemBorder : colors.otherCapabilityBorder);
            
            elements.push({
                data: {
                    id: node.id,
                    label: node.label,
                    nodeColor: nodeColor,
                    borderColor: borderColor,
                    isCurrent: isCurrent,
                    isCapability: node.group === 'capability',
                    isSystem: isSystem,
                    isLocked: node.isLocked || false,
                    lifecycle: node.lifecycle || node.meta?.lifecycle || '',
                    lifecycleName: node.lifecycleName || node.meta?.lifecycleName || node.lifecycle || '',
                    // Spread meta properties directly into data for easier access
                    ...(node.meta || {}),
                    // Also keep meta as nested object for compatibility
                    meta: node.meta || {}
                }
            });
        });
        
        // Collect node IDs that are current (for layout: reverse edges pointing TO current so dagre places it leftmost/topmost)
        const currentNodeIds = new Set(graph.nodes.filter(n => n.isCurrent).map(n => n.id));
        
        // Add edges
        graph.edges.forEach(edge => {
            const targetIsCurrent = currentNodeIds.has(edge.to);
            const sourceIsCurrent = currentNodeIds.has(edge.from);
            const reverseForLayout = targetIsCurrent && !sourceIsCurrent;
            const source = reverseForLayout ? edge.to : edge.from;
            const target = reverseForLayout ? edge.from : edge.to;
            elements.push({
                data: {
                    id: edge.id,
                    source: source,
                    target: target,
                    label: edge.label || '',
                    lineColor: colors.relationshipLine,
                    lineStyle: edge.lineStyle || 'solid',
                    lineType: edge.lineType || 'capability-lineage',
                    reversed: !!reverseForLayout
                },
                classes: reverseForLayout ? 'reversed-edge' : ''
            });
        });

        // Note: Dagre registration happens in init() function
        // If not available, buildCytoscapeLayout() will use breadthfirst as fallback

        // Find root nodes from graph data before creating network
        const rootNodeIds = findRootNodesFromGraph(graph);
        
        // Build layout configuration with root nodes
        const layoutConfig = buildCytoscapeLayout(rootNodeIds);
        
        // Create Cytoscape instance
        CapabilityMapState.network = cytoscape({
            container: CapabilityMapState.canvas,
            elements: elements,
            style: getCytoscapeStyle(),
            layout: layoutConfig,
            minZoom: 0.3,
            maxZoom: 3
        });

        // Setup event listeners
        setupEventListeners();
        
        // Setup overlay if enabled
        if (CapabilityMapState.overlay !== 'none') {
            loadOverlayData(CapabilityMapState.overlay);
        }
    }

    // Get Cytoscape styling - uses external styles module if available
    function getCytoscapeStyle() {
        // Try to use external styles module
        if (window.InterfaceMapStyles && typeof window.InterfaceMapStyles.getCytoscapeStyles === 'function') {
            const styles = window.InterfaceMapStyles.getCytoscapeStyles('capabilityRelationshipsMap');
            // Override background-image to only apply when the data field exists
            // This prevents warnings for capability nodes that don't have backgroundImage
            return styles.map(style => {
                if (style.selector === 'node' && style.style && style.style['background-image']) {
                    // Create a modified style that only applies background-image when data exists
                    return {
                        ...style,
                        style: {
                            ...style.style,
                            'background-image': undefined // Remove it from base node style
                        }
                    };
                }
                return style;
            }).concat([
                // Add a specific selector for nodes that DO have backgroundImage
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
                    'width': nodeSizes.capability.width,
                    'height': nodeSizes.capability.height
                }
            },
            {
                selector: 'node[?isCurrent]',
                style: {
                    'border-width': 3
                }
            },
            {
                selector: 'node[?isSystem]',
                style: {
                    'width': nodeSizes.system.width,
                    'height': nodeSizes.system.height
                }
            },
            {
                selector: 'node[?isLocked]',
                style: {
                    'background-color': colors.lockedSystem || '#6b7280',
                    'border-color': colors.lockedSystem || '#6b7280'
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
                    'curve-style': 'bezier',
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
                selector: 'edge[lineStyle = "solid"]',
                style: {
                    'line-style': 'solid'
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
        const layoutOption = CapabilityMapState.layout;
        
        // Get root nodes - use provided IDs or find from network
        const roots = rootNodeIds || adapter.findRootNodes();

        // Try to use external layout configuration
        if (window.InterfaceMapStyles && typeof window.InterfaceMapStyles.getLayoutConfig === 'function') {
            const layoutConfig = window.InterfaceMapStyles.getLayoutConfig(layoutOption);
            // Add roots for breadthfirst layouts
            if (layoutConfig.name === 'breadthfirst') {
                return { ...layoutConfig, roots: roots };
            }
            // If dagre is requested but not registered, fall back to breadthfirst silently
            if (layoutConfig.name === 'dagre' && !CapabilityMapState.dagreRegistered) {
                return {
                    name: 'breadthfirst',
                    directed: true,
                    padding: 60,
                    spacingFactor: 1.8,
                    animate: true,
                    animationDuration: 500,
                    nodeDimensionsIncludeLabels: true,
                    roots: roots
                };
            }
            return layoutConfig;
        }

        // Fallback inline layout configuration
        const baseLayout = {
            name: 'breadthfirst',
            directed: true,
            padding: 60,
            spacingFactor: 1.8,
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
                if (CapabilityMapState.dagreRegistered) {
                    return {
                        name: 'dagre',
                        rankDir: 'TB',
                        padding: 60,
                        spacingFactor: 1.5,
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
                    nodeRepulsion: 5000,
                    idealEdgeLength: 150,
                    edgeElasticity: 0.45,
                    nestingFactor: 0.1,
                    gravity: 0.25,
                    numIter: 1500,
                    initialEnergyOnIncremental: 0.3
                };
            default: // left-to-right
                if (CapabilityMapState.dagreRegistered) {
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

    // Find root nodes for layout (after network is created)
    function setupEventListeners() {
        networkIx.setupEventListeners();
    }

    // Show edge info
    function showEdgeInfo(edgeData) {
        embLog('[CAPABILITY-MAP] Edge clicked:', edgeData);
    }

    // Escape HTML helper
    function escapeHtml(str) {
        if (!str) return '';
        const div = document.createElement('div');
        div.textContent = str;
        return div.innerHTML;
    }

    // Fetch overlay data for a capability
    async function fetchOverlayDataForCapability(capabilityId, overlayType) {
        try {
            switch (overlayType) {
                case 'description':
                    // Get description from cached capability data
                    if (CapabilityMapState.capabilityData?.description) {
                        return [{ name: 'Description', value: CapabilityMapState.capabilityData.description }];
                    }
                    // Try to fetch from API
                    try {
                        const resp = await fetch(`/api/capabilities/${capabilityId}`, { credentials: 'include' });
                        if (resp.ok) {
                            const data = await resp.json();
                            const capability = data?.data || data;
                            if (capability?.description) {
                                return [{ name: 'Description', value: capability.description }];
                            }
                        }
                    } catch (e) { /* ignore */ }
                    return [];

                case 'stakeholders':
                    try {
                        // Use same API endpoint pattern as capability-stakeholder.js
                        const resp = await fetch(`/api/capability-stakeholder/${capabilityId}/stakeholders`, { credentials: 'include' });
                        if (resp.ok) {
                            const responseData = await resp.json();
                            // Handle different response formats (same as capability-stakeholder.js)
                            let data;
                            if (Array.isArray(responseData)) {
                                data = responseData;
                            } else if (responseData.data && Array.isArray(responseData.data)) {
                                data = responseData.data;
                            } else if (responseData.stakeholders && Array.isArray(responseData.stakeholders)) {
                                data = responseData.stakeholders;
                            } else if (responseData.result && Array.isArray(responseData.result)) {
                                data = responseData.result;
                            } else {
                                data = [];
                            }
                            return data;
                        }
                    } catch (e) { 
                        embWarn('[CAPABILITY-MAP] Error fetching stakeholders:', e);
                    }
                    return [];

                case 'glossary':
                    try {
                        // Get glossary terms from capability impact
                        const resp = await fetch(`/api/capability-impact/${capabilityId}/glossaries`, { credentials: 'include' });
                        if (resp.ok) {
                            const data = await resp.json();
                            return Array.isArray(data?.data) ? data.data : (Array.isArray(data) ? data : []);
                        }
                    } catch (e) { /* ignore */ }
                    return [];

                case 'systems':
                    try {
                        const resp = await fetch(`/api/capability-impact/${capabilityId}/systems`, { credentials: 'include' });
                        if (resp.ok) {
                            const data = await resp.json();
                            return Array.isArray(data?.data) ? data.data : (Array.isArray(data) ? data : []);
                        }
                    } catch (e) { /* ignore */ }
                    return [];

                case 'projects':
                    try {
                        const resp = await fetch(`/api/capability-impact/${capabilityId}/projects`, { credentials: 'include' });
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
            embWarn(`[CAPABILITY-MAP] Failed to fetch ${overlayType} for capability ${capabilityId}:`, error);
            return [];
        }
    }

    // Load overlay data for all visible nodes
    async function loadOverlayData(overlayType) {
        if (!CapabilityMapState.network) return;
        
        const nodes = CapabilityMapState.network.nodes();
        embLog('[CAPABILITY-MAP] Loading overlay data:', overlayType, 'for', nodes.length, 'nodes');
        
        try {
            const overlayData = new Map();
            
            // Get capability IDs and node IDs from nodes
            // Note: meta properties are spread directly into node data (see renderNetwork line 436)
            const nodeCapabilityPairs = nodes.map(n => {
                const nodeData = n.data();
                // Try direct access first (since meta is spread), then fallback to meta object
                const capabilityId = nodeData.capabilityId || (nodeData.meta && nodeData.meta.capabilityId);
                const nodeId = n.id();
                // Only include capability nodes (not system nodes from overlay)
                const isCapability = nodeData.isCapability !== false; // Default to true if not specified
                
                // Debug logging
                if (!capabilityId && isCapability) {
                    embWarn('[CAPABILITY-MAP] Node missing capabilityId:', nodeId, 'Data:', nodeData);
                }
                
                return { nodeId, capabilityId, isCapability };
            }).filter(pair => {
                // Only include pairs with valid capability IDs and that are capability nodes
                return pair.isCapability && pair.capabilityId && pair.capabilityId !== 'undefined' && pair.capabilityId !== null && pair.capabilityId !== '';
            });
            
            embLog('[CAPABILITY-MAP] Capability IDs for overlay:', nodeCapabilityPairs.map(p => p.capabilityId));
            embLog('[CAPABILITY-MAP] Node pairs:', nodeCapabilityPairs);
            
            // Fetch overlay data in parallel for all capabilities
            await Promise.all(nodeCapabilityPairs.map(async ({ nodeId, capabilityId }) => {
                try {
                    const data = await fetchOverlayDataForCapability(capabilityId, overlayType);
                    if (data && data.length > 0) {
                        // Map by nodeId for easier lookup during rendering
                        overlayData.set(nodeId, data);
                        embLog('[CAPABILITY-MAP] Capability', capabilityId, 'Node', nodeId, overlayType, ':', data.length, 'items');
                    }
                } catch (error) {
                    embWarn(`[CAPABILITY-MAP] Failed to load ${overlayType} for capability ${capabilityId}:`, error);
                }
            }));
            
            // Render overlay panels
            renderOverlayPanels(overlayType, overlayData);
            
        } catch (error) {
            console.error('[CAPABILITY-MAP] Error loading overlay data:', error);
        }
    }

    // Render overlay panels on nodes
    function renderOverlayPanels(overlayType, overlayData) {
        if (!CapabilityMapState.network || !CapabilityMapState.canvas) return;
        
        // Clear existing overlay panels
        clearOverlayPanels();
        
        // Create overlay container if it doesn't exist
        let overlayContainer = CapabilityMapState.canvas.querySelector('.map-overlay-container');
        if (!overlayContainer) {
            overlayContainer = document.createElement('div');
            overlayContainer.className = 'map-overlay-container';
            overlayContainer.style.cssText = 'position: absolute; top: 0; left: 0; width: 100%; height: 100%; pointer-events: none; z-index: 1000;';
            CapabilityMapState.canvas.appendChild(overlayContainer);
        }
        
        // Add panels for each node with overlay data
        CapabilityMapState.network.nodes().forEach(node => {
            const nodeId = node.id();
            const nodeData = node.data();
            
            // Get capability ID from node data (either directly or from meta)
            const capabilityId = nodeData.capabilityId || (nodeData.meta && nodeData.meta.capabilityId);
            
            // Only process capability nodes (not system nodes)
            if (!nodeData.isCapability || !capabilityId) return;
            
            // Get overlay data by nodeId (stored during loadOverlayData)
            const data = overlayData.get(nodeId);
            if (!data || data.length === 0) {
                // Try lookup by capabilityId as fallback
                const fallbackData = overlayData.get(String(capabilityId));
                if (!fallbackData || fallbackData.length === 0) return;
                // Use fallback data
                const nodeName = nodeData.label || 'Capability';
                const panel = createOverlayPanel(nodeName, overlayType, fallbackData, nodeId);
                if (panel) {
                    overlayContainer.appendChild(panel);
                    positionOverlayPanel(panel, node);
                    node.addClass('has-overlay');
                }
                return;
            }
            
            const nodeName = nodeData.label || 'Capability';
            
            // Create overlay panel
            const panel = createOverlayPanel(nodeName, overlayType, data, nodeId);
            if (!panel) return;
            overlayContainer.appendChild(panel);
            
            // Position panel
            positionOverlayPanel(panel, node);
            
            // Mark node as having overlay
            node.addClass('has-overlay');
        });
        
        // Add zoom/pan/drag listeners to update positions
        CapabilityMapState.network.on('zoom pan', updateOverlayPositions);
        CapabilityMapState.network.on('drag', 'node', updateOverlayPositions);
    }

    // Use global glossary-style overlay panel (header, body, footer Page X of Y; stakeholders = Name/Role, Accepted, Org Unit table)
    function createOverlayPanel(nodeName, overlayType, data, nodeId) {
        const arr = Array.isArray(data) ? data : [];
        if (arr.length === 0) return null;
        if (!window.MapOverlayPanel) {
            return createOverlayPanelFallback(nodeName, overlayType, arr, nodeId);
        }
        var overlayColumnDefs = [];
        if (overlayType !== 'stakeholders' && window.OverlayColumns) {
            var allCols = window.OverlayColumns.getOverlayColumns(overlayType);
            var selectedIds = CapabilityMapState.overlayColumnsByType[overlayType] || window.OverlayColumns.getDefaultOverlayColumnIds(overlayType);
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

    // Get overlay title
    function getOverlayTitle(overlayType) {
        const titles = {
            'description': 'Description',
            'stakeholders': 'Stakeholders',
            'glossary': 'Glossaries',
            'systems': 'Systems',
            'projects': 'Projects'
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
                // Handle different field name variations
                const role = item.roleName || item.role || item.RoleName || item.Role || item.role_name || '';
                const name = item.personName || item.name || item.PersonName || item.Name || item.person_name || item.person || item.Person || '';
                // If name is empty, try other common fields
                const displayName = name || item.primaryName || item.PrimaryName || item.fullName || item.FullName || '';
                return role && displayName ? `${displayName} (${role})` : (displayName || role || 'Unknown');
            case 'glossary': {
                const gName = item.glossary || item.name || '';
                const srcLabel = item.source === 'dataset' ? 'Dataset Glossary'
                   : item.source === 'attribute' ? 'Attribute Glossary' : '';
                return srcLabel ? `${gName} (${srcLabel})` : gName;
            }
            case 'systems':
                return item.systemName || item.name || item.Name || '';
            case 'projects':
                return item.projectName || item.PrimaryName || item.primaryName || item.name || item.Name || '';
            default:
                return item.name || item.primaryName || item.PrimaryName || item.Name || JSON.stringify(item);
        }
    }

    // Get overlay item ID
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
            case 'linking-attributes':
                switch (fieldId) {
                    case 'name': return v(item.name || item['Name attribute'] || item.attributeName || item.primaryName);
                    case 'type': return v(item.typeName || item.type);
                    case 'glossary': return v(item.glossaryName || item.glossary);
                    case 'refNumber': return v(item.refNumber || item.ref);
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

    // Position overlay panel above the node icon (same pattern as other maps)
    function positionOverlayPanel(panel, node) {
        const overlayContainer = CapabilityMapState.canvas?.querySelector('.map-overlay-container');
        const network = CapabilityMapState.network;
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

    // Update overlay positions on zoom/pan
    function updateOverlayPositions() {
        if (!CapabilityMapState.network || !CapabilityMapState.canvas) return;
        
        const overlayContainer = CapabilityMapState.canvas.querySelector('.map-overlay-container');
        if (!overlayContainer) return;
        
        const panels = overlayContainer.querySelectorAll('.map-node-overlay-panel');
        panels.forEach(panel => {
            const nodeId = panel.getAttribute('data-node-id');
            const node = CapabilityMapState.network.getElementById(nodeId);
            if (node.length) {
                positionOverlayPanel(panel, node);
            }
        });
    }

    // Clear overlay panels
    function clearOverlayPanels() {
        if (!CapabilityMapState.network) return;
        
        // Remove overlay container
        const overlayContainer = CapabilityMapState.canvas?.querySelector('.map-overlay-container');
        if (overlayContainer) {
            overlayContainer.innerHTML = '';
        }
        
        // Remove zoom/pan/drag listeners
        CapabilityMapState.network.off('zoom pan', updateOverlayPositions);
        CapabilityMapState.network.off('drag', 'node', updateOverlayPositions);
        
        // Reset node styles
        CapabilityMapState.network.nodes().forEach(node => {
            node.removeClass('has-overlay');
        });
    }

    // Highlight overlay item across the map
    function highlightOverlayItem(overlayType, item, sourceNodeId) {
        const overlayContainer = CapabilityMapState.canvas?.querySelector('.map-overlay-container');
        if (!overlayContainer) return;
        
        const itemId = getOverlayItemId(overlayType, item);
        const itemText = getOverlayItemText(overlayType, item);
        
        embLog('[CAPABILITY-MAP] Highlighting overlay item:', overlayType, 'Value:', itemText, 'ID:', itemId);
        
        // Remove previous highlights
        overlayContainer.querySelectorAll('.map-node-overlay-item').forEach(row => {
            row.classList.remove('highlighted-source', 'highlighted-related');
        });
        
        // Find and highlight matching items
        overlayContainer.querySelectorAll('.map-node-overlay-item').forEach(row => {
            const panel = row.closest('.map-node-overlay-panel');
            const isSource = panel?.getAttribute('data-node-id') === sourceNodeId;
            
            let shouldHighlight = false;
            
            // ID-based matching
            if (itemId) {
                const rowItemId = row.dataset.itemId ? String(row.dataset.itemId) : null;
                if (rowItemId && rowItemId === String(itemId)) {
                    shouldHighlight = true;
                }
            }
            
            // Value-based matching
            if (!shouldHighlight && row.dataset.overlayValue === itemText) {
                shouldHighlight = true;
            }
            
            if (shouldHighlight) {
                if (isSource) row.classList.add('highlighted-source');
                else row.classList.add('highlighted-related');
            }
        });
        applyOverlayRelatedNodeAndEdgeHighlights(overlayContainer, sourceNodeId);
    }

    function applyOverlayRelatedNodeAndEdgeHighlights(overlayContainer, sourceNodeId) {
        const network = CapabilityMapState.network;
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

    // Show loading indicator
    function showLoading() {
        if (CapabilityMapState.loadingEl) {
            CapabilityMapState.loadingEl.style.display = 'block';
        }
        if (CapabilityMapState.canvas) {
            CapabilityMapState.canvas.innerHTML = window.MapRenderUtils.htmlMapLoading();
        }
    }

    // Hide loading indicator
    function hideLoading() {
        if (CapabilityMapState.loadingEl) {
            CapabilityMapState.loadingEl.style.display = 'none';
        }
    }

    // Show placeholder message
    function showPlaceholder(message) {
        if (CapabilityMapState.canvas) {
            CapabilityMapState.canvas.innerHTML = window.MapRenderUtils.htmlMapCanvasMessage(message);
        }
    }

    // Set map type (only capability-lineage is supported)
    function setMapType(mapType) {
        if (mapType !== 'capability-lineage') {
            embWarn('[CAPABILITY-MAP] Only capability-lineage map type is supported');
            return;
        }
        CapabilityMapState.mapType = mapType;
        
        // Clear hidden nodes and focus state
        CapabilityMapState.hiddenNodes.clear();
        CapabilityMapState.focusedNode = null;
        CapabilityMapState.hiddenUpstreamNodes.clear();
        CapabilityMapState.hiddenDownstreamNodes.clear();
        
        const graph = buildCapabilityLineageGraph();
        renderNetwork(graph);
    }

    // Set layout
    function setLayout(layout) {
        CapabilityMapState.layout = layout;
        if (CapabilityMapState.network) {
            const rootNodeIds = adapter.findRootNodes();
            CapabilityMapState.network.layout(buildCytoscapeLayout(rootNodeIds)).run();
        }
    }

    function setHopsCount(count) {
        const val = Math.min(99, Math.max(1, parseInt(count, 10) || 15));
        CapabilityMapState.hopsCount = val;
        if (CapabilityMapState.network) {
            const graph = buildCapabilityLineageGraph();
            renderNetwork(graph);
        }
    }

    // Set overlay
    async function setOverlay(overlay) {
        embLog('[CAPABILITY-MAP] setOverlay called with:', overlay);
        CapabilityMapState.overlay = overlay;
        
        // Clear existing overlays
        clearOverlayPanels();
        
        if (overlay === 'none' || !overlay) {
            embLog('[CAPABILITY-MAP] Overlay cleared');
            return;
        }
        
        // Load and display overlay data
        embLog('[CAPABILITY-MAP] Loading overlay data for:', overlay);
        await loadOverlayData(overlay);
    }

    /** Set overlay columns (field ids) for an overlay type. Re-renders overlay if that type is active. */
    function setOverlayColumns(overlayType, columnIds) {
        if (!overlayType) return;
        CapabilityMapState.overlayColumnsByType[overlayType] = Array.isArray(columnIds) ? columnIds.slice() : [];
        if (CapabilityMapState.overlay === overlayType) {
            loadOverlayData(overlayType);
        }
    }

    /** Get selected overlay column ids for an overlay type. */
    function getOverlayColumns(overlayType) {
        return CapabilityMapState.overlayColumnsByType[overlayType] || (window.OverlayColumns ? window.OverlayColumns.getDefaultOverlayColumnIds(overlayType) : ['name']);
    }

    // Zoom in
    function zoomIn()  { adapter.zoomIn(); }
    function zoomOut() { adapter.zoomOut(); }

    // Reset map (fit to screen)
    function resetMap() {
        if (CapabilityMapState.network) {
            CapabilityMapState.network.fit(undefined, 50);
        }
    }

    // Redraw map
    function redrawMap() {
        const graph = buildCapabilityLineageGraph();
        renderNetwork(graph);
    }

    // Export to PNG (includes overlay panels when active)
    function exportAsPng() {
        if (!CapabilityMapState.network) return;
        const filename = `capability-map-${CapabilityMapState.capabilityId}.png`;
        if (typeof window.exportMapWithOverlays === 'function') {
            window.exportMapWithOverlays(CapabilityMapState.network, CapabilityMapState.canvas, filename);
        } else {
            const png = CapabilityMapState.network.png({ output: 'blob', bg: 'white', full: true });
            const url = URL.createObjectURL(png);
            const link = document.createElement('a');
            link.href = url;
            link.download = filename;
            link.click();
            URL.revokeObjectURL(url);
        }
    }

    // Toggle navigator (minimap)
    function toggleNavigator() {
        const container = CapabilityMapState.canvas && CapabilityMapState.canvas.parentElement;
        if (!container || !CapabilityMapState.network || !window.MapRenderUtils) return;
        window.MapRenderUtils.attachOrToggleLineageMinimap({
            container: container,
            network: CapabilityMapState.network,
            minimapCanvasId: 'capabilityRelationshipsMapMinimap',
            syncViewport: true,
            cytoscapeOptions: { minZoom: 0.1, maxZoom: 0.5 }
        });
    }

    // Open fullscreen in new tab with toolbar controls
    function openFullscreen() {
        if (!CapabilityMapState.network) return;
        if (typeof window.openMapFullscreen === 'function') {
            const nodes = CapabilityMapState.network.nodes().map(n => n.json());
            const edges = CapabilityMapState.network.edges().map(e => e.json());
            window.openMapFullscreen({
                title: 'Capability Map - ' + (CapabilityMapState.capabilityData?.name || CapabilityMapState.capabilityId),
                elements: { nodes: nodes, edges: edges },
                style: getCytoscapeStyle(),
                layoutName: CapabilityMapState.layout || 'top-to-bottom',
                legendHtml: getLegendHtml(),
                exportFilename: 'capability-map-' + CapabilityMapState.capabilityId + '.png',
                toolbarAnchor: CapabilityMapState.canvas,
                mapType: CapabilityMapState.mapType || 'capability-lineage',
                mapTabKind: 'capability-relationships',
                getState: function () { return CapabilityMapState; }
            });
        } else {
            const canvas = CapabilityMapState.canvas;
            if (canvas && canvas.requestFullscreen) {
                canvas.requestFullscreen();
            } else if (canvas && canvas.webkitRequestFullscreen) {
                canvas.webkitRequestFullscreen();
            }
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
                <div class="legend-color" style="background: ${colors.currentCapability || colors.currentSystem};"></div>
                <span>Current Capability</span>
            </div>
            <div class="legend-item">
                <div class="legend-color" style="background: ${colors.otherCapability || colors.otherSystem};"></div>
                <span>Related Capability</span>
            </div>
            ${lineageHtml}
            <div class="legend-item">
                <div class="legend-line" style="border-bottom: 2px solid ${colors.relationshipLine || colors.attributeLine || '#64748b'};"></div>
                <span>Capability Lineage</span>
            </div>
            <div class="legend-item">
                <div class="legend-line" style="border-bottom: 2px solid ${colors.upstreamHighlight || '#ef4444'};"></div>
                <span>Upstream</span>
            </div>
            <div class="legend-item">
                <div class="legend-line" style="border-bottom: 2px solid ${colors.downstreamHighlight || '#22c55e'};"></div>
                <span>Downstream</span>
            </div>
        `;
    }

    function updateLegend() {
        const legendContainer = document.querySelector('[data-capability-map-legend]');
        if (legendContainer) legendContainer.innerHTML = getLegendHtml();
        const dropdown = document.getElementById('capabilityRelationshipsMapLegendDropdown');
        if (dropdown) dropdown.innerHTML = getLegendHtml();
    }

    // Populate filter options from capability data
    async function populateFilterOptions() {
        try {
            // Extract unique relationship types from all capabilities
            const relationshipTypes = new Set();
            const lifecycles = new Set();
            
            // Extract lifecycles from capabilities
            CapabilityMapState.allCapabilities.forEach(cap => {
                const lifecycle = cap.lifecycleName || cap.LifecycleName || cap.lifecycle || cap.Lifecycle;
                if (lifecycle) lifecycles.add(String(lifecycle));
            });
            
            // Get relationships from API for all capabilities (limit to first 50 to avoid too many requests)
            const capabilitiesToCheck = CapabilityMapState.allCapabilities.slice(0, 50);
            await Promise.all(capabilitiesToCheck.map(async (cap) => {
                const capId = cap.id || cap.ID;
                try {
                    const relationships = await window.BUDG_API_SERVICE.getCapabilityRelationshipsBySourceId(capId);
                    const relationshipsList = relationships?.data || relationships || [];
                    relationshipsList.forEach(rel => {
                        const relType = rel.relationshipType || rel.Relationship_Type || rel.relationship_type;
                        if (relType) relationshipTypes.add(relType);
                    });
                } catch (e) {
                    // Ignore errors
                }
            }));
            
            const relArr = Array.from(relationshipTypes).sort();
            const lifeArr = Array.from(lifecycles).sort();
            CapabilityMapState.relationshipFilterUiActive = relArr.length > 0;
            CapabilityMapState.lifecycleFilterUiActive = lifeArr.length > 0;

            // Dispatch event to update filter options
            window.dispatchEvent(new CustomEvent('capabilityMapFilterOptionsUpdated', {
                detail: {
                    relationships: relArr,
                    lifecycles: lifeArr
                }
            }));
        } catch (error) {
            embWarn('[CAPABILITY-MAP] Failed to populate filter options:', error);
        }
    }

    // Set node filters
    function setNodeFilters(nodeFilters) {
        CapabilityMapState.nodeFilters = {
            relationships: nodeFilters.relationships || [],
            lifecycles: nodeFilters.lifecycles || []
        };
        applyNodeFiltersToNetwork();
    }

    function applyNodeFiltersToNetwork() {
        if (!CapabilityMapState.network) return;

        const net = CapabilityMapState.network;
        const { relationships, lifecycles } = CapabilityMapState.nodeFilters;
        const passFacet = window.MapGraphUtils && typeof window.MapGraphUtils.passesInitializedFacet === 'function'
            ? window.MapGraphUtils.passesInitializedFacet : null;

        net.nodes().forEach(node => {
            const nodeData = node.data();
            let shouldShow = true;

            if (CapabilityMapState.lifecycleFilterUiActive && passFacet) {
                const nodeLifecycle = nodeData.lifecycleName || nodeData.lifecycle || nodeData.Lifecycle || nodeData.LifecycleName || '';
                if (!passFacet(lifecycles, nodeLifecycle)) shouldShow = false;
            }

            if (shouldShow && CapabilityMapState.relationshipFilterUiActive) {
                if (!relationships || relationships.length === 0) {
                    shouldShow = false;
                } else {
                    const typesOnNode = new Set();
                    node.connectedEdges().forEach(e => {
                        const lab = String(e.data('label') || '').trim();
                        if (lab) typesOnNode.add(lab);
                    });
                    if (typesOnNode.size === 0) {
                        shouldShow = true;
                    } else {
                        let hit = false;
                        typesOnNode.forEach(t => {
                            if (relationships.indexOf(t) !== -1) hit = true;
                        });
                        shouldShow = hit;
                    }
                }
            }

            node.style('display', shouldShow ? 'element' : 'none');
        });

        net.edges().forEach(edge => {
            const sv = edge.source().style('display') !== 'none';
            const tv = edge.target().style('display') !== 'none';
            edge.style('display', (sv && tv) ? 'element' : 'none');
        });

        const rootNodeIds = adapter.findRootNodes();
        net.layout(buildCytoscapeLayout(rootNodeIds)).run();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Graph pipeline + MapEngine (shared/map/capability-facet-lineage-graph.js)
    // ─────────────────────────────────────────────────────────────────────────

    graphPipeline = window.createCapabilityFacetLineageGraphPipeline({
        state: CapabilityMapState,
        adapter: adapter,
        renderNetwork: renderNetwork,
        showPlaceholder: showPlaceholder,
        showLoading: showLoading,
        hideLoading: hideLoading,
        updateLegend: updateLegend,
        populateFilterOptions: populateFilterOptions,
        logPrefix: '[CAPABILITY-MAP]'
    });

    var _mapEngineApiLoader = graphPipeline._mapEngineApiLoader;
    var _mapEngineNodeBuilder = graphPipeline._mapEngineNodeBuilder;
    var _mapEngineEdgeBuilder = graphPipeline._mapEngineEdgeBuilder;

    networkIx = window.createCapabilityLineageNetworkInteractions({
        state: CapabilityMapState,
        getMapColors: getMapColors,
        buildCapabilityLineageGraph: buildCapabilityLineageGraph,
        renderNetwork: renderNetwork,
        showEdgeInfo: showEdgeInfo,
        getNodeIdFromCapability: graphPipeline.getNodeIdFromCapability,
        updateOverlayPositions: updateOverlayPositions
    });

    if (window.MapConfigs && window.MapConfigs.register) {
        window.MapConfigs.register('capability-lineage', {
            apiLoader:     _mapEngineApiLoader,
            nodeBuilder:   _mapEngineNodeBuilder,
            edgeBuilder:   _mapEngineEdgeBuilder,
            legendBuilder: getLegendHtml
        });
    }

    function getCapabilityRelationshipsMapSubtabHtml() {
        return `                            <div class="map-section" id="capabilityRelationshipsMapSection" style="grid-column:1/-1; margin-bottom: 1.5rem;">
                                <div class="map-section-header">
                                    <div class="map-section-title">MAP</div>
                                    <button type="button" class="map-collapse-btn" id="capabilityRelationshipsMapCollapseBtn" title="Collapse/Expand">
                                        <i class="fas fa-minus"></i>
                                    </button>
                                </div>
                                <!-- Map Toolbar -->
                                <div class="interface-map-toolbar">
                                    <!-- Map Type -->
                                    <div class="map-control-group">
                                        <label>Map type:</label>
                                        <select id="capabilityRelationshipsMapTypeSelect" class="map-select">
                                            <option value="capability-lineage" selected>Capability Lineage</option>
                                        </select>
                                    </div>
                                    
                                    <!-- Layout -->
                                    <div class="map-control-group">
                                        <label>Layout:</label>
                                        <div class="map-layout-controls">
                                            ${typeof window.SharedMapLayoutRichControlsHtml === 'function' ? window.SharedMapLayoutRichControlsHtml('capabilityRelationshipsMap', { richItems: 'four' }) : ''}
                                        </div>
                                    </div>
                                    <div class="map-control-group map-hops-group" id="capabilityRelationshipsMapHopsGroup">
                                        <label>Hops:</label>
                                        <input type="number" id="capabilityRelationshipsMapHopsCount" class="map-hops-input" min="1" max="99" value="15" title="Upstream/downstream lineage depth (1-99, recommend 15)">
                                    </div>
                                    
                                    <!-- Overlay -->
                                    <div class="map-control-group">
                                        <label>Overlay:</label>
                                        <div class="map-overlay-controls">
                                            <div class="map-overlay-dropdown">
                                                <button type="button" class="map-select-btn" id="capabilityRelationshipsMapOverlayBtn">
                                                    <span id="capabilityRelationshipsMapOverlayBtnText">None</span>
                                                    <i class="fas fa-chevron-down"></i>
                                                </button>
                                                <!-- Capability Lineage Overlay Menu -->
                                                <div class="map-overlay-menu" id="capabilityRelationshipsMapOverlayMenuCapability" data-map-type="capability-lineage">
                                                    <div class="overlay-menu-grid">
                                                        <div class="overlay-menu-column">
                                                            <div class="overlay-menu-header">Data</div>
                                                            <div class="overlay-menu-item" data-overlay="description">
                                                                <i class="fas fa-info-circle"></i> Description
                                                            </div>
                                                            <div class="overlay-menu-item" data-overlay="glossary">
                                                                <i class="fas fa-book"></i> Glossary
                                                            </div>
                                                            <div class="overlay-menu-item" data-overlay="systems">
                                                                <i class="fas fa-server"></i> Systems
                                                            </div>
                                                        </div>
                                                        <div class="overlay-menu-column">
                                                            <div class="overlay-menu-header">Business</div>
                                                            <div class="overlay-menu-item" data-overlay="stakeholders">
                                                                <i class="fas fa-users"></i> Stakeholders
                                                            </div>
                                                            <div class="overlay-menu-item" data-overlay="projects">
                                                                <i class="fas fa-project-diagram"></i> Projects
                                                            </div>
                                                        </div>
                                                    </div>
                                                    <div class="overlay-menu-footer">
                                                        <button type="button" class="overlay-clear-btn" id="capabilityRelationshipsMapClearOverlaysBtnCapability">Clear Overlays</button>
                                                    </div>
                                                </div>
                                            </div>
                                            <button type="button" class="map-toolbar-btn-sm" id="capabilityRelationshipsMapOverlayGrid" title="Overlay fields as columns">
                                                <i class="fas fa-th"></i>
                                                <i class="fas fa-chevron-down" style="font-size: 8px; margin-left: 2px;"></i>
                                            </button>
                                            <div class="map-overlay-columns-menu map-filter-menu" id="capabilityRelationshipsMapOverlayColumnsMenu" style="display: none;">
                                                <div class="map-filter-category">
                                                    <div class="map-filter-category-header">OVERLAY FIELDS AS COLUMNS</div>
                                                    <div id="capabilityRelationshipsMapOverlayColumnsOptions" class="map-filter-options-container"></div>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                    
                                    <!-- Filters -->
                                    <div class="map-control-group">
                                        <label>Filters:</label>
                                        <div class="map-filter-dropdown">
                                            <button type="button" class="map-select-btn" id="capabilityRelationshipsMapFilterBtn">
                                                <span id="capabilityRelationshipsMapFilterBtnText">All selected (2)</span>
                                                <i class="fas fa-chevron-down"></i>
                                            </button>
                                            <!-- Capability Lineage Filter Menu -->
                                            <div class="map-filter-menu" id="capabilityRelationshipsMapFilterMenuCapability" data-map-type="capability-lineage">
                                                <div class="map-filter-category">
                                                    <div class="map-filter-category-header">CAPABILITY</div>
                                                    <div class="map-filter-option">
                                                        <input type="checkbox" id="filterCapabilityLineage" checked>
                                                        <label for="filterCapabilityLineage">Capability Lineage</label>
                                                    </div>
                                                </div>
                                                <div class="map-filter-category">
                                                    <div class="map-filter-category-header">RELATIONSHIPS</div>
                                                    <div id="capabilityRelationshipsMapFilterRelationshipOptions" class="map-filter-options-container">
                                                        <!-- Dynamically populated -->
                                                    </div>
                                                </div>
                                                <div class="map-filter-category">
                                                    <div class="map-filter-category-header">LIFECYCLE</div>
                                                    <div id="capabilityRelationshipsMapFilterLifecycleOptions" class="map-filter-options-container">
                                                        <!-- Dynamically populated -->
                                                    </div>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                    
                                    <!-- Toolbar Buttons -->
                                    <div class="map-toolbar-buttons">
                                        <button type="button" class="map-toolbar-btn" id="capabilityRelationshipsMapToggleLabels" title="Toggle labels">
                                            <i class="fas fa-exchange-alt"></i>
                                        </button>
                                        <div class="map-toolbar-separator"></div>
                                        <button type="button" class="map-toolbar-btn" id="capabilityRelationshipsMapZoomIn" title="Zoom in">
                                            <i class="fas fa-search-plus"></i>
                                        </button>
                                        <button type="button" class="map-toolbar-btn" id="capabilityRelationshipsMapZoomOut" title="Zoom out">
                                            <i class="fas fa-search-minus"></i>
                                        </button>
                                        <div class="map-toolbar-separator"></div>
                                        <button type="button" class="map-toolbar-btn" id="capabilityRelationshipsMapRedraw" title="Redraw">
                                            <i class="fas fa-sync-alt"></i>
                                        </button>
                                        <button type="button" class="map-toolbar-btn" id="capabilityRelationshipsMapReset" title="Reset">
                                            <i class="fas fa-undo"></i>
                                        </button>
                                        <button type="button" class="map-toolbar-btn" id="capabilityRelationshipsMapExport" title="Export as PNG">
                                            <i class="fas fa-save"></i>
                                        </button>
                                        <div class="map-toolbar-separator"></div>
                                        <button type="button" class="map-toolbar-btn" id="capabilityRelationshipsMapNavigator" title="Map navigator">
                                            <i class="fas fa-eye"></i>
                                        </button>
                                        <button type="button" class="map-toolbar-btn" id="capabilityRelationshipsMapFullscreen" title="Fullscreen">
                                            <i class="fas fa-external-link-alt"></i>
                                        </button>
                                        <button type="button" class="map-toolbar-btn" id="capabilityRelationshipsMapLegend" title="Open the Legend" aria-label="Open the Legend – refer to the legend to identify the symbols used in the map (Insight Maps Palette)">
                                            <i class="fas fa-list-ul"></i>
                                        </button>
                                    </div>
                                </div>
                                <!-- Map Body -->
                                <div class="interface-map-body">
                                    <div class="interface-map-canvas" id="capabilityRelationshipsMapCanvas" style="width: 100%; height: 600px; position: relative;">
                                        <div class="interface-map-loading" data-capability-map-loading style="display:none;">
                                            <i class="fas fa-spinner fa-spin"></i>
                                            <span>Building lineage map...</span>
                                        </div>
                                    </div>
                                    <div class="interface-map-side-panel" id="capabilityRelationshipsMapSidePanel" style="display:none;">
                                        <div class="map-side-panel-section">
                                            <h4><i class="fas fa-info-circle"></i> Selection</h4>
                                            <div class="selection-placeholder" data-capability-map-placeholder>
                                                Select a node to see its details.
                                            </div>
                                            <div class="selection-info" data-capability-map-details style="display:none;"></div>
                                        </div>
                                        <div class="map-side-panel-section">
                                            <h4 class="map-legend-palette-title"><i class="fas fa-layer-group"></i> Insight Maps Palette</h4>
                                            <div data-capability-map-legend></div>
                                        </div>
                                    </div>
                                </div>
                            </div>`;
    }

    function initCapabilityMapToolbar() {
        var mapId = "capabilityRelationshipsMap";
        var mapRoot = document.getElementById(mapId + "Section") || document;
        if (mapRoot.dataset && mapRoot.dataset.capabilityMapToolbarWired === "1") {
            return;
        }
        if (mapRoot.dataset) {
            mapRoot.dataset.capabilityMapToolbarWired = "1";
        }
        var byId = function (id) {
            return mapRoot.querySelector("#" + id) || document.getElementById(id);
        };
        var esc = function (t) {
            return (window.MapRenderUtils && window.MapRenderUtils.escapeHtml) ? window.MapRenderUtils.escapeHtml(t) : String(t == null ? "" : t);
        };

        // Map type selector (only capability-lineage)
        const mapTypeSelect = byId(`${mapId}TypeSelect`);
        
        // Hops Count (1-99, default 15)
        const hopsInput = byId(`${mapId}HopsCount`);
        if (hopsInput) {
            hopsInput.addEventListener('change', (e) => {
                let val = parseInt(e.target.value, 10);
                if (isNaN(val) || val < 1) val = 1;
                if (val > 99) val = 99;
                e.target.value = val;
                if (window.CapabilityRelationshipsMap && typeof window.CapabilityRelationshipsMap.setHopsCount === 'function') {
                    window.CapabilityRelationshipsMap.setHopsCount(val);
                }
            });
        }

        // Initialize shared dropdown menus for expand/collapse & direction buttons
        if (typeof window.SharedMapDropdowns === 'function') {
            window.SharedMapDropdowns({
                mapId: mapId,
                getNetwork: () => window.CapabilityRelationshipsMap ? window.CapabilityRelationshipsMap.cy : null,
                setLayout: (dir) => {
                    const ls = byId(`${mapId}LayoutSelect`);
                    if (ls) ls.value = dir;
                    if (window.CapabilityRelationshipsMap) window.CapabilityRelationshipsMap.setLayout(dir);
                },
                getCanvas: () => byId(mapId + 'Canvas')
            });
        }

        // Collapse/Expand section button
        const collapseBtn = byId(`${mapId}CollapseBtn`);
        const mapSection = byId(`${mapId}Section`);
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
        const overlayBtn = byId(`${mapId}OverlayBtn`);
        const overlayMenu = byId(`${mapId}OverlayMenuCapability`);
        if (overlayBtn && overlayMenu) {
            overlayBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                overlayMenu.classList.toggle('open');
            });
        }

        // Setup overlay menu item click handlers
        if (overlayMenu) {
            overlayMenu.querySelectorAll('.overlay-menu-item').forEach(item => {
                item.addEventListener('click', (e) => {
                    e.preventDefault();
                    e.stopPropagation();
                    
                    const overlayType = e.currentTarget.getAttribute('data-overlay');
                    const wasActive = e.currentTarget.classList.contains('active');
                    
                    overlayMenu.querySelectorAll('.overlay-menu-item').forEach(i => i.classList.remove('active'));
                    
                    if (!wasActive) {
                        e.currentTarget.classList.add('active');
                        if (overlayBtn) {
                            overlayBtn.querySelector('span').textContent = e.currentTarget.textContent.trim();
                        }
                        if (window.CapabilityRelationshipsMap) {
                            window.CapabilityRelationshipsMap.setOverlay(overlayType);
                        }
                    } else {
                        if (overlayBtn) {
                            overlayBtn.querySelector('span').textContent = 'None';
                        }
                        if (window.CapabilityRelationshipsMap) {
                            window.CapabilityRelationshipsMap.setOverlay('none');
                        }
                    }
                    
                    overlayMenu.classList.remove('open');
                });
            });
        }

        // Clear overlays button
        const clearOverlaysBtn = byId(`${mapId}ClearOverlaysBtnCapability`);
        if (clearOverlaysBtn) {
            clearOverlaysBtn.addEventListener('click', () => {
                mapRoot.querySelectorAll('.overlay-menu-item').forEach(i => i.classList.remove('active'));
                if (overlayBtn) overlayBtn.querySelector('span').textContent = 'None';
                if (window.CapabilityRelationshipsMap) {
                    window.CapabilityRelationshipsMap.setOverlay('none');
                }
                if (overlayMenu) overlayMenu.classList.remove('open');
            });
        }

        // Overlay grid button: overlay fields as columns dropdown
        const overlayGridBtn = byId(`${mapId}OverlayGrid`);
        const overlayColumnsMenuEl = byId(`${mapId}OverlayColumnsMenu`);
        const overlayColumnsOptions = byId(`${mapId}OverlayColumnsOptions`);
        if (overlayGridBtn && overlayColumnsMenuEl && overlayColumnsOptions) {
            function populateOverlayColumnsMenu() {
                overlayColumnsOptions.innerHTML = '';
                const overlayType = window.CapabilityRelationshipsMap && window.CapabilityRelationshipsMap.getState ? (window.CapabilityRelationshipsMap.getState().overlay || '') : '';
                if (!overlayType || overlayType === 'none') {
                    overlayColumnsOptions.innerHTML = '<div class="map-overlay-columns-empty">Select an overlay first.</div>';
                    return;
                }
                const columns = window.OverlayColumns && window.OverlayColumns.getOverlayColumns ? window.OverlayColumns.getOverlayColumns(overlayType) : [];
                const selectedIds = window.CapabilityRelationshipsMap && typeof window.CapabilityRelationshipsMap.getOverlayColumns === 'function' ? window.CapabilityRelationshipsMap.getOverlayColumns(overlayType) : [];
                if (!columns || columns.length === 0) {
                    overlayColumnsOptions.innerHTML = '<div class="map-overlay-columns-empty">No columns for this overlay.</div>';
                    return;
                }
                columns.forEach(col => {
                    const div = document.createElement('div');
                    div.className = 'map-filter-option';
                    const input = document.createElement('input');
                    input.type = 'checkbox';
                    input.id = 'overlayCol_capability_' + overlayType + '_' + col.id;
                    input.checked = selectedIds.indexOf(col.id) !== -1;
                    input.dataset.columnId = col.id;
                    const label = document.createElement('label');
                    label.htmlFor = input.id;
                    label.textContent = col.label;
                    div.appendChild(input);
                    div.appendChild(label);
                    input.addEventListener('change', () => {
                        const checked = Array.from(overlayColumnsOptions.querySelectorAll('input:checked')).map(i => i.dataset.columnId);
                        if (window.CapabilityRelationshipsMap && typeof window.CapabilityRelationshipsMap.setOverlayColumns === 'function') {
                            window.CapabilityRelationshipsMap.setOverlayColumns(overlayType, checked);
                        }
                    });
                    overlayColumnsOptions.appendChild(div);
                });
            }
            overlayGridBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                if (filterMenu) filterMenu.classList.remove('open');
                if (overlayMenu) overlayMenu.classList.remove('open');
                const isOpen = overlayColumnsMenuEl.classList.toggle('open');
                if (isOpen) {
                    overlayColumnsMenuEl.style.display = 'block';
                    populateOverlayColumnsMenu();
                    const rect = overlayGridBtn.getBoundingClientRect();
                    overlayColumnsMenuEl.style.position = 'fixed';
                    overlayColumnsMenuEl.style.left = rect.left + 'px';
                    overlayColumnsMenuEl.style.top = (rect.bottom + 4) + 'px';
                    overlayColumnsMenuEl.style.minWidth = '200px';
                } else {
                    overlayColumnsMenuEl.style.display = 'none';
                }
                overlayGridBtn.classList.toggle('active', isOpen);
            });
        }

        // Filter dropdown
        const filterBtn = byId(`${mapId}FilterBtn`);
        const filterMenu = byId(`${mapId}FilterMenuCapability`);
        if (filterBtn && filterMenu) {
            filterBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                filterMenu.classList.toggle('open');
                if (overlayMenu) overlayMenu.classList.remove('open');
            });
        }

        // Listen for dynamic filter options from map
        window.addEventListener('capabilityMapFilterOptionsUpdated', (event) => {
            const { relationships, lifecycles } = event.detail || {};
            
            // Update Relationship filter options
            const relationshipContainer = byId('capabilityRelationshipsMapFilterRelationshipOptions');
            if (relationshipContainer && relationships && relationships.length > 0) {
                relationshipContainer.innerHTML = relationships.map((rel, idx) => `
                    <div class="map-filter-option">
                        <input type="checkbox" id="filterCapabilityRelationship_${idx}" data-filter-type="relationship" data-filter-value="${esc(rel)}" checked>
                        <label for="filterCapabilityRelationship_${idx}">${esc(rel)}</label>
                    </div>
                `).join('');
                
                // Add event listeners to new checkboxes
                relationshipContainer.querySelectorAll('input[type="checkbox"]').forEach(checkbox => {
                    checkbox.addEventListener('change', () => {
                        applyCapabilityNodeFilters();
                        updateCapabilityFilterLabel();
                    });
                });
            } else if (relationshipContainer) {
                relationshipContainer.innerHTML = '<div class="map-filter-option" style="color: var(--text-muted, #6b7280); font-size: 0.75rem; padding: 0.25rem 0.75rem;">No relationships available</div>';
            }
            
            // Update Lifecycle filter options
            const lifecycleContainer = byId('capabilityRelationshipsMapFilterLifecycleOptions');
            if (lifecycleContainer && lifecycles && lifecycles.length > 0) {
                lifecycleContainer.innerHTML = lifecycles.map((lc, idx) => `
                    <div class="map-filter-option">
                        <input type="checkbox" id="filterCapabilityLifecycle_${idx}" data-filter-type="lifecycle" data-filter-value="${esc(lc)}" checked>
                        <label for="filterCapabilityLifecycle_${idx}">${esc(lc)}</label>
                    </div>
                `).join('');
                
                // Add event listeners to new checkboxes
                lifecycleContainer.querySelectorAll('input[type="checkbox"]').forEach(checkbox => {
                    checkbox.addEventListener('change', () => {
                        applyCapabilityNodeFilters();
                        updateCapabilityFilterLabel();
                    });
                });
            } else if (lifecycleContainer) {
                lifecycleContainer.innerHTML = '<div class="map-filter-option" style="color: var(--text-muted, #6b7280); font-size: 0.75rem; padding: 0.25rem 0.75rem;">No lifecycles available</div>';
            }
            
            // Update filter label count
            updateCapabilityFilterLabel();
            applyCapabilityNodeFilters();
        });

        // Apply node filters based on relationships and lifecycle
        function applyCapabilityNodeFilters() {
            const selectedRelationships = [];
            const selectedLifecycles = [];
            
            mapRoot.querySelectorAll('#capabilityRelationshipsMapFilterRelationshipOptions input:checked').forEach(cb => {
                selectedRelationships.push(cb.dataset.filterValue);
            });
            mapRoot.querySelectorAll('#capabilityRelationshipsMapFilterLifecycleOptions input:checked').forEach(cb => {
                selectedLifecycles.push(cb.dataset.filterValue);
            });
            
            if (window.CapabilityRelationshipsMap) {
                window.CapabilityRelationshipsMap.setNodeFilters({
                    relationships: selectedRelationships,
                    lifecycles: selectedLifecycles
                });
            }
        }

        // Update filter label
        function updateCapabilityFilterLabel() {
            const filterBtnText = byId(`${mapId}FilterBtnText`);
            if (!filterBtnText) return;
            
            const checkboxes = filterMenu.querySelectorAll('input[type="checkbox"]');
            const checkedCount = Array.from(checkboxes).filter(c => c.checked && c.closest('.map-filter-option')?.style.display !== 'none').length;
            const totalCount = Array.from(checkboxes).filter(c => c.closest('.map-filter-option')?.style.display !== 'none').length;
            
            if (totalCount === 0) {
                filterBtnText.textContent = 'No filters';
            } else if (checkedCount === totalCount) {
                filterBtnText.textContent = `All selected (${totalCount})`;
            } else {
                filterBtnText.textContent = `${checkedCount} of ${totalCount}`;
            }
        }

        // Toolbar buttons
        const zoomInBtn = byId(`${mapId}ZoomIn`);
        if (zoomInBtn) {
            zoomInBtn.addEventListener('click', () => {
                if (window.CapabilityRelationshipsMap) window.CapabilityRelationshipsMap.zoomIn();
            });
        }

        const zoomOutBtn = byId(`${mapId}ZoomOut`);
        if (zoomOutBtn) {
            zoomOutBtn.addEventListener('click', () => {
                if (window.CapabilityRelationshipsMap) window.CapabilityRelationshipsMap.zoomOut();
            });
        }

        const redrawBtn = byId(`${mapId}Redraw`);
        if (redrawBtn) {
            redrawBtn.addEventListener('click', () => {
                if (window.CapabilityRelationshipsMap) window.CapabilityRelationshipsMap.redrawMap();
            });
        }

        const resetBtn = byId(`${mapId}Reset`);
        if (resetBtn) {
            resetBtn.addEventListener('click', () => {
                if (window.CapabilityRelationshipsMap) window.CapabilityRelationshipsMap.resetMap();
            });
        }

        const exportBtn = byId(`${mapId}Export`);
        if (exportBtn) {
            exportBtn.addEventListener('click', () => {
                if (window.CapabilityRelationshipsMap) window.CapabilityRelationshipsMap.exportAsPng();
            });
        }

        const fullscreenBtn = byId(`${mapId}Fullscreen`);
        if (fullscreenBtn) {
            fullscreenBtn.addEventListener('click', () => {
                if (window.CapabilityRelationshipsMap && window.CapabilityRelationshipsMap.openFullscreen) {
                    window.CapabilityRelationshipsMap.openFullscreen();
                }
            });
        }

        const legendBtn = byId(`${mapId}Legend`);
        if (legendBtn && typeof window.setupMapLegendDropdown === 'function') {
            window.setupMapLegendDropdown(mapId + 'Legend', mapId + 'LegendDropdown', function() {
                return window.CapabilityRelationshipsMap && window.CapabilityRelationshipsMap.getLegendHtml ? window.CapabilityRelationshipsMap.getLegendHtml() : '';
            });
        } else if (legendBtn) {
            const sidePanel = byId(`${mapId}SidePanel`);
            legendBtn.addEventListener('click', () => {
                if (sidePanel) {
                    const isVisible = sidePanel.style.display !== 'none';
                    sidePanel.style.display = isVisible ? 'none' : 'flex';
                    legendBtn.classList.toggle('active', !isVisible);
                }
            });
        }

        const navigatorBtn = byId(`${mapId}Navigator`);
        if (navigatorBtn) {
            navigatorBtn.addEventListener('click', () => {
                navigatorBtn.classList.toggle('active');
                if (window.CapabilityRelationshipsMap && window.CapabilityRelationshipsMap.toggleNavigator) {
                    window.CapabilityRelationshipsMap.toggleNavigator();
                }
            });
        }

        const toggleLabelsBtn = byId(`${mapId}ToggleLabels`);
        if (toggleLabelsBtn) {
            toggleLabelsBtn.addEventListener('click', () => {
                toggleLabelsBtn.classList.toggle('active');
                // Toggle labels functionality can be added if needed
            });
        }

        // Close dropdowns when clicking outside
        document.addEventListener('click', (e) => {
            if (filterBtn && filterMenu && !filterBtn.contains(e.target) && !filterMenu.contains(e.target)) {
                filterMenu.classList.remove('open');
            }
            if (overlayBtn && overlayMenu && !overlayBtn.contains(e.target) && !overlayMenu.contains(e.target)) {
                overlayMenu.classList.remove('open');
            }
            if (overlayColumnsMenuEl && overlayGridBtn && !overlayColumnsMenuEl.contains(e.target) && !overlayGridBtn.contains(e.target)) {
                overlayColumnsMenuEl.classList.remove('open');
                overlayColumnsMenuEl.style.display = 'none';
                overlayGridBtn.classList.remove('active');
            }
        });
    }

    var _capabilityRelationshipsEngine = new window.MapEngine('capability-lineage');

    Object.assign(_capabilityRelationshipsEngine, {
        init: init,
        loadMapData: loadMapData,
        setMapType: setMapType,
        setLayout: setLayout,
        setHopsCount: setHopsCount,
        setOverlay: setOverlay,
        setOverlayColumns: setOverlayColumns,
        getOverlayColumns: getOverlayColumns,
        setNodeFilters: setNodeFilters,
        zoomIn: zoomIn,
        zoomOut: zoomOut,
        resetMap: resetMap,
        redrawMap: redrawMap,
        exportAsPng: exportAsPng,
        openFullscreen: openFullscreen,
        toggleNavigator: toggleNavigator,
        getLegendHtml: getLegendHtml,
        updateLegend: updateLegend,
        getState: function () { return CapabilityMapState; },
        initToolbar: function () { initCapabilityMapToolbar(); }
    });

    Object.defineProperty(_capabilityRelationshipsEngine, 'cy', {
        get: function () { return CapabilityMapState.network; },
        configurable: true
    });

    window.CapabilityRelationshipsMap = _capabilityRelationshipsEngine;

    window.getCapabilityRelationshipsMapSubtabHtml = getCapabilityRelationshipsMapSubtabHtml;

})();
