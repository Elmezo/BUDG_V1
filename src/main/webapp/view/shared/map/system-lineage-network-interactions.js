/**
 * system-lineage-network-interactions.js — Cytoscape tap/highlight/context menu and lineage hide/focus helpers
 * for system + dataset lineage maps (Interfaces / Data Map tabs).
 *
 * Load after map-render-utils.js; before system-interfaces-map.js or dataset-relationships-map.js.
 */
(function () {
    'use strict';

    function lineageHighlightConnections(state, node) {
        if (!state.network) return;
        var nodeId = node.id();
        state.network.elements().removeClass('highlighted-upstream highlighted-downstream dimmed');
        state.network.edges().forEach(function (edge) {
            if (edge.target().id() === nodeId) {
                edge.addClass('highlighted-upstream');
            } else if (edge.source().id() === nodeId) {
                edge.addClass('highlighted-downstream');
            }
        });
        state.network.nodes().forEach(function (n) {
            if (n.id() !== nodeId) {
                var isConnected = state.network.edges().some(function (edge) {
                    return (edge.source().id() === n.id() && edge.target().id() === nodeId) ||
                        (edge.target().id() === n.id() && edge.source().id() === nodeId);
                });
                if (!isConnected) {
                    n.addClass('dimmed');
                }
            }
        });
    }

    function lineageResetHighlights(state) {
        if (!state.network) return;
        state.network.elements().removeClass('highlighted-upstream highlighted-downstream dimmed');
        state.highlightedNodeId = null;
    }

    /** Position a fixed context menu from a Cytoscape tap (shared formula + fallback if MapRenderUtils missing). */
    function applyCyTapMenuPosition(menu, evt, canvas, network) {
        var p = null;
        if (window.MapRenderUtils && typeof window.MapRenderUtils.getFixedPositionFromCyTap === 'function') {
            p = window.MapRenderUtils.getFixedPositionFromCyTap({
                canvas: canvas,
                network: network,
                evt: evt,
                offsetX: 20,
                offsetY: 0
            });
        }
        if (!p) {
            var containerRect = canvas.getBoundingClientRect();
            var position = evt.position || evt.cyPosition;
            var renderedPosition = network.pan();
            var zoom = network.zoom();
            p = {
                left: containerRect.left + (position.x * zoom) + renderedPosition.x + 20,
                top: containerRect.top + (position.y * zoom) + renderedPosition.y
            };
        }
        menu.style.position = 'fixed';
        menu.style.left = p.left + 'px';
        menu.style.top = p.top + 'px';
        menu.style.zIndex = '10000';
    }

    /**
     * @param {Object} ctx
     * @param {Object} ctx.state — network, canvas, mapType, systemId, focusedNode, hiddenNodes, hiddenUpstreamNodes, hiddenDownstreamNodes, highlightedNodeId, interfacesData, dataFlowData, linkedDatasets, datasetRelationships
     * @param {function(): Object} ctx.getMapColors
     * @param {function(): Object} ctx.buildSystemLineageGraph
     * @param {function(): Object} ctx.buildDatasetLineageGraph
     * @param {function(Object): void} ctx.renderNetwork
     * @param {function(Object): void} ctx.showEdgeInfo
     * @param {string} [ctx.contextMenuId] default 'interfaceMapContextMenu'
     * @param {string} [ctx.contextMenuClass] default 'interface-map-context-menu'
     */
    window.createSystemLineageNetworkInteractions = function (ctx) {
        var state = ctx.state;
        var getMapColors = ctx.getMapColors;
        var buildSystemLineageGraph = ctx.buildSystemLineageGraph;
        var buildDatasetLineageGraph = ctx.buildDatasetLineageGraph;
        var renderNetwork = ctx.renderNetwork;
        var showEdgeInfo = ctx.showEdgeInfo;
        var contextMenuId = ctx.contextMenuId || 'interfaceMapContextMenu';
        var contextMenuClass = ctx.contextMenuClass || 'interface-map-context-menu';

        function goToObject(objectType, objectId) {
            if (!objectId) return;
            var typeMap = {
                system: '/view/system/',
                dataset: '/view/dataset/',
                process: '/view/process/',
                project: '/view/project/',
                product: '/view/product/',
                policy: '/view/policy/',
                'business-area': '/view/business-area/',
                client: '/view/client/',
                capability: '/view/capability/',
                glossary: '/view/glossary/',
                interface: '/view/system-interface/'
            };
            var baseUrl = typeMap[objectType] || '/view/system/';
            window.open(baseUrl + objectId, '_blank');
        }

        function highlightConnections(node) {
            lineageHighlightConnections(state, node);
        }

        function resetHighlights() {
            lineageResetHighlights(state);
        }

        function hideContextMenu() {
            var menu = document.getElementById(contextMenuId);
            if (menu) {
                menu.remove();
            }
        }

        function handleContextMenuAction(action, nodeId, objectType, objectId) {
            switch (action) {
                case 'go-to-object':
                    goToObject(objectType, objectId);
                    break;
                case 'hide-object':
                    hideNode(nodeId);
                    break;
                case 'focus-object':
                    focusNode(nodeId);
                    break;
                case 'hide-upstream':
                    hideUpstream(nodeId);
                    break;
                case 'show-upstream':
                    showUpstream(nodeId);
                    break;
                case 'hide-downstream':
                    hideDownstream(nodeId);
                    break;
                case 'show-downstream':
                    showDownstream(nodeId);
                    break;
                default:
                    break;
            }
        }

        function showNodeContextMenu(evt, nodeData) {
            hideContextMenu();

            if (nodeData.isLocked) {
                return;
            }

            var menu = document.createElement('div');
            menu.className = contextMenuClass;
            menu.id = contextMenuId;

            var nodeId = nodeData.id;
            var nodeName = nodeData.label;
            var colors = getMapColors();
            var isDatasetLineage = state.mapType === 'dataset-lineage';

            var upstreamHidden = state.hiddenUpstreamNodes.has(nodeId);
            var downstreamHidden = state.hiddenDownstreamNodes.has(nodeId);

            var objectType = 'system';
            var objectId = nodeId;
            if (isDatasetLineage && nodeData.meta && nodeData.meta.datasetId) {
                objectType = 'dataset';
                objectId = nodeData.meta.datasetId;
            } else if (!isDatasetLineage) {
                objectId = nodeId;
            }

            menu.innerHTML =
                '<div class="context-menu-header">' + window.MapRenderUtils.escapeHtml(nodeName) + '</div>' +
                '<div class="context-menu-item" data-action="go-to-object" data-id="' + nodeId + '" data-object-type="' + objectType + '" data-object-id="' + objectId + '">' +
                '<i class="fas fa-external-link-alt"></i> Go to Object</div>' +
                '<div class="context-menu-item" data-action="hide-object" data-id="' + nodeId + '">' +
                '<i class="fas fa-eye-slash"></i> Hide object</div>' +
                '<div class="context-menu-item" data-action="focus-object" data-id="' + nodeId + '">' +
                '<i class="fas fa-crosshairs"></i> ' + (state.focusedNode === nodeId ? 'Undo focus' : 'Focus object') + '</div>' +
                '<div class="context-menu-separator"></div>' +
                '<div class="context-menu-item" data-action="' + (upstreamHidden ? 'show-upstream' : 'hide-upstream') + '" data-id="' + nodeId + '">' +
                '<i class="fas fa-arrow-left" style="color: ' + colors.upstreamHighlight + ';"></i> ' + (upstreamHidden ? 'Show upstream' : 'Hide upstream') + '</div>' +
                '<div class="context-menu-item" data-action="' + (downstreamHidden ? 'show-downstream' : 'hide-downstream') + '" data-id="' + nodeId + '">' +
                '<i class="fas fa-arrow-right" style="color: ' + colors.downstreamHighlight + ';"></i> ' + (downstreamHidden ? 'Show downstream' : 'Hide downstream') + '</div>';

            applyCyTapMenuPosition(menu, evt, state.canvas, state.network);

            document.body.appendChild(menu);

            menu.querySelectorAll('.context-menu-item').forEach(function (item) {
                item.addEventListener('click', function (e) {
                    var t = e.currentTarget;
                    handleContextMenuAction(
                        t.getAttribute('data-action'),
                        t.getAttribute('data-id'),
                        t.getAttribute('data-object-type'),
                        t.getAttribute('data-object-id')
                    );
                    hideContextMenu();
                });
            });

            setTimeout(function () {
                document.addEventListener('mousedown', function _dismiss(e) {
                    var m = document.getElementById(contextMenuId);
                    if (m && !m.contains(e.target)) {
                        m.remove();
                    }
                    document.removeEventListener('mousedown', _dismiss);
                });
            }, 0);
        }

        function hideNode(nodeId) {
            state.hiddenNodes.add(nodeId);
            state.hiddenUpstreamNodes.delete(nodeId);
            state.hiddenDownstreamNodes.delete(nodeId);

            var isDatasetLineage = state.mapType === 'dataset-lineage';
            var graph = isDatasetLineage ? buildDatasetLineageGraph() : buildSystemLineageGraph();
            renderNetwork(graph);
        }

        function focusNode(nodeId) {
            var isDatasetLineage = state.mapType === 'dataset-lineage';

            if (state.focusedNode === nodeId) {
                state.focusedNode = null;
                state.hiddenNodes.clear();
                state.hiddenUpstreamNodes.clear();
                state.hiddenDownstreamNodes.clear();
            } else {
                state.focusedNode = nodeId;

                var connectedNodes = new Set([nodeId]);
                var allNodes = new Set();

                if (isDatasetLineage) {
                    var currentSystemId = String(state.systemId);

                    state.linkedDatasets.forEach(function (info, datasetId) {
                        var systemId = String(info.systemId);
                        if (systemId === currentSystemId) {
                            var datasetNodeId = 'dataset-' + datasetId;
                            connectedNodes.add(datasetNodeId);
                            allNodes.add(datasetNodeId);
                        }
                    });

                    state.datasetRelationships.forEach(function (rel) {
                        var sourceDatasetId = String(rel.sourceDatasetId || rel.sourceDataSetId);
                        var targetDatasetId = String(rel.targetDatasetId || rel.targetDataSetId);
                        var sourceNodeId = 'dataset-' + sourceDatasetId;
                        var targetNodeId = 'dataset-' + targetDatasetId;

                        if (sourceNodeId === nodeId) {
                            connectedNodes.add(targetNodeId);
                        }
                        if (targetNodeId === nodeId) {
                            connectedNodes.add(sourceNodeId);
                        }

                        allNodes.add(sourceNodeId);
                        allNodes.add(targetNodeId);
                    });
                } else {
                    var currentSysId = String(state.systemId);
                    connectedNodes.add(currentSysId);

                    state.interfacesData.forEach(function (iface) {
                        var fromId = String(iface.fromId || iface.sourceSystemId);
                        var toId = String(iface.toId || iface.targetSystemId);
                        if (fromId === nodeId || toId === nodeId) {
                            connectedNodes.add(fromId);
                            connectedNodes.add(toId);
                        }
                        if (iface.fromId) allNodes.add(String(iface.fromId));
                        if (iface.toId) allNodes.add(String(iface.toId));
                    });

                    state.dataFlowData.forEach(function (flow) {
                        var fFrom = String(flow.fromId);
                        var fTo = String(flow.toId);
                        if (fFrom === nodeId || fTo === nodeId) {
                            connectedNodes.add(fFrom);
                            connectedNodes.add(fTo);
                        }
                        if (flow.fromId) allNodes.add(String(flow.fromId));
                        if (flow.toId) allNodes.add(String(flow.toId));
                    });

                    allNodes.add(currentSysId);
                }

                state.hiddenNodes.clear();
                allNodes.forEach(function (id) {
                    if (!connectedNodes.has(id)) {
                        state.hiddenNodes.add(id);
                    }
                });
            }

            var graph = isDatasetLineage ? buildDatasetLineageGraph() : buildSystemLineageGraph();
            renderNetwork(graph);
        }

        function hideUpstream(nodeId) {
            var isDatasetLineage = state.mapType === 'dataset-lineage';
            var nodesToHide = new Set();

            if (isDatasetLineage) {
                state.datasetRelationships.forEach(function (rel) {
                    var sourceDatasetId = String(rel.sourceDatasetId || rel.sourceDataSetId);
                    var targetDatasetId = String(rel.targetDatasetId || rel.targetDataSetId);
                    var sourceNodeId = 'dataset-' + sourceDatasetId;
                    var targetNodeId = 'dataset-' + targetDatasetId;

                    if (targetNodeId === nodeId) {
                        nodesToHide.add(sourceNodeId);
                    }
                });
            } else {
                var curId = String(state.systemId);

                state.interfacesData.forEach(function (iface) {
                    var fromId = String(iface.fromId || iface.sourceSystemId);
                    var toId = String(iface.toId || iface.targetSystemId);
                    if (toId === nodeId && fromId !== curId) {
                        nodesToHide.add(fromId);
                    }
                });

                state.dataFlowData.forEach(function (flow) {
                    var fromId = String(flow.fromId);
                    var toId = String(flow.toId);
                    if (toId === nodeId && fromId !== curId) {
                        nodesToHide.add(fromId);
                    }
                });
            }

            nodesToHide.forEach(function (id) { state.hiddenNodes.add(id); });
            state.hiddenUpstreamNodes.add(nodeId);

            var graph = isDatasetLineage ? buildDatasetLineageGraph() : buildSystemLineageGraph();
            renderNetwork(graph);
        }

        function showUpstream(nodeId) {
            var isDatasetLineage = state.mapType === 'dataset-lineage';
            var nodesToShow = new Set();

            if (isDatasetLineage) {
                state.datasetRelationships.forEach(function (rel) {
                    var sourceDatasetId = String(rel.sourceDatasetId || rel.sourceDataSetId);
                    var targetDatasetId = String(rel.targetDatasetId || rel.targetDataSetId);
                    var sourceNodeId = 'dataset-' + sourceDatasetId;
                    var targetNodeId = 'dataset-' + targetDatasetId;

                    if (targetNodeId === nodeId) {
                        nodesToShow.add(sourceNodeId);
                    }
                });
            } else {
                var curId2 = String(state.systemId);

                state.interfacesData.forEach(function (iface) {
                    var fromId = String(iface.fromId || iface.sourceSystemId);
                    var toId = String(iface.toId || iface.targetSystemId);
                    if (toId === nodeId && fromId !== curId2) {
                        nodesToShow.add(fromId);
                    }
                });

                state.dataFlowData.forEach(function (flow) {
                    var fromId = String(flow.fromId);
                    var toId = String(flow.toId);
                    if (toId === nodeId && fromId !== curId2) {
                        nodesToShow.add(fromId);
                    }
                });
            }

            nodesToShow.forEach(function (id) { state.hiddenNodes.delete(id); });
            state.hiddenUpstreamNodes.delete(nodeId);

            var graph = isDatasetLineage ? buildDatasetLineageGraph() : buildSystemLineageGraph();
            renderNetwork(graph);
        }

        function hideDownstream(nodeId) {
            var isDatasetLineage = state.mapType === 'dataset-lineage';
            var nodesToHide = new Set();

            if (isDatasetLineage) {
                state.datasetRelationships.forEach(function (rel) {
                    var sourceDatasetId = String(rel.sourceDatasetId || rel.sourceDataSetId);
                    var targetDatasetId = String(rel.targetDatasetId || rel.targetDataSetId);
                    var sourceNodeId = 'dataset-' + sourceDatasetId;
                    var targetNodeId = 'dataset-' + targetDatasetId;

                    if (sourceNodeId === nodeId) {
                        nodesToHide.add(targetNodeId);
                    }
                });
            } else {
                var curId3 = String(state.systemId);

                state.interfacesData.forEach(function (iface) {
                    var fromId = String(iface.fromId || iface.sourceSystemId);
                    var toId = String(iface.toId || iface.targetSystemId);
                    if (fromId === nodeId && toId !== curId3) {
                        nodesToHide.add(toId);
                    }
                });

                state.dataFlowData.forEach(function (flow) {
                    var fromId = String(flow.fromId);
                    var toId = String(flow.toId);
                    if (fromId === nodeId && toId !== curId3) {
                        nodesToHide.add(toId);
                    }
                });
            }

            nodesToHide.forEach(function (id) { state.hiddenNodes.add(id); });
            state.hiddenDownstreamNodes.add(nodeId);

            var graph = isDatasetLineage ? buildDatasetLineageGraph() : buildSystemLineageGraph();
            renderNetwork(graph);
        }

        function showDownstream(nodeId) {
            var isDatasetLineage = state.mapType === 'dataset-lineage';
            var nodesToShow = new Set();

            if (isDatasetLineage) {
                state.datasetRelationships.forEach(function (rel) {
                    var sourceDatasetId = String(rel.sourceDatasetId || rel.sourceDataSetId);
                    var targetDatasetId = String(rel.targetDatasetId || rel.targetDataSetId);
                    var sourceNodeId = 'dataset-' + sourceDatasetId;
                    var targetNodeId = 'dataset-' + targetDatasetId;

                    if (sourceNodeId === nodeId) {
                        nodesToShow.add(targetNodeId);
                    }
                });
            } else {
                var curId4 = String(state.systemId);

                state.interfacesData.forEach(function (iface) {
                    var fromId = String(iface.fromId || iface.sourceSystemId);
                    var toId = String(iface.toId || iface.targetSystemId);
                    if (fromId === nodeId && toId !== curId4) {
                        nodesToShow.add(toId);
                    }
                });

                state.dataFlowData.forEach(function (flow) {
                    var fromId = String(flow.fromId);
                    var toId = String(flow.toId);
                    if (fromId === nodeId && toId !== curId4) {
                        nodesToShow.add(toId);
                    }
                });
            }

            nodesToShow.forEach(function (id) { state.hiddenNodes.delete(id); });
            state.hiddenDownstreamNodes.delete(nodeId);

            var graph = isDatasetLineage ? buildDatasetLineageGraph() : buildSystemLineageGraph();
            renderNetwork(graph);
        }

        function setupEventListeners() {
            if (!state.network) return;

            state.network.on('tap', 'node', function (evt) {
                var node = evt.target;
                var nodeData = node.data();

                if (nodeData.isLocked) return;

                hideContextMenu();

                if (state.highlightedNodeId === node.id()) {
                    resetHighlights();
                    return;
                }

                highlightConnections(node);
                state.highlightedNodeId = node.id();
            });

            state.network.on('cxttap', 'node', function (evt) {
                var node = evt.target;
                var nodeData = node.data();
                if (nodeData.isLocked) return;
                showNodeContextMenu(evt, nodeData);
            });

            state.network.on('tap', function (evt) {
                if (evt.target === state.network) {
                    resetHighlights();
                    hideContextMenu();
                }
            });

            state.network.on('tap', 'edge', function (evt) {
                var edge = evt.target;
                showEdgeInfo(edge.data());
            });
        }

        return {
            setupEventListeners: setupEventListeners,
            highlightConnections: highlightConnections,
            resetHighlights: resetHighlights,
            hideContextMenu: hideContextMenu,
            showNodeContextMenu: showNodeContextMenu,
            hideNode: hideNode,
            focusNode: focusNode,
            hideUpstream: hideUpstream,
            showUpstream: showUpstream,
            hideDownstream: hideDownstream,
            showDownstream: showDownstream
        };
    };

    /**
     * @param {Object} ctx
     * @param {Object} ctx.state — DatasetMapState
     * @param {function(): Object} ctx.getMapColors
     * @param {function(): Object} ctx.buildDatasetLineageGraph
     * @param {function(): Object} ctx.buildSystemLineageGraph
     * @param {function(Object): void} ctx.renderNetwork
     * @param {function(Object): void} ctx.showEdgeInfo
     * @param {string} [ctx.contextMenuId] default 'datasetMapContextMenu'
     */
    window.createDatasetFacetLineageNetworkInteractions = function (ctx) {
        var state = ctx.state;
        var getMapColors = ctx.getMapColors;
        var buildDatasetLineageGraph = ctx.buildDatasetLineageGraph;
        var buildSystemLineageGraph = ctx.buildSystemLineageGraph;
        var renderNetwork = ctx.renderNetwork;
        var showEdgeInfo = ctx.showEdgeInfo;
        var contextMenuId = ctx.contextMenuId || 'datasetMapContextMenu';
        var contextMenuClass = ctx.contextMenuClass || 'interface-map-context-menu';

        function refreshGraph() {
            var g = state.mapType === 'dataset-lineage' ? buildDatasetLineageGraph() : buildSystemLineageGraph();
            renderNetwork(g);
        }

        function goToObject(objectType, objectId) {
            if (!objectId) return;
            var typeMap = {
                system: '/view/system/',
                dataset: '/view/dataset/',
                process: '/view/process/',
                project: '/view/project/',
                product: '/view/product/',
                policy: '/view/policy/',
                'business-area': '/view/business-area/',
                client: '/view/client/',
                capability: '/view/capability/',
                glossary: '/view/glossary/',
                interface: '/view/system-interface/'
            };
            var baseUrl = typeMap[objectType] || '/view/dataset/';
            window.open(baseUrl + objectId, '_blank');
        }

        function highlightConnections(node) {
            lineageHighlightConnections(state, node);
        }

        function resetHighlights() {
            lineageResetHighlights(state);
        }

        function hideContextMenu() {
            var menu = document.getElementById(contextMenuId);
            if (menu) menu.remove();
        }

        function handleContextMenuAction(action, nodeId, objectType, objectId) {
            switch (action) {
                case 'go-to-object':
                    goToObject(objectType, objectId);
                    break;
                case 'hide-object':
                    hideNode(nodeId);
                    break;
                case 'focus-object':
                    focusNode(nodeId);
                    break;
                case 'hide-upstream':
                    hideUpstream(nodeId);
                    break;
                case 'show-upstream':
                    showUpstream(nodeId);
                    break;
                case 'hide-downstream':
                    hideDownstream(nodeId);
                    break;
                case 'show-downstream':
                    showDownstream(nodeId);
                    break;
                default:
                    break;
            }
        }

        function showNodeContextMenu(evt, nodeData) {
            hideContextMenu();
            if (nodeData.isLocked) return;

            var menu = document.createElement('div');
            menu.className = contextMenuClass;
            menu.id = contextMenuId;

            var nodeId = nodeData.id;
            var nodeName = nodeData.label;
            var colors = getMapColors();
            var isDatasetLineage = state.mapType === 'dataset-lineage';
            var upstreamHidden = state.hiddenUpstreamNodes && state.hiddenUpstreamNodes.has(nodeId);
            var downstreamHidden = state.hiddenDownstreamNodes && state.hiddenDownstreamNodes.has(nodeId);

            var objectType = 'dataset';
            var objectId = nodeId;
            if (isDatasetLineage && nodeData.meta && nodeData.meta.datasetId) {
                objectType = 'dataset';
                objectId = nodeData.meta.datasetId;
            } else if (!isDatasetLineage && nodeData.meta && nodeData.meta.systemId) {
                objectType = 'system';
                objectId = nodeData.meta.systemId;
            }

            menu.innerHTML =
                '<div class="context-menu-header">' + window.MapRenderUtils.escapeHtml(nodeName) + '</div>' +
                '<div class="context-menu-item" data-action="go-to-object" data-id="' + nodeId + '" data-object-type="' + objectType + '" data-object-id="' + objectId + '">' +
                '<i class="fas fa-external-link-alt"></i> Go to Object</div>' +
                '<div class="context-menu-item" data-action="hide-object" data-id="' + nodeId + '">' +
                '<i class="fas fa-eye-slash"></i> Hide object</div>' +
                '<div class="context-menu-item" data-action="focus-object" data-id="' + nodeId + '">' +
                '<i class="fas fa-crosshairs"></i> ' + (state.focusedNode === nodeId ? 'Undo focus' : 'Focus object') + '</div>' +
                '<div class="context-menu-separator"></div>' +
                '<div class="context-menu-item" data-action="' + (upstreamHidden ? 'show-upstream' : 'hide-upstream') + '" data-id="' + nodeId + '">' +
                '<i class="fas fa-arrow-left" style="color: ' + colors.upstreamHighlight + ';"></i> ' + (upstreamHidden ? 'Show upstream' : 'Hide upstream') + '</div>' +
                '<div class="context-menu-item" data-action="' + (downstreamHidden ? 'show-downstream' : 'hide-downstream') + '" data-id="' + nodeId + '">' +
                '<i class="fas fa-arrow-right" style="color: ' + colors.downstreamHighlight + ';"></i> ' + (downstreamHidden ? 'Show downstream' : 'Hide downstream') + '</div>';

            applyCyTapMenuPosition(menu, evt, state.canvas, state.network);
            document.body.appendChild(menu);

            menu.querySelectorAll('.context-menu-item').forEach(function (item) {
                item.addEventListener('click', function (e) {
                    var t = e.currentTarget;
                    handleContextMenuAction(
                        t.getAttribute('data-action'),
                        t.getAttribute('data-id'),
                        t.getAttribute('data-object-type'),
                        t.getAttribute('data-object-id')
                    );
                    hideContextMenu();
                });
            });

            setTimeout(function () {
                document.addEventListener('mousedown', function _dismiss(e) {
                    var m = document.getElementById(contextMenuId);
                    if (m && !m.contains(e.target)) m.remove();
                    document.removeEventListener('mousedown', _dismiss);
                });
            }, 0);
        }

        function hideNode(nodeId) {
            if (!state.hiddenNodes) state.hiddenNodes = new Set();
            state.hiddenNodes.add(nodeId);
            refreshGraph();
        }

        function focusNode(nodeId) {
            if (!state.hiddenNodes) state.hiddenNodes = new Set();
            if (!state.hiddenUpstreamNodes) state.hiddenUpstreamNodes = new Set();
            if (!state.hiddenDownstreamNodes) state.hiddenDownstreamNodes = new Set();

            if (state.focusedNode === nodeId) {
                state.focusedNode = null;
                state.hiddenNodes.clear();
                state.hiddenUpstreamNodes.clear();
                state.hiddenDownstreamNodes.clear();
            } else {
                state.focusedNode = nodeId;
                var connectedNodes = new Set([nodeId]);
                if (state.mapType === 'dataset-lineage') {
                    connectedNodes.add('dataset-' + state.datasetId);
                } else {
                    connectedNodes.add(String(state.systemId));
                }

                state.datasetRelationships.forEach(function (rel) {
                    var sourceNodeId = state.mapType === 'dataset-lineage'
                        ? 'dataset-' + rel.sourceDatasetId
                        : String(rel.sourceSystemId);
                    var targetNodeId = state.mapType === 'dataset-lineage'
                        ? 'dataset-' + rel.targetDatasetId
                        : String(rel.targetSystemId);
                    if (sourceNodeId === nodeId) connectedNodes.add(targetNodeId);
                    if (targetNodeId === nodeId) connectedNodes.add(sourceNodeId);
                });

                state.linkedDatasets.forEach(function (info, datasetId) {
                    var datasetNodeId = 'dataset-' + datasetId;
                    if (!connectedNodes.has(datasetNodeId)) {
                        state.hiddenNodes.add(datasetNodeId);
                    }
                });

                state.connectedSystems.forEach(function (info, systemId) {
                    if (!connectedNodes.has(String(systemId))) {
                        state.hiddenNodes.add(String(systemId));
                    }
                });
            }

            refreshGraph();
        }

        function hideUpstream(nodeId) {
            if (!state.hiddenUpstreamNodes) state.hiddenUpstreamNodes = new Set();
            state.hiddenUpstreamNodes.add(nodeId);
            state.datasetRelationships.forEach(function (rel) {
                var sourceNodeId = state.mapType === 'dataset-lineage'
                    ? 'dataset-' + rel.sourceDatasetId
                    : String(rel.sourceSystemId);
                var targetNodeId = state.mapType === 'dataset-lineage'
                    ? 'dataset-' + rel.targetDatasetId
                    : String(rel.targetSystemId);
                if (targetNodeId === nodeId) {
                    state.hiddenNodes.add(sourceNodeId);
                }
            });
            refreshGraph();
        }

        function showUpstream(nodeId) {
            if (!state.hiddenUpstreamNodes) state.hiddenUpstreamNodes = new Set();
            state.hiddenUpstreamNodes.delete(nodeId);
            state.datasetRelationships.forEach(function (rel) {
                var sourceNodeId = state.mapType === 'dataset-lineage'
                    ? 'dataset-' + rel.sourceDatasetId
                    : String(rel.sourceSystemId);
                var targetNodeId = state.mapType === 'dataset-lineage'
                    ? 'dataset-' + rel.targetDatasetId
                    : String(rel.targetSystemId);
                if (targetNodeId === nodeId) {
                    state.hiddenNodes.delete(sourceNodeId);
                }
            });
            refreshGraph();
        }

        function hideDownstream(nodeId) {
            if (!state.hiddenDownstreamNodes) state.hiddenDownstreamNodes = new Set();
            state.hiddenDownstreamNodes.add(nodeId);
            state.datasetRelationships.forEach(function (rel) {
                var sourceNodeId = state.mapType === 'dataset-lineage'
                    ? 'dataset-' + rel.sourceDatasetId
                    : String(rel.sourceSystemId);
                var targetNodeId = state.mapType === 'dataset-lineage'
                    ? 'dataset-' + rel.targetDatasetId
                    : String(rel.targetSystemId);
                if (sourceNodeId === nodeId) {
                    state.hiddenNodes.add(targetNodeId);
                }
            });
            refreshGraph();
        }

        function showDownstream(nodeId) {
            if (!state.hiddenDownstreamNodes) state.hiddenDownstreamNodes = new Set();
            state.hiddenDownstreamNodes.delete(nodeId);
            state.datasetRelationships.forEach(function (rel) {
                var sourceNodeId = state.mapType === 'dataset-lineage'
                    ? 'dataset-' + rel.sourceDatasetId
                    : String(rel.sourceSystemId);
                var targetNodeId = state.mapType === 'dataset-lineage'
                    ? 'dataset-' + rel.targetDatasetId
                    : String(rel.targetSystemId);
                if (sourceNodeId === nodeId) {
                    state.hiddenNodes.delete(targetNodeId);
                }
            });
            refreshGraph();
        }

        function setupEventListeners() {
            if (!state.network) return;

            state.network.on('tap', 'node', function (evt) {
                var node = evt.target;
                var nodeData = node.data();
                if (nodeData.isLocked) return;
                hideContextMenu();
                if (state.highlightedNodeId === node.id()) {
                    resetHighlights();
                    return;
                }
                highlightConnections(node);
                state.highlightedNodeId = node.id();
            });

            state.network.on('cxttap', 'node', function (evt) {
                var node = evt.target;
                var nodeData = node.data();
                if (nodeData.isLocked) return;
                showNodeContextMenu(evt, nodeData);
            });

            state.network.on('tap', function (evt) {
                if (evt.target === state.network) {
                    resetHighlights();
                    hideContextMenu();
                }
            });

            state.network.on('tap', 'edge', function (evt) {
                showEdgeInfo(evt.target.data());
            });

            state.network.on('mouseover', 'node', function () {
                state.network.container().style.cursor = 'pointer';
            });
            state.network.on('mouseout', 'node', function () {
                state.network.container().style.cursor = 'default';
            });
        }

        return {
            setupEventListeners: setupEventListeners,
            highlightConnections: highlightConnections,
            resetHighlights: resetHighlights,
            hideContextMenu: hideContextMenu,
            showNodeContextMenu: showNodeContextMenu,
            hideNode: hideNode,
            focusNode: focusNode,
            hideUpstream: hideUpstream,
            showUpstream: showUpstream,
            hideDownstream: hideDownstream,
            showDownstream: showDownstream
        };
    };

    /**
     * Glossary facet Relationships map: single tap selects (highlight + side details + context menu),
     * reset clears overlay DOM selection. No cxttap / upstream-downstream menu items.
     *
     * @param {Object} ctx
     * @param {Object} ctx.state — GlossaryRelationshipsMapState (network, canvas, focusedNode, hiddenNodes)
     * @param {function(): Object} ctx.buildGraph
     * @param {function(Object): void} ctx.renderNetwork
     * @param {function(Object): void} ctx.showEdgeInfo
     * @param {function(Object): void} ctx.showNodeDetails
     * @param {function(): void} ctx.hideNodeDetails
     * @param {string} [ctx.contextMenuId] default 'glossaryRelationshipsMapContextMenu'
     */
    window.createGlossaryRelationshipsMapNetworkInteractions = function (ctx) {
        var state = ctx.state;
        var buildGraph = ctx.buildGraph;
        var renderNetwork = ctx.renderNetwork;
        var showEdgeInfo = ctx.showEdgeInfo;
        var showNodeDetails = ctx.showNodeDetails;
        var hideNodeDetails = ctx.hideNodeDetails;
        var contextMenuId = ctx.contextMenuId || 'glossaryRelationshipsMapContextMenu';
        var contextMenuClass = ctx.contextMenuClass || 'interface-map-context-menu';
        var esc = function (s) { return window.MapRenderUtils.escapeHtml(s); };

        function goToObject(objectType, objectId) {
            if (!objectId) return;
            var typeMap = {
                system: '/view/system/',
                dataset: '/view/dataset/',
                process: '/view/process/',
                project: '/view/project/',
                product: '/view/product/',
                policy: '/view/policy/',
                'business-area': '/view/business-area/',
                client: '/view/client/',
                capability: '/view/capability/',
                glossary: '/view/glossary/',
                interface: '/view/system-interface/'
            };
            var baseUrl = typeMap[objectType] || '/view/glossary/';
            window.open(baseUrl + objectId, '_blank');
        }

        function hideNode(nodeId) {
            state.hiddenNodes.add(nodeId);
            var graph = buildGraph();
            renderNetwork(graph);
        }

        function hideContextMenu() {
            var menu = document.getElementById(contextMenuId);
            if (menu) menu.remove();
        }

        function handleContextMenuAction(action, nodeId, objectType, objectId) {
            if (action === 'go-to-object') goToObject(objectType, objectId);
            else if (action === 'hide-object') hideNode(nodeId);
        }

        function showNodeContextMenu(evt, nodeData) {
            hideContextMenu();
            if (nodeData.isLocked) return;

            var menu = document.createElement('div');
            menu.className = contextMenuClass;
            menu.id = contextMenuId;

            var nodeId = nodeData.id;
            var nodeName = nodeData.glossaryName || nodeData.systemName || nodeData.datasetName || nodeData.label || nodeId;
            var objectType = 'glossary';
            var objectId = '';
            if (nodeData.isGlossary) {
                objectType = 'glossary';
                objectId = nodeData.glossaryId || String(nodeId).replace(/^glossary_/, '');
            } else if (nodeData.isSystem) {
                objectType = 'system';
                objectId = nodeData.systemId || String(nodeId).replace(/^system_/, '');
            } else if (nodeData.isDataset) {
                objectType = 'dataset';
                objectId = nodeData.datasetId || String(nodeId).replace(/^dataset_/, '');
            } else {
                objectId = String(nodeId).replace(/^(glossary_|system_|dataset_)/, '');
            }

            menu.innerHTML =
                '<div class="context-menu-header">' + esc(nodeName) + '</div>' +
                '<div class="context-menu-item" data-action="go-to-object" data-id="' + esc(nodeId) + '" data-object-type="' + esc(objectType) + '" data-object-id="' + esc(String(objectId)) + '">' +
                '<i class="fas fa-external-link-alt"></i> Go to Object</div>' +
                '<div class="context-menu-item" data-action="hide-object" data-id="' + esc(nodeId) + '">' +
                '<i class="fas fa-eye-slash"></i> Hide object</div>';

            applyCyTapMenuPosition(menu, evt, state.canvas, state.network);
            document.body.appendChild(menu);

            menu.querySelectorAll('.context-menu-item').forEach(function (item) {
                item.addEventListener('click', function (e) {
                    var t = e.currentTarget;
                    handleContextMenuAction(
                        t.getAttribute('data-action'),
                        t.getAttribute('data-id'),
                        t.getAttribute('data-object-type'),
                        t.getAttribute('data-object-id')
                    );
                    hideContextMenu();
                });
            });
        }

        function highlightConnections(node) {
            resetHighlights();

            var nodeId = node.id();
            state.focusedNode = nodeId;

            node.addClass('focused');

            node.connectedEdges().forEach(function (edge) {
                var sourceId = edge.source().id();
                var targetId = edge.target().id();
                if (sourceId === nodeId) {
                    edge.addClass('highlighted-downstream');
                    edge.target().addClass('highlighted-downstream');
                } else {
                    edge.addClass('highlighted-upstream');
                    edge.source().addClass('highlighted-upstream');
                }
            });

            state.network.nodes().forEach(function (n) {
                if (n.id() !== nodeId && !n.hasClass('highlighted-upstream') && !n.hasClass('highlighted-downstream')) {
                    n.addClass('dimmed');
                }
            });
        }

        function resetHighlights() {
            if (!state.network) return;
            state.network.elements().removeClass(
                'highlighted-upstream highlighted-downstream dimmed focused overlay-highlight-related overlay-highlight-selected-node'
            );
            state.focusedNode = null;
            document.querySelectorAll('.map-node-overlay-item.overlay-item-selected').forEach(function (el) {
                el.classList.remove('overlay-item-selected');
                el.style.background = '';
                el.style.color = '';
            });
        }

        function setupEventListeners() {
            if (!state.network) return;

            state.network.on('tap', 'node', function (evt) {
                var node = evt.target;
                var nodeData = node.data();
                if (nodeData.isLocked) return;

                highlightConnections(node);
                showNodeDetails(nodeData);
                showNodeContextMenu(evt, nodeData);
            });

            state.network.on('tap', function (evt) {
                if (evt.target === state.network) {
                    resetHighlights();
                    hideNodeDetails();
                    hideContextMenu();
                }
            });

            state.network.on('tap', 'edge', function (evt) {
                showEdgeInfo(evt.target.data());
            });

            state.network.on('mouseover', 'node', function () {
                state.network.container().style.cursor = 'pointer';
            });
            state.network.on('mouseout', 'node', function () {
                state.network.container().style.cursor = 'default';
            });
        }

        return {
            setupEventListeners: setupEventListeners,
            highlightConnections: highlightConnections,
            resetHighlights: resetHighlights,
            hideContextMenu: hideContextMenu,
            showNodeContextMenu: showNodeContextMenu,
            hideNode: hideNode
        };
    };

    /**
     * Glossary Data tab map: system/dataset nodes only — single tap (highlight + side details + context menu).
     * resetHighlights does not clear overlay DOM (data map has no overlay item selection classes on tap).
     *
     * @param {Object} ctx
     * @param {Object} ctx.state — glossary data map state (network, canvas, focusedNode, hiddenNodes)
     * @param {function(): Object} ctx.buildGraph
     * @param {function(Object): void} ctx.renderNetwork
     * @param {function(Object): void} ctx.showEdgeInfo
     * @param {function(Object): void} ctx.showNodeDetails
     * @param {function(): void} ctx.hideNodeDetails
     * @param {string} [ctx.contextMenuId] default 'glossaryDataMapContextMenu'
     */
    window.createGlossaryDataMapNetworkInteractions = function (ctx) {
        var state = ctx.state;
        var buildGraph = ctx.buildGraph;
        var renderNetwork = ctx.renderNetwork;
        var showEdgeInfo = ctx.showEdgeInfo;
        var showNodeDetails = ctx.showNodeDetails;
        var hideNodeDetails = ctx.hideNodeDetails;
        var contextMenuId = ctx.contextMenuId || 'glossaryDataMapContextMenu';
        var contextMenuClass = ctx.contextMenuClass || 'interface-map-context-menu';
        var esc = function (s) { return window.MapRenderUtils.escapeHtml(String(s == null ? '' : s)); };

        function goToObject(objectType, objectId) {
            if (!objectId) return;
            var typeMap = {
                system: '/view/system/',
                dataset: '/view/dataset/',
                process: '/view/process/',
                project: '/view/project/',
                product: '/view/product/',
                policy: '/view/policy/',
                'business-area': '/view/business-area/',
                client: '/view/client/',
                capability: '/view/capability/',
                glossary: '/view/glossary/',
                interface: '/view/system-interface/'
            };
            var baseUrl = typeMap[objectType] || '/view/system/';
            window.open(baseUrl + objectId, '_blank');
        }

        function hideNode(nodeId) {
            state.hiddenNodes.add(nodeId);
            var graph = buildGraph();
            renderNetwork(graph);
        }

        function hideContextMenu() {
            var menu = document.getElementById(contextMenuId);
            if (menu) menu.remove();
        }

        function handleContextMenuAction(action, nodeId, objectType, objectId) {
            if (action === 'go-to-object') goToObject(objectType, objectId);
            else if (action === 'hide-object') hideNode(nodeId);
        }

        function showNodeContextMenu(evt, nodeData) {
            hideContextMenu();
            if (nodeData.isLocked) return;

            var menu = document.createElement('div');
            menu.className = contextMenuClass;
            menu.id = contextMenuId;

            var nodeId = nodeData.id;
            var nodeName = nodeData.systemName || nodeData.datasetName || nodeData.label || nodeId;
            var isSystem = nodeData.isSystem;
            var objectType = isSystem ? 'system' : 'dataset';
            var objectId = (isSystem ? nodeData.systemId : nodeData.datasetId) ||
                String(nodeId).replace(/^(system_|dataset_)/, '');

            menu.innerHTML =
                '<div class="context-menu-header">' + esc(nodeName) + '</div>' +
                '<div class="context-menu-item" data-action="go-to-object" data-id="' + esc(nodeId) + '" data-object-type="' + esc(objectType) + '" data-object-id="' + esc(String(objectId)) + '">' +
                '<i class="fas fa-external-link-alt"></i> Go to Object</div>' +
                '<div class="context-menu-item" data-action="hide-object" data-id="' + esc(nodeId) + '">' +
                '<i class="fas fa-eye-slash"></i> Hide object</div>';

            applyCyTapMenuPosition(menu, evt, state.canvas, state.network);
            document.body.appendChild(menu);

            menu.querySelectorAll('.context-menu-item').forEach(function (item) {
                item.addEventListener('click', function (e) {
                    var t = e.currentTarget;
                    handleContextMenuAction(
                        t.getAttribute('data-action'),
                        t.getAttribute('data-id'),
                        t.getAttribute('data-object-type'),
                        t.getAttribute('data-object-id')
                    );
                    hideContextMenu();
                });
            });
        }

        function highlightConnections(node) {
            resetHighlights();

            var nodeId = node.id();
            state.focusedNode = nodeId;

            node.addClass('focused');

            node.connectedEdges().forEach(function (edge) {
                var sourceId = edge.source().id();
                var targetId = edge.target().id();
                if (sourceId === nodeId) {
                    edge.addClass('highlighted-downstream');
                    edge.target().addClass('highlighted-downstream');
                } else {
                    edge.addClass('highlighted-upstream');
                    edge.source().addClass('highlighted-upstream');
                }
            });

            state.network.nodes().forEach(function (n) {
                if (n.id() !== nodeId && !n.hasClass('highlighted-upstream') && !n.hasClass('highlighted-downstream')) {
                    n.addClass('dimmed');
                }
            });
        }

        function resetHighlights() {
            if (!state.network) return;
            state.network.elements().removeClass(
                'highlighted-upstream highlighted-downstream dimmed focused'
            );
            state.focusedNode = null;
        }

        function setupEventListeners() {
            if (!state.network) return;

            state.network.on('tap', 'node', function (evt) {
                var node = evt.target;
                var nodeData = node.data();
                if (nodeData.isLocked) return;

                highlightConnections(node);
                showNodeDetails(nodeData);
                showNodeContextMenu(evt, nodeData);
            });

            state.network.on('tap', function (evt) {
                if (evt.target === state.network) {
                    resetHighlights();
                    hideNodeDetails();
                    hideContextMenu();
                }
            });

            state.network.on('tap', 'edge', function (evt) {
                showEdgeInfo(evt.target.data());
            });

            state.network.on('mouseover', 'node', function () {
                state.network.container().style.cursor = 'pointer';
            });
            state.network.on('mouseout', 'node', function () {
                state.network.container().style.cursor = 'default';
            });
        }

        return {
            setupEventListeners: setupEventListeners,
            highlightConnections: highlightConnections,
            resetHighlights: resetHighlights,
            hideContextMenu: hideContextMenu,
            showNodeContextMenu: showNodeContextMenu,
            hideNode: hideNode
        };
    };

    /**
     * Process context / components Cytoscape maps: node tap opens side details + context menu (go to / hide).
     * Background tap clears details and menu. No lineage highlight or edge handlers.
     *
     * @param {Object} ctx
     * @param {Object} ctx.state — process flow state (network, canvas, hiddenNodes Set)
     * @param {function(): Object} ctx.buildGraph
     * @param {function(Object): void} ctx.renderNetwork
     * @param {function(Object): void} ctx.showNodeDetails
     * @param {function(): void} ctx.hideNodeDetails
     * @param {string} ctx.contextMenuId — DOM id for the menu element (e.g. from vcfg.contextMenuDomId)
     * @param {string} [ctx.defaultNavBase] default '/view/process/' when object type unknown
     */
    window.createProcessFlowMapNetworkInteractions = function (ctx) {
        var state = ctx.state;
        var buildGraph = ctx.buildGraph;
        var renderNetwork = ctx.renderNetwork;
        var showNodeDetails = ctx.showNodeDetails;
        var hideNodeDetails = ctx.hideNodeDetails;
        var contextMenuId = ctx.contextMenuId;
        var contextMenuClass = ctx.contextMenuClass || 'interface-map-context-menu';
        var defaultNavBase = ctx.defaultNavBase || '/view/process/';
        var esc = function (s) { return window.MapRenderUtils.escapeHtml(String(s == null ? '' : s)); };

        function goToObject(objectType, objectId) {
            if (!objectId) return;
            var typeMap = {
                system: '/view/system/',
                dataset: '/view/dataset/',
                process: '/view/process/',
                project: '/view/project/',
                product: '/view/product/',
                policy: '/view/policy/',
                'business-area': '/view/business-area/',
                client: '/view/client/',
                capability: '/view/capability/',
                glossary: '/view/glossary/',
                interface: '/view/system-interface/'
            };
            var baseUrl = typeMap[objectType] || defaultNavBase;
            window.open(baseUrl + objectId, '_blank');
        }

        function hideNode(nodeId) {
            state.hiddenNodes.add(nodeId);
            var graph = buildGraph();
            renderNetwork(graph);
        }

        function hideContextMenu() {
            var menu = document.getElementById(contextMenuId);
            if (menu) menu.remove();
        }

        function handleContextMenuAction(action, nodeId, objectType, objectId) {
            if (action === 'go-to-object') goToObject(objectType, objectId);
            else if (action === 'hide-object') hideNode(nodeId);
        }

        function showNodeContextMenu(evt, nodeData) {
            hideContextMenu();

            var menu = document.createElement('div');
            menu.className = contextMenuClass;
            menu.id = contextMenuId;

            var nodeId = nodeData.id;
            var nodeName = nodeData.label || nodeId;
            var processId = nodeData.processId || String(nodeId).replace(/^process_/, '');

            menu.innerHTML =
                '<div class="context-menu-header">' + esc(nodeName) + '</div>' +
                '<div class="context-menu-item" data-action="go-to-object" data-id="' + esc(nodeId) + '" data-object-type="process" data-object-id="' + esc(String(processId)) + '">' +
                '<i class="fas fa-external-link-alt"></i> Go to Object</div>' +
                '<div class="context-menu-item" data-action="hide-object" data-id="' + esc(nodeId) + '">' +
                '<i class="fas fa-eye-slash"></i> Hide object</div>';

            applyCyTapMenuPosition(menu, evt, state.canvas, state.network);
            document.body.appendChild(menu);

            menu.querySelectorAll('.context-menu-item').forEach(function (item) {
                item.addEventListener('click', function (e) {
                    var t = e.currentTarget;
                    handleContextMenuAction(
                        t.getAttribute('data-action'),
                        t.getAttribute('data-id'),
                        t.getAttribute('data-object-type'),
                        t.getAttribute('data-object-id')
                    );
                    hideContextMenu();
                });
            });
        }

        function setupEventListeners() {
            if (!state.network) return;

            state.network.on('tap', 'node', function (evt) {
                var node = evt.target;
                var nodeData = node.data();
                showNodeDetails(nodeData);
                showNodeContextMenu(evt, nodeData);
            });

            state.network.on('tap', function (evt) {
                if (evt.target === state.network) {
                    hideNodeDetails();
                    hideContextMenu();
                }
            });
        }

        return {
            setupEventListeners: setupEventListeners,
            hideContextMenu: hideContextMenu,
            showNodeContextMenu: showNodeContextMenu,
            hideNode: hideNode
        };
    };

    /**
     * Capability facet Relationships map: single tap highlights (system-style dimming) + full context menu
     * (focus, hide, upstream/downstream). Optional pan/zoom hooks for overlay positioning.
     *
     * @param {Object} ctx
     * @param {Object} ctx.state — capability map state (network, canvas, capabilityId, focusedNode, hiddenNodes, hiddenUpstreamNodes, hiddenDownstreamNodes, lineageCapabilities)
     * @param {function(): Object} ctx.getMapColors
     * @param {function(): Object} ctx.buildCapabilityLineageGraph
     * @param {function(Object): void} ctx.renderNetwork
     * @param {function(Object): void} ctx.showEdgeInfo
     * @param {function(Object): string} ctx.getNodeIdFromCapability
     * @param {function(): void} [ctx.updateOverlayPositions]
     * @param {string} [ctx.contextMenuId] default 'capabilityMapContextMenu'
     */
    window.createCapabilityLineageNetworkInteractions = function (ctx) {
        var state = ctx.state;
        var getMapColors = ctx.getMapColors;
        var buildCapabilityLineageGraph = ctx.buildCapabilityLineageGraph;
        var renderNetwork = ctx.renderNetwork;
        var showEdgeInfo = ctx.showEdgeInfo;
        var getNodeIdFromCapability = ctx.getNodeIdFromCapability;
        var updateOverlayPositions = ctx.updateOverlayPositions;
        var contextMenuId = ctx.contextMenuId || 'capabilityMapContextMenu';
        var contextMenuClass = ctx.contextMenuClass || 'interface-map-context-menu';
        var esc = function (s) { return window.MapRenderUtils.escapeHtml(String(s == null ? '' : s)); };

        function refreshGraph() {
            var g = buildCapabilityLineageGraph();
            renderNetwork(g);
        }

        function goToObject(objectType, objectId) {
            if (!objectId) return;
            var typeMap = {
                system: '/view/system/',
                dataset: '/view/dataset/',
                capability: '/view/capability/',
                process: '/view/process/',
                project: '/view/project/',
                product: '/view/product/',
                policy: '/view/policy/',
                'business-area': '/view/business-area/',
                client: '/view/client/',
                glossary: '/view/glossary/',
                interface: '/view/system-interface/'
            };
            var baseUrl = typeMap[objectType] || '/view/capability/';
            window.open(baseUrl + objectId, '_blank');
        }

        function highlightConnections(node) {
            lineageHighlightConnections(state, node);
        }

        function resetHighlights() {
            if (!state.network) return;
            state.network.elements().removeClass('highlighted-upstream highlighted-downstream dimmed');
        }

        function hideContextMenu() {
            var menu = document.getElementById(contextMenuId);
            if (menu) menu.remove();
        }

        function handleContextMenuAction(action, nodeId, objectType, objectId) {
            switch (action) {
                case 'go-to-object':
                    goToObject(objectType, objectId);
                    break;
                case 'hide-object':
                    hideNode(nodeId);
                    break;
                case 'focus-object':
                    focusNode(nodeId);
                    break;
                case 'hide-upstream':
                    hideUpstream(nodeId);
                    break;
                case 'show-upstream':
                    showUpstream(nodeId);
                    break;
                case 'hide-downstream':
                    hideDownstream(nodeId);
                    break;
                case 'show-downstream':
                    showDownstream(nodeId);
                    break;
                default:
                    break;
            }
        }

        function showNodeContextMenu(evt, nodeData) {
            hideContextMenu();
            if (nodeData.isLocked) return;

            var menu = document.createElement('div');
            menu.className = contextMenuClass;
            menu.id = contextMenuId;

            var nodeId = nodeData.id;
            var nodeName = nodeData.label;
            var colors = getMapColors();
            var upstreamHidden = state.hiddenUpstreamNodes.has(nodeId);
            var downstreamHidden = state.hiddenDownstreamNodes.has(nodeId);

            var objectType = 'capability';
            var objectId = nodeId;
            if (nodeData.meta && nodeData.meta.capabilityId) {
                objectType = 'capability';
                objectId = nodeData.meta.capabilityId;
            } else if (nodeData.meta && nodeData.meta.systemId) {
                objectType = 'system';
                objectId = nodeData.meta.systemId;
            }

            menu.innerHTML =
                '<div class="context-menu-header">' + esc(nodeName) + '</div>' +
                '<div class="context-menu-item" data-action="go-to-object" data-id="' + esc(nodeId) + '" data-object-type="' + esc(objectType) + '" data-object-id="' + esc(String(objectId)) + '">' +
                '<i class="fas fa-external-link-alt"></i> Go to Object</div>' +
                '<div class="context-menu-item" data-action="hide-object" data-id="' + esc(nodeId) + '">' +
                '<i class="fas fa-eye-slash"></i> Hide object</div>' +
                '<div class="context-menu-item" data-action="focus-object" data-id="' + esc(nodeId) + '">' +
                '<i class="fas fa-crosshairs"></i> ' + (state.focusedNode === nodeId ? 'Undo focus' : 'Focus object') + '</div>' +
                '<div class="context-menu-separator"></div>' +
                '<div class="context-menu-item" data-action="' + (upstreamHidden ? 'show-upstream' : 'hide-upstream') + '" data-id="' + esc(nodeId) + '">' +
                '<i class="fas fa-arrow-left" style="color: ' + (colors.upstreamHighlight || '#ef4444') + ';"></i> ' + (upstreamHidden ? 'Show upstream' : 'Hide upstream') + '</div>' +
                '<div class="context-menu-item" data-action="' + (downstreamHidden ? 'show-downstream' : 'hide-downstream') + '" data-id="' + esc(nodeId) + '">' +
                '<i class="fas fa-arrow-right" style="color: ' + (colors.downstreamHighlight || '#22c55e') + ';"></i> ' + (downstreamHidden ? 'Show downstream' : 'Hide downstream') + '</div>';

            applyCyTapMenuPosition(menu, evt, state.canvas, state.network);
            document.body.appendChild(menu);

            menu.querySelectorAll('.context-menu-item').forEach(function (item) {
                item.addEventListener('click', function (e) {
                    var t = e.currentTarget;
                    handleContextMenuAction(
                        t.getAttribute('data-action'),
                        t.getAttribute('data-id'),
                        t.getAttribute('data-object-type'),
                        t.getAttribute('data-object-id')
                    );
                    hideContextMenu();
                });
            });
        }

        function hideNode(nodeId) {
            state.hiddenNodes.add(nodeId);
            state.hiddenUpstreamNodes.delete(nodeId);
            state.hiddenDownstreamNodes.delete(nodeId);
            refreshGraph();
        }

        function focusNode(nodeId) {
            if (state.focusedNode === nodeId) {
                state.focusedNode = null;
                state.hiddenNodes.clear();
                state.hiddenUpstreamNodes.clear();
                state.hiddenDownstreamNodes.clear();
            } else {
                state.focusedNode = nodeId;
                var connectedNodes = new Set([nodeId]);
                var currentCapabilityId = String(state.capabilityId);
                var currentCap = state.lineageCapabilities.find(function (c) {
                    return String(c.id || c.ID) === currentCapabilityId;
                });
                if (currentCap) {
                    connectedNodes.add(getNodeIdFromCapability(currentCap));
                }

                state.lineageCapabilities.forEach(function (cap) {
                    var parentId = String(cap.parentId || cap.Parent_ID || cap.parent_id || '');
                    var capNodeId = getNodeIdFromCapability(cap);

                    if (capNodeId === nodeId && parentId && parentId !== '0') {
                        var parentCap = state.lineageCapabilities.find(function (c) {
                            return String(c.id || c.ID) === parentId;
                        });
                        if (parentCap) {
                            connectedNodes.add(getNodeIdFromCapability(parentCap));
                        }
                    }

                    if (parentId && parentId !== '0') {
                        var parentCap2 = state.lineageCapabilities.find(function (c) {
                            return String(c.id || c.ID) === parentId;
                        });
                        if (parentCap2) {
                            var parentNodeId = getNodeIdFromCapability(parentCap2);
                            if (parentNodeId === nodeId) {
                                connectedNodes.add(capNodeId);
                            }
                        }
                    }
                });

                state.hiddenNodes.clear();
                state.lineageCapabilities.forEach(function (cap) {
                    var capNodeId = getNodeIdFromCapability(cap);
                    if (!connectedNodes.has(capNodeId)) {
                        state.hiddenNodes.add(capNodeId);
                    }
                });
            }

            refreshGraph();
        }

        function hideUpstream(nodeId) {
            var nodesToHide = new Set();
            state.lineageCapabilities.forEach(function (cap) {
                var parentId = String(cap.parentId || cap.Parent_ID || cap.parent_id || '');
                var capNodeId = getNodeIdFromCapability(cap);
                if (capNodeId === nodeId && parentId && parentId !== '0') {
                    var parentCap = state.lineageCapabilities.find(function (c) {
                        return String(c.id || c.ID) === parentId;
                    });
                    if (parentCap) {
                        nodesToHide.add(getNodeIdFromCapability(parentCap));
                    }
                }
            });
            nodesToHide.forEach(function (id) { state.hiddenNodes.add(id); });
            state.hiddenUpstreamNodes.add(nodeId);
            refreshGraph();
        }

        function showUpstream(nodeId) {
            var nodesToShow = new Set();
            state.lineageCapabilities.forEach(function (cap) {
                var parentId = String(cap.parentId || cap.Parent_ID || cap.parent_id || '');
                var capNodeId = getNodeIdFromCapability(cap);
                if (capNodeId === nodeId && parentId && parentId !== '0') {
                    var parentCap = state.lineageCapabilities.find(function (c) {
                        return String(c.id || c.ID) === parentId;
                    });
                    if (parentCap) {
                        nodesToShow.add(getNodeIdFromCapability(parentCap));
                    }
                }
            });
            nodesToShow.forEach(function (id) { state.hiddenNodes.delete(id); });
            state.hiddenUpstreamNodes.delete(nodeId);
            refreshGraph();
        }

        function hideDownstream(nodeId) {
            var nodesToHide = new Set();
            state.lineageCapabilities.forEach(function (cap) {
                var parentId = String(cap.parentId || cap.Parent_ID || cap.parent_id || '');
                var capNodeId = getNodeIdFromCapability(cap);
                if (parentId && parentId !== '0') {
                    var parentCap = state.lineageCapabilities.find(function (c) {
                        return String(c.id || c.ID) === parentId;
                    });
                    if (parentCap) {
                        var parentNodeId = getNodeIdFromCapability(parentCap);
                        if (parentNodeId === nodeId) {
                            nodesToHide.add(capNodeId);
                        }
                    }
                }
            });
            nodesToHide.forEach(function (id) { state.hiddenNodes.add(id); });
            state.hiddenDownstreamNodes.add(nodeId);
            refreshGraph();
        }

        function showDownstream(nodeId) {
            var nodesToShow = new Set();
            state.lineageCapabilities.forEach(function (cap) {
                var parentId = String(cap.parentId || cap.Parent_ID || cap.parent_id || '');
                var capNodeId = getNodeIdFromCapability(cap);
                if (parentId && parentId !== '0') {
                    var parentCap = state.lineageCapabilities.find(function (c) {
                        return String(c.id || c.ID) === parentId;
                    });
                    if (parentCap) {
                        var parentNodeId = getNodeIdFromCapability(parentCap);
                        if (parentNodeId === nodeId) {
                            nodesToShow.add(capNodeId);
                        }
                    }
                }
            });
            nodesToShow.forEach(function (id) { state.hiddenNodes.delete(id); });
            state.hiddenDownstreamNodes.delete(nodeId);
            refreshGraph();
        }

        function setupEventListeners() {
            if (!state.network) return;

            state.network.on('tap', 'node', function (evt) {
                var node = evt.target;
                var nodeData = node.data();
                if (nodeData.isLocked) return;
                highlightConnections(node);
                showNodeContextMenu(evt, nodeData);
            });

            state.network.on('tap', function (evt) {
                if (evt.target === state.network) {
                    resetHighlights();
                    hideContextMenu();
                }
            });

            state.network.on('tap', 'edge', function (evt) {
                showEdgeInfo(evt.target.data());
            });

            state.network.on('mouseover', 'node', function () {
                state.network.container().style.cursor = 'pointer';
            });
            state.network.on('mouseout', 'node', function () {
                state.network.container().style.cursor = 'default';
            });

            if (typeof updateOverlayPositions === 'function') {
                state.network.on('pan', updateOverlayPositions);
                state.network.on('zoom', updateOverlayPositions);
            }
        }

        return {
            setupEventListeners: setupEventListeners,
            highlightConnections: highlightConnections,
            resetHighlights: resetHighlights,
            hideContextMenu: hideContextMenu,
            showNodeContextMenu: showNodeContextMenu,
            hideNode: hideNode,
            focusNode: focusNode,
            hideUpstream: hideUpstream,
            showUpstream: showUpstream,
            hideDownstream: hideDownstream,
            showDownstream: showDownstream
        };
    };

    /**
     * Project Data Map: node tap opens context menu (Go to system, Hide). No edge handlers.
     * Call attachToCurrentNetwork() after each cytoscape instance is created (e.g. end of renderNetwork).
     *
     * @param {Object} ctx
     * @param {Object} ctx.state — ProjectDataMapState (network, canvas, hiddenNodes)
     * @param {function(): Object} ctx.buildSystemLineageGraph
     * @param {function(Object): void} ctx.renderNetwork
     * @param {string} [ctx.contextMenuId] default 'projectDataMapContextMenu'
     */
    window.createProjectDataMapNetworkInteractions = function (ctx) {
        var state = ctx.state;
        var buildSystemLineageGraph = ctx.buildSystemLineageGraph;
        var renderNetwork = ctx.renderNetwork;
        var contextMenuId = ctx.contextMenuId || 'projectDataMapContextMenu';
        var contextMenuClass = ctx.contextMenuClass || 'interface-map-context-menu';
        var esc = function (s) { return window.MapRenderUtils.escapeHtml(String(s == null ? '' : s)); };

        function goToObject(objectType, objectId) {
            if (!objectId) return;
            var typeMap = {
                system: '/view/system/',
                dataset: '/view/dataset/',
                process: '/view/process/',
                project: '/view/project/',
                product: '/view/product/',
                policy: '/view/policy/',
                'business-area': '/view/business-area/',
                client: '/view/client/',
                capability: '/view/capability/',
                glossary: '/view/glossary/',
                interface: '/view/system-interface/'
            };
            var baseUrl = typeMap[objectType] || '/view/system/';
            window.open(baseUrl + objectId, '_blank');
        }

        function hideNode(nodeId) {
            state.hiddenNodes.add(nodeId);
            var graph = buildSystemLineageGraph();
            renderNetwork(graph);
        }

        function hideContextMenu() {
            var menu = document.getElementById(contextMenuId);
            if (menu) menu.remove();
        }

        function handleContextMenuAction(action, nodeId, objectType, objectId) {
            if (action === 'go-to-object') goToObject(objectType, objectId);
            else if (action === 'hide-object') hideNode(nodeId);
        }

        function showNodeContextMenu(evt, nodeData) {
            hideContextMenu();
            if (nodeData.isLocked) return;

            var menu = document.createElement('div');
            menu.className = contextMenuClass;
            menu.id = contextMenuId;

            var nodeId = nodeData.id;
            var nodeName = nodeData.label || ('System ' + nodeId);

            menu.innerHTML =
                '<div class="context-menu-header">' + esc(nodeName) + '</div>' +
                '<div class="context-menu-item" data-action="go-to-object" data-id="' + esc(String(nodeId)) + '" data-object-type="system" data-object-id="' + esc(String(nodeId)) + '">' +
                '<i class="fas fa-external-link-alt"></i> Go to Object</div>' +
                '<div class="context-menu-item" data-action="hide-object" data-id="' + esc(String(nodeId)) + '">' +
                '<i class="fas fa-eye-slash"></i> Hide object</div>';

            applyCyTapMenuPosition(menu, evt, state.canvas, state.network);
            document.body.appendChild(menu);

            menu.querySelectorAll('.context-menu-item').forEach(function (item) {
                item.addEventListener('click', function (e) {
                    var t = e.currentTarget;
                    handleContextMenuAction(
                        t.getAttribute('data-action'),
                        t.getAttribute('data-id'),
                        t.getAttribute('data-object-type'),
                        t.getAttribute('data-object-id')
                    );
                    hideContextMenu();
                });
            });
        }

        function attachToCurrentNetwork() {
            if (!state.network) return;
            state.network.on('tap', 'node', function (evt) {
                var nodeData = evt.target.data();
                showNodeContextMenu(evt, nodeData);
            });
            state.network.on('tap', function (evt) {
                if (evt.target === state.network) hideContextMenu();
            });
        }

        return {
            attachToCurrentNetwork: attachToCurrentNetwork,
            hideContextMenu: hideContextMenu,
            hideNode: hideNode
        };
    };

    /**
     * Process Data Map: system and dataset nodes (`dataset-` id prefix); hide rebuilds via
     * buildDatasetLineageGraph or buildSystemLineageGraph from state.mapType.
     *
     * @param {Object} ctx
     * @param {Object} ctx.state — ProcessDataMapState (network, canvas, hiddenNodes, mapType)
     * @param {function(): Object} ctx.buildSystemLineageGraph
     * @param {function(): Object} ctx.buildDatasetLineageGraph
     * @param {function(Object): void} ctx.renderNetwork
     * @param {string} [ctx.contextMenuId] default 'processDataMapContextMenu'
     */
    window.createProcessDataMapNetworkInteractions = function (ctx) {
        var state = ctx.state;
        var buildSystemLineageGraph = ctx.buildSystemLineageGraph;
        var buildDatasetLineageGraph = ctx.buildDatasetLineageGraph;
        var renderNetwork = ctx.renderNetwork;
        var contextMenuId = ctx.contextMenuId || 'processDataMapContextMenu';
        var contextMenuClass = ctx.contextMenuClass || 'interface-map-context-menu';
        var esc = function (s) { return window.MapRenderUtils.escapeHtml(String(s == null ? '' : s)); };

        function refreshGraph() {
            var g = state.mapType === 'dataset-lineage' ? buildDatasetLineageGraph() : buildSystemLineageGraph();
            renderNetwork(g);
        }

        function goToObject(objectType, objectId) {
            if (!objectId) return;
            var typeMap = {
                system: '/view/system/',
                dataset: '/view/dataset/',
                process: '/view/process/',
                project: '/view/project/',
                product: '/view/product/',
                policy: '/view/policy/',
                'business-area': '/view/business-area/',
                client: '/view/client/',
                capability: '/view/capability/',
                glossary: '/view/glossary/',
                interface: '/view/system-interface/'
            };
            var baseUrl = typeMap[objectType] || '/view/system/';
            window.open(baseUrl + objectId, '_blank');
        }

        function hideNode(nodeId) {
            state.hiddenNodes.add(nodeId);
            refreshGraph();
        }

        function hideContextMenu() {
            var menu = document.getElementById(contextMenuId);
            if (menu) menu.remove();
        }

        function handleContextMenuAction(action, nodeId, objectType, objectId) {
            if (action === 'go-to-object') goToObject(objectType, objectId);
            else if (action === 'hide-object') hideNode(nodeId);
        }

        function showNodeContextMenu(evt, nodeData) {
            hideContextMenu();
            if (nodeData.isLocked) return;

            var menu = document.createElement('div');
            menu.className = contextMenuClass;
            menu.id = contextMenuId;

            var nodeId = nodeData.id;
            var nodeName = nodeData.label || nodeId;
            var nodeIdStr = String(nodeId);
            var isDataset = nodeIdStr.startsWith('dataset-');
            var objectType = isDataset ? 'dataset' : 'system';
            var objectId = isDataset ? nodeIdStr.replace(/^dataset-/, '') : nodeIdStr;

            menu.innerHTML =
                '<div class="context-menu-header">' + esc(nodeName) + '</div>' +
                '<div class="context-menu-item" data-action="go-to-object" data-id="' + esc(nodeIdStr) + '" data-object-type="' + esc(objectType) + '" data-object-id="' + esc(String(objectId)) + '">' +
                '<i class="fas fa-external-link-alt"></i> Go to Object</div>' +
                '<div class="context-menu-item" data-action="hide-object" data-id="' + esc(nodeIdStr) + '">' +
                '<i class="fas fa-eye-slash"></i> Hide object</div>';

            applyCyTapMenuPosition(menu, evt, state.canvas, state.network);
            document.body.appendChild(menu);

            menu.querySelectorAll('.context-menu-item').forEach(function (item) {
                item.addEventListener('click', function (e) {
                    var t = e.currentTarget;
                    handleContextMenuAction(
                        t.getAttribute('data-action'),
                        t.getAttribute('data-id'),
                        t.getAttribute('data-object-type'),
                        t.getAttribute('data-object-id')
                    );
                    hideContextMenu();
                });
            });
        }

        function attachToCurrentNetwork() {
            if (!state.network) return;
            state.network.on('tap', 'node', function (evt) {
                showNodeContextMenu(evt, evt.target.data());
            });
            state.network.on('tap', function (evt) {
                if (evt.target === state.network) hideContextMenu();
            });
        }

        return {
            attachToCurrentNetwork: attachToCurrentNetwork,
            hideContextMenu: hideContextMenu,
            hideNode: hideNode
        };
    };
})();
