// System Page JavaScript
document.addEventListener('DOMContentLoaded', async function() {
    const saveBtn = document.getElementById('saveBtn');
    const saveAndCloseBtn = document.getElementById('saveAndCloseBtn');
    const closeBtn = document.getElementById('closeBtn');
    const form = document.getElementById('systemForm');

    if (saveBtn) {
        saveBtn.addEventListener('click', function() {
            if (validateForm()) { savePage(true); }
        });
    }

    if (saveAndCloseBtn) {
        saveAndCloseBtn.addEventListener('click', function() {
            if (validateForm()) { savePage(true); }
        });
    }

    if (closeBtn) {
        closeBtn.addEventListener('click', function() {
            closePage();
        });
    }

    if (form) {
        form.addEventListener('submit', function(e){ e.preventDefault(); });
    }

    // Load system form dropdowns (avoid clashing with global header dropdowns)
    initSystemFormDropdowns().then(() => {
        // Apply DFCR locked fields for System facet (create mode ONLY)
        // Only apply if this is actually a create page (not an edit page)
        const url = window.location.href.toLowerCase();
        const pathname = window.location.pathname.toLowerCase();
        const isEditPage = pathname.includes('-edit.html') || pathname.includes('/edit') || 
                          url.includes('tab=edit') || url.includes('?edit') || url.includes('&edit') ||
                          pathname.includes('/view/system/');
        
        if (!isEditPage && window.DFCRUtils) {
            window.DFCRUtils.applyLockedFields('System', {
                status: '#BUDGStatus',
                lifecycle: '#lifecycle'
            }).then(() => {
                console.log('DFCR locked fields applied for System create page');
            }).catch(dfcrError => {
                console.warn('Error applying DFCR locked fields:', dfcrError);
            });
        } else {
            console.log('Skipping DFCR locked fields for System (edit page detected)');
        }
    });

    // Fetch current user data on page load
    fetchCurrentUser();
    
    // Initialize segment field
    try {
        if (window.SegmentField) {
            window.segmentField = await SegmentField.init('segmentFieldContainer', {
                label: 'Segment',
                required: true,
                defaultValue: 1,
                sectionTitle: 'SEGMENTATION',
                objectType: 'System',
                fieldId: 'systemSegment',
                errorId: 'systemSegmentError',
                onChange: async () => {
                    try {
                        const parentEl = document.getElementById('parentShortName');
                        const previousParentId = parentEl?.dataset?.parentId ? parseInt(parentEl.dataset.parentId, 10) : null;
                        await initSystemFormDropdowns();
                        if (previousParentId && Number.isInteger(previousParentId)) {
                            const stillAllowed = getFilteredSystemParentOptions()
                                .some(s => parseInt(s.id || s.ID, 10) === previousParentId);
                            if (!stillAllowed && parentEl) {
                                alert('This parent is not valid for the selected segment. Please remove the parent first.');
                                return false;
                            }
                        }
                    } catch (e) {
                        console.error('Failed to refresh system parent options on segment change', e);
                    }
                }
            });
            console.log('Segment field initialized');
            // Rebuild parent options with the active segment value.
            // The first dropdown initialization runs before SegmentField exists.
            await initSystemFormDropdowns();
        }
    } catch (error) {
        console.error('Error initializing segment field:', error);
    }

    // Initialize custom fields
    if (window.CustomFields) {
        try {
            window.customFieldsContext = await window.CustomFields.initForm({
                facetId: 'System',
                containerId: 'customFieldsContainer',
                mode: 'create',
                objectId: null
            });
            console.log('Custom fields initialized:', window.customFieldsContext);
        } catch (error) {
            console.error('Error initializing custom fields:', error);
        }
    }

    // Advanced Rich Text Editor toggle for description
    const showDescriptionEditorBtn = document.getElementById('showDescriptionEditorBtn');
    if (showDescriptionEditorBtn) {
        showDescriptionEditorBtn.addEventListener('click', function() {
            window.toggleAdvancedRichTextEditor('description', showDescriptionEditorBtn);
        });
    }

});

// Get current user ID from session storage
function getCurrentUserId() {
    const userStr = sessionStorage.getItem('currentUser');
    if (userStr) {
        try {
            const user = JSON.parse(userStr);
            if (user && (user.id || user.ID || user.userId)) {
                return user.id || user.ID || user.userId;
            }
        } catch (e) {
            console.error('Error parsing user from session:', e);
        }
    }
    console.warn('⚠️ User ID not found in session storage');
    return null;
}

// Fetch current user from API and cache in session storage
async function fetchCurrentUser() {
    try {
        const response = await fetch('/api/me');
        if (response.ok) {
            const userData = await response.json();
            sessionStorage.setItem('currentUser', JSON.stringify(userData));
            console.log('✅ User data cached:', userData);
        } else {
            console.warn('⚠️ Failed to fetch user data');
        }
    } catch (error) {
        console.error('❌ Error fetching user data:', error);
    }
}

async function savePage(closeAfterSave) {
    try {
        // Sync advanced rich text editor content to textarea before saving
        window.syncAdvancedRichTextToTextarea('description');

        // Validate custom fields
        if (window.customFieldsContext && window.customFieldsContext.validate) {
            if (!window.customFieldsContext.validate()) {
                return;
            }
        }

        const buttons = [document.getElementById('saveBtn'), document.getElementById('saveAndCloseBtn')];
        buttons.forEach(btn => { if (btn) { btn.disabled = true; btn.textContent = I18n.t('createPage.message.saving'); } });

        const payload = await collectFormData();

        // Enforce parent/segment hierarchy before save to avoid creating invalid objects.
        if (Number.isInteger(payload.parent_id) && payload.parent_id > 0 && Number.isInteger(payload.segmentId) && payload.segmentId > 0) {
            let isHierarchyValid = true;
            try {
                if (window.segmentField && typeof window.segmentField.validateParentForSegment === 'function') {
                    isHierarchyValid = await window.segmentField.validateParentForSegment(payload.parent_id, payload.segmentId);
                } else {
                    const hierarchyResponse = await fetch('/api/segments/validate-hierarchy', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        credentials: 'include',
                        body: JSON.stringify({
                            parentId: payload.parent_id,
                            childSegmentId: payload.segmentId,
                            objectType: 'System'
                        })
                    });
                    if (hierarchyResponse.ok) {
                        const hierarchyResult = await hierarchyResponse.json();
                        isHierarchyValid = !!(hierarchyResult && hierarchyResult.isValid !== false);
                    }
                }
            } catch (validationError) {
                console.warn('Segment hierarchy pre-validation failed, will continue to server validation:', validationError);
            }

            if (!isHierarchyValid) {
                alert('This parent is not valid for the selected segment. Please remove the parent first.');
                buttons.forEach(btn => { if (btn) { btn.disabled = false; btn.textContent = btn.id === 'saveBtn' ? 'Save' : 'Save & Close'; } });
                return;
            }
        }

        // Helper to perform actual save
        const runSave = () => window.BUDG_API_SERVICE.saveSystem(payload)
            .then(async (resp) => {
                const systemId = resp?.id || resp?.data?.id || resp?.systemId || resp?.insertId || resp?.createdId;
                // Save custom fields
                if (window.customFieldsContext && window.customFieldsContext.saveValues && systemId) {
                    try {
                        await window.customFieldsContext.saveValues(systemId);
                        console.log('✅ Custom fields saved successfully');
                    } catch (cfError) {
                        console.error('Error saving custom fields:', cfError);
                    }
                }
                const savedMsg = I18n.t('createPage.message.systemSaved');
                if (typeof window.showNotification === 'function') { window.showNotification(savedMsg, 'success'); } else { alert(savedMsg); }

                if (closeAfterSave) {
                    const id = resp?.id || resp?.data?.id || resp?.systemId || resp?.insertId || resp?.createdId;
                    if (id != null) {
                        window.location.href = `/view/system/${encodeURIComponent(id)}`;
                    } else if (payload && payload.name) {
                        // Try to resolve by name if API didn't return id
                        try {
                            window.BUDG_API_SERVICE.getSystemsList().then(list => {
                                const arr = Array.isArray(list?.data) ? list.data : Array.isArray(list) ? list : [];
                                const found = arr.find(s => String(s.name||'').toLowerCase() === String(payload.name||'').toLowerCase());
                                if (found && found.id != null) window.location.href = `/view/system/${encodeURIComponent(found.id)}`; else window.location.href = 'index.html';
                            }).catch(()=>{ window.location.href = 'index.html'; });
                        } catch(_) { window.location.href = 'index.html'; }
                    } else {
                        window.location.href = 'index.html';
                    }
                } else {
                    // Keep form data - user can continue editing or manually clear if needed
                    console.log('✅ System saved. Form data preserved for continued editing.');
                }
            })
            .catch(err => {
                const serverMsg = err?.body?.error || err?.body?.message || err?.message;
                const serverMsgLower = String(serverMsg || '').toLowerCase();
                if (serverMsgLower.includes('segment') && (serverMsgLower.includes('parent') || serverMsgLower.includes('hierarchy') || serverMsgLower.includes('not valid'))) {
                    alert('This parent is not valid for the selected segment. Please remove the parent first.');
                    return;
                }
                const errInfo = window.getCreateSaveErrorInfo ? window.getCreateSaveErrorInfo(serverMsg, 'Systems') : null;
                if (errInfo) {
                    const errMsg = I18n.t(errInfo.key, errInfo.params);
                    if (typeof window.showNotification === 'function') { window.showNotification(errMsg, 'error'); } else { alert(errMsg); }
                    if (errInfo.focus === 'name') document.getElementById('shortName')?.focus(); else if (errInfo.focus === 'reference') document.getElementById('name')?.focus();
                } else {
                    const errMsg = I18n.t('createPage.message.failedToSaveWithHint', { facet: 'Systems' });
                    if (typeof window.showNotification === 'function') { window.showNotification(errMsg, 'error'); } else { alert(errMsg); }
                }
            })
            .finally(() => {
                buttons.forEach(btn => { if (btn) { btn.disabled = false; btn.textContent = btn.id === 'saveBtn' ? 'Save' : 'Save & Close'; } });
            });

        runSave();
    } catch (e) {
        console.error(e);
        const serverMessage = e?.message || (e?.body && (e.body.error || e.body.message));
        const serverMsgLower = String(serverMessage || '').toLowerCase();
        if (serverMsgLower.includes('segment') && (serverMsgLower.includes('parent') || serverMsgLower.includes('hierarchy') || serverMsgLower.includes('not valid'))) {
            alert('This parent is not valid for the selected segment. Please remove the parent first.');
            return;
        }
        const errInfo = window.getCreateSaveErrorInfo ? window.getCreateSaveErrorInfo(serverMessage, 'Systems') : null;
        if (errInfo) {
            const errMsg = I18n.t(errInfo.key, errInfo.params);
            if (typeof window.showNotification === 'function') { window.showNotification(errMsg, 'error'); } else { alert(errMsg); }
            if (errInfo.focus === 'name') document.getElementById('shortName')?.focus(); else if (errInfo.focus === 'reference') document.getElementById('name')?.focus();
        } else {
            const errMsg = I18n.t('createPage.message.errorSaving', { error: serverMessage || I18n.t('createPage.message.unknownError') });
            if (typeof window.showNotification === 'function') { window.showNotification(errMsg, 'error'); } else { alert(errMsg); }
        }
    }
}

function closePage() {
    const hasData = document.querySelector('input, textarea, select') && Array.from(document.querySelectorAll('input, textarea, select')).some(el => (el.value || '').trim());
    if (hasData) {
        const confirmClose = confirm('You have unsaved changes. Are you sure you want to cancel without saving?');
        if (!confirmClose) return;
    }
    window.location.href = 'index.html';
}

function setError(elementId, message) {
    const span = document.getElementById(elementId);
    if (span) {
        span.textContent = message || '';
        span.style.display = message ? 'block' : 'none';
    }
}

function validateForm() {
    let valid = true;
    const shortName = document.getElementById('shortName');
    const description = document.getElementById('description');
    const typeSelect = document.getElementById('typeSelect');
    const BUDGStatus = document.getElementById('BUDGStatus');
    const lifecycle = document.getElementById('lifecycle');
    const BUDGViewing = document.getElementById('BUDGViewing');

    if (!shortName || !description || !typeSelect || !BUDGStatus || !lifecycle || !BUDGViewing) return true;

    setError('shortNameError', '');
    setError('descriptionError', '');
    setError('typeError', '');
    setError('BUDGStatusError', '');
    setError('lifecycleError', '');
    setError('BUDGViewingError', '');

    if (!shortName.value.trim()) { setError('shortNameError', 'Short Name is required'); valid = false; }
    if (!description.value.trim()) { setError('descriptionError', 'Description is required'); valid = false; }

    // Fix Type validation - check if value exists and is not empty
    if (!typeSelect.value || typeSelect.value.trim() === '') {
        setError('typeError', 'Type is required');
        valid = false;
    }
    if (!BUDGStatus.value.trim()) { setError('BUDGStatusError', 'BUDG Status is required'); valid = false; }
    if (!lifecycle.value.trim()) { setError('lifecycleError', 'Lifecycle is required'); valid = false; }
    if (!BUDGViewing.value.trim()) { setError('BUDGViewingError', 'BUDG Viewing is required'); valid = false; }

    return valid;
}

async function initSystemFormDropdowns() {
    const svc = window.BUDG_API_SERVICE;
    if (!svc) return Promise.resolve();
    const selectedSegmentId = getActiveSystemSegmentId();
    return Promise.all([
        svc.getSystemsList(Number.isInteger(selectedSegmentId) && selectedSegmentId > 0 ? { segmentId: selectedSegmentId } : {}),
        svc.getStatusList(),
        svc.getLifecycleList(),
        svc.getSystemTypeList?.() ? svc.getSystemTypeList() : Promise.resolve([]),
        svc.getViewingList(),
        svc.getCiaRatings(),
        svc.getSystemClassifications()
    ]).then(async ([systems, statuses, lifecycles, systemTypes, viewings, ciaRatings, classifications]) => {

        fillSelect('BUDGStatus', statuses, 'name');
        fillSelectWithoutEmpty('lifecycle', lifecycles, 'name'); // Lifecycle without empty option
        fillSelect('typeSelect', systemTypes, 'name');
        fillSelect('BUDGViewing', viewings, 'name');
        fillSelect('classification', classifications, 'name');
        fillCia('ciaC', ciaRatings);
        fillCia('ciaI', ciaRatings);
        fillCia('ciaA', ciaRatings);
        const enrichedSystems = await enrichSystemParentSegmentInfo(systems);
        setupParentPicker(enrichedSystems);

        // CREATE page only: default first list item. On system-edit, loadSystem() already set values;
        // re-running this (e.g. after segment/parent refresh) must NOT stomp Type, Scope, Status, etc.
        const isSystemEditPage = document.getElementById('systemEditContainer') != null
            || (typeof window.location !== 'undefined' && window.location.pathname
                && window.location.pathname.indexOf('system-edit') !== -1);

        if (!isSystemEditPage) {
            // Defaults: Type, BUDG Status, BUDG Viewing, Lifecycle → first item; CIA → blank (already blank)
            if (Array.isArray(systemTypes) && systemTypes.length > 0) {
                const el = document.getElementById('typeSelect');
                if (el) el.value = String(systemTypes[0].id);
            }
            if (Array.isArray(statuses) && statuses.length > 0) {
                const el = document.getElementById('BUDGStatus');
                if (el) el.value = String(statuses[0].id);
            }
            if (Array.isArray(lifecycles) && lifecycles.length > 0) {
                const el = document.getElementById('lifecycle');
                if (el) el.value = String(lifecycles[0].id);
            }
            if (Array.isArray(viewings) && viewings.length > 0) {
                const el = document.getElementById('BUDGViewing');
                if (el) el.value = String(viewings[0].id);
            }
        }
    }).catch(err => {
        console.error('Failed to load dropdowns', err);
        throw err;
    });
}

function fillSelect(selectId, list, labelKey) {
    const el = document.getElementById(selectId);
    if (!el || !Array.isArray(list)) return;
    const current = el.value;
    el.innerHTML = '';
    const empty = document.createElement('option');
    empty.value = '';
    el.appendChild(empty);

    // Sort the list by ID to maintain database order
    const sortedList = [...list].sort((a, b) => (a.id || 0) - (b.id || 0));

    sortedList.forEach(item => {
        const opt = document.createElement('option');
        opt.value = item.id;
        opt.textContent = item[labelKey] || item.name || '';
        el.appendChild(opt);
    });
    if (current) el.value = current;
}

function fillSelectWithoutEmpty(selectId, list, labelKey) {
    const el = document.getElementById(selectId);
    if (!el || !Array.isArray(list)) return;
    const current = el.value;
    el.innerHTML = '';
    
    // Sort the list by ID to maintain database order
    const sortedList = [...list].sort((a, b) => (a.id || 0) - (b.id || 0));

    sortedList.forEach(item => {
        const opt = document.createElement('option');
        opt.value = item.id;
        opt.textContent = item[labelKey] || item.name || '';
        el.appendChild(opt);
    });
    if (current) el.value = current;
}

function fillSelectByName(selectId, list, labelKey) {
    const el = document.getElementById(selectId);
    if (!el || !Array.isArray(list)) return;
    const current = el.value;
    el.innerHTML = '';
    const empty = document.createElement('option');
    empty.value = '';
    el.appendChild(empty);
    list.forEach(item => {
        const opt = document.createElement('option');
        opt.value = item[labelKey] || item.name || ''; // Use name as value
        opt.textContent = item[labelKey] || item.name || '';
        el.appendChild(opt);
    });
    if (current) el.value = current;
}

function fillCia(selectId, ratings) {
    const el = document.getElementById(selectId);
    if (!el || !Array.isArray(ratings)) return;
    const current = el.value;
    el.innerHTML = '';
    const empty = document.createElement('option');
    empty.value = '';
    empty.textContent = '';
    el.appendChild(empty);
    ratings.forEach(r => {
        const opt = document.createElement('option');
        opt.value = r.id;
        opt.textContent = String(r.values);
        el.appendChild(opt);
    });
    if (current) el.value = current;
}

let parentPickerSystems = [];
const systemParentSegmentCache = new Map();

function getActiveSystemSegmentId() {
    if (window.segmentField && typeof window.segmentField.getValue === 'function') {
        const fromGlobalField = parseInt(window.segmentField.getValue(), 10);
        if (Number.isInteger(fromGlobalField) && fromGlobalField > 0) {
            return fromGlobalField;
        }
    }

    const formSegmentSelect = document.getElementById('sysSegment') || document.getElementById('systemSegment');
    if (formSegmentSelect) {
        const fromSelect = parseInt(formSegmentSelect.value, 10);
        if (Number.isInteger(fromSelect) && fromSelect > 0) {
            return fromSelect;
        }
    }

    const fromUrl = parseInt(new URLSearchParams(window.location.search).get('segmentId'), 10);
    if (Number.isInteger(fromUrl) && fromUrl > 0) {
        return fromUrl;
    }

    return NaN;
}

function extractSystemSegmentId(systemRecord) {
    const direct = parseInt(
        systemRecord?.segmentId ??
        systemRecord?.segment_id ??
        systemRecord?.Segment_ID ??
        systemRecord?.segmentID ??
        systemRecord?.SegmentId,
        10
    );
    if (Number.isInteger(direct) && direct > 0) {
        return direct;
    }

    const nested = parseInt(
        systemRecord?.segment?.id ??
        systemRecord?.segment?.ID ??
        systemRecord?.Segment?.id ??
        systemRecord?.Segment?.ID,
        10
    );
    if (Number.isInteger(nested) && nested > 0) {
        return nested;
    }

    return NaN;
}

function hasSystemSegmentInfo(systemRecord) {
    return Number.isInteger(extractSystemSegmentId(systemRecord));
}

async function enrichSystemParentSegmentInfo(systems) {
    const rows = Array.isArray(systems) ? systems : [];
    const svc = window.BUDG_API_SERVICE;
    if (!svc || typeof svc.getSystemById !== 'function') {
        return rows;
    }

    // Optimization: only enrich when the parent picker UI is actually present (create/edit screens).
    // On pure view pages (like /view/system/{id}) there is no parent picker, so we skip the
    // per-system /api/system/{id} calls to avoid generating dozens of unnecessary requests.
    const hasParentPickerField =
        document.getElementById('parentShortName') ||
        document.getElementById('parentPicker');
    if (!hasParentPickerField) {
        return rows;
    }

    const missingRows = rows.filter((row) => !hasSystemSegmentInfo(row));
    if (!missingRows.length) {
        return rows;
    }

    await Promise.all(missingRows.map(async (row) => {
        const rowId = parseInt(row?.id ?? row?.ID, 10);
        if (!Number.isInteger(rowId) || rowId <= 0) {
            return;
        }

        if (systemParentSegmentCache.has(rowId)) {
            row.segmentId = systemParentSegmentCache.get(rowId);
            return;
        }

        try {
            const details = await svc.getSystemById(rowId);
            const detailsSegmentId = parseInt(
                details?.segmentId ??
                details?.segment_id ??
                details?.Segment_ID ??
                details?.segmentID ??
                details?.SegmentId ??
                details?.segment?.id ??
                details?.segment?.ID,
                10
            );
            if (Number.isInteger(detailsSegmentId) && detailsSegmentId > 0) {
                systemParentSegmentCache.set(rowId, detailsSegmentId);
                row.segmentId = detailsSegmentId;
            }
        } catch (error) {
            console.warn('Unable to resolve parent system segment for ID:', rowId, error);
        }
    }));

    return rows;
}

function getFilteredSystemParentOptions() {
    const selectedSegmentId = getActiveSystemSegmentId();
    const source = Array.isArray(parentPickerSystems) ? parentPickerSystems : [];
    if (!Number.isInteger(selectedSegmentId) || selectedSegmentId <= 0) {
        return source;
    }
    return source.filter((s) => {
        const candidateSegmentId = extractSystemSegmentId(s);
        // Allowed rules:
        // 1) Same segment
        // 2) Any relation where either side is Enterprise (ID=1)
        // 3) If segment is unknown in payload, keep option and let server-side validation decide
        if (!Number.isInteger(candidateSegmentId) || candidateSegmentId <= 0) {
            return true;
        }
        if (candidateSegmentId === selectedSegmentId) {
            return true;
        }
        return candidateSegmentId === 1 || selectedSegmentId === 1;
    });
}

function setupParentPicker(systems) {
    parentPickerSystems = Array.isArray(systems) ? systems : [];
    const input = document.getElementById('parentShortName');
    const trigger = document.getElementById('parentPicker');
    if (!input || !trigger) return;
    // Prevent double-initialization (which would create two search UIs),
    // but refresh already-open panel content with latest segment-filtered data.
    if (input.dataset.pickerInit === '1') {
        const wrapper = input.parentElement;
        const panel = wrapper ? wrapper.querySelector('.parent-picker-panel') : null;
        if (panel && panel.style.display === 'block') {
            const searchInput = panel.querySelector('input');
            if (searchInput) searchInput.dispatchEvent(new Event('input'));
        }
        return;
    }

    // Create dropdown panel (similar to dataset searchable select)
    const wrapper = input.parentElement; // .form-input-group
    if (!wrapper) return;

    const panel = document.createElement('div');
    panel.className = 'parent-picker-panel';
    panel.style.position = 'absolute';
    panel.style.left = '0';
    panel.style.right = '0';
    panel.style.top = 'calc(100% + 6px)';
    panel.style.zIndex = '1000';
    panel.style.background = 'var(--bg-elevated, #fff)';
    panel.style.border = '1px solid var(--border-color, #d0d5dd)';
    panel.style.borderRadius = '8px';
    panel.style.boxShadow = '0 10px 20px rgba(0,0,0,0.08)';
    panel.style.padding = '8px';
    panel.style.display = 'none';

    const searchInput = document.createElement('input');
    searchInput.type = 'text';
    searchInput.placeholder = 'Search systems...';
    searchInput.className = 'form-input';
    searchInput.style.margin = '4px';

    const list = document.createElement('div');
    list.style.maxHeight = '220px';
    list.style.overflowY = 'auto';
    list.style.marginTop = '6px';

    panel.appendChild(searchInput);
    panel.appendChild(list);

    // Ensure relative parent for absolute panel
    wrapper.style.position = 'relative';
    wrapper.appendChild(panel);
    input.dataset.pickerInit = '1';

    function openPanel() {
        if (panel.style.display === 'block') return; // already open
        panel.style.display = 'block';
        searchInput.value = '';
        renderList('');
        setTimeout(() => searchInput.focus(), 0);
    }

    function closePanel() {
        panel.style.display = 'none';
    }

    function renderList(q) {
        const query = (q || '').toLowerCase();
        list.innerHTML = '';
        const data = getFilteredSystemParentOptions();
        // When editing an existing System, exclude options whose parent_id equals
        // the current system's parent_id to avoid selecting siblings as parents per requirement
        const currentParentId = input && input.dataset && input.dataset.parentId ? parseInt(input.dataset.parentId, 10) : null;
        data.forEach((s) => {
            const name = s.name || '';
            const desc = s.description || '';
            const searchable = (name + ' ' + desc).toLowerCase();
            if (query && !searchable.includes(query)) return;
            const candidateParentId = (s.parent_id != null) ? parseInt(s.parent_id, 10) : (s.parentId != null ? parseInt(s.parentId, 10) : null);
            if (currentParentId != null && candidateParentId != null && candidateParentId === currentParentId) return;
            const row = document.createElement('div');
            row.style.display = 'grid';
            row.style.gridTemplateColumns = '1fr 2fr';
            row.style.gap = '8px';
            row.style.padding = '8px 10px';
            row.style.cursor = 'pointer';
            row.style.borderRadius = '6px';
            row.addEventListener('mouseenter', () => { row.style.background = 'var(--gray-50, #f2f4f7)'; });
            row.addEventListener('mouseleave', () => { row.style.background = 'transparent'; });
            row.addEventListener('click', async () => {
                const candidateParentId = parseInt(s.id || s.ID, 10);
                const selectedSegmentId = getActiveSystemSegmentId();
                if (Number.isInteger(candidateParentId) && candidateParentId > 0 &&
                    Number.isInteger(selectedSegmentId) && selectedSegmentId > 0 &&
                    window.segmentField && typeof window.segmentField.validateParentForSegment === 'function') {
                    const isAllowed = await window.segmentField.validateParentForSegment(candidateParentId, selectedSegmentId);
                    if (!isAllowed) {
                        alert('This parent is not valid for the selected segment. Please remove the parent first.');
                        return;
                    }
                }
                input.value = name;
                input.dataset.parentId = String(s.id);
                closePanel();
            });
            const nameDiv = document.createElement('div');
            nameDiv.textContent = name;
            nameDiv.style.fontWeight = '500';
            const descDiv = document.createElement('div');
            descDiv.textContent = desc;
            descDiv.style.opacity = '0.8';
            row.appendChild(nameDiv);
            row.appendChild(descDiv);
            list.appendChild(row);
        });
        if (!list.children.length) {
            const empty = document.createElement('div');
            empty.style.padding = '8px 10px';
            empty.style.opacity = '0.8';
            empty.textContent = 'No systems found';
            list.appendChild(empty);
        }
    }

    // Open on click
    trigger.addEventListener('click', (e) => { e.stopPropagation(); openPanel(); });
    input.addEventListener('click', (e) => { e.stopPropagation(); openPanel(); });
    searchInput.addEventListener('input', (e) => renderList(e.target.value));
    // Close on outside / ESC
    document.addEventListener('click', (e) => { if (!wrapper.contains(e.target)) closePanel(); });
    document.addEventListener('keydown', (e) => { if (e.key === 'Escape') closePanel(); });
}

async function collectFormData() {
  try {
        let uid = getCurrentUserId();
        if (uid == null) {
            await fetchCurrentUser();
            uid = getCurrentUserId();
        }
    } catch (_) { /* non-blocking */ }

    const parentEl = document.getElementById('parentShortName');
    const parentId = parentEl && parentEl.dataset.parentId ? parseInt(parentEl.dataset.parentId, 10) : null;

    const typeSelect = document.getElementById('typeSelect');
    const typeId = typeSelect?.value;

    const payload = {
        name: document.getElementById('shortName')?.value?.trim() || '',
        parent_id: parentId,
        description: document.getElementById('description')?.value?.trim() || '',
        Type: typeId ? parseInt(typeId, 10) : null,
        external: (document.getElementById('scopeSelect')?.value === 'External') ? 1 : 0,
        long_name: document.getElementById('longName')?.value?.trim() || null,
        url: document.getElementById('url')?.value?.trim() || null,
        status: parseInt(document.getElementById('BUDGStatus')?.value || '', 10) || null,
        lifecycle: parseInt(document.getElementById('lifecycle')?.value || '', 10) || null,
        is_public: parseInt(document.getElementById('BUDGViewing')?.value || '', 10) || null,
        confidentiality_rating: parseInt(document.getElementById('ciaC')?.value || '', 10) || null,
        integrity_rating: parseInt(document.getElementById('ciaI')?.value || '', 10) || null,
        availability_rating: parseInt(document.getElementById('ciaA')?.value || '', 10) || null,
        asset_id: document.getElementById('assetId')?.value?.trim() || null,
        classification: parseInt(document.getElementById('classification')?.value || '', 10) || null,
        dq_automation: document.getElementById('autoLocalRules')?.checked ? 1 : 0,
        segmentId: window.segmentField ? window.segmentField.getValue() : 1,
        // Persist creator using current session user id
        CreatedBy_ID: (function(){
            const uid = getCurrentUserId();
            if (uid == null) return null;
            const asNumber = parseInt(uid, 10);
            return Number.isNaN(asNumber) ? uid : asNumber;
        })()
    };

    return payload;
}


