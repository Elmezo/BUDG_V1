/**
 * Unified Map Controller
 * ─────────────────────────────────────────────────────────────────────────────
 * Single file that contains ALL map toolbar controller logic:
 *
 *  ┌─ SECTION 1 ─ Map Type Registry & CSS Auto-Loader
 *  ├─ SECTION 2 ─ SharedMapHTML  (toolbar DOM generator)
 *  ├─ SECTION 3 ─ SharedMapDropdowns  (spacing / edge-style dropdowns)
 *  ├─ SECTION 4 ─ SharedMapControls  (toolbar button wiring)
 *  ├─ SECTION 5 ─ setupMapLegendDropdown
 *  ├─ SECTION 6 ─ exportMapWithOverlays  (PNG composite export)
 *  ├─ SECTION 7 ─ openMapFullscreen  (/view/full_map/… only: live or ?tb= snapshot; map-tab.html redirects)
 *  ├─ SECTION 8 ─ SharedMap  (class-based Cytoscape lifecycle)
 *  ├─ SECTION 9 ─ SharedMapCore  (factory-function Cytoscape lifecycle)
 *  └─ SECTION 10 ─ MapController  (unified factory — ties all sections together)
 *
 * All existing global names are preserved for backward compatibility.
 * Load map-render-utils.js before this file (SharedMap / SharedMapCore use MapRenderUtils).
 *
 * Other files (system-interfaces-map.js, dataset-relationships-map.js, …)
 * continue calling SharedMapHTML, SharedMapControls, SharedMapDropdowns, SharedMap, SharedMapCore.
 *
 * New usage:
 *   const ctrl = MapController.create({ mapId, mapType, entityId, dataLoader, … });
 *   // CSS is auto-loaded, HTML injected, controls wired, map instance returned.
 *
 * Registering a new map type:
 *   MapController.registerMapType('my-map', {
 *     css: ['/assets/css/my-map.css'],
 *     overlayConfig: { … },
 *     filterConfig: { … }
 *   });
 */

(function () {
    'use strict';

    function _mapDbgLog() {
        if (typeof window !== 'undefined' && window.BUDG_DEBUG_SHARED_MAP === true) {
            console.log.apply(console, arguments);
        }
    }
    function _mapDbgWarn() {
        if (typeof window !== 'undefined' && window.BUDG_DEBUG_SHARED_MAP === true) {
            console.warn.apply(console, arguments);
        }
    }

    // =========================================================================
    // SECTION 1 – MAP TYPE REGISTRY & CSS AUTO-LOADER
    // =========================================================================

    /**
     * Registry: maps each mapType key → { css[], overlayConfig, filterConfig }
     * Add new map types here or via MapController.registerMapType().
     */
    var _mapTypeRegistry = {
        'system-lineage': {
            css: ['/view/shared/map/map.css']
        },
        'dataset-lineage': {
            css: ['/view/shared/map/map.css']
        },
        'glossary-lineage': {
            css: ['/view/shared/map/map.css']
        },
        'multi-node-lineage': {
            css: ['/view/shared/map/map.css']
        },
        'capability-lineage': {
            css: ['/view/shared/map/map.css']
        },
        'process-lineage': {
            css: ['/view/shared/map/map.css']
        },
        'project-lineage': {
            css: ['/view/shared/map/map.css']
        }
    };

    /**
     * Inject a <link> stylesheet into <head> if it is not already present.
     * @param {string} href
     */
    function _injectCSS(href) {
        if (!href) return;
        var selector = 'link[rel="stylesheet"][href="' + href + '"]';
        if (!document.querySelector(selector)) {
            var link = document.createElement('link');
            link.rel = 'stylesheet';
            link.href = href;
            document.head.appendChild(link);
        }
    }

    /**
     * Auto-load all CSS files registered for a given mapType.
     * @param {string} mapType  e.g. 'system-lineage'
     */
    function loadMapCSS(mapType) {
        var cfg = _mapTypeRegistry[mapType];
        if (!cfg || !Array.isArray(cfg.css)) return;
        cfg.css.forEach(_injectCSS);
    }


    // =========================================================================
    // SECTION 2 – SharedMapHTML (toolbar DOM generator)
    // NOTE: The authoritative definition is now in map-ui.js (Section 3).
    // This definition is kept as a fallback for pages that load this file
    // without map-ui.js.  The guard prevents overwriting the map-ui.js version.
    // =========================================================================
    /**
     * Generate map HTML structure.
     * @param {Object} config
     * @param {string} config.mapId
     * @param {string} config.defaultMapType
     * @param {Array}  config.mapTypeOptions  — [{ value, label }]
     * @param {Object} config.overlayConfig
     * @param {Object} config.filterConfig
     * @returns {string} HTML string
     */
    window.SharedMapHTML = window.SharedMapHTML || function (config) {
        var mapId          = config.mapId || 'map';
        var includeMapBody = config.includeMapBody !== false;
        var defaultMapType = config.defaultMapType || 'system-lineage';
        var mapTypeOptions = config.mapTypeOptions || [
            { value: 'system-lineage',  label: 'System Lineage' },
            { value: 'dataset-lineage', label: 'Dataset Lineage' }
        ];

        // ── Helpers: map type → suffix / config key ─────────────────────────
        // 'system-lineage'     → suffix 'System',     key 'systemLineage'
        // 'dataset-lineage'    → suffix 'Dataset',    key 'datasetLineage'
        // 'glossary-lineage'   → suffix 'Glossary',   key 'glossaryLineage'
        // 'multi-node-lineage' → suffix 'MultiNode',  key 'multiNodeLineage'
        // 'capability-lineage' → suffix 'Capability', key 'capabilityLineage'
        // 'process-lineage'    → suffix 'Process',    key 'processLineage'
        // Any future type is handled automatically by the algorithm below.
        function _toPascalSuffix(mt) {
            return mt.replace(/-lineage$/, '')
                .split('-')
                .map(function (w) { return w.charAt(0).toUpperCase() + w.slice(1); })
                .join('');
        }
        function _toConfigKey(mt) {
            var p = _toPascalSuffix(mt);
            return p.charAt(0).toLowerCase() + p.slice(1) + 'Lineage';
        }

        // ── Complete default overlay config for every built-in map type ──────
        var overlayConfig = config.overlayConfig || {
            systemLineage: {
                columns: [
                    {
                        header: 'Data',
                        items: [
                            { overlay: 'description',        icon: 'fa-info-circle',      label: 'Description' },
                            { overlay: 'glossary',           icon: 'fa-book',             label: 'Glossary' },
                            { overlay: 'datasets',           icon: 'fa-layer-group',      label: 'Data Sets' },
                            { overlay: 'attributes',         icon: 'fa-th',               label: 'Attributes' },
                            { overlay: 'linking-attributes', icon: 'fa-th',               label: 'Linking Attributes' },
                            { overlay: 'data-quality',       icon: 'fa-bullseye',         label: 'Data Quality' },
                            { overlay: 'data-privacy',       icon: 'fa-lock',             label: 'Data Privacy' }
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
                        items: [{ overlay: 'geography', icon: 'fa-globe', label: 'Geography' }]
                    }
                ]
            },
            datasetLineage: {
                columns: [
                    {
                        header: 'Data',
                        items: [
                            { overlay: 'description',        icon: 'fa-info-circle', label: 'Description' },
                            { overlay: 'glossary',           icon: 'fa-book',        label: 'Glossary' },
                            { overlay: 'attributes',         icon: 'fa-th',          label: 'Attributes' },
                            { overlay: 'linking-attributes', icon: 'fa-th',          label: 'Linking Attributes' },
                            { overlay: 'data-quality',       icon: 'fa-bullseye',    label: 'Data Quality' }
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
                    }
                ]
            },
            // Glossary map: terms relate to each other + carry stakeholders
            glossaryLineage: {
                columns: [
                    {
                        header: 'Data',
                        items: [
                            { overlay: 'description',  icon: 'fa-info-circle', label: 'Description' },
                            { overlay: 'glossary',     icon: 'fa-book',        label: 'Glossary' },
                            { overlay: 'attributes',   icon: 'fa-th',          label: 'Attributes' }
                        ]
                    },
                    {
                        header: 'Business',
                        items: [
                            { overlay: 'stakeholders', icon: 'fa-users',           label: 'Stakeholders' },
                            { overlay: 'processes',    icon: 'fa-play',            label: 'Processes' },
                            { overlay: 'projects',     icon: 'fa-project-diagram', label: 'Projects' }
                        ]
                    }
                ]
            },
            // Multi-node lineage: mixed nodes — only common overlay
            multiNodeLineage: {
                columns: [
                    {
                        header: 'Data',
                        items: [
                            { overlay: 'description', icon: 'fa-info-circle', label: 'Description' }
                        ]
                    }
                ]
            },
            // Capability map: capabilities + related systems, stakeholders, projects
            capabilityLineage: {
                columns: [
                    {
                        header: 'Data',
                        items: [
                            { overlay: 'description', icon: 'fa-info-circle', label: 'Description' },
                            { overlay: 'glossary',    icon: 'fa-book',        label: 'Glossary' },
                            { overlay: 'systems',     icon: 'fa-server',      label: 'Systems' }
                        ]
                    },
                    {
                        header: 'Business',
                        items: [
                            { overlay: 'stakeholders', icon: 'fa-users',           label: 'Stakeholders' },
                            { overlay: 'projects',     icon: 'fa-project-diagram', label: 'Projects' }
                        ]
                    }
                ]
            },
            // Process map: processes + related systems, policies, geography, etc.
            processLineage: {
                columns: [
                    {
                        header: 'Data',
                        items: [
                            { overlay: 'description',  icon: 'fa-info-circle', label: 'Description' },
                            { overlay: 'glossary',     icon: 'fa-book',        label: 'Glossary' },
                            { overlay: 'systems',      icon: 'fa-server',      label: 'Systems' },
                            { overlay: 'data-quality', icon: 'fa-bullseye',    label: 'Data Quality' }
                        ]
                    },
                    {
                        header: 'Business',
                        items: [
                            { overlay: 'stakeholders',  icon: 'fa-users',    label: 'Stakeholders' },
                            { overlay: 'policies',      icon: 'fa-file-alt', label: 'Policies' },
                            { overlay: 'business-area', icon: 'fa-briefcase',label: 'Business Area' }
                        ]
                    },
                    {
                        header: 'Regulatory',
                        items: [
                            { overlay: 'legal-entities', icon: 'fa-landmark', label: 'Legal Entities' },
                            { overlay: 'geography',      icon: 'fa-globe',    label: 'Geography' }
                        ]
                    }
                ]
            },
            // Project map: projects + related systems (same full set)
            projectLineage: {
                columns: [
                    {
                        header: 'Data',
                        items: [
                            { overlay: 'description',        icon: 'fa-info-circle',  label: 'Description' },
                            { overlay: 'glossary',           icon: 'fa-book',         label: 'Glossary' },
                            { overlay: 'datasets',           icon: 'fa-layer-group',  label: 'Data Sets' },
                            { overlay: 'attributes',         icon: 'fa-th',           label: 'Attributes' },
                            { overlay: 'linking-attributes', icon: 'fa-th',           label: 'Linking Attributes' },
                            { overlay: 'data-quality',       icon: 'fa-bullseye',     label: 'Data Quality' },
                            { overlay: 'data-privacy',       icon: 'fa-lock',         label: 'Data Privacy' }
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
                        items: [{ overlay: 'geography', icon: 'fa-globe', label: 'Geography' }]
                    }
                ]
            }
        };

        // ── Complete default filter config for every built-in map type ────────
        var filterConfig = config.filterConfig || {
            systemLineage: {
                categories: [
                    {
                        header: 'LINKS',
                        options: [
                            { id: 'systemInterfaces',   label: 'System Interfaces',    checked: true },
                            { id: 'dataAttributeLinks', label: 'Data Attribute Links', checked: true }
                        ]
                    },
                    { header: 'CLASSIFICATION', dynamic: true, containerId: mapId + 'FilterClassificationOptions' },
                    { header: 'TYPE',           dynamic: true, containerId: mapId + 'FilterTypeOptions' },
                    { header: 'LIFECYCLE',      dynamic: true, containerId: mapId + 'FilterLifecycleOptions' }
                ]
            },
            datasetLineage: {
                categories: [
                    { header: 'TYPE',      dynamic: true, containerId: mapId + 'DatasetFilterTypeOptions' },
                    { header: 'LIFECYCLE', dynamic: true, containerId: mapId + 'DatasetFilterLifecycleOptions' }
                ]
            },
            glossaryLineage: {
                categories: [
                    { header: 'RELATIONSHIP TYPE', dynamic: true, containerId: mapId + 'GlossaryFilterRelationTypeOptions' }
                ]
            },
            multiNodeLineage: {
                categories: [
                    {
                        header: 'LINEAGE',
                        options: [
                            { id: 'multiNodeLineage', label: 'Multi Node Lineage', checked: true }
                        ]
                    }
                ]
            },
            capabilityLineage: {
                categories: [
                    {
                        header: 'CAPABILITY',
                        options: [
                            { id: 'capabilityLineage', label: 'Capability Lineage', checked: true }
                        ]
                    },
                    { header: 'LIFECYCLE', dynamic: true, containerId: mapId + 'CapabilityFilterLifecycleOptions' }
                ]
            },
            processLineage: {
                categories: [
                    {
                        header: 'PROCESS',
                        options: [
                            { id: 'filterLifecycle', label: 'Lifecycle', checked: false }
                        ]
                    }
                ]
            },
            projectLineage: {
                categories: [
                    { header: 'TYPE',      dynamic: true, containerId: mapId + 'ProjectFilterTypeOptions' },
                    { header: 'LIFECYCLE', dynamic: true, containerId: mapId + 'ProjectFilterLifecycleOptions' }
                ]
            }
        };

        // Collect which map types are actually configured (for conditional menu generation)
        var configuredMapTypes = {};
        mapTypeOptions.forEach(function (o) { configuredMapTypes[o.value] = true; });

        // Generate map type <option> tags
        var mapTypeOptionsHtml = mapTypeOptions.map(function (opt) {
            return '<option value="' + opt.value + '"' + (opt.value === defaultMapType ? ' selected' : '') + '>' + opt.label + '</option>';
        }).join('');

        // ── Overlay menu builder ─────────────────────────────────────────────
        // Fully dynamic: looks up config by computed key, derives ID suffix from map type.
        // Supports ANY map type — no hardcoded type list.
        function generateOverlayMenu(mapType) {
            if (!configuredMapTypes[mapType]) return '';
            var configKey  = _toConfigKey(mapType);        // e.g. 'capabilityLineage'
            var menuSuffix = _toPascalSuffix(mapType);     // e.g. 'Capability'
            var overlayData = overlayConfig[configKey];
            if (!overlayData || !overlayData.columns) return '';

            var columnsHtml = overlayData.columns.map(function (col) {
                return '<div class="overlay-menu-column">'
                    + '<div class="overlay-menu-header">' + col.header + '</div>'
                    + col.items.map(function (item) {
                        return '<div class="overlay-menu-item" data-overlay="' + item.overlay + '">'
                            + '<i class="fas ' + item.icon + '"></i> ' + item.label + '</div>';
                    }).join('')
                    + '</div>';
            }).join('');

            return '<div class="map-overlay-menu" id="' + mapId + 'OverlayMenu' + menuSuffix + '" data-map-type="' + mapType + '">'
                + '<div class="overlay-menu-grid">' + columnsHtml + '</div>'
                + '<div class="overlay-menu-footer">'
                + '<button type="button" class="overlay-clear-btn" id="' + mapId + 'ClearOverlaysBtn' + menuSuffix + '">Clear Overlays</button>'
                + '</div></div>';
        }

        // ── Filter menu builder ──────────────────────────────────────────────
        // Fully dynamic: same algorithm as overlay menu.
        function generateFilterMenu(mapType) {
            if (!configuredMapTypes[mapType]) return '';
            var configKey  = _toConfigKey(mapType);
            var menuSuffix = _toPascalSuffix(mapType);
            var filterData = filterConfig[configKey];
            if (!filterData || !filterData.categories) return '';

            var categoriesHtml = filterData.categories.map(function (cat, idx) {
                var optionsHtml = cat.dynamic
                    ? '<div id="' + cat.containerId + '"><!-- Dynamic options will be added here --></div>'
                    : (cat.options || []).map(function (opt) {
                        var linkAttr = (opt.id === 'systemInterfaces' || opt.id === 'dataAttributeLinks')
                            ? ' data-filter-link="' + opt.id + '"'
                            : '';
                        return '<div class="map-filter-option' + (opt.hidden ? ' map-filter-option--hidden' : '') + '">'
                            + '<input type="checkbox" id="filter' + mapId + opt.id + '"' + linkAttr + (opt.checked ? ' checked' : '') + '>'
                            + '<label for="filter' + mapId + opt.id + '">' + opt.label + '</label></div>';
                    }).join('');
                return (idx > 0 ? '<div class="map-filter-separator"></div>' : '')
                    + '<div class="map-filter-category">'
                    + '<div class="map-filter-category-header">' + cat.header + '</div>'
                    + optionsHtml + '</div>';
            }).join('');

            return '<div class="map-filter-menu" id="' + mapId + 'FilterMenu' + menuSuffix + '" data-map-type="' + mapType + '">'
                + categoriesHtml + '</div>';
        }

        // Generate overlay and filter menus for ALL configured map types (dynamic loop)
        var allOverlayMenus = mapTypeOptions.map(function (opt) { return generateOverlayMenu(opt.value); }).join('');
        var allFilterMenus  = mapTypeOptions.map(function (opt) { return generateFilterMenu(opt.value); }).join('');

        return '<div class="map-section map-section--grid-span" id="' + mapId + 'Section">'
            + '<div class="map-section-header">'
            + '<div class="map-section-title">MAP</div>'
            + '<button type="button" class="map-collapse-btn" id="' + mapId + 'CollapseBtn" title="Collapse/Expand"><i class="fas fa-minus"></i></button>'
            + '</div>'
            // ── Toolbar ────────────────────────────────────────────────────
            + '<div class="interface-map-toolbar">'
            // Map type
            + '<div class="map-control-group"><label>Map type:</label>'
            + '<select id="' + mapId + 'TypeSelect" class="map-select">' + mapTypeOptionsHtml + '</select></div>'
            // Layout
            + '<div class="map-control-group"><label>Layout:</label>'
            + '<div class="map-layout-controls">'
            + (typeof window.SharedMapLayoutRichControlsHtml === 'function'
                ? window.SharedMapLayoutRichControlsHtml(mapId)
                : '<select id="' + mapId + 'LayoutSelect" class="map-select">'
                + '<option value="top-to-bottom" selected>Top-To-Bottom</option>'
                + '<option value="left-to-right">Left-To-Right</option>'
                + '<option value="right-to-left">Right-To-Left</option>'
                + '<option value="force">Force Directed</option>'
                + '</select>'
                + '<div class="map-btn-dropdown-wrapper">'
                + '<button type="button" class="map-toolbar-btn-sm" id="' + mapId + 'Spacing" title="Node spacing">'
                + '<i class="fas fa-expand-arrows-alt" id="' + mapId + 'SpacingIcon"></i>'
                + '<i class="fas fa-chevron-down map-toolbar-chevron"></i></button>'
                + '<div class="map-btn-dropdown-menu" id="' + mapId + 'SpacingMenu">'
                + '<div class="map-btn-dropdown-item" data-spacing="compact">Compact</div>'
                + '<div class="map-btn-dropdown-item active" data-spacing="normal">Normal</div>'
                + '<div class="map-btn-dropdown-item" data-spacing="spacey">Spacey</div>'
                + '</div></div>'
                + '<div class="map-btn-dropdown-wrapper">'
                + '<button type="button" class="map-toolbar-btn-sm" id="' + mapId + 'EdgeStyle" title="Edge routing style">'
                + '<i class="fas fa-arrow-right" id="' + mapId + 'EdgeStyleIcon"></i>'
                + '<i class="fas fa-chevron-down map-toolbar-chevron"></i></button>'
                + '<div class="map-btn-dropdown-menu" id="' + mapId + 'EdgeStyleMenu">'
                + '<div class="map-btn-dropdown-item" data-edge-style="angle">Angle</div>'
                + '<div class="map-btn-dropdown-item" data-edge-style="square">Square</div>'
                + '<div class="map-btn-dropdown-item active" data-edge-style="direct">Direct</div>'
                + '<div class="map-btn-dropdown-item" data-edge-style="loop">Loop</div>'
                + '<div class="map-btn-dropdown-item" data-edge-style="top-down">Top-Down</div>'
                + '<div class="map-btn-dropdown-item" data-edge-style="left-right">Left-Right</div>'
                + '</div></div>')
            + '</div></div>'
            // Hops
            + '<div class="map-control-group map-hops-group" id="' + mapId + 'HopsGroup"><label>Hops:</label>'
            + '<input type="number" id="' + mapId + 'HopsCount" class="map-hops-input" min="1" max="99" value="15" title="Upstream/downstream lineage depth (1-99, recommend 15)"></div>'
            // Overlay
            + '<div class="map-control-group"><label>Overlay:</label>'
            + '<div class="map-overlay-controls"><div class="map-overlay-dropdown">'
            + '<button type="button" class="map-select-btn" id="' + mapId + 'OverlayBtn">'
            + '<span id="' + mapId + 'OverlayBtnText">None</span>'
            + '<i class="fas fa-chevron-down"></i></button>'
            + allOverlayMenus
            + '</div>'
            + '<button type="button" class="map-toolbar-btn-sm" id="' + mapId + 'OverlayGrid" title="Overlay fields as columns">'
            + '<i class="fas fa-th"></i><i class="fas fa-chevron-down map-toolbar-chevron"></i></button>'
            + '<div class="map-overlay-columns-menu map-filter-menu map-menu--closed" id="' + mapId + 'OverlayColumnsMenu">'
            + '<div class="map-filter-category"><div class="map-filter-category-header">OVERLAY FIELDS AS COLUMNS</div>'
            + '<div id="' + mapId + 'OverlayColumnsOptions" class="map-filter-options-container"></div>'
            + '</div></div></div></div>'
            // Filters
            + '<div class="map-control-group"><label>Filters:</label>'
            + '<div class="map-filter-dropdown">'
            + '<button type="button" class="map-select-btn" id="' + mapId + 'FilterBtn">'
            + '<span id="' + mapId + 'FilterBtnText">All selected</span>'
            + '<i class="fas fa-chevron-down"></i></button>'
            + allFilterMenus
            + '</div></div>'
            // Toolbar buttons
            + '<div class="map-toolbar-buttons">'
            + '<button type="button" class="map-toolbar-btn" id="' + mapId + 'ToggleLabels" title="Toggle labels"><i class="fas fa-exchange-alt"></i></button>'
            + '<div class="map-toolbar-separator"></div>'
            + '<button type="button" class="map-toolbar-btn" id="' + mapId + 'ZoomIn" title="Zoom in"><i class="fas fa-search-plus"></i></button>'
            + '<button type="button" class="map-toolbar-btn" id="' + mapId + 'ZoomOut" title="Zoom out"><i class="fas fa-search-minus"></i></button>'
            + '<div class="map-toolbar-separator"></div>'
            + '<button type="button" class="map-toolbar-btn" id="' + mapId + 'Redraw" title="Redraw"><i class="fas fa-sync-alt"></i></button>'
            + '<button type="button" class="map-toolbar-btn" id="' + mapId + 'Reset" title="Reset"><i class="fas fa-undo"></i></button>'
            + '<button type="button" class="map-toolbar-btn" id="' + mapId + 'Export" title="Export as PNG"><i class="fas fa-save"></i></button>'
            + '<div class="map-toolbar-separator"></div>'
            + '<button type="button" class="map-toolbar-btn" id="' + mapId + 'Navigator" title="Map navigator"><i class="fas fa-eye"></i></button>'
            + '<button type="button" class="map-toolbar-btn" id="' + mapId + 'Fullscreen" title="Fullscreen"><i class="fas fa-external-link-alt"></i></button>'
            + '<button type="button" class="map-toolbar-btn" id="' + mapId + 'Legend" title="Open the Legend" aria-label="Open the Legend – refer to the legend to identify the symbols used in the map (Insight Maps Palette)"><i class="fas fa-list-ul"></i></button>'
            + '</div>'
            + '</div>'
            + (includeMapBody
                ? ('<div class="interface-map-body">'
                    + '<div class="interface-map-canvas" id="' + mapId + 'Canvas">'
                    + '<div class="interface-map-loading map-js-hidden" data-map-loading>'
                    + '<i class="fas fa-spinner fa-spin"></i><span>Building lineage map...</span></div></div>'
                    + '<div class="interface-map-side-panel map-js-hidden" id="' + mapId + 'SidePanel">'
                    + '<div class="map-side-panel-section">'
                    + '<h4><i class="fas fa-info-circle"></i> Selection</h4>'
                    + '<div class="selection-placeholder" data-map-placeholder>Select a node to see its details.</div>'
                    + '<div class="selection-info map-js-hidden" data-map-details></div>'
                    + '</div>'
                    + '<div class="map-side-panel-section">'
                    + '<h4 class="map-legend-palette-title"><i class="fas fa-layer-group"></i> Insight Maps Palette</h4>'
                    + '<div data-map-legend></div>'
                    + '</div></div>'
                    + '</div>')
                : '')
            + '</div>';
    };


    // =========================================================================
    // SECTION 3 – SharedMapDropdowns (spacing / edge-style dropdowns)
    // =========================================================================
    // map-ui.js is canonical when loaded first; keep this block only as fallback.
    if (typeof window.SharedMapDropdowns !== 'function') {
    /**
     * Initialise spacing and edge-style dropdown menus for a map instance.
     * @param {Object} opts
     * @param {string}   opts.mapId
     * @param {Function} opts.getNetwork  — returns the cytoscape instance or null
     * @param {Function} opts.setLayout   — called with direction string when user picks one
     * @param {Function} [opts.getCanvas] — returns canvas DOM element to resize
     * @param {Element}  [opts.mapRoot]   — scope for menu close / contains(); default mapId+'Section' or document
     * @returns {Object} API: getSpacingFactor, getSpacingPadding, getCurveStyle, getSpacing, getEdgeStyle
     */
    window.SharedMapDropdowns = function (opts) {
        var mapId      = opts.mapId;
        var getNetwork = opts.getNetwork;
        var setLayout  = opts.setLayout;
        var mapRoot    = opts.mapRoot || document.getElementById(mapId + 'Section') || document.getElementById(mapId + 'Wrapper') || document;
        var byId       = function (id) { return mapRoot.querySelector('#' + id) || document.getElementById(id); };

        var spacingBtn  = byId(mapId + 'Spacing');
        var spacingMenu = byId(mapId + 'SpacingMenu');
        var edgeBtn     = byId(mapId + 'EdgeStyle');
        var edgeMenu    = byId(mapId + 'EdgeStyleMenu');
        var layoutSel   = byId(mapId + 'LayoutSelect');

        var currentSpacing   = 'normal';
        var currentEdgeStyle = 'direct';
        if (spacingMenu) {
            var initSp = spacingMenu.querySelector('.map-btn-dropdown-item.active[data-spacing]');
            if (initSp && initSp.dataset.spacing) currentSpacing = initSp.dataset.spacing;
        }
        if (edgeMenu) {
            var initEs = edgeMenu.querySelector('.map-btn-dropdown-item.active[data-edge-style]');
            if (initEs && initEs.dataset.edgeStyle) currentEdgeStyle = initEs.dataset.edgeStyle;
        }

        function closeMenus() {
            mapRoot.querySelectorAll('.map-btn-dropdown-menu.show').forEach(function (m) { m.classList.remove('show'); });
        }

        document.addEventListener('click', function (e) {
            if (!mapRoot.contains(e.target) || !e.target.closest('.map-btn-dropdown-wrapper')) closeMenus();
        });

        function spacingFactor() {
            if (currentSpacing === 'compact') return 1.2;
            if (currentSpacing === 'spacey')  return 3.5;
            return 2.2;
        }

        function spacingPadding() {
            if (currentSpacing === 'compact') return 40;
            if (currentSpacing === 'spacey')  return 150;
            return 90;
        }

        function curveStyle() {
            switch (currentEdgeStyle) {
                case 'angle':      return 'segments';
                case 'square':     return 'taxi';
                case 'loop':       return 'unbundled-bezier';
                case 'top-down':   return 'bezier';
                case 'left-right': return 'bezier';
                default:           return 'bezier';
            }
        }

        if (spacingBtn && spacingMenu) {
            spacingBtn.addEventListener('click', function (e) {
                e.stopPropagation();
                var wasOpen = spacingMenu.classList.contains('show');
                closeMenus();
                if (!wasOpen) spacingMenu.classList.add('show');
            });
            spacingMenu.addEventListener('click', function (e) {
                var item = e.target.closest('.map-btn-dropdown-item');
                if (!item) return;
                var sp = item.dataset.spacing;
                if (!sp) return;
                currentSpacing = sp;
                spacingMenu.querySelectorAll('.map-btn-dropdown-item').forEach(function (it) {
                    it.classList.toggle('active', it.dataset.spacing === sp);
                });
                if (typeof setLayout === 'function') setLayout(layoutSel ? layoutSel.value : 'left-to-right');
                closeMenus();
            });
        }

        if (edgeBtn && edgeMenu) {
            edgeBtn.addEventListener('click', function (e) {
                e.stopPropagation();
                var wasOpen = edgeMenu.classList.contains('show');
                closeMenus();
                if (!wasOpen) edgeMenu.classList.add('show');
            });
            edgeMenu.addEventListener('click', function (e) {
                var item = e.target.closest('.map-btn-dropdown-item');
                if (!item) return;
                var es = item.dataset.edgeStyle;
                if (!es) return;
                currentEdgeStyle = es;
                edgeMenu.querySelectorAll('.map-btn-dropdown-item').forEach(function (it) {
                    it.classList.toggle('active', it.dataset.edgeStyle === es);
                });
                var cy = typeof getNetwork === 'function' ? getNetwork() : null;
                if (cy) cy.edges().style('curve-style', curveStyle());
                if (es === 'top-down')  { if (layoutSel) layoutSel.value = 'top-to-bottom';  if (typeof setLayout === 'function') setLayout('top-to-bottom'); }
                else if (es === 'left-right') { if (layoutSel) layoutSel.value = 'left-to-right'; if (typeof setLayout === 'function') setLayout('left-to-right'); }
                closeMenus();
            });
        }

        if (layoutSel) {
            layoutSel.addEventListener('change', function () {
                if (typeof setLayout === 'function') setLayout(layoutSel.value);
            });
        }

        var api = {
            getSpacingFactor:  spacingFactor,
            getSpacingPadding: spacingPadding,
            getCurveStyle:     curveStyle,
            getSpacing:        function () { return currentSpacing; },
            getEdgeStyle:      function () { return currentEdgeStyle; }
        };

        if (spacingBtn) spacingBtn._dropdownApi = api;
        window._sharedDropdownApis = window._sharedDropdownApis || {};
        window._sharedDropdownApis[mapId] = api;

        return api;
    };
    }

    // =========================================================================
    // SECTION 4 – SharedMapControls (toolbar button wiring)
    // =========================================================================
    /**
     * Wire all map toolbar controls to a mapInstance.
     * @param {Object} config
     * @param {string}   config.mapId
     * @param {Object}   config.mapInstance
     * @param {Function} [config.onMapTypeChange]
     * @param {Function} [config.onFilterChange]
     * @param {Function} [config.updateFilterLabel]
     * @returns {{ switchMenus, getCurrentMapType, setCurrentMapType }}
     */
    window.SharedMapControls = function (config) {
        var mapId             = config.mapId || 'map';
        var mapInstance       = config.mapInstance;
        var onMapTypeChange   = config.onMapTypeChange;
        var onFilterChange    = config.onFilterChange;
        var updateFilterLabel = config.updateFilterLabel;

        // Scoped root: mapId+'Section', else mapId+'Wrapper', else document.
        // All overlay/filter menu queries are scoped here so multiple maps on
        // the same page do not interfere with each other.
        var mapRoot = document.getElementById(mapId + 'Section') || document.getElementById(mapId + 'Wrapper') || document;
        var byId    = function (id) { return mapRoot.querySelector('#' + id) || document.getElementById(id); };

        var mapTypeSelect   = byId(mapId + 'TypeSelect');
        var currentMapType  = mapTypeSelect ? mapTypeSelect.value : 'system-lineage';
        var layoutSelect    = byId(mapId + 'LayoutSelect');
        var collapseBtn     = byId(mapId + 'CollapseBtn');
        var mapSection      = byId(mapId + 'Section');
        var overlayBtn      = byId(mapId + 'OverlayBtn');
        var filterBtn       = byId(mapId + 'FilterBtn');
        var overlayGridBtn  = byId(mapId + 'OverlayGrid');
        var overlayColumnsMenu    = byId(mapId + 'OverlayColumnsMenu');
        var overlayColumnsOptions = byId(mapId + 'OverlayColumnsOptions');

        var zoomInBtn       = byId(mapId + 'ZoomIn');
        var zoomOutBtn      = byId(mapId + 'ZoomOut');
        var redrawBtn       = byId(mapId + 'Redraw');
        var resetBtn        = byId(mapId + 'Reset');
        var exportBtn       = byId(mapId + 'Export');
        var fullscreenBtn   = byId(mapId + 'Fullscreen');
        var legendBtn       = byId(mapId + 'Legend');
        var toggleLabelsBtn = byId(mapId + 'ToggleLabels');
        var navigatorBtn    = byId(mapId + 'Navigator');

        // ── Dynamic menu finders (work for ANY map type) ─────────────────────
        // Menus carry a data-map-type attribute set by SharedMapHTML (or by the
        // domain map's own inline HTML). No hardcoded type list needed.

        function getActiveOverlayMenu() {
            return mapRoot.querySelector('.map-overlay-menu[data-map-type="' + currentMapType + '"]') || null;
        }

        function getActiveFilterMenu() {
            return mapRoot.querySelector('.map-filter-menu:not(.map-overlay-columns-menu)[data-map-type="' + currentMapType + '"]') || null;
        }

        function closeAllOverlayMenus() {
            mapRoot.querySelectorAll('.map-overlay-menu').forEach(function (m) { m.classList.remove('open'); });
        }

        function closeAllFilterMenus() {
            mapRoot.querySelectorAll('.map-filter-menu:not(.map-overlay-columns-menu)').forEach(function (m) { m.classList.remove('open'); });
        }

        function switchMenus(mapType) {
            currentMapType = mapType;
            if (overlayBtn) { var sp = overlayBtn.querySelector('span'); if (sp) sp.textContent = 'None'; }
            mapRoot.querySelectorAll('.overlay-menu-item').forEach(function (i) { i.classList.remove('active'); });
            closeAllOverlayMenus();
            closeAllFilterMenus();
            if (mapInstance) mapInstance.setOverlay('none');
            if (updateFilterLabel) updateFilterLabel(mapType);
        }

        // Map type change
        if (mapTypeSelect) {
            mapTypeSelect.addEventListener('change', function (e) {
                var mapType = e.target.value;
                switchMenus(mapType);
                if (mapInstance) mapInstance.setMapType(mapType);
                if (onMapTypeChange) onMapTypeChange(mapType);
            });
        }

        // Layout select
        if (layoutSelect) {
            layoutSelect.addEventListener('change', function (e) {
                if (mapInstance) mapInstance.setLayout(e.target.value);
            });
        }

        // Collapse/Expand button
        if (collapseBtn && mapSection) {
            collapseBtn.addEventListener('click', function () {
                var toolbar = mapSection.querySelector('.interface-map-toolbar');
                var body    = mapSection.querySelector('.interface-map-body');
                var icon    = collapseBtn.querySelector('i');
                if (toolbar && body) {
                    var isCollapsed = toolbar.style.display === 'none';
                    toolbar.style.display = isCollapsed ? '' : 'none';
                    body.style.display    = isCollapsed ? '' : 'none';
                    icon.className = isCollapsed ? 'fas fa-minus' : 'fas fa-plus';
                }
            });
        }

        // Overlay dropdown button
        if (overlayBtn) {
            overlayBtn.addEventListener('click', function (e) {
                e.stopPropagation();
                var activeOverlayMenu = getActiveOverlayMenu();
                if (activeOverlayMenu) {
                    var wasOpen = activeOverlayMenu.classList.contains('open');
                    closeAllOverlayMenus();
                    closeAllFilterMenus();
                    if (!wasOpen) activeOverlayMenu.classList.add('open');
                } else {
                    closeAllOverlayMenus();
                    closeAllFilterMenus();
                }
            });
        }

        // Wire overlay menu item click handlers – applied to ALL menus in scope
        function setupOverlayMenuHandlers(overlayMenu) {
            if (!overlayMenu) return;
            overlayMenu.querySelectorAll('.overlay-menu-item').forEach(function (item) {
                item.addEventListener('click', function (e) {
                    e.preventDefault();
                    e.stopPropagation();
                    var overlayType = e.currentTarget.getAttribute('data-overlay');
                    var wasActive   = e.currentTarget.classList.contains('active');
                    overlayMenu.querySelectorAll('.overlay-menu-item').forEach(function (i) { i.classList.remove('active'); });
                    if (!wasActive) {
                        e.currentTarget.classList.add('active');
                        if (overlayBtn) { var sp = overlayBtn.querySelector('span'); if (sp) sp.textContent = e.currentTarget.textContent.trim(); }
                        if (mapInstance) mapInstance.setOverlay(overlayType);
                    } else {
                        if (overlayBtn) { var sp = overlayBtn.querySelector('span'); if (sp) sp.textContent = 'None'; }
                        if (mapInstance) mapInstance.setOverlay('none');
                    }
                    overlayMenu.classList.remove('open');
                });
            });
        }

        // Register handlers for ALL overlay menus in scope (any map type)
        mapRoot.querySelectorAll('.map-overlay-menu').forEach(function (m) { setupOverlayMenuHandlers(m); });

        // Clear overlays – register on ALL clear buttons in scope
        var clearOverlaysHandler = function () {
            mapRoot.querySelectorAll('.overlay-menu-item').forEach(function (i) { i.classList.remove('active'); });
            if (overlayBtn) { var sp = overlayBtn.querySelector('span'); if (sp) sp.textContent = 'None'; }
            if (mapInstance) mapInstance.setOverlay('none');
            closeAllOverlayMenus();
        };
        mapRoot.querySelectorAll('.overlay-clear-btn').forEach(function (btn) {
            btn.addEventListener('click', clearOverlaysHandler);
        });

        // Overlay grid / columns button
        if (overlayGridBtn && overlayColumnsMenu && overlayColumnsOptions) {
            function populateOverlayColumnsMenu() {
                overlayColumnsOptions.innerHTML = '';
                var overlayType = mapInstance && mapInstance.getState ? (mapInstance.getState().overlay || '') : '';
                if (!overlayType || overlayType === 'none') {
                    overlayColumnsOptions.innerHTML = '<div class="map-overlay-columns-empty">Select an overlay first.</div>';
                    return;
                }
                var columns   = (typeof window.OverlayColumns !== 'undefined' && window.OverlayColumns.getOverlayColumns) ? window.OverlayColumns.getOverlayColumns(overlayType) : [];
                var selectedIds = (mapInstance && typeof mapInstance.getOverlayColumns === 'function') ? mapInstance.getOverlayColumns(overlayType) : [];
                if (!columns || !columns.length) {
                    overlayColumnsOptions.innerHTML = '<div class="map-overlay-columns-empty">No columns for this overlay.</div>';
                    return;
                }
                columns.forEach(function (col) {
                    var div   = document.createElement('div');
                    div.className = 'map-filter-option';
                    var input = document.createElement('input');
                    input.type = 'checkbox';
                    input.id   = 'overlayCol_' + mapId + '_' + overlayType + '_' + col.id;
                    input.checked = selectedIds.indexOf(col.id) !== -1;
                    input.dataset.columnId = col.id;
                    var label = document.createElement('label');
                    label.htmlFor    = input.id;
                    label.textContent = col.label;
                    div.appendChild(input);
                    div.appendChild(label);
                    input.addEventListener('change', function () {
                        var checked = Array.from(overlayColumnsOptions.querySelectorAll('input:checked')).map(function (i) { return i.dataset.columnId; });
                        if (mapInstance && typeof mapInstance.setOverlayColumns === 'function') {
                            mapInstance.setOverlayColumns(overlayType, checked);
                        }
                    });
                    overlayColumnsOptions.appendChild(div);
                });
            }

            overlayGridBtn.addEventListener('click', function (e) {
                e.stopPropagation();
                closeAllOverlayMenus();
                closeAllFilterMenus();
                var isOpen = overlayColumnsMenu.classList.toggle('open');
                if (isOpen) {
                    overlayColumnsMenu.style.display = 'block';
                    populateOverlayColumnsMenu();
                    var rect = overlayGridBtn.getBoundingClientRect();
                    overlayColumnsMenu.style.position = 'fixed';
                    overlayColumnsMenu.style.left     = rect.left + 'px';
                    overlayColumnsMenu.style.top      = (rect.bottom + 4) + 'px';
                    overlayColumnsMenu.style.minWidth = '200px';
                } else {
                    overlayColumnsMenu.style.display = 'none';
                }
                overlayGridBtn.classList.toggle('active', isOpen);
            });
        }

        // Filter dropdown button
        if (filterBtn) {
            filterBtn.addEventListener('click', function (e) {
                e.stopPropagation();
                var activeFilterMenu = getActiveFilterMenu();
                if (activeFilterMenu) {
                    var wasOpen = activeFilterMenu.classList.contains('open');
                    closeAllFilterMenus();
                    closeAllOverlayMenus();
                    if (!wasOpen) activeFilterMenu.classList.add('open');
                } else {
                    closeAllFilterMenus();
                    closeAllOverlayMenus();
                }
            });
        }

        // Filter checkboxes (common ones — map-specific ones are wired by the domain map file)
        var filterSystemInterfaces   = byId('filter' + mapId + 'systemInterfaces');
        var filterDataAttributeLinks = byId('filter' + mapId + 'dataAttributeLinks');

        if (filterSystemInterfaces) {
            filterSystemInterfaces.addEventListener('change', function () {
                if (mapInstance) mapInstance.setFilter('systemInterfaces', filterSystemInterfaces.checked);
                if (onFilterChange) onFilterChange('systemInterfaces', filterSystemInterfaces.checked);
                if (updateFilterLabel) updateFilterLabel(currentMapType);
            });
        }
        if (filterDataAttributeLinks) {
            filterDataAttributeLinks.addEventListener('change', function () {
                if (mapInstance) mapInstance.setFilter('dataAttributeLinks', filterDataAttributeLinks.checked);
                if (onFilterChange) onFilterChange('dataAttributeLinks', filterDataAttributeLinks.checked);
                if (updateFilterLabel) updateFilterLabel(currentMapType);
            });
        }

        // Close all dropdowns when clicking outside any menu
        document.addEventListener('click', function (e) {
            var activeFilterMenu  = getActiveFilterMenu();
            var activeOverlayMenu = getActiveOverlayMenu();
            if (filterBtn && activeFilterMenu && !filterBtn.contains(e.target) && !activeFilterMenu.contains(e.target)) {
                activeFilterMenu.classList.remove('open');
            }
            if (overlayBtn && activeOverlayMenu && !overlayBtn.contains(e.target) && !activeOverlayMenu.contains(e.target)) {
                activeOverlayMenu.classList.remove('open');
            }
            if (overlayColumnsMenu && overlayGridBtn && !overlayColumnsMenu.contains(e.target) && !overlayGridBtn.contains(e.target)) {
                overlayColumnsMenu.classList.remove('open');
                overlayColumnsMenu.style.display = 'none';
                overlayGridBtn.classList.remove('active');
            }
        });

        // Toolbar action buttons
        if (zoomInBtn)    zoomInBtn.addEventListener('click',    function () { if (mapInstance) mapInstance.zoomIn(); });
        if (zoomOutBtn)   zoomOutBtn.addEventListener('click',   function () { if (mapInstance) mapInstance.zoomOut(); });
        if (redrawBtn)    redrawBtn.addEventListener('click',    function () { if (mapInstance) mapInstance.redrawMap(); });
        if (resetBtn)     resetBtn.addEventListener('click',     function () { if (mapInstance) mapInstance.resetMap(); });
        if (exportBtn)    exportBtn.addEventListener('click',    function () { if (mapInstance) mapInstance.exportAsPng(); });
        if (fullscreenBtn) fullscreenBtn.addEventListener('click', function () { if (mapInstance && mapInstance.openFullscreen) mapInstance.openFullscreen(); });

        if (legendBtn) {
            if (typeof window.setupMapLegendDropdown === 'function' && mapInstance && typeof mapInstance.getLegendHtml === 'function') {
                window.setupMapLegendDropdown(mapId + 'Legend', mapId + 'LegendDropdown', function () { return mapInstance.getLegendHtml(); });
            } else {
                var sidePanel = byId(mapId + 'SidePanel');
                legendBtn.addEventListener('click', function () {
                    if (sidePanel) {
                        var isVisible = sidePanel.style.display !== 'none';
                        sidePanel.style.display = isVisible ? 'none' : 'flex';
                        legendBtn.classList.toggle('active', !isVisible);
                    }
                });
            }
        }

        if (navigatorBtn) {
            navigatorBtn.addEventListener('click', function () {
                navigatorBtn.classList.toggle('active');
                if (mapInstance && mapInstance.toggleNavigator) mapInstance.toggleNavigator();
            });
        }

        if (toggleLabelsBtn) {
            var labelsVisible = true;
            toggleLabelsBtn.classList.add('active');
            toggleLabelsBtn.addEventListener('click', function () {
                labelsVisible = !labelsVisible;
                if (mapInstance && mapInstance.toggleInterfaceLabels) mapInstance.toggleInterfaceLabels(labelsVisible);
                toggleLabelsBtn.classList.toggle('active', labelsVisible);
            });
        }

        if (updateFilterLabel) updateFilterLabel(currentMapType);

        return {
            switchMenus:          switchMenus,
            getCurrentMapType:    function () { return currentMapType; },
            setCurrentMapType:    function (type) { currentMapType = type; switchMenus(type); }
        };
    };


    // =========================================================================
    // SECTION 5 – setupMapLegendDropdown
    // =========================================================================
    /**
     * Render the legend as a pinned overlay inside the map canvas.
     * @param {string}   buttonId     — ID of the legend toolbar button
     * @param {string}   dropdownId   — ID for the legend overlay element
     * @param {Function} getLegendHtml — () => string
     */
    window.setupMapLegendDropdown = function (buttonId, dropdownId, getLegendHtml) {
        var legendBtn = document.getElementById(buttonId);
        if (!legendBtn || typeof getLegendHtml !== 'function') return;

        var canvasId  = buttonId.replace(/Legend$/, 'Canvas');
        var mapCanvas = document.getElementById(canvasId)
            || (legendBtn.closest('.map-section, .map-view-wrapper') || {}).querySelector('.interface-map-canvas, .map-network-canvas');

        var legendEl  = document.getElementById(dropdownId);

        function ensureLegend() {
            if (legendEl && legendEl.parentNode) return legendEl;
            legendEl = document.createElement('div');
            legendEl.id        = dropdownId;
            legendEl.className = 'map-inline-legend';
            (mapCanvas || document.body).appendChild(legendEl);
            return legendEl;
        }

        function showLegend() {
            var el = ensureLegend();
            el.innerHTML  = getLegendHtml() || '';
            el.style.display = 'block';
            legendBtn.classList.add('active');
        }
        function hideLegend() {
            if (legendEl) legendEl.style.display = 'none';
            legendBtn.classList.remove('active');
        }

        legendBtn.addEventListener('click', function (e) {
            e.stopPropagation();
            if (legendBtn.classList.contains('active')) hideLegend(); else showLegend();
        });

        setTimeout(function () {
            if (!legendBtn.classList.contains('active')) {
                var html = getLegendHtml();
                if (html) showLegend();
            }
        }, 1500);
    };


    // =========================================================================
    // SECTION 6 – exportMapWithOverlays (PNG composite export)
    // =========================================================================
    function _triggerMapPngDownload(href, fname) {
        if (!href) return;
        var a = document.createElement('a');
        a.download = fname || 'map.png';
        a.href     = href;
        a.style.display = 'none';
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
    }

    /** True if an overlay panel / legend should be composited onto the exported PNG (not display:none / zero size). */
    function _mapExportDecorationIsPaintable(el) {
        if (!el || typeof el.getBoundingClientRect !== 'function') return false;
        var r = el.getBoundingClientRect();
        if (r.width < 1 || r.height < 1) return false;
        if (typeof window.getComputedStyle === 'function') {
            var cs = getComputedStyle(el);
            if (cs.display === 'none' || cs.visibility === 'hidden') return false;
            var op = parseFloat(cs.opacity);
            if (!isNaN(op) && op === 0) return false;
        } else if (el.style && el.style.display === 'none') {
            return false;
        }
        return true;
    }

    /**
     * Export a Cytoscape map as PNG including overlay panels and legend.
     * @param {Object}  cy        — Cytoscape instance
     * @param {Element} mapCanvas — container that holds cy + overlay root (facet: .map-overlay-container; fullscreen tab: .fs-overlay-container)
     * @param {string}  filename
     */
    window.exportMapWithOverlays = function (cy, mapCanvas, filename) {
        if (!cy) return;
        filename = filename || 'map.png';

        function simpleCyExport() {
            try {
                var png64 = cy.png({ bg: '#ffffff', full: true, scale: 2 });
                if (png64) _triggerMapPngDownload(png64, filename);
            } catch (e) { _mapDbgWarn('[exportMapWithOverlays] Cytoscape-only export failed:', e); }
        }

        var overlayContainer = null;
        if (mapCanvas) {
            overlayContainer = mapCanvas.querySelector('.map-overlay-container')
                || mapCanvas.querySelector('.fs-overlay-container')
                || mapCanvas.querySelector('#fsOverlayContainer');
        }
        var panelsList = [];
        if (overlayContainer) {
            overlayContainer.querySelectorAll('.map-node-overlay-panel').forEach(function (p) {
                if (_mapExportDecorationIsPaintable(p)) { panelsList.push(p); }
            });
        }
        var legend = null;
        if (mapCanvas) {
            legend = mapCanvas.querySelector('.map-inline-legend')
                || mapCanvas.querySelector('.fs-inline-legend')
                || mapCanvas.querySelector('#fsLegend');
        }
        var legendVisible = !!(legend && _mapExportDecorationIsPaintable(legend));

        if (!panelsList.length && !legendVisible) { simpleCyExport(); return; }

        var cyContainer = cy.container();
        if (!cyContainer) { simpleCyExport(); return; }

        try {
            var containerRect   = cyContainer.getBoundingClientRect();
            var scale = 2;
            var w = Math.max(1, containerRect.width  || 0);
            var h = Math.max(1, containerRect.height || 0);

            var compositeCanvas   = document.createElement('canvas');
            compositeCanvas.width  = w * scale;
            compositeCanvas.height = h * scale;
            var ctx = compositeCanvas.getContext('2d');
            ctx.scale(scale, scale);
            ctx.fillStyle = '#ffffff';
            ctx.fillRect(0, 0, w, h);

            cyContainer.querySelectorAll('canvas').forEach(function (c) {
                try { ctx.drawImage(c, 0, 0, w, h); } catch (e) { /* cross-origin guard */ }
            });

            if (panelsList.length) {
                panelsList.forEach(function (panel) { _drawOverlayPanel(ctx, panel, containerRect); });
            }
            if (legendVisible) _drawLegend(ctx, legend, containerRect);

            compositeCanvas.toBlob(function (blob) {
                if (blob) {
                    var url = URL.createObjectURL(blob);
                    _triggerMapPngDownload(url, filename);
                    setTimeout(function () { URL.revokeObjectURL(url); }, 5000);
                    return;
                }
                try { _triggerMapPngDownload(compositeCanvas.toDataURL('image/png'), filename); }
                catch (e) { _mapDbgWarn('[exportMapWithOverlays] Composite download failed:', e); simpleCyExport(); }
            }, 'image/png');
        } catch (err) {
            _mapDbgWarn('[exportMapWithOverlays] Composite export failed, using graph-only export:', err);
            simpleCyExport();
        }
    };

    function _drawOverlayPanel(ctx, panel, containerRect) {
        var pr = panel.getBoundingClientRect();
        var x  = pr.left - containerRect.left;
        var y  = pr.top  - containerRect.top;
        var pw = pr.width;
        var ph = pr.height;
        ctx.save();
        ctx.fillStyle   = 'rgba(255,255,255,0.95)';
        ctx.strokeStyle = '#d1d5db';
        ctx.lineWidth   = 1;
        _rrect(ctx, x, y, pw, ph, 6, false);
        ctx.fill();
        ctx.stroke();
        var header  = panel.querySelector('.map-node-overlay-header, .overlay-panel-header');
        var headerH = 0;
        if (header) {
            var hr = header.getBoundingClientRect();
            headerH = hr.height;
            ctx.fillStyle = '#f3f4f6';
            _rrect(ctx, x, y, pw, headerH, 6, true);
            ctx.fill();
            ctx.fillStyle = '#1f2937';
            ctx.font = 'bold 11px Inter, system-ui, sans-serif';
            ctx.fillText(_trunc((header.textContent || '').trim(), pw - 12), x + 6, y + headerH / 2 + 4, pw - 12);
        }
        var items  = panel.querySelectorAll('.map-node-overlay-item, .overlay-panel-row');
        var iy     = y + headerH + 2;
        var lineH  = 18;
        ctx.font   = '10px Inter, system-ui, sans-serif';
        items.forEach(function (item, idx) {
            if (iy + lineH > y + ph) return;
            if (idx % 2 === 0) { ctx.fillStyle = 'rgba(249,250,251,0.6)'; ctx.fillRect(x + 1, iy, pw - 2, lineH); }
            if (item.classList.contains('highlighted-source'))  { ctx.fillStyle = 'rgba(22,163,74,0.15)';  ctx.fillRect(x + 1, iy, pw - 2, lineH); }
            else if (item.classList.contains('highlighted-related')) { ctx.fillStyle = 'rgba(34,197,94,0.08)'; ctx.fillRect(x + 1, iy, pw - 2, lineH); }
            ctx.fillStyle = '#374151';
            ctx.fillText(_trunc((item.textContent || '').trim(), pw - 16), x + 8, iy + 13, pw - 16);
            iy += lineH;
        });
        ctx.restore();
    }

    function _drawLegend(ctx, legend, containerRect) {
        var lr = legend.getBoundingClientRect();
        var x  = lr.left - containerRect.left;
        var y  = lr.top  - containerRect.top;
        var lw = lr.width;
        var lh = lr.height;
        ctx.save();
        ctx.fillStyle   = 'rgba(255,255,255,0.95)';
        ctx.strokeStyle = '#d1d5db';
        ctx.lineWidth   = 1;
        _rrect(ctx, x, y, lw, lh, 6, false);
        ctx.fill();
        ctx.stroke();
        var iy    = y + 8;
        var lineH = 20;
        ctx.font  = '11px Inter, system-ui, sans-serif';
        legend.querySelectorAll('.legend-item').forEach(function (item) {
            if (iy + lineH > y + lh) return;
            var colorEl  = item.querySelector('.legend-color');
            var textEl   = item.querySelector('span');
            var dotColor = '#999';
            if (colorEl) { var bg = colorEl.style.background || colorEl.style.backgroundColor; if (bg) dotColor = bg; }
            ctx.fillStyle = dotColor;
            ctx.beginPath();
            ctx.arc(x + 14, iy + lineH / 2, 5, 0, Math.PI * 2);
            ctx.fill();
            ctx.fillStyle = '#374151';
            ctx.fillText(_trunc(textEl ? (textEl.textContent || '').trim() : (item.textContent || '').trim(), lw - 32), x + 24, iy + lineH / 2 + 4, lw - 32);
            iy += lineH;
        });
        ctx.restore();
    }

    function _rrect(ctx, x, y, w, h, r, topOnly) {
        ctx.beginPath();
        ctx.moveTo(x + r, y);
        ctx.lineTo(x + w - r, y);
        ctx.quadraticCurveTo(x + w, y, x + w, y + r);
        if (topOnly) { ctx.lineTo(x + w, y + h); ctx.lineTo(x, y + h); }
        else {
            ctx.lineTo(x + w, y + h - r);
            ctx.quadraticCurveTo(x + w, y + h, x + w - r, y + h);
            ctx.lineTo(x + r, y + h);
            ctx.quadraticCurveTo(x, y + h, x, y + h - r);
        }
        ctx.lineTo(x, y + r);
        ctx.quadraticCurveTo(x, y, x + r, y);
        ctx.closePath();
    }

    function _trunc(text, maxWidth) {
        if (!text) return '';
        var maxChars = Math.floor(maxWidth / 6);
        return text.length > maxChars ? text.substring(0, maxChars - 1) + '\u2026' : text;
    }


    // =========================================================================
    // SECTION 7 – openMapFullscreen (new-tab fullscreen view)
    // =========================================================================
    /**
     * Collect section header, primary toolbar, and (when present) the legend strip above the canvas
     * so the new-tab view matches the full chrome of the source map.
     * @param {Element} canvas — Cytoscape container (e.g. .interface-map-canvas)
     * @returns {string} concatenated outerHTML
     */
    window.collectFullscreenMapToolbarHtml = function (canvas) {
        if (!canvas || !canvas.closest) return '';
        var root = canvas.closest('.map-section')
            || canvas.closest('.interface-map-container')
            || canvas.closest('.map-view-wrapper')
            || canvas.closest('.maps-container');
        if (!root) return '';

        // maps.html — toolbar rows inside .maps-container
        if (root.classList.contains('maps-container')) {
            var mOut = [];
            for (var mi = 0; mi < root.children.length; mi++) {
                var mc = root.children[mi];
                if (mc.classList.contains('maps-toolbar')) mOut.push(mc.outerHTML);
            }
            return mOut.join('');
        }

        // Unison insight map (map-view.html): one or more .map-toolbar rows
        if (root.classList.contains('map-view-wrapper')) {
            var uOut = [];
            for (var ui = 0; ui < root.children.length; ui++) {
                var uc = root.children[ui];
                if (uc.classList.contains('map-toolbar')) uOut.push(uc.outerHTML);
            }
            return uOut.join('');
        }

        var out = [];
        for (var i = 0; i < root.children.length; i++) {
            var el = root.children[i];
            if (el.classList.contains('map-section-header')) {
                out.push(el.outerHTML);
            }
            if (el.classList.contains('interface-map-toolbar')) {
                out.push(el.outerHTML);
                var body = el.nextElementSibling;
                if (body && body.classList.contains('interface-map-body')) {
                    var lb = body.querySelector('.map-legend-bar');
                    if (lb && !lb.closest('.interface-map-side-panel')) {
                        out.push(lb.outerHTML);
                    }
                }
            }
        }
        return out.join('');
    };

    /** sessionStorage key prefix for fullscreen snapshot bridge (?tb= on /view/full_map/…). Must match map-tab-init.js. */
    window.MAP_TAB_STORAGE_PREFIX = 'mapTabState_';

    function _splitNodesEdges(flat) {
        var nodes = [];
        var edges = [];
        flat.forEach(function (e) {
            if (e.group === 'edges' || (e.data && e.data.source != null && e.data.target != null)) {
                edges.push(e);
            } else {
                nodes.push(e);
            }
        });
        return { nodes: nodes, edges: edges };
    }

    function _collectActiveFiltersFromAnchor(anchor) {
        var out = {};
        if (!anchor || !anchor.closest) return out;
        var root = anchor.closest('.map-section, .interface-map-container, .map-view-wrapper, .maps-container');
        if (!root) root = document.documentElement;
        root.querySelectorAll('input[type="checkbox"][id]').forEach(function (cb) {
            try { out[cb.id] = !!cb.checked; } catch (e) { /* no-op */ }
        });
        return out;
    }

    /**
     * Snapshot all filter + overlay menu checkboxes (ids only) for fullscreen tab restore.
     * @param {Element} [toolbarAnchor] — map canvas or toolbar root
     */
    window.collectAllActiveFilters = function (toolbarAnchor) {
        var filters = {};
        var root = (toolbarAnchor && toolbarAnchor.closest)
            ? (toolbarAnchor.closest('.map-section, .interface-map-container, .map-view-wrapper, .maps-container') || document)
            : document;
        if (!root || !root.querySelectorAll) return filters;
        root.querySelectorAll('.map-filter-menu input[type="checkbox"], .map-overlay-menu input[type="checkbox"]').forEach(function (cb) {
            if (cb.id) filters[cb.id] = !!cb.checked;
        });
        return filters;
    };

    /** All <select> values under the map chrome (for fullscreen mirror). */
    window.collectSelectValuesFromAnchor = function (toolbarAnchor) {
        var out = {};
        var root = (toolbarAnchor && toolbarAnchor.closest)
            ? (toolbarAnchor.closest('.map-section, .interface-map-container, .map-view-wrapper, .maps-container') || document.documentElement)
            : document.documentElement;
        if (!root || !root.querySelectorAll) return out;
        root.querySelectorAll('select').forEach(function (sel) {
            if (sel.id) out[sel.id] = sel.value;
        });
        return out;
    };

    /** Strip visibility/hidden classes from a Cytoscape JSON element for snapshot/open tab. */
    window.cleanCytoscapeElementJson = function (el) {
        if (!el || typeof el !== 'object') return el;
        var c;
        try {
            c = JSON.parse(JSON.stringify(el));
        } catch (e) {
            c = Object.assign({}, el);
        }
        if (c.classes) {
            c.classes = String(c.classes).split(/\s+/).filter(function (cls) {
                return cls && ['filter-hidden', 'map-js-hidden', 'hidden', 'link-filter-hidden'].indexOf(cls) === -1;
            }).join(' ');
        }
        if (c.style && typeof c.style === 'object') {
            c.style = Object.assign({}, c.style);
            if (c.style.display === 'none') delete c.style.display;
        }
        return c;
    };

    function _overlayPanelsHtmlFromAnchor(anchor) {
        if (!anchor) return '';
        var root = anchor.closest && (anchor.closest('.map-section') || anchor.closest('.interface-map-container') || anchor.closest('.map-view-wrapper') || anchor.closest('.maps-container'));
        var scope = root || anchor;
        if (!scope || !scope.querySelector) return '';
        var oc = scope.querySelector('.map-overlay-container')
            || scope.querySelector('.map-node-overlay-wrap')
            || scope.querySelector('#mapTabOverlayContainer');
        return oc ? (oc.innerHTML || '') : '';
    }

    function _legendHtmlFromAnchor(anchor, fallback) {
        if (fallback != null && String(fallback).length) return fallback;
        if (!anchor || !anchor.closest) return '';
        var root = anchor.closest('.map-section, .interface-map-container, .map-view-wrapper, .maps-container') || document;
        var leg = root.querySelector && (
            root.querySelector('[data-interface-map-legend]')
            || root.querySelector('.map-legend')
            || root.querySelector('.map-tab-legend')
            || root.querySelector('#interfaceMapLegendDropdown')
        );
        return leg ? (leg.innerHTML || '') : '';
    }

    function _elementsFromNetwork(network) {
        var nodes = network.nodes().map(function (n) { return window.cleanCytoscapeElementJson(n.json()); });
        var edges = network.edges().map(function (e) { return window.cleanCytoscapeElementJson(e.json()); });
        return { nodes: nodes, edges: edges };
    }

    /** Entity id for /view/full_map/{facet}/{id} path segment (and snapshot bridge). */
    function _resolveEntityIdForFullMap(opts, gs) {
        if (opts.entityId != null) return opts.entityId;
        if (!gs) return null;
        if (gs.systemId != null) return gs.systemId;
        if (gs.processId != null) return gs.processId;
        if (gs.projectId != null) return gs.projectId;
        if (gs.glossaryId != null) return gs.glossaryId;
        if (gs.datasetId != null) return gs.datasetId;
        if (gs.capabilityId != null) return gs.capabilityId;
        if (gs.entityId != null) return gs.entityId;
        return null;
    }

    /**
     * How to open fullscreen: live API vs snapshot + ?tb= sessionStorage bridge.
     * @returns {{ mode: 'live'|'snapshot', facet: string, variant: string|null }|null}
     */
    function _resolveFullMapRoute(mapTabKind) {
        if (!mapTabKind) return null;
        if (mapTabKind === 'system-interfaces') return { mode: 'live', facet: 'system', variant: null };
        if (mapTabKind === 'process-data') return { mode: 'live', facet: 'process', variant: null };
        /* Same path shape as system/process: /full_map/glossary/{id}?tb= — map kind lives in session JSON. */
        if (mapTabKind === 'glossary-data') return { mode: 'snapshot', facet: 'glossary', variant: null };
        if (mapTabKind === 'glossary-relationships') return { mode: 'snapshot', facet: 'glossary', variant: null };
        if (mapTabKind === 'dataset-relationships') return { mode: 'snapshot', facet: 'dataset', variant: null };
        if (mapTabKind === 'capability-relationships') return { mode: 'snapshot', facet: 'capability', variant: null };
        if (mapTabKind === 'project-data') return { mode: 'snapshot', facet: 'project', variant: null };
        if (mapTabKind === 'insight') return { mode: 'snapshot', facet: 'insight', variant: null };
        if (mapTabKind === 'process-flow-context') return { mode: 'snapshot', facet: 'process', variant: 'flow-context' };
        if (mapTabKind === 'process-flow-components') return { mode: 'snapshot', facet: 'process', variant: 'flow-components' };
        if (mapTabKind.indexOf('map-engine-') === 0) {
            var k = mapTabKind.slice('map-engine-'.length);
            if (k === 'glossary-data') return { mode: 'snapshot', facet: 'glossary', variant: null };
            if (k === 'glossary-relationships') return { mode: 'snapshot', facet: 'glossary', variant: null };
            if (k === 'dataset-lineage') return { mode: 'snapshot', facet: 'dataset', variant: null };
            if (k === 'capability-lineage') return { mode: 'snapshot', facet: 'capability', variant: null };
            if (k === 'project-data') return { mode: 'snapshot', facet: 'project', variant: null };
            if (k === 'process-data') return { mode: 'live', facet: 'process', variant: null };
            if (k === 'system-lineage') return { mode: 'live', facet: 'system', variant: null };
            return { mode: 'snapshot', facet: 'map', variant: k };
        }
        if (mapTabKind.indexOf('shared-map-core-') === 0) {
            return { mode: 'snapshot', facet: 'map', variant: mapTabKind.slice('shared-map-core-'.length) };
        }
        if (mapTabKind.indexOf('shared-map-') === 0) {
            return { mode: 'snapshot', facet: 'map', variant: mapTabKind.slice('shared-map-'.length) };
        }
        return null;
    }

    /**
     * Open the map in /view/full_map/… — live (system/process) or snapshot via sessionStorage + ?tb=.
     * @param {Object} opts
     * @param {Object} [opts.network] — Cytoscape instance; preferred source for nodes/edges (cleaned)
     * @param {Object} [opts.elements] — { nodes, edges } or flat array if no network
     * @param {function():Object} [opts.getState] — overlay, hops, labels, overlayColumns, layout, mapType, spacing, edgeStyle
     * @param {Element} [opts.toolbarAnchor] — collect toolbar + overlay + legend scope
     * @param {function():Array} [opts.getCytoscapeStyle] — if opts.style omitted, called for live style
     */
    window.openMapFullscreen = function (opts) {
        if (!opts) { alert('No map options'); return; }

        var gs0 = typeof opts.getState === 'function' ? opts.getState() : null;
        var mapTabKind0 = opts.mapTabKind || 'lineage';
        var route = _resolveFullMapRoute(mapTabKind0);
        var useLegacy = opts.useLegacyMapTabStorage === true;
        var entityForPath = _resolveEntityIdForFullMap(opts, gs0);
        if (entityForPath == null || String(entityForPath) === '') {
            entityForPath = '0';
        }

        if (!useLegacy && route && route.mode === 'live') {
            var layoutUrl = opts.layoutName != null ? opts.layoutName : (gs0 && gs0.layout) || 'top-to-bottom';
            var mapTypeUrl = opts.mapType != null ? opts.mapType : (gs0 && gs0.mapType) || 'system-lineage';
            var hopsUrl = opts.hopsCount != null ? opts.hopsCount : (gs0 && (gs0.hopsCount != null ? gs0.hopsCount : (gs0.hopsLimit != null ? gs0.hopsLimit : 15)));
            if (hopsUrl == null || isNaN(hopsUrl)) hopsUrl = 15;
            var showLabelsUrl = opts.showLabels;
            if (showLabelsUrl == null && gs0) {
                if (typeof gs0.showEdgeLabels === 'boolean') showLabelsUrl = gs0.showEdgeLabels;
                else if (typeof gs0.showInterfaceLabels === 'boolean') showLabelsUrl = gs0.showInterfaceLabels;
                else if (typeof gs0.showLabels === 'boolean') showLabelsUrl = gs0.showLabels;
                else showLabelsUrl = true;
            }
            if (showLabelsUrl == null) showLabelsUrl = true;
            var q = new URLSearchParams();
            if (layoutUrl !== 'top-to-bottom') q.set('layout', layoutUrl);
            if (mapTypeUrl !== 'system-lineage') q.set('mapType', mapTypeUrl);
            if (hopsUrl !== 15) q.set('hops', String(hopsUrl));
            if (showLabelsUrl === false) q.set('showLabels', '0');
            if (mapTabKind0 === 'system-interfaces' && mapTypeUrl === 'dataset-lineage') {
                q.set('map', 'data');
            }

            var pathLive = '/view/full_map/' + encodeURIComponent(route.facet) + '/' + encodeURIComponent(String(entityForPath));
            var qsLive = q.toString();
            var urlLive = pathLive + (qsLive ? '?' + qsLive : '');
            var wLive = window.open(urlLive, '_blank');
            if (!wLive) { alert('Popup blocked. Please allow popups for this site.'); }
            return;
        }

        var split;
        if (opts.network && typeof opts.network.nodes === 'function') {
            split = _elementsFromNetwork(opts.network);
        } else if (opts.elements) {
            var elements = opts.elements;
            var flat = elements;
            if (elements && !Array.isArray(elements) && elements.nodes && elements.edges) {
                flat = elements.nodes.concat(elements.edges);
            }
            if (!Array.isArray(flat) || flat.length === 0) {
                alert('No map data available');
                return;
            }
            flat = flat.map(function (el) { return window.cleanCytoscapeElementJson(el); });
            split = _splitNodesEdges(flat);
        } else {
            alert('No map data available');
            return;
        }

        var gs = gs0;

        var style = opts.style;
        if (style == null && typeof opts.getCytoscapeStyle === 'function') {
            try { style = opts.getCytoscapeStyle(); } catch (e1) { style = null; }
        }
        if (style == null && opts.network && opts.network.style) {
            try { style = opts.network.style().json(); } catch (e2) { style = []; }
        }

        var layoutName = opts.layoutName != null ? opts.layoutName : (gs && gs.layout) || 'top-to-bottom';
        var mapType = opts.mapType != null ? opts.mapType : (gs && gs.mapType) || 'system-lineage';

        var activeOverlay = opts.activeOverlay != null ? opts.activeOverlay : (gs && gs.overlay != null ? gs.overlay : 'none');

        var hopsCount = opts.hopsCount != null ? opts.hopsCount : (gs && (gs.hopsCount != null ? gs.hopsCount : (gs.hopsLimit != null ? gs.hopsLimit : null)));
        if (hopsCount == null || isNaN(hopsCount)) hopsCount = 15;

        var showLabels = opts.showLabels;
        if (showLabels == null && gs) {
            if (typeof gs.showEdgeLabels === 'boolean') showLabels = gs.showEdgeLabels;
            else if (typeof gs.showInterfaceLabels === 'boolean') showLabels = gs.showInterfaceLabels;
            else if (typeof gs.showLabels === 'boolean') showLabels = gs.showLabels;
            else showLabels = true;
        }
        if (showLabels == null) showLabels = true;

        var overlayColumns = opts.overlayColumns;
        if (overlayColumns == null && gs && gs.overlayColumnsByType) {
            try { overlayColumns = JSON.parse(JSON.stringify(gs.overlayColumnsByType)); }
            catch (e3) { overlayColumns = {}; }
        }
        if (overlayColumns == null) overlayColumns = {};

        var toolbarHtml = (opts.toolbarAnchor ? window.collectFullscreenMapToolbarHtml(opts.toolbarAnchor) : '') || opts.toolbarHtml || '';

        var activeFilters = opts.activeFilters || window.collectAllActiveFilters(opts.toolbarAnchor);
        if (!activeFilters || typeof activeFilters !== 'object') activeFilters = {};

        var selectValues = opts.selectValues || window.collectSelectValuesFromAnchor(opts.toolbarAnchor);

        var overlayPanelsHtml = opts.overlayPanelsHtml != null ? opts.overlayPanelsHtml : _overlayPanelsHtmlFromAnchor(opts.toolbarAnchor);

        var legendHtml = _legendHtmlFromAnchor(opts.toolbarAnchor, opts.legendHtml);

        var mapTabId = 'mt_' + Date.now().toString(36) + '_' + Math.random().toString(36).slice(2, 12);

        var spacing = opts.spacing != null ? opts.spacing : (gs && gs.spacing) || 'normal';
        var edgeStyle = opts.edgeStyle != null ? opts.edgeStyle : (gs && gs.edgeStyle) || 'direct';

        var entityId = _resolveEntityIdForFullMap(opts, gs);

        var exportFilename = (opts.exportFilename || 'map.png').replace(/"/g, '');
        var title = (opts.title || 'Map').replace(/</g, '\u003c');

        var state = {
            v: 2,
            title: title,
            mapTabKind: opts.mapTabKind || 'lineage',
            entityId: entityId != null ? entityId : null,
            nodes: split.nodes,
            edges: split.edges,
            layout: layoutName,
            mapType: mapType,
            toolbarHtml: toolbarHtml,
            activeFilters: activeFilters,
            selectValues: selectValues,
            activeOverlay: activeOverlay,
            overlayColumns: overlayColumns,
            overlayPanelsHtml: overlayPanelsHtml,
            showLabels: !!showLabels,
            hopsCount: hopsCount,
            spacing: spacing,
            edgeStyle: edgeStyle,
            style: style || [],
            legendHtml: legendHtml,
            exportFilename: exportFilename
        };

        try {
            sessionStorage.setItem(window.MAP_TAB_STORAGE_PREFIX + mapTabId, JSON.stringify(state));
        } catch (err) {
            alert('Map is too large to open in a new tab. Try reducing hops or filters.');
            return;
        }

        if (!useLegacy && route && route.mode === 'snapshot') {
            var pathSnap = '/view/full_map/' + encodeURIComponent(route.facet);
            if (route.variant) {
                pathSnap += '/' + encodeURIComponent(route.variant);
            }
            pathSnap += '/' + encodeURIComponent(String(entityForPath));
            var urlSnap = pathSnap + '?tb=' + encodeURIComponent(mapTabId);
            var wSnap = window.open(urlSnap, '_blank');
            if (!wSnap) { alert('Popup blocked. Please allow popups for this site.'); }
            return;
        }

        var pathOther = '/view/full_map/other/' + encodeURIComponent(String(entityForPath)) + '?tb=' + encodeURIComponent(mapTabId);
        var wOther = window.open(pathOther, '_blank');
        if (!wOther) { alert('Popup blocked. Please allow popups for this site.'); }
    };


    // =========================================================================
    // SECTION 8 – SharedMap class (class-based Cytoscape lifecycle)
    // =========================================================================
    class SharedMap {
        constructor(config) {
            this.config = {
                mapId:               config.mapId || null,
                containerId:         config.containerId || 'mapCanvas',
                loadingSelector:     config.loadingSelector || '[data-map-loading]',
                placeholderSelector: config.placeholderSelector || '[data-map-placeholder]',
                legendSelector:      config.legendSelector || '[data-map-legend]',
                facetType:           config.facetType || 'system',
                entityId:            config.entityId,
                defaultMapType:      config.defaultMapType || 'system-lineage',
                dataLoader:          config.dataLoader,
                nodeBuilder:         config.nodeBuilder,
                edgeBuilder:         config.edgeBuilder,
                onNodeClick:         config.onNodeClick,
                onEdgeClick:         config.onEdgeClick,
                onBackgroundClick:   config.onBackgroundClick,
                contextMenuItems:    config.contextMenuItems || [],
                legendBuilder:       config.legendBuilder
            };
            this.state = {
                initialized: false,
                network: null,
                canvas: null,
                loadingEl: null,
                entityData: null,
                rawData: null,
                nodes: [],
                edges: [],
                hiddenNodes: new Set(),
                focusedNode: null,
                hiddenUpstreamNodes: new Set(),
                hiddenDownstreamNodes: new Set(),
                layout: 'top-to-bottom',
                mapType: this.config.defaultMapType,
                overlay: 'none',
                showLabels: true,
                filters: { systemInterfaces: true, dataAttributeLinks: true },
                nodeFilters: { classifications: [], types: [], lifecycles: [] }
            };
            this.Styles = window.SharedMapStyles || window.InterfaceMapStyles;
            this.Icons  = window.SharedMapIcons  || window.InterfaceMapIcons;
        }

        async init(entityId) {
            if (entityId) this.config.entityId = entityId;
            _mapDbgLog('[SharedMap-' + this.config.facetType + '] Initializing map for entity:', this.config.entityId);
            this.state.canvas        = document.getElementById(this.config.containerId);
            this.state.loadingEl     = document.querySelector(this.config.loadingSelector);
            this.state.placeholderEl = document.querySelector(this.config.placeholderSelector);
            if (!this.state.canvas) { console.error('[SharedMap] Canvas element not found: #' + this.config.containerId); return; }
            if (typeof cytoscape === 'undefined') { console.error('[SharedMap] Cytoscape library not available'); this.showPlaceholder('Visualization library is not available.'); return; }
            if (!SharedMap.dagreRegistered) {
                try {
                    var dagre = window.cytoscapeDagre || window['cytoscape-dagre'];
                    if (dagre) { cytoscape.use(dagre); SharedMap.dagreRegistered = true; _mapDbgLog('[SharedMap] Dagre layout registered'); }
                    else { _mapDbgWarn('[SharedMap] Dagre layout extension not found, will use fallback layout'); }
                } catch (e) { _mapDbgWarn('[SharedMap] Error registering dagre layout:', e); }
            }
            this.state.initialized = true;
            this.showLoading();
            try { await this.loadAndRender(); }
            catch (error) { console.error('[SharedMap] Failed to initialize:', error); this.showPlaceholder('Failed to load map data.'); }
            finally { this.hideLoading(); }
        }

        async loadAndRender() {
            if (!this.config.dataLoader) { console.error('[SharedMap] No dataLoader provided'); this.showPlaceholder('Map configuration error.'); return; }
            const data = await this.config.dataLoader(this.config.entityId, this.state.mapType, this.state.filters);
            this.state.rawData = data;
            const nodes = this.config.nodeBuilder ? this.config.nodeBuilder(data, this.state) : (data.nodes || []);
            const edges = this.config.edgeBuilder ? this.config.edgeBuilder(data, this.state) : (data.edges || []);
            this.state.nodes = nodes;
            this.state.edges = edges;
            this.renderNetwork({ nodes, edges });
            this.updateLegend();
        }

        renderNetwork(graph) {
            if (!this.state.canvas) { console.error('[SharedMap] Canvas not found'); return; }
            if (this.state.network) { this.state.network.destroy(); this.state.network = null; }
            this.state.canvas.innerHTML = '';
            if (!graph || !graph.nodes || graph.nodes.length === 0) { this.showPlaceholder('No data to display.'); return; }
            this.hideLoading();
            try {
                this.state.network = cytoscape({
                    container:       this.state.canvas,
                    elements:        this.buildCytoscapeElements(graph),
                    style:           this.getCytoscapeStyle(),
                    layout:          this.getLayoutConfig(),
                    minZoom:         0.1,
                    maxZoom:         2,
                    wheelSensitivity: 0.2
                });
                this.setupEventListeners();
                setTimeout(() => { if (this.state.network) { this.state.network.fit(); this.state.network.center(); } }, 100);
            } catch (error) { console.error('[SharedMap] Error rendering network:', error); this.showPlaceholder('Error rendering map: ' + error.message); }
        }

        buildCytoscapeElements(graph) {
            const elements       = [];
            const colors         = this.Styles?.colors || {};
            const currentEntityId = String(this.config.entityId);
            graph.nodes.forEach(node => {
                const nodeId     = String(node.id);
                const isCurrent  = node.isCurrent || nodeId === currentEntityId;
                const isLocked   = node.isLocked || false;
                const group      = node.group || this.config.facetType;
                const nodeColor  = isCurrent ? (colors.currentSystem || '#f97316') : isLocked ? (colors.lockedSystem || '#6b7280') : (colors.otherSystem || '#1f2937');
                const borderColor = isCurrent ? (colors.currentSystemBorder || '#ea580c') : isLocked ? (colors.lockedSystem || '#6b7280') : (colors.otherSystemBorder || '#374151');
                let icon;
                if (this.Icons) {
                    if (isLocked)            icon = this.Icons.createLockIcon();
                    else if (group === 'dataset') icon = this.Icons.createDatasetIcon();
                    else if (group === 'system')  icon = this.Icons.createDatabaseIcon();
                    else                           icon = this.Icons.getIconByType(group);
                }
                elements.push({ data: { id: nodeId, label: node.label || node.name || nodeId, group, isCurrent, isLocked, nodeColor, borderColor, backgroundImage: icon || '', meta: node.meta || node, ...node } });
            });
            graph.edges.forEach(edge => {
                const lineStyle = edge.lineStyle || 'solid';
                const lineColor = lineStyle === 'dashed' ? (colors.interfaceLine || '#94a3b8') : (colors.attributeLine || '#64748b');
                elements.push({ data: { id: edge.id || (edge.source + '-' + edge.target), source: String(edge.source), target: String(edge.target), label: this.state.showLabels ? (edge.label || '') : '', lineStyle, lineColor, meta: edge.meta || edge, ...edge } });
            });
            return elements;
        }

        getCytoscapeStyle() {
            if (this.Styles?.getCytoscapeStyles) return this.Styles.getCytoscapeStyles(this.config.mapId);
            return [
                { selector: 'node', style: { label: 'data(label)', 'background-color': 'data(nodeColor)', 'border-color': 'data(borderColor)', 'border-width': 2, width: 100, height: 60, shape: 'round-rectangle', 'font-size': 12 } },
                { selector: 'edge', style: { width: 2, 'line-color': 'data(lineColor)', 'target-arrow-color': 'data(lineColor)', 'target-arrow-shape': 'triangle', 'curve-style': 'bezier' } }
            ];
        }

        getLayoutConfig() {
            if (this.Styles?.getLayoutConfig) return this.Styles.getLayoutConfig(this.state.layout);
            return { name: 'breadthfirst', directed: true, padding: 60, spacingFactor: 1.8 };
        }

        setupEventListeners() {
            if (!this.state.network) return;
            this.state.network.on('tap', 'node', (evt) => {
                const nodeData = evt.target.data();
                if (this.config.onNodeClick) this.config.onNodeClick(nodeData, evt, this);
                else this.showContextMenu(evt, nodeData);
            });
            this.state.network.on('tap', 'edge', (evt) => { if (this.config.onEdgeClick) this.config.onEdgeClick(evt.target.data(), evt, this); });
            this.state.network.on('tap', (evt) => {
                if (evt.target === this.state.network) {
                    if (this.config.onBackgroundClick) this.config.onBackgroundClick(evt, this);
                    else { this.resetHighlights(); this.hideContextMenu(); }
                }
            });
        }

        showContextMenu(evt, nodeData) {
            const defaultItems = [
                { label: 'Go to Object',      icon: 'fa-external-link-alt', action: () => this.goToObject(nodeData) },
                { type: 'separator' },
                { label: 'Focus object',      icon: 'fa-crosshairs',  action: () => this.focusNode(nodeData.id) },
                { label: 'Hide object',       icon: 'fa-eye-slash',   action: () => this.hideNode(nodeData.id) },
                { type: 'separator' },
                { label: 'Hide upstream',     icon: 'fa-arrow-left',  action: () => this.hideUpstream(nodeData.id) },
                { label: 'Show upstream',     icon: 'fa-arrow-left',  action: () => this.showUpstream(nodeData.id) },
                { label: 'Hide downstream',   icon: 'fa-arrow-right', action: () => this.hideDownstream(nodeData.id) },
                { label: 'Show downstream',   icon: 'fa-arrow-right', action: () => this.showDownstream(nodeData.id) }
            ];
            const menuItems = this.config.contextMenuItems.length > 0 ? this.config.contextMenuItems : defaultItems;
            if (window.MapRenderUtils && typeof window.MapRenderUtils.showInterfaceMapContextMenuFromItems === 'function') {
                window.MapRenderUtils.showInterfaceMapContextMenuFromItems({
                    evt: evt,
                    nodeData: nodeData,
                    host: this,
                    items: menuItems
                });
                return;
            }
            this.hideContextMenu();
            const menu = document.createElement('div');
            menu.className = 'interface-map-context-menu';
            menu.style.cssText = 'position:fixed;left:' + (evt.originalEvent?.clientX || 100) + 'px;top:' + (evt.originalEvent?.clientY || 100) + 'px;z-index:10000;';
            const header = document.createElement('div');
            header.className = 'context-menu-header';
            header.textContent = nodeData.label || nodeData.id;
            menu.appendChild(header);
            menuItems.forEach(item => {
                if (item.type === 'separator') { const sep = document.createElement('div'); sep.className = 'context-menu-separator'; menu.appendChild(sep); }
                else {
                    const mi = document.createElement('div'); mi.className = 'context-menu-item';
                    mi.innerHTML = '<i class="fas ' + (item.icon || 'fa-circle') + '"></i> ' + item.label;
                    mi.addEventListener('click', () => { item.action(nodeData, this); this.hideContextMenu(); });
                    menu.appendChild(mi);
                }
            });
            document.body.appendChild(menu);
            setTimeout(() => { const close = (e) => { if (!menu.contains(e.target)) { this.hideContextMenu(); document.removeEventListener('click', close); } }; document.addEventListener('click', close); }, 0);
        }

        hideContextMenu() { const el = document.querySelector('.interface-map-context-menu'); if (el) el.remove(); }

        goToObject(nodeData) {
            const group = nodeData.group || this.config.facetType;
            const id    = nodeData.id;
            const urlMap = { system: '/view/system/', dataset: '/view/dataset/' };
            window.open((urlMap[group] || '/view/' + group + '/') + encodeURIComponent(id), '_blank');
        }

        highlightConnections(nodeId, direction) {
            direction = direction || 'both';
            if (!this.state.network) return;
            this.resetHighlights();
            const node = this.state.network.getElementById(nodeId);
            if (!node) return;
            this.state.focusedNode = nodeId;
            this.state.network.elements().addClass('dimmed');
            node.removeClass('dimmed').addClass('focused');
            if (direction === 'both' || direction === 'upstream')   { const us = node.predecessors(); us.removeClass('dimmed'); us.edges().addClass('highlighted-upstream'); }
            if (direction === 'both' || direction === 'downstream') { const ds = node.successors();   ds.removeClass('dimmed'); ds.edges().addClass('highlighted-downstream'); }
        }

        resetHighlights() { if (this.state.network) { this.state.network.elements().removeClass('dimmed focused highlighted-upstream highlighted-downstream'); this.state.focusedNode = null; } }

        hideNode(nodeId)  { this.state.hiddenNodes.add(nodeId);    this.refreshVisibility(); }
        showNode(nodeId)  { this.state.hiddenNodes.delete(nodeId); this.refreshVisibility(); }
        focusNode(nodeId) { this.highlightConnections(nodeId, 'both'); }

        hideUpstream(nodeId) {
            if (!this.state.network) return;
            const node = this.state.network.getElementById(nodeId);
            if (!node) return;
            node.predecessors('node').forEach(n => { if (n.id() !== nodeId) this.state.hiddenUpstreamNodes.add(n.id()); });
            this.refreshVisibility();
        }
        showUpstream(nodeId) {
            if (!this.state.network) return;
            this.state.network.getElementById(nodeId)?.predecessors('node').forEach(n => this.state.hiddenUpstreamNodes.delete(n.id()));
            this.refreshVisibility();
        }
        hideDownstream(nodeId) {
            if (!this.state.network) return;
            const node = this.state.network.getElementById(nodeId);
            if (!node) return;
            node.successors('node').forEach(n => { if (n.id() !== nodeId) this.state.hiddenDownstreamNodes.add(n.id()); });
            this.refreshVisibility();
        }
        showDownstream(nodeId) {
            if (!this.state.network) return;
            this.state.network.getElementById(nodeId)?.successors('node').forEach(n => this.state.hiddenDownstreamNodes.delete(n.id()));
            this.refreshVisibility();
        }

        refreshVisibility() {
            if (!this.state.network) return;
            const allHidden = new Set([...this.state.hiddenNodes, ...this.state.hiddenUpstreamNodes, ...this.state.hiddenDownstreamNodes]);
            this.state.network.nodes().forEach(node => node.toggleClass('hidden', allHidden.has(node.id())));
            this.state.network.edges().forEach(edge => edge.toggleClass('hidden', allHidden.has(edge.source().id()) || allHidden.has(edge.target().id())));
        }

        setMapType(type) { if (type !== this.state.mapType) { this.state.mapType = type; this.loadAndRender(); } }
        setLayout(layout) { this.state.layout = layout; if (this.state.network) this.state.network.layout(this.getLayoutConfig()).run(); }
        setOverlay(overlay) { this.state.overlay = overlay; this.dispatchEvent('overlayChange', { overlay }); }
        setFilter(filterType, enabled) { if (Object.prototype.hasOwnProperty.call(this.state.filters, filterType)) { this.state.filters[filterType] = enabled; this.loadAndRender(); } }
        setNodeFilters(filters) { this.state.nodeFilters = { ...this.state.nodeFilters, ...filters }; this.applyNodeFilters(); }

        applyNodeFilters() {
            if (!this.state.network) return;
            const { classifications, types, lifecycles } = this.state.nodeFilters;
            this.state.network.nodes().forEach(node => {
                const meta = node.data('meta') || {};
                const pC = classifications.length === 0 || classifications.includes(meta.classification || '');
                const pT = types.length === 0 || types.includes(meta.type || meta.typeName || '');
                const pL = lifecycles.length === 0 || lifecycles.includes(meta.lifecycle || meta.lifecycleName || '');
                node.style('display', (pC && pT && pL) ? 'element' : 'none');
            });
            this.state.network.edges().forEach(edge => {
                edge.style('display', (edge.source().style('display') === 'element' && edge.target().style('display') === 'element') ? 'element' : 'none');
            });
        }

        zoomIn()   { if (this.state.network) { this.state.network.zoom(this.state.network.zoom() * 1.2); this.state.network.center(); } }
        zoomOut()  { if (this.state.network) { this.state.network.zoom(this.state.network.zoom() * 0.8); this.state.network.center(); } }
        resetMap() { this.state.hiddenNodes.clear(); this.state.hiddenUpstreamNodes.clear(); this.state.hiddenDownstreamNodes.clear(); this.state.focusedNode = null; this.resetHighlights(); this.loadAndRender(); }
        redrawMap(){ if (this.state.network) this.state.network.layout(this.getLayoutConfig()).run(); }

        exportAsPng() {
            if (!this.state.network) { alert('No map to export'); return; }
            const filename = this.config.facetType + '-map-' + this.config.entityId + '.png';
            if (typeof window.exportMapWithOverlays === 'function') window.exportMapWithOverlays(this.state.network, this.state.canvas, filename);
            else { const png64 = this.state.network.png({ bg: '#ffffff', full: true, scale: 2 }); const a = document.createElement('a'); a.download = filename; a.href = png64; a.click(); }
        }

        openFullscreen() {
            if (!this.state.network) { alert('No map to open'); return; }
            if (typeof window.openMapFullscreen === 'function') {
                var self = this;
                window.openMapFullscreen({
                    title:              this.config.facetType + ' Map - ' + (this.config.entityId || ''),
                    network:            this.state.network,
                    getCytoscapeStyle:  function () { return self.getCytoscapeStyle(); },
                    layoutName:         this.state.layout || 'top-to-bottom',
                    legendHtml:         this.buildDefaultLegend(),
                    exportFilename:     this.config.facetType + '-map-' + this.config.entityId + '.png',
                    toolbarAnchor:      this.state.canvas,
                    mapType:            this.state.mapType || 'system-lineage',
                    mapTabKind:         'shared-map-' + (this.config.facetType || 'map'),
                    getState:           function () { return self.state; }
                });
            }
        }

        toggleNavigator() {
            const container = this.state.canvas?.parentElement;
            if (!container) return;
            let minimap = container.querySelector('.map-minimap');
            if (minimap) { minimap.style.display = minimap.style.display === 'none' ? 'block' : 'none'; }
            else { this.createMinimap(container); }
        }

        createMinimap(container) {
            const minimap = document.createElement('div');
            minimap.className = 'map-minimap';
            minimap.style.cssText = 'position:absolute;bottom:1rem;right:1rem;width:150px;height:100px;background:var(--card-bg,#ffffff);border:1px solid var(--border-color,#e5e7eb);border-radius:4px;z-index:1000;box-shadow:0 2px 8px rgba(0,0,0,0.1);';
            container.style.position = 'relative';
            container.appendChild(minimap);
            if (this.state.network) {
                const minimapCy = cytoscape({ container: minimap, elements: this.state.network.json().elements, style: [{ selector: 'node', style: { width: 8, height: 8, label: '' } }, { selector: 'edge', style: { width: 1 } }], layout: { name: 'preset' }, minZoom: 0.1, maxZoom: 0.5 });
                this.state.network.on('viewport', () => minimapCy.fit());
            }
        }

        toggleLabels(show) {
            this.state.showLabels = show !== undefined ? show : !this.state.showLabels;
            if (this.state.network) {
                this.state.network.edges().forEach(edge => {
                    if (!edge.data('originalLabel')) edge.data('originalLabel', edge.data('label'));
                    edge.data('label', this.state.showLabels ? edge.data('originalLabel') : '');
                });
            }
        }

        // Alias used by SharedMapControls toggleLabelsBtn handler
        toggleInterfaceLabels(show) { this.toggleLabels(show); }

        updateLegend() {
            const el = document.querySelector(this.config.legendSelector);
            if (!el) return;
            el.innerHTML = this.config.legendBuilder ? this.config.legendBuilder(this.state, this.Styles?.colors) : this.buildDefaultLegend();
        }

        buildDefaultLegend() {
            const colors      = this.Styles?.colors || {};
            const isDataset   = this.state.mapType === 'dataset-lineage';
            const nodeType    = isDataset ? 'Dataset' : 'System';
            const lineageHtml = (typeof window.SharedMapStyles?.getLineageLegendHtml === 'function')
                ? window.SharedMapStyles.getLineageLegendHtml(colors)
                : (typeof window.InterfaceMapStyles?.getLineageLegendHtml === 'function')
                    ? window.InterfaceMapStyles.getLineageLegendHtml(colors)
                    : '<div class="legend-item"><div class="legend-line" style="border-top:2px solid ' + (colors.attributeLine || '#64748b') + ';"></div><span>Data Attribute Lineage</span></div>'
                      + '<div class="legend-item"><div class="legend-line" style="border-top:2px dashed ' + (colors.interfaceLine || '#94a3b8') + ';"></div><span>Interface Connection</span></div>';
            return '<div class="legend-item"><div class="legend-color" style="background-color:' + (colors.currentSystem || '#f97316') + ';"></div><span>Current ' + nodeType + '</span></div>'
                + '<div class="legend-item"><div class="legend-color" style="background-color:' + (colors.otherSystem || '#1f2937') + ';"></div><span>Other ' + nodeType + '</span></div>'
                + '<div class="legend-item"><div class="legend-color" style="background-color:' + (colors.lockedSystem || '#6b7280') + ';border-style:dashed;"></div><span>Locked ' + nodeType + '</span></div>'
                + lineageHtml;
        }

        showLoading() { if (this.state.loadingEl) this.state.loadingEl.style.display = 'flex'; if (this.state.placeholderEl) this.state.placeholderEl.style.display = 'none'; }
        hideLoading() { if (this.state.loadingEl) this.state.loadingEl.style.display = 'none'; }
        showPlaceholder(msg) {
            this.hideLoading();
            if (this.state.placeholderEl) { this.state.placeholderEl.textContent = msg; this.state.placeholderEl.style.display = 'block'; }
            else if (this.state.canvas) {
                this.state.canvas.innerHTML = window.MapRenderUtils.htmlMapCanvasMessage(msg == null ? '' : String(msg));
            }
        }

        dispatchEvent(eventName, detail) { window.dispatchEvent(new CustomEvent('sharedMap:' + eventName, { detail: { ...detail, mapInstance: this } })); }
        getState()   { return this.state; }
        getNetwork() { return this.state.network; }
    }

    SharedMap.dagreRegistered = false;
    window.SharedMap = SharedMap;


    // =========================================================================
    // SECTION 9 – SharedMapCore (factory-function Cytoscape lifecycle)
    // NOTE: The authoritative definition is now in map-engine.js.
    // window.SharedMapCore(cfg) there returns a full MapEngine instance.
    // This definition is kept as a fallback for pages without map-engine.js.
    // The guard prevents overwriting the map-engine.js version.
    // =========================================================================
    /**
     * Factory function alternative to SharedMap (backward compatible).
     * Returns a plain-object public API identical to what callers expect.
     */
    window.SharedMapCore = window.SharedMapCore || function (config) {
        var facetType         = config.facetType;
        var mapId             = config.mapId;
        var defaultMapType    = config.defaultMapType;
        var containerSelector = config.containerSelector;
        var loadingSelector   = config.loadingSelector;
        var placeholderSelector = config.placeholderSelector;
        var dataLoader        = config.dataLoader;
        var nodeBuilder       = config.nodeBuilder;
        var edgeBuilder       = config.edgeBuilder;
        var getNodeIcon       = config.getNodeIcon;
        var contextMenuActions = config.contextMenuActions;
        var legendBuilder     = config.legendBuilder;
        var onNodeClick       = config.onNodeClick;
        var onEdgeClick       = config.onEdgeClick;
        var onBackgroundClick  = config.onBackgroundClick;

        var MapState = {
            initialized: false, network: null, canvas: null, loadingEl: null, placeholderEl: null,
            entityId: null, entityData: null, nodes: [], edges: [],
            hiddenNodes: new Set(), focusedNode: null,
            hiddenUpstreamNodes: new Set(), hiddenDownstreamNodes: new Set(),
            layout: 'top-to-bottom', mapType: defaultMapType || 'system-lineage',
            overlay: 'none', showLabels: true,
            filters: { systemInterfaces: true, dataAttributeLinks: true },
            nodeFilters: { classifications: [], types: [], lifecycles: [] },
            datasetNodeFilters: { types: [], lifecycles: [] }
        };

        var UNIFIED_SHARED_MAP_COLOR_FALLBACK = {
            currentSystem: '#f97316', currentSystemBorder: '#ea580c',
            otherSystem: '#1f2937', otherSystemBorder: '#374151',
            interfaceLine: '#94a3b8', attributeLine: '#64748b',
            upstreamHighlight: '#ef4444', downstreamHighlight: '#22c55e', lockedSystem: '#6b7280'
        };
        function getMapColors() {
            return window.MapRenderUtils.getMapColors(UNIFIED_SHARED_MAP_COLOR_FALLBACK);
        }
        function getNodeSizes() {
            return window.MapRenderUtils.getNodeSizes();
        }

        function showLoading() { if (MapState.loadingEl) MapState.loadingEl.style.display = 'flex'; if (MapState.placeholderEl) MapState.placeholderEl.style.display = 'none'; }
        function hideLoading() { if (MapState.loadingEl) MapState.loadingEl.style.display = 'none'; }
        function showPlaceholder(message) {
            hideLoading();
            if (MapState.placeholderEl) { MapState.placeholderEl.textContent = message; MapState.placeholderEl.style.display = 'block'; }
            else if (MapState.canvas) {
                MapState.canvas.innerHTML = window.MapRenderUtils.htmlMapCanvasMessage(message == null ? '' : String(message));
            }
        }

        function getCytoscapeStyle() {
            if (window.InterfaceMapStyles && window.InterfaceMapStyles.getCytoscapeStyles) return window.InterfaceMapStyles.getCytoscapeStyles(mapId);
            var colors = getMapColors();
            var sizes  = getNodeSizes();
            return [
                { selector: 'node', style: { width: sizes.system?.width || 140, height: sizes.system?.height || 80, shape: 'round-rectangle', 'background-color': colors.otherSystem, 'border-width': 2, 'border-color': colors.otherSystemBorder, label: 'data(label)', 'text-valign': 'center', 'text-halign': 'center', 'text-wrap': 'wrap', 'text-max-width': 120, 'font-size': '12px', 'font-weight': '500', color: '#ffffff', 'text-outline-width': 1, 'text-outline-color': '#000000', 'background-image': 'data(icon)', 'background-fit': 'contain', 'background-position-x': '50%', 'background-position-y': '30%', 'background-width': '60%', 'background-height': '50%' } },
                { selector: 'node[?isCurrent]', style: { 'background-color': colors.currentSystem, 'border-color': colors.currentSystemBorder, 'border-width': 3 } },
                { selector: 'node[?isLocked]',  style: { 'background-color': colors.lockedSystem, opacity: 0.6 } },
                { selector: 'node[?isDataset]', style: { width: sizes.dataset?.width || 140, height: sizes.dataset?.height || 80 } },
                { selector: 'node[?isSystem]',  style: { width: sizes.system?.width || 140,  height: sizes.system?.height || 80 } },
                { selector: 'edge', style: { width: 2, 'line-color': colors.interfaceLine, 'target-arrow-color': colors.interfaceLine, 'target-arrow-shape': 'triangle', 'curve-style': (mapId && window._sharedDropdownApis && window._sharedDropdownApis[mapId] && typeof window._sharedDropdownApis[mapId].getCurveStyle === 'function') ? window._sharedDropdownApis[mapId].getCurveStyle() : 'bezier', label: 'data(label)', 'text-rotation': 'autorotate', 'text-margin-y': -10, 'font-size': '10px', color: '#64748b' } },
                { selector: 'edge[?isAttributeLine]', style: { 'line-color': colors.attributeLine, 'target-arrow-color': colors.attributeLine, 'line-style': 'solid' } },
                { selector: 'edge[?isInterfaceLine]', style: { 'line-color': colors.interfaceLine, 'target-arrow-color': colors.interfaceLine, 'line-style': 'dashed' } },
                { selector: 'edge[?isHighlighted]',   style: { width: 4, 'line-color': colors.upstreamHighlight, 'target-arrow-color': colors.upstreamHighlight } }
            ];
        }

        function buildCytoscapeLayout() {
            var ddApi = mapId && window._sharedDropdownApis && window._sharedDropdownApis[mapId];
            var sf = (ddApi && typeof ddApi.getSpacingFactor === 'function') ? ddApi.getSpacingFactor() : 2.2;
            var sp = (ddApi && typeof ddApi.getSpacingPadding === 'function') ? ddApi.getSpacingPadding() : 90;
            var layoutMap = {
                'left-to-right': { name: 'dagre', rankDir: 'LR', nodeSep: 50, edgeSep: 20, rankSep: 100, padding: sp },
                'right-to-left': { name: 'dagre', rankDir: 'RL', nodeSep: 50, edgeSep: 20, rankSep: 100, padding: sp },
                'top-to-bottom': { name: 'dagre', rankDir: 'TB', nodeSep: 50, edgeSep: 20, rankSep: 100, padding: sp },
                'force': { name: 'cose', idealEdgeLength: Math.round(100 * sf), nodeOverlap: 20, refresh: 20, fit: true, padding: sp, randomize: false, componentSpacing: 100, nodeRepulsion: Math.round(4000000 * sf), edgeElasticity: 100, nestingFactor: 5, gravity: 0.25, numIter: 1000, initialTemp: 200, coolingFactor: 0.95, minTemp: 1.0 }
            };
            var layout = layoutMap[MapState.layout] || layoutMap['left-to-right'];
            if (window.InterfaceMapStyles && window.InterfaceMapStyles.getLayoutConfig) {
                var customLayout = window.InterfaceMapStyles.getLayoutConfig(MapState.layout);
                if (customLayout) return Object.assign({}, layout, customLayout, { padding: customLayout.padding !== undefined ? customLayout.padding : sp });
            }
            return layout;
        }

        function renderNetwork(graph) {
            if (!MapState.canvas) { console.error('[SHARED-MAP] Canvas not found'); return; }
            if (MapState.network) { MapState.network.destroy(); MapState.network = null; }
            MapState.canvas.innerHTML = '';
            if (!graph || (!graph.nodes || graph.nodes.length === 0)) { showPlaceholder('No data to display.'); return; }
            hideLoading();
            try {
                var elements = [];
                if (graph.nodes) {
                    graph.nodes.forEach(function (node) {
                        var nodeData = { data: Object.assign({ id: String(node.id), label: node.label || node.name || String(node.id) }, node) };
                        if (getNodeIcon) { var iconUrl = getNodeIcon(node); if (iconUrl) nodeData.data.icon = iconUrl; }
                        elements.push(nodeData);
                    });
                }
                if (graph.edges) {
                    graph.edges.forEach(function (edge) {
                        elements.push({ data: Object.assign({ id: edge.id || (edge.source + '-' + edge.target), source: String(edge.source), target: String(edge.target), label: edge.label || '' }, edge) });
                    });
                }
                MapState.network = cytoscape({ container: MapState.canvas, elements: elements, style: getCytoscapeStyle(), layout: buildCytoscapeLayout(), minZoom: 0.1, maxZoom: 2, wheelSensitivity: 0.2 });
                setupEventListeners();
                setTimeout(function () { if (MapState.network) { MapState.network.fit(); MapState.network.center(); } }, 100);
            } catch (error) { console.error('[SHARED-MAP] Error rendering network:', error); showPlaceholder('Error rendering map: ' + error.message); }
        }

        function setupEventListeners() {
            if (!MapState.network) return;
            MapState.network.on('tap', 'node', function (evt) {
                var nodeData = evt.target.data();
                if (onNodeClick) onNodeClick(nodeData, evt); else showNodeContextMenu(evt, nodeData);
            });
            MapState.network.on('tap', 'edge', function (evt) { if (onEdgeClick) onEdgeClick(evt.target.data(), evt); });
            MapState.network.on('tap', function (evt) { if (evt.target === MapState.network) { if (onBackgroundClick) onBackgroundClick(evt); else resetHighlights(); } });
        }

        function showNodeContextMenu(evt, nodeData) {
            if (window.MapRenderUtils && typeof window.MapRenderUtils.showMapContextMenuFromActions === 'function') {
                window.MapRenderUtils.showMapContextMenuFromActions({
                    evt: evt,
                    nodeData: nodeData,
                    actions: contextMenuActions
                });
                return;
            }
            var existingMenu = document.querySelector('.map-context-menu');
            if (existingMenu) existingMenu.remove();
            if (!contextMenuActions || !contextMenuActions.length) return;
            var menu = document.createElement('div');
            menu.className = 'map-context-menu';
            menu.style.cssText = 'position:fixed;left:' + (evt.originalEvent?.clientX || 0) + 'px;top:' + (evt.originalEvent?.clientY || 0) + 'px;background:white;border:1px solid #e5e7eb;border-radius:4px;box-shadow:0 4px 6px rgba(0,0,0,0.1);z-index:10000;min-width:200px;';
            contextMenuActions.forEach(function (action) {
                var item = document.createElement('div');
                item.className = 'map-context-menu-item';
                item.textContent = action.label;
                item.style.cssText = 'padding:8px 16px;cursor:pointer;border-bottom:1px solid #f3f4f6;';
                item.addEventListener('mouseenter', function () { item.style.background = '#f9fafb'; });
                item.addEventListener('mouseleave', function () { item.style.background = 'white'; });
                item.addEventListener('click', function () { action.action(nodeData); menu.remove(); });
                menu.appendChild(item);
            });
            document.body.appendChild(menu);
            setTimeout(function () { document.addEventListener('click', function closeMenu() { menu.remove(); document.removeEventListener('click', closeMenu); }, { once: true }); }, 0);
        }

        function resetHighlights() { if (MapState.network) { MapState.network.elements().removeClass('highlighted'); MapState.focusedNode = null; } }

        function highlightConnections(nodeId, direction) {
            direction = direction || 'both';
            if (!MapState.network) return;
            resetHighlights();
            var node = MapState.network.getElementById(nodeId);
            if (!node) return;
            MapState.focusedNode = nodeId;
            node.addClass('highlighted');
            if (direction === 'both' || direction === 'upstream')   node.incomers().addClass('highlighted');
            if (direction === 'both' || direction === 'downstream') node.outgoers().addClass('highlighted');
        }

        async function init(entityId) {
            _mapDbgLog('[SHARED-MAP-' + facetType.toUpperCase() + '] Initializing map for ' + facetType + ':', entityId);
            MapState.entityId    = entityId;
            MapState.canvas      = document.querySelector(containerSelector);
            MapState.loadingEl   = document.querySelector(loadingSelector);
            MapState.placeholderEl = document.querySelector(placeholderSelector);
            if (!MapState.canvas) { console.error('[SHARED-MAP-' + facetType.toUpperCase() + '] Canvas element not found:', containerSelector); return; }
            if (typeof cytoscape === 'undefined') { console.error('[SHARED-MAP-' + facetType.toUpperCase() + '] Cytoscape library not available'); showPlaceholder('Visualization library is not available.'); return; }
            MapState.initialized = true;
            showLoading();
            try {
                var data  = await dataLoader(entityId);
                var nodes = nodeBuilder ? nodeBuilder(data) : (data.nodes || []);
                var edges = edgeBuilder ? edgeBuilder(data) : (data.edges || []);
                MapState.nodes = nodes;
                MapState.edges = edges;
                renderNetwork({ nodes, edges });
                if (legendBuilder) updateLegend();
            } catch (error) { console.error('[SHARED-MAP-' + facetType.toUpperCase() + '] Failed to load map data:', error); showPlaceholder('Failed to load map data.'); }
            finally { hideLoading(); }
        }

        function updateLegend() {
            if (!legendBuilder) return;
            var el = document.querySelector('[data-map-legend]');
            if (el) el.innerHTML = legendBuilder();
        }

        var publicApi = {
            init,
            setMapType: function (type) { MapState.mapType = type; init(MapState.entityId); },
            setLayout:  function (layout) { MapState.layout = layout; if (MapState.network) MapState.network.layout(buildCytoscapeLayout()).run(); },
            setOverlay: function (overlay) { MapState.overlay = overlay; if (MapState.network) renderNetwork({ nodes: MapState.nodes, edges: MapState.edges }); },
            setFilter:  function (filterType, enabled) { if (Object.prototype.hasOwnProperty.call(MapState.filters, filterType)) { MapState.filters[filterType] = enabled; init(MapState.entityId); } },
            setNodeFilters: function (filters) { MapState.nodeFilters = Object.assign({}, MapState.nodeFilters, filters); init(MapState.entityId); },
            setDatasetNodeFilters: function (filters) { MapState.datasetNodeFilters = Object.assign({}, MapState.datasetNodeFilters, filters); init(MapState.entityId); },
            zoomIn:  function () { if (MapState.network) { MapState.network.zoom(MapState.network.zoom() * 1.2); MapState.network.center(); } },
            zoomOut: function () { if (MapState.network) { MapState.network.zoom(MapState.network.zoom() * 0.8); MapState.network.center(); } },
            resetMap: function () { MapState.hiddenNodes.clear(); MapState.focusedNode = null; MapState.hiddenUpstreamNodes.clear(); MapState.hiddenDownstreamNodes.clear(); resetHighlights(); init(MapState.entityId); },
            redrawMap: function () { if (MapState.network) MapState.network.layout(buildCytoscapeLayout()).run(); },
            exportAsPng: function () {
                if (!MapState.network) { alert('No map to export'); return; }
                var filename = facetType + '-map-' + MapState.entityId + '.png';
                if (typeof window.exportMapWithOverlays === 'function') window.exportMapWithOverlays(MapState.network, MapState.canvas, filename);
                else { var png64 = MapState.network.png({ bg: '#ffffff', full: true, scale: 2 }); var a = document.createElement('a'); a.download = filename; a.href = png64; a.click(); }
            },
            openFullscreen: function () {
                if (!MapState.network) { alert('No map to open'); return; }
                if (typeof window.openMapFullscreen === 'function') {
                    window._fsSharedMapCoreInstance = publicApi;
                    window.openMapFullscreen({
                        title: facetType + ' Map - ' + (MapState.entityData?.name || facetType),
                        network: MapState.network,
                        getCytoscapeStyle: getCytoscapeStyle,
                        layoutName: MapState.layout || 'top-to-bottom',
                        legendHtml: legendBuilder ? legendBuilder() : '',
                        exportFilename: facetType + '-map-' + MapState.entityId + '.png',
                        toolbarAnchor: MapState.canvas,
                        mapType: MapState.mapType || 'system-lineage',
                        mapTabKind: 'shared-map-core-' + facetType,
                        getState: function () { return MapState; }
                    });
                }
            },
            toggleNavigator: function () {
                var container = MapState.canvas?.parentElement;
                if (!container) return;
                var minimap = container.querySelector('.map-minimap');
                if (minimap) { minimap.style.display = minimap.style.display === 'none' ? 'block' : 'none'; }
                else {
                    minimap = document.createElement('div');
                    minimap.className = 'map-minimap';
                    minimap.style.cssText = 'position:absolute;bottom:1rem;right:1rem;width:150px;height:100px;background:var(--card-bg,#ffffff);border:1px solid var(--border-color,#e5e7eb);border-radius:4px;z-index:1000;box-shadow:0 2px 8px rgba(0,0,0,0.1);';
                    container.style.position = 'relative';
                    container.appendChild(minimap);
                    if (MapState.network) { var minimapCy = cytoscape({ container: minimap, elements: MapState.network.json().elements, style: [{ selector: 'node', style: { width: 8, height: 8, label: '' } }, { selector: 'edge', style: { width: 1 } }], layout: { name: 'preset' }, minZoom: 0.1, maxZoom: 0.5 }); MapState.network.on('viewport', function () { minimapCy.fit(); }); }
                }
            },
            toggleInterfaceLabels: function (show) { MapState.showLabels = show !== undefined ? show : !MapState.showLabels; renderNetwork({ nodes: MapState.nodes, edges: MapState.edges }); },
            highlightConnections,
            resetHighlights,
            getState: function () { return MapState; },
            updateLegend
        };

        return publicApi;
    };


    // =========================================================================
    // SECTION 10 – MapController (unified factory)
    // =========================================================================
    /**
     * MapController ties Sections 1-9 together into a single easy-to-use API.
     *
     * Quick start:
     *   const ctrl = MapController.create({
     *     mapId:       'systemMap',
     *     mapType:     'system-lineage',       // auto-loads CSS
     *     container:   document.getElementById('myContainer'),  // inject HTML here
     *     entityId:    42,
     *     dataLoader:  async (id, type, filters) => { ... },
     *     // optional:
     *     mapTypeOptions: [{ value: 'system-lineage', label: 'System Lineage' }],
     *     overlayConfig:  { … },
     *     filterConfig:   { … },
     *     useSharedMap:   true,   // true → SharedMap class, false → SharedMapCore factory
     *     mapConfig:      { … }   // extra config passed to SharedMap or SharedMapCore
     *   });
     *
     *   // After map data is loaded from your page code:
     *   ctrl.controls.switchMenus('dataset-lineage');
     *
     * Register a new map type dynamically:
     *   MapController.registerMapType('custom-map', {
     *     css: ['/assets/css/custom-map.css']
     *   });
     */
    window.MapController = {

        /**
         * Register a new map type (or override an existing one).
         * @param {string} mapType  e.g. 'my-custom-map'
         * @param {Object} cfg      { css: [] }
         */
        registerMapType: function (mapType, cfg) {
            _mapTypeRegistry[mapType] = cfg || {};
        },

        /**
         * Get the registry entry for a map type.
         * @param {string} mapType
         * @returns {Object|undefined}
         */
        getMapTypeConfig: function (mapType) {
            return _mapTypeRegistry[mapType];
        },

        /**
         * Manually trigger CSS loading for a map type.
         * @param {string} mapType
         */
        loadCSS: loadMapCSS,

        /**
         * Create a fully wired map controller instance.
         *
         * Returns { html, mapInstance, controls, dropdowns, loadCSS }
         *
         * @param {Object} opts
         */
        create: function (opts) {
            var mapId          = opts.mapId || 'map';
            var mapType        = opts.mapType || 'system-lineage';
            var container      = opts.container;        // DOM element to inject toolbar HTML into
            var entityId       = opts.entityId;
            var dataLoader     = opts.dataLoader;
            var useSharedMap   = opts.useSharedMap !== false; // default: use SharedMap class
            var mapConfig      = opts.mapConfig || {};

            // 1. Auto-load CSS for the declared map type
            loadMapCSS(mapType);

            // 2. Build toolbar HTML
            var htmlConfig = {
                mapId:          mapId,
                defaultMapType: mapType,
                mapTypeOptions: opts.mapTypeOptions,
                overlayConfig:  opts.overlayConfig,
                filterConfig:   opts.filterConfig
            };
            // Remove undefined keys so SharedMapHTML picks its own defaults
            Object.keys(htmlConfig).forEach(function (k) { if (htmlConfig[k] === undefined) delete htmlConfig[k]; });

            var toolbarHtml = window.SharedMapHTML(htmlConfig);

            // 3. Inject HTML into container (if provided)
            if (container) container.innerHTML = toolbarHtml;

            // 4. Create map instance
            var mapInstance;
            if (useSharedMap) {
                mapInstance = new window.SharedMap(Object.assign({
                    mapId:          mapId,
                    containerId:    mapId + 'Canvas',
                    facetType:      mapType.split('-')[0],
                    entityId:       entityId,
                    defaultMapType: mapType,
                    dataLoader:     dataLoader
                }, mapConfig));
            } else {
                mapInstance = window.SharedMapCore(Object.assign({
                    facetType:         mapType.split('-')[0],
                    mapId:             mapId,
                    defaultMapType:    mapType,
                    containerSelector: '#' + mapId + 'Canvas',
                    loadingSelector:   '[data-map-loading]',
                    placeholderSelector: '[data-map-placeholder]',
                    dataLoader:        dataLoader
                }, mapConfig));
            }

            // 5. Wire up spacing/edge-style dropdowns
            var dropdowns = window.SharedMapDropdowns({
                mapId:      mapId,
                getNetwork: function () { return mapInstance && (typeof mapInstance.getNetwork === 'function' ? mapInstance.getNetwork() : (mapInstance.getState ? mapInstance.getState().network : null)); },
                setLayout:  function (dir) { if (mapInstance) mapInstance.setLayout(dir); }
            });

            // 6. Wire toolbar controls to map instance
            var controls = window.SharedMapControls({
                mapId:       mapId,
                mapInstance: mapInstance,
                onMapTypeChange: opts.onMapTypeChange,
                onFilterChange:  opts.onFilterChange,
                updateFilterLabel: opts.updateFilterLabel
            });

            return {
                mapId:       mapId,
                mapInstance: mapInstance,
                controls:    controls,
                dropdowns:   dropdowns,
                toolbarHtml: toolbarHtml,
                loadCSS:     function () { loadMapCSS(mapType); }
            };
        }
    };

})();
