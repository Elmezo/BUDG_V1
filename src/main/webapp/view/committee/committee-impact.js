// Committee Impact View JavaScript - Display read-only capability relationships
console.log('=== COMMITTEE IMPACT VIEW SCRIPT LOADING ===');

(function() {
    console.log('=== COMMITTEE IMPACT VIEW SCRIPT LOADED ===');
    
    // Helper function to escape HTML
    function escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
    
    // Load Committee Impact data
    async function loadCommitteeImpact(committeeId) {
        try {
            console.log('Loading committee impact for ID:', committeeId);
            
            const response = await fetch(`/api/committee-impact/${committeeId}/capabilities`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) {
                if (response.status === 404) {
                    console.log('No capability relationships found');
                    renderImpactView([]);
                    return;
                }
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const data = await response.json();
            const relationships = Array.isArray(data) ? data : [];
            
            console.log('Loaded capability relationships:', relationships.length);
            renderImpactView(relationships);

        } catch (error) {
            console.error('Error loading committee impact:', error);
            renderImpactView([]);
        }
    }

    function renderImpactView(relationships) {
        const container = document.getElementById('committeeImpactContainer');
        if (!container) {
            console.warn('Committee Impact container not found');
            return;
        }

        // Check if there are any relationships
        const hasData = relationships && relationships.length > 0;

        if (!hasData) {
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

        // Render sub-tabs and content (only show if there's data)
        const impI = window.I18n?.t.bind(window.I18n) || (k => k);
        let html = `
            <div class="view-section" style="grid-column:1/-1;">
                <div class="relationships-container">
                    <div class="relationships-sub-tabs">
                        <button class="sub-tab active" data-sub-tab="capability">${impI('committee.capability')}</button>
                    </div>
                    <div class="relationships-content">
                        <div id="impactCapabilityViewContent" class="sub-tab-content active" style="display:block;">
                            <div class="relationships-hierarchy">
                                <div class="hierarchy-header">
                                    <div class="hierarchy-title">${impI('committee.capability')}</div>
                                    <div class="hierarchy-actions">
                                        <button type="button" class="btn btn-secondary">
                                            <i class="fas fa-list"></i> ${impI('committee.list')}
                                        </button>
                                        <button type="button" class="btn btn-secondary">
                                            <i class="fas fa-search-plus"></i> ${impI('committee.searchAndAdd')}
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
                                                <th>
                                                    <div class="th-content">
                                                        <span>${impI('committee.relationshipType')}</span>
                                                        <i class="fas fa-sort"></i>
                                                    </div>
                                                </th>
                                                <th>
                                                    <div class="th-content">
                                                        <span>${impI('committee.capability')}</span>
                                                        <i class="fas fa-sort"></i>
                                                    </div>
                                                </th>
                                            </tr>
                                        </thead>
                                        <tbody id="capabilityImpactViewTableBody">
                                            ${renderCapabilityViewTableBody(relationships)}
                                        </tbody>
                                    </table>
                                </div>
                                <div class="hierarchy-footer" id="capabilityImpactViewFooter">
                                    ${relationships.length} ${relationships.length !== 1 ? impI('committee.records') : impI('committee.record')}
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        `;

        container.innerHTML = html;
    }

    function renderCapabilityViewTableBody(relationships) {
        if (!relationships || relationships.length === 0) {
            return `
                <tr>
                    <td colspan="2" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">
                        No capability relationships found
                    </td>
                </tr>
            `;
        }

        return relationships.map(rel => {
            const relationTypeName = escapeHtml(rel.relationTypeName || rel.relationType || 'Unnamed Type');
            const capabilityName = rel.capabilityName || 'Unnamed Capability';
            const capabilityId = rel.capabilityId;
            
            // Create link to capability view page - use green color like other impact views
            const capabilityLink = capabilityId 
                ? `<a href="/view/capability/${capabilityId}" class="entity-link" style="color: var(--secondary-color, #248567); text-decoration: none;" title="${(window.I18n?.t('committeeImpact.messages.view') || 'View')} ${escapeHtml(capabilityName)}">${escapeHtml(capabilityName)}</a>`
                : escapeHtml(capabilityName);

            return `
                <tr>
                    <td>${relationTypeName}</td>
                    <td>${capabilityLink}</td>
                </tr>
            `;
        }).join('');
    }

    // Export function to global scope
    window.loadCommitteeImpact = loadCommitteeImpact;
    
    console.log('Committee Impact View function exported to window');
})();

