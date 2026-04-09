// Product Impact Edit JavaScript - Implementation for Legal Entity, Client, and Business Area sub-tabs
console.log('=== PRODUCT IMPACT EDIT SCRIPT LOADING ===');

(function() {
    console.log('=== PRODUCT IMPACT EDIT SCRIPT LOADED ===');

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
            '#productSegment',
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
        const oid = parseInt(currentProductId, 10);
        if (Number.isInteger(oid) && oid > 0) {
            const sep2 = result.includes('?') ? '&' : '?';
            result = `${result}${sep2}sourceObjectId=${oid}&sourceObjectType=Product`;
        }
        return result;
    }
    
    // Global variables
    let currentProductId = null;
    let legalRelationships = [];
    let clientRelationships = [];
    let businessAreaRelationships = [];
    
    let originalLegalData = [];
    let originalClientData = [];
    let originalBusinessAreaData = [];
    
    let legalRelationTypes = [];
    let clientRelationTypes = [];
    let businessAreaRelationTypes = [];
    
    let legals = [];
    let clients = [];
    let businessAreas = [];

    // Initialize Impact tab edit functionality
    async function initImpactEdit(productId) {
        console.log('=== INIT IMPACT EDIT CALLED ===');
        console.log('Product ID:', productId);
        currentProductId = productId;
        await loadImpactData();
    }

    // Load all Impact data
    async function loadImpactData() {
        try {
            console.log('=== LOADING IMPACT DATA START ===');
            
            // Load dropdown data first
            await Promise.all([
                loadLegalRelationTypes(),
                loadClientRelationTypes(),
                loadBusinessAreaRelationTypes(),
                loadLegals(),
                loadClients(),
                loadBusinessAreas()
            ]);

            // Load relationship data
            await Promise.all([
                loadLegalRelationships(),
                loadClientRelationships(),
                loadBusinessAreaRelationships()
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
                if (subTabName === 'legal') {
                    targetSubTab = document.getElementById('impactLegalContent');
                } else if (subTabName === 'client') {
                    targetSubTab = document.getElementById('impactClientContent');
                } else if (subTabName === 'businessarea') {
                    targetSubTab = document.getElementById('impactBusinessAreaContent');
                }
                
                if (targetSubTab) {
                    targetSubTab.classList.add('active');
                    targetSubTab.style.display = 'block';
                }
            });
        });
        
        // Activate first sub-tab by default
        if (subTabs.length > 0) {
            subTabs[0].click();
        }
    }

    // ===== LEGAL ENTITY RELATIONSHIPS =====
    
    async function loadLegalRelationships() {
        try {
            const response = await fetch(`/api/product-impact/${currentProductId}/legals`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            legalRelationships = Array.isArray(data) ? data : [];
            renderLegalTable();
            // Update original data AFTER rendering
            originalLegalData = JSON.parse(JSON.stringify(legalRelationships));

        } catch (error) {
            console.error('Error loading legal relationships:', error);
            legalRelationships = [];
            renderLegalTable();
            // Update original data AFTER rendering
            originalLegalData = JSON.parse(JSON.stringify(legalRelationships));
        }
    }

    async function loadLegalRelationTypes() {
        try {
            const response = await fetch('/api/product-impact/legal-relation-types', {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            legalRelationTypes = Array.isArray(data) ? data : [];

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
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            legals = Array.isArray(data) ? data : [];

        } catch (error) {
            console.error('Error loading legals:', error);
            legals = [];
        }
    }

    function renderLegalTable() {
        const tbody = document.querySelector('#legalImpactTableBody');
        const footer = document.querySelector('#legalImpactFooter');
        
        if (!tbody) return;
        
        if (legalRelationships.length === 0) {
            legalRelationships.push({
                id: 'new-empty',
                relationType: null,
                legalId: null,
                legalShortName: null,
                legalLongName: null,
                legalOwnerName: null
            });
        }
        
        let html = '';
        legalRelationships.forEach((relationship, index) => {
            const rowId = relationship.id || 'new-' + index;
            // Show owner only if legal entity is selected - match policy impact behavior
            // If legalId exists, show owner name or "No owner". If no legalId, show empty.
            const ownerDisplay = relationship.legalId ? (relationship.legalOwnerName || 'No owner') : '';
            const legalDisplayName = relationship.legalLongName || relationship.legalShortName || '';
            
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
                                `<option value="${l.id || l.ID}" ${relationship.legalId == (l.id || l.ID) ? 'selected' : ''}>${l.shortName || l.shortname || l.ShortName || l.longName || l.longname || l.LongName || l.name || l.Name || 'Unnamed Legal Entity'}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td><span class="text-muted">${ownerDisplay}</span></td>
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
            legalLongName: null,
            legalOwnerName: null
        };
        legalRelationships.push(newRelationship);
        renderLegalTable();
    }

    function deleteLegalRow(id) {
        // Save current form data before re-rendering
        saveCurrentLegalFormData();
        
        if (legalRelationships.length <= 1) {
            // Clear the first row instead of deleting it
            legalRelationships[0] = {
                id: 'new-' + Date.now(),
                relationType: null,
                legalId: null,
                legalShortName: null,
                legalLongName: null,
                legalOwnerName: null
            };
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
                const ownerCell = row.querySelector('td:nth-child(3)'); // Legal Owner column
                
                if (relationTypeSelect) {
                    legalRelationships[index].relationType = relationTypeSelect.value || null;
                }
                if (legalSelect) {
                    legalRelationships[index].legalId = legalSelect.value || null;
                    // Update legal name based on selected legal entity
                    const selectedOption = legalSelect.options[legalSelect.selectedIndex];
                    if (selectedOption && selectedOption.value) {
                        const selectedLegal = legals.find(l => (l.id || l.ID) == legalSelect.value);
                        if (selectedLegal) {
                            legalRelationships[index].legalShortName = selectedLegal.shortName || selectedLegal.shortname || selectedLegal.ShortName || null;
                            legalRelationships[index].legalLongName = selectedLegal.longName || selectedLegal.longname || selectedLegal.LongName || null;
                        } else {
                            legalRelationships[index].legalShortName = selectedOption.text || null;
                        }
                    } else {
                        legalRelationships[index].legalShortName = null;
                        legalRelationships[index].legalLongName = null;
                    }
                }
                // Preserve the current owner name from the DOM only if legal entity is selected
                if (ownerCell && legalRelationships[index].legalId) {
                    const ownerSpan = ownerCell.querySelector('span');
                    if (ownerSpan) {
                        const ownerText = ownerSpan.textContent.trim();
                        // Only set owner if there's actually text (not empty and not placeholder)
                        if (ownerText && ownerText !== '' && ownerText !== 'No owner') {
                            legalRelationships[index].legalOwnerName = ownerText;
                        } else if (ownerText === 'No owner') {
                            // Explicitly set to null if it says "No owner" (will be shown in render if legalId exists)
                            legalRelationships[index].legalOwnerName = null;
                        } else {
                            legalRelationships[index].legalOwnerName = null;
                        }
                    } else {
                        legalRelationships[index].legalOwnerName = null;
                    }
                } else if (!legalRelationships[index].legalId) {
                    // If no legal entity selected, always clear owner
                    legalRelationships[index].legalOwnerName = null;
                }
            }
        });
    }

    async function updateLegalOwner(selectElement) {
        const legalId = selectElement.value;
        const row = selectElement.closest('tr');
        const ownerCell = row.querySelector('td:nth-child(3)');
        const rowIndex = Array.from(row.parentElement.children).indexOf(row);
        
        if (!legalId) {
            if (ownerCell) ownerCell.innerHTML = '<span class="text-muted"></span>';
            if (legalRelationships[rowIndex]) {
                legalRelationships[rowIndex].legalOwnerName = null;
            }
            return;
        }
        
        try {
            const response = await fetch(`/api/product-impact/legal-owner/${legalId}`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            const ownerName = data.ownerName || data.legalOwnerName || 'No owner';
            ownerCell.innerHTML = `<span class="text-muted">${ownerName}</span>`;
            
            if (legalRelationships[rowIndex]) {
                legalRelationships[rowIndex].legalOwnerName = data.ownerName || data.legalOwnerName || null;
            }
        } catch (error) {
            console.error('Error fetching legal owner:', error);
            ownerCell.innerHTML = '<span class="text-muted">No owner</span>';
        }
    }

    async function saveLegalRelationships() {
        try {
            saveCurrentLegalFormData();
            
            const relationships = legalRelationships
                .filter(r => r.legalId && r.relationType)
                .map(r => ({
                    legalId: parseInt(r.legalId),
                    relationType: parseInt(r.relationType)
                }));

            const requestData = {
                productId: currentProductId,
                relationships: relationships
            };

            const response = await fetch('/api/product-impact/legals/save', {
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
                await loadLegalRelationships();
                // Update original data after reload to match server data structure
                originalLegalData = JSON.parse(JSON.stringify(legalRelationships));
                return true;
            } else {
                return { success: false, message: result.message || 'Failed to save legal relationships' };
            }
        } catch (error) {
            console.error('Error saving legal relationships:', error);
            return { success: false, message: 'Network error: ' + error.message };
        }
    }

    // ===== CLIENT RELATIONSHIPS =====
    
    async function loadClientRelationships() {
        try {
            const response = await fetch(`/api/product-impact/${currentProductId}/clients`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            clientRelationships = Array.isArray(data) ? data : [];
            renderClientTable();
            // Update original data AFTER rendering
            originalClientData = JSON.parse(JSON.stringify(clientRelationships));

        } catch (error) {
            console.error('Error loading client relationships:', error);
            clientRelationships = [];
            renderClientTable();
            // Update original data AFTER rendering
            originalClientData = JSON.parse(JSON.stringify(clientRelationships));
        }
    }

    async function loadClientRelationTypes() {
        try {
            const response = await fetch('/api/product-impact/client-relation-types', {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            clientRelationTypes = Array.isArray(data) ? data : [];

        } catch (error) {
            console.error('Error loading client relation types:', error);
            clientRelationTypes = [];
        }
    }

    async function loadClients() {
        try {
            console.log('=== LOADING CLIENTS START ===');
            console.log('Loading clients from /api/client/...');
            const response = await fetch(withImpactSegment('/api/client/'), {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            console.log('Raw client data received:', data);
            
            // Handle nested data structure
            if (data && data.success && Array.isArray(data.data)) {
                clients = data.data;
                console.log('Loaded clients from nested data:', clients.length);
            } else if (Array.isArray(data)) {
                clients = data;
                console.log('Loaded clients from direct array:', clients.length);
            } else {
                clients = [];
                console.log('No valid client data found');
            }
            
            console.log('Final clients array length:', clients.length);
            if (clients.length > 0) {
                console.log('First client sample:', clients[0]);
                console.log('Client field names:', Object.keys(clients[0]));
            }
            console.log('=== LOADING CLIENTS END ===');

        } catch (error) {
            console.error('=== CLIENT LOADING ERROR ===');
            console.error('Error loading clients:', error);
            clients = [];
        }
    }

    function renderClientTable() {
        const tbody = document.querySelector('#clientImpactTableBody');
        const footer = document.querySelector('#clientImpactFooter');
        
        if (!tbody) return;
        
        if (clientRelationships.length === 0) {
            clientRelationships.push({
                id: 'new-empty',
                relationType: null,
                clientId: null,
                clientName: null,
                clientOwnerName: null
            });
        }
        
        let html = '';
        clientRelationships.forEach((relationship, index) => {
            const rowId = relationship.id || 'new-' + index;
            // Show owner only if client is selected - match policy impact behavior
            // If clientId exists, show owner name or "No owner". If no clientId, show empty.
            const ownerDisplay = relationship.clientId ? (relationship.clientOwnerName || 'No owner') : '';
            
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
                                `<option value="${c.id || c.ID}" ${relationship.clientId == (c.id || c.ID) ? 'selected' : ''}>${c.primary_name || c.primaryname || c.PrimaryName || c.name || c.Name || 'Unnamed Client'}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td><span class="text-muted">${ownerDisplay}</span></td>
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
        // Save current form data before re-rendering
        saveCurrentClientFormData();
        
        if (clientRelationships.length <= 1) {
            // Clear the first row instead of deleting it
            clientRelationships[0] = {
                id: 'new-' + Date.now(),
                relationType: null,
                clientId: null,
                clientName: null,
                clientOwnerName: null
            };
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
                const ownerCell = row.querySelector('td:nth-child(3)'); // Client Owner column
                
                if (relationTypeSelect) {
                    clientRelationships[index].relationType = relationTypeSelect.value || null;
                }
                if (clientSelect) {
                    clientRelationships[index].clientId = clientSelect.value || null;
                    // Update client name based on selected client
                    const selectedOption = clientSelect.options[clientSelect.selectedIndex];
                    if (selectedOption && selectedOption.value) {
                        const selectedClient = clients.find(c => (c.id || c.ID) == clientSelect.value);
                        if (selectedClient) {
                            clientRelationships[index].clientName = selectedClient.primary_name || selectedClient.primaryname || selectedClient.PrimaryName || selectedClient.name || selectedClient.Name || null;
                        } else {
                            clientRelationships[index].clientName = selectedOption.text || null;
                        }
                    } else {
                        clientRelationships[index].clientName = null;
                    }
                }
                // Preserve the current owner name from the DOM only if client is selected
                if (ownerCell && clientRelationships[index].clientId) {
                    const ownerSpan = ownerCell.querySelector('span');
                    if (ownerSpan) {
                        const ownerText = ownerSpan.textContent.trim();
                        // Only set owner if there's actually text (not empty and not placeholder)
                        if (ownerText && ownerText !== '' && ownerText !== 'No owner') {
                            clientRelationships[index].clientOwnerName = ownerText;
                        } else if (ownerText === 'No owner') {
                            // Explicitly set to null if it says "No owner" (will be shown in render if clientId exists)
                            clientRelationships[index].clientOwnerName = null;
                        } else {
                            clientRelationships[index].clientOwnerName = null;
                        }
                    } else {
                        clientRelationships[index].clientOwnerName = null;
                    }
                } else if (!clientRelationships[index].clientId) {
                    // If no client selected, always clear owner
                    clientRelationships[index].clientOwnerName = null;
                }
            }
        });
    }

    async function updateClientOwner(selectElement) {
        const clientId = selectElement.value;
        const row = selectElement.closest('tr');
        const ownerCell = row.querySelector('td:nth-child(3)');
        const rowIndex = Array.from(row.parentElement.children).indexOf(row);
        
        if (!clientId) {
            if (ownerCell) ownerCell.innerHTML = '<span class="text-muted"></span>';
            if (clientRelationships[rowIndex]) {
                clientRelationships[rowIndex].clientOwnerName = null;
            }
            return;
        }
        
        try {
            const response = await fetch(`/api/product-impact/client-owner/${clientId}`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            const ownerName = data.ownerName || data.clientOwnerName || 'No owner';
            ownerCell.innerHTML = `<span class="text-muted">${ownerName}</span>`;
            
            if (clientRelationships[rowIndex]) {
                clientRelationships[rowIndex].clientOwnerName = data.ownerName || data.clientOwnerName || null;
            }
        } catch (error) {
            console.error('Error fetching client owner:', error);
            ownerCell.innerHTML = '<span class="text-muted">No owner</span>';
        }
    }

    async function saveClientRelationships() {
        try {
            saveCurrentClientFormData();
            
            const relationships = clientRelationships
                .filter(r => r.clientId && r.relationType)
                .map(r => ({
                    clientId: parseInt(r.clientId),
                    relationType: parseInt(r.relationType)
                }));

            const requestData = {
                productId: currentProductId,
                relationships: relationships
            };

            const response = await fetch('/api/product-impact/clients/save', {
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
                await loadClientRelationships();
                // Update original data after reload to match server data structure
                originalClientData = JSON.parse(JSON.stringify(clientRelationships));
                return true;
            } else {
                return { success: false, message: result.message || 'Failed to save client relationships' };
            }
        } catch (error) {
            console.error('Error saving client relationships:', error);
            return { success: false, message: 'Network error: ' + error.message };
        }
    }

    // ===== BUSINESS AREA RELATIONSHIPS =====
    
    async function loadBusinessAreaRelationships() {
        try {
            const response = await fetch(`/api/product-impact/${currentProductId}/businessareas`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            // Normalize relationTypeId to relationType for consistency (like project)
            businessAreaRelationships = Array.isArray(data) ? data.map(r => ({
                ...r,
                relationType: r.relationType || r.relationTypeId || null
            })) : [];
            renderBusinessAreaTable();
            // Update original data AFTER rendering
            originalBusinessAreaData = JSON.parse(JSON.stringify(businessAreaRelationships));

        } catch (error) {
            console.error('Error loading business area relationships:', error);
            businessAreaRelationships = [];
            renderBusinessAreaTable();
            // Update original data AFTER rendering
            originalBusinessAreaData = JSON.parse(JSON.stringify(businessAreaRelationships));
        }
    }

    async function loadBusinessAreaRelationTypes() {
        try {
            const response = await fetch('/api/product-impact/businessarea-relation-types', {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            businessAreaRelationTypes = Array.isArray(data) ? data : [];

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
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            // Handle nested data structure
            if (data && data.success && Array.isArray(data.data)) {
                businessAreas = data.data;
            } else if (Array.isArray(data)) {
                businessAreas = data;
            } else {
                businessAreas = [];
            }

        } catch (error) {
            console.error('Error loading business areas:', error);
            businessAreas = [];
        }
    }

    function renderBusinessAreaTable() {
        const tbody = document.querySelector('#businessAreaImpactTableBody');
        const footer = document.querySelector('#businessAreaImpactFooter');
        
        if (!tbody) return;
        
        if (businessAreaRelationships.length === 0) {
            businessAreaRelationships.push({
                id: 'new-empty',
                relationType: null,
                businessAreaId: null,
                businessAreaName: null
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
                                `<option value="${ba.id || ba.ID}" ${relationship.businessAreaId == (ba.id || ba.ID) ? 'selected' : ''}>${ba.primaryName || ba.primary_name || ba.PrimaryName || ba.name || ba.Name || 'Unnamed Business Area'}</option>`
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
        // Save current form data before re-rendering
        saveCurrentBusinessAreaFormData();
        
        if (businessAreaRelationships.length <= 1) {
            // Clear the first row instead of deleting it
            businessAreaRelationships[0] = {
                id: 'new-' + Date.now(),
                relationType: null,
                businessAreaId: null,
                businessAreaName: null
            };
        } else {
            businessAreaRelationships = businessAreaRelationships.filter(rel => String(rel.id) !== String(id));
        }
        renderBusinessAreaTable();
    }

    function saveCurrentBusinessAreaFormData() {
        console.log('=== saveCurrentBusinessAreaFormData called ===');
        const rows = document.querySelectorAll('#businessAreaImpactTableBody tr');
        console.log('Found rows:', rows.length);
        rows.forEach((row, index) => {
            if (businessAreaRelationships[index]) {
                const relationTypeSelect = row.querySelector('select[data-field="relationType"]');
                const businessAreaSelect = row.querySelector('select[data-field="businessAreaId"]');
                
                console.log(`Row ${index} - relationType value:`, relationTypeSelect?.value, 'businessAreaId value:', businessAreaSelect?.value);
                
                if (relationTypeSelect) {
                    businessAreaRelationships[index].relationType = relationTypeSelect.value || null;
                }
                if (businessAreaSelect) {
                    businessAreaRelationships[index].businessAreaId = businessAreaSelect.value || null;
                    // Update business area name based on selected business area
                    const selectedOption = businessAreaSelect.options[businessAreaSelect.selectedIndex];
                    if (selectedOption && selectedOption.value) {
                        const selectedBusinessArea = businessAreas.find(ba => (ba.id || ba.ID) == businessAreaSelect.value);
                        if (selectedBusinessArea) {
                            businessAreaRelationships[index].businessAreaName = selectedBusinessArea.primaryName || selectedBusinessArea.primary_name || selectedBusinessArea.PrimaryName || selectedBusinessArea.name || selectedBusinessArea.Name || null;
                        } else {
                            businessAreaRelationships[index].businessAreaName = selectedOption.text || null;
                        }
                    } else {
                        businessAreaRelationships[index].businessAreaName = null;
                    }
                }
                console.log(`Row ${index} after save:`, businessAreaRelationships[index]);
            }
        });
        console.log('businessAreaRelationships after saveCurrentBusinessAreaFormData:', businessAreaRelationships);
    }


    async function saveBusinessAreaRelationships() {
        try {
            saveCurrentBusinessAreaFormData();
            
            console.log('=== saveBusinessAreaRelationships START ===');
            console.log('businessAreaRelationships before filter:', businessAreaRelationships);
            
            const relationships = businessAreaRelationships
                .filter(r => r.businessAreaId && r.relationType)
                .map(r => ({
                    businessAreaId: parseInt(r.businessAreaId),
                    relationTypeId: parseInt(r.relationType)
                }));

            console.log('Filtered relationships:', relationships);

            const requestData = {
                productId: currentProductId,
                relationships: relationships
            };

            console.log('Request data:', JSON.stringify(requestData, null, 2));

            const response = await fetch('/api/product-impact/businessareas/save', {
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
                await loadBusinessAreaRelationships();
                // Update original data after reload to match server data structure
                originalBusinessAreaData = JSON.parse(JSON.stringify(businessAreaRelationships));
                return true;
            } else {
                return { success: false, message: result.message || 'Failed to save business area relationships' };
            }
        } catch (error) {
            console.error('Error saving business area relationships:', error);
            return { success: false, message: 'Network error: ' + error.message };
        }
    }

    // ===== SAVE ALL IMPACT DATA =====
    
    async function saveAllImpactData(productIdParam) {
        console.log('=== saveAllImpactData called ===');
        const parsedParam = productIdParam != null && productIdParam !== ''
            ? parseInt(productIdParam, 10)
            : NaN;
        const parsedCurrent = currentProductId != null && currentProductId !== ''
            ? parseInt(currentProductId, 10)
            : NaN;
        const productIdToUse = Number.isInteger(parsedParam) && parsedParam > 0
            ? parsedParam
            : (Number.isInteger(parsedCurrent) && parsedCurrent > 0 ? parsedCurrent : null);
        if (!productIdToUse) {
            return {
                success: false,
                message: 'Product ID is required to save impact relationships.',
                failedTabs: ['all'],
                errorDetails: ['Product ID is missing (open the Impact tab once or save from a loaded product).']
            };
        }
        const previousProductId = currentProductId;
        currentProductId = productIdToUse;
        try {
        // IMPORTANT: Save current form data before checking changes or saving
        // This ensures that any changes made in the UI are captured in the data arrays
        saveCurrentLegalFormData();
        saveCurrentClientFormData();
        saveCurrentBusinessAreaFormData();
        
        const results = {
            legal: await saveLegalRelationships(),
            client: await saveClientRelationships(),
            businessArea: await saveBusinessAreaRelationships()
        };
        console.log('saveAllImpactData results:', results);
        
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
        } finally {
            currentProductId = previousProductId;
        }
    }

    function hasImpactChanges() {
        console.log('=== hasImpactChanges called ===');
        
        // IMPORTANT: Save current form data before checking changes
        // This ensures that any changes made in the UI are captured in the data arrays
        saveCurrentLegalFormData();
        saveCurrentClientFormData();
        saveCurrentBusinessAreaFormData();
        
        console.log('legalRelationships:', legalRelationships);
        console.log('originalLegalData:', originalLegalData);
        console.log('clientRelationships:', clientRelationships);
        console.log('originalClientData:', originalClientData);
        console.log('businessAreaRelationships:', businessAreaRelationships);
        console.log('originalBusinessAreaData:', originalBusinessAreaData);
        
        const hasLegalChanges = JSON.stringify(legalRelationships) !== JSON.stringify(originalLegalData);
        const hasClientChanges = JSON.stringify(clientRelationships) !== JSON.stringify(originalClientData);
        const hasBusinessAreaChanges = JSON.stringify(businessAreaRelationships) !== JSON.stringify(originalBusinessAreaData);
        
        console.log('hasLegalChanges:', hasLegalChanges);
        console.log('hasClientChanges:', hasClientChanges);
        console.log('hasBusinessAreaChanges:', hasBusinessAreaChanges);
        
        const hasChanges = hasLegalChanges || hasClientChanges || hasBusinessAreaChanges;
        
        if (hasChanges) {
            console.log('Product Impact Edit: hasImpactChanges detected changes:');
            if (hasLegalChanges) console.log('  - Legal entity relationships changed');
            if (hasClientChanges) console.log('  - Client relationships changed');
            if (hasBusinessAreaChanges) console.log('  - Business area relationships changed');
        } else {
            console.log('Product Impact Edit: No changes detected');
        }
        
        return hasChanges;
    }

    // ===== GLOBAL EXPORTS =====
    
    window.initImpactEdit = initImpactEdit;
    window.saveAllImpactData = saveAllImpactData;
    window.hasImpactChanges = hasImpactChanges;
    
    window.addLegalRow = addLegalRow;
    window.deleteLegalRow = deleteLegalRow;
    window.updateLegalOwner = updateLegalOwner;
    
    window.addClientRow = addClientRow;
    window.deleteClientRow = deleteClientRow;
    window.updateClientOwner = updateClientOwner;
    
    window.addBusinessAreaRow = addBusinessAreaRow;
    window.deleteBusinessAreaRow = deleteBusinessAreaRow;

})();

