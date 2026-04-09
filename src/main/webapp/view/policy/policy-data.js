// Policy Data View JavaScript - Read-only display for Datasets and Attributes
(function() {
    let currentPolicyId = null;
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
    function loadPolicyData(policyId) {
        currentPolicyId = policyId;
        console.log('Loading Policy Data for policy:', policyId);
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
            const container = document.getElementById('policyDataContainer');
            if (container) {
                container.innerHTML = '<div class="error">Error loading data: ' + (error.message || 'Unknown error') + '</div>';
            }
        }
    }

    // Load datasets data
    async function loadDatasetsData() {
        try {
            console.log('Loading datasets for policy:', currentPolicyId);
            
            const response = await fetch(`/api/policy-data/${currentPolicyId}/datasets`, {
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
            console.log('Loading attributes for policy:', currentPolicyId);
            
            const response = await fetch(`/api/policy-data/${currentPolicyId}/attributes`, {
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

    // Render the Data view
    function renderDataView() {
        const container = document.getElementById('policyDataContainer');
        if (!container) return;

        let html = `
            <div class="view-section" style="grid-column:1/-1;">
                ${renderDataSections()}
            </div>
        `;

        container.innerHTML = html;
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
                                <th>Data Set Ref.</th>
                                <th>Data Set</th>
                                <th>Data Set Definition</th>
                                <th>System</th>
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
                    <td>${escapeHtml(dataset.datasetRefNumber || '-')}</td>
                    <td>${datasetLink}</td>
                    <td>${escapeHtml(dataset.datasetDefinition || '-')}</td>
                    <td>${systemLink}</td>
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
                                <th><div class="th-content"><span>System</span><i class="fas fa-sort"></i></div></th>
                                <th><div class="th-content"><span>Data Set</span><i class="fas fa-sort"></i></div></th>
                                <th><div class="th-content"><span>Glossary</span><i class="fas fa-sort"></i></div></th>
                                <th><div class="th-content"><span>Attribute Name</span><i class="fas fa-sort"></i></div></th>
                                <th><div class="th-content"><span>Editability</span><i class="fas fa-sort"></i></div></th>
                                <th><div class="th-content"><span>Editability Role</span><i class="fas fa-sort"></i></div></th>
                                <th><div class="th-content"><span>Key</span><i class="fas fa-sort"></i></div></th>
                                <th><div class="th-content"><span>Rank</span><i class="fas fa-sort"></i></div></th>
                                <th><div class="th-content"><span>Ref.</span><i class="fas fa-sort"></i></div></th>
                                <th><div class="th-content"><span>DB Field Name</span><i class="fas fa-sort"></i></div></th>
                                <th><div class="th-content"><span>Definition</span><i class="fas fa-sort"></i></div></th>
                                <th><div class="th-content"><span>Origin</span><i class="fas fa-sort"></i></div></th>
                                <th><div class="th-content"><span>KDE</span><i class="fas fa-sort"></i></div></th>
                                <th><div class="th-content"><span>Attribute Glossary Definition</span><i class="fas fa-sort"></i></div></th>
                                <th><div class="th-content"><span>Requirement</span><i class="fas fa-sort"></i></div></th>
                                <th><div class="th-content"><span>Business Logic</span><i class="fas fa-sort"></i></div></th>
                                <th><div class="th-content"><span>Data Type</span><i class="fas fa-sort"></i></div></th>
                                <th><div class="th-content"><span>Data Length</span><i class="fas fa-sort"></i></div></th>
                                <th><div class="th-content"><span>Confidence Score</span><i class="fas fa-sort"></i></div></th>
                                <th><div class="th-content"><span>Child Glossary</span><i class="fas fa-sort"></i></div></th>
                                <th><div class="th-content"><span>Child Glossary Type</span><i class="fas fa-sort"></i></div></th>
                                <th><div class="th-content"><span>Related To</span><i class="fas fa-sort"></i></div></th>
                                <th><div class="th-content"><span>Relationship Type</span><i class="fas fa-sort"></i></div></th>
                                <th><div class="th-content"><span>Interface</span><i class="fas fa-sort"></i></div></th>
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
            return '<tr><td colspan="24" style="text-align: center; padding: 2rem; color: var(--text-muted, #6b7280);">No attributes found</td></tr>';
        }

        return attributesData.map(attribute => {
            const systemLink = attribute.systemId && attribute.systemName 
                ? createEntityLink('system', attribute.systemId, attribute.systemName)
                : '<span class="empty">-</span>';
            const datasetLink = attribute.datasetId && attribute.datasetName 
                ? createEntityLink('dataset', attribute.datasetId, attribute.datasetName)
                : '<span class="empty">-</span>';
            const glossaryLink = attribute.glossaryId && attribute.glossaryName 
                ? createEntityLink('glossary', attribute.glossaryId, attribute.glossaryName || 'Glossary')
                : '<span class="empty">-</span>';
            const attributeLink = attribute.attributeId && attribute.attributeName 
                ? `<div class="relationship-item"><i class="fas fa-table" style="color: var(--secondary-color, #248567); margin-right: 0.5rem;"></i>${attribute.datasetId ? `<a href="/view/dataset/${attribute.datasetId}?tab=attribute&attributeId=${attribute.attributeId}" class="relationship-link" title="View attribute in dataset">${escapeHtml(attribute.attributeName)}</a>` : escapeHtml(attribute.attributeName)}</div>`
                : '<span class="empty">-</span>';
            
            return `
                <tr>
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
                    <td>${attributeLink}</td>
                    <td>${escapeHtml(attribute.editability || '-')}</td>
                    <td>${escapeHtml(attribute.editabilityRole || '-')}</td>
                    <td style="text-align: center;">${attribute.isPrimary || attribute.isPrimaryKey ? 'Yes' : 'No'}</td>
                    <td>${escapeHtml(attribute.attributeRank || attribute.rank || '-')}</td>
                    <td>${escapeHtml(attribute.attributeRefNumber || attribute.attributeRef || attribute.refNumber || '-')}</td>
                    <td>${escapeHtml(attribute.dbFieldName || '-')}</td>
                    <td>${escapeHtml(attribute.attributeDefinition || attribute.definition || '-')}</td>
                    <td>${escapeHtml(attribute.origin || '-')}</td>
                    <td>${escapeHtml(attribute.kde || '-')}</td>
                    <td>${escapeHtml(attribute.attributeGlossaryDefinition || '-')}</td>
                    <td>${escapeHtml(attribute.requirement || '-')}</td>
                    <td>${escapeHtml(attribute.businessLogic || attribute.business_Logic || '-')}</td>
                    <td>${escapeHtml(attribute.dataType || '-')}</td>
                    <td>${escapeHtml(attribute.dataLength || '-')}</td>
                    <td>${escapeHtml(attribute.confidenceScore !== null && attribute.confidenceScore !== undefined ? (attribute.confidenceScore + '%') : '-')}</td>
                    <td>${escapeHtml(attribute.childGlossaryName || '-')}</td>
                    <td>${escapeHtml(attribute.childGlossaryType || '-')}</td>
                    <td>${escapeHtml(attribute.relatedTo || '-')}</td>
                    <td>${escapeHtml(attribute.relationshipType || '-')}</td>
                    <td>${escapeHtml(attribute.interfaceName || '-')}</td>
                </tr>
            `;
        }).join('');
    }

    // Export function to global scope
    window.loadPolicyData = loadPolicyData;

})();

