// Org Unit Edit Page JavaScript - Version 2.0
(function() {
    // Segment field removed - org units don't have segments
    function parseId() {
        // First try URL params (for separate edit pages)
        const urlParams = new URLSearchParams(window.location.search);
        const id = parseInt(urlParams.get('id'), 10);
        if (!Number.isNaN(id)) return id;
        
        // Fallback to path-based ID (for main view pages)
        const parts = window.location.pathname.split('/').filter(Boolean);
        const idx = parts.indexOf('org-unit');
        if (idx === -1 || parts.length < idx + 2) return null;
        const pathId = parseInt(parts[idx + 1], 10);
        return Number.isNaN(pathId) ? null : pathId;
    }

    async function resolveReferences(unit) {
        const api = window.BUDG_API_SERVICE;
        const tasks = [];

        // Parent name from Parent_ID
        if (!unit.Parent_Name && (unit.Parent_ID || unit.parent_id)) {
            const parentId = unit.Parent_ID ?? unit.parent_id;
            tasks.push((async () => {
                try {
                    let p = await api.getOrgUnitById(parentId);
                    if (p && p.data) p = p.data;
                    unit.Parent_Name = p?.Name || p?.primaryname || p?.name || String(parentId);
                } catch(_) { /* ignore */ }
            })());
        }

        await Promise.all(tasks);
        return unit;
    }

    // Generate automatic reference
    // This function is kept for compatibility but does nothing.
    // Ref generation happens in the backend when saving if ref is empty,
    // ensuring unique sequential refs (OU001, OU002, etc.).
    function generateReference(nameInput, refInput) {
        // No-op: backend handles unique ref generation
    }


    async function loadOrgUnit(id) {
        try {
            let u = await window.BUDG_API_SERVICE.getOrgUnitById(id);
            if (u && u.data) u = u.data;
            if (!u) {
                throw new Error('Org unit not found');
            }
            u = await resolveReferences(u);

            const nameInput = document.getElementById('editNameInput');
            const refInput = document.getElementById('editRefInput');
            const descInput = document.getElementById('editDescInput');
            const parentInput = document.getElementById('editParentInput');
            
            if (nameInput) nameInput.value = u.name || u.primaryname || u.Name || '';
            if (refInput) refInput.value = u.reference || u.refnumber || u.Reference || '';
            if (descInput) descInput.value = u.description || u.Description || '';
            if (parentInput) parentInput.value = u.Parent_Name || u.parent_name || '';
            
            // Add event listener for name input to auto-generate reference
            if (nameInput && refInput) {
                console.log('Edit page: Adding event listeners for name input to auto-generate reference (only when empty)');
                const shouldAutoGenerate = () => !refInput.value || !refInput.value.trim();
                // If user edits reference manually, stop auto-generation
                refInput.addEventListener('input', () => {
                    // Once user types anything, auto-gen stops because shouldAutoGenerate() becomes false
                });
                nameInput.addEventListener('input', function() {
                    if (!shouldAutoGenerate()) return;
                    generateReference(nameInput, refInput);
                });
                nameInput.addEventListener('blur', function() {
                    if (!shouldAutoGenerate()) return;
                    generateReference(nameInput, refInput);
                });
            } else {
                console.error('Edit page: Name input or ref input not found for auto-generate reference');
            }
            
            // Update the page title with the org unit's name
            const orgUnitEditDisplayName = document.getElementById('orgUnitEditDisplayName');
            if (orgUnitEditDisplayName) {
                const orgUnitName = u.name || u.primaryname || u.Name || 'Edit Org Unit';
                orgUnitEditDisplayName.textContent = `Edit ${orgUnitName}`;
            }

            // Load and set status
            await loadStatuses();
            const statusSelect = document.getElementById('editStatusSelect');
            const currentStatusId = u.status_id ?? u.Status_ID ?? u.statusId;
            if (statusSelect && currentStatusId != null) {
                statusSelect.value = String(currentStatusId);
            }

            // Segment field removed
        } catch(err) {
            console.error('Failed to load org unit:', err);
            alert('Failed to load org unit data');
        }
    }

    async function loadStatuses() {
        try {
            const resp = await window.BUDG_API_SERVICE.getStatusesForDropdown();
            const select = document.getElementById('editStatusSelect');
            if (!select) return;
            
            select.innerHTML = '';
            const data = resp && resp.data ? resp.data : resp;
            (Array.isArray(data) ? data : []).forEach(s => {
                const opt = document.createElement('option');
                opt.value = s.id;
                opt.textContent = s.primaryname || s.name || s.Name || s.id;
                select.appendChild(opt);
            });
        } catch(err) {
            console.error('Failed to load statuses:', err);
        }
    }

    function initParentModal() {
        console.log('Edit page: Starting initParentModal');
        console.log('Edit page: Document ready state:', document.readyState);
        console.log('Edit page: Document body:', document.body);
        
        const modal = document.getElementById('editParentModal');
        const openBtn = document.getElementById('editParentBtn');
        const closeBtn = document.getElementById('editModalClose');
        const closeBtn2 = document.getElementById('editModalCloseBtn');
        const tableBody = document.getElementById('editTableBody');
        const searchInput = document.getElementById('editSearchInput');
        const clearBtn = document.getElementById('editSearchClearBtn');
        const parentInput = document.getElementById('editParentInput');
        
        console.log('Edit page: Modal elements found:');
        console.log('modal:', modal);
        console.log('openBtn:', openBtn);
        console.log('closeBtn:', closeBtn);
        console.log('closeBtn2:', closeBtn2);
        console.log('tableBody:', tableBody);
        console.log('searchInput:', searchInput);
        console.log('clearBtn:', clearBtn);
        console.log('parentInput:', parentInput);
        
        // All elements should be available at this point due to waitForElements
        if (!modal || !openBtn || !closeBtn || !closeBtn2 || !tableBody || !searchInput || !clearBtn || !parentInput) {
            console.error('Edit page: Critical elements still not found after waiting');
            return;
        }
        
        function hide() { 
            if (modal) modal.classList.remove('active'); 
        }
        
        async function show() { 
            console.log('Edit page: Opening modal');
            console.log('Edit page: Modal element:', modal);
            if (modal) {
                console.log('Edit page: Adding active class to modal');
            if (modal) modal.classList.add('active'); 
                console.log('Edit page: Modal classes after adding active:', modal.className);
                console.log('Edit page: Modal computed style display:', window.getComputedStyle(modal).display);
                console.log('Edit page: Modal computed style position:', window.getComputedStyle(modal).position);
                console.log('Edit page: Modal computed style z-index:', window.getComputedStyle(modal).zIndex);
            } else {
                console.error('Edit page: Modal element not found!');
            }
            all = await fetchAll();
            render(''); 
            if (searchInput) searchInput.value = ''; 
            console.log('Edit page: Modal should be visible now'); 
        }
        
        async function fetchAll() { 
            try {
                console.log('Edit page fetchAll: Starting to fetch org units...');
                const response = await fetch('/api/org-units');
                console.log('Edit page fetchAll: Response status:', response.status);
                if (!response.ok) {
                    throw new Error(`HTTP error! status: ${response.status}`);
                }
                const list = await response.json();
                console.log('Edit page fetchAll: Received data:', list);
            return Array.isArray(list) ? list : []; 
            } catch (error) {
                console.error('Error fetching org units:', error);
                return [];
            }
        }
        
        let all = [];
        
        function render(q) {
            console.log('Edit page render: Query:', q);
            console.log('Edit page render: All data:', all);
            console.log('Edit page render: All data length:', all ? all.length : 'null/undefined');
            
            const query = (q || '').toLowerCase();
            const rows = query ? 
                all.filter(x => 
                    String(x.name || x.Name || '').toLowerCase().includes(query) || 
                    String(x.description || x.Description || '').toLowerCase().includes(query)
                ) : all;
            
            console.log('Edit page render: Filtered rows:', rows);
            console.log('Edit page render: Rows length:', rows ? rows.length : 'null/undefined');
            
            if (tableBody) {
                console.log('Edit page: Rendering rows to table body');
                tableBody.innerHTML = rows.map(item => {
                    const name = item.name || item.Name || item.primaryname || '';
                    const description = item.description || item.Description || item.desc || '';
                    console.log('Edit page: Rendering item:', { name, description, item });
                    return `<tr data-name="${(item.name || item.Name || '').replace(/"/g, '&quot;')}">
                        <td><i class="fas fa-home item-icon"></i>${item.name || item.Name || ''}</td>
                        <td>${item.description || item.Description || ''}</td>
                    </tr>`;
                }).join('');
                console.log('Edit page: Table body innerHTML set');
            }
        }
        
        if (tableBody) {
            tableBody.addEventListener('click', (e) => {
                const tr = e.target.closest('tr'); 
                if (!tr) return; 
                if (parentInput) parentInput.value = tr.getAttribute('data-name') || ''; 
                hide();
            });
        }
        
        if (openBtn) {
            console.log('Edit page: Adding click listener to open button');
            openBtn.addEventListener('click', async (e) => { 
                e.preventDefault();
                e.stopPropagation();
                console.log('Edit page: Open button clicked');
                all = await fetchAll(); 
                show(); 
            });
        } else {
            console.error('Edit page: Open button not found!');
        }
        
        console.log('Edit page: initParentModal completed');
        
        if (closeBtn) closeBtn.addEventListener('click', (e) => {
            e.preventDefault();
            e.stopPropagation();
            hide();
        });
        if (closeBtn2) closeBtn2.addEventListener('click', hide);
        
        if (modal) {
            modal.addEventListener('click', (e) => { 
                if (e.target === modal) hide(); 
            });
        }
        
        if (searchInput) {
            searchInput.addEventListener('input', () => { 
                const q = searchInput.value.trim(); 
                if (!q) { 
                    render(''); 
                    if (clearBtn) clearBtn.classList.remove('show'); 
                } else { 
                    render(q); 
                    if (clearBtn) clearBtn.classList.add('show'); 
                } 
            });
        }
        
        if (clearBtn) {
            clearBtn.addEventListener('click', () => { 
                if (searchInput) searchInput.value = ''; 
                render(''); 
                clearBtn.classList.remove('show'); 
            });
        }
    }

    async function saveOrgUnit(id, closeAfter = false) {
        const nameInput = document.getElementById('editNameInput');
        const refInput = document.getElementById('editRefInput');
        const descInput = document.getElementById('editDescInput');
        const parentInput = document.getElementById('editParentInput');
        const statusSelect = document.getElementById('editStatusSelect');
        // Segment field removed

        if (!nameInput?.value.trim()) { 
            alert('Name is required'); 
            if (nameInput) nameInput.focus(); 
            return; 
        }
        
        if (!refInput?.value.trim()) { 
            alert('Reference is required'); 
            if (refInput) refInput.focus(); 
            return; 
        }
        
        if (!statusSelect?.value) { 
            alert('BUDG Status is required'); 
            if (statusSelect) statusSelect.focus(); 
            return; 
        }

        // Segment field removed

        const statusIdNumber = parseInt(statusSelect.value, 10);
        const statusNameSelected = (statusSelect.options[statusSelect.selectedIndex] || {}).textContent || '';
        
        // Sync rich-text editor content back to textarea before reading
        if (typeof syncAdvancedRichTextToTextarea === 'function') {
            syncAdvancedRichTextToTextarea('editDescInput');
        }

        const payload = {
            id: id,
            name: nameInput.value.trim(),
            reference: refInput.value.trim(),
            description: descInput?.value.trim() || '',
            parent_name: parentInput?.value.trim() || '',
            status_id: statusIdNumber,
            Status_ID: statusIdNumber,
            statusId: statusIdNumber,
            status_name: statusNameSelected
        };
        
        const buttons = [document.getElementById('editSaveBtn'), document.getElementById('editSaveCloseBtn')];
        
        try {
            buttons.forEach(b=>{ 
                if (b){ 
                    b.disabled = true; 
                    b.dataset._txt = b.textContent; 
                    b.textContent = 'Saving...'; 
                }
            });
            
            const res = await window.BUDG_API_SERVICE.updateOrgUnit(id, payload);
            const ok = res?.success === true || res?.updated === true || res?.status === 'OK' || !!res?.id;
            if (!ok) throw new Error('Update failed');
            
            // Save custom fields if context exists
            if (window.customFieldsContext && window.customFieldsContext.saveValues) {
                try {
                    await window.customFieldsContext.saveValues(id);
                    console.log('✅ Custom fields saved successfully');
                } catch (error) {
                    console.error('Error saving custom fields:', error);
                }
            }
            
            // Release lock after successful save
            await window.LockInitHelper.releaseLock();
            
            if (closeAfter) {
                window.location.href = `/view/org-unit/${id}`;
            } else {
                alert('Saved successfully!');
            }
        } catch (err) {
            alert(typeof window.formatSaveError === 'function' ? window.formatSaveError(err) : (err?.body?.error || err?.body?.message || err?.message || 'Failed to save'));
        } finally {
            buttons.forEach(b=>{ 
                if (b){ 
                    b.disabled = false; 
                    if (b.dataset._txt) b.textContent = b.dataset._txt; 
                }
            });
        }
    }

    // Wait for DOM to be fully loaded and then initialize
    function waitForElements() {
        const requiredElements = [
            'editParentModal', 'editParentBtn', 'editModalClose', 'editModalCloseBtn',
            'editTableBody', 'editSearchInput', 'editSearchClearBtn', 'editParentInput',
            'editNameInput', 'editRefInput', 'editDescInput', 'editStatusSelect',
            'editSaveBtn', 'editSaveCloseBtn', 'editCancelBtn'
        ];
        
        const missingElements = requiredElements.filter(id => !document.getElementById(id));
        
        if (missingElements.length > 0) {
            console.log('Edit page: Missing elements:', missingElements);
            setTimeout(waitForElements, 100);
            return;
        }
        
        console.log('Edit page: All required elements found, initializing...');
        initializePage();
    }
    
    async function initializePage() {
        const id = parseId();
        if (!id) {
            alert('No org unit ID provided');
            window.location.href = '/';
            return;
        }

        // Initialize lock
        const lockAcquired = await window.LockInitHelper.initializeLock('org-unit', id, 'org-unit');
        if (!lockAcquired) {
            return; // Lock initialization failed, user was redirected
        }

        // Initialize parent modal
        console.log('Edit page: Initializing parent modal');
        initParentModal();
        console.log('Edit page: Parent modal initialized');

        // Segment field removed

        // Load existing org unit data
        await loadOrgUnit(id);
        
        // Initialize custom fields
        if (window.CustomFields) {
            try {
                window.customFieldsContext = await window.CustomFields.initForm({
                    facetId: 'Org Unit',
                    containerId: 'customFieldsContainer',
                    mode: 'edit',
                    objectId: id
                });
                console.log('Custom fields initialized:', window.customFieldsContext);
            } catch (error) {
                console.error('Error initializing custom fields:', error);
            }
        }

        // Wire up action buttons
        const saveBtn = document.getElementById('editSaveBtn');
        const saveCloseBtn = document.getElementById('editSaveCloseBtn');
        const cancelBtn = document.getElementById('editCancelBtn');

        if (saveBtn) saveBtn.addEventListener('click', () => saveOrgUnit(id, false));
        if (saveCloseBtn) saveCloseBtn.addEventListener('click', () => saveOrgUnit(id, true));
        if (cancelBtn) cancelBtn.addEventListener('click', async () => {
            await window.LockInitHelper.releaseLock();
            window.location.href = `/view/org-unit/${id}`;
        });

        // Wire up editor button – advanced rich text editor
        const editorButton = document.querySelector('.editor-button');
        if (editorButton) {
            editorButton.addEventListener('click', function(e) {
                e.preventDefault();
                e.stopPropagation();
                toggleAdvancedRichTextEditor('editDescInput', editorButton);
            });
        }
    }

    // Rich Text Editor function
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
        editor.innerHTML = textarea.value || '';

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

    document.addEventListener('DOMContentLoaded', function() {
        console.log('Edit page: DOMContentLoaded fired');
        waitForElements();
    });
})();

