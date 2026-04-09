// Business Area Impact Edit JavaScript - Implementation for Glossary, System, and Process sub-tabs
console.log('=== BUSINESS AREA IMPACT EDIT SCRIPT LOADING ===');

(function() {
    console.log('=== BUSINESS AREA IMPACT EDIT SCRIPT LOADED ===');

    function resolveImpactSegmentId() {
        const normalizeSegmentId = (value) => {
            const parsed = parseInt(value, 10);
            return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
        };

        const explicit = normalizeSegmentId(window.currentImpactSegmentId);
        if (explicit) return explicit;

        const globalSegmentField = window.segmentField;
        if (globalSegmentField && typeof globalSegmentField.getValue === 'function') {
            const fromGlobalField = normalizeSegmentId(globalSegmentField.getValue());
            if (fromGlobalField) return fromGlobalField;
        }

        const segmentContainer = document.getElementById('segmentFieldContainer');
        const segmentFieldInstance = segmentContainer?._segmentFieldInstance;
        if (segmentFieldInstance && typeof segmentFieldInstance.getValue === 'function') {
            const fromContainerField = normalizeSegmentId(segmentFieldInstance.getValue());
            if (fromContainerField) return fromContainerField;
        }

        const domSelectors = [
            '#baSegment',
            '[name="segmentId"]',
            '[name="segment_id"]'
        ];
        for (const selector of domSelectors) {
            const element = document.querySelector(selector);
            const fromDom = normalizeSegmentId(element?.value);
            if (fromDom) return fromDom;
        }

        return null;
    }

    function withImpactSegment(url) {
        const segmentId = resolveImpactSegmentId();
        let result = url;
        if (segmentId) {
            window.currentImpactSegmentId = segmentId;
            const sep1 = result.includes('?') ? '&' : '?';
            result = `${result}${sep1}segmentId=${encodeURIComponent(segmentId)}`;
        }
        const oid = parseInt(currentBusinessAreaId, 10);
        if (Number.isInteger(oid) && oid > 0) {
            const sep2 = result.includes('?') ? '&' : '?';
            result = `${result}${sep2}sourceObjectId=${oid}&sourceObjectType=BusinessArea`;
        }
        return result;
    }
    
    // Global variables
    let currentBusinessAreaId = null;
    let glossaryRelationships = [];
    let systemRelationships = [];
    let processRelationships = [];
    
    let originalGlossaryData = [];
    let originalSystemData = [];
    let originalProcessData = [];
    
    let glossaryRelationTypes = [];
    let systemRelationTypes = [];
    let processRelationTypes = [];
    
    let glossaries = [];
    let systems = [];
    let processes = [];

    // Initialize Impact tab edit functionality
    function initImpactEdit(businessAreaId) {
        console.log('=== INIT IMPACT EDIT CALLED ===');
        console.log('Business Area ID:', businessAreaId);
        currentBusinessAreaId = businessAreaId;
        loadImpactData();
    }

    // Load all Impact data
    async function loadImpactData() {
        try {
            console.log('=== LOADING IMPACT DATA START ===');
            
            // Load dropdown data first
            await Promise.all([
                loadGlossaryRelationTypes(),
                loadSystemRelationTypes(),
                loadProcessRelationTypes(),
                loadGlossaries(),
                loadSystems(),
                loadProcesses()
            ]);

            // Load relationship data
            await Promise.all([
                loadGlossaryRelationships(),
                loadSystemRelationships(),
                loadProcessRelationships()
            ]);

            // Setup sub-tabs
            setupImpactSubTabs();
            console.log('=== LOADING IMPACT DATA END ===');

        } catch (error) {
            console.error('Error loading Impact data:', error);
        }
    }

    // Setup Impact sub-tabs
    function setupImpactSubTabs() {
        const subTabs = document.querySelectorAll('#impact .sub-tab');
        
        subTabs.forEach(function(subTab) {
            subTab.addEventListener('click', function(e) {
                e.preventDefault();
                
                // Remove active class from all sub-tabs
                subTabs.forEach(function(t) { t.classList.remove('active'); });
                this.classList.add('active');
                
                const subTabName = this.getAttribute('data-sub-tab');
                
                // Hide all sub-tab contents
                const subTabContents = document.querySelectorAll('#impact .sub-tab-content');
                subTabContents.forEach(function(content) {
                    content.classList.remove('active');
                    content.style.display = 'none';
                });
                
                // Show selected sub-tab content
                let targetSubTab;
                if (subTabName === 'glossary') {
                    targetSubTab = document.getElementById('impactGlossaryContent');
                } else if (subTabName === 'system') {
                    targetSubTab = document.getElementById('impactSystemContent');
                } else if (subTabName === 'process') {
                    targetSubTab = document.getElementById('impactProcessContent');
                }
                
                if (targetSubTab) {
                    targetSubTab.classList.add('active');
                    targetSubTab.style.display = 'block';
                }
            });
        });
    }

    // ===== GLOSSARY RELATIONSHIPS =====
    
    async function loadGlossaryRelationships() {
        try {
            const response = await fetch(`/api/businessarea-impact/${currentBusinessAreaId}/glossaries`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            glossaryRelationships = Array.isArray(data) ? data : [];
            renderGlossaryTable();
            // Update original data AFTER rendering (rendering may modify glossaryRelationships with placeholder rows)
            originalGlossaryData = JSON.parse(JSON.stringify(glossaryRelationships));

        } catch (error) {
            console.error('Error loading glossary relationships:', error);
            glossaryRelationships = [];
            renderGlossaryTable();
            // Update original data AFTER rendering
            originalGlossaryData = JSON.parse(JSON.stringify(glossaryRelationships));
        }
    }

    async function loadGlossaryRelationTypes() {
        try {
            const response = await fetch('/api/businessarea-impact/glossary-relation-types', {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            glossaryRelationTypes = Array.isArray(data) ? data : [];

        } catch (error) {
            console.error('Error loading glossary relation types:', error);
            glossaryRelationTypes = [];
        }
    }

    async function loadGlossaries() {
        try {
            const response = await fetch(withImpactSegment('/api/glossary/list'), {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            glossaries = Array.isArray(data) ? data : [];

        } catch (error) {
            console.error('Error loading glossaries:', error);
            glossaries = [];
        }
    }

    function renderGlossaryTable() {
        const tbody = document.querySelector('#glossaryImpactTableBody');
        const footer = document.querySelector('#glossaryImpactFooter');
        
        if (!tbody) return;
        
        if (glossaryRelationships.length === 0) {
            glossaryRelationships.push({
                id: 'new-empty',
                relationType: null,
                glossaryId: null,
                glossaryName: null,
                glossaryType: null,
                glossaryTypeName: null,
                glossaryOwnerName: null
            });
        }
        
        let html = '';
        glossaryRelationships.forEach((relationship, index) => {
            const rowId = relationship.id || 'new-' + index;
            const isNewRow = !relationship.id || String(relationship.id).startsWith('new-');
            const ownerDisplay = isNewRow && !relationship.glossaryId ? '' : (relationship.glossaryOwnerName || 'No owner');
            
            html += `
                <tr data-id="${rowId}">
                    <td>
                        <select class="form-control" data-field="relationType">
                            <option value="">Select relationship type</option>
                            ${glossaryRelationTypes.map(rt => 
                                `<option value="${rt.id || rt.ID}" ${relationship.relationType == (rt.id || rt.ID) ? 'selected' : ''}>${rt.primaryname || rt.primaryName || rt.PrimaryName || 'Unnamed Type'}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td>
                        <select class="form-control" data-field="glossaryId" onchange="updateGlossaryOwner(this)">
                            <option value="">Select glossary</option>
                            ${glossaries.map(g => {
                                const name = g.PrimaryName || g.primaryName || g.name || g.Name || 'Unnamed Glossary';
                                const id = g.ID || g.id;
                                return `<option value="${id}" ${relationship.glossaryId == id ? 'selected' : ''}>${name}</option>`;
                            }).join('')}
                        </select>
                    </td>
                    <td><span class="text-muted">${relationship.glossaryTypeName || relationship.glossaryType || ''}</span></td>
                    <td><span class="text-muted">${ownerDisplay}</span></td>
                    <td>
                        <div class="action-buttons">
                            <button type="button" class="btn btn-sm btn-success" onclick="addGlossaryRow()" title="${window.I18n?.t('impactEdit.buttons.addRow') || 'Add row'}">
                                <i class="fas fa-plus"></i>
                            </button>
                            <button type="button" class="btn btn-sm btn-danger" onclick="deleteGlossaryRow('${rowId}')" title="${window.I18n?.t('impactEdit.buttons.deleteRow') || 'Delete row'}">
                                <i class="fas fa-minus"></i>
                            </button>
                        </div>
                    </td>
                </tr>
            `;
        });
        
        tbody.innerHTML = html;
        if (footer) footer.textContent = `${glossaryRelationships.length} record${glossaryRelationships.length !== 1 ? 's' : ''}`;
    }

    function addGlossaryRow() {
        saveCurrentGlossaryFormData();
        const newRelationship = {
            id: 'new-' + Date.now(),
            relationType: null,
            glossaryId: null,
            glossaryName: null,
            glossaryType: null,
            glossaryTypeName: null,
            glossaryOwnerName: null
        };
        glossaryRelationships.push(newRelationship);
        renderGlossaryTable();
    }

    function deleteGlossaryRow(id) {
        // Save current form data before re-rendering
        saveCurrentGlossaryFormData();
        
        if (glossaryRelationships.length <= 1) {
            // Clear the first row instead of deleting it
            glossaryRelationships[0] = {
                id: 'new-' + Date.now(),
                relationType: null,
                glossaryId: null,
                glossaryName: null,
                glossaryType: null,
                glossaryTypeName: null,
                glossaryOwnerName: null
            };
        } else {
            glossaryRelationships = glossaryRelationships.filter(rel => String(rel.id) !== String(id));
        }
        renderGlossaryTable();
    }

    function saveCurrentGlossaryFormData() {
        const tbody = document.querySelector('#glossaryImpactTableBody');
        if (!tbody) return;
        
        const rows = tbody.querySelectorAll('tr');
        rows.forEach((row, index) => {
            if (glossaryRelationships[index]) {
                const relationTypeSelect = row.querySelector('[data-field="relationType"]');
                const glossaryIdSelect = row.querySelector('[data-field="glossaryId"]');
                
                if (relationTypeSelect) {
                    glossaryRelationships[index].relationType = relationTypeSelect.value || null;
                }
                if (glossaryIdSelect) {
                    const previousGlossaryId = glossaryRelationships[index].glossaryId;
                    glossaryRelationships[index].glossaryId = glossaryIdSelect.value || null;
                    
                    // Preserve owner name if glossaryId hasn't changed
                    if (previousGlossaryId == glossaryRelationships[index].glossaryId) {
                        // Keep existing glossaryOwnerName
                    } else {
                        // Glossary changed, clear owner (will be fetched by updateGlossaryOwner)
                        glossaryRelationships[index].glossaryOwnerName = null;
                    }
                }
            }
        });
    }

    async function updateGlossaryOwner(selectElement) {
        const glossaryId = selectElement.value;
        const row = selectElement.closest('tr');
        const ownerCell = row.querySelector('td:nth-child(4)');
        const typeCell = row.querySelector('td:nth-child(3)');
        
        if (glossaryId) {
            // Update owner
            if (ownerCell) {
                await updateGlossaryOwnerDisplay(ownerCell, glossaryId, row);
            }
            
            // Update type - fetch from backend to get the type name
            if (typeCell) {
                try {
                    const response = await fetch(`/api/glossary/${glossaryId}`, {
                        method: 'GET',
                        credentials: 'include',
                        headers: { 'Content-Type': 'application/json' }
                    });
                    
                    if (response.ok) {
                        const glossaryData = await response.json();
                        const typeName = glossaryData.typeName || glossaryData.type_name || glossaryData.Type || glossaryData.type || '';
                        typeCell.innerHTML = `<span class="text-muted">${typeName}</span>`;
                        
                        // Update relationship data
                        const rowIndex = Array.from(row.parentElement.children).indexOf(row);
                        if (glossaryRelationships[rowIndex]) {
                            glossaryRelationships[rowIndex].glossaryTypeName = typeName;
                            glossaryRelationships[rowIndex].glossaryType = typeName;
                        }
                    } else {
                        typeCell.innerHTML = `<span class="text-muted"></span>`;
                    }
                } catch (error) {
                    console.error('Error fetching glossary type:', error);
                    typeCell.innerHTML = `<span class="text-muted"></span>`;
                }
            }
        } else {
            // Clear both owner and type if no glossary selected
            if (ownerCell) ownerCell.innerHTML = '<span class="text-muted">No owner</span>';
            if (typeCell) typeCell.innerHTML = '<span class="text-muted"></span>';
        }
    }

    async function updateGlossaryOwnerDisplay(ownerCell, glossaryId, row) {
        try {
            const response = await fetch(`/api/businessarea-impact/glossary-owner/${glossaryId}`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            const ownerName = data.ownerName || data.glossaryOwnerName || 'No owner';
            ownerCell.innerHTML = `<span class="text-muted">${ownerName}</span>`;
            
            // Update the relationship data structure
            if (row) {
                const rowIndex = Array.from(row.parentElement.children).indexOf(row);
                if (glossaryRelationships[rowIndex]) {
                    glossaryRelationships[rowIndex].glossaryOwnerName = data.ownerName || data.glossaryOwnerName || null;
                }
            }
        } catch (error) {
            console.error('Error fetching glossary owner:', error);
            ownerCell.innerHTML = '<span class="text-muted">No owner</span>';
        }
    }

    async function saveGlossaryRelationships() {
        try {
            saveCurrentGlossaryFormData();
            
            const relationships = glossaryRelationships
                .filter(r => r.glossaryId && r.relationType)
                .map(r => ({
                    glossaryId: parseInt(r.glossaryId),
                    relationType: parseInt(r.relationType),
                    description: r.description || null
                }));

            const requestData = {
                businessAreaId: currentBusinessAreaId,
                relationships: relationships
            };

            const response = await fetch('/api/businessarea-impact/glossaries/save', {
                method: 'POST',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(requestData)
            });

            if (!response.ok) {
                let errorText = '';
                try {
                    errorText = await response.text();
                } catch (e) {
                    errorText = response.statusText;
                }
                
                // Handle authentication errors specifically
                if (response.status === 401) {
                    console.error('Authentication failed - session may have expired. Please refresh the page or log in again.');
                    throw new Error(`Authentication failed (HTTP ${response.status}). Your session may have expired. Please refresh the page or log in again.`);
                }
                
                throw new Error(`HTTP error! status: ${response.status}, message: ${errorText}`);
            }

            const result = await response.json();
            
            if (result.success) {
                // Reload data from server to verify save
                await loadGlossaryRelationships();
                // Update original data after reload to match server data structure (prevents false change detection)
                // Note: loadGlossaryRelationships already sets originalGlossaryData, but we ensure it matches current state
                originalGlossaryData = JSON.parse(JSON.stringify(glossaryRelationships));
                return true;
            } else {
                return { success: false, message: result.message || 'Failed to save glossary relationships' };
            }
        } catch (error) {
            console.error('Error saving glossary relationships:', error);
            return { success: false, message: 'Network error: ' + error.message };
        }
    }

    // ===== SYSTEM RELATIONSHIPS =====
    
    async function loadSystemRelationships() {
        try {
            const response = await fetch(`/api/businessarea-impact/${currentBusinessAreaId}/systems`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            systemRelationships = Array.isArray(data) ? data : [];
            renderSystemTable();
            // Update original data AFTER rendering (rendering may modify systemRelationships with placeholder rows)
            originalSystemData = JSON.parse(JSON.stringify(systemRelationships));

        } catch (error) {
            console.error('Error loading system relationships:', error);
            systemRelationships = [];
            renderSystemTable();
            // Update original data AFTER rendering
            originalSystemData = JSON.parse(JSON.stringify(systemRelationships));
        }
    }

    async function loadSystemRelationTypes() {
        try {
            const response = await fetch('/api/businessarea-impact/system-relation-types', {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            systemRelationTypes = Array.isArray(data) ? data : [];

        } catch (error) {
            console.error('Error loading system relation types:', error);
            systemRelationTypes = [];
        }
    }

    async function loadSystems() {
        try {
            const response = await fetch(withImpactSegment('/api/system/list'), {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            systems = Array.isArray(data) ? data : [];

        } catch (error) {
            console.error('Error loading systems:', error);
            systems = [];
        }
    }

    function renderSystemTable() {
        const tbody = document.querySelector('#systemImpactTableBody');
        const footer = document.querySelector('#systemImpactFooter');
        
        if (!tbody) return;
        
        if (systemRelationships.length === 0) {
            systemRelationships.push({
                id: 'new-empty',
                relationType: null,
                systemId: null,
                systemName: null,
                systemOwnerName: null
            });
        }
        
        let html = '';
        systemRelationships.forEach((relationship, index) => {
            const rowId = relationship.id || 'new-' + index;
            const isNewRow = !relationship.id || String(relationship.id).startsWith('new-');
            const ownerDisplay = isNewRow && !relationship.systemId ? '' : (relationship.systemOwnerName || 'No owner');
            
            html += `
                <tr data-id="${rowId}">
                    <td>
                        <select class="form-control" data-field="relationType">
                            <option value="">Select relationship type</option>
                            ${systemRelationTypes.map(rt => 
                                `<option value="${rt.id || rt.ID}" ${relationship.relationType == (rt.id || rt.ID) ? 'selected' : ''}>${rt.primaryname || rt.primaryName || rt.PrimaryName || 'Unnamed Type'}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td>
                        <select class="form-control" data-field="systemId" onchange="updateSystemOwner(this)">
                            <option value="">Select system</option>
                            ${systems.map(s => 
                                `<option value="${s.id || s.ID}" ${relationship.systemId == (s.id || s.ID) ? 'selected' : ''}>${s.name || s.Name || 'Unnamed System'}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td><span class="text-muted">${ownerDisplay}</span></td>
                    <td>
                        <div class="action-buttons">
                            <button type="button" class="btn btn-sm btn-success" onclick="addSystemRow()" title="${window.I18n?.t('impactEdit.buttons.addRow') || 'Add row'}">
                                <i class="fas fa-plus"></i>
                            </button>
                            <button type="button" class="btn btn-sm btn-danger" onclick="deleteSystemRow('${rowId}')" title="${window.I18n?.t('impactEdit.buttons.deleteRow') || 'Delete row'}">
                                <i class="fas fa-minus"></i>
                            </button>
                        </div>
                    </td>
                </tr>
            `;
        });
        
        tbody.innerHTML = html;
        if (footer) footer.textContent = `${systemRelationships.length} record${systemRelationships.length !== 1 ? 's' : ''}`;
    }

    function addSystemRow() {
        saveCurrentSystemFormData();
        const newRelationship = {
            id: 'new-' + Date.now(),
            relationType: null,
            systemId: null,
            systemName: null,
            systemOwnerName: null
        };
        systemRelationships.push(newRelationship);
        renderSystemTable();
    }

    function deleteSystemRow(id) {
        // Save current form data before re-rendering
        saveCurrentSystemFormData();
        
        if (systemRelationships.length <= 1) {
            // Clear the first row instead of deleting it
            systemRelationships[0] = {
                id: 'new-' + Date.now(),
                relationType: null,
                systemId: null,
                systemName: null,
                systemOwnerName: null
            };
        } else {
            systemRelationships = systemRelationships.filter(rel => String(rel.id) !== String(id));
        }
        renderSystemTable();
    }

    function saveCurrentSystemFormData() {
        const rows = document.querySelectorAll('#systemImpactTableBody tr');
        const savedData = [];
        
        rows.forEach((row, index) => {
            const existingRelationship = systemRelationships[index];
            const rowId = existingRelationship?.id || ('new-' + Date.now() + '-' + index);
            
            const relationTypeSelect = row.querySelector('select[data-field="relationType"]');
            const systemSelect = row.querySelector('select[data-field="systemId"]');
            
            let systemName = null;
            if (systemSelect && systemSelect.value) {
                const system = systems.find(s => (s.id || s.ID) == systemSelect.value);
                if (system) {
                    systemName = system.name || system.Name;
                }
            }
            
            const data = {
                id: rowId,
                relationType: relationTypeSelect ? parseInt(relationTypeSelect.value) : null,
                systemId: systemSelect ? parseInt(systemSelect.value) : null,
                systemName: systemName,
                systemOwnerName: existingRelationship?.systemOwnerName || existingRelationship?.ownerName || null
            };
            
            savedData.push(data);
        });
        
        systemRelationships = savedData.length > 0 ? savedData : [{ id: 'new-empty', relationType: null, systemId: null, systemName: null, systemOwnerName: null }];
    }

    async function updateSystemOwner(selectElement) {
        const systemId = selectElement.value;
        const row = selectElement.closest('tr');
        const ownerCell = row.querySelector('td:nth-child(3)');
        
        if (systemId) {
            await updateSystemOwnerDisplay(ownerCell, parseInt(systemId), row);
        } else {
            ownerCell.innerHTML = '<span class="text-muted"></span>';
        }
    }

    async function updateSystemOwnerDisplay(ownerCell, systemId, row) {
        try {
            const response = await fetch(`/api/businessarea-impact/system-owner/${systemId}`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            const ownerName = data.ownerName || data.systemOwnerName || 'No owner';
            ownerCell.innerHTML = `<span class="text-muted">${ownerName}</span>`;
            
            // Update the relationship data structure
            if (row) {
                const rowIndex = Array.from(row.parentElement.children).indexOf(row);
                if (systemRelationships[rowIndex]) {
                    systemRelationships[rowIndex].systemOwnerName = data.ownerName || data.systemOwnerName || null;
                }
            }

        } catch (error) {
            console.error('Error loading system owner:', error);
            ownerCell.innerHTML = '<span class="text-muted">Error loading owner</span>';
        }
    }

    async function saveSystemRelationships() {
        saveCurrentSystemFormData();
        
        const relationships = [];
        const rows = document.querySelectorAll('#systemImpactTableBody tr');
        
        rows.forEach(row => {
            const relationTypeSelect = row.querySelector('select[data-field="relationType"]');
            const systemSelect = row.querySelector('select[data-field="systemId"]');
            
            if (relationTypeSelect?.value && systemSelect?.value) {
                relationships.push({
                    relationType: parseInt(relationTypeSelect.value),
                    systemId: parseInt(systemSelect.value)
                });
            }
        });
        
        const requestData = {
            businessAreaId: currentBusinessAreaId,
            relationships: relationships
        };
        
        try {
            const response = await fetch('/api/businessarea-impact/systems/save', {
                method: 'POST',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(requestData)
            });
            
            if (!response.ok) {
                let errorText = '';
                try {
                    errorText = await response.text();
                } catch (e) {
                    errorText = response.statusText;
                }
                
                // Handle authentication errors specifically
                if (response.status === 401) {
                    console.error('Authentication failed - session may have expired. Please refresh the page or log in again.');
                    return { success: false, message: `Authentication failed (HTTP ${response.status}). Your session may have expired. Please refresh the page or log in again.` };
                }
                
                return { success: false, message: `Failed to save (HTTP ${response.status}): ${errorText}` };
            }
            
            const result = await response.json();
            const success = result && result.success === true;
            
            if (!success) {
                return { success: false, message: result.message || 'Failed to save' };
            }
            
            // Reload data from server to verify save
            await loadSystemRelationships();
            // Update original data after reload to match server data structure (prevents false change detection)
            // Note: loadSystemRelationships already sets originalSystemData, but we ensure it matches current state
            originalSystemData = JSON.parse(JSON.stringify(systemRelationships));
            return true;
            
        } catch (error) {
            console.error('Error saving system relationships:', error);
            return { success: false, message: 'Network error: ' + error.message };
        }
    }

    // ===== PROCESS RELATIONSHIPS =====
    
    async function loadProcessRelationships() {
        try {
            const response = await fetch(`/api/businessarea-impact/${currentBusinessAreaId}/processes`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            processRelationships = Array.isArray(data) ? data : [];
            renderProcessTable();
            // Update original data AFTER rendering (rendering may modify processRelationships with placeholder rows)
            originalProcessData = JSON.parse(JSON.stringify(processRelationships));

        } catch (error) {
            console.error('Error loading process relationships:', error);
            processRelationships = [];
            renderProcessTable();
            // Update original data AFTER rendering
            originalProcessData = JSON.parse(JSON.stringify(processRelationships));
        }
    }

    async function loadProcessRelationTypes() {
        try {
            const response = await fetch('/api/businessarea-impact/process-relation-types', {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            processRelationTypes = Array.isArray(data) ? data : [];

        } catch (error) {
            console.error('Error loading process relation types:', error);
            processRelationTypes = [];
        }
    }

    async function loadProcesses() {
        try {
            console.log('=== LOADING PROCESSES START ===');
            console.log('Loading processes from /api/process/...');
            
            const response = await fetch(withImpactSegment('/api/process/'));
            console.log('Process response status:', response.status);
            console.log('Process response ok:', response.ok);
            
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            
            const rawData = await response.json();
            console.log('Raw process data received:', rawData);
            
            if (rawData.success && rawData.data) {
                processes = rawData.data;
                console.log('Loaded processes from nested data:', processes.length);
            } else if (Array.isArray(rawData)) {
                processes = rawData;
                console.log('Loaded processes from direct array:', rawData.length);
            } else {
                console.log('No valid process data found');
                processes = [];
            }
            
            console.log('Final processes array length:', processes.length);
            
            if (processes.length > 0) {
                console.log('First process sample:', processes[0]);
                console.log('Process field names:', Object.keys(processes[0]));
            } else {
                console.log('No processes found in response');
            }
            console.log('=== LOADING PROCESSES END ===');

        } catch (error) {
            console.error('=== PROCESS LOADING ERROR ===');
            console.error('Error loading processes:', error);
            console.error('Error details:', error.message);
            processes = [];
            console.log('=== PROCESS LOADING ERROR END ===');
        }
    }

    function renderProcessTable() {
        const tbody = document.querySelector('#processImpactTableBody');
        const footer = document.querySelector('#processImpactFooter');
        
        if (!tbody) return;
        
        if (processRelationships.length === 0) {
            processRelationships.push({
                id: 'new-empty',
                relationType: null,
                processId: null,
                processName: null,
                processRefNumber: null,
                processRef: null,
                processOwnerName: null
            });
        }
        
        let html = '';
        processRelationships.forEach((relationship, index) => {
            const rowId = relationship.id || 'new-' + index;
            const isNewRow = !relationship.id || String(relationship.id).startsWith('new-');
            const ownerDisplay = isNewRow && !relationship.processId ? '' : (relationship.processOwnerName || 'No owner');
            
            html += `
                <tr data-id="${rowId}">
                    <td>
                        <select class="form-control" data-field="relationType">
                            <option value="">Select relationship type</option>
                            ${processRelationTypes.map(rt => 
                                `<option value="${rt.id || rt.ID}" ${relationship.relationType == (rt.id || rt.ID) ? 'selected' : ''}>${rt.primaryname || rt.primaryName || rt.PrimaryName || 'Unnamed Type'}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td>
                        <select class="form-control" data-field="processId" onchange="updateProcessOwner(this)">
                            <option value="">Select process</option>
                            ${processes.map(p => 
                                `<option value="${p.id || p.ID}" ${relationship.processId == (p.id || p.ID) ? 'selected' : ''}>${p.primaryname || p.primaryName || p.name || p.Name || 'Unnamed Process'}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td><span class="text-muted">${relationship.processRefNumber || relationship.processRef || ''}</span></td>
                    <td><span class="text-muted">${ownerDisplay}</span></td>
                    <td>
                        <div class="action-buttons">
                            <button type="button" class="btn btn-sm btn-success" onclick="addProcessRow()" title="${window.I18n?.t('impactEdit.buttons.addRow') || 'Add row'}">
                                <i class="fas fa-plus"></i>
                            </button>
                            <button type="button" class="btn btn-sm btn-danger" onclick="deleteProcessRow('${rowId}')" title="${window.I18n?.t('impactEdit.buttons.deleteRow') || 'Delete row'}">
                                <i class="fas fa-minus"></i>
                            </button>
                        </div>
                    </td>
                </tr>
            `;
        });
        
        tbody.innerHTML = html;
        if (footer) footer.textContent = `${processRelationships.length} record${processRelationships.length !== 1 ? 's' : ''}`;
    }

    function addProcessRow() {
        saveCurrentProcessFormData();
        const newRelationship = {
            id: 'new-' + Date.now(),
            relationType: null,
            processId: null,
            processName: null,
            processRefNumber: null,
            processRef: null,
            processOwnerName: null
        };
        processRelationships.push(newRelationship);
        renderProcessTable();
    }

    function deleteProcessRow(id) {
        // Save current form data before re-rendering
        saveCurrentProcessFormData();
        
        if (processRelationships.length <= 1) {
            // Clear the first row instead of deleting it
            processRelationships[0] = {
                id: 'new-' + Date.now(),
                relationType: null,
                processId: null,
                processName: null,
                processRefNumber: null,
                processRef: null,
                processOwnerName: null
            };
        } else {
            processRelationships = processRelationships.filter(rel => String(rel.id) !== String(id));
        }
        renderProcessTable();
    }

    function saveCurrentProcessFormData() {
        const rows = document.querySelectorAll('#processImpactTableBody tr');
        rows.forEach((row, index) => {
            if (processRelationships[index]) {
                const relationTypeSelect = row.querySelector('select[data-field="relationType"]');
                const processSelect = row.querySelector('select[data-field="processId"]');
                const ownerCell = row.querySelector('td:nth-child(4)'); // Process Owner column
                const refCell = row.querySelector('td:nth-child(3)'); // Ref. column
                
                if (relationTypeSelect) {
                    processRelationships[index].relationType = relationTypeSelect.value;
                }
                if (processSelect) {
                    processRelationships[index].processId = processSelect.value;
                    // Update process name based on selected process
                    const selectedOption = processSelect.options[processSelect.selectedIndex];
                    if (selectedOption) {
                        processRelationships[index].processName = selectedOption.text;
                    }
                }
                // Preserve the current owner name from the DOM
                if (ownerCell) {
                    const ownerSpan = ownerCell.querySelector('span');
                    if (ownerSpan) {
                        processRelationships[index].processOwnerName = ownerSpan.textContent.trim();
                    }
                }
                // Preserve the current reference number from the DOM
                if (refCell) {
                    const refSpan = refCell.querySelector('span');
                    if (refSpan) {
                        processRelationships[index].processRefNumber = refSpan.textContent.trim();
                        processRelationships[index].processRef = refSpan.textContent.trim();
                    }
                }
            }
        });
    }

    async function updateProcessOwner(selectElement) {
        const processId = selectElement.value;
        const row = selectElement.closest('tr');
        const ownerCell = row.querySelector('td:nth-child(4)'); // Process Owner column
        const refCell = row.querySelector('td:nth-child(3)'); // Ref. column
        
        if (!processId) {
            if (ownerCell) ownerCell.innerHTML = '<span class="text-muted"></span>';
            if (refCell) refCell.innerHTML = '<span class="text-muted"></span>';
            return;
        }
        
        try {
            console.log('Updating process owner for process ID:', processId);
            
            // Update reference number FIRST - get from processes array (synchronously)
            const selectedProcess = processes.find(p => p.id == processId || p.ID == processId);
            if (selectedProcess && refCell) {
                // Try refnumber field (standard field name)
                const processRef = selectedProcess.refnumber || '';
                refCell.innerHTML = `<span class="text-muted">${processRef || ''}</span>`;
                console.log('Updated process ref:', processRef, 'from process:', selectedProcess);
            } else if (refCell) {
                console.warn('Process not found for ID:', processId, 'Available processes:', processes.length);
                refCell.innerHTML = '<span class="text-muted"></span>';
            }
            
            // Update owner
            const response = await fetch(`/api/businessarea-impact/process-owner/${processId}`, {
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
            console.log('Process owner data:', data);
            
            if (data && data.ownerName) {
                if (ownerCell) ownerCell.innerHTML = `<span class="text-muted">${data.ownerName}</span>`;
            } else {
                if (ownerCell) ownerCell.innerHTML = '<span class="text-muted">No owner</span>';
            }
            
        } catch (error) {
            console.error('Error fetching process owner:', error);
            if (ownerCell) ownerCell.innerHTML = '<span class="text-muted">Error loading owner</span>';
            if (refCell) refCell.innerHTML = '<span class="text-muted"></span>';
        }
    }


    async function saveProcessRelationships() {
        try {
            saveCurrentProcessFormData();
            
            const relationships = processRelationships
                .filter(r => r.processId && r.relationType)
                .map(r => ({
                    processId: parseInt(r.processId),
                    relationType: parseInt(r.relationType),
                    description: r.description || null
                }));

            const requestData = {
                businessAreaId: currentBusinessAreaId,
                relationships: relationships
            };

            const response = await fetch('/api/businessarea-impact/processes/save', {
                method: 'POST',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(requestData)
            });

            if (!response.ok) {
                let errorText = '';
                try {
                    errorText = await response.text();
                } catch (e) {
                    errorText = response.statusText;
                }
                
                // Handle authentication errors specifically
                if (response.status === 401) {
                    console.error('Authentication failed - session may have expired. Please refresh the page or log in again.');
                    throw new Error(`Authentication failed (HTTP ${response.status}). Your session may have expired. Please refresh the page or log in again.`);
                }
                
                throw new Error(`HTTP error! status: ${response.status}, message: ${errorText}`);
            }

            const result = await response.json();
            
            if (result.success) {
                // Reload data from server to verify save
                await loadProcessRelationships();
                // Update original data after reload to match server data structure (prevents false change detection)
                // Note: loadProcessRelationships already sets originalProcessData, but we ensure it matches current state
                originalProcessData = JSON.parse(JSON.stringify(processRelationships));
                return true;
            } else {
                return { success: false, message: result.message || 'Failed to save process relationships' };
            }
        } catch (error) {
            console.error('Error saving process relationships:', error);
            return { success: false, message: 'Network error: ' + error.message };
        }
    }

    // ===== SAVE ALL IMPACT DATA =====
    
    async function saveAllImpactData() {
        const results = {
            glossary: await saveGlossaryRelationships(),
            system: await saveSystemRelationships(),
            process: await saveProcessRelationships()
        };
        
        const failedTabs = [];
        let errorDetails = [];
        
        for (const [tab, result] of Object.entries(results)) {
            if (result !== true && (!result.success || result.success === false)) {
                failedTabs.push(tab);
                if (result.message) {
                    errorDetails.push(`${tab}: ${result.message}`);
                }
            }
        }
        
        if (failedTabs.length > 0) {
            return {
                success: false,
                failedTabs: failedTabs,
                message: `Failed to save: ${failedTabs.join(', ')}`,
                errorDetails: errorDetails
            };
        }
        
        return { success: true };
    }

    function hasImpactChanges() {
        const hasGlossaryChanges = JSON.stringify(glossaryRelationships) !== JSON.stringify(originalGlossaryData);
        const hasSystemChanges = JSON.stringify(systemRelationships) !== JSON.stringify(originalSystemData);
        const hasProcessChanges = JSON.stringify(processRelationships) !== JSON.stringify(originalProcessData);
        
        const hasChanges = hasGlossaryChanges || hasSystemChanges || hasProcessChanges;
        
        if (hasChanges) {
            console.log('Business Area Impact Edit: hasImpactChanges detected changes:');
            if (hasGlossaryChanges) console.log('  - Glossary relationships changed');
            if (hasSystemChanges) console.log('  - System relationships changed');
            if (hasProcessChanges) console.log('  - Process relationships changed');
        }
        
        return hasChanges;
    }

    // ===== GLOBAL EXPORTS =====
    // Use facet-specific names so shared globals (saveAllImpactData / hasImpactChanges / initImpactEdit)
    // from another edit page cannot be left on window and cause this page to call the wrong API (e.g. project-impact).
    window.initBusinessAreaImpactEdit = initImpactEdit;
    window.saveBusinessAreaImpactData = saveAllImpactData;
    window.hasBusinessAreaImpactChanges = hasImpactChanges;
    
    window.addGlossaryRow = addGlossaryRow;
    window.deleteGlossaryRow = deleteGlossaryRow;
    window.updateGlossaryOwner = updateGlossaryOwner;
    
    window.addSystemRow = addSystemRow;
    window.deleteSystemRow = deleteSystemRow;
    window.updateSystemOwner = updateSystemOwner;
    
    window.addProcessRow = addProcessRow;
    window.deleteProcessRow = deleteProcessRow;
    window.updateProcessOwner = updateProcessOwner;

})();

