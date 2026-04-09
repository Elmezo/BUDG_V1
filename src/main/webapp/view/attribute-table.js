(function () {
    function html(strings, ...values) { return strings.reduce((s, v, i) => s + v + (values[i] ?? ''), ''); }
    function escapeHtml(str) {
        if (str == null) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    class AttributeTable {
        constructor(containerId, datasetId, view = null) {
            this.container = document.getElementById(containerId);
            this.datasetId = datasetId;
            this.view = view; // View parameter: 'changes' or null (original)
            this.rows = [];
            this.lookups = null;
            this.isEditMode = false;
            this.deletedIds = new Set();
            this.newRowSeq = 1;
            this.currentUserId = null;
            this.cfMeta = [];
            this.cfValues = {};
            /** Resolved module primary name for custom fields on attributes */
            this.cfFacetId = 'Attribute';
        }

        async init() {
            console.log('[AttributeTable.init] START - datasetId:', this.datasetId, 'isEditMode:', this.isEditMode);
            await this.loadCurrentUser();
            await this.loadLookups();
            await this.loadCFMetadata();
            await this.load();
            console.log('[AttributeTable.init] Loaded', this.rows.length, 'rows, calling render()');
            this.render();
            console.log('[AttributeTable.init] END - isEditMode:', this.isEditMode);
        }

        async loadCFMetadata() {
            try {
                let facetParam = 'Attribute';
                if (window.CustomFields && typeof window.CustomFields.mapFacetName === 'function') {
                    facetParam = await window.CustomFields.mapFacetName('Attribute');
                }
                this.cfFacetId = facetParam || 'Attribute';
                const resp = await fetch(`/api/custom-fields/metadata?facetId=${encodeURIComponent(this.cfFacetId)}`);
                const json = await resp.json();
                this.cfMeta = (json.success && Array.isArray(json.data)) ? json.data : [];
            } catch (e) {
                console.warn('[AttributeTable] Failed to load CF metadata:', e);
                this.cfMeta = [];
                this.cfFacetId = 'Attribute';
            }
        }

        async loadCFValues() {
            this.cfValues = {};
            if (!this.cfMeta.length || !this.rows.length) return;
            const promises = this.rows.map(async (r) => {
                const attrId = r.ID;
                if (attrId == null) return;
                try {
                    const facet = encodeURIComponent(this.cfFacetId || 'Attribute');
                    const resp = await fetch(`/api/custom-fields/data?facetId=${facet}&objectId=${attrId}`);
                    const json = await resp.json();
                    if (json.success && Array.isArray(json.data)) {
                        const valMap = {};
                        json.data.forEach(entry => {
                            const mid = entry.metadataId;
                            if (!valMap[mid]) {
                                valMap[mid] = { value: entry.value, enumId: entry.enumId, enumIds: [] };
                            }
                            if (entry.enumId != null) {
                                if (!valMap[mid].enumIds.includes(entry.enumId)) {
                                    valMap[mid].enumIds.push(entry.enumId);
                                }
                            }
                        });
                        this.cfValues[attrId] = valMap;
                    }
                } catch (_) { /* ignore per-attribute errors */ }
            });
            await Promise.all(promises);
        }

        async loadCurrentUser() {
            try {
                const resp = await fetch('/api/me', { method: 'GET', credentials: 'include' });
                if (!resp.ok) {
                    console.warn('[AttributeTable] Failed to load current user, status:', resp.status);
                    this.currentUserId = null;
                    return;
                }
                const me = await resp.json();
                this.currentUserId = me && (me.id || me.ID || (me.data && (me.data.id || me.data.ID))) || null;
                console.log('[AttributeTable] Current user ID loaded:', this.currentUserId);
            } catch (e) {
                console.error('[AttributeTable] Error loading current user:', e);
                this.currentUserId = null;
            }
        }

        enterEditMode() {
            console.log('[AttributeTable.enterEditMode] Entering edit mode');
            this.isEditMode = true;
            this.deletedIds.clear();
            this.render();
        }

        async loadLookups() {
            const resp = await fetch('/api/attribute/lookups', { headers: { 'Accept': 'application/json' } });
            const json = await resp.json();
            this.lookups = json && json.data ? json.data : {};
        }

        async load() {
            let url = `/api/attribute/${this.datasetId}`;
            if (this.view === 'changes') {
                url += '?view=changes';
            }
            url += (url.includes('?') ? '&' : '?') + '_t=' + new Date().getTime();
            
            const resp = await fetch(url, { headers: { 'Accept': 'application/json' } });
            const json = await resp.json();
            this.rows = Array.isArray(json.data) ? json.data : [];
            await this.loadCFValues();
        }
        
        setView(view) {
            this.view = view;
        }

        render() {
            console.log('[AttributeTable.render] START - isEditMode:', this.isEditMode);
            if (!this.container) {
                console.log('[AttributeTable.render] ERROR - Container not found!');
                return;
            }

            // Helper for translation
            const t = (key, defaultVal) => {
                if (window.I18n && typeof window.I18n.t === 'function') {
                    const tr = window.I18n.t(key);
                    if (tr !== key) return tr;
                }
                return defaultVal;
            };

            const header = html`
                <div class="section-title">ATTRIBUTE</div>`;

            if (this.isEditMode) {
                console.log('[AttributeTable.render] Rendering EDIT MODE table');
                // Edit mode - show editable table
                const editPanel = this.renderEditTable(t);
                this.container.innerHTML = header + editPanel;
            } else {
                console.log('[AttributeTable.render] Rendering VIEW MODE table with', this.rows.length, 'rows');
                // View mode - show read-only table
                const viewTable = html`
                    <div class="attribute-view-section">
                        <div class="attribute-view-header">
                            <h3 class="attribute-view-title"><i class="fas fa-table"></i> ${t('label.attributes', 'Attributes')}</h3>
                            <div class="attribute-count">
                                <i class="fas fa-list-ol"></i>
                                <span>${this.rows.length} ${this.rows.length !== 1 ? t('label.attributes', 'attributes') : t('label.attribute', 'attribute')}</span>
                            </div>
                        </div>
                        <div class="table-wrapper view-mode attribute-view-wrapper">
                            <table class="attribute-table view-table">
                                <thead>
                                    <tr>
                                        <th class="narrow">${t('label.attributeKey', 'Key')}</th>
                                        <th class="medium">${t('label.attributeRef', 'Ref.')}</th>
                                        <th class="medium">${t('label.attributeName', 'Name')}</th>
                                        <th class="medium">${t('label.dbFieldName', 'DB Field Name')}</th>
                                        <th class="wide">${t('label.definition', 'Definition')}</th>
                                        <th class="medium">${t('label.kde', 'KDE')}</th>
                                        <th class="medium">${t('label.origin', 'Origin')}</th>
                                        <th class="medium">${t('label.physicalFields', 'Physical Fields')}</th>
                                        <th class="medium">${t('label.reviewStatus', 'Review Status')}</th>
                                        <th class="medium">${t('label.confidenceScore', 'Confidence Score(%)')}</th>
                                        <th class="medium">${t('label.requirement', 'Requirement')}</th>
                                        <th class="medium">${t('label.glossaryName', 'Glossary Name')}</th>
                                        <th class="wide">${t('label.attributeGlossaryDefinition', 'Attribute Glossary Definition')}</th>
                                        <th class="medium">${t('label.glossaryType', 'Glossary Type')}</th>
                                        <th class="medium">${t('label.editability', 'Editability')}</th>
                                        <th class="medium">${t('label.editabilityRole', 'Editability Role')}</th>
                                        <th class="wide">${t('label.businessLogic', 'Business Logic')}</th>
                                        <th class="medium">${t('label.dataType', 'Data Type')}</th>
                                        <th class="narrow">${t('label.dataLength', 'Data Length')}</th>
                                        <th class="medium">${t('label.relatedTo', 'Related To')}</th>
                                        <th class="medium">${t('label.relationshipType', 'Relationship Type')}</th>
                                        <th class="medium">${t('label.interface', 'Interface')}</th>
                                        <th class="medium">${t('label.sourcingLogic', 'Sourcing Logic')}</th>
                                        <th class="medium">${t('label.createdBy', 'Created By')}</th>
                                        <th class="medium">${t('label.createdDate', 'Created Date')}</th>
                                        ${this.cfMeta.map(cf => `<th class="medium">${escapeHtml(cf.displayName || 'Custom Field')}</th>`).join('')}
                                    </tr>
                                </thead>
                                <tbody>
                                    ${this.rows.map(r => this.renderRow(r, t)).join('')}
                                </tbody>
                            </table>
                        </div>
                    </div>`;
                this.container.innerHTML = header + viewTable;
            }

            // Row click opens view-only details
            this.container.querySelectorAll('tr.attr-row').forEach(tr => tr.addEventListener('click', (e) => {
                const target = e.target;
                if (target.closest('button')) return; // ignore clicks on action buttons
                const id = tr.getAttribute('data-row-id');
                const row = this.rows.find(x => String(x.ID) === String(id));
                if (row) this.openViewer(row, t);
            }));

            this.bindEditHandlers();
            // Setup Data Type handlers after rendering
            if (this.isEditMode) {
                setTimeout(() => this.setupDataTypeHandlers(), 100);
            }
            console.log('[AttributeTable.render] END - Rendering complete');
        }

        renderEditTable(t) {
            // Fallback for t if not provided (should not happen if called from render)
            t = t || ((k, d) => d);
            const editableRows = this.rows.map(r => this.renderEditRow(r, t)).join('');
            return html`
                <div class="attribute-edit-section">
                    <div class="attribute-edit-header">
                        <h3 class="attribute-edit-title"><i class="fas fa-edit"></i> ${t('label.editAttributes', 'Edit Attributes')}</h3>
                        <div class="table-actions">
                            <button type="button" class="btn btn-add" id="attrAddInline" title="${t('button.addAttribute', 'Add New Attribute')}">
                                <i class="fas fa-plus-circle"></i> ${t('button.addAttribute', 'Add Attribute')}
                            </button>
                        </div>
                    </div>
                    <div class="table-wrapper-alt attribute-edit-wrapper">
                        <table class="attribute-table attribute-edit-table">
                            <thead>
                                <tr>
                                    <th class="narrow">${t('label.attributeKey', 'Key')}</th>
                                    <th class="narrow">${t('label.mandatory', 'Mandatory')}</th>
                                    <th class="medium">${t('label.confidenceScore', 'Confidence(%)')}</th>
                                    <th class="medium">${t('label.requirement', 'Requirement')}</th>
                                    <th class="xwide">${t('label.businessLogic', 'Business Logic')}</th>
                                    <th class="medium">${t('label.attributeRef', 'Ref.')}</th>
                                    <th class="medium">${t('label.attributeName', 'Name')} <span class="required-star">*</span></th>
                                    <th class="xwide">${t('label.definition', 'Definition')} <span class="required-star">*</span></th>
                                    <th class="medium">${t('label.dbFieldName', 'DB Field Name')}</th>
                                    <th class="medium">${t('label.glossaryName', 'Glossary Name')}</th>
                                    <th class="medium">${t('label.origin', 'Origin')}</th>
                                    <th class="medium">${t('label.editability', 'Editability')}</th>
                                    <th class="medium">${t('label.editabilityRole', 'Editability Role')}</th>
                                    <th class="medium">${t('label.dataType', 'Data Type')}</th>
                                    <th class="narrow">${t('label.dataLength', 'Data Length')}</th>
                                    ${this.cfMeta.map(cf => `<th class="medium">${escapeHtml(cf.displayName || 'Custom Field')}${cf.mandatory ? ' <span class="required-star">*</span>' : ''}</th>`).join('')}
                                    <th class="actions-col">${t('label.action', 'Action')}</th>
                                </tr>
                            </thead>
                            <tbody id="attrEditTbody">
                                ${editableRows}
                            </tbody>
                        </table>
                    </div>
                </div>
            `;
        }

        renderEditRow(r, t) {
            t = t || ((k, d) => d);
            const rid = r && r.ID != null ? String(r.ID) : `new-${this.newRowSeq++}`;
            const key = r ? (r['Key attribute'] === 1 || r['Key attribute'] === '1') : false;
            const mandatory = r ? (r['Is_Mandatory attribute'] === 1 || r['Is_Mandatory attribute'] === '1') : false;
            const reqName = r?.['Requirement attribute'] ?? '';
            const glossName = r?.['Glossary Name attribute'] ?? '';
            const originName = r?.['Origin attribute'] ?? '';
            const editName = r?.['Editability attribute'] ?? '';
            const roleName = r?.['Editability Role attribute'] ?? '';
            const dtName = r?.['Data Type attribute'] ?? '';
            const input = (name, val, placeholder = '') => `<input class="form-input attr-edit-input" type="text" name="${name}" value="${val ? String(val).replace(/"/g, '&quot;') : ''}" placeholder="${placeholder}">`;
            const inputNum = (name, val, dataTypeName) => {
                // Check if Data Length should be disabled based on current Data Type
                const isStringType = dataTypeName && (dataTypeName.toUpperCase() === 'STRING' ||
                    dataTypeName.toUpperCase() === 'VARCHAR' ||
                    dataTypeName.toUpperCase() === 'CHAR' ||
                    dataTypeName.toUpperCase() === 'TEXT');
                const disabled = dataTypeName && !isStringType;
                const style = disabled ? 'style="background-color: #f5f5f5;"' : '';
                const title = disabled ? 'title="Data Length is not applicable for non-string types"' :
                    (isStringType ? 'title="Data Length is required for string types"' : '');
                return `<input class="form-input attr-edit-input" type="number" name="${name}" value="${val ?? ''}" ${disabled ? 'disabled' : ''} ${style} ${title}>`;
            };
            const inputFloat = (name, val) => `<input class="form-input attr-edit-input" type="number" step="0.01" name="${name}" value="${val ?? ''}">`;
            const isNewRow = !r || r.ID == null;
            const select = (name, list, selName) => {
                const items = list || [];
                return `<select class="form-input attr-edit-select" name="${name}"><option value="">--</option>${items.map(o => {
                    const isSelected = selName && String(selName) === String(o.name);
                    return `<option value="${o.id}" ${isSelected ? 'selected' : ''}>${escapeHtml(String(o.name))}</option>`;
                }).join('')}</select>`;
            };
            const checkbox = (name, checked) => `<input class="form-input" type="checkbox" name="${name}" ${checked ? 'checked' : ''}>`;
            return html`<tr data-edit-id="${rid}" class="edit-row">
                <td class="narrow center">${checkbox('is_primary_key', key)}</td>
                <td class="narrow center">${checkbox('is_mandatory', mandatory)}</td>
                <td class="medium">${inputFloat('confidence_score', r?.['Confidence Score(%)'] || '')}</td>
                <td class="medium">${select('requirement_id', this.lookups.requirements, reqName)}</td>
                <td class="xwide">${input('business_logic', r?.['Business Logic attribute'] || '')}</td>
                <td class="medium">${input('ref_number', r?.['Ref. attribute'] || '', t('placeholder.enterRef', 'Enter reference'))}</td>
                <td class="medium">${input('primary_name', r?.['Name attribute'] || '', t('placeholder.enterName', 'Enter name'))}</td>
                <td class="xwide">${input('definition', r?.['Definition attribute'] || '', t('placeholder.enterDefinition', 'Enter definition'))}</td>
                <td class="medium">${input('db_field_name', r?.['DB Field Name attribute'] || '', t('placeholder.enterDbFieldName', 'Enter DB field name'))}</td>
                <td class="medium">${select('glossary_id', this.lookups.glossaries, glossName)}</td>
                <td class="medium">${select('origination', this.lookups.originations, originName)}</td>
                <td class="medium">${select('editability', this.lookups.editabilities, editName)}</td>
                <td class="medium">${select('editability_role', this.lookups.editRoles, roleName)}</td>
                <td class="medium">${select('data_type_id', this.lookups.dataTypes, dtName)}</td>
                <td class="narrow">${inputNum('data_length', r?.['Data Length attribute'] || '', dtName)}</td>
                ${this.renderCFEditCells(r, rid)}
                <td class="actions-col">
                    <button class="btn btn-remove" data-inline-del="${rid}" title="Delete Row">
                        <i class="fas fa-trash-alt"></i> Delete
                    </button>
                </td>
            </tr>`;
        }

        renderCFEditCells(r, rid) {
            if (!this.cfMeta.length) return '';
            /** Metadata defaults apply only to new rows; existing attributes stay blank until the user sets a value. */
            const useCfDefaults = String(rid || '').startsWith('new-');
            return this.cfMeta.map(cf => {
                const cfId = cf.id || cf.ID;
                const dt = (cf.type || '').toLowerCase();
                const attrCf = r && r.ID != null ? ((this.cfValues[r.ID] || {})[cfId] || null) : null;
                const savedVal = attrCf ? (attrCf.value || '') : '';
                const savedEnumId = attrCf ? attrCf.enumId : null;
                const savedEnumIds = attrCf && attrCf.enumIds ? attrCf.enumIds : [];
                const defaultVal = (cf.defaultValue != null && cf.defaultValue !== '') ? cf.defaultValue : (cf.default_value || '');
                const inputName = `cf_${cfId}`;

                if (dt === 'checkbox') {
                    const checked = attrCf
                        ? (savedVal === 'true' || savedVal === true)
                        : (useCfDefaults && String(defaultVal).toLowerCase() === 'true');
                    return `<td class="medium"><input class="form-input" type="checkbox" name="${inputName}" ${checked ? 'checked' : ''}></td>`;
                }
                if (dt === 'dropdown') {
                    let selId = savedEnumId != null ? savedEnumId : null;
                    if (selId == null && useCfDefaults && defaultVal && cf.enumValues) {
                        const match = cf.enumValues.find(ev => {
                            const enumVal = ev.enumValue || ev.EnumValue || ev.name || '';
                            return String(enumVal).toLowerCase() === String(defaultVal).toLowerCase();
                        });
                        if (match) {
                            selId = match.id || match.ID;
                        }
                    }
                    const opts = (cf.enumValues || []).map(ev => {
                        const eid = ev.id || ev.ID;
                        const ename = ev.enumValue || ev.EnumValue || ev.name || '';
                        return `<option value="${eid}" ${eid === selId ? 'selected' : ''}>${escapeHtml(ename)}</option>`;
                    }).join('');
                    return `<td class="medium"><select class="form-input attr-edit-select" name="${inputName}"><option value="">--</option>${opts}</select></td>`;
                }
                if (dt === 'multiselect') {
                    const defaultValues = String(defaultVal || '').split(',').map(v => v.trim().toLowerCase()).filter(Boolean);
                    const opts = (cf.enumValues || []).map(ev => {
                        const eid = ev.id || ev.ID;
                        const ename = ev.enumValue || ev.EnumValue || ev.name || '';
                        const hasSavedSelection = savedEnumIds && savedEnumIds.length > 0;
                        const useDefaultEnum = useCfDefaults && !hasSavedSelection && defaultValues.includes(String(ename).toLowerCase());
                        const sel = (savedEnumIds.includes(eid) || useDefaultEnum) ? 'selected' : '';
                        return `<option value="${eid}" ${sel}>${escapeHtml(ename)}</option>`;
                    }).join('');
                    return `<td class="medium"><select class="form-input attr-edit-select" name="${inputName}" multiple size="3">${opts}</select></td>`;
                }
                const fallback = useCfDefaults ? defaultVal : '';
                if (dt === 'date') {
                    const val = savedVal || fallback || '';
                    return `<td class="medium"><input class="form-input attr-edit-input" type="date" name="${inputName}" value="${escapeHtml(val)}"></td>`;
                }
                if (dt === 'time') {
                    const val = savedVal || fallback || '';
                    return `<td class="medium"><input class="form-input attr-edit-input" type="time" name="${inputName}" value="${escapeHtml(val)}"></td>`;
                }
                if (dt === 'number' || dt === 'decimal') {
                    const val = savedVal || fallback || '';
                    const step = dt === 'decimal' ? '0.01' : '1';
                    return `<td class="medium"><input class="form-input attr-edit-input" type="number" step="${step}" name="${inputName}" value="${escapeHtml(val)}"></td>`;
                }
                if (dt === 'percentage') {
                    const val = savedVal || fallback || '';
                    return `<td class="medium"><div class="cf-percentage-wrapper"><input class="form-input attr-edit-input" type="number" step="1" min="0" max="100" name="${inputName}" value="${escapeHtml(val)}" style="padding-right:2.2rem"><span class="cf-percentage-icon"><i class="fas fa-percent"></i></span></div></td>`;
                }
                const val = savedVal || fallback || '';
                return `<td class="medium"><input class="form-input attr-edit-input" type="text" name="${inputName}" value="${val ? String(val).replace(/"/g, '&quot;') : ''}"></td>`;
            }).join('');
        }

        collectCFValues(tr) {
            const values = [];
            this.cfMeta.forEach(cf => {
                const cfId = cf.id || cf.ID;
                const dt = (cf.type || '').toLowerCase();
                const inputName = `cf_${cfId}`;
                const el = tr.querySelector(`[name="${inputName}"]`);
                if (!el) return;
                if (dt === 'checkbox') {
                    values.push({ metadataId: cfId, value: el.checked ? 'true' : 'false', enumId: null });
                } else if (dt === 'dropdown') {
                    const enumId = el.value ? parseInt(el.value, 10) : null;
                    values.push({ metadataId: cfId, value: null, enumId: Number.isInteger(enumId) ? enumId : null });
                } else if (dt === 'multiselect') {
                    const selected = Array.from(el.selectedOptions).map(o => parseInt(o.value, 10)).filter(Number.isInteger);
                    if (selected.length === 0) {
                        values.push({ metadataId: cfId, value: null, enumId: null });
                    } else {
                        selected.forEach(eid => values.push({ metadataId: cfId, value: null, enumId: eid }));
                    }
                } else {
                    const val = (el.value || '').trim();
                    if (dt === 'percentage' && val) {
                        const n = Number(val);
                        if (!Number.isFinite(n) || n < 0 || n > 100) {
                            throw new Error(`Custom field "${cf.displayName || cfId}" must be between 0 and 100`);
                        }
                    }
                    values.push({ metadataId: cfId, value: val || null, enumId: null });
                }
            });
            return values;
        }

        async saveCFForAttribute(attributeId, cfVals) {
            if (!cfVals.length) return;
            const facetId = this.cfFacetId || 'Attribute';
            await fetch('/api/custom-fields/data', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ facetId, objectId: attributeId, values: cfVals })
            });
        }

        /**
         * Check if a data type is a string type (requires Data Length)
         */
        isStringDataType(dataTypeName) {
            if (!dataTypeName) return false;
            const upper = String(dataTypeName).trim().toUpperCase();
            return upper === 'STRING' || upper === 'VARCHAR' || upper === 'CHAR' || upper === 'TEXT';
        }

        /**
         * Get data type name by ID from lookups
         */
        getDataTypeNameById(dataTypeId) {
            if (!this.lookups || !this.lookups.dataTypes || !dataTypeId) return null;
            const dt = this.lookups.dataTypes.find(d => d.id === parseInt(dataTypeId, 10));
            return dt ? dt.name : null;
        }

        /**
         * Handle Data Type change - enable/disable Data Length field
         */
        handleDataTypeChange(selectElement) {
            const row = selectElement.closest('tr[data-edit-id]');
            if (!row) return;

            const dataLengthInput = row.querySelector('input[name="data_length"]');
            if (!dataLengthInput) return;

            const dataTypeId = selectElement.value;
            if (!dataTypeId) {
                // No Data Type selected - disable Data Length
                dataLengthInput.disabled = true;
                dataLengthInput.required = false;
                dataLengthInput.value = '';
                dataLengthInput.style.backgroundColor = '#f5f5f5';
                dataLengthInput.title = 'Please select Data Type first';
                return;
            }

            const dataTypeName = this.getDataTypeNameById(dataTypeId);
            const isStringType = this.isStringDataType(dataTypeName);

            if (isStringType) {
                // Enable Data Length for string types
                dataLengthInput.disabled = false;
                dataLengthInput.required = true;
                dataLengthInput.style.backgroundColor = '';
                dataLengthInput.style.borderColor = '';
                dataLengthInput.title = 'Data Length is required for string types (String, VARCHAR, CHAR, Text)';

                // Add visual indicator if empty
                if (!dataLengthInput.value || dataLengthInput.value <= 0) {
                    dataLengthInput.style.borderColor = '#dc2626';
                }
            } else {
                // Disable and clear Data Length for non-string types
                dataLengthInput.disabled = true;
                dataLengthInput.required = false;
                dataLengthInput.value = '';
                dataLengthInput.style.backgroundColor = '#f5f5f5';
                dataLengthInput.style.borderColor = '';
                dataLengthInput.title = `Data Length is not applicable for ${dataTypeName || 'non-string'} types`;
            }
        }

        bindEditHandlers() {
            if (!this.isEditMode) return;
            const tbody = this.container.querySelector('#attrEditTbody');

            // Reset buttons to avoid duplicate bindings across re-renders
            const reset = (selector) => {
                const el = this.container.querySelector(selector);
                if (!el) return null;
                const clone = el.cloneNode(true);
                el.parentNode.replaceChild(clone, el);
                return clone;
            };

            const addBtn = reset('#attrAddInline');
            addBtn?.addEventListener('click', () => {
                const currentTbody = this.container.querySelector('#attrEditTbody');
                if (currentTbody) {
                    currentTbody.insertAdjacentHTML('beforeend', this.renderEditRow(null));
                    // Setup Data Type change handlers for the new row
                    setTimeout(() => {
                        this.setupDataTypeHandlers();
                        this.setupMandatoryRequirementHandlers();
                    }, 100);
                }
            });

            // Use event delegation for row delete to avoid per-row listeners and duplicates
            const tbodyClone = tbody.cloneNode(true);
            tbody.parentNode.replaceChild(tbodyClone, tbody);
            tbodyClone.addEventListener('click', (e) => {
                const del = e.target.closest('[data-inline-del]');
                if (!del) return;
                const rid = del.getAttribute('data-inline-del');
                const tr = this.container.querySelector(`tr[data-edit-id="${rid}"]`);
                if (!tr) return;
                if (!rid.startsWith('new-')) this.deletedIds.add(rid);
                tr.remove();
            });

            // Setup Data Type change handlers for existing rows
            this.setupDataTypeHandlers();
            
            // Setup Mandatory/Requirement linking handlers
            this.setupMandatoryRequirementHandlers();
        }
        
        /**
         * Setup event handlers for Mandatory/Requirement linking
         * - When Requirement is selected → check Mandatory checkbox
         * - When Mandatory is unchecked → clear Requirement selection
         */
        setupMandatoryRequirementHandlers() {
            const tbody = this.container.querySelector('#attrEditTbody');
            if (!tbody) return;
            
            // Use event delegation for Requirement select changes
            tbody.addEventListener('change', (e) => {
                if (e.target.name === 'requirement_id') {
                    const row = e.target.closest('tr[data-edit-id]');
                    if (!row) return;
                    
                    const requirementSelect = e.target;
                    const mandatoryCheckbox = row.querySelector('input[name="is_mandatory"]');
                    
                    if (mandatoryCheckbox && requirementSelect.value) {
                        // Check if the selected requirement is "Mandatory"
                        const selectedOption = requirementSelect.options[requirementSelect.selectedIndex];
                        const requirementName = selectedOption ? selectedOption.textContent.trim() : '';
                        const isMandatoryRequirement = requirementName.toLowerCase() === 'mandatory';
                        
                        // Only check Mandatory checkbox if requirement is "Mandatory"
                        // Otherwise, uncheck it
                        mandatoryCheckbox.checked = isMandatoryRequirement;
                    } else if (mandatoryCheckbox && !requirementSelect.value) {
                        // If no requirement is selected, uncheck Mandatory
                        mandatoryCheckbox.checked = false;
                    }
                }
            });
            
            // Use event delegation for Mandatory checkbox changes
            tbody.addEventListener('change', (e) => {
                if (e.target.name === 'is_mandatory') {
                    const row = e.target.closest('tr[data-edit-id]');
                    if (!row) return;
                    
                    const mandatoryCheckbox = e.target;
                    const requirementSelect = row.querySelector('select[name="requirement_id"]');
                    
                    if (requirementSelect) {
                        if (mandatoryCheckbox.checked) {
                            // If Mandatory is checked, select "Mandatory" from requirement dropdown
                            // Find the option with text "Mandatory"
                            const options = requirementSelect.options;
                            for (let i = 0; i < options.length; i++) {
                                if (options[i].textContent.trim().toLowerCase() === 'mandatory') {
                                    requirementSelect.value = options[i].value;
                                    requirementSelect.dispatchEvent(new Event('change'));
                                    break;
                                }
                            }
                        } else {
                            // If Mandatory is unchecked, clear Requirement
                            requirementSelect.value = '';
                            requirementSelect.dispatchEvent(new Event('change'));
                        }
                    }
                }
            });
            
            // Initialize state for existing rows - only check Mandatory if Requirement is "Mandatory"
            tbody.querySelectorAll('select[name="requirement_id"]').forEach(select => {
                if (select.value) {
                    const row = select.closest('tr[data-edit-id]');
                    if (row) {
                        const mandatoryCheckbox = row.querySelector('input[name="is_mandatory"]');
                        if (mandatoryCheckbox) {
                            // Check if the selected requirement is "Mandatory"
                            const selectedOption = select.options[select.selectedIndex];
                            const requirementName = selectedOption ? selectedOption.textContent.trim() : '';
                            const isMandatoryRequirement = requirementName.toLowerCase() === 'mandatory';
                            
                            // Only check Mandatory checkbox if requirement is "Mandatory"
                            mandatoryCheckbox.checked = isMandatoryRequirement;
                        }
                    }
                } else {
                    // If no requirement is selected, ensure Mandatory is unchecked
                    const row = select.closest('tr[data-edit-id]');
                    if (row) {
                        const mandatoryCheckbox = row.querySelector('input[name="is_mandatory"]');
                        if (mandatoryCheckbox) {
                            mandatoryCheckbox.checked = false;
                        }
                    }
                }
            });
        }

        /**
         * Setup event handlers for Data Type select changes
         */
        setupDataTypeHandlers() {
            const tbody = this.container.querySelector('#attrEditTbody');
            if (!tbody) return;

            // Use event delegation for Data Type select changes
            tbody.addEventListener('change', (e) => {
                if (e.target.name === 'data_type_id') {
                    this.handleDataTypeChange(e.target);
                }
            });

            // Initialize state for existing rows
            tbody.querySelectorAll('select[name="data_type_id"]').forEach(select => {
                this.handleDataTypeChange(select);
            });
        }

        async saveInlineEdits() {
            console.log('[AttributeTable.saveInlineEdits] Starting save, currentUserId:', this.currentUserId);

            // Validate currentUserId
            if (!this.currentUserId) {
                const msg = 'Unable to save: User not identified. Please refresh the page.';
                console.error('[AttributeTable.saveInlineEdits] No current user ID available');
                this.toast('error', msg);
                throw new Error(msg);
            }

            const rows = Array.from(this.container.querySelectorAll('#attrEditTbody tr[data-edit-id]'));
            // Build actions
            const inserts = [];
            const updates = [];
            const seenNames = new Set();
            const seenRefs = new Set();

            // Calculate next auto-ref number
            let maxRefNum = 0;
            this.rows.forEach(r => {
                const ref = r['Ref. attribute'];
                if (ref && typeof ref === 'string' && ref.startsWith('ATT_')) {
                    const num = parseInt(ref.substring(4), 10);
                    if (!isNaN(num) && num > maxRefNum) maxRefNum = num;
                }
            });
            let autoRefCounter = maxRefNum + 1;

            for (const tr of rows) {
                const rid = tr.getAttribute('data-edit-id');
                const get = (name) => {
                    const el = tr.querySelector(`[name="${name}"]`);
                    if (!el) return null;
                    if (el.type === 'checkbox') return el.checked ? 1 : 0;
                    if (el.tagName === 'SELECT') return el.value ? parseInt(el.value, 10) : null;
                    if (el.type === 'number') {
                        if (el.step && el.step !== '1') return el.value ? parseFloat(el.value) : null;
                        return el.value ? parseInt(el.value, 10) : null;
                    }
                    // For text inputs, return empty string if empty, not null, to ensure the field is sent
                    const value = el.value?.trim();
                    return value !== undefined && value !== '' ? value : null;
                };
                const dbFieldNameValue = get('db_field_name');
                const payload = {
                    is_primary_key: get('is_primary_key'),
                    is_mandatory: get('is_mandatory'),
                    confidence_score: get('confidence_score'),
                    requirement_id: get('requirement_id'),
                    business_logic: get('business_logic'),
                    ref_number: get('ref_number'),
                    primary_name: get('primary_name'),
                    definition: get('definition'),
                    db_field_name: dbFieldNameValue, // Explicitly include even if null/empty
                    glossary_id: get('glossary_id'),
                    origination: get('origination'),
                    editability: get('editability'),
                    editability_role: get('editability_role'),
                    data_type_id: get('data_type_id'),
                    data_length: get('data_length'),
                    dataset_id: this.datasetId
                };
                
                // Debug: Log db_field_name value
                console.log('[AttributeTable] Saving db_field_name:', dbFieldNameValue, 'for attribute:', payload.primary_name);

                // Leave ref_number empty if not provided - backend will auto-generate a unique one
                if (!payload.ref_number) {
                    payload.ref_number = null;
                }

                const currentLang = (
                    (window.I18n?.getCurrentLanguage && window.I18n.getCurrentLanguage())
                    || document.documentElement.lang
                    || ''
                ).toLowerCase();
                const isArabicUi = currentLang.startsWith('ar');

                // basic required validation
                if (!payload.primary_name || !payload.definition) {
                    const msg = isArabicUi ? 'الاسم والتعريف مطلوبان.' : 'Name and Definition are required.';
                    throw new Error(msg);
                }

                // Data Type validation - required for all attributes
                if (!payload.data_type_id) {
                    const attrName = payload.primary_name || 'Attribute';
                    const msg = isArabicUi
                        ? `نوع البيانات مطلوب للسمة "${attrName}".`
                        : `Data Type is required for attribute "${attrName}".`;
                    // Highlight the Data Type field
                    const tr = Array.from(this.container.querySelectorAll('#attrEditTbody tr[data-edit-id]')).find(t => {
                        const nameInput = t.querySelector('input[name="primary_name"]');
                        return nameInput && nameInput.value === payload.primary_name;
                    });
                    if (tr) {
                        const dataTypeSelect = tr.querySelector('select[name="data_type_id"]');
                        if (dataTypeSelect) {
                            dataTypeSelect.style.borderColor = '#dc2626';
                            dataTypeSelect.focus();
                        }
                    }
                    throw new Error(msg);
                }

                // Data Length validation - required only for string types
                const dataTypeName = this.getDataTypeNameById(payload.data_type_id);
                const isStringType = this.isStringDataType(dataTypeName);

                if (isStringType) {
                    // String types require Data Length
                    const dataLength = payload.data_length;
                    if (!dataLength || dataLength <= 0) {
                        const attrName = payload.primary_name || 'Attribute';
                        const msg = isArabicUi
                            ? `طول البيانات مطلوب للسمة النصية "${attrName}" (نوع البيانات: ${dataTypeName}).`
                            : `Data Length is required for string attribute "${attrName}" (Data Type: ${dataTypeName}).`;
                        // Highlight the Data Length field
                        const tr = Array.from(this.container.querySelectorAll('#attrEditTbody tr[data-edit-id]')).find(t => {
                            const nameInput = t.querySelector('input[name="primary_name"]');
                            return nameInput && nameInput.value === payload.primary_name;
                        });
                        if (tr) {
                            const dataLengthInput = tr.querySelector('input[name="data_length"]');
                            if (dataLengthInput) {
                                dataLengthInput.style.borderColor = '#dc2626';
                                dataLengthInput.focus();
                            }
                        }
                        throw new Error(msg);
                    }
                } else {
                    // Non-string types should not have Data Length - clear it
                    payload.data_length = null;
                }
                // client-side uniqueness within current edits
                const nameKey = payload.primary_name ? payload.primary_name.toLowerCase() : '';
                if (nameKey) {
                    const dup = seenNames.has(nameKey);
                    if (dup) { throw new Error('Primary Name already exists in this dataset. Please choose a different name.'); }
                    seenNames.add(nameKey);
                }
                const refKey = payload.ref_number ? payload.ref_number.toLowerCase() : '';
                if (refKey) {
                    const dup = seenRefs.has(refKey);
                    if (dup) { throw new Error('RefNumber already exists. Please choose a different reference number.'); }
                    seenRefs.add(refKey);
                }
                const cfVals = this.cfMeta.length ? this.collectCFValues(tr) : [];
                if (rid.startsWith('new-')) inserts.push({ payload, cfVals, tr }); else updates.push({ id: parseInt(rid, 10), payload, cfVals });
            }
            const deletes = Array.from(this.deletedIds).map(id => parseInt(id, 10));
            let successfulInserts = [];

            try {
                // Perform deletes first
                for (const id of deletes) {
                    const response = await fetch(`/api/attribute/${id}`, { method: 'DELETE' });
                    if (!response.ok) {
                        const errorData = await response.json();
                        throw new Error(errorData.error || errorData.message || 'Failed to delete attribute');
                    }
                }
                // Updates
                for (const u of updates) {
                    const updatePayload = { ...u.payload, last_updated_user_id: this.currentUserId };
                    console.log('[AttributeTable.saveInlineEdits] Updating attribute ID:', u.id, 'with payload:', updatePayload);
                    const response = await fetch(`/api/attribute/${u.id}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(updatePayload) });
                    if (!response.ok) {
                        let errorMsg = 'Failed to update attribute';
                        try {
                            const errorData = await response.json();
                            errorMsg = errorData.error || errorData.message || errorMsg;
                        } catch (_) {
                            try { errorMsg = await response.text(); } catch (_2) {}
                        }
                        console.error('[AttributeTable] Update failed for ID:', u.id, '- Error:', errorMsg);
                        throw new Error(errorMsg);
                    }
                    if (u.cfVals.length) {
                        await this.saveCFForAttribute(u.id, u.cfVals);
                    }
                    console.log('[AttributeTable.saveInlineEdits] Successfully updated attribute ID:', u.id);
                }
                // Inserts
                for (const entry of inserts) {
                    const insertPayload = { ...entry.payload, created_by: this.currentUserId };
                    console.log('[AttributeTable.saveInlineEdits] Inserting new attribute with payload:', insertPayload);
                    const response = await fetch('/api/attribute', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(insertPayload) });
                    if (!response.ok) {
                        let errorMsg = 'Failed to create attribute';
                        try {
                            const errorData = await response.json();
                            errorMsg = errorData.error || errorData.message || errorMsg;
                        } catch (_) {
                            try { errorMsg = await response.text(); } catch (_2) {}
                        }
                        console.error('[AttributeTable] Insert failed for:', entry.payload.primary_name, '- Error:', errorMsg, '- Payload:', insertPayload);
                        throw new Error(errorMsg);
                    }
                    let newId = null;
                    try {
                        const insertResult = await response.json();
                        newId = insertResult.id || insertResult.ID || insertResult.attributeId;
                        if (newId) successfulInserts.push({ tr: entry.tr, id: newId });
                        if (newId && entry.cfVals.length) {
                            await this.saveCFForAttribute(newId, entry.cfVals);
                        }
                    } catch (_) { /* response already consumed or no ID returned */ }
                    console.log('[AttributeTable.saveInlineEdits] Successfully inserted new attribute');
                }
                this.isEditMode = false;
                this.deletedIds.clear();
                await this.load();
                this.render();
                this.toast('success', 'Changes saved');
            } catch (e) {
                console.error('[AttributeTable.saveInlineEdits] Save error:', e);
                // Mark rows that were already inserted so next save sends them as updates, not duplicate inserts
                for (const { tr, id } of successfulInserts) {
                    if (tr && id) tr.setAttribute('data-edit-id', String(id));
                }
                const errorMsg = e.message || 'Failed to save changes';
                this.toast('error', errorMsg);
                throw e;
            }
        }

        toast(type, message) {
            const id = 'budg-toast-container';
            let ctn = document.getElementById(id);
            if (!ctn) {
                ctn = document.createElement('div');
                ctn.id = id;
                ctn.style.position = 'fixed';
                ctn.style.top = '16px';
                ctn.style.right = '16px';
                ctn.style.zIndex = '9999';
                ctn.style.display = 'flex';
                ctn.style.flexDirection = 'column';
                ctn.style.gap = '8px';
                document.body.appendChild(ctn);
            }
            const toast = document.createElement('div');
            toast.style.minWidth = '220px';
            toast.style.maxWidth = '420px';
            toast.style.padding = '10px 12px';
            toast.style.borderRadius = '8px';
            toast.style.color = '#fff';
            toast.style.boxShadow = '0 6px 20px rgba(0,0,0,0.15)';
            toast.style.fontSize = '14px';
            toast.style.display = 'flex';
            toast.style.alignItems = 'center';
            toast.style.gap = '8px';
            if (type === 'success') toast.style.background = '#16a34a';
            else if (type === 'error') toast.style.background = '#dc2626';
            else toast.style.background = '#374151';
            toast.innerHTML = `<span>${escapeHtml(message)}</span>`;
            ctn.appendChild(toast);
            setTimeout(() => { toast.style.opacity = '0'; toast.style.transition = 'opacity .3s'; }, 2200);
            setTimeout(() => { toast.remove(); if (!ctn.children.length) ctn.remove(); }, 2600);
        }

        /**
         * View-mode CF text from saved API data only.
         *
         * We intentionally do NOT fall back to metadata `defaultValue` here: if we did, every legacy attribute
         * with no stored custom-field row would still show the field definition default as if it were the
         * object’s value. New attributes get defaults persisted (see server materialize on POST /api/attribute
         * + edit-mode prefilling for `new-*` rows), so the grid reads real values from `loadCFValues`.
         */
        getCfDisplayParts(cf, attrCf) {
            const dt = (cf.type || '').toLowerCase();
            let display = '';
            let percentIcon = false;

            if (!attrCf) {
                return { display: '', percentIcon: false };
            }
            if ((dt === 'dropdown' || dt === 'multiselect') && cf.enumValues) {
                const ids = attrCf.enumIds && attrCf.enumIds.length ? attrCf.enumIds : (attrCf.enumId != null ? [attrCf.enumId] : []);
                display = ids.map(eid => {
                    const match = cf.enumValues.find(ev => (ev.id || ev.ID) === eid);
                    return match ? (match.enumValue || match.EnumValue || match.name || '') : '';
                }).filter(Boolean).join(', ');
                return { display, percentIcon: false };
            }
            if (dt === 'checkbox') {
                display = (attrCf.value === 'true' || attrCf.value === true) ? '✓' : '';
                return { display, percentIcon: false };
            }
            if (dt === 'percentage' && attrCf.value != null && String(attrCf.value).trim() !== '') {
                percentIcon = true;
                display = String(attrCf.value).replace(/%/g, '').trim();
                return { display, percentIcon };
            }
            if (attrCf.value != null && String(attrCf.value).trim() !== '') {
                display = String(attrCf.value);
            }
            return { display, percentIcon };
        }

        formatCfCellContent(cf, attrCf) {
            const { display, percentIcon } = this.getCfDisplayParts(cf, attrCf);
            if (!display) return '<span class="empty">-</span>';
            if (percentIcon) {
                return `${escapeHtml(String(display))} <i class="fas fa-percent cf-percentage-view-icon"></i>`;
            }
            return escapeHtml(String(display));
        }

        renderRow(r) {
            const tfKey = (val) => {
                if (val == null || val === '') return '<span class="empty">-</span>';
                const num = Number(val);
                if (!Number.isNaN(num)) return num === 1 ? '<span class="check-mark">✓</span>' : '<span class="empty">-</span>';
                const s = String(val).trim();
                if (s === '1') return '<span class="check-mark">✓</span>';
                if (s === '0') return '<span class="empty">-</span>';
                return s;
            };
            const cell = (val) => {
                if (val == null || val === '') return '<span class="empty">-</span>';
                const s = String(val);
                if (s.indexOf('\n') !== -1) return s.split('\n').map(line => `<div>${escapeHtml(line)}</div>`).join('');
                return escapeHtml(s);
            };
            const m = {
                'Key': tfKey(r['Key attribute']),
                'Ref.': r['Ref. attribute'],
                'Name': r['Name attribute'],
                'DB Field Name': r['DB Field Name attribute'],
                'Definition': r['Definition attribute'],
                'KDE': r['KDE attribute'],
                'Origin': r['Origin attribute'],
                'Physical Fields': '',
                'Review Status': '',
                'Confidence Score(%)': r['Confidence Score(%)'],
                'Requirement': r['Requirement attribute'],
                'Glossary Name': r['Glossary Name attribute'],
                'Attribute Glossary Definition': r['Attribute Glossary Definition'],
                'Glossary Type': r['Glossary Type attribute'],
                'Editability': r['Editability attribute'],
                'Editability Role': r['Editability Role attribute'],
                'Business Logic': r['Business Logic attribute'],
                'Data Type': r['Data Type attribute'],
                'Data Length': r['Data Length attribute'],
                'Related To': r['Related To attribute'],
                'Relationship Type': r['Relationship Type attribute'],
                'Interface': '',
                'Sourcing Logic': '',
                'Created By': r['Created By attribute'],
                'Created Date': r['Created Date attribute']
            };
            const order = [
                'Key', 'Ref.', 'Name', 'DB Field Name', 'Definition', 'KDE', 'Origin', 'Physical Fields', 'Review Status', 'Confidence Score(%)', 'Requirement', 'Glossary Name', 'Attribute Glossary Definition', 'Glossary Type', 'Editability', 'Editability Role', 'Business Logic', 'Data Type', 'Data Length', 'Related To', 'Relationship Type', 'Interface', 'Sourcing Logic', 'Created By', 'Created Date'
            ];
            const cfCells = this.cfMeta.map(cf => {
                const cfId = cf.id || cf.ID;
                const attrCf = (this.cfValues[r.ID] || {})[cfId];
                return `<td>${this.formatCfCellContent(cf, attrCf)}</td>`;
            }).join('');
            return html`<tr data-row-id="${r.ID}" data-attribute-id="${r.ID}" class="attr-row">
                ${order.map(label => `<td>${label === 'Key' ? m[label] : cell(m[label])}</td>`).join('')}${cfCells}
            </tr>`;
        }

        openViewer(row) {
            const dlg = document.createElement('div');
            dlg.className = 'modal-backdrop';

            // Format Key value same as in table
            const formatKey = (val) => {
                if (val == null || val === '') return '-';
                const num = Number(val);
                if (!Number.isNaN(num)) return num === 1 ? '✓' : '-';
                const s = String(val).trim();
                if (s === '1') return '✓';
                if (s === '0') return '-';
                return val;
            };

            const fields = [
                ['Key', formatKey(row['Key attribute'])],
                ['Ref.', row['Ref. attribute']],
                ['Name', row['Name attribute']],
                ['DB Field Name', row['DB Field Name attribute']],
                ['Definition', row['Definition attribute']],
                ['KDE', row['KDE attribute']],
                ['Origin', row['Origin attribute']],
                ['Physical Fields', ''],
                ['Review Status', ''],
                ['Confidence Score(%)', row['Confidence Score(%)']],
                ['Requirement', row['Requirement attribute']],
                ['Glossary Name', row['Glossary Name attribute']],
                ['Attribute Glossary Definition', row['Attribute Glossary Definition']],
                ['Glossary Type', row['Glossary Type attribute']],
                ['Editability', row['Editability attribute']],
                ['Editability Role', row['Editability Role attribute']],
                ['Business Logic', row['Business Logic attribute']],
                ['Data Type', row['Data Type attribute']],
                ['Data Length', row['Data Length attribute']],
                ['Related To', row['Related To attribute']],
                ['Relationship Type', row['Relationship Type attribute']],
                ['Interface', ''],
                ['Sourcing Logic', ''],
                ['Created By', row['Created By attribute']],
                ['Created Date', row['Created Date attribute']]
            ];
            const notSpecified = (window.I18n && typeof window.I18n.t === 'function' && window.I18n.t('message.notSpecified') !== 'message.notSpecified')
                ? window.I18n.t('message.notSpecified')
                : 'Not specified';
            this.cfMeta.forEach(cf => {
                const cfId = cf.id || cf.ID;
                const attrCf = (this.cfValues[row.ID] || {})[cfId];
                const { display, percentIcon } = this.getCfDisplayParts(cf, attrCf);
                const dt = (cf.type || '').toLowerCase();
                let plain = display || '';
                if (dt === 'checkbox') {
                    plain = plain === '✓' ? 'Yes' : notSpecified;
                } else if (percentIcon && plain) {
                    plain = `${plain}%`;
                } else if (!plain) {
                    plain = notSpecified;
                }
                fields.push([cf.displayName || 'Custom Field', plain]);
            });
            function cell(val) {
                if (val == null || val === '') return '';
                const s = String(val);
                if (s.indexOf('\n') !== -1) return s.split('\n').map(line => `<div>${escapeHtml(line)}</div>`).join('');
                return escapeHtml(s);
            }
            dlg.innerHTML = html`
                <div class="modal">
                    <div class="modal-header">
                        <div class="modal-title">View Attribute</div>
                        <button type="button" class="modal-close">×</button>
                    </div>
                    <div class="modal-body">
                        <div class="table-wrapper">
                            <table class="attribute-table">
                                <tbody>
                                    ${fields.map(([label, value]) => `<tr><td class="col-label">${escapeHtml(label)}</td><td class="col-value">${cell(value)}</td></tr>`).join('')}
                                </tbody>
                            </table>
                        </div>
                    </div>
                    <div class="modal-footer">
                        <button type="button" class="btn" id="viewCloseBtn">Close</button>
                    </div>
                </div>
            `;
            document.body.appendChild(dlg);
            const close = (e) => {
                if (e) {
                    e.preventDefault();
                    e.stopPropagation();
                }
                dlg.remove();
            };
            dlg.querySelector('.modal-close').addEventListener('click', close);
            dlg.querySelector('#viewCloseBtn').addEventListener('click', close);
        }

        // Public methods for external use
        async saveAttributes() {
            if (!this.isEditMode) return false;
            try {
                await this.saveInlineEdits();
                return true;
            } catch (error) {
                console.error('Failed to save attributes:', error);
                throw error;
            }
        }

        cancelEdit() {
            if (this.isEditMode) {
                this.isEditMode = false;
                this.deletedIds.clear();
                this.render();
            }
        }

        hasDataChanged() {
            return this.isEditMode;
        }
    }

    window.AttributeTable = AttributeTable;
})();
