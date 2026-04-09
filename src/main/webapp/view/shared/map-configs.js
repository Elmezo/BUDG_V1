/**
 * map-configs.js
 * ─────────────────────────────────────────────────────────────────────────────
 * Central configuration registry for every map type in the system.
 *
 * Each entry defines the *static* shape of that map:
 *   • engine            — 'cytoscape' | 'leaflet'
 *   • styleModule       — function returning the styles global (lazy getter)
 *   • iconModule        — function returning the icons global (lazy getter)
 *   • mapTypeOptions    — [{ value, label }]  for the type-selector dropdown
 *   • overlayConfig     — overlay-menu column definitions (fed into SharedMapHTML)
 *   • filterConfig      — filter-menu category definitions (fed into SharedMapHTML)
 *   • overlayTypes      — flat list of supported overlay type strings
 *   • apiEndpoints      — descriptive list of backend endpoints used by this map
 *   • defaultMapType    — default selected map type string
 *
 * Dynamic builder functions (apiLoader, nodeBuilder, edgeBuilder) are
 * registered at runtime by each module's own file (or shim) via
 *   window.MapConfigs['key'].apiLoader   = async (id, type, filters) => { ... };
 *   window.MapConfigs['key'].nodeBuilder = (data) => [...];
 *   window.MapConfigs['key'].edgeBuilder = (data) => [...];
 *
 * This keeps map-configs.js lightweight and lets MapEngine look up
 * any config by key:
 *   const cfg = window.MapConfigs['system-lineage'];
 *
 * Usage:
 *   const engine = new MapEngine('system-lineage');   // Cytoscape
 *   const engine = new MapEngine('geographic');        // Leaflet
 */
(function () {
    'use strict';

    // =========================================================================
    // Shared overlay / filter building blocks (reused across map types)
    // =========================================================================

    /** Full system-lineage overlay menu (all 4 columns) */
    var _systemLineageOverlay = {
        columns: [
            {
                header: 'Data',
                items: [
                    { overlay: 'description',        icon: 'fa-info-circle', label: 'Description' },
                    { overlay: 'glossary',           icon: 'fa-book',        label: 'Glossary' },
                    { overlay: 'datasets',           icon: 'fa-layer-group', label: 'Data Sets' },
                    { overlay: 'attributes',         icon: 'fa-th',          label: 'Attributes' },
                    { overlay: 'linking-attributes', icon: 'fa-th',          label: 'Linking Attributes' },
                    { overlay: 'data-quality',       icon: 'fa-bullseye',    label: 'Data Quality' },
                    { overlay: 'data-privacy',       icon: 'fa-lock',        label: 'Data Privacy' }
                ]
            },
            {
                header: 'Business',
                items: [
                    { overlay: 'stakeholders', icon: 'fa-users',           label: 'Stakeholders' },
                    { overlay: 'processes',    icon: 'fa-play',            label: 'Processes' },
                    { overlay: 'projects',     icon: 'fa-project-diagram', label: 'Projects' },
                    { overlay: 'policies',     icon: 'fa-file-alt',        label: 'Policies' }
                ]
            },
            {
                header: 'Organizational',
                items: [
                    { overlay: 'business-area',  icon: 'fa-briefcase', label: 'Business Area' },
                    { overlay: 'products',       icon: 'fa-tag',       label: 'Products' },
                    { overlay: 'legal-entities', icon: 'fa-landmark',  label: 'Legal Entities' }
                ]
            },
            {
                header: 'Regulatory',
                items: [
                    { overlay: 'geography', icon: 'fa-globe', label: 'Geography' }
                ]
            }
        ]
    };

    /** Dataset-lineage overlay menu (data + business only) */
    var _datasetLineageOverlay = {
        columns: [
            {
                header: 'Data',
                items: [
                    { overlay: 'description',  icon: 'fa-info-circle', label: 'Description' },
                    { overlay: 'glossary',     icon: 'fa-book',        label: 'Glossary' },
                    { overlay: 'attributes',   icon: 'fa-th',          label: 'Attributes' },
                    { overlay: 'data-quality', icon: 'fa-bullseye',    label: 'Data Quality' }
                ]
            },
            {
                header: 'Business',
                items: [
                    { overlay: 'stakeholders', icon: 'fa-users', label: 'Stakeholders' },
                    { overlay: 'processes',    icon: 'fa-play',  label: 'Processes' }
                ]
            }
        ]
    };

    /** Glossary-lineage overlay menu */
    var _glossaryLineageOverlay = {
        columns: [
            {
                header: 'Data',
                items: [
                    { overlay: 'description', icon: 'fa-info-circle', label: 'Description' },
                    { overlay: 'attributes',  icon: 'fa-th',          label: 'Attributes' }
                ]
            },
            {
                header: 'Business',
                items: [
                    { overlay: 'stakeholders', icon: 'fa-users', label: 'Stakeholders' }
                ]
            }
        ]
    };

    /** System-lineage filter categories */
    var _systemLineageFilters = {
        categories: [
            {
                header: 'LINKS',
                options: [
                    { id: 'systemInterfaces',   label: 'System Interfaces',    checked: true },
                    { id: 'dataAttributeLinks', label: 'Data Attribute Links', checked: true }
                ]
            },
            { header: 'CLASSIFICATION', dynamic: true, containerId: 'filterClassificationOptions' },
            { header: 'TYPE',           dynamic: true, containerId: 'filterTypeOptions' },
            { header: 'LIFECYCLE',      dynamic: true, containerId: 'filterLifecycleOptions' }
        ]
    };

    /** Dataset-lineage filter categories */
    var _datasetLineageFilters = {
        categories: [
            { header: 'TYPE',      dynamic: true, containerId: 'filterTypeOptions' },
            { header: 'LIFECYCLE', dynamic: true, containerId: 'filterLifecycleOptions' }
        ]
    };

    /** Glossary-lineage filter categories */
    var _glossaryLineageFilters = {
        categories: [
            { header: 'RELATIONSHIP TYPE', dynamic: true, containerId: 'filterRelationshipTypeOptions' },
            { header: 'LIFECYCLE',         dynamic: true, containerId: 'filterLifecycleOptions' }
        ]
    };

    /** Capability filter categories */
    var _capabilityFilters = {
        categories: [
            { header: 'RELATIONSHIPS', dynamic: true, containerId: 'filterRelationshipOptions' },
            { header: 'LIFECYCLE',     dynamic: true, containerId: 'filterLifecycleOptions' }
        ]
    };

    // =========================================================================
    // MapConfigs registry
    // =========================================================================

    /**
     * window.MapConfigs
     *
     * Keys match the 'mapType' string used throughout the application
     * (e.g. 'system-lineage', 'process-data', 'geographic', …).
     *
     * Dynamic fields (apiLoader, nodeBuilder, edgeBuilder) start as null
     * and are registered by each module's init file.
     */
    window.MapConfigs = {

        // ─────────────────────────────────────────────────────────────────────
        // SYSTEM LINEAGE
        // Source: system-interfaces-map.js
        // API:    BUDG_API_SERVICE.getSystemById / getSystemInterfaces /
        //         getDataFlowOutsideInterfaces
        // ─────────────────────────────────────────────────────────────────────
        'system-lineage': {
            engine:         'cytoscape',
            defaultMapType: 'system-lineage',
            styleModule:    function () { return window.InterfaceMapStyles; },
            iconModule:     function () { return window.InterfaceMapIcons; },
            mapTypeOptions: [
                { value: 'system-lineage',  label: 'System Lineage' },
                { value: 'dataset-lineage', label: 'Dataset Lineage' }
            ],
            overlayConfig: {
                systemLineage:  _systemLineageOverlay,
                datasetLineage: _datasetLineageOverlay
            },
            filterConfig: {
                systemLineage:  _systemLineageFilters,
                datasetLineage: _datasetLineageFilters
            },
            overlayTypes: [
                'description', 'glossary', 'datasets', 'attributes', 'linking-attributes',
                'data-quality', 'data-privacy', 'stakeholders', 'processes', 'projects',
                'policies', 'business-area', 'products', 'legal-entities', 'geography'
            ],
            apiEndpoints: [
                'BUDG_API_SERVICE.getSystemById(id)',
                'BUDG_API_SERVICE.getSystemInterfaces(id)',
                'BUDG_API_SERVICE.getDataFlowOutsideInterfaces(id)'
            ],
            // Registered by system-interfaces-map.js
            apiLoader:   null,
            nodeBuilder: null,
            edgeBuilder: null,
            legendBuilder: null
        },

        // ─────────────────────────────────────────────────────────────────────
        // DATASET LINEAGE (standalone dataset view)
        // Source: dataset-relationships-map.js + shared/map/dataset-facet-lineage-graph.js
        // API:    BUDG_API_SERVICE.getDatasetById / getSystemById
        // ─────────────────────────────────────────────────────────────────────
        'dataset-lineage': {
            engine:         'cytoscape',
            defaultMapType: 'dataset-lineage',
            styleModule:    function () { return window.InterfaceMapStyles; },
            iconModule:     function () { return window.InterfaceMapIcons; },
            mapTypeOptions: [
                { value: 'dataset-lineage', label: 'Dataset Lineage' },
                { value: 'system-lineage',  label: 'System Lineage' }
            ],
            overlayConfig: {
                systemLineage:  _systemLineageOverlay,
                datasetLineage: _datasetLineageOverlay
            },
            filterConfig: {
                systemLineage:  _systemLineageFilters,
                datasetLineage: _datasetLineageFilters
            },
            overlayTypes: [
                'description', 'glossary', 'attributes', 'data-quality',
                'stakeholders', 'processes', 'systems'
            ],
            apiEndpoints: [
                'BUDG_API_SERVICE.getDatasetById(id)',
                'BUDG_API_SERVICE.getSystemById(systemId)'
            ],
            apiLoader:    null,
            nodeBuilder:  null,
            edgeBuilder:  null,
            legendBuilder: null
        },

        // ─────────────────────────────────────────────────────────────────────
        // PROCESS – DATA MAP (system/dataset lineage from process context)
        // Source: process-data-map.js + shared/map/process-data-facet-graph.js
        // API:    fetch /api/process-impact/{id}/...
        // ─────────────────────────────────────────────────────────────────────
        'process-data': {
            engine:         'cytoscape',
            defaultMapType: 'system-lineage',
            styleModule:    function () { return window.ProcessMapStyles  || window.InterfaceMapStyles; },
            iconModule:     function () { return window.ProcessMapIcons   || window.InterfaceMapIcons; },
            mapTypeOptions: [
                { value: 'system-lineage',  label: 'System Lineage' },
                { value: 'dataset-lineage', label: 'Dataset Lineage' }
            ],
            overlayConfig: {
                systemLineage:  _systemLineageOverlay,
                datasetLineage: _datasetLineageOverlay
            },
            filterConfig: {
                systemLineage:  _systemLineageFilters,
                datasetLineage: _datasetLineageFilters
            },
            overlayTypes: [
                'description', 'glossary', 'datasets', 'attributes', 'linking-attributes',
                'data-quality', 'data-privacy', 'stakeholders', 'processes', 'projects',
                'policies', 'business-area', 'products', 'legal-entities', 'geography'
            ],
            apiEndpoints: [
                '/api/process-impact/{id}/systems',
                '/api/process-impact/{id}/datasets',
                '/api/process-impact/{id}/attributes'
            ],
            apiLoader:    null,
            nodeBuilder:  null,
            edgeBuilder:  null,
            legendBuilder: null
        },

        // ─────────────────────────────────────────────────────────────────────
        // PROCESS – COMPONENTS MAP (child process steps and their connections)
        // Source: process-flow-maps.js (components)
        // API:    fetch /api/process-impact/{id}/predecessors|successors
        // ─────────────────────────────────────────────────────────────────────
        'process-components': {
            engine:         'cytoscape',
            defaultMapType: 'process-lineage',
            styleModule:    function () { return window.ProcessMapStyles  || window.InterfaceMapStyles; },
            iconModule:     function () { return window.ProcessMapIcons   || window.InterfaceMapIcons; },
            mapTypeOptions: [
                { value: 'process-lineage', label: 'Process Components' }
            ],
            overlayConfig:  {},
            filterConfig: {
                processLineage: {
                    categories: [
                        { header: 'LIFECYCLE', dynamic: true, containerId: 'filterLifecycleOptions' }
                    ]
                }
            },
            overlayTypes: ['stakeholders', 'processes'],
            apiEndpoints: [
                '/api/process/{id}/children',
                '/api/process-impact/{id}/predecessors',
                '/api/process-impact/{id}/successors'
            ],
            apiLoader:    null,
            nodeBuilder:  null,
            edgeBuilder:  null,
            legendBuilder: null
        },

        // ─────────────────────────────────────────────────────────────────────
        // PROCESS – CONTEXT MAP (predecessor/successor lineage for a process)
        // Source: process-flow-maps.js (context)
        // API:    fetch /api/process/{id}/predecessors|successors recursive
        // ─────────────────────────────────────────────────────────────────────
        'process-context': {
            engine:         'cytoscape',
            defaultMapType: 'process-lineage',
            styleModule:    function () { return window.ProcessMapStyles  || window.InterfaceMapStyles; },
            iconModule:     function () { return window.ProcessMapIcons   || window.InterfaceMapIcons; },
            mapTypeOptions: [
                { value: 'process-lineage', label: 'Process Context' }
            ],
            overlayConfig:  {},
            filterConfig: {
                processLineage: {
                    categories: [
                        { header: 'LIFECYCLE', dynamic: true, containerId: 'filterLifecycleOptions' }
                    ]
                }
            },
            overlayTypes: ['stakeholders', 'processes'],
            apiEndpoints: [
                '/api/process/{id}/predecessors',
                '/api/process/{id}/successors'
            ],
            apiLoader:    null,
            nodeBuilder:  null,
            edgeBuilder:  null,
            legendBuilder: null
        },

        // ─────────────────────────────────────────────────────────────────────
        // GLOSSARY – RELATIONSHIPS MAP
        // Source: glossary-relationships-map.js + shared/map/glossary-relationships-facet-graph.js
        // API:    BUDG_API_SERVICE.getGlossaryById + related glossary/dataset/system
        // ─────────────────────────────────────────────────────────────────────
        'glossary-relationships': {
            engine:         'cytoscape',
            defaultMapType: 'glossary-lineage',
            styleModule:    function () { return window.InterfaceMapStyles; },
            iconModule:     function () { return window.InterfaceMapIcons; },
            mapTypeOptions: [
                { value: 'glossary-lineage', label: 'Glossary Lineage' },
                { value: 'dataset-lineage',  label: 'Dataset Lineage' },
                { value: 'system-lineage',   label: 'System Lineage' }
            ],
            overlayConfig: {
                glossaryLineage: _glossaryLineageOverlay,
                datasetLineage:  _datasetLineageOverlay,
                systemLineage:   _systemLineageOverlay
            },
            filterConfig: {
                glossaryLineage: _glossaryLineageFilters,
                datasetLineage:  _datasetLineageFilters,
                systemLineage:   _systemLineageFilters
            },
            overlayTypes: [
                'description', 'stakeholders', 'attributes',
                'glossary', 'datasets', 'projects', 'systems'
            ],
            apiEndpoints: [
                'BUDG_API_SERVICE.getGlossaryById(id)',
                'BUDG_API_SERVICE.getGlossaryRelationships(id)',
                'BUDG_API_SERVICE.getGlossaryDatasets(id)',
                'BUDG_API_SERVICE.getGlossarySystems(id)'
            ],
            apiLoader:    null,
            nodeBuilder:  null,
            edgeBuilder:  null,
            legendBuilder: null
        },

        // ─────────────────────────────────────────────────────────────────────
        // GLOSSARY – DATA MAP (system/dataset lineage from glossary context)
        // Source: glossary-data-map.js + shared/map/glossary-data-facet-graph.js
        // API:    BUDG_API_SERVICE.getGlossaryById + system/dataset lineage APIs
        // ─────────────────────────────────────────────────────────────────────
        'glossary-data': {
            engine:         'cytoscape',
            defaultMapType: 'system-lineage',
            styleModule:    function () { return window.InterfaceMapStyles; },
            iconModule:     function () { return window.InterfaceMapIcons; },
            mapTypeOptions: [
                { value: 'system-lineage',      label: 'System Lineage' },
                { value: 'dataset-lineage',     label: 'Dataset Lineage' },
                { value: 'multi-node-lineage',  label: 'Multi-Node Lineage' }
            ],
            overlayConfig: {
                systemLineage:     _systemLineageOverlay,
                datasetLineage:    _datasetLineageOverlay,
                multiNodeLineage:  _systemLineageOverlay
            },
            filterConfig: {
                systemLineage:    _systemLineageFilters,
                datasetLineage:   _datasetLineageFilters,
                multiNodeLineage: _systemLineageFilters
            },
            overlayTypes: [
                'description', 'glossary', 'attributes', 'linking-attributes',
                'data-quality', 'stakeholders', 'datasets'
            ],
            apiEndpoints: [
                'BUDG_API_SERVICE.getGlossaryById(id)',
                'BUDG_API_SERVICE.getGlossaryDatasets(id)',
                'BUDG_API_SERVICE.getSystemInterfaces(systemId)',
                'BUDG_API_SERVICE.getDataFlowOutsideInterfaces(systemId)'
            ],
            apiLoader:    null,
            nodeBuilder:  null,
            edgeBuilder:  null,
            legendBuilder: null
        },

        // ─────────────────────────────────────────────────────────────────────
        // CAPABILITY – RELATIONSHIPS MAP
        // Source: capability-relationships-map.js + shared/map/capability-facet-lineage-graph.js
        // API:    BUDG_API_SERVICE.getCapabilityById / getCapabilities
        // ─────────────────────────────────────────────────────────────────────
        'capability-lineage': {
            engine:         'cytoscape',
            defaultMapType: 'capability-lineage',
            styleModule:    function () { return window.InterfaceMapStyles; },
            iconModule:     function () { return window.InterfaceMapIcons; },
            mapTypeOptions: [
                { value: 'capability-lineage', label: 'Capability Lineage' }
            ],
            overlayConfig:  {},
            filterConfig: {
                capabilityLineage: _capabilityFilters
            },
            overlayTypes: ['systems', 'processes', 'projects', 'datasets'],
            apiEndpoints: [
                'BUDG_API_SERVICE.getCapabilityById(id)',
                'BUDG_API_SERVICE.getCapabilities()'
            ],
            apiLoader:    null,
            nodeBuilder:  null,
            edgeBuilder:  null,
            legendBuilder: null
        },

        // ─────────────────────────────────────────────────────────────────────
        // PROJECT – DATA MAP (system/dataset lineage from project context)
        // Source: project-data-map.js + shared/map/project-data-facet-graph.js
        // API:    fetch /api/project-impact/{id}/... (similar pattern to process-data)
        // ─────────────────────────────────────────────────────────────────────
        'project-data': {
            engine:         'cytoscape',
            defaultMapType: 'system-lineage',
            styleModule:    function () { return window.InterfaceMapStyles; },
            iconModule:     function () { return window.InterfaceMapIcons; },
            mapTypeOptions: [
                { value: 'system-lineage', label: 'System Lineage' }
            ],
            overlayConfig: {
                systemLineage: _systemLineageOverlay
            },
            filterConfig: {
                systemLineage: {
                    categories: [
                        { header: 'TYPE',      dynamic: true, containerId: 'filterTypeOptions' },
                        { header: 'LIFECYCLE', dynamic: true, containerId: 'filterLifecycleOptions' }
                    ]
                }
            },
            overlayTypes: [
                'description', 'glossary', 'datasets', 'attributes', 'linking-attributes',
                'data-quality', 'data-privacy', 'stakeholders', 'processes', 'projects',
                'policies', 'business-area', 'products', 'legal-entities', 'geography'
            ],
            apiEndpoints: [
                '/api/project-impact/{id}/systems',
                '/api/project-impact/{id}/datasets',
                '/api/project-impact/{id}/attributes'
            ],
            apiLoader:    null,
            nodeBuilder:  null,
            edgeBuilder:  null,
            legendBuilder: null
        },

        // ─────────────────────────────────────────────────────────────────────
        // GEOGRAPHIC MAP (Leaflet-based)
        // Source: map-loader.js + layer-controller.js + marker-controller.js
        // API:    /api/maps/{id} / /api/layers / /api/markers / /api/shapes
        // ─────────────────────────────────────────────────────────────────────
        'geographic': {
            engine:         'leaflet',
            defaultMapType: 'geographic',
            styleModule:    null,
            iconModule:     null,
            mapTypeOptions: [
                { value: 'geographic', label: 'Geographic Map' }
            ],
            overlayConfig:  {},
            filterConfig:   {},
            overlayTypes:   [],
            tileUrl:        'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
            tileAttribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
            defaultZoom:    10,
            defaultCenter:  [51.5, -0.1],
            apiEndpoints: [
                '/api/maps/{id}',
                '/api/maps/{id}/layers',
                '/api/layers/{layerId}/markers',
                '/api/layers/{layerId}/shapes'
            ],
            // Registered by map-loader.js / geographic-map.html
            apiLoader:    null,
            nodeBuilder:  null,
            edgeBuilder:  null,
            legendBuilder: null
        }

    };

    // =========================================================================
    // Alias keys
    // ─────────────────────────────────────────────────────────────────────────
    // Several callers (module shims, toolbar HTML, map type selectors) use
    // the map-type string (e.g. 'process-lineage') as the config key when
    // constructing a MapEngine or looking up config.  Register these aliases
    // so MapConfigs.get('process-lineage') returns a usable config object
    // instead of null / a warning.
    // ─────────────────────────────────────────────────────────────────────────

    // 'process-lineage'  ← used as defaultMapType by process-components & process-context
    window.MapConfigs['process-lineage']   = window.MapConfigs['process-components'];

    // 'glossary-lineage' ← used as defaultMapType by glossary-relationships
    window.MapConfigs['glossary-lineage']  = window.MapConfigs['glossary-relationships'];

    // 'multi-node-lineage' ← used as mapTypeOption value in glossary-data
    window.MapConfigs['multi-node-lineage'] = window.MapConfigs['glossary-data'];

    // 'capability-lineage' is already a direct key — no alias needed.
    // 'project-lineage' is not used as a registry key; project-data handles it.

    // =========================================================================
    // Helper: register a config key's dynamic functions at runtime.
    //
    // Called by each module's own file (or shim) after it has defined its
    // apiLoader, nodeBuilder, and edgeBuilder:
    //
    //   MapConfigs.register('system-lineage', {
    //     apiLoader:    myApiLoader,
    //     nodeBuilder:  myNodeBuilder,
    //     edgeBuilder:  myEdgeBuilder,
    //     legendBuilder: myLegendBuilder   // optional
    //   });
    // =========================================================================
    window.MapConfigs.register = function (configKey, fns) {
        var cfg = window.MapConfigs[configKey];
        if (!cfg) {
            console.warn('[MapConfigs] Unknown config key:', configKey);
            return;
        }
        if (fns.apiLoader)    cfg.apiLoader    = fns.apiLoader;
        if (fns.nodeBuilder)  cfg.nodeBuilder  = fns.nodeBuilder;
        if (fns.edgeBuilder)  cfg.edgeBuilder  = fns.edgeBuilder;
        if (fns.legendBuilder) cfg.legendBuilder = fns.legendBuilder;
    };

    // =========================================================================
    // Helper: get a config entry, with a warning if missing.
    // =========================================================================
    window.MapConfigs.get = function (configKey) {
        var cfg = window.MapConfigs[configKey];
        if (!cfg) {
            console.warn('[MapConfigs] No config registered for key:', configKey);
        }
        return cfg || null;
    };

})();
