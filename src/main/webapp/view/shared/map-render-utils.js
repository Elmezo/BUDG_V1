/**
 * map-render-utils.js
 * ─────────────────────────────────────────────────────────────────────────────
 * Cytoscape / rendering utility functions shared across every map module.
 *
 * These helpers wrap Cytoscape operations and UI patterns that appear nearly
 * identically in system-interfaces, dataset, glossary-data, glossary-
 * relationships, capability, process-data, and project-data map modules.
 *
 * Usage examples:
 *   // Zoom (with optional viewport re-center):
 *   MapRenderUtils.zoomIn(State.network);           // zoom only
 *   MapRenderUtils.zoomIn(State.network, true);     // zoom + fit
 *
 *   // Common color palette:
 *   const pal = MapRenderUtils.MAP_PALETTE;
 *   const ORANGE = pal.orange;
 *
 *   // Find root nodes as ID strings (for dagre `roots`):
 *   const roots = MapRenderUtils.findRootNodeIds(State.network);
 *
 *   // Build dagre layout options (identical in process-data + project-data):
 *   return MapRenderUtils.buildDagreLayoutOptions(State.layout, MAP_ID);
 *
 *   const colors = MapRenderUtils.getMapColors({ currentSystem: '#f97316', ... });
 *   canvas.innerHTML = MapRenderUtils.htmlCytoscapeUnavailable();
 *
 *   MapRenderUtils.wireLineageToolbarSharedControls({ mapId, adapter, state, loadMapData, setLayout, ... });
 *
 *   // MapEngine / SharedMapCore legacy pointer menu (.map-context-menu):
 *   MapRenderUtils.showMapContextMenuFromActions({ evt, nodeData, actions });
 *
 *   // SharedMap class: toolbar lineage menu (.interface-map-context-menu):
 *   MapRenderUtils.showInterfaceMapContextMenuFromItems({ evt, nodeData, host, items });
 *
 *   // Cytoscape tap → fixed left/top for context menus (canvas rect + graph pos * zoom + pan):
 *   MapRenderUtils.getFixedPositionFromCyTap({ canvas, network, evt, offsetX: 20, offsetY: 0 });
 *
 * Load order: place BEFORE any module-specific map files (and before map-engine.js).
 */

(function () {
    'use strict';

    // =========================================================================
    // 1. MAP_PALETTE — shared color constants
    // ─────────────────────────────────────────────────────────────────────────
    // These hex values are used consistently across all map modules.
    // Alias semantic names so modules can keep their LOCAL const names:
    //
    //   const ORANGE        = MapRenderUtils.MAP_PALETTE.orange;
    //   const ORANGE_BORDER = MapRenderUtils.MAP_PALETTE.orangeBorder;
    //   …
    // =========================================================================
    var MAP_PALETTE = {
        orange:       '#f97316',  // current / impact system fill
        orangeBorder: '#ea580c',  // current / impact system border
        black:        '#1f2937',  // standard "other system" fill
        blackBorder:  '#374151',  // standard "other system" border
        grey:         '#6b7280',  // linked / secondary system (used by project map)
        greyBorder:   '#4b5563',  // linked / secondary system border
        edgeLine:     '#64748b',  // solid interface / data-flow edge
        edgeDashed:   '#94a3b8',  // dashed edge (interface with zero data-attributes)
        locked:       '#6b7280',  // inaccessible / locked system fill
        lockedBorder: '#4b5563',  // inaccessible / locked system border
        upstream:     '#ef4444',  // upstream highlight
        downstream:   '#22c55e', // downstream highlight
        selected:     '#3b82f6'  // selected / focused node accent
    };

    // =========================================================================
    // 1b. escapeHtml — safe text for templates / overlay panels
    // ─────────────────────────────────────────────────────────────────────────
    function escapeHtml(str) {
        if (str == null || str === '') return '';
        var div = document.createElement('div');
        div.textContent = str;
        return div.innerHTML;
    }

    // =========================================================================
    // 2. getNodeSizes
    // ─────────────────────────────────────────────────────────────────────────
    // Returns node width/height for each map-kind from `InterfaceMapStyles`
    // (configured server-side / globally), falling back to static defaults.
    //
    // All five modules that call `getNodeSizes()` share identical logic;
    // only the fallback object differed (each module listed a subset of keys).
    // This implementation returns ALL keys so every module can use it.
    //
    // @returns {{ system, dataset, glossary, capability, process, project }}
    // =========================================================================
    function getNodeSizes() {
        if (window.InterfaceMapStyles && window.InterfaceMapStyles.nodeSizes) {
            return window.InterfaceMapStyles.nodeSizes;
        }
        return {
            system:     { width: 140, height: 80 },
            dataset:    { width: 140, height: 80 },
            glossary:   { width: 140, height: 80 },
            capability: { width: 140, height: 80 },
            process:    { width: 140, height: 80 },
            project:    { width: 140, height: 80 }
        };
    }

    // =========================================================================
    // 3. getSystemIcon
    // ─────────────────────────────────────────────────────────────────────────
    // Returns a data-URI for the system node icon.
    // Prefers `InterfaceMapIcons.getIconByType`; falls back to an inline SVG
    // (three horizontal database-style bars).
    //
    // Identical implementation appeared in both process-data-map.js and
    // project-data-map.js.
    //
    // @param {boolean} isOrange - True for the "current/impact" (orange) variant
    // @returns {string} data-URI string suitable for Cytoscape `background-image`
    // =========================================================================
    function getSystemIcon(isOrange) {
        if (window.InterfaceMapIcons && typeof window.InterfaceMapIcons.getIconByType === 'function') {
            return window.InterfaceMapIcons.getIconByType('system', !!isOrange);
        }
        var iconColor = '#ffffff';
        var svg = '<svg width="40" height="40" viewBox="0 0 40 40" xmlns="http://www.w3.org/2000/svg">' +
            '<rect x="6" y="4"  width="28" height="6" rx="2" fill="' + iconColor + '" opacity="0.95"/>' +
            '<rect x="6" y="14" width="28" height="6" rx="2" fill="' + iconColor + '" opacity="0.95"/>' +
            '<rect x="6" y="24" width="28" height="6" rx="2" fill="' + iconColor + '" opacity="0.95"/>' +
            '</svg>';
        return 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg);
    }

    // =========================================================================
    // 4. zoomIn / zoomOut
    // ─────────────────────────────────────────────────────────────────────────
    // Zoom the Cytoscape viewport by ×1.2 (in) or ×0.8 (out).
    //
    // @param {Object}  network  - Cytoscape instance
    // @param {boolean} [recenter=false] - If true, call network.center() after zoom.
    //   Pass true for maps that re-fit to the full graph on zoom (system, dataset).
    //   Omit or pass false for maps that keep the current viewport centre
    //   (glossary-data, glossary-relationships, capability).
    // =========================================================================
    function zoomIn(network, recenter) {
        if (!network) return;
        network.zoom(network.zoom() * 1.2);
        if (recenter) network.center();
    }

    function zoomOut(network, recenter) {
        if (!network) return;
        network.zoom(network.zoom() * 0.8);
        if (recenter) network.center();
    }

    // =========================================================================
    // 5. findRootNodeIds
    // ─────────────────────────────────────────────────────────────────────────
    // Return an array of node ID strings for nodes that have no incoming edges
    // (i.e. DAG roots). Falls back to the first node if the graph has cycles.
    //
    // Used as the `roots` value for Cytoscape dagre layouts in capability and
    // glossary maps. For glossary-relationships the caller adds extra logic to
    // prefer the "current glossary" node — that override lives in the module.
    //
    // @param {Object} network - Cytoscape instance
    // @returns {string[]}     - Array of root node ID strings
    // =========================================================================
    function findRootNodeIds(network) {
        if (!network) return [];
        var nodes = network.nodes();
        if (nodes.length === 0) return [];
        var targets = new Set();
        network.edges().forEach(function (edge) { targets.add(edge.target().id()); });
        var roots = nodes.filter(function (node) { return !targets.has(node.id()); });
        return roots.length > 0
            ? roots.map(function (n) { return n.id(); })
            : [nodes[0].id()];
    }

    // =========================================================================
    // 6. buildDagreLayoutOptions
    // ─────────────────────────────────────────────────────────────────────────
    // Build the Cytoscape layout options object for dagre-based maps.
    // Identical implementation in process-data-map.js and project-data-map.js;
    // both read spacing-padding from the shared dropdown API.
    //
    // @param {string} stateLayout - Layout string from module state
    //                               ('left-to-right' | 'top-to-bottom' | 'right-to-left')
    // @param {string} mapId       - The map's DOM/API identifier (e.g. 'processDataMap')
    // @returns {Object} Cytoscape layout config
    // =========================================================================
    function buildDagreLayoutOptions(stateLayout, mapId) {
        var ddApi = window._sharedDropdownApis && window._sharedDropdownApis[mapId];
        var DEFAULT_PAD = 90;
        var sp = (ddApi && typeof ddApi.getSpacingPadding === 'function')
            ? ddApi.getSpacingPadding()
            : DEFAULT_PAD;
        var layout  = stateLayout || 'left-to-right';
        var rankDir = layout === 'top-to-bottom' ? 'TB'
                    : layout === 'right-to-left'  ? 'RL'
                    : 'LR';
        try {
            var dagre = window.cytoscapeDagre || window['cytoscape-dagre'];
            if (dagre && typeof cytoscape !== 'undefined') cytoscape.use(dagre);
        } catch (e) { /* ignore re-register errors */ }
        return {
            name: 'dagre',
            rankDir: rankDir,
            padding: sp,
            nodeDimensionsIncludeLabels: true,
            animate: true,
            animationDuration: 400
        };
    }

    // =========================================================================
    // 7. getMapColors(fallback)
    // =========================================================================
    function getMapColors(fallback) {
        if (window.InterfaceMapStyles && window.InterfaceMapStyles.colors) {
            return window.InterfaceMapStyles.colors;
        }
        return fallback || {};
    }

    // Fallback palette for system lineage map (Interfaces / Data tab) when styles module has no colors.
    var SYSTEM_LINEAGE_MAP_COLOR_FALLBACK = {
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

    /**
     * Build Cytoscape elements array from system/dataset lineage graph { nodes, edges }.
     * @param {{ nodes: Array, edges: Array }} graph
     * @param {{ getMapColors: function(): Object, getNodeIcon: function(string, boolean): string }} deps
     * @returns {Array} cytoscape elements
     */
    function buildSystemLineageCytoscapeElements(graph, deps) {
        if (!graph || !deps || typeof deps.getMapColors !== 'function' || typeof deps.getNodeIcon !== 'function') {
            return [];
        }
        var colors = deps.getMapColors();
        var getNodeIcon = deps.getNodeIcon;
        var elements = [];
        var nodes = graph.nodes || [];
        var edges = graph.edges || [];
        var lockPrefix = '\uD83D\uDD12 ';

        nodes.forEach(function (node) {
            var isCurrent = node.isCurrent;
            var isLocked = node.isLocked;
            var isDataset = node.group === 'dataset';
            var nodeColor;
            var borderColor;
            var icon;
            if (isDataset) {
                nodeColor = isLocked ? colors.lockedSystem : (isCurrent ? colors.currentSystem : colors.otherSystem);
                borderColor = isLocked ? colors.lockedSystem : (isCurrent ? (colors.currentSystemBorder || '#ea580c') : (colors.otherSystemBorder || '#374151'));
                icon = isLocked ? getNodeIcon('locked', false) : getNodeIcon('dataset', isCurrent);
            } else {
                nodeColor = isLocked ? colors.lockedSystem : (isCurrent ? colors.currentSystem : colors.otherSystem);
                borderColor = isLocked ? colors.lockedSystem : (isCurrent ? (colors.currentSystemBorder || '#ea580c') : (colors.otherSystemBorder || '#374151'));
                icon = isLocked ? getNodeIcon('locked', false) : getNodeIcon(node.group || 'system', isCurrent);
            }
            elements.push({
                data: {
                    id: node.id,
                    label: (isLocked ? lockPrefix : '') + (node.label || ''),
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

        var sortedEdges = edges.slice().sort(function (a, b) {
            if (a.dashes && !b.dashes) return -1;
            if (!a.dashes && b.dashes) return 1;
            return 0;
        });

        var currentNodeIds = new Set();
        nodes.forEach(function (n) {
            if (n.isCurrent) currentNodeIds.add(String(n.id));
        });

        sortedEdges.forEach(function (edge) {
            var lineColor = edge.dashes ? '#94a3b8' : '#64748b';
            var isReversed = currentNodeIds.has(String(edge.to)) && !currentNodeIds.has(String(edge.from));
            elements.push({
                data: {
                    id: edge.id,
                    source: isReversed ? edge.to : edge.from,
                    target: isReversed ? edge.from : edge.to,
                    label: edge.label || '',
                    lineStyle: edge.dashes ? 'dashed' : 'solid',
                    lineColor: lineColor,
                    dataAttributes: edge.dataAttributes,
                    lineType: edge.lineType || (edge.dashes ? 'interface' : 'lineage'),
                    reversed: isReversed
                },
                classes: isReversed ? 'reversed-edge' : ''
            });
        });

        return elements;
    }

    // =========================================================================
    // 8. Canvas placeholder HTML (classes in view/shared/map/map.css)
    // =========================================================================
    function htmlMapCanvasMessage(text) {
        var t = text == null ? '' : String(text);
        return '<div class="map-canvas-message">' + t + '</div>';
    }

    function htmlCytoscapeUnavailable() {
        return htmlMapCanvasMessage('Visualization library is not available.');
    }

    function htmlMapLibraryUnavailable() {
        return htmlMapCanvasMessage('Map library not available.');
    }

    function htmlMapLoading() {
        return htmlMapCanvasMessage('Loading map...');
    }

    function htmlMapCanvasCenteredDiagram(message) {
        var m = message == null ? '' : String(message);
        return '<div class="map-canvas-message map-canvas-message--centered">' +
            '<i class="fas fa-project-diagram map-canvas-message__icon"></i>' +
            '<span>' + m + '</span></div>';
    }

    function setMapCanvasHtml(canvas, html) {
        if (canvas && html != null) canvas.innerHTML = html;
    }

    // =========================================================================
    // 9. Process / project data maps — toolbar wiring + overlay *button text*
    // =========================================================================
    // STANDARD_LINEAGE_OVERLAY_LABELS: only maps data-overlay keys → label for the
    // overlay dropdown button span after a selection. It does NOT define which
    // overlays exist; each map’s getMapHtml() + setOverlay/loadOverlayData still
    // own menus, filters, and fetch/render. Other facets (system, glossary, …)
    // keep their own label objects or UI. wireLineageToolbarSharedControls only
    // wires zoom/export/fullscreen/dropdown spacing — not overlay/filter logic.
    var STANDARD_LINEAGE_OVERLAY_LABELS = {
        description: 'Description',
        glossary: 'Glossaries',
        datasets: 'Data Sets',
        dataset: 'Data Sets',
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

    /**
     * SharedMapControls + SharedMapDropdowns for lineage maps using createMapAdapter.
     * @param {{ mapId: string, adapter: Object, state: { network: *, layout: string }, loadMapData: function, setLayout: function, exportAsPng: function, openFullscreen: function, getLegendHtml: function }} options
     */
    /**
     * Export Cytoscape lineage map to PNG (exportMapWithOverlays if available, else blob / data URL fallback).
     */
    function exportLineageMapToPng(opts) {
        var network = opts.network;
        var canvas = opts.canvas;
        var filename = opts.filename || 'map.png';
        var emptyMessage = opts.emptyMessage || 'No map to export.';
        var failMessage = opts.failMessage || 'Could not export the map.';
        var logPrefix = opts.logPrefix || '[LineageMap]';
        if (!network) {
            if (typeof window.showNotification === 'function') window.showNotification(emptyMessage, 'warning');
            else alert(emptyMessage);
            return;
        }
        if (typeof window.exportMapWithOverlays === 'function' && canvas && document.contains(canvas)) {
            window.exportMapWithOverlays(network, canvas, filename);
            return;
        }
        try {
            var png = network.png({ output: 'blob', bg: 'white', full: true });
            var url = URL.createObjectURL(png);
            var a = document.createElement('a');
            a.href = url;
            a.download = filename;
            a.style.display = 'none';
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
            URL.revokeObjectURL(url);
        } catch (e) {
            try {
                var png64 = network.png({ bg: '#ffffff', full: true, scale: 2 });
                var a2 = document.createElement('a');
                a2.download = filename;
                a2.href = png64;
                a2.style.display = 'none';
                document.body.appendChild(a2);
                a2.click();
                document.body.removeChild(a2);
            } catch (e2) {
                console.error(logPrefix + ' Export failed:', e2);
                if (typeof window.showNotification === 'function') window.showNotification(failMessage, 'error');
                else alert(failMessage);
            }
        }
    }

    // =========================================================================
    // attachOrToggleLineageMinimap — lineage navigator (Cytoscape inset)
    // ─────────────────────────────────────────────────────────────────────────
    // Creates or toggles visibility of a `.map-minimap` sibling under `container`
    // (typically the map canvas parent). First open builds a simplified Cy graph
    // from `network.json().elements` with compact node/edge styles.
    //
    // @param {Object} opts
    // @param {HTMLElement} opts.container - Usually canvas.parentElement
    // @param {Object} opts.network       - Main Cytoscape instance
    // @param {string} [opts.minimapCanvasId] - Optional id for inner cy div
    // @param {boolean} [opts.syncViewport] - If true, minimap refits on main viewport
    // @param {Object} [opts.cytoscapeOptions] - Extra keys merged into cytoscape({...})
    // @returns {HTMLElement|null} the .map-minimap root (or null if missing args)
    // =========================================================================
    function attachOrToggleLineageMinimap(opts) {
        if (!opts || !opts.container || !opts.network) return null;
        var container = opts.container;
        var network = opts.network;
        var minimap = container.querySelector('.map-minimap');
        if (minimap) {
            minimap.style.display = minimap.style.display === 'none' ? 'block' : 'none';
            return minimap;
        }
        var pos = window.getComputedStyle(container).position;
        if (pos === 'static' || pos === '') {
            container.style.position = 'relative';
        }
        minimap = document.createElement('div');
        minimap.className = 'map-minimap';
        minimap.style.cssText =
            'position:absolute;' +
            'bottom:1rem;' +
            'right:1rem;' +
            'width:150px;' +
            'height:100px;' +
            'background:var(--card-bg,#ffffff);' +
            'border:1px solid var(--border-color,#e5e7eb);' +
            'border-radius:6px;' +
            'box-shadow:0 4px 6px rgba(0,0,0,0.1);' +
            'z-index:50;' +
            'overflow:hidden;';
        var minimapCanvas = document.createElement('div');
        if (opts.minimapCanvasId) {
            minimapCanvas.id = opts.minimapCanvasId;
        }
        minimapCanvas.style.cssText = 'width:100%;height:100%;';
        minimap.appendChild(minimapCanvas);
        container.appendChild(minimap);

        if (typeof cytoscape === 'undefined') {
            return minimap;
        }
        var elements = network.json().elements;
        var cyOpts = {
            container: minimapCanvas,
            elements: elements,
            style: [
                {
                    selector: 'node',
                    style: {
                        width: 8,
                        height: 8,
                        'background-color': 'data(nodeColor)',
                        label: ''
                    }
                },
                {
                    selector: 'edge',
                    style: {
                        width: 1,
                        'line-color': '#94a3b8'
                    }
                }
            ],
            layout: { name: 'preset' },
            userZoomingEnabled: false,
            userPanningEnabled: false,
            boxSelectionEnabled: false
        };
        if (opts.cytoscapeOptions && typeof opts.cytoscapeOptions === 'object') {
            Object.keys(opts.cytoscapeOptions).forEach(function (k) {
                cyOpts[k] = opts.cytoscapeOptions[k];
            });
        }
        var minimapCy = cytoscape(cyOpts);
        if (opts.syncViewport) {
            network.on('viewport', function () {
                minimapCy.fit();
            });
        }
        return minimap;
    }

    /**
     * Convert a Cytoscape tap position to fixed viewport coordinates for a floating menu.
     * Formula: canvas.getBoundingClientRect() + (evt.position × zoom) + pan + offsets.
     *
     * @param {Object} opts
     * @param {HTMLElement} opts.canvas
     * @param {Object} opts.network — cytoscape instance (pan/zoom)
     * @param {Object} opts.evt — Cytoscape event with position or cyPosition
     * @param {number} [opts.offsetX=20]
     * @param {number} [opts.offsetY=0]
     * @returns {{ left: number, top: number }|null}
     */
    function getFixedPositionFromCyTap(opts) {
        if (!opts || !opts.canvas || !opts.network || !opts.evt) return null;
        var evt = opts.evt;
        var position = evt.position || evt.cyPosition;
        if (!position || position.x == null || position.y == null) return null;
        var rect = opts.canvas.getBoundingClientRect();
        var pan = opts.network.pan();
        var zoom = opts.network.zoom();
        var ox = opts.offsetX !== undefined && opts.offsetX !== null ? opts.offsetX : 20;
        var oy = opts.offsetY !== undefined && opts.offsetY !== null ? opts.offsetY : 0;
        return {
            left: rect.left + position.x * zoom + pan.x + ox,
            top: rect.top + position.y * zoom + pan.y + oy
        };
    }

    /**
     * MapEngine + SharedMapCore (fallback): context menu at pointer using `.map-context-menu`.
     * Removes any existing menu. No-op if actions is missing or empty.
     *
     * @param {Object} opts
     * @param {Object} opts.evt — Cytoscape event (uses originalEvent.clientX/Y)
     * @param {Object} opts.nodeData — passed to each action.callback
     * @param {Array<{label: string, action: function(Object): void}>} opts.actions
     * @returns {HTMLElement|null}
     */
    function showMapContextMenuFromActions(opts) {
        if (!opts || !opts.evt) return null;
        var actions = opts.actions;
        if (!actions || !actions.length) return null;
        var nodeData = opts.nodeData;
        var evt = opts.evt;

        var existing = document.querySelector('.map-context-menu');
        if (existing) existing.remove();

        var oe = evt.originalEvent;
        var left = oe ? oe.clientX : 0;
        var top = oe ? oe.clientY : 0;

        var menu = document.createElement('div');
        menu.className = 'map-context-menu';
        menu.style.cssText =
            'position:fixed;' +
            'left:' + left + 'px;' +
            'top:' + top + 'px;' +
            'background:#fff;border:1px solid #e5e7eb;border-radius:4px;' +
            'box-shadow:0 4px 6px rgba(0,0,0,0.1);z-index:10000;min-width:200px;';

        actions.forEach(function (action) {
            if (!action) return;
            var item = document.createElement('div');
            item.className = 'map-context-menu-item';
            item.textContent = action.label;
            item.style.cssText = 'padding:8px 16px;cursor:pointer;border-bottom:1px solid #f3f4f6;';
            item.addEventListener('mouseenter', function () { item.style.background = '#f9fafb'; });
            item.addEventListener('mouseleave', function () { item.style.background = '#fff'; });
            item.addEventListener('click', function () {
                if (typeof action.action === 'function') action.action(nodeData);
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
        return menu;
    }

    /**
     * SharedMap (unified-map-controller): `.interface-map-context-menu` with header, separators, Font Awesome rows.
     * Position uses evt.originalEvent client coordinates (fallback 100,100). Calls host.hideContextMenu() before show
     * and when clicking outside or after an item action.
     *
     * @param {Object} opts
     * @param {Object} opts.evt — Cytoscape event
     * @param {Object} opts.nodeData
     * @param {Object} opts.host — map instance; must implement hideContextMenu(); passed to item.action(nodeData, host)
     * @param {Array<{type?: string, label?: string, icon?: string, action?: function(Object, Object): void}>} opts.items
     * @returns {HTMLElement|null}
     */
    function showInterfaceMapContextMenuFromItems(opts) {
        if (!opts || !opts.evt || !opts.host) return null;
        var evt = opts.evt;
        var nodeData = opts.nodeData;
        var host = opts.host;
        var items = opts.items;
        if (!items || !items.length) return null;

        function hide() {
            if (typeof host.hideContextMenu === 'function') {
                host.hideContextMenu();
            } else {
                var el = document.querySelector('.interface-map-context-menu');
                if (el) el.remove();
            }
        }

        hide();

        var menu = document.createElement('div');
        menu.className = 'interface-map-context-menu';
        var oe = evt.originalEvent;
        var left = (oe && oe.clientX != null) ? oe.clientX : 100;
        var top = (oe && oe.clientY != null) ? oe.clientY : 100;
        menu.style.cssText = 'position:fixed;left:' + left + 'px;top:' + top + 'px;z-index:10000;';

        var header = document.createElement('div');
        header.className = 'context-menu-header';
        header.textContent = nodeData ? (nodeData.label || nodeData.id || '') : '';
        menu.appendChild(header);

        items.forEach(function (item) {
            if (!item) return;
            if (item.type === 'separator') {
                var sep = document.createElement('div');
                sep.className = 'context-menu-separator';
                menu.appendChild(sep);
                return;
            }
            var mi = document.createElement('div');
            mi.className = 'context-menu-item';
            mi.innerHTML = '<i class="fas ' + (item.icon || 'fa-circle') + '"></i> ' + (item.label || '');
            mi.addEventListener('click', function () {
                if (typeof item.action === 'function') item.action(nodeData, host);
                hide();
            });
            menu.appendChild(mi);
        });

        document.body.appendChild(menu);
        setTimeout(function () {
            var close = function (e) {
                if (!menu.contains(e.target)) {
                    hide();
                    document.removeEventListener('click', close);
                }
            };
            document.addEventListener('click', close);
        }, 0);
        return menu;
    }

    function wireLineageToolbarSharedControls(options) {
        if (!options || !options.mapId || !options.adapter || !options.state) return;
        var mapId = options.mapId;
        var adapter = options.adapter;
        var state = options.state;
        function lineageSetLayout(dir) {
            state.layout = dir;
            var ls = document.getElementById(mapId + 'LayoutSelect');
            if (ls) ls.value = dir;
            if (typeof options.setLayout === 'function') options.setLayout(dir);
        }
        if (typeof window.SharedMapControls === 'function') {
            window.SharedMapControls({
                mapId: mapId,
                mapInstance: {
                    zoomIn: function () { adapter.zoomIn(); },
                    zoomOut: function () { adapter.zoomOut(); },
                    redrawMap: function () { options.loadMapData(); },
                    resetMap: function () { if (state.network) state.network.fit(undefined, 50); },
                    exportAsPng: options.exportAsPng,
                    openFullscreen: options.openFullscreen,
                    getLegendHtml: options.getLegendHtml,
                    setLayout: lineageSetLayout,
                    toggleLabels: function (show) {
                        if (state.network) state.network.edges().style('label', show ? 'data(label)' : '');
                    }
                }
            });
        }
        if (typeof window.SharedMapDropdowns === 'function') {
            window.SharedMapDropdowns({
                mapId: mapId,
                getNetwork: function () { return state.network; },
                setLayout: lineageSetLayout,
                getCanvas: function () { return document.getElementById(mapId + 'Canvas'); }
            });
        }
    }

    // =========================================================================
    // Public API
    // =========================================================================
    window.MapRenderUtils = {
        MAP_PALETTE:            MAP_PALETTE,
        escapeHtml:             escapeHtml,
        getNodeSizes:           getNodeSizes,
        getSystemIcon:          getSystemIcon,
        zoomIn:                 zoomIn,
        zoomOut:                zoomOut,
        findRootNodeIds:        findRootNodeIds,
        buildDagreLayoutOptions: buildDagreLayoutOptions,
        getMapColors:           getMapColors,
        SYSTEM_LINEAGE_MAP_COLOR_FALLBACK: SYSTEM_LINEAGE_MAP_COLOR_FALLBACK,
        buildSystemLineageCytoscapeElements: buildSystemLineageCytoscapeElements,
        htmlMapCanvasMessage:   htmlMapCanvasMessage,
        htmlCytoscapeUnavailable: htmlCytoscapeUnavailable,
        htmlMapLibraryUnavailable: htmlMapLibraryUnavailable,
        htmlMapLoading:         htmlMapLoading,
        htmlMapCanvasCenteredDiagram: htmlMapCanvasCenteredDiagram,
        setMapCanvasHtml:       setMapCanvasHtml,
        STANDARD_LINEAGE_OVERLAY_LABELS: STANDARD_LINEAGE_OVERLAY_LABELS,
        wireLineageToolbarSharedControls: wireLineageToolbarSharedControls,
        exportLineageMapToPng: exportLineageMapToPng,
        attachOrToggleLineageMinimap: attachOrToggleLineageMinimap,
        showMapContextMenuFromActions: showMapContextMenuFromActions,
        showInterfaceMapContextMenuFromItems: showInterfaceMapContextMenuFromItems,
        getFixedPositionFromCyTap: getFixedPositionFromCyTap
    };

})();
