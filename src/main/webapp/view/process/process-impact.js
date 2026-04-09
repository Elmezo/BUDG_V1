// Process Impact View JavaScript - Read-only display for System, Product, Client, Project, Policy, Interface, Legal, Dataset, Attribute, and Glossary sub-tabs
(function() {
    let currentProcessId = null;
    let currentViewMode = 'original'; // 'original' | 'changes'
    let systemRelationships = [];
    let productRelationships = [];
    let clientRelationships = [];
    let projectRelationships = [];
    let policyRelationships = [];
    let interfaceRelationships = [];
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
            case 'policy':
                url = `/view/policy/${encodeURIComponent(id)}`;
                break;
            case 'interface':
                url = `/view/system-interface/${encodeURIComponent(id)}`;
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
            case 'businessarea':
                url = `/view/business-area/${encodeURIComponent(id)}`;
                break;
            default:
                return escapeHtml(name);
        }
        
        const viewText = window.I18n?.t('processImpact.messages.view') || 'View';
        return `<a href="${url}" class="entity-link" style="color: var(--secondary-color, #248567); text-decoration: none;" title="${viewText} ${escapeHtml(name)}">${escapeHtml(name)}</a>`;
    }

    // Initialize Impact view
    function loadProcessImpact(processId, viewMode = 'original') {
        currentProcessId = processId;
        currentViewMode = viewMode || 'original';
        console.log('Loading Process Impact for process:', processId);
        loadImpactViewData();
    }

    function getViewParam() {
        return currentViewMode === 'changes' ? '?view=changes' : '';
    }

    // Reverse relationship arrays
    let systemRelationshipsReverse = [];
    let glossaryRelationshipsReverse = [];
    let businessAreaRelationshipsReverse = [];
    let capabilityRelationshipsReverse = [];
    
    // Load all Impact view data
    async function loadImpactViewData() {
        try {
            // Load relationship data (forward and reverse)
            await Promise.all([
                loadSystemRelationshipsView(),
                loadProductRelationshipsView(),
                loadClientRelationshipsView(),
                loadProjectRelationshipsView(),
                loadPolicyRelationshipsView(),
                loadInterfaceRelationshipsView(),
                loadLegalRelationshipsView(),
                loadDatasetRelationshipsView(),
                loadAttributeRelationshipsView(),
                loadGlossaryRelationshipsView(),
                loadSystemRelationshipsReverse(),
                loadGlossaryRelationshipsReverse(),
                loadBusinessAreaRelationshipsReverse(),
                loadCapabilityRelationshipsReverse()
            ]);

            // Render the view
            renderImpactView();

        } catch (error) {
            console.error('Error loading Impact view data:', error);
        }
    }
    
    // Load reverse relationships
    // Note: These show which Systems and Glossaries impact this Process
    async function loadSystemRelationshipsReverse() {
        // Reverse lookup: Systems that impact this Process
        // Note: /api/system-impact/processes/{processId}/systems does not exist in the backend
        // Keeping structure for future implementation
        systemRelationshipsReverse = [];
    }
    
    async function loadGlossaryRelationshipsReverse() {
        // Reverse lookup: Glossaries that impact this Process
        try {
            console.log('Loading glossary reverse relationships for process:', currentProcessId);
            
            const response = await fetch(`/api/glossary-impact/processes/${currentProcessId}/glossaries`, {
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
            console.log('Glossary reverse relationships API response:', data);
            
            glossaryRelationshipsReverse = Array.isArray(data) ? data : [];
            
        } catch (error) {
            console.error('Error loading glossary reverse relationships:', error);
            glossaryRelationshipsReverse = [];
        }
    }
    
    async function loadBusinessAreaRelationshipsReverse() {
        // Reverse lookup: Business Areas that impact this Process
        try {
            console.log('Loading business area reverse relationships for process:', currentProcessId);
            
            const response = await fetch(`/api/businessarea-impact/processes/${currentProcessId}/businessareas`, {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });
            
            if (!response.ok) {
                businessAreaRelationshipsReverse = [];
                return;
            }
            
            const data = await response.json();
            console.log('Business Area reverse relationships API response:', data);
            
            businessAreaRelationshipsReverse = Array.isArray(data) ? data : [];
            
        } catch (error) {
            console.error('Error loading business area reverse relationships:', error);
            businessAreaRelationshipsReverse = [];
        }
    }
    
    async function loadCapabilityRelationshipsReverse() {
        // Reverse lookup: Capabilities that impact this Process
        try {
            console.log('Loading capability reverse relationships for process:', currentProcessId);
            
            const response = await fetch(`/api/capability-impact/processes/${currentProcessId}/capabilities`, {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });
            
            if (!response.ok) {
                capabilityRelationshipsReverse = [];
                return;
            }
            
            const data = await response.json();
            console.log('Capability reverse relationships API response:', data);
            
            capabilityRelationshipsReverse = Array.isArray(data) ? data : [];
            
        } catch (error) {
            console.error('Error loading capability reverse relationships:', error);
            capabilityRelationshipsReverse = [];
        }
    }

    // Load system relationships for view
    async function loadSystemRelationshipsView() {
        try {
            console.log('Loading system relationships for view, process:', currentProcessId);

            const response = await fetch(`/api/process-impact/${currentProcessId}/systems${getViewParam()}`, {
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
            console.log('Loading product relationships for view, process:', currentProcessId);

            const response = await fetch(`/api/process-impact/${currentProcessId}/products${getViewParam()}`, {
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
            console.log('Loading client relationships for view, process:', currentProcessId);

            const response = await fetch(`/api/process-impact/${currentProcessId}/clients${getViewParam()}`, {
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
            console.log('Loading project relationships for view, process:', currentProcessId);

            const response = await fetch(`/api/process-impact/${currentProcessId}/projects${getViewParam()}`, {
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

    // Load policy relationships for view
    async function loadPolicyRelationshipsView() {
        try {
            console.log('Loading policy relationships for view, process:', currentProcessId);

            const response = await fetch(`/api/process-impact/${currentProcessId}/policies${getViewParam()}`, {
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
            console.log('Policy relationships view API response:', data);

            policyRelationships = Array.isArray(data) ? data : [];

        } catch (error) {
            console.error('Error loading policy relationships for view:', error);
            policyRelationships = [];
        }
    }

    // Load interface relationships for view
    async function loadInterfaceRelationshipsView() {
        try {
            console.log('Loading interface relationships for view, process:', currentProcessId);

            const response = await fetch(`/api/process-impact/${currentProcessId}/interfaces${getViewParam()}`, {
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
            console.log('Interface relationships view API response:', data);

            interfaceRelationships = Array.isArray(data) ? data : [];

        } catch (error) {
            console.error('Error loading interface relationships for view:', error);
            interfaceRelationships = [];
        }
    }

    // Load legal relationships for view
    async function loadLegalRelationshipsView() {
        try {
            console.log('Loading legal relationships for view, process:', currentProcessId);

            const response = await fetch(`/api/process-impact/${currentProcessId}/legals${getViewParam()}`, {
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
            console.log('Loading dataset relationships for view, process:', currentProcessId);

            const response = await fetch(`/api/process-impact/${currentProcessId}/datasets${getViewParam()}`, {
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
            console.log('Loading attribute relationships for view, process:', currentProcessId);

            const response = await fetch(`/api/process-impact/${currentProcessId}/attributes${getViewParam()}`, {
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
            console.log('Loading glossary relationships for view, process:', currentProcessId);

            const response = await fetch(`/api/process-impact/${currentProcessId}/glossaries${getViewParam()}`, {
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
        const container = document.getElementById('processImpactContainer');
        if (!container) {
            console.error('processImpactContainer not found');
            return;
        }

        // Determine which sub-tabs to show based on data availability
        const hasSystemData = systemRelationships.length > 0;
        const hasProductData = productRelationships.length > 0;
        const hasClientData = clientRelationships.length > 0;
        const hasProjectData = projectRelationships.length > 0;
        const hasPolicyData = policyRelationships.length > 0;
        const hasInterfaceData = interfaceRelationships.length > 0;
        const hasLegalData = legalRelationships.length > 0;
        const hasDatasetData = datasetRelationships.length > 0;
        const hasAttributeData = attributeRelationships.length > 0;
        const hasGlossaryData = glossaryRelationships.length > 0;
        
        // Reverse relationships
        const hasGlossaryReverseData = glossaryRelationshipsReverse.length > 0;
        const hasBusinessAreaReverseData = businessAreaRelationshipsReverse.length > 0;
        const hasCapabilityReverseData = capabilityRelationshipsReverse.length > 0;

        if (!hasSystemData && !hasProductData && !hasClientData && !hasProjectData && !hasPolicyData && !hasInterfaceData && !hasLegalData && !hasDatasetData && !hasAttributeData && !hasGlossaryData && !hasGlossaryReverseData && !hasBusinessAreaReverseData && !hasCapabilityReverseData) {
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

        // Product tab
        if (hasProductData) {
            const activeClass = !hasSystemData ? 'active' : '';
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
                                        <th><div class="th-content"><span>Ref.</span><i class="fas fa-sort"></i></div></th>
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
            const activeClass = !hasSystemData && !hasProductData ? 'active' : '';
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

        // Project tab
        if (hasProjectData) {
            const activeClass = !hasSystemData && !hasProductData && !hasClientData ? 'active' : '';
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

        // Policy tab
        if (hasPolicyData) {
            const activeClass = !hasSystemData && !hasProductData && !hasClientData && !hasProjectData ? 'active' : '';
            subTabsHtml += `<button class="sub-tab ${activeClass}" data-sub-tab="policy">Policy</button>`;
            subTabsContentHtml += `
                <div id="impactPolicyViewContent" class="sub-tab-content ${activeClass}">
                    <div class="relationships-hierarchy">
                        <div class="hierarchy-header">
                            <div class="hierarchy-title">POLICY</div>
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
                                        <th><div class="th-content"><span>Policy</span><i class="fas fa-sort"></i></div></th>
                                        <th style="text-align: center;"><div class="th-content" style="justify-content: center;"><span>Ref.</span><i class="fas fa-sort"></i></div></th>
                                        <th style="text-align: center;"><div class="th-content" style="justify-content: center;"><span>Policy Type</span><i class="fas fa-sort"></i></div></th>
                                        <th style="text-align: center;"><div class="th-content" style="justify-content: center;"><span>Policy Owner</span><i class="fas fa-sort"></i></div></th>
                                    </tr>
                                </thead>
                                <tbody id="policyImpactViewTableBody">
                                    ${renderPolicyViewTableBody()}
                                </tbody>
                            </table>
                        </div>
                        <div class="hierarchy-footer">
                            ${policyRelationships.length} record${policyRelationships.length !== 1 ? 's' : ''}
                        </div>
                    </div>
                </div>
            `;
        }

        // Interface tab
        if (hasInterfaceData) {
            const activeClass = !hasSystemData && !hasProductData && !hasClientData && !hasProjectData && !hasPolicyData ? 'active' : '';
            subTabsHtml += `<button class="sub-tab ${activeClass}" data-sub-tab="interface">Interface</button>`;
            subTabsContentHtml += `
                <div id="impactInterfaceViewContent" class="sub-tab-content ${activeClass}">
                    <div class="relationships-hierarchy">
                        <div class="hierarchy-header">
                            <div class="hierarchy-title">SYSTEM INTERFACE</div>
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
                                        <th style="text-align: center;"><div class="th-content" style="justify-content: center;"><span>Interface</span><i class="fas fa-sort"></i></div></th>
                                    </tr>
                                </thead>
                                <tbody id="interfaceImpactViewTableBody">
                                    ${renderInterfaceViewTableBody()}
                                </tbody>
                            </table>
                        </div>
                        <div class="hierarchy-footer">
                            ${interfaceRelationships.length} record${interfaceRelationships.length !== 1 ? 's' : ''}
                        </div>
                    </div>
                </div>
            `;
        }

        // Legal tab
        if (hasLegalData) {
            const activeClass = !hasSystemData && !hasProductData && !hasClientData && !hasProjectData && !hasPolicyData && !hasInterfaceData ? 'active' : '';
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

        // Dataset tab
        if (hasDatasetData) {
            const activeClass = !hasSystemData && !hasProductData && !hasClientData && !hasProjectData && !hasPolicyData && !hasInterfaceData && !hasLegalData ? 'active' : '';
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
                                        <th style="text-align: center;"><div class="th-content" style="justify-content: center;"><span>Data Set Owner</span><i class="fas fa-sort"></i></div></th>
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

        // Attribute tab
        if (hasAttributeData) {
            const activeClass = !hasSystemData && !hasProductData && !hasClientData && !hasProjectData && !hasPolicyData && !hasInterfaceData && !hasLegalData && !hasDatasetData ? 'active' : '';
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
                                        <th style="text-align: center;"><div class="th-content" style="justify-content: center;"><span>Attribute Owner</span><i class="fas fa-sort"></i></div></th>
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

        // Glossary tab
        if (hasGlossaryData) {
            const activeClass = !hasSystemData && !hasProductData && !hasClientData && !hasProjectData && !hasPolicyData && !hasInterfaceData && !hasLegalData && !hasDatasetData && !hasAttributeData ? 'active' : '';
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
        
        // Glossary Reverse tab (Glossaries that impact this Process)
        if (hasGlossaryReverseData) {
            const activeClass = !hasSystemData && !hasProductData && !hasClientData && !hasProjectData && !hasPolicyData && !hasInterfaceData && !hasLegalData && !hasDatasetData && !hasAttributeData && !hasGlossaryData ? 'active' : '';
            subTabsHtml += `<button class="sub-tab ${activeClass}" data-sub-tab="glossary-reverse">Glossary</button>`;
            subTabsContentHtml += `
                <div id="impactGlossaryReverseViewContent" class="sub-tab-content ${activeClass}">
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
                                        <th><div class="th-content"><span>Glossary</span><i class="fas fa-sort"></i></div></th>
                                        <th style="text-align: center;"><div class="th-content" style="justify-content: center;"><span>Strategic Owner</span><i class="fas fa-sort"></i></div></th>
                                    </tr>
                                </thead>
                                <tbody id="glossaryReverseImpactViewTableBody">
                                    ${renderGlossaryReverseViewTableBody()}
                                </tbody>
                            </table>
                        </div>
                        <div class="hierarchy-footer">
                            ${glossaryRelationshipsReverse.length} record${glossaryRelationshipsReverse.length !== 1 ? 's' : ''}
                        </div>
                    </div>
                </div>
            `;
        }
        
        // Business Area Reverse tab (Business Areas that impact this Process)
        if (hasBusinessAreaReverseData) {
            const activeClass = !hasSystemData && !hasProductData && !hasClientData && !hasProjectData && !hasPolicyData && !hasInterfaceData && !hasLegalData && !hasDatasetData && !hasAttributeData && !hasGlossaryData && !hasGlossaryReverseData ? 'active' : '';
            subTabsHtml += `<button class="sub-tab ${activeClass}" data-sub-tab="businessarea-reverse">Business Area</button>`;
            subTabsContentHtml += `
                <div id="impactBusinessAreaReverseViewContent" class="sub-tab-content ${activeClass}">
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
                                        <th style="text-align: center;"><div class="th-content" style="justify-content: center;"><span>Business Area Owner</span><i class="fas fa-sort"></i></div></th>
                                    </tr>
                                </thead>
                                <tbody id="businessAreaReverseImpactViewTableBody">
                                    ${renderBusinessAreaReverseViewTableBody()}
                                </tbody>
                            </table>
                        </div>
                        <div class="hierarchy-footer">
                            ${businessAreaRelationshipsReverse.length} record${businessAreaRelationshipsReverse.length !== 1 ? 's' : ''}
                        </div>
                    </div>
                </div>
            `;
        }
        
        // Capability Reverse tab (Capabilities that impact this Process)
        if (hasCapabilityReverseData) {
            const activeClass = !hasSystemData && !hasProductData && !hasClientData && !hasProjectData && !hasPolicyData && !hasInterfaceData && !hasLegalData && !hasDatasetData && !hasAttributeData && !hasGlossaryData && !hasGlossaryReverseData && !hasBusinessAreaReverseData ? 'active' : '';
            subTabsHtml += `<button class="sub-tab ${activeClass}" data-sub-tab="capability-reverse">Capability</button>`;
            subTabsContentHtml += `
                <div id="impactCapabilityReverseViewContent" class="sub-tab-content ${activeClass}">
                    <div class="relationships-hierarchy">
                        <div class="hierarchy-header">
                            <div class="hierarchy-title">CAPABILITY</div>
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
                                        <th><div class="th-content"><span>Capability</span><i class="fas fa-sort"></i></div></th>
                                        <th style="text-align: center;"><div class="th-content" style="justify-content: center;"><span>Capability Owner</span><i class="fas fa-sort"></i></div></th>
                                    </tr>
                                </thead>
                                <tbody id="capabilityReverseImpactViewTableBody">
                                    ${renderCapabilityReverseViewTableBody()}
                                </tbody>
                            </table>
                        </div>
                        <div class="hierarchy-footer">
                            ${capabilityRelationshipsReverse.length} record${capabilityRelationshipsReverse.length !== 1 ? 's' : ''}
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
            return '<tr><td colspan="4" style="text-align: center; padding: 2rem; color: var(--text-muted, #6b7280);">No product relationships found</td></tr>';
        }

        return productRelationships.map(relationship => {
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
            // DAO returns it as projectRef, but also check projectRefNumber for compatibility
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

    // Render policy view table body
    function renderPolicyViewTableBody() {
        if (policyRelationships.length === 0) {
            return '<tr><td colspan="5" style="text-align: center; padding: 2rem; color: var(--text-muted, #6b7280);">No policy relationships found</td></tr>';
        }

        return policyRelationships.map(relationship => {
            const policyLink = createEntityLink('policy', relationship.policyId, relationship.policyName);
            const refNumber = relationship.policyRefNumber || relationship.refNumber || '';
            const policyType = relationship.policyTypeName || '';
            
            return `
            <tr>
                <td>${escapeHtml(relationship.relationTypeName || 'N/A')}</td>
                <td>
                    <div class="entity-info">
                        <span class="entity-name">${policyLink}</span>
                    </div>
                </td>
                <td style="text-align: center;">${escapeHtml(refNumber)}</td>
                <td style="text-align: center;">${escapeHtml(policyType)}</td>
                <td style="text-align: center;">
                    <div class="owner-info">
                        <div class="owner-name" style="font-weight: bold; color: #333;">${escapeHtml(relationship.policyOwnerName || 'No owner')}</div>
                    </div>
                </td>
            </tr>
        `;
        }).join('');
    }

    // Render interface view table body
    function renderInterfaceViewTableBody() {
        if (interfaceRelationships.length === 0) {
            return '<tr><td colspan="2" style="text-align: center; padding: 2rem; color: var(--text-muted, #6b7280);">No interface relationships found</td></tr>';
        }

        return interfaceRelationships.map(relationship => {
            const interfaceLink = createEntityLink('interface', relationship.interfaceId, relationship.interfaceName);
            
            return `
            <tr>
                <td>${escapeHtml(relationship.relationTypeName || 'N/A')}</td>
                <td style="text-align: center;">
                    <div class="entity-info">
                        <span class="entity-name">${interfaceLink}</span>
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
                <td style="text-align: center;">
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
                <td style="text-align: center;">
                    <div class="owner-info">
                        <div class="owner-name" style="font-weight: bold; color: #333;">${escapeHtml(relationship.attributeOwnerName || 'No owner')}</div>
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
            const glossaryType = relationship.glossaryTypeName || relationship.glossaryType || '';
            
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
    
    function renderGlossaryReverseViewTableBody() {
        if (glossaryRelationshipsReverse.length === 0) {
            return '<tr class="reverse-relationship-row"><td colspan="3" style="text-align: center; padding: 2rem; color: var(--text-muted, #6b7280);">No glossary relationships found</td></tr>';
        }

        return glossaryRelationshipsReverse.map(relationship => {
            // Use reverse name if available, otherwise use regular relation type name
            const relationTypeDisplay = relationship.relationTypeReverseName || relationship.relationTypeName || 'N/A';
            
            const glossaryName = relationship.glossaryName || 'N/A';
            const glossaryRefNumber = relationship.glossaryRefNumber || '';
            const displayName = glossaryRefNumber ? `${glossaryName} (${glossaryRefNumber})` : glossaryName;
            const glossaryLink = createEntityLink('glossary', relationship.glossaryId, displayName);
            
            // Get owner name
            const ownerName = relationship.glossaryOwnerName || relationship.ownerName || 'No owner';
            
            return `
                <tr class="reverse-relationship-row">
                    <td>${escapeHtml(relationTypeDisplay)}</td>
                    <td>
                        <div class="entity-info reverse-relationship-entity">
                            <i class="fas fa-book" style="color: var(--text-secondary, #64748b); margin-right: 0.5rem;"></i>
                            <span class="entity-name">${glossaryLink}</span>
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
    
    function renderBusinessAreaReverseViewTableBody() {
        if (businessAreaRelationshipsReverse.length === 0) {
            return '<tr class="reverse-relationship-row"><td colspan="3" style="text-align: center; padding: 2rem; color: var(--text-muted, #6b7280);">No business area relationships found</td></tr>';
        }

        return businessAreaRelationshipsReverse.map(relationship => {
            // Use reverse name if available, otherwise use regular relation type name
            const relationTypeDisplay = relationship.relationTypeReverseName || relationship.relationTypeName || 'N/A';
            
            const businessAreaName = relationship.businessAreaName || 'N/A';
            const businessAreaLink = createEntityLink('businessarea', relationship.businessAreaId, businessAreaName);
            
            // Get owner name
            const ownerName = relationship.businessAreaOwnerName || 'No owner';
            
            return `
                <tr class="reverse-relationship-row">
                    <td>${escapeHtml(relationTypeDisplay)}</td>
                    <td>
                        <div class="entity-info reverse-relationship-entity">
                            <i class="fas fa-building" style="color: var(--text-secondary, #64748b); margin-right: 0.5rem;"></i>
                            <span class="entity-name">${businessAreaLink}</span>
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
    
    function renderCapabilityReverseViewTableBody() {
        if (capabilityRelationshipsReverse.length === 0) {
            return '<tr class="reverse-relationship-row"><td colspan="3" style="text-align: center; padding: 2rem; color: var(--text-muted, #6b7280);">No capability relationships found</td></tr>';
        }

        return capabilityRelationshipsReverse.map(relationship => {
            // Use reverse name if available, otherwise use regular relation type name
            const relationTypeDisplay = relationship.relationTypeReverseName || relationship.relationTypeName || 'N/A';
            
            const capabilityName = relationship.capabilityName || 'N/A';
            const capabilityRefNumber = relationship.capabilityRefNumber || '';
            const displayName = capabilityRefNumber ? `${capabilityName} (${capabilityRefNumber})` : capabilityName;
            const capabilityLink = createEntityLink('capability', relationship.capabilityId, displayName);
            
            // Get owner name
            const ownerName = relationship.capabilityOwnerName || 'No owner';
            
            return `
                <tr class="reverse-relationship-row">
                    <td>${escapeHtml(relationTypeDisplay)}</td>
                    <td>
                        <div class="entity-info reverse-relationship-entity">
                            <i class="fas fa-cogs" style="color: var(--text-secondary, #64748b); margin-right: 0.5rem;"></i>
                            <span class="entity-name">${capabilityLink}</span>
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

    // Listen for pending changes view switch event
    document.addEventListener('pendingChangesViewSwitch', async function(event) {
        const { view, facetType, objectId } = event.detail || {};
        if (facetType !== 'Process' || !objectId || objectId !== currentProcessId) {
            return;
        }
        
        console.log('[Process Impact] pendingChangesViewSwitch event received, view:', view);
        currentViewMode = view || 'original';
        
        // Reload all impact data with new view mode
        await loadImpactViewData();
    });

    // Setup Impact view sub-tabs
    function setupImpactViewSubTabs() {
        console.log('Setting up Impact view sub-tabs...');
        
        const subTabs = document.querySelectorAll('#processImpactContainer .sub-tab');
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
                const subTabContents = document.querySelectorAll('#processImpactContainer .sub-tab-content');
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
                } else if (subTabName === 'policy') {
                    targetSubTab = document.getElementById('impactPolicyViewContent');
                } else if (subTabName === 'interface') {
                    targetSubTab = document.getElementById('impactInterfaceViewContent');
                } else if (subTabName === 'legal') {
                    targetSubTab = document.getElementById('impactLegalViewContent');
                } else if (subTabName === 'dataset') {
                    targetSubTab = document.getElementById('impactDatasetViewContent');
                } else if (subTabName === 'attribute') {
                    targetSubTab = document.getElementById('impactAttributeViewContent');
                } else if (subTabName === 'glossary') {
                    targetSubTab = document.getElementById('impactGlossaryViewContent');
                } else if (subTabName === 'glossary-reverse') {
                    targetSubTab = document.getElementById('impactGlossaryReverseViewContent');
                } else if (subTabName === 'businessarea-reverse') {
                    targetSubTab = document.getElementById('impactBusinessAreaReverseViewContent');
                } else if (subTabName === 'capability-reverse') {
                    targetSubTab = document.getElementById('impactCapabilityReverseViewContent');
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

    // Expose function globally for use by process.js
    window.loadProcessImpact = loadProcessImpact;

})();

