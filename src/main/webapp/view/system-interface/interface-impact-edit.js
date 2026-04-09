// Interface Impact Edit JavaScript - Implementation for Process sub-tab
console.log('=== INTERFACE IMPACT EDIT SCRIPT LOADING ===');

(function() {
    console.log('=== INTERFACE IMPACT EDIT SCRIPT LOADED ===');

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
            '#interfaceSegment',
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
        const tId = parseInt(currentTargetSystemId, 10);
        const useSystemFallback = Number.isInteger(tId) && tId > 0;
        const sourceId   = useSystemFallback ? tId : parseInt(currentInterfaceId, 10);
        const sourceType = useSystemFallback ? 'System' : 'Interface';
        if (Number.isInteger(sourceId) && sourceId > 0) {
            const sep2 = result.includes('?') ? '&' : '?';
            result = `${result}${sep2}sourceObjectId=${sourceId}&sourceObjectType=${sourceType}`;
        }
        return result;
    }

    function showRelationshipError(message) {
        const existing = document.getElementById('impact-relationship-error');
        if (existing) existing.remove();

        const el = document.createElement('div');
        el.id = 'impact-relationship-error';
        el.style.cssText = `
            position: fixed;
            top: 80px;
            left: 50%;
            transform: translateX(-50%);
            background-color: #dc2626;
            color: #fff;
            padding: 12px 20px;
            border-radius: 8px;
            z-index: 10000;
            box-shadow: 0 8px 24px rgba(0,0,0,0.18);
            max-width: 760px;
            font-size: 14px;
            line-height: 1.4;
            text-align: center;
        `;
        el.textContent = message;
        document.body.appendChild(el);

        setTimeout(() => {
            el.style.transition = 'opacity 0.4s ease';
            el.style.opacity = '0';
            setTimeout(() => el.remove(), 400);
        }, 6500);
    }
    
    // Global variables
    let currentInterfaceId = null;
    let currentTargetSystemId = null;
    let processRelationships = [];
    let originalProcessData = [];
    let processRelationTypes = [];
    let processes = [];

    // Initialize Impact tab edit functionality
    function initImpactEdit(interfaceId) {
        console.log('=== INIT IMPACT EDIT CALLED ===');
        console.log('Interface ID:', interfaceId);
        currentInterfaceId = interfaceId;
        const targetInput  = document.getElementById('targetShortName');
        const targetSelect = document.getElementById('targetSystem');
        const rawTargetId  = targetInput?.dataset?.systemId || targetSelect?.value || '';
        const parsedTargetId = parseInt(rawTargetId, 10);
        currentTargetSystemId = Number.isInteger(parsedTargetId) && parsedTargetId > 0
            ? parsedTargetId
            : null;
        loadImpactData();
    }

    // Load all Impact data
    async function loadImpactData() {
        try {
            console.log('=== LOADING IMPACT DATA START ===');
            
            // Load dropdown data first
            await Promise.all([
                loadProcessRelationTypes(),
                loadProcesses()
            ]);

            // Load relationship data
            await loadProcessRelationships();

            // Setup sub-tabs
            setupImpactSubTabs();
            console.log('=== LOADING IMPACT DATA END ===');

        } catch (error) {
            console.error('Error loading Impact data:', error);
            showRelationshipError('Unable to load impact relationships right now. Please refresh and try again.');
        }
    }

    // Setup Impact sub-tabs
    function setupImpactSubTabs() {
        const subTabs = document.querySelectorAll('#impact .sub-tab');
        
        // Get current active tab from parent page
        function getCurrentTab() {
            const activeTab = document.querySelector('.tab.active');
            return activeTab ? activeTab.getAttribute('data-tab') : 'impact';
        }
        
        subTabs.forEach(function(subTab) {
            subTab.addEventListener('click', function(e) {
                e.preventDefault();
                
                // Remove active class from all sub-tabs
                subTabs.forEach(function(t) { t.classList.remove('active'); });
                this.classList.add('active');
                
                const subTabName = this.getAttribute('data-sub-tab');
                
                // Update URL with subtab
                if (window.updateInterfaceTabURL) {
                    const currentTab = getCurrentTab();
                    window.updateInterfaceTabURL(currentTab, subTabName);
                }
                
                // Hide all sub-tab contents
                const subTabContents = document.querySelectorAll('#impact .sub-tab-content');
                subTabContents.forEach(function(content) {
                    content.classList.remove('active');
                    content.style.display = 'none';
                });
                
                // Show selected sub-tab content
                let targetSubTab;
                if (subTabName === 'process') {
                    targetSubTab = document.getElementById('impactProcessContent');
                }
                
                if (targetSubTab) {
                    targetSubTab.classList.add('active');
                    targetSubTab.style.display = 'block';
                }
            });
        });
        
        // Check URL for subtab and activate it, otherwise activate first sub-tab by default
        const urlParams = new URLSearchParams(window.location.search);
        const urlSubTab = urlParams.get('subtab');
        if (urlSubTab) {
            const urlSubTabElement = document.querySelector(`[data-sub-tab="${urlSubTab}"]`);
            if (urlSubTabElement) {
                urlSubTabElement.click();
            } else if (subTabs.length > 0) {
                subTabs[0].click();
            }
        } else if (subTabs.length > 0) {
            subTabs[0].click();
        }
    }

    // ===== PROCESS RELATIONSHIPS =====
    
    async function loadProcessRelationships() {
        try {
            const response = await fetch(`/api/interface-impact/${currentInterfaceId}/processes`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            processRelationships = Array.isArray(data) ? data : [];
            renderProcessTable();
            // Update original data AFTER rendering
            originalProcessData = JSON.parse(JSON.stringify(processRelationships));

        } catch (error) {
            console.error('Error loading process relationships:', error);
            processRelationships = [];
            renderProcessTable();
            // Update original data AFTER rendering
            originalProcessData = JSON.parse(JSON.stringify(processRelationships));
            showRelationshipError('Unable to load existing process relationships. Please refresh and try again.');
        }
    }

    async function loadProcessRelationTypes() {
        try {
            const response = await fetch('/api/interface-impact/process-relation-types', {
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
            showRelationshipError('Unable to load relationship types. Please refresh and try again.');
        }
    }

    async function loadProcesses() {
        try {
            const response = await fetch(withImpactSegment('/api/process/'), {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });

            if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

            const data = await response.json();
            
            // Handle nested data structure
            if (data && data.success && Array.isArray(data.data)) {
                processes = data.data;
            } else if (Array.isArray(data)) {
                processes = data;
            } else {
                processes = [];
            }

        } catch (error) {
            console.error('Error loading processes:', error);
            processes = [];
            showRelationshipError('Unable to load process options for relationships. Please refresh and try again.');
        }
    }

    function renderProcessTable() {
        const tbody = document.querySelector('#processImpactTableBody');
        const footer = document.querySelector('#processImpactFooter');
        
        if (!tbody) return;
        
        if (processRelationships.length === 0) {
            processRelationships.push({
                id: 'new-empty',
                relationType: null,
                processId: null,
                processName: null
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
                        <select class="form-control" data-field="processId">
                            <option value="">Select process</option>
                            ${processes.map(p => 
                                `<option value="${p.id || p.ID}" ${relationship.processId == (p.id || p.ID) ? 'selected' : ''}>${p.primaryname || p.primaryName || p.PrimaryName || p.name || p.Name || 'Unnamed Process'}</option>`
                            ).join('')}
                        </select>
                    </td>
                    <td style="text-align: center;">
                        <div class="action-buttons">
                            <button type="button" class="btn btn-sm" onclick="addProcessRow()" title="${window.I18n?.t('impactEdit.buttons.addRow') || 'Add row'}">
                                <i class="fas fa-plus"></i>
                            </button>
                            <button type="button" class="btn btn-sm" onclick="deleteProcessRow('${rowId}')" title="${window.I18n?.t('impactEdit.buttons.deleteRow') || 'Delete row'}">
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

    function escapeHtml(text) {
        if (text == null) return '';
        const map = {
            '&': '&amp;',
            '<': '&lt;',
            '>': '&gt;',
            '"': '&quot;',
            "'": '&#039;'
        };
        return String(text).replace(/[&<>"']/g, m => map[m]);
    }

    function addProcessRow() {
        saveCurrentProcessFormData();
        const newRelationship = {
            id: 'new-' + Date.now(),
            relationType: null,
            processId: null,
            processName: null
        };
        processRelationships.push(newRelationship);
        renderProcessTable();
    }

    function deleteProcessRow(id) {
        saveCurrentProcessFormData();
        
        // Remove from array
        processRelationships = processRelationships.filter(rel => {
            const relId = rel.id || 'new-' + processRelationships.indexOf(rel);
            return String(relId) !== String(id);
        });
        
        // Ensure at least one empty row
        if (processRelationships.length === 0) {
            processRelationships.push({
                id: 'new-empty',
                relationType: null,
                processId: null,
                processName: null
            });
        }
        
        renderProcessTable();
    }

    function saveCurrentProcessFormData() {
        const rows = document.querySelectorAll('#processImpactTableBody tr');
        rows.forEach((row, index) => {
            if (index < processRelationships.length) {
                const relationship = processRelationships[index];
                const relationTypeSelect = row.querySelector('select[data-field="relationType"]');
                const processSelect = row.querySelector('select[data-field="processId"]');
                
                if (relationTypeSelect) {
                    relationship.relationType = relationTypeSelect.value ? parseInt(relationTypeSelect.value) : null;
                }
                if (processSelect) {
                    relationship.processId = processSelect.value ? parseInt(processSelect.value) : null;
                }
            }
        });
    }


    async function saveProcessRelationships() {
        try {
            saveCurrentProcessFormData();
            
            // Filter out empty rows
            const validRelationships = processRelationships.filter(rel => {
                return rel.processId != null && rel.relationType != null;
            });
            
            const requestBody = {
                interfaceId: currentInterfaceId,
                relationships: validRelationships.map(rel => ({
                    processId: rel.processId,
                    relationType: rel.relationType
                }))
            };
            
            const response = await fetch('/api/interface-impact/processes/save', {
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
                await loadProcessRelationships();
                return { success: true, message: result.message || 'Process relationships saved successfully' };
            } else {
                return { success: false, message: result.message || 'Failed to save process relationships' };
            }

        } catch (error) {
            console.error('Error saving process relationships:', error);
            if (error.message && error.message.includes('Authentication failed')) {
                alert('Authentication failed - session may have expired. Please refresh the page or log in again.');
            } else {
                showRelationshipError(`Failed to save process relationships. ${error.message || 'Please try again.'}`);
            }
            throw error;
        }
    }

    // ===== CHANGE DETECTION =====
    
    function hasImpactChanges() {
        saveCurrentProcessFormData();
        
        // Compare current with original
        const currentData = processRelationships
            .filter(rel => rel.processId != null && rel.relationType != null)
            .map(rel => ({
                processId: rel.processId,
                relationType: rel.relationType
            }))
            .sort((a, b) => {
                if (a.processId !== b.processId) return a.processId - b.processId;
                return a.relationType - b.relationType;
            });
        
        const originalData = originalProcessData
            .filter(rel => rel.processId != null && rel.relationType != null)
            .map(rel => ({
                processId: rel.processId,
                relationType: rel.relationType
            }))
            .sort((a, b) => {
                if (a.processId !== b.processId) return a.processId - b.processId;
                return a.relationType - b.relationType;
            });
        
        if (currentData.length !== originalData.length) {
            return true;
        }
        
        for (let i = 0; i < currentData.length; i++) {
            if (currentData[i].processId !== originalData[i].processId ||
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
            const processResult = await saveProcessRelationships();
            results.process = processResult;
        } catch (error) {
            results.process = { success: false, message: error.message };
        }
        
        return results;
    }

    // Export functions to global scope
    window.initImpactEdit = initImpactEdit;
            window.addProcessRow = addProcessRow;
            window.deleteProcessRow = deleteProcessRow;
            window.saveAllImpactData = saveAllImpactData;
            window.hasImpactChanges = hasImpactChanges;
    
    console.log('Interface Impact Edit functions exported to window');
})();

