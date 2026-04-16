/**
 * map-engine.js
 * ─────────────────────────────────────────────────────────────────────────────
 * Central engine for every map type in the system.
 *
 * Supports two rendering backends:
 *   • 'cytoscape'  — relationship / lineage maps (system, dataset, process, etc.)
 *   • 'leaflet'    — geographic maps
 *
 * Usage (new pattern):
 *   const engine = new MapEngine('system-lineage');
 *   engine.init(entityId);
 *
 *   const geo = new MapEngine('geographic');
 *   geo.init(mapId);
 *
 * Backward-compatible factory mode (used by SharedMapCore callers):
 *   window.SharedMapCore = function(cfg) { return new MapEngine(cfg); }
 *   — MapEngine detects a plain config object and routes accordingly.
 *
 * Every existing window global method name is preserved on the returned
 * instance so that existing module pages call the same API without changes.
 *
 * Dependencies (must be loaded before this file):
 *   • map-render-utils.js  (window.MapRenderUtils — context menu helper, optional at parse time;
 *                           load before this file so helpers exist when the user opens a menu)
 *   • map-configs.js       (window.MapConfigs)
 *   • cytoscape.min.js     (window.cytoscape)
 *   • dagre.min.js         (window.cytoscapeDagre or window['cytoscape-dagre'])
 *   • leaflet.min.js       (window.L — only for geographic maps)
 *   • unified-map-controller.js  (window.exportMapWithOverlays,
 *                                  window.openMapFullscreen,
 *                                  window.SharedMapDropdowns,
 *                                  window.setupMapLegendDropdown)
 *   • map-ui.js            (window.MapOverlayPanel, window.OverlayColumns)
 */

(function () {
    'use strict';

    // =========================================================================
    // Static flag: dagre registered once across all instances
    // =========================================================================
    var _dagreRegistered = false;

    function _ensureDagre() {
        if (_dagreRegistered) return;
        try {
            var dagre = window.cytoscapeDagre || window['cytoscape-dagre'];
            if (dagre && typeof cytoscape !== 'undefined') {
                cytoscape.use(dagre);
                _dagreRegistered = true;
            }
        } catch (e) {
            console.warn('[MapEngine] Could not register dagre layout:', e);
        }
    }

    // =========================================================================
    // MapEngine Class
    // =========================================================================
    function MapEngine(configKeyOrLegacyCfg) {
        // Support both new-style string key and legacy plain-object config
        if (typeof configKeyOrLegacyCfg === 'object' && configKeyOrLegacyCfg !== null) {
            this._initFromLegacyConfig(configKeyOrLegacyCfg);
        } else {
            this._initFromConfigKey(configKeyOrLegacyCfg);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Initialization helpers
    // ─────────────────────────────────────────────────────────────────────────

    MapEngine.prototype._initFromConfigKey = function (configKey) {
        var cfg = (window.MapConfigs && window.MapConfigs[configKey]) || {};
        this._configKey      = configKey;
        this._engineType     = cfg.engine || 'cytoscape';
        this._mapConfig      = cfg;
        this._apiLoader      = null; // set by MapConfigs.register() later
        this._nodeBuilder    = null;
        this._edgeBuilder    = null;
        this._legendBuilder  = cfg.legendBuilder || null;
        this._contextMenuActions = [];
        this._onNodeClick    = null;
        this._onEdgeClick    = null;
        this._onBackgroundClick = null;
        this._mapId          = configKey.replace(/[^a-zA-Z0-9]/g, '') + 'Map';
        this._containerSelector   = null;
        this._loadingSelector     = '[data-map-loading]';
        this._placeholderSelector = '[data-map-placeholder]';

        this._state = this._buildDefaultState(cfg);
        this._state.mapType = cfg.defaultMapType || configKey;
    };

    MapEngine.prototype._initFromLegacyConfig = function (legacyCfg) {
        // Legacy SharedMapCore config object — preserved 100%
        var configKey = legacyCfg.defaultMapType || legacyCfg.facetType || 'system-lineage';
        var cfg = (window.MapConfigs && window.MapConfigs[configKey]) || {};
        this._configKey      = configKey;
        this._engineType     = 'cytoscape';
        this._mapConfig      = cfg;
        this._apiLoader      = legacyCfg.dataLoader || null;
        this._nodeBuilder    = legacyCfg.nodeBuilder || null;
        this._edgeBuilder    = legacyCfg.edgeBuilder || null;
        this._legendBuilder  = legacyCfg.legendBuilder || null;
        this._contextMenuActions = legacyCfg.contextMenuActions || [];
        this._onNodeClick    = legacyCfg.onNodeClick    || null;
        this._onEdgeClick    = legacyCfg.onEdgeClick    || null;
        this._onBackgroundClick = legacyCfg.onBackgroundClick || null;
        this._mapId          = legacyCfg.mapId || configKey.replace(/[^a-zA-Z0-9]/g, '') + 'Map';
        this._containerSelector   = legacyCfg.containerSelector || null;
        this._loadingSelector     = legacyCfg.loadingSelector     || '[data-map-loading]';
        this._placeholderSelector = legacyCfg.placeholderSelector || '[data-map-placeholder]';

        this._state = this._buildDefaultState(cfg);
        this._state.mapType = legacyCfg.defaultMapType || configKey;
        this._facetType = legacyCfg.facetType || configKey.split('-')[0];
    };

    MapEngine.prototype._buildDefaultState = function (cfg) {
        return {
            initialized:            false,
            network:                null,     // cytoscape instance or leaflet map
            canvas:                 null,     // DOM element
            loadingEl:              null,
            placeholderEl:          null,
            entityId:               null,
            entityData:             null,
            rawData:                null,
            nodes:                  [],
            edges:                  [],
            hiddenNodes:            new Set(),
            focusedNode:            null,
            hiddenUpstreamNodes:    new Set(),
            hiddenDownstreamNodes:  new Set(),
            layout:                 'top-to-bottom',
            mapType:                (cfg && cfg.defaultMapType) || 'system-lineage',
            overlay:                'none',
            overlayColumnsByType:   {},
            showLabels:             true,
            hopsCount:              15,
            filters: {
                systemInterfaces:   true,
                dataAttributeLinks: true
            },
            nodeFilters: {
                classifications: [],
                types:           [],
                lifecycles:      [],
                relationshipTypes: []
            },
            datasetNodeFilters: {
                types:      [],
                lifecycles: []
            }
        };
    };

    // ─────────────────────────────────────────────────────────────────────────
    // Style / icon helpers (delegate to registered modules)
    // ─────────────────────────────────────────────────────────────────────────

    MapEngine.prototype._getStyles = function () {
        var cfg = this._mapConfig;
        if (cfg && typeof cfg.styleModule === 'function') return cfg.styleModule();
        return window.InterfaceMapStyles || window.SharedMapStyles || null;
    };

    MapEngine.prototype._getIcons = function () {
        var cfg = this._mapConfig;
        if (cfg && typeof cfg.iconModule === 'function') return cfg.iconModule();
        return window.InterfaceMapIcons || window.SharedMapIcons || null;
    };

    MapEngine.prototype._getColors = function () {
        var styles = this._getStyles();
        if (styles && styles.colors) return styles.colors;
        return {
            currentSystem:       '#f97316',
            currentSystemBorder: '#ea580c',
            otherSystem:         '#1f2937',
            otherSystemBorder:   '#374151',
            interfaceLine:       '#94a3b8',
            attributeLine:       '#64748b',
            upstreamHighlight:   '#ef4444',
            downstreamHighlight: '#22c55e',
            lockedSystem:        '#6b7280'
        };
    };

    MapEngine.prototype._getNodeSizes = function () {
        var styles = this._getStyles();
        if (styles && styles.nodeSizes) return styles.nodeSizes;
        return {
            system:  { width: 140, height: 80 },
            dataset: { width: 140, height: 80 }
        };
    };

    // ─────────────────────────────────────────────────────────────────────────
    // Loading / placeholder UI
    // ─────────────────────────────────────────────────────────────────────────

    MapEngine.prototype._showLoading = function () {
        if (this._state.loadingEl)   this._state.loadingEl.style.display = 'flex';
        if (this._state.placeholderEl) this._state.placeholderEl.style.display = 'none';
    };

    MapEngine.prototype._hideLoading = function () {
        if (this._state.loadingEl) this._state.loadingEl.style.display = 'none';
    };

    MapEngine.prototype._showPlaceholder = function (message) {
        this._hideLoading();
        if (this._state.placeholderEl) {
            this._state.placeholderEl.textContent = message;
            this._state.placeholderEl.style.display = 'block';
        } else if (this._state.canvas) {
            this._state.canvas.innerHTML =
                '<div style="padding:2rem;text-align:center;color:var(--text-secondary)">' +
                message + '</div>';
        }
    };

    // ─────────────────────────────────────────────────────────────────────────
    // DOM resolution
    // ─────────────────────────────────────────────────────────────────────────

    MapEngine.prototype._resolveDOM = function () {
        var mapId = this._mapId;
        // Canvas
        if (this._containerSelector) {
            this._state.canvas = document.querySelector(this._containerSelector);
        } else {
            this._state.canvas =
                document.getElementById(mapId + 'Canvas') ||
                document.querySelector('#' + mapId + 'Canvas') ||
                document.querySelector('[data-map-canvas]');
        }
        // Loading
        this._state.loadingEl = document.querySelector(this._loadingSelector);
        // Placeholder
        this._state.placeholderEl = document.querySelector(this._placeholderSelector);
    };

    // ─────────────────────────────────────────────────────────────────────────
    // Cytoscape: style sheet
    // ─────────────────────────────────────────────────────────────────────────

    MapEngine.prototype._getCytoscapeStyle = function () {
        var styles = this._getStyles();
        if (styles && typeof styles.getCytoscapeStyles === 'function') {
            return styles.getCytoscapeStyles(this._mapId);
        }
        var colors = this._getColors();
        var sizes  = this._getNodeSizes();
        var mapId  = this._mapId;

        // Read current curve-style from SharedMapDropdowns API if available
        var curveStyle = 'bezier';
        if (window._sharedDropdownApis && window._sharedDropdownApis[mapId] &&
            typeof window._sharedDropdownApis[mapId].getCurveStyle === 'function') {
            curveStyle = window._sharedDropdownApis[mapId].getCurveStyle();
        }

        return [
            {
                selector: 'node',
                style: {
                    'width':                   sizes.system ? sizes.system.width  : 140,
                    'height':                  sizes.system ? sizes.system.height : 80,
                    'shape':                   'round-rectangle',
                    'background-color':        colors.otherSystem,
                    'border-width':            2,
                    'border-color':            colors.otherSystemBorder,
                    'label':                   'data(label)',
                    'text-valign':             'center',
                    'text-halign':             'center',
                    'text-wrap':               'wrap',
                    'text-max-width':          120,
                    'font-size':               '12px',
                    'font-weight':             '500',
                    'color':                   '#ffffff',
                    'text-outline-width':      1,
                    'text-outline-color':      '#000000',
                    'background-image':        'data(backgroundImage)',
                    'background-fit':          'contain',
                    'background-position-x':   '50%',
                    'background-position-y':   '30%',
                    'background-width':        '60%',
                    'background-height':       '50%'
                }
            },
            {
                selector: 'node[?isCurrent]',
                style: {
                    'background-color': colors.currentSystem,
                    'border-color':     colors.currentSystemBorder,
                    'border-width':     3
                }
            },
            {
                selector: 'node[?isLocked]',
                style: {
                    'background-color': colors.lockedSystem,
                    'opacity':          0.6
                }
            },
            {
                selector: 'node[?isDataset]',
                style: {
                    'width':  sizes.dataset ? sizes.dataset.width  : (sizes.system ? sizes.system.width  : 140),
                    'height': sizes.dataset ? sizes.dataset.height : (sizes.system ? sizes.system.height : 80)
                }
            },
            {
                selector: 'edge',
                style: {
                    'width':                2,
                    'line-color':           colors.interfaceLine,
                    'target-arrow-color':   colors.interfaceLine,
                    'target-arrow-shape':   'triangle',
                    'curve-style':          curveStyle,
                    'label':                'data(label)',
                    'text-rotation':        'autorotate',
                    'text-margin-y':        -10,
                    'font-size':            '10px',
                    'color':               '#64748b'
                }
            },
            {
                selector: 'edge[?isAttributeLine]',
                style: {
                    'line-color':          colors.attributeLine,
                    'target-arrow-color':  colors.attributeLine,
                    'line-style':          'solid'
                }
            },
            {
                selector: 'edge[?isInterfaceLine]',
                style: {
                    'line-color':          colors.interfaceLine,
                    'target-arrow-color':  colors.interfaceLine,
                    'line-style':          'dashed'
                }
            },
            {
                selector: 'edge[?isHighlighted]',
                style: {
                    'width':               4,
                    'line-color':          colors.upstreamHighlight,
                    'target-arrow-color':  colors.upstreamHighlight
                }
            },
            {
                selector: 'node.highlighted',
                style: {
                    'border-width': 4,
                    'border-color': colors.upstreamHighlight || '#ef4444'
                }
            },
            {
                selector: 'edge.highlighted',
                style: {
                    'width':              4,
                    'line-color':         colors.upstreamHighlight || '#ef4444',
                    'target-arrow-color': colors.upstreamHighlight || '#ef4444'
                }
            }
        ];
    };

    // ─────────────────────────────────────────────────────────────────────────
    // Cytoscape: layout config
    // ─────────────────────────────────────────────────────────────────────────

    MapEngine.prototype._buildCytoscapeLayout = function () {
        var styles = this._getStyles();
        var mapId  = this._mapId;
        var layout = this._state.layout;

        var ddApi = window._sharedDropdownApis && window._sharedDropdownApis[mapId];
        var DEFAULT_SF  = 2.2;
        var DEFAULT_PAD = 90;
        var sf = (ddApi && typeof ddApi.getSpacingFactor  === 'function') ? ddApi.getSpacingFactor()  : DEFAULT_SF;
        var sp = (ddApi && typeof ddApi.getSpacingPadding === 'function') ? ddApi.getSpacingPadding() : DEFAULT_PAD;

        var coseOrganicBase = {
            name:             'cose',
            idealEdgeLength:   Math.round(100 * sf),
            nodeOverlap:       20,
            refresh:           20,
            fit:               true,
            padding:           sp,
            randomize:         false,
            componentSpacing:  100,
            nodeRepulsion:     Math.round(4000000 * sf),
            edgeElasticity:    100,
            nestingFactor:     5,
            gravity:           0.25,
            numIter:           1000,
            initialTemp:       200,
            coolingFactor:     0.95,
            minTemp:           1.0
        };
        var layoutMap = {
            'left-to-right': {
                name:    'dagre',
                rankDir: 'LR',
                nodeSep: 50,
                edgeSep: 20,
                rankSep: 100,
                padding: sp
            },
            'right-to-left': {
                name:    'dagre',
                rankDir: 'RL',
                nodeSep: 50,
                edgeSep: 20,
                rankSep: 100,
                padding: sp
            },
            'top-to-bottom': {
                name:    'dagre',
                rankDir: 'TB',
                nodeSep: 50,
                edgeSep: 20,
                rankSep: 100,
                padding: sp
            },
            'force': coseOrganicBase,
            'organic': coseOrganicBase
        };

        var layoutCfg = layoutMap[layout] || layoutMap['top-to-bottom'];

        if (styles && typeof styles.getLayoutConfig === 'function') {
            var custom = styles.getLayoutConfig(layout);
            if (custom) {
                return Object.assign({}, layoutCfg, custom, {
                    padding: custom.padding !== undefined ? custom.padding : sp
                });
            }
        }

        return layoutCfg;
    };

    // ─────────────────────────────────────────────────────────────────────────
    // Cytoscape: build elements array from { nodes, edges }
    // ─────────────────────────────────────────────────────────────────────────

    MapEngine.prototype._buildCytoscapeElements = function (graph) {
        var elements = [];
        var colors   = this._getColors();
        var icons    = this._getIcons();
        var currentEntityId = this._state.entityId ? String(this._state.entityId) : null;
        var showLabels = this._state.showLabels;

        (graph.nodes || []).forEach(function (node) {
            var nodeId   = String(node.id);
            var isCurrent = node.isCurrent || (currentEntityId && nodeId === currentEntityId);
            var isLocked  = node.isLocked || false;
            var group     = node.group || 'system';

            var nodeColor, borderColor;
            if (isCurrent) {
                nodeColor   = colors.currentSystem;
                borderColor = colors.currentSystemBorder;
            } else if (isLocked) {
                nodeColor   = colors.lockedSystem;
                borderColor = colors.lockedSystem;
            } else {
                nodeColor   = colors.otherSystem;
                borderColor = colors.otherSystemBorder;
            }

            // Icon
            var bgImage = node.backgroundImage || node.icon || '';
            if (!bgImage && icons) {
                try {
                    if (isLocked && typeof icons.createLockIcon === 'function') {
                        bgImage = icons.createLockIcon();
                    } else if (group === 'dataset' && typeof icons.createDatasetIcon === 'function') {
                        bgImage = icons.createDatasetIcon();
                    } else if (typeof icons.getIconByType === 'function') {
                        bgImage = icons.getIconByType(group) || '';
                    }
                } catch (e) { /* icon lookup is best-effort */ }
            }

            var nodeData = Object.assign({}, node, {
                id:              nodeId,
                label:           node.label || node.name || node.primaryName || nodeId,
                group:           group,
                isCurrent:       isCurrent || undefined,
                isLocked:        isLocked  || undefined,
                nodeColor:       nodeColor,
                borderColor:     borderColor,
                backgroundImage: bgImage,
                isDataset:       (group === 'dataset') || node.isDataset || undefined,
                isSystem:        (group === 'system')  || node.isSystem  || undefined
            });

            elements.push({ data: nodeData });
        });

        (graph.edges || []).forEach(function (edge) {
            var edgeData = Object.assign({}, edge, {
                id:     edge.id || (String(edge.source || edge.from) + '->' + String(edge.target || edge.to)),
                source: String(edge.source || edge.from),
                target: String(edge.target || edge.to),
                label:  showLabels ? (edge.label || '') : ''
            });
            // Normalize vis.js-style from/to to cytoscape source/target
            delete edgeData.from;
            delete edgeData.to;
            elements.push({ data: edgeData });
        });

        return elements;
    };

    // ─────────────────────────────────────────────────────────────────────────
    // Cytoscape: render
    // ─────────────────────────────────────────────────────────────────────────

    MapEngine.prototype._renderCytoscape = function (graph) {
        var self    = this;
        var canvas  = this._state.canvas;

        if (!canvas) {
            console.error('[MapEngine] Canvas not found for', this._configKey);
            return;
        }

        // Destroy existing instance
        if (this._state.network) {
            try { this._state.network.destroy(); } catch (e) {}
            this._state.network = null;
        }
        canvas.innerHTML = '';

        if (!graph || !(graph.nodes || []).length) {
            this._showPlaceholder('No data to display.');
            return;
        }

        this._hideLoading();

        try {
            _ensureDagre();

            var elements   = this._buildCytoscapeElements(graph);
            var layoutCfg  = this._buildCytoscapeLayout();
            var styleCfg   = this._getCytoscapeStyle();

            this._state.network = cytoscape({
                container:        canvas,
                elements:         elements,
                style:            styleCfg,
                layout:           layoutCfg,
                minZoom:          0.1,
                maxZoom:          2,
                wheelSensitivity: 0.2
            });

            this._setupCytoscapeEvents();

            // Fit after layout settles
            setTimeout(function () {
                if (self._state.network) {
                    self._state.network.fit();
                    self._state.network.center();
                }
            }, 100);

        } catch (err) {
            console.error('[MapEngine] Error rendering cytoscape map:', err);
            this._showPlaceholder('Error rendering map: ' + err.message);
        }
    };

    // ─────────────────────────────────────────────────────────────────────────
    // Cytoscape: events
    // ─────────────────────────────────────────────────────────────────────────

    MapEngine.prototype._setupCytoscapeEvents = function () {
        var self = this;
        var cy   = this._state.network;
        if (!cy) return;

        cy.on('tap', 'node', function (evt) {
            var nodeData = evt.target.data();
            if (typeof self._onNodeClick === 'function') {
                self._onNodeClick(nodeData, evt);
            } else {
                self._showContextMenu(evt, nodeData);
            }
        });

        cy.on('tap', 'edge', function (evt) {
            var edgeData = evt.target.data();
            if (typeof self._onEdgeClick === 'function') {
                self._onEdgeClick(edgeData, evt);
            }
        });

        cy.on('tap', function (evt) {
            if (evt.target === cy) {
                if (typeof self._onBackgroundClick === 'function') {
                    self._onBackgroundClick(evt);
                } else {
                    self._resetHighlights();
                }
            }
        });
    };

    MapEngine.prototype._showContextMenu = function (evt, nodeData) {
        if (window.MapRenderUtils && typeof window.MapRenderUtils.showMapContextMenuFromActions === 'function') {
            window.MapRenderUtils.showMapContextMenuFromActions({
                evt: evt,
                nodeData: nodeData,
                actions: this._contextMenuActions
            });
            return;
        }
        if (!this._contextMenuActions || !this._contextMenuActions.length) return;
        var existing = document.querySelector('.map-context-menu');
        if (existing) existing.remove();
        var menu = document.createElement('div');
        menu.className = 'map-context-menu';
        menu.style.cssText =
            'position:fixed;' +
            'left:' + (evt.originalEvent ? evt.originalEvent.clientX : 0) + 'px;' +
            'top:' + (evt.originalEvent ? evt.originalEvent.clientY : 0) + 'px;' +
            'background:#fff;border:1px solid #e5e7eb;border-radius:4px;' +
            'box-shadow:0 4px 6px rgba(0,0,0,0.1);z-index:10000;min-width:200px;';
        var selfActions = this._contextMenuActions;
        selfActions.forEach(function (action) {
            var item = document.createElement('div');
            item.className = 'map-context-menu-item';
            item.textContent = action.label;
            item.style.cssText = 'padding:8px 16px;cursor:pointer;border-bottom:1px solid #f3f4f6;';
            item.addEventListener('mouseenter', function () { item.style.background = '#f9fafb'; });
            item.addEventListener('mouseleave', function () { item.style.background = '#fff'; });
            item.addEventListener('click', function () {
                action.action(nodeData);
                menu.remove();
            });
            menu.appendChild(item);
        });
        document.body.appendChild(menu);
        setTimeout(function () {
            document.addEventListener('click', function closeMenu() {
                menu.remove();
                document.removeEventListener('click', closeMenu);
            }, { once: true });
        }, 0);
    };

    // ─────────────────────────────────────────────────────────────────────────
    // Highlight helpers
    // ─────────────────────────────────────────────────────────────────────────

    MapEngine.prototype._resetHighlights = function () {
        if (!this._state.network) return;
        this._state.network.elements().removeClass('highlighted');
        this._state.focusedNode = null;
    };

    MapEngine.prototype._highlightConnections = function (nodeId, direction) {
        if (!this._state.network) return;
        this._resetHighlights();
        var node = this._state.network.getElementById(String(nodeId));
        if (!node || node.empty()) return;
        this._state.focusedNode = nodeId;
        node.addClass('highlighted');
        var dir = direction || 'both';
        if (dir === 'both' || dir === 'upstream')   node.incomers().addClass('highlighted');
        if (dir === 'both' || dir === 'downstream') node.outgoers().addClass('highlighted');
    };

    // ─────────────────────────────────────────────────────────────────────────
    // Leaflet: render (geographic engine)
    // ─────────────────────────────────────────────────────────────────────────

    MapEngine.prototype._renderLeaflet = function (geoConfig) {
        var canvas = this._state.canvas;
        if (!canvas) {
            console.error('[MapEngine] Canvas not found for geographic map');
            return;
        }
        if (typeof window.L === 'undefined') {
            console.error('[MapEngine] Leaflet library (L) not available');
            this._showPlaceholder('Mapping library is not available.');
            return;
        }

        // Destroy existing map
        if (this._state.network) {
            try { this._state.network.remove(); } catch (e) {}
            this._state.network = null;
        }

        this._hideLoading();

        var cfg    = this._mapConfig;
        var center = (geoConfig && geoConfig.center) || cfg.defaultCenter || [51.5, -0.1];
        var zoom   = (geoConfig && geoConfig.zoom)   || cfg.defaultZoom   || 10;
        var tileUrl = cfg.tileUrl || 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png';
        var attr    = cfg.tileAttribution || '';

        this._state.network = window.L.map(canvas, {
            center: center,
            zoom:   zoom
        });

        window.L.tileLayer(tileUrl, { attribution: attr }).addTo(this._state.network);

        // Load layers if apiLoader is registered
        var self = this;
        if (typeof cfg.apiLoader === 'function') {
            cfg.apiLoader(this._state.entityId).then(function (mapData) {
                if (mapData && mapData.center) {
                    self._state.network.setView(mapData.center, mapData.zoom || zoom);
                }
                if (mapData && mapData.layers) {
                    mapData.layers.forEach(function (layer) {
                        if (layer.visible !== false) {
                            // Individual layer rendering delegated to GeographicMapLoader
                            if (typeof window.loadMarkersForLayer === 'function') {
                                window.loadMarkersForLayer(self._state.network, layer);
                            }
                        }
                    });
                }
            }).catch(function (err) {
                console.error('[MapEngine] Leaflet data load error:', err);
            });
        }
    };

    // ─────────────────────────────────────────────────────────────────────────
    // Legend
    // ─────────────────────────────────────────────────────────────────────────

    MapEngine.prototype._getLegendBuilder = function () {
        if (this._legendBuilder) return this._legendBuilder;
        var cfg = this._mapConfig;
        if (cfg && typeof cfg.legendBuilder === 'function') return cfg.legendBuilder;
        return null;
    };

    // ─────────────────────────────────────────────────────────────────────────
    // Overlay helpers (Cytoscape maps)
    // ─────────────────────────────────────────────────────────────────────────

    MapEngine.prototype._getApiLoader = function () {
        if (typeof this._apiLoader === 'function') return this._apiLoader;
        var cfg = this._mapConfig;
        if (cfg && typeof cfg.apiLoader === 'function') return cfg.apiLoader;
        return null;
    };

    MapEngine.prototype._getNodeBuilder = function () {
        if (typeof this._nodeBuilder === 'function') return this._nodeBuilder;
        var cfg = this._mapConfig;
        if (cfg && typeof cfg.nodeBuilder === 'function') return cfg.nodeBuilder;
        return null;
    };

    MapEngine.prototype._getEdgeBuilder = function () {
        if (typeof this._edgeBuilder === 'function') return this._edgeBuilder;
        var cfg = this._mapConfig;
        if (cfg && typeof cfg.edgeBuilder === 'function') return cfg.edgeBuilder;
        return null;
    };

    // ─────────────────────────────────────────────────────────────────────────
    // Core init / reload pipeline
    // ─────────────────────────────────────────────────────────────────────────

    MapEngine.prototype._loadAndRender = function () {
        var self       = this;
        var apiLoader  = this._getApiLoader();
        var nodeBuilder = this._getNodeBuilder();
        var edgeBuilder = this._getEdgeBuilder();

        if (!apiLoader) {
            // No loader registered yet — show placeholder but don't error
            this._showPlaceholder('Map configuration pending — apiLoader not registered.');
            return Promise.resolve();
        }

        this._showLoading();

        return Promise.resolve()
            .then(function () {
                return apiLoader(
                    self._state.entityId,
                    self._state.mapType,
                    {
                        filters:            self._state.filters,
                        nodeFilters:        self._state.nodeFilters,
                        datasetNodeFilters: self._state.datasetNodeFilters,
                        hopsCount:          self._state.hopsCount
                    }
                );
            })
            .then(function (data) {
                self._state.rawData = data;

                var nodes, edges;

                if (nodeBuilder) {
                    nodes = nodeBuilder(data, self._state);
                } else if (data && data.nodes) {
                    nodes = data.nodes;
                } else {
                    nodes = [];
                }

                if (edgeBuilder) {
                    edges = edgeBuilder(data, self._state);
                } else if (data && data.edges) {
                    edges = data.edges;
                } else {
                    edges = [];
                }

                self._state.nodes = nodes;
                self._state.edges = edges;
                self._state.entityData = data && data.entity ? data.entity : null;

                self._renderCytoscape({ nodes: nodes, edges: edges });
                self.updateLegend();
            })
            .catch(function (err) {
                console.error('[MapEngine] Failed to load map data:', err);
                self._showPlaceholder('Failed to load map data.');
            })
            .then(function () {
                self._hideLoading();
            });
    };

    // =========================================================================
    // PUBLIC API — mirrors every method exposed by existing module globals
    // =========================================================================

    /**
     * Initialize the map for the given entity/map id.
     * @param {number|string} entityId
     * @returns {Promise<void>}
     */
    MapEngine.prototype.init = function (entityId) {
        this._state.entityId = entityId;
        this._resolveDOM();

        if (!this._state.canvas) {
            console.error('[MapEngine] Canvas element not found — mapId:', this._mapId);
            return Promise.resolve();
        }

        this._state.initialized = true;

        if (this._engineType === 'leaflet') {
            this._renderLeaflet(null);
            return Promise.resolve();
        }

        return this._loadAndRender();
    };

    /** Switch to a different map type (e.g. 'dataset-lineage') and re-render. */
    MapEngine.prototype.setMapType = function (type) {
        this._state.mapType = type;
        return this.init(this._state.entityId);
    };

    /** Change the Cytoscape layout direction without reloading data. */
    MapEngine.prototype.setLayout = function (layout) {
        this._state.layout = layout;
        if (this._state.network && this._engineType === 'cytoscape') {
            try {
                this._state.network.layout(this._buildCytoscapeLayout()).run();
            } catch (e) {
                console.warn('[MapEngine] setLayout error:', e);
            }
        }
    };

    /** Set the hops/depth limit and reload. */
    MapEngine.prototype.setHopsCount = function (n) {
        this._state.hopsCount = parseInt(n, 10) || 15;
        return this.init(this._state.entityId);
    };

    /** Set the active overlay type and re-render. */
    MapEngine.prototype.setOverlay = function (overlay) {
        this._state.overlay = overlay;
        if (this._state.network && this._engineType === 'cytoscape') {
            this._renderCytoscape({ nodes: this._state.nodes, edges: this._state.edges });
        }
    };

    /** Set selected overlay columns for a given overlay type. */
    MapEngine.prototype.setOverlayColumns = function (overlayType, columnIds) {
        if (!this._state.overlayColumnsByType) this._state.overlayColumnsByType = {};
        this._state.overlayColumnsByType[overlayType] = Array.isArray(columnIds) ? columnIds.slice() : [];
    };

    /** Get selected overlay column ids for a given overlay type. */
    MapEngine.prototype.getOverlayColumns = function (overlayType) {
        var raw = this._state.overlayColumnsByType && this._state.overlayColumnsByType[overlayType];
        if (window.OverlayColumns && typeof window.OverlayColumns.resolveSelectedColumnIds === 'function') {
            return window.OverlayColumns.resolveSelectedColumnIds(raw, overlayType);
        }
        if (Array.isArray(raw) && raw.length > 0) return raw.slice();
        if (window.OverlayColumns && typeof window.OverlayColumns.getDefaultOverlayColumnIds === 'function') {
            return window.OverlayColumns.getDefaultOverlayColumnIds(overlayType);
        }
        return [];
    };

    /** Toggle a named boolean filter (e.g. 'systemInterfaces') and reload. */
    MapEngine.prototype.setFilter = function (filterType, enabled) {
        if (Object.prototype.hasOwnProperty.call(this._state.filters, filterType)) {
            this._state.filters[filterType] = enabled;
            return this.init(this._state.entityId);
        }
        return Promise.resolve();
    };

    /** Set node classification/type/lifecycle filters and reload. */
    MapEngine.prototype.setNodeFilters = function (filters) {
        this._state.nodeFilters = Object.assign({}, this._state.nodeFilters, filters);
        return this.init(this._state.entityId);
    };

    /** Set dataset-specific node filters and reload. */
    MapEngine.prototype.setDatasetNodeFilters = function (filters) {
        this._state.datasetNodeFilters = Object.assign({}, this._state.datasetNodeFilters, filters);
        return this.init(this._state.entityId);
    };

    /** Toggle edge label visibility and re-render. */
    MapEngine.prototype.toggleInterfaceLabels = function (show) {
        this._state.showLabels = (show !== undefined) ? show : !this._state.showLabels;
        if (this._state.network && this._engineType === 'cytoscape') {
            this._renderCytoscape({ nodes: this._state.nodes, edges: this._state.edges });
        }
    };

    /** Toggle the minimap navigator. */
    MapEngine.prototype.toggleNavigator = function () {
        var MRU = window.MapRenderUtils;
        var container = this._state.canvas && this._state.canvas.parentElement;
        if (!container || !this._state.network) return;
        if (MRU && typeof MRU.attachOrToggleLineageMinimap === 'function') {
            MRU.attachOrToggleLineageMinimap({
                container: container,
                network: this._state.network,
                syncViewport: true,
                cytoscapeOptions: { minZoom: 0.1, maxZoom: 0.5 }
            });
            return;
        }
        var minimap = container.querySelector('.map-minimap');
        if (minimap) {
            minimap.style.display = minimap.style.display === 'none' ? 'block' : 'none';
        }
    };

    /** Zoom in one step. */
    MapEngine.prototype.zoomIn = function () {
        if (this._state.network) {
            if (this._engineType === 'cytoscape') {
                this._state.network.zoom(this._state.network.zoom() * 1.2);
                this._state.network.center();
            } else if (this._engineType === 'leaflet') {
                this._state.network.zoomIn();
            }
        }
    };

    /** Zoom out one step. */
    MapEngine.prototype.zoomOut = function () {
        if (this._state.network) {
            if (this._engineType === 'cytoscape') {
                this._state.network.zoom(this._state.network.zoom() * 0.8);
                this._state.network.center();
            } else if (this._engineType === 'leaflet') {
                this._state.network.zoomOut();
            }
        }
    };

    /** Reset map state and reload from scratch. */
    MapEngine.prototype.resetMap = function () {
        this._state.hiddenNodes.clear();
        this._state.focusedNode = null;
        this._state.hiddenUpstreamNodes.clear();
        this._state.hiddenDownstreamNodes.clear();
        this._state.overlay = 'none';
        this._resetHighlights();
        return this.init(this._state.entityId);
    };

    /** Re-run the current layout without reloading data. */
    MapEngine.prototype.redrawMap = function () {
        if (this._state.network && this._engineType === 'cytoscape') {
            try {
                this._state.network.layout(this._buildCytoscapeLayout()).run();
            } catch (e) {
                console.warn('[MapEngine] redrawMap error:', e);
            }
        } else if (this._state.network && this._engineType === 'leaflet') {
            this._state.network.invalidateSize();
        }
    };

    /** Export the visible map as a PNG file download. */
    MapEngine.prototype.exportAsPng = function () {
        if (!this._state.network) { alert('No map to export.'); return; }
        var facetType = this._facetType || this._configKey.split('-')[0];
        var filename  = facetType + '-map-' + (this._state.entityId || 'export') + '.png';

        if (typeof window.exportMapWithOverlays === 'function') {
            window.exportMapWithOverlays(this._state.network, this._state.canvas, filename);

        } else if (this._engineType === 'cytoscape') {
            var png64 = this._state.network.png({ bg: '#ffffff', full: true, scale: 2 });
            var link  = document.createElement('a');
            link.download = filename;
            link.href     = png64;
            link.click();

        } else if (this._engineType === 'leaflet') {
            // Leaflet does not expose a native PNG export.
            // Use leaflet-image if available (window.leafletImage), otherwise
            // fall back to html2canvas on the map container, otherwise warn.
            var leafletMap = this._state.network;
            var self       = this;
            if (typeof window.leafletImage === 'function') {
                window.leafletImage(leafletMap, function (err, canvas) {
                    if (err) { console.error('[MapEngine] leafletImage error:', err); return; }
                    var link = document.createElement('a');
                    link.download = filename;
                    link.href = canvas.toDataURL('image/png');
                    link.click();
                });
            } else if (typeof window.html2canvas === 'function') {
                var container = leafletMap.getContainer();
                window.html2canvas(container).then(function (canvas) {
                    var link = document.createElement('a');
                    link.download = filename;
                    link.href = canvas.toDataURL('image/png');
                    link.click();
                }).catch(function (err) {
                    console.error('[MapEngine] html2canvas error:', err);
                    alert('Export failed. Please install leaflet-image or html2canvas to export geographic maps.');
                });
            } else {
                alert('PNG export for geographic maps requires leaflet-image or html2canvas.\n' +
                      'Add one of these libraries to the page to enable export.');
            }
        }
    };

    /** Open the map in a fullscreen tab. */
    MapEngine.prototype.openFullscreen = function () {
        if (!this._state.network) { alert('No map to open.'); return; }
        var self      = this;
        var facetType = this._facetType || this._configKey.split('-')[0];
        var filename  = facetType + '-map-' + (this._state.entityId || 'export') + '.png';
        var legendFn  = this._getLegendBuilder();

        if (typeof window.openMapFullscreen === 'function' && this._engineType === 'cytoscape') {
            window.openMapFullscreen({
                title:              facetType + ' Map — ' + (this._state.entityData && this._state.entityData.name ? this._state.entityData.name : facetType),
                network:            this._state.network,
                getCytoscapeStyle:  function () { return self._getCytoscapeStyle(); },
                layoutName:         this._state.layout || 'top-to-bottom',
                legendHtml:         legendFn ? legendFn() : '',
                exportFilename:     filename,
                toolbarAnchor:      this._state.canvas,
                mapType:            this._state.mapType || 'system-lineage',
                mapTabKind:         'map-engine-' + this._configKey,
                getState:           function () { return self._state; }
            });
        } else {
            // Fallback: open in new window
            var newWin = window.open('', '_blank');
            if (newWin && this._engineType === 'cytoscape') {
                var elStr  = JSON.stringify(this._state.network.json().elements);
                var styStr = JSON.stringify(this._getCytoscapeStyle());
                newWin.document.write(
                    '<!DOCTYPE html><html><head><title>' + facetType + ' Map</title>' +
                    '<style>body{margin:0;padding:0;}#fsMap{width:100vw;height:100vh;}</style>' +
                    '<script src="/assets/js/cytoscape.min.js"><\/script></head><body>' +
                    '<div id="fsMap"></div>' +
                    '<script>cytoscape({container:document.getElementById("fsMap"),' +
                    'elements:' + elStr + ',style:' + styStr + ',layout:{name:"preset"}});<\/script>' +
                    '</body></html>'
                );
                newWin.document.close();
            }
        }
    };

    /** Remove all overlay panels from the canvas. */
    MapEngine.prototype.clearOverlayPanels = function () {
        if (this._state.canvas) {
            this._state.canvas.querySelectorAll('.map-node-overlay-panel').forEach(function (el) {
                el.remove();
            });
        }
    };

    /** Get the HTML string for the legend (from config or legendBuilder). */
    MapEngine.prototype.getLegendHtml = function () {
        var fn = this._getLegendBuilder();
        return fn ? fn(this._state) : '';
    };

    /** Update the legend DOM element ([data-map-legend]). */
    MapEngine.prototype.updateLegend = function () {
        var html = this.getLegendHtml();
        if (!html) return;
        var el = document.querySelector('[data-map-legend]');
        if (el) el.innerHTML = html;
    };

    /** Return the full internal state (read-only reference). */
    MapEngine.prototype.getState = function () {
        return this._state;
    };

    /** Return the Cytoscape instance (or Leaflet map). */
    MapEngine.prototype.getNetwork = function () {
        return this._state.network;
    };

    /** Alias for getNetwork() used by some callers. */
    Object.defineProperty(MapEngine.prototype, 'cy', {
        get: function () { return this._state.network; }
    });

    /** Notify subscribers that map data has been updated (dispatches a custom event). */
    MapEngine.prototype.notifyMapDataUpdated = function () {
        document.dispatchEvent(new CustomEvent('mapDataUpdated', {
            detail: { configKey: this._configKey, entityId: this._state.entityId }
        }));
    };

    /** Highlight connections from/to a node. */
    MapEngine.prototype.highlightConnections = function (nodeId, direction) {
        this._highlightConnections(nodeId, direction);
    };

    /** Reset all highlights. */
    MapEngine.prototype.resetHighlights = function () {
        this._resetHighlights();
    };

    // ─────────────────────────────────────────────────────────────────────────
    // Preferences (glossary-relationships-map.js compatibility)
    // ─────────────────────────────────────────────────────────────────────────

    MapEngine.prototype.savePreferences = function () {
        try {
            localStorage.setItem(
                'mapPrefs_' + this._configKey,
                JSON.stringify({
                    layout:    this._state.layout,
                    mapType:   this._state.mapType,
                    hopsCount: this._state.hopsCount,
                    overlay:   this._state.overlay
                })
            );
        } catch (e) {}
    };

    MapEngine.prototype.resetPreferences = function () {
        try { localStorage.removeItem('mapPrefs_' + this._configKey); } catch (e) {}
    };

    MapEngine.prototype.getPreferences = function () {
        try {
            var raw = localStorage.getItem('mapPrefs_' + this._configKey);
            return raw ? JSON.parse(raw) : null;
        } catch (e) { return null; }
    };

    // ─────────────────────────────────────────────────────────────────────────
    // system-interfaces-map.js specific surface (no-op stubs for shimming)
    // ─────────────────────────────────────────────────────────────────────────

    MapEngine.prototype.getAllInterfaces = function () {
        return (this._state.rawData && this._state.rawData.interfaces) || [];
    };

    // =========================================================================
    // Expose globally
    // =========================================================================
    window.MapEngine = MapEngine;

    // -------------------------------------------------------------------------
    // Backward compatibility: window.SharedMapCore
    // Existing callers:  window.SharedMapCore({ facetType, mapId, ... })
    // will now get a MapEngine instance with the full public API.
    // -------------------------------------------------------------------------
    window.SharedMapCore = function (cfg) {
        return new MapEngine(cfg);
    };

})();
