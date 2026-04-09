// System Interface Page JavaScript
document.addEventListener('DOMContentLoaded', function() {
    const saveBtn = document.getElementById('saveBtn');
    const saveAndCloseBtn = document.getElementById('saveAndCloseBtn');
    const closeBtn = document.getElementById('closeBtn');
    initValidation();
    initLookupsAndSearchables();
    initAutoReferenceGeneration();
    
    // Initialize custom fields
    initializeCustomFields();

    if (saveBtn) {
        saveBtn.addEventListener('click', function() {
            savePage(true);
        });
    }

    if (saveAndCloseBtn) {
        saveAndCloseBtn.addEventListener('click', function() {
            savePage(true);
        });
    }

    if (closeBtn) {
        closeBtn.addEventListener('click', function() {
            closePage();
        });
    }

    // Advanced Rich Text Editor toggle for ifDescription
    const showDescriptionEditorBtn = document.getElementById('showDescriptionEditorBtn');
    if (showDescriptionEditorBtn) {
        showDescriptionEditorBtn.addEventListener('click', function() {
            window.toggleAdvancedRichTextEditor('ifDescription', showDescriptionEditorBtn);
        });
    }

});

// Initialize custom fields
async function initializeCustomFields() {
    try {
        if (window.CustomFields) {
            window.customFieldsContext = await window.CustomFields.initForm({
                facetId: 'System Interface',
                containerId: 'customFieldsContainer',
                mode: 'create',
                objectId: null
            });
            console.log('Custom fields initialized:', window.customFieldsContext);
        } else {
            console.warn('CustomFields not available');
        }
    } catch (error) {
        console.error('Error initializing custom fields:', error);
    }
    
    // Initialize segment field
    try {
        if (window.SegmentField) {
            window.segmentField = await SegmentField.init('segmentFieldContainer', {
                label: 'Segment',
                required: true,
                defaultValue: 1,
                sectionTitle: 'SEGMENTATION',
                objectType: 'System Interface',
                fieldId: 'systemInterfaceSegment',
                errorId: 'systemInterfaceSegmentError'
            });
            console.log('Segment field initialized');
        }
    } catch (error) {
        console.error('Error initializing segment field:', error);
    }
}

async function initLookupsAndSearchables() {
    try {
        const svc = window.BUDG_API_SERVICE;
        const [statuses, viewings, lifecycles, automations, frequencies, tMethods, tFormats, classifications, systems] = await Promise.all([
            svc.getStatusList(),
            svc.getViewingList(),
            svc.getInterfaceLifecycleList?.() ? svc.getInterfaceLifecycleList() : svc.getLifecycleList(),
            svc.getInterfaceAutomation(),
            svc.getInterfaceFrequencies(),
            svc.getInterfaceTransferMethods(),
            svc.getInterfaceTransferFormats(),
            svc.getInterfaceClassifications?.() ? svc.getInterfaceClassifications() : svc.getSystemClassifications(),
            svc.getSystemsList()
        ]);

        fillSelect('BUDGStatus', (Array.isArray(statuses?.data) ? statuses.data : statuses), 'name');
        fillSelect('BUDGViewing', viewings, 'name');
        fillSelect('lifecycle', lifecycles, 'name');
        fillSelect('automation', automations, 'name');
        fillSelect('frequency', frequencies, 'name');
        fillSelect('transferMethod', tMethods, 'name');
        fillSelect('transferFormat', tFormats, 'name');
        fillSelect('interfaceClassification', classifications, 'name');

        // Hidden selects for systems to back the searchable panels
        ensureHiddenSystemSelects(systems);
        initDualSystemDropdown('source');
        initDualSystemDropdown('target');

        // Set sensible defaults if found
        setDefaultByText('BUDGStatus', ['Active']);
        setDefaultByText('BUDGViewing', ['Public']);
        setDefaultByText('lifecycle', ['In Production', 'Production', 'Prod']);
        setDefaultByText('automation', ['Unknown', 'Manual', 'Automated']);
    } catch (e) {
        console.error('Failed to load lookups', e);
    }
}

function fillSelect(selectId, list, labelKey) {
    const el = document.getElementById(selectId);
    if (!el || !Array.isArray(list)) return;
    const current = el.value;
    el.innerHTML = '';
    const empty = document.createElement('option');
    empty.value = '';
    empty.textContent = 'Please select';
    el.appendChild(empty);

    list.forEach(item => {
        const opt = document.createElement('option');
        opt.value = item.id;
        opt.textContent = item[labelKey] || item.name || '';
        el.appendChild(opt);
    });
    if (current) el.value = current;
}

function ensureHiddenSystemSelects(systems) {
    ['source', 'target'].forEach(kind => {
        let sel = document.getElementById(kind + 'System');
        if (!sel) {
            sel = document.createElement('select');
            sel.id = kind + 'System';
            sel.style.display = 'none';
            const anchor = document.getElementById(kind + 'ShortName');
            if (anchor && anchor.parentElement) anchor.parentElement.appendChild(sel);
        }
        sel.innerHTML = '';
        (systems || []).forEach(s => {
            const opt = document.createElement('option');
            opt.value = s.id;
            opt.textContent = s.name;
            opt.dataset.full = JSON.stringify(s);
            sel.appendChild(opt);
        });
    });
}

function initDualSystemDropdown(kind) {
    const hiddenSelect = document.getElementById(kind + 'System');
    const input = document.getElementById(kind + 'ShortName');
    if (!hiddenSelect || !input) return;
    // Make the input open a panel like dataset's searchable
    // Prevent double init
    if (input.dataset.init === '1') return; input.dataset.init = '1';

    const wrapper = input.parentElement;
    if (!wrapper) return;
    const panel = document.createElement('div');
    panel.className = 'searchable-select-panel';
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
    wrapper.style.position = 'relative';
    wrapper.appendChild(panel);

    function openPanel() {
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
        Array.from(hiddenSelect.options).forEach((opt, idx) => {
            const item = opt?.dataset?.full ? JSON.parse(opt.dataset.full) : null;
            const name = item?.name || opt.textContent || '';
            const description = item?.description || '';
            const searchable = (name + ' ' + description).toLowerCase();
            if (query && !searchable.includes(query)) return;
            const row = document.createElement('div');
            row.style.display = 'grid';
            row.style.gridTemplateColumns = '1fr 2fr';
            row.style.gap = '8px';
            row.style.padding = '8px 10px';
            row.style.cursor = 'pointer';
            row.style.borderRadius = '6px';
            row.addEventListener('mouseenter', () => { row.style.background = 'var(--gray-50, #f2f4f7)'; });
            row.addEventListener('mouseleave', () => { row.style.background = 'transparent'; });
            row.addEventListener('click', () => {
                hiddenSelect.selectedIndex = idx;
                input.value = name;
                input.dataset.systemId = String(item.id);
                closePanel();
            });
            const nameDiv = document.createElement('div');
            nameDiv.textContent = name;
            nameDiv.style.fontWeight = '500';
            const descDiv = document.createElement('div');
            descDiv.textContent = description;
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

    input.addEventListener('click', (e) => { e.stopPropagation(); openPanel(); });
    input.addEventListener('keydown', (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); openPanel(); }});
    searchInput.addEventListener('input', (e) => renderList(e.target.value));
    document.addEventListener('click', (e) => { if (!wrapper.contains(e.target)) closePanel(); });
    document.addEventListener('keydown', (e) => { if (e.key === 'Escape') closePanel(); });
}


// Initialize automatic reference generation
function initAutoReferenceGeneration() {
    // This function is kept for compatibility but does nothing.
    // Ref generation happens in the backend when saving if ref is empty,
    // ensuring unique sequential refs (IF001, IF002, etc.).
}

async function savePage(closeAfterSave) {
    try {
        // Sync advanced rich text editor content to textarea before saving
        window.syncAdvancedRichTextToTextarea('ifDescription');

        const buttons = [document.getElementById('saveBtn'), document.getElementById('saveAndCloseBtn')];
        buttons.forEach(btn => { if (btn) { btn.disabled = true; btn.textContent = I18n.t('createPage.message.saving'); } });

        
        const isValid = validateForm();
        if (!isValid) {
            const firstError = document.querySelector('.field-error[style*="display: block"], .field-error:not([style])');
            if (firstError) {
                const input = firstError.previousElementSibling;
                if (input && typeof input.focus === 'function') input.focus();
            }
            buttons.forEach(btn => { if (btn) { btn.disabled = false; btn.textContent = btn.id === 'saveBtn' ? I18n.t('button.save') : I18n.t('button.saveAndClose'); } });
            return;
        }
        
        // Validate custom fields
        if (window.customFieldsContext && window.customFieldsContext.validate) {
            const customFieldsValid = window.customFieldsContext.validate();
            if (!customFieldsValid) {
                buttons.forEach(btn => {
                    if (btn) {
                        btn.disabled = false;
                        btn.textContent = btn.id === 'saveBtn' ? 'Save' : 'Save & Close';
                    }
                });
                return;
            }
        }

        const payload = collectInterfacePayload();

        const resp = await window.BUDG_API_SERVICE.saveInterface(payload);
        const id = resp?.interfaceId || resp?.id || resp?.data?.interfaceId || resp?.data?.id || resp?.insertId || resp?.createdId;
        const okStatus = (resp?.status && String(resp.status).toLowerCase() === 'success') || resp?.success === true;
        if (!id && !okStatus) {
            const msg = resp?.message || resp?.error || 'Failed to save interface';
            throw new Error(msg);
        }
        
        // Save custom fields if context exists
        if (window.customFieldsContext && window.customFieldsContext.saveValues && id != null) {
            try {
                await window.customFieldsContext.saveValues(id);
                console.log('✅ Custom fields saved successfully');
            } catch (error) {
                console.error('Error saving custom fields:', error);
            }
        }

        const savedMsg = (window.I18n && window.I18n.t('createPage.message.systemInterfaceSaved')) || 'Interface saved successfully';
        if (typeof window.showNotification === 'function') { window.showNotification(savedMsg, 'success'); } else { alert(savedMsg); }
        if (closeAfterSave) {
            if (id != null) {
                window.location.href = `/view/interface/${encodeURIComponent(id)}`;
            } else {
                window.location.href = 'index.html';
            }
        } else {
            // Reload the page to refresh everything
            window.location.reload();
        }
    } catch (e) {
        console.error(e);
        const serverMsg = (e?.body?.error) || (e?.body?.message) || e?.message;
        const errInfo = window.getCreateSaveErrorInfo ? window.getCreateSaveErrorInfo(serverMsg, 'System Interfaces') : null;
        if (errInfo) {
            const m = I18n.t(errInfo.key, errInfo.params);
            if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
            if (errInfo.focus === 'name') document.getElementById('ifName')?.focus(); else if (errInfo.focus === 'reference') document.getElementById('ifRef')?.focus();
        } else {
            const serverField = e?.body?.field;
            if (serverField) {
                const normalizedMsg = serverMsg || I18n.t('createPage.message.required');
                mapServerErrorToField(serverField, normalizedMsg);
            } else if (typeof serverMsg === 'string' && serverMsg.toLowerCase().includes('missing')) {
                inferAndShowFieldErrorFromMessage(serverMsg);
            } else {
                const m = I18n.t('createPage.message.failedToSaveWithHint', { facet: 'System Interfaces' });
                if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
            }
        }
    } finally {
        const saveBtn = document.getElementById('saveBtn');
        const saveAndCloseBtn = document.getElementById('saveAndCloseBtn');
        if (saveBtn) saveBtn.textContent = 'Save', saveBtn.disabled = false;
        if (saveAndCloseBtn) saveAndCloseBtn.textContent = 'Save & Close', saveAndCloseBtn.disabled = false;
    }
}

function collectInterfacePayload() {
    return {
        name: document.getElementById('ifName')?.value?.trim() || '',
        ref_number: document.getElementById('ifRef')?.value?.trim() || null,
        description: document.getElementById('ifDescription')?.value?.trim() || '',
        synchronisation_control: document.getElementById('syncControl')?.value?.trim() || null,
        asset_id: document.getElementById('assetId')?.value?.trim() || null,
        status_id: parseInt(document.getElementById('BUDGStatus')?.value || '', 10) || null,
        lifecycle_id: parseInt(document.getElementById('lifecycle')?.value || '', 10) || null,
        is_public: parseInt(document.getElementById('BUDGViewing')?.value || '', 10) || null,
        automation_id: parseInt(document.getElementById('automation')?.value || '', 10) || null,
        frequency_id: parseInt(document.getElementById('frequency')?.value || '', 10) || null,
        transfer_method_id: parseInt(document.getElementById('transferMethod')?.value || '', 10) || null,
        transfer_format_id: parseInt(document.getElementById('transferFormat')?.value || '', 10) || null,
        classification_id: parseInt(document.getElementById('interfaceClassification')?.value || '', 10) || null,
        source_system_id: (() => { const el = document.getElementById('sourceShortName'); return el?.dataset?.systemId ? parseInt(el.dataset.systemId, 10) : null; })(),
        target_system_id: (() => { const el = document.getElementById('targetShortName'); return el?.dataset?.systemId ? parseInt(el.dataset.systemId, 10) : null; })(),
        // Segment is inherited from target system; no segment selector on interface create
        segmentId: null
    };
}

function closePage() {
    const hasData = document.querySelector('input, textarea, select') && Array.from(document.querySelectorAll('input, textarea, select')).some(el => (el.value || '').trim());
    if (hasData) {
        const confirmClose = confirm('You have unsaved changes. Are you sure you want to cancel without saving?');
        if (!confirmClose) return;
    }
    window.location.href = 'index.html';
}

/* --- Lightweight Rich Text Editor (no external libs) --- */
function toggleRichTextEditor(textareaId, toggleBtn) {
    const textarea = document.getElementById(textareaId);
    if (!textarea) return;

    // If editor already active → destroy and sync back
    const existing = textarea.parentElement.querySelector('.rte-container');
    if (existing) {
        const editor = existing.querySelector('.rte-editor');
        textarea.value = editor.innerHTML.trim();
        existing.remove();
        textarea.style.display = '';
        toggleBtn.textContent = 'Show Editor';
        return;
    }

    // Build editor UI
    const container = document.createElement('div');
    container.className = 'rte-container';

    const toolbar = document.createElement('div');
    toolbar.className = 'rte-toolbar';

    const buttons = [
        { cmd: 'bold', icon: '<b>B</b>', title: 'Bold' },
        { cmd: 'italic', icon: '<i>I</i>', title: 'Italic' },
        { cmd: 'underline', icon: '<u>U</u>', title: 'Underline' },
        { sep: true },
        { cmd: 'insertUnorderedList', icon: '• List', title: 'Bulleted List' },
        { cmd: 'insertOrderedList', icon: '1. List', title: 'Numbered List' },
        { sep: true },
        { cmd: 'createLink', icon: '🔗', title: 'Insert Link', prompt: 'Enter URL' },
        { cmd: 'unlink', icon: '⨯', title: 'Remove Link' },
        { sep: true },
        { cmd: 'undo', icon: '↶', title: 'Undo' },
        { cmd: 'redo', icon: '↷', title: 'Redo' }
    ];

    buttons.forEach(b => {
        if (b.sep) {
            const sep = document.createElement('span');
            sep.className = 'rte-sep';
            toolbar.appendChild(sep);
            return;
        }
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'rte-btn';
        btn.title = b.title;
        btn.innerHTML = b.icon;
        btn.addEventListener('click', () => {
            if (b.cmd === 'createLink') {
                const url = prompt(b.prompt || 'Enter URL');
                if (url) document.execCommand('createLink', false, url);
                return;
            }
            document.execCommand(b.cmd, false, null);
        });
        toolbar.appendChild(btn);
    });

    const editor = document.createElement('div');
    editor.className = 'rte-editor form-input';
    editor.contentEditable = 'true';

    const footer = document.createElement('div');
    footer.className = 'rte-footer';
    const hideBtn = document.createElement('button');
    hideBtn.type = 'button';
    hideBtn.className = 'btn btn-secondary';
    hideBtn.textContent = 'Hide editor';
    hideBtn.addEventListener('click', () => toggleRichTextEditor(textareaId, toggleBtn));
    footer.appendChild(hideBtn);

    container.appendChild(toolbar);
    container.appendChild(editor);
    container.appendChild(footer);

    textarea.style.display = 'none';
    textarea.parentElement.appendChild(container);
    toggleBtn.textContent = 'Hide editor';
}

function initValidation() {
    const requiredSelectors = [
        { id: 'ifName', errorId: 'ifNameError', message: 'Name is required' },
        { id: 'sourceShortName', errorId: 'sourceShortNameError', message: 'Source System Short Name is required' },
        { id: 'targetShortName', errorId: 'targetShortNameError', message: 'Target System Short Name is required' },
        { id: 'ifDescription', errorId: 'ifDescriptionError', message: 'Description is required' },
        { id: 'BUDGStatus', errorId: 'BUDGStatusError', message: 'BUDG Status is required' },
        { id: 'BUDGViewing', errorId: 'BUDGViewingError', message: 'BUDG Viewing is required' },
        { id: 'lifecycle', errorId: 'lifecycleError', message: 'Lifecycle is required' },
        { id: 'automation', errorId: 'automationError', message: 'Automation is required' }
    ];

    requiredSelectors.forEach(({ id, errorId }) => {
        const el = document.getElementById(id);
        const err = document.getElementById(errorId);
        if (!el || !err) return;
        const handler = () => {
            let hasVal = (el.value || '').trim();
            if (id === 'sourceShortName' || id === 'targetShortName') {
                const sysId = el?.dataset?.systemId;
                hasVal = !!sysId;
            }
            if (hasVal) {
                err.style.display = 'none';
            }
        };
        el.addEventListener('input', handler);
        el.addEventListener('change', handler);
    });
}

function validateForm() {
    let valid = true;
    const setError = (errorId, message, show) => {
        const err = document.getElementById(errorId);
        if (!err) return;
        err.textContent = message;
        err.style.display = show ? 'block' : 'none';
    };

    const fields = [
        { value: document.getElementById('ifName')?.value, errorId: 'ifNameError', message: 'Name is required' },
        { value: document.getElementById('sourceShortName')?.dataset?.systemId, errorId: 'sourceShortNameError', message: 'Source System Short Name is required' },
        { value: document.getElementById('targetShortName')?.dataset?.systemId, errorId: 'targetShortNameError', message: 'Target System Short Name is required' },
        { value: document.getElementById('ifDescription')?.value, errorId: 'ifDescriptionError', message: 'Description is required' },
        { value: document.getElementById('BUDGStatus')?.value, errorId: 'BUDGStatusError', message: 'BUDG Status is required' },
        { value: document.getElementById('BUDGViewing')?.value, errorId: 'BUDGViewingError', message: 'BUDG Viewing is required' },
        { value: document.getElementById('lifecycle')?.value, errorId: 'lifecycleError', message: 'Lifecycle is required' },
        { value: document.getElementById('automation')?.value, errorId: 'automationError', message: 'Automation is required' }
    ];

    fields.forEach(f => {
        const hasVal = (f.value || '').trim() !== '';
        if (!hasVal) valid = false;
        setError(f.errorId, f.message, !hasVal);
    });

    return valid;
}

function mapServerErrorToField(fieldKey, message) {
    const mapping = {
        name: 'ifNameError',
        ref_number: 'ifRefError',
        description: 'ifDescriptionError',
        status_id: 'BUDGStatusError',
        is_public: 'BUDGViewingError',
        lifecycle_id: 'lifecycleError',
        automation_id: 'automationError',
        source_system_id: 'sourceShortNameError',
        target_system_id: 'targetShortNameError'
    };
    const errorId = mapping[fieldKey];
    if (!errorId) { const m = message || (window.I18n && window.I18n.t('createPage.message.failedToSaveWithHint', { facet: 'System Interfaces' })) || 'Failed to save interface'; if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); } return; }
    const err = document.getElementById(errorId);
    if (err) {
        err.textContent = message || 'This field is required';
        err.style.display = 'block';
        const input = err.previousElementSibling;
        if (input && typeof input.focus === 'function') input.focus();
    } else {
        const m = message || (window.I18n && window.I18n.t('createPage.message.failedToSaveWithHint', { facet: 'System Interfaces' })) || 'Failed to save interface';
        if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
    }
}

function inferAndShowFieldErrorFromMessage(message) {
    const lower = (message || '').toLowerCase();
    if (lower.includes('name')) return mapServerErrorToField('name', message);
    if (lower.includes('description')) return mapServerErrorToField('description', message);
    if (lower.includes('status')) return mapServerErrorToField('status_id', message);
    if (lower.includes('viewing') || lower.includes('public')) return mapServerErrorToField('is_public', message);
    if (lower.includes('lifecycle')) return mapServerErrorToField('lifecycle_id', message);
    if (lower.includes('automation')) return mapServerErrorToField('automation_id', message);
    if (lower.includes('source')) return mapServerErrorToField('source_system_id', message);
    if (lower.includes('target')) return mapServerErrorToField('target_system_id', message);
    const m = message || (window.I18n && window.I18n.t('createPage.message.failedToSaveWithHint', { facet: 'System Interfaces' })) || 'Failed to save interface';
    if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
}

// Helper: set default select option by matching visible text (case-insensitive contains)
function setDefaultByText(selectId, preferredTexts = []) {
    const sel = document.getElementById(selectId);
    if (!sel || !sel.options || !sel.options.length) return;
    const texts = preferredTexts.map(t => (t || '').toLowerCase());
    let index = -1;
    for (let i = 0; i < sel.options.length; i++) {
        const txt = (sel.options[i].textContent || '').toLowerCase();
        if (texts.some(t => txt.includes(t))) { index = i; break; }
    }
    if (index === -1) index = 0; // fallback to first option
    sel.selectedIndex = index;
    sel.dispatchEvent(new Event('change'));
}


