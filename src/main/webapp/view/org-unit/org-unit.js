(function() {
    function t(key, params) {
        return (window.I18n && typeof window.I18n.t === 'function') ? window.I18n.t(key, params) : key;
    }

    function parseId() {
        const parts = window.location.pathname.split('/').filter(Boolean);
        const idx = parts.indexOf('org-unit');
        if (idx === -1 || parts.length < idx + 2) return null;
        const id = parseInt(parts[idx + 1], 10);
        return Number.isNaN(id) ? null : id;
    }

	function hideEditControls() {
		// Hide the edit dropdown for unauthorized users
		if (window.EditDropdown) {
			window.EditDropdown.hideEditControls();
		}
		try { document.querySelectorAll('[data-action="edit"]').forEach(function(el){ el.style.display = 'none'; }); } catch(_) {}
	}

	async function hideEditsIfUnauthenticated() {
		try {
			// Use the new permissions API to check user permissions for Org Units module
			const permResp = await fetch('/api/user/permissions/Org Units', { method: 'GET', credentials: 'include' });
			if (!permResp.ok) { 
				console.log('API /api/user/permissions/Org Units failed:', permResp.status);
				// Fallback to old method
				await hideEditsIfUnauthenticatedFallback();
				return; 
			}
			const perms = await permResp.json();
			console.log('User permissions for Org Units:', perms);
			
			if (perms.success) {
				const canEdit = perms.canEdit || perms.isAdmin;
				const canDelete = perms.canDelete; // Only Super Admins can delete
				const isAdmin = perms.isAdmin;
				
				console.log('Can edit?', canEdit);
				console.log('Can delete?', canDelete);
				console.log('Is admin?', isAdmin);
				
				// Show the main edit dropdown if user has edit permission
				// Note: Create functionality is now in the header Create menu, not here
				if (canEdit || isAdmin) {
					console.log('User has edit permission - showing edit controls');
					showEditControls();
				} else {
					console.log('User does not have edit permission - hiding edit controls');
					hideEditControls();
				}
				
				// Hide delete buttons for non-admins (only admin/super admin can delete)
				if (!canDelete) {
					hideDeleteControls();
				}
				
				// Apply permission visibility to the dropdown
				if (window.EditDropdown && typeof window.EditDropdown.applyPermissionVisibility === 'function') {
					window.EditDropdown.applyPermissionVisibility({
						canEdit: canEdit,
						canDelete: canDelete,
						isAdmin: isAdmin
					});
				}
			} else {
				hideEditControls();
			}
		} catch (e) {
			console.log('Error in hideEditsIfUnauthenticated:', e);
			// Fallback to old method
			await hideEditsIfUnauthenticatedFallback();
		}
	}
	
	// Fallback to old admin check method
	async function hideEditsIfUnauthenticatedFallback() {
		try {
			const meResp = await fetch('/api/me', { method: 'GET', credentials: 'include' });
			if (!meResp.ok) { 
				hideEditControls(); 
				return; 
			}
			const me = await meResp.json();
			const role = (me.role || me.Role || me.userRole || '').toString().toLowerCase();
			const isAdmin = role === 'admin' || role === 'super admin' || role === 'super-admin' || role === 'super_admin' || role === 'suber admin';
			if (!isAdmin) {
				hideEditControls();
			} else {
				showEditControls();
			}
		} catch (e) {
			hideEditControls();
		}
	}
	
	function hideDeleteControls() {
		// Hide delete buttons - only admin/super admin can delete
		const deleteSelectors = [
			'[data-action="delete"]',
			'.delete-btn',
			'.btn-delete',
			'#deleteBtn',
			'#deleteOrgUnitBtn'
		];
		deleteSelectors.forEach(selector => {
			try {
				document.querySelectorAll(selector).forEach(el => {
					el.style.display = 'none';
				});
			} catch (_) {}
		});
		console.log('Delete controls hidden - only admin can delete');
	}

	function showEditControls() {
		// Show the edit dropdown for authorized users
		if (window.EditDropdown) {
			window.EditDropdown.showEditControls();
		}
		try { 
			document.querySelectorAll('[data-action="edit"]').forEach(function(el){ 
				el.style.display = '';
				el.style.setProperty('display', '', 'important');
			}); 
		} catch(_) {}
		console.log('Edit controls shown for admin user');
	}

    function escapeHtml(str) {
        if (str == null) return '';
        return String(str).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;').replace(/'/g,'&#039;');
    }

    /** True if org unit row represents a soft-deleted record (API / list payloads). */
    function isDeletedOrgUnitRow(u) {
        if (!u || typeof u !== 'object') return true;
        const d = u.deleted_date ?? u.deleted_Date ?? u.Deleted_Date ?? u.deletedDate;
        if (d == null || d === '') return false;
        if (typeof d === 'string' && d.trim() === '') return false;
        return true;
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

    function formatDateTime(value) {
        if (!value) return '';
        try {
            const d = new Date(value);
            if (isNaN(d.getTime())) return escapeHtml(String(value));
            return d.toLocaleDateString() + ' ' + d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
        } catch (_) {
            return escapeHtml(String(value));
        }
    }

    function renderItem(label, valueHtml) {
        return `<div class="view-item"><div class="view-label">${label}</div><div class="view-value">${valueHtml ?? '<span class=\"empty\">-</span>'}</div></div>`;
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
                    if (isDeletedOrgUnitRow(p)) {
                        unit.Parent_Name = '';
                        return;
                    }
                    unit.Parent_Name = p?.Name || p?.primaryname || p?.name || String(parentId);
                } catch(_) { /* ignore */ }
            })());
        }

        // Status name from status_id
        if (!unit.Status_Name && (unit.status_id || unit.Status_ID)) {
            const statusId = unit.status_id ?? unit.Status_ID;
            tasks.push((async () => {
                try {
                    let s = await api.getStatusById(statusId);
                    if (s && s.data) s = s.data;
                    unit.Status_Name = s?.primaryname || s?.Name || s?.name || String(statusId);
                } catch(_) { /* ignore */ }
            })());
        }

        // Last updated by person
        if (!unit.Last_Updated_By && (unit.lastupdateuser_ID || unit.last_updated_by)) {
            const userId = unit.lastupdateuser_ID ?? unit.last_updated_by;
            tasks.push((async () => {
                try {
                    let person = await api.getPersonById(userId);
                    if (person && person.data) person = person.data;
                    const fullName = [person?.First_Name || person?.first_name, person?.Last_Name || person?.last_name]
                        .filter(Boolean).join(' ').trim();
                    unit.Last_Updated_By = fullName || person?.Email || person?.email || String(userId);
                } catch(_) { /* ignore */ }
            })());
        }

        await Promise.all(tasks);
        return unit;
    }


    async function load(id) {
        const container = document.getElementById('orgUnitViewContainer');
        container.innerHTML = '<div class="view-section" style="grid-column:1/-1;">' + t('orgUnit.messages.loading') + '</div>';
        try {
            let u = await window.BUDG_API_SERVICE.getOrgUnitById(id);
            try { window.BUDG_API_SERVICE.logVisit({  entity: 'OrgUnit', entityId: String(id), route: `/view/org-unit/${id}` }); } catch(_) {}
            if (u && u.data) u = u.data;
            // Resolve FK -> names when API returns only ids
            u = await resolveReferences(u);
            
            // Update the page title with the org unit's name
            const orgUnitDisplayName = document.getElementById('orgUnitDisplayName');
            if (orgUnitDisplayName) {
                const orgUnitName = u.Name || u.PrimaryName || u.primaryname || u.primaryName
                    || u.name || u.displayName || u.DisplayName || t('orgUnit.pageTitle');
                // Remove data-i18n so I18n.applyTranslations() (e.g. after EditDropdown init) does not overwrite the name
                orgUnitDisplayName.removeAttribute('data-i18n');
                orgUnitDisplayName.textContent = orgUnitName;
                try { document.title = 'BUDG - ' + String(orgUnitName); } catch (_) {}
            }

            const statusName = (u.Status_Name || u.status_name || u.status || '').toString();
            const statusColor = /deleted/i.test(statusName) ? '#dc2626' : /active/i.test(statusName) ? '#16a34a' : '#9ca3af';

            // If never updated, fall back to created date for "Last Updated"
            const createdAt = u.created_date || u.Created_Date || u.Created_On || u.created_on || u.createdAt || u.created_at || u.Created || u.created;
            const lastUpdatedAt = u.last_updated_date || u.Last_Updated_On || u.last_updated_on || u.updatedAt || u.updated_at || u.Last_Updated || u.updated || createdAt;

            // Create two-column card-based layout
            container.innerHTML = `
                <div class="org-unit-view-container">
                    <!-- Left Column - Definition -->
                    <div class="left-column">
                        <div class="form-section">
                            <h4 class="section-title">${t('orgUnit.sections.definition')}</h4>
                            <div class="form-fields-container">
                                ${renderItem(t('orgUnit.labels.name'), escapeHtml(u.Name || u.primaryname || u.name || ''))}
                                ${renderItem(t('orgUnit.labels.reference'), escapeHtml(u.Reference || u.refnumber || u.reference || ''))}
                                ${renderItem(t('orgUnit.labels.description'), _richHtml(u.Description || u.description || ''))}
                                ${renderItem(t('orgUnit.labels.parent'), (u.Parent_Name || u.parent_name) ? ((u.Parent_ID || u.parent_id) ? `<a href="/view/org-unit/${u.Parent_ID || u.parent_id}" class="org-link">${escapeHtml(u.Parent_Name || u.parent_name)}</a>` : escapeHtml(u.Parent_Name || u.parent_name)) : '<span class="empty">-</span>')}
                            </div>
                        </div>
                    </div>
                    
                    <!-- Right Column - Classifications -->
                    <div class="right-column">
                        <div class="form-section">
                            <h4 class="section-title">${t('orgUnit.sections.classifications')}</h4>
                            <div class="form-fields-container">
                                ${renderItem(t('orgUnit.labels.status'), `<span style=\"display:inline-flex;align-items:center;gap:.4rem;\"><span style=\"width:.5rem;height:.5rem;border-radius:9999px;background:${statusColor};display:inline-block\"></span>${escapeHtml(statusName || '-')}</span>`)}
                                ${renderItem(t('orgUnit.labels.created'), formatDateTime(u.created_date || u.Created_Date || u.Created_On || u.created_on || u.createdAt || u.created_at || u.Created || u.created))}
                                ${renderItem(t('orgUnit.labels.lastUpdatedBy'), escapeHtml(u.Last_Updated_By || u.lastupdateuser_ID || u.last_updated_by || u.updatedBy || u.updated_by || ''))}
                                ${renderItem(t('orgUnit.labels.lastUpdated'), formatDateTime(lastUpdatedAt))}
                            </div>
                        </div>
                    </div>
                </div>
            `;
            
            // Render custom fields section
            if (window.CustomFields) {
                try {
                    await window.CustomFields.renderViewSection({
                        facetId: 'Org Unit',
                        containerId: 'orgUnitViewContainer',
                        objectId: id,
                        title: t('orgUnit.sections.customFields')
                    });
                } catch (error) {
                    console.error('Error rendering custom fields:', error);
                }
            }

            // Add follow button to header actions
            if (window.addFollowButton) {
                try {
                    await window.addFollowButton('org-unit', id, null, '.form-actions');
                } catch (e) {
                    console.error('Failed to initialize follow button for org unit:', e);
                }
            }
            
        } catch (e) {
            const isForbidden = e?.status === 403 || String(e?.message || '').includes('403');
            const message = isForbidden ? 'This object is not available.' : t('orgUnit.messages.failedToLoad', { id: id });
            container.innerHTML = '<div class="view-section" style="grid-column:1/-1;color:var(--danger,#b91c1c);">' + message + '</div>';
        }
    }

    function buildDirectLineageTree(orgUnits, currentId) {
        const byId = new Map();
        orgUnits.forEach(u => {
            const id = u.ID ?? u.id;
            byId.set(id, u);
        });
        
        console.log('Org Unit - Building FULL EXTENDED FAMILY TREE for ID:', currentId);
        console.log('Org Unit - Available org units:', orgUnits.map(u => ({ id: u.ID ?? u.id, name: u.name || u.Name, parent_id: u.Parent_ID ?? u.parent_id })));
        
        // Find ALL ancestors (parents, grandparents, great-grandparents, etc.)
        const ancestors = new Set();
        let current = byId.get(currentId);
        console.log('Org Unit - Current org unit:', current);
        
        while (current) {
            const parentId = current.Parent_ID ?? current.parent_id;
            console.log('Org Unit - Looking for parent ID:', parentId);
            if (parentId && parentId !== 0 && byId.has(parentId)) {
                ancestors.add(parentId);
                current = byId.get(parentId);
                console.log('Org Unit - Found ancestor:', current);
            } else {
                break;
            }
        }
        
        // Find ALL descendants (children, grandchildren, great-grandchildren, etc.)
        const descendants = new Set();
        function findDescendants(id) {
            orgUnits.forEach(u => {
                const parentId = u.Parent_ID ?? u.parent_id;
                if (parentId === id) {
                    const childId = u.ID ?? u.id;
                    descendants.add(childId);
                    console.log('Org Unit - Found descendant:', u);
                    findDescendants(childId); // Recursively find ALL descendants
                }
            });
        }
        findDescendants(currentId);
        
        // Find ALL siblings (other children of the same parent)
        const siblings = new Set();
        const currentOrgUnit = byId.get(currentId);
        if (currentOrgUnit) {
            const currentParentId = currentOrgUnit.Parent_ID ?? currentOrgUnit.parent_id;
            console.log('Org Unit - Current parent ID:', currentParentId);
            if (currentParentId) {
                orgUnits.forEach(u => {
                    const parentId = u.Parent_ID ?? u.parent_id;
                    if (parentId === currentParentId && (u.ID ?? u.id) !== currentId) {
                        siblings.add(u.ID ?? u.id);
                        console.log('Org Unit - Found sibling:', u);
                    }
                });
            }
        }
        
        // Find ALL siblings of ancestors (uncles, aunts, cousins, etc.)
        const extendedFamily = new Set();
        ancestors.forEach(ancestorId => {
            const ancestor = byId.get(ancestorId);
            if (ancestor) {
                const ancestorParentId = ancestor.Parent_ID ?? ancestor.parent_id;
                if (ancestorParentId) {
                    orgUnits.forEach(u => {
                        const parentId = u.Parent_ID ?? u.parent_id;
                        if (parentId === ancestorParentId && (u.ID ?? u.id) !== ancestorId) {
                            extendedFamily.add(u.ID ?? u.id);
                            console.log('Org Unit - Found extended family member:', u);
                        }
                    });
                }
            }
        });
        
        // Find ALL siblings of descendants (nieces, nephews, etc.)
        descendants.forEach(descendantId => {
            const descendant = byId.get(descendantId);
            if (descendant) {
                const descendantParentId = descendant.Parent_ID ?? descendant.parent_id;
                if (descendantParentId) {
                    orgUnits.forEach(u => {
                        const parentId = u.Parent_ID ?? u.parent_id;
                        if (parentId === descendantParentId && (u.ID ?? u.id) !== descendantId) {
                            extendedFamily.add(u.ID ?? u.id);
                            console.log('Org Unit - Found extended family member:', u);
                        }
                    });
                }
            }
        });
        
        // CRITICAL: Ensure we include ALL children of the current org unit
        const currentChildren = new Set();
        orgUnits.forEach(u => {
            const parentId = u.Parent_ID ?? u.parent_id;
            if (parentId === currentId) {
                currentChildren.add(u.ID ?? u.id);
                console.log('Org Unit - Found direct child:', u);
            }
        });
        
        // CRITICAL: Ensure we include ALL siblings of the current org unit
        const currentSiblings = new Set();
        if (currentOrgUnit) {
            const currentParentId = currentOrgUnit.Parent_ID ?? currentOrgUnit.parent_id;
            if (currentParentId) {
                orgUnits.forEach(u => {
                    const parentId = u.Parent_ID ?? u.parent_id;
                    if (parentId === currentParentId && (u.ID ?? u.id) !== currentId) {
                        currentSiblings.add(u.ID ?? u.id);
                        console.log('Org Unit - Found direct sibling:', u);
                    }
                });
            }
        }
        
        // CRITICAL: Find children of siblings (nieces and nephews)
        const childrenOfSiblings = new Set();
        currentSiblings.forEach(siblingId => {
            orgUnits.forEach(u => {
                const parentId = u.Parent_ID ?? u.parent_id;
                if (parentId === siblingId) {
                    childrenOfSiblings.add(u.ID ?? u.id);
                    console.log('Org Unit - Found child of sibling (niece/nephew):', u);
                }
            });
        });
        
        // CRITICAL: Find children of siblings of parent (cousins) - SIMPLIFIED
        const childrenOfParentSiblings = new Set();
        if (currentOrgUnit) {
            const currentParentId = currentOrgUnit.Parent_ID ?? currentOrgUnit.parent_id;
            if (currentParentId) {
                const parent = byId.get(currentParentId);
                if (parent) {
                    const grandparentId = parent.Parent_ID ?? parent.parent_id;
                    if (grandparentId) {
                        // Find siblings of parent (uncles/aunts)
                        orgUnits.forEach(uncleAunt => {
                            const uncleAuntParentId = uncleAunt.Parent_ID ?? uncleAunt.parent_id;
                            if (uncleAuntParentId === grandparentId && (uncleAunt.ID ?? uncleAunt.id) !== currentParentId) {
                                console.log('Org Unit - Found uncle/aunt:', uncleAunt);
                                // Find children of this uncle/aunt (cousins)
                                orgUnits.forEach(u => {
                                    const parentId = u.Parent_ID ?? u.parent_id;
                                    if (parentId === (uncleAunt.ID ?? uncleAunt.id)) {
                                        childrenOfParentSiblings.add(u.ID ?? u.id);
                                        console.log('Org Unit - Found cousin:', u);
                                    }
                                });
                            }
                        });
                    }
                }
            }
        }
        
        console.log('Org Unit - Ancestors:', Array.from(ancestors));
        console.log('Org Unit - Descendants:', Array.from(descendants));
        console.log('Org Unit - Siblings:', Array.from(siblings));
        console.log('Org Unit - Extended Family:', Array.from(extendedFamily));
        console.log('Org Unit - Current Children:', Array.from(currentChildren));
        console.log('Org Unit - Current Siblings:', Array.from(currentSiblings));
        console.log('Org Unit - Children of Siblings (Nieces/Nephews):', Array.from(childrenOfSiblings));
        console.log('Org Unit - Children of Parent Siblings (Cousins):', Array.from(childrenOfParentSiblings));
        
        // DEBUG: Test if we can find any siblings at all
        console.log('Org Unit - DEBUG: Testing sibling detection...');
        if (currentOrgUnit) {
            const currentParentId = currentOrgUnit.Parent_ID ?? currentOrgUnit.parent_id;
            console.log('Org Unit - DEBUG: Current parent ID:', currentParentId);
            if (currentParentId) {
                const allChildrenOfParent = orgUnits.filter(u => {
                    const parentId = u.Parent_ID ?? u.parent_id;
                    return parentId === currentParentId;
                });
                console.log('Org Unit - DEBUG: All children of parent:', allChildrenOfParent.map(u => ({ id: u.ID ?? u.id, name: u.name || u.Name })));
            }
        }
        
        // Include the current org unit, all its ancestors, all its descendants, all its siblings, all extended family, and CRITICAL direct relationships
        const includedIds = new Set([
            currentId, 
            ...ancestors, 
            ...descendants, 
            ...siblings, 
            ...extendedFamily,
            ...currentChildren,          // CRITICAL: Direct children
            ...currentSiblings,           // CRITICAL: Direct siblings
            ...childrenOfSiblings,        // CRITICAL: Children of siblings (nieces/nephews)
            ...childrenOfParentSiblings  // CRITICAL: Children of parent siblings (cousins)
        ]);
        
        // Filter org units to include the COMPLETE EXTENDED FAMILY TREE
        const result = orgUnits.filter(u => {
            const id = u.ID ?? u.id;
            return includedIds.has(id);
        });
        
        console.log('Org Unit - Final COMPLETE FAMILY TREE:', result);
        return result;
    }

    function buildHierarchyTree(orgUnits, rootId) {
        const byParent = new Map();
        const byId = new Map();
        orgUnits.forEach(u => {
            const id = u.ID ?? u.id;
            const parentId = u.Parent_ID ?? u.parent_id ?? null;
            byId.set(id, u);
            if (!byParent.has(parentId)) byParent.set(parentId, []);
            byParent.get(parentId).push(u);
        });
        byParent.forEach(list => list.sort((a,b) => String(a.Name||a.name||'').localeCompare(String(b.Name||b.name||''))));

        // Compute child counts recursively
        const childCount = new Map();
        function countDescendants(id) {
            const children = byParent.get(id) || [];
            let total = children.length;
            children.forEach(c => { total += countDescendants(c.ID ?? c.id); });
            childCount.set(id, total);
            return total;
        }
        countDescendants(rootId);

        // Build DFS including root itself
        const result = [];
        function dfs(node, depth) {
            const id = node.ID ?? node.id;
            result.push({ node, depth, childCount: childCount.get(id) || 0, hasChildren: (byParent.get(id) || []).length > 0 });
            const children = byParent.get(id) || [];
            children.forEach(child => dfs(child, depth + 1));
        }
        const root = byId.get(rootId);
        if (root) dfs(root, 0);
        else (byParent.get(rootId) || []).forEach(child => dfs(child, 0));
        return { rows: result, parentMap: new Map([...byId.entries()].map(([i, n]) => [i, n.Parent_ID ?? n.parent_id ?? null])) };
    }

    function renderComponentsTable(hierarchyRows, currentId) {
        const table = document.createElement('div');
        table.className = 'view-section';
        const rowsHtml = hierarchyRows.rows.map(({ node, depth, childCount, hasChildren }) => {
            const name = node.Name || node.name || '';
            const desc = node.Description || node.description || '';
            const isCurrent = String(node.ID ?? node.id) === String(currentId);
            const id = node.ID ?? node.id;
            const parentId = node.Parent_ID ?? node.parent_id ?? '';
            const indent = Array(depth).fill('<span class="tree-indent"></span>').join('');
            const expander = hasChildren ? `<button type="button" class="tree-expander" aria-label="Toggle"><i class="fas fa-caret-down"></i></button>` : '<span class="tree-placeholder"></span>';
            const countBadge = hasChildren ? `<span class="child-count" title="${t('orgUnit.messages.children')}">${childCount}</span>` : '';
            const link = `<a class="org-link" href="/view/org-unit/${encodeURIComponent(id)}">${escapeHtml(name)}</a>`;
            return `<tr class="${isCurrent ? 'current-row' : ''}" data-id="${id}" data-parent-id="${parentId}" data-depth="${depth}">
                <td><div class="tree-cell">${indent}${expander}${depth>0?'<span class="tree-branch"></span>':''}<i class="fas fa-sitemap item-icon"></i><span class="org-name">${link}</span>${countBadge}</div></td>
                <td><span title="${escapeHtml(desc)}">${escapeHtml(desc)}</span></td>
            </tr>`;
        }).join('');

        table.innerHTML = `
            <div class="section-title">${t('orgUnit.sections.orgUnitLineage')}</div>
            <div class="people-table-wrapper">
                <table class="people-table">
                    <thead>
                        <tr>
                            <th>${t('orgUnit.pageTitle')}</th>
                            <th>${t('orgUnit.labels.description')}</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${rowsHtml}
                    </tbody>
                </table>
            </div>
        `;
        return table.outerHTML;
    }

    function initComponentsInteractions(containerEl, hierarchyRows) {
        const collapsed = new Set();
        const parentMap = hierarchyRows.parentMap;
        const tbody = containerEl.querySelector('tbody');
        if (!tbody) return;
        function computeParent(id) {
            return parentMap.get(Number(id)) ?? parentMap.get(id) ?? null;
        }
        function updateVisibility() {
            tbody.querySelectorAll('tr').forEach(tr => {
                let pid = tr.getAttribute('data-parent-id');
                let hide = false;
                while (pid && !hide) {
                    if (collapsed.has(String(pid))) hide = true;
                    pid = computeParent(pid);
                }
                tr.style.display = hide ? 'none' : '';
            });
            tbody.querySelectorAll('tr').forEach(tr => {
                const id = tr.getAttribute('data-id');
                const btn = tr.querySelector('.tree-expander i');
                if (btn) btn.style.transform = collapsed.has(String(id)) ? 'rotate(-90deg)' : 'rotate(0deg)';
            });
        }
        tbody.addEventListener('click', (e) => {
            const button = e.target.closest('.tree-expander');
            if (!button) return;
            const tr = button.closest('tr');
            const id = tr.getAttribute('data-id');
            if (collapsed.has(id)) collapsed.delete(id); else collapsed.add(id);
            updateVisibility();
        });
        updateVisibility();
    }

    async function loadComponents(orgUnitId) {
        const container = document.getElementById('orgUnitComponentsContainer');
        
        // Check if data is already cached
        if (tabCache.components && tabCache.components.orgUnitId === orgUnitId) {
            container.innerHTML = tabCache.components.html;
            initComponentsInteractions(container, tabCache.components.hierarchyRows);
            return;
        }

        container.innerHTML = '<div class="view-section" style="grid-column:1/-1;">' + t('orgUnit.messages.loading') + '</div>';
        try {
            // Fetch all org units
            let list = await window.BUDG_API_SERVICE.getOrgUnits();
            if (list && list.data) list = list.data;
            const normalized = Array.isArray(list) ? list : [];
            const activeOnly = normalized.filter(u => !isDeletedOrgUnitRow(u));
            
            // Build a filtered tree showing only the direct lineage path and descendants
            const filteredUnits = buildDirectLineageTree(activeOnly, orgUnitId);
            console.log('Org Unit - Filtered units:', filteredUnits);
            
            // Find the root of the filtered tree (the topmost ancestor)
            const rootOrgUnit = filteredUnits.find(unit => {
                const parentId = unit.Parent_ID ?? unit.parent_id;
                return !parentId || parentId === 0 || parentId === null;
            });
            
            // If no root found in filtered units, use the current org unit as root
            const rootId = rootOrgUnit ? (rootOrgUnit.ID ?? rootOrgUnit.id) : orgUnitId;
            console.log('Org Unit - Using root ID:', rootId, 'for org unit:', rootOrgUnit);
            
            const hierarchyRows = buildHierarchyTree(filteredUnits, rootId);
            console.log('Org Unit - Hierarchy rows:', hierarchyRows);
            const html = renderComponentsTable(hierarchyRows, orgUnitId);
            container.innerHTML = html;
            
            // Cache the rendered HTML and hierarchy data
            tabCache.components = {
                orgUnitId: orgUnitId,
                html: html,
                hierarchyRows: hierarchyRows
            };
            
            initComponentsInteractions(container, hierarchyRows);
        } catch (e) {
            container.innerHTML = '<div class="view-section" style="grid-column:1/-1;color:var(--danger,#b91c1c);">' + t('orgUnit.messages.failedToLoadHierarchy') + '</div>';
        }
    }

    function renderPeopleTable(rows) {
        const table = document.createElement('div');
        table.className = 'view-section';
        if (!rows || rows.length === 0) {
            table.innerHTML = `
                <div class="section-title">${t('orgUnit.tabs.people')}</div>
                <div class="empty-state">
                    <i class="fas fa-users"></i>
                    <div class="empty-title">${t('orgUnit.messages.noPeopleFound')}</div>
                    <div class="empty-sub">${t('orgUnit.messages.noPeopleSub')}</div>
                </div>
            `;
            return table.outerHTML;
        }

        const bodyRows = rows.map(p => {
            const first = p.First_Name || p.first_name || '';
            const last = p.Last_Name || p.last_name || '';
            const initials = [first, last].filter(Boolean).map(x => String(x).trim()[0] || '').slice(0,2).join('').toUpperCase() || '?';
            const fullName = [first, last].filter(Boolean).join(' ');
            const func = p.Function_Name || p.function_name || '';
            const email = p.Email || p.email || '';
            const pid = p.ID || p.id || p.peopleId || p.people_id;
            const nameLink = pid ? `<a class="person-link" href="/view/people/${encodeURIComponent(pid)}">${escapeHtml(fullName)}</a>` : escapeHtml(fullName);
            return `
                <tr>
                    <td>
                        <div class="name-cell">
                            <div class="avatar">${escapeHtml(initials)}</div>
                            <div class="name-meta">
                                <div class="full-name">${nameLink}</div>
                                <div class="email-mobile">${escapeHtml(email)}</div>
                            </div>
                        </div>
                    </td>
                    <td>${escapeHtml(func)}</td>
                    <td class="email-desktop">${escapeHtml(email)}</td>
                </tr>`;
        }).join('');

        table.innerHTML = `
            <div class="section-title">${t('orgUnit.tabs.people')}</div>
            <div class="table-tools">
                <div class="tools-left"></div>
                <div class="tools-right">
                    <input type="text" class="people-search" placeholder="${t('orgUnit.messages.searchPlaceholder')}" />
                </div>
            </div>
            <div class="people-table-wrapper">
                <table class="people-table">
                    <thead>
                        <tr>
                            <th class="sortable" data-key="name"><div class="th-content">${t('orgUnit.labels.name')} <span class="sort-indicator"></span></div></th>
                            <th class="sortable" data-key="function"><div class="th-content">${t('label.function')} <span class="sort-indicator"></span></div></th>
                            <th class="sortable" data-key="email"><div class="th-content">${t('label.email')} <span class="sort-indicator"></span></div></th>
                        </tr>
                    </thead>
                    <tbody>
                        ${bodyRows}
                    </tbody>
                </table>
                <div class="table-footer">${rows.length} ${t('orgUnit.messages.records')}</div>
            </div>
        `;
        return table.outerHTML;
    }

    // Global cache for tab data
    const tabCache = {
        people: null,
        components: null
    };

    async function loadPeople(orgUnitId) {
        const container = document.getElementById('orgUnitPeopleContainer');
        
        // Check if data is already cached
        if (tabCache.people && tabCache.people.orgUnitId === orgUnitId) {
            container.innerHTML = tabCache.people.html;
            initPeopleInteractions(container, tabCache.people.state);
            return;
        }

        container.innerHTML = '<div class="view-section" style="grid-column:1/-1;">' + t('orgUnit.messages.loading') + '</div>';
        const state = { original: [], filtered: [], sortKey: 'name', sortDir: 'asc', search: '' };
        
        function normalize(p) {
            const first = p.First_Name || p.first_name || '';
            const last = p.Last_Name || p.last_name || '';
            return {
                ...p,
                _name: [first, last].filter(Boolean).join(' ').toLowerCase(),
                _function: (p.Function_Name || p.function_name || '').toLowerCase(),
                _email: (p.Email || p.email || '').toLowerCase()
            };
        }
        
        function applyFilterAndSort() {
            const q = state.search.trim().toLowerCase();
            let rows = state.original;
            if (q) {
                rows = rows.filter(p => p._name.includes(q) || p._function.includes(q) || p._email.includes(q));
            }
            const key = state.sortKey;
            const dir = state.sortDir === 'desc' ? -1 : 1;
            rows = rows.slice().sort((a,b) => {
                const va = key==='name'?a._name:key==='function'?a._function:a._email;
                const vb = key==='name'?b._name:key==='function'?b._function:b._email;
                return va.localeCompare(vb) * dir;
            });
            state.filtered = rows;
        }
        
        function render() {
            applyFilterAndSort();
            const html = renderPeopleTable(state.filtered);
            container.innerHTML = html;
            
            // Cache the rendered HTML and state
            tabCache.people = {
                orgUnitId: orgUnitId,
                html: html,
                state: state
            };
            
            initPeopleInteractions(container, state);
        }
        
        try {
            let list = await window.BUDG_API_SERVICE.getPeopleByOrgUnitId(orgUnitId);
            if (list && list.data) list = list.data;
            const arr = Array.isArray(list) ? list : [];
            state.original = arr.map(normalize);
            render();
        } catch (e) {
            container.innerHTML = '<div class="view-section" style="grid-column:1/-1;color:var(--danger,#b91c1c);">' + t('orgUnit.messages.failedToLoadPeople') + '</div>';
        }
    }

    function initPeopleInteractions(container, state) {
        const searchEl = container.querySelector('.people-search');
        if (searchEl) {
            searchEl.value = state.search;
            searchEl.addEventListener('input', () => { 
                state.search = searchEl.value; 
                renderPeopleFromCache();
            });
        }
        
        container.querySelectorAll('th.sortable').forEach(th => {
            const key = th.getAttribute('data-key');
            const indicator = th.querySelector('.sort-indicator');
            if (key === state.sortKey) {
                indicator.textContent = state.sortDir === 'asc' ? '▲' : '▼';
            } else {
                indicator.textContent = '';
            }
            th.addEventListener('click', () => {
                if (state.sortKey === key) {
                    state.sortDir = state.sortDir === 'asc' ? 'desc' : 'asc';
                } else {
                    state.sortKey = key; 
                    state.sortDir = 'asc';
                }
                renderPeopleFromCache();
            });
        });
    }

    function renderPeopleFromCache() {
        if (!tabCache.people) return;
        
        const container = document.getElementById('orgUnitPeopleContainer');
        const state = tabCache.people.state;
        
        function applyFilterAndSort() {
            const q = state.search.trim().toLowerCase();
            let rows = state.original;
            if (q) {
                rows = rows.filter(p => p._name.includes(q) || p._function.includes(q) || p._email.includes(q));
            }
            const key = state.sortKey;
            const dir = state.sortDir === 'desc' ? -1 : 1;
            rows = rows.slice().sort((a,b) => {
                const va = key==='name'?a._name:key==='function'?a._function:a._email;
                const vb = key==='name'?b._name:key==='function'?b._function:b._email;
                return va.localeCompare(vb) * dir;
            });
            state.filtered = rows;
        }
        
        applyFilterAndSort();
        const html = renderPeopleTable(state.filtered);
        container.innerHTML = html;
        tabCache.people.html = html;
        
        initPeopleInteractions(container, state);
    }

    document.addEventListener('DOMContentLoaded', async function() {
			// Hide edit controls for guests
			hideEditsIfUnauthenticated();
        const backBtn = document.getElementById('backBtn');
        if (backBtn) backBtn.addEventListener('click', () => window.history.length>1?window.history.back():window.location.assign('/'));
        const id = parseId();
        if (id != null) {
            await (window.i18nReadyPromise || Promise.resolve());
            load(id).then(async () => {
                // Check and display lock status
                if (window.ViewLockHelper) {
                    await window.ViewLockHelper.checkAndDisplayLockStatus('org-unit', id);
                }
            });

            // Initialize unified edit dropdown
            if (window.EditDropdown && id != null) {
                try {
                    window.EditDropdown.initialize('org-unit', id, {
                        container: '.tab-actions',
                        editUrl: `/view/org-unit/org-unit-edit.html?id=${id}`
                    });
                } catch (error) {
                    console.error('Failed to initialize edit dropdown:', error);
                }
            }

            // Tabs handling with improved caching
            const tabs = document.querySelectorAll('.tab-container .tab');
            const summaryEl = document.getElementById('orgUnitViewContainer');
            const componentsEl = document.getElementById('orgUnitComponentsContainer');
            const peopleEl = document.getElementById('orgUnitPeopleContainer');
            const historyEl = document.getElementById('orgUnitHistoryContainer');
            
            tabs.forEach(btn => btn.addEventListener('click', () => {
                tabs.forEach(b => b.classList.remove('active'));
                btn.classList.add('active');
                const tab = btn.getAttribute('data-tab');
                
                if (tab === 'people') {
                    summaryEl.style.display = 'none';
                    componentsEl.style.display = 'none';
                    historyEl.style.display = 'none';
                    peopleEl.style.display = '';
                    // Load people data (cached on subsequent calls)
                    loadPeople(id);
                } else if (tab === 'components') {
                    summaryEl.style.display = 'none';
                    peopleEl.style.display = 'none';
                    historyEl.style.display = 'none';
                    componentsEl.style.display = '';
                    // Load components data (cached on subsequent calls)
                    loadComponents(id);
                } else if (tab === 'history') {
                    summaryEl.style.display = 'none';
                    peopleEl.style.display = 'none';
                    componentsEl.style.display = 'none';
                    historyEl.style.display = '';
                    // Load history data
                    loadHistory(id);
                } else {
                    peopleEl.style.display = 'none';
                    componentsEl.style.display = 'none';
                    historyEl.style.display = 'none';
                    summaryEl.style.display = '';
                }
            }));

            window.addEventListener('languageChanged', function() {
                load(id).catch(function() {});
                if (tabCache.people && tabCache.people.orgUnitId === id) { tabCache.people = null; loadPeople(id); }
                if (tabCache.components && tabCache.components.orgUnitId === id) { tabCache.components = null; loadComponents(id); }
            });
        }

    });
 // Load history data
    async function loadHistory(id) {
        const container = document.getElementById('orgUnitHistoryContainer');
        if (!container) return;

        container.innerHTML = `
            <div class="view-section" style="grid-column:1/-1;">
                <div id="orgUnitHistoryComponentContainer"></div>
            </div>
        `;

        // Initialize history component if available
        if (window.HistoryComponent && id) {
            try {
                console.log('Initializing history component for Org Unit with ID:', id);
                window.HistoryComponent.initialize('Org Unit', id, 'orgUnitHistoryComponentContainer');
            } catch (error) {
                console.error('Failed to initialize history component:', error);
            }
        } else if (window.initializeHistoryComponent && id) {
            try {
                console.log('Initializing history component for Org Unit with ID (fallback):', id);
                window.initializeHistoryComponent('Org Unit', id, 'orgUnitHistoryComponentContainer');
            } catch (error) {
                console.error('Failed to initialize history component (fallback):', error);
            }
        } else {
            console.error('HistoryComponent not available or id not found:', { 
                HistoryComponent: !!window.HistoryComponent, 
                initializeHistoryComponent: !!window.initializeHistoryComponent,
                id 
            });
        }
    }
})();


