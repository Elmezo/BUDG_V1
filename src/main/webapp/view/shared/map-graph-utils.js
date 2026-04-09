/**
 * map-graph-utils.js
 * ─────────────────────────────────────────────────────────────────────────────
 * Pure, stateless graph-utility functions shared across every map module.
 *
 * These functions NEVER read from module-level state or touch the DOM.
 * All required values are passed as explicit arguments so they can be
 * called identically from system-interfaces, dataset, process-data,
 * project-data, glossary-data, glossary-relationships and capability maps.
 *
 * Usage in a module:
 *
 *   // BFS hops trim
 *   const trimmed = MapGraphUtils.applyHopsFilter(graph, State.hopsCount, n => n.isCurrent);
 *
 *   // Hidden-node + edge prune
 *   return MapGraphUtils.filterHiddenNodes(nodes, edges, State.hiddenNodes);
 *
 *   // Sync MapEngine filter args into module state (inside _mapEngineApiLoader)
 *   MapGraphUtils.mergeEngineFilters(State, filters);
 *   MapGraphUtils.buildDataFlowKey(flow, { useArrowSeparator: true });
 *
 *   // Or one adapter per map file (binds State + mapId + zoom behaviour):
 *   const adapter = createMapAdapter(State, 'myMapId', { zoomRecenter: true });
 *   adapter.applyHopsFilter(graph); adapter.filterHiddenNodes(nodes, edges);
 *
 * Every function is guarded with a safe passthrough so that if the file is
 * not loaded, calling code can do:
 *   const result = window.MapGraphUtils
 *       ? window.MapGraphUtils.someUtil(...)
 *       : fallback;
 *
 * Load order: place BEFORE any module-specific map files.
 */

(function () {
    'use strict';

    // =========================================================================
    // 1. applyHopsFilter
    // ─────────────────────────────────────────────────────────────────────────
    // BFS from root nodes, keeping every node within maxHops steps.
    // Edges whose source or target falls outside the allowed set are removed.
    //
    // @param {Object}   graph        - { nodes: [], edges: [] }  (raw data, not Cytoscape)
    // @param {number}   maxHops      - Maximum steps from root (clamped 1–99)
    // @param {Function} rootSelector - Predicate: (node) => boolean
    //                                  If no root matches, first node is used as fallback.
    // @returns {{ nodes: [], edges: [] }}
    // =========================================================================
    function applyHopsFilter(graph, maxHops, rootSelector) {
        var nodes   = graph.nodes;
        var edges   = graph.edges;
        var clamped = Math.min(99, Math.max(1, maxHops || 15));

        // Build adjacency index (both directions for undirected BFS)
        var outEdges = new Map();
        var inEdges  = new Map();
        edges.forEach(function (e) {
            if (!outEdges.has(e.from)) outEdges.set(e.from, []);
            outEdges.get(e.from).push(e);
            if (!inEdges.has(e.to)) inEdges.set(e.to, []);
            inEdges.get(e.to).push(e);
        });

        // Determine root set
        var selector = typeof rootSelector === 'function'
            ? rootSelector
            : function (n) { return !!n[rootSelector]; };   // string property name

        var roots = nodes.filter(selector).map(function (n) { return n.id; });
        if (roots.length === 0 && nodes.length > 0) roots.push(nodes[0].id);

        // BFS
        var allowed = new Set();
        var queue   = roots.map(function (id) { return { id: id, hop: 0 }; });
        var seen    = new Set();
        while (queue.length) {
            var item = queue.shift();
            var id   = item.id;
            var hop  = item.hop;
            if (seen.has(id)) continue;
            seen.add(id);
            if (hop <= clamped) allowed.add(id);
            if (hop >= clamped) continue;
            (outEdges.get(id) || []).forEach(function (e) { queue.push({ id: e.to,   hop: hop + 1 }); });
            (inEdges.get(id)  || []).forEach(function (e) { queue.push({ id: e.from, hop: hop + 1 }); });
        }

        return {
            nodes: nodes.filter(function (n) { return allowed.has(n.id); }),
            edges: edges.filter(function (e) { return allowed.has(e.from) && allowed.has(e.to); })
        };
    }

    // =========================================================================
    // 2. filterHiddenNodes
    // ─────────────────────────────────────────────────────────────────────────
    // Remove hidden nodes from a graph data structure, then prune any edges
    // whose source or target is no longer visible.
    //
    // @param {Array}  nodes     - Raw node array
    // @param {Array}  edges     - Raw edge array
    // @param {Set}    hiddenSet - Set of node IDs to exclude
    // @returns {{ nodes: [], edges: [] }}
    // =========================================================================
    function filterHiddenNodes(nodes, edges, hiddenSet) {
        if (!hiddenSet || hiddenSet.size === 0) return { nodes: nodes, edges: edges };
        var visible   = nodes.filter(function (n) { return !hiddenSet.has(n.id); });
        var visibleIds = new Set(visible.map(function (n) { return n.id; }));
        return {
            nodes: visible,
            edges: edges.filter(function (e) {
                return visibleIds.has(String(e.from || e.source)) &&
                       visibleIds.has(String(e.to   || e.target));
            })
        };
    }

    // =========================================================================
    // 3. mergeEngineFilters
    // ─────────────────────────────────────────────────────────────────────────
    // Sync the `filters` argument received by every _mapEngineApiLoader into
    // the module's state object.  Handles all common filter shapes:
    //   hopsCount, nodeFilters, datasetNodeFilters, filters (boolean flags).
    //
    // @param {Object} state   - Module state object (e.g. InterfaceMapState)
    // @param {Object} filters - The third argument from MapEngine._loadAndRender
    // =========================================================================
    function mergeEngineFilters(state, filters) {
        if (!filters) return;
        if (filters.hopsCount !== undefined) {
            var parsed = parseInt(filters.hopsCount, 10);
            if (!isNaN(parsed) && parsed > 0) state.hopsCount = parsed;
        }
        if (filters.nodeFilters && state.nodeFilters) {
            Object.assign(state.nodeFilters, filters.nodeFilters);
        }
        if (filters.datasetNodeFilters && state.datasetNodeFilters) {
            Object.assign(state.datasetNodeFilters, filters.datasetNodeFilters);
        }
        if (filters.filters && state.filters) {
            Object.assign(state.filters, filters.filters);
        }
    }

    // =========================================================================
    // 4. applySystemNodeFilters
    // ─────────────────────────────────────────────────────────────────────────
    // Show/hide Cytoscape SYSTEM nodes based on classification / type / lifecycle
    // filter arrays.  Updates connected edge visibility as a side-effect.
    //
    // @param {Object} network     - Cytoscape instance
    // @param {Object} nodeFilters - { classifications: [], types: [], lifecycles: [] }
    // @param {Object} [opts]      - { updateOverlayPositions, runLayout,
    //                                 filtersInitialized, logPrefix, verboseSystemFilters }
    //   filtersInitialized === true: system-lineage semantics — each dimension with an empty
    //   array hides non-current nodes for that dimension; all dimensions must pass (AND).
    //   Omit or falsey: legacy behavior — empty filter arrays mean "no constraint" on that
    //   dimension; non-current nodes hidden only when at least one array is non-empty.
    //   verboseSystemFilters === true with logPrefix: logs filter apply + visible/hidden counts.
    // =========================================================================
    function applySystemNodeFilters(network, nodeFilters, opts) {
        if (!network) return;
        opts = opts || {};
        var classifications = (nodeFilters && nodeFilters.classifications) || [];
        var types           = (nodeFilters && nodeFilters.types)           || [];
        var lifecycles      = (nodeFilters && nodeFilters.lifecycles)      || [];

        if (opts.filtersInitialized === true) {
            if (opts.logPrefix && opts.verboseSystemFilters === true) {
                console.log(opts.logPrefix + ' Applying system filters - classifications:',
                    classifications, 'types:', types, 'lifecycles:', lifecycles);
            }
            var visibleCount = 0;
            var hiddenCount = 0;
            network.nodes().forEach(function (node) {
                var meta = node.data('meta') || {};
                var nodeClassification = meta.classification || '';
                var nodeType = meta.type || '';
                var nodeLifecycle = meta.lifecycle || '';
                var passesClassification = classifications.length > 0
                    ? (nodeClassification && classifications.indexOf(nodeClassification) !== -1)
                    : false;
                var passesType = types.length > 0
                    ? (nodeType && types.indexOf(nodeType) !== -1)
                    : false;
                var passesLifecycle = lifecycles.length > 0
                    ? (nodeLifecycle && lifecycles.indexOf(nodeLifecycle) !== -1)
                    : false;
                var isCurrent = node.data('isCurrent');
                if (isCurrent || (passesClassification && passesType && passesLifecycle)) {
                    node.style('display', 'element');
                    node.removeClass('hidden');
                    visibleCount++;
                } else {
                    node.style('display', 'none');
                    node.addClass('hidden');
                    hiddenCount++;
                }
            });
            if (opts.logPrefix && opts.verboseSystemFilters === true) {
                console.log(opts.logPrefix + ' System filter result - visible:', visibleCount, 'hidden:', hiddenCount);
            }
            network.edges().forEach(function (edge) {
                var sourceVisible = edge.source().style('display') !== 'none';
                var targetVisible = edge.target().style('display') !== 'none';
                edge.style('display', (sourceVisible && targetVisible) ? 'element' : 'none');
            });
            if (typeof opts.updateOverlayPositions === 'function') opts.updateOverlayPositions();
            if (typeof opts.runLayout === 'function') opts.runLayout();
            return;
        }

        if (opts.logPrefix && opts.verboseSystemFilters === true) {
            console.log(opts.logPrefix + ' Applying system filters - classifications:',
                classifications, 'types:', types, 'lifecycles:', lifecycles);
        }

        var hasFilter = classifications.length || types.length || lifecycles.length;

        network.nodes().forEach(function (node) {
            var isCurrent = node.data('isCurrent');
            var show = true;

            if (!isCurrent && hasFilter) {
                var meta  = node.data('meta') || {};
                var klass = String(meta.classification || '');
                var type  = String(meta.type  || meta.typeName  || '');
                var life  = String(meta.lifecycle || meta.lifecycleName || '');

                if (classifications.length && klass && classifications.indexOf(klass) === -1) show = false;
                if (types.length           && type  && types.indexOf(type) === -1)            show = false;
                if (lifecycles.length      && life  && lifecycles.indexOf(life) === -1)       show = false;
            }

            node.style('display', show ? 'element' : 'none');
        });

        network.edges().forEach(function (edge) {
            var sv = edge.source().style('display') === 'element';
            var tv = edge.target().style('display') === 'element';
            edge.style('display', (sv && tv) ? 'element' : 'none');
        });

        if (opts.logPrefix && opts.verboseSystemFilters === true) {
            var visibleCountLegacy = 0;
            var hiddenCountLegacy = 0;
            network.nodes().forEach(function (node) {
                if (node.style('display') === 'none') hiddenCountLegacy++;
                else visibleCountLegacy++;
            });
            console.log(opts.logPrefix + ' System filter result - visible:', visibleCountLegacy,
                'hidden:', hiddenCountLegacy);
        }

        if (typeof opts.updateOverlayPositions === 'function') opts.updateOverlayPositions();
        if (typeof opts.runLayout === 'function') opts.runLayout();
    }

    // =========================================================================
    // 5. applyDatasetNodeFilters
    // ─────────────────────────────────────────────────────────────────────────
    // Show/hide Cytoscape DATASET nodes based on type and lifecycle filters.
    // isCurrent nodes are always shown regardless of filter state.
    //
    // @param {Object} network        - Cytoscape instance
    // @param {Object} datasetFilters - { types: [], lifecycles: [] }
    // @param {Object} [opts]         - { linkedDatasets: Map, updateOverlayPositions: fn }
    // =========================================================================
    function applyDatasetNodeFilters(network, datasetFilters, opts) {
        if (!network) return;
        var types      = (datasetFilters && datasetFilters.types)      || [];
        var lifecycles = (datasetFilters && datasetFilters.lifecycles) || [];
        var linked     = (opts && opts.linkedDatasets) ? opts.linkedDatasets : new Map();

        network.nodes().forEach(function (node) {
            var isCurrent = node.data('isCurrent');
            if (isCurrent) {
                node.style('display', 'element');
                node.removeClass('hidden');
                return;
            }

            var meta          = node.data('meta') || {};
            var nodeType      = String(meta.typeName || meta.type || '');
            var nodeLifecycle = String(meta.lifecycleName || meta.lifecycle || '');

            // Fallback: look up dataset from linked map when meta is sparse
            if ((!nodeType || !nodeLifecycle) && linked.size) {
                var datasetId   = meta.datasetId;
                var datasetInfo = linked.get(String(datasetId));
                var dataset     = (datasetInfo && datasetInfo.dataset) ? datasetInfo.dataset : {};
                nodeType      = nodeType      || String(dataset.typeName || dataset.datasetTypeName || dataset.type || '');
                nodeLifecycle = nodeLifecycle || String(dataset.lifecycleName || dataset.lifecycle || '');
            }

            var passesType = types.length      === 0 || types.includes(nodeType);
            var passesLife = lifecycles.length === 0 || lifecycles.includes(nodeLifecycle);
            var show       = passesType && passesLife;

            node.style('display', show ? 'element' : 'none');
            if (show) node.removeClass('hidden'); else node.addClass('hidden');
        });

        // Update edge visibility
        network.edges().forEach(function (edge) {
            var sv = edge.source().style('display') === 'element';
            var tv = edge.target().style('display') === 'element';
            edge.style('display', (sv && tv) ? 'element' : 'none');
        });

        if (opts && typeof opts.updateOverlayPositions === 'function') opts.updateOverlayPositions();
    }

    // =========================================================================
    // 6. extractSystemMeta
    // ─────────────────────────────────────────────────────────────────────────
    // Normalise a raw system API response into the standard meta object shape
    // used by every system node in graph builders.
    //
    // @param {Object|null} systemData - Raw API response for a system
    // @param {string|number} systemId - The system's ID
    // @returns {{ systemId, systemName, refNumber, classification, type, lifecycle }}
    // =========================================================================
    function extractSystemMeta(systemData, systemId) {
        var id   = String(systemId);
        var data = systemData || {};
        return {
            systemId:       id,
            systemName:     data.name || data.primaryName || data.primaryname || ('System ' + id),
            refNumber:      data.refNumber || data.RefNumber || data.ref_number || data.ref || '',
            classification: data.classificationName || data.classification || '',
            type:           data.typeName  || data.type  || '',
            lifecycle:      data.lifecycleName || data.lifecycle || ''
        };
    }

    // =========================================================================
    // 6b. buildDataFlowKey
    // ─────────────────────────────────────────────────────────────────────────
    // Dedup key for data-flow records. System map uses "from->to"; process-data
    // map uses "from|to|interfaceId" when includeInterfaceId is true.
    //
    // @param {Object} flow
    // @param {{ useArrowSeparator?: boolean, includeInterfaceId?: boolean }} [options]
    // @returns {string|null}
    // =========================================================================
    function buildDataFlowKey(flow, options) {
        options = options || {};
        if (!flow) return null;
        var fromId = flow.fromId != null ? flow.fromId : (flow.sourceSystemId != null ? flow.sourceSystemId : flow.from_id);
        var toId = flow.toId != null ? flow.toId : (flow.targetSystemId != null ? flow.targetSystemId : flow.to_id);
        if (fromId == null || toId == null || fromId === '' || toId === '') return null;
        var arrow = options.useArrowSeparator === true;
        var sep = arrow ? '->' : '|';
        var key = String(fromId) + sep + String(toId);
        if (options.includeInterfaceId) {
            var iface = flow.interfaceId != null ? flow.interfaceId : flow.interface_id;
            if (iface != null) key += sep + String(iface);
        }
        return key;
    }

    // =========================================================================
    // 7. buildEdgesFromInterfacesAndFlow
    // ─────────────────────────────────────────────────────────────────────────
    // Build deduplicated edge objects from interfaces + data-flow records.
    // Used by process-data, project-data and system-interfaces maps.
    //
    // Edge format: { id, from, to, dashes }
    //   dashes === true  → data-attribute count is zero (dashed line)
    //   dashes === false → at least one data attribute (solid line)
    //
    // @param {Array}  interfacesData - Array of interface records
    // @param {Array}  dataFlowData   - Array of data-flow records
    // @param {Set}    visibleNodeSet - Set of node IDs that exist in the graph
    // @returns {Array} edges
    // =========================================================================
    function buildEdgesFromInterfacesAndFlow(interfacesData, dataFlowData, visibleNodeSet) {
        var edges   = [];
        var edgeMap = new Set();

        (interfacesData || []).forEach(function (iface) {
            var fromId = String(iface.fromId || iface.sourceSystemId || '');
            var toId   = String(iface.toId   || iface.targetSystemId || '');
            if (!fromId || !toId) return;
            if (!visibleNodeSet.has(fromId) || !visibleNodeSet.has(toId)) return;
            var key = fromId + '->' + toId + '-i-' + (iface.id || '');
            if (edgeMap.has(key)) return;
            edgeMap.add(key);
            edges.push({
                id:     key,
                from:   fromId,
                to:     toId,
                dashes: (iface.dataAttributes || iface.data_attributes || 0) === 0
            });
        });

        (dataFlowData || []).forEach(function (flow) {
            var fromId = String(flow.fromId || flow.sourceSystemId || '');
            var toId   = String(flow.toId   || flow.targetSystemId || '');
            if (!fromId || !toId) return;
            if (!visibleNodeSet.has(fromId) || !visibleNodeSet.has(toId)) return;
            var key = fromId + '->' + toId + '-f-' + (flow.interfaceId || flow.interface_id || '');
            if (edgeMap.has(key)) return;
            edgeMap.add(key);
            edges.push({ id: key, from: fromId, to: toId, dashes: false });
        });

        return edges;
    }

    // =========================================================================
    // 8. extractGlossaryNameFromAttr
    // ─────────────────────────────────────────────────────────────────────────
    // Extract the glossary-name string from a raw data-attribute object.
    // Tries the exact key 'Glossary Name attribute' first, then falls back to
    // a case-insensitive search for any key containing both 'glossary' and
    // 'name' (but not 'type').
    //
    // Identical implementation in system-interfaces-map.js, dataset-
    // relationships-map.js, and glossary-data-map.js.
    //
    // @param {Object} attr - Raw attribute object from the API
    // @returns {string|null}
    // =========================================================================
    function extractGlossaryNameFromAttr(attr) {
        if (!attr || typeof attr !== 'object') return null;
        var v = attr['Glossary Name attribute'];
        if (v != null && v !== '') return String(v);
        var keys = Object.keys(attr);
        for (var i = 0; i < keys.length; i++) {
            var k = keys[i].toLowerCase();
            if (k.indexOf('glossary') !== -1 && k.indexOf('name') !== -1 && k.indexOf('type') === -1) {
                var val = attr[keys[i]];
                if (val != null && val !== '') return String(val);
            }
        }
        return null;
    }

    // =========================================================================
    // createMapAdapter(state, mapId, options?)
    // ─────────────────────────────────────────────────────────────────────────
    // Single binding point per map module: graph helpers use `state`, render
    // helpers read `state.network` / `state.layout`. No per-module thin wrappers.
    //
    // options.zoomRecenter — passed to MapRenderUtils.zoomIn/Out (true for
    //   system + dataset maps; false for glossary, capability, process, project).
    // =========================================================================
    function createMapAdapter(state, mapId, options) {
        options = options || {};
        var zoomRecenter = !!options.zoomRecenter;

        function graph() {
            return window.MapGraphUtils;
        }
        function render() {
            return window.MapRenderUtils;
        }

        return {
            mapId: mapId || '',
            applyHopsFilter: function (graphData, rootSelector) {
                var U = graph();
                if (!U || !graphData) return graphData;
                var sel;
                if (typeof rootSelector === 'function') {
                    sel = rootSelector;
                } else if (typeof rootSelector === 'string' && rootSelector) {
                    sel = function (n) { return !!n[rootSelector]; };
                } else {
                    sel = function (n) { return n.isCurrent; };
                }
                return U.applyHopsFilter(graphData, state.hopsCount, sel);
            },
            filterHiddenNodes: function (nodes, edges) {
                var U = graph();
                if (!U) return { nodes: nodes, edges: edges };
                return U.filterHiddenNodes(nodes, edges, state.hiddenNodes);
            },
            mergeEngineFilters: function (filters) {
                if (graph()) graph().mergeEngineFilters(state, filters);
            },
            extractGlossaryNameFromAttr: function (attr) {
                return graph() ? graph().extractGlossaryNameFromAttr(attr) : null;
            },
            applyDatasetNodeFilters: function (opts) {
                if (graph()) graph().applyDatasetNodeFilters(state.network, state.datasetNodeFilters, opts || {});
            },
            applySystemNodeFilters: function (opts) {
                if (graph()) graph().applySystemNodeFilters(state.network, state.nodeFilters, opts || {});
            },
            findRootNodes: function () {
                return render() ? render().findRootNodeIds(state.network) : [];
            },
            getNodeSizes: function () {
                if (render() && render().getNodeSizes) return render().getNodeSizes();
                return {
                    system: { width: 140, height: 80 },
                    dataset: { width: 140, height: 80 },
                    glossary: { width: 140, height: 80 },
                    capability: { width: 140, height: 80 },
                    process: { width: 140, height: 80 },
                    project: { width: 140, height: 80 }
                };
            },
            getSystemIcon: function (isOrange) {
                return render() && render().getSystemIcon ? render().getSystemIcon(isOrange) : null;
            },
            buildLayoutOptions: function () {
                return render() && render().buildDagreLayoutOptions
                    ? render().buildDagreLayoutOptions(state.layout, mapId || '')
                    : { name: 'dagre', animate: true };
            },
            zoomIn: function () {
                if (render() && state.network) render().zoomIn(state.network, zoomRecenter);
            },
            zoomOut: function () {
                if (render() && state.network) render().zoomOut(state.network, zoomRecenter);
            }
        };
    }

    // =========================================================================
    // Public API
    // =========================================================================
    window.MapGraphUtils = {
        applyHopsFilter:                applyHopsFilter,
        filterHiddenNodes:              filterHiddenNodes,
        mergeEngineFilters:             mergeEngineFilters,
        applySystemNodeFilters:         applySystemNodeFilters,
        applyDatasetNodeFilters:        applyDatasetNodeFilters,
        extractSystemMeta:              extractSystemMeta,
        buildDataFlowKey:               buildDataFlowKey,
        buildEdgesFromInterfacesAndFlow: buildEdgesFromInterfacesAndFlow,
        extractGlossaryNameFromAttr:    extractGlossaryNameFromAttr
    };

    window.createMapAdapter = createMapAdapter;

})();
