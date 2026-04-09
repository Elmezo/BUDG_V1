// Business Area Impact View JavaScript - Read-only display for Glossary, System, and Process sub-tabs
(function() {
    let currentBusinessAreaId = null;
    let glossaryRelationships = [];
    let systemRelationships = [];
    let processRelationships = [];

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
            case 'glossary':
                url = `/view/glossary/${encodeURIComponent(id)}`;
                break;
            case 'system':
                url = `/view/system/${encodeURIComponent(id)}`;
                break;
            case 'process':
                url = `/view/process/${encodeURIComponent(id)}`;
                break;
            case 'product':
                url = `/view/product/${encodeURIComponent(id)}`;
                break;
            case 'capability':
                url = `/view/capability/${encodeURIComponent(id)}`;
                break;
            case 'project':
                url = `/view/project/${encodeURIComponent(id)}`;
                break;
            case 'policy':
                url = `/view/policy/${encodeURIComponent(id)}`;
                break;
            default:
                return escapeHtml(name);
        }
        
        const viewText = window.I18n?.t('businessAreaImpact.messages.view') || 'View';
        return `<a href="${url}" class="entity-link" style="color: var(--secondary-color, #248567); text-decoration: none;" title="${viewText} ${escapeHtml(name)}">${escapeHtml(name)}</a>`;
    }

    // Initialize Impact view
    function loadBusinessAreaImpact(businessAreaId) {
        currentBusinessAreaId = businessAreaId;
        console.log('Loading Business Area Impact for business area:', businessAreaId);
        loadImpactViewData();
    }

    // Reverse relationship arrays
    let productRelationshipsReverse = [];
    let capabilityRelationshipsReverse = [];
    let projectRelationshipsReverse = [];
    let policyRelationshipsReverse = [];
    
    // Load all Impact view data
    async function loadImpactViewData() {
        try {
            // Load relationship data (forward and reverse)
            await Promise.all([
                loadGlossaryRelationshipsView(),
                loadSystemRelationshipsView(),
                loadProcessRelationshipsView(),
                loadProductRelationshipsReverse(),
                loadCapabilityRelationshipsReverse(),
                loadProjectRelationshipsReverse(),
                loadPolicyRelationshipsReverse()
            ]);

            // Render the view
            renderImpactView();

        } catch (error) {
            console.error('Error loading Impact view data:', error);
        }
    }
    
    // Load reverse relationships
    async function loadProductRelationshipsReverse() {
        try {
            const response = await fetch(`/api/product-impact/businessareas/${currentBusinessAreaId}/products`, {
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
    
    async function loadCapabilityRelationshipsReverse() {
        try {
            const response = await fetch(`/api/capability-impact/businessareas/${currentBusinessAreaId}/capabilities`, {
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
    
    async function loadProjectRelationshipsReverse() {
        try {
            const response = await fetch(`/api/project-impact/businessareas/${currentBusinessAreaId}/projects`, {
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
            const response = await fetch(`/api/policy-impact/businessareas/${currentBusinessAreaId}/policies`, {
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

    // Load glossary relationships for view
    async function loadGlossaryRelationshipsView() {
        try {
            console.log('Loading glossary relationships for view, business area:', currentBusinessAreaId);

            const response = await fetch(`/api/businessarea-impact/${currentBusinessAreaId}/glossaries`, {
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
            console.log('Glossary relationships view API response:', data);

            glossaryRelationships = Array.isArray(data) ? data : [];

        } catch (error) {
            console.error('Error loading glossary relationships for view:', error);
            glossaryRelationships = [];
        }
    }

    // Load system relationships for view
    async function loadSystemRelationshipsView() {
        try {
            console.log('Loading system relationships for view, business area:', currentBusinessAreaId);

            const response = await fetch(`/api/businessarea-impact/${currentBusinessAreaId}/systems`, {
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
            console.log('System relationships view API response:', data);

            systemRelationships = Array.isArray(data) ? data : [];

        } catch (error) {
            console.error('Error loading system relationships for view:', error);
            systemRelationships = [];
        }
    }

    // Load process relationships for view
    async function loadProcessRelationshipsView() {
        try {
            console.log('Loading process relationships for view, business area:', currentBusinessAreaId);

            const response = await fetch(`/api/businessarea-impact/${currentBusinessAreaId}/processes`, {
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
            console.log('Process relationships view API response:', data);

            processRelationships = Array.isArray(data) ? data : [];

        } catch (error) {
            console.error('Error loading process relationships for view:', error);
            processRelationships = [];
        }
    }

    // Render Impact view with sub-tabs
    function renderImpactView() {
        const container = document.getElementById('businessAreaImpactContainer');
        if (!container) {
            console.error('businessAreaImpactContainer not found');
            return;
        }

        // Determine which sub-tabs to show based on data availability
        const hasGlossaryData = glossaryRelationships.length > 0;
        const hasSystemData = systemRelationships.length > 0;
        const hasProcessData = processRelationships.length > 0;
        const hasProductReverse = productRelationshipsReverse.length > 0;
        const hasCapabilityReverse = capabilityRelationshipsReverse.length > 0;
        const hasProjectReverse = projectRelationshipsReverse.length > 0;
        const hasPolicyReverse = policyRelationshipsReverse.length > 0;

        if (!hasGlossaryData && !hasSystemData && !hasProcessData && !hasProductReverse && !hasCapabilityReverse && !hasProjectReverse && !hasPolicyReverse) {
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

        if (hasGlossaryData) {
            subTabsHtml += '<button class="sub-tab active" data-sub-tab="glossary">Glossary</button>';
            subTabsContentHtml += `
                <!-- Glossary Sub-tab -->
                <div id="impactGlossaryViewContent" class="sub-tab-content active">
                <div class="relationships-hierarchy">
                    <div class="hierarchy-header">
                        <div class="hierarchy-title">GLOSSARY</div>
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
                                    <th><div class="th-content"><span>Name</span><i class="fas fa-sort"></i></div></th>
                                    <th><div class="th-content" style="justify-content: center;"><span>Type</span><i class="fas fa-sort"></i></div></th>
                                    <th><div class="th-content" style="justify-content: center;"><span>Strategic Owner</span><i class="fas fa-sort"></i></div></th>
                                </tr>
                            </thead>
                            <tbody>
                                ${renderGlossaryViewTableBody()}
                            </tbody>
                        </table>
                    </div>
                    <div class="hierarchy-footer">
                        ${glossaryRelationships.length} record${glossaryRelationships.length !== 1 ? 's' : ''}
                    </div>
                </div>
            </div>
            `;
        }

        if (hasSystemData) {
            // Only make system tab active if there's no glossary data
            const activeClass = !hasGlossaryData ? 'active' : '';
            subTabsHtml += `<button class="sub-tab ${activeClass}" data-sub-tab="system">System</button>`;
            subTabsContentHtml += `
                <!-- System Sub-tab -->
                <div id="impactSystemViewContent" class="sub-tab-content ${activeClass}">
                <div class="relationships-hierarchy">
                    <div class="hierarchy-header">
                        <div class="hierarchy-title">SYSTEM</div>
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
                            <tbody>
                                ${renderSystemViewTableBody()}
                            </tbody>
                        </table>
                    </div>
                    <div class="hierarchy-footer">
                        ${systemRelationships.length} record${systemRelationships.length !== 1 ? 's' : ''}
                    </div>
                </div>
            </div>
            `;
        }

        if (hasProcessData) {
            // Only make process tab active if there's no glossary or system data
            const activeClass = !hasGlossaryData && !hasSystemData ? 'active' : '';
            subTabsHtml += `<button class="sub-tab ${activeClass}" data-sub-tab="process">Process</button>`;
            subTabsContentHtml += `
                <!-- Process Sub-tab -->
                <div id="impactProcessViewContent" class="sub-tab-content ${activeClass}">
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
                                    <th><div class="th-content" style="justify-content: center;"><span>Ref.</span><i class="fas fa-sort"></i></div></th>
                                    <th><div class="th-content" style="justify-content: center;"><span>Process Owner</span><i class="fas fa-sort"></i></div></th>
                                </tr>
                            </thead>
                            <tbody>
                                ${renderProcessViewTableBody()}
                            </tbody>
                        </table>
                    </div>
                    <div class="hierarchy-footer">
                        ${processRelationships.length} record${processRelationships.length !== 1 ? 's' : ''}
                    </div>
                </div>
            </div>
            `;
            firstActive = false;
        }
        
        // Add reverse relationship sub-tabs
        if (hasProductReverse) {
            const activeClass = firstActive ? 'active' : '';
            if (firstActive) subTabsHtml += '<button class="sub-tab active" data-sub-tab="product">Product</button>';
            else subTabsHtml += '<button class="sub-tab" data-sub-tab="product">Product</button>';
            subTabsContentHtml += `
                <div id="impactProductReverseViewContent" class="sub-tab-content ${activeClass}" ${firstActive ? 'style="display:block;"' : ''}>
                    <div class="relationships-hierarchy">
                        <div class="hierarchy-header">
                            <div class="hierarchy-title">PRODUCT</div>
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
        
        if (hasCapabilityReverse) {
            const activeClass = firstActive ? 'active' : '';
            if (firstActive) subTabsHtml += '<button class="sub-tab active" data-sub-tab="capability">Capability</button>';
            else subTabsHtml += '<button class="sub-tab" data-sub-tab="capability">Capability</button>';
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
        
        if (hasProjectReverse) {
            const activeClass = firstActive ? 'active' : '';
            if (firstActive) subTabsHtml += '<button class="sub-tab active" data-sub-tab="project">Project</button>';
            else subTabsHtml += '<button class="sub-tab" data-sub-tab="project">Project</button>';
            subTabsContentHtml += `
                <div id="impactProjectReverseViewContent" class="sub-tab-content ${activeClass}" ${firstActive ? 'style="display:block;"' : ''}>
                    <div class="relationships-hierarchy">
                        <div class="hierarchy-header">
                            <div class="hierarchy-title">PROJECT</div>
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
        
        if (hasPolicyReverse) {
            const activeClass = firstActive ? 'active' : '';
            if (firstActive) subTabsHtml += '<button class="sub-tab active" data-sub-tab="policy">Policy</button>';
            else subTabsHtml += '<button class="sub-tab" data-sub-tab="policy">Policy</button>';
            subTabsContentHtml += `
                <div id="impactPolicyReverseViewContent" class="sub-tab-content ${activeClass}" ${firstActive ? 'style="display:block;"' : ''}>
                    <div class="relationships-hierarchy">
                        <div class="hierarchy-header">
                            <div class="hierarchy-title">POLICY</div>
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

    // Render glossary view table body
    function renderGlossaryViewTableBody() {
        if (glossaryRelationships.length === 0) {
            return '<tr><td colspan="4" style="text-align: center; padding: 2rem; color: var(--text-muted, #6b7280);">No glossary relationships found</td></tr>';
        }

        return glossaryRelationships.map(relationship => {
            const glossaryName = relationship.glossaryName || 'N/A';
            const glossaryLink = createEntityLink('glossary', relationship.glossaryId, glossaryName);
            
            return `
                <tr>
                    <td>${escapeHtml(relationship.relationTypeName || 'N/A')}</td>
                    <td>
                        <div class="entity-info">
                            <span class="entity-name">${glossaryLink}</span>
                        </div>
                    </td>
                    <td style="text-align: center;">
                        <div class="entity-info">
                            <span class="entity-name">${escapeHtml(relationship.glossaryTypeName || relationship.glossaryType || 'N/A')}</span>
                        </div>
                    </td>
                    <td style="text-align: center;">
                        <div class="owner-info">
                            <div class="owner-name" style="font-weight: bold; color: #333;">${escapeHtml(relationship.glossaryOwnerName || 'No owner')}</div>
                        </div>
                    </td>
                </tr>
            `;
        }).join('');
    }

    // Render system view table body
    function renderSystemViewTableBody() {
        if (systemRelationships.length === 0) {
            return '<tr><td colspan="3" style="text-align: center; padding: 2rem; color: var(--text-muted, #6b7280);">No system relationships found</td></tr>';
        }

        return systemRelationships.map(relationship => {
            const systemLink = createEntityLink('system', relationship.systemId, relationship.systemName);
            
            return `
                <tr>
                    <td>${escapeHtml(relationship.relationTypeName || 'N/A')}</td>
                    <td>
                        <div class="entity-info">
                            <span class="entity-name">${systemLink}</span>
                        </div>
                    </td>
                    <td style="text-align: center;">
                        <div class="owner-info">
                            <span class="owner-name">${escapeHtml(relationship.systemOwnerName || 'No owner')}</span>
                        </div>
                    </td>
                </tr>
            `;
        }).join('');
    }

    // Render process view table body
    function renderProcessViewTableBody() {
        if (processRelationships.length === 0) {
            return '<tr><td colspan="4" style="text-align: center; padding: 2rem; color: var(--text-muted, #6b7280);">No process relationships found</td></tr>';
        }

        return processRelationships.map(relationship => {
            const processLink = createEntityLink('process', relationship.processId, relationship.processName);
            
            return `
                <tr>
                    <td>${escapeHtml(relationship.relationTypeName || 'N/A')}</td>
                    <td>
                        <div class="entity-info">
                            <span class="entity-name">${processLink}</span>
                        </div>
                    </td>
                    <td style="text-align: center;">
                        <div class="entity-ref">${escapeHtml(relationship.processRefNumber || relationship.processRef || 'N/A')}</div>
                    </td>
                    <td style="text-align: center;">
                        <div class="owner-info">
                            <div class="owner-name" style="font-weight: bold; color: #333;">${escapeHtml(relationship.processOwnerName || 'No owner')}</div>
                        </div>
                    </td>
                </tr>
            `;
        }).join('');
    }

    // Setup Impact view sub-tabs
    function setupImpactViewSubTabs() {
        console.log('Setting up Impact view sub-tabs...');
        
        const subTabs = document.querySelectorAll('#businessAreaImpactContainer .sub-tab');
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
                const subTabContents = document.querySelectorAll('#businessAreaImpactContainer .sub-tab-content');
                subTabContents.forEach(function(content) {
                    content.classList.remove('active');
                    content.style.display = 'none';
                });
                
                // Show selected sub-tab content
                let targetSubTab;
                if (subTabName === 'glossary') {
                    targetSubTab = document.getElementById('impactGlossaryViewContent');
                } else if (subTabName === 'system') {
                    targetSubTab = document.getElementById('impactSystemViewContent');
                } else if (subTabName === 'process') {
                    targetSubTab = document.getElementById('impactProcessViewContent');
                } else if (subTabName === 'product') {
                    targetSubTab = document.getElementById('impactProductReverseViewContent');
                } else if (subTabName === 'capability') {
                    targetSubTab = document.getElementById('impactCapabilityReverseViewContent');
                } else if (subTabName === 'project') {
                    targetSubTab = document.getElementById('impactProjectReverseViewContent');
                } else if (subTabName === 'policy') {
                    targetSubTab = document.getElementById('impactPolicyReverseViewContent');
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

    // Render reverse relationship table bodies
    function renderProductReverseViewTableBody() {
        if (!productRelationshipsReverse || productRelationshipsReverse.length === 0) {
            return '<tr class="reverse-relationship-row"><td colspan="3" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">No product relationships found</td></tr>';
        }
        return productRelationshipsReverse.map(rel => {
            const relationTypeName = escapeHtml(rel.relationTypeReverseName || rel.relationTypeName || 'N/A');
            const productName = rel.productName || 'N/A';
            const productId = rel.productId;
            const productLink = createEntityLink('product', productId, productName);
            const ownerName = rel.productOwnerName || 'No owner';
            return `<tr class="reverse-relationship-row"><td>${relationTypeName}</td><td><div class="entity-info reverse-relationship-entity"><i class="fas fa-box" style="color: var(--text-secondary, #64748b); margin-right: 0.5rem;"></i><span class="entity-name">${productLink}</span></div></td><td style="text-align: center;"><div class="owner-info reverse-relationship-owner"><span class="owner-name">${escapeHtml(ownerName)}</span></div></td></tr>`;
        }).join('');
    }
    
    function renderCapabilityReverseViewTableBody() {
        if (!capabilityRelationshipsReverse || capabilityRelationshipsReverse.length === 0) {
            return '<tr class="reverse-relationship-row"><td colspan="3" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">No capability relationships found</td></tr>';
        }
        return capabilityRelationshipsReverse.map(rel => {
            const relationTypeName = escapeHtml(rel.relationTypeReverseName || rel.relationTypeName || 'N/A');
            const capabilityName = rel.capabilityName || 'N/A';
            const capabilityId = rel.capabilityId;
            const capabilityLink = createEntityLink('capability', capabilityId, capabilityName);
            const ownerName = rel.capabilityOwnerName || rel.ownerName || 'No owner';
            return `<tr class="reverse-relationship-row"><td>${relationTypeName}</td><td><div class="entity-info reverse-relationship-entity"><i class="fas fa-cogs" style="color: var(--text-secondary, #64748b); margin-right: 0.5rem;"></i><span class="entity-name">${capabilityLink}</span></div></td><td style="text-align: center;"><div class="owner-info reverse-relationship-owner"><span class="owner-name">${escapeHtml(ownerName)}</span></div></td></tr>`;
        }).join('');
    }
    
    function renderProjectReverseViewTableBody() {
        if (!projectRelationshipsReverse || projectRelationshipsReverse.length === 0) {
            return '<tr class="reverse-relationship-row"><td colspan="3" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">No project relationships found</td></tr>';
        }
        return projectRelationshipsReverse.map(rel => {
            const relationTypeName = escapeHtml(rel.relationTypeReverseName || rel.relationTypeName || 'N/A');
            const projectName = rel.projectName || 'N/A';
            const projectId = rel.projectId;
            const projectLink = createEntityLink('project', projectId, projectName);
            const ownerName = rel.projectOwnerName || rel.ownerName || 'No owner';
            return `<tr class="reverse-relationship-row"><td>${relationTypeName}</td><td><div class="entity-info reverse-relationship-entity"><i class="fas fa-project-diagram" style="color: var(--text-secondary, #64748b); margin-right: 0.5rem;"></i><span class="entity-name">${projectLink}</span></div></td><td style="text-align: center;"><div class="owner-info reverse-relationship-owner"><span class="owner-name">${escapeHtml(ownerName)}</span></div></td></tr>`;
        }).join('');
    }
    
    function renderPolicyReverseViewTableBody() {
        if (!policyRelationshipsReverse || policyRelationshipsReverse.length === 0) {
            return '<tr class="reverse-relationship-row"><td colspan="3" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">No policy relationships found</td></tr>';
        }
        return policyRelationshipsReverse.map(rel => {
            const relationTypeName = escapeHtml(rel.relationTypeReverseName || rel.relationTypeName || 'N/A');
            const policyName = rel.policyName || 'N/A';
            const policyId = rel.policyId;
            const policyLink = createEntityLink('policy', policyId, policyName);
            const ownerName = rel.policyOwnerName || 'No owner';
            return `<tr class="reverse-relationship-row"><td>${relationTypeName}</td><td><div class="entity-info reverse-relationship-entity"><i class="fas fa-file-contract" style="color: var(--text-secondary, #64748b); margin-right: 0.5rem;"></i><span class="entity-name">${policyLink}</span></div></td><td style="text-align: center;"><div class="owner-info reverse-relationship-owner"><span class="owner-name">${escapeHtml(ownerName)}</span></div></td></tr>`;
        }).join('');
    }

    // Expose function globally for use by business-area.js
    window.loadBusinessAreaImpact = loadBusinessAreaImpact;

})();

