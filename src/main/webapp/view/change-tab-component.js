/**
 * Change Tab Component
 * Reusable component for displaying Change Requests and Workflow sub-tabs
 * across all facet view pages.
 * 
 * Usage:
 * 1. Include this script in your HTML
 * 2. Call ChangeTabComponent.initialize(facetType, facetId, containerId)
 */
(function () {
    'use strict';

    const ChangeTabComponent = {
        currentFacetType: null,
        currentFacetId: null,
        currentSubTab: 'change-requests',
        changeRequests: [], // Store loaded change requests for blocking logic
        currentUserId: null, // Store current user ID

        /**
         * Initialize the change tab component
         * @param {string} facetType - The type of facet (e.g., 'dataset', 'system')
         * @param {number} facetId - The ID of the current facet object
         * @param {string} containerId - The ID of the container element
         */
        initialize: function (facetType, facetId, containerId) {
            this.currentFacetType = facetType;
            this.currentFacetId = facetId;

            const container = document.getElementById(containerId);
            if (!container) {
                console.error('Change tab container not found:', containerId);
                return;
            }

            // Render the component
            this.render(container);

            // Load initial data
            this.loadChangeRequests();
        },

        /**
         * Render the change tab component HTML
         */
        render: function (container) {
            const t = (k) => (window.I18n && window.I18n.t(k)) || k;
            const changeRequestsLabel = t('tab.changeRequests');
            const workflowLabel = t('tab.workflow');
            const sectionChangeRequest = t('changeTab.sectionTitleChangeRequest');
            const sectionWorkflow = t('changeTab.sectionTitleWorkflow');
            const loadingCR = t('changeTab.loadingChangeRequests');
            const noWorkflowData = t('changeTab.noWorkflowData');
            container.innerHTML = `
                <div class="view-section full-width">
                    <div class="change-tab-component">
                        <!-- Sub-tabs -->
                        <div class="change-sub-tabs">
                            <button class="change-sub-tab active" data-subtab="change-requests">${changeRequestsLabel}</button>
                            <button class="change-sub-tab" data-subtab="workflow">${workflowLabel}</button>
                        </div>
                        
                        <!-- Sub-tab content -->
                        <div class="change-sub-content">
                            <div id="changeRequestsContent" class="change-sub-panel active">
                                <div class="section-header">
                                    <div class="section-title">${sectionChangeRequest}</div>
                                    <div class="section-actions">
                                        <button class="btn-icon" title="Settings">
                                            <i class="fas fa-cog"></i>
                                        </button>
                                    </div>
                                </div>
                                <div id="changeRequestsList" class="change-requests-list">
                                    <div class="loading-state">
                                        <i class="fas fa-spinner fa-spin"></i> ${loadingCR}
                                    </div>
                                </div>
                            </div>
                            <div id="workflowContent" class="change-sub-panel">
                                <div class="section-header">
                                    <div class="section-title">${sectionWorkflow}</div>
                                </div>
                                <div id="workflowList" class="workflow-list">
                                    <div class="empty-state">
                                        <i class="fas fa-project-diagram"></i>
                                        <p>${noWorkflowData}</p>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            `;

            // Setup sub-tab event listeners
            this.setupSubTabListeners(container);
        },

        /**
         * Styles are now handled by static CSS in glossary.css
         * This method is kept for backward compatibility but does nothing
         */
        addStyles: function () {
            // Styles are now in glossary.css - no dynamic injection needed
            return;
        },

        /**
         * Setup sub-tab click listeners
         */
        setupSubTabListeners: function (container) {
            const self = this;
            const subTabs = container.querySelectorAll('.change-sub-tab');

            subTabs.forEach(tab => {
                tab.addEventListener('click', function () {
                    const subtab = this.getAttribute('data-subtab');

                    // Update active tab
                    subTabs.forEach(t => t.classList.remove('active'));
                    this.classList.add('active');

                    // Update active panel
                    container.querySelectorAll('.change-sub-panel').forEach(panel => {
                        panel.classList.remove('active');
                    });

                    if (subtab === 'change-requests') {
                        container.querySelector('#changeRequestsContent').classList.add('active');
                        self.loadChangeRequests();
                    } else if (subtab === 'workflow') {
                        container.querySelector('#workflowContent').classList.add('active');
                        self.loadWorkflow();
                    }

                    self.currentSubTab = subtab;
                });
            });
        },

        /**
         * Load change requests for the current facet
         */
        loadChangeRequests: async function () {
            const listContainer = document.getElementById('changeRequestsList');
            if (!listContainer) return;

            // Get current user ID first
            await this.fetchCurrentUserId();

            // Get all possible reference variations (e.g., "Data Set 47", "Dataset 47")
            const referenceVariations = this.getReferenceVariations(this.currentFacetType, this.currentFacetId);

            console.log('Loading change requests for references:', referenceVariations);

            try {
                // Fetch change requests for all variations and combine results
                let changeRequests = [];
                const seenIds = new Set();
                
                for (const reference of referenceVariations) {
                    const response = await fetch(`/api/changerequests?reference=${encodeURIComponent(reference)}`);
                    if (response.ok) {
                        const crs = await response.json();
                        // Add unique CRs only
                        for (const cr of crs) {
                            if (!seenIds.has(cr.id)) {
                                seenIds.add(cr.id);
                                changeRequests.push(cr);
                            }
                        }
                    }
                }

                console.log('Loaded change requests:', changeRequests);
                // Debug: Log status information for each CR
                changeRequests.forEach(cr => {
                    console.log(`CR ${cr.id}: statusId=${cr.crStatusId}, statusName="${cr.statusName}", primaryName="${cr.primaryName}"`);
                });

                if (changeRequests.length === 0) {
                    listContainer.innerHTML = `
                        <div class="empty-state">
                            <i class="fas fa-clipboard-list"></i>
                            <p>No change requests found for this ${this.formatFacetName(this.currentFacetType)}</p>
                        </div>
                    `;
                    this.changeRequests = [];
                    return;
                }

                // Store change requests for blocking logic
                this.changeRequests = changeRequests;
                
                // Render change requests as a table
                listContainer.innerHTML = this.renderChangeRequestTable(changeRequests);

            } catch (error) {
                console.error('Error loading change requests:', error);
                listContainer.innerHTML = `
                    <div class="empty-state">
                        <i class="fas fa-exclamation-triangle"></i>
                        <p>Failed to load change requests</p>
                    </div>
                `;
            }
        },

        /**
         * Fetch current user ID from API
         */
        fetchCurrentUserId: async function () {
            if (this.currentUserId !== null) {
                return this.currentUserId; // Already fetched
            }
            
            try {
                const response = await fetch('/api/me', {
                    method: 'GET',
                    credentials: 'include'
                });
                if (response.ok) {
                    const userData = await response.json();
                    this.currentUserId = userData.id || userData.ID || userData.userId;
                    console.log('[ChangeTab] Current user ID:', this.currentUserId);
                } else {
                    console.warn('[ChangeTab] Failed to fetch current user');
                    this.currentUserId = null;
                }
            } catch (error) {
                console.error('[ChangeTab] Error fetching current user:', error);
                this.currentUserId = null;
            }
            
            return this.currentUserId;
        },

        /**
         * Load workflow data for the current facet
         */
        loadWorkflow: async function () {
            const listContainer = document.getElementById('workflowList');
            if (!listContainer) return;

            // Get the module ID from the facet type
            const moduleId = await this.getModuleIdFromFacetType(this.currentFacetType);

            if (!moduleId) {
                listContainer.innerHTML = `
                    <div class="empty-state">
                        <i class="fas fa-exclamation-triangle"></i>
                        <p>Unable to determine module for this ${this.formatFacetName(this.currentFacetType)}</p>
                    </div>
                `;
                return;
            }

            // Initialize WorkflowView component
            if (typeof WorkflowView !== 'undefined') {
                WorkflowView.initialize(moduleId, 'workflowList');
            } else {
                console.error('WorkflowView component not loaded');
                const t = (k) => (window.I18n && window.I18n.t(k)) || k;
                listContainer.innerHTML = `
                    <div class="empty-state">
                        <i class="fas fa-exclamation-triangle"></i>
                        <p>${t('changeTab.workflowViewerNotAvailable')}</p>
                    </div>
                `;
            }
        },

        /**
         * Get module ID from facet type
         */
        getModuleIdFromFacetType: async function (facetType) {
            try {
                const response = await fetch('/api/modules');
                if (!response.ok) {
                    throw new Error('Failed to fetch modules');
                }

                const data = await response.json();
                const modules = data.modules || [];

                // Map facet types to module names
                const facetToModuleMap = {
                    'dataset': 'Data Sets',
                    'system': 'System',
                    'capability': 'Capability',
                    'client': 'Client',
                    'product': 'Product',
                    'system-interface': 'Interface',
                    'policy': 'Policy',
                    'committee': 'Committee',
                    'process': 'Process',
                    'business-area': 'Business Area',
                    'glossary': 'Glossary',
                    'geography': 'Geography',
                    'legal-entity': 'Legal Entity',
                    'org-unit': 'Org Unit',
                    'project': 'Project',
                    'regulation': 'Regulation',
                    'regulator': 'Regulator',
                    'regulatory-theme': 'Regulatory Theme'
                };

                const moduleName = facetToModuleMap[facetType];
                if (!moduleName) {
                    console.warn('No module mapping found for facet type:', facetType);
                    return null;
                }

                const module = modules.find(m => m.primaryName === moduleName);
                return module ? module.id : null;

            } catch (error) {
                console.error('Error fetching module ID:', error);
                return null;
            }
        },


        /**
         * Build reference string from facet type and ID
         * Returns an array of possible references to search for (handles naming variations)
         */
        buildReference: function (facetType, facetId) {
            const facetNames = {
                'dataset': 'Data Set',  // Primary reference format used by DFCR
                'system': 'System',
                'capability': 'Capability',
                'client': 'Client',
                'product': 'Product',
                'system-interface': 'System Interface',
                'policy': 'Policy',
                'committee': 'Committee',
                'process': 'Process',
                'business-area': 'Business Area',
                'glossary': 'Glossary',
                'geography': 'Geography',
                'legal-entity': 'Legal Entity',
                'org-unit': 'Org Unit',
                'project': 'Project',
                'regulation': 'Regulation',
                'regulator': 'Regulator',
                'regulatory-theme': 'Regulatory Theme'
            };

            const facetName = facetNames[facetType] || this.capitalizeFirst(facetType);
            return `${facetName} ${facetId}`;
        },
        
        /**
         * Get all possible reference variations for a facet (handles naming differences)
         */
        getReferenceVariations: function (facetType, facetId) {
            const variations = [];
            
            // Add primary reference
            const primary = this.buildReference(facetType, facetId);
            variations.push(primary);
            
            // Add alternate variations for facets with naming differences
            if (facetType === 'dataset') {
                variations.push(`Dataset ${facetId}`);  // Also search for "Dataset X"
            }
            
            return variations;
        },

        /**
         * Format facet name for display
         */
        formatFacetName: function (facetType) {
            const names = {
                'dataset': 'data set',
                'system': 'system',
                'capability': 'capability',
                'client': 'client',
                'product': 'product',
                'system-interface': 'system interface',
                'policy': 'policy',
                'committee': 'committee',
                'process': 'process',
                'business-area': 'business area'
            };
            return names[facetType] || facetType;
        },

        /**
         * Capitalize first letter
         */
        capitalizeFirst: function (str) {
            if (!str) return '';
            return str.charAt(0).toUpperCase() + str.slice(1).replace(/-/g, ' ');
        },

        /**
         * Render change requests as a table
         */
        renderChangeRequestTable: function (changeRequests) {
            const rows = changeRequests.map(cr => this.renderChangeRequestRow(cr)).join('');

            return `
                <div class="data-table-wrapper">
                    <table class="data-table">
                        <thead>
                            <tr>
                                <th><div class="th-content"><span>Ref</span></div></th>
                                <th><div class="th-content"><span>Title</span></div></th>
                                <th><div class="th-content"><span>Description</span></div></th>
                                <th><div class="th-content"><span>Type</span></div></th>
                                <th><div class="th-content"><span>Status</span></div></th>
                                <th><div class="th-content"><span>User</span></div></th>
                            </tr>
                        </thead>
                        <tbody>
                            ${rows}
                        </tbody>
                    </table>
                    <div class="table-footer">${changeRequests.length} ${(window.I18n && (changeRequests.length !== 1 ? window.I18n.t('changeTab.records') : window.I18n.t('changeTab.record'))) || (changeRequests.length !== 1 ? 'records' : 'record')}</div>
                </div>
            `;
        },

        /**
         * Render a single change request row
         */
        renderChangeRequestRow: function (cr) {
            const typeName = cr.typeName || '';
            
            // Debug logging
            console.log('Rendering CR:', cr.id, 'Status ID:', cr.crStatusId, 'Status Name:', cr.statusName);
            
            // Get status name directly from backend - NO frontend overrides
            // The backend should provide the correct status name from the database
            let statusName = cr.statusName || '';
            
            // Debug: Log what we received
            if (!statusName) {
                console.warn('⚠️ CR', cr.id, 'has no statusName. Status ID:', cr.crStatusId, 'Full CR object:', cr);
            }
            
            // Only use fallback if backend didn't provide any status
            if (!statusName && cr.status) {
                statusName = cr.status;
            }
            
            // NEVER show "Status X" - if no status name, show empty or "No Status"
            if (!statusName) {
                statusName = ''; // Will show "No Status" in the UI
            }
            
            const statusClass = this.getStatusClass(statusName);
            
            // Enhanced blocking logic: ANY CR should be blocked if there are older incomplete CRs
            // This includes both CREATE and EDIT CRs - only the oldest CR for an object should be unblocked
            const isBlocked = this.isChangeRequestBlocked(cr);
            
            const rowClass = isBlocked ? 'blocked-cr' : '';
            const linkClass = isBlocked ? 'blocked-link' : '';
            
            // Get list of older incomplete CRs for tooltip
            const olderCRs = this.getOlderIncompleteCRs(cr);
            const tooltipText = isBlocked && olderCRs.length > 0 
                ? `This change request is blocked. Complete or cancel older change request(s): ${olderCRs.map(c => c.id).join(', ')}`
                : '';
            
            // Add blocking indicator for blocked CRs
            const blockingIndicator = isBlocked ? 
                '<i class="fas fa-lock" title="' + tooltipText + '" style="color: #dc3545; margin-left: 5px;"></i>' : '';
            
            // For blocked CRs, make the link non-functional
            const linkHtml = isBlocked 
                ? `<span class="${linkClass}" style="cursor: not-allowed;">${this.escapeHtml(cr.primaryName)}</span>`
                : `<a href="/view/change-request/change-request-view.html?id=${cr.id}">${this.escapeHtml(cr.primaryName)}</a>`;
            
            // Get user name (created by)
            const userName = cr.createdByName || '';
            
            return `
                <tr class="${rowClass}" ${tooltipText ? `title="${tooltipText}"` : ''}>
                    <td>${cr.id}${blockingIndicator}</td>
                    <td>
                        <div class="title-cell">
                            <i class="fas fa-comment"></i>
                            ${linkHtml}
                            ${isBlocked ? '<span class="blocked-badge">BLOCKED</span>' : ''}
                        </div>
                    </td>
                    <td>${this.escapeHtml(cr.summary || '')}</td>
                    <td><span class="type-badge">${this.escapeHtml(typeName)}</span></td>
                    <td>${statusName ? `<span class="status-badge ${statusClass}">${this.escapeHtml(statusName)}</span>` : '<span class="status-badge">No Status</span>'}</td>
                    <td>${this.escapeHtml(userName)}</td>
                </tr>
            `;
        },

        /**
         * Check if a change request is blocked by older incomplete CRs
         * For Manually Raised CRs: Only the first (oldest) CR should be blocked if there are older incomplete CRs
         * For Auto-created CRs: Use backend blocking flag
         */
        isChangeRequestBlocked: function(currentCR) {
            // Check if this is a manually raised CR (not auto-created)
            const isManuallyRaised = !currentCR.mandatoryWorkflow && !currentCR.processDefinitionId;
            
            // For auto-created CRs, use backend blocking flag
            if (!isManuallyRaised) {
                if (currentCR.isBlocked === true) {
                    return true;
                }
                // Check for older incomplete CRs for auto-created CRs
                const olderCRs = this.getOlderIncompleteCRs(currentCR);
                return olderCRs.length > 0;
            }
            
            // For manually raised CRs: Only block the first (oldest) CR
            // Get all manually raised CRs for the same object, sorted by creation date
            const manuallyRaisedCRs = this.getManuallyRaisedCRs(currentCR);
            
            if (manuallyRaisedCRs.length === 0) {
                return false; // No manually raised CRs found
            }
            
            // Sort by creation date (oldest first)
            manuallyRaisedCRs.sort((a, b) => {
                const dateA = new Date(a.createdAt);
                const dateB = new Date(b.createdAt);
                return dateA - dateB;
            });
            
            // Check if current CR is the first (oldest) manually raised CR
            const isFirstManuallyRaised = manuallyRaisedCRs[0].id === currentCR.id;
            
            // Only block the first manually raised CR if there are older incomplete CRs
            if (isFirstManuallyRaised) {
                const olderCRs = this.getOlderIncompleteCRs(currentCR);
                return olderCRs.length > 0;
            }
            
            // For 2nd to last manually raised CRs, never block
            return false;
        },
        
        /**
         * Get all manually raised CRs for the same object created by the same user
         * Returns array of manually raised CR objects (excluding auto-created CRs)
         */
        getManuallyRaisedCRs: function(currentCR) {
            if (!this.changeRequests || !currentCR.reference) return [];
            
            // Only consider CRs created by the same user
            if (!currentCR.createdBy || currentCR.createdBy !== this.currentUserId) {
                return [];
            }
            
            return this.changeRequests.filter(cr => {
                // Same reference (same object)
                if (cr.reference !== currentCR.reference) return false;
                
                // Only consider CRs created by the same user
                if (!cr.createdBy || cr.createdBy !== this.currentUserId) return false;
                
                // Only manually raised CRs (not auto-created)
                const isManuallyRaised = !cr.mandatoryWorkflow && !cr.processDefinitionId;
                return isManuallyRaised;
            });
        },

        /**
         * Get list of older incomplete CRs for the same object created by the same user
         * Returns array of CR objects that are blocking the current CR
         */
        getOlderIncompleteCRs: function(currentCR) {
            if (!this.changeRequests || !currentCR.reference) return [];
            
            // Only block if current user created this CR
            if (!currentCR.createdBy || currentCR.createdBy !== this.currentUserId) {
                return []; // Don't block CRs created by different users
            }
            
            const currentCreatedAt = new Date(currentCR.createdAt);
            
            return this.changeRequests.filter(cr => {
                // Same reference (same object)
                if (cr.reference !== currentCR.reference) return false;
                
                // Different CR (not the same one)
                if (cr.id === currentCR.id) return false;
                
                // Only consider CRs created by the same user
                if (!cr.createdBy || cr.createdBy !== this.currentUserId) return false;
                
                // Older CR (created before current)
                const crCreatedAt = new Date(cr.createdAt);
                if (crCreatedAt >= currentCreatedAt) return false;
                
                // Incomplete status (not Completed or Cancelled)
                const status = (cr.statusName || '').toLowerCase();
                const isComplete = status.includes('completed') || status.includes('cancelled') || status.includes('canceled');
                
                return !isComplete;
            });
        },

        /**
         * Legacy helper function - kept for compatibility
         */
        hasOlderIncompleteCRs: function(currentCR) {
            return this.getOlderIncompleteCRs(currentCR).length > 0;
        },

        /**
         * Get CSS class for status badge
         */
        getStatusClass: function (status) {
            if (!status) return '';

            const normalizedStatus = status.toLowerCase().replace(/\s+/g, '-');

            const statusClasses = {
                'pending-start': 'pending-start',
                'pending': 'pending-start',
                'running': 'running',
                'in-progress': 'running',
                'cancelled': 'cancelled',
                'canceled': 'cancelled',
                'completed': 'completed',
                'complete': 'completed',
                'approved': 'approved',
                'rejected': 'rejected'
            };

            return statusClasses[normalizedStatus] || '';
        },

        /**
         * Escape HTML to prevent XSS
         */
        escapeHtml: function (str) {
            if (str == null) return '';
            return String(str)
                .replace(/&/g, '&amp;')
                .replace(/</g, '&lt;')
                .replace(/>/g, '&gt;')
                .replace(/"/g, '&quot;')
                .replace(/'/g, '&#039;');
        }
    };

    // Export to global scope
    window.ChangeTabComponent = ChangeTabComponent;

    console.log('ChangeTabComponent loaded successfully');
})();
