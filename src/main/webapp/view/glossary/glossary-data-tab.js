// Glossary Data Tab JavaScript - Isolated module for Datasets and Data Attributes

(function() {
    let currentGlossaryId = null;

    // Helper function to escape HTML
    function escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    function dataRecordLabel(n) {
        const I = window.I18n && window.I18n.t.bind(window.I18n);
        if (n === 0 && I) return I('glossary.data.zeroRecords') || '0 records';
        if (n === 1 && I) return I('glossary.data.recordOne') || '1 record';
        if (I) return (I('glossary.data.recordCount') || '{count} records').replace('{count}', n);
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
    function init(glossaryId) {
        currentGlossaryId = glossaryId;
        console.log('Initializing Glossary Data Tab for glossary ID:', glossaryId);
        
        const container = document.getElementById('glossaryViewContainer');
        if (!container) {
            console.error('glossaryViewContainer not found');
            return;
        }

        const dT = (k) => (window.I18n && window.I18n.t(k)) || k;
        // Render data tab HTML
        container.innerHTML = `
            <div class="view-section" style="grid-column:1/-1;">
                <div class="data-container">
                    <div class="data-sub-tabs">
                        <button class="sub-tab disabled" data-sub-tab="data-discovery" disabled>${dT('glossary.data.dataDiscovery')}</button>
                        <button class="sub-tab active" data-sub-tab="data-sets">${dT('glossary.data.dataSets')}</button>
                        <button class="sub-tab" data-sub-tab="data-attributes">${dT('glossary.data.dataAttributes')}</button>
                        <button class="sub-tab" data-sub-tab="data-map">${dT('glossary.data.dataMap')}</button>
                    </div>
                    <div class="data-content">
                        <div id="dataDiscoveryContent" class="sub-tab-content">
                            <div class="data-section">
                                <div class="data-header">
                                    <div class="data-title">${dT('glossary.data.dataDiscovery')}</div>
                                    <div class="data-actions">
                                        <i class="fas fa-cog" style="color: var(--text-secondary, #6c757d); cursor: pointer;"></i>
                                        <i class="fas fa-chevron-down" style="color: var(--text-secondary, #6c757d); cursor: pointer;"></i>
                                    </div>
                                </div>
                                <div style="padding: 2rem; text-align: center; color: var(--text-secondary, #6c757d);">
                                    <i class="fas fa-search" style="font-size: 3rem; margin-bottom: 1rem; opacity: 0.5;"></i>
                                    <p>Data discovery content will be displayed here</p>
                                </div>
                            </div>
                        </div>
                        <div id="dataSetsContent" class="sub-tab-content active">
                            <div class="relationships-section">
                                <div class="relationships-header">
                                    <div class="relationships-title">${dT('glossary.data.dataSets')}</div>
                                    <div class="relationships-actions">
                                        <i class="fas fa-cog" style="color: var(--text-secondary, #6c757d); cursor: pointer;"></i>
                                        <i class="fas fa-chevron-down" style="color: var(--text-secondary, #6c757d); cursor: pointer;"></i>
                                    </div>
                                </div>
                                <div class="relationships-table-wrapper">
                                    <table class="relationships-table">
                                        <thead>
                                            <tr>
                                                <th><div class="th-content"><span>${dT('glossary.data.system')}</span></div></th>
                                                <th><div class="th-content"><span>${dT('glossary.data.dataSetRef')}</span></div></th>
                                                <th><div class="th-content"><span>${dT('glossary.data.dataSet')}</span></div></th>
                                                <th><div class="th-content"><span>${dT('glossary.data.dataSetDefinition')}</span></div></th>
                                                <th><div class="th-content"><span>${dT('glossary.data.childGlossary')}</span></div></th>
                                                <th><div class="th-content"><span>${dT('glossary.data.childGlossaryType')}</span></div></th>
                                            </tr>
                                        </thead>
                                        <tbody id="dataSetsTableBody">
                                            <tr><td colspan="6" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">${dT('glossary.data.loading')}</td></tr>
                                        </tbody>
                                    </table>
                                </div>
                                <div class="relationships-count" id="dataSetsFooter">
                                    ${dT('glossary.data.zeroRecords')}
                                </div>
                            </div>
                        </div>
                        <div id="dataAttributesContent" class="sub-tab-content">
                            <div class="relationships-section">
                                <div class="relationships-header">
                                    <div class="relationships-title">${dT('glossary.data.dataAttributes')}</div>
                                    <div class="relationships-actions">
                                        <i class="fas fa-cog" style="color: var(--text-secondary, #6c757d); cursor: pointer;"></i>
                                        <i class="fas fa-chevron-down" style="color: var(--text-secondary, #6c757d); cursor: pointer;"></i>
                                    </div>
                                </div>
                                <div class="relationships-table-wrapper">
                                    <table class="relationships-table">
                                        <thead>
                                            <tr>
                                                <th><div class="th-content"><span>${dT('glossary.data.system')}</span></div></th>
                                                <th><div class="th-content"><span>Data Set</span></div></th>
                                                <th><div class="th-content"><span>Glossary</span></div></th>
                                                <th><div class="th-content"><span>Attribute Glossary</span></div></th>
                                                <th><div class="th-content"><span>Attribute Name</span></div></th>
                                                <th><div class="th-content"><span>Editability</span></div></th>
                                                <th><div class="th-content"><span>Editability Role</span></div></th>
                                                <th><div class="th-content"><span>Key</span></div></th>
                                                <th><div class="th-content"><span>Rank</span></div></th>
                                                <th><div class="th-content"><span>Ref.</span></div></th>
                                                <th><div class="th-content"><span>DB Field Name</span></div></th>
                                                <th><div class="th-content"><span>Definition</span></div></th>
                                                <th><div class="th-content"><span>Origin</span></div></th>
                                                <th><div class="th-content"><span>KDE</span></div></th>
                                                <th><div class="th-content"><span>Attribute Glossary Definition</span></div></th>
                                                <th><div class="th-content"><span>Requirement</span></div></th>
                                                <th><div class="th-content"><span>Business Logic</span></div></th>
                                                <th><div class="th-content"><span>Data Type</span></div></th>
                                                <th><div class="th-content"><span>Data Length</span></div></th>
                                                <th><div class="th-content"><span>Confidence Score(%)</span></div></th>
                                                <th><div class="th-content"><span>Physical Fields</span></div></th>
                                                <th><div class="th-content"><span>Review Status</span></div></th>
                                                <th><div class="th-content"><span>Child Glossary</span></div></th>
                                                <th><div class="th-content"><span>Child Glossary Type</span></div></th>
                                                <th><div class="th-content"><span>Related To</span></div></th>
                                                <th><div class="th-content"><span>Relationship Type</span></div></th>
                                                <th><div class="th-content"><span>Interface</span></div></th>
                                                <th><div class="th-content"><span>Sourcing Logic</span></div></th>
                                            </tr>
                                        </thead>
                                        <tbody id="dataAttributesTableBody">
                                            <tr><td colspan="28" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">${dT('glossary.data.loading')}</td></tr>
                                        </tbody>
                                    </table>
                                </div>
                                <div class="relationships-count" id="dataAttributesFooter">
                                    ${dT('glossary.data.zeroRecords')}
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
                                        <select id="glossaryDataMapTypeSelect" class="map-select">
                                            <option value="system-lineage" selected>System Lineage</option>
                                            <option value="dataset-lineage">Data Set Lineage</option>
                                            <option value="multi-node-lineage">Multi Node Lineage</option>
                                        </select>
                                    </div>
                                    
                                    <!-- Layout -->
                                    <div class="map-control-group">
                                        <label>Layout:</label>
                                        <div class="map-layout-controls">
                                            <select id="glossaryDataMapLayoutSelect" class="map-select" title="Change lineage flow direction">
                                                <option value="top-to-bottom" selected>Top to Bottom</option>
                                                <option value="left-to-right">Left to Right</option>
                                                <option value="organic">Organic</option>
                                            </select>
                                            <div class="map-btn-dropdown-wrapper">
                                                <button type="button" class="map-toolbar-btn-sm" id="glossaryDataMapSpacing" title="Node spacing">
                                                    <i class="fas fa-expand-arrows-alt" id="glossaryDataMapSpacingIcon"></i>
                                                    <i class="fas fa-chevron-down" style="font-size: 8px; margin-left: 2px;"></i>
                                                </button>
                                                <div class="map-btn-dropdown-menu" id="glossaryDataMapSpacingMenu">
                                                    <div class="map-btn-dropdown-item" data-spacing="compact">Compact</div>
                                                    <div class="map-btn-dropdown-item active" data-spacing="normal">Normal</div>
                                                    <div class="map-btn-dropdown-item" data-spacing="spacey">Spacey</div>
                                                </div>
                                            </div>
                                            <div class="map-btn-dropdown-wrapper">
                                                <button type="button" class="map-toolbar-btn-sm" id="glossaryDataMapEdgeStyle" title="Edge routing style">
                                                    <i class="fas fa-arrow-right" id="glossaryDataMapEdgeStyleIcon"></i>
                                                    <i class="fas fa-chevron-down" style="font-size: 8px; margin-left: 2px;"></i>
                                                </button>
                                                <div class="map-btn-dropdown-menu" id="glossaryDataMapEdgeStyleMenu">
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
                                    <div class="map-control-group map-hops-group" id="glossaryDataMapHopsGroup">
                                        <label>Hops:</label>
                                        <input type="number" id="glossaryDataMapHopsCount" class="map-hops-input" min="1" max="99" value="15" title="Upstream/downstream lineage depth (1-99, recommend 15)">
                                    </div>
                                    
                                    <!-- Overlay -->
                                    <div class="map-control-group">
                                        <label>Overlay:</label>
                                        <div class="map-overlay-controls">
                                            <div class="map-overlay-dropdown">
                                                <button type="button" class="map-select-btn" id="glossaryDataMapOverlayBtn">
                                                    <span id="glossaryDataMapOverlayBtnText">None</span>
                                                    <i class="fas fa-chevron-down"></i>
                                                </button>
                                                <!-- System Lineage Overlay Menu (same as System facet) -->
                                                <div class="map-overlay-menu" id="glossaryDataMapOverlayMenuSystem" data-map-type="system-lineage">
                                                    <div class="overlay-menu-grid">
                                                        <div class="overlay-menu-column">
                                                            <div class="overlay-menu-header">Data</div>
                                                            <div class="overlay-menu-item" data-overlay="description">
                                                                <i class="fas fa-info-circle"></i> Description
                                                            </div>
                                                            <div class="overlay-menu-item" data-overlay="glossary">
                                                                <i class="fas fa-book"></i> Glossary
                                                            </div>
                                                            <div class="overlay-menu-item" data-overlay="datasets">
                                                                <i class="fas fa-layer-group"></i> Data Sets
                                                            </div>
                                                            <div class="overlay-menu-item" data-overlay="attributes">
                                                                <i class="fas fa-th"></i> Attributes
                                                            </div>
                                                            <div class="overlay-menu-item" data-overlay="linking-attributes">
                                                                <i class="fas fa-link"></i> Linking Attributes
                                                            </div>
                                                            <div class="overlay-menu-item" data-overlay="data-quality">
                                                                <i class="fas fa-bullseye"></i> Data Quality
                                                            </div>
                                                            <div class="overlay-menu-item" data-overlay="data-privacy">
                                                                <i class="fas fa-lock"></i> Data Privacy
                                                            </div>
                                                        </div>
                                                        <div class="overlay-menu-column">
                                                            <div class="overlay-menu-header">Business</div>
                                                            <div class="overlay-menu-item" data-overlay="stakeholders">
                                                                <i class="fas fa-users"></i> Stakeholders
                                                            </div>
                                                            <div class="overlay-menu-item" data-overlay="processes">
                                                                <i class="fas fa-play"></i> Processes
                                                            </div>
                                                            <div class="overlay-menu-item" data-overlay="projects">
                                                                <i class="fas fa-project-diagram"></i> Projects
                                                            </div>
                                                            <div class="overlay-menu-item" data-overlay="policies">
                                                                <i class="fas fa-file-alt"></i> Policies
                                                            </div>
                                                        </div>
                                                        <div class="overlay-menu-column">
                                                            <div class="overlay-menu-header">Organizational</div>
                                                            <div class="overlay-menu-item" data-overlay="business-area">
                                                                <i class="fas fa-briefcase"></i> Business Area
                                                            </div>
                                                            <div class="overlay-menu-item" data-overlay="products">
                                                                <i class="fas fa-tag"></i> Products
                                                            </div>
                                                            <div class="overlay-menu-item" data-overlay="legal-entities">
                                                                <i class="fas fa-landmark"></i> Legal Entities
                                                            </div>
                                                        </div>
                                                        <div class="overlay-menu-column">
                                                            <div class="overlay-menu-header">Regulatory</div>
                                                            <div class="overlay-menu-item" data-overlay="geography">
                                                                <i class="fas fa-globe"></i> Geography
                                                            </div>
                                                        </div>
                                                    </div>
                                                    <div class="overlay-menu-footer">
                                                        <button type="button" class="overlay-clear-btn" id="glossaryDataMapClearOverlaysBtnSystem">Clear Overlays</button>
                                                    </div>
                                                </div>
                                                <!-- Dataset Lineage Overlay Menu (same as Dataset facet) -->
                                                <div class="map-overlay-menu" id="glossaryDataMapOverlayMenuDataset" data-map-type="dataset-lineage" style="display:none;">
                                                    <div class="overlay-menu-grid">
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
                                                                <i class="fas fa-link"></i> Linking Attributes
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
                                                                <i class="fas fa-play"></i> Processes
                                                            </div>
                                                            <div class="overlay-menu-item" data-overlay="projects">
                                                                <i class="fas fa-project-diagram"></i> Projects
                                                            </div>
                                                            <div class="overlay-menu-item" data-overlay="policies">
                                                                <i class="fas fa-file-alt"></i> Policies
                                                            </div>
                                                        </div>
                                                    </div>
                                                    <div class="overlay-menu-footer">
                                                        <button type="button" class="overlay-clear-btn" id="glossaryDataMapClearOverlaysBtnDataset">Clear Overlays</button>
                                                    </div>
                                                </div>
                                                <!-- Multi Node Lineage Overlay Menu -->
                                                <div class="map-overlay-menu" id="glossaryDataMapOverlayMenuMultiNode" data-map-type="multi-node-lineage" style="display:none;">
                                                    <div class="overlay-menu-grid">
                                                        <div class="overlay-menu-column">
                                                            <div class="overlay-menu-header">Data</div>
                                                            <div class="overlay-menu-item" data-overlay="description">
                                                                <i class="fas fa-info-circle"></i> Description
                                                            </div>
                                                        </div>
                                                    </div>
                                                    <div class="overlay-menu-footer">
                                                        <button type="button" class="overlay-clear-btn" id="glossaryDataMapClearOverlaysBtnMultiNode">Clear Overlays</button>
                                                    </div>
                                                </div>
                                            </div>
                                            <button type="button" class="map-toolbar-btn-sm" id="glossaryDataMapOverlayGrid" title="Overlay fields as columns">
                                                <i class="fas fa-th"></i>
                                                <i class="fas fa-chevron-down" style="font-size: 8px; margin-left: 2px;"></i>
                                            </button>
                                            <div class="map-overlay-columns-menu map-filter-menu" id="glossaryDataMapOverlayColumnsMenu" style="display: none;">
                                                <div class="map-filter-category">
                                                    <div class="map-filter-category-header">OVERLAY FIELDS AS COLUMNS</div>
                                                    <div id="glossaryDataMapOverlayColumnsOptions" class="map-filter-options-container"></div>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                    
                                    <!-- Filters -->
                                    <div class="map-control-group">
                                        <label>Filters:</label>
                                        <div class="map-filter-dropdown">
                                            <button type="button" class="map-select-btn" id="glossaryDataMapFilterBtn">
                                                <span id="glossaryDataMapFilterBtnText">All selected (0)</span>
                                                <i class="fas fa-chevron-down"></i>
                                            </button>
                                            <!-- System Lineage Filter Menu (same as System/Dataset facet) -->
                                            <div class="map-filter-menu" id="glossaryDataMapFilterMenuSystem" data-map-type="system-lineage">
                                                <div class="map-filter-category">
                                                    <div class="map-filter-category-header">LINKS</div>
                                                    <div class="map-filter-option">
                                                        <input type="checkbox" id="filterGlossaryDataSystemInterfaces" checked>
                                                        <label for="filterGlossaryDataSystemInterfaces">System Interfaces</label>
                                                    </div>
                                                    <div class="map-filter-option">
                                                        <input type="checkbox" id="filterGlossaryDataAttributeLinks" checked>
                                                        <label for="filterGlossaryDataAttributeLinks">Data Attribute Links</label>
                                                    </div>
                                                </div>
                                                <div class="map-filter-separator"></div>
                                                <div class="map-filter-category">
                                                    <div class="map-filter-category-header">CLASSIFICATION</div>
                                                    <div id="glossaryDataMapFilterClassificationOptions">
                                                        <!-- Dynamic options will be added here -->
                                                    </div>
                                                </div>
                                                <div class="map-filter-separator"></div>
                                                <div class="map-filter-category">
                                                    <div class="map-filter-category-header">TYPE</div>
                                                    <div id="glossaryDataMapFilterTypeOptions">
                                                        <!-- Dynamic options will be added here -->
                                                    </div>
                                                </div>
                                                <div class="map-filter-separator"></div>
                                                <div class="map-filter-category">
                                                    <div class="map-filter-category-header">LIFECYCLE</div>
                                                    <div id="glossaryDataMapFilterLifecycleOptions">
                                                        <!-- Dynamic options will be added here -->
                                                    </div>
                                                </div>
                                            </div>
                                            <!-- Dataset Lineage Filter Menu (same as Dataset facet) -->
                                            <div class="map-filter-menu" id="glossaryDataMapFilterMenuDataset" data-map-type="dataset-lineage" style="display:none;">
                                                <div class="map-filter-category">
                                                    <div class="map-filter-category-header">TYPE</div>
                                                    <div id="glossaryDataMapFilterDatasetTypeOptions">
                                                        <!-- Dynamic options will be added here -->
                                                    </div>
                                                </div>
                                                <div class="map-filter-separator"></div>
                                                <div class="map-filter-category">
                                                    <div class="map-filter-category-header">LIFECYCLE</div>
                                                    <div id="glossaryDataMapFilterDatasetLifecycleOptions">
                                                        <!-- Dynamic options will be added here -->
                                                    </div>
                                                </div>
                                            </div>
                                            <!-- Multi Node Lineage Filter Menu -->
                                            <div class="map-filter-menu" id="glossaryDataMapFilterMenuMultiNode" data-map-type="multi-node-lineage" style="display:none;">
                                                <div class="map-filter-category">
                                                    <div class="map-filter-category-header">MULTI NODE</div>
                                                    <div class="map-filter-option">
                                                        <input type="checkbox" id="filterGlossaryMultiNodeLineage" checked>
                                                        <label for="filterGlossaryMultiNodeLineage">Multi Node Lineage</label>
                                                    </div>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                    
                                    <!-- Toolbar Buttons -->
                                    <div class="map-toolbar-buttons">
                                        <button type="button" class="map-toolbar-btn" id="glossaryDataMapToggleLabels" title="Toggle labels">
                                            <i class="fas fa-exchange-alt"></i>
                                        </button>
                                        <div class="map-toolbar-separator"></div>
                                        <button type="button" class="map-toolbar-btn" id="glossaryDataMapZoomIn" title="Zoom in">
                                            <i class="fas fa-search-plus"></i>
                                        </button>
                                        <button type="button" class="map-toolbar-btn" id="glossaryDataMapZoomOut" title="Zoom out">
                                            <i class="fas fa-search-minus"></i>
                                        </button>
                                        <div class="map-toolbar-separator"></div>
                                        <button type="button" class="map-toolbar-btn" id="glossaryDataMapRedraw" title="Redraw">
                                            <i class="fas fa-sync-alt"></i>
                                        </button>
                                        <button type="button" class="map-toolbar-btn" id="glossaryDataMapReset" title="Reset">
                                            <i class="fas fa-undo"></i>
                                        </button>
                                        <button type="button" class="map-toolbar-btn" id="glossaryDataMapExport" title="Export as PNG">
                                            <i class="fas fa-save"></i>
                                        </button>
                                        <div class="map-toolbar-separator"></div>
                                        <button type="button" class="map-toolbar-btn" id="glossaryDataMapNavigator" title="Map navigator">
                                            <i class="fas fa-eye"></i>
                                        </button>
                                        <button type="button" class="map-toolbar-btn" id="glossaryDataMapFullscreen" title="Fullscreen">
                                            <i class="fas fa-external-link-alt"></i>
                                        </button>
                                        <button type="button" class="map-toolbar-btn" id="glossaryDataMapLegend" title="Open the Legend" aria-label="Open the Legend – refer to the legend to identify the symbols used in the map (Insight Maps Palette)">
                                            <i class="fas fa-list-ul"></i>
                                        </button>
                                    </div>
                                </div>
                                
                                <!-- Map Body -->
                                <div class="interface-map-body" style="flex: 1; position: relative;">
                                    <div class="interface-map-canvas" id="glossaryDataMapCanvas" style="width: 100%; height: 100%; position: relative;">
                                        <div class="interface-map-loading" data-glossary-data-map-loading style="display:none; position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%); text-align: center; z-index: 10;">
                                            <i class="fas fa-spinner fa-spin"></i>
                                            <span>Building map...</span>
                                        </div>
                                    </div>
                                    <div class="interface-map-side-panel" id="glossaryDataMapSidePanel" style="display:none;">
                                        <div class="map-side-panel-section">
                                            <h4><i class="fas fa-info-circle"></i> Selection</h4>
                                            <div class="selection-placeholder" data-glossary-data-map-placeholder>
                                                Select a node to see its details.
                                            </div>
                                            <div class="selection-info" data-glossary-data-map-details style="display:none;"></div>
                                        </div>
                                        <div class="map-side-panel-section">
                                            <h4 class="map-legend-palette-title"><i class="fas fa-layer-group"></i> Insight Maps Palette</h4>
                                            <div data-glossary-data-map-legend></div>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        `;

        // Set up sub-tabs and load initial data
        setupDataSubTabs(glossaryId);
        
        // Load initial data based on active sub-tab
        setTimeout(() => {
            const activeSubTab = document.querySelector('.data-sub-tabs .sub-tab.active');
            if (activeSubTab) {
                const subTabType = activeSubTab.getAttribute('data-sub-tab');
                if (subTabType === 'data-attributes') {
                    loadDataAttributesData(glossaryId);
                } else {
                    loadDatasetsData(glossaryId);
                }
            } else {
                // Default to datasets
                loadDatasetsData(glossaryId);
            }
        }, 100);
    }

    // Set up data sub-tabs functionality
    function setupDataSubTabs(glossaryId) {
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
                        loadDatasetsData(glossaryId);
                    } else if (subTabType === 'data-attributes') {
                        console.log('Loading attributes for sub-tab:', subTabType);
                        loadDataAttributesData(glossaryId);
                    } else if (subTabType === 'data-map') {
                        console.log('Initializing glossary data map for sub-tab:', subTabType);
                        initGlossaryDataMap(glossaryId);
                    }
                } else {
                    console.error('Target content not found for sub-tab:', subTabType);
                }
            });
        });
    }

    // Load datasets data
    async function loadDatasetsData(glossaryId) {
        const tbody = document.getElementById('dataSetsTableBody');
        const footer = document.getElementById('dataSetsFooter');

        if (!tbody || !footer) {
            console.error('Required elements not found for datasets table');
            return;
        }

        const loadLabel = (window.I18n && window.I18n.t('glossary.data.loading')) || 'Loading...';
        try {
            tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">' + loadLabel + '</td></tr>';

            const isRollupViewEnabled = localStorage.getItem(`glossary_${glossaryId}_rollup_view`) === 'true';
            let response = await window.BUDG_API_SERVICE.getGlossaryDatasets(glossaryId, isRollupViewEnabled);
            console.log('Datasets API raw response:', response);
            
            // Handle response that might be wrapped in data property
            let datasets = response;
            if (response && response.data && Array.isArray(response.data)) {
                datasets = response.data;
            } else if (response && Array.isArray(response)) {
                datasets = response;
            }
            
            // Filter datasets based on rollup view settings
            datasets = await filterByRollupSettings(glossaryId, datasets, 'dataset');
            
            console.log('Datasets loaded:', datasets);

            if (!Array.isArray(datasets) || datasets.length === 0) {
                tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;padding:2rem;color:var(--text-muted,#9ca3af);">No datasets found for this glossary</td></tr>';
                footer.textContent = dataRecordLabel(0);
                return;
            }

            // Render datasets table
            tbody.innerHTML = datasets.map(function(dataset) {
                const isChildGlossary = dataset.isChildGlossary || false;
                const rowClass = isChildGlossary ? 'child-glossary-row' : '';
                
                const systemLink = dataset.systemId && dataset.systemName 
                    ? createEntityLink('system', dataset.systemId, dataset.systemName)
                    : '<span class="empty">-</span>';
                
                const childGlossaryLink = dataset.childGlossaryId && dataset.childGlossaryName 
                    ? createEntityLink('glossary', dataset.childGlossaryId, dataset.childGlossaryName)
                    : '<span class="empty">-</span>';
                
                return `
                    <tr class="${rowClass}">
                        <td>
                            <div class="relationship-item">
                                <i class="fas fa-database" style="color: var(--secondary-color, #248567); margin-right: 0.5rem;"></i>
                                ${systemLink}
                            </div>
                        </td>
                        <td>${escapeHtml(dataset.refNumber || '-')}</td>
                        <td>
                            <div class="relationship-item">
                                <i class="fas fa-layer-group" style="color: var(--secondary-color, #248567); margin-right: 0.5rem;"></i>
                                ${dataset.id ? createEntityLink('dataset', dataset.id, dataset.primaryName || 'Unknown Dataset') : escapeHtml(dataset.primaryName || '-')}
                            </div>
                        </td>
                        <td>${escapeHtml(dataset.definition || '-')}</td>
                        <td>${childGlossaryLink}</td>
                        <td>${escapeHtml(dataset.childGlossaryType || '-')}</td>
                    </tr>
                `;
            }).join('');

            footer.textContent = dataRecordLabel(datasets.length);

        } catch (error) {
            console.error('Failed to load datasets:', error);
            tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;padding:2rem;color:var(--danger,#b91c1c);">Failed to load datasets: ' + escapeHtml(error.message) + '</td></tr>';
            footer.textContent = dataRecordLabel(0);
        }
    }
    
    // Helper function to filter data by rollup settings
    async function filterByRollupSettings(glossaryId, data, dataType) {
        // Check if rollup view is enabled
        const isRollupViewEnabled = localStorage.getItem(`glossary_${glossaryId}_rollup_view`) === 'true';
        
        if (!isRollupViewEnabled) {
            // Hide rollup view: filter out child glossary relationships
            return data.filter(item => !item.isChildGlossary && !item.childGlossaryId);
        }
        
        // Get enabled glossary types from admin settings
        let enabledGlossaryTypes = [];
        try {
            const settingsResponse = await fetch('/api/system-settings/GlossaryRollup/enabled_types', {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });
            if (settingsResponse.ok) {
                const settings = await settingsResponse.json();
                if (settings.value) {
                    enabledGlossaryTypes = JSON.parse(settings.value);
                    // Normalize: ensure all types are trimmed and non-empty
                    enabledGlossaryTypes = enabledGlossaryTypes
                        .map(type => (type || '').trim())
                        .filter(type => type.length > 0);
                }
                // If empty array or null, default to all types
                if (!enabledGlossaryTypes || enabledGlossaryTypes.length === 0) {
                    enabledGlossaryTypes = ['Domain', 'Subdomain', 'Term', 'Metric'];
                }
            } else {
                enabledGlossaryTypes = ['Domain', 'Subdomain', 'Term', 'Metric'];
            }
        } catch (error) {
            console.warn('Failed to load rollup settings, using defaults:', error);
            enabledGlossaryTypes = ['Domain', 'Subdomain', 'Term', 'Metric'];
        }
        
        // Normalize enabled types for comparison (trim and lowercase)
        const normalizedEnabledTypes = enabledGlossaryTypes.map(type => 
            (type || '').trim().toLowerCase()
        ).filter(type => type.length > 0);
        
        // Filter child glossary relationships by enabled types
        return data.filter(item => {
            // Keep direct links (not from child glossaries)
            if (!item.isChildGlossary && !item.childGlossaryId) return true;
            // Filter child links by enabled types
            const childType = (item.childGlossaryType || '').trim();
            if (!childType) return false; // If no type, exclude it
            // Use exact match (case-insensitive)
            const normalizedChildType = childType.toLowerCase();
            return normalizedEnabledTypes.includes(normalizedChildType);
        });
    }

    // Load data attributes data
    async function loadDataAttributesData(glossaryId) {
        console.log('loadDataAttributesData called with glossaryId:', glossaryId);
        const tbody = document.getElementById('dataAttributesTableBody');
        const footer = document.getElementById('dataAttributesFooter');

        if (!tbody || !footer) {
            console.error('Required elements not found for attributes table', { tbody: !!tbody, footer: !!footer });
            return;
        }

        try {
            tbody.innerHTML = '<tr><td colspan="28" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">Loading...</td></tr>';

            const isRollupViewEnabled = localStorage.getItem(`glossary_${glossaryId}_rollup_view`) === 'true';
            console.log('Calling API: getGlossaryAttributes(' + glossaryId + ', rollup=' + isRollupViewEnabled + ')');
            let response = await window.BUDG_API_SERVICE.getGlossaryAttributes(glossaryId, isRollupViewEnabled);
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
            
            // Filter attributes based on rollup view settings
            attributes = await filterByRollupSettings(glossaryId, attributes, 'attribute');
            
            console.log('Attributes after processing:', attributes);
            console.log('Attributes is array?', Array.isArray(attributes));
            console.log('Attributes length:', attributes ? attributes.length : 'null/undefined');

            if (!Array.isArray(attributes) || attributes.length === 0) {
                tbody.innerHTML = '<tr><td colspan="28" style="text-align:center;padding:2rem;color:var(--text-muted,#9ca3af);">No attributes found for this glossary</td></tr>';
                footer.textContent = dataRecordLabel(0);
                return;
            }

            // Render attributes table
            tbody.innerHTML = attributes.map(function(attr) {
                const isChildGlossary = attr.isChildGlossary || false;
                const rowClass = isChildGlossary ? 'child-glossary-row' : '';
                
                const systemLink = attr.systemId && attr.systemName 
                    ? createEntityLink('system', attr.systemId, attr.systemName)
                    : '<span class="empty">-</span>';
                
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

                const confidenceScoreDisplay = (attr.confidenceScore !== null && attr.confidenceScore !== undefined) ? (attr.confidenceScore + '%') : '-';
                return `
                    <tr class="${rowClass}">
                        <td>
                            <div class="relationship-item">
                                <i class="fas fa-database" style="color: var(--secondary-color, #248567); margin-right: 0.5rem;"></i>
                                ${systemLink}
                            </div>
                        </td>
                        <td>
                            <div class="relationship-item">
                                <i class="fas fa-layer-group" style="color: var(--secondary-color, #248567); margin-right: 0.5rem;"></i>
                                ${datasetLink}
                            </div>
                        </td>
                        <td>${glossaryLink}</td>
                        <td>${attributeGlossaryLink}</td>
                        <td>${attributeNameLink}</td>
                        <td>${escapeHtml(attr.editability || '-')}</td>
                        <td>${escapeHtml(attr.editabilityRole || '-')}</td>
                        <td style="text-align: center;">${attr.isPrimary ? 'Yes' : 'No'}</td>
                        <td>${escapeHtml(attr.attributeRank || '-')}</td>
                        <td>${escapeHtml(attr.attributeRef || '-')}</td>
                        <td>${escapeHtml(attr.dbFieldName || '-')}</td>
                        <td>${escapeHtml(attr.attributeDefinition || '-')}</td>
                        <td>${escapeHtml(attr.origin || '-')}</td>
                        <td>${escapeHtml(attr.kde || '-')}</td>
                        <td>${escapeHtml(attr.attributeGlossaryDefinition || '-')}</td>
                        <td>${escapeHtml(attr.requirement || '-')}</td>
                        <td>${escapeHtml(attr.businessLogic || '-')}</td>
                        <td>${escapeHtml(attr.dataType || '-')}</td>
                        <td>${escapeHtml(attr.dataLength || '-')}</td>
                        <td>${escapeHtml(confidenceScoreDisplay)}</td>
                        <td>${escapeHtml(attr.physicalFields || '-')}</td>
                        <td>${escapeHtml(attr.reviewStatus || '-')}</td>
                        <td>${escapeHtml(attr.childGlossaryName || '-')}</td>
                        <td>${escapeHtml(attr.childGlossaryType || '-')}</td>
                        <td>${escapeHtml(attr.relatedTo || '-')}</td>
                        <td>${escapeHtml(attr.relationshipType || '-')}</td>
                        <td>${escapeHtml(attr.interfaceName || '-')}</td>
                        <td>${escapeHtml(attr.sourcingLogic || '-')}</td>
                    </tr>
                `;
            }).join('');

            footer.textContent = dataRecordLabel(attributes.length);

        } catch (error) {
            console.error('Failed to load attributes:', error);
            tbody.innerHTML = '<tr><td colspan="28" style="text-align:center;padding:2rem;color:var(--danger,#b91c1c);">Failed to load attributes: ' + escapeHtml(error.message) + '</td></tr>';
            footer.textContent = dataRecordLabel(0);
        }
    }

    // Initialize Glossary Data Map
    function initGlossaryDataMap(glossaryId) {
        console.log('Initializing Glossary Data Map for glossary:', glossaryId);
        
        // Check if GlossaryDataMap is available
        if (!window.GlossaryDataMap) {
            console.error('GlossaryDataMap not available');
            const canvas = document.getElementById('glossaryDataMapCanvas');
            if (canvas) {
                canvas.innerHTML = '<div style="padding: 2rem; text-align: center; color: var(--text-secondary, #6c757d);">Map visualization library not available</div>';
            }
            return;
        }
        
        // Initialize the map
        const canvas = document.getElementById('glossaryDataMapCanvas');
        if (!canvas) {
            console.error('Glossary data map canvas not found');
            return;
        }
        
        // Clear any existing content
        const loadingEl = canvas.querySelector('[data-glossary-data-map-loading]');
        if (loadingEl) {
            loadingEl.style.display = 'block';
        }
        
        // Initialize the map
        try {
            // Initialize the map with the glossary data map canvas
            const mapContainer = '#glossaryDataMapCanvas';
            window.GlossaryDataMap.init(glossaryId, mapContainer);
            
            // Set up event handlers for controls
            setupGlossaryDataMapControls(glossaryId);
            
            // Hide loading after a short delay to allow map to render
            setTimeout(() => {
                if (loadingEl) {
                    loadingEl.style.display = 'none';
                }
            }, 500);
            
        } catch (error) {
            console.error('Failed to initialize glossary data map:', error);
            if (loadingEl) {
                loadingEl.style.display = 'none';
            }
            canvas.innerHTML = '<div style="padding: 2rem; text-align: center; color: var(--danger, #b91c1c);">Failed to initialize data map: ' + escapeHtml(error.message) + '</div>';
        }
    }

    // Set up event handlers for Glossary Data Map controls
    function setupGlossaryDataMapControls(glossaryId) {
        const mapId = 'glossaryDataMap';
        const mapRoot = document.getElementById(`${mapId}Section`) || document;
        const byId = (id) => mapRoot.querySelector(`#${id}`) || document.getElementById(id);
        
        // Map type selector
        const mapTypeSelect = byId(`${mapId}TypeSelect`);
        if (mapTypeSelect) {
            mapTypeSelect.addEventListener('change', (e) => {
                const mapType = e.target.value;
                // Switch overlay and filter menus based on map type
                switchMapTypeMenus(mapType);
                if (window.GlossaryDataMap) {
                    window.GlossaryDataMap.setMapType(mapType);
                }
            });
        }
        
        // Function to switch overlay and filter menus based on map type
        function switchMapTypeMenus(mapType) {
            // Hide all overlay menus
            ['System', 'Dataset', 'MultiNode'].forEach(type => {
                const menu = byId(`${mapId}OverlayMenu${type}`);
                if (menu) menu.style.display = 'none';
            });
            
            // Show appropriate overlay menu
            let overlayMenuType = 'System';
            if (mapType === 'dataset-lineage') {
                overlayMenuType = 'Dataset';
            } else if (mapType === 'multi-node-lineage') {
                overlayMenuType = 'MultiNode';
            }
            const activeOverlayMenu = byId(`${mapId}OverlayMenu${overlayMenuType}`);
            if (activeOverlayMenu) {
                activeOverlayMenu.style.display = '';
            }
            
            // Hide all filter menus
            ['System', 'Dataset', 'MultiNode'].forEach(type => {
                const menu = byId(`${mapId}FilterMenu${type}`);
                if (menu) menu.classList.remove('open');
            });
            
            // Reset overlay button text
            if (overlayBtn) {
                overlayBtn.querySelector('span').textContent = 'None';
            }
            
            // Clear active overlays
            mapRoot.querySelectorAll('.overlay-menu-item').forEach(i => i.classList.remove('active'));
            
            // Clear overlay on map
            if (window.GlossaryDataMap) {
                window.GlossaryDataMap.setOverlay('none');
            }
        }
        
        // Layout selector
        const layoutSelect = byId(`${mapId}LayoutSelect`);
        if (layoutSelect) {
            layoutSelect.addEventListener('change', (e) => {
                if (window.GlossaryDataMap) {
                    window.GlossaryDataMap.setLayout(e.target.value);
                }
            });
        }
        // Hops Count (1-99, default 15)
        const hopsInput = byId(`${mapId}HopsCount`);
        if (hopsInput) {
            hopsInput.addEventListener('change', (e) => {
                let val = parseInt(e.target.value, 10);
                if (isNaN(val) || val < 1) val = 1;
                if (val > 99) val = 99;
                e.target.value = val;
                if (window.GlossaryDataMap && typeof window.GlossaryDataMap.setHopsCount === 'function') {
                    window.GlossaryDataMap.setHopsCount(val);
                }
            });
        }

        // Initialize shared dropdown menus for expand/collapse & direction buttons
        if (typeof window.SharedMapDropdowns === 'function') {
            window.SharedMapDropdowns({
                mapId: mapId,
                getNetwork: () => window.GlossaryDataMap ? window.GlossaryDataMap.cy : null,
                setLayout: (dir) => {
                    if (layoutSelect) layoutSelect.value = dir;
                    if (window.GlossaryDataMap) window.GlossaryDataMap.setLayout(dir);
                },
                getCanvas: () => byId(mapId + 'Canvas')
            });
        }

        // Overlay dropdown
        const overlayBtn = byId(`${mapId}OverlayBtn`);
        const overlayMenus = {
            'system-lineage': byId(`${mapId}OverlayMenuSystem`),
            'dataset-lineage': byId(`${mapId}OverlayMenuDataset`),
            'multi-node-lineage': byId(`${mapId}OverlayMenuMultiNode`)
        };
        
        if (overlayBtn) {
            overlayBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                const currentMapType = mapTypeSelect ? mapTypeSelect.value : 'system-lineage';
                let activeMenu;
                if (currentMapType === 'system-lineage') {
                    activeMenu = overlayMenus['system-lineage'];
                } else if (currentMapType === 'dataset-lineage') {
                    activeMenu = overlayMenus['dataset-lineage'];
                } else if (currentMapType === 'multi-node-lineage') {
                    activeMenu = overlayMenus['multi-node-lineage'];
                }
                
                if (activeMenu) {
                    activeMenu.classList.toggle('open');
                    // Close other menus
                    Object.values(overlayMenus).forEach(menu => {
                        if (menu && menu !== activeMenu) {
                            menu.classList.remove('open');
                        }
                    });
                }
                // Close filter menus
                Object.values(filterMenus).forEach(menu => {
                    if (menu) menu.classList.remove('open');
                });
            });
        }

        // Setup overlay menu item click handlers
        Object.values(overlayMenus).forEach(overlayMenu => {
            if (overlayMenu) {
                overlayMenu.querySelectorAll('.overlay-menu-item').forEach(item => {
                    item.addEventListener('click', (e) => {
                        e.preventDefault();
                        e.stopPropagation();
                        
                        const overlayType = e.currentTarget.getAttribute('data-overlay');
                        const wasActive = e.currentTarget.classList.contains('active');
                        
                        overlayMenu.querySelectorAll('.overlay-menu-item').forEach(i => i.classList.remove('active'));
                        
                        if (!wasActive) {
                            e.currentTarget.classList.add('active');
                            if (overlayBtn) {
                                overlayBtn.querySelector('span').textContent = e.currentTarget.textContent.trim();
                            }
                            if (window.GlossaryDataMap) {
                                window.GlossaryDataMap.setOverlay(overlayType);
                            }
                        } else {
                            if (overlayBtn) {
                                overlayBtn.querySelector('span').textContent = 'None';
                            }
                            if (window.GlossaryDataMap) {
                                window.GlossaryDataMap.setOverlay('none');
                            }
                        }
                        
                        overlayMenu.classList.remove('open');
                    });
                });
            }
        });

        // Clear overlays buttons
        ['System', 'Dataset', 'MultiNode'].forEach(type => {
            const clearBtn = byId(`${mapId}ClearOverlaysBtn${type}`);
            if (clearBtn) {
                clearBtn.addEventListener('click', () => {
                    mapRoot.querySelectorAll('.overlay-menu-item').forEach(i => i.classList.remove('active'));
                    if (overlayBtn) overlayBtn.querySelector('span').textContent = 'None';
                    if (window.GlossaryDataMap) {
                        window.GlossaryDataMap.setOverlay('none');
                    }
                    Object.values(overlayMenus).forEach(menu => {
                        if (menu) menu.classList.remove('open');
                    });
                });
            }
        });

        // Overlay grid button: overlay fields as columns dropdown
        const overlayGridBtn = byId(`${mapId}OverlayGrid`);
        const overlayColumnsMenuEl = byId(`${mapId}OverlayColumnsMenu`);
        const overlayColumnsOptions = byId(`${mapId}OverlayColumnsOptions`);
        if (overlayGridBtn && overlayColumnsMenuEl && overlayColumnsOptions) {
            function populateOverlayColumnsMenu() {
                overlayColumnsOptions.innerHTML = '';
                const overlayType = window.GlossaryDataMap && window.GlossaryDataMap.getState ? (window.GlossaryDataMap.getState().overlay || '') : '';
                if (!overlayType || overlayType === 'none') {
                    overlayColumnsOptions.innerHTML = '<div class="map-overlay-columns-empty">Select an overlay first.</div>';
                    return;
                }
                const columns = window.OverlayColumns && window.OverlayColumns.getOverlayColumns ? window.OverlayColumns.getOverlayColumns(overlayType) : [];
                const selectedIds = window.GlossaryDataMap && typeof window.GlossaryDataMap.getOverlayColumns === 'function' ? window.GlossaryDataMap.getOverlayColumns(overlayType) : [];
                if (!columns || columns.length === 0) {
                    overlayColumnsOptions.innerHTML = '<div class="map-overlay-columns-empty">No columns for this overlay.</div>';
                    return;
                }
                columns.forEach(col => {
                    const div = document.createElement('div');
                    div.className = 'map-filter-option';
                    const input = document.createElement('input');
                    input.type = 'checkbox';
                    input.id = 'overlayCol_glossaryData_' + overlayType + '_' + col.id;
                    input.checked = selectedIds.indexOf(col.id) !== -1;
                    input.dataset.columnId = col.id;
                    const label = document.createElement('label');
                    label.htmlFor = input.id;
                    label.textContent = col.label;
                    div.appendChild(input);
                    div.appendChild(label);
                    input.addEventListener('change', () => {
                        const checked = Array.from(overlayColumnsOptions.querySelectorAll('input:checked')).map(i => i.dataset.columnId);
                        if (window.GlossaryDataMap && typeof window.GlossaryDataMap.setOverlayColumns === 'function') {
                            window.GlossaryDataMap.setOverlayColumns(overlayType, checked);
                        }
                    });
                    overlayColumnsOptions.appendChild(div);
                });
            }
            overlayGridBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                Object.values(filterMenus).forEach(menu => { if (menu) menu.classList.remove('open'); });
                Object.values(overlayMenus).forEach(menu => { if (menu) menu.classList.remove('open'); });
                const isOpen = overlayColumnsMenuEl.classList.toggle('open');
                if (isOpen) {
                    overlayColumnsMenuEl.style.display = 'block';
                    populateOverlayColumnsMenu();
                    const rect = overlayGridBtn.getBoundingClientRect();
                    overlayColumnsMenuEl.style.position = 'fixed';
                    overlayColumnsMenuEl.style.left = rect.left + 'px';
                    overlayColumnsMenuEl.style.top = (rect.bottom + 4) + 'px';
                    overlayColumnsMenuEl.style.minWidth = '200px';
                } else {
                    overlayColumnsMenuEl.style.display = 'none';
                }
                overlayGridBtn.classList.toggle('active', isOpen);
            });
        }

        // Filter dropdown
        const filterBtn = byId(`${mapId}FilterBtn`);
        const filterMenus = {
            'system-lineage': byId(`${mapId}FilterMenuSystem`),
            'dataset-lineage': byId(`${mapId}FilterMenuDataset`),
            'multi-node-lineage': byId(`${mapId}FilterMenuMultiNode`)
        };
        
        if (filterBtn) {
            filterBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                const currentMapType = mapTypeSelect ? mapTypeSelect.value : 'system-lineage';
                let activeMenu;
                if (currentMapType === 'system-lineage') {
                    activeMenu = filterMenus['system-lineage'];
                } else if (currentMapType === 'dataset-lineage') {
                    activeMenu = filterMenus['dataset-lineage'];
                } else if (currentMapType === 'multi-node-lineage') {
                    activeMenu = filterMenus['multi-node-lineage'];
                }
                
                if (activeMenu) {
                    activeMenu.classList.toggle('open');
                    // Close other menus and overlay menus
                    Object.values(filterMenus).forEach(menu => {
                        if (menu && menu !== activeMenu) {
                            menu.classList.remove('open');
                        }
                    });
                    Object.values(overlayMenus).forEach(menu => {
                        if (menu) menu.classList.remove('open');
                    });
                }
            });
        }
        
        // Initialize menu visibility based on default map type
        if (mapTypeSelect) {
            switchMapTypeMenus(mapTypeSelect.value);
        }

        // Toolbar buttons
        const zoomInBtn = byId(`${mapId}ZoomIn`);
        if (zoomInBtn) {
            zoomInBtn.addEventListener('click', () => {
                if (window.GlossaryDataMap) window.GlossaryDataMap.zoomIn();
            });
        }

        const zoomOutBtn = byId(`${mapId}ZoomOut`);
        if (zoomOutBtn) {
            zoomOutBtn.addEventListener('click', () => {
                if (window.GlossaryDataMap) window.GlossaryDataMap.zoomOut();
            });
        }

        const redrawBtn = byId(`${mapId}Redraw`);
        if (redrawBtn) {
            redrawBtn.addEventListener('click', () => {
                if (window.GlossaryDataMap) window.GlossaryDataMap.redrawMap();
            });
        }

        const resetBtn = byId(`${mapId}Reset`);
        if (resetBtn) {
            resetBtn.addEventListener('click', () => {
                if (window.GlossaryDataMap) window.GlossaryDataMap.resetMap();
            });
        }

        const exportBtn = byId(`${mapId}Export`);
        if (exportBtn) {
            exportBtn.addEventListener('click', () => {
                if (window.GlossaryDataMap) window.GlossaryDataMap.exportAsPng();
            });
        }

        const fullscreenBtn = byId(`${mapId}Fullscreen`);
        if (fullscreenBtn) {
            fullscreenBtn.addEventListener('click', () => {
                if (window.GlossaryDataMap && window.GlossaryDataMap.openFullscreen) {
                    window.GlossaryDataMap.openFullscreen();
                }
            });
        }

        const legendBtn = byId(`${mapId}Legend`);
        if (legendBtn && typeof window.setupMapLegendDropdown === 'function') {
            window.setupMapLegendDropdown(mapId + 'Legend', mapId + 'LegendDropdown', function() {
                return window.GlossaryDataMap && window.GlossaryDataMap.getLegendHtml ? window.GlossaryDataMap.getLegendHtml() : '';
            });
        } else if (legendBtn) {
            const sidePanel = byId(`${mapId}SidePanel`);
            legendBtn.addEventListener('click', () => {
                if (sidePanel) {
                    const isVisible = sidePanel.style.display !== 'none';
                    sidePanel.style.display = isVisible ? 'none' : 'flex';
                    legendBtn.classList.toggle('active', !isVisible);
                }
            });
        }

        const navigatorBtn = byId(`${mapId}Navigator`);
        if (navigatorBtn) {
            navigatorBtn.addEventListener('click', () => {
                navigatorBtn.classList.toggle('active');
                if (window.GlossaryDataMap && window.GlossaryDataMap.toggleNavigator) {
                    window.GlossaryDataMap.toggleNavigator();
                }
            });
        }

        const toggleLabelsBtn = byId(`${mapId}ToggleLabels`);
        if (toggleLabelsBtn) {
            toggleLabelsBtn.addEventListener('click', () => {
                toggleLabelsBtn.classList.toggle('active');
                // Toggle labels functionality can be added if needed
            });
        }

        // Close dropdowns when clicking outside
        document.addEventListener('click', (e) => {
            if (filterBtn) {
                Object.values(filterMenus).forEach(menu => {
                    if (menu && !filterBtn.contains(e.target) && !menu.contains(e.target)) {
                        menu.classList.remove('open');
                    }
                });
            }
            if (overlayBtn) {
                Object.values(overlayMenus).forEach(menu => {
                    if (menu && !overlayBtn.contains(e.target) && !menu.contains(e.target)) {
                        menu.classList.remove('open');
                    }
                });
            }
        });
    }

    // Export to global scope
    window.GlossaryDataView = {
        init: init
    };

})();

