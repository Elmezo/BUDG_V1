// Project Impact Edit JavaScript - Implementation for all impact sub-tabs

(function() {
    function normalizeSegmentId(value) {
        const parsed = parseInt(value, 10);
        return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
    }

    function resolveImpactSegmentId() {
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

        const domSelectors = ['#projectSegment', '[name="segmentId"]', '[name="segment_id"]'];
        for (const selector of domSelectors) {
            const element = document.querySelector(selector);
            const fromDom = normalizeSegmentId(element?.value);
            if (fromDom) return fromDom;
        }

        return null;
    }

    function resolveObjectSegmentId(row) {
        return normalizeSegmentId(
            row?.segmentId ??
            row?.segment_id ??
            row?.Segment_ID ??
            row?.segmentID ??
            row?.SegmentId
        );
    }

    function isAllowedSegmentPair(sourceSegmentId, targetSegmentId) {
        if (!sourceSegmentId || !targetSegmentId) return true;
        const ENTERPRISE_SEGMENT_ID = 1;
        if (sourceSegmentId === ENTERPRISE_SEGMENT_ID || targetSegmentId === ENTERPRISE_SEGMENT_ID) return true;
        return sourceSegmentId === targetSegmentId;
    }

    function withImpactSegment(url) {
        const segmentId = resolveImpactSegmentId();
        let result = url;
        if (segmentId) {
            window.currentImpactSegmentId = segmentId;
            const sep1 = result.includes('?') ? '&' : '?';
            result = `${result}${sep1}segmentId=${encodeURIComponent(segmentId)}`;
        }
        const oid = parseInt(currentProjectId, 10);
        if (Number.isInteger(oid) && oid > 0) {
            const sep2 = result.includes('?') ? '&' : '?';
            result = `${result}${sep2}sourceObjectId=${oid}&sourceObjectType=Project`;
        }
        return result;
    }

    const impactSegmentValidationCache = new Map();

    async function isImpactRelationshipAllowed(targetObjectId, targetObjectType) {
        const targetId = parseInt(targetObjectId, 10);
        if (!Number.isInteger(targetId) || targetId <= 0 || !currentProjectId) {
            return true;
        }

        const cacheKey = `Project:${currentProjectId}->${targetObjectType}:${targetId}`;
        if (impactSegmentValidationCache.has(cacheKey)) {
            return impactSegmentValidationCache.get(cacheKey);
        }

        try {
            const response = await fetch('/api/segments/validate-relationship', {
                method: 'POST',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    sourceObjectId: parseInt(currentProjectId, 10),
                    sourceObjectType: 'Project',
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
        const sourceSegmentId = resolveImpactSegmentId();

        for (const relationship of input) {
            const targetSegmentId = resolveObjectSegmentId(relationship);
            if (sourceSegmentId && targetSegmentId && !isAllowedSegmentPair(sourceSegmentId, targetSegmentId)) {
                skippedCount++;
                continue;
            }

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
    let currentProjectId = null;
    let systemRelationships = [];
    let processRelationships = [];
    let glossaryRelationships = [];
    let policyRelationships = [];
    let productRelationships = [];
    let clientRelationships = [];
    let capabilityRelationships = [];
    let businessAreaRelationships = [];
    let datasetRelationships = [];
    let attributeRelationships = [];
    let projectRelationships = []; // For project-to-project relationships (if needed)
    let interfaceRelationships = []; // For interface relationships (if needed)
    let legalRelationships = []; // For legal relationships (if needed)
    
    let originalSystemData = [];
    let originalProcessData = [];
    let originalGlossaryData = [];
    let originalPolicyData = [];
    let originalProductData = [];
    let originalClientData = [];
    let originalCapabilityData = [];
    let originalBusinessAreaData = [];
    let originalDatasetData = [];
    let originalAttributeData = [];
    let originalProjectData = [];
    let originalInterfaceData = [];
    let originalLegalData = [];
    
    let systemRelationTypes = [];
    let processRelationTypes = [];
    let glossaryRelationTypes = [];
    let policyRelationTypes = [];
    let productRelationTypes = [];
    let clientRelationTypes = [];
    let clientRelationTypesLoaded = false;
    let capabilityRelationTypes = [];
    let businessAreaRelationTypes = [];
    let datasetRelationTypes = [];
    let attributeRelationTypes = [];
    
    let systems = [];
    let processes = [];
    let glossaries = [];
    let policies = [];
    let policyTypes = []; // For policy type ID to name lookup
    let products = [];
    let clients = [];
    let capabilities = [];
    let businessAreas = [];
    let datasets = [];
    let attributes = [];

    // Initialize Impact tab edit functionality
    function initImpactEdit(projectId) {
        currentProjectId = projectId;
        loadImpactData();
    }

    // Load all Impact data
    async function loadImpactData() {
        try {
            
            // Load dropdown data first
            await Promise.all([
                loadSystemRelationTypes(),
                loadProcessRelationTypes(),
                loadGlossaryRelationTypes(),
                loadPolicyRelationTypes(),
                loadProductRelationTypes(),
                loadClientRelationTypes(),
                loadCapabilityRelationTypes(),
                loadBusinessAreaRelationTypes(),
                loadDatasetRelationTypes(),
                loadAttributeRelationTypes(),
                loadSystems(),
                loadProcesses(),
                loadGlossaries(),
                loadPolicies(),
                loadPolicyTypes(), // Load policy types for lookup
                loadProducts(),
                loadClients(),
                loadCapabilities(),
                loadBusinessAreas(),
                loadDatasets(),
                loadAttributes()
            ]);

            // Load relationship data
            await Promise.all([
                loadSystemRelationships(),
                loadProcessRelationships(),
                loadGlossaryRelationships(),
                loadPolicyRelationships(),
                loadProductRelationships(),
                loadClientRelationships(),
                loadCapabilityRelationships(),
                loadBusinessAreaRelationships(),
                loadDatasetRelationships(),
                loadAttributeRelationships()
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
                if (subTabName === 'system') {
                    targetSubTab = document.getElementById('impactSystemContent');
                } else if (subTabName === 'process') {
                    targetSubTab = document.getElementById('impactProcessContent');
                } else if (subTabName === 'glossary') {
                    targetSubTab = document.getElementById('impactGlossaryContent');
                } else if (subTabName === 'policy') {
                    targetSubTab = document.getElementById('impactPolicyContent');
                } else if (subTabName === 'product') {
                    targetSubTab = document.getElementById('impactProductContent');
                } else if (subTabName === 'client') {
                    targetSubTab = document.getElementById('impactClientContent');
                } else if (subTabName === 'capability') {
                    targetSubTab = document.getElementById('impactCapabilityContent');
                } else if (subTabName === 'businessarea') {
                    targetSubTab = document.getElementById('impactBusinessAreaContent');
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
    
    async function loadSystemRelationships(forceReload = false) {
        try {
            console.log('=== loadSystemRelationships START ===');
            console.log('Current project ID:', currentProjectId);
            console.log('Force reload:', forceReload);
            
            // Only check visibility on initial load, not when force reloading after save
            if (!forceReload) {
                const systemContent = document.getElementById('impactSystemContent');
                if (systemContent && systemContent.style.display === 'none') {
                    // System tab is not visible on initial load, don't load to avoid affecting other tabs
                    console.log('loadSystemRelationships: Tab not visible on initial load, skipping');
                    return;
                }
            }
            
            const response = await fetch(`/api/project-impact/${currentProjectId}/systems`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            console.log('loadSystemRelationships: API response:', data);
            // Normalize relationTypeId to relationType for consistency
            systemRelationships = Array.isArray(data) ? data.map(r => ({
                ...r,
                relationType: r.relationType || r.relationTypeId || null
            })) : [];
            console.log('loadSystemRelationships: Loaded', systemRelationships.length, 'relationships');
            originalSystemData = JSON.parse(JSON.stringify(systemRelationships));
            renderSystemTable();
            console.log('=== loadSystemRelationships SUCCESS ===');

        } catch (error) {
            console.error('=== loadSystemRelationships ERROR ===', error);
            systemRelationships = [];
            originalSystemData = [];
            renderSystemTable();
        }
    }

    async function loadSystemRelationTypes() {
        try {
            const response = await fetch('/api/project-impact/system-relation-types', {
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
            systems = (await filterRelationshipsForSegment(systems, (s) => s?.id ?? s?.ID, 'System')).validRelationships;

        } catch (error) {
            console.error('Error loading systems:', error);
            systems = [];
        }
    }

    function renderSystemTable() {
        const tbody = document.querySelector('#systemImpactTableBody');
        const footer = document.querySelector('#systemImpactFooter');
        
        if (!tbody) return;
        
        // Check if the system sub-tab content is visible
        const systemContent = document.getElementById('impactSystemContent');
        if (systemContent && systemContent.style.display === 'none') {
            // System tab is not visible, don't render to avoid affecting other tabs
            return;
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
                    <td><textarea class="form-control" data-field="description" rows="2" placeholder="Enter description">${relationship.description || ''}</textarea></td>
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
            systemOwnerName: null,
            description: ''
        };
        systemRelationships.push(newRelationship);
        renderSystemTable();
    }

    function deleteSystemRow(id) {
        saveCurrentSystemFormData();
        if (systemRelationships.length <= 1) {
            systemRelationships[0] = { id: 'new-' + Date.now(), relationType: null, systemId: null, systemName: null, systemOwnerName: null, description: '' };
        } else {
            systemRelationships = systemRelationships.filter(rel => String(rel.id) !== String(id));
        }
        renderSystemTable();
    }

    function saveCurrentSystemFormData() {
        // Always read from DOM, regardless of visibility
        const rows = document.querySelectorAll('#systemImpactTableBody tr');
        console.log('saveCurrentSystemFormData: Found', rows.length, 'rows in DOM');
        
        rows.forEach((row, index) => {
            // Ensure the relationship exists in the array
            if (!systemRelationships[index]) {
                systemRelationships[index] = {
                    id: 'new-' + Date.now() + '-' + index,
                    relationType: null,
                    systemId: null,
                    systemName: null,
                    systemOwnerName: null,
                    description: ''
                };
            }
        
            const relationTypeSelect = row.querySelector('select[data-field="relationType"]');
            const systemSelect = row.querySelector('select[data-field="systemId"]');
            const descriptionTextarea = row.querySelector('textarea[data-field="description"]');
            const ownerCell = row.querySelector('td:nth-child(3)'); // System Owner column
            
            console.log(`saveCurrentSystemFormData: Row ${index} - relationType: ${relationTypeSelect?.value}, systemId: ${systemSelect?.value}`);
            
            if (relationTypeSelect) {
                systemRelationships[index].relationType = relationTypeSelect.value ? parseInt(relationTypeSelect.value) : null;
            }
            if (systemSelect) {
                systemRelationships[index].systemId = systemSelect.value ? parseInt(systemSelect.value) : null;
                // Update system name based on selected system
                if (systemSelect.value) {
                    const system = systems.find(s => s.id == systemSelect.value || s.ID == systemSelect.value);
                    if (system) {
                        systemRelationships[index].systemName = system.name || system.Name;
                    }
                }
            }
            if (descriptionTextarea) {
                systemRelationships[index].description = descriptionTextarea.value || '';
            }
            // Preserve the current owner name from the DOM
            if (ownerCell) {
                const ownerSpan = ownerCell.querySelector('span');
                if (ownerSpan) {
                    systemRelationships[index].systemOwnerName = ownerSpan.textContent.trim();
                }
            }
        });
        
        console.log('saveCurrentSystemFormData: Updated systemRelationships:', systemRelationships);
    }

    async function updateSystemOwner(selectElement) {
        const systemId = selectElement.value;
        const row = selectElement.closest('tr');
        const ownerCell = row.querySelector('td:nth-child(3)'); // Owner is still 3rd column (before description)
        
        if (systemId) {
            await updateSystemOwnerDisplay(ownerCell, parseInt(systemId), row);
        } else {
            ownerCell.innerHTML = '<span class="text-muted"></span>';
        }
    }

    async function updateSystemOwnerDisplay(ownerCell, systemId, row) {
        try {
            const response = await fetch(`/api/project-impact/system-owner/${systemId}`, {
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

    async function saveSystemRelationships(projectIdParam) {
        try {
            const projectIdToUse = projectIdParam || currentProjectId;
            console.log('=== saveSystemRelationships START ===');
            console.log('Project ID to use:', projectIdToUse);
            
            if (!projectIdToUse) {
                console.error('saveSystemRelationships: Project ID is required');
                return { success: false, message: 'Project ID is required' };
            }
            
            // Read from DOM
            saveCurrentSystemFormData();
        
        const relationships = [];
            // Read from the array, not from DOM, to ensure we have all data
        systemRelationships.forEach(relationship => {
                // Only save relationships that have both relationType and systemId
            if (relationship.relationType && relationship.systemId) {
                relationships.push({
                        id: relationship.id && !String(relationship.id).startsWith('new-') ? parseInt(relationship.id) : null,
                        relationTypeId: parseInt(relationship.relationType),
                        systemId: parseInt(relationship.systemId),
                    description: relationship.description || ''
                });
            }
        });
            
            console.log('saveSystemRelationships: Prepared relationships:', relationships);
            console.log('saveSystemRelationships: Total systemRelationships:', systemRelationships.length);
            console.log('saveSystemRelationships: Relationships to save:', relationships.length);
        
            const filtered = await filterRelationshipsForSegment(relationships, 'systemId', 'System');
            if (relationships.length > 0 && filtered.validRelationships.length === 0 && filtered.skippedCount > 0) {
                return { success: false, message: 'No system relationships were saved: selected objects belong to another private segment.' };
            }
        const requestData = {
            projectId: projectIdToUse,
                relationships: filtered.validRelationships
        };
        
            console.log('saveSystemRelationships: Sending request:', requestData);
            
            const response = await fetch('/api/project-impact/systems/save', {
                method: 'POST',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(requestData)
            });
            
            if (!response.ok) {
                const errorText = await response.text();
                console.error('saveSystemRelationships: HTTP error:', response.status, errorText);
                throw new Error(`HTTP error! status: ${response.status}, message: ${errorText}`);
            }
            
            const result = await response.json();
            console.log('saveSystemRelationships: API response:', result);
            
            if (result.success) {
                // Update original data to mark as saved
                originalSystemData = JSON.parse(JSON.stringify(systemRelationships));
                console.log('=== saveSystemRelationships SUCCESS ===');
            return true;
            } else {
                console.error('saveSystemRelationships: API returned success=false:', result.message);
                return { success: false, message: result.message || 'Failed to save system relationships' };
            }
        } catch (error) {
            console.error('=== saveSystemRelationships ERROR ===', error);
            return { success: false, message: 'Network error: ' + error.message };
        }
    }

    // ===== PROCESS RELATIONSHIPS =====
    
    async function loadProcessRelationships(forceReload = false) {
        try {
            console.log('=== loadProcessRelationships START ===');
            console.log('Current project ID:', currentProjectId);
            console.log('Force reload:', forceReload);
            
            // Only check visibility on initial load, not when force reloading after save
            if (!forceReload) {
                const processContent = document.getElementById('impactProcessContent');
                if (processContent && processContent.style.display === 'none') {
                    console.log('loadProcessRelationships: Tab not visible on initial load, skipping');
                    return;
                }
            }
            
            const response = await fetch(`/api/project-impact/${currentProjectId}/processes`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            console.log('loadProcessRelationships: API response:', data);
            // Normalize relationTypeId to relationType for consistency
            // Also ensure processRefNumber is preserved
            processRelationships = Array.isArray(data) ? data.map(r => ({
                ...r,
                relationType: r.relationType || r.relationTypeId || null,
                processRefNumber: r.processRefNumber || r.refNumber || r.ref_number || null
            })) : [];
            console.log('loadProcessRelationships: Loaded', processRelationships.length, 'relationships');
            originalProcessData = JSON.parse(JSON.stringify(processRelationships));
            renderProcessTable();
            console.log('=== loadProcessRelationships SUCCESS ===');

        } catch (error) {
            console.error('=== loadProcessRelationships ERROR ===', error);
            processRelationships = [];
            originalProcessData = [];
            renderProcessTable();
        }
    }

    async function loadProcessRelationTypes() {
        try {
            const response = await fetch('/api/project-impact/process-relation-types', {
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
            const response = await fetch(withImpactSegment('/api/process/list'), {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            processes = Array.isArray(data) ? data : [];
            processes = (await filterRelationshipsForSegment(processes, (p) => p?.id ?? p?.ID, 'Process')).validRelationships;

        } catch (error) {
            console.error('Error loading processes:', error);
            processes = [];
        }
    }

    function renderProcessTable() {
        const tbody = document.querySelector('#processImpactTableBody');
        const footer = document.querySelector('#processImpactFooter');
        
        if (!tbody) {
            // Table doesn't exist in DOM, don't render
            return;
        }
        
        // Check if the process sub-tab content is visible
        const processContent = document.getElementById('impactProcessContent');
        if (processContent && processContent.style.display === 'none') {
            // Process tab is not visible, don't render to avoid affecting other tabs
            return;
        }
        
        if (processRelationships.length === 0) {
            processRelationships.push({
                id: 'new-empty',
                relationType: null,
                processId: null,
                processName: null,
                processRefNumber: null,
                processOwnerName: null,
                description: ''
            });
        }
        
        let html = '';
        processRelationships.forEach((relationship, index) => {
            const rowId = relationship.id || 'new-' + index;
            const isNewRow = !relationship.id || String(relationship.id).startsWith('new-');
            const ownerName = relationship.processOwnerName || relationship.ownerName || '';
            const ownerDisplay = isNewRow && !relationship.processId ? '' : (ownerName || 'No owner');
            
            html += `
                <tr data-id="${rowId}">
                    <td>
                        <select class="form-control" data-field="relationType">
                            <option value="">Select relationship type</option>
                            ${processRelationTypes.map(rt => {
                                const rtId = rt.id || rt.ID;
                                const relType = relationship.relationType || relationship.relationTypeId;
                                return `<option value="${rtId}" ${relType == rtId ? 'selected' : ''}>${rt.primaryname || rt.primaryName || rt.PrimaryName || 'Unnamed Type'}</option>`;
                            }).join('')}
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
                    <td><span class="text-muted">${relationship.processRefNumber || ''}</span></td>
                    <td><span class="text-muted">${ownerDisplay}</span></td>
                    <td><textarea class="form-control" data-field="description" rows="2" placeholder="Enter description">${relationship.description || ''}</textarea></td>
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
        
        // Update ref numbers for already selected processes
        // Use setTimeout to ensure DOM is fully rendered
        setTimeout(() => {
            processRelationships.forEach((relationship, index) => {
                if (relationship.processId) {
                    const row = tbody.children[index];
                    if (row) {
                        const processSelect = row.querySelector('select[data-field="processId"]');
                        const refCell = row.querySelector('td:nth-child(3)');
                        if (processSelect && processSelect.value) {
                            // If we already have processRefNumber from API, use it directly
                            if (relationship.processRefNumber && refCell) {
                                refCell.innerHTML = `<span class="text-muted">${relationship.processRefNumber}</span>`;
                            }
                            // Trigger updateProcessOwner to set ref number and owner
                            updateProcessOwner(processSelect);
                        }
                    }
                }
            });
        }, 0);
    }

    function addProcessRow() {
        // Only add row if process tab is visible
        const processContent = document.getElementById('impactProcessContent');
        if (!processContent || processContent.style.display === 'none') {
            console.warn('Cannot add process row: process tab is not visible');
            return;
        }
        
        // Save current form data before adding new row to preserve existing data
        saveCurrentProcessFormData();
        
        const newRelationship = {
            id: 'new-' + Date.now(),
            relationType: null,
            processId: null,
            processName: null,
            processRefNumber: null,
            processOwnerName: null,
            description: ''
        };
        processRelationships.push(newRelationship);
        renderProcessTable();
    }

    function deleteProcessRow(id) {
        saveCurrentProcessFormData();
        if (processRelationships.length <= 1) {
            processRelationships[0] = { id: 'new-' + Date.now(), relationType: null, processId: null, processName: null, processRefNumber: null, processOwnerName: null, description: '' };
        } else {
            processRelationships = processRelationships.filter(rel => String(rel.id) !== String(id));
        }
        renderProcessTable();
    }

    function saveCurrentProcessFormData() {
        const rows = document.querySelectorAll('#processImpactTableBody tr');
        rows.forEach((row, index) => {
            // Ensure the relationship exists in the array
            if (!processRelationships[index]) {
                processRelationships[index] = {
                    id: 'new-' + Date.now() + '-' + index,
                    relationType: null,
                    processId: null,
                    processName: null,
                    processRefNumber: null,
                    processOwnerName: null,
                    description: ''
                };
            }
            
            const relationTypeSelect = row.querySelector('select[data-field="relationType"]');
            const processSelect = row.querySelector('select[data-field="processId"]');
            const descriptionTextarea = row.querySelector('textarea[data-field="description"]');
            const ownerCell = row.querySelector('td:nth-child(4)'); // Process Owner column
            const refCell = row.querySelector('td:nth-child(3)'); // Ref. column
            
            if (relationTypeSelect) {
                processRelationships[index].relationType = relationTypeSelect.value ? parseInt(relationTypeSelect.value) : null;
            }
            if (processSelect) {
                processRelationships[index].processId = processSelect.value ? parseInt(processSelect.value) : null;
                // Update process name and ref based on selected process
                if (processSelect.value) {
                const process = processes.find(p => (p.id || p.ID) == processSelect.value);
                if (process) {
                        processRelationships[index].processName = process.primaryname || process.primaryName || process.name || process.Name;
                        processRelationships[index].processRefNumber = process.refnumber || process.refNumber || process.ref || '';
                    }
                }
            }
            if (descriptionTextarea) {
                processRelationships[index].description = descriptionTextarea.value || '';
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
                }
            }
        });
    }

    async function updateProcessOwner(selectElement) {
        const processId = selectElement.value;
        const row = selectElement.closest('tr');
        const ownerCell = row.querySelector('td:nth-child(4)');
        const refCell = row.querySelector('td:nth-child(3)');
        
        if (processId) {
            // First try to get ref number from processes array (if available)
            const process = processes.find(p => (p.id || p.ID) == processId);
            let processRef = process ? (process.refnumber || process.refNumber || process.ref || '') : '';
            
            // If ref number not found in processes array, fetch it from API
            if (!processRef && refCell) {
                try {
                    const response = await fetch(`/api/process/${processId}`, {
                        method: 'GET',
                        credentials: 'include',
                        headers: { 'Content-Type': 'application/json' }
                    });
                    
                    if (response.ok) {
                        const result = await response.json();
                        const processData = result.data || result;
                        processRef = processData.refnumber || processData.refNumber || processData.ref || '';
                    }
                } catch (error) {
                    console.error('Error fetching process ref number:', error);
                }
            }
            
            if (refCell) {
                if (processRef) {
                refCell.innerHTML = `<span class="text-muted">${processRef}</span>`;
                    
                    // Update the relationship data structure with ref number
                    const rowIndex = Array.from(row.parentElement.children).indexOf(row);
                    if (processRelationships[rowIndex]) {
                        processRelationships[rowIndex].processRefNumber = processRef;
            }
                } else {
                    refCell.innerHTML = `<span class="text-muted"></span>`;
                }
            }
            
            await updateProcessOwnerDisplay(ownerCell, parseInt(processId), row);
        } else {
            if (ownerCell) ownerCell.innerHTML = '<span class="text-muted"></span>';
            if (refCell) refCell.innerHTML = '<span class="text-muted"></span>';
            
            // Clear ref number in relationship data
            const rowIndex = Array.from(row.parentElement.children).indexOf(row);
            if (processRelationships[rowIndex]) {
                processRelationships[rowIndex].processRefNumber = null;
            }
        }
    }

    async function updateProcessOwnerDisplay(ownerCell, processId, row) {
        try {
            const response = await fetch(`/api/project-impact/process-owner/${processId}`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            const ownerName = data.ownerName || data.processOwnerName || 'No owner';
            ownerCell.innerHTML = `<span class="text-muted">${ownerName}</span>`;
            
            if (row) {
                const rowIndex = Array.from(row.parentElement.children).indexOf(row);
                if (processRelationships[rowIndex]) {
                    processRelationships[rowIndex].processOwnerName = data.ownerName || data.processOwnerName || null;
                }
            }
        } catch (error) {
            console.error('Error fetching process owner:', error);
            ownerCell.innerHTML = '<span class="text-muted">No owner</span>';
        }
    }

    async function saveProcessRelationships(projectIdParam) {
        try {
            const projectIdToUse = projectIdParam || currentProjectId;
            if (!projectIdToUse) {
                return { success: false, message: 'Project ID is required' };
            }
            
            // Read from DOM
            saveCurrentProcessFormData();
            
            const relationships = [];
            // Read from the array, not from DOM, to ensure we have all data
            processRelationships.forEach(relationship => {
                // Only save relationships that have both relationType and processId
                if (relationship.relationType && relationship.processId) {
                    relationships.push({
                        id: relationship.id && !String(relationship.id).startsWith('new-') ? parseInt(relationship.id) : null,
                        processId: parseInt(relationship.processId),
                        relationTypeId: parseInt(relationship.relationType),
                        description: relationship.description || ''
                    });
                }
            });

            const requestData = {
                projectId: projectIdToUse,
                relationships: (await (async () => {
                    const filtered = await filterRelationshipsForSegment(relationships, 'processId', 'Process');
                    if (relationships.length > 0 && filtered.validRelationships.length === 0 && filtered.skippedCount > 0) {
                        throw new Error('No process relationships were saved: selected objects belong to another private segment.');
                    }
                    return filtered.validRelationships;
                })())
            };

            const response = await fetch('/api/project-impact/processes/save', {
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
                // Update original data to mark as saved
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

    // ===== CAPABILITY RELATIONSHIPS =====
    
    async function loadCapabilityRelationships(forceReload = false) {
        try {
            console.log('=== loadCapabilityRelationships START ===');
            console.log('Current project ID:', currentProjectId);
            console.log('Force reload:', forceReload);
            
            // Only check visibility on initial load, not when force reloading after save
            if (!forceReload) {
                const capabilityContent = document.getElementById('impactCapabilityContent');
                if (capabilityContent && capabilityContent.style.display === 'none') {
                    console.log('loadCapabilityRelationships: Tab not visible on initial load, skipping');
                    return;
                }
            }
            
            const response = await fetch(`/api/project-impact/${currentProjectId}/capabilities`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            console.log('loadCapabilityRelationships: API response:', data);
            // Normalize relationTypeId to relationType for consistency
            capabilityRelationships = Array.isArray(data) ? data.map(r => ({
                ...r,
                relationType: r.relationType || r.relationTypeId || null
            })) : [];
            console.log('loadCapabilityRelationships: Loaded', capabilityRelationships.length, 'relationships');
            originalCapabilityData = JSON.parse(JSON.stringify(capabilityRelationships));
            renderCapabilityTable();
            console.log('=== loadCapabilityRelationships SUCCESS ===');

        } catch (error) {
            console.error('=== loadCapabilityRelationships ERROR ===', error);
            capabilityRelationships = [];
            originalCapabilityData = [];
            renderCapabilityTable();
        }
    }

    async function loadCapabilityRelationTypes() {
        try {
            const response = await fetch('/api/project-impact/capability-relation-types', {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            capabilityRelationTypes = Array.isArray(data) ? data : [];

        } catch (error) {
            console.error('Error loading capability relation types:', error);
            capabilityRelationTypes = [];
        }
    }

    async function loadCapabilities() {
        try {
            const response = await fetch(withImpactSegment('/api/capabilities/list'), {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            // CapabilityServlet returns {success: true, count: N, data: [...]}
            if (data.success && data.data && Array.isArray(data.data)) {
                capabilities = data.data;
            } else if (Array.isArray(data)) {
                capabilities = data;
            } else {
                capabilities = [];
            }
            capabilities = (await filterRelationshipsForSegment(capabilities, (c) => c?.id ?? c?.ID, 'Capability')).validRelationships;

        } catch (error) {
            console.error('Error loading capabilities:', error);
            capabilities = [];
        }
    }

    function renderCapabilityTable() {
        const tbody = document.querySelector('#capabilityImpactTableBody');
        const footer = document.querySelector('#capabilityImpactFooter');
        
        if (!tbody) return;
        
        // Check if the capability sub-tab content is visible
        const capabilityContent = document.getElementById('impactCapabilityContent');
        if (capabilityContent && capabilityContent.style.display === 'none') {
            // Capability tab is not visible, don't render to avoid affecting other tabs
            return;
        }
        
        if (capabilityRelationships.length === 0) {
            capabilityRelationships.push({
                id: 'new-empty',
                relationType: null,
                capabilityId: null,
                capabilityName: null
            });
        }
        
        let html = '';
        capabilityRelationships.forEach((relationship, index) => {
            const rowId = relationship.id || 'new-' + index;
            
            html += `
                <tr data-id="${rowId}">
                    <td>
                        <select class="form-control" data-field="relationType">
                            <option value="">Select relationship type</option>
                            ${capabilityRelationTypes.map(rt => 
                                `<option value="${rt.id || rt.ID}" ${relationship.relationType == (rt.id || rt.ID) ? 'selected' : ''}>${rt.primaryname || rt.primaryName || rt.PrimaryName || 'Unnamed Type'}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td>
                        <select class="form-control" data-field="capabilityId">
                            <option value="">Select capability</option>
                            ${capabilities.map(c => 
                                `<option value="${c.id || c.ID}" ${relationship.capabilityId == (c.id || c.ID) ? 'selected' : ''}>${c.primaryname || c.primaryName || c.name || c.Name || 'Unnamed Capability'}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td>
                        <div class="action-buttons">
                            <button type="button" class="btn btn-sm btn-success" onclick="addCapabilityRow()" title="${window.I18n?.t('impactEdit.buttons.addRow') || 'Add row'}">
                                <i class="fas fa-plus"></i>
                            </button>
                            <button type="button" class="btn btn-sm btn-danger" onclick="deleteCapabilityRow('${rowId}')" title="${window.I18n?.t('impactEdit.buttons.deleteRow') || 'Delete row'}">
                                <i class="fas fa-minus"></i>
                            </button>
                        </div>
                    </td>
                </tr>
            `;
        });
        
        tbody.innerHTML = html;
        if (footer) footer.textContent = `${capabilityRelationships.length} record${capabilityRelationships.length !== 1 ? 's' : ''}`;
    }

    function addCapabilityRow() {
        saveCurrentCapabilityFormData();
        const newRelationship = {
            id: 'new-' + Date.now(),
            relationType: null,
            capabilityId: null,
            capabilityName: null
        };
        capabilityRelationships.push(newRelationship);
        renderCapabilityTable();
    }

    function deleteCapabilityRow(id) {
        saveCurrentCapabilityFormData();
        if (capabilityRelationships.length <= 1) {
            capabilityRelationships[0] = { id: 'new-' + Date.now(), relationType: null, capabilityId: null, capabilityName: null };
        } else {
            capabilityRelationships = capabilityRelationships.filter(rel => String(rel.id) !== String(id));
        }
        renderCapabilityTable();
    }

    function saveCurrentCapabilityFormData() {
        const rows = document.querySelectorAll('#capabilityImpactTableBody tr');
        rows.forEach((row, index) => {
            // Ensure the relationship exists in the array
            if (!capabilityRelationships[index]) {
                capabilityRelationships[index] = {
                    id: 'new-' + Date.now() + '-' + index,
                    relationType: null,
                    capabilityId: null,
                    capabilityName: null
                };
            }
            
            const relationTypeSelect = row.querySelector('select[data-field="relationType"]');
            const capabilitySelect = row.querySelector('select[data-field="capabilityId"]');
            
            if (relationTypeSelect) {
                capabilityRelationships[index].relationType = relationTypeSelect.value ? parseInt(relationTypeSelect.value) : null;
            }
            if (capabilitySelect) {
                capabilityRelationships[index].capabilityId = capabilitySelect.value ? parseInt(capabilitySelect.value) : null;
                // Update capability name based on selected capability
                const selectedOption = capabilitySelect.options[capabilitySelect.selectedIndex];
                if (selectedOption) {
                    capabilityRelationships[index].capabilityName = selectedOption.text;
                }
            }
        });
    }

    async function saveCapabilityRelationships(projectIdParam) {
        try {
            // Read from DOM
            saveCurrentCapabilityFormData();
            
            const relationships = [];
            // Read from the array, not from DOM, to ensure we have all data
            capabilityRelationships.forEach(relationship => {
                // Only save relationships that have both relationType and capabilityId
                if (relationship.relationType && relationship.capabilityId) {
                    relationships.push({
                        id: relationship.id && !String(relationship.id).startsWith('new-') ? parseInt(relationship.id) : null,
                        capabilityId: parseInt(relationship.capabilityId),
                        relationTypeId: parseInt(relationship.relationType)
                    });
                }
            });

            const projectIdToUse = projectIdParam || currentProjectId;
            if (!projectIdToUse) {
                return { success: false, message: 'Project ID is required' };
            }
            
            const requestData = {
                projectId: projectIdToUse,
                relationships: (await (async () => {
                    const filtered = await filterRelationshipsForSegment(relationships, 'capabilityId', 'Capability');
                    if (relationships.length > 0 && filtered.validRelationships.length === 0 && filtered.skippedCount > 0) {
                        throw new Error('No capability relationships were saved: selected objects belong to another private segment.');
                    }
                    return filtered.validRelationships;
                })())
            };

            const response = await fetch('/api/project-impact/capabilities/save', {
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
                // Update original data to mark as saved
                originalCapabilityData = JSON.parse(JSON.stringify(capabilityRelationships));
            return true;
            } else {
                return { success: false, message: result.message || 'Failed to save capability relationships' };
            }
        } catch (error) {
            console.error('Error saving capability relationships:', error);
            return { success: false, message: 'Network error: ' + error.message };
        }
    }

    // ===== BUSINESS AREA RELATIONSHIPS =====
    
    async function loadBusinessAreaRelationships(forceReload = false) {
        try {
            console.log('=== loadBusinessAreaRelationships START ===');
            console.log('Current project ID:', currentProjectId);
            console.log('Force reload:', forceReload);
            
            // Only check visibility on initial load, not when force reloading after save
            if (!forceReload) {
                const businessAreaContent = document.getElementById('impactBusinessAreaContent');
                if (businessAreaContent && businessAreaContent.style.display === 'none') {
                    console.log('loadBusinessAreaRelationships: Tab not visible on initial load, skipping');
                    return;
                }
            }
            
            const response = await fetch(`/api/project-impact/${currentProjectId}/businessareas`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            console.log('loadBusinessAreaRelationships: API response:', data);
            // Normalize relationTypeId to relationType for consistency
            businessAreaRelationships = Array.isArray(data) ? data.map(r => ({
                ...r,
                relationType: r.relationType || r.relationTypeId || null
            })) : [];
            console.log('loadBusinessAreaRelationships: Loaded', businessAreaRelationships.length, 'relationships');
            originalBusinessAreaData = JSON.parse(JSON.stringify(businessAreaRelationships));
            renderBusinessAreaTable();
            console.log('=== loadBusinessAreaRelationships SUCCESS ===');

        } catch (error) {
            console.error('=== loadBusinessAreaRelationships ERROR ===', error);
            businessAreaRelationships = [];
            originalBusinessAreaData = [];
            renderBusinessAreaTable();
        }
    }

    async function loadBusinessAreaRelationTypes() {
        try {
            const response = await fetch('/api/project-impact/businessarea-relation-types', {
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
            businessAreas = (data.success && data.data) ? data.data : (Array.isArray(data) ? data : []);
            businessAreas = (await filterRelationshipsForSegment(businessAreas, (b) => b?.id ?? b?.ID ?? b?.BusinessArea_ID, 'BusinessArea')).validRelationships;

        } catch (error) {
            console.error('Error loading business areas:', error);
            businessAreas = [];
        }
    }

    function renderBusinessAreaTable() {
        const tbody = document.querySelector('#businessAreaImpactTableBody');
        const footer = document.querySelector('#businessAreaImpactFooter');
        
        if (!tbody) return;
        
        // Check if the business area sub-tab content is visible
        const businessAreaContent = document.getElementById('impactBusinessAreaContent');
        if (businessAreaContent && businessAreaContent.style.display === 'none') {
            // Business area tab is not visible, don't render to avoid affecting other tabs
            return;
        }
        
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
                                `<option value="${ba.id || ba.ID}" ${relationship.businessAreaId == (ba.id || ba.ID) ? 'selected' : ''}>${ba.primaryName || ba.primaryname || ba.name || ba.Name || 'Unnamed Business Area'}</option>`
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
            // Ensure the relationship exists in the array
            if (!businessAreaRelationships[index]) {
                businessAreaRelationships[index] = {
                    id: 'new-' + Date.now() + '-' + index,
                    relationType: null,
                    businessAreaId: null,
                    businessAreaName: null
                };
            }
            
            const relationTypeSelect = row.querySelector('select[data-field="relationType"]');
            const businessAreaSelect = row.querySelector('select[data-field="businessAreaId"]');
            
            if (relationTypeSelect) {
                businessAreaRelationships[index].relationType = relationTypeSelect.value ? parseInt(relationTypeSelect.value) : null;
            }
            if (businessAreaSelect) {
                businessAreaRelationships[index].businessAreaId = businessAreaSelect.value ? parseInt(businessAreaSelect.value) : null;
                // Update business area name based on selected business area
                const selectedOption = businessAreaSelect.options[businessAreaSelect.selectedIndex];
                if (selectedOption) {
                    businessAreaRelationships[index].businessAreaName = selectedOption.text;
                }
            }
        });
    }

    async function saveBusinessAreaRelationships(projectIdParam) {
        try {
            // Read from DOM
            saveCurrentBusinessAreaFormData();
            
            const relationships = [];
            // Read from the array, not from DOM, to ensure we have all data
            businessAreaRelationships.forEach(relationship => {
                // Only save relationships that have both relationType and businessAreaId
                if (relationship.relationType && relationship.businessAreaId) {
                    relationships.push({
                        id: relationship.id && !String(relationship.id).startsWith('new-') ? parseInt(relationship.id) : null,
                        businessAreaId: parseInt(relationship.businessAreaId),
                        relationTypeId: parseInt(relationship.relationType)
                    });
                }
            });

            const projectIdToUse = projectIdParam || currentProjectId;
            if (!projectIdToUse) {
                return { success: false, message: 'Project ID is required' };
            }
            
            const requestData = {
                projectId: projectIdToUse,
                relationships: (await (async () => {
                    const filtered = await filterRelationshipsForSegment(relationships, 'businessAreaId', 'BusinessArea');
                    if (relationships.length > 0 && filtered.validRelationships.length === 0 && filtered.skippedCount > 0) {
                        throw new Error('No business area relationships were saved: selected objects belong to another private segment.');
                    }
                    return filtered.validRelationships;
                })())
            };

            const response = await fetch('/api/project-impact/businessareas/save', {
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
                // Update original data to mark as saved
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

    // ===== PRODUCT RELATIONSHIPS =====
    
    async function loadProductRelationships(forceReload = false) {
        try {
            console.log('=== loadProductRelationships START ===');
            console.log('Current project ID:', currentProjectId);
            console.log('Force reload:', forceReload);
            
            // Only check visibility on initial load, not when force reloading after save
            if (!forceReload) {
                const productContent = document.getElementById('impactProductContent');
                if (productContent && productContent.style.display === 'none') {
                    console.log('loadProductRelationships: Tab not visible on initial load, skipping');
                    return;
                }
            }
            
            const response = await fetch(`/api/project-impact/${currentProjectId}/products`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            console.log('loadProductRelationships: API response:', data);
            // Normalize relationTypeId to relationType for consistency
            productRelationships = Array.isArray(data) ? data.map(r => ({
                ...r,
                relationType: r.relationType || r.relationTypeId || null
            })) : [];
            console.log('loadProductRelationships: Loaded', productRelationships.length, 'relationships');
            originalProductData = JSON.parse(JSON.stringify(productRelationships));
            renderProductTable();
            console.log('=== loadProductRelationships SUCCESS ===');

        } catch (error) {
            console.error('=== loadProductRelationships ERROR ===', error);
            productRelationships = [];
            originalProductData = [];
            renderProductTable();
        }
    }

    async function loadProductRelationTypes() {
        try {
            const response = await fetch('/api/project-impact/product-relation-types', {
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
        const tbody = document.querySelector('#productImpactTableBody');
        const footer = document.querySelector('#productImpactFooter');
        
        if (!tbody) return;
        
        // Check if the product sub-tab content is visible
        const productContent = document.getElementById('impactProductContent');
        if (productContent && productContent.style.display === 'none') {
            // Product tab is not visible, don't render to avoid affecting other tabs
            return;
        }
        
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
                    <td><textarea class="form-control" data-field="description" rows="2" placeholder="Enter description">${relationship.description || ''}</textarea></td>
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
            productOwnerName: null,
            description: ''
        };
        productRelationships.push(newRelationship);
        renderProductTable();
    }

    function deleteProductRow(id) {
        saveCurrentProductFormData();
        if (productRelationships.length <= 1) {
            productRelationships[0] = { id: 'new-' + Date.now(), relationType: null, productId: null, productName: null, productOwnerName: null, description: '' };
        } else {
            productRelationships = productRelationships.filter(rel => String(rel.id) !== String(id));
        }
        renderProductTable();
    }

    function saveCurrentProductFormData() {
        const rows = document.querySelectorAll('#productImpactTableBody tr');
        rows.forEach((row, index) => {
            // Ensure the relationship exists in the array
            if (!productRelationships[index]) {
                productRelationships[index] = {
                    id: 'new-' + Date.now() + '-' + index,
                    relationType: null,
                    productId: null,
                    productName: null,
                    productOwnerName: null,
                    description: ''
                };
            }
            
            const relationTypeSelect = row.querySelector('select[data-field="relationType"]');
            const productSelect = row.querySelector('select[data-field="productId"]');
            const descriptionTextarea = row.querySelector('textarea[data-field="description"]');
            const ownerCell = row.querySelector('td:nth-child(3)'); // Product Owner column
            
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
            if (descriptionTextarea) {
                productRelationships[index].description = descriptionTextarea.value || '';
            }
            // Preserve the current owner name from the DOM
            if (ownerCell) {
                const ownerSpan = ownerCell.querySelector('span');
                if (ownerSpan) {
                    productRelationships[index].productOwnerName = ownerSpan.textContent.trim();
                }
            }
        });
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
            const response = await fetch(`/api/project-impact/product-owner/${productId}`, {
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

    async function saveProductRelationships(projectIdParam) {
        try {
            const projectIdToUse = projectIdParam || currentProjectId;
            if (!projectIdToUse) {
                return { success: false, message: 'Project ID is required' };
            }
            
            // Read from DOM
            saveCurrentProductFormData();
        
        const relationships = [];
            // Read from the array, not from DOM, to ensure we have all data
        productRelationships.forEach(relationship => {
                // Only save relationships that have both relationType and productId
            if (relationship.relationType && relationship.productId) {
                relationships.push({
                        id: relationship.id && !String(relationship.id).startsWith('new-') ? parseInt(relationship.id) : null,
                        productId: parseInt(relationship.productId),
                        relationTypeId: parseInt(relationship.relationType),
                    description: relationship.description || ''
                });
            }
        });
        
            const filtered = await filterRelationshipsForSegment(relationships, 'productId', 'Product');
            if (relationships.length > 0 && filtered.validRelationships.length === 0 && filtered.skippedCount > 0) {
                return { success: false, message: 'No product relationships were saved: selected objects belong to another private segment.' };
            }
        const requestData = {
            projectId: projectIdToUse,
            relationships: filtered.validRelationships
        };
        
            const response = await fetch('/api/project-impact/products/save', {
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
                // Update original data to mark as saved
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
    
    async function loadClientRelationships(forceReload = false) {
        try {
            console.log('=== loadClientRelationships START ===');
            console.log('Current project ID:', currentProjectId);
            console.log('Force reload:', forceReload);
            
            // Only check visibility on initial load, not when force reloading after save
            if (!forceReload) {
                const clientContent = document.getElementById('impactClientContent');
                if (clientContent && clientContent.style.display === 'none') {
                    console.log('loadClientRelationships: Tab not visible on initial load, skipping');
                    return;
                }
            }
            
            const response = await fetch(`/api/project-impact/${currentProjectId}/clients`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            console.log('loadClientRelationships: API response:', data);
            // Normalize relationTypeId to relationType for consistency
            clientRelationships = Array.isArray(data) ? data.map(r => ({
                ...r,
                relationType: r.relationType || r.relationTypeId || null
            })) : [];
            console.log('Client relationships loaded:', clientRelationships.length, 'relationships');
            clientRelationships.forEach((rel, idx) => {
                console.log(`Client relationship ${idx + 1}: id=${rel.id}, clientId=${rel.clientId}, relationType=${rel.relationType}`);
            });
            originalClientData = JSON.parse(JSON.stringify(clientRelationships));
            
            // Ensure relation types are loaded before rendering
            if (clientRelationTypes.length === 0) {
                console.log('Client relation types not loaded, waiting...');
                await loadClientRelationTypes();
            }
            renderClientTable();
            console.log('=== loadClientRelationships SUCCESS ===');

        } catch (error) {
            console.error('=== loadClientRelationships ERROR ===', error);
            clientRelationships = [];
            originalClientData = [];
            renderClientTable();
        }
    }

    async function loadClientRelationTypes() {
        try {
            const response = await fetch('/api/project-impact/client-relation-types', {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            console.log('Client relation types API response:', data);
            clientRelationTypes = Array.isArray(data) ? data : [];
            clientRelationTypesLoaded = true;
            console.log('Client relation types loaded:', clientRelationTypes.length, 'types');
            if (clientRelationTypes.length > 0) {
                clientRelationTypes.forEach((rt, idx) => {
                    console.log(`Client relation type ${idx + 1}: id=${rt.id || rt.ID}, name=${rt.primaryname || rt.primaryName || rt.PrimaryName}`);
                });
            } else {
                console.warn('No client relation types found in database');
            }
            
            // Re-render table if it already exists (in case relation types loaded after relationships)
            const tbody = document.querySelector('#clientImpactTableBody');
            if (tbody && clientRelationships.length > 0) {
                console.log('Re-rendering client table after relation types loaded');
                renderClientTable();
            }

        } catch (error) {
            console.error('Error loading client relation types:', error);
            clientRelationTypes = [];
            clientRelationTypesLoaded = true; // Mark as loaded even on error
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
            clients = (await filterRelationshipsForSegment(clients, (c) => c?.id ?? c?.ID, 'Client')).validRelationships;

        } catch (error) {
            console.error('Error loading clients:', error);
            clients = [];
        }
    }

    function renderClientTable() {
        const tbody = document.querySelector('#clientImpactTableBody');
        const footer = document.querySelector('#clientImpactFooter');
        
        if (!tbody) return;
        
        // Check if the client sub-tab content is visible
        const clientContent = document.getElementById('impactClientContent');
        if (clientContent && clientContent.style.display === 'none') {
            // Client tab is not visible, don't render to avoid affecting other tabs
            return;
        }
        
        // Debug: Check if relation types are loaded
        if (clientRelationTypes.length === 0) {
            console.warn('Client relation types not loaded yet when rendering table');
        } else {
            console.log('Rendering client table with', clientRelationTypes.length, 'relation types available');
        }
        
        if (clientRelationships.length === 0) {
            clientRelationships.push({
                id: 'new-empty',
                relationType: null,
                clientId: null,
                clientName: null,
                clientOwnerName: null,
                description: ''
            });
        }
        
        let html = '';
        clientRelationships.forEach((relationship, index) => {
            const rowId = relationship.id || 'new-' + index;
            const isNewRow = !relationship.id || String(relationship.id).startsWith('new-');
            const ownerName = relationship.clientOwnerName || relationship.ownerName || '';
            const ownerDisplay = isNewRow && !relationship.clientId ? '' : (ownerName || 'No owner');
            
            // Build relation type options
            let relationTypeOptions = '<option value="">Select relationship type</option>';
            if (clientRelationTypesLoaded) {
                // Data has been loaded (even if empty)
                if (clientRelationTypes.length > 0) {
                    relationTypeOptions += clientRelationTypes.map(rt => {
                        const rtId = rt.id || rt.ID;
                        const rtName = rt.primaryname || rt.primaryName || rt.PrimaryName || 'Unnamed Type';
                        const relType = relationship.relationType;
                        const isSelected = relType != null && (relType == rtId || String(relType) === String(rtId));
                        return `<option value="${rtId}" ${isSelected ? 'selected' : ''}>${rtName}</option>`;
                    }).join('');
                }
                // If loaded but empty, just show the default option
            } else {
                // Still loading
                relationTypeOptions += '<option value="">Loading...</option>';
            }
            
            html += `
                <tr data-id="${rowId}">
                    <td>
                        <select class="form-control" data-field="relationType">
                            ${relationTypeOptions}
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
                    <td><textarea class="form-control" data-field="description" rows="2" placeholder="Enter description">${relationship.description || ''}</textarea></td>
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
            clientOwnerName: null,
            description: ''
        };
        clientRelationships.push(newRelationship);
        renderClientTable();
    }

    function deleteClientRow(id) {
        saveCurrentClientFormData();
        if (clientRelationships.length <= 1) {
            clientRelationships[0] = { id: 'new-' + Date.now(), relationType: null, clientId: null, clientName: null, clientOwnerName: null, description: '' };
        } else {
            clientRelationships = clientRelationships.filter(rel => String(rel.id) !== String(id));
        }
        renderClientTable();
    }

    function saveCurrentClientFormData() {
        const rows = document.querySelectorAll('#clientImpactTableBody tr');
        rows.forEach((row, index) => {
            // Ensure the relationship exists in the array
            if (!clientRelationships[index]) {
                clientRelationships[index] = {
                    id: 'new-' + Date.now() + '-' + index,
                    relationType: null,
                    clientId: null,
                    clientName: null,
                    clientOwnerName: null,
                    description: ''
                };
            }
            
            const relationTypeSelect = row.querySelector('select[data-field="relationType"]');
            const clientSelect = row.querySelector('select[data-field="clientId"]');
            const descriptionTextarea = row.querySelector('textarea[data-field="description"]');
            const ownerCell = row.querySelector('td:nth-child(3)'); // Client Owner column
            
            if (relationTypeSelect) {
                clientRelationships[index].relationType = relationTypeSelect.value ? parseInt(relationTypeSelect.value) : null;
            }
            if (clientSelect) {
                clientRelationships[index].clientId = clientSelect.value ? parseInt(clientSelect.value) : null;
                // Update client name based on selected client
                const selectedOption = clientSelect.options[clientSelect.selectedIndex];
                if (selectedOption) {
                    clientRelationships[index].clientName = selectedOption.text;
                }
            }
            if (descriptionTextarea) {
                clientRelationships[index].description = descriptionTextarea.value || '';
            }
            // Preserve the current owner name from the DOM
            if (ownerCell) {
                const ownerSpan = ownerCell.querySelector('span');
                if (ownerSpan) {
                    clientRelationships[index].clientOwnerName = ownerSpan.textContent.trim();
                }
            }
        });
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

    async function saveClientRelationships(projectIdParam) {
        try {
            // Read from DOM
            saveCurrentClientFormData();
            
            const relationships = [];
            // Read from the array, not from DOM, to ensure we have all data
            clientRelationships.forEach(relationship => {
                // Only save relationships that have both relationType and clientId
                if (relationship.relationType && relationship.clientId) {
                    relationships.push({
                        id: relationship.id && !String(relationship.id).startsWith('new-') ? parseInt(relationship.id) : null,
                        clientId: parseInt(relationship.clientId),
                        relationTypeId: parseInt(relationship.relationType),
                        description: relationship.description || ''
                    });
                }
            });

            const projectIdToUse = projectIdParam || currentProjectId;
            if (!projectIdToUse) {
                return { success: false, message: 'Project ID is required' };
            }
            
            const requestData = {
                projectId: projectIdToUse,
                relationships: (await (async () => {
                    const filtered = await filterRelationshipsForSegment(relationships, 'clientId', 'Client');
                    if (relationships.length > 0 && filtered.validRelationships.length === 0 && filtered.skippedCount > 0) {
                        throw new Error('No client relationships were saved: selected objects belong to another private segment.');
                    }
                    return filtered.validRelationships;
                })())
            };
            
            console.log('saveClientRelationships: Prepared relationships:', relationships);
            console.log('saveClientRelationships: Total clientRelationships:', clientRelationships.length);
            console.log('saveClientRelationships: Relationships to save:', relationships.length);
            console.log('saveClientRelationships: Sending request:', requestData);

            const response = await fetch('/api/project-impact/clients/save', {
                method: 'POST',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(requestData)
            });
            
            if (!response.ok) {
                const errorText = await response.text();
                console.error('saveClientRelationships: HTTP error:', response.status, errorText);
                throw new Error(`HTTP error! status: ${response.status}, message: ${errorText}`);
            }
            
            const result = await response.json();
            console.log('saveClientRelationships: API response:', result);
            
            if (result.success) {
                // Update original data to mark as saved
                originalClientData = JSON.parse(JSON.stringify(clientRelationships));
                console.log('=== saveClientRelationships SUCCESS ===');
            return true;
            } else {
                console.error('saveClientRelationships: API returned success=false:', result.message);
                return { success: false, message: result.message || 'Failed to save client relationships' };
            }
        } catch (error) {
            console.error('Error saving client relationships:', error);
            return { success: false, message: 'Network error: ' + error.message };
        }
    }

    async function updateClientOwnerDisplay(ownerCell, clientId, row) {
        try {
            const response = await fetch(`/api/project-impact/client-owner/${clientId}`, {
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

    // ===== GLOSSARY RELATIONSHIPS =====
    
    async function loadGlossaryRelationships(forceReload = false) {
        try {
            console.log('=== loadGlossaryRelationships START ===');
            console.log('Current project ID:', currentProjectId);
            console.log('Force reload:', forceReload);
            
            // Only check visibility on initial load, not when force reloading after save
            if (!forceReload) {
                const glossaryContent = document.getElementById('impactGlossaryContent');
                if (glossaryContent && glossaryContent.style.display === 'none') {
                    console.log('loadGlossaryRelationships: Tab not visible on initial load, skipping');
                    return;
                }
            }
            
            const response = await fetch(`/api/project-impact/${currentProjectId}/glossaries`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            console.log('loadGlossaryRelationships: API response:', data);
            // Normalize relationTypeId to relationType for consistency
            // Also ensure glossaryTypeName is preserved (not just glossaryType number)
            glossaryRelationships = Array.isArray(data) ? data.map(r => ({
                ...r,
                relationType: r.relationType || r.relationTypeId || null,
                glossaryTypeName: r.glossaryTypeName || r.typeName || r.type_name || null
            })) : [];
            console.log('loadGlossaryRelationships: Loaded', glossaryRelationships.length, 'relationships');
            originalGlossaryData = JSON.parse(JSON.stringify(glossaryRelationships));
            renderGlossaryTable();
            console.log('=== loadGlossaryRelationships SUCCESS ===');

        } catch (error) {
            console.error('=== loadGlossaryRelationships ERROR ===', error);
            glossaryRelationships = [];
            originalGlossaryData = [];
            renderGlossaryTable();
        }
    }

    async function loadGlossaryRelationTypes() {
        try {
            const response = await fetch('/api/project-impact/glossary-relation-types', {
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
            glossaries = (await filterRelationshipsForSegment(glossaries, (g) => g?.id ?? g?.ID, 'Glossary')).validRelationships;

        } catch (error) {
            console.error('Error loading glossaries:', error);
            glossaries = [];
        }
    }

    function renderGlossaryTable() {
        const tbody = document.querySelector('#glossaryImpactTableBody');
        const footer = document.querySelector('#glossaryImpactFooter');
        
        if (!tbody) return;
        
        // Check if the glossary sub-tab content is visible
        const glossaryContent = document.getElementById('impactGlossaryContent');
        if (glossaryContent && glossaryContent.style.display === 'none') {
            // Glossary tab is not visible, don't render to avoid affecting other tabs
            return;
        }
        
        if (glossaryRelationships.length === 0) {
            glossaryRelationships.push({
                id: 'new-empty',
                relationType: null,
                glossaryId: null,
                glossaryName: null,
                glossaryType: null,
                glossaryTypeName: null,
                glossaryOwnerName: null,
                description: ''
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
                    <td><span class="text-muted">${relationship.glossaryTypeName || relationship.typeName || relationship.type_name || ''}</span></td>
                    <td><span class="text-muted">${relationship.glossaryOwnerName || 'No owner'}</span></td>
                    <td><textarea class="form-control" data-field="description" rows="2" placeholder="Enter description">${relationship.description || ''}</textarea></td>
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
        
        // Update type names for already selected glossaries
        // Use setTimeout to ensure DOM is fully rendered
        setTimeout(() => {
            glossaryRelationships.forEach((relationship, index) => {
                if (relationship.glossaryId) {
                    const row = tbody.children[index];
                    if (row) {
                        const glossarySelect = row.querySelector('select[data-field="glossaryId"]');
                        const typeCell = row.querySelector('td:nth-child(3)');
                        if (glossarySelect && glossarySelect.value && relationship.glossaryTypeName && typeCell) {
                            // If we already have glossaryTypeName from API, use it directly
                            typeCell.innerHTML = `<span class="text-muted">${relationship.glossaryTypeName}</span>`;
                        }
                        // Trigger updateGlossaryOwner to set type and owner
                        if (glossarySelect && glossarySelect.value) {
                            updateGlossaryOwner(glossarySelect);
                        }
                    }
                }
            });
        }, 0);
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
            glossaryOwnerName: null,
            description: ''
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
                glossaryOwnerName: null,
                description: ''
            };
        } else {
            glossaryRelationships = glossaryRelationships.filter(rel => String(rel.id) !== String(id));
        }
        renderGlossaryTable();
    }

    function saveCurrentGlossaryFormData() {
        const rows = document.querySelectorAll('#glossaryImpactTableBody tr');
        rows.forEach((row, index) => {
            // Ensure the relationship exists in the array
            if (!glossaryRelationships[index]) {
                glossaryRelationships[index] = {
                    id: 'new-' + Date.now() + '-' + index,
                    relationType: null,
                    glossaryId: null,
                    glossaryName: null,
                    glossaryOwnerName: null,
                    glossaryTypeName: null,
                    description: ''
                };
            }
            
            const relationTypeSelect = row.querySelector('select[data-field="relationType"]');
            const glossaryIdSelect = row.querySelector('select[data-field="glossaryId"]');
                const descriptionTextarea = row.querySelector('textarea[data-field="description"]');
            const ownerCell = row.querySelector('td:nth-child(4)'); // Glossary Owner column
            const typeCell = row.querySelector('td:nth-child(3)'); // Type column
                
                if (relationTypeSelect) {
                glossaryRelationships[index].relationType = relationTypeSelect.value ? parseInt(relationTypeSelect.value) : null;
                }
                if (glossaryIdSelect) {
                glossaryRelationships[index].glossaryId = glossaryIdSelect.value ? parseInt(glossaryIdSelect.value) : null;
                // Update glossary name based on selected glossary
                const selectedOption = glossaryIdSelect.options[glossaryIdSelect.selectedIndex];
                if (selectedOption) {
                    glossaryRelationships[index].glossaryName = selectedOption.text;
                    }
                }
                if (descriptionTextarea) {
                    glossaryRelationships[index].description = descriptionTextarea.value || '';
            }
            // Preserve the current owner name from the DOM
            if (ownerCell) {
                const ownerSpan = ownerCell.querySelector('span');
                if (ownerSpan) {
                    glossaryRelationships[index].glossaryOwnerName = ownerSpan.textContent.trim();
                }
            }
            // Preserve the current type from the DOM
            if (typeCell) {
                const typeSpan = typeCell.querySelector('span');
                if (typeSpan) {
                    glossaryRelationships[index].glossaryTypeName = typeSpan.textContent.trim();
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
                        
                        // Update the relationship data structure with type name
                        const rowIndex = Array.from(row.parentElement.children).indexOf(row);
                        if (glossaryRelationships[rowIndex]) {
                            glossaryRelationships[rowIndex].glossaryTypeName = typeName || null;
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
            const response = await fetch(`/api/project-impact/glossary-owner/${glossaryId}`, {
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

    async function saveGlossaryRelationships(projectIdParam) {
        try {
            // Read from DOM
            saveCurrentGlossaryFormData();
            
            // Collect data from form (read from DOM to ensure all deletions are captured)
            const relationships = [];
            const rows = document.querySelectorAll('#glossaryImpactTableBody tr');
            
            rows.forEach((row, index) => {
                const relationTypeSelect = row.querySelector('select[data-field="relationType"]');
                const glossaryIdSelect = row.querySelector('select[data-field="glossaryId"]');
                const descriptionTextarea = row.querySelector('textarea[data-field="description"]');
                
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
                        relationTypeId: parseInt(relationTypeSelect.value),
                        description: descriptionTextarea ? (descriptionTextarea.value || '') : ''
                    });
                }
            });

            const projectIdToUse = projectIdParam || currentProjectId;
            if (!projectIdToUse) {
                return { success: false, message: 'Project ID is required' };
            }
            
            const requestData = {
                projectId: projectIdToUse,
                relationships: (await (async () => {
                    const filtered = await filterRelationshipsForSegment(relationships, 'glossaryId', 'Glossary');
                    if (relationships.length > 0 && filtered.validRelationships.length === 0 && filtered.skippedCount > 0) {
                        throw new Error('No glossary relationships were saved: selected objects belong to another private segment.');
                    }
                    return filtered.validRelationships;
                })())
            };

            const response = await fetch('/api/project-impact/glossaries/save', {
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
                // Update original data to mark as saved
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
    
    async function loadProjectRelationships(forceReload = false) {
        try {
            console.log('=== loadProjectRelationships START ===');
            console.log('Current project ID:', currentProjectId);
            console.log('Force reload:', forceReload);
            
            // Only check visibility on initial load, not when force reloading after save
            if (!forceReload) {
                const projectContent = document.getElementById('impactProjectContent');
                if (projectContent && projectContent.style.display === 'none') {
                    console.log('loadProjectRelationships: Tab not visible on initial load, skipping');
                    return;
                }
            }
            
            const response = await fetch(`/api/project-impact/${currentProjectId}/projects`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            console.log('loadProjectRelationships: API response:', data);
            // Normalize relationTypeId to relationType for consistency
            projectRelationships = Array.isArray(data) ? data.map(r => ({
                ...r,
                relationType: r.relationType || r.relationTypeId || null
            })) : [];
            console.log('loadProjectRelationships: Loaded', projectRelationships.length, 'relationships');
            originalProjectData = JSON.parse(JSON.stringify(projectRelationships));
            renderProjectTable();
            console.log('=== loadProjectRelationships SUCCESS ===');

        } catch (error) {
            console.error('=== loadProjectRelationships ERROR ===', error);
            projectRelationships = [];
            originalProjectData = [];
            renderProjectTable();
        }
    }

    async function loadProjectRelationTypes() {
        try {
            const response = await fetch('/api/project-impact/project-relation-types', {
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
            projects = (await filterRelationshipsForSegment(projects, (p) => p?.id ?? p?.ID, 'Project')).validRelationships;

        } catch (error) {
            console.error('Error loading projects:', error);
            projects = [];
        }
    }

    function renderProjectTable() {
        const tbody = document.querySelector('#projectImpactTableBody');
        const footer = document.querySelector('#projectImpactFooter');
        
        if (!tbody) return;
        
        // Check if the project sub-tab content is visible
        const projectContent = document.getElementById('impactProjectContent');
        if (projectContent && projectContent.style.display === 'none') {
            // Project tab is not visible, don't render to avoid affecting other tabs
            return;
        }
        
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
        // Compare as strings so persisted numeric IDs and DOM string IDs match.
        const index = projectRelationships.findIndex(r => String(r.id || 'new-empty') === String(id));
        if (index > -1) {
            projectRelationships.splice(index, 1);
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
            renderProjectTable();
        }
    }

    function saveCurrentProjectFormData() {
        const rows = document.querySelectorAll('#projectImpactTableBody tr');
        rows.forEach((row, index) => {
            // Ensure the relationship exists in the array
            if (!projectRelationships[index]) {
                projectRelationships[index] = {
                    id: 'new-' + Date.now() + '-' + index,
                relationType: null,
                    projectId: null,
                projectName: null,
                    projectRefNumber: null,
                projectOwnerName: null,
                    description: ''
                };
            }
            
            const relationTypeSelect = row.querySelector('select[data-field="relationType"]');
            const projectIdSelect = row.querySelector('select[data-field="projectId"]');
            const descriptionInput = row.querySelector('input[data-field="description"]');
            const ownerCell = row.querySelector('td:nth-child(4)'); // Project Owner column
            const refCell = row.querySelector('td:nth-child(3)'); // Ref. column
            
                if (relationTypeSelect) {
                projectRelationships[index].relationType = relationTypeSelect.value ? parseInt(relationTypeSelect.value) : null;
                }
                if (projectIdSelect) {
                projectRelationships[index].projectId = projectIdSelect.value ? parseInt(projectIdSelect.value) : null;
                // Update project name based on selected project
                const selectedOption = projectIdSelect.options[projectIdSelect.selectedIndex];
                if (selectedOption) {
                    projectRelationships[index].projectName = selectedOption.text;
                }
                }
                if (descriptionInput) {
                projectRelationships[index].description = descriptionInput.value || '';
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
        });
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
            const response = await fetch(`/api/project-impact/project-owner/${projectId}`, {
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

    async function saveProjectRelationships(projectIdParam) {
        try {
            // Read from DOM
            saveCurrentProjectFormData();
            
            const relationships = projectRelationships
                .filter(r => r.projectId && r.relationType)
                .map(r => ({
                    id: r.id && !String(r.id).startsWith('new-') ? r.id : null,
                    projectId: parseInt(r.projectId),
                    relationTypeId: parseInt(r.relationType),
                    description: r.description || ''
                }));

            const projectIdToUse = projectIdParam || currentProjectId;
            if (!projectIdToUse) {
                return { success: false, message: 'Project ID is required' };
            }
            
            const requestData = {
                projectId: projectIdToUse,
                relationships: (await (async () => {
                    const filtered = await filterRelationshipsForSegment(relationships, 'projectId', 'Project');
                    if (relationships.length > 0 && filtered.validRelationships.length === 0 && filtered.skippedCount > 0) {
                        throw new Error('No project relationships were saved: selected objects belong to another private segment.');
                    }
                    return filtered.validRelationships;
                })())
            };

            const response = await fetch('/api/project-impact/projects/save', {
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
                // Update original data to mark as saved
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
    
    async function loadPolicyRelationships(forceReload = false) {
        try {
            console.log('=== loadPolicyRelationships START ===');
            console.log('Current project ID:', currentProjectId);
            console.log('Force reload:', forceReload);
            
            // Only check visibility on initial load, not when force reloading after save
            if (!forceReload) {
                const policyContent = document.getElementById('impactPolicyContent');
                if (policyContent && policyContent.style.display === 'none') {
                    console.log('loadPolicyRelationships: Tab not visible on initial load, skipping');
                    return;
                }
            }
            
            const response = await fetch(`/api/project-impact/${currentProjectId}/policies`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            console.log('loadPolicyRelationships: API response:', data);
            // Normalize relationTypeId to relationType for consistency
            // Also ensure policyTypeName and policyRefNumber are preserved
            policyRelationships = Array.isArray(data) ? data.map(r => ({
                ...r,
                relationType: r.relationType || r.relationTypeId || null,
                policyTypeName: r.policyTypeName || r.typeName || r.type_name || null,
                policyRefNumber: r.policyRefNumber || r.refNumber || r.ref_number || null
            })) : [];
            console.log('loadPolicyRelationships: Loaded', policyRelationships.length, 'relationships');
            
            originalPolicyData = JSON.parse(JSON.stringify(policyRelationships));
            renderPolicyTable();
            console.log('=== loadPolicyRelationships SUCCESS ===');

        } catch (error) {
            console.error('=== loadPolicyRelationships ERROR ===', error);
            policyRelationships = [];
            originalPolicyData = [];
            renderPolicyTable();
        }
    }

    async function loadPolicyRelationTypes() {
        try {
            const response = await fetch('/api/project-impact/policy-relation-types', {
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
            policies = (await filterRelationshipsForSegment(policies, (p) => p?.id ?? p?.ID, 'Policy')).validRelationships;

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
            if (Array.isArray(data)) {
                policyTypes = data;
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
        
        // Check if the policy sub-tab content is visible
        const policyContent = document.getElementById('impactPolicyContent');
        if (policyContent && policyContent.style.display === 'none') {
            // Policy tab is not visible, don't render to avoid affecting other tabs
            return;
        }
        
        if (policyRelationships.length === 0) {
            policyRelationships.push({
                id: 'new-empty',
                relationType: null,
                policyId: null,
                policyName: null,
                policyOwnerName: null,
                description: ''
            });
        }
        
        console.log('Project Impact Edit: renderPolicyTable called with', policyRelationships.length, 'relationships');
        
        let html = '';
        let renderedCount = 0;
        policyRelationships.forEach((relationship, index) => {
            renderedCount++;
            const rowId = relationship.id || 'new-' + index;
            const isNewRow = !relationship.id || String(relationship.id).startsWith('new-');
            const ownerName = relationship.policyOwnerName || relationship.ownerName || '';
            const ownerDisplay = isNewRow && !relationship.policyId ? '' : (ownerName || 'No owner');
            
            console.log(`Project Impact Edit: Rendering policy relationship ${renderedCount}:`, {
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
                    <td><textarea class="form-control" data-field="description" rows="2" placeholder="Enter description">${relationship.description || ''}</textarea></td>
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
        
        console.log('Project Impact Edit: renderPolicyTable rendered', renderedCount, 'rows');
        tbody.innerHTML = html;
        if (footer) footer.textContent = `${policyRelationships.length} record${policyRelationships.length !== 1 ? 's' : ''}`;
        
        // Update type names and ref numbers for already selected policies
        // Use setTimeout to ensure DOM is fully rendered
        setTimeout(() => {
            policyRelationships.forEach((relationship, index) => {
                if (relationship.policyId) {
                    const row = tbody.children[index];
                    if (row) {
                        const policySelect = row.querySelector('select[data-field="policyId"]');
                        const refCell = row.querySelector('td:nth-child(3)');
                        const typeCell = row.querySelector('td:nth-child(4)');
                        if (policySelect && policySelect.value) {
                            // If we already have policyRefNumber from API, use it directly
                            if (relationship.policyRefNumber && refCell) {
                                refCell.innerHTML = `<span class="text-muted">${relationship.policyRefNumber}</span>`;
                            }
                            // If we already have policyTypeName from API, use it directly
                            if (relationship.policyTypeName && typeCell) {
                                typeCell.innerHTML = `<span class="text-muted">${relationship.policyTypeName}</span>`;
                            }
                            // Trigger updatePolicyOwner to set ref, type, and owner
                            updatePolicyOwner(policySelect);
                        }
                    }
                }
            });
        }, 0);
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
            policyOwnerName: null,
            description: ''
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
                policyOwnerName: null,
                description: ''
            }];
        } else {
            policyRelationships = policyRelationships.filter(r => r.id != id);
        }
        renderPolicyTable();
    }

    function saveCurrentPolicyFormData() {
        const rows = document.querySelectorAll('#policyImpactTableBody tr');
        rows.forEach((row, index) => {
            // Ensure the relationship exists in the array
            if (!policyRelationships[index]) {
                policyRelationships[index] = {
                    id: 'new-' + Date.now() + '-' + index,
                relationType: null,
                    policyId: null,
                policyName: null,
                policyRefNumber: null,
                policyTypeName: null,
                    policyOwnerName: null,
                    description: ''
                };
            }
            
            const relationTypeSelect = row.querySelector('select[data-field="relationType"]');
            const policyIdSelect = row.querySelector('select[data-field="policyId"]');
            const descriptionTextarea = row.querySelector('textarea[data-field="description"]');
            const ownerCell = row.querySelector('td:nth-child(5)'); // Policy Owner column
            const refCell = row.querySelector('td:nth-child(3)'); // Ref. column
            const typeCell = row.querySelector('td:nth-child(4)'); // Type column
            
            // Preserve the current reference number from the DOM
            if (refCell) {
                const refSpan = refCell.querySelector('span');
                if (refSpan) {
                    policyRelationships[index].policyRefNumber = refSpan.textContent.trim();
                }
            }
            
            // Preserve the current type name from the DOM
            if (typeCell) {
                const typeSpan = typeCell.querySelector('span');
                if (typeSpan) {
                    policyRelationships[index].policyTypeName = typeSpan.textContent.trim();
                }
            }
            
                if (relationTypeSelect) {
                policyRelationships[index].relationType = relationTypeSelect.value ? parseInt(relationTypeSelect.value) : null;
                }
                if (policyIdSelect) {
                policyRelationships[index].policyId = policyIdSelect.value ? parseInt(policyIdSelect.value) : null;
                // Update policy name based on selected policy
                const selectedOption = policyIdSelect.options[policyIdSelect.selectedIndex];
                if (selectedOption) {
                    policyRelationships[index].policyName = selectedOption.text;
                }
            }
                if (descriptionTextarea) {
                policyRelationships[index].description = descriptionTextarea.value || '';
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
            // Preserve the current type from the DOM
            if (typeCell) {
                const typeSpan = typeCell.querySelector('span');
                if (typeSpan) {
                    policyRelationships[index].policyTypeName = typeSpan.textContent.trim();
                }
            }
        });
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
            const response = await fetch(`/api/project-impact/policy-owner/${policyId}`, {
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

    async function savePolicyRelationships(projectIdParam) {
        try {
            // Read from DOM
            saveCurrentPolicyFormData();
            
            const relationships = [];
            // Read from the array, not from DOM, to ensure we have all data
            policyRelationships.forEach(relationship => {
                // Only save relationships that have both relationType and policyId
                if (relationship.relationType && relationship.policyId) {
                    relationships.push({
                        id: relationship.id && !String(relationship.id).startsWith('new-') ? parseInt(relationship.id) : null,
                        policyId: parseInt(relationship.policyId),
                        relationTypeId: parseInt(relationship.relationType),
                        description: relationship.description || ''
                    });
                }
            });

            const projectIdToUse = projectIdParam || currentProjectId;
            if (!projectIdToUse) {
                return { success: false, message: 'Project ID is required' };
            }
            
            const requestData = {
                projectId: projectIdToUse,
                relationships: (await (async () => {
                    const filtered = await filterRelationshipsForSegment(relationships, 'policyId', 'Policy');
                    if (relationships.length > 0 && filtered.validRelationships.length === 0 && filtered.skippedCount > 0) {
                        throw new Error('No policy relationships were saved: selected objects belong to another private segment.');
                    }
                    return filtered.validRelationships;
                })())
            };

            const response = await fetch('/api/project-impact/policies/save', {
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
                // Update original data to mark as saved
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
    
    async function loadInterfaceRelationships(forceReload = false) {
        try {
            console.log('=== loadInterfaceRelationships START ===');
            console.log('Current project ID:', currentProjectId);
            console.log('Force reload:', forceReload);
            
            // Only check visibility on initial load, not when force reloading after save
            if (!forceReload) {
                const interfaceContent = document.getElementById('impactInterfaceContent');
                if (interfaceContent && interfaceContent.style.display === 'none') {
                    console.log('loadInterfaceRelationships: Tab not visible on initial load, skipping');
                    return;
                }
            }
            
            const response = await fetch(`/api/project-impact/${currentProjectId}/interfaces`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            console.log('loadInterfaceRelationships: API response:', data);
            // Normalize relationTypeId to relationType for consistency
            interfaceRelationships = Array.isArray(data) ? data.map(r => ({
                ...r,
                relationType: r.relationType || r.relationTypeId || null
            })) : [];
            console.log('loadInterfaceRelationships: Loaded', interfaceRelationships.length, 'relationships');
            originalInterfaceData = JSON.parse(JSON.stringify(interfaceRelationships));
            renderInterfaceTable();
            console.log('=== loadInterfaceRelationships SUCCESS ===');

        } catch (error) {
            console.error('=== loadInterfaceRelationships ERROR ===', error);
            interfaceRelationships = [];
            originalInterfaceData = [];
            renderInterfaceTable();
        }
    }

    async function loadInterfaceRelationTypes() {
        try {
            const response = await fetch('/api/project-impact/interface-relation-types', {
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
            const response = await fetch('/api/project-impact/interfaces', {
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
        
        // Check if the interface sub-tab content is visible
        const interfaceContent = document.getElementById('impactInterfaceContent');
        if (interfaceContent && interfaceContent.style.display === 'none') {
            // Interface tab is not visible, don't render to avoid affecting other tabs
            return;
        }
        
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
        const rows = document.querySelectorAll('#interfaceImpactTableBody tr');
        rows.forEach((row, index) => {
            // Ensure the relationship exists in the array
            if (!interfaceRelationships[index]) {
                interfaceRelationships[index] = {
                    id: 'new-' + Date.now() + '-' + index,
                relationType: null,
                    interfaceId: null,
                interfaceName: null
            };
            }
            
            const relationTypeSelect = row.querySelector('select[data-field="relationType"]');
            const interfaceIdSelect = row.querySelector('select[data-field="interfaceId"]');
                
                if (relationTypeSelect) {
                interfaceRelationships[index].relationType = relationTypeSelect.value ? parseInt(relationTypeSelect.value) : null;
                }
                if (interfaceIdSelect) {
                interfaceRelationships[index].interfaceId = interfaceIdSelect.value ? parseInt(interfaceIdSelect.value) : null;
                // Update interface name based on selected interface
                const selectedOption = interfaceIdSelect.options[interfaceIdSelect.selectedIndex];
                if (selectedOption) {
                    interfaceRelationships[index].interfaceName = selectedOption.text;
                }
            }
        });
    }

    // Interface owner functionality removed - interface table does not have owner column

    async function saveInterfaceRelationships() {
        try {
            // Read from DOM
            saveCurrentInterfaceFormData();
            
            const relationships = [];
            // Read from the array, not from DOM, to ensure we have all data
            interfaceRelationships.forEach(relationship => {
                // Only save relationships that have both relationType and interfaceId
                if (relationship.relationType && relationship.interfaceId) {
                    relationships.push({
                        id: relationship.id && !String(relationship.id).startsWith('new-') ? parseInt(relationship.id) : null,
                        interfaceId: parseInt(relationship.interfaceId),
                        relationTypeId: parseInt(relationship.relationType)
                    });
                }
            });

            const projectIdToUse = projectIdParam || currentProjectId;
            if (!projectIdToUse) {
                return { success: false, message: 'Project ID is required' };
            }
            
            const requestData = {
                projectId: projectIdToUse,
                relationships: (await (async () => {
                    const filtered = await filterRelationshipsForSegment(relationships, 'datasetId', 'Dataset');
                    if (relationships.length > 0 && filtered.validRelationships.length === 0 && filtered.skippedCount > 0) {
                        throw new Error('No dataset relationships were saved: selected objects belong to another private segment.');
                    }
                    return filtered.validRelationships;
                })())
            };

            const response = await fetch('/api/project-impact/interfaces/save', {
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
                // Update original data to mark as saved
                originalInterfaceData = JSON.parse(JSON.stringify(interfaceRelationships));
                return true;
            } else {
                return { success: false, message: result.message || 'Failed to save interface relationships' };
            }
        } catch (error) {
            console.error('Error saving interface relationships:', error);
            return { success: false, message: 'Network error: ' + error.message };
        }
    }

    // ===== LEGAL ENTITY RELATIONSHIPS =====
    
    async function loadLegalRelationships(forceReload = false) {
        try {
            console.log('=== loadLegalRelationships START ===');
            console.log('Current project ID:', currentProjectId);
            console.log('Force reload:', forceReload);
            
            // Only check visibility on initial load, not when force reloading after save
            if (!forceReload) {
                const legalContent = document.getElementById('impactLegalContent');
                if (legalContent && legalContent.style.display === 'none') {
                    console.log('loadLegalRelationships: Tab not visible on initial load, skipping');
                    return;
                }
            }
            
            const response = await fetch(`/api/project-impact/${currentProjectId}/legals`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            console.log('loadLegalRelationships: API response:', data);
            // Normalize relationTypeId to relationType for consistency
            legalRelationships = Array.isArray(data) ? data.map(r => ({
                ...r,
                relationType: r.relationType || r.relationTypeId || null
            })) : [];
            console.log('loadLegalRelationships: Loaded', legalRelationships.length, 'relationships');
            originalLegalData = JSON.parse(JSON.stringify(legalRelationships));
            renderLegalTable();
            console.log('=== loadLegalRelationships SUCCESS ===');

        } catch (error) {
            console.error('=== loadLegalRelationships ERROR ===', error);
            legalRelationships = [];
            originalLegalData = [];
            renderLegalTable();
        }
    }

    async function loadLegalRelationTypes() {
        try {
            const response = await fetch('/api/project-impact/legal-relation-types', {
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
            legals = (await filterRelationshipsForSegment(legals, (l) => l?.id ?? l?.ID ?? l?.Legal_ID, 'LegalEntity')).validRelationships;
            
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
        
        // Check if the legal sub-tab content is visible
        const legalContent = document.getElementById('impactLegalContent');
        if (legalContent && legalContent.style.display === 'none') {
            // Legal tab is not visible, don't render to avoid affecting other tabs
            return;
        }
        
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
        const rows = document.querySelectorAll('#legalImpactTableBody tr');
        rows.forEach((row, index) => {
            // Ensure the relationship exists in the array
            if (!legalRelationships[index]) {
                legalRelationships[index] = {
                    id: 'new-' + Date.now() + '-' + index,
                    relationType: null,
                    legalId: null
                };
            }
            
            const relationTypeSelect = row.querySelector('select[data-field="relationType"]');
            const legalIdSelect = row.querySelector('select[data-field="legalId"]');
                
                if (relationTypeSelect) {
                legalRelationships[index].relationType = relationTypeSelect.value ? parseInt(relationTypeSelect.value) : null;
                }
                if (legalIdSelect) {
                legalRelationships[index].legalId = legalIdSelect.value ? parseInt(legalIdSelect.value) : null;
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
            const response = await fetch(`/api/project-impact/legal-owner/${legalId}`, {
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
            // Read from DOM
            saveCurrentLegalFormData();
            
            const relationships = [];
            // Read from the array, not from DOM, to ensure we have all data
            legalRelationships.forEach(relationship => {
                // Only save relationships that have both relationType and legalId
                if (relationship.relationType && relationship.legalId) {
                    relationships.push({
                        id: relationship.id && !String(relationship.id).startsWith('new-') ? parseInt(relationship.id) : null,
                        legalId: parseInt(relationship.legalId),
                        relationTypeId: parseInt(relationship.relationType)
                    });
                }
            });

            const projectIdToUse = projectIdParam || currentProjectId;
            if (!projectIdToUse) {
                return { success: false, message: 'Project ID is required' };
            }
            
            const requestData = {
                projectId: projectIdToUse,
                relationships: (await (async () => {
                    const filtered = await filterRelationshipsForSegment(relationships, 'attributeId', 'Attribute');
                    if (relationships.length > 0 && filtered.validRelationships.length === 0 && filtered.skippedCount > 0) {
                        throw new Error('No attribute relationships were saved: selected objects belong to another private segment.');
                    }
                    return filtered.validRelationships;
                })())
            };

            const response = await fetch('/api/project-impact/legals/save', {
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
                // Update original data to mark as saved
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
            const response = await fetch('/api/project-impact/dataset-relation-types', {
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
            const response = await fetch('/api/project-impact/datasets-list', {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });
            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
            const data = await response.json();
            datasets = Array.isArray(data) ? data : [];
            console.log('Loaded datasets:', datasets.length);
        } catch (error) {
            console.error('Error loading datasets:', error);
            datasets = [];
        }
    }

    // Load dataset relationships
    async function loadDatasetRelationships(forceReload = false) {
        try {
            console.log('=== loadDatasetRelationships START ===');
            console.log('Current project ID:', currentProjectId);
            console.log('Force reload:', forceReload);
            
            // Only check visibility on initial load, not when force reloading after save
            if (!forceReload) {
                const datasetContent = document.getElementById('impactDatasetContent');
                if (datasetContent && datasetContent.style.display === 'none') {
                    console.log('loadDatasetRelationships: Tab not visible on initial load, skipping');
                    return;
                }
            }
            
            const response = await fetch(`/api/project-impact/${currentProjectId}/datasets`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });
            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
            const data = await response.json();
            console.log('loadDatasetRelationships: API response:', data);
            // Normalize relationTypeId to relationType for consistency
            datasetRelationships = Array.isArray(data) ? data.map(r => ({
                ...r,
                relationType: r.relationType || r.relationTypeId || null
            })) : [];
            console.log('loadDatasetRelationships: Loaded', datasetRelationships.length, 'relationships');
            originalDatasetData = JSON.parse(JSON.stringify(datasetRelationships));
            if (datasetRelationships.length > 0) {
                console.log('First dataset relationship:', datasetRelationships[0]);
                console.log('Dataset owner name:', datasetRelationships[0].datasetOwnerName);
            }
            renderDatasetTable();
            console.log('=== loadDatasetRelationships SUCCESS ===');
        } catch (error) {
            console.error('=== loadDatasetRelationships ERROR ===', error);
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
                            ${datasetRelationTypes.map(rt => {
                                const rtId = rt.id || rt.ID;
                                const relType = relationship.relationType || relationship.relationTypeId;
                                return `<option value="${rtId}" ${relType == rtId ? 'selected' : ''}>${rt.primaryName || rt.primaryname || rt.PrimaryName || 'Unnamed Type'}</option>`;
                            }).join('')}
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
        const rows = document.querySelectorAll('#datasetImpactTableBody tr');
        rows.forEach((row, index) => {
            // Ensure the relationship exists in the array
            if (!datasetRelationships[index]) {
                datasetRelationships[index] = {
                    id: 'new-' + Date.now() + '-' + index,
                    relationType: null,
                systemId: null,
                datasetId: null,
                    systemName: null,
                datasetName: null,
                    datasetOwnerName: null
                };
            }
            
            const relationTypeSelect = row.querySelector('select[data-field="relationType"]');
            const systemSelect = row.querySelector('select[data-field="systemId"]');
            const datasetSelect = row.querySelector('select[data-field="datasetId"]');
            const ownerCell = row.querySelector('td:nth-child(4)'); // Dataset Owner column
            
            if (relationTypeSelect) {
                datasetRelationships[index].relationType = relationTypeSelect.value ? parseInt(relationTypeSelect.value) : null;
            }
            if (systemSelect) {
                datasetRelationships[index].systemId = systemSelect.value ? parseInt(systemSelect.value) : null;
                // Update system name based on selected system
                const selectedOption = systemSelect.options[systemSelect.selectedIndex];
                if (selectedOption) {
                    datasetRelationships[index].systemName = selectedOption.getAttribute('data-system-name') || selectedOption.text;
                }
            }
            if (datasetSelect) {
                datasetRelationships[index].datasetId = datasetSelect.value ? parseInt(datasetSelect.value) : null;
                // Update dataset name based on selected dataset
                const selectedOption = datasetSelect.options[datasetSelect.selectedIndex];
                if (selectedOption) {
                    datasetRelationships[index].datasetName = selectedOption.text;
                }
            }
            // Preserve the current owner name from the DOM
            if (ownerCell) {
                const ownerSpan = ownerCell.querySelector('span');
                if (ownerSpan) {
                    datasetRelationships[index].datasetOwnerName = ownerSpan.textContent.trim();
                }
            }
        });
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
                const response = await fetch(`/api/project-impact/dataset-owner/${datasetId}`, {
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
    async function saveDatasetRelationships(projectIdParam) {
        try {
            // Read from DOM
            saveCurrentDatasetFormData();
            
            const relationships = [];
            // Read from the array, not from DOM, to ensure we have all data
            datasetRelationships.forEach(relationship => {
                // Only save relationships that have relationType, systemId, and datasetId
                if (relationship.relationType && relationship.systemId && relationship.datasetId) {
                    relationships.push({
                        id: relationship.id && !String(relationship.id).startsWith('new-') ? parseInt(relationship.id) : null,
                        systemId: parseInt(relationship.systemId),
                        datasetId: parseInt(relationship.datasetId),
                        relationTypeId: parseInt(relationship.relationType)
                    });
                }
            });
            
            const projectIdToUse = projectIdParam || currentProjectId;
            if (!projectIdToUse) {
                return { success: false, message: 'Project ID is required' };
            }
            
            const requestData = {
                projectId: projectIdToUse,
                relationships: relationships
            };
            
            const response = await fetch('/api/project-impact/datasets/save', {
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
                // Update original data to mark as saved
                originalDatasetData = JSON.parse(JSON.stringify(datasetRelationships));
            return true;
            } else {
                return { success: false, message: result.message || 'Failed to save dataset relationships' };
            }
        } catch (error) {
            console.error('Error saving dataset relationships:', error);
            return { success: false, message: 'Network error: ' + error.message };
        }
    }

    // ==================== ATTRIBUTE FUNCTIONS ====================
    
    // Load attribute relation types
    async function loadAttributeRelationTypes() {
        try {
            const response = await fetch('/api/project-impact/attribute-relation-types', {
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
            const response = await fetch('/api/project-impact/attributes-list', {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });
            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
            const data = await response.json();
            attributes = Array.isArray(data) ? data : [];
            console.log('Loaded attributes:', attributes.length);
        } catch (error) {
            console.error('Error loading attributes:', error);
            attributes = [];
        }
    }

    // Load attribute relationships
    async function loadAttributeRelationships(forceReload = false) {
        try {
            console.log('=== loadAttributeRelationships START ===');
            console.log('Current project ID:', currentProjectId);
            console.log('Force reload:', forceReload);
            
            // Only check visibility on initial load, not when force reloading after save
            if (!forceReload) {
                const attributeContent = document.getElementById('impactAttributeContent');
                if (attributeContent && attributeContent.style.display === 'none') {
                    console.log('loadAttributeRelationships: Tab not visible on initial load, skipping');
                    return;
                }
            }
            
            const response = await fetch(`/api/project-impact/${currentProjectId}/attributes`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });
            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
            const data = await response.json();
            console.log('loadAttributeRelationships: API response:', data);
            // Normalize relationTypeId to relationType for consistency
            attributeRelationships = Array.isArray(data) ? data.map(r => ({
                ...r,
                relationType: r.relationType || r.relationTypeId || null
            })) : [];
            console.log('loadAttributeRelationships: Loaded', attributeRelationships.length, 'relationships');
            originalAttributeData = JSON.parse(JSON.stringify(attributeRelationships));
            if (attributeRelationships.length > 0) {
                console.log('First attribute relationship:', attributeRelationships[0]);
                console.log('Attribute owner name:', attributeRelationships[0].attributeOwnerName);
            }
            renderAttributeTable();
            console.log('=== loadAttributeRelationships SUCCESS ===');
        } catch (error) {
            console.error('=== loadAttributeRelationships ERROR ===', error);
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
                            ${attributeRelationTypes.map(rt => {
                                const rtId = rt.id || rt.ID;
                                const relType = relationship.relationType || relationship.relationTypeId;
                                return `<option value="${rtId}" ${relType == rtId ? 'selected' : ''}>${rt.primaryName || rt.primaryname || rt.PrimaryName || 'Unnamed Type'}</option>`;
                            }).join('')}
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
        const rows = document.querySelectorAll('#attributeImpactTableBody tr');
        rows.forEach((row, index) => {
            // Ensure the relationship exists in the array
            if (!attributeRelationships[index]) {
                attributeRelationships[index] = {
                    id: 'new-' + Date.now() + '-' + index,
                relationType: null,
                systemId: null,
                    datasetId: null,
                    attributeId: null,
                    systemName: null,
                    datasetName: null,
                    attributeName: null,
                    attributeOwnerName: null
                };
            }
            
            const relationTypeSelect = row.querySelector('select[data-field="relationType"]');
            const systemSelect = row.querySelector('select[data-field="systemId"]');
            const datasetSelect = row.querySelector('select[data-field="datasetId"]');
            const attributeSelect = row.querySelector('select[data-field="attributeId"]');
            const ownerCell = row.querySelector('td:nth-child(5)'); // Attribute Owner column
            
            if (relationTypeSelect) {
                attributeRelationships[index].relationType = relationTypeSelect.value ? parseInt(relationTypeSelect.value) : null;
            }
            if (systemSelect) {
                attributeRelationships[index].systemId = systemSelect.value ? parseInt(systemSelect.value) : null;
                // Update system name based on selected system
                const selectedOption = systemSelect.options[systemSelect.selectedIndex];
                if (selectedOption) {
                    attributeRelationships[index].systemName = selectedOption.getAttribute('data-system-name') || selectedOption.text;
                }
            }
            if (datasetSelect) {
                attributeRelationships[index].datasetId = datasetSelect.value ? parseInt(datasetSelect.value) : null;
                // Update dataset name based on selected dataset
                const selectedOption = datasetSelect.options[datasetSelect.selectedIndex];
                if (selectedOption) {
                    attributeRelationships[index].datasetName = selectedOption.text;
                }
            }
            if (attributeSelect) {
                attributeRelationships[index].attributeId = attributeSelect.value ? parseInt(attributeSelect.value) : null;
                // Update attribute name based on selected attribute
                const selectedOption = attributeSelect.options[attributeSelect.selectedIndex];
                if (selectedOption) {
                    attributeRelationships[index].attributeName = selectedOption.text;
                }
            }
            // Preserve the current owner name from the DOM
            if (ownerCell) {
                const ownerSpan = ownerCell.querySelector('span');
                if (ownerSpan) {
                    attributeRelationships[index].attributeOwnerName = ownerSpan.textContent.trim();
                }
            }
        });
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
                const response = await fetch(`/api/project-impact/attribute-owner/${attributeId}`, {
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
    async function saveAttributeRelationships(projectIdParam) {
        try {
            // Read from DOM
            saveCurrentAttributeFormData();
            
            const relationships = [];
            // Read from the array, not from DOM, to ensure we have all data
            attributeRelationships.forEach(relationship => {
                // Only save relationships that have relationType, systemId, datasetId, and attributeId
                if (relationship.relationType && relationship.systemId && relationship.datasetId && relationship.attributeId) {
                    relationships.push({
                        id: relationship.id && !String(relationship.id).startsWith('new-') ? parseInt(relationship.id) : null,
                        systemId: parseInt(relationship.systemId),
                        datasetId: parseInt(relationship.datasetId),
                        attributeId: parseInt(relationship.attributeId),
                        relationTypeId: parseInt(relationship.relationType)
                    });
                }
            });
            
            const projectIdToUse = projectIdParam || currentProjectId;
            if (!projectIdToUse) {
                return { success: false, message: 'Project ID is required' };
            }
            
            const requestData = {
                projectId: projectIdToUse,
                relationships: relationships
            };
            
            const response = await fetch('/api/project-impact/attributes/save', {
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
                // Update original data to mark as saved
                originalAttributeData = JSON.parse(JSON.stringify(attributeRelationships));
            return true;
            } else {
                return { success: false, message: result.message || 'Failed to save attribute relationships' };
            }
        } catch (error) {
            console.error('Error saving attribute relationships:', error);
            return { success: false, message: 'Network error: ' + error.message };
        }
    }

    // ===== SAVE ALL IMPACT DATA =====
    
    async function saveAllImpactData(projectIdParam) {
        try {
            // Use passed projectId or fall back to currentProjectId
            const projectIdToUse = projectIdParam || currentProjectId;
            console.log('=== saveAllImpactData START ===');
            console.log('Project ID to use:', projectIdToUse);
            console.log('Current project ID:', currentProjectId);
            
            if (!projectIdToUse) {
                return {
                    success: false,
                    message: 'Project ID is required. Please ensure the project is loaded correctly.',
                    failedTabs: ['all'],
                    errorDetails: ['Project ID is missing']
                };
            }
            
            // Check if user is authenticated before attempting to save
            try {
                const authCheck = await fetch('/api/me', {
                    method: 'GET',
                    credentials: 'include'
                });
                if (!authCheck.ok) {
                    console.error('User not authenticated, cannot save');
                    return {
                        success: false,
                        message: 'Authentication required. Please refresh the page and log in again.',
                        failedTabs: ['all'],
                        errorDetails: ['Session expired. Please refresh the page.']
                    };
                }
            } catch (authError) {
                console.error('Auth check failed:', authError);
                return {
                    success: false,
                    message: 'Authentication check failed. Please refresh the page and log in again.',
                    failedTabs: ['all'],
                    errorDetails: ['Authentication check failed']
                };
            }
            
            const results = {
                system: await saveSystemRelationships(projectIdToUse),
                process: await saveProcessRelationships(projectIdToUse),
                glossary: await saveGlossaryRelationships(projectIdToUse),
                policy: await savePolicyRelationships(projectIdToUse),
                product: await saveProductRelationships(projectIdToUse),
                client: await saveClientRelationships(projectIdToUse),
                capability: await saveCapabilityRelationships(projectIdToUse),
                businessArea: await saveBusinessAreaRelationships(projectIdToUse),
                dataset: await saveDatasetRelationships(projectIdToUse),
                attribute: await saveAttributeRelationships(projectIdToUse)
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
            
            // Check each result and collect failure information
            if (results.system !== true && (typeof results.system !== 'object' || !results.system.success)) {
                failedTabs.push('System');
                if (results.system && typeof results.system === 'object' && results.system.message) {
                    errorDetails.push(`System: ${results.system.message}`);
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
            if (results.policy !== true && (typeof results.policy !== 'object' || !results.policy.success)) {
                failedTabs.push('Policy');
                if (results.policy && typeof results.policy === 'object' && results.policy.message) {
                    errorDetails.push(`Policy: ${results.policy.message}`);
                }
            }
            if (results.product !== true && (typeof results.product !== 'object' || !results.product.success)) {
                failedTabs.push('Product');
                if (results.product && typeof results.product === 'object' && results.product.message) {
                    errorDetails.push(`Product: ${results.product.message}`);
                }
            }
            if (results.client !== true && (typeof results.client !== 'object' || !results.client.success)) {
                failedTabs.push('Client');
                if (results.client && typeof results.client === 'object' && results.client.message) {
                    errorDetails.push(`Client: ${results.client.message}`);
                }
            }
            if (results.capability !== true && (typeof results.capability !== 'object' || !results.capability.success)) {
                failedTabs.push('Capability');
                if (results.capability && typeof results.capability === 'object' && results.capability.message) {
                    errorDetails.push(`Capability: ${results.capability.message}`);
                }
            }
            if (results.businessArea !== true && (typeof results.businessArea !== 'object' || !results.businessArea.success)) {
                failedTabs.push('Business Area');
                if (results.businessArea && typeof results.businessArea === 'object' && results.businessArea.message) {
                    errorDetails.push(`Business Area: ${results.businessArea.message}`);
                }
            }
            if (results.dataset !== true && (typeof results.dataset !== 'object' || !results.dataset.success)) {
                failedTabs.push('Data Set');
                if (results.dataset && typeof results.dataset === 'object' && results.dataset.message) {
                    errorDetails.push(`Data Set: ${results.dataset.message}`);
                }
            }
            if (results.attribute !== true && (typeof results.attribute !== 'object' || !results.attribute.success)) {
                failedTabs.push('Data Attributes');
                if (results.attribute && typeof results.attribute === 'object' && results.attribute.message) {
                    errorDetails.push(`Data Attributes: ${results.attribute.message}`);
                }
            }
            
            console.log('All success:', allSuccess, 'Failed tabs:', failedTabs, 'Error details:', errorDetails);
            
            if (allSuccess) {
                console.log('=== saveAllImpactData SUCCESS ===');
                return {
                    success: true,
                    failedTabs: [],
                    message: 'All Impact data saved successfully',
                    errorDetails: []
                };
            } else {
                // Build error message with details
                let errorMessage = failedTabs.length > 0 
                    ? `Failed to save: ${failedTabs.join(', ')}`
                    : 'Failed to save Impact data';
                
                // Add detailed error messages if available
                if (errorDetails.length > 0) {
                    errorMessage += '. ' + errorDetails.join('. ');
                }
                
                console.log('=== saveAllImpactData FAILED ===', errorMessage);
                return {
                    success: false,
                    failedTabs: failedTabs,
                    message: errorMessage,
                    errorDetails: errorDetails
                };
            }
        } catch (error) {
            console.error('=== saveAllImpactData ERROR ===', error);
            return {
                success: false,
                message: 'Error saving impact data: ' + (error.message || 'Unknown error'),
                failedTabs: ['all'],
                errorDetails: [error.message || 'Unknown error']
            };
        }
    }

    function hasImpactChanges() {
        saveCurrentSystemFormData();
        saveCurrentProcessFormData();
        saveCurrentProductFormData();
        saveCurrentClientFormData();
        saveCurrentGlossaryFormData();
        saveCurrentProjectFormData();
        saveCurrentPolicyFormData();
        saveCurrentInterfaceFormData();
        saveCurrentLegalFormData();
        saveCurrentDatasetFormData();
        saveCurrentAttributeFormData();
        saveCurrentCapabilityFormData();
        saveCurrentBusinessAreaFormData();

        const hasSystemChanges = JSON.stringify(systemRelationships) !== JSON.stringify(originalSystemData);
        const hasProcessChanges = JSON.stringify(processRelationships) !== JSON.stringify(originalProcessData);
        const hasProductChanges = JSON.stringify(productRelationships) !== JSON.stringify(originalProductData);
        const hasClientChanges = JSON.stringify(clientRelationships) !== JSON.stringify(originalClientData);
        const hasGlossaryChanges = JSON.stringify(glossaryRelationships) !== JSON.stringify(originalGlossaryData);
        const hasProjectChanges = JSON.stringify(projectRelationships) !== JSON.stringify(originalProjectData);
        const hasPolicyChanges = JSON.stringify(policyRelationships) !== JSON.stringify(originalPolicyData);
        const hasInterfaceChanges = JSON.stringify(interfaceRelationships) !== JSON.stringify(originalInterfaceData);
        const hasLegalChanges = JSON.stringify(legalRelationships) !== JSON.stringify(originalLegalData);
        const hasDatasetChanges = JSON.stringify(datasetRelationships) !== JSON.stringify(originalDatasetData);
        const hasAttributeChanges = JSON.stringify(attributeRelationships) !== JSON.stringify(originalAttributeData);
        const hasCapabilityChanges = JSON.stringify(capabilityRelationships) !== JSON.stringify(originalCapabilityData);
        const hasBusinessAreaChanges = JSON.stringify(businessAreaRelationships) !== JSON.stringify(originalBusinessAreaData);
        
        const hasChanges = hasSystemChanges || hasProcessChanges || hasProductChanges || hasClientChanges || 
                          hasGlossaryChanges || hasProjectChanges || hasPolicyChanges ||
                          hasInterfaceChanges || hasLegalChanges || hasDatasetChanges || hasAttributeChanges ||
                          hasCapabilityChanges || hasBusinessAreaChanges;
        
        if (hasChanges) {
            console.log('Project Impact Edit: hasImpactChanges detected changes:');
            if (hasSystemChanges) console.log('  - System relationships changed');
            if (hasProcessChanges) console.log('  - Process relationships changed');
            if (hasProductChanges) console.log('  - Product relationships changed');
            if (hasClientChanges) console.log('  - Client relationships changed');
            if (hasGlossaryChanges) console.log('  - Glossary relationships changed');
            if (hasProjectChanges) console.log('  - Project relationships changed');
            if (hasPolicyChanges) console.log('  - Policy relationships changed');
            if (hasInterfaceChanges) console.log('  - Interface relationships changed');
            if (hasLegalChanges) console.log('  - Legal relationships changed');
            if (hasDatasetChanges) console.log('  - Dataset relationships changed');
            if (hasAttributeChanges) console.log('  - Attribute relationships changed');
            if (hasCapabilityChanges) console.log('  - Capability relationships changed');
            if (hasBusinessAreaChanges) console.log('  - Business Area relationships changed');
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
    
    window.addProcessRow = addProcessRow;
    window.deleteProcessRow = deleteProcessRow;
    window.updateProcessOwner = updateProcessOwner;
    
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
    
    window.addCapabilityRow = addCapabilityRow;
    window.deleteCapabilityRow = deleteCapabilityRow;
    
    window.addBusinessAreaRow = addBusinessAreaRow;
    window.deleteBusinessAreaRow = deleteBusinessAreaRow;
    
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


