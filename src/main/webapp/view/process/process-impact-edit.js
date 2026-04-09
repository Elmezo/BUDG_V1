// Process Impact Edit JavaScript - Implementation for all impact sub-tabs
console.log('=== PROCESS IMPACT EDIT SCRIPT LOADING ===');

(function() {
    console.log('=== PROCESS IMPACT EDIT SCRIPT LOADED ===');

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
            '#processSegment',
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
        const pid = parseInt(currentProcessId, 10);
        if (Number.isInteger(pid) && pid > 0) {
            const sep2 = result.includes('?') ? '&' : '?';
            result = `${result}${sep2}sourceObjectId=${pid}&sourceObjectType=Process`;
        }
        return result;
    }
    
    // Global variables
    let currentProcessId = null;
    let currentViewMode = 'original'; // 'original' | 'changes'
    let systemRelationships = [];
    let productRelationships = [];
    let clientRelationships = [];
    let glossaryRelationships = [];
    let projectRelationships = [];
    let policyRelationships = [];
    let interfaceRelationships = [];
    let legalRelationships = [];
    let datasetRelationships = [];
    let attributeRelationships = [];
    
    let originalSystemData = [];
    let originalProductData = [];
    let originalClientData = [];
    let originalGlossaryData = [];
    let originalProjectData = [];
    let originalPolicyData = [];
    let originalInterfaceData = [];
    let originalLegalData = [];
    let originalDatasetData = [];
    let originalAttributeData = [];
    
    let systemRelationTypes = [];
    let productRelationTypes = [];
    let clientRelationTypes = [];
    let glossaryRelationTypes = [];
    let projectRelationTypes = [];
    let policyRelationTypes = [];
    let interfaceRelationTypes = [];
    let legalRelationTypes = [];
    let datasetRelationTypes = [];
    let attributeRelationTypes = [];
    
    let systems = [];
    let products = [];
    let clients = [];
    let glossaries = [];
    let projects = [];
    let policies = [];
    let policyTypes = []; // For policy type ID to name lookup
    let interfaces = [];
    let legals = [];
    let datasets = [];
    let attributes = [];

    // Initialize Impact tab edit functionality
    function initImpactEdit(processId, viewMode = 'original') {
        console.log('=== INIT IMPACT EDIT CALLED ===');
        console.log('Process ID:', processId);
        currentProcessId = processId;
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
                loadSystemRelationTypes(),
                loadProductRelationTypes(),
                loadClientRelationTypes(),
                loadGlossaryRelationTypes(),
                loadProjectRelationTypes(),
                loadPolicyRelationTypes(),
                loadInterfaceRelationTypes(),
                loadLegalRelationTypes(),
                loadDatasetRelationTypes(),
                loadAttributeRelationTypes(),
                loadSystems(),
                loadProducts(),
                loadClients(),
                loadGlossaries(),
                loadProjects(),
                loadPolicies(),
                loadPolicyTypes(), // Load policy types for lookup
                loadInterfaces(),
                loadLegals(),
                loadDatasets(),
                loadAttributes()
            ]);

            // Load relationship data
            await Promise.all([
                loadSystemRelationships(),
                loadProductRelationships(),
                loadClientRelationships(),
                loadGlossaryRelationships(),
                loadProjectRelationships(),
                loadPolicyRelationships(),
                loadInterfaceRelationships(),
                loadLegalRelationships(),
                loadDatasetRelationships(),
                loadAttributeRelationships()
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
                if (subTabName === 'system') {
                    targetSubTab = document.getElementById('impactSystemContent');
                } else if (subTabName === 'product') {
                    targetSubTab = document.getElementById('impactProductContent');
                } else if (subTabName === 'client') {
                    targetSubTab = document.getElementById('impactClientContent');
                } else if (subTabName === 'glossary') {
                    targetSubTab = document.getElementById('impactGlossaryContent');
                } else if (subTabName === 'project') {
                    targetSubTab = document.getElementById('impactProjectContent');
                } else if (subTabName === 'policy') {
                    targetSubTab = document.getElementById('impactPolicyContent');
                } else if (subTabName === 'interface') {
                    targetSubTab = document.getElementById('impactInterfaceContent');
                } else if (subTabName === 'legal') {
                    targetSubTab = document.getElementById('impactLegalContent');
                } else if (subTabName === 'dataset') {
                    targetSubTab = document.getElementById('impactDatasetContent');
                } else if (subTabName === 'attribute') {
                    targetSubTab = document.getElementById('impactAttributeContent');
                }
                
                if (targetSubTab) {
                    targetSubTab.classList.add('active');
                    targetSubTab.style.display = 'block';
                }
            });
        });
    }

    // ===== SYSTEM RELATIONSHIPS =====
    
    async function loadSystemRelationships() {
        try {
            const response = await fetch(`/api/process-impact/${currentProcessId}/systems${getViewParam()}`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            systemRelationships = Array.isArray(data) ? data : [];
            originalSystemData = JSON.parse(JSON.stringify(systemRelationships));
            renderSystemTable();

        } catch (error) {
            console.error('Error loading system relationships:', error);
            systemRelationships = [];
            originalSystemData = [];
        }
    }

    async function loadSystemRelationTypes() {
        try {
            const response = await fetch('/api/process-impact/system-relation-types', {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            systemRelationTypes = Array.isArray(data) ? data : [];
            console.log('Process Impact Edit: Loaded', systemRelationTypes.length, 'system relation types');
            console.log('Process Impact Edit: System relation types:', systemRelationTypes);

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
        
        console.log('Process Impact Edit: renderSystemTable called with', systemRelationTypes.length, 'system relation types');
        if (systemRelationTypes.length > 0) {
            console.log('Process Impact Edit: First system relation type:', systemRelationTypes[0]);
        }
        
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
                                `<option value="${rt.id}" ${relationship.relationType == rt.id ? 'selected' : ''}>${rt.primaryname || rt.primaryName || rt.PrimaryName || 'Unnamed Type'}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td>
                        <select class="form-control" data-field="systemId" onchange="updateSystemOwner(this)">
                            <option value="">Select system</option>
                            ${systems.map(s => 
                                `<option value="${s.id}" ${relationship.systemId == s.id ? 'selected' : ''}>${s.name || s.Name || 'Unnamed System'}</option>`
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
            const response = await fetch(`/api/process-impact/system-owner/${systemId}`, {
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
            processId: currentProcessId,
            relationships: relationships
        };
        
        try {
            const response = await fetch('/api/process-impact/systems/save', {
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

    // ===== PRODUCT RELATIONSHIPS =====
    
    async function loadProductRelationships() {
        try {
            const response = await fetch(`/api/process-impact/${currentProcessId}/products${getViewParam()}`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            productRelationships = Array.isArray(data) ? data : [];
            originalProductData = JSON.parse(JSON.stringify(productRelationships));
            renderProductTable();

        } catch (error) {
            console.error('Error loading product relationships:', error);
            productRelationships = [];
            originalProductData = [];
        }
    }

    async function loadProductRelationTypes() {
        try {
            const response = await fetch('/api/process-impact/product-relation-types', {
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
                productOwnerName: null
            });
        }
        
        let html = '';
        productRelationships.forEach((relationship, index) => {
            const rowId = relationship.id || 'new-' + index;
            const isNewRow = !relationship.id || String(relationship.id).startsWith('new-');
            const ownerName = relationship.productOwnerName || relationship.ownerName || '';
            const ownerDisplay = isNewRow && !relationship.productId ? '' : (ownerName || 'No owner');
            
            html += `
                <tr data-id="${rowId}">
                    <td>
                        <select class="form-control" data-field="relationType">
                            <option value="">Select relationship type</option>
                            ${productRelationTypes.map(rt => 
                                `<option value="${rt.id}" ${relationship.relationType == rt.id ? 'selected' : ''}>${rt.primaryName}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td>
                        <select class="form-control" data-field="productId" onchange="updateProductOwner(this)">
                            <option value="">Select product</option>
                            ${products.map(p => 
                                `<option value="${p.id}" ${relationship.productId == p.id ? 'selected' : ''}>${p.primaryname || p.name || 'Unnamed Product'}</option>`
                            ).join('')}
                        </select>
                    </td>
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
        const savedData = [];
        
        rows.forEach((row, index) => {
            const existingRelationship = productRelationships[index];
            const rowId = existingRelationship?.id || ('new-' + Date.now() + '-' + index);
            
            const relationTypeSelect = row.querySelector('select[data-field="relationType"]');
            const productSelect = row.querySelector('select[data-field="productId"]');
            
            let productName = null;
            if (productSelect && productSelect.value) {
                const product = products.find(p => p.id == productSelect.value || p.ID == productSelect.value);
                if (product) {
                    productName = product.primaryname || product.name || product.Name;
                }
            }
            
            const data = {
                id: rowId,
                relationType: relationTypeSelect ? parseInt(relationTypeSelect.value) : null,
                productId: productSelect ? parseInt(productSelect.value) : null,
                productName: productName,
                productOwnerName: existingRelationship?.productOwnerName || existingRelationship?.ownerName || null
            };
            
            savedData.push(data);
        });
        
        productRelationships = savedData.length > 0 ? savedData : [{ id: 'new-empty', relationType: null, productId: null, productName: null, productOwnerName: null }];
    }

    async function updateProductOwner(selectElement) {
        const productId = selectElement.value;
        const row = selectElement.closest('tr');
        const ownerCell = row.querySelector('td:nth-child(3)');
        
        if (productId) {
            await updateProductOwnerDisplay(ownerCell, parseInt(productId), row);
        } else {
            ownerCell.innerHTML = '<span class="text-muted"></span>';
        }
    }

    async function updateProductOwnerDisplay(ownerCell, productId, row) {
        try {
            const response = await fetch(`/api/process-impact/product-owner/${productId}`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            const ownerName = data.ownerName || data.productOwnerName || 'No owner';
            ownerCell.innerHTML = `<span class="text-muted">${ownerName}</span>`;
            
            // Update the relationship data structure
            if (row) {
                const rowIndex = Array.from(row.parentElement.children).indexOf(row);
                if (productRelationships[rowIndex]) {
                    productRelationships[rowIndex].productOwnerName = data.ownerName || data.productOwnerName || null;
                }
            }

        } catch (error) {
            console.error('Error loading product owner:', error);
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
        
        const requestData = {
            processId: currentProcessId,
            relationships: relationships
        };
        
        try {
            const response = await fetch('/api/process-impact/products/save', {
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
            console.error('Error saving product relationships:', error);
            return { success: false, message: 'Network error: ' + error.message };
        }
    }

    // ===== CLIENT RELATIONSHIPS =====
    
    async function loadClientRelationships() {
        try {
            const response = await fetch(`/api/process-impact/${currentProcessId}/clients${getViewParam()}`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            clientRelationships = Array.isArray(data) ? data : [];
            originalClientData = JSON.parse(JSON.stringify(clientRelationships));
            renderClientTable();

        } catch (error) {
            console.error('Error loading client relationships:', error);
            clientRelationships = [];
            originalClientData = [];
        }
    }

    async function loadClientRelationTypes() {
        try {
            const response = await fetch('/api/process-impact/client-relation-types', {
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
            const response = await fetch(withImpactSegment('/api/client/'), {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            // Handle nested data structure
            if (data && data.success && Array.isArray(data.data)) {
                clients = data.data;
            } else if (Array.isArray(data)) {
                clients = data;
            } else {
                clients = [];
            }

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
            const isNewRow = !relationship.id || String(relationship.id).startsWith('new-');
            const ownerName = relationship.clientOwnerName || relationship.ownerName || '';
            const ownerDisplay = isNewRow && !relationship.clientId ? '' : (ownerName || 'No owner');
            
            html += `
                <tr data-id="${rowId}">
                    <td>
                        <select class="form-control" data-field="relationType">
                            <option value="">Select relationship type</option>
                            ${clientRelationTypes.map(rt => 
                                `<option value="${rt.id || rt.ID}" ${relationship.relationType == (rt.id || rt.ID) ? 'selected' : ''}>${rt.primaryName || rt.PrimaryName}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td>
                        <select class="form-control" data-field="clientId" onchange="updateClientOwner(this)">
                            <option value="">Select client</option>
                            ${clients.map(c => 
                                `<option value="${c.id || c.ID}" ${relationship.clientId == (c.id || c.ID) ? 'selected' : ''}>${c.primary_name || c.PrimaryName || 'Unnamed Client'}</option>`
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
        const savedData = [];
        
        rows.forEach((row, index) => {
            const existingRelationship = clientRelationships[index];
            const rowId = existingRelationship?.id || ('new-' + Date.now() + '-' + index);
            
            const relationTypeSelect = row.querySelector('select[data-field="relationType"]');
            const clientSelect = row.querySelector('select[data-field="clientId"]');
            
            let clientName = null;
            if (clientSelect && clientSelect.value) {
                const client = clients.find(c => (c.id || c.ID) == clientSelect.value);
                if (client) {
                    clientName = client.primary_name || client.PrimaryName || client.name || client.Name;
                }
            }
            
            const data = {
                id: rowId,
                relationType: relationTypeSelect ? parseInt(relationTypeSelect.value) : null,
                clientId: clientSelect ? parseInt(clientSelect.value) : null,
                clientName: clientName,
                clientOwnerName: existingRelationship?.clientOwnerName || existingRelationship?.ownerName || null
            };
            
            savedData.push(data);
        });
        
        clientRelationships = savedData.length > 0 ? savedData : [{ id: 'new-empty', relationType: null, clientId: null, clientName: null, clientOwnerName: null }];
    }

    async function updateClientOwner(selectElement) {
        const clientId = selectElement.value;
        const row = selectElement.closest('tr');
        const ownerCell = row.querySelector('td:nth-child(3)');
        
        if (clientId) {
            await updateClientOwnerDisplay(ownerCell, parseInt(clientId), row);
        } else {
            ownerCell.innerHTML = '<span class="text-muted"></span>';
        }
    }

    async function updateClientOwnerDisplay(ownerCell, clientId, row) {
        try {
            const response = await fetch(`/api/process-impact/client-owner/${clientId}`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            const ownerName = data.ownerName || data.clientOwnerName || 'No owner';
            ownerCell.innerHTML = `<span class="text-muted">${ownerName}</span>`;
            
            // Update the relationship data structure
            if (row) {
                const rowIndex = Array.from(row.parentElement.children).indexOf(row);
                if (clientRelationships[rowIndex]) {
                    clientRelationships[rowIndex].clientOwnerName = data.ownerName || data.clientOwnerName || null;
                }
            }

        } catch (error) {
            console.error('Error loading client owner:', error);
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
        
        const requestData = {
            processId: currentProcessId,
            relationships: relationships
        };
        
        try {
            const response = await fetch('/api/process-impact/clients/save', {
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
            console.error('Error saving client relationships:', error);
            return { success: false, message: 'Network error: ' + error.message };
        }
    }

    // ===== GLOSSARY RELATIONSHIPS =====
    
    async function loadGlossaryRelationships() {
        try {
            const response = await fetch(`/api/process-impact/${currentProcessId}/glossaries${getViewParam()}`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
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
            const response = await fetch('/api/process-impact/glossary-relation-types', {
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
            
            html += `
                <tr data-id="${rowId}">
                    <td>
                        <select class="form-control" data-field="relationType">
                            <option value="">Select relationship type</option>
                            ${glossaryRelationTypes.map(rt => 
                                `<option value="${rt.id}" ${relationship.relationType == rt.id ? 'selected' : ''}>${rt.primaryName || rt.PrimaryName}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td>
                        <select class="form-control" data-field="glossaryId" onchange="updateGlossaryOwner(this)">
                            <option value="">Select glossary</option>
                            ${glossaries.map(g => 
                                `<option value="${g.id}" ${relationship.glossaryId == g.id ? 'selected' : ''}>${g.name || g.Name || 'Unnamed Glossary'}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td><span class="text-muted">${relationship.glossaryType || relationship.glossaryTypeName || ''}</span></td>
                    <td><span class="text-muted">${relationship.glossaryOwnerName || 'No owner'}</span></td>
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
                        const typeName = glossaryData.typeName || glossaryData.type_name || '';
                        typeCell.innerHTML = `<span class="text-muted">${typeName}</span>`;
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
            const response = await fetch(`/api/process-impact/glossary-owner/${glossaryId}`, {
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
            
            // Collect data from form (read from DOM to ensure all deletions are captured)
            const relationships = [];
            const rows = document.querySelectorAll('#glossaryImpactTableBody tr');
            
            rows.forEach((row, index) => {
                const relationTypeSelect = row.querySelector('[data-field="relationType"]');
                const glossaryIdSelect = row.querySelector('[data-field="glossaryId"]');
                
                if (relationTypeSelect && glossaryIdSelect && 
                    relationTypeSelect.value && glossaryIdSelect.value) {
                    // Get the relationship id from the array if available
                    const relationship = glossaryRelationships[index];
                    const relationshipId = relationship && relationship.id && !String(relationship.id).startsWith('new-') 
                        ? parseInt(relationship.id) 
                        : null;
                    
                    relationships.push({
                        id: relationshipId,
                        glossaryId: parseInt(glossaryIdSelect.value),
                        relationType: parseInt(relationTypeSelect.value)
                    });
                }
            });

            const requestData = {
                processId: currentProcessId,
                relationships: relationships
            };

            const response = await fetch('/api/process-impact/glossaries/save', {
                method: 'POST',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(requestData)
            });

            if (!response.ok) {
                const errorText = await response.text();
                throw new Error(`HTTP error! status: ${response.status}, message: ${errorText}`);
            }

            const result = await response.json();
            
            if (result.success) {
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

    // ===== PROJECT RELATIONSHIPS =====
    
    async function loadProjectRelationships() {
        try {
            const response = await fetch(`/api/process-impact/${currentProcessId}/projects${getViewParam()}`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            projectRelationships = Array.isArray(data) ? data : [];
            originalProjectData = JSON.parse(JSON.stringify(projectRelationships));
            renderProjectTable();

        } catch (error) {
            console.error('Error loading project relationships:', error);
            projectRelationships = [];
            originalProjectData = [];
            renderProjectTable();
        }
    }

    async function loadProjectRelationTypes() {
        try {
            const response = await fetch('/api/process-impact/project-relation-types', {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            projectRelationTypes = Array.isArray(data) ? data : [];

        } catch (error) {
            console.error('Error loading project relation types:', error);
            projectRelationTypes = [];
        }
    }

    async function loadProjects() {
        try {
            const response = await fetch(withImpactSegment('/api/project/'), {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            projects = Array.isArray(data) ? data : [];

        } catch (error) {
            console.error('Error loading projects:', error);
            projects = [];
        }
    }

    function renderProjectTable() {
        const tbody = document.querySelector('#projectImpactTableBody');
        const footer = document.querySelector('#projectImpactFooter');
        
        if (!tbody) return;
        
        if (projectRelationships.length === 0) {
            projectRelationships.push({
                id: 'new-empty',
                relationType: null,
                projectId: null,
                projectName: null,
                projectRef: null,
                projectDescription: null,
                projectOwnerName: null
            });
        }
        
        let html = '';
        projectRelationships.forEach((relationship, index) => {
            const rowId = relationship.id || 'new-' + index;
            const isNewRow = !relationship.id || String(relationship.id).startsWith('new-');
            const ownerName = relationship.projectOwnerName || relationship.ownerName || '';
            const ownerDisplay = isNewRow && !relationship.projectId ? '' : (ownerName || 'No owner');
            
            html += `
                <tr data-id="${rowId}">
                    <td>
                        <select class="form-control" data-field="relationType">
                            <option value="">Select relationship type</option>
                            ${projectRelationTypes.map(rt => 
                                `<option value="${rt.id}" ${relationship.relationType == rt.id ? 'selected' : ''}>${rt.primaryName || rt.PrimaryName}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td>
                        <select class="form-control" data-field="projectId" onchange="updateProjectOwner(this)">
                            <option value="">Select project</option>
                            ${projects.map(p => 
                                `<option value="${p.id}" ${relationship.projectId == p.id ? 'selected' : ''}>${p.primaryname || p.name || p.Name || 'Unnamed Project'}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td><span class="text-muted">${relationship.projectRef || ''}</span></td>
                    <td><span class="text-muted">${ownerDisplay}</span></td>
                    <td><input type="text" class="form-control" data-field="description" value="${relationship.description || ''}" placeholder="Enter description"></td>
                    <td>
                        <div class="action-buttons">
                            <button type="button" class="btn btn-sm btn-success" onclick="addProjectRow()" title="${window.I18n?.t('impactEdit.buttons.addRow') || 'Add row'}">
                                <i class="fas fa-plus"></i>
                            </button>
                            <button type="button" class="btn btn-sm btn-danger" onclick="deleteProjectRow('${rowId}')" title="${window.I18n?.t('impactEdit.buttons.deleteRow') || 'Delete row'}">
                                <i class="fas fa-minus"></i>
                            </button>
                        </div>
                    </td>
                </tr>
            `;
        });
        
        tbody.innerHTML = html;
        if (footer) footer.textContent = `${projectRelationships.length} record${projectRelationships.length !== 1 ? 's' : ''}`;
    }

    function addProjectRow() {
        saveCurrentProjectFormData();
        const newRelationship = {
            id: 'new-' + Date.now(),
            relationType: null,
            projectId: null,
            projectName: null,
            projectRef: null,
            projectDescription: null,
            projectOwnerName: null
        };
        projectRelationships.push(newRelationship);
        renderProjectTable();
    }

    function deleteProjectRow(id) {
        // Save current form data before re-rendering
        saveCurrentProjectFormData();
        
        if (projectRelationships.length <= 1) {
            // Clear the first row instead of deleting it
            projectRelationships[0] = {
                id: 'new-' + Date.now(),
                relationType: null,
                projectId: null,
                projectName: null,
                projectRef: null,
                projectDescription: null,
                projectOwnerName: null
            };
        } else {
            projectRelationships = projectRelationships.filter(rel => String(rel.id) !== String(id));
        }
        renderProjectTable();
    }

    function saveCurrentProjectFormData() {
        const tbody = document.querySelector('#projectImpactTableBody');
        if (!tbody) return;
        
        const rows = tbody.querySelectorAll('tr');
        const updatedRelationships = [];
        
        rows.forEach((row, index) => {
                const relationTypeSelect = row.querySelector('[data-field="relationType"]');
                const projectIdSelect = row.querySelector('[data-field="projectId"]');
                const descriptionInput = row.querySelector('[data-field="description"]');
            const rowId = row.getAttribute('data-id');
            
            // Get existing relationship or create new one (preserve existing data)
            let relationship = projectRelationships[index] || {
                id: rowId && !rowId.toString().startsWith('new-') ? parseInt(rowId) : null,
                projectId: null,
                relationType: null,
                projectName: null,
                projectRef: null,
                projectOwnerName: null,
                description: null
            };
            
            // Store previous values to preserve them if projectId hasn't changed
            const previousProjectId = relationship.projectId;
            const previousProjectRef = relationship.projectRef;
            const previousProjectOwnerName = relationship.projectOwnerName;
            
            // Update from form fields
                if (relationTypeSelect) {
                relationship.relationType = relationTypeSelect.value || null;
                }
                if (projectIdSelect) {
                relationship.projectId = projectIdSelect.value || null;
                }
                if (descriptionInput) {
                relationship.description = descriptionInput.value || '';
            }
            
            // Only update project details if projectId changed or if missing
            if (relationship.projectId && projects.length > 0) {
                const project = projects.find(p => p.id == relationship.projectId);
                if (project) {
                    relationship.projectName = project.primaryname || project.name || project.Name || null;
                    
                    // Only update ref if missing or projectId changed
                    if (!previousProjectRef || previousProjectId != relationship.projectId) {
                        relationship.projectRef = project.ref || project.Ref || project.refnumber || null;
                    } else {
                        relationship.projectRef = previousProjectRef;
                    }
                }
            } else {
                // Clear if no project selected
                if (!relationship.projectId) {
                    relationship.projectRef = null;
                }
            }
            
            // Preserve projectOwnerName (from API or previous update) if projectId hasn't changed
            // This matches how policy preserves policyOwnerName - only clear if project changed
            if (previousProjectOwnerName && previousProjectId == relationship.projectId) {
                relationship.projectOwnerName = previousProjectOwnerName;
            } else if (previousProjectId != relationship.projectId) {
                // Project changed, clear owner (it will be fetched by updateProjectOwner when selected)
                relationship.projectOwnerName = null;
            }
            // If no previous owner but projectId unchanged, keep it as is (might be loaded from API)
            
            updatedRelationships.push(relationship);
        });
        
        // Update the array with all collected relationships
        projectRelationships = updatedRelationships;
        console.log('Process Impact Edit: saveCurrentProjectFormData collected', projectRelationships.length, 'project relationships');
    }

    async function updateProjectOwner(selectElement) {
        const projectId = selectElement.value;
        const row = selectElement.closest('tr');
        const ownerCell = row.querySelector('td:nth-child(4)');
        
        if (ownerCell && projectId) {
            await updateProjectOwnerDisplay(ownerCell, projectId, row);
        } else if (ownerCell) {
            ownerCell.innerHTML = '<span class="text-muted"></span>';
        }
        
        // Also update the ref column
        const refCell = row.querySelector('td:nth-child(3)');
        if (projectId && refCell) {
            const project = projects.find(p => p.id == projectId);
            if (project) {
                const projectRef = project.ref || project.Ref || project.refnumber || '';
                refCell.innerHTML = `<span class="text-muted">${projectRef}</span>`;
            }
        }
    }

    async function updateProjectOwnerDisplay(ownerCell, projectId, row) {
        try {
            const response = await fetch(`/api/process-impact/project-owner/${projectId}`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            const ownerName = data.ownerName || data.projectOwnerName || 'No owner';
            ownerCell.innerHTML = `<span class="text-muted">${ownerName}</span>`;
            
            // Update the relationship data structure
            if (row) {
                const rowIndex = Array.from(row.parentElement.children).indexOf(row);
                if (projectRelationships[rowIndex]) {
                    projectRelationships[rowIndex].projectOwnerName = data.ownerName || data.projectOwnerName || null;
                }
            }
        } catch (error) {
            console.error('Error fetching project owner:', error);
            ownerCell.innerHTML = '<span class="text-muted">No owner</span>';
        }
    }

    async function saveProjectRelationships() {
        try {
            saveCurrentProjectFormData();
            
            // Collect data from form (read from DOM to ensure all deletions are captured)
            const relationships = [];
            const rows = document.querySelectorAll('#projectImpactTableBody tr');
            
            rows.forEach((row, index) => {
                const relationTypeSelect = row.querySelector('[data-field="relationType"]');
                const projectIdSelect = row.querySelector('[data-field="projectId"]');
                const descriptionInput = row.querySelector('[data-field="description"]');
                
                if (relationTypeSelect && projectIdSelect && 
                    relationTypeSelect.value && projectIdSelect.value) {
                    // Get the relationship id from the array if available
                    const relationship = projectRelationships[index];
                    const relationshipId = relationship && relationship.id && !String(relationship.id).startsWith('new-') 
                        ? parseInt(relationship.id) 
                        : null;
                    
                    relationships.push({
                        id: relationshipId,
                        projectId: parseInt(projectIdSelect.value),
                        relationType: parseInt(relationTypeSelect.value),
                        description: descriptionInput ? (descriptionInput.value || '') : ''
                    });
                }
            });

            const requestData = {
                processId: currentProcessId,
                relationships: relationships
            };

            const response = await fetch('/api/process-impact/projects/save', {
                method: 'POST',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(requestData)
            });

            if (!response.ok) {
                const errorText = await response.text();
                throw new Error(`HTTP error! status: ${response.status}, message: ${errorText}`);
            }

            const result = await response.json();
            
            if (result.success) {
                originalProjectData = JSON.parse(JSON.stringify(projectRelationships));
                return true;
            } else {
                return { success: false, message: result.message || 'Failed to save project relationships' };
            }
        } catch (error) {
            console.error('Error saving project relationships:', error);
            return { success: false, message: 'Network error: ' + error.message };
        }
    }

    // ===== POLICY, INTERFACE, LEGAL, DATASET, ATTRIBUTE RELATIONSHIPS =====
    // Implementations are stubs for now - to be completed
    
    // ===== POLICY RELATIONSHIPS =====
    
    async function loadPolicyRelationships() {
        try {
            const response = await fetch(`/api/process-impact/${currentProcessId}/policies${getViewParam()}`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            console.log('Process Impact Edit: Loaded policy relationships from API:', data);
            console.log('Process Impact Edit: Number of policy relationships received:', Array.isArray(data) ? data.length : 0);
            
            policyRelationships = Array.isArray(data) ? data : [];
            console.log('Process Impact Edit: policyRelationships array length:', policyRelationships.length);
            
            // Log each relationship for debugging
            policyRelationships.forEach((rel, idx) => {
                console.log(`Process Impact Edit: Policy relationship ${idx + 1}:`, {
                    id: rel.id,
                    policyId: rel.policyId,
                    relationType: rel.relationType,
                    policyName: rel.policyName
                });
            });
            
            originalPolicyData = JSON.parse(JSON.stringify(policyRelationships));
            renderPolicyTable();

        } catch (error) {
            console.error('Error loading policy relationships:', error);
            policyRelationships = [];
            originalPolicyData = [];
            renderPolicyTable();
        }
    }

    async function loadPolicyRelationTypes() {
        try {
            const response = await fetch('/api/process-impact/policy-relation-types', {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            policyRelationTypes = Array.isArray(data) ? data : [];

        } catch (error) {
            console.error('Error loading policy relation types:', error);
            policyRelationTypes = [];
        }
    }

    async function loadPolicies() {
        try {
            const response = await fetch(withImpactSegment('/api/policy/list'), {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            // /list endpoint returns a direct array
            if (Array.isArray(data)) {
                policies = data;
            } else if (data && data.success && Array.isArray(data.data)) {
                policies = data.data;
            } else {
                policies = [];
            }

        } catch (error) {
            console.error('Error loading policies:', error);
            policies = [];
        }
    }

    async function loadPolicyTypes() {
        try {
            const response = await fetch('/api/policy-type/list', {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            console.log('Policy types API response:', data);
            if (Array.isArray(data)) {
                policyTypes = data;
                console.log('Policy types loaded:', policyTypes.length, 'types');
                if (policyTypes.length > 0) {
                    console.log('Sample policy type:', policyTypes[0]);
                }
            } else if (data && data.success && Array.isArray(data.data)) {
                policyTypes = data.data;
                console.log('Policy types loaded from data.data:', policyTypes.length, 'types');
            } else {
                policyTypes = [];
                console.warn('Policy types response was not an array:', data);
            }

        } catch (error) {
            console.error('Error loading policy types:', error);
            policyTypes = [];
        }
    }
    
    function renderPolicyTable() {
        const tbody = document.querySelector('#policyImpactTableBody');
        const footer = document.querySelector('#policyImpactFooter');
        
        if (!tbody) return;
        
        if (policyRelationships.length === 0) {
            policyRelationships.push({
                id: 'new-empty',
                relationType: null,
                policyId: null,
                policyName: null,
                policyOwnerName: null
            });
        }
        
        console.log('Process Impact Edit: renderPolicyTable called with', policyRelationships.length, 'relationships');
        
        let html = '';
        let renderedCount = 0;
        policyRelationships.forEach((relationship, index) => {
            renderedCount++;
            const rowId = relationship.id || 'new-' + index;
            const isNewRow = !relationship.id || String(relationship.id).startsWith('new-');
            const ownerName = relationship.policyOwnerName || relationship.ownerName || '';
            const ownerDisplay = isNewRow && !relationship.policyId ? '' : (ownerName || 'No owner');
            
            console.log(`Process Impact Edit: Rendering policy relationship ${renderedCount}:`, {
                id: relationship.id,
                policyId: relationship.policyId,
                relationType: relationship.relationType,
                rowId: rowId
            });
            
            html += `
                <tr data-id="${rowId}">
                    <td>
                        <select class="form-control" data-field="relationType">
                            <option value="">Select relationship type</option>
                            ${policyRelationTypes.map(rt => 
                                `<option value="${rt.id || rt.ID}" ${relationship.relationType == (rt.id || rt.ID) ? 'selected' : ''}>${rt.primaryName || rt.PrimaryName}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td>
                        <select class="form-control" data-field="policyId" onchange="updatePolicyOwner(this)">
                            <option value="">Select policy</option>
                            ${policies.map(p => 
                                `<option value="${p.id || p.ID}" ${relationship.policyId == (p.id || p.ID) ? 'selected' : ''}>${p.name || p.Name || p.primaryname || p.PrimaryName || 'Unnamed Policy'}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td><span class="text-muted">${relationship.policyRefNumber || ''}</span></td>
                    <td><span class="text-muted">${relationship.policyTypeName || ''}</span></td>
                    <td><span class="text-muted">${ownerDisplay}</span></td>
                    <td>
                        <div class="action-buttons">
                            <button type="button" class="btn btn-sm btn-success" onclick="addPolicyRow()" title="${window.I18n?.t('impactEdit.buttons.addRow') || 'Add row'}">
                                <i class="fas fa-plus"></i>
                            </button>
                            <button type="button" class="btn btn-sm btn-danger" onclick="deletePolicyRow('${rowId}')" title="${window.I18n?.t('impactEdit.buttons.deleteRow') || 'Delete row'}">
                                <i class="fas fa-minus"></i>
                            </button>
                        </div>
                    </td>
                </tr>
            `;
        });
        
        console.log('Process Impact Edit: renderPolicyTable rendered', renderedCount, 'rows');
        tbody.innerHTML = html;
        if (footer) footer.textContent = `${policyRelationships.length} record${policyRelationships.length !== 1 ? 's' : ''}`;
    }

    function addPolicyRow() {
        saveCurrentPolicyFormData();
        const newRelationship = {
            id: 'new-' + Date.now(),
            relationType: null,
            policyId: null,
            policyName: null,
            policyRefNumber: null,
            policyTypeName: null,
            policyOwnerName: null
        };
        policyRelationships.push(newRelationship);
        renderPolicyTable();
    }

    function deletePolicyRow(id) {
        saveCurrentPolicyFormData();
        if (policyRelationships.length <= 1) {
            policyRelationships = [{
                id: 'new-empty',
                relationType: null,
                policyId: null,
                policyName: null,
                policyRefNumber: null,
                policyTypeName: null,
                policyOwnerName: null
            }];
        } else {
            policyRelationships = policyRelationships.filter(r => r.id != id);
        }
        renderPolicyTable();
    }

    function saveCurrentPolicyFormData() {
        const tbody = document.querySelector('#policyImpactTableBody');
        if (!tbody) return;
        
        const rows = tbody.querySelectorAll('tr');
        const updatedRelationships = [];
        
        rows.forEach((row, index) => {
                const relationTypeSelect = row.querySelector('[data-field="relationType"]');
                const policyIdSelect = row.querySelector('[data-field="policyId"]');
            const rowId = row.getAttribute('data-id');
            
            // Get existing relationship or create new one (preserve existing data)
            let relationship = policyRelationships[index] || {
                id: rowId && !rowId.toString().startsWith('new-') ? parseInt(rowId) : null,
                policyId: null,
                relationType: null,
                policyName: null,
                policyRefNumber: null,
                policyTypeName: null,
                policyOwnerName: null
            };
            
            // Store previous values to preserve them if policyId hasn't changed
            const previousPolicyId = relationship.policyId;
            const previousPolicyRefNumber = relationship.policyRefNumber;
            const previousPolicyTypeName = relationship.policyTypeName;
            
            // Update from form fields
                if (relationTypeSelect) {
                relationship.relationType = relationTypeSelect.value || null;
                }
                if (policyIdSelect) {
                relationship.policyId = policyIdSelect.value || null;
            }
            
            // Only update policy details if policyId changed or if missing
            if (relationship.policyId && policies.length > 0) {
                const policy = policies.find(p => (p.id || p.ID) == relationship.policyId);
                if (policy) {
                    relationship.policyName = policy.name || policy.Name || policy.primaryname || policy.PrimaryName || null;
                    
                    // Only update refNumber if missing or policyId changed
                    if (!previousPolicyRefNumber || previousPolicyId != relationship.policyId) {
                        relationship.policyRefNumber = policy.refNumber || policy.refnumber || policy.RefNumber || null;
                    } else {
                        relationship.policyRefNumber = previousPolicyRefNumber;
                    }
                    
                    // Preserve policyTypeName if it exists and policyId hasn't changed
                    // (policyTypeName is fetched via API in updatePolicyOwner, not in policies array)
                    if (previousPolicyTypeName && previousPolicyId == relationship.policyId) {
                        relationship.policyTypeName = previousPolicyTypeName;
                    } else if (!previousPolicyTypeName || previousPolicyId != relationship.policyId) {
                        // Clear if policyId changed or if missing, but don't try to get from policies array
                        relationship.policyTypeName = null;
                    }
                }
            } else {
                // Clear if no policy selected, but preserve if just reloading
                if (!relationship.policyId) {
                    relationship.policyRefNumber = null;
                    relationship.policyTypeName = null;
                }
            }
            
            updatedRelationships.push(relationship);
        });
        
        // Update the array with all collected relationships
        policyRelationships = updatedRelationships;
        console.log('Process Impact Edit: saveCurrentPolicyFormData collected', policyRelationships.length, 'policy relationships');
    }

    async function updatePolicyOwner(selectElement) {
        const policyId = selectElement.value;
        const row = selectElement.closest('tr');
        
        // Column indices: 1=relationType, 2=policyId, 3=ref, 4=type, 5=owner, 6=actions
        const refCell = row.querySelector('td:nth-child(3)');
        const typeCell = row.querySelector('td:nth-child(4)');
        const ownerCell = row.querySelector('td:nth-child(5)');
        
        if (policyId) {
            // Find the policy from the policies array (has refNumber)
            const policy = policies.find(p => (p.id || p.ID) == policyId);
            
            // Fetch full policy details to get policy type
            let policyTypeName = null;
            let refNumber = null;
            
            if (policy) {
                refNumber = policy.refNumber || policy.refnumber || policy.RefNumber || '';
            }
            
            // Fetch full policy details to get type information
            try {
                const response = await fetch(`/api/policy/${policyId}`, {
                    method: 'GET',
                    credentials: 'include',
                    headers: { 'Content-Type': 'application/json' }
                });
                
                if (response.ok) {
                    const policyDetails = await response.json();
                    console.log('Policy details fetched:', policyDetails);
                    refNumber = policyDetails.refNumber || policyDetails.refnumber || policyDetails.RefNumber || refNumber || '';
                    
                    // Get policy type name from policyType ID using the lookup
                    // Policy model uses @SerializedName(value = "policyType", alternate = {"Policy_Type"})
                    const policyTypeId = policyDetails.policyType || policyDetails.PolicyType || policyDetails.Policy_Type;
                    console.log('Policy type ID from details:', policyTypeId);
                    console.log('Available policy types:', policyTypes);
                    
                    if (policyTypeId && policyTypes.length > 0) {
                        // PolicyTypeServlet returns {id, primaryname} - lowercase
                        const policyType = policyTypes.find(pt => (pt.id || pt.ID) == policyTypeId);
                        console.log('Found policy type:', policyType);
                        if (policyType) {
                            // API returns "primaryname" (lowercase), not "primaryName"
                            policyTypeName = policyType.primaryname || policyType.primaryName || policyType.PrimaryName || policyType.name || policyType.Name || null;
                            console.log('Policy type name resolved:', policyTypeName);
                        } else {
                            console.warn('Policy type not found for ID:', policyTypeId);
                        }
                    } else {
                        console.warn('Policy type ID not found or policyTypes array is empty');
                    }
                }
            } catch (error) {
                console.error('Error fetching policy details:', error);
            }
            
            // Update Ref column
            if (refCell) {
                refCell.innerHTML = `<span class="text-muted">${refNumber || ''}</span>`;
            }
            
            // Update Type column
            if (typeCell) {
                typeCell.innerHTML = `<span class="text-muted">${policyTypeName || ''}</span>`;
            }
            
            // Update the relationship data structure
            const rowIndex = Array.from(row.parentElement.children).indexOf(row);
            if (policyRelationships[rowIndex]) {
                policyRelationships[rowIndex].policyRefNumber = refNumber || null;
                policyRelationships[rowIndex].policyTypeName = policyTypeName || null;
            }
            
            // Update owner
            if (ownerCell) {
                await updatePolicyOwnerDisplay(ownerCell, policyId, row);
            }
        } else {
            // Clear all columns if no policy selected
            if (refCell) {
                refCell.innerHTML = '<span class="text-muted"></span>';
            }
            if (typeCell) {
                typeCell.innerHTML = '<span class="text-muted"></span>';
            }
            if (ownerCell) {
                ownerCell.innerHTML = '<span class="text-muted"></span>';
            }
        }
    }

    async function updatePolicyOwnerDisplay(ownerCell, policyId, row) {
        try {
            const response = await fetch(`/api/process-impact/policy-owner/${policyId}`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            const ownerName = data.ownerName || data.policyOwnerName || 'No owner';
            ownerCell.innerHTML = `<span class="text-muted">${ownerName}</span>`;
            
            // Update the relationship data structure
            if (row) {
                const rowIndex = Array.from(row.parentElement.children).indexOf(row);
                if (policyRelationships[rowIndex]) {
                    policyRelationships[rowIndex].policyOwnerName = data.ownerName || data.policyOwnerName || null;
                }
            }
        } catch (error) {
            console.error('Error fetching policy owner:', error);
            ownerCell.innerHTML = '<span class="text-muted">No owner</span>';
        }
    }

    async function savePolicyRelationships() {
        try {
            saveCurrentPolicyFormData();
            
            const relationships = policyRelationships
                .filter(r => r.policyId && r.relationType)
                .map(r => ({
                    policyId: parseInt(r.policyId),
                    relationType: parseInt(r.relationType)
                }));

            console.log('Process Impact Edit: savePolicyRelationships sending', relationships.length, 'relationships:', relationships);

            const requestData = {
                processId: currentProcessId,
                relationships: relationships
            };

            const response = await fetch('/api/process-impact/policies/save', {
                method: 'POST',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(requestData)
            });

            if (!response.ok) {
                const errorText = await response.text();
                throw new Error(`HTTP error! status: ${response.status}, message: ${errorText}`);
            }

            const result = await response.json();
            
            if (result.success) {
                originalPolicyData = JSON.parse(JSON.stringify(policyRelationships));
                return true;
            } else {
                return { success: false, message: result.message || 'Failed to save policy relationships' };
            }
        } catch (error) {
            console.error('Error saving policy relationships:', error);
            return { success: false, message: 'Network error: ' + error.message };
        }
    }

    // ===== SYSTEM INTERFACE RELATIONSHIPS =====
    
    async function loadInterfaceRelationships() {
        try {
            const response = await fetch(`/api/process-impact/${currentProcessId}/interfaces${getViewParam()}`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            interfaceRelationships = Array.isArray(data) ? data : [];
            originalInterfaceData = JSON.parse(JSON.stringify(interfaceRelationships));
            renderInterfaceTable();

        } catch (error) {
            console.error('Error loading interface relationships:', error);
            interfaceRelationships = [];
            originalInterfaceData = [];
            renderInterfaceTable();
        }
    }

    async function loadInterfaceRelationTypes() {
        try {
            const response = await fetch('/api/process-impact/interface-relation-types', {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            interfaceRelationTypes = Array.isArray(data) ? data : [];

        } catch (error) {
            console.error('Error loading interface relation types:', error);
            interfaceRelationTypes = [];
        }
    }

    async function loadInterfaces() {
        try {
            const response = await fetch('/api/process-impact/interfaces', {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            if (Array.isArray(data)) {
                interfaces = data;
            } else {
                interfaces = [];
            }

        } catch (error) {
            console.error('Error loading interfaces:', error);
            interfaces = [];
        }
    }
    
    function renderInterfaceTable() {
        const tbody = document.querySelector('#interfaceImpactTableBody');
        const footer = document.querySelector('#interfaceImpactFooter');
        
        if (!tbody) return;
        
        if (interfaceRelationships.length === 0) {
            interfaceRelationships.push({
                id: 'new-empty',
                relationType: null,
                interfaceId: null,
                interfaceName: null
            });
        }
        
        let html = '';
        interfaceRelationships.forEach((relationship, index) => {
            const rowId = relationship.id || 'new-' + index;
            
            html += `
                <tr data-id="${rowId}">
                    <td>
                        <select class="form-control" data-field="relationType">
                            <option value="">Select relationship type</option>
                            ${interfaceRelationTypes.map(rt => 
                                `<option value="${rt.id || rt.ID}" ${relationship.relationType == (rt.id || rt.ID) ? 'selected' : ''}>${rt.primaryname || rt.primaryName || rt.PrimaryName || 'Unnamed Type'}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td>
                        <select class="form-control" data-field="interfaceId">
                            <option value="">Select interface</option>
                            ${interfaces.map(i => 
                                `<option value="${i.id || i.ID}" ${relationship.interfaceId == (i.id || i.ID) ? 'selected' : ''}>${i.name || i.Name || 'Unnamed Interface'}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td>
                        <div class="action-buttons">
                            <button type="button" class="btn btn-sm btn-success" onclick="addInterfaceRow()" title="${window.I18n?.t('impactEdit.buttons.addRow') || 'Add row'}">
                                <i class="fas fa-plus"></i>
                            </button>
                            <button type="button" class="btn btn-sm btn-danger" onclick="deleteInterfaceRow('${rowId}')" title="${window.I18n?.t('impactEdit.buttons.deleteRow') || 'Delete row'}">
                                <i class="fas fa-minus"></i>
                            </button>
                        </div>
                    </td>
                </tr>
            `;
        });
        
        tbody.innerHTML = html;
        if (footer) footer.textContent = `${interfaceRelationships.length} record${interfaceRelationships.length !== 1 ? 's' : ''}`;
    }

    function addInterfaceRow() {
        saveCurrentInterfaceFormData();
        const newRelationship = {
            id: 'new-' + Date.now(),
            relationType: null,
            interfaceId: null,
            interfaceName: null
        };
        interfaceRelationships.push(newRelationship);
        renderInterfaceTable();
    }

    function deleteInterfaceRow(id) {
        saveCurrentInterfaceFormData();
        if (interfaceRelationships.length <= 1) {
            interfaceRelationships = [{
                id: 'new-empty',
                relationType: null,
                interfaceId: null,
                interfaceName: null
            }];
        } else {
            interfaceRelationships = interfaceRelationships.filter(r => r.id != id);
        }
        renderInterfaceTable();
    }

    function saveCurrentInterfaceFormData() {
        const tbody = document.querySelector('#interfaceImpactTableBody');
        if (!tbody) return;
        
        const rows = tbody.querySelectorAll('tr');
        const updatedRelationships = [];
        
        rows.forEach((row, index) => {
                const relationTypeSelect = row.querySelector('[data-field="relationType"]');
                const interfaceIdSelect = row.querySelector('[data-field="interfaceId"]');
            const rowId = row.getAttribute('data-id');
            
            let relationship = interfaceRelationships[index] || {
                id: rowId && !rowId.toString().startsWith('new-') ? parseInt(rowId) : null,
                interfaceId: null,
                relationType: null,
                interfaceName: null
            };
                
                if (relationTypeSelect) {
                relationship.relationType = relationTypeSelect.value || null;
                }
                if (interfaceIdSelect) {
                relationship.interfaceId = interfaceIdSelect.value || null;
                
                // Update interface name if interface is selected
                if (relationship.interfaceId && interfaces.length > 0) {
                    const selectedInterface = interfaces.find(i => (i.id || i.ID) == relationship.interfaceId);
                    if (selectedInterface) {
                        relationship.interfaceName = selectedInterface.name || selectedInterface.Name || null;
                    }
                } else {
                    relationship.interfaceName = null;
                }
            }
            
            updatedRelationships.push(relationship);
        });
        
        interfaceRelationships = updatedRelationships;
        console.log('Process Impact Edit: saveCurrentInterfaceFormData collected', interfaceRelationships.length, 'interface relationships');
    }

    // Interface owner functionality removed - interface table does not have owner column

    async function saveInterfaceRelationships() {
        try {
            saveCurrentInterfaceFormData();
            
            console.log('Process Impact Edit: saveInterfaceRelationships - interfaceRelationships array:', interfaceRelationships);
            console.log('Process Impact Edit: saveInterfaceRelationships - interfaceRelationships length:', interfaceRelationships.length);
            
            const relationships = interfaceRelationships
                .filter(r => r.interfaceId && r.relationType)
                .map(r => ({
                    interfaceId: parseInt(r.interfaceId),
                    relationType: parseInt(r.relationType)
                }));

            console.log('Process Impact Edit: saveInterfaceRelationships - filtered relationships:', relationships);
            console.log('Process Impact Edit: saveInterfaceRelationships - relationships to send:', relationships.length);

            const requestData = {
                processId: currentProcessId,
                relationships: relationships
            };

            console.log('Process Impact Edit: saveInterfaceRelationships - requestData:', requestData);

            const response = await fetch('/api/process-impact/interfaces/save', {
                method: 'POST',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(requestData)
            });

            if (!response.ok) {
                const errorText = await response.text();
                console.error('Process Impact Edit: saveInterfaceRelationships - HTTP error:', response.status, errorText);
                throw new Error(`HTTP error! status: ${response.status}, message: ${errorText}`);
            }

            const result = await response.json();
            console.log('Process Impact Edit: saveInterfaceRelationships - API response:', result);
            
            if (result.success) {
                originalInterfaceData = JSON.parse(JSON.stringify(interfaceRelationships));
                console.log('Process Impact Edit: saveInterfaceRelationships - SUCCESS');
                return true;
            } else {
                console.error('Process Impact Edit: saveInterfaceRelationships - API returned failure:', result.message);
                return { success: false, message: result.message || 'Failed to save interface relationships' };
            }
        } catch (error) {
            console.error('Process Impact Edit: Error saving interface relationships:', error);
            return { success: false, message: 'Network error: ' + error.message };
        }
    }

    // ===== LEGAL ENTITY RELATIONSHIPS =====
    
    async function loadLegalRelationships() {
        try {
            const response = await fetch(`/api/process-impact/${currentProcessId}/legals${getViewParam()}`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
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
            const response = await fetch('/api/process-impact/legal-relation-types', {
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
            const response = await fetch(withImpactSegment('/api/LegalEntity/'), {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            if (data && data.success && Array.isArray(data.data)) {
                legals = data.data;
            } else if (Array.isArray(data)) {
                legals = data;
            } else {
                legals = [];
            }
            
            console.log('Legal entities loaded:', legals.length);
            if (legals.length > 0) {
                console.log('Sample legal entity:', legals[0]);
            }

        } catch (error) {
            console.error('Error loading legal entities:', error);
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
                legalName: null,
                legalOwnerName: null
            });
        }
        
        let html = '';
        legalRelationships.forEach((relationship, index) => {
            const rowId = relationship.id || 'new-' + index;
            const isNewRow = !relationship.id || String(relationship.id).startsWith('new-');
            const ownerName = relationship.legalOwnerName || relationship.ownerName || '';
            const ownerDisplay = isNewRow && !relationship.legalId ? '' : (ownerName || 'No owner');
            
            html += `
                <tr data-id="${rowId}">
                    <td>
                        <select class="form-control" data-field="relationType">
                            <option value="">Select relationship type</option>
                            ${legalRelationTypes.map(rt => 
                                `<option value="${rt.id || rt.ID}" ${relationship.relationType == (rt.id || rt.ID) ? 'selected' : ''}>${rt.primaryName || rt.PrimaryName}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td>
                        <select class="form-control" data-field="legalId" onchange="updateLegalOwner(this)">
                            <option value="">Select legal entity</option>
                            ${legals.map(l => {
                                // Debug: log the legal entity object
                                if (legals.indexOf(l) === 0) {
                                    console.log('First legal entity object:', l);
                                    console.log('Available keys:', Object.keys(l));
                                }
                                const name = l.longName || l.LongName || l.long_name || l.LONG_NAME ||
                                           l.shortName || l.ShortName || l.short_name || l.SHORT_NAME ||
                                           l.name || l.Name || l.NAME ||
                                           l.primaryName || l.PrimaryName || l.primary_name || l.PRIMARY_NAME ||
                                           l.description || l.Description || l.DESCRIPTION ||
                                           `Legal Entity ${l.id || l.ID}`;
                                const id = l.id || l.ID;
                                const selected = relationship.legalId == id ? 'selected' : '';
                                return `<option value="${id}" ${selected}>${name}</option>`;
                            }).join('')}
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
            legalName: null,
            legalOwnerName: null
        };
        legalRelationships.push(newRelationship);
        renderLegalTable();
    }

    function deleteLegalRow(id) {
        saveCurrentLegalFormData();
        if (legalRelationships.length <= 1) {
            legalRelationships = [{
                id: 'new-empty',
                relationType: null,
                legalId: null,
                legalName: null,
                legalOwnerName: null
            }];
        } else {
            legalRelationships = legalRelationships.filter(r => r.id != id);
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
                    legalRelationships[index].legalId = legalIdSelect.value || null;
                }
            }
        });
    }

    async function updateLegalOwner(selectElement) {
        const legalId = selectElement.value;
        const row = selectElement.closest('tr');
        const ownerCell = row.querySelector('td:nth-child(3)');
        
        if (ownerCell && legalId) {
            await updateLegalOwnerDisplay(ownerCell, legalId, row);
        } else if (ownerCell) {
            ownerCell.innerHTML = '<span class="text-muted"></span>';
        }
    }

    async function updateLegalOwnerDisplay(ownerCell, legalId, row) {
        try {
            const response = await fetch(`/api/process-impact/legal-owner/${legalId}`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            const ownerName = data.ownerName || data.legalOwnerName || 'No owner';
            ownerCell.innerHTML = `<span class="text-muted">${ownerName}</span>`;
            
            // Update the relationship data structure
            if (row) {
                const rowIndex = Array.from(row.parentElement.children).indexOf(row);
                if (legalRelationships[rowIndex]) {
                    legalRelationships[rowIndex].legalOwnerName = data.ownerName || data.legalOwnerName || null;
                }
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
                processId: currentProcessId,
                relationships: relationships
            };

            const response = await fetch('/api/process-impact/legals/save', {
                method: 'POST',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(requestData)
            });

            if (!response.ok) {
                const errorText = await response.text();
                throw new Error(`HTTP error! status: ${response.status}, message: ${errorText}`);
            }

            const result = await response.json();
            
            if (result.success) {
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

    // ==================== DATASET FUNCTIONS ====================
    
    // Load dataset relation types
    async function loadDatasetRelationTypes() {
        try {
            const response = await fetch('/api/process-impact/dataset-relation-types', {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });
            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
            const data = await response.json();
            datasetRelationTypes = Array.isArray(data) ? data : [];
            console.log('Loaded dataset relation types:', datasetRelationTypes.length);
        } catch (error) {
            console.error('Error loading dataset relation types:', error);
            datasetRelationTypes = [];
        }
    }

    // Load datasets
    async function loadDatasets() {
        try {
            console.log('=== LOADING DATASETS START ===');
            const response = await fetch('/api/process-impact/datasets-list', {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });
            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
            const data = await response.json();
            datasets = Array.isArray(data) ? data : [];
            console.log('Loaded datasets:', datasets.length);
            console.log('=== LOADING DATASETS END ===');
        } catch (error) {
            console.error('Error loading datasets:', error);
            datasets = [];
        }
    }

    // Load dataset relationships
    async function loadDatasetRelationships() {
        try {
            console.log('Loading dataset relationships for process:', currentProcessId);
            const response = await fetch(`/api/process-impact/${currentProcessId}/datasets${getViewParam()}`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });
            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
            const data = await response.json();
            console.log('Dataset relationships API response:', data);
            datasetRelationships = Array.isArray(data) ? data : [];
            originalDatasetData = JSON.parse(JSON.stringify(datasetRelationships));
            console.log('Loaded dataset relationships:', datasetRelationships.length);
            if (datasetRelationships.length > 0) {
                console.log('First dataset relationship:', datasetRelationships[0]);
                console.log('Dataset owner name:', datasetRelationships[0].datasetOwnerName);
            }
            renderDatasetTable();
        } catch (error) {
            console.error('Error loading dataset relationships:', error);
            datasetRelationships = [];
            originalDatasetData = [];
            renderDatasetTable();
        }
    }

    // Render dataset table
    function renderDatasetTable() {
        const tbody = document.getElementById('datasetImpactTableBody');
        const footer = document.getElementById('datasetImpactFooter');
        
        if (!tbody) return;

        if (datasetRelationships.length === 0) {
            datasetRelationships.push({
                id: 'new-empty',
                systemId: null,
                datasetId: null,
                relationType: null,
                datasetName: '',
                datasetOwnerName: '',
                systemName: ''
            });
        }

        let html = '';
        datasetRelationships.forEach((relationship, index) => {
            const rowId = relationship.id || 'new-' + index;
            html += `
                <tr data-id="${rowId}">
                    <td>
                        <select class="form-control" data-field="relationType">
                            <option value="">Select relationship type</option>
                            ${datasetRelationTypes.map(rt => 
                                `<option value="${rt.id || rt.ID}" ${relationship.relationType == (rt.id || rt.ID) ? 'selected' : ''}>${rt.primaryName || rt.primaryname || rt.PrimaryName || 'Unnamed Type'}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td>
                        <select class="form-control" data-field="systemId" onchange="filterDatasetsBySystem(this);">
                            <option value="">Select system</option>
                            ${systems.map(s => 
                                `<option value="${s.id || s.ID}" data-system-name="${s.name || s.Name || s.primaryname || s.PrimaryName}" ${relationship.systemId == (s.id || s.ID) ? 'selected' : ''}>${s.name || s.Name || s.primaryname || s.PrimaryName || 'Unnamed System'}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td>
                        <select class="form-control" data-field="datasetId" data-system-id="${relationship.systemId || ''}" onchange="updateDatasetOwner(this);">
                            <option value="">Select dataset</option>
                            ${datasets.map(d => {
                                const isSelected = relationship.datasetId == (d.ID || d.id);
                                const showOption = !relationship.systemId || (d.MasterSource || d.masterSource) == relationship.systemId;
                                return `<option value="${d.ID || d.id}" ${isSelected ? 'selected' : ''} ${showOption ? 'style="display:block;"' : 'style="display:none;"'}>${d.PrimaryName || d.primaryName || d.name || 'Unnamed Dataset'}</option>`;
                            }).join('')}
                        </select>
                    </td>
                    <td title="${relationship.datasetOwnerName || 'No owner'}">
                        <span class="text-muted">${relationship.datasetOwnerName || 'No owner'}</span>
                    </td>
                    <td>
                        <div class="action-buttons">
                            <button type="button" class="btn btn-sm btn-success" onclick="addDatasetRow()" title="${window.I18n?.t('impactEdit.buttons.addRow') || 'Add row'}">
                                <i class="fas fa-plus"></i>
                            </button>
                            <button type="button" class="btn btn-sm btn-danger" onclick="deleteDatasetRow('${rowId}')" title="${window.I18n?.t('impactEdit.buttons.deleteRow') || 'Delete row'}">
                                <i class="fas fa-minus"></i>
                            </button>
                        </div>
                    </td>
                </tr>
            `;
        });

        tbody.innerHTML = html;
        if (footer) footer.textContent = `${datasetRelationships.length} record${datasetRelationships.length !== 1 ? 's' : ''}`;
    }

    // Save current dataset form data
    function saveCurrentDatasetFormData() {
        const tbody = document.querySelector('#datasetImpactTableBody');
        if (!tbody) return;
        
        const rows = tbody.querySelectorAll('tr');
        const updatedRelationships = [];
        
        rows.forEach((row, index) => {
            const relationTypeSelect = row.querySelector('[data-field="relationType"]');
            const systemSelect = row.querySelector('[data-field="systemId"]');
            const datasetSelect = row.querySelector('[data-field="datasetId"]');
            const rowId = row.getAttribute('data-id');
            const ownerCell = row.querySelector('td:nth-child(4)');
            
            let relationship = datasetRelationships[index] || {
                id: rowId && !rowId.toString().startsWith('new-') ? parseInt(rowId) : null,
                systemId: null,
                datasetId: null,
                relationType: null,
                datasetName: null,
                datasetOwnerName: null,
                systemName: null
            };
            
            if (relationTypeSelect) {
                relationship.relationType = relationTypeSelect.value || null;
            }
            if (systemSelect) {
                relationship.systemId = systemSelect.value || null;
                const systemName = systemSelect.options[systemSelect.selectedIndex]?.getAttribute('data-system-name') || '';
                relationship.systemName = systemName;
            }
            if (datasetSelect) {
                relationship.datasetId = datasetSelect.value || null;
                const selectedOption = datasetSelect.options[datasetSelect.selectedIndex];
                if (selectedOption) {
                    relationship.datasetName = selectedOption.text;
                }
            }
            if (ownerCell) {
                const ownerSpan = ownerCell.querySelector('span');
                if (ownerSpan) {
                    relationship.datasetOwnerName = ownerSpan.textContent.trim();
                }
            }
            
            updatedRelationships.push(relationship);
        });
        
        datasetRelationships = updatedRelationships;
        console.log('Process Impact Edit: saveCurrentDatasetFormData collected', datasetRelationships.length, 'dataset relationships');
    }

    // Add dataset row
    function addDatasetRow() {
        saveCurrentDatasetFormData();
        datasetRelationships.push({
            id: 'new-' + Date.now(),
            systemId: null,
            datasetId: null,
            relationType: null,
            datasetName: '',
            datasetOwnerName: '',
            systemName: ''
        });
        renderDatasetTable();
    }

    // Delete dataset row
    function deleteDatasetRow(id) {
        saveCurrentDatasetFormData();
        if (datasetRelationships.length <= 1) {
            datasetRelationships[0] = {
                id: 'new-' + Date.now(),
                systemId: null,
                datasetId: null,
                relationType: null,
                datasetName: '',
                datasetOwnerName: '',
                systemName: ''
            };
        } else {
            datasetRelationships = datasetRelationships.filter(rel => String(rel.id) !== String(id));
        }
        renderDatasetTable();
    }

    // Filter datasets by system
    function filterDatasetsBySystem(selectElement) {
        const systemId = selectElement.value;
        const row = selectElement.closest('tr');
        const datasetSelect = row.querySelector('select[data-field="datasetId"]');
        const ownerCell = row.querySelector('td:nth-child(4)');
        
        if (!datasetSelect) return;
        
        datasetSelect.setAttribute('data-system-id', systemId || '');
        const options = datasetSelect.querySelectorAll('option');
        
        if (systemId) {
            options.forEach(option => {
                if (option.value) {
                    const dataset = datasets.find(d => (d.ID || d.id) == option.value);
                    if (dataset && (dataset.MasterSource || dataset.masterSource) == systemId) {
                        option.style.display = 'block';
                    } else {
                        option.style.display = 'none';
                        if (datasetSelect.value == option.value) {
                            datasetSelect.value = '';
                            if (ownerCell) ownerCell.innerHTML = '<span class="text-muted">No owner</span>';
                        }
                    }
                }
            });
        } else {
            options.forEach(option => {
                option.style.display = 'block';
            });
        }
    }

    // Update dataset owner
    async function updateDatasetOwner(selectElement) {
        const datasetId = selectElement.value;
        const row = selectElement.closest('tr');
        const ownerCell = row.querySelector('td:nth-child(4)');
        const rowIndex = Array.from(row.parentElement.children).indexOf(row);
        
        if (!ownerCell) return;
        
        if (datasetId) {
            try {
                const response = await fetch(`/api/process-impact/dataset-owner/${datasetId}`, {
                    method: 'GET',
                    credentials: 'include',
                    headers: { 'Content-Type': 'application/json' }
                });
                
                if (response.ok) {
                    const ownerData = await response.json();
                    const ownerName = ownerData.ownerName || 'No owner';
                    ownerCell.innerHTML = `<span class="text-muted">${ownerName}</span>`;
                    if (datasetRelationships[rowIndex]) {
                        datasetRelationships[rowIndex].datasetOwnerName = ownerName;
                    }
                } else {
                    ownerCell.innerHTML = '<span class="text-muted">No owner</span>';
                    if (datasetRelationships[rowIndex]) {
                        datasetRelationships[rowIndex].datasetOwnerName = null;
                    }
                }
            } catch (error) {
                console.error('Error fetching dataset owner:', error);
                ownerCell.innerHTML = '<span class="text-muted">No owner</span>';
                if (datasetRelationships[rowIndex]) {
                    datasetRelationships[rowIndex].datasetOwnerName = null;
                }
            }
        } else {
            ownerCell.innerHTML = '<span class="text-muted">No owner</span>';
            if (datasetRelationships[rowIndex]) {
                datasetRelationships[rowIndex].datasetOwnerName = null;
            }
        }
    }

    // Save dataset relationships
    async function saveDatasetRelationships() {
        try {
            saveCurrentDatasetFormData();
            
            const relationships = datasetRelationships
                .filter(r => r.relationType && r.datasetId)
                .map(r => ({
                    datasetId: parseInt(r.datasetId),
                    relationType: parseInt(r.relationType)
                }));
            
            const requestData = {
                processId: currentProcessId,
                relationships: relationships
            };
            
            const response = await fetch('/api/process-impact/datasets/save', {
                method: 'POST',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(requestData)
            });
            
            if (!response.ok) {
                console.error('Failed to save dataset relationships:', response.status);
                return {
                    success: false,
                    message: `Failed to save dataset relationships (HTTP ${response.status})`
                };
            }

            const result = await response.json();
            console.log('Dataset relationships saved successfully:', result);
            const success = result && result.success === true;
            
            if (!success) {
                return {
                    success: false,
                    message: result.message || 'Failed to save dataset relationships'
                };
            }
            return true;
        } catch (error) {
            console.error('Error saving dataset relationships:', error);
            return {
                success: false,
                message: 'Error saving dataset relationships: ' + (error.message || 'Unknown error')
            };
        }
    }

    // ==================== ATTRIBUTE FUNCTIONS ====================
    
    // Load attribute relation types
    async function loadAttributeRelationTypes() {
        try {
            const response = await fetch('/api/process-impact/attribute-relation-types', {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });
            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
            const data = await response.json();
            attributeRelationTypes = Array.isArray(data) ? data : [];
            console.log('Loaded attribute relation types:', attributeRelationTypes.length);
        } catch (error) {
            console.error('Error loading attribute relation types:', error);
            attributeRelationTypes = [];
        }
    }

    // Load attributes
    async function loadAttributes() {
        try {
            console.log('=== LOADING ATTRIBUTES START ===');
            const response = await fetch('/api/process-impact/attributes-list', {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });
            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
            const data = await response.json();
            attributes = Array.isArray(data) ? data : [];
            console.log('Loaded attributes:', attributes.length);
            console.log('=== LOADING ATTRIBUTES END ===');
        } catch (error) {
            console.error('Error loading attributes:', error);
            attributes = [];
        }
    }

    // Load attribute relationships
    async function loadAttributeRelationships() {
        try {
            console.log('Loading attribute relationships for process:', currentProcessId);
            const response = await fetch(`/api/process-impact/${currentProcessId}/attributes${getViewParam()}`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });
            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
            const data = await response.json();
            console.log('Attribute relationships API response:', data);
            attributeRelationships = Array.isArray(data) ? data : [];
            originalAttributeData = JSON.parse(JSON.stringify(attributeRelationships));
            console.log('Loaded attribute relationships:', attributeRelationships.length);
            if (attributeRelationships.length > 0) {
                console.log('First attribute relationship:', attributeRelationships[0]);
                console.log('Attribute owner name:', attributeRelationships[0].attributeOwnerName);
            }
            renderAttributeTable();
        } catch (error) {
            console.error('Error loading attribute relationships:', error);
            attributeRelationships = [];
            originalAttributeData = [];
            renderAttributeTable();
        }
    }

    // Render attribute table
    function renderAttributeTable() {
        const tbody = document.getElementById('attributeImpactTableBody');
        const footer = document.getElementById('attributeImpactFooter');
        
        if (!tbody) return;

        if (attributeRelationships.length === 0) {
            attributeRelationships.push({
                id: 'new-empty',
                attributeId: null,
                relationType: null,
                attributeName: '',
                attributeOwnerName: '',
                datasetName: '',
                systemName: '',
                systemId: null,
                datasetId: null
            });
        }

        let html = '';
        attributeRelationships.forEach((relationship, index) => {
            const rowId = relationship.id || 'new-' + index;
            html += `
                <tr data-id="${rowId}">
                    <td>
                        <select class="form-control" data-field="relationType">
                            <option value="">Select relationship type</option>
                            ${attributeRelationTypes.map(rt => 
                                `<option value="${rt.id || rt.ID}" ${relationship.relationType == (rt.id || rt.ID) ? 'selected' : ''}>${rt.primaryName || rt.primaryname || rt.PrimaryName || 'Unnamed Type'}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td>
                        <select class="form-control" data-field="systemId" onchange="filterDatasetsBySystemForAttribute(this);">
                            <option value="">Select system</option>
                            ${systems.map(s => 
                                `<option value="${s.id || s.ID}" data-system-name="${s.name || s.Name || s.primaryname || s.PrimaryName}" ${relationship.systemId == (s.id || s.ID) ? 'selected' : ''}>${s.name || s.Name || s.primaryname || s.PrimaryName || 'Unnamed System'}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td>
                        <select class="form-control" data-field="datasetId" data-system-id="${relationship.systemId || ''}" onchange="filterAttributesByDataset(this);">
                            <option value="">Select dataset</option>
                            ${datasets.map(d => {
                                const isSelected = relationship.datasetId == (d.ID || d.id);
                                const showOption = !relationship.systemId || (d.MasterSource || d.masterSource) == relationship.systemId;
                                return `<option value="${d.ID || d.id}" ${isSelected ? 'selected' : ''} ${showOption ? 'style="display:block;"' : 'style="display:none;"'}>${d.PrimaryName || d.primaryName || d.name || 'Unnamed Dataset'}</option>`;
                            }).join('')}
                        </select>
                    </td>
                    <td>
                        <select class="form-control" data-field="attributeId" data-dataset-id="${relationship.datasetId || ''}" onchange="updateAttributeOwner(this);">
                            <option value="">Select attribute</option>
                            ${attributes.map(a => {
                                const isSelected = relationship.attributeId == (a.ID || a.id);
                                const showOption = !relationship.datasetId || (a.Dataset_ID || a.datasetId || a.dataset_id) == relationship.datasetId;
                                return `<option value="${a.ID || a.id}" ${isSelected ? 'selected' : ''} ${showOption ? 'style="display:block;"' : 'style="display:none;"'}>${a.PrimaryName || a.primaryName || a.name || 'Unnamed Attribute'}</option>`;
                            }).join('')}
                        </select>
                    </td>
                    <td title="${relationship.attributeOwnerName || 'No owner'}">
                        <span class="text-muted">${relationship.attributeOwnerName || 'No owner'}</span>
                    </td>
                    <td>
                        <div class="action-buttons">
                            <button type="button" class="btn btn-sm btn-success" onclick="addAttributeRow()" title="${window.I18n?.t('impactEdit.buttons.addRow') || 'Add row'}">
                                <i class="fas fa-plus"></i>
                            </button>
                            <button type="button" class="btn btn-sm btn-danger" onclick="deleteAttributeRow('${rowId}')" title="${window.I18n?.t('impactEdit.buttons.deleteRow') || 'Delete row'}">
                                <i class="fas fa-minus"></i>
                            </button>
                        </div>
                    </td>
                </tr>
            `;
        });

        tbody.innerHTML = html;
        if (footer) footer.textContent = `${attributeRelationships.length} record${attributeRelationships.length !== 1 ? 's' : ''}`;
    }

    // Save current attribute form data
    function saveCurrentAttributeFormData() {
        const tbody = document.querySelector('#attributeImpactTableBody');
        if (!tbody) return;
        
        const rows = tbody.querySelectorAll('tr');
        const updatedRelationships = [];
        
        rows.forEach((row, index) => {
            const relationTypeSelect = row.querySelector('[data-field="relationType"]');
            const systemSelect = row.querySelector('[data-field="systemId"]');
            const datasetSelect = row.querySelector('[data-field="datasetId"]');
            const attributeSelect = row.querySelector('[data-field="attributeId"]');
            const rowId = row.getAttribute('data-id');
            const ownerCell = row.querySelector('td:nth-child(5)');
            
            let relationship = attributeRelationships[index] || {
                id: rowId && !rowId.toString().startsWith('new-') ? parseInt(rowId) : null,
                attributeId: null,
                relationType: null,
                attributeName: null,
                attributeOwnerName: null,
                datasetName: null,
                systemName: null,
                systemId: null,
                datasetId: null
            };
            
            if (relationTypeSelect) {
                relationship.relationType = relationTypeSelect.value || null;
            }
            if (systemSelect) {
                relationship.systemId = systemSelect.value || null;
                const systemName = systemSelect.options[systemSelect.selectedIndex]?.getAttribute('data-system-name') || '';
                relationship.systemName = systemName;
            }
            if (datasetSelect) {
                relationship.datasetId = datasetSelect.value || null;
                const selectedOption = datasetSelect.options[datasetSelect.selectedIndex];
                if (selectedOption) {
                    relationship.datasetName = selectedOption.text;
                }
            }
            if (attributeSelect) {
                relationship.attributeId = attributeSelect.value || null;
                const selectedOption = attributeSelect.options[attributeSelect.selectedIndex];
                if (selectedOption) {
                    relationship.attributeName = selectedOption.text;
                }
            }
            if (ownerCell) {
                const ownerSpan = ownerCell.querySelector('span');
                if (ownerSpan) {
                    relationship.attributeOwnerName = ownerSpan.textContent.trim();
                }
            }
            
            updatedRelationships.push(relationship);
        });
        
        attributeRelationships = updatedRelationships;
        console.log('Process Impact Edit: saveCurrentAttributeFormData collected', attributeRelationships.length, 'attribute relationships');
    }

    // Add attribute row
    function addAttributeRow() {
        saveCurrentAttributeFormData();
        attributeRelationships.push({
            id: 'new-' + Date.now(),
            attributeId: null,
            relationType: null,
            attributeName: '',
            attributeOwnerName: '',
            datasetName: '',
            systemName: '',
            systemId: null,
            datasetId: null
        });
        renderAttributeTable();
    }

    // Delete attribute row
    function deleteAttributeRow(id) {
        saveCurrentAttributeFormData();
        if (attributeRelationships.length <= 1) {
            attributeRelationships[0] = {
                id: 'new-' + Date.now(),
                attributeId: null,
                relationType: null,
                attributeName: '',
                attributeOwnerName: '',
                datasetName: '',
                systemName: '',
                systemId: null,
                datasetId: null
            };
        } else {
            attributeRelationships = attributeRelationships.filter(rel => String(rel.id) !== String(id));
        }
        renderAttributeTable();
    }

    // Filter datasets by system for attribute
    function filterDatasetsBySystemForAttribute(selectElement) {
        const systemId = selectElement.value;
        const row = selectElement.closest('tr');
        const datasetSelect = row.querySelector('select[data-field="datasetId"]');
        const attributeSelect = row.querySelector('select[data-field="attributeId"]');
        
        if (!datasetSelect) return;
        
        datasetSelect.setAttribute('data-system-id', systemId || '');
        const options = datasetSelect.querySelectorAll('option');
        
        if (systemId) {
            options.forEach(option => {
                if (option.value) {
                    const dataset = datasets.find(d => (d.ID || d.id) == option.value);
                    if (dataset && (dataset.MasterSource || dataset.masterSource) == systemId) {
                        option.style.display = 'block';
                    } else {
                        option.style.display = 'none';
                        if (datasetSelect.value == option.value) {
                            datasetSelect.value = '';
                        }
                    }
                }
            });
        } else {
            options.forEach(option => {
                option.style.display = 'block';
            });
        }
        
        if (attributeSelect) {
            attributeSelect.value = '';
        }
    }

    // Filter attributes by dataset
    function filterAttributesByDataset(selectElement) {
        const datasetId = selectElement.value;
        const row = selectElement.closest('tr');
        const attributeSelect = row.querySelector('select[data-field="attributeId"]');
        
        if (!attributeSelect) return;
        
        attributeSelect.setAttribute('data-dataset-id', datasetId || '');
        const options = attributeSelect.querySelectorAll('option');
        
        if (datasetId) {
            options.forEach(option => {
                if (option.value) {
                    const attribute = attributes.find(a => (a.ID || a.id) == option.value);
                    if (attribute && (attribute.Dataset_ID || attribute.datasetId || attribute.dataset_id) == datasetId) {
                        option.style.display = 'block';
                    } else {
                        option.style.display = 'none';
                        if (attributeSelect.value == option.value) {
                            attributeSelect.value = '';
                        }
                    }
                }
            });
        } else {
            options.forEach(option => {
                option.style.display = 'block';
            });
        }
    }

    // Update attribute owner
    async function updateAttributeOwner(selectElement) {
        const attributeId = selectElement.value;
        const row = selectElement.closest('tr');
        const ownerCell = row.querySelector('td:nth-child(5)');
        const rowIndex = Array.from(row.parentElement.children).indexOf(row);
        
        if (!ownerCell) return;
        
        if (attributeId) {
            try {
                const response = await fetch(`/api/process-impact/attribute-owner/${attributeId}`, {
                    method: 'GET',
                    credentials: 'include',
                    headers: { 'Content-Type': 'application/json' }
                });
                
                if (response.ok) {
                    const ownerData = await response.json();
                    const ownerName = ownerData.ownerName || 'No owner';
                    ownerCell.innerHTML = `<span class="text-muted">${ownerName}</span>`;
                    if (attributeRelationships[rowIndex]) {
                        attributeRelationships[rowIndex].attributeOwnerName = ownerName;
                    }
                } else {
                    ownerCell.innerHTML = '<span class="text-muted">No owner</span>';
                    if (attributeRelationships[rowIndex]) {
                        attributeRelationships[rowIndex].attributeOwnerName = null;
                    }
                }
            } catch (error) {
                console.error('Error fetching attribute owner:', error);
                ownerCell.innerHTML = '<span class="text-muted">No owner</span>';
                if (attributeRelationships[rowIndex]) {
                    attributeRelationships[rowIndex].attributeOwnerName = null;
                }
            }
        } else {
            ownerCell.innerHTML = '<span class="text-muted">No owner</span>';
            if (attributeRelationships[rowIndex]) {
                attributeRelationships[rowIndex].attributeOwnerName = null;
            }
        }
    }

    // Save attribute relationships
    async function saveAttributeRelationships() {
        try {
            saveCurrentAttributeFormData();
            
            const relationships = attributeRelationships
                .filter(r => r.relationType && r.attributeId)
                .map(r => ({
                    attributeId: parseInt(r.attributeId),
                    relationType: parseInt(r.relationType)
                }));
            
            const requestData = {
                processId: currentProcessId,
                relationships: relationships
            };
            
            const response = await fetch('/api/process-impact/attributes/save', {
                method: 'POST',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(requestData)
            });
            
            if (!response.ok) {
                console.error('Failed to save attribute relationships:', response.status);
                return {
                    success: false,
                    message: `Failed to save attribute relationships (HTTP ${response.status})`
                };
            }

            const result = await response.json();
            console.log('Attribute relationships saved successfully:', result);
            const success = result && result.success === true;
            
            if (!success) {
                return {
                    success: false,
                    message: result.message || 'Failed to save attribute relationships'
                };
            }
            return true;
        } catch (error) {
            console.error('Error saving attribute relationships:', error);
            return {
                success: false,
                message: 'Error saving attribute relationships: ' + (error.message || 'Unknown error')
            };
        }
    }

    // ===== SAVE ALL IMPACT DATA =====
    
    async function saveAllImpactData() {
        const results = {
            system: await saveSystemRelationships(),
            product: await saveProductRelationships(),
            client: await saveClientRelationships(),
            glossary: await saveGlossaryRelationships(),
            project: await saveProjectRelationships(),
            policy: await savePolicyRelationships(),
            interface: await saveInterfaceRelationships(),
            legal: await saveLegalRelationships(),
            dataset: await saveDatasetRelationships(),
            attribute: await saveAttributeRelationships()
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
        const hasSystemChanges = JSON.stringify(systemRelationships) !== JSON.stringify(originalSystemData);
        const hasProductChanges = JSON.stringify(productRelationships) !== JSON.stringify(originalProductData);
        const hasClientChanges = JSON.stringify(clientRelationships) !== JSON.stringify(originalClientData);
        const hasGlossaryChanges = JSON.stringify(glossaryRelationships) !== JSON.stringify(originalGlossaryData);
        const hasProjectChanges = JSON.stringify(projectRelationships) !== JSON.stringify(originalProjectData);
        const hasPolicyChanges = JSON.stringify(policyRelationships) !== JSON.stringify(originalPolicyData);
        const hasInterfaceChanges = JSON.stringify(interfaceRelationships) !== JSON.stringify(originalInterfaceData);
        const hasLegalChanges = JSON.stringify(legalRelationships) !== JSON.stringify(originalLegalData);
        const hasDatasetChanges = JSON.stringify(datasetRelationships) !== JSON.stringify(originalDatasetData);
        const hasAttributeChanges = JSON.stringify(attributeRelationships) !== JSON.stringify(originalAttributeData);
        
        const hasChanges = hasSystemChanges || hasProductChanges || hasClientChanges || 
                          hasGlossaryChanges || hasProjectChanges || hasPolicyChanges ||
                          hasInterfaceChanges || hasLegalChanges || hasDatasetChanges || hasAttributeChanges;
        
        if (hasChanges) {
            console.log('Process Impact Edit: hasImpactChanges detected changes:');
            if (hasSystemChanges) console.log('  - System relationships changed');
            if (hasProductChanges) console.log('  - Product relationships changed');
            if (hasClientChanges) console.log('  - Client relationships changed');
            if (hasGlossaryChanges) console.log('  - Glossary relationships changed');
            if (hasProjectChanges) console.log('  - Project relationships changed');
            if (hasPolicyChanges) console.log('  - Policy relationships changed');
            if (hasInterfaceChanges) console.log('  - Interface relationships changed');
            if (hasLegalChanges) console.log('  - Legal relationships changed');
            if (hasDatasetChanges) console.log('  - Dataset relationships changed');
            if (hasAttributeChanges) console.log('  - Attribute relationships changed');
        }
        
        return hasChanges;
    }

    // ===== GLOBAL EXPORTS =====
    
    window.initImpactEdit = initImpactEdit;
    window.saveAllImpactData = saveAllImpactData;
    window.hasImpactChanges = hasImpactChanges;
    
    window.addSystemRow = addSystemRow;
    window.deleteSystemRow = deleteSystemRow;
    window.updateSystemOwner = updateSystemOwner;
    
    window.addProductRow = addProductRow;
    window.deleteProductRow = deleteProductRow;
    window.updateProductOwner = updateProductOwner;
    
    window.addClientRow = addClientRow;
    window.deleteClientRow = deleteClientRow;
    window.updateClientOwner = updateClientOwner;
    
    window.addGlossaryRow = addGlossaryRow;
    window.deleteGlossaryRow = deleteGlossaryRow;
    window.updateGlossaryOwner = updateGlossaryOwner;
    
    window.addProjectRow = addProjectRow;
    window.deleteProjectRow = deleteProjectRow;
    window.updateProjectOwner = updateProjectOwner;
    
    window.addPolicyRow = addPolicyRow;
    window.deletePolicyRow = deletePolicyRow;
    window.updatePolicyOwner = updatePolicyOwner;
    
    window.addInterfaceRow = addInterfaceRow;
    window.deleteInterfaceRow = deleteInterfaceRow;
    
    window.addLegalRow = addLegalRow;
    window.deleteLegalRow = deleteLegalRow;
    window.updateLegalOwner = updateLegalOwner;
    
    window.addDatasetRow = addDatasetRow;
    window.deleteDatasetRow = deleteDatasetRow;
    window.updateDatasetOwner = updateDatasetOwner;
    window.filterDatasetsBySystem = filterDatasetsBySystem;
    
    window.addAttributeRow = addAttributeRow;
    window.deleteAttributeRow = deleteAttributeRow;
    window.updateAttributeOwner = updateAttributeOwner;
    window.filterDatasetsBySystemForAttribute = filterDatasetsBySystemForAttribute;
    window.filterAttributesByDataset = filterAttributesByDataset;
    
})();

