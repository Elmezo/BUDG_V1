(function () {
    const MAP_TEMPLATE_URL = '/assets/templates/map-view.html';
    const MAP_GROUP_COLORS = {
        dataset: { bg: '#2563eb', border: '#1d4ed8' },
        system: { bg: '#0d9488', border: '#0f766e' },
        attribute: { bg: '#9333ea', border: '#7e22ce' },
        glossary: { bg: '#f97316', border: '#ea580c' },
        interface: { bg: '#facc15', border: '#eab308' },
        people: { bg: '#e11d48', border: '#be123c' },
        policy: { bg: '#0891b2', border: '#0e7490' },
        process: { bg: '#14b8a6', border: '#0d9488' },
        project: { bg: '#ef4444', border: '#dc2626' },
        product: { bg: '#10b981', border: '#059669' },
        legal: { bg: '#8b5cf6', border: '#7c3aed' },
        client: { bg: '#ec4899', border: '#db2777' },
        other: { bg: '#94a3b8', border: '#64748b' }
    };

    const MAP_STATUS_COLORS = {
        approved: '#22c55e',
        active: '#10b981',
        draft: '#f97316',
        pending: '#eab308',
        deprecated: '#ef4444',
        retired: '#ef4444',
        default: '#94a3b8'
    };

    const MAP_RELATIONSHIP_FIELDS = {
        dataset: [
            { field: 'System Short Name', type: 'system', relation: 'feeds' },
            { field: 'Glossary Name', type: 'glossary', relation: 'describes' },
            { field: 'Created By', type: 'people', relation: 'created' },
            { field: 'BUDG Viewing', type: 'policy', relation: 'viewing' }
        ],
        attribute: [
            { field: 'Data Set Name', type: 'dataset', relation: 'belongs to' },
            { field: 'System Short Name', type: 'system', relation: 'source' },
            { field: 'Glossary Name', type: 'glossary', relation: 'defined by' }
        ],
        system: [
            { field: 'Parent Short Name', type: 'system', relation: 'parent' },
            { field: 'BUDG Viewing', type: 'policy', relation: 'controlled by' }
        ],
        process: [
            { field: 'Parent Name', type: 'process', relation: 'parent' },
            { field: 'Type', type: 'process', relation: 'type' },
            { field: 'Classification', type: 'process', relation: 'classified' }
        ],
        policy: [
            { field: 'Parent Name', type: 'policy', relation: 'parent' },
            { field: 'Type', type: 'policy', relation: 'type' }
        ],
        interface: [
            { field: 'Source System Short Name', type: 'system', relation: 'source' },
            { field: 'Target System Short Name', type: 'system', relation: 'target' }
        ]
    };

    const MAP_NODE_FILTERS = [
        { value: 'dataset', label: 'Datasets' },
        { value: 'system', label: 'Systems' },
        { value: 'attribute', label: 'Attributes' },
        { value: 'glossary', label: 'Glossaries' },
        { value: 'interface', label: 'Interfaces' },
        { value: 'process', label: 'Processes' },
        { value: 'policy', label: 'Policies' },
        { value: 'people', label: 'People' },
        { value: 'other', label: 'Other' }
    ];

    const MapState = {
        initialized: false,
        host: null,
        wrapper: null,
        canvas: null,
        loadingEl: null,
        legendEl: null,
        detailPlaceholder: null,
        detailsList: null,
        summaryEl: null,
        network: null,
        nodes: null,
        edges: null,
        layout: 'top-to-bottom',
        overlay: 'none',
        mapType: 'system-lineage',
        activeGroups: new Set(MAP_NODE_FILTERS.map(f => f.value)),
        cachedCategory: null,
        dataSignature: null,
        rawData: [],
        lastGraph: null,
        templateHTML: null
    };

    window.renderMapView = async function renderMapView(forceRefresh = false) {
        const host = getMapHost();
        if (!host) {
            console.error('[MAP] map host not found');
            return;
        }

        host.style.display = 'block';

        // Check if Cytoscape is available (should be loaded via script tag)
        if (typeof cytoscape === 'undefined') {
            console.error('[MAP] Cytoscape library not available');
            // Mount template first to show error message
            if (!host.dataset.mapLoaded) {
                await mountMapTemplate(host);
            }
            const wrapper = host.querySelector('[data-map-wrapper]');
            if (wrapper) {
                const placeholder = wrapper.querySelector('[data-map-placeholder]');
                if (placeholder) {
                    placeholder.textContent = 'Visualization library is not available. Please ensure cytoscape.min.js is loaded.';
                    placeholder.style.display = 'block';
                }
            }
            return;
        }

        if (!host.dataset.mapLoaded) {
            await mountMapTemplate(host);
        }

        const wrapper = host.querySelector('[data-map-wrapper]');
        if (!wrapper) {
            console.error('[MAP] template missing wrapper');
            return;
        }

        MapState.host = host;
        MapState.wrapper = wrapper;

        if (!MapState.initialized) {
            cacheMapElements(wrapper);
            initMapControls(wrapper);
            renderLegend();
            MapState.initialized = true;
        }

        await loadAndRenderMap(forceRefresh);
    };

    function getMapHost() {
        if (MapState.host && document.body.contains(MapState.host)) {
            return MapState.host;
        }
        MapState.host = document.querySelector('[data-map-host]');
        return MapState.host;
    }

    async function mountMapTemplate(host) {
        if (!MapState.templateHTML) {
            const response = await fetch(MAP_TEMPLATE_URL, { credentials: 'include' });
            if (!response.ok) {
                throw new Error('Failed to load map template');
            }
            MapState.templateHTML = await response.text();
        }
        host.innerHTML = MapState.templateHTML;
        host.dataset.mapLoaded = 'true';
    }

    function cacheMapElements(wrapper) {
        MapState.canvas = wrapper.querySelector('#mapNetworkCanvas');
        MapState.loadingEl = wrapper.querySelector('[data-map-loading]');
        MapState.legendEl = wrapper.querySelector('[data-map-legend]');
        MapState.detailPlaceholder = wrapper.querySelector('[data-map-placeholder]');
        MapState.detailsList = wrapper.querySelector('[data-map-details]');
        MapState.summaryEl = wrapper.querySelector('[data-map-summary]');
    }

    function initMapControls(wrapper) {
        const layoutSelect = wrapper.querySelector('#mapLayoutSelect');
        const overlaySelect = wrapper.querySelector('#mapOverlaySelect');
        const mapTypeSelect = wrapper.querySelector('#mapTypeSelect');
        const refreshBtn = wrapper.querySelector('#mapRefreshBtn');

        layoutSelect.addEventListener('change', (e) => {
            MapState.layout = e.target.value;
            updateNetworkLayout();
        });

        overlaySelect.addEventListener('change', (e) => {
            MapState.overlay = e.target.value;
            applyOverlay();
        });

        mapTypeSelect.addEventListener('change', async (e) => {
            MapState.mapType = e.target.value;
            await loadAndRenderMap(false);
        });

        refreshBtn.addEventListener('click', () => loadAndRenderMap(true));

        wrapper.querySelectorAll('[data-map-filter]').forEach(input => {
            input.addEventListener('change', (event) => {
                const group = event.target.value;
                if (event.target.checked) {
                    MapState.activeGroups.add(group);
                    event.target.closest('.map-toggle').dataset.active = 'true';
                } else {
                    MapState.activeGroups.delete(group);
                    event.target.closest('.map-toggle').dataset.active = 'false';
                }
                applyNodeFilters();
            });
        });
    }

    function renderLegend() {
        if (!MapState.legendEl) return;
        const legendHtml = Object.entries(MAP_GROUP_COLORS).map(([key, value]) => `
            <span class="map-legend-item">
                <span class="map-legend-color" style="background:${value.bg};border:1px solid ${value.border};"></span>
                ${capitalizeLabel(key)}
            </span>
        `).join('');
        MapState.legendEl.innerHTML = legendHtml;
    }

    async function loadAndRenderMap(forceRefresh) {
        if (!MapState.canvas) return;

        const category = typeof getActiveCategoryWithFallback === 'function'
            ? getActiveCategoryWithFallback()
            : null;

        if (!category) {
            showPlaceholder('Select a category to view its lineage map.');
            destroyNetwork();
            return;
        }

        showLoading();

        try {
            const signature = typeof getSearchSignature === 'function' ? getSearchSignature() : null;

            let data = (!forceRefresh && MapState.dataSignature === signature && MapState.cachedCategory === category)
                ? MapState.rawData
                : null;

            if (!data || !data.length) {
                const cached = !forceRefresh && typeof getCachedModuleData === 'function'
                    ? getCachedModuleData(category, signature)
                    : null;
                if (cached && cached.data && cached.data.length) {
                    data = cached.data;
                }
            }

            if (!data || !data.length) {
                if (typeof fetchCategoryData === 'function') {
                    const query = typeof getCurrentQuery === 'function' ? getCurrentQuery() : '';
                    data = await fetchCategoryData(category, query);
                    if (typeof cacheModuleData === 'function') {
                        cacheModuleData(category, data, signature);
                    }
                }
            }

            if (!data || !data.length) {
                showPlaceholder('No results available for the current category.');
                destroyNetwork();
                return;
            }

            MapState.rawData = data;
            MapState.cachedCategory = category;
            MapState.dataSignature = signature;

            // Build graph based on map type
            const graph = await buildGraphFromResults(category, data, MapState.mapType);
            MapState.lastGraph = graph;

            renderNetwork(graph);
            updateSummary(graph);
        } catch (error) {
            console.error('[MAP] Failed to render map', error);
            showPlaceholder('Failed to build map. Try refreshing the view.');
            destroyNetwork();
        } finally {
            hideLoading();
        }
    }

    function showLoading() {
        if (MapState.loadingEl) {
            MapState.loadingEl.style.display = 'flex';
        }
    }

    function hideLoading() {
        if (MapState.loadingEl) {
            MapState.loadingEl.style.display = 'none';
        }
    }

    function showPlaceholder(message) {
        if (MapState.detailPlaceholder) {
            MapState.detailPlaceholder.textContent = message;
        }
        if (MapState.detailsList) {
            MapState.detailsList.innerHTML = '';
        }
    }

    async function buildGraphFromResults(category, rows, mapType) {
        const nodesMap = new Map();
        const edges = [];
        const idCounts = {};

        // For System Lineage, focus on systems and their connections
        if (mapType === 'system-lineage') {
            return await buildSystemLineageGraph(rows, category);
        }

        rows.forEach((row, index) => {
            const primaryNode = createPrimaryNode(row, category, index, nodesMap);

            const relationshipFields = getRelationshipConfig(category, mapType);
            relationshipFields.forEach(rel => {
                const value = row[rel.field] || row[rel.field.replace('_', ' ')];
                if (!value || String(value).trim() === '') return;

                const relatedType = rel.type || 'other';
                const relatedId = `${relatedType}:${slugify(value)}`;

                if (!nodesMap.has(relatedId)) {
                    nodesMap.set(relatedId, {
                        id: relatedId,
                        label: String(value),
                        group: relatedType,
                        level: primaryNode.level + 1,
                        meta: { type: relatedType, label: value }
                    });
                }

                const edgeId = `${primaryNode.id}->${relatedId}-${rel.relation || 'rel'}`;
                edges.push({
                    id: edgeId,
                    from: primaryNode.id,
                    to: relatedId,
                    arrows: 'to',
                    label: rel.relation || '',
                    smooth: true
                });
            });

            // Create implicit parent relationships if possible
            const parentValue = row['Parent Name'] || row['Parent Short Name'];
            if (parentValue) {
                const parentType = category;
                const parentId = `${parentType}:parent:${slugify(parentValue)}`;
                if (!nodesMap.has(parentId)) {
                    nodesMap.set(parentId, {
                        id: parentId,
                        label: parentValue,
                        group: parentType,
                        level: primaryNode.level - 1,
                        meta: { role: 'parent', label: parentValue }
                    });
                }
                edges.push({
                    id: `${parentId}->${primaryNode.id}-parent`,
                    from: parentId,
                    to: primaryNode.id,
                    arrows: 'to',
                    label: 'parent',
                    dashes: true
                });
            }

            nodesMap.set(primaryNode.id, primaryNode);
        });

        return {
            nodes: Array.from(nodesMap.values()),
            edges
        };
    }

    async function buildSystemLineageGraph(rows, category) {
        const nodesMap = new Map();
        const edges = [];
        const edgeMap = new Map(); // Track edges to avoid duplicates

        // First pass: Create system nodes from current category data
        rows.forEach((row, index) => {
            const systemName = row['Short Name'] || row['Long Name'] || row.Name || `System ${index + 1}`;
            const systemId = `system:${slugify(systemName)}`;

            if (!nodesMap.has(systemId)) {
                nodesMap.set(systemId, {
                    id: systemId,
                    label: systemName,
                    group: 'system',
                    level: 1,
                    meta: {
                        category: 'system',
                        raw: row,
                        status: row.Status || '',
                        lifecycle: row.Lifecycle || ''
                    }
                });
            }

            // Check for parent system relationships
            const parentSystem = row['Parent Short Name'] || row['Parent System'];
            if (parentSystem) {
                const parentId = `system:${slugify(parentSystem)}`;
                
                if (!nodesMap.has(parentId)) {
                    nodesMap.set(parentId, {
                        id: parentId,
                        label: parentSystem,
                        group: 'system',
                        level: 0,
                        meta: { category: 'system' }
                    });
                }

                const edgeId = `${parentId}->${systemId}`;
                if (!edgeMap.has(edgeId)) {
                    edgeMap.set(edgeId, true);
                    edges.push({
                        id: edgeId,
                        from: parentId,
                        to: systemId,
                        label: 'parent',
                        dashes: true
                    });
                }
            }
        });

        // Always fetch interface data to show system connections
        if (typeof fetchCategoryData === 'function') {
            try {
                const interfaceData = await fetchCategoryData('interface', '');
                if (interfaceData && interfaceData.length > 0) {
                    interfaceData.forEach(row => {
                        const sourceSystem = row['Source System Short Name'] || row['Source System'];
                        const targetSystem = row['Target System Short Name'] || row['Target System'];

                        if (sourceSystem && targetSystem) {
                            const sourceId = `system:${slugify(sourceSystem)}`;
                            const targetId = `system:${slugify(targetSystem)}`;

                            // Create source system node if it doesn't exist
                            if (!nodesMap.has(sourceId)) {
                                nodesMap.set(sourceId, {
                                    id: sourceId,
                                    label: sourceSystem,
                                    group: 'system',
                                    level: 1,
                                    meta: { category: 'system' }
                                });
                            }

                            // Create target system node if it doesn't exist
                            if (!nodesMap.has(targetId)) {
                                nodesMap.set(targetId, {
                                    id: targetId,
                                    label: targetSystem,
                                    group: 'system',
                                    level: 2,
                                    meta: { category: 'system' }
                                });
                            }

                            // Create edge between systems (dashed line for interface)
                            const edgeId = `${sourceId}->${targetId}`;
                            if (!edgeMap.has(edgeId)) {
                                edgeMap.set(edgeId, true);
                                edges.push({
                                    id: edgeId,
                                    from: sourceId,
                                    to: targetId,
                                    label: row.Name || '',
                                    dashes: true
                                });
                            }
                        }
                    });
                }
            } catch (error) {
                console.warn('[MAP] Could not fetch interface data:', error);
            }
        }

        return {
            nodes: Array.from(nodesMap.values()),
            edges
        };
    }

    function createPrimaryNode(row, category, index, nodesMap) {
        const id = row.ID || row.id || `${category}-${index}`;
        const nodeId = `${category}:${id}`;
        if (nodesMap.has(nodeId)) {
            return nodesMap.get(nodeId);
        }

        const name = row.Name || row['Primary Name'] || row['Short Name'] || `Item ${index + 1}`;
        const status = row.Status || row['BUDG Status'] || '';
        const lifecycle = row.Lifecycle || row['Lifecycle'] || '';

        const node = {
            id: nodeId,
            label: name,
            group: normalizeGroup(category),
            level: 1,
            shape: 'box',
            margin: 10,
            font: { multi: 'html', face: 'Inter', size: 16, bold: true },
            meta: {
                category,
                status,
                lifecycle,
                raw: row
            }
        };

        nodesMap.set(nodeId, node);
        return node;
    }

    function renderNetwork(graph) {
        if (typeof cytoscape === 'undefined') {
            showPlaceholder('Visualization library is not available.');
            return;
        }

        // Clear existing network
        if (MapState.network) {
            MapState.network.destroy();
            MapState.network = null;
        }

        // Prepare Cytoscape elements
        const elements = [];
        
        // Add nodes with database icon styling (orange stacked bars like in image)
        graph.nodes.forEach(node => {
            // For system lineage, all nodes are systems with orange icon
            const isSystem = node.group === 'system' || MapState.mapType === 'system-lineage';
            
            // Filter by active groups
            const group = node.group || 'other';
            const isVisible = MapState.activeGroups.has(group);

            // Use orange color for systems (matching image)
            const nodeColor = isSystem ? '#f97316' : getGroupColor(group).bg;
            const borderColor = isSystem ? '#ea580c' : getGroupColor(group).border;

            // For system lineage, all nodes should be systems with orange icon
            const finalIsSystem = MapState.mapType === 'system-lineage' || isSystem;
            const finalNodeColor = finalIsSystem ? '#f97316' : nodeColor;
            const finalBorderColor = finalIsSystem ? '#ea580c' : borderColor;

            const nodeData = {
                id: node.id,
                label: node.label,
                group: group,
                meta: node.meta,
                isSystem: finalIsSystem,
                nodeColor: finalNodeColor,
                borderColor: finalBorderColor
            };

            // Only add visible nodes
            if (!isVisible) {
                return; // Skip invisible nodes
            }

            // Add database icon for system nodes
            if (finalIsSystem) {
                nodeData.backgroundImage = createDatabaseIcon(finalNodeColor);
            }

            elements.push({
                data: nodeData
            });
        });

        // Add edges
        graph.edges.forEach(edge => {
            elements.push({
                data: {
                    id: edge.id,
                    source: edge.from,
                    target: edge.to,
                    label: edge.label || ''
                }
            });
        });

        // Initialize Cytoscape
        MapState.network = cytoscape({
            container: MapState.canvas,
            elements: elements,
            style: [
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
                        'text-max-width': 180,
                        'font-size': 13,
                        'font-weight': '600',
                        'color': '#1f2937',
                        'shape': 'round-rectangle',
                        'width': 140,
                        'height': 80
                    }
                },
                {
                    selector: 'node[group = "system"]',
                    style: {
                        'background-color': '#f97316',
                        'border-color': '#ea580c',
                        'background-image': 'data(backgroundImage)',
                        'background-fit': 'contain',
                        'background-width': '50%',
                        'background-height': '50%',
                        'background-position-y': '25%'
                    }
                },
                {
                    selector: 'edge',
                    style: {
                        'width': 2,
                        'line-color': '#94a3b8',
                        'target-arrow-color': '#94a3b8',
                        'target-arrow-shape': 'triangle',
                        'target-arrow-width': 8,
                        'target-arrow-height': 8,
                        'curve-style': 'bezier',
                        'line-style': 'dashed',
                        'line-dash-pattern': [5, 5]
                    }
                },
                {
                    selector: 'node:selected',
                    style: {
                        'border-width': 4,
                        'border-color': '#3b82f6',
                        'background-opacity': 1
                    }
                }
            ],
            layout: buildCytoscapeLayout()
        });

        // Event handlers
        MapState.network.on('tap', 'node', function(evt) {
            const cyNode = evt.target;
            const nodeData = cyNode.data();
            // Find the original node data
            const originalNode = MapState.nodes.find(n => n.id === nodeData.id);
            if (originalNode) {
                handleNodeSelection(originalNode);
            }
        });

        MapState.network.on('tap', function(evt) {
            if (evt.target === MapState.network) {
                showPlaceholder('Select a node to see its details.');
            }
        });

        // Store elements for filtering
        MapState.nodes = graph.nodes;
        MapState.edges = graph.edges;
    }

    function destroyNetwork() {
        if (MapState.network) {
            MapState.network.destroy();
            MapState.network = null;
        }
        MapState.nodes = null;
        MapState.edges = null;
    }

    function buildCytoscapeLayout() {
        const layoutOption = MapState.layout;
        
        const baseLayout = {
            name: 'breadthfirst',
            directed: true,
            padding: 50,
            spacingFactor: 1.5,
            animate: true,
            animationDuration: 500
        };

        switch (layoutOption) {
            case 'right-to-left':
                return {
                    ...baseLayout,
                    roots: 'left',
                    nodeDimensionsIncludeLabels: true
                };
            case 'top-to-bottom':
                return {
                    ...baseLayout,
                    roots: 'top',
                    nodeDimensionsIncludeLabels: true
                };
            case 'force':
                return {
                    name: 'cose',
                    animate: true,
                    animationDuration: 500,
                    nodeRepulsion: 4500,
                    idealEdgeLength: 100,
                    edgeElasticity: 0.45,
                    nestingFactor: 0.1,
                    gravity: 0.25,
                    numIter: 2500,
                    initialEnergyOnIncremental: 0.3
                };
            default: // left-to-right
                return {
                    ...baseLayout,
                    roots: 'right',
                    nodeDimensionsIncludeLabels: true
                };
        }
    }

    function updateNetworkLayout() {
        if (!MapState.network) return;
        const layout = buildCytoscapeLayout();
        MapState.network.layout(layout).run();
    }

    function applyNodeFilters() {
        if (!MapState.network || !MapState.nodes) return;
        const allowedGroups = MapState.activeGroups;

        MapState.nodes.forEach((node) => {
            const group = node.group || 'other';
            const isVisible = allowedGroups.has(group);
            const cyNode = MapState.network.getElementById(node.id);
            if (cyNode.length > 0) {
                cyNode.style('display', isVisible ? 'element' : 'none');
            }
        });
    }

    function applyOverlay() {
        if (!MapState.network || !MapState.nodes) return;
        const overlay = MapState.overlay;

        MapState.nodes.forEach((node) => {
            const baseColor = getGroupColor(node.group);
            let bgColor = baseColor.bg;
            let borderColor = baseColor.border;

            if (overlay === 'status' && node.meta?.status) {
                bgColor = getStatusColor(node.meta.status);
                borderColor = shadeColor(bgColor, -15);
            } else if (overlay === 'lifecycle' && node.meta?.lifecycle) {
                bgColor = stringToColor(node.meta.lifecycle);
                borderColor = shadeColor(bgColor, -15);
            }

            const cyNode = MapState.network.getElementById(node.id);
            if (cyNode.length > 0) {
                cyNode.style({
                    'background-color': bgColor,
                    'border-color': borderColor
                });
            }
        });
    }

    function handleNodeSelection(nodeData) {
        if (!nodeData) return;

        if (MapState.detailPlaceholder) {
            MapState.detailPlaceholder.textContent = nodeData.label || nodeData.id;
        }

        if (MapState.detailsList) {
            MapState.detailsList.innerHTML = formatNodeDetails(nodeData);
        }
    }

    function formatNodeDetails(node) {
        if (!node.meta || !node.meta.raw) {
            return `<div class="map-details-row">
                <span>Type</span>
                <span>${capitalizeLabel(node.group || 'Other')}</span>
            </div>`;
        }

        const row = node.meta.raw;
        const importantFields = ['Ref.', 'Status', 'Lifecycle', 'Type', 'Classification', 'Created By', 'Created Date', 'Last Updated'];
        const entries = Object.entries(row)
            .filter(([key, value]) => value && typeof value !== 'object' && String(value).trim() !== '')
            .slice(0, 12);

        const rowsHtml = entries.map(([key, value]) => {
            if (!importantFields.includes(key) && key === key.toUpperCase()) {
                return '';
            }
            return `
                <div class="map-details-row">
                    <span>${key}</span>
                    <span>${value}</span>
                </div>
            `;
        }).join('');

        return rowsHtml || '<div class="map-details-row"><span>No additional attributes.</span></div>';
    }

    function updateSummary(graph) {
        if (!MapState.summaryEl) return;
        const nodeCount = graph.nodes.length;
        const edgeCount = graph.edges.length;
        MapState.summaryEl.innerHTML = `
            <div><strong>Nodes:</strong> ${nodeCount}</div>
            <div><strong>Connections:</strong> ${edgeCount}</div>
        `;
    }

    function getGroupColor(group) {
        const key = normalizeGroup(group);
        return MAP_GROUP_COLORS[key] || MAP_GROUP_COLORS.other;
    }

    function getStatusColor(status) {
        if (!status) return MAP_STATUS_COLORS.default;
        const normalized = status.toString().toLowerCase();
        return MAP_STATUS_COLORS[normalized] || MAP_STATUS_COLORS.default;
    }

    function stringToColor(str) {
        if (!str) return MAP_STATUS_COLORS.default;
        let hash = 0;
        for (let i = 0; i < str.length; i++) {
            hash = str.charCodeAt(i) + ((hash << 5) - hash);
        }
        const hue = hash % 360;
        return `hsl(${hue}, 70%, 65%)`;
    }

    function slugify(value) {
        return String(value)
            .toLowerCase()
            .replace(/\s+/g, '-')
            .replace(/[^\w-]/g, '');
    }

    function normalizeGroup(group) {
        if (!group) return 'other';
        const value = group.toString().toLowerCase();
        if (value.includes('dataset')) return 'dataset';
        if (value.includes('system')) return 'system';
        if (value.includes('attribute')) return 'attribute';
        if (value.includes('glossary')) return 'glossary';
        if (value.includes('interface')) return 'interface';
        if (value.includes('process')) return 'process';
        if (value.includes('policy')) return 'policy';
        if (value.includes('people') || value.includes('person')) return 'people';
        if (value.includes('project')) return 'project';
        if (value.includes('product')) return 'product';
        if (value.includes('client')) return 'client';
        if (value.includes('legal')) return 'legal';
        return value || 'other';
    }

    function getRelationshipConfig(category, mapType) {
        const normalized = normalizeGroup(category);
        const base = MAP_RELATIONSHIP_FIELDS[normalized] || [];

        if (mapType === 'data-lineage') {
            return base.filter(rel => ['dataset', 'system', 'attribute', 'glossary'].includes(rel.type));
        }

        if (mapType === 'system-lineage') {
            // For system lineage, focus on system-to-system connections
            return [
                { field: 'Source System Short Name', type: 'system', relation: 'feeds' },
                { field: 'Target System Short Name', type: 'system', relation: 'targets' },
                { field: 'Parent Short Name', type: 'system', relation: 'parent' }
            ];
        }

        return base;
    }

    function createDatabaseIcon(color) {
        // Create SVG data URI for database/stacked bars icon (orange like in image)
        // Using white/light color for the icon on orange background
        const iconColor = '#ffffff';
        const svg = `<svg width="40" height="40" viewBox="0 0 40 40" xmlns="http://www.w3.org/2000/svg">
            <rect x="6" y="4" width="28" height="5" rx="1.5" fill="${iconColor}" opacity="0.9"/>
            <rect x="6" y="13" width="28" height="5" rx="1.5" fill="${iconColor}" opacity="0.9"/>
            <rect x="6" y="22" width="28" height="5" rx="1.5" fill="${iconColor}" opacity="0.9"/>
            <rect x="6" y="31" width="28" height="5" rx="1.5" fill="${iconColor}" opacity="0.9"/>
        </svg>`;
        const encoded = encodeURIComponent(svg);
        return `data:image/svg+xml;charset=utf-8,${encoded}`;
    }

    function capitalizeLabel(text) {
        if (!text) return '';
        return text.charAt(0).toUpperCase() + text.slice(1).replace('-', ' ');
    }

    function shadeColor(color, percent) {
        if (!color || color.startsWith('hsl')) return color;
        const num = parseInt(color.replace('#', ''), 16);
        const amt = Math.round(2.55 * percent);
        const R = (num >> 16) + amt;
        const G = (num >> 8 & 0x00FF) + amt;
        const B = (num & 0x0000FF) + amt;
        return '#' + (
            0x1000000 +
            (R < 255 ? (R < 0 ? 0 : R) : 255) * 0x10000 +
            (G < 255 ? (G < 0 ? 0 : G) : 255) * 0x100 +
            (B < 255 ? (B < 0 ? 0 : B) : 255)
        ).toString(16).slice(1);
    }

    window.reloadMapIfActive = function reloadMapIfActive() {
        if (typeof isMapViewActive === 'function' && isMapViewActive()) {
            loadAndRenderMap(false);
        }
    };
})();

