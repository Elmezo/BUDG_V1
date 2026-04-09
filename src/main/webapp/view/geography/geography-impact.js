// Geography Impact View JavaScript - Read-only display for Legal Entity sub-tab
console.log('Geography Impact View script loading...');

(function() {
    console.log('Geography Impact View script executing...');
    
    let currentGeographyId = null;
    let legalEntityRelationships = [];

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
            case 'legal':
                url = `/view/LegalEntity/${encodeURIComponent(id)}`;
                break;
            default:
                return escapeHtml(name);
        }
        
        const viewText = window.I18n?.t('geographyImpact.messages.view') || 'View';
        return `<a href="${url}" class="entity-link" style="color: var(--secondary-color, #248567); text-decoration: none;" title="${viewText} ${escapeHtml(name)}">${escapeHtml(name)}</a>`;
    }

    // Initialize Impact view
    async function loadGeographyImpact(geographyId) {
        console.log('loadGeographyImpact called with ID:', geographyId);
        currentGeographyId = geographyId;
        
        if (!geographyId) {
            console.error('No geography ID provided to loadGeographyImpact');
            const container = document.getElementById('geographyImpactContainer');
            if (container) {
                container.innerHTML = '<div class="view-section" style="grid-column:1/-1; padding: 2rem; text-align: center; color: var(--danger, #dc3545);"><p>Error: No geography ID provided</p></div>';
            }
            return;
        }
        
        try {
            await loadImpactViewData();
        } catch (error) {
            console.error('Error in loadGeographyImpact:', error);
            const container = document.getElementById('geographyImpactContainer');
            if (container) {
                container.innerHTML = `<div class="view-section" style="grid-column:1/-1; padding: 2rem; text-align: center; color: var(--danger, #dc3545);"><p>Error loading impact: ${error.message}</p></div>`;
            }
        }
    }

    // Load all Impact view data
    async function loadImpactViewData() {
        try {
            console.log('loadImpactViewData: Starting to load impact data...');
            
            // Load relationship data
            await loadLegalEntityRelationshipsView();
            
            console.log('loadImpactViewData: Data loaded, rendering view. Relationships count:', legalEntityRelationships.length);

            // Render the view
            renderImpactView();
            
            console.log('loadImpactViewData: View rendered successfully');

        } catch (error) {
            console.error('Error loading Impact view data:', error);
            const container = document.getElementById('geographyImpactContainer');
            if (container) {
                container.innerHTML = `
                    <div class="view-section" style="grid-column:1/-1; padding: 2rem; text-align: center; color: var(--danger, #dc3545);">
                        <i class="fas fa-exclamation-triangle" style="font-size: 2rem; margin-bottom: 1rem;"></i>
                        <p>Error loading impact data: ${error.message}</p>
                    </div>
                `;
            }
        }
    }

    // Load legal entity relationships for view
    async function loadLegalEntityRelationshipsView() {
        try {
            console.log('Loading legal entity relationships for view, geography:', currentGeographyId);
            
            if (!currentGeographyId) {
                console.error('No geography ID available for loading relationships');
                legalEntityRelationships = [];
                return;
            }

            const url = `/api/legal-impact/geographies/${currentGeographyId}/legal-entities`;
            console.log('Fetching from URL:', url);

            const response = await fetch(url, {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });

            console.log('API response status:', response.status, response.statusText);

            if (!response.ok) {
                if (response.status === 404) {
                    console.log('No legal entity relationships found (404)');
                    legalEntityRelationships = [];
                    return;
                }
                const errorText = await response.text();
                console.error('API error response:', errorText);
                throw new Error(`HTTP error! status: ${response.status}, message: ${errorText}`);
            }

            const data = await response.json();
            console.log('Legal entity relationships view API response:', data);
            console.log('Number of relationships:', Array.isArray(data) ? data.length : 0);

            legalEntityRelationships = Array.isArray(data) ? data : [];

        } catch (error) {
            console.error('Error loading legal entity relationships for view:', error);
            legalEntityRelationships = [];
            // Don't throw - just set empty array so render can show "no data" message
        }
    }

    // Render Impact view with sub-tabs
    function renderImpactView() {
        const container = document.getElementById('geographyImpactContainer');
        if (!container) {
            console.error('geographyImpactContainer not found');
            return;
        }

        // Determine which sub-tabs to show based on data availability
        const hasLegalData = legalEntityRelationships.length > 0;

        if (!hasLegalData) {
            container.innerHTML = `
                <div class="view-section" style="grid-column:1/-1; padding: 2rem; text-align: center;">
                    <div style="color: var(--text-muted, #6b7280);">
                        <i class="fas fa-info-circle" style="font-size: 2rem; margin-bottom: 1rem; opacity: 0.5;"></i>
                        <p>${window.I18n?.t('message.noImpactRelationships') || 'No impact relationships found'}</p>
                    </div>
                </div>
            `;
            return;
        }

        // Build sub-tabs HTML
        let subTabsHtml = '';
        let subTabsContentHtml = '';

        if (hasLegalData) {
            subTabsHtml += '<button class="sub-tab active" data-sub-tab="legal">Legal Entity</button>';
            subTabsContentHtml += `
                <!-- Legal Entity Sub-tab -->
                <div id="impactLegalViewContent" class="sub-tab-content active">
                <div class="relationships-hierarchy">
                    <div class="hierarchy-header">
                        <div class="hierarchy-title">LEGAL ENTITY</div>
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
                                    <th><div class="th-content"><span>Legal Entity</span><i class="fas fa-sort"></i></div></th>
                                    <th><div class="th-content" style="justify-content: center;"><span>Legal Entity Owner</span><i class="fas fa-sort"></i></div></th>
                                </tr>
                            </thead>
                            <tbody>
                                ${renderLegalEntityViewTableBody()}
                            </tbody>
                        </table>
                    </div>
                    <div class="hierarchy-footer">
                        ${legalEntityRelationships.length} record${legalEntityRelationships.length !== 1 ? 's' : ''}
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
                
                /* Reverse relationship styling - distinct from forward relationships */
                .reverse-relationship-row {
                    background-color: var(--reverse-relationship-bg, #f8fafc);
                    border-left: 3px solid var(--reverse-relationship-border, #3b82f6);
                }
                
                .reverse-relationship-row:hover {
                    background-color: var(--reverse-relationship-bg-hover, #f1f5f9);
                }
                
                .reverse-relationship-entity {
                    display: flex;
                    align-items: center;
                }
                
                .reverse-relationship-entity .entity-name {
                    font-weight: 500;
                }
                
                .reverse-relationship-owner {
                    color: var(--text-secondary, #64748b);
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

        // Setup sub-tab switching (if multiple sub-tabs in future)
        setupImpactViewSubTabs();
    }

    // Render legal entity view table body (reverse relationship view)
    function renderLegalEntityViewTableBody() {
        if (legalEntityRelationships.length === 0) {
            return '<tr class="reverse-relationship-row"><td colspan="3" style="text-align: center; padding: 2rem; color: var(--text-muted, #6b7280);">No legal entity relationships found</td></tr>';
        }

        return legalEntityRelationships.map(relationship => {
            // Use reverse name if available, otherwise use regular relation type name
            const relationTypeDisplay = relationship.relationTypeReverseName || relationship.relationTypeName || 'N/A';
            
            // Prefer LongName, fallback to ShortName or other names
            const legalName = relationship.legalLongName || relationship.legalEntityName || relationship.legalShortName || 'N/A';
            const legalId = relationship.legalId || relationship.legalEntityId;
            const legalLink = createEntityLink('legal', legalId, legalName);
            
            // Get owner name
            const ownerName = relationship.legalOwnerName || relationship.ownerName || 'No owner';
            
            return `
                <tr class="reverse-relationship-row">
                    <td>${escapeHtml(relationTypeDisplay)}</td>
                    <td>
                        <div class="entity-info reverse-relationship-entity">
                            <i class="fas fa-building" style="color: var(--text-secondary, #64748b); margin-right: 0.5rem;"></i>
                            <span class="entity-name">${legalLink}</span>
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

    // Setup Impact view sub-tabs
    function setupImpactViewSubTabs() {
        console.log('Setting up Impact view sub-tabs...');
        
        const subTabs = document.querySelectorAll('#geographyImpactContainer .sub-tab');
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
                const subTabContents = document.querySelectorAll('#geographyImpactContainer .sub-tab-content');
                subTabContents.forEach(function(content) {
                    content.classList.remove('active');
                    content.style.display = 'none';
                });
                
                // Show selected sub-tab content
                let targetSubTab;
                if (subTabName === 'legal') {
                    targetSubTab = document.getElementById('impactLegalViewContent');
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

    // Expose function globally for use by geography.js
    window.loadGeographyImpact = loadGeographyImpact;
    
    console.log('Geography Impact View script loaded. loadGeographyImpact function exposed to window.');

})();
