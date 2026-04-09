// Legal Impact Edit JavaScript - Implementation for Geography sub-tab
console.log('=== LEGAL IMPACT EDIT SCRIPT LOADING ===');

(function() {
    console.log('=== LEGAL IMPACT EDIT SCRIPT LOADED ===');
    
    // Global variables
    let currentLegalId = null;
    let currentLegalSegmentId = null; // segment of the legal entity - geographies dropdown filtered by this
    let geographyRelationships = [];
    let originalGeographyData = [];
    let geographyRelationTypes = [];
    let geographies = [];

    // Initialize Impact tab edit functionality (segmentId optional - when set, geography dropdown is filtered to same segment plus Enterprise)
    function initImpactEdit(legalId, segmentId) {
        console.log('=== INIT IMPACT EDIT CALLED ===');
        console.log('Legal ID:', legalId, 'Segment ID:', segmentId);
        currentLegalId = legalId;
        currentLegalSegmentId = segmentId != null && segmentId !== undefined ? segmentId : null;
        loadImpactData();
    }

    // Load all Impact data
    async function loadImpactData() {
        try {
            console.log('=== LOADING IMPACT DATA START ===');
            
            // Load dropdown data first
            await Promise.all([
                loadGeographyRelationTypes(),
                loadGeographies()
            ]);

            // Load relationship data
            await loadGeographyRelationships();

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
                if (subTabName === 'geography') {
                    targetSubTab = document.getElementById('impactGeographyContent');
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

    // ===== GEOGRAPHY RELATIONSHIPS =====
    
    async function loadGeographyRelationships() {
        try {
            const response = await fetch(`/api/legal-impact/${currentLegalId}/geographies`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            geographyRelationships = Array.isArray(data) ? data : [];
            renderGeographyTable();
            // Update original data AFTER rendering
            originalGeographyData = JSON.parse(JSON.stringify(geographyRelationships));

        } catch (error) {
            console.error('Error loading geography relationships:', error);
            geographyRelationships = [];
            renderGeographyTable();
            // Update original data AFTER rendering
            originalGeographyData = JSON.parse(JSON.stringify(geographyRelationships));
        }
    }

    async function loadGeographyRelationTypes() {
        try {
            const response = await fetch('/api/legal-impact/geography-relation-types', {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            geographyRelationTypes = Array.isArray(data) ? data : [];

        } catch (error) {
            console.error('Error loading geography relation types:', error);
            geographyRelationTypes = [];
        }
    }

    async function loadGeographies() {
        try {
            console.log('=== LOADING GEOGRAPHIES START ===');
            const normalizeGeographyResponse = (data) => {
                if (Array.isArray(data)) {
                    return data;
                }
                if (data && Array.isArray(data.data)) {
                    return data.data;
                }
                if (data && typeof data === 'object') {
                    return [data];
                }
                return [];
            };

            if (currentLegalSegmentId != null && currentLegalSegmentId > 1) {
                const [sameSegmentResponse, enterpriseResponse] = await Promise.all([
                    fetch(`/api/geography/list?segmentId=${encodeURIComponent(currentLegalSegmentId)}`, {
                        method: 'GET',
                        credentials: 'include',
                        headers: { 'Content-Type': 'application/json' }
                    }),
                    fetch('/api/geography/list?segmentId=1', {
                        method: 'GET',
                        credentials: 'include',
                        headers: { 'Content-Type': 'application/json' }
                    })
                ]);

                if (!sameSegmentResponse.ok) throw new Error(`HTTP error! status: ${sameSegmentResponse.status}`);
                if (!enterpriseResponse.ok) throw new Error(`HTTP error! status: ${enterpriseResponse.status}`);

                const [sameSegmentData, enterpriseData] = await Promise.all([
                    sameSegmentResponse.json(),
                    enterpriseResponse.json()
                ]);

                const mergedGeographies = new Map();
                [...normalizeGeographyResponse(enterpriseData), ...normalizeGeographyResponse(sameSegmentData)].forEach(g => {
                    const geographyId = g?.id ?? g?.ID;
                    if (geographyId != null) {
                        mergedGeographies.set(geographyId, g);
                    }
                });
                geographies = Array.from(mergedGeographies.values());
            } else {
                const response = await fetch('/api/geography/list', {
                    method: 'GET',
                    credentials: 'include',
                    headers: { 'Content-Type': 'application/json' }
                });

                if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

                const data = await response.json();
                console.log('Raw geography data received:', data);
                geographies = normalizeGeographyResponse(data);
            }
            
            console.log('Loaded geographies from nested data:', geographies.length);
            
            // Filter out deleted geographies
            geographies = geographies.filter(g => {
                const deleted = g.DeletedDatetime || g.deletedDatetime;
                return !deleted || deleted === '1970-01-01 00:00:00' || deleted === null;
            });
            
            console.log('Final geographies array length:', geographies.length);
            if (geographies.length > 0) {
                console.log('First geography sample:', geographies[0]);
                console.log('Geography field names:', Object.keys(geographies[0]));
            }
            console.log('=== LOADING GEOGRAPHIES END ===');

        } catch (error) {
            console.error('Error loading geographies:', error);
            geographies = [];
        }
    }

    function renderGeographyTable() {
        const tbody = document.querySelector('#geographyImpactTableBody');
        const footer = document.querySelector('#geographyImpactFooter');
        
        if (!tbody) return;
        
        if (geographyRelationships.length === 0) {
            geographyRelationships.push({
                id: 'new-empty',
                relationType: null,
                geographyId: null,
                geographyName: null
            });
        }
        
        let html = '';
        geographyRelationships.forEach((relationship, index) => {
            const rowId = relationship.id || 'new-' + index;
            
            html += `
                <tr data-id="${rowId}">
                    <td>
                        <select class="form-control" data-field="relationType">
                            <option value="">${window.I18n ? window.I18n.t('legalEntity.impact.messages.selectRelationshipType') : 'Select relationship type'}</option>
                            ${geographyRelationTypes.map(rt => 
                                `<option value="${rt.id || rt.ID}" ${relationship.relationType == (rt.id || rt.ID) ? 'selected' : ''}>${rt.primaryname || rt.primaryName || rt.PrimaryName || 'Unnamed Type'}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td>
                        <select class="form-control" data-field="geographyId">
                            <option value="">${window.I18n ? window.I18n.t('legalEntity.impact.messages.selectGeography') : 'Select geography'}</option>
                            ${geographies.map(g => 
                                `<option value="${g.id || g.ID}" ${relationship.geographyId == (g.id || g.ID) ? 'selected' : ''}>${g.primaryName || g.primaryname || g.PrimaryName || g.name || g.Name || 'Unnamed Geography'}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td>
                        <div class="action-buttons">
                            <button type="button" class="btn btn-sm btn-success" onclick="addGeographyRow()" title="${window.I18n ? window.I18n.t('legalEntity.impact.buttons.addRow') : 'Add row'}">
                                <i class="fas fa-plus"></i>
                            </button>
                            <button type="button" class="btn btn-sm btn-danger" onclick="deleteGeographyRow('${rowId}')" title="${window.I18n ? window.I18n.t('legalEntity.impact.buttons.deleteRow') : 'Delete row'}">
                                <i class="fas fa-minus"></i>
                            </button>
                        </div>
                    </td>
                </tr>
            `;
        });
        
        tbody.innerHTML = html;
        if (footer) {
            const recordText = geographyRelationships.length === 1 
                ? (window.I18n ? window.I18n.t('legalEntity.relationships.records', {count: geographyRelationships.length}) : `${geographyRelationships.length} record`)
                : (window.I18n ? window.I18n.t('legalEntity.relationships.recordsPlural', {count: geographyRelationships.length}) : `${geographyRelationships.length} records`);
            footer.textContent = recordText;
        }
    }

    function addGeographyRow() {
        saveCurrentGeographyFormData();
        const newRelationship = {
            id: 'new-' + Date.now(),
            relationType: null,
            geographyId: null,
            geographyName: null
        };
        geographyRelationships.push(newRelationship);
        renderGeographyTable();
    }

    function deleteGeographyRow(id) {
        saveCurrentGeographyFormData();
        
        // Remove from array
        geographyRelationships = geographyRelationships.filter(rel => {
            const relId = rel.id || 'new-' + geographyRelationships.indexOf(rel);
            return String(relId) !== String(id);
        });
        
        // Ensure at least one empty row
        if (geographyRelationships.length === 0) {
            geographyRelationships.push({
                id: 'new-empty',
                relationType: null,
                geographyId: null,
                geographyName: null
            });
        }
        
        renderGeographyTable();
    }

    function saveCurrentGeographyFormData() {
        const rows = document.querySelectorAll('#geographyImpactTableBody tr');
        rows.forEach((row, index) => {
            if (index < geographyRelationships.length) {
                const relationship = geographyRelationships[index];
                const relationTypeSelect = row.querySelector('select[data-field="relationType"]');
                const geographySelect = row.querySelector('select[data-field="geographyId"]');
                
                if (relationTypeSelect) {
                    relationship.relationType = relationTypeSelect.value ? parseInt(relationTypeSelect.value) : null;
                }
                if (geographySelect) {
                    relationship.geographyId = geographySelect.value ? parseInt(geographySelect.value) : null;
                }
            }
        });
    }

    async function saveGeographyRelationships() {
        try {
            saveCurrentGeographyFormData();
            
            // Filter out empty rows
            const validRelationships = geographyRelationships.filter(rel => {
                return rel.geographyId != null && rel.relationType != null;
            });
            
            const requestBody = {
                legalId: currentLegalId,
                relationships: validRelationships.map(rel => ({
                    geographyId: rel.geographyId,
                    relationType: rel.relationType
                }))
            };
            
            const response = await fetch('/api/legal-impact/geographies/save', {
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
                await loadGeographyRelationships();
                const successMsg = window.I18n ? window.I18n.t('legalEntity.messages.geographyRelationshipsSaved') : 'Geography relationships saved successfully';
                return { success: true, message: result.message || successMsg };
            } else {
                const detail = result.error || result.message || '';
                const baseMsg = window.I18n ? window.I18n.t('legalEntity.messages.failedToSaveGeography') : 'Failed to save geography relationships';
                const errorMsg = detail ? (baseMsg + ': ' + detail) : baseMsg;
                return { success: false, message: errorMsg };
            }

        } catch (error) {
            console.error('Error saving geography relationships:', error);
            if (error.message && error.message.includes('Authentication failed')) {
                const errorMsg = window.I18n ? window.I18n.t('legalEntity.errors.authenticationFailed') : 'Authentication failed - session may have expired. Please refresh the page or log in again.';
                alert(errorMsg);
            }
            throw error;
        }
    }

    // ===== CHANGE DETECTION =====
    
    function hasImpactChanges() {
        saveCurrentGeographyFormData();
        
        // Compare current with original
        const currentData = geographyRelationships
            .filter(rel => rel.geographyId != null && rel.relationType != null)
            .map(rel => ({
                geographyId: rel.geographyId,
                relationType: rel.relationType
            }))
            .sort((a, b) => {
                if (a.geographyId !== b.geographyId) return a.geographyId - b.geographyId;
                return a.relationType - b.relationType;
            });
        
        const originalData = originalGeographyData
            .filter(rel => rel.geographyId != null && rel.relationType != null)
            .map(rel => ({
                geographyId: rel.geographyId,
                relationType: rel.relationType
            }))
            .sort((a, b) => {
                if (a.geographyId !== b.geographyId) return a.geographyId - b.geographyId;
                return a.relationType - b.relationType;
            });
        
        if (currentData.length !== originalData.length) {
            return true;
        }
        
        for (let i = 0; i < currentData.length; i++) {
            if (currentData[i].geographyId !== originalData[i].geographyId ||
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
            const geographyResult = await saveGeographyRelationships();
            results.geography = geographyResult;
        } catch (error) {
            results.geography = { success: false, message: error.message };
        }
        
        return results;
    }

    // Export functions to global scope
    window.initImpactEdit = initImpactEdit;
    window.addGeographyRow = addGeographyRow;
    window.deleteGeographyRow = deleteGeographyRow;
    window.saveAllImpactData = saveAllImpactData;
    window.hasImpactChanges = hasImpactChanges;
    
    console.log('Legal Impact Edit functions exported to window');
})();

