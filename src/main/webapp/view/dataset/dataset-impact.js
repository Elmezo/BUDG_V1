// Dataset Impact View JavaScript - Shows reverse relationships (which entities impact this Dataset)
(function() {
    let currentDatasetId = null;
    let currentViewMode = 'original'; // 'original' | 'changes'

    // Reverse relationship arrays
    // These show which Products, Clients, Legal Entities, Policies, Processes, and Projects impact this Dataset
    let productRelationshipsReverse = [];
    let clientRelationshipsReverse = [];
    let legalRelationshipsReverse = [];
    let policyRelationshipsReverse = [];
    let processRelationshipsReverse = [];
    let projectRelationshipsReverse = [];

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
            case 'product':
                url = `/view/product/${encodeURIComponent(entityId)}`;
                break;
            case 'client':
                url = `/view/client/${encodeURIComponent(entityId)}`;
                break;
            case 'legal':
                url = `/view/LegalEntity/${encodeURIComponent(entityId)}`;
                break;
            case 'policy':
                url = `/view/policy/${encodeURIComponent(entityId)}`;
                break;
            case 'process':
                url = `/view/process/${encodeURIComponent(entityId)}`;
                break;
            case 'project':
                url = `/view/project/${encodeURIComponent(entityId)}`;
                break;
            default:
                return escapeHtml(entityName);
        }
        
        const viewText = window.I18n?.t('datasetImpact.messages.view') || 'View';
        return `<a href="${url}" class="entity-link" style="color: var(--secondary-color, #248567); text-decoration: none;" title="${viewText} ${escapeHtml(entityName)}">${escapeHtml(entityName)}</a>`;
    }

    // Initialize Impact view
    function loadDatasetImpact(datasetId, viewMode = 'original') {
        currentDatasetId = datasetId;
        currentViewMode = viewMode || 'original';
        console.log('Loading Dataset Impact for dataset:', datasetId);
        loadImpactViewData();
    }

    // Load all Impact view data
    async function loadImpactViewData() {
        try {
            console.log('loadImpactViewData: Starting to load impact data for dataset:', currentDatasetId);
            
            // Load reverse relationships
            await Promise.all([
                loadProductRelationshipsReverse(),
                loadClientRelationshipsReverse(),
                loadLegalRelationshipsReverse(),
                loadPolicyRelationshipsReverse(),
                loadProcessRelationshipsReverse(),
                loadProjectRelationshipsReverse()
            ]);

            console.log('loadImpactViewData: Data loaded, rendering view. Product count:', productRelationshipsReverse.length, 'Client count:', clientRelationshipsReverse.length, 'Legal count:', legalRelationshipsReverse.length, 'Policy count:', policyRelationshipsReverse.length, 'Process count:', processRelationshipsReverse.length, 'Project count:', projectRelationshipsReverse.length);

            // Render the view
            renderImpactView();

            console.log('loadImpactViewData: View rendered successfully');

        } catch (error) {
            console.error('Error loading Impact view data:', error);
            const container = document.getElementById('datasetImpactContainer');
            if (container) {
                container.innerHTML = `
                    <div class="view-section" style="grid-column:1/-1; padding: 2rem; text-align: center; color: var(--danger, #dc3545);">
                        <i class="fas fa-exclamation-triangle" style="font-size: 2rem; margin-bottom: 1rem;"></i>
                        <p>${window.I18n?.t('datasetImpact.messages.errorLoadingImpact') || 'Error loading impact data:'} ${error.message}</p>
                    </div>
                `;
            }
        }
    }
    
    // Load reverse relationships - these show which entities impact this Dataset
    // Note: The reverse APIs show which Products, Clients, and Legal Entities are related to this Dataset
    // We use the existing forward relationship APIs which already provide this information
    // The reverse relationships are the same as forward relationships for Dataset
    async function loadProductRelationshipsReverse() {
        try {
            console.log('Loading product relationships for view, dataset:', currentDatasetId);
            const viewParam = currentViewMode === 'changes' ? '?view=changes' : '';
            const url = `/api/dataset-impact/${currentDatasetId}/products${viewParam}`;
            console.log('Fetching from URL:', url);

            const response = await fetch(url, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });
            
            console.log('API response status:', response.status);

            if (!response.ok) {
                const errorText = await response.text();
                console.error('API error response:', errorText);
                productRelationshipsReverse = [];
                return;
            }

            const data = await response.json();
            console.log('Product relationships API response:', data);
            productRelationshipsReverse = Array.isArray(data) ? data : [];
            console.log('Product relationships loaded:', productRelationshipsReverse.length);
            if (productRelationshipsReverse.length > 0) {
                console.log('Sample product relationship:', productRelationshipsReverse[0]);
            }
        } catch (error) {
            console.error('Error loading reverse product relationships:', error);
            productRelationshipsReverse = [];
        }
    }

    async function loadClientRelationshipsReverse() {
        try {
            console.log('Loading client relationships for view, dataset:', currentDatasetId);
            const viewParam = currentViewMode === 'changes' ? '?view=changes' : '';
            const url = `/api/dataset-impact/${currentDatasetId}/clients${viewParam}`;
            console.log('Fetching from URL:', url);

            const response = await fetch(url, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });
            
            console.log('API response status:', response.status);

            if (!response.ok) {
                const errorText = await response.text();
                console.error('API error response:', errorText);
                clientRelationshipsReverse = [];
                return;
            }

            const data = await response.json();
            console.log('Client relationships API response:', data);
            clientRelationshipsReverse = Array.isArray(data) ? data : [];
            console.log('Client relationships loaded:', clientRelationshipsReverse.length);
            if (clientRelationshipsReverse.length > 0) {
                console.log('Sample client relationship:', clientRelationshipsReverse[0]);
            }
        } catch (error) {
            console.error('Error loading reverse client relationships:', error);
            clientRelationshipsReverse = [];
        }
    }

    async function loadLegalRelationshipsReverse() {
        try {
            console.log('Loading legal relationships for view, dataset:', currentDatasetId);
            const viewParam = currentViewMode === 'changes' ? '?view=changes' : '';
            const url = `/api/dataset-impact/${currentDatasetId}/legals${viewParam}`;
            console.log('Fetching from URL:', url);

            const response = await fetch(url, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });
            
            console.log('API response status:', response.status);

            if (!response.ok) {
                const errorText = await response.text();
                console.error('API error response:', errorText);
                legalRelationshipsReverse = [];
                return;
            }

            const data = await response.json();
            console.log('Legal relationships API response:', data);
            legalRelationshipsReverse = Array.isArray(data) ? data : [];
            console.log('Legal relationships loaded:', legalRelationshipsReverse.length);
            if (legalRelationshipsReverse.length > 0) {
                console.log('Sample legal relationship:', legalRelationshipsReverse[0]);
            }
        } catch (error) {
            console.error('Error loading reverse legal relationships:', error);
            legalRelationshipsReverse = [];
        }
    }
    
    async function loadPolicyRelationshipsReverse() {
        try {
            console.log('Loading policy relationships for view, dataset:', currentDatasetId);
            const url = `/api/policy-impact/datasets/${currentDatasetId}/policies`;
            console.log('Fetching from URL:', url);
            
            const response = await fetch(url, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });
            
            console.log('API response status:', response.status);
            
            if (!response.ok) {
                const errorText = await response.text();
                console.error('API error response:', errorText);
                policyRelationshipsReverse = [];
                return;
            }
            
            const data = await response.json();
            console.log('Policy relationships API response:', data);
            policyRelationshipsReverse = Array.isArray(data) ? data : [];
            console.log('Policy relationships loaded:', policyRelationshipsReverse.length);
            if (policyRelationshipsReverse.length > 0) {
                console.log('Sample policy relationship:', policyRelationshipsReverse[0]);
            }
        } catch (error) {
            console.error('Error loading reverse policy relationships:', error);
            policyRelationshipsReverse = [];
        }
    }
    
    async function loadProcessRelationshipsReverse() {
        try {
            console.log('Loading process relationships for view, dataset:', currentDatasetId);
            const url = `/api/process-impact/datasets/${currentDatasetId}/processes`;
            console.log('Fetching from URL:', url);
            
            const response = await fetch(url, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });
            
            console.log('API response status:', response.status);
            
            if (!response.ok) {
                const errorText = await response.text();
                console.error('API error response:', errorText);
                processRelationshipsReverse = [];
                return;
            }
            
            const data = await response.json();
            console.log('Process relationships API response:', data);
            processRelationshipsReverse = Array.isArray(data) ? data : [];
            console.log('Process relationships loaded:', processRelationshipsReverse.length);
            if (processRelationshipsReverse.length > 0) {
                console.log('Sample process relationship:', processRelationshipsReverse[0]);
            }
        } catch (error) {
            console.error('Error loading reverse process relationships:', error);
            processRelationshipsReverse = [];
        }
    }
    
    async function loadProjectRelationshipsReverse() {
        try {
            console.log('Loading project relationships for view, dataset:', currentDatasetId);
            const url = `/api/project-impact/datasets/${currentDatasetId}/projects`;
            console.log('Fetching from URL:', url);
            
            const response = await fetch(url, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });
            
            console.log('API response status:', response.status);
            
            if (!response.ok) {
                const errorText = await response.text();
                console.error('API error response:', errorText);
                projectRelationshipsReverse = [];
                return;
            }
            
            const data = await response.json();
            console.log('Project relationships API response:', data);
            projectRelationshipsReverse = Array.isArray(data) ? data : [];
            console.log('Project relationships loaded:', projectRelationshipsReverse.length);
            if (projectRelationshipsReverse.length > 0) {
                console.log('Sample project relationship:', projectRelationshipsReverse[0]);
            }
        } catch (error) {
            console.error('Error loading reverse project relationships:', error);
            projectRelationshipsReverse = [];
        }
    }

    // Render Impact view with sub-tabs
    function renderImpactView() {
        let container = document.getElementById('datasetImpactContainer');
        if (!container) {
            // Fallback: create the container inside the main content body if needed
            const body = document.querySelector('.content-body');
            if (body) {
                container = document.createElement('div');
                container.id = 'datasetImpactContainer';
                container.className = 'view-section';
                container.style.gridColumn = '1/-1';
                body.appendChild(container);
            } else {
                console.error('datasetImpactContainer not found');
                console.error('Available containers:', document.querySelectorAll('[id*="Container"]'));
                return;
            }
        }
        
        console.log('renderImpactView: Container found, rendering view');

        // Determine which sub-tabs to show based on data availability
        const hasProductReverse = productRelationshipsReverse.length > 0;
        const hasClientReverse = clientRelationshipsReverse.length > 0;
        const hasLegalReverse = legalRelationshipsReverse.length > 0;
        const hasPolicyReverse = policyRelationshipsReverse.length > 0;
        const hasProcessReverse = processRelationshipsReverse.length > 0;
        const hasProjectReverse = projectRelationshipsReverse.length > 0;

        if (!hasProductReverse && !hasClientReverse && !hasLegalReverse && !hasPolicyReverse && !hasProcessReverse && !hasProjectReverse) {
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

        if (hasProductReverse) {
            const activeClass = firstActive ? 'active' : '';
            const productText = window.I18n?.t('datasetImpact.sections.products') || 'Product';
            if (firstActive) subTabsHtml += `<button class="sub-tab active" data-sub-tab="product-reverse">${productText}</button>`;
            else subTabsHtml += `<button class="sub-tab" data-sub-tab="product-reverse">${productText}</button>`;
            subTabsContentHtml += `
                <div id="impactProductReverseViewContent" class="sub-tab-content ${activeClass}" ${firstActive ? 'style="display:block;"' : ''}>
                <div class="relationships-hierarchy">
                    <div class="hierarchy-header">
                        <div class="hierarchy-title">${window.I18n?.t('datasetImpact.sections.products') || 'PRODUCT'}</div>
                        <div class="hierarchy-actions">
                            <button type="button" class="btn btn-secondary">
                                <i class="fas fa-list"></i> List
                            </button>
                            <button type="button" class="btn btn-secondary edit-only-changes">
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
                                    <th><div class="th-content"><span>Product</span><i class="fas fa-sort"></i></div></th>
                                    <th><div class="th-content" style="justify-content: center;"><span>Product Owner</span><i class="fas fa-sort"></i></div></th>
                                </tr>
                            </thead>
                                <tbody>${renderProductReverseViewTableBody()}</tbody>
                        </table>
                    </div>
                        <div class="hierarchy-footer">${productRelationshipsReverse.length} ${productRelationshipsReverse.length !== 1 ? (window.I18n?.t('dataset.messages.records') || 'records') : (window.I18n?.t('dataset.messages.record') || 'record')}</div>
                </div>
            </div>
            `;
            firstActive = false;
        }
        
        if (hasClientReverse) {
            const activeClass = firstActive ? 'active' : '';
            const clientText = window.I18n?.t('datasetImpact.sections.clients') || 'Client';
            if (firstActive) subTabsHtml += `<button class="sub-tab active" data-sub-tab="client-reverse">${clientText}</button>`;
            else subTabsHtml += `<button class="sub-tab" data-sub-tab="client-reverse">${clientText}</button>`;
            subTabsContentHtml += `
                <div id="impactClientReverseViewContent" class="sub-tab-content ${activeClass}" ${firstActive ? 'style="display:block;"' : ''}>
                <div class="relationships-hierarchy">
                    <div class="hierarchy-header">
                        <div class="hierarchy-title">${window.I18n?.t('datasetImpact.sections.clients') || 'CLIENT'}</div>
                        <div class="hierarchy-actions">
                            <button type="button" class="btn btn-secondary">
                                <i class="fas fa-list"></i> List
                            </button>
                            <button type="button" class="btn btn-secondary edit-only-changes">
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
                                <tbody>${renderClientReverseViewTableBody()}</tbody>
                        </table>
                    </div>
                        <div class="hierarchy-footer">${clientRelationshipsReverse.length} ${clientRelationshipsReverse.length !== 1 ? (window.I18n?.t('dataset.messages.records') || 'records') : (window.I18n?.t('dataset.messages.record') || 'record')}</div>
                </div>
            </div>
            `;
            firstActive = false;
        }
        
        if (hasLegalReverse) {
            const activeClass = firstActive ? 'active' : '';
            const legalEntityText = window.I18n?.t('datasetImpact.sections.legalEntities') || 'Legal Entity';
            if (firstActive) subTabsHtml += `<button class="sub-tab active" data-sub-tab="legal-reverse">${legalEntityText}</button>`;
            else subTabsHtml += `<button class="sub-tab" data-sub-tab="legal-reverse">${legalEntityText}</button>`;
            subTabsContentHtml += `
                <div id="impactLegalReverseViewContent" class="sub-tab-content ${activeClass}" ${firstActive ? 'style="display:block;"' : ''}>
                <div class="relationships-hierarchy">
                    <div class="hierarchy-header">
                        <div class="hierarchy-title">${window.I18n?.t('datasetImpact.sections.legalEntities') || 'LEGAL ENTITY'}</div>
                        <div class="hierarchy-actions">
                            <button type="button" class="btn btn-secondary">
                                <i class="fas fa-list"></i> List
                            </button>
                            <button type="button" class="btn btn-secondary edit-only-changes">
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
                                    <th><div class="th-content"><span>Legal Entity</span><i class="fas fa-sort"></i></div></th>
                                    <th><div class="th-content" style="justify-content: center;"><span>Legal Entity Owner</span><i class="fas fa-sort"></i></div></th>
                                </tr>
                            </thead>
                                <tbody>${renderLegalReverseViewTableBody()}</tbody>
                        </table>
                    </div>
                        <div class="hierarchy-footer">${legalRelationshipsReverse.length} ${legalRelationshipsReverse.length !== 1 ? (window.I18n?.t('dataset.messages.records') || 'records') : (window.I18n?.t('dataset.messages.record') || 'record')}</div>
                </div>
            </div>
            `;
            firstActive = false;
        }
        
        if (hasPolicyReverse) {
            const activeClass = firstActive ? 'active' : '';
            const policyText = window.I18n?.t('datasetImpact.sections.policies') || 'Policy';
            if (firstActive) subTabsHtml += `<button class="sub-tab active" data-sub-tab="policy-reverse">${policyText}</button>`;
            else subTabsHtml += `<button class="sub-tab" data-sub-tab="policy-reverse">${policyText}</button>`;
            subTabsContentHtml += `
                <div id="impactPolicyReverseViewContent" class="sub-tab-content ${activeClass}" ${firstActive ? 'style="display:block;"' : ''}>
                <div class="relationships-hierarchy">
                    <div class="hierarchy-header">
                        <div class="hierarchy-title">${window.I18n?.t('datasetImpact.sections.policies') || 'POLICY'}</div>
                        <div class="hierarchy-actions">
                            <button type="button" class="btn btn-secondary">
                                <i class="fas fa-list"></i> List
                            </button>
                            <button type="button" class="btn btn-secondary edit-only-changes">
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
        
        if (hasProcessReverse) {
            const activeClass = firstActive ? 'active' : '';
            const processText = window.I18n?.t('datasetImpact.sections.processes') || 'Process';
            if (firstActive) subTabsHtml += `<button class="sub-tab active" data-sub-tab="process-reverse">${processText}</button>`;
            else subTabsHtml += `<button class="sub-tab" data-sub-tab="process-reverse">${processText}</button>`;
            subTabsContentHtml += `
                <div id="impactProcessReverseViewContent" class="sub-tab-content ${activeClass}" ${firstActive ? 'style="display:block;"' : ''}>
                <div class="relationships-hierarchy">
                    <div class="hierarchy-header">
                        <div class="hierarchy-title">${window.I18n?.t('datasetImpact.sections.processes') || 'PROCESS'}</div>
                        <div class="hierarchy-actions">
                            <button type="button" class="btn btn-secondary">
                                <i class="fas fa-list"></i> List
                            </button>
                            <button type="button" class="btn btn-secondary edit-only-changes">
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
                        <div class="hierarchy-footer">${processRelationshipsReverse.length} ${processRelationshipsReverse.length !== 1 ? (window.I18n?.t('dataset.messages.records') || 'records') : (window.I18n?.t('dataset.messages.record') || 'record')}</div>
                </div>
            </div>
            `;
            firstActive = false;
        }
        
        if (hasProjectReverse) {
            const activeClass = firstActive ? 'active' : '';
            const projectText = window.I18n?.t('datasetImpact.sections.projects') || 'Project';
            if (firstActive) subTabsHtml += `<button class="sub-tab active" data-sub-tab="project-reverse">${projectText}</button>`;
            else subTabsHtml += `<button class="sub-tab" data-sub-tab="project-reverse">${projectText}</button>`;
            subTabsContentHtml += `
                <div id="impactProjectReverseViewContent" class="sub-tab-content ${activeClass}" ${firstActive ? 'style="display:block;"' : ''}>
                <div class="relationships-hierarchy">
                    <div class="hierarchy-header">
                        <div class="hierarchy-title">${window.I18n?.t('datasetImpact.sections.projects') || 'PROJECT'}</div>
                        <div class="hierarchy-actions">
                            <button type="button" class="btn btn-secondary">
                                <i class="fas fa-list"></i> List
                            </button>
                            <button type="button" class="btn btn-secondary edit-only-changes">
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
                                    <th><div class="th-content" style="justify-content: center;"><span>Project Owner</span><i class="fas fa-sort"></i></div></th>
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

    function renderProductReverseViewTableBody() {
        if (!productRelationshipsReverse || productRelationshipsReverse.length === 0) {
            const noProductRelationshipsText = window.I18n?.t('message.noProductRelationships') || 'No product relationships found';
            return `<tr class="reverse-relationship-row"><td colspan="3" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">${noProductRelationshipsText}</td></tr>`;
        }
        return productRelationshipsReverse.map(rel => {
            // Use reverse name if available, otherwise use regular relation type name
            const relationTypeDisplay = escapeHtml(rel.relationTypeReverseName || rel.relationTypeName || 'N/A');
            const productName = rel.productName || 'N/A';
            const productId = rel.productId;
            const productLink = createEntityLink('product', productId, productName);
            const ownerName = rel.productOwnerName || rel.ownerName || 'No owner';
            return `<tr class="reverse-relationship-row"><td>${relationTypeDisplay}</td><td><div class="entity-info reverse-relationship-entity"><i class="fas fa-box" style="color: var(--text-secondary, #64748b); margin-right: 0.5rem;"></i><span class="entity-name">${productLink}</span></div></td><td style="text-align: center;"><div class="owner-info reverse-relationship-owner"><span class="owner-name">${escapeHtml(ownerName)}</span></div></td></tr>`;
        }).join('');
    }

    function renderClientReverseViewTableBody() {
        if (!clientRelationshipsReverse || clientRelationshipsReverse.length === 0) {
            const noClientRelationshipsText = window.I18n?.t('message.noClientRelationships') || 'No client relationships found';
            return `<tr class="reverse-relationship-row"><td colspan="3" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">${noClientRelationshipsText}</td></tr>`;
        }
        return clientRelationshipsReverse.map(rel => {
            // Use reverse name if available, otherwise use regular relation type name
            const relationTypeDisplay = escapeHtml(rel.relationTypeReverseName || rel.relationTypeName || 'N/A');
            const clientName = rel.clientName || 'N/A';
            const clientId = rel.clientId;
            const clientLink = createEntityLink('client', clientId, clientName);
            const ownerName = rel.clientOwnerName || rel.ownerName || 'No owner';
            return `<tr class="reverse-relationship-row"><td>${relationTypeDisplay}</td><td><div class="entity-info reverse-relationship-entity"><i class="fas fa-user-tie" style="color: var(--text-secondary, #64748b); margin-right: 0.5rem;"></i><span class="entity-name">${clientLink}</span></div></td><td style="text-align: center;"><div class="owner-info reverse-relationship-owner"><span class="owner-name">${escapeHtml(ownerName)}</span></div></td></tr>`;
        }).join('');
    }

    function renderLegalReverseViewTableBody() {
        if (!legalRelationshipsReverse || legalRelationshipsReverse.length === 0) {
            const noLegalRelationshipsText = window.I18n?.t('message.noLegalEntityRelationships') || 'No legal entity relationships found';
            return `<tr class="reverse-relationship-row"><td colspan="3" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">${noLegalRelationshipsText}</td></tr>`;
        }
        return legalRelationshipsReverse.map(rel => {
            // Use reverse name if available, otherwise use regular relation type name
            const relationTypeDisplay = escapeHtml(rel.relationTypeReverseName || rel.relationTypeName || 'N/A');
            // Legal entity uses legalLongName or legalShortName, not legalEntityName
            const legalName = rel.legalLongName || rel.legalShortName || 'N/A';
            const legalId = rel.legalId;
            const legalLink = createEntityLink('legal', legalId, legalName);
            const ownerName = rel.legalOwnerName || rel.ownerName || 'No owner';
            return `<tr class="reverse-relationship-row"><td>${relationTypeDisplay}</td><td><div class="entity-info reverse-relationship-entity"><i class="fas fa-building" style="color: var(--text-secondary, #64748b); margin-right: 0.5rem;"></i><span class="entity-name">${legalLink}</span></div></td><td style="text-align: center;"><div class="owner-info reverse-relationship-owner"><span class="owner-name">${escapeHtml(ownerName)}</span></div></td></tr>`;
        }).join('');
    }
    
    function renderPolicyReverseViewTableBody() {
        if (!policyRelationshipsReverse || policyRelationshipsReverse.length === 0) {
            const noPolicyRelationshipsText = window.I18n?.t('message.noPolicyRelationships') || 'No policy relationships found';
            return `<tr class="reverse-relationship-row"><td colspan="3" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">${noPolicyRelationshipsText}</td></tr>`;
        }
        return policyRelationshipsReverse.map(rel => {
            // Use reverse name if available, otherwise use regular relation type name
            const relationTypeDisplay = escapeHtml(rel.relationTypeReverseName || rel.relationTypeName || 'N/A');
            const policyName = rel.policyName || 'N/A';
            const policyId = rel.policyId;
            const policyLink = createEntityLink('policy', policyId, policyName);
            const ownerName = rel.policyOwnerName || rel.ownerName || 'No owner';
            return `<tr class="reverse-relationship-row"><td>${relationTypeDisplay}</td><td><div class="entity-info reverse-relationship-entity"><i class="fas fa-file-contract" style="color: var(--text-secondary, #64748b); margin-right: 0.5rem;"></i><span class="entity-name">${policyLink}</span></div></td><td style="text-align: center;"><div class="owner-info reverse-relationship-owner"><span class="owner-name">${escapeHtml(ownerName)}</span></div></td></tr>`;
        }).join('');
    }
    
    function renderProcessReverseViewTableBody() {
        if (!processRelationshipsReverse || processRelationshipsReverse.length === 0) {
            const noProcessRelationshipsText = window.I18n?.t('message.noProcessRelationships') || 'No process relationships found';
            return `<tr class="reverse-relationship-row"><td colspan="3" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">${noProcessRelationshipsText}</td></tr>`;
        }
        return processRelationshipsReverse.map(rel => {
            // Use reverse name if available, otherwise use regular relation type name
            const relationTypeDisplay = escapeHtml(rel.relationTypeReverseName || rel.relationTypeName || 'N/A');
            const processName = rel.processName || 'N/A';
            const processId = rel.processId;
            const processLink = createEntityLink('process', processId, processName);
            const ownerName = rel.processOwnerName || rel.ownerName || 'No owner';
            return `<tr class="reverse-relationship-row"><td>${relationTypeDisplay}</td><td><div class="entity-info reverse-relationship-entity"><i class="fas fa-project-diagram" style="color: var(--text-secondary, #64748b); margin-right: 0.5rem;"></i><span class="entity-name">${processLink}</span></div></td><td style="text-align: center;"><div class="owner-info reverse-relationship-owner"><span class="owner-name">${escapeHtml(ownerName)}</span></div></td></tr>`;
        }).join('');
    }
    
    function renderProjectReverseViewTableBody() {
        if (!projectRelationshipsReverse || projectRelationshipsReverse.length === 0) {
            const noProjectRelationshipsText = window.I18n?.t('message.noProjectRelationships') || 'No project relationships found';
            return `<tr class="reverse-relationship-row"><td colspan="3" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">${noProjectRelationshipsText}</td></tr>`;
        }
        return projectRelationshipsReverse.map(rel => {
            // Use reverse name if available, otherwise use regular relation type name
            const relationTypeDisplay = escapeHtml(rel.relationTypeReverseName || rel.relationTypeName || 'N/A');
            const projectName = rel.projectName || 'N/A';
            const projectId = rel.projectId;
            const projectLink = createEntityLink('project', projectId, projectName);
            const ownerName = rel.projectOwnerName || rel.ownerName || 'No owner';
            return `<tr class="reverse-relationship-row"><td>${relationTypeDisplay}</td><td><div class="entity-info reverse-relationship-entity"><i class="fas fa-project-diagram" style="color: var(--text-secondary, #64748b); margin-right: 0.5rem;"></i><span class="entity-name">${projectLink}</span></div></td><td style="text-align: center;"><div class="owner-info reverse-relationship-owner"><span class="owner-name">${escapeHtml(ownerName)}</span></div></td></tr>`;
        }).join('');
    }

    // Setup Impact view sub-tabs
    function setupImpactViewSubTabs() {
        console.log('Setting up Impact view sub-tabs...');
        
        const subTabs = document.querySelectorAll('#datasetImpactContainer .sub-tab');
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
                const subTabContents = document.querySelectorAll('#datasetImpactContainer .sub-tab-content');
                subTabContents.forEach(function(content) {
                    content.classList.remove('active');
                    content.style.display = 'none';
                });
                
                // Show selected sub-tab content
                let targetSubTab;
                if (subTabName === 'product-reverse') {
                    targetSubTab = document.getElementById('impactProductReverseViewContent');
                } else if (subTabName === 'client-reverse') {
                    targetSubTab = document.getElementById('impactClientReverseViewContent');
                } else if (subTabName === 'legal-reverse') {
                    targetSubTab = document.getElementById('impactLegalReverseViewContent');
                } else if (subTabName === 'policy-reverse') {
                    targetSubTab = document.getElementById('impactPolicyReverseViewContent');
                } else if (subTabName === 'process-reverse') {
                    targetSubTab = document.getElementById('impactProcessReverseViewContent');
                } else if (subTabName === 'project-reverse') {
                    targetSubTab = document.getElementById('impactProjectReverseViewContent');
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

    // Expose function globally for use by dataset.js
    window.loadDatasetImpact = loadDatasetImpact;

})();
