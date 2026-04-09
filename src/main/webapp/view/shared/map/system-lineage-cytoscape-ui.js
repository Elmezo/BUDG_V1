/**
 * system-lineage-cytoscape-ui.js — Cytoscape stylesheet + layout for system Interfaces/Data lineage maps.
 * Load after map-graph-utils (adapter) and map-render-utils; before system-interfaces-map.js.
 */
(function () {
    'use strict';

    /**
     * @param {Object} ctx
     * @param {Object} ctx.state — e.g. InterfaceMapState (layout, dagreRegistered)
     * @param {Object} ctx.adapter — createMapAdapter result (getNodeSizes, findRootNodes)
     * @param {function(): Object} ctx.getMapColors
     * @param {function(string, boolean): string} ctx.getNodeIcon
     * @param {string} [ctx.mapId] — SharedMapDropdowns + InterfaceMapStyles id (default 'interfaceMap')
     * @param {string} [ctx.logPrefix]
     */
    window.createSystemLineageCytoscapeStyleLayout = function (ctx) {
        var state = ctx.state;
        var adapter = ctx.adapter;
        var getMapColors = ctx.getMapColors;
        var getNodeIcon = ctx.getNodeIcon;
        var mapId = ctx.mapId || 'interfaceMap';
        var logPrefix = ctx.logPrefix || '[SYSTEM-LINEAGE-CY]';

        function cyLayoutWarn(suffix) {
            if (typeof window !== 'undefined' && window.BUDG_DEBUG_SYSTEM_LINEAGE_GRAPH === true) {
                console.warn(logPrefix + suffix);
            }
        }

        function getCytoscapeStyle() {
            if (window.InterfaceMapStyles && typeof window.InterfaceMapStyles.getCytoscapeStyles === 'function') {
                return window.InterfaceMapStyles.getCytoscapeStyles(mapId);
            }

            var colors = getMapColors();
            var nodeSizes = adapter.getNodeSizes();
            var ddApis = window._sharedDropdownApis;
            var curveStyle = (ddApis && ddApis[mapId] && typeof ddApis[mapId].getCurveStyle === 'function')
                ? ddApis[mapId].getCurveStyle()
                : 'bezier';

            return [
                {
                    selector: 'node',
                    style: {
                        label: 'data(label)',
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
                        color: colors.nodeLabel || '#1f2937',
                        shape: 'round-rectangle',
                        width: nodeSizes.system.width,
                        height: nodeSizes.system.height,
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
                        shape: 'round-rectangle',
                        width: nodeSizes.system.width * 1.2,
                        height: nodeSizes.system.height * 1.3,
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
                        width: 2,
                        'line-color': 'data(lineColor)',
                        'target-arrow-color': 'data(lineColor)',
                        'target-arrow-shape': 'triangle',
                        'target-arrow-width': 8,
                        'curve-style': curveStyle,
                        label: 'data(label)',
                        'font-size': 10,
                        'font-family': 'Inter, system-ui, sans-serif',
                        'text-rotation': 'autorotate',
                        'text-margin-y': -10,
                        color: colors.edgeLabelColor || '#6b7280',
                        'text-background-color': '#ffffff',
                        'text-background-opacity': 0.8,
                        'text-background-padding': '2px'
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
                        width: 3,
                        'z-index': 999
                    }
                },
                {
                    selector: '.highlighted-downstream',
                    style: {
                        'line-color': colors.downstreamHighlight,
                        'target-arrow-color': colors.downstreamHighlight,
                        width: 3,
                        'z-index': 999
                    }
                },
                {
                    selector: '.dimmed',
                    style: { opacity: 0.25 }
                },
                {
                    selector: '.focused',
                    style: {
                        'border-color': colors.downstreamHighlight,
                        'border-width': 4
                    }
                },
                {
                    selector: '.reversed-edge',
                    style: {
                        'target-arrow-shape': 'none',
                        'source-arrow-shape': 'triangle',
                        'source-arrow-color': colors.attributeLine || '#64748b'
                    }
                },
                {
                    selector: '.reversed-edge[lineColor]',
                    style: {
                        'source-arrow-color': 'data(lineColor)'
                    }
                }
            ];
        }

        function buildCytoscapeLayout() {
            var layoutOption = state.layout;
            var ddApi = window._sharedDropdownApis && window._sharedDropdownApis[mapId];
            var sf = ddApi ? ddApi.getSpacingFactor() : 1.8;
            var sp = ddApi ? ddApi.getSpacingPadding() : 60;

            if (window.InterfaceMapStyles && typeof window.InterfaceMapStyles.getLayoutConfig === 'function') {
                var layoutConfig = window.InterfaceMapStyles.getLayoutConfig(layoutOption);
                if (layoutConfig.spacingFactor != null) layoutConfig.spacingFactor = sf;
                if (layoutConfig.padding != null) layoutConfig.padding = sp;
                if (layoutConfig.name === 'dagre' && !state.dagreRegistered) {
                    cyLayoutWarn(' Dagre not available, falling back to breadthfirst');
                    return {
                        name: 'breadthfirst',
                        directed: true,
                        padding: sp,
                        spacingFactor: sf,
                        animate: true,
                        animationDuration: 500,
                        nodeDimensionsIncludeLabels: true,
                        roots: adapter.findRootNodes()
                    };
                }
                if (layoutConfig.name === 'breadthfirst') {
                    return Object.assign({}, layoutConfig, { roots: adapter.findRootNodes() });
                }
                return layoutConfig;
            }

            var baseLayout = {
                name: 'breadthfirst',
                directed: true,
                padding: sp,
                spacingFactor: sf,
                animate: true,
                animationDuration: 500,
                nodeDimensionsIncludeLabels: true
            };

            var spMult = ddApi ? (ddApi.getSpacing() === 'compact' ? 0.6 : (ddApi.getSpacing() === 'spacey' ? 1.8 : 1.0)) : 1.0;

            switch (layoutOption) {
                case 'right-to-left':
                    return Object.assign({}, baseLayout, {
                        roots: adapter.findRootNodes(),
                        transform: function (node, pos) { return { x: -pos.x, y: pos.y }; }
                    });
                case 'top-to-bottom':
                    if (state.dagreRegistered) {
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
                    cyLayoutWarn(' Dagre not available, using breadthfirst fallback for top-to-bottom');
                    return Object.assign({}, baseLayout, {
                        roots: adapter.findRootNodes(),
                        circle: false
                    });
                case 'force':
                    return {
                        name: 'cose',
                        animate: true,
                        animationDuration: 500,
                        nodeRepulsion: Math.round(5000 * spMult),
                        idealEdgeLength: Math.round(150 * spMult),
                        edgeElasticity: 0.45,
                        nestingFactor: 0.1,
                        gravity: 0.25,
                        numIter: 1500,
                        padding: sp,
                        initialEnergyOnIncremental: 0.3
                    };
                default:
                    if (state.dagreRegistered) {
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
                    return Object.assign({}, baseLayout, { roots: adapter.findRootNodes() });
            }
        }

        return {
            getCytoscapeStyle: getCytoscapeStyle,
            buildCytoscapeLayout: buildCytoscapeLayout
        };
    };
})();
