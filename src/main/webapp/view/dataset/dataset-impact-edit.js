// Dataset Impact Edit JavaScript - Implementation for Product, Client, and Legal Entity sub-tabs
console.log('=== DATASET IMPACT EDIT SCRIPT LOADING ===');

(function() {
    console.log('=== DATASET IMPACT EDIT SCRIPT LOADED ===');

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
            '#datasetSegment',
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
        const oid = parseInt(currentDatasetId, 10);
        if (Number.isInteger(oid) && oid > 0) {
            const sep2 = result.includes('?') ? '&' : '?';
            result = `${result}${sep2}sourceObjectId=${oid}&sourceObjectType=Dataset`;
        }
        return result;
    }
    
    // Global variables
    let currentDatasetId = null;
    let currentViewMode = 'original'; // 'original' | 'changes'
    let productRelationships = [];
    let clientRelationships = [];
    let legalRelationships = [];
    
    let originalProductData = [];
    let originalClientData = [];
    let originalLegalData = [];
    
    let productRelationTypes = [];
    let clientRelationTypes = [];
    let legalRelationTypes = [];
    
    let products = [];
    let clients = [];
    let legals = [];

    // Initialize Impact tab edit functionality
    function initImpactEdit(datasetId, viewMode = 'original') {
        console.log('=== INIT IMPACT EDIT CALLED ===');
        console.log('Dataset ID:', datasetId);
        currentDatasetId = datasetId;
        currentViewMode = viewMode || 'original';
        loadImpactData();
    }

    // Load all Impact data
    async function loadImpactData() {
        try {
            console.log('=== LOADING IMPACT DATA START ===');
            
            // Load dropdown data first
            await Promise.all([
                loadProductRelationTypes(),
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

    // ===== PRODUCT RELATIONSHIPS =====
    
    async function loadProductRelationships() {
        try {
            const viewParam = currentViewMode === 'changes' ? '?view=changes' : '';
            const response = await fetch(`/api/dataset-impact/${currentDatasetId}/products${viewParam}`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            productRelationships = Array.isArray(data) ? data : [];
            renderProductTable();
            // Update original data AFTER rendering (rendering may modify productRelationships with placeholder rows)
            originalProductData = JSON.parse(JSON.stringify(productRelationships));

        } catch (error) {
            console.error('Error loading product relationships:', error);
            productRelationships = [];
            renderProductTable();
            // Update original data AFTER rendering
            originalProductData = JSON.parse(JSON.stringify(productRelationships));
        }
    }

    async function loadProductRelationTypes() {
        try {
            const response = await fetch('/api/dataset-impact/product-relation-types', {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            productRelationTypes = Array.isArray(data) ? data : [];

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
                relationType: null,
                productId: null,
                productName: null,
                productRefNumber: null,
                productOwnerName: null
            });
        }
        
        let html = '';
        productRelationships.forEach((relationship, index) => {
            const rowId = relationship.id || 'new-' + index;
            const isNewRow = !relationship.id || String(relationship.id).startsWith('new-');
            const ownerDisplay = isNewRow && !relationship.productId ? '' : (relationship.productOwnerName || 'No owner');
            
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
                                `<option value="${p.id || p.ID}" ${relationship.productId == (p.id || p.ID) ? 'selected' : ''}>${p.primaryname || p.PrimaryName || p.name || p.Name || 'Unnamed Product'}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td><span class="text-muted">${relationship.productRefNumber || ''}</span></td>
                    <td><span class="text-muted">${ownerDisplay}</span></td>
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
            productRefNumber: null,
            productOwnerName: null
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
                relationType: null,
                productId: null,
                productName: null,
                productRefNumber: null,
                productOwnerName: null
            };
        } else {
            productRelationships = productRelationships.filter(rel => String(rel.id) !== String(id));
        }
        renderProductTable();
    }

    function saveCurrentProductFormData() {
        const tbody = document.querySelector('#productImpactTableBody');
        if (!tbody) return;
        
        const rows = tbody.querySelectorAll('tr');
        rows.forEach((row, index) => {
            if (productRelationships[index]) {
                const relationTypeSelect = row.querySelector('[data-field="relationType"]');
                const productIdSelect = row.querySelector('[data-field="productId"]');
                
                if (relationTypeSelect) {
                    productRelationships[index].relationType = relationTypeSelect.value || null;
                }
                if (productIdSelect) {
                    const previousProductId = productRelationships[index].productId;
                    productRelationships[index].productId = productIdSelect.value || null;
                    
                    // Update product ref if product changed
                    if (productRelationships[index].productId) {
                        const selectedProduct = products.find(p => (p.id || p.ID) == productRelationships[index].productId);
                        if (selectedProduct) {
                            productRelationships[index].productRefNumber = selectedProduct.refnumber || selectedProduct.refNumber || selectedProduct.RefNumber || '';
                        }
                    } else {
                        productRelationships[index].productRefNumber = null;
                    }
                    
                    // Preserve owner name if productId hasn't changed
                    if (previousProductId == productRelationships[index].productId) {
                        // Keep existing productOwnerName
                    } else {
                        // Product changed, clear owner (will be fetched by updateProductOwner)
                        productRelationships[index].productOwnerName = null;
                    }
                }
            }
        });
    }

    async function updateProductOwner(selectElement) {
        const productId = selectElement.value;
        const row = selectElement.closest('tr');
        const ownerCell = row.querySelector('td:nth-child(4)');
        const refCell = row.querySelector('td:nth-child(3)');
        const rowIndex = Array.from(row.parentElement.children).indexOf(row);
        
        // Update ref number immediately from products array
        if (productId && refCell) {
            const selectedProduct = products.find(p => (p.id || p.ID) == productId);
            if (selectedProduct) {
                const productRef = selectedProduct.refnumber || selectedProduct.refNumber || selectedProduct.RefNumber || '';
                refCell.innerHTML = `<span class="text-muted">${productRef || ''}</span>`;
                if (productRelationships[rowIndex]) {
                    productRelationships[rowIndex].productRefNumber = productRef;
                }
            } else if (refCell) {
                refCell.innerHTML = '<span class="text-muted"></span>';
            }
        } else if (refCell) {
            refCell.innerHTML = '<span class="text-muted"></span>';
        }
        
        if (!productId) {
            if (ownerCell) ownerCell.innerHTML = '<span class="text-muted"></span>';
            if (productRelationships[rowIndex]) {
                productRelationships[rowIndex].productOwnerName = null;
            }
            return;
        }
        
        try {
            const response = await fetch(`/api/dataset-impact/product-owner/${productId}`, {
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
                .filter(r => r.productId && r.relationType)
                .map(r => ({
                    productId: parseInt(r.productId),
                    relationType: parseInt(r.relationType),
                    description: r.description || null
                }));

            const requestData = {
                datasetId: currentDatasetId,
                relationships: relationships
            };

            const response = await fetch('/api/dataset-impact/products/save', {
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
                // Update original data after reload to match server data structure (prevents false change detection)
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
            const viewParam = currentViewMode === 'changes' ? '?view=changes' : '';
            const response = await fetch(`/api/dataset-impact/${currentDatasetId}/clients${viewParam}`, {
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
            const response = await fetch('/api/dataset-impact/client-relation-types', {
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
            const isNewRow = !relationship.id || String(relationship.id).startsWith('new-');
            const ownerDisplay = isNewRow && !relationship.clientId ? '' : (relationship.clientOwnerName || 'No owner');
            
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
        const tbody = document.querySelector('#clientImpactTableBody');
        if (!tbody) return;
        
        const rows = tbody.querySelectorAll('tr');
        rows.forEach((row, index) => {
            if (clientRelationships[index]) {
                const relationTypeSelect = row.querySelector('[data-field="relationType"]');
                const clientIdSelect = row.querySelector('[data-field="clientId"]');
                
                if (relationTypeSelect) {
                    clientRelationships[index].relationType = relationTypeSelect.value || null;
                }
                if (clientIdSelect) {
                    const previousClientId = clientRelationships[index].clientId;
                    clientRelationships[index].clientId = clientIdSelect.value || null;
                    
                    // Preserve owner name if clientId hasn't changed
                    if (previousClientId == clientRelationships[index].clientId) {
                        // Keep existing clientOwnerName
                    } else {
                        // Client changed, clear owner (will be fetched by updateClientOwner)
                        clientRelationships[index].clientOwnerName = null;
                    }
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
            const response = await fetch(`/api/dataset-impact/client-owner/${clientId}`, {
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
                    relationType: parseInt(r.relationType),
                    description: r.description || null
                }));

            const requestData = {
                datasetId: currentDatasetId,
                relationships: relationships
            };

            const response = await fetch('/api/dataset-impact/clients/save', {
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

    // ===== LEGAL ENTITY RELATIONSHIPS =====
    
    async function loadLegalRelationships() {
        try {
            const viewParam = currentViewMode === 'changes' ? '?view=changes' : '';
            const response = await fetch(`/api/dataset-impact/${currentDatasetId}/legals${viewParam}`, {
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
            const response = await fetch('/api/dataset-impact/legal-relation-types', {
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
            const isNewRow = !relationship.id || String(relationship.id).startsWith('new-');
            const legalDisplayName = relationship.legalLongName || relationship.legalShortName || '';
            const ownerDisplay = isNewRow && !relationship.legalId ? '' : (relationship.legalOwnerName || 'No owner');
            
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
        const tbody = document.querySelector('#legalImpactTableBody');
        if (!tbody) return;
        
        const rows = tbody.querySelectorAll('tr');
        rows.forEach((row, index) => {
            if (legalRelationships[index]) {
                const relationTypeSelect = row.querySelector('[data-field="relationType"]');
                const legalIdSelect = row.querySelector('[data-field="legalId"]');
                
                if (relationTypeSelect) {
                    legalRelationships[index].relationType = relationTypeSelect.value || null;
                }
                if (legalIdSelect) {
                    const previousLegalId = legalRelationships[index].legalId;
                    legalRelationships[index].legalId = legalIdSelect.value || null;
                    
                    // Update legal name if legal changed
                    if (legalRelationships[index].legalId) {
                        const selectedLegal = legals.find(l => (l.id || l.ID) == legalRelationships[index].legalId);
                        if (selectedLegal) {
                            legalRelationships[index].legalShortName = selectedLegal.shortName || selectedLegal.shortname || selectedLegal.ShortName || null;
                            legalRelationships[index].legalLongName = selectedLegal.longName || selectedLegal.longname || selectedLegal.LongName || null;
                        }
                    } else {
                        legalRelationships[index].legalShortName = null;
                        legalRelationships[index].legalLongName = null;
                    }
                    
                    // Preserve owner name if legalId hasn't changed
                    if (previousLegalId == legalRelationships[index].legalId) {
                        // Keep existing legalOwnerName
                    } else {
                        // Legal changed, clear owner (will be fetched by updateLegalOwner)
                        legalRelationships[index].legalOwnerName = null;
                    }
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
            const response = await fetch(`/api/dataset-impact/legal-owner/${legalId}`, {
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
                    relationType: parseInt(r.relationType),
                    description: r.description || null
                }));

            const requestData = {
                datasetId: currentDatasetId,
                relationships: relationships
            };

            const response = await fetch('/api/dataset-impact/legals/save', {
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

    // ===== SAVE ALL IMPACT DATA =====
    
    async function saveAllImpactData() {
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
            console.log('Dataset Impact Edit: hasImpactChanges detected changes:');
            if (hasProductChanges) console.log('  - Product relationships changed');
            if (hasClientChanges) console.log('  - Client relationships changed');
            if (hasLegalChanges) console.log('  - Legal relationships changed');
        }
        
        return hasChanges;
    }

    // ===== GLOBAL EXPORTS =====
    
    window.initImpactEdit = initImpactEdit;
    window.saveAllImpactData = saveAllImpactData;
    window.hasImpactChanges = hasImpactChanges;
    
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

