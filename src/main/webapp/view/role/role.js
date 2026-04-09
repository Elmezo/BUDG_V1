(function() {
    function parseId() {
        const parts = window.location.pathname.split('/').filter(Boolean);
        const idx = parts.indexOf('role');
        if (idx === -1 || parts.length < idx + 2) return null;
        const id = parseInt(parts[idx + 1], 10);
        return Number.isNaN(id) ? null : id;
    }

    function escapeHtml(str) {
        if (str == null) return '';
        return String(str).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;').replace(/'/g,'&#039;');
    }

    function _richHtml(val) {
        if (val == null || String(val).trim() === '') return '<span class="empty">-</span>';
        var s = String(val);
        if (!/<[a-z][\s\S]*>/i.test(s)) return escapeHtml(s);
        var t = document.createElement('template'); t.innerHTML = s;
        t.content.querySelectorAll('script,style,iframe,object,embed,link,meta').forEach(function(e){e.remove();});
        t.content.querySelectorAll('*').forEach(function(el){
            Array.from(el.attributes).forEach(function(a){
                if(a.name.toLowerCase().indexOf('on')===0) el.removeAttribute(a.name);
                if((a.name==='href'||a.name==='src')&&/^\s*javascript:/i.test(a.value)) el.removeAttribute(a.name);
            });
        });
        return '<div class="rich-html-content">' + t.innerHTML + '</div>';
    }

    function renderItem(label, valueHtml) {
        return `<div class=\"view-item\"><div class=\"view-label\">${label}</div><div class=\"view-value\">${valueHtml ?? '<span class=\"empty\">-</span>'}</div></div>`;
    }


    async function load(id) {
        const container = document.getElementById('roleViewContainer');
        container.innerHTML = '<div class="view-section" style="grid-column:1/-1;">Loading...</div>';
        try {
            // If ROLE endpoint not available, we used STATUS as fallback in api-service
            let r = await window.BUDG_API_SERVICE.getRoleById(id);
            try { window.BUDG_API_SERVICE.logVisit({entity: 'Role', entityId: String(id), route: `/view/role/${id}` }); } catch(_) {}
            if (r && r.data) r = r.data;
            const name = r?.primaryname || r?.Name || r?.name || '';
            const desc = r?.description || r?.Description || r?.desc || '';

            const left = `
                <div class="view-section">
                    <div class="section-title">SUMMARY</div>
                    ${renderItem('Name', escapeHtml(name))}
                    ${renderItem('Description', _richHtml(desc))}
                </div>
            `;

            container.innerHTML = left;
            
            // Render custom fields section
            if (window.CustomFields) {
                try {
                    await window.CustomFields.renderViewSection({
                        facetId: 'Role',
                        containerId: 'roleViewContainer',
                        objectId: id,
                        title: 'CUSTOM FIELDS'
                    });
                } catch (error) {
                    console.error('Error rendering custom fields:', error);
                }
            }
            
        } catch (e) {
            container.innerHTML = `<div class=\"view-section\" style=\"grid-column:1/-1;color:var(--danger,#b91c1c);\">Failed to load role (id=${id}).</div>`;
        }
    }

    document.addEventListener('DOMContentLoaded', function() {
        const backBtn = document.getElementById('backBtn');
        if (backBtn) backBtn.addEventListener('click', () => window.history.length>1?window.history.back():window.location.assign('/'));
        const id = parseId();
        if (id != null) {
            load(id).then(async function(){
                // Check and display lock status
                if (window.ViewLockHelper) {
                    await window.ViewLockHelper.checkAndDisplayLockStatus('role', id);
                }
                
                if (window.addFollowButton) {
                    try {
                        await window.addFollowButton('role', id, null, '.form-actions');
                    } catch (e) {
                        console.error('Failed to initialize follow button for role:', e);
                    }
                }
            });
        }

        // Wire up tab edit button (role only has summary tab)
        const tabEditBtn = document.getElementById('tabEditBtn');
        if (tabEditBtn) {
            tabEditBtn.addEventListener('click', async function() {
                if (id != null) {
                    // Check lock status before navigating
                    if (window.ViewLockHelper) {
                        const canEdit = await window.ViewLockHelper.interceptEditButton(
                            'role',
                            id,
                            function() {
                                // Role only has summary tab, so always go to edit page
                                window.location.href = `/view/role/role-edit.html?id=${id}`;
                            }
                        );
                        if (!canEdit) return; // Lock check failed, navigation prevented
                    } else {
                        // Fallback if ViewLockHelper not available
                        window.location.href = `/view/role/role-edit.html?id=${id}`;
                    }
                }
            });
        }
    });
})();


