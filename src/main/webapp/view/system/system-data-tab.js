// System Data Tab JavaScript - Isolated module for Datasets and Data Attributes

(function() {
    let currentSystemId = null;
    let currentViewMode = 'original'; // 'original' | 'changes'

    // Helper function to escape HTML
    function escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    function systemDataRecordLabel(n) {
        const I = window.I18n && window.I18n.t.bind(window.I18n);
        if (n === 0 && I) return I('system.data.zeroRecords') || '0 records';
        if (n === 1 && I) return I('system.data.recordOne') || '1 record';
        if (I) return (I('system.data.recordCount') || '{count} records').replace('{count}', n);
        return n + ' record' + (n === 1 ? '' : 's');
    }

    // Helper function to create entity links
    function createEntityLink(entityType, id, name) {
        if (!id || !name) return escapeHtml(name || 'N/A');
        
        let url;
        switch(entityType) {
            case 'system':
                url = `/view/system/${encodeURIComponent(id)}`;
                break;
            case 'dataset':
                url = `/view/dataset/${encodeURIComponent(id)}`;
                break;
            case 'glossary':
                url = `/view/glossary/${encodeURIComponent(id)}`;
                break;
            default:
                return escapeHtml(name);
        }
        
        return `<a href="${url}" class="relationship-link" title="View ${escapeHtml(name)}">${escapeHtml(name)}</a>`;
    }

    // Initialize Data Tab
    function init(systemId, viewMode = 'original') {
        currentSystemId = systemId;
        currentViewMode = viewMode || 'original';
        console.log('Initializing System Data Tab for system ID:', systemId);
        
        const container = document.getElementById('systemViewContainer');
        if (!container) {
            console.error('systemViewContainer not found');
            return;
        }

        const sDT = (k) => (window.I18n && window.I18n.t(k)) || k;
        // Render data tab HTML
        container.innerHTML = `
            <div class="view-section" style="grid-column:1/-1;">
                <div class="data-container">
                    <div class="data-sub-tabs">
                        <button class="sub-tab active" data-sub-tab="data-sets">${sDT('system.data.dataSets')}</button>
                        <button class="sub-tab" data-sub-tab="data-attributes">${sDT('system.data.dataAttributes')}</button>
                        <button class="sub-tab" data-sub-tab="data-map">${sDT('system.data.dataMap')}</button>
                    </div>
                    <div class="data-content">
                        <div id="dataSetsContent" class="sub-tab-content active">
                            <div class="relationships-section">
                                <div class="relationships-header">
                                    <div class="relationships-title">${sDT('system.data.dataSets')}</div>
                                    <div class="relationships-actions">
                                        ${window._gridSettingsHtml ? window._gridSettingsHtml('dataSets') : ''}
                                    </div>
                                </div>
                                <div class="relationships-table-wrapper">
                                    <table class="relationships-table">
                                        <thead>
                                            <tr>
                                                <th><div class="th-content"><span>${sDT('system.data.ref')}</span></div></th>
                                                <th><div class="th-content"><span>${sDT('system.data.name')}</span></div></th>
                                                <th><div class="th-content"><span>${sDT('system.data.definition')}</span></div></th>
                                                <th><div class="th-content"><span>${sDT('system.data.type')}</span></div></th>
                                                <th><div class="th-content"><span>${sDT('system.data.budgStatus')}</span></div></th>
                                            </tr>
                                        </thead>
                                        <tbody id="dataSetsTableBody">
                                            <tr><td colspan="5" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">${sDT('system.data.loading')}</td></tr>
                                        </tbody>
                                    </table>
                                </div>
                                <div class="relationships-count" id="dataSetsFooter">
                                    ${sDT('system.data.zeroRecords')}
                                </div>
                            </div>
                        </div>
                        <div id="dataAttributesContent" class="sub-tab-content">
                            <div class="relationships-section">
                                <div class="relationships-header">
                                    <div class="relationships-title">${sDT('system.data.dataAttributes')}</div>
                                    <div class="relationships-actions">
                                        ${window._gridSettingsHtml ? window._gridSettingsHtml('dataAttributes') : ''}
                                    </div>
                                </div>
                                <div class="relationships-table-wrapper">
                                    <table class="relationships-table">
                                        <thead>
                                            <tr>
                                                <th><div class="th-content"><span>Data Set</span></div></th>
                                                <th><div class="th-content"><span>Glossary</span></div></th>
                                                <th><div class="th-content"><span>Attribute Glossary</span></div></th>
                                                <th><div class="th-content"><span>Attribute Name</span></div></th>
                                                <th><div class="th-content"><span>Key</span></div></th>
                                                <th><div class="th-content"><span>Rank</span></div></th>
                                                <th><div class="th-content"><span>Ref.</span></div></th>
                                                <th><div class="th-content"><span>DB Field Name</span></div></th>
                                                <th><div class="th-content"><span>Definition</span></div></th>
                                                <th><div class="th-content"><span>KDE</span></div></th>
                                                <th><div class="th-content"><span>Attribute Glossary Definition</span></div></th>
                                                <th><div class="th-content"><span>Origin</span></div></th>
                                                <th><div class="th-content"><span>Physical Fields</span></div></th>
                                                <th><div class="th-content"><span>Review Status</span></div></th>
                                                <th><div class="th-content"><span>Confidence Score(%)</span></div></th>
                                                <th><div class="th-content"><span>Requirement</span></div></th>
                                                <th><div class="th-content"><span>Business Logic</span></div></th>
                                                <th><div class="th-content"><span>Data Type</span></div></th>
                                                <th><div class="th-content"><span>Data Length</span></div></th>
                                                <th><div class="th-content"><span>Related To</span></div></th>
                                                <th><div class="th-content"><span>Relationship Type</span></div></th>
                                                <th><div class="th-content"><span>Interface</span></div></th>
                                                <th><div class="th-content"><span>Sourcing Logic</span></div></th>
                                            </tr>
                                        </thead>
                                        <tbody id="dataAttributesTableBody">
                                            <tr><td colspan="23" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">${sDT('system.data.loading')}</td></tr>
                                        </tbody>
                                    </table>
                                </div>
                                <div class="relationships-count" id="dataAttributesFooter">
                                    ${sDT('system.data.zeroRecords')}
                                </div>
                            </div>
                        </div>
                        <div id="dataMapContent" class="sub-tab-content">
                            <div class="interface-map-container" style="height: 100%; display: flex; flex-direction: column;">
                                <!-- Map Toolbar -->
                                <div class="interface-map-toolbar">
                                    <!-- Map Type -->
                                    <div class="map-control-group">
                                        <label>Map type:</label>
                                        <select id="dataMapTypeSelect" class="map-select" disabled>
                                            <option value="dataset-lineage" selected>Data Set Lineage</option>
                                        </select>
                                    </div>
                                    
                                    <!-- Layout -->
                                    <div class="map-control-group">
                                        <label>Layout:</label>
                                        <div class="map-layout-controls">
                                            <select id="dataMapLayoutSelect" class="map-select">
                                                <option value="top-to-bottom" selected>Top-To-Bottom</option>
                                                <option value="left-to-right">Left-To-Right</option>
                                                <option value="right-to-left">Right-To-Left</option>
                                                <option value="force">Force Directed</option>
                                            </select>
                                            <div class="map-btn-dropdown-wrapper">
                                                <button type="button" class="map-toolbar-btn-sm" id="dataMapSpacing" title="Node spacing">
                                                    <i class="fas fa-expand-arrows-alt" id="dataMapSpacingIcon"></i>
                                                    <i class="fas fa-chevron-down" style="font-size: 8px; margin-left: 2px;"></i>
                                                </button>
                                                <div class="map-btn-dropdown-menu" id="dataMapSpacingMenu">
                                                    <div class="map-btn-dropdown-item" data-spacing="compact">Compact</div>
                                                    <div class="map-btn-dropdown-item active" data-spacing="normal">Normal</div>
                                                    <div class="map-btn-dropdown-item" data-spacing="spacey">Spacey</div>
                                                </div>
                                            </div>
                                            <div class="map-btn-dropdown-wrapper">
                                                <button type="button" class="map-toolbar-btn-sm" id="dataMapEdgeStyle" title="Edge routing style">
                                                    <i class="fas fa-arrow-right" id="dataMapEdgeStyleIcon"></i>
                                                    <i class="fas fa-chevron-down" style="font-size: 8px; margin-left: 2px;"></i>
                                                </button>
                                                <div class="map-btn-dropdown-menu" id="dataMapEdgeStyleMenu">
                                                    <div class="map-btn-dropdown-item" data-edge-style="angle">Angle</div>
                                                    <div class="map-btn-dropdown-item" data-edge-style="square">Square</div>
                                                    <div class="map-btn-dropdown-item active" data-edge-style="direct">Direct</div>
                                                    <div class="map-btn-dropdown-item" data-edge-style="loop">Loop</div>
                                                    <div class="map-btn-dropdown-item" data-edge-style="top-down">Top-Down</div>
                                                    <div class="map-btn-dropdown-item" data-edge-style="left-right">Left-Right</div>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                    
                                    <!-- Hops Count -->
                                    <div class="map-control-group map-hops-group" id="dataMapHopsGroup">
                                        <label>Hops:</label>
                                        <input type="number" id="dataMapHopsCount" class="map-hops-input" min="1" max="99" value="15" title="Upstream/downstream lineage depth (1-99, recommend 15)">
                                    </div>

                                    <!-- Overlay -->
                                    <div class="map-control-group">
                                        <label>Overlay:</label>
                                        <div class="map-overlay-controls">
                                            <div class="map-overlay-dropdown">
                                                <button type="button" class="map-select-btn" id="dataMapOverlayBtn">
                                                    <span>None</span>
                                                    <i class="fas fa-chevron-down"></i>
                                                </button>
                                                <!-- Dataset Lineage Overlay Menu -->
                                                <div class="map-overlay-menu" id="dataMapOverlayMenu" data-map-type="dataset-lineage">
                                                    <div class="overlay-menu-grid dataset-overlay-grid">
                                                        <div class="overlay-menu-column">
                                                            <div class="overlay-menu-header">Data</div>
                                                            <div class="overlay-menu-item" data-overlay="description">
                                                                <i class="fas fa-info-circle"></i> Description
                                                            </div>
                                                            <div class="overlay-menu-item" data-overlay="glossary">
                                                                <i class="fas fa-book"></i> Glossary
                                                            </div>
                                                            <div class="overlay-menu-item" data-overlay="attributes">
                                                                <i class="fas fa-th"></i> Attributes
                                                            </div>
                                                            <div class="overlay-menu-item" data-overlay="linking-attributes">
                                                                <i class="fas fa-th"></i> Linking Attributes
                                                            </div>
                                                            <div class="overlay-menu-item" data-overlay="data-quality">
                                                                <i class="fas fa-bullseye"></i> Data Quality
                                                            </div>
                                                        </div>
                                                        <div class="overlay-menu-column">
                                                            <div class="overlay-menu-header">Business</div>
                                                            <div class="overlay-menu-item" data-overlay="stakeholders">
                                                                <i class="fas fa-users"></i> Stakeholders
                                                            </div>
                                                            <div class="overlay-menu-item" data-overlay="processes">
                                                                <i class="fas fa-play-circle"></i> Processes
                                                            </div>
                                                            <div class="overlay-menu-item" data-overlay="projects">
                                                                <i class="fas fa-tasks"></i> Projects
                                                            </div>
                                                            <div class="overlay-menu-item" data-overlay="policies">
                                                                <i class="fas fa-file-alt"></i> Policies
                                                            </div>
                                                        </div>
                                                    </div>
                                                    <div class="overlay-menu-footer">
                                                        <button type="button" class="overlay-clear-btn" id="dataMapClearOverlaysBtn">Clear Overlays</button>
                                                    </div>
                                                </div>
                                            </div>
                                            <button type="button" class="map-toolbar-btn-sm" id="dataMapOverlayGrid" title="Overlay fields as columns">
                                                <i class="fas fa-th"></i>
                                                <i class="fas fa-chevron-down" style="font-size: 8px; margin-left: 2px;"></i>
                                            </button>
                                            <div class="map-overlay-columns-menu map-filter-menu" id="dataMapOverlayColumnsMenu" style="display: none;">
                                                <div class="map-filter-category">
                                                    <div class="map-filter-category-header">OVERLAY FIELDS AS COLUMNS</div>
                                                    <div id="dataMapOverlayColumnsOptions" class="map-filter-options-container"></div>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                    
                                    <!-- Filters -->
                                    <div class="map-control-group">
                                        <label>Filters:</label>
                                        <div class="map-filter-dropdown">
                                            <button type="button" class="map-select-btn" id="dataMapFilterBtn">
                                                <span id="dataMapFilterBtnText">All selected (0)</span>
                                                <i class="fas fa-chevron-down"></i>
                                            </button>
                                            <!-- Dataset Lineage Filter Menu -->
                                            <div class="map-filter-menu" id="dataMapFilterMenu" data-map-type="dataset-lineage">
                                                <div class="map-filter-category">
                                                    <div class="map-filter-category-header">TYPE</div>
                                                    <div id="dataMapFilterTypeOptions">
                                                        <!-- Dynamic options will be added here -->
                                                    </div>
                                                </div>
                                                <div class="map-filter-separator"></div>
                                                <div class="map-filter-category">
                                                    <div class="map-filter-category-header">LIFECYCLE</div>
                                                    <div id="dataMapFilterLifecycleOptions">
                                                        <!-- Dynamic options will be added here -->
                                                    </div>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                    
                                    <!-- Toolbar Buttons -->
                                    <div class="map-toolbar-buttons">
                                        <button type="button" class="map-toolbar-btn" id="dataMapToggleLabels" title="Toggle labels">
                                            <i class="fas fa-exchange-alt"></i>
                                        </button>
                                        <div class="map-toolbar-separator"></div>
                                        <button type="button" class="map-toolbar-btn" id="dataMapZoomIn" title="Zoom in">
                                            <i class="fas fa-search-plus"></i>
                                        </button>
                                        <button type="button" class="map-toolbar-btn" id="dataMapZoomOut" title="Zoom out">
                                            <i class="fas fa-search-minus"></i>
                                        </button>
                                        <div class="map-toolbar-separator"></div>
                                        <button type="button" class="map-toolbar-btn" id="dataMapRedraw" title="Redraw">
                                            <i class="fas fa-sync-alt"></i>
                                        </button>
                                        <button type="button" class="map-toolbar-btn" id="dataMapReset" title="Reset">
                                            <i class="fas fa-undo"></i>
                                        </button>
                                        <button type="button" class="map-toolbar-btn" id="dataMapExport" title="Export as PNG">
                                            <i class="fas fa-save"></i>
                                        </button>
                                        <div class="map-toolbar-separator"></div>
                                        <button type="button" class="map-toolbar-btn" id="dataMapNavigator" title="Map navigator">
                                            <i class="fas fa-eye"></i>
                                        </button>
                                        <button type="button" class="map-toolbar-btn" id="dataMapFullscreen" title="Fullscreen">
                                            <i class="fas fa-external-link-alt"></i>
                                        </button>
                                        <button type="button" class="map-toolbar-btn" id="dataMapLegend" title="Open the Legend" aria-label="Open the Legend – refer to the legend to identify the symbols used in the map (Insight Maps Palette)">
                                            <i class="fas fa-list-ul"></i>
                                        </button>
                                    </div>
                                </div>
                                
                                <!-- Map Body -->
                                <div class="interface-map-body" style="flex: 1; position: relative;">
                                    <div class="interface-map-canvas" id="dataMapCanvas" style="width: 100%; height: 100%; position: relative;">
                                        <div class="interface-map-loading" data-interface-map-loading style="display:none; position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%); text-align: center; z-index: 10;">
                                            <i class="fas fa-spinner fa-spin"></i>
                                            <span>Building dataset lineage map...</span>
                                        </div>
                                    </div>
                                    <div class="interface-map-side-panel" id="dataMapSidePanel" style="display:none;">
                                        <div class="map-side-panel-section">
                                            <h4><i class="fas fa-info-circle"></i> Selection</h4>
                                            <div class="selection-placeholder" data-interface-map-placeholder>
                                                Select a node to see its details.
                                            </div>
                                            <div class="selection-info" data-interface-map-details style="display:none;"></div>
                                        </div>
                                        <div class="map-side-panel-section">
                                            <h4 class="map-legend-palette-title"><i class="fas fa-layer-group"></i> Insight Maps Palette</h4>
                                            <div data-interface-map-legend></div>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        `;

        // Initialize grid settings for data tab tables
        if (window._initGridSettings) window._initGridSettings(container);

        // Set up sub-tabs and load initial data
        setupDataSubTabs(systemId);
        
        // Load initial data based on active sub-tab
        setTimeout(() => {
            const activeSubTab = document.querySelector('.data-sub-tabs .sub-tab.active');
            if (activeSubTab) {
                const subTabType = activeSubTab.getAttribute('data-sub-tab');
                if (subTabType === 'data-attributes') {
                    loadDataAttributesData(systemId, currentViewMode);
                } else {
                    loadDatasetsData(systemId, currentViewMode);
                }
            } else {
                // Default to datasets
                loadDatasetsData(systemId, currentViewMode);
            }
        }, 100);
    }

    // Set up data sub-tabs functionality
    function setupDataSubTabs(systemId) {
        const dataSubTabs = document.querySelectorAll('.data-sub-tabs .sub-tab');
        dataSubTabs.forEach(function(btn) {
            btn.addEventListener('click', function() {
                // Only allow switching if the tab is not disabled
                if (this.disabled || this.classList.contains('disabled')) {
                    return;
                }

                dataSubTabs.forEach(function(b) { b.classList.remove('active'); });
                this.classList.add('active');

                const subTabType = this.getAttribute('data-sub-tab');
                const allContents = document.querySelectorAll('.data-content .sub-tab-content');

                allContents.forEach(function(content) {
                    content.classList.remove('active');
                });

                // Convert kebab-case to camelCase for ID lookup
                // data-sets -> dataSetsContent, data-attributes -> dataAttributesContent
                const contentId = subTabType.split('-').map((word, index) => 
                    index === 0 ? word : word.charAt(0).toUpperCase() + word.slice(1)
                ).join('') + 'Content';
                
                const targetContent = document.getElementById(contentId);
                console.log('Sub-tab clicked:', subTabType, 'Target content:', targetContent);
                if (targetContent) {
                    targetContent.classList.add('active');
                    console.log('Content div activated, classList:', targetContent.classList.toString());

                    // Load data when switching tabs
                    if (subTabType === 'data-sets') {
                        console.log('Loading datasets for sub-tab:', subTabType);
                        loadDatasetsData(systemId, currentViewMode);
                    } else if (subTabType === 'data-attributes') {
                        console.log('Loading attributes for sub-tab:', subTabType);
                        loadDataAttributesData(systemId, currentViewMode);
                    } else if (subTabType === 'data-map') {
                        console.log('Initializing data map for sub-tab:', subTabType);
                        initDataMap(systemId);
                    }
                } else {
                    console.error('Target content not found for sub-tab:', subTabType);
                }
            });
        });
    }

    // Load datasets data
    async function loadDatasetsData(systemId, viewMode = 'original') {
        const tbody = document.getElementById('dataSetsTableBody');
        const footer = document.getElementById('dataSetsFooter');

        if (!tbody || !footer) {
            console.error('Required elements not found for datasets table');
            return;
        }

        const sDT = (k) => (window.I18n && window.I18n.t(k)) || k;
        try {
            tbody.innerHTML = '<tr><td colspan="5" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">' + sDT('system.data.loading') + '</td></tr>';

            let response = await window.BUDG_API_SERVICE.getSystemDatasets(systemId, viewMode === 'changes' ? 'changes' : null);
            console.log('Datasets API raw response:', response);
            
            // Handle response that might be wrapped in data property
            let datasets = response;
            if (response && response.data && Array.isArray(response.data)) {
                datasets = response.data;
            } else if (response && Array.isArray(response)) {
                datasets = response;
            }
            
            console.log('Datasets loaded:', datasets);

            if (!Array.isArray(datasets) || datasets.length === 0) {
                tbody.innerHTML = '<tr><td colspan="5" style="text-align:center;padding:2rem;color:var(--text-muted,#9ca3af);">' + sDT('system.data.noDatasetsForSystem') + '</td></tr>';
                footer.textContent = systemDataRecordLabel(0);
                return;
            }

            // Render datasets table
            tbody.innerHTML = datasets.map(function(dataset) {
                return `
                    <tr>
                        <td>${escapeHtml(dataset.refNumber || '-')}</td>
                        <td>
                            <div class="relationship-item">
                                <i class="fas fa-layer-group" style="color: var(--secondary-color, #248567); margin-right: 0.5rem;"></i>
                                ${dataset.id ? createEntityLink('dataset', dataset.id, dataset.primaryName || 'Unknown Dataset') : escapeHtml(dataset.primaryName || '-')}
                            </div>
                        </td>
                        <td>${escapeHtml(dataset.definition || '-')}</td>
                        <td>${escapeHtml(dataset.typeName || '-')}</td>
                        <td>${escapeHtml(dataset.statusName || '-')}</td>
                    </tr>
                `;
            }).join('');

            footer.textContent = systemDataRecordLabel(datasets.length);

        } catch (error) {
            console.error('Failed to load datasets:', error);
            tbody.innerHTML = '<tr><td colspan="5" style="text-align:center;padding:2rem;color:var(--danger,#b91c1c);">Failed to load datasets: ' + escapeHtml(error.message) + '</td></tr>';
            footer.textContent = systemDataRecordLabel(0);
        }
    }

    // Load data attributes data
    async function loadDataAttributesData(systemId, viewMode = 'original') {
        console.log('loadDataAttributesData called with systemId:', systemId);
        const tbody = document.getElementById('dataAttributesTableBody');
        const footer = document.getElementById('dataAttributesFooter');

        if (!tbody || !footer) {
            console.error('Required elements not found for attributes table', { tbody: !!tbody, footer: !!footer });
            return;
        }

        const sDTAttr = (k) => (window.I18n && window.I18n.t(k)) || k;
        try {
            tbody.innerHTML = '<tr><td colspan="23" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">' + sDTAttr('system.data.loading') + '</td></tr>';

            console.log('Calling API: getSystemAttributes(' + systemId + ') view=' + viewMode);
            let response = await window.BUDG_API_SERVICE.getSystemAttributes(systemId, viewMode === 'changes' ? 'changes' : null);
            console.log('Attributes API raw response:', response);
            
            // Handle response that might be wrapped in data property
            let attributes = response;
            if (response && response.data && Array.isArray(response.data)) {
                attributes = response.data;
            } else if (response && Array.isArray(response)) {
                attributes = response;
            } else if (response && typeof response === 'object' && !Array.isArray(response)) {
                // If response is an object but not an array, try to extract data
                console.warn('Unexpected response format:', response);
                attributes = [];
            }
            
            console.log('Attributes after processing:', attributes);
            console.log('Attributes is array?', Array.isArray(attributes));
            console.log('Attributes length:', attributes ? attributes.length : 'null/undefined');

            if (!Array.isArray(attributes) || attributes.length === 0) {
                tbody.innerHTML = '<tr><td colspan="23" style="text-align:center;padding:2rem;color:var(--text-muted,#9ca3af);">' + sDTAttr('system.data.noAttributesForSystem') + '</td></tr>';
                footer.textContent = systemDataRecordLabel(0);
                return;
            }

            // Render attributes table
            tbody.innerHTML = attributes.map(function(attr) {
                const datasetLink = attr.datasetId && attr.datasetName 
                    ? createEntityLink('dataset', attr.datasetId, attr.datasetName)
                    : '<span class="empty">-</span>';
                
                const glossaryLink = attr.datasetGlossaryId && attr.datasetGlossaryName 
                    ? createEntityLink('glossary', attr.datasetGlossaryId, attr.datasetGlossaryName)
                    : '<span class="empty">-</span>';

                const attributeGlossaryLink = attr.attributeGlossaryId && attr.attributeGlossaryName 
                    ? createEntityLink('glossary', attr.attributeGlossaryId, attr.attributeGlossaryName)
                    : '<span class="empty">-</span>';

                const attributeNameLink = attr.attributeId && attr.attributeName
                    ? `<div class="relationship-item"><i class="fas fa-table" style="color: var(--secondary-color, #248567); margin-right: 0.5rem;"></i>${attr.datasetId ? `<a href="/view/dataset/${attr.datasetId}?tab=attribute&attributeId=${attr.attributeId}" class="relationship-link" title="View attribute in dataset">${escapeHtml(attr.attributeName)}</a>` : escapeHtml(attr.attributeName)}</div>`
                    : '<span class="empty">-</span>';

                return `
                    <tr>
                        <td>
                            <div class="relationship-item">
                                <i class="fas fa-layer-group" style="color: var(--secondary-color, #248567); margin-right: 0.5rem;"></i>
                                ${datasetLink}
                            </div>
                        </td>
                        <td>${glossaryLink}</td>
                        <td>${attributeGlossaryLink}</td>
                        <td>${attributeNameLink}</td>
                        <td style="text-align: center;">${attr.isPrimary ? 'Yes' : 'No'}</td>
                        <td>${escapeHtml(attr.attributeRank || '-')}</td>
                        <td>${escapeHtml(attr.attributeRef || '-')}</td>
                        <td>${escapeHtml(attr.dbFieldName || '-')}</td>
                        <td>${escapeHtml(attr.attributeDefinition || '-')}</td>
                        <td>${escapeHtml(attr.kde || '-')}</td>
                        <td>${escapeHtml(attr.attributeGlossaryDefinition || '-')}</td>
                        <td>${escapeHtml(attr.origin || '-')}</td>
                        <td>${escapeHtml(attr.physicalFields || '-')}</td>
                        <td>${escapeHtml(attr.reviewStatus || '-')}</td>
                        <td>${escapeHtml(attr.confidenceScore !== null && attr.confidenceScore !== undefined ? attr.confidenceScore + '%' : '-')}</td>
                        <td>${escapeHtml(attr.requirement || '-')}</td>
                        <td>${escapeHtml(attr.businessLogic || '-')}</td>
                        <td>${escapeHtml(attr.dataType || '-')}</td>
                        <td>${escapeHtml(attr.dataLength || '-')}</td>
                        <td>${escapeHtml(attr.relatedTo || '-')}</td>
                        <td>${escapeHtml(attr.relationshipType || '-')}</td>
                        <td>${escapeHtml(attr.interfaceName || '-')}</td>
                        <td>${escapeHtml(attr.sourcingLogic || '-')}</td>
                    </tr>
                `;
            }).join('');

            footer.textContent = systemDataRecordLabel(attributes.length);

        } catch (error) {
            console.error('Failed to load attributes:', error);
            tbody.innerHTML = '<tr><td colspan="23" style="text-align:center;padding:2rem;color:var(--danger,#b91c1c);">Failed to load attributes: ' + escapeHtml(error.message) + '</td></tr>';
            footer.textContent = systemDataRecordLabel(0);
        }
    }

    // Initialize Data Map (Dataset Lineage)
    function initDataMap(systemId) {
        console.log('Initializing Data Map for system:', systemId);
        if (systemId == null || String(systemId).trim() === '' || String(systemId).toLowerCase() === 'null') {
            console.error('initDataMap: invalid system id');
            const canvas = document.getElementById('dataMapCanvas');
            if (canvas) {
                canvas.innerHTML = '<div style="padding: 2rem; text-align: center; color: var(--danger, #b91c1c);">Cannot load the data map: system id is missing.</div>';
            }
            return;
        }

        // Check if SystemInterfacesMap is available
        if (!window.SystemInterfacesMap) {
            console.error('SystemInterfacesMap not available');
            const canvas = document.getElementById('dataMapCanvas');
            if (canvas) {
                canvas.innerHTML = '<div style="padding: 2rem; text-align: center; color: var(--text-secondary, #6c757d);">Map visualization library not available</div>';
            }
            return;
        }
        
        // Initialize the map with dataset-lineage type
        const canvas = document.getElementById('dataMapCanvas');
        if (!canvas) {
            console.error('Data map canvas not found');
            return;
        }
        
        // Clear any existing content
        const loadingEl = canvas.querySelector('[data-interface-map-loading]');
        if (loadingEl) {
            loadingEl.style.display = 'block';
        }
        
        // Initialize the map
        try {
            // init() must run before any setMapType('dataset-lineage') load: setMapType calls
            // loadDatasetLineageData() which reads InterfaceMapState.systemId — that is only
            // set inside init(). Calling setMapType first produced /api/system/null requests.
            const mapContainer = '#dataMapCanvas';
            window.SystemInterfacesMap.init(systemId, mapContainer);
            
            // Set up event handlers for controls
            setupDataMapControls(systemId);
            
            // Hide loading after a short delay to allow map to render
            setTimeout(() => {
                if (loadingEl) {
                    loadingEl.style.display = 'none';
                }
            }, 500);
            
        } catch (error) {
            console.error('Failed to initialize data map:', error);
            if (loadingEl) {
                loadingEl.style.display = 'none';
            }
            canvas.innerHTML = '<div style="padding: 2rem; text-align: center; color: var(--danger, #b91c1c);">Failed to initialize data map: ' + escapeHtml(error.message) + '</div>';
        }
    }
    
    // Set up event handlers for Data Map controls
    function setupDataMapControls(systemId) {
        const mapRoot = document.getElementById('dataMapSection') || document;
        const byId = (id) => mapRoot.querySelector(`#${id}`) || document.getElementById(id);

        // Layout select
        const layoutSelect = byId('dataMapLayoutSelect');
        if (layoutSelect) {
            layoutSelect.addEventListener('change', (e) => {
                if (window.SystemInterfacesMap) {
                    window.SystemInterfacesMap.setLayout(e.target.value);
                }
            });
        }
        
        // Initialize shared dropdown menus for expand/collapse & direction buttons
        if (typeof window.SharedMapDropdowns === 'function') {
            window.SharedMapDropdowns({
                mapId: 'dataMap',
                getNetwork: () => window.SystemInterfacesMap ? window.SystemInterfacesMap.cy : null,
                setLayout: (dir) => {
                    if (layoutSelect) layoutSelect.value = dir;
                    if (window.SystemInterfacesMap) window.SystemInterfacesMap.setLayout(dir);
                },
                getCanvas: () => byId('dataMapCanvas')
            });
        }
        
        // Overlay button and menu
        const overlayBtn = byId('dataMapOverlayBtn');
        const overlayMenu = byId('dataMapOverlayMenu');
        if (overlayBtn && overlayMenu) {
            overlayBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                overlayMenu.style.display = overlayMenu.style.display === 'block' ? 'none' : 'block';
            });
            
            // Close overlay menu when clicking outside
            document.addEventListener('click', (e) => {
                if (!overlayBtn.contains(e.target) && !overlayMenu.contains(e.target)) {
                    overlayMenu.style.display = 'none';
                }
            });
            
            // Overlay menu items
            overlayMenu.querySelectorAll('.overlay-menu-item').forEach(item => {
                item.addEventListener('click', (e) => {
                    const overlayType = e.currentTarget.getAttribute('data-overlay');
                    if (window.SystemInterfacesMap) {
                        window.SystemInterfacesMap.setOverlay(overlayType);
                        overlayBtn.querySelector('span').textContent = e.currentTarget.textContent.trim();
                        overlayMenu.style.display = 'none';
                    }
                });
            });
            
            // Clear overlays button
            const clearOverlaysBtn = byId('dataMapClearOverlaysBtn');
            if (clearOverlaysBtn) {
                clearOverlaysBtn.addEventListener('click', () => {
                    if (window.SystemInterfacesMap) {
                        window.SystemInterfacesMap.setOverlay('none');
                        overlayBtn.querySelector('span').textContent = 'None';
                        overlayMenu.style.display = 'none';
                    }
                });
            }
        }
        
        // Overlay grid button: overlay fields as columns dropdown
        const overlayGridBtn = byId('dataMapOverlayGrid');
        const overlayColumnsMenu = byId('dataMapOverlayColumnsMenu');
        const overlayColumnsOptions = byId('dataMapOverlayColumnsOptions');
        if (overlayGridBtn && overlayColumnsMenu && overlayColumnsOptions) {
            function populateOverlayColumnsMenu() {
                overlayColumnsOptions.innerHTML = '';
                const ot = window.SystemInterfacesMap && window.SystemInterfacesMap.getState ? (window.SystemInterfacesMap.getState().overlay || '') : '';
                if (!ot || ot === 'none') {
                    overlayColumnsOptions.innerHTML = '<div class="map-overlay-columns-empty">Select an overlay first.</div>';
                    return;
                }
                const columns = window.OverlayColumns ? window.OverlayColumns.getOverlayColumns(ot) : [];
                const selectedIds = window.SystemInterfacesMap && typeof window.SystemInterfacesMap.getOverlayColumns === 'function' ? window.SystemInterfacesMap.getOverlayColumns(ot) : [];
                if (!columns || columns.length === 0) {
                    overlayColumnsOptions.innerHTML = '<div class="map-overlay-columns-empty">No columns for this overlay.</div>';
                    return;
                }
                columns.forEach(col => {
                    const div = document.createElement('div');
                    div.className = 'map-filter-option';
                    const input = document.createElement('input');
                    input.type = 'checkbox';
                    input.id = 'overlayCol_dataMap_' + ot + '_' + col.id;
                    input.checked = selectedIds.indexOf(col.id) !== -1;
                    input.dataset.columnId = col.id;
                    const label = document.createElement('label');
                    label.htmlFor = input.id;
                    label.textContent = col.label;
                    div.appendChild(input);
                    div.appendChild(label);
                    input.addEventListener('change', () => {
                        const checked = Array.from(overlayColumnsOptions.querySelectorAll('input:checked')).map(i => i.dataset.columnId);
                        if (window.SystemInterfacesMap && typeof window.SystemInterfacesMap.setOverlayColumns === 'function') {
                            window.SystemInterfacesMap.setOverlayColumns(ot, checked);
                        }
                    });
                    overlayColumnsOptions.appendChild(div);
                });
            }
            overlayGridBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                if (overlayMenu) overlayMenu.style.display = 'none';
                const isOpen = overlayColumnsMenu.classList.toggle('open');
                if (isOpen) {
                    overlayColumnsMenu.style.display = 'block';
                    populateOverlayColumnsMenu();
                    const rect = overlayGridBtn.getBoundingClientRect();
                    overlayColumnsMenu.style.position = 'fixed';
                    overlayColumnsMenu.style.left = rect.left + 'px';
                    overlayColumnsMenu.style.top = (rect.bottom + 4) + 'px';
                    overlayColumnsMenu.style.minWidth = '200px';
                } else {
                    overlayColumnsMenu.style.display = 'none';
                }
                overlayGridBtn.classList.toggle('active', isOpen);
            });
            document.addEventListener('click', (e) => {
                if (overlayColumnsMenu && overlayGridBtn && !overlayColumnsMenu.contains(e.target) && !overlayGridBtn.contains(e.target)) {
                    overlayColumnsMenu.classList.remove('open');
                    overlayColumnsMenu.style.display = 'none';
                    overlayGridBtn.classList.remove('active');
                }
            });
        }

        // Filter button and menu
        const filterBtn = byId('dataMapFilterBtn');
        const filterMenu = byId('dataMapFilterMenu');
        if (filterBtn && filterMenu) {
            filterBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                filterMenu.style.display = filterMenu.style.display === 'block' ? 'none' : 'block';
            });
            
            // Close filter menu when clicking outside
            document.addEventListener('click', (e) => {
                if (!filterBtn.contains(e.target) && !filterMenu.contains(e.target)) {
                    filterMenu.style.display = 'none';
                }
            });
            
            // Filter checkboxes
            filterMenu.querySelectorAll('input[type="checkbox"]').forEach(checkbox => {
                checkbox.addEventListener('change', () => {
                    updateDataMapFilterCount(mapRoot);
                    // Apply filters to map
                    const filters = getDataMapFilters(mapRoot);
                    if (window.SystemInterfacesMap) {
                        window.SystemInterfacesMap.setDatasetNodeFilters(filters);
                    }
                });
            });
        }
        
        // Toolbar buttons
        const zoomInBtn = byId('dataMapZoomIn');
        if (zoomInBtn) {
            zoomInBtn.addEventListener('click', () => {
                if (window.SystemInterfacesMap) window.SystemInterfacesMap.zoomIn();
            });
        }
        
        const zoomOutBtn = byId('dataMapZoomOut');
        if (zoomOutBtn) {
            zoomOutBtn.addEventListener('click', () => {
                if (window.SystemInterfacesMap) window.SystemInterfacesMap.zoomOut();
            });
        }
        
        const redrawBtn = byId('dataMapRedraw');
        if (redrawBtn) {
            redrawBtn.addEventListener('click', () => {
                if (window.SystemInterfacesMap) window.SystemInterfacesMap.redrawMap();
            });
        }
        
        const resetBtn = byId('dataMapReset');
        if (resetBtn) {
            resetBtn.addEventListener('click', () => {
                if (window.SystemInterfacesMap) window.SystemInterfacesMap.resetMap();
            });
        }
        
        const exportBtn = byId('dataMapExport');
        if (exportBtn) {
            exportBtn.addEventListener('click', () => {
                if (window.SystemInterfacesMap) window.SystemInterfacesMap.exportAsPng();
            });
        }
        
        const fullscreenBtn = byId('dataMapFullscreen');
        if (fullscreenBtn) {
            fullscreenBtn.addEventListener('click', () => {
                if (window.SystemInterfacesMap) window.SystemInterfacesMap.openFullscreen();
            });
        }
        
        const legendBtn = byId('dataMapLegend');
        if (legendBtn && typeof window.setupMapLegendDropdown === 'function') {
            window.setupMapLegendDropdown('dataMapLegend', 'dataMapLegendDropdown', function() {
                return window.SystemInterfacesMap && window.SystemInterfacesMap.getLegendHtml ? window.SystemInterfacesMap.getLegendHtml() : '';
            });
        } else if (legendBtn) {
            const sidePanel = byId('dataMapSidePanel');
            legendBtn.addEventListener('click', () => {
                if (sidePanel) {
                    const isVisible = sidePanel.style.display !== 'none';
                    sidePanel.style.display = isVisible ? 'none' : 'block';
                    legendBtn.classList.toggle('active', !isVisible);
                }
            });
        }
        
        // Listen for dynamic filter options from dataset lineage map
        window.addEventListener('datasetMapFilterOptionsUpdated', (event) => {
            updateDataMapFilterOptions(event.detail, mapRoot);
        });
    }
    
    // Get current filter state for data map
    function getDataMapFilters(mapRoot) {
        const scope = mapRoot && mapRoot.querySelector ? mapRoot : document;
        const typeOptions = scope.querySelectorAll('#dataMapFilterTypeOptions input[type="checkbox"]');
        const lifecycleOptions = scope.querySelectorAll('#dataMapFilterLifecycleOptions input[type="checkbox"]');
        
        const types = Array.from(typeOptions)
            .filter(cb => cb.checked)
            .map(cb => cb.value);
        
        const lifecycles = Array.from(lifecycleOptions)
            .filter(cb => cb.checked)
            .map(cb => cb.value);
        
        return {
            types: types,
            lifecycles: lifecycles
        };
    }
    
    // Update filter options dynamically
    function updateDataMapFilterOptions(options, mapRoot) {
        const scope = mapRoot && mapRoot.querySelector ? mapRoot : document;
        const typeContainer = scope.querySelector('#dataMapFilterTypeOptions');
        const lifecycleContainer = scope.querySelector('#dataMapFilterLifecycleOptions');
        
        if (typeContainer && options.types) {
            typeContainer.innerHTML = options.types.map(type => `
                <div class="map-filter-option">
                    <input type="checkbox" id="dataMapFilterType_${escapeHtml(type)}" value="${escapeHtml(type)}" checked>
                    <label for="dataMapFilterType_${escapeHtml(type)}">${escapeHtml(type)}</label>
                </div>
            `).join('');
            
            // Re-attach event listeners
            typeContainer.querySelectorAll('input[type="checkbox"]').forEach(checkbox => {
                checkbox.addEventListener('change', () => {
                    updateDataMapFilterCount(scope);
                    const filters = getDataMapFilters(scope);
                    if (window.SystemInterfacesMap) {
                        window.SystemInterfacesMap.setDatasetNodeFilters(filters);
                    }
                });
            });
        }
        
        if (lifecycleContainer && options.lifecycles) {
            lifecycleContainer.innerHTML = options.lifecycles.map(lifecycle => `
                <div class="map-filter-option">
                    <input type="checkbox" id="dataMapFilterLifecycle_${escapeHtml(lifecycle)}" value="${escapeHtml(lifecycle)}" checked>
                    <label for="dataMapFilterLifecycle_${escapeHtml(lifecycle)}">${escapeHtml(lifecycle)}</label>
                </div>
            `).join('');
            
            // Re-attach event listeners
            lifecycleContainer.querySelectorAll('input[type="checkbox"]').forEach(checkbox => {
                checkbox.addEventListener('change', () => {
                    updateDataMapFilterCount(scope);
                    const filters = getDataMapFilters(scope);
                    if (window.SystemInterfacesMap) {
                        window.SystemInterfacesMap.setDatasetNodeFilters(filters);
                    }
                });
            });
        }
        
        updateDataMapFilterCount(scope);
    }
    
    // Update filter count display
    function updateDataMapFilterCount(mapRoot) {
        const scope = mapRoot && mapRoot.querySelector ? mapRoot : document;
        const filterBtnText = scope.querySelector('#dataMapFilterBtnText');
        if (!filterBtnText) return;
        
        const allCheckboxes = scope.querySelectorAll('#dataMapFilterMenu input[type="checkbox"]');
        const checkedCount = Array.from(allCheckboxes).filter(cb => cb.checked).length;
        const totalCount = allCheckboxes.length;
        
        if (checkedCount === totalCount) {
            filterBtnText.textContent = `All selected (${totalCount})`;
        } else {
            filterBtnText.textContent = `${checkedCount} of ${totalCount} selected`;
        }
    }

    // Export to global scope
    window.SystemDataView = {
        init: init
    };

})();

