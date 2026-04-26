/**
 * Workflow View Component
 * Displays BPMN workflows in read-only mode within the CHANGE tab
 * Filters workflows by module ID and shows only enabled workflows
 */
(function () {
    'use strict';

    const WorkflowView = {
        currentModuleId: null,
        /** When set with currentObjectId, workflows are loaded via /api/object_workflows (view mode) to include object-private definitions. */
        currentFacetType: null,
        currentObjectId: null,
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
         * @param {string} [facetType] - Facet key (e.g. glossary) for object-scoped workflow merge
         * @param {number} [objectId] - Object instance id for object-scoped workflow merge
         */
        initialize: async function (moduleId, containerId, facetType, objectId) {
            this.currentModuleId = moduleId;
            this.currentFacetType = facetType != null && facetType !== '' ? String(facetType) : null;
            this.currentObjectId = objectId != null && objectId !== '' ? parseInt(objectId, 10) : null;
            if (this.currentObjectId != null && Number.isNaN(this.currentObjectId)) {
                this.currentObjectId = null;
            }

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
            const t = (k, p) => (window.I18n && window.I18n.t(k, p)) || k;
            const w = {
                selectWorkflow: t('workflowView.selectWorkflow'),
                processDetails: t('workflowView.processDetails'),
                workflowLabel: t('workflowView.workflowLabel'),
                workflowRequired: t('workflowView.workflowRequired'),
                selectPlaceholder: t('workflowView.selectPlaceholder'),
                processName: t('workflowView.processName'),
                description: t('workflowView.description'),
                workflowDiagram: t('workflowView.workflowDiagram'),
                diagramSubtitle: t('workflowView.diagramSubtitle'),
                loadingWorkflow: t('workflowView.loadingWorkflow'),
                noWorkflowsAvailable: t('workflowView.noWorkflowsAvailable'),
                elementProperties: t('workflowView.elementProperties'),
                close: t('workflowView.close'),
                fitView: t('workflowView.fitView'),
                showProperties: t('workflowView.showProperties'),
                hideProperties: t('workflowView.hideProperties')
            };
            container.innerHTML = `
                <div class="workflow-view-component wv-root">
                    <div id="workflowLoadingState" class="wv-state wv-state-loading" style="display: none;">
                        <i class="fas fa-spinner fa-spin" aria-hidden="true"></i>
                        <span>${w.loadingWorkflow}</span>
                    </div>
                    <div id="workflowEmptyState" class="wv-state wv-state-empty" style="display: none;">
                        <i class="fas fa-project-diagram" aria-hidden="true"></i>
                        <p>${w.noWorkflowsAvailable}</p>
                    </div>
                    <div id="workflowMainStack" class="wv-main-stack" style="display: none;">
                        <div class="wv-card wv-card-select">
                            <div class="wv-card-head">
                                <span class="wv-card-kicker">${w.selectWorkflow}</span>
                                <h3 class="wv-card-title">${w.workflowLabel}</h3>
                            </div>
                            <div class="wv-card-body">
                                <label class="wv-label" for="workflowSelectDropdown">${w.workflowLabel} <span class="required">${w.workflowRequired}</span></label>
                                <select id="workflowSelectDropdown" class="workflow-select wv-select">
                                    <option value="">${w.selectPlaceholder}</option>
                                </select>
                                <p id="workflowMetaHint" class="wv-hint" style="display: none;"></p>
                            </div>
                        </div>
                        <div id="workflowDetailsSection" class="wv-card wv-card-details" style="display: none;">
                            <div class="wv-card-head">
                                <span class="wv-card-kicker">${w.processDetails}</span>
                                <h3 class="wv-card-title" id="workflowDetailsTitle"></h3>
                            </div>
                            <div class="wv-card-body">
                                <dl class="wv-dl">
                                    <div class="wv-dl-row">
                                        <dt>${w.processName}</dt>
                                        <dd id="workflowProcessName" class="wv-dd"></dd>
                                    </div>
                                    <div class="wv-dl-row">
                                        <dt>${w.description}</dt>
                                        <dd id="workflowDescription" class="wv-dd wv-dd-multiline"></dd>
                                    </div>
                                </dl>
                            </div>
                        </div>
                        <div id="workflowDiagramSection" class="wv-card wv-card-diagram" style="display: none;">
                            <div class="wv-card-head wv-card-head-row">
                                <div class="wv-card-head-text">
                                    <span class="wv-card-kicker">${w.workflowDiagram}</span>
                                    <h3 class="wv-card-title">${w.diagramSubtitle}</h3>
                                </div>
                                <div class="wv-toolbar" role="toolbar" aria-label="${w.workflowDiagram}">
                                    <button type="button" id="wvFitViewBtn" class="btn btn-sm btn-outline-secondary">${w.fitView}</button>
                                    <button type="button" id="wvTogglePropsBtn" class="btn btn-sm btn-outline-secondary" aria-expanded="false">${w.showProperties}</button>
                                </div>
                            </div>
                            <div class="bpmn-viewer-container">
                                <div id="bpmnViewerSlot" class="bpmn-viewer-slot">
                                    <div id="bpmnViewerCanvas" class="bpmn-canvas"></div>
                                </div>
                                <div id="wvDiagramError" class="wv-diagram-error" style="display: none;" role="alert"></div>
                                <div id="bpmn-properties-panel" class="bpmn-properties-tabs-links" style="display: none;">
                                    <div class="properties-header properties-panel-drag-handle">
                                        <div class="wv-prop-head-text">
                                            <span class="wv-prop-kicker">${w.elementProperties}</span>
                                            <h4>${w.elementProperties}</h4>
                                        </div>
                                        <div class="wv-prop-head-actions">
                                            <i class="fas fa-grip-vertical" aria-hidden="true"></i>
                                            <button type="button" id="closePropertiesBtn" class="close-btn" title="${w.close}" aria-label="${w.close}">
                                                <i class="fas fa-times" aria-hidden="true"></i>
                                            </button>
                                        </div>
                                    </div>
                                    <div id="propertiesContent" class="properties-content"></div>
                                </div>
                            </div>
                        </div>
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
                .workflow-view-component.wv-root {
                    width: 100%;
                    display: flex;
                    flex-direction: column;
                    gap: 1rem;
                }

                .workflow-view-component .wv-main-stack {
                    display: flex;
                    flex-direction: column;
                    gap: 1rem;
                }

                .workflow-view-component .wv-card {
                    background: var(--background-primary, #fff);
                    border: 1px solid var(--border-color, #e9ecef);
                    border-radius: 8px;
                    box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
                    overflow: hidden;
                }

                .workflow-view-component .wv-card-head {
                    padding: 0.875rem 1.125rem 0.5rem;
                    border-bottom: 1px solid var(--border-color, #eef0f2);
                    background: var(--background-secondary, #fafbfc);
                }

                .workflow-view-component .wv-card-head-row {
                    display: flex;
                    flex-wrap: wrap;
                    align-items: flex-start;
                    justify-content: space-between;
                    gap: 0.75rem 1rem;
                }

                .workflow-view-component .wv-card-head-text {
                    min-width: 0;
                    flex: 1 1 12rem;
                }

                .workflow-view-component .wv-card-kicker {
                    display: block;
                    font-size: 0.6875rem;
                    font-weight: 600;
                    letter-spacing: 0.06em;
                    text-transform: uppercase;
                    color: var(--text-secondary, #6c757d);
                    margin-bottom: 0.25rem;
                }

                .workflow-view-component .wv-card-title {
                    margin: 0;
                    font-size: 1rem;
                    font-weight: 600;
                    color: var(--text-primary, #2c3e50);
                    line-height: 1.35;
                }

                .workflow-view-component .wv-card-body {
                    padding: 1rem 1.125rem 1.125rem;
                }

                .workflow-view-component .wv-label {
                    display: block;
                    margin-bottom: 0.5rem;
                    font-weight: 500;
                    font-size: 0.875rem;
                    color: var(--text-primary, #2c3e50);
                }

                .workflow-view-component .wv-label .required {
                    color: #dc3545;
                }

                .workflow-view-component .wv-hint {
                    margin: 0.75rem 0 0;
                    font-size: 0.8125rem;
                    line-height: 1.45;
                    color: var(--text-secondary, #6c757d);
                }

                .workflow-view-component .wv-select,
                .workflow-view-component .workflow-select {
                    width: 100%;
                    max-width: 32rem;
                    padding: 0.5rem 0.75rem;
                    border: 1px solid var(--border-color, #e9ecef);
                    border-radius: 6px;
                    font-size: 0.875rem;
                    background: var(--background-primary, #fff);
                    color: var(--text-primary, #2c3e50);
                    cursor: pointer;
                    transition: border-color 0.2s ease, box-shadow 0.2s ease;
                }

                .workflow-view-component .workflow-select:hover {
                    border-color: var(--primary-color, #248567);
                }

                .workflow-view-component .workflow-select:focus {
                    outline: none;
                    border-color: var(--primary-color, #248567);
                    box-shadow: 0 0 0 3px rgba(36, 133, 103, 0.12);
                }

                .workflow-view-component .wv-dl {
                    margin: 0;
                    display: grid;
                    gap: 0.875rem 1.25rem;
                }

                .workflow-view-component .wv-dl-row {
                    display: grid;
                    grid-template-columns: minmax(7rem, 10rem) 1fr;
                    gap: 0.5rem 1rem;
                    align-items: start;
                }

                @media (max-width: 520px) {
                    .workflow-view-component .wv-dl-row {
                        grid-template-columns: 1fr;
                    }
                }

                .workflow-view-component .wv-dl dt {
                    margin: 0;
                    font-size: 0.75rem;
                    font-weight: 600;
                    text-transform: uppercase;
                    letter-spacing: 0.02em;
                    color: var(--text-secondary, #6c757d);
                }

                .workflow-view-component .wv-dl dd {
                    margin: 0;
                }

                .workflow-view-component .wv-dd {
                    font-size: 0.9375rem;
                    color: var(--text-primary, #2c3e50);
                    word-break: break-word;
                }

                .workflow-view-component .wv-dd-multiline {
                    white-space: pre-wrap;
                }

                .workflow-view-component .wv-toolbar {
                    display: flex;
                    flex-wrap: wrap;
                    gap: 0.5rem;
                    align-items: center;
                    flex-shrink: 0;
                }

                .workflow-view-component .bpmn-viewer-container {
                    position: relative;
                    width: 100%;
                    height: clamp(22rem, 52vh, 44rem);
                    min-height: 20rem;
                    border-top: 1px solid var(--border-color, #e9ecef);
                    overflow: hidden;
                    background: #f4f5f7;
                }

                .workflow-view-component .bpmn-viewer-slot {
                    position: absolute;
                    inset: 0;
                }

                .workflow-view-component .bpmn-canvas {
                    width: 100%;
                    height: 100%;
                }

                .workflow-view-component .wv-diagram-error {
                    position: absolute;
                    inset: 0;
                    display: none;
                    align-items: center;
                    justify-content: center;
                    flex-direction: column;
                    gap: 0.5rem;
                    padding: 1.5rem;
                    text-align: center;
                    font-size: 0.875rem;
                    color: var(--text-secondary, #6c757d);
                    background: rgba(255, 255, 255, 0.92);
                    z-index: 5;
                }

                .workflow-view-component .bpmn-properties-tabs-links {
                    position: absolute;
                    right: 12px;
                    top: 12px;
                    bottom: 12px;
                    width: min(300px, calc(100% - 24px));
                    max-height: none;
                    background: var(--background-primary, #fff);
                    border: 1px solid var(--border-color, #ddd);
                    border-radius: 8px;
                    box-shadow: 0 6px 20px rgba(0, 0, 0, 0.12);
                    overflow: hidden;
                    z-index: 100;
                    display: flex;
                    flex-direction: column;
                }

                .workflow-view-component .bpmn-properties-tabs-links .properties-header {
                    display: flex;
                    justify-content: space-between;
                    align-items: flex-start;
                    gap: 0.5rem;
                    padding: 0.75rem 0.875rem;
                    background: var(--background-secondary, #f8f9fa);
                    border-bottom: 1px solid var(--border-color, #e9ecef);
                    flex-shrink: 0;
                }

                .workflow-view-component .wv-prop-head-text {
                    min-width: 0;
                }

                .workflow-view-component .wv-prop-kicker {
                    display: block;
                    font-size: 0.625rem;
                    font-weight: 600;
                    letter-spacing: 0.06em;
                    text-transform: uppercase;
                    color: var(--text-secondary, #6c757d);
                    margin-bottom: 0.125rem;
                }

                .workflow-view-component .bpmn-properties-tabs-links .properties-header.properties-panel-drag-handle {
                    cursor: move;
                    user-select: none;
                }

                .workflow-view-component .bpmn-properties-tabs-links.dragging {
                    box-shadow: 0 10px 28px rgba(0, 0, 0, 0.2);
                    opacity: 0.98;
                }

                .workflow-view-component .wv-prop-head-actions {
                    display: flex;
                    align-items: center;
                    gap: 0.5rem;
                    flex-shrink: 0;
                }

                .workflow-view-component .wv-prop-head-actions .fa-grip-vertical {
                    color: #999;
                    cursor: move;
                }

                .workflow-view-component .bpmn-properties-tabs-links .properties-header h4 {
                    margin: 0;
                    font-size: 0.8125rem;
                    font-weight: 600;
                    color: var(--text-primary, #2c3e50);
                }

                .workflow-view-component .bpmn-properties-tabs-links .close-btn {
                    background: none;
                    border: none;
                    padding: 0.25rem;
                    cursor: pointer;
                    color: var(--text-secondary, #6c757d);
                    font-size: 1rem;
                    line-height: 1;
                    border-radius: 4px;
                    transition: color 0.2s ease, background 0.2s ease;
                }

                .workflow-view-component .bpmn-properties-tabs-links .close-btn:hover {
                    color: var(--text-primary, #2c3e50);
                    background: rgba(0, 0, 0, 0.05);
                }

                .workflow-view-component .bpmn-properties-tabs-links .properties-content {
                    padding: 0.875rem 1rem;
                    flex: 1 1 auto;
                    min-height: 0;
                    overflow-y: auto;
                }

                .workflow-view-component .bpmn-properties-tabs-links .property-item {
                    margin-bottom: 0.75rem;
                    padding-bottom: 0.75rem;
                    border-bottom: 1px solid var(--border-color, #e9ecef);
                }

                .workflow-view-component .bpmn-properties-tabs-links .property-item:last-child {
                    border-bottom: none;
                    margin-bottom: 0;
                    padding-bottom: 0;
                }

                .workflow-view-component .bpmn-properties-tabs-links .property-label {
                    font-weight: 600;
                    font-size: 0.6875rem;
                    color: var(--text-secondary, #6c757d);
                    text-transform: uppercase;
                    letter-spacing: 0.025em;
                    margin-bottom: 0.25rem;
                }

                .workflow-view-component .bpmn-properties-tabs-links .property-value {
                    font-size: 0.875rem;
                    color: var(--text-primary, #2c3e50);
                    word-break: break-word;
                }

                .workflow-view-component .wv-state {
                    text-align: center;
                    padding: 2.5rem 1.5rem;
                    color: var(--text-muted, #6b7280);
                    border: 1px dashed var(--border-color, #e9ecef);
                    border-radius: 8px;
                    background: var(--background-secondary, #fafbfc);
                }

                .workflow-view-component .wv-state-loading {
                    flex-direction: row;
                    align-items: center;
                    justify-content: center;
                    gap: 0.5rem;
                }

                .workflow-view-component .wv-state-empty {
                    flex-direction: column;
                    align-items: center;
                    justify-content: center;
                }

                .workflow-view-component .wv-state-loading i {
                    font-size: 1.125rem;
                }

                .workflow-view-component .wv-state-empty i {
                    font-size: 2.5rem;
                    margin-bottom: 0.75rem;
                    display: block;
                    color: #ced4da;
                }

                .workflow-view-component .wv-state-empty p {
                    margin: 0;
                    font-size: 0.875rem;
                    max-width: 24rem;
                    margin-left: auto;
                    margin-right: auto;
                }

                [dir="rtl"] .workflow-view-component .bpmn-properties-tabs-links {
                    right: auto;
                    left: 12px;
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
                    const workflowId = parseInt(e.target.value, 10);
                    if (workflowId) {
                        this.loadWorkflowDetails(workflowId);
                    } else {
                        this.hideWorkflowDetails();
                    }
                });
            }

            const fitBtn = container.querySelector('#wvFitViewBtn');
            if (fitBtn) {
                fitBtn.addEventListener('click', () => this.zoomFitView());
            }

            const togglePropsBtn = container.querySelector('#wvTogglePropsBtn');
            if (togglePropsBtn) {
                togglePropsBtn.addEventListener('click', () => {
                    const panel = document.getElementById('bpmn-properties-panel');
                    if (!panel) return;
                    const visible = panel.style.display !== 'none';
                    if (visible) {
                        this.hidePropertiesPanel();
                    } else if (document.getElementById('propertiesContent') && document.getElementById('propertiesContent').innerHTML.trim()) {
                        panel.style.display = 'flex';
                        this.syncPropertiesToolbar(true);
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

        /** Fit BPMN canvas to viewport (read-only viewer). */
        zoomFitView: function () {
            if (!this.bpmnViewer) return;
            try {
                const canvas = this.bpmnViewer.get('canvas');
                canvas.zoom('fit-viewport');
            } catch (e) {
                console.warn('[WorkflowView] zoomFitView failed:', e);
            }
        },

        /** Keep toolbar toggle label in sync with panel visibility. */
        syncPropertiesToolbar: function (panelOpen) {
            const btn = document.getElementById('wvTogglePropsBtn');
            if (!btn) return;
            const t = (k, p) => (window.I18n && window.I18n.t(k, p)) || k;
            btn.textContent = panelOpen ? t('workflowView.hideProperties') : t('workflowView.showProperties');
            btn.setAttribute('aria-expanded', panelOpen ? 'true' : 'false');
        },

        clearDiagramError: function () {
            const err = document.getElementById('wvDiagramError');
            const slot = document.getElementById('bpmnViewerSlot');
            if (err) {
                err.style.display = 'none';
                err.textContent = '';
            }
            if (slot) slot.style.display = '';
        },

        showDiagramError: function (message) {
            const err = document.getElementById('wvDiagramError');
            const slot = document.getElementById('bpmnViewerSlot');
            if (slot) slot.style.display = 'none';
            if (err) {
                err.textContent = message;
                err.style.display = 'flex';
            }
        },

        /**
         * Load workflows for the current module
         */
        loadWorkflows: async function () {
            const loadingState = document.getElementById('workflowLoadingState');
            const emptyState = document.getElementById('workflowEmptyState');
            const mainStack = document.getElementById('workflowMainStack');

            if (loadingState) loadingState.style.display = 'flex';
            if (emptyState) emptyState.style.display = 'none';
            if (mainStack) mainStack.style.display = 'none';

            try {
                let url;
                if (this.currentFacetType && this.currentObjectId != null) {
                    url = '/api/object_workflows?mode=view&facetType=' + encodeURIComponent(this.currentFacetType) +
                        '&objectId=' + encodeURIComponent(String(this.currentObjectId)) +
                        '&entityId=' + encodeURIComponent(String(this.currentModuleId));
                } else {
                    url = `/api/process_definitions?entityId=${this.currentModuleId}`;
                }
                const response = await fetch(url);

                if (!response.ok) {
                    throw new Error('Failed to load workflows');
                }

                const allWorkflows = await response.json();

                // Filter to show only enabled workflows
                this.workflows = allWorkflows.filter(wf => wf.status === 'Enabled');

                if (loadingState) loadingState.style.display = 'none';

                if (this.workflows.length === 0) {
                    if (emptyState) emptyState.style.display = 'flex';
                    if (mainStack) mainStack.style.display = 'none';
                } else {
                    if (emptyState) emptyState.style.display = 'none';
                    if (mainStack) mainStack.style.display = 'flex';
                    this.populateWorkflowSelect();
                }

            } catch (error) {
                console.error('Error loading workflows:', error);
                if (loadingState) loadingState.style.display = 'none';
                const errMsg = (window.I18n && window.I18n.t('workflowView.workflowsLoadError')) || 'Failed to load workflows';
                if (emptyState) {
                    emptyState.innerHTML = `
                        <i class="fas fa-exclamation-triangle" aria-hidden="true"></i>
                        <p>${this.escapeHtml(errMsg)}</p>
                    `;
                    emptyState.style.display = 'flex';
                }
                if (mainStack) mainStack.style.display = 'none';
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

            const t = (k, p) => (window.I18n && window.I18n.t(k, p)) || k;
            const placeholder = t('workflowView.selectPlaceholder');
            select.innerHTML = '<option value="">' + this.escapeHtml(placeholder) + '</option>';

            this.workflows.forEach(workflow => {
                const option = document.createElement('option');
                option.value = workflow.id;
                option.textContent = workflow.primaryName;
                select.appendChild(option);
            });

            const hint = document.getElementById('workflowMetaHint');
            if (hint) {
                const n = this.workflows.length;
                const countLine = t('workflowView.workflowCount', { count: n });
                let text = countLine;
                if (this.currentFacetType && this.currentObjectId != null) {
                    text += ' ' + t('workflowView.combinedListHint');
                }
                hint.textContent = text;
                hint.style.display = 'block';
            }
        },

        /**
         * Load and display workflow details
         */
        loadWorkflowDetails: async function (workflowId) {
            const workflow = this.workflows.find(wf => Number(wf.id) === Number(workflowId));
            if (!workflow) return;

            this.selectedWorkflow = workflow;

            // Show workflow details
            const detailsSection = document.getElementById('workflowDetailsSection');
            const processNameEl = document.getElementById('workflowProcessName');
            const descriptionEl = document.getElementById('workflowDescription');

            if (processNameEl) processNameEl.textContent = workflow.primaryName;
            if (descriptionEl) descriptionEl.textContent = workflow.description || '';
            const detailsTitle = document.getElementById('workflowDetailsTitle');
            if (detailsTitle) detailsTitle.textContent = workflow.primaryName || '';
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

            this.clearDiagramError();
            this.hidePropertiesPanel();
            this.destroyBpmnViewer();
            this.selectedWorkflow = null;
        },

        /**
         * Load and display BPMN diagram
         */
        loadBpmnDiagram: async function (workflowId) {
            const diagramSection = document.getElementById('workflowDiagramSection');
            const errMsg = (window.I18n && window.I18n.t('workflowView.diagramLoadError')) || 'Failed to load workflow diagram';

            this.clearDiagramError();

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
                if (diagramSection) diagramSection.style.display = 'block';
                this.destroyBpmnViewer();
                this.showDiagramError(errMsg);
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

            if (canvasElement.dataset.wvPanInit === 'true') {
                return;
            }
            canvasElement.dataset.wvPanInit = 'true';

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

            const t = (k, p) => (window.I18n && window.I18n.t(k, p)) || k;
            const businessObject = element.businessObject;
            let propertiesHtml = '';

            const savedProperties = this.getElementProperties(businessObject);
            const savedPropKeys = new Set(Object.keys(savedProperties));

            if (element.type) {
                propertiesHtml += `
                    <div class="property-item">
                        <div class="property-label">${this.escapeHtml(t('workflowView.elementType'))}</div>
                        <div class="property-value">${this.escapeHtml(this.getFriendlyElementType(element.type))}</div>
                    </div>
                `;
            }

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
            propertiesPanel.style.display = 'flex';
            this.syncPropertiesToolbar(true);
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
            this.syncPropertiesToolbar(false);
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
            
            // Store initial position (right: 12px, top: 12px) — matches diagram card CSS
            let panelLeft = null;
            let panelTop = null;
            let hasBeenDragged = false;
            
            // Get initial position
            function getInitialPosition() {
                const parent = panel.parentElement;
                if (parent && !hasBeenDragged) {
                    const parentRect = parent.getBoundingClientRect();
                    const panelRect = panel.getBoundingClientRect();
                    panelLeft = parentRect.width - panelRect.width - 12;
                    panelTop = 12;
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
                    panelLeft = parentRect.width - rect.width - 12;
                    panelTop = 12;
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
        },

        /**
         * Destroy BPMN viewer instance
         */
        destroyBpmnViewer: function () {
            const canvasEl = document.getElementById('bpmnViewerCanvas');
            if (canvasEl) {
                delete canvasEl.dataset.wvPanInit;
            }
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
