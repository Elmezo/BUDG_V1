/**
 * Object-private workflow editor for facet edit pages (mirrors admin default workflow fields, not admin-global list).
 */
(function () {
    'use strict';

    const FACET_TO_MODULE = {
        'dataset': 'Data Sets',
        'system': 'System',
        'capability': 'Capability',
        'client': 'Client',
        'product': 'Product',
        'system-interface': 'Interface',
        'policy': 'Policy',
        'committee': 'Committee',
        'process': 'Process',
        'business-area': 'Business Area',
        'glossary': 'Glossary',
        'geography': 'Geography',
        'legal-entity': 'Legal Entity',
        'org-unit': 'Org Unit',
        'project': 'Project',
        'regulation': 'Regulation',
        'regulator': 'Regulator',
        'regulatory-theme': 'Regulatory Theme'
    };

    let state = {
        rootId: null,
        facetType: null,
        objectId: null,
        moduleEntityId: null,
        workflows: [],
        bpmnModeler: null,
        initialized: false,
        crTypes: [],
        propertiesHooked: false,
        propOffFns: [],
        _owePropCurrentId: null
    };

    function escapeHtml(s) {
        if (s == null) return '';
        const d = document.createElement('div');
        d.textContent = s;
        return d.innerHTML;
    }

    function escapeAttr(s) {
        if (s == null) return '';
        return String(s)
            .replace(/&/g, '&amp;')
            .replace(/"/g, '&quot;')
            .replace(/</g, '&lt;');
    }

    async function resolveModuleEntityId(facetType) {
        const name = FACET_TO_MODULE[facetType];
        if (!name) return null;
        const res = await fetch('/api/modules', { credentials: 'include' });
        if (!res.ok) return null;
        const data = await res.json();
        const modules = data.modules || [];
        const m = modules.find(function (x) { return x.primaryName === name; });
        return m ? m.id : null;
    }

    function minimalBpmn(processName) {
        const safe = (processName || 'Workflow').replace(/&/g, '&amp;').replace(/"/g, '&quot;');
        return '<?xml version="1.0" encoding="UTF-8"?>\n' +
            '<bpmn2:definitions xmlns:bpmn2="http://www.omg.org/spec/BPMN/20100524/MODEL" ' +
            'xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" ' +
            'xmlns:di="http://www.omg.org/spec/DD/20100524/DI" id="Definitions_owe" targetNamespace="http://budg/object-workflow">\n' +
            '  <bpmn2:process id="Process_owe" name="' + safe + '" isExecutable="true">\n' +
            '    <bpmn2:startEvent id="StartEvent_owe"/>\n' +
            '    <bpmn2:endEvent id="EndEvent_owe"/>\n' +
            '    <bpmn2:sequenceFlow id="Flow_owe" sourceRef="StartEvent_owe" targetRef="EndEvent_owe"/>\n' +
            '  </bpmn2:process>\n' +
            '  <bpmndi:BPMNDiagram id="BPMNDiagram_owe">\n' +
            '    <bpmndi:BPMNPlane id="BPMNPlane_owe" bpmnElement="Process_owe">\n' +
            '      <bpmndi:BPMNShape id="Shape_Start_owe" bpmnElement="StartEvent_owe"><dc:Bounds x="179" y="99" width="36" height="36"/></bpmndi:BPMNShape>\n' +
            '      <bpmndi:BPMNShape id="Shape_End_owe" bpmnElement="EndEvent_owe"><dc:Bounds x="322" y="99" width="36" height="36"/></bpmndi:BPMNShape>\n' +
            '      <bpmndi:BPMNEdge id="Edge_Flow_owe" bpmnElement="Flow_owe">\n' +
            '        <di:waypoint x="215" y="117"/><di:waypoint x="322" y="117"/>\n' +
            '      </bpmndi:BPMNEdge>\n' +
            '    </bpmndi:BPMNPlane>\n' +
            '  </bpmndi:BPMNDiagram>\n' +
            '</bpmn2:definitions>';
    }

    function getModelerClass() {
        return window.BpmnModeler || window.BpmnJS || (typeof BpmnModeler !== 'undefined' ? BpmnModeler : null) ||
            (typeof BpmnJS !== 'undefined' ? BpmnJS : null);
    }

    function tearDownPropertyPanelHooks() {
        (state.propOffFns || []).forEach(function (off) {
            try { off(); } catch (e) { /* ignore */ }
        });
        state.propOffFns = [];
        state.propertiesHooked = false;
        state._owePropCurrentId = null;
    }

    function destroyModeler() {
        tearDownPropertyPanelHooks();
        if (state.bpmnModeler) {
            try { state.bpmnModeler.destroy(); } catch (e) { /* ignore */ }
            state.bpmnModeler = null;
        }
    }

    function getDocumentationText(bo) {
        if (!bo || !bo.documentation || !bo.documentation.length) return '';
        var d = bo.documentation[0];
        return (d && d.text) ? String(d.text) : '';
    }

    function clearOwePropertiesPanel() {
        var body = document.getElementById('owe_propertiesTabContent');
        if (body) {
            body.innerHTML = '<div class="owe-prop-empty">Select an element to view properties</div>';
        }
    }

    function renderPropertiesForElement(element) {
        var body = document.getElementById('owe_propertiesTabContent');
        if (!body || !element || !state.bpmnModeler) return;
        var bo = element.businessObject;
        if (!bo) {
            clearOwePropertiesPanel();
            return;
        }
        state._owePropCurrentId = element.id;
        var id = bo.id || '';
        var name = bo.name != null ? String(bo.name) : '';
        var doc = getDocumentationText(bo);
        var extra = '';
        if (element.type === 'bpmn:SequenceFlow') {
            var condBody = (bo.conditionExpression && bo.conditionExpression.body) ? String(bo.conditionExpression.body) : '';
            extra =
                '<div class="owe-prop-field">' +
                '<label for="owe_prop_condition">Condition</label>' +
                '<input type="text" class="owe-prop-input" id="owe_prop_condition" placeholder="e.g. ${approved}" value="' + escapeAttr(condBody) + '" />' +
                '</div>';
        }
        body.innerHTML =
            '<div class="owe-prop-field">' +
            '<label for="owe_prop_id">Id</label>' +
            '<input type="text" class="owe-prop-input" id="owe_prop_id" readonly value="' + escapeAttr(id) + '" />' +
            '</div>' +
            '<div class="owe-prop-field">' +
            '<label for="owe_prop_name">Name</label>' +
            '<input type="text" class="owe-prop-input" id="owe_prop_name" value="' + escapeAttr(name) + '" />' +
            '</div>' +
            '<div class="owe-prop-field">' +
            '<label for="owe_prop_doc">Documentation</label>' +
            '<textarea class="owe-prop-textarea" id="owe_prop_doc" rows="3">' + escapeHtml(doc) + '</textarea>' +
            '</div>' +
            extra +
            '<div class="owe-prop-actions">' +
            '<button type="button" class="btn btn-secondary btn-sm" id="owe_prop_apply">Apply</button>' +
            '</div>';
        var applyBtn = document.getElementById('owe_prop_apply');
        if (applyBtn) {
            applyBtn.addEventListener('click', function () {
                applyOwePropertiesFromPanel(element.id);
            });
        }
    }

    function applyOwePropertiesFromPanel(elementId) {
        if (!state.bpmnModeler || !elementId) return;
        var registry = state.bpmnModeler.get('elementRegistry');
        var modeling = state.bpmnModeler.get('modeling');
        var moddle = state.bpmnModeler.get('moddle');
        if (!registry || !modeling || !moddle) return;
        var el = registry.get(elementId);
        if (!el || !el.businessObject) return;
        var nameEl = document.getElementById('owe_prop_name');
        var docEl = document.getElementById('owe_prop_doc');
        var nm = nameEl ? nameEl.value.trim() : '';
        try {
            modeling.updateProperties(el, { name: nm || undefined });
        } catch (e) {
            console.warn('workflow-edit: update name', e);
        }
        if (docEl) {
            var text = docEl.value.trim();
            try {
                if (text) {
                    var d = moddle.create('bpmn:Documentation', { text: text });
                    modeling.updateProperties(el, { documentation: [d] });
                } else {
                    modeling.updateProperties(el, { documentation: [] });
                }
            } catch (e) {
                console.warn('workflow-edit: update documentation', e);
            }
        }
        var condEl = document.getElementById('owe_prop_condition');
        if (condEl && el.type === 'bpmn:SequenceFlow') {
            var c = condEl.value.trim();
            try {
                if (c) {
                    var fe = moddle.create('bpmn:FormalExpression', { body: c });
                    modeling.updateProperties(el, { conditionExpression: fe });
                } else {
                    modeling.updateProperties(el, { conditionExpression: undefined });
                }
            } catch (e) {
                console.warn('workflow-edit: update condition', e);
            }
        }
    }

    function onOweSelectionChanged(event) {
        var sel = event.newSelection || [];
        if (sel.length !== 1) {
            clearOwePropertiesPanel();
            state._owePropCurrentId = null;
            return;
        }
        renderPropertiesForElement(sel[0]);
    }

    function onOweElementChanged(event) {
        if (state._owePropCurrentId && event.element && event.element.id === state._owePropCurrentId) {
            renderPropertiesForElement(event.element);
        }
    }

    function ensurePropertyPanelHooks() {
        if (!state.bpmnModeler || state.propertiesHooked) return;
        var bus = state.bpmnModeler.get('eventBus');
        if (!bus) return;
        bus.on('selection.changed', onOweSelectionChanged);
        bus.on('element.changed', onOweElementChanged);
        state.propOffFns.push(function () { bus.off('selection.changed', onOweSelectionChanged); });
        state.propOffFns.push(function () { bus.off('element.changed', onOweElementChanged); });
        state.propertiesHooked = true;
        clearOwePropertiesPanel();
        try {
            var selection = state.bpmnModeler.get('selection');
            if (selection && typeof selection.get === 'function') {
                var selected = selection.get();
                if (selected && selected.length === 1) {
                    renderPropertiesForElement(selected[0]);
                }
            }
        } catch (e) { /* ignore */ }
    }

    function getWorkflowSelect() {
        return document.getElementById('owe_workflowSelect');
    }

    function isExistingWorkflowSelected() {
        const sel = getWorkflowSelect();
        return !!(sel && sel.value && String(sel.value).trim() !== '');
    }

    function getNameDescLengths() {
        const name = (document.getElementById('owe_workflowName') || {}).value || '';
        const desc = (document.getElementById('owe_workflowDescription') || {}).value || '';
        return { nameLen: name.trim().length, descLen: desc.trim().length };
    }

    /** Modeler can run: existing workflow selected, or new with both fields >= 6 */
    function diagramEditorEnabled() {
        if (isExistingWorkflowSelected()) return true;
        const L = getNameDescLengths();
        return L.nameLen >= 6 && L.descLen >= 6;
    }

    /** Show diagram card shell always; hint when name ok but description short (new only) */
    function updateDiagramHint() {
        const hint = document.getElementById('owe_diagramHint');
        if (!hint) return;
        const L = getNameDescLengths();
        const isNew = !isExistingWorkflowSelected();
        if (isNew && L.nameLen >= 6 && L.descLen < 6) {
            hint.style.display = 'block';
            hint.textContent = 'Add at least 6 characters in the description to unlock the diagram editor and save.';
        } else if (isNew && L.nameLen > 0 && L.nameLen < 6) {
            hint.style.display = 'block';
            hint.textContent = 'Enter a workflow name (at least 6 characters) to prepare the diagram.';
        } else {
            hint.style.display = 'none';
        }
    }

    function renderBpmnLibraryMissing(container) {
        if (!container) return;
        container.innerHTML =
            '<div class="owe-bpmn-missing">' +
            '  <i class="fas fa-exclamation-triangle"></i>' +
            '  <p><strong>BPMN editor could not load</strong></p>' +
            '  <p>Ensure <code>bpmn-modeler.production.min.js</code> is included before <code>workflow-edit.js</code> on this page.</p>' +
            '</div>';
    }

    function renderBpmnPlaceholder(container, message) {
        if (!container) return;
        container.innerHTML =
            '<div class="owe-bpmn-placeholder">' +
            '  <i class="fas fa-project-diagram"></i>' +
            '  <p>' + escapeHtml(message || 'Diagram will appear here when the form is ready.') + '</p>' +
            '</div>';
    }

    function renderShell(root) {
        root.innerHTML =
            '<div class="owe-workflow-edit">' +
            '  <div class="owe-workflow-form-card">' +
            '    <h3 class="owe-card-title">Select workflow</h3>' +
            '    <div class="owe-form-group">' +
            '      <label class="owe-label">Workflow <span class="owe-required">*</span></label>' +
            '      <select id="owe_workflowSelect" class="owe-select"></select>' +
            '    </div>' +
            '    <div class="owe-form-row owe-form-row-2">' +
            '      <div class="owe-form-group">' +
            '        <label class="owe-label">Workflow name <span class="owe-required">*</span></label>' +
            '        <input type="text" id="owe_workflowName" class="owe-input" minlength="6" placeholder="Enter workflow name" />' +
            '        <small class="owe-help">Minimum 6 characters</small>' +
            '      </div>' +
            '      <div class="owe-form-group owe-toggle-wrap">' +
            '        <span class="owe-label">Status</span>' +
            '        <label class="owe-switch">' +
            '          <input type="checkbox" id="owe_workflowActive" checked />' +
            '          <span class="owe-switch-slider"></span>' +
            '          <span class="owe-switch-text" id="owe_activeLabel">Active</span>' +
            '        </label>' +
            '      </div>' +
            '    </div>' +
            '    <div class="owe-form-group">' +
            '      <label class="owe-label">Description <span class="owe-required">*</span></label>' +
            '      <textarea id="owe_workflowDescription" class="owe-textarea" rows="4" minlength="6" placeholder="Enter workflow description"></textarea>' +
            '      <small class="owe-help">Minimum 6 characters</small>' +
            '    </div>' +
            '    <div class="owe-form-group" id="owe_typeGroup">' +
            '      <label class="owe-label">Change request types</label>' +
            '      <div id="owe_crTypeOptions" class="owe-cr-types"></div>' +
            '    </div>' +
            '    <div class="owe-form-actions">' +
            '      <button type="button" class="btn btn-primary" id="owe_saveWorkflowBtn"><i class="fas fa-save"></i> Save workflow</button>' +
            '    </div>' +
            '  </div>' +
            '  <div class="owe-bpmn-editor-card" id="owe_diagramCard">' +
            '    <h3 class="owe-bpmn-title">Workflow diagram</h3>' +
            '    <p id="owe_diagramHint" class="owe-diagram-hint" style="display:none;"></p>' +
            '    <div class="owe-bpmn-wrap">' +
            '      <div class="owe-bpmn-split">' +
            '        <div id="owe_bpmnContainer" class="owe-bpmn-container"></div>' +
            '        <div id="owe_propertiesPanel" class="owe-floating-properties">' +
            '          <div class="owe-prop-header">' +
            '            <span class="owe-prop-title">Properties</span>' +
            '            <i class="fas fa-grip-vertical owe-prop-drag-hint" title=""></i>' +
            '          </div>' +
            '          <div class="owe-prop-tabs"><span class="owe-prop-tab active">General</span></div>' +
            '          <div id="owe_propertiesTabContent" class="owe-prop-body">' +
            '            <div class="owe-prop-empty">Select an element to view properties</div>' +
            '          </div>' +
            '        </div>' +
            '      </div>' +
            '    </div>' +
            '  </div>' +
            '</div>' +
            '<style>' +
            '.owe-workflow-edit{max-width:1200px;margin:0 auto;}' +
            '.owe-workflow-form-card,.owe-bpmn-editor-card{background:#fff;border:1px solid #e5e7eb;border-radius:10px;padding:0 0 1.25rem;margin-bottom:1.25rem;box-shadow:0 1px 3px rgba(0,0,0,.06);}' +
            '.owe-card-title,.owe-bpmn-title{font-size:.8rem;font-weight:600;text-transform:uppercase;letter-spacing:.04em;margin:0;padding:.85rem 1.25rem;background:#248567;color:#fff;border-radius:10px 10px 0 0;}' +
            '.owe-bpmn-title{background:#1f6d54;}' +
            '.owe-form-group{padding:0 1.25rem;margin-top:1rem;}' +
            '.owe-form-row{display:flex;gap:1.25rem;flex-wrap:wrap;align-items:flex-start;}' +
            '.owe-form-row-2 .owe-form-group:first-child{flex:1;min-width:220px;}' +
            '.owe-toggle-wrap{flex:0 0 auto;padding-top:.15rem;}' +
            '.owe-label{display:block;font-size:.8rem;font-weight:600;color:#374151;margin-bottom:.4rem;}' +
            '.owe-required{color:#dc2626;}' +
            '.owe-input,.owe-select,.owe-textarea{width:100%;box-sizing:border-box;padding:.6rem .75rem;border:1px solid #d1d5db;border-radius:8px;font-size:.9rem;font-family:inherit;background:#fff;}' +
            '.owe-input:focus,.owe-select:focus,.owe-textarea:focus{outline:none;border-color:#248567;box-shadow:0 0 0 3px rgba(36,133,103,.15);}' +
            '.owe-textarea{resize:vertical;min-height:96px;}' +
            '.owe-help{display:block;font-size:.75rem;color:#6b7280;margin-top:.35rem;}' +
            '.owe-diagram-hint{margin:.75rem 1.25rem 0;padding:.65rem .85rem;background:#fffbeb;border:1px solid #fcd34d;border-radius:8px;font-size:.85rem;color:#92400e;}' +
            '.owe-bpmn-wrap{padding:1rem 1.25rem 0;}' +
            '.owe-bpmn-split{position:relative;width:100%;height:min(70vh,640px);min-height:420px;}' +
            '.owe-bpmn-container{position:absolute;left:0;right:0;top:0;bottom:0;border:1px solid #e5e7eb;border-radius:8px;background:#f9fafb;overflow:hidden;isolation:isolate;}' +
            '.owe-bpmn-container .bjs-container,.owe-bpmn-container .djs-container{width:100%!important;height:100%!important;position:relative;}' +
            '.owe-floating-properties{position:absolute;width:300px;max-width:42%;max-height:calc(100% - 24px);right:12px;top:12px;bottom:12px;border:1px solid #e5e7eb;border-radius:8px;background:#fff;box-shadow:0 4px 12px rgba(0,0,0,.12);display:flex;flex-direction:column;z-index:30;overflow:hidden;}' +
            '.owe-prop-header{display:flex;align-items:center;justify-content:space-between;padding:10px 12px;border-bottom:1px solid #e5e7eb;background:#fafafa;}' +
            '.owe-prop-title{font-size:11px;font-weight:700;letter-spacing:.06em;color:#374151;text-transform:uppercase;}' +
            '.owe-prop-drag-hint{color:#9ca3af;font-size:12px;}' +
            '.owe-prop-tabs{padding:6px 12px 0;border-bottom:1px solid #e5e7eb;background:#fafafa;}' +
            '.owe-prop-tab{display:inline-block;font-size:12px;font-weight:600;color:#b91c1c;padding-bottom:6px;border-bottom:2px solid #b91c1c;}' +
            '.owe-prop-body{flex:1;overflow-y:auto;padding:12px;}' +
            '.owe-prop-empty{color:#6b7280;text-align:center;padding:16px 8px;font-size:13px;}' +
            '.owe-prop-field{margin-bottom:12px;}' +
            '.owe-prop-field label{display:block;font-size:11px;font-weight:600;color:#6b7280;margin-bottom:4px;}' +
            '.owe-prop-input,.owe-prop-textarea{width:100%;box-sizing:border-box;border:1px solid #d1d5db;border-radius:6px;padding:6px 8px;font-size:13px;font-family:inherit;}' +
            '.owe-prop-textarea{min-height:72px;resize:vertical;}' +
            '.owe-prop-actions{margin-top:8px;}' +
            '.owe-bpmn-placeholder,.owe-bpmn-missing{position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;text-align:center;padding:1.5rem;color:#6b7280;}' +
            '.owe-bpmn-placeholder i,.owe-bpmn-missing i{font-size:2.5rem;color:#d1d5db;margin-bottom:.75rem;}' +
            '.owe-bpmn-missing i{color:#f59e0b;}' +
            '.owe-bpmn-missing code{background:#f3f4f6;padding:.15rem .4rem;border-radius:4px;font-size:.8rem;}' +
            '.owe-cr-types{max-height:200px;overflow-y:auto;border:1px solid #e5e7eb;padding:.5rem .65rem;border-radius:8px;background:#fafafa;}' +
            '.owe-cr-types label{display:flex;align-items:center;gap:.5rem;margin:.35rem 0;font-size:.875rem;color:#374151;cursor:pointer;}' +
            '.owe-cr-types input{flex-shrink:0;}' +
            '.owe-form-actions{display:flex;justify-content:flex-end;padding:0 1.25rem;margin-top:1.25rem;}' +
            '.owe-switch{position:relative;display:inline-flex;align-items:center;gap:.65rem;cursor:pointer;user-select:none;margin-top:.25rem;}' +
            '.owe-switch input{opacity:0;width:0;height:0;position:absolute;}' +
            '.owe-switch-slider{position:relative;width:48px;height:26px;background:#d1d5db;border-radius:26px;transition:background .2s;flex-shrink:0;}' +
            '.owe-switch-slider:before{content:"";position:absolute;height:20px;width:20px;left:3px;bottom:3px;background:#fff;border-radius:50%;transition:transform .2s;box-shadow:0 1px 2px rgba(0,0,0,.12);}' +
            '.owe-switch input:checked + .owe-switch-slider{background:#248567;}' +
            '.owe-switch input:checked + .owe-switch-slider:before{transform:translateX(22px);}' +
            '.owe-switch-text{font-size:.875rem;font-weight:500;color:#374151;}' +
            '</style>';
    }

    function syncActiveLabel() {
        const cb = document.getElementById('owe_workflowActive');
        const lbl = document.getElementById('owe_activeLabel');
        if (lbl && cb) lbl.textContent = cb.checked ? 'Active' : 'Inactive';
    }

    async function loadCrTypes() {
        const box = document.getElementById('owe_crTypeOptions');
        if (!box) return;
        try {
            const res = await fetch('/api/changerequest_types', { credentials: 'include' });
            if (!res.ok) return;
            state.crTypes = await res.json();
            box.innerHTML = (state.crTypes || []).map(function (t) {
                const name = t.name || t.primaryName || String(t);
                const id = 'owe_ct_' + String(name).replace(/\W+/g, '_');
                return '<label><input type="checkbox" class="owe-cr-cb" value="' + escapeHtml(name) + '" id="' + id + '"/> ' + escapeHtml(name) + '</label>';
            }).join('');
        } catch (e) {
            console.warn('workflow-edit: could not load CR types', e);
        }
    }

    function selectedCrTypes() {
        return Array.prototype.slice.call(document.querySelectorAll('.owe-cr-cb:checked')).map(function (cb) {
            return cb.value;
        });
    }

    async function loadEditList() {
        const sel = document.getElementById('owe_workflowSelect');
        if (!sel) return;
        const url = '/api/object_workflows?mode=edit&facetType=' + encodeURIComponent(state.facetType) +
            '&objectId=' + encodeURIComponent(String(state.objectId)) +
            '&entityId=' + encodeURIComponent(String(state.moduleEntityId));
        const res = await fetch(url, { credentials: 'include' });
        if (!res.ok) {
            sel.innerHTML = '<option value="">(failed to load)</option>';
            return;
        }
        state.workflows = await res.json();
        sel.innerHTML = '<option value="">Add a new Workflow</option>';
        (state.workflows || []).forEach(function (wf) {
            const opt = document.createElement('option');
            opt.value = String(wf.id);
            opt.textContent = wf.primaryName || ('Workflow ' + wf.id);
            sel.appendChild(opt);
        });
    }

    function applyBpmnLayoutFixes() {
        const container = document.getElementById('owe_bpmnContainer');
        if (!container || !state.bpmnModeler) return;
        try {
            const bjs = container.querySelector('.bjs-container');
            if (bjs) {
                bjs.style.width = '100%';
                bjs.style.height = '100%';
                bjs.style.position = 'relative';
            }
            const palette = container.querySelector('.djs-palette');
            if (palette) {
                palette.style.display = 'block';
                palette.style.visibility = 'visible';
                if (!palette.classList.contains('open')) {
                    const toggle = palette.querySelector('.djs-palette-toggle');
                    if (toggle) toggle.click();
                }
            }
            const canvas = state.bpmnModeler.get('canvas');
            if (canvas && canvas.zoom) canvas.zoom('fit-viewport', 'auto');
        } catch (e) { /* ignore */ }
    }

    async function initModelerIfNeeded() {
        const container = document.getElementById('owe_bpmnContainer');
        if (!container) return;

        const ModelerClass = getModelerClass();
        if (!ModelerClass) {
            renderBpmnLibraryMissing(container);
            return;
        }

        if (!diagramEditorEnabled()) {
            destroyModeler();
            renderBpmnPlaceholder(container, 'Fill in the workflow name and description to edit the diagram.');
            return;
        }

        if (state.bpmnModeler) return;

        container.innerHTML = '';
        try {
            const additionalModules = [];
            if (typeof window.CustomBpmnRenderer !== 'undefined') {
                additionalModules.push(window.CustomBpmnRenderer);
            }
            state.bpmnModeler = new ModelerClass({
                container: container,
                additionalModules: additionalModules
            });
            try {
                const bus = state.bpmnModeler.get('eventBus');
                if (bus) {
                    bus.on('commandStack.changed', function () { /* optional dirty */ });
                }
            } catch (e) { /* ignore */ }
            ensurePropertyPanelHooks();
        } catch (e) {
            console.error('workflow-edit: modeler init', e);
            container.innerHTML = '<div class="owe-bpmn-missing"><p>Failed to start BPMN editor.</p></div>';
            state.bpmnModeler = null;
        }
    }

    async function loadXmlIntoModeler(xml) {
        await initModelerIfNeeded();
        if (!state.bpmnModeler) return;
        try {
            await state.bpmnModeler.importXML(xml);
            setTimeout(function () {
                applyBpmnLayoutFixes();
            }, 200);
        } catch (e) {
            console.error('workflow-edit importXML', e);
        }
    }

    async function fetchObjectWorkflowBpmn(processDefId) {
        const q = 'facetType=' + encodeURIComponent(state.facetType) + '&objectId=' + encodeURIComponent(String(state.objectId));
        const ts = new Date().getTime();
        const bRes = await fetch('/api/object_workflows/' + processDefId + '/bpmn?' + q + '&t=' + ts, { credentials: 'include' });
        if (!bRes.ok) return null;
        const bJson = await bRes.json().catch(function () { return null; });
        return bJson && bJson.xml ? bJson.xml : null;
    }

    async function onWorkflowSelectChange() {
        const sel = getWorkflowSelect();
        const id = sel && sel.value ? parseInt(sel.value, 10) : 0;
        const nameEl = document.getElementById('owe_workflowName');
        const descEl = document.getElementById('owe_workflowDescription');
        const actEl = document.getElementById('owe_workflowActive');
        document.querySelectorAll('.owe-cr-cb').forEach(function (cb) { cb.checked = false; });

        destroyModeler();
        updateDiagramHint();

        if (!id) {
            if (nameEl) nameEl.value = '';
            if (descEl) descEl.value = '';
            if (actEl) actEl.checked = true;
            syncActiveLabel();
            const c = document.getElementById('owe_bpmnContainer');
            if (c) renderBpmnPlaceholder(c, 'Choose an existing workflow or enter name and description to create a new diagram.');
            return;
        }
        const wf = (state.workflows || []).find(function (w) { return w.id === id; });
        if (!wf) return;
        if (nameEl) nameEl.value = wf.primaryName || '';
        if (descEl) descEl.value = wf.description || '';
        if (actEl) actEl.checked = (wf.status === 'Enabled');
        syncActiveLabel();
        updateDiagramHint();

        await initModelerIfNeeded();
        try {
            var xml = await fetchObjectWorkflowBpmn(id);
            if (xml && xml.trim()) {
                await loadXmlIntoModeler(xml);
                return;
            }
        } catch (e) {
            console.warn('workflow-edit: load object bpmn', e);
        }
        var nm = (document.getElementById('owe_workflowName') || {}).value || 'Workflow';
        await loadXmlIntoModeler(minimalBpmn(nm));
    }

    async function saveBpmnForId(processDefId) {
        if (!state.bpmnModeler) return;
        var result = await state.bpmnModeler.saveXML({ format: true });
        var xml = result && result.xml ? result.xml : '';
        if (!xml || !xml.trim()) return;
        var body = {
            xml: xml,
            facetType: state.facetType,
            objectId: state.objectId
        };
        var res = await fetch('/api/object_workflows/' + processDefId + '/bpmn', {
            method: 'POST',
            credentials: 'include',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        });
        if (!res.ok) {
            var err = await res.json().catch(function () { return {}; });
            throw new Error(err.error || ('BPMN save failed: ' + res.status));
        }
    }

    async function onSaveClick() {
        const nameEl = document.getElementById('owe_workflowName');
        const descEl = document.getElementById('owe_workflowDescription');
        const actEl = document.getElementById('owe_workflowActive');
        const sel = getWorkflowSelect();
        const name = (nameEl && nameEl.value || '').trim();
        const desc = (descEl && descEl.value || '').trim();
        const active = actEl && actEl.checked;
        if (name.length < 6 || desc.length < 6) {
            alert('Name and description must be at least 6 characters.');
            return;
        }
        await initModelerIfNeeded();
        if (!state.bpmnModeler) {
            alert('Workflow diagram is not ready. Check that the BPMN library is loaded.');
            return;
        }

        var crTypes = selectedCrTypes();
        var selectedId = sel && sel.value ? parseInt(sel.value, 10) : 0;
        var wfExisting = selectedId ? (state.workflows || []).find(function (w) { return w.id === selectedId; }) : null;
        var payload = {
            facetType: state.facetType,
            objectId: state.objectId,
            entityId: state.moduleEntityId,
            primaryName: name,
            description: desc,
            status: active ? 'Enabled' : 'Disabled',
            reference: name,
            isDefault: wfExisting ? !!(wfExisting.isDefault === true || wfExisting.default === true) : false,
            crTypes: crTypes
        };

        try {
            if (!selectedId) {
                var cRes = await fetch('/api/object_workflows', {
                    method: 'POST',
                    credentials: 'include',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(payload)
                });
                var cData = await cRes.json().catch(function () { return ({}); });
                if (!cRes.ok) throw new Error(cData.error || ('Create failed: ' + cRes.status));
                selectedId = cData.id;
            } else {
                var uRes = await fetch('/api/object_workflows/' + selectedId, {
                    method: 'PUT',
                    credentials: 'include',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(payload)
                });
                var uData = await uRes.json().catch(function () { return ({}); });
                if (!uRes.ok) throw new Error(uData.error || ('Update failed: ' + uRes.status));
            }

            await saveBpmnForId(selectedId);
            alert('Workflow saved successfully.');
            await loadEditList();
            if (sel) {
                sel.value = String(selectedId);
            }
            await onWorkflowSelectChange();
        } catch (e) {
            console.error(e);
            alert(e.message || String(e));
        }
    }

    function onFormInputsChanged() {
        updateDiagramHint();
        const sel = getWorkflowSelect();
        const container = document.getElementById('owe_bpmnContainer');
        if (!diagramEditorEnabled()) {
            destroyModeler();
            if (container && !getModelerClass()) {
                renderBpmnLibraryMissing(container);
            } else if (container) {
                renderBpmnPlaceholder(container, 'Fill in the workflow name and description to edit the diagram.');
            }
            return;
        }
        initModelerIfNeeded().then(function () {
            if (!state.bpmnModeler || !sel) return;
            if (!sel.value) {
                var nm = (document.getElementById('owe_workflowName') || {}).value || 'Workflow';
                loadXmlIntoModeler(minimalBpmn(nm));
            }
        });
    }

    function wireEventsOnce() {
        var nameEl = document.getElementById('owe_workflowName');
        var descEl = document.getElementById('owe_workflowDescription');
        var sel = getWorkflowSelect();
        var saveBtn = document.getElementById('owe_saveWorkflowBtn');
        var actEl = document.getElementById('owe_workflowActive');
        if (nameEl) nameEl.addEventListener('input', onFormInputsChanged);
        if (descEl) descEl.addEventListener('input', onFormInputsChanged);
        if (actEl) {
            actEl.addEventListener('change', syncActiveLabel);
        }
        if (sel) sel.addEventListener('change', function () { onWorkflowSelectChange(); });
        if (saveBtn) saveBtn.addEventListener('click', onSaveClick);
    }

    async function ensureInitialized(opts) {
        if (!opts || !opts.rootId || !opts.facetType || opts.objectId == null) {
            console.warn('ObjectWorkflowEdit.ensureInitialized: rootId, facetType, objectId required');
            return;
        }
        var root = document.getElementById(opts.rootId);
        if (!root) {
            console.warn('ObjectWorkflowEdit: root not found', opts.rootId);
            return;
        }

        state.rootId = opts.rootId;
        state.facetType = opts.facetType;
        state.objectId = parseInt(String(opts.objectId), 10);
        state.moduleEntityId = await resolveModuleEntityId(state.facetType);
        if (!state.moduleEntityId) {
            root.innerHTML = '<div class="empty-state"><p>Could not resolve module for this facet.</p></div>';
            return;
        }

        if (!state.initialized) {
            renderShell(root);
            await loadCrTypes();
            wireEventsOnce();
            state.initialized = true;
        }

        await loadEditList();
        await onWorkflowSelectChange();
        updateDiagramHint();
    }

    function reset() {
        destroyModeler();
        state.initialized = false;
        state.workflows = [];
    }

    window.ObjectWorkflowEdit = {
        ensureInitialized: ensureInitialized,
        reset: reset
    };
})();
