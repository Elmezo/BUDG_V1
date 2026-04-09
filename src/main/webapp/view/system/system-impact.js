// System Impact View JavaScript - Implementation for Product (special), Client, and Legal Entity sub-tabs
console.log('=== SYSTEM IMPACT VIEW SCRIPT LOADING ===');

(function() {
    console.log('=== SYSTEM IMPACT VIEW SCRIPT LOADED ===');

    // Grid settings helpers (from /assets/js/grid-settings.js)
    function gsHtml(id) { return window.GridSettings ? GridSettings.html(id) : ''; }
    function gsInit(el) { if (window.GridSettings) GridSettings.init(el); }

    let productRelationships = [];
    let clientRelationships = [];
    let legalRelationships = [];
    let processRelationshipsReverse = [];
    let capabilityRelationshipsReverse = [];
    let projectRelationshipsReverse = [];
    let businessAreaRelationshipsReverse = [];
    let policyRelationshipsReverse = [];
    let currentSystemId = null;
    let currentViewMode = 'original'; // 'original' | 'changes'

    // Initialize Impact tab view functionality
    function loadSystemImpact(systemId, viewMode = 'original') {
        console.log('=== LOAD SYSTEM IMPACT CALLED ===');
        console.log('System ID:', systemId);
        currentSystemId = systemId;
        currentViewMode = viewMode || 'original';
        loadImpactViewData(systemId);
    }

    function getViewParam() {
        return currentViewMode === 'changes' ? '?view=changes' : '';
    }

    // Load all Impact view data
    async function loadImpactViewData(systemId) {
        try {
            console.log('=== LOADING IMPACT VIEW DATA START ===');
            
            await Promise.all([
                loadProductRelationships(systemId),
                loadClientRelationships(systemId),
                loadLegalRelationships(systemId),
                loadProcessRelationshipsReverse(systemId),
                loadCapabilityRelationshipsReverse(systemId),
                loadProjectRelationshipsReverse(systemId),
                loadBusinessAreaRelationshipsReverse(systemId),
                loadPolicyRelationshipsReverse(systemId)
            ]);

            renderImpactView();
            console.log('=== LOADING IMPACT VIEW DATA END ===');

        } catch (error) {
            console.error('Error loading Impact view data:', error);
        }
    }
    
    // Load reverse relationships
    // These show which Processes, Capabilities, and Projects impact this System
    async function loadProcessRelationshipsReverse(systemId) {
        try {
            const response = await fetch(`/api/process-impact/systems/${systemId}/processes`, {
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
    
    async function loadCapabilityRelationshipsReverse(systemId) {
        try {
            const response = await fetch(`/api/capability-impact/systems/${systemId}/capabilities`, {
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
    
    async function loadProjectRelationshipsReverse(systemId) {
        try {
            const response = await fetch(`/api/project-impact/systems/${systemId}/projects`, {
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
    
    async function loadBusinessAreaRelationshipsReverse(systemId) {
        try {
            const response = await fetch(`/api/businessarea-impact/systems/${systemId}/businessareas`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });
            if (!response.ok) {
                businessAreaRelationshipsReverse = [];
                return;
            }
            const data = await response.json();
            businessAreaRelationshipsReverse = Array.isArray(data) ? data : [];
        } catch (error) {
            console.error('Error loading reverse business area relationships:', error);
            businessAreaRelationshipsReverse = [];
        }
    }
    
    async function loadPolicyRelationshipsReverse(systemId) {
        try {
            const response = await fetch(`/api/policy-impact/systems/${systemId}/policies`, {
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

    // ===== PRODUCT RELATIONSHIPS =====
    
    async function loadProductRelationships(systemId) {
        try {
            const response = await fetch(`/api/system-impact/${systemId}/products${getViewParam()}`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            productRelationships = Array.isArray(data) ? data : [];

        } catch (error) {
            console.error('Error loading product relationships:', error);
            productRelationships = [];
        }
    }

    // ===== CLIENT RELATIONSHIPS =====
    
    async function loadClientRelationships(systemId) {
        try {
            const response = await fetch(`/api/system-impact/${systemId}/clients${getViewParam()}`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            clientRelationships = Array.isArray(data) ? data : [];

        } catch (error) {
            console.error('Error loading client relationships:', error);
            clientRelationships = [];
        }
    }

    // ===== LEGAL ENTITY RELATIONSHIPS =====
    
    async function loadLegalRelationships(systemId) {
        try {
            const response = await fetch(`/api/system-impact/${systemId}/legals${getViewParam()}`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            legalRelationships = Array.isArray(data) ? data : [];

        } catch (error) {
            console.error('Error loading legal relationships:', error);
            legalRelationships = [];
        }
    }

    // ===== RENDER VIEW =====
    
    function renderImpactView() {
        const container = document.getElementById('systemImpactContainer');
        if (!container) return;
        
        // Determine which sub-tabs to show (only if they have data)
        const hasProductData = productRelationships && productRelationships.length > 0;
        const hasClientData = clientRelationships && clientRelationships.length > 0;
        const hasLegalData = legalRelationships && legalRelationships.length > 0;
        const hasProcessReverse = processRelationshipsReverse && processRelationshipsReverse.length > 0;
        const hasCapabilityReverse = capabilityRelationshipsReverse && capabilityRelationshipsReverse.length > 0;
        const hasProjectReverse = projectRelationshipsReverse && projectRelationshipsReverse.length > 0;
        const hasBusinessAreaReverse = businessAreaRelationshipsReverse && businessAreaRelationshipsReverse.length > 0;
        const hasPolicyReverse = policyRelationshipsReverse && policyRelationshipsReverse.length > 0;
        
        const hasAnyData = hasProductData || hasClientData || hasLegalData || hasProcessReverse || hasCapabilityReverse || hasProjectReverse || hasBusinessAreaReverse || hasPolicyReverse;
        
        if (!hasAnyData) {
            container.innerHTML = `
                <div class="view-section" style="grid-column:1/-1;">
                    <div style="padding: 2rem; text-align: center; color: var(--text-muted, #6b7280);">
                        <i class="fas fa-info-circle" style="font-size: 2rem; margin-bottom: 1rem; opacity: 0.5;"></i>
                        <p>${window.I18n?.t('message.noImpactRelationships') || 'No impact relationships found'}</p>
                    </div>
                </div>
            `;
            return;
        }
        
        // Build sub-tabs HTML
        let subTabsHtml = '';
        let firstActive = true;
        if (hasProductData) {
            const productText = window.I18n?.t('systemImpact.sections.products') || 'Product';
            subTabsHtml += `<button class="sub-tab active" data-sub-tab="product">${productText}</button>`;
            firstActive = false;
        }
        if (hasClientData) {
            const clientText = window.I18n?.t('systemImpact.sections.clients') || 'Client';
            subTabsHtml += `<button class="sub-tab ${firstActive ? 'active' : ''}" data-sub-tab="client">${clientText}</button>`;
            if (firstActive) firstActive = false;
        }
        if (hasLegalData) {
            const legalText = window.I18n?.t('systemImpact.sections.legalEntities') || 'Legal Entity';
            subTabsHtml += `<button class="sub-tab ${firstActive ? 'active' : ''}" data-sub-tab="legal">${legalText}</button>`;
            if (firstActive) firstActive = false;
        }
        if (hasProcessReverse) {
            const processText = window.I18n?.t('systemImpact.sections.processes') || 'Process';
            subTabsHtml += `<button class="sub-tab ${firstActive ? 'active' : ''}" data-sub-tab="process">${processText}</button>`;
            if (firstActive) firstActive = false;
        }
        if (hasCapabilityReverse) {
            const capabilityText = window.I18n?.t('systemImpact.sections.capabilities') || 'Capability';
            subTabsHtml += `<button class="sub-tab ${firstActive ? 'active' : ''}" data-sub-tab="capability">${capabilityText}</button>`;
            if (firstActive) firstActive = false;
        }
        if (hasProjectReverse) {
            const projectText = window.I18n?.t('systemImpact.sections.projects') || 'Project';
            subTabsHtml += `<button class="sub-tab ${firstActive ? 'active' : ''}" data-sub-tab="project">${projectText}</button>`;
            if (firstActive) firstActive = false;
        }
        if (hasBusinessAreaReverse) {
            const businessAreaText = window.I18n?.t('systemImpact.sections.businessAreas') || 'Business Area';
            subTabsHtml += `<button class="sub-tab ${firstActive ? 'active' : ''}" data-sub-tab="businessarea">${businessAreaText}</button>`;
            if (firstActive) firstActive = false;
        }
        if (hasPolicyReverse) {
            const policyText = window.I18n?.t('systemImpact.sections.policies') || 'Policy';
            subTabsHtml += `<button class="sub-tab ${firstActive ? 'active' : ''}" data-sub-tab="policy">${policyText}</button>`;
        }
        
        // Build sub-tab content HTML
        let subTabsContentHtml = '';
        let firstActiveContent = true;
        
        if (hasProductData) {
            const productText = window.I18n?.t('systemImpact.sections.products') || 'Product';
            const relTypeText = window.I18n?.t('systemImpact.labels.relationshipType') || 'Relationship Type';
            const productNameText = window.I18n?.t('systemImpact.labels.productName') || 'Product Name';
            const productOwnerText = window.I18n?.t('systemImpact.labels.productOwner') || 'Product Owner';
            const legalRelTypeText = window.I18n?.t('systemImpact.labels.legalEntityRelationshipType') || 'Legal Entity Relationship Type';
            const legalEntityText = window.I18n?.t('systemImpact.labels.legalEntity') || 'Legal Entity';
            const recordText = productRelationships.length !== 1 ? (window.I18n?.t('dataset.messages.records') || 'records') : (window.I18n?.t('dataset.messages.record') || 'record');
            subTabsContentHtml += `
                <div id="impactProductViewContent" class="sub-tab-content ${firstActiveContent ? 'active' : ''}" ${firstActiveContent ? 'style="display:block;"' : ''}>
                    <div class="relationships-hierarchy">
                        <div class="hierarchy-header">
                            <div class="hierarchy-title">${productText.toUpperCase()}</div>
                            <div class="hierarchy-actions">
                                ${gsHtml('impactProduct')}
                            </div>
                        </div>
                        <div class="data-table-wrapper">
                            <table class="data-table">
                                <thead>
                                    <tr>
                                        <th><div class="th-content"><span>${relTypeText}</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content"><span>${productNameText}</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content" style="justify-content: center;"><span>${productOwnerText}</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content"><span>${legalRelTypeText}</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content"><span>${legalEntityText}</span><i class="fas fa-sort"></i></div></th>
                                    </tr>
                                </thead>
                                <tbody id="productImpactViewTableBody">
                                    ${renderProductViewTableBody()}
                                </tbody>
                            </table>
                        </div>
                        <div class="hierarchy-footer">
                            ${productRelationships.length} ${recordText}
                        </div>
                    </div>
                </div>
            `;
            firstActiveContent = false;
        }
        
        if (hasClientData) {
            const clientText = window.I18n?.t('systemImpact.sections.clients') || 'Client';
            const relTypeText = window.I18n?.t('systemImpact.labels.relationshipType') || 'Relationship Type';
            const clientOwnerText = window.I18n?.t('systemImpact.labels.clientOwner') || 'Client Owner';
            const recordText = clientRelationships.length !== 1 ? (window.I18n?.t('dataset.messages.records') || 'records') : (window.I18n?.t('dataset.messages.record') || 'record');
            subTabsContentHtml += `
                <div id="impactClientViewContent" class="sub-tab-content ${firstActiveContent ? 'active' : ''}" ${firstActiveContent ? 'style="display:block;"' : ''}>
                    <div class="relationships-hierarchy">
                        <div class="hierarchy-header">
                            <div class="hierarchy-title">${clientText.toUpperCase()}</div>
                            <div class="hierarchy-actions">
                                ${gsHtml('impactClient')}
                            </div>
                        </div>
                        <div class="data-table-wrapper">
                            <table class="data-table">
                                <thead>
                                    <tr>
                                        <th><div class="th-content"><span>${relTypeText}</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content"><span>${clientText}</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content" style="justify-content: center;"><span>${clientOwnerText}</span><i class="fas fa-sort"></i></div></th>
                                    </tr>
                                </thead>
                                <tbody id="clientImpactViewTableBody">
                                    ${renderClientViewTableBody()}
                                </tbody>
                            </table>
                        </div>
                        <div class="hierarchy-footer">
                            ${clientRelationships.length} ${recordText}
                        </div>
                    </div>
                </div>
            `;
            firstActiveContent = false;
        }
        
        if (hasLegalData) {
            const legalText = window.I18n?.t('systemImpact.sections.legalEntities') || 'Legal Entity';
            const relTypeText = window.I18n?.t('systemImpact.labels.relationshipType') || 'Relationship Type';
            const legalOwnerText = window.I18n?.t('systemImpact.labels.legalEntityOwner') || 'Legal Entity Owner';
            const recordText = legalRelationships.length !== 1 ? (window.I18n?.t('dataset.messages.records') || 'records') : (window.I18n?.t('dataset.messages.record') || 'record');
            subTabsContentHtml += `
                <div id="impactLegalViewContent" class="sub-tab-content ${firstActiveContent ? 'active' : ''}" ${firstActiveContent ? 'style="display:block;"' : ''}>
                    <div class="relationships-hierarchy">
                        <div class="hierarchy-header">
                            <div class="hierarchy-title">${legalText.toUpperCase()}</div>
                            <div class="hierarchy-actions">
                                ${gsHtml('impactLegal')}
                            </div>
                        </div>
                        <div class="data-table-wrapper">
                            <table class="data-table">
                                <thead>
                                    <tr>
                                        <th><div class="th-content"><span>${relTypeText}</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content"><span>${legalText}</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content" style="justify-content: center;"><span>${legalOwnerText}</span><i class="fas fa-sort"></i></div></th>
                                    </tr>
                                </thead>
                                <tbody id="legalImpactViewTableBody">
                                    ${renderLegalViewTableBody()}
                                </tbody>
                            </table>
                        </div>
                        <div class="hierarchy-footer">
                            ${legalRelationships.length} ${recordText}
                        </div>
                    </div>
                </div>
            `;
            firstActiveContent = false;
        }
        
        // Add reverse relationship sub-tabs
        if (hasProcessReverse) {
            const processText = window.I18n?.t('systemImpact.sections.processes') || 'Process';
            const relTypeText = window.I18n?.t('systemImpact.labels.relationshipType') || 'Relationship Type';
            const processOwnerText = window.I18n?.t('systemImpact.labels.processOwner') || 'Process Owner';
            const recordText = processRelationshipsReverse.length !== 1 ? (window.I18n?.t('dataset.messages.records') || 'records') : (window.I18n?.t('dataset.messages.record') || 'record');
            subTabsContentHtml += `
                <div id="impactProcessReverseViewContent" class="sub-tab-content ${firstActiveContent ? 'active' : ''}" ${firstActiveContent ? 'style="display:block;"' : ''}>
                    <div class="relationships-hierarchy">
                        <div class="hierarchy-header">
                            <div class="hierarchy-title">${processText.toUpperCase()}</div>
                            <div class="hierarchy-actions">
                                ${gsHtml('impactProcess')}
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
            firstActiveContent = false;
        }
        
        if (hasCapabilityReverse) {
            const capabilityText = window.I18n?.t('systemImpact.sections.capabilities') || 'Capability';
            const relTypeText = window.I18n?.t('systemImpact.labels.relationshipType') || 'Relationship Type';
            const capabilityOwnerText = window.I18n?.t('systemImpact.labels.capabilityOwner') || 'Capability Owner';
            const recordText = capabilityRelationshipsReverse.length !== 1 ? (window.I18n?.t('dataset.messages.records') || 'records') : (window.I18n?.t('dataset.messages.record') || 'record');
            subTabsContentHtml += `
                <div id="impactCapabilityReverseViewContent" class="sub-tab-content ${firstActiveContent ? 'active' : ''}" ${firstActiveContent ? 'style="display:block;"' : ''}>
                    <div class="relationships-hierarchy">
                        <div class="hierarchy-header">
                            <div class="hierarchy-title">${capabilityText.toUpperCase()}</div>
                            <div class="hierarchy-actions">
                                ${gsHtml('impactCapability')}
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
            firstActiveContent = false;
        }
        
        if (hasProjectReverse) {
            const projectText = window.I18n?.t('systemImpact.sections.projects') || 'Project';
            const relTypeText = window.I18n?.t('systemImpact.labels.relationshipType') || 'Relationship Type';
            const projectOwnerText = window.I18n?.t('systemImpact.labels.projectOwner') || 'Project Owner';
            const recordText = projectRelationshipsReverse.length !== 1 ? (window.I18n?.t('dataset.messages.records') || 'records') : (window.I18n?.t('dataset.messages.record') || 'record');
            subTabsContentHtml += `
                <div id="impactProjectReverseViewContent" class="sub-tab-content ${firstActiveContent ? 'active' : ''}" ${firstActiveContent ? 'style="display:block;"' : ''}>
                    <div class="relationships-hierarchy">
                        <div class="hierarchy-header">
                            <div class="hierarchy-title">${projectText.toUpperCase()}</div>
                            <div class="hierarchy-actions">
                                ${gsHtml('impactProject')}
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
            firstActiveContent = false;
        }
        
        if (hasBusinessAreaReverse) {
            const businessAreaText = window.I18n?.t('systemImpact.sections.businessAreas') || 'Business Area';
            const relTypeText = window.I18n?.t('systemImpact.labels.relationshipType') || 'Relationship Type';
            const businessAreaOwnerText = window.I18n?.t('systemImpact.labels.businessAreaOwner') || 'Business Area Owner';
            const recordText = businessAreaRelationshipsReverse.length !== 1 ? (window.I18n?.t('dataset.messages.records') || 'records') : (window.I18n?.t('dataset.messages.record') || 'record');
            subTabsContentHtml += `
                <div id="impactBusinessAreaReverseViewContent" class="sub-tab-content ${firstActiveContent ? 'active' : ''}" ${firstActiveContent ? 'style="display:block;"' : ''}>
                    <div class="relationships-hierarchy">
                        <div class="hierarchy-header">
                            <div class="hierarchy-title">${businessAreaText.toUpperCase()}</div>
                            <div class="hierarchy-actions">
                                ${gsHtml('impactBusinessArea')}
                            </div>
                        </div>
                        <div class="data-table-wrapper">
                            <table class="data-table">
                                <thead>
                                    <tr>
                                        <th><div class="th-content"><span>${relTypeText}</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content"><span>${businessAreaText}</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content" style="justify-content: center;"><span>${businessAreaOwnerText}</span><i class="fas fa-sort"></i></div></th>
                                    </tr>
                                </thead>
                                <tbody>${renderBusinessAreaReverseViewTableBody()}</tbody>
                            </table>
                        </div>
                        <div class="hierarchy-footer">${businessAreaRelationshipsReverse.length} ${recordText}</div>
                    </div>
                </div>
            `;
            firstActiveContent = false;
        }
        
        if (hasPolicyReverse) {
            const policyText = window.I18n?.t('systemImpact.sections.policies') || 'Policy';
            const relTypeText = window.I18n?.t('systemImpact.labels.relationshipType') || 'Relationship Type';
            const policyOwnerText = window.I18n?.t('systemImpact.labels.policyOwner') || 'Policy Owner';
            const recordText = policyRelationshipsReverse.length !== 1 ? (window.I18n?.t('dataset.messages.records') || 'records') : (window.I18n?.t('dataset.messages.record') || 'record');
            subTabsContentHtml += `
                <div id="impactPolicyReverseViewContent" class="sub-tab-content ${firstActiveContent ? 'active' : ''}" ${firstActiveContent ? 'style="display:block;"' : ''}>
                    <div class="relationships-hierarchy">
                        <div class="hierarchy-header">
                            <div class="hierarchy-title">${policyText.toUpperCase()}</div>
                            <div class="hierarchy-actions">
                                ${gsHtml('impactPolicy')}
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
            firstActiveContent = false;
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

        // Initialize grid settings dropdowns
        gsInit(container);
    }

    // ===== RENDER TABLE BODIES =====
    
    function renderProductViewTableBody() {
        if (!productRelationships || productRelationships.length === 0) {
            const msg = window.I18n?.t('message.noProductRelationships') || 'No product relationships found';
            return `<tr><td colspan="5" style="text-align: center; padding: 2rem; color: var(--text-muted, #6b7280);">${msg}</td></tr>`;
        }
        const noOwnerText = window.I18n?.t('message.noOwner') || 'No owner';
        return productRelationships.map(relationship => {
            const productName = relationship.productName || 'Unnamed Product';
            const productOwner = relationship.productOwnerName || noOwnerText;
            const legalRelationType = relationship.legalRelationTypeName || '';
            const legalEntity = relationship.legalLongName || relationship.legalShortName || '';
            const relationType = relationship.productSystemRelationTypeName || '';
            
            return `
                <tr>
                    <td>${relationType}</td>
                    <td>${createEntityLink(productName, 'product', relationship.productId)}</td>
                    <td style="text-align: center;">${productOwner}</td>
                    <td>${legalRelationType}</td>
                    <td>${legalEntity ? escapeHtml(legalEntity) : ''}</td>
                </tr>
            `;
        }).join('');
    }
    
    function renderClientViewTableBody() {
        if (!clientRelationships || clientRelationships.length === 0) {
            const msg = window.I18n?.t('message.noClientRelationships') || 'No client relationships found';
            return `<tr><td colspan="3" style="text-align: center; padding: 2rem; color: var(--text-muted, #6b7280);">${msg}</td></tr>`;
        }
        const noOwnerText = window.I18n?.t('message.noOwner') || 'No owner';
        return clientRelationships.map(relationship => {
            const clientName = relationship.clientName || 'Unnamed Client';
            const clientOwner = relationship.clientOwnerName || noOwnerText;
            const relationType = relationship.relationTypeName || '';
            
            return `
                <tr>
                    <td>${relationType}</td>
                    <td>${createEntityLink(clientName, 'client', relationship.clientId)}</td>
                    <td style="text-align: center;">${clientOwner}</td>
                </tr>
            `;
        }).join('');
    }
    
    function renderLegalViewTableBody() {
        if (!legalRelationships || legalRelationships.length === 0) {
            const msg = window.I18n?.t('message.noLegalEntityRelationships') || 'No legal entity relationships found';
            return `<tr><td colspan="3" style="text-align: center; padding: 2rem; color: var(--text-muted, #6b7280);">${msg}</td></tr>`;
        }
        const noOwnerText = window.I18n?.t('message.noOwner') || 'No owner';
        return legalRelationships.map(relationship => {
            const legalName = relationship.legalLongName || relationship.legalShortName || 'Unnamed Legal Entity';
            const legalOwner = relationship.legalOwnerName || noOwnerText;
            const relationType = relationship.relationTypeName || '';
            
            return `
                <tr>
                    <td>${relationType}</td>
                    <td>${createEntityLink(legalName, 'legal', relationship.legalId)}</td>
                    <td style="text-align: center;">${legalOwner}</td>
                </tr>
            `;
        }).join('');
    }

    // Helper function to escape HTML
    function escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    // Helper function to create entity links - match policy impact styling
    function createEntityLink(name, entityType, entityId) {
        if (!name || !entityId || name === 'N/A') {
            return escapeHtml(name || 'N/A');
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
            case 'process':
                url = `/view/process/${encodeURIComponent(entityId)}`;
                break;
            case 'capability':
                url = `/view/capability/${encodeURIComponent(entityId)}`;
                break;
            case 'project':
                url = `/view/project/${encodeURIComponent(entityId)}`;
                break;
            case 'businessarea':
                url = `/view/business-area/${encodeURIComponent(entityId)}`;
                break;
            case 'policy':
                url = `/view/policy/${encodeURIComponent(entityId)}`;
                break;
            default:
                return escapeHtml(name);
        }
        
        const viewText = window.I18n?.t('systemImpact.messages.view') || 'View';
        return `<a href="${url}" class="entity-link" style="color: var(--secondary-color, #248567); text-decoration: none;" title="${viewText} ${escapeHtml(name)}">${escapeHtml(name)}</a>`;
    }

    // Render reverse relationship table bodies
    function renderProcessReverseViewTableBody() {
        if (!processRelationshipsReverse || processRelationshipsReverse.length === 0) {
            return '<tr class="reverse-relationship-row"><td colspan="3" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">No process relationships found</td></tr>';
        }
        return processRelationshipsReverse.map(rel => {
            const relationTypeName = escapeHtml(rel.relationTypeReverseName || rel.relationTypeName || 'N/A');
            const processName = rel.processName || 'N/A';
            const processId = rel.processId;
            const processLink = createEntityLink(processName, 'process', processId);
            const ownerName = rel.processOwnerName || rel.ownerName || 'No owner';
            return `<tr class="reverse-relationship-row"><td>${relationTypeName}</td><td><div class="entity-info reverse-relationship-entity"><i class="fas fa-cogs" style="color: var(--text-secondary, #64748b); margin-right: 0.5rem;"></i><span class="entity-name">${processLink}</span></div></td><td style="text-align: center;"><div class="owner-info reverse-relationship-owner"><span class="owner-name">${escapeHtml(ownerName)}</span></div></td></tr>`;
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
            const capabilityLink = createEntityLink(capabilityName, 'capability', capabilityId);
            const ownerName = rel.capabilityOwnerName || rel.ownerName || noOwnerText;
            return `<tr class="reverse-relationship-row"><td>${relationTypeName}</td><td><div class="entity-info reverse-relationship-entity"><i class="fas fa-cogs" style="color: var(--text-secondary, #64748b); margin-right: 0.5rem;"></i><span class="entity-name">${capabilityLink}</span></div></td><td style="text-align: center;"><div class="owner-info reverse-relationship-owner"><span class="owner-name">${escapeHtml(ownerName)}</span></div></td></tr>`;
        }).join('');
    }
    
    function renderProjectReverseViewTableBody() {
        if (!projectRelationshipsReverse || projectRelationshipsReverse.length === 0) {
            return '<tr class="reverse-relationship-row"><td colspan="3" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">No project relationships found</td></tr>';
        }
        return projectRelationshipsReverse.map(rel => {
            // For reverse relationships, use primary name (relationship type name) for consistency
            const relationTypeDisplay = escapeHtml(rel.relationTypeName || rel.relationTypeReverseName || 'N/A');
            const projectName = rel.projectName || 'N/A';
            const projectId = rel.projectId;
            const projectLink = createEntityLink(projectName, 'project', projectId);
            const ownerName = rel.projectOwnerName || rel.ownerName || 'No owner';
            return `<tr class="reverse-relationship-row"><td>${relationTypeDisplay}</td><td><div class="entity-info reverse-relationship-entity"><i class="fas fa-project-diagram" style="color: var(--text-secondary, #64748b); margin-right: 0.5rem;"></i><span class="entity-name">${projectLink}</span></div></td><td style="text-align: center;"><div class="owner-info reverse-relationship-owner"><span class="owner-name">${escapeHtml(ownerName)}</span></div></td></tr>`;
        }).join('');
    }
    
    function renderBusinessAreaReverseViewTableBody() {
        if (!businessAreaRelationshipsReverse || businessAreaRelationshipsReverse.length === 0) {
            const msg = window.I18n?.t('message.noBusinessAreaRelationships') || 'No business area relationships found';
            return `<tr class="reverse-relationship-row"><td colspan="3" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">${msg}</td></tr>`;
        }
        const noOwnerText = window.I18n?.t('message.noOwner') || 'No owner';
        return businessAreaRelationshipsReverse.map(rel => {
            const relationTypeDisplay = escapeHtml(rel.relationTypeReverseName || rel.relationTypeName || 'N/A');
            const businessAreaName = rel.businessAreaName || 'N/A';
            const businessAreaId = rel.businessAreaId;
            const businessAreaLink = createEntityLink(businessAreaName, 'businessarea', businessAreaId);
            const ownerName = rel.businessAreaOwnerName || noOwnerText;
            return `<tr class="reverse-relationship-row"><td>${relationTypeDisplay}</td><td><div class="entity-info reverse-relationship-entity"><i class="fas fa-building" style="color: var(--text-secondary, #64748b); margin-right: 0.5rem;"></i><span class="entity-name">${businessAreaLink}</span></div></td><td style="text-align: center;"><div class="owner-info reverse-relationship-owner"><span class="owner-name">${escapeHtml(ownerName)}</span></div></td></tr>`;
        }).join('');
    }
    
    function renderPolicyReverseViewTableBody() {
        if (!policyRelationshipsReverse || policyRelationshipsReverse.length === 0) {
            const msg = window.I18n?.t('message.noPolicyRelationships') || 'No policy relationships found';
            return `<tr class="reverse-relationship-row"><td colspan="3" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">${msg}</td></tr>`;
        }
        const noOwnerTextPolicy = window.I18n?.t('message.noOwner') || 'No owner';
        return policyRelationshipsReverse.map(rel => {
            const relationTypeName = escapeHtml(rel.relationTypeReverseName || rel.relationTypeName || 'N/A');
            const policyName = rel.policyName || 'N/A';
            const policyId = rel.policyId;
            const policyLink = createEntityLink(policyName, 'policy', policyId);
            const ownerName = rel.policyOwnerName || rel.ownerName || noOwnerTextPolicy;
            return `<tr class="reverse-relationship-row"><td>${relationTypeName}</td><td><div class="entity-info reverse-relationship-entity"><i class="fas fa-file-contract" style="color: var(--text-secondary, #64748b); margin-right: 0.5rem;"></i><span class="entity-name">${policyLink}</span></div></td><td style="text-align: center;"><div class="owner-info reverse-relationship-owner"><span class="owner-name">${escapeHtml(ownerName)}</span></div></td></tr>`;
        }).join('');
    }
    
    // Setup Impact view sub-tabs
    function setupImpactViewSubTabs() {
        const subTabs = document.querySelectorAll('#systemImpactContainer .sub-tab');
        
        subTabs.forEach(function(subTab) {
            subTab.addEventListener('click', function(e) {
                e.preventDefault();
                
                // Remove active class from all sub-tabs
                subTabs.forEach(function(t) { t.classList.remove('active'); });
                this.classList.add('active');
                
                const subTabName = this.getAttribute('data-sub-tab');
                
                // Hide all sub-tab contents
                const subTabContents = document.querySelectorAll('#systemImpactContainer .sub-tab-content');
                subTabContents.forEach(function(content) {
                    content.classList.remove('active');
                    content.style.display = 'none';
                });
                
                // Show selected sub-tab content
                let targetSubTab;
                if (subTabName === 'product') {
                    targetSubTab = document.getElementById('impactProductViewContent');
                } else if (subTabName === 'client') {
                    targetSubTab = document.getElementById('impactClientViewContent');
                } else if (subTabName === 'legal') {
                    targetSubTab = document.getElementById('impactLegalViewContent');
                } else if (subTabName === 'process') {
                    targetSubTab = document.getElementById('impactProcessReverseViewContent');
                } else if (subTabName === 'capability') {
                    targetSubTab = document.getElementById('impactCapabilityReverseViewContent');
                } else if (subTabName === 'project') {
                    targetSubTab = document.getElementById('impactProjectReverseViewContent');
                } else if (subTabName === 'businessarea') {
                    targetSubTab = document.getElementById('impactBusinessAreaReverseViewContent');
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

    // ===== GLOBAL EXPORTS =====
    
    window.loadSystemImpact = loadSystemImpact;

})();

