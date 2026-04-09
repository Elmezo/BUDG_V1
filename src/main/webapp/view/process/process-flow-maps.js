/**
 * Registers Process Context (Summary) and Process Components maps.
 * Depends on: view/shared/map/process-flow-map-core.js (before this file).
 */
(function() {
    'use strict';

    if (typeof window.createProcessFlowMapController !== 'function') {
        console.error('[process-flow-maps] Load process-flow-map-core.js before this file.');
        return;
    }

    const contextState = {
        initialized: false,
        dagreRegistered: false,
        network: null,
        canvas: null,
        processId: null,
        processData: null,
        layout: 'top-to-bottom',
        layoutDirection: 'forward',
        overlay: 'none',
        overlayColumnsByType: {},
        filterLifecycle: false,
        predecessors: [],
        successors: [],
        lastSelectedNodeData: null,
        hiddenNodes: new Set()
    };

    const contextApi = window.createProcessFlowMapController({
        variant: 'context',
        mapId: 'processContextMap',
        mapEngineKey: 'process-context',
        state: contextState,
        containerId: 'processContextMapContainer',
        canvasId: 'processContextMapCanvas',
        wrapperClass: 'process-context-map-wrapper',
        logPrefix: '[ProcessContextMap]',
        fullscreenTitle: 'Process Context Map',
        exportPrefix: 'process-context-map',
        parentModule: 'ProcessContextMap',
        overlayColPrefix: 'overlayCol_processContextMap_',
        contextMenuDomId: 'processContextMapContextMenu',
        selectors: {
            loading: '[data-process-context-map-loading]',
            details: '[data-process-context-map-details]',
            placeholder: '[data-process-context-map-placeholder]',
            legend: '[data-process-context-map-legend]',
            overlay: '[data-process-context-map-overlay]'
        }
    });

    contextApi.registerMapEngine();

    const contextEngine = new window.MapEngine('process-context');
    Object.assign(contextEngine, {
        getContextMapHtml: contextApi.getShellHtml,
        init: contextApi.init,
        getLegendHtml: contextApi.getLegendHtml,
        getState: contextApi.getState
    });
    window.ProcessContextMap = contextEngine;
})();

(function() {
    'use strict';

    if (typeof window.createProcessFlowMapController !== 'function') {
        console.error('[process-flow-maps] Load process-flow-map-core.js before this file.');
        return;
    }

    const componentsState = {
        initialized: false,
        dagreRegistered: false,
        network: null,
        canvas: null,
        processId: null,
        processData: null,
        children: [],
        childRelations: new Map(),
        layout: 'top-to-bottom',
        layoutDirection: 'forward',
        overlay: 'none',
        overlayColumnsByType: {},
        lastSelectedNodeData: null,
        filterLifecycle: false,
        hiddenNodes: new Set()
    };

    const componentsApi = window.createProcessFlowMapController({
        variant: 'components',
        mapId: 'processComponentsMap',
        mapEngineKey: 'process-components',
        state: componentsState,
        containerId: 'processComponentsMapContainer',
        canvasId: 'processComponentsMapCanvas',
        wrapperClass: 'process-components-map-wrapper',
        logPrefix: '[ProcessComponentsMap]',
        fullscreenTitle: 'Process Components Map',
        exportPrefix: 'process-components-map',
        parentModule: 'ProcessComponentsMap',
        overlayColPrefix: 'overlayCol_processComponentsMap_',
        contextMenuDomId: 'processComponentsMapContextMenu',
        selectors: {
            loading: '[data-process-components-map-loading]',
            details: '[data-process-components-map-details]',
            placeholder: '[data-process-components-map-placeholder]',
            legend: '[data-process-components-map-legend]',
            overlay: '[data-process-components-map-overlay]'
        }
    });

    componentsApi.registerMapEngine();

    const componentsEngine = new window.MapEngine('process-components');
    Object.assign(componentsEngine, {
        getComponentsMapHtml: componentsApi.getShellHtml,
        init: componentsApi.init,
        getLegendHtml: componentsApi.getLegendHtml,
        getState: componentsApi.getState
    });
    window.ProcessComponentsMap = componentsEngine;
})();
