// Glossary Impact Edit JavaScript - Implementation for Product and Client sub-tabs
(function() {
    if (window.__BUDG_DEBUG__) console.log('=== GLOSSARY IMPACT EDIT SCRIPT LOADED ===');

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
            '#glossarySegment',
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
        const oid = parseInt(currentGlossaryId, 10);
        if (Number.isInteger(oid) && oid > 0) {
            const sep2 = result.includes('?') ? '&' : '?';
            result = `${result}${sep2}sourceObjectId=${oid}&sourceObjectType=Glossary`;
        }
        return result;
    }
    
    // Global variables
    let currentGlossaryId = null;
    let currentViewMode = 'original'; // 'original' | 'changes'
    let productRelationships = [];
    let clientRelationships = [];
    let originalProductData = [];
    let originalClientData = [];
    let productRelationTypes = [];
    let clientRelationTypes = [];
    let products = [];
    let clients = [];
    
    // Deduplication and caching flags
    let _isLoading = false;
    let _loadedForGlossaryId = null;
    let _productsLoaded = false;
    let _clientsLoaded = false;

    // Initialize Impact tab edit functionality
    function initImpactEdit(glossaryId, viewMode = 'original') {
        console.log('=== INIT IMPACT EDIT CALLED ===');
        console.log('Glossary ID:', glossaryId);
        currentViewMode = viewMode || 'original';
        
        // Prevent duplicate initialization for the same glossary
        if (_isLoading) {
            console.log('=== INIT IMPACT EDIT SKIPPED - Already loading ===');
            return;
        }
        
        // If already loaded for this glossary, skip
        if (_loadedForGlossaryId === glossaryId && products.length > 0) {
            console.log('=== INIT IMPACT EDIT SKIPPED - Already loaded for this glossary ===');
            return;
        }
        
        currentGlossaryId = glossaryId;
        loadImpactData();
    }

    // Load all Impact data
    async function loadImpactData() {
        // Prevent concurrent loads
        if (_isLoading) {
            console.log('=== LOADING IMPACT DATA SKIPPED - Already in progress ===');
            return;
        }
        
        _isLoading = true;
        
        try {
            console.log('=== LOADING IMPACT DATA START ===');
            
            // Load dropdown data first (only if not already loaded)
            const lookupsToLoad = [];
            if (productRelationTypes.length === 0) lookupsToLoad.push(loadProductRelationTypes());
            if (clientRelationTypes.length === 0) lookupsToLoad.push(loadClientRelationTypes());
            if (!_productsLoaded) lookupsToLoad.push(loadProducts());
            if (!_clientsLoaded) lookupsToLoad.push(loadClients());
            
            if (lookupsToLoad.length > 0) {
                await Promise.all(lookupsToLoad);
            }

            // Load relationship data
            await Promise.all([
                loadProductRelationships(),
                loadClientRelationships()
            ]);

            // Setup sub-tabs
            setupImpactSubTabs();
            
            // Mark as loaded for this glossary
            _loadedForGlossaryId = currentGlossaryId;
            
            console.log('=== LOADING IMPACT DATA END ===');

        } catch (error) {
            console.error('Error loading Impact data:', error);
        } finally {
            _isLoading = false;
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
            const response = await fetch(`/api/glossary-impact/${currentGlossaryId}/products${viewParam}`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            // Filter out child glossary relationships - only show direct relationships in edit view
            productRelationships = Array.isArray(data) ? data.filter(rel => {
                // Only include direct relationships (no child glossary)
                return rel.isDirectLink !== false && (!rel.childGlossaryId || rel.childGlossaryId === null);
            }) : [];
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

    async function loadProductRelationTypes() {
        try {
            const response = await fetch('/api/glossary-impact/product-relation-types', {
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
        // Skip if already loaded
        if (_productsLoaded && products.length > 0) {
            console.log('Products already loaded, skipping fetch');
            return;
        }
        
        try {
            console.log('Fetching products from /api/product/list');
            const response = await fetch(withImpactSegment('/api/product/list'), {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) {
                console.error('Product API response not OK:', response.status, response.statusText);
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const data = await response.json();
            console.log('Raw product data received:', data);
            console.log('Data type:', typeof data);
            console.log('Is array:', Array.isArray(data));
            
            // Handle different response formats
            if (Array.isArray(data)) {
                products = data;
            } else if (data && data.success && Array.isArray(data.data)) {
                products = data.data;
            } else if (data && Array.isArray(data.products)) {
                products = data.products;
            } else {
                console.warn('Unexpected product data format:', data);
                products = [];
            }
            
            _productsLoaded = true;
            console.log('Products loaded successfully:', products.length);
            if (products.length > 0) {
                console.log('First product sample:', products[0]);
            } else {
                console.warn('⚠️ No products returned from API');
            }
            
            // Re-render product table if it exists to update dropdowns
            const tbody = document.querySelector('#productImpactTableBody');
            if (tbody) {
                console.log('Re-rendering product table after products loaded');
                renderProductTable();
            }

        } catch (error) {
            console.error('Error loading products:', error);
            products = [];
            _productsLoaded = true; // Mark as loaded even on error to prevent infinite retries
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
                productOwnerName: null
            });
        }
        
        let html = '';
        productRelationships.forEach((relationship, index) => {
            const rowId = relationship.id || 'new-' + index;
            const ownerDisplay = relationship.productId ? (relationship.productOwnerName || 'No owner') : '';
            
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
                                `<option value="${p.id || p.ID}" ${relationship.productId == (p.id || p.ID) ? 'selected' : ''}>${p.primaryName || p.primaryname || p.PrimaryName || p.name || p.Name || 'Unnamed Product'}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td style="text-align: center;">
                        ${escapeHtml(ownerDisplay)}
                    </td>
                    <td>
                        <div class="action-buttons">
                            <button type="button" class="btn btn-sm" onclick="addProductRow()" title="${window.I18n?.t('impactEdit.buttons.addRow') || 'Add row'}">
                                <i class="fas fa-plus"></i>
                            </button>
                            <button type="button" class="btn btn-sm" onclick="deleteProductRow('${rowId}')" title="${window.I18n?.t('impactEdit.buttons.deleteRow') || 'Delete row'}">
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
        
        // Remove from array
        productRelationships = productRelationships.filter(rel => {
            const relId = rel.id || 'new-' + productRelationships.indexOf(rel);
            return String(relId) !== String(id);
        });
        
        // Ensure at least one empty row
        if (productRelationships.length === 0) {
            productRelationships.push({
                id: 'new-empty',
                relationType: null,
                productId: null,
                productName: null,
                productOwnerName: null
            });
        }
        
        renderProductTable();
    }

    function saveCurrentProductFormData() {
        const rows = document.querySelectorAll('#productImpactTableBody tr');
        rows.forEach((row, index) => {
            if (index < productRelationships.length) {
                const relationship = productRelationships[index];
                const relationTypeSelect = row.querySelector('select[data-field="relationType"]');
                const productSelect = row.querySelector('select[data-field="productId"]');
                
                if (relationTypeSelect) {
                    relationship.relationType = relationTypeSelect.value ? parseInt(relationTypeSelect.value) : null;
                }
                if (productSelect) {
                    relationship.productId = productSelect.value ? parseInt(productSelect.value) : null;
                }
                
                // Clear child glossary fields to prevent duplication
                relationship.childGlossaryId = null;
                relationship.childGlossaryName = null;
                relationship.childGlossaryType = null;
                relationship.isDirectLink = true;
            }
        });
    }

    async function updateProductOwner(selectElement) {
        const productId = selectElement.value;
        const row = selectElement.closest('tr');
        
        if (!productId) {
            return;
        }
        
        try {
            const response = await fetch(`/api/glossary-impact/product-owner/${productId}`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            const ownerName = data.productOwnerName || data.ownerName || 'No owner';
            
            // Update the owner cell in the table
            const ownerCell = row.querySelector('td:nth-child(3)');
            if (ownerCell) {
                ownerCell.textContent = ownerName;
            }
            
            // Update the relationship data
            const rowId = row.getAttribute('data-id');
            const relationship = productRelationships.find(rel => {
                const relId = rel.id || 'new-' + productRelationships.indexOf(rel);
                return String(relId) === String(rowId);
            });
            
            if (relationship) {
                relationship.productOwnerName = ownerName;
            }

        } catch (error) {
            console.error('Error fetching product owner:', error);
        }
    }

    async function saveProductRelationships() {
        try {
            saveCurrentProductFormData();
            
            // Filter out empty rows
            const validRelationships = productRelationships.filter(rel => {
                return rel.productId != null && rel.relationType != null;
            });
            
            const requestBody = {
                glossaryId: currentGlossaryId,
                relationships: validRelationships.map(rel => ({
                    productId: rel.productId,
                    relationType: rel.relationType
                }))
            };
            
            const response = await fetch('/api/glossary-impact/products/save', {
                method: 'POST',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(requestBody)
            });

            if (!response.ok) {
                const errorData = await response.json().catch(() => ({ error: 'Unknown error' }));
                if (response.status === 401) {
                    throw new Error('Authentication failed (HTTP 401). Your session may have expired. Please refresh the page or log in again.');
                }
                throw new Error(`HTTP error! status: ${response.status}, message: ${JSON.stringify(errorData)}`);
            }

            const result = await response.json();
            
            if (result.success) {
                // Reload relationships to get updated data
                await loadProductRelationships();
                return { success: true, message: result.message || 'Product relationships saved successfully' };
            } else {
                return { success: false, message: result.message || 'Failed to save product relationships' };
            }

        } catch (error) {
            console.error('Error saving product relationships:', error);
            if (error.message && error.message.includes('Authentication failed')) {
                alert('Authentication failed - session may have expired. Please refresh the page or log in again.');
            }
            throw error;
        }
    }

    // ===== CLIENT RELATIONSHIPS =====
    
    async function loadClientRelationships() {
        try {
            const viewParam = currentViewMode === 'changes' ? '?view=changes' : '';
            const response = await fetch(`/api/glossary-impact/${currentGlossaryId}/clients${viewParam}`, {
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
            const response = await fetch('/api/glossary-impact/client-relation-types', {
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
        // Skip if already loaded
        if (_clientsLoaded && clients.length > 0) {
            console.log('Clients already loaded, skipping fetch');
            return;
        }
        
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
            } else if (Array.isArray(data)) {
                clients = data;
            } else {
                clients = [];
            }
            
            _clientsLoaded = true;
            console.log('Final clients array length:', clients.length);
            console.log('=== LOADING CLIENTS END ===');

        } catch (error) {
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
                            ${clients.map(c => {
                                const clientName = c.primary_name || c.primaryName || c.primaryname || c.PrimaryName || c.name || c.Name || 'Unnamed Client';
                                return `<option value="${c.id || c.ID}" ${relationship.clientId == (c.id || c.ID) ? 'selected' : ''}>${clientName}</option>`;
                            }).join('')}
                        </select>
                    </td>
                    <td style="text-align: center;">
                        ${escapeHtml(ownerDisplay)}
                    </td>
                    <td>
                        <div class="action-buttons">
                            <button type="button" class="btn btn-sm" onclick="addClientRow()" title="${window.I18n?.t('impactEdit.buttons.addRow') || 'Add row'}">
                                <i class="fas fa-plus"></i>
                            </button>
                            <button type="button" class="btn btn-sm" onclick="deleteClientRow('${rowId}')" title="${window.I18n?.t('impactEdit.buttons.deleteRow') || 'Delete row'}">
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
        
        // Remove from array
        clientRelationships = clientRelationships.filter(rel => {
            const relId = rel.id || 'new-' + clientRelationships.indexOf(rel);
            return String(relId) !== String(id);
        });
        
        // Ensure at least one empty row
        if (clientRelationships.length === 0) {
            clientRelationships.push({
                id: 'new-empty',
                relationType: null,
                clientId: null,
                clientName: null,
                clientOwnerName: null
            });
        }
        
        renderClientTable();
    }

    function saveCurrentClientFormData() {
        const rows = document.querySelectorAll('#clientImpactTableBody tr');
        rows.forEach((row, index) => {
            if (index < clientRelationships.length) {
                const relationship = clientRelationships[index];
                const relationTypeSelect = row.querySelector('select[data-field="relationType"]');
                const clientSelect = row.querySelector('select[data-field="clientId"]');
                
                if (relationTypeSelect) {
                    relationship.relationType = relationTypeSelect.value ? parseInt(relationTypeSelect.value) : null;
                }
                if (clientSelect) {
                    relationship.clientId = clientSelect.value ? parseInt(clientSelect.value) : null;
                }
            }
        });
    }

    async function updateClientOwner(selectElement) {
        const clientId = selectElement.value;
        const row = selectElement.closest('tr');
        
        if (!clientId) {
            return;
        }
        
        try {
            const response = await fetch(`/api/glossary-impact/client-owner/${clientId}`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            const ownerName = data.clientOwnerName || data.ownerName || 'No owner';
            
            // Update the owner cell in the table
            const ownerCell = row.querySelector('td:nth-child(3)');
            if (ownerCell) {
                ownerCell.textContent = ownerName;
            }
            
            // Update the relationship data
            const rowId = row.getAttribute('data-id');
            const relationship = clientRelationships.find(rel => {
                const relId = rel.id || 'new-' + clientRelationships.indexOf(rel);
                return String(relId) === String(rowId);
            });
            
            if (relationship) {
                relationship.clientOwnerName = ownerName;
            }

        } catch (error) {
            console.error('Error fetching client owner:', error);
        }
    }

    async function saveClientRelationships() {
        try {
            saveCurrentClientFormData();
            
            // Filter out empty rows
            const validRelationships = clientRelationships.filter(rel => {
                return rel.clientId != null && rel.relationType != null;
            });
            
            const requestBody = {
                glossaryId: currentGlossaryId,
                relationships: validRelationships.map(rel => ({
                    clientId: rel.clientId,
                    relationType: rel.relationType
                }))
            };
            
            const response = await fetch('/api/glossary-impact/clients/save', {
                method: 'POST',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(requestBody)
            });

            if (!response.ok) {
                const errorData = await response.json().catch(() => ({ error: 'Unknown error' }));
                if (response.status === 401) {
                    throw new Error('Authentication failed (HTTP 401). Your session may have expired. Please refresh the page or log in again.');
                }
                throw new Error(`HTTP error! status: ${response.status}, message: ${JSON.stringify(errorData)}`);
            }

            const result = await response.json();
            
            if (result.success) {
                // Reload relationships to get updated data
                await loadClientRelationships();
                return { success: true, message: result.message || 'Client relationships saved successfully' };
            } else {
                return { success: false, message: result.message || 'Failed to save client relationships' };
            }

        } catch (error) {
            console.error('Error saving client relationships:', error);
            if (error.message && error.message.includes('Authentication failed')) {
                alert('Authentication failed - session may have expired. Please refresh the page or log in again.');
            }
            throw error;
        }
    }

    // Helper function to escape HTML
    function escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    // ===== CHANGE DETECTION =====
    
    function hasImpactChanges() {
        saveCurrentProductFormData();
        saveCurrentClientFormData();
        
        // Compare product relationships
        const currentProductData = productRelationships
            .filter(rel => rel.productId != null && rel.relationType != null)
            .map(rel => ({
                productId: rel.productId,
                relationType: rel.relationType
            }))
            .sort((a, b) => {
                if (a.productId !== b.productId) return a.productId - b.productId;
                return a.relationType - b.relationType;
            });
        
        const originalProductDataSorted = originalProductData
            .filter(rel => rel.productId != null && rel.relationType != null)
            .map(rel => ({
                productId: rel.productId,
                relationType: rel.relationType
            }))
            .sort((a, b) => {
                if (a.productId !== b.productId) return a.productId - b.productId;
                return a.relationType - b.relationType;
            });
        
        // Compare client relationships
        const currentClientData = clientRelationships
            .filter(rel => rel.clientId != null && rel.relationType != null)
            .map(rel => ({
                clientId: rel.clientId,
                relationType: rel.relationType
            }))
            .sort((a, b) => {
                if (a.clientId !== b.clientId) return a.clientId - b.clientId;
                return a.relationType - b.relationType;
            });
        
        const originalClientDataSorted = originalClientData
            .filter(rel => rel.clientId != null && rel.relationType != null)
            .map(rel => ({
                clientId: rel.clientId,
                relationType: rel.relationType
            }))
            .sort((a, b) => {
                if (a.clientId !== b.clientId) return a.clientId - b.clientId;
                return a.relationType - b.relationType;
            });
        
        // Check if product data changed
        if (currentProductData.length !== originalProductDataSorted.length) {
            return true;
        }
        for (let i = 0; i < currentProductData.length; i++) {
            if (currentProductData[i].productId !== originalProductDataSorted[i].productId ||
                currentProductData[i].relationType !== originalProductDataSorted[i].relationType) {
                return true;
            }
        }
        
        // Check if client data changed
        if (currentClientData.length !== originalClientDataSorted.length) {
            return true;
        }
        for (let i = 0; i < currentClientData.length; i++) {
            if (currentClientData[i].clientId !== originalClientDataSorted[i].clientId ||
                currentClientData[i].relationType !== originalClientDataSorted[i].relationType) {
                return true;
            }
        }
        
        return false;
    }

    // ===== SAVE ALL IMPACT DATA =====
    
    async function saveAllImpactData() {
        const results = {};
        
        try {
            const productResult = await saveProductRelationships();
            results.product = productResult;
        } catch (error) {
            results.product = { success: false, message: error.message };
        }
        
        try {
            const clientResult = await saveClientRelationships();
            results.client = clientResult;
        } catch (error) {
            results.client = { success: false, message: error.message };
        }
        
        return results;
    }

    // Export functions to global scope
    window.initImpactEdit = initImpactEdit;
    window.addProductRow = addProductRow;
    window.deleteProductRow = deleteProductRow;
    window.updateProductOwner = updateProductOwner;
    window.addClientRow = addClientRow;
    window.deleteClientRow = deleteClientRow;
    window.updateClientOwner = updateClientOwner;
    window.saveAllImpactData = saveAllImpactData;
    window.hasImpactChanges = hasImpactChanges;
    
    if (window.__BUDG_DEBUG__) console.log('Glossary Impact Edit functions exported to window');
})();

