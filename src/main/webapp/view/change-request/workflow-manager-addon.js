    // ========================================
    // WORKFLOW MANAGER INTEGRATION
    // ========================================
    
    let workflowManager = null;
    let bpmnViewer = null;

    async function initializeWorkflowManager(changeRequest) {
        try {
            workflowManager = new WorkflowStartManager();
            const initialized = await workflowManager.init(changeRequest);
            
            if (initialized === 'already_started') {
                console.log('Workflow already started - locking UI');
                lockWorkflowSection();
                
                // Check workflow status and show appropriate message (only for auto CRs)
                // Get changeRequest from workflowManager state if available
                const changeRequest = workflowManager.getState()?.changeRequest || null;
                const state = workflowManager.getState();
                const instance = state.workflowInstance;
                if (instance && (instance.status === 'Completed' || instance.endedAt)) {
                    showWorkflowCompletedStatus(changeRequest);
                } else {
                    showWorkflowStartedStatus(changeRequest);
                }
                return;
            }
            
            if (!initialized) {
                console.error('Failed to initialize workflow manager');
                return;
            }

            const state = workflowManager.getState();
            const workflowSelect = document.getElementById('workflowSelect');
            
            if (state.workflows && state.workflows.length > 0) {
                state.workflows.forEach(workflow => {
                    const option = document.createElement('option');
                    option.value = workflow.id;
                    option.textContent = workflow.name || workflow.primaryName || 'Workflow';
                    workflowSelect.appendChild(option);
                });
            }

            // Pre-select default workflow if set
            if (changeRequest.processDefinitionId) {
                // Convert to number for comparison (workflow.id might be number or string)
                const processDefId = parseInt(changeRequest.processDefinitionId);
                const defaultWorkflow = state.workflows.find(w => {
                    const workflowId = typeof w.id === 'string' ? parseInt(w.id) : w.id;
                    return workflowId === processDefId;
                });
                
                if (defaultWorkflow && workflowSelect) {
                    // Use the actual workflow.id value (might be string or number)
                    // Convert to string to ensure it matches option value
                    const workflowValue = String(defaultWorkflow.id);
                    workflowSelect.value = workflowValue;
                    console.log('[WorkflowManager-Addon] Pre-selected default workflow in dropdown:', {
                        processDefinitionId: changeRequest.processDefinitionId,
                        selectedValue: workflowValue,
                        workflowId: defaultWorkflow.id,
                        workflowIdType: typeof defaultWorkflow.id,
                        workflowName: defaultWorkflow.name || defaultWorkflow.primaryName,
                        dropdownValue: workflowSelect.value,
                        dropdownSelectedText: workflowSelect.options[workflowSelect.selectedIndex]?.textContent
                    });
                    // Trigger change event to load diagram, validate, and show start button
                    workflowSelect.dispatchEvent(new Event('change'));
                } else {
                    console.warn('[WorkflowManager-Addon] Default workflow not found:', {
                        processDefinitionId: changeRequest.processDefinitionId,
                        availableWorkflows: state.workflows.map(w => ({ id: w.id, name: w.name || w.primaryName }))
                    });
                }
            } else {
                console.log('[WorkflowManager] No processDefinitionId set for change request:', changeRequest.id);
            }

            setupWorkflowEventListeners();

        } catch (error) {
            console.error('Error initializing workflow manager:', error);
        }
    }

    function lockWorkflowSection() {
        const workflowSelect = document.getElementById('workflowSelect');
        if (workflowSelect) {
            workflowSelect.disabled = true;
            workflowSelect.style.opacity = '0.6';
        }
    }

    function setupWorkflowEventListeners() {
        const workflowSelect = document.getElementById('workflowSelect');
        const startWorkflowBtn = document.getElementById('startWorkflowBtn');
        
        if (workflowSelect) {
            workflowSelect.addEventListener('change', async (e) => {
                const processDefId = parseInt(e.target.value);
                if (!processDefId) {
                    // Hide actions if no workflow selected
                    hideWorkflowActions();
                    hideValidationError();
                    return;
                }

                try {
                    // Show loading state on button
                    if (startWorkflowBtn) {
                        startWorkflowBtn.disabled = true;
                        startWorkflowBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Validating...';
                    }
                    
                    await workflowManager.selectWorkflow(processDefId);
                    const state = workflowManager.getState();
                    
                    if (state.validationResult && state.validationResult.valid) {
                        // Validation passed - show start button immediately
                        showWorkflowActions();
                        hideValidationError();
                        if (startWorkflowBtn) {
                            startWorkflowBtn.disabled = false;
                            startWorkflowBtn.innerHTML = '<i class="fas fa-play"></i> Start Workflow';
                        }
                    } else {
                        // Validation failed - show error
                        const errorMsg = workflowManager.getValidationErrorMessage();
                        showValidationError(errorMsg);
                        hideWorkflowActions();
                    }
                } catch (error) {
                    console.error('Error selecting workflow:', error);
                    showValidationError('Error loading workflow: ' + error.message);
                    hideWorkflowActions();
                }
            });
        }
        
        // Start workflow button click handler
        if (startWorkflowBtn) {
            startWorkflowBtn.addEventListener('click', async () => {
                if (!workflowManager || !workflowManager.canStartWorkflow()) {
                    alert('Workflow cannot be started. Please check validation.');
                    return;
                }
                
                // ⚠️ CRITICAL: Only show IMPORTANT NOTICE for auto CRs (mandatory_workflow = true)
                // Manual CRs should NOT show this message - they work like other facets without locking
                const changeRequest = workflowManager.getState()?.changeRequest;
                const mandatoryWorkflow = changeRequest?.mandatoryWorkflow;
                const isAutoCR = mandatoryWorkflow === true || mandatoryWorkflow === 1 || mandatoryWorkflow === 'true' || mandatoryWorkflow === '1';
                
                // Only show confirmation message for auto CRs
                if (isAutoCR) {
                    // Get object reference from CR to show in warning message
                    const objectReference = changeRequest?.reference || 'this object';
                    
                    // Extract current facet from reference (e.g., "Data Set 86" -> "Data Set", "Glossary 20" -> "Glossary")
                    let currentFacet = 'this facet';
                    if (changeRequest?.reference) {
                        const refParts = changeRequest.reference.split(/\s+/);
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
                    // Disable button and show loading
                    startWorkflowBtn.disabled = true;
                    startWorkflowBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Starting...';
                    
                    // Start workflow
                    await workflowManager.startWorkflow();
                    
                    // Show success state (only for auto CRs)
                    // Get changeRequest from current context
                    const currentCRId = typeof currentChangeRequestId !== 'undefined' ? currentChangeRequestId : null;
                    let currentCR = null;
                    if (currentCRId) {
                        try {
                            const crResponse = await fetch(`/api/changerequests/${currentCRId}`, {
                                credentials: 'include'
                            });
                            if (crResponse.ok) {
                                currentCR = await crResponse.json();
                            }
                        } catch (e) {
                            console.warn('Could not fetch CR for workflow status check:', e);
                        }
                    }
                    showWorkflowStartedStatus(currentCR);
                    hideWorkflowActions();
                    hideValidationError();
                    
                    // Lock dropdown (ONE WORKFLOW PER CR RULE)
                    lockWorkflowSection();
                    
                    // Refresh CR Data to show "Running" status and other updates
                    if (typeof loadChangeRequestData === 'function') {
                        console.log('Refreshing Change Request data...');
                        await loadChangeRequestData();
                        
                        // Wait a bit for database transaction to commit and tasks to be created
                        console.log('⏳ Waiting 500ms for database transaction to commit...');
                        await new Promise(resolve => setTimeout(resolve, 500));
                        
                        // Check for active task after workflow start (with retry)
                        if (typeof checkAndShowActiveTaskWithRetry === 'function') {
                            console.log('🔄 Workflow started, checking for active task with retry...');
                            await checkAndShowActiveTaskWithRetry();
                        }
                    }
                } catch (error) {
                    console.error('Error starting workflow:', error);
                    alert('Failed to start workflow: ' + error.message);
                    startWorkflowBtn.disabled = false;
                    startWorkflowBtn.innerHTML = '<i class="fas fa-play"></i> Start Workflow';
                }
            });
        }
    }

    function showWorkflowActions() {
        const actionsDiv = document.getElementById('workflowActions');
        if (actionsDiv) {
            actionsDiv.style.display = 'block';
        }
        // Hide validation error when showing actions
        hideValidationError();
    }

    function showValidationError(message) {
        const errorDiv = document.getElementById('workflowValidationError');
        const errorText = document.getElementById('validationErrorText');
        if (errorDiv && errorText) {
            errorText.textContent = message;
            errorDiv.style.display = 'block';
        }
    }

    function hideValidationError() {
        const errorDiv = document.getElementById('workflowValidationError');
        if (errorDiv) errorDiv.style.display = 'none';
    }

    function hideWorkflowActions() {
        const actionsDiv = document.getElementById('workflowActions');
        if (actionsDiv) actionsDiv.style.display = 'none';
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
        }
        
        // Show message for auto CRs
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

    function showWorkflowCompletedStatus() {
        const statusDiv = document.getElementById('workflowStartedStatus');
        const statusText = document.getElementById('workflowStatusText');
        if (statusDiv) {
            if (statusText) {
                statusText.textContent = 'Workflow completed successfully';
            }
            statusDiv.style.display = 'block';
        }
    }

