// Process Edit Page JavaScript
let segmentField = null; // Segment field component reference
let editViewMode = 'original'; // 'original' | 'changes' (under active CR, edit should load nobject_id data)
let originalProcessSegmentId = null; // segment loaded from server; used to detect segment change

function normalizeProcessSegmentId(value) {
    if (value == null || value === '') return null;
    const n = parseInt(value, 10);
    return Number.isInteger(n) ? n : null;
}

document.addEventListener('DOMContentLoaded', async function() {
    console.log('Initializing process edit page...');
    
    const id = parseId();
    if (!id) {
        console.log('No process ID — initializing create mode');
        window.currentLockManager = null;
        await initializePage();
        return;
    }
    
    // Initialize lock manager
    const lockManager = new window.LockManager('process', id);
    window.currentLockManager = lockManager;
    
    // Setup auto-release on page unload
    lockManager.setupBeforeUnload();
    
    // Check lock status
    const lockStatus = await lockManager.checkLockStatus();
    const status = lockStatus?.status || 'no_lock';
    
    // Handle lock conflicts
    if (status === 'locked_by_other') {
        const lockedBy = lockStatus.lockedByName || 'another user';
        alert(`This process is currently locked by ${lockedBy}. Please try again later.`);
        window.location.href = `/view/process/${id}`;
        return;
    } else if (status === 'permanently_locked') {
        const isSuperAdmin = await lockManager.checkIsSuperAdmin();
        if (!isSuperAdmin) {
            const lockedBy = lockStatus.lockedByName || 'an administrator';
            alert(`This process has a permanent lock by ${lockedBy}. Only administrators can edit it.`);
            window.location.href = `/view/process/${id}`;
            return;
        }
    }
    
    // Acquire lock
    const lockResult = await lockManager.acquireLock(false);
    if (!lockResult || !lockResult.success) {
        // Check if we have details about who locked it
        if (lockResult && lockResult.lockedBy) {
            const lockedBy = lockResult.lockedBy;
            alert(`The object is currently locked by ${lockedBy}. Try again later.`);
        } else {
            alert('Failed to acquire lock. Please try again.');
        }
        window.location.href = `/view/process/${id}`;
        return;
    }
    
    // Update global lock count if available
    if (window.globalLockUI) {
        await window.globalLockUI.updateLockCount();
    }
    
    console.log('Initializing process edit page for ID:', id);
    
    // Initialize the page
    initializePage();
    
});

async function determineEditViewMode(processId) {
    try {
        const res = await fetch(`/api/pending-changes/status/Process/${processId}`, {
            method: 'GET',
            credentials: 'include',
            headers: { 'Content-Type': 'application/json' }
        });
        if (!res.ok) return 'original';
        const status = await res.json();
        return status?.underRevision ? 'changes' : 'original';
    } catch (e) {
        console.warn('[Process Edit] Failed to determine pending-changes status:', e);
        return 'original';
    }
}

function parseId() {
    console.log('Parsing ID from URL:', window.location.href);
    
    // First try URL params (for separate edit pages)
    const urlParams = new URLSearchParams(window.location.search);
    const id = parseInt(urlParams.get('id'), 10);
    if (!Number.isNaN(id)) return id;
    
    // Fallback to path-based ID (for main view pages)
    const parts = window.location.pathname.split('/').filter(Boolean);
    const idx = parts.indexOf('process-edit.html');
    if (idx === -1 || parts.length < idx + 2) return null;
    const pathId = parseInt(parts[idx + 1], 10);
    return Number.isNaN(pathId) ? null : pathId;
}

function populateSelectSimple(selectId, list, getLabel) {
    const el = document.getElementById(selectId);
    if (!el) return;
    
    el.innerHTML = '';
    (Array.isArray(list) ? list : []).forEach(item => {
        const op = document.createElement('option');
        op.value = String(item.ID || item.id);
        op.textContent = getLabel ? getLabel(item) : (item.PrimaryName || item.primaryname || item.name || '');
        el.appendChild(op);
    });
}

async function populateStepTypeOptions() {
    console.log('=== POPULATING STEP TYPE OPTIONS ===');
    const stepTypeSelect = document.getElementById('stepType');
    console.log('Step type select element found:', !!stepTypeSelect);
    if (!stepTypeSelect) {
        console.error('Step type select element not found!');
        return;
    }
    
    // Clear existing options
    stepTypeSelect.innerHTML = '<option value="">Select step type</option>';
    console.log('Cleared existing step type options');
    
    try {
        // Load step types from API
        const stepTypesResponse = await window.BUDG_API_SERVICE.getProcessStepTypeList();
        console.log('Step types response:', stepTypesResponse);
        
        let stepTypes = [];
        if (stepTypesResponse && stepTypesResponse.success && stepTypesResponse.data) {
            stepTypes = stepTypesResponse.data;
        } else if (Array.isArray(stepTypesResponse)) {
            stepTypes = stepTypesResponse;
        }
        
        console.log('Step types loaded:', stepTypes);
        
        stepTypes.forEach((stepType, index) => {
            const option = document.createElement('option');
            option.value = String(stepType.id || stepType.ID);
            option.textContent = stepType.primaryName || stepType.primaryname || stepType.PrimaryName || stepType.name || stepType.Name || String(stepType.id || stepType.ID);
            stepTypeSelect.appendChild(option);
            console.log(`Added step type option ${index}:`, {
                value: option.value,
                text: option.textContent,
                originalData: stepType
            });
        });
        
        console.log('Step type options populated successfully');
    } catch (error) {
        console.error('Error loading step types:', error);
        
        // Fallback to predefined list
        const fallbackStepTypes = [
            { value: '1', label: 'Start' },
            { value: '2', label: 'Data Entry' },
            { value: '3', label: 'Validation' },
            { value: '4', label: 'Manager Review' },
            { value: '5', label: 'Finance Approval' },
            { value: '6', label: 'Compliance Check' },
            { value: '7', label: 'Execution' }
        ];
        
        fallbackStepTypes.forEach(stepType => {
            const option = document.createElement('option');
            option.value = stepType.value;
            option.textContent = stepType.label;
            stepTypeSelect.appendChild(option);
        });
    }
}

async function loadProcessEditLookups() {
    console.log('=== LOADING PROCESS EDIT LOOKUPS ===');
    console.log('Loading process edit lookups...');
    
    // Wait for API service to be available
    if (!window.BUDG_API_SERVICE) {
        await new Promise(resolve => {
            const checkApi = () => {
                if (window.BUDG_API_SERVICE) {
                    resolve();
                } else {
                    setTimeout(checkApi, 100);
                }
            };
            checkApi();
        });
    }
    
    try {
        // Load lookups with individual error handling
        const [statuses, lifecycle, viewing, processTypes, durationType, processClass, processAutomation] = await Promise.allSettled([
            window.BUDG_API_SERVICE.getStatusList(),
            window.BUDG_API_SERVICE.getProcessLifecycleList(),
            window.BUDG_API_SERVICE.getViewingList(),
            window.BUDG_API_SERVICE.getProcessTypeList(),
            window.BUDG_API_SERVICE.getDurationTypeList().catch(() => []),
            window.BUDG_API_SERVICE.getProcessClassList(),
            window.BUDG_API_SERVICE.getProcessAutomationList()
        ]);
        
        // Populate dropdowns with safe access
        if (statuses.status === 'fulfilled') {
            populateSelectSimple('budgStatus', (Array.isArray(statuses.value?.data) ? statuses.value.data : statuses.value), x => x.name);
        }
        if (lifecycle.status === 'fulfilled') {
            console.log('Process lifecycle data loaded:', lifecycle.value);
            console.log('Process lifecycle data type:', typeof lifecycle.value);
            console.log('Process lifecycle data length:', lifecycle.value?.length);
            console.log('Process lifecycle data sample:', lifecycle.value?.[0]);
            console.log('Process lifecycle data keys:', lifecycle.value?.[0] ? Object.keys(lifecycle.value[0]) : 'No data');
            console.log('Process lifecycle data sample primaryname:', lifecycle.value?.[0]?.primaryname);
            console.log('Process lifecycle data sample PrimaryName:', lifecycle.value?.[0]?.PrimaryName);
            console.log('Process lifecycle data sample name:', lifecycle.value?.[0]?.name);
            console.log('Process lifecycle data sample id:', lifecycle.value?.[0]?.id);
            console.log('Process lifecycle data sample ID:', lifecycle.value?.[0]?.ID);
            console.log('Process lifecycle data sample description:', lifecycle.value?.[0]?.description);
            console.log('Process lifecycle data sample Description:', lifecycle.value?.[0]?.Description);
            console.log('Process lifecycle data sample lastupdatedatetime:', lifecycle.value?.[0]?.lastupdatedatetime);
            console.log('Process lifecycle data sample LastUpdateDatetime:', lifecycle.value?.[0]?.LastUpdateDatetime);
            populateSelectSimple('lifecycle', lifecycle.value, x => x.PrimaryName || x.primaryname || x.name);
        } else {
            console.log('Process lifecycle loading failed:', lifecycle);
        }
        if (viewing.status === 'fulfilled') {
            populateSelectSimple('budgViewing', viewing.value, x => x.name);
        }
        if (processTypes.status === 'fulfilled') {
            populateSelectSimple('processType', processTypes.value, x => x.PrimaryName || x.primaryname || x.name);
        }
        // Load step types from API
        console.log('Loading step types from API...');
        await populateStepTypeOptions();
        console.log('Step types loaded successfully');
        
        // Verify step type options are loaded
        const stepTypeSelect = document.getElementById('stepType');
        if (stepTypeSelect) {
            console.log('Final step type options count:', stepTypeSelect.options.length);
            console.log('Final step type options:', Array.from(stepTypeSelect.options).map(opt => ({value: opt.value, text: opt.textContent})));
        }
        if (durationType.status === 'fulfilled' && durationType.value.length > 0) {
            console.log('Duration type data loaded:', durationType.value);
            populateSelectSimple('durationType', durationType.value, x => x.PrimaryName || x.primaryname || x.name);
        } else {
            console.log('Duration type loading failed or empty:', durationType);
        }
        if (processClass.status === 'fulfilled') {
            populateSelectSimple('classification', processClass.value, x => x.PrimaryName || x.primaryname || x.name);
        }
        if (processAutomation.status === 'fulfilled') {
            populateSelectSimple('automation', processAutomation.value, x => x.PrimaryName || x.primaryname || x.name);
        }
        
        // Apply DFCR locked fields for Process facet (existing process only)
        // NOTE: This is an EDIT page, so we pass isEditPage: true to NOT lock fields
        // Locked fields only apply to CREATE pages for new objects
        const processIdForDfcr = parseId();
        if (processIdForDfcr && window.DFCRUtils) {
            try {
                await window.DFCRUtils.applyLockedFields('Process', {
                    status: '#budgStatus',
                    lifecycle: '#lifecycle'
                }, { isEditPage: true, objectId: processIdForDfcr });
                console.log('DFCR locked fields check done for Process edit page (objectId:', processIdForDfcr, ')');
                
                // Check if edit workflow is enabled and show Save & Submit button
                const dfcrInfo = await window.DFCRUtils.getInfo('Process', processIdForDfcr);
                console.log('DFCR Info for Process edit:', dfcrInfo);
                
                // Show Save & Submit button if workflow is enabled OR if object is under revision
                let isUnderRevision = false;
                try {
                    const statusRes = await fetch(`/api/pending-changes/status/Process/${processIdForDfcr}`, {
                        method: 'GET',
                        credentials: 'include'
                    });
                    if (statusRes.ok) {
                        const statusData = await statusRes.json();
                        isUnderRevision = statusData.underRevision === true;
                        console.log('Process under revision:', isUnderRevision);
                    }
                } catch (e) {
                    console.warn('Could not check revision status:', e);
                }
                
                if ((dfcrInfo.editWorkflowEnabled && !dfcrInfo.adminBypassEnabled) || isUnderRevision) {
                    const saveSubmitBtn = document.getElementById('saveAndSubmitBtn');
                    if (saveSubmitBtn) {
                        saveSubmitBtn.style.display = 'inline-block';
                        console.log('Save & Submit button shown for edit workflow');
                    }
                }
            } catch (dfcrError) {
                console.warn('Error applying DFCR locked fields:', dfcrError);
            }
        }
        
        console.log('Process edit lookups loaded successfully');
    } catch (error) {
        console.error('Error loading process edit lookups:', error);
    }
}

async function loadProcess(id, viewMode = 'original') {
    console.log('=== LOADING PROCESS ===');
    console.log('Loading process with ID:', id);
    
    // Wait for API service to be available
    if (!window.BUDG_API_SERVICE) {
        await new Promise(resolve => {
            const checkApi = () => {
                if (window.BUDG_API_SERVICE) {
                    resolve();
                } else {
                    setTimeout(checkApi, 100);
                }
            };
            checkApi();
        });
    }
    
    try {
        const response = await window.BUDG_API_SERVICE.getProcessById(id, viewMode === 'changes' ? 'changes' : null);
        console.log('=== LOADED PROCESS DATA ===');
        console.log('API response:', response);
        console.log('Response type:', typeof response);
        console.log('Response keys:', Object.keys(response || {}));
        
        // Handle both direct data and wrapped response
        const process = response?.data || response;
        console.log('Process data after unwrapping:', process);
        console.log('Process data keys:', Object.keys(process || {}));
        
        if (process) {
            console.log('Calling populateForm...');
            populateForm(process);
            updateTitle(process);
            loadParentProcess(process);
            // Load predecessors
            await loadPredecessors(id);
        } else {
            console.error('No process data found in response');
        }
    } catch (error) {
        console.error('Error loading process:', error);
    }
}

// Load predecessors for the process
let predecessorRelationships = [];
let predecessorRelationTypes = [];
let predecessorProcesses = [];
let processTypesMap = new Map(); // Map of type ID to type name

async function loadPredecessors(processId) {
    try {
        console.log('Loading predecessors for process:', processId);
        const tbody = document.getElementById('predecessorsTableBody');
        const footer = document.getElementById('predecessorsFooter');
        
        if (!tbody || !footer) {
            console.warn('Predecessors table elements not found');
            return;
        }
        
        // Load relation types, processes, process types, and relationships in parallel
        const [relationTypesRes, processesRes, processTypesRes, relationshipsRes] = await Promise.all([
            fetch('/api/process-impact/process-relation-types', {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            }),
            fetch('/api/process', {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            }),
            window.BUDG_API_SERVICE ? window.BUDG_API_SERVICE.getProcessTypeList() : Promise.resolve([]),
            fetch(`/api/process-impact/${processId}/predecessors`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            })
        ]);
        
        if (relationTypesRes.ok) {
            const relationTypesData = await relationTypesRes.json();
            predecessorRelationTypes = Array.isArray(relationTypesData) ? relationTypesData : (relationTypesData?.data || []);
        }
        
        if (processesRes.ok) {
            const processesData = await processesRes.json();
            predecessorProcesses = Array.isArray(processesData) ? processesData : (processesData?.data || []);
            // Filter out current process
            predecessorProcesses = predecessorProcesses.filter(p => (p.id || p.ID) != processId);
        }
        
        // Build process types map
        if (processTypesRes && Array.isArray(processTypesRes)) {
            processTypesMap.clear();
            processTypesRes.forEach(pt => {
                const typeId = pt.id || pt.ID;
                const typeName = pt.primaryname || pt.PrimaryName || pt.name || 'Process';
                if (typeId) {
                    processTypesMap.set(typeId, typeName);
                }
            });
        }
        
        // Enrich processes with type names
        predecessorProcesses.forEach(p => {
            const typeId = p.type || p.Type_ID || p.type_id;
            if (typeId && processTypesMap.has(typeId)) {
                p.typeName = processTypesMap.get(typeId);
            } else if (!p.typeName) {
                p.typeName = 'Process'; // Default
            }
        });
        
        if (relationshipsRes.ok) {
            const relationshipsData = await relationshipsRes.json();
            predecessorRelationships = Array.isArray(relationshipsData) ? relationshipsData : (relationshipsData?.data || []);
        } else if (relationshipsRes.status === 404) {
            // Endpoint might not exist yet, use empty array
            predecessorRelationships = [];
        }
        
        renderPredecessorsTable();
        
    } catch (error) {
        console.error('Error loading predecessors:', error);
        predecessorRelationships = [];
        renderPredecessorsTable();
    }
}

function renderPredecessorsTable() {
    const tbody = document.getElementById('predecessorsTableBody');
    const footer = document.getElementById('predecessorsFooter');
    
    if (!tbody || !footer) return;
    
    // Always show at least one empty row
    let html = '';
    
    // Render existing relationships
    predecessorRelationships.forEach((rel, index) => {
        const rowId = rel.id || 'new-' + index;
        const targetProcess = predecessorProcesses.find(p => (p.id || p.ID) == (rel.targetProcessId || rel.targetprocess_id));
        const processRef = targetProcess ? (targetProcess.refnumber || targetProcess.RefNumber || targetProcess.ref || '') : '';
        const processName = targetProcess ? (targetProcess.primaryname || targetProcess.PrimaryName || targetProcess.name || 'Unknown') : 'Unknown';
        const processType = targetProcess ? (targetProcess.typeName || 'Process') : 'Process';
        const condition = rel.annotations || rel.condition || '';
        const targetProcessId = targetProcess ? (targetProcess.id || targetProcess.ID) : '';
        
        html += `
            <tr data-id="${rowId}" data-process-id="${targetProcessId}">
                <td>
                    <select class="form-control form-control-sm" data-field="ref" onchange="updatePredecessorFromRef(this)">
                        <option value="">Select ref</option>
                        ${predecessorProcesses.map(p => {
                            const pRef = p.refnumber || p.RefNumber || p.ref || '';
                            const pId = p.id || p.ID;
                            const selected = processRef && pRef == processRef ? 'selected' : '';
                            return `<option value="${pId}" data-ref="${escapeHtml(pRef)}" data-name="${escapeHtml(p.primaryname || p.PrimaryName || p.name || 'Unnamed Process')}" ${selected}>${escapeHtml(pRef || 'No Ref')}</option>`;
                        }).join('')}
                    </select>
                </td>
                <td>
                    <select class="form-control form-control-sm" data-field="predecessor" onchange="updatePredecessorFromName(this)">
                        <option value="">Select predecessor</option>
                        ${predecessorProcesses.map(p => {
                            const pName = p.primaryname || p.PrimaryName || p.name || 'Unnamed Process';
                            const pId = p.id || p.ID;
                            const selected = targetProcessId && pId == targetProcessId ? 'selected' : '';
                            return `<option value="${pId}" data-ref="${escapeHtml(p.refnumber || p.RefNumber || p.ref || '')}" data-name="${escapeHtml(pName)}" ${selected}>${escapeHtml(pName)}</option>`;
                        }).join('')}
                    </select>
                </td>
                <td>
                    <input type="text" class="form-control form-control-sm" data-field="type" value="${escapeHtml(processType)}" readonly style="background: #f5f5f5;">
                </td>
                <td>
                    <input type="text" class="form-control form-control-sm" data-field="condition" value="${escapeHtml(condition)}" placeholder="Enter condition" maxlength="40">
                </td>
                <td>
                    <div class="action-buttons">
                        <button type="button" class="btn btn-sm btn-success" onclick="addPredecessorRow()" title="Add row">
                            <i class="fas fa-plus"></i>
                        </button>
                        <button type="button" class="btn btn-sm btn-danger" onclick="deletePredecessorRow('${rowId}')" title="Delete row">
                            <i class="fas fa-minus"></i>
                        </button>
                    </div>
                </td>
            </tr>
        `;
    });
    
    // Add one empty row for adding new predecessors
    html += `
        <tr data-id="new-empty" data-process-id="">
            <td>
                <select class="form-control form-control-sm" data-field="ref" onchange="updatePredecessorFromRef(this)">
                    <option value="">Select ref</option>
                    ${predecessorProcesses.map(p => {
                        const pRef = p.refnumber || p.RefNumber || p.ref || '';
                        const pId = p.id || p.ID;
                        return `<option value="${pId}" data-ref="${escapeHtml(pRef)}" data-name="${escapeHtml(p.primaryname || p.PrimaryName || p.name || 'Unnamed Process')}">${escapeHtml(pRef || 'No Ref')}</option>`;
                    }).join('')}
                </select>
            </td>
            <td>
                <select class="form-control form-control-sm" data-field="predecessor" onchange="updatePredecessorFromName(this)">
                    <option value="">Select predecessor</option>
                    ${predecessorProcesses.map(p => {
                        const pName = p.primaryname || p.PrimaryName || p.name || 'Unnamed Process';
                        const pId = p.id || p.ID;
                        return `<option value="${pId}" data-ref="${escapeHtml(p.refnumber || p.RefNumber || p.ref || '')}" data-name="${escapeHtml(pName)}">${escapeHtml(pName)}</option>`;
                    }).join('')}
                </select>
            </td>
            <td>
                <input type="text" class="form-control form-control-sm" data-field="type" readonly style="background: #f5f5f5;">
            </td>
            <td>
                <input type="text" class="form-control form-control-sm" data-field="condition" placeholder="Enter condition" maxlength="40">
            </td>
            <td>
                <div class="action-buttons">
                    <button type="button" class="btn btn-sm btn-success" onclick="addPredecessorRow()" title="Add row">
                        <i class="fas fa-plus"></i>
                    </button>
                    <button type="button" class="btn btn-sm btn-danger" onclick="deletePredecessorRow('new-empty')" title="Delete row">
                        <i class="fas fa-minus"></i>
                    </button>
                </div>
            </td>
        </tr>
    `;
    
    tbody.innerHTML = html;
    const existingRows = tbody.querySelectorAll('tr[data-id]:not([data-id="new-empty"])');
    footer.textContent = `${existingRows.length} record${existingRows.length !== 1 ? 's' : ''}`;
}

function updatePredecessorFromRef(selectElement) {
    const row = selectElement.closest('tr');
    const processId = selectElement.value;
    const selectedOption = selectElement.options[selectElement.selectedIndex];
    const processRef = selectedOption ? selectedOption.getAttribute('data-ref') : '';
    
    const predecessorSelect = row.querySelector('[data-field="predecessor"]');
    const typeInput = row.querySelector('[data-field="type"]');
    
    if (processId) {
        const process = predecessorProcesses.find(p => (p.id || p.ID) == processId);
        if (process) {
            const processType = process.typeName || 'Process';
            
            // Update predecessor select to match
            if (predecessorSelect) {
                predecessorSelect.value = processId;
            }
            if (typeInput) {
                typeInput.value = processType;
            }
            
            // Store process ID in row data attribute
            row.setAttribute('data-process-id', processId);
        }
    } else {
        if (predecessorSelect) {
            predecessorSelect.value = '';
        }
        if (typeInput) {
            typeInput.value = '';
        }
        row.setAttribute('data-process-id', '');
    }
}

function updatePredecessorFromName(selectElement) {
    const row = selectElement.closest('tr');
    const processId = selectElement.value;
    const selectedOption = selectElement.options[selectElement.selectedIndex];
    const processRef = selectedOption ? selectedOption.getAttribute('data-ref') : '';
    
    const refSelect = row.querySelector('[data-field="ref"]');
    const typeInput = row.querySelector('[data-field="type"]');
    
    if (processId) {
        const process = predecessorProcesses.find(p => (p.id || p.ID) == processId);
        if (process) {
            const processType = process.typeName || 'Process';
            
            // Update ref select to match
            if (refSelect) {
                refSelect.value = processId;
            }
            if (typeInput) {
                typeInput.value = processType;
            }
            
            // Store process ID in row data attribute
            row.setAttribute('data-process-id', processId);
        }
    } else {
        if (refSelect) {
            refSelect.value = '';
        }
        if (typeInput) {
            typeInput.value = '';
        }
        row.setAttribute('data-process-id', '');
    }
}

function addPredecessorRow() {
    const tbody = document.getElementById('predecessorsTableBody');
    if (!tbody) return;
    
    const newRow = document.createElement('tr');
    const rowId = 'new-' + Date.now();
    newRow.setAttribute('data-id', rowId);
    newRow.setAttribute('data-process-id', '');
    newRow.innerHTML = `
        <td>
            <select class="form-control form-control-sm" data-field="ref" onchange="updatePredecessorFromRef(this)">
                <option value="">Select ref</option>
                ${predecessorProcesses.map(p => {
                    const pRef = p.refnumber || p.RefNumber || p.ref || '';
                    const pId = p.id || p.ID;
                    return `<option value="${pId}" data-ref="${escapeHtml(pRef)}" data-name="${escapeHtml(p.primaryname || p.PrimaryName || p.name || 'Unnamed Process')}">${escapeHtml(pRef || 'No Ref')}</option>`;
                }).join('')}
            </select>
        </td>
        <td>
            <select class="form-control form-control-sm" data-field="predecessor" onchange="updatePredecessorFromName(this)">
                <option value="">Select predecessor</option>
                ${predecessorProcesses.map(p => {
                    const pName = p.primaryname || p.PrimaryName || p.name || 'Unnamed Process';
                    const pId = p.id || p.ID;
                    return `<option value="${pId}" data-ref="${escapeHtml(p.refnumber || p.RefNumber || p.ref || '')}" data-name="${escapeHtml(pName)}">${escapeHtml(pName)}</option>`;
                }).join('')}
            </select>
        </td>
        <td>
            <input type="text" class="form-control form-control-sm" data-field="type" readonly style="background: #f5f5f5;">
        </td>
        <td>
            <input type="text" class="form-control form-control-sm" data-field="condition" placeholder="Enter condition" maxlength="40">
        </td>
        <td>
            <div class="action-buttons">
                <button type="button" class="btn btn-sm btn-success" onclick="addPredecessorRow()" title="Add row">
                    <i class="fas fa-plus"></i>
                </button>
                <button type="button" class="btn btn-sm btn-danger" onclick="deletePredecessorRow('${rowId}')" title="Delete row">
                    <i class="fas fa-minus"></i>
                </button>
            </div>
        </td>
    `;
    
    // Insert before the last row (which is the empty row)
    const lastRow = tbody.querySelector('tr[data-id="new-empty"]');
    if (lastRow) {
        tbody.insertBefore(newRow, lastRow);
    } else {
        tbody.appendChild(newRow);
    }
}

function deletePredecessorRow(rowId) {
    const row = document.querySelector(`tr[data-id="${rowId}"]`);
    if (row) {
        row.remove();
        // Update footer count
        const tbody = document.getElementById('predecessorsTableBody');
        const footer = document.getElementById('predecessorsFooter');
        if (tbody && footer) {
            const existingRows = tbody.querySelectorAll('tr[data-id]:not([data-id="new-empty"])');
            footer.textContent = `${existingRows.length} record${existingRows.length !== 1 ? 's' : ''}`;
        }
    }
}

function collectPredecessorsData() {
    const tbody = document.getElementById('predecessorsTableBody');
    if (!tbody) {
        console.warn('Predecessors table body not found');
        return [];
    }
    
    const relationships = [];
    // Get all rows including the empty row if it has data
    const allRows = tbody.querySelectorAll('tr[data-id]');
    
    console.log('Collecting predecessors data from', allRows.length, 'total rows');
    
    allRows.forEach((row, index) => {
        const rowId = row.getAttribute('data-id');
        const processId = row.getAttribute('data-process-id');
        const refSelect = row.querySelector('[data-field="ref"]');
        const predecessorSelect = row.querySelector('[data-field="predecessor"]');
        // Get process ID from either ref select or predecessor select (prefer ref if both are set)
        const targetProcessId = refSelect?.value || predecessorSelect?.value || processId;
        const condition = row.querySelector('[data-field="condition"]')?.value || '';
        
        console.log(`Row ${index + 1}: rowId=${rowId}, processId=${processId}, refSelect.value=${refSelect?.value}, predecessorSelect.value=${predecessorSelect?.value}, targetProcessId=${targetProcessId}`);
        
        // Skip if no target process ID is selected
        if (!targetProcessId || targetProcessId === '' || targetProcessId === '0') {
            console.log(`Row ${index + 1}: Skipping - no target process ID selected`);
            return;
        }
        
        // Get relation type - use "Is predecessor to" (ID 2) as default if available
        let relationType = 2; // Default to "Is predecessor to"
        if (predecessorRelationTypes && predecessorRelationTypes.length > 0) {
            const predecessorType = predecessorRelationTypes.find(rt => {
                const rtName = (rt.primaryname || rt.PrimaryName || rt.name || '').toLowerCase();
                return rtName.includes('predecessor');
            });
            if (predecessorType) {
                relationType = predecessorType.id || predecessorType.ID || 2;
            }
        } else {
            console.warn('No predecessor relation types loaded, using default:', relationType);
        }
        
        const relationship = {
            id: rowId && !rowId.startsWith('new-') && rowId !== 'new-empty' ? parseInt(rowId) : null,
            targetProcessId: parseInt(targetProcessId),
            relationType: relationType,
            annotations: condition || null
        };
        
        console.log(`Row ${index + 1}: Collected relationship:`, relationship);
        relationships.push(relationship);
    });
    
    console.log('Total relationships collected:', relationships.length);
    return relationships;
}

async function savePredecessors(processId) {
    try {
        const relationships = collectPredecessorsData();
        console.log('Saving predecessors for process:', processId);
        console.log('Collected relationships:', relationships);
        
        if (!relationships || relationships.length === 0) {
            console.log('No predecessor relationships to save');
            return true; // No relationships to save is not an error
        }
        
        const response = await fetch(`/api/process-impact/${processId}/predecessors/save`, {
            method: 'POST',
            credentials: 'include',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ relationships })
        });
        
        if (!response.ok) {
            const errorText = await response.text();
            console.error('Failed to save predecessors:', response.status, errorText);
            throw new Error(`HTTP error! status: ${response.status}, message: ${errorText}`);
        }
        
        const result = await response.json();
        console.log('Predecessors save result:', result);
        
        if (result.success === false) {
            console.error('Predecessors save failed:', result.message || 'Unknown error');
            return false;
        }
        
        console.log('✅ Predecessors saved successfully');
        return true;
        
    } catch (error) {
        console.error('Error saving predecessors:', error);
        console.error('Error stack:', error.stack);
        return false;
    }
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function populateForm(process) {
    console.log('=== POPULATING FORM ===');
    console.log('Process data received:', process);
    const loadedSegmentId = process.segmentId ?? process.segment_id ?? process.Segment_ID;
    window.currentImpactSegmentId = loadedSegmentId || 1;
    console.log('Process data keys:', Object.keys(process));
    console.log('Process step_type value:', process.step_type);
    console.log('Process step_type type:', typeof process.step_type);
    
    const setVal = (i, v) => { 
        const el = document.getElementById(i); 
        if (el) {
            el.value = v ?? '';
            console.log(`Set ${i} to:`, v);
        } else {
            console.warn(`Element ${i} not found`);
        }
    };
    
    const setSel = (i, v) => { 
        const el = document.getElementById(i); 
        console.log(`Setting select ${i} with value:`, v, 'Element found:', !!el);
        if (el) {
            // Explicitly handle null, undefined, or -1 for non-mandatory fields like durationType
            if (v == null || v === undefined || v === -1) {
                el.value = ''; // Set to empty string to select the "no value" option
                console.log(`Set select ${i} to empty (value was null/undefined/-1)`);
            } else {
                const value = String(v);
                el.value = value;
                console.log(`Set select ${i} to:`, value);
            }
            if (i === 'stepType') {
                console.log('Step type select options:', Array.from(el.options).map(opt => ({value: opt.value, text: opt.textContent})));
                console.log('Step type select value after setting:', el.value);
                console.log('Step type select selected option:', el.options[el.selectedIndex]);
            }
        } else {
            console.warn(`Select element ${i} not found`);
        }
    };
    
    const setCheckbox = (i, v) => {
        const el = document.getElementById(i);
        if (el) {
            el.checked = v === 1 || v === true;
            console.log(`Set checkbox ${i} to:`, el.checked);
        }
    };
    
    // Populate form fields based on schema.sql process table structure
    // Use SerializedName from Process.java model
    setVal('processName', process.primaryname);
    setVal('processRef', process.refnumber);
    setVal('processDescription', process.description);
    setVal('inputDescription', process.input_description);
    setVal('outputDescription', process.output_description);
    setVal('durationValue', process.duration);
    console.log('Setting duration value:', process.duration);
    
    // Set dropdowns
    setSel('budgStatus', process.status);
    setSel('lifecycle', process.lifecycle_status);
    console.log('Setting lifecycle status:', process.lifecycle_status);
    console.log('Process lifecycle_status field:', process.lifecycle_status);
    console.log('Process lifecycle field:', process.lifecycle);
    setSel('budgViewing', process.ispublic);
    setSel('processType', process.type);
    console.log('Setting step type:', process.step_type);
    setSel('stepType', process.step_type);
    setSel('durationType', process.duration_type);
    console.log('Setting duration type:', process.duration_type);
    setSel('classification', process.processclass_id);
    setSel('automation', process.processautomation_id);
    
    // Set permissions checkboxes
    setCheckbox('permCreate', process.cancreate);
    setCheckbox('permRead', process.canread);
    setCheckbox('permUpdate', process.canupdate);
    setCheckbox('permDelete', process.candelete);
    setCheckbox('permArchive', process.canarchive);
    
    // Set segment value if available
    if (segmentField && loadedSegmentId != null && loadedSegmentId !== '') {
        segmentField.setValue(loadedSegmentId);
    }
    if (segmentField && typeof segmentField.getValue === 'function') {
        originalProcessSegmentId = normalizeProcessSegmentId(segmentField.getValue());
    } else {
        originalProcessSegmentId = normalizeProcessSegmentId(loadedSegmentId);
    }
    
    console.log('Form populated successfully');

    // Backfill a missing ref number on edit so users can persist it on save.
    if (!process.refnumber || !String(process.refnumber).trim()) {
        initAutoReferenceGeneration({ markDirty: true });
    }
    
    // ⚠️ CRITICAL: Re-apply DFCR locks after populating form
    // This ensures locks are applied based on current Auto CR state from backend
    const processId = parseId();
    if (processId && window.reapplyDFCRLocks) {
        setTimeout(async () => {
            await window.reapplyDFCRLocks('Process', processId);
        }, 50);
    }
}

function loadParentProcess(process) {
    const parentId = getProcessParentId(process);
    console.log('=== LOADING PARENT PROCESS DEBUG ===');
    console.log('Loading parent process for process:', process);
    console.log('Parent ID found:', parentId);
    console.log('Process keys:', Object.keys(process));
    console.log('Process parentid field:', process.parentid);
    console.log('Process parent_id field:', process.parent_id);
    console.log('Process ParentID field:', process.ParentID);
    console.log('Process Parent_ID field:', process.Parent_ID);
    
    if (parentId) {
        // Load parent process name
        console.log('Calling getProcessById with parentId:', parentId);
        window.BUDG_API_SERVICE.getProcessById(parentId)
            .then(parentProcess => {
                console.log('=== PARENT PROCESS API RESPONSE ===');
                console.log('Parent process loaded:', parentProcess);
                console.log('Parent process type:', typeof parentProcess);
                console.log('Parent process keys:', Object.keys(parentProcess || {}));
                const parentNameInput = document.getElementById('parentName');
                if (parentNameInput && parentProcess) {
                    // Handle both direct data and wrapped response
                    const parent = parentProcess?.data || parentProcess;
                    console.log('Parent data after unwrapping:', parent);
                    console.log('Parent primaryname field:', parent?.primaryname);
                    console.log('Parent PrimaryName field:', parent?.PrimaryName);
                    console.log('Parent name field:', parent?.name);
                    console.log('Parent Name field:', parent?.Name);
                    console.log('Parent description field:', parent?.description);
                    console.log('Parent Description field:', parent?.Description);
                    
                    const parentName = parent?.primaryname || parent?.PrimaryName || parent?.name || parent?.Name || 'Unnamed Process';
                    const parentDescription = parent?.description || parent?.Description || '';
                    
                    // Display both name and description in the input field
                    const displayText = parentDescription ? `${parentName} - ${parentDescription}` : parentName;
                    parentNameInput.value = displayText;
                    parentNameInput.dataset.parentId = parentId;
                    parentNameInput.dataset.parentName = parentName;
                    parentNameInput.dataset.parentDescription = parentDescription;
                    
                    console.log('Set parent display text in input:', displayText);
                    console.log('Set parent ID in dataset:', parentId);
                    console.log('Set parent name in dataset:', parentName);
                    console.log('Set parent description in dataset:', parentDescription);
                } else {
                    console.warn('Parent name input not found or parent process is null');
                }
            })
            .catch(error => {
                console.error('Error loading parent process:', error);
            });
    } else {
        console.log('No parent ID found for this process');
        // Clear parent fields if no parent
        const parentNameInput = document.getElementById('parentName');
        if (parentNameInput) {
            parentNameInput.value = '';
            delete parentNameInput.dataset.parentId;
            delete parentNameInput.dataset.parentName;
            delete parentNameInput.dataset.parentDescription;
        }
    }
}

function updateTitle(process) {
    const titleElement = document.getElementById('processTitle');
    if (titleElement && process) {
        const name = process.primaryname || 'Unnamed Process';
        const ref = process.refnumber || '';
        titleElement.textContent = ref ? `${ref}: ${name}` : name;
    }
}

function getCurrentActiveTab() {
    const activeTab = document.querySelector('.tab.active');
    return activeTab ? activeTab.getAttribute('data-tab') : 'summary';
}

async function saveNewProcess(closeAfter) {
    const activeTab = getCurrentActiveTab();
    const buttons = [document.getElementById('saveBtn'), document.getElementById('saveAndCloseBtn')];
    const restoreButtons = () => { buttons.forEach(b => { if (b) b.disabled = false; }); };
    buttons.forEach(b => { if (b) b.disabled = true; });

    if (activeTab !== 'summary') {
        showSuccessMessage(window.I18n ? window.I18n.t('regulation.messages.noDataToSaveOnTab') : 'Save the new process from the Summary tab', false);
        restoreButtons();
        return;
    }

    try {
        if (window.customFieldsContext && window.customFieldsContext.validate && !window.customFieldsContext.validate()) {
            restoreButtons();
            return;
        }

        let currentUserId = null;
        try {
            const meResp = await fetch('/api/me', { method: 'GET', credentials: 'include' });
            if (meResp.ok) {
                const me = await meResp.json();
                currentUserId = me.id || me.ID || me.userId || me.UserID;
            }
        } catch (e) {
            console.warn('Error fetching current user:', e);
        }

        if (typeof syncAdvancedRichTextToTextarea === 'function') {
            syncAdvancedRichTextToTextarea('processDescription');
            syncAdvancedRichTextToTextarea('inputDescription');
            syncAdvancedRichTextToTextarea('outputDescription');
        }

        if (segmentField && !segmentField.validate()) {
            restoreButtons();
            return;
        }

        const payload = collectFormData(currentUserId);
        if (!window.BUDG_API_SERVICE || typeof window.BUDG_API_SERVICE.createProcess !== 'function') {
            throw new Error('API service not available');
        }
        const res = await window.BUDG_API_SERVICE.createProcess(payload);
        if (res && res.success === false) {
            throw new Error(res.message || 'Create failed');
        }
        const data = res && (res.data || res);
        const newId = data && (data.id ?? data.ID ?? data.Id);
        if (!newId) {
            console.error('Create response:', res);
            throw new Error('Server did not return new process id');
        }

        if (window.customFieldsContext && window.customFieldsContext.saveValues) {
            await window.customFieldsContext.saveValues(newId);
        }

        preventUnloadWarning = true;
        markAsClean();
        showSuccessMessage('Process created');
        const dest = `/view/process/process-edit.html?id=${newId}`;
        if (closeAfter) {
            setTimeout(() => { window.location.href = `/view/process/${newId}`; }, 1200);
        } else {
            setTimeout(() => { window.location.href = dest; }, 800);
        }
    } catch (err) {
        console.error('Create process error:', err);
        const errorMsg = err?.body?.error || err?.body?.message || err?.message || 'Failed to create process';
        alert(errorMsg);
    } finally {
        restoreButtons();
    }
}

async function saveProcess(id, closeAfter) {
    if (id == null || id === '') {
        await saveNewProcess(closeAfter);
        return;
    }

    const activeTab = getCurrentActiveTab();
    console.log(`=== SAVING PROCESS (active tab: ${activeTab}) ===`);

    const buttons = [document.getElementById('saveBtn'), document.getElementById('saveAndCloseBtn')];
    const restoreButtons = () => { buttons.forEach(b => { if (b) b.disabled = false; }); };
    buttons.forEach(b => { if (b) b.disabled = true; });

    try {
        if (activeTab === 'components') {
            // Components tab has no direct save action
            showSuccessMessage(window.I18n ? window.I18n.t('regulation.messages.noDataToSaveOnTab') : 'No data to save on this tab', false);
            restoreButtons();
            return;

        } else if (activeTab === 'stakeholders') {
            if (window.ProcessStakeholderEdit) {
                const stakeholdersSaved = await window.ProcessStakeholderEdit.saveStakeholders();
                if (!stakeholdersSaved) {
                    showSuccessMessage('Failed to save stakeholders.', true);
                    restoreButtons();
                    return;
                }
                console.log('✅ Stakeholders saved successfully');
            } else {
                showSuccessMessage(window.I18n ? window.I18n.t('message.noChangesToSave') : 'No changes to save', false);
                restoreButtons();
                return;
            }

        } else if (activeTab === 'impact') {
            const impactHasChanges = window.hasImpactChanges && window.hasImpactChanges();
            if (!impactHasChanges) {
                showSuccessMessage(window.I18n ? window.I18n.t('message.noChangesToSave') : 'No changes to save', false);
                restoreButtons();
                return;
            }
            const impactResult = await window.saveAllImpactData(id);
            if (!impactResult.success) {
                showSuccessMessage('Impact data failed to save: ' + impactResult.message, true);
                restoreButtons();
                return;
            }
            console.log('✅ Impact saved successfully');

        } else {
            // summary tab — also save predecessors first
            try {
                const predecessorsSaved = await savePredecessors(id);
                if (!predecessorsSaved) {
                    console.warn('⚠️ Predecessors save failed, but continuing with main save');
                }
            } catch (error) {
                console.error('Error saving predecessors:', error);
            }

            const formHasChanges = hasFormChanges;
            if (!formHasChanges) {
                // Still save custom fields even if main form hasn't changed
                if (window.customFieldsContext && window.customFieldsContext.saveValues) {
                    try {
                        await window.customFieldsContext.saveValues(id);
                        console.log('✅ Custom fields saved successfully');
                        showSuccessMessage('UPDATES SAVED');
                    } catch (error) {
                        console.error('Error saving custom fields:', error);
                        showSuccessMessage('Error saving custom fields', true);
                    }
                } else {
                    showSuccessMessage('NO CHANGES', true);
                }
                if (closeAfter) {
                    setTimeout(() => { window.location.href = `/view/process/${id}`; }, 1500);
                }
                restoreButtons();
                return;
            }

            let currentUserId = null;
            try {
                const meResp = await fetch('/api/me', { method: 'GET', credentials: 'include' });
                if (meResp.ok) {
                    const me = await meResp.json();
                    currentUserId = me.id || me.ID || me.userId || me.UserID;
                }
            } catch (e) {
                console.warn('Error fetching current user:', e);
            }

            // Sync rich-text editor content back to textareas before reading
            if (typeof syncAdvancedRichTextToTextarea === 'function') {
                syncAdvancedRichTextToTextarea('processDescription');
                syncAdvancedRichTextToTextarea('inputDescription');
                syncAdvancedRichTextToTextarea('outputDescription');
            }

            if (segmentField && !segmentField.validate()) {
                restoreButtons();
                return;
            }

            const payload = collectFormData(currentUserId);
            const res = await window.BUDG_API_SERVICE.updateProcess(id, payload);

            if (res && res.success === false) {
                if (res.locked) {
                    const lockedBy = res.lockedBy || 'another user';
                    const isPermanent = res.isPermanent || false;
                    showSuccessMessage(`This process is currently locked by ${lockedBy}. ${isPermanent ? 'This is a permanent lock.' : 'Please try again later.'}`, true);
                    if (window.currentLockManager) { await window.currentLockManager.releaseLock(); }
                    setTimeout(() => { window.location.href = `/view/process/${id}`; }, 3000);
                    restoreButtons();
                    return false;
                }
                throw new Error(res.message || 'Update failed');
            }

            // Save custom fields if context exists
            if (window.customFieldsContext && window.customFieldsContext.saveValues) {
                try {
                    await window.customFieldsContext.saveValues(id);
                    console.log('✅ Custom fields saved successfully');
                } catch (error) {
                    console.error('Error saving custom fields:', error);
                }
            }

            // If segment changed, reload impact data (do not save impact from summary)
            const currentProcessSegment = normalizeProcessSegmentId(payload.segmentId ?? (segmentField && segmentField.getValue()));
            const processSegmentChanged = currentProcessSegment !== normalizeProcessSegmentId(originalProcessSegmentId);
            if (processSegmentChanged && window.initImpactEdit) {
                console.log('=== Segment changed; reloading process impact from server ===');
                await window.initImpactEdit(id, editViewMode);
            }

            // Release lock after successful save
            if (window.currentLockManager) { await window.currentLockManager.releaseLock(); }

            markAsClean();
            originalProcessSegmentId = normalizeProcessSegmentId(payload.segmentId ?? (segmentField && segmentField.getValue()));

            try { window.BUDG_API_SERVICE.logVisit({ entity: 'Process', entityId: String(id), route: `/view/process/${id}` }); } catch(_) {}
        }

        showSuccessMessage('UPDATES SAVED');
        if (closeAfter) {
            setTimeout(() => { window.location.href = `/view/process/${id}`; }, 1500);
        }
    } catch (err) {
        console.error('Save error:', err);
        const errorMsg = err?.body?.error || err?.body?.message || err?.message || 'Failed to save';
        alert(errorMsg);
    } finally {
        restoreButtons();
    }
}

function collectFormData(currentUserId = null) {
    const name = document.getElementById('processName')?.value?.trim();
    const description = document.getElementById('processDescription')?.value?.trim();
    const status = parseInt(document.getElementById('budgStatus')?.value || '', 10);
    const lifecycle = parseInt(document.getElementById('lifecycle')?.value || '', 10);
    console.log('Collecting lifecycle value:', lifecycle);
    console.log('Lifecycle select element:', document.getElementById('lifecycle'));
    console.log('Lifecycle select value:', document.getElementById('lifecycle')?.value);
    const viewing = parseInt(document.getElementById('budgViewing')?.value || '', 10);
    const processType = parseInt(document.getElementById('processType')?.value || '', 10);
    const stepType = document.getElementById('stepType')?.value?.trim();
    const refNumber = document.getElementById('processRef')?.value?.trim();
    const inputDescription = document.getElementById('inputDescription')?.value?.trim();
    const outputDescription = document.getElementById('outputDescription')?.value?.trim();
    const durationType = parseInt(document.getElementById('durationType')?.value || '', 10);
    const durationValue = parseInt(document.getElementById('durationValue')?.value || '', 10);
    const classification = parseInt(document.getElementById('classification')?.value || '', 10);
    const automation = parseInt(document.getElementById('automation')?.value || '', 10);
    
    // Get permissions
    const permCreate = document.getElementById('permCreate')?.checked ? 1 : 0;
    const permRead = document.getElementById('permRead')?.checked ? 1 : 0;
    const permUpdate = document.getElementById('permUpdate')?.checked ? 1 : 0;
    const permDelete = document.getElementById('permDelete')?.checked ? 1 : 0;
    const permArchive = document.getElementById('permArchive')?.checked ? 1 : 0;
    
    const required = [
        { ok: !!name, field: 'Name' },
        { ok: !!description, field: 'Description' },
        { ok: Number.isInteger(status), field: 'BUDG Status' },
        { ok: Number.isInteger(lifecycle), field: 'Lifecycle' },
        { ok: Number.isInteger(viewing), field: 'BUDG Viewing' },
        { ok: Number.isInteger(processType), field: 'Type' },
        { ok: !!stepType, field: 'Step Type' }
    ];
    
    const missing = required.filter(r => !r.ok).map(r => r.field);
    
    if (missing.length > 0) {
        throw new Error(`Please fill required fields: ${missing.join(', ')}`);
    }
    
    console.log('Setting lifecycle_status in payload:', lifecycle);
    console.log('Setting lastupdateuser_id in payload:', currentUserId);
    
    const payload = {
        primaryname: name,
        description: description,
        refnumber: refNumber || null,
        input_description: inputDescription || null,
        output_description: outputDescription || null,
        status: status,
        lifecycle_status: lifecycle,
        ispublic: viewing,
        type: processType,
        step_type: stepType,
        duration_type: Number.isInteger(durationType) ? durationType : null,
        duration: Number.isInteger(durationValue) ? durationValue : null,
        processclass_id: Number.isInteger(classification) ? classification : null,
        processautomation_id: Number.isInteger(automation) ? automation : null,
        cancreate: permCreate,
        canread: permRead,
        canupdate: permUpdate,
        candelete: permDelete,
        canarchive: permArchive,
        segmentId: segmentField ? segmentField.getValue() : 1
    };
    
    // Add current user ID if available
    if (currentUserId) {
        payload.lastupdateuser_id = currentUserId;
        console.log('✅ Added lastupdateuser_id to payload:', currentUserId);
    } else {
        console.warn('⚠️ No current user ID available - lastupdateuser_id not set');
    }
    
    // Add parent ID if selected - use 'parent_id' (with underscore) as expected by backend ProcessServlet
    const parentNameInput = document.getElementById('parentName');
    
    // Get parent ID from parentName input dataset (set when parent is selected)
    let parentId = null;
    if (parentNameInput && parentNameInput.dataset.parentId) {
        parentId = parseInt(parentNameInput.dataset.parentId, 10);
        if (isNaN(parentId) || parentId === 0) {
            parentId = null;
        }
        console.log('Found parent ID in parentName dataset:', parentId);
    } else {
        console.log('No parent ID found in parentName dataset');
    }
    
    // Use 'parent_id' (with underscore) as expected by ProcessServlet.java (line 140)
    // The backend expects 'parent_id' in JSON, but the database column is 'parentid' (no underscore)
    // Always set parent_id, even if null (to explicitly clear parent)
    payload.parent_id = parentId;
    console.log('Adding parent ID to payload as parent_id:', payload.parent_id);
    console.log('Parent ID type:', typeof payload.parent_id);
    console.log('Parent ID is null?', payload.parent_id === null);
    console.log('Parent ID is undefined?', payload.parent_id === undefined);
    if (parentNameInput) {
        console.log('Parent name from dataset:', parentNameInput.dataset.parentName);
        console.log('Parent description from dataset:', parentNameInput.dataset.parentDescription);
    }
    
    return payload;
}

function showSuccessMessage(message, isError = false) {
    const existingMessage = document.getElementById('success-message');
    if (existingMessage) {
        existingMessage.remove();
    }
    
    const successDiv = document.createElement('div');
    successDiv.id = 'success-message';
    const backgroundColor = isError ? '#ef4444' : '#248567';
    successDiv.style.cssText = `
        position: fixed;
        top: 80px;
        left: 50%;
        transform: translateX(-50%);
        background-color: ${backgroundColor};
        color: white;
        padding: 12px 24px;
        border-radius: 6px;
        font-weight: 600;
        font-size: 14px;
        z-index: 1000;
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
        animation: slideDown 0.3s ease-out;
    `;
    successDiv.textContent = message;
    
    const style = document.createElement('style');
    style.textContent = `
        @keyframes slideDown {
            from {
                opacity: 0;
                transform: translateX(-50%) translateY(-20px);
            }
            to {
                opacity: 1;
                transform: translateX(-50%) translateY(0);
            }
        }
        @keyframes slideUp {
            from {
                opacity: 1;
                transform: translateX(-50%) translateY(0);
            }
            to {
                opacity: 0;
                transform: translateX(-50%) translateY(-20px);
            }
        }
    `;
    document.head.appendChild(style);
    document.body.appendChild(successDiv);
    
    setTimeout(() => {
        if (successDiv.parentNode) {
            successDiv.style.animation = 'slideUp 0.3s ease-out';
            setTimeout(() => {
                if (successDiv.parentNode) {
                    successDiv.remove();
                }
            }, 300);
        }
    }, 3000);
}

function markAsClean() {
    document.body.classList.remove('dirty');
    preventUnloadWarning = true;
    hasFormChanges = false;
    
    // Also mark stakeholders as clean if available
    if (window.ProcessStakeholderEdit) {
        // Update original data to match current data after successful save
        window.ProcessStakeholderEdit.originalData = JSON.parse(JSON.stringify(window.ProcessStakeholderEdit.getData()));
    }
    
    updateSaveButtons(false);
}

function markAsDirty(e) {
    if (e && e.isTrusted === false) return;
    isDirty = true;
    hasFormChanges = true;
    preventUnloadWarning = false;
    updateSaveButtons();
}

function updateSaveButtons(isDirty = true) {
    const saveBtn = document.getElementById('saveBtn');
    const saveAndCloseBtn = document.getElementById('saveAndCloseBtn');
    
    // Check if there are any changes (form or stakeholders)
    const hasAnyChanges = isDirty || (window.ProcessStakeholderEdit && window.ProcessStakeholderEdit.hasDataChanged());
    
    if (hasAnyChanges) {
        if (saveBtn) saveBtn.disabled = false;
        if (saveAndCloseBtn) saveAndCloseBtn.disabled = false;
    } else {
        if (saveBtn) saveBtn.disabled = true;
        if (saveAndCloseBtn) saveAndCloseBtn.disabled = true;
    }
}

// Make updateSaveButtons available globally
window.updateSaveButtons = updateSaveButtons;

// Global variable to store form data
let formDataCache = {};

// Global variable to track if form has been modified
let hasFormChanges = false;

function saveCurrentFormData() {
    const currentTab = document.querySelector('.tab.active');
    if (!currentTab) return;
    
    const tabName = currentTab.getAttribute('data-tab');
    if (!tabName) return;
    
    // Save all form data in the current tab
    const formData = {};
    const currentTabContent = document.getElementById(tabName);
    if (currentTabContent) {
        const inputs = currentTabContent.querySelectorAll('input, textarea, select');
        inputs.forEach(input => {
            const key = input.name || input.id;
            if (!key) return;
            if (input.type === 'file') return;
            if (input.type === 'checkbox' || input.type === 'radio') {
                formData[key] = input.checked;
            } else {
                formData[key] = input.value;
            }
        });
    }
    
    formDataCache[tabName] = formData;
    console.log('Saved form data for tab:', tabName, formData);
}

function restoreFormData(tabName) {
    if (!formDataCache[tabName]) return;
    
    const tabContent = document.getElementById(tabName);
    if (!tabContent) return;
    
    const formData = formDataCache[tabName];
    const inputs = tabContent.querySelectorAll('input, textarea, select');
    
    inputs.forEach(input => {
        const key = input.name || input.id;
        if (!key) return;
        if (formData.hasOwnProperty(key)) {
            if (input.type === 'checkbox' || input.type === 'radio') {
                input.checked = formData[key];
            } else {
                if (input.type === 'file') return;
                try {
                    input.value = formData[key];
                } catch (e) {
                    // Some controls (notably file inputs) cannot be programmatically set
                }
            }
        }
    });
    
    console.log('Restored form data for tab:', tabName, formData);
}

// Helper function to escape HTML
function escapeHtml(str) {
    if (str == null) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

// Resolve status ID to name
async function resolveStatusName(statusId) {
    if (!statusId) return 'Unknown';
    
    try {
        const api = window.BUDG_API_SERVICE;
        if (!api || !api.getStatuses) {
            console.warn('API service or getStatuses method not available');
            return `Status ID: ${statusId}`;
        }
        
        const response = await api.getStatuses();
        let statuses = response;
        if (response && response.data) {
            statuses = response.data;
        }
        
        if (Array.isArray(statuses)) {
            const status = statuses.find(s => s.id === statusId || s.ID === statusId);
            return status ? (status.primaryname || status.PrimaryName || status.name || status.Name || `Status ID: ${statusId}`) : `Status ID: ${statusId}`;
        }
        
        return `Status ID: ${statusId}`;
    } catch (error) {
        console.error('Error resolving status name:', error);
        return `Status ID: ${statusId}`;
    }
}

// Load process components (hierarchy) - same as view page
async function loadProcessComponents(processId, container) {
    console.log('=== LOADING PROCESS COMPONENTS (EDIT) ===');
    console.log('Process ID:', processId);
    console.log('Container:', container);
    
    if (!container) {
        console.error('Container not provided for loadProcessComponents');
        return;
    }
    
    container.innerHTML = `
        <div class="view-section" style="grid-column:1/-1;">
            <div class="section-title">COMPONENTS</div>
            <div class="loading">Loading components...</div>
        </div>
    `;
    
    try {
        const api = window.BUDG_API_SERVICE;
        if (!api) {
            throw new Error('BUDG_API_SERVICE not available');
        }
        
        console.log('Calling getProcessHierarchy API...');
        const response = await api.getProcessHierarchy(processId);
        console.log('Hierarchy response:', response);
        
        let hierarchy = response;
        if (response && response.data) {
            hierarchy = response.data;
        }
        
        console.log('Processed hierarchy data:', hierarchy);
        
        if (!hierarchy || hierarchy.length === 0) {
            container.innerHTML = `
                <div class="view-section" style="grid-column:1/-1;">
                    <div class="section-title">COMPONENTS</div>
                    <div class="empty">No components available for this process</div>
                </div>
            `;
            return;
        }
        
        // Render hierarchy table
        const tableHtml = await renderProcessHierarchyTable(hierarchy);
        container.innerHTML = `
            <div class="view-section" style="grid-column:1/-1;">
                <div class="section-title">COMPONENTS</div>
                ${tableHtml}
            </div>
        `;
        
        // Initialize hierarchy interactions with current process ID
        initHierarchyInteractions(processId);
        
    } catch (error) {
        console.error('Error loading process components:', error);
        container.innerHTML = `
            <div class="view-section" style="grid-column:1/-1;">
                <div class="section-title">COMPONENTS</div>
                <div class="error">Failed to load components: ${error.message}</div>
            </div>
        `;
    }
}

// Render process hierarchy table - same as view page with policy style
async function renderProcessHierarchyTable(hierarchy) {
    console.log('Rendering hierarchy table with', hierarchy.length, 'items');
    
    if (!hierarchy || hierarchy.length === 0) {
        return '<div class="empty">No hierarchy data available</div>';
    }
    
    // Build children map for tree structure
    const childrenMap = new Map();
    const processById = new Map();
    hierarchy.forEach(process => {
        const processId = process.id;
        const parentId = process.parentid;
        processById.set(processId, process);
        if (parentId) {
            if (!childrenMap.has(parentId)) {
                childrenMap.set(parentId, []);
            }
            childrenMap.get(parentId).push(process);
        }
    });
    
    // Sort children by name for each parent
    childrenMap.forEach((children, parentId) => {
        children.sort((a, b) => {
            return (a.primaryname || '').localeCompare(b.primaryname || '');
        });
    });
    
    // Build proper hierarchical tree structure using parent-child relationships
    // This ensures children appear directly under their parents, not just sorted by level
    function buildTreeOrder(rootId) {
        const result = [];
        const process = processById.get(rootId);
        
        if (!process) return result;
        
        const children = childrenMap.get(rootId) || [];
        const level = Math.max(0, process.level || 0);
        
        // Add current process
        result.push(process);
        
        // Add children recursively (they will appear directly after their parent)
        children.forEach(child => {
            result.push(...buildTreeOrder(child.id));
        });
        
        return result;
    }
    
    // Find root processes (no parent or parent not in hierarchy)
    const rootProcesses = hierarchy.filter(process => {
        const parentId = process.parentid;
        return !parentId || !processById.has(parentId);
    });
    
    // Sort roots by level, then by name
    rootProcesses.sort((a, b) => {
        const levelDiff = (a.level || 0) - (b.level || 0);
        if (levelDiff !== 0) return levelDiff;
        return (a.primaryname || '').localeCompare(b.primaryname || '');
    });
    
    // Build tree-ordered hierarchy
    const sortedHierarchy = [];
    rootProcesses.forEach(root => {
        sortedHierarchy.push(...buildTreeOrder(root.id));
    });
    
        let tableHtml = `
        <div class="hierarchy-table-container">
            <div class="hierarchy-table-wrapper">
            <table class="hierarchy-table">
                <thead>
                    <tr>
                        <th class="ref-col">Ref.</th>
                        <th class="process-col">Process</th>
                        <th class="description-col">Description</th>
                        <th class="parent-col">Parent</th>
                        <th class="status-col">BUDG Status</th>
                        <th class="predecessors-col">Predecessors</th>
                        <th class="type-col">Type</th>
                        <th class="class-col">Class</th>
                        <th class="input-col">Input / Trigger</th>
                        <th class="output-col">Output / Result</th>
                    </tr>
                </thead>
                <tbody>
    `;
    
    for (let index = 0; index < sortedHierarchy.length; index++) {
        const process = sortedHierarchy[index];
        const level = process.level || 0;
        const hasChildren = (childrenMap.get(process.id) || []).length > 0;
        const childCount = (childrenMap.get(process.id) || []).length;

        // Resolve status name
        const resolvedStatusName = await resolveStatusName(process.status);

        // Build tree structure like policy - proper indentation
        const indent = Array(level).fill('<span class="tree-indent"></span>').join('');
        const expander = hasChildren ? `<button type="button" class="tree-expander" aria-label="Toggle" data-id="${process.id}"><i class="fas fa-caret-down"></i></button>` : '<span class="tree-placeholder"></span>';
        const countBadge = hasChildren ? `<span class="child-count" title="Children">${childCount}</span>` : '';
        const branchLine = level > 0 ? '<span class="tree-branch"></span>' : '';

        tableHtml += `
            <tr class="hierarchy-row level-${level}" data-level="${level}" data-id="${process.id}" data-parent-id="${process.parentid || ''}">
                <td class="ref-cell">
                    <div class="tree-cell">
                        ${indent}${expander}${branchLine}
                        <i class="fas fa-cogs item-icon"></i>
                        <span class="ref-number">${escapeHtml(process.refnumber || 'N/A')}</span>
                        ${countBadge}
                    </div>
                </td>
                <td class="process-cell">
                    <div class="process-name">
                        <a class="process-link" href="/view/process/${process.id}">${escapeHtml(process.primaryname || process.primaryName || 'Unnamed Process')}</a>
                    </div>
                </td>
                <td class="description-cell">
                    ${escapeHtml(process.description || 'No description')}
                </td>
                <td class="parent-cell">
                    ${process.parentid ? 
                        `<span class="parent-name">${escapeHtml(process.parentName || `Process ID: ${process.parentid}`)}</span>` : 
                        '<span class="empty">-</span>'}
                </td>
                <td class="status-cell">
                    <span class="status-badge ${resolvedStatusName === 'Active' ? 'status-active' : 'status-inactive'}">
                        ${escapeHtml(resolvedStatusName)}
                    </span>
                </td>
                <td class="predecessors-cell">
                    ${process.predecessors ? 
                        `<span class="predecessors-list">${escapeHtml(process.predecessors)}</span>` : 
                        '<span class="empty">-</span>'}
                </td>
                <td class="type-cell">
                    <span class="type-badge">
                        ${escapeHtml(process.typeName || process.type || 'Process')}
                    </span>
                </td>
                <td class="class-cell">
                    ${process.classificationName ? 
                        `<span class="classification-name">${escapeHtml(process.classificationName)}</span>` : 
                        '<span class="empty">-</span>'}
                </td>
                <td class="input-cell">
                    ${process.input_description ? 
                        `<span class="input-description">${escapeHtml(process.input_description)}</span>` : 
                        '<span class="empty">-</span>'}
                </td>
                <td class="output-cell">
                    ${process.output_description ? 
                        `<span class="output-description">${escapeHtml(process.output_description)}</span>` : 
                        '<span class="empty">-</span>'}
                </td>
            </tr>
        `;
    }
    
    tableHtml += `
                </tbody>
            </table>
            </div>
            <div class="hierarchy-footer">${sortedHierarchy.length} record${sortedHierarchy.length !== 1 ? 's' : ''}</div>
        </div>
    `;
    
    return tableHtml;
}

// Initialize hierarchy interactions (expand/collapse) - same as view page
function initHierarchyInteractions(currentProcessId = null) {
    console.log('Initializing hierarchy interactions');
    
    const tbody = document.querySelector('.hierarchy-table tbody');
    if (!tbody) return;
    
    // Get current process ID from parameter or URL
    if (!currentProcessId) {
        const urlParams = new URLSearchParams(window.location.search);
        currentProcessId = urlParams.get('id') ? parseInt(urlParams.get('id')) : null;
    }
    console.log('Current process ID:', currentProcessId);
    
    // Build children map - group by parent ID
    const childrenByParent = new Map();
    const allRows = Array.from(tbody.querySelectorAll('tr.hierarchy-row'));
    const rowById = new Map();
    
    allRows.forEach(tr => {
        const parentId = tr.getAttribute('data-parent-id');
        const rowId = tr.getAttribute('data-id');
        const rowLevel = parseInt(tr.getAttribute('data-level') || '0');
        
        rowById.set(rowId, { tr, id: rowId, level: rowLevel, parentId });
        
        if (parentId) {
            if (!childrenByParent.has(parentId)) {
                childrenByParent.set(parentId, []);
            }
            childrenByParent.get(parentId).push({ tr, id: rowId, level: rowLevel });
        }
    });
    
    const collapsed = new Set();
    
    // Find all ancestors of current process to expand them
    function getAncestors(processId) {
        const ancestors = [];
        let currentId = processId;
        let maxDepth = 20; // Prevent infinite loops
        
        while (currentId && maxDepth > 0) {
            const rowData = rowById.get(String(currentId));
            if (rowData && rowData.parentId) {
                ancestors.push(rowData.parentId);
                currentId = rowData.parentId;
            } else {
                break;
            }
            maxDepth--;
        }
        
        return ancestors;
    }
    
    // Initially collapse all non-root items (level > 0)
    allRows.forEach(tr => {
        const level = parseInt(tr.getAttribute('data-level') || '0');
        if (level > 0) {
            tr.style.display = 'none';
            tr.classList.add('collapsed');
        }
    });
    
    // Expand all ancestors of current process to show its location
    if (currentProcessId) {
        const ancestors = getAncestors(currentProcessId);
        console.log('Ancestors to expand:', ancestors);
        
        // Expand all ancestors (from root to current)
        ancestors.reverse().forEach(ancestorId => {
            collapsed.delete(String(ancestorId));
            const ancestorRow = rowById.get(String(ancestorId));
            if (ancestorRow) {
                const expander = ancestorRow.tr.querySelector('.tree-expander');
                if (expander) {
                    const icon = expander.querySelector('i');
                    if (icon) {
                        icon.style.transform = 'rotate(0deg)';
                    }
                }
            }
        });
        
        // Show current process and all its ancestors
        function expandPathToProcess(processId) {
            const rowData = rowById.get(String(processId));
            if (!rowData) return;
            
            // Show this row
            rowData.tr.style.display = '';
            rowData.tr.classList.remove('collapsed');
            
            // If it has a parent, expand the parent path
            if (rowData.parentId) {
                expandPathToProcess(rowData.parentId);
            }
        }
        
        // Expand path to current process
        expandPathToProcess(currentProcessId);
        
        // Highlight current process row
        const currentRow = rowById.get(String(currentProcessId));
        if (currentRow) {
            currentRow.tr.classList.add('current-row');
            // Scroll to current row after a short delay
            setTimeout(() => {
                currentRow.tr.scrollIntoView({ behavior: 'smooth', block: 'center' });
            }, 100);
        }
    }
    
    function toggleChildren(parentId, isCollapsed) {
        const directChildren = childrenByParent.get(String(parentId)) || [];
        
        directChildren.forEach(({ tr, id, level }) => {
            if (isCollapsed) {
                // Hide this child and all its descendants
                tr.style.display = 'none';
                tr.classList.add('collapsed');
                // Recursively hide all descendants
                if (childrenByParent.has(String(id))) {
                    toggleChildren(id, true);
                }
            } else {
                // Show direct children only
                tr.style.display = '';
                tr.classList.remove('collapsed');
                // Recursively show descendants only if they are also expanded
                if (childrenByParent.has(String(id)) && !collapsed.has(String(id))) {
                    toggleChildren(id, false);
                }
            }
        });
    }
    
    // Add click handlers to expanders
    tbody.querySelectorAll('.tree-expander').forEach(btn => {
        btn.addEventListener('click', function(e) {
            e.preventDefault();
            e.stopPropagation();
            
            const processId = this.getAttribute('data-id');
            const icon = this.querySelector('i');
            
            if (collapsed.has(processId)) {
                // Expand
                collapsed.delete(processId);
                icon.style.transform = 'rotate(0deg)';
                toggleChildren(processId, false);
            } else {
                // Collapse
                collapsed.add(processId);
                icon.style.transform = 'rotate(-90deg)';
                toggleChildren(processId, true);
            }
        });
    });
}

function switchTab(tabName) {
    // Save current form data before switching tabs
    saveCurrentFormData();

    // Update active class on top-level tab buttons only
    document.querySelectorAll('.tab-container .tab').forEach(tab => tab.classList.remove('active'));
    const selectedTab = document.querySelector(`.tab-container .tab[data-tab="${tabName}"]`);
    if (selectedTab) selectedTab.classList.add('active');

    // Get all 4 tab-content panels by their known IDs
    const tabIds = ['summary', 'components', 'stakeholders', 'impact'];
    const tabPanels = tabIds.map(id => document.getElementById(id)).filter(Boolean);

    // Hide all tab panels
    tabPanels.forEach(panel => {
        panel.classList.remove('active');
        panel.style.display = 'none';
    });

    // Show the selected one
    const selectedContent = document.getElementById(tabName);
    if (selectedContent) {
        selectedContent.classList.add('active');
        selectedContent.style.display = 'block';
    }

    // Ensure inner containers are visible (some start with display:none)
    if (selectedContent) {
        selectedContent.querySelectorAll('[id$="Container"]').forEach(c => {
            if (c.style.display === 'none') c.style.display = '';
        });
    }

    const id = parseId();

    // Load stakeholders data when switching to stakeholders tab
    if (tabName === 'stakeholders') {
        if (id && window.ProcessStakeholderEdit) {
            window.ProcessStakeholderEdit.init(id, editViewMode);
        }
    }
    
    // Load components data when switching to components tab
    if (tabName === 'components') {
        const container = document.getElementById('processComponentsContainer');
        if (id && container) {
            container.style.display = 'block';
            loadProcessComponents(id, container);
        }
    }

    // Restore form data for the new tab
    restoreFormData(tabName);
}

function setupEventListeners() {
    const id = parseId();

    const saveBtn = document.getElementById('saveBtn');
    const saveCloseBtn = document.getElementById('saveAndCloseBtn');
    const closeBtn = document.getElementById('closeBtn');

    if (saveBtn) saveBtn.addEventListener('click', () => saveProcess(id, false));
    if (saveCloseBtn) saveCloseBtn.addEventListener('click', () => saveProcess(id, true));

    // Show editor buttons – advanced rich text editor
    const showDescEditorBtn = document.getElementById('showDescEditorBtn');
    if (showDescEditorBtn) {
        showDescEditorBtn.addEventListener('click', function(e) {
            e.preventDefault();
            e.stopPropagation();
            toggleAdvancedRichTextEditor('processDescription', showDescEditorBtn);
        });
    }
    const showInputEditorBtn = document.getElementById('showInputEditorBtn');
    if (showInputEditorBtn) {
        showInputEditorBtn.addEventListener('click', function(e) {
            e.preventDefault();
            e.stopPropagation();
            toggleAdvancedRichTextEditor('inputDescription', showInputEditorBtn);
        });
    }
    const showOutputEditorBtn = document.getElementById('showOutputEditorBtn');
    if (showOutputEditorBtn) {
        showOutputEditorBtn.addEventListener('click', function(e) {
            e.preventDefault();
            e.stopPropagation();
            toggleAdvancedRichTextEditor('outputDescription', showOutputEditorBtn);
        });
    }
    if (closeBtn) closeBtn.addEventListener('click', async () => {
        if (window.currentLockManager) {
            await window.currentLockManager.releaseLock();
        }
        if (id) {
            window.location.href = `/view/process/${id}`;
        } else {
            window.location.href = '/view/process/';
        }
    });
    
            // Save & Submit button handler - for edit workflow on existing objects
            const saveSubmitBtn = document.getElementById('saveAndSubmitBtn');
            if (id && saveSubmitBtn) {
                saveSubmitBtn.addEventListener('click', async () => {
                    console.log('Save & Submit clicked - will create CR first, then save');
                    const processId = id;
                    const processName = document.getElementById('processName')?.value || 'Process';
                    
                    try {
                        // First check if CR already exists
                        let crExists = false;
                        let existingCrId = null;
                        try {
                            const statusRes = await fetch(`/api/pending-changes/status/Process/${processId}`, {
                                method: 'GET',
                                credentials: 'include'
                            });
                            if (statusRes.ok) {
                                const statusData = await statusRes.json();
                                crExists = statusData.underRevision === true;
                                existingCrId = statusData.changeRequestId;
                                console.log('CR exists:', crExists, 'CR ID:', existingCrId);
                            }
                        } catch (e) {
                            console.warn('Could not check CR status:', e);
                        }
                        
                        // Create CR first if it doesn't exist
                        if (!crExists) {
                            console.log('No active CR - creating one first');
                            
                            // Get DFCR info for default CR values
                            let dfcrInfo = null;
                            if (window.DFCRUtils) {
                                try {
                                    const processIdForDFCR = parseId();
                                    dfcrInfo = await window.DFCRUtils.getInfo('Process', processIdForDFCR);
                                } catch (e) {
                                    console.warn('Could not get DFCR info:', e);
                                }
                            }
                            
                            const crPayload = {
                                title: `Edit request: ${processName}`,
                                summary: `Auto-generated CR for editing Process: ${processName}`,
                                facetType: 'Process',
                                facetId: String(processId),
                                facetName: processName,
                                type: dfcrInfo?.defaultCrTypeId || 1,
                                urgency: dfcrInfo?.defaultCrUrgencyId || 1,
                                severity: dfcrInfo?.defaultCrSeverityId || 1,
                                mandatoryWorkflow: true
                            };
                            
                            console.log('Creating change request with payload:', crPayload);
                            
                            const crResponse = await fetch('/api/changerequests', {
                                method: 'POST',
                                headers: {
                                    'Content-Type': 'application/json'
                                },
                                credentials: 'include',
                                body: JSON.stringify(crPayload)
                            });
                            
                            if (!crResponse.ok) {
                                const errorText = await crResponse.text();
                                console.error('Failed to create change request:', crResponse.status, errorText);
                                alert('Failed to create change request: ' + errorText);
                                return;
                            }
                            
                            const crResult = await crResponse.json();
                            console.log('Change request created:', crResult);
                            existingCrId = crResult.id || crResult.changeRequestId;
                        }
                        
                        // Now save the process (backend will detect the CR and clone/update)
                        console.log('Saving process with active CR:', existingCrId);
                        const saveSuccess = await saveProcess(processId, false);
                        
                        if (saveSuccess !== false) {
                            alert('Changes saved and submitted for approval.');
                            window.location.href = `/view/process/${processId}`;
                        }
                    } catch (error) {
                        console.error('Error in Save & Submit:', error);
                        alert('Error: ' + error.message);
                    }
                });
            }

    // Tab switching
    const tabs = document.querySelectorAll('.tab');
    tabs.forEach(tab => {
        tab.addEventListener('click', () => {
            const tabName = tab.dataset.tab;
            switchTab(tabName);
        });
    });

    // Form change detection
    const formElements = document.querySelectorAll('input, textarea, select');
    formElements.forEach(element => {
        element.addEventListener('change', markAsDirty);
        element.addEventListener('input', markAsDirty);
    });

    // Parent selection modal
    document.getElementById('selectParentBtn')?.addEventListener('click', selectParent);
    document.getElementById('closeParentModal')?.addEventListener('click', closeParentSelectionModal);
    document.getElementById('cancelParentSelection')?.addEventListener('click', closeParentSelectionModal);
    document.getElementById('parentSearchInput')?.addEventListener('input', function(e) {
        filterProcesses(e.target.value);
    });
    document.getElementById('parentSelectionModal')?.addEventListener('click', function(e) {
        if (e.target === this) {
            closeParentSelectionModal();
        }
    });
}

// Global variables for dirty state
let isDirty = false;
let preventUnloadWarning = false;

window.removeEventListener('beforeunload', window.processEditBeforeUnload);

window.processEditBeforeUnload = function(e) {
    if (document.body.classList.contains('dirty') && !preventUnloadWarning) {
        e.preventDefault();
        e.returnValue = '';
    }
};

window.addEventListener('beforeunload', window.processEditBeforeUnload);

// Parent selection functionality
let currentProcessId = null;
let allProcesses = [];
let filteredProcesses = [];

// Normalize process identifiers - use exact schema field names
function getProcessId(process) {
    const id = process?.id ?? process?.ID ?? process?.Id ?? null;
    console.log('getProcessId for process:', process);
    console.log('Process ID found:', id);
    return id;
}

function getProcessParentId(process) {
    // Check both parentid and parent_id fields
    const parentId = process?.parentid ?? process?.parent_id ?? process?.parentId ?? process?.ParentID ?? process?.Parent_ID ?? null;
    console.log('getProcessParentId for process:', process);
    console.log('Parent ID found:', parentId);
    // Return null if parentId is 0 (no parent)
    const result = (parentId === 0 || parentId === '0') ? null : parentId;
    console.log('Final parent ID result:', result);
    return result;
}

function isDescendantOf(process, ancestorId, allProcesses) {
    const processId = getProcessId(process);
    const parentId = getProcessParentId(process);
    
    console.log(`isDescendantOf: process ${processId}, parent ${parentId}, ancestor ${ancestorId}`);
    
    if (parentId == null) {
        console.log(`  No parent - not descendant`);
        return false;
    }
    
    if (String(parentId) === String(ancestorId)) {
        console.log(`  Direct child of ancestor`);
        return true;
    }
    
    const parentProcess = allProcesses.find(p => String(getProcessId(p)) === String(parentId));
    if (!parentProcess) {
        console.log(`  Parent process not found`);
        return false;
    }
    
    console.log(`  Checking parent process ${getProcessId(parentProcess)}`);
    const result = isDescendantOf(parentProcess, ancestorId, allProcesses);
    console.log(`  Result for process ${processId}: ${result}`);
    return result;
}

async function selectParent() {
    try {
        currentProcessId = parseId();
        console.log('=== PARENT SELECTION DEBUG START ===');
        console.log('Opening parent selection for process ID:', currentProcessId);

        // Try multiple API endpoints to get complete process data with parent_id
        let allProcesses = null;
        let apiSource = '';
        
        // Try: getProcessParentOptions (server-side filtering)
        try {
            const parentOptions = await window.BUDG_API_SERVICE.getProcessParentOptions(currentProcessId);
            console.log('Parent options (server-filtered):', parentOptions);
            console.log('Parent options type:', typeof parentOptions);
            console.log('Parent options length:', parentOptions?.length);
            
            if (Array.isArray(parentOptions) && parentOptions.length > 0) {
                console.log('Parent options sample:', parentOptions[0]);
                console.log('Parent options keys:', Object.keys(parentOptions[0] || {}));
                const hasParentId = parentOptions.some(p => p.hasOwnProperty('parentid') || p.hasOwnProperty('parent_id'));
                console.log('Has parent ID in server response:', hasParentId);
                if (hasParentId) {
                    allProcesses = parentOptions;
                    apiSource = 'getProcessParentOptions';
                    console.log('✅ Using getProcessParentOptions with parent data');
                } else {
                    console.log('Server response missing parent data - falling back to full process list');
                }
            }
        } catch (e) {
            console.warn('getProcessParentOptions failed or not available, falling back to all processes', e);
        }
        
        // Fallback: UnisonSearch endpoint (if API helper exists)
        if (!allProcesses) {
            try {
                if (window.BUDG_API_SERVICE && typeof window.BUDG_API_SERVICE.getUnisionSearchData === 'function') {
                    allProcesses = await window.BUDG_API_SERVICE.getUnisionSearchData('process');
                    console.log('Using UnisionSearch process data for hierarchy checking');
                    console.log('UnisionSearch data sample:', allProcesses[0]);
                    console.log('UnisionSearch data keys:', allProcesses[0] ? Object.keys(allProcesses[0]) : 'No data');
                    apiSource = 'getUnisionSearchData';
                } else if (window.BUDG_API_SERVICE && typeof window.BUDG_API_SERVICE.getUnisonSearchData === 'function') {
                    allProcesses = await window.BUDG_API_SERVICE.getUnisonSearchData('process');
                    console.log('Using UnisonSearch process data for hierarchy checking');
                    console.log('UnisonSearch data sample:', allProcesses[0]);
                    console.log('UnisonSearch data keys:', allProcesses[0] ? Object.keys(allProcesses[0]) : 'No data');
                    apiSource = 'getUnisonSearchData';
                } else {
                    console.warn('UnisonSearch API helper not available, skipping this fallback');
                }
            } catch (e) {
                console.warn('UnisonSearch fallback failed:', e);
            }
        }
        
        // Fallback: getAllProcesses endpoint
        if (!allProcesses) {
            try {
                allProcesses = await window.BUDG_API_SERVICE.getAllProcesses();
                console.log('Using getAllProcesses data');
                console.log('All processes sample:', allProcesses[0]);
                console.log('All processes keys:', allProcesses[0] ? Object.keys(allProcesses[0]) : 'No data');
                apiSource = 'getAllProcesses';
            } catch (e) {
                console.warn('getAllProcesses failed:', e);
            }
        }
        
        if (!allProcesses) {
            throw new Error('Failed to load processes from any API endpoint');
        }
        
        console.log(`Using API source: ${apiSource}`);
        console.log('All processes loaded:', allProcesses);
        console.log('All processes count:', allProcesses.length);
        
        // Apply client-side safety filter to exclude current and descendants
        const hasParentIdData = allProcesses.some(p => getProcessParentId(p) !== null);
        console.log('Has parent ID data:', hasParentIdData);
        
        if (!hasParentIdData) {
            console.warn('⚠️ WARNING: No parent data available - using basic filtering only');
            filteredProcesses = allProcesses.filter(process => {
                const pid = getProcessId(process);
                console.log(`Basic filtering: process ${pid}, current ${currentProcessId}`);
                return String(pid) !== String(currentProcessId);
            });
        } else {
            // Full hierarchy checking
            console.log('Applying full hierarchy checking');
            filteredProcesses = allProcesses.filter(process => {
                const pid = getProcessId(process);
                console.log(`Checking process ${pid} for filtering`);
                
                if (String(pid) === String(currentProcessId)) {
                    console.log(`  Excluding current process ${pid}`);
                    return false;
                }
                
                const isDescendant = isDescendantOf(process, currentProcessId, allProcesses);
                console.log(`  Process ${pid} is descendant: ${isDescendant}`);
                return !isDescendant;
            });
        }
        
        console.log('Filtered processes count:', filteredProcesses.length);
        console.log('Filtered processes sample:', filteredProcesses[0]);
        console.log('Filtered processes details:', filteredProcesses.map(p => ({
            id: getProcessId(p),
            name: p.primaryname || p.PrimaryName || p.name || p.Name,
            parentId: getProcessParentId(p)
        })));
        
        console.log('=== PARENT SELECTION DEBUG END ===');
        showParentSelectionModal();
    } catch (error) {
        console.error('Error loading processes for parent selection:', error);
        alert('Failed to load processes: ' + error.message);
    }
}

function showParentSelectionModal() {
    console.log('=== SHOWING PARENT SELECTION MODAL ===');
    console.log('Filtered processes for modal:', filteredProcesses);
    console.log('Filtered processes count:', filteredProcesses.length);
    
    const modal = document.getElementById('parentSelectionModal');
    if (modal) {
        modal.style.display = 'flex';
        renderProcesses(filteredProcesses);
    } else {
        console.error('Parent selection modal not found');
    }
}

function renderProcesses(processes) {
    console.log('=== RENDERING PROCESSES ===');
    console.log('Processes to render:', processes);
    console.log('Processes count:', processes.length);
    
    const container = document.getElementById('processesList');
    if (!container) {
        console.error('Processes list container not found');
        return;
    }
    
    container.innerHTML = '';
    
    if (processes.length === 0) {
        container.innerHTML = '<div class="no-processes">No processes available</div>';
        return;
    }
    
    processes.forEach(process => {
        const processItem = document.createElement('div');
        processItem.className = 'process-item';
        processItem.style.cssText = `
            padding: 12px 16px;
            border-bottom: 1px solid var(--border-color);
            cursor: pointer;
            transition: background-color 0.2s;
        `;
        
        // Get process name from SerializedName fields
        const processName = process.primaryname || process.PrimaryName || process.name || process.Name || 'Unnamed Process';
        const processDescription = process.description || process.Description || '';
        console.log('Rendering process:', process);
        console.log('Process name for display:', processName);
        console.log('Process description for display:', processDescription);
        
        const processNameDiv = document.createElement('div');
        processNameDiv.className = 'process-name';
        processNameDiv.style.cssText = 'font-weight: 500; color: var(--text-primary);';
        processNameDiv.textContent = processName;
        
        const processDescriptionDiv = document.createElement('div');
        processDescriptionDiv.className = 'process-description';
        processDescriptionDiv.style.cssText = 'font-size: 12px; color: var(--text-secondary); margin-top: 4px;';
        processDescriptionDiv.textContent = processDescription;
        
        processItem.appendChild(processNameDiv);
        processItem.appendChild(processDescriptionDiv);
        
        processItem.addEventListener('click', () => {
            selectProcessAsParent(process);
        });
        
        // Add hover effects
        processItem.addEventListener('mouseenter', () => {
            processItem.style.backgroundColor = '#f9fafb';
        });
        
        processItem.addEventListener('mouseleave', () => {
            processItem.style.backgroundColor = '';
        });
        
        container.appendChild(processItem);
    });
}

function selectProcessAsParent(process) {
    console.log('Selected parent process:', process);
    console.log('Process keys:', Object.keys(process));
    console.log('Process primaryname:', process.primaryname);
    console.log('Process PrimaryName:', process.PrimaryName);
    console.log('Process name:', process.name);
    console.log('Process Name:', process.Name);
    console.log('Process description:', process.description);
    console.log('Process Description:', process.Description);
    
    const parentNameInput = document.getElementById('parentName');
    const processName = process.primaryname || process.PrimaryName || process.name || process.Name || 'Unnamed Process';
    const processDescription = process.description || process.Description || '';
    
    console.log('Final process name to display:', processName);
    console.log('Final process description to display:', processDescription);
    
    if (parentNameInput) {
        // Display both name and description in the input field
        const displayText = processDescription ? `${processName} - ${processDescription}` : processName;
        parentNameInput.value = displayText;
        parentNameInput.dataset.parentId = getProcessId(process);
        parentNameInput.dataset.parentName = processName;
        parentNameInput.dataset.parentDescription = processDescription;
        console.log('Set parent display text in input:', displayText);
        console.log('Set parent ID in dataset:', getProcessId(process));
        console.log('Set parent name in dataset:', processName);
        console.log('Set parent description in dataset:', processDescription);
    }
    
    closeParentSelectionModal();
    markAsDirty();
}

function closeParentSelectionModal() {
    const modal = document.getElementById('parentSelectionModal');
    if (modal) {
        modal.style.display = 'none';
    }
    const searchInput = document.getElementById('parentSearchInput');
    if (searchInput) {
        searchInput.value = '';
    }
}

async function refreshProcessParentForSelectedSegment(previousSegmentId) {
    const currentProcessId = parseId();
    if (!currentProcessId) return;

    let candidates = [];
    try {
        const parentOptions = await window.BUDG_API_SERVICE.getProcessParentOptions(currentProcessId);
        candidates = Array.isArray(parentOptions?.data) ? parentOptions.data : (Array.isArray(parentOptions) ? parentOptions : []);
    } catch (_) {
        candidates = [];
    }

    const parentNameInput = document.getElementById('parentName');
    const selectedParentId = parseInt(parentNameInput?.dataset?.parentId || '', 10);
    if (!Number.isInteger(selectedParentId) || selectedParentId <= 0) {
        return;
    }

    const allowedParentIds = new Set(
        candidates
            .map(p => parseInt(getProcessId(p), 10))
            .filter(id => Number.isInteger(id) && id > 0)
    );

    if (!allowedParentIds.has(selectedParentId)) {
        alert('This parent is not valid for the selected segment. Please remove the parent first.');
        return false;
    }
    return true;
}

function filterProcesses(searchTerm) {
    console.log('=== FILTERING PROCESSES ===');
    console.log('Search term:', searchTerm);
    console.log('Filtered processes before filtering:', filteredProcesses);
    
    const filtered = filteredProcesses.filter(process => {
        const name = (process.primaryname || process.PrimaryName || process.name || process.Name || '').toLowerCase();
        const description = (process.description || process.Description || '').toLowerCase();
        const search = searchTerm.toLowerCase();
        
        const matches = name.includes(search) || description.includes(search);
        console.log('Process:', process, 'name:', name, 'description:', description, 'matches:', matches);
        return matches;
    });
    
    console.log('Filtered processes after filtering:', filtered);
    renderProcesses(filtered);
}


// Initialize automatic reference generation for process
async function initAutoReferenceGeneration(options = {}) {
    const markDirty = !!options.markDirty;
    const refInput = document.getElementById('processRef');
    if (!refInput) return;
    if ((refInput.value || '').trim()) return;
    if (refInput.dataset.autoRefLoading === '1') return;

    refInput.dataset.autoRefLoading = '1';
    try {
        let generatedRef = '';
        if (window.BUDG_API_SERVICE && typeof window.BUDG_API_SERVICE.getNextProcessRefNumber === 'function') {
            const refResp = await window.BUDG_API_SERVICE.getNextProcessRefNumber();
            generatedRef =
                refResp?.refnumber ||
                refResp?.refNumber ||
                refResp?.nextRef ||
                refResp?.data?.refnumber ||
                refResp?.data?.refNumber ||
                '';
        }
        if (!generatedRef) return;
        if ((refInput.value || '').trim()) return; // User may have typed while request was in-flight

        refInput.value = generatedRef;
        refInput.dataset.autoGenerated = 'true';
        if (markDirty) {
            markAsDirty();
        }
    } catch (error) {
        console.warn('Failed to auto-generate process ref number:', error);
    } finally {
        delete refInput.dataset.autoRefLoading;
    }
}

async function initializePage() {
    const id = parseId();
    console.log('Initializing process edit page with ID:', id);
    
    try {
        console.log('=== INITIALIZING PAGE ===');
        console.log('Loading lookups and process data...');
        // Initialize segment field FIRST so segmentField is ready when loadProcess runs
        if (window.SegmentField) {
            try {
                segmentField = await SegmentField.init('segmentFieldContainer', {
                    label: 'Segment',
                    required: true,
                    sectionTitle: 'SEGMENTATION',
                    objectType: 'Process',
                    fieldId: 'processSegment',
                    errorId: 'processSegmentError',
                    onChange: async (selectedSegmentId, previousSegmentId) => {
                        return await refreshProcessParentForSelectedSegment(previousSegmentId);
                    }
                });
                console.log('Segment field initialized');
            } catch (error) {
                console.error('Error initializing segment field:', error);
            }
        }

        // Load lookups first, then process data
        console.log('Step 1: Loading lookups...');
        await loadProcessEditLookups();

        if (!id) {
            await initAutoReferenceGeneration();
            setupEventListeners();
            if (window.CustomFields) {
                try {
                    window.customFieldsContext = await window.CustomFields.initForm({
                        facetId: 'Process',
                        containerId: 'customFieldsContainer',
                        mode: 'create',
                        objectId: null
                    });
                    console.log('Custom fields initialized (create):', window.customFieldsContext);
                } catch (error) {
                    console.error('Error initializing custom fields:', error);
                }
            }
            handleTabParameter();
            console.log('Process create page initialized successfully');
            return;
        }

        // Under active CR, edit page must load pending (nobject_id) data
        editViewMode = await determineEditViewMode(id);
        console.log('[Process Edit] View mode:', editViewMode);
        console.log('Step 2: Loading process data...');
        await loadProcess(id, editViewMode);
        
        // Initialize Impact tab if available
        if (window.initImpactEdit) {
            console.log('Step 3: Initializing Impact tab...');
            window.currentImpactSegmentId = segmentField ? segmentField.getValue() : (window.currentImpactSegmentId || 1);
            window.initImpactEdit(id, editViewMode);
        }
        
        console.log('Setting up event listeners...');
        // Setup event listeners after data is loaded
        setupEventListeners();
        
        // Initialize custom fields
        if (window.CustomFields) {
            try {
                window.customFieldsContext = await window.CustomFields.initForm({
                    facetId: 'Process',
                    containerId: 'customFieldsContainer',
                    mode: 'edit',
                    objectId: id
                });
                console.log('Custom fields initialized:', window.customFieldsContext);
            } catch (error) {
                console.error('Error initializing custom fields:', error);
            }
        }
        
        console.log('Handling tab parameter...');
        handleTabParameter();
        
        console.log('Process edit page initialized successfully');
    } catch (error) {
        console.error('Error initializing process edit page:', error);
    }
}

function handleTabParameter() {
    const urlParams = new URLSearchParams(window.location.search);
    const tabParam = urlParams.get('tab');
    const isStakeholderOnly = urlParams.get('stakeholderOnly') === 'true';
    
    if (tabParam) {
        console.log('Tab parameter found:', tabParam);
        
        // Check if the tab exists in the edit page
        const targetTab = document.querySelector(`[data-tab="${tabParam}"]`);
        if (targetTab) {
            console.log('Switching to tab:', tabParam);
            // Small delay to ensure everything is loaded
            setTimeout(() => {
                switchTab(tabParam);
            }, 100);
        } else {
            console.log('Tab not found in edit page:', tabParam);
        }
    }
    
    // Stakeholder-only mode: disable all other tabs when opened from view page with auto CR active
    if (isStakeholderOnly && tabParam === 'stakeholders') {
        console.log('[Process Edit] Stakeholder-only mode enabled - locking other tabs');
        document.querySelectorAll('.tab').forEach(function(tab) {
            if (tab.getAttribute('data-tab') !== 'stakeholders') {
                tab.disabled = true;
                tab.classList.add('disabled');
                tab.style.opacity = '0.4';
                tab.style.cursor = 'not-allowed';
                tab.style.pointerEvents = 'none';
                tab.title = 'Only stakeholder editing is allowed during an active Auto CR';
            }
        });
    }
}

