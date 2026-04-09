// Regulation Impact Edit JavaScript - Implementation for 4 sub-tabs
(function() {

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
            '#regulationSegment',
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
        const oid = parseInt(currentRegulationId, 10);
        if (Number.isInteger(oid) && oid > 0) {
            const sep2 = result.includes('?') ? '&' : '?';
            result = `${result}${sep2}sourceObjectId=${oid}&sourceObjectType=Regulation`;
        }
        return result;
    }

    const impactSegmentValidationCache = new Map();

    async function isImpactRelationshipAllowed(targetObjectId, targetObjectType) {
        const targetId = parseInt(targetObjectId, 10);
        if (!Number.isInteger(targetId) || targetId <= 0 || !currentRegulationId) {
            return true;
        }

        const cacheKey = `Regulation:${currentRegulationId}->${targetObjectType}:${targetId}`;
        if (impactSegmentValidationCache.has(cacheKey)) {
            return impactSegmentValidationCache.get(cacheKey);
        }

        try {
            const response = await fetch('/api/segments/validate-relationship', {
                method: 'POST',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    sourceObjectId: parseInt(currentRegulationId, 10),
                    sourceObjectType: 'Regulation',
                    targetObjectId: targetId,
                    targetObjectType
                })
            });

            let result = null;
            try { result = await response.json(); } catch (_) { result = null; }
            const isValid = !!(response.ok && result && result.isValid !== false);
            impactSegmentValidationCache.set(cacheKey, isValid);
            return isValid;
        } catch (error) {
            console.warn('Segment relationship validation failed; blocking relationship by fallback:', error);
            impactSegmentValidationCache.set(cacheKey, false);
            return false;
        }
    }

    async function filterRelationshipsForSegment(relationships, targetIdSelector, targetObjectType) {
        const input = Array.isArray(relationships) ? relationships : [];
        const validRelationships = [];
        let skippedCount = 0;

        for (const relationship of input) {
            const targetRaw = typeof targetIdSelector === 'function'
                ? targetIdSelector(relationship)
                : relationship?.[targetIdSelector];
            const targetId = parseInt(targetRaw, 10);
            if (!Number.isInteger(targetId) || targetId <= 0) {
                continue;
            }
            const isAllowed = await isImpactRelationshipAllowed(targetId, targetObjectType);
            if (isAllowed) {
                validRelationships.push(relationship);
            } else {
                skippedCount++;
            }
        }

        return { validRelationships, skippedCount };
    }
    
    // Global variables
    let currentRegulationId = null;
    let productRelationships = [];
    let policyRelationships = [];
    let projectRelationships = [];
    let regulatoryThemeRelationships = [];
    
    let originalProductData = [];
    let originalPolicyData = [];
    let originalProjectData = [];
    let originalRegulatoryThemeData = [];
    
    let productRelationTypes = [];
    let policyRelationTypes = [];
    let projectRelationTypes = [];
    let regulatoryThemeRelationTypes = [];
    
    let products = [];
    let policies = [];
    let projects = [];
    let regulatoryThemes = [];

    // Initialize Impact tab edit functionality
    function initImpactEdit(regulationId) {
        currentRegulationId = regulationId;
        loadImpactData();
    }

    // Load all Impact data
    async function loadImpactData() {
        try {
            
            // Load dropdown data first
            await Promise.all([
                loadProductRelationTypes(),
                loadPolicyRelationTypes(),
                loadProjectRelationTypes(),
                loadRegulatoryThemeRelationTypes(),
                loadProducts(),
                loadPolicies(),
                loadProjects(),
                loadRegulatoryThemes()
            ]);

            // Load relationship data
            await Promise.all([
                loadProductRelationships(),
                loadPolicyRelationships(),
                loadProjectRelationships(),
                loadRegulatoryThemeRelationships()
            ]);

            // Setup sub-tabs
            setupImpactSubTabs();

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
                switch(subTabName) {
                    case 'product':
                        targetSubTab = document.getElementById('impactProductContent');
                        break;
                    case 'policy':
                        targetSubTab = document.getElementById('impactPolicyContent');
                        break;
                    case 'project':
                        targetSubTab = document.getElementById('impactProjectContent');
                        break;
                    case 'regulatorytheme':
                        targetSubTab = document.getElementById('impactRegulatoryThemeContent');
                        break;
                }
                
                if (targetSubTab) {
                    targetSubTab.classList.add('active');
                    targetSubTab.style.display = 'block';
                }
            });
        });
    }

    // ===== PRODUCT FUNCTIONS =====
    
    async function loadProductRelationships() {
        try {
            const response = await fetch(`/api/regulation-impact/${currentRegulationId}/products`, {
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
            const response = await fetch('/api/regulation-impact/product-relation-types', {
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
            products = (await filterRelationshipsForSegment(products, (p) => p?.id ?? p?.ID, 'Product')).validRelationships;
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
                productRefNumber: '',
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
                                `<option value="${rt.id}" ${relationship.relationType == rt.id ? 'selected' : ''}>${rt.primaryname || rt.primaryName}</option>`
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
                    <td style="text-align: center;">
                        <span class="text-muted">${relationship.productRefNumber || ''}</span>
                    </td>
                    <td style="text-align: center;">
                        <span class="text-muted">${relationship.productOwnerName || 'No owner'}</span>
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
        // Save current form data before adding new row to preserve existing data
        saveCurrentProductFormData();
        
        productRelationships.push({
            id: 'new-' + Date.now(),
            productId: null,
            relationType: null,
            productName: '',
            productRefNumber: '',
            productOwnerName: ''
        });
        renderProductTable();
    }
    
    function deleteProductRow(rowId) {
        // Save current form data before deleting row
        saveCurrentProductFormData();
        
        productRelationships = productRelationships.filter(r => (r.id || '').toString() !== rowId.toString());
        if (productRelationships.length === 0) {
            productRelationships.push({
                id: 'new-empty',
                productId: null,
                relationType: null,
                productName: '',
                productRefNumber: '',
                productOwnerName: ''
            });
        }
        renderProductTable();
    }
    
    async function updateProductOwner(selectElement) {
        const productId = selectElement.value;
        const row = selectElement.closest('tr');
        const ownerCell = row.querySelector('td:nth-child(4)');
        const refCell = row.querySelector('td:nth-child(3)');
        
        if (!productId) {
            if (ownerCell) {
                ownerCell.innerHTML = '<span class="text-muted">No owner</span>';
                ownerCell.style.textAlign = 'center';
            }
            if (refCell) {
                refCell.innerHTML = '<span class="text-muted"></span>';
                refCell.style.textAlign = 'center';
            }
            return;
        }
        
        const product = products.find(p => p.id == productId);
        if (product && refCell) {
            refCell.innerHTML = `<span class="text-muted">${product.refnumber || product.refNumber || ''}</span>`;
            refCell.style.textAlign = 'center';
        }
        
        try {
            const response = await fetch(`/api/regulation-impact/product-owner/${productId}`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });
            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
            const data = await response.json();
            if (ownerCell) {
                ownerCell.innerHTML = `<span class="text-muted">${data.ownerName || 'No owner'}</span>`;
                ownerCell.style.textAlign = 'center';
            }
        } catch (error) {
            console.error('Error loading product owner:', error);
            if (ownerCell) {
                ownerCell.innerHTML = '<span class="text-muted">No owner</span>';
                ownerCell.style.textAlign = 'center';
            }
        }
    }
    
    function saveCurrentProductFormData() {
        const rows = document.querySelectorAll('#productImpactTableBody tr');
        rows.forEach((row, index) => {
            if (productRelationships[index]) {
                const relationTypeSelect = row.querySelector('select[data-field="relationType"]');
                const productSelect = row.querySelector('select[data-field="productId"]');
                const ownerCell = row.querySelector('td:nth-child(4)');
                const refCell = row.querySelector('td:nth-child(3)');
                
                if (relationTypeSelect) {
                    productRelationships[index].relationType = relationTypeSelect.value ? parseInt(relationTypeSelect.value) : null;
                }
                if (productSelect) {
                    productRelationships[index].productId = productSelect.value ? parseInt(productSelect.value) : null;
                    // Update product name based on selected product
                    const selectedOption = productSelect.options[productSelect.selectedIndex];
                    if (selectedOption) {
                        productRelationships[index].productName = selectedOption.text;
                    }
                }
                // Preserve the current owner name from the DOM
                if (ownerCell) {
                    const ownerSpan = ownerCell.querySelector('span');
                    if (ownerSpan) {
                        productRelationships[index].productOwnerName = ownerSpan.textContent.trim();
                    }
                }
                // Preserve the current reference number from the DOM
                if (refCell) {
                    const refSpan = refCell.querySelector('span');
                    if (refSpan) {
                        productRelationships[index].productRefNumber = refSpan.textContent.trim();
                    }
                }
            }
        });
    }
    
    async function saveProductRelationships() {
        try {
            saveCurrentProductFormData();
            const relationships = productRelationships.map(r => ({
                productId: r.productId,
                relationType: r.relationType
            }));
            const filtered = await filterRelationshipsForSegment(relationships, 'productId', 'Product');
            if (relationships.length > 0 && filtered.validRelationships.length === 0 && filtered.skippedCount > 0) {
                return { success: false, message: 'No product relationships were saved: selected objects belong to another private segment.' };
            }
            
            const response = await fetch('/api/regulation-impact/products/save', {
                method: 'POST',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    regulationId: currentRegulationId,
                    relationships: filtered.validRelationships
                })
            });
            
            if (!response.ok) {
                return { success: false, message: `Failed to save product relationships (HTTP ${response.status})` };
            }
            
            const result = await response.json();
            const success = result && result.success === true;
            if (!success) {
                return { success: false, message: result.message || 'Failed to save product relationships' };
            }
            return true;
        } catch (error) {
            console.error('Error saving product relationships:', error);
            return { success: false, message: 'Error saving product relationships: ' + (error.message || 'Unknown error') };
        }
    }

    // ===== POLICY FUNCTIONS =====
    
    async function loadPolicyRelationships() {
        try {
            const response = await fetch(`/api/regulation-impact/${currentRegulationId}/policies`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });
            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
            const data = await response.json();
            policyRelationships = Array.isArray(data) ? data : [];
            originalPolicyData = JSON.parse(JSON.stringify(policyRelationships));
            renderPolicyTable();
        } catch (error) {
            console.error('Error loading policy relationships:', error);
            policyRelationships = [];
            originalPolicyData = [];
        }
    }
    
    async function loadPolicyRelationTypes() {
        try {
            const response = await fetch('/api/regulation-impact/policy-relation-types', {
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
            policies = Array.isArray(data) ? data : [];
            policies = (await filterRelationshipsForSegment(policies, (p) => p?.id ?? p?.ID, 'Policy')).validRelationships;
        } catch (error) {
            console.error('Error loading policies:', error);
            policies = [];
        }
    }
    
    function renderPolicyTable() {
        const tbody = document.getElementById('policyImpactTableBody');
        const footer = document.getElementById('policyImpactFooter');
        if (!tbody) return;
        
        if (policyRelationships.length === 0) {
            policyRelationships.push({
                id: 'new-empty',
                policyId: null,
                relationType: null,
                policyName: '',
                policyRefNumber: '',
                policyOwnerName: ''
            });
        }
        
        let html = '';
        policyRelationships.forEach((relationship, index) => {
            const rowId = relationship.id || 'new-' + index;
            html += `
                <tr data-id="${rowId}">
                    <td>
                        <select class="form-control" data-field="relationType">
                            <option value="">Select relationship type</option>
                            ${policyRelationTypes.map(rt => 
                                `<option value="${rt.id}" ${relationship.relationType == rt.id ? 'selected' : ''}>${rt.primaryname || rt.primaryName}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td>
                        <select class="form-control" data-field="policyId" onchange="updatePolicyOwner(this)">
                            <option value="">Select policy</option>
                            ${policies.map(p => 
                                `<option value="${p.id || p.ID}" ${relationship.policyId == (p.id || p.ID) ? 'selected' : ''}>${p.primaryname || p.PrimaryName || p.primaryName || 'Unnamed Policy'}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td style="text-align: center;">
                        <span class="text-muted">${relationship.policyRefNumber || ''}</span>
                    </td>
                    <td style="text-align: center;">
                        <span class="text-muted">${relationship.policyOwnerName || 'No owner'}</span>
                    </td>
                    <td>
                        <div class="action-buttons">
                            <button type="button" class="btn btn-sm" onclick="addPolicyRow()" title="${window.I18n?.t('impactEdit.buttons.addRow') || 'Add row'}">
                                <i class="fas fa-plus"></i>
                            </button>
                            <button type="button" class="btn btn-sm" onclick="deletePolicyRow('${rowId}')" title="${window.I18n?.t('impactEdit.buttons.deleteRow') || 'Delete row'}">
                                <i class="fas fa-minus"></i>
                            </button>
                        </div>
                    </td>
                </tr>
            `;
        });
        
        tbody.innerHTML = html;
        if (footer) footer.textContent = `${policyRelationships.length} record${policyRelationships.length !== 1 ? 's' : ''}`;
    }
    
    function addPolicyRow() {
        // Save current form data before adding new row to preserve existing data
        saveCurrentPolicyFormData();
        
        policyRelationships.push({
            id: 'new-' + Date.now(),
            policyId: null,
            relationType: null,
            policyName: '',
            policyRefNumber: '',
            policyOwnerName: ''
        });
        renderPolicyTable();
    }
    
    function deletePolicyRow(rowId) {
        // Save current form data before deleting row
        saveCurrentPolicyFormData();
        
        policyRelationships = policyRelationships.filter(r => (r.id || '').toString() !== rowId.toString());
        if (policyRelationships.length === 0) {
            policyRelationships.push({
                id: 'new-empty',
                policyId: null,
                relationType: null,
                policyName: '',
                policyRefNumber: '',
                policyOwnerName: ''
            });
        }
        renderPolicyTable();
    }
    
    async function updatePolicyOwner(selectElement) {
        const policyId = selectElement.value;
        const row = selectElement.closest('tr');
        const ownerCell = row.querySelector('td:nth-child(4)');
        const refCell = row.querySelector('td:nth-child(3)');
        
        if (!policyId) {
            if (ownerCell) {
                ownerCell.innerHTML = '<span class="text-muted">No owner</span>';
                ownerCell.style.textAlign = 'center';
            }
            if (refCell) {
                refCell.innerHTML = '<span class="text-muted"></span>';
                refCell.style.textAlign = 'center';
            }
            return;
        }
        
        const policy = policies.find(p => (p.id || p.ID) == policyId);
        if (policy && refCell) {
            refCell.innerHTML = `<span class="text-muted">${policy.refnumber || policy.RefNumber || policy.refNumber || ''}</span>`;
            refCell.style.textAlign = 'center';
        }
        
        try {
            const response = await fetch(`/api/regulation-impact/policy-owner/${policyId}`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });
            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
            const data = await response.json();
            if (ownerCell) {
                ownerCell.innerHTML = `<span class="text-muted">${data.ownerName || 'No owner'}</span>`;
                ownerCell.style.textAlign = 'center';
            }
        } catch (error) {
            console.error('Error loading policy owner:', error);
            if (ownerCell) {
                ownerCell.innerHTML = '<span class="text-muted">No owner</span>';
                ownerCell.style.textAlign = 'center';
            }
        }
    }
    
    function saveCurrentPolicyFormData() {
        const rows = document.querySelectorAll('#policyImpactTableBody tr');
        rows.forEach((row, index) => {
            if (policyRelationships[index]) {
                const relationTypeSelect = row.querySelector('select[data-field="relationType"]');
                const policySelect = row.querySelector('select[data-field="policyId"]');
                const ownerCell = row.querySelector('td:nth-child(4)');
                const refCell = row.querySelector('td:nth-child(3)');
                
                if (relationTypeSelect) {
                    policyRelationships[index].relationType = relationTypeSelect.value ? parseInt(relationTypeSelect.value) : null;
                }
                if (policySelect) {
                    policyRelationships[index].policyId = policySelect.value ? parseInt(policySelect.value) : null;
                    // Update policy name based on selected policy
                    const selectedOption = policySelect.options[policySelect.selectedIndex];
                    if (selectedOption) {
                        policyRelationships[index].policyName = selectedOption.text;
                    }
                }
                // Preserve the current owner name from the DOM
                if (ownerCell) {
                    const ownerSpan = ownerCell.querySelector('span');
                    if (ownerSpan) {
                        policyRelationships[index].policyOwnerName = ownerSpan.textContent.trim();
                    }
                }
                // Preserve the current reference number from the DOM
                if (refCell) {
                    const refSpan = refCell.querySelector('span');
                    if (refSpan) {
                        policyRelationships[index].policyRefNumber = refSpan.textContent.trim();
                    }
                }
            }
        });
    }
    
    async function savePolicyRelationships() {
        try {
            saveCurrentPolicyFormData();
            const relationships = policyRelationships.map(r => ({
                policyId: r.policyId,
                relationType: r.relationType
            }));
            const filtered = await filterRelationshipsForSegment(relationships, 'policyId', 'Policy');
            if (relationships.length > 0 && filtered.validRelationships.length === 0 && filtered.skippedCount > 0) {
                return { success: false, message: 'No policy relationships were saved: selected objects belong to another private segment.' };
            }
            
            const response = await fetch('/api/regulation-impact/policies/save', {
                method: 'POST',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    regulationId: currentRegulationId,
                    relationships: filtered.validRelationships
                })
            });
            
            if (!response.ok) {
                return { success: false, message: `Failed to save policy relationships (HTTP ${response.status})` };
            }
            
            const result = await response.json();
            const success = result && result.success === true;
            if (!success) {
                return { success: false, message: result.message || 'Failed to save policy relationships' };
            }
            return true;
        } catch (error) {
            console.error('Error saving policy relationships:', error);
            return { success: false, message: 'Error saving policy relationships: ' + (error.message || 'Unknown error') };
        }
    }

    // ===== PROJECT FUNCTIONS =====
    
    async function loadProjectRelationships() {
        try {
            const response = await fetch(`/api/regulation-impact/${currentRegulationId}/projects`, {
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
        }
    }
    
    async function loadProjectRelationTypes() {
        try {
            const response = await fetch('/api/regulation-impact/project-relation-types', {
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
            const rawData = await response.json();
            if (rawData.success && rawData.data) {
                projects = rawData.data;
            } else if (Array.isArray(rawData)) {
                projects = rawData;
            } else {
                projects = [];
            }
            projects = (await filterRelationshipsForSegment(projects, (p) => p?.id ?? p?.ID, 'Project')).validRelationships;
        } catch (error) {
            console.error('Error loading projects:', error);
            projects = [];
        }
    }
    
    function renderProjectTable() {
        const tbody = document.getElementById('projectImpactTableBody');
        const footer = document.getElementById('projectImpactFooter');
        if (!tbody) return;
        
        if (projectRelationships.length === 0) {
            projectRelationships.push({
                id: 'new-empty',
                projectId: null,
                relationType: null,
                projectName: '',
                projectRefNumber: '',
                projectOwnerName: ''
            });
        }
        
        let html = '';
        projectRelationships.forEach((relationship, index) => {
            const rowId = relationship.id || 'new-' + index;
            html += `
                <tr data-id="${rowId}">
                    <td>
                        <select class="form-control" data-field="relationType">
                            <option value="">Select relationship type</option>
                            ${projectRelationTypes.map(rt => 
                                `<option value="${rt.id}" ${relationship.relationType == rt.id ? 'selected' : ''}>${rt.primaryname || rt.primaryName}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td>
                        <select class="form-control" data-field="projectId" onchange="updateProjectOwner(this)">
                            <option value="">Select project</option>
                            ${projects.map(p => 
                                `<option value="${p.id}" ${relationship.projectId == p.id ? 'selected' : ''}>${p.primaryname || 'Unnamed Project'}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td style="text-align: center;">
                        <span class="text-muted">${relationship.projectRefNumber || ''}</span>
                    </td>
                    <td style="text-align: center;">
                        <span class="text-muted">${relationship.projectOwnerName || 'No owner'}</span>
                    </td>
                    <td>
                        <div class="action-buttons">
                            <button type="button" class="btn btn-sm" onclick="addProjectRow()" title="${window.I18n?.t('impactEdit.buttons.addRow') || 'Add row'}">
                                <i class="fas fa-plus"></i>
                            </button>
                            <button type="button" class="btn btn-sm" onclick="deleteProjectRow('${rowId}')" title="${window.I18n?.t('impactEdit.buttons.deleteRow') || 'Delete row'}">
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
        // Save current form data before adding new row to preserve existing data
        saveCurrentProjectFormData();
        
        projectRelationships.push({
            id: 'new-' + Date.now(),
            projectId: null,
            relationType: null,
            projectName: '',
            projectRefNumber: '',
            projectOwnerName: ''
        });
        renderProjectTable();
    }
    
    function deleteProjectRow(rowId) {
        // Save current form data before deleting row
        saveCurrentProjectFormData();
        
        projectRelationships = projectRelationships.filter(r => (r.id || '').toString() !== rowId.toString());
        if (projectRelationships.length === 0) {
            projectRelationships.push({
                id: 'new-empty',
                projectId: null,
                relationType: null,
                projectName: '',
                projectRefNumber: '',
                projectOwnerName: ''
            });
        }
        renderProjectTable();
    }
    
    async function updateProjectOwner(selectElement) {
        const projectId = selectElement.value;
        const row = selectElement.closest('tr');
        const ownerCell = row.querySelector('td:nth-child(4)');
        const refCell = row.querySelector('td:nth-child(3)');
        
        if (!projectId) {
            if (ownerCell) {
                ownerCell.innerHTML = '<span class="text-muted">No owner</span>';
                ownerCell.style.textAlign = 'center';
            }
            if (refCell) {
                refCell.innerHTML = '<span class="text-muted"></span>';
                refCell.style.textAlign = 'center';
            }
            return;
        }
        
        const project = projects.find(p => p.id == projectId);
        if (project && refCell) {
            refCell.innerHTML = `<span class="text-muted">${project.refnumber || project.refNumber || ''}</span>`;
            refCell.style.textAlign = 'center';
        }
        
        try {
            const response = await fetch(`/api/regulation-impact/project-owner/${projectId}`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });
            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
            const data = await response.json();
            if (ownerCell) {
                ownerCell.innerHTML = `<span class="text-muted">${data.ownerName || 'No owner'}</span>`;
                ownerCell.style.textAlign = 'center';
            }
        } catch (error) {
            console.error('Error loading project owner:', error);
            if (ownerCell) {
                ownerCell.innerHTML = '<span class="text-muted">No owner</span>';
                ownerCell.style.textAlign = 'center';
            }
        }
    }
    
    function saveCurrentProjectFormData() {
        const rows = document.querySelectorAll('#projectImpactTableBody tr');
        rows.forEach((row, index) => {
            if (projectRelationships[index]) {
                const relationTypeSelect = row.querySelector('select[data-field="relationType"]');
                const projectSelect = row.querySelector('select[data-field="projectId"]');
                const ownerCell = row.querySelector('td:nth-child(4)');
                const refCell = row.querySelector('td:nth-child(3)');
                
                if (relationTypeSelect) {
                    projectRelationships[index].relationType = relationTypeSelect.value ? parseInt(relationTypeSelect.value) : null;
                }
                if (projectSelect) {
                    projectRelationships[index].projectId = projectSelect.value ? parseInt(projectSelect.value) : null;
                    // Update project name based on selected project
                    const selectedOption = projectSelect.options[projectSelect.selectedIndex];
                    if (selectedOption) {
                        projectRelationships[index].projectName = selectedOption.text;
                    }
                }
                // Preserve the current owner name from the DOM
                if (ownerCell) {
                    const ownerSpan = ownerCell.querySelector('span');
                    if (ownerSpan) {
                        projectRelationships[index].projectOwnerName = ownerSpan.textContent.trim();
                    }
                }
                // Preserve the current reference number from the DOM
                if (refCell) {
                    const refSpan = refCell.querySelector('span');
                    if (refSpan) {
                        projectRelationships[index].projectRefNumber = refSpan.textContent.trim();
                    }
                }
            }
        });
    }
    
    async function saveProjectRelationships() {
        try {
            saveCurrentProjectFormData();
            const relationships = projectRelationships.map(r => ({
                projectId: r.projectId,
                relationType: r.relationType
            }));
            const filtered = await filterRelationshipsForSegment(relationships, 'projectId', 'Project');
            if (relationships.length > 0 && filtered.validRelationships.length === 0 && filtered.skippedCount > 0) {
                return { success: false, message: 'No project relationships were saved: selected objects belong to another private segment.' };
            }
            
            const response = await fetch('/api/regulation-impact/projects/save', {
                method: 'POST',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    regulationId: currentRegulationId,
                    relationships: filtered.validRelationships
                })
            });
            
            if (!response.ok) {
                return { success: false, message: `Failed to save project relationships (HTTP ${response.status})` };
            }
            
            const result = await response.json();
            const success = result && result.success === true;
            if (!success) {
                return { success: false, message: result.message || 'Failed to save project relationships' };
            }
            return true;
        } catch (error) {
            console.error('Error saving project relationships:', error);
            return { success: false, message: 'Error saving project relationships: ' + (error.message || 'Unknown error') };
        }
    }

    // ===== REGULATORY THEME FUNCTIONS =====
    
    async function loadRegulatoryThemeRelationships() {
        try {
            const response = await fetch(`/api/regulation-impact/${currentRegulationId}/regulatorythemes`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });
            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
            const data = await response.json();
            regulatoryThemeRelationships = Array.isArray(data) ? data : [];
            originalRegulatoryThemeData = JSON.parse(JSON.stringify(regulatoryThemeRelationships));
            renderRegulatoryThemeTable();
        } catch (error) {
            console.error('Error loading regulatory theme relationships:', error);
            regulatoryThemeRelationships = [];
            originalRegulatoryThemeData = [];
        }
    }
    
    async function loadRegulatoryThemeRelationTypes() {
        try {
            const response = await fetch('/api/regulation-impact/regulatorytheme-relation-types', {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });
            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
            const data = await response.json();
            regulatoryThemeRelationTypes = Array.isArray(data) ? data : [];
        } catch (error) {
            console.error('Error loading regulatory theme relation types:', error);
            regulatoryThemeRelationTypes = [];
        }
    }
    
    async function loadRegulatoryThemes() {
        try {
            const response = await fetch(withImpactSegment('/api/regulatory-theme/list'), {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });
            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
            const data = await response.json();
            regulatoryThemes = Array.isArray(data) ? data : [];
            regulatoryThemes = (await filterRelationshipsForSegment(regulatoryThemes, (t) => t?.id ?? t?.ID, 'RegulatoryTheme')).validRelationships;
        } catch (error) {
            console.error('Error loading regulatory themes:', error);
            regulatoryThemes = [];
        }
    }
    
    function renderRegulatoryThemeTable() {
        const tbody = document.getElementById('regulatoryThemeImpactTableBody');
        const footer = document.getElementById('regulatoryThemeImpactFooter');
        if (!tbody) return;
        
        if (regulatoryThemeRelationships.length === 0) {
            regulatoryThemeRelationships.push({
                id: 'new-empty',
                regulatoryThemeId: null,
                relationType: null,
                regulatoryThemeName: ''
            });
        }
        
        let html = '';
        regulatoryThemeRelationships.forEach((relationship, index) => {
            const rowId = relationship.id || 'new-' + index;
            html += `
                <tr data-id="${rowId}">
                    <td>
                        <select class="form-control" data-field="relationType">
                            <option value="">Select relationship type</option>
                            ${regulatoryThemeRelationTypes.map(rt => 
                                `<option value="${rt.id}" ${relationship.relationType == rt.id ? 'selected' : ''}>${rt.primaryname || rt.PrimaryName}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td>
                        <select class="form-control" data-field="regulatoryThemeId">
                            <option value="">Select regulatory theme</option>
                            ${regulatoryThemes.map(rt => 
                                `<option value="${rt.id || rt.ID}" ${relationship.regulatoryThemeId == (rt.id || rt.ID) ? 'selected' : ''}>${rt.primaryname || rt.PrimaryName || rt.primaryName || 'Unnamed Regulatory Theme'}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td>
                        <div class="action-buttons">
                            <button type="button" class="btn btn-sm" onclick="addRegulatoryThemeRow()" title="${window.I18n?.t('impactEdit.buttons.addRow') || 'Add row'}">
                                <i class="fas fa-plus"></i>
                            </button>
                            <button type="button" class="btn btn-sm" onclick="deleteRegulatoryThemeRow('${rowId}')" title="${window.I18n?.t('impactEdit.buttons.deleteRow') || 'Delete row'}">
                                <i class="fas fa-minus"></i>
                            </button>
                        </div>
                    </td>
                </tr>
            `;
        });
        
        tbody.innerHTML = html;
        if (footer) footer.textContent = `${regulatoryThemeRelationships.length} record${regulatoryThemeRelationships.length !== 1 ? 's' : ''}`;
    }
    
    function addRegulatoryThemeRow() {
        // Save current form data before adding new row to preserve existing data
        saveCurrentRegulatoryThemeFormData();
        
        regulatoryThemeRelationships.push({
            id: 'new-' + Date.now(),
            regulatoryThemeId: null,
            relationType: null,
            regulatoryThemeName: ''
        });
        renderRegulatoryThemeTable();
    }
    
    function deleteRegulatoryThemeRow(rowId) {
        // Save current form data before deleting row
        saveCurrentRegulatoryThemeFormData();
        
        regulatoryThemeRelationships = regulatoryThemeRelationships.filter(r => (r.id || '').toString() !== rowId.toString());
        if (regulatoryThemeRelationships.length === 0) {
            regulatoryThemeRelationships.push({
                id: 'new-empty',
                regulatoryThemeId: null,
                relationType: null,
                regulatoryThemeName: ''
            });
        }
        renderRegulatoryThemeTable();
    }
    
    function saveCurrentRegulatoryThemeFormData() {
        const rows = document.querySelectorAll('#regulatoryThemeImpactTableBody tr');
        rows.forEach((row, index) => {
            if (regulatoryThemeRelationships[index]) {
                const relationTypeSelect = row.querySelector('select[data-field="relationType"]');
                const regulatoryThemeSelect = row.querySelector('select[data-field="regulatoryThemeId"]');
                
                if (relationTypeSelect) {
                    regulatoryThemeRelationships[index].relationType = relationTypeSelect.value ? parseInt(relationTypeSelect.value) : null;
                }
                if (regulatoryThemeSelect) {
                    regulatoryThemeRelationships[index].regulatoryThemeId = regulatoryThemeSelect.value ? parseInt(regulatoryThemeSelect.value) : null;
                    // Update regulatory theme name based on selected theme
                    const selectedOption = regulatoryThemeSelect.options[regulatoryThemeSelect.selectedIndex];
                    if (selectedOption) {
                        regulatoryThemeRelationships[index].regulatoryThemeName = selectedOption.text;
                    }
                }
            }
        });
    }
    
    async function saveRegulatoryThemeRelationships() {
        try {
            saveCurrentRegulatoryThemeFormData();
            const relationships = regulatoryThemeRelationships.map(r => ({
                regulatoryThemeId: r.regulatoryThemeId,
                relationType: r.relationType
            }));
            const filtered = await filterRelationshipsForSegment(relationships, 'regulatoryThemeId', 'RegulatoryTheme');
            if (relationships.length > 0 && filtered.validRelationships.length === 0 && filtered.skippedCount > 0) {
                return { success: false, message: 'No regulatory theme relationships were saved: selected objects belong to another private segment.' };
            }
            
            const response = await fetch('/api/regulation-impact/regulatorythemes/save', {
                method: 'POST',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    regulationId: currentRegulationId,
                    relationships: filtered.validRelationships
                })
            });
            
            if (!response.ok) {
                return { success: false, message: `Failed to save regulatory theme relationships (HTTP ${response.status})` };
            }
            
            const result = await response.json();
            const success = result && result.success === true;
            if (!success) {
                return { success: false, message: result.message || 'Failed to save regulatory theme relationships' };
            }
            return true;
        } catch (error) {
            console.error('Error saving regulatory theme relationships:', error);
            return { success: false, message: 'Error saving regulatory theme relationships: ' + (error.message || 'Unknown error') };
        }
    }

    // ===== SAVE ALL AND CHANGE DETECTION =====
    
    async function saveAllImpactData() {
        const results = {
            product: await saveProductRelationships(),
            policy: await savePolicyRelationships(),
            project: await saveProjectRelationships(),
            regulatoryTheme: await saveRegulatoryThemeRelationships()
        };

        const failedTabs = [];
        const errorDetails = [];
        for (const [tab, result] of Object.entries(results)) {
            if (result !== true && (!result || result.success === false)) {
                failedTabs.push(tab);
                if (result && result.message) {
                    errorDetails.push(`${tab}: ${result.message}`);
                }
            }
        }

        if (failedTabs.length > 0) {
            return {
                success: false,
                failedTabs,
                message: `Failed to save: ${failedTabs.join(', ')}`,
                errorDetails
            };
        }

        return { success: true, failedTabs: [], message: 'All impact data saved successfully', errorDetails: [] };
    }
    
    function hasImpactChanges() {
        // Collect current form data before comparing
        saveCurrentProductFormData();
        saveCurrentPolicyFormData();
        saveCurrentProjectFormData();
        saveCurrentRegulatoryThemeFormData();
        
        // Normalize data for comparison (only compare relationType and entity IDs, not display fields)
        const normalizeProduct = (arr) => arr.map(r => ({ relationType: r.relationType, productId: r.productId })).sort((a, b) => (a.productId || 0) - (b.productId || 0));
        const normalizePolicy = (arr) => arr.map(r => ({ relationType: r.relationType, policyId: r.policyId })).sort((a, b) => (a.policyId || 0) - (b.policyId || 0));
        const normalizeProject = (arr) => arr.map(r => ({ relationType: r.relationType, projectId: r.projectId })).sort((a, b) => (a.projectId || 0) - (b.projectId || 0));
        const normalizeRegulatoryTheme = (arr) => arr.map(r => ({ relationType: r.relationType, regulatoryThemeId: r.regulatoryThemeId })).sort((a, b) => (a.regulatoryThemeId || 0) - (b.regulatoryThemeId || 0));
        
        const productChanged = JSON.stringify(normalizeProduct(productRelationships)) !== JSON.stringify(normalizeProduct(originalProductData));
        const policyChanged = JSON.stringify(normalizePolicy(policyRelationships)) !== JSON.stringify(normalizePolicy(originalPolicyData));
        const projectChanged = JSON.stringify(normalizeProject(projectRelationships)) !== JSON.stringify(normalizeProject(originalProjectData));
        const regulatoryThemeChanged = JSON.stringify(normalizeRegulatoryTheme(regulatoryThemeRelationships)) !== JSON.stringify(normalizeRegulatoryTheme(originalRegulatoryThemeData));
        
        return productChanged || policyChanged || projectChanged || regulatoryThemeChanged;
    }

    // Expose functions globally
    window.initImpactEdit = initImpactEdit;
    window.saveAllImpactData = saveAllImpactData;
    window.hasImpactChanges = hasImpactChanges;
    window.addProductRow = addProductRow;
    window.deleteProductRow = deleteProductRow;
    window.updateProductOwner = updateProductOwner;
    window.addPolicyRow = addPolicyRow;
    window.deletePolicyRow = deletePolicyRow;
    window.updatePolicyOwner = updatePolicyOwner;
    window.addProjectRow = addProjectRow;
    window.deleteProjectRow = deleteProjectRow;
    window.updateProjectOwner = updateProjectOwner;
    window.addRegulatoryThemeRow = addRegulatoryThemeRow;
    window.deleteRegulatoryThemeRow = deleteRegulatoryThemeRow;
    
})();

