// Client Impact View JavaScript - Shows reverse relationships (which entities impact this Client)
(function() {
    let currentClientId = null;

    // Reverse relationship arrays
    // These show which Systems, Products, Processes, Projects, Policies, Capabilities, Datasets, and Glossaries impact this Client
    let systemRelationshipsReverse = [];
    let productRelationshipsReverse = [];
    let processRelationshipsReverse = [];
    let projectRelationshipsReverse = [];
    let policyRelationshipsReverse = [];
    let capabilityRelationshipsReverse = [];
    let datasetRelationshipsReverse = [];
    let glossaryRelationshipsReverse = [];

    // Helper function to escape HTML
    function escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    // Helper function to create entity link
    function createEntityLink(entityType, entityId, entityName) {
        if (!entityName || !entityId || entityName === 'N/A') {
            return escapeHtml(entityName || 'N/A');
        }
        
        let url;
        switch(entityType) {
            case 'system':
                url = `/view/system/${encodeURIComponent(entityId)}`;
                break;
            case 'product':
                url = `/view/product/${encodeURIComponent(entityId)}`;
                break;
            case 'process':
                url = `/view/process/${encodeURIComponent(entityId)}`;
                break;
            case 'project':
                url = `/view/project/${encodeURIComponent(entityId)}`;
                break;
            case 'policy':
                url = `/view/policy/${encodeURIComponent(entityId)}`;
                break;
            case 'capability':
                url = `/view/capability/${encodeURIComponent(entityId)}`;
                break;
            case 'dataset':
                url = `/view/dataset/${encodeURIComponent(entityId)}`;
                break;
            case 'glossary':
                url = `/view/glossary/${encodeURIComponent(entityId)}`;
                break;
            default:
                return escapeHtml(entityName);
        }
        
        const viewText = window.I18n?.t('clientImpact.messages.view') || 'View';
        return `<a href="${url}" class="entity-link" style="color: var(--secondary-color, #248567); text-decoration: none;" title="${viewText} ${escapeHtml(entityName)}">${escapeHtml(entityName)}</a>`;
    }

    // Initialize Impact view
    function loadClientImpact(clientId) {
        currentClientId = clientId;
        console.log('Loading Client Impact for client:', clientId);
        loadImpactViewData();
    }

    // Load all Impact view data
    async function loadImpactViewData() {
        try {
            // Load reverse relationships
            await Promise.all([
                loadSystemRelationshipsReverse(),
                loadProductRelationshipsReverse(),
                loadProcessRelationshipsReverse(),
                loadProjectRelationshipsReverse(),
                loadPolicyRelationshipsReverse(),
                loadCapabilityRelationshipsReverse(),
                loadDatasetRelationshipsReverse(),
                loadGlossaryRelationshipsReverse()
            ]);

            // Render the view
            renderImpactView();

        } catch (error) {
            console.error('Error loading Impact view data:', error);
        }
    }
    
    // Load reverse relationships - these show which entities impact this Client
    async function loadSystemRelationshipsReverse() {
        try {
            const response = await fetch(`/api/system-impact/clients/${currentClientId}/systems`, {
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
    
    async function loadProductRelationshipsReverse() {
        try {
            const response = await fetch(`/api/product-impact/clients/${currentClientId}/products`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });
            if (!response.ok) {
                productRelationshipsReverse = [];
                return;
            }
            const data = await response.json();
            productRelationshipsReverse = Array.isArray(data) ? data : [];
        } catch (error) {
            console.error('Error loading reverse product relationships:', error);
            productRelationshipsReverse = [];
        }
    }
    
    async function loadProcessRelationshipsReverse() {
        try {
            const response = await fetch(`/api/process-impact/clients/${currentClientId}/processes`, {
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
    
    async function loadProjectRelationshipsReverse() {
        try {
            const response = await fetch(`/api/project-impact/clients/${currentClientId}/projects`, {
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
    
    async function loadPolicyRelationshipsReverse() {
        try {
            const response = await fetch(`/api/policy-impact/clients/${currentClientId}/policies`, {
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
    
    async function loadCapabilityRelationshipsReverse() {
        try {
            const response = await fetch(`/api/capability-impact/clients/${currentClientId}/capabilities`, {
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
    
    async function loadDatasetRelationshipsReverse() {
        try {
            const response = await fetch(`/api/dataset-impact/clients/${currentClientId}/datasets`, {
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
    
    async function loadGlossaryRelationshipsReverse() {
        try {
            const response = await fetch(`/api/glossary-impact/clients/${currentClientId}/glossaries`, {
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

    // Render Impact view with sub-tabs
    function renderImpactView() {
        const container = document.getElementById('clientImpactContainer');
        if (!container) {
            console.error('clientImpactContainer not found');
            return;
        }

        // Determine which sub-tabs to show based on data availability
        const hasSystemReverse = systemRelationshipsReverse.length > 0;
        const hasProductReverse = productRelationshipsReverse.length > 0;
        const hasProcessReverse = processRelationshipsReverse.length > 0;
        const hasProjectReverse = projectRelationshipsReverse.length > 0;
        const hasPolicyReverse = policyRelationshipsReverse.length > 0;
        const hasCapabilityReverse = capabilityRelationshipsReverse.length > 0;
        const hasDatasetReverse = datasetRelationshipsReverse.length > 0;
        const hasGlossaryReverse = glossaryRelationshipsReverse.length > 0;

        if (!hasSystemReverse && !hasProductReverse && !hasProcessReverse && !hasProjectReverse && 
            !hasPolicyReverse && !hasCapabilityReverse && !hasDatasetReverse && !hasGlossaryReverse) {
            container.innerHTML = `
                <div class="view-section" style="grid-column:1/-1;">
                    <div class="section-title">${window.I18n?.t('clientImpact.sectionTitle') || 'IMPACT'}</div>
                    <div class="empty">${window.I18n?.t('message.noImpactRelationships') || 'No impact relationships found'}</div>
                </div>
            `;
            return;
        }

        // Build sub-tabs HTML
        let subTabsHtml = '';
        let subTabsContentHtml = '';
        let firstActive = true;

        if (hasSystemReverse) {
            const activeClass = firstActive ? 'active' : '';
            const systemText = window.I18n?.t('clientImpact.sections.systems') || 'System';
            const listText = window.I18n?.t('clientImpact.buttons.list') || 'List';
            const searchAddText = window.I18n?.t('clientImpact.buttons.searchAndAdd') || 'Search and Add';
            const relTypeText = window.I18n?.t('clientImpact.labels.relationshipType') || 'Relationship Type';
            const systemOwnerText = window.I18n?.t('clientImpact.labels.systemOwner') || 'System Owner';
            const recordText = systemRelationshipsReverse.length !== 1 ? (window.I18n?.t('dataset.messages.records') || 'records') : (window.I18n?.t('dataset.messages.record') || 'record');
            if (firstActive) subTabsHtml += `<button class="sub-tab active" data-sub-tab="system-reverse">${systemText}</button>`;
            else subTabsHtml += `<button class="sub-tab" data-sub-tab="system-reverse">${systemText}</button>`;
            subTabsContentHtml += `
                <div id="impactSystemReverseViewContent" class="sub-tab-content ${activeClass}" ${firstActive ? 'style="display:block;"' : ''}>
                    <div class="relationships-hierarchy">
                        <div class="hierarchy-header">
                            <div class="hierarchy-title">${systemText.toUpperCase()}</div>
                            <div class="hierarchy-actions">
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-list"></i> ${listText}
                                </button>
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-search-plus"></i> ${searchAddText}
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
                                        <th><div class="th-content"><span>${relTypeText}</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content"><span>${systemText}</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content" style="justify-content: center;"><span>${systemOwnerText}</span><i class="fas fa-sort"></i></div></th>
                                    </tr>
                                </thead>
                                <tbody>${renderSystemReverseViewTableBody()}</tbody>
                            </table>
                        </div>
                        <div class="hierarchy-footer">${systemRelationshipsReverse.length} ${recordText}</div>
                    </div>
                </div>
            `;
            firstActive = false;
        }
        
        if (hasProductReverse) {
            const activeClass = firstActive ? 'active' : '';
            const productText = window.I18n?.t('clientImpact.sections.products') || 'Product';
            const listText = window.I18n?.t('clientImpact.buttons.list') || 'List';
            const searchAddText = window.I18n?.t('clientImpact.buttons.searchAndAdd') || 'Search and Add';
            const relTypeText = window.I18n?.t('clientImpact.labels.relationshipType') || 'Relationship Type';
            const productOwnerText = window.I18n?.t('clientImpact.labels.productOwner') || 'Product Owner';
            const recordText = productRelationshipsReverse.length !== 1 ? (window.I18n?.t('dataset.messages.records') || 'records') : (window.I18n?.t('dataset.messages.record') || 'record');
            if (firstActive) subTabsHtml += `<button class="sub-tab active" data-sub-tab="product-reverse">${productText}</button>`;
            else subTabsHtml += `<button class="sub-tab" data-sub-tab="product-reverse">${productText}</button>`;
            subTabsContentHtml += `
                <div id="impactProductReverseViewContent" class="sub-tab-content ${activeClass}" ${firstActive ? 'style="display:block;"' : ''}>
                    <div class="relationships-hierarchy">
                        <div class="hierarchy-header">
                            <div class="hierarchy-title">${productText.toUpperCase()}</div>
                            <div class="hierarchy-actions">
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-list"></i> ${listText}
                                </button>
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-search-plus"></i> ${searchAddText}
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
                                        <th><div class="th-content"><span>${relTypeText}</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content"><span>${productText}</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content" style="justify-content: center;"><span>${productOwnerText}</span><i class="fas fa-sort"></i></div></th>
                                    </tr>
                                </thead>
                                <tbody>${renderProductReverseViewTableBody()}</tbody>
                            </table>
                        </div>
                        <div class="hierarchy-footer">${productRelationshipsReverse.length} ${recordText}</div>
                    </div>
                </div>
            `;
            firstActive = false;
        }
        
        if (hasProcessReverse) {
            const activeClass = firstActive ? 'active' : '';
            const processText = window.I18n?.t('clientImpact.sections.processes') || 'Process';
            const listText = window.I18n?.t('clientImpact.buttons.list') || 'List';
            const searchAddText = window.I18n?.t('clientImpact.buttons.searchAndAdd') || 'Search and Add';
            const relTypeText = window.I18n?.t('clientImpact.labels.relationshipType') || 'Relationship Type';
            const processOwnerText = window.I18n?.t('clientImpact.labels.processOwner') || 'Process Owner';
            const recordText = processRelationshipsReverse.length !== 1 ? (window.I18n?.t('dataset.messages.records') || 'records') : (window.I18n?.t('dataset.messages.record') || 'record');
            if (firstActive) subTabsHtml += `<button class="sub-tab active" data-sub-tab="process-reverse">${processText}</button>`;
            else subTabsHtml += `<button class="sub-tab" data-sub-tab="process-reverse">${processText}</button>`;
            subTabsContentHtml += `
                <div id="impactProcessReverseViewContent" class="sub-tab-content ${activeClass}" ${firstActive ? 'style="display:block;"' : ''}>
                    <div class="relationships-hierarchy">
                        <div class="hierarchy-header">
                            <div class="hierarchy-title">${processText.toUpperCase()}</div>
                            <div class="hierarchy-actions">
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-list"></i> ${listText}
                                </button>
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-search-plus"></i> ${searchAddText}
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
                                        <th><div class="th-content"><span>${relTypeText}</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content"><span>${processText}</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content" style="justify-content: center;"><span>${processOwnerText}</span><i class="fas fa-sort"></i></div></th>
                                    </tr>
                                </thead>
                                <tbody>${renderProcessReverseViewTableBody()}</tbody>
                            </table>
                        </div>
                        <div class="hierarchy-footer">${processRelationshipsReverse.length} ${recordText}</div>
                    </div>
                </div>
            `;
            firstActive = false;
        }
        
        if (hasProjectReverse) {
            const activeClass = firstActive ? 'active' : '';
            const projectText = window.I18n?.t('clientImpact.sections.projects') || 'Project';
            const listText = window.I18n?.t('clientImpact.buttons.list') || 'List';
            const searchAddText = window.I18n?.t('clientImpact.buttons.searchAndAdd') || 'Search and Add';
            const relTypeText = window.I18n?.t('clientImpact.labels.relationshipType') || 'Relationship Type';
            const projectOwnerText = window.I18n?.t('clientImpact.labels.projectOwner') || 'Project Owner';
            const recordText = projectRelationshipsReverse.length !== 1 ? (window.I18n?.t('dataset.messages.records') || 'records') : (window.I18n?.t('dataset.messages.record') || 'record');
            if (firstActive) subTabsHtml += `<button class="sub-tab active" data-sub-tab="project-reverse">${projectText}</button>`;
            else subTabsHtml += `<button class="sub-tab" data-sub-tab="project-reverse">${projectText}</button>`;
            subTabsContentHtml += `
                <div id="impactProjectReverseViewContent" class="sub-tab-content ${activeClass}" ${firstActive ? 'style="display:block;"' : ''}>
                    <div class="relationships-hierarchy">
                        <div class="hierarchy-header">
                            <div class="hierarchy-title">${projectText.toUpperCase()}</div>
                            <div class="hierarchy-actions">
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-list"></i> ${listText}
                                </button>
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-search-plus"></i> ${searchAddText}
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
                                        <th><div class="th-content"><span>${relTypeText}</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content"><span>${projectText}</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content" style="justify-content: center;"><span>${projectOwnerText}</span><i class="fas fa-sort"></i></div></th>
                                    </tr>
                                </thead>
                                <tbody>${renderProjectReverseViewTableBody()}</tbody>
                            </table>
                        </div>
                        <div class="hierarchy-footer">${projectRelationshipsReverse.length} ${recordText}</div>
                    </div>
                </div>
            `;
            firstActive = false;
        }
        
        if (hasPolicyReverse) {
            const activeClass = firstActive ? 'active' : '';
            const policyText = window.I18n?.t('clientImpact.sections.policies') || 'Policy';
            const listText = window.I18n?.t('clientImpact.buttons.list') || 'List';
            const searchAddText = window.I18n?.t('clientImpact.buttons.searchAndAdd') || 'Search and Add';
            const relTypeText = window.I18n?.t('clientImpact.labels.relationshipType') || 'Relationship Type';
            const policyOwnerText = window.I18n?.t('clientImpact.labels.policyOwner') || 'Policy Owner';
            const recordText = policyRelationshipsReverse.length !== 1 ? (window.I18n?.t('dataset.messages.records') || 'records') : (window.I18n?.t('dataset.messages.record') || 'record');
            if (firstActive) subTabsHtml += `<button class="sub-tab active" data-sub-tab="policy-reverse">${policyText}</button>`;
            else subTabsHtml += `<button class="sub-tab" data-sub-tab="policy-reverse">${policyText}</button>`;
            subTabsContentHtml += `
                <div id="impactPolicyReverseViewContent" class="sub-tab-content ${activeClass}" ${firstActive ? 'style="display:block;"' : ''}>
                    <div class="relationships-hierarchy">
                        <div class="hierarchy-header">
                            <div class="hierarchy-title">${policyText.toUpperCase()}</div>
                            <div class="hierarchy-actions">
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-list"></i> ${listText}
                                </button>
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-search-plus"></i> ${searchAddText}
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
                                        <th><div class="th-content"><span>${relTypeText}</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content"><span>${policyText}</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content" style="justify-content: center;"><span>${policyOwnerText}</span><i class="fas fa-sort"></i></div></th>
                                    </tr>
                                </thead>
                                <tbody>${renderPolicyReverseViewTableBody()}</tbody>
                            </table>
                        </div>
                        <div class="hierarchy-footer">${policyRelationshipsReverse.length} ${recordText}</div>
                    </div>
                </div>
            `;
            firstActive = false;
        }
        
        if (hasCapabilityReverse) {
            const activeClass = firstActive ? 'active' : '';
            const capabilityText = window.I18n?.t('clientImpact.sections.capabilities') || 'Capability';
            const listText = window.I18n?.t('clientImpact.buttons.list') || 'List';
            const searchAddText = window.I18n?.t('clientImpact.buttons.searchAndAdd') || 'Search and Add';
            const relTypeText = window.I18n?.t('clientImpact.labels.relationshipType') || 'Relationship Type';
            const capabilityOwnerText = window.I18n?.t('clientImpact.labels.capabilityOwner') || 'Capability Owner';
            const recordText = capabilityRelationshipsReverse.length !== 1 ? (window.I18n?.t('dataset.messages.records') || 'records') : (window.I18n?.t('dataset.messages.record') || 'record');
            if (firstActive) subTabsHtml += `<button class="sub-tab active" data-sub-tab="capability-reverse">${capabilityText}</button>`;
            else subTabsHtml += `<button class="sub-tab" data-sub-tab="capability-reverse">${capabilityText}</button>`;
            subTabsContentHtml += `
                <div id="impactCapabilityReverseViewContent" class="sub-tab-content ${activeClass}" ${firstActive ? 'style="display:block;"' : ''}>
                    <div class="relationships-hierarchy">
                        <div class="hierarchy-header">
                            <div class="hierarchy-title">${capabilityText.toUpperCase()}</div>
                            <div class="hierarchy-actions">
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-list"></i> ${listText}
                                </button>
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-search-plus"></i> ${searchAddText}
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
                                        <th><div class="th-content"><span>${relTypeText}</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content"><span>${capabilityText}</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content" style="justify-content: center;"><span>${capabilityOwnerText}</span><i class="fas fa-sort"></i></div></th>
                                    </tr>
                                </thead>
                                <tbody>${renderCapabilityReverseViewTableBody()}</tbody>
                            </table>
                        </div>
                        <div class="hierarchy-footer">${capabilityRelationshipsReverse.length} ${recordText}</div>
                    </div>
                </div>
            `;
            firstActive = false;
        }
        
        if (hasDatasetReverse) {
            const activeClass = firstActive ? 'active' : '';
            const datasetText = window.I18n?.t('clientImpact.sections.datasets') || 'Dataset';
            const listText = window.I18n?.t('clientImpact.buttons.list') || 'List';
            const searchAddText = window.I18n?.t('clientImpact.buttons.searchAndAdd') || 'Search and Add';
            const relTypeText = window.I18n?.t('clientImpact.labels.relationshipType') || 'Relationship Type';
            const datasetOwnerText = window.I18n?.t('clientImpact.labels.datasetOwner') || 'Dataset Owner';
            const recordText = datasetRelationshipsReverse.length !== 1 ? (window.I18n?.t('dataset.messages.records') || 'records') : (window.I18n?.t('dataset.messages.record') || 'record');
            if (firstActive) subTabsHtml += `<button class="sub-tab active" data-sub-tab="dataset-reverse">${datasetText}</button>`;
            else subTabsHtml += `<button class="sub-tab" data-sub-tab="dataset-reverse">${datasetText}</button>`;
            subTabsContentHtml += `
                <div id="impactDatasetReverseViewContent" class="sub-tab-content ${activeClass}" ${firstActive ? 'style="display:block;"' : ''}>
                    <div class="relationships-hierarchy">
                        <div class="hierarchy-header">
                            <div class="hierarchy-title">${datasetText.toUpperCase()}</div>
                            <div class="hierarchy-actions">
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-list"></i> ${listText}
                                </button>
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-search-plus"></i> ${searchAddText}
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
                                        <th><div class="th-content"><span>${relTypeText}</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content"><span>${datasetText}</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content" style="justify-content: center;"><span>${datasetOwnerText}</span><i class="fas fa-sort"></i></div></th>
                                    </tr>
                                </thead>
                                <tbody>${renderDatasetReverseViewTableBody()}</tbody>
                            </table>
                        </div>
                        <div class="hierarchy-footer">${datasetRelationshipsReverse.length} ${recordText}</div>
                    </div>
                </div>
            `;
            firstActive = false;
        }
        
        if (hasGlossaryReverse) {
            const activeClass = firstActive ? 'active' : '';
            const glossaryText = window.I18n?.t('clientImpact.sections.glossaries') || 'Glossary';
            const listText = window.I18n?.t('clientImpact.buttons.list') || 'List';
            const searchAddText = window.I18n?.t('clientImpact.buttons.searchAndAdd') || 'Search and Add';
            const relTypeText = window.I18n?.t('clientImpact.labels.relationshipType') || 'Relationship Type';
            const glossaryOwnerText = window.I18n?.t('clientImpact.labels.glossaryOwner') || 'Glossary Owner';
            const recordText = glossaryRelationshipsReverse.length !== 1 ? (window.I18n?.t('dataset.messages.records') || 'records') : (window.I18n?.t('dataset.messages.record') || 'record');
            if (firstActive) subTabsHtml += `<button class="sub-tab active" data-sub-tab="glossary-reverse">${glossaryText}</button>`;
            else subTabsHtml += `<button class="sub-tab" data-sub-tab="glossary-reverse">${glossaryText}</button>`;
            subTabsContentHtml += `
                <div id="impactGlossaryReverseViewContent" class="sub-tab-content ${activeClass}" ${firstActive ? 'style="display:block;"' : ''}>
                    <div class="relationships-hierarchy">
                        <div class="hierarchy-header">
                            <div class="hierarchy-title">${glossaryText.toUpperCase()}</div>
                            <div class="hierarchy-actions">
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-list"></i> ${listText}
                                </button>
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-search-plus"></i> ${searchAddText}
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
                                        <th><div class="th-content"><span>${relTypeText}</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content"><span>${glossaryText}</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content" style="justify-content: center;"><span>${glossaryOwnerText}</span><i class="fas fa-sort"></i></div></th>
                                    </tr>
                                </thead>
                                <tbody>${renderGlossaryReverseViewTableBody()}</tbody>
                            </table>
                        </div>
                        <div class="hierarchy-footer">${glossaryRelationshipsReverse.length} ${recordText}</div>
                    </div>
                </div>
            `;
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
    }

    // Render reverse relationship table bodies
    function renderSystemReverseViewTableBody() {
        if (!systemRelationshipsReverse || systemRelationshipsReverse.length === 0) {
            const msg = window.I18n?.t('message.noSystemRelationships') || 'No system relationships found';
            return `<tr class="reverse-relationship-row"><td colspan="3" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">${msg}</td></tr>`;
        }
        const noOwnerText = window.I18n?.t('message.noOwner') || 'No owner';
        return systemRelationshipsReverse.map(rel => {
            const relationTypeName = escapeHtml(rel.relationTypeReverseName || rel.relationTypeName || 'N/A');
            const systemName = rel.systemName || rel.systemLongName || 'N/A';
            const systemId = rel.systemId;
            const systemLink = createEntityLink('system', systemId, systemName);
            const ownerName = rel.systemOwnerName || rel.ownerName || noOwnerText;
            return `<tr class="reverse-relationship-row"><td>${relationTypeName}</td><td><div class="entity-info reverse-relationship-entity"><i class="fas fa-server" style="color: var(--text-secondary, #64748b); margin-right: 0.5rem;"></i><span class="entity-name">${systemLink}</span></div></td><td style="text-align: center;"><div class="owner-info reverse-relationship-owner"><span class="owner-name">${escapeHtml(ownerName)}</span></div></td></tr>`;
        }).join('');
    }
    
    function renderProductReverseViewTableBody() {
        if (!productRelationshipsReverse || productRelationshipsReverse.length === 0) {
            const msg = window.I18n?.t('message.noProductRelationships') || 'No product relationships found';
            return `<tr class="reverse-relationship-row"><td colspan="3" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">${msg}</td></tr>`;
        }
        const noOwnerText = window.I18n?.t('message.noOwner') || 'No owner';
        return productRelationshipsReverse.map(rel => {
            const relationTypeName = escapeHtml(rel.relationTypeReverseName || rel.relationTypeName || 'N/A');
            const productName = rel.productName || 'N/A';
            const productId = rel.productId;
            const productLink = createEntityLink('product', productId, productName);
            const ownerName = rel.productOwnerName || rel.ownerName || noOwnerText;
            return `<tr class="reverse-relationship-row"><td>${relationTypeName}</td><td><div class="entity-info reverse-relationship-entity"><i class="fas fa-box" style="color: var(--text-secondary, #64748b); margin-right: 0.5rem;"></i><span class="entity-name">${productLink}</span></div></td><td style="text-align: center;"><div class="owner-info reverse-relationship-owner"><span class="owner-name">${escapeHtml(ownerName)}</span></div></td></tr>`;
        }).join('');
    }
    
    function renderProcessReverseViewTableBody() {
        if (!processRelationshipsReverse || processRelationshipsReverse.length === 0) {
            const msg = window.I18n?.t('message.noProcessRelationships') || 'No process relationships found';
            return `<tr class="reverse-relationship-row"><td colspan="3" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">${msg}</td></tr>`;
        }
        const noOwnerText = window.I18n?.t('message.noOwner') || 'No owner';
        return processRelationshipsReverse.map(rel => {
            const relationTypeName = escapeHtml(rel.relationTypeReverseName || rel.relationTypeName || 'N/A');
            const processName = rel.processName || 'N/A';
            const processId = rel.processId;
            const processLink = createEntityLink('process', processId, processName);
            const ownerName = rel.processOwnerName || rel.ownerName || noOwnerText;
            return `<tr class="reverse-relationship-row"><td>${relationTypeName}</td><td><div class="entity-info reverse-relationship-entity"><i class="fas fa-cogs" style="color: var(--text-secondary, #64748b); margin-right: 0.5rem;"></i><span class="entity-name">${processLink}</span></div></td><td style="text-align: center;"><div class="owner-info reverse-relationship-owner"><span class="owner-name">${escapeHtml(ownerName)}</span></div></td></tr>`;
        }).join('');
    }
    
    function renderProjectReverseViewTableBody() {
        if (!projectRelationshipsReverse || projectRelationshipsReverse.length === 0) {
            const msg = window.I18n?.t('message.noProjectRelationships') || 'No project relationships found';
            return `<tr class="reverse-relationship-row"><td colspan="3" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">${msg}</td></tr>`;
        }
        const noOwnerText = window.I18n?.t('message.noOwner') || 'No owner';
        return projectRelationshipsReverse.map(rel => {
            const relationTypeName = escapeHtml(rel.relationTypeReverseName || rel.relationTypeName || 'N/A');
            const projectName = rel.projectName || 'N/A';
            const projectId = rel.projectId;
            const projectLink = createEntityLink('project', projectId, projectName);
            const ownerName = rel.projectOwnerName || rel.ownerName || noOwnerText;
            return `<tr class="reverse-relationship-row"><td>${relationTypeName}</td><td><div class="entity-info reverse-relationship-entity"><i class="fas fa-project-diagram" style="color: var(--text-secondary, #64748b); margin-right: 0.5rem;"></i><span class="entity-name">${projectLink}</span></div></td><td style="text-align: center;"><div class="owner-info reverse-relationship-owner"><span class="owner-name">${escapeHtml(ownerName)}</span></div></td></tr>`;
        }).join('');
    }
    
    function renderPolicyReverseViewTableBody() {
        if (!policyRelationshipsReverse || policyRelationshipsReverse.length === 0) {
            const msg = window.I18n?.t('message.noPolicyRelationships') || 'No policy relationships found';
            return `<tr class="reverse-relationship-row"><td colspan="3" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">${msg}</td></tr>`;
        }
        const noOwnerText = window.I18n?.t('message.noOwner') || 'No owner';
        return policyRelationshipsReverse.map(rel => {
            const relationTypeName = escapeHtml(rel.relationTypeReverseName || rel.relationTypeName || 'N/A');
            const policyName = rel.policyName || 'N/A';
            const policyId = rel.policyId;
            const policyLink = createEntityLink('policy', policyId, policyName);
            const ownerName = rel.policyOwnerName || rel.ownerName || noOwnerText;
            return `<tr class="reverse-relationship-row"><td>${relationTypeName}</td><td><div class="entity-info reverse-relationship-entity"><i class="fas fa-file-contract" style="color: var(--text-secondary, #64748b); margin-right: 0.5rem;"></i><span class="entity-name">${policyLink}</span></div></td><td style="text-align: center;"><div class="owner-info reverse-relationship-owner"><span class="owner-name">${escapeHtml(ownerName)}</span></div></td></tr>`;
        }).join('');
    }
    
    function renderCapabilityReverseViewTableBody() {
        if (!capabilityRelationshipsReverse || capabilityRelationshipsReverse.length === 0) {
            const msg = window.I18n?.t('message.noCapabilityRelationships') || 'No capability relationships found';
            return `<tr class="reverse-relationship-row"><td colspan="3" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">${msg}</td></tr>`;
        }
        const noOwnerText = window.I18n?.t('message.noOwner') || 'No owner';
        return capabilityRelationshipsReverse.map(rel => {
            const relationTypeName = escapeHtml(rel.relationTypeReverseName || rel.relationTypeName || 'N/A');
            const capabilityName = rel.capabilityName || 'N/A';
            const capabilityId = rel.capabilityId;
            const capabilityLink = createEntityLink('capability', capabilityId, capabilityName);
            const ownerName = rel.capabilityOwnerName || rel.ownerName || noOwnerText;
            return `<tr class="reverse-relationship-row"><td>${relationTypeName}</td><td><div class="entity-info reverse-relationship-entity"><i class="fas fa-lightbulb" style="color: var(--text-secondary, #64748b); margin-right: 0.5rem;"></i><span class="entity-name">${capabilityLink}</span></div></td><td style="text-align: center;"><div class="owner-info reverse-relationship-owner"><span class="owner-name">${escapeHtml(ownerName)}</span></div></td></tr>`;
        }).join('');
    }
    
    function renderDatasetReverseViewTableBody() {
        if (!datasetRelationshipsReverse || datasetRelationshipsReverse.length === 0) {
            const msg = window.I18n?.t('message.noDatasetRelationships') || 'No dataset relationships found';
            return `<tr class="reverse-relationship-row"><td colspan="3" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">${msg}</td></tr>`;
        }
        const noOwnerText = window.I18n?.t('message.noOwner') || 'No owner';
        return datasetRelationshipsReverse.map(rel => {
            const relationTypeName = escapeHtml(rel.relationTypeReverseName || rel.relationTypeName || 'N/A');
            const datasetName = rel.datasetName || 'N/A';
            const datasetId = rel.datasetId;
            const datasetLink = createEntityLink('dataset', datasetId, datasetName);
            const ownerName = rel.datasetOwnerName || rel.ownerName || noOwnerText;
            return `<tr class="reverse-relationship-row"><td>${relationTypeName}</td><td><div class="entity-info reverse-relationship-entity"><i class="fas fa-database" style="color: var(--text-secondary, #64748b); margin-right: 0.5rem;"></i><span class="entity-name">${datasetLink}</span></div></td><td style="text-align: center;"><div class="owner-info reverse-relationship-owner"><span class="owner-name">${escapeHtml(ownerName)}</span></div></td></tr>`;
        }).join('');
    }
    
    function renderGlossaryReverseViewTableBody() {
        if (!glossaryRelationshipsReverse || glossaryRelationshipsReverse.length === 0) {
            const msg = window.I18n?.t('message.noGlossaryRelationships') || 'No glossary relationships found';
            return `<tr class="reverse-relationship-row"><td colspan="3" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">${msg}</td></tr>`;
        }
        const noOwnerText = window.I18n?.t('message.noOwner') || 'No owner';
        return glossaryRelationshipsReverse.map(rel => {
            const relationTypeName = escapeHtml(rel.relationTypeReverseName || rel.relationTypeName || 'N/A');
            const glossaryName = rel.glossaryName || 'N/A';
            const glossaryId = rel.glossaryId;
            const glossaryLink = createEntityLink('glossary', glossaryId, glossaryName);
            const ownerName = rel.glossaryOwnerName || rel.ownerName || noOwnerText;
            return `<tr class="reverse-relationship-row"><td>${relationTypeName}</td><td><div class="entity-info reverse-relationship-entity"><i class="fas fa-book" style="color: var(--text-secondary, #64748b); margin-right: 0.5rem;"></i><span class="entity-name">${glossaryLink}</span></div></td><td style="text-align: center;"><div class="owner-info reverse-relationship-owner"><span class="owner-name">${escapeHtml(ownerName)}</span></div></td></tr>`;
        }).join('');
    }

    // Setup sub-tab switching
    function setupImpactViewSubTabs() {
        console.log('Setting up Impact view sub-tabs...');
        
        const subTabs = document.querySelectorAll('#clientImpactContainer .sub-tab');
        console.log('Found Impact view sub-tabs:', subTabs.length);
        
        subTabs.forEach(function(subTab) {
            subTab.addEventListener('click', function(e) {
                e.preventDefault();
                console.log('Impact view sub-tab clicked:', this.textContent.trim());
                
                // Remove active class from all sub-tabs
                subTabs.forEach(function(t) { t.classList.remove('active'); });
                this.classList.add('active');
                
                const subTabName = this.getAttribute('data-sub-tab');
                console.log('Switching to Impact view sub-tab:', subTabName);
                
                // Hide all sub-tab contents
                const subTabContents = document.querySelectorAll('#clientImpactContainer .sub-tab-content');
                subTabContents.forEach(function(content) {
                    content.classList.remove('active');
                    content.style.display = 'none';
                });
                
                // Show selected sub-tab content
                let targetSubTab;
                if (subTabName === 'system-reverse') {
                    targetSubTab = document.getElementById('impactSystemReverseViewContent');
                } else if (subTabName === 'product-reverse') {
                    targetSubTab = document.getElementById('impactProductReverseViewContent');
                } else if (subTabName === 'process-reverse') {
                    targetSubTab = document.getElementById('impactProcessReverseViewContent');
                } else if (subTabName === 'project-reverse') {
                    targetSubTab = document.getElementById('impactProjectReverseViewContent');
                } else if (subTabName === 'policy-reverse') {
                    targetSubTab = document.getElementById('impactPolicyReverseViewContent');
                } else if (subTabName === 'capability-reverse') {
                    targetSubTab = document.getElementById('impactCapabilityReverseViewContent');
                } else if (subTabName === 'dataset-reverse') {
                    targetSubTab = document.getElementById('impactDatasetReverseViewContent');
                } else if (subTabName === 'glossary-reverse') {
                    targetSubTab = document.getElementById('impactGlossaryReverseViewContent');
                }
                
                if (targetSubTab) {
                    targetSubTab.classList.add('active');
                    targetSubTab.style.display = 'block';
                    console.log('Switched to Impact view sub-tab:', subTabName);
                } else {
                    console.error('Impact view sub-tab content not found for:', subTabName);
                }
            });
        });
    }

    // Expose function globally for use by client.js
    window.loadClientImpact = loadClientImpact;
    
    // Also expose as initImpactView for compatibility
    window.initClientImpactView = function(clientId, container) {
        if (container) {
            // If container is provided, ensure it's set up
            container.id = 'clientImpactContainer';
        }
        loadClientImpact(clientId);
        return Promise.resolve();
    };

})();

