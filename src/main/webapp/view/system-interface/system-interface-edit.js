(function() {
    function parseId() {
        const urlParams = new URLSearchParams(window.location.search);
        const id = urlParams.get('id');
        return id ? parseInt(id, 10) : null;
    }

    // Get active tab from URL parameters
    function getActiveTab() {
        const urlParams = new URLSearchParams(window.location.search);
        return urlParams.get('tab') || 'summary';
    }

    // Get active subtab from URL parameters
    function getActiveSubTabFromURL() {
        const urlParams = new URLSearchParams(window.location.search);
        return urlParams.get('subtab');
    }

    // Get current active tab from DOM
    function getCurrentActiveTab() {
        const activeTab = document.querySelector('.tab.active');
        return activeTab ? activeTab.getAttribute('data-tab') : 'summary';
    }

    // Update URL for tab and subtab
    function updateURLForTab(tabName, subTabName = null) {
        const id = parseId();
        if (!id) return;
        const url = new URL(window.location);
        url.searchParams.set('tab', tabName);
        if (subTabName) {
            url.searchParams.set('subtab', subTabName);
        } else {
            url.searchParams.delete('subtab');
        }
        window.history.pushState({ tab: tabName, subtab: subTabName }, '', url);
    }
    
    // Make updateURLForTab available globally for subtabs
    window.updateInterfaceTabURL = updateURLForTab;

    function escapeHtml(str) {
        if (str == null) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

        // Store original interface data to preserve unchanged fields
        let originalInterfaceData = null;
        
        // Store lookup data for converting names to IDs
        let lookupData = {
            statuses: [],
            lifecycles: [],
            viewings: [],
            automations: [],
            frequencies: [],
            transferMethods: [],
            transferFormats: [],
            classifications: []
        };
        
        // Store interface data
        let currentInterfaceId = null;
        let hasChanges = false;
        let originalInterfaceSegmentId = null; // segment loaded from server; used to detect segment change

        function normalizeInterfaceSegmentId(value) {
            if (value == null || value === '') return null;
            const n = parseInt(value, 10);
            return Number.isInteger(n) ? n : null;
        }

        function getInterfaceCurrentSegmentId() {
            const container = document.getElementById('segmentFieldContainer');
            if (container) {
                const inst = container._segmentFieldInstance;
                if (inst && typeof inst.getValue === 'function') return normalizeInterfaceSegmentId(inst.getValue());
            }
            return null;
        }
        
        // Store glossary data
        let glossaryData = [];
        let relationTypes = [];
        
        // Tab management
        let currentTab = 'summary';

    // Track changes in form
    function markAsChanged() {
        hasChanges = true;
        console.log('Form marked as changed');
    }

    // Reset changes flag
    function resetChanges() {
        hasChanges = false;
        console.log('Changes flag reset');
    }

    // Check if there are any changes
    function hasFormChanges() {
        let formHasChanges = hasChanges;
        
        // Check if stakeholders data has changed
        if (window.InterfaceStakeholderEdit && window.InterfaceStakeholderEdit.hasDataChanged) {
            const stakeholdersChanged = window.InterfaceStakeholderEdit.hasDataChanged();
            if (stakeholdersChanged) {
                formHasChanges = true;
            }
        }
        
        // Check if impact data has changed (only if impact tab is active)
        const activeTab = document.querySelector('.tab.active');
        const tabName = activeTab ? activeTab.getAttribute('data-tab') : 'summary';
        if (tabName === 'impact' && window.hasImpactChanges) {
            const impactChanged = window.hasImpactChanges();
            if (impactChanged) {
                formHasChanges = true;
            }
        }
        
        return formHasChanges;
    }

    async function updateDerivedSegmentDisplayFromTarget() {
        const display = document.getElementById('segmentDisplay');
        const targetInput = document.getElementById('targetShortName');
        const targetSystemId = targetInput?.dataset?.systemId;

        if (!targetSystemId) {
            if (display) {
                display.value = 'Enterprise';
            }
            return;
        }

        try {
            const response = await window.BUDG_API_SERVICE.getSystemById(targetSystemId);
            const system = response?.data || response;
            const segmentId = parseInt(system?.segmentId ?? system?.segment_id ?? system?.Segment_ID ?? 1, 10);
            const segmentName = system?.segmentName || system?.segment_name || system?.Segment_Name || (segmentId === 1 ? 'Enterprise' : '');
            if (display) {
                display.value = segmentName || 'Enterprise';
            }
        } catch (error) {
            console.error('Error deriving segment from target system:', error);
        }
    }

    async function loadInterfaceData(id, segmentField = null) {
        try {
            const interfaceData = await window.BUDG_API_SERVICE.getInterfaceById(id);

            // Store original data for later use
            originalInterfaceData = interfaceData;
            
            // Reset changes flag when loading data
            resetChanges();
            window.originalInterfaceData = interfaceData;
            console.log('Stored original interface data:', interfaceData);

            // Debug: Log the interface data to see what fields are available
            console.log('Interface data loaded:', interfaceData);
            console.log('Interface data type:', typeof interfaceData);
            console.log('Interface data keys:', interfaceData ? Object.keys(interfaceData) : 'No data');
            console.log('Interface ID from URL:', id);

            // Log specific fields we need
            console.log('Available fields:', {
                asset_id: interfaceData?.asset_id,
                status_id: interfaceData?.status_id,
                lifecycle_id: interfaceData?.lifecycle_id,
                is_public: interfaceData?.is_public,
                automation_id: interfaceData?.automation_id,
                frequency_id: interfaceData?.frequency_id,
                transfer_method_id: interfaceData?.transfer_method_id,
                transfer_format_id: interfaceData?.transfer_format_id,
                classification_id: interfaceData?.classification_id,
                // System IDs - check all possible variations
                sourceSystemId: interfaceData?.sourceSystemId,
                source_system_id: interfaceData?.source_system_id,
                source_systemID: interfaceData?.source_systemID,
                Source_systemID: interfaceData?.Source_systemID,
                sourceSystemID: interfaceData?.sourceSystemID,
                targetSystemId: interfaceData?.targetSystemId,
                target_system_id: interfaceData?.target_system_id,
                target_systemID: interfaceData?.target_systemID,
                Target_systemID: interfaceData?.Target_systemID,
                targetSystemID: interfaceData?.targetSystemID,
                // System names
                sourceName: interfaceData?.sourceName,
                targetName: interfaceData?.targetName
            });

            // Update header
            const titleElement = document.getElementById('interfaceTitle');
            const breadcrumbElement = document.getElementById('interfaceBreadcrumb');

            if (titleElement) {
                titleElement.textContent = `From ${escapeHtml(interfaceData.sourceName || '')} To ${escapeHtml(interfaceData.targetName || '')}`;
            }
            if (breadcrumbElement) {
                breadcrumbElement.textContent = 'Interface';
            }

            // Populate form fields with correct database mapping
            document.getElementById('interfaceName').value = interfaceData.name || '';
            document.getElementById('interfaceRef').value = interfaceData.ref || interfaceData.ref_number || '';
            document.getElementById('interfaceDescription').value = interfaceData.description || '';
            document.getElementById('syncControl').value = interfaceData.syncControl || '';

            // Set selected systems in dropdowns
            if (interfaceData.sourceName) {
                const sourceInput = document.getElementById('sourceShortName');
                const sourceSelect = document.getElementById('sourceSystem');
                // Support all possible field name variations (camelCase, snake_case, PascalCase)
                const sourceId = interfaceData.sourceSystemId || 
                                interfaceData.source_system_id || 
                                interfaceData.source_systemID || 
                                interfaceData.Source_systemID || 
                                interfaceData.sourceSystemID || '';
                
                if (sourceInput) {
                    sourceInput.value = interfaceData.sourceName;
                    if (sourceId) {
                        sourceInput.dataset.systemId = String(sourceId);
                        console.log('Set source system:', interfaceData.sourceName, 'ID:', sourceId);
                    } else {
                        console.warn('Source system name found but no ID available:', interfaceData.sourceName);
                    }
                }
                
                // Set hidden select element value
                if (sourceSelect && sourceId) {
                    sourceSelect.value = String(sourceId);
                    console.log('Set sourceSystem select value to:', sourceId);
                }
            }
            
            if (interfaceData.targetName) {
                const targetInput = document.getElementById('targetShortName');
                const targetSelect = document.getElementById('targetSystem');
                // Support all possible field name variations (camelCase, snake_case, PascalCase)
                const targetId = interfaceData.targetSystemId || 
                                interfaceData.target_system_id || 
                                interfaceData.target_systemID || 
                                interfaceData.Target_systemID || 
                                interfaceData.targetSystemID || '';
                
                if (targetInput) {
                    targetInput.value = interfaceData.targetName;
                    if (targetId) {
                        targetInput.dataset.systemId = String(targetId);
                        console.log('Set target system:', interfaceData.targetName, 'ID:', targetId);
                    } else {
                        console.warn('Target system name found but no ID available:', interfaceData.targetName);
                    }
                }
                
                // Set hidden select element value
                if (targetSelect && targetId) {
                    targetSelect.value = String(targetId);
                    console.log('Set targetSystem select value to:', targetId);
                }
            }

            // Populate classification fields
            // Note: lookupData should already be loaded since we await loadLookupData() before calling loadInterfaceData()
            populateClassificationFields(interfaceData);

            // Set segment value if available
            const serverSegmentId = interfaceData.segmentId ?? interfaceData.segment_id ?? interfaceData.Segment_ID;
            originalInterfaceSegmentId = normalizeInterfaceSegmentId(serverSegmentId);
            if (segmentField && serverSegmentId != null) {
                segmentField.setValue(parseInt(serverSegmentId, 10));
                console.log('Segment field set to:', serverSegmentId);
            }

            // Store interface ID for glossary operations
            currentInterfaceId = id;
            console.log('Set currentInterfaceId to:', currentInterfaceId);

            // Load required data in sequence to ensure proper order
            await loadRelationTypes();
            await loadGlossaryItems();
            await loadGlossaryData(id);

        } catch (error) {
            console.error('Error loading interface data:', error);
            alert('Failed to load interface data');
        }
    }

    // Create data row for existing items
    function createDataRow(item) {
        console.log('Creating data row for item:', item);
        const row = document.createElement('tr');
        row.className = 'data-row';
        row.setAttribute('data-id', item.id); // Set data-id on the tr element
        row.innerHTML = `
            <td>
                <select class="form-select-sm" data-field="relationType">
                    <option value="">Select Type</option>
                </select>
            </td>
            <td>
                <select class="form-select-sm" data-field="glossary">
                    <option value="">Select Glossary</option>
                </select>
            </td>
            <td class="definition-cell">
                <span class="definition-text">${escapeHtml(item.glossaryDefinition || '')}</span>
            </td>
            <td class="status-cell">
                <span class="status-text">${escapeHtml(item.relationshipStatus || '')}</span>
            </td>
            <td class="action-cell">
                <div class="action-buttons">
                    <button type="button" class="action-btn add-btn" title="Add Row">
                        <i class="fas fa-plus"></i>
                    </button>
                    <button type="button" class="action-btn delete-btn" title="Delete Row">
                        <i class="fas fa-minus"></i>
                    </button>
                </div>
            </td>
        `;
        
        // Populate selects after row is added
        setTimeout(() => {
            populateRowSelects(row, item);
            // Add event listeners for updating definition and status
            addRowEventListeners(row);
        }, 100);
        
        return row;
    }

    // Create empty row for new entries
    function createEmptyRow() {
        const row = document.createElement('tr');
        row.className = 'empty-row';
        row.innerHTML = `
            <td>
                <select class="form-select-sm" data-field="relationType" data-new="true">
                    <option value="">Select Type</option>
                </select>
            </td>
            <td>
                <select class="form-select-sm" data-field="glossary" data-new="true">
                    <option value="">Select Glossary</option>
                </select>
            </td>
            <td class="definition-cell">
                <span class="definition-text">-</span>
            </td>
            <td class="status-cell">
                <span class="status-text">-</span>
            </td>
            <td class="action-cell">
                <div class="action-buttons">
                    <button type="button" class="action-btn add-btn" title="Add Row">
                        <i class="fas fa-plus"></i>
                    </button>
                    <button type="button" class="action-btn delete-btn" title="Delete Row">
                        <i class="fas fa-minus"></i>
                    </button>
                </div>
            </td>
        `;
        
        // Populate selects after row is added
        setTimeout(() => {
            populateRowSelects(row, null);
            // Add event listeners for updating definition and status
            addRowEventListeners(row);
        }, 100);
        
        return row;
    }

    // Populate select options for a row
    function populateRowSelects(row, item) {
        console.log('Populating row selects for item:', item);
        console.log('Available relation types:', relationTypes);
        console.log('Available glossary items:', window.glossaryItems);
        
        const relationSelect = row.querySelector('[data-field="relationType"]');
        const glossarySelect = row.querySelector('[data-field="glossary"]');
        
        // Clear existing options first
        if (relationSelect) {
            relationSelect.innerHTML = '<option value="">Select Type</option>';
        }
        if (glossarySelect) {
            glossarySelect.innerHTML = '<option value="">Select Glossary</option>';
        }
        
        // Populate relation types
        if (relationSelect && relationTypes && relationTypes.length > 0) {
            console.log('Populating relation types:', relationTypes.length);
            relationTypes.forEach(type => {
                const option = document.createElement('option');
                option.value = type.id;
                option.textContent = type.name;
                // Check if this is the selected relation type
                if (item && item.relationshipType === type.name) {
                    option.selected = true;
                    console.log('Selected relation type:', type.name);
                }
                relationSelect.appendChild(option);
            });
        } else {
            console.warn('No relation types available or relationTypes is empty');
        }
        
        // Populate glossary items
        if (glossarySelect && window.glossaryItems && window.glossaryItems.length > 0) {
            console.log('Populating glossary items:', window.glossaryItems.length);
            window.glossaryItems.forEach(glossary => {
                const option = document.createElement('option');
                option.value = glossary.id;
                option.textContent = glossary.name;
                // Check if this is the selected glossary
                if (item && item.glossaryName === glossary.name) {
                    option.selected = true;
                    console.log('Selected glossary:', glossary.name);
                }
                glossarySelect.appendChild(option);
            });
        } else {
            console.warn('No glossary items available or window.glossaryItems is empty');
        }
    }

    // Add event listeners for row selects
    function addRowEventListeners(row) {
        const glossarySelect = row.querySelector('[data-field="glossary"]');
        const definitionSpan = row.querySelector('.definition-text');
        const statusSpan = row.querySelector('.status-text');
        
        if (glossarySelect) {
            glossarySelect.addEventListener('change', function() {
                const selectedGlossaryId = this.value;
                if (selectedGlossaryId && window.glossaryItems) {
                    const selectedGlossary = window.glossaryItems.find(item => item.id == selectedGlossaryId);
                    if (selectedGlossary && definitionSpan) {
                        definitionSpan.textContent = selectedGlossary.description || '-';
                    }
                } else if (definitionSpan) {
                    definitionSpan.textContent = '-';
                }
            });
        }
        
        // Set initial status for new rows
        if (statusSpan && originalInterfaceData) {
            statusSpan.textContent = originalInterfaceData.statusName || 'To Be Documented';
        }
    }

    // Load relation types for dropdown
    async function loadRelationTypes() {
        try {
            const response = await fetch('/api/glossary-relation-type/list');
            if (response.ok) {
                relationTypes = await response.json();
            }
        } catch (error) {
            console.error('Error loading relation types:', error);
        }
    }

    // Load glossary items for dropdown
    async function loadGlossaryItems() {
        try {
            const response = await fetch('/api/glossary/list');
            if (response.ok) {
                const items = await response.json();
                // Store glossary items separately from table data
                window.glossaryItems = items;
            }
        } catch (error) {
            console.error('Error loading glossary items:', error);
        }
    }

    // Add new row
    function addNewRow() {
        console.log('Adding new row');
        const tbody = document.getElementById('glossaryTableBody');
        if (!tbody) return;
        
        // Add new empty row
        const emptyRow = createEmptyRow();
        tbody.appendChild(emptyRow);
    }

    // Remove empty row
    function removeEmptyRow(button) {
        console.log('Removing empty row');
        const row = button.closest('tr');
        if (row && row.classList.contains('empty-row') && !row.classList.contains('cleared-row')) {
            // Check if this is the first row in the table
            const tbody = row.closest('tbody');
            const allRows = tbody.querySelectorAll('tr');
            const isFirstRow = row === allRows[0];
            
            if (isFirstRow) {
                console.log('Cannot remove first row - preserving table structure');
                return;
            }
            
            row.remove();
        }
    }

    // Delete glossary item (silent deletion during save)
    async function deleteGlossaryItem(id) {
        try {
            console.log('Silently deleting glossary item with ID:', id);
            
            const response = await fetch('/api/interface-x-glossary', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    action: 'delete',
                    id: parseInt(id)
                })
            });
            
            if (response.ok) {
                const result = await response.json();
                console.log('Delete record response:', result);
                if (result.success) {
                    console.log('Glossary item silently deleted from database:', id);
                } else {
                    console.error('Failed to delete glossary item:', result.message);
                    throw new Error(result.message || 'Failed to delete record');
                }
            } else {
                const errorText = await response.text();
                throw new Error(`HTTP ${response.status}: ${errorText}`);
            }
        } catch (error) {
            console.error('Error silently deleting glossary item:', error);
            throw error; // Re-throw to be handled by caller
        }
    }

        // Removed duplicate saveInterface function

    // Update existing glossary record
    async function updateGlossaryRecord(id, relationTypeId, glossaryId) {
        try {
            console.log('Updating glossary record:', {
                id: parseInt(id),
                interfaceId: currentInterfaceId,
                glossaryId: parseInt(glossaryId),
                relationTypeId: parseInt(relationTypeId)
            });

            // Update the existing record directly
            const updateResponse = await fetch('/api/interface-x-glossary', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    action: 'update',
                    id: parseInt(id),
                    interfaceId: currentInterfaceId,
                    glossaryId: parseInt(glossaryId),
                    relationTypeId: parseInt(relationTypeId)
                })
            });

            if (updateResponse.ok) {
                const result = await updateResponse.json();
                console.log('Update record response:', result);
                if (!result.success) {
                    throw new Error(result.message || 'Failed to update record');
                }
            } else {
                const errorText = await updateResponse.text();
                throw new Error(`HTTP ${updateResponse.status}: ${errorText}`);
            }
        } catch (error) {
            console.error('Error updating glossary record:', error);
            throw error;
        }
    }

    // Add new glossary record
    async function addGlossaryRecord(relationTypeId, glossaryId) {
        try {
            console.log('Adding new glossary record:', {
                interfaceId: currentInterfaceId,
                glossaryId: parseInt(glossaryId),
                relationTypeId: parseInt(relationTypeId)
            });

            // Validate required data
            if (!currentInterfaceId) {
                throw new Error('Interface ID is required');
            }
            if (!relationTypeId || !glossaryId) {
                throw new Error('Relation type and glossary are required');
            }

            const requestData = {
                action: 'add',
                interfaceId: currentInterfaceId,
                glossaryId: parseInt(glossaryId),
                relationTypeId: parseInt(relationTypeId)
            };

            console.log('Sending request data:', requestData);
            console.log('Request URL:', '/api/interface-x-glossary');

            const response = await fetch('/api/interface-x-glossary', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(requestData)
            }).catch(error => {
                console.error('Fetch error:', error);
                console.error('Error type:', error.constructor.name);
                console.error('Error message:', error.message);
                throw error;
            });

            console.log('Add record response status:', response.status);
            console.log('Add record response ok:', response.ok);

            if (response.ok) {
                const result = await response.json();
                console.log('Add record response:', result);
                if (!result.success) {
                    console.error('Server returned error:', result);
                    throw new Error(result.message || 'Failed to add new record');
                }
            } else {
                const errorText = await response.text();
                console.error('Add record error response:', errorText);
                console.error('Response status:', response.status);
                console.error('Response status text:', response.statusText);
                console.error('Response URL:', response.url);
                console.error('Response headers:', response.headers);
                throw new Error(`HTTP ${response.status}: ${errorText}`);
            }
        } catch (error) {
            console.error('Error adding glossary record:', error);
            console.error('Error details:', {
                message: error.message,
                stack: error.stack,
                interfaceId: currentInterfaceId,
                relationTypeId: relationTypeId,
                glossaryId: glossaryId
            });

            // Check if it's a network error
            if (error.name === 'TypeError' && error.message.includes('fetch')) {
                console.error('Network error - server may not be running');
                throw new Error('Cannot connect to server. Please make sure the server is running.');
            }

            // Check if it's a CORS error
            if (error.message.includes('CORS')) {
                console.error('CORS error - check server CORS configuration');
                throw new Error('CORS error. Please check server configuration.');
            }

            throw error;
        }
    }

    // Load systems data for dropdowns
        async function loadSystemsData() {
            try {
                const systems = await window.BUDG_API_SERVICE.getSystemsList();
                
                // Handle different data structures
                let systemsArray = systems;
                if (systems && systems.data && Array.isArray(systems.data)) {
                    systemsArray = systems.data;
                } else if (Array.isArray(systems)) {
                    systemsArray = systems;
                } else {
                    console.warn('Systems data is not in expected format:', systems);
                    systemsArray = [];
                }
                
                populateSystemSelects(systemsArray);
                initDualSystemDropdown('source');
                initDualSystemDropdown('target');
            } catch (error) {
                console.error('Error loading systems data:', error);
            }
        }

        async function refreshSystemInterfaceLinkedSystemsForSegment() {
            const sourceInput = document.getElementById('sourceShortName');
            const targetInput = document.getElementById('targetShortName');
            const sourceSelect = document.getElementById('sourceSystem');
            const targetSelect = document.getElementById('targetSystem');

            const previousSourceId = sourceInput?.dataset?.systemId || sourceSelect?.value || '';
            const previousTargetId = targetInput?.dataset?.systemId || targetSelect?.value || '';

            await loadSystemsData();

            let clearedAny = false;
            const sourceStillAllowed = previousSourceId
                ? Array.from(sourceSelect?.options || []).some(opt => String(opt.value) === String(previousSourceId))
                : true;
            const targetStillAllowed = previousTargetId
                ? Array.from(targetSelect?.options || []).some(opt => String(opt.value) === String(previousTargetId))
                : true;

            if (!sourceStillAllowed) {
                if (sourceInput) {
                    sourceInput.value = '';
                    delete sourceInput.dataset.systemId;
                }
                if (sourceSelect) {
                    sourceSelect.value = '';
                }
                clearedAny = true;
            }

            if (!targetStillAllowed) {
                if (targetInput) {
                    targetInput.value = '';
                    delete targetInput.dataset.systemId;
                }
                if (targetSelect) {
                    targetSelect.value = '';
                }
                clearedAny = true;
            }

            if (clearedAny) {
                alert('Related system was cleared because it is not valid for the selected segment.');
            }
        }

        // Populate hidden system selects
        function populateSystemSelects(systems) {
            const sourceSelect = document.getElementById('sourceSystem');
            const targetSelect = document.getElementById('targetSystem');
            const sourceInput = document.getElementById('sourceShortName');
            const targetInput = document.getElementById('targetShortName');
            
            console.log('Populating system selects with data:', systems);
            console.log('Source select element:', sourceSelect);
            console.log('Target select element:', targetSelect);
            
            if (sourceSelect && systems) {
                sourceSelect.innerHTML = '<option value="">Please select</option>';
                systems.forEach(system => {
                    console.log('Processing source system:', system);
                    const option = document.createElement('option');
                    option.value = system.id;
                    option.textContent = system.name || system.shortName;
                    option.dataset.full = JSON.stringify(system);
                    sourceSelect.appendChild(option);
                });
                
                // Set the selected value if input has a value
                if (sourceInput && sourceInput.value) {
                    for (let option of sourceSelect.options) {
                        if (option.textContent === sourceInput.value) {
                            sourceSelect.value = option.value;
                            sourceInput.dataset.systemId = option.value;
                            console.log('Matched source system:', sourceInput.value, 'ID:', option.value);
                            break;
                        }
                    }
                }
            }
            
            if (targetSelect && systems) {
                targetSelect.innerHTML = '<option value="">Please select</option>';
                systems.forEach(system => {
                    const option = document.createElement('option');
                    option.value = system.id;
                    option.textContent = system.name || system.shortName;
                    option.dataset.full = JSON.stringify(system);
                    targetSelect.appendChild(option);
                });
                
                // Set the selected value if input has a value
                if (targetInput && targetInput.value) {
                    for (let option of targetSelect.options) {
                        if (option.textContent === targetInput.value) {
                            targetSelect.value = option.value;
                            targetInput.dataset.systemId = option.value;
                            console.log('Matched target system:', targetInput.value, 'ID:', option.value);
                            break;
                        }
                    }
                    updateDerivedSegmentDisplayFromTarget();
                }
            }
        }

        // Initialize searchable dropdown for systems (from creation page)
        function initDualSystemDropdown(kind) {
            const hiddenSelect = document.getElementById(kind + 'System');
            const input = document.getElementById(kind + 'ShortName');
            if (!hiddenSelect || !input) return;
            
            // Prevent double init
            if (input.dataset.init === '1') return; 
            input.dataset.init = '1';

            const wrapper = input.parentElement;
            if (!wrapper) return;
            
            const panel = document.createElement('div');
            panel.className = 'searchable-select-panel';
            panel.style.position = 'absolute';
            panel.style.left = '0';
            panel.style.right = '0';
            panel.style.top = 'calc(100% + 6px)';
            panel.style.zIndex = '1000';
            panel.style.background = 'var(--bg-elevated, #fff)';
            panel.style.border = '1px solid var(--border-color, #d0d5dd)';
            panel.style.borderRadius = '8px';
            panel.style.boxShadow = '0 10px 20px rgba(0,0,0,0.08)';
            panel.style.padding = '8px';
            panel.style.display = 'none';

            const searchInput = document.createElement('input');
            searchInput.type = 'text';
            searchInput.placeholder = 'Search systems...';
            searchInput.className = 'form-input';
            searchInput.style.margin = '4px';

            const list = document.createElement('div');
            list.style.maxHeight = '220px';
            list.style.overflowY = 'auto';
            list.style.marginTop = '6px';

            panel.appendChild(searchInput);
            panel.appendChild(list);
            wrapper.style.position = 'relative';
            wrapper.appendChild(panel);

            function openPanel() {
                panel.style.display = 'block';
                searchInput.value = '';
                renderList('');
                setTimeout(() => searchInput.focus(), 0);
            }
            
            function closePanel() {
                panel.style.display = 'none';
            }
            
            function renderList(q) {
                const query = (q || '').toLowerCase();
                list.innerHTML = '';
                Array.from(hiddenSelect.options).forEach((opt, idx) => {
                    const item = opt?.dataset?.full ? JSON.parse(opt.dataset.full) : null;
                    const name = item?.name || opt.textContent || '';
                    const description = item?.description || '';
                    const searchable = (name + ' ' + description).toLowerCase();
                    if (query && !searchable.includes(query)) return;
                    
                    console.log(`Rendering ${kind} option:`, { name, id: item?.id, optValue: opt.value });
                    
                    const row = document.createElement('div');
                    row.style.display = 'grid';
                    row.style.gridTemplateColumns = '1fr 2fr';
                    row.style.gap = '8px';
                    row.style.padding = '8px 10px';
                    row.style.cursor = 'pointer';
                    row.style.borderRadius = '6px';
                    row.addEventListener('mouseenter', () => { row.style.background = 'var(--gray-50, #f2f4f7)'; });
                    row.addEventListener('mouseleave', () => { row.style.background = 'transparent'; });
                    row.addEventListener('click', () => {
                        hiddenSelect.selectedIndex = idx;
                        input.value = name;
                        input.dataset.systemId = String(item.id);
                        console.log(`Selected ${kind} system:`, { name, id: item.id });
                        console.log(`Hidden select value after selection:`, hiddenSelect.value);
                        console.log(`Input dataset after selection:`, input.dataset.systemId);
                        markAsChanged(); // Mark form as changed when system is selected
                        if (kind === 'target') {
                            updateDerivedSegmentDisplayFromTarget();
                        }
                        closePanel();
                    });
                    
                    const nameDiv = document.createElement('div');
                    nameDiv.textContent = name;
                    nameDiv.style.fontWeight = '500';
                    const descDiv = document.createElement('div');
                    descDiv.textContent = description;
                    descDiv.style.opacity = '0.8';
                    row.appendChild(nameDiv);
                    row.appendChild(descDiv);
                    list.appendChild(row);
                });
                
                if (!list.children.length) {
                    const empty = document.createElement('div');
                    empty.style.padding = '8px 10px';
                    empty.style.opacity = '0.8';
                    empty.textContent = 'No systems found';
                    list.appendChild(empty);
                }
            }

            input.addEventListener('click', (e) => { e.stopPropagation(); openPanel(); });
            input.addEventListener('keydown', (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); openPanel(); }});
            searchInput.addEventListener('input', (e) => renderList(e.target.value));
            document.addEventListener('click', (e) => { if (!wrapper.contains(e.target)) closePanel(); });
            document.addEventListener('keydown', (e) => { if (e.key === 'Escape') closePanel(); });
        }

        // Load lookup data for converting names to IDs
        async function loadLookupData() {
            try {
                console.log('Loading lookup data...');

                // Load all lookup tables in parallel
                const [
                    statuses,
                    lifecycles,
                    viewings,
                    automations,
                    frequencies,
                    transferMethods,
                    transferFormats,
                    classifications
                ] = await Promise.all([
                    window.BUDG_API_SERVICE.getStatusList(),
                    // Use getInterfaceLifecycleList() instead of getLifecycleList() for interface-specific lifecycle
                    window.BUDG_API_SERVICE.getInterfaceLifecycleList ? 
                        window.BUDG_API_SERVICE.getInterfaceLifecycleList() : 
                        window.BUDG_API_SERVICE.getLifecycleList(),
                    window.BUDG_API_SERVICE.getViewingList(),
                    window.BUDG_API_SERVICE.getInterfaceAutomation(),
                    window.BUDG_API_SERVICE.getInterfaceFrequencies(),
                    window.BUDG_API_SERVICE.getInterfaceTransferMethods(),
                    window.BUDG_API_SERVICE.getInterfaceTransferFormats(),
                    window.BUDG_API_SERVICE.getInterfaceClassifications()
                ]);

                // Store the data
                lookupData.statuses = statuses || [];
                lookupData.lifecycles = lifecycles || [];
                lookupData.viewings = viewings || [];
                lookupData.automations = automations || [];
                lookupData.frequencies = frequencies || [];
                lookupData.transferMethods = transferMethods || [];
                lookupData.transferFormats = transferFormats || [];
                lookupData.classifications = classifications || [];

                console.log('Lookup data loaded:', lookupData);
                console.log('Interface classifications loaded:', classifications);
                console.log('Classifications count:', classifications ? classifications.length : 0);

                // Populate classification dropdowns
                populateClassificationDropdowns();

                // Debug: Log structure of each lookup array
                console.log('Lookup data structure:');
                Object.keys(lookupData).forEach(key => {
                    const data = lookupData[key];
                    console.log(`${key}:`, {
                        length: data.length,
                        sample: data[0],
                        allKeys: data.length > 0 ? Object.keys(data[0]) : 'No data'
                    });
                });
            } catch (error) {
                console.error('Error loading lookup data:', error);
            }
        }

        // Convert name to ID using lookup data
        function convertNameToId(name, lookupArray, nameField = 'name', idField = 'id') {
            if (!name || !lookupArray || !Array.isArray(lookupArray)) {
                console.log(`convertNameToId: Missing data - name: ${name}, lookupArray: ${lookupArray?.length || 'null'}`);
                return null;
            }

            console.log(`convertNameToId: Looking for "${name}" in array with ${lookupArray.length} items`);
            console.log(`convertNameToId: Using fields - nameField: ${nameField}, idField: ${idField}`);
            console.log(`convertNameToId: Sample items:`, lookupArray.slice(0, 3));

            const item = lookupArray.find(item => {
                const itemName = item[nameField];
                const matches = itemName && itemName.toString().toLowerCase() === name.toString().toLowerCase();
                if (matches) {
                    console.log(`convertNameToId: Found match:`, item);
                }
                return matches;
            });

            const result = item ? item[idField] : null;
            console.log(`convertNameToId: Result for "${name}": ${result}`);
            return result;
        }

        // Populate classification dropdowns
        function populateClassificationDropdowns() {
            console.log('Populating classification dropdowns...');
            
            // Populate BUDG Status dropdown
            populateClassificationDropdown('BUDGStatus', lookupData.statuses, 'name', 'id');
            
            // Populate BUDG Viewing dropdown
            populateClassificationDropdown('BUDGViewing', lookupData.viewings, 'name', 'id');
            
            // Populate Lifecycle dropdown
            populateClassificationDropdown('lifecycle', lookupData.lifecycles, 'name', 'id');
            
            // Populate Automation dropdown
            populateClassificationDropdown('automation', lookupData.automations, 'name', 'id');
            
            // Populate Frequency dropdown
            populateClassificationDropdown('frequency', lookupData.frequencies, 'name', 'id');
            
            // Populate Transfer Method dropdown
            populateClassificationDropdown('transferMethod', lookupData.transferMethods, 'name', 'id');
            
            // Populate Transfer Format dropdown
            populateClassificationDropdown('transferFormat', lookupData.transferFormats, 'name', 'id');
            
            // Populate Interface Classification dropdown
            populateClassificationDropdown('interfaceClassification', lookupData.classifications, 'name', 'id');
        }

        // Generic function to populate a dropdown
        function populateClassificationDropdown(selectId, data, nameField = 'name', idField = 'id') {
            const select = document.getElementById(selectId);
            console.log(`populateClassificationDropdown: Attempting to populate ${selectId}`);
            console.log(`populateClassificationDropdown: Select element found:`, !!select);
            console.log(`populateClassificationDropdown: Data provided:`, data);
            console.log(`populateClassificationDropdown: Data is array:`, Array.isArray(data));
            console.log(`populateClassificationDropdown: Data length:`, data ? data.length : 0);
            
            if (!select || !data || !Array.isArray(data)) {
                console.log(`populateClassificationDropdown: Missing data for ${selectId}`);
                return;
            }

            // Clear existing options except the first one
            while (select.children.length > 1) {
                select.removeChild(select.lastChild);
            }

            // Add options from data
            data.forEach((item, index) => {
                console.log(`populateClassificationDropdown: Adding option ${index}:`, item);
                const option = document.createElement('option');
                option.value = item[idField];
                option.textContent = item[nameField];
                select.appendChild(option);
            });

            console.log(`populateClassificationDropdown: Populated ${selectId} with ${data.length} options`);
            console.log(`populateClassificationDropdown: Final select options count:`, select.children.length);
        }

        // Populate classification fields with interface data
        function populateClassificationFields(interfaceData) {
            console.log('=== POPULATING CLASSIFICATION FIELDS ===');
            console.log('Interface data lifecycle fields:', {
                lifecycleName: interfaceData.lifecycleName,
                lifecycle_id: interfaceData.lifecycle_id,
                lifecycleId: interfaceData.lifecycleId,
                Lifecycle_id: interfaceData.Lifecycle_id
            });
            console.log('Available lookup data:', {
                statuses: lookupData.statuses.length,
                viewings: lookupData.viewings.length,
                lifecycles: lookupData.lifecycles.length,
                automations: lookupData.automations.length
            });
            console.log('Lifecycles data sample:', lookupData.lifecycles.slice(0, 3));
            
            // Set values for classification fields
            if (interfaceData.statusName) {
                setDropdownValue('BUDGStatus', interfaceData.statusName, lookupData.statuses);
            }
            if (interfaceData.viewingName) {
                setDropdownValue('BUDGViewing', interfaceData.viewingName, lookupData.viewings);
            }
            // Set lifecycle - try by ID first (more reliable), then by name
            const lifecycleId = interfaceData.lifecycle_id || interfaceData.lifecycleId || interfaceData.Lifecycle_id;
            const lifecycleSelect = document.getElementById('lifecycle');
            
            if (lifecycleId && lifecycleSelect) {
                // Try setting by ID first (most reliable)
                lifecycleSelect.value = String(lifecycleId);
                console.log(`✅ Set lifecycle by ID to ${lifecycleId}`);
                console.log(`   Select value after setting: ${lifecycleSelect.value}`);
                // Verify the value was set correctly
                if (lifecycleSelect.value !== String(lifecycleId)) {
                    console.warn(`⚠️ Lifecycle ID ${lifecycleId} not found in dropdown options, trying by name...`);
                    // If ID didn't work, try by name
                    if (interfaceData.lifecycleName) {
                        setDropdownValue('lifecycle', interfaceData.lifecycleName, lookupData.lifecycles);
                    }
                }
            } else if (interfaceData.lifecycleName) {
                // Fallback: set by name if ID is not available
                console.log('Setting lifecycle by name:', interfaceData.lifecycleName);
                setDropdownValue('lifecycle', interfaceData.lifecycleName, lookupData.lifecycles);
            } else {
                console.warn('No lifecycle data available in interfaceData');
            }
            if (interfaceData.automationName) {
                setDropdownValue('automation', interfaceData.automationName, lookupData.automations);
            }
            if (interfaceData.frequencyName) {
                setDropdownValue('frequency', interfaceData.frequencyName, lookupData.frequencies);
            }
            if (interfaceData.transferMethodName) {
                setDropdownValue('transferMethod', interfaceData.transferMethodName, lookupData.transferMethods);
            }
            if (interfaceData.transferFormatName) {
                setDropdownValue('transferFormat', interfaceData.transferFormatName, lookupData.transferFormats);
            }
            if (interfaceData.classificationName) {
                setDropdownValue('interfaceClassification', interfaceData.classificationName, lookupData.classifications);
            }
            if (interfaceData.assetId) {
                document.getElementById('assetId').value = interfaceData.assetId;
            }
        }

        // Set dropdown value by name (with fuzzy matching for typos)
        function setDropdownValue(selectId, valueName, data) {
            const select = document.getElementById(selectId);
            if (!select) {
                console.warn(`setDropdownValue: Select element not found for ${selectId}`);
                return;
            }
            if (!data || !Array.isArray(data)) {
                console.warn(`setDropdownValue: Invalid data for ${selectId}`, data);
                return;
            }

            console.log(`setDropdownValue: Looking for "${valueName}" in ${selectId} with ${data.length} items`);
            
            // First try exact match (case-insensitive)
            let item = data.find(item => 
                item.name && item.name.toString().toLowerCase() === valueName.toString().toLowerCase()
            );
            
            // If not found, try fuzzy matching (normalize by removing extra spaces and common typos)
            if (!item) {
                const normalizedValueName = valueName.toString().toLowerCase()
                    .replace(/\s+/g, ' ')  // Normalize spaces
                    .trim();
                
                item = data.find(item => {
                    if (!item.name) return false;
                    const normalizedItemName = item.name.toString().toLowerCase()
                        .replace(/\s+/g, ' ')  // Normalize spaces
                        .trim();
                    // Try exact match after normalization
                    if (normalizedItemName === normalizedValueName) return true;
                    // Try contains match (for partial matches)
                    if (normalizedItemName.includes(normalizedValueName) || normalizedValueName.includes(normalizedItemName)) {
                        // Check if it's a close match (length difference <= 2 characters)
                        const lengthDiff = Math.abs(normalizedItemName.length - normalizedValueName.length);
                        if (lengthDiff <= 2) return true;
                    }
                    return false;
                });
            }
            
            if (item) {
                select.value = String(item.id);
                console.log(`✅ setDropdownValue: Set ${selectId} to "${item.name}" (ID: ${item.id})`);
                console.log(`   Matched "${valueName}" to "${item.name}"`);
                console.log(`   Select value after setting: ${select.value}`);
            } else {
                console.warn(`❌ setDropdownValue: Could not find "${valueName}" in data for ${selectId}`);
                console.warn(`   Available names:`, data.map(d => d.name));
            }
        }

    // Old dropdown functions removed - now using initDualSystemDropdown

    // Old searchable dropdown function removed - now using initDualSystemDropdown

    function validateForm() {
        const requiredFields = [
            'interfaceName',
            'sourceShortName',
            'targetShortName',
            'interfaceDescription',
            'BUDGStatus',
            'BUDGViewing',
            'lifecycle',
            'automation'
        ];

        let isValid = true;
        requiredFields.forEach(fieldId => {
            const field = document.getElementById(fieldId);
            if (!field.value.trim()) {
                field.style.borderColor = '#dc3545';
                isValid = false;
            } else {
                field.style.borderColor = '';
            }
        });

        return isValid;
    }


    document.addEventListener('DOMContentLoaded', async function() {
        const id = parseId();
        if (!id) {
            console.error('No interface ID found');
            return;
        }

        // Initialize lock
        const lockAcquired = await window.LockInitHelper.initializeLock('system-interface', id, 'system-interface');
        if (!lockAcquired) {
            return; // Lock initialization failed, user was redirected
        }

        const backBtn = document.getElementById('backBtn');
        const saveBtn = document.getElementById('saveBtn');
        const saveCloseBtn = document.getElementById('saveCloseBtn');
        const closeBtn = document.getElementById('closeBtn');

        // Back button
        if (backBtn) {
            backBtn.addEventListener('click', () => {
                window.history.length > 1 ? window.history.back() : window.location.assign('/');
            });
        }

        // Save button - removed duplicate event listener

        // Save & Close button - removed duplicate event listener

        // Close button
        if (closeBtn) {
            closeBtn.addEventListener('click', async () => {
                await window.LockInitHelper.releaseLock();
                const id = currentInterfaceId || parseId();
                if (id) {
                    window.location.href = `/view/system-interface/${id}`;
                } else {
                    window.history.length > 1 ? window.history.back() : window.location.assign('/');
                }
            });
        }

        // Show editor button – advanced rich text editor
        const editorButton = document.querySelector('.editor-button');
        if (editorButton) {
            editorButton.addEventListener('click', function(e) {
                e.preventDefault();
                e.stopPropagation();
                toggleAdvancedRichTextEditor('interfaceDescription', editorButton);
            });
        }

        // Tab functionality
        const tabs = document.querySelectorAll('.tab-container .tab');
        const tabContents = {
            'summary': document.getElementById('summaryTab'),
            'stakeholders': document.getElementById('stakeholdersTab'),
            'impact': document.getElementById('impactTab')
        };

        tabs.forEach(function(btn) {
            btn.addEventListener('click', function() {
                // Remove active class from all tabs
                tabs.forEach(function(b) { b.classList.remove('active'); });
                this.classList.add('active');

                // Hide all tab contents
                Object.values(tabContents).forEach(function(content) {
                    if (content) content.classList.remove('active');
                });

                // Show selected tab content
                const tabName = this.getAttribute('data-tab');
                
                // Hide all containers
                const form = document.getElementById('interfaceEditForm');
                const stakeholdersContainer = document.getElementById('interfaceStakeholdersContainer');
                const impactTab = document.getElementById('impactTab');
                
                if (form) form.style.display = 'block';
                if (stakeholdersContainer) stakeholdersContainer.style.display = 'none';
                if (impactTab) impactTab.style.display = 'none';
                
                if (tabName === 'stakeholders') {
                    // Show stakeholders container and hide form
                    if (form) form.style.display = 'none';
                    if (stakeholdersContainer) stakeholdersContainer.style.display = 'block';
                } else if (tabName === 'impact') {
                    // Show impact tab and hide form
                    if (form) form.style.display = 'none';
                    if (impactTab) impactTab.style.display = 'block';
                }
                
                if (tabContents[tabName]) {
                    tabContents[tabName].classList.add('active');
                }
                
                // Load content for specific tab
                loadTabContentForEdit(tabName);
            });
        });

        // Searchable dropdowns are now initialized in loadSystemsData()

        // Make functions globally available
        window.addNewRow = addNewRow;
        window.removeEmptyRow = removeEmptyRow;
        window.deleteGlossaryItem = deleteGlossaryItem;

        // Event delegation for action buttons is now handled in initializeDataSummaryTable()

            // Load systems data
            loadSystemsData();

            // Load lookup data for name-to-ID conversion FIRST (must complete before loading interface data)
            await loadLookupData();

            // Initialize segment field before loading data so value can be set
            let segmentField = null;
            if (window.SegmentField) {
                try {
                    segmentField = await SegmentField.init('segmentFieldContainer', {
                        label: 'Segment',
                        required: true,
                        sectionTitle: 'SEGMENTATION',
                        objectType: 'System Interface',
                        fieldId: 'interfaceSegment',
                        errorId: 'interfaceSegmentError',
                        onChange: async () => {
                            await refreshSystemInterfaceLinkedSystemsForSegment();
                        }
                    });
                    // Store reference for save function
                    const container = document.getElementById('segmentFieldContainer');
                    if (container) {
                        container._segmentFieldInstance = segmentField;
                    }
                    console.log('Segment field initialized');
                } catch (error) {
                    console.error('Error initializing segment field:', error);
                }
            }

            // Load interface data if ID is provided (after lookup data is loaded)
            if (id) {
                currentInterfaceId = id;
                await loadInterfaceData(id, segmentField);
                
                // Initialize custom fields
                if (window.CustomFields) {
                    try {
                        window.customFieldsContext = await window.CustomFields.initForm({
                            facetId: 'System Interface',
                            containerId: 'customFieldsContainer',
                            mode: 'edit',
                            objectId: id
                        });
                        console.log('Custom fields initialized:', window.customFieldsContext);
                    } catch (error) {
                        console.error('Error initializing custom fields:', error);
                    }
                }
                
                // Set active tab based on URL parameter
                setActiveTab();
                
                
            // Load required data for glossary table
            await loadRelationTypes();
            await loadGlossaryItems();
            await loadGlossaryData(id);
            
            // Re-render table after data is loaded
            if (document.getElementById('glossaryTableBody')) {
                renderGlossaryTable();
            }
                
                            }

        // Form validation on input
        const formInputs = document.querySelectorAll('.form-input, .form-select, .form-textarea');
        formInputs.forEach(input => {
            input.addEventListener('blur', function() {
                if (this.hasAttribute('required') && !this.value.trim()) {
                    this.style.borderColor = '#dc3545';
                } else {
                    this.style.borderColor = '';
                }
            });
        });

    });

    // Set active tab based on URL parameter
    function setActiveTab() {
        const activeTabName = getActiveTab();
        const activeSubTab = getActiveSubTabFromURL();
        const targetTab = document.querySelector(`[data-tab="${activeTabName}"]`);

        if (targetTab) {
            // Remove active class from all tabs
            document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
            // Add active class to target tab
            targetTab.classList.add('active');
            
            // Update currentTab
            currentTab = activeTabName;

            // Load content for the active tab
            loadTabContentForEdit(activeTabName);
            
            // Note: Subtabs are handled within their respective load functions:
            // - For impact tab: setupImpactSubTabs in interface-impact-edit.js handles it
            // - For other tabs with subtabs: they handle it in their load functions
        }
    }

    // Load content for specific tab in edit mode
    function loadTabContentForEdit(tabName) {
        const id = parseId();
        if (!id) return;
        
        // Hide all containers
        const form = document.getElementById('interfaceEditForm');
        const stakeholdersContainer = document.getElementById('interfaceStakeholdersContainer');
        const impactTab = document.getElementById('impactTab');
        
        if (form) form.style.display = 'block';
        if (stakeholdersContainer) stakeholdersContainer.style.display = 'none';
        if (impactTab) impactTab.style.display = 'none';
        
        switch(tabName) {
            case 'stakeholders':
                // Show stakeholders container and hide form
                if (form) form.style.display = 'none';
                if (stakeholdersContainer) stakeholdersContainer.style.display = 'block';
                
                // Load stakeholders in edit mode
                if (window.InterfaceStakeholderEdit) {
                    window.InterfaceStakeholderEdit.init(id);
                }
                break;
            case 'impact':
                // Show impact tab and hide form
                if (form) form.style.display = 'none';
                if (impactTab) impactTab.style.display = 'block';
                
                // Initialize Impact edit if switching to impact tab
                if (window.initImpactEdit) {
                    window.initImpactEdit(id);
                    // setupImpactSubTabs will handle subtab activation from URL
                }
                break;
            case 'summary':
                // Main interface details (Summary) - already loaded
                break;
            default:
                // Handle other tabs if needed
                break;
        }
    }

    // Tab navigation functionality
    function initializeTabNavigation() {
        const tabs = document.querySelectorAll('.tab');
        
        tabs.forEach(tab => {
            tab.addEventListener('click', function() {
                const tabName = this.getAttribute('data-tab');
                switchTab(tabName);
            });
        });
        
        // Handle browser back/forward buttons
        window.addEventListener('popstate', function(event) {
            // When user navigates back/forward, update tab based on URL
            setActiveTab();
        });
    }

    function switchTab(tabName) {
        // Update active tab
        document.querySelectorAll('.tab').forEach(tab => {
            tab.classList.remove('active');
        });
        document.querySelector(`[data-tab="${tabName}"]`).classList.add('active');
        
        currentTab = tabName;
        
        // Update URL when tab changes
        updateURLForTab(tabName);
        
        // Load content for the active tab
        loadTabContentForEdit(tabName);
        
        // Show/hide content based on tab
        const formContent = document.querySelector('.interface-edit-form');
        if (formContent) {
            // For now, all tabs show the same form content
            // In the future, you can add different content for each tab
            console.log('Switched to tab:', tabName);
        }
    }

    // Data summary table management - V1 Style
    function initializeDataSummaryTable() {
        console.log('Initializing data summary table...');
        const tableBody = document.getElementById('glossaryTableBody');
        if (!tableBody) {
            console.error('Table body not found!');
            return;
        }
        console.log('Table body found, adding event listeners...');

        // Add event listeners for action buttons
        tableBody.addEventListener('click', async function(e) {
            console.log('Table click event triggered, target:', e.target);
            if (e.target.closest('.add-btn')) {
                e.preventDefault();
                console.log('Add button clicked');
                addNewRow();
            } else if (e.target.closest('.delete-btn')) {
                e.preventDefault();
                console.log('Delete button clicked');
                const row = e.target.closest('tr');
                if (row) {
                    // Check if the row itself has data-id attribute
                    const id = row.getAttribute('data-id');
                    console.log('Delete button clicked on row:', row);
                    console.log('Row classes:', row.className);
                    console.log('Row data-id attribute:', id);
                    console.log('Row data-id type:', typeof id);
                    
                    if (id) {
                        // Existing row with data - handle deletion based on position
                        const numericId = parseInt(id);
                        console.log('Parsed ID:', numericId);
                        console.log('Is valid number:', !isNaN(numericId));
                        
                        // Check if this is the first row BEFORE removing from DOM
                        const tbody = row.closest('tbody');
                        const allRows = tbody.querySelectorAll('tr');
                        const isFirstRow = row === allRows[0];
                        
                        console.log('Row position check:', {
                            isFirstRow: isFirstRow,
                            totalRows: allRows.length,
                            currentRowIndex: Array.from(allRows).indexOf(row)
                        });
                        
                        console.log('All rows in table:', Array.from(allRows).map((r, i) => ({
                            index: i,
                            classes: r.className,
                            dataId: r.getAttribute('data-id'),
                            isFirst: r === allRows[0]
                        })));
                        
                        if (isFirstRow) {
                            // For first row, delete from database but preserve structure by creating new empty row
                            console.log('Deleting first row from database but preserving structure');
                            
                            // Store ID for database deletion on save
                            if (!window.rowsToDelete) {
                                window.rowsToDelete = [];
                                console.log('Initialized window.rowsToDelete array');
                            }
                            window.rowsToDelete.push(numericId);
                            console.log(`Added first row ID ${numericId} to rowsToDelete array. Current array:`, window.rowsToDelete);
                            
                            // Clear the row data and convert to empty row (preserve structure)
                            clearRowData(row);
                            console.log(`First row ${numericId} will be deleted from database on save, structure preserved`);
                        } else {
                            // For other rows, remove the entire row and mark for database deletion
                            console.log('Removing entire row (not first row)');
                            
                            // Store ID for database deletion on save
                            if (!window.rowsToDelete) {
                                window.rowsToDelete = [];
                                console.log('Initialized window.rowsToDelete array');
                            }
                            window.rowsToDelete.push(numericId);
                            console.log(`Added row ID ${numericId} to rowsToDelete array. Current array:`, window.rowsToDelete);
                            
                            row.remove();
                            console.log(`Row ${numericId} removed from frontend, will be deleted from database on save`);
                        }
                    } else {
                        // Empty row - remove immediately
                        console.log('No data-id found, treating as empty row');
                        removeEmptyRow(e.target);
                    }
                }
            }
        });
        console.log('Data summary table event listeners added successfully');
    }

    // Clear row data (for first row) - preserve structure, clear content only
    async function clearRowData(row) {
        // Remove data-id attribute and convert to empty row
        row.removeAttribute('data-id');
        row.classList.remove('data-row');
        row.classList.add('empty-row');
        // Remove cleared-row class to allow normal empty row behavior
        row.classList.remove('cleared-row');
        
        // Clear all form fields but preserve structure
        const relationSelect = row.querySelector('[data-field="relationType"]');
        const glossarySelect = row.querySelector('[data-field="glossary"]');
        const definitionSpan = row.querySelector('.definition-text');
        const statusSpan = row.querySelector('.status-text');
        
        if (relationSelect) {
            relationSelect.value = '';
            relationSelect.removeAttribute('data-id');
            relationSelect.setAttribute('data-new', 'true');
        }
        if (glossarySelect) {
            glossarySelect.value = '';
            glossarySelect.removeAttribute('data-id');
            glossarySelect.setAttribute('data-new', 'true');
        }
        if (definitionSpan) definitionSpan.textContent = '-';
        if (statusSpan) statusSpan.textContent = '-';
        
        // Reset visual styling
        row.style.backgroundColor = '';
        row.style.borderLeft = '';
        row.style.opacity = '';
        
        // Remove any existing indicators
        const existingIndicators = row.querySelectorAll('.clear-indicator, .delete-indicator');
        existingIndicators.forEach(indicator => indicator.remove());
        
        // Ensure action buttons are present (in case they were removed)
        const actionCell = row.querySelector('.action-cell');
        if (actionCell && !actionCell.querySelector('.action-buttons')) {
            actionCell.innerHTML = `
                <div class="action-buttons">
                    <button type="button" class="action-btn add-btn" title="Add Row">
                        <i class="fas fa-plus"></i>
                    </button>
                    <button type="button" class="action-btn delete-btn" title="Delete Row">
                        <i class="fas fa-minus"></i>
                    </button>
                </div>
            `;
        }
        
        console.log('First row data cleared and converted to empty row - structure preserved');
    }

    // Load glossary data for the interface
    async function loadGlossaryData(interfaceId) {
        try {
            console.log('Loading glossary data for interface:', interfaceId);
            if (window.BUDG_API_SERVICE) {
                const data = await window.BUDG_API_SERVICE.getInterfaceXGlossary(interfaceId);
                console.log('Glossary data received:', data);
                glossaryData = data || [];
                renderGlossaryTable();
            } else {
                console.error('BUDG_API_SERVICE not available');
                glossaryData = [];
                renderGlossaryTable();
            }
        } catch (error) {
            console.error('Error loading glossary data:', error);
            glossaryData = [];
            renderGlossaryTable();
        }
    }
    
    // Render glossary table
    function renderGlossaryTable() {
        const tbody = document.getElementById('glossaryTableBody');
        if (!tbody) return;
        
        console.log('Rendering glossary table with data:', glossaryData);
        tbody.innerHTML = '';
        
        // Always ensure there's at least one row (empty row for new entries)
        // This guarantees the first row structure is always preserved
        let hasDataRows = false;
        
        // Add existing data rows
        if (glossaryData && glossaryData.length > 0) {
            glossaryData.forEach(item => {
                const row = createDataRow(item);
                tbody.appendChild(row);
                hasDataRows = true;
            });
        }
        
        // Always add at least one empty row for new entries
        // This ensures the table structure is always maintained
        const emptyRow = createEmptyRow();
        tbody.appendChild(emptyRow);
        
        console.log('Table rendered with data rows:', hasDataRows, 'and empty row added');
    }
    
    // Create data row for existing items
    function createDataRow(item) {
        console.log('Creating data row for item:', item);
        const row = document.createElement('tr');
        row.className = 'data-row';
        row.setAttribute('data-id', item.id); // Set data-id on the tr element
        row.innerHTML = `
            <td>
                <select class="form-select-sm" data-field="relationType">
                    <option value="">Select Type</option>
                </select>
            </td>
            <td>
                <select class="form-select-sm" data-field="glossary">
                    <option value="">Select Glossary</option>
                </select>
            </td>
            <td class="definition-cell">
                <span class="definition-text">${escapeHtml(item.glossaryDefinition || '')}</span>
            </td>
            <td class="status-cell">
                <span class="status-text">${escapeHtml(item.relationshipStatus || '')}</span>
            </td>
            <td class="action-cell">
                <div class="action-buttons">
                    <button type="button" class="action-btn add-btn" title="Add Row">
                        <i class="fas fa-plus"></i>
                    </button>
                    <button type="button" class="action-btn delete-btn" title="Delete Row">
                        <i class="fas fa-minus"></i>
                    </button>
                </div>
            </td>
        `;
        
        // Populate selects after row is added
        setTimeout(() => {
            populateRowSelects(row, item);
            // Add event listeners for updating definition and status
            addRowEventListeners(row);
        }, 100);
        
        return row;
    }
    
    // Create empty row for new entries
    function createEmptyRow() {
        const row = document.createElement('tr');
        row.className = 'empty-row';
        row.innerHTML = `
            <td>
                <select class="form-select-sm" data-field="relationType" data-new="true">
                    <option value="">Select Type</option>
                </select>
            </td>
            <td>
                <select class="form-select-sm" data-field="glossary" data-new="true">
                    <option value="">Select Glossary</option>
                </select>
            </td>
            <td class="definition-cell">
                <span class="definition-text">-</span>
            </td>
            <td class="status-cell">
                <span class="status-text">-</span>
            </td>
            <td class="action-cell">
                <div class="action-buttons">
                    <button type="button" class="action-btn add-btn" title="Add Row">
                        <i class="fas fa-plus"></i>
                    </button>
                    <button type="button" class="action-btn delete-btn" title="Delete Row">
                        <i class="fas fa-minus"></i>
                    </button>
                </div>
            </td>
        `;
        
        // Populate selects after row is added
        setTimeout(() => {
            populateRowSelects(row, null);
            // Add event listeners for updating definition and status
            addRowEventListeners(row);
        }, 100);
        
        return row;
    }
    
    // Add event listeners for row selects
    function addRowEventListeners(row) {
        const glossarySelect = row.querySelector('[data-field="glossary"]');
        const definitionSpan = row.querySelector('.definition-text');
        const statusSpan = row.querySelector('.status-text');
        
        if (glossarySelect) {
            glossarySelect.addEventListener('change', function() {
                const selectedGlossaryId = this.value;
                if (selectedGlossaryId && window.glossaryItems) {
                    const selectedGlossary = window.glossaryItems.find(item => item.id == selectedGlossaryId);
                    if (selectedGlossary && definitionSpan) {
                        definitionSpan.textContent = selectedGlossary.description || '-';
                    }
                } else if (definitionSpan) {
                    definitionSpan.textContent = '-';
                }
            });
        }
        
        // Set initial status for new rows
        if (statusSpan && originalInterfaceData) {
            statusSpan.textContent = originalInterfaceData.statusName || 'To Be Documented';
        }
    }
    
    // Load relation types for dropdown
    async function loadRelationTypes() {
        try {
            // Try multiple API endpoints to find the correct one
            let response;
            
            // First try: interface-x-glossary-relationtype
            try {
                response = await fetch('/api/interface-x-glossary-relationtype/list');
                if (response.ok) {
                    relationTypes = await response.json();
                    console.log('Loaded from interface-x-glossary-relationtype:', relationTypes);
                }
            } catch (e) {
                console.log('interface-x-glossary-relationtype not available, trying next...');
            }
            
            // Second try: interface-x-glossary with type parameter
            if (!relationTypes || relationTypes.length === 0) {
                try {
                    response = await fetch('/api/interface-x-glossary?type=relation-types');
                    if (response.ok) {
                        relationTypes = await response.json();
                        console.log('Loaded from interface-x-glossary with type param:', relationTypes);
                    }
                } catch (e) {
                    console.log('interface-x-glossary with type not available, trying next...');
                }
            }
            
            // Third try: regular glossary relation types as fallback
            if (!relationTypes || relationTypes.length === 0) {
                try {
                    response = await fetch('/api/glossary-relation-type/list');
                    if (response.ok) {
                        relationTypes = await response.json();
                        console.log('Loaded from glossary-relation-type (fallback):', relationTypes);
                    }
                } catch (e) {
                    console.log('glossary-relation-type not available');
                }
            }
            
            if (!relationTypes || relationTypes.length === 0) {
                console.warn('No relation types could be loaded from any endpoint, using hardcoded data');
                relationTypes = [
                    { id: 1, name: 'connected' },
                    { id: 2, name: 'Expected Glossary Item Coverage' }
                ];
            }
            
        } catch (error) {
            console.error('Error loading relation types:', error);
            relationTypes = [
                { id: 1, name: 'connected' },
                { id: 2, name: 'Expected Glossary Item Coverage' }
            ];
        }
    }
    
    // Load glossary items for dropdown
    async function loadGlossaryItems() {
        try {
            if (window.BUDG_API_SERVICE) {
                const items = await window.BUDG_API_SERVICE.getGlossaryList();
                // Store glossary items separately from table data
                window.glossaryItems = items;
                console.log('Loaded glossary items:', items);
            } else {
                console.error('BUDG_API_SERVICE not available');
            }
        } catch (error) {
            console.error('Error loading glossary items:', error);
        }
    }
    
    // Add new row
    function addNewRow() {
        console.log('Adding new row');
        const tbody = document.getElementById('glossaryTableBody');
        if (!tbody) return;
        
        // Add new empty row
        const emptyRow = createEmptyRow();
        tbody.appendChild(emptyRow);
    }
    
    // Remove empty row
    function removeEmptyRow(button) {
        console.log('Removing empty row');
        const row = button.closest('tr');
        if (row && row.classList.contains('empty-row') && !row.classList.contains('cleared-row')) {
            // Check if this is the first row in the table
            const tbody = row.closest('tbody');
            const allRows = tbody.querySelectorAll('tr');
            const isFirstRow = row === allRows[0];
            
            if (isFirstRow) {
                console.log('Cannot remove first row - preserving table structure');
                return;
            }
            
            row.remove();
        }
    }

    // Initialize change tracking
    function initializeChangeTracking() {
        // Track changes in form inputs
        const form = document.getElementById('interfaceEditForm');
        if (!form) return;
        
        // Track input changes
        const inputs = form.querySelectorAll('input, select, textarea');
        inputs.forEach(input => {
            input.addEventListener('change', markAsChanged);
            input.addEventListener('input', markAsChanged);
        });
        
        // Special tracking for system dropdowns
        const sourceInput = document.getElementById('sourceShortName');
        const targetInput = document.getElementById('targetShortName');
        
        if (sourceInput) {
            sourceInput.addEventListener('change', markAsChanged);
            sourceInput.addEventListener('input', markAsChanged);
            // Also track when dataset.systemId changes
            const observer = new MutationObserver(() => markAsChanged());
            observer.observe(sourceInput, { attributes: true, attributeFilter: ['data-system-id'] });
        }
        
        if (targetInput) {
            targetInput.addEventListener('change', markAsChanged);
            targetInput.addEventListener('input', markAsChanged);
            // Also track when dataset.systemId changes
            const observer = new MutationObserver(() => markAsChanged());
            observer.observe(targetInput, { attributes: true, attributeFilter: ['data-system-id'] });
        }
        
        // Track table changes
        const tableBody = document.getElementById('glossaryTableBody');
        if (tableBody) {
            tableBody.addEventListener('change', markAsChanged);
            tableBody.addEventListener('click', function(e) {
                if (e.target.closest('.add-btn') || e.target.closest('.delete-btn')) {
                    markAsChanged();
                }
            });
        }
        
        console.log('Change tracking initialized');
    }

    // Form validation
    function initializeFormValidation() {
        const form = document.getElementById('interfaceEditForm');
        if (!form) return;

        // Real-time validation
        const requiredFields = form.querySelectorAll('[required]');
        requiredFields.forEach(field => {
            field.addEventListener('blur', function() {
                validateField(this);
            });
            
            field.addEventListener('input', function() {
                if (this.style.borderColor === 'rgb(220, 53, 69)') {
                    validateField(this);
                }
            });
        });

        // Form submission validation
        form.addEventListener('submit', function(e) {
            e.preventDefault();
            if (validateForm()) {
                saveInterface();
            }
        });
    }

    function validateField(field) {
        const isValid = field.value.trim() !== '';
        
        if (!isValid) {
            field.style.borderColor = '#dc3545';
            field.style.boxShadow = '0 0 0 2px rgba(220, 53, 69, 0.1)';
        } else {
            field.style.borderColor = '';
            field.style.boxShadow = '';
        }
        
        return isValid;
    }

    function validateForm() {
        // Get active tab
        const activeTab = document.querySelector('.tab.active');
        const tabName = activeTab ? activeTab.getAttribute('data-tab') : 'summary';
        
        // For stakeholders and impact tabs, skip form validation
        // They have their own validation logic
        if (tabName === 'stakeholders' || tabName === 'impact') {
            console.log('Skipping form validation for tab:', tabName);
            return true;
        }
        
        // For summary tab, validate the main form
        const form = document.getElementById('interfaceEditForm');
        if (!form) {
            console.warn('Form not found for validation');
            return true; // Don't block save if form doesn't exist
        }
        
        // Only validate visible required fields
        const requiredFields = form.querySelectorAll('[required]');
        let isValid = true;

        requiredFields.forEach(field => {
            // Only validate if field is visible
            const isVisible = field.offsetParent !== null || 
                            field.style.display !== 'none' || 
                            !field.closest('[style*="display: none"]');
            
            if (isVisible && !validateField(field)) {
                isValid = false;
            }
        });

        return isValid;
    }

    // Helper function to get active sub-tab
    // This function detects which sub-tab is currently active within the active main tab
    function getActiveSubTab() {
        // Check for impact sub-tabs
        const impactSubTab = document.querySelector('#impact .sub-tab.active, #impactTab .sub-tab.active');
        if (impactSubTab) {
            const subTabName = impactSubTab.getAttribute('data-sub-tab');
            console.log('Found active impact sub-tab:', subTabName);
            return subTabName;
        }
        
        // Check for stakeholders sub-tabs (if any)
        const stakeholderSubTab = document.querySelector('#interfaceStakeholdersContainer .sub-tab.active, #stakeholdersTab .sub-tab.active');
        if (stakeholderSubTab) {
            const subTabName = stakeholderSubTab.getAttribute('data-sub-tab');
            console.log('Found active stakeholder sub-tab:', subTabName);
            return subTabName;
        }
        
        // Check for summary sub-tabs (if any)
        const summarySubTab = document.querySelector('#summaryTab .sub-tab.active, #summary .sub-tab.active');
        if (summarySubTab) {
            const subTabName = summarySubTab.getAttribute('data-sub-tab');
            console.log('Found active summary sub-tab:', subTabName);
            return subTabName;
        }
        
        console.log('No active sub-tab found');
        return null;
    }

    // Save interface function (saves current tab only)
    async function saveInterface(closeAfter = false) {
        // Get current active tab and subtab
        const activeTab = currentTab || getCurrentActiveTab();
        const activeSubTab = getActiveSubTab() || getActiveSubTabFromURL();
        
        console.log(`=== SAVING INTERFACE (TAB: ${activeTab}, SUBTAB: ${activeSubTab || 'none'}) ===`);
        
        const buttons = [document.getElementById('saveBtn'), document.getElementById('saveCloseBtn')];
        buttons.forEach(b => { 
            if (b) b.disabled = true; 
        });
        
        let convertedPayload = null;
        
        try {
            // Save data based on current tab
            if (activeTab === 'summary') {
                // Sync rich-text editor content back to textarea before reading
                if (typeof syncAdvancedRichTextToTextarea === 'function') {
                    syncAdvancedRichTextToTextarea('interfaceDescription');
                }
                // Save summary data (if there are form changes)
                if (hasFormChanges()) {
                    const form = document.getElementById('interfaceEditForm');
                    const formData = new FormData(form);

                    // Convert FormData to JSON object
                    const payload = {};
                    for (let [key, value] of formData.entries()) {
                        payload[key] = value;
                    }

                    // Get system IDs from the current dropdown selections
                    const sourceInput = document.getElementById('sourceShortName');
                    const targetInput = document.getElementById('targetShortName');
                    const sourceSelect = document.getElementById('sourceSystem');
                    const targetSelect = document.getElementById('targetSystem');
        
                    console.log('=== GETTING SYSTEM IDs FOR SAVE ===');
                    console.log('Source input value:', sourceInput?.value);
                    console.log('Target input value:', targetInput?.value);
                    console.log('Source input dataset.systemId:', sourceInput?.dataset?.systemId);
                    console.log('Target input dataset.systemId:', targetInput?.dataset?.systemId);
                    console.log('Source select value:', sourceSelect?.value);
                    console.log('Target select value:', targetSelect?.value);
                    
                    // Get system IDs from dataset attributes (set by dropdown selection) - PRIMARY SOURCE
                    let sourceSystemId = sourceInput?.dataset?.systemId;
                    let targetSystemId = targetInput?.dataset?.systemId;
        
                    // Fallback 1: try to get from hidden select elements
                    if (!sourceSystemId && sourceSelect?.value) {
                        sourceSystemId = sourceSelect.value;
                        if (sourceInput) {
                            sourceInput.dataset.systemId = sourceSystemId;
                        }
                        console.log('Got source system ID from hidden select:', sourceSystemId);
                    }
                    if (!targetSystemId && targetSelect?.value) {
                        targetSystemId = targetSelect.value;
                        if (targetInput) {
                            targetInput.dataset.systemId = targetSystemId;
                        }
                        console.log('Got target system ID from hidden select:', targetSystemId);
                    }
                    
                    // Fallback 2: try to match by name if IDs are still missing
                    if (!sourceSystemId && sourceInput?.value && sourceSelect) {
                        console.log('Trying to match source system by name:', sourceInput.value);
                        for (let option of sourceSelect.options || []) {
                            if (option.textContent === sourceInput.value || option.textContent.trim() === sourceInput.value.trim()) {
                                sourceSystemId = option.value;
                                if (sourceInput) {
                                    sourceInput.dataset.systemId = sourceSystemId;
                                }
                                if (sourceSelect) {
                                    sourceSelect.value = sourceSystemId;
                                }
                                console.log('Matched source system by name:', sourceInput.value, 'ID:', sourceSystemId);
                                break;
                            }
                        }
                    }
                    if (!targetSystemId && targetInput?.value && targetSelect) {
                        console.log('Trying to match target system by name:', targetInput.value);
                        for (let option of targetSelect.options || []) {
                            if (option.textContent === targetInput.value || option.textContent.trim() === targetInput.value.trim()) {
                                targetSystemId = option.value;
                                if (targetInput) {
                                    targetInput.dataset.systemId = targetSystemId;
                                }
                                if (targetSelect) {
                                    targetSelect.value = targetSystemId;
                                }
                                console.log('Matched target system by name:', targetInput.value, 'ID:', targetSystemId);
                                break;
                            }
                        }
                    }
                    
                    // Fallback 3: try to get from original data if still missing
                    if (!sourceSystemId && originalInterfaceData) {
                        const originalSourceId = originalInterfaceData.sourceSystemId || 
                                                originalInterfaceData.source_system_id || 
                                                originalInterfaceData.source_systemID || 
                                                originalInterfaceData.Source_systemID;
                        if (originalSourceId) {
                            sourceSystemId = String(originalSourceId);
                            if (sourceInput) {
                                sourceInput.dataset.systemId = sourceSystemId;
                            }
                            if (sourceSelect) {
                                sourceSelect.value = sourceSystemId;
                            }
                            console.log('Got source system ID from original data:', sourceSystemId);
                        }
                    }
                    if (!targetSystemId && originalInterfaceData) {
                        const originalTargetId = originalInterfaceData.targetSystemId || 
                                                originalInterfaceData.target_system_id || 
                                                originalInterfaceData.target_systemID || 
                                                originalInterfaceData.Target_systemID;
                        if (originalTargetId) {
                            targetSystemId = String(originalTargetId);
                            if (targetInput) {
                                targetInput.dataset.systemId = targetSystemId;
                            }
                            if (targetSelect) {
                                targetSelect.value = targetSystemId;
                            }
                            console.log('Got target system ID from original data:', targetSystemId);
                        }
                    }
                    
                    console.log('=== FINAL SYSTEM IDs FOR SAVE ===');
                    console.log('Current system selections:', {
                        sourceName: sourceInput?.value,
                        targetName: targetInput?.value,
                        sourceId: sourceSystemId,
                        targetId: targetSystemId,
                        sourceSelectValue: sourceSelect?.value,
                        targetSelectValue: targetSelect?.value,
                        sourceDataset: sourceInput?.dataset?.systemId,
                        targetDataset: targetInput?.dataset?.systemId
                    });
                    
                    // Set system IDs in payload
                    if (sourceSystemId) {
                        payload.source_system_id = sourceSystemId;
                        console.log('✅ Set source_system_id in payload:', sourceSystemId);
                    } else {
                        console.error('❌ No source system ID found!');
                        console.error('Source input value:', sourceInput?.value);
                        console.error('Source select value:', sourceSelect?.value);
                        console.error('Source input dataset:', sourceInput?.dataset);
                    }
                    if (targetSystemId) {
                        payload.target_system_id = targetSystemId;
                        console.log('✅ Set target_system_id in payload:', targetSystemId);
                    } else {
                        console.error('❌ No target system ID found!');
                        console.error('Target input value:', targetInput?.value);
                        console.error('Target select value:', targetSelect?.value);
                        console.error('Target input dataset:', targetInput?.dataset);
                    }

        const segmentFieldContainer = document.getElementById('segmentFieldContainer');
        let segmentId = null;
        if (segmentFieldContainer && window.SegmentField) {
            // Try to get the segment field instance
            const segmentFieldInstance = segmentFieldContainer._segmentFieldInstance;
            if (segmentFieldInstance && typeof segmentFieldInstance.getValue === 'function') {
                segmentId = segmentFieldInstance.getValue() || null;
            }
        }

        // Convert field names to match backend expectations
        convertedPayload = {
            name: payload.Name,
            ref_number: payload.Ref_number,
            description: payload.Description,
            synchronisation_control: payload.Synchronisation_Control,
            asset_id: payload.Asset_ID,
            status_id: parseInt(payload.status_id) || null,
            lifecycle_id: parseInt(payload.Lifecycle_id) || null,
            is_public: parseInt(payload.is_public) || null,
            automation_id: parseInt(payload.Automation_ID) || null,
            frequency_id: parseInt(payload.Frequency_ID) || null,
            transfer_method_id: parseInt(payload.Transfer_Method_ID) || null,
            transfer_format_id: parseInt(payload.Transfer_Format_ID) || null,
            classification_id: parseInt(payload.Classification_id) || null,
            source_system_id: parseInt(payload.source_system_id) || null,
            target_system_id: parseInt(payload.target_system_id) || null,
            segmentId: segmentId
        };

                    // Add ID for updates
                    if (currentInterfaceId) {
                        convertedPayload.id = currentInterfaceId;
                    }

                    // Log the data being sent for debugging
                    console.log('Sending interface payload:', convertedPayload);

                    // Check for required fields based on schema
                    const requiredFields = ['name', 'description', 'status_id', 'is_public', 'lifecycle_id', 'automation_id', 'source_system_id', 'target_system_id'];
                    const missingFields = [];
                    
                    for (const field of requiredFields) {
                        if (!convertedPayload[field] || convertedPayload[field] === '' || convertedPayload[field] === null) {
                            missingFields.push(field);
                        }
                    }
                    
                    // Additional validation for system IDs with helpful messages
                    if (!convertedPayload.source_system_id || convertedPayload.source_system_id === null) {
                        console.error('Source system ID is missing!');
                        console.error('Source input:', {
                            value: sourceInput?.value,
                            dataset: sourceInput?.dataset,
                            exists: !!sourceInput
                        });
                        console.error('Source select:', {
                            value: sourceSelect?.value,
                            optionsCount: sourceSelect?.options?.length,
                            exists: !!sourceSelect
                        });
                        if (!missingFields.includes('source_system_id')) {
                            missingFields.push('source_system_id');
                        }
                    }
                    
                    if (!convertedPayload.target_system_id || convertedPayload.target_system_id === null) {
                        console.error('Target system ID is missing!');
                        console.error('Target input:', {
                            value: targetInput?.value,
                            dataset: targetInput?.dataset,
                            exists: !!targetInput
                        });
                        console.error('Target select:', {
                            value: targetSelect?.value,
                            optionsCount: targetSelect?.options?.length,
                            exists: !!targetSelect
                        });
                        if (!missingFields.includes('target_system_id')) {
                            missingFields.push('target_system_id');
                        }
                    }
                    
                    if (missingFields.length > 0) {
                        console.error('Missing required fields:', missingFields);
                        const userFriendlyNames = {
                            'name': 'Name',
                            'description': 'Description',
                            'status_id': 'BUDG Status',
                            'is_public': 'BUDG Viewing',
                            'lifecycle_id': 'Lifecycle',
                            'automation_id': 'Automation',
                            'source_system_id': 'Source System',
                            'target_system_id': 'Target System'
                        };
                        const friendlyFieldNames = missingFields.map(f => userFriendlyNames[f] || f).join(', ');
                        showMessage('Missing required fields: ' + friendlyFieldNames, 'error');
                        buttons.forEach(b => { if (b) b.disabled = false; });
                        return;
                    }
                }
            }
            
            // For summary tab, even if there are no form changes, we might need to save Data Content Summary table
            if (activeTab === 'summary' && !convertedPayload) {
                console.log('No form changes to save, checking Data Content Summary table...');
                // Still save glossary data if there are changes in the table
                const interfaceId = currentInterfaceId || parseId();
                if (interfaceId != null) {
                    try {
                        await saveGlossaryData(closeAfter);
                        console.log('✅ Data Content Summary table saved successfully');
                    } catch (glossaryError) {
                        console.error('Error saving Data Content Summary table:', glossaryError);
                        showMessage('Error saving Data Content Summary table: ' + glossaryError.message, 'error');
                    }
                    // Save custom fields even if main form hasn't changed
                    if (window.customFieldsContext && window.customFieldsContext.saveValues) {
                        try {
                            await window.customFieldsContext.saveValues(interfaceId);
                            console.log('✅ Custom fields saved successfully');
                        } catch (cfError) {
                            console.error('Error saving custom fields:', cfError);
                        }
                    }
                    resetChanges();
                    await window.LockInitHelper.releaseLock();
                    showMessage('UPDATES SAVED', 'success');
                    if (closeAfter) {
                        setTimeout(() => {
                            if (interfaceId) {
                                window.location.href = `/view/system-interface/${interfaceId}`;
                            }
                        }, 1500);
                    }
                } else {
                    console.log('No interface ID available and no form changes');
                    showMessage('No changes to save', 'info');
                }
                buttons.forEach(b => { if (b) b.disabled = false; });
                return;
            }

            // For summary tab, save the interface data
            if (activeTab === 'summary' && convertedPayload) {
            try {
                // Use the single save method for both create and update
                const result = await window.BUDG_API_SERVICE.saveInterface(convertedPayload);
                console.log('Interface saved successfully:', result);
                
                // Set current interface ID if it's a new interface
                if (!currentInterfaceId && result.interfaceId) {
                    currentInterfaceId = result.interfaceId;
                }
                
                // Save custom fields if context exists
                const interfaceId = currentInterfaceId || result.interfaceId || parseId();
                if (window.customFieldsContext && window.customFieldsContext.saveValues && interfaceId != null) {
                    try {
                        await window.customFieldsContext.saveValues(interfaceId);
                        console.log('✅ Custom fields saved successfully');
                    } catch (error) {
                        console.error('Error saving custom fields:', error);
                    }
                }
                
                // Save Data Content Summary table data (glossary data) - it's in the summary tab
                if (interfaceId != null) {
                    try {
                        await saveGlossaryData(closeAfter);
                        console.log('✅ Data Content Summary table saved successfully');
                    } catch (glossaryError) {
                        console.error('Error saving Data Content Summary table:', glossaryError);
                        // Don't fail the entire save if glossary data fails
                        showMessage('Interface saved, but there was an error saving Data Content Summary table: ' + glossaryError.message, 'warning');
                    }
                }
            } catch (error) {
                console.error('Error saving interface:', error);
                showMessage('Error saving interface: ' + (error?.message || 'Unknown error'), 'error');
                buttons.forEach(b => { if (b) b.disabled = false; });
                return;
            }
            }
            
            // Save stakeholders data only if we're on stakeholders tab
            if (activeTab === 'stakeholders' && window.InterfaceStakeholderEdit && window.InterfaceStakeholderEdit.saveStakeholders) {
            try {
                const stakeholdersHasChanges = window.InterfaceStakeholderEdit.hasDataChanged && 
                    window.InterfaceStakeholderEdit.hasDataChanged();
                if (stakeholdersHasChanges) {
                    await window.InterfaceStakeholderEdit.saveStakeholders(false);
                    console.log('✅ Stakeholders saved successfully');
                } else {
                    console.log('No stakeholders changes to save');
                    showMessage('No changes to save', 'info');
                    buttons.forEach(b => { if (b) b.disabled = false; });
                    return;
                }
            } catch (stakeholdersError) {
                console.error('Error saving stakeholders:', stakeholdersError);
                showMessage('Error saving stakeholders: ' + stakeholdersError.message, 'error');
                buttons.forEach(b => { if (b) b.disabled = false; });
                return;
            }
            }
            
            // Save impact: always if impact tab active, or when segment changed from summary tab.
            if (window.saveAllImpactData && typeof window.saveAllImpactData === 'function') {
            try {
                const interfaceImpactDirty = typeof window.hasImpactChanges === 'function' && window.hasImpactChanges();
                const currentInterfaceSegment = getInterfaceCurrentSegmentId();
                const interfaceSegmentChanged = currentInterfaceSegment !== normalizeInterfaceSegmentId(originalInterfaceSegmentId);
                const shouldSaveInterfaceImpact = interfaceImpactDirty || (activeTab === 'summary' && interfaceSegmentChanged);
                if (activeTab === 'impact') {
                    if (interfaceImpactDirty || interfaceSegmentChanged) {
                        if (interfaceSegmentChanged && !interfaceImpactDirty && window.initImpactEdit) {
                            const id = currentInterfaceId || parseId();
                            if (id) await window.initImpactEdit(id);
                        }
                        const impactResult = await window.saveAllImpactData(currentInterfaceId || parseId());
                        if (impactResult && impactResult.process?.success !== false) {
                            const id = currentInterfaceId || parseId();
                            if (window.initImpactEdit && id) await window.initImpactEdit(id);
                            console.log('✅ Impact saved successfully');
                        }
                    } else {
                        console.log('No impact changes to save');
                        showMessage('No changes to save', 'info');
                        buttons.forEach(b => { if (b) b.disabled = false; });
                        return;
                    }
                } else if (shouldSaveInterfaceImpact) {
                    if (interfaceSegmentChanged && !interfaceImpactDirty && window.initImpactEdit) {
                        const id = currentInterfaceId || parseId();
                        if (id) await window.initImpactEdit(id);
                    }
                    const impactResult = await window.saveAllImpactData(currentInterfaceId || parseId());
                    if (impactResult && impactResult.process?.success !== false) {
                        console.log('✅ Impact saved on segment change');
                    }
                }
            } catch (impactError) {
                console.error('Error saving impact:', impactError);
                if (activeTab === 'impact') {
                    showMessage('Error saving impact: ' + impactError.message, 'error');
                    buttons.forEach(b => { if (b) b.disabled = false; });
                    return;
                }
            }
            }
            
            // Save glossary/data content only if we're on data tab
            if (activeTab === 'data') {
            try {
                await saveGlossaryData(closeAfter);
            } catch (glossaryError) {
                console.error('Error saving glossary data:', glossaryError);
                showMessage('Error saving glossary data: ' + glossaryError.message, 'error');
                buttons.forEach(b => { if (b) b.disabled = false; });
                return;
            }
            }
            
            // For other tabs (history, change), show message
            if (activeTab === 'history' || activeTab === 'change') {
                showMessage('This tab does not support saving', 'info');
                buttons.forEach(b => { if (b) b.disabled = false; });
                return;
            }
            
            // Mark form as clean after successful save
            resetChanges();
            originalInterfaceSegmentId = getInterfaceCurrentSegmentId();
            
            // Release lock after successful save
            await window.LockInitHelper.releaseLock();
            
            // Show success message
            showMessage('UPDATES SAVED', 'success');
                
            if (closeAfter) {
                setTimeout(() => {
                    // Use currentInterfaceId if available (from save result), otherwise parse from URL
                    const id = currentInterfaceId || parseId();
                    if (id) {
                        window.location.href = `/view/system-interface/${id}`;
                    } else {
                        console.error('No interface ID available for redirect');
                        showMessage('Error: Could not determine interface ID', 'error');
                    }
                }, 1500);
            }
            
        } catch (error) {
            console.error('Error saving interface:', error);
            const errorMsg = error?.body?.error || error?.body?.message || error?.message || 'Error saving interface';
            showMessage(errorMsg, 'error');
        } finally {
            buttons.forEach(b => { 
                if (b) b.disabled = false; 
            });
        }
    }

    // ========================================
    // DATA CONTENT SUMMARY TABLE FUNCTIONS
    // ========================================
    
    // Save glossary data separately (like strategic source)
    async function saveGlossaryData(closeAfter = false) {
        try {
            console.log('Saving DATA CONTENT SUMMARY table data...');
            
            // Get interface ID from currentInterfaceId or parse from URL
            let interfaceId = currentInterfaceId || parseId();
            console.log('Current interface ID:', currentInterfaceId, 'Parsed ID:', parseId(), 'Using:', interfaceId);
            if (!interfaceId) {
                console.warn('Interface ID is required to save glossary data');
                showMessage('Interface ID is required to save Data Content Summary table', 'error');
                return;
            }
            // Update currentInterfaceId if it wasn't set (for use by addGlossaryRecord, etc.)
            if (!currentInterfaceId && interfaceId) {
                currentInterfaceId = interfaceId;
            }
            
            // Check if there are any changes in the table
            const hasTableChanges = window.rowsToDelete && window.rowsToDelete.length > 0;
            const hasDataRows = document.querySelectorAll('.data-row:not(.marked-for-deletion)').length > 0;
            const hasEmptyRowsWithData = Array.from(document.querySelectorAll('.empty-row:not(.cleared-row)')).some(row => {
                const relationSelect = row.querySelector('[data-field="relationType"]');
                const glossarySelect = row.querySelector('[data-field="glossary"]');
                return relationSelect?.value || glossarySelect?.value;
            });
            
            console.log('Table changes check:', {
                hasTableChanges: hasTableChanges,
                rowsToDeleteLength: window.rowsToDelete ? window.rowsToDelete.length : 0,
                hasDataRows: hasDataRows,
                hasEmptyRowsWithData: hasEmptyRowsWithData
            });
            
            // Debug: Show all rows in table
            const allTableRows = document.querySelectorAll('#glossaryTableBody tr');
            console.log('All table rows at save time:', Array.from(allTableRows).map((r, i) => ({
                index: i,
                classes: r.className,
                dataId: r.getAttribute('data-id'),
                isDataRow: r.classList.contains('data-row'),
                isEmptyRow: r.classList.contains('empty-row'),
                isClearedRow: r.classList.contains('cleared-row')
            })));
            
            if (!hasTableChanges && !hasDataRows && !hasEmptyRowsWithData) {
                console.log('No table changes detected');
                return;
            }

            // First, handle staged deletions (silent deletion from database)
            console.log('Checking for rows to delete:', window.rowsToDelete);
            if (window.rowsToDelete && window.rowsToDelete.length > 0) {
                console.log('Deleting rows from database:', window.rowsToDelete);
                let deletedCount = 0;
                for (const id of window.rowsToDelete) {
                    try {
                        console.log(`Attempting to delete row with ID: ${id}`);
                        await deleteGlossaryItem(id);
                        deletedCount++;
                        console.log(`Successfully deleted row with ID: ${id}`);
                    } catch (error) {
                        console.error(`Failed to delete row ${id}:`, error);
                        // Continue with other deletions even if one fails
                    }
                }
                console.log(`Successfully deleted ${deletedCount} rows from database`);
                // Clear the rows to delete array
                window.rowsToDelete = [];
            } else {
                console.log('No rows to delete from database');
            }

            // Get all data rows from the table (excluding marked for deletion)
            const dataRows = document.querySelectorAll('.data-row:not(.marked-for-deletion)');
            const emptyRows = document.querySelectorAll('.empty-row:not(.cleared-row)');
            
            console.log('Found data rows:', dataRows.length);
            console.log('Found empty rows:', emptyRows.length);
            
            // Process existing data rows (updates)
            for (const row of dataRows) {
                const relationSelect = row.querySelector('[data-field="relationType"]');
                const glossarySelect = row.querySelector('[data-field="glossary"]');
                const idAttr = row.querySelector('[data-id]');
                
                if (relationSelect && glossarySelect && idAttr) {
                    const relationTypeId = relationSelect.value;
                    const glossaryId = glossarySelect.value;
                    const id = idAttr.getAttribute('data-id');
                    
                    // Validate required fields
                    if (!relationTypeId || !glossaryId) {
                        showMessage('Relationship Type and Glossary are required for all rows', 'error');
                        return;
                    }
                    
                    if (relationTypeId && glossaryId) {
                        console.log(`Updating row ${id} with relationType: ${relationTypeId}, glossary: ${glossaryId}`);
                        
                        // Update the existing record
                        await updateGlossaryRecord(id, relationTypeId, glossaryId);
                    }
                }
            }
            
            // Process empty rows (new additions) - only if they have data
            for (const row of emptyRows) {
                const relationSelect = row.querySelector('[data-field="relationType"]');
                const glossarySelect = row.querySelector('[data-field="glossary"]');
                
                if (relationSelect && glossarySelect) {
                    const relationTypeId = relationSelect.value;
                    const glossaryId = glossarySelect.value;
                    
                    console.log('Empty row values:', { relationTypeId, glossaryId });
                    
                    // Only save if both values are selected
                    if (relationTypeId && glossaryId && relationTypeId !== '' && glossaryId !== '') {
                        console.log(`Adding new row with relationType: ${relationTypeId}, glossary: ${glossaryId}`);
                        
                        // Add new record
                        await addGlossaryRecord(relationTypeId, glossaryId);
                    }
                }
            }
            
            console.log('DATA CONTENT SUMMARY table data saved successfully');
            
            // Refresh the table to show updated data
            await loadGlossaryData(interfaceId);
            
        } catch (error) {
            console.error('Error saving DATA CONTENT SUMMARY table data:', error);
            showMessage('Error saving table data: ' + error.message, 'error');
        }
    }

    // Additional table control functions
    async function refreshTableData() {
        try {
            if (currentInterfaceId) {
                await loadGlossaryData(currentInterfaceId);
                showMessage('Interface Updated Successfully', 'success');
            } else {
                showMessage('No interface ID available to refresh table', 'error');
            }
        } catch (error) {
            console.error('Error refreshing table data:', error);
            showMessage('Error refreshing table data: ' + error.message, 'error');
        }
    }

    function clearTableData() {
        const tableBody = document.getElementById('glossaryTableBody');
        if (tableBody) {
            tableBody.innerHTML = '';
            // Add empty row
            const emptyRow = createEmptyRow();
            tableBody.appendChild(emptyRow);
            showMessage('Table data cleared', 'info');
        }
    }

    function validateTableData() {
        const dataRows = document.querySelectorAll('.data-row');
        const emptyRows = document.querySelectorAll('.empty-row');
        
        // Check if there are any rows with incomplete data
        for (const row of dataRows) {
            const relationSelect = row.querySelector('[data-field="relationType"]');
            const glossarySelect = row.querySelector('[data-field="glossary"]');
            
            if (relationSelect && glossarySelect) {
                const relationTypeId = relationSelect.value;
                const glossaryId = glossarySelect.value;
                
                if (!relationTypeId || !glossaryId) {
                    showMessage('Please complete all required fields in the table', 'error');
                    return false;
                }
            }
        }
        
        // Check empty rows for incomplete data
        for (const row of emptyRows) {
            const relationSelect = row.querySelector('[data-field="relationType"]');
            const glossarySelect = row.querySelector('[data-field="glossary"]');
            
            if (relationSelect && glossarySelect) {
                const relationTypeId = relationSelect.value;
                const glossaryId = glossarySelect.value;
                
                // If any field is filled, both must be filled
                if ((relationTypeId && !glossaryId) || (!relationTypeId && glossaryId)) {
                    showMessage('Please complete both Relationship Type and Glossary fields', 'error');
                    return false;
                }
            }
        }
        
        return true;
    }

    // ========================================
    // END DATA CONTENT SUMMARY TABLE FUNCTIONS
    // ========================================

    // Update existing glossary record
    async function updateGlossaryRecord(id, relationTypeId, glossaryId) {
        try {
            console.log('Updating glossary record:', {
                id: parseInt(id),
                interfaceId: currentInterfaceId,
                glossaryId: parseInt(glossaryId),
                relationTypeId: parseInt(relationTypeId)
            });
            
            const response = await fetch('/api/interface-x-glossary', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    action: 'update',
                    id: parseInt(id),
                    interfaceId: currentInterfaceId,
                    glossaryId: parseInt(glossaryId),
                    relationTypeId: parseInt(relationTypeId)
                })
            });
            
            if (response.ok) {
                const result = await response.json();
                console.log('Update record response:', result);
                if (!result.success) {
                    throw new Error(result.message || 'Failed to update record');
                }
            } else {
                const errorText = await response.text();
                throw new Error(`HTTP ${response.status}: ${errorText}`);
            }
        } catch (error) {
            console.error('Error updating glossary record:', error);
            throw error;
        }
    }
    
    // Add new glossary record
    async function addGlossaryRecord(relationTypeId, glossaryId) {
        try {
            console.log('Adding new glossary record:', {
                interfaceId: currentInterfaceId,
                glossaryId: parseInt(glossaryId),
                relationTypeId: parseInt(relationTypeId)
            });
            
            // Validate required data
            if (!currentInterfaceId) {
                throw new Error('Interface ID is required');
            }
            if (!relationTypeId || !glossaryId) {
                throw new Error('Relation type and glossary are required');
            }
            
            const requestData = {
                action: 'add',
                interfaceId: currentInterfaceId,
                glossaryId: parseInt(glossaryId),
                relationTypeId: parseInt(relationTypeId)
            };
            
            console.log('Sending request data:', requestData);
            
            const response = await fetch('/api/interface-x-glossary', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(requestData)
            });
            
            console.log('Add record response status:', response.status);
            
            if (response.ok) {
                const result = await response.json();
                console.log('Add record response:', result);
                if (!result.success) {
                    throw new Error(result.message || 'Failed to add new record');
                }
            } else {
                const errorText = await response.text();
                throw new Error(`HTTP ${response.status}: ${errorText}`);
            }
        } catch (error) {
            console.error('Error adding glossary record:', error);
            throw error;
        }
    }

    // Show message function (like glossary page)
    function showMessage(message, type = 'info') {
        // Remove existing messages
        const existingMessage = document.getElementById('success-message');
        if (existingMessage) {
            existingMessage.remove();
        }
        
        // Create message element
        const messageDiv = document.createElement('div');
        messageDiv.id = 'success-message';
        
        // Determine if it's an error (red) or success (green)
        // "NO CHANGES" should be red, "UPDATES SAVED" should be green
        const isError = type === 'error' || message === 'NO CHANGES' || message.toUpperCase().includes('NO CHANGES');
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
            animation: slideDown 0.3s ease-out;
        `;
        messageDiv.textContent = message;
        
        // Add CSS animation if not already present
        if (!document.querySelector('#slideDown-animation-style')) {
            const style = document.createElement('style');
            style.id = 'slideDown-animation-style';
            style.textContent = `
                @keyframes slideDown {
                    from {
                        opacity: 0;
                        transform: translateX(-50%) translateY(-20px);
                    }
                    to {
                        opacity: 1;
                        transform: translateX(-50%) translateY(0);
                    }
                }
                @keyframes slideUp {
                    from {
                        opacity: 1;
                        transform: translateX(-50%) translateY(0);
                    }
                    to {
                        opacity: 0;
                        transform: translateX(-50%) translateY(-20px);
                    }
                }
            `;
            document.head.appendChild(style);
        }
        
        // Add to page
        document.body.appendChild(messageDiv);
        
        // Auto remove after 3 seconds
        setTimeout(() => {
            if (messageDiv.parentNode) {
                messageDiv.style.animation = 'slideUp 0.3s ease-out';
                setTimeout(() => {
                    if (messageDiv.parentNode) {
                        messageDiv.remove();
                    }
                }, 300);
            }
        }, 3000);
    }

    // Initialize all functionality when DOM is loaded
    document.addEventListener('DOMContentLoaded', async function() {
        try {
            // Initialize rowsToDelete array
            window.rowsToDelete = [];
            console.log('Initialized window.rowsToDelete array');
            
            // Initialize tab navigation
            initializeTabNavigation();
            
            // Initialize data summary table
            initializeDataSummaryTable();
            
            // Initialize form validation
            initializeFormValidation();
            
            // Initialize change tracking
            initializeChangeTracking();
        
        // Add event listeners for save buttons
        const saveBtn = document.getElementById('saveBtn');
        const saveCloseBtn = document.getElementById('saveCloseBtn');
        const closeBtn = document.getElementById('closeBtn');

        // Ensure buttons don't submit the form
        if (saveBtn) {
            // Set button type to prevent form submission
            if (saveBtn.tagName === 'BUTTON') {
                saveBtn.type = 'button';
            }
            // Remove any existing listeners by cloning (cleaner approach)
            const saveBtnHandler = async function(e) {
                e.preventDefault();
                e.stopPropagation();
                console.log('Save button clicked');
                
                if (validateForm()) {
                    await saveInterface(false);
                } else {
                    console.log('Form validation failed');
                    showMessage('Please fill in all required fields', 'error');
                }
            };
            // Remove old listener if exists and add new one
            saveBtn.onclick = null;
            saveBtn.addEventListener('click', saveBtnHandler);
        } else {
            console.warn('Save button not found!');
        }

        if (saveCloseBtn) {
            // Set button type to prevent form submission
            if (saveCloseBtn.tagName === 'BUTTON') {
                saveCloseBtn.type = 'button';
            }
            const saveCloseBtnHandler = async function(e) {
                e.preventDefault();
                e.stopPropagation();
                console.log('Save & Close button clicked');
                
                if (validateForm()) {
                    await saveInterface(true); // Pass closeAfter parameter
                } else {
                    console.log('Form validation failed');
                    showMessage('Please fill in all required fields', 'error');
                }
            };
            // Remove old listener if exists and add new one
            saveCloseBtn.onclick = null;
            saveCloseBtn.addEventListener('click', saveCloseBtnHandler);
        } else {
            console.warn('Save & Close button not found!');
        }

        if (closeBtn) {
            closeBtn.addEventListener('click', async function(e) {
                e.preventDefault();
                const confirmMessage = 'Are you sure you want to close without saving?';
                const confirmed = await (typeof window.showConfirmDialog === 'function'
                    ? window.showConfirmDialog({ message: confirmMessage, type: 'warning' })
                    : Promise.resolve(confirm(confirmMessage)));
                if (confirmed) {
                    // Release lock before closing
                    if (window.LockInitHelper) {
                        await window.LockInitHelper.releaseLock();
                    } else if (window.currentLockManager) {
                        await window.currentLockManager.releaseLock();
                    }
                    // Get interface ID from URL or current interface ID
                    const interfaceId = currentInterfaceId || parseId();
                    if (interfaceId) {
                        // Navigate to interface view page
                        window.location.href = `/view/system-interface/${interfaceId}`;
                    } else {
                        // Fallback to interface list if no ID available
                        window.location.href = '/view/system-interface';
                    }
                }
            });
        }

        // Separate event listeners for DATA CONTENT SUMMARY table
        // These will be triggered by the same save buttons but handle only table data
        const saveButtons = [saveBtn, saveCloseBtn];

        } catch (error) {
            console.error('Error initializing system interface edit page:', error);
            showMessage('Error initializing page: ' + (error?.message || 'Unknown error'), 'error');
        }
    });
})();


