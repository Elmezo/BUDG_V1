/**
 * Workflow View Component
 * Displays BPMN workflows in read-only mode within the CHANGE tab
 * Filters workflows by module ID and shows only enabled workflows
 */
(function () {
    'use strict';

    const WorkflowView = {
        currentModuleId: null,
        workflows: [],
        selectedWorkflow: null,
        bpmnViewer: null,
        statuses: [],
        lifecycles: [],
        lifecycleModuleId: null,
        hasDragged: false, // Track if user dragged (not just clicked)

        /**
         * Initialize the workflow view component
         * @param {number} moduleId - The ID of the current module
         * @param {string} containerId - The ID of the container element
         */
        initialize: async function (moduleId, containerId) {
            this.currentModuleId = moduleId;

            const container = document.getElementById(containerId);
            if (!container) {
                console.error('Workflow view container not found:', containerId);
                return;
            }

            // Render the component
            this.render(container);

            // Preload reference data then load workflows
            try {
                await this.loadReferenceData();
            } catch (e) {
                console.warn('Failed to preload reference data:', e);
            }

            this.loadWorkflows();
        },

        /**
         * Render the workflow view component HTML
         */
        render: function (container) {
            const t = (k) => (window.I18n && window.I18n.t(k)) || k;
            const w = {
                selectWorkflow: t('workflowView.selectWorkflow'),
                workflowLabel: t('workflowView.workflowLabel'),
                workflowRequired: t('workflowView.workflowRequired'),
                selectPlaceholder: t('workflowView.selectPlaceholder'),
                processName: t('workflowView.processName'),
                description: t('workflowView.description'),
                workflowDiagram: t('workflowView.workflowDiagram'),
                loadingWorkflow: t('workflowView.loadingWorkflow'),
                noWorkflowsAvailable: t('workflowView.noWorkflowsAvailable'),
                elementProperties: t('workflowView.elementProperties'),
                close: t('workflowView.close')
            };
            container.innerHTML = `
                <div class="workflow-view-component">
                    <!-- Workflow Selection Section -->
                    <div class="view-section">
                        <div class="section-title">${w.selectWorkflow}</div>
                        <div class="workflow-select-container">
                            <label for="workflowSelectDropdown">${w.workflowLabel} <span class="required">${w.workflowRequired}</span></label>
                            <select id="workflowSelectDropdown" class="workflow-select">
                                <option value="">${w.selectPlaceholder}</option>
                            </select>
                        </div>
                    </div>

                    <!-- Workflow Details Section (hidden initially) -->
                    <div id="workflowDetailsSection" class="view-section" style="display: none;">
                        <div class="workflow-details">
                            <div class="detail-row">
                                <label>${w.processName}</label>
                                <span id="workflowProcessName" class="detail-value"></span>
                            </div>
                            <div class="detail-row">
                                <label>${w.description}</label>
                                <span id="workflowDescription" class="detail-value"></span>
                            </div>
                        </div>
                    </div>

                    <!-- Workflow Diagram Section (hidden initially) -->
                    <div id="workflowDiagramSection" class="view-section" style="display: none;">
                        <div class="section-title">${w.workflowDiagram}</div>
                        <div class="bpmn-viewer-container">
                            <div id="bpmnViewerCanvas" class="bpmn-canvas"></div>
                            <!-- Properties Panel -->
                            <div id="bpmn-properties-panel" class="bpmn-properties-tabs-links" style="display: none;">
                                <div class="properties-header properties-panel-drag-handle">
                                    <h4>${w.elementProperties}</h4>
                                    <div style="display: flex; align-items: center; gap: 0.5rem;">
                                        <i class="fas fa-grip-vertical" style="color: #999; cursor: move;"></i>
                                        <button id="closePropertiesBtn" class="close-btn" title="${w.close}">
                                            <i class="fas fa-times"></i>
                                        </button>
                                    </div>
                                </div>
                                <div id="propertiesContent" class="properties-content"></div>
                            </div>
                        </div>
                    </div>

                    <!-- Loading State -->
                    <div id="workflowLoadingState" class="loading-state" style="display: none;">
                        <i class="fas fa-spinner fa-spin"></i> ${w.loadingWorkflow}
                    </div>

                    <!-- Empty State -->
                    <div id="workflowEmptyState" class="empty-state" style="display: none;">
                        <i class="fas fa-project-diagram"></i>
                        <p>${w.noWorkflowsAvailable}</p>
                    </div>
                </div>
            `;

            // Add styles
            this.addStyles();

            // Setup event listeners
            this.setupEventListeners(container);
        },

        /**
         * Add component styles
         */
        addStyles: function () {
            if (document.getElementById('workflow-view-styles')) {
                return; // Styles already added
            }

            const styles = document.createElement('style');
            styles.id = 'workflow-view-styles';
            styles.textContent = `
                .workflow-view-component {
                    width: 100%;
                }

                .workflow-view-component .view-section {
                    margin-bottom: 1.5rem;
                }

                .workflow-view-component .section-title {
                    font-size: 0.875rem;
                    font-weight: 600;
                    color: var(--text-secondary, #6c757d);
                    letter-spacing: 0.025em;
                    text-transform: uppercase;
                    margin-bottom: 1rem;
                    padding-bottom: 0.5rem;
                    border-bottom: 1px solid var(--border-color, #e9ecef);
                }

                .workflow-select-container {
                    max-width: 500px;
                }

                .workflow-select-container label {
                    display: block;
                    margin-bottom: 0.5rem;
                    font-weight: 500;
                    color: var(--text-primary, #2c3e50);
                }

                .workflow-select-container .required {
                    color: #dc3545;
                }

                .workflow-select {
                    width: 100%;
                    padding: 0.5rem 0.75rem;
                    border: 1px solid var(--border-color, #e9ecef);
                    border-radius: 4px;
                    font-size: 0.875rem;
                    background: var(--background-primary, #fff);
                    color: var(--text-primary, #2c3e50);
                    cursor: pointer;
                    transition: border-color 0.2s ease;
                }

                .workflow-select:hover {
                    border-color: var(--primary-color, #248567);
                }

                .workflow-select:focus {
                    outline: none;
                    border-color: var(--primary-color, #248567);
                    box-shadow: 0 0 0 3px rgba(36, 133, 103, 0.1);
                }

                .workflow-details {
                    background: var(--background-secondary, #f8f9fa);
                    padding: 1rem;
                    border-radius: 4px;
                    border: 1px solid var(--border-color, #e9ecef);
                }

                .workflow-details .detail-row {
                    display: flex;
                    margin-bottom: 0.75rem;
                }

                .workflow-details .detail-row:last-child {
                    margin-bottom: 0;
                }

                .workflow-details label {
                    font-weight: 600;
                    color: var(--text-secondary, #6c757d);
                    min-width: 150px;
                    margin-right: 1rem;
                }

                .workflow-details .detail-value {
                    color: var(--text-primary, #2c3e50);
                    flex: 1;
                }

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

                .bpmn-properties-tabs-links .properties-header .fa-grip-vertical {
                    cursor: move;
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
                }

                .bpmn-properties-tabs-links .close-btn:hover {
                    color: var(--text-primary, #2c3e50);
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

                .loading-state {
                    text-align: center;
                    padding: 3rem 2rem;
                    color: var(--text-muted, #6b7280);
                }

                .loading-state i {
                    margin-right: 0.5rem;
                    font-size: 1.25rem;
                }

                .empty-state {
                    text-align: center;
                    padding: 3rem 2rem;
                    color: var(--text-muted, #6b7280);
                }

                .empty-state i {
                    font-size: 3rem;
                    margin-bottom: 1rem;
                    display: block;
                    color: #ccc;
                }

                .empty-state p {
                    margin: 0;
                    font-size: 0.875rem;
                }
            `;
            document.head.appendChild(styles);
        },

        /**
         * Setup event listeners
         */
        setupEventListeners: function (container) {
            const workflowSelect = container.querySelector('#workflowSelectDropdown');
            if (workflowSelect) {
                workflowSelect.addEventListener('change', (e) => {
                    const workflowId = parseInt(e.target.value);
                    if (workflowId) {
                        this.loadWorkflowDetails(workflowId);
                    } else {
                        this.hideWorkflowDetails();
                    }
                });
            }

            // Close properties panel button
            const closeBtn = container.querySelector('#closePropertiesBtn');
            if (closeBtn) {
                closeBtn.addEventListener('click', () => {
                    this.hidePropertiesPanel();
                });
            }

            // Initialize drag functionality for properties panel
            setTimeout(() => {
                this.initializePropertiesPanelDrag();
            }, 100);
        },

        /**
         * Load workflows for the current module
         */
        loadWorkflows: async function () {
            const loadingState = document.getElementById('workflowLoadingState');
            const emptyState = document.getElementById('workflowEmptyState');
            const selectContainer = document.querySelector('.workflow-select-container');

            if (loadingState) loadingState.style.display = 'block';
            if (selectContainer) selectContainer.style.display = 'none';

            try {
                const response = await fetch(`/api/process_definitions?entityId=${this.currentModuleId}`);

                if (!response.ok) {
                    throw new Error('Failed to load workflows');
                }

                const allWorkflows = await response.json();

                // Filter to show only enabled workflows
                this.workflows = allWorkflows.filter(wf => wf.status === 'Enabled');

                if (loadingState) loadingState.style.display = 'none';

                if (this.workflows.length === 0) {
                    if (emptyState) emptyState.style.display = 'block';
                    if (selectContainer) selectContainer.style.display = 'none';
                } else {
                    if (emptyState) emptyState.style.display = 'none';
                    if (selectContainer) selectContainer.style.display = 'block';
                    this.populateWorkflowSelect();
                }

            } catch (error) {
                console.error('Error loading workflows:', error);
                if (loadingState) loadingState.style.display = 'none';
                if (emptyState) {
                    emptyState.innerHTML = `
                        <i class="fas fa-exclamation-triangle"></i>
                        <p>Failed to load workflows</p>
                    `;
                    emptyState.style.display = 'block';
                }
            }
        },

        /**
         * Load reference data needed for readable properties
         */
        loadReferenceData: async function () {
            await Promise.all([
                this.loadStatuses(),
                this.loadLifecycles(this.currentModuleId)
            ]);
        },

        /**
         * Populate workflow select dropdown
         */
        populateWorkflowSelect: function () {
            const select = document.getElementById('workflowSelectDropdown');
            if (!select) return;

            // Clear existing options except the first one
            select.innerHTML = '<option value="">Select a workflow...</option>';

            // Add workflow options
            this.workflows.forEach(workflow => {
                const option = document.createElement('option');
                option.value = workflow.id;
                option.textContent = workflow.primaryName;
                select.appendChild(option);
            });
        },

        /**
         * Load and display workflow details
         */
        loadWorkflowDetails: async function (workflowId) {
            const workflow = this.workflows.find(wf => wf.id === workflowId);
            if (!workflow) return;

            this.selectedWorkflow = workflow;

            // Show workflow details
            const detailsSection = document.getElementById('workflowDetailsSection');
            const processNameEl = document.getElementById('workflowProcessName');
            const descriptionEl = document.getElementById('workflowDescription');

            if (processNameEl) processNameEl.textContent = workflow.primaryName;
            if (descriptionEl) descriptionEl.textContent = workflow.description;
            if (detailsSection) detailsSection.style.display = 'block';

            // Load BPMN diagram
            await this.loadBpmnDiagram(workflowId);
        },

        /**
         * Hide workflow details
         */
        hideWorkflowDetails: function () {
            const detailsSection = document.getElementById('workflowDetailsSection');
            const diagramSection = document.getElementById('workflowDiagramSection');

            if (detailsSection) detailsSection.style.display = 'none';
            if (diagramSection) diagramSection.style.display = 'none';

            this.destroyBpmnViewer();
            this.selectedWorkflow = null;
        },

        /**
         * Load and display BPMN diagram
         */
        loadBpmnDiagram: async function (workflowId) {
            const diagramSection = document.getElementById('workflowDiagramSection');

            try {
                // Fetch BPMN XML with cache-busting timestamp to ensure fresh data
                const timestamp = new Date().getTime();
                const response = await fetch(`/api/process_definitions/${workflowId}/bpmn?t=${timestamp}`);

                if (!response.ok) {
                    throw new Error('Failed to load BPMN diagram');
                }

                const data = await response.json();

                if (!data.xml) {
                    throw new Error('No BPMN XML found');
                }

                // Show diagram section
                if (diagramSection) diagramSection.style.display = 'block';

                // Initialize BPMN viewer if not already initialized
                if (!this.bpmnViewer) {
                    this.initializeBpmnViewer();
                }

                // Import BPMN XML
                await this.bpmnViewer.importXML(data.xml);

                // Wait for canvas to be fully rendered before enabling pan/drag
                await new Promise(resolve => {
                    requestAnimationFrame(() => {
                        requestAnimationFrame(resolve);
                    });
                });

                // Additional wait to ensure diagram is fully loaded
                await new Promise(resolve => setTimeout(resolve, 300));

                // Enable pan/drag functionality
                this.enablePanAndDrag();

                // Fit diagram to viewport
                const canvas = this.bpmnViewer.get('canvas');
                canvas.zoom('fit-viewport');

            } catch (error) {
                console.error('Error loading BPMN diagram:', error);
                if (diagramSection) {
                    diagramSection.innerHTML = `
                        <div class="section-title">WORKFLOW DIAGRAM</div>
                        <div class="empty-state">
                            <i class="fas fa-exclamation-triangle"></i>
                            <p>Failed to load workflow diagram</p>
                        </div>
                    `;
                    diagramSection.style.display = 'block';
                }
            }
        },

        /**
         * Initialize BPMN viewer
         */
        initializeBpmnViewer: function () {
            const container = document.getElementById('bpmnViewerCanvas');
            if (!container) {
                console.error('BPMN viewer canvas not found');
                return;
            }

            // Check if BpmnJS is available
            if (typeof BpmnJS === 'undefined') {
                console.error('BpmnJS library not loaded');
                return;
            }

            // Create viewer instance (read-only)
            // Note: bpmn-viewer has pan and zoom enabled by default
            // - Pan: Click and drag on the canvas
            // - Zoom: Use mouse wheel or trackpad
            this.bpmnViewer = new BpmnJS({
                container: container,
                keyboard: {
                    bindTo: document
                }
            });

            // Add click event listener to show properties
            // Only show properties for actual workflow elements, not structural elements like Lane, Collaboration, etc.
            const eventBus = this.bpmnViewer.get('eventBus');
            eventBus.on('element.click', (e) => {
                // Don't show properties if user was dragging
                if (this.hasDragged) {
                    return;
                }
                
                if (e.element && e.element.id) {
                    const elementType = e.element.type;
                    // Ignore structural elements that shouldn't show properties
                    const ignoredTypes = [
                        'bpmn:Lane',
                        'bpmn:Collaboration',
                        'bpmn:Participant',
                        'bpmn:Process',
                        'bpmn:SubProcess',
                        'bpmn:TextAnnotation',
                        'bpmn:Group'
                    ];
                    
                    if (!ignoredTypes.includes(elementType)) {
                        this.showElementProperties(e.element);
                    }
                }
            });
        },

        /**
         * Enable pan and drag functionality for the BPMN diagram
         */
        enablePanAndDrag: function () {
            if (!this.bpmnViewer) return;

            const canvasElement = document.getElementById('bpmnViewerCanvas');
            if (!canvasElement) return;

            const bpmnCanvas = this.bpmnViewer.get('canvas');
            let isDragging = false;
            let lastX = 0;
            let lastY = 0;
            let startX = 0;
            let startY = 0;
            let spacePressed = false;
            const DRAG_THRESHOLD = 5; // Pixels to move before considering it a drag

            // Track space key for pan mode
            document.addEventListener('keydown', (e) => {
                if (e.code === 'Space' && !e.repeat) {
                    spacePressed = true;
                    canvasElement.style.cursor = 'grab';
                    e.preventDefault();
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
                // Allow drag on:
                // 1. Middle mouse button (button === 1)
                // 2. Space + Left mouse button (button === 0)
                // 3. Left mouse button (on any element or empty canvas)
                if (e.button === 1 || (spacePressed && e.button === 0) || e.button === 0) {
                    // Reset drag tracking
                    this.hasDragged = false;
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
                    
                    // Check if movement exceeds threshold to consider it a drag
                    const totalDeltaX = Math.abs(e.clientX - startX);
                    const totalDeltaY = Math.abs(e.clientY - startY);
                    const totalDistance = Math.sqrt(totalDeltaX * totalDeltaX + totalDeltaY * totalDeltaY);
                    
                    if (totalDistance > DRAG_THRESHOLD) {
                        this.hasDragged = true;
                    }
                    
                    bpmnCanvas.scroll({
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
                    
                    // Reset hasDragged after a short delay to allow click event to check it
                    setTimeout(() => {
                        this.hasDragged = false;
                    }, 100);
                    
                    e.preventDefault();
                    e.stopPropagation();
                }
            });

            canvasElement.addEventListener('mouseleave', () => {
                if (isDragging) {
                    isDragging = false;
                    canvasElement.style.cursor = 'default';
                    // Reset hasDragged when leaving canvas
                    setTimeout(() => {
                        this.hasDragged = false;
                    }, 100);
                }
            });

            // Prevent context menu on middle mouse button
            canvasElement.addEventListener('contextmenu', (e) => {
                if (e.button === 1) {
                    e.preventDefault();
                }
            });
        },

        /**
         * Show element properties in panel
         */
        showElementProperties: function (element) {
            const propertiesPanel = document.getElementById('bpmn-properties-panel');
            const propertiesContent = document.getElementById('propertiesContent');

            if (!propertiesPanel || !propertiesContent) return;

            console.log('[WorkflowView] Element clicked:', element);
            console.log('[WorkflowView] Element ID:', element.id);
            console.log('[WorkflowView] Element type:', element.type);
            console.log('[WorkflowView] Business object:', element.businessObject);

            const businessObject = element.businessObject;
            let propertiesHtml = '';

            const savedProperties = this.getElementProperties(businessObject);
            const savedPropKeys = new Set(Object.keys(savedProperties));

            // Element Name
            if (businessObject && businessObject.name) {
                propertiesHtml += `
                    <div class="property-item">
                        <div class="property-label">Name</div>
                        <div class="property-value">${this.escapeHtml(businessObject.name)}</div>
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
                            <div class="property-value">${this.escapeHtml(docText)}</div>
                        </div>
                    `;
                }
            }

            // For User Tasks and Tasks - show assignee, candidate groups, due date, and unlock object
            if (businessObject && (businessObject.$type === 'bpmn:UserTask' || businessObject.$type === 'bpmn:Task')) {
                if (businessObject.assignee && !savedPropKeys.has('assignee')) {
                    propertiesHtml += `
                        <div class="property-item">
                            <div class="property-label">Assignee</div>
                            <div class="property-value">${this.escapeHtml(businessObject.assignee)}</div>
                        </div>
                    `;
                }
                if (businessObject.candidateGroups && !savedPropKeys.has('candidateGroups')) {
                    propertiesHtml += `
                        <div class="property-item">
                            <div class="property-label">Candidate Groups</div>
                            <div class="property-value">${this.escapeHtml(businessObject.candidateGroups)}</div>
                        </div>
                    `;
                }
                // Display Due Date if present
                if (savedProperties.dueDate) {
                    const dueDateValue = savedProperties.dueDate;
                    let days = parseInt(dueDateValue, 10);
                    
                    // If parsing failed, try to extract number from expression like ${dateTime().plusDays(5).toDate()}
                    if (isNaN(days)) {
                        const match = dueDateValue.match(/plusDays\((\d+)\)/);
                        if (match && match[1]) {
                            days = parseInt(match[1], 10);
                        }
                    }
                    
                    const dueDateText = isNaN(days) ? dueDateValue : `${days} day${days !== 1 ? 's' : ''}`;
                    propertiesHtml += `
                        <div class="property-item">
                            <div class="property-label">Due Date</div>
                            <div class="property-value">${this.escapeHtml(dueDateText)}</div>
                        </div>
                    `;
                }
                // Display Unlock Object if present
                if (savedProperties.unlockObject !== undefined && savedProperties.unlockObject !== null && savedProperties.unlockObject !== '') {
                    const unlockObjectValue = savedProperties.unlockObject === 'true' || savedProperties.unlockObject === true || savedProperties.unlockObject === '1' || savedProperties.unlockObject === 1;
                    propertiesHtml += `
                        <div class="property-item">
                            <div class="property-label">Unlock Object</div>
                            <div class="property-value">${unlockObjectValue ? 'Yes' : 'No'}</div>
                        </div>
                    `;
                }
            }

            // For End Events - show status, lifecycle, and commit changes
            if (businessObject && businessObject.$type === 'bpmn:EndEvent') {
                // Display Status if present
                if (savedProperties.status) {
                    const statusValue = this.formatPropertyValue('status', savedProperties.status);
                    propertiesHtml += `
                        <div class="property-item">
                            <div class="property-label">Status</div>
                            <div class="property-value">${this.escapeHtml(statusValue)}</div>
                        </div>
                    `;
                }
                // Display Lifecycle if present
                if (savedProperties.lifecycle) {
                    const lifecycleValue = this.formatPropertyValue('lifecycle', savedProperties.lifecycle);
                    propertiesHtml += `
                        <div class="property-item">
                            <div class="property-label">Lifecycle</div>
                            <div class="property-value">${this.escapeHtml(lifecycleValue)}</div>
                        </div>
                    `;
                }
                // Display Commit Changes if present
                if (savedProperties.commitChanges !== undefined && savedProperties.commitChanges !== null && savedProperties.commitChanges !== '') {
                    const commitChangesValue = savedProperties.commitChanges === 'true' || savedProperties.commitChanges === true || savedProperties.commitChanges === '1' || savedProperties.commitChanges === 1;
                    propertiesHtml += `
                        <div class="property-item">
                            <div class="property-label">Commit Changes</div>
                            <div class="property-value">${commitChangesValue ? 'Yes' : 'No'}</div>
                        </div>
                    `;
                }
            }

            // For Sequence Flows - show condition
            if (businessObject && businessObject.$type === 'bpmn:SequenceFlow') {
                if (businessObject.conditionExpression) {
                    const condition = businessObject.conditionExpression.body || '';
                    if (condition.trim()) {
                        const formattedCondition = this.formatCondition(condition);
                        propertiesHtml += `
                            <div class="property-item">
                                <div class="property-label">Condition</div>
                                <div class="property-value">${this.escapeHtml(formattedCondition)}</div>
                            </div>
                        `;
                    }
                }
            }

            // Saved properties from extension elements / camunda attributes
            // Exclude properties that are displayed specifically above
            const excludedKeys = new Set(['dueDate', 'unlockObject', 'status', 'lifecycle', 'commitChanges']);
            Object.entries(savedProperties).forEach(([key, value]) => {
                // Skip properties that are already displayed with specific formatting
                if (excludedKeys.has(key)) return;
                
                const displayValue = this.formatPropertyValue(key, value);
                propertiesHtml += `
                    <div class="property-item">
                        <div class="property-label">${this.escapeHtml(this.formatPropertyLabel(key))}</div>
                        <div class="property-value">${this.escapeHtml(displayValue)}</div>
                    </div>
                `;
            });

            propertiesContent.innerHTML = propertiesHtml;
            propertiesPanel.style.display = 'block';
        },

        /**
         * Load statuses for mapping ID -> name
         */
        loadStatuses: async function () {
            try {
                const res = await fetch('/api/status/list');
                if (!res.ok) throw new Error('Failed to load statuses');
                const data = await res.json();
                this.statuses = Array.isArray(data) ? data : (data.data || []);
            } catch (err) {
                console.error('Error loading statuses for view:', err);
                this.statuses = [];
            }
        },

        /**
         * Normalize lifecycle data structure
         */
        normalizeLifecycles: function (data) {
            let lifecycles = [];
            if (data && typeof data === 'object' && data.data && Array.isArray(data.data)) {
                lifecycles = data.data;
            } else if (Array.isArray(data)) {
                lifecycles = data;
            }

            return lifecycles.map(item => {
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
        },

        /**
         * Load lifecycles for current module (best-effort across known endpoints)
         */
        loadLifecycles: async function (moduleNameOrId) {
            if (!moduleNameOrId) {
                this.lifecycles = [];
                return;
            }

            // If already loaded for this module, skip
            if (this.lifecycleModuleId === moduleNameOrId && this.lifecycles.length > 0) return;

            const tryFetch = async (endpoint) => {
                const res = await fetch(endpoint);
                if (!res.ok) throw new Error('failed');
                const json = await res.json();
                return this.normalizeLifecycles(json);
            };

            // Derive moduleName string if module id is given with select text
            // Here assume moduleNameOrId can be name; we attempt known variants in admin code
            const moduleName = typeof moduleNameOrId === 'string' ? moduleNameOrId : '';
            const moduleNameLower = moduleName.toLowerCase().trim().replace(/\s+/g, '_');
            const endpoints = [];

            if (moduleName) {
                endpoints.push(`/api/${moduleNameLower}/lifecycle/list`);
                endpoints.push(`/api/${moduleNameLower.replace(/_/g, '')}/lifecycle/list`);
                endpoints.push(`/api/${moduleNameLower.replace('_', '-')}/lifecycle/list`);
                if (moduleNameLower === 'data_set' || moduleNameLower === 'dataset' || moduleName === 'Data Sets') {
                    endpoints.unshift('/api/lifecycle/list');
                }
                if (moduleNameLower === 'business_area' || moduleNameLower === 'businessarea' || moduleName === 'Business Area') {
                    endpoints.unshift('/api/business-area-lifecycles');
                }
                if (moduleNameLower === 'committee' || moduleName === 'Committee') {
                    endpoints.unshift('/api/committee/lookup?type=lifecycle');
                }
                if (moduleNameLower === 'capability' || moduleName === 'Capability') {
                    endpoints.unshift('/api/capability-lifecycles/dropdown');
                }
                if (moduleNameLower === 'client' || moduleName === 'Client') {
                    endpoints.unshift('/api/client/lifecycle-list');
                }
                if (moduleNameLower === 'process' || moduleName === 'Process') {
                    endpoints.unshift('/api/process/lifecycle/list');
                }
            }

            // Always try generic lifecycle list last
            endpoints.push('/api/lifecycle/list');

            for (const ep of endpoints) {
                try {
                    const list = await tryFetch(ep);
                    if (Array.isArray(list) && list.length > 0) {
                        this.lifecycles = list;
                        this.lifecycleModuleId = moduleNameOrId;
                        return;
                    }
                } catch (err) {
                    // continue
                }
            }

            this.lifecycles = [];
            this.lifecycleModuleId = moduleNameOrId;
        },

        /**
         * Format condition expression for better readability
         * Removes ${} wrapper and formats the condition nicely
         */
        formatCondition: function (condition) {
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
        },

        /**
         * Format property value for display (map IDs to names where possible)
         */
        formatPropertyValue: function (key, value) {
            if (value === undefined || value === null) return '';
            const v = String(value);
            if (key === 'status') {
                const match = this.statuses.find(s => String(s.id || s.ID) === v);
                if (match) return match.primaryName || match.primaryname || match.name || v;
            }
            if (key === 'lifecycle') {
                const match = this.lifecycles.find(l => String(l.id || l.ID) === v);
                if (match) return match.primaryName || match.primaryname || match.name || v;
            }
            return v;
        },

        /**
         * Get user-friendly element type name
         */
        getFriendlyElementType: function (type) {
            const typeMap = {
                'bpmn:StartEvent': 'Start Event',
                'bpmn:EndEvent': 'End Event',
                'bpmn:Task': 'Task',
                'bpmn:UserTask': 'User Task',
                'bpmn:ServiceTask': 'Service Task',
                'bpmn:ScriptTask': 'Script Task',
                'bpmn:ManualTask': 'Manual Task',
                'bpmn:BusinessRuleTask': 'Business Rule Task',
                'bpmn:SendTask': 'Send Task',
                'bpmn:ReceiveTask': 'Receive Task',
                'bpmn:ExclusiveGateway': 'Exclusive Gateway',
                'bpmn:ParallelGateway': 'Parallel Gateway',
                'bpmn:InclusiveGateway': 'Inclusive Gateway',
                'bpmn:EventBasedGateway': 'Event-Based Gateway',
                'bpmn:ComplexGateway': 'Complex Gateway',
                'bpmn:SequenceFlow': 'Sequence Flow',
                'bpmn:MessageFlow': 'Message Flow',
                'bpmn:Association': 'Association',
                'bpmn:DataObjectReference': 'Data Object',
                'bpmn:DataStoreReference': 'Data Store',
                'bpmn:SubProcess': 'Sub Process',
                'bpmn:CallActivity': 'Call Activity',
                'bpmn:IntermediateCatchEvent': 'Intermediate Catch Event',
                'bpmn:IntermediateThrowEvent': 'Intermediate Throw Event',
                'bpmn:BoundaryEvent': 'Boundary Event',
                'bpmn:Lane': 'Lane',
                'bpmn:Participant': 'Pool',
                'bpmn:TextAnnotation': 'Text Annotation',
                'bpmn:Group': 'Group'
            };

            return typeMap[type] || type.replace('bpmn:', '').replace(/([A-Z])/g, ' $1').trim();
        },

        /**
         * Hide properties panel
         */
        hidePropertiesPanel: function () {
            const propertiesPanel = document.getElementById('bpmn-properties-panel');
            if (propertiesPanel) {
                propertiesPanel.style.display = 'none';
            }
        },

        /**
         * Initialize drag functionality for properties panel
         */
        initializePropertiesPanelDrag: function () {
            const panel = document.getElementById('bpmn-properties-panel');
            if (!panel) {
                console.warn('[WorkflowView] Properties panel not found for drag initialization');
                return;
            }
            
            // Check if already initialized to prevent duplicate listeners
            if (panel.dataset.dragInitialized === 'true') {
                return;
            }
            
            const dragHandle = panel.querySelector('.properties-panel-drag-handle');
            if (!dragHandle) {
                console.warn('[WorkflowView] Drag handle not found in properties panel');
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
            
            console.log('[WorkflowView] Properties panel drag functionality initialized');
        },

        /**
         * Destroy BPMN viewer instance
         */
        destroyBpmnViewer: function () {
            if (this.bpmnViewer) {
                this.bpmnViewer.destroy();
                this.bpmnViewer = null;
            }
        },

        /**
         * Extract stored properties for an element (camunda properties and attrs)
         */
        getElementProperties: function (businessObject) {
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

            return properties;
        },

        /**
         * Make property keys readable for display
         */
        formatPropertyLabel: function (name) {
            if (!name) return '';
            return name
                .replace(/^camunda:/, '')
                .replace(/[_-]+/g, ' ')
                .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
                .replace(/\s+/g, ' ')
                .trim()
                .replace(/^./, c => c.toUpperCase());
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
    window.WorkflowView = WorkflowView;

    console.log('WorkflowView component loaded successfully');
})();
