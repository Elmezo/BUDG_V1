(function() {
    'use strict';

    let currentChangeRequestId = null;
    let originalData = null;
    let relationshipTypes = [];
    let relationships = [];
    let editingRelationshipId = null;
    let pendingRelationships = []; // Store relationships that haven't been saved to DB yet
    let customFieldsContext = null;

    // Helper function to escape HTML
    function escapeHtml(str) {
        if (str == null) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    // Parse ID from URL
    function parseId() {
        const urlParams = new URLSearchParams(window.location.search);
        const id = parseInt(urlParams.get('id'), 10);
        return Number.isNaN(id) ? null : id;
    }

    // Show success/error message
    function showMessage(message, isError = false) {
        const existingMessage = document.getElementById('status-message');
        if (existingMessage) {
            existingMessage.remove();
        }
        
        const messageDiv = document.createElement('div');
        messageDiv.id = 'status-message';
        const backgroundColor = isError ? '#ef4444' : '#248567';
        messageDiv.style.cssText = `
            position: fixed;
            top: 80px;
            left: 50%;
            transform: translateX(-50%);
            background-color: ${backgroundColor};
            color: white;
            padding: 12px 24px;
            border-radius: 6px;
            font-weight: 600;
            font-size: 14px;
            z-index: 10000;
            box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
            transition: opacity 0.3s ease;
        `;
        messageDiv.textContent = message;
        
        document.body.appendChild(messageDiv);
        
        setTimeout(() => {
            if (messageDiv.parentNode) {
                messageDiv.style.opacity = '0';
                setTimeout(() => {
                    if (messageDiv.parentNode) {
                        messageDiv.remove();
                    }
                }, 300);
            }
        }, 3000);
    }

    // Load dropdown options
    async function loadDropdownOptions() {
        try {
            // Load types
            const typesResponse = await fetch('/api/changerequest-types');
            if (typesResponse.ok) {
                const types = await typesResponse.json();
                const typeSelect = document.getElementById('type');
                types.forEach(type => {
                    const option = document.createElement('option');
                    option.value = type.id;
                    option.textContent = type.primaryName || type.name;
                    typeSelect.appendChild(option);
                });
            }

            // Load severities
            const severitiesResponse = await fetch('/api/changerequest-severities');
            if (severitiesResponse.ok) {
                const severities = await severitiesResponse.json();
                const severitySelect = document.getElementById('severity');
                severities.forEach(severity => {
                    const option = document.createElement('option');
                    option.value = severity.id;
                    option.textContent = severity.primaryName || severity.name;
                    severitySelect.appendChild(option);
                });
            }

            // Load urgencies
            const urgenciesResponse = await fetch('/api/changerequest-urgencies');
            if (urgenciesResponse.ok) {
                const urgencies = await urgenciesResponse.json();
                const urgencySelect = document.getElementById('urgency');
                urgencies.forEach(urgency => {
                    const option = document.createElement('option');
                    option.value = urgency.id;
                    option.textContent = urgency.primaryName || urgency.name;
                    urgencySelect.appendChild(option);
                });
            }

            // Load currencies
            const currenciesResponse = await fetch('/api/changerequest-currencies');
            if (currenciesResponse.ok) {
                const currencies = await currenciesResponse.json();
                const benefitCurrencySelect = document.getElementById('estimatedBenefitCurrency');
                const costCurrencySelect = document.getElementById('estimatedCostCurrency');
                
                // Clear existing options
                benefitCurrencySelect.innerHTML = '';
                costCurrencySelect.innerHTML = '';
                
                currencies.forEach(currency => {
                    const option1 = document.createElement('option');
                    option1.value = currency.primaryName || currency.name;
                    option1.textContent = currency.primaryName || currency.name;
                    benefitCurrencySelect.appendChild(option1);
                    
                    const option2 = document.createElement('option');
                    option2.value = currency.primaryName || currency.name;
                    option2.textContent = currency.primaryName || currency.name;
                    costCurrencySelect.appendChild(option2);
                });
            }

            // Load relationship types
            const relationshipTypesResponse = await fetch('/api/cr-relationship-types');
            if (relationshipTypesResponse.ok) {
                relationshipTypes = await relationshipTypesResponse.json();
                const relationshipTypeSelect = document.getElementById('relationshipType');
                relationshipTypes.forEach(type => {
                    const option = document.createElement('option');
                    option.value = type.id;
                    option.textContent = type.name;
                    relationshipTypeSelect.appendChild(option);
                });
            }

            // Load resolution statuses
            try {
                const resolutionStatusesResponse = await fetch('/api/changerequest-resolution-status');
                if (resolutionStatusesResponse.ok) {
                    const resolutionStatuses = await resolutionStatusesResponse.json();
                    console.log('Loaded resolution statuses:', resolutionStatuses);
                    const resolutionStatusSelect = document.getElementById('resolutionStatus');
                    if (resolutionStatusSelect) {
                        if (Array.isArray(resolutionStatuses) && resolutionStatuses.length > 0) {
                            resolutionStatuses.forEach(status => {
                                const option = document.createElement('option');
                                option.value = status.id;
                                option.textContent = status.statusName || status.primaryName || status.name || '';
                                resolutionStatusSelect.appendChild(option);
                            });
                        } else {
                            console.warn('No resolution statuses found in response');
                        }
                    } else {
                        console.warn('Resolution status select element not found');
                    }
                } else {
                    console.error('Failed to load resolution statuses:', resolutionStatusesResponse.status);
                }
            } catch (error) {
                console.error('Error loading resolution statuses:', error);
            }

        } catch (error) {
            console.error('Error loading dropdown options:', error);
        }
    }

    // Load parent change requests
    async function loadParentChangeRequests(reference, currentId) {
        try {
            const response = await fetch(`/api/changerequests?reference=${encodeURIComponent(reference)}`);
            if (response.ok) {
                const changeRequests = await response.json();
                const parentSelect = document.getElementById('parent');
                
                // Filter out the current change request
                changeRequests.forEach(cr => {
                    if (cr.id !== currentId) {
                        const option = document.createElement('option');
                        option.value = cr.id;
                        option.textContent = cr.primaryName;
                        parentSelect.appendChild(option);
                    }
                });
            }
        } catch (error) {
            console.error('Error loading parent change requests:', error);
        }
    }

    // Load change request data
    async function loadChangeRequestData(id) {
        try {
            const response = await fetch(`/api/changerequests/${id}`);
            if (!response.ok) {
                throw new Error('Failed to load change request');
            }
            
            const data = await response.json();
            originalData = data;
            console.log('Loaded change request data:', data);
            
            // Check if CR is cancelled or completed - prevent editing (backup check, should already be caught in init)
            const statusName = data.statusName || data.StatusName || '';
            const statusLower = statusName.toLowerCase();
            const crStatusId = data.crStatusId || data.CR_StatusID;
            const isCancelled = crStatusId === 3 || statusLower.includes('cancelled') || statusLower.includes('canceled');
            const isCompleted = statusLower.includes('completed');
            
            if (isCancelled || isCompleted) {
                const message = isCompleted ? 'Cannot edit a completed change request' : 'Cannot edit a cancelled change request';
                showMessage(message, true);
                // Disable all form fields
                const form = document.getElementById('changeRequestEditForm');
                if (form) {
                    const inputs = form.querySelectorAll('input, select, textarea, button');
                    inputs.forEach(input => {
                        if (input.id !== 'closeBtn') {
                            input.disabled = true;
                        }
                    });
                }
                // Redirect to view page after 1.5 seconds
                setTimeout(() => {
                    window.location.href = `/view/change-request/change-request-view.html?id=${id}`;
                }, 1500);
                return;
            }
            
            // Update page title
            const pageTitle = document.getElementById('pageTitle');
            if (pageTitle) {
                pageTitle.textContent = data.primaryName || 'Edit Change Request';
            }
            
            // Update affected item link
            const affectedItemLink = document.getElementById('affectedItemLink');
            if (affectedItemLink && data.reference) {
                affectedItemLink.textContent = data.reference;
                affectedItemLink.href = getAffectedItemUrl(data.reference);
            }
            
            // Populate form fields
            document.getElementById('title').value = data.primaryName || '';
            document.getElementById('summary').value = data.summary || '';
            
            // Set dropdown values after options are loaded
            if (data.crTypeId) {
                document.getElementById('type').value = data.crTypeId;
            }
            if (data.crSeverityId) {
                document.getElementById('severity').value = data.crSeverityId;
            }
            if (data.crUrgencyId) {
                document.getElementById('urgency').value = data.crUrgencyId;
            }
            if (data.parentId) {
                document.getElementById('parent').value = data.parentId;
            }
            
            // Set estimated values
            if (data.estimatedBenefit) {
                document.getElementById('estimatedBenefit').value = data.estimatedBenefit;
            }
            if (data.estimatedBenefitCurrency) {
                document.getElementById('estimatedBenefitCurrency').value = data.estimatedBenefitCurrency;
            }
            if (data.estimatedCost) {
                document.getElementById('estimatedCost').value = data.estimatedCost;
            }
            if (data.estimatedCostCurrency) {
                document.getElementById('estimatedCostCurrency').value = data.estimatedCostCurrency;
            }
            
            // Load parent options based on reference
            if (data.reference) {
                await loadParentChangeRequests(data.reference, id);
                // Re-set parent value after loading options
                if (data.parentId) {
                    document.getElementById('parent').value = data.parentId;
                }
            }
            
            // Load analysis and resolution data
            await loadAnalysisAndResolution(id);
            
        } catch (error) {
            console.error('Error loading change request:', error);
            showMessage('Failed to load change request: ' + error.message, true);
        }
    }

    // Load analysis and resolution data
    async function loadAnalysisAndResolution(changeRequestId) {
        try {
            // Load analysis
            const analysisResponse = await fetch(`/api/changerequest-analysis?changeRequestId=${changeRequestId}`);
            if (analysisResponse.ok) {
                const analysisList = await analysisResponse.json();
                if (analysisList && analysisList.length > 0) {
                    // Use the latest analysis (first in the list, ordered by Created_At DESC)
                    const latestAnalysis = analysisList[0];
                    const analysisField = document.getElementById('analysis');
                    if (analysisField && latestAnalysis.analysis) {
                        analysisField.value = latestAnalysis.analysis;
                    }
                }
            }

            // Load resolution
            const resolutionResponse = await fetch(`/api/changerequest-resolution?changeRequestId=${changeRequestId}`);
            if (resolutionResponse.ok) {
                const resolutionList = await resolutionResponse.json();
                if (resolutionList && resolutionList.length > 0) {
                    // Use the latest resolution (first in the list, ordered by Created_At DESC)
                    const latestResolution = resolutionList[0];
                    const resolutionField = document.getElementById('resolution');
                    const resolutionStatusField = document.getElementById('resolutionStatus');
                    if (resolutionField && latestResolution.description) {
                        resolutionField.value = latestResolution.description;
                    }
                    if (resolutionStatusField && latestResolution.resolutionStatusId) {
                        resolutionStatusField.value = latestResolution.resolutionStatusId;
                    }
                }
            }
        } catch (error) {
            console.error('Error loading analysis/resolution:', error);
            // Don't show error to user, just log it
        }
    }

    // Get affected item URL from reference
    function getAffectedItemUrl(reference) {
        if (!reference) return '#';
        
        const match = reference.match(/^(.+?)\s+(\d+)$/);
        if (match) {
            const facetName = match[1].trim();
            const facetId = match[2];
            
            const facetType = facetName.toLowerCase()
                .replace(/\s+/g, '-')
                .replace(/^data-set$/, 'dataset');
            
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
            if (viewPath) {
                return `${viewPath}/${facetId}`;
            }
        }
        
        return '#';
    }

    // Save change request
    async function saveChangeRequest(closeAfterSave = false) {
        const form = document.getElementById('changeRequestEditForm');
        if (customFieldsContext && !customFieldsContext.validate()) {
            showMessage('Please correct custom field validation errors', true);
            return;
        }

        // Sync rich-text editor content back to textarea before reading
        if (typeof syncAdvancedRichTextToTextarea === 'function') {
            syncAdvancedRichTextToTextarea('summary');
        }

        // Validate required fields
        const title = document.getElementById('title').value.trim();
        const type = document.getElementById('type').value;
        const summary = document.getElementById('summary').value.trim();
        const severity = document.getElementById('severity').value;
        const urgency = document.getElementById('urgency').value;
        
        if (!title || !type || !summary || !severity || !urgency) {
            showMessage('Please fill in all required fields', true);
            return;
        }
        
        // Build update data
        const updateData = {
            primaryName: title,
            summary: summary,
            crTypeId: parseInt(type),
            crSeverityId: parseInt(severity),
            crUrgencyId: parseInt(urgency)
        };
        
        // Optional fields
        const parent = document.getElementById('parent').value;
        if (parent) {
            updateData.parentId = parseInt(parent);
        }
        
        const estimatedBenefit = document.getElementById('estimatedBenefit').value;
        if (estimatedBenefit) {
            updateData.estimatedBenefit = parseFloat(estimatedBenefit);
            updateData.estimatedBenefitCurrency = document.getElementById('estimatedBenefitCurrency').value;
        }
        
        const estimatedCost = document.getElementById('estimatedCost').value;
        if (estimatedCost) {
            updateData.estimatedCost = parseFloat(estimatedCost);
            updateData.estimatedCostCurrency = document.getElementById('estimatedCostCurrency').value;
        }
        
        // Add analysis and resolution
        const analysis = document.getElementById('analysis').value.trim();
        if (analysis) {
            updateData.analysis = analysis;
        }
        
        const resolutionStatus = document.getElementById('resolutionStatus').value;
        if (resolutionStatus) {
            updateData.resolutionStatusId = parseInt(resolutionStatus);
        }
        
        const resolution = document.getElementById('resolution').value.trim();
        if (resolution) {
            updateData.resolution = resolution;
        }
        
        try {
            // First, save pending relationships if any
            if (pendingRelationships.length > 0) {
                console.log('[CR Edit] Saving', pendingRelationships.length, 'pending relationships');
                
                // Process creates
                const creates = pendingRelationships.filter(rel => rel.operation === 'create');
                for (const rel of creates) {
                    try {
                        const relationshipData = {
                            sourceId: rel.sourceId || currentChangeRequestId,
                            targetId: rel.targetId,
                            crRelationshipTypeId: rel.crRelationshipTypeId
                        };
                        
                        const response = await fetch('/api/cr-relationships/', {
                            method: 'POST',
                            headers: {
                                'Content-Type': 'application/json'
                            },
                            body: JSON.stringify(relationshipData)
                        });
                        
                        if (!response.ok) {
                            const errorData = await response.json();
                            throw new Error(errorData.error || 'Failed to create relationship');
                        }
                    } catch (error) {
                        console.error('Error creating relationship:', error);
                        throw new Error('Failed to save relationships: ' + error.message);
                    }
                }
                
                // Process updates
                const updates = pendingRelationships.filter(rel => rel.operation === 'update' && rel.id);
                for (const rel of updates) {
                    try {
                        const relationshipData = {
                            targetId: rel.targetId,
                            crRelationshipTypeId: rel.crRelationshipTypeId
                        };
                        
                        const response = await fetch(`/api/cr-relationships/${rel.id}`, {
                            method: 'PUT',
                            headers: {
                                'Content-Type': 'application/json'
                            },
                            body: JSON.stringify(relationshipData)
                        });
                        
                        if (!response.ok) {
                            const errorData = await response.json();
                            throw new Error(errorData.error || 'Failed to update relationship');
                        }
                    } catch (error) {
                        console.error('Error updating relationship:', error);
                        throw new Error('Failed to save relationships: ' + error.message);
                    }
                }
                
                // Process deletes
                const deletes = pendingRelationships.filter(rel => rel.operation === 'delete' && rel.id);
                for (const rel of deletes) {
                    try {
                        const response = await fetch(`/api/cr-relationships/${rel.id}`, {
                            method: 'DELETE',
                            credentials: 'include',
                            headers: {
                                'Content-Type': 'application/json'
                            }
                        });
                        
                        if (!response.ok && response.status !== 204) {
                            const errorData = await response.json().catch(() => ({}));
                            throw new Error(errorData.error || 'Failed to delete relationship');
                        }
                    } catch (error) {
                        console.error('Error deleting relationship:', error);
                        throw new Error('Failed to save relationships: ' + error.message);
                    }
                }
                
                // Clear pending relationships after successful save
                pendingRelationships = [];
                
                // Reload relationships to reflect changes
                await loadRelationships();
            }
            
            // Then save the change request itself
            const response = await fetch(`/api/changerequests/${currentChangeRequestId}`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(updateData)
            });
            
            if (!response.ok) {
                const errorData = await response.json();
                throw new Error(errorData.error || 'Failed to save change request');
            }

            if (customFieldsContext) {
                await customFieldsContext.saveValues(currentChangeRequestId);
            }
            
            showMessage('Change request saved successfully');
            
            if (closeAfterSave) {
                // Navigate back to view page
                setTimeout(() => {
                    window.location.href = `/view/change-request/change-request-view.html?id=${currentChangeRequestId}`;
                }, 500);
            }
            
        } catch (error) {
            console.error('Error saving change request:', error);
            showMessage('Failed to save: ' + error.message, true);
        }
    }

    // Setup tab switching
    function setupTabs() {
        const tabs = document.querySelectorAll('.tab');
        tabs.forEach(tab => {
            tab.addEventListener('click', function() {
                const tabName = this.getAttribute('data-tab');
                
                // Update active tab
                tabs.forEach(t => t.classList.remove('active'));
                this.classList.add('active');
                
                // Show corresponding content
                document.querySelectorAll('.tab-content').forEach(content => {
                    content.classList.remove('active');
                });
                
                const targetContent = document.getElementById(tabName + 'Tab');
                if (targetContent) {
                    targetContent.classList.add('active');
                    
                    // Load relationships when relationships tab is activated
                    if (tabName === 'relationships') {
                        loadRelationships();
                    }
                }
            });
        });
    }

    // Load relationships for the current change request
    // Flag to prevent multiple simultaneous calls to loadRelationships
    let isLoadingRelationships = false;
    
    async function loadRelationships() {
        // Prevent multiple simultaneous calls
        if (isLoadingRelationships) {
            console.log('[CR Edit] loadRelationships already in progress, skipping...');
            return;
        }
        
        if (!currentChangeRequestId) return;
        
        isLoadingRelationships = true;
        
        // Hide the add relationship form by default
        const addForm = document.getElementById('addRelationshipForm');
        if (addForm) {
            addForm.style.display = 'none';
        }
        
        try {
            const response = await fetch(`/api/cr-relationships/${currentChangeRequestId}`);
            if (response.ok) {
                const data = await response.json();
                console.log('[CR Edit] Loaded relationships from API:', data);
                
                // Remove duplicates based on relationship ID or unique combination
                const uniqueRelationships = [];
                const seenIds = new Set();
                const seenCombinations = new Set();
                
                if (Array.isArray(data)) {
                    console.log('[CR Edit] Processing', data.length, 'relationships for deduplication');
                    data.forEach((rel, index) => {
                        const relId = rel.id || rel.ID;
                        const sourceId = rel.sourceId || currentChangeRequestId;
                        const targetId = rel.targetId;
                        const typeId = rel.crRelationshipTypeId;
                        
                        console.log(`[CR Edit] Relationship ${index}: id=${relId}, sourceId=${sourceId}, targetId=${targetId}, typeId=${typeId}`);
                        
                        if (relId) {
                            // Use ID as primary key
                            if (!seenIds.has(relId)) {
                                seenIds.add(relId);
                                uniqueRelationships.push(rel);
                                console.log(`[CR Edit] Added relationship with ID ${relId}`);
                            } else {
                                console.warn('[CR Edit] Duplicate relationship ID found, skipping:', relId, rel);
                            }
                        } else {
                            // If no ID, check for duplicate based on sourceId + targetId + type
                            // This is the true unique combination for a relationship
                            const combinationKey = `${sourceId}_${targetId}_${typeId}`;
                            
                            if (!seenCombinations.has(combinationKey)) {
                                seenCombinations.add(combinationKey);
                                uniqueRelationships.push(rel);
                                console.log(`[CR Edit] Added relationship with combination ${combinationKey}`);
                            } else {
                                console.warn('[CR Edit] Duplicate relationship combination found, skipping:', combinationKey, rel);
                            }
                        }
                    });
                }
                
                console.log('[CR Edit] Unique relationships after deduplication:', uniqueRelationships);
                relationships = uniqueRelationships;
                renderRelationships();
            }
        } catch (error) {
            console.error('Error loading relationships:', error);
        } finally {
            isLoadingRelationships = false;
        }
    }

    // Render relationships table
    function renderRelationships() {
        const tableBody = document.getElementById('relationshipsTableBody');
        const emptyState = document.getElementById('emptyRelationships');
        const table = document.getElementById('relationshipsTable');
        
        // If markup is not present (current page uses placeholder), bail out safely
        if (!tableBody || !emptyState || !table) {
            console.warn('[ChangeRequestEdit] Relationships UI not present, skipping render');
            return;
        }
        
        // Combine relationships and pendingRelationships for display
        // Filter out relationships that are marked for deletion in pending
        const pendingDeleteIds = new Set(pendingRelationships
            .filter(rel => rel.operation === 'delete')
            .map(rel => rel.id || rel.tempId));
        
        const activeRelationships = relationships.filter(rel => {
            const relId = rel.id || rel.ID;
            return !pendingDeleteIds.has(relId);
        });
        
        // Combine with pending create/update relationships
        const allRelationships = [...activeRelationships];
        pendingRelationships.forEach(pendingRel => {
            if (pendingRel.operation === 'create' || pendingRel.operation === 'update') {
                allRelationships.push(pendingRel);
            }
        });
        
        if (allRelationships.length === 0) {
            table.style.display = 'none';
            emptyState.style.display = 'block';
            return;
        }
        
        table.style.display = 'table';
        emptyState.style.display = 'none';
        
        tableBody.innerHTML = '';
        
        // Additional deduplication check before rendering
        const renderedIds = new Set();
        const renderedCombinations = new Set();
        const uniqueRelationships = allRelationships.filter(relationship => {
            const relId = relationship.id || relationship.ID || relationship.tempId;
            if (relId) {
                if (renderedIds.has(relId)) {
                    console.warn('[CR Edit] Duplicate relationship ID detected during render:', relId, relationship);
                    return false;
                }
                renderedIds.add(relId);
                return true;
            }
            // If no ID, use combination of sourceId + targetId + type
            const sourceId = relationship.sourceId || currentChangeRequestId;
            const targetId = relationship.targetId;
            const typeId = relationship.crRelationshipTypeId;
            const combinationKey = `${sourceId}_${targetId}_${typeId}`;
            
            if (renderedCombinations.has(combinationKey)) {
                console.warn('[CR Edit] Duplicate relationship combination detected during render:', combinationKey, relationship);
                return false;
            }
            renderedCombinations.add(combinationKey);
            return true;
        });
        
        console.log('[CR Edit] Rendering relationships:', uniqueRelationships.length, 'out of', allRelationships.length);
        
        uniqueRelationships.forEach(relationship => {
            const row = document.createElement('tr');
            
            // Check if this is a pending relationship
            const isPending = relationship.tempId || (relationship.operation && relationship.operation !== '');
            if (isPending) {
                row.style.opacity = '0.7';
                row.style.fontStyle = 'italic';
            }
            
            // Extract object type from reference or use stored value
            let objectType = relationship.targetChangeRequestReference 
                ? extractObjectTypeFromReference(relationship.targetChangeRequestReference)
                : (relationship.objectTypeName || relationship.objectType || 'Unknown');
            
            // Get relationship type name
            const relationshipTypeName = relationship.relationshipTypeName || 
                relationshipTypes.find(t => t.id === relationship.crRelationshipTypeId)?.name || 
                'Unknown';
            
            // Get target CR name
            const targetCRName = relationship.targetChangeRequestTitle || 
                relationship.targetCRName || 
                'Unknown';
            
            // Get target ID
            const targetId = relationship.targetId;
            
            // Get identifier for edit/delete (use tempId for pending, id for existing)
            const relIdentifier = relationship.tempId || relationship.id || relationship.ID;
            const isTemp = !!relationship.tempId;
            
            row.innerHTML = `
                <td>
                    <span class="relationship-type-badge">${escapeHtml(relationshipTypeName)}</span>
                    ${isPending ? '<span style="color: #f59e0b; margin-left: 5px;" title="Pending save">●</span>' : ''}
                </td>
                <td>
                    <span class="object-type-badge">${escapeHtml(objectType)}</span>
                </td>
                <td>
                    ${targetId ? `<a href="/view/change-request/change-request-view.html?id=${targetId}" 
                       class="related-cr-link" target="_blank">
                        ${escapeHtml(targetCRName)}
                    </a>` : escapeHtml(targetCRName)}
                </td>
                <td style="display: flex; gap: 0.5rem;">
                    <button type="button" class="edit-relationship-btn" 
                            onclick="editRelationship('${relIdentifier}', ${relationship.crRelationshipTypeId}, ${targetId || 'null'}, '${escapeHtml(objectType)}', ${isTemp ? 'true' : 'false'})" 
                            title="Edit Relationship">
                        <i class="fas fa-edit"></i>
                    </button>
                    <button type="button" class="delete-relationship-btn" 
                            onclick="deleteRelationship('${relIdentifier}', ${isTemp ? 'true' : 'false'})" 
                            title="Delete Relationship">
                        <i class="fas fa-trash"></i>
                    </button>
                </td>
            `;
            
            tableBody.appendChild(row);
        });
    }

    // Extract object type from reference string
    function extractObjectTypeFromReference(reference) {
        if (!reference) return 'Unknown';
        const match = reference.match(/^(.+?)\s+\d+$/);
        return match ? match[1].trim() : 'Unknown';
    }

    // Edit relationship
    window.editRelationship = async function(relationshipId, relationshipTypeId, targetId, targetReference, isTemp = false) {
        editingRelationshipId = relationshipId;
        
        // Show the form
        const form = document.getElementById('addRelationshipForm');
        form.style.display = 'block';
        
        // Extract object type from reference (if it's a string) or use as-is
        const objectType = typeof targetReference === 'string' 
            ? extractObjectTypeFromReference(targetReference) 
            : targetReference;
        
        // Set form values
        document.getElementById('relationshipType').value = relationshipTypeId;
        document.getElementById('objectType').value = objectType;
        
        // Load related change requests for the object type
        try {
            const response = await fetch(`/api/cr-relationships-by-reference?reference=${encodeURIComponent(objectType)}`);
            if (response.ok) {
                const changeRequests = await response.json();
                const relatedSelect = document.getElementById('relatedChangeRequest');
                
                relatedSelect.innerHTML = '<option value="">Please select</option>';
                changeRequests.forEach(cr => {
                    const option = document.createElement('option');
                    option.value = cr.targetId;
                    option.textContent = cr.targetChangeRequestTitle;
                    if (cr.targetId === targetId || (targetId && parseInt(cr.targetId) === parseInt(targetId))) {
                        option.selected = true;
                    }
                    relatedSelect.appendChild(option);
                });
                
                relatedSelect.disabled = false;
            }
        } catch (error) {
            console.error('Error loading change requests:', error);
        }
        
        // Update button text
        document.getElementById('saveRelationshipBtn').textContent = 'Update';
    };
    
    // Delete relationship
    window.deleteRelationship = async function(relationshipId, isTemp = false) {
        const confirmed = await (typeof window.showConfirmDialog === 'function'
            ? window.showConfirmDialog({ message: 'Are you sure you want to delete this relationship?', type: 'warning' })
            : Promise.resolve(confirm('Are you sure you want to delete this relationship?')));
        if (!confirmed) {
            return;
        }
        
        if (isTemp) {
            // Remove from pendingRelationships
            pendingRelationships = pendingRelationships.filter(rel => rel.tempId !== relationshipId);
            renderRelationships();
            showMessage('Relationship removed (will be saved when you click Save above)');
            return;
        }
        
        // Check if it's in pendingRelationships as create/update
        const pendingIndex = pendingRelationships.findIndex(rel => 
            (rel.id && rel.id.toString() === relationshipId.toString()) || 
            (rel.tempId && rel.tempId === relationshipId)
        );
        
        if (pendingIndex !== -1) {
            // Remove from pending
            pendingRelationships.splice(pendingIndex, 1);
            renderRelationships();
            showMessage('Relationship removed (will be saved when you click Save above)');
            return;
        }
        
        // It's an existing DB relationship - mark for deletion in pending
        const existingRel = relationships.find(rel => (rel.id || rel.ID) && (rel.id || rel.ID).toString() === relationshipId.toString());
        if (existingRel) {
            pendingRelationships.push({
                id: existingRel.id || existingRel.ID,
                sourceId: existingRel.sourceId || currentChangeRequestId,
                targetId: existingRel.targetId,
                crRelationshipTypeId: existingRel.crRelationshipTypeId,
                operation: 'delete'
            });
            renderRelationships();
            showMessage('Relationship marked for deletion (will be saved when you click Save above)');
            return;
        }
        
        // Fallback: delete directly from DB (shouldn't happen in auto CR, but keep for safety)
        try {
            const response = await fetch(`/api/cr-relationships/${relationshipId}`, {
                method: 'DELETE'
            });
            
            if (response.ok) {
                showMessage('Relationship deleted successfully');
                // Reload the table after a short delay
                setTimeout(() => {
                    loadRelationships();
                }, 300);
            } else {
                throw new Error('Failed to delete relationship');
            }
        } catch (error) {
            console.error('Error deleting relationship:', error);
            showMessage('Failed to delete relationship: ' + error.message, true);
        }
    };

    // Setup relationships form handlers
    // Track if handlers are already set up to prevent duplicates
    let relationshipsHandlersSetup = false;
    
    function setupRelationshipsHandlers() {
        // Prevent duplicate setup
        if (relationshipsHandlersSetup) {
            console.log('[CR Edit] Relationships handlers already setup, skipping...');
            return;
        }
        
        // Remove existing event listeners by cloning elements (this removes all listeners)
        const addBtn = document.getElementById('addRelationshipBtn');
        const objectTypeSelect = document.getElementById('objectType');
        const saveBtn = document.getElementById('saveRelationshipBtn');
        const cancelBtn = document.getElementById('cancelRelationshipBtn');
        
        // Clone and replace to remove all event listeners
        if (addBtn && addBtn.parentNode) {
            const newAddBtn = addBtn.cloneNode(true);
            addBtn.parentNode.replaceChild(newAddBtn, addBtn);
        }
        if (objectTypeSelect && objectTypeSelect.parentNode) {
            const newObjectTypeSelect = objectTypeSelect.cloneNode(true);
            objectTypeSelect.parentNode.replaceChild(newObjectTypeSelect, objectTypeSelect);
        }
        if (saveBtn && saveBtn.parentNode) {
            const newSaveBtn = saveBtn.cloneNode(true);
            saveBtn.parentNode.replaceChild(newSaveBtn, saveBtn);
        }
        if (cancelBtn && cancelBtn.parentNode) {
            const newCancelBtn = cancelBtn.cloneNode(true);
            cancelBtn.parentNode.replaceChild(newCancelBtn, cancelBtn);
        }
        
        // Add relationship button
        document.getElementById('addRelationshipBtn').addEventListener('click', function() {
            const form = document.getElementById('addRelationshipForm');
            form.style.display = form.style.display === 'none' ? 'block' : 'none';
            
            if (form.style.display === 'block') {
                // Reset form for adding new relationship
                editingRelationshipId = null;
                document.getElementById('relationshipType').value = '';
                document.getElementById('objectType').value = '';
                document.getElementById('relatedChangeRequest').value = '';
                document.getElementById('relatedChangeRequest').disabled = true;
                document.getElementById('relatedChangeRequest').innerHTML = '<option value="">Select object type first</option>';
                document.getElementById('saveRelationshipBtn').textContent = 'Save';
            }
        });
        
        // Object type change handler
        document.getElementById('objectType').addEventListener('change', async function() {
            const objectType = this.value;
            const relatedSelect = document.getElementById('relatedChangeRequest');
            
            if (!objectType) {
                relatedSelect.disabled = true;
                relatedSelect.innerHTML = '<option value="">Select object type first</option>';
                return;
            }
            
            try {
                // Load change requests for the selected object type
                const response = await fetch(`/api/cr-relationships-by-reference?reference=${encodeURIComponent(objectType)}`);
                if (response.ok) {
                    const changeRequests = await response.json();
                    
                    relatedSelect.innerHTML = '<option value="">Please select</option>';
                    
                    // Filter out current change request
                    changeRequests.forEach(cr => {
                        if (cr.targetId !== currentChangeRequestId) {
                            const option = document.createElement('option');
                            option.value = cr.targetId;
                            option.textContent = cr.targetChangeRequestTitle;
                            relatedSelect.appendChild(option);
                        }
                    });
                    
                    relatedSelect.disabled = false;
                } else {
                    throw new Error('Failed to load change requests');
                }
            } catch (error) {
                console.error('Error loading change requests:', error);
                relatedSelect.innerHTML = '<option value="">Error loading options</option>';
            }
        });
        
        // Save relationship button (handles both create and update)
        let isSavingRelationship = false;
        document.getElementById('saveRelationshipBtn').addEventListener('click', async function() {
            // Prevent duplicate saves
            if (isSavingRelationship) {
                console.warn('[CR Edit] Save relationship already in progress, ignoring duplicate click');
                return;
            }
            
            const relationshipTypeId = document.getElementById('relationshipType').value;
            const targetId = document.getElementById('relatedChangeRequest').value;
            
            if (!relationshipTypeId || !targetId) {
                showMessage('Please fill in all required fields', true);
                return;
            }
            
            // Check for duplicate relationship before saving
            const targetIdInt = parseInt(targetId);
            const relationshipTypeIdInt = parseInt(relationshipTypeId);
            const duplicateExists = relationships.some(rel => {
                const relTargetId = rel.targetId;
                const relTypeId = rel.crRelationshipTypeId;
                const relSourceId = rel.sourceId;
                // Check if this exact relationship already exists (and we're not editing it)
                if (!editingRelationshipId) {
                    return relTargetId === targetIdInt && 
                           relTypeId === relationshipTypeIdInt &&
                           relSourceId === currentChangeRequestId;
                } else {
                    // When editing, check if another relationship with same values exists (excluding current one)
                    return rel.id !== editingRelationshipId &&
                           relTargetId === targetIdInt && 
                           relTypeId === relationshipTypeIdInt &&
                           relSourceId === currentChangeRequestId;
                }
            });
            
            if (duplicateExists) {
                showMessage('This relationship already exists. Please choose a different relationship.', true);
                return;
            }
            
            isSavingRelationship = true;
            const saveBtn = document.getElementById('saveRelationshipBtn');
            const originalText = saveBtn.textContent;
            saveBtn.disabled = true;
            saveBtn.textContent = 'Saving...';
            
            try {
                const relationshipData = {
                    targetId: targetIdInt,
                    crRelationshipTypeId: relationshipTypeIdInt,
                    sourceId: currentChangeRequestId
                };
                
                // Get relationship type name for display
                const relationshipType = relationshipTypes.find(t => t.id === relationshipTypeIdInt);
                const relationshipTypeName = relationshipType ? relationshipType.name : 'Unknown';
                
                // Get target CR name for display
                const relatedSelect = document.getElementById('relatedChangeRequest');
                const selectedOption = relatedSelect.options[relatedSelect.selectedIndex];
                const targetCRName = selectedOption ? selectedOption.textContent : 'Unknown';
                
                // Get object type from the form
                const objectTypeSelect = document.getElementById('objectType');
                const objectType = objectTypeSelect ? objectTypeSelect.value : '';
                
                if (editingRelationshipId) {
                    // Update existing relationship in pending list or in DB relationships
                    // First check if it's in pendingRelationships
                    const pendingIndex = pendingRelationships.findIndex(rel => rel.tempId === editingRelationshipId);
                    if (pendingIndex !== -1) {
                        // Update in pending list
                        pendingRelationships[pendingIndex] = {
                            ...pendingRelationships[pendingIndex],
                            targetId: targetIdInt,
                            crRelationshipTypeId: relationshipTypeIdInt,
                            targetCRName: targetCRName,
                            relationshipTypeName: relationshipTypeName,
                            objectType: objectType,
                            objectTypeName: objectType
                        };
                    } else {
                        // It's an existing DB relationship - mark for update in pending
                        const existingRel = relationships.find(rel => rel.id === editingRelationshipId);
                        if (existingRel) {
                            // Remove from relationships and add to pending as update
                            const updateIndex = pendingRelationships.findIndex(rel => rel.id === editingRelationshipId);
                            if (updateIndex === -1) {
                                pendingRelationships.push({
                                    id: editingRelationshipId,
                                    sourceId: currentChangeRequestId,
                                    targetId: targetIdInt,
                                    crRelationshipTypeId: relationshipTypeIdInt,
                                    targetCRName: targetCRName,
                                    relationshipTypeName: relationshipTypeName,
                                    objectType: objectType,
                                    objectTypeName: objectType,
                                    operation: 'update'
                                });
                            } else {
                                pendingRelationships[updateIndex] = {
                                    ...pendingRelationships[updateIndex],
                                    targetId: targetIdInt,
                                    crRelationshipTypeId: relationshipTypeIdInt,
                                    targetCRName: targetCRName,
                                    relationshipTypeName: relationshipTypeName,
                                    objectType: objectType,
                                    objectTypeName: objectType,
                                    operation: 'update'
                                };
                            }
                        }
                    }
                    
                    showMessage('Relationship updated (will be saved when you click Save above)');
                    document.getElementById('addRelationshipForm').style.display = 'none';
                    editingRelationshipId = null;
                    document.getElementById('saveRelationshipBtn').textContent = 'Save';
                    // Re-render to show updated relationship
                    renderRelationships();
                } else {
                    // Create new relationship - add to pending list only
                    const tempId = 'temp_' + Date.now() + '_' + Math.random();
                    pendingRelationships.push({
                        tempId: tempId,
                        sourceId: currentChangeRequestId,
                        targetId: targetIdInt,
                        crRelationshipTypeId: relationshipTypeIdInt,
                        targetCRName: targetCRName,
                        relationshipTypeName: relationshipTypeName,
                        objectType: objectType,
                        objectTypeName: objectType,
                        operation: 'create'
                    });
                    
                    showMessage('Relationship added (will be saved when you click Save above)');
                    document.getElementById('addRelationshipForm').style.display = 'none';
                    // Clear form
                    document.getElementById('relationshipType').value = '';
                    document.getElementById('objectType').value = '';
                    document.getElementById('relatedChangeRequest').value = '';
                    document.getElementById('relatedChangeRequest').disabled = true;
                    document.getElementById('relatedChangeRequest').innerHTML = '<option value="">Select object type first</option>';
                    // Re-render to show new relationship
                    renderRelationships();
                }
            } catch (error) {
                console.error('Error saving relationship:', error);
                showMessage('Failed to save relationship: ' + error.message, true);
            } finally {
                isSavingRelationship = false;
                const saveBtn = document.getElementById('saveRelationshipBtn');
                if (saveBtn) {
                    saveBtn.disabled = false;
                    saveBtn.textContent = originalText;
                }
            }
        });
        
        // Cancel relationship button
        document.getElementById('cancelRelationshipBtn').addEventListener('click', function() {
            document.getElementById('addRelationshipForm').style.display = 'none';
            editingRelationshipId = null;
            document.getElementById('saveRelationshipBtn').textContent = 'Save';
        });
        
        // Mark as setup
        relationshipsHandlersSetup = true;
    }

    // Setup event listeners
    function setupEventListeners() {
        // Save button
        document.getElementById('saveBtn').addEventListener('click', function() {
            saveChangeRequest(false);
        });
        
        // Save & Close button
        document.getElementById('saveAndCloseBtn').addEventListener('click', function() {
            saveChangeRequest(true);
        });
        
        // Close button
        document.getElementById('closeBtn').addEventListener('click', function() {
            // Navigate back to view page
            window.location.href = `/view/change-request/change-request-view.html?id=${currentChangeRequestId}`;
        });
        
        // Show Editor button (placeholder)
        document.getElementById('showEditorBtn').addEventListener('click', function(e) {
            e.preventDefault();
            e.stopPropagation();
            toggleAdvancedRichTextEditor('summary', document.getElementById('showEditorBtn'));
        });
    }

    // Initialize
    async function init() {
        currentChangeRequestId = parseId();
        
        if (!currentChangeRequestId) {
            showMessage('No change request ID provided', true);
            return;
        }
        
        console.log('Editing change request ID:', currentChangeRequestId);
        
        // First, check if CR is cancelled or completed - redirect immediately if so
        try {
            const checkResponse = await fetch(`/api/changerequests/${currentChangeRequestId}`);
            if (checkResponse.ok) {
                const crData = await checkResponse.json();
                const statusName = crData.statusName || crData.StatusName || '';
                const statusLower = statusName.toLowerCase();
                const crStatusId = crData.crStatusId || crData.CR_StatusID;
                const isCancelled = crStatusId === 3 || statusLower.includes('cancelled') || statusLower.includes('canceled');
                const isCompleted = statusLower.includes('completed');
                
                if (isCancelled || isCompleted) {
                    const message = isCompleted ? 'Cannot edit a completed change request' : 'Cannot edit a cancelled change request';
                    showMessage(message, true);
                    // Redirect to view page immediately
                    setTimeout(() => {
                        window.location.href = `/view/change-request/change-request-view.html?id=${currentChangeRequestId}`;
                    }, 1500);
                    return;
                }
            }
        } catch (error) {
            console.error('Error checking CR status:', error);
            // Continue with normal flow if check fails
        }
        
        // Load dropdown options first
        await loadDropdownOptions();
        
        // Then load change request data
        await loadChangeRequestData(currentChangeRequestId);

        if (window.CustomFields && typeof window.CustomFields.initForm === 'function') {
            try {
                customFieldsContext = await window.CustomFields.initForm({
                    facetId: 'Change Requests',
                    containerId: 'customFieldsContainer',
                    mode: 'edit',
                    objectId: Number(currentChangeRequestId)
                });
            } catch (cfErr) {
                console.error('Change request custom fields (edit):', cfErr);
            }
        }
        
        // Setup tabs
        setupTabs();
        
        // Setup event listeners
        setupEventListeners();

        // Setup relationship form/listeners now that DOM is ready
        setupRelationshipsHandlers();
    }

    // Run on DOM ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();

