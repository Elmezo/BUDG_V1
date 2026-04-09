// Glossary Impact View JavaScript - Implementation for Product and Client sub-tabs

(function() {
    
    // Helper function to escape HTML
    function escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
    
    // Helper function to create entity links
    function createEntityLink(id, name, entityType) {
        if (!id || !name) return escapeHtml(name || '');
        let url;
        switch(entityType) {
            case 'product':
                url = `/view/product/${id}`;
                break;
            case 'client':
                url = `/view/client/${id}`;
                break;
            case 'business-area':
                url = `/view/business-area/business-area.html?id=${id}`;
                break;
            case 'process':
                url = `/view/process/${id}`;
                break;
            case 'policy':
                url = `/view/policy/${id}`;
                break;
            case 'capability':
                url = `/view/capability/${id}`;
                break;
            case 'project':
                url = `/view/project/project.html?id=${id}`;
                break;
            default:
                url = `/view/${entityType}/${entityType}.html?id=${id}`;
        }
        const viewText = window.I18n?.t('glossaryImpact.messages.view') || 'View';
        return `<a href="${url}" class="entity-link" style="color: var(--secondary-color, #248567); text-decoration: none;" title="${viewText} ${escapeHtml(name)}">${escapeHtml(name)}</a>`;
    }
    
    let currentGlossaryId = null;
    let productRelationships = [];
    let clientRelationships = [];
    let businessAreaRelationshipsReverse = [];
    let processRelationshipsReverse = [];
    let policyRelationshipsReverse = [];
    let capabilityRelationshipsReverse = [];
    let projectRelationshipsReverse = [];
    
    // Load Glossary Impact data
    async function loadGlossaryImpact(glossaryId, view = null) {
        try {
            currentGlossaryId = glossaryId;
            
            // Check if rollup view is enabled
            const isRollupViewEnabled = localStorage.getItem(`glossary_${glossaryId}_rollup_view`) === 'true';
            
            // Get enabled glossary types from admin settings
            let enabledGlossaryTypes = [];
            if (isRollupViewEnabled) {
                try {
                    const settingsResponse = await fetch('/api/system-settings/GlossaryRollup/enabled_types', {
                        method: 'GET',
                        credentials: 'include',
                        headers: { 'Content-Type': 'application/json' }
                    });
                    if (settingsResponse.ok) {
                        const settings = await settingsResponse.json();
                        if (settings.value) {
                            enabledGlossaryTypes = JSON.parse(settings.value);
                            // Normalize: ensure all types are trimmed and non-empty
                            enabledGlossaryTypes = enabledGlossaryTypes
                                .map(type => (type || '').trim())
                                .filter(type => type.length > 0);
                        }
                        // If empty array or null, default to all types
                        if (!enabledGlossaryTypes || enabledGlossaryTypes.length === 0) {
                            enabledGlossaryTypes = ['Domain', 'Subdomain', 'Term', 'Metric'];
                        }
                    } else {
                        // Default to all types if settings not found
                        enabledGlossaryTypes = ['Domain', 'Subdomain', 'Term', 'Metric'];
                    }
                } catch (error) {
                    console.warn('Failed to load rollup settings, using defaults:', error);
                    enabledGlossaryTypes = ['Domain', 'Subdomain', 'Term', 'Metric'];
                }
            }
            
            // Build query string with view parameter if provided
            const viewParam = view === 'changes' ? '?view=changes' : '';
            
            // Load both forward and reverse relationships
            const [productRelationshipsData, clientRelationshipsData] = await Promise.all([
                fetch(`/api/glossary-impact/${glossaryId}/products${viewParam}`, {
                    method: 'GET',
                    credentials: 'include',
                    headers: { 'Content-Type': 'application/json' }
                }).then(res => res.ok ? res.json() : []).catch(() => []),
                
                fetch(`/api/glossary-impact/${glossaryId}/clients${viewParam}`, {
                    method: 'GET',
                    credentials: 'include',
                    headers: { 'Content-Type': 'application/json' }
                }).then(res => res.ok ? res.json() : []).catch(() => [])
            ]);
            
            // Filter relationships based on rollup view and enabled types
            let filteredProductData = productRelationshipsData || [];
            let filteredClientData = clientRelationshipsData || [];
            
            // Debug logging - show raw data from API
            console.log('[Glossary Impact] Raw productRelationshipsData from API:', JSON.stringify(productRelationshipsData, null, 2));
            console.log('[Glossary Impact] Rollup view enabled:', isRollupViewEnabled);
            console.log('[Glossary Impact] Enabled glossary types:', enabledGlossaryTypes);
            console.log('[Glossary Impact] Total product relationships before filter:', filteredProductData.length);
            console.log('[Glossary Impact] Product relationships with childGlossaryId:', filteredProductData.filter(rel => rel.childGlossaryId).length);
            
            // Log each relationship to see its structure
            filteredProductData.forEach((rel, index) => {
                console.log(`[Glossary Impact] Relationship ${index}:`, {
                    productId: rel.productId,
                    productName: rel.productName,
                    childGlossaryId: rel.childGlossaryId,
                    childGlossaryName: rel.childGlossaryName,
                    childGlossaryType: rel.childGlossaryType,
                    isDirectLink: rel.isDirectLink
                });
            });
            
            if (!isRollupViewEnabled) {
                // Hide rollup view: filter out child glossary relationships (orange rows)
                filteredProductData = filteredProductData.filter(rel => !rel.childGlossaryId);
                filteredClientData = filteredClientData.filter(rel => !rel.childGlossaryId);
            } else {
                // Show rollup view: filter child glossary relationships by enabled types
                // Normalize enabled types for comparison (trim and lowercase)
                const normalizedEnabledTypes = enabledGlossaryTypes.map(type => 
                    (type || '').trim().toLowerCase()
                ).filter(type => type.length > 0);
                
                filteredProductData = filteredProductData.filter(rel => {
                    // Keep direct links (blue rows)
                    if (!rel.childGlossaryId) return true;
                    // Filter child links (orange rows) by enabled types
                    const childType = (rel.childGlossaryType || '').trim();
                    if (!childType) {
                        console.log('[Glossary Impact] Excluding relationship with no childGlossaryType:', rel);
                        return false; // If no type, exclude it
                    }
                    // Use exact match (case-insensitive)
                    const normalizedChildType = childType.toLowerCase();
                    const matches = normalizedEnabledTypes.includes(normalizedChildType);
                    if (!matches) {
                        console.log('[Glossary Impact] Excluding relationship - childType:', childType, 'not in enabled types:', enabledGlossaryTypes);
                    }
                    return matches;
                });
                filteredClientData = filteredClientData.filter(rel => {
                    // Keep direct links
                    if (!rel.childGlossaryId) return true;
                    // Filter child links by enabled types
                    const childType = (rel.childGlossaryType || '').trim();
                    if (!childType) return false; // If no type, exclude it
                    // Use exact match (case-insensitive)
                    const normalizedChildType = childType.toLowerCase();
                    return normalizedEnabledTypes.includes(normalizedChildType);
                });
            }
            
            console.log('[Glossary Impact] Total product relationships after filter:', filteredProductData.length);
            
            // Deduplicate product relationships: if same product+relationType exists as both direct and child link,
            // prefer the direct link (child links are view-only for impact display)
            productRelationships = deduplicateProductRelationships(filteredProductData);
            clientRelationships = filteredClientData;
            
            // Load reverse relationships
            await Promise.all([
                loadBusinessAreaRelationshipsReverse(),
                loadProcessRelationshipsReverse(),
                loadPolicyRelationshipsReverse(),
                loadCapabilityRelationshipsReverse(),
                loadProjectRelationshipsReverse()
            ]);
            
            renderImpactView();
            
        } catch (error) {
            console.error('Error loading glossary impact:', error);
            renderImpactView();
        }
    }
    
    // Deduplicate product relationships to prevent showing duplicates
    // If same product+relationType exists as both direct and child link, keep only the direct link
    // Child glossary relationships are view-only and shown for impact visibility
    function deduplicateProductRelationships(relationships) {
        if (!Array.isArray(relationships) || relationships.length === 0) {
            return [];
        }
        
        // Create a map to track unique relationships
        // Key: productId_relationType_childGlossaryId (null for direct links)
        const relationshipMap = new Map();
        
        relationships.forEach(rel => {
            // Create unique key: include childGlossaryId to distinguish between direct and child links
            // This allows showing both if product is linked directly AND through a child
            const childId = rel.childGlossaryId || 'direct';
            const key = `${rel.productId}_${rel.relationType}_${childId}`;
            
            // Check if we already have this exact relationship
            if (!relationshipMap.has(key)) {
                relationshipMap.set(key, rel);
            } else {
                // If duplicate exists, prefer direct link over child link
                const existing = relationshipMap.get(key);
                const isCurrentDirect = rel.isDirectLink !== false && !rel.childGlossaryId;
                const isExistingDirect = existing.isDirectLink !== false && !existing.childGlossaryId;
                
                // Prefer direct links (they are editable from parent)
                if (isCurrentDirect && !isExistingDirect) {
                    relationshipMap.set(key, rel);
                }
                // Otherwise keep existing (first occurrence)
            }
        });
        
        return Array.from(relationshipMap.values());
    }
    
    // Load reverse relationships
    async function loadBusinessAreaRelationshipsReverse() {
        try {
            const response = await fetch(`/api/businessarea-impact/glossaries/${currentGlossaryId}/businessareas`, {
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
    
    async function loadProcessRelationshipsReverse() {
        try {
            console.log('Loading Process reverse relationships for glossary:', currentGlossaryId);
            const response = await fetch(`/api/process-impact/glossaries/${currentGlossaryId}/processes`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });
            if (!response.ok) {
                console.error('Failed to load Process reverse relationships. Status:', response.status);
                processRelationshipsReverse = [];
                return;
            }
            const data = await response.json();
            console.log('Process reverse relationships data:', data);
            processRelationshipsReverse = Array.isArray(data) ? data : [];
            console.log('Process reverse relationships loaded:', processRelationshipsReverse.length);
        } catch (error) {
            console.error('Error loading reverse process relationships:', error);
            processRelationshipsReverse = [];
        }
    }
    
    async function loadPolicyRelationshipsReverse() {
        try {
            const response = await fetch(`/api/policy-impact/glossaries/${currentGlossaryId}/policies`, {
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
            const response = await fetch(`/api/capability-impact/glossaries/${currentGlossaryId}/capabilities`, {
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
            const response = await fetch(`/api/project-impact/glossaries/${currentGlossaryId}/projects`, {
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
    
    // Render Impact view
    function renderImpactView() {
        const container = document.getElementById('impactContainer');
        if (!container) return;
        
        // Check if there's any data
        const hasProductData = productRelationships.length > 0;
        const hasClientData = clientRelationships.length > 0;
        const hasBusinessAreaReverse = businessAreaRelationshipsReverse.length > 0;
        const hasProcessReverse = processRelationshipsReverse.length > 0;
        const hasPolicyReverse = policyRelationshipsReverse.length > 0;
        const hasCapabilityReverse = capabilityRelationshipsReverse.length > 0;
        const hasProjectReverse = projectRelationshipsReverse.length > 0;
        
        console.log('Glossary Impact View - Data check:', {
            hasProductData,
            hasClientData,
            hasBusinessAreaReverse,
            hasProcessReverse,
            hasPolicyReverse,
            hasCapabilityReverse,
            hasProjectReverse,
            processRelationshipsReverseCount: processRelationshipsReverse.length
        });
        
        const hasAnyData = hasProductData || hasClientData || hasBusinessAreaReverse || hasProcessReverse || hasPolicyReverse || hasCapabilityReverse || hasProjectReverse;
        
        if (!hasAnyData) {
            container.innerHTML = `
                <div style="text-align: center; padding: 3rem; color: var(--text-secondary, #6c757d);">
                    <i class="fas fa-inbox" style="font-size: 3rem; margin-bottom: 1rem; opacity: 0.5;"></i>
                    <p style="font-size: 1.1rem; margin: 0;">${window.I18n?.t('message.noImpactRelationships') || 'No impact relationships found'}</p>
                </div>
            `;
            return;
        }
        
        // Build sub-tabs HTML
        let subTabsHtml = '';
        let subTabsContentHtml = '';
        let firstActive = true;
        
        if (hasProductData) {
            subTabsHtml += '<button class="sub-tab active" data-sub-tab="product">Product</button>';
            subTabsContentHtml += `
                <div id="impactProductViewContent" class="sub-tab-content active" style="display:block;">
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
                                        <th><div class="th-content"><span>Product Name</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content"><span>Product Owner</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content"><span>Child Glossary</span><i class="fas fa-sort"></i></div></th>
                                        <th><div class="th-content"><span>Child Glossary Type</span><i class="fas fa-sort"></i></div></th>
                                    </tr>
                                </thead>
                                <tbody>${renderProductViewTableBody()}</tbody>
                            </table>
                        </div>
                        <div class="hierarchy-footer">${productRelationships.length} record${productRelationships.length !== 1 ? 's' : ''}</div>
                    </div>
                </div>
            `;
            firstActive = false;
        }
        
        if (hasClientData) {
            const activeClass = firstActive ? 'active' : '';
            if (firstActive) subTabsHtml += '<button class="sub-tab active" data-sub-tab="client">Client</button>';
            else subTabsHtml += '<button class="sub-tab" data-sub-tab="client">Client</button>';
            subTabsContentHtml += `
                <div id="impactClientViewContent" class="sub-tab-content ${activeClass}" ${firstActive ? 'style="display:block;"' : ''}>
                    <div class="relationships-hierarchy">
                        <div class="hierarchy-header">
                            <div class="hierarchy-title">CLIENT</div>
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
                                        <th><div class="th-content"><span>Client Owner</span><i class="fas fa-sort"></i></div></th>
                                    </tr>
                                </thead>
                                <tbody>${renderClientViewTableBody()}</tbody>
                            </table>
                        </div>
                        <div class="hierarchy-footer">${clientRelationships.length} record${clientRelationships.length !== 1 ? 's' : ''}</div>
                    </div>
                </div>
            `;
            firstActive = false;
        }
        
        // Add reverse relationship sub-tabs
        if (hasBusinessAreaReverse) {
            const activeClass = firstActive ? 'active' : '';
            if (firstActive) subTabsHtml += '<button class="sub-tab active" data-sub-tab="businessarea">Business Area</button>';
            else subTabsHtml += '<button class="sub-tab" data-sub-tab="businessarea">Business Area</button>';
            subTabsContentHtml += `
                <div id="impactBusinessAreaReverseViewContent" class="sub-tab-content ${activeClass}" ${firstActive ? 'style="display:block;"' : ''}>
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
                                <tbody>${renderBusinessAreaReverseViewTableBody()}</tbody>
                            </table>
                        </div>
                        <div class="hierarchy-footer">${businessAreaRelationshipsReverse.length} record${businessAreaRelationshipsReverse.length !== 1 ? 's' : ''}</div>
                    </div>
                </div>
            `;
            firstActive = false;
        }
        
        if (hasProcessReverse) {
            const activeClass = firstActive ? 'active' : '';
            if (firstActive) subTabsHtml += '<button class="sub-tab active" data-sub-tab="process">Process</button>';
            else subTabsHtml += '<button class="sub-tab" data-sub-tab="process">Process</button>';
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
        }
        
        // Render sub-tabs and content
        let html = `
            <div class="view-section" style="grid-column:1/-1;">
                <div class="relationships-container">
                    <div class="relationships-sub-tabs">${subTabsHtml}</div>
                    <div class="relationships-content">${subTabsContentHtml}</div>
                </div>
            </div>
        `;

        container.innerHTML = html;
        
        // Setup sub-tab switching
        setupSubTabs();
    }
    
    // Setup sub-tab switching
    function setupSubTabs() {
        const subTabs = document.querySelectorAll('#impactContainer .sub-tab');
        
        subTabs.forEach(function(subTab) {
            subTab.addEventListener('click', function(e) {
                e.preventDefault();
                
                // Remove active class from all sub-tabs
                subTabs.forEach(function(t) { t.classList.remove('active'); });
                this.classList.add('active');
                
                const subTabName = this.getAttribute('data-sub-tab');
                
                // Hide all sub-tab contents
                const subTabContents = document.querySelectorAll('#impactContainer .sub-tab-content');
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
                } else if (subTabName === 'businessarea') {
                    targetSubTab = document.getElementById('impactBusinessAreaReverseViewContent');
                } else if (subTabName === 'process') {
                    targetSubTab = document.getElementById('impactProcessReverseViewContent');
                } else if (subTabName === 'policy') {
                    targetSubTab = document.getElementById('impactPolicyReverseViewContent');
                } else if (subTabName === 'capability') {
                    targetSubTab = document.getElementById('impactCapabilityReverseViewContent');
                } else if (subTabName === 'project') {
                    targetSubTab = document.getElementById('impactProjectReverseViewContent');
                }
                
                if (targetSubTab) {
                    targetSubTab.classList.add('active');
                    targetSubTab.style.display = 'block';
                }
            });
        });
    }
    
    // Render product view table body
    function renderProductViewTableBody() {
        if (!productRelationships || productRelationships.length === 0) {
            return '<tr><td colspan="5" style="text-align: center; padding: 2rem; color: var(--text-secondary, #6c757d);">No product relationships found</td></tr>';
        }
        
        return productRelationships.map(rel => {
            const relationTypeName = escapeHtml(rel.relationTypeName || '');
            const productName = rel.productName || '';
            const productId = rel.productId;
            const productOwner = escapeHtml(rel.productOwnerName || 'No owner');
            const childGlossaryId = rel.childGlossaryId;
            const childGlossaryName = rel.childGlossaryName || '';
            const childGlossaryType = rel.childGlossaryType || '';
            const isDirectLink = rel.isDirectLink !== false; // Default to true if not specified
            
            // Determine row color: blue for direct links, orange for child links
            const rowClass = isDirectLink ? 'direct-link-row' : 'child-link-row';
            const rowStyle = isDirectLink 
                ? 'background-color: #e3f2fd;' // Light blue
                : 'background-color: #fff3e0;'; // Light orange
            
            // Create child glossary link if available
            // Note: Child glossary relationships are view-only (not editable from parent)
            let childGlossaryCell = '';
            if (childGlossaryId && childGlossaryName) {
                childGlossaryCell = `<a href="/view/glossary/glossary.html?id=${childGlossaryId}" class="entity-link" style="color: var(--secondary-color, #248567); text-decoration: none;" title="Child glossary relationship (view-only - edit from child glossary)">
                    <i class="fas fa-file-alt" style="color: var(--text-secondary, #64748b); margin-right: 0.5rem;"></i>${escapeHtml(childGlossaryName)}
                    <i class="fas fa-info-circle" style="color: var(--text-secondary, #64748b); margin-left: 0.25rem; font-size: 0.75rem;" title="This relationship is from a child glossary and is view-only"></i>
                </a>`;
            } else {
                childGlossaryCell = '<span style="color: var(--text-secondary, #6c757d);">—</span>';
            }
            
            // Child glossary type with badge styling
            let childGlossaryTypeCell = '';
            if (childGlossaryType) {
                // Determine badge color based on type (similar to other views)
                const typeLower = childGlossaryType.toLowerCase();
                let badgeColor = '#248567'; // Default teal
                if (typeLower.includes('domain')) {
                    badgeColor = '#ff9800'; // Orange
                } else if (typeLower.includes('term')) {
                    badgeColor = '#2196f3'; // Blue
                }
                childGlossaryTypeCell = `<span class="badge" style="background-color: ${badgeColor}; color: white; padding: 0.25rem 0.5rem; border-radius: 0.25rem; font-size: 0.875rem;">${escapeHtml(childGlossaryType)}</span>`;
            } else {
                childGlossaryTypeCell = '<span style="color: var(--text-secondary, #6c757d);">—</span>';
            }
            
            // Add view-only indicator for child glossary relationships
            const viewOnlyIndicator = !isDirectLink && childGlossaryId 
                ? '<i class="fas fa-eye" style="color: var(--text-secondary, #64748b); margin-left: 0.5rem; font-size: 0.75rem;" title="View-only: This relationship is from a child glossary"></i>'
                : '';
            
            return `
                <tr class="${rowClass}" style="${rowStyle}" ${!isDirectLink && childGlossaryId ? 'title="Child glossary relationship - view-only (edit from child glossary)"' : ''}>
                    <td>
                        <i class="fas fa-gem" style="color: var(--text-secondary, #64748b); margin-right: 0.5rem;"></i>${relationTypeName}${viewOnlyIndicator}
                    </td>
                    <td>${createEntityLink(productId, productName, 'product')}</td>
                    <td style="text-align: center;">${productOwner}</td>
                    <td>${childGlossaryCell}</td>
                    <td style="text-align: center;">${childGlossaryTypeCell}</td>
                </tr>
            `;
        }).join('');
    }
    
    // Render client view table body
    function renderClientViewTableBody() {
        if (!clientRelationships || clientRelationships.length === 0) {
            return '<tr><td colspan="3" style="text-align: center; padding: 2rem; color: var(--text-secondary, #6c757d);">No client relationships found</td></tr>';
        }
        
        return clientRelationships.map(rel => {
            const relationTypeName = escapeHtml(rel.relationTypeName || '');
            const clientName = rel.clientName || '';
            const clientId = rel.clientId;
            const clientOwner = escapeHtml(rel.clientOwnerName || 'No owner');
            
            return `
                <tr>
                    <td>${relationTypeName}</td>
                    <td>${createEntityLink(clientId, clientName, 'client')}</td>
                    <td style="text-align: center;">${clientOwner}</td>
                </tr>
            `;
        }).join('');
    }
    
    // Render reverse relationship table bodies
    function renderBusinessAreaReverseViewTableBody() {
        if (!businessAreaRelationshipsReverse || businessAreaRelationshipsReverse.length === 0) {
            return '<tr class="reverse-relationship-row"><td colspan="2" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">No business area relationships found</td></tr>';
        }
        return businessAreaRelationshipsReverse.map(rel => {
            const relationTypeName = escapeHtml(rel.relationTypeReverseName || rel.relationTypeName || 'N/A');
            const businessAreaName = rel.businessAreaName || 'N/A';
            const businessAreaId = rel.businessAreaId;
            const businessAreaLink = createEntityLink(businessAreaId, businessAreaName, 'business-area');
            return `<tr class="reverse-relationship-row"><td>${relationTypeName}</td><td><div class="entity-info reverse-relationship-entity"><i class="fas fa-building" style="color: var(--text-secondary, #64748b); margin-right: 0.5rem;"></i><span class="entity-name">${businessAreaLink}</span></div></td></tr>`;
        }).join('');
    }
    
    function renderProcessReverseViewTableBody() {
        if (!processRelationshipsReverse || processRelationshipsReverse.length === 0) {
            return '<tr class="reverse-relationship-row"><td colspan="3" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">No process relationships found</td></tr>';
        }
        return processRelationshipsReverse.map(rel => {
            const relationTypeName = escapeHtml(rel.relationTypeReverseName || rel.relationTypeName || 'N/A');
            const processName = rel.processName || 'N/A';
            const processRefNumber = rel.processRefNumber || '';
            const displayName = processRefNumber ? `${processName} (${processRefNumber})` : processName;
            const processId = rel.processId;
            const processLink = createEntityLink(processId, displayName, 'process');
            const ownerName = rel.processOwnerName || 'No owner';
            return `<tr class="reverse-relationship-row"><td>${relationTypeName}</td><td><div class="entity-info reverse-relationship-entity"><i class="fas fa-cogs" style="color: var(--text-secondary, #64748b); margin-right: 0.5rem;"></i><span class="entity-name">${processLink}</span></div></td><td style="text-align: center;"><div class="owner-info reverse-relationship-owner"><span class="owner-name">${escapeHtml(ownerName)}</span></div></td></tr>`;
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
            const policyLink = createEntityLink(policyId, policyName, 'policy');
            const ownerName = rel.policyOwnerName || rel.ownerName || 'No owner';
            return `<tr class="reverse-relationship-row"><td>${relationTypeName}</td><td><div class="entity-info reverse-relationship-entity"><i class="fas fa-file-contract" style="color: var(--text-secondary, #64748b); margin-right: 0.5rem;"></i><span class="entity-name">${policyLink}</span></div></td><td style="text-align: center;"><div class="owner-info reverse-relationship-owner"><span class="owner-name">${escapeHtml(ownerName)}</span></div></td></tr>`;
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
            const capabilityLink = createEntityLink(capabilityId, capabilityName, 'capability');
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
            const projectLink = createEntityLink(projectId, projectName, 'project');
            const ownerName = rel.projectOwnerName || rel.ownerName || 'No owner';
            return `<tr class="reverse-relationship-row"><td>${relationTypeName}</td><td><div class="entity-info reverse-relationship-entity"><i class="fas fa-project-diagram" style="color: var(--text-secondary, #64748b); margin-right: 0.5rem;"></i><span class="entity-name">${projectLink}</span></div></td><td style="text-align: center;"><div class="owner-info reverse-relationship-owner"><span class="owner-name">${escapeHtml(ownerName)}</span></div></td></tr>`;
        }).join('');
    }
    
    // Export to global scope
    window.loadGlossaryImpact = loadGlossaryImpact;
    
})();

