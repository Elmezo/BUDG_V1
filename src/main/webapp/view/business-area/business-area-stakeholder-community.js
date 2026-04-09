// Business Area Stakeholder Community JavaScript
(function() {
    let currentBusinessAreaId = null;
    let allStakeholders = [];
    let filteredStakeholders = [];

    // Object type icons mapping
    const objectTypeIcons = {
        'Process': 'fa-diagram-project',
        'Product': 'fa-tag',
        'Project': 'fa-folder',
        'Glossary': 'fa-book',
        'Capability': 'fa-gem',
        'Policy': 'fa-file-lines',
        'System': 'fa-desktop'
    };
    
    // Object type view URL mapping
    const objectTypeUrls = {
        'Process': (id) => `/view/process/${id}`,
        'Product': (id) => `/view/product/${id}`,
        'Project': (id) => `/view/project/${id}`,
        'Glossary': (id) => `/view/glossary/${id}`,
        'Capability': (id) => `/view/capability/${id}`,
        'Policy': (id) => `/view/policy/${id}`,
        'System': (id) => `/view/system/${id}`
    };

    // Initialize stakeholder community view
    async function initStakeholderCommunity(businessAreaId) {
        currentBusinessAreaId = businessAreaId;
        await loadStakeholderCommunity();
    }

    // Load stakeholder community data from API
    async function loadStakeholderCommunity() {
        try {
            const container = document.getElementById('businessAreaStakeholderCommunityContainer');
            if (!container) return;

            container.innerHTML = `<div class="loading">${window.I18n?.t('message.loadingStakeholderCommunity') || 'Loading stakeholder community...'}</div>`;

            // Call API to get stakeholder community data
            const response = await fetch(`/api/businessarea/stakeholder-community?businessarea_id=${currentBusinessAreaId}`, {
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

            // Extract stakeholders array
            const stakeholders = responseData.stakeholders || [];
            allStakeholders = stakeholders;
            filteredStakeholders = stakeholders;

            if (stakeholders.length === 0) {
                renderEmptyState();
                return;
            }

            renderStakeholderCommunity();
        } catch (error) {
            console.error('Error loading stakeholder community:', error);
            const container = document.getElementById('businessAreaStakeholderCommunityContainer');
            if (container) {
                container.innerHTML = '<div class="error">Error loading stakeholder community data: ' + (error.message || 'Unknown error') + '</div>';
            }
        }
    }

    // Render the stakeholder community section
    function renderStakeholderCommunity() {
        const container = document.getElementById('businessAreaStakeholderCommunityContainer');
        if (!container) return;

        // Get available object types and their counts
        const objectTypes = getObjectTypeCounts(allStakeholders);

        // Generate tabs HTML
        const tabsHtml = generateTabsHtml(objectTypes);

        // Generate table HTML
        const tableHtml = generateTableHtml(filteredStakeholders);

        const html = `
            <div class="view-section stakeholder-community-section" style="grid-column: 1/-1; margin-top: 2rem;">
                <div class="stakeholder-community-header" style="margin-bottom: 1rem;">
                    <h3 class="stakeholder-community-title" style="font-size: 1.25rem; font-weight: 600; color: var(--text-primary, #2c3e50); margin: 0 0 1rem 0; text-transform: uppercase; letter-spacing: 0.05em;">
                        ${window.I18n?.t('businessArea.community.title') || 'STAKEHOLDER COMMUNITY'}
                    </h3>
                    <div class="stakeholder-community-tabs" style="display: flex; gap: 0.5rem; flex-wrap: wrap; margin-bottom: 1rem;">
                        ${tabsHtml}
                    </div>
                </div>
                <div class="stakeholder-community-content" style="background: var(--background-primary, #ffffff); border: 1px solid var(--border-color, #e9ecef); border-radius: 8px; padding: 1rem; box-shadow: var(--shadow-light, 0 2px 10px rgba(0, 0, 0, 0.1));">
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
                <button class="stakeholder-community-tab ${isActive}" 
                        data-object-type="${type === 'All' ? 'all' : type.toLowerCase()}" 
                        style="padding: 0.5rem 1rem; background: ${isActive ? 'var(--background-primary, #ffffff)' : 'var(--background-secondary, #f8f9fa)'}; 
                               color: ${isActive ? 'var(--secondary-color, #248567)' : 'var(--text-secondary, #6c757d)'}; 
                               border: 1px solid ${isActive ? 'var(--secondary-color, #248567)' : 'var(--border-color, #e9ecef)'}; 
                               border-radius: 6px; cursor: pointer; 
                               font-weight: 500; font-size: 0.875rem; transition: all 0.2s ease;
                               border-bottom: ${isActive ? '2px solid var(--secondary-color, #248567)' : '2px solid transparent'};">
                    ${escapeHtml(type)}
                    <span class="tab-badge" style="background: ${isActive ? 'rgba(36, 133, 103, 0.15)' : 'rgba(0, 0, 0, 0.1)'}; 
                                                   color: ${isActive ? 'var(--secondary-color, #248567)' : 'var(--text-secondary, #6c757d)'}; 
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
                    ${window.I18n?.t('businessArea.community.noDataForFilter') || 'No stakeholders found for the selected filter.'}
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
                objectNameHtml = `<a href="${url}" class="stakeholder-link" style="color: var(--secondary-color, #248567); text-decoration: none;">${objectName}</a>`;
            } else {
                objectNameHtml = objectName;
            }
            
            // Build Name link
            const personId = stakeholder.personId;
            const personName = escapeHtml(stakeholder.name || '');
            const nameHtml = personId 
                ? `<a href="/view/people/${personId}" class="stakeholder-link" style="color: var(--secondary-color, #248567); text-decoration: none;">${personName}</a>`
                : personName;
            
            // Build Org Unit link
            const orgUnitId = stakeholder.orgUnitId;
            const orgUnitName = escapeHtml(stakeholder.orgUnit || '');
            const orgUnitHtml = orgUnitId && orgUnitName
                ? `<a href="/view/org-unit/${orgUnitId}" class="stakeholder-link" style="color: var(--secondary-color, #248567); text-decoration: none;">${orgUnitName}</a>`
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
            <div class="stakeholder-community-table-container" style="background: var(--background-primary, #ffffff); border-radius: 8px; overflow: hidden; border: 1px solid var(--border-color, #e9ecef);">
                <table class="stakeholder-community-table" style="width: 100%; border-collapse: collapse;">
                    <thead style="background: var(--background-secondary, #f8f9fa); border-bottom: 2px solid var(--border-color, #e9ecef);">
                        <tr>
                            <th style="padding: 0.875rem 1.25rem; text-align: left; font-weight: 600; color: var(--text-secondary, #6c757d); font-size: 0.8rem; letter-spacing: 0.04em; text-transform: uppercase;">
                                ${window.I18n?.t('businessArea.community.objectName') || 'Object Name'}
                            </th>
                            <th style="padding: 0.875rem 1.25rem; text-align: left; font-weight: 600; color: var(--text-secondary, #6c757d); font-size: 0.8rem; letter-spacing: 0.04em; text-transform: uppercase;">
                                ${window.I18n?.t('businessArea.community.role') || 'Role'}
                            </th>
                            <th style="padding: 0.875rem 1.25rem; text-align: left; font-weight: 600; color: var(--text-secondary, #6c757d); font-size: 0.8rem; letter-spacing: 0.04em; text-transform: uppercase;">
                                ${window.I18n?.t('businessArea.community.name') || 'Name'}
                            </th>
                            <th style="padding: 0.875rem 1.25rem; text-align: left; font-weight: 600; color: var(--text-secondary, #6c757d); font-size: 0.8rem; letter-spacing: 0.04em; text-transform: uppercase;">
                                ${window.I18n?.t('businessArea.community.orgUnit') || 'Org Unit'}
                            </th>
                        </tr>
                    </thead>
                    <tbody>
                        ${rowsHtml}
                    </tbody>
                </table>
                <div class="table-footer" style="padding: 0.75rem 1.25rem; background: var(--background-secondary, #f8f9fa); border-top: 1px solid var(--border-color, #e9ecef); text-align: right; color: var(--text-secondary, #6c757d); font-size: 0.875rem;">
                    ${stakeholders.length} ${stakeholders.length !== 1 ? (window.I18n?.t('businessArea.community.records') || 'records') : (window.I18n?.t('businessArea.community.record') || 'record')}
                </div>
            </div>
        `;
    }

    // Attach event listeners to tabs
    function attachTabListeners() {
        const tabs = document.querySelectorAll('.stakeholder-community-tab');
        tabs.forEach(tab => {
            tab.addEventListener('click', function() {
                // Remove active class from all tabs
                tabs.forEach(t => {
                    t.classList.remove('active');
                    t.style.background = 'var(--background-secondary, #f8f9fa)';
                    t.style.color = 'var(--text-secondary, #6c757d)';
                    t.style.borderColor = 'var(--border-color, #e9ecef)';
                    t.style.borderBottom = '2px solid transparent';
                });

                // Add active class to clicked tab
                this.classList.add('active');
                this.style.background = 'var(--background-primary, #ffffff)';
                this.style.color = 'var(--secondary-color, #248567)';
                this.style.borderColor = 'var(--secondary-color, #248567)';
                this.style.borderBottom = '2px solid var(--secondary-color, #248567)';

                // Update badge colors
                tabs.forEach(t => {
                    const badge = t.querySelector('.tab-badge');
                    if (badge) {
                        if (t === this) {
                            badge.style.background = 'rgba(36, 133, 103, 0.15)';
                            badge.style.color = 'var(--secondary-color, #248567)';
                        } else {
                            badge.style.background = 'rgba(0, 0, 0, 0.1)';
                            badge.style.color = 'var(--text-secondary, #6c757d)';
                        }
                    }
                });

                // Filter stakeholders
                const objectType = this.getAttribute('data-object-type');
                if (objectType === 'all') {
                    filteredStakeholders = allStakeholders;
                } else {
                    filteredStakeholders = allStakeholders.filter(s => 
                        (s.objectType || '').toLowerCase() === objectType
                    );
                }

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
        const container = document.getElementById('businessAreaStakeholderCommunityContainer');
        if (!container) return;

        const html = `
            <div class="view-section stakeholder-community-section" style="grid-column: 1/-1; margin-top: 2rem;">
                <div class="stakeholder-community-header" style="margin-bottom: 1rem;">
                    <h3 class="stakeholder-community-title" style="font-size: 1.25rem; font-weight: 600; color: var(--text-primary, #2c3e50); margin: 0 0 1rem 0; text-transform: uppercase; letter-spacing: 0.05em;">
                        ${window.I18n?.t('businessArea.community.title') || 'STAKEHOLDER COMMUNITY'}
                    </h3>
                </div>
                <div class="stakeholder-community-content" style="background: var(--background-primary, #ffffff); border: 1px solid var(--border-color, #e9ecef); border-radius: 8px; padding: 2rem; text-align: center; box-shadow: var(--shadow-light, 0 2px 10px rgba(0, 0, 0, 0.1));">
                    <div class="no-data" style="color: var(--text-tertiary, #adb5bd); font-style: italic;">
                        ${window.I18n?.t('businessArea.community.noData') || 'No stakeholder data available for this business area.'}
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
    window.BusinessAreaStakeholderCommunity = {
        init: initStakeholderCommunity,
        loadStakeholderCommunity: loadStakeholderCommunity
    };

})();

