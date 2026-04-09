// Legal Impact View JavaScript - Display read-only geography relationships and reverse lookups
console.log('=== LEGAL IMPACT VIEW SCRIPT LOADING ===');

(function() {
    console.log('=== LEGAL IMPACT VIEW SCRIPT LOADED ===');
    
    let currentLegalId = null;
    let geographyRelationships = [];
    let productRelationshipsReverse = [];
    let systemRelationshipsReverse = [];
    let clientRelationshipsReverse = [];
    let datasetRelationshipsReverse = [];
    let projectRelationshipsReverse = [];
    let capabilityRelationshipsReverse = [];
    let processRelationshipsReverse = [];
    let policyRelationshipsReverse = [];
    
    // Helper function to escape HTML
    function escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
    
    // Helper function to create entity link
    function createEntityLink(entityType, id, name) {
        if (!id || !name || name === 'N/A') {
            return escapeHtml(name || 'N/A');
        }
        
        let url;
        switch(entityType) {
            case 'geography':
                url = `/view/geography/geography.html?id=${encodeURIComponent(id)}`;
                break;
            case 'product':
                url = `/view/product/${encodeURIComponent(id)}`;
                break;
            case 'system':
                url = `/view/system/${encodeURIComponent(id)}`;
                break;
            case 'client':
                url = `/view/client/${encodeURIComponent(id)}`;
                break;
            case 'dataset':
                url = `/view/dataset/${encodeURIComponent(id)}`;
                break;
            case 'project':
                url = `/view/project/${encodeURIComponent(id)}`;
                break;
            case 'capability':
                url = `/view/capability/${encodeURIComponent(id)}`;
                break;
            case 'process':
                url = `/view/process/${encodeURIComponent(id)}`;
                break;
            case 'policy':
                url = `/view/policy/${encodeURIComponent(id)}`;
                break;
            default:
                return escapeHtml(name);
        }
        
        const viewText = window.I18n?.t('legalImpact.messages.view') || 'View';
        return `<a href="${url}" class="entity-link" style="color: var(--secondary-color, #248567); text-decoration: none;" title="${viewText} ${escapeHtml(name)}">${escapeHtml(name)}</a>`;
    }
    
    // Load Legal Impact data
    async function loadLegalImpact(legalId) {
        try {
            console.log('Loading legal impact for ID:', legalId);
            currentLegalId = legalId;
            
            await loadImpactViewData();

        } catch (error) {
            console.error('Error loading legal impact:', error);
            renderImpactView();
        }
    }
    
    // Load all Impact view data
    async function loadImpactViewData() {
        try {
            await Promise.all([
                loadGeographyRelationshipsView(),
                loadProductRelationshipsReverse(),
                loadSystemRelationshipsReverse(),
                loadClientRelationshipsReverse(),
                loadDatasetRelationshipsReverse(),
                loadProjectRelationshipsReverse(),
                loadCapabilityRelationshipsReverse(),
                loadProcessRelationshipsReverse(),
                loadPolicyRelationshipsReverse()
            ]);
            
            renderImpactView();
        } catch (error) {
            console.error('Error loading Impact view data:', error);
            renderImpactView();
        }
    }
    
    // Load geography relationships (forward)
    async function loadGeographyRelationshipsView() {
        try {
            const response = await fetch(`/api/legal-impact/${currentLegalId}/geographies`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) {
                if (response.status === 404) {
                    geographyRelationships = [];
                    return;
                }
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const data = await response.json();
            geographyRelationships = Array.isArray(data) ? data : [];
        } catch (error) {
            console.error('Error loading geography relationships:', error);
            geographyRelationships = [];
        }
    }
    
    // Load reverse relationships
    async function loadProductRelationshipsReverse() {
        try {
            const response = await fetch(`/api/product-impact/legals/${currentLegalId}/products`, {
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
    
    async function loadSystemRelationshipsReverse() {
        try {
            const response = await fetch(`/api/system-impact/legals/${currentLegalId}/systems`, {
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
    
    async function loadClientRelationshipsReverse() {
        // Note: Client reverse lookup for Legal Entity may not be implemented in backend
        // Keeping this for future implementation
        clientRelationshipsReverse = [];
    }
    
    async function loadDatasetRelationshipsReverse() {
        try {
            const response = await fetch(`/api/dataset-impact/legals/${currentLegalId}/datasets`, {
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
    
    async function loadProjectRelationshipsReverse() {
        try {
            const response = await fetch(`/api/project-impact/legals/${currentLegalId}/projects`, {
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
    
    async function loadCapabilityRelationshipsReverse() {
        try {
            const response = await fetch(`/api/capability-impact/legals/${currentLegalId}/capabilities`, {
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
    
    async function loadProcessRelationshipsReverse() {
        try {
            const response = await fetch(`/api/process-impact/legals/${currentLegalId}/processes`, {
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
            const response = await fetch(`/api/policy-impact/legals/${currentLegalId}/policies`, {
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

    function renderImpactView() {
        const container = document.getElementById('legalEntityImpactContainer');
        if (!container) {
            console.warn('Legal Impact container not found');
            return;
        }

        // Check if there are any relationships
        const hasGeographyData = geographyRelationships.length > 0;
        const hasProductReverse = productRelationshipsReverse.length > 0;
        const hasSystemReverse = systemRelationshipsReverse.length > 0;
        const hasDatasetReverse = datasetRelationshipsReverse.length > 0;
        const hasProjectReverse = projectRelationshipsReverse.length > 0;
        const hasCapabilityReverse = capabilityRelationshipsReverse.length > 0;
        const hasProcessReverse = processRelationshipsReverse.length > 0;
        const hasPolicyReverse = policyRelationshipsReverse.length > 0;
        
        const hasAnyData = hasGeographyData || hasProductReverse || hasSystemReverse || hasDatasetReverse || hasProjectReverse || hasCapabilityReverse || hasProcessReverse || hasPolicyReverse;

        if (!hasAnyData) {
            container.innerHTML = `
                <div class="view-section" style="grid-column:1/-1;">
                    <div style="padding: 2rem; text-align: center; color: var(--text-muted, #6b7280);">
                        <i class="fas fa-info-circle" style="font-size: 2rem; margin-bottom: 1rem; opacity: 0.5;"></i>
                        <p>${window.I18n ? window.I18n.t('legalEntity.messages.noImpactRelationships') : 'No impact relationships found'}</p>
                    </div>
                </div>
            `;
            return;
        }

        // Build sub-tabs HTML
        let subTabsHtml = '';
        let subTabsContentHtml = '';
        let firstActive = true;
        
        if (hasGeographyData) {
            const geographyLabel = window.I18n ? window.I18n.t('legalEntity.impact.tabs.geography') : 'Geography';
            subTabsHtml += `<button class="sub-tab active" data-sub-tab="geography">${geographyLabel}</button>`;
            subTabsContentHtml += `
                <div id="impactGeographyViewContent" class="sub-tab-content active" style="display:block;">
                    <div class="relationships-hierarchy">
                        <div class="hierarchy-header">
                            <div class="hierarchy-title">${window.I18n ? window.I18n.t('legalEntity.impact.headers.geography') : 'GEOGRAPHY'}</div>
                            <div class="hierarchy-actions">
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-list"></i> ${window.I18n ? window.I18n.t('legalEntity.buttons.list') : 'List'}
                                </button>
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-search-plus"></i> ${window.I18n ? window.I18n.t('legalEntity.buttons.searchAndAdd') : 'Search and Add'}
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
                                        <th><div class="th-content"><span>${window.I18n ? window.I18n.t('legalEntity.impact.columns.relationshipType') : 'Relationship Type'}</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content"><span>${window.I18n ? window.I18n.t('legalEntity.impact.columns.geography') : 'Geography'}</span><i class="fas fa-sort"></i></div></th>
                                    </tr>
                                </thead>
                                <tbody>${renderGeographyViewTableBody()}</tbody>
                            </table>
                        </div>
                        <div class="hierarchy-footer">${geographyRelationships.length === 1 
                            ? (window.I18n ? window.I18n.t('legalEntity.relationships.records', {count: geographyRelationships.length}) : `${geographyRelationships.length} record`)
                            : (window.I18n ? window.I18n.t('legalEntity.relationships.recordsPlural', {count: geographyRelationships.length}) : `${geographyRelationships.length} records`)}</div>
                    </div>
                </div>
            `;
            firstActive = false;
        }
        
        if (hasProductReverse) {
            const activeClass = firstActive ? 'active' : '';
            const productLabel = window.I18n ? window.I18n.t('legalEntity.impact.tabs.product') : 'Product';
            if (firstActive) subTabsHtml += `<button class="sub-tab active" data-sub-tab="product">${productLabel}</button>`;
            else subTabsHtml += `<button class="sub-tab" data-sub-tab="product">${productLabel}</button>`;
            subTabsContentHtml += `
                <div id="impactProductReverseViewContent" class="sub-tab-content ${activeClass}" ${firstActive ? 'style="display:block;"' : ''}>
                    <div class="relationships-hierarchy">
                        <div class="hierarchy-header">
                            <div class="hierarchy-title">${window.I18n ? window.I18n.t('legalEntity.impact.headers.product') : 'PRODUCT'}</div>
                            <div class="hierarchy-actions">
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-list"></i> ${window.I18n ? window.I18n.t('legalEntity.buttons.list') : 'List'}
                                </button>
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-search-plus"></i> ${window.I18n ? window.I18n.t('legalEntity.buttons.searchAndAdd') : 'Search and Add'}
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
                                        <th><div class="th-content"><span>Product</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content" style="justify-content: center;"><span>Product Owner</span><i class="fas fa-sort"></i></div></th>
                                    </tr>
                                </thead>
                                <tbody>${renderProductReverseViewTableBody()}</tbody>
                            </table>
                        </div>
                        <div class="hierarchy-footer">${productRelationshipsReverse.length} record${productRelationshipsReverse.length !== 1 ? 's' : ''}</div>
                    </div>
                </div>
            `;
            firstActive = false;
        }
        
        if (hasSystemReverse) {
            const activeClass = firstActive ? 'active' : '';
            const systemLabel = window.I18n ? window.I18n.t('legalEntity.impact.tabs.system') : 'System';
            if (firstActive) subTabsHtml += `<button class="sub-tab active" data-sub-tab="system">${systemLabel}</button>`;
            else subTabsHtml += `<button class="sub-tab" data-sub-tab="system">${systemLabel}</button>`;
            subTabsContentHtml += `
                <div id="impactSystemReverseViewContent" class="sub-tab-content ${activeClass}" ${firstActive ? 'style="display:block;"' : ''}>
                    <div class="relationships-hierarchy">
                        <div class="hierarchy-header">
                            <div class="hierarchy-title">${window.I18n ? window.I18n.t('legalEntity.impact.headers.system') : 'SYSTEM'}</div>
                            <div class="hierarchy-actions">
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-list"></i> ${window.I18n ? window.I18n.t('legalEntity.buttons.list') : 'List'}
                                </button>
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-search-plus"></i> ${window.I18n ? window.I18n.t('legalEntity.buttons.searchAndAdd') : 'Search and Add'}
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
                        <div class="hierarchy-footer">${systemRelationshipsReverse.length} record${systemRelationshipsReverse.length !== 1 ? 's' : ''}</div>
                    </div>
                </div>
            `;
            firstActive = false;
        }
        
        if (hasDatasetReverse) {
            const activeClass = firstActive ? 'active' : '';
            const datasetLabel = window.I18n ? window.I18n.t('legalEntity.impact.tabs.dataset') : 'Dataset';
            if (firstActive) subTabsHtml += `<button class="sub-tab active" data-sub-tab="dataset">${datasetLabel}</button>`;
            else subTabsHtml += `<button class="sub-tab" data-sub-tab="dataset">${datasetLabel}</button>`;
            subTabsContentHtml += `
                <div id="impactDatasetReverseViewContent" class="sub-tab-content ${activeClass}" ${firstActive ? 'style="display:block;"' : ''}>
                    <div class="relationships-hierarchy">
                        <div class="hierarchy-header">
                            <div class="hierarchy-title">${window.I18n ? window.I18n.t('legalEntity.impact.headers.dataset') : 'DATASET'}</div>
                            <div class="hierarchy-actions">
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-list"></i> ${window.I18n ? window.I18n.t('legalEntity.buttons.list') : 'List'}
                                </button>
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-search-plus"></i> ${window.I18n ? window.I18n.t('legalEntity.buttons.searchAndAdd') : 'Search and Add'}
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
        
        if (hasProjectReverse) {
            const activeClass = firstActive ? 'active' : '';
            const projectLabel = window.I18n ? window.I18n.t('legalEntity.impact.tabs.project') : 'Project';
            if (firstActive) subTabsHtml += `<button class="sub-tab active" data-sub-tab="project">${projectLabel}</button>`;
            else subTabsHtml += `<button class="sub-tab" data-sub-tab="project">${projectLabel}</button>`;
            subTabsContentHtml += `
                <div id="impactProjectReverseViewContent" class="sub-tab-content ${activeClass}" ${firstActive ? 'style="display:block;"' : ''}>
                    <div class="relationships-hierarchy">
                        <div class="hierarchy-header">
                            <div class="hierarchy-title">${window.I18n ? window.I18n.t('legalEntity.impact.headers.project') : 'PROJECT'}</div>
                            <div class="hierarchy-actions">
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-list"></i> ${window.I18n ? window.I18n.t('legalEntity.buttons.list') : 'List'}
                                </button>
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-search-plus"></i> ${window.I18n ? window.I18n.t('legalEntity.buttons.searchAndAdd') : 'Search and Add'}
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
                                        <th><div class="th-content" style="justify-content: center;"><span>Project Owner</span><i class="fas fa-sort"></i></div></th>
                                    </tr>
                                </thead>
                                <tbody>${renderProjectReverseViewTableBody()}</tbody>
                            </table>
                        </div>
                        <div class="hierarchy-footer">${projectRelationshipsReverse.length} record${projectRelationshipsReverse.length !== 1 ? 's' : ''}</div>
                    </div>
                </div>
            `;
            firstActive = false;
        }
        
        if (hasCapabilityReverse) {
            const activeClass = firstActive ? 'active' : '';
            const capabilityLabel = window.I18n ? window.I18n.t('legalEntity.impact.tabs.capability') : 'Capability';
            if (firstActive) subTabsHtml += `<button class="sub-tab active" data-sub-tab="capability">${capabilityLabel}</button>`;
            else subTabsHtml += `<button class="sub-tab" data-sub-tab="capability">${capabilityLabel}</button>`;
            subTabsContentHtml += `
                <div id="impactCapabilityReverseViewContent" class="sub-tab-content ${activeClass}" ${firstActive ? 'style="display:block;"' : ''}>
                    <div class="relationships-hierarchy">
                        <div class="hierarchy-header">
                            <div class="hierarchy-title">${window.I18n ? window.I18n.t('legalEntity.impact.headers.capability') : 'CAPABILITY'}</div>
                            <div class="hierarchy-actions">
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-list"></i> ${window.I18n ? window.I18n.t('legalEntity.buttons.list') : 'List'}
                                </button>
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-search-plus"></i> ${window.I18n ? window.I18n.t('legalEntity.buttons.searchAndAdd') : 'Search and Add'}
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
        
        if (hasProcessReverse) {
            const activeClass = firstActive ? 'active' : '';
            const processLabel = window.I18n ? window.I18n.t('legalEntity.impact.tabs.process') : 'Process';
            if (firstActive) subTabsHtml += `<button class="sub-tab active" data-sub-tab="process">${processLabel}</button>`;
            else subTabsHtml += `<button class="sub-tab" data-sub-tab="process">${processLabel}</button>`;
            subTabsContentHtml += `
                <div id="impactProcessReverseViewContent" class="sub-tab-content ${activeClass}" ${firstActive ? 'style="display:block;"' : ''}>
                    <div class="relationships-hierarchy">
                        <div class="hierarchy-header">
                            <div class="hierarchy-title">${window.I18n ? window.I18n.t('legalEntity.impact.headers.process') : 'PROCESS'}</div>
                            <div class="hierarchy-actions">
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-list"></i> ${window.I18n ? window.I18n.t('legalEntity.buttons.list') : 'List'}
                                </button>
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-search-plus"></i> ${window.I18n ? window.I18n.t('legalEntity.buttons.searchAndAdd') : 'Search and Add'}
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
            const policyLabel = window.I18n ? window.I18n.t('legalEntity.impact.tabs.policy') : 'Policy';
            if (firstActive) subTabsHtml += `<button class="sub-tab active" data-sub-tab="policy">${policyLabel}</button>`;
            else subTabsHtml += `<button class="sub-tab" data-sub-tab="policy">${policyLabel}</button>`;
            subTabsContentHtml += `
                <div id="impactPolicyReverseViewContent" class="sub-tab-content ${activeClass}" ${firstActive ? 'style="display:block;"' : ''}>
                    <div class="relationships-hierarchy">
                        <div class="hierarchy-header">
                            <div class="hierarchy-title">${window.I18n ? window.I18n.t('legalEntity.impact.headers.policy') : 'POLICY'}</div>
                            <div class="hierarchy-actions">
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-list"></i> ${window.I18n ? window.I18n.t('legalEntity.buttons.list') : 'List'}
                                </button>
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-search-plus"></i> ${window.I18n ? window.I18n.t('legalEntity.buttons.searchAndAdd') : 'Search and Add'}
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
                        <div class="hierarchy-footer">${policyRelationshipsReverse.length} record${policyRelationshipsReverse.length !== 1 ? 's' : ''}</div>
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
                    display: none;
                }
                .sub-tab-content.active {
                    display: block;
                }
            </style>
            <div class="view-section" style="grid-column:1/-1;">
                <div class="relationships-container">
                    <div class="relationships-sub-tabs">${subTabsHtml}</div>
                    <div class="relationships-content">${subTabsContentHtml}</div>
                </div>
            </div>
        `;
        
        setupImpactViewSubTabs();
    }

    function renderGeographyViewTableBody() {
        if (!geographyRelationships || geographyRelationships.length === 0) {
            const noDataMsg = window.I18n ? window.I18n.t('legalEntity.impact.messages.noGeographyRelationships') : 'No geography relationships found';
            return `<tr><td colspan="2" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">${noDataMsg}</td></tr>`;
        }
        return geographyRelationships.map(rel => {
            const relationTypeName = escapeHtml(rel.relationTypeName || rel.relationType || 'Unnamed Type');
            const geographyName = rel.geographyName || 'Unnamed Geography';
            const geographyId = rel.geographyId;
            const geographyLink = createEntityLink('geography', geographyId, geographyName);
            return `<tr><td>${relationTypeName}</td><td>${geographyLink}</td></tr>`;
        }).join('');
    }
    
    function renderProductReverseViewTableBody() {
        if (!productRelationshipsReverse || productRelationshipsReverse.length === 0) {
            const noDataMsg = window.I18n ? window.I18n.t('legalEntity.impact.messages.noProductRelationships') : 'No product relationships found';
            return `<tr class="reverse-relationship-row"><td colspan="3" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">${noDataMsg}</td></tr>`;
        }
        return productRelationshipsReverse.map(rel => {
            const relationTypeName = escapeHtml(rel.relationTypeReverseName || rel.relationTypeName || 'N/A');
            const productName = rel.productName || 'N/A';
            const productId = rel.productId;
            const productLink = createEntityLink('product', productId, productName);
            const noOwner = window.I18n ? window.I18n.t('legalEntity.impact.columns.noOwner') : 'No owner';
            const ownerName = rel.productOwnerName || noOwner;
            return `<tr class="reverse-relationship-row"><td>${relationTypeName}</td><td><div class="entity-info reverse-relationship-entity"><i class="fas fa-box" style="color: var(--text-secondary, #64748b); margin-right: 0.5rem;"></i><span class="entity-name">${productLink}</span></div></td><td style="text-align: center;"><div class="owner-info reverse-relationship-owner"><span class="owner-name">${escapeHtml(ownerName)}</span></div></td></tr>`;
        }).join('');
    }
    
    function renderSystemReverseViewTableBody() {
        if (!systemRelationshipsReverse || systemRelationshipsReverse.length === 0) {
            const noDataMsg = window.I18n ? window.I18n.t('legalEntity.impact.messages.noSystemRelationships') : 'No system relationships found';
            return `<tr class="reverse-relationship-row"><td colspan="3" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">${noDataMsg}</td></tr>`;
        }
        return systemRelationshipsReverse.map(rel => {
            const relationTypeName = escapeHtml(rel.relationTypeReverseName || rel.relationTypeName || 'N/A');
            const systemName = rel.systemName || rel.systemLongName || 'N/A';
            const systemId = rel.systemId;
            const systemLink = createEntityLink('system', systemId, systemName);
            const noOwner = window.I18n ? window.I18n.t('legalEntity.impact.columns.systemOwner') : 'No owner';
            const ownerName = rel.systemOwnerName || noOwner;
            return `<tr class="reverse-relationship-row"><td>${relationTypeName}</td><td><div class="entity-info reverse-relationship-entity"><i class="fas fa-server" style="color: var(--text-secondary, #64748b); margin-right: 0.5rem;"></i><span class="entity-name">${systemLink}</span></div></td><td style="text-align: center;"><div class="owner-info reverse-relationship-owner"><span class="owner-name">${escapeHtml(ownerName)}</span></div></td></tr>`;
        }).join('');
    }
    
    function renderDatasetReverseViewTableBody() {
        if (!datasetRelationshipsReverse || datasetRelationshipsReverse.length === 0) {
            const noDataMsg = window.I18n ? window.I18n.t('legalEntity.impact.messages.noDatasetRelationships') : 'No dataset relationships found';
            return `<tr class="reverse-relationship-row"><td colspan="3" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">${noDataMsg}</td></tr>`;
        }
        return datasetRelationshipsReverse.map(rel => {
            const relationTypeName = escapeHtml(rel.relationTypeReverseName || rel.relationTypeName || 'N/A');
            const datasetName = rel.datasetName || 'N/A';
            const datasetId = rel.datasetId;
            const datasetLink = createEntityLink('dataset', datasetId, datasetName);
            const noOwner = window.I18n ? window.I18n.t('legalEntity.impact.columns.datasetOwner') : 'No owner';
            const ownerName = rel.datasetOwnerName || noOwner;
            return `<tr class="reverse-relationship-row"><td>${relationTypeName}</td><td><div class="entity-info reverse-relationship-entity"><i class="fas fa-database" style="color: var(--text-secondary, #64748b); margin-right: 0.5rem;"></i><span class="entity-name">${datasetLink}</span></div></td><td style="text-align: center;"><div class="owner-info reverse-relationship-owner"><span class="owner-name">${escapeHtml(ownerName)}</span></div></td></tr>`;
        }).join('');
    }
    
    function renderProjectReverseViewTableBody() {
        if (!projectRelationshipsReverse || projectRelationshipsReverse.length === 0) {
            const noDataMsg = window.I18n ? window.I18n.t('legalEntity.impact.messages.noProjectRelationships') : 'No project relationships found';
            return `<tr class="reverse-relationship-row"><td colspan="3" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">${noDataMsg}</td></tr>`;
        }
        return projectRelationshipsReverse.map(rel => {
            const relationTypeName = escapeHtml(rel.relationTypeReverseName || rel.relationTypeName || 'N/A');
            const projectName = rel.projectName || 'N/A';
            const projectId = rel.projectId;
            const projectLink = createEntityLink('project', projectId, projectName);
            const noOwner = window.I18n ? window.I18n.t('legalEntity.impact.columns.projectOwner') : 'No owner';
            const ownerName = rel.projectOwnerName || rel.ownerName || noOwner;
            return `<tr class="reverse-relationship-row"><td>${relationTypeName}</td><td><div class="entity-info reverse-relationship-entity"><i class="fas fa-project-diagram" style="color: var(--text-secondary, #64748b); margin-right: 0.5rem;"></i><span class="entity-name">${projectLink}</span></div></td><td style="text-align: center;"><div class="owner-info reverse-relationship-owner"><span class="owner-name">${escapeHtml(ownerName)}</span></div></td></tr>`;
        }).join('');
    }
    
    function renderCapabilityReverseViewTableBody() {
        if (!capabilityRelationshipsReverse || capabilityRelationshipsReverse.length === 0) {
            const noDataMsg = window.I18n ? window.I18n.t('legalEntity.impact.messages.noCapabilityRelationships') : 'No capability relationships found';
            return `<tr class="reverse-relationship-row"><td colspan="3" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">${noDataMsg}</td></tr>`;
        }
        return capabilityRelationshipsReverse.map(rel => {
            const relationTypeName = escapeHtml(rel.relationTypeReverseName || rel.relationTypeName || 'N/A');
            const capabilityName = rel.capabilityName || 'N/A';
            const capabilityRefNumber = rel.capabilityRefNumber || '';
            const displayName = capabilityRefNumber ? `${capabilityName} (${capabilityRefNumber})` : capabilityName;
            const capabilityId = rel.capabilityId;
            const capabilityLink = createEntityLink('capability', capabilityId, displayName);
            const noOwner = window.I18n ? window.I18n.t('legalEntity.impact.columns.capabilityOwner') : 'No owner';
            const ownerName = rel.capabilityOwnerName || noOwner;
            return `<tr class="reverse-relationship-row"><td>${relationTypeName}</td><td><div class="entity-info reverse-relationship-entity"><i class="fas fa-cogs" style="color: var(--text-secondary, #64748b); margin-right: 0.5rem;"></i><span class="entity-name">${capabilityLink}</span></div></td><td style="text-align: center;"><div class="owner-info reverse-relationship-owner"><span class="owner-name">${escapeHtml(ownerName)}</span></div></td></tr>`;
        }).join('');
    }
    
    function renderProcessReverseViewTableBody() {
        if (!processRelationshipsReverse || processRelationshipsReverse.length === 0) {
            const noDataMsg = window.I18n ? window.I18n.t('legalEntity.impact.messages.noProcessRelationships') : 'No process relationships found';
            return `<tr class="reverse-relationship-row"><td colspan="3" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">${noDataMsg}</td></tr>`;
        }
        return processRelationshipsReverse.map(rel => {
            const relationTypeName = escapeHtml(rel.relationTypeReverseName || rel.relationTypeName || 'N/A');
            const processName = rel.processName || 'N/A';
            const processRefNumber = rel.processRefNumber || '';
            const displayName = processRefNumber ? `${processName} (${processRefNumber})` : processName;
            const processId = rel.processId;
            const processLink = createEntityLink('process', processId, displayName);
            const noOwner = window.I18n ? window.I18n.t('legalEntity.impact.columns.processOwner') : 'No owner';
            const ownerName = rel.processOwnerName || noOwner;
            return `<tr class="reverse-relationship-row"><td>${relationTypeName}</td><td><div class="entity-info reverse-relationship-entity"><i class="fas fa-cogs" style="color: var(--text-secondary, #64748b); margin-right: 0.5rem;"></i><span class="entity-name">${processLink}</span></div></td><td style="text-align: center;"><div class="owner-info reverse-relationship-owner"><span class="owner-name">${escapeHtml(ownerName)}</span></div></td></tr>`;
        }).join('');
    }
    
    function renderPolicyReverseViewTableBody() {
        if (!policyRelationshipsReverse || policyRelationshipsReverse.length === 0) {
            const noDataMsg = window.I18n ? window.I18n.t('legalEntity.impact.messages.noPolicyRelationships') : 'No policy relationships found';
            return `<tr class="reverse-relationship-row"><td colspan="3" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">${noDataMsg}</td></tr>`;
        }
        return policyRelationshipsReverse.map(rel => {
            const relationTypeName = escapeHtml(rel.relationTypeReverseName || rel.relationTypeName || 'N/A');
            const policyName = rel.policyName || 'N/A';
            const policyId = rel.policyId;
            const policyLink = createEntityLink('policy', policyId, policyName);
            const noOwner = window.I18n ? window.I18n.t('legalEntity.impact.columns.policyOwner') : 'No owner';
            const ownerName = rel.policyOwnerName || noOwner;
            return `<tr class="reverse-relationship-row"><td>${relationTypeName}</td><td><div class="entity-info reverse-relationship-entity"><i class="fas fa-file-contract" style="color: var(--text-secondary, #64748b); margin-right: 0.5rem;"></i><span class="entity-name">${policyLink}</span></div></td><td style="text-align: center;"><div class="owner-info reverse-relationship-owner"><span class="owner-name">${escapeHtml(ownerName)}</span></div></td></tr>`;
        }).join('');
    }
    
    function setupImpactViewSubTabs() {
        const subTabs = document.querySelectorAll('#legalEntityImpactContainer .sub-tab');
        subTabs.forEach(function(subTab) {
            subTab.addEventListener('click', function(e) {
                e.preventDefault();
                subTabs.forEach(function(t) { t.classList.remove('active'); });
                this.classList.add('active');
                const subTabName = this.getAttribute('data-sub-tab');
                const subTabContents = document.querySelectorAll('#legalEntityImpactContainer .sub-tab-content');
                subTabContents.forEach(function(content) {
                    content.classList.remove('active');
                    content.style.display = 'none';
                });
                let targetSubTab;
                if (subTabName === 'geography') {
                    targetSubTab = document.getElementById('impactGeographyViewContent');
                } else if (subTabName === 'product') {
                    targetSubTab = document.getElementById('impactProductReverseViewContent');
                } else if (subTabName === 'system') {
                    targetSubTab = document.getElementById('impactSystemReverseViewContent');
                } else if (subTabName === 'dataset') {
                    targetSubTab = document.getElementById('impactDatasetReverseViewContent');
                } else if (subTabName === 'project') {
                    targetSubTab = document.getElementById('impactProjectReverseViewContent');
                } else if (subTabName === 'capability') {
                    targetSubTab = document.getElementById('impactCapabilityReverseViewContent');
                } else if (subTabName === 'process') {
                    targetSubTab = document.getElementById('impactProcessReverseViewContent');
                } else if (subTabName === 'policy') {
                    targetSubTab = document.getElementById('impactPolicyReverseViewContent');
                }
                if (targetSubTab) {
                    targetSubTab.classList.add('active');
                    targetSubTab.style.display = 'block';
                }
            });
        });
    }

    // Export function to global scope
    window.loadLegalImpact = loadLegalImpact;
    
    console.log('Legal Impact View function exported to window');
})();

