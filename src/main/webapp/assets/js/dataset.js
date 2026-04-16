// Dataset Page JavaScript
let segmentField = null; // Global reference to segment field

document.addEventListener('DOMContentLoaded', async function() {
    wireButtons();
    await initSegmentField();
    await loadLookups();
    
    // Apply DFCR locked fields for Data Set facet (create mode)
    if (window.DFCRUtils) {
        try {
            await window.DFCRUtils.applyLockedFields('Data Set', {
                status: '#dsStatus',
                lifecycle: '#dsLifecycle'
            });
            console.log('DFCR locked fields applied for Data Set create page');
        } catch (dfcrError) {
            console.warn('Error applying DFCR locked fields:', dfcrError);
        }
    }
    
    initSearchableSelectDropdowns();
    initValidation();
    initReferenceField();
    await initializeCustomFields();

    // Advanced Rich Text Editor toggles
    const showDefinitionEditorBtn = document.getElementById('showDefinitionEditorBtn');
    if (showDefinitionEditorBtn) {
        showDefinitionEditorBtn.addEventListener('click', function() {
            window.toggleAdvancedRichTextEditor('dsDefinition', showDefinitionEditorBtn);
        });
    }
    const showUsageEditorBtn = document.getElementById('showUsageEditorBtn');
    if (showUsageEditorBtn) {
        showUsageEditorBtn.addEventListener('click', function() {
            window.toggleAdvancedRichTextEditor('dsUsage', showUsageEditorBtn);
        });
    }
});

async function reloadSystemsForSelectedSegment() {
    try {
        const selectedSegmentId = segmentField ? segmentField.getValue() : 1;
        const currentValue = document.getElementById('dsSystem')?.value || null;
        const systems = await window.BUDG_API_SERVICE.getSystemsList({ segmentId: selectedSegmentId });

        populateSystemSelect('dsSystem', systems);

        // Preserve selection if still valid; otherwise fall back to first option
        const sel = document.getElementById('dsSystem');
        if (sel) {
            if (currentValue && Array.from(sel.options).some(o => o.value === String(currentValue))) {
                sel.value = String(currentValue);
            } else if (sel.options.length > 0) {
                sel.selectedIndex = 0;
            }
            sel.dispatchEvent(new Event('change'));
        }
    } catch (e) {
        console.error('Failed to reload systems for segment', e);
    }
}

function populateSystemSelect(selectId, systems) {
    // Remove duplicate IDs from backend payload (defensive)
    const byId = new Map();
    (systems || []).forEach(item => {
        if (!item || item.id == null) return;
        byId.set(String(item.id), item);
    });
    const uniqueSystems = Array.from(byId.values());

    // If multiple systems share the same name, append ID to disambiguate in UI.
    const nameCounts = new Map();
    uniqueSystems.forEach(item => {
        const key = (item?.name || '').trim().toLowerCase();
        if (!key) return;
        nameCounts.set(key, (nameCounts.get(key) || 0) + 1);
    });

    const decorated = uniqueSystems.map(item => {
        const rawName = item?.name || '';
        const key = rawName.trim().toLowerCase();
        const duplicateName = key && (nameCounts.get(key) || 0) > 1;
        return {
            ...item,
            _displayName: duplicateName ? `${rawName} (ID: ${item.id})` : rawName
        };
    });

    populateSelect(selectId, decorated, item => item?._displayName || item?.name || '');
}

async function reloadGlossariesForSelectedSegment() {
    try {
        const selectedSegmentId = segmentField ? segmentField.getValue() : 1;
        const currentValue = document.getElementById('dsGlossary')?.value || null;
        const glossary = await window.BUDG_API_SERVICE.getGlossaryList({ segmentId: selectedSegmentId });

        populateSelect('dsGlossary', glossary, (item) => item?.description ? `${item.name} — ${item.description}` : item.name, (item) => {
            const hint = document.getElementById('glossaryHint');
            if (hint) hint.textContent = item?.description ? `${item.name} — ${item.description}` : '';
        });

        // Preserve selection if still valid, otherwise leave empty
        const sel = document.getElementById('dsGlossary');
        if (sel) {
            if (currentValue && Array.from(sel.options).some(o => o.value === String(currentValue))) {
                sel.value = String(currentValue);
                sel.dispatchEvent(new Event('change'));
            } else {
                // Ensure no selection if no valid current value
                sel.selectedIndex = -1;
                sel.value = '';
                sel.dispatchEvent(new Event('change'));
            }
        }
    } catch (e) {
        console.error('Failed to reload glossaries for segment', e);
    }
}

// Initialize segment field using reusable component
async function initSegmentField() {
    if (window.SegmentField) {
        segmentField = await SegmentField.init('segmentFieldContainer', {
            label: 'Segment',
            required: true,
            defaultValue: 1, // Enterprise
            sectionTitle: 'OTHER INFORMATION',
            onChange: async () => {
                // When dataset segment changes, filter systems to:
                // Enterprise OR same segment as selected dataset segment
                await reloadSystemsForSelectedSegment();
                await reloadGlossariesForSelectedSegment();
            }
        });
    } else {
        console.warn('SegmentField component not loaded');
    }
}

// Initialize custom fields
async function initializeCustomFields() {
    try {
        if (window.CustomFields) {
            window.customFieldsContext = await window.CustomFields.initForm({
                facetId: 'Dataset',
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
}

function wireButtons() {
    const saveBtn = document.getElementById('saveBtn');
    const saveAndCloseBtn = document.getElementById('saveAndCloseBtn');
    const closeBtn = document.getElementById('closeBtn');

    if (saveBtn) saveBtn.addEventListener('click', () => saveDataset(true));
    if (saveAndCloseBtn) saveAndCloseBtn.addEventListener('click', () => saveDataset(true));
    if (closeBtn) closeBtn.addEventListener('click', () => window.location.href = 'index.html');
}

async function loadLookups() {
    try {
        const selectedSegmentId = segmentField ? segmentField.getValue() : 1;
        const [systems, glossary, statuses, types, viewing, lifecycle] = await Promise.all([
            window.BUDG_API_SERVICE.getSystemsList({ segmentId: selectedSegmentId }),
            window.BUDG_API_SERVICE.getGlossaryList({ segmentId: selectedSegmentId }),
            window.BUDG_API_SERVICE.getStatusList(),
            window.BUDG_API_SERVICE.getDatasetTypes(),
            window.BUDG_API_SERVICE.getViewingList(),
            window.BUDG_API_SERVICE.getDatasetLifecycleList()
        ]);

        populateSystemSelect('dsSystem', systems);
        populateSelect('dsGlossary', glossary, (item) => item?.description ? `${item.name} — ${item.description}` : item.name, (item) => {
            const hint = document.getElementById('glossaryHint');
            if (hint) hint.textContent = item?.description ? `${item.name} — ${item.description}` : '';
        });
        // Ensure no default selection for glossary field - must be empty
        const glossarySelect = document.getElementById('dsGlossary');
        if (glossarySelect) {
            glossarySelect.selectedIndex = -1;
            glossarySelect.value = '';
        }
        const statusList = Array.isArray(statuses?.data) ? statuses.data : statuses;
        populateSelect('dsStatus', statusList, 'name');
        populateSelect('dsType', types, 'primaryName');
        populateSelect('dsViewing', viewing, 'name');
        populateSelect('dsLifecycle', lifecycle, 'name');

        // Set sensible defaults if found
        setDefaultByText('dsStatus', ['Active']);
        setDefaultByText('dsType', ['Data Set', 'Dataset']);
        setDefaultByText('dsViewing', ['Public', 'Internal']);

        // Ensure systems are filtered based on the current segment selection
        await reloadSystemsForSelectedSegment();
        await reloadGlossariesForSelectedSegment();
    } catch (e) {
        console.error('Failed to load lookups', e);
    }
}

function populateSelect(selectId, data, labelKey = 'name', onChange) {
    const select = document.getElementById(selectId);
    if (!select) return;
    select.innerHTML = '';
    (data || []).forEach(item => {
        const opt = document.createElement('option');
        opt.value = item.id;
        const label = (typeof labelKey === 'function') ? labelKey(item) : (item[labelKey] || item.name);
        opt.textContent = label;
        opt.dataset.full = JSON.stringify(item);
        select.appendChild(opt);
    });
    // Do not force-select first; defaults handled separately
    if (onChange) {
        select.addEventListener('change', () => {
            const sel = select.options[select.selectedIndex];
            const obj = sel?.dataset?.full ? JSON.parse(sel.dataset.full) : null;
            onChange(obj);
        });
    }
}

function setDefaultByText(selectId, preferredTexts = []) {
    const sel = document.getElementById(selectId);
    if (!sel || !sel.options.length) return;
    const texts = preferredTexts.map(t => (t || '').toLowerCase());
    let index = -1;
    for (let i = 0; i < sel.options.length; i++) {
        const txt = (sel.options[i].textContent || '').toLowerCase();
        if (texts.some(t => txt.includes(t))) { index = i; break; }
    }
    if (index === -1) index = 0;
    sel.selectedIndex = index;
    sel.dispatchEvent(new Event('change'));
}

function initSearchableSelectDropdowns() {
    initSystemDropdown();

    const glossarySelect = document.getElementById('dsGlossary');
    // Skip creating searchable dropdown if we're on edit page (which uses modal picker)
    const glossaryDisplay = document.getElementById('dsGlossaryDisplay');
    if (glossaryDisplay) {
        // This is the edit page, skip creating searchable dropdown
        return;
    }
    
    if (glossarySelect) {
        // Ensure no default selection before creating dropdown
        if (!glossarySelect.value || glossarySelect.selectedIndex < 0) {
            glossarySelect.selectedIndex = -1;
            glossarySelect.value = '';
        }
        createSearchableDropdown(glossarySelect, {
            useFormInputGroup: true,
            useTextInputDisplay: true,
            getRowContent: (opt) => {
                const item = opt?.dataset?.full ? JSON.parse(opt.dataset.full) : null;
                const name = item?.name || opt.textContent || '';
                const description = item?.description || '';
                const row = document.createElement('div');
                row.style.display = 'grid';
                row.style.gridTemplateColumns = '1fr 2fr';
                row.style.gap = '8px';
                const nameDiv = document.createElement('div');
                nameDiv.textContent = name;
                nameDiv.style.fontWeight = '500';
                const descDiv = document.createElement('div');
                descDiv.textContent = description;
                descDiv.style.opacity = '0.8';
                row.appendChild(nameDiv);
                row.appendChild(descDiv);
                return row;
            },
            getSearchText: (opt) => {
                const item = opt?.dataset?.full ? JSON.parse(opt.dataset.full) : null;
                const name = (item?.name || opt.textContent || '').toLowerCase();
                const description = (item?.description || '').toLowerCase();
                return name + ' ' + description;
            }
        });
    }
}

// ------------------- System Select Dropdown (like OrgUnit) -------------------
function initSystemDropdown() {
    const hiddenSelect = document.getElementById('dsSystem');
    const container = document.getElementById('systemSelectDropdown');
    const display = document.getElementById('systemSelectDisplay');
    const arrow = document.getElementById('systemSelectArrow');
    const menu = document.getElementById('systemSelectMenu');
    const search = document.getElementById('systemSelectSearch');
    const items = document.getElementById('systemSelectItems');

    if (!hiddenSelect || !container || !display || !arrow || !menu || !search || !items) return;

    let allItems = [];
    let isOpen = false;
    let focusIndex = -1;

    // Populate from the hidden select once lookups load
    function syncFromSelect() {
        allItems = Array.from(hiddenSelect.options).map((opt, index) => ({
            id: opt.value,
            name: opt.textContent || '',
            index
        }));
        renderItems(allItems);
        if (hiddenSelect.selectedIndex >= 0) {
            display.value = hiddenSelect.options[hiddenSelect.selectedIndex].textContent || '';
        }
    }
    // Wait until lookup fill happens
    const observer = new MutationObserver(() => syncFromSelect());
    observer.observe(hiddenSelect, { childList: true });
    // Also run once in case already filled
    setTimeout(syncFromSelect, 0);

    function open() {
        isOpen = true;
        container.classList.add('active');
        menu.classList.add('show');
        setTimeout(() => search.focus(), 50);
    }
    function close() {
        isOpen = false;
        container.classList.remove('active');
        menu.classList.remove('show');
        search.value = '';
        focusIndex = -1;
        renderItems(allItems);
    }
    function toggle() { isOpen ? close() : open(); }

    function renderItems(data) {
        items.innerHTML = '';
        if (!data || data.length === 0) {
            const empty = document.createElement('div');
            empty.className = 'select-dropdown-empty';
            empty.innerHTML = '<i class="fas fa-search"></i> No systems found';
            items.appendChild(empty);
            return;
        }
        data.forEach((item, idx) => {
            const el = document.createElement('div');
            el.className = 'select-dropdown-item';
            el.dataset.id = item.id;
            el.dataset.index = item.index;
            el.innerHTML = `
                <i class="fas fa-server select-dropdown-item-icon"></i>
                <div class="select-dropdown-item-content">
                    <div class="select-dropdown-item-name">${item.name}</div>
                </div>
            `;
            el.addEventListener('click', () => selectItem(item, el));
            el.addEventListener('keydown', (e) => {
                if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); selectItem(item, el); }
            });
            items.appendChild(el);
        });
    }

    function selectItem(item, el) {
        // Update hidden select
        hiddenSelect.selectedIndex = item.index;
        hiddenSelect.dispatchEvent(new Event('change'));
        // Update display
        display.value = item.name;
        // Close
        close();
    }

    function searchData(term) {
        const q = term.trim().toLowerCase();
        if (!q) { renderItems(allItems); return; }
        const filtered = allItems.filter(i => (i.name || '').toLowerCase().includes(q));
        renderItems(filtered);
    }

    // Events
    display.addEventListener('click', (e) => { e.stopPropagation(); toggle(); });
    display.addEventListener('keydown', (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); toggle(); } });
    arrow.addEventListener('click', (e) => { e.stopPropagation(); toggle(); });
    search.addEventListener('input', () => searchData(search.value));
    search.addEventListener('keydown', (e) => { if (e.key === 'Escape') close(); });
    document.addEventListener('click', (e) => { if (!container.contains(e.target) && e.target !== search) close(); });
}

function createSearchableDropdown(select, options = {}) {
    // Hide native select but keep it for form submission
    select.style.display = 'none';

    const wrapper = document.createElement('div');
    wrapper.className = 'searchable-select-wrapper';
    if (options.useFormInputGroup) {
        wrapper.classList.add('form-input-group');
    }
    wrapper.style.position = 'relative';

    let display; // clickable element (either input or div)
    let displayIsInput = false;
    if (options.useTextInputDisplay) {
        display = document.createElement('input');
        display.type = 'text';
        display.readOnly = true;
        display.className = 'form-input';
        display.placeholder = 'Select...';
        displayIsInput = true;
    } else {
        display = document.createElement('div');
        display.className = 'form-input searchable-select-display';
        display.tabIndex = 0;
        display.style.cursor = 'pointer';
        display.style.display = 'flex';
        display.style.alignItems = 'center';
        display.style.justifyContent = 'space-between';
    }

    const label = document.createElement('span');
    // Only show text if an option is actually selected (selectedIndex >= 0)
    label.textContent = (select.selectedIndex >= 0) ? (getSelectedText(select) || '') : '';
    let icon;
    if (displayIsInput) {
        display.value = label.textContent;
        icon = document.createElement('i');
        icon.className = 'input-icon fas fa-edit';
        icon.style.opacity = '0.6';
    } else {
        const caret = document.createElement('i');
        caret.className = 'input-icon fas fa-chevron-down';
        caret.style.opacity = '0.6';
        display.appendChild(label);
        display.appendChild(caret);
    }

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
    searchInput.placeholder = 'Search...';
    searchInput.className = 'form-input';
    searchInput.style.margin = '4px';

    const list = document.createElement('div');
    list.style.maxHeight = '220px';
    list.style.overflowY = 'auto';
    list.style.marginTop = '6px';

    panel.appendChild(searchInput);
    panel.appendChild(list);

    // Insert DOM
    select.parentElement.insertBefore(wrapper, select);
    wrapper.appendChild(select);
    wrapper.appendChild(display);
    if (icon) wrapper.appendChild(icon);
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
        Array.from(select.options).forEach((opt, idx) => {
            const baseText = opt.textContent || '';
            const searchable = options.getSearchText ? options.getSearchText(opt) : baseText.toLowerCase();
            if (query && !searchable.includes(query)) return;
            const row = options.getRowContent ? options.getRowContent(opt) : document.createElement('div');
            if (!options.getRowContent) {
                row.textContent = baseText;
            }
            row.style.padding = '8px 10px';
            row.style.cursor = 'pointer';
            row.style.borderRadius = '6px';
            if (opt.selected) {
                row.style.background = 'var(--primary-50, #e6f0ff)';
            }
            row.addEventListener('mouseenter', () => { row.style.background = 'var(--gray-50, #f2f4f7)'; });
            row.addEventListener('mouseleave', () => { row.style.background = opt.selected ? 'var(--primary-50, #e6f0ff)' : 'transparent'; });
            row.addEventListener('click', () => {
                select.selectedIndex = idx;
                label.textContent = baseText;
                closePanel();
                select.dispatchEvent(new Event('change'));
            });
            list.appendChild(row);
        });
    }

    // Wire events
    const openClickTargets = [display];
    if (icon) openClickTargets.push(icon);
    openClickTargets.forEach(t => t.addEventListener('click', () => {
        const isOpen = panel.style.display === 'block';
        if (isOpen) closePanel(); else openPanel();
    }));
    display.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); openPanel(); }
    });
    searchInput.addEventListener('input', (e) => renderList(e.target.value));
    document.addEventListener('click', (e) => {
        if (!wrapper.contains(e.target)) closePanel();
    });
    document.addEventListener('keydown', (e) => { if (e.key === 'Escape') closePanel(); });

    // Sync external programmatic changes
    select.addEventListener('change', () => {
        const selectedText = (select.selectedIndex >= 0) ? getSelectedText(select) : '';
        label.textContent = selectedText;
        if (displayIsInput) display.value = selectedText;
    });
}

function getSelectedText(select) {
    const opt = select.options[select.selectedIndex];
    return opt ? (opt.textContent || '') : '';
}

// Initialize reference field with auto-generation logic
function initReferenceField() {
    const refInput = document.getElementById('dsRef');
    const refHint = document.getElementById('refHint');
    const refPreview = document.getElementById('refPreview');

    if (!refInput || !refHint || !refPreview) return;

    function updateRefHint() {
        const value = refInput.value.trim();
        if (value) {
            refHint.textContent = 'Using manual reference';
            refPreview.textContent = value;
            refPreview.style.display = 'inline';
        } else {
            refHint.textContent = 'Leave empty to auto-generate on save';
            refPreview.style.display = 'none';
        }
    }

    // Initial state
    updateRefHint();

    // Update on input change
    refInput.addEventListener('input', updateRefHint);
}


async function saveDataset(closeAfter) {
    // Sync advanced rich text editor content to textareas before saving
    window.syncAdvancedRichTextToTextarea('dsDefinition');
    window.syncAdvancedRichTextToTextarea('dsUsage');

    const name = document.getElementById('dsName')?.value.trim();
    const masterSource = parseInt(document.getElementById('dsSystem')?.value || '', 10);
    let refNumber = document.getElementById('dsRef')?.value.trim() || null;
    const segmentId = segmentField ? segmentField.getValue() : 1; // Default to Enterprise
    
    // Keep empty reference as null so backend generates a unique value safely.
    if (!refNumber) refNumber = null;
    const definition = document.getElementById('dsDefinition')?.value.trim();
    const glossary = parseInt(document.getElementById('dsGlossary')?.value || '', 10);
    const usage = document.getElementById('dsUsage')?.value.trim() || null;
    const status = parseInt(document.getElementById('dsStatus')?.value || '', 10);
    const datasetType = parseInt(document.getElementById('dsType')?.value || '', 10);
    const accessControlType = parseInt(document.getElementById('dsViewing')?.value || '', 10);
    const lifecycle = parseInt(document.getElementById('dsLifecycle')?.value || '', 10);

    const errors = [];
    const showErr = (id, msg, show) => { const el = document.getElementById(id); if (el) { el.textContent = msg; el.style.display = show ? 'block' : 'none'; }};
    showErr('dsNameError', '', false);
    showErr('dsSystemError', '', false);
    showErr('dsDefinitionError', '', false);
    showErr('dsGlossaryError', '', false);
    showErr('dsStatusError', '', false);
    showErr('dsTypeError', '', false);
    showErr('dsViewingError', '', false);
    showErr('dsLifecycleError', '', false);

    if (!name) { errors.push('Name'); showErr('dsNameError', I18n.t('createPage.message.nameRequired'), true); }
    if (!Number.isInteger(masterSource)) { errors.push('System Short Name'); showErr('dsSystemError', I18n.t('createPage.message.systemRequired'), true); }
    if (!definition) { errors.push('Definition'); showErr('dsDefinitionError', I18n.t('createPage.message.descriptionRequired'), true); }
    if (!Number.isInteger(glossary)) { errors.push('Glossary Name'); showErr('dsGlossaryError', I18n.t('createPage.message.glossaryRequired'), true); }
    if (!Number.isInteger(status)) { errors.push('BUDG Status'); showErr('dsStatusError', I18n.t('createPage.message.statusRequired'), true); }
    if (!Number.isInteger(datasetType)) { errors.push('Type'); showErr('dsTypeError', I18n.t('createPage.message.datasetTypeRequired'), true); }
    if (!Number.isInteger(accessControlType)) { errors.push('BUDG Viewing'); showErr('dsViewingError', I18n.t('createPage.message.viewingRequired'), true); }
    if (!Number.isInteger(lifecycle)) { errors.push('Lifecycle'); showErr('dsLifecycleError', I18n.t('createPage.message.lifecycleRequired'), true); }
    
    // Validate segment using the component
    if (segmentField && !segmentField.validate()) {
        errors.push('Segment');
    }


    if (errors.length) { return; }
    
    // Validate custom fields
    if (window.customFieldsContext && window.customFieldsContext.validate) {
        const customFieldsValid = window.customFieldsContext.validate();
        if (!customFieldsValid) {
            return;
        }
    }

    // Client-side uniqueness checks (Name, RefNumber) using list/search when available
    try {
        // Check PrimaryName uniqueness
        if (typeof window.BUDG_API_SERVICE?.getDatasetStatuses === 'function') { /* noop to ensure service is loaded */ }
        if (typeof window.BUDG_API_SERVICE?.getDatasetById === 'function' && typeof window.BUDG_API_SERVICE?.getSystemsList === 'function') {
            // Attempt to fetch existing datasets list if config exposes it via search config
            if (typeof window.BUDG_API_SERVICE?.getStatusList === 'function') {
                // No dedicated dataset list endpoint found; skip to server validation
            }
        }
    } catch (_) { /* ignore */ }

    const payload = {
        primaryName: name,
        masterSource,
        refNumber,
        definition,
        glossary,
        usage,
        status,
        datasetType,
        accessControlType,
        lifecycle,
        segmentId
    };

    const saveBtn = document.getElementById('saveBtn');
    const saveAndCloseBtn = document.getElementById('saveAndCloseBtn');
    [saveBtn, saveAndCloseBtn].forEach(b => { if (b) { b.disabled = true; b.textContent = I18n.t('createPage.message.saving'); }});
    try {
        const resp = await window.BUDG_API_SERVICE.createDataset(payload);
        const id = resp?.datasetId || resp?.id || resp?.data?.datasetId || resp?.data?.id || resp?.insertId || resp?.createdId;
        const okStatus = (resp?.status && String(resp.status).toLowerCase() === 'success') || resp?.success === true;
        if (!id && !okStatus) {
            const msg = resp?.message || resp?.error || 'Failed to save dataset';
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

        const savedMsg = I18n.t('createPage.message.datasetSaved');
        if (typeof window.showNotification === 'function') { window.showNotification(savedMsg, 'success'); } else { alert(savedMsg); }
        if (id != null) {
            window.location.href = `/view/dataset/${encodeURIComponent(id)}`;
        } else if (closeAfter) {
            window.location.href = 'index.html';
        }
    } catch (e) {
        console.error(e);
        const serverMsg = e?.body?.message || e?.body?.error || e?.message;
        const serverField = e?.body?.field;
        const t = (key, params) => (window.I18n && window.I18n.t(key, params)) || key;
        // Segment errors: show i18n message (en/ar)
        if (serverMsg && (serverMsg.includes('segment assignment') || serverMsg.includes('has no segment'))) {
            const systemMatch = String(serverMsg).match(/System\s+['"]([^'"]+)['"]\s+\(ID:\s*(\d+)\)/);
            if (systemMatch) {
                const systemName = systemMatch[1];
                const systemId = systemMatch[2];
                const editUrl = `${window.location.origin}/view/system/system-edit.html?id=${systemId}`;
                const msg = t('dataset.errors.systemNoSegment', { systemName }) + '\n\n' + editUrl;
                if (typeof window.showNotification === 'function') { window.showNotification(msg, 'error'); } else { alert(msg); }
            } else {
                const glossaryMatch = String(serverMsg).match(/Glossary\s+['"]([^'"]+)['"]/);
                const glossaryName = glossaryMatch ? glossaryMatch[1] : t('createPage.message.glossaryRequired');
                const gMsg = t('dataset.errors.glossaryNoSegment', { glossaryName });
                if (typeof window.showNotification === 'function') { window.showNotification(gMsg, 'error'); } else { alert(gMsg); }
            }
        } else {
            const errInfo = window.getCreateSaveErrorInfo ? window.getCreateSaveErrorInfo(serverMsg, 'Data Sets') : null;
            const lowerMsg = String(serverMsg || '').toLowerCase();
            if (errInfo) {
                const errMsg = I18n.t(errInfo.key, errInfo.params);
                if (typeof window.showNotification === 'function') { window.showNotification(errMsg, 'error'); } else { alert(errMsg); }
                const fieldKey = errInfo.focus === 'name' ? 'primaryName' : errInfo.focus === 'reference' ? 'refNumber' : null;
                if (fieldKey) mapDatasetServerError(fieldKey, I18n.t(errInfo.key, errInfo.params));
            } else if (
                lowerMsg.includes('dataset/system segment rule violation') ||
                lowerMsg.includes('when a system belongs to a private segment') ||
                lowerMsg.includes('visibility rule violation') ||
                lowerMsg.includes('cannot be set to public because')
            ) {
                const visMsg = I18n.t('dataset.errors.systemPrivateSegmentRule');
                if (typeof window.showNotification === 'function') { window.showNotification(visMsg, 'error'); } else { alert(visMsg); }
            } else if (serverField) {
                mapDatasetServerError(serverField, serverMsg || I18n.t('createPage.message.required'));
            } else if (serverMsg) {
                // Prefer specific backend message over generic duplicate/reference hint.
                if (typeof window.showNotification === 'function') { window.showNotification(serverMsg, 'error'); } else { alert(serverMsg); }
            } else {
                const failMsg = I18n.t('createPage.message.failedToSaveWithHint', { facet: 'Data Sets' });
                if (typeof window.showNotification === 'function') { window.showNotification(failMsg, 'error'); } else { alert(failMsg); }
            }
        }
    } finally {
        if (saveBtn) saveBtn.textContent = I18n.t('button.save'), saveBtn.disabled = false;
        if (saveAndCloseBtn) saveAndCloseBtn.textContent = I18n.t('button.saveAndClose'), saveAndCloseBtn.disabled = false;
    }
}

function initValidation() {
    const bind = (inputId, errId) => {
        const el = document.getElementById(inputId);
        const err = document.getElementById(errId);
        if (!el || !err) return;
        const handler = () => { if ((el.value || '').trim()) err.style.display = 'none'; };
        el.addEventListener('input', handler);
        el.addEventListener('change', handler);
    };
    bind('dsName', 'dsNameError');
    bind('dsSystem', 'dsSystemError');
    bind('dsDefinition', 'dsDefinitionError');
    bind('dsGlossary', 'dsGlossaryError');
    bind('dsStatus', 'dsStatusError');
    bind('dsType', 'dsTypeError');
    bind('dsViewing', 'dsViewingError');
    bind('dsLifecycle', 'dsLifecycleError');
    bind('dsRef', 'dsRefError');
    // Segment field validation is handled by the SegmentField component
}

function mapDatasetServerError(fieldKey, message) {
    const map = {
        primaryName: 'dsNameError',
        masterSource: 'dsSystemError',
        refNumber: 'dsRefError',
        definition: 'dsDefinitionError',
        glossary: 'dsGlossaryError',
        status: 'dsStatusError',
        accessControlType: 'dsTypeError',
        lifecycle: 'dsLifecycleError',
        viewing: 'dsViewingError'
    };
    const errorId = map[fieldKey];
    if (!errorId) {
        const errMsg = I18n.t('createPage.message.errorSaving', {error: message});
        if (typeof window.showNotification === 'function') { window.showNotification(errMsg, 'error'); } else { alert(errMsg); }
        return;
    }
    const el = document.getElementById(errorId);
    if (el) { el.textContent = message; el.style.display = 'block'; }
}

