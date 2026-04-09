// System Impact Edit JavaScript - Implementation for Product (special), Client, and Legal Entity sub-tabs
console.log('=== SYSTEM IMPACT EDIT SCRIPT LOADING ===');

(function() {
    console.log('=== SYSTEM IMPACT EDIT SCRIPT LOADED ===');

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
            '#systemSegment',
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
        const sid = parseInt(currentSystemId, 10);
        if (Number.isInteger(sid) && sid > 0) {
            const sep2 = result.includes('?') ? '&' : '?';
            result = `${result}${sep2}sourceObjectId=${sid}&sourceObjectType=System`;
        }
        return result;
    }
    
    // Global variables
    let currentSystemId = null;
    let currentViewMode = 'original'; // 'original' | 'changes'
    let productRelationships = [];
    let clientRelationships = [];
    let legalRelationships = [];
    
    let originalProductData = [];
    let originalClientData = [];
    let originalLegalData = [];
    
    let productSystemRelationTypes = [];
    let productLegalRelationTypes = [];
    let clientRelationTypes = [];
    let legalRelationTypes = [];
    
    let products = [];
    let clients = [];
    let legals = [];

    // Initialize Impact tab edit functionality
    function initImpactEdit(systemId, viewMode = 'original') {
        console.log('=== INIT IMPACT EDIT CALLED ===');
        console.log('System ID:', systemId);
        currentSystemId = systemId;
        currentViewMode = viewMode || 'original';
        loadImpactData();
    }

    function getViewParam() {
        return currentViewMode === 'changes' ? '?view=changes' : '';
    }

    // Load all Impact data
    async function loadImpactData() {
        try {
            console.log('=== LOADING IMPACT DATA START ===');
            
            // Load dropdown data first
            await Promise.all([
                loadProductSystemRelationTypes(),
                loadProductLegalRelationTypes(),
                loadClientRelationTypes(),
                loadLegalRelationTypes(),
                loadProducts(),
                loadClients(),
                loadLegals()
            ]);

            // Load relationship data
            await Promise.all([
                loadProductRelationships(),
                loadClientRelationships(),
                loadLegalRelationships()
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
                if (subTabName === 'product') {
                    targetSubTab = document.getElementById('impactProductContent');
                } else if (subTabName === 'client') {
                    targetSubTab = document.getElementById('impactClientContent');
                } else if (subTabName === 'legal') {
                    targetSubTab = document.getElementById('impactLegalContent');
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

    // ===== PRODUCT RELATIONSHIPS (SPECIAL - includes Legal Entity relationship) =====
    
    async function loadProductRelationships() {
        try {
            const response = await fetch(`/api/system-impact/${currentSystemId}/products${getViewParam()}`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            productRelationships = Array.isArray(data) ? data : [];
            renderProductTable();
            // Update original data AFTER rendering
            originalProductData = JSON.parse(JSON.stringify(productRelationships));

        } catch (error) {
            console.error('Error loading product relationships:', error);
            productRelationships = [];
            renderProductTable();
            // Update original data AFTER rendering
            originalProductData = JSON.parse(JSON.stringify(productRelationships));
        }
    }

    async function loadProductSystemRelationTypes() {
        try {
            const response = await fetch('/api/system-impact/product-system-relation-types', {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            productSystemRelationTypes = Array.isArray(data) ? data : [];

        } catch (error) {
            console.error('Error loading product-system relation types:', error);
            productSystemRelationTypes = [];
        }
    }

    async function loadProductLegalRelationTypes() {
        try {
            const response = await fetch('/api/system-impact/product-legal-relation-types', {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            productLegalRelationTypes = Array.isArray(data) ? data : [];

        } catch (error) {
            console.error('Error loading product-legal relation types:', error);
            productLegalRelationTypes = [];
        }
    }

    async function loadProducts() {
        try {
            const response = await fetch(withImpactSegment('/api/product/list'), {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            products = Array.isArray(data) ? data : [];

        } catch (error) {
            console.error('Error loading products:', error);
            products = [];
        }
    }

    function renderProductTable() {
        const tbody = document.querySelector('#productImpactTableBody');
        const footer = document.querySelector('#productImpactFooter');
        
        if (!tbody) return;
        
        if (productRelationships.length === 0) {
            productRelationships.push({
                id: 'new-empty',
                productSystemRelationType: null,
                productId: null,
                productName: null,
                productOwnerName: null,
                legalRelationType: null,
                legalId: null,
                legalShortName: null,
                legalLongName: null
            });
        }
        
        let html = '';
        productRelationships.forEach((relationship, index) => {
            const rowId = relationship.id || 'new-' + index;
            // Show owner only if product is selected - match policy impact behavior
            // If productId exists, show owner name or "No owner". If no productId, show empty.
            const ownerDisplay = relationship.productId ? (relationship.productOwnerName || 'No owner') : '';
            const legalDisplay = relationship.legalLongName || relationship.legalShortName || '';
            
            html += `
                <tr data-id="${rowId}">
                    <td>
                        <select class="form-control" data-field="productSystemRelationType">
                            <option value="">Select relationship type</option>
                            ${productSystemRelationTypes.map(rt => 
                                `<option value="${rt.id || rt.ID}" ${relationship.productSystemRelationType == (rt.id || rt.ID) ? 'selected' : ''}>${rt.primaryname || rt.primaryName || rt.PrimaryName || 'Unnamed Type'}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td>
                        <select class="form-control" data-field="productId" onchange="updateProductOwner(this)">
                            <option value="">Select product</option>
                            ${products.map(p => 
                                `<option value="${p.id || p.ID}" ${relationship.productId == (p.id || p.ID) ? 'selected' : ''}>${p.primaryname || p.PrimaryName || p.name || p.Name || 'Unnamed Product'}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td><span class="text-muted">${ownerDisplay}</span></td>
                    <td>
                        <select class="form-control" data-field="legalRelationType">
                            <option value="">Select legal relationship type</option>
                            ${productLegalRelationTypes.map(rt => 
                                `<option value="${rt.id || rt.ID}" ${relationship.legalRelationType == (rt.id || rt.ID) ? 'selected' : ''}>${rt.primaryname || rt.primaryName || rt.PrimaryName || 'Unnamed Type'}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td>
                        <select class="form-control" data-field="legalId">
                            <option value="">Select legal entity</option>
                            ${legals.map(l => 
                                `<option value="${l.id || l.ID}" ${relationship.legalId == (l.id || l.ID) ? 'selected' : ''}>${l.shortName || l.shortname || l.ShortName || l.longName || l.longname || l.LongName || 'Unnamed Legal Entity'}</option>`
                            ).join('')}
                        </select>
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
            productSystemRelationType: null,
            productId: null,
            productName: null,
            productOwnerName: null,
            legalRelationType: null,
            legalId: null,
            legalShortName: null,
            legalLongName: null
        };
        productRelationships.push(newRelationship);
        renderProductTable();
    }

    function deleteProductRow(id) {
        // Save current form data before re-rendering
        saveCurrentProductFormData();
        
        if (productRelationships.length <= 1) {
            // Clear the first row instead of deleting it
            productRelationships[0] = {
                id: 'new-' + Date.now(),
                productSystemRelationType: null,
                productId: null,
                productName: null,
                productOwnerName: null,
                legalRelationType: null,
                legalId: null,
                legalShortName: null,
                legalLongName: null
            };
        } else {
            productRelationships = productRelationships.filter(rel => String(rel.id) !== String(id));
        }
        renderProductTable();
    }

    function saveCurrentProductFormData() {
        const rows = document.querySelectorAll('#productImpactTableBody tr');
        rows.forEach((row, index) => {
            if (productRelationships[index]) {
                const productSystemRelationTypeSelect = row.querySelector('select[data-field="productSystemRelationType"]');
                const productSelect = row.querySelector('select[data-field="productId"]');
                const legalRelationTypeSelect = row.querySelector('select[data-field="legalRelationType"]');
                const legalIdSelect = row.querySelector('select[data-field="legalId"]');
                const ownerCell = row.querySelector('td:nth-child(3)'); // Product Owner column
                
                if (productSystemRelationTypeSelect) {
                    productRelationships[index].productSystemRelationType = productSystemRelationTypeSelect.value || null;
                }
                if (productSelect) {
                    productRelationships[index].productId = productSelect.value || null;
                    // Update product name based on selected product
                    const selectedOption = productSelect.options[productSelect.selectedIndex];
                    if (selectedOption && selectedOption.value) {
                        const selectedProduct = products.find(p => (p.id || p.ID) == productSelect.value);
                        if (selectedProduct) {
                            productRelationships[index].productName = selectedProduct.primaryname || selectedProduct.PrimaryName || selectedProduct.name || selectedProduct.Name || null;
                        } else {
                            productRelationships[index].productName = selectedOption.text || null;
                        }
                    } else {
                        productRelationships[index].productName = null;
                    }
                }
                // Preserve the current owner name from the DOM only if product is selected
                if (ownerCell && productRelationships[index].productId) {
                    const ownerSpan = ownerCell.querySelector('span');
                    if (ownerSpan) {
                        const ownerText = ownerSpan.textContent.trim();
                        // Only set owner if there's actually text (not empty and not placeholder)
                        if (ownerText && ownerText !== '' && ownerText !== 'No owner') {
                            productRelationships[index].productOwnerName = ownerText;
                        } else if (ownerText === 'No owner') {
                            // Explicitly set to null if it says "No owner" (will be shown in render if productId exists)
                            productRelationships[index].productOwnerName = null;
                        } else {
                            productRelationships[index].productOwnerName = null;
                        }
                    } else {
                        productRelationships[index].productOwnerName = null;
                    }
                } else if (!productRelationships[index].productId) {
                    // If no product selected, always clear owner
                    productRelationships[index].productOwnerName = null;
                }
                if (legalRelationTypeSelect) {
                    productRelationships[index].legalRelationType = legalRelationTypeSelect.value || null;
                }
                if (legalIdSelect) {
                    productRelationships[index].legalId = legalIdSelect.value || null;
                    
                    // Update legal name if legal changed
                    if (productRelationships[index].legalId) {
                        const selectedLegal = legals.find(l => (l.id || l.ID) == productRelationships[index].legalId);
                        if (selectedLegal) {
                            productRelationships[index].legalShortName = selectedLegal.shortName || selectedLegal.shortname || selectedLegal.ShortName || null;
                            productRelationships[index].legalLongName = selectedLegal.longName || selectedLegal.longname || selectedLegal.LongName || null;
                        }
                    } else {
                        productRelationships[index].legalShortName = null;
                        productRelationships[index].legalLongName = null;
                    }
                }
            }
        });
    }

    async function updateProductOwner(selectElement) {
        const productId = selectElement.value;
        const row = selectElement.closest('tr');
        const ownerCell = row.querySelector('td:nth-child(3)');
        const rowIndex = Array.from(row.parentElement.children).indexOf(row);
        
        if (!productId) {
            if (ownerCell) ownerCell.innerHTML = '<span class="text-muted"></span>';
            if (productRelationships[rowIndex]) {
                productRelationships[rowIndex].productOwnerName = null;
            }
            return;
        }
        
        try {
            const response = await fetch(`/api/system-impact/product-owner/${productId}`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            const ownerName = data.ownerName || data.productOwnerName || 'No owner';
            ownerCell.innerHTML = `<span class="text-muted">${ownerName}</span>`;
            
            if (productRelationships[rowIndex]) {
                productRelationships[rowIndex].productOwnerName = data.ownerName || data.productOwnerName || null;
            }
        } catch (error) {
            console.error('Error fetching product owner:', error);
            ownerCell.innerHTML = '<span class="text-muted">No owner</span>';
        }
    }

    async function saveProductRelationships() {
        try {
            saveCurrentProductFormData();
            
            const relationships = productRelationships
                .filter(r => r.productId && r.productSystemRelationType)
                .map(r => ({
                    productId: parseInt(r.productId),
                    productSystemRelationType: parseInt(r.productSystemRelationType),
                    legalId: r.legalId ? parseInt(r.legalId) : null,
                    legalRelationType: r.legalRelationType ? parseInt(r.legalRelationType) : null
                }));

            const requestData = {
                systemId: currentSystemId,
                relationships: relationships
            };

            const response = await fetch('/api/system-impact/products/save', {
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
                await loadProductRelationships();
                // Update original data after reload to match server data structure
                originalProductData = JSON.parse(JSON.stringify(productRelationships));
                return true;
            } else {
                return { success: false, message: result.message || 'Failed to save product relationships' };
            }
        } catch (error) {
            console.error('Error saving product relationships:', error);
            return { success: false, message: 'Network error: ' + error.message };
        }
    }

    // ===== CLIENT RELATIONSHIPS =====
    
    async function loadClientRelationships() {
        try {
            const response = await fetch(`/api/system-impact/${currentSystemId}/clients${getViewParam()}`, {
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
            const response = await fetch('/api/system-impact/client-relation-types', {
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
                                `<option value="${c.id || c.ID}" ${relationship.clientId == (c.id || c.ID) ? 'selected' : ''}>${c.primary_name || c.PrimaryName || c.name || c.Name || 'Unnamed Client'}</option>`
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
                            clientRelationships[index].clientName = selectedClient.primary_name || selectedClient.PrimaryName || selectedClient.name || selectedClient.Name || null;
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
            const response = await fetch(`/api/system-impact/client-owner/${clientId}`, {
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
                systemId: currentSystemId,
                relationships: relationships
            };

            const response = await fetch('/api/system-impact/clients/save', {
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
                
                if (response.status === 401) {
                    console.error('Authentication failed - session may have expired. Please refresh the page or log in again.');
                    throw new Error(`Authentication failed (HTTP ${response.status}). Your session may have expired. Please refresh the page or log in again.`);
                }
                
                throw new Error(`HTTP error! status: ${response.status}, message: ${errorText}`);
            }

            const result = await response.json();
            
            if (result.success) {
                await loadClientRelationships();
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

    // ===== LEGAL ENTITY RELATIONSHIPS =====
    
    async function loadLegalRelationships() {
        try {
            const response = await fetch(`/api/system-impact/${currentSystemId}/legals${getViewParam()}`, {
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
            const response = await fetch('/api/system-impact/legal-relation-types', {
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
                                `<option value="${l.id || l.ID}" ${relationship.legalId == (l.id || l.ID) ? 'selected' : ''}>${l.shortName || l.shortname || l.ShortName || l.longName || l.longname || l.LongName || 'Unnamed Legal Entity'}</option>`
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
            const response = await fetch(`/api/system-impact/legal-owner/${legalId}`, {
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
                systemId: currentSystemId,
                relationships: relationships
            };

            const response = await fetch('/api/system-impact/legals/save', {
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
                
                if (response.status === 401) {
                    console.error('Authentication failed - session may have expired. Please refresh the page or log in again.');
                    throw new Error(`Authentication failed (HTTP ${response.status}). Your session may have expired. Please refresh the page or log in again.`);
                }
                
                throw new Error(`HTTP error! status: ${response.status}, message: ${errorText}`);
            }

            const result = await response.json();
            
            if (result.success) {
                await loadLegalRelationships();
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

    // ===== VALIDATION =====

    function showImpactMessage(message, isError = false) {
        const existingMessage = document.getElementById('impact-validation-message');
        if (existingMessage) existingMessage.remove();

        const div = document.createElement('div');
        div.id = 'impact-validation-message';
        div.style.cssText = `
            position: fixed;
            top: 80px;
            left: 50%;
            transform: translateX(-50%);
            background-color: ${isError ? '#ef4444' : '#248567'};
            color: white;
            padding: 12px 24px;
            border-radius: 6px;
            font-size: 14px;
            font-weight: 500;
            z-index: 10000;
            box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
            animation: slideDown 0.3s ease-out;
            max-width: 600px;
            text-align: center;
            line-height: 1.5;
        `;
        div.textContent = message;
        document.body.appendChild(div);

        setTimeout(() => {
            div.style.transition = 'opacity 0.5s ease-out';
            div.style.opacity = '0';
            setTimeout(() => div.remove(), 500);
        }, 4000);
    }

    function validateImpactData() {
        const errors = [];

        // Validate product relationships
        saveCurrentProductFormData();
        productRelationships.forEach((r, i) => {
            // Skip completely empty rows
            if (!r.productId && !r.productSystemRelationType && !r.legalId && !r.legalRelationType) return;
            if (!r.productSystemRelationType) {
                errors.push(`Product row ${i + 1}: Relationship Type is required`);
            }
            if (!r.productId) {
                errors.push(`Product row ${i + 1}: Product Name is required`);
            }
        });

        // Validate client relationships
        saveCurrentClientFormData();
        clientRelationships.forEach((r, i) => {
            // Skip completely empty rows
            if (!r.clientId && !r.relationType) return;
            if (!r.relationType) {
                errors.push(`Client row ${i + 1}: Relationship Type is required`);
            }
            if (!r.clientId) {
                errors.push(`Client row ${i + 1}: Client is required`);
            }
        });

        // Validate legal entity relationships
        saveCurrentLegalFormData();
        legalRelationships.forEach((r, i) => {
            // Skip completely empty rows
            if (!r.legalId && !r.relationType) return;
            if (!r.relationType) {
                errors.push(`Legal Entity row ${i + 1}: Relationship Type is required`);
            }
            if (!r.legalId) {
                errors.push(`Legal Entity row ${i + 1}: Legal Entity is required`);
            }
        });

        return errors;
    }

    function highlightInvalidFields() {
        // Clear previous highlights
        document.querySelectorAll('#impact select.is-invalid').forEach(el => {
            el.classList.remove('is-invalid');
            el.style.borderColor = '';
        });

        // Product rows
        document.querySelectorAll('#productImpactTableBody tr').forEach((row, i) => {
            const rel = productRelationships[i];
            if (!rel) return;
            // Skip completely empty rows
            if (!rel.productId && !rel.productSystemRelationType && !rel.legalId && !rel.legalRelationType) return;
            const typeSelect = row.querySelector('select[data-field="productSystemRelationType"]');
            const nameSelect = row.querySelector('select[data-field="productId"]');
            if (typeSelect && !typeSelect.value) { typeSelect.classList.add('is-invalid'); typeSelect.style.borderColor = '#dc2626'; }
            if (nameSelect && !nameSelect.value) { nameSelect.classList.add('is-invalid'); nameSelect.style.borderColor = '#dc2626'; }
        });

        // Client rows
        document.querySelectorAll('#clientImpactTableBody tr').forEach((row, i) => {
            const rel = clientRelationships[i];
            if (!rel) return;
            if (!rel.clientId && !rel.relationType) return;
            const typeSelect = row.querySelector('select[data-field="relationType"]');
            const nameSelect = row.querySelector('select[data-field="clientId"]');
            if (typeSelect && !typeSelect.value) { typeSelect.classList.add('is-invalid'); typeSelect.style.borderColor = '#dc2626'; }
            if (nameSelect && !nameSelect.value) { nameSelect.classList.add('is-invalid'); nameSelect.style.borderColor = '#dc2626'; }
        });

        // Legal rows
        document.querySelectorAll('#legalImpactTableBody tr').forEach((row, i) => {
            const rel = legalRelationships[i];
            if (!rel) return;
            if (!rel.legalId && !rel.relationType) return;
            const typeSelect = row.querySelector('select[data-field="relationType"]');
            const nameSelect = row.querySelector('select[data-field="legalId"]');
            if (typeSelect && !typeSelect.value) { typeSelect.classList.add('is-invalid'); typeSelect.style.borderColor = '#dc2626'; }
            if (nameSelect && !nameSelect.value) { nameSelect.classList.add('is-invalid'); nameSelect.style.borderColor = '#dc2626'; }
        });
    }

    // ===== SAVE ALL IMPACT DATA =====
    
    async function saveAllImpactData() {
        // Validate before saving
        const validationErrors = validateImpactData();
        if (validationErrors.length > 0) {
            highlightInvalidFields();
            showImpactMessage('Relationship Type and Name are required for all impact rows', true);
            return { success: false, message: 'Validation failed: ' + validationErrors.join('; ') };
        }

        const results = {
            product: await saveProductRelationships(),
            client: await saveClientRelationships(),
            legal: await saveLegalRelationships()
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
        const hasProductChanges = JSON.stringify(productRelationships) !== JSON.stringify(originalProductData);
        const hasClientChanges = JSON.stringify(clientRelationships) !== JSON.stringify(originalClientData);
        const hasLegalChanges = JSON.stringify(legalRelationships) !== JSON.stringify(originalLegalData);
        
        const hasChanges = hasProductChanges || hasClientChanges || hasLegalChanges;
        
        if (hasChanges) {
            console.log('System Impact Edit: hasImpactChanges detected changes:');
            if (hasProductChanges) console.log('  - Product relationships changed');
            if (hasClientChanges) console.log('  - Client relationships changed');
            if (hasLegalChanges) console.log('  - Legal relationships changed');
        }
        
        return hasChanges;
    }

    // ===== ACTIVE SUBTAB: get, validate, save, hasChanges =====

    function getActiveImpactSubTab() {
        const activeSubTab = document.querySelector('#impact .sub-tab.active');
        if (!activeSubTab) return 'product';
        const name = (activeSubTab.getAttribute('data-sub-tab') || 'product').toLowerCase();
        if (name === 'client' || name === 'legal') return name;
        return 'product';
    }

    function validateImpactDataForSubTab(subTab) {
        const errors = [];
        if (subTab === 'product') {
            saveCurrentProductFormData();
            productRelationships.forEach((r, i) => {
                if (!r.productId && !r.productSystemRelationType && !r.legalId && !r.legalRelationType) return;
                if (!r.productSystemRelationType) errors.push(`Product row ${i + 1}: Relationship Type is required`);
                if (!r.productId) errors.push(`Product row ${i + 1}: Product Name is required`);
            });
        } else if (subTab === 'client') {
            saveCurrentClientFormData();
            clientRelationships.forEach((r, i) => {
                if (!r.clientId && !r.relationType) return;
                if (!r.relationType) errors.push(`Client row ${i + 1}: Relationship Type is required`);
                if (!r.clientId) errors.push(`Client row ${i + 1}: Client is required`);
            });
        } else if (subTab === 'legal') {
            saveCurrentLegalFormData();
            legalRelationships.forEach((r, i) => {
                if (!r.legalId && !r.relationType) return;
                if (!r.relationType) errors.push(`Legal Entity row ${i + 1}: Relationship Type is required`);
                if (!r.legalId) errors.push(`Legal Entity row ${i + 1}: Legal Entity is required`);
            });
        }
        return errors;
    }

    function hasImpactChangesForActiveSubTab() {
        const subTab = getActiveImpactSubTab();
        if (subTab === 'product') return JSON.stringify(productRelationships) !== JSON.stringify(originalProductData);
        if (subTab === 'client') return JSON.stringify(clientRelationships) !== JSON.stringify(originalClientData);
        if (subTab === 'legal') return JSON.stringify(legalRelationships) !== JSON.stringify(originalLegalData);
        return false;
    }

    function highlightInvalidFieldsForSubTab(subTab) {
        document.querySelectorAll('#impact select.is-invalid').forEach(el => {
            el.classList.remove('is-invalid');
            el.style.borderColor = '';
        });
        if (subTab === 'product') {
            document.querySelectorAll('#productImpactTableBody tr').forEach((row, i) => {
                const rel = productRelationships[i];
                if (!rel) return;
                if (!rel.productId && !rel.productSystemRelationType && !rel.legalId && !rel.legalRelationType) return;
                const typeSelect = row.querySelector('select[data-field="productSystemRelationType"]');
                const nameSelect = row.querySelector('select[data-field="productId"]');
                if (typeSelect && !typeSelect.value) { typeSelect.classList.add('is-invalid'); typeSelect.style.borderColor = '#dc2626'; }
                if (nameSelect && !nameSelect.value) { nameSelect.classList.add('is-invalid'); nameSelect.style.borderColor = '#dc2626'; }
            });
        } else if (subTab === 'client') {
            document.querySelectorAll('#clientImpactTableBody tr').forEach((row, i) => {
                const rel = clientRelationships[i];
                if (!rel) return;
                if (!rel.clientId && !rel.relationType) return;
                const typeSelect = row.querySelector('select[data-field="relationType"]');
                const nameSelect = row.querySelector('select[data-field="clientId"]');
                if (typeSelect && !typeSelect.value) { typeSelect.classList.add('is-invalid'); typeSelect.style.borderColor = '#dc2626'; }
                if (nameSelect && !nameSelect.value) { nameSelect.classList.add('is-invalid'); nameSelect.style.borderColor = '#dc2626'; }
            });
        } else if (subTab === 'legal') {
            document.querySelectorAll('#legalImpactTableBody tr').forEach((row, i) => {
                const rel = legalRelationships[i];
                if (!rel) return;
                if (!rel.legalId && !rel.relationType) return;
                const typeSelect = row.querySelector('select[data-field="relationType"]');
                const nameSelect = row.querySelector('select[data-field="legalId"]');
                if (typeSelect && !typeSelect.value) { typeSelect.classList.add('is-invalid'); typeSelect.style.borderColor = '#dc2626'; }
                if (nameSelect && !nameSelect.value) { nameSelect.classList.add('is-invalid'); nameSelect.style.borderColor = '#dc2626'; }
            });
        }
    }

    async function saveImpactDataForActiveSubTab() {
        const subTab = getActiveImpactSubTab();
        const validationErrors = validateImpactDataForSubTab(subTab);
        if (validationErrors.length > 0) {
            highlightInvalidFieldsForSubTab(subTab);
            showImpactMessage('Relationship Type and Name are required for all impact rows', true);
            return { success: false, message: 'Validation failed: ' + validationErrors.join('; ') };
        }
        let result;
        if (subTab === 'product') {
            result = await saveProductRelationships();
        } else if (subTab === 'client') {
            result = await saveClientRelationships();
        } else {
            result = await saveLegalRelationships();
        }
        if (result === true) return { success: true };
        if (result && result.success === false) return result;
        return { success: true };
    }

    // ===== GLOBAL EXPORTS =====
    
    window.initImpactEdit = initImpactEdit;
    window.saveAllImpactData = saveAllImpactData;
    window.hasImpactChanges = hasImpactChanges;
    window.getActiveImpactSubTab = getActiveImpactSubTab;
    window.validateImpactDataForSubTab = validateImpactDataForSubTab;
    window.hasImpactChangesForActiveSubTab = hasImpactChangesForActiveSubTab;
    window.saveImpactDataForActiveSubTab = saveImpactDataForActiveSubTab;
    
    window.addProductRow = addProductRow;
    window.deleteProductRow = deleteProductRow;
    window.updateProductOwner = updateProductOwner;
    
    window.addClientRow = addClientRow;
    window.deleteClientRow = deleteClientRow;
    window.updateClientOwner = updateClientOwner;
    
    window.addLegalRow = addLegalRow;
    window.deleteLegalRow = deleteLegalRow;
    window.updateLegalOwner = updateLegalOwner;

})();

