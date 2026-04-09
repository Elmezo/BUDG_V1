// Glossary Page JavaScript
document.addEventListener('DOMContentLoaded', function() {
    const saveBtn = document.getElementById('saveBtn');
    const saveAndCloseBtn = document.getElementById('saveAndCloseBtn');
    const closeBtn = document.getElementById('closeBtn');
    const aliasesContainer = document.getElementById('gloAliases');

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

    // Initialize tags input for alias names
    if (aliasesContainer) {
        initTagsInput(aliasesContainer);
    }

    // Load dropdowns and init parent picker
    initGlossaryFormDropdowns().then(() => {
        // Apply DFCR locked fields for Glossary facet (create mode)
        if (window.DFCRUtils) {
            window.DFCRUtils.applyLockedFields('Glossary', {
                status: '#gloStatus',
                lifecycle: '#gloLifecycle'
            }).then(() => {
                console.log('DFCR locked fields applied for Glossary create page');
            }).catch(dfcrError => {
                console.warn('Error applying DFCR locked fields:', dfcrError);
            });
        }
    });
    
    // Initialize custom fields
    initializeCustomFields();

    // Advanced Rich Text Editor toggles
    const showDefinitionEditorBtn = document.getElementById('showDefinitionEditorBtn');
    if (showDefinitionEditorBtn) {
        showDefinitionEditorBtn.addEventListener('click', function() {
            window.toggleAdvancedRichTextEditor('gloDefinition', showDefinitionEditorBtn);
        });
    }
    const showBusinessLogicEditorBtn = document.getElementById('showBusinessLogicEditorBtn');
    if (showBusinessLogicEditorBtn) {
        showBusinessLogicEditorBtn.addEventListener('click', function() {
            window.toggleAdvancedRichTextEditor('gloBusinessLogic', showBusinessLogicEditorBtn);
        });
    }
    const showExamplesEditorBtn = document.getElementById('showExamplesEditorBtn');
    if (showExamplesEditorBtn) {
        showExamplesEditorBtn.addEventListener('click', function() {
            window.toggleAdvancedRichTextEditor('gloExamples', showExamplesEditorBtn);
        });
    }
});

// Initialize custom fields
async function initializeCustomFields() {
    try {
        if (window.CustomFields) {
            window.customFieldsContext = await window.CustomFields.initForm({
                facetId: 'Glossary',
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
                objectType: 'Glossary',
                fieldId: 'glossarySegment',
                errorId: 'glossarySegmentError',
                onChange: async (selectedSegmentId, previousSegmentId) => {
                    return await reloadGlossaryParentsForSelectedSegment(previousSegmentId);
                }
            });
            console.log('Segment field initialized');
        }
    } catch (error) {
        console.error('Error initializing segment field:', error);
    }
}

function savePage(closeAfterSave) {
    try {
        // Sync advanced rich text editor content to textareas before saving
        window.syncAdvancedRichTextToTextarea('gloDefinition');
        window.syncAdvancedRichTextToTextarea('gloBusinessLogic');
        window.syncAdvancedRichTextToTextarea('gloExamples');

        const buttons = [document.getElementById('saveBtn'), document.getElementById('saveAndCloseBtn')];
        buttons.forEach(btn => { if (btn) { btn.disabled = true; btn.textContent = I18n.t('createPage.message.saving'); } });
        
        const payload = collectGlossaryFormData();
        
        // Validate custom fields
        if (window.customFieldsContext && window.customFieldsContext.validate) {
            const customFieldsValid = window.customFieldsContext.validate();
            if (!customFieldsValid) {
                buttons.forEach(btn => { if (btn) { btn.disabled = false; btn.textContent = btn.id === 'saveBtn' ? I18n.t('button.save') : I18n.t('button.saveAndClose'); } });
                return;
            }
        }

        const runSave = () => window.BUDG_API_SERVICE.saveGlossary(payload)
            .then(async res => {
                console.log('Save response:', res);
                
                // Get the ID from the response
                const id = res?.id || res?.data?.id || res?.glossaryId || res?.insertId || res?.createdId;
                
                // Save custom fields if context exists
                if (window.customFieldsContext && window.customFieldsContext.saveValues && id != null) {
                    try {
                        await window.customFieldsContext.saveValues(id);
                        console.log('✅ Custom fields saved successfully');
                    } catch (error) {
                        console.error('Error saving custom fields:', error);
                    }
                }
                
                const savedMsg = I18n.t('createPage.message.glossarySaved');
                if (typeof window.showNotification === 'function') { window.showNotification(savedMsg, 'success'); } else { alert(savedMsg); }
                
                if (closeAfterSave) {
                    if (id != null) {
                        window.location.href = `/view/glossary/${encodeURIComponent(id)}`;
                    } else {
                        window.location.href = 'index.html';
                    }
                }
            })
            .catch(err => {
                console.error('Save error details:', err);
                const serverMsg = err?.body?.error || err?.body?.message || err?.message;
                const errInfo = window.getCreateSaveErrorInfo ? window.getCreateSaveErrorInfo(serverMsg, 'Glossary') : null;
                if (errInfo) {
                    const errMsg = I18n.t(errInfo.key, errInfo.params);
                    if (typeof window.showNotification === 'function') { window.showNotification(errMsg, 'error'); } else { alert(errMsg); }
                    const fieldId = errInfo.focus === 'name' ? 'gloName' : errInfo.focus === 'reference' ? 'gloRef' : '';
                    const fieldElement = fieldId ? document.getElementById(fieldId) : null;
                    if (fieldElement) {
                        fieldElement.focus();
                        fieldElement.style.borderColor = '#e74c3c';
                        setTimeout(() => { fieldElement.style.borderColor = ''; }, 3000);
                    }
                } else {
                    const msg = serverMsg || I18n.t('createPage.message.unknownError');
                    const errMsg = I18n.t('createPage.message.errorSaving', {error: msg});
                    if (typeof window.showNotification === 'function') { window.showNotification(errMsg, 'error'); } else { alert(errMsg); }
                }
            })
            .finally(() => {
                buttons.forEach(btn => { if (btn) { btn.disabled = false; btn.textContent = btn.id === 'saveBtn' ? I18n.t('button.save') : I18n.t('button.saveAndClose'); } });
            });

        // Uniqueness checks: Name only (Ref_Number checked by backend, auto-generated if empty)
        try {
            if (typeof window.BUDG_API_SERVICE?.getGlossaryList === 'function') {
                window.BUDG_API_SERVICE.getGlossaryList().then(list => {
                    const rows = Array.isArray(list?.data) ? list.data : Array.isArray(list) ? list : [];
                    const refVal = String(payload.ref_number || '').trim().toLowerCase();
                    
                    // Only check ref_number uniqueness if user manually entered a value
                    // If empty, backend will auto-generate, so skip frontend check
                    if (refVal) {
                        const refClash = rows.some(r => String(r.ref_number || r.Ref_Number || '').trim().toLowerCase() === refVal);
                        if (refClash) {
                            const refDupMsg = I18n.t('createPage.message.duplicateRefNumber', {facet: 'Glossary'});
                            if (typeof window.showNotification === 'function') { window.showNotification(refDupMsg, 'error'); } else { alert(refDupMsg); }
                            document.getElementById('gloRef')?.focus();
                            buttons.forEach(btn => { if (btn) { btn.disabled = false; btn.textContent = btn.id === 'saveBtn' ? I18n.t('button.save') : I18n.t('button.saveAndClose'); } });
                            return;
                        }
                    }
                    
                    runSave();
                }).catch(()=> runSave());
                return;
            }
        } catch(_) {}

        // Fallback
        runSave();
    } catch (e) {
        console.error(e);
        const serverMsg = e?.message || (e?.body && (e.body.error || e.body.message));
        const errInfo = window.getCreateSaveErrorInfo ? window.getCreateSaveErrorInfo(serverMsg, 'Glossary') : null;
        if (errInfo) {
            const errMsg = I18n.t(errInfo.key, errInfo.params);
            if (typeof window.showNotification === 'function') { window.showNotification(errMsg, 'error'); } else { alert(errMsg); }
            const fieldId = errInfo.focus === 'name' ? 'gloName' : errInfo.focus === 'reference' ? 'gloRef' : '';
            document.getElementById(fieldId)?.focus();
        } else {
            const failMsg = I18n.t('createPage.message.failedToSaveWithHint', { facet: 'Glossary' });
            if (typeof window.showNotification === 'function') { window.showNotification(failMsg, 'error'); } else { alert(failMsg); }
        }
    }
}

function closePage() {
    const hasData = document.querySelector('input, textarea, select') && Array.from(document.querySelectorAll('input, textarea, select')).some(el => (el.value || '').trim());
    if (hasData) {
        const confirmClose = confirm(I18n.t('createPage.message.unsavedChanges'));
        if (!confirmClose) return;
    }
    window.location.href = 'index.html';
}

// ---------------------------- Tags Input (Aliases) ----------------------------
function initTagsInput(container) {
    // Ensure container is clean and not contenteditable for better control
    container.removeAttribute('contenteditable');
    container.setAttribute('role', 'list');
    container.setAttribute('aria-label', 'Aliases');
    container.classList.add('tags-input-initialized');

    // Create editable input that lives inside the container
    const input = document.createElement('input');
    input.type = 'text';
    input.className = 'tags-input-field';
    input.placeholder = container.dataset.placeholder || '';
    container.appendChild(input);

    // Store tags in a Set to avoid duplicates (case-insensitive)
    const tags = new Set();

    function normalizeTag(text) {
        return (text || '').trim().replace(/\s+/g, ' ');
    }

    function addTag(text) {
        const value = normalizeTag(text);
        if (!value) return;
        // Case-insensitive duplicate check
        const exists = Array.from(tags).some(t => t.toLowerCase() === value.toLowerCase());
        if (exists) return;
        tags.add(value);
        const chip = createTagChip(value);
        container.insertBefore(chip, input);
        input.value = '';
        updatePlaceholderVisibility();
    }

    function removeTag(value) {
        tags.delete(value);
        Array.from(container.querySelectorAll('.tag-chip')).forEach(chip => {
            if (chip.dataset.value === value) chip.remove();
        });
        updatePlaceholderVisibility();
    }

    function createTagChip(value) {
        const chip = document.createElement('span');
        chip.className = 'tag-chip';
        chip.dataset.value = value;
        chip.setAttribute('role', 'listitem');
        const label = document.createElement('span');
        label.className = 'tag-label';
        label.textContent = value;
        const remove = document.createElement('button');
        remove.type = 'button';
        remove.className = 'tag-remove';
        remove.setAttribute('aria-label', 'Remove ' + value);
        remove.textContent = '×';
        remove.addEventListener('click', () => removeTag(value));
        chip.appendChild(label);
        chip.appendChild(remove);
        return chip;
    }

    function commitFromInput() {
        addTag(input.value);
    }

    function updatePlaceholderVisibility() {
        const hasTags = tags.size > 0;
        input.placeholder = hasTags ? '' : (container.dataset.placeholder || '');
    }

    // Keyboard interactions
    input.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' || e.key === ',') {
            e.preventDefault();
            commitFromInput();
        } else if (e.key === 'Backspace' && input.value === '') {
            // Remove last tag
            const chips = container.querySelectorAll('.tag-chip');
            const last = chips[chips.length - 1];
            if (last) {
                removeTag(last.dataset.value);
            }
        }
    });

    // Paste: split by commas and newlines
    input.addEventListener('paste', (e) => {
        e.preventDefault();
        const text = (e.clipboardData || window.clipboardData).getData('text');
        text.split(/[\n,]+/).forEach(part => addTag(part));
    });

    // Focus container when clicked for easier UX
    container.addEventListener('click', () => input.focus());

    // Expose a getter on the container for integration when saving
    container.getTags = function() { return Array.from(tags); };

    updatePlaceholderVisibility();
}

// ---------------------------- Dropdowns & parent picker ----------------------------
async function initGlossaryFormDropdowns() {
    const svc = window.BUDG_API_SERVICE;
    if (!svc) return Promise.resolve();
    const selectedSegmentId = window.segmentField && typeof window.segmentField.getValue === 'function'
        ? parseInt(window.segmentField.getValue(), 10)
        : NaN;
    const glossaryOptions = Number.isInteger(selectedSegmentId) && selectedSegmentId > 0
        ? { segmentId: selectedSegmentId }
        : {};
    return Promise.all([
        svc.getStatusList(),
        svc.getGlossaryLifecycleList ? svc.getGlossaryLifecycleList() : svc.getLifecycleList(),
        svc.getViewingList(),
        svc.getCiaRatings(),
        svc.getGlossaryKdeList(),
        svc.getGlossaryTypeList(),
        svc.getGlossarySecurityList(),
        svc.getGlossaryFormatTypeList(),
        svc.getGlossaryList(glossaryOptions)
    ]).then(([statuses, lifecycles, viewings, ciaRatings, kdeList, typeList, securityList, formatTypeList, glossaryList]) => {
        // Fill selects with id/value
        fillSelect('gloStatus', statuses, 'name');
        fillSelect('gloLifecycle', lifecycles, 'name');
        fillSelect('gloViewing', viewings, 'name');
        fillSelect('gloKde', kdeList, 'name');
        fillSelect('gloType', typeList, 'name');
        fillSelect('gloSecurity', securityList, 'name');
        fillSelect('gloFormatType', formatTypeList, 'name');
        // CIA dropdowns use values label
        fillCia('gloCiaC', ciaRatings);
        fillCia('gloCiaI', ciaRatings);
        fillCia('gloCiaA', ciaRatings);
        setupGlossaryParentPicker(glossaryList);
        
        
        // Initialize reference field behavior
        initReferenceField();
    }).catch(err => {
        console.error('Failed to load glossary dropdowns', err);
        throw err;
    });
}

// Initialize reference field behavior
function initReferenceField() {
    const refInput = document.getElementById('gloRef');
    const refHint = document.getElementById('refHint');
    const refPreview = document.getElementById('refPreview');
    
    if (!refInput || !refHint || !refPreview) return;
    
    // Handle input changes
    refInput.addEventListener('input', function() {
        const value = this.value.trim();
        
        if (value === '') {
            // Field is empty - show auto-generation hint
            refHint.textContent = 'Leave empty for auto-generation';
            refHint.style.display = 'inline';
            refPreview.style.display = 'none';
        } else {
            // User has entered a value - show manual input confirmation
            refHint.textContent = 'Using manual reference:';
            refHint.style.display = 'inline';
            refPreview.textContent = value;
            refPreview.style.display = 'inline';
        }
    });
    
    // Handle focus events
    refInput.addEventListener('focus', function() {
        if (this.value.trim() === '') {
            refHint.textContent = 'Leave empty for auto-generation';
            refPreview.style.display = 'none';
        }
    });
    
    // Handle blur events
    refInput.addEventListener('blur', function() {
        const value = this.value.trim();
        if (value === '') {
            refHint.textContent = 'Leave empty for auto-generation';
            refPreview.style.display = 'none';
        }
    });
    
    // Initial state
    refHint.textContent = 'Leave empty for auto-generation';
    refPreview.style.display = 'none';
}

function fillSelect(selectId, list, labelKey) {
    const el = document.getElementById(selectId);
    if (!el || !Array.isArray(list)) return;
    const current = el.value;
    list.forEach(item => {
        const opt = document.createElement('option');
        opt.value = item.id;
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
    // Add empty option first (default)
    const emptyOpt = document.createElement('option');
    emptyOpt.value = '';
    emptyOpt.textContent = '-';
    el.appendChild(emptyOpt);
    // Add rating options
    ratings.forEach(r => {
        const opt = document.createElement('option');
        opt.value = r.id;
        opt.textContent = String(r.values);
        el.appendChild(opt);
    });
    // Only restore previous value if it exists, otherwise leave empty
    if (current) el.value = current;
}

let glossaryParentOptions = [];
function setupGlossaryParentPicker(glossary) {
    glossaryParentOptions = Array.isArray(glossary) ? glossary : [];
    const input = document.getElementById('gloParentName');
    if (!input) return;
    if (input.dataset.pickerInit === '1') {
        const wrapperExisting = input.parentElement;
        const panelExisting = wrapperExisting ? wrapperExisting.querySelector('.parent-picker-panel') : null;
        if (panelExisting && panelExisting.style.display === 'block') {
            const searchInputExisting = panelExisting.querySelector('input');
            if (searchInputExisting) {
                searchInputExisting.dispatchEvent(new Event('input'));
            }
        }
        return;
    }
    // Build wrapper like searchable-select-wrapper
    const wrapper = document.createElement('div');
    wrapper.className = 'searchable-select-wrapper form-input-group';
    const parent = input.parentElement;
    if (!parent) return;
    parent.replaceChild(wrapper, input);
    input.readOnly = true;
    input.classList.add('form-input');
    input.id = 'gloParentName';
    wrapper.appendChild(input);
    const icon = document.createElement('i');
    icon.className = 'fas fa-edit input-icon';
    wrapper.appendChild(icon);

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
    searchInput.placeholder = 'Search glossary...';
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
    input.dataset.pickerInit = '1';

    function openPanel() {
        if (panel.style.display === 'block') return;
        panel.style.display = 'block';
        searchInput.value = '';
        renderList('');
        setTimeout(() => searchInput.focus(), 0);
    }
    function closePanel() { panel.style.display = 'none'; }
    function renderList(q) {
        const query = (q || '').toLowerCase();
        list.innerHTML = '';
        const data = Array.isArray(glossaryParentOptions) ? glossaryParentOptions : [];
        data.forEach(g => {
            const name = g.name || '';
            const desc = g.description || '';
            const searchable = (name + ' ' + desc).toLowerCase();
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
                input.value = name;
                input.dataset.parentId = String(g.id);
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
            empty.textContent = 'No glossary entries found';
            list.appendChild(empty);
        }
    }
    icon.addEventListener('click', (e) => { e.stopPropagation(); openPanel(); });
    input.addEventListener('click', (e) => { e.stopPropagation(); openPanel(); });
    searchInput.addEventListener('input', (e) => renderList(e.target.value));
    document.addEventListener('click', (e) => { if (!wrapper.contains(e.target)) closePanel(); });
    document.addEventListener('keydown', (e) => { if (e.key === 'Escape') closePanel(); });
}

async function reloadGlossaryParentsForSelectedSegment(previousSegmentId) {
    try {
        const svc = window.BUDG_API_SERVICE;
        if (!svc || typeof svc.getGlossaryList !== 'function') return;
        const selectedSegmentId = window.segmentField && typeof window.segmentField.getValue === 'function'
            ? parseInt(window.segmentField.getValue(), 10)
            : NaN;
        const list = await svc.getGlossaryList(
            Number.isInteger(selectedSegmentId) && selectedSegmentId > 0 ? { segmentId: selectedSegmentId } : {}
        );
        glossaryParentOptions = Array.isArray(list?.data) ? list.data : (Array.isArray(list) ? list : []);
        const input = document.getElementById('gloParentName');
        if (input && input.dataset.parentId) {
            const selectedParentId = parseInt(input.dataset.parentId, 10);
            const stillAllowed = glossaryParentOptions.some(g => parseInt(g.id || g.ID, 10) === selectedParentId);
            if (!stillAllowed) {
                alert('This parent is not valid for the selected segment. Please remove the parent first.');
                return false;
            }
        }
        if (input && input.dataset.pickerInit === '1') {
            const wrapper = input.parentElement;
            const panel = wrapper ? wrapper.querySelector('.parent-picker-panel') : null;
            if (panel && panel.style.display === 'block') {
                const searchInput = panel.querySelector('input');
                if (searchInput) searchInput.dispatchEvent(new Event('input'));
            }
        }
    } catch (e) {
        console.error('Failed to refresh glossary parent options for selected segment', e);
    }
    return true;
}

function collectGlossaryFormData() {
    const parentEl = document.getElementById('gloParentName');
    const parentId = parentEl && parentEl.dataset.parentId ? parseInt(parentEl.dataset.parentId, 10) : null;
    const aliasesContainer = document.getElementById('gloAliases');
    const aliases = (aliasesContainer && typeof aliasesContainer.getTags === 'function') ? aliasesContainer.getTags() : [];
    return {
        name: document.getElementById('gloName')?.value?.trim() || '',
        description: document.getElementById('gloDefinition')?.value?.trim() || '',
        format: document.getElementById('gloFormatDescription')?.value?.trim() || null,
        ldm: document.getElementById('gloLdmRef')?.value?.trim() || null,
        business_logic: document.getElementById('gloBusinessLogic')?.value?.trim() || null,
        examples: document.getElementById('gloExamples')?.value?.trim() || null,
        ref_number: document.getElementById('gloRef')?.value?.trim() || null,
        confidentiality_rating: parseInt(document.getElementById('gloCiaC')?.value || '', 10) || null,
        integrity_rating: parseInt(document.getElementById('gloCiaI')?.value || '', 10) || null,
        availability_rating: parseInt(document.getElementById('gloCiaA')?.value || '', 10) || null,
        status: parseInt(document.getElementById('gloStatus')?.value || '', 10) || null,
        lifecycle: parseInt(document.getElementById('gloLifecycle')?.value || '', 10) || null,
        is_public: parseInt(document.getElementById('gloViewing')?.value || '', 10) || null,
        kde: parseInt(document.getElementById('gloKde')?.value || '', 10) || null,
        type: parseInt(document.getElementById('gloType')?.value || '', 10) || null,
        security_classification: parseInt(document.getElementById('gloSecurity')?.value || '', 10) || null,
        format_type: parseInt(document.getElementById('gloFormatType')?.value || '', 10) || null,
        parent_id: parentId,
        aliases: aliases,
        segmentId: window.segmentField ? window.segmentField.getValue() : 1
    };
}


