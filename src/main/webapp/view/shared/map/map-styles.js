/**
 * Shared Map Styles Configuration
 * Defines all visual styles for map visualization across facets
 * 
 * Usage: Include this file before any map components
 */

(function() {
    'use strict';

    // ============================================
    // COLOR CONFIGURATION
    // ============================================
    const MAP_COLORS = {
        // Node Colors - Systems
        currentSystem: '#f97316',      // Orange for current/focused system
        currentSystemBorder: '#ea580c',
        otherSystem: '#1f2937',        // Dark gray for other systems
        otherSystemBorder: '#374151',
        lockedSystem: '#6b7280',       // Gray for locked/inaccessible systems
        
        // Node Colors - Datasets
        currentDataset: '#f97316',     // Orange for current dataset
        currentDatasetBorder: '#ea580c',
        otherDataset: '#1f2937',       // Dark gray for other datasets
        otherDatasetBorder: '#374151',
        
        // Edge Colors
        interfaceLine: '#94a3b8',      // Gray for interface connections (dashed)
        attributeLine: '#64748b',      // Darker gray for attribute lineage (solid)
        upstreamHighlight: '#ef4444',  // Red for upstream
        downstreamHighlight: '#22c55e', // Green for downstream
        
        // Selection & Hover
        selectedBorder: '#248567',     // Green for selected nodes
        hoverBorder: '#34d399',
        
        // Text Colors
        edgeLabelColor: '#6b7280',
        nodeLabel: '#1f2937',
        nodeLabelLight: '#ffffff'
    };

    // ============================================
    // NODE SIZE CONFIGURATION
    // ============================================
    const MAP_NODE_SIZES = {
        system: {
            width: 100,
            height: 60
        },
        dataset: {
            width: 120,
            height: 60
        },
        attribute: {
            width: 100,
            height: 50
        }
    };

    // ============================================
    // CYTOSCAPE STYLES
    // ============================================
    /** Edge curve from toolbar (map-ui); mapId e.g. interfaceMap, datasetRelationshipsMap. */
    function resolveEdgeCurveStyle(mapId) {
        var apis = window._sharedDropdownApis;
        if (!apis || !mapId) return 'bezier';
        var a = apis[mapId];
        if (a && typeof a.getCurveStyle === 'function') return a.getCurveStyle();
        return 'bezier';
    }

    /**
     * @param {string} [mapId] - Pass the map’s toolbar id so edge curve-style matches the dropdown.
     */
    function getCytoscapeStyles(mapId) {
        var curve = resolveEdgeCurveStyle(mapId);
        return [
            // Base Node Style
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
                    'color': MAP_COLORS.nodeLabel,
                    'shape': 'round-rectangle',
                    'width': MAP_NODE_SIZES.system.width,
                    'height': MAP_NODE_SIZES.system.height,
                    'background-image': 'data(backgroundImage)',
                    'background-fit': 'contain',
                    'background-width': '50%',
                    'background-height': '50%',
                    'background-position-y': '30%'
                }
            },

            // Dataset Node - Size and layout
            {
                selector: 'node[group = "dataset"]',
                style: {
                    'width': MAP_NODE_SIZES.dataset.width,
                    'height': MAP_NODE_SIZES.dataset.height,
                    'text-wrap': 'wrap',
                    'text-max-width': MAP_NODE_SIZES.dataset.width * 0.9,
                    'font-size': 11,
                    'line-height': 1.3,
                    'text-margin-y': 8
                }
            },

            // Current Node (Orange) - applies to both system and dataset
            {
                selector: 'node[?isCurrent]',
                style: {
                    'background-color': MAP_COLORS.currentSystem,
                    'border-color': MAP_COLORS.currentSystemBorder,
                    'border-width': 3
                }
            },

            // Locked Node (Inaccessible)
            {
                selector: 'node[?isLocked]',
                style: {
                    'background-color': MAP_COLORS.lockedSystem,
                    'border-style': 'dashed'
                }
            },

            // Attribute Node
            {
                selector: 'node[group = "attribute"]',
                style: {
                    'width': MAP_NODE_SIZES.attribute.width,
                    'height': MAP_NODE_SIZES.attribute.height,
                    'shape': 'ellipse',
                    'background-color': '#9333ea',
                    'border-color': '#7e22ce'
                }
            },

            // Base Edge Style
            {
                selector: 'edge',
                style: {
                    'width': 2,
                    'line-color': 'data(lineColor)',
                    'target-arrow-color': 'data(lineColor)',
                    'target-arrow-shape': 'triangle',
                    'arrow-scale': 1.2,
                    'curve-style': curve,
                    'label': 'data(label)',
                    'font-size': 10,
                    'font-family': 'Inter, system-ui, sans-serif',
                    'text-rotation': 'autorotate',
                    'text-margin-y': -10,
                    'color': MAP_COLORS.edgeLabelColor,
                    'text-background-color': '#ffffff',
                    'text-background-opacity': 0.8,
                    'text-background-padding': '2px'
                }
            },

            // Dashed Edge (Interface only, no attributes)
            {
                selector: 'edge[lineStyle = "dashed"]',
                style: {
                    'line-style': 'dashed',
                    'line-dash-pattern': [6, 3]
                }
            },

            // Solid Edge (Has attribute lineage)
            {
                selector: 'edge[lineStyle = "solid"]',
                style: {
                    'line-style': 'solid',
                    'line-color': MAP_COLORS.attributeLine,
                    'target-arrow-color': MAP_COLORS.attributeLine,
                    'target-arrow-shape': 'triangle',
                    'arrow-scale': 1.2,
                    'z-index': 2
                }
            },

            // Selected Node
            {
                selector: 'node:selected',
                style: {
                    'border-width': 4,
                    'border-color': MAP_COLORS.selectedBorder
                }
            },

            // Hovered Node
            {
                selector: 'node:active',
                style: {
                    'overlay-color': MAP_COLORS.selectedBorder,
                    'overlay-opacity': 0.1
                }
            },

            // Highlighted Upstream Edge (Red)
            {
                selector: '.highlighted-upstream',
                style: {
                    'line-color': MAP_COLORS.upstreamHighlight,
                    'target-arrow-color': MAP_COLORS.upstreamHighlight,
                    'width': 3,
                    'z-index': 999
                }
            },

            // Highlighted Downstream Edge (Green)
            {
                selector: '.highlighted-downstream',
                style: {
                    'line-color': MAP_COLORS.downstreamHighlight,
                    'target-arrow-color': MAP_COLORS.downstreamHighlight,
                    'width': 3,
                    'z-index': 999
                }
            },

            // Dimmed Elements
            {
                selector: '.dimmed',
                style: {
                    'opacity': 0.25
                }
            },

            // Focused Node
            {
                selector: '.focused',
                style: {
                    'border-color': MAP_COLORS.downstreamHighlight,
                    'border-width': 4
                }
            },

            // Overlay highlight - related nodes (simple highlighting: light blue)
            {
                selector: '.overlay-highlight-related',
                style: {
                    'border-color': '#3b82f6',
                    'border-width': 3,
                    'background-color': '#dbeafe'
                }
            },
            // Overlay item click: source node (dark green border)
            {
                selector: '.overlay-highlight-source-node',
                style: {
                    'border-color': '#166534',
                    'border-width': 4,
                    'z-index': 999
                }
            },
            // Overlay item click: related nodes (light green border)
            {
                selector: '.overlay-highlight-related-node',
                style: {
                    'border-color': '#22c55e',
                    'border-width': 3,
                    'background-color': '#dcfce7',
                    'z-index': 998
                }
            },
            // Overlay item click: edges between related nodes (light green)
            {
                selector: '.overlay-highlight-edge',
                style: {
                    'line-color': '#22c55e',
                    'target-arrow-color': '#22c55e',
                    'width': 3,
                    'z-index': 997
                }
            },

            // Reversed edges (layout / lineage direction) — only map lineColor when present (Cytoscape warns otherwise)
            {
                selector: '.reversed-edge',
                style: {
                    'target-arrow-shape': 'none',
                    'source-arrow-shape': 'triangle',
                    'source-arrow-color': MAP_COLORS.attributeLine
                }
            },
            {
                selector: '.reversed-edge[lineColor]',
                style: {
                    'source-arrow-color': 'data(lineColor)'
                }
            },

            // Hidden Node
            {
                selector: '.hidden',
                style: {
                    'display': 'none'
                }
            }
        ];
    }

    // ============================================
    // LAYOUT CONFIGURATIONS (Axon-style)
    // Left to Right: small to medium maps
    // Top to Bottom: flows that aggregate into a central point
    // Organic: larger maps or no overriding direction
    // ============================================
    const MAP_LAYOUTS = {
        'left-to-right': {
            name: 'breadthfirst',
            directed: true,
            padding: 60,
            spacingFactor: 1.8,
            animate: true,
            animationDuration: 500,
            nodeDimensionsIncludeLabels: true
        },
        'right-to-left': {
            name: 'breadthfirst',
            directed: true,
            padding: 60,
            spacingFactor: 1.8,
            animate: true,
            animationDuration: 500,
            nodeDimensionsIncludeLabels: true,
            transform: function(node, pos) {
                return { x: -pos.x, y: pos.y };
            }
        },
        'top-to-bottom': {
            name: 'dagre',
            rankDir: 'TB',
            padding: 60,
            spacingFactor: 1.5,
            animate: true,
            animationDuration: 500,
            nodeDimensionsIncludeLabels: true
        },
        'organic': {
            name: 'cose',
            animate: true,
            animationDuration: 500,
            nodeRepulsion: 2000,
            idealEdgeLength: 50,
            edgeElasticity: 0.45,
            nestingFactor: 0.1,
            gravity: 2.0,
            numIter: 1500,
            initialEnergyOnIncremental: 0.3
        },
        // Legacy alias
        'force': {
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
        }
    };

    /** Layout display labels (Axon) */
    const MAP_LAYOUT_LABELS = {
        'left-to-right': 'Left to Right',
        'top-to-bottom': 'Top to Bottom',
        'organic': 'Organic',
        'right-to-left': 'Right to Left',
        'force': 'Organic'
    };

    function getLayoutConfig(layoutName) {
        const key = layoutName === 'force' ? 'organic' : layoutName;
        return { ...(MAP_LAYOUTS[key] || MAP_LAYOUTS['left-to-right']) };
    }

    // ============================================
    // SHARED LINEAGE LEGEND (same in all maps)
    // ============================================
    /**
     * Returns HTML fragment for the standard lineage legend items (same in all maps).
     * Use in system, dataset, process, glossary, capability, project maps.
     * @param {Object} [colors] - Map colors (defaults to MAP_COLORS). Needs interfaceLine, attributeLine, upstreamHighlight, downstreamHighlight.
     * @param {Object} [opts] - Optional. { includeEucTypes: true } to add EUC-Word node type.
     * @returns {string} HTML string for legend items
     */
    function getLineageLegendHtml(colors, opts) {
        const c = colors || MAP_COLORS;
        const interfaceLine = c.interfaceLine || '#94a3b8';
        const attributeLine = c.attributeLine || '#64748b';
        const upstreamHighlight = c.upstreamHighlight || '#ef4444';
        const downstreamHighlight = c.downstreamHighlight || '#22c55e';
        const eucColor = '#ea580c';
        const eucHtml = (opts && opts.includeEucTypes) ? `
            <div class="legend-item">
                <div class="legend-icon" style="width:20px;height:20px;background:${eucColor};border-radius:2px;display:inline-flex;align-items:center;justify-content:center;color:#fff;font-size:11px;font-weight:bold;">W</div>
                <span>EUC-Word</span>
            </div>
        ` : '';
        return `
            <div class="legend-item">
                <div class="legend-line" style="border-bottom: 2px dashed ${interfaceLine};"></div>
                <span>Interface Lineage</span>
            </div>
            <div class="legend-item">
                <div class="legend-line" style="border-bottom: 2px dashed ${downstreamHighlight};"></div>
                <span>Outbound Interface Lineage</span>
            </div>
            <div class="legend-item">
                <div class="legend-line" style="border-bottom: 2px dashed ${upstreamHighlight};"></div>
                <span>Inbound Interface Lineage</span>
            </div>
            <div class="legend-item">
                <div class="legend-line" style="border-bottom: 2px solid ${attributeLine};"></div>
                <span>Attribute Lineage</span>
            </div>
            ${eucHtml}
        `;
    }

    // ============================================
    // EXPORT TO GLOBAL SCOPE
    // ============================================
    window.SharedMapStyles = {
        colors: MAP_COLORS,
        nodeSizes: MAP_NODE_SIZES,
        getCytoscapeStyles: getCytoscapeStyles,
        getLayoutConfig: getLayoutConfig,
        getLineageLegendHtml: getLineageLegendHtml,
        layouts: MAP_LAYOUTS,
        layoutLabels: MAP_LAYOUT_LABELS
    };

    // Also export as InterfaceMapStyles for backward compatibility
    window.InterfaceMapStyles = window.SharedMapStyles;

})();

