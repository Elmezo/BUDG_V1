// Change Request Stakeholder Community JavaScript
// Copied from System Stakeholder Community - same functionality
(function() {
    let currentCrId = null;
    let currentReference = null;
    let allStakeholders = [];
    let filteredStakeholders = [];

    // Object type icons mapping
    const objectTypeIcons = {
        'Process': 'fa-diagram-project',
        'Product': 'fa-tag',
        'Project': 'fa-folder',
        'Client': 'fa-building',
        'Glossary': 'fa-book',
        'Business Area': 'fa-chart-pie',
        'Capability': 'fa-gem',
        'Policy': 'fa-file-lines',
        'Legal': 'fa-scale-balanced',
        'System': 'fa-desktop',
        'Dataset': 'fa-database',
        'Regulation': 'fa-gavel',
        'Committee': 'fa-users'
    };
    
    // Object type view URL mapping
    const objectTypeUrls = {
        'Process': (id) => `/view/process/${id}`,
        'Product': (id) => `/view/product/${id}`,
        'Project': (id) => `/view/project/${id}`,
        'Client': (id) => `/view/client/client.html?id=${id}`,
        'Glossary': (id) => `/view/glossary/${id}`,
        'Business Area': (id) => `/view/business-area/business-area.html?id=${id}`,
        'Capability': (id) => `/view/capability/${id}`,
        'Policy': (id) => `/view/policy/${id}`,
        'Legal': (id) => `/view/LegalEntity/${id}`,
        'System': (id) => `/view/system/${id}`,
        'Dataset': (id) => `/view/dataset/${id}`,
        'Regulation': (id) => `/view/regulation/${id}`,
        'Committee': (id) => `/view/committee/${id}`
    };

    // Initialize stakeholder community view
    async function initStakeholderCommunity(crId, reference) {
        currentCrId = crId;
        currentReference = reference;
        await loadStakeholderCommunity();
    }

    // Map facet types to their stakeholder community API endpoints
    const facetApiEndpoints = {
        'system': (id) => `/api/system/stakeholder-community?system_id=${id}`,
        'dataset': (id) => `/api/dataset/stakeholder-community?dataset_id=${id}`,
        'data-set': (id) => `/api/dataset/stakeholder-community?dataset_id=${id}`,
        'process': (id) => `/api/process/stakeholder-community?process_id=${id}`,
        'product': (id) => `/api/product/stakeholder-community?product_id=${id}`,
        'project': (id) => `/api/project/stakeholder-community?project_id=${id}`,
        'policy': (id) => `/api/policy/stakeholder-community?policy_id=${id}`,
        'glossary': (id) => `/api/glossary/stakeholder-community?glossary_id=${id}`,
        'business-area': (id) => `/api/businessarea/stakeholder-community?businessarea_id=${id}`,
        'businessarea': (id) => `/api/businessarea/stakeholder-community?businessarea_id=${id}`,
        'client': (id) => `/api/client/stakeholder-community?client_id=${id}`,
        'capability': (id) => `/api/capability/stakeholder-community?capability_id=${id}`,
        'legal-entity': (id) => `/api/legalentity/stakeholder-community?legalentity_id=${id}`,
        'legalentity': (id) => `/api/legalentity/stakeholder-community?legalentity_id=${id}`,
        'regulation': (id) => `/api/regulation/stakeholder-community?regulation_id=${id}`,
        'committee': (id) => `/api/committee/stakeholder-community?committee_id=${id}`
    };

    // Parse reference string (e.g., "System 63" -> {facetType: "system", facetId: 63})
    function parseReference(reference) {
        if (!reference) return null;
        
        // Match pattern: "Facet Name 123" or "Facet-Name 123"
        const match = reference.trim().match(/^(.+?)\s+(\d+)$/);
        if (match) {
            const facetName = match[1].trim();
            const facetId = parseInt(match[2]);
            
            // Normalize facet name to match our mapping keys
            const normalizedFacetType = facetName.toLowerCase()
                .replace(' ', '-')
                .replace('_', '-');
            
            return { facetType: normalizedFacetType, facetId };
        }
        return null;
    }

    // Load stakeholder community data from API - calls the EXISTING facet stakeholder community endpoints
    async function loadStakeholderCommunity() {
        try {
            const container = document.getElementById('crStakeholderCommunityContainer');
            if (!container) {
                console.error('crStakeholderCommunityContainer not found');
                return;
            }

            container.innerHTML = '<div class="loading" style="text-align: center; padding: 2rem; color: var(--text-muted, #6b7280);"><i class="fas fa-spinner fa-spin"></i> Loading stakeholder community...</div>';

            // Parse the reference to get facet type and ID
            const parsed = parseReference(currentReference);
            if (!parsed) {
                console.error('Could not parse reference:', currentReference);
                renderEmptyState();
                return;
            }
            
            console.log('Parsed reference:', parsed);
            
            // Get the API endpoint for this facet type
            const getEndpoint = facetApiEndpoints[parsed.facetType];
            if (!getEndpoint) {
                console.warn('No stakeholder community API for facet type:', parsed.facetType);
                renderEmptyState();
                return;
            }
            
            const apiUrl = getEndpoint(parsed.facetId);
            console.log('Calling existing facet stakeholder community API:', apiUrl);

            // Call the EXISTING facet stakeholder community API (same as object view uses)
            const response = await fetch(apiUrl, {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const responseData = await response.json();
            console.log('CR Stakeholder Community response from existing API:', responseData);

            // Extract stakeholders array
            const stakeholders = responseData.stakeholders || [];
            allStakeholders = stakeholders;
            filteredStakeholders = stakeholders;

            console.log('CR Stakeholder Community loaded:', stakeholders.length, 'stakeholders');
            
            if (stakeholders.length === 0) {
                renderEmptyState();
                return;
            }

            renderStakeholderCommunity();
        } catch (error) {
            console.error('Error loading CR stakeholder community:', error);
            const container = document.getElementById('crStakeholderCommunityContainer');
            if (container) {
                container.innerHTML = '<div class="error" style="text-align: center; padding: 2rem; color: #dc2626;">' + (window.I18n?.t('changeRequest.community.errorLoading') || 'Error loading stakeholder community data') + ': ' + (error.message || 'Unknown error') + '</div>';
            }
        }
    }

    // Render the stakeholder community section
    function renderStakeholderCommunity() {
        const container = document.getElementById('crStakeholderCommunityContainer');
        if (!container) return;

        // Get available object types and their counts
        const objectTypes = getObjectTypeCounts(allStakeholders);
        console.log('Object types for tabs:', objectTypes);

        // Generate tabs HTML
        const tabsHtml = generateTabsHtml(objectTypes);

        // Generate table HTML
        const tableHtml = generateTableHtml(filteredStakeholders);

        const html = `
            <div class="view-section stakeholder-community-section" style="margin-top: 1.5rem;">
                <div class="stakeholder-community-header" style="margin-bottom: 1rem;">
                    <div class="section-title" style="font-size: 0.9rem; font-weight: 600; color: var(--text-muted, #6b7280); margin: 0 0 1rem 0; text-transform: uppercase; letter-spacing: 0.04em;">
                        ${window.I18n?.t('changeRequest.community.title') || 'STAKEHOLDER COMMUNITY'}
                    </div>
                    <div class="stakeholder-community-tabs" style="display: flex; gap: 0.5rem; flex-wrap: wrap; margin-bottom: 1rem;">
                        ${tabsHtml}
                    </div>
                </div>
                <div class="stakeholder-community-content" style="background: var(--background-primary, #ffffff); border: 1px solid var(--border-color, #e9ecef); border-radius: 8px; overflow: hidden;">
                    ${tableHtml}
                </div>
            </div>
        `;

        container.innerHTML = html;

        // Attach event listeners to tabs
        attachTabListeners();
    }

    // Get object type counts
    function getObjectTypeCounts(stakeholders) {
        const counts = {};
        stakeholders.forEach(stakeholder => {
            const type = stakeholder.objectType || 'Unknown';
            counts[type] = (counts[type] || 0) + 1;
        });

        // Always include 'All' tab
        const result = { 'All': stakeholders.length };
        
        // Add other types that have data
        Object.keys(counts).forEach(type => {
            if (counts[type] > 0) {
                result[type] = counts[type];
            }
        });

        return result;
    }

    // Generate tabs HTML
    function generateTabsHtml(objectTypes) {
        const tabs = [];
        
        // Sort tabs: All first, then alphabetically
        const sortedTypes = Object.keys(objectTypes).sort((a, b) => {
            if (a === 'All') return -1;
            if (b === 'All') return 1;
            return a.localeCompare(b);
        });

        sortedTypes.forEach((type, index) => {
            const count = objectTypes[type];
            const isActive = index === 0 ? 'active' : '';
            tabs.push(`
                <button class="cr-stakeholder-community-tab ${isActive}" 
                        data-object-type="${type === 'All' ? 'all' : type.toLowerCase()}" 
                        style="padding: 0.5rem 1rem; background: ${isActive ? 'var(--secondary-color, #248567)' : 'var(--background-secondary, #f8f9fa)'}; 
                               color: ${isActive ? '#ffffff' : 'var(--text-secondary, #6c757d)'}; 
                               border: 1px solid ${isActive ? 'var(--secondary-color, #248567)' : 'var(--border-color, #e9ecef)'}; 
                               border-radius: 6px; cursor: pointer; 
                               font-weight: 500; font-size: 0.875rem; transition: all 0.2s ease;">
                    ${escapeHtml(type)}
                    <span class="tab-badge" style="background: ${isActive ? 'rgba(255, 255, 255, 0.3)' : 'rgba(0, 0, 0, 0.1)'}; 
                                                   color: ${isActive ? '#ffffff' : 'var(--text-secondary, #6c757d)'}; 
                                                   padding: 0.15rem 0.5rem; border-radius: 9999px; 
                                                   margin-left: 0.5rem; font-size: 0.75rem; font-weight: 600;">
                        ${count}
                    </span>
                </button>
            `);
        });

        return tabs.join('');
    }

    // Generate table HTML
    function generateTableHtml(stakeholders) {
        if (stakeholders.length === 0) {
            return `
                <div class="no-data" style="text-align: center; padding: 2rem; color: var(--text-tertiary, #adb5bd); font-style: italic;">
                    ${window.I18n?.t('changeRequest.community.noDataForFilter') || 'No stakeholders found for the selected filter.'}
                </div>
            `;
        }

        const rowsHtml = stakeholders.map((stakeholder, index) => {
            const objectType = stakeholder.objectType || 'Unknown';
            const icon = objectTypeIcons[objectType] || 'fa-circle';
            const rowClass = index % 2 === 0 ? 'even-row' : 'odd-row';
            
            // Build Object Name link
            const objectId = stakeholder.objectId;
            const objectName = escapeHtml(stakeholder.objectName || '');
            let objectNameHtml;
            if (objectId && objectTypeUrls[objectType]) {
                const url = objectTypeUrls[objectType](objectId);
                objectNameHtml = `<a href="${url}" class="stakeholder-link" style="color: var(--secondary-color, #248567); text-decoration: none; font-weight: 500;">${objectName}</a>`;
            } else {
                objectNameHtml = objectName;
            }
            
            // Build Name link
            const personId = stakeholder.personId;
            const personName = escapeHtml(stakeholder.name || '');
            const nameHtml = personId 
                ? `<a href="/view/people/${personId}" class="stakeholder-link" style="color: var(--secondary-color, #248567); text-decoration: none; font-weight: 500;">${personName}</a>`
                : personName;
            
            // Build Org Unit link
            const orgUnitId = stakeholder.orgUnitId;
            const orgUnitName = escapeHtml(stakeholder.orgUnit || '');
            const orgUnitHtml = orgUnitId && orgUnitName
                ? `<a href="/view/org-unit/${orgUnitId}" class="stakeholder-link" style="color: var(--secondary-color, #248567); text-decoration: none; font-weight: 500;">${orgUnitName}</a>`
                : orgUnitName;

            return `
                <tr class="${rowClass}" style="background: ${index % 2 === 0 ? 'var(--background-primary, #ffffff)' : 'var(--background-secondary, #f8f9fa)'};">
                    <td style="padding: 0.875rem 1.25rem;">
                        <div style="display: flex; align-items: center; gap: 0.5rem;">
                            <i class="fas ${icon}" style="color: var(--secondary-color, #248567); font-size: 1rem;"></i>
                            ${objectNameHtml}
                        </div>
                    </td>
                    <td style="padding: 0.875rem 1.25rem; color: var(--text-primary, #2c3e50);">${escapeHtml(stakeholder.role || '')}</td>
                    <td style="padding: 0.875rem 1.25rem;">
                        ${nameHtml}
                    </td>
                    <td style="padding: 0.875rem 1.25rem;">
                        ${orgUnitHtml}
                    </td>
                </tr>
            `;
        }).join('');

        return `
            <div class="stakeholder-community-table-container" style="background: var(--background-primary, #ffffff); border-radius: 8px; overflow: hidden;">
                <table class="stakeholder-community-table" style="width: 100%; border-collapse: collapse;">
                    <thead style="background: var(--background-secondary, #f8f9fa); border-bottom: 2px solid var(--border-color, #e9ecef);">
                        <tr>
                            <th style="padding: 0.875rem 1.25rem; text-align: left; font-weight: 600; color: var(--text-secondary, #6c757d); font-size: 0.8rem; letter-spacing: 0.04em; text-transform: uppercase;">
                                ${window.I18n?.t('changeRequest.community.objectName') || 'Object Name'}
                            </th>
                            <th style="padding: 0.875rem 1.25rem; text-align: left; font-weight: 600; color: var(--text-secondary, #6c757d); font-size: 0.8rem; letter-spacing: 0.04em; text-transform: uppercase;">
                                ${window.I18n?.t('changeRequest.community.role') || 'Role'}
                            </th>
                            <th style="padding: 0.875rem 1.25rem; text-align: left; font-weight: 600; color: var(--text-secondary, #6c757d); font-size: 0.8rem; letter-spacing: 0.04em; text-transform: uppercase;">
                                ${window.I18n?.t('changeRequest.community.name') || 'Name'}
                            </th>
                            <th style="padding: 0.875rem 1.25rem; text-align: left; font-weight: 600; color: var(--text-secondary, #6c757d); font-size: 0.8rem; letter-spacing: 0.04em; text-transform: uppercase;">
                                ${window.I18n?.t('changeRequest.community.orgUnit') || 'Org Unit'}
                            </th>
                        </tr>
                    </thead>
                    <tbody>
                        ${rowsHtml}
                    </tbody>
                </table>
                <div class="table-footer" style="padding: 0.75rem 1.25rem; background: var(--background-secondary, #f8f9fa); border-top: 1px solid var(--border-color, #e9ecef); text-align: right; color: var(--text-secondary, #6c757d); font-size: 0.875rem;">
                    ${stakeholders.length} ${stakeholders.length !== 1 ? (window.I18n?.t('changeRequest.community.records') || 'records') : (window.I18n?.t('changeRequest.community.record') || 'record')}
                </div>
            </div>
        `;
    }

    // Attach event listeners to tabs
    function attachTabListeners() {
        const tabs = document.querySelectorAll('.cr-stakeholder-community-tab');
        tabs.forEach(tab => {
            tab.addEventListener('click', function() {
                // Remove active class from all tabs
                tabs.forEach(t => {
                    t.classList.remove('active');
                    t.style.background = 'var(--background-secondary, #f8f9fa)';
                    t.style.color = 'var(--text-secondary, #6c757d)';
                    t.style.borderColor = 'var(--border-color, #e9ecef)';
                    const badge = t.querySelector('.tab-badge');
                    if (badge) {
                        badge.style.background = 'rgba(0, 0, 0, 0.1)';
                        badge.style.color = 'var(--text-secondary, #6c757d)';
                    }
                });

                // Add active class to clicked tab
                this.classList.add('active');
                this.style.background = 'var(--secondary-color, #248567)';
                this.style.color = '#ffffff';
                this.style.borderColor = 'var(--secondary-color, #248567)';
                const activeBadge = this.querySelector('.tab-badge');
                if (activeBadge) {
                    activeBadge.style.background = 'rgba(255, 255, 255, 0.3)';
                    activeBadge.style.color = '#ffffff';
                }

                // Filter stakeholders
                const objectType = this.getAttribute('data-object-type');
                console.log('Tab clicked, filtering by:', objectType);
                
                if (objectType === 'all') {
                    filteredStakeholders = allStakeholders;
                } else {
                    filteredStakeholders = allStakeholders.filter(s => 
                        (s.objectType || '').toLowerCase() === objectType
                    );
                }

                console.log('Filtered stakeholders:', filteredStakeholders.length);

                // Update table
                const contentContainer = document.querySelector('.stakeholder-community-content');
                if (contentContainer) {
                    contentContainer.innerHTML = generateTableHtml(filteredStakeholders);
                }
            });
        });
    }

    // Render empty state
    function renderEmptyState() {
        const container = document.getElementById('crStakeholderCommunityContainer');
        if (!container) return;

        const html = `
            <div class="view-section stakeholder-community-section" style="margin-top: 1.5rem;">
                <div class="stakeholder-community-header" style="margin-bottom: 1rem;">
                    <div class="section-title" style="font-size: 0.9rem; font-weight: 600; color: var(--text-muted, #6b7280); margin: 0 0 1rem 0; text-transform: uppercase; letter-spacing: 0.04em;">
                        ${window.I18n?.t('changeRequest.community.title') || 'STAKEHOLDER COMMUNITY'}
                    </div>
                </div>
                <div class="stakeholder-community-content" style="background: var(--background-primary, #ffffff); border: 1px solid var(--border-color, #e9ecef); border-radius: 8px; padding: 2rem; text-align: center;">
                    <div class="empty-state" style="color: var(--text-tertiary, #adb5bd);">
                        <i class="fas fa-info-circle" style="font-size: 2rem; margin-bottom: 1rem; opacity: 0.5;"></i>
                        <p style="margin: 0;">${window.I18n?.t('changeRequest.community.noData') || 'No stakeholder community data available for this change request.'}</p>
                    </div>
                </div>
            </div>
        `;

        container.innerHTML = html;
    }

    // Escape HTML to prevent XSS
    function escapeHtml(str) {
        if (str == null) return '';
        return String(str).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;').replace(/'/g,'&#039;');
    }

    // Export functions to global scope
    window.CRStakeholderCommunity = {
        init: initStakeholderCommunity,
        loadStakeholderCommunity: loadStakeholderCommunity
    };

})();

