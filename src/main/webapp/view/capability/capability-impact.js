// Capability Impact View JavaScript - Read-only display for System, Process, Glossary, Product, Client, Legal Entity, Business Area, and Project sub-tabs
(function() {
    let currentCapabilityId = null;
    let systemRelationships = [];
    let processRelationships = [];
    let glossaryRelationships = [];
    let productRelationships = [];
    let clientRelationships = [];
    let legalRelationships = [];
    let businessAreaRelationships = [];
    let projectRelationships = [];

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
            case 'system':
                url = `/view/system/${encodeURIComponent(id)}`;
                break;
            case 'product':
                url = `/view/product/${encodeURIComponent(id)}`;
                break;
            case 'client':
                url = `/view/client/${encodeURIComponent(id)}`;
                break;
            case 'project':
                url = `/view/project/${encodeURIComponent(id)}`;
                break;
            case 'interface':
                url = `/view/system-interface/${encodeURIComponent(id)}`;
                break;
            case 'legal':
                url = `/view/LegalEntity/${encodeURIComponent(id)}`;
                break;
            case 'glossary':
                url = `/view/glossary/${encodeURIComponent(id)}`;
                break;
            case 'process':
                url = `/view/process/${encodeURIComponent(id)}`;
                break;
            case 'capability':
                url = `/view/capability/${encodeURIComponent(id)}`;
                break;
            case 'businessarea':
                url = `/view/business-area/business-area.html?id=${encodeURIComponent(id)}`;
                break;
            case 'committee':
                url = `/view/committee/${encodeURIComponent(id)}`;
                break;
            default:
                return escapeHtml(name);
        }
        
        const viewText = window.I18n?.t('capabilityImpact.messages.view') || 'View';
        return `<a href="${url}" class="entity-link" style="color: var(--secondary-color, #248567); text-decoration: none;" title="${viewText} ${escapeHtml(name)}">${escapeHtml(name)}</a>`;
    }

    // Initialize Impact view
    function loadCapabilityImpact(capabilityId) {
        currentCapabilityId = capabilityId;
        console.log('Loading Capability Impact for capability:', capabilityId);
        loadImpactViewData();
    }

    // Reverse relationship arrays
    // Note: When viewing Capability, reverse lookups show which Systems, Processes, etc. impact this Capability
    // But the reverse APIs are: /api/capability-impact/{entityType}s/{entityId}/capabilities
    // These are for when viewing System/Process/etc., to see Capabilities that impact them
    // For Capability view, we also need reverse lookups for Committee (which Committees impact this Capability)
    let systemRelationshipsReverse = [];
    let processRelationshipsReverse = [];
    let productRelationshipsReverse = [];
    let clientRelationshipsReverse = [];
    let legalRelationshipsReverse = [];
    let businessAreaRelationshipsReverse = [];
    let projectRelationshipsReverse = [];
    let glossaryRelationshipsReverse = [];
    let committeeRelationshipsReverse = [];
    
    // Load all Impact view data
    async function loadImpactViewData() {
        try {
            // Load relationship data (forward and reverse for Capability)
            await Promise.all([
                loadSystemRelationshipsView(),
                loadProcessRelationshipsView(),
                loadGlossaryRelationshipsView(),
                loadProductRelationshipsView(),
                loadClientRelationshipsView(),
                loadLegalRelationshipsView(),
                loadBusinessAreaRelationshipsView(),
                loadProjectRelationshipsView(),
                loadCommitteeRelationshipsReverse()
            ]);

            // Render the view
            renderImpactView();

        } catch (error) {
            console.error('Error loading Impact view data:', error);
        }
    }

    // Load system relationships for view
    async function loadSystemRelationshipsView() {
        try {
            console.log('Loading system relationships for view, capability:', currentCapabilityId);

            const response = await fetch(`/api/capability-impact/${currentCapabilityId}/systems`, {
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

    // Load product relationships for view
    async function loadProductRelationshipsView() {
        try {
            console.log('Loading product relationships for view, capability:', currentCapabilityId);

            const response = await fetch(`/api/capability-impact/${currentCapabilityId}/products`, {
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
            console.log('Product relationships view API response:', data);

            productRelationships = Array.isArray(data) ? data : [];

        } catch (error) {
            console.error('Error loading product relationships for view:', error);
            productRelationships = [];
        }
    }

    // Load client relationships for view
    async function loadClientRelationshipsView() {
        try {
            console.log('Loading client relationships for view, capability:', currentCapabilityId);

            const response = await fetch(`/api/capability-impact/${currentCapabilityId}/clients`, {
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

    // Load project relationships for view
    async function loadProjectRelationshipsView() {
        try {
            console.log('Loading project relationships for view, capability:', currentCapabilityId);

            const response = await fetch(`/api/capability-impact/${currentCapabilityId}/projects`, {
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
            console.log('Project relationships view API response:', data);

            projectRelationships = Array.isArray(data) ? data : [];

        } catch (error) {
            console.error('Error loading project relationships for view:', error);
            projectRelationships = [];
        }
    }
    
    async function loadCommitteeRelationshipsReverse() {
        try {
            console.log('Loading committee reverse relationships for capability:', currentCapabilityId);
            
            const response = await fetch(`/api/committee-impact/capabilities/${currentCapabilityId}/committees`, {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });
            
            if (!response.ok) {
                committeeRelationshipsReverse = [];
                return;
            }
            
            const data = await response.json();
            console.log('Committee reverse relationships API response:', data);
            
            committeeRelationshipsReverse = Array.isArray(data) ? data : [];
            
        } catch (error) {
            console.error('Error loading committee reverse relationships:', error);
            committeeRelationshipsReverse = [];
        }
    }

    // Load legal relationships for view
    async function loadLegalRelationshipsView() {
        try {
            console.log('Loading legal relationships for view, capability:', currentCapabilityId);

            const response = await fetch(`/api/capability-impact/${currentCapabilityId}/legals`, {
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

    // Load process relationships for view
    async function loadProcessRelationshipsView() {
        try {
            console.log('Loading process relationships for view, capability:', currentCapabilityId);

            const response = await fetch(`/api/capability-impact/${currentCapabilityId}/processes`, {
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

    // Load business area relationships for view
    async function loadBusinessAreaRelationshipsView() {
        try {
            console.log('Loading business area relationships for view, capability:', currentCapabilityId);

            const response = await fetch(`/api/capability-impact/${currentCapabilityId}/businessareas`, {
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

    // Load glossary relationships for view
    async function loadGlossaryRelationshipsView() {
        try {
            console.log('Loading glossary relationships for view, capability:', currentCapabilityId);

            const response = await fetch(`/api/capability-impact/${currentCapabilityId}/glossaries`, {
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

    // Render Impact view with sub-tabs
    function renderImpactView() {
        const container = document.getElementById('capabilityImpactContainer');
        if (!container) {
            console.error('capabilityImpactContainer not found');
            return;
        }

        // Determine which sub-tabs to show based on data availability
        const hasSystemData = systemRelationships.length > 0;
        const hasProcessData = processRelationships.length > 0;
        const hasGlossaryData = glossaryRelationships.length > 0;
        const hasProductData = productRelationships.length > 0;
        const hasClientData = clientRelationships.length > 0;
        const hasLegalData = legalRelationships.length > 0;
        const hasBusinessAreaData = businessAreaRelationships.length > 0;
        const hasProjectData = projectRelationships.length > 0;
        const hasCommitteeReverse = committeeRelationshipsReverse.length > 0;

        if (!hasSystemData && !hasProcessData && !hasGlossaryData && !hasProductData && !hasClientData && !hasLegalData && !hasBusinessAreaData && !hasProjectData && !hasCommitteeReverse) {
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

        // System tab (first available becomes active)
        if (hasSystemData) {
            subTabsHtml += '<button class="sub-tab active" data-sub-tab="system">System</button>';
            subTabsContentHtml += `
                <div id="impactSystemViewContent" class="sub-tab-content active">
                    <div class="relationships-hierarchy">
                        <div class="hierarchy-header">
                            <div class="hierarchy-title">SYSTEM</div>
                            <div class="hierarchy-actions">
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-cog"></i><i class="fas fa-chevron-down" style="margin-left:.3rem;font-size:.7rem;"></i>
                                </button>
                            </div>
                        </div>
                        <div class="data-table-wrapper">
                            <table class="data-table">
                                <thead>
                                    <tr>
                                        <th><div class="th-content"><span>Relationship Type</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content"><span>Short Name</span><i class="fas fa-sort"></i></div></th>
                                        <th style="text-align: center;"><div class="th-content" style="justify-content: center;"><span>System Owner</span><i class="fas fa-sort"></i></div></th>
                                    </tr>
                                </thead>
                                <tbody id="systemImpactViewTableBody">
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

        // Process tab
        if (hasProcessData) {
            const activeClass = !hasSystemData ? 'active' : '';
            subTabsHtml += `<button class="sub-tab ${activeClass}" data-sub-tab="process">Process</button>`;
            subTabsContentHtml += `
                <div id="impactProcessViewContent" class="sub-tab-content ${activeClass}">
                    <div class="relationships-hierarchy">
                        <div class="hierarchy-header">
                            <div class="hierarchy-title">PROCESS</div>
                            <div class="hierarchy-actions">
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-cog"></i><i class="fas fa-chevron-down" style="margin-left:.3rem;font-size:.7rem;"></i>
                                </button>
                            </div>
                        </div>
                        <div class="data-table-wrapper">
                            <table class="data-table">
                                <thead>
                                    <tr>
                                        <th><div class="th-content"><span>Relationship Type</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content"><span>Process</span><i class="fas fa-sort"></i></div></th>
                                        <th style="text-align: center;"><div class="th-content" style="justify-content: center;"><span>Ref.</span><i class="fas fa-sort"></i></div></th>
                                        <th style="text-align: center;"><div class="th-content" style="justify-content: center;"><span>Process Owner</span><i class="fas fa-sort"></i></div></th>
                                    </tr>
                                </thead>
                                <tbody id="processImpactViewTableBody">
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
        }

        // Glossary tab
        if (hasGlossaryData) {
            const activeClass = !hasSystemData && !hasProcessData ? 'active' : '';
            subTabsHtml += `<button class="sub-tab ${activeClass}" data-sub-tab="glossary">Glossary</button>`;
            subTabsContentHtml += `
                <div id="impactGlossaryViewContent" class="sub-tab-content ${activeClass}">
                    <div class="relationships-hierarchy">
                        <div class="hierarchy-header">
                            <div class="hierarchy-title">GLOSSARY</div>
                            <div class="hierarchy-actions">
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-cog"></i><i class="fas fa-chevron-down" style="margin-left:.3rem;font-size:.7rem;"></i>
                                </button>
                            </div>
                        </div>
                        <div class="data-table-wrapper">
                            <table class="data-table">
                                <thead>
                                    <tr>
                                        <th><div class="th-content"><span>Relationship Type</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content"><span>Name</span><i class="fas fa-sort"></i></div></th>
                                        <th style="text-align: center;"><div class="th-content" style="justify-content: center;"><span>Type</span><i class="fas fa-sort"></i></div></th>
                                        <th style="text-align: center;"><div class="th-content" style="justify-content: center;"><span>Strategic Owner</span><i class="fas fa-sort"></i></div></th>
                                    </tr>
                                </thead>
                                <tbody id="glossaryImpactViewTableBody">
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

        // Product tab
        if (hasProductData) {
            const activeClass = !hasSystemData && !hasProcessData && !hasGlossaryData ? 'active' : '';
            subTabsHtml += `<button class="sub-tab ${activeClass}" data-sub-tab="product">Product</button>`;
            subTabsContentHtml += `
                <div id="impactProductViewContent" class="sub-tab-content ${activeClass}">
                    <div class="relationships-hierarchy">
                        <div class="hierarchy-header">
                            <div class="hierarchy-title">PRODUCT</div>
                            <div class="hierarchy-actions">
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-cog"></i><i class="fas fa-chevron-down" style="margin-left:.3rem;font-size:.7rem;"></i>
                                </button>
                            </div>
                        </div>
                        <div class="data-table-wrapper">
                            <table class="data-table">
                                <thead>
                                    <tr>
                                        <th><div class="th-content"><span>Relationship Type</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content"><span>Product</span><i class="fas fa-sort"></i></div></th>
                                        <th style="text-align: center;"><div class="th-content" style="justify-content: center;"><span>Product Owner</span><i class="fas fa-sort"></i></div></th>
                                    </tr>
                                </thead>
                                <tbody id="productImpactViewTableBody">
                                    ${renderProductViewTableBody()}
                                </tbody>
                            </table>
                        </div>
                        <div class="hierarchy-footer">
                            ${productRelationships.length} record${productRelationships.length !== 1 ? 's' : ''}
                        </div>
                    </div>
                </div>
            `;
        }

        // Client tab
        if (hasClientData) {
            const activeClass = !hasSystemData && !hasProcessData && !hasGlossaryData && !hasProductData ? 'active' : '';
            subTabsHtml += `<button class="sub-tab ${activeClass}" data-sub-tab="client">Client</button>`;
            subTabsContentHtml += `
                <div id="impactClientViewContent" class="sub-tab-content ${activeClass}">
                    <div class="relationships-hierarchy">
                        <div class="hierarchy-header">
                            <div class="hierarchy-title">CLIENT</div>
                            <div class="hierarchy-actions">
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-cog"></i><i class="fas fa-chevron-down" style="margin-left:.3rem;font-size:.7rem;"></i>
                                </button>
                            </div>
                        </div>
                        <div class="data-table-wrapper">
                            <table class="data-table">
                                <thead>
                                    <tr>
                                        <th><div class="th-content"><span>Relationship Type</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content"><span>Client</span><i class="fas fa-sort"></i></div></th>
                                        <th style="text-align: center;"><div class="th-content" style="justify-content: center;"><span>Client Owner</span><i class="fas fa-sort"></i></div></th>
                                    </tr>
                                </thead>
                                <tbody id="clientImpactViewTableBody">
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

        // Legal Entity tab
        if (hasLegalData) {
            const activeClass = !hasSystemData && !hasProcessData && !hasGlossaryData && !hasProductData && !hasClientData ? 'active' : '';
            subTabsHtml += `<button class="sub-tab ${activeClass}" data-sub-tab="legal">Legal Entity</button>`;
            subTabsContentHtml += `
                <div id="impactLegalViewContent" class="sub-tab-content ${activeClass}">
                    <div class="relationships-hierarchy">
                        <div class="hierarchy-header">
                            <div class="hierarchy-title">LEGAL ENTITY</div>
                            <div class="hierarchy-actions">
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-cog"></i><i class="fas fa-chevron-down" style="margin-left:.3rem;font-size:.7rem;"></i>
                                </button>
                            </div>
                        </div>
                        <div class="data-table-wrapper">
                            <table class="data-table">
                                <thead>
                                    <tr>
                                        <th><div class="th-content"><span>Relationship Type</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content"><span>Legal Entity</span><i class="fas fa-sort"></i></div></th>
                                        <th style="text-align: center;"><div class="th-content" style="justify-content: center;"><span>Legal Entity Owner</span><i class="fas fa-sort"></i></div></th>
                                    </tr>
                                </thead>
                                <tbody id="legalImpactViewTableBody">
                                    ${renderLegalViewTableBody()}
                                </tbody>
                            </table>
                        </div>
                        <div class="hierarchy-footer">
                            ${legalRelationships.length} record${legalRelationships.length !== 1 ? 's' : ''}
                        </div>
                    </div>
                </div>
            `;
        }

        // Business Area tab
        if (hasBusinessAreaData) {
            const activeClass = !hasSystemData && !hasProcessData && !hasGlossaryData && !hasProductData && !hasClientData && !hasLegalData ? 'active' : '';
            subTabsHtml += `<button class="sub-tab ${activeClass}" data-sub-tab="businessarea">Business Area</button>`;
            subTabsContentHtml += `
                <div id="impactBusinessAreaViewContent" class="sub-tab-content ${activeClass}">
                    <div class="relationships-hierarchy">
                        <div class="hierarchy-header">
                            <div class="hierarchy-title">BUSINESS AREA</div>
                            <div class="hierarchy-actions">
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-cog"></i><i class="fas fa-chevron-down" style="margin-left:.3rem;font-size:.7rem;"></i>
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
                                <tbody id="businessAreaImpactViewTableBody">
                                    ${renderBusinessAreaViewTableBody()}
                                </tbody>
                            </table>
                        </div>
                        <div class="hierarchy-footer">
                            ${businessAreaRelationships.length} record${businessAreaRelationships.length !== 1 ? 's' : ''}
                        </div>
                    </div>
                </div>
            `;
        }

        // Project tab
        if (hasProjectData) {
            const activeClass = !hasSystemData && !hasProcessData && !hasGlossaryData && !hasProductData && !hasClientData && !hasLegalData && !hasBusinessAreaData ? 'active' : '';
            subTabsHtml += `<button class="sub-tab ${activeClass}" data-sub-tab="project">Project</button>`;
            subTabsContentHtml += `
                <div id="impactProjectViewContent" class="sub-tab-content ${activeClass}">
                    <div class="relationships-hierarchy">
                        <div class="hierarchy-header">
                            <div class="hierarchy-title">PROJECT</div>
                            <div class="hierarchy-actions">
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-cog"></i><i class="fas fa-chevron-down" style="margin-left:.3rem;font-size:.7rem;"></i>
                                </button>
                            </div>
                        </div>
                        <div class="data-table-wrapper">
                            <table class="data-table">
                                <thead>
                                    <tr>
                                        <th><div class="th-content"><span>Relationship Type</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content"><span>Project</span><i class="fas fa-sort"></i></div></th>
                                        <th style="text-align: center;"><div class="th-content" style="justify-content: center;"><span>Ref.</span><i class="fas fa-sort"></i></div></th>
                                        <th style="text-align: center;"><div class="th-content" style="justify-content: center;"><span>Project Owner</span><i class="fas fa-sort"></i></div></th>
                                    </tr>
                                </thead>
                                <tbody id="projectImpactViewTableBody">
                                    ${renderProjectViewTableBody()}
                                </tbody>
                            </table>
                        </div>
                        <div class="hierarchy-footer">
                            ${projectRelationships.length} record${projectRelationships.length !== 1 ? 's' : ''}
                        </div>
                    </div>
                </div>
            `;
        }
        
        // Committee Reverse tab (Committees that impact this Capability)
        if (hasCommitteeReverse) {
            const activeClass = !hasSystemData && !hasProcessData && !hasGlossaryData && !hasProductData && !hasClientData && !hasLegalData && !hasBusinessAreaData && !hasProjectData ? 'active' : '';
            subTabsHtml += `<button class="sub-tab ${activeClass}" data-sub-tab="committee">Committee</button>`;
            subTabsContentHtml += `
                <div id="impactCommitteeReverseViewContent" class="sub-tab-content ${activeClass}">
                    <div class="relationships-hierarchy">
                        <div class="hierarchy-header">
                            <div class="hierarchy-title">COMMITTEE</div>
                            <div class="hierarchy-actions">
                                <button type="button" class="btn btn-secondary">
                                    <i class="fas fa-cog"></i><i class="fas fa-chevron-down" style="margin-left:.3rem;font-size:.7rem;"></i>
                                </button>
                            </div>
                        </div>
                        <div class="data-table-wrapper">
                            <table class="data-table">
                                <thead>
                                    <tr>
                                        <th><div class="th-content"><span>Relationship Type</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content"><span>Committee</span><i class="fas fa-sort"></i></div></th>
                                        <th style="text-align: center;"><div class="th-content" style="justify-content: center;"><span>Committee Owner</span><i class="fas fa-sort"></i></div></th>
                                    </tr>
                                </thead>
                                <tbody id="committeeReverseImpactViewTableBody">
                                    ${renderCommitteeReverseViewTableBody()}
                                </tbody>
                            </table>
                        </div>
                        <div class="hierarchy-footer">
                            ${committeeRelationshipsReverse.length} record${committeeRelationshipsReverse.length !== 1 ? 's' : ''}
                        </div>
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

    // Render product view table body
    function renderProductViewTableBody() {
        if (productRelationships.length === 0) {
            return '<tr><td colspan="3" style="text-align: center; padding: 2rem; color: var(--text-muted, #6b7280);">No product relationships found</td></tr>';
        }

        return productRelationships.map(relationship => {
            const productLink = createEntityLink('product', relationship.productId, relationship.productName);
            
            return `
            <tr>
                <td>${escapeHtml(relationship.relationTypeName || 'N/A')}</td>
                <td>
                    <div class="entity-info">
                        <span class="entity-name">${productLink}</span>
                        ${relationship.productRefNumber ? `<span class="entity-ref">${escapeHtml(relationship.productRefNumber)}</span>` : ''}
                    </div>
                </td>
                <td style="text-align: center;">
                    <div class="owner-info">
                        <div class="owner-name" style="font-weight: bold; color: #333;">${escapeHtml(relationship.productOwnerName || 'No owner')}</div>
                    </div>
                </td>
            </tr>
        `;
        }).join('');
    }

    // Render client view table body
    function renderClientViewTableBody() {
        if (clientRelationships.length === 0) {
            return '<tr><td colspan="3" style="text-align: center; padding: 2rem; color: var(--text-muted, #6b7280);">No client relationships found</td></tr>';
        }

        return clientRelationships.map(relationship => {
            const clientLink = createEntityLink('client', relationship.clientId, relationship.clientName);
            
            return `
            <tr>
                <td>${escapeHtml(relationship.relationTypeName || 'N/A')}</td>
                <td>
                    <div class="entity-info">
                        <span class="entity-name">${clientLink}</span>
                        ${relationship.clientRefNumber ? `<span class="entity-ref">${escapeHtml(relationship.clientRefNumber)}</span>` : ''}
                    </div>
                </td>
                <td style="text-align: center;">
                    <div class="owner-info">
                        <div class="owner-name" style="font-weight: bold; color: #333;">${escapeHtml(relationship.clientOwnerName || 'No owner')}</div>
                    </div>
                </td>
                <td>${escapeHtml(relationship.description || '')}</td>
            </tr>
        `;
        }).join('');
    }

    // Render project view table body
    function renderProjectViewTableBody() {
        if (projectRelationships.length === 0) {
            return '<tr><td colspan="4" style="text-align: center; padding: 2rem; color: var(--text-muted, #6b7280);">No project relationships found</td></tr>';
        }

        return projectRelationships.map(relationship => {
            const projectLink = createEntityLink('project', relationship.projectId, relationship.projectName);
            const refNumber = relationship.projectRef || relationship.projectRefNumber || '';
            
            return `
            <tr>
                <td>${escapeHtml(relationship.relationTypeName || 'N/A')}</td>
                <td>
                    <div class="entity-info">
                        <span class="entity-name">${projectLink}</span>
                    </div>
                </td>
                <td style="text-align: center;">${escapeHtml(refNumber)}</td>
                <td style="text-align: center;">
                    <div class="owner-info">
                        <div class="owner-name" style="font-weight: bold; color: #333;">${escapeHtml(relationship.projectOwnerName || 'No owner')}</div>
                    </div>
                </td>
            </tr>
        `;
        }).join('');
    }

    // Render legal view table body
    function renderLegalViewTableBody() {
        if (legalRelationships.length === 0) {
            return '<tr><td colspan="3" style="text-align: center; padding: 2rem; color: var(--text-muted, #6b7280);">No legal entity relationships found</td></tr>';
        }

        return legalRelationships.map(relationship => {
            const legalName = relationship.legalShortName || relationship.legalLongName || 'N/A';
            const legalLink = createEntityLink('legal', relationship.legalId, legalName);
            
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
                        <span class="owner-name">${escapeHtml(relationship.legalOwnerName || 'No owner')}</span>
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
            const refNumber = relationship.processRef || relationship.processRefNumber || '';
            
            return `
            <tr>
                <td>${escapeHtml(relationship.relationTypeName || 'N/A')}</td>
                <td>
                    <div class="entity-info">
                        <span class="entity-name">${processLink}</span>
                    </div>
                </td>
                <td style="text-align: center;">${escapeHtml(refNumber)}</td>
                <td style="text-align: center;">
                    <div class="owner-info">
                        <span class="owner-name" style="font-weight: bold; color: #333;">${escapeHtml(relationship.processOwnerName || 'No owner')}</span>
                    </div>
                </td>
            </tr>
        `;
        }).join('');
    }

    // Render business area view table body
    function renderBusinessAreaViewTableBody() {
        if (businessAreaRelationships.length === 0) {
            return '<tr><td colspan="2" style="text-align: center; padding: 2rem; color: var(--text-muted, #6b7280);">No business area relationships found</td></tr>';
        }

        return businessAreaRelationships.map(relationship => {
            const businessAreaLink = createEntityLink('businessarea', relationship.businessAreaId, relationship.businessAreaName);
            
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
    
    // Render committee reverse view table body
    function renderCommitteeReverseViewTableBody() {
        if (committeeRelationshipsReverse.length === 0) {
            return '<tr class="reverse-relationship-row"><td colspan="3" style="text-align: center; padding: 2rem; color: var(--text-muted, #6b7280);">No committee relationships found</td></tr>';
        }

        return committeeRelationshipsReverse.map(relationship => {
            // Use reverse name if available, otherwise use regular relation type name
            const relationTypeDisplay = relationship.relationTypeReverseName || relationship.relationTypeName || 'N/A';
            
            const committeeName = relationship.committeeName || 'N/A';
            const committeeLink = createEntityLink('committee', relationship.committeeId, committeeName);
            
            // Get owner name
            const ownerName = relationship.committeeOwnerName || 'No owner';
            
            return `
                <tr class="reverse-relationship-row">
                    <td>${escapeHtml(relationTypeDisplay)}</td>
                    <td>
                        <div class="entity-info reverse-relationship-entity">
                            <i class="fas fa-users" style="color: var(--text-secondary, #64748b); margin-right: 0.5rem;"></i>
                            <span class="entity-name">${committeeLink}</span>
                        </div>
                    </td>
                    <td style="text-align: center;">
                        <div class="owner-info reverse-relationship-owner">
                            <span class="owner-name">${escapeHtml(ownerName)}</span>
                        </div>
                    </td>
                </tr>
            `;
        }).join('');
    }

    // Render glossary view table body
    function renderGlossaryViewTableBody() {
        if (glossaryRelationships.length === 0) {
            return '<tr><td colspan="4" style="text-align: center; padding: 2rem; color: var(--text-muted, #6b7280);">No glossary relationships found</td></tr>';
        }

        return glossaryRelationships.map(relationship => {
            const glossaryName = relationship.glossaryName || 'N/A';
            const glossaryRefNumber = relationship.glossaryRefNumber || '';
            const displayName = glossaryRefNumber ? `${glossaryName} (${glossaryRefNumber})` : glossaryName;
            const glossaryLink = createEntityLink('glossary', relationship.glossaryId, displayName);
            
            return `
                <tr>
                    <td>${escapeHtml(relationship.relationTypeName || 'N/A')}</td>
                    <td>
                        <div class="entity-info">
                            <span class="entity-name">${glossaryLink}</span>
                        </div>
                    </td>
                    <td style="text-align: center;">${escapeHtml(relationship.glossaryTypeName || relationship.glossaryType || 'N/A')}</td>
                    <td style="text-align: center;">
                        <div class="owner-info">
                            <div class="owner-name" style="font-weight: bold; color: #333;">${escapeHtml(relationship.glossaryOwnerName || 'No owner')}</div>
                        </div>
                    </td>
                </tr>
            `;
        }).join('');
    }

    // Setup Impact view sub-tabs
    function setupImpactViewSubTabs() {
        console.log('Setting up Impact view sub-tabs...');
        
        const subTabs = document.querySelectorAll('#capabilityImpactContainer .sub-tab');
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
                const subTabContents = document.querySelectorAll('#capabilityImpactContainer .sub-tab-content');
                subTabContents.forEach(function(content) {
                    content.classList.remove('active');
                    content.style.display = 'none';
                });
                
                // Show selected sub-tab content
                let targetSubTab;
                if (subTabName === 'system') {
                    targetSubTab = document.getElementById('impactSystemViewContent');
                } else if (subTabName === 'product') {
                    targetSubTab = document.getElementById('impactProductViewContent');
                } else if (subTabName === 'client') {
                    targetSubTab = document.getElementById('impactClientViewContent');
                } else if (subTabName === 'project') {
                    targetSubTab = document.getElementById('impactProjectViewContent');
                } else if (subTabName === 'legal') {
                    targetSubTab = document.getElementById('impactLegalViewContent');
                } else if (subTabName === 'glossary') {
                    targetSubTab = document.getElementById('impactGlossaryViewContent');
                } else if (subTabName === 'process') {
                    targetSubTab = document.getElementById('impactProcessViewContent');
                } else if (subTabName === 'businessarea') {
                    targetSubTab = document.getElementById('impactBusinessAreaViewContent');
                } else if (subTabName === 'committee') {
                    targetSubTab = document.getElementById('impactCommitteeReverseViewContent');
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

    // Expose function globally for use by capability.js

    window.loadCapabilityImpact = loadCapabilityImpact;
    
    // Also expose as initImpactView for compatibility
    window.initCapabilityImpactView = function(capabilityId, container) {
        if (container) {
            // If container is provided, ensure it's set up
            container.id = 'capabilityImpactContainer';
        }
        loadCapabilityImpact(capabilityId);
        return Promise.resolve();
    };

})();

