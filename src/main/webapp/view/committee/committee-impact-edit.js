// Committee Impact Edit JavaScript - Implementation for Capability sub-tab
console.log('=== COMMITTEE IMPACT EDIT SCRIPT LOADING ===');

(function() {
    console.log('=== COMMITTEE IMPACT EDIT SCRIPT LOADED ===');

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
            '#committeeSegment',
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
        const oid = parseInt(currentCommitteeId, 10);
        if (Number.isInteger(oid) && oid > 0) {
            const sep2 = result.includes('?') ? '&' : '?';
            result = `${result}${sep2}sourceObjectId=${oid}&sourceObjectType=Committee`;
        }
        return result;
    }
    
    // Global variables
    let currentCommitteeId = null;
    let capabilityRelationships = [];
    let originalCapabilityData = [];
    let capabilityRelationTypes = [];
    let capabilities = [];

    // Initialize Impact tab edit functionality
    function initImpactEdit(committeeId) {
        console.log('=== INIT IMPACT EDIT CALLED ===');
        console.log('Committee ID:', committeeId);
        currentCommitteeId = committeeId;
        loadImpactData();
    }

    // Load all Impact data
    async function loadImpactData() {
        try {
            console.log('=== LOADING IMPACT DATA START ===');
            
            // Load dropdown data first
            await Promise.all([
                loadCapabilityRelationTypes(),
                loadCapabilities()
            ]);

            // Load relationship data
            await loadCapabilityRelationships();

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
                if (subTabName === 'capability') {
                    targetSubTab = document.getElementById('impactCapabilityContent');
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

    // ===== CAPABILITY RELATIONSHIPS =====
    
    async function loadCapabilityRelationships() {
        try {
            const response = await fetch(`/api/committee-impact/${currentCommitteeId}/capabilities`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            capabilityRelationships = Array.isArray(data) ? data : [];
            renderCapabilityTable();
            // Update original data AFTER rendering
            originalCapabilityData = JSON.parse(JSON.stringify(capabilityRelationships));

        } catch (error) {
            console.error('Error loading capability relationships:', error);
            capabilityRelationships = [];
            renderCapabilityTable();
            // Update original data AFTER rendering
            originalCapabilityData = JSON.parse(JSON.stringify(capabilityRelationships));
        }
    }

    async function loadCapabilityRelationTypes() {
        try {
            const response = await fetch('/api/committee-impact/capability-relation-types', {
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
            const response = await fetch(withImpactSegment('/api/capabilities/'), {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            
            // Handle nested data structure
            if (data && data.success && Array.isArray(data.data)) {
                capabilities = data.data;
            } else if (Array.isArray(data)) {
                capabilities = data;
            } else {
                capabilities = [];
            }

        } catch (error) {
            console.error('Error loading capabilities:', error);
            capabilities = [];
        }
    }

    function renderCapabilityTable() {
        const tbody = document.querySelector('#capabilityImpactTableBody');
        const footer = document.querySelector('#capabilityImpactFooter');
        
        if (!tbody) return;
        
        if (capabilityRelationships.length === 0) {
            capabilityRelationships.push({
                id: 'new-empty',
                relationType: null,
                capabilityId: null,
                capabilityName: null,
                capabilityOwnerName: null
            });
        }
        
        let html = '';
        capabilityRelationships.forEach((relationship, index) => {
            const rowId = relationship.id || 'new-' + index;
            // Show owner only if capability is selected
            const ownerDisplay = relationship.capabilityId ? (relationship.capabilityOwnerName || 'No owner') : '';
            
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
                        <select class="form-control" data-field="capabilityId" onchange="updateCapabilityOwner(this)">
                            <option value="">Select capability</option>
                            ${capabilities.map(c => 
                                `<option value="${c.id || c.ID}" ${relationship.capabilityId == (c.id || c.ID) ? 'selected' : ''}>${c.primaryName || c.primaryname || c.PrimaryName || c.name || c.Name || 'Unnamed Capability'}</option>`
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
            capabilityName: null,
            capabilityOwnerName: null
        };
        capabilityRelationships.push(newRelationship);
        renderCapabilityTable();
    }

    function deleteCapabilityRow(id) {
        saveCurrentCapabilityFormData();
        
        // Remove from array
        capabilityRelationships = capabilityRelationships.filter(rel => {
            const relId = rel.id || 'new-' + capabilityRelationships.indexOf(rel);
            return String(relId) !== String(id);
        });
        
        // Ensure at least one empty row
        if (capabilityRelationships.length === 0) {
            capabilityRelationships.push({
                id: 'new-empty',
                relationType: null,
                capabilityId: null,
                capabilityName: null,
                capabilityOwnerName: null
            });
        }
        
        renderCapabilityTable();
    }

    function saveCurrentCapabilityFormData() {
        const rows = document.querySelectorAll('#capabilityImpactTableBody tr');
        rows.forEach((row, index) => {
            if (index < capabilityRelationships.length) {
                const relationship = capabilityRelationships[index];
                const relationTypeSelect = row.querySelector('select[data-field="relationType"]');
                const capabilitySelect = row.querySelector('select[data-field="capabilityId"]');
                
                if (relationTypeSelect) {
                    relationship.relationType = relationTypeSelect.value ? parseInt(relationTypeSelect.value) : null;
                }
                if (capabilitySelect) {
                    relationship.capabilityId = capabilitySelect.value ? parseInt(capabilitySelect.value) : null;
                }
            }
        });
    }

    async function updateCapabilityOwner(selectElement) {
        const capabilityId = selectElement.value;
        const row = selectElement.closest('tr');
        
        if (!capabilityId) {
            return;
        }
        
        try {
            const response = await fetch(`/api/committee-impact/capability-owner/${capabilityId}`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            const ownerName = data.capabilityOwnerName || data.ownerName || 'No owner';
            
            // Update the relationship data
            const rowId = row.getAttribute('data-id');
            const relationship = capabilityRelationships.find(rel => {
                const relId = rel.id || 'new-' + capabilityRelationships.indexOf(rel);
                return String(relId) === String(rowId);
            });
            
            if (relationship) {
                relationship.capabilityOwnerName = ownerName;
            }

        } catch (error) {
            console.error('Error fetching capability owner:', error);
        }
    }

    async function saveCapabilityRelationships() {
        try {
            saveCurrentCapabilityFormData();
            
            // Filter out empty rows
            const validRelationships = capabilityRelationships.filter(rel => {
                return rel.capabilityId != null && rel.relationType != null;
            });
            
            const requestBody = {
                committeeId: currentCommitteeId,
                relationships: validRelationships.map(rel => ({
                    capabilityId: rel.capabilityId,
                    relationType: rel.relationType
                }))
            };
            
            const response = await fetch('/api/committee-impact/capabilities/save', {
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
                await loadCapabilityRelationships();
                return { success: true, message: result.message || 'Capability relationships saved successfully' };
            } else {
                return { success: false, message: result.message || 'Failed to save capability relationships' };
            }

        } catch (error) {
            console.error('Error saving capability relationships:', error);
            if (error.message && error.message.includes('Authentication failed')) {
                alert('Authentication failed - session may have expired. Please refresh the page or log in again.');
            }
            throw error;
        }
    }

    // ===== CHANGE DETECTION =====
    
    function hasImpactChanges() {
        saveCurrentCapabilityFormData();
        
        // Compare current with original
        const currentData = capabilityRelationships
            .filter(rel => rel.capabilityId != null && rel.relationType != null)
            .map(rel => ({
                capabilityId: rel.capabilityId,
                relationType: rel.relationType
            }))
            .sort((a, b) => {
                if (a.capabilityId !== b.capabilityId) return a.capabilityId - b.capabilityId;
                return a.relationType - b.relationType;
            });
        
        const originalData = originalCapabilityData
            .filter(rel => rel.capabilityId != null && rel.relationType != null)
            .map(rel => ({
                capabilityId: rel.capabilityId,
                relationType: rel.relationType
            }))
            .sort((a, b) => {
                if (a.capabilityId !== b.capabilityId) return a.capabilityId - b.capabilityId;
                return a.relationType - b.relationType;
            });
        
        if (currentData.length !== originalData.length) {
            return true;
        }
        
        for (let i = 0; i < currentData.length; i++) {
            if (currentData[i].capabilityId !== originalData[i].capabilityId ||
                currentData[i].relationType !== originalData[i].relationType) {
                return true;
            }
        }
        
        return false;
    }

    // ===== SAVE ALL IMPACT DATA =====
    
    async function saveAllImpactData() {
        const results = {};
        
        try {
            const capabilityResult = await saveCapabilityRelationships();
            results.capability = capabilityResult;
        } catch (error) {
            results.capability = { success: false, message: error.message };
        }
        
        return results;
    }

    // Export functions to global scope
    window.initImpactEdit = initImpactEdit;
    window.addCapabilityRow = addCapabilityRow;
    window.deleteCapabilityRow = deleteCapabilityRow;
    window.updateCapabilityOwner = updateCapabilityOwner;
    window.saveAllImpactData = saveAllImpactData;
    window.hasImpactChanges = hasImpactChanges;
    
    console.log('Committee Impact Edit functions exported to window');
})();

