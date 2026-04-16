(function () {
    'use strict';

    function mapsDbgWarn() {
        if (typeof window !== 'undefined' && window.BUDG_DEBUG_MAPS_JS === true) {
            console.warn.apply(console, arguments);
        }
    }

    /* In-scope nodes: orange palette; out-of-scope: grey */
    const MAP_GROUP_COLORS = {
        system: { bg: '#ea580c', border: '#c2410c' },
        dataset: { bg: '#f97316', border: '#ea580c' },
        attribute: { bg: '#fb923c', border: '#ea580c' },
        glossary: { bg: '#ea580c', border: '#9a3412' },
        interface: { bg: '#f97316', border: '#ea580c' },
        people: { bg: '#ea580c', border: '#c2410c' },
        policy: { bg: '#ea580c', border: '#c2410c' },
        process: { bg: '#f97316', border: '#ea580c' },
        project: { bg: '#c2410c', border: '#9a3412' },
        product: { bg: '#ea580c', border: '#c2410c' },
        legal: { bg: '#c2410c', border: '#9a3412' },
        client: { bg: '#ea580c', border: '#c2410c' },
        other: { bg: '#6b7280', border: '#4b5563' }
    };

    /** Node shape per facet (Cytoscape shape) */
    const MAP_GROUP_SHAPES = {
        system: 'round-rectangle',
        dataset: 'ellipse',
        process: 'diamond',
        project: 'rectangle',
        people: 'ellipse',
        policy: 'hexagon',
        glossary: 'round-rectangle',
        product: 'round-rectangle',
        attribute: 'hexagon',
        interface: 'round-rectangle',
        legal: 'hexagon',
        client: 'ellipse',
        other: 'round-rectangle'
    };

    const MapState = {
        initialized: false,
        canvas: null,
        loadingEl: null,
        network: null,
        nodes: null,
        edges: null,
        layout: 'top-to-bottom',
        /** Node spacing density: compact | normal | spacey */
        spacing: 'normal',
        /** Edge routing style: angle | square | direct | loop | top-down | left-right */
        edgeStyle: 'direct',
        overlay: 'none',
        mapType: 'system-lineage',
        rawData: [],
        lastGraph: null,
        zoomLevel: 1,
        history: [],
        historyIndex: -1,
        searchQuery: '',
        /** Axon: Show interface info along dotted lines (system lineage) */
        showInterfaceLabels: true,
        /** System IDs in segments user cannot access – show lock */
        inaccessibleSystemIds: new Set(),
        /** Dataset IDs in segments user cannot access – show lock */
        inaccessibleDatasetIds: new Set(),
        /** Multi-node: active node type filters (system, process, project, stakeholder) */
        multiNodeFilters: new Set(['system', 'process', 'project', 'stakeholder']),
        /** Axon: node IDs in scope of active search (orange); others grey for lineage context */
        inScopeNodeIds: new Set(),
        /** Axon: when a node is selected, show red=into / green=out of */
        selectedNodeId: null,
        /** Axon: hops limit for upstream/downstream (1–99, default 15) */
        hopsLimit: 15,
        /** Full graph before hops filter (so hops change can re-apply without reload) */
        fullGraph: null,
        /** Node IDs hidden via "Hide object" context menu */
        hiddenNodes: new Set(),
        /** Node ID focused via "Focus object" (center + zoom); null = no focus */
        focusedNodeId: null,
        /** Active node filters (classification, type, lifecycle) - empty Set = all selected */
        nodeFilters: {
            classifications: new Set(),
            types: new Set(),
            lifecycles: new Set()
        },
        /** System lineage only: show/hide edge types (like other maps) */
        linkFilters: {
            systemInterfaces: true,
            dataAttributeLinks: true
        }
    };

    const OVERLAY_LABELS = {
        none: 'None',
        description: 'Description',
        glossary: 'Glossary',
        datasets: 'Data Sets',
        attributes: 'Attributes',
        'linking-attributes': 'Linking Attributes',
        'data-quality': 'Data Quality',
        'data-privacy': 'Data Privacy',
        stakeholders: 'Stakeholders',
        processes: 'Processes',
        projects: 'Projects',
        policies: 'Policies',
        'business-area': 'Business Area',
        products: 'Products',
        'legal-entities': 'Legal Entities',
        geography: 'Geography'
    };

    /** Dataset facet uses definition field; label overlay button text for dataset-lineage. */
    function overlayButtonLabel(overlayKey, mapType) {
        if (overlayKey === 'description' && mapType === 'dataset-lineage') return 'Definition';
        return OVERLAY_LABELS[overlayKey] || overlayKey || 'None';
    }

    function syncMapLayoutRichUiFromSelect() {
        const layoutSel = document.getElementById('mapLayoutSelect');
        const layoutMenu = document.getElementById('mapLayoutMenu');
        const layoutText = document.getElementById('mapLayoutBtnText');
        if (!layoutSel || !layoutMenu || !layoutText) return;
        const v = layoutSel.value;
        layoutMenu.querySelectorAll('.map-layout-rich-item').forEach(function (it) {
            it.classList.toggle('active', it.dataset.layout === v);
        });
        const activeItem = layoutMenu.querySelector('.map-layout-rich-item.active');
        const titleEl = activeItem && activeItem.querySelector('.map-layout-rich-title');
        if (titleEl) layoutText.textContent = titleEl.textContent;
        else {
            const opt = layoutSel.options[layoutSel.selectedIndex];
            if (opt) layoutText.textContent = opt.textContent;
        }
    }

    function syncMapStateFromSharedDropdowns() {
        const api = window._sharedDropdownApis && window._sharedDropdownApis.map;
        if (!api) return;
        if (typeof api.getSpacing === 'function') MapState.spacing = api.getSpacing();
        if (typeof api.getEdgeStyle === 'function') MapState.edgeStyle = api.getEdgeStyle();
    }

    // Initialize maps when DOM is ready
    document.addEventListener('DOMContentLoaded', function() {
        initMaps();
    });

    // Expose initMaps globally for dynamic loading
    window.initMaps = function() {
        initMaps();
    };

    function initMaps() {
        MapState.canvas = document.getElementById('mapNetworkCanvas');
        MapState.loadingEl = document.querySelector('[data-map-loading]');

        if (!MapState.canvas) {
            console.error('[MAPS] Canvas element not found');
            return;
        }

        // Check if Cytoscape is available
        if (typeof cytoscape === 'undefined') {
            console.error('[MAPS] Cytoscape library not available');
            MapState.canvas.innerHTML = '<div style="padding: 2rem; text-align: center; color: var(--text-secondary);">Visualization library is not available. Please ensure cytoscape.min.js is loaded.</div>';
            return;
        }

        // Register cytoscape-dagre for directional layouts (LR, TB, RL)
        if (!window._mapsDagreRegistered) {
            try {
                const dagreExt = window.cytoscapeDagre || window['cytoscape-dagre'];
                if (dagreExt) {
                    cytoscape.use(dagreExt);
                    window._mapsDagreRegistered = true;
                }
            } catch (e) {
                mapsDbgWarn('[MAPS] cytoscape-dagre not available, directional layouts may fall back to breadthfirst');
            }
        }

        initMapControls();
        updateMapsLegend();
        MapState.initialized = true;
        loadAndRenderMap(true);
    }

    function initMapControls() {
        const layoutHost = document.getElementById('mapLayoutControlsHost');
        if (layoutHost && !layoutHost.dataset.injected && typeof window.SharedMapLayoutRichControlsHtml === 'function') {
            layoutHost.innerHTML = window.SharedMapLayoutRichControlsHtml('map', { richItems: 'four' });
            layoutHost.dataset.injected = '1';
        }

        const mapTypeSelect = document.getElementById('mapTypeSelect');
        const refreshBtn = document.getElementById('mapRefreshBtn');
        const zoomInBtn = document.getElementById('mapZoomInBtn');
        const zoomOutBtn = document.getElementById('mapZoomOutBtn');
        const undoBtn = document.getElementById('mapUndoBtn');
        const redoBtn = document.getElementById('mapRedoBtn');
        const saveBtn = document.getElementById('mapSaveBtn');
        const viewBtn = document.getElementById('mapViewBtn');
        const exportBtn = document.getElementById('mapExportBtn');
        const listBtn = document.getElementById('mapListBtn');
        const legendBtn = document.getElementById('mapLegendBtn');

        if (legendBtn) {
            const sidePanel = document.getElementById('mapLegendSidePanel');
            legendBtn.addEventListener('click', () => {
                if (sidePanel) {
                    const isVisible = sidePanel.style.display !== 'none';
                    sidePanel.style.display = isVisible ? 'none' : 'flex';
                    legendBtn.classList.toggle('active', !isVisible);
                }
            });
        }

        if (mapTypeSelect) {
            mapTypeSelect.addEventListener('change', async (e) => {
                MapState.mapType = e.target.value;
                toggleMultiNodeFiltersVisibility();
                toggleMultiNodeLayoutOrganicOnly();
                switchOverlayMenuByMapType();
                switchFilterMenuByMapType();
                await loadAndRenderMap(false);
            });
        }

        toggleMultiNodeFiltersVisibility();
        toggleMultiNodeLayoutOrganicOnly();
        switchFilterMenuByMapType();
        bindMultiNodeFilterCheckboxes();
        bindLinkFilterCheckboxes();

        initOverlayDropdown();
        initFilterDropdown();
        initLayoutToggles();
        initResetRedrawFullscreen();

        const showHideInterfaceBtn = document.getElementById('mapShowHideInterfaceBtn');
        if (showHideInterfaceBtn) {
            showHideInterfaceBtn.addEventListener('click', () => {
                MapState.showInterfaceLabels = !MapState.showInterfaceLabels;
                showHideInterfaceBtn.title = MapState.showInterfaceLabels ? 'Hide interface information' : 'Show interface information';
                showHideInterfaceBtn.setAttribute('aria-label', showHideInterfaceBtn.title);
                const icon = showHideInterfaceBtn.querySelector('i');
                if (icon) icon.className = MapState.showInterfaceLabels ? 'fas fa-project-diagram' : 'fas fa-project-diagram map-icon-muted';
                if (MapState.network) {
                    MapState.network.edges().forEach(edge => {
                        const d = edge.data();
                        const showLabel = d.dashes ? MapState.showInterfaceLabels : true;
                        edge.data('label', showLabel ? (d.edgeLabel || '') : '');
                    });
                }
            });
        }

        if (refreshBtn) {
            refreshBtn.addEventListener('click', () => loadAndRenderMap(true));
        }

        const navigatorBtn = document.getElementById('mapNavigatorBtn');
        if (navigatorBtn) {
            navigatorBtn.addEventListener('click', () => {
                if (MapState.network) MapState.network.fit(50);
            });
        }

        const hopsSelect = document.getElementById('mapHopsSelect');
        if (hopsSelect && hopsSelect.options.length <= 1) {
            const frag = document.createDocumentFragment();
            for (let i = 1; i <= 99; i++) {
                const opt = document.createElement('option');
                opt.value = String(i);
                opt.textContent = String(i);
                if (i === 15) opt.selected = true;
                frag.appendChild(opt);
            }
            hopsSelect.innerHTML = '';
            hopsSelect.appendChild(frag);
            hopsSelect.value = '15';
            hopsSelect.addEventListener('change', () => {
                MapState.hopsLimit = parseInt(hopsSelect.value, 10) || 15;
                if (MapState.fullGraph) {
                    let graphToRender = MapState.fullGraph;
                    // For system lineage with search results, apply direct-relations filter first
                    if (MapState.mapType === 'system-lineage' && MapState.inScopeNodeIds.size > 0 &&
                        MapState.fullGraph.nodes && MapState.fullGraph.nodes.length !== MapState.inScopeNodeIds.size) {
                        graphToRender = filterToDirectRelationsOnly(graphToRender, MapState.inScopeNodeIds);
                    }
                    const filtered = applyHopsFilter(graphToRender);
                    MapState.lastGraph = filtered;
                    renderNetwork(filtered);
                }
            });
        }

        if (zoomInBtn) {
            zoomInBtn.addEventListener('click', () => {
                if (MapState.network) {
                    MapState.network.zoom(MapState.network.zoom() * 1.2);
                }
            });
        }

        if (zoomOutBtn) {
            zoomOutBtn.addEventListener('click', () => {
                if (MapState.network) {
                    MapState.network.zoom(MapState.network.zoom() * 0.8);
                }
            });
        }

        if (undoBtn) {
            undoBtn.addEventListener('click', () => {
                if (MapState.historyIndex > 0) {
                    MapState.historyIndex--;
                    const previousState = MapState.history[MapState.historyIndex];
                    restoreState(previousState);
                }
            });
        }

        if (redoBtn) {
            redoBtn.addEventListener('click', () => {
                if (MapState.historyIndex < MapState.history.length - 1) {
                    MapState.historyIndex++;
                    const nextState = MapState.history[MapState.historyIndex];
                    restoreState(nextState);
                }
            });
        }

        if (saveBtn) {
            saveBtn.addEventListener('click', () => {
                saveMapState();
            });
        }

        if (viewBtn) {
            viewBtn.addEventListener('click', () => {
                // Open map in new window
                const newWindow = window.open('', '_blank');
                if (newWindow) {
                    newWindow.document.write(`
                        <html>
                            <head>
                                <title>Map View - BUDG</title>
                                <style>
                                    body { margin: 0; padding: 0; }
                                    #mapCanvas { width: 100vw; height: 100vh; }
                                </style>
                            </head>
                            <body>
                                <div id="mapCanvas"></div>
                                <script src="/assets/js/cytoscape.min.js"></script>
                                <script>
                                    // Render map in new window
                                    const cy = cytoscape({
                                        container: document.getElementById('mapCanvas'),
                                        elements: ${JSON.stringify(getCurrentElements())},
                                        style: ${JSON.stringify(getCytoscapeStyle())},
                                        layout: ${JSON.stringify(buildCytoscapeLayout())}
                                    });
                                </script>
                            </body>
                        </html>
                    `);
                }
            });
        }

        if (exportBtn) {
            exportBtn.addEventListener('click', () => {
                exportMapAsPNG();
            });
        }

        if (listBtn) {
            listBtn.addEventListener('click', () => {
                // Navigate to search page
                window.location.href = `/search.html`;
            });
        }
    }

    function switchOverlayMenuByMapType() {
        const t = MapState.mapType;
        ['mapOverlayMenuSystem', 'mapOverlayMenuDataset', 'mapOverlayMenuMultiNode'].forEach(id => {
            const el = document.getElementById(id);
            if (!el) return;
            if (el.getAttribute('data-map-type') === t) {
                el.style.display = 'none'; /* closed by default; only .open shows it */
                el.classList.remove('open');
            } else {
                el.style.display = 'none';
                el.classList.remove('open');
            }
        });
        const btnText = document.getElementById('mapOverlayBtnText');
        if (btnText) btnText.textContent = overlayButtonLabel(MapState.overlay, MapState.mapType);
    }

    function switchFilterMenuByMapType() {
        const t = MapState.mapType;
        const linksCat = document.getElementById('mapFilterLinksCategory');
        const linksSep = document.getElementById('mapFilterLinksSeparator');
        const classCat = document.getElementById('mapFilterClassificationCategory');
        if (linksCat) linksCat.style.display = t === 'system-lineage' ? 'block' : 'none';
        if (linksSep) linksSep.style.display = t === 'system-lineage' ? 'block' : 'none';
        if (classCat) classCat.style.display = t === 'system-lineage' ? 'block' : 'none';
    }

    function bindLinkFilterCheckboxes() {
        const systemInterfacesCb = document.getElementById('mapFilterSystemInterfaces');
        const dataAttributeLinksCb = document.getElementById('mapFilterDataAttributeLinks');
        const syncFromUI = () => {
            if (systemInterfacesCb) MapState.linkFilters.systemInterfaces = systemInterfacesCb.checked;
            if (dataAttributeLinksCb) MapState.linkFilters.dataAttributeLinks = dataAttributeLinksCb.checked;
        };
        const applyAndRender = () => {
            syncFromUI();
            if (MapState.lastGraph && MapState.mapType === 'system-lineage') renderNetwork(MapState.lastGraph);
        };
        if (systemInterfacesCb) systemInterfacesCb.addEventListener('change', applyAndRender);
        if (dataAttributeLinksCb) dataAttributeLinksCb.addEventListener('change', applyAndRender);
    }

    function initOverlayDropdown() {
        const overlayBtn = document.getElementById('mapOverlayBtn');
        const overlayBtnText = document.getElementById('mapOverlayBtnText');
        const menus = [
            document.getElementById('mapOverlayMenuSystem'),
            document.getElementById('mapOverlayMenuDataset'),
            document.getElementById('mapOverlayMenuMultiNode')
        ].filter(Boolean);

        if (overlayBtn) {
            overlayBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                const visible = menus.find(m => m.getAttribute('data-map-type') === MapState.mapType);
                if (visible) {
                    const isOpen = visible.classList.contains('open');
                    if (isOpen) {
                        visible.classList.remove('open');
                        visible.style.display = 'none';
                    } else {
                        menus.forEach(m => { m.classList.remove('open'); m.style.display = m === visible ? '' : 'none'; });
                        visible.classList.add('open');
                        visible.style.display = 'block';
                    }
                }
            });
        }
        menus.forEach(menu => {
            if (menu) menu.addEventListener('click', (e) => e.stopPropagation());
        });

        function closeAllOverlayMenus() {
            menus.forEach(m => { m.classList.remove('open'); m.style.display = 'none'; });
        }

        document.querySelectorAll('.map-overlay-menu .overlay-menu-item').forEach(item => {
            item.addEventListener('click', () => {
                const overlay = item.getAttribute('data-overlay');
                if (overlay) {
                    MapState.overlay = overlay;
                    if (overlayBtnText) overlayBtnText.textContent = overlayButtonLabel(overlay, MapState.mapType);
                    closeAllOverlayMenus();
                    applyOverlay();
                    document.querySelectorAll('.map-overlay-menu .overlay-menu-item').forEach(i => i.classList.remove('active'));
                    item.classList.add('active');
                }
            });
        });

        ['mapClearOverlaysBtn', 'mapClearOverlaysBtnDataset', 'mapClearOverlaysBtnMultiNode'].forEach(id => {
            const btn = document.getElementById(id);
            if (btn) {
                btn.addEventListener('click', () => {
                    MapState.overlay = 'none';
                    if (overlayBtnText) overlayBtnText.textContent = 'None';
                    closeAllOverlayMenus();
                    document.querySelectorAll('.map-overlay-menu .overlay-menu-item').forEach(i => i.classList.remove('active'));
                    clearOverlayPanels();
                });
            }
        });

        document.addEventListener('click', function(e) {
            if (overlayBtn && menus.every(m => !m.contains(e.target)) && !overlayBtn.contains(e.target)) {
                closeAllOverlayMenus();
            }
        });
        switchOverlayMenuByMapType();
    }

    function initFilterDropdown() {
        const filterBtn = document.getElementById('mapFilterBtn');
        const filterMenu = document.getElementById('mapFilterMenu');
        if (filterBtn && filterMenu) {
            filterBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                filterMenu.classList.toggle('open');
            });
            filterMenu.addEventListener('click', (e) => e.stopPropagation());
            document.addEventListener('click', function(e) {
                if (!filterMenu.contains(e.target) && !filterBtn.contains(e.target)) {
                    filterMenu.classList.remove('open');
                }
            });
        }

        window.addEventListener('insightMapFilterOptionsUpdated', (event) => {
            const { classifications = [], types = [], lifecycles = [] } = event.detail || {};
            const classificationContainer = document.getElementById('mapFilterClassificationOptions');
            const typeContainer = document.getElementById('mapFilterTypeOptions');
            const lifecycleContainer = document.getElementById('mapFilterLifecycleOptions');

            const renderOptions = (container, values, filterKey) => {
                if (!container) return;
                const currentSet = MapState.nodeFilters[filterKey];
                const hasAny = currentSet.size > 0;
                container.innerHTML = values.length ? values.map((val, idx) => {
                    const checked = !hasAny || currentSet.has(val);
                    return `<div class="map-filter-option">
                        <input type="checkbox" id="mapFilter_${filterKey}_${idx}" data-filter-type="${filterKey}" data-filter-value="${String(val).replace(/"/g, '&quot;')}" ${checked ? 'checked' : ''}>
                        <label for="mapFilter_${filterKey}_${idx}">${escapeHtml(String(val))}</label>
                    </div>`;
                }).join('') : '<div class="map-filter-option" style="color: var(--text-muted); font-size: 0.75rem;">None</div>';
                container.querySelectorAll('input[type="checkbox"]').forEach(cb => {
                    cb.addEventListener('change', () => {
                        applyMapNodeFiltersFromUI();
                        applyNodeFilters();
                        updateMapFilterLabel();
                    });
                });
            };

            renderOptions(classificationContainer, classifications, 'classifications');
            renderOptions(typeContainer, types, 'types');
            renderOptions(lifecycleContainer, lifecycles, 'lifecycles');
            updateMapFilterLabel();
        });
    }

    function applyMapNodeFiltersFromUI() {
        const getChecked = (type) => {
            const all = document.querySelectorAll(`#mapFilterMenu input[data-filter-type="${type}"]`);
            const checked = document.querySelectorAll(`#mapFilterMenu input[data-filter-type="${type}"]:checked`);
            const values = new Set(Array.from(checked).map(n => n.getAttribute('data-filter-value')));
            return (all.length > 0 && values.size === all.length) ? new Set() : values;
        };
        MapState.nodeFilters.classifications = getChecked('classifications');
        MapState.nodeFilters.types = getChecked('types');
        MapState.nodeFilters.lifecycles = getChecked('lifecycles');
    }

    function applyNodeFilters() {
        if (!MapState.network) return;
        const cf = MapState.nodeFilters.classifications;
        const tf = MapState.nodeFilters.types;
        const lf = MapState.nodeFilters.lifecycles;
        const noClassification = cf.size === 0;
        const noType = tf.size === 0;
        const noLifecycle = lf.size === 0;

        MapState.network.nodes().forEach(node => {
            const meta = node.data('meta') || {};
            const raw = meta.raw || {};
            const status = (raw.Status || raw.status || meta.status || '').toString().trim();
            const typeVal = (raw.Type || raw.type || meta.type || '').toString().trim();
            const lifecycle = (raw.Lifecycle || raw.lifecycle || meta.lifecycle || '').toString().trim();

            let hide = false;
            if (!noClassification && status && !cf.has(status)) hide = true;
            if (!noType && typeVal && !tf.has(typeVal)) hide = true;
            if (!noLifecycle && lifecycle && !lf.has(lifecycle)) hide = true;
            if (hide) node.addClass('filter-hidden');
            else node.removeClass('filter-hidden');
        });

        MapState.network.edges().forEach(edge => {
            const src = edge.source();
            const tgt = edge.target();
            if (src.hasClass('filter-hidden') || tgt.hasClass('filter-hidden')) edge.addClass('filter-hidden');
            else edge.removeClass('filter-hidden');
        });
        
        updateOverlayPositions();
    }

    function updateMapFilterLabel() {
        const textEl = document.getElementById('mapFilterBtnText');
        if (!textEl) return;
        applyMapNodeFiltersFromUI();
        let count = 0;
        if (MapState.network) {
            count = MapState.network.nodes().filter(n => !n.hasClass('filter-hidden')).length;
        }
        textEl.textContent = count > 0 ? `All selected (${count})` : 'All selected (0)';
    }

    function emitInsightMapFilterOptions(graph) {
        if (!graph || !graph.nodes || !graph.nodes.length) return;
        const classifications = new Set();
        const types = new Set();
        const lifecycles = new Set();
        graph.nodes.forEach(node => {
            const meta = node.meta || {};
            const raw = meta.raw || {};
            const status = (raw.Status || raw.status || meta.status || '').toString().trim();
            const typeVal = (raw.Type || raw.type || meta.type || '').toString().trim();
            const lifecycle = (raw.Lifecycle || raw.lifecycle || meta.lifecycle || '').toString().trim();
            if (status) classifications.add(status);
            if (typeVal) types.add(typeVal);
            if (lifecycle) lifecycles.add(lifecycle);
        });
        window.dispatchEvent(new CustomEvent('insightMapFilterOptionsUpdated', {
            detail: {
                classifications: Array.from(classifications).sort(),
                types: Array.from(types).sort(),
                lifecycles: Array.from(lifecycles).sort()
            }
        }));
    }

    function initLayoutToggles() {
        const layoutHost = document.getElementById('mapLayoutControlsHost');
        if (layoutHost && layoutHost.dataset.sharedDropdownsBound === '1') return;
        if (typeof window.SharedMapDropdowns !== 'function') return;
        window.SharedMapDropdowns({
            mapId: 'map',
            getNetwork: () => MapState.network,
            setLayout: (dir) => {
                MapState.layout = dir;
                updateNetworkLayout();
            },
            getCanvas: () => MapState.canvas
        });
        if (layoutHost) layoutHost.dataset.sharedDropdownsBound = '1';
    }

    /** Get spacing factor based on MapState.spacing */
    function getSpacingFactor() {
        switch (MapState.spacing) {
            case 'compact': return 1.2;
            case 'spacey':  return 3.5;
            default:        return 2.2; // normal
        }
    }

    /** Get padding based on MapState.spacing */
    function getSpacingPadding() {
        switch (MapState.spacing) {
            case 'compact': return 40;
            case 'spacey':  return 150;
            default:        return 90; // normal
        }
    }

    /** Map edgeStyle to a Cytoscape curve-style value */
    function getCurveStyle() {
        switch (MapState.edgeStyle) {
            case 'angle':     return 'segments';       // angular/segmented edges
            case 'square':    return 'taxi';            // orthogonal right-angle edges
            case 'loop':      return 'unbundled-bezier';// looping curved edges
            case 'top-down':  return 'bezier';
            case 'left-right':return 'bezier';
            default:          return 'bezier';          // direct - smooth bezier curves
        }
    }

    /** Apply edge routing style to the current network (live update) */
    function applyEdgeStyle() {
        if (!MapState.network) return;
        const curveStyle = getCurveStyle();
        MapState.network.edges().style('curve-style', curveStyle);
    }

    function initResetRedrawFullscreen() {
        const redrawBtn = document.getElementById('mapRedrawBtn');
        if (redrawBtn) redrawBtn.addEventListener('click', () => updateNetworkLayout());

        const resetBtn = document.getElementById('mapResetBtn');
        if (resetBtn) resetBtn.addEventListener('click', () => resetMap());

        const fullscreenBtn = document.getElementById('mapFullscreenBtn');
        if (fullscreenBtn) fullscreenBtn.addEventListener('click', () => openFullscreen());
    }

    function resetMap() {
        MapState.hiddenNodes.clear();
        MapState.selectedNodeId = null;
        if (MapState.network) {
            MapState.network.nodes().removeClass('filter-hidden');
            MapState.network.edges().removeClass('filter-hidden');
            MapState.network.elements('edge').removeClass('flow-in flow-out');
        }
        if (MapState.lastGraph) {
            renderNetwork(MapState.lastGraph);
        } else {
            updateNetworkLayout();
        }
    }

    function openFullscreen() {
        if (!MapState.network) return;
        if (typeof window.openMapFullscreen === 'function') {
            var nodes = MapState.network.nodes().map(function(n){ return n.json(); });
            var edges = MapState.network.edges().map(function(e){ return e.json(); });
            var legendEl = document.querySelector('[data-maps-legend]');
            window.openMapFullscreen({
                title: 'Insight Map - BUDG',
                elements: { nodes: nodes, edges: edges },
                style: getCytoscapeStyle(),
                layoutName: MapState.layout || 'top-to-bottom',
                legendHtml: legendEl ? legendEl.innerHTML : '',
                exportFilename: 'insight-map-' + Date.now() + '.png',
                toolbarAnchor: MapState.canvas,
                mapType: MapState.mapType || 'system-lineage',
                mapTabKind: 'insight',
                getState: function () { return MapState; }
            });
        } else {
            var newWindow = window.open('', '_blank');
            if (!newWindow) return;
            var elements = MapState.network.json().elements;
            var style = getCytoscapeStyle();
            newWindow.document.write('<!DOCTYPE html><html><head><title>Insight Map - BUDG</title><style>body{margin:0;padding:0;font-family:Inter,sans-serif;}#fullscreenMap{width:100vw;height:100vh;}</style><script src="/assets/js/cytoscape.min.js"><\/script></head><body><div id="fullscreenMap"></div><script>cytoscape({container:document.getElementById("fullscreenMap"),elements:' + JSON.stringify(elements) + ',style:' + JSON.stringify(style) + ',layout:{name:"preset"}});<\/script></body></html>');
            newWindow.document.close();
        }
    }

    function updateMapsLegend() {
        const el = document.querySelector('[data-maps-legend]');
        if (!el) return;
        const colors = MAP_GROUP_COLORS;
        el.innerHTML = `
            <div class="legend-section-title">Node types (facets)</div>
            <div class="legend-item"><div class="legend-color" style="background:${colors.system.bg}; border-radius:6px;"></div><span>System</span></div>
            <div class="legend-item"><div class="legend-color" style="background:${colors.dataset.bg}; border-radius:50%;"></div><span>Data set</span></div>
            <div class="legend-item"><div class="legend-color" style="background:${colors.process.bg}; transform:rotate(45deg); width:12px; height:12px;"></div><span>Process</span></div>
            <div class="legend-item"><div class="legend-color" style="background:${colors.project.bg};"></div><span>Project</span></div>
            <div class="legend-item"><div class="legend-color" style="background:${colors.people.bg}; border-radius:50%;"></div><span>Stakeholder / People</span></div>
            <div class="legend-item"><div class="legend-color" style="background:${colors.policy.bg}; clip-path:polygon(50% 0%, 100% 25%, 100% 75%, 50% 100%, 0% 75%, 0% 25%); width:14px; height:14px;"></div><span>Policy</span></div>
            <div class="legend-item"><div class="legend-color" style="background:${colors.glossary.bg}; border-radius:6px;"></div><span>Glossary</span></div>
            <div class="legend-item"><div class="legend-color" style="background:${colors.product.bg}; border-radius:6px;"></div><span>Product</span></div>
            <div class="legend-section-title">Relation type</div>
            <div class="legend-item"><div class="legend-color" style="background:var(--text-secondary, #6c757d);"></div><span>Direct relation (grey) / Out-of-scope</span></div>
            <div class="legend-item"><div class="legend-line" style="border-bottom:2.5px solid #ea580c;"></div><span>Searched facet (orange) / Direct relation (both in scope)</span></div>
            <div class="legend-item"><div class="legend-line" style="border-bottom:1.5px solid var(--text-secondary, #6c757d);"></div><span>Indirect relation (hidden when opened from search)</span></div>
            <div class="legend-section-title">Selection</div>
            <div class="legend-item"><div class="legend-line" style="border-bottom:3px solid #c2410c;"></div><span>Into selected node</span></div>
            <div class="legend-item"><div class="legend-line" style="border-bottom:3px solid #f97316;"></div><span>Out of selected node</span></div>
            <div class="legend-item"><span class="legend-lock">&#x1F512;</span><span>Segment not accessible</span></div>
        `;
    }

    function toggleMultiNodeFiltersVisibility() {
        const multiNodeFiltersEl = document.getElementById('mapMultiNodeFilters');
        const filtersGroupEl = document.getElementById('mapFiltersGroup');
        if (!multiNodeFiltersEl || !filtersGroupEl) return;
        const isMultiNode = MapState.mapType === 'multi-node-lineage';
        multiNodeFiltersEl.style.display = isMultiNode ? 'flex' : 'none';
        filtersGroupEl.style.display = isMultiNode ? 'none' : 'block';
    }

    /** Axon Table 1: Multi-Node Lineage uses Organic layout only (general connections, not directional flow) */
    function toggleMultiNodeLayoutOrganicOnly() {
        const layoutSelect = document.getElementById('mapLayoutSelect');
        const layoutBtn = document.getElementById('mapLayoutBtn');
        if (!layoutSelect) return;
        const isMultiNode = MapState.mapType === 'multi-node-lineage';
        if (isMultiNode) {
            MapState.layout = 'force';
            layoutSelect.innerHTML = '<option value="force">Organic</option>';
            layoutSelect.value = 'force';
            layoutSelect.title = 'Multi-Node uses Organic layout only';
            if (layoutBtn) {
                layoutBtn.disabled = true;
                layoutBtn.title = layoutSelect.title;
            }
        } else {
            if (layoutBtn) {
                layoutBtn.disabled = false;
                layoutBtn.title = '';
            }
            if (layoutSelect.options.length === 1) {
                layoutSelect.innerHTML = '<option value="top-to-bottom">Top-To-Bottom</option><option value="left-to-right">Left-To-Right</option><option value="right-to-left">Right-To-Left</option><option value="force">Organic</option>';
                layoutSelect.title = '';
            }
            if (!layoutSelect.querySelector('option[value="' + MapState.layout + '"]')) {
                MapState.layout = 'top-to-bottom';
            }
            layoutSelect.value = MapState.layout;
        }
        syncMapLayoutRichUiFromSelect();
    }

    function bindMultiNodeFilterCheckboxes() {
        const filters = [
            { id: 'mapFilterSystem', key: 'system' },
            { id: 'mapFilterProcess', key: 'process' },
            { id: 'mapFilterProject', key: 'project' },
            { id: 'mapFilterStakeholder', key: 'stakeholder' }
        ];
        filters.forEach(({ id, key }) => {
            const cb = document.getElementById(id);
            if (!cb) return;
            cb.checked = MapState.multiNodeFilters.has(key);
            cb.addEventListener('change', async () => {
                if (cb.checked) MapState.multiNodeFilters.add(key);
                else MapState.multiNodeFilters.delete(key);
                if (MapState.mapType === 'multi-node-lineage') await loadAndRenderMap(false);
            });
        });
    }

    async function loadAndRenderMap(forceRefresh) {
        if (!MapState.canvas) return;

        showLoading();

        try {
            let data = MapState.rawData;
            let category = null;

            // Filter facets before loading map: only facets with search results (rows or ids)
            const rawResults = (window.currentUnisonSearchResults && window.currentUnisonSearchResults.results) ? window.currentUnisonSearchResults.results : {};
            const filteredResults = getFilteredUnisonResults(rawResults);
            const hasUnisonResults = Object.keys(filteredResults).length > 0;

            // Do not override user's map type: when user selects Dataset Lineage or Multi-node, respect it (do not force System Lineage).

            // Category and refetch: Dataset Lineage needs dataset data; when switching from System Lineage we must refetch
            const getActiveCategory = typeof window.getActiveCategoryWithFallback === 'function' ? window.getActiveCategoryWithFallback : null;
            if (MapState.mapType === 'dataset-lineage') {
                category = 'data-sets';
            } else if (getActiveCategory) {
                category = getActiveCategory();
            }
            if (!category) {
                category = MapState.mapType === 'dataset-lineage' ? 'data-sets' : 'system';
            }
            const isSystemRows = (d) => d && d.length && (d[0]['Short Name'] != null || d[0]['Long Name'] != null) && d[0]['Primary Name'] == null;
            const needDatasetDataForDatasetLineage = MapState.mapType === 'dataset-lineage' && data && data.length && isSystemRows(data);
            // Multi-node without search: always fetch system list fresh (ignore cached rawData) so systems + links appear
            const multiNodeNoSearch = MapState.mapType === 'multi-node-lineage' && !hasUnisonResults;
            if (!data || !data.length || forceRefresh || multiNodeNoSearch || needDatasetDataForDatasetLineage) {

                // Prefer data from Unison search results / saved search so map reflects current search (facet-filtered)
                if (hasUnisonResults && category) {
                    const facetId = typeof window.categoryToFacetId === 'function' ? window.categoryToFacetId(category) : (category === 'data-sets' ? 'DATASET' : 'SYSTEM');
                    const tryIds = [facetId, facetId && facetId.replace(/-/g, '_'), 'DATASET', 'SYSTEM'];
                    let facetResult = null;
                    for (const fid of tryIds) {
                        if (!fid) continue;
                        const r = filteredResults[fid];
                        if (r && ((r.rows && r.rows.length) || (r.ids && (Array.isArray(r.ids) ? r.ids.length : r.ids.size)))) {
                            facetResult = r;
                            break;
                        }
                    }
                    if (facetResult && Array.isArray(facetResult.rows) && facetResult.rows.length > 0) {
                        data = facetResult.rows;
                    } else if (facetResult && facetResult.ids) {
                        const ids = Array.isArray(facetResult.ids) ? facetResult.ids : Array.from(facetResult.ids);
                        if (ids.length > 0 && typeof window.fetchFacetDataByIds === 'function') {
                            try {
                                data = await window.fetchFacetDataByIds(category, ids);
                                if (!data || !data.length) data = await fetchCategoryData(category, typeof window.getCurrentQuery === 'function' ? window.getCurrentQuery() : '');
                            } catch (e) {
                                data = await fetchCategoryData(category, typeof window.getCurrentQuery === 'function' ? window.getCurrentQuery() : '');
                            }
                        } else {
                            data = await fetchCategoryData(category, typeof window.getCurrentQuery === 'function' ? window.getCurrentQuery() : '');
                        }
                    } else {
                        data = await fetchCategoryData(category, typeof window.getCurrentQuery === 'function' ? window.getCurrentQuery() : '');
                    }
                } else {
                    const query = typeof window.getCurrentQuery === 'function' ? window.getCurrentQuery() : MapState.searchQuery || '';
                    // Multi-node without search: load systems via direct fetch so systems + stakeholder links always load
                    if (MapState.mapType === 'multi-node-lineage') {
                        category = 'system';
                        data = await fetchSystemListForMultiNode();
                    }
                    if (!data || !data.length) {
                        data = await fetchCategoryData(category, query);
                    }
                }

                // Fallback: if UnisonSearch returned no data (e.g. standalone Maps or service unavailable), use BUDG API
                if (!data || !data.length) {
                    data = await fetchFallbackMapData(category || (MapState.mapType === 'dataset-lineage' ? 'data-sets' : 'system'));
                }

                MapState.rawData = data || [];
            }

            data = MapState.rawData;

            // Gather only facets that have search results (filtered before loading map)
            let multiFacetRows = null;
            if (hasUnisonResults) {
                multiFacetRows = getMultiFacetRowsFromSearchResults(filteredResults);
            }

            // When user searched for datasets (category=data-sets) but chose System Lineage: in-scope = host systems of those datasets + any systems from System facet (so e.g. HR System1 is green when it is the dataset's system)
            if (category === 'data-sets' && MapState.mapType === 'system-lineage' && data && data.length) {
                const isDatasetRows = data[0] && (data[0]['Primary Name'] != null || data[0]['System Short Name'] != null || (data[0].Name != null && data[0]['System'] != null));
                if (isDatasetRows && multiFacetRows) {
                    const systemNamesFromDatasets = new Set();
                    data.forEach(row => {
                        const sn = row['System Short Name'] || row['System'] || row['System Name'] || row.systemName;
                        if (sn) systemNamesFromDatasets.add(sn);
                    });
                    const systemRowsFromDatasets = Array.from(systemNamesFromDatasets).map(name => ({ 'Short Name': name, Name: name }));
                    if (multiFacetRows.systemRows && multiFacetRows.systemRows.length > 0) {
                        const slugSet = new Set(systemRowsFromDatasets.map(r => slugify(r['Short Name'] || r.Name || '')));
                        const combined = systemRowsFromDatasets.slice();
                        multiFacetRows.systemRows.forEach(row => {
                            const name = row['Short Name'] || row['Long Name'] || row.Name || row.name;
                            if (name && !slugSet.has(slugify(name))) {
                                combined.push(row);
                                slugSet.add(slugify(name));
                            }
                        });
                        data = combined;
                    } else {
                        data = systemRowsFromDatasets;
                    }
                    MapState.rawData = data;
                }
            }

            if (!data || !data.length) {
                // Multi-node: allow building from multi-facet only (Axon: Process, Project, Policy, People, Dataset, System)
                if (MapState.mapType === 'multi-node-lineage' && multiFacetRows && (multiFacetRows.systemRows?.length || multiFacetRows.peopleRows?.length || multiFacetRows.datasetRows?.length || multiFacetRows.processRows?.length || multiFacetRows.projectRows?.length || multiFacetRows.policyRows?.length || multiFacetRows.glossaryRows?.length || multiFacetRows.attributeRows?.length)) {
                    data = multiFacetRows.systemRows?.length ? multiFacetRows.systemRows
                        : (multiFacetRows.datasetRows?.length ? multiFacetRows.datasetRows
                            : (multiFacetRows.processRows?.length ? multiFacetRows.processRows
                                : (multiFacetRows.projectRows?.length ? multiFacetRows.projectRows
                                    : (multiFacetRows.policyRows?.length ? multiFacetRows.policyRows
                                        : (multiFacetRows.peopleRows?.length ? multiFacetRows.peopleRows
                                            : (multiFacetRows.glossaryRows?.length ? multiFacetRows.glossaryRows : multiFacetRows.attributeRows))))));
                }
                if (!data || !data.length) {
                    showPlaceholder('No data available. Perform a search or select a category to see the map.');
                    destroyNetwork();
                    return;
                }
            }

            // Build graph based on map type (search results as focus / axon reference)
            const graph = await buildGraphFromResults(data, MapState.mapType, multiFacetRows);
            MapState.inScopeNodeIds = graph.inScopeNodeIds ? new Set(graph.inScopeNodeIds) : new Set();
            // When opened without search: treat all nodes as in-scope (all orange) so no grey systems from interface/parent data
            if (!hasUnisonResults && graph.nodes && graph.nodes.length > 0) {
                MapState.inScopeNodeIds = new Set(graph.nodes.map(n => n.id));
                // Sync graph.inScopeNodeIds so hops filter BFS roots include ALL nodes
                graph.inScopeNodeIds = new Set(MapState.inScopeNodeIds);
            }
            MapState.fullGraph = graph;
            // When opened from search: show only direct relations (searched facet in orange, direct connections in grey)
            let graphToRender = graph;
            if (hasUnisonResults && MapState.inScopeNodeIds.size > 0) {
                if (MapState.mapType === 'system-lineage') {
                    graphToRender = filterToDirectRelationsOnly(graph, MapState.inScopeNodeIds);
                }
                // Multi-node: do not filter to direct-only so all facets (people, process, attribute, etc.) stay visible
            }
            // Apply hops filter for all map types
            graphToRender = applyHopsFilter(graphToRender);
            MapState.lastGraph = graphToRender;
            MapState.selectedNodeId = null;

            saveToHistory();

            renderNetwork(graphToRender);
            updateMapsLegend();
            emitInsightMapFilterOptions(graph);

            // Apply overlay when not none
            if (MapState.overlay && MapState.overlay !== 'none') {
                applyOverlay();
            }
        } catch (error) {
            console.error('[MAPS] Failed to render map', error);
            showPlaceholder('Failed to build map. Try refreshing the view.');
            destroyNetwork();
        } finally {
            hideLoading();
        }
    }

    async function fetchCategoryData(category, query) {
        try {
            const cat = category || (MapState.mapType === 'dataset-lineage' ? 'data-sets' : 'system');
            const module = typeof window.categoryToModule === 'function'
                ? window.categoryToModule(cat)
                : (typeof categoryToModule === 'function' ? categoryToModule(cat) : cat.toLowerCase().replace(/\s+/g, '-').replace('data-sets', 'dataset'));

            if (!module) return [];

            const params = new URLSearchParams();
            if (query) {
                params.append('q', query);
            }

            const url = `/UnisonSearch/${encodeURIComponent(module)}${params.toString() ? `?${params.toString()}` : ''}`;

            const resp = await fetch(url, {
                headers: { 'Accept': 'application/json' },
                credentials: 'include'
            });

            if (!resp.ok) {
                return [];
            }

            const json = await resp.json();
            return Array.isArray(json) ? json : [];
        } catch (e) {
            console.error('[MAPS] Error fetching data:', e);
            return [];
        }
    }

    /**
     * Load system list for multi-node map (no search). Uses GET /api/system/list so systems + stakeholder edges load reliably.
     */
    async function fetchSystemListForMultiNode() {
        try {
            const r = await fetch('/api/system/list', { credentials: 'include', headers: { 'Accept': 'application/json' } });
            if (!r.ok) return await fetchFallbackMapData('system');
            const list = await r.json();
            const arr = Array.isArray(list) ? list : (list && list.data && Array.isArray(list.data) ? list.data : []);
            if (arr.length === 0) return await fetchFallbackMapData('system');
            return arr.map(function (x) {
                const id = x.id != null ? x.id : x.ID;
                return {
                    'Short Name': x.name || x.Short_Name || x.Name,
                    'Name': x.name || x.Name,
                    'Long Name': x.longName || x.Long_Name || x.name,
                    'Parent Short Name': null,
                    'Parent System': (x.parent && x.parent.name) ? x.parent.name : null,
                    'ID': id,
                    id: id
                };
            });
        } catch (e) {
            mapsDbgWarn('[MAPS] fetchSystemListForMultiNode failed', e);
            return await fetchFallbackMapData('system');
        }
    }

    /**
     * Fallback data for map when UnisonSearch returns nothing (e.g. opening Maps directly or UnisonSearch unavailable).
     * Uses BUDG REST API so the map always has something to show.
     */
    async function fetchFallbackMapData(category) {
        try {
            const svc = window.BUDG_API_SERVICE;
            if (!svc) return [];

            const cat = category || (MapState.mapType === 'dataset-lineage' ? 'data-sets' : 'system');

            if (cat === 'system' || cat === 'systems') {
                let list = null;
                if (svc && typeof svc.getSystemsList === 'function') {
                    list = await svc.getSystemsList().catch(() => null);
                }
                if (!list || !Array.isArray(list) || list.length === 0) {
                    const r = await fetch('/api/system/list', { credentials: 'include', headers: { 'Accept': 'application/json' } }).catch(() => null);
                    if (r && r.ok) list = await r.json();
                }
                if (Array.isArray(list) && list.length > 0) {
                    return list.map(function (x) {
                        const id = x.id != null ? x.id : x.ID;
                        return {
                            'Short Name': x.name || x.Short_Name,
                            'Name': x.name || x.Name,
                            'Long Name': x.longName || x.Long_Name || x.name,
                            'Parent Short Name': x.parent_id ? null : null,
                            'Parent System': (x.parent && x.parent.name) ? x.parent.name : null,
                            'ID': id,
                            id: id
                        };
                    });
                }
            }

            if (cat === 'data-sets' || cat === 'dataset' || cat === 'datasets') {
                const resp = await fetch('/api/dataset/list', { credentials: 'include', headers: { 'Accept': 'application/json' } });
                if (!resp.ok) return [];
                const list = await resp.json();
                if (Array.isArray(list) && list.length > 0) {
                    return list.map(function (x) {
                        const name = x.name || x.primaryName || x.PrimaryName;
                        return {
                            'Primary Name': name,
                            'Name': name,
                            'System Short Name': x.systemName || (x.system && x.system.name) || null,
                            'System': x.systemName || (x.system && x.system.name) || null
                        };
                    });
                }
            }

            return [];
        } catch (e) {
            mapsDbgWarn('[MAPS] Fallback data fetch failed:', e);
            return [];
        }
    }

    /**
     * Filter Unison search results to only facets that have data (rows or ids).
     * Used before loading the map so we only consider facets with search results.
     */
    function getFilteredUnisonResults(results) {
        if (!results || typeof results !== 'object') return {};
        const filtered = {};
        for (const [facetId, r] of Object.entries(results)) {
            if (!r) continue;
            const hasRows = r.rows && Array.isArray(r.rows) && r.rows.length > 0;
            const hasIds = r.ids && (Array.isArray(r.ids) ? r.ids.length > 0 : (r.ids.size != null ? r.ids.size > 0 : false));
            if (hasRows || hasIds) filtered[facetId] = r;
        }
        return filtered;
    }

    function getMultiFacetRowsFromSearchResults(results) {
        const out = { systemRows: [], peopleRows: [], datasetRows: [], processRows: [], projectRows: [], policyRows: [], glossaryRows: [], attributeRows: [] };
        const facetKeys = [
            { key: 'SYSTEM', outKey: 'systemRows' },
            { key: 'PEOPLE', outKey: 'peopleRows' },
            { key: 'DATASET', outKey: 'datasetRows' },
            { key: 'PROCESS', outKey: 'processRows' },
            { key: 'PROJECT', outKey: 'projectRows' },
            { key: 'POLICY', outKey: 'policyRows' },
            { key: 'GLOSSARY', outKey: 'glossaryRows' },
            { key: 'ATTRIBUTE', outKey: 'attributeRows' }
        ];
        const aliases = { 'DATA_SETS': 'DATASET', 'DATA-SETS': 'DATASET', 'PERSON': 'PEOPLE' };
        facetKeys.forEach(({ key, outKey }) => {
            const r = results[key] || results[aliases[key]] || results[key.toLowerCase()];
            if (r && Array.isArray(r.rows) && r.rows.length > 0) {
                out[outKey] = r.rows;
            } else if (r && r.ids && (Array.isArray(r.ids) ? r.ids.length : r.ids.size) > 0) {
                const ids = Array.isArray(r.ids) ? r.ids : Array.from(r.ids);
                out[outKey + 'Ids'] = ids;
            }
        });
        return out;
    }

    async function buildGraphFromResults(rows, mapType, multiFacetRows) {
        const nodesMap = new Map();
        const edges = [];
        const edgeMap = new Map();

        if (mapType === 'system-lineage') {
            return await buildSystemLineageGraph(rows);
        } else if (mapType === 'dataset-lineage') {
            return await buildDatasetLineageGraph(rows, multiFacetRows);
        } else if (mapType === 'multi-node-lineage') {
            return await buildMultiNodeLineageGraph(rows, multiFacetRows);
        }

        return { nodes: [], edges: [] };
    }

    /**
     * Restrict graph to direct relations only: in-scope nodes (orange) + nodes that share an edge with an in-scope node (grey).
     * Used when opening map from search: System Lineage shows searched system(s) in orange and only directly connected systems in grey;
     * Multi-node shows searched facets and only directly linked facets.
     */
    function filterToDirectRelationsOnly(graph, inScopeNodeIds) {
        if (!graph || !graph.nodes || !graph.edges) return graph;
        const scope = inScopeNodeIds && (inScopeNodeIds.size != null ? inScopeNodeIds.size > 0 : inScopeNodeIds.length > 0)
            ? new Set(Array.isArray(inScopeNodeIds) ? inScopeNodeIds : Array.from(inScopeNodeIds))
            : new Set();
        if (scope.size === 0) return graph;
        const allowed = new Set(scope);
        (graph.edges || []).forEach(e => {
            if (scope.has(e.from) || scope.has(e.to)) {
                allowed.add(e.from);
                allowed.add(e.to);
            }
        });
        const filteredNodes = (graph.nodes || []).filter(n => allowed.has(n.id));
        const filteredEdges = (graph.edges || []).filter(e => allowed.has(e.from) && allowed.has(e.to));
        return {
            nodes: filteredNodes,
            edges: filteredEdges,
            inScopeNodeIds: graph.inScopeNodeIds
        };
    }

    /** Restrict graph to nodes within hopsLimit steps from in-scope (root) nodes. */
    function applyHopsFilter(graph) {
        if (!graph || !graph.nodes || !graph.nodes.length) return graph;
        const maxHops = Math.min(99, Math.max(1, MapState.hopsLimit || 15));

        // Determine roots: in-scope nodes from search, otherwise all nodes marked inScope
        let roots = graph.inScopeNodeIds && graph.inScopeNodeIds.size
            ? Array.from(graph.inScopeNodeIds)
            : graph.nodes.filter(n => n.inScope).map(n => n.id);
        if (roots.length === 0 && graph.nodes.length > 0) roots.push(graph.nodes[0].id);

        // If every node is a root AND hops is at max (15+), no filtering needed
        if (roots.length >= graph.nodes.length && maxHops >= 15) return graph;

        // When all nodes are roots (no search context), use BFS from all roots
        // The hops limit still restricts how far the traversal goes from ANY root,
        // which effectively prunes leaf branches beyond hopsLimit steps from the roots.
        // For this to be meaningful we need edge connectivity.
        const outEdges = new Map();
        const inEdges = new Map();
        (graph.edges || []).forEach(e => {
            if (!outEdges.has(e.from)) outEdges.set(e.from, []);
            outEdges.get(e.from).push(e);
            if (!inEdges.has(e.to)) inEdges.set(e.to, []);
            inEdges.get(e.to).push(e);
        });

        // When all nodes are roots and hops < 15, use the first root node only
        // so hops actually limits how many connections are shown from that starting point
        let bfsRoots = roots;
        if (roots.length >= graph.nodes.length && maxHops < 15) {
            // Find nodes with the most connections (degree) to use as the starting root
            const degreeMap = new Map();
            graph.nodes.forEach(n => degreeMap.set(n.id, 0));
            (graph.edges || []).forEach(e => {
                degreeMap.set(e.from, (degreeMap.get(e.from) || 0) + 1);
                degreeMap.set(e.to, (degreeMap.get(e.to) || 0) + 1);
            });
            // Sort by degree descending and use the top node as BFS root
            const sorted = [...degreeMap.entries()].sort((a, b) => b[1] - a[1]);
            bfsRoots = sorted.length ? [sorted[0][0]] : roots.slice(0, 1);
        }

        const allowed = new Set();
        const queue = bfsRoots.map(id => ({ id, hop: 0 }));
        const seen = new Set();
        while (queue.length) {
            const { id, hop } = queue.shift();
            if (seen.has(id)) continue;
            seen.add(id);
            if (hop <= maxHops) allowed.add(id);
            if (hop >= maxHops) continue;
            (outEdges.get(id) || []).forEach(edge => queue.push({ id: edge.to, hop: hop + 1 }));
            (inEdges.get(id) || []).forEach(edge => queue.push({ id: edge.from, hop: hop + 1 }));
        }
        const filteredNodes = graph.nodes.filter(n => allowed.has(n.id));
        const filteredEdges = (graph.edges || []).filter(e => allowed.has(e.from) && allowed.has(e.to));
        return {
            nodes: filteredNodes,
            edges: filteredEdges,
            inScopeNodeIds: graph.inScopeNodeIds
        };
    }

    async function buildSystemLineageGraph(rows) {
        const nodesMap = new Map();
        const edges = [];
        const edgeMap = new Map();
        const inScopeNodeIds = new Set();

        // Create system nodes (Axon: orange = in scope of active search)
        rows.forEach((row, index) => {
            const systemName = row['Short Name'] || row['Long Name'] || row.Name || `System ${index + 1}`;
            const systemId = `system:${slugify(systemName)}`;
            inScopeNodeIds.add(systemId);

            if (!nodesMap.has(systemId)) {
                nodesMap.set(systemId, {
                    id: systemId,
                    label: systemName,
                    group: 'system',
                    level: 1,
                    inScope: true,
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
                                dashes: true,
                                color: '#94a3b8'
                            });
                }
            }
        });

        // Fetch interface data: solid = linking attributes (dataAttributes > 0), dotted = system interface only (dataAttributes = 0)
        try {
            const interfaceData = await fetchCategoryData('interface', '');
            if (interfaceData && interfaceData.length > 0) {
                interfaceData.forEach(row => {
                    const sourceSystem = row['Source System Short Name'] || row['Source System'];
                    const targetSystem = row['Target System Short Name'] || row['Target System'];
                    const interfaceName = row.Name || row['Primary Name'] || '';
                    const dataAttributes = row.dataAttributes ?? row.data_attributes ?? 0;
                    const hasLinkingAttributes = dataAttributes > 0;

                    if (sourceSystem && targetSystem) {
                        const sourceId = `system:${slugify(sourceSystem)}`;
                        const targetId = `system:${slugify(targetSystem)}`;

                        if (!nodesMap.has(sourceId)) {
                            nodesMap.set(sourceId, {
                                id: sourceId,
                                label: sourceSystem,
                                group: 'system',
                                level: 1,
                                meta: { category: 'system' }
                            });
                        }
                        if (!nodesMap.has(targetId)) {
                            nodesMap.set(targetId, {
                                id: targetId,
                                label: targetSystem,
                                group: 'system',
                                level: 2,
                                meta: { category: 'system' }
                            });
                        }

                        const edgeId = `${sourceId}->${targetId}-${slugify(interfaceName)}`;
                        if (!edgeMap.has(edgeId)) {
                            edgeMap.set(edgeId, true);
                            edges.push({
                                id: edgeId,
                                from: sourceId,
                                to: targetId,
                                label: interfaceName,
                                dashes: !hasLinkingAttributes,
                                color: hasLinkingAttributes ? '#64748b' : '#94a3b8'
                            });
                        }
                    }
                });
            }
        } catch (error) {
            mapsDbgWarn('[MAPS] Could not fetch interface data:', error);
        }

        return {
            nodes: Array.from(nodesMap.values()),
            edges,
            inScopeNodeIds
        };
    }

    async function buildDatasetLineageGraph(rows, multiFacetRows) {
        const nodesMap = new Map();
        const edges = [];
        const edgeMap = new Map();
        const inScopeNodeIds = new Set();

        // 1) Dataset nodes from search (in scope) and their host systems
        rows.forEach((row, index) => {
            const datasetName = row['Primary Name'] || row.Name || row['Short Name'] || `Dataset ${index + 1}`;
            const rawId = row.ID != null ? String(row.ID) : (row.id != null ? String(row.id) : null);
            const refNumber = row.Ref || row.Reference || row.refNumber || row.RefNumber || row.ref || (rawId ? `DS-${rawId}` : `DS-${index + 1}`);
            const datasetId = `dataset:${rawId || slugify(datasetName)}`;

            const systemName = row['System Short Name'] || row['System'] || row['System Name'] || '';

            // Axon-style: label = system name + dataset name + searchable reference number
            const displayLabel = systemName
                ? `${systemName}\n${datasetName}\n(${refNumber})`
                : `${datasetName}\n(${refNumber})`;

            if (!nodesMap.has(datasetId)) {
                inScopeNodeIds.add(datasetId);
                nodesMap.set(datasetId, {
                    id: datasetId,
                    label: displayLabel,
                    group: 'dataset',
                    level: 1,
                    inScope: true,
                    meta: {
                        category: 'dataset',
                        raw: row,
                        systemName: systemName || null,
                        datasetName: datasetName,
                        refNumber: refNumber
                    }
                });
            }

            if (systemName) {
                const systemNodeId = `system:${slugify(systemName)}`;
                if (!nodesMap.has(systemNodeId)) {
                    nodesMap.set(systemNodeId, {
                        id: systemNodeId,
                        label: systemName,
                        group: 'system',
                        level: 0,
                        inScope: false,
                        meta: { category: 'system' }
                    });
                }

                const edgeId = `${systemNodeId}->${datasetId}`;
                if (!edgeMap.has(edgeId)) {
                    edgeMap.set(edgeId, true);
                    edges.push({
                        id: edgeId,
                        from: systemNodeId,
                        to: datasetId,
                        label: 'feeds',
                        dashes: false,
                        color: '#ea580c'
                    });
                }
            }
        });

        // 1b) Direct relations only: fetch direct dataset relationships for each searched dataset and add only those datasets + edges
        const rowDatasetIds = rows.map(r => (r.ID != null ? String(r.ID) : (r.id != null ? String(r.id) : null))).filter(Boolean);
        for (const datasetId of rowDatasetIds) {
            try {
                const relResp = await fetch(`/api/dataset-relationships/${datasetId}`, {
                    method: 'GET',
                    credentials: 'include',
                    headers: { 'Content-Type': 'application/json' }
                });
                if (!relResp.ok) continue;
                const relData = await relResp.json();
                const inbound = relData.inbound || [];
                const outbound = relData.outbound || [];
                const addDirectRelated = (rel, sourceId, targetId, otherSystemName) => {
                    const otherId = `dataset:${sourceId === datasetId ? targetId : sourceId}`;
                    if (nodesMap.has(otherId)) {
                        const edgeId = `dataset:${sourceId}->dataset:${targetId}`;
                        if (!edgeMap.has(edgeId)) {
                            edgeMap.set(edgeId, true);
                            edges.push({
                                id: edgeId,
                                from: `dataset:${sourceId}`,
                                to: `dataset:${targetId}`,
                                label: 'relates to',
                                dashes: false,
                                color: '#64748b'
                            });
                        }
                        return;
                    }
                    const otherDsId = sourceId === datasetId ? targetId : sourceId;
                    const otherName = (rel.targetDatasetName || rel.sourceDatasetName || rel.targetPrimaryName || rel.sourcePrimaryName) || `Dataset ${otherDsId}`;
                    const sysName = otherSystemName || (rel.systemName || rel.targetSystemName || rel.sourceSystemName) || '';
                    const displayLabel = sysName ? `${sysName}\n${otherName}` : otherName;
                    nodesMap.set(otherId, {
                        id: otherId,
                        label: displayLabel,
                        group: 'dataset',
                        level: 1,
                        inScope: false,
                        meta: { category: 'dataset', systemName: sysName || null, datasetName: otherName }
                    });
                    const edgeId = `dataset:${sourceId}->dataset:${targetId}`;
                    if (!edgeMap.has(edgeId)) {
                        edgeMap.set(edgeId, true);
                        edges.push({
                            id: edgeId,
                            from: `dataset:${sourceId}`,
                            to: `dataset:${targetId}`,
                            label: 'relates to',
                            dashes: false,
                            color: '#64748b'
                        });
                    }
                    if (sysName) {
                        const systemNodeId = `system:${slugify(sysName)}`;
                        if (!nodesMap.has(systemNodeId)) {
                            nodesMap.set(systemNodeId, {
                                id: systemNodeId,
                                label: sysName,
                                group: 'system',
                                level: 0,
                                inScope: false,
                                meta: { category: 'system' }
                            });
                        }
                        const sysEdgeId = `${systemNodeId}->${otherId}`;
                        if (!edgeMap.has(sysEdgeId)) {
                            edgeMap.set(sysEdgeId, true);
                            edges.push({
                                id: sysEdgeId,
                                from: systemNodeId,
                                to: otherId,
                                label: 'feeds',
                                dashes: false,
                                color: '#ea580c'
                            });
                        }
                    }
                };
                const myId = String(datasetId);
                inbound.forEach(rel => {
                    const srcId = rel.sourceDatasetId != null ? String(rel.sourceDatasetId) : (rel.sourceDatasetID != null ? String(rel.sourceDatasetID) : null);
                    const tgtId = rel.targetDatasetId != null ? String(rel.targetDatasetId) : (rel.targetDatasetID != null ? String(rel.targetDatasetID) : null);
                    if (tgtId === myId && srcId) addDirectRelated(rel, srcId, tgtId, rel.sourceSystemName || rel.systemName);
                });
                outbound.forEach(rel => {
                    const srcId = rel.sourceDatasetId != null ? String(rel.sourceDatasetId) : (rel.sourceDatasetID != null ? String(rel.sourceDatasetID) : null);
                    const tgtId = rel.targetDatasetId != null ? String(rel.targetDatasetId) : (rel.targetDatasetID != null ? String(rel.targetDatasetID) : null);
                    if (srcId === myId && tgtId) addDirectRelated(rel, srcId, tgtId, rel.targetSystemName || rel.systemName);
                });
            } catch (e) {
                mapsDbgWarn('[MAPS] Could not fetch dataset relationships for', datasetId, e);
            }
        }

        // 2) Add systems from unison SYSTEM facet only when they are already in the graph (host of a dataset we show)
        const systemRows = multiFacetRows && multiFacetRows.systemRows ? multiFacetRows.systemRows : [];
        systemRows.forEach((row, index) => {
            const systemName = row['Short Name'] || row['Long Name'] || row.Name || row.name || `System ${index + 1}`;
            const systemNodeId = `system:${slugify(systemName)}`;
            if (!nodesMap.has(systemNodeId)) return;
            const existing = nodesMap.get(systemNodeId);
            existing.inScope = true;
            if (row && Object.keys(row).length) existing.meta.raw = row;
            inScopeNodeIds.add(systemNodeId);
            rows.forEach(r => {
                const hostSystem = r['System Short Name'] || r['System'] || r['System Name'] || '';
                if (hostSystem && slugify(hostSystem) === slugify(systemName)) {
                    const dsName = r['Primary Name'] || r.Name || r['Short Name'] || '';
                    const rawId = r.ID != null ? String(r.ID) : (r.id != null ? String(r.id) : null);
                    const datasetId = `dataset:${rawId || slugify(dsName)}`;
                    const edgeId = `${systemNodeId}->${datasetId}`;
                    if (nodesMap.has(datasetId) && !edgeMap.has(edgeId)) {
                        edgeMap.set(edgeId, true);
                        edges.push({
                            id: edgeId,
                            from: systemNodeId,
                            to: datasetId,
                            label: 'feeds',
                            dashes: false,
                            color: '#ea580c'
                        });
                    }
                }
            });
        });

        return {
            nodes: Array.from(nodesMap.values()),
            edges,
            inScopeNodeIds
        };
    }

    async function buildMultiNodeLineageGraph(rows, multiFacetRows) {
        // Multi-node: with search = multiple facets (Axon-style); without search = only systems + people (stakeholders + unlinked)
        const noSearch = !multiFacetRows || (typeof multiFacetRows === 'object' && !multiFacetRows.systemRows && !multiFacetRows.peopleRows && !multiFacetRows.datasetRows);
        const nodesMap = new Map();
        const edges = [];
        const edgeMap = new Map();
        const filters = MapState.multiNodeFilters;
        const API = window.BUDG_API_SERVICE || window.apiService;

        const searchOnlyGroups = new Set(['dataset', 'glossary', 'attribute', 'policy']);
        function addNode(id, label, group, meta, inScope) {
            if (noSearch) {
                if (group !== 'system' && group !== 'stakeholder' && group !== 'people') return;
            } else if (!filters.has(group) && !searchOnlyGroups.has(group)) return;
            if (nodesMap.has(id)) return;
            const nodeGroup = group === 'stakeholder' ? 'people' : group;
            nodesMap.set(id, {
                id,
                label: label || id,
                group: nodeGroup,
                level: 1,
                inScope: !!inScope,
                meta: meta || { category: group }
            });
        }

        function addEdge(from, to, label, key) {
            const edgeId = key || `${from}->${to}`;
            if (edgeMap.has(edgeId)) return;
            edgeMap.set(edgeId, true);
            edges.push({ id: edgeId, from, to, label: label || '', dashes: false, color: '#94a3b8' });
        }

        // Resolve node id by type and name/id (Axon: link Process/Project/Policy to Data Set, Attribute, System, Glossary)
        function resolveNodeId(nodeGroup, nameOrId) {
            if (!nameOrId || String(nameOrId).trim() === '') return null;
            const s = slugify(String(nameOrId).trim());
            for (const [id, node] of nodesMap) {
                if (node.group !== nodeGroup) continue;
                if (node.id === id && (id === nameOrId || id.endsWith(s) || slugify((node.label || '')) === s)) return id;
                if (slugify(String(node.label || '')) === s) return id;
                if (String(node.id).replace(/^[^:]+:/, '') === s) return id;
            }
            return null;
        }

        // 1) System nodes: only add systems that are linked (host of a dataset or system of a person) so no disconnected "System" appears
        const systemIds = new Set();
        const usedSystemNames = new Set();
        const usedSystemLabels = new Map();
        if (!noSearch && multiFacetRows) {
            (multiFacetRows.datasetRows || []).forEach((row) => {
                const sn = row['System Short Name'] || row['System'] || row['System Name'] || row.systemName;
                if (sn) {
                    const s = slugify(sn);
                    usedSystemNames.add(s);
                    if (!usedSystemLabels.has(s)) usedSystemLabels.set(s, sn);
                }
            });
            (multiFacetRows.peopleRows || []).forEach((row) => {
                const sn = row.systemName || row['System Name'] || row['System Short Name'];
                if (sn) {
                    const s = slugify(sn);
                    usedSystemNames.add(s);
                    if (!usedSystemLabels.has(s)) usedSystemLabels.set(s, sn);
                }
            });
        }
        const rowsLookLikeSystems = rows && rows.length && (rows[0]['Short Name'] != null || rows[0]['Long Name'] != null || rows[0]['Name'] != null || rows[0].name != null || rows[0].id != null || rows[0].ID != null);
        const systemRows = (multiFacetRows && multiFacetRows.systemRows && multiFacetRows.systemRows.length) ? multiFacetRows.systemRows : (noSearch && rows && rows.length ? rows : (rowsLookLikeSystems ? rows : []));
        (systemRows || []).forEach((row, index) => {
            const name = row['Short Name'] || row['Long Name'] || row.Name || row.name || `System ${index + 1}`;
            const sid = `system:${slugify(name)}`;
            if (!noSearch && usedSystemNames.size > 0 && !usedSystemNames.has(slugify(name))) return;
            systemIds.add(sid);
            addNode(sid, name, 'system', { category: 'system', raw: row }, false);
        });
        if (!noSearch && usedSystemNames.size > 0) {
            usedSystemLabels.forEach((label, s) => {
                const sid = `system:${s}`;
                if (!systemIds.has(sid)) {
                    systemIds.add(sid);
                    addNode(sid, label, 'system', { category: 'system' }, false);
                }
            });
        }

        // 1b) Dataset facet from search only – do NOT add extra system nodes; only link to systems already in search results (so filter count matches)
        if (!noSearch && multiFacetRows && multiFacetRows.datasetRows && multiFacetRows.datasetRows.length && filters.has('system')) {
            multiFacetRows.datasetRows.forEach((row) => {
                const systemName = row['System Short Name'] || row['System'] || row['System Name'] || row.systemName;
                if (systemName) {
                    const sid = `system:${slugify(systemName)}`;
                    if (systemIds.has(sid)) {
                        // Only use system if it was already in search results (systemRows)
                    }
                    // Do not add new system nodes so filter count (e.g. 1 of 56) matches what appears on map
                }
            });
        }

        // 1c) People from search results only – only the people in search; do NOT add extra systems or fetch systems from API (so 5 people = 5 on map)
        if (!noSearch && multiFacetRows && multiFacetRows.peopleRows && multiFacetRows.peopleRows.length && filters.has('stakeholder')) {
            for (const row of multiFacetRows.peopleRows) {
                const personId = row.id != null ? row.id : row.ID || row.personId;
                const fn = row['First Name'] || row.first_name || row.First_Name || row.firstName || row.FirstName;
                const ln = row['Last Name'] || row.last_name || row.Last_Name || row.lastName || row.LastName;
                const name = (row.name || row.Name || row['Primary Name'] || row.primaryName || row.displayName || row.PersonName || (fn && ln ? (fn + ' ' + ln).trim() : (fn || ln)) || '').trim() || `Person ${personId}`;
                if (personId == null) continue;
                const nid = `stakeholder:${personId}`;
                addNode(nid, name, 'stakeholder', { category: 'stakeholder', raw: row }, true);
                const systemName = row.systemName || row['System Name'] || row['System Short Name'];
                if (systemName) {
                    const sid = `system:${slugify(systemName)}`;
                    if (systemIds.has(sid)) {
                        addEdge(sid, nid, 'stakeholder', `${sid}->${nid}`);
                    }
                }
                // Cross-linking to all related objects is handled in step 4 below
            }
        }

        // 1d) Dataset nodes from search only (not when no search)
        if (!noSearch && multiFacetRows && multiFacetRows.datasetRows && multiFacetRows.datasetRows.length) {
            multiFacetRows.datasetRows.forEach((row) => {
                const datasetName = row['Primary Name'] || row.Name || row['Short Name'] || row.name || `Dataset`;
                const rawId = row.ID != null ? row.ID : row.id;
                const datasetId = `dataset:${rawId || slugify(datasetName)}`;
                const systemName = row['System Short Name'] || row['System'] || row['System Name'] || row.systemName;
                if (!nodesMap.has(datasetId)) {
                    nodesMap.set(datasetId, {
                        id: datasetId,
                        label: datasetName,
                        group: 'dataset',
                        level: 1,
                        inScope: true,
                        meta: { category: 'dataset', raw: row }
                    });
                }
                if (systemName) {
                    const sid = `system:${slugify(systemName)}`;
                    if (systemIds.has(sid)) {
                        addEdge(sid, datasetId, 'feeds', `${sid}->${datasetId}`);
                    }
                    // Do not add new system nodes so filter count (e.g. System 1 of 56) matches map
                }
            });
        }

        // 1e) Axon: Glossary nodes from search – link Glossary to Data Set, Attribute
        if (multiFacetRows && multiFacetRows.glossaryRows && multiFacetRows.glossaryRows.length) {
            multiFacetRows.glossaryRows.forEach((row, index) => {
                const name = row.Name || row['Primary Name'] || row['Short Name'] || row.name || `Glossary ${index + 1}`;
                const rawId = row.ID != null ? row.ID : row.id;
                const gid = `glossary:${rawId != null ? rawId : slugify(name)}`;
                addNode(gid, name, 'glossary', { category: 'glossary', raw: row }, false);
                const dsName = row['Data Set Name'] || row['Dataset Name'] || row.dataSetName;
                if (dsName) {
                    const did = resolveNodeId('dataset', dsName) || resolveNodeId('dataset', row['Data Set Name_ID'] || row.datasetId);
                    if (did) addEdge(gid, did, 'describes', `${gid}->${did}`);
                }
                const attrName = row['Attribute'] || row['Attribute Name'] || row.attributeName;
                if (attrName) {
                    const aid = resolveNodeId('attribute', attrName) || resolveNodeId('attribute', row['Attribute_ID'] || row.attributeId);
                    if (aid) addEdge(gid, aid, 'defined by', `${gid}->${aid}`);
                }
            });
        }

        // 1f) Attribute from search only (not when no search)
        if (!noSearch && multiFacetRows && multiFacetRows.attributeRows && multiFacetRows.attributeRows.length) {
            multiFacetRows.attributeRows.forEach((row, index) => {
                const name = row.Name || row['Primary Name'] || row['Short Name'] || row.name || `Attribute ${index + 1}`;
                const rawId = row.ID != null ? row.ID : row.id;
                const aid = `attribute:${rawId != null ? rawId : slugify(name)}`;
                addNode(aid, name, 'attribute', { category: 'attribute', raw: row }, false);
                const dsName = row['Data Set Name'] || row['Dataset Name'] || row.dataSetName;
                if (dsName) {
                    const did = resolveNodeId('dataset', dsName) || resolveNodeId('dataset', row['Data Set Name_ID'] || row.datasetId);
                    if (did) addEdge(aid, did, 'belongs to', `${aid}->${did}`);
                }
                const sysName = row['System Short Name'] || row['System'] || row.systemName;
                if (sysName) {
                    const sid = resolveNodeId('system', sysName);
                    if (sid) addEdge(sid, aid, 'source', `${sid}->${aid}`);
                }
            });
        }

        // 1g) Process from search only (not when no search)
        if (!noSearch && multiFacetRows && multiFacetRows.processRows && multiFacetRows.processRows.length && filters.has('process')) {
            multiFacetRows.processRows.forEach((row, index) => {
                const name = row.Name || row['Primary Name'] || row['Short Name'] || row.name || `Process ${index + 1}`;
                const rawId = row.ID != null ? row.ID : row.id;
                const pid = `process:${rawId != null ? rawId : slugify(name)}`;
                addNode(pid, name, 'process', { category: 'process', raw: row }, true);
                const dsName = row['Data Set Name'] || row['Dataset Name'] || row.dataSetName;
                if (dsName) { const did = resolveNodeId('dataset', dsName); if (did) addEdge(pid, did, 'uses', `${pid}->${did}`); }
                const attrName = row['Attribute'] || row['Attribute Name'] || row.attributeName;
                if (attrName) { const aid = resolveNodeId('attribute', attrName); if (aid) addEdge(pid, aid, 'uses', `${pid}->${aid}`); }
                const sysName = row['System Short Name'] || row['System'] || row.systemName;
                if (sysName) { const sid = resolveNodeId('system', sysName); if (sid) addEdge(pid, sid, 'impacts', `${pid}->${sid}`); }
                const glossName = row['Glossary Name'] || row['Glossary'] || row.glossaryName;
                if (glossName) { const gid = resolveNodeId('glossary', glossName); if (gid) addEdge(pid, gid, 'references', `${pid}->${gid}`); }
            });
        }

        // 1h) Project from search only (not when no search)
        if (!noSearch && multiFacetRows && multiFacetRows.projectRows && multiFacetRows.projectRows.length && filters.has('project')) {
            multiFacetRows.projectRows.forEach((row, index) => {
                const name = row.Name || row['Primary Name'] || row['Short Name'] || row.name || row.projectName || `Project ${index + 1}`;
                const rawId = row.ID != null ? row.ID : row.id;
                const pid = `project:${rawId != null ? rawId : slugify(name)}`;
                addNode(pid, name, 'project', { category: 'project', raw: row }, true);
                const dsName = row['Data Set Name'] || row['Dataset Name'] || row.dataSetName;
                if (dsName) { const did = resolveNodeId('dataset', dsName); if (did) addEdge(pid, did, 'impacts', `${pid}->${did}`); }
                const attrName = row['Attribute'] || row['Attribute Name'] || row.attributeName;
                if (attrName) { const aid = resolveNodeId('attribute', attrName); if (aid) addEdge(pid, aid, 'impacts', `${pid}->${aid}`); }
                const sysName = row['System Short Name'] || row['System'] || row.systemName;
                if (sysName) { const sid = resolveNodeId('system', sysName); if (sid) addEdge(pid, sid, 'impacts', `${pid}->${sid}`); }
                const glossName = row['Glossary Name'] || row['Glossary'] || row.glossaryName;
                if (glossName) { const gid = resolveNodeId('glossary', glossName); if (gid) addEdge(pid, gid, 'references', `${pid}->${gid}`); }
            });
        }

        // 1i) Policy from search only (not when no search)
        if (!noSearch && multiFacetRows && multiFacetRows.policyRows && multiFacetRows.policyRows.length) {
            multiFacetRows.policyRows.forEach((row, index) => {
                const name = row.Name || row['Primary Name'] || row['Short Name'] || row.name || `Policy ${index + 1}`;
                const rawId = row.ID != null ? row.ID : row.id;
                const pid = `policy:${rawId != null ? rawId : slugify(name)}`;
                addNode(pid, name, 'policy', { category: 'policy', raw: row }, false);
                const dsName = row['Data Set Name'] || row['Dataset Name'] || row.dataSetName;
                if (dsName) { const did = resolveNodeId('dataset', dsName); if (did) addEdge(pid, did, 'governs', `${pid}->${did}`); }
                const attrName = row['Attribute'] || row['Attribute Name'] || row.attributeName;
                if (attrName) { const aid = resolveNodeId('attribute', attrName); if (aid) addEdge(pid, aid, 'governs', `${pid}->${aid}`); }
                const sysName = row['System Short Name'] || row['System'] || row.systemName;
                if (sysName) { const sid = resolveNodeId('system', sysName); if (sid) addEdge(pid, sid, 'controlled by', `${pid}->${sid}`); }
                const glossName = row['Glossary Name'] || row['Glossary'] || row.glossaryName;
                if (glossName) { const gid = resolveNodeId('glossary', glossName); if (gid) addEdge(pid, gid, 'references', `${pid}->${gid}`); }
            });
        }

        // Resolve system name -> id when raw has no ID (e.g. from UnisonSearch)
        let systemNameToId = new Map();
        if (API && typeof API.getSystemsList === 'function') {
            try {
                const list = await API.getSystemsList().catch(() => []);
                const arr = Array.isArray(list) ? list : [];
                arr.forEach(s => {
                    const name = s.name || s.Short_Name || s.Name;
                    const id = s.id != null ? s.id : s.ID;
                    if (name && id != null) systemNameToId.set(slugify(name), String(id));
                });
            } catch (e) { /* ignore */ }
        }

        // 2) For each system, fetch processes, projects, stakeholders – only when NO search results (noSearch), so filter counts match (e.g. 5 people = 5 on map)
        if (!noSearch) {
            // With search results: do not fetch extra stakeholders/processes/projects from API
        } else for (const systemNodeId of systemIds) {
            const node = nodesMap.get(systemNodeId);
            const raw = node?.meta?.raw;
            let systemId = raw && (raw.ID != null ? raw.ID : raw.id);
            if (systemId == null && node && systemNameToId.size) {
                const name = node.label || node.id.replace(/^system:/, '');
                systemId = systemNameToId.get(slugify(name));
            }
            if (!systemId) continue;

            const sysId = String(systemId);

            try {
                if (!noSearch && filters.has('process')) {
                    const r = await fetch(`/api/process-impact/systems/${sysId}/processes`, { credentials: 'include' }).catch(() => null);
                    const json = r && r.ok ? await r.json() : null;
                    const list = Array.isArray((json && json.data) || json) ? ((json && json.data) || json) : [];
                    list.forEach(p => {
                        const pid = p.id != null ? p.id : p.ID;
                        const name = p.name || p.Name || p.primaryName || `Process ${pid}`;
                        const nid = `process:${pid}`;
                        addNode(nid, name, 'process', { category: 'process', raw: p });
                        addEdge(systemNodeId, nid, 'impacts', `${systemNodeId}->process:${pid}`);
                    });
                }
                if (!noSearch && filters.has('project')) {
                    const r = await fetch(`/api/project-impact/systems/${sysId}/projects`, { credentials: 'include' }).catch(() => null);
                    if (!r || !r.ok) {
                        const alt = await fetch(`/api/system-impact/${sysId}/projects`, { credentials: 'include' }).catch(() => null);
                        if (alt && alt.ok) {
                            const json = await alt.json();
                            const list = Array.isArray((json && json.data) || json) ? ((json && json.data) || json) : [];
                            list.forEach(p => {
                                const pid = p.id != null ? p.id : p.ID;
                                const name = p.name || p.Name || p.projectName || `Project ${pid}`;
                                const nid = `project:${pid}`;
                                addNode(nid, name, 'project', { category: 'project', raw: p });
                                addEdge(systemNodeId, nid, 'impacts', `${systemNodeId}->project:${pid}`);
                            });
                        }
                    } else {
                        const json = await r.json();
                        const list = Array.isArray((json && json.data) || json) ? ((json && json.data) || json) : [];
                        list.forEach(p => {
                            const pid = p.id != null ? p.id : p.ID;
                            const name = p.name || p.Name || p.projectName || `Project ${pid}`;
                            const nid = `project:${pid}`;
                            addNode(nid, name, 'project', { category: 'project', raw: p });
                            addEdge(systemNodeId, nid, 'impacts', `${systemNodeId}->project:${pid}`);
                        });
                    }
                }
                if (filters.has('stakeholder')) {
                    let list = [];
                    if (API && typeof API.getSystemStakeholders === 'function') {
                        const r = await API.getSystemStakeholders(sysId).catch(() => null);
                        list = Array.isArray((r && r.data) || r) ? ((r && r.data) || r) : [];
                    }
                    if (!list.length) {
                        const r = await fetch(`/api/system-stakeholder/${sysId}/stakeholders`, { credentials: 'include' }).catch(() => null);
                        if (r && r.ok) {
                            const data = await r.json();
                            list = Array.isArray(data) ? data : (Array.isArray((data && data.data) || data) ? ((data && data.data) || data) : (data && data.stakeholders ? data.stakeholders : []));
                        }
                    }
                    list.forEach(s => {
                        const pid = s.PeopleID != null ? s.PeopleID : (s.personId != null ? s.personId : (s.id != null ? s.id : s.ID));
                        const sfn = s['First Name'] || s.first_name || s.First_Name || s.firstName || s.FirstName;
                        const sln = s['Last Name'] || s.last_name || s.Last_Name || s.lastName || s.LastName;
                        const name = (s.PersonName || s.personName || s.name || s.Name || s['Primary Name'] || (sfn && sln ? (sfn + ' ' + sln).trim() : (sfn || sln)) || '').trim() || `Stakeholder ${pid}`;
                        const nid = `stakeholder:${pid}`;
                        addNode(nid, name, 'people', { category: 'stakeholder', raw: s }, true);
                        addEdge(systemNodeId, nid, 'stakeholder', `${systemNodeId}->stakeholder:${pid}`);
                    });
                }
            } catch (e) {
                mapsDbgWarn('[MAPS] Multi-node fetch error for system', systemId, e);
            }
        }

        // 3) People who are not stakeholders of any system – only when NO search (with search we only show people from results)
        if (noSearch && filters.has('stakeholder')) {
            const linkedPersonIds = new Set();
            nodesMap.forEach((node) => {
                if (node.group === 'people' && node.id.startsWith('stakeholder:')) {
                    linkedPersonIds.add(String(node.id.replace(/^stakeholder:/, '')));
                }
            });
            try {
                const r = await fetch('/api/people', { credentials: 'include' }).catch(() => null);
                if (r && r.ok) {
                    const data = await r.json();
                    const list = Array.isArray((data && data.data) || data) ? ((data && data.data) || data) : (Array.isArray(data) ? data : []);
                    list.forEach((p) => {
                        const pid = p.id != null ? p.id : p.ID;
                        if (pid == null || linkedPersonIds.has(String(pid))) return;
                        const fn = p['First Name'] || p.first_name || p.First_Name || p.firstName || p.FirstName;
                        const ln = p['Last Name'] || p.last_name || p.Last_Name || p.lastName || p.LastName;
                        const name = (p.name || p.Name || p['Primary Name'] || (fn && ln ? (fn + ' ' + ln).trim() : (fn || ln)) || p.primaryName || p.PersonName || '').trim() || ('Person ' + pid);
                        const nid = `stakeholder:${pid}`;
                        if (nodesMap.has(nid)) return;
                        nodesMap.set(nid, {
                            id: nid,
                            label: name,
                            group: 'people',
                            level: 2,
                            inScope: true,
                            meta: { category: 'stakeholder', unlinkedPerson: true, raw: p }
                        });
                    });
                }
            } catch (e) {
                mapsDbgWarn('[MAPS] Could not fetch people for unlinked nodes', e);
            }
        }

        // 4) Cross-link: for each person on the map, fetch their responsibilities and create edges to all related objects on the map
        const personNodes = Array.from(nodesMap.values()).filter(n => n.group === 'people' && n.id.startsWith('stakeholder:'));
        const objectTypeToGroup = {
            'System': 'system', 'Data Set': 'dataset', 'Process': 'process',
            'Project': 'project', 'Policy': 'policy', 'Glossary': 'glossary',
            'Attribute': 'attribute', 'Capability': 'capability',
            'Business Area': 'businessArea', 'Client': 'client',
            'Committee': 'committee', 'Legal Entity': 'legalEntity',
            'Product': 'product', 'Regulation': 'regulation',
            'System Interface': 'interface'
        };
        // Batch: fetch responsibilities for all persons in parallel (limit concurrency)
        const batchSize = 5;
        for (let i = 0; i < personNodes.length; i += batchSize) {
            const batch = personNodes.slice(i, i + batchSize);
            const promises = batch.map(async (personNode) => {
                const personId = personNode.id.replace(/^stakeholder:/, '');
                if (!personId || personId === 'undefined') return;
                try {
                    const resp = await fetch(`/api/responsibilities/${encodeURIComponent(personId)}`, { credentials: 'include' }).catch(() => null);
                    if (!resp || !resp.ok) return;
                    const responsibilities = await resp.json();
                    if (!Array.isArray(responsibilities)) return;
                    responsibilities.forEach(r => {
                        const objType = r.object_type || r.objectType;
                        const objId = r.object_id || r.objectId;
                        const objName = r.object_name || r.objectName;
                        const roleName = r.role_name || r.roleName || 'stakeholder';
                        if (!objType || objId == null) return;
                        const group = objectTypeToGroup[objType];
                        if (!group) return;
                        // Find matching node on the map by group + id
                        const targetPrefix = group === 'interface' ? 'interface' : group;
                        const targetId = `${targetPrefix}:${objId}`;
                        const targetIdSlug = `${targetPrefix}:${slugify(objName || '')}`;
                        let targetNodeId = null;
                        if (nodesMap.has(targetId)) targetNodeId = targetId;
                        else if (nodesMap.has(targetIdSlug)) targetNodeId = targetIdSlug;
                        else {
                            // Try to find by label match
                            for (const [nid, nd] of nodesMap) {
                                if (nd.group === group && objName && slugify(nd.label || '') === slugify(objName)) {
                                    targetNodeId = nid;
                                    break;
                                }
                            }
                        }
                        if (!targetNodeId) {
                            // If no-search mode, add the system node so person connects to it
                            if (noSearch && group === 'system' && objName) {
                                const sid = `system:${slugify(objName)}`;
                                if (!nodesMap.has(sid)) {
                                    nodesMap.set(sid, {
                                        id: sid, label: objName, group: 'system', level: 1, inScope: true,
                                        meta: { category: 'system' }
                                    });
                                    systemIds.add(sid);
                                }
                                targetNodeId = sid;
                            } else {
                                return; // Node not on map, skip
                            }
                        }
                        const edgeKey = `${personNode.id}->${targetNodeId}:${roleName}`;
                        const reverseKey = `${targetNodeId}->${personNode.id}`;
                        if (!edgeMap.has(edgeKey) && !edgeMap.has(reverseKey) && !edgeMap.has(`${targetNodeId}->${personNode.id}:stakeholder`)) {
                            addEdge(targetNodeId, personNode.id, roleName, edgeKey);
                        }
                    });
                } catch (e) {
                    mapsDbgWarn('[MAPS] Cross-link fetch error for person', personId, e);
                }
            });
            await Promise.all(promises);
        }

        const inScopeNodeIds = new Set(Array.from(nodesMap.values()).filter(n => n.inScope).map(n => n.id));
        return {
            nodes: Array.from(nodesMap.values()),
            edges,
            inScopeNodeIds
        };
    }

    function renderNetwork(graph) {
        if (typeof cytoscape === 'undefined') {
            showPlaceholder('Visualization library is not available.');
            return;
        }

        // Filter out hidden nodes and their edges
        const hidden = MapState.hiddenNodes;
        const visibleNodes = (graph.nodes || []).filter(n => !hidden.has(n.id));
        const visibleNodeIds = new Set(visibleNodes.map(n => n.id));
        let visibleEdges = (graph.edges || []).filter(e => visibleNodeIds.has(e.from) && visibleNodeIds.has(e.to));
        // System lineage: filter by LINKS (System Interfaces / Data Attribute Links) like other maps
        if (MapState.mapType === 'system-lineage' && MapState.linkFilters) {
            const lf = MapState.linkFilters;
            visibleEdges = visibleEdges.filter(e => {
                const isDashed = e.dashes === true;
                if (isDashed) return lf.systemInterfaces !== false;
                return lf.dataAttributeLinks !== false;
            });
        }

        // Clear existing network
        if (MapState.network) {
            MapState.network.destroy();
            MapState.network = null;
        }
        MapState.focusedNodeId = null;

        // Prepare Cytoscape elements
        const elements = [];
        
        const rawId = (node) => (node.meta && node.meta.raw && (node.meta.raw.ID || node.meta.raw.id)) ? String(node.meta.raw.ID || node.meta.raw.id) : null;

        visibleNodes.forEach(node => {
            const isSystem = node.group === 'system';
            const isDataset = node.group === 'dataset';
            const inScope = MapState.inScopeNodeIds.has(node.id);
            const baseColor = MAP_GROUP_COLORS[node.group] || MAP_GROUP_COLORS.other;
            const otherColor = (MapState.mapType === 'multi-node-lineage' && MapState.inScopeNodeIds.size > 0) ? '#15803d' : '#6c757d';
            const nodeColor = inScope ? (baseColor.bg || '#ea580c') : otherColor;
            const borderColor = inScope ? (baseColor.border || '#64748b') : (otherColor === '#15803d' ? '#14532d' : '#4b5563');
            const id = rawId(node);
            const isLocked = id && (isSystem ? MapState.inaccessibleSystemIds.has(id) : (isDataset ? MapState.inaccessibleDatasetIds.has(id) : false));
            let displayLabel = (node.label && String(node.label).trim()) || node.id;
            // If the label is just the node ID (e.g. stakeholder:5, process:123), try to extract a real name from raw data
            if (displayLabel === node.id && node.meta && node.meta.raw) {
                const raw = node.meta.raw;
                if (node.group === 'people') {
                    const fn = raw['First Name'] || raw.first_name || raw.First_Name || raw.firstName || '';
                    const ln = raw['Last Name'] || raw.last_name || raw.Last_Name || raw.lastName || '';
                    const fullName = (fn && ln ? (fn + ' ' + ln).trim() : (fn || ln || '')).trim();
                    if (fullName) displayLabel = fullName;
                } else {
                    const rawName = raw.Name || raw['Primary Name'] || raw.name || raw['Short Name'] || raw.primaryName || '';
                    if (rawName) displayLabel = rawName;
                }
            }
            if (node.group === 'people' && displayLabel.startsWith('stakeholder:')) {
                displayLabel = 'Person ' + (displayLabel.replace(/^stakeholder:/, '') || '');
            }
            // Also clean up other prefix patterns that might slip through
            if (displayLabel.startsWith('process:') || displayLabel.startsWith('attribute:') || displayLabel.startsWith('project:') || displayLabel.startsWith('policy:') || displayLabel.startsWith('glossary:') || displayLabel.startsWith('dataset:')) {
                const cleanLabel = displayLabel.replace(/^[^:]+:/, '').replace(/-/g, ' ').trim();
                if (cleanLabel) displayLabel = cleanLabel;
            }
            displayLabel = (isLocked ? '\u{1F512} ' : '') + displayLabel;

            const isCurrent = inScope.size === 1 && inScope.has(node.id);
            const nodeData = {
                id: node.id,
                label: displayLabel,
                group: node.group,
                meta: node.meta,
                nodeColor: nodeColor,
                borderColor: borderColor,
                isLocked: !!isLocked,
                shape: getNodeShape(node.group),
                backgroundImage: createNodeIcon(node.group, nodeColor),
                unlinkedPerson: !!(node.meta && node.meta.unlinkedPerson),
                isCurrent: !!isCurrent
            };

            elements.push({
                data: nodeData
            });
        });

        // Current node IDs for layout: edges pointing TO a current node are reversed so dagre places current node leftmost (LR) / topmost (TB)
        const inScope = MapState.inScopeNodeIds;
        const currentNodeIds = new Set(visibleNodes.filter(n => inScope.has(n.id) && inScope.size === 1).map(n => n.id));

        // Add edges: direct = both endpoints in scope (blue); indirect = at least one out of scope (grey)
        visibleEdges.forEach(edge => {
            const showLabel = edge.dashes ? MapState.showInterfaceLabels : true;
            const direct = inScope.has(edge.from) && inScope.has(edge.to);
            const lineColor = direct ? '#ea580c' : '#6c757d';
            const rawLabel = edge.label || '';
            const hideStakeholderText = rawLabel.toLowerCase() === 'stakeholder';
            const displayLabel = showLabel && !hideStakeholderText ? rawLabel : '';
            const pointsToCurrent = currentNodeIds.has(edge.to);
            const source = pointsToCurrent ? edge.to : edge.from;
            const target = pointsToCurrent ? edge.from : edge.to;
            const edgeData = {
                id: edge.id,
                source: source,
                target: target,
                label: displayLabel,
                edgeLabel: hideStakeholderText ? '' : rawLabel,
                dashes: edge.dashes,
                lineStyle: edge.dashes === false ? 'solid' : 'dashed',
                lineColor: edge.color || lineColor,
                direct: direct,
                reversed: !!pointsToCurrent
            };
            const classes = pointsToCurrent ? 'reversed-edge' : '';
            elements.push(classes ? { data: edgeData, classes: classes } : { data: edgeData });
        });

        // Initialize Cytoscape
        MapState.network = cytoscape({
            container: MapState.canvas,
            elements: elements,
            style: getCytoscapeStyle()
        });

        const layoutOpt = buildCytoscapeLayout();
        const layout = MapState.network.layout(layoutOpt);
        layout.run();
        layout.on('layoutstop', function() {
            const cy = MapState.network;
            // Multi-node: place unlinked people in a row below; then spread linked people apart so they're visible
            if (MapState.mapType === 'multi-node-lineage') {
                const unlinked = cy.nodes().filter(n => n.data('unlinkedPerson'));
                if (unlinked.length > 0) {
                    const others = cy.nodes().filter(n => !n.data('unlinkedPerson'));
                    const pad = 80;
                    if (others.length > 0) {
                        const box = others.boundingBox();
                        const bottomY = box.y2 + pad;
                        const width = Math.max(box.w, unlinked.length * 90);
                        const startX = box.x1 + (box.w - width) / 2;
                        const step = unlinked.length > 1 ? width / (unlinked.length - 1) : 0;
                        unlinked.forEach((n, i) => {
                            n.position({ x: startX + (unlinked.length === 1 ? width / 2 : step * i), y: bottomY });
                        });
                    } else {
                        unlinked.forEach((n, i) => {
                            n.position({ x: 150 + i * 120, y: 150 });
                        });
                    }
                }
                spreadPersonNodesApart(cy, 200);
            }
            cy.fit(50);
        });

        // Event handlers: context menu on node tap (Go to Object / Hide object); background tap closes menu
        MapState.network.on('tap', 'node', function(evt) {
            const cyNode = evt.target;
            const nodeData = cyNode.data();
            const nodeId = cyNode.id();
            evt.preventDefault();
            // Show context menu on every node tap (including current object)
            showNodeContextMenu(evt, nodeData);
            // Axon: highlight edges into/out of selected node
            MapState.selectedNodeId = nodeId;
            MapState.network.edges().removeClass('flow-in flow-out');
            const connectedEdges = cyNode.connectedEdges();
            connectedEdges.forEach(function(edge) {
                if (edge.target().id() === nodeId) edge.addClass('flow-in');
                else edge.addClass('flow-out');
            });
        });

        MapState.network.on('tap', function(evt) {
            if (evt.target === MapState.network) {
                MapState.selectedNodeId = null;
                MapState.network.edges().removeClass('flow-in flow-out');
                hideContextMenu();
            }
        });

        // Store elements for filtering
        MapState.nodes = visibleNodes;
        MapState.edges = visibleEdges;
    }

    function escapeHtml(str) {
        if (str == null) return '';
        const div = document.createElement('div');
        div.textContent = String(str);
        return div.innerHTML;
    }

    function hideContextMenu() {
        const menu = document.getElementById('mapsContextMenu');
        if (menu) menu.remove();
    }

    function showNodeContextMenu(evt, nodeData) {
        hideContextMenu();
        const menu = document.createElement('div');
        menu.className = 'map-context-menu';
        menu.id = 'mapsContextMenu';
        const nodeId = nodeData.id;
        const group = nodeData.group;
        const raw = nodeData.meta && nodeData.meta.raw;
        const systemId = raw && (raw.ID != null ? raw.ID : raw.id);
        const isSystem = group === 'system';

        let items = '<button type="button" class="map-context-menu-item" data-action="go">' +
            '<i class="fas fa-external-link-alt"></i> Go to Object</button>' +
            '<button type="button" class="map-context-menu-item" data-action="hide">' +
            '<i class="fas fa-eye-slash"></i> Hide object</button>';
        if (isSystem && systemId != null) {
            items += '<button type="button" class="map-context-menu-item" data-action="show-stakeholders">' +
                '<i class="fas fa-users"></i> Show Stakeholders</button>' +
                '<button type="button" class="map-context-menu-item" data-action="show-processes">' +
                '<i class="fas fa-project-diagram"></i> Show Processes</button>' +
                '<button type="button" class="map-context-menu-item" data-action="show-projects">' +
                '<i class="fas fa-folder-open"></i> Show Projects</button>';
        }
        items += '<button type="button" class="map-context-menu-item" data-action="focus">' +
            '<i class="fas fa-crosshairs"></i> ' + (MapState.focusedNodeId === nodeId ? 'Undo focus' : 'Focus object') + '</button>';
        menu.innerHTML = items;

        const canvas = MapState.canvas;
        if (!canvas || !MapState.network) return;
        document.body.appendChild(menu);
        const node = MapState.network.getElementById(nodeId);
        if (node.length) {
            const pos = node.renderedPosition();
            const containerRect = canvas.getBoundingClientRect();
            const pan = MapState.network.pan();
            const zoom = MapState.network.zoom();
            const left = containerRect.left + pos.x * zoom + pan.x;
            const top = containerRect.top + pos.y * zoom + pan.y + 50;
            menu.style.position = 'fixed';
            menu.style.left = Math.max(8, left) + 'px';
            menu.style.top = Math.max(8, top) + 'px';
            menu.style.zIndex = '10000';
        }
        menu.querySelectorAll('.map-context-menu-item').forEach(btn => {
            btn.addEventListener('click', function(e) {
                e.stopPropagation();
                const action = this.getAttribute('data-action');
                if (action === 'go') goToObject(nodeData);
                else if (action === 'hide') hideNode(nodeId);
                else if (action === 'show-stakeholders' && systemId != null) goToSystemTab(systemId, 'stakeholders');
                else if (action === 'show-processes' && systemId != null) goToSystemTab(systemId, 'impact');
                else if (action === 'show-projects' && systemId != null) goToSystemTab(systemId, 'impact');
                else if (action === 'focus') focusMapNode(nodeId);
                hideContextMenu();
            });
        });
        setTimeout(() => {
            document.addEventListener('click', function closeMenu() {
                document.removeEventListener('click', closeMenu);
                hideContextMenu();
            }, { once: true });
        }, 0);
    }

    function goToObject(nodeData) {
        const raw = nodeData.meta && nodeData.meta.raw;
        const id = raw && (raw.ID != null ? raw.ID : raw.id);
        const group = nodeData.group;
        if (!id && !group) return;
        const typeMap = {
            system: '/view/system/',
            dataset: '/view/dataset/',
            process: '/view/process/',
            project: '/view/project/',
            product: '/view/product/',
            policy: '/view/policy/',
            attribute: '/view/dataset/',  // attribute detail typically under dataset
            'business-area': '/view/business-area/',
            client: '/view/client/',
            capability: '/view/capability/',
            glossary: '/view/glossary/',
            people: '/view/people/',
            stakeholder: '/view/people/'
        };
        const base = typeMap[group] || (group === 'people' ? '/view/people/' : null);
        if (base) window.open(base + encodeURIComponent(String(id)), '_blank');
    }

    function hideNode(nodeId) {
        MapState.hiddenNodes.add(nodeId);
        loadAndRenderMap(false);
    }

    /** Navigate to system view/edit page with a specific tab (stakeholders, impact, etc.). */
    function goToSystemTab(systemId, tab) {
        const id = String(systemId);
        if (tab === 'stakeholders') {
            window.location.href = '/view/system/system-edit.html?id=' + encodeURIComponent(id) + '&tab=stakeholders';
        } else {
            window.location.href = '/view/system/system-edit.html?id=' + encodeURIComponent(id) + '&tab=' + encodeURIComponent(tab);
        }
    }

    /** Focus map on a node (center + zoom) or undo focus to show all. */
    function focusMapNode(nodeId) {
        const cy = MapState.network;
        if (!cy) return;
        const cyNode = cy.getElementById(nodeId);
        if (!cyNode.length) return;

        if (MapState.focusedNodeId === nodeId) {
            MapState.focusedNodeId = null;
            cy.elements().removeClass('dimmed');
            cy.fit(50);
            return;
        }
        MapState.focusedNodeId = nodeId;
        cy.elements().removeClass('dimmed');
        cyNode.connectedEdges().addClass('flow-in flow-out');
        const connected = cyNode.connectedEdges().connectedNodes();
        cy.nodes().not(cyNode).not(connected).addClass('dimmed');
        cy.edges().not(cyNode.connectedEdges()).addClass('dimmed');
        cy.animate({
            center: { eles: cyNode },
            zoom: 1.4,
            duration: 300
        }, { queue: false });
    }

    function getCytoscapeStyle() {
        return [
            {
                selector: 'node',
                style: {
                    'label': 'data(label)',
                    'text-valign': 'bottom',
                    'text-halign': 'center',
                    'text-opacity': 1,
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
                    'shape': 'data(shape)',
                    'width': 140,
                    'height': 80,
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
                    'line-color': 'data(lineColor)',
                    'target-arrow-color': 'data(lineColor)',
                    'target-arrow-shape': 'triangle',
                    'target-arrow-width': 6,
                    'target-arrow-height': 6,
                    'curve-style': getCurveStyle(),
                    'line-style': 'data(lineStyle)',
                    'line-dash-pattern': [5, 5],
                    'label': 'data(label)',
                    'font-size': 10,
                    'color': '#6b7280',
                    'text-background-color': '#ffffff',
                    'text-background-opacity': 0.9,
                    'text-margin-y': -5
                }
            },
            {
                selector: 'edge[direct = true]',
                style: { 'width': 2.5 }
            },
            {
                selector: 'edge[direct = false]',
                style: { 'width': 1.5 }
            },
            {
                selector: 'edge.reversed-edge',
                style: {
                    'source-arrow-shape': 'triangle',
                    'source-arrow-color': '#64748b',
                    'source-arrow-width': 6,
                    'source-arrow-height': 6,
                    'target-arrow-shape': 'none'
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
                    'line-style': 'solid',
                    'line-dash-pattern': []
                }
            },
            {
                selector: 'edge.flow-in',
                style: {
                    'line-color': '#c2410c',
                    'target-arrow-color': '#c2410c',
                    'width': 3
                }
            },
            {
                selector: 'edge.flow-out',
                style: {
                    'line-color': '#f97316',
                    'target-arrow-color': '#f97316',
                    'width': 3
                }
            },
            {
                selector: 'node:selected',
                style: {
                    'border-width': 4,
                    'border-color': '#ea580c',
                    'background-opacity': 1
                }
            },
            {
                selector: 'node.dimmed',
                style: { 'opacity': 0.3, 'text-opacity': 0.4 }
            },
            {
                selector: 'edge.dimmed',
                style: { 'opacity': 0.25 }
            },
            {
                selector: 'node.filter-hidden',
                style: { 'opacity': 0, 'events': 'no', 'text-opacity': 0 }
            },
            {
                selector: 'edge.filter-hidden',
                style: { 'opacity': 0, 'events': 'no' }
            }
        ];
    }

    /** Root nodes for tree layouts: nodes with no incoming edges (or first node if none). */
    function findRootNodes() {
        if (!MapState.network) return [];
        const nodes = MapState.network.nodes();
        const targets = new Set();
        MapState.network.edges().forEach(edge => targets.add(edge.target().id()));
        const roots = nodes.filter(node => !targets.has(node.id()));
        return roots.length > 0 ? roots : (nodes.length ? [nodes[0]] : []);
    }

    function buildCytoscapeLayout() {
        // Axon Table 1: Multi-Node Lineage uses Organic layout only (general connections, not directional flow)
        const layoutOption = MapState.mapType === 'multi-node-lineage' ? 'force' : MapState.layout;

        // Organic / force-directed — spacing affects nodeRepulsion, idealEdgeLength, and padding
        if (layoutOption === 'force' || layoutOption === 'organic') {
            const spPad = getSpacingPadding();
            const spMult = MapState.spacing === 'compact' ? 0.6 : (MapState.spacing === 'spacey' ? 1.8 : 1.0);
            if (MapState.mapType === 'multi-node-lineage') {
                return {
                    name: 'cose',
                    animate: true,
                    animationDuration: 500,
                    nodeRepulsion: Math.round(4500 * spMult),
                    idealEdgeLength: Math.round(100 * spMult),
                    edgeElasticity: 0.4,
                    nestingFactor: 0.15,
                    gravity: 0.2,
                    numIter: 2500,
                    padding: spPad,
                    initialEnergyOnIncremental: 0.3
                };
            }
            return {
                name: 'cose',
                animate: true,
                animationDuration: 500,
                nodeRepulsion: Math.round(5000 * spMult),
                idealEdgeLength: Math.round(120 * spMult),
                edgeElasticity: 0.45,
                nestingFactor: 0.1,
                gravity: 0.25,
                numIter: 2500,
                padding: spPad,
                initialEnergyOnIncremental: 0.3
            };
        }

        // Directional layouts: use dagre when available (rankDir), else breadthfirst with real roots
        const sf = getSpacingFactor();
        const sp = getSpacingPadding();
        const dagreOpts = {
            padding: sp,
            spacingFactor: sf,
            animate: true,
            animationDuration: 500,
            nodeDimensionsIncludeLabels: true
        };
        const breadthfirstBase = {
            name: 'breadthfirst',
            directed: true,
            padding: sp,
            spacingFactor: sf,
            animate: true,
            animationDuration: 500,
            nodeDimensionsIncludeLabels: true,
            roots: findRootNodes()
        };

        switch (layoutOption) {
            case 'right-to-left':
                if (window._mapsDagreRegistered) {
                    return { name: 'dagre', rankDir: 'RL', ...dagreOpts };
                }
                return { ...breadthfirstBase, transform: (node, pos) => ({ x: -pos.x, y: pos.y }) };
            case 'top-to-bottom':
                if (window._mapsDagreRegistered) {
                    return { name: 'dagre', rankDir: 'TB', ...dagreOpts };
                }
                return breadthfirstBase;
            case 'left-to-right':
            default:
                if (window._mapsDagreRegistered) {
                    return { name: 'dagre', rankDir: 'LR', ...dagreOpts };
                }
                return breadthfirstBase;
        }
    }

    function updateNetworkLayout() {
        syncMapStateFromSharedDropdowns();
        if (!MapState.network) return;
        let layoutOpt = buildCytoscapeLayout();
        let layout;
        try {
            layout = MapState.network.layout(layoutOpt);
            layout.run();
        } catch (err) {
            // Fallback when dagre is not registered: use breadthfirst with real roots
            if (layoutOpt.name === 'dagre') {
                const rankDir = layoutOpt.rankDir;
                const breadthfirstBase = {
                    name: 'breadthfirst',
                    directed: true,
                    padding: layoutOpt.padding || 90,
                    spacingFactor: layoutOpt.spacingFactor || 2.2,
                    animate: layoutOpt.animate !== false,
                    animationDuration: layoutOpt.animationDuration || 500,
                    nodeDimensionsIncludeLabels: true,
                    roots: findRootNodes()
                };
                if (rankDir === 'RL') {
                    layoutOpt = { ...breadthfirstBase, transform: (node, pos) => ({ x: -pos.x, y: pos.y }) };
                } else {
                    layoutOpt = breadthfirstBase;
                }
                layout = MapState.network.layout(layoutOpt);
                layout.run();
            } else {
                throw err;
            }
        }
        layout.on('layoutstop', function() {
            const cy = MapState.network;
            if (MapState.mapType === 'multi-node-lineage') {
                const unlinked = cy.nodes().filter(n => n.data('unlinkedPerson'));
                if (unlinked.length > 0) {
                    const others = cy.nodes().filter(n => !n.data('unlinkedPerson'));
                    const pad = 80;
                    if (others.length > 0) {
                        const box = others.boundingBox();
                        const bottomY = box.y2 + pad;
                        const width = Math.max(box.w, unlinked.length * 90);
                        const startX = box.x1 + (box.w - width) / 2;
                        const step = unlinked.length > 1 ? width / (unlinked.length - 1) : 0;
                        unlinked.forEach((n, i) => {
                            n.position({ x: startX + (unlinked.length === 1 ? width / 2 : step * i), y: bottomY });
                        });
                    } else {
                        unlinked.forEach((n, i) => {
                            n.position({ x: 150 + i * 120, y: 150 });
                        });
                    }
                }
            }
            cy.fit(50);
        });
    }

    function applyOverlay() {
        if (!MapState.network || !MapState.canvas) return;
        clearOverlayPanels();
        if (MapState.overlay === 'none') return;

        const overlayType = MapState.overlay;
        const overlayData = new Map();

        MapState.network.nodes().forEach(node => {
            const nodeId = node.id();
            const nodeData = node.data();
            const meta = nodeData.meta || {};
            const raw = meta.raw || {};
            const id = raw.ID || raw.id;
            const group = nodeData.group || 'system';
            const systemId = group === 'system' ? id : (raw['System ID'] != null ? raw['System ID'] : raw.systemId);
            const datasetId = group === 'dataset' ? id : null;

            if (!systemId && !datasetId) return;

            overlayData.set(nodeId, { systemId, datasetId, group, nodeData, overlayType });
        });

        loadOverlayDataForNodes(overlayType, overlayData);
    }

    function clearOverlayPanels() {
        if (!MapState.canvas) return;
        const container = MapState.canvas.querySelector('.map-overlay-container');
        if (container) container.remove();
        if (MapState.network) {
            try { MapState.network.off('zoom pan', updateOverlayPositions); } catch (e) { /* no-op */ }
            try { MapState.network.off('drag', 'node', updateOverlayPositions); } catch (e) { /* no-op */ }
        }
    }

    async function loadOverlayDataForNodes(overlayType, overlayData) {
        if (!MapState.network || !MapState.canvas) return;

        const results = new Map();
        const API = window.BUDG_API_SERVICE || window.apiService;

        function markInaccessibleSystem(id) {
            if (id != null) MapState.inaccessibleSystemIds.add(String(id));
        }
        function markInaccessibleDataset(id) {
            if (id != null) MapState.inaccessibleDatasetIds.add(String(id));
        }

        for (const [nodeId, info] of overlayData) {
            const { systemId, datasetId, group } = info;
            let items = [];
            try {
                if (group === 'system' && systemId) {
                    if (overlayType === 'description' && API && typeof API.getSystemById === 'function') {
                        const s = await API.getSystemById(systemId).catch(err => { if (err && err.status === 403) markInaccessibleSystem(systemId); return null; });
                        const d = (s && (s.data || s)) || {};
                        if (d.description) items = [{ value: d.description }];
                    } else if (overlayType === 'stakeholders' && API && typeof API.getSystemStakeholders === 'function') {
                        const r = await API.getSystemStakeholders(systemId).catch(err => { if (err && err.status === 403) markInaccessibleSystem(systemId); return null; });
                        items = Array.isArray((r && r.data) || r) ? ((r && r.data) || r) : [];
                    } else if (overlayType === 'processes') {
                        const r = await fetch(`/api/process-impact/systems/${systemId}/processes`, { credentials: 'include' }).catch(() => null);
                        if (r && r.status === 403) markInaccessibleSystem(systemId);
                        const json = r && r.ok ? await r.json() : null;
                        items = Array.isArray((json && json.data) || json) ? ((json && json.data) || json) : [];
                    } else if (overlayType === 'projects') {
                        const r = await fetch(`/api/project-impact/systems/${systemId}/projects`, { credentials: 'include' }).catch(() => null);
                        if (r && r.status === 403) markInaccessibleSystem(systemId);
                        const json = r && r.ok ? await r.json() : null;
                        items = Array.isArray((json && json.data) || json) ? ((json && json.data) || json) : [];
                    } else if (overlayType === 'policies') {
                        const r = await fetch(`/api/policy-impact/systems/${systemId}/policies`, { credentials: 'include' }).catch(() => null);
                        if (r && r.status === 403) markInaccessibleSystem(systemId);
                        const json = r && r.ok ? await r.json() : null;
                        items = Array.isArray((json && json.data) || json) ? ((json && json.data) || json) : [];
                    } else if (overlayType === 'glossary') {
                        var seenSysG = new Set();
                        // 1) System-level glossary
                        if (API && typeof API.getSystemById === 'function') {
                            var s = await API.getSystemById(systemId).catch(err => { if (err && err.status === 403) markInaccessibleSystem(systemId); return null; });
                            var d = (s && (s.data || s)) || {};
                            var sysGName = d.primaryGlossaryName || d.glossaryName;
                            if (sysGName && !seenSysG.has(sysGName)) { seenSysG.add(sysGName); items.push({ name: sysGName, source: 'system' }); }
                        }
                        // 2) Dataset-level and attribute-level glossary
                        var sysDsList = [];
                        if (API && typeof API.getSystemDatasets === 'function') {
                            var dsRes = await API.getSystemDatasets(systemId).catch(() => null);
                            var dsArr = Array.isArray(dsRes?.data) ? dsRes.data : (Array.isArray(dsRes) ? dsRes : []);
                            sysDsList = dsArr;
                        }
                        for (var sd of sysDsList) {
                            var sdId = sd.id ?? sd.datasetId ?? sd.dataset_id;
                            var sdGName = sd.glossaryName;
                            if (sdGName && !seenSysG.has(sdGName)) { seenSysG.add(sdGName); items.push({ name: sdGName, source: 'dataset' }); }
                            if (sdId) {
                                try {
                                    var attrR = await fetch('/api/attribute/' + sdId, { credentials: 'include' }).catch(function() { return null; });
                                    var attrJ = attrR && attrR.ok ? await attrR.json() : null;
                                    var attrArr = Array.isArray(attrJ?.data) ? attrJ.data : (Array.isArray(attrJ) ? attrJ : []);
                                    attrArr.forEach(function(a) {
                                        var gn = a['Glossary Name attribute'] || a.glossaryName || a.GlossaryName;
                                        if (gn && !seenSysG.has(gn)) { seenSysG.add(gn); items.push({ name: gn, source: 'attribute' }); }
                                    });
                                } catch (e) { /* skip */ }
                            }
                        }
                    } else if (overlayType === 'attributes' || overlayType === 'linking-attributes') {
                        // Attributes overlay: all attributes for system's datasets. Linking: only attributes that participate in a relationship.
                        let sysDatasets = [];
                        if (API && typeof API.getSystemDatasets === 'function') {
                            const res = await API.getSystemDatasets(systemId).catch(err => { if (err && err.status === 403) markInaccessibleSystem(systemId); return null; });
                            const data = res?.data ?? res;
                            sysDatasets = Array.isArray(data) ? data : (Array.isArray(data?.data) ? data.data : []);
                        } else {
                            const r = await fetch(`/api/system-data/${systemId}/datasets`, { credentials: 'include' }).catch(() => null);
                            if (r && r.status === 403) markInaccessibleSystem(systemId);
                            const json = r && r.ok ? await r.json() : null;
                            sysDatasets = Array.isArray(json?.data) ? json.data : (Array.isArray(json) ? json : []);
                        }
                        const linkingOnly = overlayType === 'linking-attributes';
                        for (const sd of sysDatasets) {
                            const did = sd.id ?? sd.datasetId ?? sd.dataset_id;
                            if (!did) continue;
                            const attrUrl = linkingOnly ? `/api/attribute/${did}?linking=true` : `/api/attribute/${did}`;
                            const attrRes = await fetch(attrUrl, { credentials: 'include' }).catch(() => null);
                            const attrJson = attrRes && attrRes.ok ? await attrRes.json() : null;
                            const arr = Array.isArray(attrJson?.data) ? attrJson.data : (Array.isArray(attrJson) ? attrJson : []);
                            arr.forEach(a => items.push({ id: a.id ?? a.ID, name: a['Name attribute'] || a.name || a.primaryName || '', refNumber: a.refNumber || a.ref || '' }));
                        }
                    }
                } else if (group === 'dataset' && datasetId) {
                    if (overlayType === 'description' && API && typeof API.getDatasetById === 'function') {
                        const s = await API.getDatasetById(datasetId, null, { silent404: true }).catch(err => { if (err && err.status === 403) markInaccessibleDataset(datasetId); return null; });
                        const d = (s && (s.data || s)) || {};
                        const defText = d.definition || d.Definition || d.description || d.Description;
                        if (defText) items = [{ value: defText }];
                    } else if (overlayType === 'glossary') {
                        var seenDsG = new Set();
                        // 1) Dataset's own glossary
                        if (API && typeof API.getDatasetById === 'function') {
                            var dsInfo = await API.getDatasetById(datasetId, null, { silent404: true }).catch(err => { if (err && err.status === 403) markInaccessibleDataset(datasetId); return null; });
                            var dsD = (dsInfo && (dsInfo.data || dsInfo)) || {};
                            var dsGN = dsD.primaryGlossaryName || dsD.glossaryName;
                            if (dsGN && !seenDsG.has(dsGN)) { seenDsG.add(dsGN); items.push({ name: dsGN, source: 'dataset' }); }
                        }
                        // 2) Attribute-level glossary
                        try {
                            var attrResp = await fetch('/api/attribute/' + datasetId, { credentials: 'include' }).catch(function() { return null; });
                            if (attrResp && attrResp.status === 403) markInaccessibleDataset(datasetId);
                            var attrJson2 = attrResp && attrResp.ok ? await attrResp.json() : null;
                            var attrList = Array.isArray(attrJson2?.data) ? attrJson2.data : (Array.isArray(attrJson2) ? attrJson2 : []);
                            attrList.forEach(function(a) {
                                var gn = a['Glossary Name attribute'] || a.glossaryName || a.GlossaryName;
                                if (gn && !seenDsG.has(gn)) { seenDsG.add(gn); items.push({ name: gn, source: 'attribute' }); }
                            });
                        } catch (e) { /* skip */ }
                    } else if (overlayType === 'attributes' || overlayType === 'linking-attributes') {
                        // Attributes: all dataset attributes. Linking: only attributes that participate in a relationship.
                        const linkingOnly = overlayType === 'linking-attributes';
                        const url = linkingOnly ? `/api/attribute/${datasetId}?linking=true` : `/api/attribute/${datasetId}`;
                        const r = await fetch(url, { credentials: 'include' }).catch(() => null);
                        if (r && r.status === 403) markInaccessibleDataset(datasetId);
                        const json = r && r.ok ? await r.json() : null;
                        const arr = Array.isArray(json?.data) ? json.data : (Array.isArray(json) ? json : []);
                        arr.forEach(a => items.push({ id: a.id ?? a.ID, name: a['Name attribute'] || a.name || a.primaryName || '', refNumber: a.refNumber || a.ref || '' }));
                    }
                }
            } catch (e) { /* ignore */ }
            results.set(nodeId, items);
        }

        updateInaccessibleNodeLabels();
        renderOverlayPanels(overlayType, results);
    }

    function updateInaccessibleNodeLabels() {
        if (!MapState.network) return;
        const rawId = (node) => (node.data('meta') && node.data('meta').raw && (node.data('meta').raw.ID || node.data('meta').raw.id)) ? String(node.data('meta').raw.ID || node.data('meta').raw.id) : null;
        MapState.network.nodes().forEach(cyNode => {
            const group = cyNode.data('group');
            const id = rawId(cyNode);
            const isSystem = group === 'system';
            const isDataset = group === 'dataset';
            const isLocked = id && (isSystem ? MapState.inaccessibleSystemIds.has(id) : (isDataset ? MapState.inaccessibleDatasetIds.has(id) : false));
            let label = cyNode.data('label') || cyNode.id();
            if (isLocked && label.indexOf('\u{1F512}') !== 0) label = '\u{1F512} ' + label;
            if (!isLocked && label.indexOf('\u{1F512} ') === 0) label = label.replace(/^\u{1F512} /, '');
            cyNode.data('label', label);
        });
    }

    function renderOverlayPanels(overlayType, overlayData) {
        if (!MapState.network || !MapState.canvas) return;

        let container = MapState.canvas.querySelector('.map-overlay-container');
        if (!container) {
            container = document.createElement('div');
            container.className = 'map-overlay-container';
            container.style.cssText = 'position:absolute;top:0;left:0;pointer-events:none;z-index:50;';
            MapState.canvas.appendChild(container);
        }
        container.innerHTML = '';
        MapState.network.nodes().forEach(node => {
            const nodeId = node.id();
            const items = overlayData.get(nodeId) || [];
            if (!items || !items.length) return;
            const panelTitle = getOverlayTitle(overlayType, nodeId, node);
            const panel = document.createElement('div');
            panel.className = 'map-node-overlay-panel';
            panel.setAttribute('data-node-id', nodeId);
            panel.style.pointerEvents = 'auto';

            const header = document.createElement('div');
            header.className = 'map-node-overlay-header';
            header.innerHTML = '<span>' + escapeOverlayHtml(panelTitle) + '</span>';
            const gearBtn = document.createElement('button');
            gearBtn.type = 'button';
            gearBtn.innerHTML = '<i class="fas fa-cog"></i>';
            gearBtn.style.cssText = 'border:none;background:transparent;cursor:pointer;padding:2px 4px;font-size:12px;color:var(--text-secondary,#6b7280);';
            gearBtn.title = 'Filter & display settings';
            header.appendChild(gearBtn);
            panel.appendChild(header);

            var filterBar = document.createElement('div');
            filterBar.className = 'map-overlay-settings-bar';
            filterBar.style.display = 'none';
            var filterRow = document.createElement('div');
            filterRow.style.cssText = 'position:relative;margin-bottom:6px;';
            var sIcon = document.createElement('i');
            sIcon.className = 'fas fa-search';
            sIcon.style.cssText = 'position:absolute;left:8px;top:50%;transform:translateY(-50%);font-size:10px;color:var(--text-secondary,#9ca3af);pointer-events:none;';
            filterRow.appendChild(sIcon);
            var searchInput = document.createElement('input');
            searchInput.type = 'text';
            searchInput.placeholder = 'Filter entries...';
            searchInput.style.cssText = 'width:100%;box-sizing:border-box;padding:4px 8px 4px 26px;border:1px solid var(--border-color,#d1d5db);border-radius:4px;font-size:11px;outline:none;';
            filterRow.appendChild(searchInput);
            filterBar.appendChild(filterRow);
            var showRow = document.createElement('div');
            showRow.style.cssText = 'display:flex;align-items:center;justify-content:space-between;font-size:11px;color:var(--text-secondary,#4b5563);';
            var totalSpan = document.createElement('span');
            showRow.appendChild(totalSpan);
            var showWrap = document.createElement('span');
            showWrap.style.cssText = 'display:flex;align-items:center;gap:4px;';
            showWrap.innerHTML = '<span>Show</span>';
            var showSelect = document.createElement('select');
            showSelect.style.cssText = 'border:1px solid var(--border-color,#d1d5db);border-radius:4px;font-size:11px;padding:2px 4px;outline:none;cursor:pointer;';
            [{ v: '0', t: 'All' }, { v: '5', t: '5' }, { v: '10', t: '10' }, { v: '20', t: '20' }].forEach(function(o) { var opt = document.createElement('option'); opt.value = o.v; opt.textContent = o.t; showSelect.appendChild(opt); });
            showWrap.appendChild(showSelect);
            showRow.appendChild(showWrap);
            filterBar.appendChild(showRow);
            panel.appendChild(filterBar);

            gearBtn.addEventListener('click', function(e) { e.stopPropagation(); var isOpen = filterBar.style.display !== 'none'; filterBar.style.display = isOpen ? 'none' : ''; if (!isOpen) searchInput.focus(); });

            var body = document.createElement('div');
            body.className = 'map-node-overlay-body';
            var currentFilter = '';
            var currentMax = 0;
            var updateDisplay = function() {
                var filtered = items;
                if (currentFilter) {
                    var q = currentFilter.toLowerCase();
                    filtered = items.filter(function(item) { var text = (item.value || item.name || item.description || '').toLowerCase(); return text.indexOf(q) !== -1; });
                }
                if (currentMax > 0) filtered = filtered.slice(0, currentMax);
                totalSpan.textContent = 'Total entries: ' + filtered.length;
                body.innerHTML = formatOverlayHtml(overlayType, filtered);
            };
            searchInput.addEventListener('click', function(e) { e.stopPropagation(); });
            searchInput.addEventListener('mousedown', function(e) { e.stopPropagation(); });
            searchInput.addEventListener('input', function(e) { e.stopPropagation(); currentFilter = searchInput.value; updateDisplay(); });
            showSelect.addEventListener('click', function(e) { e.stopPropagation(); });
            showSelect.addEventListener('mousedown', function(e) { e.stopPropagation(); });
            showSelect.addEventListener('change', function(e) { e.stopPropagation(); currentMax = parseInt(showSelect.value, 10) || 0; updateDisplay(); });
            updateDisplay();

            panel.appendChild(body);
            container.appendChild(panel);
            positionOverlayPanel(panel, node);
        });

        MapState.network.on('zoom pan', updateOverlayPositions);
        MapState.network.on('drag', 'node', updateOverlayPositions);
        requestAnimationFrame(() => requestAnimationFrame(updateOverlayPositions));
    }

    function getOverlayTitle(overlayType, nodeId, node) {
        const group = node && typeof node.data === 'function' ? node.data('group') : null;
        if (overlayType === 'description' && (group === 'dataset' || (nodeId != null && String(nodeId).indexOf('dataset-') === 0))) {
            return 'Definition';
        }
        const titles = { description: 'Description', glossary: 'Glossary', attributes: 'Attributes', 'linking-attributes': 'Linking Attributes', 'data-quality': 'Data Quality', stakeholders: 'Stakeholders', processes: 'Processes', projects: 'Projects', policies: 'Policies' };
        return titles[overlayType] || overlayType || 'Overlay';
    }

    function escapeOverlayHtml(str) {
        if (str == null) return '';
        const div = document.createElement('div');
        div.textContent = String(str);
        return div.innerHTML;
    }

    function formatOverlayHtml(overlayType, items) {
        if (!items || !items.length) return '<p class="map-overlay-empty">No data.</p>';
        const list = items.slice(0, 20).map(item => {
            const text = item.value || item.name || item.description || '';
            return '<div class="map-node-overlay-item">' + escapeOverlayHtml(text || '-') + '</div>';
        });
        const more = items.length > 20 ? '<div class="map-overlay-more">+' + (items.length - 20) + ' more</div>' : '';
        return list.join('') + more;
    }

    function positionOverlayPanel(panel, node) {
        if (!MapState.canvas || !MapState.network || !panel || !node) return;
        const container = MapState.canvas.querySelector('.map-overlay-container');
        if (!container) return;
        const pos = node.renderedPosition();
        const bb = node.boundingBox();
        const zoom = MapState.network.zoom();
        const nodeH = (bb.h || 60) * zoom;
        const panelW = panel.offsetWidth || 220;
        const panelH = panel.offsetHeight || 120;
        const margin = 12;
        let x = pos.x - panelW / 2;
        let y = pos.y - nodeH / 2 - panelH - margin;
        const cw = container.clientWidth || 800;
        const ch = container.clientHeight || 560;
        if (x < margin) x = margin;
        if (x + panelW > cw - margin) x = cw - panelW - margin;
        if (y < margin) {
            y = pos.y + nodeH / 2 + margin;
            if (y + panelH > ch - margin) y = ch - panelH - margin;
        } else if (y + panelH > ch - margin) {
            y = ch - panelH - margin;
        }
        panel.style.left = x + 'px';
        panel.style.top = y + 'px';
    }

    function updateOverlayPositions() {
        if (!MapState.network || !MapState.canvas) return;
        const container = MapState.canvas.querySelector('.map-overlay-container');
        if (!container) return;
        container.querySelectorAll('.map-node-overlay-panel').forEach(panel => {
            const nodeId = panel.getAttribute('data-node-id');
            const node = nodeId ? MapState.network.getElementById(nodeId) : null;
            if (node && node.length) {
                const isVisible = node.style('display') !== 'none' && !node.hasClass('filter-hidden');
                panel.style.display = isVisible ? '' : 'none';
                if (isVisible) positionOverlayPanel(panel, node[0]);
            } else {
                panel.style.display = 'none';
            }
        });
    }

    function destroyNetwork() {
        if (MapState.network) {
            MapState.network.destroy();
            MapState.network = null;
        }
        MapState.nodes = null;
        MapState.edges = null;
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
        if (MapState.canvas) {
            MapState.canvas.innerHTML = `<div class="map-empty-state" style="min-height: 400px; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 2rem; text-align: center; color: var(--text-secondary);"><i class="fas fa-map" style="font-size: 2.5rem; color: var(--text-muted); margin-bottom: 0.75rem;"></i><p style="margin: 0; font-size: 1rem;">${message}</p></div>`;
        }
    }

    const ICON_WHITE = '#ffffff';
    function createNodeIcon(group, color) {
        const c = ICON_WHITE;
        const o = '0.92';
        let svg = '';
        switch (group) {
            case 'system':
                svg = `<svg width="40" height="40" viewBox="0 0 40 40" xmlns="http://www.w3.org/2000/svg"><rect x="6" y="4" width="28" height="5" rx="1.5" fill="${c}" opacity="${o}"/><rect x="6" y="13" width="28" height="5" rx="1.5" fill="${c}" opacity="${o}"/><rect x="6" y="22" width="28" height="5" rx="1.5" fill="${c}" opacity="${o}"/><rect x="6" y="31" width="28" height="5" rx="1.5" fill="${c}" opacity="${o}"/></svg>`;
                break;
            case 'dataset':
                svg = `<svg width="40" height="40" viewBox="0 0 40 40" xmlns="http://www.w3.org/2000/svg"><ellipse cx="20" cy="12" rx="10" ry="6" fill="${c}" opacity="${o}"/><path d="M10 12 v16 q0 6 10 6 q10 0 10 -6 v-16" fill="${c}" opacity="${o}"/><ellipse cx="20" cy="28" rx="10" ry="6" fill="${c}" opacity="${o}"/></svg>`;
                break;
            case 'process':
                svg = `<svg width="40" height="40" viewBox="0 0 40 40" xmlns="http://www.w3.org/2000/svg"><path d="M14 8 L26 20 L14 32 Z" fill="${c}" opacity="${o}"/></svg>`;
                break;
            case 'project':
                svg = `<svg width="40" height="40" viewBox="0 0 40 40" xmlns="http://www.w3.org/2000/svg"><path d="M8 6 L20 6 L24 12 L24 34 L8 34 Z M20 6 L20 12 L24 12" stroke="${c}" stroke-width="2" fill="none" opacity="${o}"/><rect x="12" y="16" width="8" height="2" rx="0.5" fill="${c}" opacity="${o}"/><rect x="12" y="21" width="8" height="2" rx="0.5" fill="${c}" opacity="${o}"/></svg>`;
                break;
            case 'people':
                svg = `<svg width="40" height="40" viewBox="0 0 40 40" xmlns="http://www.w3.org/2000/svg"><circle cx="20" cy="12" r="6" fill="${c}" opacity="${o}"/><path d="M8 34 q0 -10 12 -10 q12 0 12 10" fill="${c}" opacity="${o}"/></svg>`;
                break;
            case 'policy':
                svg = `<svg width="40" height="40" viewBox="0 0 40 40" xmlns="http://www.w3.org/2000/svg"><path d="M20 4 L32 10 L32 22 Q32 32 20 36 Q8 32 8 22 L8 10 Z" fill="${c}" opacity="${o}"/><path d="M14 20 L18 24 L26 14" stroke="${c}" stroke-width="2" fill="none" opacity="${o}"/></svg>`;
                break;
            case 'glossary':
                svg = `<svg width="40" height="40" viewBox="0 0 40 40" xmlns="http://www.w3.org/2000/svg"><rect x="8" y="6" width="24" height="28" rx="2" fill="none" stroke="${c}" stroke-width="2" opacity="${o}"/><path d="M12 14 h16 M12 20 h12 M12 26 h10" stroke="${c}" stroke-width="1.5" opacity="${o}"/></svg>`;
                break;
            case 'product':
                svg = `<svg width="40" height="40" viewBox="0 0 40 40" xmlns="http://www.w3.org/2000/svg"><rect x="6" y="10" width="28" height="20" rx="2" fill="none" stroke="${c}" stroke-width="2" opacity="${o}"/><path d="M14 10 L20 4 L26 10" stroke="${c}" stroke-width="1.5" fill="none" opacity="${o}"/></svg>`;
                break;
            default:
                svg = `<svg width="40" height="40" viewBox="0 0 40 40" xmlns="http://www.w3.org/2000/svg"><rect x="8" y="8" width="24" height="24" rx="4" fill="${c}" opacity="${o}"/></svg>`;
        }
        return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
    }

    function createDatabaseIcon(color) {
        return createNodeIcon('system', color);
    }

    function getNodeShape(group) {
        return MAP_GROUP_SHAPES[group] || 'round-rectangle';
    }

    function slugify(value) {
        return String(value)
            .toLowerCase()
            .replace(/\s+/g, '-')
            .replace(/[^\w-]/g, '');
    }

    function saveToHistory() {
        const state = {
            layout: MapState.layout,
            overlay: MapState.overlay,
            mapType: MapState.mapType,
            zoom: MapState.network ? MapState.network.zoom() : 1
        };
        MapState.history.push(state);
        MapState.historyIndex = MapState.history.length - 1;
        // Keep only last 50 states
        if (MapState.history.length > 50) {
            MapState.history.shift();
            MapState.historyIndex--;
        }
    }

    function restoreState(state) {
        MapState.layout = state.layout;
        MapState.overlay = state.overlay || 'none';
        MapState.mapType = state.mapType;
        
        const layoutSelect = document.getElementById('mapLayoutSelect');
        const mapTypeSelect = document.getElementById('mapTypeSelect');
        const overlayBtnText = document.getElementById('mapOverlayBtnText');
        
        if (layoutSelect) {
            layoutSelect.value = state.layout;
            syncMapLayoutRichUiFromSelect();
        }
        if (mapTypeSelect) mapTypeSelect.value = state.mapType;
        if (overlayBtnText) overlayBtnText.textContent = overlayButtonLabel(MapState.overlay, MapState.mapType);
        
        updateNetworkLayout();
        if (MapState.network && state.zoom) {
            MapState.network.zoom(state.zoom);
        }
    }

    function saveMapState() {
        const state = {
            layout: MapState.layout,
            overlay: MapState.overlay,
            mapType: MapState.mapType,
            nodes: MapState.nodes,
            edges: MapState.edges
        };
        localStorage.setItem('budg-map-state', JSON.stringify(state));
        alert('Map state saved successfully!');
    }

    function getCurrentElements() {
        if (!MapState.network) return [];
        return MapState.network.json().elements;
    }

    function exportMapAsPNG() {
        if (!MapState.network) {
            alert('No map to export');
            return;
        }

        const png = MapState.network.png({
            output: 'blob',
            bg: '#ffffff',
            full: true
        });

        png.then(blob => {
            if (!blob || blob.size === 0) {
                alert('Export failed: empty image');
                return;
            }
            const url = URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = url;
            link.download = `budg-map-${Date.now()}.png`;
            link.click();
            URL.revokeObjectURL(url);
        }).catch(err => {
            console.error('[MAPS] Export PNG failed', err);
            alert('Export failed. Try zooming or refreshing the map.');
        });
    }

    // Helper for categoryToModule when not provided by search (e.g. standalone maps.html)
    if (typeof window.categoryToModule === 'undefined') {
        window.categoryToModule = function(category) {
            if (!category) return null;
            const c = category.toString().toLowerCase().trim().replace(/\s+/g, '-');
            const categoryMap = {
                'dataset': 'dataset',
                'datasets': 'dataset',
                'data-sets': 'dataset',
                'system': 'system',
                'systems': 'system',
                'interface': 'interface',
                'interfaces': 'interface'
            };
            return categoryMap[c] || c;
        };
    }

})();

