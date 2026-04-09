// Capability Impact Edit JavaScript - Implementation for System, Client, Product, Process, Glossary, Business Area, Legal Entity sub-tabs
console.log('=== CAPABILITY IMPACT EDIT SCRIPT LOADING ===');

(function() {
    console.log('=== CAPABILITY IMPACT EDIT SCRIPT LOADED ===');

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
            '#capabilitySegment',
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
        const oid = parseInt(currentCapabilityId, 10);
        if (Number.isInteger(oid) && oid > 0) {
            const sep2 = result.includes('?') ? '&' : '?';
            result = `${result}${sep2}sourceObjectId=${oid}&sourceObjectType=Capability`;
        }
        return result;
    }
    
    // Global variables
    let currentCapabilityId = null;
    let systemRelationships = [];
    let clientRelationships = [];
    let productRelationships = [];
    let processRelationships = [];
    let glossaryRelationships = [];
    let businessAreaRelationships = [];
    let legalRelationships = [];
    
    let originalSystemData = [];
    let originalClientData = [];
    let originalProductData = [];
    let originalProcessData = [];
    let originalGlossaryData = [];
    let originalBusinessAreaData = [];
    let originalLegalData = [];
    
    let systemRelationTypes = [];
    let clientRelationTypes = [];
    let productRelationTypes = [];
    let processRelationTypes = [];
    let glossaryRelationTypes = [];
    let businessAreaRelationTypes = [];
    let legalRelationTypes = [];
    
    let systems = [];
    let clients = [];
    let products = [];
    let processes = [];
    let glossaries = [];
    let businessAreas = [];
    let legals = [];

    // Initialize Impact tab edit functionality
    function initImpactEdit(capabilityId) {
        console.log('=== INIT IMPACT EDIT CALLED ===');
        console.log('Capability ID:', capabilityId);
        currentCapabilityId = capabilityId;
        console.log('Initializing Impact edit for capability:', capabilityId);
        loadImpactData();
    }

    // Load all Impact data
    async function loadImpactData() {
        try {
            console.log('=== LOADING IMPACT DATA START ===');
            console.log('Starting to load Impact data...');
            
            // Load dropdown data first
            console.log('Loading dropdown data...');
            await Promise.all([
                loadSystemRelationTypes(),
                loadClientRelationTypes(),
                loadProductRelationTypes(),
                loadProcessRelationTypes(),
                loadGlossaryRelationTypes(),
                loadBusinessAreaRelationTypes(),
                loadLegalRelationTypes(),
                loadSystems(),
                loadClients(),
                loadProducts(),
                loadProcesses(),
                loadGlossaries(),
                loadBusinessAreas(),
                loadLegals()
            ]);
            console.log('Dropdown data loaded successfully');

            // Load relationship data
            console.log('Loading relationship data...');
            await Promise.all([
                loadSystemRelationships(),
                loadClientRelationships(),
                loadProductRelationships(),
                loadProcessRelationships(),
                loadGlossaryRelationships(),
                loadBusinessAreaRelationships(),
                loadLegalRelationships()
            ]);
            console.log('Relationship data loaded successfully');

            // Setup sub-tabs
            setupImpactSubTabs();
            console.log('=== LOADING IMPACT DATA END ===');

        } catch (error) {
            console.error('=== IMPACT DATA LOADING ERROR ===');
            console.error('Error loading Impact data:', error);
            console.error('Error details:', error.message);
            console.log('=== IMPACT DATA LOADING ERROR END ===');
        }
    }

    // Setup Impact sub-tabs
    function setupImpactSubTabs() {
        console.log('Setting up Impact sub-tabs...');
        
        const subTabs = document.querySelectorAll('#impactTab .sub-tab');
        console.log('Found Impact sub-tabs:', subTabs.length);
        
        subTabs.forEach(function(subTab) {
            subTab.addEventListener('click', function(e) {
                e.preventDefault();
                console.log('Impact sub-tab clicked:', this.textContent.trim());
                
                // Remove active class from all sub-tabs
                subTabs.forEach(function(t) { t.classList.remove('active'); });
                this.classList.add('active');
                
                const subTabName = this.getAttribute('data-sub-tab');
                console.log('Switching to Impact sub-tab:', subTabName);
                
                // Hide all sub-tab contents
                const subTabContents = document.querySelectorAll('#impactTab .sub-tab-content');
                subTabContents.forEach(function(content) {
                    content.classList.remove('active');
                    content.style.display = 'none';
                });
                
                // Show selected sub-tab content
                let targetSubTab;
                if (subTabName === 'system') {
                    targetSubTab = document.getElementById('impactSystemContent');
                } else if (subTabName === 'client') {
                    targetSubTab = document.getElementById('impactClientContent');
                } else if (subTabName === 'product') {
                    targetSubTab = document.getElementById('impactProductContent');
                } else if (subTabName === 'process') {
                    targetSubTab = document.getElementById('impactProcessContent');
                } else if (subTabName === 'glossary') {
                    targetSubTab = document.getElementById('impactGlossaryContent');
                } else if (subTabName === 'businessarea') {
                    targetSubTab = document.getElementById('impactBusinessAreaContent');
                } else if (subTabName === 'legal') {
                    targetSubTab = document.getElementById('impactLegalContent');
                }
                
                if (targetSubTab) {
                    targetSubTab.classList.add('active');
                    targetSubTab.style.display = 'block';
                    console.log('Switched to Impact sub-tab:', subTabName);
                } else {
                    console.error('Impact sub-tab content not found for:', subTabName);
                }
            });
        });
    }

    // ===== SYSTEM RELATIONSHIPS =====
    
    async function loadSystemRelationships() {
        try {
            console.log('Loading system relationships for capability:', currentCapabilityId);

            const response = await fetch(`/api/capability-impact/${currentCapabilityId}/systems`, {
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
            console.log('System relationships API response:', data);

            systemRelationships = Array.isArray(data) ? data : [];
            originalSystemData = JSON.parse(JSON.stringify(systemRelationships));

            renderSystemTable();

        } catch (error) {
            console.error('Error loading system relationships:', error);
            systemRelationships = [];
            originalSystemData = [];
            renderSystemTable();
        }
    }

    async function loadSystemRelationTypes() {
        try {
            const response = await fetch('/api/capability-impact/system-relation-types', {
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
            systemRelationTypes = Array.isArray(data) ? data : [];
            console.log('Loaded system relation types:', systemRelationTypes.length);

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
                headers: {
                    'Content-Type': 'application/json'
                }
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const data = await response.json();
            systems = Array.isArray(data) ? data : [];
            console.log('Loaded systems:', systems.length);

        } catch (error) {
            console.error('Error loading systems:', error);
            systems = [];
        }
    }

    function renderSystemTable() {
        const tbody = document.getElementById('systemImpactTableBody');
        const footer = document.getElementById('systemImpactFooter');
        
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
            const ownerName = relationship.systemOwnerName || relationship.ownerName || '';
            const ownerDisplay = isNewRow && !relationship.systemId ? '' : (ownerName || 'No owner');
            
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
                    <td style="text-align: center;">
                        <span class="text-muted">${ownerDisplay}</span>
                    </td>
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
        saveCurrentSystemFormData();
        if (systemRelationships.length <= 1) {
            systemRelationships[0] = { id: 'new-' + Date.now(), relationType: null, systemId: null, systemName: null, systemOwnerName: null };
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
                const system = systems.find(s => s.id == systemSelect.value || s.ID == systemSelect.value);
                if (system) {
                    systemName = system.name || system.Name;
                }
            }
            
            const ownerCell = row.querySelector('td:nth-child(3)');
            let ownerName = null;
            if (ownerCell) {
                const ownerSpan = ownerCell.querySelector('span');
                if (ownerSpan) {
                    ownerName = ownerSpan.textContent.trim();
                }
            }
            
            const data = {
                id: rowId,
                relationType: relationTypeSelect ? (relationTypeSelect.value ? parseInt(relationTypeSelect.value) : null) : null,
                systemId: systemSelect ? (systemSelect.value ? parseInt(systemSelect.value) : null) : null,
                systemName: systemName,
                systemOwnerName: ownerName
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
            const response = await fetch(`/api/capability-impact/system-owner/${systemId}`, {
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
            capabilityId: currentCapabilityId,
            relationships: relationships
        };
        
        try {
            const response = await fetch('/api/capability-impact/systems/save', {
                method: 'POST',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(requestData)
            });
            
            if (!response.ok) {
                return { success: false, message: `Failed to save (HTTP ${response.status})` };
            }
            
            const result = await response.json();
            const success = result && result.success === true;
            
            if (!success) {
                return { success: false, message: result.message || 'Failed to save' };
            }
            return true;
            
        } catch (error) {
            console.error('Error saving system relationships:', error);
            return { success: false, message: 'Network error: ' + error.message };
        }
    }

    // ===== CLIENT RELATIONSHIPS =====
    
    async function loadClientRelationships() {
        try {
            console.log('Loading client relationships for capability:', currentCapabilityId);

            const response = await fetch(`/api/capability-impact/${currentCapabilityId}/clients`, {
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
            console.log('Client relationships API response:', data);

            clientRelationships = Array.isArray(data) ? data : [];
            originalClientData = JSON.parse(JSON.stringify(clientRelationships));

            renderClientTable();

        } catch (error) {
            console.error('Error loading client relationships:', error);
            clientRelationships = [];
            originalClientData = [];
            renderClientTable();
        }
    }

    async function loadClientRelationTypes() {
        try {
            const response = await fetch('/api/capability-impact/client-relation-types', {
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
            clientRelationTypes = Array.isArray(data) ? data : [];
            console.log('Loaded client relation types:', clientRelationTypes.length);

        } catch (error) {
            console.error('Error loading client relation types:', error);
            clientRelationTypes = [];
        }
    }

    async function loadClients() {
        try {
            const response = await fetch(withImpactSegment('/api/client/'), {
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
            
            // Handle nested data structure
            if (data && data.success && Array.isArray(data.data)) {
                clients = data.data;
            } else if (Array.isArray(data)) {
                clients = data;
            } else {
                clients = [];
            }
            
            console.log('Loaded clients:', clients.length);

        } catch (error) {
            console.error('Error loading clients:', error);
            clients = [];
        }
    }

    function renderClientTable() {
        const tbody = document.getElementById('clientImpactTableBody');
        const footer = document.getElementById('clientImpactFooter');
        
        if (!tbody) return;

        if (clientRelationships.length === 0) {
            clientRelationships.push({
                id: 'new-empty',
                clientId: null,
                relationType: null,
                clientName: '',
                clientOwnerName: ''
            });
        }

        let html = '';
        clientRelationships.forEach((relationship, index) => {
            const rowId = relationship.id || 'new-' + index;
            html += `
                <tr data-id="${rowId}">
                    <td>
                        <select class="form-control" data-field="relationType">
                            <option value="">Select relationship type</option>
                            ${clientRelationTypes.map(rt => 
                                `<option value="${rt.id || rt.ID}" ${relationship.relationType == (rt.id || rt.ID) ? 'selected' : ''}>${rt.primaryname || rt.primaryName || rt.PrimaryName || 'Unnamed Type'}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td>
                        <select class="form-control" data-field="clientId" onchange="updateClientOwner(this)">
                            <option value="">Select client</option>
                            ${clients.map(c => 
                                `<option value="${c.id || c.ID}" ${relationship.clientId == (c.id || c.ID) ? 'selected' : ''}>${c.primary_name || c.primaryName || c.PrimaryName || 'Unnamed Client'}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td style="text-align: center;">
                        <span class="text-muted">${relationship.clientOwnerName || 'No owner'}</span>
                    </td>
                    <td>
                        <div class="action-buttons">
                            <button type="button" class="btn btn-sm btn-success" onclick="addClientRow()" title="${window.I18n?.t('impactEdit.buttons.addRow') || 'Add row'}">
                                <i class="fas fa-plus"></i>
                            </button>
                            <button type="button" class="btn btn-sm btn-danger" onclick="deleteClientRow('${rowId}')" title="${window.I18n?.t('impactEdit.buttons.deleteRow') || 'Delete row'}">
                                <i class="fas fa-minus"></i>
                            </button>
                        </div>
                    </td>
                </tr>
            `;
        });

        tbody.innerHTML = html;
        if (footer) footer.textContent = `${clientRelationships.length} record${clientRelationships.length !== 1 ? 's' : ''}`;
    }

    function addClientRow() {
        saveCurrentClientFormData();
        const newRelationship = {
            id: 'new-' + Date.now(),
            relationType: null,
            clientId: null,
            clientName: null,
            clientOwnerName: null
        };
        clientRelationships.push(newRelationship);
        renderClientTable();
    }

    function deleteClientRow(id) {
        saveCurrentClientFormData();
        if (clientRelationships.length <= 1) {
            clientRelationships[0] = { id: 'new-' + Date.now(), relationType: null, clientId: null, clientName: null, clientOwnerName: null };
        } else {
            clientRelationships = clientRelationships.filter(rel => String(rel.id) !== String(id));
        }
        renderClientTable();
    }

    function saveCurrentClientFormData() {
        const rows = document.querySelectorAll('#clientImpactTableBody tr');
        rows.forEach((row, index) => {
            if (clientRelationships[index]) {
                const relationTypeSelect = row.querySelector('select[data-field="relationType"]');
                const clientSelect = row.querySelector('select[data-field="clientId"]');
                const ownerCell = row.querySelector('td:nth-child(3)');
                
                if (relationTypeSelect) {
                    clientRelationships[index].relationType = relationTypeSelect.value ? parseInt(relationTypeSelect.value) : null;
                }
                if (clientSelect) {
                    clientRelationships[index].clientId = clientSelect.value ? parseInt(clientSelect.value) : null;
                    const selectedOption = clientSelect.options[clientSelect.selectedIndex];
                    if (selectedOption) {
                        clientRelationships[index].clientName = selectedOption.text;
                    }
                }
                if (ownerCell) {
                    const ownerSpan = ownerCell.querySelector('span');
                    if (ownerSpan) {
                        clientRelationships[index].clientOwnerName = ownerSpan.textContent.trim();
                    }
                }
            }
        });
    }

    async function updateClientOwner(selectElement) {
        const clientId = selectElement.value;
        const row = selectElement.closest('tr');
        const ownerCell = row.querySelector('td:nth-child(3)');
        
        if (!clientId) {
            ownerCell.innerHTML = '<span class="text-muted">No owner</span>';
            return;
        }
        
        try {
            const response = await fetch(`/api/capability-impact/client-owner/${clientId}`, {
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
            if (data && data.ownerName) {
                ownerCell.innerHTML = `<span class="text-muted">${data.ownerName}</span>`;
            } else {
                ownerCell.innerHTML = '<span class="text-muted">No owner</span>';
            }
            
        } catch (error) {
            console.error('Error fetching client owner:', error);
            ownerCell.innerHTML = '<span class="text-muted">Error loading owner</span>';
        }
    }

    async function saveClientRelationships() {
        saveCurrentClientFormData();
        
        const relationships = [];
        const rows = document.querySelectorAll('#clientImpactTableBody tr');
        
        rows.forEach(row => {
            const relationTypeSelect = row.querySelector('select[data-field="relationType"]');
            const clientSelect = row.querySelector('select[data-field="clientId"]');
            
            if (relationTypeSelect?.value && clientSelect?.value) {
                relationships.push({
                    relationType: parseInt(relationTypeSelect.value),
                    clientId: parseInt(clientSelect.value)
                });
            }
        });
        
        try {
            const response = await fetch('/api/capability-impact/clients/save', {
                method: 'POST',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    capabilityId: currentCapabilityId,
                    relationships: relationships
                })
            });
            
            if (!response.ok) {
                return {
                    success: false,
                    message: `Failed to save client relationships (HTTP ${response.status})`
                };
            }
            
            const result = await response.json();
            const success = result && result.success === true;
            if (!success) {
                return {
                    success: false,
                    message: result.message || 'Failed to save client relationships'
                };
            }
            return true;
            
        } catch (error) {
            console.error('Error saving client relationships:', error);
            return {
                success: false,
                message: 'Error saving client relationships: ' + (error.message || 'Unknown error')
            };
        }
    }

    // ===== PRODUCT RELATIONSHIPS =====
    
    async function loadProductRelationships() {
        try {
            console.log('Loading product relationships for capability:', currentCapabilityId);

            const response = await fetch(`/api/capability-impact/${currentCapabilityId}/products`, {
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
            console.log('Product relationships API response:', data);

            productRelationships = Array.isArray(data) ? data : [];
            originalProductData = JSON.parse(JSON.stringify(productRelationships));

            renderProductTable();

        } catch (error) {
            console.error('Error loading product relationships:', error);
            productRelationships = [];
            originalProductData = [];
            renderProductTable();
        }
    }

    async function loadProductRelationTypes() {
        try {
            const response = await fetch('/api/capability-impact/product-relation-types', {
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
            productRelationTypes = Array.isArray(data) ? data : [];
            console.log('Loaded product relation types:', productRelationTypes.length);

        } catch (error) {
            console.error('Error loading product relation types:', error);
            productRelationTypes = [];
        }
    }

    async function loadProducts() {
        try {
            const response = await fetch(withImpactSegment('/api/product/list'), {
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
            products = Array.isArray(data) ? data : [];
            console.log('Loaded products:', products.length);

        } catch (error) {
            console.error('Error loading products:', error);
            products = [];
        }
    }

    function renderProductTable() {
        const tbody = document.getElementById('productImpactTableBody');
        const footer = document.getElementById('productImpactFooter');
        
        if (!tbody) return;

        if (productRelationships.length === 0) {
            productRelationships.push({
                id: 'new-empty',
                productId: null,
                relationType: null,
                productName: '',
                productOwnerName: ''
            });
        }

        let html = '';
        productRelationships.forEach((relationship, index) => {
            const rowId = relationship.id || 'new-' + index;
            html += `
                <tr data-id="${rowId}">
                    <td>
                        <select class="form-control" data-field="relationType">
                            <option value="">Select relationship type</option>
                            ${productRelationTypes.map(rt => 
                                `<option value="${rt.id || rt.ID}" ${relationship.relationType == (rt.id || rt.ID) ? 'selected' : ''}>${rt.primaryname || rt.primaryName || rt.PrimaryName || 'Unnamed Type'}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td>
                        <select class="form-control" data-field="productId" onchange="updateProductOwner(this)">
                            <option value="">Select product</option>
                            ${products.map(p => 
                                `<option value="${p.id || p.ID}" ${relationship.productId == (p.id || p.ID) ? 'selected' : ''}>${p.primaryname || p.primaryName || p.PrimaryName || p.name || p.Name || 'Unnamed Product'}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td style="text-align: center;">
                        <span class="text-muted">${relationship.productOwnerName || 'No owner'}</span>
                    </td>
                    <td>
                        <div class="action-buttons">
                            <button type="button" class="btn btn-sm btn-success" onclick="addProductRow()" title="${window.I18n?.t('impactEdit.buttons.addRow') || 'Add row'}">
                                <i class="fas fa-plus"></i>
                            </button>
                            <button type="button" class="btn btn-sm btn-danger" onclick="deleteProductRow('${rowId}')" title="${window.I18n?.t('impactEdit.buttons.deleteRow') || 'Delete row'}">
                                <i class="fas fa-minus"></i>
                            </button>
                        </div>
                    </td>
                </tr>
            `;
        });

        tbody.innerHTML = html;
        if (footer) footer.textContent = `${productRelationships.length} record${productRelationships.length !== 1 ? 's' : ''}`;
    }

    function addProductRow() {
        saveCurrentProductFormData();
        const newRelationship = {
            id: 'new-' + Date.now(),
            relationType: null,
            productId: null,
            productName: null,
            productOwnerName: null
        };
        productRelationships.push(newRelationship);
        renderProductTable();
    }

    function deleteProductRow(id) {
        saveCurrentProductFormData();
        if (productRelationships.length <= 1) {
            productRelationships[0] = { id: 'new-' + Date.now(), relationType: null, productId: null, productName: null, productOwnerName: null };
        } else {
            productRelationships = productRelationships.filter(rel => String(rel.id) !== String(id));
        }
        renderProductTable();
    }

    function saveCurrentProductFormData() {
        const rows = document.querySelectorAll('#productImpactTableBody tr');
        rows.forEach((row, index) => {
            if (productRelationships[index]) {
                const relationTypeSelect = row.querySelector('select[data-field="relationType"]');
                const productSelect = row.querySelector('select[data-field="productId"]');
                const ownerCell = row.querySelector('td:nth-child(3)');
                
                if (relationTypeSelect) {
                    productRelationships[index].relationType = relationTypeSelect.value ? parseInt(relationTypeSelect.value) : null;
                }
                if (productSelect) {
                    productRelationships[index].productId = productSelect.value ? parseInt(productSelect.value) : null;
                    const selectedOption = productSelect.options[productSelect.selectedIndex];
                    if (selectedOption) {
                        productRelationships[index].productName = selectedOption.text;
                    }
                }
                if (ownerCell) {
                    const ownerSpan = ownerCell.querySelector('span');
                    if (ownerSpan) {
                        productRelationships[index].productOwnerName = ownerSpan.textContent.trim();
                    }
                }
            }
        });
    }

    async function updateProductOwner(selectElement) {
        const productId = selectElement.value;
        const row = selectElement.closest('tr');
        const ownerCell = row.querySelector('td:nth-child(3)');
        
        if (!productId) {
            ownerCell.innerHTML = '<span class="text-muted">No owner</span>';
            return;
        }
        
        try {
            const response = await fetch(`/api/capability-impact/product-owner/${productId}`, {
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
            if (data && data.ownerName) {
                ownerCell.innerHTML = `<span class="text-muted">${data.ownerName}</span>`;
            } else {
                ownerCell.innerHTML = '<span class="text-muted">No owner</span>';
            }
            
        } catch (error) {
            console.error('Error fetching product owner:', error);
            ownerCell.innerHTML = '<span class="text-muted">Error loading owner</span>';
        }
    }

    async function saveProductRelationships() {
        saveCurrentProductFormData();
        
        const relationships = [];
        const rows = document.querySelectorAll('#productImpactTableBody tr');
        
        rows.forEach(row => {
            const relationTypeSelect = row.querySelector('select[data-field="relationType"]');
            const productSelect = row.querySelector('select[data-field="productId"]');
            
            if (relationTypeSelect?.value && productSelect?.value) {
                relationships.push({
                    relationType: parseInt(relationTypeSelect.value),
                    productId: parseInt(productSelect.value)
                });
            }
        });
        
        try {
            const response = await fetch('/api/capability-impact/products/save', {
                method: 'POST',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    capabilityId: currentCapabilityId,
                    relationships: relationships
                })
            });
            
            if (!response.ok) {
                return {
                    success: false,
                    message: `Failed to save product relationships (HTTP ${response.status})`
                };
            }
            
            const result = await response.json();
            const success = result && result.success === true;
            if (!success) {
                return {
                    success: false,
                    message: result.message || 'Failed to save product relationships'
                };
            }
            return true;
            
        } catch (error) {
            console.error('Error saving product relationships:', error);
            return {
                success: false,
                message: 'Error saving product relationships: ' + (error.message || 'Unknown error')
            };
        }
    }

    // ===== PROCESS RELATIONSHIPS =====
    
    async function loadProcessRelationships() {
        try {
            console.log('Loading process relationships for capability:', currentCapabilityId);

            const response = await fetch(`/api/capability-impact/${currentCapabilityId}/processes`, {
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
            console.log('Process relationships API response:', data);

            processRelationships = Array.isArray(data) ? data : [];
            originalProcessData = JSON.parse(JSON.stringify(processRelationships));

            renderProcessTable();

        } catch (error) {
            console.error('Error loading process relationships:', error);
            processRelationships = [];
            originalProcessData = [];
            renderProcessTable();
        }
    }

    async function loadProcessRelationTypes() {
        try {
            const response = await fetch('/api/capability-impact/process-relation-types', {
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
            processRelationTypes = Array.isArray(data) ? data : [];
            console.log('Loaded process relation types:', processRelationTypes.length);

        } catch (error) {
            console.error('Error loading process relation types:', error);
            processRelationTypes = [];
        }
    }

    async function loadProcesses() {
        try {
            const response = await fetch(withImpactSegment('/api/process/'), {
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
            
            if (data && data.success && Array.isArray(data.data)) {
                processes = data.data;
            } else if (Array.isArray(data)) {
                processes = data;
            } else {
                processes = [];
            }
            
            console.log('Loaded processes:', processes.length);

        } catch (error) {
            console.error('Error loading processes:', error);
            processes = [];
        }
    }

    function renderProcessTable() {
        const tbody = document.getElementById('processImpactTableBody');
        const footer = document.getElementById('processImpactFooter');
        
        if (!tbody) return;

        if (processRelationships.length === 0) {
            processRelationships.push({
                id: 'new-empty',
                processId: null,
                relationType: null,
                processName: '',
                processRefNumber: '',
                processOwnerName: ''
            });
        }

        let html = '';
        processRelationships.forEach((relationship, index) => {
            const rowId = relationship.id || 'new-' + index;
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
                                `<option value="${p.id || p.ID}" ${relationship.processId == (p.id || p.ID) ? 'selected' : ''}>${p.primaryname || p.primaryName || p.PrimaryName || 'Unnamed Process'}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td style="text-align: center;">
                        <span class="text-muted">${relationship.processRefNumber || ''}</span>
                    </td>
                    <td style="text-align: center;">
                        <span class="text-muted">${relationship.processOwnerName || 'No owner'}</span>
                    </td>
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
            processOwnerName: null
        };
        processRelationships.push(newRelationship);
        renderProcessTable();
    }

    function deleteProcessRow(id) {
        saveCurrentProcessFormData();
        if (processRelationships.length <= 1) {
            processRelationships[0] = { id: 'new-' + Date.now(), relationType: null, processId: null, processName: null, processRefNumber: null, processOwnerName: null };
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
                const ownerCell = row.querySelector('td:nth-child(4)');
                const refCell = row.querySelector('td:nth-child(3)');
                
                if (relationTypeSelect) {
                    processRelationships[index].relationType = relationTypeSelect.value ? parseInt(relationTypeSelect.value) : null;
                }
                if (processSelect) {
                    processRelationships[index].processId = processSelect.value ? parseInt(processSelect.value) : null;
                    const selectedOption = processSelect.options[processSelect.selectedIndex];
                    if (selectedOption) {
                        processRelationships[index].processName = selectedOption.text;
                    }
                }
                if (ownerCell) {
                    const ownerSpan = ownerCell.querySelector('span');
                    if (ownerSpan) {
                        processRelationships[index].processOwnerName = ownerSpan.textContent.trim();
                    }
                }
                if (refCell) {
                    const refSpan = refCell.querySelector('span');
                    if (refSpan) {
                        processRelationships[index].processRefNumber = refSpan.textContent.trim();
                    }
                }
            }
        });
    }

    async function updateProcessOwner(selectElement) {
        const processId = selectElement.value;
        const row = selectElement.closest('tr');
        const ownerCell = row.querySelector('td:nth-child(4)');
        const refCell = row.querySelector('td:nth-child(3)');
        
        if (!processId) {
            ownerCell.innerHTML = '<span class="text-muted">No owner</span>';
            refCell.innerHTML = '<span class="text-muted"></span>';
            return;
        }
        
        try {
            // Fetch process details to get ref number
            const processResponse = await fetch(`/api/process/${processId}`, {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });
            
            if (processResponse.ok) {
                const processData = await processResponse.json();
                const process = processData.data || processData;
                if (process && process.refnumber) {
                    refCell.innerHTML = `<span class="text-muted">${process.refnumber}</span>`;
                }
            }
            
            // Fetch owner
            const ownerResponse = await fetch(`/api/capability-impact/process-owner/${processId}`, {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });
            
            if (!ownerResponse.ok) {
                throw new Error(`HTTP error! status: ${ownerResponse.status}`);
            }
            
            const data = await ownerResponse.json();
            if (data && data.ownerName) {
                ownerCell.innerHTML = `<span class="text-muted">${data.ownerName}</span>`;
            } else {
                ownerCell.innerHTML = '<span class="text-muted">No owner</span>';
            }
            
        } catch (error) {
            console.error('Error fetching process owner:', error);
            ownerCell.innerHTML = '<span class="text-muted">Error loading owner</span>';
        }
    }

    async function saveProcessRelationships() {
        saveCurrentProcessFormData();
        
        const relationships = [];
        const rows = document.querySelectorAll('#processImpactTableBody tr');
        
        rows.forEach(row => {
            const relationTypeSelect = row.querySelector('select[data-field="relationType"]');
            const processSelect = row.querySelector('select[data-field="processId"]');
            
            if (relationTypeSelect?.value && processSelect?.value) {
                relationships.push({
                    relationType: parseInt(relationTypeSelect.value),
                    processId: parseInt(processSelect.value)
                });
            }
        });
        
        try {
            const response = await fetch('/api/capability-impact/processes/save', {
                method: 'POST',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    capabilityId: currentCapabilityId,
                    relationships: relationships
                })
            });
            
            if (!response.ok) {
                return {
                    success: false,
                    message: `Failed to save process relationships (HTTP ${response.status})`
                };
            }
            
            const result = await response.json();
            const success = result && result.success === true;
            if (!success) {
                return {
                    success: false,
                    message: result.message || 'Failed to save process relationships'
                };
            }
            return true;
            
        } catch (error) {
            console.error('Error saving process relationships:', error);
            return {
                success: false,
                message: 'Error saving process relationships: ' + (error.message || 'Unknown error')
            };
        }
    }

    // ===== GLOSSARY RELATIONSHIPS =====
    
    async function loadGlossaryRelationships() {
        try {
            console.log('Loading glossary relationships for capability:', currentCapabilityId);

            const response = await fetch(`/api/capability-impact/${currentCapabilityId}/glossaries`, {
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
            console.log('Glossary relationships API response:', data);

            glossaryRelationships = Array.isArray(data) ? data : [];
            originalGlossaryData = JSON.parse(JSON.stringify(glossaryRelationships));

            renderGlossaryTable();

        } catch (error) {
            console.error('Error loading glossary relationships:', error);
            glossaryRelationships = [];
            originalGlossaryData = [];
            renderGlossaryTable();
        }
    }

    async function loadGlossaryRelationTypes() {
        try {
            const response = await fetch('/api/capability-impact/glossary-relation-types', {
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
            glossaryRelationTypes = Array.isArray(data) ? data : [];
            console.log('Loaded glossary relation types:', glossaryRelationTypes.length);

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
                headers: {
                    'Content-Type': 'application/json'
                }
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const data = await response.json();
            glossaries = Array.isArray(data) ? data : [];
            console.log('Loaded glossaries:', glossaries.length);

        } catch (error) {
            console.error('Error loading glossaries:', error);
            glossaries = [];
        }
    }

    function renderGlossaryTable() {
        const tbody = document.getElementById('glossaryImpactTableBody');
        const footer = document.getElementById('glossaryImpactFooter');
        
        if (!tbody) return;
        
        if (glossaryRelationships.length === 0) {
            glossaryRelationships.push({
                id: 'new-empty',
                glossaryId: null,
                relationType: null,
                glossaryName: '',
                glossaryTypeName: '',
                glossaryOwnerName: ''
            });
        }
        
        let html = '';
        glossaryRelationships.forEach((relationship, index) => {
            const rowId = relationship.id || 'new-' + index;
            
            let glossaryName = relationship.glossaryName || '';
            const glossaryRefNumber = relationship.glossaryRefNumber || '';
            const displayName = glossaryRefNumber ? `${glossaryName} (${glossaryRefNumber})` : glossaryName;
            
            let typeName = relationship.glossaryTypeName || '';
            if (!typeName && relationship.glossaryId) {
                const glossary = glossaries.find(g => (g.ID === relationship.glossaryId || g.id === relationship.glossaryId));
                if (glossary) {
                    typeName = glossary.typeName || glossary.Type_Name || glossary.Type || '';
                }
            }
            
            html += `
                <tr data-id="${rowId}">
                    <td>
                        <select class="form-control" data-field="relationType">
                            <option value="">Select relationship type</option>
                            ${glossaryRelationTypes.map(rt => 
                                `<option value="${rt.ID || rt.id}" ${relationship.relationType && (rt.ID === relationship.relationType || rt.id === relationship.relationType) ? 'selected' : ''}>${rt.PrimaryName || rt.primaryName || rt.primaryname || rt.name || 'Unnamed Type'}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td>
                        <select class="form-control" data-field="glossaryId" onchange="updateGlossaryType(this); updateGlossaryOwner(this);">
                            <option value="">Select glossary</option>
                            ${glossaries.map(g => {
                                const name = g.Name || g.name || '';
                                const refNumber = g.Ref_Number || g.refNumber || g.ref_number || '';
                                const displayText = refNumber ? `${name} (${refNumber})` : name;
                                return `<option value="${g.ID || g.id}" ${relationship.glossaryId && (g.ID === relationship.glossaryId || g.id === relationship.glossaryId) ? 'selected' : ''}>${displayText}</option>`;
                            }).join('')}
                        </select>
                    </td>
                    <td style="text-align: center;">
                        <span class="text-muted">${typeName || ''}</span>
                    </td>
                    <td style="text-align: center;">
                        <span class="text-muted">${relationship.glossaryOwnerName || 'Loading...'}</span>
                    </td>
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
        
        // Update owner display for rows with glossaryId
        glossaryRelationships.forEach((relationship, index) => {
            if (relationship.glossaryId) {
                const row = tbody.querySelectorAll('tr')[index];
                if (row) {
                    const ownerCell = row.querySelector('td:nth-child(4)');
                    if (ownerCell) {
                        updateGlossaryOwnerDisplay(ownerCell, relationship.glossaryId);
                    }
                }
            }
        });
        
        if (footer) {
            const count = glossaryRelationships.filter(r => r.glossaryId && r.relationType).length;
            footer.textContent = `${count} record${count !== 1 ? 's' : ''}`;
        }
    }
    
    function addGlossaryRow() {
        saveCurrentGlossaryFormData();
        
        const newRelationship = {
            id: 'new-' + Date.now(),
            glossaryId: null,
            relationType: null,
            glossaryName: '',
            glossaryTypeName: '',
            glossaryOwnerName: ''
        };
        glossaryRelationships.push(newRelationship);
        renderGlossaryTable();
    }
    
    function deleteGlossaryRow(id) {
        saveCurrentGlossaryFormData();
        
        if (glossaryRelationships.length <= 1) {
            glossaryRelationships[0] = {
                id: 'new-' + Date.now(),
                glossaryId: null,
                relationType: null,
                glossaryName: '',
                glossaryTypeName: '',
                glossaryOwnerName: ''
            };
        } else {
            glossaryRelationships = glossaryRelationships.filter(rel => String(rel.id) !== String(id));
        }
        renderGlossaryTable();
    }
    
    function updateGlossaryType(selectElement) {
        const glossaryId = selectElement.value;
        const row = selectElement.closest('tr');
        const typeCell = row.querySelector('td:nth-child(3)');
        if (!typeCell) return;
        
        if (glossaryId) {
            const glossary = glossaries.find(g => (g.ID == glossaryId || g.id == glossaryId));
            if (glossary) {
                const typeName = glossary.typeName || glossary.Type_Name || glossary.Type;
                if (typeName) {
                    typeCell.innerHTML = `<span class="text-muted">${typeName}</span>`;
                } else {
                    typeCell.innerHTML = '<span class="text-muted"></span>';
                }
            } else {
                typeCell.innerHTML = '<span class="text-muted"></span>';
            }
        } else {
            typeCell.innerHTML = '<span class="text-muted"></span>';
        }
    }
    
    async function updateGlossaryOwner(selectElement) {
        const glossaryId = selectElement.value;
        const row = selectElement.closest('tr');
        const ownerCell = row.querySelector('td:nth-child(4)');
        if (!ownerCell) return;
        
        if (glossaryId) {
            await updateGlossaryOwnerDisplay(ownerCell, parseInt(glossaryId));
        } else {
            ownerCell.innerHTML = '<span class="text-muted"></span>';
        }
    }
    
    async function updateGlossaryOwnerDisplay(ownerCell, glossaryId) {
        try {
            const response = await fetch(`/api/capability-impact/glossary-owner/${glossaryId}`, {
                credentials: 'include'
            });
            if (response.ok) {
                const data = await response.json();
                if (data && data.ownerName) {
                    ownerCell.innerHTML = `<span class="text-muted" title="${data.ownerName}">${data.ownerName}</span>`;
                } else {
                    ownerCell.innerHTML = '<span class="text-muted">No owner</span>';
                }
            } else {
                ownerCell.innerHTML = '<span class="text-muted">Error loading owner</span>';
            }
        } catch (error) {
            console.error('Error fetching glossary owner:', error);
            ownerCell.innerHTML = '<span class="text-muted">Error loading owner</span>';
        }
    }
    
    function saveCurrentGlossaryFormData() {
        const rows = document.querySelectorAll('#glossaryImpactTableBody tr');
        const savedData = [];
        
        rows.forEach((row, index) => {
            const relationTypeSelect = row.querySelector('select[data-field="relationType"]');
            const glossarySelect = row.querySelector('select[data-field="glossaryId"]');
            const typeSpan = row.querySelector('td:nth-child(3) span');
            const ownerSpan = row.querySelector('td:nth-child(4) span');
            
            const existingRelationship = glossaryRelationships[index];
            const rowId = existingRelationship && existingRelationship.id ? existingRelationship.id : ('new-' + Date.now() + '-' + index);
            
            let glossaryName = null;
            let glossaryRefNumber = null;
            if (glossarySelect && glossarySelect.value) {
                const glossary = glossaries.find(g => (g.ID == glossarySelect.value || g.id == glossarySelect.value));
                if (glossary) {
                    glossaryName = glossary.Name || glossary.name || null;
                    glossaryRefNumber = glossary.Ref_Number || glossary.refNumber || glossary.ref_number || null;
                }
            }
            
            const data = {
                id: rowId,
                relationType: relationTypeSelect ? parseInt(relationTypeSelect.value) : null,
                glossaryId: glossarySelect ? parseInt(glossarySelect.value) : null,
                glossaryName: glossaryName,
                glossaryRefNumber: glossaryRefNumber,
                glossaryTypeName: typeSpan ? typeSpan.textContent.trim() : null,
                glossaryOwnerName: ownerSpan ? ownerSpan.textContent.trim() : null
            };
            
            savedData.push(data);
        });
        
        glossaryRelationships = savedData.length > 0 ? savedData : [{
            id: 'new-empty',
            glossaryId: null,
            relationType: null,
            glossaryName: '',
            glossaryTypeName: '',
            glossaryOwnerName: ''
        }];
    }

    async function saveGlossaryRelationships() {
        saveCurrentGlossaryFormData();
        
        const relationships = [];
        const rows = document.querySelectorAll('#glossaryImpactTableBody tr');
        
        rows.forEach(row => {
            const relationTypeSelect = row.querySelector('select[data-field="relationType"]');
            const glossarySelect = row.querySelector('select[data-field="glossaryId"]');
            
            if (relationTypeSelect?.value && glossarySelect?.value) {
                relationships.push({
                    relationType: parseInt(relationTypeSelect.value),
                    glossaryId: parseInt(glossarySelect.value)
                });
            }
        });
        
        try {
            const response = await fetch('/api/capability-impact/glossaries/save', {
                method: 'POST',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    capabilityId: currentCapabilityId,
                    relationships: relationships
                })
            });
            
            if (!response.ok) {
                return {
                    success: false,
                    message: `Failed to save glossary relationships (HTTP ${response.status})`
                };
            }
            
            const result = await response.json();
            const success = result && result.success === true;
            if (!success) {
                return {
                    success: false,
                    message: result.message || 'Failed to save glossary relationships'
                };
            }
            return true;
            
        } catch (error) {
            console.error('Error saving glossary relationships:', error);
            return {
                success: false,
                message: 'Error saving glossary relationships: ' + (error.message || 'Unknown error')
            };
        }
    }

    // ===== BUSINESS AREA RELATIONSHIPS =====
    
    async function loadBusinessAreaRelationships() {
        try {
            console.log('Loading business area relationships for capability:', currentCapabilityId);

            const response = await fetch(`/api/capability-impact/${currentCapabilityId}/businessareas`, {
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
            console.log('Business area relationships API response:', data);

            businessAreaRelationships = Array.isArray(data) ? data : [];
            originalBusinessAreaData = JSON.parse(JSON.stringify(businessAreaRelationships));

            renderBusinessAreaTable();

        } catch (error) {
            console.error('Error loading business area relationships:', error);
            businessAreaRelationships = [];
            originalBusinessAreaData = [];
            renderBusinessAreaTable();
        }
    }

    async function loadBusinessAreaRelationTypes() {
        try {
            const response = await fetch('/api/capability-impact/businessarea-relation-types', {
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
            businessAreaRelationTypes = Array.isArray(data) ? data : [];
            console.log('Loaded business area relation types:', businessAreaRelationTypes.length);

        } catch (error) {
            console.error('Error loading business area relation types:', error);
            businessAreaRelationTypes = [];
        }
    }

    async function loadBusinessAreas() {
        try {
            const response = await fetch(withImpactSegment('/api/business-areas/'), {
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
            businessAreas = data.success && data.data ? data.data : [];
            console.log('Loaded business areas:', businessAreas.length);

        } catch (error) {
            console.error('Error loading business areas:', error);
            businessAreas = [];
        }
    }

    function renderBusinessAreaTable() {
        const tbody = document.getElementById('businessAreaImpactTableBody');
        const footer = document.getElementById('businessAreaImpactFooter');
        
        if (!tbody) return;

        if (businessAreaRelationships.length === 0) {
            businessAreaRelationships.push({
                id: 'new-empty',
                businessAreaId: null,
                relationType: null,
                businessAreaName: ''
            });
        }

        let html = '';
        businessAreaRelationships.forEach((relationship, index) => {
            const rowId = relationship.id || 'new-' + index;
            html += `
                <tr data-id="${rowId}">
                    <td>
                        <select class="form-control" data-field="relationType">
                            <option value="">Select relationship type</option>
                            ${businessAreaRelationTypes.map(rt => 
                                `<option value="${rt.id || rt.ID}" ${relationship.relationType == (rt.id || rt.ID) ? 'selected' : ''}>${rt.primaryname || rt.primaryName || rt.PrimaryName || 'Unnamed Type'}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td>
                        <select class="form-control" data-field="businessAreaId">
                            <option value="">Select business area</option>
                            ${businessAreas.map(ba => 
                                `<option value="${ba.id || ba.ID}" ${relationship.businessAreaId == (ba.id || ba.ID) ? 'selected' : ''}>${ba.primaryName || ba.PrimaryName || 'Unnamed Business Area'}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td>
                        <div class="action-buttons">
                            <button type="button" class="btn btn-sm btn-success" onclick="addBusinessAreaRow()" title="${window.I18n?.t('impactEdit.buttons.addRow') || 'Add row'}">
                                <i class="fas fa-plus"></i>
                            </button>
                            <button type="button" class="btn btn-sm btn-danger" onclick="deleteBusinessAreaRow('${rowId}')" title="${window.I18n?.t('impactEdit.buttons.deleteRow') || 'Delete row'}">
                                <i class="fas fa-minus"></i>
                            </button>
                        </div>
                    </td>
                </tr>
            `;
        });

        tbody.innerHTML = html;
        if (footer) footer.textContent = `${businessAreaRelationships.length} record${businessAreaRelationships.length !== 1 ? 's' : ''}`;
    }

    function addBusinessAreaRow() {
        saveCurrentBusinessAreaFormData();
        const newRelationship = {
            id: 'new-' + Date.now(),
            relationType: null,
            businessAreaId: null,
            businessAreaName: null
        };
        businessAreaRelationships.push(newRelationship);
        renderBusinessAreaTable();
    }

    function deleteBusinessAreaRow(id) {
        saveCurrentBusinessAreaFormData();
        if (businessAreaRelationships.length <= 1) {
            businessAreaRelationships[0] = { id: 'new-' + Date.now(), relationType: null, businessAreaId: null, businessAreaName: null };
        } else {
            businessAreaRelationships = businessAreaRelationships.filter(rel => String(rel.id) !== String(id));
        }
        renderBusinessAreaTable();
    }

    function saveCurrentBusinessAreaFormData() {
        const rows = document.querySelectorAll('#businessAreaImpactTableBody tr');
        rows.forEach((row, index) => {
            if (businessAreaRelationships[index]) {
                const relationTypeSelect = row.querySelector('select[data-field="relationType"]');
                const businessAreaSelect = row.querySelector('select[data-field="businessAreaId"]');
                
                if (relationTypeSelect) {
                    businessAreaRelationships[index].relationType = relationTypeSelect.value ? parseInt(relationTypeSelect.value) : null;
                }
                if (businessAreaSelect) {
                    businessAreaRelationships[index].businessAreaId = businessAreaSelect.value ? parseInt(businessAreaSelect.value) : null;
                    const selectedOption = businessAreaSelect.options[businessAreaSelect.selectedIndex];
                    if (selectedOption) {
                        businessAreaRelationships[index].businessAreaName = selectedOption.text;
                    }
                }
            }
        });
    }

    async function saveBusinessAreaRelationships() {
        saveCurrentBusinessAreaFormData();
        
        const relationships = [];
        const rows = document.querySelectorAll('#businessAreaImpactTableBody tr');
        
        rows.forEach(row => {
            const relationTypeSelect = row.querySelector('select[data-field="relationType"]');
            const businessAreaSelect = row.querySelector('select[data-field="businessAreaId"]');
            
            if (relationTypeSelect?.value && businessAreaSelect?.value) {
                relationships.push({
                    relationType: parseInt(relationTypeSelect.value),
                    businessAreaId: parseInt(businessAreaSelect.value)
                });
            }
        });
        
        try {
            const response = await fetch('/api/capability-impact/businessareas/save', {
                method: 'POST',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    capabilityId: currentCapabilityId,
                    relationships: relationships
                })
            });
            
            if (!response.ok) {
                return {
                    success: false,
                    message: `Failed to save business area relationships (HTTP ${response.status})`
                };
            }
            
            const result = await response.json();
            const success = result && result.success === true;
            if (!success) {
                return {
                    success: false,
                    message: result.message || 'Failed to save business area relationships'
                };
            }
            return true;
            
        } catch (error) {
            console.error('Error saving business area relationships:', error);
            return {
                success: false,
                message: 'Error saving business area relationships: ' + (error.message || 'Unknown error')
            };
        }
    }

    // ===== LEGAL ENTITY RELATIONSHIPS =====
    
    async function loadLegalRelationships() {
        try {
            console.log('Loading legal relationships for capability:', currentCapabilityId);

            const response = await fetch(`/api/capability-impact/${currentCapabilityId}/legals`, {
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
            console.log('Legal relationships API response:', data);

            legalRelationships = Array.isArray(data) ? data : [];
            originalLegalData = JSON.parse(JSON.stringify(legalRelationships));

            renderLegalTable();

        } catch (error) {
            console.error('Error loading legal relationships:', error);
            legalRelationships = [];
            originalLegalData = [];
            renderLegalTable();
        }
    }

    async function loadLegalRelationTypes() {
        try {
            const response = await fetch('/api/capability-impact/legal-relation-types', {
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
            legalRelationTypes = Array.isArray(data) ? data : [];
            console.log('Loaded legal relation types:', legalRelationTypes.length);

        } catch (error) {
            console.error('Error loading legal relation types:', error);
            legalRelationTypes = [];
        }
    }

    async function loadLegals() {
        try {
            const response = await fetch(withImpactSegment('/api/LegalEntity/hierarchy'), {
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
            legals = Array.isArray(data) ? data : [];
            console.log('Loaded legals:', legals.length);

        } catch (error) {
            console.error('Error loading legals:', error);
            legals = [];
        }
    }

    function renderLegalTable() {
        const tbody = document.getElementById('legalImpactTableBody');
        const footer = document.getElementById('legalImpactFooter');
        
        if (!tbody) return;

        if (legalRelationships.length === 0) {
            legalRelationships.push({
                id: 'new-empty',
                legalId: null,
                relationType: null,
                legalShortName: '',
                legalOwnerName: ''
            });
        }

        let html = '';
        legalRelationships.forEach((relationship, index) => {
            const rowId = relationship.id || 'new-' + index;
            html += `
                <tr data-id="${rowId}">
                    <td>
                        <select class="form-control" data-field="relationType">
                            <option value="">Select relationship type</option>
                            ${legalRelationTypes.map(rt => 
                                `<option value="${rt.id || rt.ID}" ${relationship.relationType == (rt.id || rt.ID) ? 'selected' : ''}>${rt.primaryname || rt.primaryName || rt.PrimaryName || 'Unnamed Type'}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td>
                        <select class="form-control" data-field="legalId" onchange="updateLegalOwner(this)">
                            <option value="">Select legal entity</option>
                            ${legals.map(l => 
                                `<option value="${l.id || l.ID}" ${relationship.legalId == (l.id || l.ID) ? 'selected' : ''}>${l.shortName || l.longName || 'Unnamed Legal Entity'}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td style="text-align: center;">
                        <span class="text-muted">${relationship.legalOwnerName || 'No owner'}</span>
                    </td>
                    <td>
                        <div class="action-buttons">
                            <button type="button" class="btn btn-sm btn-success" onclick="addLegalRow()" title="${window.I18n?.t('impactEdit.buttons.addRow') || 'Add row'}">
                                <i class="fas fa-plus"></i>
                            </button>
                            <button type="button" class="btn btn-sm btn-danger" onclick="deleteLegalRow('${rowId}')" title="${window.I18n?.t('impactEdit.buttons.deleteRow') || 'Delete row'}">
                                <i class="fas fa-minus"></i>
                            </button>
                        </div>
                    </td>
                </tr>
            `;
        });

        tbody.innerHTML = html;
        if (footer) footer.textContent = `${legalRelationships.length} record${legalRelationships.length !== 1 ? 's' : ''}`;
    }

    function addLegalRow() {
        saveCurrentLegalFormData();
        const newRelationship = {
            id: 'new-' + Date.now(),
            relationType: null,
            legalId: null,
            legalShortName: null,
            legalOwnerName: null
        };
        legalRelationships.push(newRelationship);
        renderLegalTable();
    }

    function deleteLegalRow(id) {
        saveCurrentLegalFormData();
        if (legalRelationships.length <= 1) {
            legalRelationships[0] = { id: 'new-' + Date.now(), relationType: null, legalId: null, legalShortName: null, legalOwnerName: null };
        } else {
            legalRelationships = legalRelationships.filter(rel => String(rel.id) !== String(id));
        }
        renderLegalTable();
    }

    function saveCurrentLegalFormData() {
        const rows = document.querySelectorAll('#legalImpactTableBody tr');
        rows.forEach((row, index) => {
            if (legalRelationships[index]) {
                const relationTypeSelect = row.querySelector('select[data-field="relationType"]');
                const legalSelect = row.querySelector('select[data-field="legalId"]');
                const ownerCell = row.querySelector('td:nth-child(3)');
                
                if (relationTypeSelect) {
                    legalRelationships[index].relationType = relationTypeSelect.value ? parseInt(relationTypeSelect.value) : null;
                }
                if (legalSelect) {
                    legalRelationships[index].legalId = legalSelect.value ? parseInt(legalSelect.value) : null;
                    const selectedOption = legalSelect.options[legalSelect.selectedIndex];
                    if (selectedOption) {
                        legalRelationships[index].legalShortName = selectedOption.text;
                    }
                }
                if (ownerCell) {
                    const ownerSpan = ownerCell.querySelector('span');
                    if (ownerSpan) {
                        legalRelationships[index].legalOwnerName = ownerSpan.textContent.trim();
                    }
                }
            }
        });
    }

    async function updateLegalOwner(selectElement) {
        const legalId = selectElement.value;
        const row = selectElement.closest('tr');
        const ownerCell = row.querySelector('td:nth-child(3)');
        
        if (!legalId) {
            ownerCell.innerHTML = '<span class="text-muted">No owner</span>';
            return;
        }
        
        try {
            const response = await fetch(`/api/capability-impact/legal-owner/${legalId}`, {
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
            if (data && data.ownerName) {
                ownerCell.innerHTML = `<span class="text-muted">${data.ownerName}</span>`;
            } else {
                ownerCell.innerHTML = '<span class="text-muted">No owner</span>';
            }
            
        } catch (error) {
            console.error('Error fetching legal owner:', error);
            ownerCell.innerHTML = '<span class="text-muted">Error loading owner</span>';
        }
    }

    async function saveLegalRelationships() {
        saveCurrentLegalFormData();
        
        const relationships = [];
        const rows = document.querySelectorAll('#legalImpactTableBody tr');
        
        rows.forEach(row => {
            const relationTypeSelect = row.querySelector('select[data-field="relationType"]');
            const legalSelect = row.querySelector('select[data-field="legalId"]');
            
            if (relationTypeSelect?.value && legalSelect?.value) {
                relationships.push({
                    relationType: parseInt(relationTypeSelect.value),
                    legalId: parseInt(legalSelect.value)
                });
            }
        });
        
        try {
            const response = await fetch('/api/capability-impact/legals/save', {
                method: 'POST',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    capabilityId: currentCapabilityId,
                    relationships: relationships
                })
            });
            
            if (!response.ok) {
                return {
                    success: false,
                    message: `Failed to save legal relationships (HTTP ${response.status})`
                };
            }
            
            const result = await response.json();
            const success = result && result.success === true;
            if (!success) {
                return {
                    success: false,
                    message: result.message || 'Failed to save legal relationships'
                };
            }
            return true;
            
        } catch (error) {
            console.error('Error saving legal relationships:', error);
            return {
                success: false,
                message: 'Error saving legal relationships: ' + (error.message || 'Unknown error')
            };
        }
    }

    // Save all Impact data
    async function saveAllImpactData() {
        try {
            console.log('=== saveAllImpactData START ===');
            const results = {
                system: await saveSystemRelationships(),
                client: await saveClientRelationships(),
                product: await saveProductRelationships(),
                process: await saveProcessRelationships(),
                glossary: await saveGlossaryRelationships(),
                businessArea: await saveBusinessAreaRelationships(),
                legal: await saveLegalRelationships()
            };
            
            console.log('Save results:', results);
            
            // Check if all succeeded - only consider true as success
            const allSuccess = Object.values(results).every(result => {
                if (typeof result === 'boolean') return result === true;
                if (result && typeof result === 'object') return result.success === true;
                return false;
            });
            
            const failedTabs = [];
            const errorDetails = [];
            
            if (results.system !== true && (typeof results.system !== 'object' || !results.system.success)) {
                failedTabs.push('System');
                if (results.system && typeof results.system === 'object' && results.system.message) {
                    errorDetails.push(`System: ${results.system.message}`);
                }
            }
            if (results.client !== true && (typeof results.client !== 'object' || !results.client.success)) {
                failedTabs.push('Client');
                if (results.client && typeof results.client === 'object' && results.client.message) {
                    errorDetails.push(`Client: ${results.client.message}`);
                }
            }
            if (results.product !== true && (typeof results.product !== 'object' || !results.product.success)) {
                failedTabs.push('Product');
                if (results.product && typeof results.product === 'object' && results.product.message) {
                    errorDetails.push(`Product: ${results.product.message}`);
                }
            }
            if (results.process !== true && (typeof results.process !== 'object' || !results.process.success)) {
                failedTabs.push('Process');
                if (results.process && typeof results.process === 'object' && results.process.message) {
                    errorDetails.push(`Process: ${results.process.message}`);
                }
            }
            if (results.glossary !== true && (typeof results.glossary !== 'object' || !results.glossary.success)) {
                failedTabs.push('Glossary');
                if (results.glossary && typeof results.glossary === 'object' && results.glossary.message) {
                    errorDetails.push(`Glossary: ${results.glossary.message}`);
                }
            }
            if (results.businessArea !== true && (typeof results.businessArea !== 'object' || !results.businessArea.success)) {
                failedTabs.push('Business Area');
                if (results.businessArea && typeof results.businessArea === 'object' && results.businessArea.message) {
                    errorDetails.push(`Business Area: ${results.businessArea.message}`);
                }
            }
            if (results.legal !== true && (typeof results.legal !== 'object' || !results.legal.success)) {
                failedTabs.push('Legal Entity');
                if (results.legal && typeof results.legal === 'object' && results.legal.message) {
                    errorDetails.push(`Legal Entity: ${results.legal.message}`);
                }
            }
            
            if (allSuccess) {
                console.log('=== saveAllImpactData SUCCESS ===');
                return {
                    success: true,
                    message: 'All impact data saved successfully'
                };
            } else {
                console.log('=== saveAllImpactData PARTIAL FAILURE ===');
                return {
                    success: false,
                    failedTabs: failedTabs,
                    message: `Failed to save: ${failedTabs.join(', ')}`,
                    errorDetails: errorDetails
                };
            }
            
        } catch (error) {
            console.error('=== saveAllImpactData ERROR ===');
            console.error('Error saving impact data:', error);
            return {
                success: false,
                message: 'Error saving impact data: ' + (error.message || 'Unknown error')
            };
        }
    }

    // Check if Impact data has changes
    function hasImpactChanges() {
        // Capture current UI values from all subtabs before comparison.
        // Without this, change detection can miss newly selected values
        // and parent save flow may skip impact persistence.
        saveCurrentSystemFormData();
        saveCurrentClientFormData();
        saveCurrentProductFormData();
        saveCurrentProcessFormData();
        saveCurrentGlossaryFormData();
        saveCurrentBusinessAreaFormData();
        saveCurrentLegalFormData();

        const systemChanged = JSON.stringify(systemRelationships) !== JSON.stringify(originalSystemData);
        const clientChanged = JSON.stringify(clientRelationships) !== JSON.stringify(originalClientData);
        const productChanged = JSON.stringify(productRelationships) !== JSON.stringify(originalProductData);
        const processChanged = JSON.stringify(processRelationships) !== JSON.stringify(originalProcessData);
        const glossaryChanged = JSON.stringify(glossaryRelationships) !== JSON.stringify(originalGlossaryData);
        const businessAreaChanged = JSON.stringify(businessAreaRelationships) !== JSON.stringify(originalBusinessAreaData);
        const legalChanged = JSON.stringify(legalRelationships) !== JSON.stringify(originalLegalData);
        
        return systemChanged || clientChanged || productChanged || processChanged || glossaryChanged || businessAreaChanged || legalChanged;
    }

    // Expose functions globally for use by capability-edit.js
    console.log('=== EXPORTING FUNCTIONS TO GLOBAL SCOPE ===');
    window.initImpactEdit = initImpactEdit;
    window.saveAllImpactData = saveAllImpactData;
    window.hasImpactChanges = hasImpactChanges;
    window.addSystemRow = addSystemRow;
    window.addClientRow = addClientRow;
    window.addProductRow = addProductRow;
    window.addProcessRow = addProcessRow;
    window.addGlossaryRow = addGlossaryRow;
    window.addBusinessAreaRow = addBusinessAreaRow;
    window.addLegalRow = addLegalRow;
    window.deleteSystemRow = deleteSystemRow;
    window.deleteClientRow = deleteClientRow;
    window.deleteProductRow = deleteProductRow;
    window.deleteProcessRow = deleteProcessRow;
    window.deleteGlossaryRow = deleteGlossaryRow;
    window.deleteBusinessAreaRow = deleteBusinessAreaRow;
    window.deleteLegalRow = deleteLegalRow;
    window.updateSystemOwner = updateSystemOwner;
    window.updateClientOwner = updateClientOwner;
    window.updateProductOwner = updateProductOwner;
    window.updateProcessOwner = updateProcessOwner;
    window.updateGlossaryOwner = updateGlossaryOwner;
    window.updateGlossaryType = updateGlossaryType;
    window.updateLegalOwner = updateLegalOwner;
    console.log('=== FUNCTIONS EXPORTED ===');

})();
console.log('=== CAPABILITY IMPACT EDIT SCRIPT COMPLETE ===');

