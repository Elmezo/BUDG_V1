// Regulation Impact View JavaScript - Read-only display for 4 sub-tabs
(function() {
    let currentRegulationId = null;
    let productRelationships = [];
    let policyRelationships = [];
    let projectRelationships = [];
    let regulatoryThemeRelationships = [];

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
            case 'product':
                url = `/view/product/${encodeURIComponent(id)}`;
                break;
            case 'policy':
                url = `/view/policy/${encodeURIComponent(id)}`;
                break;
            case 'project':
                url = `/view/project/${encodeURIComponent(id)}`;
                break;
            case 'regulatorytheme':
                url = `/view/regulatory-theme/${encodeURIComponent(id)}`;
                break;
            default:
                return escapeHtml(name);
        }
        
        const viewText = window.I18n?.t('regulationImpact.messages.view') || 'View';
        return `<a href="${url}" class="entity-link" style="color: var(--secondary-color, #248567); text-decoration: none;" title="${viewText} ${escapeHtml(name)}">${escapeHtml(name)}</a>`;
    }

    // Initialize Impact view
    function loadRegulationImpact(regulationId) {
        currentRegulationId = regulationId;
        console.log('Loading Regulation Impact for regulation:', regulationId);
        loadImpactViewData();
    }

    // Load all Impact view data
    async function loadImpactViewData() {
        try {
            await Promise.all([
                loadProductRelationshipsView(),
                loadPolicyRelationshipsView(),
                loadProjectRelationshipsView(),
                loadRegulatoryThemeRelationshipsView()
            ]);

            renderImpactView();
        } catch (error) {
            console.error('Error loading Impact view data:', error);
        }
    }

    // ===== LOAD RELATIONSHIPS FOR VIEW =====
    
    async function loadProductRelationshipsView() {
        try {
            const response = await fetch(`/api/regulation-impact/${currentRegulationId}/products`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });
            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
            const data = await response.json();
            productRelationships = Array.isArray(data) ? data : [];
        } catch (error) {
            console.error('Error loading product relationships for view:', error);
            productRelationships = [];
        }
    }
    
    async function loadPolicyRelationshipsView() {
        try {
            const response = await fetch(`/api/regulation-impact/${currentRegulationId}/policies`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });
            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
            const data = await response.json();
            policyRelationships = Array.isArray(data) ? data : [];
        } catch (error) {
            console.error('Error loading policy relationships for view:', error);
            policyRelationships = [];
        }
    }
    
    async function loadProjectRelationshipsView() {
        try {
            const response = await fetch(`/api/regulation-impact/${currentRegulationId}/projects`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });
            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
            const data = await response.json();
            projectRelationships = Array.isArray(data) ? data : [];
        } catch (error) {
            console.error('Error loading project relationships for view:', error);
            projectRelationships = [];
        }
    }
    
    async function loadRegulatoryThemeRelationshipsView() {
        try {
            const response = await fetch(`/api/regulation-impact/${currentRegulationId}/regulatorythemes`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });
            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
            const data = await response.json();
            regulatoryThemeRelationships = Array.isArray(data) ? data : [];
        } catch (error) {
            console.error('Error loading regulatory theme relationships for view:', error);
            regulatoryThemeRelationships = [];
        }
    }

    // ===== RENDER IMPACT VIEW =====
    
    function renderImpactView() {
        const container = document.getElementById('regulationImpactContainer');
        if (!container) {
            console.error('Regulation Impact container not found');
            return;
        }
        
        // Determine which sub-tabs to show based on data availability
        const hasProductData = productRelationships.length > 0;
        const hasPolicyData = policyRelationships.length > 0;
        const hasProjectData = projectRelationships.length > 0;
        const hasRegulatoryThemeData = regulatoryThemeRelationships.length > 0;
        
        if (!hasProductData && !hasPolicyData && !hasProjectData && !hasRegulatoryThemeData) {
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
        
        if (hasProductData) {
            subTabsHtml += '<button class="sub-tab active" data-sub-tab="product">Product</button>';
            subTabsContentHtml += `
                <div id="impactProductContent" class="sub-tab-content active">
                    ${renderProductViewTableBody()}
                </div>
            `;
        }
        
        if (hasPolicyData) {
            // Only make policy tab active if there's no product data
            const activeClass = !hasProductData ? 'active' : '';
            subTabsHtml += `<button class="sub-tab ${activeClass}" data-sub-tab="policy">Policy</button>`;
            subTabsContentHtml += `
                <div id="impactPolicyContent" class="sub-tab-content ${activeClass}">
                    ${renderPolicyViewTableBody()}
                </div>
            `;
        }
        
        if (hasProjectData) {
            // Only make project tab active if there's no product or policy data
            const activeClass = !hasProductData && !hasPolicyData ? 'active' : '';
            subTabsHtml += `<button class="sub-tab ${activeClass}" data-sub-tab="project">Project</button>`;
            subTabsContentHtml += `
                <div id="impactProjectContent" class="sub-tab-content ${activeClass}">
                    ${renderProjectViewTableBody()}
                </div>
            `;
        }
        
        if (hasRegulatoryThemeData) {
            // Only make regulatory theme tab active if there's no product, policy, or project data
            const activeClass = !hasProductData && !hasPolicyData && !hasProjectData ? 'active' : '';
            subTabsHtml += `<button class="sub-tab ${activeClass}" data-sub-tab="regulatorytheme">Regulatory Theme</button>`;
            subTabsContentHtml += `
                <div id="impactRegulatoryThemeContent" class="sub-tab-content ${activeClass}">
                    ${renderRegulatoryThemeViewTableBody()}
                </div>
            `;
        }
        
        let html = `
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
        
        container.innerHTML = html;
        setupImpactViewSubTabs();
    }

    // ===== RENDER TABLE BODIES =====
    
    function renderProductViewTableBody() {
        if (productRelationships.length === 0) {
            return '<div class="empty-message">No product relationships found</div>';
        }
        
        return `
            <div class="relationships-hierarchy">
                <div class="hierarchy-header">
                    <div class="hierarchy-title">PRODUCT</div>
                </div>
                <div class="data-table-wrapper">
                    <table class="data-table">
                        <thead>
                            <tr>
                                <th><div class="th-content"><span>Relationship Type</span></div></th>
                                <th><div class="th-content"><span>Product</span></div></th>
                                <th><div class="th-content"><span>Ref.</span></div></th>
                                <th><div class="th-content"><span>Product Owner</span></div></th>
                            </tr>
                        </thead>
                        <tbody>
                            ${productRelationships.map(rel => `
                                <tr>
                                    <td>${escapeHtml(rel.relationTypeName || 'N/A')}</td>
                                    <td>${createEntityLink('product', rel.productId, rel.productName)}</td>
                                    <td style="text-align: center;">${escapeHtml(rel.productRefNumber || '')}</td>
                                    <td style="text-align: center;">${escapeHtml(rel.productOwnerName || 'No owner')}</td>
                                </tr>
                            `).join('')}
                        </tbody>
                    </table>
                </div>
                <div class="hierarchy-footer">${productRelationships.length} record${productRelationships.length !== 1 ? 's' : ''}</div>
            </div>
        `;
    }
    
    function renderPolicyViewTableBody() {
        if (policyRelationships.length === 0) {
            return '<div class="empty-message">No policy relationships found</div>';
        }
        
        return `
            <div class="relationships-hierarchy">
                <div class="hierarchy-header">
                    <div class="hierarchy-title">POLICY</div>
                </div>
                <div class="data-table-wrapper">
                    <table class="data-table">
                        <thead>
                            <tr>
                                <th><div class="th-content"><span>Relationship Type</span></div></th>
                                <th><div class="th-content"><span>Policy</span></div></th>
                                <th><div class="th-content"><span>Ref.</span></div></th>
                                <th><div class="th-content"><span>Policy Owner</span></div></th>
                            </tr>
                        </thead>
                        <tbody>
                            ${policyRelationships.map(rel => `
                                <tr>
                                    <td>${escapeHtml(rel.relationTypeName || 'N/A')}</td>
                                    <td>${createEntityLink('policy', rel.policyId, rel.policyName)}</td>
                                    <td style="text-align: center;">${escapeHtml(rel.policyRefNumber || '')}</td>
                                    <td style="text-align: center;">${escapeHtml(rel.policyOwnerName || 'No owner')}</td>
                                </tr>
                            `).join('')}
                        </tbody>
                    </table>
                </div>
                <div class="hierarchy-footer">${policyRelationships.length} record${policyRelationships.length !== 1 ? 's' : ''}</div>
            </div>
        `;
    }
    
    function renderProjectViewTableBody() {
        if (projectRelationships.length === 0) {
            return '<div class="empty-message">No project relationships found</div>';
        }
        
        return `
            <div class="relationships-hierarchy">
                <div class="hierarchy-header">
                    <div class="hierarchy-title">PROJECT</div>
                </div>
                <div class="data-table-wrapper">
                    <table class="data-table">
                        <thead>
                            <tr>
                                <th><div class="th-content"><span>Relationship Type</span></div></th>
                                <th><div class="th-content"><span>Project</span></div></th>
                                <th><div class="th-content"><span>Ref.</span></div></th>
                                <th><div class="th-content"><span>Project Owner</span></div></th>
                            </tr>
                        </thead>
                        <tbody>
                            ${projectRelationships.map(rel => `
                                <tr>
                                    <td>${escapeHtml(rel.relationTypeName || 'N/A')}</td>
                                    <td>${createEntityLink('project', rel.projectId, rel.projectName)}</td>
                                    <td style="text-align: center;">${escapeHtml(rel.projectRefNumber || '')}</td>
                                    <td style="text-align: center;">${escapeHtml(rel.projectOwnerName || 'No owner')}</td>
                                </tr>
                            `).join('')}
                        </tbody>
                    </table>
                </div>
                <div class="hierarchy-footer">${projectRelationships.length} record${projectRelationships.length !== 1 ? 's' : ''}</div>
            </div>
        `;
    }
    
    function renderRegulatoryThemeViewTableBody() {
        if (regulatoryThemeRelationships.length === 0) {
            return '<div class="empty-message">No regulatory theme relationships found</div>';
        }
        
        return `
            <div class="relationships-hierarchy">
                <div class="hierarchy-header">
                    <div class="hierarchy-title">REGULATORY THEME</div>
                </div>
                <div class="data-table-wrapper">
                    <table class="data-table">
                        <thead>
                            <tr>
                                <th><div class="th-content"><span>Relationship Type</span></div></th>
                                <th><div class="th-content"><span>Regulatory Theme Name</span></div></th>
                            </tr>
                        </thead>
                        <tbody>
                            ${regulatoryThemeRelationships.map(rel => `
                                <tr>
                                    <td>${escapeHtml(rel.relationTypeName || 'N/A')}</td>
                                    <td>${createEntityLink('regulatorytheme', rel.regulatoryThemeId, rel.regulatoryThemeName)}</td>
                                </tr>
                            `).join('')}
                        </tbody>
                    </table>
                </div>
                <div class="hierarchy-footer">${regulatoryThemeRelationships.length} record${regulatoryThemeRelationships.length !== 1 ? 's' : ''}</div>
            </div>
        `;
    }

    // Setup Impact view sub-tabs
    function setupImpactViewSubTabs() {
        const subTabs = document.querySelectorAll('#regulationImpactContainer .sub-tab');
        
        subTabs.forEach(function(subTab) {
            subTab.addEventListener('click', function(e) {
                e.preventDefault();
                
                // Remove active class from all sub-tabs
                subTabs.forEach(function(t) { t.classList.remove('active'); });
                this.classList.add('active');
                
                const subTabName = this.getAttribute('data-sub-tab');
                
                // Hide all sub-tab contents
                const subTabContents = document.querySelectorAll('#regulationImpactContainer .sub-tab-content');
                subTabContents.forEach(function(content) {
                    content.classList.remove('active');
                    content.style.display = 'none';
                });
                
                // Show selected sub-tab content
                let targetSubTab;
                switch(subTabName) {
                    case 'product':
                        targetSubTab = document.getElementById('impactProductContent');
                        break;
                    case 'policy':
                        targetSubTab = document.getElementById('impactPolicyContent');
                        break;
                    case 'project':
                        targetSubTab = document.getElementById('impactProjectContent');
                        break;
                    case 'regulatorytheme':
                        targetSubTab = document.getElementById('impactRegulatoryThemeContent');
                        break;
                }
                
                if (targetSubTab) {
                    targetSubTab.classList.add('active');
                    targetSubTab.style.display = 'block';
                }
            });
        });
    }

    // Expose function globally
    window.loadRegulationImpact = loadRegulationImpact;
})();

