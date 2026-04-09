(function() {
    'use strict';

    let currentChangeRequestId = null;
    let currentChangeRequest = null;
    let taskPollingInterval = null; // For polling active tasks
    let workflowCompleted = false; // Track if workflow has reached End event
    let currentUser = null; // Store current user info
    let isUserStakeholder = false; // Track if current user is a stakeholder
    let statuses = []; // Store statuses for ID to name mapping
    let lifecycles = []; // Store lifecycles for ID to name mapping
    let lifecycleModuleId = null; // Track which module's lifecycles are loaded
    let isLoadingChangeRequestData = false; // Flag to prevent refresh loop

    // Initialize the page
    function init() {
        currentChangeRequestId = getChangeRequestIdFromUrl();
        
        if (!currentChangeRequestId) {
            console.error('No change request ID found in URL');
            console.log('Current URL:', window.location.href);
            console.log('URL search params:', window.location.search);
            showError('Change request ID not provided in URL. Please provide ?id=X parameter.');
            return;
        }

        setupEventHandlers();
        setupTabs();
        
        // Initialize container visibility classes
        const summaryContainer = document.getElementById('changeRequestViewContainer');
        const relationshipsContainer = document.getElementById('changeRequestRelationshipsContainer');
        const stakeholdersContainer = document.getElementById('changeRequestStakeholdersContainer');
        const historyContainer = document.getElementById('changeRequestHistoryContainer');
        
        // Set initial visibility - summary is visible, others are hidden
        if (summaryContainer) {
            summaryContainer.classList.add('tab-visible');
            summaryContainer.classList.remove('tab-hidden');
            summaryContainer.style.display = '';
            summaryContainer.style.visibility = '';
        }
        [relationshipsContainer, stakeholdersContainer, historyContainer].forEach(container => {
            if (container) {
                container.classList.add('tab-hidden');
                container.classList.remove('tab-visible');
                // Clear any inline styles that might interfere
                container.style.display = '';
                container.style.visibility = '';
                container.style.height = '';
                container.style.width = '';
            }
        });
        
        // First test if we can get all change requests
        testApiConnection();
        loadChangeRequestData();
    }

    // Get change request ID from URL
    function getChangeRequestIdFromUrl() {
        const urlParams = new URLSearchParams(window.location.search);
        const id = urlParams.get('id');
        console.log('Change request ID from URL:', id);
        return id;
    }

    // Setup event handlers
    function setupEventHandlers() {
        // Back button
        const backBtn = document.getElementById('backBtn');
        if (backBtn) {
            backBtn.addEventListener('click', () => {
                window.history.back();
            });
        }
    }

    // Setup tab functionality
    function setupTabs() {
        const tabs = document.querySelectorAll('.tab');
        tabs.forEach(tab => {
            tab.addEventListener('click', () => {
                const tabName = tab.getAttribute('data-tab');
                switchTab(tabName);
            });
        });
    }

    // Switch between tabs
    function switchTab(tabName) {
        // Update active tab
        document.querySelectorAll('.tab').forEach(tab => {
            tab.classList.remove('active');
        });
        document.querySelector(`[data-tab="${tabName}"]`).classList.add('active');

        // Get all containers
        const summaryContainer = document.getElementById('changeRequestViewContainer');
        const relationshipsContainer = document.getElementById('changeRequestRelationshipsContainer');
        const stakeholdersContainer = document.getElementById('changeRequestStakeholdersContainer');
        const historyContainer = document.getElementById('changeRequestHistoryContainer');

        // Hide all containers using class-based approach to preserve layout
        [summaryContainer, relationshipsContainer, stakeholdersContainer, historyContainer].forEach(container => {
            if (container) {
                container.classList.add('tab-hidden');
                container.classList.remove('tab-visible');
                // Clear any inline styles that might interfere
                container.style.display = '';
                container.style.visibility = '';
                container.style.height = '';
                container.style.width = '';
            }
        });

        // Show selected container
        switch(tabName) {
            case 'summary':
                if (summaryContainer) {
                    summaryContainer.classList.remove('tab-hidden');
                    summaryContainer.classList.add('tab-visible');
                    // Ensure grid layout is explicitly set and maintained
                    summaryContainer.className = 'view-grid tab-visible';
                    // Force browser to recalculate layout
                    void summaryContainer.offsetHeight;
                }
                break;
            case 'relationships':
                if (relationshipsContainer) {
                    relationshipsContainer.classList.remove('tab-hidden');
                    relationshipsContainer.classList.add('tab-visible');
                    // Clear any conflicting inline styles - CSS will handle display
                    relationshipsContainer.style.display = '';
                    relationshipsContainer.style.visibility = '';
                    relationshipsContainer.style.height = '';
                    loadRelationships();
                }
                break;
            case 'stakeholders':
                if (stakeholdersContainer) {
                    stakeholdersContainer.classList.remove('tab-hidden');
                    stakeholdersContainer.classList.add('tab-visible');
                    loadStakeholders();
                }
                break;
            case 'history':
                if (historyContainer) {
                    historyContainer.classList.remove('tab-hidden');
                    historyContainer.classList.add('tab-visible');
                    loadHistory();
                }
                break;
        }
    }

    // Load change request data
    async function loadChangeRequestData() {
        // Prevent refresh loop
        if (isLoadingChangeRequestData) {
            console.log('loadChangeRequestData already in progress, skipping...');
            return;
        }
        
        isLoadingChangeRequestData = true;
        try {
            console.log('Fetching change request with ID:', currentChangeRequestId);
            const response = await fetch(`/api/changerequests/${currentChangeRequestId}`);
            
            if (!response.ok) {
                const errorText = await response.text();
                console.error('Server error response:', errorText);
                throw new Error(`Server error (${response.status}): ${errorText}`);
            }

            const changeRequest = await response.json();
            currentChangeRequest = changeRequest; // Store globally for use in other functions
            // Store in window for getEntityInfo to access
            window.currentChangeRequest = changeRequest;
            console.log('Loaded change request data:', changeRequest);
            console.log('Created By:', changeRequest.createdBy, 'Name:', changeRequest.createdByName);
            console.log('Last User Change:', changeRequest.lastUserChange, 'Name:', changeRequest.lastUserChangeName);
            console.log('Process Definition ID:', changeRequest.processDefinitionId, '(type:', typeof changeRequest.processDefinitionId, ')');
            console.log('Mandatory Workflow:', changeRequest.mandatoryWorkflow, '(type:', typeof changeRequest.mandatoryWorkflow, ')');
            
            // Load current user info if not already loaded
            if (!currentUser) {
                try {
                    const userResponse = await fetch('/api/me', {
                        method: 'GET',
                        credentials: 'include'
                    });
                    if (userResponse.ok) {
                        currentUser = await userResponse.json();
                        // Check if user is stakeholder
                        isUserStakeholder = await checkUserIsStakeholder(changeRequest.id);
                    }
                } catch (error) {
                    console.error('Error loading current user:', error);
                }
            }
            
            // Load statuses and lifecycles for Element Properties display
            await loadStatuses();
            if (changeRequest.reference) {
                // Parse reference to get facet name for lifecycle loading
                // Handle multi-word facet names like "Business Area", "Data Set", etc.
                const refParts = changeRequest.reference.split(/\s+/);
                if (refParts.length > 0) {
                    // Try to match common multi-word facet names first
                    let facetName = refParts[0];
                    if (refParts.length >= 2) {
                        const twoWords = `${refParts[0]} ${refParts[1]}`;
                        // Check for common multi-word facets
                        if (twoWords === 'Business Area' || twoWords === 'Data Set' || 
                            twoWords === 'System Interface' || twoWords === 'Legal Entity' ||
                            twoWords === 'Regulatory Theme' || twoWords === 'Org Unit') {
                            facetName = twoWords;
                        }
                    }
                    await loadLifecycles(facetName);
                }
            }
            
            await displayChangeRequestData(changeRequest);
            await setupEditDropdown();
            
            // Load task history if workflow exists (even if completed) - DISCUSSION should always be visible
            if (changeRequest.processInstanceId) {
                try {
                    const workflowResponse = await fetch(`/api/workflow_instances/by-cr/${changeRequest.id}`, {
                        method: 'GET',
                        credentials: 'include'
                    }).catch(err => {
                        // Silently handle network errors - don't pollute console
                        return { ok: false, status: 0 };
                    });
                    // 404 is expected when workflow hasn't started yet - handle gracefully
                    if (workflowResponse.status === 404) {
                        // No workflow instance yet - this is normal, don't log as error
                        // Check for active task even if cancelled
                        if (!workflowCompleted) {
                            checkActiveTask(); // Check for banner
                        }
                    } else if (workflowResponse.ok) {
                        const workflowData = await workflowResponse.json();
                        const instance = workflowData.instance;
                        if (instance && instance.id) {
                            // Check if workflow definition has been updated since instance started
                            await checkAndShowWorkflowUpdateWarning(changeRequest.id, workflowData);
                            
                            // Check if workflow is completed
                            if (instance.status === 'Disabled' || instance.status === 'Completed' || instance.endedAt) {
                                workflowCompleted = true;
                                // Load task history for completed workflow (DISCUSSION as history)
                                await loadTaskHistory(instance.id);
                            } else {
                                // Workflow is still running - check for active task
                                checkActiveTask(); // Check for banner
                            }
                        }
                    } else {
                        // Other non-404 errors - log at debug level only
                        console.debug('Error fetching workflow instance (status:', workflowResponse.status, ')');
                        if (!workflowCompleted) {
                            checkActiveTask();
                        }
                    }
                } catch (error) {
                    console.warn('Could not load workflow instance for task history:', error);
                    // Still try to check for active task if workflow might be running
                    if (!workflowCompleted) {
                        checkActiveTask();
                    }
                }
            } else {
                // No workflow instance yet - check for active task even if cancelled
                if (!workflowCompleted) {
                    checkActiveTask(); // Check for banner
                }
            }

        } catch (error) {
            console.error('Error loading change request:', error);
            showError('Failed to load change request data: ' + error.message);
        } finally {
            isLoadingChangeRequestData = false;
        }
    }

    // Display change request data
    async function displayChangeRequestData(changeRequest) {
        // Update page title
        const titleElement = document.getElementById('changeRequestTitle');
        const breadcrumbElement = document.getElementById('changeRequestBreadcrumb');
        
        if (titleElement) {
            titleElement.textContent = changeRequest.primaryName || changeRequest.PrimaryName || 'Change Request';
        }
        if (breadcrumbElement) {
            breadcrumbElement.textContent = 'Change Request';
        }

        // Create view sections
        const container = document.getElementById('changeRequestViewContainer');
        
        // ALWAYS clear and recreate content to ensure fresh data
        // This is especially important after completing a CR when reference field is updated
        if (container) {
            container.innerHTML = '';
            container.className = 'view-grid tab-visible';
        }

            // Left column - Definition and Documents
            const leftColumn = document.createElement('div');
            leftColumn.className = 'left-column';

        // Definition section
        const definitionSection = await createDefinitionSection(changeRequest);
        leftColumn.appendChild(definitionSection);
        
        // Changes to Review section (only for auto-generated CRs with pending changes, in-progress status)
        // Will be populated asynchronously
        const changesToReviewSection = document.createElement('div');
        changesToReviewSection.id = 'changesToReviewSection';
        changesToReviewSection.className = 'view-section';
        changesToReviewSection.style.display = 'none'; // Hidden by default
        leftColumn.appendChild(changesToReviewSection);

        // Load changes to review if applicable
        loadChangesToReview(changeRequest, changesToReviewSection);

        // Documents section
        const documentsSection = createDocumentsSection(changeRequest);
        leftColumn.appendChild(documentsSection);



            // Right column - Classifications and Other Info
            const rightColumn = document.createElement('div');
            rightColumn.className = 'right-column';

        // Classifications section (async - needs to await)
        const classificationsSection = await createClassificationsSection(changeRequest);
        rightColumn.appendChild(classificationsSection);

            // Other Information section
            const otherInfoSection = createOtherInfoSection(changeRequest);
            rightColumn.appendChild(otherInfoSection);

            container.appendChild(leftColumn);
            container.appendChild(rightColumn);

        const crId = changeRequest.id != null ? changeRequest.id : changeRequest.ID;
        if (crId != null && window.CustomFields && typeof window.CustomFields.initForm === 'function') {
            const customFieldsSection = document.createElement('div');
            customFieldsSection.className = 'view-section';
            customFieldsSection.style.marginTop = '1.25rem';
            customFieldsSection.style.gridColumn = '1 / -1';
            const titleEl = document.createElement('div');
            titleEl.className = 'section-title';
            titleEl.textContent = (typeof window !== 'undefined' && window.I18n && typeof window.I18n.t === 'function')
                ? window.I18n.t('createPage.section.customFields')
                : 'CUSTOM FIELDS';
            const fieldsWrap = document.createElement('div');
            fieldsWrap.className = 'form-fields-container';
            const cfHost = document.createElement('div');
            cfHost.id = 'customFieldsViewContainer';
            fieldsWrap.appendChild(cfHost);
            customFieldsSection.appendChild(titleEl);
            customFieldsSection.appendChild(fieldsWrap);
            container.appendChild(customFieldsSection);
            try {
                await window.CustomFields.initForm({
                    facetId: 'Change Requests',
                    containerId: 'customFieldsViewContainer',
                    mode: 'view',
                    objectId: Number(crId)
                });
            } catch (cfErr) {
                console.error('Change request custom fields (view):', cfErr);
            }
        }

        // Workflow section (Full Width) - only hide if cancelled (show for completed)
        const statusName = (changeRequest.statusName || changeRequest.StatusName || '').toLowerCase();
        const isCancelled = statusName && (statusName.includes('cancelled') || statusName.includes('canceled'));
        
        if (!isCancelled) {
            const workflowSection = createWorkflowSection(changeRequest);
            workflowSection.style.gridColumn = '1 / -1';
            workflowSection.style.marginTop = '1.25rem';
            container.appendChild(workflowSection);
        }
    }

    // Create definition section
    async function createDefinitionSection(changeRequest) {
        const section = document.createElement('div');
        section.className = 'view-section';
        
        // Parse reference to create hyperlink (async to fetch object name)
        const affectedItemHtml = await createAffectedItemLink(changeRequest.reference);
        
        section.innerHTML = `
            <div class="section-title">DEFINITION</div>
            <div class="view-item">
                <div class="view-label">Title:</div>
                <div class="view-value">${changeRequest.primaryName || changeRequest.PrimaryName || ((typeof window !== 'undefined' && window.I18n && window.I18n.t) ? window.I18n.t('message.notSpecified') : 'Not specified')}</div>
            </div>
            <div class="view-item">
                <div class="view-label">Type:</div>
                <div class="view-value">${changeRequest.typeName || changeRequest.TypeName || ((typeof window !== 'undefined' && window.I18n && window.I18n.t) ? window.I18n.t('message.notSpecified') : 'Not specified')}</div>
            </div>
            <div class="view-item">
                <div class="view-label">Affected Item:</div>
                <div class="view-value">${affectedItemHtml}</div>
            </div>
            <div class="view-item">
                <div class="view-label">Analysis:</div>
                <div class="view-value" id="analysisDisplay">${escapeHtml(changeRequest.analysis || 'Not specified')}</div>
                        </div>
            <div class="view-item">
                <div class="view-label">Resolution Status:</div>
                <div class="view-value" id="resolutionStatusDisplay">${escapeHtml(changeRequest.resolutionStatusName || 'Not specified')}</div>
            </div>
            <div class="view-item">
                <div class="view-label">Resolution:</div>
                <div class="view-value" id="resolutionDisplay">${escapeHtml(changeRequest.resolution || 'Not specified')}</div>
            </div>
        `;
        
        return section;
    }

    // Create hyperlink for affected item with object name
    async function createAffectedItemLink(reference) {
        if (!reference) {
            return (typeof window !== 'undefined' && window.I18n && window.I18n.t) ? window.I18n.t('message.notSpecified') : 'Not specified';
        }
        
        // Parse reference format: "FacetType FacetId" (e.g., "Glossary 20", "System 5", "Data Set 47")
        // Handle multi-word facet types by finding the last number
        const match = reference.match(/^(.+?)\s+(\d+)$/);
        if (match) {
            const facetName = match[1].trim();
            const facetId = parseInt(match[2]);
            
            // Normalize facet name to lowercase and handle special cases
            const facetType = facetName.toLowerCase()
                .replace(/\s+/g, '-')  // Replace spaces with hyphens
                .replace(/^data-set$/, 'dataset')  // Special case: "data set" -> "dataset"
                .replace(/^business-area$/, 'business-area')  // Keep as is
                .replace(/^legal-entity$/, 'legal-entity')  // Keep as is
                .replace(/^org-unit$/, 'org-unit')  // Keep as is
                .replace(/^regulatory-theme$/, 'regulatory-theme')  // Keep as is
                .replace(/^system-interface$/, 'system-interface');  // Keep as is
            
            // Map facet types to their view paths (using path format: /view/{facet}/{id})
            const facetPaths = {
                'glossary': '/view/glossary',
                'system': '/view/system',
                'dataset': '/view/dataset',
                'data-set': '/view/dataset',
                'business-area': '/view/business-area',
                'capability': '/view/capability',
                'client': '/view/client',
                'committee': '/view/committee',
                'geography': '/view/geography',
                'legal-entity': '/view/LegalEntity',
                'org-unit': '/view/org-unit',
                'people': '/view/people',
                'policy': '/view/policy',
                'process': '/view/process',
                'product': '/view/product',
                'project': '/view/project',
                'regulation': '/view/regulation',
                'regulator': '/view/regulator',
                'regulatory-theme': '/view/regulatory-theme',
                'system-interface': '/view/system-interface'
            };
            
            const viewPath = facetPaths[facetType];
            
            // Try to fetch the object name
            let objectName = reference; // Default to reference if fetch fails
            try {
                if (window.BUDG_API_SERVICE) {
                    // Map facet types to API methods (only include methods that exist)
                    const apiMethodMap = {
                        'glossary': 'getGlossaryById',
                        'system': 'getSystemById',
                        'dataset': 'getDatasetById',
                        'data-set': 'getDatasetById',
                        'business-area': 'getBusinessAreaById',
                        'capability': 'getCapabilityById',
                        'client': 'getClientById',
                        'committee': 'getCommitteeById',
                        'legal-entity': 'getLegalEntityById',
                        'org-unit': 'getOrgUnitById',
                        'people': 'getPersonById',
                        'policy': 'getPolicyById',
                        'process': 'getProcessById',
                        'product': 'getProductById',
                        'project': 'getProjectById',
                        'system-interface': 'getInterfaceById'
                        // Note: geography, regulation, regulator, regulatory-theme don't have getById methods yet
                    };
                    
                    const apiMethod = apiMethodMap[facetType];
                    if (apiMethod && typeof window.BUDG_API_SERVICE[apiMethod] === 'function') {
                        try {
                            const objectData = await window.BUDG_API_SERVICE[apiMethod](facetId);
                            if (objectData) {
                                // Handle data wrapped in a 'data' property
                                const data = objectData.data || objectData;
                                
                                // Try different name fields based on object type
                                objectName = data.name || 
                                            data.primaryName || 
                                            data.PrimaryName ||
                                            data.primaryname ||
                                            data.shortName ||
                                            data.ShortName ||
                                            data.shortname ||
                                            data.longName ||
                                            data.LongName ||
                                            data.longname ||
                                            (data.firstName && data.lastName ? `${data.firstName} ${data.lastName}` : null) ||
                                            (data.First_Name && data.Last_Name ? `${data.First_Name} ${data.Last_Name}` : null) ||
                                            reference;
                            }
                        } catch (fetchError) {
                            console.warn('Error fetching object data for', facetType, facetId, ':', fetchError);
                            // Keep reference as fallback
                        }
                    }
                }
            } catch (error) {
                console.warn('Failed to fetch object name for reference:', reference, error);
                // Fall back to reference if fetch fails
            }
            
            if (viewPath) {
                // Use path format: /view/{facet}/{id} instead of query parameter
                return `<i class="fas fa-link"></i> <a href="${viewPath}/${facetId}" style="color: #248567; text-decoration: none;">${escapeHtml(objectName)}</a>`;
            }
        }
        
        return `<i class="fas fa-link"></i> ${escapeHtml(reference)}`;
    }

    // Create documents section
    function createDocumentsSection(changeRequest) {
        const section = document.createElement('div');
        section.className = 'view-section';
        section.setAttribute('data-collapsible', '');
        const documentsTitle = (typeof window !== 'undefined' && window.I18n && window.I18n.t) ? window.I18n.t('card.documents') : 'DOCUMENTS';
        section.innerHTML = `
            <div class="collapsible-header" style="display: flex; align-items: center; justify-content: space-between; padding: 1rem 1.5rem; background: var(--background-secondary, #f8f9fa); border: 1px solid var(--border-color, #e5e7eb); border-radius: 8px; cursor: pointer;">
                <div style="display: flex; align-items: center; gap: 0.75rem;">
                    <i class="fa-solid fa-file-lines" style="color: var(--secondary-color, #248567);"></i>
                    <h3 style="margin: 0; font-size: 0.875rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px;">${documentsTitle}</h3>
                </div>
                <i class="fas fa-chevron-down" style="transition: transform 0.2s;"></i>
            </div>
            <div class="collapsible-body" style="display: none; padding: 1.5rem; border: 1px solid var(--border-color, #e5e7eb); border-top: none; border-radius: 0 0 8px 8px;">
                <div id="changeRequestDocumentsTableContainer"></div>
            </div>
        `;

        // Wire up collapsible toggle
        const header = section.querySelector('.collapsible-header');
        const body = section.querySelector('.collapsible-body');
        if (header && body) {
            header.addEventListener('click', function() {
                const isOpen = section.classList.contains('open');
                section.classList.toggle('open');
                header.setAttribute('aria-expanded', String(!isOpen));
                body.style.display = isOpen ? 'none' : 'block';
                const chevron = header.querySelector('.fa-chevron-down');
                if (chevron) {
                    chevron.style.transform = isOpen ? 'rotate(0deg)' : 'rotate(180deg)';
                }
            });
        }

        // Load documents after section is added to DOM
        requestAnimationFrame(() => {
            setTimeout(() => {
                loadDocuments(changeRequest.id);
            }, 100);
        });

        return section;
    }

    async function loadDocuments(changeRequestId) {
        const container = document.getElementById('changeRequestDocumentsTableContainer');
        if (!container) {
            console.warn('Documents container not found');
            return;
        }

        // Initialize document table component (read-only for view page)
        if (typeof DocumentTableComponent !== 'undefined') {
            try {
                new DocumentTableComponent({
                    facetType: 'changerequest',
                    facetId: changeRequestId,
                    container: '#changeRequestDocumentsTableContainer',
                    canEdit: false // Read-only in view mode
                });
            } catch (error) {
                console.error('Error initializing document table:', error);
                const failedToLoadDocs = (typeof window !== 'undefined' && window.I18n && window.I18n.t) ? window.I18n.t('message.failedToLoadDocuments') : 'Failed to load documents.';
                container.innerHTML = '<div class="empty" style="color:var(--danger,#b91c1c);">' + failedToLoadDocs + '</div>';
            }
        } else {
            console.error('DocumentTableComponent not found');
            const docComponentNotAvailable = (typeof window !== 'undefined' && window.I18n && window.I18n.t) ? window.I18n.t('message.documentComponentNotAvailable') : 'Document component not available.';
            container.innerHTML = '<div class="empty">' + docComponentNotAvailable + '</div>';
        }
    }

    // Create workflow section
    function createWorkflowSection(changeRequest) {
        const section = document.createElement('div');
        section.className = 'view-section';
        section.id = 'workflowSection';
        section.innerHTML = `
            <div class="section-title">WORKFLOW</div>
            <div class="view-item">
                <div class="view-label">Workflow <span style="color: #dc2626;">*</span>:</div>
                <div class="view-value">
                    <select id="workflowSelect" class="form-select" style="width: 100%;">
                        <option value="">Please select</option>
                    </select>
                </div>
            </div>
            
            <!-- Validation Error Message -->
            <div id="workflowValidationError" style="display: none; margin-top: 1rem; padding: 0.75rem; background: #fee2e2; border-left: 3px solid #dc2626; border-radius: 4px;">
                <i class="fas fa-exclamation-triangle" style="color: #dc2626;"></i>
                <span id="validationErrorText" style="color: #991b1b; margin-left: 0.5rem;"></span>
            </div>
            
            <!-- Workflow Actions -->
            <div id="workflowActions" style="display: none; margin-top: 1rem;">
                <button id="startWorkflowBtn" class="btn btn-primary">
                    <i class="fas fa-play"></i> Start Workflow
                </button>
            </div>
            
            <!-- Workflow Status -->
            <div id="workflowStartedStatus" style="display: none; margin-top: 1rem; padding: 0.75rem; background: #d1fae5; border-left: 3px solid #10b981; border-radius: 4px;">
                <i class="fas fa-check-circle" style="color: #059669;"></i>
                <span id="workflowStatusText" style="color: #065f46; margin-left: 0.5rem; font-weight: 500;">Workflow started successfully</span>
            </div>
            
            <div class="section-title" style="margin-top: 1.5rem;">WORKFLOW DIAGRAM</div>
            <div class="bpmn-viewer-container" id="workflowDiagramContainer" style="min-height: 400px;">
                <div id="workflowDiagramCanvas" class="bpmn-canvas"></div>
                <!-- Properties Panel -->
                <div id="bpmn-properties-panel" class="bpmn-properties-tabs-links" style="display: none;">
                    <div class="properties-header properties-panel-drag-handle">
                        <h4>Element Properties</h4>
                        <div style="display: flex; align-items: center; gap: 0.5rem;">
                            <i class="fas fa-grip-vertical" style="color: #999; cursor: move;"></i>
                            <button id="closePropertiesBtn" class="close-btn" title="Close">
                                <i class="fas fa-times"></i>
                            </button>
                        </div>
                    </div>
                    <div id="propertiesContent" class="properties-content"></div>
                </div>
                <div id="workflowDiagramPlaceholder" style="display: flex; align-items: center; justify-content: center; height: 400px; color: #9ca3af;">
                    <div style="text-align: center;">
                        <i class="fas fa-project-diagram" style="font-size: 3rem; margin-bottom: 1rem; opacity: 0.3;"></i>
                        <p>Select a workflow to view diagram</p>
                    </div>
                </div>
            </div>
        `;

        // Check permissions and hide workflow UI for unauthorized users
        setTimeout(async () => {
            // Check if CR is completed - hide start workflow button only (keep diagram visible)
            const statusName = (changeRequest.statusName || changeRequest.StatusName || '').toLowerCase();
            const isCompleted = statusName && statusName.includes('completed');
            
            const canManage = await canManageWorkflows(changeRequest.id);
            if (!canManage) {
                // Hide workflow select dropdown
                const workflowSelectItem = section.querySelector('.view-item');
                if (workflowSelectItem) {
                    workflowSelectItem.style.display = 'none';
                }
                // Hide workflow actions (start button)
                const workflowActions = section.querySelector('#workflowActions');
                if (workflowActions) {
                    workflowActions.style.display = 'none';
                }
                // Hide validation error (not needed if they can't manage)
                const validationError = section.querySelector('#workflowValidationError');
                if (validationError) {
                    validationError.style.display = 'none';
                }
            } else {
                // Initialize workflow manager if user has permissions
                initializeWorkflowManager(changeRequest);
                
                // If CR is completed, hide start workflow button but keep diagram visible
                if (isCompleted) {
                    const workflowActions = section.querySelector('#workflowActions');
                    if (workflowActions) {
                        workflowActions.style.display = 'none';
                    }
                    // Also hide workflow select dropdown for completed CRs
                    const workflowSelectItem = section.querySelector('.view-item');
                    if (workflowSelectItem) {
                        workflowSelectItem.style.display = 'none';
                    }
                }
            }
        }, 0);

        // Setup properties panel close button
        setTimeout(() => {
            const closeBtn = document.getElementById('closePropertiesBtn');
            if (closeBtn) {
                closeBtn.addEventListener('click', () => {
                    hidePropertiesPanel();
                });
            }
            // Initialize drag functionality for properties panel
            initializePropertiesPanelDrag();
        }, 100);

        // Add workflow diagram styles if not already added
        addWorkflowDiagramStyles();

        return section;
    }

    /**
     * Add styles for workflow diagram (similar to workflow-view.js)
     */
    function addWorkflowDiagramStyles() {
        if (document.getElementById('workflow-diagram-styles')) {
            return; // Styles already added
        }

        const styles = document.createElement('style');
        styles.id = 'workflow-diagram-styles';
        styles.textContent = `
            .bpmn-viewer-container {
                position: relative;
                width: 100%;
                height: 600px;
                border: 1px solid var(--border-color, #e9ecef);
                border-radius: 4px;
                overflow: hidden;
                background: #f9f9f9;
            }

            .bpmn-canvas {
                width: 100%;
                height: 100%;
                cursor: grab;
            }

            .bpmn-canvas:active {
                cursor: grabbing;
            }

            /* Enable pan/drag for BPMN diagram */
            .bpmn-canvas .djs-container {
                cursor: grab;
            }

            .bpmn-canvas .djs-container:active {
                cursor: grabbing;
            }

            /* Enable pan/drag on SVG element */
            .bpmn-canvas svg {
                cursor: grab;
                user-select: none;
            }

            .bpmn-canvas svg:active {
                cursor: grabbing;
            }

            /* Make canvas focusable for pan/drag */
            .bpmn-canvas {
                outline: none;
            }

            .bpmn-canvas:focus {
                outline: none;
            }

            .bpmn-properties-tabs-links {
                position: absolute;
                right: 20px;
                top: 20px;
                width: 300px;
                max-height: 500px;
                background: var(--background-primary, #fff);
                border: 1px solid var(--border-color, #ddd);
                border-radius: 4px;
                box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
                overflow: hidden;
                z-index: 100;
            }

            .bpmn-properties-tabs-links .properties-header {
                display: flex;
                justify-content: space-between;
                align-items: center;
                padding: 0.75rem 1rem;
                background: var(--background-secondary, #f8f9fa);
                border-bottom: 1px solid var(--border-color, #e9ecef);
            }

            .bpmn-properties-tabs-links .properties-header.properties-panel-drag-handle {
                cursor: move;
                user-select: none;
            }

            .bpmn-properties-tabs-links.dragging {
                box-shadow: 0 8px 24px rgba(0, 0, 0, 0.25);
                opacity: 0.95;
            }

            .bpmn-properties-tabs-links .properties-header h4 {
                margin: 0;
                font-size: 0.875rem;
                font-weight: 600;
                color: var(--text-primary, #2c3e50);
            }

            .bpmn-properties-tabs-links .close-btn {
                background: none;
                border: none;
                padding: 0.25rem;
                cursor: pointer;
                color: var(--text-secondary, #6c757d);
                font-size: 1rem;
                line-height: 1;
                transition: color 0.2s ease;
                position: relative;
                z-index: 10;
            }

            .bpmn-properties-tabs-links .close-btn:hover {
                color: var(--text-primary, #2c3e50);
            }

            .bpmn-properties-tabs-links .properties-header .fa-grip-vertical {
                cursor: move;
            }

            .bpmn-properties-tabs-links .properties-content {
                padding: 1rem;
                max-height: 450px;
                overflow-y: auto;
            }

            .bpmn-properties-tabs-links .property-item {
                margin-bottom: 0.75rem;
                padding-bottom: 0.75rem;
                border-bottom: 1px solid var(--border-color, #e9ecef);
            }

            .bpmn-properties-tabs-links .property-item:last-child {
                border-bottom: none;
                margin-bottom: 0;
                padding-bottom: 0;
            }

            .bpmn-properties-tabs-links .property-label {
                font-weight: 600;
                font-size: 0.75rem;
                color: var(--text-secondary, #6c757d);
                text-transform: uppercase;
                letter-spacing: 0.025em;
                margin-bottom: 0.25rem;
            }

            .bpmn-properties-tabs-links .property-value {
                font-size: 0.875rem;
                color: var(--text-primary, #2c3e50);
                word-break: break-word;
            }
        `;
        document.head.appendChild(styles);
    }

    /**
     * Check if current user is admin or super admin
     */
    async function checkUserIsAdmin() {
        if (currentUser) {
            const role = (currentUser.role || currentUser.systemRole || currentUser.SystemRole || '').toString().toLowerCase();
            return role === 'admin' || role === 'super admin' || role === 'superadmin' ||
                   role === 'super-admin' || role === 'super_admin' ||
                   role === '1' || role === '2';
        }
        return false;
    }

    /**
     * Check if current user is a stakeholder of the change request
     */
    async function checkUserIsStakeholder(changeRequestId) {
        if (!changeRequestId || !currentUser) {
            console.log('[Stakeholder Check] Missing changeRequestId or currentUser');
            return false;
        }
        
        try {
            const userId = currentUser.id || currentUser.userId || currentUser.ID;
            console.log('[Stakeholder Check] Checking if user', userId, 'is stakeholder for CR', changeRequestId);
            
            const response = await fetch(`/api/cr-stakeholders?crId=${changeRequestId}`, {
                method: 'GET',
                credentials: 'include'
            });
            
            if (!response.ok) {
                console.log('[Stakeholder Check] API response not OK:', response.status);
                return false;
            }
            
            const stakeholders = await response.json();
            if (!Array.isArray(stakeholders)) {
                console.log('[Stakeholder Check] Response is not an array:', stakeholders);
                return false;
            }
            
            console.log('[Stakeholder Check] Found', stakeholders.length, 'stakeholders');
            
            const isStakeholder = stakeholders.some(stakeholder => {
                const stakeholderUserId = stakeholder.personId || stakeholder.User_ID || stakeholder.userId || stakeholder.user_ID;
                const matches = stakeholderUserId == userId || stakeholderUserId === userId;
                if (matches) {
                    console.log('[Stakeholder Check] User', userId, 'matches stakeholder', stakeholderUserId);
                }
                return matches;
            });
            
            console.log('[Stakeholder Check] User', userId, 'is stakeholder:', isStakeholder);
            return isStakeholder;
        } catch (error) {
            console.error('[Stakeholder Check] Error checking if user is stakeholder:', error);
            return false;
        }
    }

    /**
     * Check if current user is a stakeholder but NOT the requestor (creator)
     * Only stakeholders (excluding requestor) can complete the CR
     */
    async function checkUserIsStakeholderNotRequestor(changeRequestId, changeRequest) {
        if (!changeRequestId || !currentUser) return false;
        
        try {
            const userId = currentUser.id || currentUser.userId || currentUser.ID;
            
            // Check if user is the requestor (creator)
            const createdBy = changeRequest?.createdBy || changeRequest?.CreatedBy || changeRequest?.createdby_id;
            if (createdBy != null && (createdBy == userId || createdBy === userId)) {
                return false; // User is the requestor, cannot complete
            }
            
            // Check if user is a stakeholder
            return await checkUserIsStakeholder(changeRequestId);
        } catch (error) {
            console.error('Error checking if user is stakeholder (not requestor):', error);
            return false;
        }
    }

    /**
     * Check if current user is a stakeholder on the CR AND has a role that is used in the workflow.
     * Only stakeholders with a workflow role can see the Complete button.
     */
    async function checkUserIsStakeholderWithWorkflowRole(changeRequestId, changeRequest) {
        if (!changeRequestId || !currentUser) return false;

        try {
            const userId = currentUser.id || currentUser.userId || currentUser.ID;

            // 1. Get stakeholders for this CR
            const stakeholdersResponse = await fetch(`/api/cr-stakeholders?crId=${changeRequestId}`, {
                method: 'GET', credentials: 'include'
            });
            if (!stakeholdersResponse.ok) return false;

            let stakeholders = await stakeholdersResponse.json();
            if (!Array.isArray(stakeholders)) stakeholders = [];

            // Find user's roles from stakeholders
            const userStakeholderRoles = new Set();
            for (const s of stakeholders) {
                const sUserId = s.personId || s.User_ID || s.userId || s.user_ID;
                if (sUserId == userId || sUserId === userId) {
                    const role = s.roleName || s.role_name || s.role;
                    if (role) {
                        userStakeholderRoles.add(normalizeRoleName(role));
                    }
                }
            }

            if (userStakeholderRoles.size === 0) {
                console.log('[CR Complete] User is not a stakeholder on this CR');
                return false;
            }

            // 2. Get workflow roles from the workflow instance tasks OR process definition
            let workflowRoles = new Set();

            // Try to get workflow instance first (for running workflows)
            const wfResponse = await fetch(`/api/workflow_instances/by-cr/${changeRequestId}`, {
                method: 'GET', credentials: 'include'
            }).catch(() => ({ ok: false }));

            if (wfResponse.ok) {
                const wfData = await wfResponse.json();
                const instance = wfData.instance;
                if (instance && instance.id) {
                    // Get all tasks for this workflow instance
                    const tasksResponse = await fetch(`/api/workflow_instances/${instance.id}/tasks`, {
                        method: 'GET', credentials: 'include'
                    }).catch(() => ({ ok: false }));

                    if (tasksResponse.ok) {
                        const tasksData = await tasksResponse.json();
                        const tasks = tasksData.tasks || [];
                        for (const task of tasks) {
                            const taskRole = task.role || task.roleName;
                            if (taskRole) {
                                workflowRoles.add(normalizeRoleName(taskRole));
                            }
                        }
                    }
                }
            }

            // If no workflow instance exists (e.g., "Pending Start" status), get roles from process definition
            if (workflowRoles.size === 0 && changeRequest && changeRequest.processDefinitionId) {
                console.log('[CR Complete] No workflow instance found, checking process definition for roles...');
                try {
                    // Try to get workflow validation which includes required roles
                    const validationResponse = await fetch(`/api/workflow/validate-start?changeRequestId=${changeRequestId}&processDefId=${changeRequest.processDefinitionId}`, {
                        method: 'GET', credentials: 'include'
                    }).catch(() => ({ ok: false }));

                    if (validationResponse.ok) {
                        const validationData = await validationResponse.json();
                        // Check if validation data contains required roles
                        if (validationData.requiredRoles && Array.isArray(validationData.requiredRoles)) {
                            for (const role of validationData.requiredRoles) {
                                const roleName = typeof role === 'string' ? role : (role.name || role.roleName || role);
                                if (roleName) {
                                    workflowRoles.add(normalizeRoleName(roleName));
                                }
                            }
                            console.log('[CR Complete] Found', workflowRoles.size, 'workflow roles from process definition');
                        }
                    } else {
                        console.warn('[CR Complete] Validation endpoint returned status:', validationResponse.status);
                    }
                } catch (error) {
                    console.warn('[CR Complete] Could not fetch roles from process definition:', error);
                }
            }

            if (workflowRoles.size === 0) {
                console.log('[CR Complete] No workflow roles found - cannot determine if user has workflow role');
                return false;
            }

            // 3. Check if any of user's stakeholder roles match a workflow role
            for (const userRole of userStakeholderRoles) {
                if (workflowRoles.has(userRole)) {
                    console.log('[CR Complete] User has matching workflow role:', userRole);
                    return true;
                }
            }

            console.log('[CR Complete] User stakeholder roles do not match any workflow role',
                { userRoles: [...userStakeholderRoles], workflowRoles: [...workflowRoles] });
            return false;
        } catch (error) {
            console.error('Error checking stakeholder with workflow role:', error);
            return false;
        }
    }

    /**
     * Check if current user is the requester (creator) of the CR
     */
    async function checkUserIsRequester(changeRequestId) {
        if (!changeRequestId || !currentUser) return false;

        try {
            const userId = currentUser.id || currentUser.userId || currentUser.ID;
            const response = await fetch(`/api/changerequests/${changeRequestId}`, {
                method: 'GET', credentials: 'include'
            });
            if (!response.ok) return false;

            const crData = await response.json();
            const createdBy = crData.createdBy || crData.CreatedBy || crData.created_by;
            return createdBy != null && (createdBy == userId || createdBy.toString() === userId.toString());
        } catch (error) {
            console.error('Error checking if user is requester:', error);
            return false;
        }
    }

    /**
     * Check if current user can see Complete/Cancel buttons
     * (Super admin, admin, stakeholders, or requester)
     */
    async function canSeeCRButtons(changeRequestId) {
        const isAdmin = await checkUserIsAdmin();
        if (isAdmin) return true;

        const isRequester = await checkUserIsRequester(changeRequestId);
        if (isRequester) return true;
        
        return await checkUserIsStakeholder(changeRequestId);
    }

    /**
     * Check if current user can manage workflows (start workflows, see workflow dropdown)
     * Allowed: admin, super admin, stakeholders, requester
     */
    async function canManageWorkflows(changeRequestId) {
        const isAdmin = await checkUserIsAdmin();
        if (isAdmin) return true;

        // Requester (creator) can start workflows
        const isRequester = await checkUserIsRequester(changeRequestId);
        if (isRequester) return true;
        
        return await checkUserIsStakeholder(changeRequestId);
    }

    /**
     * Check if current user can edit CR
     * Allowed: admin, super admin, requester (creator),
     *          OR web user with edit permission on Change Request fact + stakeholder on this CR
     */
    async function checkUserCanEditCR(changeRequestId) {
        if (!changeRequestId || !currentUser) {
            console.log('[CR Edit] Missing changeRequestId or currentUser');
            return false;
        }
        
        try {
            const currentUserId = currentUser.id || currentUser.ID || currentUser.userId || currentUser.UserID;
            console.log('[CR Edit] Checking edit permission for user', currentUserId, 'on CR', changeRequestId);
            
            // 1. Check if user is admin or super admin
            const isAdmin = await checkUserIsAdmin();
            if (isAdmin) {
                console.log('[CR Edit] ✅ User is admin/super admin, can edit CR');
                    return true;
            }
            
            // 2. Get CR details to check creator
            const response = await fetch(`/api/changerequests/${changeRequestId}`, {
                method: 'GET',
                credentials: 'include'
            });
            
            if (!response.ok) {
                console.log('[CR Edit] ❌ Failed to fetch CR details, status:', response.status);
                return false;
            }
            
            const crData = await response.json();
            const createdBy = crData.createdBy || crData.CreatedBy || crData.created_by;
            console.log('[CR Edit] CR createdBy:', createdBy, 'currentUserId:', currentUserId);
            
            // 3. Check if current user is the requester (creator)
            if (createdBy != null && currentUserId != null &&
                (createdBy == currentUserId || createdBy.toString() === currentUserId.toString())) {
                console.log('[CR Edit] ✅ User is the requester of CR, can edit');
                return true;
            }
            
            // 4. Check if user is a web user with edit permission on Change Request fact AND is a stakeholder
            const isStakeholder = await checkUserIsStakeholder(changeRequestId);
            console.log('[CR Edit] User is stakeholder:', isStakeholder);
            
            if (isStakeholder) {
                // Check edit permission on Change Request module
                try {
                    const permResponse = await fetch('/api/user/permissions/Change Requests', {
                        method: 'GET', credentials: 'include'
                    });
                    if (permResponse.ok) {
                        const perms = await permResponse.json();
                        console.log('[CR Edit] Change Request permissions:', perms);
                        if (perms.success && (perms.canEdit === true || perms.isAdmin === true)) {
                            console.log('[CR Edit] ✅ User is stakeholder with edit permission on Change Request, can edit');
                return true;
                        }
                    }
                } catch (permError) {
                    console.warn('[CR Edit] Could not check Change Request permissions:', permError);
                }
            }
            
            console.log('[CR Edit] ❌ User does not have permission to edit this CR');
                return false;
        } catch (error) {
            console.error('Error checking if user can edit CR:', error);
            return false;
        }
    }

    /**
     * Check if current user can cancel CR
     * Requester can cancel only if CR status is NOT RUNNING
     * Stakeholders can cancel even if CR status is RUNNING
     * For auto CRs, stakeholders must have a role that matches workflow roles
     */
    async function canCancelCR(changeRequest) {
        if (!changeRequest || !currentUser) {
            console.log('[CR Cancel] Missing changeRequest or currentUser');
            return false;
        }
        
        const userId = currentUser.id || currentUser.userId || currentUser.ID;
        const statusName = (changeRequest.statusName || changeRequest.StatusName || '').toLowerCase();
        const isRunning = statusName.includes('running') || statusName.includes('in progress');
        const isAutoCR = changeRequest.mandatoryWorkflow === true || changeRequest.mandatoryWorkflow === 'true';
        
        console.log('[CR Cancel] Checking cancel permission for user', userId, 'on CR', changeRequest.id, 'status:', statusName, 'isRunning:', isRunning, 'isAutoCR:', isAutoCR);
        
        // Check if user is the creator
        const isCreator = (changeRequest.createdBy == userId || changeRequest.createdBy === userId);
        console.log('[CR Cancel] User is creator:', isCreator);
        
        // Check if user is a stakeholder - this check is needed for both Running and non-Running statuses
        const isStakeholder = await checkUserIsStakeholder(changeRequest.id);
        console.log('[CR Cancel] User is stakeholder:', isStakeholder);
        
        // For auto CRs, check if stakeholder has matching workflow role
        let hasMatchingWorkflowRole = false;
        if (isAutoCR && isStakeholder) {
            hasMatchingWorkflowRole = await checkUserIsStakeholderWithWorkflowRole(changeRequest.id, changeRequest);
            console.log('[CR Cancel] User has matching workflow role:', hasMatchingWorkflowRole);
        }
        
        // NEW LOGIC: If CR status is RUNNING, only stakeholders can cancel
        // For auto CRs, stakeholder must have matching workflow role
        if (isRunning) {
            if (isStakeholder) {
                if (isAutoCR) {
                    if (hasMatchingWorkflowRole) {
                        console.log('[CR Cancel] ✅ User is a stakeholder with matching workflow role of CR with RUNNING status - can cancel');
                        return true;
                    } else {
                        console.log('[CR Cancel] ❌ User is a stakeholder but does not have a matching workflow role for CR with RUNNING status');
                        return false;
                    }
                } else {
                    // Manual CR - any stakeholder can cancel
                    console.log('[CR Cancel] ✅ User is a stakeholder of CR with RUNNING status - can cancel');
                    return true;
                }
            } else {
                // Even if user is creator, they cannot cancel if CR is RUNNING (unless they are also stakeholder)
                console.log('[CR Cancel] ❌ User is not authorized to cancel CR with RUNNING status. Only stakeholders can cancel RUNNING CRs.');
                if (isCreator) {
                    console.log('[CR Cancel] ℹ️ User is the creator but not a stakeholder - cannot cancel RUNNING CR');
                }
                return false;
            }
        }
        
        // If CR status is NOT RUNNING: creator OR stakeholder can cancel
        // For auto CRs, stakeholder must have matching workflow role
        if (isCreator) {
            console.log('[CR Cancel] ✅ User is creator and CR is not RUNNING - can cancel');
            return true;
        }
        
        if (isStakeholder) {
            if (isAutoCR) {
                if (hasMatchingWorkflowRole) {
                    console.log('[CR Cancel] ✅ User is a stakeholder with matching workflow role and CR is not RUNNING - can cancel');
                    return true;
                } else {
                    console.log('[CR Cancel] ❌ User is a stakeholder but does not have a matching workflow role for CR (not RUNNING)');
                    return false;
                }
            } else {
                // Manual CR - any stakeholder can cancel
                console.log('[CR Cancel] ✅ User is stakeholder and CR is not RUNNING - can cancel');
                return true;
            }
        }
        
        console.log('[CR Cancel] ❌ User is neither creator nor stakeholder - cannot cancel');
        return false;
    }

    /**
     * Get auto-complete change requests setting from system settings
     */
    async function getAutoCompleteSetting() {
        try {
            const response = await fetch('/api/system-settings/Change Requests', {
                method: 'GET',
                credentials: 'include'
            });
            if (response.ok) {
                const settings = await response.json();
                const autoComplete = settings.auto_complete_change_requests;
                return autoComplete === true || autoComplete === 'true';
            }
        } catch (error) {
            console.warn('Could not fetch auto-complete setting:', error);
        }
        return false; // Default to false if cannot fetch
    }

    // Create classifications section
    async function createClassificationsSection(changeRequest) {
        const section = document.createElement('div');
        section.className = 'view-section';
        
        const notSpecifiedText = (typeof window !== 'undefined' && window.I18n && window.I18n.t) ? window.I18n.t('message.notSpecified') : 'Not specified';
        const estimatedBenefit = changeRequest.estimatedBenefit ? 
            `${changeRequest.estimatedBenefit} ${changeRequest.estimatedBenefitCurrency || 'GBP'}` : 
            notSpecifiedText;
        
        const estimatedCost = changeRequest.estimatedCost ? 
            `${changeRequest.estimatedCost} ${changeRequest.estimatedCostCurrency || 'GBP'}` : 
            notSpecifiedText;

        // Get CR status name
        const statusName = changeRequest.statusName || changeRequest.axonStatus || 'Pending Start';
        const statusLower = statusName.toLowerCase();
        
        // Determine button visibility and state
        const canSeeButtons = await canSeeCRButtons(changeRequest.id);
        const canCancel = await canCancelCR(changeRequest);
        
        // Log cancel permission for debugging
        const isAutoCR = changeRequest.mandatoryWorkflow === true || changeRequest.mandatoryWorkflow === 'true';
        if (isAutoCR) {
            const hasWorkflowRole = await checkUserIsStakeholderWithWorkflowRole(changeRequest.id, changeRequest);
            console.log('[CR Cancel Button] Auto CR - canCancel:', canCancel, 'hasWorkflowRole:', hasWorkflowRole);
        } else {
            console.log('[CR Cancel Button] Manual CR - canCancel:', canCancel);
        }
        
        // Check workflow completion status directly
        let isWorkflowCompleted = workflowCompleted; // Use cached value first
        if (!isWorkflowCompleted && changeRequest.processInstanceId) {
            // Check workflow instance status directly
            try {
                const workflowResponse = await fetch(`/api/workflow_instances/by-cr/${changeRequest.id}`, {
                    method: 'GET',
                    credentials: 'include'
                }).catch(err => {
                    // Silently handle network errors - don't pollute console
                    return { ok: false, status: 0 };
                });
                // 404 is expected when workflow hasn't started yet - handle gracefully
                if (workflowResponse.status === 404) {
                    // No workflow instance yet - this is normal
                } else if (workflowResponse.ok) {
                    const workflowData = await workflowResponse.json();
                    const instance = workflowData.instance;
                    if (instance && (instance.status === 'Disabled' || instance.status === 'Completed' || instance.endedAt)) {
                        isWorkflowCompleted = true;
                        workflowCompleted = true; // Update cached value
                    }
                }
            } catch (error) {
                console.error('Error checking workflow status:', error);
            }
        }
        
        // Button visibility rules based on status
        let showComplete = false;
        let showCancel = false;
        let completeDisabled = true;
        let cancelDisabled = false; // Cancel button is always enabled (not blocked by workflow)
        
        // Check if already cancelled
        const isCancelled = statusLower.includes('cancelled') || statusLower.includes('canceled');
        
        // Check auto-complete setting first - if false, don't show complete button at all
        const autoCompleteEnabled = await getAutoCompleteSetting();
        
        if (canSeeButtons) {
            if (statusLower.includes('pending start')) {
                // Don't show complete button for pending start status
                showComplete = false;
                showCancel = !isCancelled && canCancel; // Show cancel only if not cancelled AND user can cancel
                console.log('[CR Cancel Button] Pending Start - showCancel:', showCancel, 'canCancel:', canCancel, 'isCancelled:', isCancelled);
                cancelDisabled = false; // Always enabled - not blocked by workflow
            } else if (statusLower.includes('running')) {
                // For Running CRs, show Complete button only if:
                // 1. Auto-complete is disabled (if enabled, button should not be shown at all)
                // 2. Workflow is completed (last task finished)
                // 3. User is a stakeholder on this CR AND has a role that is used in the workflow
                if (!autoCompleteEnabled) {
                    // Auto-complete is disabled - check if we should show the button
                    if (isWorkflowCompleted) {
                        // Workflow is completed - check if user is a stakeholder with a workflow role
                        const hasWorkflowRole = await checkUserIsStakeholderWithWorkflowRole(changeRequest.id, changeRequest);
                        if (hasWorkflowRole) {
                            showComplete = true;
                            completeDisabled = false;
                        } else {
                            // User is not a stakeholder with a workflow role - don't show button
                                showComplete = false;
                        }
                    } else {
                        // Workflow not completed yet - don't show button
                        showComplete = false;
                    }
                } else {
                    // Auto-complete is enabled - don't show button at all
                    showComplete = false;
                }
                // For auto CRs: Only stakeholders with workflow roles can cancel
                // For manual CRs: Any stakeholder can cancel
                // The canCancel check already handles this logic
                showCancel = !isCancelled && canCancel; // Show cancel only if not cancelled AND user can cancel
                console.log('[CR Cancel Button] Running - showCancel:', showCancel, 'canCancel:', canCancel, 'isCancelled:', isCancelled, 'isAutoCR:', isAutoCR);
                cancelDisabled = false; // Always enabled - not blocked by workflow
            } else if (statusLower.includes('paused')) {
                showComplete = false;
                showCancel = !isCancelled && canCancel; // Show cancel only if not cancelled AND user can cancel
                console.log('[CR Cancel Button] Paused - showCancel:', showCancel, 'canCancel:', canCancel, 'isCancelled:', isCancelled);
                cancelDisabled = false; // Always enabled - not blocked by workflow
            } else if (statusLower.includes('completed')) {
                // For Completed CRs, hide Complete button (already completed)
                showComplete = false;
                // Hide Cancel button for completed CRs (cannot cancel after completion)
                showCancel = false;
            } else {
                // For any other status, show cancel button only if not cancelled AND user can cancel
                showCancel = !isCancelled && canCancel;
                console.log('[CR Cancel Button] Other status - showCancel:', showCancel, 'canCancel:', canCancel, 'isCancelled:', isCancelled, 'status:', statusLower);
                cancelDisabled = false; // Always enabled - not blocked by workflow
            }
            // Cancelled: no buttons shown (handled by isCancelled check above)
        }
        
        // Build buttons HTML
        let buttonsHtml = '';
        console.log('[CR Cancel Button] Final decision - showComplete:', showComplete, 'showCancel:', showCancel, 'canSeeButtons:', canSeeButtons);
        if (showComplete || showCancel) {
            buttonsHtml = '<div class="section-actions">';
            if (showComplete) {
                // Complete button should be green and enabled when workflow is completed
                const completeBtnClass = 'btn btn-success btn-sm';
                const completeBtnStyle = completeDisabled ? 'opacity: 0.6; cursor: not-allowed;' : 'opacity: 1; cursor: pointer;';
                buttonsHtml += `<button id="completeCRBtn" class="${completeBtnClass}" ${completeDisabled ? 'disabled' : ''} style="${completeBtnStyle}">Complete</button>`;
            }
            if (showCancel) {
                const cancelBtnClass = 'btn btn-secondary btn-sm';
                const cancelBtnStyle = cancelDisabled ? 'opacity: 0.6; cursor: not-allowed;' : 'opacity: 1; cursor: pointer;';
                buttonsHtml += `<button id="cancelCRBtn" class="${cancelBtnClass}" ${cancelDisabled ? 'disabled' : ''} style="${cancelBtnStyle}">Cancel Request</button>`;
                console.log('[CR Cancel Button] ✅ Cancel button will be rendered');
            } else {
                console.log('[CR Cancel Button] ❌ Cancel button will NOT be rendered - showCancel is false');
            }
            buttonsHtml += '</div>';
        } else {
            console.log('[CR Cancel Button] ❌ No buttons will be rendered - both showComplete and showCancel are false');
        }

        section.innerHTML = `
            <div class="section-title">CLASSIFICATIONS</div>
            ${buttonsHtml}
            <div class="section-subtitle">BASIC CLASSIFICATIONS</div>
            <div class="view-item">
                <div class="view-label">BUDG Status:</div>
                <div class="view-value">${statusName}</div>
            </div>
            <div class="view-item">
                <div class="view-label">Severity:</div>
                <div class="view-value">${changeRequest.severityName || changeRequest.SeverityName || ((typeof window !== 'undefined' && window.I18n && window.I18n.t) ? window.I18n.t('message.notSpecified') : 'Not specified')}</div>
            </div>
            <div class="view-item">
                <div class="view-label">Urgency:</div>
                <div class="view-value">${changeRequest.urgencyName || changeRequest.UrgencyName || ((typeof window !== 'undefined' && window.I18n && window.I18n.t) ? window.I18n.t('message.notSpecified') : 'Not specified')}</div>
            </div>
            <div class="section-subtitle" style="margin-top: 1.5rem;">ADVANCED CLASSIFICATIONS</div>
            <div class="view-item">
                <div class="view-label">Estimated Benefit:</div>
                <div class="view-value">${estimatedBenefit}</div>
            </div>
            <div class="view-item">
                <div class="view-label">Estimated Cost:</div>
                <div class="view-value">${estimatedCost}</div>
            </div>
        `;
        
        // Add event listeners for buttons
        setTimeout(() => {
            const completeBtn = document.getElementById('completeCRBtn');
            if (completeBtn) {
                console.log('✅ [FRONTEND] Complete button found, attaching click handler for CR:', changeRequest.id);
                completeBtn.addEventListener('click', () => {
                    console.log('🖱️ [FRONTEND] Complete button clicked for CR:', changeRequest.id);
                    handleCompleteChangeRequest(changeRequest.id);
                });
            } else {
                const statusName = changeRequest.statusName || changeRequest.axonStatus || 'Unknown';
                console.log('⚠️ [FRONTEND] Complete button NOT found for CR:', changeRequest.id, 'Status:', statusName, '(Status ID:', changeRequest.crStatusId || changeRequest.statusId || 'N/A', ')');
            }
            const cancelBtn = document.getElementById('cancelCRBtn');
            if (cancelBtn) {
                cancelBtn.addEventListener('click', () => handleCancelChangeRequest(changeRequest.id));
            }
        }, 100);
        
        return section;
    }

    // Create other information section
    function createOtherInfoSection(changeRequest) {
        const section = document.createElement('div');
        section.className = 'view-section';
        
        const createdDate = changeRequest.createdAt ? 
            new Date(changeRequest.createdAt).toLocaleDateString('en-GB') : 
            ((typeof window !== 'undefined' && window.I18n && window.I18n.t) ? window.I18n.t('message.notSpecified') : 'Not specified');
        
        // Render created by user as a link
        const createdByHtml = renderPersonLink(
            changeRequest.createdByName, 
            changeRequest.createdBy
        );
        
        // Check if the record has been updated (updatedAt different from createdAt)
        const hasBeenUpdated = changeRequest.updatedAt && changeRequest.createdAt && 
            new Date(changeRequest.updatedAt).getTime() !== new Date(changeRequest.createdAt).getTime();
        
        // If not updated, show Created By/Date; otherwise show Last Updated By/Date
        const lastUpdatedHtml = hasBeenUpdated ? 
            renderPersonLink(changeRequest.lastUserChangeName, changeRequest.lastUserChange) : 
            createdByHtml; // Use Created By if not updated
        
        const updatedDate = hasBeenUpdated && changeRequest.updatedAt ? 
            new Date(changeRequest.updatedAt).toLocaleDateString('en-GB') : 
            createdDate; // Use Created Date if not updated

        section.innerHTML = `
            <div class="section-title">OTHER INFORMATION</div>
            <div class="view-item">
                <div class="view-label">Created By:</div>
                <div class="view-value">${createdByHtml}</div>
            </div>
            <div class="view-item">
                <div class="view-label">Created:</div>
                <div class="view-value">${createdDate}</div>
            </div>
            <div class="view-item">
                <div class="view-label">Last Updated By:</div>
                <div class="view-value">${lastUpdatedHtml}</div>
            </div>
            <div class="view-item">
                <div class="view-label">Last Updated:</div>
                <div class="view-value">${updatedDate}</div>
            </div>
        `;
        return section;
    }
    
    // Helper function to render person as a link
    function renderPersonLink(name, id) {
        if (!name || !id) {
            return '<span class="empty">-</span>';
        }
        // Use path format: /view/people/{id} instead of query parameter
        return `<a href="/view/people/${encodeURIComponent(id)}" class="people-link">${escapeHtml(name)}</a>`;
    }
    
    // Helper function to escape HTML
    function escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    // Setup edit dropdown
    async function setupEditDropdown() {
        const tabActions = document.querySelector('.tab-actions');
        if (tabActions && window.EditDropdown) {
            try {
                // Clear any existing dropdown containers first to prevent duplication
                const existingDropdowns = tabActions.querySelectorAll('.edit-dropdown-container');
                existingDropdowns.forEach(dropdown => dropdown.remove());
                
                // Check if CR is cancelled or completed - hide "Edit Item" option but keep dropdown for other actions
                let isCancelled = false;
                let isCompleted = false;
                if (currentChangeRequest) {
                    const statusName = currentChangeRequest.statusName || currentChangeRequest.StatusName || '';
                    const statusLower = statusName.toLowerCase();
                    const crStatusId = currentChangeRequest.crStatusId || currentChangeRequest.CR_StatusID;
                    isCancelled = crStatusId === 3 || statusLower.includes('cancelled') || statusLower.includes('canceled');
                    isCompleted = statusLower.includes('completed');
                }
                
                // Check if user can edit CR (Super admin, Admin, or Web user who is stakeholder)
                const canEditCR = await checkUserCanEditCR(currentChangeRequestId);
                const shouldHideEdit = isCancelled || isCompleted || !canEditCR;
                
                window.EditDropdown.initialize('change-request', currentChangeRequestId, {
                    container: '.tab-actions',
                    editUrl: `/view/change-request/change-request-edit.html?id=${currentChangeRequestId}`,
                    hideEditOption: shouldHideEdit
                });
            } catch (error) {
                console.error('Failed to initialize edit dropdown:', error);
            }
        }
    }

    // Load Changes to Review section for auto-generated CRs ONLY
    // ⚠️ CRITICAL: This section should ONLY appear for automatic CRs (mandatory_workflow = true)
    // Manual CRs should NOT show this section - they work like other facets without pending changes
    async function loadChangesToReview(changeRequest, container) {
        // ⚠️ CRITICAL: Only show for auto-generated CRs (mandatory_workflow = true)
        // Manual CRs should NOT show "Changes to Review" table
        const mandatoryWorkflow = changeRequest.mandatoryWorkflow;
        const isAutoCR = mandatoryWorkflow === true || mandatoryWorkflow === 1 || mandatoryWorkflow === 'true' || mandatoryWorkflow === '1';
        
        console.log('[ChangesToReview] Checking CR type:', {
            mandatoryWorkflow: mandatoryWorkflow,
            mandatoryWorkflowType: typeof mandatoryWorkflow,
            isAutoCR: isAutoCR
        });
        
        if (!isAutoCR) {
            console.log('[ChangesToReview] Manual CR detected - hiding Changes to Review section (only auto CRs show this)');
            container.style.display = 'none';
            return;
        }
        
        // Show CHANGES TO REVIEW table for ALL CR statuses (In Progress, Completed, Cancelled)
        // This allows users to review changes even after CR is completed or cancelled
        const crStatusId = changeRequest.crStatusId || changeRequest.CR_StatusID;
        console.log('[ChangesToReview] Auto CR detected - Loading changes for CR with status ID:', crStatusId);

        try {
            // Check if user can view this section (admin, super admin, or stakeholder)
            const canView = await canViewChangesToReview(changeRequest);
            if (!canView) {
                console.log('[ChangesToReview] User not authorized to view changes');
                container.style.display = 'none';
                return;
            }

            // Load pending changes for this CR
            const response = await fetch(`/api/pending-changes/cr/${currentChangeRequestId}`, {
                credentials: 'include'
            });
            
            if (!response.ok) {
                console.warn('[ChangesToReview] Failed to load pending changes:', response.status);
                container.style.display = 'none';
                return;
            }

            const data = await response.json();
            console.log('[ChangesToReview] Loaded pending changes:', data);

            const changes = (data && data.changes) ? data.changes : [];

            if (!data.success) {
                console.log('[ChangesToReview] API returned success=false');
                renderChangesToReview(container, [], []);
                return;
            }

            // Render even when empty, with an empty state
            const groupedChanges = groupChangesByObject(changes);
            renderChangesToReview(container, groupedChanges, changes);

        } catch (error) {
            console.error('[ChangesToReview] Error loading changes:', error);
            container.style.display = 'none';
        }
    }

    // Check if current user can view the Changes to Review section
    // Allowed users: 1) Super Admins, 2) Admins, 3) Requester (CR creator), 4) Stakeholders
    async function canViewChangesToReview(changeRequest) {
        try {
            let userData = null;
            let userId = null;
            let userRole = '';

            // Try to get user from AuthHelper first
            if (window.AuthHelper) {
                try {
                    userData = await window.AuthHelper.getCurrentUser();
                    if (userData) {
                        userId = userData.id || userData.userId;
                        userRole = (userData.role || userData.systemRole || '').toString().toLowerCase();
                    }
                } catch (e) {
                    console.debug('[ChangesToReview] AuthHelper failed, trying fallback');
                }
            }

            // Fallback: try /api/me endpoint
            if (!userData) {
                try {
                    const userResponse = await fetch('/api/me', { credentials: 'include' });
                    if (userResponse.ok) {
                        userData = await userResponse.json();
                        userId = userData.id || userData.userId;
                        userRole = (userData.role || userData.systemRole || '').toString().toLowerCase();
                    }
                } catch (e) {
                    console.debug('[ChangesToReview] /api/me failed, trying sessionStorage');
                }
            }

            // Fallback: try sessionStorage
            if (!userData) {
                try {
                    const userStr = sessionStorage.getItem('currentUser');
                    if (userStr) {
                        userData = JSON.parse(userStr);
                        userId = userData.id || userData.userId || userData.ID;
                        userRole = (userData.role || userData.systemRole || userData.SystemRole || '').toString().toLowerCase();
                    }
                } catch (e) {
                    console.debug('[ChangesToReview] sessionStorage parse failed');
                }
            }

            if (!userData || !userId) {
                console.log('[ChangesToReview] Could not determine user, defaulting to show (admin check)');
                // If we can't determine user, default to showing (will be filtered by backend if needed)
                return true;
            }
            
            console.log('[ChangesToReview] Checking permissions for user:', {
                userId: userId,
                userRole: userRole,
                normalizedRole: userRole.replace(/[_\s-]/g, ' ').replace(/\s+/g, ' ').trim(),
                crId: currentChangeRequestId
            });

            // 1. Check if user is Super Admin or Admin
            // Allowed: Super Admins and Admins
            // Normalize role string (handle variations like "super admin", "super-admin", "superadmin", etc.)
            const normalizedRole = userRole.replace(/[_\s-]/g, ' ').replace(/\s+/g, ' ').trim();
            const isSuperAdmin = normalizedRole === 'super admin' || 
                                 normalizedRole === 'superadmin' ||
                                 normalizedRole === 'suber admin'; // Handle typo in DB
            const isAdmin = normalizedRole === 'admin' || 
                           userRole === '1' || 
                           userRole === '2' ||
                           userRole === 'admin' ||
                           userRole === 'super admin' ||
                           userRole === 'superadmin' ||
                           userRole === 'super-admin' ||
                           userRole === 'super_admin';
            
            if (isSuperAdmin || isAdmin) {
                console.log('[ChangesToReview] User is ' + (isSuperAdmin ? 'Super Admin' : 'Admin') + ', can view');
                return true;
            }

            // 2. Check if user is the creator (requester) of the CR
            // Allowed: Requester (the user who created the CR)
            if (changeRequest) {
                const crCreatedBy = changeRequest.createdBy || changeRequest.CreatedBy || changeRequest.created_by || 
                                   changeRequest.createdby_id || changeRequest.Created_By;
                if (crCreatedBy != null && (crCreatedBy == userId || crCreatedBy === userId || 
                    String(crCreatedBy) === String(userId))) {
                    console.log('[ChangesToReview] User is the creator (requester) of CR, can view');
                    return true;
                }
            }

            // 3. Check if user is a stakeholder of this CR
            // Allowed: Stakeholders
            try {
                // Get CR ID from currentChangeRequestId or from changeRequest object
                const crId = currentChangeRequestId || 
                            changeRequest?.id || 
                            changeRequest?.ID || 
                            changeRequest?.changeRequestId;
                
                if (!crId) {
                    console.warn('[ChangesToReview] Cannot check stakeholders - no CR ID available');
                    return false;
                }
                
                const stakeholdersResponse = await fetch(`/api/cr-stakeholders?crId=${crId}`, {
                    credentials: 'include'
                });
                if (stakeholdersResponse.ok) {
                    let stakeholders = await stakeholdersResponse.json();
                    
                    // Handle case where response might be wrapped in an object
                    if (stakeholders && typeof stakeholders === 'object' && !Array.isArray(stakeholders)) {
                        stakeholders = stakeholders.stakeholders || stakeholders.data || Object.values(stakeholders);
                    }
                    
                    // Ensure it's an array
                    if (!Array.isArray(stakeholders)) {
                        stakeholders = [];
                    }
                    
                    console.log('[ChangesToReview] Checking stakeholders:', {
                        crId: crId,
                        userId: userId,
                        stakeholdersCount: stakeholders.length,
                        stakeholders: stakeholders.map(s => ({
                            userId: s.userId || s.User_ID || s.personId || s.id || s.ID,
                            userName: s.userName || s.user_name || s.name
                        }))
                    });
                    
                    const isStakeholder = stakeholders.some(s => {
                        const sId = s.userId || s.User_ID || s.personId || s.id || s.ID;
                        if (!sId) return false;
                        // Compare as both numbers and strings to handle type mismatches
                        return (sId == userId || sId === userId || String(sId) === String(userId));
                    });
                    
                    if (isStakeholder) {
                        console.log('[ChangesToReview] ✅ User is CR stakeholder, can view');
                        return true;
                    } else {
                        console.log('[ChangesToReview] ❌ User is NOT a CR stakeholder');
                    }
                } else {
                    console.warn('[ChangesToReview] Failed to fetch stakeholders:', stakeholdersResponse.status);
                }
            } catch (e) {
                console.error('[ChangesToReview] Error checking stakeholders:', e);
            }

            console.log('[ChangesToReview] User not authorized (not super admin, admin, requester, or stakeholder)');
            return false;
        } catch (error) {
            console.error('[ChangesToReview] Error checking authorization:', error);
            // Default to showing if we can't verify (better UX - backend will filter if needed)
            return true;
        }
    }

    // Group changes by object and tab (facetType + objectId + tabName)
    // IMPORTANT: Deduplicates changes and properly groups them
    function groupChangesByObject(changes) {
        // Step 1: Deduplicate changes - same fieldName + oldValue + newValue + tabName + objectId = duplicate
        const seen = new Set();
        const uniqueChanges = [];
        for (const change of changes) {
            const tabName = change.tabName || 'Summary';
            // Build a dedup key: tabName + objectId + fieldName + oldValue + newValue + relatedName + areaKey
            const dedupKey = `${tabName}|${change.objectId}|${change.fieldName || ''}|${change.oldValue || ''}|${change.newValue || ''}|${change.relatedName || ''}|${change.areaKey || ''}|${change.nobjectId || ''}`;
            if (seen.has(dedupKey)) {
                continue; // Skip duplicate
            }
            seen.add(dedupKey);
            uniqueChanges.push(change);
        }

        // Step 2: Group by facetType + objectId + tabName + objectName + nobjectId
        // This ensures each distinct object (document, relationship, impact, attribute, etc.) gets its own row
        // For attributes, nobjectId is the attribute ID, so each attribute gets its own row
        const grouped = {};
        uniqueChanges.forEach(change => {
            const tabName = change.tabName || 'Summary';
            const objectName = change.objectName || `${change.facetType} ${change.objectId}`;
            // Include nobjectId in the key for multi-row tables (documents, attributes, etc.)
            // This ensures each distinct row gets its own group
            const nobjectId = change.nobjectId || '';
            const key = `${change.facetType}_${change.objectId}_${tabName}_${objectName}_${nobjectId}`;
            if (!grouped[key]) {
                grouped[key] = {
                    facetType: change.facetType,
                    objectId: change.objectId,
                    objectName: objectName,
                    tabName: tabName,
                    changes: []
                };
            }
            grouped[key].changes.push(change);
        });

        // Return grouped changes - each group now represents a single distinct object
        return Object.values(grouped);
    }

    // Render the Changes to Review section
    function renderChangesToReview(container, groupedChanges, allChanges) {
        // Build table rows
        let tableRows = '';
        groupedChanges.forEach(group => {
            // Determine operation type (most common in group)
            // Priority: Inserted > Deleted > Updated
            const operations = group.changes.map(c => c.operation);
            const operation = operations.includes('Inserted') ? 'Inserted' : 
                             operations.includes('Created') ? 'Inserted' : 
                             operations.includes('Deleted') ? 'Deleted' : 
                             operations.includes('Removed') ? 'Deleted' : 
                             'Updated';
            
            // Get author name from first change
            const authorName = group.changes[0].userName || 'Unknown';
            
            const groupKey = `${group.facetType}_${group.objectId}_${group.tabName}`;
            tableRows += `
                <tr class="changes-row" data-object-key="${groupKey}">
                    <td>${escapeHtml(group.tabName || 'Summary')}</td>
                    <td>${escapeHtml(group.facetType)}</td>
                    <td>${escapeHtml(group.objectName)}</td>
                    <td>${escapeHtml(operation)}</td>
                    <td>${escapeHtml(authorName)}</td>
                    <td>
                        <a href="#" class="details-link" onclick="showChangeDetails('${group.facetType}', ${group.objectId}, '${escapeHtml(group.objectName).replace(/'/g, "\\'")}', '${group.tabName}'); return false;">Details</a>
                    </td>
                </tr>
            `;
        });

        const hasChanges = allChanges && allChanges.length > 0;

        container.innerHTML = `
            <div class="section-title">CHANGES TO REVIEW</div>
            <div class="changes-table-container">
                <table class="changes-table">
                    <thead>
                        <tr>
                            <th>Tab</th>
                            <th>Component</th>
                            <th>Object</th>
                            <th>Operation</th>
                            <th>Author</th>
                            <th></th>
                        </tr>
                    </thead>
                    <tbody>
                        ${hasChanges ? tableRows : `
                            <tr>
                                <td colspan="6" style="text-align:center; padding: 1rem; color: var(--text-muted, #9ca3af);">
                                    No pending changes to review
                                </td>
                            </tr>
                        `}
                    </tbody>
                </table>
                <div class="changes-count">${allChanges.length} record${allChanges.length !== 1 ? 's' : ''}</div>
            </div>
        `;

        container.style.display = 'block';

        // Store changes data for details modal
        window._pendingChangesData = allChanges;
    }

    // Show change details modal
    window.showChangeDetails = function(facetType, objectId, objectName, tabName) {
        const changes = window._pendingChangesData || [];
        const objectChanges = changes.filter(c => 
            c.facetType === facetType && 
            c.objectId === objectId && 
            (tabName ? c.tabName === tabName : true) &&
            (objectName ? c.objectName === objectName : true)
        );

        if (objectChanges.length === 0) {
            alert('No change details available');
            return;
        }

        // Deduplicate detail rows: same fieldName + oldValue + newValue = duplicate
        const detailSeen = new Set();
        const uniqueObjectChanges = [];
        for (const change of objectChanges) {
            const dedupKey = `${change.fieldName || ''}|${change.oldValue || ''}|${change.newValue || ''}|${change.relatedName || ''}|${change.nobjectId || ''}`;
            if (detailSeen.has(dedupKey)) continue;
            detailSeen.add(dedupKey);
            uniqueObjectChanges.push(change);
        }

        // Track which "Relationship Type" values have already been rendered
        // to avoid duplicates from both explicit field changes AND relationTypeName metadata
        const renderedRelTypes = new Set();

        // Build details table
        let detailRows = '';
        uniqueObjectChanges.forEach(change => {
            // Check if this is a file link field
            let newValueDisplay = escapeHtml(change.newValue || '');
            if (change.isFileLink && change.newValue) {
                const filePath = change.filePath || '';
                const isUrl = change.isUrl === 'true' || change.isUrl === true;
                const documentId = change.documentId;
                
                if (isUrl && filePath) {
                    newValueDisplay = `<a href="${escapeHtml(filePath)}" target="_blank" rel="noopener noreferrer" class="file-link">${escapeHtml(change.newValue)}</a>`;
                } else if (documentId) {
                    newValueDisplay = `<a href="/api/documents/${documentId}/download?facetType=glossary" target="_blank" class="file-link">${escapeHtml(change.newValue)}</a>`;
                }
            }
            
            // Track if this is a "Relationship Type" field to avoid duplicate rendering
            const isRelTypeField = (change.fieldName === 'Relationship Type' || change.fieldName === 'relationshipType');
            if (isRelTypeField) {
                const rtKey = `${change.oldValue || ''}|${change.newValue || ''}|${change.relatedName || ''}`;
                renderedRelTypes.add(rtKey);
            }
            
            // Add the main field row
            detailRows += `
                <tr>
                    <td>${escapeHtml(formatFieldName(change.fieldName))}</td>
                    <td>${escapeHtml(change.oldValue || '')}</td>
                    <td>${newValueDisplay}</td>
                </tr>
            `;
        });

        // Create modal
        const modal = document.createElement('div');
        modal.className = 'change-details-modal-overlay';
        modal.innerHTML = `
            <div class="change-details-modal">
                <div class="change-details-header">
                    <span>${escapeHtml(objectName)} Change Details</span>
                    <button class="change-details-close" onclick="closeChangeDetailsModal()">&times;</button>
                </div>
                <div class="change-details-body">
                    <table class="change-details-table">
                        <thead>
                            <tr>
                                <th>Field Name</th>
                                <th>Old Value</th>
                                <th>New Value</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${detailRows}
                        </tbody>
                    </table>
                    <div class="change-details-count">${objectChanges.length} records</div>
                </div>
                <div class="change-details-footer">
                    <button class="btn btn-secondary" onclick="closeChangeDetailsModal()">Close</button>
                </div>
            </div>
        `;

        document.body.appendChild(modal);

        // Close on overlay click
        modal.addEventListener('click', function(e) {
            if (e.target === modal) {
                closeChangeDetailsModal();
            }
        });
    };

    // Close change details modal
    window.closeChangeDetailsModal = function() {
        const modal = document.querySelector('.change-details-modal-overlay');
        if (modal) {
            modal.remove();
        }
    };

    // Format field name for display (convert camelCase/snake_case to Title Case)
    function formatFieldName(fieldName) {
        if (!fieldName) return '';
        return fieldName
            .replace(/_/g, ' ')
            .replace(/([a-z])([A-Z])/g, '$1 $2')
            .replace(/\b\w/g, c => c.toUpperCase());
    }

    // Load relationships (placeholder)
    async function loadRelationships() {
        const container = document.getElementById('changeRequestRelationshipsContainer');
        
        if (!currentChangeRequestId) {
            container.innerHTML = `
                <div class="relationships-full-width">
                    <div class="section-title">RELATIONSHIPS</div>
                    <div class="empty-relationships">
                        <i class="fas fa-exclamation-triangle"></i>
                        <p>Unable to load relationships: No change request ID found.</p>
                    </div>
                </div>
            `;
            return;
        }
        
        try {
            const response = await fetch(`/api/cr-relationships/${currentChangeRequestId}`);
            if (!response.ok) {
                throw new Error('Failed to load relationships');
            }
            
            const relationships = await response.json();
            
            if (relationships.length === 0) {
                container.innerHTML = `
                    <div class="relationships-full-width">
                        <div class="section-title">RELATIONSHIPS</div>
                        <div class="empty-relationships">
                            <i class="fas fa-project-diagram"></i>
                            <p>No relationships found for this change request.</p>
                        </div>
                    </div>
                `;
                return;
            }
            
            // Build relationships table
            let relationshipsHtml = `
                <div class="relationships-full-width">
                    <div class="section-title">RELATIONSHIPS</div>
                    <div class="relationships-table-container">
                        <table class="relationships-table">
                            <thead>
                                <tr>
                                    <th>Relationship Type</th>
                                    <th>Object Type</th>
                                    <th>Related Change Request</th>
                                    <th>Created</th>
                                </tr>
                            </thead>
                            <tbody>
            `;
            
            relationships.forEach(relationship => {
                const objectType = extractObjectTypeFromReference(relationship.targetChangeRequestReference);
                const createdDate = new Date(relationship.createdAt).toLocaleDateString();
                
                relationshipsHtml += `
                    <tr>
                        <td>
                            <span class="relationship-type-badge">${escapeHtml(relationship.relationshipTypeName)}</span>
                        </td>
                        <td>
                            <span class="object-type-badge">${escapeHtml(objectType)}</span>
                        </td>
                        <td>
                            <a href="/view/change-request/change-request-view.html?id=${relationship.targetId}" 
                               class="related-cr-link" target="_blank">
                                ${escapeHtml(relationship.targetChangeRequestTitle)}
                            </a>
                        </td>
                        <td>${createdDate}</td>
                    </tr>
                `;
            });
            
            relationshipsHtml += `
                            </tbody>
                        </table>
                    </div>
                </div>
            `;
            
            container.innerHTML = relationshipsHtml;
            
        } catch (error) {
            console.error('Error loading relationships:', error);
            container.innerHTML = `
                <div class="relationships-full-width">
                    <div class="section-title">RELATIONSHIPS</div>
                    <div class="empty-relationships">
                        <i class="fas fa-exclamation-triangle"></i>
                        <p>Error loading relationships: ${escapeHtml(error.message)}</p>
                    </div>
                </div>
            `;
        }
    }

    // Extract object type from reference string
    function extractObjectTypeFromReference(reference) {
        if (!reference) return 'Unknown';
        const match = reference.match(/^(.+?)\s+\d+$/);
        return match ? match[1].trim() : 'Unknown';
    }

    // Load stakeholders from API
    async function loadStakeholders() {
        const container = document.getElementById('changeRequestStakeholdersContainer');
        
        // Show loading state
        container.innerHTML = `
            <div class="view-section">
                <div class="section-title">${window.I18n?.t('changeRequest.stakeholder.sections.directStakeholders') || 'DIRECT STAKEHOLDERS'}</div>
                <div class="loading-state">
                    <i class="fas fa-spinner fa-spin"></i> Loading stakeholders...
                </div>
            </div>
        `;
        
        try {
            const response = await fetch(`/api/cr-stakeholders?crId=${currentChangeRequestId}`);
            
            if (!response.ok) {
                throw new Error('Failed to load stakeholders');
            }
            
            const stakeholders = await response.json();
            console.log('Loaded stakeholders:', stakeholders);
            
            if (stakeholders.length === 0) {
                container.innerHTML = `
                    <div class="view-section">
                        <div class="section-header">
                            <div class="section-title">${window.I18n?.t('changeRequest.stakeholder.sections.directStakeholders') || 'DIRECT STAKEHOLDERS'}</div>
                            <div class="section-actions">
                                <button class="btn-icon" title="Settings">
                                    <i class="fas fa-cog"></i>
                                </button>
                            </div>
                        </div>
                        <div class="empty-state">
                            <i class="fas fa-info-circle"></i>
                            <p>No stakeholders found for this change request.</p>
                        </div>
                    </div>
                    <div id="crStakeholderCommunityContainer">
                        <div class="loading-state">
                            <i class="fas fa-spinner fa-spin"></i> Loading stakeholder community...
                        </div>
                    </div>
                `;
                
                // Load stakeholder community using the dedicated component
                if (window.CRStakeholderCommunity) {
                    window.CRStakeholderCommunity.init(currentChangeRequestId, currentChangeRequest?.reference);
                }
                
                // Ensure sub-tabs are attached for Followers (separate from Stakeholders)
                // Use setTimeout to ensure DOM is ready and container is visible
                setTimeout(() => {
                    if (window.attachSubtabs && container) {
                        if (!container.dataset.subtabsAttached) {
                            console.log('[CR View] Attaching sub-tabs for empty stakeholders');
                            window.attachSubtabs(container);
                        }
                    }
                }, 200);
                
                return;
            }
            
            // Render stakeholders table
            container.innerHTML = `
                <div class="view-section">
                    <div class="section-header">
                        <div class="section-title">${window.I18n?.t('changeRequest.stakeholder.sections.directStakeholders') || 'DIRECT STAKEHOLDERS'}</div>
                        <div class="section-actions">
                            <button class="btn-icon" title="Settings">
                                <i class="fas fa-cog"></i>
                            </button>
                        </div>
                    </div>
                    <div class="data-table-wrapper">
                        <table class="data-table stakeholder-table">
                            <thead>
                                <tr>
                                    <th><div class="th-content"><span>Role</span></div></th>
                                    <th><div class="th-content"><span>Name</span></div></th>
                                    <th><div class="th-content"><span>Org Unit</span></div></th>
                                    <th><div class="th-content"><span>Role Accepted</span></div></th>
                                </tr>
                            </thead>
                            <tbody>
                                ${stakeholders.map(s => renderStakeholderRow(s)).join('')}
                            </tbody>
                        </table>
                        <div class="table-footer">${stakeholders.length} record${stakeholders.length !== 1 ? 's' : ''}</div>
                    </div>
                </div>
                <div id="crStakeholderCommunityContainer">
                    <div class="loading-state">
                        <i class="fas fa-spinner fa-spin"></i> Loading stakeholder community...
                    </div>
                </div>
            `;
            
            // Load stakeholder community using the dedicated component
            if (window.CRStakeholderCommunity) {
                window.CRStakeholderCommunity.init(currentChangeRequestId, currentChangeRequest?.reference);
            }
            
            // Ensure sub-tabs are attached for Followers (separate from Stakeholders)
            // Use setTimeout to ensure DOM is ready and container is visible
            setTimeout(() => {
                if (window.attachSubtabs && container) {
                    // Check if sub-tabs are already attached
                    if (!container.dataset.subtabsAttached) {
                        console.log('[CR View] Attaching sub-tabs for Stakeholders/Followers');
                        window.attachSubtabs(container);
                    } else {
                        console.log('[CR View] Sub-tabs already attached');
                    }
                }
            }, 200);
            
        } catch (error) {
            console.error('Error loading stakeholders:', error);
            container.innerHTML = `
                <div class="view-section">
                    <div class="section-title">${window.I18n?.t('changeRequest.stakeholder.sections.directStakeholders') || 'DIRECT STAKEHOLDERS'}</div>
                    <div class="empty-state">
                        <i class="fas fa-exclamation-triangle"></i>
                        <p>Failed to load stakeholders.</p>
                    </div>
                </div>
            `;
            
                // Still try to attach sub-tabs even if stakeholders failed to load
                setTimeout(() => {
                    if (window.attachSubtabs && container) {
                        if (!container.dataset.subtabsAttached) {
                            console.log('[CR View] Attaching sub-tabs after error');
                            window.attachSubtabs(container);
                        }
                    }
                }, 200);
        }
    }

    // Stakeholder community is now handled by the dedicated CRStakeholderCommunity component
    // See: change-request-stakeholder-community.js


    // Get object type icon
    function getObjectTypeIcon(objectType) {
        const icons = {
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
            'Dataset': 'fa-database'
        };
        return icons[objectType] || 'fa-circle';
    }

    // Get object type URL path
    function getObjectTypeUrl(objectType) {
        const urls = {
            'Process': 'process',
            'Product': 'product',
            'Project': 'project',
            'Client': 'client',
            'Glossary': 'glossary',
            'Business Area': 'business-area',
            'Capability': 'capability',
            'Policy': 'policy',
            'Legal': 'LegalEntity',
            'System': 'system',
            'Dataset': 'dataset'
        };
        return urls[objectType] || 'unknown';
    }
    
    // Render a single stakeholder row
    function renderStakeholderRow(stakeholder) {
        // Support both old field names (userName, userId) and new field names (personName, personId)
        const roleName = stakeholder.roleName || '';
        const userName = stakeholder.personName || stakeholder.userName || '';
        const orgUnitName = stakeholder.orgUnitName || '';
        const acceptedStatus = stakeholder.roleAccepted || stakeholder.acceptedStatus || '';
        const userId = stakeholder.personId || stakeholder.userId;
        const orgUnitId = stakeholder.orgUnitId;
        
        return `
            <tr>
                <td>
                    <div class="role-cell">
                        <i class="fas fa-user-tag"></i>
                        <span>${escapeHtml(roleName)}</span>
                    </div>
                </td>
                <td>
                    <div class="name-cell">
                        <i class="fas fa-user"></i>
                        ${userId ? `<a href="/view/people/${userId}">${escapeHtml(userName)}</a>` : escapeHtml(userName)}
                    </div>
                </td>
                <td>
                    <div class="org-cell">
                        <i class="fas fa-sitemap"></i>
                        ${orgUnitId ? `<a href="/view/org-unit/${orgUnitId}">${escapeHtml(orgUnitName)}</a>` : escapeHtml(orgUnitName)}
                    </div>
                </td>
                <td>${escapeHtml(acceptedStatus)}</td>
            </tr>
        `;
    }

    // Load history via change-request-history.js component
    function loadHistory() {
        const container = document.getElementById('changeRequestHistoryContainer');
        const crId = currentChangeRequestId || getChangeRequestIdFromUrl();
        if (container && crId && typeof window.initChangeRequestHistory === 'function') {
            window.initChangeRequestHistory('changeRequestHistoryContainer', crId);
        } else if (container) {
            container.innerHTML = `
                <div class="view-section">
                    <div class="section-title">HISTORY</div>
                    <div class="empty-state">
                        <i class="fas fa-info-circle"></i>
                        No history records found for this change request.
                    </div>
                </div>
            `;
        }
    }

    // Test API connection
    async function testApiConnection() {
        try {
            console.log('Testing API connection...');
            const response = await fetch('/api/changerequests');
            console.log('API test response status:', response.status);
            
            if (response.ok) {
                const data = await response.json();
                console.log('API test successful. Found', data.length, 'change requests');
                if (data.length > 0) {
                    console.log('Sample change request:', data[0]);
                }
            } else {
                const errorText = await response.text();
                console.error('API test failed:', errorText);
            }
        } catch (error) {
            console.error('API test error:', error);
        }
    }

    // Show error message
    function showError(message) {
        const container = document.getElementById('changeRequestViewContainer');
        if (container) {
            container.innerHTML = `
                <div class="error-state">
                    <i class="fas fa-exclamation-triangle"></i>
                    <p>${message}</p>
                </div>
            `;
        }
    }

    // Analysis form functions
    window.showAnalysisForm = function() {
        document.getElementById('analysisForm').style.display = 'block';
        document.getElementById('addAnalysisBtn').style.display = 'none';
        // Clear any edit state
        currentEditingAnalysisId = null;
        document.getElementById('analysisText').value = '';
        setTimeout(() => attachMentionSystem('analysisText'), 100);
    };

    window.cancelAnalysis = async function() {
        document.getElementById('analysisForm').style.display = 'none';
        document.getElementById('analysisText').value = '';
        currentEditingAnalysisId = null;
        // Wait a bit for DOM to update, then check button visibility based on actual data
        setTimeout(async () => {
            await ensureAnalysisResolutionButtonsVisible();
        }, 100);
    };

    // Edit analysis functionality
    let currentEditingAnalysisId = null;
    
    window.editAnalysis = function(analysisId, analysisText) {
        currentEditingAnalysisId = analysisId;
        document.getElementById('analysisText').value = analysisText;
        document.getElementById('analysisForm').style.display = 'block';
        document.getElementById('addAnalysisBtn').style.display = 'none';
    };

    window.saveAnalysis = async function() {
        const analysisText = document.getElementById('analysisText').value.trim();
        if (!analysisText) {
            alert('Please enter analysis text');
            return;
        }

        try {
            let response;
            if (currentEditingAnalysisId) {
                // Update existing analysis
                response = await fetch(`/api/changerequest-analysis/${currentEditingAnalysisId}`, {
                    method: 'PUT',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    credentials: 'include',
                    body: JSON.stringify({
                        analysis: analysisText
                    })
                });
            } else {
                // Create new analysis
                response = await fetch('/api/changerequest-analysis', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    credentials: 'include',
                    body: JSON.stringify({
                        changeRequestId: currentChangeRequestId,
                        analysis: analysisText
                    })
                });
            }

            if (response.ok) {
                alert(currentEditingAnalysisId ? 'Analysis updated successfully' : 'Analysis saved successfully');
                cancelAnalysis();
                // Force reload to update UI and ensure buttons are hidden correctly (bypass cache)
                await loadAnalysisData(currentChangeRequestId, 0, true);
                await ensureAnalysisResolutionButtonsVisible();
            } else {
                const error = await response.json().catch(() => ({ error: 'Failed to save analysis' }));
                alert('Error: ' + (error.error || 'Failed to save analysis'));
            }
        } catch (error) {
            console.error('Error saving analysis:', error);
            alert('Error saving analysis: ' + error.message);
        }
    };

    // Resolution form functions
    window.showResolutionForm = function() {
        document.getElementById('resolutionForm').style.display = 'block';
        document.querySelector('button[onclick="showResolutionForm()"]').style.display = 'none';
        // Clear any edit state
        currentEditingResolutionId = null;
        document.getElementById('resolutionStatus').value = '';
        document.getElementById('resolutionDescription').value = '';
        setTimeout(() => attachMentionSystem('resolutionDescription'), 100);
    };

    window.cancelResolution = async function() {
        document.getElementById('resolutionForm').style.display = 'none';
        document.getElementById('resolutionStatus').value = '';
        document.getElementById('resolutionDescription').value = '';
        currentEditingResolutionId = null;
        // Wait a bit for DOM to update, then check button visibility based on actual data
        setTimeout(async () => {
            await ensureAnalysisResolutionButtonsVisible();
        }, 100);
    };

    // Edit resolution functionality
    let currentEditingResolutionId = null;
    
    window.editResolution = async function(resolutionId, statusId, description) {
        currentEditingResolutionId = resolutionId;
        
        // Ensure resolution statuses are loaded before setting the value
        const statusSelect = document.getElementById('resolutionStatus');
        if (statusSelect && statusSelect.options.length <= 1) {
            // Statuses not loaded yet, load them first
            await loadResolutionStatuses();
            // Wait a bit for options to be added
            await new Promise(resolve => setTimeout(resolve, 100));
        }
        
        // Set the status value (handle null/undefined/0)
        if (statusSelect) {
            // Convert statusId to string and handle null/undefined/0
            const statusValue = statusId != null && statusId !== 'null' && statusId !== 'undefined' && statusId !== 0 
                ? String(statusId) 
                : '';
            
            // Check if the option exists before setting
            const optionExists = Array.from(statusSelect.options).some(opt => opt.value === statusValue);
            if (optionExists || statusValue === '') {
                statusSelect.value = statusValue;
                console.log('Set resolution status to:', statusValue);
            } else {
                console.warn('Resolution status option not found:', statusValue, 'Available options:', Array.from(statusSelect.options).map(o => o.value));
                statusSelect.value = ''; // Clear if option doesn't exist
            }
        }
        
        const descField = document.getElementById('resolutionDescription');
        if (descField) {
            descField.value = description || '';
        }
        
        const form = document.getElementById('resolutionForm');
        if (form) {
            form.style.display = 'block';
        }
        
        const addBtn = document.querySelector('button[onclick="showResolutionForm()"]');
        if (addBtn) {
            addBtn.style.display = 'none';
        }
    };

    window.saveResolution = async function() {
        const statusId = document.getElementById('resolutionStatus').value;
        const description = document.getElementById('resolutionDescription').value.trim();
        
        if (!statusId) {
            alert('Please select a resolution status');
            return;
        }
        
        if (!description) {
            alert('Please enter a description');
            return;
        }

        try {
            let response;
            if (currentEditingResolutionId) {
                // Update existing resolution
                response = await fetch(`/api/changerequest-resolution/${currentEditingResolutionId}`, {
                    method: 'PUT',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    credentials: 'include',
                    body: JSON.stringify({
                        resolutionStatusId: statusId,
                        description: description
                    })
                });
            } else {
                // Create new resolution
                response = await fetch('/api/changerequest-resolution', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    credentials: 'include',
                    body: JSON.stringify({
                        changeRequestId: currentChangeRequestId,
                        resolutionStatusId: statusId,
                        description: description
                    })
                });
            }

            if (response.ok) {
                alert(currentEditingResolutionId ? 'Resolution updated successfully' : 'Resolution saved successfully');
                cancelResolution();
                // Force reload to update UI and ensure buttons are hidden correctly (bypass cache)
                await loadResolutionData(currentChangeRequestId, 0, true);
                await ensureAnalysisResolutionButtonsVisible();
            } else {
                const error = await response.json().catch(() => ({ error: 'Failed to save resolution' }));
                alert('Error: ' + (error.error || 'Failed to save resolution'));
            }
        } catch (error) {
            console.error('Error saving resolution:', error);
            alert('Error saving resolution: ' + error.message);
        }
    };

    // ===== REQUEST DEDUPLICATION & ABORT SYSTEM =====
    // Prevents duplicate API calls and cancels stale requests
    const _activeAbortControllers = { analysis: null, resolution: null };
    let _isLoadingAnalysis = false;
    let _isLoadingResolution = false;
    // Shared data store so ensureButtonsVisible doesn't re-fetch from API
    let _lastAnalysisData = null;
    let _lastResolutionData = null;

    // Stable DOM element finder with polling
    function _waitForElement(id, maxMs = 2000) {
        return new Promise((resolve) => {
            const el = document.getElementById(id);
            if (el) return resolve(el);
            const start = Date.now();
            const iv = setInterval(() => {
                const e = document.getElementById(id);
                if (e || Date.now() - start > maxMs) { clearInterval(iv); resolve(e || null); }
            }, 50);
        });
    }

    // Load analysis data – deduped, abortable, with retry
    async function loadAnalysisData(changeRequestId, retryCount = 0, forceRefresh = false) {
        const maxRetries = 3;
        if (_isLoadingAnalysis && retryCount === 0 && !forceRefresh) return;
        _isLoadingAnalysis = true;

        // Cancel any previous in-flight request (unless force refresh)
        if (_activeAbortControllers.analysis && !forceRefresh) { _activeAbortControllers.analysis.abort(); }
        const ac = new AbortController();
        _activeAbortControllers.analysis = ac;

        try {
            const display = await _waitForElement('analysisDisplay');
                if (!display) {
                if (retryCount < maxRetries) {
                    _isLoadingAnalysis = false;
                    setTimeout(() => loadAnalysisData(changeRequestId, retryCount + 1, forceRefresh), 500 * (retryCount + 1));
                }
                    return;
                }

            // Add cache-busting timestamp if force refresh
            const url = forceRefresh 
                ? `/api/changerequest-analysis?changeRequestId=${changeRequestId}&_t=${Date.now()}`
                : `/api/changerequest-analysis?changeRequestId=${changeRequestId}`;
            
            const response = await fetch(url, {
                credentials: 'include', cache: 'no-cache', signal: ac.signal
            });

            let analysisData = null;
            const text = await response.text();
            if (text && text.trim()) { try { analysisData = JSON.parse(text); } catch (e) { /* ignore */ } }

            // Store for shared use (ensureButtonsVisible will read this)
            _lastAnalysisData = (analysisData && Array.isArray(analysisData)) ? analysisData : [];
            console.log('[loadAnalysisData] Updated _lastAnalysisData:', _lastAnalysisData.length, 'items');

            if (_lastAnalysisData.length > 0) {
                const cr = currentChangeRequest;
                const sn = cr ? (cr.statusName || cr.StatusName || cr.axonStatus || '').toLowerCase() : '';
                const isStatusBlocked = sn.includes('completed') || sn.includes('cancelled') || sn.includes('canceled');
                
                // Check if user has permission to edit analysis
                const canEditAnalysis = await checkUserCanEditCR(changeRequestId);
                const hideEdit = isStatusBlocked || !canEditAnalysis;

                // Don't manually hide buttons here - let ensureButtonsVisible handle it

                display.innerHTML = _lastAnalysisData.map(item => `
                    <div class="analysis-entry" style="background:#f8f9fa;padding:.75rem;border-radius:6px;margin-bottom:.5rem;border-left:3px solid #6366f1;">
                        <div class="analysis-content" style="margin-bottom:.5rem;">${item.analysis || 'No description'}</div>
                        <div class="analysis-actions" style="display:flex;gap:.5rem;align-items:center;">
                            ${hideEdit ? '' : `<button class="btn btn-sm btn-outline-primary" onclick="editAnalysis(${item.id}, '${(item.analysis || '').replace(/'/g, "\\'").replace(/"/g, "&quot;")}')"><i class="fas fa-edit"></i> Edit</button>`}
                            <span style="font-size:.75rem;color:#6b7280;">${item.createdAt ? new Date(item.createdAt).toLocaleDateString() : ''}</span>
                            </div>
                        </div>
                    `).join('');
            } else if (response.ok) {
                    display.innerHTML = 'No analysis recorded yet';
                // Don't manually show/hide buttons here - let ensureButtonsVisible handle it
            }
            
            // Always update button visibility after loading data
            await ensureAnalysisResolutionButtonsVisible();
        } catch (error) {
            if (error.name === 'AbortError') return; // Cancelled – newer request in flight
            console.error('[loadAnalysisData] Error:', error);
            if (retryCount < maxRetries) {
                setTimeout(() => { _isLoadingAnalysis = false; loadAnalysisData(changeRequestId, retryCount + 1); },
                    500 * Math.pow(2, retryCount) + Math.random() * 100);
            }
        } finally {
            _isLoadingAnalysis = false;
            if (_activeAbortControllers.analysis === ac) _activeAbortControllers.analysis = null;
        }
    }
    
    // Load resolution data – deduped, abortable, with retry
    async function loadResolutionData(changeRequestId, retryCount = 0, forceRefresh = false) {
        const maxRetries = 3;
        if (_isLoadingResolution && retryCount === 0 && !forceRefresh) return;
        _isLoadingResolution = true;

        if (_activeAbortControllers.resolution && !forceRefresh) { _activeAbortControllers.resolution.abort(); }
        const ac = new AbortController();
        _activeAbortControllers.resolution = ac;

        try {
            const display = await _waitForElement('resolutionDisplay');
                if (!display) {
                if (retryCount < maxRetries) {
                    _isLoadingResolution = false;
                    setTimeout(() => loadResolutionData(changeRequestId, retryCount + 1, forceRefresh), 500 * (retryCount + 1));
                }
                    return;
                }

            // Add cache-busting timestamp if force refresh
            const url = forceRefresh 
                ? `/api/changerequest-resolution?changeRequestId=${changeRequestId}&_t=${Date.now()}`
                : `/api/changerequest-resolution?changeRequestId=${changeRequestId}`;
            
            const response = await fetch(url, {
                credentials: 'include', cache: 'no-cache', signal: ac.signal
            });

            let resolutionData = null;
            const text = await response.text();
            if (text && text.trim()) { try { resolutionData = JSON.parse(text); } catch (e) { /* ignore */ } }

            _lastResolutionData = (resolutionData && Array.isArray(resolutionData)) ? resolutionData : [];
            console.log('[loadResolutionData] Updated _lastResolutionData:', _lastResolutionData.length, 'items');

                display.innerHTML = '';

            if (_lastResolutionData.length > 0) {
                const cr = currentChangeRequest;
                const sn = cr ? (cr.statusName || cr.StatusName || cr.axonStatus || '').toLowerCase() : '';
                const isStatusBlocked = sn.includes('completed') || sn.includes('cancelled') || sn.includes('canceled');
                
                // Check if user has permission to edit resolution
                const canEditResolution = await checkUserCanEditCR(changeRequestId);
                const hideEdit = isStatusBlocked || !canEditResolution;

                // Don't manually hide buttons here - let ensureButtonsVisible handle it

                display.innerHTML = _lastResolutionData.map(item => {
                    const desc = (item.description || '').replace(/'/g, "\\'").replace(/"/g, "&quot;");
                        return `
                    <div class="resolution-entry" style="background:#f0f9ff;padding:.75rem;border-radius:6px;margin-bottom:.5rem;border-left:3px solid #0ea5e9;">
                        <div class="resolution-status" style="font-weight:600;color:#0369a1;margin-bottom:.25rem;">${item.statusName || 'Status not specified'}</div>
                        ${item.description ? `<div class="resolution-content" style="margin-bottom:.5rem;">${item.description}</div>` : ''}
                        <div class="resolution-actions" style="display:flex;gap:.5rem;align-items:center;">
                            ${hideEdit ? '' : `<button class="btn btn-sm btn-outline-primary" onclick="editResolution(${item.id}, ${item.resolutionStatusId || null}, '${desc}')"><i class="fas fa-edit"></i> Edit</button>`}
                            <span style="font-size:.75rem;color:#6b7280;">${item.createdAt ? new Date(item.createdAt).toLocaleDateString() : ''}</span>
                            </div>
                    </div>`;
                    }).join('');
            } else if (response.ok) {
                    display.innerHTML = 'No resolution recorded yet';
                // Don't manually show/hide buttons here - let ensureButtonsVisible handle it
                }
                
            // Always update button visibility after loading data
                await ensureAnalysisResolutionButtonsVisible();
        } catch (error) {
            if (error.name === 'AbortError') return;
            console.error('[loadResolutionData] Error:', error);
            if (retryCount < maxRetries) {
                setTimeout(() => { _isLoadingResolution = false; loadResolutionData(changeRequestId, retryCount + 1); },
                    500 * Math.pow(2, retryCount) + Math.random() * 100);
            }
        } finally {
            _isLoadingResolution = false;
            if (_activeAbortControllers.resolution === ac) _activeAbortControllers.resolution = null;
        }
    }

    // Show/hide buttons – uses in-memory data with DOM fallback
    async function ensureAnalysisResolutionButtonsVisible(changeRequestParam) {
        const changeRequest = changeRequestParam || currentChangeRequest;
        if (!changeRequest) return;
        
        const statusName = (changeRequest.statusName || changeRequest.StatusName || changeRequest.axonStatus || '').toLowerCase();
        const shouldHideButtons = statusName.includes('completed') || statusName.includes('cancelled') || statusName.includes('canceled');
        const canEditCR = await checkUserCanEditCR(changeRequest.id || changeRequest.ID);

        // Use in-memory data if available, otherwise check DOM as fallback
        let hasAnalysis = _lastAnalysisData && Array.isArray(_lastAnalysisData) && _lastAnalysisData.length > 0;
        let hasResolution = _lastResolutionData && Array.isArray(_lastResolutionData) && _lastResolutionData.length > 0;
        
        // Fallback to DOM check if memory data is not available (e.g., initial load or after cancel)
        if (!hasAnalysis) {
        const analysisDisplay = document.getElementById('analysisDisplay');
            if (analysisDisplay) {
                const content = analysisDisplay.innerHTML.trim();
                hasAnalysis = content && 
                    content !== 'No analysis recorded yet' && 
                    !content.includes('No analysis recorded yet') &&
                    content.length > 0;
            }
        }
        
        if (!hasResolution) {
        const resolutionDisplay = document.getElementById('resolutionDisplay');
            if (resolutionDisplay) {
                const content = resolutionDisplay.innerHTML.trim();
                hasResolution = content && 
                    content !== 'No resolution recorded yet' && 
                    !content.includes('No resolution recorded yet') &&
                    content.length > 0;
            }
        }

        console.log('[ensureButtonsVisible] hasAnalysis:', hasAnalysis, 'hasResolution:', hasResolution, 'canEditCR:', canEditCR, 'shouldHideButtons:', shouldHideButtons);

        const addAnalysisBtn = document.getElementById('addAnalysisBtn');
        if (addAnalysisBtn) {
            const shouldHide = shouldHideButtons || hasAnalysis || !canEditCR;
            addAnalysisBtn.style.display = shouldHide ? 'none' : 'inline-block';
            console.log('[ensureButtonsVisible] Add Analysis button:', shouldHide ? 'HIDDEN' : 'VISIBLE');
        }
        
        const addResolutionBtn = document.querySelector('button[onclick="showResolutionForm()"]');
        if (addResolutionBtn) {
            const shouldHide = shouldHideButtons || hasResolution || !canEditCR;
            addResolutionBtn.style.display = shouldHide ? 'none' : 'inline-block';
            console.log('[ensureButtonsVisible] Add Resolution button:', shouldHide ? 'HIDDEN' : 'VISIBLE');
        }
    }

    // Load resolution statuses
    async function loadResolutionStatuses() {
        try {
            console.log('Loading resolution statuses...');
            const response = await fetch('/api/changerequest-resolution-status');
            console.log('Resolution status response status:', response.status);
            
            if (response.ok) {
                const statuses = await response.json();
                console.log('Resolution statuses received:', statuses);
                console.log('Number of statuses:', statuses ? statuses.length : 0);
                
                // Try to find the select element, with retry if not found immediately
                let select = document.getElementById('resolutionStatus');
                if (!select) {
                    // Wait a bit and try again (element might not be in DOM yet)
                    await new Promise(resolve => setTimeout(resolve, 100));
                    select = document.getElementById('resolutionStatus');
                }
                
                if (select) {
                    // Clear existing options except the first "Please select" option
                    while (select.children.length > 1) {
                        select.removeChild(select.lastChild);
                    }
                    
                    statuses.forEach(status => {
                        const option = document.createElement('option');
                        option.value = status.id || status.ID;
                        option.textContent = status.statusName || status.name || status.PrimaryName || status.Name;
                        select.appendChild(option);
                        console.log('Added status option:', option.value, option.textContent);
                    });
                } else {
                    console.warn('Resolution status select element not found - it may not be created yet or form is not visible');
                }
            } else {
                console.error('Failed to load resolution statuses, status:', response.status);
            }
        } catch (error) {
            console.error('Error loading resolution statuses:', error);
        }
    }
    // ========================================
    // WORKFLOW MANAGER INTEGRATION
    // ========================================

    let workflowManager = null;
    let bpmnViewer = null;
    /**
     * Initialize workflow manager for change request
     * CRITICAL: Checks if workflow already started (ONE WORKFLOW PER CR RULE)
     */
    async function initializeWorkflowManager(changeRequest) {
        try {
            // Check if user has permission to manage workflows
            const canManage = await canManageWorkflows(changeRequest.id);
            if (!canManage) {
                console.log('User does not have permission to manage workflows - skipping workflow initialization');
                // Hide workflow select dropdown if it's visible
                const workflowSelectItem = document.querySelector('#workflowSection .view-item');
                if (workflowSelectItem) {
                    workflowSelectItem.style.display = 'none';
                }
                // Hide workflow actions
                const workflowActions = document.getElementById('workflowActions');
                if (workflowActions) {
                    workflowActions.style.display = 'none';
                }
                return;
            }
            
            // Check if CR is completed - hide start workflow button but keep diagram visible
            const statusName = (changeRequest.statusName || changeRequest.StatusName || '').toLowerCase();
            const isCompleted = statusName && statusName.includes('completed');
            
            if (isCompleted) {
                console.log('CR is completed - hiding start workflow button but keeping diagram visible');
                // Hide workflow actions (start button) for completed CRs
                const workflowActions = document.getElementById('workflowActions');
                if (workflowActions) {
                    workflowActions.style.display = 'none';
                }
                // Also hide workflow select dropdown for completed CRs
                const workflowSelectItem = document.querySelector('#workflowSection .view-item');
                if (workflowSelectItem) {
                    workflowSelectItem.style.display = 'none';
                }
                // Still initialize workflow manager to show diagram (but don't allow starting new workflow)
                // Continue to show existing workflow diagram if available
            }

            // Create workflow manager instance
            workflowManager = new WorkflowStartManager();

            // Initialize with change request
            const initialized = await workflowManager.init(changeRequest);

            // ⚠️ CRITICAL: Check if workflow already started
            if (initialized === 'already_started') {
                console.log('Workflow already started - locking UI & showing diagram');
                lockWorkflowSection();
                
                // Ensure start workflow button is hidden for completed CRs
                if (isCompleted) {
                    const workflowActions = document.getElementById('workflowActions');
                    if (workflowActions) {
                        workflowActions.style.display = 'none';
                    }
                }
                
                // Show existing diagram if available (set by init)
                const state = workflowManager.getState();
                
                // Check workflow status and show appropriate message (only for auto CRs)
                const instance = state.workflowInstance;
                if (instance && (instance.status === 'Completed' || instance.endedAt)) {
                    showWorkflowCompletedStatus(changeRequest);
                } else {
                    showWorkflowStartedStatus(changeRequest);
                }
                
                // Check if workflow definition has been updated since instance started
                await checkAndShowWorkflowUpdateWarning(changeRequest.id);
                
                if (state.selectedWorkflow && state.selectedWorkflow.id) {
                    try {
                        // Load XML 
                        await workflowManager.loadBpmnXml(state.selectedWorkflow.id);
                        const updatedState = workflowManager.getState();
                        if (updatedState.bpmnXml) {
                            await displayBpmnDiagram(updatedState.bpmnXml);
                        }
                    } catch (e) {
                        console.error("Failed to load existing workflow diagram", e);
                    }
                }

                // Check for active task and show notification
                // Don't start polling here - let checkAndShowActiveTask decide based on workflow status
                // Only check if workflow is not already completed to avoid refresh loop
                if (!workflowCompleted) {
                    await checkAndShowActiveTask();
                } else {
                    stopTaskPolling(); // Ensure polling is stopped
                }
                return;
            }

            if (!initialized) {
                console.error('Failed to initialize workflow manager');
                return;
            }
            // Populate workflow dropdown
            const state = workflowManager.getState();
            const workflowSelect = document.getElementById('workflowSelect');

            if (state.workflows && state.workflows.length > 0) {
                // Clear existing options first
                workflowSelect.innerHTML = '<option value="">Please select</option>';
                state.workflows.forEach(workflow => {
                    const option = document.createElement('option');
                    // Ensure value is always a string to match dropdown value setting
                    option.value = String(workflow.id);
                    option.textContent = workflow.name || workflow.primaryName || `Workflow ${workflow.id}`;
                    workflowSelect.appendChild(option);
                });
                console.log('[ChangeRequestView] Populated workflow dropdown with', state.workflows.length, 'workflows');
                console.log('[ChangeRequestView] Workflow options:', Array.from(workflowSelect.options).map(opt => ({ 
                    value: opt.value, 
                    text: opt.textContent 
                })));
                
                // If there's only one workflow, don't auto-select it - let the processDefinitionId or manual selection handle it
                // But if processDefinitionId is set, it will be selected below
            }
            
            // Setup event listeners BEFORE pre-selecting (so change event will be handled)
            setupWorkflowEventListeners();
            
            // Check if dropdown already has a value (from previous selection or page state)
            // This can happen if the CR was created before processDefinitionId was stored
            // Also check selectedIndex in case the value is set but not detected
            const currentValue = workflowSelect ? workflowSelect.value : null;
            const selectedIndex = workflowSelect ? workflowSelect.selectedIndex : -1;
            const hasExistingValue = currentValue && currentValue !== '' && currentValue !== '0' && currentValue !== 'Please select' && selectedIndex > 0;
            console.log('[ChangeRequestView] Dropdown state:', {
                currentValue: currentValue,
                selectedIndex: selectedIndex,
                hasExistingValue: hasExistingValue,
                optionsCount: workflowSelect ? workflowSelect.options.length : 0
            });
            
            // Pre-select default workflow if set
            if (changeRequest.processDefinitionId) {
                // Convert to number for comparison (workflow.id might be number or string)
                const processDefId = typeof changeRequest.processDefinitionId === 'string' 
                    ? parseInt(changeRequest.processDefinitionId) 
                    : changeRequest.processDefinitionId;
                const defaultWorkflow = state.workflows.find(w => {
                    const workflowId = typeof w.id === 'string' ? parseInt(w.id) : w.id;
                    return workflowId === processDefId;
                });
                
                if (defaultWorkflow && workflowSelect) {
                    // Use the actual workflow.id value (might be string or number)
                    // Convert to string to ensure it matches option value
                    const workflowValue = String(defaultWorkflow.id);
                    workflowSelect.value = workflowValue;
                    console.log('[ChangeRequestView] Pre-selected default workflow in dropdown:', {
                        processDefinitionId: changeRequest.processDefinitionId,
                        processDefinitionIdType: typeof changeRequest.processDefinitionId,
                        selectedValue: workflowValue,
                        workflowId: defaultWorkflow.id,
                        workflowIdType: typeof defaultWorkflow.id,
                        workflowName: defaultWorkflow.name || defaultWorkflow.primaryName,
                        workflowPrimaryName: defaultWorkflow.primaryName,
                        dropdownValue: workflowSelect.value,
                        dropdownSelectedIndex: workflowSelect.selectedIndex,
                        dropdownSelectedText: workflowSelect.options[workflowSelect.selectedIndex]?.textContent,
                        allOptions: Array.from(workflowSelect.options).map(opt => ({
                            value: opt.value,
                            text: opt.textContent,
                            selected: opt.selected
                        }))
                    });
                    
                    // Double-check: Verify the value was actually set
                    setTimeout(() => {
                        const actualValue = workflowSelect.value;
                        const actualSelectedText = workflowSelect.options[workflowSelect.selectedIndex]?.textContent;
                        console.log('[ChangeRequestView] Dropdown verification after setting:', {
                            expectedValue: workflowValue,
                            actualValue: actualValue,
                            expectedName: defaultWorkflow.name || defaultWorkflow.primaryName,
                            actualSelectedText: actualSelectedText,
                            matches: actualValue === workflowValue
                        });
                    }, 100);
                    
                    // Manually trigger the full workflow selection process
                    // This ensures diagram loads and start button appears even if change event doesn't fire
                    setTimeout(async () => {
                        try {
                            console.log('[ChangeRequestView] Manually triggering workflow selection for pre-selected workflow...');
                            const btn = document.getElementById('startWorkflowBtn');
                            if (btn) {
                                btn.disabled = true;
                                btn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Loading...';
                            }
                            showWorkflowLoading();
                            
                            // Select workflow and validate
                            await workflowManager.selectWorkflow(processDefId);
                            
                            // Get updated state
                            const state = workflowManager.getState();
                            
                            // Display BPMN diagram
                            if (state.bpmnXml) {
                                await displayBpmnDiagram(state.bpmnXml);
                            }
                            
                            // Show validation result
                            if (state.validationResult) {
                                if (state.validationResult.valid) {
                                    // Validation passed - show start button immediately (if not completed)
                                    showWorkflowActions(changeRequest);
                                    hideValidationError();
                                    if (btn) {
                                        btn.disabled = false;
                                        btn.innerHTML = '<i class="fas fa-play"></i> Start Workflow';
                                    }
                                    console.log('[ChangeRequestView] Pre-selected workflow loaded successfully, start button shown');
                                } else {
                                    // Validation failed - show error
                                    const errorMsg = workflowManager.getValidationErrorMessage();
                                    showValidationError(errorMsg);
                                    hideWorkflowActions();
                                    if (btn) {
                                        btn.disabled = true;
                                        btn.innerHTML = '<i class="fas fa-play"></i> Start Workflow';
                                    }
                                    console.log('[ChangeRequestView] Pre-selected workflow validation failed:', errorMsg);
                                }
                            }
                        } catch (error) {
                            console.error('[ChangeRequestView] Error loading pre-selected workflow:', error);
                            showValidationError('Error loading workflow: ' + error.message);
                            hideWorkflowActions();
                        }
                    }, 100); // Small delay to ensure event listeners are ready
                } else {
                    console.warn('[ChangeRequestView] Default workflow not found:', {
                        processDefinitionId: changeRequest.processDefinitionId,
                        availableWorkflows: state.workflows.map(w => ({ id: w.id, name: w.name || w.primaryName }))
                    });
                }
            } else if (hasExistingValue) {
                // No processDefinitionId, but dropdown has a value - trigger selection for existing value
                // This handles cases where CR was created before processDefinitionId was stored
                console.log('[ChangeRequestView] No processDefinitionId, but dropdown has value:', currentValue, '- triggering workflow selection');
                setTimeout(async () => {
                    try {
                        const processDefId = parseInt(currentValue);
                        if (!isNaN(processDefId) && processDefId > 0 && workflowManager) {
                            console.log('[ChangeRequestView] Triggering workflow selection for existing dropdown value:', processDefId);
                            const btn = document.getElementById('startWorkflowBtn');
                            if (btn) {
                                btn.disabled = true;
                                btn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Loading...';
                            }
                            showWorkflowLoading();
                            
                            // Select workflow and validate
                            await workflowManager.selectWorkflow(processDefId);
                            
                            // Get updated state
                            const state = workflowManager.getState();
                            console.log('[ChangeRequestView] Workflow state after selection:', {
                                hasBpmnXml: !!state.bpmnXml,
                                hasValidationResult: !!state.validationResult,
                                isValid: state.validationResult?.valid
                            });
                            
                            // Display BPMN diagram
                            if (state.bpmnXml) {
                                console.log('[ChangeRequestView] Displaying BPMN diagram for existing value...');
                                await displayBpmnDiagram(state.bpmnXml);
                                console.log('[ChangeRequestView] BPMN diagram displayed');
                            } else {
                                console.warn('[ChangeRequestView] No BPMN XML after selecting existing workflow value');
                            }
                            
                            // Show validation result
                            if (state.validationResult) {
                                if (state.validationResult.valid) {
                                    // Validation passed - show start button immediately (if not completed)
                                    showWorkflowActions(changeRequest);
                                    hideValidationError();
                                    if (btn) {
                                        btn.disabled = false;
                                        btn.innerHTML = '<i class="fas fa-play"></i> Start Workflow';
                                    }
                                    console.log('[ChangeRequestView] Existing workflow value loaded successfully, start button shown');
                                } else {
                                    // Validation failed - show error
                                    const errorMsg = workflowManager.getValidationErrorMessage();
                                    showValidationError(errorMsg);
                                    hideWorkflowActions();
                                    console.log('[ChangeRequestView] Existing workflow validation failed:', errorMsg);
                                }
                            } else {
                                console.warn('[ChangeRequestView] No validation result after selecting existing workflow');
                            }
                        } else {
                            console.warn('[ChangeRequestView] Invalid processDefId or workflowManager not ready:', {
                                processDefId,
                                isNaN: isNaN(processDefId),
                                hasWorkflowManager: !!workflowManager
                            });
                        }
                    } catch (error) {
                        console.error('[ChangeRequestView] Error loading existing workflow value:', error);
                        showValidationError('Error loading workflow: ' + error.message);
                        hideWorkflowActions();
                    }
                }, 200); // Slightly longer delay to ensure everything is ready
            } else {
                console.log('[ChangeRequestView] No processDefinitionId set for change request:', changeRequest.id);
                console.log('[ChangeRequestView] Dropdown value:', currentValue, 'hasExistingValue:', hasExistingValue);
                
                // If there's only one workflow available and no processDefinitionId, auto-select it
                // This handles cases where CR was created before processDefinitionId was stored
                // ⚠️ CRITICAL: Don't auto-select if processDefinitionId is set - use that instead
                if (state.workflows && state.workflows.length === 1 && !hasExistingValue && !changeRequest.processDefinitionId) {
                    const singleWorkflow = state.workflows[0];
                    console.log('[ChangeRequestView] Only one workflow available and no processDefinitionId, auto-selecting:', singleWorkflow.id);
                    
                    if (workflowSelect && singleWorkflow) {
                        workflowSelect.value = singleWorkflow.id;
                        
                        // Manually trigger the workflow selection process
                        setTimeout(async () => {
                            try {
                                const processDefId = typeof singleWorkflow.id === 'string' 
                                    ? parseInt(singleWorkflow.id) 
                                    : singleWorkflow.id;
                                    
                                console.log('[ChangeRequestView] Auto-selecting single workflow:', processDefId);
                                const btn = document.getElementById('startWorkflowBtn');
                                if (btn) {
                                    btn.disabled = true;
                                    btn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Loading...';
                                }
                                showWorkflowLoading();
                                
                                // Select workflow and validate
                                await workflowManager.selectWorkflow(processDefId);
                                
                                // Get updated state
                                const state = workflowManager.getState();
                                
                                // Display BPMN diagram
                                if (state.bpmnXml) {
                                    await displayBpmnDiagram(state.bpmnXml);
                                }
                                
                                // Show validation result
                                if (state.validationResult) {
                                    if (state.validationResult.valid) {
                                        // Validation passed - show start button immediately (if not completed)
                                        showWorkflowActions(changeRequest);
                                        hideValidationError();
                                        if (btn) {
                                            btn.disabled = false;
                                            btn.innerHTML = '<i class="fas fa-play"></i> Start Workflow';
                                        }
                                        console.log('[ChangeRequestView] Single workflow auto-selected and loaded successfully');
                                    } else {
                                        // Validation failed - show error
                                        const errorMsg = workflowManager.getValidationErrorMessage();
                                        showValidationError(errorMsg);
                                        hideWorkflowActions();
                                    }
                                }
                            } catch (error) {
                                console.error('[ChangeRequestView] Error auto-selecting single workflow:', error);
                                showValidationError('Error loading workflow: ' + error.message);
                                hideWorkflowActions();
                            }
                        }, 200);
                    }
                }
            }
            
            // Also add a direct click handler as fallback for manual selection
            if (workflowSelect) {
                workflowSelect.addEventListener('click', () => {
                    console.log('[ChangeRequestView] Workflow dropdown clicked');
                });
                
                // Add input event as additional fallback
                workflowSelect.addEventListener('input', async (e) => {
                    const value = e.target.value;
                    if (value && value !== '') {
                        console.log('[ChangeRequestView] Workflow input event triggered, value:', value);
                        // Manually trigger change event if it didn't fire
                        setTimeout(() => {
                            if (workflowSelect.value === value) {
                                workflowSelect.dispatchEvent(new Event('change', { bubbles: true }));
                            }
                        }, 100);
                    }
                });
            }
        } catch (error) {
            console.error('Error initializing workflow manager:', error);
        }
    }
    /**
     * Lock workflow section (workflow already started)
     */
    function lockWorkflowSection() {
        const workflowSelect = document.getElementById('workflowSelect');
        const startWorkflowBtn = document.getElementById('startWorkflowBtn');

        if (workflowSelect) {
            workflowSelect.disabled = true;
            workflowSelect.style.display = 'none'; // User requested to remove the list

            // Also hide the label container if possible
            const parentItem = workflowSelect.closest('.view-item');
            if (parentItem) {
                parentItem.style.display = 'none';
            }
        }

        if (startWorkflowBtn) {
            // Disable button instead of hiding it (user requirement)
            startWorkflowBtn.disabled = true;
            startWorkflowBtn.style.opacity = '0.6';
            startWorkflowBtn.style.cursor = 'not-allowed';
            startWorkflowBtn.title = 'Workflow is already running';
        }
    }

    /**
     * Re-enable Start Workflow button when workflow reaches End
     */
    function enableStartWorkflowButton() {
        const startWorkflowBtn = document.getElementById('startWorkflowBtn');
        if (startWorkflowBtn) {
            startWorkflowBtn.disabled = false;
            startWorkflowBtn.style.opacity = '1';
            startWorkflowBtn.style.cursor = 'pointer';
            startWorkflowBtn.title = '';
            console.log('Start Workflow button re-enabled (workflow reached End)');
        }
    }

    // Store the event handler reference to prevent duplicates
    let workflowSelectHandler = null;
    
    /**
     * Setup event listeners for workflow UI
     */
    function setupWorkflowEventListeners() {
        const workflowSelect = document.getElementById('workflowSelect');
        const startWorkflowBtn = document.getElementById('startWorkflowBtn');
        
        // Remove existing event listener if it exists
        if (workflowSelect && workflowSelectHandler) {
            workflowSelect.removeEventListener('change', workflowSelectHandler);
        }
        
        // Workflow selection change
        if (workflowSelect) {
            workflowSelectHandler = async (e) => {
                console.log('[ChangeRequestView] Workflow dropdown changed, value:', e.target.value);
                const processDefId = parseInt(e.target.value);
                console.log('[ChangeRequestView] Processing workflow selection, processDefId:', processDefId);

                if (!processDefId || isNaN(processDefId)) {
                    console.log('[ChangeRequestView] No valid workflow selected, clearing UI');
                    // Clear diagram and hide actions
                    clearWorkflowDiagram();
                    hideWorkflowActions();
                    hideValidationError();
                    return;
                }
                
                if (!workflowManager) {
                    console.error('[ChangeRequestView] WorkflowManager not initialized!');
                    alert('Workflow manager not initialized. Please refresh the page.');
                    return;
                }
                
                try {
                    console.log('[ChangeRequestView] Starting workflow selection process...');
                    // Show loading state on button
                    const btn = document.getElementById('startWorkflowBtn');
                    if (btn) {
                        btn.disabled = true;
                        btn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Validating...';
                    }
                    showWorkflowLoading();
                    
                    // Select workflow and validate
                    console.log('[ChangeRequestView] Calling workflowManager.selectWorkflow(', processDefId, ')');
                    await workflowManager.selectWorkflow(processDefId);
                    console.log('[ChangeRequestView] Workflow selection completed');
                    
                    // Get updated state
                    const state = workflowManager.getState();
                    console.log('[ChangeRequestView] Workflow state after selection:', {
                        hasBpmnXml: !!state.bpmnXml,
                        hasValidationResult: !!state.validationResult,
                        isValid: state.validationResult?.valid
                    });
                    
                    // Display BPMN diagram
                    if (state.bpmnXml) {
                        console.log('[ChangeRequestView] Displaying BPMN diagram...');
                        await displayBpmnDiagram(state.bpmnXml);
                        console.log('[ChangeRequestView] BPMN diagram displayed');
                    } else {
                        console.warn('[ChangeRequestView] No BPMN XML in state after selection');
                    }
                    
                    // Show validation result
                    if (state.validationResult) {
                        console.log('[ChangeRequestView] Validation result:', state.validationResult.valid);
                        if (state.validationResult.valid) {
                            // Validation passed - show start button immediately (if not completed)
                            showWorkflowActions(currentChangeRequest);
                            hideValidationError();
                            // Ensure button is enabled and shows correct text (if not completed)
                            if (startWorkflowBtn) {
                                const statusName = (currentChangeRequest?.statusName || currentChangeRequest?.StatusName || '').toLowerCase();
                                const isCompleted = statusName && statusName.includes('completed');
                                if (!isCompleted) {
                                    startWorkflowBtn.disabled = false;
                                    startWorkflowBtn.innerHTML = '<i class="fas fa-play"></i> Start Workflow';
                                } else {
                                    // Hide button if completed
                                    const workflowActions = document.getElementById('workflowActions');
                                    if (workflowActions) {
                                        workflowActions.style.display = 'none';
                                    }
                                }
                            }
                            console.log('[ChangeRequestView] Workflow validated successfully, start button shown');
                        } else {
                            // Validation failed - show error
                            const errorMsg = workflowManager.getValidationErrorMessage();
                            showValidationError(errorMsg);
                            hideWorkflowActions();
                            if (startWorkflowBtn) {
                                startWorkflowBtn.disabled = true;
                                startWorkflowBtn.innerHTML = '<i class="fas fa-play"></i> Start Workflow';
                            }
                            console.log('[ChangeRequestView] Workflow validation failed:', errorMsg);
                        }
                    } else {
                        // No validation result - this shouldn't happen, but handle it
                        console.warn('[ChangeRequestView] No validation result after selecting workflow');
                        hideWorkflowActions();
                        if (startWorkflowBtn) {
                            startWorkflowBtn.disabled = true;
                            startWorkflowBtn.innerHTML = '<i class="fas fa-play"></i> Start Workflow';
                        }
                    }
                } catch (error) {
                    console.error('[ChangeRequestView] Error selecting workflow:', error);
                    showValidationError('Error loading workflow: ' + error.message);
                    hideWorkflowActions();
                    const btn = document.getElementById('startWorkflowBtn');
                    if (btn) {
                        btn.disabled = true;
                        btn.innerHTML = '<i class="fas fa-play"></i> Start Workflow';
                    }
                }
            };
            
            // Attach the event listener
            workflowSelect.addEventListener('change', workflowSelectHandler);
            console.log('[ChangeRequestView] Workflow change event listener attached');
        } else {
            console.warn('[ChangeRequestView] workflowSelect element not found when setting up event listeners');
        }
        // Start workflow button
        if (startWorkflowBtn) {
            startWorkflowBtn.addEventListener('click', async () => {
                // Check if user has permission to manage workflows
                if (currentChangeRequestId) {
                    const canManage = await canManageWorkflows(currentChangeRequestId);
                    if (!canManage) {
                        alert('You do not have permission to start workflows. Only admins and stakeholders can start workflows.');
                        return;
                    }
                }

                if (!workflowManager.canStartWorkflow()) {
                    alert('Workflow cannot be started. Please check validation.');
                    return;
                }
                
                // ⚠️ CRITICAL: Only show IMPORTANT NOTICE for auto CRs (mandatory_workflow = true)
                // Manual CRs should NOT show this message - they work like other facets without locking
                // Ensure we have the latest CR data with mandatory_workflow field
                let crData = currentChangeRequest;
                if (!crData || crData.mandatoryWorkflow === undefined) {
                    try {
                        const crResponse = await fetch(`/api/changerequests/${currentChangeRequestId}`, {
                            credentials: 'include'
                        });
                        if (crResponse.ok) {
                            crData = await crResponse.json();
                        }
                    } catch (e) {
                        console.warn('Could not fetch CR data for workflow start check:', e);
                    }
                }
                const mandatoryWorkflow = crData?.mandatoryWorkflow;
                const isAutoCR = mandatoryWorkflow === true || mandatoryWorkflow === 1 || mandatoryWorkflow === 'true' || mandatoryWorkflow === '1';
                
                // Only show confirmation message for auto CRs
                if (isAutoCR) {
                    // Get object reference from CR to show in warning message
                    const objectReference = crData?.reference || currentChangeRequest?.reference || 'this object';
                    
                    // Extract current facet from reference (e.g., "Data Set 86" -> "Data Set", "Glossary 20" -> "Glossary")
                    let currentFacet = 'this facet';
                    const reference = crData?.reference || currentChangeRequest?.reference;
                    if (reference) {
                        const refParts = reference.split(/\s+/);
                        if (refParts.length >= 2) {
                            // Handle multi-word facets like "Data Set", "System Interface"
                            const firstTwoWords = refParts[0] + ' ' + refParts[1];
                            if (firstTwoWords === 'Data Set' || firstTwoWords === 'System Interface' || 
                                firstTwoWords === 'Business Area' || firstTwoWords === 'Legal Entity') {
                                currentFacet = firstTwoWords;
                            } else {
                                currentFacet = refParts[0];
                            }
                        } else if (refParts.length === 1) {
                            currentFacet = refParts[0];
                        }
                    }
                    
                    // Show detailed confirmation message for Auto CR (only mention current facet)
                    const confirmationMessage = 
                        `⚠️ IMPORTANT NOTICE:\n\n` +
                        `Starting this workflow will lock editing on ${objectReference} in the ${currentFacet} facet.\n\n` +
                        `Once the workflow starts (status becomes "Running"), you will NOT be able to edit ${objectReference} until the Change Request status becomes:\n` +
                        `• Completed (changes will be accepted)\n` +
                        `• OR Cancelled (changes will be rejected)\n\n` +
                        `Note: You can still edit ${objectReference} while the CR status is "Pending Start" (before starting the workflow).\n\n` +
                        `Do you want to proceed with starting the workflow?`;
                    
                    if (!confirm(confirmationMessage)) {
                        return;
                    }
                } else {
                    // Manual CR - just confirm with simple message (no locking warning)
                    if (!confirm('Do you want to proceed with starting the workflow?')) {
                        return;
                    }
                }
                try {
                    // Disable button
                    startWorkflowBtn.disabled = true;
                    startWorkflowBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Starting...';
                    // Start workflow
                    await workflowManager.startWorkflow();
                    // Show success state (only for auto CRs)
                    // Get changeRequest from current context
                    const currentCR = await fetch(`/api/changerequests/${currentChangeRequestId}`, {
                        credentials: 'include'
                    }).then(res => res.ok ? res.json() : null).catch(() => null);
                    showWorkflowStartedStatus(currentCR);
                    hideWorkflowActions();

                    // Lock dropdown (ONE WORKFLOW PER CR RULE)
                    lockWorkflowSection();

                    // Refresh CR Data to show "Running" status and other updates
                    console.log('Refreshing Change Request data...');
                    await loadChangeRequestData();

                    // Wait a bit for database transaction to commit and tasks to be created
                    console.log('⏳ Waiting 500ms for database transaction to commit...');
                    await new Promise(resolve => setTimeout(resolve, 500));

                    // Check for active task after workflow start (with retry)
                    console.log('🔄 Workflow started, checking for active task with retry...');
                    await checkAndShowActiveTaskWithRetry();
                } catch (error) {
                    console.error('Error starting workflow:', error);
                    alert('Failed to start workflow: ' + error.message);

                    // Re-enable button
                    startWorkflowBtn.disabled = false;
                    startWorkflowBtn.innerHTML = '<i class="fas fa-play"></i> Start Workflow';
                }
            });
        }
    }
    /**
     * Enable pan/drag functionality for BPMN viewer
     */
    function enablePanDrag(viewer, canvasElement) {
        if (!viewer || !canvasElement) return;

        const canvas = viewer.get('canvas');
        let isDragging = false;
        let startX = 0;
        let startY = 0;
        let lastX = 0;
        let lastY = 0;

        // Track if space key is pressed for pan mode
        let spacePressed = false;
        document.addEventListener('keydown', (e) => {
            if (e.code === 'Space' && !e.target.matches('input, textarea, select')) {
                spacePressed = true;
                canvasElement.style.cursor = 'grab';
            }
        });
        document.addEventListener('keyup', (e) => {
            if (e.code === 'Space') {
                spacePressed = false;
                if (!isDragging) {
                    canvasElement.style.cursor = 'default';
                }
            }
        });

        // Mouse events for pan/drag
        canvasElement.addEventListener('mousedown', (e) => {
            // Start drag on:
            // 1. Middle mouse button (button === 1)
            // 2. Space + Left mouse button (button === 0)
            // 3. Left mouse button on empty canvas (not on an element)
            const target = e.target;
            const isClickableElement = target.closest('.djs-element') && 
                                     !target.closest('.djs-element').classList.contains('djs-shape') &&
                                     !target.closest('.djs-connection');
            
            if (e.button === 1 || (spacePressed && e.button === 0) || (!isClickableElement && e.button === 0)) {
                isDragging = true;
                startX = e.clientX;
                startY = e.clientY;
                lastX = e.clientX;
                lastY = e.clientY;
                canvasElement.style.cursor = 'grabbing';
                e.preventDefault();
                e.stopPropagation();
            }
        });

        canvasElement.addEventListener('mousemove', (e) => {
            if (isDragging) {
                const deltaX = e.clientX - lastX;
                const deltaY = e.clientY - lastY;
                
                canvas.scroll({
                    dx: deltaX,
                    dy: deltaY
                });
                
                lastX = e.clientX;
                lastY = e.clientY;
                e.preventDefault();
                e.stopPropagation();
            } else if (spacePressed) {
                canvasElement.style.cursor = 'grab';
            }
        });

        canvasElement.addEventListener('mouseup', (e) => {
            if (isDragging) {
                isDragging = false;
                canvasElement.style.cursor = spacePressed ? 'grab' : 'default';
                e.preventDefault();
                e.stopPropagation();
            }
        });

        canvasElement.addEventListener('mouseleave', (e) => {
            if (isDragging) {
                isDragging = false;
                canvasElement.style.cursor = spacePressed ? 'grab' : 'default';
            }
        });

        // Prevent context menu on middle mouse button
        canvasElement.addEventListener('contextmenu', (e) => {
            if (e.button === 1) {
                e.preventDefault();
            }
        });

        // Touch events for mobile/tablet
        let touchStartX = 0;
        let touchStartY = 0;
        let touchLastX = 0;
        let touchLastY = 0;

        canvasElement.addEventListener('touchstart', (e) => {
            if (e.touches.length === 1) {
                touchStartX = e.touches[0].clientX;
                touchStartY = e.touches[0].clientY;
                touchLastX = touchStartX;
                touchLastY = touchStartY;
                isDragging = true;
            }
        });

        canvasElement.addEventListener('touchmove', (e) => {
            if (isDragging && e.touches.length === 1) {
                const deltaX = e.touches[0].clientX - touchLastX;
                const deltaY = e.touches[0].clientY - touchLastY;
                
                canvas.scroll({
                    dx: deltaX,
                    dy: deltaY
                });
                
                touchLastX = e.touches[0].clientX;
                touchLastY = e.touches[0].clientY;
                e.preventDefault();
            }
        });

        canvasElement.addEventListener('touchend', (e) => {
            isDragging = false;
        });

        // Set initial cursor - will change to grab when space is pressed
        canvasElement.style.cursor = 'default';
    }

    /**
     * Display BPMN diagram using BPMN.js viewer
     */
    async function displayBpmnDiagram(bpmnXml) {
        const container = document.getElementById('workflowDiagramContainer');
        const canvas = document.getElementById('workflowDiagramCanvas');
        const placeholder = document.getElementById('workflowDiagramPlaceholder');
        if (!canvas || !container) return;
        try {
            // Hide placeholder, show canvas
            if (placeholder) placeholder.style.display = 'none';
            canvas.style.display = 'block';

            // Validate BPMN XML
            if (!bpmnXml || typeof bpmnXml !== 'string' || bpmnXml.trim().length === 0) {
                console.error('Invalid or empty BPMN XML');
                throw new Error('Invalid BPMN XML data');
            }

            // Ensure container has proper height
            if (container) {
                container.style.height = '600px';
            }

            // Initialize BPMN viewer if not already initialized
            if (!bpmnViewer) {
                // Check if BpmnJS is available
                if (typeof BpmnJS === 'undefined') {
                    console.error('BpmnJS library not loaded');
                    throw new Error('BPMN viewer library not available');
                }
                bpmnViewer = new BpmnJS({
                    container: canvas,
                    keyboard: {
                        bindTo: document
                    }
                });

                // Add click event listener to show properties
                const eventBus = bpmnViewer.get('eventBus');
                eventBus.on('element.click', (e) => {
                    if (e.element && e.element.id) {
                        showElementProperties(e.element);
                    }
                });

                // Enable pan/drag functionality manually
                enablePanDrag(bpmnViewer, canvas);
            }
            // Import BPMN XML
            await bpmnViewer.importXML(bpmnXml);

            // Wait for canvas to be fully rendered before zooming
            await new Promise(resolve => {
                requestAnimationFrame(() => {
                    requestAnimationFrame(resolve);
                });
            });

            // Additional wait to ensure diagram is fully loaded
            await new Promise(resolve => setTimeout(resolve, 300));

            // Ensure canvas is focused to enable pan/drag
            const canvasElement = document.getElementById('workflowDiagramCanvas');
            if (canvasElement && bpmnViewer) {
                // Focus the canvas to enable pan/drag functionality
                canvasElement.focus();
                
                // Also focus the SVG element inside if available
                const svgElement = canvasElement.querySelector('svg');
                if (svgElement) {
                    svgElement.style.cursor = 'grab';
                    svgElement.setAttribute('tabindex', '0');
                }

                // Add manual pan/drag support
                // Enable pan by clicking and dragging on empty canvas area
                const bpmnCanvas = bpmnViewer.get('canvas');
                const elementRegistry = bpmnViewer.get('elementRegistry');
                let isDragging = false;
                let startX = 0;
                let startY = 0;
                let lastScrollX = 0;
                let lastScrollY = 0;

                const handleMouseDown = (e) => {
                    // Check if clicking on empty canvas area (not on a BPMN element)
                    const target = e.target;
                    const isElement = target && (
                        target.closest('.djs-element') || 
                        target.closest('.djs-shape') ||
                        target.closest('.djs-connection')
                    );
                    
                    // Only start drag if clicking on empty canvas (not on an element)
                    if (!isElement && target && (target.classList.contains('viewport') || target.closest('.viewport'))) {
                        isDragging = true;
                        startX = e.clientX;
                        startY = e.clientY;
                        canvasElement.style.cursor = 'grabbing';
                        e.preventDefault();
                        e.stopPropagation();
                    }
                };

                const handleMouseMove = (e) => {
                    if (isDragging) {
                        const dx = e.clientX - startX;
                        const dy = e.clientY - startY;
                        
                        // Use canvas.scroll to move the diagram (relative to last position)
                        const deltaX = dx - lastScrollX;
                        const deltaY = dy - lastScrollY;
                        
                        if (Math.abs(deltaX) > 0 || Math.abs(deltaY) > 0) {
                            bpmnCanvas.scroll({
                                dx: deltaX,
                                dy: deltaY
                            });
                            
                            lastScrollX = dx;
                            lastScrollY = dy;
                        }
                        
                        e.preventDefault();
                    }
                };

                const handleMouseUp = () => {
                    if (isDragging) {
                        isDragging = false;
                        lastScrollX = 0;
                        lastScrollY = 0;
                        canvasElement.style.cursor = 'grab';
                    }
                };

                // Add event listeners to the SVG viewport
                if (svgElement) {
                    const viewport = svgElement.querySelector('.viewport');
                    if (viewport) {
                        viewport.addEventListener('mousedown', handleMouseDown);
                        document.addEventListener('mousemove', handleMouseMove);
                        document.addEventListener('mouseup', handleMouseUp);
                    }
                }
            }

            // Highlight active tasks after diagram is loaded
            await highlightActiveTasks();

            // Zoom to fit with validation and retry mechanism
            const bpmnCanvas = bpmnViewer.get('canvas');
            let viewboxValid = false;
            let retryCount = 0;
            const maxRetries = 3;

            while (!viewboxValid && retryCount < maxRetries) {
                try {
                    // Get viewbox to validate dimensions before zooming
                    const viewbox = bpmnCanvas.viewbox();
                    if (viewbox &&
                        isFinite(viewbox.x) && isFinite(viewbox.y) &&
                        isFinite(viewbox.width) && isFinite(viewbox.height) &&
                        viewbox.width > 0 && viewbox.height > 0) {
                        bpmnCanvas.zoom('fit-viewport');
                        viewboxValid = true;
                    } else {
                        retryCount++;
                        if (retryCount < maxRetries) {
                            // Wait a bit longer and retry
                            await new Promise(resolve => setTimeout(resolve, 500));
                        } else {
                            console.warn('Invalid viewbox dimensions after retries, refreshing page...');
                            // Refresh page to reload properly
                            window.location.reload();
                            return;
                        }
                    }
                } catch (zoomError) {
                    retryCount++;
                    if (retryCount < maxRetries) {
                        await new Promise(resolve => setTimeout(resolve, 500));
                    } else {
                        console.warn('Error during zoom operation after retries, refreshing page...', zoomError);
                        // Refresh page to reload properly
                        window.location.reload();
                        return;
                    }
                }
            }
        } catch (error) {
            console.error('Error displaying BPMN diagram:', error);
            if (placeholder) {
                placeholder.style.display = 'flex';
                placeholder.innerHTML = `
                        <div style="text-align: center; color: #dc2626;">
                            <i class="fas fa-exclamation-triangle" style="font-size: 3rem; margin-bottom: 1rem;"></i>
                            <p>Error loading diagram</p>
                        </div>
                    `;
            }
            canvas.style.display = 'none';
        }
    }

    /**
     * Highlight active tasks in the BPMN diagram
     */
    async function highlightActiveTasks() {
        if (!bpmnViewer || !currentChangeRequestId) return;

        try {
            // Get workflow instance to find active tasks
            const response = await fetch(`/api/workflow_instances/by-cr/${currentChangeRequestId}`, {
                method: 'GET',
                credentials: 'include'
            }).catch(err => {
                // Silently handle network errors - don't pollute console
                return { ok: false, status: 0 };
            });

            if (!response.ok) {
                // 404 is expected when workflow hasn't started yet - don't log as error
                if (response.status === 404) {
                    // Silently return - workflow not started yet
                    return;
                }
                // For other errors, log but don't throw
                console.debug('No workflow instance found for highlighting (status:', response.status, ')');
                return;
            }

            const workflowData = await response.json();
            const instance = workflowData.instance;

            if (!instance || !instance.id) {
                return;
            }

            // Get all tasks for this instance
            const tasksResponse = await fetch(`/api/workflow_instances/${instance.id}/tasks`, {
                method: 'GET',
                credentials: 'include'
            });

            if (!tasksResponse.ok) {
                return;
            }

            const tasksData = await tasksResponse.json();
            const tasks = tasksData.tasks || [];

            // Filter to get only pending/in-progress tasks
            const activeTasks = tasks.filter(task =>
                task.status === 'Pending' || task.status === 'InProgress'
            );

            const canvas = bpmnViewer.get('canvas');
            const elementRegistry = bpmnViewer.get('elementRegistry');

            // Clear previous highlights
            clearTaskHighlights();

            // Highlight current BPMN node (the node where workflow is currently at)
            // This ensures the current task appears in green when workflow starts
            if (instance.currentBpmnNodeId) {
                const currentElement = elementRegistry.get(instance.currentBpmnNodeId);
                if (currentElement) {
                    // Add marker for current node (green highlight)
                    canvas.addMarker(instance.currentBpmnNodeId, 'highlight-active');
                    console.log(`Highlighted current workflow node: ${instance.currentBpmnNodeId}`);
                } else {
                    console.warn(`Element not found for current node ID: ${instance.currentBpmnNodeId}`);
                }
            }

            // Highlight active tasks
            activeTasks.forEach(task => {
                const bpmnNodeId = task.bpmnNodeId || task.nodeId;
                if (bpmnNodeId) {
                    const element = elementRegistry.get(bpmnNodeId);
                    if (element) {
                        // Add marker for active task (green highlight)
                        canvas.addMarker(bpmnNodeId, 'highlight-active');
                        console.log(`Highlighted active task: ${bpmnNodeId}`);
                    } else {
                        console.warn(`Element not found for node ID: ${bpmnNodeId}`);
                    }
                }
            });

            // Also highlight completed tasks with different color
            const completedTasks = tasks.filter(task => task.status === 'Completed');
            completedTasks.forEach(task => {
                const bpmnNodeId = task.bpmnNodeId || task.nodeId;
                if (bpmnNodeId) {
                    const element = elementRegistry.get(bpmnNodeId);
                    if (element) {
                        // Add marker for completed task (gray/blue)
                        canvas.addMarker(bpmnNodeId, 'highlight-completed');
                    }
                }
            });

            // If workflow is completed, highlight only the end event that was reached
            // Check if workflow is truly completed:
            // 1. Status is 'Completed', OR
            // 2. Status is 'Disabled' AND endedAt is set (not null/undefined), OR
            // 3. No active tasks (workflow reached end event)
            const hasActiveTasks = tasks.some(task => 
                task.status === 'Pending' || task.status === 'InProgress'
            );
            const isWorkflowCompleted = instance.status === 'Completed' || 
                                       (instance.status === 'Disabled' && instance.endedAt != null) ||
                                       (!hasActiveTasks && tasks.length > 0); // No active tasks but has tasks = reached end
            
            if (isWorkflowCompleted) {
                // Find the last completed task to determine which path was taken
                const lastCompletedTask = completedTasks
                    .filter(task => task.completedAt)
                    .sort((a, b) => {
                        const dateA = new Date(a.completedAt);
                        const dateB = new Date(b.completedAt);
                        return dateB - dateA; // Sort descending (newest first)
                    })[0];
                
                if (lastCompletedTask) {
                    const lastTaskNodeId = lastCompletedTask.bpmnNodeId || lastCompletedTask.nodeId;
                    console.log(`🔍 [HIGHLIGHT] Last completed task: ${lastTaskNodeId} (${lastCompletedTask.taskName || lastCompletedTask.name})`);
                    
                    // Find the end event that follows this task by following outgoing flows
                    const lastTaskElement = elementRegistry.get(lastTaskNodeId);
                    if (lastTaskElement) {
                        // Function to recursively find end event from a node
                        const findEndEventFromNode = (nodeId, visited = new Set()) => {
                            if (visited.has(nodeId)) return null; // Avoid cycles
                            visited.add(nodeId);
                            
                            const nodeElement = elementRegistry.get(nodeId);
                            if (!nodeElement) return null;
                            
                            // If this is an end event, return it
                            if (nodeElement.type === 'bpmn:EndEvent') {
                                return nodeId;
                            }
                            
                            // Get outgoing flows from this node using businessObject
                            const businessObject = nodeElement.businessObject;
                            if (!businessObject || !businessObject.outgoing) {
                                return null;
                            }
                            
                            const outgoingFlows = businessObject.outgoing;
                            console.log(`🔍 [HIGHLIGHT] Node ${nodeId} has ${outgoingFlows.length} outgoing flow(s)`);
                            
                            // Follow each outgoing flow
                            for (const flow of outgoingFlows) {
                                let targetId = null;
                                
                                // Get target from flow
                                if (flow.targetRef) {
                                    targetId = flow.targetRef.id || flow.targetRef;
                                } else if (flow.target) {
                                    targetId = flow.target.id || flow.target;
                                }
                                
                                if (targetId) {
                                    console.log(`🔍 [HIGHLIGHT] Following flow from ${nodeId} to ${targetId}`);
                                    const endEventId = findEndEventFromNode(targetId, visited);
                                    if (endEventId) {
                                        return endEventId;
                                    }
                                }
                            }
                            
                            return null;
                        };
                        
                        // Find end event from last completed task
                        const reachedEndEventId = findEndEventFromNode(lastTaskNodeId);
                        if (reachedEndEventId) {
                            canvas.addMarker(reachedEndEventId, 'highlight-active');
                            console.log(`✅ [HIGHLIGHT] Highlighted reached end event: ${reachedEndEventId}`);
                        } else {
                            console.warn(`⚠️ [HIGHLIGHT] Could not find end event from last completed task: ${lastTaskNodeId}`);
                            // Fallback: highlight all end events if we can't determine the path
                            elementRegistry.forEach(element => {
                                if (element.type === 'bpmn:EndEvent') {
                                    canvas.addMarker(element.id, 'highlight-active');
                                    console.log(`⚠️ [HIGHLIGHT] Fallback: Highlighted end event: ${element.id}`);
                                }
                            });
                        }
                    } else {
                        console.warn(`⚠️ [HIGHLIGHT] Could not find element for last completed task: ${lastTaskNodeId}`);
                        // Fallback: highlight all end events if we can't determine the path
                        elementRegistry.forEach(element => {
                            if (element.type === 'bpmn:EndEvent') {
                                canvas.addMarker(element.id, 'highlight-active');
                                console.log(`⚠️ [HIGHLIGHT] Fallback: Highlighted end event: ${element.id}`);
                            }
                        });
                    }
                } else {
                    console.warn('⚠️ [HIGHLIGHT] No completed tasks found to determine end event path');
                    // Fallback: highlight all end events if we can't determine the path
                    elementRegistry.forEach(element => {
                        if (element.type === 'bpmn:EndEvent') {
                            canvas.addMarker(element.id, 'highlight-active');
                            console.log(`⚠️ [HIGHLIGHT] Fallback: Highlighted end event: ${element.id}`);
                        }
                    });
                }
            }

        } catch (error) {
            console.error('Error highlighting active tasks:', error);
        }
    }

    /**
     * Clear all task highlights from the diagram
     */
    function clearTaskHighlights() {
        if (!bpmnViewer) return;

        try {
            const canvas = bpmnViewer.get('canvas');
            const elementRegistry = bpmnViewer.get('elementRegistry');

            // Get all elements and remove markers
            elementRegistry.forEach(element => {
                canvas.removeMarker(element.id, 'highlight-active');
                canvas.removeMarker(element.id, 'highlight-completed');
            });
        } catch (error) {
            console.error('Error clearing task highlights:', error);
        }
    }

    /**
     * Clear BPMN diagram
     */
    function clearWorkflowDiagram() {
        const canvas = document.getElementById('workflowDiagramCanvas');
        const placeholder = document.getElementById('workflowDiagramPlaceholder');
        if (canvas) canvas.style.display = 'none';
        if (placeholder) {
            placeholder.style.display = 'flex';
            placeholder.innerHTML = `
                    <div style="text-align: center;">
                        <i class="fas fa-project-diagram" style="font-size: 3rem; margin-bottom: 1rem; opacity: 0.3;"></i>
                        <p>Select a workflow to view diagram</p>
                    </div>
                `;
        }
        if (bpmnViewer) {
            bpmnViewer.clear();
        }
        clearTaskHighlights();
    }
    /**
     * Show workflow loading state
     */
    function showWorkflowLoading() {
        const placeholder = document.getElementById('workflowDiagramPlaceholder');
        if (placeholder) {
            placeholder.style.display = 'flex';
            placeholder.innerHTML = `
                    <div style="text-align: center;">
                        <i class="fas fa-spinner fa-spin" style="font-size: 3rem; margin-bottom: 1rem;"></i>
                        <p>Loading workflow...</p>
                    </div>
                `;
        }
    }

    /**
     * Show element properties in panel (similar to workflow-view.js)
     */
    function showElementProperties(element) {
        const propertiesPanel = document.getElementById('bpmn-properties-panel');
        const propertiesContent = document.getElementById('propertiesContent');

        if (!propertiesPanel || !propertiesContent || !bpmnViewer) return;

        const businessObject = element.businessObject;
        let propertiesHtml = '';

        const savedProperties = getElementProperties(businessObject);
        const savedPropKeys = new Set(Object.keys(savedProperties));

        // Element Name
        if (businessObject && businessObject.name) {
            propertiesHtml += `
                <div class="property-item">
                    <div class="property-label">Name</div>
                    <div class="property-value">${escapeHtml(businessObject.name)}</div>
                </div>
            `;
        }

        // Description/Documentation
        if (businessObject && businessObject.documentation && businessObject.documentation.length > 0) {
            const docText = businessObject.documentation[0].text || '';
            if (docText.trim()) {
                propertiesHtml += `
                    <div class="property-item">
                        <div class="property-label">Description</div>
                        <div class="property-value">${escapeHtml(docText)}</div>
                    </div>
                `;
            }
        }

        // For User Tasks - show assignee, candidate groups, Due Date, and Unlock Object
        if (businessObject && (businessObject.$type === 'bpmn:UserTask' || businessObject.$type === 'bpmn:Task')) {
            if (businessObject.assignee && !savedPropKeys.has('assignee')) {
                propertiesHtml += `
                    <div class="property-item">
                        <div class="property-label">Assignee</div>
                        <div class="property-value">${escapeHtml(businessObject.assignee)}</div>
                    </div>
                `;
            }
            if (businessObject.candidateGroups && !savedPropKeys.has('candidateGroups')) {
                propertiesHtml += `
                    <div class="property-item">
                        <div class="property-label">Candidate Groups</div>
                        <div class="property-value">${escapeHtml(businessObject.candidateGroups)}</div>
                    </div>
                `;
            }
            
            // Show Due Date if it exists
            const dueDateValue = savedProperties.dueDate || savedProperties['camunda:dueDate'];
            if (dueDateValue) {
                const displayDueDate = formatPropertyValue('dueDate', dueDateValue);
                propertiesHtml += `
                    <div class="property-item">
                        <div class="property-label">Due Date</div>
                        <div class="property-value">${escapeHtml(displayDueDate)}</div>
                    </div>
                `;
            }
            
            // Show Unlock Object if it exists (even if false)
            const unlockObjectValue = savedProperties.unlockObject !== undefined ? savedProperties.unlockObject : 
                                     (savedProperties['camunda:unlockObject'] !== undefined ? savedProperties['camunda:unlockObject'] : null);
            if (unlockObjectValue !== null && unlockObjectValue !== undefined) {
                const displayUnlock = formatPropertyValue('unlockObject', unlockObjectValue);
                propertiesHtml += `
                    <div class="property-item">
                        <div class="property-label">Unlock Object</div>
                        <div class="property-value">${escapeHtml(displayUnlock)}</div>
                    </div>
                `;
            }
        }

        // For Gateways - show gateway direction
        if (businessObject && businessObject.$type && businessObject.$type.includes('Gateway')) {
            if (businessObject.gatewayDirection) {
                propertiesHtml += `
                    <div class="property-item">
                        <div class="property-label">Gateway Direction</div>
                        <div class="property-value">${escapeHtml(businessObject.gatewayDirection)}</div>
                    </div>
                `;
            }
        }

        // For Sequence Flows - show condition
        if (businessObject && businessObject.$type === 'bpmn:SequenceFlow') {
            if (businessObject.conditionExpression) {
                const condition = businessObject.conditionExpression.body || '';
                if (condition.trim()) {
                    const formattedCondition = formatCondition(condition);
                    propertiesHtml += `
                        <div class="property-item">
                            <div class="property-label">Condition</div>
                            <div class="property-value">${escapeHtml(formattedCondition)}</div>
                        </div>
                    `;
                }
            }
        }

        // For End Events - explicitly show Status, Lifecycle, and Commit Changes if they exist
        if (businessObject && businessObject.$type === 'bpmn:EndEvent') {
            const statusValue = savedProperties.status || savedProperties['camunda:status'];
            const lifecycleValue = savedProperties.lifecycle || savedProperties['camunda:lifecycle'];
            const commitChangesValue = savedProperties.commitChanges || savedProperties['camunda:commitChanges'];
            
            if (statusValue) {
                const displayStatus = formatPropertyValue('status', statusValue);
                propertiesHtml += `
                    <div class="property-item">
                        <div class="property-label">Status</div>
                        <div class="property-value">${escapeHtml(displayStatus)}</div>
                    </div>
                `;
            }
            
            if (lifecycleValue) {
                const displayLifecycle = formatPropertyValue('lifecycle', lifecycleValue);
                propertiesHtml += `
                    <div class="property-item">
                        <div class="property-label">Lifecycle</div>
                        <div class="property-value">${escapeHtml(displayLifecycle)}</div>
                    </div>
                `;
            }
            
            // Show Commit Changes if it exists (even if false)
            if (commitChangesValue !== null && commitChangesValue !== undefined) {
                const displayCommit = formatPropertyValue('commitChanges', commitChangesValue);
                propertiesHtml += `
                    <div class="property-item">
                        <div class="property-label">Commit Changes</div>
                        <div class="property-value">${escapeHtml(displayCommit)}</div>
                    </div>
                `;
            }
        }

        // Saved properties from extension elements / camunda attributes
        // Exclude properties already shown above for specific element types
        Object.entries(savedProperties).forEach(([key, value]) => {
            // Skip status, lifecycle, and commitChanges for End Events (already shown above)
            if (businessObject && businessObject.$type === 'bpmn:EndEvent' && 
                (key === 'status' || key === 'lifecycle' || key === 'commitChanges' || 
                 key === 'camunda:status' || key === 'camunda:lifecycle' || key === 'camunda:commitChanges')) {
                return;
            }
            
            // Skip dueDate and unlockObject for UserTasks (already shown above)
            if (businessObject && (businessObject.$type === 'bpmn:UserTask' || businessObject.$type === 'bpmn:Task' || businessObject.$type === 'bpmn:ServiceTask') &&
                (key === 'dueDate' || key === 'unlockObject' || 
                 key === 'camunda:dueDate' || key === 'camunda:unlockObject')) {
                return;
            }
            
            const displayValue = formatPropertyValue(key, value);
            propertiesHtml += `
                <div class="property-item">
                    <div class="property-label">${escapeHtml(formatPropertyLabel(key))}</div>
                    <div class="property-value">${escapeHtml(displayValue)}</div>
                </div>
            `;
        });

        propertiesContent.innerHTML = propertiesHtml;
        propertiesPanel.style.display = 'block';
    }

    /**
     * Hide properties panel
     */
    function hidePropertiesPanel() {
        const propertiesPanel = document.getElementById('bpmn-properties-panel');
        if (propertiesPanel) {
            propertiesPanel.style.display = 'none';
        }
    }

    /**
     * Initialize drag functionality for properties panel
     */
    function initializePropertiesPanelDrag() {
        const panel = document.getElementById('bpmn-properties-panel');
        if (!panel) {
            console.warn('[Workflow] Properties panel not found for drag initialization');
            return;
        }
        
        // Check if already initialized to prevent duplicate listeners
        if (panel.dataset.dragInitialized === 'true') {
            return;
        }
        
        const dragHandle = panel.querySelector('.properties-panel-drag-handle');
        if (!dragHandle) {
            console.warn('[Workflow] Drag handle not found in properties panel');
            return;
        }
        
        let isDragging = false;
        let startX = 0;
        let startY = 0;
        let initialLeft = 0;
        let initialTop = 0;
        
        // Store initial position (right: 20px, top: 20px)
        let panelLeft = null;
        let panelTop = null;
        let hasBeenDragged = false;
        
        // Get initial position
        function getInitialPosition() {
            const parent = panel.parentElement;
            if (parent && !hasBeenDragged) {
                const parentRect = parent.getBoundingClientRect();
                const panelRect = panel.getBoundingClientRect();
                // Calculate from right: 20px, top: 20px
                panelLeft = parentRect.width - panelRect.width - 20;
                panelTop = 20;
            }
        }
        
        getInitialPosition();
        
        // Drag start handler
        function dragStart(e) {
            if (e.button !== 0) return; // Only left mouse button
            
            // Don't start drag if clicking on close button or its children
            if (e.target.closest('.close-btn')) return;
            
            // Allow dragging from anywhere in the drag handle (header)
            e.preventDefault();
            e.stopPropagation();
            
            isDragging = true;
            panel.classList.add('dragging');
            
            // Get current position
            const rect = panel.getBoundingClientRect();
            const parentRect = panel.parentElement.getBoundingClientRect();
            
            // If panel hasn't been dragged, use initial position
            if (!hasBeenDragged) {
                panelLeft = parentRect.width - rect.width - 20;
                panelTop = 20;
                // Set initial position using left/top instead of right/top
                panel.style.right = 'auto';
                panel.style.left = panelLeft + 'px';
                panel.style.top = panelTop + 'px';
                panel.style.transform = 'none';
            } else {
                // Get current left/top from computed style
                const computedStyle = window.getComputedStyle(panel);
                panelLeft = parseInt(computedStyle.left) || 0;
                panelTop = parseInt(computedStyle.top) || 0;
            }
            
            startX = e.clientX;
            startY = e.clientY;
            initialLeft = panelLeft;
            initialTop = panelTop;
        }
        
        // Drag handler
        function drag(e) {
            if (isDragging) {
                e.preventDefault();
                e.stopPropagation();
                
                const deltaX = e.clientX - startX;
                const deltaY = e.clientY - startY;
                
                const newLeft = initialLeft + deltaX;
                const newTop = initialTop + deltaY;
                
                // Constrain to parent bounds
                const parent = panel.parentElement;
                if (parent) {
                    const parentRect = parent.getBoundingClientRect();
                    const panelRect = panel.getBoundingClientRect();
                    const maxLeft = parentRect.width - panelRect.width;
                    const maxTop = parentRect.height - panelRect.height;
                    
                    panelLeft = Math.max(0, Math.min(newLeft, maxLeft));
                    panelTop = Math.max(0, Math.min(newTop, maxTop));
                } else {
                    panelLeft = newLeft;
                    panelTop = newTop;
                }
                
                // Update position using left/top
                panel.style.right = 'auto';
                panel.style.left = panelLeft + 'px';
                panel.style.top = panelTop + 'px';
                panel.style.transform = 'none';
                panel.style.bottom = 'auto';
                
                hasBeenDragged = true;
            }
        }
        
        // Drag end handler
        function dragEnd(e) {
            if (isDragging) {
                isDragging = false;
                panel.classList.remove('dragging');
            }
        }
        
        // Add event listeners
        dragHandle.addEventListener('mousedown', dragStart);
        document.addEventListener('mousemove', drag);
        document.addEventListener('mouseup', dragEnd);
        
        // Mark as initialized
        panel.dataset.dragInitialized = 'true';
        
        console.log('[Workflow] Properties panel drag functionality initialized');
    }

    /**
     * Extract stored properties for an element (camunda properties and attrs)
     */
    function getElementProperties(businessObject) {
        const properties = {};
        if (!businessObject) return properties;

        const toArray = (val) => {
            if (!val) return [];
            if (Array.isArray(val)) return val;
            if (typeof val === 'object') {
                if (Array.isArray(val.$children)) return val.$children;
                if (Array.isArray(val.values)) return val.values;
                return Object.values(val);
            }
            return [];
        };

        if (businessObject.extensionElements) {
            const extValues = businessObject.extensionElements.values || [];
            toArray(extValues).forEach(ext => {
                if (!ext) return;

                if (ext.$type === 'camunda:Properties' || ext.$type === 'Properties' ||
                    (ext.$type && ext.$type.includes('Properties') && ext.values)) {
                    toArray(ext.values).forEach(prop => {
                        if (prop && prop.name && prop.value !== undefined && prop.value !== null) {
                            properties[prop.name] = String(prop.value);
                        }
                    });
                }

                if (ext.$type && (ext.$type.includes('Property') || ext.$type === 'camunda:Property') &&
                    ext.name && ext.value !== undefined && ext.value !== null) {
                    properties[ext.name] = String(ext.value);
                }

                if (ext.$children && Array.isArray(ext.$children)) {
                    ext.$children.forEach(child => {
                        if (child && child.name && child.value !== undefined && child.value !== null) {
                            properties[child.name] = String(child.value);
                        }
                    });
                }
            });
        }

        if (businessObject.$attrs) {
            Object.keys(businessObject.$attrs).forEach(key => {
                if (key.startsWith('camunda:')) {
                    const propName = key.replace('camunda:', '');
                    const value = businessObject.$attrs[key];
                    if (value !== undefined && value !== null) {
                        properties[propName] = String(value);
                    }
                }
            });
        }
        
        // Also check direct attributes for status and lifecycle (for End Events)
        // Some BPMN implementations store these directly
        if (businessObject.$type === 'bpmn:EndEvent') {
            if (businessObject.status && !properties.status) {
                properties.status = String(businessObject.status);
            }
            if (businessObject.lifecycle && !properties.lifecycle) {
                properties.lifecycle = String(businessObject.lifecycle);
            }
        }

        return properties;
    }

    /**
     * Handle Complete Change Request
     */
    async function handleCompleteChangeRequest(changeRequestId) {
        console.log('🎯 [FRONTEND] handleCompleteChangeRequest called with ID:', changeRequestId);
        if (!changeRequestId) {
            alert('Error: Change request ID not found');
            return;
        }
        
        // Show confirmation dialog
        const confirmed = confirm('Are you sure you want to complete this change request?');
        if (!confirmed) {
            console.log('❌ [FRONTEND] User cancelled Complete action');
            return;
        }
        
        console.log('📡 [FRONTEND] Sending POST request to /api/changerequests/' + changeRequestId + '/complete');
        try {
            const response = await fetch(`/api/changerequests/${changeRequestId}/complete`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                credentials: 'include'
            });
            
            console.log('📥 [FRONTEND] Response status:', response.status, response.statusText);
            
            if (!response.ok) {
                const errorData = await response.json().catch(() => ({ error: 'Failed to complete change request' }));
                alert('Error: ' + (errorData.error || 'Failed to complete change request'));
                return;
            }
            
            const result = await response.json();
            if (result.success) {
                alert('Change request completed successfully!');
                
                // ⚠️ CRITICAL: Refresh locks on dataset edit page if user is on it
                // This ensures status/lifecycle fields are unlocked immediately after CREATE CR completion
                // Check if we're on a dataset edit page and refresh locks
                const currentPath = window.location.pathname;
                const datasetEditMatch = currentPath.match(/\/view\/dataset\/(\d+)/);
                if (datasetEditMatch) {
                    const datasetId = parseInt(datasetEditMatch[1]);
                    console.log('[CR Complete] Refreshing locks for dataset edit page, datasetId:', datasetId);
                    if (window.reapplyDFCRLocks) {
                        setTimeout(async () => {
                            await window.reapplyDFCRLocks('Data Set', datasetId);
                            console.log('[CR Complete] Locks refreshed for dataset', datasetId);
                        }, 500);
                    }
                }
                
                // Force a small delay to ensure database transaction is committed
                await new Promise(resolve => setTimeout(resolve, 500));
                
                // Force full page reload to ensure all data is fresh
                // This ensures the reference field (Affected Item) is updated in the UI
                // with the new nobject_id instead of the old object_id
                window.location.reload();
            } else {
                alert('Error: ' + (result.error || 'Failed to complete change request'));
            }
        } catch (error) {
            console.error('Error completing change request:', error);
            alert('Error completing change request: ' + error.message);
        }
    }

    /**
     * Handle Cancel Change Request
     */
    async function handleCancelChangeRequest(changeRequestId) {
        if (!changeRequestId) {
            alert('Error: Change request ID not found');
            return;
        }
        
        // Check if user can cancel this CR
        if (!currentChangeRequest) {
            alert('Error: Could not load change request data');
            return;
        }
        
        const canCancel = await canCancelCR(currentChangeRequest);
        if (!canCancel) {
            const statusName = (currentChangeRequest.statusName || currentChangeRequest.StatusName || '').toLowerCase();
            const isRunning = statusName.includes('running') || statusName.includes('in progress');
            const isCreator = (currentChangeRequest.createdBy == (currentUser.id || currentUser.userId || currentUser.ID));
            
            if (isCreator && isRunning) {
                alert('You cannot cancel this change request. The requester cannot cancel a change request when its status is RUNNING. Only stakeholders can cancel it in this state.');
            } else {
                alert('You are not authorized to cancel this change request. Only stakeholders and the creator (when not RUNNING) can cancel.');
            }
            return;
        }
        
        // Show confirmation dialog
        const confirmed = confirm('Are you sure you want to cancel this change request?');
        if (!confirmed) {
            return;
        }
        
        try {
            const response = await fetch(`/api/changerequests/${changeRequestId}/cancel`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                credentials: 'include'
            });
            
            if (!response.ok) {
                const errorData = await response.json().catch(() => ({ error: 'Failed to cancel change request' }));
                alert('Error: ' + (errorData.error || 'Failed to cancel change request'));
                return;
            }
            
            const result = await response.json();
            if (result.success) {
                alert('Change request cancelled successfully!');
                
                // ⚠️ CRITICAL: Refresh locks on dataset edit page if user is on it
                // This ensures status/lifecycle fields are unlocked immediately after CREATE CR cancellation
                // Check if we're on a dataset edit page and refresh locks
                const currentPath = window.location.pathname;
                const datasetEditMatch = currentPath.match(/\/view\/dataset\/(\d+)/);
                if (datasetEditMatch) {
                    const datasetId = parseInt(datasetEditMatch[1]);
                    console.log('[CR Cancel] Refreshing locks for dataset edit page, datasetId:', datasetId);
                    if (window.reapplyDFCRLocks) {
                        setTimeout(async () => {
                            await window.reapplyDFCRLocks('Data Set', datasetId);
                            console.log('[CR Cancel] Locks refreshed for dataset', datasetId);
                        }, 500);
                    }
                }
                
                // Reload CR data to refresh UI
                await loadChangeRequestData();
            } else {
                alert('Error: ' + (result.error || 'Failed to cancel change request'));
            }
        } catch (error) {
            console.error('Error cancelling change request:', error);
            alert('Error cancelling change request: ' + error.message);
        }
    }

    /**
     * Load statuses for mapping ID -> name
     */
    async function loadStatuses() {
        try {
            const res = await fetch('/api/status/list');
            if (!res.ok) throw new Error('Failed to load statuses');
            const data = await res.json();
            statuses = Array.isArray(data) ? data : (data.data || []);
        } catch (err) {
            console.error('Error loading statuses for view:', err);
            statuses = [];
        }
    }

    /**
     * Normalize lifecycle data structure
     */
    function normalizeLifecycles(data) {
        let lifecyclesList = [];
        if (data && typeof data === 'object' && data.data && Array.isArray(data.data)) {
            lifecyclesList = data.data;
        } else if (Array.isArray(data)) {
            lifecyclesList = data;
        }

        return lifecyclesList.map(item => {
            const copy = { ...item };
            if (copy.ID !== undefined && copy.id === undefined) copy.id = copy.ID;
            if (copy.PrimaryName !== undefined) {
                if (copy.primaryname === undefined) copy.primaryname = copy.PrimaryName;
                if (copy.primaryName === undefined) copy.primaryName = copy.PrimaryName;
                if (copy.name === undefined) copy.name = copy.PrimaryName;
            }
            if (copy.name !== undefined && copy.primaryname === undefined) {
                copy.primaryname = copy.name;
                copy.primaryName = copy.name;
            }
            return copy;
        });
    }

    /**
     * Load lifecycles for current module/facet
     */
    async function loadLifecycles(moduleNameOrId) {
        if (!moduleNameOrId) {
            lifecycles = [];
            return;
        }

        // If already loaded for this module, skip
        if (lifecycleModuleId === moduleNameOrId && lifecycles.length > 0) return;

        const tryFetch = async (endpoint) => {
            const res = await fetch(endpoint);
            if (!res.ok) {
                // 404 is expected for endpoints that don't exist - don't throw, just return null
                if (res.status === 404) {
                    return null;
                }
                throw new Error('failed');
            }
            const json = await res.json();
            return normalizeLifecycles(json);
        };

        const moduleName = typeof moduleNameOrId === 'string' ? moduleNameOrId : '';
        const moduleNameLower = moduleName.toLowerCase().trim().replace(/\s+/g, '_');
        const endpoints = [];

        if (moduleName) {
            endpoints.push(`/api/${moduleNameLower}/lifecycle/list`);
            endpoints.push(`/api/${moduleNameLower.replace(/_/g, '')}/lifecycle/list`);
            endpoints.push(`/api/${moduleNameLower.replace('_', '-')}/lifecycle/list`);
            // Dataset
            if (moduleNameLower === 'data_set' || moduleNameLower === 'dataset' || moduleName === 'Data Sets' || moduleName === 'Dataset') {
                endpoints.unshift('/api/lifecycle/list');
            }
            // Business Area
            if (moduleNameLower === 'business_area' || moduleNameLower === 'businessarea' || 
                moduleName === 'Business Area' || moduleNameLower === 'business') {
                endpoints.unshift('/api/business-area-lifecycles');
            }
            // Committee
            if (moduleNameLower === 'committee' || moduleName === 'Committee') {
                endpoints.unshift('/api/committee/lookup?type=lifecycle');
            }
            // Capability
            if (moduleNameLower === 'capability' || moduleName === 'Capability') {
                endpoints.unshift('/api/capability-lifecycles/dropdown');
            }
            // Client
            if (moduleNameLower === 'client' || moduleName === 'Client') {
                endpoints.unshift('/api/client/lifecycle-list');
            }
            // Process
            if (moduleNameLower === 'process' || moduleName === 'Process') {
                endpoints.unshift('/api/process/lifecycle/list');
            }
            // Glossary
            if (moduleNameLower === 'glossary' || moduleName === 'Glossary') {
                endpoints.unshift('/api/glossary/lifecycle/list');
            }
            // System
            if (moduleNameLower === 'system' || moduleName === 'System') {
                endpoints.unshift('/api/system/lifecycle/list');
            }
            // Interface / System Interface
            if (moduleNameLower === 'interface' || moduleNameLower === 'system_interface' || 
                moduleNameLower === 'system-interface' || moduleName === 'System Interface' || moduleName === 'Interface') {
                endpoints.unshift('/api/interface/lifecycle/list');
            }
            // Project
            if (moduleNameLower === 'project' || moduleName === 'Project') {
                endpoints.unshift('/api/project/lifecycle/list');
            }
            // Product
            if (moduleNameLower === 'product' || moduleName === 'Product') {
                endpoints.unshift('/api/product/lifecycle/list');
            }
            // Policy
            if (moduleNameLower === 'policy' || moduleName === 'Policy') {
                endpoints.unshift('/api/policy/lifecycle/list');
            }
            // People
            if (moduleNameLower === 'people' || moduleName === 'People') {
                endpoints.unshift('/api/people/lifecycle');
            }
        }

        // Always try generic lifecycle list last
        endpoints.push('/api/lifecycle/list');

        for (const ep of endpoints) {
            try {
                const list = await tryFetch(ep);
                if (list && Array.isArray(list) && list.length > 0) {
                    lifecycles = list;
                    lifecycleModuleId = moduleNameOrId;
                    return;
                }
            } catch (err) {
                // Silently continue to next endpoint
                // 404s are expected for endpoints that don't exist
            }
        }

        lifecycles = [];
        lifecycleModuleId = moduleNameOrId;
    }

    /**
     * Format condition expression for better readability
     * Removes ${} wrapper and formats the condition nicely
     */
    function formatCondition(condition) {
        if (!condition || typeof condition !== 'string') return condition;
        
        let formatted = condition.trim();
        
        // Remove ${} wrapper if present
        if (formatted.startsWith('${') && formatted.endsWith('}')) {
            formatted = formatted.substring(2, formatted.length - 1).trim();
        }
        
        // Format common patterns for better readability
        // Replace == with more readable format
        formatted = formatted.replace(/\s*==\s*/g, ' == ');
        formatted = formatted.replace(/\s*!=\s*/g, ' != ');
        formatted = formatted.replace(/\s*<=\s*/g, ' <= ');
        formatted = formatted.replace(/\s*>=\s*/g, ' >= ');
        formatted = formatted.replace(/\s*<\s*/g, ' < ');
        formatted = formatted.replace(/\s*>\s*/g, ' > ');
        
        // Clean up extra spaces
        formatted = formatted.replace(/\s+/g, ' ');
        
        return formatted;
    }

    /**
     * Format property value for display (map IDs to names where possible)
     */
    function formatPropertyValue(key, value) {
        if (value === undefined || value === null) return '';
        const v = String(value);
        if (key === 'status') {
            const match = statuses.find(s => String(s.id || s.ID) === v);
            if (match) return match.primaryName || match.primaryname || match.name || v;
        }
        if (key === 'lifecycle') {
            const match = lifecycles.find(l => String(l.id || l.ID) === v);
            if (match) return match.primaryName || match.primaryname || match.name || v;
        }
        if (key === 'dueDate') {
            // Format as "X day(s)"
            const days = parseInt(v);
            if (!isNaN(days) && days > 0) {
                return `${days} day${days > 1 ? 's' : ''}`;
            }
            return v;
        }
        if (key === 'unlockObject' || key === 'commitChanges') {
            // Format boolean values
            if (v === 'true' || v === '1' || v === true) {
                return 'Yes';
            }
            if (v === 'false' || v === '0' || v === false) {
                return 'No';
            }
            return v;
        }
        return v;
    }

    /**
     * Make property keys readable for display
     */
    function formatPropertyLabel(name) {
        if (!name) return '';
        return name
            .replace(/^camunda:/, '')
            .replace(/[_-]+/g, ' ')
            .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
            .replace(/\s+/g, ' ')
            .trim()
            .replace(/^./, c => c.toUpperCase());
    }
    /**
     * Show validation error message
     */
    function showValidationError(message) {
        const errorDiv = document.getElementById('workflowValidationError');
        const errorText = document.getElementById('validationErrorText');

        if (errorDiv && errorText) {
            errorText.textContent = message;
            errorDiv.style.display = 'block';
        }
    }
    /**
     * Hide validation error message
     */
    function hideValidationError() {
        const errorDiv = document.getElementById('workflowValidationError');
        if (errorDiv) {
            errorDiv.style.display = 'none';
        }
    }
    /**
     * Show workflow actions (start button)
     */
    function showWorkflowActions(changeRequestParam) {
        // Check if CR is completed - don't show start workflow button for completed CRs
        // Check both currentChangeRequest and changeRequest from function parameter if available
        let changeRequestToCheck = changeRequestParam || currentChangeRequest;
        
        if (changeRequestToCheck) {
            const statusName = (changeRequestToCheck.statusName || changeRequestToCheck.StatusName || '').toLowerCase();
            const isCompleted = statusName && statusName.includes('completed');
            if (isCompleted) {
                // Don't show start workflow button for completed CRs
                const actionsDiv = document.getElementById('workflowActions');
                if (actionsDiv) {
                    actionsDiv.style.display = 'none';
                }
                return;
            }
        }
        
        const actionsDiv = document.getElementById('workflowActions');
        if (actionsDiv) {
            actionsDiv.style.display = 'block';
        }
    }
    /**
     * Hide workflow actions
     */
    function hideWorkflowActions() {
        const actionsDiv = document.getElementById('workflowActions');
        if (actionsDiv) {
            actionsDiv.style.display = 'none';
        }
    }
    /**
     * Show workflow started status
     * ⚠️ CRITICAL: Only show for auto CRs (mandatory_workflow = true)
     * Manual CRs should NOT show this message
     */
    function showWorkflowStartedStatus(changeRequest = null) {
        // ⚠️ CRITICAL: Only show for auto CRs (mandatory_workflow = true)
        // Manual CRs should NOT show "Workflow started successfully" message
        if (changeRequest) {
            const mandatoryWorkflow = changeRequest.mandatoryWorkflow;
            const isAutoCR = mandatoryWorkflow === true || mandatoryWorkflow === 1 || mandatoryWorkflow === 'true' || mandatoryWorkflow === '1';
            
            if (!isAutoCR) {
                console.log('[WorkflowStatus] Manual CR detected - hiding "Workflow started successfully" message');
                const statusDiv = document.getElementById('workflowStartedStatus');
                if (statusDiv) {
                    statusDiv.style.display = 'none';
                }
                return;
            }
        } else {
            // Try to get changeRequest from currentChangeRequestId
            if (typeof currentChangeRequestId !== 'undefined' && currentChangeRequestId) {
                // Fetch change request data to check type
                fetch(`/api/changerequests/${currentChangeRequestId}`, {
                    credentials: 'include'
                })
                .then(res => res.ok ? res.json() : null)
                .then(cr => {
                    if (cr) {
                        const mandatoryWorkflow = cr.mandatoryWorkflow;
                        const isAutoCR = mandatoryWorkflow === true || mandatoryWorkflow === 1 || mandatoryWorkflow === 'true' || mandatoryWorkflow === '1';
                        
                        if (!isAutoCR) {
                            console.log('[WorkflowStatus] Manual CR detected - hiding "Workflow started successfully" message');
                            const statusDiv = document.getElementById('workflowStartedStatus');
                            if (statusDiv) {
                                statusDiv.style.display = 'none';
                            }
                            return;
                        }
                    }
                    // Show message for auto CRs
                    displayWorkflowStartedStatus();
                })
                .catch(err => {
                    console.warn('[WorkflowStatus] Could not check CR type, showing message by default:', err);
                    // Default to showing if we can't check (better UX)
                    displayWorkflowStartedStatus();
                });
                return;
            }
        }
        
        // Show message for auto CRs
        displayWorkflowStartedStatus();
    }
    
    /**
     * Internal helper to display the workflow started status message
     */
    function displayWorkflowStartedStatus() {
        const statusDiv = document.getElementById('workflowStartedStatus');
        const statusText = document.getElementById('workflowStatusText');
        if (statusDiv) {
            if (statusText) {
                statusText.textContent = 'Workflow started successfully';
            }
            statusDiv.style.display = 'block';
        }
    }

    /**
     * Show workflow completed status
     * ⚠️ CRITICAL: Only show for auto CRs (mandatory_workflow = true)
     * Manual CRs should NOT show this message
     */
    function showWorkflowCompletedStatus(changeRequest = null) {
        // ⚠️ CRITICAL: Only show for auto CRs (mandatory_workflow = true)
        // Manual CRs should NOT show "Workflow completed successfully" message
        if (changeRequest) {
            const mandatoryWorkflow = changeRequest.mandatoryWorkflow;
            const isAutoCR = mandatoryWorkflow === true || mandatoryWorkflow === 1 || mandatoryWorkflow === 'true' || mandatoryWorkflow === '1';
            
            if (!isAutoCR) {
                console.log('[WorkflowStatus] Manual CR detected - hiding "Workflow completed successfully" message');
                const statusDiv = document.getElementById('workflowStartedStatus');
                if (statusDiv) {
                    statusDiv.style.display = 'none';
                }
                return;
            }
        }
        
        // Show message for auto CRs
        const statusDiv = document.getElementById('workflowStartedStatus');
        const statusText = document.getElementById('workflowStatusText');
        if (statusDiv) {
            if (statusText) {
                statusText.textContent = 'Workflow completed successfully';
            }
            statusDiv.style.display = 'block';
        }
    }

    /**
     * Check if workflow definition has been updated since instance started and show warning
     */
    async function checkAndShowWorkflowUpdateWarning(changeRequestId, workflowData = null) {
        try {
            // Fetch workflow status if not provided
            if (!workflowData) {
                const response = await fetch(`/api/workflow_instances/by-cr/${changeRequestId}`, {
                    method: 'GET',
                    credentials: 'include'
                });
                if (!response.ok) {
                    return; // No workflow instance or error
                }
                workflowData = await response.json();
            }

            const instance = workflowData.instance;
            const processDefinition = workflowData.processDefinition;

            // Need both instance and process definition to compare timestamps
            if (!instance || !processDefinition) {
                return;
            }

            // Need both timestamps to compare
            if (!instance.startedAt || !processDefinition.updatedAt) {
                return;
            }

            // Compare timestamps: if processDefinition.updatedAt > instance.startedAt, show warning
            const startedAt = new Date(instance.startedAt);
            const updatedAt = new Date(processDefinition.updatedAt);

            if (updatedAt > startedAt) {
                showWorkflowUpdateWarning(processDefinition.name || 'Workflow');
            } else {
                hideWorkflowUpdateWarning();
            }
        } catch (error) {
            console.error('Error checking workflow definition update:', error);
            // Don't show error to user, just log it
        }
    }

    /**
     * Show warning that workflow definition has been updated
     */
    function showWorkflowUpdateWarning(workflowName) {
        let warningDiv = document.getElementById('workflowUpdateWarning');
        
        if (!warningDiv) {
            // Create warning element if it doesn't exist
            const workflowSection = document.getElementById('workflowSection');
            if (!workflowSection) return;

            warningDiv = document.createElement('div');
            warningDiv.id = 'workflowUpdateWarning';
            warningDiv.style.cssText = 'display: none; margin-top: 1rem; padding: 0.75rem; background: #fef3c7; border-left: 3px solid #f59e0b; border-radius: 4px;';
            
            const icon = document.createElement('i');
            icon.className = 'fas fa-exclamation-triangle';
            icon.style.cssText = 'color: #d97706; margin-right: 0.5rem;';
            
            const message = document.createElement('span');
            message.id = 'workflowUpdateWarningText';
            message.style.cssText = 'color: #92400e;';
            
            warningDiv.appendChild(icon);
            warningDiv.appendChild(message);
            
            // Insert after workflow status or before workflow diagram
            const workflowStatus = document.getElementById('workflowStartedStatus');
            const workflowDiagram = document.querySelector('.section-title[style*="WORKFLOW DIAGRAM"]');
            
            if (workflowStatus && workflowStatus.nextSibling) {
                workflowSection.insertBefore(warningDiv, workflowStatus.nextSibling);
            } else if (workflowDiagram && workflowDiagram.parentNode) {
                workflowDiagram.parentNode.insertBefore(warningDiv, workflowDiagram);
            } else {
                workflowSection.appendChild(warningDiv);
            }
        }

        // Update message
        const messageText = document.getElementById('workflowUpdateWarningText');
        if (messageText) {
            messageText.textContent = `This workflow definition "${workflowName}" has been updated since this change request started. The CR continues using the original workflow version.`;
        }

        warningDiv.style.display = 'block';
    }

    /**
     * Hide workflow update warning
     */
    function hideWorkflowUpdateWarning() {
        const warningDiv = document.getElementById('workflowUpdateWarning');
        if (warningDiv) {
            warningDiv.style.display = 'none';
        }
    }

    // ========================================
    // TASK NOTIFICATION BAR (Phase 2)
    // ========================================

    /**
     * Check for active task with retry mechanism (for workflow start)
     * Retries up to 5 times with increasing delays
     */
    async function checkAndShowActiveTaskWithRetry(maxRetries = 5, initialDelay = 500) {
        for (let attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                console.log(`Checking for active task (attempt ${attempt}/${maxRetries})...`);
                const result = await checkAndShowActiveTask();

                // If task was found and displayed, stop retrying
                const notificationContainer = document.getElementById('workflowTaskNotification');
                if (notificationContainer && notificationContainer.style.display !== 'none') {
                    console.log('Active task found and displayed successfully');
                    return true;
                }

                // If this is not the last attempt, wait before retrying
                if (attempt < maxRetries) {
                    const delay = initialDelay * attempt; // Exponential backoff
                    console.log(`No active task found yet, retrying in ${delay}ms...`);
                    await new Promise(resolve => setTimeout(resolve, delay));
                } else {
                    console.warn('No active task found after all retry attempts');
                }
            } catch (error) {
                console.error(`Error on attempt ${attempt}:`, error);
                if (attempt < maxRetries) {
                    const delay = initialDelay * attempt;
                    await new Promise(resolve => setTimeout(resolve, delay));
                }
            }
        }
        return false;
    }

    /**
     * Check for active task and show notification if user has matching role
     * Returns true if task was found and displayed, false otherwise
     */
    async function checkAndShowActiveTask() {
        // Early return if workflow is already marked as completed to prevent refresh loop
        if (workflowCompleted) {
            console.log('⚠️ Workflow already marked as completed, skipping checkAndShowActiveTask');
            stopTaskPolling(); // Ensure polling is stopped
            return false;
        }
        
        console.log('═══════════════════════════════════════════════════════════════');
        console.log('🔄 [ACTIVE TASK CHECK] Starting checkAndShowActiveTask');
        console.log('📋 Current Change Request ID:', currentChangeRequestId);
        console.log('═══════════════════════════════════════════════════════════════');

        try {
            // Get workflow instance to find active tasks
            const response = await fetch(`/api/workflow_instances/by-cr/${currentChangeRequestId}`, {
                method: 'GET',
                credentials: 'include'
            });

            console.log('Workflow instance response status:', response.status, response.statusText);

            // 404 is expected when workflow hasn't started yet - handle gracefully
            if (response.status === 404) {
                // No workflow instance yet - this is normal, don't log as error
                hideTaskNotification();
                stopTaskPolling(); // Stop polling when no workflow exists
                return false;
            }
            
            if (!response.ok) {
                // Other errors - log but don't show to user
                console.debug('Error fetching workflow instance (status:', response.status, ')');
                hideTaskNotification();
                stopTaskPolling();
                return false;
            }

            const workflowData = await response.json();
            console.log('Workflow data received:', workflowData);
            const instance = workflowData.instance;

            if (!instance || !instance.id) {
                console.log('❌ Workflow instance data is invalid:', instance);
                hideTaskNotification();
                stopTaskPolling();
                return false;
            }

            console.log('✅ Workflow instance found:', {
                id: instance.id,
                status: instance.status,
                endedAt: instance.endedAt,
                changeRequestId: instance.changeRequestId
            });

            // Check if workflow is completed FIRST before loading task history
            // Status can be: 'Enabled', 'Disabled', 'Paused', 'Completed'
            if (instance.status === 'Disabled' || instance.status === 'Completed' || instance.endedAt) {
                console.log('⚠️ Workflow is completed or disabled. Status:', instance.status, 'EndedAt:', instance.endedAt);
                workflowCompleted = true; // Mark workflow as completed
                hideTaskNotification();
                stopTaskPolling(); // Stop polling when workflow is completed
                // Re-enable Start Workflow button when workflow reaches End
                enableStartWorkflowButton();
                // Highlight end event in green when workflow is completed
                await highlightActiveTasks();
                // Load task history only (no full reload to avoid refresh loop)
                await loadTaskHistory(instance.id);
                return false;
            } else {
                workflowCompleted = false; // Reset if workflow is not completed
            }

            // ALWAYS load task history if we have an instance (only if not completed)
            // This ensures the "Discussion" section is visible to everyone
            await loadTaskHistory(instance.id);

            // Check if workflow is paused
            if (instance.status === 'Paused') {
                console.log('⚠️ Workflow is paused. Status:', instance.status);
                hideTaskNotification();
                stopTaskPolling(); // Stop polling when workflow is paused
                return false;
            }

            // Workflow is active if status is 'Enabled' (and not ended)
            // 'Enabled' is the active/running state for workflow instances

            // Get all tasks for this instance to handle parallel tasks
            console.log(`Fetching all tasks for instance ${instance.id}...`);
            const tasksResponse = await fetch(`/api/workflow_instances/${instance.id}/tasks`, {
                method: 'GET',
                credentials: 'include'
            });

            console.log('Tasks response status:', tasksResponse.status, tasksResponse.statusText);

            if (!tasksResponse.ok) {
                if (tasksResponse.status === 404) {
                    console.log('❌ No tasks found for workflow instance (404)');
                } else {
                    console.error('❌ Error fetching tasks:', tasksResponse.status, tasksResponse.statusText);
                    try {
                        const errorData = await tasksResponse.json();
                        console.error('Error details:', errorData);
                    } catch (e) {
                        console.error('Could not parse error response');
                    }
                }
                hideTaskNotification();
                return false;
            }

            const tasksData = await tasksResponse.json();
            const tasks = tasksData.tasks || [];
            console.log(`✅ Found ${tasks.length} total tasks`);

            // Filter to get only pending/in-progress tasks
            const activeTasks = tasks.filter(task =>
                task.status === 'Pending' || task.status === 'InProgress'
            );

            if (activeTasks.length === 0) {
                console.log('❌ No active tasks found');
                hideTaskNotification();
                stopTaskPolling(); // Stop polling when no active tasks
                // Check if workflow reached end event (no active tasks = workflow completed)
                // Re-enable Start Workflow button when workflow reaches End
                enableStartWorkflowButton();
                // If workflow instance exists and has no active tasks, it likely reached the end
                // Update instance status check and highlight end event
                if (instance && (instance.status === 'Enabled' || instance.status === 'Disabled')) {
                    // Workflow likely completed - highlight end event
                    await highlightActiveTasks();
                }
                return false;
            }

            // Get current user info first to check roles
            console.log('Fetching current user info to check roles...');
            const meResponse = await fetch('/api/me', {
                method: 'GET',
                credentials: 'include'
            });

            if (!meResponse.ok) {
                console.log('❌ Failed to fetch current user');
                hideTaskNotification();
                return false;
            }

            const userData = await meResponse.json();
            const userId = userData.id || userData.ID;
            console.log('Current user ID:', userId);

            // Get stakeholders to check user roles
            console.log(`Fetching stakeholders for CR ${currentChangeRequestId}...`);
            const stakeholdersResponse = await fetch(`/api/cr-stakeholders?crId=${currentChangeRequestId}`, {
                method: 'GET',
                credentials: 'include'
            });

            if (!stakeholdersResponse.ok) {
                console.log('❌ Failed to fetch stakeholders');
                hideTaskNotification();
                return false;
            }

            let stakeholders = await stakeholdersResponse.json();
            if (!Array.isArray(stakeholders)) {
                stakeholders = [];
            }
            console.log(`Found ${stakeholders.length} stakeholders`);

            // Find user's roles from stakeholders
            const userRoles = new Set();
            for (const stakeholder of stakeholders) {
                const stakeholderUserId = stakeholder.personId || stakeholder.User_ID || stakeholder.userId || stakeholder.user_ID;
                if (stakeholderUserId == userId || stakeholderUserId === userId) {
                    const role = stakeholder.roleName || stakeholder.role_name || stakeholder.role;
                    if (role) {
                        const normalizedRole = normalizeRoleName(role);
                        userRoles.add(normalizedRole);
                        console.log(`User has role: ${role} (normalized: ${normalizedRole})`);
                    }
                }
            }

            // Special handling: Check if user is the creator (Requestor role)
            // Fetch change request to check creator
            try {
                const crResponse = await fetch(`/api/changerequests/${currentChangeRequestId}`, {
                    method: 'GET',
                    credentials: 'include'
                });
                if (crResponse.ok) {
                    const changeRequest = await crResponse.json();
                    const creatorId = changeRequest.createdBy || changeRequest.created_by;
                    if (creatorId != null && (creatorId == userId || creatorId === userId)) {
                        userRoles.add('REQUESTOR');
                        console.log(`User is the creator - added REQUESTOR role`);
                    }
                }
            } catch (err) {
                console.warn('Could not fetch change request to check creator:', err);
            }

            console.log(`User has ${userRoles.size} roles:`, Array.from(userRoles));

            // Find the first active task that matches user's role
            let matchingTask = null;
            for (const task of activeTasks) {
                const taskRole = task.role || task.roleName;
                if (taskRole) {
                    const normalizedTaskRole = normalizeRoleName(taskRole);
                    if (userRoles.has(normalizedTaskRole)) {
                        matchingTask = task;
                        break;
                    }
                }
            }

            if (!matchingTask) {
                console.log('❌ No active task found that matches user roles');
                console.log('Active tasks roles:', activeTasks.map(t => t.role || t.roleName));
                console.log('User roles:', Array.from(userRoles));
                hideTaskNotification();
                // Only start polling if there are active tasks AND workflow is not completed
                // But don't poll if workflow is completed or no active tasks at all
                if (activeTasks.length > 0 && !workflowCompleted) {
                    startTaskPolling();
                } else {
                    stopTaskPolling(); // No active tasks or workflow completed, stop polling
                }
                return false;
            }

            console.log('✅ Matching task found:', matchingTask);

            // Get full task details from active-task endpoint to get isGatewayTask and decisionOptions
            let taskData = {
                id: matchingTask.taskId || matchingTask.id,
                taskId: matchingTask.taskId || matchingTask.id,
                name: matchingTask.taskName || matchingTask.name,
                taskName: matchingTask.taskName || matchingTask.name,
                role: matchingTask.role || matchingTask.roleName,
                roleName: matchingTask.role || matchingTask.roleName,
                assignedAt: matchingTask.assignedAt,
                dueAt: matchingTask.dueAt || matchingTask.dueDate,
                isOverdue: matchingTask.isOverdue,
                taskDescription: matchingTask.taskDescription || matchingTask.taskName || matchingTask.name
            };

            // Fetch full task details from active-task endpoint to get gateway info
            try {
                const activeTaskResponse = await fetch(`/api/workflow_instances/${instance.id}/active-task`, {
                    method: 'GET',
                    credentials: 'include'
                });
                
                if (activeTaskResponse.ok) {
                    const activeTaskData = await activeTaskResponse.json();
                    
                    // Merge gateway-specific data
                    if (activeTaskData.isGatewayTask !== undefined) {
                        taskData.isGatewayTask = activeTaskData.isGatewayTask;
                    }
                    if (activeTaskData.decisionOptions) {
                        taskData.decisionOptions = activeTaskData.decisionOptions;
                    }
                    // Also merge other fields that might be missing
                    if (activeTaskData.taskDescription) {
                        taskData.taskDescription = activeTaskData.taskDescription;
                    }
                }
            } catch (error) {
                console.error('Error fetching active-task endpoint:', error);
            }

            await renderTaskNotificationPanel(taskData);
            // Highlight active tasks in diagram
            await highlightActiveTasks();
            // Start polling for new tasks only if workflow is not completed
            if (!workflowCompleted) {
                startTaskPolling();
            } else {
                stopTaskPolling(); // Ensure polling is stopped if workflow completed
            }
            console.log('═══════════════════════════════════════════════════════════════');
            console.log('✅ [ACTIVE TASK CHECK] checkAndShowActiveTask SUCCESS');
            console.log('📊 Active task displayed:', taskData.taskName);
            console.log('═══════════════════════════════════════════════════════════════');
            return true;

        } catch (error) {
            console.error('❌ ERROR in checkAndShowActiveTask:', error);
            console.error('Error stack:', error.stack);
            hideTaskNotification();
            stopTaskPolling(); // Stop polling on error
            return false;
        }
    }

    /**
     * Start polling for active tasks (useful when workflow is running)
     * Polls every 30 seconds to check for new tasks
     */
    function startTaskPolling() {
        // Clear existing polling if any
        stopTaskPolling();

        // Poll every 30 seconds (reduced frequency to avoid excessive requests)
        taskPollingInterval = setInterval(async () => {
            try {
                // Don't poll if workflow is completed
                if (workflowCompleted) {
                    stopTaskPolling();
                    return;
                }
                await checkAndShowActiveTask();
            } catch (error) {
                console.error('Error in task polling:', error);
                // Stop polling on error to avoid continuous failures
                stopTaskPolling();
            }
        }, 30000); // 30 seconds (was incorrectly set to 900000 = 15 minutes)

        console.log('Started task polling (every 30 seconds)');
    }

    /**
     * Stop polling for active tasks
     */
    function stopTaskPolling() {
        if (taskPollingInterval) {
            clearInterval(taskPollingInterval);
            taskPollingInterval = null;
            console.log('Stopped task polling');
        }
    }

    /**
     * Stakeholder Resolver - Service مركزي
     * يحل task assignee بناءً على role و workflow instance
     */
    async function resolveTaskAssignee(task, workflowInstance, currentUser) {
        try {
            // 1. استخرج role من task BPMN properties
            const taskRole = task.role || task.roleName;
            if (!taskRole) {
                console.warn('Task has no role:', task);
                return null;
            }
            
            const normalizedTaskRole = normalizeRoleName(taskRole);
            console.log('Resolving assignee for role:', normalizedTaskRole);
            
            // 2. إذا role = "Requestor"
            if (normalizedTaskRole === 'REQUESTOR') {
                // احصل على changeRequest.createdBy
                const changeRequestId = workflowInstance?.changeRequestId || currentChangeRequestId;
                if (!changeRequestId) {
                    console.warn('No change request ID found');
                    return null;
                }
                
                const changeRequest = await fetchChangeRequest(changeRequestId);
                if (!changeRequest) {
                    console.warn('Change request not found:', changeRequestId);
                    return null;
                }
                
                const creatorId = changeRequest.createdBy || changeRequest.created_by;
                console.log('Requestor role - Creator ID:', creatorId, 'Current User:', currentUser?.id);
                
                // تحقق من currentUser.id === createdBy
                if (currentUser && currentUser.id == creatorId) {
                    return creatorId;
                }
                
                return null;
            }
            
            // 3. إذا role عادي - ابحث في stakeholders
            const changeRequestId = workflowInstance?.changeRequestId || currentChangeRequestId;
            const stakeholders = await fetchStakeholders(changeRequestId);
            
            for (const stakeholder of stakeholders) {
                const stakeholderUserId = stakeholder.personId || stakeholder.User_ID || stakeholder.userId;
                const stakeholderRole = stakeholder.roleName || stakeholder.role_name || stakeholder.role;
                
                if (stakeholderRole) {
                    const normalizedStakeholderRole = normalizeRoleName(stakeholderRole);
                    if (normalizedTaskRole === normalizedStakeholderRole) {
                        return stakeholderUserId;
                    }
                }
            }
            
            return null;
        } catch (error) {
            console.error('Error resolving task assignee:', error);
            return null;
        }
    }

    async function fetchChangeRequest(crId) {
        try {
            const response = await fetch(`/api/changerequests/${crId}`, {
                credentials: 'include'
            });
            if (response.ok) {
                return await response.json();
            }
        } catch (error) {
            console.error('Error fetching change request:', error);
        }
        return null;
    }

    async function fetchStakeholders(crId) {
        try {
            const response = await fetch(`/api/cr-stakeholders?crId=${crId}`, {
                credentials: 'include'
            });
            if (response.ok) {
                let data = await response.json();
                if (data && typeof data === 'object' && !Array.isArray(data)) {
                    data = data.stakeholders || data.data || Object.values(data);
                }
                return Array.isArray(data) ? data : [];
            }
        } catch (error) {
            console.error('Error fetching stakeholders:', error);
        }
        return [];
    }

    /**
     * Check if current user has the required role
     */
    async function checkUserHasRole(roleName) {
        console.log('=== checkUserHasRole START ===');
        console.log('Checking for role:', roleName);

        try {
            // Normalize role
            const normalizedTaskRole = normalizeRoleName(roleName);
            
            // إذا role = "Requestor"
            if (normalizedTaskRole === 'REQUESTOR') {
                // احصل على change request creator
                const changeRequest = await fetchChangeRequest(currentChangeRequestId);
                if (!changeRequest) {
                    console.log('❌ Change request not found');
                    return false;
                }
                
                const creatorId = changeRequest.createdBy || changeRequest.created_by;
                
                // احصل على current user
                const meResponse = await fetch('/api/me', { credentials: 'include' });
                if (!meResponse.ok) {
                    console.log('❌ Failed to fetch current user');
                    return false;
                }
                
                const userData = await meResponse.json();
                const userId = userData.id || userData.ID;
                
                console.log('Requestor check - Creator ID:', creatorId, 'Current User ID:', userId);
                
                if (userId == creatorId) {
                    console.log('✅ User is the creator (Requestor)');
                    console.log('=== checkUserHasRole SUCCESS ===');
                    return true;
                } else {
                    console.log('❌ User is not the creator');
                    console.log('=== checkUserHasRole FAILED ===');
                    return false;
                }
            }
            
            // Role عادي - استخدم المنطق الموجود
            // Get stakeholders for change request
            console.log(`Fetching stakeholders for CR ${currentChangeRequestId}...`);
            const response = await fetch(`/api/cr-stakeholders?crId=${currentChangeRequestId}`, {
                method: 'GET',
                credentials: 'include'
            });

            console.log('Stakeholders response status:', response.status);

            if (!response.ok) {
                console.log('❌ Failed to fetch stakeholders (status:', response.status, ')');
                return false;
            }

            let stakeholders = await response.json();
            console.log('Stakeholders data received:', stakeholders);

            // Handle case where stakeholders might be an object with a stakeholders property
            if (stakeholders && typeof stakeholders === 'object' && !Array.isArray(stakeholders)) {
                console.log('Stakeholders is an object, converting to array...');
                if (stakeholders.stakeholders && Array.isArray(stakeholders.stakeholders)) {
                    stakeholders = stakeholders.stakeholders;
                } else if (stakeholders.data && Array.isArray(stakeholders.data)) {
                    stakeholders = stakeholders.data;
                } else {
                    // Convert object to array if it's an object with numeric keys
                    stakeholders = Object.values(stakeholders);
                }
            }

            // Ensure stakeholders is an array
            if (!Array.isArray(stakeholders)) {
                console.warn('❌ Stakeholders is not an array:', stakeholders);
                return false;
            }

            console.log(`Found ${stakeholders.length} stakeholders`);

            // Get current user ID
            console.log('Fetching current user info...');
            const meResponse = await fetch('/api/me', {
                method: 'GET',
                credentials: 'include'
            });

            if (!meResponse.ok) {
                console.log('❌ Failed to fetch current user (status:', meResponse.status, ')');
                return false;
            }

            const userData = await meResponse.json();
            const userId = userData.id || userData.ID;
            console.log('Current user ID:', userId);

            console.log('Normalized task role:', normalizedTaskRole);

            // Check if user is a stakeholder with matching role
            console.log('Checking stakeholders for matching role...');
            for (const stakeholder of stakeholders) {
                // Handle different field names from API (personId, User_ID, userId, user_ID)
                const stakeholderUserId = stakeholder.personId || stakeholder.User_ID || stakeholder.userId || stakeholder.user_ID;
                // Handle different field names for role (roleName, role_name, role)
                const stakeholderRole = stakeholder.roleName || stakeholder.role_name || stakeholder.role;
                const normalizedStakeholderRole = stakeholderRole ? normalizeRoleName(stakeholderRole) : '';

                console.log('Checking stakeholder:', {
                    personId: stakeholder.personId,
                    userId: stakeholderUserId,
                    role: stakeholderRole,
                    normalizedRole: normalizedStakeholderRole,
                    fullStakeholder: stakeholder
                });

                // Use == instead of === to handle string/number comparison
                if (stakeholderUserId == userId || stakeholderUserId === userId) {
                    console.log('✅ User ID matches!');
                    if (stakeholderRole) {
                        if (normalizedTaskRole === normalizedStakeholderRole) {
                            console.log('✅ Role matches! User has required role.');
                            console.log('=== checkUserHasRole SUCCESS ===');
                            return true;
                        } else {
                            console.log('❌ Role does not match:', normalizedTaskRole, 'vs', normalizedStakeholderRole);
                        }
                    } else {
                        console.log('⚠️ Stakeholder has no role');
                    }
                } else {
                    console.log(`User ID mismatch: ${stakeholderUserId} (${typeof stakeholderUserId}) vs ${userId} (${typeof userId})`);
                }
            }

            console.log('❌ No matching stakeholder found with required role');
            console.log('=== checkUserHasRole FAILED ===');
            return false;
        } catch (error) {
            console.error('❌ ERROR in checkUserHasRole:', error);
            console.error('Error stack:', error.stack);
            return false;
        }
    }

    /**
     * Normalize role name: remove prefix (e.g., "3:"), uppercase, replace spaces and dashes with underscores
     * CRITICAL: Must match normalization in backend (WorkflowValidationServlet.java)
     */
    function normalizeRoleName(roleName) {
        if (!roleName) return '';
        
        let cleanRole = roleName.trim();
        
        // Remove prefix like "3:", "13:", or "R01:" if present
        if (cleanRole.includes(':')) {
            cleanRole = cleanRole.substring(cleanRole.indexOf(':') + 1);
        }
        
        return cleanRole.trim()
            .toUpperCase()
            .replace(/\s+/g, '_')  // Replace spaces with underscores
            .replace(/-/g, '_');    // Replace dashes with underscores
    }

    /**
     * Convert normalized role name to human-readable format
     * Example: "DATASET_OWNER" -> "Dataset Owner"
     */
    function formatRoleName(roleName) {
        if (!roleName) return '';
        
        // Remove prefix if present
        let cleanRole = roleName.trim();
        if (cleanRole.includes(':')) {
            cleanRole = cleanRole.substring(cleanRole.indexOf(':') + 1);
        }
        
        // Convert from normalized format (UPPER_CASE) to readable format
        return cleanRole
            .toLowerCase()
            .split('_')
            .map(word => word.charAt(0).toUpperCase() + word.slice(1))
            .join(' ');
    }

    /**
     * Get stakeholders matching a specific role for the change request
     */
    async function getStakeholdersByRole(changeRequestId, taskRole) {
        if (!changeRequestId || !taskRole) return [];
        
        try {
            const response = await fetch(`/api/cr-stakeholders?crId=${changeRequestId}`, {
                method: 'GET',
                credentials: 'include'
            });
            
            if (!response.ok) {
                console.warn('Failed to fetch stakeholders for role matching');
                return [];
            }
            
            let stakeholders = await response.json();
            if (!Array.isArray(stakeholders)) {
                stakeholders = [];
            }
            
            // Normalize task role for comparison
            const normalizedTaskRole = normalizeRoleName(taskRole);
            
            // Filter stakeholders by matching role
            const matchingStakeholders = [];
            for (const stakeholder of stakeholders) {
                const stakeholderRole = stakeholder.roleName || stakeholder.role_name || stakeholder.role;
                if (stakeholderRole) {
                    const normalizedStakeholderRole = normalizeRoleName(stakeholderRole);
                    if (normalizedStakeholderRole === normalizedTaskRole) {
                        const personId = stakeholder.personId || stakeholder.person_id;
                        let personName = stakeholder.personName || stakeholder.person_name;
                        
                        // If personName is not available, try to construct from firstName/lastName
                        if (!personName || personName.trim() === '') {
                            const firstName = stakeholder.firstName || stakeholder.first_name || '';
                            const lastName = stakeholder.lastName || stakeholder.last_name || '';
                            personName = `${firstName} ${lastName}`.trim();
                        }
                        
                        // If still no name and we have personId, fetch it
                        if ((!personName || personName.trim() === '' || personName === 'Unknown User') && personId) {
                            personName = await getUserName(personId);
                        }
                        
                        if (personId && personName) {
                            matchingStakeholders.push({
                                personId: personId,
                                personName: personName
                            });
                        }
                    }
                }
            }
            
            return matchingStakeholders;
        } catch (error) {
            console.warn('Error fetching stakeholders by role:', error);
            return [];
        }
    }

    /**
     * Inject CSS styles for notification panel
     */
    function injectNotificationStyles() {
        if (document.getElementById('workflowTaskNotificationStyles')) {
            return; // Styles already injected
        }

        const style = document.createElement('style');
        style.id = 'workflowTaskNotificationStyles';
        style.textContent = `
            .task-notification-panel {
                margin-bottom: 1.5rem;
                border-radius: 4px;
                overflow: hidden;
                box-shadow: 0 2px 4px rgba(0,0,0,0.1);
            }
            .notification-header {
                background-color: #28a745;
                color: white;
                padding: 0.75rem 1rem;
                display: flex;
                align-items: center;
                gap: 0.5rem;
            }
            .notification-header i {
                font-size: 1.25rem;
            }
            .notification-header .go-to-details {
                margin-left: auto;
                color: white;
                text-decoration: underline;
            }
            .notification-header .go-to-details:hover {
                text-decoration: none;
            }
            .notification-card {
                background: white;
                padding: 1.5rem;
                border: 1px solid #e5e7eb;
            }
            .task-title-section {
                display: flex;
                justify-content: space-between;
                align-items: baseline;
                margin-bottom: 1rem;
            }
            .task-title-section h3 {
                margin: 0;
                font-size: 1.5rem;
                font-weight: 600;
            }
            .task-timestamp {
                color: #6b7280;
                font-size: 0.875rem;
            }
            .task-description {
                margin-bottom: 1rem;
                color: #374151;
                line-height: 1.6;
            }
            .task-metadata {
                display: flex;
                gap: 1.5rem;
                margin-bottom: 1.5rem;
                color: #6b7280;
                font-size: 0.875rem;
            }
            .task-actions {
                display: flex;
                gap: 0.75rem;
                margin-bottom: 1rem;
            }
            .btn-decision, .btn-complete {
                padding: 0.5rem 1rem;
                border: none;
                border-radius: 4px;
                cursor: pointer;
                font-weight: 500;
                transition: background-color 0.2s;
                background-color: #0d9488;
                color: white;
            }
            .btn-decision:hover, .btn-complete:hover {
                background-color: #0f766e;
            }
            .task-comment-section {
                display: flex;
                gap: 0.5rem;
            }
            .comment-input {
                flex: 1;
                padding: 0.5rem;
                border: 1px solid #d1d5db;
                border-radius: 4px;
            }
            .btn-comment {
                padding: 0.5rem 1rem;
                background-color: #0d9488;
                color: white;
                border: none;
                border-radius: 4px;
                cursor: pointer;
            }
            .btn-comment:hover {
                background-color: #0f766e;
            }
            .sla-badge {
                display: inline-block;
                padding: 0.25rem 0.5rem;
                border-radius: 4px;
                font-size: 0.75rem;
                font-weight: 600;
                margin-left: 0.5rem;
            }
            .sla-badge.overdue {
                background-color: #dc3545;
                color: white;
            }
            .sla-badge.escalated {
                background-color: #fd7e14;
                color: white;
            }
            .due-date-status {
                color: #6b7280;
                font-size: 0.875rem;
                margin-left: 0.5rem;
            }
        `;
        document.head.appendChild(style);
    }

    /**
     * Calculate and display due date status (e.g., "Due in 2 days" or "Overdue by 1 day")
     */
    function calculateDueDateStatus(task) {
        const dueDate = task.dueAt || task.dueDate;
        if (!dueDate) return '';

        const now = new Date();
        const due = new Date(dueDate);
        const diffMs = due - now;
        const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));
        const diffHours = Math.floor(diffMs / (1000 * 60 * 60));

        if (diffMs < 0) {
            // Overdue
            const overdueDays = Math.abs(diffDays);
            const overdueHours = Math.abs(diffHours);
            if (overdueDays > 0) {
                return `<span class="due-date-status">(Overdue by ${overdueDays} day${overdueDays > 1 ? 's' : ''})</span>`;
            } else {
                return `<span class="due-date-status">(Overdue by ${overdueHours} hour${overdueHours > 1 ? 's' : ''})</span>`;
            }
        } else {
            // Not yet due
            if (diffDays > 0) {
                return `<span class="due-date-status">(Due in ${diffDays} day${diffDays > 1 ? 's' : ''})</span>`;
            } else if (diffHours > 0) {
                return `<span class="due-date-status">(Due in ${diffHours} hour${diffHours > 1 ? 's' : ''})</span>`;
            } else {
                return `<span class="due-date-status">(Due soon)</span>`;
            }
        }
    }

    /**
     * Render SLA badges (Overdue, Escalated)
     */
    function renderSlaBadges(task) {
        let badges = '';

        if (task.isOverdue) {
            badges += `<span class="sla-badge overdue">Overdue</span>`;
        }

        if (task.escalatedAt) {
            badges += `<span class="sla-badge escalated">Escalated</span>`;
        }

        if (badges) {
            return `<div style="margin-top: 0.5rem;">${badges}</div>`;
        }

        return '';
    }

    /**
     * Scroll to a specific task in the discussion section
     * @param {number} taskId - The ID of the task to scroll to
     */
    function scrollToTaskInDiscussion(taskId) {
        if (!taskId) {
            console.warn('Cannot scroll to task: taskId is missing');
            return;
        }

        // Find the task element in the discussion section
        const taskElement = document.getElementById(`task-item-${taskId}`);
        
        if (taskElement) {
            // Scroll to the task element smoothly
            taskElement.scrollIntoView({ 
                behavior: 'smooth', 
                block: 'start' 
            });
            
            // Add a temporary highlight to draw attention
            taskElement.style.transition = 'box-shadow 0.3s ease';
            taskElement.style.boxShadow = '0 0 0 3px rgba(16, 185, 129, 0.3), 0 1px 2px rgba(0,0,0,0.05)';
            
            setTimeout(() => {
                taskElement.style.boxShadow = '0 1px 2px rgba(0,0,0,0.05)';
            }, 2000);
        } else {
            // Task element not found - might not be rendered yet
            // Try to find the discussion section and scroll to it
            const discussionSection = document.getElementById('workflowTaskHistory');
            if (discussionSection) {
                discussionSection.scrollIntoView({ 
                    behavior: 'smooth', 
                    block: 'start' 
                });
                console.warn(`Task element with ID task-item-${taskId} not found, scrolled to discussion section instead`);
            } else {
                console.warn(`Task element with ID task-item-${taskId} and discussion section not found`);
            }
        }
    }

    /**
     * Check if current user can complete a specific task
     * User must be a stakeholder on the CR AND have a role that matches the task role
     */
    async function canUserCompleteTask(task, changeRequestId) {
        if (!task || !changeRequestId || !currentUser) return false;

        try {
            const userId = currentUser.id || currentUser.userId || currentUser.ID;
            const taskRole = task.role || task.roleName;
            
            if (!taskRole) {
                console.log('[Task Complete] Task has no role assigned');
                return false;
            }

            const normalizedTaskRole = normalizeRoleName(taskRole);

            // Special case: Requestor role - check if user is the requester
            if (normalizedTaskRole === 'REQUESTOR') {
                const isRequester = await checkUserIsRequester(changeRequestId);
                if (isRequester) {
                    console.log('[Task Complete] User is requester, can complete Requestor task');
                    return true;
                }
                return false;
            }

            // For other roles: check if user is stakeholder with matching role
            const stakeholdersResponse = await fetch(`/api/cr-stakeholders?crId=${changeRequestId}`, {
                method: 'GET', credentials: 'include'
            });
            if (!stakeholdersResponse.ok) return false;

            let stakeholders = await stakeholdersResponse.json();
            if (!Array.isArray(stakeholders)) stakeholders = [];

            // Check if user is a stakeholder with matching role
            for (const s of stakeholders) {
                const sUserId = s.personId || s.User_ID || s.userId || s.user_ID;
                if (sUserId == userId || sUserId === userId) {
                    const stakeholderRole = s.roleName || s.role_name || s.role;
                    if (stakeholderRole) {
                        const normalizedStakeholderRole = normalizeRoleName(stakeholderRole);
                        if (normalizedTaskRole === normalizedStakeholderRole) {
                            console.log('[Task Complete] User has matching stakeholder role:', normalizedStakeholderRole);
                            return true;
                        }
                    }
                }
            }

            console.log('[Task Complete] User does not have matching stakeholder role for task role:', normalizedTaskRole);
            return false;
        } catch (error) {
            console.error('Error checking if user can complete task:', error);
            return false;
        }
    }

    /**
     * Render task notification panel
     */
    async function renderTaskNotificationPanel(task) {
        console.log('=== renderTaskNotificationPanel START ===');
        console.log('Task data:', task);

        // Ensure styles are injected
        injectNotificationStyles();
        console.log('Notification styles injected');

        // Create or get notification container
        let notificationContainer = document.getElementById('workflowTaskNotification');
        console.log('Existing notification container:', notificationContainer ? 'found' : 'not found');

        if (!notificationContainer) {
            // Create notification container at top of change request view
            const viewContainer = document.getElementById('changeRequestViewContainer');
            if (!viewContainer) {
                console.error('❌ changeRequestViewContainer not found!');
                return;
            }
            console.log('✅ changeRequestViewContainer found, creating notification container...');

            notificationContainer = document.createElement('div');
            notificationContainer.id = 'workflowTaskNotification';
            notificationContainer.className = 'task-notification-panel';
            viewContainer.insertBefore(notificationContainer, viewContainer.firstChild);
            console.log('✅ Notification container created and inserted');
        }

        // Format dates
        const formatDate = (dateStr) => {
            if (!dateStr) return '';
            const date = new Date(dateStr);
            return date.toLocaleDateString('en-GB', {
                day: '2-digit',
                month: 'short',
                year: 'numeric',
                hour: '2-digit',
                minute: '2-digit',
                second: '2-digit'
            });
        };

        // Check if user can complete this task
        const canComplete = await canUserCompleteTask(task, currentChangeRequestId);

        // Build decision buttons
        let decisionButtonsHtml = '';
        const decisionOptions = task.decisionOptions || ['complete'];

        if (task.isGatewayTask) {
            // Gateway task - must show decision options, but only if user has permission
            if (canComplete) {
            if (decisionOptions.length > 0) {
                // Handle both object format {value, label} and string format
                decisionOptions.forEach(option => {
                    let value, label;
                    if (typeof option === 'object' && option !== null) {
                        value = option.value || option;
                        label = option.label || option.value || option;
                    } else {
                        value = option;
                        label = option.charAt(0).toUpperCase() + option.slice(1);
                    }
                    decisionButtonsHtml += `
                        <button class="btn btn-decision" data-decision="${escapeHtml(value)}">${escapeHtml(label)}</button>
                    `;
                });
            } else {
                // Fallback: if no decision options found, show error message
                decisionButtonsHtml = `
                    <div class="alert alert-warning">No decision options available for this gateway task. Please contact administrator.</div>
                `;
            }
        } else {
                // User doesn't have permission - show message instead of buttons
                decisionButtonsHtml = `
                    <div class="alert alert-info">You do not have permission to complete this task. Only stakeholders with the required role can complete tasks.</div>
                `;
            }
        } else {
            // Simple task - show complete button only if user has permission
            if (canComplete) {
            decisionButtonsHtml = `
                <button class="btn btn-complete" id="completeTaskBtn" data-decision="complete">Complete Task</button>
            `;
            } else {
                // User doesn't have permission - show message instead of button
                decisionButtonsHtml = `
                    <div class="alert alert-info">You do not have permission to complete this task. Only stakeholders with the required role can complete tasks.</div>
            `;
            }
        }

        // Build HTML
        const taskId = task.id || task.taskId;
        notificationContainer.innerHTML = `
            <div class="notification-header">
                <i class="fas fa-exclamation-circle"></i>
                <span><strong>YOUR NEXT ACTION</strong></span>
                <a href="#" class="go-to-details" data-task-id="${taskId}">Go to details →</a>
            </div>
            <div class="notification-card">
                <div class="task-title-section">
                    <h3 id="taskNameDisplay">${escapeHtml(task.taskName || 'Unnamed Task')}</h3>
                    <span class="task-timestamp">${formatDate(task.assignedAt)}</span>
                </div>
                <div class="task-description" id="taskDescriptionDisplay">
                    ${escapeHtml(task.taskDescription || task.taskName || 'No description available')}
                </div>
                <div class="task-metadata">
                    ${task.dueAt || task.dueDate ? `
                        <span>
                            <strong>Due Date:</strong> 
                            <span>${formatDate(task.dueAt || task.dueDate)}</span>
                            ${calculateDueDateStatus(task)}
                        </span>
                    ` : ''}
                    ${task.role ? `<span><strong>Role:</strong> <span>${escapeHtml(task.role)}</span></span>` : ''}
                </div>
                ${renderSlaBadges(task)}
                <div class="task-actions" id="taskDecisionButtons">
                    ${decisionButtonsHtml}
                </div>
                <div class="task-comment-section">
                    <input type="text" id="taskCommentInput" placeholder="Enter a comment" class="comment-input">
                    <button class="btn btn-comment" id="submitCommentBtn">Comment</button>
                </div>
            </div>
        `;

        // Add event listeners
        const decisionButtons = notificationContainer.querySelectorAll('.btn-decision, .btn-complete');
        
        // Add click handler for "Go to details" link
        const goToDetailsLink = notificationContainer.querySelector('.go-to-details');
        if (goToDetailsLink) {
            goToDetailsLink.addEventListener('click', (e) => {
                e.preventDefault();
                scrollToTaskInDiscussion(taskId);
            });
        }

        decisionButtons.forEach(btn => {
            btn.addEventListener('click', () => {
                const decision = btn.getAttribute('data-decision') || 'complete';
                const commentInput = document.getElementById('taskCommentInput');
                const comment = commentInput ? commentInput.value : '';
                handleTaskDecision(taskId, decision, comment);
            });
        });

        // Add listener for submitCommentBtn (Comment without completing)
        const submitCommentBtn = document.getElementById('submitCommentBtn');
        if (submitCommentBtn) {
            submitCommentBtn.addEventListener('click', () => {
                const commentInput = document.getElementById('taskCommentInput');
                if (commentInput && commentInput.value.trim()) {
                    submitActiveTaskComment(taskId, commentInput.value.trim());
                    commentInput.value = ''; // Clear input
                }
            });
        }

        // Show notification
        notificationContainer.style.display = 'block';

        // Attach MentionSystem to the new input
        // Using timeout to ensure DOM is ready and layout is calculated
        setTimeout(() => {
            if (typeof attachMentionSystem === 'function') {
                attachMentionSystem('taskCommentInput');
            }
        }, 100);

        console.log('✅ Task notification panel displayed');
        console.log('Notification container element:', notificationContainer);
        console.log('Notification container display style:', notificationContainer.style.display);
        console.log('Notification container computed style:', window.getComputedStyle(notificationContainer).display);
        console.log('=== renderTaskNotificationPanel SUCCESS ===');
    }

    /**
     * Handle task decision/completion
     */
    let isCompletingTask = false; // Flag to prevent double completion
    
    /**
     * Show loading state on complete button
     */
    function showCompleteButtonLoading() {
        const completeBtn = document.getElementById('completeTaskBtn');
        if (completeBtn) {
            completeBtn.disabled = true;
            completeBtn.style.opacity = '0.6';
            completeBtn.style.cursor = 'not-allowed';
            const originalText = completeBtn.innerHTML;
            completeBtn.setAttribute('data-original-text', originalText);
            completeBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Processing...';
        }
        
        // Also disable decision buttons if they exist
        const decisionButtons = document.querySelectorAll('.btn-decision');
        decisionButtons.forEach(btn => {
            btn.disabled = true;
            btn.style.opacity = '0.6';
            btn.style.cursor = 'not-allowed';
        });
    }
    
    /**
     * Hide loading state on complete button
     */
    function hideCompleteButtonLoading() {
        const completeBtn = document.getElementById('completeTaskBtn');
        if (completeBtn) {
            completeBtn.disabled = false;
            completeBtn.style.opacity = '1';
            completeBtn.style.cursor = 'pointer';
            const originalText = completeBtn.getAttribute('data-original-text') || 'Complete Task';
            completeBtn.innerHTML = originalText;
        }
        
        // Also enable decision buttons if they exist
        const decisionButtons = document.querySelectorAll('.btn-decision');
        decisionButtons.forEach(btn => {
            btn.disabled = false;
            btn.style.opacity = '1';
            btn.style.cursor = 'pointer';
        });
    }
    
    async function handleTaskDecision(taskId, decision, comment) {
        // Prevent double completion
        if (isCompletingTask) {
            return;
        }
        
        isCompletingTask = true;
        
        // Show loading indicator
        showCompleteButtonLoading();
        
        try {
            const requestBody = {
                decision: decision,
                comment: comment
            };
            
            const response = await fetch(`/api/workflow_tasks/${taskId}/complete`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                credentials: 'include',
                body: JSON.stringify(requestBody)
            });

            if (!response.ok) {
                const errorData = await response.json().catch(() => ({ error: 'Failed to complete task' }));
                const errorMessage = errorData.error || 'Failed to complete task';
                
                // Check if error is about task already completed
                if (errorMessage.includes("not in a completable state") || errorMessage.includes("Completed")) {
                    // Task was already completed (possibly by another request)
                    console.log('⚠️ Task already completed, checking workflow status...');
                    // Just reload task history and check workflow status
                    const workflowResponse = await fetch(`/api/workflow_instances/by-cr/${currentChangeRequestId}`, {
                        method: 'GET',
                        credentials: 'include'
                    });
                    if (workflowResponse.ok) {
                        const workflowData = await workflowResponse.json();
                        if (workflowData.instance && workflowData.instance.id) {
                            await loadTaskHistory(workflowData.instance.id);
                            // Check if workflow is completed
                            if (workflowData.instance.status === 'Completed' || workflowData.instance.endedAt) {
                                workflowCompleted = true;
                                alert('Workflow completed successfully!');
                                hideTaskNotification();
                                stopTaskPolling();
                                enableStartWorkflowButton();
                            }
                        }
                    }
                    return;
                }
                
                alert('Error: ' + errorMessage);
                return;
            }

            const result = await response.json();
            
            console.log('═══════════════════════════════════════════════════════════════');
            console.log('✅ [TASK COMPLETION] Task completion response received');
            console.log('📊 Workflow completed:', result.workflowCompleted);
            console.log('📊 Next task info:', result.nextTask);
            console.log('📊 Full response:', result);
            console.log('═══════════════════════════════════════════════════════════════');

            // Get workflow instance ID to reload task history
            const workflowResponse = await fetch(`/api/workflow_instances/by-cr/${currentChangeRequestId}`, {
                method: 'GET',
                credentials: 'include'
            });

            let instanceId = null;
            // 404 is expected when workflow hasn't started yet - handle gracefully
            if (workflowResponse.status === 404) {
                // No workflow instance yet - this is normal
            } else if (workflowResponse.ok) {
                const workflowData = await workflowResponse.json();
                if (workflowData.instance && workflowData.instance.id) {
                    instanceId = workflowData.instance.id;
                }
            }

            if (result.workflowCompleted) {
                workflowCompleted = true; // Mark workflow as completed
                alert('Workflow completed successfully!');
                hideTaskNotification();
                stopTaskPolling(); // Stop polling when workflow is completed
                // Show workflow completed status message
                showWorkflowCompletedStatus();
                // Re-enable Start Workflow button when workflow reaches End
                enableStartWorkflowButton();
                // Reload task history only (don't reload full CR data to avoid refresh loop)
                if (instanceId) {
                    await loadTaskHistory(instanceId);
                }
                // Reload classifications section to update Complete button visibility
                // This ensures the button is shown/hidden based on auto-complete setting and stakeholder status
                try {
                    const crResponse = await fetch(`/api/changerequests/${currentChangeRequestId}`, {
                        method: 'GET',
                        credentials: 'include'
                    });
                    if (crResponse.ok) {
                        const updatedCR = await crResponse.json();
                        currentChangeRequest = updatedCR; // Update cached CR data
                        // Find and replace the classifications section
                        const container = document.getElementById('changeRequestViewContainer');
                        if (container) {
                            const rightColumn = container.querySelector('.right-column');
                            if (rightColumn) {
                                const oldSection = rightColumn.querySelector('.view-section:first-child');
                                if (oldSection) {
                                    const newSection = await createClassificationsSection(updatedCR);
                                    rightColumn.replaceChild(newSection, oldSection);
                                }
                            }
                        }
                    }
                } catch (e) {
                    console.warn('Could not reload classifications section:', e);
                }
                // Don't call checkAndShowActiveTask if workflow is completed
            } else {
                workflowCompleted = false; // Reset if workflow is not completed
                console.log('🔄 [TASK COMPLETION] Workflow not completed, checking for next task...');
                
                if (result.nextTask) {
                    // Next task exists - refresh to show it
                    console.log('✅ [TASK COMPLETION] Next task found:', result.nextTask);
                    alert('Task completed successfully!');
                    // Reload task history only (avoid full reload to prevent refresh loop)
                    if (instanceId) {
                        console.log('🔄 [TASK COMPLETION] Reloading task history for instance:', instanceId);
                        await loadTaskHistory(instanceId);
                    }
                    // Check for next task after a short delay
                    console.log('⏳ [TASK COMPLETION] Waiting 500ms for database commit...');
                    await new Promise(resolve => setTimeout(resolve, 500));
                    console.log('🔄 [TASK COMPLETION] Checking for active task...');
                    await checkAndShowActiveTask();
                } else {
                    console.log('⚠️ [TASK COMPLETION] No next task info in response, but workflow not completed');
                    alert('Task completed successfully!');
                    hideTaskNotification();
                    // Reload task history
                    if (instanceId) {
                        console.log('🔄 [TASK COMPLETION] Reloading task history for instance:', instanceId);
                        await loadTaskHistory(instanceId);
                    }
                    // Check for next task after a short delay
                    console.log('⏳ [TASK COMPLETION] Waiting 500ms for database commit...');
                    await new Promise(resolve => setTimeout(resolve, 500));
                    console.log('🔄 [TASK COMPLETION] Checking for active task...');
                    await checkAndShowActiveTask();
                }
            }

        } catch (error) {
            console.error('Error completing task:', error);
            // Check if error is about task already completed
            if (error.message != null && (error.message.includes("not in a completable state") || error.message.includes("Completed"))) {
                console.log('⚠️ Task already completed, checking workflow status...');
                // Just reload task history and check workflow status
                try {
                    const workflowResponse = await fetch(`/api/workflow_instances/by-cr/${currentChangeRequestId}`, {
                        method: 'GET',
                        credentials: 'include'
                    });
                    if (workflowResponse.ok) {
                        const workflowData = await workflowResponse.json();
                        if (workflowData.instance && workflowData.instance.id) {
                            await loadTaskHistory(workflowData.instance.id);
                            // Check if workflow is completed
                            if (workflowData.instance.status === 'Completed' || workflowData.instance.endedAt) {
                                workflowCompleted = true;
                                alert('Workflow completed successfully!');
                                hideTaskNotification();
                                stopTaskPolling();
                                enableStartWorkflowButton();
                            }
                        }
                    }
                } catch (e) {
                    console.error('Error checking workflow status:', e);
                }
            } else {
                alert('Error completing task: ' + error.message);
            }
        } finally {
            // Hide loading indicator
            hideCompleteButtonLoading();
            isCompletingTask = false; // Reset flag
            // Always try to load task history to show the user what happened (only if not already loaded above)
            if (!workflowCompleted) {
                try {
                    const workflowResponse = await fetch(`/api/workflow_instances/by-cr/${currentChangeRequestId}`);
                    if (workflowResponse.ok) {
                        const data = await workflowResponse.json();
                        if (data.instance && data.instance.id) {
                            await loadTaskHistory(data.instance.id);
                        }
                    }
                } catch (e) {
                    console.error('Error loading task history in finally block:', e);
                }
            }
        }
    }

    /**
     * Hide task notification
     */
    function hideTaskNotification() {
        const notificationContainer = document.getElementById('workflowTaskNotification');
        if (notificationContainer) {
            notificationContainer.style.display = 'none';
        }
    }

    /**
     * Escape HTML to prevent XSS
     */
    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    /**
     * Load and display task history for a workflow instance
     */
    async function loadTaskHistory(instanceId) {
        try {
            const response = await fetch(`/api/workflow_instances/${instanceId}/tasks`, {
                method: 'GET',
                credentials: 'include'
            });

            if (!response.ok) {
                console.warn('Failed to load task history:', response.status);
                return;
            }

            const data = await response.json();
            let tasks = data.tasks || [];
            
            console.log('📋 [TASK HISTORY] Loaded', tasks.length, 'tasks');
            tasks.forEach((task, index) => {
                console.log(`📋 [TASK HISTORY] Task ${index + 1}:`, {
                    name: task.taskName || task.name,
                    status: task.status,
                    decision: task.decision || task.Decision,
                    id: task.taskId || task.id
                });
            });

            // Render all tasks (completed AND active) in timeline
            renderTaskHistory(tasks);

        } catch (error) {
            console.error('Error loading task history:', error);
        }
    }

    /**
     * Fetch user name by ID
     */
    const userNamesCache = new Map();
    async function getUserName(userId) {
        if (!userId) return 'Unknown User';
        
        // Check cache first
        if (userNamesCache.has(userId)) {
            const cachedName = userNamesCache.get(userId);
            // Don't return cached "User {id}" if it's a failed lookup - retry
            if (cachedName && (!cachedName.startsWith('User ') || isNaN(cachedName.replace('User ', '')))) {
                return cachedName;
            }
            // If cached name is "User {id}", clear it and retry
            if (cachedName && cachedName.startsWith('User ')) {
                userNamesCache.delete(userId);
            }
        }
        
        try {
            const response = await fetch(`/api/people/${userId}`, {
                method: 'GET',
                credentials: 'include'
            });
            
            if (response.ok) {
                const responseData = await response.json();
                if (!responseData) {
                    console.warn(`[getUserName] Response data is null for ID ${userId}`);
                    userNamesCache.set(userId, 'Unknown User');
                    return 'Unknown User';
                }
                
                // Handle API response format: {success: true, data: {...}} or direct object
                const user = responseData.data || responseData;
                if (!user) {
                    console.warn(`[getUserName] User data is null for ID ${userId}`);
                    userNamesCache.set(userId, 'Unknown User');
                    return 'Unknown User';
                }
                
                // Try all possible field name variations
                const firstName = user.firstName || user.first_name || user.First_Name || user.FirstName || user.FIRST_NAME || '';
                const lastName = user.lastName || user.last_name || user.Last_Name || user.LastName || user.LAST_NAME || '';
                const fullName = `${firstName} ${lastName}`.trim();
                
                if (fullName) {
                    userNamesCache.set(userId, fullName);
                    return fullName;
                }
                
                // Try alternative fields
                const altName = user.name || user.primaryName || user.PrimaryName || user.primary_name || user.Primary_Name || 
                               user.NAME || user.primary_name || '';
                if (altName && altName !== 'Unknown User' && altName.trim() !== '') {
                    userNamesCache.set(userId, altName);
                    return altName;
                }
                
                // If still no name found, log for debugging and try to construct from any available fields
                const availableFields = Object.keys(user);
                console.warn(`[getUserName] No name found for user ID ${userId}, available fields:`, availableFields);
                
                // Try to find any field that might contain a name
                for (const key of availableFields) {
                    const value = user[key];
                    if (value && typeof value === 'string' && value.trim() !== '' && 
                        (key.toLowerCase().includes('name') || key.toLowerCase().includes('first') || key.toLowerCase().includes('last'))) {
                        const trimmedValue = value.trim();
                        if (trimmedValue !== 'Unknown User' && trimmedValue.length > 0) {
                            console.log(`[getUserName] Found name in field '${key}': ${trimmedValue}`);
                            userNamesCache.set(userId, trimmedValue);
                            return trimmedValue;
                        }
                    }
                }
            } else {
                console.warn(`Failed to fetch user name for ID ${userId}: HTTP ${response.status}`);
            }
        } catch (error) {
            console.warn(`Could not fetch user name for ID ${userId}:`, error);
        }
        
        // Fallback - return Unknown User instead of "User {id}"
        userNamesCache.set(userId, 'Unknown User');
        return 'Unknown User';
    }

    /**
     * Render task history in the workflow section
     */
    async function renderTaskHistory(tasks) {
        // Find or create task history container
        let historyContainer = document.getElementById('workflowTaskHistory');

        if (!historyContainer) {
            // Find workflow section
            const workflowSection = document.getElementById('workflowSection');
            if (!workflowSection) return;

            // Create history container
            historyContainer = document.createElement('div');
            historyContainer.id = 'workflowTaskHistory';
            historyContainer.style.marginTop = '1.5rem';
            historyContainer.style.padding = '1rem';
            historyContainer.style.background = '#f9fafb';
            historyContainer.style.borderRadius = '4px';
            historyContainer.style.border = '1px solid #e5e7eb';
            workflowSection.appendChild(historyContainer);
        }

        if (tasks.length === 0) {
            historyContainer.innerHTML = `
                <div class="section-title" style="margin-bottom: 0.5rem;">TASK HISTORY</div>
                <p style="color: #6b7280; margin: 0;">No tasks yet.</p>
            `;
            return;
        }

        // Format date helper
        const formatDate = (dateStr) => {
            if (!dateStr) return '';
            const date = new Date(dateStr);
            return date.toLocaleDateString('en-GB', {
                day: '2-digit',
                month: 'short',
                year: 'numeric',
                hour: '2-digit',
                minute: '2-digit'
            });
        };

        // Build history HTML
        let historyHtml = `
            <div class="section-title" style="margin-bottom: 1rem;">DISCUSSION</div>
        `;

        // Render all timeline items asynchronously
        const timelineItems = await Promise.all(
            tasks.map((task, index) => renderTimelineItem(task, formatDate, index, tasks.length))
        );
        historyHtml += timelineItems.join('');

        historyContainer.innerHTML = historyHtml;

        // Attach listeners to active task comment buttons
        document.querySelectorAll('.active-task-comment-btn').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const taskId = e.target.getAttribute('data-task-id');
                const input = document.getElementById(`activeTaskCommentInput-${taskId}`);
                if (input && input.value.trim()) {
                    submitActiveTaskComment(taskId, input.value.trim());
                }
            });
        });

        // Attach listeners to decision/complete buttons in discussion section
        document.querySelectorAll('.discussion-task-decision-btn, .discussion-task-complete-btn').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const taskId = parseInt(e.target.getAttribute('data-task-id'));
                const decision = e.target.getAttribute('data-decision') || 'complete';
                const input = document.getElementById(`activeTaskCommentInput-${taskId}`);
                const comment = input ? input.value : '';
                
                if (taskId) {
                    handleTaskDecision(taskId, decision, comment);
                }
            });
        });

        // Attach MentionSystem to all task comment inputs (active, completed, canceled)
        setTimeout(() => {
            tasks.forEach(task => {
                const taskId = task.id || task.taskId;
                if (typeof attachMentionSystem === 'function') {
                    const inputId = `activeTaskCommentInput-${taskId}`;
                    attachMentionSystem(inputId);
                    console.log(`Checking mention attachment for ${inputId}:`, document.getElementById(inputId) ? 'FOUND' : 'NOT FOUND');
                } else {
                    console.warn('attachMentionSystem function not defined');
                }
            });
        }, 100);
    }

    /**
     * Render a single timeline item (Active or Completed)
     */
    async function renderTimelineItem(task, formatDate, index, total) {
        const isActive = task.status === 'Pending' || task.status === 'InProgress';
        const taskId = task.id || task.taskId; // fallback

        // Style distinction
        const borderLeftColor = isActive ? '#10b981' : '#3b82f6'; // Green for active, Blue for completed
        const opacity = isActive ? '1' : '0.8';
        const statusIcon = isActive ? '' : '<i class="fas fa-check" style="color: #10b981;"></i> ';

        // Display decision badge if decision exists
        const decision = task.decision || task.Decision; // Try both lowercase and uppercase
        const decisionBadge = decision ?
            `<span style="display: inline-block; padding: 0.25rem 0.5rem; background: #dbeafe; color: #1e40af; border-radius: 4px; font-size: 0.75rem; font-weight: 500; margin-left: 0.5rem;">${escapeHtml(decision)}</span>` : '';

        // Get user names for comments
        const commentsHtml = task.comments && task.comments.length > 0 ?
            await Promise.all(task.comments.map(async (comment) => {
                // Try multiple field names for createdBy
                const createdById = comment.createdBy || comment.created_by || comment.Created_By || comment.createdBy_ID;
                let userName = 'Unknown User';
                if (createdById) {
                    userName = await getUserName(createdById);
                    // Ensure we don't show "User {id}" - show "Unknown User" instead
                    if (userName && userName.startsWith('User ') && !isNaN(userName.replace('User ', ''))) {
                        userName = 'Unknown User';
                    }
                } else {
                    console.warn('Comment missing createdBy field:', comment);
                }
                return `
                    <div style="margin-top: 0.75rem; padding: 0.75rem; background: white; border-left: 3px solid #6b7280; border-radius: 4px;">
                        <div style="font-size: 0.875rem; color: #374151; line-height: 1.5;">${escapeHtml(comment.commentText || comment.text || '')}</div>
                        <div style="font-size: 0.75rem; color: #6b7280; margin-top: 0.5rem;">
                            ${formatDate(comment.createdAt || comment.created_at || comment.Created_At)} by ${escapeHtml(userName)}
                        </div>
                    </div>
                `;
            })).then(htmls => htmls.join('')) : '';

        // SLA badges
        let slaBadges = '';
        if (task.isOverdue) slaBadges += `<span class="sla-badge overdue" style="margin-left: 0.5rem;">Overdue</span>`;
        if (task.escalatedAt) slaBadges += `<span class="sla-badge escalated" style="margin-left: 0.5rem;">Escalated</span>`;

        // Get completed by name if task is completed
        let completedByNameHtml = '';
        if (!isActive && task.completedBy) {
            const completedByName = await getUserName(task.completedBy);
            completedByNameHtml = `${statusIcon} Completed on ${formatDate(task.completedAt)} by ${escapeHtml(completedByName)}`;
        }

        const statusHtml = isActive ?
            `<strong>Status:</strong> <span style="color: #10b981; font-weight: 600;">In Progress</span>` :
            completedByNameHtml;

        // Get task role and format it
        const taskRole = task.role || task.roleName;
        const formattedRole = taskRole ? formatRoleName(taskRole) : '';
        
        // Get stakeholders matching task role
        let assignablePeopleHtml = '';
        if (taskRole && currentChangeRequestId) {
            const matchingStakeholders = await getStakeholdersByRole(currentChangeRequestId, taskRole);
            if (matchingStakeholders.length > 0) {
                const peopleLinks = matchingStakeholders.map(stakeholder => {
                    const personName = stakeholder.personName || 'Unknown';
                    const personId = stakeholder.personId;
                    if (personId) {
                        return renderPersonLink(personName, personId);
                    }
                    return escapeHtml(personName);
                }).join(', ');
                assignablePeopleHtml = `<div style="font-size: 0.875rem; color: #6b7280; margin-top: 0.5rem;"><strong>Assigned to:</strong> ${peopleLinks}</div>`;
            }
        }

        // Due Date display with time and days remaining
        let dueDateHtml = '';
        if (task.dueAt || task.dueDate) {
            const dueDateStatus = calculateDueDateStatus(task);
            dueDateHtml = `<div style="font-size: 0.875rem; color: #6b7280; margin-top: 0.5rem;"><strong>Due Date:</strong> ${formatDate(task.dueAt || task.dueDate)} ${dueDateStatus}</div>`;
        }

        // Build decision buttons for active tasks - only if user has permission
        let decisionButtonsHtml = '';
        if (isActive) {
            // Check if user can complete this task
            const canComplete = await canUserCompleteTask(task, currentChangeRequestId);
            
            if (canComplete) {
            const decisionOptions = task.decisionOptions || ['complete'];
            
            // Check if this is a gateway/decision task:
            // 1. Explicit isGatewayTask flag, OR
            // 2. decisionOptions has more than just 'complete', OR
            // 3. decisionOptions contains decision values like 'rework', 'approved', 'rejected', etc.
            const hasMultipleDecisionOptions = decisionOptions.length > 1 || 
                (decisionOptions.length === 1 && decisionOptions[0] !== 'complete' && decisionOptions[0] !== 'Complete');
            const isDecisionTask = task.isGatewayTask || hasMultipleDecisionOptions ||
                decisionOptions.some(opt => {
                    const value = typeof opt === 'object' ? (opt.value || opt) : opt;
                    const valueStr = String(value).toLowerCase();
                    return valueStr === 'rework' || valueStr === 'approved' || valueStr === 'rejected' || 
                           valueStr === 'approve' || valueStr === 'reject';
                });
            
            if (isDecisionTask) {
                // Gateway/Decision task - show decision options
                if (decisionOptions.length > 0) {
                    decisionOptions.forEach(option => {
                        let value, label;
                        if (typeof option === 'object' && option !== null) {
                            value = option.value || option;
                            label = option.label || option.value || option;
                        } else {
                            value = option;
                            label = option.charAt(0).toUpperCase() + option.slice(1);
                        }
                        decisionButtonsHtml += `
                            <button class="btn btn-decision discussion-task-decision-btn" data-task-id="${taskId}" data-decision="${escapeHtml(value)}" style="padding: 0.5rem 1rem; background-color: #248567; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 0.875rem; font-weight: 500;">${escapeHtml(label)}</button>
                        `;
                    });
                }
            } else {
                // Simple task - just complete button
                decisionButtonsHtml = `
                    <button class="btn btn-complete discussion-task-complete-btn" data-task-id="${taskId}" data-decision="complete" style="padding: 0.5rem 1rem; background-color: #248567; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 0.875rem; font-weight: 500;">Complete Task</button>
                    `;
                }
            } else {
                // User doesn't have permission - show info message
                decisionButtonsHtml = `
                    <div style="padding: 0.5rem 1rem; background-color: #dbeafe; color: #1e40af; border-radius: 4px; font-size: 0.875rem; text-align: center;">You do not have permission to complete this task</div>
                `;
            }
        }

        // Comment Box - Available for all tasks (active, completed, canceled)
        const activeTaskInput = `
            <div class="task-comment-section" style="margin-top: 1rem; border-top: 1px solid #e5e7eb; padding-top: 1rem;">
                <input type="text" id="activeTaskCommentInput-${taskId}" placeholder="Enter a comment" class="comment-input" style="width: 100%; padding: 0.5rem; border: 1px solid #d1d5db; border-radius: 4px; margin-bottom: 0.5rem;">
                <button class="btn btn-comment active-task-comment-btn" data-task-id="${taskId}" style="padding: 0.4rem 1rem; background-color: #0d9488; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 0.875rem;">Comment</button>
            </div>
        `;

        // Get task description - check multiple field names
        const taskDescription = task.taskDescription || task.description || task.taskName || '';

        return `
            <div id="task-item-${taskId}" style="margin-bottom: 1.5rem; padding: 1rem; background: white; border-left: 4px solid ${borderLeftColor}; box-shadow: 0 1px 2px rgba(0,0,0,0.05); border-radius: 0 4px 4px 0; opacity: ${opacity};">
                <div style="display: flex; align-items: flex-start; gap: 1.5rem;">
                    <div style="flex: 1;">
                        <div style="display: flex; align-items: baseline; justify-content: space-between; margin-bottom: 0.5rem;">
                            <div>
                                <h4 style="margin: 0; font-size: 1rem; font-weight: 600; color: #111827;">${escapeHtml(task.taskName || task.name || 'Unnamed Task')}</h4>
                                <div style="font-size: 0.75rem; color: #6b7280; margin-top: 0.25rem;">
                                     ${formatDate(task.assignedAt)}
                                </div>
                            </div>
                            <div>${decisionBadge} ${slaBadges}</div>
                        </div>
                        
                        ${taskDescription ? `<div style="font-size: 0.875rem; color: #4b5563; margin-bottom: 0.5rem;">${escapeHtml(taskDescription)}</div>` : ''}

                        <div style="font-size: 0.875rem; color: #6b7280; margin-bottom: 0.5rem;">
                            ${statusHtml}
                            ${formattedRole ? ` • ${escapeHtml(formattedRole)}` : ''}
                        </div>
                        
                        ${dueDateHtml}
                        ${assignablePeopleHtml}
                        
                        ${commentsHtml ? `<div style="margin-top: 0.75rem;">${commentsHtml}</div>` : ''}
                        ${activeTaskInput}
                    </div>
                    ${isActive && decisionButtonsHtml ? `
                        <div style="display: flex; flex-direction: column; align-items: flex-end; gap: 0.5rem; min-width: 150px;">
                            ${decisionButtonsHtml}
                        </div>
                    ` : ''}
                </div>
            </div>
        `;
    }

    /**
     * Submit comment for active task
     */
    async function submitActiveTaskComment(taskId, commentText) {
        try {
            const response = await fetch(`/api/workflow_tasks/${taskId}/comments`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ comment: commentText })
            });

            if (response.ok) {
                console.log('Comment added');
                // Reload history to show new comment
                const workflowResponse = await fetch(`/api/workflow_instances/by-cr/${currentChangeRequestId}`);
                if (workflowResponse.ok) {
                    const data = await workflowResponse.json();
                    if (data.instance && data.instance.id) {
                        await loadTaskHistory(data.instance.id);
                    }
                }
            } else {
                const err = await response.json();
                alert('Failed to add comment: ' + (err.error || 'Unknown error'));
            }
        } catch (e) {
            console.error('Error submitting comment:', e);
            alert('Error submitting comment');
        }
    }

    // ========================================
    // END WORKFLOW MANAGER INTEGRATION
    // ========================================

    // Initialize when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

    // Cleanup: Stop polling when page is unloaded
    window.addEventListener('beforeunload', () => {
        stopTaskPolling();
    });

    // --- Active Task Banner & Mention System ---

    // Load MentionSystem script
    function loadMentionSystem() {
        if (!window.MentionSystem) {
            const script = document.createElement('script');
            script.src = '/assets/js/mention-system.js';
            script.onload = () => console.log('MentionSystem loaded');
            document.head.appendChild(script);
        }
    }

    // Attach MentionSystem to a textarea
    function attachMentionSystem(elementId) {
        const textarea = document.getElementById(elementId);
        if (textarea && !textarea._mentionSystem && window.MentionSystem) {
            textarea._mentionSystem = new window.MentionSystem(textarea);
            console.log(`MentionSystem attached to ${elementId}`);
        }
    }

    // Get current user ID (reusing logic from canViewChangesToReview)
    async function getCurrentUserId() {
        try {
            if (window.AuthHelper) {
                const user = await window.AuthHelper.getCurrentUser();
                if (user) return user.id || user.userId;
            }

            // Fallback
            const response = await fetch('/api/me');
            if (response.ok) {
                const user = await response.json();
                return user.id || user.userId;
            }
        } catch (e) {
            console.error('Error getting current user:', e);
        }
        return null; // Unable to determine
    }

    // Check for active tasks and display banner
    async function checkActiveTask() {
        if (!currentChangeRequestId) return;
        
        // Don't check if workflow is completed to avoid refresh loop
        if (workflowCompleted) return;

        // Continue checking for active tasks even if CR is cancelled

        try {
            // Get workflow status/instances for this CR
            const response = await fetch(`/api/workflow_instances/by-cr/${currentChangeRequestId}`).catch(err => {
                // Silently handle network errors - don't pollute console
                return { ok: false, status: 0 };
            });
            
            // 404 is expected when workflow hasn't started yet - handle gracefully
            if (response.status === 404) {
                // No workflow instance yet - this is normal, don't log as error
                return;
            }
            if (!response.ok) {
                // Other errors - log at debug level only
                console.debug('Error fetching workflow instance (status:', response.status, ')');
                return;
            }

            const data = await response.json();
            const instance = data.instance;
            const tasks = data.tasks || [];

            if (!instance || instance.status !== 'Enabled') return;
            
            // If workflow is completed, stop checking
            if (instance.status === 'Disabled' || instance.status === 'Completed' || instance.endedAt) {
                workflowCompleted = true;
                return;
            }

            // Find pending tasks
            const activeTasks = tasks.filter(t => t.status === 'Pending' || t.status === 'InProgress');
            if (activeTasks.length === 0) return;

            // Get current user ID
            const currentUserId = await getCurrentUserId();
            if (!currentUserId) return;

            // Check if any active task is assigned to current user
            // We need to match logic: Task can be assigned by Role or User.
            // Phase 4: Task has 'roleName'. We need to check if user has that role.
            // For now, simpler check: User is in the role or specifically assigned?
            // The task object returns 'roleName'.
            // We need to fetch user's roles.

            // Or use the /active-task endpoint if it acts on current user context?
            // Currently WorkflowInstanceServlet /active-task takes instanceId and returns "the active task"
            // It doesn't filter by user.

            // Let's assume we need to check if user has the role.
            // Fetch user roles
            const userRoles = await fetchUserRoles(currentUserId); // Need this helper

            const myTasks = activeTasks.filter(task => {
                // Check if user has the role required by the task
                // activeTask.roleName
                if (!task.roleName) return false;

                // Check if user has this role mapping (Person_Role_Matrix)
                return userRoles.includes(task.roleName);
            });

            if (myTasks.length > 0) {
                renderActiveTaskBanner(myTasks[0]); // Show first one
            }

        } catch (error) {
            console.error('Error checking active tasks:', error);
        }
    }

    // Fetch user roles (simplified helper)
    async function fetchUserRoles(userId) {
        try {
            // We might not have a direct endpoint for "my roles names".
            // But we can check if user is stakeholder with that role on this CR?
            // Or simpler: The notification system knows.
            // Let's rely on `api/changerequests/{id}/stakeholders` which lists user's roles on this CR.
            // Fetch stakeholders using the dedicated servlet
            const response = await fetch(`/api/cr-stakeholders?crId=${currentChangeRequestId}`);
            if (response.ok) {
                let stakeholders = await response.json();
                // Handle wrapper object if present (e.g. { stakeholders: [...] })
                if (stakeholders && !Array.isArray(stakeholders) && stakeholders.stakeholders) {
                    stakeholders = stakeholders.stakeholders;
                }

                if (Array.isArray(stakeholders)) {
                    // Filter where personId == userId
                    const myEntries = stakeholders.filter(s => (s.personId || s.userId) === userId);
                    return myEntries.map(s => s.roleName);
                } else {
                    console.warn('Stakeholders response is not an array:', stakeholders);
                }
            }
        } catch (e) {
            console.error('Error fetching user roles:', e);
        }
        return [];
    }

    // Render Active Task Banner
    function renderActiveTaskBanner(task) {
        const container = document.getElementById('changeRequestViewContainer');
        // Check if banner already exists
        if (document.getElementById('activeTaskBanner')) return;

        const banner = document.createElement('div');
        banner.id = 'activeTaskBanner';
        banner.className = 'active-task-banner';
        banner.style.gridColumn = '1 / -1';
        banner.style.marginBottom = '1.25rem';
        banner.style.padding = '1rem';
        banner.style.backgroundColor = '#ecfdf5'; // Green-50
        banner.style.border = '1px solid #10b981'; // Green-500
        banner.style.borderRadius = '0.5rem';
        banner.style.display = 'flex';
        banner.style.alignItems = 'center';
        banner.style.justifyContent = 'space-between';
        banner.style.boxShadow = '0 2px 4px rgba(0,0,0,0.05)';

        const content = `
            <div style="display: flex; align-items: center; gap: 1rem;">
                <div style="background: #10b981; color: white; width: 40px; height: 40px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 1.25rem;">
                    <i class="fas fa-clipboard-check"></i>
                </div>
                <div>
                    <h4 style="margin: 0; color: #065f46; font-size: 1rem; font-weight: 600;">Active Task: ${escapeHtml(task.taskName || task.name)}</h4>
                    <div style="color: #047857; font-size: 0.875rem; margin-top: 0.25rem;">
                        ${task.isOverdue ? '<span style="color: #dc2626; font-weight: 600;"><i class="fas fa-exclamation-circle"></i> Overdue</span> • ' : ''}
                        Due: ${task.dueAt ? new Date(task.dueAt).toLocaleDateString() : 'No Due Date'}
                    </div>
                </div>
            </div>
            <button class="btn btn-primary" onclick="document.getElementById('workflowSection').scrollIntoView({behavior: 'smooth'})">
                Complete Task <i class="fas fa-arrow-down"></i>
            </button>
         `;
        banner.innerHTML = content;

        // Insert at top
        container.insertBefore(banner, container.firstChild);
    }

    // Auto-init
    loadMentionSystem();

})();





