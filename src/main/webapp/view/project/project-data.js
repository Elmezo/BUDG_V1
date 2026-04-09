// Project Data View JavaScript - Read-only display for Datasets and Attributes
(function() {
    let currentProjectId = null;
    let datasetsData = [];
    let attributesData = [];

    // Helper function to escape HTML
    function escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    // Helper function to create entity link
    function createEntityLink(entityType, id, name, datasetId = null) {
        if (!id || !name || name === 'N/A') {
            return escapeHtml(name || 'N/A');
        }
        
        let url;
        switch(entityType) {
            case 'system':
                url = `/view/system/${encodeURIComponent(id)}`;
                break;
            case 'dataset':
                url = `/view/dataset/${encodeURIComponent(id)}`;
                break;
            case 'attribute':
                // Attributes don't have their own view page, link to dataset with attribute highlighted
                if (datasetId) {
                    url = `/view/dataset/${encodeURIComponent(datasetId)}?tab=attribute&attributeId=${encodeURIComponent(id)}`;
                } else {
                    // Fallback: just show as text if no dataset ID
                    return escapeHtml(name);
                }
                break;
            case 'glossary':
                url = `/view/glossary/${encodeURIComponent(id)}`;
                break;
            default:
                return escapeHtml(name);
        }
        
        return `<a href="${url}" class="relationship-link" title="View ${escapeHtml(name)}">${escapeHtml(name)}</a>`;
    }

    // Initialize Data view
    function loadProjectData(projectId) {
        currentProjectId = projectId;
        console.log('Loading Project Data for project:', projectId);
        const container = document.getElementById('projectDataContainer');
        if (container) {
            container.innerHTML = '<div class="view-section" style="grid-column:1/-1;"><div class="section-title">PROJECT DATA</div><p style="color:var(--text-muted,#6b7280);">Loading...</p></div>';
        }
        loadDataViewData();
    }

    // Load all Data view data
    async function loadDataViewData() {
        try {
            await Promise.all([
                loadDatasetsData(),
                loadAttributesData()
            ]);

            // Render the view
            renderDataView();

        } catch (error) {
            console.error('Error loading Data view data:', error);
            const container = document.getElementById('projectDataContainer');
            if (container) {
                container.innerHTML = '<div class="error">Error loading data: ' + (error.message || 'Unknown error') + '</div>';
            }
        }
    }

    // Load datasets data
    async function loadDatasetsData() {
        try {
            console.log('Loading datasets for project:', currentProjectId);
            
            const response = await fetch(`/api/project-data/${currentProjectId}/datasets`, {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });
            
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            
            const responseData = await response.json();
            console.log('Datasets API Response:', responseData);
            
            // Handle different API response formats
            if (Array.isArray(responseData)) {
                datasetsData = responseData;
            } else if (responseData.data && Array.isArray(responseData.data)) {
                datasetsData = responseData.data;
            } else {
                datasetsData = [];
            }
            
            console.log('Extracted datasets data:', datasetsData);
        } catch (error) {
            console.error('Error loading datasets:', error);
            datasetsData = [];
        }
    }

    // Load attributes data
    async function loadAttributesData() {
        try {
            console.log('Loading attributes for project:', currentProjectId);
            
            const response = await fetch(`/api/project-data/${currentProjectId}/attributes`, {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });
            
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            
            const responseData = await response.json();
            console.log('Attributes API Response:', responseData);
            
            // Handle different API response formats
            if (Array.isArray(responseData)) {
                attributesData = responseData;
            } else if (responseData.data && Array.isArray(responseData.data)) {
                attributesData = responseData.data;
            } else {
                attributesData = [];
            }
            
            console.log('Extracted attributes data:', attributesData);
        } catch (error) {
            console.error('Error loading attributes:', error);
            attributesData = [];
        }
    }

    // Render the Data view (with Data and Data Map sub-tabs)
    function renderDataView() {
        const container = document.getElementById('projectDataContainer');
        if (!container) return;

        let html = `
            <div class="view-section" style="grid-column:1/-1;">
                <div class="relationships-sub-tabs" style="display:flex;border-bottom:1px solid var(--border-color,#e5e7eb);margin-bottom:1rem;">
                    <button type="button" class="sub-tab active" data-data-sub-tab="data">Data</button>
                    <button type="button" class="sub-tab" data-data-sub-tab="dataMap">Data Map</button>
                </div>
                <div id="projectDataContent" class="sub-tab-content" style="display:block;">
                    ${renderDataSections()}
                </div>
                <div id="projectDataMapContent" class="sub-tab-content" style="display:none;">
                    <div id="projectDataMapContainer" style="height: 560px; min-height: 560px;"></div>
                </div>
            </div>
        `;

        container.innerHTML = html;
        setupDataSubTabs();
    }

    function setupDataSubTabs() {
        const container = document.getElementById('projectDataContainer');
        if (!container) return;
        const subTabs = container.querySelectorAll('.relationships-sub-tabs .sub-tab');
        const dataContent = document.getElementById('projectDataContent');
        const mapContent = document.getElementById('projectDataMapContent');
        subTabs.forEach(function(tab) {
            tab.addEventListener('click', function() {
                subTabs.forEach(function(t) { t.classList.remove('active'); });
                this.classList.add('active');
                const which = this.getAttribute('data-data-sub-tab');
                if (which === 'data') {
                    if (dataContent) dataContent.style.display = 'block';
                    if (mapContent) mapContent.style.display = 'none';
                } else if (which === 'dataMap') {
                    if (dataContent) dataContent.style.display = 'none';
                    if (mapContent) mapContent.style.display = 'block';
                    if (currentProjectId && window.ProjectDataMap && typeof window.ProjectDataMap.init === 'function') {
                        window.ProjectDataMap.init(currentProjectId);
                    }
                }
            });
        });
    }

    // Render data sections (Datasets and Attributes)
    function renderDataSections() {
        return `
            ${renderDatasetsSection()}
            ${renderAttributesSection()}
        `;
    }

    // Render datasets section
    function renderDatasetsSection() {
        return `
            <div class="relationships-section">
                <div class="relationships-header">
                    <div class="relationships-title">DATA SETS</div>
                    <div class="relationships-actions">
                        <button type="button" class="btn btn-secondary btn-sm">
                            <i class="fas fa-cog"></i>
                            <i class="fas fa-chevron-down"></i>
                        </button>
                    </div>
                </div>
                <div class="relationships-table-wrapper">
                    <table class="relationships-table">
                        <thead>
                            <tr>
                                <th>Glossary</th>
                                <th>System</th>
                                <th>Data Set Ref.</th>
                                <th>Data Set</th>
                                <th>Data Set Definition</th>
                            </tr>
                        </thead>
                        <tbody id="datasetsTableBody">
                            ${renderDatasetsTableBody()}
                        </tbody>
                    </table>
                </div>
                <div class="relationships-count">
                    ${datasetsData.length} record${datasetsData.length !== 1 ? 's' : ''}
                </div>
            </div>
        `;
    }

    // Render datasets table body
    function renderDatasetsTableBody() {
        if (datasetsData.length === 0) {
            return '<tr><td colspan="5" style="text-align: center; padding: 2rem; color: var(--text-muted, #6b7280);">No datasets found</td></tr>';
        }

        return datasetsData.map(dataset => {
            const glossaryLink = dataset.glossaryId && dataset.glossaryName 
                ? createEntityLink('glossary', dataset.glossaryId, dataset.glossaryName || 'Glossary')
                : '<span class="empty">-</span>';
            const datasetLink = dataset.datasetId && dataset.datasetName 
                ? `<div class="relationship-item"><i class="fas fa-database"></i>${createEntityLink('dataset', dataset.datasetId, dataset.datasetName)}</div>`
                : '<span class="empty">-</span>';
            const systemLink = dataset.systemId && dataset.systemName 
                ? createEntityLink('system', dataset.systemId, dataset.systemName)
                : '<span class="empty">-</span>';
            
            return `
                <tr>
                    <td>${glossaryLink}</td>
                    <td>${systemLink}</td>
                    <td>${escapeHtml(dataset.datasetRefNumber || '-')}</td>
                    <td>${datasetLink}</td>
                    <td>${escapeHtml(dataset.datasetDefinition || '-')}</td>
                </tr>
            `;
        }).join('');
    }

    // Render attributes section
    function renderAttributesSection() {
        return `
            <div class="relationships-section">
                <div class="relationships-header">
                    <div class="relationships-title">DATA ATTRIBUTES</div>
                    <div class="relationships-actions">
                        <button type="button" class="btn btn-secondary btn-sm">
                            <i class="fas fa-cog"></i>
                            <i class="fas fa-chevron-down"></i>
                        </button>
                    </div>
                </div>
                <div class="relationships-table-wrapper">
                    <table class="relationships-table">
                        <thead>
                            <tr>
                                <th>Attribute Glossary</th>
                                <th>System</th>
                                <th>Data Set</th>
                                <th>Attribute Name</th>
                                <th>Ref.</th>
                                <th>Definition</th>
                            </tr>
                        </thead>
                        <tbody id="attributesTableBody">
                            ${renderAttributesTableBody()}
                        </tbody>
                    </table>
                </div>
                <div class="relationships-count">
                    ${attributesData.length} record${attributesData.length !== 1 ? 's' : ''}
                </div>
            </div>
        `;
    }

    // Render attributes table body
    function renderAttributesTableBody() {
        if (attributesData.length === 0) {
            return '<tr><td colspan="6" style="text-align: center; padding: 2rem; color: var(--text-muted, #6b7280);">No attributes found</td></tr>';
        }

        return attributesData.map(attribute => {
            const glossaryLink = attribute.glossaryId && attribute.glossaryName 
                ? createEntityLink('glossary', attribute.glossaryId, attribute.glossaryName || 'Glossary')
                : '<span class="empty">-</span>';
            const systemLink = attribute.systemId && attribute.systemName 
                ? createEntityLink('system', attribute.systemId, attribute.systemName)
                : '<span class="empty">-</span>';
            const datasetLink = attribute.datasetId && attribute.datasetName 
                ? createEntityLink('dataset', attribute.datasetId, attribute.datasetName)
                : '<span class="empty">-</span>';
            const attributeLink = attribute.attributeId && attribute.attributeName 
                ? `<div class="relationship-item"><i class="fas fa-table"></i>${createEntityLink('attribute', attribute.attributeId, attribute.attributeName, attribute.datasetId)}</div>`
                : '<span class="empty">-</span>';
            
            return `
                <tr>
                    <td>${glossaryLink}</td>
                    <td>${systemLink}</td>
                    <td>${datasetLink}</td>
                    <td>${attributeLink}</td>
                    <td>${escapeHtml(attribute.attributeRefNumber || '-')}</td>
                    <td>${escapeHtml(attribute.attributeDefinition || 'Not Defined')}</td>
                </tr>
            `;
        }).join('');
    }

    // Export function to global scope
    window.loadProjectData = loadProjectData;

})();

