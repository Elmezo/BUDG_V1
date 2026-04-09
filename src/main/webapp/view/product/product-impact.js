// Product Impact View JavaScript - Read-only display for Legal Entity, Client, and Business Area sub-tabs
(function() {
    let currentProductId = null;
    let legalRelationships = [];
    let clientRelationships = [];
    let businessAreaRelationships = [];

    // Helper function to escape HTML
    function escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    // Helper function to create entity link - match policy impact styling
    function createEntityLink(name, entityType, entityId) {
        if (!name || !entityId || name === 'N/A') {
            return escapeHtml(name || 'N/A');
        }
        
        let url;
        switch(entityType) {
            case 'legal':
                url = `/view/LegalEntity/${encodeURIComponent(entityId)}`;
                break;
            case 'client':
                url = `/view/client/${encodeURIComponent(entityId)}`;
                break;
            case 'businessarea':
                url = `/view/business-area/business-area.html?id=${encodeURIComponent(entityId)}`;
                break;
            case 'system':
                url = `/view/system/${encodeURIComponent(entityId)}`;
                break;
            case 'project':
                url = `/view/project/${encodeURIComponent(entityId)}`;
                break;
            case 'dataset':
                url = `/view/dataset/${encodeURIComponent(entityId)}`;
                break;
            case 'regulation':
                url = `/view/regulation/${encodeURIComponent(entityId)}`;
                break;
            case 'capability':
                url = `/view/capability/${encodeURIComponent(entityId)}`;
                break;
            case 'glossary':
                url = `/view/glossary/${encodeURIComponent(entityId)}`;
                break;
            case 'process':
                url = `/view/process/${encodeURIComponent(entityId)}`;
                break;
            case 'policy':
                url = `/view/policy/${encodeURIComponent(entityId)}`;
                break;
            default:
                return escapeHtml(name);
        }
        
        const viewText = window.I18n?.t('productImpact.messages.view') || 'View';
        return `<a href="${url}" class="entity-link" style="color: var(--secondary-color, #248567); text-decoration: none;" title="${viewText} ${escapeHtml(name)}">${escapeHtml(name)}</a>`;
    }

    // Initialize Impact view
    function loadProductImpact(productId) {
        currentProductId = productId;
        console.log('Loading Product Impact for product:', productId);
        loadImpactViewData();
    }

    // Reverse relationship arrays
    let systemRelationshipsReverse = [];
    let projectRelationshipsReverse = [];
    let datasetRelationshipsReverse = [];
    let regulationRelationshipsReverse = [];
    let capabilityRelationshipsReverse = [];
    let glossaryRelationshipsReverse = [];
    let processRelationshipsReverse = [];
    let policyRelationshipsReverse = [];
    
    // Load all Impact view data
    async function loadImpactViewData() {
        try {
            // Load relationship data (forward and reverse)
            await Promise.all([
                loadLegalRelationshipsView(),
                loadClientRelationshipsView(),
                loadBusinessAreaRelationshipsView(),
                loadSystemRelationshipsReverse(),
                loadProjectRelationshipsReverse(),
                loadDatasetRelationshipsReverse(),
                loadRegulationRelationshipsReverse(),
                loadCapabilityRelationshipsReverse(),
                loadGlossaryRelationshipsReverse(),
                loadProcessRelationshipsReverse(),
                loadPolicyRelationshipsReverse()
            ]);

            // Render the view
            renderImpactView();

        } catch (error) {
            console.error('Error loading Impact view data:', error);
        }
    }
    
    // Load reverse relationships
    // These show which Systems, Projects, and Datasets impact this Product
    async function loadSystemRelationshipsReverse() {
        try {
            const response = await fetch(`/api/system-impact/products/${currentProductId}/systems`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });
            if (!response.ok) {
                systemRelationshipsReverse = [];
                return;
            }
            const data = await response.json();
            systemRelationshipsReverse = Array.isArray(data) ? data : [];
        } catch (error) {
            console.error('Error loading reverse system relationships:', error);
            systemRelationshipsReverse = [];
        }
    }
    
    async function loadProjectRelationshipsReverse() {
        try {
            const response = await fetch(`/api/project-impact/products/${currentProductId}/projects`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });
            if (!response.ok) {
                projectRelationshipsReverse = [];
                return;
            }
            const data = await response.json();
            projectRelationshipsReverse = Array.isArray(data) ? data : [];
        } catch (error) {
            console.error('Error loading reverse project relationships:', error);
            projectRelationshipsReverse = [];
        }
    }
    
    async function loadDatasetRelationshipsReverse() {
        try {
            const response = await fetch(`/api/dataset-impact/products/${currentProductId}/datasets`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });
            if (!response.ok) {
                datasetRelationshipsReverse = [];
                return;
            }
            const data = await response.json();
            datasetRelationshipsReverse = Array.isArray(data) ? data : [];
        } catch (error) {
            console.error('Error loading reverse dataset relationships:', error);
            datasetRelationshipsReverse = [];
        }
    }
    
    async function loadRegulationRelationshipsReverse() {
        try {
            const response = await fetch(`/api/regulation-impact/products/${currentProductId}/regulations`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });
            if (!response.ok) {
                regulationRelationshipsReverse = [];
                return;
            }
            const data = await response.json();
            regulationRelationshipsReverse = Array.isArray(data) ? data : [];
        } catch (error) {
            console.error('Error loading reverse regulation relationships:', error);
            regulationRelationshipsReverse = [];
        }
    }
    
    async function loadCapabilityRelationshipsReverse() {
        try {
            const response = await fetch(`/api/capability-impact/products/${currentProductId}/capabilities`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });
            if (!response.ok) {
                capabilityRelationshipsReverse = [];
                return;
            }
            const data = await response.json();
            capabilityRelationshipsReverse = Array.isArray(data) ? data : [];
        } catch (error) {
            console.error('Error loading reverse capability relationships:', error);
            capabilityRelationshipsReverse = [];
        }
    }
    
    async function loadGlossaryRelationshipsReverse() {
        try {
            const response = await fetch(`/api/glossary-impact/products/${currentProductId}/glossaries`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });
            if (!response.ok) {
                glossaryRelationshipsReverse = [];
                return;
            }
            const data = await response.json();
            glossaryRelationshipsReverse = Array.isArray(data) ? data : [];
        } catch (error) {
            console.error('Error loading reverse glossary relationships:', error);
            glossaryRelationshipsReverse = [];
        }
    }
    
    async function loadProcessRelationshipsReverse() {
        try {
            const response = await fetch(`/api/process-impact/products/${currentProductId}/processes`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });
            if (!response.ok) {
                processRelationshipsReverse = [];
                return;
            }
            const data = await response.json();
            processRelationshipsReverse = Array.isArray(data) ? data : [];
        } catch (error) {
            console.error('Error loading reverse process relationships:', error);
            processRelationshipsReverse = [];
        }
    }
    
    async function loadPolicyRelationshipsReverse() {
        try {
            const response = await fetch(`/api/policy-impact/products/${currentProductId}/policies`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });
            if (!response.ok) {
                policyRelationshipsReverse = [];
                return;
            }
            const data = await response.json();
            policyRelationshipsReverse = Array.isArray(data) ? data : [];
        } catch (error) {
            console.error('Error loading reverse policy relationships:', error);
            policyRelationshipsReverse = [];
        }
    }

    // Load legal relationships for view
    async function loadLegalRelationshipsView() {
        try {
            console.log('Loading legal relationships for view, product:', currentProductId);

            const response = await fetch(`/api/product-impact/${currentProductId}/legals`, {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const data = await response.json();
            console.log('Legal relationships view API response:', data);

            legalRelationships = Array.isArray(data) ? data : [];

        } catch (error) {
            console.error('Error loading legal relationships for view:', error);
            legalRelationships = [];
        }
    }

    // Load client relationships for view
    async function loadClientRelationshipsView() {
        try {
            console.log('Loading client relationships for view, product:', currentProductId);

            const response = await fetch(`/api/product-impact/${currentProductId}/clients`, {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const data = await response.json();
            console.log('Client relationships view API response:', data);

            clientRelationships = Array.isArray(data) ? data : [];

        } catch (error) {
            console.error('Error loading client relationships for view:', error);
            clientRelationships = [];
        }
    }

    // Load business area relationships for view
    async function loadBusinessAreaRelationshipsView() {
        try {
            console.log('Loading business area relationships for view, product:', currentProductId);

            const response = await fetch(`/api/product-impact/${currentProductId}/businessareas`, {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const data = await response.json();
            console.log('Business area relationships view API response:', data);

            businessAreaRelationships = Array.isArray(data) ? data : [];

        } catch (error) {
            console.error('Error loading business area relationships for view:', error);
            businessAreaRelationships = [];
        }
    }

    // Render Impact view with sub-tabs
    function renderImpactView() {
        const container = document.getElementById('productImpactContainer');
        if (!container) {
            console.error('productImpactContainer not found');
            return;
        }

        // Determine which sub-tabs to show based on data availability
        const hasLegalData = legalRelationships.length > 0;
        const hasClientData = clientRelationships.length > 0;
        const hasBusinessAreaData = businessAreaRelationships.length > 0;
        const hasSystemReverse = systemRelationshipsReverse.length > 0;
        const hasProjectReverse = projectRelationshipsReverse.length > 0;
        const hasDatasetReverse = datasetRelationshipsReverse.length > 0;
        const hasRegulationReverse = regulationRelationshipsReverse.length > 0;
        const hasCapabilityReverse = capabilityRelationshipsReverse.length > 0;
        const hasGlossaryReverse = glossaryRelationshipsReverse.length > 0;
        const hasProcessReverse = processRelationshipsReverse.length > 0;
        const hasPolicyReverse = policyRelationshipsReverse.length > 0;

        if (!hasLegalData && !hasClientData && !hasBusinessAreaData && !hasSystemReverse && !hasProjectReverse && !hasDatasetReverse && !hasRegulationReverse && !hasCapabilityReverse && !hasGlossaryReverse && !hasProcessReverse && !hasPolicyReverse) {
            container.innerHTML = `
                <div class="view-section" style="grid-column:1/-1;">
                    <div class="section-title">IMPACT</div>
                    <div class="empty">${window.I18n?.t('message.noImpactRelationships') || 'No impact relationships found'}</div>
                </div>
            `;
            return;
        }

        // Build sub-tabs HTML
        let subTabsHtml = '';
        let subTabsContentHtml = '';
        let firstActive = true;

        if (hasLegalData) {
            subTabsHtml += `<button class="sub-tab active" data-sub-tab="legal">${window.I18n?.t('productImpact.sections.legalEntities') || 'Legal Entity'}</button>`;
            firstActive = false;
            subTabsContentHtml += `
                <!-- Legal Entity Sub-tab -->
                <div id="impactLegalViewContent" class="sub-tab-content active">
                <div class="relationships-hierarchy">
                    <div class="hierarchy-header">
                        <div class="hierarchy-title">${(window.I18n?.t('productImpact.sections.legalEntities') || 'Legal Entity').toUpperCase()}</div>
                        <div class="hierarchy-actions">
                            <button type="button" class="btn btn-secondary">
                                <i class="fas fa-list"></i> List
                            </button>
                            <button type="button" class="btn btn-secondary">
                                <i class="fas fa-search-plus"></i> Search and Add
                            </button>
                            <button type="button" class="btn btn-secondary">
                                <i class="fas fa-cog"></i><i class="fas fa-chevron-down"></i>
                            </button>
                        </div>
                    </div>
                    <div class="data-table-wrapper">
                        <table class="data-table">
                            <thead>
                                <tr>
                                    <th><div class="th-content"><span>Relationship Type</span><i class="fas fa-sort"></i></div></th>
                                    <th><div class="th-content"><span>Legal Entity Name</span><i class="fas fa-sort"></i></div></th>
                                    <th><div class="th-content" style="justify-content: center;"><span>Legal Entity Owner</span><i class="fas fa-sort"></i></div></th>
                                </tr>
                            </thead>
                            <tbody>
                                ${renderLegalViewTableBody()}
                            </tbody>
                        </table>
                    </div>
                    <div class="hierarchy-footer">
                        ${legalRelationships.length} ${legalRelationships.length !== 1 ? (window.I18n?.t('dataset.messages.records') || 'records') : (window.I18n?.t('dataset.messages.record') || 'record')}
                    </div>
                </div>
            </div>
            `;
        }

        if (hasClientData) {
            const activeClass = firstActive ? 'active' : '';
            const clientTabText = window.I18n?.t('productImpact.sections.clients') || 'Client';
            if (firstActive) subTabsHtml += `<button class="sub-tab active" data-sub-tab="client">${clientTabText}</button>`;
            else subTabsHtml += `<button class="sub-tab" data-sub-tab="client">${clientTabText}</button>`;
            if (firstActive) firstActive = false;
            subTabsContentHtml += `
                <!-- Client Sub-tab -->
                <div id="impactClientViewContent" class="sub-tab-content ${activeClass}">
                <div class="relationships-hierarchy">
                    <div class="hierarchy-header">
                        <div class="hierarchy-title">${(window.I18n?.t('productImpact.sections.clients') || 'Client').toUpperCase()}</div>
                        <div class="hierarchy-actions">
                            <button type="button" class="btn btn-secondary">
                                <i class="fas fa-list"></i> List
                            </button>
                            <button type="button" class="btn btn-secondary">
                                <i class="fas fa-search-plus"></i> Search and Add
                            </button>
                            <button type="button" class="btn btn-secondary">
                                <i class="fas fa-cog"></i><i class="fas fa-chevron-down"></i>
                            </button>
                        </div>
                    </div>
                    <div class="data-table-wrapper">
                        <table class="data-table">
                            <thead>
                                <tr>
                                    <th><div class="th-content"><span>Relationship Type</span><i class="fas fa-sort"></i></div></th>
                                    <th><div class="th-content"><span>Client</span><i class="fas fa-sort"></i></div></th>
                                    <th><div class="th-content" style="justify-content: center;"><span>Client Owner</span><i class="fas fa-sort"></i></div></th>
                                </tr>
                            </thead>
                            <tbody>
                                ${renderClientViewTableBody()}
                            </tbody>
                        </table>
                    </div>
                    <div class="hierarchy-footer">
                        ${clientRelationships.length} record${clientRelationships.length !== 1 ? 's' : ''}
                    </div>
                </div>
            </div>
            `;
        }

        if (hasBusinessAreaData) {
            const activeClass = firstActive ? 'active' : '';
            const businessAreaTabText = window.I18n?.t('productImpact.sections.businessAreas') || 'Business Area';
            if (firstActive) subTabsHtml += `<button class="sub-tab active" data-sub-tab="businessarea">${businessAreaTabText}</button>`;
            else subTabsHtml += `<button class="sub-tab" data-sub-tab="businessarea">${businessAreaTabText}</button>`;
            if (firstActive) firstActive = false;
            subTabsContentHtml += `
                <!-- Business Area Sub-tab -->
                <div id="impactBusinessAreaViewContent" class="sub-tab-content ${activeClass}">
                <div class="relationships-hierarchy">
                    <div class="hierarchy-header">
                        <div class="hierarchy-title">BUSINESS AREA</div>
                        <div class="hierarchy-actions">
                            <button type="button" class="btn btn-secondary">
                                <i class="fas fa-list"></i> List
                            </button>
                            <button type="button" class="btn btn-secondary">
                                <i class="fas fa-search-plus"></i> Search and Add
                            </button>
                            <button type="button" class="btn btn-secondary">
                                <i class="fas fa-cog"></i><i class="fas fa-chevron-down"></i>
                            </button>
                        </div>
                    </div>
                    <div class="data-table-wrapper">
                        <table class="data-table">
                            <thead>
                                <tr>
                                    <th><div class="th-content"><span>Relationship Type</span><i class="fas fa-sort"></i></div></th>
                                    <th><div class="th-content"><span>Business Area</span><i class="fas fa-sort"></i></div></th>
                                </tr>
                            </thead>
                            <tbody>
                                ${renderBusinessAreaViewTableBody()}
                            </tbody>
                        </table>
                    </div>
                    <div class="hierarchy-footer">
                        ${businessAreaRelationships.length} ${businessAreaRelationships.length !== 1 ? (window.I18n?.t('dataset.messages.records') || 'records') : (window.I18n?.t('dataset.messages.record') || 'record')}
                    </div>
                </div>
            </div>
            `;
        }
        
        // Add reverse relationship sub-tabs
        if (hasSystemReverse) {
            const activeClass = firstActive ? 'active' : '';
            const systemTabText = window.I18n?.t('productImpact.sections.systems') || 'System';
            if (firstActive) subTabsHtml += `<button class="sub-tab active" data-sub-tab="system">${systemTabText}</button>`;
            else subTabsHtml += `<button class="sub-tab" data-sub-tab="system">${systemTabText}</button>`;
            subTabsContentHtml += `
                <div id="impactSystemReverseViewContent" class="sub-tab-content ${activeClass}" ${firstActive ? 'style="display:block;"' : ''}>
                    <div class="relationships-hierarchy">
                        <div class="hierarchy-header">
                            <div class="hierarchy-title">${(window.I18n?.t('productImpact.sections.systems') || 'System').toUpperCase()}</div>
                            <div class="hierarchy-actions">
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-list"></i> List
                                </button>
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-search-plus"></i> Search and Add
                                </button>
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-cog"></i><i class="fas fa-chevron-down"></i>
                                </button>
                            </div>
                        </div>
                        <div class="data-table-wrapper">
                            <table class="data-table">
                                <thead>
                                    <tr>
                                        <th><div class="th-content"><span>Relationship Type</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content"><span>System</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content" style="justify-content: center;"><span>System Owner</span><i class="fas fa-sort"></i></div></th>
                                    </tr>
                                </thead>
                                <tbody>${renderSystemReverseViewTableBody()}</tbody>
                            </table>
                        </div>
                        <div class="hierarchy-footer">${systemRelationshipsReverse.length} ${systemRelationshipsReverse.length !== 1 ? (window.I18n?.t('dataset.messages.records') || 'records') : (window.I18n?.t('dataset.messages.record') || 'record')}</div>
                    </div>
                </div>
            `;
            firstActive = false;
        }
        
        if (hasProjectReverse) {
            const activeClass = firstActive ? 'active' : '';
            const projectTabText = window.I18n?.t('productImpact.sections.projects') || 'Project';
            if (firstActive) subTabsHtml += `<button class="sub-tab active" data-sub-tab="project">${projectTabText}</button>`;
            else subTabsHtml += `<button class="sub-tab" data-sub-tab="project">${projectTabText}</button>`;
            subTabsContentHtml += `
                <div id="impactProjectReverseViewContent" class="sub-tab-content ${activeClass}" ${firstActive ? 'style="display:block;"' : ''}>
                    <div class="relationships-hierarchy">
                        <div class="hierarchy-header">
                            <div class="hierarchy-title">${(window.I18n?.t('productImpact.sections.projects') || 'Project').toUpperCase()}</div>
                            <div class="hierarchy-actions">
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-list"></i> List
                                </button>
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-search-plus"></i> Search and Add
                                </button>
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-cog"></i><i class="fas fa-chevron-down"></i>
                                </button>
                            </div>
                        </div>
                        <div class="data-table-wrapper">
                            <table class="data-table">
                                <thead>
                                    <tr>
                                        <th><div class="th-content"><span>Relationship Type</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content"><span>Project</span><i class="fas fa-sort"></i></div></th>
                                    </tr>
                                </thead>
                                <tbody>${renderProjectReverseViewTableBody()}</tbody>
                            </table>
                        </div>
                        <div class="hierarchy-footer">${projectRelationshipsReverse.length} ${projectRelationshipsReverse.length !== 1 ? (window.I18n?.t('dataset.messages.records') || 'records') : (window.I18n?.t('dataset.messages.record') || 'record')}</div>
                    </div>
                </div>
            `;
            firstActive = false;
        }
        
        if (hasDatasetReverse) {
            const activeClass = firstActive ? 'active' : '';
            if (firstActive) subTabsHtml += '<button class="sub-tab active" data-sub-tab="dataset">Dataset</button>';
            else subTabsHtml += '<button class="sub-tab" data-sub-tab="dataset">Dataset</button>';
            subTabsContentHtml += `
                <div id="impactDatasetReverseViewContent" class="sub-tab-content ${activeClass}" ${firstActive ? 'style="display:block;"' : ''}>
                    <div class="relationships-hierarchy">
                        <div class="hierarchy-header">
                            <div class="hierarchy-title">DATASET</div>
                            <div class="hierarchy-actions">
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-list"></i> List
                                </button>
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-search-plus"></i> Search and Add
                                </button>
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-cog"></i><i class="fas fa-chevron-down"></i>
                                </button>
                            </div>
                        </div>
                        <div class="data-table-wrapper">
                            <table class="data-table">
                                <thead>
                                    <tr>
                                        <th><div class="th-content"><span>Relationship Type</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content"><span>Dataset</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content" style="justify-content: center;"><span>Dataset Owner</span><i class="fas fa-sort"></i></div></th>
                                    </tr>
                                </thead>
                                <tbody>${renderDatasetReverseViewTableBody()}</tbody>
                            </table>
                        </div>
                        <div class="hierarchy-footer">${datasetRelationshipsReverse.length} record${datasetRelationshipsReverse.length !== 1 ? 's' : ''}</div>
                    </div>
                </div>
            `;
            firstActive = false;
        }
        
        if (hasRegulationReverse) {
            const activeClass = firstActive ? 'active' : '';
            const regulationTabText = window.I18n?.t('productImpact.sections.regulations') || 'Regulation';
            if (firstActive) subTabsHtml += `<button class="sub-tab active" data-sub-tab="regulation">${regulationTabText}</button>`;
            else subTabsHtml += `<button class="sub-tab" data-sub-tab="regulation">${regulationTabText}</button>`;
            subTabsContentHtml += `
                <div id="impactRegulationReverseViewContent" class="sub-tab-content ${activeClass}" ${firstActive ? 'style="display:block;"' : ''}>
                    <div class="relationships-hierarchy">
                        <div class="hierarchy-header">
                            <div class="hierarchy-title">${(window.I18n?.t('productImpact.sections.regulations') || 'Regulation').toUpperCase()}</div>
                            <div class="hierarchy-actions">
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-list"></i> List
                                </button>
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-search-plus"></i> Search and Add
                                </button>
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-cog"></i><i class="fas fa-chevron-down"></i>
                                </button>
                            </div>
                        </div>
                        <div class="data-table-wrapper">
                            <table class="data-table">
                                <thead>
                                    <tr>
                                        <th><div class="th-content"><span>Relationship Type</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content"><span>Regulation</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content" style="justify-content: center;"><span>Regulation Owner</span><i class="fas fa-sort"></i></div></th>
                                    </tr>
                                </thead>
                                <tbody>${renderRegulationReverseViewTableBody()}</tbody>
                            </table>
                        </div>
                        <div class="hierarchy-footer">${regulationRelationshipsReverse.length} ${regulationRelationshipsReverse.length !== 1 ? (window.I18n?.t('dataset.messages.records') || 'records') : (window.I18n?.t('dataset.messages.record') || 'record')}</div>
                    </div>
                </div>
            `;
            firstActive = false;
        }
        
        if (hasCapabilityReverse) {
            const activeClass = firstActive ? 'active' : '';
            const capabilityTabText = window.I18n?.t('productImpact.sections.capabilities') || 'Capability';
            if (firstActive) subTabsHtml += `<button class="sub-tab active" data-sub-tab="capability">${capabilityTabText}</button>`;
            else subTabsHtml += `<button class="sub-tab" data-sub-tab="capability">${capabilityTabText}</button>`;
            subTabsContentHtml += `
                <div id="impactCapabilityReverseViewContent" class="sub-tab-content ${activeClass}" ${firstActive ? 'style="display:block;"' : ''}>
                    <div class="relationships-hierarchy">
                        <div class="hierarchy-header">
                            <div class="hierarchy-title">CAPABILITY</div>
                            <div class="hierarchy-actions">
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-list"></i> List
                                </button>
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-search-plus"></i> Search and Add
                                </button>
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-cog"></i><i class="fas fa-chevron-down"></i>
                                </button>
                            </div>
                        </div>
                        <div class="data-table-wrapper">
                            <table class="data-table">
                                <thead>
                                    <tr>
                                        <th><div class="th-content"><span>Relationship Type</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content"><span>Capability</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content" style="justify-content: center;"><span>Capability Owner</span><i class="fas fa-sort"></i></div></th>
                                    </tr>
                                </thead>
                                <tbody>${renderCapabilityReverseViewTableBody()}</tbody>
                            </table>
                        </div>
                        <div class="hierarchy-footer">${capabilityRelationshipsReverse.length} record${capabilityRelationshipsReverse.length !== 1 ? 's' : ''}</div>
                    </div>
                </div>
            `;
            firstActive = false;
        }
        
        if (hasGlossaryReverse) {
            const activeClass = firstActive ? 'active' : '';
            if (firstActive) subTabsHtml += '<button class="sub-tab active" data-sub-tab="glossary">Glossary</button>';
            else subTabsHtml += '<button class="sub-tab" data-sub-tab="glossary">Glossary</button>';
            subTabsContentHtml += `
                <div id="impactGlossaryReverseViewContent" class="sub-tab-content ${activeClass}" ${firstActive ? 'style="display:block;"' : ''}>
                    <div class="relationships-hierarchy">
                        <div class="hierarchy-header">
                            <div class="hierarchy-title">${(window.I18n?.t('productImpact.sections.glossaries') || 'Glossary').toUpperCase()}</div>
                            <div class="hierarchy-actions">
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-list"></i> List
                                </button>
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-search-plus"></i> Search and Add
                                </button>
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-cog"></i><i class="fas fa-chevron-down"></i>
                                </button>
                            </div>
                        </div>
                        <div class="data-table-wrapper">
                            <table class="data-table">
                                <thead>
                                    <tr>
                                        <th><div class="th-content"><span>Relationship Type</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content"><span>Glossary</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content" style="justify-content: center;"><span>Glossary Owner</span><i class="fas fa-sort"></i></div></th>
                                    </tr>
                                </thead>
                                <tbody>${renderGlossaryReverseViewTableBody()}</tbody>
                            </table>
                        </div>
                        <div class="hierarchy-footer">${glossaryRelationshipsReverse.length} ${glossaryRelationshipsReverse.length !== 1 ? (window.I18n?.t('dataset.messages.records') || 'records') : (window.I18n?.t('dataset.messages.record') || 'record')}</div>
                    </div>
                </div>
            `;
            firstActive = false;
        }
        
        if (hasProcessReverse) {
            const activeClass = firstActive ? 'active' : '';
            const processTabText = window.I18n?.t('productImpact.sections.processes') || 'Process';
            if (firstActive) subTabsHtml += `<button class="sub-tab active" data-sub-tab="process">${processTabText}</button>`;
            else subTabsHtml += `<button class="sub-tab" data-sub-tab="process">${processTabText}</button>`;
            subTabsContentHtml += `
                <div id="impactProcessReverseViewContent" class="sub-tab-content ${activeClass}" ${firstActive ? 'style="display:block;"' : ''}>
                    <div class="relationships-hierarchy">
                        <div class="hierarchy-header">
                            <div class="hierarchy-title">PROCESS</div>
                            <div class="hierarchy-actions">
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-list"></i> List
                                </button>
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-search-plus"></i> Search and Add
                                </button>
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-cog"></i><i class="fas fa-chevron-down"></i>
                                </button>
                            </div>
                        </div>
                        <div class="data-table-wrapper">
                            <table class="data-table">
                                <thead>
                                    <tr>
                                        <th><div class="th-content"><span>Relationship Type</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content"><span>Process</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content" style="justify-content: center;"><span>Process Owner</span><i class="fas fa-sort"></i></div></th>
                                    </tr>
                                </thead>
                                <tbody>${renderProcessReverseViewTableBody()}</tbody>
                            </table>
                        </div>
                        <div class="hierarchy-footer">${processRelationshipsReverse.length} record${processRelationshipsReverse.length !== 1 ? 's' : ''}</div>
                    </div>
                </div>
            `;
            firstActive = false;
        }
        
        if (hasPolicyReverse) {
            const activeClass = firstActive ? 'active' : '';
            const policyTabText = window.I18n?.t('productImpact.sections.policies') || 'Policy';
            if (firstActive) subTabsHtml += `<button class="sub-tab active" data-sub-tab="policy">${policyTabText}</button>`;
            else subTabsHtml += `<button class="sub-tab" data-sub-tab="policy">${policyTabText}</button>`;
            subTabsContentHtml += `
                <div id="impactPolicyReverseViewContent" class="sub-tab-content ${activeClass}" ${firstActive ? 'style="display:block;"' : ''}>
                    <div class="relationships-hierarchy">
                        <div class="hierarchy-header">
                            <div class="hierarchy-title">${(window.I18n?.t('productImpact.sections.policies') || 'Policy').toUpperCase()}</div>
                            <div class="hierarchy-actions">
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-list"></i> List
                                </button>
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-search-plus"></i> Search and Add
                                </button>
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-cog"></i><i class="fas fa-chevron-down"></i>
                                </button>
                            </div>
                        </div>
                        <div class="data-table-wrapper">
                            <table class="data-table">
                                <thead>
                                    <tr>
                                        <th><div class="th-content"><span>Relationship Type</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content"><span>Policy</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content" style="justify-content: center;"><span>Policy Owner</span><i class="fas fa-sort"></i></div></th>
                                    </tr>
                                </thead>
                                <tbody>${renderPolicyReverseViewTableBody()}</tbody>
                            </table>
                        </div>
                        <div class="hierarchy-footer">${policyRelationshipsReverse.length} ${policyRelationshipsReverse.length !== 1 ? (window.I18n?.t('dataset.messages.records') || 'records') : (window.I18n?.t('dataset.messages.record') || 'record')}</div>
                    </div>
                </div>
            `;
            firstActive = false;
        }

        container.innerHTML = `
            <style>
                .relationships-sub-tabs {
                    display: flex;
                    border-bottom: 1px solid #e5e7eb;
                    margin-bottom: 1rem;
                }
                
                .sub-tab {
                    background: none;
                    border: none;
                    padding: 0.75rem 1.5rem;
                    cursor: pointer;
                    font-size: 0.875rem;
                    font-weight: 500;
                    color: #6b7280;
                    border-bottom: 2px solid transparent;
                    transition: all 0.2s ease;
                }
                
                .sub-tab:hover {
                    color: #374151;
                    background-color: #f9fafb;
                }
                
                .sub-tab.active {
                    color: #059669;
                    border-bottom-color: #059669;
                    background-color: #f0fdf4;
                }
                
                .sub-tab-content {
                    display: none !important;
                }
                
                .sub-tab-content.active {
                    display: block !important;
                }
            </style>
            <div class="view-section" style="grid-column:1/-1;">
                <div class="relationships-container">
                    <div class="relationships-sub-tabs">
                        ${subTabsHtml}
                    </div>
                    <div class="relationships-content">
                        ${subTabsContentHtml}
                    </div>
                </div>
            </div>
        `;

        // Setup sub-tab switching
        setupImpactViewSubTabs();
        
        // Ensure only the active sub-tab content is visible after rendering
        setTimeout(() => {
            const allSubTabContents = document.querySelectorAll('#productImpactContainer .sub-tab-content');
            allSubTabContents.forEach(content => {
                if (!content.classList.contains('active')) {
                    content.style.display = 'none';
                } else {
                    content.style.display = 'block';
                }
            });
        }, 0);
    }

    // Render legal view table body
    function renderLegalViewTableBody() {
        if (legalRelationships.length === 0) {
            const msg = window.I18n?.t('message.noLegalEntityRelationships') || 'No legal entity relationships found';
            return `<tr><td colspan="3" style="text-align: center; padding: 2rem; color: var(--text-muted, #6b7280);">${msg}</td></tr>`;
        }

        return legalRelationships.map(relationship => {
            const legalName = relationship.legalLongName || relationship.legalShortName || 'N/A';
            const legalLink = createEntityLink(legalName, 'legal', relationship.legalId);
            
            return `
                <tr>
                    <td>${escapeHtml(relationship.relationTypeName || 'N/A')}</td>
                    <td>
                        <div class="entity-info">
                            <span class="entity-name">${legalLink}</span>
                        </div>
                    </td>
                    <td style="text-align: center;">
                        <div class="owner-info">
                            <div class="owner-name" style="font-weight: bold; color: #333;">${escapeHtml(relationship.legalOwnerName || (window.I18n?.t('message.noOwner') || 'No owner'))}</div>
                        </div>
                    </td>
                </tr>
            `;
        }).join('');
    }

    // Render client view table body
    function renderClientViewTableBody() {
        if (clientRelationships.length === 0) {
            const msg = window.I18n?.t('message.noClientRelationships') || 'No client relationships found';
            return `<tr><td colspan="3" style="text-align: center; padding: 2rem; color: var(--text-muted, #6b7280);">${msg}</td></tr>`;
        }

        return clientRelationships.map(relationship => {
            const clientLink = createEntityLink(relationship.clientName, 'client', relationship.clientId);
            
            return `
                <tr>
                    <td>${escapeHtml(relationship.relationTypeName || 'N/A')}</td>
                    <td>
                        <div class="entity-info">
                            <span class="entity-name">${clientLink}</span>
                        </div>
                    </td>
                    <td style="text-align: center;">
                        <div class="owner-info">
                            <div class="owner-name" style="font-weight: bold; color: #333;">${escapeHtml(relationship.clientOwnerName || (window.I18n?.t('message.noOwner') || 'No owner'))}</div>
                        </div>
                    </td>
                </tr>
            `;
        }).join('');
    }

    // Render business area view table body
    function renderBusinessAreaViewTableBody() {
        if (businessAreaRelationships.length === 0) {
            const msg = window.I18n?.t('message.noBusinessAreaRelationships') || 'No business area relationships found';
            return `<tr><td colspan="2" style="text-align: center; padding: 2rem; color: var(--text-muted, #6b7280);">${msg}</td></tr>`;
        }

        return businessAreaRelationships.map(relationship => {
            const businessAreaLink = createEntityLink(relationship.businessAreaName, 'businessarea', relationship.businessAreaId);
            
            return `
                <tr>
                    <td>${escapeHtml(relationship.relationTypeName || 'N/A')}</td>
                    <td>
                        <div class="entity-info">
                            <span class="entity-name">${businessAreaLink}</span>
                        </div>
                    </td>
                </tr>
            `;
        }).join('');
    }

    // Render reverse relationship table bodies
    function renderSystemReverseViewTableBody() {
        if (!systemRelationshipsReverse || systemRelationshipsReverse.length === 0) {
            return '<tr class="reverse-relationship-row"><td colspan="3" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">No system relationships found</td></tr>';
        }
        return systemRelationshipsReverse.map(rel => {
            const relationTypeName = escapeHtml(rel.relationTypeReverseName || rel.relationTypeName || 'N/A');
            const systemName = rel.systemName || rel.systemLongName || 'N/A';
            const systemId = rel.systemId;
            const systemLink = createEntityLink(systemName, 'system', systemId);
            const ownerName = rel.systemOwnerName || (window.I18n?.t('message.noOwner') || 'No owner');
            return `<tr class="reverse-relationship-row"><td>${relationTypeName}</td><td><div class="entity-info reverse-relationship-entity"><i class="fas fa-server" style="color: var(--text-secondary, #64748b); margin-right: 0.5rem;"></i><span class="entity-name">${systemLink}</span></div></td><td style="text-align: center;"><div class="owner-info reverse-relationship-owner"><span class="owner-name">${escapeHtml(ownerName)}</span></div></td></tr>`;
        }).join('');
    }
    
    function renderProjectReverseViewTableBody() {
        if (!projectRelationshipsReverse || projectRelationshipsReverse.length === 0) {
            const msg = window.I18n?.t('message.noProjectRelationships') || 'No project relationships found';
            return `<tr class="reverse-relationship-row"><td colspan="2" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">${msg}</td></tr>`;
        }
        return projectRelationshipsReverse.map(rel => {
            const relationTypeName = escapeHtml(rel.relationTypeReverseName || rel.relationTypeName || 'N/A');
            const projectName = rel.projectName || 'N/A';
            const projectId = rel.projectId;
            const projectLink = createEntityLink(projectName, 'project', projectId);
            return `<tr class="reverse-relationship-row"><td>${relationTypeName}</td><td><div class="entity-info reverse-relationship-entity"><i class="fas fa-project-diagram" style="color: var(--text-secondary, #64748b); margin-right: 0.5rem;"></i><span class="entity-name">${projectLink}</span></div></td></tr>`;
        }).join('');
    }
    
    function renderDatasetReverseViewTableBody() {
        if (!datasetRelationshipsReverse || datasetRelationshipsReverse.length === 0) {
            const msg = window.I18n?.t('message.noDatasetRelationships') || 'No dataset relationships found';
            return `<tr class="reverse-relationship-row"><td colspan="3" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">${msg}</td></tr>`;
        }
        return datasetRelationshipsReverse.map(rel => {
            const relationTypeName = escapeHtml(rel.relationTypeReverseName || rel.relationTypeName || 'N/A');
            const datasetName = rel.datasetName || 'N/A';
            const datasetId = rel.datasetId;
            const datasetLink = createEntityLink(datasetName, 'dataset', datasetId);
            const ownerName = rel.datasetOwnerName || (window.I18n?.t('message.noOwner') || 'No owner');
            return `<tr class="reverse-relationship-row"><td>${relationTypeName}</td><td><div class="entity-info reverse-relationship-entity"><i class="fas fa-database" style="color: var(--text-secondary, #64748b); margin-right: 0.5rem;"></i><span class="entity-name">${datasetLink}</span></div></td><td style="text-align: center;"><div class="owner-info reverse-relationship-owner"><span class="owner-name">${escapeHtml(ownerName)}</span></div></td></tr>`;
        }).join('');
    }
    
    function renderRegulationReverseViewTableBody() {
        if (!regulationRelationshipsReverse || regulationRelationshipsReverse.length === 0) {
            return '<tr class="reverse-relationship-row"><td colspan="3" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">No regulation relationships found</td></tr>';
        }
        return regulationRelationshipsReverse.map(rel => {
            const relationTypeName = escapeHtml(rel.relationTypeName || 'N/A');
            const regulationName = rel.regulationName || 'N/A';
            const regulationId = rel.regulationId;
            const regulationLink = createEntityLink(regulationName, 'regulation', regulationId);
            const ownerName = rel.regulationOwnerName || (window.I18n?.t('message.noOwner') || 'No owner');
            return `<tr class="reverse-relationship-row"><td>${relationTypeName}</td><td><div class="entity-info reverse-relationship-entity"><i class="fas fa-gavel" style="color: var(--text-secondary, #64748b); margin-right: 0.5rem;"></i><span class="entity-name">${regulationLink}</span></div></td><td style="text-align: center;"><div class="owner-info reverse-relationship-owner"><span class="owner-name">${escapeHtml(ownerName)}</span></div></td></tr>`;
        }).join('');
    }
    
    function renderCapabilityReverseViewTableBody() {
        if (!capabilityRelationshipsReverse || capabilityRelationshipsReverse.length === 0) {
            const msg = window.I18n?.t('message.noCapabilityRelationships') || 'No capability relationships found';
            return `<tr class="reverse-relationship-row"><td colspan="3" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">${msg}</td></tr>`;
        }
        return capabilityRelationshipsReverse.map(rel => {
            const relationTypeName = escapeHtml(rel.relationTypeReverseName || rel.relationTypeName || 'N/A');
            const capabilityName = rel.capabilityName || 'N/A';
            const capabilityRefNumber = rel.capabilityRefNumber || '';
            const displayName = capabilityRefNumber ? `${capabilityName} (${capabilityRefNumber})` : capabilityName;
            const capabilityId = rel.capabilityId;
            const capabilityLink = createEntityLink(displayName, 'capability', capabilityId);
            const ownerName = rel.capabilityOwnerName || (window.I18n?.t('message.noOwner') || 'No owner');
            return `<tr class="reverse-relationship-row"><td>${relationTypeName}</td><td><div class="entity-info reverse-relationship-entity"><i class="fas fa-cogs" style="color: var(--text-secondary, #64748b); margin-right: 0.5rem;"></i><span class="entity-name">${capabilityLink}</span></div></td><td style="text-align: center;"><div class="owner-info reverse-relationship-owner"><span class="owner-name">${escapeHtml(ownerName)}</span></div></td></tr>`;
        }).join('');
    }
    
    function renderGlossaryReverseViewTableBody() {
        if (!glossaryRelationshipsReverse || glossaryRelationshipsReverse.length === 0) {
            const msg = window.I18n?.t('message.noGlossaryRelationships') || 'No glossary relationships found';
            return `<tr class="reverse-relationship-row"><td colspan="3" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">${msg}</td></tr>`;
        }
        return glossaryRelationshipsReverse.map(rel => {
            const relationTypeName = escapeHtml(rel.relationTypeReverseName || rel.relationTypeName || 'N/A');
            const glossaryName = rel.glossaryName || 'N/A';
            const glossaryRefNumber = rel.glossaryRefNumber || '';
            const displayName = glossaryRefNumber ? `${glossaryName} (${glossaryRefNumber})` : glossaryName;
            const glossaryId = rel.glossaryId;
            const glossaryLink = createEntityLink(displayName, 'glossary', glossaryId);
            const ownerName = rel.glossaryOwnerName || (window.I18n?.t('message.noOwner') || 'No owner');
            return `<tr class="reverse-relationship-row"><td>${relationTypeName}</td><td><div class="entity-info reverse-relationship-entity"><i class="fas fa-book" style="color: var(--text-secondary, #64748b); margin-right: 0.5rem;"></i><span class="entity-name">${glossaryLink}</span></div></td><td style="text-align: center;"><div class="owner-info reverse-relationship-owner"><span class="owner-name">${escapeHtml(ownerName)}</span></div></td></tr>`;
        }).join('');
    }
    
    function renderProcessReverseViewTableBody() {
        if (!processRelationshipsReverse || processRelationshipsReverse.length === 0) {
            const msg = window.I18n?.t('message.noProcessRelationships') || 'No process relationships found';
            return `<tr class="reverse-relationship-row"><td colspan="3" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">${msg}</td></tr>`;
        }
        return processRelationshipsReverse.map(rel => {
            const relationTypeName = escapeHtml(rel.relationTypeReverseName || rel.relationTypeName || 'N/A');
            const processName = rel.processName || 'N/A';
            const processRefNumber = rel.processRefNumber || '';
            const displayName = processRefNumber ? `${processName} (${processRefNumber})` : processName;
            const processId = rel.processId;
            const processLink = createEntityLink(displayName, 'process', processId);
            const ownerName = rel.processOwnerName || (window.I18n?.t('message.noOwner') || 'No owner');
            return `<tr class="reverse-relationship-row"><td>${relationTypeName}</td><td><div class="entity-info reverse-relationship-entity"><i class="fas fa-cogs" style="color: var(--text-secondary, #64748b); margin-right: 0.5rem;"></i><span class="entity-name">${processLink}</span></div></td><td style="text-align: center;"><div class="owner-info reverse-relationship-owner"><span class="owner-name">${escapeHtml(ownerName)}</span></div></td></tr>`;
        }).join('');
    }
    
    function renderPolicyReverseViewTableBody() {
        if (!policyRelationshipsReverse || policyRelationshipsReverse.length === 0) {
            const msg = window.I18n?.t('message.noPolicyRelationships') || 'No policy relationships found';
            return `<tr class="reverse-relationship-row"><td colspan="3" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">${msg}</td></tr>`;
        }
        return policyRelationshipsReverse.map(rel => {
            const relationTypeName = escapeHtml(rel.relationTypeReverseName || rel.relationTypeName || 'N/A');
            const policyName = rel.policyName || 'N/A';
            const policyId = rel.policyId;
            const policyLink = createEntityLink(policyName, 'policy', policyId);
            const ownerName = rel.policyOwnerName || (window.I18n?.t('message.noOwner') || 'No owner');
            return `<tr class="reverse-relationship-row"><td>${relationTypeName}</td><td><div class="entity-info reverse-relationship-entity"><i class="fas fa-file-contract" style="color: var(--text-secondary, #64748b); margin-right: 0.5rem;"></i><span class="entity-name">${policyLink}</span></div></td><td style="text-align: center;"><div class="owner-info reverse-relationship-owner"><span class="owner-name">${escapeHtml(ownerName)}</span></div></td></tr>`;
        }).join('');
    }
    
    // Setup sub-tab switching for view
    function setupImpactViewSubTabs() {
        const subTabs = document.querySelectorAll('#productImpactContainer .sub-tab');
        const subTabContents = document.querySelectorAll('#productImpactContainer .sub-tab-content');
        
        subTabs.forEach(function(subTab) {
            subTab.addEventListener('click', function(e) {
                e.preventDefault();
                
                // Remove active class from all sub-tabs and contents
                subTabs.forEach(function(t) { t.classList.remove('active'); });
                subTabContents.forEach(function(c) { 
                    c.classList.remove('active');
                    // Remove inline display style to ensure proper hiding
                    c.style.display = 'none';
                });
                
                // Add active class to clicked sub-tab
                this.classList.add('active');
                
                // Show corresponding content
                const subTabName = this.getAttribute('data-sub-tab');
                let targetContent;
                if (subTabName === 'legal') {
                    targetContent = document.getElementById('impactLegalViewContent');
                } else if (subTabName === 'client') {
                    targetContent = document.getElementById('impactClientViewContent');
                } else if (subTabName === 'businessarea') {
                    targetContent = document.getElementById('impactBusinessAreaViewContent');
                } else if (subTabName === 'system') {
                    targetContent = document.getElementById('impactSystemReverseViewContent');
                } else if (subTabName === 'project') {
                    targetContent = document.getElementById('impactProjectReverseViewContent');
                } else if (subTabName === 'dataset') {
                    targetContent = document.getElementById('impactDatasetReverseViewContent');
                } else if (subTabName === 'regulation') {
                    targetContent = document.getElementById('impactRegulationReverseViewContent');
                } else if (subTabName === 'capability') {
                    targetContent = document.getElementById('impactCapabilityReverseViewContent');
                } else if (subTabName === 'glossary') {
                    targetContent = document.getElementById('impactGlossaryReverseViewContent');
                } else if (subTabName === 'process') {
                    targetContent = document.getElementById('impactProcessReverseViewContent');
                } else if (subTabName === 'policy') {
                    targetContent = document.getElementById('impactPolicyReverseViewContent');
                }
                
                if (targetContent) {
                    targetContent.classList.add('active');
                    targetContent.style.display = 'block';
                }
            });
        });
    }

    // Export to global scope
    window.loadProductImpact = loadProductImpact;

})();

