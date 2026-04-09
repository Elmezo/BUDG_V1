// Interface Impact View JavaScript - Display read-only process relationships
console.log('=== INTERFACE IMPACT VIEW SCRIPT LOADING ===');

(function() {
    console.log('=== INTERFACE IMPACT VIEW SCRIPT LOADED ===');
    
    // Helper function to escape HTML
    function escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
    
    // Helper function to create entity link
    function createEntityLink(entityId, entityName, entityType) {
        if (!entityId) return escapeHtml(entityName || 'Unnamed');
        const viewText = window.I18n?.t('interfaceImpact.messages.view') || 'View';
        return `<a href="/view/${entityType}/${entityType}.html?id=${entityId}" class="entity-link" style="color: var(--secondary-color, #248567); text-decoration: none;" title="${viewText} ${escapeHtml(entityName)}">${escapeHtml(entityName || 'Unnamed')}</a>`;
    }
    
    // Load Interface Impact data
    async function loadInterfaceImpact(interfaceId) {
        try {
            console.log('Loading interface impact for ID:', interfaceId);
            
            const response = await fetch(`/api/interface-impact/${interfaceId}/processes`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) {
                if (response.status === 404) {
                    console.log('No process relationships found');
                    renderImpactView([]);
                    return;
                }
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const data = await response.json();
            const relationships = Array.isArray(data) ? data : [];
            
            console.log('Loaded process relationships:', relationships.length);
            renderImpactView(relationships);

        } catch (error) {
            console.error('Error loading interface impact:', error);
            renderImpactView([]);
        }
    }

    function renderImpactView(relationships) {
        const container = document.getElementById('interfaceImpactContainer');
        if (!container) {
            console.warn('Interface Impact container not found');
            return;
        }

        // Check if there are any relationships
        const hasData = relationships && relationships.length > 0;

        if (!hasData) {
            container.innerHTML = `
                <div style="padding: 2rem; text-align: center; color: var(--text-muted, #6b7280);">
                    <i class="fas fa-info-circle" style="font-size: 2rem; margin-bottom: 1rem; opacity: 0.5;"></i>
                    <p>${window.I18n?.t('message.noImpactRelationships') || 'No impact relationships found'}</p>
                </div>
            `;
            return;
        }

        // Render sub-tabs and content (only show if there's data)
        let html = `
            <div class="relationships-container">
                <div class="relationships-sub-tabs">
                    <button class="sub-tab active" data-sub-tab="process">Process</button>
                </div>
                <div class="relationships-content">
                    <div id="impactProcessViewContent" class="sub-tab-content active" style="display:block;">
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
                                            <th>
                                                <div class="th-content">
                                                    <span>Relationship Type</span>
                                                    <i class="fas fa-sort"></i>
                                                </div>
                                            </th>
                                            <th>
                                                <div class="th-content">
                                                    <span>Name</span>
                                                    <i class="fas fa-sort"></i>
                                                </div>
                                            </th>
                                        </tr>
                                    </thead>
                                    <tbody id="processImpactViewTableBody">
                                        ${renderProcessViewTableBody(relationships)}
                                    </tbody>
                                </table>
                            </div>
                            <div class="hierarchy-footer" id="processImpactViewFooter">
                                ${relationships.length} record${relationships.length !== 1 ? 's' : ''}
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        `;

        container.innerHTML = html;
    }

    function renderProcessViewTableBody(relationships) {
        if (!relationships || relationships.length === 0) {
            return `
                <tr>
                    <td colspan="2" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">
                        No process relationships found
                    </td>
                </tr>
            `;
        }

        return relationships.map(rel => {
            const relationTypeName = escapeHtml(rel.relationTypeName || rel.relationType || 'Unnamed Type');
            const processName = rel.processName || 'Unnamed Process';
            const processId = rel.processId;
            
            // Create link to process view page - use green color like other impact views
            const processLink = createEntityLink(processId, processName, 'process');

            return `
                <tr>
                    <td>${relationTypeName}</td>
                    <td>${processLink}</td>
                </tr>
            `;
        }).join('');
    }

    // Export function to global scope
    window.loadInterfaceImpact = loadInterfaceImpact;
    
    console.log('Interface Impact View function exported to window');
})();

