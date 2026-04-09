/**
 * Glossary Relationships Map Component
 * Implements Relationships Map for Glossary facet Relationships tab > Map sub-tab
 * 
 * Map Types:
 * - Glossary Lineage: Shows full hierarchy (entire connected component of relationship graph)
 *   - Orange: opened glossary object
 *   - Shows all related glossaries reachable by any relationship path (e.g. Customer Data, x, Total Revenue, Customer Data_Proj, gloo, Products)
 *   - When Customer Data_Proj is opened, the map still shows Total Revenue and all other connected glossaries
 * - Dataset Lineage: Datasets linked with the opened glossary (orange) + other datasets (connected via relationships)
 *   - Linked datasets: (1) dataset directly associated with opened glossary, (2) one of its attributes is associated with it, (3) dataset's glossary is in the hierarchy of the opened glossary
 * - System Lineage: Systems linked with the opened glossary (orange) + other systems (connected via interfaces/data flow). Linked = strategic source, or system owns linked dataset/attribute or dataset whose glossary is in hierarchy.
 * 
 * Overlays:
 * - Glossary Lineage: Only Description and Stakeholders (Stakeholders has 2 extra columns: accepted, org unit). Filter uses glossary_x_glossary_relationtype.
 * - Dataset Lineage: Description, Glossary, Attributes
 * - System Lineage: Description, Glossary, Data Sets, Stakeholders, Projects
 * 
 * Filters:
 * - Glossary Lineage: Relationship Type (from glossary_x_glossary_relationtype table)
 * - Dataset Lineage: Type, Lifecycle
 * - System Lineage: Classification, Type, Lifecycle
 * 
 * Dependencies:
 * - shared/map/map-styles.js, map-icons.js (InterfaceMapStyles / InterfaceMapIcons)
 * - shared/map/glossary-relationships-facet-graph.js (createGlossaryRelationshipsFacetGraphPipeline)
 * - shared/map/system-lineage-network-interactions.js (createGlossaryRelationshipsMapNetworkInteractions)
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

    var GLOSSARY_REL_MAP_COLOR_FALLBACK = {
        currentGlossary: '#f97316',
        currentGlossaryBorder: '#ea580c',
        otherGlossary: '#1f2937',
        otherGlossaryBorder: '#374151',
        currentDataset: '#f97316',
        currentDatasetBorder: '#ea580c',
        otherDataset: '#1f2937',
        otherDatasetBorder: '#374151',
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
        return window.MapRenderUtils.getMapColors(GLOSSARY_REL_MAP_COLOR_FALLBACK);
    }

    // Map State Management
    const GlossaryRelationshipsMapState = {
        initialized: false,
        dagreRegistered: false,
        network: null,
        canvas: null,
        loadingEl: null,
        glossaryId: null,
        glossaryData: null,
        layout: 'top-to-bottom',
        mapType: 'glossary-lineage', // Default to glossary-lineage
        overlay: 'none',
        overlayColumnsByType: {},
        hopsCount: 15, // 1-99, Axon recommends 15 for lineage
        filters: {},
        nodeFilters: {
            relationshipTypes: [], // For glossary lineage
            classifications: [],
            types: [],
            lifecycles: []
        },
        datasetNodeFilters: {
            types: [],
            lifecycles: []
        },
        hiddenNodes: new Set(),
        focusedNode: null,
        // Glossary lineage state
        allGlossaries: [],
        glossaryRelationships: [], // All relationships from glossary_x_glossary
        relationshipTypes: [], // From glossary_x_glossary_relationtype
        linearGlossaries: [], // Glossaries to show (only linear path, no siblings)
        // Dataset lineage state
        linkedDatasets: new Set(), // Datasets linked with opened glossary
        datasetsData: new Map(),
        datasetRelationships: [],
        // System lineage state
        linkedSystems: new Set(), // Systems linked with opened glossary
        systemsData: new Map(),
        interfacesData: [],
        dataFlowData: [],
        connectedSystems: new Map(),
        inaccessibleSystems: new Set(),
        /** Attribute relationships for overlay highlighting (linking-attributes); populated when loading system/dataset lineage overlay */
        attributeRelationships: []
    };

    const adapter = window.createMapAdapter(GlossaryRelationshipsMapState, 'glossaryRelationshipsMap', { zoomRecenter: false });

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
        embLog('[GLOSSARY-RELATIONSHIPS-MAP] Initializing map for glossary:', glossaryId, 'container:', containerSelector);
        GlossaryRelationshipsMapState.glossaryId = glossaryId;
        GlossaryRelationshipsMapState.canvas = document.querySelector(containerSelector || '#glossaryRelationshipsMapCanvas');
        GlossaryRelationshipsMapState.loadingEl = document.querySelector('[data-glossary-relationships-map-loading]');

        if (!GlossaryRelationshipsMapState.canvas) {
            console.error('[GLOSSARY-RELATIONSHIPS-MAP] Canvas element not found');
            return;
        }

        // Check if Cytoscape is available
        if (typeof cytoscape === 'undefined') {
            console.error('[GLOSSARY-RELATIONSHIPS-MAP] Cytoscape library not available');
            GlossaryRelationshipsMapState.canvas.innerHTML = window.MapRenderUtils.htmlCytoscapeUnavailable();
            return;
        }

        // Register dagre layout if available - same as system map
        if (!GlossaryRelationshipsMapState.dagreRegistered) {
            try {
                const dagre = window.cytoscapeDagre || window['cytoscape-dagre'];
                if (dagre) {
                    cytoscape.use(dagre);
                    GlossaryRelationshipsMapState.dagreRegistered = true;
                }
                // Silently fall back to breadthfirst if dagre not available
            } catch (e) {
                // Silently handle error - will use breadthfirst fallback
            }
        }

        GlossaryRelationshipsMapState.initialized = true;
        
        // Load saved preferences (Axon-style: layout, overlay, hops, etc.) before loading data
        loadPreferences();
        
        // Load map data
        loadMapData(glossaryId);
    }
    
    const PREFERENCES_KEY = 'glossaryRelationshipsMapPreferences';
    
    function loadPreferences() {
        try {
            const raw = localStorage.getItem(PREFERENCES_KEY);
            if (raw) {
                const prefs = JSON.parse(raw);
                if (prefs.layout) GlossaryRelationshipsMapState.layout = prefs.layout;
                if (prefs.overlay) GlossaryRelationshipsMapState.overlay = prefs.overlay;
                if (prefs.mapType) GlossaryRelationshipsMapState.mapType = prefs.mapType;
                if (typeof prefs.hopsCount === 'number' && prefs.hopsCount >= 1 && prefs.hopsCount <= 99) GlossaryRelationshipsMapState.hopsCount = prefs.hopsCount;
                if (prefs.nodeFilters) GlossaryRelationshipsMapState.nodeFilters = { ...GlossaryRelationshipsMapState.nodeFilters, ...prefs.nodeFilters };
            }
            window.dispatchEvent(new CustomEvent('glossaryRelationshipsMapPreferencesLoaded', { detail: getPreferences() }));
        } catch (e) {
            window.dispatchEvent(new CustomEvent('glossaryRelationshipsMapPreferencesLoaded', { detail: getPreferences() }));
        }
    }
    
    function getPreferences() {
        return {
            layout: GlossaryRelationshipsMapState.layout,
            overlay: GlossaryRelationshipsMapState.overlay,
            mapType: GlossaryRelationshipsMapState.mapType,
            hopsCount: GlossaryRelationshipsMapState.hopsCount,
            nodeFilters: { ...GlossaryRelationshipsMapState.nodeFilters }
        };
    }
    
    function savePreferences() {
        try {
            const prefs = getPreferences();
            localStorage.setItem(PREFERENCES_KEY, JSON.stringify(prefs));
            window.dispatchEvent(new CustomEvent('glossaryRelationshipsMapPreferencesSaved', { detail: prefs }));
        } catch (e) {
            embWarn('[GLOSSARY-RELATIONSHIPS-MAP] Could not save preferences:', e);
        }
    }
    
    function resetPreferences() {
        GlossaryRelationshipsMapState.layout = 'left-to-right';
        GlossaryRelationshipsMapState.overlay = 'none';
        GlossaryRelationshipsMapState.hopsCount = 15;
        GlossaryRelationshipsMapState.nodeFilters = { relationshipTypes: [], classifications: [], types: [], lifecycles: [] };
        try {
            localStorage.removeItem(PREFERENCES_KEY);
        } catch (e) {}
        window.dispatchEvent(new CustomEvent('glossaryRelationshipsMapPreferencesLoaded', { detail: getPreferences() }));
        // Re-render map with default settings
        if (GlossaryRelationshipsMapState.glossaryId) {
            loadMapData(GlossaryRelationshipsMapState.glossaryId);
        }
    }

    

    // Build Cytoscape elements array from graph { nodes, edges }
    function buildElementsFromGraph(graph) {
        const elements = [];
        const colors = getMapColors();
        graph.nodes.forEach(node => {
            const isCurrent = node.isCurrent || false;
            const isGlossary = node.group === 'glossary';
            const isSystem = node.group === 'system';
            const isDataset = node.group === 'dataset';
            
            // Always provide default colors to avoid Cytoscape warnings
            // Ensure colors are always strings, never undefined/null
            let nodeColor, borderColor;
            if (isGlossary) {
                nodeColor = (isCurrent ? colors.currentGlossary : colors.otherGlossary) || '#1f2937';
                borderColor = (isCurrent ? colors.currentGlossaryBorder : colors.otherGlossaryBorder) || '#374151';
            } else if (isDataset) {
                nodeColor = (isCurrent ? colors.currentDataset : colors.otherDataset) || '#1f2937';
                borderColor = (isCurrent ? colors.currentDatasetBorder : colors.otherDatasetBorder) || '#374151';
            } else if (isSystem) {
                nodeColor = (isCurrent ? colors.currentSystem : colors.otherSystem) || '#1f2937';
                borderColor = (isCurrent ? colors.currentSystemBorder : colors.otherSystemBorder) || '#374151';
            } else {
                // Default fallback colors if node type is unknown
                nodeColor = colors.otherGlossary || '#1f2937';
                borderColor = colors.otherGlossaryBorder || '#374151';
            }
            
            // Build data object - ensure nodeColor and borderColor are set BEFORE spreading meta
            const nodeData = {
                id: node.id,
                label: (node.isLocked ? '\u{1F512} ' : '') + (node.label || ''),
                nodeColor: String(nodeColor), // Ensure it's always a string
                borderColor: String(borderColor), // Ensure it's always a string
                isCurrent: isCurrent,
                isGlossary: isGlossary,
                isSystem: isSystem,
                isDataset: isDataset,
                isLocked: node.isLocked || false
            };
            
            // Spread meta properties but don't let them overwrite nodeColor/borderColor
            if (node.meta) {
                Object.keys(node.meta).forEach(key => {
                    if (key !== 'nodeColor' && key !== 'borderColor') {
                        nodeData[key] = node.meta[key];
                    }
                });
                nodeData.meta = node.meta; // Keep meta as nested object for compatibility
            }
            
            elements.push({ data: nodeData });
        });
        
        // Create a set of node IDs for validation
        const nodeIds = new Set(elements.filter(el => el.data && !el.data.source).map(el => el.data.id));
        // Collect current node IDs for edge reversal (so dagre places current node leftmost/topmost)
        const currentNodeIds = new Set(graph.nodes.filter(n => n.isCurrent).map(n => n.id));
        
        // Add edges (only if both source and target nodes exist)
        graph.edges.forEach(edge => {
            // Validate that both source and target nodes exist
            if (nodeIds.has(edge.from) && nodeIds.has(edge.to)) {
                // Ensure lineColor is always a string, never undefined/null
                const lineColor = String(edge.lineColor || colors.relationshipLine || '#64748b');
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
                        lineColor: lineColor,
                        lineStyle: edge.lineStyle || 'solid',
                        lineType: edge.lineType || 'relationship',
                        relationType: edge.relationType || '',
                        reversed: !!reverseEdge
                    },
                    classes: reverseEdge ? 'reversed-edge' : ''
                });
            } else {
                embWarn('[GLOSSARY-RELATIONSHIPS-MAP] Skipped edge - missing nodes. From:', edge.from, 'exists:', nodeIds.has(edge.from), 'To:', edge.to, 'exists:', nodeIds.has(edge.to));
            }
        });
        return elements;
    }

    // Render network using Cytoscape
    function renderNetwork(graph) {
        if (!GlossaryRelationshipsMapState.canvas) {
            console.error('[GLOSSARY-RELATIONSHIPS-MAP] Canvas not available');
            return;
        }
        if (graphPipeline && graphPipeline.invalidateGraphCache) {
            graphPipeline.invalidateGraphCache();
        }

        // Clear canvas
        GlossaryRelationshipsMapState.canvas.innerHTML = '';

        if (!graph || !graph.nodes || graph.nodes.length === 0) {
            embWarn('[GLOSSARY-RELATIONSHIPS-MAP] No nodes to display. Graph:', graph);
            embWarn('[GLOSSARY-RELATIONSHIPS-MAP] Linear glossaries count:', GlossaryRelationshipsMapState.linearGlossaries.length);
            embWarn('[GLOSSARY-RELATIONSHIPS-MAP] Relationships count:', GlossaryRelationshipsMapState.glossaryRelationships.length);
            showPlaceholder('No data to display.');
            return;
        }

        const elements = buildElementsFromGraph(graph);

        // Find root nodes from graph data before creating network
        const rootNodeIds = findRootNodesFromGraph(graph);
        
        // Build layout configuration with root nodes
        const layoutConfig = buildCytoscapeLayout(rootNodeIds);
        
        // Create Cytoscape instance
        GlossaryRelationshipsMapState.network = cytoscape({
            container: GlossaryRelationshipsMapState.canvas,
            elements: elements,
            style: getCytoscapeStyle(),
            layout: layoutConfig,
            minZoom: 0.3,
            maxZoom: 3
        });

        // Setup event listeners
        setupEventListeners();
        
        // Setup overlay if enabled
        if (GlossaryRelationshipsMapState.overlay !== 'none') {
            loadOverlayData(GlossaryRelationshipsMapState.overlay);
        }
    }

    // Get Cytoscape styling
    function getCytoscapeStyle() {
        // Try to use external styles module
        if (window.InterfaceMapStyles && typeof window.InterfaceMapStyles.getCytoscapeStyles === 'function') {
            const styles = window.InterfaceMapStyles.getCytoscapeStyles('glossaryRelationshipsMap');
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
                    'background-color': 'data(nodeColor)', // Use data() directly - all nodes have nodeColor now
                    'border-color': 'data(borderColor)', // Use data() directly - all nodes have borderColor now
                    'border-width': 2,
                    'text-margin-y': 12,
                    'text-wrap': 'wrap',
                    'text-max-width': 150,
                    'font-size': 12,
                    'font-weight': '600',
                    'font-family': 'Inter, system-ui, sans-serif',
                    'color': colors.nodeLabel || '#1f2937',
                    'shape': 'round-rectangle',
                    'width': nodeSizes.glossary.width,
                    'height': nodeSizes.glossary.height
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
                selector: 'node[isSystem = true]',
                style: {
                    'width': nodeSizes.system.width,
                    'height': nodeSizes.system.height
                }
            },
            {
                selector: 'edge',
                style: {
                    'width': 2,
                    'line-color': 'data(lineColor)', // Use data() directly - all edges have lineColor now
                    'target-arrow-color': 'data(lineColor)', // Use data() directly - all edges have lineColor now
                    'target-arrow-shape': 'triangle',
                    'curve-style': (window._sharedDropdownApis && window._sharedDropdownApis['glossaryRelationshipsMap'] && typeof window._sharedDropdownApis['glossaryRelationshipsMap'].getCurveStyle === 'function') ? window._sharedDropdownApis['glossaryRelationshipsMap'].getCurveStyle() : 'bezier',
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

    // Build Cytoscape layout configuration
    function buildCytoscapeLayout(rootNodeIds = null) {
        const mapId = 'glossaryRelationshipsMap';
        const ddApi = window._sharedDropdownApis && window._sharedDropdownApis[mapId];
        const DEFAULT_SF = 2.2;
        const DEFAULT_PAD = 90;
        const sf = ddApi && typeof ddApi.getSpacingFactor === 'function' ? ddApi.getSpacingFactor() : DEFAULT_SF;
        const sp = ddApi && typeof ddApi.getSpacingPadding === 'function' ? ddApi.getSpacingPadding() : DEFAULT_PAD;
        const layoutOption = GlossaryRelationshipsMapState.layout;
        
        // Get root nodes - use provided IDs or find from network
        const roots = rootNodeIds || findRootNodes();

        // Try to use external layout configuration
        if (window.InterfaceMapStyles && typeof window.InterfaceMapStyles.getLayoutConfig === 'function') {
            const layoutConfig = window.InterfaceMapStyles.getLayoutConfig(layoutOption);
            // Add roots for breadthfirst layouts
            if (layoutConfig.name === 'breadthfirst') {
                return { ...layoutConfig, roots: roots, padding: sp, spacingFactor: sf };
            }
            // If dagre is requested but not registered, fall back to breadthfirst silently
            if (layoutConfig.name === 'dagre' && !GlossaryRelationshipsMapState.dagreRegistered) {
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
                if (GlossaryRelationshipsMapState.dagreRegistered) {
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
                if (GlossaryRelationshipsMapState.dagreRegistered) {
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
        
        // For glossary lineage, the current glossary should be a root
        if (GlossaryRelationshipsMapState.mapType === 'glossary-lineage') {
            const currentGlossaryId = String(GlossaryRelationshipsMapState.glossaryId);
            const currentNode = graph.nodes.find(n => n.id === `glossary_${currentGlossaryId}`);
            if (currentNode && !targets.has(currentNode.id)) {
                return [currentNode.id];
            }
        }
        
        const roots = graph.nodes.filter(node => !targets.has(node.id));
        return roots.length > 0 ? roots.map(n => n.id) : (graph.nodes.length > 0 ? [graph.nodes[0].id] : []);
    }

    // Find root nodes for layout (after network is created)
    // NOTE: this function keeps its own implementation (not delegated to MapRenderUtils)
    // because for 'glossary-lineage' map type the current glossary must be forced as
    // root even when it has incoming edges — standard BFS root detection is insufficient.
    function findRootNodes() {
        if (!GlossaryRelationshipsMapState.network) return [];
        const nodes = GlossaryRelationshipsMapState.network.nodes();
        if (nodes.length === 0) return [];

        const targets = new Set();
        GlossaryRelationshipsMapState.network.edges().forEach(edge => {
            targets.add(edge.target().id());
        });

        // For glossary lineage, the current glossary should be a root
        if (GlossaryRelationshipsMapState.mapType === 'glossary-lineage') {
            const currentGlossaryId = String(GlossaryRelationshipsMapState.glossaryId);
            const currentNode = nodes.filter(n => n.data('glossaryId') === currentGlossaryId);
            if (currentNode.length > 0 && !targets.has(currentNode[0].id())) {
                return [currentNode[0].id()];
            }
        }
        
        const roots = nodes.filter(node => !targets.has(node.id()));
        // Return node IDs (strings) not node objects
        return roots.length > 0 ? roots.map(n => n.id()) : [nodes[0].id()];
    }

    function setupEventListeners() {
        networkIx.setupEventListeners();
    }

    // Show node details in side panel
    function showNodeDetails(nodeData) {
        const detailsEl = document.querySelector('[data-glossary-relationships-map-details]');
        const placeholderEl = document.querySelector('[data-glossary-relationships-map-placeholder]');
        
        if (!detailsEl || !placeholderEl) return;
        
        placeholderEl.style.display = 'none';
        detailsEl.style.display = 'block';
        
        const name = nodeData.glossaryName || nodeData.systemName || nodeData.datasetName || nodeData.label || 'Unknown';
        let type = 'Unknown';
        if (nodeData.isGlossary) type = 'Glossary';
        else if (nodeData.isSystem) type = 'System';
        else if (nodeData.isDataset) type = 'Dataset';
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
        const detailsEl = document.querySelector('[data-glossary-relationships-map-details]');
        const placeholderEl = document.querySelector('[data-glossary-relationships-map-placeholder]');
        
        if (detailsEl) detailsEl.style.display = 'none';
        if (placeholderEl) placeholderEl.style.display = 'block';
    }

    // Show edge info
    function showEdgeInfo(edgeData) {
        embLog('[GLOSSARY-RELATIONSHIPS-MAP] Edge clicked:', edgeData);
    }

    // Set map type
    function setMapType(mapType) {
        GlossaryRelationshipsMapState.mapType = mapType;
        if (GlossaryRelationshipsMapState.glossaryId) {
            loadMapData(GlossaryRelationshipsMapState.glossaryId);
        }
    }

    // Set layout
    function setLayout(layout) {
        GlossaryRelationshipsMapState.layout = layout;
        if (GlossaryRelationshipsMapState.network) {
            const rootNodeIds = findRootNodes();
            GlossaryRelationshipsMapState.network.layout(buildCytoscapeLayout(rootNodeIds)).run();
        }
    }

    // Set overlay
    /** Set overlay columns (field ids) for an overlay type. Re-renders overlay if that type is active. */
    function setOverlayColumns(overlayType, columnIds) {
        if (!overlayType) return;
        GlossaryRelationshipsMapState.overlayColumnsByType[overlayType] = Array.isArray(columnIds) ? columnIds.slice() : [];
        if (GlossaryRelationshipsMapState.overlay === overlayType && GlossaryRelationshipsMapState.lastOverlayData) {
            renderOverlayPanels(overlayType, GlossaryRelationshipsMapState.lastOverlayData);
        }
    }

    /** Get selected overlay column ids for an overlay type. */
    function getOverlayColumns(overlayType) {
        return GlossaryRelationshipsMapState.overlayColumnsByType[overlayType] || (window.OverlayColumns ? window.OverlayColumns.getDefaultOverlayColumnIds(overlayType) : ['name']);
    }

    function setOverlay(overlayType) {
        embLog('[GLOSSARY-RELATIONSHIPS-MAP] setOverlay called with:', overlayType);
        GlossaryRelationshipsMapState.overlay = overlayType;

        if (overlayType === 'none') {
            clearOverlayPanels();
        } else {
            loadOverlayData(overlayType);
        }
    }

    // Set hops count (1-99, Axon recommends 15)
    function setHopsCount(count) {
        const val = Math.min(99, Math.max(1, parseInt(count, 10) || 15));
        GlossaryRelationshipsMapState.hopsCount = val;
        if (GlossaryRelationshipsMapState.network && (GlossaryRelationshipsMapState.mapType === 'system-lineage' || GlossaryRelationshipsMapState.mapType === 'dataset-lineage')) {
            const graph = buildGraph();
            const rootNodeIds = findRootNodesFromGraph(graph);
            GlossaryRelationshipsMapState.network.elements().remove();
            const elements = buildElementsFromGraph(graph);
            GlossaryRelationshipsMapState.network.add(elements);
            GlossaryRelationshipsMapState.network.layout(buildCytoscapeLayout(rootNodeIds)).run();
            if (GlossaryRelationshipsMapState.overlay !== 'none') loadOverlayData(GlossaryRelationshipsMapState.overlay);
        }
    }

    // Load overlay data for all visible nodes
    async function loadOverlayData(overlayType) {
        if (!GlossaryRelationshipsMapState.network) return;
        
        embLog('[GLOSSARY-RELATIONSHIPS-MAP] Loading overlay data:', overlayType);
        
        try {
            const overlayData = new Map();
            
            if (GlossaryRelationshipsMapState.mapType === 'glossary-lineage') {
                // Get glossary IDs from nodes
                const glossaryIds = new Set();
                GlossaryRelationshipsMapState.network.nodes().forEach(node => {
                    const nodeData = node.data();
                    if (nodeData.isGlossary && nodeData.glossaryId) {
                        glossaryIds.add(String(nodeData.glossaryId));
                    }
                });
                
                // Fetch overlay data for each glossary (always set entry so every node gets a panel)
                await Promise.all(Array.from(glossaryIds).map(async (glossaryId) => {
                    try {
                        const data = await fetchOverlayDataForGlossary(glossaryId, overlayType);
                        overlayData.set(glossaryId, Array.isArray(data) ? data : []);
                    } catch (error) {
                        embWarn(`[GLOSSARY-RELATIONSHIPS-MAP] Failed to load ${overlayType} for glossary ${glossaryId}:`, error);
                        overlayData.set(glossaryId, []);
                    }
                }));
            } else if (GlossaryRelationshipsMapState.mapType === 'system-lineage') {
                // Get system IDs from nodes
                const systemIds = new Set();
                GlossaryRelationshipsMapState.network.nodes().forEach(node => {
                    const nodeData = node.data();
                    if (nodeData.isSystem && nodeData.systemId) {
                        systemIds.add(String(nodeData.systemId));
                    }
                });
                
                // Fetch overlay data for each system (always set entry so every node gets a panel)
                await Promise.all(Array.from(systemIds).map(async (systemId) => {
                    try {
                        const data = await fetchOverlayDataForSystem(systemId, overlayType);
                        overlayData.set(systemId, Array.isArray(data) ? data : []);
                    } catch (error) {
                        embWarn(`[GLOSSARY-RELATIONSHIPS-MAP] Failed to load ${overlayType} for system ${systemId}:`, error);
                        overlayData.set(systemId, []);
                    }
                }));
            } else if (GlossaryRelationshipsMapState.mapType === 'dataset-lineage') {
                // Get dataset IDs from nodes
                const datasetIds = new Set();
                GlossaryRelationshipsMapState.network.nodes().forEach(node => {
                    const nodeData = node.data();
                    if (nodeData.isDataset && nodeData.datasetId) {
                        datasetIds.add(String(nodeData.datasetId));
                    }
                });
                
                // Fetch overlay data for each dataset (always set entry so every node gets a panel)
                await Promise.all(Array.from(datasetIds).map(async (datasetId) => {
                    try {
                        const data = await fetchOverlayDataForDataset(datasetId, overlayType);
                        overlayData.set(datasetId, Array.isArray(data) ? data : []);
                    } catch (error) {
                        embWarn(`[GLOSSARY-RELATIONSHIPS-MAP] Failed to load ${overlayType} for dataset ${datasetId}:`, error);
                        overlayData.set(datasetId, []);
                    }
                }));
            }
            
            // Render overlay panels
            renderOverlayPanels(overlayType, overlayData);
            
        } catch (error) {
            console.error('[GLOSSARY-RELATIONSHIPS-MAP] Error loading overlay data:', error);
        }
    }

    // Fetch overlay data for a glossary
    async function fetchOverlayDataForGlossary(glossaryId, overlayType) {
        try {
            switch (overlayType) {
                case 'description':
                    const glossary = await window.BUDG_API_SERVICE.getGlossaryById(glossaryId);
                    const glossaryData = glossary?.data || glossary;
                    if (glossaryData && glossaryData.description) {
                        return [{ name: 'Description', value: glossaryData.description }];
                    }
                    return [];
                    
                case 'stakeholders':
                    // Stakeholders with accepted and org unit columns
                    try {
                        const resp = await fetch(`/api/glossary-stakeholder/${glossaryId}/stakeholders`, { credentials: 'include' });
                        if (resp.ok) {
                            const data = await resp.json();
                            const stakeholders = Array.isArray(data) ? data : (Array.isArray(data?.data) ? data.data : (Array.isArray(data?.stakeholders) ? data.stakeholders : []));
                            // Normalize so Name/Role column and Accepted/Org Unit work (API may return PascalCase: PersonName, RoleName, OrgUnit, RoleAccepted)
                            return stakeholders.map(sh => ({
                                ...sh,
                                personName: sh.personName || sh.PersonName || sh.name || sh.Name || '',
                                roleName: sh.roleName || sh.RoleName || sh.role || sh.Role || '',
                                accepted: sh.accepted || sh.Accepted || sh.acceptedStatus || sh.RoleAccepted || '',
                                orgUnit: sh.orgUnit || sh.OrgUnit || sh.orgUnitName || sh.OrgUnitName || sh.organizationUnit || sh.OrganizationUnit || ''
                            }));
                        }
                    } catch (e) {
                        embWarn('[GLOSSARY-RELATIONSHIPS-MAP] Error fetching stakeholders:', e);
                    }
                    return [];
                    
                default:
                    return [];
            }
        } catch (error) {
            embWarn(`[GLOSSARY-RELATIONSHIPS-MAP] Failed to fetch ${overlayType} for glossary ${glossaryId}:`, error);
            return [];
        }
    }

    // Fetch overlay data for a system
    async function fetchOverlayDataForSystem(systemId, overlayType) {
        try {
            switch (overlayType) {
                case 'description':
                    const system = GlossaryRelationshipsMapState.systemsData.get(String(systemId));
                    if (system && system.description) {
                        return [{ name: 'Description', value: system.description }];
                    }
                    return [];
                    
                case 'glossary': {
                    const glossaries = [];
                    const seenSysG = new Set();
                    GlossaryRelationshipsMapState.datasetsData.forEach((dataset, dsId) => {
                        const datasetSystemId = String(dataset.systemId || dataset.masterSource || dataset.MasterSource);
                        if (datasetSystemId === String(systemId)) {
                            // 1) Dataset's own glossary
                            if (dataset.glossaryId) {
                                const gKey = String(dataset.glossaryId);
                                if (!seenSysG.has(gKey)) {
                                    seenSysG.add(gKey);
                                    const gName = dataset.glossaryName || '';
                                    glossaries.push({ id: dataset.glossaryId, name: gName, glossary: gName, glossaryId: dataset.glossaryId, source: 'dataset', _dsId: dsId });
                                }
                            }
                        }
                    });
                    // Resolve dataset-level glossary names that are missing
                    for (const g of glossaries) {
                        if (!g.name) {
                            try {
                                const gResp = await window.BUDG_API_SERVICE.getGlossaryById(g.id);
                                const gd = gResp?.data || gResp;
                                g.name = gd?.name || gd?.Name || gd?.primaryName || '';
                                g.glossary = g.name;
                            } catch (e) { /* ignore */ }
                        }
                    }
                    // 2) Attribute-level glossary terms for each system dataset
                    const sysDatasetIds = [];
                    GlossaryRelationshipsMapState.datasetsData.forEach((dataset, dsId) => {
                        const datasetSystemId = String(dataset.systemId || dataset.masterSource || dataset.MasterSource);
                        if (datasetSystemId === String(systemId)) sysDatasetIds.push(dsId);
                    });
                    for (const dsId of sysDatasetIds) {
                        try {
                            const attrResp = await fetch(`/api/attribute/${dsId}`, { credentials: 'include' });
                            if (attrResp.ok) {
                                const attrJson = await attrResp.json();
                                const attrs = Array.isArray(attrJson?.data) ? attrJson.data : (Array.isArray(attrJson) ? attrJson : []);
                                attrs.forEach(attr => {
                                    const gName = attr['Glossary Name attribute'] || attr.glossary || attr.glossaryName || attr.GlossaryName;
                                    if (gName && !seenSysG.has(gName)) {
                                        seenSysG.add(gName);
                                        const gId = attr.glossaryId || attr.glossary_id || attr.Glossary_ID;
                                        glossaries.push({ id: gId, name: gName, glossary: gName, glossaryId: gId, source: 'attribute' });
                                    }
                                });
                            }
                        } catch (e) { /* ignore */ }
                    }
                    return glossaries;
                }
                    
                case 'datasets':
                    const systemDatasets = [];
                    GlossaryRelationshipsMapState.datasetsData.forEach((dataset, datasetId) => {
                        const datasetSystemId = String(dataset.systemId || dataset.masterSource || dataset.MasterSource);
                        if (datasetSystemId === String(systemId)) {
                            systemDatasets.push({
                                id: datasetId,
                                name: dataset.name || dataset.primaryName || dataset.PrimaryName || `Dataset ${datasetId}`,
                                refNumber: dataset.refNumber || dataset.RefNumber || ''
                            });
                        }
                    });
                    return systemDatasets;
                    
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
                        embWarn('[GLOSSARY-RELATIONSHIPS-MAP] Error fetching stakeholders:', e);
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

                case 'geography':
                    try {
                        const API = window.BUDG_API_SERVICE;
                        if (API && typeof API.getSystemById === 'function') {
                            const sysData = await API.getSystemById(systemId);
                            const sys = sysData?.data || sysData;
                            if (sys && (sys.geography || sys.region || sys.country)) {
                                return [{ name: sys.geography || sys.region || sys.country, type: 'geography' }];
                            }
                        }
                    } catch (e) { /* ignore */ }
                    return [];

                case 'attributes':
                    try {
                        const API = window.BUDG_API_SERVICE;
                        if (API && typeof API.getSystemAttributes === 'function') {
                            const attrs = await API.getSystemAttributes(systemId);
                            return Array.isArray(attrs?.data) ? attrs.data : (Array.isArray(attrs) ? attrs : []);
                        }
                    } catch (e) { embWarn('[GLOSSARY-RELATIONSHIPS-MAP] Error fetching system attributes:', e); }
                    return [];
                case 'linking-attributes':
                    try {
                        const API = window.BUDG_API_SERVICE;
                        const allAttrs = API && typeof API.getSystemAttributes === 'function'
                            ? await API.getSystemAttributes(systemId)
                            : [];
                        const attrsList = Array.isArray(allAttrs?.data) ? allAttrs.data : (Array.isArray(allAttrs) ? allAttrs : []);
                        const linkingIds = new Set();
                        (GlossaryRelationshipsMapState.datasetRelationships || []).forEach(rel => {
                            const sid = rel.sourceAttributeId ?? rel.Source_AttributeID;
                            const tid = rel.targetAttributeId ?? rel.Target_AttributeID;
                            if (sid != null) linkingIds.add(String(sid));
                            if (tid != null) linkingIds.add(String(tid));
                        });
                        return attrsList.filter(a => linkingIds.has(String(a.id ?? a.ID ?? '')));
                    } catch (e) { embWarn('[GLOSSARY-RELATIONSHIPS-MAP] Error fetching system linking attributes:', e); }
                    return [];

                case 'data-quality':
                    try {
                        const resp = await fetch(`/api/data-quality/system/${systemId}`, { credentials: 'include' });
                        if (resp.ok) {
                            const data = await resp.json();
                            return Array.isArray(data?.data) ? data.data : (Array.isArray(data) ? data : []);
                        }
                    } catch (e) { /* ignore */ }
                    return [];

                case 'data-privacy':
                    try {
                        const resp = await fetch(`/api/data-privacy/system/${systemId}`, { credentials: 'include' });
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
            embWarn(`[GLOSSARY-RELATIONSHIPS-MAP] Failed to fetch ${overlayType} for system ${systemId}:`, error);
            return [];
        }
    }

    // Fetch overlay data for a dataset
    async function fetchOverlayDataForDataset(datasetId, overlayType) {
        try {
            switch (overlayType) {
                case 'description':
                    const dataset = GlossaryRelationshipsMapState.datasetsData.get(String(datasetId));
                    if (dataset && dataset.definition) {
                        return [{ name: 'Description', value: dataset.definition }];
                    }
                    return [];
                    
                case 'glossary': {
                    const glossaryTerms = [];
                    const seenG = new Set();
                    // 1) Dataset's own glossary term
                    const datasetData = GlossaryRelationshipsMapState.datasetsData.get(String(datasetId));
                    if (datasetData && datasetData.glossaryId) {
                        try {
                            const glossary = await window.BUDG_API_SERVICE.getGlossaryById(datasetData.glossaryId);
                            const gd = glossary?.data || glossary;
                            if (gd) {
                                const gName = gd.name || gd.Name || gd.primaryName || '';
                                if (gName && !seenG.has(gName)) {
                                    seenG.add(gName);
                                    glossaryTerms.push({ id: datasetData.glossaryId, name: gName, glossary: gName, glossaryId: datasetData.glossaryId, source: 'dataset' });
                                }
                            }
                        } catch (e) { /* ignore */ }
                    }
                    // 2) Glossary terms from attributes
                    try {
                        const attrResp = await fetch(`/api/attribute/${datasetId}`, { credentials: 'include' });
                        if (attrResp.ok) {
                            const attrJson = await attrResp.json();
                            const attrs = Array.isArray(attrJson?.data) ? attrJson.data : (Array.isArray(attrJson) ? attrJson : []);
                            attrs.forEach(attr => {
                                const gName = attr['Glossary Name attribute'] || attr.glossary || attr.glossaryName || attr.GlossaryName;
                                if (gName && !seenG.has(gName)) {
                                    seenG.add(gName);
                                    const gId = attr.glossaryId || attr.glossary_id || attr.Glossary_ID;
                                    glossaryTerms.push({ id: gId, name: gName, glossary: gName, glossaryId: gId, source: 'attribute' });
                                }
                            });
                        }
                    } catch (e) { /* ignore */ }
                    return glossaryTerms;
                }
                    
                case 'attributes':
                    try {
                        const datasetData = GlossaryRelationshipsMapState.datasetsData.get(String(datasetId));
                        if (datasetData && datasetData.attributes) {
                            return datasetData.attributes.map(attr => ({
                                id: attr.id || attr.ID,
                                name: attr.name || attr.attributeName || attr.Name || `Attribute ${attr.id}`
                            }));
                        }
                        const API = window.BUDG_API_SERVICE;
                        if (API) {
                            const resp = await fetch(`/api/attribute/${datasetId}`, { credentials: 'include' });
                            if (resp.ok) {
                                const data = await resp.json();
                                const attrs = Array.isArray(data?.data) ? data.data : (Array.isArray(data) ? data : []);
                                return attrs;
                            }
                        }
                    } catch (e) {
                        // Ignore
                    }
                    return [];

                case 'linking-attributes':
                    try {
                        const API = window.BUDG_API_SERVICE;
                        if (API && typeof API.getDatasetLinkingAttributes === 'function') {
                            const attrs = await API.getDatasetLinkingAttributes(datasetId);
                            return Array.isArray(attrs?.data) ? attrs.data : (Array.isArray(attrs) ? attrs : []);
                        }
                        const resp = await fetch(`/api/attribute/${datasetId}?linking=true`, { credentials: 'include' });
                        if (resp.ok) {
                            const data = await resp.json();
                            return Array.isArray(data?.data) ? data.data : (Array.isArray(data) ? data : []);
                        }
                    } catch (e) { /* ignore */ }
                    return [];

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
                        const API = window.BUDG_API_SERVICE;
                        if (API && typeof API.getDatasetStakeholders === 'function') {
                            const stakeholdersData = await API.getDatasetStakeholders(datasetId);
                            return Array.isArray(stakeholdersData?.data) ? stakeholdersData.data : (Array.isArray(stakeholdersData) ? stakeholdersData : []);
                        }
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
            embWarn(`[GLOSSARY-RELATIONSHIPS-MAP] Failed to fetch ${overlayType} for dataset ${datasetId}:`, error);
            return [];
        }
    }

    // Render overlay panels on nodes
    function renderOverlayPanels(overlayType, overlayData) {
        if (!GlossaryRelationshipsMapState.network || !GlossaryRelationshipsMapState.canvas) return;
        
        // Clear existing overlay panels
        clearOverlayPanels();
        
        // Create overlay container if it doesn't exist
        let overlayContainer = GlossaryRelationshipsMapState.canvas.querySelector('.map-overlay-container');
        if (!overlayContainer) {
            overlayContainer = document.createElement('div');
            overlayContainer.className = 'map-overlay-container';
            overlayContainer.style.cssText = 'position: absolute; top: 0; left: 0; width: 100%; height: 100%; pointer-events: none; z-index: 1000;';
            GlossaryRelationshipsMapState.canvas.appendChild(overlayContainer);
        }
        
        // Add panels for each node with overlay data
        GlossaryRelationshipsMapState.network.nodes().forEach(node => {
            const nodeId = node.id();
            const nodeData = node.data();
            
            // Get entity ID from node data
            let entityId = null;
            if (nodeData.isGlossary && nodeData.glossaryId) {
                entityId = String(nodeData.glossaryId);
            } else if (nodeData.isSystem && nodeData.systemId) {
                entityId = String(nodeData.systemId);
            } else if (nodeData.isDataset && nodeData.datasetId) {
                entityId = String(nodeData.datasetId);
            }
            
            if (!entityId) return;
            
            // Get overlay data by entityId — show panel only when there is data
            const data = overlayData.get(entityId);
            const items = Array.isArray(data) ? data : [];
            if (!items.length) return;
            
            const nodeName = nodeData.glossaryName || nodeData.systemName || nodeData.datasetName || nodeData.label || 'Node';
            
            // Store overlay data for highlighting (which nodes have which items)
            GlossaryRelationshipsMapState.lastOverlayData = overlayData;
            GlossaryRelationshipsMapState.lastOverlayType = overlayType;
            // Create overlay panel (max 10 items per page, pagination footer)
            const panel = createOverlayPanel(nodeName, overlayType, items, nodeId);
            if (!panel) return;
            overlayContainer.appendChild(panel);
            
            // Position panel
            positionOverlayPanelElement(panel, node);
            
            // Mark node as having overlay
            node.addClass('has-overlay');
        });
        
        // Add zoom/pan/drag listeners to update positions
        GlossaryRelationshipsMapState.network.on('zoom pan', updateOverlayPositions);
        GlossaryRelationshipsMapState.network.on('drag', 'node', updateOverlayPositions);
    }

    // Use global glossary-style overlay panel (header, body, footer with Page X of Y; stakeholders = Name/Role, Accepted, Org Unit table; or custom columns from overlayColumnsByType)
    function createOverlayPanel(nodeName, overlayType, data, nodeId) {
        const arr = Array.isArray(data) ? data : [];
        if (arr.length === 0) return null;
        if (!window.MapOverlayPanel) {
            embWarn('[GLOSSARY-RELATIONSHIPS-MAP] MapOverlayPanel not loaded, using fallback');
            return createOverlayPanelFallback(nodeName, overlayType, arr, nodeId);
        }
        var overlayColumnDefs = [];
        if (overlayType !== 'stakeholders' && window.OverlayColumns) {
            var allCols = window.OverlayColumns.getOverlayColumns(overlayType);
            var selectedIds = GlossaryRelationshipsMapState.overlayColumnsByType[overlayType] || window.OverlayColumns.getDefaultOverlayColumnIds(overlayType);
            overlayColumnDefs = allCols.filter(function(c) { return selectedIds && selectedIds.indexOf(c.id) !== -1; });
        }
        const panel = window.MapOverlayPanel.create(overlayType, arr, nodeId, {
            getTitle: getOverlayTitle,
            getItemText: getOverlayItemText,
            getItemId: getOverlayItemId,
            escapeHtml: escapeHtml,
            onItemClick: function(panel, el) {
                const idx = parseInt(el.getAttribute('data-item-index'), 10);
                const list = panel._overlayData;
                const item = (list && list[idx] != null) ? list[idx] : null;
                if (item != null) highlightOverlayItem(panel._overlayType, item, panel.getAttribute('data-node-id'));
            },
            overlayColumnDefs: overlayColumnDefs,
            getItemField: getOverlayItemField
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

    // Overlay item highlighting: clicked = dark green (highlighted-source), same/related in other panels = light green (highlighted-related)
    function highlightOverlayItem(overlayType, item, sourceNodeId) {
        const overlayContainer = GlossaryRelationshipsMapState.canvas?.querySelector('.map-overlay-container');
        if (!overlayContainer) return;

        // For attributes/linking-attributes with relationship data use relationship-based highlighting
        const attributeRelationships = GlossaryRelationshipsMapState.attributeRelationships;
        if ((overlayType === 'attributes' || overlayType === 'linking-attributes') && item && (item.id != null || item.ID != null) && Array.isArray(attributeRelationships) && attributeRelationships.length > 0) {
            const attrId = item.id != null ? item.id : item.ID;
            highlightAttributeRelationships(overlayContainer, attrId, sourceNodeId);
            return;
        }

        const clickedItemId = getOverlayItemId(overlayType, item);
        const itemValue = getOverlayItemText(overlayType, item);

        overlayContainer.querySelectorAll('.map-node-overlay-item').forEach(el => {
            el.classList.remove('highlighted-source', 'highlighted-related');
        });
        overlayContainer.querySelectorAll('.map-node-overlay-item').forEach(el => {
            const panel = el.closest('.map-node-overlay-panel');
            const isSource = panel?.getAttribute('data-node-id') === sourceNodeId;
            let shouldHighlight = false;
            if (clickedItemId) {
                const elItemId = el.dataset.itemId ? String(el.dataset.itemId) : null;
                if (elItemId && elItemId === String(clickedItemId)) shouldHighlight = true;
            }
            if (!shouldHighlight && (el.dataset.overlayValue || '').trim() === (itemValue || '').trim()) shouldHighlight = true;
            if (shouldHighlight) {
                if (isSource) el.classList.add('highlighted-source');
                else el.classList.add('highlighted-related');
            }
        });
        applyOverlayRelatedNodeAndEdgeHighlights(overlayContainer, sourceNodeId);
    }

    function applyOverlayRelatedNodeAndEdgeHighlights(overlayContainer, sourceNodeId) {
        const network = GlossaryRelationshipsMapState.network;
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

    function highlightAttributeRelationships(overlayContainer, clickedAttributeId, sourceNodeId) {
        const relationships = GlossaryRelationshipsMapState.attributeRelationships || [];
        const clickedAttrIdStr = String(clickedAttributeId);
        const relatedAttributeIds = findAllRelatedAttributes(clickedAttrIdStr, relationships);
        overlayContainer.querySelectorAll('.map-node-overlay-item').forEach(el => {
            el.classList.remove('highlighted-source', 'highlighted-related');
            const attrIdStr = (el.dataset.attributeId || el.dataset.itemId) ? String(el.dataset.attributeId || el.dataset.itemId) : null;
            if (!attrIdStr) return;
            const panel = el.closest('.map-node-overlay-panel');
            const isSource = panel?.getAttribute('data-node-id') === sourceNodeId;
            if (attrIdStr === clickedAttrIdStr) el.classList.add('highlighted-source');
            else if (relatedAttributeIds.has(attrIdStr)) el.classList.add('highlighted-related');
        });
        applyOverlayRelatedNodeAndEdgeHighlights(overlayContainer, sourceNodeId);
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

    // Get overlay title
    function getOverlayTitle(overlayType) {
        const titles = {
            'description': 'Description',
            'stakeholders': 'Stakeholders',
            'glossary': 'Glossaries',
            'datasets': 'Data Sets',
            'attributes': 'Attributes',
            'linking-attributes': 'Linking Attributes',
            'data-quality': 'Data Quality',
            'data-privacy': 'Data Privacy',
            'processes': 'Processes',
            'projects': 'Projects',
            'policies': 'Policies',
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
                // Include accepted and org unit (support API PascalCase: PersonName, RoleName, RoleAccepted, OrgUnit)
                const role = item.roleName || item.RoleName || item.role || item.Role || '';
                const name = item.personName || item.PersonName || item.name || item.Name || '';
                const accepted = item.accepted || item.Accepted || item.RoleAccepted || '';
                const orgUnit = item.orgUnit || item.OrgUnit || item.orgUnitName || item.OrgUnitName || '';
                let text = name;
                if (role) text += ` (${role})`;
                if (accepted) text += ` - Accepted: ${accepted}`;
                if (orgUnit) text += ` - Org Unit: ${orgUnit}`;
                return text;
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
                return item.name || item.attributeName || item.primaryName || item.PrimaryName || item['Name attribute'] || '';
            case 'projects':
                return item.projectName || item.PrimaryName || item.primaryName || item.name || item.Name || '';
            case 'processes':
                return item.processName || item.name || item.primaryName || item.PrimaryName || item.Name || '';
            case 'policies':
                return item.policyName || item.name || item.primaryName || item.PrimaryName || item.Name || '';
            case 'business-area':
                return item.businessAreaName || item.name || item.primaryName || item.PrimaryName || item.Name || '';
            case 'products':
                return item.productName || item.name || item.primaryName || item.PrimaryName || item.Name || '';
            case 'legal-entities':
                return item.legalEntityName || item.longName || item.name || item.primaryName || item.Name || '';
            case 'geography':
                return item.name || item.region || item.country || item.primaryName || item.Name || '';
            case 'data-quality':
                return item.ruleName || item.name || item.primaryName || item.Name || '';
            case 'data-privacy':
                return item.classification || item.privacyClassification || item.name || item.value || '';
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
            case 'description':
                return fieldId === 'value' ? v(item.value || item.description) : v(item[fieldId]);
            case 'stakeholders':
                switch (fieldId) {
                    case 'name': return v(item.personName || item.PersonName || item.name || item.Name);
                    case 'role': return v(item.roleName || item.RoleName || item.role || item.Role);
                    default: return v(item[fieldId]);
                }
            case 'glossary':
                switch (fieldId) {
                    case 'name': return v(item.name || item.glossaryName || item.primaryName || item.PrimaryName);
                    case 'source': return item.source === 'dataset' ? 'Dataset Glossary'
                                        : item.source === 'attribute' ? 'Attribute Glossary' : '';
                    default: return v(item[fieldId]);
                }
            case 'datasets':
                switch (fieldId) {
                    case 'name': return v(item.name || item.datasetName || item.primaryName);
                    case 'refNumber': return v(item.refNumber || item.ref);
                    case 'type': return v(item.typeName || item.type);
                    case 'lifecycle': return v(item.lifecycleName || item.lifecycle);
                    default: return v(item[fieldId]);
                }
            case 'attributes':
                switch (fieldId) {
                    case 'name': return v(item.name || item.attributeName || item.primaryName);
                    case 'type': return v(item.typeName || item.type);
                    case 'glossary': return v(item.glossaryName || item.glossary);
                    case 'refNumber': return v(item.refNumber || item.ref);
                    default: return v(item[fieldId]);
                }
            case 'projects':
                switch (fieldId) {
                    case 'name': return v(item.projectName || item.primaryName || item.name);
                    case 'refNumber': return v(item.refNumber || item.ref);
                    case 'status': return v(item.statusName || item.status);
                    default: return v(item[fieldId]);
                }
            default:
                return v(item[fieldId] || item.name || item.primaryName);
        }
    }

    function positionOverlayPanelElement(panel, node) {
        const overlayContainer = GlossaryRelationshipsMapState.canvas?.querySelector('.map-overlay-container');
        const network = GlossaryRelationshipsMapState.network;
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
        const overlayContainer = GlossaryRelationshipsMapState.canvas?.querySelector('.map-overlay-container');
        if (!overlayContainer || !GlossaryRelationshipsMapState.network) return;
        const cw = overlayContainer.clientWidth || 800;
        const ch = overlayContainer.clientHeight || 600;
        const buf = 150;

        overlayContainer.querySelectorAll('.map-node-overlay-panel').forEach(panel => {
            const nodeId = panel.getAttribute('data-node-id');
            const node = GlossaryRelationshipsMapState.network.getElementById(nodeId);
            
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
        if (!GlossaryRelationshipsMapState.canvas) return;
        
        const overlayContainer = GlossaryRelationshipsMapState.canvas.querySelector('.map-overlay-container');
        if (overlayContainer) {
            overlayContainer.innerHTML = '';
        }
        
        if (GlossaryRelationshipsMapState.network) {
            GlossaryRelationshipsMapState.network.nodes().removeClass('has-overlay');
            try { GlossaryRelationshipsMapState.network.off('zoom pan', updateOverlayPositions); } catch (e) { /* no-op */ }
            try { GlossaryRelationshipsMapState.network.off('drag', 'node', updateOverlayPositions); } catch (e) { /* no-op */ }
        }
    }

    // Populate filter options
    async function populateFilterOptions() {
        try {
            if (GlossaryRelationshipsMapState.mapType === 'glossary-lineage') {
                // Populate relationship type filters from glossary_x_glossary_relationtype
                const relTypes = GlossaryRelationshipsMapState.relationshipTypes;
                const container = document.getElementById('glossaryRelationshipsMapFilterRelationshipTypeOptions');
                if (container) {
                    container.innerHTML = '';
                    relTypes.forEach(relType => {
                        const id = String(relType.id || relType.ID);
                        const name = relType.primaryName || relType.PrimaryName || relType.name || `Type ${id}`;
                        const option = document.createElement('div');
                        option.className = 'map-filter-option';
                        option.innerHTML = `
                            <input type="checkbox" id="filterRelType_${id}" checked data-rel-type="${id}">
                            <label for="filterRelType_${id}">${escapeHtml(name)}</label>
                        `;
                        container.appendChild(option);
                    });
                    
                    // Dispatch event for filter options updated
                    window.dispatchEvent(new CustomEvent('glossaryRelationshipsMapFilterOptionsUpdated', {
                        detail: { relationshipTypes: relTypes }
                    }));
                }
            }
            // TODO: Populate other filter options for dataset and system lineage
        } catch (error) {
            console.error('[GLOSSARY-RELATIONSHIPS-MAP] Error populating filter options:', error);
        }
    }

    // Set node filters
    function setNodeFilters(nodeFilters) {
        GlossaryRelationshipsMapState.nodeFilters = {
            relationshipTypes: nodeFilters.relationshipTypes || [],
            classifications: nodeFilters.classifications || [],
            types: nodeFilters.types || [],
            lifecycles: nodeFilters.lifecycles || []
        };
        applyNodeFiltersToNetwork();
    }

    // NOTE: this function keeps its own implementation (not delegated to MapGraphUtils)
    // because it has two distinct filter modes: edge-based relationship-type filtering
    // (for glossary-lineage view) and node-based filtering (for other views). It also
    // stores classification/type/lifecycle on nodeData directly, not under nodeData.meta.
    function applyNodeFiltersToNetwork() {
        if (!GlossaryRelationshipsMapState.network) return;

        const { relationshipTypes, classifications, types, lifecycles } = GlossaryRelationshipsMapState.nodeFilters;
        
        if (GlossaryRelationshipsMapState.mapType === 'glossary-lineage') {
            // Filter by relationship type (on edges)
            GlossaryRelationshipsMapState.network.edges().forEach(edge => {
                const edgeData = edge.data();
                const relType = edgeData.relationType;
                
                if (relationshipTypes.length > 0 && relType && !relationshipTypes.includes(String(relType))) {
                    edge.style('display', 'none');
                } else {
                    edge.style('display', 'element');
                }
            });
        } else {
            // Filter nodes by classification, type, lifecycle
            GlossaryRelationshipsMapState.network.nodes().forEach(node => {
                const nodeData = node.data();
                let shouldShow = true;
                
                // Filter by classification
                if (classifications.length > 0) {
                    const nodeClassification = nodeData.classification;
                    if (nodeClassification && !classifications.includes(String(nodeClassification))) {
                        shouldShow = false;
                    }
                }
                
                // Filter by type
                if (types.length > 0) {
                    const nodeType = nodeData.type;
                    if (nodeType && !types.includes(String(nodeType))) {
                        shouldShow = false;
                    }
                }
                
                // Filter by lifecycle
                if (lifecycles.length > 0) {
                    const nodeLifecycle = nodeData.lifecycle;
                    if (nodeLifecycle && !lifecycles.includes(String(nodeLifecycle))) {
                        shouldShow = false;
                    }
                }
                
                if (shouldShow) {
                    node.style('display', 'element');
                } else {
                    node.style('display', 'none');
                }
            });
        }
        
        // Refresh layout
        const rootNodeIds = findRootNodes();
        GlossaryRelationshipsMapState.network.layout(buildCytoscapeLayout(rootNodeIds)).run();
        updateOverlayPositions();
    }

    function getLegendHtml() {
        const colors = getMapColors();
        const lineageHtml = (window.SharedMapStyles && typeof window.SharedMapStyles.getLineageLegendHtml === 'function')
            ? window.SharedMapStyles.getLineageLegendHtml(colors)
            : (window.InterfaceMapStyles && typeof window.InterfaceMapStyles.getLineageLegendHtml === 'function')
                ? window.InterfaceMapStyles.getLineageLegendHtml(colors)
                : '';
        let currentColor, currentLabel, otherColor, otherLabel;
        if (GlossaryRelationshipsMapState.mapType === 'glossary-lineage') {
            currentColor = colors.currentGlossary;
            currentLabel = 'Opened Glossary';
            otherColor = colors.otherGlossary;
            otherLabel = 'Related Glossary';
        } else if (GlossaryRelationshipsMapState.mapType === 'dataset-lineage') {
            currentColor = colors.currentDataset;
            currentLabel = 'Linked Dataset';
            otherColor = colors.otherDataset;
            otherLabel = 'Related Dataset';
        } else {
            currentColor = colors.currentSystem;
            currentLabel = 'Linked System';
            otherColor = colors.otherSystem;
            otherLabel = 'Related System';
        }
        return `
            <div class="legend-item">
                <div class="legend-color" style="background: ${currentColor};"></div>
                <span>${currentLabel}</span>
            </div>
            <div class="legend-item">
                <div class="legend-color" style="background: ${otherColor};"></div>
                <span>${otherLabel}</span>
            </div>
            <div class="legend-item">
                <span class="legend-lock" aria-hidden="true">&#x1F512;</span>
                <span>Segment not accessible</span>
            </div>
            ${lineageHtml}
            <div class="legend-item">
                <div class="legend-line" style="border-bottom: 2px dashed ${colors.relationshipLine || colors.interfaceLine || '#64748b'};"></div>
                <span>Relationship</span>
            </div>
        `;
    }

    function updateLegend() {
        const legendEl = document.querySelector('[data-glossary-relationships-map-legend]');
        if (legendEl) legendEl.innerHTML = getLegendHtml();
        const dropdown = document.getElementById('glossaryRelationshipsMapLegendDropdown');
        if (dropdown) dropdown.innerHTML = getLegendHtml();
    }

    // Zoom in
    function zoomIn()  { adapter.zoomIn(); }
    function zoomOut() { adapter.zoomOut(); }

    // Reset map
    function resetMap() {
        if (GlossaryRelationshipsMapState.network) {
            GlossaryRelationshipsMapState.network.reset();
            const rootNodeIds = findRootNodes();
            GlossaryRelationshipsMapState.network.layout(buildCytoscapeLayout(rootNodeIds)).run();
        }
    }

    // Redraw map
    function redrawMap() {
        if (GlossaryRelationshipsMapState.glossaryId) {
            loadMapData(GlossaryRelationshipsMapState.glossaryId);
        }
    }

    // Export as PNG (includes overlay panels when active)
    function exportAsPng() {
        if (!GlossaryRelationshipsMapState.network) return;
        const filename = `glossary-relationships-map-${GlossaryRelationshipsMapState.glossaryId}-${Date.now()}.png`;
        try {
            if (typeof window.exportMapWithOverlays === 'function') {
                window.exportMapWithOverlays(GlossaryRelationshipsMapState.network, GlossaryRelationshipsMapState.canvas, filename);
            } else {
                const png = GlossaryRelationshipsMapState.network.png({ output: 'blob', bg: 'white', full: true });
                const url = URL.createObjectURL(png);
                const link = document.createElement('a');
                link.href = url;
                link.download = filename;
                link.click();
                URL.revokeObjectURL(url);
            }
        } catch (error) {
            console.error('[GLOSSARY-RELATIONSHIPS-MAP] Error exporting map:', error);
        }
    }

    // Open fullscreen in new tab with toolbar controls
    function openFullscreen() {
        if (!GlossaryRelationshipsMapState.network) return;
        if (typeof window.openMapFullscreen === 'function') {
            const nodes = GlossaryRelationshipsMapState.network.nodes().map(n => n.json());
            const edges = GlossaryRelationshipsMapState.network.edges().map(e => e.json());
            window.openMapFullscreen({
                title: 'Glossary Relationships Map - ' + (GlossaryRelationshipsMapState.glossaryData?.name || GlossaryRelationshipsMapState.glossaryId),
                elements: { nodes: nodes, edges: edges },
                style: getCytoscapeStyle(),
                layoutName: GlossaryRelationshipsMapState.layout || 'top-to-bottom',
                legendHtml: getLegendHtml(),
                exportFilename: 'glossary-relationships-map-' + GlossaryRelationshipsMapState.glossaryId + '.png',
                toolbarAnchor: GlossaryRelationshipsMapState.canvas,
                mapType: GlossaryRelationshipsMapState.mapType || 'glossary-lineage',
                mapTabKind: 'glossary-relationships',
                getState: function () { return GlossaryRelationshipsMapState; }
            });
        } else {
            const canvas = GlossaryRelationshipsMapState.canvas;
            if (canvas && canvas.requestFullscreen) {
                canvas.requestFullscreen();
            } else if (canvas && canvas.webkitRequestFullscreen) {
                canvas.webkitRequestFullscreen();
            }
        }
    }

    // Toggle navigator
    function toggleNavigator() {
        const container = GlossaryRelationshipsMapState.canvas && GlossaryRelationshipsMapState.canvas.parentElement;
        if (!container || !GlossaryRelationshipsMapState.network || !window.MapRenderUtils) return;
        window.MapRenderUtils.attachOrToggleLineageMinimap({
            container: container,
            network: GlossaryRelationshipsMapState.network,
            minimapCanvasId: 'glossaryRelationshipsMapMinimap',
            syncViewport: true,
            cytoscapeOptions: { minZoom: 0.1, maxZoom: 0.5 }
        });
    }

    // Show loading indicator
    function showLoading() {
        if (GlossaryRelationshipsMapState.loadingEl) {
            GlossaryRelationshipsMapState.loadingEl.style.display = 'block';
        } else if (GlossaryRelationshipsMapState.canvas) {
            GlossaryRelationshipsMapState.canvas.innerHTML = window.MapRenderUtils.htmlMapLoading();
        }
    }

    // Hide loading indicator
    function hideLoading() {
        if (GlossaryRelationshipsMapState.loadingEl) {
            GlossaryRelationshipsMapState.loadingEl.style.display = 'none';
        }
    }

    // Show placeholder
    function showPlaceholder(message) {
        if (GlossaryRelationshipsMapState.canvas) {
            GlossaryRelationshipsMapState.canvas.innerHTML = window.MapRenderUtils.htmlMapCanvasMessage(message || 'No data available');
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
    // Graph pipeline + MapEngine (shared/map/glossary-relationships-facet-graph.js)
    // ─────────────────────────────────────────────────────────────────────────

    graphPipeline = window.createGlossaryRelationshipsFacetGraphPipeline({
        state: GlossaryRelationshipsMapState,
        adapter: adapter,
        renderNetwork: renderNetwork,
        showPlaceholder: showPlaceholder,
        showLoading: showLoading,
        hideLoading: hideLoading,
        updateLegend: updateLegend,
        populateFilterOptions: populateFilterOptions,
        logPrefix: '[GLOSSARY-RELATIONSHIPS-MAP]'
    });

    var _mapEngineApiLoader = graphPipeline._mapEngineApiLoader;
    var _mapEngineNodeBuilder = graphPipeline._mapEngineNodeBuilder;
    var _mapEngineEdgeBuilder = graphPipeline._mapEngineEdgeBuilder;

    networkIx = window.createGlossaryRelationshipsMapNetworkInteractions({
        state: GlossaryRelationshipsMapState,
        buildGraph: buildGraph,
        renderNetwork: renderNetwork,
        showEdgeInfo: showEdgeInfo,
        showNodeDetails: showNodeDetails,
        hideNodeDetails: hideNodeDetails
    });

    if (window.MapConfigs && window.MapConfigs.register) {
        window.MapConfigs.register('glossary-relationships', {
            apiLoader:     _mapEngineApiLoader,
            nodeBuilder:   _mapEngineNodeBuilder,
            edgeBuilder:   _mapEngineEdgeBuilder,
            legendBuilder: getLegendHtml
        });
    }

    var _glossaryRelationshipsEngine = new window.MapEngine('glossary-relationships');

    Object.assign(_glossaryRelationshipsEngine, {
        init: init,
        loadMapData: loadMapData,
        setMapType: setMapType,
        setLayout: setLayout,
        setOverlay: setOverlay,
        setOverlayColumns: setOverlayColumns,
        getOverlayColumns: getOverlayColumns,
        setHopsCount: setHopsCount,
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
        loadPreferences: loadPreferences,
        savePreferences: savePreferences,
        resetPreferences: resetPreferences,
        getPreferences: getPreferences,
        getState: function () { return GlossaryRelationshipsMapState; }
    });

    Object.defineProperty(_glossaryRelationshipsEngine, 'cy', {
        get: function () { return GlossaryRelationshipsMapState.network; },
        configurable: true
    });

    window.GlossaryRelationshipsMap = _glossaryRelationshipsEngine;

})();
