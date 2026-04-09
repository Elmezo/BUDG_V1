// Dataset Relationship Edit JavaScript
(function() {
    let currentDatasetId = null;
    let currentView = null; // Store current view mode ('changes' or null for 'original')
    let relationshipsData = [];
    let originalData = [];
    let lookupData = {
        relationTypes: [],
        relationScopes: []
    };
    let cascadingData = {
        system: null,
        attributes: [],
        interfaces: [],
        datasets: []
    };
    let attributesByDataset = new Map(); // Cache for attributes by dataset

    // Initialize relationship edit
    window.initDatasetRelationshipEdit = function(datasetId, view = null) {
        console.log('Initializing relationship edit for dataset:', datasetId, 'view:', view);
        currentDatasetId = datasetId;
        currentView = view; // Store view mode
        loadRelationshipsForEdit(view);
    };

    // Load all data needed for edit
    async function loadRelationshipsForEdit(view = null) {
        const container = document.getElementById('relationshipsEditContainer');
        if (!container) return;

        container.innerHTML = '<div style="padding: 2rem; text-align: center;">Loading relationships...</div>';

        try {
            // Load in parallel
            await Promise.all([
                loadExistingRelationships(view),
                loadLookups(),
                loadCascadingData()
            ]);

            renderEditableRelationshipsTable();
        } catch (error) {
            console.error('Error loading relationships for edit:', error);
            container.innerHTML = `
                <div style="padding: 2rem; text-align: center; color: var(--required-color, #dc3545);">
                    <i class="fas fa-exclamation-triangle"></i>
                    <p>Error loading relationships: ${error.message}</p>
                </div>
            `;
        }
    }

    // Load existing relationships
    async function loadExistingRelationships(view = null) {
        try {
            // Build URL with view parameter and cache-busting
            let url = `/api/dataset-relationships/${currentDatasetId}`;
            if (view === 'changes') {
                url += '?view=changes';
            }
            url += (url.includes('?') ? '&' : '?') + '_t=' + new Date().getTime(); // Cache-busting
            
            const response = await fetch(url, {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const data = await response.json();
            // Transform the data to include all necessary fields
            relationshipsData = (data.inbound || []).map(rel => ({
                ID: rel.ID,
                Source_AttributeID: rel.Source_AttributeID,
                Target_AttributeID: rel.Target_AttributeID,
                Relation_Type: rel.Relation_Type,
                Relation_Scope: rel.Relation_Scope,
                Relation_Method: rel.Relation_Method,
                Sourcing_Logic: rel.Sourcing_Logic || rel.sourcingLogic || null,
                Review_Status: rel.Review_Status || rel.reviewStatus || null,
                targetDatasetId: rel.targetDatasetId,  // This is now available from backend
                systemId: rel.systemId  // System ID from the loaded relationship
            }));
            originalData = JSON.parse(JSON.stringify(relationshipsData));
            
            console.log('Loaded relationships:', relationshipsData);
        } catch (error) {
            console.error('Error loading existing relationships:', error);
            relationshipsData = [];
            originalData = [];
        }
    }

    // Load lookup data
    async function loadLookups() {
        try {
            const response = await fetch('/api/dataset-relationships/lookups', {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            lookupData = await response.json();
            console.log('Loaded lookups:', lookupData);
        } catch (error) {
            console.error('Error loading lookups:', error);
        }
    }

    // Load cascading data
    async function loadCascadingData() {
        try {
            const response = await fetch(`/api/dataset-relationships/cascading/${currentDatasetId}`, {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            cascadingData = await response.json();
            console.log('Loaded cascading data:', cascadingData);
        } catch (error) {
            console.error('Error loading cascading data:', error);
        }
    }

    // Load interfaces and datasets for a specific system
    async function loadSystemData(systemId) {
        try {
            const response = await fetch(`/api/dataset-relationships/system-data/${systemId}`, {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const data = await response.json();
            return {
                interfaces: data.interfaces || [],
                datasets: data.datasets || []
            };
        } catch (error) {
            console.error('Error loading system data:', error);
            return { interfaces: [], datasets: [] };
        }
    }

    // Load attributes for a specific dataset (cascading)
    async function loadAttributesForDataset(datasetId) {
        if (attributesByDataset.has(datasetId)) {
            return attributesByDataset.get(datasetId);
        }

        try {
            const response = await fetch(`/api/Attribute/stakeholder/lookup?type=attributes&datasetId=${datasetId}`, {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const data = await response.json();
            const attributes = Array.isArray(data) ? data : (data.data || []);
            const formattedAttributes = attributes.map(attr => ({
                id: attr.AttributeID || attr.id,
                name: attr.AttributeName || attr.name || ''
            }));

            attributesByDataset.set(datasetId, formattedAttributes);
            return formattedAttributes;
        } catch (error) {
            console.error('Error loading attributes for dataset:', datasetId, error);
            return [];
        }
    }

    // Render editable table
    async function renderEditableRelationshipsTable() {
        const container = document.getElementById('relationshipsEditContainer');
        if (!container) return;

        // Ensure at least one empty row
        if (relationshipsData.length === 0) {
            relationshipsData.push(createEmptyRow());
        }

        const html = `
            <div class="relationships-section">
                <div class="relationships-header">
                    <div class="relationships-title">INBOUND RELATIONSHIPS</div>
                </div>
                <div class="relationships-table-wrapper">
                    <table class="relationships-table editable-table" id="relationshipsTable">
                        <thead>
                            <tr>
                                <th>Attribute</th>
                                <th>Source System</th>
                                <th>Interface</th>
                                <th>Related Data Set</th>
                                <th>Related Attributes</th>
                                <th>Type</th>
                                <th>Scope of Data</th>
                                <th>Sourcing Logic</th>
                                <th>Review Status</th>
                                <th>Action</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${relationshipsData.map((rel, index) => renderEditableRow(rel, index)).join('')}
                        </tbody>
                    </table>
                </div>
            </div>
        `;

        container.innerHTML = html;
        attachEventListeners();
        
        // Load attributes for existing relationships with targetDatasetId
        // Use setTimeout to ensure DOM is fully rendered
        setTimeout(async () => {
            await loadAttributesForExistingRelationships();
        }, 10);
    }

    // Load attributes for existing relationships
    async function loadAttributesForExistingRelationships() {
        for (let i = 0; i < relationshipsData.length; i++) {
            const row = relationshipsData[i];
            
            // For inbound relationships, if we have targetDatasetId, fetch its system and set it
            if (row.targetDatasetId && !row.systemId) {
                try {
                    const datasetResponse = await fetch(`/api/dataset/${row.targetDatasetId}`, {
                        method: 'GET',
                        credentials: 'include',
                        headers: { 'Content-Type': 'application/json' }
                    });
                    if (datasetResponse.ok) {
                        const datasetData = await datasetResponse.json();
                        row.systemId = datasetData.systemId || datasetData.MasterSource;
                    }
                } catch (error) {
                    console.error(`Error fetching system for target dataset ${row.targetDatasetId}:`, error);
                }
            }
            
            // Set the source system dropdown if we have a systemId
            if (row.systemId) {
                const systemSelect = document.getElementById(`system-${i}`);
                if (systemSelect) {
                    systemSelect.value = row.systemId;
                }
            }
            
            // Load system-specific data if system ID exists
            if (row.systemId) {
                try {
                    const systemData = await loadSystemData(row.systemId);
                    
                    // Update interface dropdown
                    const interfaceSelect = document.getElementById(`interface-${i}`);
                    if (interfaceSelect) {
                        let optionsHtml = '<option value="">Select...</option>';
                        optionsHtml += systemData.interfaces.map(iface => {
                            const selected = iface.id == row.Relation_Method ? 'selected' : '';
                            const label = iface.refNumber ? `${iface.refNumber} - ${iface.name}` : iface.name;
                            return `<option value="${iface.id}" ${selected}>${escapeHtml(label)}</option>`;
                        }).join('');
                        interfaceSelect.innerHTML = optionsHtml;
                    }
                    
                    // Update dataset dropdown
                    const datasetSelect = document.getElementById(`dataset-${i}`);
                    if (datasetSelect) {
                        let optionsHtml = '<option value="">Select...</option>';
                        optionsHtml += systemData.datasets.map(ds => {
                            const selected = ds.id == row.targetDatasetId ? 'selected' : '';
                            const label = ds.refNumber ? `${ds.refNumber}: ${ds.name}` : ds.name;
                            return `<option value="${ds.id}" ${selected}>${escapeHtml(label)}</option>`;
                        }).join('');
                        datasetSelect.innerHTML = optionsHtml;
                    }
                } catch (error) {
                    console.error(`Error loading system data for row ${i}:`, error);
                }
            }
            
            // Load related attributes if target dataset exists
            if (row.targetDatasetId && row.Target_AttributeID) {
                try {
                    const attributes = await loadAttributesForDataset(row.targetDatasetId);
                    const selectId = `relatedAttribute-${i}`;
                    const select = document.getElementById(selectId);
                    if (select) {
                        let optionsHtml = '<option value="">Select...</option>';
                        optionsHtml += attributes.map(attr => {
                            const selected = attr.id == row.Target_AttributeID ? 'selected' : '';
                            return `<option value="${attr.id}" ${selected}>${escapeHtml(attr.name)}</option>`;
                        }).join('');
                        select.innerHTML = optionsHtml;
                    }
                } catch (error) {
                    console.error(`Error loading attributes for row ${i}:`, error);
                }
            }
        }
    }

    // Create empty row
    function createEmptyRow() {
        return {
            ID: null,
            Source_AttributeID: null,
            Target_AttributeID: null,
            Relation_Type: null,
            Relation_Scope: null,
            Relation_Method: null,
            Sourcing_Logic: null,
            Review_Status: null,
            _isNew: true
        };
    }

    // Render editable row
    function renderEditableRow(rel, index) {
        const isFirst = index === 0;
        // For inbound relationships, don't use default system - it should come from target dataset
        // Only use systemId if it exists, otherwise leave empty (will be set when dataset is selected or loaded)
        const selectedSystemId = rel.systemId || null;
        
        return `
            <tr data-index="${index}" data-id="${rel.ID || ''}" data-system-id="${selectedSystemId || ''}">
                <td>
                    ${renderDropdown('attribute', index, rel.Source_AttributeID, cascadingData.attributes, 'sourceAttributeName')}
                </td>
                <td>
                    ${renderDropdown('system', index, selectedSystemId, cascadingData.systems, null, false)}
                </td>
                <td>
                    ${renderDropdown('interface', index, rel.Relation_Method, cascadingData.interfaces, 'interfaceName')}
                </td>
                <td>
                    ${renderDropdown('dataset', index, rel.targetDatasetId, cascadingData.datasets, 'targetDatasetName')}
                </td>
                <td>
                    ${renderDropdown('relatedAttribute', index, rel.Target_AttributeID, [], 'targetAttributeName')}
                </td>
                <td>
                    ${renderDropdown('type', index, rel.Relation_Type, lookupData.relationTypes, 'relationType')}
                </td>
                <td>
                    ${renderDropdown('scope', index, rel.Relation_Scope, lookupData.relationScopes, 'relationScope')}
                </td>
                <td>
                    <textarea 
                        id="sourcingLogic-${index}" 
                        class="form-control relationship-textarea" 
                        data-type="sourcingLogic" 
                        data-index="${index}"
                        rows="2"
                        placeholder="Sourcing Logic">${escapeHtml(rel.Sourcing_Logic || rel.sourcingLogic || '')}</textarea>
                </td>
                <td>
                    <input 
                        type="text" 
                        id="reviewStatus-${index}" 
                        class="form-control relationship-input" 
                        data-type="reviewStatus" 
                        data-index="${index}"
                        placeholder="Review Status"
                        value="${escapeHtml(rel.Review_Status || rel.reviewStatus || '')}">
                </td>
                <td>
                    <div class="relationship-actions">
                        <button type="button" class="btn-icon add-row" data-index="${index}" title="Add Row">
                            <i class="fas fa-plus"></i>
                        </button>
                        ${!isFirst ? `
                            <button type="button" class="btn-icon danger delete-row" data-index="${index}" title="Delete Row">
                                <i class="fas fa-minus"></i>
                            </button>
                        ` : `
                            <button type="button" class="btn-icon danger clear-row" data-index="${index}" title="Clear Row">
                                <i class="fas fa-minus"></i>
                            </button>
                        `}
                    </div>
                </td>
            </tr>
        `;
    }

    // Render dropdown
    function renderDropdown(type, rowIndex, selectedValue, options, displayField, isReadonly = false) {
        const selectId = `${type}-${rowIndex}`;
        const readonlyAttr = isReadonly ? 'disabled' : '';
        
        let optionsHtml = '<option value="">Select...</option>';
        
        if (options && options.length > 0) {
            optionsHtml += options.map(opt => {
                const value = opt.id || opt.ID;
                let label = opt.name || opt.Name || opt.PrimaryName || '';
                
                // Add ref number if available
                if (type === 'dataset' && opt.refNumber) {
                    label = `${opt.refNumber}: ${label}`;
                } else if (type === 'interface' && opt.refNumber) {
                    label = `${opt.refNumber} - ${label}`;
                }
                
                const selected = value == selectedValue ? 'selected' : '';
                return `<option value="${value}" ${selected}>${escapeHtml(label)}</option>`;
            }).join('');
        }

        // For related attributes, we need to populate based on selected dataset
        if (type === 'relatedAttribute') {
            const row = relationshipsData[rowIndex];
            if (row && row.targetDatasetId) {
                // This will be populated async
                populateRelatedAttributesDropdown(rowIndex, row.targetDatasetId, selectedValue);
            }
        }

        return `
            <select id="${selectId}" 
                    class="form-control relationship-dropdown" 
                    data-type="${type}" 
                    data-index="${rowIndex}"
                    ${readonlyAttr}>
                ${optionsHtml}
            </select>
        `;
    }

    // Populate related attributes dropdown asynchronously
    async function populateRelatedAttributesDropdown(rowIndex, datasetId, selectedValue) {
        const selectId = `relatedAttribute-${rowIndex}`;
        const select = document.getElementById(selectId);
        if (!select) return;

        try {
            const attributes = await loadAttributesForDataset(datasetId);
            
            let optionsHtml = '<option value="">Select...</option>';
            optionsHtml += attributes.map(attr => {
                const selected = attr.id == selectedValue ? 'selected' : '';
                return `<option value="${attr.id}" ${selected}>${escapeHtml(attr.name)}</option>`;
            }).join('');
            
            select.innerHTML = optionsHtml;
        } catch (error) {
            console.error('Error populating related attributes:', error);
        }
    }

    // Attach event listeners
    function attachEventListeners() {
        // Dropdown change handlers
        document.querySelectorAll('.relationship-dropdown').forEach(dropdown => {
            dropdown.addEventListener('change', handleDropdownChange);
        });

        // Text input and textarea change handlers
        document.querySelectorAll('.relationship-input, .relationship-textarea').forEach(input => {
            input.addEventListener('input', handleInputChange);
        });

        // Add row buttons
        document.querySelectorAll('.add-row').forEach(btn => {
            btn.addEventListener('click', handleAddRow);
        });

        // Delete row buttons
        document.querySelectorAll('.delete-row').forEach(btn => {
            btn.addEventListener('click', handleDeleteRow);
        });

        // Clear row buttons (for first row)
        document.querySelectorAll('.clear-row').forEach(btn => {
            btn.addEventListener('click', handleClearRow);
        });
    }

    // Handle input/textarea change
    function handleInputChange(event) {
        const input = event.target;
        const type = input.dataset.type;
        const rowIndex = parseInt(input.dataset.index);
        const value = input.value.trim();

        console.log(`Input changed: ${type} at row ${rowIndex} to value ${value}`);

        // Update data
        if (!relationshipsData[rowIndex]) {
            relationshipsData[rowIndex] = createEmptyRow();
        }

        const row = relationshipsData[rowIndex];

        switch (type) {
            case 'sourcingLogic':
                row.Sourcing_Logic = value || null;
                break;
            case 'reviewStatus':
                row.Review_Status = value || null;
                break;
        }

        console.log('Updated row:', row);
    }

    // Handle dropdown change
    async function handleDropdownChange(event) {
        const dropdown = event.target;
        const type = dropdown.dataset.type;
        const rowIndex = parseInt(dropdown.dataset.index);
        const value = dropdown.value;

        console.log(`Dropdown changed: ${type} at row ${rowIndex} to value ${value}`);

        // Update data
        if (!relationshipsData[rowIndex]) {
            relationshipsData[rowIndex] = createEmptyRow();
        }

        const row = relationshipsData[rowIndex];

        // Update based on type
        switch (type) {
            case 'attribute':
                row.Source_AttributeID = value ? parseInt(value) : null;
                break;
            case 'system':
                const systemId = value ? parseInt(value) : null;
                row.systemId = systemId;
                
                // Clear dependent fields
                row.Relation_Method = null;
                row.targetDatasetId = null;
                row.Target_AttributeID = null;
                
                if (systemId) {
                    // Load interfaces and datasets for this system
                    const systemData = await loadSystemData(systemId);
                    
                    // Update interface dropdown
                    const interfaceSelect = document.getElementById(`interface-${rowIndex}`);
                    if (interfaceSelect) {
                        let optionsHtml = '<option value="">Select...</option>';
                        optionsHtml += systemData.interfaces.map(iface => {
                            const label = iface.refNumber ? `${iface.refNumber} - ${iface.name}` : iface.name;
                            return `<option value="${iface.id}">${escapeHtml(label)}</option>`;
                        }).join('');
                        interfaceSelect.innerHTML = optionsHtml;
                    }
                    
                    // Update dataset dropdown
                    const datasetSelect = document.getElementById(`dataset-${rowIndex}`);
                    if (datasetSelect) {
                        let optionsHtml = '<option value="">Select...</option>';
                        optionsHtml += systemData.datasets.map(ds => {
                            const label = ds.refNumber ? `${ds.refNumber}: ${ds.name}` : ds.name;
                            return `<option value="${ds.id}">${escapeHtml(label)}</option>`;
                        }).join('');
                        datasetSelect.innerHTML = optionsHtml;
                    }
                    
                    // Clear related attributes dropdown
                    const relatedAttrSelect = document.getElementById(`relatedAttribute-${rowIndex}`);
                    if (relatedAttrSelect) {
                        relatedAttrSelect.innerHTML = '<option value="">Select...</option>';
                    }
                } else {
                    // Clear all dependent dropdowns
                    const interfaceSelect = document.getElementById(`interface-${rowIndex}`);
                    if (interfaceSelect) interfaceSelect.innerHTML = '<option value="">Select...</option>';
                    
                    const datasetSelect = document.getElementById(`dataset-${rowIndex}`);
                    if (datasetSelect) datasetSelect.innerHTML = '<option value="">Select...</option>';
                    
                    const relatedAttrSelect = document.getElementById(`relatedAttribute-${rowIndex}`);
                    if (relatedAttrSelect) relatedAttrSelect.innerHTML = '<option value="">Select...</option>';
                }
                break;
            case 'interface':
                row.Relation_Method = value ? parseInt(value) : null;
                break;
            case 'dataset':
                row.targetDatasetId = value ? parseInt(value) : null;
                // Clear and reload related attributes
                row.Target_AttributeID = null;
                if (value) {
                    // Fetch dataset to get its MasterSource (systemId) and update source system
                    try {
                        const datasetResponse = await fetch(`/api/dataset/${value}`, {
                            method: 'GET',
                            credentials: 'include',
                            headers: { 'Content-Type': 'application/json' }
                        });
                        if (datasetResponse.ok) {
                            const datasetData = await datasetResponse.json();
                            const datasetSystemId = datasetData.systemId || datasetData.MasterSource;
                            
                            if (datasetSystemId) {
                                // Update the row's systemId
                                row.systemId = datasetSystemId;
                                
                                // Update the source system dropdown
                                const systemSelect = document.getElementById(`system-${rowIndex}`);
                                if (systemSelect) {
                                    systemSelect.value = datasetSystemId;
                                    
                                    // Load interfaces and datasets for this system
                                    const systemData = await loadSystemData(datasetSystemId);
                                    
                                    // Update interface dropdown — preserve existing selection if still valid
                                    const interfaceSelect = document.getElementById(`interface-${rowIndex}`);
                                    if (interfaceSelect) {
                                        const previousInterfaceId = row.Relation_Method;
                                        let optionsHtml = '<option value="">Select...</option>';
                                        optionsHtml += systemData.interfaces.map(iface => {
                                            const label = iface.refNumber ? `${iface.refNumber} - ${iface.name}` : iface.name;
                                            const selected = previousInterfaceId && iface.id == previousInterfaceId ? 'selected' : '';
                                            return `<option value="${iface.id}" ${selected}>${escapeHtml(label)}</option>`;
                                        }).join('');
                                        interfaceSelect.innerHTML = optionsHtml;
                                    }
                                    
                                    // Update dataset dropdown (should already have the selected dataset)
                                    const datasetSelect = document.getElementById(`dataset-${rowIndex}`);
                                    if (datasetSelect) {
                                        let optionsHtml = '<option value="">Select...</option>';
                                        optionsHtml += systemData.datasets.map(ds => {
                                            const selected = ds.id == value ? 'selected' : '';
                                            const label = ds.refNumber ? `${ds.refNumber}: ${ds.name}` : ds.name;
                                            return `<option value="${ds.id}" ${selected}>${escapeHtml(label)}</option>`;
                                        }).join('');
                                        datasetSelect.innerHTML = optionsHtml;
                                    }
                                }
                            }
                        }
                    } catch (error) {
                        console.error('Error fetching dataset system:', error);
                    }
                    
                    await populateRelatedAttributesDropdown(rowIndex, parseInt(value), null);
                } else {
                    const relatedAttrSelect = document.getElementById(`relatedAttribute-${rowIndex}`);
                    if (relatedAttrSelect) {
                        relatedAttrSelect.innerHTML = '<option value="">Select...</option>';
                    }
                }
                break;
            case 'relatedAttribute':
                row.Target_AttributeID = value ? parseInt(value) : null;
                break;
            case 'type':
                row.Relation_Type = value ? parseInt(value) : null;
                break;
            case 'scope':
                row.Relation_Scope = value ? parseInt(value) : null;
                break;
        }

        console.log('Updated row:', row);
    }

    // Handle add row
    function handleAddRow(event) {
        const index = parseInt(event.currentTarget.dataset.index);
        console.log('Adding row after index:', index);
        
        relationshipsData.splice(index + 1, 0, createEmptyRow());
        renderEditableRelationshipsTable();
    }

    // Handle delete row
    function handleDeleteRow(event) {
        const index = parseInt(event.currentTarget.dataset.index);
        console.log('Deleting row at index:', index);
        
        if (relationshipsData.length > 1) {
            relationshipsData.splice(index, 1);
            renderEditableRelationshipsTable();
        }
    }

    // Handle clear row (first row only)
    function handleClearRow(event) {
        const index = parseInt(event.currentTarget.dataset.index);
        console.log('Clearing row at index:', index);
        
        relationshipsData[index] = createEmptyRow();
        renderEditableRelationshipsTable();
    }

    // Save relationships
    window.saveDatasetRelationships = async function() {
        try {
            console.log('[Dataset Relationships] Saving relationships...');
            console.log('[Dataset Relationships] Current datasetId:', currentDatasetId);
            console.log('[Dataset Relationships] Current data:', relationshipsData);
            console.log('[Dataset Relationships] Original data:', originalData);

            const operations = [];

            // Track unique combinations to detect duplicates
            const seenRelationships = new Map(); // key: "sourceAttributeId_targetAttributeId", value: row index
            
            // Determine operations for each row
            for (let i = 0; i < relationshipsData.length; i++) {
                const row = relationshipsData[i];
                
                // Skip empty rows (all fields null/empty)
                if (!row.Source_AttributeID && !row.Target_AttributeID && !row.Relation_Type && 
                    !row.Relation_Scope && !row.Relation_Method) {
                    continue;
                }

                // Validate required fields (all required except Interface and Scope of Data)
                if (!row.Source_AttributeID || !row.Target_AttributeID || !row.Relation_Type || !row.targetDatasetId || !row.systemId) {
                    const errorMsg = 'Please fill all required fields (Attribute, Source System, Related Data Set, Related Attributes, and Type) for each relationship. Interface and Scope of Data are optional.';
                    showErrorMessage(errorMsg);
                    // Don't auto-remove error message - keep it visible
                    throw new Error(errorMsg);
                }

                // Validate: If same dataset, the source and target attributes must be different
                if (row.targetDatasetId && row.targetDatasetId === currentDatasetId) {
                    if (row.Source_AttributeID === row.Target_AttributeID) {
                        const errorMsg = 'Please choose another related data set or different related attribute';
                        showErrorMessage(errorMsg);
                        throw new Error(errorMsg);
                    }
                }

                // Check for duplicate relationships (same Source_AttributeID and Target_AttributeID combination)
                const relationshipKey = `${row.Source_AttributeID}_${row.Target_AttributeID}`;
                if (seenRelationships.has(relationshipKey)) {
                    const duplicateIndex = seenRelationships.get(relationshipKey);
                    const errorMsg = `Duplicate relationship detected: The same attribute combination (Source Attribute and Related Attribute) already exists in row ${duplicateIndex + 1}. Please remove the duplicate or choose different attributes.`;
                    showErrorMessage(errorMsg);
                    throw new Error(errorMsg);
                }
                seenRelationships.set(relationshipKey, i);
                
                // Check against existing relationships (excluding the current row if it's an update)
                for (const original of originalData) {
                    // Skip if this is an update of the same relationship
                    if (row.ID && original.ID && row.ID === original.ID) {
                        continue;
                    }
                    
                    // Check if this combination already exists in the database
                    if (original.Source_AttributeID === row.Source_AttributeID && 
                        original.Target_AttributeID === row.Target_AttributeID) {
                        const errorMsg = 'This relationship already exists. Please choose different attributes.';
                        showErrorMessage(errorMsg);
                        throw new Error(errorMsg);
                    }
                }

                const operation = {
                    data: {
                        id: row.ID,
                        sourceAttributeId: row.Source_AttributeID,
                        targetAttributeId: row.Target_AttributeID,
                        relationTypeId: row.Relation_Type,
                        relationScopeId: row.Relation_Scope,
                        interfaceId: row.Relation_Method,
                        sourcingLogic: row.Sourcing_Logic || null,
                        reviewStatus: row.Review_Status || null,
                        userId: window.currentUserId || 1 // Get from session
                    }
                };

                if (row.ID) {
                    // Existing row - check if modified
                    const original = originalData.find(o => o.ID === row.ID);
                    if (original) {
                        if (isRowModified(row, original)) {
                            operation.operation = 'UPDATE';
                            operations.push(operation);
                        }
                    }
                } else {
                    // New row
                    operation.operation = 'INSERT';
                    operations.push(operation);
                }
            }

            // Check for deleted rows
            for (const original of originalData) {
                const exists = relationshipsData.find(r => r.ID === original.ID);
                if (!exists) {
                    operations.push({
                        operation: 'DELETE',
                        data: { id: original.ID }
                    });
                }
            }

            console.log('[Dataset Relationships] Operations to perform:', operations);
            console.log('[Dataset Relationships] Request body will include datasetId:', currentDatasetId);

            if (operations.length === 0) {
                console.log('[Dataset Relationships] No changes to save');
                return { success: true, message: 'No changes to save', noChanges: true };
            }

            if (!currentDatasetId) {
                console.error('[Dataset Relationships] ERROR: currentDatasetId is null or undefined!');
                throw new Error('Dataset ID is required to save relationships');
            }

            // Send to server with datasetId for pending changes tracking
            const requestBody = { 
                operations,
                datasetId: currentDatasetId // Include datasetId for pending changes tracking
            };
            console.log('[Dataset Relationships] Sending request to /api/dataset-relationships/save with body:', JSON.stringify(requestBody));
            
            const response = await fetch('/api/dataset-relationships/save', {
                method: 'POST',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(requestBody)
            });

            if (!response.ok) {
                const error = await response.json();
                console.error('[Dataset Relationships] Save failed with status:', response.status, 'Error:', error);
                throw new Error(error.message || 'Failed to save relationships');
            }

            const result = await response.json();
            console.log('[Dataset Relationships] Save result:', result);

            // Reload data with current view mode
            await loadExistingRelationships(currentView);
            renderEditableRelationshipsTable();

            return result;
        } catch (error) {
            console.error('Error saving relationships:', error);
            throw error;
        }
    };

    // Check if row is modified
    function isRowModified(current, original) {
        return current.Source_AttributeID !== original.Source_AttributeID ||
               current.Target_AttributeID !== original.Target_AttributeID ||
               current.Relation_Type !== original.Relation_Type ||
               current.Relation_Scope !== original.Relation_Scope ||
               current.Relation_Method !== original.Relation_Method ||
               (current.Sourcing_Logic || '') !== (original.Sourcing_Logic || '') ||
               (current.Review_Status || '') !== (original.Review_Status || '');
    }

    // Show error message (persistent - won't auto-remove)
    function showErrorMessage(message) {
        const container = document.getElementById('relationshipsEditContainer');
        if (!container) return;
        
        // Remove existing messages
        const existing = container.querySelectorAll('.alert');
        existing.forEach(msg => msg.remove());
        
        const messageDiv = document.createElement('div');
        messageDiv.className = 'alert alert-danger';
        messageDiv.style.cssText = 'padding: 1rem; background: #fee2e2; color: #dc2626; border: 1px solid #fecaca; border-radius: 6px; margin-bottom: 1rem; display: flex; align-items: center; gap: 0.5rem;';
        messageDiv.innerHTML = `
            <i class="fas fa-exclamation-circle"></i>
            <span style="flex: 1;">${escapeHtml(message)}</span>
            <button onclick="this.parentElement.remove()" style="background: none; border: none; color: #dc2626; font-size: 1.2rem; cursor: pointer; padding: 0 0.5rem; opacity: 0.7; hover: opacity: 1;">&times;</button>
        `;
        container.insertBefore(messageDiv, container.firstChild);
        
        // Don't auto-remove - user must close manually or fix the error
    }

    // Escape HTML helper
    function escapeHtml(text) {
        if (text == null) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

})();

