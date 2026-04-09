// Policy Impact View JavaScript - Read-only display for Product, Client, Process, and Project sub-tabs
(function() {
    let currentPolicyId = null;
    let productRelationships = [];
    let clientRelationships = [];
    let processRelationships = [];
    let projectRelationships = [];
    let systemRelationships = [];
    let businessAreaRelationships = [];
    let legalRelationships = [];
    let datasetRelationships = [];
    let attributeRelationships = [];
    let glossaryRelationships = [];

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
            case 'client':
                url = `/view/client/${encodeURIComponent(id)}`;
                break;
            case 'process':
                url = `/view/process/${encodeURIComponent(id)}`;
                break;
            case 'project':
                url = `/view/project/${encodeURIComponent(id)}`;
                break;
            case 'system':
                url = `/view/system/${encodeURIComponent(id)}`;
                break;
            case 'businessarea':
                url = `/view/business-area/business-area.html?id=${encodeURIComponent(id)}`;
                break;
            case 'legal':
                url = `/view/LegalEntity/${encodeURIComponent(id)}`;
                break;
            case 'dataset':
                url = `/view/dataset/${encodeURIComponent(id)}`;
                break;
            case 'glossary':
                url = `/view/glossary/${encodeURIComponent(id)}`;
                break;
            case 'regulation':
                url = `/view/regulation/${encodeURIComponent(id)}`;
                break;
            default:
                return escapeHtml(name);
        }
        
        const viewText = window.I18n?.t('policyImpact.messages.view') || 'View';
        return `<a href="${url}" class="entity-link" style="color: var(--secondary-color, #248567); text-decoration: none;" title="${viewText} ${escapeHtml(name)}">${escapeHtml(name)}</a>`;
    }

    // Initialize Impact view
    function loadPolicyImpact(policyId) {
        currentPolicyId = policyId;
        console.log('Loading Policy Impact for policy:', policyId);
        loadImpactViewData();
    }

    // Reverse relationship arrays
    let regulationRelationshipsReverse = [];
    
    // Load all Impact view data
    async function loadImpactViewData() {
        try {
            // Load relationship data (forward and reverse)
            await Promise.all([
                loadProductRelationshipsView(),
                loadClientRelationshipsView(),
                loadProcessRelationshipsView(),
                loadProjectRelationshipsView(),
                loadSystemRelationshipsView(),
                loadBusinessAreaRelationshipsView(),
                loadLegalRelationshipsView(),
                loadDatasetRelationshipsView(),
                loadAttributeRelationshipsView(),
                loadGlossaryRelationshipsView(),
                loadRegulationRelationshipsReverse()
            ]);

            // Render the view
            renderImpactView();

        } catch (error) {
            console.error('Error loading Impact view data:', error);
        }
    }

    // Load product relationships for view
    async function loadProductRelationshipsView() {
        try {
            console.log('Loading product relationships for view, policy:', currentPolicyId);

            const response = await fetch(`/api/policy-impact/${currentPolicyId}/products`, {
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
            
            // Debug each relationship to see what fields are available
            if (Array.isArray(data)) {
                data.forEach((rel, index) => {
                    console.log(`Product relationship ${index}:`, {
                        productOwnerName: rel.productOwnerName,
                        productOwnerEmail: rel.productOwnerEmail,
                        productName: rel.productName
                    });
                });
            }

            productRelationships = Array.isArray(data) ? data : [];

        } catch (error) {
            console.error('Error loading product relationships for view:', error);
            productRelationships = [];
        }
    }

    // Load client relationships for view
    async function loadClientRelationshipsView() {
        try {
            console.log('Loading client relationships for view, policy:', currentPolicyId);

            const response = await fetch(`/api/policy-impact/${currentPolicyId}/clients`, {
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

    // Load process relationships for view
    async function loadProcessRelationshipsView() {
        try {
            console.log('Loading process relationships for view, policy:', currentPolicyId);

            const response = await fetch(`/api/policy-impact/${currentPolicyId}/processes`, {
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

    // Load project relationships for view
    async function loadProjectRelationshipsView() {
        try {
            console.log('Loading project relationships for view, policy:', currentPolicyId);

            const response = await fetch(`/api/policy-impact/${currentPolicyId}/projects`, {
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

    // Load system relationships for view
    async function loadSystemRelationshipsView() {
        try {
            console.log('Loading system relationships for view, policy:', currentPolicyId);

            const response = await fetch(`/api/policy-impact/${currentPolicyId}/systems`, {
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

    // Load business area relationships for view
    async function loadBusinessAreaRelationshipsView() {
        try {
            console.log('Loading business area relationships for view, policy:', currentPolicyId);

            const response = await fetch(`/api/policy-impact/${currentPolicyId}/businessareas`, {
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

    // Load legal relationships for view
    async function loadLegalRelationshipsView() {
        try {
            console.log('Loading legal relationships for view, policy:', currentPolicyId);

            const response = await fetch(`/api/policy-impact/${currentPolicyId}/legals`, {
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

    // Load dataset relationships for view
    async function loadDatasetRelationshipsView() {
        try {
            console.log('Loading dataset relationships for view, policy:', currentPolicyId);

            const response = await fetch(`/api/policy-impact/${currentPolicyId}/datasets`, {
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
            console.log('Dataset relationships view API response:', data);

            datasetRelationships = Array.isArray(data) ? data : [];

        } catch (error) {
            console.error('Error loading dataset relationships for view:', error);
            datasetRelationships = [];
        }
    }

    // Load attribute relationships for view
    async function loadAttributeRelationshipsView() {
        try {
            console.log('Loading attribute relationships for view, policy:', currentPolicyId);

            const response = await fetch(`/api/policy-impact/${currentPolicyId}/attributes`, {
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
            console.log('Attribute relationships view API response:', data);

            attributeRelationships = Array.isArray(data) ? data : [];

        } catch (error) {
            console.error('Error loading attribute relationships for view:', error);
            attributeRelationships = [];
        }
    }

    // Load glossary relationships for view
    async function loadGlossaryRelationshipsView() {
        try {
            console.log('Loading glossary relationships for view, policy:', currentPolicyId);

            const response = await fetch(`/api/policy-impact/${currentPolicyId}/glossaries`, {
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
    
    async function loadRegulationRelationshipsReverse() {
        try {
            const response = await fetch(`/api/regulation-impact/policies/${currentPolicyId}/regulations`, {
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

    // Render Impact view with sub-tabs
    function renderImpactView() {
        const container = document.getElementById('policyImpactContainer');
        if (!container) {
            console.error('policyImpactContainer not found');
            return;
        }

        // Determine which sub-tabs to show based on data availability
        const hasProductData = productRelationships.length > 0;
        const hasClientData = clientRelationships.length > 0;
        const hasProcessData = processRelationships.length > 0;
        const hasProjectData = projectRelationships.length > 0;
        const hasSystemData = systemRelationships.length > 0;
        const hasBusinessAreaData = businessAreaRelationships.length > 0;
        const hasLegalData = legalRelationships.length > 0;
        const hasDatasetData = datasetRelationships.length > 0;
        const hasAttributeData = attributeRelationships.length > 0;
        const hasGlossaryData = glossaryRelationships.length > 0;
        const hasRegulationReverse = regulationRelationshipsReverse.length > 0;

        if (!hasProductData && !hasClientData && !hasProcessData && !hasProjectData && !hasSystemData && !hasBusinessAreaData && !hasLegalData && !hasDatasetData && !hasAttributeData && !hasGlossaryData && !hasRegulationReverse) {
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
                <div id="impactProductViewContent" class="sub-tab-content active">
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
                                        <th><div class="th-content"><span>Ref.</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content"><span>Product Owner</span><i class="fas fa-sort"></i></div></th>
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

        if (hasClientData) {
            // Only make client tab active if there's no product data
            const activeClass = !hasProductData ? 'active' : '';
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
                                        <th><div class="th-content"><span>Client Owner</span><i class="fas fa-sort"></i></div></th>
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

        if (hasProcessData) {
            // Only make process tab active if there's no product or client data
            const activeClass = !hasProductData && !hasClientData ? 'active' : '';
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
                                        <th><div class="th-content"><span>Name</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content"><span>Ref.</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content"><span>Process Owner</span><i class="fas fa-sort"></i></div></th>
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

        if (hasProjectData) {
            // Only make project tab active if there's no product, client, or process data
            const activeClass = !hasProductData && !hasClientData && !hasProcessData ? 'active' : '';
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
                                        <th><div class="th-content"><span>Ref.</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content"><span>Project Owner</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content"><span>Description</span><i class="fas fa-sort"></i></div></th>
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

        if (hasSystemData) {
            // Only make system tab active if there's no data in previous sub-tabs
            const activeClass = !hasProductData && !hasClientData && !hasProcessData && !hasProjectData ? 'active' : '';
            subTabsHtml += `<button class="sub-tab ${activeClass}" data-sub-tab="system">System</button>`;
            subTabsContentHtml += `
                <div id="impactSystemViewContent" class="sub-tab-content ${activeClass}">
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
                                        <th><div class="th-content"><span>System Owner</span><i class="fas fa-sort"></i></div></th>
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

        if (hasBusinessAreaData) {
            // Only make business area tab active if there's no data in previous sub-tabs
            const activeClass = !hasProductData && !hasClientData && !hasProcessData && !hasProjectData && !hasSystemData ? 'active' : '';
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
                                        <th><div class="th-content"><span>Business</span><i class="fas fa-sort"></i></div></th>
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

        if (hasLegalData) {
            // Only make legal tab active if there's no data in previous sub-tabs
            const activeClass = !hasProductData && !hasClientData && !hasProcessData && !hasProjectData && !hasSystemData && !hasBusinessAreaData ? 'active' : '';
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
                                        <th><div class="th-content"><span>Legal Entity Name</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content"><span>Legal Entity Owner</span><i class="fas fa-sort"></i></div></th>
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

        if (hasDatasetData) {
            // Only make dataset tab active if there's no data in previous sub-tabs
            const activeClass = !hasProductData && !hasClientData && !hasProcessData && !hasProjectData && !hasSystemData && !hasBusinessAreaData && !hasLegalData ? 'active' : '';
            subTabsHtml += `<button class="sub-tab ${activeClass}" data-sub-tab="dataset">Data Set</button>`;
            subTabsContentHtml += `
                <div id="impactDatasetViewContent" class="sub-tab-content ${activeClass}">
                    <div class="relationships-hierarchy">
                        <div class="hierarchy-header">
                            <div class="hierarchy-title">DATA SET</div>
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
                                        <th><div class="th-content"><span>System</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content"><span>Data Set</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content"><span>Data Set Owner</span><i class="fas fa-sort"></i></div></th>
                                    </tr>
                                </thead>
                                <tbody id="datasetImpactViewTableBody">
                                    ${renderDatasetViewTableBody()}
                                </tbody>
                            </table>
                        </div>
                        <div class="hierarchy-footer">
                            ${datasetRelationships.length} record${datasetRelationships.length !== 1 ? 's' : ''}
                        </div>
                    </div>
                </div>
            `;
        }

        if (hasAttributeData) {
            // Only make attribute tab active if there's no data in previous sub-tabs
            const activeClass = !hasProductData && !hasClientData && !hasProcessData && !hasProjectData && !hasSystemData && !hasBusinessAreaData && !hasLegalData && !hasDatasetData ? 'active' : '';
            subTabsHtml += `<button class="sub-tab ${activeClass}" data-sub-tab="attribute">Data Attributes</button>`;
            subTabsContentHtml += `
                <div id="impactAttributeViewContent" class="sub-tab-content ${activeClass}">
                    <div class="relationships-hierarchy">
                        <div class="hierarchy-header">
                            <div class="hierarchy-title">DATA ATTRIBUTES</div>
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
                                        <th><div class="th-content"><span>System</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content"><span>Data Set</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content"><span>Data Attributes</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content"><span>Attribute Owner</span><i class="fas fa-sort"></i></div></th>
                                    </tr>
                                </thead>
                                <tbody id="attributeImpactViewTableBody">
                                    ${renderAttributeViewTableBody()}
                                </tbody>
                            </table>
                        </div>
                        <div class="hierarchy-footer">
                            ${attributeRelationships.length} record${attributeRelationships.length !== 1 ? 's' : ''}
                        </div>
                    </div>
                </div>
            `;
        }

        if (hasGlossaryData) {
            // Only make glossary tab active if there's no data in previous sub-tabs
            const activeClass = !hasProductData && !hasClientData && !hasProcessData && !hasProjectData && !hasSystemData && !hasBusinessAreaData && !hasLegalData && !hasDatasetData && !hasAttributeData ? 'active' : '';
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
                                        <th><div class="th-content"><span>Type</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content"><span>Strategic Owner</span><i class="fas fa-sort"></i></div></th>
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
        
        if (hasRegulationReverse) {
            const activeClass = !hasProductData && !hasClientData && !hasProcessData && !hasProjectData && !hasSystemData && !hasBusinessAreaData && !hasLegalData && !hasDatasetData && !hasAttributeData && !hasGlossaryData ? 'active' : '';
            subTabsHtml += `<button class="sub-tab ${activeClass}" data-sub-tab="regulation">Regulation</button>`;
            subTabsContentHtml += `
                <div id="impactRegulationReverseViewContent" class="sub-tab-content ${activeClass}">
                    <div class="relationships-hierarchy">
                        <div class="hierarchy-header">
                            <div class="hierarchy-title">REGULATION</div>
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
                                        <th><div class="th-content"><span>Regulation</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content"><span>Regulation Owner</span><i class="fas fa-sort"></i></div></th>
                                    </tr>
                                </thead>
                                <tbody>${renderRegulationReverseViewTableBody()}</tbody>
                            </table>
                        </div>
                        <div class="hierarchy-footer">${regulationRelationshipsReverse.length} record${regulationRelationshipsReverse.length !== 1 ? 's' : ''}</div>
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

    // Render product view table body
    function renderProductViewTableBody() {
        if (productRelationships.length === 0) {
            return '<tr><td colspan="4" style="text-align: center; padding: 2rem; color: var(--text-muted, #6b7280);">No product relationships found</td></tr>';
        }

        return productRelationships.map(relationship => {
            console.log('Rendering product relationship:', {
                productOwnerName: relationship.productOwnerName,
                productOwnerEmail: relationship.productOwnerEmail,
                productName: relationship.productName
            });
            
            const productLink = createEntityLink('product', relationship.productId, relationship.productName);
            
            return `
            <tr>
                <td>${escapeHtml(relationship.relationTypeName || 'N/A')}</td>
                <td>
                    <div class="entity-info">
                        <span class="entity-name">${productLink}</span>
                    </div>
                </td>
                <td>
                    ${relationship.productRefNumber ? `<span class="entity-ref">${escapeHtml(relationship.productRefNumber)}</span>` : '<span class="empty">-</span>'}
                </td>
                <td>
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
                <td>
                    <div class="owner-info">
                        <div class="owner-name" style="font-weight: bold; color: #333;">${escapeHtml(relationship.clientOwnerName || 'No owner')}</div>
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
                <td>
                    <div class="entity-ref">${escapeHtml(relationship.processRefNumber || 'N/A')}</div>
                </td>
                <td>
                    <div class="owner-info">
                        <div class="owner-name" style="font-weight: bold; color: #333;">${escapeHtml(relationship.processOwnerName || 'No owner')}</div>
                    </div>
                </td>
            </tr>
        `;
        }).join('');
    }

    // Render project view table body
    function renderProjectViewTableBody() {
        if (projectRelationships.length === 0) {
            return '<tr><td colspan="5" style="text-align: center; padding: 2rem; color: var(--text-muted, #6b7280);">No project relationships found</td></tr>';
        }

        return projectRelationships.map(relationship => {
            const projectLink = createEntityLink('project', relationship.projectId, relationship.projectName);
            
            return `
            <tr>
                <td>${escapeHtml(relationship.relationTypeName || 'N/A')}</td>
                <td>
                    <div class="entity-info">
                        <span class="entity-name">${projectLink}</span>
                    </div>
                </td>
                <td>
                    <div class="entity-ref">${escapeHtml(relationship.projectRefNumber || 'N/A')}</div>
                </td>
                <td>
                    <div class="owner-info">
                        <div class="owner-name" style="font-weight: bold; color: #333;">${escapeHtml(relationship.projectOwnerName || 'No owner')}</div>
                    </div>
                </td>
                <td>
                    <div class="description">${escapeHtml(relationship.description || 'N/A')}</div>
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
                <td>
                    <div class="owner-info">
                        <span class="owner-name">${escapeHtml(relationship.systemOwnerName || 'No owner')}</span>
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
                <td>
                    <div class="owner-info">
                        <span class="owner-name">${escapeHtml(relationship.legalOwnerName || 'No owner')}</span>
                    </div>
                </td>
            </tr>
        `;
        }).join('');
    }

    // Render dataset view table body
    function renderDatasetViewTableBody() {
        if (datasetRelationships.length === 0) {
            return '<tr><td colspan="4" style="text-align: center; padding: 2rem; color: var(--text-muted, #6b7280);">No dataset relationships found</td></tr>';
        }

        return datasetRelationships.map(relationship => {
            const systemLink = createEntityLink('system', relationship.systemId, relationship.systemName);
            const datasetLink = createEntityLink('dataset', relationship.datasetId, relationship.datasetName);
            
            return `
            <tr>
                <td>${escapeHtml(relationship.relationTypeName || 'N/A')}</td>
                <td>
                    <div class="entity-info">
                        <span class="entity-name">${systemLink}</span>
                    </div>
                </td>
                <td>
                    <div class="entity-info">
                        <span class="entity-name">${datasetLink}</span>
                    </div>
                </td>
                <td>
                    <div class="owner-info">
                        <div class="owner-name" style="font-weight: bold; color: #333;">${escapeHtml(relationship.datasetOwnerName || 'No owner')}</div>
                    </div>
                </td>
            </tr>
        `;
        }).join('');
    }

    // Render attribute view table body
    function renderAttributeViewTableBody() {
        if (attributeRelationships.length === 0) {
            return '<tr><td colspan="5" style="text-align: center; padding: 2rem; color: var(--text-muted, #6b7280);">No attribute relationships found</td></tr>';
        }

        return attributeRelationships.map(relationship => {
            const systemLink = createEntityLink('system', relationship.systemId, relationship.systemName);
            const datasetLink = createEntityLink('dataset', relationship.datasetId, relationship.datasetName);
            // Attributes might not have a direct view page, so just show as text for now
            const attributeName = escapeHtml(relationship.attributeName || 'N/A');
            
            return `
            <tr>
                <td>${escapeHtml(relationship.relationTypeName || 'N/A')}</td>
                <td>
                    <div class="entity-info">
                        <span class="entity-name">${systemLink}</span>
                    </div>
                </td>
                <td>
                    <div class="entity-info">
                        <span class="entity-name">${datasetLink}</span>
                    </div>
                </td>
                <td>
                    <div class="entity-info">
                        <span class="entity-name">${attributeName}</span>
                    </div>
                </td>
                <td>
                    <div class="owner-info">
                        <div class="owner-name" style="font-weight: bold; color: #333;">${escapeHtml(relationship.attributeOwnerName || 'No owner')}</div>
                    </div>
                </td>
            </tr>
        `;
        }).join('');
    }

    function renderGlossaryViewTableBody() {
        if (glossaryRelationships.length === 0) {
            return '<tr><td colspan="4" style="text-align: center; padding: 2rem; color: var(--text-muted, #6b7280);">No glossary relationships found</td></tr>';
        }

        return glossaryRelationships.map(relationship => {
            // Format glossary name with ref number if available
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
                    <td>
                        <div class="entity-info">
                            <span class="entity-name">${escapeHtml(relationship.glossaryTypeName || 'N/A')}</span>
                        </div>
                    </td>
                    <td>
                        <div class="owner-info">
                            <div class="owner-name" style="font-weight: bold; color: #333;">${escapeHtml(relationship.glossaryOwnerName || 'No owner')}</div>
                        </div>
                    </td>
                </tr>
            `;
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
            const regulationLink = createEntityLink('regulation', regulationId, regulationName);
            const ownerName = rel.regulationOwnerName || 'No owner';
            return `<tr class="reverse-relationship-row"><td>${relationTypeName}</td><td><div class="entity-info reverse-relationship-entity"><i class="fas fa-gavel" style="color: var(--text-secondary, #64748b); margin-right: 0.5rem;"></i><span class="entity-name">${regulationLink}</span></div></td><td style="text-align: center;"><div class="owner-info reverse-relationship-owner"><span class="owner-name">${escapeHtml(ownerName)}</span></div></td></tr>`;
        }).join('');
    }

    // Setup Impact view sub-tabs
    function setupImpactViewSubTabs() {
        console.log('Setting up Impact view sub-tabs...');
        
        const subTabs = document.querySelectorAll('#policyImpactContainer .sub-tab');
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
                const subTabContents = document.querySelectorAll('#policyImpactContainer .sub-tab-content');
                subTabContents.forEach(function(content) {
                    content.classList.remove('active');
                    content.style.display = 'none';
                });
                
                // Show selected sub-tab content
                let targetSubTab;
                if (subTabName === 'product') {
                    targetSubTab = document.getElementById('impactProductViewContent');
                    console.log('Found product view content:', !!targetSubTab);
                } else if (subTabName === 'client') {
                    targetSubTab = document.getElementById('impactClientViewContent');
                    console.log('Found client view content:', !!targetSubTab);
                } else if (subTabName === 'process') {
                    targetSubTab = document.getElementById('impactProcessViewContent');
                    console.log('Found process view content:', !!targetSubTab);
                } else if (subTabName === 'project') {
                    targetSubTab = document.getElementById('impactProjectViewContent');
                    console.log('Found project view content:', !!targetSubTab);
                } else if (subTabName === 'system') {
                    targetSubTab = document.getElementById('impactSystemViewContent');
                    console.log('Found system view content:', !!targetSubTab);
                } else if (subTabName === 'businessarea') {
                    targetSubTab = document.getElementById('impactBusinessAreaViewContent');
                    console.log('Found business area view content:', !!targetSubTab);
                } else if (subTabName === 'legal') {
                    targetSubTab = document.getElementById('impactLegalViewContent');
                    console.log('Found legal view content:', !!targetSubTab);
                } else if (subTabName === 'dataset') {
                    targetSubTab = document.getElementById('impactDatasetViewContent');
                    console.log('Found dataset view content:', !!targetSubTab);
                } else if (subTabName === 'attribute') {
                    targetSubTab = document.getElementById('impactAttributeViewContent');
                    console.log('Found attribute view content:', !!targetSubTab);
                } else if (subTabName === 'glossary') {
                    targetSubTab = document.getElementById('impactGlossaryViewContent');
                    console.log('Found glossary view content:', !!targetSubTab);
                } else if (subTabName === 'regulation') {
                    targetSubTab = document.getElementById('impactRegulationReverseViewContent');
                    console.log('Found regulation reverse view content:', !!targetSubTab);
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

    // Expose function globally for use by policy.js
    window.loadPolicyImpact = loadPolicyImpact;

})();
