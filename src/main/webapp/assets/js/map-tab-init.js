/**
 * Fullscreen map page: /view/full_map/… only (live URL or ?tb= sessionStorage snapshot).
 * Legacy /map-tab.html?mapState= redirects to /view/full_map/other/0?tb=…
 *
 * Live API engines (map matches toolbar; map type / hops reload data):
 *   • mapTabKind "system-interfaces" — SystemInterfacesMap
 *   • mapTabKind "process-data" — ProcessDataMap (full_map.html includes scripts; map-tab may load on demand)
 * Other mapTabKind values use the frozen snapshot + generic toolbar handlers until wired.
 */
(function () {
    'use strict';

    var PREFIX = typeof window.MAP_TAB_STORAGE_PREFIX === 'string' ? window.MAP_TAB_STORAGE_PREFIX : 'mapTabState_';

    function mtDbgWarn() {
        if (typeof window !== 'undefined' && window.BUDG_DEBUG_MAP_TAB === true) {
            console.warn.apply(console, arguments);
        }
    }

    /** Load optional map modules for map-tab (order matters). */
    function loadScriptsSequentially(urls, callback) {
        var i = 0;
        function next() {
            if (i >= urls.length) {
                if (typeof callback === 'function') callback();
                return;
            }
            var url = urls[i++];
            var attr = 'data-map-tab-dynamic';
            if (document.querySelector('script[' + attr + '="' + url.replace(/"/g, '') + '"]')) {
                next();
                return;
            }
            var s = document.createElement('script');
            s.src = url;
            s.setAttribute(attr, url);
            s.onload = next;
            s.onerror = function () {
                console.error('[map-tab] script load failed', url);
                next();
            };
            document.head.appendChild(s);
        }
        next();
    }

    function queryMapStateId() {
        var q = window.location.search || '';
        var m = q.match(/[?&]mapState=([^&]+)/);
        return m ? decodeURIComponent(m[1].replace(/\+/g, ' ')) : null;
    }

    function isFullMapPath() {
        return (window.location.pathname || '').indexOf('/full_map/') >= 0;
    }

    function parseFullMapPathSegments() {
        var p = window.location.pathname || '';
        var idx = p.indexOf('/full_map/');
        if (idx < 0) return null;
        var rest = p.slice(idx + '/full_map/'.length);
        var parts = rest.split('/').filter(function (s) { return s.length > 0; });
        if (parts.length < 2) return null;
        var facet = parts[0];
        var mapVariant = null;
        var entityId;
        if (parts.length === 2) {
            entityId = decodeURIComponent(parts[1]);
        } else {
            mapVariant = parts[1];
            entityId = decodeURIComponent(parts[2]);
        }
        return { facet: facet, entityId: entityId, mapVariant: mapVariant };
    }

    /** Build state for /view/full_map/... — ?tb= loads session snapshot; else live system/process. */
    function buildStateFromFullMapPath() {
        if (!isFullMapPath()) return null;
        var q = new URLSearchParams(window.location.search || '');
        var tb = q.get('tb');
        if (tb) {
            var preTb = typeof window.MAP_TAB_STORAGE_PREFIX === 'string' ? window.MAP_TAB_STORAGE_PREFIX : 'mapTabState_';
            var rawTb = sessionStorage.getItem(preTb + tb);
            if (!rawTb) {
                return {
                    v: 2,
                    mapTabKind: 'unsupported-full-map',
                    title: 'Map',
                    toolbarHtml: '<p class="map-full-map-unsupported">This link has expired. Open fullscreen again from the application.</p>'
                };
            }
            try {
                return JSON.parse(rawTb);
            } catch (eTb) {
                console.error('[map-tab] full_map tb parse', eTb);
                return null;
            }
        }

        var segs = parseFullMapPathSegments();
        if (!segs) return null;
        var layout = q.get('layout') || 'top-to-bottom';
        var mapTypeQ = q.get('mapType') || '';
        var mapParam = q.get('map') || '';
        var hops = parseInt(q.get('hops') || '15', 10);
        if (isNaN(hops)) hops = 15;
        var showLabels = q.get('showLabels') !== '0';
        var title = (q.get('title') || 'Map').replace(/</g, '\u003c');

        var state = {
            v: 2,
            liveFromUrl: true,
            title: title,
            entityId: segs.entityId,
            nodes: [],
            edges: [],
            layout: layout,
            mapType: 'system-lineage',
            toolbarHtml: '',
            activeFilters: {},
            selectValues: {},
            activeOverlay: 'none',
            overlayColumns: {},
            overlayPanelsHtml: '',
            showLabels: showLabels,
            hopsCount: hops,
            spacing: q.get('spacing') || 'normal',
            edgeStyle: q.get('edgeStyle') || 'direct',
            style: [],
            legendHtml: '',
            exportFilename: 'map.png'
        };

        if (segs.facet === 'system') {
            state.mapTabKind = 'system-interfaces';
            if (mapParam === 'data') {
                state.mapType = 'dataset-lineage';
            } else if (mapTypeQ === 'dataset-lineage' || mapTypeQ === 'system-lineage') {
                state.mapType = mapTypeQ;
            } else {
                state.mapType = 'system-lineage';
            }
            if (typeof window.SharedMapHTML === 'function') {
                state.toolbarHtml = window.SharedMapHTML({
                    mapId: 'interfaceMap',
                    defaultMapType: state.mapType,
                    includeMapBody: false
                });
            }
            state.selectValues = {
                interfaceMapTypeSelect: state.mapType,
                interfaceMapLayoutSelect: layout
            };
            state.exportFilename = 'system-lineage-map-' + segs.entityId + '.png';
            return state;
        }
        if (segs.facet === 'process') {
            state.mapTabKind = 'process-data';
            state.mapType = (mapTypeQ === 'dataset-lineage' || mapTypeQ === 'system-lineage') ? mapTypeQ : 'system-lineage';
            if (window.ProcessDataMap && typeof ProcessDataMap.getToolbarHtmlForFullscreen === 'function') {
                state.toolbarHtml = ProcessDataMap.getToolbarHtmlForFullscreen();
            }
            state.selectValues = {
                processDataMapTypeSelect: state.mapType,
                processDataMapLayoutSelect: layout
            };
            state.exportFilename = 'process-data-map-' + segs.entityId + '.png';
            return state;
        }

        state.mapTabKind = 'unsupported-full-map';
        state.title = 'Map — open from the app';
        state.toolbarHtml = '<p class="map-full-map-unsupported">This URL must include a valid <code>tb=</code> session key from the app, or use a live system/process link. Use the <strong>fullscreen</strong> button on the map in the application.</p>';
        return state;
    }

    function ensureDagre() {
        try {
            if (typeof cytoscape === 'undefined') return;
            var dagre = window.cytoscapeDagre || window['cytoscape-dagre'];
            if (dagre && !window._mapTabDagreRegistered) {
                cytoscape.use(dagre);
                window._mapTabDagreRegistered = true;
            }
        } catch (e) { /* no-op */ }
    }

    /** Must use window.dagre: bare `dagre` throws ReferenceError in strict mode and breaks layouts. */
    function hasDagre() {
        return !!window._mapTabDagreRegistered
            || (typeof window.dagre !== 'undefined' && !!window.dagre);
    }

    function initMapTab(state) {
        if (!state || (state.v !== 1 && state.v !== 2)) {
            document.body.innerHTML = '<p style="padding:2rem;font-family:sans-serif;">Invalid or missing map state. Open the map from the application and use “Open in new tab” again.</p>';
            return;
        }

        document.title = state.title || 'Map';

        if (state.mapTabKind === 'unsupported-full-map') {
            var thu = document.getElementById('mapTabToolbarHost');
            if (thu) thu.innerHTML = '<div class="map-section map-section--flush-edges">' + (state.toolbarHtml || '') + '</div>';
            var b0 = document.getElementById('mapTabBody');
            if (b0) {
                b0.innerHTML = '<p style="position:absolute;left:1rem;top:1rem;color:#6b7280;font-family:sans-serif;">Use the in-app map fullscreen for this entity type until this route is implemented.</p>';
            }
            return;
        }

        var toolbarHost = document.getElementById('mapTabToolbarHost');
        if (toolbarHost && state.toolbarHtml) {
            toolbarHost.innerHTML = '<div class="map-section map-section--flush-edges">' + state.toolbarHtml + '</div>';
        }

        /** Toolbar root for scoped queries (avoids broken [id*=] selectors and stray matches). */
        var mapToolRoot = document.getElementById('mapTabToolbarHost');

        /** Must be set before applyMapTypeMenusVisibility (toolbar menus use data-map-type). */
        var currentLayout = state.layout || 'top-to-bottom';
        var currentMapType = state.mapType || 'system-lineage';
        var currentSpacing = state.spacing || 'normal';
        var currentEdge = state.edgeStyle || 'direct';
        var exportFilename = (state.exportFilename || 'map.png').replace(/"/g, '');
        var selectValues = (state.selectValues && typeof state.selectValues === 'object') ? state.selectValues : {};

        function isLayoutSelectId(id) {
            if (!id) return false;
            if (id === 'mapLayoutSelect') return true;
            return id.slice(-12) === 'LayoutSelect';
        }
        /** Matches toolbar pattern `${mapId}TypeSelect` and maps.html `mapTypeSelect`; not relationship UIs. */
        function isMapTypeSelectId(id) {
            if (!id) return false;
            if (id === 'mapTypeSelect') return true;
            if (id.length < 10 || id.slice(-10) !== 'TypeSelect') return false;
            if (id === 'relationshipTypeSelect') return false;
            if (isLayoutSelectId(id)) return false;
            return true;
        }

        Object.keys(selectValues).forEach(function (sid) {
            if (isMapTypeSelectId(sid)) {
                currentMapType = selectValues[sid] || currentMapType;
            }
            if (isLayoutSelectId(sid)) {
                currentLayout = selectValues[sid] || currentLayout;
            }
        });

        function root() {
            return mapToolRoot || document.documentElement;
        }

        function normalizeClonedMenus() {
            if (!mapToolRoot) return;
            mapToolRoot.querySelectorAll('.map-overlay-menu, .map-filter-menu').forEach(function (m) {
                if (m.classList.contains('map-overlay-columns-menu')) return;
                m.style.removeProperty('display');
                m.classList.remove('open');
            });
        }
        normalizeClonedMenus();

        function applyMapTypeMenusVisibility() {
            if (!mapToolRoot) return;
            var mt = String(currentMapType || '');
            mapToolRoot.querySelectorAll('.map-overlay-menu[data-map-type], .map-filter-menu[data-map-type]').forEach(function (m) {
                if (m.classList.contains('map-overlay-columns-menu')) return;
                var dmt = m.getAttribute('data-map-type') || '';
                m.style.display = dmt === mt ? '' : 'none';
            });
        }
        applyMapTypeMenusVisibility();

        var legendEl = document.getElementById('mapTabLegend');
        if (legendEl && state.legendHtml) {
            legendEl.innerHTML = state.legendHtml;
            legendEl.classList.remove('map-js-hidden');
        }

        var elements = (state.nodes || []).concat(state.edges || []);
        var allowLiveEmpty = state.liveFromUrl === true && state.entityId != null
            && (state.mapTabKind === 'system-interfaces' || state.mapTabKind === 'process-data');
        if (!elements.length && !allowLiveEmpty) {
            document.getElementById('mapTabBody').innerHTML += '<p style="position:absolute;left:1rem;top:1rem;">No graph elements in saved state.</p>';
            return;
        }

        var cy = null;
        /** Graph driven by SystemInterfacesMap (live API). */
        var mapTabLineageEngineActive = false;
        /** Graph driven by ProcessDataMap (live API). */
        var mapTabProcessDataActive = false;
        var mapTabEntityId = state.entityId != null ? state.entityId : null;
        var mapTabOverlayCtl = null;
        var mapTabOverlayShim = null;
        var savedActiveOverlay = state.activeOverlay && state.activeOverlay !== 'none' ? state.activeOverlay : 'none';
        var liveActiveOverlay = savedActiveOverlay;
        var liveOverlayColumns = {};
        if (state.overlayColumns && typeof state.overlayColumns === 'object') {
            Object.keys(state.overlayColumns).forEach(function (k) {
                var arr = state.overlayColumns[k];
                liveOverlayColumns[k] = Array.isArray(arr) ? arr.slice() : [];
            });
        }

        function interfaceMapDdApi() {
            if (state.mapTabKind !== 'system-interfaces' || !window._sharedDropdownApis) return null;
            var a = window._sharedDropdownApis.interfaceMap;
            return a && typeof a.getSpacingFactor === 'function' ? a : null;
        }
        function sp() {
            var dd = interfaceMapDdApi();
            if (dd) return dd.getSpacingFactor();
            return currentSpacing === 'compact' ? 1.2 : currentSpacing === 'spacey' ? 3.5 : 2.2;
        }
        function pad() {
            var dd = interfaceMapDdApi();
            if (dd) return dd.getSpacingPadding();
            return currentSpacing === 'compact' ? 40 : currentSpacing === 'spacey' ? 150 : 90;
        }
        function curve() {
            var dd = interfaceMapDdApi();
            if (dd && typeof dd.getCurveStyle === 'function') return dd.getCurveStyle();
            switch (currentEdge) {
                case 'angle': return 'segments';
                case 'square': return 'taxi';
                case 'loop': return 'unbundled-bezier';
                default: return 'bezier';
            }
        }
        /** Organic / cose multiplier: keep in sync with spacing dropdown (including SharedMapDropdowns in tab). */
        function spacingMultKind() {
            var dd = interfaceMapDdApi();
            var sk = dd && typeof dd.getSpacing === 'function' ? dd.getSpacing() : currentSpacing;
            return sk === 'compact' ? 'compact' : (sk === 'spacey' ? 'spacey' : 'normal');
        }

        /**
         * Breadthfirst roots: collection when cy exists, else Cytoscape selector string (needed before cy is assigned).
         */
        function breadthfirstRootsOption() {
            if (cy && window.MapRenderUtils && typeof window.MapRenderUtils.findRootNodeIds === 'function') {
                var ids = window.MapRenderUtils.findRootNodeIds(cy);
                if (ids.length) {
                    return cy.nodes().filter(function (n) { return ids.indexOf(n.id()) >= 0; });
                }
            }
            var targets = Object.create(null);
            var nodeIds = [];
            (elements || []).forEach(function (item) {
                var d = item && item.data;
                if (!d) return;
                if (d.source != null && d.target != null) {
                    targets[String(d.target)] = true;
                } else if (d.id != null) {
                    nodeIds.push(String(d.id));
                }
            });
            if (!nodeIds.length) return undefined;
            var roots = nodeIds.filter(function (id) { return !targets[id]; });
            var pick = roots.length ? roots : [nodeIds[0]];
            return pick.map(function (id) {
                return 'node[id="' + id.replace(/\\/g, '\\\\').replace(/"/g, '\\"') + '"]';
            }).join(', ');
        }

        /**
         * Same choices as system interface map: SharedMapStyles / InterfaceMapStyles.getLayoutConfig
         * plus tab spacing (pad/sp) and organic scaling (spMult).
         */
        function buildLayout(dir) {
            var p = pad();
            var s = sp();
            var sk = spacingMultKind();
            var spMult = sk === 'compact' ? 0.6 : (sk === 'spacey' ? 1.8 : 1.0);
            var a = { animate: true, animationDuration: 500, nodeDimensionsIncludeLabels: true };
            var dagreOk = hasDagre();
            var styles = window.InterfaceMapStyles || window.SharedMapStyles;
            var rootsOpt = breadthfirstRootsOption();

            if (styles && typeof styles.getLayoutConfig === 'function') {
                var base = styles.getLayoutConfig(dir);
                var layoutConfig = base ? Object.assign({}, base) : null;
                if (!layoutConfig) return { name: 'preset' };
                if (layoutConfig.spacingFactor != null) layoutConfig.spacingFactor = s;
                if (layoutConfig.padding != null) layoutConfig.padding = p;

                if (layoutConfig.name === 'cose') {
                    return Object.assign({}, layoutConfig, {
                        padding: p,
                        nodeRepulsion: Math.round((layoutConfig.nodeRepulsion != null ? layoutConfig.nodeRepulsion : 5000) * spMult),
                        idealEdgeLength: Math.round((layoutConfig.idealEdgeLength != null ? layoutConfig.idealEdgeLength : 150) * spMult)
                    });
                }
                if (layoutConfig.name === 'dagre' && dagreOk) {
                    return Object.assign({}, layoutConfig, a);
                }
                if (layoutConfig.name === 'dagre' && !dagreOk) {
                    return Object.assign({
                        name: 'breadthfirst',
                        directed: true,
                        padding: p,
                        spacingFactor: s,
                        animate: true,
                        animationDuration: 500,
                        nodeDimensionsIncludeLabels: true
                    }, rootsOpt ? { roots: rootsOpt } : {});
                }
                if (layoutConfig.name === 'breadthfirst') {
                    return Object.assign({}, layoutConfig, rootsOpt ? { roots: rootsOpt } : {});
                }
                return layoutConfig;
            }

            switch (dir) {
                case 'top-to-bottom':
                    return dagreOk ? Object.assign({ name: 'dagre', rankDir: 'TB', padding: p, spacingFactor: s }, a)
                        : Object.assign({
                            name: 'breadthfirst',
                            directed: true,
                            padding: p,
                            spacingFactor: s,
                            animate: true,
                            animationDuration: 500,
                            nodeDimensionsIncludeLabels: true
                        }, rootsOpt ? { roots: rootsOpt } : {});
                case 'left-to-right':
                    return dagreOk ? Object.assign({ name: 'dagre', rankDir: 'LR', padding: p, spacingFactor: s }, a)
                        : Object.assign({
                            name: 'breadthfirst',
                            directed: true,
                            padding: p,
                            spacingFactor: s,
                            animate: true,
                            animationDuration: 500,
                            nodeDimensionsIncludeLabels: true
                        }, rootsOpt ? { roots: rootsOpt } : {});
                case 'right-to-left':
                    return dagreOk ? Object.assign({ name: 'dagre', rankDir: 'RL', padding: p, spacingFactor: s }, a)
                        : Object.assign({
                            name: 'breadthfirst',
                            directed: true,
                            padding: p,
                            spacingFactor: s,
                            animate: true,
                            animationDuration: 500,
                            nodeDimensionsIncludeLabels: true,
                            transform: function (node, pos) { return { x: -pos.x, y: pos.y }; }
                        }, rootsOpt ? { roots: rootsOpt } : {});
                case 'force':
                case 'organic':
                    return {
                        name: 'cose',
                        padding: p,
                        animate: true,
                        animationDuration: 500,
                        nodeRepulsion: Math.round(5000 * spMult),
                        idealEdgeLength: Math.round(150 * spMult),
                        edgeElasticity: 0.45,
                        nestingFactor: 0.1,
                        gravity: 0.25,
                        numIter: 1500,
                        initialEnergyOnIncremental: 0.3
                    };
                default:
                    return { name: 'preset' };
            }
        }

        /** Matches SharedMapHTML: system-lineage → System, dataset-lineage → Dataset, etc. */
        function pascalSuffixFromMapType(mt) {
            return String(mt || '').replace(/-lineage$/, '').split('-').map(function (w) {
                return w ? w.charAt(0).toUpperCase() + w.slice(1) : '';
            }).join('');
        }

        function getActiveOverlayMenu() {
            var r = root();
            var mt = String(currentMapType || '');
            var esc = mt.replace(/\\/g, '\\\\').replace(/"/g, '\\"');
            var m = r.querySelector('.map-overlay-menu[data-map-type="' + esc + '"]');
            if (!m) {
                var all = r.querySelectorAll('.map-overlay-menu');
                if (all.length === 1) {
                    m = all[0];
                } else if (all.length > 1) {
                    var suff = pascalSuffixFromMapType(mt);
                    for (var i = 0; i < all.length; i++) {
                        var el = all[i];
                        var id = el.id || '';
                        if (suff && id.indexOf('OverlayMenu') !== -1 && id.indexOf(suff) !== -1) {
                            m = el;
                            break;
                        }
                    }
                    if (!m) {
                        for (var j = 0; j < all.length; j++) {
                            if (all[j].getAttribute('data-map-type') === mt) {
                                m = all[j];
                                break;
                            }
                        }
                    }
                }
            }
            return m;
        }
        function getActiveFilterMenu() {
            var r = root();
            var mt = String(currentMapType || '');
            var esc = mt.replace(/\\/g, '\\\\').replace(/"/g, '\\"');
            var m = r.querySelector('.map-filter-menu:not(.map-overlay-columns-menu)[data-map-type="' + esc + '"]');
            if (!m) {
                var all = r.querySelectorAll('.map-filter-menu:not(.map-overlay-columns-menu)');
                if (all.length === 1) {
                    m = all[0];
                } else if (all.length > 1) {
                    var suff = pascalSuffixFromMapType(mt);
                    for (var i = 0; i < all.length; i++) {
                        var el = all[i];
                        var id = el.id || '';
                        if (suff && id.indexOf('FilterMenu') !== -1 && id.indexOf(suff) !== -1) {
                            m = el;
                            break;
                        }
                    }
                    if (!m) {
                        for (var j = 0; j < all.length; j++) {
                            if (all[j].getAttribute('data-map-type') === mt) {
                                m = all[j];
                                break;
                            }
                        }
                    }
                }
            }
            return m;
        }

        function setOverlayButtonLabel(overlayType) {
            var oBtn = root().querySelector('.map-select-btn[id*="OverlayBtn"]');
            if (!oBtn) return;
            var spn = oBtn.querySelector('span');
            if (!spn) return;
            if (!overlayType || overlayType === 'none') {
                spn.textContent = 'None';
                return;
            }
            var menu = getActiveOverlayMenu();
            var item = menu ? menu.querySelector('.overlay-menu-item[data-overlay="' + overlayType + '"]') : null;
            spn.textContent = item ? item.textContent.trim() : overlayType;
        }

        function syncShimFromTab() {
            if (!mapTabOverlayShim) return;
            mapTabOverlayShim.network = cy;
            mapTabOverlayShim.canvas = document.getElementById('mapTabCanvas');
            mapTabOverlayShim.systemId = mapTabEntityId;
            mapTabOverlayShim.mapType = currentMapType;
            mapTabOverlayShim.overlay = liveActiveOverlay;
            mapTabOverlayShim.overlayColumnsByType = liveOverlayColumns;
        }

        function ensureTabOverlayController() {
            if (state.mapTabKind !== 'system-interfaces' || !cy || !mapTabEntityId || !window.createSystemLineageOverlayController) return null;
            if (!mapTabOverlayShim) {
                mapTabOverlayShim = {
                    network: cy,
                    canvas: document.getElementById('mapTabCanvas'),
                    systemId: mapTabEntityId,
                    mapType: currentMapType,
                    overlay: 'none',
                    overlayData: null,
                    overlayColumnsByType: liveOverlayColumns,
                    linkedDatasets: new Map(),
                    interfacesData: [],
                    datasetRelationships: [],
                    dataFlowData: [],
                    connectedSystems: new Map(),
                    inaccessibleSystems: new Set(),
                    attributeRelationships: []
                };
                mapTabOverlayCtl = window.createSystemLineageOverlayController({
                    state: mapTabOverlayShim,
                    showLoading: function () {},
                    hideLoading: function () {},
                    checkSystemHasDataAttributes: function () { return true; },
                    extractGlossaryNameFromAttr: function (attr) {
                        return window.MapGraphUtils && typeof window.MapGraphUtils.extractGlossaryNameFromAttr === 'function'
                            ? window.MapGraphUtils.extractGlossaryNameFromAttr(attr) : null;
                    },
                    logPrefix: '[map-tab-overlay]'
                });
            }
            syncShimFromTab();
            return mapTabOverlayCtl;
        }

        function clearOverlayPanels() {
            var wrap = document.getElementById('mapTabOverlayContainer');
            if (wrap) wrap.innerHTML = '';
            if (mapTabOverlayCtl) mapTabOverlayCtl.clearOverlayPanels();
        }

        function injectOverlayPanelsHtml(html) {
            var wrap = document.getElementById('mapTabOverlayContainer');
            if (!wrap || !html) return;
            wrap.innerHTML = html;
            positionAllOverlayPanels();
        }

        function positionFsOverlayPanel(panel, node) {
            var wrap = document.getElementById('mapTabOverlayContainer');
            if (!wrap || !node || !node.length || !cy) return;
            var rp = node.renderedPosition();
            var pw = panel.offsetWidth || 260;
            var ph = panel.offsetHeight || 160;
            var nh = (typeof node.renderedHeight === 'function' ? node.renderedHeight() : 36) || 36;
            var left = rp.x - pw / 2;
            var top = rp.y - nh / 2 - ph - 12;
            var maxLeft = Math.max(0, (wrap.clientWidth || 0) - pw);
            var maxTop = Math.max(0, (wrap.clientHeight || 0) - ph);
            left = Math.max(0, Math.min(maxLeft, left));
            top = Math.max(0, Math.min(maxTop, top));
            panel.style.position = 'absolute';
            panel.style.left = left + 'px';
            panel.style.top = top + 'px';
        }

        function positionAllOverlayPanels() {
            if (!cy) return;
            var wrap = document.getElementById('mapTabOverlayContainer');
            if (!wrap) return;
            wrap.querySelectorAll('.map-node-overlay-panel').forEach(function (panel) {
                var nodeId = panel.getAttribute('data-node-id');
                var node = nodeId ? cy.getElementById(nodeId) : null;
                if (node && node.length) {
                    var isVisible = node.style('display') !== 'none' && !node.hasClass('filter-hidden');
                    panel.style.display = isVisible ? '' : 'none';
                    if (isVisible) positionFsOverlayPanel(panel, node);
                } else {
                    panel.style.display = 'none';
                }
            });
        }

        function closeAllMenus() {
            var r = root();
            r.querySelectorAll('.map-btn-dropdown-menu.show,.map-layout-rich-menu.show').forEach(function (m) { m.classList.remove('show'); });
            r.querySelectorAll('.map-overlay-menu.open,.map-filter-menu.open').forEach(function (m) { m.classList.remove('open'); });
            r.querySelectorAll('.map-overlay-columns-menu').forEach(function (m) { m.style.display = 'none'; });
        }

        function reloadSystemInterfacesGraphForMapTab() {
            if (state.mapTabKind !== 'system-interfaces' || mapTabEntityId == null) return false;
            if (!window.SystemInterfacesMap || typeof SystemInterfacesMap.adoptMapTabCanvas !== 'function') return false;
            var canvas = document.getElementById('mapTabCanvas');
            if (!canvas) return false;

            closeAllMenus();
            liveActiveOverlay = 'none';
            var oBtn0 = root().querySelector('.map-select-btn[id*="OverlayBtn"]');
            if (oBtn0) {
                var sp0 = oBtn0.querySelector('span');
                if (sp0) sp0.textContent = 'None';
            }
            root().querySelectorAll('.map-overlay-menu .overlay-menu-item').forEach(function (i) {
                i.classList.remove('active');
            });
            clearOverlayPanels();

            try {
                if (cy) cy.destroy();
            } catch (eD) { /* no-op */ }
            cy = null;
            canvas.innerHTML = '';

            var hopsRead = state.hopsCount != null ? state.hopsCount : 15;
            root().querySelectorAll('input.map-hops-input,input[type=number]').forEach(function (inp) {
                if (!inp.id || inp.id.indexOf('Hops') < 0) return;
                var hv = parseInt(inp.value, 10);
                if (!isNaN(hv)) hopsRead = hv;
            });
            var showLab = state.showLabels !== false;
            var tlBtn = root().querySelector('.map-toolbar-btn[id*="ToggleLabels"]');
            if (tlBtn) showLab = tlBtn.classList.contains('active');

            if (typeof SystemInterfacesMap.prepareMapTabEngineState === 'function') {
                SystemInterfacesMap.prepareMapTabEngineState({
                    layout: currentLayout,
                    hopsCount: hopsRead,
                    showInterfaceLabels: showLab,
                    mapType: currentMapType
                });
            }

            SystemInterfacesMap.setMapTabAfterRender(function (net) {
                cy = net;
                mapTabLineageEngineActive = true;
                applyMapTypeMenusVisibility();
                updateFilterButtonSummary();
                syncShimFromTab();
                setOverlayButtonLabel('none');
                if (cy) {
                    ensureFilterHiddenStyles(cy);
                    cy.off('zoom pan');
                    cy.off('drag', 'node');
                    cy.on('zoom pan', positionAllOverlayPanels);
                    cy.on('drag', 'node', positionAllOverlayPanels);
                    try { cy.edges().style('curve-style', curve()); } catch (eC) { /* no-op */ }
                    ensureDatasetFilterOptionsFromGraph();
                    applyAllDynamicFilters();
                    try { cy.layout(buildLayout(currentLayout)).run(); } catch (eL2) { /* no-op */ }
                    cy.fit(undefined, 50);
                    positionAllOverlayPanels();
                }
            });

            SystemInterfacesMap.adoptMapTabCanvas(mapTabEntityId, canvas);
            SystemInterfacesMap.setMapType(currentMapType);
            return true;
        }

        function startProcessDataLiveTab() {
            var urls = ['/view/shared/map/process-data-facet-graph.js', '/view/process/process-data-map.js'];
            function bindAfterProcessDataRender(net) {
                cy = net;
                mapTabProcessDataActive = true;
                applyMapTypeMenusVisibility();
                updateFilterButtonSummary();
                syncShimFromTab();
                setOverlayButtonLabel('none');
                if (cy) {
                    ensureFilterHiddenStyles(cy);
                    cy.off('zoom pan');
                    cy.off('drag', 'node');
                    cy.on('zoom pan', positionAllOverlayPanels);
                    cy.on('drag', 'node', positionAllOverlayPanels);
                    try { cy.edges().style('curve-style', curve()); } catch (eC) { /* no-op */ }
                    applyAllDynamicFilters();
                    try { cy.layout(buildLayout(currentLayout)).run(); } catch (eL2) { /* no-op */ }
                    cy.fit(undefined, 50);
                    positionAllOverlayPanels();
                }
            }
            function runAdopt() {
                if (!window.ProcessDataMap) {
                    mtDbgWarn('[map-tab] ProcessDataMap not available');
                    if (cy) {
                        setTimeout(function () {
                            try { cy.layout(buildLayout(currentLayout)).run(); } catch (eL) { mtDbgWarn('[map-tab] final layout', eL); }
                            cy.fit(undefined, 50);
                            positionAllOverlayPanels();
                        }, 120);
                    }
                    return;
                }
                try {
                    if (cy) cy.destroy();
                } catch (e0) { /* no-op */ }
                cy = null;
                var canvas = document.getElementById('mapTabCanvas');
                if (!canvas) return;
                canvas.innerHTML = '';
                ProcessDataMap.setMapTabAfterRender(bindAfterProcessDataRender);
                var hopsRead = state.hopsCount != null ? state.hopsCount : 15;
                root().querySelectorAll('input.map-hops-input,input[type=number]').forEach(function (inp) {
                    if (!inp.id || inp.id.indexOf('Hops') < 0) return;
                    var hv = parseInt(inp.value, 10);
                    if (!isNaN(hv)) hopsRead = hv;
                });
                var showLab = state.showLabels !== false;
                var tlBtn2 = root().querySelector('.map-toolbar-btn[id*="ToggleLabels"]');
                if (tlBtn2) showLab = tlBtn2.classList.contains('active');
                ProcessDataMap.applyMapTabToolbarPrefs({
                    mapType: currentMapType,
                    layout: currentLayout,
                    hopsCount: hopsRead,
                    showEdgeLabels: showLab
                });
                ProcessDataMap.adoptForMapTab(mapTabEntityId, canvas);
                ProcessDataMap.loadMapData();
            }
            if (window.ProcessDataMap) runAdopt();
            else loadScriptsSequentially(urls, runAdopt);
        }

        function switchMenusForMapTypeLocal() {
            closeAllMenus();
            applyMapTypeMenusVisibility();
            if (reloadSystemInterfacesGraphForMapTab()) return;
            if (mapTabProcessDataActive && state.mapTabKind === 'process-data' && window.ProcessDataMap && typeof ProcessDataMap.setMapType === 'function') {
                ProcessDataMap.setMapType(currentMapType);
            }
            liveActiveOverlay = 'none';
            var oBtn = root().querySelector('.map-select-btn[id*="OverlayBtn"]');
            if (oBtn) {
                var spn = oBtn.querySelector('span');
                if (spn) spn.textContent = 'None';
            }
            root().querySelectorAll('.map-overlay-menu .overlay-menu-item').forEach(function (i) {
                i.classList.remove('active');
            });
            clearOverlayPanels();
            applyAllDynamicFilters();
            updateFilterButtonSummary();
            syncShimFromTab();
            if (cy) cy.layout(buildLayout(currentLayout)).run();
        }

        function applyLayout(dir) {
            dir = String(dir == null ? '' : dir).trim();
            if (!dir) return;
            currentLayout = dir;
            if (mapTabProcessDataActive && state.mapTabKind === 'process-data' && window.ProcessDataMap && typeof ProcessDataMap.setLayout === 'function') {
                ProcessDataMap.setLayout(dir);
            } else if (mapTabLineageEngineActive && window.SystemInterfacesMap && typeof SystemInterfacesMap.setLayout === 'function') {
                SystemInterfacesMap.setLayout(dir);
            } else if (cy) {
                try { cy.layout(buildLayout(dir)).run(); } catch (e) { mtDbgWarn('[map-tab] layout', e); }
            }
            root().querySelectorAll('select').forEach(function (sel) {
                if (sel.id && isLayoutSelectId(sel.id)) sel.value = dir;
            });
            root().querySelectorAll('[data-layout]').forEach(function (it) {
                it.classList.toggle('active', it.dataset.layout === dir);
            });
            var dirEsc = dir.replace(/\\/g, '\\\\').replace(/"/g, '\\"');
            var rt = root().querySelector('[data-layout="' + dirEsc + '"] .map-layout-rich-title');
            if (rt) {
                var btn = root().querySelector('.map-select-btn[id*="LayoutBtn"] span');
                if (!btn) btn = root().querySelector('button[id*="LayoutBtn"] span');
                if (!btn) btn = root().querySelector('[id*="LayoutBtnText"]');
                if (btn) btn.textContent = rt.textContent;
            }
        }

        /** System / interface toolbar: refresh “All selected (n)” on the filter dropdown button. */
        function updateFilterButtonSummary() {
            var fm = getActiveFilterMenu();
            var txt = root().querySelector('[id*="FilterBtnText"]');
            if (!fm || !txt) return;
            var boxes = fm.querySelectorAll('input[type="checkbox"]');
            var total = 0;
            var checked = 0;
            for (var i = 0; i < boxes.length; i++) {
                var row = boxes[i].closest('.map-filter-option');
                if (row && row.style.display === 'none') continue;
                total++;
                if (boxes[i].checked) checked++;
            }
            if (total === 0) {
                txt.textContent = 'No filters';
                return;
            }
            txt.textContent = checked === total ? 'All selected (' + total + ')' : (checked + ' of ' + total);
        }

        /** Populate overlay column checkboxes (requires map-ui.js / window.OverlayColumns). */
        function populateOverlayColumnsForTab(colMenu) {
            if (!colMenu || !window.OverlayColumns || typeof window.OverlayColumns.getOverlayColumns !== 'function') return;
            var optContainer = colMenu.querySelector('[id*="OverlayColumnsOptions"], .map-filter-options-container');
            if (!optContainer) return;
            var overlayType = liveActiveOverlay;
            if (!overlayType || overlayType === 'none') {
                optContainer.innerHTML = '<div class="map-overlay-columns-empty">Select an overlay first.</div>';
                return;
            }
            var columns = window.OverlayColumns.getOverlayColumns(overlayType);
            var selectedIds = liveOverlayColumns[overlayType] && liveOverlayColumns[overlayType].length
                ? liveOverlayColumns[overlayType].slice()
                : (typeof window.OverlayColumns.getDefaultOverlayColumnIds === 'function'
                    ? window.OverlayColumns.getDefaultOverlayColumnIds(overlayType)
                    : []);
            if (!columns || !columns.length) {
                optContainer.innerHTML = '<div class="map-overlay-columns-empty">No columns for this overlay.</div>';
                return;
            }
            optContainer.innerHTML = '';
            var ns = 'mtcol_' + Date.now().toString(36) + '_';
            columns.forEach(function (col) {
                var div = document.createElement('div');
                div.className = 'map-filter-option';
                var input = document.createElement('input');
                input.type = 'checkbox';
                input.id = ns + overlayType + '_' + col.id;
                input.checked = selectedIds.indexOf(col.id) !== -1;
                input.dataset.columnId = col.id;
                var label = document.createElement('label');
                label.htmlFor = input.id;
                label.textContent = col.label || col.id;
                div.appendChild(input);
                div.appendChild(label);
                input.addEventListener('change', function () {
                    var ids = Array.prototype.map.call(optContainer.querySelectorAll('input:checked'), function (el) {
                        return el.dataset.columnId;
                    });
                    liveOverlayColumns[overlayType] = ids;
                    if (state.mapTabKind === 'process-data' && window.ProcessDataMap && typeof ProcessDataMap.setOverlayColumns === 'function') {
                        ProcessDataMap.setOverlayColumns(overlayType, ids);
                    }
                    if (mapTabOverlayCtl && mapTabOverlayShim && liveActiveOverlay === overlayType && liveActiveOverlay !== 'none') {
                        mapTabOverlayShim.overlayColumnsByType = liveOverlayColumns;
                        mapTabOverlayCtl.loadOverlayData(overlayType, { quiet: true });
                    }
                });
                optContainer.appendChild(div);
            });
        }

        function edgeLabelFromData(edge) {
            var d = edge.data() || {};
            var v = d.label;
            return v != null && String(v).length ? String(v) : '';
        }

        function applyLabelsFromState(show) {
            if (!cy) return;
            var on = !!show;
            cy.edges().forEach(function (edge) {
                edge.style('label', on ? edgeLabelFromData(edge) : '');
            });
            var tb = root().querySelector('.map-toolbar-btn[id*="ToggleLabels"]');
            if (tb) tb.classList.toggle('active', on);
        }

        function filterOptionsScope() {
            var fm = getActiveFilterMenu();
            return fm || root();
        }

        /**
         * LINKS checkboxes: search whole toolbar (#mapTabToolbarHost). Menus without data-map-type
         * (e.g. maps.html #mapFilterMenu) made getActiveFilterMenu() pick the wrong scope; ids like
         * mapFilterSystemInterfaces do not match [id^="filter"].
         */
        function resolveLinkFilterCheckbox(linkKey) {
            var host = root();
            if (!host || !host.querySelector) return null;
            var byData = host.querySelector('input[type="checkbox"][data-filter-link="' + linkKey + '"]');
            if (byData) return byData;
            if (linkKey === 'systemInterfaces') {
                return host.querySelector('#filterSystemInterfaces')
                    || host.querySelector('#mapFilterSystemInterfaces')
                    || host.querySelector('[id^="filter"][id$="systemInterfaces"]')
                    || host.querySelector('input[type="checkbox"][id*="systemInterfaces"]');
            }
            return host.querySelector('#filterDataAttributeLinks')
                || host.querySelector('#mapFilterDataAttributeLinks')
                || host.querySelector('[id^="filter"][id$="dataAttributeLinks"]')
                || host.querySelector('input[type="checkbox"][id*="dataAttributeLinks"]');
        }

        function ensureLinksFilterEdgeStyles(cyInst) {
            if (!cyInst || cyInst.scratch('_linksFilterStyled')) return;
            try {
                cyInst.style()
                    .selector('edge.link-filter-hidden')
                    .style({
                        opacity: 0,
                        'line-opacity': 0,
                        'target-arrow-opacity': 0,
                        'source-arrow-opacity': 0,
                        'events': 'no',
                        'text-opacity': 0
                    })
                    .update();
                cyInst.scratch('_linksFilterStyled', true);
            } catch (e) { /* no-op */ }
        }

        /** System + dataset lineage: hide edges from LINKS toggles (stylesheet class; reliable vs display bypass). */
        function applyLinkFiltersInTab() {
            if (!cy) return;
            if (state.mapTabKind === 'process-data' && window.ProcessDataMap && ProcessDataMap.cy === cy) {
                return;
            }
            if (currentMapType !== 'system-lineage' && currentMapType !== 'dataset-lineage') {
                cy.batch(function () {
                    cy.edges().removeClass('link-filter-hidden');
                });
                return;
            }
            ensureLinksFilterEdgeStyles(cy);
            var si = resolveLinkFilterCheckbox('systemInterfaces');
            var da = resolveLinkFilterCheckbox('dataAttributeLinks');
            if (!si && !da) return;
            var showInt = !si || si.checked;
            var showAttr = !da || da.checked;
            cy.batch(function () {
                cy.edges().forEach(function (e) {
                    var d = e.data() || {};
                    var lt = String(d.lineType || '').toLowerCase();
                    var ls = String(d.lineStyle || '').toLowerCase();
                    var kind = 'attribute';
                    if (lt === 'interface' || lt === 'system-interface' || lt === 'interfaces' || ls === 'dashed' || d.dashes === true) {
                        kind = 'interface';
                    }
                    var hide = (kind === 'interface' && !showInt) || (kind === 'attribute' && !showAttr);
                    if (hide) e.addClass('link-filter-hidden');
                    else e.removeClass('link-filter-hidden');
                });
            });
            positionAllOverlayPanels();
        }

        function firstFilterContainer(scope, selectors) {
            for (var i = 0; i < selectors.length; i++) {
                var el = scope.querySelector(selectors[i]);
                if (el) return el;
            }
            return null;
        }

        function collectCheckedFromContainer(container) {
            var arr = [];
            if (!container) return arr;
            container.querySelectorAll('input[type="checkbox"]:checked').forEach(function (cb) {
                var v = cb.getAttribute('data-filter-value') || cb.dataset.filterValue;
                if (!v && cb.value != null && String(cb.value).length) v = String(cb.value);
                if (v) arr.push(v);
            });
            return arr;
        }

        function collectSystemNodeFiltersFromDom(scopeOpt) {
            var scope = scopeOpt || filterOptionsScope();
            var clsSel = ['#filterClassificationOptions', '#mapFilterClassificationOptions', '[id$="filterClassificationOptions"]'];
            var typSel = ['#filterTypeOptions', '#mapFilterTypeOptions', '[id$="filterTypeOptions"]'];
            var lifeSel = ['#filterLifecycleOptions', '#mapFilterLifecycleOptions', '[id$="filterLifecycleOptions"]'];
            return {
                classifications: collectCheckedFromContainer(firstFilterContainer(scope, clsSel)),
                types: collectCheckedFromContainer(firstFilterContainer(scope, typSel)),
                lifecycles: collectCheckedFromContainer(firstFilterContainer(scope, lifeSel))
            };
        }

        function collectDatasetNodeFiltersFromDom(scopeOpt) {
            var scope = scopeOpt || filterOptionsScope();
            var tEl = scope.querySelector('#datasetFilterTypeOptions') || scope.querySelector('[id$="datasetFilterTypeOptions"]');
            var lEl = scope.querySelector('#datasetFilterLifecycleOptions') || scope.querySelector('[id$="datasetFilterLifecycleOptions"]');
            return {
                types: collectCheckedFromContainer(tEl),
                lifecycles: collectCheckedFromContainer(lEl)
            };
        }

        /** Insight maps.html: same semantics as maps.js applyNodeFilters (meta.raw + filter-hidden). */
        function ensureFilterHiddenStyles(cyInst) {
            if (!cyInst) return;
            try {
                cyInst.style()
                    .selector('node.filter-hidden')
                    .style({ 'opacity': 0, 'events': 'no', 'text-opacity': 0 })
                    .selector('edge.filter-hidden')
                    .style({ 'opacity': 0, 'events': 'no' })
                    .update();
            } catch (e) { /* no-op */ }
        }

        function applyInsightMapNodeFiltersInTab() {
            if (!cy) return;
            var menu = root().querySelector('#mapFilterMenu');
            if (!menu || !menu.querySelector('input[data-filter-type]')) return;

            function filterSetFor(type) {
                var all = menu.querySelectorAll('input[data-filter-type="' + type + '"]');
                if (!all.length) return null;
                var checked = menu.querySelectorAll('input[data-filter-type="' + type + '"]:checked');
                if (checked.length === all.length) return null;
                var values = new Set();
                checked.forEach(function (n) {
                    var v = n.getAttribute('data-filter-value');
                    if (v) values.add(v);
                });
                if (values.size === 0) return null;
                var s = {};
                values.forEach(function (v) { s[v] = true; });
                return s;
            }

            var cf = filterSetFor('classifications');
            var tf = filterSetFor('types');
            var lf = filterSetFor('lifecycles');

            cy.nodes().forEach(function (node) {
                var meta = node.data('meta') || {};
                var raw = meta.raw || {};
                var status = String(raw.Status || raw.status || meta.status || '').trim();
                var typeVal = String(raw.Type || raw.type || meta.type || '').trim();
                var lifecycle = String(raw.Lifecycle || raw.lifecycle || meta.lifecycle || '').trim();
                var hide = false;
                if (cf && status && !cf[status]) hide = true;
                if (tf && typeVal && !tf[typeVal]) hide = true;
                if (lf && lifecycle && !lf[lifecycle]) hide = true;
                if (hide) node.addClass('filter-hidden');
                else node.removeClass('filter-hidden');
            });
            cy.edges().forEach(function (edge) {
                if (edge.source().hasClass('filter-hidden') || edge.target().hasClass('filter-hidden')) {
                    edge.addClass('filter-hidden');
                } else {
                    edge.removeClass('filter-hidden');
                }
            });
            positionAllOverlayPanels();
        }

        function applyNodeFiltersInTab() {
            if (!cy) return;
            if (root().querySelector('#mapFilterMenu input[data-filter-type]')) {
                applyInsightMapNodeFiltersInTab();
                return;
            }
            /* Live process map applies node filters inside ProcessDataMap (adapter); avoid double-filtering with MapGraphUtils. */
            if (state.mapTabKind === 'process-data' && window.ProcessDataMap && ProcessDataMap.cy === cy) {
                return;
            }
            if (!window.MapGraphUtils) return;

            var mt = String(currentMapType || '');
            if (mt === 'dataset-lineage') {
                var dsMenu = getActiveFilterMenu();
                var dsScope = dsMenu || root();
                var hasDataset = !!(dsScope.querySelector('#datasetFilterTypeOptions') || dsScope.querySelector('#datasetFilterLifecycleOptions')
                    || dsScope.querySelector('[id$="datasetFilterTypeOptions"]') || dsScope.querySelector('[id$="datasetFilterLifecycleOptions"]'));
                if (hasDataset && typeof window.MapGraphUtils.applyDatasetNodeFilters === 'function') {
                    window.MapGraphUtils.applyDatasetNodeFilters(cy, collectDatasetNodeFiltersFromDom(dsScope), {
                        updateOverlayPositions: positionAllOverlayPanels
                    });
                }
                return;
            }
            if (mt === 'system-lineage') {
                var sysMenu = getActiveFilterMenu();
                var sysScope = sysMenu || root();
                var hasSystem = !!(firstFilterContainer(sysScope, ['#filterClassificationOptions', '#mapFilterClassificationOptions', '[id$="filterClassificationOptions"]'])
                    || firstFilterContainer(sysScope, ['#filterTypeOptions', '#mapFilterTypeOptions', '[id$="filterTypeOptions"]'])
                    || firstFilterContainer(sysScope, ['#filterLifecycleOptions', '#mapFilterLifecycleOptions', '[id$="filterLifecycleOptions"]']));
                if (hasSystem && typeof window.MapGraphUtils.applySystemNodeFilters === 'function') {
                    window.MapGraphUtils.applySystemNodeFilters(cy, collectSystemNodeFiltersFromDom(sysScope), {
                        filtersInitialized: false,
                        updateOverlayPositions: positionAllOverlayPanels
                    });
                }
                return;
            }

            var scope = filterOptionsScope();
            var hasSystem = !!(firstFilterContainer(scope, ['#filterClassificationOptions', '#mapFilterClassificationOptions', '[id$="filterClassificationOptions"]'])
                || firstFilterContainer(scope, ['#filterTypeOptions', '#mapFilterTypeOptions', '[id$="filterTypeOptions"]'])
                || firstFilterContainer(scope, ['#filterLifecycleOptions', '#mapFilterLifecycleOptions', '[id$="filterLifecycleOptions"]']));
            var hasDataset = !!(scope.querySelector('#datasetFilterTypeOptions') || scope.querySelector('#datasetFilterLifecycleOptions')
                || scope.querySelector('[id$="datasetFilterTypeOptions"]') || scope.querySelector('[id$="datasetFilterLifecycleOptions"]'));

            if (hasSystem && typeof window.MapGraphUtils.applySystemNodeFilters === 'function') {
                window.MapGraphUtils.applySystemNodeFilters(cy, collectSystemNodeFiltersFromDom(scope), {
                    filtersInitialized: false,
                    updateOverlayPositions: positionAllOverlayPanels
                });
            }
            if (hasDataset && typeof window.MapGraphUtils.applyDatasetNodeFilters === 'function') {
                window.MapGraphUtils.applyDatasetNodeFilters(cy, collectDatasetNodeFiltersFromDom(scope), {
                    updateOverlayPositions: positionAllOverlayPanels
                });
            }
        }

        function applyAllDynamicFilters() {
            applyLinkFiltersInTab();
            applyNodeFiltersInTab();
            updateFilterButtonSummary();
        }

        function mapTabDatasetFilterAttrEsc(s) {
            return String(s == null ? '' : s).replace(/\\/g, '\\\\').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
        }
        function mapTabDatasetFilterLabelEsc(s) {
            return String(s == null ? '' : s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/"/g, '&quot;');
        }

        /**
         * Dataset lineage builds emit this on the window; system.js listens — map-tab must too or filter menus stay empty.
         */
        function populateDatasetFilterOptionsInTab(detail) {
            if (!mapToolRoot || !detail) return;
            var types = detail.types || [];
            var lifecycles = detail.lifecycles || [];
            var typeContainer = mapToolRoot.querySelector('#datasetFilterTypeOptions') || mapToolRoot.querySelector('[id$="datasetFilterTypeOptions"]');
            var lifecycleContainer = mapToolRoot.querySelector('#datasetFilterLifecycleOptions') || mapToolRoot.querySelector('[id$="datasetFilterLifecycleOptions"]');
            if (typeContainer && types.length > 0) {
                typeContainer.innerHTML = types.map(function (type, idx) {
                    var a = mapTabDatasetFilterAttrEsc(type);
                    var lab = mapTabDatasetFilterLabelEsc(type);
                    return '<div class="map-filter-option"><input type="checkbox" id="mapTabDsType_' + idx + '" data-filter-type="type" data-filter-value="' + a + '" checked><label for="mapTabDsType_' + idx + '">' + lab + '</label></div>';
                }).join('');
            } else if (typeContainer) {
                typeContainer.innerHTML = '<div class="map-filter-option" style="color: var(--text-muted, #6b7280); font-size: 0.75rem; padding: 0.25rem 0.75rem;">No types available</div>';
            }
            if (lifecycleContainer && lifecycles.length > 0) {
                lifecycleContainer.innerHTML = lifecycles.map(function (lc, idx) {
                    var a = mapTabDatasetFilterAttrEsc(lc);
                    var lab = mapTabDatasetFilterLabelEsc(lc);
                    return '<div class="map-filter-option"><input type="checkbox" id="mapTabDsLc_' + idx + '" data-filter-type="lifecycle" data-filter-value="' + a + '" checked><label for="mapTabDsLc_' + idx + '">' + lab + '</label></div>';
                }).join('');
            } else if (lifecycleContainer) {
                lifecycleContainer.innerHTML = '<div class="map-filter-option" style="color: var(--text-muted, #6b7280); font-size: 0.75rem; padding: 0.25rem 0.75rem;">No lifecycles available</div>';
            }
            updateFilterButtonSummary();
            if (cy && String(currentMapType) === 'dataset-lineage') {
                applyAllDynamicFilters();
            }
        }

        /** system-lineage-graph dispatches this; mirrors system.js dynamic filter rows for fullscreen. */
        function populateSystemLineageDynamicFiltersInTab(detail) {
            if (!mapToolRoot || !detail || state.mapTabKind !== 'system-interfaces') return;
            var classifications = detail.classifications || [];
            var types = detail.types || [];
            var lifecycles = detail.lifecycles || [];
            /* Do not call setNodeFilters with empty triple — it sets filtersInitialized and hides all non-current nodes. */
            if (!classifications.length && !types.length && !lifecycles.length) {
                updateFilterButtonSummary();
                applyLinkFiltersInTab();
                if (cy) applyNodeFiltersInTab();
                return;
            }

            function fillBySuffix(suffix, arr, dataType) {
                var c = mapToolRoot.querySelector('#' + suffix) || mapToolRoot.querySelector('[id$="' + suffix + '"]');
                if (!c || !arr.length) return;
                c.innerHTML = arr.map(function (val, idx) {
                    var a = mapTabDatasetFilterAttrEsc(val);
                    var lab = mapTabDatasetFilterLabelEsc(val);
                    return '<div class="map-filter-option"><input type="checkbox" id="mapTabSysFlt_' + dataType + '_' + idx + '" data-filter-type="' + dataType + '" data-filter-value="' + a + '" checked><label for="mapTabSysFlt_' + dataType + '_' + idx + '">' + lab + '</label></div>';
                }).join('');
            }

            fillBySuffix('filterClassificationOptions', classifications, 'classification');
            fillBySuffix('filterTypeOptions', types, 'type');
            fillBySuffix('filterLifecycleOptions', lifecycles, 'lifecycle');

            updateFilterButtonSummary();
            if (window.SystemInterfacesMap && typeof SystemInterfacesMap.setNodeFilters === 'function') {
                var sysScope = getActiveFilterMenu() || root();
                SystemInterfacesMap.setNodeFilters(collectSystemNodeFiltersFromDom(sysScope));
            }
            applyLinkFiltersInTab();
            if (cy) applyNodeFiltersInTab();
        }

        function ensureDatasetFilterOptionsFromGraph() {
            if (String(currentMapType) !== 'dataset-lineage' || !cy || !mapToolRoot) return;
            var typeContainer = mapToolRoot.querySelector('#datasetFilterTypeOptions') || mapToolRoot.querySelector('[id$="datasetFilterTypeOptions"]');
            if (!typeContainer || typeContainer.querySelector('input[type="checkbox"]')) return;
            var types = {};
            var lifes = {};
            cy.nodes().forEach(function (n) {
                var m = n.data('meta') || {};
                var tn = String(m.typeName || m.type || '').trim();
                var ln = String(m.lifecycleName || m.lifecycle || '').trim();
                if (tn) types[tn] = true;
                if (ln) lifes[ln] = true;
            });
            var tArr = Object.keys(types).sort();
            var lArr = Object.keys(lifes).sort();
            if (!tArr.length && !lArr.length) return;
            populateDatasetFilterOptionsInTab({ types: tArr, lifecycles: lArr });
        }

        window.addEventListener('datasetMapFilterOptionsUpdated', function (ev) {
            var d = ev && ev.detail;
            if (!d || (state.mapTabKind !== 'system-interfaces' && state.mapTabKind !== 'process-data')) return;
            populateDatasetFilterOptionsInTab(d);
        });

        window.addEventListener('systemMapFilterOptionsUpdated', function (ev) {
            var d = ev && ev.detail;
            if (!d || state.mapTabKind !== 'system-interfaces') return;
            populateSystemLineageDynamicFiltersInTab(d);
        });

        /**
         * Wire spacing / edge dropdowns inside wrappers (supports mapSpacing vs mapSpacingBtn).
         */
        function wireSpacingEdgeDropdowns() {
            if (!mapToolRoot) return;
            if (state.mapTabKind === 'system-interfaces') return;
            if (state.mapTabKind === 'process-data') return;
            mapToolRoot.querySelectorAll('.map-btn-dropdown-wrapper').forEach(function (wrap) {
                if (wrap.dataset.mapTabDropdownWired) return;
                var menu = wrap.querySelector('.map-btn-dropdown-menu');
                var btn = wrap.querySelector('button.map-toolbar-btn-sm, button[type="button"]');
                if (!menu || !btn) return;
                var hasSpacing = !!menu.querySelector('[data-spacing]');
                var hasEdge = !!menu.querySelector('[data-edge-style]');
                if (!hasSpacing && !hasEdge) return;
                wrap.dataset.mapTabDropdownWired = '1';
                btn.addEventListener('click', function (ev) {
                    ev.stopPropagation();
                    var wasOpen = menu.classList.contains('show');
                    mapToolRoot.querySelectorAll('.map-btn-dropdown-menu.show').forEach(function (m) { m.classList.remove('show'); });
                    if (!wasOpen) menu.classList.add('show');
                });
                menu.addEventListener('click', function (ev) {
                    var item = ev.target.closest('.map-btn-dropdown-item');
                    if (!item) return;
                    if (hasSpacing && item.dataset.spacing) {
                        currentSpacing = item.dataset.spacing;
                        menu.querySelectorAll('.map-btn-dropdown-item[data-spacing]').forEach(function (it) {
                            it.classList.toggle('active', it.dataset.spacing === currentSpacing);
                        });
                        if (cy) cy.layout(buildLayout(currentLayout)).run();
                    }
                    if (hasEdge && item.dataset.edgeStyle) {
                        currentEdge = item.dataset.edgeStyle;
                        menu.querySelectorAll('.map-btn-dropdown-item[data-edge-style]').forEach(function (it) {
                            it.classList.toggle('active', it.dataset.edgeStyle === currentEdge);
                        });
                        if (cy) cy.edges().style('curve-style', curve());
                        if (currentEdge === 'top-down') applyLayout('top-to-bottom');
                        else if (currentEdge === 'left-right') applyLayout('left-to-right');
                    }
                    menu.classList.remove('show');
                });
            });
        }

        function fsToolbarBtn(t) {
            return t && t.closest ? t.closest('.map-toolbar-btn') : null;
        }
        function fsBtnId(b, s) {
            return b && b.id && b.id.indexOf(s) >= 0;
        }

        ensureDagre();
        try {
            var fsLayout = buildLayout(currentLayout);
            cy = cytoscape({
                container: document.getElementById('mapTabCanvas'),
                elements: elements,
                style: state.style || [],
                layout: fsLayout,
                minZoom: 0.1,
                maxZoom: 5
            });
            cy.edges().style('curve-style', curve());
        } catch (err) {
            console.error('[map-tab] Cytoscape init failed', err);
            return;
        }

        if (toolbarHost && toolbarHost.querySelector('#mapFilterMenu input[data-filter-type]')) {
            ensureFilterHiddenStyles(cy);
        }

        function dispatchUiChange(el) {
            if (!el) return;
            try {
                el.dispatchEvent(new Event('change', { bubbles: true }));
            } catch (e0) { /* IE */ }
        }

        /** Filter-menu checkboxes: must run before applyAllDynamicFilters reads DOM. */
        if (mapToolRoot) {
            mapToolRoot.addEventListener('change', function (e) {
                var el = e.target;
                if (el.type !== 'checkbox' || !el.closest || !el.closest('.map-filter-menu')) return;
                var inDsType = el.closest('#datasetFilterTypeOptions') || el.closest('[id$="datasetFilterTypeOptions"]');
                var inDsLife = el.closest('#datasetFilterLifecycleOptions') || el.closest('[id$="datasetFilterLifecycleOptions"]');
                var inSysNodeFilters = el.closest('#filterClassificationOptions') || el.closest('[id$="filterClassificationOptions"]')
                    || el.closest('#filterTypeOptions') || el.closest('[id$="filterTypeOptions"]')
                    || el.closest('#filterLifecycleOptions') || el.closest('[id$="filterLifecycleOptions"]');
                if (mapTabLineageEngineActive && state.mapTabKind === 'system-interfaces' && window.SystemInterfacesMap) {
                    if (String(currentMapType) === 'dataset-lineage' && (inDsType || inDsLife) && typeof SystemInterfacesMap.setDatasetNodeFilters === 'function') {
                        var selTypes = [];
                        var selLife = [];
                        mapToolRoot.querySelectorAll('#datasetFilterTypeOptions input:checked, [id$="datasetFilterTypeOptions"] input:checked').forEach(function (cb) {
                            var v = cb.getAttribute('data-filter-value') || cb.dataset.filterValue || cb.value;
                            if (v) selTypes.push(String(v));
                        });
                        mapToolRoot.querySelectorAll('#datasetFilterLifecycleOptions input:checked, [id$="datasetFilterLifecycleOptions"] input:checked').forEach(function (cb) {
                            var v = cb.getAttribute('data-filter-value') || cb.dataset.filterValue || cb.value;
                            if (v) selLife.push(String(v));
                        });
                        SystemInterfacesMap.setDatasetNodeFilters({ types: selTypes, lifecycles: selLife });
                        applyLinkFiltersInTab();
                        updateFilterButtonSummary();
                        return;
                    }
                    if (String(currentMapType) === 'system-lineage' && inSysNodeFilters && typeof SystemInterfacesMap.setNodeFilters === 'function') {
                        var sysScope = getActiveFilterMenu() || root();
                        SystemInterfacesMap.setNodeFilters(collectSystemNodeFiltersFromDom(sysScope));
                        applyLinkFiltersInTab();
                        updateFilterButtonSummary();
                        return;
                    }
                }
                applyAllDynamicFilters();
            });
        }

        if (state.activeFilters && typeof state.activeFilters === 'object') {
            Object.keys(state.activeFilters).forEach(function (id) {
                var el = null;
                if (mapToolRoot) {
                    try {
                        el = mapToolRoot.querySelector('[id="' + String(id).replace(/\\/g, '\\\\').replace(/"/g, '\\"') + '"]');
                    } catch (e2) { /* no-op */ }
                }
                if (!el) el = document.getElementById(id);
                if (el && el.type === 'checkbox') {
                    el.checked = !!state.activeFilters[id];
                    dispatchUiChange(el);
                }
            });
        }

        root().querySelectorAll('select').forEach(function (sel) {
            if (!sel.id) return;
            var isMapType = isMapTypeSelectId(sel.id);
            var isLayout = isLayoutSelectId(sel.id);
            if (selectValues[sel.id] != null && selectValues[sel.id] !== '') {
                try {
                    sel.value = selectValues[sel.id];
                } catch (e3) { /* invalid option */ }
                if (isMapType) {
                    currentMapType = sel.value;
                    applyMapTypeMenusVisibility();
                } else if (isLayout) {
                    dispatchUiChange(sel);
                } else {
                    dispatchUiChange(sel);
                }
                return;
            }
            if (isLayout) {
                sel.value = currentLayout;
                dispatchUiChange(sel);
            }
            if (isMapType) {
                sel.value = currentMapType;
                applyMapTypeMenusVisibility();
            }
        });
        root().querySelectorAll('input.map-hops-input,input[type=number]').forEach(function (inp) {
            if (!inp.id || inp.id.indexOf('Hops') < 0) return;
            inp.value = String(state.hopsCount != null ? state.hopsCount : 15);
            dispatchUiChange(inp);
        });

        root().querySelectorAll('.map-overlay-menu .overlay-menu-item').forEach(function (i) { i.classList.remove('active'); });
        var ao = savedActiveOverlay;
        if (ao !== 'none') {
            var om = getActiveOverlayMenu();
            var oit = om ? om.querySelector('.overlay-menu-item[data-overlay="' + ao + '"]') : null;
            if (oit) oit.classList.add('active');
        }
        setOverlayButtonLabel(ao);
        if (ao && ao !== 'none' && mapToolRoot) {
            var activeItem = mapToolRoot.querySelector('.overlay-menu-item[data-overlay="' + String(ao).replace(/\\/g, '\\\\').replace(/"/g, '\\"') + '"]');
            if (activeItem) activeItem.classList.add('active');
        }

        var ctlInit = ensureTabOverlayController();
        if (ctlInit && savedActiveOverlay && savedActiveOverlay !== 'none') {
            clearOverlayPanels();
            liveActiveOverlay = savedActiveOverlay;
            syncShimFromTab();
            ctlInit.loadOverlayData(savedActiveOverlay, { quiet: true });
        } else if (state.overlayPanelsHtml && String(state.overlayPanelsHtml).trim()) {
            injectOverlayPanelsHtml(state.overlayPanelsHtml);
        }

        applyLabelsFromState(state.showLabels !== false);
        root().querySelectorAll('.map-toolbar-btn[id*="ToggleLabels"]').forEach(function (tb) {
            tb.classList.toggle('active', state.showLabels !== false);
        });

        root().querySelectorAll('[data-layout]').forEach(function (it) {
            it.classList.toggle('active', it.dataset.layout === currentLayout);
        });

        root().querySelectorAll('.map-btn-dropdown-menu [data-spacing]').forEach(function (it) {
            it.classList.toggle('active', it.dataset.spacing === currentSpacing);
        });
        root().querySelectorAll('.map-btn-dropdown-menu [data-edge-style]').forEach(function (it) {
            it.classList.toggle('active', it.dataset.edgeStyle === currentEdge);
        });

        if (state.mapTabKind === 'system-interfaces' && mapToolRoot && typeof window.SharedMapDropdowns === 'function') {
            if (mapToolRoot.dataset.interfaceMapDropdownsWired !== '1') {
                mapToolRoot.dataset.interfaceMapDropdownsWired = '1';
                window.SharedMapDropdowns({
                    mapId: 'interfaceMap',
                    mapRoot: mapToolRoot,
                    getNetwork: function () {
                        return window.SystemInterfacesMap && SystemInterfacesMap.cy ? SystemInterfacesMap.cy : cy;
                    },
                    setLayout: applyLayout,
                    getCanvas: function () {
                        return document.getElementById('mapTabCanvas');
                    }
                });
            }
        }

        if (state.mapTabKind === 'process-data' && mapToolRoot && typeof window.SharedMapDropdowns === 'function') {
            if (mapToolRoot.dataset.processDataMapDropdownsWired !== '1') {
                mapToolRoot.dataset.processDataMapDropdownsWired = '1';
                window.SharedMapDropdowns({
                    mapId: 'processDataMap',
                    mapRoot: mapToolRoot,
                    getNetwork: function () {
                        return window.ProcessDataMap && ProcessDataMap.cy ? ProcessDataMap.cy : cy;
                    },
                    setLayout: applyLayout,
                    getCanvas: function () {
                        return document.getElementById('mapTabCanvas');
                    }
                });
            }
        }

        wireSpacingEdgeDropdowns();

        if (state.overlayColumns && typeof state.overlayColumns === 'object' && mapToolRoot) {
            Object.keys(state.overlayColumns).forEach(function (overlayType) {
                var cols = state.overlayColumns[overlayType];
                if (!Array.isArray(cols)) return;
                var otEsc = String(overlayType).replace(/\\/g, '\\\\').replace(/"/g, '\\"');
                var menu = mapToolRoot.querySelector('.map-overlay-columns-menu[data-overlay-type="' + otEsc + '"], .map-overlay-columns-menu');
                if (!menu) return;
                cols.forEach(function (col) {
                    var colId = col != null && typeof col === 'object' && col.id != null ? String(col.id) : String(col);
                    var cEsc = colId.replace(/\\/g, '\\\\').replace(/"/g, '\\"');
                    var cb = menu.querySelector('input[type="checkbox"][data-column-id="' + cEsc + '"]')
                        || menu.querySelector('input[type="checkbox"][data-column="' + cEsc + '"]')
                        || menu.querySelector('input[type="checkbox"][value="' + cEsc + '"]');
                    if (cb) cb.checked = true;
                });
            });
        }

        if (cy) {
            cy.on('zoom pan', positionAllOverlayPanels);
            cy.on('drag', 'node', positionAllOverlayPanels);
        }

        applyAllDynamicFilters();

        document.addEventListener('click', function (e) {
            var t = e.target;
            /** Toolbar-scoped id lookup (cloned controls live under #mapTabToolbarHost). Page chrome uses document. */
            function elInToolbarOrDoc(id) {
                if (!id) return null;
                if (mapToolRoot) {
                    try {
                        var q = mapToolRoot.querySelector('[id="' + String(id).replace(/\\/g, '\\\\').replace(/"/g, '\\"') + '"]');
                        if (q) return q;
                    } catch (e3) { /* no-op */ }
                }
                return document.getElementById(id);
            }
            var overlayBtn = t.closest('.map-select-btn[id*="OverlayBtn"]');
            if (overlayBtn) {
                e.stopPropagation();
                var omenu = getActiveOverlayMenu();
                if (omenu) {
                    var wasOpen = omenu.classList.contains('open');
                    closeAllMenus();
                    if (!wasOpen) omenu.classList.add('open');
                }
                return;
            }
            var filterBtn = t.closest('.map-select-btn[id*="FilterBtn"]');
            if (filterBtn) {
                e.stopPropagation();
                var fmenu = getActiveFilterMenu();
                if (fmenu) {
                    var wf = fmenu.classList.contains('open');
                    closeAllMenus();
                    if (!wf) fmenu.classList.add('open');
                }
                return;
            }
            var overlayItem = t.closest('.overlay-menu-item[data-overlay]');
            if (overlayItem) {
                e.preventDefault();
                e.stopPropagation();
                var overlayType = overlayItem.getAttribute('data-overlay');
                var wasActive = overlayItem.classList.contains('active');
                var menu3 = overlayItem.closest('.map-overlay-menu');
                if (menu3) menu3.querySelectorAll('.overlay-menu-item').forEach(function (i) { i.classList.remove('active'); });
                var oBtn2 = root().querySelector('.map-select-btn[id*="OverlayBtn"]');
                if (!wasActive) {
                    overlayItem.classList.add('active');
                    liveActiveOverlay = overlayType;
                    if (oBtn2) {
                        var spn2 = oBtn2.querySelector('span');
                        if (spn2) spn2.textContent = overlayItem.textContent.trim();
                    }
                    clearOverlayPanels();
                    if (state.mapTabKind === 'process-data' && window.ProcessDataMap && typeof ProcessDataMap.setOverlay === 'function') {
                        ProcessDataMap.setOverlay(overlayType);
                    } else {
                        var ctlPick = ensureTabOverlayController();
                        if (ctlPick) {
                            syncShimFromTab();
                            ctlPick.loadOverlayData(overlayType);
                        } else if (state.overlayPanelsHtml && String(state.overlayPanelsHtml).trim() && overlayType === savedActiveOverlay) {
                            injectOverlayPanelsHtml(state.overlayPanelsHtml);
                        }
                    }
                } else {
                    liveActiveOverlay = 'none';
                    if (oBtn2) {
                        var spn3 = oBtn2.querySelector('span');
                        if (spn3) spn3.textContent = 'None';
                    }
                    clearOverlayPanels();
                    if (state.mapTabKind === 'process-data' && window.ProcessDataMap && typeof ProcessDataMap.setOverlay === 'function') {
                        ProcessDataMap.setOverlay('none');
                    }
                }
                closeAllMenus();
                return;
            }
            var clearBtn = t.closest('.overlay-clear-btn');
            if (clearBtn) {
                e.preventDefault();
                e.stopPropagation();
                var menu4 = clearBtn.closest('.map-overlay-menu');
                if (menu4) menu4.querySelectorAll('.overlay-menu-item').forEach(function (i) { i.classList.remove('active'); });
                var oBtn3 = root().querySelector('.map-select-btn[id*="OverlayBtn"]');
                if (oBtn3) {
                    var spn4 = oBtn3.querySelector('span');
                    if (spn4) spn4.textContent = 'None';
                }
                liveActiveOverlay = 'none';
                clearOverlayPanels();
                if (state.mapTabKind === 'process-data' && window.ProcessDataMap && typeof ProcessDataMap.setOverlay === 'function') {
                    ProcessDataMap.setOverlay('none');
                }
                closeAllMenus();
                return;
            }
            var gridBtn = t.closest('[title="Overlay fields as columns"]');
            if (gridBtn) {
                e.stopPropagation();
                var colMenu = gridBtn.parentElement.querySelector('.map-overlay-columns-menu');
                if (colMenu) {
                    var wasOpen3 = colMenu.style.display !== 'none' && window.getComputedStyle(colMenu).display !== 'none';
                    closeAllMenus();
                    colMenu.style.display = wasOpen3 ? 'none' : 'block';
                    if (!wasOpen3) populateOverlayColumnsForTab(colMenu);
                }
                return;
            }
            var richLayoutBtn = t.closest('.map-select-btn[id*="LayoutBtn"]') || t.closest('button[id*="LayoutBtn"]');
            if (richLayoutBtn) {
                e.stopPropagation();
                var rmenu = root().querySelector('.map-layout-rich-menu');
                if (rmenu) {
                    var ro = rmenu.classList.contains('show');
                    closeAllMenus();
                    if (!ro) rmenu.classList.add('show');
                }
                return;
            }
            var richItem = t.closest('.map-layout-rich-item');
            if (richItem && richItem.dataset.layout) {
                applyLayout(richItem.dataset.layout);
                closeAllMenus();
                return;
            }
            var tb = fsToolbarBtn(t);
            if (tb && fsBtnId(tb, 'ZoomIn') && cy) {
                cy.zoom(cy.zoom() * 1.2);
                cy.center();
                return;
            }
            if (tb && fsBtnId(tb, 'ZoomOut') && cy) {
                cy.zoom(cy.zoom() * 0.8);
                cy.center();
                return;
            }
            if (tb && fsBtnId(tb, 'Redraw') && cy) {
                if (mapTabProcessDataActive && state.mapTabKind === 'process-data' && window.ProcessDataMap && typeof ProcessDataMap.redrawMap === 'function') {
                    ProcessDataMap.redrawMap();
                } else if (mapTabLineageEngineActive && window.SystemInterfacesMap && typeof SystemInterfacesMap.redrawMap === 'function') {
                    SystemInterfacesMap.redrawMap();
                } else {
                    cy.layout(buildLayout(currentLayout)).run();
                }
                return;
            }
            if (tb && fsBtnId(tb, 'Reset') && cy) {
                if (mapTabProcessDataActive && state.mapTabKind === 'process-data' && window.ProcessDataMap && typeof ProcessDataMap.resetMap === 'function') {
                    ProcessDataMap.resetMap();
                } else if (mapTabLineageEngineActive && window.SystemInterfacesMap && typeof SystemInterfacesMap.resetMap === 'function') {
                    SystemInterfacesMap.resetMap();
                } else {
                    cy.nodes().removeClass('filter-hidden');
                    cy.edges().removeClass('filter-hidden');
                    cy.nodes().forEach(function (n) { n.style('display', 'element'); });
                    cy.edges().forEach(function (ed) { ed.style('display', 'element'); });
                    applyAllDynamicFilters();
                    cy.fit(undefined, 50);
                    positionAllOverlayPanels();
                }
                return;
            }
            if (tb && fsBtnId(tb, 'Export') && cy) {
                var ex = typeof window.exportMapWithOverlays === 'function' ? window.exportMapWithOverlays : null;
                var body = elInToolbarOrDoc('mapTabBody') || document.getElementById('mapTabBody');
                if (ex) ex(cy, body, exportFilename);
                else {
                    var png = cy.png({ bg: '#ffffff', full: true, scale: 2 });
                    var a = document.createElement('a');
                    a.download = exportFilename;
                    a.href = png;
                    a.click();
                }
                return;
            }
            if (tb && fsBtnId(tb, 'ToggleLabels') && cy) {
                var on = !tb.classList.contains('active');
                tb.classList.toggle('active', on);
                if (mapTabProcessDataActive && state.mapTabKind === 'process-data' && window.ProcessDataMap && typeof ProcessDataMap.toggleInterfaceLabels === 'function') {
                    ProcessDataMap.toggleInterfaceLabels(on);
                } else if (mapTabLineageEngineActive && window.SystemInterfacesMap && typeof SystemInterfacesMap.toggleInterfaceLabels === 'function') {
                    SystemInterfacesMap.toggleInterfaceLabels(on);
                } else {
                    cy.edges().forEach(function (edge) {
                        edge.style('label', on ? edgeLabelFromData(edge) : '');
                    });
                }
                return;
            }
            if (tb && (fsBtnId(tb, 'Legend') || (tb.getAttribute('aria-label') && tb.getAttribute('aria-label').indexOf('Legend') >= 0))) {
                var leg = elInToolbarOrDoc('mapTabLegend') || document.getElementById('mapTabLegend');
                if (leg) {
                    var vis = leg.style.display !== 'none' && window.getComputedStyle(leg).display !== 'none';
                    leg.style.display = vis ? 'none' : 'block';
                    tb.classList.toggle('active', !vis);
                }
                return;
            }
            if (tb && fsBtnId(tb, 'Navigator') && window.MapRenderUtils && typeof MapRenderUtils.attachOrToggleLineageMinimap === 'function' && cy) {
                var fb = elInToolbarOrDoc('mapTabBody') || document.getElementById('mapTabBody');
                if (fb) {
                    MapRenderUtils.attachOrToggleLineageMinimap({
                        container: fb,
                        network: cy,
                        syncViewport: true,
                        minimapCanvasId: 'mapTabLineageMinimapCy'
                    });
                }
                return;
            }
            var insideToolbarSelect = mapToolRoot && t.closest && mapToolRoot.contains(t) && t.closest('select');
            var insideMenu = t.closest('.map-btn-dropdown-menu') || t.closest('.map-layout-rich-menu') || t.closest('.map-overlay-menu') ||
                t.closest('.map-filter-menu') || t.closest('.map-overlay-columns-menu') || t.closest('.map-select-btn') ||
                t.closest('button[id*="LayoutBtn"]') || insideToolbarSelect;
            if (!insideMenu) closeAllMenus();
        }, true);

        document.addEventListener('change', function (e) {
            var el = e.target;
            if (el.tagName === 'SELECT' && el.id && isMapTypeSelectId(el.id)) {
                currentMapType = el.value;
                switchMenusForMapTypeLocal();
                return;
            }
            if (el.tagName === 'SELECT' && el.id && isLayoutSelectId(el.id)) {
                applyLayout(el.value);
                return;
            }
            if (el.tagName === 'INPUT' && el.type === 'number' && el.id && el.id.indexOf('Hops') >= 0) {
                if (mapTabLineageEngineActive && state.mapTabKind === 'system-interfaces' && window.SystemInterfacesMap && typeof SystemInterfacesMap.setHopsCount === 'function') {
                    SystemInterfacesMap.setHopsCount(el.value);
                    return;
                }
                if (mapTabProcessDataActive && state.mapTabKind === 'process-data' && window.ProcessDataMap && typeof ProcessDataMap.setHopsCount === 'function') {
                    ProcessDataMap.setHopsCount(el.value);
                    return;
                }
            }
        }, true);

        root().querySelectorAll('input.map-hops-input,input[type=number]').forEach(function (inp) {
            if (!inp.id || inp.id.indexOf('Hops') < 0) return;
            inp.addEventListener('change', function () { /* hops handled on document capture when lineage engine active */ });
        });

        var leg2 = document.getElementById('mapTabLegend');
        if (leg2 && leg2.innerHTML.trim()) leg2.style.display = 'block';

        if (state.mapTabKind === 'system-interfaces' && mapTabEntityId != null && window.SystemInterfacesMap) {
            setTimeout(function () {
                if (!reloadSystemInterfacesGraphForMapTab() && cy) {
                    setTimeout(function () {
                        try {
                            cy.layout(buildLayout(currentLayout)).run();
                        } catch (eL) { mtDbgWarn('[map-tab] final layout', eL); }
                        cy.fit(undefined, 50);
                        positionAllOverlayPanels();
                    }, 120);
                }
            }, 0);
        } else if (state.mapTabKind === 'process-data' && mapTabEntityId != null) {
            setTimeout(function () {
                startProcessDataLiveTab();
            }, 0);
        } else if (cy) {
            setTimeout(function () {
                try {
                    cy.layout(buildLayout(currentLayout)).run();
                } catch (eL) { mtDbgWarn('[map-tab] final layout', eL); }
                cy.fit(undefined, 50);
                positionAllOverlayPanels();
            }, 120);
        }
    }

    window.initMapTabFromSession = function (mapStateId) {
        if (!mapStateId) return;
        var raw = sessionStorage.getItem(PREFIX + mapStateId);
        if (!raw) {
            document.body.innerHTML = '<p style="padding:2rem;font-family:sans-serif;">This map tab session expired. Close this tab and open the map again from the application.</p>';
            return;
        }
        try {
            var state = JSON.parse(raw);
            initMapTab(state);
        } catch (err) {
            console.error('[map-tab] parse', err);
            document.body.innerHTML = '<p style="padding:2rem;font-family:sans-serif;">Could not read saved map state.</p>';
        }
    };

    window.initMapTab = initMapTab;

    function bootMapTabPage() {
        if (isFullMapPath()) {
            var st = buildStateFromFullMapPath();
            if (st) {
                initMapTab(st);
                return;
            }
            document.body.innerHTML = '<p style="padding:2rem;font-family:sans-serif;">Invalid full map URL. Expected <code>/view/full_map/{facet}/{id}</code> or <code>?tb=</code> for a snapshot.</p>';
            return;
        }
        var legacyStateId = queryMapStateId();
        if (legacyStateId) {
            window.initMapTabFromSession(legacyStateId);
            return;
        }
        document.body.innerHTML = '<p style="padding:2rem;font-family:sans-serif;">Open the map via <code>/view/full_map/…</code> (use fullscreen from the application).</p>';
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', bootMapTabPage);
    } else {
        bootMapTabPage();
    }
})();
