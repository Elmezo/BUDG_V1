(function() {
    function parseId() {
        // First try URL params (for separate edit pages)
        const urlParams = new URLSearchParams(window.location.search);
        const id = parseInt(urlParams.get('id'), 10);
        if (!Number.isNaN(id)) return id;
        
        // Fallback to path-based ID (for main view pages)
        const parts = window.location.pathname.split('/').filter(Boolean);
        const idx = parts.indexOf('role');
        if (idx === -1 || parts.length < idx + 2) return null;
        const pathId = parseInt(parts[idx + 1], 10);
        return Number.isNaN(pathId) ? null : pathId;
    }

    async function loadRole(id) {
        try {
            let r = await window.BUDG_API_SERVICE.getRoleById(id);
            if (r && r.data) r = r.data;
            
            const setVal = (i, v) => { const el = document.getElementById(i); if (el) el.value = v ?? ''; };
            
            setVal('roleName', r?.primaryname || r?.Name || r?.name || '');
            setVal('roleDescription', r?.description || r?.Description || r?.desc || '');
        } catch(err) {
            console.error('Failed to load role:', err);
            alert('Failed to load role data');
        }
    }

    async function saveRole(id, closeAfter = false) {
        // Sync rich-text editor content back to textarea before reading
        if (typeof syncAdvancedRichTextToTextarea === 'function') {
            syncAdvancedRichTextToTextarea('roleDescription');
        }

        const name = document.getElementById('roleName')?.value.trim();
        const description = document.getElementById('roleDescription')?.value.trim() || null;

        const required = [
            { ok: !!name, field: 'Name' }
        ];
        const missing = required.filter(r => !r.ok).map(r => r.field);
        if (missing.length) { 
            alert('Please fill required fields: ' + missing.join(', ')); 
            return; 
        }

        const payload = { 
            name,
            description
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
            
            const res = await window.BUDG_API_SERVICE.updateRole(id, payload);
            if (res && res.success === false) throw new Error(res.message || 'Update failed');
            
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
                window.location.href = `/view/role/${id}`;
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

    document.addEventListener('DOMContentLoaded', async function() {
        const id = parseId();
        if (!id) {
            alert('No role ID provided');
            window.location.href = '/';
            return;
        }

        // Initialize lock
        const lockAcquired = await window.LockInitHelper.initializeLock('role', id, 'role');
        if (!lockAcquired) {
            return; // Lock initialization failed, user was redirected
        }

        // Load existing role data
        await loadRole(id);
        
        // Initialize custom fields
        if (window.CustomFields) {
            try {
                window.customFieldsContext = await window.CustomFields.initForm({
                    facetId: 'Role',
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

        if (saveBtn) saveBtn.addEventListener('click', () => saveRole(id, false));
        if (saveCloseBtn) saveCloseBtn.addEventListener('click', () => saveRole(id, true));
        if (cancelBtn) cancelBtn.addEventListener('click', async () => {
            await window.LockInitHelper.releaseLock();
            window.location.href = `/view/role/${id}`;
        });

        // Show editor button – advanced rich text editor
        const showEditorBtn = document.getElementById('showEditorBtn');
        if (showEditorBtn) {
            showEditorBtn.addEventListener('click', function(e) {
                e.preventDefault();
                e.stopPropagation();
                toggleAdvancedRichTextEditor('roleDescription', showEditorBtn);
            });
        }
    });
})();

