/**
 * Workflow Start Manager
 * Manages workflow selection, validation, and instance creation for change requests
 * 
 * Architecture:
 * - All BPMN role extraction happens server-side
 * - Frontend calls validation API and reacts to response
 * - No local BPMN parsing for roles
 * - ONE WORKFLOW PER CHANGE REQUEST (strict rule)
 */

class WorkflowStartManager {
    constructor() {
        this.state = {
            changeRequest: null,
            workflows: [],
            selectedWorkflow: null,
            bpmnXml: null,  // Only for diagram display
            validationResult: null,  // From API: { valid, missingRoles, requiredRoles, objectStakeholders }
            workflowInstance: null,
            status: 'idle',  // idle | loading | ready | validating | starting | started | error
            error: null,
            modules: [],
            storedWorkflow: null // Store the workflow loaded by processDefinitionId
        };
    }

    /**
     * Initialize manager with change request data
     */
    async init(changeRequest) {
        this.state.changeRequest = changeRequest;
        this.state.status = 'loading';

        try {
            // ⚠️ CRITICAL: Check if workflow already started
            // Rule: ONE WORKFLOW PER CHANGE REQUEST
            if (changeRequest.workflowInstanceId || changeRequest.processInstanceId) {
                console.log('Workflow already started for this change request');

                // Fetch full instance details to get processDefinitionId for display
                try {
                    const response = await fetch(`/api/workflow_instances/by-cr/${changeRequest.id}`);
                    if (response.ok) {
                        const data = await response.json();
                        if (data.instance) {
                            this.state.workflowInstance = data.instance;
                            // Set selected workflow so UI can show diagram
                            this.state.selectedWorkflow = { id: data.instance.processDefinitionId };
                            console.log('Loaded existing workflow instance:', data.instance);
                        }
                    }
                } catch (e) {
                    console.error('Failed to load existing workflow instance:', e);
                }

                this.state.status = 'started';

                // Fallback if API failed but we have ID
                if (!this.state.workflowInstance) {
                    this.state.workflowInstance = {
                        workflowInstanceId: changeRequest.workflowInstanceId || changeRequest.processInstanceId
                    };
                }

                return 'already_started';
            }

            // Extract facet information from reference
            const facetInfo = this.parseFacetFromReference(changeRequest.reference);
            if (!facetInfo) {
                throw new Error('Invalid change request reference format');
            }

            // Load modules to resolve facet name to Module ID (entityId)
            await this.loadModules();
            const moduleId = this.resolveModuleId(facetInfo.facetName);

            if (!moduleId) {
                console.warn(`Could not resolve Module ID for facet: ${facetInfo.facetName}. Using facetId as fallback.`);
            }

            // Load available workflows for this facet (Module ID)
            await this.loadWorkflows(moduleId || facetInfo.facetId);

            // Check if CR has a default workflow set
            if (changeRequest.processDefinitionId) {
                // Pre-select the default workflow (handle type conversion)
                const processDefId = typeof changeRequest.processDefinitionId === 'string' 
                    ? parseInt(changeRequest.processDefinitionId) 
                    : changeRequest.processDefinitionId;
                
                console.log('[WorkflowManager] Looking for workflow with processDefinitionId:', processDefId);
                console.log('[WorkflowManager] Available workflows:', this.state.workflows.map(w => ({ 
                    id: w.id, 
                    idType: typeof w.id, 
                    name: w.name || w.primaryName 
                })));
                
                // Use stored workflow directly if available and matches (ensures correct workflow)
                let defaultWorkflow = null;
                if (this.state.storedWorkflow) {
                    const storedId = typeof this.state.storedWorkflow.id === 'string' 
                        ? parseInt(this.state.storedWorkflow.id) 
                        : this.state.storedWorkflow.id;
                    if (storedId === processDefId) {
                        defaultWorkflow = this.state.storedWorkflow;
                        console.log('[WorkflowManager] Using stored workflow directly (loaded by ID):', {
                            id: defaultWorkflow.id,
                            name: defaultWorkflow.name || defaultWorkflow.primaryName,
                            entityId: defaultWorkflow.entityId
                        });
                    }
                }
                
                // If stored workflow doesn't match, search in list
                if (!defaultWorkflow) {
                    defaultWorkflow = this.state.workflows.find(w => {
                        const workflowId = typeof w.id === 'string' ? parseInt(w.id) : w.id;
                        const matches = workflowId === processDefId;
                        if (matches) {
                            console.log('[WorkflowManager] Found matching workflow in list:', w);
                        }
                        return matches;
                    });
                }
                
                if (defaultWorkflow) {
                    this.state.selectedWorkflow = defaultWorkflow;
                    // Automatically validate the default workflow
                    await this.validateWorkflowStart(processDefId);
                    console.log('[WorkflowManager] Pre-selected default workflow:', {
                        id: defaultWorkflow.id,
                        idType: typeof defaultWorkflow.id,
                        name: defaultWorkflow.name || defaultWorkflow.primaryName,
                        processDefinitionId: processDefId,
                        matches: defaultWorkflow.id === processDefId || String(defaultWorkflow.id) === String(processDefId)
                    });
                } else {
                    console.warn('[WorkflowManager] Default workflow ID', changeRequest.processDefinitionId, 'not found in available workflows');
                    console.warn('[WorkflowManager] Available workflow IDs:', this.state.workflows.map(w => ({ 
                        id: w.id, 
                        idType: typeof w.id, 
                        name: w.name || w.primaryName 
                    })));
                }
            }

            this.state.status = 'ready';
            return true;

        } catch (error) {
            console.error('Error initializing workflow manager:', error);
            this.state.status = 'error';
            this.state.error = error.message;
            return false;
        }
    }

    /**
     * Parse facet type and ID from reference string
     * Format: "FacetType FacetId" (e.g., "Dataset 47", "System 5")
     */
    parseFacetFromReference(reference) {
        if (!reference) return null;

        const match = reference.match(/^(.+?)\s+(\d+)$/);
        if (!match) return null;

        const facetName = match[1].trim();
        const facetId = parseInt(match[2]);

        // Normalize facet name to match API expectations
        const facetType = facetName.toLowerCase()
            .replace(/\s+/g, '-')
            .replace(/^data-set$/, 'dataset')
            .replace(/^system-interface$/, 'systeminterface');

        return { facetName, facetType, facetId };
    }

    /**
     * Load all modules from API
     */
    async loadModules() {
        if (this.state.modules && this.state.modules.length > 0) return;
        try {
            const response = await fetch('/api/modules');
            if (response.ok) {
                const data = await response.json();
                this.state.modules = data.modules || [];
            }
        } catch (error) {
            console.error('Failed to load modules:', error);
        }
    }

    /**
     * Resolve module ID from facet name
     */
    resolveModuleId(facetName) {
        if (!this.state.modules || !facetName) return null;

        const normalize = (str) => str.toLowerCase().replace(/[^a-z0-9]/g, '');
        const search = normalize(facetName);
        const manualMap = { 'dataset': 'datasets', 'data_set': 'datasets', 'business_area': 'businessarea' };
        const target = manualMap[search] || search;

        const module = this.state.modules.find(m => {
            const modName = normalize(m.primaryName);
            return modName === target || modName === target + 's' || target === modName + 's';
        });
        return module ? module.id : null;
    }

    /**
     * Normalize role name for comparison
     * CRITICAL: Must match normalization in backend and database
     * Format: UPPERCASE, NO SPACES, UNDERSCORES ONLY
     */
    normalizeRole(role) {
        if (!role) return '';
        return role.trim()
            .toUpperCase()
            .replace(/\s+/g, '_')
            .replace(/-/g, '_');
    }

    /**
     * Load available workflows for the facet
     */
    async loadWorkflows(entityId) {
        try {
            // Check if this is an automatically raised CR (mandatoryWorkflow = true)
            // Also check if processDefinitionId exists (indicates workflow was chosen in admin panel)
            const mandatoryWorkflow = this.state.changeRequest?.mandatoryWorkflow;
            const hasProcessDefinitionId = this.state.changeRequest?.processDefinitionId != null;
            const isAutoCreated = mandatoryWorkflow === true || mandatoryWorkflow === 1 || mandatoryWorkflow === 'true' || mandatoryWorkflow === '1';
            
            console.log('[WorkflowManager] Checking CR type:', {
                mandatoryWorkflow: mandatoryWorkflow,
                mandatoryWorkflowType: typeof mandatoryWorkflow,
                hasProcessDefinitionId: hasProcessDefinitionId,
                processDefinitionId: this.state.changeRequest?.processDefinitionId,
                isAutoCreated: isAutoCreated
            });
            
            // First, if CR has a processDefinitionId, load that specific workflow
            // This ensures the correct workflow is available even if it has a different Entity_ID
            let storedWorkflow = null;
            if (this.state.changeRequest && this.state.changeRequest.processDefinitionId) {
                const processDefId = typeof this.state.changeRequest.processDefinitionId === 'string' 
                    ? parseInt(this.state.changeRequest.processDefinitionId) 
                    : this.state.changeRequest.processDefinitionId;
                
                try {
                    const workflowResponse = await fetch(`/api/process_definitions/${processDefId}`);
                    if (workflowResponse.ok) {
                        storedWorkflow = await workflowResponse.json();
                        console.log('[WorkflowManager] Loaded stored workflow by ID:', {
                            id: storedWorkflow.id,
                            name: storedWorkflow.name || storedWorkflow.primaryName,
                            entityId: storedWorkflow.entityId,
                            processDefinitionId: processDefId,
                            fullObject: storedWorkflow
                        });
                    } else {
                        console.warn('[WorkflowManager] Failed to load stored workflow, status:', workflowResponse.status);
                    }
                } catch (error) {
                    console.warn('[WorkflowManager] Could not load stored workflow by ID:', error);
                }
            }
            
            // For automatically raised CRs (from DF_CR), only show the workflow chosen in admin panel
            // If processDefinitionId exists, it means a workflow was chosen in admin panel (DF_CR settings)
            // In this case, show ONLY that workflow, not all available workflows
            if (hasProcessDefinitionId && storedWorkflow) {
                console.log('[WorkflowManager] CR has processDefinitionId from admin panel - showing ONLY the selected workflow:', {
                    id: storedWorkflow.id,
                    name: storedWorkflow.name || storedWorkflow.primaryName,
                    mandatoryWorkflow: mandatoryWorkflow,
                    processDefinitionId: this.state.changeRequest.processDefinitionId,
                    isAutoCreated: isAutoCreated
                });
                // Set workflows to contain ONLY the workflow from admin panel
                this.state.workflows = [storedWorkflow];
                this.state.storedWorkflow = storedWorkflow;
                return [storedWorkflow];
            }
            
            // For manually created CRs, load all workflows filtered by entityId
            const response = await fetch(`/api/process_definitions?entityId=${entityId}`);
            if (!response.ok) {
                throw new Error('Failed to load workflows');
            }

            const workflows = await response.json();
            
            // If we have a stored workflow, ensure it's in the list and prioritized
            if (storedWorkflow) {
                const storedId = typeof storedWorkflow.id === 'string' ? parseInt(storedWorkflow.id) : storedWorkflow.id;
                const existingIndex = workflows.findIndex(w => {
                    const wId = typeof w.id === 'string' ? parseInt(w.id) : w.id;
                    return wId === storedId;
                });
                
                if (existingIndex !== -1) {
                    // Replace the existing one with the stored workflow to ensure correct data
                    workflows[existingIndex] = storedWorkflow;
                    console.log('[WorkflowManager] Replaced workflow in list with stored workflow at index', existingIndex, {
                        id: storedWorkflow.id,
                        name: storedWorkflow.name || storedWorkflow.primaryName
                    });
                } else {
                    // Add at the beginning so it's found first
                    workflows.unshift(storedWorkflow);
                    console.log('[WorkflowManager] Added stored workflow to list (different Entity_ID):', {
                        id: storedWorkflow.id,
                        name: storedWorkflow.name || storedWorkflow.primaryName,
                        entityId: storedWorkflow.entityId
                    });
                }
            }
            
            this.state.workflows = workflows;
            // Store reference to stored workflow for direct access
            if (storedWorkflow) {
                this.state.storedWorkflow = storedWorkflow;
            }
            return workflows;

        } catch (error) {
            console.error('Error loading workflows:', error);
            throw error;
        }
    }

    /**
     * Select a workflow and trigger validation
     */
    async selectWorkflow(processDefId) {
        const workflow = this.state.workflows.find(w => w.id === processDefId);
        if (!workflow) {
            throw new Error('Workflow not found');
        }

        this.state.selectedWorkflow = workflow;
        this.state.status = 'loading';

        try {
            // Load BPMN XML for diagram display
            await this.loadBpmnXml(processDefId);

            // Validate workflow start requirements (server-side)
            await this.validateWorkflowStart(processDefId);

            this.state.status = 'ready';
            return true;

        } catch (error) {
            console.error('Error selecting workflow:', error);
            this.state.status = 'error';
            this.state.error = error.message;
            return false;
        }
    }

    /**
     * Load BPMN XML for diagram display
     */
    async loadBpmnXml(processDefId) {
        try {
            const response = await fetch(`/api/process_definitions/${processDefId}/bpmn`);
            if (!response.ok) {
                throw new Error('Failed to load BPMN diagram');
            }

            const data = await response.json();
            this.state.bpmnXml = data.xml;
            return data.xml;

        } catch (error) {
            console.error('Error loading BPMN XML:', error);
            throw error;
        }
    }

    /**
     * Validate workflow start requirements (SERVER-SIDE)
     * Calls backend API to check stakeholder coverage
     * Backend handles role normalization
     */
    async validateWorkflowStart(processDefId) {
        if (!this.state.changeRequest) {
            throw new Error('No change request loaded');
        }

        this.state.status = 'validating';

        try {
            const crId = this.state.changeRequest.id;
            const response = await fetch(
                `/api/workflow/validate-start?changeRequestId=${crId}&processDefId=${processDefId}`
            );

            if (!response.ok) {
                const errorData = await response.json().catch(() => ({ error: 'Validation failed' }));
                throw new Error(errorData.error || 'Validation failed');
            }

            const validationResult = await response.json();
            this.state.validationResult = validationResult;

            console.log('Validation result:', validationResult);
            return validationResult;

        } catch (error) {
            console.error('Error validating workflow start:', error);
            this.state.validationResult = {
                valid: false,
                missingRoles: [],
                error: error.message
            };
            throw error;
        }
    }

    /**
     * Start workflow instance
     */
    async startWorkflow() {
        console.log('Start workflow called. Selected workflow:', this.state.selectedWorkflow);
        console.log('Current validation result:', this.state.validationResult);

        if (!this.state.selectedWorkflow) {
            console.error('No workflow selected');
            throw new Error('No workflow selected');
        }

        if (!this.state.validationResult || !this.state.validationResult.valid) {
            console.error('Validation failed', this.state.validationResult);
            throw new Error('Workflow validation failed. Cannot start workflow.');
        }

        this.state.status = 'starting';
        console.log('Sending start request to server...');

        try {
            const payload = {
                processDefinitionId: this.state.selectedWorkflow.id,
                changeRequestId: this.state.changeRequest.id
            };
            console.log('Payload:', payload);

            const response = await fetch('/api/workflow_instances', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                credentials: 'include',
                body: JSON.stringify(payload)
            });

            console.log('Server response status:', response.status);

            if (!response.ok) {
                const errorData = await response.json().catch(() => ({ error: 'Failed to start workflow' }));
                console.error('Server error response:', errorData);
                throw new Error(errorData.error || 'Failed to start workflow');
            }

            const result = await response.json();
            this.state.workflowInstance = result;
            this.state.status = 'started';

            console.log('Workflow started successfully:', result);
            return result;

        } catch (error) {
            console.error('Error starting workflow:', error);
            this.state.status = 'error';
            this.state.error = error.message;
            throw error;
        }
    }

    /**
     * Get current state
     */
    getState() {
        return { ...this.state };
    }

    /**
     * Check if workflow can be started
     */
    canStartWorkflow() {
        return this.state.selectedWorkflow !== null &&
            this.state.validationResult !== null &&
            this.state.validationResult.valid === true &&
            this.state.status !== 'starting' &&
            this.state.status !== 'started';
    }

    /**
     * Get validation error message
     */
    getValidationErrorMessage() {
        if (!this.state.validationResult || this.state.validationResult.valid) {
            return null;
        }

        const missingRoles = this.state.validationResult.missingRoles || [];
        if (missingRoles.length === 0) {
            return 'Validation failed';
        }

        return `Cannot start workflow. Missing stakeholders for roles: ${missingRoles.join(', ')}`;
    }

    /**
     * Reset manager state
     */
    reset() {
        this.state = {
            changeRequest: this.state.changeRequest,  // Keep change request
            workflows: this.state.workflows,  // Keep loaded workflows
            selectedWorkflow: null,
            bpmnXml: null,
            validationResult: null,
            workflowInstance: null,
            status: 'ready',
            error: null,
            storedWorkflow: null // Reset stored workflow
        };
    }
}

// Export for use in change-request-view.js
if (typeof module !== 'undefined' && module.exports) {
    module.exports = WorkflowStartManager;
}
